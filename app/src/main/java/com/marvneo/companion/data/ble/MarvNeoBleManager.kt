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
import com.marvneo.companion.data.db.AppDatabase
import com.marvneo.companion.data.db.entity.BloodOxygenRecord
import com.marvneo.companion.data.db.entity.BloodPressureRecord
import com.marvneo.companion.data.db.entity.HeartRateRecord
import com.marvneo.companion.data.db.entity.StepRecord
import com.marvneo.companion.domain.model.AlarmModel
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
    private val database: AppDatabase = AppDatabase.getDatabase(context)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Discovered & Bonded Devices
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Connected device metadata
    val connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceAddress = MutableStateFlow<String?>(null)
    val lastCommunicationTimestamp = MutableStateFlow(0L)

    // Telemetry StateFlows
    val stepsFlow = MutableStateFlow(0)
    val caloriesFlow = MutableStateFlow(0)
    val distanceFlow = MutableStateFlow(0)
    val heartRateFlow = MutableStateFlow(0)
    val spO2Flow = MutableStateFlow(0)
    val bloodPressureFlow = MutableStateFlow<Pair<Int, Int>?>(null) // (systolic, diastolic)
    val batteryFlow = MutableStateFlow(100)

    // Health Measurement StateFlows
    val isMeasuringHeartRate = MutableStateFlow(false)
    val isMeasuringSpO2 = MutableStateFlow(false)
    val isMeasuringBloodPressure = MutableStateFlow(false)

    private val commandMutex = Mutex()
    private var targetDeviceAddress: String? = null
    private var reconnectAttempts = 0
    private var isUserDisconnecting = false

    private var connectionTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var keepAliveJob: Job? = null

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
            _connectionState.value = ConnectionState.ConnectionFailed("BLE Scan failed (Code $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "onConnectionStateChange: status=$status, newState=$newState")
            connectionTimeoutJob?.cancel()

            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                Log.w(tag, "GATT connection failed with status $status. Cleaning up...")
                cleanup()
                _connectionState.value = ConnectionState.Disconnected("GATT disconnected (Status $status)")
                if (!isUserDisconnecting) {
                    scheduleReconnect()
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(tag, "GATT connected. Requesting MTU 512...")
                    _connectionState.value = ConnectionState.Synchronizing("Negotiating MTU buffer...")
                    reconnectAttempts = 0
                    isUserDisconnecting = false
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(tag, "GATT disconnected cleanly. Cleaning up...")
                    cleanup()
                    _connectionState.value = ConnectionState.Disconnected("Device disconnected")
                    if (!isUserDisconnecting) {
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(tag, "MTU changed to $mtu (status: $status). Discovering GATT services...")
            _connectionState.value = ConnectionState.Synchronizing("Discovering smartwatch services...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                var foundTx: BluetoothGattCharacteristic? = null
                var foundRx: BluetoothGattCharacteristic? = null

                // Phase 1: Known Crrepa / DaFit / HunterSun UUID candidate matching
                val knownServices = listOf(
                    BleGattAttributes.CRP_FFF0_SERVICE_UUID,
                    BleGattAttributes.CRP_FEE7_SERVICE_UUID,
                    BleGattAttributes.NUS_SERVICE_UUID,
                    BleGattAttributes.HS_AE00_SERVICE_UUID,
                    BleGattAttributes.DFU_SERVICE_UUID,
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
                        Log.i(tag, "Resolved TX/RX via candidate service: $srvUuid")
                        break
                    }
                }

                // Phase 2: Dynamic fallback across all discovered non-standard services
                if (foundTx == null || foundRx == null) {
                    Log.w(tag, "Candidate service not found. Running dynamic non-standard GATT scan...")
                    for (srv in gatt.services) {
                        val srvUuidStr = srv.uuid.toString().lowercase()
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

                    _connectionState.value = ConnectionState.Synchronizing("Enabling telemetry notifications...")
                    enableNotifications(gatt, rxCharacteristic)

                    val device = gatt.device
                    val name = device.name ?: "beatXP Marv Neo"
                    connectedDeviceName.value = name
                    connectedDeviceAddress.value = device.address

                    prefs.edit()
                        .putString("last_device_address", device.address)
                        .putString("last_device_name", name)
                        .apply()

                    // Perform initial handshake before declaring full Connected state
                    scope.launch {
                        delay(300)
                        val timeSynced = sendCommand(CrrepaProtocol.createTimeSyncPacket())
                        delay(200)
                        val battQueried = sendCommand(CrrepaProtocol.createBatteryQueryPacket())
                        delay(200)
                        sendCommand(CrrepaProtocol.createStepsQueryPacket())

                        if (timeSynced || battQueried) {
                            val now = System.currentTimeMillis()
                            lastCommunicationTimestamp.value = now
                            _connectionState.value = ConnectionState.Connected(
                                deviceName = name,
                                deviceAddress = device.address,
                                lastSeenMs = now
                            )
                            startKeepAlive()
                            Log.i(tag, "GATT communication verified. Watch is genuinely connected.")
                        } else {
                            _connectionState.value = ConnectionState.ConnectionUnstable("Initial packet handshake unacknowledged")
                        }
                    }
                } else {
                    Log.e(tag, "Failed to resolve GATT TX/RX characteristics.")
                    _connectionState.value = ConnectionState.ConnectionFailed("Smartwatch data channels could not be resolved")
                    cleanup()
                }
            } else {
                Log.e(tag, "Service discovery failed with status $status")
                _connectionState.value = ConnectionState.ConnectionFailed("GATT service discovery error ($status)")
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

    fun autoConnect(): Boolean {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return false
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return false
        }

        val gattConnected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        val connectedWatch = gattConnected.firstOrNull { isSmartwatchDevice(it) }
        if (connectedWatch != null) {
            Log.i(tag, "Auto-connecting to active GATT watch: ${connectedWatch.name} (${connectedWatch.address})")
            connect(connectedWatch.address)
            return true
        }

        val lastAddress = prefs.getString("last_device_address", null)
        if (!lastAddress.isNullOrBlank()) {
            Log.i(tag, "Auto-connecting to saved address: $lastAddress")
            connect(lastAddress)
            return true
        }

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
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return
        }

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
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return
        }

        refreshBondedDevices()

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.ConnectionFailed("Bluetooth LE scanner unavailable")
            return
        }

        if (_isScanning.value) {
            stopScan()
        }

        _isScanning.value = true
        _connectionState.value = ConnectionState.SearchingForWatch

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
                if (_connectionState.value is ConnectionState.SearchingForWatch) {
                    _connectionState.value = ConnectionState.Disconnected("Scan timed out. Watch not found.")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting BLE scan", e)
            _isScanning.value = false
            _connectionState.value = ConnectionState.ConnectionFailed("Failed to start BLE scan: ${e.message}")
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
                if (_connectionState.value is ConnectionState.SearchingForWatch) {
                    _connectionState.value = ConnectionState.WatchFound(name, address, rssi)
                }
            } else {
                currentList.add(discovered)
            }
        }
        _discoveredDevices.value = currentList
    }

    fun connect(macAddress: String) {
        if (macAddress.isBlank()) return
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothDisabled
            return
        }

        targetDeviceAddress = macAddress
        isUserDisconnecting = false
        reconnectJob?.cancel()

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            Log.e(tag, "Invalid MAC format: $macAddress", e)
            _connectionState.value = ConnectionState.ConnectionFailed("Invalid MAC address: $macAddress")
            return
        }

        stopScan()
        cleanup()

        _connectionState.value = ConnectionState.Connecting("Connecting to ${device.name ?: macAddress}...")
        connectedDeviceAddress.value = macAddress
        connectedDeviceName.value = device.name ?: "beatXP Marv Neo"

        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(15_000)
            if (_connectionState.value is ConnectionState.Connecting ||
                _connectionState.value is ConnectionState.Synchronizing
            ) {
                Log.w(tag, "Connection timed out after 15s. Resetting state.")
                _connectionState.value = ConnectionState.ConnectionFailed("Connection timed out after 15s")
                cleanup()
            }
        }

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(tag, "connectGatt error", e)
            _connectionState.value = ConnectionState.ConnectionFailed("GATT link error: ${e.message}")
            cleanup()
        }
    }

    fun disconnect() {
        isUserDisconnecting = true
        targetDeviceAddress = null
        reconnectJob?.cancel()
        connectionTimeoutJob?.cancel()
        keepAliveJob?.cancel()

        _connectionState.value = ConnectionState.Disconnected("Disconnected by user")

        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            Log.e(tag, "disconnect error", e)
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
        if (success) {
            lastCommunicationTimestamp.value = System.currentTimeMillis()
        }
        delay(35)
        return success
    }

    // Health Sensor Measurement Operations
    suspend fun startHeartRateMeasurement(): Boolean {
        isMeasuringHeartRate.value = true
        val packet = CrrepaProtocol.createHeartRateControlPacket(true)
        val sent = sendCommand(packet)
        if (!sent) isMeasuringHeartRate.value = false
        return sent
    }

    suspend fun stopHeartRateMeasurement(): Boolean {
        isMeasuringHeartRate.value = false
        val packet = CrrepaProtocol.createHeartRateControlPacket(false)
        return sendCommand(packet)
    }

    suspend fun startSpO2Measurement(): Boolean {
        isMeasuringSpO2.value = true
        val packet = CrrepaProtocol.createSpO2ControlPacket(true)
        val sent = sendCommand(packet)
        if (!sent) isMeasuringSpO2.value = false
        return sent
    }

    suspend fun stopSpO2Measurement(): Boolean {
        isMeasuringSpO2.value = false
        val packet = CrrepaProtocol.createSpO2ControlPacket(false)
        return sendCommand(packet)
    }

    suspend fun startBloodPressureMeasurement(): Boolean {
        isMeasuringBloodPressure.value = true
        val packet = CrrepaProtocol.createBloodPressureControlPacket(true)
        val sent = sendCommand(packet)
        if (!sent) isMeasuringBloodPressure.value = false
        return sent
    }

    suspend fun stopBloodPressureMeasurement(): Boolean {
        isMeasuringBloodPressure.value = false
        val packet = CrrepaProtocol.createBloodPressureControlPacket(false)
        return sendCommand(packet)
    }

    suspend fun findWatch(enable: Boolean): Boolean {
        val packet = CrrepaProtocol.createFindWatchPacket(enable)
        return sendCommand(packet)
    }

    suspend fun sendAlarm(alarm: AlarmModel): Boolean {
        val packet = CrrepaProtocol.createAlarmPacket(
            id = alarm.id,
            isEnabled = alarm.isEnabled,
            repeatDaysMask = alarm.repeatDaysMask,
            hour = alarm.hour,
            minute = alarm.minute
        )
        return sendCommand(packet)
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
        val now = System.currentTimeMillis()
        lastCommunicationTimestamp.value = now

        // Check if framed packet: [0xFE, 0xEA, version, len, opcode, payload...]
        val opcode = if (data.size >= 5 && data[0] == CrrepaProtocol.MAGIC_BYTE_0 && data[1] == CrrepaProtocol.MAGIC_BYTE_1) {
            data[4]
        } else {
            data[0]
        }

        when (opcode) {
            CrrepaProtocol.CMD_QUERY_STEPS -> {
                CrrepaProtocol.parseSteps(data)?.let {
                    stepsFlow.value = it.steps
                    caloriesFlow.value = it.calories
                    distanceFlow.value = it.distanceMeters
                    scope.launch {
                        database.healthDao().insertStep(
                            StepRecord(
                                timestamp = now,
                                steps = it.steps,
                                calories = it.calories,
                                distanceMeters = it.distanceMeters
                            )
                        )
                    }
                }
            }
            CrrepaProtocol.CMD_QUERY_BATTERY -> {
                CrrepaProtocol.parseBattery(data)?.let {
                    batteryFlow.value = it.percentage
                }
            }
            CrrepaProtocol.CMD_HEART_RATE_ONCE -> {
                CrrepaProtocol.parseHeartRate(data)?.let {
                    if (it.bpm > 0) {
                        heartRateFlow.value = it.bpm
                        isMeasuringHeartRate.value = false
                        scope.launch {
                            database.healthDao().insertHeartRate(
                                HeartRateRecord(timestamp = now, bpm = it.bpm)
                            )
                        }
                    }
                }
            }
            CrrepaProtocol.CMD_BLOOD_OXYGEN -> {
                CrrepaProtocol.parseSpO2(data)?.let {
                    if (it.percentage > 0) {
                        spO2Flow.value = it.percentage
                        isMeasuringSpO2.value = false
                        scope.launch {
                            database.healthDao().insertBloodOxygen(
                                BloodOxygenRecord(timestamp = now, percentage = it.percentage)
                            )
                        }
                    }
                }
            }
            CrrepaProtocol.CMD_BLOOD_PRESSURE -> {
                CrrepaProtocol.parseBloodPressure(data)?.let {
                    if (it.systolic > 0 && it.diastolic > 0) {
                        bloodPressureFlow.value = Pair(it.systolic, it.diastolic)
                        isMeasuringBloodPressure.value = false
                        scope.launch {
                            database.healthDao().insertBloodPressure(
                                BloodPressureRecord(
                                    timestamp = now,
                                    systolic = it.systolic,
                                    diastolic = it.diastolic
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                delay(35_000) // 35s keep-alive interval
                val success = sendCommand(CrrepaProtocol.createBatteryQueryPacket())
                if (!success) {
                    val timeSinceLast = System.currentTimeMillis() - lastCommunicationTimestamp.value
                    if (timeSinceLast > 60_000) {
                        Log.w(tag, "Keep-alive ping failed. Watch communication unstable.")
                        _connectionState.value = ConnectionState.ConnectionUnstable("No packet response for >60s")
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val address = targetDeviceAddress ?: return
        if (isUserDisconnecting) return

        reconnectAttempts++
        if (reconnectAttempts > 5) {
            _connectionState.value = ConnectionState.ConnectionFailed("Reconnect limit reached (5 attempts)")
            return
        }

        val delayMs = minOf(30000L, 1000L * (1 shl reconnectAttempts))
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts, 5)

        reconnectJob = scope.launch {
            Log.i(tag, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms...")
            delay(delayMs)
            if (targetDeviceAddress != null && !isUserDisconnecting) {
                connect(address)
            }
        }
    }

    private fun cleanup() {
        keepAliveJob?.cancel()
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
