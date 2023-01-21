package org.autojs.autojs.util;

import org.autojs.autojs.app.GlobalAppContext;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class Observers {

    @SuppressWarnings("rawtypes")
    private static final Consumer CONSUMER = ignored -> {

    };

    private static final Consumer<Throwable> TOAST_MESSAGE = e -> {
        e.printStackTrace();
        ViewUtils.showToast(GlobalAppContext.get(), e.getMessage());
    };

    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> emptyConsumer() {
        return CONSUMER;
    }

    public static Consumer<Throwable> toastMessage() {
        return TOAST_MESSAGE;
    }

    public static <T> Observer<T> emptyObserver() {
        return new Observer<>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(T t) {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        };

    }

}
