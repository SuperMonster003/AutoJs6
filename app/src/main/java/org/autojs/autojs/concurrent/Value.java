package org.autojs.autojs.concurrent;

/**
 * Created by Stardust on Dec 27, 2017.
 */
public class Value<T> {

    private T mValue;

    public Value() {
        /* Empty body. */
    }

    public Value(T value) {
        mValue = value;
    }

    public T get() {
        return mValue;
    }

    public void set(T value) {
        mValue = value;
    }

}
