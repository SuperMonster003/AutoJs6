package org.autojs.autojs.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Created by SuperMonster003 on May 26, 2022.
 */
public class ThreadUtils {

    private static final int DEFAULT_TIMEOUT = 10 * 1000;
    private static final int DEFAULT_INTERVAL = 200;

    private static final ExecutorService waitExecutor = Executors.newSingleThreadExecutor();

    public static boolean wait(Supplier<Boolean> condition) throws InterruptedException {
        return wait(condition, DEFAULT_TIMEOUT);
    }

    public static boolean wait(Supplier<Boolean> condition, int timeout) throws InterruptedException {
        return wait(condition, timeout, DEFAULT_INTERVAL);
    }

    @SuppressWarnings("BusyWait")
    public static boolean wait(Supplier<Boolean> condition, int timeout, int interval) throws InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        Future<?> future = waitExecutor.submit(() -> {
            while (!condition.get()) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException ignore) {
                    result.set(false);
                    return;
                }
            }
            result.set(true);
        });
        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException ignore) {
            // Ignored.
        }
        future.cancel(true);
        return result.get();
    }
}
