package org.autojs.autojs.ui.widget

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by Stardust on Apr 8, 2017.
 * Transformed by SuperMonster003 on Nov 23, 2024.
 */
abstract class BindableViewHolder<DataType>(itemView: View) : RecyclerView.ViewHolder(itemView) {

    abstract fun bind(data: DataType, position: Int)

    open fun onViewRecycled() {
        /* Nothing to do by default. */
    }

}
