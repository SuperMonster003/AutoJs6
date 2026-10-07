package org.autojs.autojs.ui.main.drawer;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.TooltipCompat;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Dec 10, 2017.
 */
public class DrawerMenuGroupViewHolder extends BindableViewHolder<DrawerMenuItem> {

    private final TextView mTextView;
    private final ImageView mActionView;

    public DrawerMenuGroupViewHolder(View itemView) {
        super(itemView);
        mTextView = itemView.findViewById(R.id.title);
        mActionView = itemView.findViewById(R.id.action);
    }

    @Override
    public void bind(DrawerMenuItem data, int position) {
        mTextView.setText(data.getTitle());
        int padding = itemView.getResources().getDimensionPixelOffset(R.dimen.divider_drawer_menu_group);
        itemView.setPadding(0, position == 0 ? 0 : padding, 0, 0);
        if (data instanceof DrawerMenuGroup group && group.hasAction()) {
            mActionView.setVisibility(View.VISIBLE);
            mActionView.setImageResource(group.getActionIcon());
            if (group.getActionDescription() != 0) {
                String description = itemView.getContext().getString(group.getActionDescription());
                mActionView.setContentDescription(description);
                TooltipCompat.setTooltipText(mActionView, description);
            }
            mActionView.setOnClickListener(v -> group.performGroupAction());
        } else {
            mActionView.setVisibility(View.GONE);
            mActionView.setOnClickListener(null);
        }
    }
}
