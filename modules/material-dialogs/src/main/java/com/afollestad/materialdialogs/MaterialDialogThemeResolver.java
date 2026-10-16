package com.afollestad.materialdialogs;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class MaterialDialogThemeResolver {

    private static final int MAX_CONTEXT_WRAPPER_DEPTH = 32;

    private MaterialDialogThemeResolver() {
    }

    /** The first {@link MaterialDialogPresenter} among the ContextWrapper layers of {@code initialContext}, or null. */
    @Nullable
    static MaterialDialogPresenter resolvePresenter(@NonNull Context initialContext) {
        Context context = initialContext;
        for (int depth = 0; depth < MAX_CONTEXT_WRAPPER_DEPTH; depth++) {
            if (context instanceof MaterialDialogPresenter) {
                return (MaterialDialogPresenter) context;
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            Context baseContext = ((ContextWrapper) context).getBaseContext();
            if (baseContext == null || baseContext == context) {
                return null;
            }
            context = baseContext;
        }
        return null;
    }

    @Nullable
    static MaterialDialogThemeDelegate resolve(@NonNull Context initialContext) {
        Context context = initialContext;
        for (int depth = 0; depth < MAX_CONTEXT_WRAPPER_DEPTH; depth++) {
            if (context instanceof MaterialDialogThemeOwner) {
                MaterialDialogThemeDelegate delegate =
                        ((MaterialDialogThemeOwner) context).getMaterialDialogThemeDelegate();
                if (delegate != null) {
                    return delegate;
                }
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            Context baseContext = ((ContextWrapper) context).getBaseContext();
            if (baseContext == null || baseContext == context) {
                return null;
            }
            context = baseContext;
        }
        return null;
    }
}
