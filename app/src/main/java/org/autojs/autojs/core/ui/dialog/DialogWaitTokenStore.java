package org.autojs.autojs.core.ui.dialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.core.looper.Loopers;

import java.util.concurrent.atomic.AtomicReference;

final class DialogWaitTokenStore {

    private final AtomicReference<Loopers.WaitToken> mCurrent = new AtomicReference<>();

    void replace(@NonNull Loopers.WaitToken waitToken) {
        Loopers.WaitToken previous = mCurrent.getAndSet(waitToken);
        if (previous != null) {
            previous.release();
        }
    }

    @Nullable
    Loopers.WaitToken detach() {
        return mCurrent.getAndSet(null);
    }

    void detach(@NonNull Loopers.WaitToken waitToken) {
        mCurrent.compareAndSet(waitToken, null);
    }

    void release(@NonNull Loopers.WaitToken waitToken) {
        mCurrent.compareAndSet(waitToken, null);
        waitToken.release();
    }

}
