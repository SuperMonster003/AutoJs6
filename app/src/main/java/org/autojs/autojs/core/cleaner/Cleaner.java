package org.autojs.autojs.core.cleaner;

import android.util.Log;

import org.autojs.autojs.core.ref.MonitorResource;
import org.autojs.autojs.core.ref.NativeObjectReference;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by SuperMonster003 on Dec 22, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 22, 2023.
public final class Cleaner {

    public static final Cleaner instance = new Cleaner();
    public static final AtomicInteger pointer = new AtomicInteger();
    public static final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

    static {
        Executors.newFixedThreadPool(1).execute(() -> {
            while (true) {
                try {
                    NativeObjectReference<?> nativeObjectReference = (NativeObjectReference<?>) Cleaner.referenceQueue.remove();
                    if (nativeObjectReference == null) {
                        continue;
                    }
                    instance.cleanup(nativeObjectReference);
                } catch (InterruptedException e) {
                    /* Ignored. */
                }
                break;
            }
        });
    }

    public void cleanup(NativeObjectReference<?> nativeObjectReference) {
        try {
            pointer.decrementAndGet();
            RegisteredCleaners.registeredCleaners.remove(nativeObjectReference);
            long pointer = nativeObjectReference.pointer;
            if (pointer == 0L) return;
            nativeObjectReference.cleaner.cleanup(pointer);
            nativeObjectReference.pointer = 0L;
        } catch (Throwable throwable) {
            Log.w(Cleaner.class.getSimpleName(), "cleanup error: " + nativeObjectReference.pointer, throwable);
        }
    }

    public void cleanup(MonitorResource resource, ICleaner cleaner) {
        resource.getPointer();
        NativeObjectReference<MonitorResource> resourceNativeCleaner = new NativeObjectReference<>(resource, referenceQueue, cleaner);
        resourceNativeCleaner.pointer = resource.getPointer();
        resource.setNativeObjectReference(resourceNativeCleaner);
        RegisteredCleaners.registeredCleaners.add(resourceNativeCleaner);
        pointer.incrementAndGet();
    }

}
