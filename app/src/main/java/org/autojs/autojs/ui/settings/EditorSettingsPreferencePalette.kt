package org.autojs.autojs.ui.settings

import androidx.annotation.ColorInt
import org.autojs.autojs.ui.edit.theme.ColorThemeGenerator

internal data class EditorSettingsPreferencePalette(
    @param:ColorInt val categoryAndButton: Int,
    @param:ColorInt val title: Int,
    @param:ColorInt val subtitle: Int,
) {

    companion object {

        fun generate(
            @ColorInt background: Int,
            @ColorInt primary: Int,
        ): EditorSettingsPreferencePalette {
            val generated = ColorThemeGenerator.generate(
                backgroundArgb = background,
                titleArgb = primary,
                separation = ROLE_SEPARATION_PERCENT / 100.0,
            )
            return EditorSettingsPreferencePalette(
                categoryAndButton = generated.a,
                title = generated.b,
                subtitle = generated.c,
            )
        }

        private const val ROLE_SEPARATION_PERCENT = 48
    }
}
