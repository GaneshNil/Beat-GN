package com.marvneo.companion.data.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.marvneo.companion.MarvNeoApp
import com.marvneo.companion.data.ble.protocol.CrrepaProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WatchNotificationListenerService : NotificationListenerService() {

    private val tag = "WatchNotifListener"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Configurable whitelist map
    private val packageTypeMap = mapOf(
        "com.whatsapp" to CrrepaProtocol.NOTIF_WHATSAPP,
        "com.whatsapp.w4b" to CrrepaProtocol.NOTIF_WHATSAPP,
        "com.instagram.android" to CrrepaProtocol.NOTIF_INSTAGRAM,
        "org.telegram.messenger" to CrrepaProtocol.NOTIF_TELEGRAM,
        "com.facebook.katana" to CrrepaProtocol.NOTIF_FACEBOOK,
        "com.facebook.orca" to CrrepaProtocol.NOTIF_FACEBOOK,
        "com.twitter.android" to CrrepaProtocol.NOTIF_TWITTER,
        "com.google.android.gm" to CrrepaProtocol.NOTIF_GMAIL,
        "com.google.android.apps.messaging" to CrrepaProtocol.NOTIF_SMS,
        "com.android.mms" to CrrepaProtocol.NOTIF_SMS,
        "com.google.android.dialer" to CrrepaProtocol.NOTIF_CALL,
        "com.android.dialer" to CrrepaProtocol.NOTIF_CALL
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // Filter ongoing or transient progress notifications
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return
        }

        val type = packageTypeMap[pkgName] ?: return // Drop un-whitelisted apps

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        Log.d(tag, "Forwarding notification from $pkgName (type=$type): $title")

        val bleManager = (application as MarvNeoApp).bleManager
        val packet = CrrepaProtocol.createNotificationPacket(type, title, text)

        scope.launch {
            bleManager.sendCommand(packet)
        }
    }
}

