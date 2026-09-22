package org.autojs.autojs.core.plugin.center.wizard

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.app.SimpleActivityLifecycleCallbacks
import org.autojs.autojs.ui.main.scripts.PackageInstallStatusCoordinator
import org.autojs.autojs.ui.main.scripts.PackageInstallerActivity
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Launches one package installation through [PackageInstallerActivity] and suspends
 * until the system reports a terminal result for that package.
 *
 * Completion is detected from three sources:
 * - a terminal [PackageInstallStatusCoordinator] record for the package (success or failure);
 * - the `PACKAGE_ADDED` broadcast for the package (success);
 * - [PackageInstallerActivity] finishing without having created a session (failure).
 *
 * zh-CN: 通过 [PackageInstallerActivity] 发起一次安装并挂起, 直到系统给出该包的最终结果.
 * 完成信号来自三处: 协调器中该包的终态记录 (成功或失败), 该包的 `PACKAGE_ADDED` 广播 (成功),
 * 以及安装页面未创建会话即退出 (失败).
 */
internal object PluginInstallWizardInstallAwaiter {

    private const val INSTALLER_EXIT_GRACE_MILLIS = 600L

    sealed interface Result {
        data object Success : Result
        data class Failure(val detail: String) : Result
    }

    suspend fun install(
        context: Context,
        packageName: String,
        launch: () -> Boolean,
    ): Result {
        val appContext = context.applicationContext
        val application = appContext as? Application
        val knownSessionIds = runCatching {
            PackageInstallStatusCoordinator.records(appContext)
                .filter { it.packageName == packageName }
                .map { it.sessionId }
                .toSet()
        }.getOrDefault(emptySet())

        return suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val settled = AtomicBoolean(false)
            lateinit var cleanup: () -> Unit

            fun settle(result: Result) {
                if (!settled.compareAndSet(false, true)) return
                cleanup()
                if (continuation.isActive) continuation.resume(result)
            }

            fun hasNewSession(): Boolean = runCatching {
                PackageInstallStatusCoordinator.records(appContext)
                    .any { it.packageName == packageName && it.sessionId !in knownSessionIds }
            }.getOrDefault(false)

            val statusListener: (Int) -> Unit = { sessionId ->
                if (sessionId !in knownSessionIds) {
                    val record = runCatching { PackageInstallStatusCoordinator.record(appContext, sessionId) }.getOrNull()
                    if (record != null && record.packageName == packageName && record.isTerminal) {
                        settle(
                            if (record.status == PackageInstaller.STATUS_SUCCESS) {
                                Result.Success
                            } else {
                                Result.Failure(
                                    record.detail.ifEmpty {
                                        appContext.getString(R.string.error_package_installation_status, record.status)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            val packageReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
                    if (intent.data?.schemeSpecificPart != packageName) return
                    settle(Result.Success)
                }
            }

            val lifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
                override fun onActivityDestroyed(activity: Activity) {
                    if (activity !is PackageInstallerActivity || !activity.isFinishing) return
                    mainHandler.postDelayed({
                        if (settled.get()) return@postDelayed
                        if (!hasNewSession() && !isPackageInstalled(appContext, packageName)) {
                            settle(Result.Failure(appContext.getString(R.string.text_plugin_install_wizard_install_not_started)))
                        }
                    }, INSTALLER_EXIT_GRACE_MILLIS)
                }
            }

            PackageInstallStatusCoordinator.addListener(statusListener)
            val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
            appContext.registerReceiver(packageReceiver, packageFilter)
            application?.registerActivityLifecycleCallbacks(lifecycleCallbacks)

            cleanup = {
                runCatching { PackageInstallStatusCoordinator.removeListener(statusListener) }
                runCatching { appContext.unregisterReceiver(packageReceiver) }
                runCatching { application?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks) }
            }

            continuation.invokeOnCancellation {
                if (settled.compareAndSet(false, true)) cleanup()
            }

            if (!launch()) {
                settle(Result.Failure(appContext.getString(R.string.text_plugin_install_wizard_install_not_started)))
            }
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

}
