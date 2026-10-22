package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsBackupVault
import org.autojs.autojs.core.pref.archive.SettingsEffects
import org.autojs.autojs.core.pref.deploy.DeploymentException
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs6.R
import java.text.DateFormat
import java.util.Date

/**
 * Messages of the settings wizards (docs/settings/ROADMAP.md P6.4 / P7.5): one per [Failure], that is per reason of
 * `SettingsArchiveException`, for other I/O errors and for an interrupted operation; the summary of a settings file
 * (import step 1), the result page of an import or an undo, and the texts of "Undo last import".
 * zh-CN: 设置向导的提示 (ROADMAP P6.4 / P7.5): 每种 [Failure] 一条, 即 `SettingsArchiveException` 的每种原因, 其他 I/O 错误与被中断的
 * 操作; 设置文件的摘要 (导入第 1 步), 导入或撤销的结果页, 以及 "撤销上次导入" 的文字.
 */
object SettingsTransferMessages {

    @StringRes
    @JvmStatic
    fun failureRes(failure: Failure): Int = when (failure) {
        Failure.TOO_LARGE -> R.string.settings_transfer_error_too_large
        Failure.NOT_AN_ARCHIVE -> R.string.settings_transfer_error_not_an_archive
        Failure.INVALID_MANIFEST -> R.string.settings_transfer_error_invalid_manifest
        Failure.MISSING_FILE -> R.string.settings_transfer_error_missing_file
        Failure.INVALID_FILE -> R.string.settings_transfer_error_invalid_file
        Failure.LOCKED -> R.string.settings_transfer_error_locked
        Failure.IO -> R.string.settings_transfer_error_io
        Failure.INTERRUPTED -> R.string.settings_transfer_error_interrupted
    }

    /** The size limit as the messages show it, e.g. "16 MB". zh-CN: 提示中显示的大小上限, 例如 "16 MB". */
    @JvmStatic
    fun sizeLimit(): String = "${SettingsArchive.MAX_BYTES / (1024 * 1024)} MB"

    @JvmStatic
    fun failureText(context: Context, failure: Failure, detail: String?): String = when (failure) {
        Failure.TOO_LARGE -> context.getString(failureRes(failure), sizeLimit())
        Failure.IO -> context.getString(failureRes(failure), detail.orEmpty())
        else -> context.getString(failureRes(failure))
    }

    /** The label of a skip reason on the result page (D9). zh-CN: 结果页中跳过原因的标签 (D9). */
    @StringRes
    @JvmStatic
    fun skipRes(reason: SkipReason): Int = when (reason) {
        SkipReason.UNKNOWN_KEY -> R.string.settings_transfer_skip_unknown_key
        SkipReason.TYPE_MISMATCH -> R.string.settings_transfer_skip_type_mismatch
        SkipReason.INVALID_VALUE -> R.string.settings_transfer_skip_invalid_value
        SkipReason.TARGET_MISSING -> R.string.settings_transfer_skip_target_missing
        SkipReason.NEWER_FORMAT -> R.string.settings_transfer_skip_newer_format
        SkipReason.DECLINED -> R.string.settings_transfer_skip_declined
    }

    /** The name of an effect entry (T12), as its settings page names it. zh-CN: 生效入口 (T12) 的名称, 与其设置页一致. */
    @StringRes
    @JvmStatic
    fun effectRes(entry: SettingsEffects.Entry): Int = when (entry) {
        SettingsEffects.Entry.THEME_COLOR -> R.string.text_theme_color
        SettingsEffects.Entry.NIGHT_MODE -> R.string.text_night_mode
        SettingsEffects.Entry.LANGUAGE -> R.string.text_app_language
        SettingsEffects.Entry.LAUNCHER_ICON -> R.string.text_launcher_icon_title
        SettingsEffects.Entry.TIMED_TASK_BACKEND -> R.string.text_timed_task_backend_title
        SettingsEffects.Entry.FOREGROUND_SERVICE -> R.string.text_foreground_service
    }

