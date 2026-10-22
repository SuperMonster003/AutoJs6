package org.autojs.autojs.core.pref.deploy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.archive.ImportPlan
import java.io.File

/**
 * The implementations of the deployment kinds (docs/settings/ROADMAP.md T18): [of] makes the item of a deployment from
 * the device parts of a [Device] (`AndroidDeployments` in the app, fakes in the tests). [KINDS] are the kinds with an
 * implementation, which guard (e) of T5 checks against the kinds in use; the `when` of [of] keeps them complete.
 *
 * zh-CN: 各部署种类的实现 (ROADMAP T18): [of] 以 [Device] 提供的设备部分 (应用中为 `AndroidDeployments`, 测试中为替身) 生成部署项.
 * [KINDS] 为已有实现的种类, 由 T5 守卫 (e) 对照使用中的种类检查; [of] 的 `when` 保证覆盖全部种类.
 */
object DeploymentItems {

    @JvmField
    val KINDS: Set<DeploymentKind> = setOf(DeploymentKind.CREATE_DIRECTORY, DeploymentKind.DOWNLOAD_EDITOR_FONT)

    /** What the items need from the device. zh-CN: 部署项需要的设备能力. */
    interface Device {

        /** The directory the stored value of `key_working_directory` names. */
        fun directory(stored: String): File

        /** True when the app may write any directory of the shared storage ("All files access"). */
        fun hasStorageAccess(): Boolean

        /** True when the Ace editor plugin is installed. */
        fun hasAceEditorPlugin(): Boolean

        val fonts: EditorFontInstaller
    }

    @JvmStatic
    fun of(deployment: ImportPlan.Deployment, device: Device): DeploymentItem = when (deployment.kind) {
        DeploymentKind.CREATE_DIRECTORY -> CreateWorkingDirectoryItem(deployment, device.directory(deployment.value as String), device::hasStorageAccess)
        DeploymentKind.DOWNLOAD_EDITOR_FONT -> DownloadEditorFontItem(deployment, device.fonts, device::hasAceEditorPlugin)
    }
}

/**
 * `key_working_directory` names a directory that does not exist (appendix C Q1): create it. Without the storage access
 * of [hasStorageAccess] a failure is [DeploymentException.Reason.PERMISSION], and the result page offers the
 * permission page.
 * zh-CN: `key_working_directory` 指向的目录不存在 (附录 C Q1): 创建该目录. 没有 [hasStorageAccess] 的存储权限时失败原因为
 * `PERMISSION`, 结果页提供权限页入口.
 */
class CreateWorkingDirectoryItem(
    override val deployment: ImportPlan.Deployment,
    private val directory: File,
    private val hasStorageAccess: () -> Boolean,
) : DeploymentItem {

    override fun describe() = DeploymentItem.Description(
        kind, directory.path, if (hasStorageAccess()) emptySet() else setOf(DeploymentItem.Permission.ALL_FILES_ACCESS),
    )

    override fun check(): Boolean = directory.isDirectory

    override suspend fun run(progress: (Float?) -> Unit) = withContext(Dispatchers.IO) {
        progress(null)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            val reason = if (hasStorageAccess()) DeploymentException.Reason.IO else DeploymentException.Reason.PERMISSION
            throw DeploymentException(reason, "Cannot create ${directory.path}")
        }
        progress(1f)
    }
}

/**
 * Downloads the editor font a value of `key_ace_editor_font` names (appendix C Q2), through [EditorFontInstaller]; the
 * Ace editor plugin is its prerequisite (T18).
 * zh-CN: 经 [EditorFontInstaller] 下载 `key_ace_editor_font` 的值所指的编辑器字体 (附录 C Q2); 前置条件为 Ace 编辑器插件 (T18).
 */
class DownloadEditorFontItem(
    override val deployment: ImportPlan.Deployment,
    private val fonts: EditorFontInstaller,
    private val hasAceEditorPlugin: () -> Boolean,
) : DeploymentItem {

    private val fontId: String
        get() = deployment.value as String

    override fun describe() = DeploymentItem.Description(
        kind, fonts.displayName(fontId), prerequisites = if (hasAceEditorPlugin()) emptySet() else setOf(ImportPlan.Prerequisite.ACE_EDITOR_PLUGIN),
    )

    override fun check(): Boolean = fonts.isInstalled(fontId)

    override suspend fun run(progress: (Float?) -> Unit) {
        if (!hasAceEditorPlugin()) throw DeploymentException(DeploymentException.Reason.PREREQUISITE, "The Ace editor plugin is not installed")
        if (fonts.isInstalled(fontId)) return progress(1f)
        fonts.install(fontId, progress)
        progress(1f)
    }
}

/**
 * The editor fonts of this device, for [DownloadEditorFontItem]; the app's implementation downloads through the font
 * catalog and downloader of the Ace editor (`FontCatalogRepository`, `FontDownloader`).
 * zh-CN: 本机的编辑器字体, 供 [DownloadEditorFontItem] 使用; 应用中的实现经 Ace 编辑器的字体目录与下载器下载.
 */
interface EditorFontInstaller {

    fun isInstalled(fontId: String): Boolean

    fun displayName(fontId: String): String

    /** Downloads and installs [fontId] (0 to 1 to [progress]); throws [DeploymentException]; cancellation cancels it. */
    suspend fun install(fontId: String, progress: (Float?) -> Unit)
}
