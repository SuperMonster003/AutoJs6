package com.baidu.paddle.lite.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NativeLibraryLoadGuardTest {

    @Test
    public void loadsNativeLibraryOnlyOnce() throws Throwable {
        NativeLibraryLoadGuard guard = new NativeLibraryLoadGuard();
        AtomicInteger nativeLoads = new AtomicInteger();

        guard.load(nativeLoads::incrementAndGet);
        guard.load(() -> fail("native library reloaded"));

        assertEquals(1, nativeLoads.get());
        assertTrue(guard.isLoaded());
    }

    @Test
    public void failedLoadCanBeRetried() throws Throwable {
        NativeLibraryLoadGuard guard = new NativeLibraryLoadGuard();
        AtomicInteger nativeLoads = new AtomicInteger();

        try {
            guard.load(() -> {
                nativeLoads.incrementAndGet();
                throw new UnsatisfiedLinkError("first attempt");
            });
            fail("Expected the first load to fail");
        } catch (UnsatisfiedLinkError expected) {
            assertEquals("first attempt", expected.getMessage());
        }

        assertFalse(guard.isLoaded());
        guard.load(nativeLoads::incrementAndGet);

        assertEquals(2, nativeLoads.get());
        assertTrue(guard.isLoaded());
    }

    @Test
    public void concurrentCallWaitsForPublishedSuccess() throws Throwable {
        NativeLibraryLoadGuard guard = new NativeLibraryLoadGuard();
        AtomicInteger nativeLoads = new AtomicInteger();
        CountDownLatch loadEntered = new CountDownLatch(1);
        CountDownLatch allowLoadToFinish = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();

        Runnable load = () -> {
            try {
                guard.load(() -> {
                    nativeLoads.incrementAndGet();
                    loadEntered.countDown();
                    if (!allowLoadToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to finish the native load");
                    }
                });
            } catch (Throwable error) {
                synchronized (failures) {
                    failures.add(error);
                }
            }
        };

        Thread first = new Thread(load, "native-load-first");
        Thread second = new Thread(load, "native-load-second");
        first.start();
        assertTrue(loadEntered.await(5, TimeUnit.SECONDS));
        second.start();
        allowLoadToFinish.countDown();
        first.join(5_000);
        second.join(5_000);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertTrue(failures.toString(), failures.isEmpty());
        assertEquals(1, nativeLoads.get());
        assertTrue(guard.isLoaded());
    }
}
