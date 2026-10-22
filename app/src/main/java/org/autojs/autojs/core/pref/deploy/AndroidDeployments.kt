package org.autojs.autojs.core.pref.deploy

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.editor.AceEditorPluginHost
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.archive.ImportEnvironment
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFont
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadEvent
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.WorkingDirectoryPathPolicy
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The device side of the deployment items (docs/settings/ROADMAP.md T18) and the environment probe of an import (T8):
 * the working directory resolved like `WorkingDirectoryUtils`, the storage access of `AllFilesAccessPermission`, the
 * Ace editor plugin of `AceEditorPluginHost`, and the editor fonts of `AceEditorFontManager` (bundled ones or
 * downloaded through its catalog and downloader). Call [environment] off the main thread.
 *
 * zh-CN: 部署项的设备部分 (ROADMAP T18) 与导入的环境探测 (T8): 工作目录按 `WorkingDirectoryUtils` 的方式解析, 存储权限取
 * `AllFilesAccessPermission`, Ace 编辑器插件取 `AceEditorPluginHost`, 编辑器字体取 `AceEditorFontManager` (内置字体, 或经其字体目录与
 * 下载器下载). [environment] 在主线程之外调用.
 */
class AndroidDeployments(context: Context) : DeploymentItems.Device {

    private val context = context.applicationContext

    override fun directory(stored: String): File = File(WorkingDirectoryPathPolicy.resolveStored(stored, EnvironmentUtils.externalStoragePath))

    override fun hasStorageAccess(): Boolean = AllFilesAccessPermission(context).has()

    override fun hasAceEditorPlugin(): Boolean = AceEditorPluginHost.isAvailable(context)

    override val fonts: EditorFontInstaller = AndroidEditorFontInstaller(this.context)

    /**
     * What an import plan needs to know about this device (T8): the installed packages, the Ace editor plugin, and
     * whether a deployment is satisfied already (the directory exists, the font is bundled or downloaded).
     * zh-CN: 导入计划需要的本机情况 (T8): 已安装的包, Ace 编辑器插件, 以及某项部署是否已满足 (目录存在, 字体为内置或已下载).
     */
    fun environment(): ImportEnvironment = ImportEnvironment(installedPackages(), hasAceEditorPlugin()) { kind, value ->
        val text = value as? String
        text != null && when (kind) {
            DeploymentKind.CREATE_DIRECTORY -> directory(text).isDirectory
            DeploymentKind.DOWNLOAD_EDITOR_FONT -> fonts.isInstalled(text)
        }
    }

    private fun installedPackages(): Set<String> {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(0)
        }
        return packages.mapTo(HashSet()) { it.packageName }
    }
}

/**
 * Editor fonts through `AceEditorFontManager`: a font is installed when it is bundled ([AceEditorFont]) or downloaded;
 * [install] takes the font of the active catalog (refreshing it once when the font is missing) and downloads it.
 * zh-CN: 经 `AceEditorFontManager` 管理编辑器字体: 内置字体 ([AceEditorFont]) 或已下载即为已安装; [install] 从当前字体目录取得字体
 * (缺少时刷新一次目录) 并下载.
 */
class AndroidEditorFontInstaller(private val context: Context) : EditorFontInstaller {

    private val manager by lazy { AceEditorFontManager.get(context) }

    override fun isInstalled(fontId: String): Boolean =
        AceEditorFont.entries.any { it.preferenceValue == fontId } || manager.store.installedFont(fontId) != null

    override fun displayName(fontId: String): String = runCatching { manager.displayName(fontId, context) }.getOrDefault(fontId)

    override suspend fun install(fontId: String, progress: (Float?) -> Unit) {
        val font = manager.catalog()?.find(fontId) ?: refreshedCatalogFont(fontId)
            ?: throw DeploymentException(DeploymentException.Reason.UNAVAILABLE, "The font catalog has no $fontId")
        suspendCancellableCoroutine { continuation ->
            val subscription = try {
                manager.download(fontId, font.artifact.sha256) { event ->
                    when (event) {
                        is FontDownloadEvent.Queued -> progress(null)
                        is FontDownloadEvent.Progress -> progress(if (event.totalBytes > 0) event.bytesDownloaded.toFloat() / event.totalBytes else null)
                        is FontDownloadEvent.Success -> if (continuation.isActive) continuation.resume(Unit)
                        is FontDownloadEvent.Cancelled -> if (continuation.isActive) {
                            continuation.resumeWithException(DeploymentException(DeploymentException.Reason.CANCELLED, "The download of $fontId was cancelled"))
                        }
                        is FontDownloadEvent.Failure -> if (continuation.isActive) {
                            val error = event.error
                            continuation.resumeWithException(DeploymentException(DeploymentException.Reason.NETWORK, error.message ?: error.javaClass.simpleName, error))
                        }
                    }
                }
            } catch (e: Exception) {
                continuation.resumeWithException(DeploymentException(DeploymentException.Reason.UNAVAILABLE, e.message ?: e.javaClass.simpleName, e))
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { subscription.cancel() }
        }
    }

    private suspend fun refreshedCatalogFont(fontId: String) = suspendCancellableCoroutine { continuation ->
        val subscription = manager.refreshAsync { result -> if (continuation.isActive) continuation.resume(result.getOrNull()?.find(fontId)) }
        continuation.invokeOnCancellation { subscription.cancel() }
    }
}
