package org.autojs.autojs.ui.edit.keyboard

import org.autojs.autojs.model.autocomplete.CodeCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletions

object AceShortcutPanel {

    private const val COMMAND_PREFIX = "__autojs6_ace_shortcut:"
    private const val ACTION_PREFIX_COMMAND = "command:"
    private const val ACTION_PREFIX_INSERT = "insert:"

    enum class Command(val id: String, val mutatesText: Boolean) {
        Escape("escape", mutatesText = false),
        MoveUp("move_up", mutatesText = false),
        MoveDown("move_down", mutatesText = false),
        MoveLeft("move_left", mutatesText = false),
        MoveRight("move_right", mutatesText = false),
        Tab("tab", mutatesText = true),
        ActionFavorites("action_favorites", mutatesText = false);

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

    data class Action(
        val id: String,
        val label: String,
        val insertText: String,
        val command: Command?,
        val mutatesText: Boolean,
    )

    private data class ShortcutItem(
        val completion: CodeCompletion,
        val action: Action?,
    )

    private fun command(label: String, command: Command): ShortcutItem {
        val insertText = command.insertText
        return ShortcutItem(
            completion = CodeCompletion(label, null, insertText),
            action = when (command) {
                Command.ActionFavorites -> null
                else -> Action(
                    id = ACTION_PREFIX_COMMAND + command.id,
                    label = label,
                    insertText = insertText,
                    command = command,
                    mutatesText = command.mutatesText,
                )
            },
        )
    }

    private fun insert(text: String): ShortcutItem {
        return ShortcutItem(
            completion = CodeCompletion(text, null, text),
            action = Action(
                id = ACTION_PREFIX_INSERT + text,
                label = text,
                insertText = text,
                command = null,
                mutatesText = true,
            ),
        )
    }

    private val FIRST_ROW = listOf(
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
    )

    private val SECOND_ROW = listOf(
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
    )

    val firstRowCompletions: CodeCompletions = CodeCompletions.fromCompletions(
        FIRST_ROW.map { it.completion },
    )

    val secondRowCompletions: CodeCompletions = CodeCompletions.fromCompletions(
        SECOND_ROW.map { it.completion },
    )

    private val actionsByInsertText: Map<String, Action> = (FIRST_ROW + SECOND_ROW)
        .mapNotNull { it.action }
        .associateBy { it.insertText }

    private val actionsById: Map<String, Action> = (FIRST_ROW + SECOND_ROW)
        .mapNotNull { it.action }
        .associateBy { it.id }

    fun commandOf(completion: CodeCompletion): Command? {
        return Command.fromInsertText(completion.insertText)
    }

    fun actionOf(completion: CodeCompletion): Action? {
        return actionsByInsertText[completion.insertText]
    }

    fun actionById(id: String?): Action? {
        return id?.let { actionsById[it] }
    }
}
