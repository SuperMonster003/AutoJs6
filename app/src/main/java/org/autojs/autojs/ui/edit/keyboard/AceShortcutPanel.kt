package org.autojs.autojs.ui.edit.keyboard

import org.autojs.autojs.model.autocomplete.CodeCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletions

object AceShortcutPanel {

    private const val COMMAND_PREFIX = "__autojs6_ace_shortcut:"

    enum class Command(private val id: String) {
        Escape("escape"),
        MoveUp("move_up"),
        MoveDown("move_down"),
        MoveLeft("move_left"),
        MoveRight("move_right"),
        Tab("tab"),
        ActionFavorites("action_favorites");

        val insertText: String
            get() = COMMAND_PREFIX + id

        companion object {
            fun fromInsertText(insertText: String): Command? {
                if (!insertText.startsWith(COMMAND_PREFIX)) {
                    return null
                }
                val id = insertText.removePrefix(COMMAND_PREFIX)
                return values().firstOrNull { it.id == id }
            }
        }
    }

    val firstRowCompletions: CodeCompletions = CodeCompletions.fromCompletions(
        listOf(
            command("ESC", Command.Escape),
            command("^", Command.MoveUp),
            command("TAB", Command.Tab),
            insert("("),
            insert(")"),
            insert("/"),
            insert("="),
            insert(","),
            insert(";"),
            insert("\""),
            insert("'"),
        ),
    )

    val secondRowCompletions: CodeCompletions = CodeCompletions.fromCompletions(
        listOf(
            command("*", Command.ActionFavorites),
            command("<", Command.MoveLeft),
            command("v", Command.MoveDown),
            command(">", Command.MoveRight),
            insert("{"),
            insert("}"),
            insert("["),
            insert("]"),
            insert("`"),
            insert("<"),
            insert(">"),
            insert("-"),
        ),
    )

    fun commandOf(completion: CodeCompletion): Command? {
        return Command.fromInsertText(completion.insertText)
    }

    private fun command(label: String, command: Command): CodeCompletion {
        return CodeCompletion(label, null, command.insertText)
    }

    private fun insert(text: String): CodeCompletion {
        return CodeCompletion(text, null, text)
    }
}
