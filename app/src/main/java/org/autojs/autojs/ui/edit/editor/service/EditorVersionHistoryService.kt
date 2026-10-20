package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.net.Uri
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.HistoryRetentionPolicy
import org.autojs.autojs.storage.history.HistoryUriUtils
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.storage.history.VersionHistoryEditorBridge
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingCodec
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.nio.charset.Charset

class EditorVersionHistoryService(
    private val context: Context,
    private val currentEditor: () -> ScriptEditorEngine,
    private val onRestoredToEditor: () -> Unit,
    private val onRestoredUi: () -> Unit,
    private val onSaveRequested: () -> Unit,
) {

    // Held strongly here: the bridge only keeps a weak reference, so the callbacks live as long as this service.
    // zh-CN: 此处强引用持有: bridge 仅弱引用, 回调随本服务存活.
    private val editorCallbacks = VersionHistoryEditorBridge.Callbacks(
        onRestoreToEditor = { restoredText ->
            currentEditor().text = restoredText
            onRestoredToEditor()
        },
        onRestoredUi = onRestoredUi,
        onRestoreToEditorAndSave = { restoredText ->
            currentEditor().text = restoredText
            onRestoredToEditor()
            onSaveRequested()
        },
    )

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
            showMessageDialog(R.string.error_unable_to_display_version_history_as_current_file_path_is_unknown)
            return
        }

        VersionHistoryController(context).showForEditor(uri, editorCallbacks)
    }

    /**
     * Ask for an optional label, then store the current editor text as a pinned MANUAL revision.
     * zh-CN: 询问可选标签, 然后把当前编辑器文本存为固定的 MANUAL revision.
     */
    fun showCreateSnapshotDialog(uri: Uri?, charset: Charset, hadBom: Boolean) {
        if (uri == null) {
            showMessageDialog(R.string.error_unable_to_create_snapshot_as_current_file_path_is_unknown)
            return
        }
        val logicalPath = HistoryUriUtils.toLogicalPathOrNull(uri)
        if (logicalPath == null) {
            showMessageDialog(R.string.text_snapshot_requires_internal_storage_file)
            return
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_create_snapshot)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(context.getString(R.string.text_snapshot_label_optional), "", true) { _, input ->
                    createSnapshot(logicalPath, currentEditor().text, charset, hadBom, input.toString())
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .apply { widgetThemeColor() }
                .build()
        }
    }

    private fun createSnapshot(logicalPath: String, text: String, charset: Charset, hadBom: Boolean, label: String) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            val result = runCatching {
                val body = EditorEncodingCodec.encodeStrict(text, charset)
                val bytes = if (hadBom) StringUtils.bomBytes(charset) + body else body
                HistoryRepository(appCtx).recordManualSnapshot(logicalPath, bytes, charset.name(), hadBom, label)
                    ?: throw IllegalStateException("Snapshot was not stored: $logicalPath")
            }
            AndroidSchedulers.mainThread().scheduleDirect {
                result.onSuccess {
                    ViewUtils.showToast(context, R.string.text_snapshot_created)
                }.onFailure {
                    it.printStackTrace()
                    ViewUtils.showToast(context, it.message ?: it.toString(), true)
                }
            }
        }
    }

    private fun showMessageDialog(contentRes: Int) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(contentRes)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    private fun shouldTrackHistory(oldBytes: ByteArray, newBytes: ByteArray): Boolean {
        val limit = HistoryKeys.maxFileSizeToTrackBytes()
        if (HistoryRetentionPolicy.isUnlimited(limit)) return true

        return oldBytes.size.toLong() <= limit && newBytes.size.toLong() <= limit
    }
}
