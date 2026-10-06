package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.net.Uri
import org.autojs.autojs.core.plugin.installer.PackageInstallRouter
import org.autojs.plugin.installer.api.InstallerContract

/**
 * Installs one wizard download through [PackageInstallRouter] and suspends until the package
 * landed or the attempt ended (plugin roadmap D22): with the installer plugin the session outcome
 * decides, with the system installer the `PACKAGE_ADDED` / `PACKAGE_REPLACED` broadcast of the
 * package decides, up to [PackageInstallRouter.SYSTEM_INSTALL_AWAIT_MILLIS]. The plugin path uses
 * the `auto` interaction, which installs silently when a privileged authorizer is usable and asks
 * for confirmation otherwise.
 *
 * zh-CN: 通过 [PackageInstallRouter] 发起一次向导安装并挂起, 直到该包落地或本次尝试结束:
 * 插件路径以会话结果为准, 系统安装器路径以该包的安装广播为准 (有超时).
 * 插件路径使用 `auto` 交互方式: 有可用的特权授权方式时静默安装, 否则请求确认.
 */
internal object PluginInstallWizardInstallAwaiter {

    sealed interface Result {
        data object Success : Result
        data class Failure(val detail: String) : Result
    }

    suspend fun install(context: Context, packageName: String, packageUri: Uri): Result =
        when (val outcome = PackageInstallRouter.installAndAwait(
            context = context.applicationContext,
            packageUri = packageUri,
            packageName = packageName,
            interaction = InstallerContract.INTERACTION_AUTO,
        )) {
            is PackageInstallRouter.AwaitResult.Success -> Result.Success
            is PackageInstallRouter.AwaitResult.Failure -> Result.Failure(outcome.detail)
        }
}
