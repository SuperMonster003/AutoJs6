package org.autojs.autojs.ui.edit.editor.loading

import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

interface EditorTextLoadTarget {

    val isUserTouching: Boolean

    fun loadRestoredText(text: String, undoRedoEnabled: Boolean)
    fun loadSmallText(text: String)
    fun beginProgressiveTextLoad()
    fun appendTextChunk(chunk: CharSequence)
    fun finishProgressiveTextLoad(markClean: Boolean)
    fun cancelProgressiveTextLoad()
    fun beginStreamedLargeTextLoad()
    fun finishStreamedLargeTextLoad(markClean: Boolean)
    fun cancelStreamedLargeTextLoad()
}

class EngineTextLoadTarget(
    private val editor: ScriptEditorEngine,
) : EditorTextLoadTarget {

    override val isUserTouching: Boolean
        get() = editor.isUserTouching

    override fun loadRestoredText(text: String, undoRedoEnabled: Boolean) {
        editor.text = text
        editor.markUndoRedoBaselineAsUnchanged()
        editor.setRedoUndoEnabled(undoRedoEnabled)
    }

    override fun loadSmallText(text: String) {
        editor.setLoadingText(true)
        editor.setRedoUndoEnabled(false)
        try {
            editor.text = text
            editor.markUndoRedoBaselineAsUnchanged()
        } finally {
            editor.setRedoUndoEnabled(true)
            editor.setLoadingText(false)
        }
    }

    override fun beginProgressiveTextLoad() {
        editor.setLoadingText(true)
        editor.setRedoUndoEnabled(false)
        editor.text = ""
    }

    override fun appendTextChunk(chunk: CharSequence) {
        editor.appendTextChunk(chunk)
    }

    override fun finishProgressiveTextLoad(markClean: Boolean) {
        editor.setLoadingText(false)
        editor.setRedoUndoEnabled(true)
        if (markClean) {
            editor.markUndoRedoBaselineAsUnchanged()
        }
        editor.setProgress(false)
    }

    override fun cancelProgressiveTextLoad() {
        editor.setLoadingText(false)
        editor.setRedoUndoEnabled(true)
        editor.setProgress(false)
    }

    override fun beginStreamedLargeTextLoad() {
        editor.beginLargeTextLoading()
    }

    override fun finishStreamedLargeTextLoad(markClean: Boolean) {
        editor.setLoadingText(false)
        editor.setRedoUndoEnabled(false)
        if (markClean) {
            editor.markUndoRedoBaselineAsUnchanged()
        }
    }

    override fun cancelStreamedLargeTextLoad() {
        editor.cancelLargeTextLoading()
    }
}
