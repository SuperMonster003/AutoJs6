package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.autojs.autojs6.R;

import java.util.List;

/**
 * Created by Stardust on May 23, 2017.
 */
public class BubblePopupMenu extends PopupWindow {

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }

    private final RecyclerView mRecyclerView;
    private OnItemClickListener mOnItemClickListener;
    private final View mLittleTriangle;

    public BubblePopupMenu(Context context, List<String> options) {
        super(context);
        View view = View.inflate(context, R.layout.bubble_popup_menu, null);
        mLittleTriangle = view.findViewById(R.id.little_triangle);
        mRecyclerView = view.findViewById(R.id.list);
        mRecyclerView.setAdapter(new SimpleRecyclerViewAdapter<>(R.layout.bubble_popup_menu_item, options, MenuItemViewHolder::new));
        setContentView(view);
        setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setOutsideTouchable(true);
        setFocusable(true);
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    public void showAsDropDownAtLocation(View parent, int contentHeight, int x, int y) {
        int screenWidth = getContentView().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContentView().getResources().getDisplayMetrics().heightPixels;
        int width = getContentView().getMeasuredWidth();
        int height = getContentView().getMeasuredHeight();
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mLittleTriangle.getLayoutParams();
        params.leftMargin = x + width > screenWidth ? x + width - screenWidth : Math.min(x, 0);
        if (y > screenHeight / 2) {
            getContentView().setRotation(180);
            mRecyclerView.setRotation(180);
            params.leftMargin = -params.leftMargin;
            y -= contentHeight + height;
        } else {
            getContentView().setRotation(0);
            mRecyclerView.setRotation(0);
        }
        mLittleTriangle.setLayoutParams(params);
        super.showAtLocation(parent, Gravity.NO_GRAVITY, x, y);
    }

    public void preMeasure() {
        getContentView().measure(getWidth(), getHeight());
    }

    private class MenuItemViewHolder extends BindableViewHolder<String> {

        private final TextView mOption;

        public MenuItemViewHolder(View itemView) {
            super(itemView);
            mOption = itemView.findViewById(R.id.option);
            itemView.setOnClickListener(v -> {
                if (mOnItemClickListener != null) {
                    int i = mRecyclerView.getChildAdapterPosition(v);
                    mOnItemClickListener.onClick(v, i);
                }
            });
        }

        @Override
        public void bind(String s, int position) {
            mOption.setText(s);
        }
    }

}
