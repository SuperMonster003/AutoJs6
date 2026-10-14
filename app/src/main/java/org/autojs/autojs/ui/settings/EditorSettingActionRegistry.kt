package org.autojs.autojs.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs6.R

object EditorSettingActionRegistry {

    private const val ACTION_ID_PREFIX = "editor_setting:"
    private const val LEGACY_PINCH_TO_ZOOM_STRATEGY_KEY = "key_\$_editor_pinch_to_zoom_strategy"
    private const val PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED_KEY = "key_\$_editor_pinch_to_zoom_change_text_size_enabled"

    private data class Spec(
        @param:StringRes val preferenceKeyRes: Int,
        @param:StringRes val titleRes: Int,
    )

    private val specs = listOf(
        Spec(R.string.key_editor_engine, R.string.text_editor_engine),
        Spec(R.string.key_editor_theme_mode, R.string.text_editor_theme_mode),
        Spec(R.string.key_editor_theme, R.string.text_editor_theme),
        Spec(R.string.key_ace_editor_font, R.string.text_ace_editor_font),
        Spec(R.string.key_editor_text_size, R.string.text_text_size),
        Spec(R.string.key_editor_fullscreen_mode, R.string.text_editor_fullscreen_mode),
        Spec(R.string.key_ace_font_ligatures_enabled, R.string.text_ace_font_ligatures_enabled),
        Spec(R.string.key_ace_font_styles_enabled, R.string.text_ace_font_styles_enabled),
        Spec(R.string.key_ace_print_margin_enabled, R.string.text_ace_print_margin_enabled),
        Spec(R.string.key_ace_indent_guides_enabled, R.string.text_ace_indent_guides_enabled),
        Spec(R.string.key_ace_highlight_indent_guides_enabled, R.string.text_ace_highlight_indent_guides_enabled),
        Spec(R.string.key_ace_word_wrap_enabled, R.string.text_ace_word_wrap_enabled),
        Spec(R.string.key_editor_word_wrap_indent_style, R.string.text_editor_word_wrap_indent_style),
        Spec(R.string.key_ace_whitespaces_in_selection_enabled, R.string.text_ace_whitespaces_in_selection_enabled),
        Spec(R.string.key_ace_custom_scrollbar_enabled, R.string.text_ace_custom_scrollbar_enabled),
        Spec(R.string.key_ace_scrollbar_auto_hide_enabled, R.string.text_ace_scrollbar_auto_hide_enabled),
        Spec(R.string.key_editor_log_button_behavior, R.string.text_editor_log_button_behavior),
        Spec(R.string.key_editor_auto_show_log_bottom_sheet_on_run, R.string.text_editor_auto_show_log_bottom_sheet_on_run),
        Spec(R.string.key_editor_shortcut_input_bar, R.string.text_editor_shortcut_input_bar),
        Spec(R.string.key_ace_language_support_matrix, R.string.text_ace_language_support_matrix),
        Spec(R.string.key_ace_lsp_enabled, R.string.text_ace_lsp_enabled),
        Spec(R.string.key_ace_lsp_declaration_groups, R.string.text_ace_lsp_declaration_groups),
        Spec(R.string.key_ace_lsp_file_types, R.string.text_ace_lsp_file_types),
        Spec(R.string.key_ace_semantic_languages, R.string.text_ace_semantic_languages),
        Spec(R.string.key_ace_smart_hint_completion_trigger, R.string.text_ace_smart_hint_completion_trigger),
        Spec(R.string.key_ace_smart_hint_completion_min_chars, R.string.text_ace_smart_hint_completion_min_chars),
        Spec(R.string.key_ace_smart_hint_completion_delay, R.string.text_ace_smart_hint_completion_delay),
        Spec(R.string.key_ace_smart_hint_signature_trigger, R.string.text_ace_smart_hint_signature_trigger),
        Spec(R.string.key_ace_smart_hint_arrow_overload_cycling, R.string.text_ace_smart_hint_arrow_overload_cycling),
        Spec(R.string.key_ace_symbol_highlight_enabled, R.string.text_ace_symbol_highlight_enabled),
        Spec(R.string.key_ace_inline_completion_preview_enabled, R.string.text_ace_inline_completion_preview_enabled),
        Spec(R.string.key_editor_pinch_to_zoom_change_text_size_enabled, R.string.text_editor_pinch_to_zoom_change_text_size_enabled),
        Spec(R.string.key_ace_touch_magnifier_enabled, R.string.text_ace_touch_magnifier_enabled),
        Spec(R.string.key_editor_gutter_width_mode, R.string.text_editor_gutter_width_mode),
        Spec(R.string.key_editor_breakpoint_markers_enabled, R.string.text_editor_breakpoint_markers_enabled),
        Spec(R.string.key_ace_line_numbers_enabled, R.string.text_ace_line_numbers_enabled),
        Spec(R.string.key_editor_fold_markers_enabled, R.string.text_editor_fold_markers_enabled),
        Spec(R.string.key_ace_svg_gutter_icons_enabled, R.string.text_ace_svg_gutter_icons_enabled),
        Spec(R.string.key_editor_code_highlight_threshold_bytes, R.string.text_editor_code_highlight_threshold),
        Spec(R.string.key_editor_large_file_mode_threshold_bytes, R.string.text_editor_large_file_mode_threshold),
    )

    fun actionIdForPreferenceKey(preferenceKey: String): String = "$ACTION_ID_PREFIX$preferenceKey"

    fun preferenceKeyFromActionId(actionId: String): String? {
        return actionId.removePrefix(ACTION_ID_PREFIX)
            .takeIf { it.length != actionId.length }
            ?.let(::normalizePreferenceKey)
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
        val normalizedKey = normalizePreferenceKey(preferenceKey)
        return specs.firstOrNull { context.getString(it.preferenceKeyRes) == normalizedKey }
    }

    private fun normalizePreferenceKey(preferenceKey: String): String {
        return when (preferenceKey) {
            LEGACY_PINCH_TO_ZOOM_STRATEGY_KEY -> PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED_KEY
            else -> preferenceKey
        }
    }
}
