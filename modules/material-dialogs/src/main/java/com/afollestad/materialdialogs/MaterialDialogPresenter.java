package com.afollestad.materialdialogs;

import androidx.annotation.NonNull;

/**
 * Shows MaterialDialogs inside another view hierarchy instead of their own windows, for a host whose content has no
 * window of its own (the AutoJs6 ui-mode preview, whose content is embedded in another app's surface).
 *
 * <p>Implemented by a {@link android.content.Context} given to a dialog's builder, and resolved through
 * {@link android.content.ContextWrapper} layers like {@link MaterialDialogThemeOwner}. A presented dialog never gets a
 * window: {@link MaterialDialog#isShowing()} follows the presentation, and {@link MaterialDialog#dismiss()} (also
 * through {@link MaterialDialog#cancel()}) removes it with {@link #dismissPresentedDialog(MaterialDialog)} and then
 * notifies the dismiss listener the way a window dismissal would. Both methods are called on the main thread.
 */
public interface MaterialDialogPresenter {

    /**
     * Takes over showing {@code dialog}: its view ({@link MaterialDialog#getView()}, still the content of the dialog's
     * unused window) is moved into the presenter's hierarchy. Returns false to let the dialog show its own window.
     */
    boolean presentDialog(@NonNull MaterialDialog dialog);

    /** Removes a dialog that {@link #presentDialog(MaterialDialog)} showed; called once per presentation. */
    void dismissPresentedDialog(@NonNull MaterialDialog dialog);
}
