package org.autojs.autojs.core.pref.deploy

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.archive.ImportPlan

/**
 * A deployment item (docs/settings/ROADMAP.md D20 / T18): what an import does on this device before it writes a key
 * whose value needs something here (a directory that does not exist, a font that is not downloaded). The confirm step
 * shows [describe]; an item the user confirms runs ([run]) in the progress dialog of the import, and only once it has
 * succeeded is its key written ([ImportPlan.Deployment.toPut]). [check] tells whether nothing is left to do. Pure JVM
 * (R3): the Android parts come in through the constructor of each implementation.
 *
 * zh-CN: 部署项 (ROADMAP D20 / T18): 导入在写入某个键之前需要先在本机完成的动作 (目录不存在, 字体未下载). 确认步骤显示 [describe];
 * 用户确认的项在导入的进度对话框中运行 ([run]), 成功后才写入其键 ([ImportPlan.Deployment.toPut]). [check] 判断是否已无需部署.
 * 纯 JVM (R3): Android 相关部分经各实现的构造参数传入.
 */
interface DeploymentItem {

    val deployment: ImportPlan.Deployment

    val kind: DeploymentKind
        get() = deployment.kind

    /** What the confirm step and the progress dialog show. zh-CN: 确认步骤与进度对话框显示的内容. */
    fun describe(): Description

    /** True when this device needs nothing any more. zh-CN: 本机已无需部署时为 true. */
    fun check(): Boolean

    /**
     * Runs the item, reporting progress to [progress] (0 to 1, null while unknown); throws [DeploymentException]. A
     * cancelled coroutine cancels what runs (a download).
     * zh-CN: 运行部署项, 进度报告给 [progress] (0 到 1, 未知时为 null); 失败抛 [DeploymentException]. 协程取消时取消正在进行的动作 (下载).
     */
    suspend fun run(progress: (Float?) -> Unit)

    /**
     * What an item is about: its [kind], the [target] (a directory, the name of a font), the [permissions] it lacks and
     * the [prerequisites] it waits for.
     * zh-CN: 部署项的内容: 种类 [kind], 目标 [target] (目录, 字体名称), 缺少的权限 [permissions] 与等待的前置条件 [prerequisites].
     */
    data class Description(
        val kind: DeploymentKind,
        val target: String,
        val permissions: Set<Permission> = emptySet(),
        val prerequisites: Set<ImportPlan.Prerequisite> = emptySet(),
    )

    /** A permission an item may need (T18). zh-CN: 部署项可能需要的权限 (T18). */
    enum class Permission {
        /** "All files access" (Android 11 and later) or the storage permission before it. */
        ALL_FILES_ACCESS,
    }
}

/**
 * Why a deployment item failed; the result page explains each [reason] and offers the permission page for
 * [Reason.PERMISSION] (T18).
 * zh-CN: 部署项失败的原因; 结果页说明每种 [reason], 对 [Reason.PERMISSION] 提供权限页入口 (T18).
 */
class DeploymentException @JvmOverloads constructor(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {

    enum class Reason {
        /** The storage access the item needs is not granted. */
        PERMISSION,

        /** Writing failed for another reason. */
        IO,

        /** A download failed. */
        NETWORK,

        /** What the value names is not offered here (a font the catalog does not have). */
        UNAVAILABLE,

        /** A prerequisite is missing (the Ace editor plugin). */
        PREREQUISITE,

        /** The user cancelled the item. */
        CANCELLED,
    }
}

/**
 * What became of one deployment item: the key it was for ([store] and [name]), its [kind] and [target], and why it
 * failed ([failure], null when it succeeded). Pure data, kept in the result of the import.
 * zh-CN: 一个部署项的结果: 对应的键 ([store] 与 [name]), 种类 [kind] 与目标 [target], 以及失败原因 ([failure], 成功时为 null). 纯数据.
 */
data class DeploymentResult @JvmOverloads constructor(
    val kind: DeploymentKind,
    val store: String,
    val name: String,
    val target: String,
    val failure: DeploymentException.Reason? = null,
    val detail: String? = null,
) {
    val succeeded: Boolean
        get() = failure == null
}
