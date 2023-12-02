package org.autojs.autojs.ui.widget;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

/**
 * Created by Stardust on Apr 8, 2017.
 */
public abstract class BindableViewHolder<DataType> extends RecyclerView.ViewHolder{


    public BindableViewHolder(View itemView) {
        super(itemView);
    }

    public abstract void bind(DataType data, int position);
}
