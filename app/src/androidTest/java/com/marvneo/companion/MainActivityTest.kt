package com.marvneo.companion

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun opensHomeOnFreshInstallWithoutBluetoothPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
                assertFalse((activity.application as MarvNeoApp).bleManager.hasPermissions())
            }
            val ui = instrumentation.uiAutomation
            ui.awaitLabel("Marv Neo")
            ui.clickLabel("Watch")
            ui.awaitLabel("Grant Bluetooth access")
            ui.clickLabel("More")
            ui.clickLabel("About")
            val context = instrumentation.targetContext
            @Suppress("DEPRECATION")
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            ui.awaitLabel("${installed.versionName} (${installed.longVersionCode})")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun UiAutomation.clickLabel(label: String) {
        var node = awaitLabel(label)
        while (!node.isClickable) {
            node = requireNotNull(node.parent) { "No clickable parent for '$label'" }
        }
        assertTrue("Could not click '$label'", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun UiAutomation.awaitLabel(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            rootInActiveWindow?.let(pending::addLast)
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (node.isVisibleToUser && (node.text?.toString() == label ||
                        node.contentDescription?.toString() == label)) return node
                for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
            }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out waiting for '$label'")
    }
}
