package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsBackupVault
import org.autojs.autojs.core.pref.archive.SettingsEffects
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

    /**
     * The summary of a settings file (import step 1, T10): file, export time, host, device and counts, then the
     * warnings (newer format, another host package, appendix C Q26), the credentials sealed for another device (D23)
     * and the categories unknown to this version.
     * zh-CN: 设置文件摘要 (导入第 1 步, T10): 文件, 导出时间, 宿主, 设备与计数, 然后是警告 (更高格式, 其他宿主包名, 附录 C Q26), 以其他
     * 设备密钥加密的凭据 (D23), 以及本版本不认识的分类.
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
            if (inspection.locked.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_import_credentials_locked))
            if (inspection.unknown.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_import_unknown_categories, inspection.unknown.size))
        }
    }

    /**
     * The result page of an import or an undo (T10): the counts, the skips by reason, then what needs attention.
     * zh-CN: 导入或撤销的结果页 (T10): 计数, 按原因的跳过数, 然后是需要注意的事项.
     */
    @JvmStatic
    fun outcomeText(context: Context, outcome: SettingsTransferOutcome): String = buildString {
        append(context.getString(R.string.settings_transfer_result_counts, outcome.categories, outcome.written, outcome.restored, outcome.changed))
        if (outcome.skips.isNotEmpty()) {
            append("\n\n").append(context.getString(R.string.settings_transfer_result_skipped))
            outcome.skips.forEach { (reason, count) -> append("\n  ").append(context.getString(skipRes(reason), count)) }
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
    fun undoSummary(context: Context, last: SettingsBackupVault.LastOperation?): String = when (last) {
        null -> context.getString(R.string.settings_transfer_undo_none)
        else -> context.getString(R.string.settings_transfer_undo_summary, timeText(last.time), last.categories.size)
    }

    /**
     * The confirmation of "Undo last import"; until P10.3 it tells that the data categories of the import stay
     * (appendix C Q27), only when the import touched any.
     * zh-CN: "撤销上次导入" 的确认文字; 在 P10.3 之前, 只在导入触及数据分类时说明它们不会撤销 (附录 C Q27).
     */
    @JvmStatic
    fun undoConfirmText(context: Context, last: SettingsBackupVault.LastOperation): String = buildString {
        append(context.getString(R.string.settings_transfer_undo_confirm, last.categories.size, timeText(last.time)))
        if (last.data.isNotEmpty()) append("\n\n").append(context.getString(R.string.settings_transfer_result_data_not_undone))
    }

    @JvmStatic
    fun timeText(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}
