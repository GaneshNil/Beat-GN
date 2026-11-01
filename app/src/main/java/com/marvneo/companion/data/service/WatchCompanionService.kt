package com.marvneo.companion.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.marvneo.companion.MainActivity
import com.marvneo.companion.MarvNeoApp
import com.marvneo.companion.data.ble.MarvNeoBleManager
import com.marvneo.companion.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the GATT link alive in the background and mirrors the real connection state into the
 * ongoing notification. It never fabricates a state: the notification text comes straight from
 * [MarvNeoBleManager.connectionState].
 */
@SuppressLint("MissingPermission")
class WatchCompanionService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var bleManager: MarvNeoBleManager

    private val channelId = "watch_link"
    private val notificationId = 101

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF ->
                    bleManager.onBluetoothTurnedOff()

                BluetoothAdapter.STATE_ON -> bleManager.onBluetoothTurnedOn()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getBleManager(): MarvNeoBleManager = bleManager
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = (application as MarvNeoApp).bleManager
        createChannel()
        startForeground(notificationId, buildNotification("Starting…"))

        ContextCompat.registerReceiver(
            this,
            adapterReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        scope.launch {
            bleManager.connectionState.collectLatest { state ->
                updateNotification(describe(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra("MAC_ADDRESS")
        if (!address.isNullOrBlank()) {
            bleManager.connect(address)
        } else if (bleManager.hasPermissions()) {
            bleManager.autoConnect()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runCatching { unregisterReceiver(adapterReceiver) }
        scope.coroutineContext.cancel()
        super.onDestroy()
    }

    private fun describe(state: ConnectionState): String = when (state) {
        is ConnectionState.Ready -> "Connected to ${state.deviceName}"
        is ConnectionState.Connecting -> "Connecting to ${state.name}…"
        is ConnectionState.DiscoveringServices -> "Discovering watch services…"
        is ConnectionState.Subscribing -> "Subscribing to telemetry…"
        is ConnectionState.VerifyingLink -> "Verifying watch link…"
        is ConnectionState.Scanning -> "Scanning for watch…"
        is ConnectionState.WatchFound -> "Found ${state.name} (${state.rssi} dBm)"
        is ConnectionState.Reconnecting -> "Reconnecting (${state.attempt}/${state.maxAttempts})…"
        is ConnectionState.Unstable -> "Link unstable: ${state.reason}"
        is ConnectionState.Failed -> "Connection failed: ${state.reason}"
        is ConnectionState.BluetoothOff -> "Bluetooth is off"
        is ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Idle -> "Idle"
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            getString(com.marvneo.companion.R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(com.marvneo.companion.R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.marvneo.companion.R.string.app_name))
            .setContentText(text)
            .setSmallIcon(com.marvneo.companion.R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(notificationId, buildNotification(text))
    }
}
