package com.marvneo.companion.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.DiscoveredDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@SuppressLint("MissingPermission")
class MarvNeoBleManager(private val context: Context) {

    private val tag = "MarvNeoBleManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs: SharedPreferences = context.getSharedPreferences("marv_neo_prefs", Context.MODE_PRIVATE)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Discovered & Bonded Devices
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Connected device metadata
    val connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceAddress = MutableStateFlow<String?>(null)

    // Telemetry StateFlows
    val stepsFlow = MutableStateFlow(0)
    val caloriesFlow = MutableStateFlow(0)
    val distanceFlow = MutableStateFlow(0)
    val heartRateFlow = MutableStateFlow(0)
    val spO2Flow = MutableStateFlow(0)
    val batteryFlow = MutableStateFlow(100)

    private val commandMutex = Mutex()
    private var targetDeviceAddress: String? = null
    private var reconnectAttempts = 0
    private var isUserDisconnecting = false

    private var connectionTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(tag, "BLE Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "onConnectionStateChange: status=$status, newState=$newState")
            connectionTimeoutJob?.cancel()

            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                Log.w(tag, "GATT connection failure with status $status. Cleaning up...")
                cleanup()
                _connectionState.value = ConnectionState.Disconnected
                if (!isUserDisconnecting) {
                    scheduleReconnect()
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(tag, "GATT Connected. Status: $status. Requesting MTU 512...")
                    _connectionState.value = ConnectionState.Connecting
                    reconnectAttempts = 0
                    isUserDisconnecting = false
                    // Request MTU 512 for fast data transfer
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(tag, "GATT Disconnected. Status: $status. Cleaning up...")
                    cleanup()
                    _connectionState.value = ConnectionState.Disconnected
                    if (!isUserDisconnecting) {
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(tag, "MTU changed to $mtu, status: $status. Discovering services...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                var foundTx: BluetoothGattCharacteristic? = null
                var foundRx: BluetoothGattCharacteristic? = null

                // Phase 1: Check known Crrepa / DaFit / Nordic services
                val knownServices = listOf(
                    BleGattAttributes.NUS_SERVICE_UUID,
                    BleGattAttributes.CRP_FFF0_SERVICE_UUID,
                    BleGattAttributes.CRP_FEE7_SERVICE_UUID,
                    BleGattAttributes.DFU_SERVICE_UUID,
                    BleGattAttributes.HS_AE00_SERVICE_UUID,
                    UUID.fromString("c6a22920-f821-18bf-9704-0266f20e80fd"),
                    UUID.fromString("000034f1-0000-1000-8000-00805f9b34fb")
                )

                for (srvUuid in knownServices) {
                    val srv = gatt.getService(srvUuid) ?: continue
                    for (char in srv.characteristics) {
                        val props = char.properties
                        if (foundTx == null && (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                    props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)) {
                            foundTx = char
                        }
                        if (foundRx == null && (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                                    props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)) {
                            foundRx = char
                        }
                    }
                    if (foundTx != null && foundRx != null) {
                        Log.i(tag, "Resolved TX/RX via known service: $srvUuid")
                        break
                    }
                }

                // Phase 2: Universal Fallback across all non-standard services
                if (foundTx == null || foundRx == null) {
                    Log.w(tag, "Known service match incomplete. Performing dynamic universal GATT scan...")
                    for (srv in gatt.services) {
                        val srvUuidStr = srv.uuid.toString().lowercase()
                        // Skip standard Bluetooth SIG generic profile services
                        if (srvUuidStr.startsWith("00001800") || srvUuidStr.startsWith("00001801") || srvUuidStr.startsWith("0000180a")) {
                            continue
                        }
                        for (char in srv.characteristics) {
                            val props = char.properties
                            if (foundTx == null && (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                        props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)) {
                                foundTx = char
                            }
                            if (foundRx == null && (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                                        props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)) {
                                foundRx = char
                            }
                        }
                        if (foundTx != null && foundRx != null) {
                            Log.i(tag, "Resolved TX/RX dynamically from service: ${srv.uuid}")
                            break
                        }
                    }
                }

                if (foundTx != null && foundRx != null) {
                    txCharacteristic = foundTx
                    rxCharacteristic = foundRx

                    enableNotifications(gatt, rxCharacteristic)
                    _connectionState.value = ConnectionState.Connected

                    val device = gatt.device
                    val name = device.name ?: "beatXP Marv Neo"
                    connectedDeviceName.value = name
                    connectedDeviceAddress.value = device.address

                    // Save to preferences for subsequent auto-connect
                    prefs.edit()
                        .putString("last_device_address", device.address)
                        .putString("last_device_name", name)
                        .apply()

                    Log.i(tag, "Successfully established watch communication with $name ($device.address)")

                    // Handshake: Time sync, battery query, and steps query
                    scope.launch {
                        delay(350)
                        sendCommand(CrrepaProtocol.createTimeSyncPacket())
                        delay(250)
                        sendCommand(CrrepaProtocol.createBatteryQueryPacket())
                        delay(250)
                        sendCommand(CrrepaProtocol.createStepsQueryPacket())
                    }
                } else {
                    Log.e(tag, "Failed to resolve GATT TX and RX characteristics on device.")
                    _connectionState.value = ConnectionState.Error("Unable to locate watch data channels")
                    cleanup()
                }
            } else {
                Log.e(tag, "Service discovery failed with status $status")
                _connectionState.value = ConnectionState.Disconnected
                cleanup()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPacket(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleIncomingPacket(characteristic.value)
        }
    }

    init {
        refreshBondedDevices()
    }

    /**
     * Checks if a BluetoothDevice matches a smartwatch (by address, name, or Bluetooth Class).
     */
    fun isSmartwatchDevice(device: BluetoothDevice): Boolean {
        val lastAddress = prefs.getString("last_device_address", null)
        if (!lastAddress.isNullOrBlank() && device.address.equals(lastAddress, ignoreCase = true)) {
            return true
        }

        val name = device.name ?: ""
        if (name.contains("Marv", ignoreCase = true) ||
            name.contains("beatXP", ignoreCase = true) ||
            name.contains("MOY", ignoreCase = true) ||
            name.contains("Watch", ignoreCase = true) ||
            name.contains("Band", ignoreCase = true)
        ) {
            return true
        }

        val deviceClass = device.bluetoothClass?.majorDeviceClass
        if (deviceClass == BluetoothClass.Device.Major.WEARABLE ||
            deviceClass == BluetoothClass.Device.Major.HEALTH
        ) {
            return true
        }

        return false
    }

    /**
     * Automatically connects to a connected or bonded smartwatch device.
     */
    fun autoConnect(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) return false

        // 1. Check if device is already connected via GATT
        val gattConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        val connectedWatch = gattConnected.firstOrNull { isSmartwatchDevice(it) }
        if (connectedWatch != null) {
            Log.i(tag, "Found already connected GATT watch: ${connectedWatch.name} (${connectedWatch.address})")
            connect(connectedWatch.address)
            return true
        }

        // 2. Check saved device address
        val lastAddress = prefs.getString("last_device_address", null)
        if (!lastAddress.isNullOrBlank()) {
            Log.i(tag, "Auto-connecting to previously paired address: $lastAddress")
            connect(lastAddress)
            return true
        }

        // 3. Check bonded devices for any smartwatch
        val bonded = adapter.bondedDevices
        val bondedWatch = bonded?.firstOrNull { isSmartwatchDevice(it) }
        if (bondedWatch != null) {
            Log.i(tag, "Auto-connecting to bonded watch: ${bondedWatch.name} (${bondedWatch.address})")
            connect(bondedWatch.address)
            return true
        }

        return false
    }

    fun refreshBondedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        val bonded = adapter.bondedDevices ?: return
        val currentList = _discoveredDevices.value.toMutableList()

        for (device in bonded) {
            val name = device.name ?: "Bluetooth Device"
            val address = device.address
            val existingIndex = currentList.indexOfFirst { it.address == address }
            val item = DiscoveredDevice(
                name = name,
                address = address,
                rssi = -50,
                isBonded = true
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                if (isSmartwatchDevice(device)) {
                    currentList.add(0, item)
                } else {
                    currentList.add(item)
                }
            }
        }
        _discoveredDevices.value = currentList
    }

    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Log.w(tag, "Bluetooth disabled. Cannot scan.")
            return
        }

