package com.marvneo.companion.data.service

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

class WatchCompanionService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var bleManager: MarvNeoBleManager

    private val channelId = "watch_companion_channel"
    private val notificationId = 101
    private var wakeLock: PowerManager.WakeLock? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                    bleManager.disconnect()
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

        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        scope.launch {
            bleManager.connectionState.collectLatest { state ->
                val stateText = when (state) {
                    is ConnectionState.Connected -> {
                        wakeLock?.acquire(10_000) // Brief lock during connection negotiation
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val macAddress = intent?.getStringExtra("MAC_ADDRESS")
        if (!macAddress.isNullOrBlank()) {
            bleManager.connect(macAddress)
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

    @SuppressLint("NotificationPermission")
    private fun updateNotification(status: String, battery: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, buildNotification(status, battery))
    }

    private fun buildNotification(status: String, battery: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (status == "Connected") "Connected • Battery: $battery%" else status

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Marv Neo Companion")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
