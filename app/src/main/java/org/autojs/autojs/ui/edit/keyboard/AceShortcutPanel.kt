package org.autojs.autojs.ui.edit.keyboard

import org.autojs.autojs.model.autocomplete.CodeCompletion
import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs6.R

object AceShortcutPanel {

    private const val COMMAND_PREFIX = "__autojs6_ace_shortcut:"
    private const val ACTION_PREFIX_COMMAND = "command:"

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

    private fun command(label: String, command: Command, iconResId: Int = 0): ShortcutItem {
        val insertText = command.insertText
        return ShortcutItem(
            completion = CodeCompletion(label, null, insertText, iconResId),
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

    private val TOP_ROW = listOf(
        command("ESC", Command.Escape),
        command("Up", Command.MoveUp, R.drawable.ic_keyboard_arrow_up_black_48dp),
        command("TAB", Command.Tab),
    )

    private val BOTTOM_ROW = listOf(
        command("★", Command.ActionFavorites, R.drawable.ic_star_black_48dp),
        command("Left", Command.MoveLeft, R.drawable.ic_keyboard_arrow_left_black_48dp),
        command("Down", Command.MoveDown, R.drawable.ic_keyboard_arrow_down_black_48dp),
        command("Right", Command.MoveRight, R.drawable.ic_keyboard_arrow_right_black_48dp),
    )

    val topRowCompletions: CodeCompletions = CodeCompletions.fromCompletions(
        TOP_ROW.map { it.completion },
    )

    val bottomRowCompletions: CodeCompletions = CodeCompletions.fromCompletions(
        BOTTOM_ROW.map { it.completion },
    )

    private val actionsByInsertText: Map<String, Action> = (TOP_ROW + BOTTOM_ROW)
        .mapNotNull { it.action }
        .associateBy { it.insertText }

    private val actionsById: Map<String, Action> = (TOP_ROW + BOTTOM_ROW)
        .mapNotNull { it.action }
        .associateBy { it.id }

    fun commandOf(completion: CodeCompletion): Command? {
        return Command.fromInsertText(completion.insertText)
    }

    fun actionOf(completion: CodeCompletion): Action? {
        return actionsByInsertText[completion.insertText]
    }

    fun actionOf(command: Command): Action? {
        return actionsByInsertText[command.insertText]
    }

    fun actionById(id: String?): Action? {
        return id?.let { actionsById[it] }
    }
}