        refreshBondedDevices()

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(tag, "BluetoothLeScanner is null")
            return
        }

        if (_isScanning.value) {
            stopScan()
        }

        _isScanning.value = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.i(tag, "BLE Scan started.")

            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(15_000)
                stopScan()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting BLE scan", e)
            _isScanning.value = false
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        if (_isScanning.value) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping BLE scan", e)
            }
            _isScanning.value = false
            Log.i(tag, "BLE Scan stopped.")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val name = device.name ?: record?.deviceName

        if (name.isNullOrBlank()) return

        val address = device.address
        val rssi = result.rssi

        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == address }

        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
        val discovered = DiscoveredDevice(name = name, address = address, rssi = rssi, isBonded = isBonded)

        if (existingIndex >= 0) {
            currentList[existingIndex] = discovered
        } else {
            if (isSmartwatchDevice(device)) {
                currentList.add(0, discovered)
            } else {
                currentList.add(discovered)
            }
        }
        _discoveredDevices.value = currentList
    }

    fun connect(macAddress: String) {
        if (macAddress.isBlank()) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is turned off")
            return
        }

        targetDeviceAddress = macAddress
        isUserDisconnecting = false
        reconnectJob?.cancel()

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            Log.e(tag, "Invalid MAC address format: $macAddress", e)
            _connectionState.value = ConnectionState.Error("Invalid MAC address")
            return
        }

        stopScan()
        cleanup()

        _connectionState.value = ConnectionState.Connecting
        connectedDeviceAddress.value = macAddress
        connectedDeviceName.value = device.name ?: "beatXP Marv Neo"

        // 15-second connection watchdog timeout
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(15_000)
            if (_connectionState.value == ConnectionState.Connecting) {
                Log.w(tag, "Connection timed out after 15s. Resetting state.")
                _connectionState.value = ConnectionState.Disconnected
                cleanup()
            }
        }

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(tag, "Failed to call connectGatt", e)
            _connectionState.value = ConnectionState.Disconnected
            cleanup()
        }
    }

    fun disconnect() {
        isUserDisconnecting = true
        targetDeviceAddress = null
        reconnectJob?.cancel()
        connectionTimeoutJob?.cancel()

        _connectionState.value = ConnectionState.Disconnected

        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            Log.e(tag, "Error during gatt disconnect", e)
        }
        cleanup()
    }

    fun forgetDevice() {
        prefs.edit().remove("last_device_address").remove("last_device_name").apply()
        disconnect()
    }

    suspend fun sendCommand(packet: ByteArray): Boolean = commandMutex.withLock {
        val gatt = bluetoothGatt ?: return false
        val char = txCharacteristic ?: return false

        char.value = packet
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val success = gatt.writeCharacteristic(char)
        delay(35)
        return success
    }

    suspend fun flashBinaryChunks(
        chunks: List<ByteArray>,
        onProgress: (Float) -> Unit
    ): Boolean {
        if (_connectionState.value !is ConnectionState.Connected) return false
        val total = chunks.size
        for (i in chunks.indices) {
            val chunk = chunks[i]
            val success = sendCommand(chunk)
            if (!success) {
                delay(30)
                sendCommand(chunk)
            }
            onProgress((i + 1).toFloat() / total.toFloat())
            delay(15)
        }
        return true
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleGattAttributes.CCCD_DESCRIPTOR_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleIncomingPacket(data: ByteArray) {
        if (data.isEmpty()) return
        when (data[0]) {
            CrrepaProtocol.CMD_QUERY_STEPS -> {
                CrrepaProtocol.parseSteps(data)?.let {
                    stepsFlow.value = it.steps
                    caloriesFlow.value = it.calories
                    distanceFlow.value = it.distanceMeters
                }
            }
            CrrepaProtocol.CMD_QUERY_BATTERY -> {
                CrrepaProtocol.parseBattery(data)?.let {
                    batteryFlow.value = it.percentage
                }
            }
            CrrepaProtocol.CMD_HEART_RATE -> {
                CrrepaProtocol.parseHeartRate(data)?.let {
                    heartRateFlow.value = it.bpm
                }
            }
            CrrepaProtocol.CMD_BLOOD_OXYGEN -> {
                CrrepaProtocol.parseSpO2(data)?.let {
                    spO2Flow.value = it.percentage
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val address = targetDeviceAddress ?: return
        if (isUserDisconnecting) return

        reconnectAttempts++
        val delayMs = minOf(30000L, 1000L * (1 shl minOf(reconnectAttempts, 5)))
        reconnectJob = scope.launch {
            Log.i(tag, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms...")
            delay(delayMs)
            if (targetDeviceAddress != null && _connectionState.value == ConnectionState.Disconnected && !isUserDisconnecting) {
                connect(address)
            }
        }
    }

    private fun cleanup() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing GATT", e)
        }
        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
    }
}
