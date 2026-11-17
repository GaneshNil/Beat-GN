package com.marvneo.companion

import android.content.Intent
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
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
