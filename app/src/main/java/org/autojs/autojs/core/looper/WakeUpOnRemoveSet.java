package org.autojs.autojs.core.looper;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class WakeUpOnRemoveSet<E> extends AbstractSet<E> {

    private final Set<E> mDelegate;
    private final BooleanSupplier mWakeUp;

    WakeUpOnRemoveSet(Set<E> delegate, BooleanSupplier wakeUp) {
        mDelegate = delegate;
        mWakeUp = wakeUp;
    }

    @Override
    public Iterator<E> iterator() {
        return mDelegate.iterator();
    }

    @Override
    public int size() {
        return mDelegate.size();
    }

    @Override
    public boolean add(E element) {
        return mDelegate.add(element);
    }

    @Override
    public boolean contains(Object element) {
        return mDelegate.contains(element);
    }

    @Override
    public boolean remove(Object element) {
        boolean removed = mDelegate.remove(element);
        try {
            mWakeUp.getAsBoolean();
        } catch (RuntimeException ignored) {
            // The owner looper may already be unavailable during runtime shutdown.
        }
        return removed;
    }

}
