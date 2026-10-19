package org.autojs.autojs.engine

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/** Each runtime client owns its timed wake lock; execution shutdown releases every lock. */
internal class NodeDeviceControls(private val context: Context) {
    private val wakeLocks = linkedMapOf<String, PowerManager.WakeLock>()
    private val closedScopes = hashSetOf<String>()
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
    fun keepScreenOn(timeoutMs: Long, scope: String = "") {
        check(!closed && scope !in closedScopes) { "The script has ended." }
        require(timeoutMs > 0) { "device.keepScreenOn requires a positive timeout in milliseconds." }
        cancelKeepingAwake(scope)
        val manager = checkNotNull(context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        wakeLocks[scope] = manager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AutoJs6:NodeScreen",
        ).apply { setReferenceCounted(false); acquire(timeoutMs) }
    }

    @Synchronized
    fun cancelKeepingAwake(scope: String = "") {
        wakeLocks.remove(scope)?.let { if (it.isHeld) it.release() }
    }

    @Synchronized
    fun closeScope(scope: String) { closedScopes.add(scope); cancelKeepingAwake(scope) }

    @Synchronized
    fun activeCount(): Int = wakeLocks.values.count { it.isHeld }

    @Synchronized
    fun close() {
        closed = true
        wakeLocks.keys.toList().forEach(::cancelKeepingAwake)
        closedScopes.clear()
    }
}
