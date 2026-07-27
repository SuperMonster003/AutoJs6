package com.afollestad.materialdialogs;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;

/**
 * Context-scoped hooks for applying a host palette to MaterialDialog without changing the
 * process-wide application theme.
 */
public interface MaterialDialogThemeDelegate {

    /** Called immediately before the dialog theme is selected and its layout is inflated. */
    default void onPrepareBuilder(@NonNull MaterialDialog.Builder builder) {
    }

    /** Creates the popup used by a dialog title's options button. */
    @NonNull
    default PopupMenu createOptionsPopupMenu(
            @NonNull Context context,
            @NonNull View anchor,
            int gravity
    ) {
        return new PopupMenu(context, anchor, gravity);
    }

    /** Called after all title options have been added and before the popup is shown. */
    default void onPrepareOptionsPopupMenu(@NonNull PopupMenu popupMenu) {
    }

    /** Called after the MaterialDialog's own views have been initialized. */
    default void onDialogCreated(@NonNull MaterialDialog dialog) {
    }

    /** Called from the dialog's internal show listener, after its content has been attached. */
    default void onDialogShown(@NonNull MaterialDialog dialog) {
    }

    /** Called after the dialog has been dismissed. Implementations must tolerate repeat calls. */
    default void onDialogDismissed(@NonNull MaterialDialog dialog) {
    }
}
