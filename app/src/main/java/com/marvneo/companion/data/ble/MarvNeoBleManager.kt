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
                val nusService = gatt.getService(BleGattAttributes.NUS_SERVICE_UUID)
                if (nusService != null) {
                    txCharacteristic = nusService.getCharacteristic(BleGattAttributes.NUS_TX_CHAR_UUID)
                    rxCharacteristic = nusService.getCharacteristic(BleGattAttributes.NUS_RX_CHAR_UUID)

                    enableNotifications(gatt, rxCharacteristic)
                    _connectionState.value = ConnectionState.Connected

                    val device = gatt.device
                    val name = device.name ?: "beatXP Marv Neo"
                    connectedDeviceName.value = name
                    connectedDeviceAddress.value = device.address

                    // Save to SharedPreferences for auto-connection
                    prefs.edit()
                        .putString("last_device_address", device.address)
                        .putString("last_device_name", name)
                        .apply()

                    // Handshake: Time sync, battery query, and steps query
                    scope.launch {
                        delay(300)
                        sendCommand(CrrepaProtocol.createTimeSyncPacket())
                        delay(200)
                        sendCommand(CrrepaProtocol.createBatteryQueryPacket())
                        delay(200)
                        sendCommand(CrrepaProtocol.createStepsQueryPacket())
                    }
                } else {
                    Log.e(tag, "Nordic UART Service not found on device!")
                    _connectionState.value = ConnectionState.Error("Unsupported watch protocol")
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
        // Populate bonded devices immediately
        refreshBondedDevices()
    }

    /**
     * Refresh bonded Bluetooth devices list.
     */
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
                rssi = -55,
                isBonded = true
            )
            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                currentList.add(0, item) // Place bonded at the top
            }
        }
        _discoveredDevices.value = currentList
    }

    /**
     * Start live BLE radar scan for smartwatches.
     */
    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Log.w(tag, "Bluetooth is disabled. Cannot start scan.")
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

            // Automatically stop scan after 15 seconds
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

    /**
     * Stop BLE scan.
     */
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

        // Filter out empty or noise names, prioritize smartwatches
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
            // Give preference to beatXP, Marv, DaFit, and MOY devices
            if (name.contains("Marv", ignoreCase = true) ||
                name.contains("beatXP", ignoreCase = true) ||
                name.contains("MOY", ignoreCase = true) ||
                name.contains("Watch", ignoreCase = true)
            ) {
                currentList.add(0, discovered)
            } else {
                currentList.add(discovered)
            }
        }
        _discoveredDevices.value = currentList
    }

    /**
     * Attempts auto-connection:
     * 1) Checks saved device address from previous successful connection.
     * 2) If no saved device, looks for bonded Marv Neo or beatXP watch.
     */
    fun autoConnect(): Boolean {
        val lastAddress = prefs.getString("last_device_address", null)
        if (!lastAddress.isNullOrBlank() && bluetoothAdapter?.isEnabled == true) {
            Log.i(tag, "Auto-connecting to previously paired device: $lastAddress")
            connect(lastAddress)
            return true
        }

        // Check bonded devices for Marv Neo / beatXP
        val bonded = bluetoothAdapter?.bondedDevices
        val watchDevice = bonded?.firstOrNull {
            val name = it.name ?: ""
            name.contains("Marv", ignoreCase = true) ||
                    name.contains("beatXP", ignoreCase = true) ||
                    name.contains("MOY", ignoreCase = true)
        }

        if (watchDevice != null) {
            Log.i(tag, "Auto-connecting to bonded watch: ${watchDevice.name} (${watchDevice.address})")
            connect(watchDevice.address)
            return true
        }

        return false
    }

    /**
     * Connect to a specific watch MAC address.
     */
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

    /**
     * Immediately disconnects the watch and stops any auto-reconnection.
     */
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

    /**
     * Clears paired device preference and disconnects.
     */
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
        delay(35) // Enforce interval between sequential GATT writes
        return success
    }

    /**
     * Flashes raw binary chunks (e.g. watchface or mini-app payload) with live progress.
     */
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
                sendCommand(chunk) // 1 retry
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
