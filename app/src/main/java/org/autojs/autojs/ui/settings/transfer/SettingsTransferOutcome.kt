package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.deploy.DeploymentException
import org.autojs.autojs.core.pref.deploy.DeploymentResult
import org.autojs.autojs.core.pref.archive.ImportPlan
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsEffects
import org.autojs.autojs.core.pref.archive.SettingsEffects.EffectException

/**
 * What an import or an undo did (docs/settings/ROADMAP.md T10, P7.5), as its result page shows it: the number of
 * categories of the plan, the entries written ([written], the plan's `puts`) and restored to their defaults
 * ([restored], `removes`), how many stored values really changed ([changed]), the skips per reason ([skips], D9: only
 * the reasons that occurred, in the order of [SkipReason]), the effect entries that failed ([failedEffects], T12) and
 * whether one of them needs the notification permission (appendix C Q13), whether a restart is needed (D10), the data
 * categories an undo leaves as they are ([dataNotUndone]: appendix C Q27 decided they are undone, which P10.3 does; the
 * result page tells about them only when there are any), whether the credentials of the file could not be decrypted
 * on this device ([credentialsLocked], D23), why the operation backup failed ([backupFailure], T20, P9.1; it does
 * not block), whether the launcher icon changed, which the result page switches once it is closed
 * ([launcherIconPending]: switching it closes the task of the page), and whether the language was applied
 * ([languageChanged], the page is recreated in it when needed), and what became of the deployment items ([deployments],
 * T18, P7.6). Pure data (R3), kept in the wizard state as JSON.
 *
 * zh-CN: 一次导入或撤销的结果 (ROADMAP T10, P7.5), 即结果页显示的内容: 计划中的分类数, 写入的条目 ([written], 计划的 `puts`) 与恢复
 * 默认的条目 ([restored], `removes`), 存储值真正变化的个数 ([changed]), 按原因的跳过数 ([skips], D9: 只含出现过的原因, 按
 * [SkipReason] 的顺序), 失败的生效入口 ([failedEffects], T12) 以及其中是否有需要通知权限的 (附录 C Q13), 是否需要重启 (D10), 撤销
 * 未处理的数据分类 ([dataNotUndone]: 附录 C Q27 已定为也要撤销, 由 P10.3 实现; 结果页只在有这类分类时提示), 以及文件中的凭据能否在
 * 本机解密 ([credentialsLocked], D23), 操作后备份失败的原因 ([backupFailure], T20, P9.1; 不阻断), 以及启动图标是否变化, 由结果页在关闭后
 * 切换 ([launcherIconPending]: 切换会关闭本页所在的任务), 是否应用了语言 ([languageChanged], 需要时以新语言重建本页), 以及各部署项的
 * 结果 ([deployments], T18, P7.6). 纯数据 (R3), 以 JSON 保存在向导状态中.
 */
