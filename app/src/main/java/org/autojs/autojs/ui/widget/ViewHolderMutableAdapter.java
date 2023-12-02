package org.autojs.autojs.ui.widget;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;

/**
 * Created by Stardust on Apr 8, 2017.
 */
public abstract class ViewHolderMutableAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private ViewHolderSupplier<VH> mViewHolderSupplier;

    public ViewHolderMutableAdapter(ViewHolderSupplier<VH> viewHolderSupplier) {
        mViewHolderSupplier = viewHolderSupplier;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return mViewHolderSupplier.createViewHolder(parent, viewType);
    }

    public void setViewHolderSupplier(ViewHolderSupplier<VH> viewHolderSupplier) {
        mViewHolderSupplier = viewHolderSupplier;
        notifyDataSetChanged();
    }

}
