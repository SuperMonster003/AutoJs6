package org.autojs.autojs.storage.history

import android.content.Context
import org.autojs.autojs6.R
import java.util.zip.ZipException

/**
 * Localized failure text for trash preview / restore errors.
 * zh-CN: 回收站预览 / 恢复失败的本地化提示文案.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashFailureText {

    fun describe(context: Context, e: Throwable): String = when (e) {
        is TrashBlobMissingException -> context.getString(R.string.error_trash_data_missing)
        is TrashArchiveExtractor.PartialFailure -> context.getString(
            R.string.text_trash_restore_partial,
            e.report.restoredEntries,
            e.report.failed.size,
        )
        is ZipException -> context.getString(R.string.error_trash_data_damaged)
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }
}