    /** The title of a deployment item (T18). zh-CN: 部署项的标题 (T18). */
    @StringRes
    @JvmStatic
    fun deploymentRes(kind: DeploymentKind): Int = when (kind) {
        DeploymentKind.CREATE_DIRECTORY -> R.string.settings_transfer_deploy_create_directory
        DeploymentKind.DOWNLOAD_EDITOR_FONT -> R.string.settings_transfer_deploy_download_font
    }

    /** Why a deployment item failed (T18). zh-CN: 部署项失败的原因 (T18). */
    @StringRes
    @JvmStatic
    fun deploymentFailureRes(reason: DeploymentException.Reason): Int = when (reason) {
        DeploymentException.Reason.PERMISSION -> R.string.settings_transfer_deploy_failure_permission
        DeploymentException.Reason.IO -> R.string.settings_transfer_deploy_failure_io
        DeploymentException.Reason.NETWORK -> R.string.settings_transfer_deploy_failure_network
        DeploymentException.Reason.UNAVAILABLE -> R.string.settings_transfer_deploy_failure_unavailable
        DeploymentException.Reason.PREREQUISITE -> R.string.text_ace_editor_plugin_required
        DeploymentException.Reason.CANCELLED -> R.string.settings_transfer_deploy_cancelled
    }

