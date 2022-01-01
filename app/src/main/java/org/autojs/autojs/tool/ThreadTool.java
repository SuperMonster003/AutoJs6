package org.autojs.autojs.tool;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ThreadTool {

    public static boolean wait(Supplier<Boolean> condition, int timeout) throws InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            while (!condition.get()) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            result.set(true);
        });
        thread.start();
        thread.join(timeout);
        if (thread.isAlive()) {
            thread.interrupt();
        }
        return result.get();
    }

    public static boolean wait(Supplier<Boolean> condition) throws InterruptedException {
        return wait(condition, 10 * 1000);
    }
}
