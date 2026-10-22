package org.autojs.autojs.concurrent;

import androidx.annotation.Nullable;

import org.autojs.autojs.runtime.exception.ScriptInterruptedException;

import java.util.concurrent.TimeUnit;

/**
 * Created by Stardust on Oct 28, 2017.
 * <p>
 * A wait returns once a value is set, whether it was set before the wait began or during it (a null value counts as set),
 * a wakeup without a value does not end it, every waiter wakes, and an interrupt ends the wait of {@code blockedGet} with a
 * ScriptInterruptedException (docs/augment/ROADMAP.md appendix C Q55). A timeout of 0 waits without a bound and a negative
 * one is rejected while no value is set, as {@link Object#wait(long)} does.
 * zh-CN: 值在等待前或等待中设置 (null 也算已设置), 等待都会返回; 没有值的唤醒不结束等待; 全部等待者都被唤醒; 中断以 ScriptInterruptedException
 * 结束 blockedGet 的等待 (docs/augment/ROADMAP.md 附录 C Q55). 超时为 0 时不限时, 为负数且尚未设置值时被拒绝, 同 {@link Object#wait(long)}.
 */
public class VolatileDispose<T> {

    private volatile T mValue;

    // Guarded by this.
    private boolean mIsSet;

    @Nullable
    public T blockedGet() {
        return blockedGet(0);
    }

    @Nullable
    public T blockedGet(long timeout) {
        synchronized (this) {
            try {
                awaitValue(timeout);
            } catch (InterruptedException e) {
                throw new ScriptInterruptedException();
            }
            return mValue;
        }
    }

    public T blockedGetOrThrow(Class<? extends RuntimeException> exception) {
        synchronized (this) {
            try {
                awaitValue(0);
            } catch (InterruptedException e) {
                throw newException(exception);
            }
            return mValue;
        }
    }

    public T blockedGetOrThrow(Class<? extends RuntimeException> exception, long timeout, T defaultValue) {
        synchronized (this) {
            try {
                awaitValue(timeout);
            } catch (InterruptedException e) {
                throw newException(exception);
            }
            return mValue == null ? defaultValue : mValue;
        }
    }

    public void setAndNotify(T value) {
        synchronized (this) {
            mValue = value;
            mIsSet = true;
            notifyAll();
        }
    }

    // Called holding the lock of this.
    private void awaitValue(long timeout) throws InterruptedException {
        if (timeout == 0L) {
            while (!mIsSet) {
                wait();
            }
            return;
        }
        if (timeout < 0L && !mIsSet) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        long nanos = TimeUnit.MILLISECONDS.toNanos(timeout);
        long start = System.nanoTime();
        while (!mIsSet) {
            long remaining = nanos - (System.nanoTime() - start);
            if (remaining <= 0L) {
                return;
            }
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
    }

    private static RuntimeException newException(Class<? extends RuntimeException> exception) {
        try {
            return exception.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            return new RuntimeException(e);
        }
    }

}
