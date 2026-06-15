package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.file.StableDraftFileHelper
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

class EditorDraftService(
    context: Context,
    draftKeyPath: String?,
    private val currentEditor: () -> ScriptEditorEngine,
    private val shouldSaveDraft: () -> Boolean,
    private val restoreDraftText: (String) -> Unit,
    private val log: (String) -> Unit,
) {

    private val stableDraftFileHelper = StableDraftFileHelper(context, draftKeyPath)

    fun restoreDraftForThisSession(savedInstanceState: Bundle?) {
        savedInstanceState?.getString(KEY_TEXT)?.let { draftText ->
            restoreDraftText(draftText)
        } ?: savedInstanceState?.getString(KEY_PATH)?.let { path ->
            Observable.just(path)
                .observeOn(Schedulers.io())
                .map { PFiles.read(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ draftText ->
                    restoreDraftText(draftText)
                }, Throwable::printStackTrace)
        }
    }

    fun saveDraftToInstanceState(outState: Bundle): Boolean {
        if (!shouldSaveDraft()) {
            return false
        }

        val text = currentEditor().text
        if (text.length < SMALL_DRAFT_TEXT_THRESHOLD) {
            log("saveDraftText, length: ${text.length}")
            outState.putString(KEY_TEXT, text)
            return true
        }

        log("saveDraftFile, length: ${text.length}")
        stableDraftFileHelper.saveDraft(text)?.let { tmp ->
            outState.putString(KEY_PATH, tmp.path)
        }
        return true
    }

    fun deleteDraft() {
        stableDraftFileHelper.deleteDraft()
    }

    private companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_PATH = "path"
        private const val SMALL_DRAFT_TEXT_THRESHOLD = 256 * 1024
    }
}
