package org.autojs.autojs.ui.widget;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Stardust on Apr 6, 2017.
 */
public class ItemTouchHelperSimpleCallback extends ItemTouchHelper.SimpleCallback {

    private final boolean mLongPressDragEnabled;
    private final boolean mItemViewSwipeEnabled;

    public ItemTouchHelperSimpleCallback(int dragDirs, int swipeDirs) {
        super(dragDirs, swipeDirs);
        mItemViewSwipeEnabled = swipeDirs != 0;
        mLongPressDragEnabled = dragDirs != 0;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        /* Empty body. */
}

    @Override
    public boolean isLongPressDragEnabled() {
        return mLongPressDragEnabled;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return mItemViewSwipeEnabled;
    }

}
