package org.autojs.autojs.storage.database;

import androidx.annotation.NonNull;

/**
 * Created by Stardust on Nov 28, 2017.
 */
public class ModelChange<M> {

    public static final int INSERT = 1;
    public static final int UPDATE = 2;
    public static final int DELETE = 3;

    private final M mData;
    private final int mAction;

    public ModelChange(M data, int action) {
        mData = data;
        mAction = action;
    }

    public M getData() {
        return mData;
    }

    public int getAction() {
        return mAction;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModelChange{" +
                "mData=" + mData +
                ", mAction=" + mAction +
                '}';
    }
}
