package org.autojs.autojs.theme.intelligent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.script.ScriptSource

/**
 * Reads and watches the device facts of the device state sources (roadmap P3.3). Only the facts the active library
 * needs are watched, and nothing is watched without an active library (D5). Changes are reported on the main thread.
 * zh-CN: 读取并监听设备类状态源所需的设备状态 (Roadmap P3.3). 只监听激活库需要的状态, 没有激活库时不监听任何状态 (D5).
 * 变化在主线程上报.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object DeviceStateMonitor {

    enum class Fact { BATTERY, POWER_SAVE, ACCESSIBILITY_SERVICE, SCRIPTS }

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private var context: Context? = null
    private var onChanged: (() -> Unit)? = null
    private var watched: Set<Fact> = emptySet()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = notifyChanged()
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = notifyChanged()
    }

    private val accessibilityListener = A11yServiceRegistry.StateListener<AccessibilityService> { _, _ -> notifyChanged() }

    private val engineCallback = object : ScriptEngineManager.EngineLifecycleCallback {
        override fun onEngineCreate(engine: ScriptEngine<out ScriptSource>) = notifyChanged()
        override fun onEngineRemove(engine: ScriptEngine<out ScriptSource>) = notifyChanged()
    }

    private val notifyRunnable = Runnable { onChanged?.invoke() }

    fun factsFor(source: StateSource): Set<Fact> = when (source) {
        is StateSource.Battery, StateSource.Charging -> setOf(Fact.BATTERY)
        StateSource.PowerSave -> setOf(Fact.POWER_SAVE)
        StateSource.AccessibilityService -> setOf(Fact.ACCESSIBILITY_SERVICE)
        StateSource.ScriptRunning -> setOf(Fact.SCRIPTS)
        else -> emptySet()
    }

    /**
     * Watches exactly [facts], registering and unregistering as needed; [onChanged] runs on the main thread.
     * zh-CN: 只监听 [facts], 按需注册与注销; [onChanged] 在主线程运行.
     */
    fun watch(context: Context, facts: Set<Fact>, onChanged: () -> Unit) {
        val appContext = context.applicationContext
        this.context = appContext
        this.onChanged = onChanged
        (watched - facts).forEach { stop(appContext, it) }
        (facts - watched).forEach { start(appContext, it) }
        watched = facts
    }

    fun stopAll() {
        val appContext = context ?: return
        watched.forEach { stop(appContext, it) }
        watched = emptySet()
        handler.removeCallbacks(notifyRunnable)
    }

    /** Current facts; values that [facts] does not ask for keep their defaults. zh-CN: 当前设备状态; [facts] 未要求的值保持默认. */
    fun read(context: Context, facts: Set<Fact>): DeviceFacts {
        var result = DeviceFacts()
        if (Fact.BATTERY in facts) {
            val intent = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            result = result.copy(batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else null, charging = plugged != 0)
        }
        if (Fact.POWER_SAVE in facts) {
            result = result.copy(powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
        }
        if (Fact.ACCESSIBILITY_SERVICE in facts) {
            result = result.copy(accessibilityServiceRunning = AccessibilityService.hasInstance())
        }
        if (Fact.SCRIPTS in facts) {
            val running = runCatching { ScriptEngineService.getInstance()?.engines?.isNotEmpty() == true }.getOrDefault(false)
            result = result.copy(scriptRunning = running)
        }
        return result
    }

    private fun start(context: Context, fact: Fact) {
        when (fact) {
            Fact.BATTERY -> ContextCompat.registerReceiver(context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            Fact.POWER_SAVE -> ContextCompat.registerReceiver(
                context, powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            Fact.ACCESSIBILITY_SERVICE -> AccessibilityService.registry.addListener(this, accessibilityListener)
            Fact.SCRIPTS -> ScriptEngineService.getInstance()?.registerEngineLifecycleCallback(engineCallback)
        }
    }

    private fun stop(context: Context, fact: Fact) {
        when (fact) {
            Fact.BATTERY -> runCatching { context.unregisterReceiver(batteryReceiver) }
            Fact.POWER_SAVE -> runCatching { context.unregisterReceiver(powerSaveReceiver) }
            Fact.ACCESSIBILITY_SERVICE -> AccessibilityService.registry.removeListener(this)
            Fact.SCRIPTS -> ScriptEngineService.getInstance()?.unregisterEngineLifecycleCallback(engineCallback)
        }
    }

    /** Coalesces bursts (battery broadcasts, several engines) into one main thread callback. zh-CN: 将连续的变化 (电池广播, 多个引擎) 合并为一次主线程回调. */
    private fun notifyChanged() {
        handler.removeCallbacks(notifyRunnable)
        handler.post(notifyRunnable)
    }

}