data class SettingsTransferOutcome(
    val categories: Int,
    val written: Int,
    val restored: Int,
    val changed: Int,
    val skips: Map<SkipReason, Int> = emptyMap(),
    val failedEffects: List<SettingsEffects.Entry> = emptyList(),
    val notificationPermission: Boolean = false,
    val restartNeeded: Boolean = false,
    val dataNotUndone: List<String> = emptyList(),
    val credentialsLocked: Boolean = false,
    val backupFailure: String? = null,
    val launcherIconPending: Boolean = false,
    val languageChanged: Boolean = false,
    val deployments: List<DeploymentResult> = emptyList(),
) {

    /** The number of skipped entries. zh-CN: 跳过的条目总数. */
    val skipped: Int
        get() = skips.values.sum()

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "categories" to categories,
        "written" to written,
        "restored" to restored,
        "changed" to changed,
        "skips" to skips.entries.associateTo(LinkedHashMap()) { (reason, count) -> reason.name to count },
        "failedEffects" to failedEffects.map { it.name },
        "notificationPermission" to notificationPermission,
        "restartNeeded" to restartNeeded,
        "dataNotUndone" to dataNotUndone,
        "credentialsLocked" to credentialsLocked,
        "backupFailure" to backupFailure,
        "launcherIconPending" to launcherIconPending,
        "languageChanged" to languageChanged,
        "deployments" to deployments.map {
            linkedMapOf("kind" to it.kind.name, "store" to it.store, "name" to it.name, "target" to it.target, "failure" to it.failure?.name, "detail" to it.detail)
        },
    )

    companion object {

        /**
         * The outcome of [plan], of which [changes] changed and [report] is what its effects did; [dataNotUndone] and
         * [credentialsLocked] as the undo and the import know them.
         * zh-CN: [plan] 的结果: [changes] 为发生的变化, [report] 为生效报告; [dataNotUndone] 与 [credentialsLocked] 由撤销与导入给出.
         */
        @JvmStatic
        @JvmOverloads
        fun of(
            plan: ImportPlan,
            changes: List<SettingsEffects.Change>,
            report: SettingsEffects.Report,
            dataNotUndone: List<String> = emptyList(),
            credentialsLocked: Boolean = false,
            backupFailure: String? = null,
            launcherIconPending: Boolean = false,
            deployments: List<DeploymentResult> = emptyList(),
        ) = SettingsTransferOutcome(
            categories = plan.categories.size,
            written = plan.puts.size,
            restored = plan.removes.size,
            changed = changes.size,
            skips = plan.skipCounts.filterValues { it > 0 },
            failedEffects = report.failures.keys.toList(),
            notificationPermission = report.failures.values.any { (it as? EffectException)?.reason == EffectException.Reason.NOTIFICATION_PERMISSION },
            restartNeeded = report.restartNeeded,
            dataNotUndone = dataNotUndone,
            credentialsLocked = credentialsLocked,
            backupFailure = backupFailure,
            launcherIconPending = launcherIconPending,
            languageChanged = SettingsEffects.Entry.LANGUAGE in report.applied,
            deployments = deployments,
        )

        /**
         * The outcome of the JSON document [json], or null when it is none; unknown reasons and entries (of a newer
         * version) are left out.
         * zh-CN: JSON 文档 [json] 对应的结果, 不是结果时为 null; 未知的原因与入口 (更新的版本) 被略去.
         */
        private fun deploymentOf(json: Any?): DeploymentResult? {
            val map = json as? Map<*, *> ?: return null
            val kind = DeploymentKind.entries.firstOrNull { it.name == map["kind"] } ?: return null
            return DeploymentResult(
                kind,
                map["store"] as? String ?: return null,
                map["name"] as? String ?: return null,
                map["target"] as? String ?: return null,
                DeploymentException.Reason.entries.firstOrNull { it.name == map["failure"] },
                map["detail"] as? String,
            )
        }

        @JvmStatic
        fun fromJson(json: Any?): SettingsTransferOutcome? {
            val root = json as? Map<*, *> ?: return null
            val skips = LinkedHashMap<SkipReason, Int>()
            (root["skips"] as? Map<*, *>)?.let { map ->
                SkipReason.entries.forEach { reason -> SettingsJson.toInt(map[reason.name])?.takeIf { it > 0 }?.let { skips[reason] = it } }
            }
            return SettingsTransferOutcome(
                categories = SettingsJson.toInt(root["categories"]) ?: return null,
                written = SettingsJson.toInt(root["written"]) ?: return null,
                restored = SettingsJson.toInt(root["restored"]) ?: return null,
                changed = SettingsJson.toInt(root["changed"]) ?: return null,
                skips = skips,
                failedEffects = (root["failedEffects"] as? List<*>).orEmpty().mapNotNull { name -> SettingsEffects.Entry.entries.firstOrNull { it.name == name } },
                notificationPermission = root["notificationPermission"] == true,
                restartNeeded = root["restartNeeded"] == true,
                dataNotUndone = (root["dataNotUndone"] as? List<*>).orEmpty().filterIsInstance<String>().filter(SettingsArchive::isCategoryId),
                credentialsLocked = root["credentialsLocked"] == true,
                backupFailure = root["backupFailure"] as? String,
                launcherIconPending = root["launcherIconPending"] == true,
                languageChanged = root["languageChanged"] == true,
                deployments = (root["deployments"] as? List<*>).orEmpty().mapNotNull(::deploymentOf),
            )
        }
    }
}
