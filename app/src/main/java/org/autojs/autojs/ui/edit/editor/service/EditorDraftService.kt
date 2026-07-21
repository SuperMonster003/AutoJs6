package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.file.StableDraftFileHelper
import java.util.concurrent.atomic.AtomicLong

class EditorDraftService(
    private val context: Context,
    draftKeyPath: String?,
    private val currentText: () -> String,
    private val shouldSaveDraft: () -> Boolean,
    private val restoreDraftText: (String) -> Unit,
    private val onRestorePendingChanged: (Boolean) -> Unit,
    private val log: (String) -> Unit,
) {

    private var draftKeyPath = draftKeyPath
    private var stableDraftFileHelper = StableDraftFileHelper(context, draftKeyPath)
    private var restoreDisposable: Disposable? = null
    private var rebindDisposable: Disposable? = null
    private var pendingRestorePath: String? = null
    private val draftWriteGeneration = AtomicLong(0L)
    private val oldDraftHelpersPendingDeletion = mutableListOf<StableDraftFileHelper>()

    fun restoreDraftForThisSession(savedInstanceState: Bundle?) {
        savedInstanceState?.getString(KEY_TEXT)?.let { draftText ->
            restoreDraftText(draftText)
        } ?: savedInstanceState?.getString(KEY_PATH)?.let { path ->
            pendingRestorePath = path
            onRestorePendingChanged(true)
            restoreDisposable?.dispose()
            restoreDisposable = Observable.just(path)
                .observeOn(Schedulers.io())
                .map { PFiles.read(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    onRestorePendingChanged(false)
                    restoreDisposable = null
                }
                .subscribe({ draftText ->
                    restoreDraftText(draftText)
                    pendingRestorePath = null
                }, Throwable::printStackTrace)
        }
    }

    fun destroy() {
        draftWriteGeneration.incrementAndGet()
        restoreDisposable?.dispose()
        restoreDisposable = null
        rebindDisposable?.dispose()
        rebindDisposable = null
    }

    /** Rebinds lifecycle draft persistence after Save As changes the active document path. */
    fun rebindDraftKeyPath(newDraftKeyPath: String?) {
        if (newDraftKeyPath.isNullOrBlank() || newDraftKeyPath == draftKeyPath) return

        val oldHelper = stableDraftFileHelper
        val newHelper = StableDraftFileHelper(context, newDraftKeyPath)
        val writeGeneration = draftWriteGeneration.incrementAndGet()
        draftKeyPath = newDraftKeyPath
        stableDraftFileHelper = newHelper

        rebindDisposable?.dispose()
        rebindDisposable = null
        if (!shouldSaveDraft()) {
            oldDraftHelpersPendingDeletion += oldHelper
            deletePendingOldDrafts()
            newHelper.deleteDraft()
            return
        }
        oldDraftHelpersPendingDeletion += oldHelper

        // Preserve the current dirty snapshot under the new key before removing the old-key
        // draft. A failed migration intentionally leaves the old recovery copy in place.
        // zh-CN: 删除旧 key 草稿前, 先把当前未保存快照写入新 key. 若迁移失败,
        // 则有意保留旧恢复副本.
        val text = currentText()
        rebindDisposable = Observable.fromCallable {
            newHelper.saveDraft(text) {
                draftWriteGeneration.get() == writeGeneration
            } != null
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { rebindDisposable = null }
            .subscribe({ saved ->
                if (saved) deletePendingOldDrafts()
            }, Throwable::printStackTrace)
    }

    fun saveDraftToInstanceState(outState: Bundle): Boolean {
        draftWriteGeneration.incrementAndGet()
        rebindDisposable?.dispose()
        rebindDisposable = null
        pendingRestorePath?.let { path ->
            outState.putString(KEY_PATH, path)
            return true
        }
        if (!shouldSaveDraft()) {
            deletePendingOldDrafts()
            return false
        }

        val text = currentText()
        if (text.length < SMALL_DRAFT_TEXT_THRESHOLD) {
            log("saveDraftText, length: ${text.length}")
            outState.putString(KEY_TEXT, text)
            deletePendingOldDrafts()
            return true
        }

        log("saveDraftFile, length: ${text.length}")
        val saved = stableDraftFileHelper.saveDraft(text)?.let { tmp ->
            outState.putString(KEY_PATH, tmp.path)
            true
        } == true
        if (saved) deletePendingOldDrafts()
        return saved
    }

    fun deleteDraft() {
        draftWriteGeneration.incrementAndGet()
        stableDraftFileHelper.deleteDraft()
        deletePendingOldDrafts()
    }

    private fun deletePendingOldDrafts() {
        oldDraftHelpersPendingDeletion.forEach(StableDraftFileHelper::deleteDraft)
        oldDraftHelpersPendingDeletion.clear()
    }

    private companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_PATH = "path"
        private const val SMALL_DRAFT_TEXT_THRESHOLD = 256 * 1024
    }
}
