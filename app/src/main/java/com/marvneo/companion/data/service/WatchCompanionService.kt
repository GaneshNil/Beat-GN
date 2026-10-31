package com.marvneo.companion.data.service

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.marvneo.companion.MainActivity
import com.marvneo.companion.MarvNeoApp
import com.marvneo.companion.R
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class WatchCompanionService : Service() {

    private val tag = "WatchCompanionService"
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var bleManager: MarvNeoBleManager

    private val channelId = "watch_companion_channel"
    private val notificationId = 101
    private var wakeLock: PowerManager.WakeLock? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                        bleManager.disconnect()
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        bleManager.autoConnect()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { dev ->
                        if (bleManager.isSmartwatchDevice(dev)) {
                            Log.i(tag, "Smartwatch connected via Bluetooth: ${dev.name} (${dev.address}). Connecting GATT...")
                            bleManager.connect(dev.address)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { dev ->
                        if (dev.address == bleManager.connectedDeviceAddress.value) {
                            Log.w(tag, "Connected watch disconnected from phone Bluetooth.")
                            bleManager.disconnect()
                        }
                    }
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): WatchCompanionService = this@WatchCompanionService
        fun getBleManager(): MarvNeoBleManager = bleManager
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = (application as MarvNeoApp).bleManager
        createNotificationChannel()

        // Acquire partial wakelock for OriginOS 6+ keep-alive during packet sync
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MarvNeo:CompanionWakeLock").apply {
            setReferenceCounted(false)
        }

        startForeground(notificationId, buildNotification("Disconnected", 0))

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)

        scope.launch {
            bleManager.connectionState.collectLatest { state ->
                val stateText = when (state) {
                    is ConnectionState.Connected -> {
                        wakeLock?.acquire(10_000)
                        "Connected"
                    }
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Error -> "Error: ${state.message}"
                }
                updateNotification(stateText, bleManager.batteryFlow.value)
            }
        }

        scope.launch {
            bleManager.batteryFlow.collectLatest { battery ->
                if (bleManager.connectionState.value is ConnectionState.Connected) {
                    updateNotification("Connected", battery)
                }
            }
        }

        // Automatic connection on service start
        scope.launch {
            bleManager.autoConnect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val macAddress = intent?.getStringExtra("MAC_ADDRESS")
        if (!macAddress.isNullOrBlank()) {
            bleManager.connect(macAddress)
        } else {
            bleManager.autoConnect()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Watch Companion Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains persistent BLE connection with beatXP Marv Neo"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String, batteryPercentage: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val content = if (statusText == "Connected") {
            "Watch Connected • Battery: $batteryPercentage%"
        } else {
            "Watch Status: $statusText"
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("beatXP Marv Neo Companion")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String, batteryPercentage: Int) {
        val notification = buildNotification(statusText, batteryPercentage)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
