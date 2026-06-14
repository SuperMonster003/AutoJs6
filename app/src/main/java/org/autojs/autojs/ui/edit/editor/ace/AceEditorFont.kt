package org.autojs.autojs.ui.edit.editor.ace

enum class AceEditorFont(
    val preferenceValue: String,
    val cssFontFamily: String,
) {
    JETBRAINS_MONO(
        "jetbrains_mono",
        "\"JetBrains Mono\", \"Source Code Pro\", monospace",
    ),
    SOURCE_CODE_PRO(
        "source_code_pro",
        "\"Source Code Pro\", \"JetBrains Mono\", monospace",
    ),
    SYSTEM_MONOSPACE(
        "system_monospace",
        "monospace",
    );

    companion object {
        fun fromPreferenceValue(value: String?): AceEditorFont {
            return entries.firstOrNull { it.preferenceValue == value } ?: JETBRAINS_MONO
        }
    }
}
