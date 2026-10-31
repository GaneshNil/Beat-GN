package com.marvneo.companion.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import com.marvneo.companion.domain.model.ConnectionState
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

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(tag, "GATT Connected. Status: $status. Requesting MTU 512...")
                    _connectionState.value = ConnectionState.Connecting
                    reconnectAttempts = 0
                    // Step 1: Request maximum MTU for high-speed sync
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(tag, "GATT Disconnected. Status: $status. Cleaning up...")
                    cleanup()
                    _connectionState.value = ConnectionState.Disconnected
                    scheduleReconnect()
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

                    // Perform initial handshake: Time sync & Battery query
                    scope.launch {
                        delay(300)
                        sendCommand(CrrepaProtocol.createTimeSyncPacket())
                        delay(200)
                        sendCommand(CrrepaProtocol.createBatteryQueryPacket())
                        delay(200)
                        sendCommand(CrrepaProtocol.createStepsQueryPacket())
                    }
                } else {
                    Log.e(tag, "NUS Service not found on device!")
                }
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

    fun connect(macAddress: String) {
        targetDeviceAddress = macAddress
        val device = bluetoothAdapter?.getRemoteDevice(macAddress) ?: return
        cleanup()
        _connectionState.value = ConnectionState.Connecting
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        targetDeviceAddress = null
        bluetoothGatt?.disconnect()
        cleanup()
    }

    suspend fun sendCommand(packet: ByteArray): Boolean = commandMutex.withLock {
        val gatt = bluetoothGatt ?: return false
        val char = txCharacteristic ?: return false

        char.value = packet
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val success = gatt.writeCharacteristic(char)
        delay(35) // Enforce small interval between sequential GATT writes
        return success
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
        reconnectAttempts++
        val delayMs = minOf(30000L, 1000L * (1 shl minOf(reconnectAttempts, 5)))
        scope.launch {
            Log.i(tag, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms...")
            delay(delayMs)
            if (targetDeviceAddress != null && _connectionState.value == ConnectionState.Disconnected) {
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

