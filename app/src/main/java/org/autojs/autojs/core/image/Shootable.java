package org.autojs.autojs.core.image;

public interface Shootable<T> {

    void recycle();

    boolean isRecycled();

    T setOneShot(boolean b);

    default T oneShot() {
        return setOneShot(true);
    }

    void shoot();

}
