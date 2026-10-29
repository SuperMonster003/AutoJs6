package org.autojs.autojs.ui.main

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * Starts [MainActivity] for a device test and returns the resumed instance, the way InfoDialogInstrumentationTest does.
 *
 * Instrumentation.startActivitySync only accepts an activity it saw being created and then waits for an idle main thread,
 * 45 s at most. The first launch after a read-only emulator boots takes 30 s and more, and once that launch has timed out
 * the test cannot finish the activity it never received; MainActivity is singleTask, so every later launch in the process
 * only reaches the leaked instance as onNewIntent and times out too. A resumed instance, whoever created it, is all the
 * tests need.
 *
 * UiAutomation is connected before the activity starts. ListView gives an item the accessibility delegate that reports
 * ACTION_CLICK only when accessibility is enabled at the moment the item view is created, and accessibility is enabled for
 * the process by the UiAutomation connection, which the first uiAutomation access makes lazily. On API 33+ the
 * notification permission is granted so that LeakCanary's permission request activity, shown once per fresh install, does
 * not take the active window away from the views under test.
 */
object ResumedMainActivity {

    /** The first launch of a cold, freshly booted read-only emulator has been measured above 30 s. */
    private const val LAUNCH_TIMEOUT_MS = 120_000L
    private const val ACCESSIBILITY_TIMEOUT_MS = 30_000L

    /**
     * Adds [extraFlags] to FLAG_ACTIVITY_NEW_TASK. With FLAG_ACTIVITY_CLEAR_TASK an instance alive before the call is
     * about to be destroyed while it may still be resumed, so only a new instance is accepted.
     */
    fun launch(extraFlags: Int = 0): MainActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        awaitMain(instrumentation, ACCESSIBILITY_TIMEOUT_MS, "Accessibility did not become enabled for the test process") {
            accessibilityManager.isEnabled
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val replaced = mutableListOf<Activity>()
        if (extraFlags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0) {
            instrumentation.runOnMainSync {
                val registry = ActivityLifecycleMonitorRegistry.getInstance()
                Stage.entries.filter { it != Stage.DESTROYED }
                    .flatMapTo(replaced) { registry.getActivitiesInStage(it).filterIsInstance<MainActivity>() }
            }
        }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or extraFlags))
        var resumed: MainActivity? = null
        awaitMain(instrumentation, LAUNCH_TIMEOUT_MS, "MainActivity did not reach RESUMED") {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull { !it.isFinishing && replaced.none { old -> old === it } }
            resumed != null
        }
        return checkNotNull(resumed)
    }

    private fun awaitMain(instrumentation: Instrumentation, timeoutMs: Long, message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }
}
