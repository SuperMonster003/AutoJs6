package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs6.R

/**
 * Messages of the settings wizards (docs/settings/ROADMAP.md P6.4 / P7): one per [Failure], that is per reason of
 * `SettingsArchiveException` and for other I/O errors.
 * zh-CN: 设置向导的提示 (ROADMAP P6.4 / P7): 每种 [Failure] 一条, 即 `SettingsArchiveException` 的每种原因与其他 I/O 错误.
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
}
