package org.autojs.autojs.tool;

import android.util.SparseArray;

import androidx.annotation.NonNull;

/**
 * Created by Stardust on Jan 26, 2017.
 */
public class SparseArrayEntries<E> {

    private final SparseArray<E> mSparseArray = new SparseArray<>();

    public SparseArrayEntries<E> entry(int key, E value) {
        mSparseArray.put(key, value);
        return this;
    }

    @NonNull
    public SparseArray<E> sparseArray() {
        return mSparseArray;
    }

}