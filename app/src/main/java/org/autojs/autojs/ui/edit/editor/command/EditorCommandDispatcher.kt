package org.autojs.autojs.ui.edit.editor.command

import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs.model.indices.Module
import org.autojs.autojs.model.indices.Property
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.service.EditorBreakpointModelStore

class EditorCommandDispatcher(
    private val currentEditor: () -> ScriptEditorEngine,
    private val breakpointModelStore: EditorBreakpointModelStore,
    private val showManual: (urlSuffix: String, title: String) -> Unit,
    private val hideFunctionsKeyboard: (showSoftInput: Boolean) -> Unit,
) {

    fun insert(text: CharSequence?) {
        text?.let { currentMutableEditor()?.insert(it.toString()) }
    }

    fun insert(line: Int, text: String?) {
        currentMutableEditor()?.insert(line, text)
    }

    fun copyLine() {
        currentEditor().copyLine()
    }

    fun deleteLine() {
        currentMutableEditor()?.deleteLine()
    }

    fun clear() {
        currentMutableEditor()?.clear()
    }

    fun toggleComment() {
        currentMutableEditor()?.toggleComment()
    }

    fun beautifyCode() {
        currentMutableEditor()?.beautifyCode()
    }

    fun foldAll(): Boolean {
        val editor = currentFoldingEditor() ?: return false
        editor.foldAll()
        return true
    }

    fun unfoldAll(): Boolean {
        val editor = currentFoldingEditor() ?: return false
        editor.unfoldAll()
        return true
    }

    fun undo() {
        currentMutableEditor()?.undo()
    }

    fun redo() {
        currentMutableEditor()?.redo()
    }

    fun replaceSelection() {
        currentMutableEditor()?.replaceSelection()
    }

    fun jumpTo(line: Int, column: Int) {
        currentEditor().jumpTo(line, column)
    }

    fun jumpToStart() {
        currentEditor().jumpToStart()
    }

    fun jumpToEnd() {
        currentEditor().jumpToEnd()
    }

    fun jumpToLineStart() {
        currentEditor().jumpToLineStart()
    }

    fun jumpToLineEnd() {
        currentEditor().jumpToLineEnd()
    }

    fun addOrRemoveBreakpointAtCurrentLine() {
        breakpointModelStore.addOrRemoveAtCurrentLine()
    }

    fun removeAllBreakpoints() {
        breakpointModelStore.removeAll()
    }

    fun onCompletionClick(completions: CodeCompletions, position: Int) {
        insert(completions[position].insertText)
    }

    fun onCompletionLongClick(completions: CodeCompletions, position: Int) {
        val completion = completions[position]
        when {
            completion.insertText == "/" -> toggleComment()
            completion.insertText == "=" -> clear()
            completion.insertText == "-" -> deleteLine()
            completion.insertText == "(" -> currentEditor().moveCursor(-1)
            completion.insertText == ")" -> currentEditor().moveCursor(1)
            completion.insertText == "[" -> jumpToLineStart()
            completion.insertText == "]" -> jumpToLineEnd()
            completion.insertText == "{" -> jumpToStart()
            completion.insertText == "}" -> jumpToEnd()
            completion.insertText == "<" -> currentEditor().jumpToPrevLine()
            completion.insertText == ">" -> currentEditor().jumpToNextLine()
            completion.url != null -> showManual(completion.url, completion.hint)
        }
    }

    fun onModuleLongClick(module: Module) {
        showManual(module.url, module.name)
    }

    fun onPropertyClick(module: Module, property: Property) {
        val editor = currentMutableEditor() ?: return
        val propertyText = if (property.isVariable) property.key else "${property.key}()"
        if (property.isGlobal) {
            editor.insert(propertyText)
        } else {
            editor.insert("${module.name}.$propertyText")
        }
        if (!property.isVariable) {
            editor.moveCursor(-1)
        }
        hideFunctionsKeyboard(true)
    }

    fun onPropertyLongClick(module: Module, property: Property) {
        val url = property.url?.takeIf { it.isNotEmpty() } ?: module.url
        showManual(url, property.key)
    }

    private fun currentMutableEditor(): ScriptEditorEngine? {
        return currentEditor().takeUnless { it.isReadOnly }
    }

    private fun currentFoldingEditor(): ScriptEditorEngine? {
        return currentEditor().takeIf { it.supportsCodeFolding && it.isReady && !it.isLoadingText }
    }
}
