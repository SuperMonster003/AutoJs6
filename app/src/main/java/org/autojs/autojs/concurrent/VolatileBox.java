package org.autojs.autojs.concurrent;


/**
 * Created by Stardust on 2017/5/8.
 * Modified by SuperMonster003 as of Jun 11, 2022.
 */
public class VolatileBox<T> {

    private volatile T mValue;

    public VolatileBox() {

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
