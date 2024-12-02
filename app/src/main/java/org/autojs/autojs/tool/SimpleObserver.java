package org.autojs.autojs.tool;

import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

/**
 * Created by Stardust on Aug 20, 2017.
 */
public class SimpleObserver<T> implements Observer<T> {
    @Override
    public void onSubscribe(@NonNull Disposable d) {
        /* Empty body. */
    }

    @Override
    public void onNext(@NonNull T t) {
        /* Empty body. */
    }

    @Override
    public void onError(@NonNull Throwable e) {
        /* Empty body. */
    }

    @Override
    public void onComplete() {
        /* Empty body. */
    }
}
