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
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.marvneo.companion.data.ble.protocol.CrrepaFrameAssembler
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.repository.HealthRepository
import com.marvneo.companion.domain.model.CommandOutcome
import com.marvneo.companion.domain.model.ConnectionState
import com.marvneo.companion.domain.model.DiscoveredDevice
import com.marvneo.companion.domain.model.MeasurementState
import com.marvneo.companion.data.ble.GattOperations.Kind
import com.marvneo.companion.data.ble.GattOperations.Request
import com.marvneo.companion.domain.model.AlarmSlots
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext

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
 *     never sends a reading, the request times out.
 */
@SuppressLint("MissingPermission")
class MarvNeoBleManager(
    private val context: Context,
    private val healthRepository: HealthRepository
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
        private const val SCAN_DURATION_MS = 20_000L
        private const val DIAGNOSTIC_LIMIT = 250
        private const val PRE_DISCOVERY_SETTLE_MS = 300L
    }

    // Android UI, service events, the GATT callbacks and BLE coroutines share this looper.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callbackHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var packetCharacteristic: BluetoothGattCharacteristic? = null
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null
    private var stepCharacteristic: BluetoothGattCharacteristic? = null
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null

    private var targetAddress: String? = null
    private var userDisconnected = prefs().getBoolean("user_disconnected", false)
    private var reconnectAttempt = 0

    private var activeScan: Pair<BluetoothLeScanner, ScanCallback>? = null
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

    private val _bluetoothEnabled = MutableStateFlow(isAdapterEnabled())
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    val connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceAddress = MutableStateFlow<String?>(null)
    val lastCommunicationTimestamp = MutableStateFlow(0L)
    val negotiatedMtu = MutableStateFlow(0)
    val firmwareVersion = MutableStateFlow<String?>(null)
    val modelNumber = MutableStateFlow<String?>(null)
    val manufacturerName = MutableStateFlow<String?>(null)

    // ---- Telemetry ----
    val stepsFlow = MutableStateFlow<Int?>(null)
    val caloriesFlow = MutableStateFlow<Int?>(null)
    val distanceFlow = MutableStateFlow<Int?>(null)
    val activeMinutesFlow = MutableStateFlow<Int?>(null)
    val heartRateFlow = MutableStateFlow(0)
    val spO2Flow = MutableStateFlow(0)
    val bloodPressureFlow = MutableStateFlow<Pair<Int, Int>?>(null)
    val batteryFlow = MutableStateFlow(0)
    val batteryKnown = MutableStateFlow(false)
    val batteryReadMessage = MutableStateFlow<String?>(null)
    val pulseAvailable = MutableStateFlow(false)

    // ---- Measurements ----
    val heartRateMeasurement = MutableStateFlow<MeasurementState>(MeasurementState.Idle)
    val spo2Measurement = MutableStateFlow<MeasurementState>(MeasurementState.Idle)
    val bloodPressureMeasurement = MutableStateFlow<MeasurementState>(MeasurementState.Idle)

    /** True only once this install has received at least one real BP reading. */
    val bloodPressureEverReported = MutableStateFlow(false)

    // ---- Commands ----
    val findWatchOutcome = MutableStateFlow<CommandOutcome>(CommandOutcome.Idle)

    // ---- Diagnostics (the evidence trail shown in the app) ----
    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private val gattOperations = GattOperations()
    private var lastTrafficElapsed = 0L

    private enum class MeasurementKind { HEART_RATE, SPO2, BLOOD_PRESSURE }

    private val measurementTimeouts = mutableMapOf<MeasurementKind, Job>()
    private val rxAssembler = CrrepaFrameAssembler()

    private val gattCallback = object : BluetoothGattCallback() {

        /**
         * A late callback from a closed connection must never release the single-operation mutex
         * or feed data into the current session. Every callback below starts with this guard.
         */
        private fun isCurrent(gatt: BluetoothGatt, what: String): Boolean {
            if (gatt === this@MarvNeoBleManager.gatt) return true
            log("Ignoring stale $what callback from an earlier GATT connection")
            return false
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrent(gatt, "connection-state")) return
            log("onConnectionStateChange status=$status newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleLinkLost("GATT error $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (_connectionState.value !is ConnectionState.Connecting) return
                    connectTimeoutJob?.cancel()
                    _connectionState.value = ConnectionState.DiscoveringServices(gatt.device.address)
                    scope.launch { initialiseConnection(gatt) }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleLinkLost("Disconnected by device")
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrent(gatt, "MTU")) return
            val effective = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            negotiatedMtu.value = effective
            log("MTU now $effective (status $status)")
            gattOperations.complete(gatt, Request(Kind.MTU), status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrent(gatt, "service-discovery")) return
            log("onServicesDiscovered status=$status (${gatt.services.size} services)")
            gattOperations.complete(gatt, Request(Kind.DISCOVERY), status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (!isCurrent(gatt, "descriptor-write")) return
            log("CCCD write ${descriptor.characteristic.uuid} status=$status")
            gattOperations.complete(
                gatt, Request(Kind.DESCRIPTOR_WRITE, descriptor), status == BluetoothGatt.GATT_SUCCESS
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (!isCurrent(gatt, "characteristic-write")) return
            gattOperations.complete(
                gatt, Request(Kind.WRITE, characteristic), status == BluetoothGatt.GATT_SUCCESS
            )
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            finishRead(gatt, characteristic, value, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            finishRead(gatt, characteristic, characteristic.value?.copyOf(), status)
        }

        private fun finishRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray?,
            status: Int
        ) {
            if (!isCurrent(gatt, "characteristic-read")) return
            val hasValue = status == BluetoothGatt.GATT_SUCCESS && value != null
            val success = hasValue && (characteristic !== batteryCharacteristic ||
                value?.let(::batteryPercent) != null)
            if (!success) log("Read ${characteristic.uuid} returned no valid value (status $status)")
            gattOperations.complete(gatt, Request(Kind.READ, characteristic), success) {
                handleReadValue(characteristic, checkNotNull(value))
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

    private fun isAdapterEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    fun refreshAdapterState() {
        if (isAdapterEnabled()) {
            if (!_bluetoothEnabled.value) onBluetoothTurnedOn()
        } else if (_bluetoothEnabled.value || gatt != null || activeScan != null) {
            onBluetoothTurnedOff()
        } else if (hasPermissions()) {
            _connectionState.value = ConnectionState.BluetoothOff
        }
    }

    fun onBluetoothTurnedOff() {
        log("Bluetooth turned off")
        stopScan()
        reconnectJob?.cancel()
        closeGatt()
        _connectionState.value = ConnectionState.BluetoothOff
        _bluetoothEnabled.value = false
    }

    fun onBluetoothTurnedOn() {
        if (!_bluetoothEnabled.value) reconnectAttempt = 0
        _bluetoothEnabled.value = true
        if (_connectionState.value is ConnectionState.BluetoothOff) {
            _connectionState.value = ConnectionState.Idle
        }
        autoConnect()
    }

    fun onServiceStopped() {
        stopScan()
        reconnectJob?.cancel()
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    // ------------------------------------------------------------------ scanning

    fun startScan() {
        if (gatt != null || _isScanning.value) return
        if (!hasPermissions()) {
            _connectionState.value =
                ConnectionState.Failed("Bluetooth permission not granted", retryable = false)
            return
        }
        if (!isAdapterEnabled()) {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R &&
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false
        ) {
            _connectionState.value = ConnectionState.Failed("Turn on Location to scan on Android 11")
            return
        }
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull() ?: run {
            _connectionState.value = ConnectionState.Failed("BLE scanner unavailable")
            return
        }
        reconnectJob?.cancel()
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _connectionState.value = ConnectionState.Scanning()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (activeScan?.second === this && result != null) handleScanResult(result)
            }

            override fun onScanFailed(errorCode: Int) {
                if (activeScan?.second !== this) return
                stopScan()
                log("Scan failed (error $errorCode)")
                _connectionState.value = ConnectionState.Failed("Bluetooth scan failed (code $errorCode)")
            }
        }
        activeScan = scanner to callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(null, settings, callback)
            log("BLE scan started")
        } catch (e: RuntimeException) {
            stopScan()
            log("Scan could not start (${e.javaClass.simpleName})")
            _connectionState.value = ConnectionState.Failed(
                if (e is SecurityException) "Bluetooth permission revoked" else "Could not start scan"
            )
            return
        }
        scanTimeoutJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
            log("Scan finished")
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        val scan = activeScan
        activeScan = null
        scan?.let { (scanner, callback) -> runCatching { scanner.stopScan(callback) } }
        _isScanning.value = false
        if (_connectionState.value is ConnectionState.Scanning ||
            _connectionState.value is ConnectionState.WatchFound
        ) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun handleScanResult(result: ScanResult) {
        if (!hasPermissions()) {
            stopScan()
            _connectionState.value = ConnectionState.Failed("Bluetooth permission revoked", retryable = false)
            return
        }
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
        val record = DiscoveredDevice(
            name = name?.takeIf { it.isNotBlank() } ?: "Unnamed device",
            address = device.address,
            rssi = result.rssi,
            isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
            likelyWatch = looksLikeWatch(name.orEmpty(), device) ||
                result.scanRecord?.serviceUuids.orEmpty().any { it.uuid == BleGattAttributes.CRP_SERVICE_FEEA }
        )

        _discoveredDevices.value = (_discoveredDevices.value.filterNot { it.address == record.address } + record)
            .sortedWith(compareByDescending<DiscoveredDevice> { it.likelyWatch }
                .thenByDescending { it.isBonded }
                .thenByDescending { it.rssi })

        if (record.likelyWatch && _connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.WatchFound(record.name, record.address, record.rssi)
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

    fun canReconnect(): Boolean = targetAddress != null || lastKnownAddress() != null

    private fun prefs() = context.getSharedPreferences("marv_neo_prefs", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ connection

    /** A previous verified selection may reconnect; discovery still uses the Scan screen. */
    fun autoConnect(): Boolean {
        if (userDisconnected || !hasPermissions()) return false
        if (gatt != null || _isScanning.value || reconnectJob?.isActive == true) return true
        if ((_connectionState.value as? ConnectionState.Failed)?.retryable == false ||
            reconnectAttempt >= MAX_RECONNECT_ATTEMPTS
        ) return false
        val saved = targetAddress ?: lastKnownAddress() ?: return false
        return connectInternal(saved, isReconnect = false)
    }

    fun reconnect(): Boolean {
        val address = targetAddress ?: lastKnownAddress() ?: return false
        return connectInternal(address, isReconnect = false)
    }

    private fun setUserDisconnected(disconnected: Boolean) {
        userDisconnected = disconnected
        prefs().edit().putBoolean("user_disconnected", disconnected).apply()
    }

    fun connect(address: String) {
        connectInternal(address, isReconnect = false)
    }

    /**
     * @param isReconnect true only for a retry scheduled by [scheduleReconnect]. A scheduled retry
     * must not reset [reconnectAttempt], otherwise `MAX_RECONNECT_ATTEMPTS` is never reached.
     */
    private fun connectInternal(address: String, isReconnect: Boolean): Boolean {
        if (!hasPermissions()) {
            _connectionState.value =
                ConnectionState.Failed("Bluetooth permission not granted", retryable = false)
            return false
        }
        val adapter = adapter
        if (adapter == null || !isAdapterEnabled()) {
            _connectionState.value = ConnectionState.BluetoothOff
            return false
        }
        if (gatt != null && address.equals(connectedDeviceAddress.value, ignoreCase = true)) return true

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Failed("Invalid Bluetooth address", retryable = false)
            return false
        }

        stopScan()
        _discoveredDevices.value = emptyList()
        if (!isReconnect) {
            setUserDisconnected(false)
            reconnectAttempt = 0
            reconnectJob?.cancel()
        }
        targetAddress = address
        closeGatt()

        val name = runCatching { device.name }.getOrNull() ?: "Smartwatch"
        connectedDeviceName.value = name
        connectedDeviceAddress.value = address
        _connectionState.value = ConnectionState.Connecting(name, address)
        log("Opening GATT connection")

        try {
            gatt = device.connectGatt(
                context, false, gattCallback, BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK, callbackHandler
            )
        } catch (_: SecurityException) {
            closeGatt()
            _connectionState.value = ConnectionState.Failed("Bluetooth permission revoked", retryable = false)
            return false
        } catch (e: RuntimeException) {
            log("Could not open GATT connection (${e.javaClass.simpleName})")
        }

        val link = gatt
        if (link == null) {
            handleLinkLost("Could not open GATT connection")
            return false
        }
        connectTimeoutJob = scope.launch {
            delay(15_000)
            if (link === gatt) handleLinkLost("Connection timed out")
        }
        return true
    }

    fun disconnect() {
        setUserDisconnected(true)
        targetAddress = null
        reconnectJob?.cancel()
        stopScan()
        log("User disconnected")
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun initialiseConnection(gatt: BluetoothGatt) {
        delay(PRE_DISCOVERY_SETTLE_MS)
        if (gatt !== this.gatt) return

        val discovered = runGattOp(gatt, Request(Kind.DISCOVERY)) { gatt.discoverServices() }
        if (gatt !== this.gatt) return
        if (!discovered) {
            handleLinkLost("Service discovery failed")
            return
        }

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
        if (fee2.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0
        ) {
            _connectionState.value = ConnectionState.Failed("Watch command channel is not writable", retryable = false)
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

        // Request MTU only after identifying the documented data channel.
        val mtuOk = runGattOp(gatt, Request(Kind.MTU)) { gatt.requestMtu(MTU_REQUEST) }
        if (gatt !== this.gatt) return
        if (!mtuOk) log("Using the default ATT MTU until Android reports a negotiated value")

        val subscriptions = listOfNotNull(fee3, heartRateCharacteristic, fee1)
        for (characteristic in subscriptions) {
            _connectionState.value = ConnectionState.Subscribing(
                gatt.device.address,
                characteristic.uuid.toString()
            )
            val ok = enableNotifications(gatt, characteristic)
            if (gatt !== this.gatt) return
            if (!ok) {
                if (characteristic === fee3) {
                    handleLinkLost("Packet channel subscribe failed")
                    return
                }
                log("Could not subscribe to ${characteristic.uuid}")
            } else if (characteristic === heartRateCharacteristic) {
                pulseAvailable.value = true
            }
        }

        _connectionState.value = ConnectionState.VerifyingLink(gatt.device.address)
        if (verifyLink(gatt)) {
            publishReady(gatt)
        } else if (gatt === this.gatt) {
            log("Handshake read did not confirm the link")
            _connectionState.value = ConnectionState.Unstable(
                reason = "Watch link awaiting a valid battery read",
                lastVerifiedAt = null,
                connected = true
            )
        }
        if (gatt !== this.gatt) return
        startHeartbeat(gatt)
        scope.launch { readDeviceInformation(gatt) }
    }

    private fun publishReady(gatt: BluetoothGatt) {
        if (gatt !== this.gatt) return
        val now = System.currentTimeMillis()
        reconnectAttempt = 0
        val name = connectedDeviceName.value ?: "Smartwatch"
        val address = gatt.device.address
        prefs().edit()
            .putString("last_device_address", address)
            .apply()

        _connectionState.value = ConnectionState.Ready(
            deviceName = name,
            deviceAddress = address,
            protocolName = "Crrepa FEEA",
            mtu = negotiatedMtu.value.coerceAtLeast(23),
            lastVerifiedAt = now
        )
        log("Link verified by battery read")
        if (!timeSyncRequested) {
            timeSyncRequested = true
            scope.launch {
                if (writePacket(CrrepaProtocol.timeSync(), gatt)) log("Time sync written; verify on watch")
            }
        }
    }

    private var timeSyncRequested = false

    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val cccd = characteristic.getDescriptor(BleGattAttributes.CCCD) ?: return false
        val value = when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> return false
        }

        if (!runCatching { gatt.setCharacteristicNotification(characteristic, true) }.getOrDefault(false)) {
            return false
        }
        repeat(2) { attempt ->
            val ok = runGattOp(gatt, Request(Kind.DESCRIPTOR_WRITE, cccd)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = value.copyOf()
                        gatt.writeDescriptor(cccd)
                    }
                }
            }
            if (gatt !== this.gatt) return false
            if (ok) return true
            if (attempt == 0) delay(200)
        }
        runCatching { gatt.setCharacteristicNotification(characteristic, false) }
        return false
    }

    private suspend fun verifyLink(gatt: BluetoothGatt): Boolean {
        val battery = batteryCharacteristic ?: return false
        if (battery.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) return false
        return runGattOp(gatt, Request(Kind.READ, battery), HANDSHAKE_TIMEOUT_MS) {
            gatt.readCharacteristic(battery)
        }
    }

    /**
     * Reads device-information characteristics. These are standard GATT reads; each one that
     * succeeds is displayed, each one that fails stays null and is shown as "not reported".
     */
    private suspend fun readDeviceInformation(gatt: BluetoothGatt) {
        val info = gatt.getService(BleGattAttributes.DEVICE_INFO_SERVICE) ?: return
        for (uuid in listOf(
            BleGattAttributes.FIRMWARE_REVISION_CHAR,
            BleGattAttributes.MODEL_NUMBER_CHAR,
            BleGattAttributes.MANUFACTURER_NAME_CHAR
        )) {
            if (gatt !== this.gatt) return
            val characteristic = info.getCharacteristic(uuid) ?: continue
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) continue
            runGattOp(gatt, Request(Kind.READ, characteristic)) { gatt.readCharacteristic(characteristic) }
        }
    }

    private fun handleReadValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        when (characteristic.uuid) {
            BleGattAttributes.BATTERY_LEVEL_CHAR -> {
                batteryPercent(value)?.let { percent ->
                    batteryFlow.value = percent
                    batteryKnown.value = true
                    markTraffic()
                }
            }

            BleGattAttributes.FIRMWARE_REVISION_CHAR -> firmwareVersion.value = readDeviceString(value)
            BleGattAttributes.MODEL_NUMBER_CHAR -> modelNumber.value = readDeviceString(value)
            BleGattAttributes.MANUFACTURER_NAME_CHAR -> manufacturerName.value = readDeviceString(value)
        }
    }

    private fun batteryPercent(value: ByteArray): Int? =
        value.singleOrNull()?.toInt()?.and(0xFF)?.takeIf { it in 0..100 }

    private fun readDeviceString(value: ByteArray): String? =
        CrrepaProtocol.decodeFirmware(value).takeIf { it.isNotBlank() }

    private fun markTraffic() {
        lastTrafficElapsed = SystemClock.elapsedRealtime()
        lastCommunicationTimestamp.value = System.currentTimeMillis()
    }

    private fun handleLinkLost(reason: String) {
        log("Link lost: $reason")
        val shouldReconnect = !userDisconnected && targetAddress != null && hasPermissions() &&
            isAdapterEnabled()
        closeGatt()
        if (shouldReconnect) {
            scheduleReconnect()
        } else {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun scheduleReconnect() {
        val address = targetAddress ?: return
        if (userDisconnected) return
        val delayMs = reconnectDelayMillis(reconnectAttempt)
        if (delayMs == null) {
            _connectionState.value = ConnectionState.Failed(
                "Could not reconnect after $MAX_RECONNECT_ATTEMPTS attempts"
            )
            return
        }
        reconnectAttempt++
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS)
        log("Reconnect attempt $reconnectAttempt in ${delayMs}ms")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userDisconnected && targetAddress == address) {
                connectInternal(address, isReconnect = true)
            }
        }
    }

    private fun closeGatt() {
        val oldGatt = gatt
        gatt = null
        connectTimeoutJob?.cancel()
        heartbeatJob?.cancel()
        gattOperations.invalidate()
        runCatching { oldGatt?.close() }
        resetMeasurementStates("Watch link ended during measurement")
        writeCharacteristic = null
        packetCharacteristic = null
        heartRateCharacteristic = null
        stepCharacteristic = null
        batteryCharacteristic = null
        pulseAvailable.value = false
        negotiatedMtu.value = 0
        rxAssembler.reset()
        timeSyncRequested = false

        connectedDeviceName.value = null
        connectedDeviceAddress.value = null
        stepsFlow.value = null
        caloriesFlow.value = null
        distanceFlow.value = null
        activeMinutesFlow.value = null
        heartRateFlow.value = 0
        spO2Flow.value = 0
        bloodPressureFlow.value = null
        batteryFlow.value = 0
        batteryKnown.value = false
        batteryReadMessage.value = null
        firmwareVersion.value = null
        modelNumber.value = null
        manufacturerName.value = null
        lastCommunicationTimestamp.value = 0
        lastTrafficElapsed = 0
        findWatchOutcome.value = CommandOutcome.Idle
    }

    private suspend fun runGattOp(
        link: BluetoothGatt,
        request: Request,
        timeoutMs: Long = GATT_OP_TIMEOUT_MS,
        start: () -> Boolean
    ): Boolean = gattOperations.run(
        link, request, timeoutMs,
        isCurrent = { link === gatt },
        onAbandoned = {
            // Android has no request ID for a late callback to a retried read or write.
            // No second MTU request is issued on this link, so a late MTU callback is harmless.
            if (request.kind != Kind.MTU) handleLinkLost("GATT ${request.kind.name.lowercase()} timed out")
        }
    ) {
        try {
            start()
        } catch (_: SecurityException) {
            closeGatt()
            reconnectJob?.cancel()
            _connectionState.value = ConnectionState.Failed("Bluetooth permission revoked", retryable = false)
            false
        } catch (e: RuntimeException) {
            log("GATT operation failed (${e.javaClass.simpleName})")
            false
        }
    }

    // ------------------------------------------------------------------ outbound

    private suspend fun writePacket(packet: ByteArray, link: BluetoothGatt? = gatt): Boolean {
        val gatt = link ?: return false
        if (gatt !== this.gatt) return false
        val characteristic = writeCharacteristic ?: run {
            log("No write characteristic resolved")
            return false
        }

        val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        var wirePacket = packet
        val ok = runGattOp(gatt, Request(Kind.WRITE, characteristic)) {
            wirePacket = CrrepaProtocol.forMtu(packet, negotiatedMtu.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, wirePacket, writeType) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = writeType
                    characteristic.value = wirePacket
                    gatt.writeCharacteristic(characteristic)
                }
            }
        }
        log("TX op=0x%02X (%d bytes, callback success=%s)".format(wirePacket[4], wirePacket.size, ok))
        return ok
    }

    // ------------------------------------------------------------------ inbound

    /**
     * Feeds a notification into the reassembly buffer, then dispatches every complete frame.
     * Mirrors `com.crrepa.l.a#n0` + `#w0`.
     */
    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (value.isEmpty()) return
        when {
            characteristic === heartRateCharacteristic && pulseAvailable.value ->
                CrrepaProtocol.parseHeartRateMeasurement(value)?.let(::onHeartRate)
                    ?: log("Invalid heart-rate notification")
            characteristic === stepCharacteristic ->
                CrrepaProtocol.parseSteps(value)?.let(::onSteps) ?: log("Invalid step notification")
            characteristic === packetCharacteristic -> {
                rxAssembler.append(value).forEach(::dispatchFrame)
            }
        }
    }

    private fun dispatchFrame(frame: CrrepaProtocol.Frame) {
        log("RX op=0x%02X (%d payload bytes)".format(frame.opcode, frame.payload.size))
        when (frame.opcode) {
            CrrepaProtocol.CMD_BLOOD_OXYGEN -> {
                val reading = CrrepaProtocol.parseBloodOxygen(frame.payload) ?: return
                spO2Flow.value = reading.percentage
                markTraffic()
                completeMeasurement(MeasurementKind.SPO2, spo2Measurement, MeasurementState.Completed(reading.percentage))
                val timestamp = System.currentTimeMillis()
                storeTelemetry { healthRepository.recordBloodOxygen(reading.percentage, timestamp) }
            }

            CrrepaProtocol.CMD_BLOOD_PRESSURE -> {
                val reading = CrrepaProtocol.parseBloodPressure(frame.payload) ?: return
                bloodPressureEverReported.value = true
                bloodPressureFlow.value = reading.systolic to reading.diastolic
                markTraffic()
                completeMeasurement(
                    MeasurementKind.BLOOD_PRESSURE, bloodPressureMeasurement,
                    MeasurementState.Completed(reading.systolic, reading.diastolic)
                )
                val timestamp = System.currentTimeMillis()
                storeTelemetry { healthRepository.recordBloodPressure(reading.systolic, reading.diastolic, timestamp) }
            }

            CrrepaProtocol.CMD_HEART_RATE_ONCE -> {
                // The SDK calls this a completion state. Its values are unverified on the target.
                if (CrrepaProtocol.parseHeartRateState(frame.payload) != null) markTraffic()
            }
        }
    }

    private fun onHeartRate(bpm: Int) {
        if (bpm !in 25..250) {
            log("Invalid heart-rate value")
            return
        }
        heartRateFlow.value = bpm
        markTraffic()
        completeMeasurement(MeasurementKind.HEART_RATE, heartRateMeasurement, MeasurementState.Completed(bpm))
        val timestamp = System.currentTimeMillis()
        storeTelemetry { healthRepository.recordHeartRate(bpm, timestamp) }
    }

    private fun onSteps(steps: CrrepaProtocol.Steps) {
        stepsFlow.value = steps.steps
        distanceFlow.value = steps.distanceMeters
        caloriesFlow.value = steps.calories
        activeMinutesFlow.value = steps.activeMinutes
        if (steps.steps == null && steps.distanceMeters == null &&
            steps.calories == null && steps.activeMinutes == null
        ) return
        markTraffic()

        val stepCount = steps.steps ?: return
        val distance = steps.distanceMeters ?: return
        val calories = steps.calories ?: return
        val timestamp = System.currentTimeMillis()
        storeTelemetry { healthRepository.recordSteps(stepCount, distance, calories, timestamp) }
    }

    private fun storeTelemetry(write: suspend () -> Unit) {
        scope.launch {
            try {
                write()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                log("Could not save health reading (${error.javaClass.simpleName})")
            }
        }
    }

    private fun completeMeasurement(
        kind: MeasurementKind,
        target: MutableStateFlow<MeasurementState>,
        state: MeasurementState
    ) {
        if (!target.value.active) return
        measurementTimeouts.remove(kind)?.cancel()
        target.value = state
    }

    private fun resetMeasurementStates(reason: String) {
        listOf(heartRateMeasurement, spo2Measurement, bloodPressureMeasurement).forEach { flow ->
            if (flow.value.active) {
                flow.value = MeasurementState.Failed(reason)
            } else if (flow.value is MeasurementState.Completed) {
                flow.value = MeasurementState.Idle
            }
        }
        measurementTimeouts.values.forEach { it.cancel() }
        measurementTimeouts.clear()
    }

    fun readBattery() {
        if (batteryReadMessage.value == "Reading battery…") return
        scope.launch {
            val link = gatt ?: return@launch
            batteryReadMessage.value = "Reading battery…"
            val verified = verifyLink(link)
            if (link !== gatt) return@launch
            batteryReadMessage.value = if (verified) null else "No valid battery level received"
            if (verified && _connectionState.value is ConnectionState.Unstable) publishReady(link)
        }
    }

    fun startHeartRateMeasurement() = startMeasurement(
        MeasurementKind.HEART_RATE, heartRateMeasurement, MEASUREMENT_TIMEOUT_MS,
        CrrepaProtocol::heartRateMeasurement
    )

    fun startSpO2Measurement() = startMeasurement(
        MeasurementKind.SPO2, spo2Measurement, SPO2_TIMEOUT_MS,
        CrrepaProtocol::bloodOxygenMeasurement
    )

    fun probeBloodPressure() = startMeasurement(
        MeasurementKind.BLOOD_PRESSURE, bloodPressureMeasurement, BP_TIMEOUT_MS,
        CrrepaProtocol::bloodPressureMeasurement
    )

    private fun startMeasurement(
        kind: MeasurementKind,
        state: MutableStateFlow<MeasurementState>,
        timeoutMs: Long,
        packet: (Boolean) -> ByteArray
    ) {
        val link = gatt
        if (_connectionState.value !is ConnectionState.Ready || link == null) {
            state.value = MeasurementState.Failed("Watch is not ready")
            return
        }
        if (kind == MeasurementKind.HEART_RATE && !pulseAvailable.value) {
            state.value = MeasurementState.Failed("Heart-rate notifications are unavailable on this link")
            return
        }
        scope.launch {
            if (!state.beginMeasurement({ writePacket(packet(true), link) })) return@launch
            measurementTimeouts[kind] = scope.launch {
                delay(timeoutMs)
                measurementTimeouts.remove(kind)
                state.value = MeasurementState.TimedOut("No reading received within ${timeoutMs / 1000}s")
                log("Measurement ${kind.name.lowercase()} timed out")
                writePacket(packet(false), link)
            }
        }
    }

    fun findWatch() {
        if (findWatchOutcome.value is CommandOutcome.Sending) return
        val link = gatt
        if (_connectionState.value !is ConnectionState.Ready || link == null) {
            findWatchOutcome.value = CommandOutcome.Failed("Watch is not ready")
            return
        }
        findWatchOutcome.value = CommandOutcome.Sending
        scope.launch {
            val written = writePacket(CrrepaProtocol.findDevice(), link)
            if (link !== gatt) return@launch
            findWatchOutcome.value = if (written) CommandOutcome.AwaitingAcknowledgement()
            else CommandOutcome.Failed("Bluetooth write failed")
        }
    }

    suspend fun writeAlarm(alarm: AlarmRecord): CommandOutcome = withContext(Dispatchers.Main.immediate) {
        if (_connectionState.value !is ConnectionState.Ready) {
            return@withContext CommandOutcome.Failed("Watch is not ready")
        }
        if (!AlarmSlots.isValid(alarm.id, alarm.hour, alarm.minute, alarm.repeatDays, alarm.isEnabled)) {
            return@withContext CommandOutcome.Failed("Check alarm time and repeat days")
        }
        val written = writePacket(
            CrrepaProtocol.alarm(alarm.id, alarm.isEnabled, alarm.repeatDays, alarm.hour, alarm.minute)
        )
        if (written) CommandOutcome.AwaitingAcknowledgement() else CommandOutcome.Failed("Bluetooth write failed")
    }

    // ------------------------------------------------------------------ heartbeat

    private fun startHeartbeat(link: BluetoothGatt) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && link === gatt) {
                delay(HEARTBEAT_INTERVAL_MS)
                val verified = verifyLink(link)
                if (link !== gatt) return@launch
                when (_connectionState.value) {
                    is ConnectionState.Ready -> {
                        if (!verified && SystemClock.elapsedRealtime() - lastTrafficElapsed > 75_000) {
                            log("No recent watch traffic")
                            _connectionState.value = ConnectionState.Unstable(
                                "No recent watch traffic",
                                lastVerifiedAt = lastCommunicationTimestamp.value.takeIf { it > 0 },
                                connected = true
                            )
                        }
                    }

                    is ConnectionState.Unstable -> if (verified) publishReady(link)
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

}
