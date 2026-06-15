package org.autojs.autojs.ui.edit.editor.service

import org.autojs.autojs.ui.edit.editor.engine.EditorBreakpoint
import org.autojs.autojs.ui.edit.editor.engine.EditorBreakpointChangeListener
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

class EditorBreakpointModelStore(
    private val currentEditor: () -> ScriptEditorEngine,
) {

    private var changeListener: EditorBreakpointChangeListener? = null
    private var listenerOwner: ScriptEditorEngine? = null

    fun getBreakpoints(): Collection<EditorBreakpoint> {
        return currentEditor().breakpoints.values
    }

    fun getBreakpointsSnapshot(): List<EditorBreakpoint> {
        return currentEditor().breakpointsSnapshot
    }

    fun addOrRemoveAtCurrentLine() {
        currentEditor().addOrRemoveBreakpointAtCurrentLine()
    }

    fun removeAll() {
        currentEditor().removeAllBreakpoints()
    }

    fun setChangeListener(listener: EditorBreakpointChangeListener?) {
        if (listener == null) {
            clearChangeListener()
            return
        }
        changeListener = listener
        bindChangeListenerToCurrentEditor()
    }

    fun clearChangeListener() {
        listenerOwner?.setBreakpointChangeListener(null)
        listenerOwner = null
        changeListener = null
    }

    fun onEditorChanged() {
        bindChangeListenerToCurrentEditor()
    }

    private fun bindChangeListenerToCurrentEditor() {
        val listener = changeListener ?: return
        val owner = currentEditor()
        if (listenerOwner === owner) {
            return
        }
        listenerOwner?.setBreakpointChangeListener(null)
        listenerOwner = owner
        owner.setBreakpointChangeListener(listener)
    }
}
