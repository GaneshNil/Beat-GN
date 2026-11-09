package com.marvneo.companion.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.marvneo.companion.data.ble.protocol.CrrepaFrameAssembler
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.StepRecord
import com.marvneo.companion.data.repository.HealthRepository
import com.marvneo.companion.domain.model.CommandOutcome
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.DiscoveredDevice
import com.marvneo.companion.domain.model.MeasurementState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single owner of the BLE link with the watch.
 *
 * Rules enforced here:
 *  1. **Serialised GATT operations.** Android allows one outstanding GATT request per connection.
 *     Every write/read/descriptor-write goes through [runGattOp] and waits for its callback.
 *  2. **No invented state.** [ConnectionState.Ready] is published only after service discovery,
 *     notification subscription *and* a real application-level round trip (a battery read that
 *     returned a plausible value). A successful `connectGatt()` call proves nothing.
 *  3. **Chunked reassembly.** Inbound `FE EA` packets may arrive split across notifications; they
 *     are reassembled before parsing, and only complete, valid frames become data.
 *  4. **Measurements are never fabricated.** Heart rate comes from the standard 0x2A37
 *     characteristic, SpO2 from opcode 0x6B and blood pressure from opcode 0x69. If the watch
 *     never sends a reading, the state stays "not supported" / "timed out".
 */
