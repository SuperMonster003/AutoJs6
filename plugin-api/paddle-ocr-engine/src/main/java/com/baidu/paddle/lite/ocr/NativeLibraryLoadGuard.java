package com.baidu.paddle.lite.ocr;

import java.util.concurrent.locks.ReentrantLock;

/** Serializes native loading and only publishes success after the load finishes. */
final class NativeLibraryLoadGuard {

    @FunctionalInterface
    interface Loader {
        void load() throws Throwable;
    }

    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean loaded;

    void load(Loader nativeLibraryLoader) throws Throwable {
        if (loaded) {
            return;
        }
        lock.lock();
        try {
            if (loaded) {
                return;
            }
            nativeLibraryLoader.load();
            loaded = true;
        } finally {
            lock.unlock();
        }
    }

    boolean isLoaded() {
        return loaded;
    }
}