    /**
     * The summary of a settings file (import step 1, T10): file, export time, host, device and counts, then the
     * warnings (newer format, another host package, appendix C Q26), the credentials sealed for another device (D23:
     * how to enter their password, P7.7, or that this device cannot open them) and the categories unknown to this
     * version.
     * zh-CN: 设置文件摘要 (导入第 1 步, T10): 文件, 导出时间, 宿主, 设备与计数, 然后是警告 (更高格式, 其他宿主包名, 附录 C Q26), 以其他
     * 设备密钥加密的凭据 (D23: 如何输入其口令 (P7.7), 或本机无法打开), 以及本版本不认识的分类.
     */
    @JvmStatic
    fun inspectionText(context: Context, inspection: SettingsImporter.Inspection): String {
        val manifest = inspection.manifest
        return buildString {
            append(
                context.getString(
                    R.string.settings_transfer_import_file,
                    inspection.fileName,
                    manifest.exportedAt.replace('T', ' '),
                    manifest.host.versionName,
                    manifest.host.versionCode,
                    manifest.device.model,
                    manifest.device.sdk,
                    manifest.categories.size + manifest.data.size,
                    manifest.categories.sumOf { it.entries } + manifest.data.sumOf { it.entries },
                )
            )
            inspection.warnings.forEach { warning ->
                append("\n\n").append(
                    when (warning) {
                        SettingsArchiveManifest.Warning.NEWER_FORMAT ->
                            context.getString(R.string.settings_transfer_import_newer_format, manifest.formatVersion, SettingsArchiveManifest.FORMAT_VERSION)
                        SettingsArchiveManifest.Warning.OTHER_HOST_PACKAGE ->
                            context.getString(R.string.settings_transfer_import_other_host, manifest.host.packageName, context.packageName)
                    }
                )
            }
            when {
                inspection.password.isNotEmpty() -> append("\n\n").append(context.getString(R.string.settings_transfer_import_credentials_password))
                inspection.locked.isNotEmpty() -> append("\n\n").append(context.getString(R.string.settings_transfer_import_credentials_locked))
            }
            if (inspection.unknown.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_import_unknown_categories, inspection.unknown.size))
        }
    }

    /**
     * The result page of an import or an undo (T10): the counts, what became of the data categories (P10.2), the skips
     * by reason, then what needs attention.
     * zh-CN: 导入或撤销的结果页 (T10): 计数, 数据分类的处理结果 (P10.2), 按原因的跳过数, 然后是需要注意的事项.
     */
    @JvmStatic
    fun outcomeText(context: Context, outcome: SettingsTransferOutcome): String = buildString {
        append(context.getString(R.string.settings_transfer_result_counts, outcome.categories, outcome.written, outcome.restored, outcome.changed))
        outcome.data.forEachIndexed { index, data ->
            val title = SettingCategory.ofIdOrNull(data.id)?.let { context.getString(it.titleRes) } ?: data.id
            append(if (index == 0) "\n\n" else "\n")
            append(
                when (val failure = data.failure) {
                    null -> when (val removed = data.removed) {
                        null -> context.getString(R.string.settings_transfer_result_data_imported, title, data.entries, data.added)
                        else -> context.getString(R.string.settings_transfer_result_data_removed, title, removed)
                    }
                    else -> context.getString(if (data.removed == null) R.string.settings_transfer_result_data_failed else R.string.settings_transfer_result_data_remove_failed, title, failure)
                }
            )
            if (data.kept > 0) append("\n  ").append(context.getString(R.string.settings_transfer_result_shortcuts_kept, data.kept))
        }
        if (outcome.restorableShortcuts.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_result_shortcuts_restore))
        if (outcome.skips.isNotEmpty()) {
            append("\n\n").append(context.getString(R.string.settings_transfer_result_skipped))
            outcome.skips.forEach { (reason, count) -> append("\n  ").append(context.getString(skipRes(reason), count)) }
        }
        if (outcome.deployments.isNotEmpty()) {
            val failed = outcome.deployments.filterNot { it.succeeded }
            append("\n\n").append(context.getString(R.string.settings_transfer_result_deployments, outcome.deployments.size - failed.size, failed.size))
            failed.forEach { result ->
                append("\n  ").append(context.getString(deploymentRes(result.kind))).append(" (").append(result.target).append("): ")
                append(context.getString(deploymentFailureRes(result.failure!!)))
            }
        }
        if (outcome.failedEffects.isNotEmpty()) {
            val names = outcome.failedEffects.joinToString(", ") { context.getString(effectRes(it)) }
            append("\n\n").append(context.getString(R.string.settings_transfer_result_effects_failed, names))
        }
        if (outcome.notificationPermission) append("\n\n").append(context.getString(R.string.settings_transfer_result_notification_permission))
        if (outcome.restartNeeded) append("\n\n").append(context.getString(R.string.settings_transfer_result_restart))
        if (outcome.launcherIconPending) append("\n\n").append(context.getString(R.string.settings_transfer_result_launcher_icon))
        if (outcome.credentialsLocked) append("\n\n").append(context.getString(R.string.settings_transfer_result_credentials_locked))
        if (outcome.dataNotUndone.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_result_data_not_undone))
        outcome.backupFailure?.let { append("\n\n").append(context.getString(R.string.settings_transfer_result_backup_failed, it)) }
    }

    /** The summary of "Undo last import": what it undoes, or that there is nothing. zh-CN: "撤销上次导入" 的摘要. */
    @JvmStatic
    fun undoSummary(context: Context, last: SettingsBackupVault.LastOperation?): String = when (last?.operation) {
        null -> context.getString(R.string.settings_transfer_undo_none)
        SettingsBackupVault.Operation.IMPORT -> context.getString(R.string.settings_transfer_undo_summary, timeText(last.time), last.categories.size + last.data.size)
        SettingsBackupVault.Operation.RESET -> context.getString(R.string.settings_transfer_undo_reset_summary, timeText(last.time), last.categories.size)
    }

    /**
     * The confirmation of "Undo last import"; it tells that the data categories of the import stay only when its
     * record is from before P10.3 ([SettingsBackupVault.LastOperation.dataNotUndone], appendix C Q27).
     * zh-CN: "撤销上次导入" 的确认文字; 只在其记录早于 P10.3 时说明数据分类不会撤销 ([SettingsBackupVault.LastOperation.dataNotUndone],
     * 附录 C Q27).
     */
    @JvmStatic
    fun undoConfirmText(context: Context, last: SettingsBackupVault.LastOperation): String = buildString {
        val res = if (last.operation == SettingsBackupVault.Operation.RESET) R.string.settings_transfer_undo_reset_confirm else R.string.settings_transfer_undo_confirm
        append(context.getString(res, last.categories.size + last.data.size - last.dataNotUndone.size, timeText(last.time)))
        if (last.dataNotUndone.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_result_data_not_undone))
    }

    @JvmStatic
    fun timeText(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}
