package org.autojs.autojs.ui.main.drawer;

import android.view.View;
import android.widget.TextView;

import org.autojs.autojs6.R;
import org.autojs.autojs.ui.widget.BindableViewHolder;

/**
 * Created by Stardust on Dec 10, 2017.
 */
public class DrawerMenuGroupViewHolder extends BindableViewHolder<DrawerMenuItem> {

    private final TextView mTextView;

    public DrawerMenuGroupViewHolder(View itemView) {
        super(itemView);
        mTextView = itemView.findViewById(R.id.title);
    }

    @Override
    public void bind(DrawerMenuItem data, int position) {
        mTextView.setText(data.getTitle());
        int padding = itemView.getResources().getDimensionPixelOffset(R.dimen.divider_drawer_menu_group) / 3;
        itemView.setPadding(0, position == 0 ? 0 : padding, 0, 0);
    }
}
