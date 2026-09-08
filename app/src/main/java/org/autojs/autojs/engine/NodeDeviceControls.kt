package org.autojs.autojs.engine

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/** One execution owns one timed screen wake lock; termination releases it immediately. */
internal class NodeDeviceControls(private val context: Context) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var closed = false

    fun setBrightness(value: Int) {
        require(value in 0..255) { "device.setBrightness requires an integer in 0..255." }
        writeSetting(Settings.System.SCREEN_BRIGHTNESS, value)
    }

    fun setBrightnessMode(value: Int) {
        require(value == 0 || value == 1) { "device.setBrightnessMode requires 0 (manual) or 1 (automatic)." }
        writeSetting(Settings.System.SCREEN_BRIGHTNESS_MODE, value)
    }

    private fun writeSetting(key: String, value: Int) {
        if (!Settings.System.canWrite(context)) {
            throw SecurityException("Allow AutoJs6 to modify system settings in Android settings before changing brightness.")
        }
        check(Settings.System.putInt(context.contentResolver, key, value)) { "Android did not accept the requested setting." }
    }

    @Synchronized
    @Suppress("DEPRECATION")
    fun keepScreenOn(timeoutMs: Long) {
        check(!closed) { "The script has ended." }
        require(timeoutMs > 0) { "device.keepScreenOn requires a positive timeout in milliseconds." }
        cancelKeepingAwake()
        val manager = checkNotNull(context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        wakeLock = manager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AutoJs6:NodeScreen",
        ).apply { setReferenceCounted(false); acquire(timeoutMs) }
    }

    @Synchronized
    fun cancelKeepingAwake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    @Synchronized
    fun close() {
        closed = true
        cancelKeepingAwake()
    }
}
