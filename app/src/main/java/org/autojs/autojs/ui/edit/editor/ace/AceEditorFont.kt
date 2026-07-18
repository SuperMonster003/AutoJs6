package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import androidx.annotation.StringRes
import org.autojs.autojs6.R

enum class AceEditorFont(
    val preferenceValue: String,
    @param:StringRes val displayNameRes: Int,
    val author: String,
    val license: String,
    val cssFontFamily: String,
) {
    SYSTEM_MONOSPACE(
        "system_monospace",
        R.string.entry_ace_editor_font_system_monospace,
        "monospace / Typeface.MONOSPACE",
        "System font",
        "monospace",
    ),
    IOSEVKA(
        "iosevka",
        R.string.entry_ace_editor_font_iosevka,
        "Renzhi Li (be5invis) / Iosevka Project",
        "SIL Open Font License 1.1",
        "\"Iosevka\", monospace",
    ),
    IOSEVKA_SLAB(
        "iosevka_slab",
        R.string.entry_ace_editor_font_iosevka_slab,
        "Renzhi Li (be5invis) / Iosevka Project",
        "SIL Open Font License 1.1",
        "\"Iosevka Slab\", \"Iosevka\", monospace",
    ),
    JETBRAINS_MONO(
        "jetbrains_mono",
        R.string.entry_ace_editor_font_jetbrains_mono,
        "JetBrains; Philipp Nurullin / Konstantin Bulenkov",
        "SIL Open Font License 1.1",
        "\"JetBrains Mono\", \"Iosevka\", monospace",
    ),
    FIRA_CODE(
        "fira_code",
        R.string.entry_ace_editor_font_fira_code,
        "Nikita Prokopov; based on Fira Mono",
        "SIL Open Font License 1.1",
        "\"Fira Code\", \"Iosevka\", monospace",
    ),
    CASCADIA_CODE(
        "cascadia_code",
        R.string.entry_ace_editor_font_cascadia_code,
        "Microsoft; Aaron Bell / Saja Typeworks",
        "SIL Open Font License 1.1",
        "\"Cascadia Code\", \"Iosevka\", monospace",
    ),
    INTEL_ONE_MONO(
        "intel_one_mono",
        R.string.entry_ace_editor_font_intel_one_mono,
        "Intel; Frere-Jones Type with Intel Brand Team and VMLY&R",
        "SIL Open Font License 1.1",
        "\"Intel One Mono\", \"Iosevka\", monospace",
    ),
    IBM_PLEX_MONO(
        "ibm_plex_mono",
        R.string.entry_ace_editor_font_ibm_plex_mono,
        "IBM BX&D / Mike Abbink / Bold Monday",
        "SIL Open Font License 1.1",
        "\"IBM Plex Mono\", \"Iosevka\", monospace",
    ),
    HACK(
        "hack",
        R.string.entry_ace_editor_font_hack,
        "Source Foundry Authors; Chris Simpkins / David van Gemeren",
        "MIT + upstream Bitstream Vera / DejaVu notices",
        "\"Hack\", \"Iosevka\", monospace",
    ),
    SOURCE_CODE_PRO(
        "source_code_pro",
        R.string.entry_ace_editor_font_source_code_pro,
        "Adobe Originals; Paul D. Hunt / Teo Tuominen",
        "SIL Open Font License 1.1",
        "\"Source Code Pro\", \"Iosevka\", monospace",
    );

    fun displayName(context: Context): String = context.getString(displayNameRes)

    fun dialogLabel(context: Context): String = "${displayName(context)}\n$author"

    companion object {
        fun fromPreferenceValue(value: String?): AceEditorFont {
            return entries.firstOrNull { it.preferenceValue == value } ?: IOSEVKA
        }
    }
}