@SuppressLint("MissingPermission")
class MarvNeoBleManager(
    private val context: Context,
    private val healthRepository: HealthRepository,
    private val healthDao: HealthDao
) {

    companion object {
        private const val TAG = "WatchBleManager"
        private const val MTU_REQUEST = 247
        private const val GATT_OP_TIMEOUT_MS = 5_000L
        private const val HANDSHAKE_TIMEOUT_MS = 6_000L
        private const val MEASUREMENT_TIMEOUT_MS = 40_000L
        private const val SPO2_TIMEOUT_MS = 45_000L
        private const val BP_TIMEOUT_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val SCAN_DURATION_MS = 20_000L
        private const val DIAGNOSTIC_LIMIT = 250
        private const val POST_DISCOVERY_SETTLE_MS = 300L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var gattGeneration = 0

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var packetCharacteristic: BluetoothGattCharacteristic? = null
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null
    private var stepCharacteristic: BluetoothGattCharacteristic? = null
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null

    private var targetAddress: String? = null
    private var userDisconnected = false
    private var reconnectAttempt = 0

    private var scanCallbackActive = false
    private var scanTimeoutJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null

    // ---- Observable connection state ----
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    val connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceAddress = MutableStateFlow<String?>(null)
    val lastCommunicationTimestamp = MutableStateFlow(0L)
    val negotiatedMtu = MutableStateFlow(0)
    val firmwareVersion = MutableStateFlow<String?>(null)
    val modelNumber = MutableStateFlow<String?>(null)
    val manufacturerName = MutableStateFlow<String?>(null)

    // ---- Telemetry (0 = nothing received yet; never seeded with sample data) ----
    val stepsFlow = MutableStateFlow(0)
    val caloriesFlow = MutableStateFlow(0)
    val distanceFlow = MutableStateFlow(0)
    val heartRateFlow = MutableStateFlow(0)
    val spO2Flow = MutableStateFlow(0)
    val bloodPressureFlow = MutableStateFlow<Pair<Int, Int>?>(null)
    val batteryFlow = MutableStateFlow(0)
    val isChargingFlow = MutableStateFlow(false)
    val batteryKnown = MutableStateFlow(false)

    // ---- Measurements ----
    val heartRateMeasurement = MutableStateFlow<MeasurementState>(MeasurementState.Idle)
    val spo2Measurement = MutableStateFlow<MeasurementState>(MeasurementState.Idle)
    val bloodPressureMeasurement = MutableStateFlow<MeasurementState>(MeasurementState.Idle)

    /** True only once this install has received at least one real BP reading. */
    val bloodPressureEverReported = MutableStateFlow(false)

    // ---- Commands ----
    val findWatchOutcome = MutableStateFlow<CommandOutcome>(CommandOutcome.Idle)
    val alarmSyncOutcome = MutableStateFlow<CommandOutcome>(CommandOutcome.Idle)

    // ---- Diagnostics (the evidence trail shown in the app) ----
    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private var alarmReplyDeferred: CompletableDeferred<CrrepaProtocol.Frame>? = null

    private val gattOpMutex = Mutex()
    private var pendingGattOp: CompletableDeferred<Boolean>? = null

    private enum class MeasurementKind { HEART_RATE, SPO2, BLOOD_PRESSURE }

    /**
     * One timeout job per measurement. A single shared job used to let starting Pulse cancel the
     * Oxygen timeout (and vice versa), leaving a request "Measuring…" forever.
     */
    private val measurementTimeouts = ConcurrentHashMap<MeasurementKind, Job>()

    /** Chunked inbound packet reassembly, mirroring `com.crrepa.l.a#n0`. */
    private val rxAssembler = CrrepaFrameAssembler()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan failed (error $errorCode)")
            _isScanning.value = false
            _connectionState.value = ConnectionState.Failed("Bluetooth scan failed (code $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        /**
         * A late callback from a closed connection must never release the single-operation mutex
         * or feed data into the current session. Every callback below starts with this guard.
         */
        private fun isCurrent(gatt: BluetoothGatt, what: String): Boolean {
            if (gatt === this@MarvNeoBleManager.gatt) return true
            log("Ignoring stale $what callback from an earlier GATT connection")
            runCatching { gatt.close() }
            return false
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrent(gatt, "connection-state")) return
            log("onConnectionStateChange status=$status newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeGattOp(false)
                handleLinkLost("GATT error $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectTimeoutJob?.cancel()
                    _connectionState.value = ConnectionState.DiscoveringServices(gatt.device.address)
                    scope.launch { initialiseConnection() }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    completeGattOp(false)
                    handleLinkLost("Disconnected by device")
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrent(gatt, "MTU")) return
            val effective = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            negotiatedMtu.value = effective
            log("MTU now $effective (status $status)")
            completeGattOp(true)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrent(gatt, "service-discovery")) return
            log("onServicesDiscovered status=$status (${gatt.services.size} services)")
            completeGattOp(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (!isCurrent(gatt, "descriptor-write")) return
            log("CCCD write ${descriptor.characteristic.uuid} status=$status")
            completeGattOp(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (!isCurrent(gatt, "characteristic-write")) return
            completeGattOp(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (!isCurrent(gatt, "characteristic-read")) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleReadValue(characteristic, value)
                completeGattOp(true)
            } else {
                log("Read ${characteristic.uuid} failed status=$status")
                completeGattOp(false)
            }
        }

        /**
         * Android 11/12 (API 30-32) deliver reads through this three-argument callback. The
         * four-argument overload only exists from API 33, but `minSdk` is 30: without this override
         * reads never complete and the link could never reach `Ready`.
         */
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (!isCurrent(gatt, "legacy-characteristic-read")) return
            @Suppress("DEPRECATION")
            val value = characteristic.value
            if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
                handleReadValue(characteristic, value)
                completeGattOp(true)
            } else {
                log("Read ${characteristic.uuid} failed status=$status")
                completeGattOp(false)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (!isCurrent(gatt, "notification")) return
            handleNotification(characteristic, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (!isCurrent(gatt, "legacy-notification")) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { handleNotification(characteristic, it) }
        }
    }

    // ------------------------------------------------------------------ permissions

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------------ lifecycle

    fun refreshAdapterState() {
        val enabled = adapter?.isEnabled == true
        _bluetoothEnabled.value = enabled
        if (!enabled && _connectionState.value !is ConnectionState.Ready) {
            _connectionState.value = ConnectionState.BluetoothOff
        }
    }

    fun onBluetoothTurnedOff() {
        log("Bluetooth turned off")
        stopScan()
        userDisconnected = true
        closeGatt()
        _connectionState.value = ConnectionState.BluetoothOff
        _bluetoothEnabled.value = false
    }

    fun onBluetoothTurnedOn() {
        _bluetoothEnabled.value = true
        if (_connectionState.value is ConnectionState.BluetoothOff) {
            _connectionState.value = ConnectionState.Idle
        }
        userDisconnected = false
        autoConnect()
    }

    // ------------------------------------------------------------------ scanning

    fun startScan() {
        if (!hasPermissions()) {
            _connectionState.value =
                ConnectionState.Failed("Bluetooth permission not granted", retryable = false)
            return
        }
        val adapter = adapter ?: run {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _connectionState.value = ConnectionState.Failed("BLE scanner unavailable", retryable = false)
            return
        }

        if (_isScanning.value) stopScan()

        // Scanning is an explicit user action; it clears a previous intentional disconnect.
        userDisconnected = false
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _connectionState.value = ConnectionState.Scanning()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            scanCallbackActive = true
            log("BLE scan started")
        } catch (e: SecurityException) {
            _isScanning.value = false
            _connectionState.value = ConnectionState.Failed("Bluetooth permission revoked", retryable = false)
            return
        } catch (e: Exception) {
            _isScanning.value = false
            _connectionState.value = ConnectionState.Failed("Could not start scan: ${e.message}")
            return
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_DURATION_MS)
            val wasScanning = _connectionState.value is ConnectionState.Scanning ||
                _connectionState.value is ConnectionState.WatchFound
            stopScan()
            if (wasScanning) log("Scan finished without connecting")
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        if (scanCallbackActive) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            scanCallbackActive = false
        }
        _isScanning.value = false
        // Leaving the state at Scanning after "Stop scan" made the UI claim an active scan.
        if (_connectionState.value is ConnectionState.Scanning ||
            _connectionState.value is ConnectionState.WatchFound
        ) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
        if (name.isNullOrBlank()) return

        val record = DiscoveredDevice(
            name = name,
            address = device.address,
            rssi = result.rssi,
            isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
            likelyWatch = looksLikeWatch(name, device)
        )

        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.address == record.address }
        if (index >= 0) current[index] = record else current.add(record)
        current.sortWith(
            compareByDescending<DiscoveredDevice> { it.likelyWatch }
                .thenByDescending { it.isBonded }
                .thenByDescending { it.rssi }
        )
        _discoveredDevices.value = current

        if (record.likelyWatch && _connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.WatchFound(name, device.address, result.rssi)
        }
    }

    /** Name/class heuristics. Used only to sort and highlight scan results. */
    fun looksLikeWatch(name: String, device: BluetoothDevice? = null): Boolean {
        val normalized = name.lowercase()
        val nameMatch = listOf("marv", "beatxp", "beat xp", "moy", "watch", "band", "smart")
            .any { normalized.contains(it) }
        if (nameMatch) return true
        val major = runCatching { device?.bluetoothClass?.majorDeviceClass }.getOrNull()
        return major == android.bluetooth.BluetoothClass.Device.Major.WEARABLE
    }

    fun lastKnownAddress(): String? =
        prefs().getString("last_device_address", null)

    fun lastKnownName(): String? =
        prefs().getString("last_device_name", null)

    private fun prefs() = context.getSharedPreferences("marv_neo_prefs", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ connection

    /** Connects to the last known watch, or to a bonded watch, without scanning. */
    fun autoConnect(): Boolean {
        // An explicit Disconnect must survive the service being restarted or the app returning to
        // the foreground. Only a user action (scan or [reconnect]) clears this flag.
        if (userDisconnected) return false
        if (!hasPermissions()) return false
        val adapter = adapter ?: return false
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothOff
            return false
        }
        if (_connectionState.value.inProgress()) return true

        val saved = lastKnownAddress()
        if (!saved.isNullOrBlank()) {
            connect(saved)
            return true
        }

        val bonded = runCatching {
            adapter.bondedDevices?.firstOrNull { looksLikeWatch(it.name ?: "", it) }
        }.getOrNull()
        if (bonded != null) {
            connect(bonded.address)
            return true
        }
        return false
    }

    /**
     * User-initiated reconnect (the Watch screen's "Reconnect" button). Unlike [autoConnect] it
     * explicitly overrides a previous Disconnect.
     */
    fun reconnect(): Boolean {
        userDisconnected = false
        return autoConnect()
    }

    private fun ConnectionState.inProgress(): Boolean = when (this) {
        is ConnectionState.Ready,
        is ConnectionState.Connecting,
        is ConnectionState.DiscoveringServices,
        is ConnectionState.Subscribing,
        is ConnectionState.VerifyingLink -> true
        else -> false
    }

    fun connect(address: String) {
        connectInternal(address, isReconnect = false)
    }

    /**
     * @param isReconnect true only for a retry scheduled by [scheduleReconnect]. A scheduled retry
     * must not reset [reconnectAttempt], otherwise `MAX_RECONNECT_ATTEMPTS` is never reached.
     */
    private fun connectInternal(address: String, isReconnect: Boolean) {
        if (address.isBlank()) return
        if (!hasPermissions()) {
            _connectionState.value =
                ConnectionState.Failed("Bluetooth permission not granted", retryable = false)
            return
        }
        val adapter = adapter ?: run {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }
        if (!adapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Failed("Invalid Bluetooth address", retryable = false)
            return
        }

        if (!isReconnect &&
            address.equals(connectedDeviceAddress.value, ignoreCase = true) && gatt != null
        ) {
            log("Already handling $address; ignoring duplicate connect")
            return
        }

        stopScan()
        userDisconnected = false
        targetAddress = address
        if (!isReconnect) reconnectAttempt = 0
        reconnectJob?.cancel()

        closeGatt()
        // Verification must be earned by this session, not inherited from a previous one.
        batteryKnown.value = false
        batteryFlow.value = 0
        val generation = ++gattGeneration

        val name = runCatching { device.name }.getOrNull() ?: "Smartwatch"
        connectedDeviceName.value = name
        connectedDeviceAddress.value = address
        _connectionState.value = ConnectionState.Connecting(name, address)
        log("Connecting to $name (${redactAddress(address)})")

        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(15_000)
            if (generation == gattGeneration && _connectionState.value !is ConnectionState.Ready) {
                log("Connect timed out")
                handleLinkLost("Connection timed out")
            }
        }

        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            gatt = null
            _connectionState.value = ConnectionState.Failed("Bluetooth permission revoked", retryable = false)
        } catch (e: Exception) {
            gatt = null
            _connectionState.value = ConnectionState.Failed("Could not open GATT link: ${e.message}")
        }
    }

    fun disconnect() {
        userDisconnected = true
        targetAddress = null
        reconnectJob?.cancel()
        connectTimeoutJob?.cancel()
        heartbeatJob?.cancel()
        stopScan()
        log("User disconnected")
        runCatching { gatt?.disconnect() }
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun forgetDevice() {
        prefs().edit().remove("last_device_address").remove("last_device_name").apply()
        disconnect()
    }

    private suspend fun initialiseConnection() {
        val gatt = gatt ?: return
        val generation = gattGeneration

        // 1. MTU. A failure here is not fatal; 23-byte packets still work.
        runGattOp(GATT_OP_TIMEOUT_MS) { gatt.requestMtu(MTU_REQUEST) }
        if (generation != gattGeneration) return
        // The reference SDK waits before discovery after a connection is established.
        delay(POST_DISCOVERY_SETTLE_MS)

        // 2. Service discovery.
        _connectionState.value = ConnectionState.DiscoveringServices(gatt.device.address)
        val discovered = runGattOp(GATT_OP_TIMEOUT_MS) { gatt.discoverServices() }
        if (!discovered) {
            _connectionState.value = ConnectionState.Failed("Service discovery failed")
            handleLinkLost("Service discovery failed")
            return
        }
        if (generation != gattGeneration) return

        // 3. Resolve the Crrepa data channel.
        val service = gatt.getService(BleGattAttributes.CRP_SERVICE_FEEA)
        if (service == null) {
            log("Device does not expose the Crrepa FEEA service")
            _connectionState.value = ConnectionState.Failed(
                "Not a supported watch: the Crrepa data service (FEEA) is missing",
                retryable = false
            )
            closeGatt()
            return
        }

        fun char(uuid: java.util.UUID) = service.getCharacteristic(uuid)

        val fee1 = char(BleGattAttributes.CRP_CHAR_FEE1)
        val fee2 = char(BleGattAttributes.CRP_CHAR_FEE2)
        val fee3 = char(BleGattAttributes.CRP_CHAR_FEE3)

        if (fee1 == null || fee2 == null || fee3 == null) {
            // The vendor SDK treats a device as valid only when FEE1, FEE2 and FEE3 all exist.
            log("FEEA present but FEE1/FEE2/FEE3 incomplete")
            _connectionState.value = ConnectionState.Failed(
                "Watch data channel incomplete (FEE1/FEE2/FEE3)",
                retryable = false
            )
            closeGatt()
            return
        }

        writeCharacteristic = fee2
        packetCharacteristic = fee3
        stepCharacteristic = fee1
        heartRateCharacteristic = gatt
            .getService(BleGattAttributes.HEART_RATE_SERVICE)
            ?.getCharacteristic(BleGattAttributes.HEART_RATE_MEASUREMENT_CHAR)
        batteryCharacteristic = gatt
            .getService(BleGattAttributes.BATTERY_SERVICE)
            ?.getCharacteristic(BleGattAttributes.BATTERY_LEVEL_CHAR)

        // 4. Subscribe to the characteristics the vendor SDK subscribes to.
        val subscriptions = listOfNotNull(fee3, heartRateCharacteristic, fee1)
        for (characteristic in subscriptions) {
            _connectionState.value = ConnectionState.Subscribing(
                gatt.device.address,
                characteristic.uuid.toString()
            )
            val ok = enableNotifications(gatt, characteristic)
            if (!ok) {
                log("Could not subscribe to ${characteristic.uuid}")
            }
            if (generation != gattGeneration) return
        }

        if (packetCharacteristic?.let { startedNotify(it) } != true) {
            _connectionState.value = ConnectionState.Failed(
                "Could not subscribe to the watch data channel",
                retryable = true
            )
            handleLinkLost("Packet channel subscribe failed")
            return
        }

        // 5. Application-level verification: read the standard battery characteristic.
        _connectionState.value = ConnectionState.VerifyingLink(gatt.device.address)
        val verified = verifyLink()
        if (generation != gattGeneration) return

        if (!verified) {
            log("Handshake read did not confirm the link")
            _connectionState.value = ConnectionState.Unstable(
                reason = "Connected, but the watch did not answer a read request",
                lastVerifiedAt = null,
                connected = true
            )
            scope.launch {
                repeat(3) {
                    delay(4_000)
                    if (_connectionState.value is ConnectionState.Ready) return@launch
                    if (verifyLink()) {
                        publishReady(gatt)
                        return@launch
                    }
                }
                // Keep trying at a bounded cadence instead of giving up on an Unstable link.
                if (_connectionState.value is ConnectionState.Unstable) startHeartbeat()
            }
            return
        }

        publishReady(gatt)
    }

    /** Publishes the only state that may be called "connected": a verified round trip. */
    private fun publishReady(gatt: BluetoothGatt) {
        val now = System.currentTimeMillis()
        lastCommunicationTimestamp.value = now
        reconnectAttempt = 0
        val name = connectedDeviceName.value ?: "Smartwatch"
        val address = connectedDeviceAddress.value ?: gatt.device.address
        prefs().edit()
            .putString("last_device_address", address)
            .putString("last_device_name", name)
            .apply()

        _connectionState.value = ConnectionState.Ready(
            deviceName = name,
            deviceAddress = address,
            protocolName = "Crrepa FEEA (0x${CrrepaProtocol.VERSION_1.toString(16)})",
            mtu = negotiatedMtu.value.coerceAtLeast(23),
            lastVerifiedAt = now
        )
        log("Link verified by battery read; state=Ready")

        startHeartbeat()
        syncTime()
        readDeviceInformation()
        readBattery()
    }

    private val notifyConfirmed: MutableSet<java.util.UUID> =
        ConcurrentHashMap.newKeySet()

    private fun startedNotify(c: BluetoothGattCharacteristic): Boolean =
        notifyConfirmed.contains(c.uuid)

    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            log("setCharacteristicNotification() returned false for ${characteristic.uuid}")
            return false
        }
        val cccd = characteristic.getDescriptor(BleGattAttributes.CCCD)
        if (cccd == null) {
            log("No CCCD on ${characteristic.uuid}")
            return false
        }

        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        repeat(2) { attempt ->
            val ok = runGattOp(GATT_OP_TIMEOUT_MS) {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = value.copyOf()
                    gatt.writeDescriptor(cccd)
                }
            }
            if (ok) {
                notifyConfirmed.add(characteristic.uuid)
                return true
            }
            if (attempt == 0) delay(200)
        }
        return false
    }

    /**
     * Requires a real read of the battery characteristic. A device that answers this has a live,
     * bidirectional link; one that does not is reported as unstable rather than "connected".
     */
    private suspend fun verifyLink(): Boolean {
        val gatt = gatt ?: return false
        val battery = batteryCharacteristic ?: run {
            log("No battery characteristic available for verification")
            return false
        }
        val ok = runGattOp(HANDSHAKE_TIMEOUT_MS) { gatt.readCharacteristic(battery) }
        if (!ok) return false
        return batteryKnown.value
    }

    /**
     * Reads device-information characteristics. These are standard GATT reads; each one that
     * succeeds is displayed, each one that fails stays null and is shown as "not reported".
     */
    private fun readDeviceInformation() {
        scope.launch {
            val gatt = gatt ?: return@launch
            val info = gatt.getService(BleGattAttributes.DEVICE_INFO_SERVICE) ?: return@launch
            val reads = listOf(
                BleGattAttributes.FIRMWARE_REVISION_CHAR to { v: ByteArray ->
                    firmwareVersion.value = CrrepaProtocol.decodeFirmware(v)
                },
                BleGattAttributes.MODEL_NUMBER_CHAR to { v: ByteArray ->
                    modelNumber.value = CrrepaProtocol.decodeFirmware(v)
                },
                BleGattAttributes.MANUFACTURER_NAME_CHAR to { v: ByteArray ->
                    manufacturerName.value = CrrepaProtocol.decodeFirmware(v)
                }
            )
            for ((uuid, apply) in reads) {
                val characteristic = info.getCharacteristic(uuid) ?: continue
                readTarget = apply
                val ok = runGattOp(GATT_OP_TIMEOUT_MS) { gatt.readCharacteristic(characteristic) }
                readTarget = null
                if (!ok) log("Could not read $uuid")
                delay(120)
            }
        }
    }

    private var readTarget: ((ByteArray) -> Unit)? = null

    private fun handleReadValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        log("RX read ${characteristic.uuid} = ${value.toHex()}")
        when (characteristic.uuid) {
            BleGattAttributes.BATTERY_LEVEL_CHAR -> {
                val percent = value.firstOrNull()?.toInt()?.and(0xFF)
                if (percent != null && percent in 0..100) {
                    batteryFlow.value = percent
                    batteryKnown.value = true
                    lastCommunicationTimestamp.value = System.currentTimeMillis()
                    log("Battery read: $percent%")
                } else {
                    log("Battery read returned an implausible value")
                }
            }

            else -> {
                readTarget?.invoke(value)
                readTarget = null
            }
        }
    }

    private fun handleLinkLost(reason: String) {
        log("Link lost: $reason")
        connectTimeoutJob?.cancel()
        heartbeatJob?.cancel()
        resetMeasurementStates("Link lost during measurement")
        notifyConfirmed.clear()
        val shouldReconnect = !userDisconnected && targetAddress != null
        closeGatt()
        _connectionState.value = if (shouldReconnect) {
            ConnectionState.Reconnecting(reconnectAttempt + 1, MAX_RECONNECT_ATTEMPTS)
        } else {
            ConnectionState.Disconnected
        }
        if (shouldReconnect) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val address = targetAddress ?: return
        if (userDisconnected) return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.Failed(
                "Could not reconnect after $MAX_RECONNECT_ATTEMPTS attempts"
            )
            return
        }
        reconnectAttempt++
        val delayMs = minOf(30_000L, 1_000L * (1 shl reconnectAttempt))
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS)
        log("Reconnect attempt $reconnectAttempt in ${delayMs}ms")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userDisconnected && targetAddress != null) {
                connectInternal(address, isReconnect = true)
            }
        }
    }

    private fun closeGatt() {
        gattGeneration++
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
        packetCharacteristic = null
        heartRateCharacteristic = null
        stepCharacteristic = null
        batteryCharacteristic = null
        readTarget = null
        notifyConfirmed.clear()
        pendingGattOp?.complete(false)
        pendingGattOp = null
        negotiatedMtu.value = 0
        rxAssembler.reset()

        // Live telemetry belongs to a link. Keeping the last values after a disconnect made the
        // Home screen present stale measurements as "live". Historical rows stay in Room.
        stepsFlow.value = 0
        caloriesFlow.value = 0
        distanceFlow.value = 0
        heartRateFlow.value = 0
        spO2Flow.value = 0
        bloodPressureFlow.value = null
        batteryFlow.value = 0
        batteryKnown.value = false
        isChargingFlow.value = false
        firmwareVersion.value = null
        modelNumber.value = null
        manufacturerName.value = null
        lastCommunicationTimestamp.value = 0
        lastStoredSteps = -1
    }

    /** Diagnostics keep only a short suffix; the full MAC is shown on the Device information page. */
    private fun redactAddress(address: String): String =
        if (address.length <= 5) "…" else "…${address.takeLast(5)}"

    // ------------------------------------------------------------------ GATT operation queue

    /**
     * Runs one GATT request at a time and waits for its callback.
     * Returns false when the request could not be started, failed, or timed out.
     */
    private suspend fun runGattOp(timeoutMs: Long, block: () -> Boolean): Boolean =
        gattOpMutex.withLock {
            if (gatt == null) return false
            val deferred = CompletableDeferred<Boolean>()
            pendingGattOp = deferred
            val started = try {
                block()
            } catch (e: SecurityException) {
                log("GATT operation denied: ${e.message}")
                false
            } catch (e: Exception) {
                log("GATT operation threw: ${e.message}")
                false
            }
            if (!started) {
                pendingGattOp = null
                return false
            }
            val result = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
            pendingGattOp = null
            result
        }

    private fun completeGattOp(success: Boolean) {
        pendingGattOp?.complete(success)
    }

    // ------------------------------------------------------------------ outbound

    private suspend fun writePacket(packet: ByteArray): Boolean {
        val gatt = gatt ?: return false
        val characteristic = writeCharacteristic ?: run {
            log("No write characteristic resolved")
            return false
        }

        val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        val ok = runGattOp(GATT_OP_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, packet, writeType) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = writeType
                    characteristic.value = packet
                    gatt.writeCharacteristic(characteristic)
                }
            }
        }
        if (ok) log("TX ${packet.toHex()}") else log("TX failed ${packet.toHex()}")
        return ok
    }

    // ------------------------------------------------------------------ inbound

    /**
     * Feeds a notification into the reassembly buffer, then dispatches every complete frame.
     * Mirrors `com.crrepa.l.a#n0` + `#w0`.
     */
    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (value.isEmpty()) return
        val uuid = characteristic.uuid

        when (uuid) {
            BleGattAttributes.HEART_RATE_MEASUREMENT_CHAR -> {
                CrrepaProtocol.parseHeartRateMeasurement(value)?.let { bpm ->
                    onHeartRate(bpm)
                } ?: log("Unparsable heart-rate measurement ${value.toHex()}")
                return
            }

            BleGattAttributes.CRP_CHAR_FEE1 -> {
                CrrepaProtocol.parseSteps(value)?.let(::onSteps)
                    ?: log("Unparsable step notification ${value.toHex()}")
                return
            }

            BleGattAttributes.BATTERY_LEVEL_CHAR -> {
                val percent = value.firstOrNull()?.toInt()?.and(0xFF) ?: return
                if (percent in 0..100) {
                    batteryFlow.value = percent
                    batteryKnown.value = true
                    lastCommunicationTimestamp.value = System.currentTimeMillis()
                }
                return
            }
        }

        // Everything else on the data service is a framed packet.
        appendAndDrain(value)
    }

    private fun appendAndDrain(chunk: ByteArray) {
        val frames = rxAssembler.append(chunk)
        if (frames.isEmpty()) return
        lastCommunicationTimestamp.value = System.currentTimeMillis()
        frames.forEach(::dispatchFrame)
    }

    private fun dispatchFrame(frame: CrrepaProtocol.Frame) {
        log("RX op=0x${"%02X".format(frame.opcode)} ${frame.payload.toHex()}")

        when (frame.opcode) {
            CrrepaProtocol.CMD_BLOOD_OXYGEN -> {
                CrrepaProtocol.parseBloodOxygen(frame.payload)?.let { spo2 ->
                    spO2Flow.value = spo2.percentage
                    completeMeasurement(
                        MeasurementKind.SPO2,
                        spo2Measurement,
                        MeasurementState.Completed(spo2.percentage)
                    )
                    scope.launch { healthRepository.recordBloodOxygen(spo2.percentage) }
                } ?: log("Ignoring implausible SpO2 payload")
            }

            CrrepaProtocol.CMD_BLOOD_PRESSURE -> {
                CrrepaProtocol.parseBloodPressure(frame.payload)?.let { bp ->
                    if (bp.systolic in 60..260 && bp.diastolic in 30..160 &&
                        bp.systolic > bp.diastolic
                    ) {
                        bloodPressureEverReported.value = true
                        bloodPressureFlow.value = bp.systolic to bp.diastolic
                        completeMeasurement(
                            MeasurementKind.BLOOD_PRESSURE,
                            bloodPressureMeasurement,
                            MeasurementState.Completed(bp.systolic, bp.diastolic)
                        )
                        scope.launch {
                            healthRepository.recordBloodPressure(bp.systolic, bp.diastolic)
                        }
                    } else {
                        log("Ignoring implausible blood pressure ${bp.systolic}/${bp.diastolic}")
                    }
                } ?: log("Blood-pressure payload was not the documented 3-byte form")
            }

            CrrepaProtocol.CMD_HEART_RATE_ONCE -> {
                // This reply is a completion state; the BPM itself arrives on 0x2A37.
                val state = CrrepaProtocol.parseHeartRateState(frame.payload)
                log("Heart-rate measurement state=${state?.state}")
                if (state != null && state.state == 0xFF) {
                    if (heartRateMeasurement.value is MeasurementState.InProgress) {
                        heartRateMeasurement.value =
                            MeasurementState.TimedOut("The watch reported no pulse signal")
                    }
                }
            }

            CrrepaProtocol.CMD_QUERY_NEW_ALARMS -> {
                alarmReplyDeferred?.complete(frame)
                alarmReplyDeferred = null
            }
        }
    }

    private fun onHeartRate(bpm: Int) {
        if (bpm !in 25..250) {
            log("Ignoring implausible heart rate $bpm")
            return
        }
        heartRateFlow.value = bpm
        lastCommunicationTimestamp.value = System.currentTimeMillis()
        if (heartRateMeasurement.value is MeasurementState.InProgress ||
            heartRateMeasurement.value is MeasurementState.Requesting
        ) {
            completeMeasurement(
                MeasurementKind.HEART_RATE,
                heartRateMeasurement,
                MeasurementState.Completed(bpm)
            )
        }
        scope.launch { healthRepository.recordHeartRate(bpm) }
    }

    private fun onSteps(steps: CrrepaProtocol.Steps) {
        // Only update a field the watch actually reported; never write a fabricated zero.
        steps.steps?.let { stepsFlow.value = it }
        steps.distanceMeters?.let { distanceFlow.value = it }
        steps.calories?.let { caloriesFlow.value = it }
        lastCommunicationTimestamp.value = System.currentTimeMillis()

        // A persisted snapshot needs steps, distance and calories together. A 3- or 6-byte
        // notification (or an all-0xFF field) updates the live value but is not a full snapshot.
        val stepCount = steps.steps ?: return
        val distance = steps.distanceMeters ?: return
        val calories = steps.calories ?: return
        if (stepCount == lastStoredSteps) return
        lastStoredSteps = stepCount
        scope.launch {
            healthDao.insertStep(
                StepRecord(
                    timestamp = System.currentTimeMillis(),
                    steps = stepCount,
                    calories = calories,
                    distanceMeters = distance
                )
            )
            healthRepository.requestRefresh()
        }
    }

    @Volatile
    private var lastStoredSteps = -1

    private fun completeMeasurement(
        kind: MeasurementKind,
        target: MutableStateFlow<MeasurementState>,
        state: MeasurementState
    ) {
        cancelMeasurementTimeout(kind)
        target.value = state
    }

    private fun resetMeasurementStates(reason: String) {
        listOf(heartRateMeasurement, spo2Measurement, bloodPressureMeasurement).forEach { flow ->
            if (flow.value is MeasurementState.InProgress || flow.value is MeasurementState.Requesting) {
                flow.value = MeasurementState.Failed(reason)
            }
        }
        MeasurementKind.values().forEach(::cancelMeasurementTimeout)
    }

    // ------------------------------------------------------------------ public commands

    fun syncTime() {
        scope.launch {
            if (writePacket(CrrepaProtocol.timeSync())) log("Time sync sent")
        }
    }

    fun readBattery() {
        scope.launch {
            val gatt = gatt ?: return@launch
            val characteristic = batteryCharacteristic ?: return@launch
            runGattOp(GATT_OP_TIMEOUT_MS) { gatt.readCharacteristic(characteristic) }
        }
    }

    suspend fun startHeartRateMeasurement() {
        if (_connectionState.value !is ConnectionState.Ready) {
            heartRateMeasurement.value = MeasurementState.Failed("Watch is not ready")
            return
        }
        heartRateMeasurement.value = MeasurementState.Requesting
        if (!writePacket(CrrepaProtocol.heartRateMeasurement(true))) {
            heartRateMeasurement.value = MeasurementState.Failed("Could not send measurement request")
            return
        }
        heartRateMeasurement.value = MeasurementState.InProgress(System.currentTimeMillis())
        armMeasurementTimeout(
            MeasurementKind.HEART_RATE,
            heartRateMeasurement,
            MEASUREMENT_TIMEOUT_MS
        ) {
            if (_connectionState.value is ConnectionState.Ready) {
                writePacket(CrrepaProtocol.heartRateMeasurement(false))
            }
        }
    }

    suspend fun stopHeartRateMeasurement() {
        cancelMeasurementTimeout(MeasurementKind.HEART_RATE)
        heartRateMeasurement.value = MeasurementState.Idle
        writePacket(CrrepaProtocol.heartRateMeasurement(false))
    }

    suspend fun startSpO2Measurement() {
        if (_connectionState.value !is ConnectionState.Ready) {
            spo2Measurement.value = MeasurementState.Failed("Watch is not ready")
            return
        }
        spo2Measurement.value = MeasurementState.Requesting
        if (!writePacket(CrrepaProtocol.bloodOxygenMeasurement(true))) {
            spo2Measurement.value = MeasurementState.Failed("Could not send measurement request")
            return
        }
        spo2Measurement.value = MeasurementState.InProgress(System.currentTimeMillis())
        armMeasurementTimeout(MeasurementKind.SPO2, spo2Measurement, SPO2_TIMEOUT_MS) {
            if (_connectionState.value is ConnectionState.Ready) {
                writePacket(CrrepaProtocol.bloodOxygenMeasurement(false))
            }
        }
    }

    suspend fun stopSpO2Measurement() {
        cancelMeasurementTimeout(MeasurementKind.SPO2)
        spo2Measurement.value = MeasurementState.Idle
        writePacket(CrrepaProtocol.bloodOxygenMeasurement(false))
    }

    /**
     * Probes whether this watch reports blood pressure. A reading is shown only if the watch
     * actually sends opcode 0x69 with a 3-byte payload; otherwise the state becomes
     * [MeasurementState.NotSupported].
     */
    suspend fun probeBloodPressure() {
        if (_connectionState.value !is ConnectionState.Ready) {
            bloodPressureMeasurement.value = MeasurementState.Failed("Watch is not ready")
            return
        }
        bloodPressureMeasurement.value = MeasurementState.Requesting
        if (!writePacket(CrrepaProtocol.bloodPressureMeasurement(true))) {
            bloodPressureMeasurement.value = MeasurementState.Failed("Could not send request")
            return
        }
        bloodPressureMeasurement.value = MeasurementState.InProgress(System.currentTimeMillis())
        armMeasurementTimeout(
            MeasurementKind.BLOOD_PRESSURE,
            bloodPressureMeasurement,
            BP_TIMEOUT_MS
        ) {
            bloodPressureMeasurement.value = MeasurementState.NotSupported
            log("No blood-pressure response within ${BP_TIMEOUT_MS / 1000}s; marking unsupported")
            if (_connectionState.value is ConnectionState.Ready) {
                writePacket(CrrepaProtocol.bloodPressureMeasurement(false))
            }
        }
    }

    private fun armMeasurementTimeout(
        kind: MeasurementKind,
        target: MutableStateFlow<MeasurementState>,
        timeoutMs: Long,
        onTimeout: suspend () -> Unit = {}
    ) {
        cancelMeasurementTimeout(kind)
        val job = scope.launch {
            delay(timeoutMs)
            val state = target.value
            if (state is MeasurementState.InProgress || state is MeasurementState.Requesting) {
                target.value = MeasurementState.TimedOut(
                    "No reading received within ${timeoutMs / 1000}s"
                )
                log("Measurement ${kind.name.lowercase()} timed out")
                onTimeout()
            }
            coroutineContext[Job]?.let { measurementTimeouts.remove(kind, it) }
        }
        measurementTimeouts[kind] = job
    }

    private fun cancelMeasurementTimeout(kind: MeasurementKind) {
        measurementTimeouts.remove(kind)?.cancel()
    }

    /**
     * Triggers the watch's find-me alert.
     *
     * The vendor protocol defines **no reply** for `findDevice` (`com.crrepa.g.a#findDevice` is a
     * single `Q(97, null)` write and `com.crrepa.l.a#w0` has no case for 0x61). The outcome is
     * therefore reported as [CommandOutcome.AwaitingAcknowledgement] — "sent, unconfirmed" — and
     * never as acknowledged.
     */
    suspend fun findWatch(): CommandOutcome {
        if (_connectionState.value !is ConnectionState.Ready) {
            findWatchOutcome.value = CommandOutcome.Failed("Watch is not ready")
            return findWatchOutcome.value
        }
        findWatchOutcome.value = CommandOutcome.Sending
        val written = writePacket(CrrepaProtocol.findDevice())
        findWatchOutcome.value = if (written) {
            CommandOutcome.AwaitingAcknowledgement()
        } else {
            CommandOutcome.Failed("Bluetooth write failed")
        }
        return findWatchOutcome.value
    }

    /**
     * Writes one alarm slot.
     *
     * The write itself is verifiable (the GATT write callback confirms the watch received the
     * bytes). The *alarm being stored* is not: the vendor SDK exposes `queryAllNewAlarm` but the
     * reference application never reads alarms back to confirm a write. This method therefore
     * returns [CommandOutcome.AwaitingAcknowledgement]; callers must not mark the row as synced.
     */
    suspend fun writeAlarm(alarm: AlarmRecord): CommandOutcome {
        if (_connectionState.value !is ConnectionState.Ready) {
            return CommandOutcome.Failed("Watch is not ready").also { alarmSyncOutcome.value = it }
        }
        alarmSyncOutcome.value = CommandOutcome.Sending
        val written = writePacket(
            CrrepaProtocol.alarm(
                slot = alarm.id,
                enabled = alarm.isEnabled,
                repeatMask = alarm.repeatDays,
                hour = alarm.hour,
                minute = alarm.minute
            )
        )
        val outcome = if (written) {
            CommandOutcome.AwaitingAcknowledgement()
        } else {
            CommandOutcome.Failed("Bluetooth write failed")
        }
        alarmSyncOutcome.value = outcome
        return outcome
    }

    // ------------------------------------------------------------------ heartbeat

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                when (val state = _connectionState.value) {
                    is ConnectionState.Ready -> {
                        readBattery()
                        delay(2_000)
                        val silentFor = System.currentTimeMillis() - lastCommunicationTimestamp.value
                        if (silentFor > 75_000) {
                            log("No traffic from watch for ${silentFor}ms")
                            _connectionState.value = ConnectionState.Unstable(
                                "No traffic from the watch for over a minute",
                                lastVerifiedAt = lastCommunicationTimestamp.value.takeIf { it > 0 },
                                connected = true
                            )
                        }
                    }

                    is ConnectionState.Unstable -> {
                        // Recovery path: prove the link with a real read before publishing Ready.
                        val current = gatt ?: return@launch
                        if (verifyLink()) {
                            publishReady(current)
                            return@launch
                        }
                    }

                    else -> return@launch
                }
            }
        }
    }

    // ------------------------------------------------------------------ diagnostics

    private fun log(message: String) {
        Log.d(TAG, message)
        val entry = "${timestampNow()}  $message"
        _diagnostics.value = (_diagnostics.value + entry).takeLast(DIAGNOSTIC_LIMIT)
    }

    fun clearDiagnostics() {
        _diagnostics.value = emptyList()
    }

    private fun timestampNow(): String =
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())

    private fun ByteArray.toHex(): String =
        if (isEmpty()) "(empty)" else joinToString(" ") { "%02X".format(it) }
}
