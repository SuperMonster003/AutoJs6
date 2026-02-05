package com.kevinluo.autoglm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import com.kevinluo.autoglm.ui.FloatingWindowService
import com.kevinluo.autoglm.voice.ContinuousListeningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Background keep-alive manager for the AutoGLM application.
 *
 * Manages background keep-alive strategies including:
 * - Battery optimization whitelist checking and guidance
 * - Service state monitoring and auto-recovery
 * - WakeLock management during task execution
 */
object KeepAliveManager {
    // 电池优化状态
    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

    // 悬浮窗服务状态
    private val _floatingWindowServiceRunning = MutableStateFlow(false)
    val floatingWindowServiceRunning: StateFlow<Boolean> = _floatingWindowServiceRunning.asStateFlow()

    // 持续监听服务状态
    private val _continuousListeningServiceRunning = MutableStateFlow(false)
    val continuousListeningServiceRunning: StateFlow<Boolean> = _continuousListeningServiceRunning.asStateFlow()

    // WakeLock 引用
    private var wakeLock: PowerManager.WakeLock? = null
    private var listeningWakeLock: PowerManager.WakeLock? = null

    /**
     * Checks if the app is ignoring battery optimizations.
     *
     * @param context Application context
     * @return true if battery optimizations are ignored
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService<PowerManager>() ?: return false
        val ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        _batteryOptimizationIgnored.value = ignored
        return ignored
    }

    /**
     * Requests to ignore battery optimizations.
     *
     * @param context Application context
     * @return true if settings page was opened successfully
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        } else {
            // Android M 以下不需要
            true
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to request ignore battery optimizations", e)
        // 尝试打开电池优化设置页面
        openBatteryOptimizationSettings(context)
    }

    /**
     * Opens battery optimization settings page.
     *
     * @param context Application context
     * @return true if settings page was opened successfully
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean = try {
        val intent =
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to open battery optimization settings", e)
        false
    }

    /**
     * Updates service states.
     */
    fun updateServiceStates() {
        _floatingWindowServiceRunning.value = FloatingWindowService.getInstance() != null
        _continuousListeningServiceRunning.value = ContinuousListeningService.isRunning()
    }

    /**
     * Checks and restores service states.
     *
     * @param context Application context
     * @param shouldFloatingWindowRun Whether floating window service should be running
     * @param shouldContinuousListeningRun Whether continuous listening service should be running
     */
    fun checkAndRestoreServices(
        context: Context,
        shouldFloatingWindowRun: Boolean,
        shouldContinuousListeningRun: Boolean = false,
    ) {
        updateServiceStates()

        // 恢复悬浮窗服务
        if (shouldFloatingWindowRun && !_floatingWindowServiceRunning.value) {
            if (FloatingWindowService.canDrawOverlays(context)) {
                Logger.i(TAG, "Restoring floating window service")
                startFloatingWindowService(context)
            }
        }

        // 恢复持续监听服务
        if (shouldContinuousListeningRun && !_continuousListeningServiceRunning.value) {
            Logger.i(TAG, "Restoring continuous listening service")
            ContinuousListeningService.start(context)
        }
    }

    /**
     * Starts floating window service (regular service, no foreground notification needed).
     */
    private fun startFloatingWindowService(context: Context) {
        try {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.startService(intent)
            _floatingWindowServiceRunning.value = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start floating window service", e)
        }
    }

    /**
     * Acquires WakeLock for task execution.
     *
     * Keeps CPU awake during task execution to prevent system sleep from interrupting tasks.
     *
     * @param context Application context
     */
    fun acquireTaskWakeLock(context: Context) {
        if (wakeLock?.isHeld == true) {
            Logger.d(TAG, "WakeLock already held")
            return
        }

        try {
            val powerManager = context.getSystemService<PowerManager>() ?: return
            wakeLock =
                powerManager
                    .newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        WAKELOCK_TAG,
                    ).apply {
                        acquire(WAKELOCK_TIMEOUT)
                    }
            Logger.i(TAG, "Task WakeLock acquired")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    /**
     * Releases task execution WakeLock.
     */
    fun releaseTaskWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Logger.i(TAG, "Task WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to release WakeLock", e)
        }
    }

    /**
     * Acquires WakeLock for continuous listening.
     *
     * Used by ContinuousListeningService to keep CPU awake for voice listening.
     *
     * @param context Application context
     */
    fun acquireListeningWakeLock(context: Context) {
        if (listeningWakeLock?.isHeld == true) {
            Logger.d(TAG, "Listening WakeLock already held")
            return
        }

        try {
            val powerManager = context.getSystemService<PowerManager>() ?: return
            listeningWakeLock =
                powerManager
                    .newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        LISTENING_WAKELOCK_TAG,
                    ).apply {
                        setReferenceCounted(false)
                        acquire(LISTENING_WAKELOCK_TIMEOUT)
                    }
            Logger.i(TAG, "Listening WakeLock acquired")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire Listening WakeLock", e)
        }
    }

    /**
     * Releases continuous listening WakeLock.
     */
    fun releaseListeningWakeLock() {
        try {
            listeningWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Logger.i(TAG, "Listening WakeLock released")
                }
            }
            listeningWakeLock = null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to release Listening WakeLock", e)
        }
    }

    /**
     * Syncs and fixes state.
     *
     * Called when Activity resumes to check and fix various states.
     *
     * @param context Application context
     */
    fun syncFixState(context: Context) {
        Logger.d(TAG, "Syncing and fixing state")

        // 更新电池优化状态
        isIgnoringBatteryOptimizations(context)

        // 更新服务状态
        updateServiceStates()
    }

    /**
     * Gets state summary for debugging.
     *
     * @param context Application context
     * @return Formatted state summary string
     */
    fun getStateSummary(context: Context): String = buildString {
        appendLine("KeepAliveManager State:")
        appendLine("  - Battery optimization ignored: ${isIgnoringBatteryOptimizations(context)}")
        appendLine("  - Floating window service running: ${_floatingWindowServiceRunning.value}")
        appendLine("  - Continuous listening service running: ${_continuousListeningServiceRunning.value}")
        appendLine("  - Task WakeLock held: ${wakeLock?.isHeld == true}")
        appendLine("  - Listening WakeLock held: ${listeningWakeLock?.isHeld == true}")
    }

    // Constants - placed at the bottom following code style guidelines
    private const val TAG = "KeepAliveManager"
    private const val WAKELOCK_TAG = "AutoGLM:TaskExecution"
    private const val WAKELOCK_TIMEOUT = 30 * 60 * 1000L // 30 minutes timeout
    private const val LISTENING_WAKELOCK_TAG = "AutoGLM:ContinuousListening"
    private const val LISTENING_WAKELOCK_TIMEOUT = 10 * 60 * 60 * 1000L // 10 hours timeout
}
