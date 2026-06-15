package org.autojs.autojs.ui.edit.editor.command

import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs.model.indices.Module
import org.autojs.autojs.model.indices.Property
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine

class EditorCommandDispatcher(
    private val currentEditor: () -> ScriptEditorEngine,
    private val showManual: (urlSuffix: String, title: String) -> Unit,
    private val hideFunctionsKeyboard: (showSoftInput: Boolean) -> Unit,
) {

    fun insert(text: CharSequence?) {
        text?.let { currentEditor().insert(it.toString()) }
    }

    fun insert(line: Int, text: String?) {
        currentEditor().insert(line, text)
    }

    fun copyLine() {
        currentEditor().copyLine()
    }

    fun deleteLine() {
        currentEditor().deleteLine()
    }

    fun clear() {
        currentEditor().clear()
    }

    fun toggleComment() {
        currentEditor().toggleComment()
    }

    fun beautifyCode() {
        currentEditor().beautifyCode()
    }

    fun undo() {
        currentEditor().undo()
    }

    fun redo() {
        currentEditor().redo()
    }

    fun replaceSelection() {
        currentEditor().replaceSelection()
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
        currentEditor().addOrRemoveBreakpointAtCurrentLine()
    }

    fun removeAllBreakpoints() {
        currentEditor().removeAllBreakpoints()
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
        val propertyText = if (property.isVariable) property.key else "${property.key}()"
        if (property.isGlobal) {
            insert(propertyText)
        } else {
            insert("${module.name}.$propertyText")
        }
        if (!property.isVariable) {
            currentEditor().moveCursor(-1)
        }
        hideFunctionsKeyboard(true)
    }

    fun onPropertyLongClick(module: Module, property: Property) {
        val url = property.url?.takeIf { it.isNotEmpty() } ?: module.url
        showManual(url, property.key)
    }
}
