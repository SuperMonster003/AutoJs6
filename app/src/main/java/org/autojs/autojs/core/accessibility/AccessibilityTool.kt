package org.autojs.autojs.core.accessibility

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Secure
import android.util.Log
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.accessibility.control.A11yServiceController
import org.autojs.autojs.core.accessibility.control.A11yServiceController.EnsureResult
import org.autojs.autojs.core.accessibility.control.A11yServiceController.FailureReason
import org.autojs.autojs.core.accessibility.control.EnabledA11yServices
import org.autojs.autojs.core.accessibility.control.RootEnabledServicesStore
import org.autojs.autojs.core.accessibility.control.SecureEnabledServicesStore
import org.autojs.autojs.core.accessibility.control.SettingsStoreStartStrategy
import org.autojs.autojs.core.accessibility.control.ShizukuEnabledServicesStore
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.autojs.autojs.core.pref.keys.A11yKeys
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.service.AccessibilityInteractionClient
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.function.Consumer

/**
 * Android-side facade of the process-wide [controller]: every predicate and start / stop / wait
 * method delegates to it, this class only adds what needs a [Context] (toasts, the system settings
 * page, error messages) and the node cache. Instances are stateless apart from the context used
 * for messages, so creating one per call site is fine.
 *
 * zh-CN: 进程级 [controller] 的 Android 侧门面: 全部谓词与启动 / 停止 / 等待方法都委托给它,
 * 本类只补充需要 [Context] 的部分 (toast, 系统设置页, 错误文案) 与节点缓存.
 * 除用于文案的 context 外实例无状态, 因此各调用点各自创建一个实例是可以的.
 *
 * Created by Stardust on Jan 26, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
class AccessibilityTool(private val context: Context? = null) {

    private val mApplicationContext = GlobalAppContext.get()
    private val mContext: Context
        get() = context ?: mApplicationContext

    @ScriptInterface
    fun clearCache(): Boolean = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                AccessibilityService.instance?.clearCache() == true
            }
            else -> AccessibilityInteractionClient.clearCache()
        }
    }.getOrDefault(false)

    @ScriptInterface
    @JvmOverloads
    fun launchSettings(showGuideMessage: Boolean = true, showExceptionHint: Boolean = true) {
        if (showGuideMessage) {
            val msg = "${mContext.getString(R.string.text_please_choose)} ${mContext.getString(R.string.app_name)}"
            ViewUtils.showToast(mContext, msg, true)
        }
        val result = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).startSafely(mApplicationContext)
        if (!result && showExceptionHint) {
            ViewUtils.showToast(mContext, R.string.go_to_accessibility_settings, true)
        }
    }

    /** Every accessibility service enabled in system settings. zh-CN: 系统设置中已启用的全部无障碍服务. */
    @ScriptInterface
    fun getServices(): Array<String> = controller.enabledServices().toTypedArray()

    @ScriptInterface
    fun hasInstance() = controller.hasInstance()

    // Means the service is enabled in system settings.
    // zh-CN: 表示服务在系统设置中处于已启用状态.
    @ScriptInterface
    fun hasService() = controller.hasService()

    @ScriptInterface
    fun isRunning() = controller.isRunning()

    // Means the service is enabled and confirmed workable in current process.
    // zh-CN: 表示服务已启用, 且在当前进程中已确认可用.
    @ScriptInterface
    fun isOperational() = controller.isOperational()

    @ScriptInterface
    fun isMalfunctioning() = controller.isMalfunctioning()

    @JvmOverloads
    @ScriptInterface
    fun startService(withLaunchSettings: Boolean = true): Boolean = controller.start(withLaunchSettings)

    @JvmOverloads
    @ScriptInterface
    fun stopService(withLaunchSettings: Boolean = true): Boolean = controller.stop(withLaunchSettings)

    @JvmOverloads
    @ScriptInterface
    fun restartService(withLaunchSettings: Boolean = false): Boolean = controller.restart(withLaunchSettings)

    /**
     * Starts the service and waits until it is bound; the script is interrupted when the user does
     * not enable it within [timeout] (negative waits forever).
     * zh-CN: 启动服务并等待绑定; 用户未在 [timeout] 内启用时中断脚本 (负数为无限等待).
     */
    @JvmOverloads
    @ScriptInterface
    fun startServiceAndWaitFor(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        if (!controller.ensure(State.CONNECTED, timeout).isSatisfied) {
            throw ScriptInterruptedException()
        }
    }

    // Start service and wait until it becomes operational.
    // zh-CN: 启动服务并等待其进入可工作状态.
    @JvmOverloads
    @ScriptInterface
    fun startServiceAndWaitForOperational(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        if (!controller.ensure(State.OPERATIONAL, timeout).isSatisfied) {
            throw ScriptInterruptedException()
        }
    }

    /** Ensures the service is running, otherwise throws with the concrete reason. zh-CN: 确保服务运行中, 否则以具体原因抛出. */
    @ScriptInterface
    fun ensureService() = ensureService(false)

    /**
     * [ensureService] that first stops a service enabled in system settings when [isForcibleRestart]
     * is set (`auto(true)` in scripts), waiting for the unbind so that the restart really happens.
     * zh-CN: [isForcibleRestart] 为 true 时 (脚本中的 `auto(true)`) 先停止已在系统设置中启用的服务并等待解绑,
     * 使重启真正发生, 然后同 [ensureService].
     */
    fun ensureService(isForcibleRestart: Boolean) {
        throwUnlessSatisfied(controller.ensure(State.CONNECTED, forceRestart = isForcibleRestart))
    }

    // Ensure service is operational, otherwise throw a concrete error.
    // zh-CN: 确保服务处于可工作状态, 否则抛出明确的错误信息.
    @ScriptInterface
    fun ensureServiceOperational(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        throwUnlessSatisfied(controller.ensure(State.OPERATIONAL, timeout))
    }

    private fun throwUnlessSatisfied(result: EnsureResult) {
        if (result is EnsureResult.Failed) {
            throw ScriptException(mContext.getString(messageResOf(result.reason)))
        }
    }

    /**
     * [A11yServiceController.ensure] run off the main thread (a root shell or the wait for the bind
     * must not block the UI), with the outcome delivered to [onResult] on the main thread. For UI
     * trigger points (floating menu, tiles, layout inspect broadcasts) that want to carry on with
     * their own feature right after the service has come up.
     *
     * zh-CN: 在非主线程运行 [A11yServiceController.ensure] (root shell 与等待绑定不应阻塞 UI), 结果在主线程投递给 [onResult].
     * 供 UI 触发点 (悬浮菜单, 磁贴, 布局分析广播) 在服务启动后立即接续自身功能.
     */
    @JvmOverloads
    fun ensureServiceAsync(
        level: State = State.CONNECTED,
        timeoutMillis: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT,
        launchSettingsIfNeeded: Boolean = false,
        onResult: Consumer<EnsureResult>,
    ): Disposable = Single
        .fromCallable { controller.ensure(level, timeoutMillis, launchSettingsIfNeeded) }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({ onResult.accept(it) }, {
            Log.w(TAG, "ensureServiceAsync failed", it)
            onResult.accept(EnsureResult.Failed(controller.failureReason()))
        })

    /**
     * Explains a failed [ensureServiceAsync] to the user: a service that is not enabled at all gets
     * the settings page (with the guide toast), any other reason a toast naming it.
     * zh-CN: 向用户说明 [ensureServiceAsync] 的失败: 完全未启用时打开设置页 (带引导 toast), 其它原因则 toast 说明.
     */
    fun explainFailure(reason: FailureReason) {
        when (reason) {
            FailureReason.NOT_ENABLED -> {
                ViewUtils.showToast(mContext, R.string.error_no_accessibility_permission_to_capture)
                launchSettings()
            }
            else -> ViewUtils.showToast(mContext, messageResOf(reason))
        }
    }

    companion object {

        private val TAG = AccessibilityTool::class.java.simpleName

        @JvmStatic
        val DEFAULT_A11Y_SERVICE_START_TIMEOUT = A11yServiceController.DEFAULT_START_TIMEOUT_MILLIS

        @JvmStatic
        val DEFAULT_A11Y_SERVICE_STOP_TIMEOUT = A11yServiceController.DEFAULT_STOP_TIMEOUT_MILLIS

        @JvmStatic
        val SERVICES_DELIMITER = EnabledA11yServices.DELIMITER

        /**
         * The process-wide controller every tool instance delegates to; the only holder of start /
         * stop logic. Built lazily from the application context.
         * zh-CN: 所有工具实例共同委托的进程级控制器, 启停逻辑的唯一持有者. 以应用上下文惰性构建.
         */
        @JvmStatic
        val controller: A11yServiceController<AccessibilityService> by lazy { createController(GlobalAppContext.get()) }

        @JvmStatic
        fun messageResOf(reason: FailureReason): Int = when (reason) {
            FailureReason.NOT_ENABLED -> R.string.error_no_accessibility_permission
            FailureReason.ENABLED_BUT_UNBOUND -> R.string.text_a11y_service_enabled_but_not_running
            FailureReason.BOUND_BUT_NOT_OPERATIONAL -> R.string.error_a11y_service_not_operational
        }

        private fun createController(context: Context): A11yServiceController<AccessibilityService> {
            val serviceName = "${context.packageName}/${AccessibilityServiceUsher::class.java.name}"
            val resolver = context.contentResolver
            val readEnabledServices = { Secure.getString(resolver, Secure.ENABLED_ACCESSIBILITY_SERVICES) }
            val log: (String) -> Unit = { Log.d(TAG, it) }
            return A11yServiceController(
                serviceName = serviceName,
                registry = AccessibilityService.registry,
                enabledServicesReader = readEnabledServices,
                strategies = listOf(
                    SettingsStoreStartStrategy(
                        name = "root",
                        serviceName = serviceName,
                        store = RootEnabledServicesStore(log),
                        available = { A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get() && RootUtils.isRootAvailable() },
                        readBack = readEnabledServices,
                        log = log,
                    ),
                    SettingsStoreStartStrategy(
                        name = "secure settings",
                        serviceName = serviceName,
                        store = SecureEnabledServicesStore(resolver),
                        available = { A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get() && SettingsUtils.SecureSettings.isGranted(context) },
                        readBack = readEnabledServices,
                        log = log,
                    ),
                    // Last because it involves another process (the Shizuku user service) and a shell round trip.
                    // zh-CN: 排在最后, 因为它经过另一个进程 (Shizuku 用户服务) 与一次 shell 往返.
                    SettingsStoreStartStrategy(
                        name = "shizuku",
                        serviceName = serviceName,
                        store = ShizukuEnabledServicesStore(context, log),
                        available = { A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get() && WrappedShizuku.isOperational() },
                        readBack = readEnabledServices,
                        log = log,
                    ),
                ),
                disableSelf = { AccessibilityService.stop() },
                settingsUi = { AccessibilityTool(context).launchSettings() },
                startTimeoutMillis = A11yServiceController.DEFAULT_START_TIMEOUT_MILLIS,
                stopTimeoutMillis = A11yServiceController.DEFAULT_STOP_TIMEOUT_MILLIS,
                log = log,
            )
        }

    }

}
