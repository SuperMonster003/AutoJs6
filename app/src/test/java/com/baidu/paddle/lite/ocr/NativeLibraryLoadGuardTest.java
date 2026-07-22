package com.baidu.paddle.lite.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NativeLibraryLoadGuardTest {

    @Test
    public void loadsDependencyBeforeNativeLibraryOnlyOnce() throws Throwable {
        NativeLibraryLoadGuard guard = new NativeLibraryLoadGuard();
        List<String> calls = new ArrayList<>();

        guard.load(() -> calls.add("opencv"), () -> calls.add("native"));
        guard.load(() -> fail("dependency reloaded"), () -> fail("native library reloaded"));

        assertEquals(Arrays.asList("opencv", "native"), calls);
        assertTrue(guard.isLoaded());
    }

    @Test
    public void failedLoadCanBeRetried() throws Throwable {
        NativeLibraryLoadGuard guard = new NativeLibraryLoadGuard();
        AtomicInteger dependencyLoads = new AtomicInteger();
        AtomicInteger nativeLoads = new AtomicInteger();

        try {
            guard.load(dependencyLoads::incrementAndGet, () -> {
                nativeLoads.incrementAndGet();
                throw new UnsatisfiedLinkError("first attempt");
            });
            fail("Expected the first load to fail");
        } catch (UnsatisfiedLinkError expected) {
            assertEquals("first attempt", expected.getMessage());
        }

        assertFalse(guard.isLoaded());
        guard.load(dependencyLoads::incrementAndGet, nativeLoads::incrementAndGet);

        assertEquals(2, dependencyLoads.get());
        assertEquals(2, nativeLoads.get());
        assertTrue(guard.isLoaded());
    }

    @Test
    public void concurrentCallWaitsForPublishedSuccess() throws Throwable {
        NativeLibraryLoadGuard guard = new NativeLibraryLoadGuard();
        AtomicInteger dependencyLoads = new AtomicInteger();
        AtomicInteger nativeLoads = new AtomicInteger();
        CountDownLatch dependencyEntered = new CountDownLatch(1);
        CountDownLatch allowDependencyToFinish = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();

        Runnable load = () -> {
            try {
                guard.load(() -> {
                    dependencyLoads.incrementAndGet();
                    dependencyEntered.countDown();
                    if (!allowDependencyToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to finish dependency load");
                    }
                }, nativeLoads::incrementAndGet);
            } catch (Throwable error) {
                synchronized (failures) {
                    failures.add(error);
                }
            }
        };

        Thread first = new Thread(load, "native-load-first");
        Thread second = new Thread(load, "native-load-second");
        first.start();
        assertTrue(dependencyEntered.await(5, TimeUnit.SECONDS));
        second.start();
        allowDependencyToFinish.countDown();
        first.join(5_000);
        second.join(5_000);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertTrue(failures.toString(), failures.isEmpty());
        assertEquals(1, dependencyLoads.get());
        assertEquals(1, nativeLoads.get());
        assertTrue(guard.isLoaded());
    }
}
