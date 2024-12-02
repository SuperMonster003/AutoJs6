package org.autojs.autojs.ui.widget;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Stardust on Mar 14, 2017.
 */
public class SimpleAdapterDataObserver extends RecyclerView.AdapterDataObserver {

    public void onChanged() {
        onSomethingChanged();
    }

    public void onItemRangeChanged(int positionStart, int itemCount) {
        onSomethingChanged();
    }

    public void onItemRangeInserted(int positionStart, int itemCount) {
        onSomethingChanged();
    }

    public void onItemRangeRemoved(int positionStart, int itemCount) {
        onSomethingChanged();

    }

    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
        onSomethingChanged();
    }

    public void onSomethingChanged() {
        /* Empty body. */
    }
}
