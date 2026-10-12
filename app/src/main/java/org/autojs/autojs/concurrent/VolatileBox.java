package org.autojs.autojs.concurrent;

/**
 * Created by Stardust on May 8, 2017.
 * Modified by SuperMonster003 as of Jun 11, 2022.
 */
public class VolatileBox<T> {

    private volatile T mValue;

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
     * Sets the value and wakes one blocked reader, as {@code com.stardust.concurrent.VolatileBox.setAndNotify} of
     * Auto.js 4; that class name resolves to this class through {@link org.autojs.autojs.rhino.StardustClassAliases}.
     * zh-CN: 设置值并唤醒一个阻塞的读取方, 对应 Auto.js 4 的 {@code com.stardust.concurrent.VolatileBox.setAndNotify};
     * 该类名经 {@link org.autojs.autojs.rhino.StardustClassAliases} 解析到本类.
     */
    public void setAndNotify(T value) {
        mValue = value;
        synchronized (this) {
            notify();
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
            notify();
        }
    }

    public void unblock(T value) {
        synchronized (this) {
            mValue = value;
            notify();
        }
    }

    public void block() {
        block(0);
    }

    public void block(long timeout) {
        synchronized (this) {
            try {
                wait(timeout);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public T blockedGet() {
        return blockedGet(0);
    }

    public T blockedGet(long timeout) {
        block(timeout);
        return mValue;
    }

    public T blockedGetOrThrow(Class<? extends RuntimeException> exception) {
        return blockedGetOrThrow(0, exception);
    }

    public T blockedGetOrThrow(long timeout, Class<? extends RuntimeException> exception) {
        synchronized (this) {
            try {
                wait(timeout);
            } catch (InterruptedException e) {
                try {
                    throw exception.newInstance();
                } catch (InstantiationException | IllegalAccessException e1) {
                    throw new RuntimeException(e1);
                }
            }
            return mValue;
        }
    }
}
