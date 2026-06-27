package org.autojs.autojs.ui.edit.editor.service

import android.view.View
import io.reactivex.Observable
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

class EditorTextProvider(
    private val hostView: View,
    private val currentEditor: () -> ScriptEditorEngine,
) {

    fun getTextForSaving(): Observable<String> {
        return Observable.create<String> { emitter ->
            hostView.post {
                runCatching {
                    currentEditor().getTextForSaving { latestText ->
                        if (!emitter.isDisposed) {
                            emitter.onNext(latestText)
                            emitter.onComplete()
                        }
                    }
                }.onFailure { error ->
                    if (!emitter.isDisposed) {
                        emitter.onError(error)
                    }
                }
            }
        }
    }
}
