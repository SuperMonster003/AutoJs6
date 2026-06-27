package org.autojs.autojs.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs6.R

object EditorSettingActionRegistry {

    private const val ACTION_ID_PREFIX = "editor_setting:"

    private data class Spec(
        @param:StringRes val preferenceKeyRes: Int,
        @param:StringRes val titleRes: Int,
    )

    private val specs = listOf(
        Spec(R.string.key_editor_engine, R.string.text_editor_engine),
        Spec(R.string.key_editor_theme, R.string.text_editor_theme),
        Spec(R.string.key_ace_editor_font, R.string.text_ace_editor_font),
        Spec(R.string.key_editor_text_size, R.string.text_text_size),
        Spec(R.string.key_ace_word_wrap_enabled, R.string.text_ace_word_wrap_enabled),
        Spec(R.string.key_editor_word_wrap_indent_style, R.string.text_editor_word_wrap_indent_style),
        Spec(R.string.key_editor_log_button_behavior, R.string.text_editor_log_button_behavior),
        Spec(R.string.key_editor_auto_show_log_bottom_sheet_on_run, R.string.text_editor_auto_show_log_bottom_sheet_on_run),
        Spec(R.string.key_editor_shortcut_input_bar, R.string.text_editor_shortcut_input_bar),
        Spec(R.string.key_ace_lsp_enabled, R.string.text_ace_lsp_enabled),
        Spec(R.string.key_editor_pinch_to_zoom_strategy, R.string.text_pinch_to_zoom),
        Spec(R.string.key_editor_gutter_width_mode, R.string.text_editor_gutter_width_mode),
        Spec(R.string.key_editor_breakpoint_markers_enabled, R.string.text_editor_breakpoint_markers_enabled),
        Spec(R.string.key_ace_line_numbers_enabled, R.string.text_ace_line_numbers_enabled),
        Spec(R.string.key_editor_fold_markers_enabled, R.string.text_editor_fold_markers_enabled),
    )

    fun actionIdForPreferenceKey(preferenceKey: String): String = "$ACTION_ID_PREFIX$preferenceKey"

    fun preferenceKeyFromActionId(actionId: String): String? {
        return actionId.removePrefix(ACTION_ID_PREFIX)
            .takeIf { it.length != actionId.length }
    }

    fun labelForPreferenceKey(context: Context, preferenceKey: String): String? {
        val spec = specForPreferenceKey(context, preferenceKey) ?: return null
        return context.getString(
            R.string.text_set_editor_setting_action,
            context.getString(spec.titleRes),
        )
    }

    fun isKnownPreferenceKey(context: Context, preferenceKey: String): Boolean {
        return specForPreferenceKey(context, preferenceKey) != null
    }

    private fun specForPreferenceKey(context: Context, preferenceKey: String): Spec? {
        return specs.firstOrNull { context.getString(it.preferenceKeyRes) == preferenceKey }
    }
}
