package org.autojs.autojs.core.ref;

import org.autojs.autojs.core.cleaner.ICleaner;

import java.lang.ref.*;

/**
 * Created by SuperMonster003 on Dec 15, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 15, 2023.
public final class NativeObjectReference<T> extends PhantomReference<T> {

    public final ICleaner cleaner;
    public volatile long pointer;

    public NativeObjectReference(final T referent, final ReferenceQueue<? super T> referenceQueue, final ICleaner cleaner) {
        super(referent, referenceQueue);
        this.cleaner = cleaner;
    }

}
