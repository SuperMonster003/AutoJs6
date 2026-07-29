package org.autojs.autojs.core.ui.dialog;

import androidx.annotation.Nullable;

final class DialogDismissDispatcher {

    private DialogDismissDispatcher() {
        throw new UnsupportedOperationException();
    }

    static void dispatch(Runnable internalCallback, @Nullable Runnable userCallback, Runnable release) {
        try {
            try {
                internalCallback.run();
            } finally {
                if (userCallback != null) {
                    userCallback.run();
                }
            }
        } finally {
            release.run();
        }
    }

}
