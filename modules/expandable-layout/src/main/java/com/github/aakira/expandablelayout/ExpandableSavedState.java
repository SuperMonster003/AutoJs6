package com.github.aakira.expandablelayout;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

public class ExpandableSavedState extends View.BaseSavedState {
    private int size;
    private float weight;

    ExpandableSavedState(Parcelable superState) {
        super(superState);
    }

    private ExpandableSavedState(Parcel in) {
        super(in);
        this.size = in.readInt();
        this.weight = in.readFloat();
    }

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public float getWeight() {
        return this.weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeInt(this.size);
        out.writeFloat(this.weight);
    }

    public static final Creator<ExpandableSavedState> CREATOR =
            new Creator<ExpandableSavedState>() {
                public ExpandableSavedState createFromParcel(Parcel in) {
                    return new ExpandableSavedState(in);
                }

                public ExpandableSavedState[] newArray(int size) {
                    return new ExpandableSavedState[size];
                }
            };
}
