package com.afollestad.materialdialogs;

import androidx.annotation.Nullable;

/**
 * Implemented by a {@link android.content.Context} which supplies scoped MaterialDialog theming.
 *
 * <p>The owner is resolved through {@link android.content.ContextWrapper} layers, so callers can
 * keep using themed contexts without losing the enclosing Activity's dialog palette.
 */
public interface MaterialDialogThemeOwner {

    @Nullable
    MaterialDialogThemeDelegate getMaterialDialogThemeDelegate();
}
