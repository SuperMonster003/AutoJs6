package org.autojs.sdk37.fixture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Mirrors the method-handle lookup performed by embedded Kotlin/IntelliJ containers. */
public final class UnsafeAtomicsProbe {
    private static final MethodHandle ADD_INT;
    private static final MethodHandle ADD_LONG;
    private static final long INT_OFFSET;
    private static final long LONG_OFFSET;

    private volatile int intValue;
    private volatile long longValue;

    static {
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Object unsafe = type.getMethod("getUnsafe").invoke(null);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ADD_INT = lookup.findVirtual(type, "getAndAddInt",
                    MethodType.methodType(int.class, Object.class, long.class, int.class)).bindTo(unsafe);
            ADD_LONG = lookup.findVirtual(type, "getAndAddLong",
                    MethodType.methodType(long.class, Object.class, long.class, long.class)).bindTo(unsafe);
            INT_OFFSET = (Long) type.getMethod("objectFieldOffset", java.lang.reflect.Field.class)
                    .invoke(unsafe, UnsafeAtomicsProbe.class.getDeclaredField("intValue"));
            LONG_OFFSET = (Long) type.getMethod("objectFieldOffset", java.lang.reflect.Field.class)
                    .invoke(unsafe, UnsafeAtomicsProbe.class.getDeclaredField("longValue"));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public int addInt(int delta) throws Throwable {
        return (int) ADD_INT.invokeExact((Object) this, INT_OFFSET, delta);
    }

    public long addLong(long delta) throws Throwable {
        return (long) ADD_LONG.invokeExact((Object) this, LONG_OFFSET, delta);
    }
}
