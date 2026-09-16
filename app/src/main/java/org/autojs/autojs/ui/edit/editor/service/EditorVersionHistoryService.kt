package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.net.Uri
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.HistoryRetentionPolicy
import org.autojs.autojs.storage.history.HistoryUriUtils
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs6.R

class EditorVersionHistoryService(
    private val context: Context,
    private val currentEditor: () -> ScriptEditorEngine,
    private val onRestoredToEditor: () -> Unit,
    private val onRestoredUi: () -> Unit,
) {

    fun recordSavePreIfNeeded(
        uri: Uri,
        oldBytes: ByteArray?,
        newBytes: ByteArray,
        encodingName: String,
        hadBom: Boolean,
    ) {
        val logicalPath = HistoryUriUtils.toLogicalPathOrNull(uri) ?: return
        if (oldBytes == null || !shouldTrackHistory(oldBytes, newBytes)) {
            return
        }

        runCatching {
            HistoryRepository(context.applicationContext).recordSavePre(
                logicalPath = logicalPath,
                oldBytes = oldBytes,
                encodingName = encodingName,
                hadBom = hadBom,
                newBytes = newBytes,
            )
        }
    }

    fun showDialog(uri: Uri?) {
        if (uri == null) {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_display_version_history_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
            }
            return
        }

        VersionHistoryController(context).showForEditor(
            uri = uri,
            onRestoreToEditor = { restoredText ->
                currentEditor().text = restoredText
                onRestoredToEditor()
            },
            onRestoredUi = onRestoredUi,
        )
    }

    private fun shouldTrackHistory(oldBytes: ByteArray, newBytes: ByteArray): Boolean {
        val limit = HistoryPrefs.maxFileSizeToTrackBytes()
        if (HistoryRetentionPolicy.isUnlimited(limit)) return true

        return oldBytes.size.toLong() <= limit && newBytes.size.toLong() <= limit
    }
}
