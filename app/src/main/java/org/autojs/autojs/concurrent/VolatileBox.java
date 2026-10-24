package org.autojs.autojs.concurrent;

import org.autojs.autojs.runtime.exception.ScriptInterruptedException;

import java.util.concurrent.TimeUnit;

/**
 * Created by Stardust on May 8, 2017.
 * Modified by SuperMonster003 as of Jun 11, 2022.
 * <p>
 * A wait ({@code block}, {@code blockedGet}, {@code blockedGetOrThrow}) ends with a notification ({@code setAndNotify},
 * {@code unblock}): one sent while threads wait wakes all of them, one sent while none waits is kept for the next wait, which
 * then returns at once; a wakeup without a notification does not end a wait, and an interrupt ends the wait of {@code block}
 * and {@code blockedGet} with a ScriptInterruptedException (docs/augment/ROADMAP.md appendix C Q130). A timeout of 0 waits
 * without a bound and a negative one is rejected unless a notification is kept, as {@link Object#wait(long)} does.
 * zh-CN: 等待 (block, blockedGet, blockedGetOrThrow) 以通知 (setAndNotify, unblock) 结束: 有线程等待时发出的通知唤醒全部等待者, 无人等待时发出的通知
 * 留给下一次等待, 该等待立即返回; 没有通知的唤醒不结束等待; 中断以 ScriptInterruptedException 结束 block 与 blockedGet 的等待
 * (docs/augment/ROADMAP.md 附录 C Q130). 超时为 0 时不限时, 为负数且没有留下的通知时被拒绝, 同 {@link Object#wait(long)}.
 */
public class VolatileBox<T> {

    private volatile T mValue;

    // Guarded by this: a notification kept for the next wait, the threads waiting, and the notifications sent to them.
    private boolean mPending;
    private int mWaiters;
    private long mNotifications;

    public VolatileBox() {
        /* Empty body. */
    }

    public VolatileBox(T value) {
        set(value);
    }

    public T get() {
        return mValue;
    }

    public void set(T value) {
        mValue = value;
    }

    /**
     * Sets the value and notifies (see the class comment), as {@code com.stardust.concurrent.VolatileBox.setAndNotify} of
     * Auto.js 4; that class name resolves to this class through {@link org.autojs.autojs.rhino.StardustClassAliases}.
     * zh-CN: 设置值并发出通知 (见类注释), 对应 Auto.js 4 的 {@code com.stardust.concurrent.VolatileBox.setAndNotify};
     * 该类名经 {@link org.autojs.autojs.rhino.StardustClassAliases} 解析到本类.
     */
    public void setAndNotify(T value) {
        synchronized (this) {
            mValue = value;
            notifyWaiters();
        }
    }

    public boolean isNull() {
        return mValue == null;
    }

    public boolean notNull() {
        return mValue != null;
    }

    public void unblock() {
        synchronized (this) {
            notifyWaiters();
        }
    }

    public void unblock(T value) {
        setAndNotify(value);
    }

    public void block() {
        block(0);
    }

    public void block(long timeout) {
        synchronized (this) {
            try {
                awaitNotification(timeout);
            } catch (InterruptedException e) {
                throw new ScriptInterruptedException();
            }
        }
    }

    public T blockedGet() {
        return blockedGet(0);
    }

    public T blockedGet(long timeout) {
        synchronized (this) {
            block(timeout);
            return mValue;
        }
    }

    public T blockedGetOrThrow(Class<? extends RuntimeException> exception) {
        return blockedGetOrThrow(0, exception);
    }

    public T blockedGetOrThrow(long timeout, Class<? extends RuntimeException> exception) {
        synchronized (this) {
            try {
                awaitNotification(timeout);
            } catch (InterruptedException e) {
                throw newException(exception);
            }
            return mValue;
        }
    }

    // Called holding the lock of this.
    private void notifyWaiters() {
        if (mWaiters > 0) {
            mNotifications++;
        } else {
            mPending = true;
        }
        notifyAll();
    }

    // Called holding the lock of this.
    private void awaitNotification(long timeout) throws InterruptedException {
        if (mPending) {
            mPending = false;
            return;
        }
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        long notifications = mNotifications;
        long nanos = TimeUnit.MILLISECONDS.toNanos(timeout);
        long start = System.nanoTime();
        mWaiters++;
        try {
            while (notifications == mNotifications) {
                if (timeout == 0L) {
                    wait();
                    continue;
                }
                long remaining = nanos - (System.nanoTime() - start);
                if (remaining <= 0L) {
                    return;
                }
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        } finally {
            mWaiters--;
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
