package org.autojs.autojs.ui.widget;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

/**
 * Created by Stardust on Mar 27, 2017.
 */
public interface OnItemClickListener {

    void onItemClick(RecyclerView parent, View item, int position);

}
