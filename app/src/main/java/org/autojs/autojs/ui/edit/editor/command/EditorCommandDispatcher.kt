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

    fun onCompletionClick(completions: CodeCompletions, position: Int) {
        currentEditor().insert(completions[position].insertText)
    }

    fun onCompletionLongClick(completions: CodeCompletions, position: Int) {
        val completion = completions[position]
        val editor = currentEditor()
        when {
            completion.insertText == "/" -> editor.toggleComment()
            completion.insertText == "=" -> editor.clear()
            completion.insertText == "-" -> editor.deleteLine()
            completion.insertText == "(" -> editor.moveCursor(-1)
            completion.insertText == ")" -> editor.moveCursor(1)
            completion.insertText == "[" -> editor.jumpToLineStart()
            completion.insertText == "]" -> editor.jumpToLineEnd()
            completion.insertText == "{" -> editor.jumpToStart()
            completion.insertText == "}" -> editor.jumpToEnd()
            completion.insertText == "<" -> editor.jumpToPrevLine()
            completion.insertText == ">" -> editor.jumpToNextLine()
            completion.url != null -> showManual(completion.url, completion.hint)
        }
    }

    fun onModuleLongClick(module: Module) {
        showManual(module.url, module.name)
    }

    fun onPropertyClick(module: Module, property: Property) {
        val editor = currentEditor()
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
}
