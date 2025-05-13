package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs.util.DisplayUtils;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by Stardust on May 23, 2017.
 * Modified by SuperMonster003 as of Mar 20, 2022.
 */
public class BubblePopupMenu extends PopupWindow {

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }

    private final RecyclerView mRecyclerView;
    private OnItemClickListener mOnItemClickListener;
    private final View mLittleTriangle;

    public BubblePopupMenu(Context context, List<String> options) {
        this(context, options, null);
    }

    public BubblePopupMenu(Context context, List<String> options, List<Integer> dividerPositions) {
        super(context);
        View view = View.inflate(context, R.layout.bubble_popup_menu, null);
        mLittleTriangle = view.findViewById(R.id.little_triangle);
        mRecyclerView = view.findViewById(R.id.list);
        mRecyclerView.setAdapter(new SimpleRecyclerViewAdapter<>(R.layout.bubble_popup_menu_item, options, MenuItemViewHolder::new));
        if (dividerPositions != null && !dividerPositions.isEmpty()) {
            mRecyclerView.addItemDecoration(new DividerDecoration(context, dividerPositions, 5f, 0.64f, 20f));
        }
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

    private static class DividerDecoration extends RecyclerView.ItemDecoration {

        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mLineHeight;
        private final int mMarginTop;
        private final int mMarginBottom;
        private final int mFadeWidth;
        private final List<Integer> mPositions;

        DividerDecoration(Context ctx,
                          List<Integer> positions,
                          float marginDp,
                          float thicknessDp,
                          float fadeWidthDp) {
            mPositions = positions;
            mMarginTop = (int) DisplayUtils.dpToPx(marginDp);
            mMarginBottom = (int) DisplayUtils.dpToPx(marginDp);
            mLineHeight = (int) DisplayUtils.dpToPx(thicknessDp);
            mFadeWidth = (int) DisplayUtils.dpToPx(fadeWidthDp);

            mPaint.setColor(ctx.getColor(R.color.divider_lighter_night_day));
            mPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void onDrawOver(@NotNull Canvas canvas,
                               RecyclerView parent,
                               RecyclerView.@NotNull State state) {

            int childCnt = parent.getChildCount();
            int leftPad = parent.getPaddingLeft();
            int rightPad = parent.getPaddingRight();

            int widthAll = parent.getWidth() - leftPad - rightPad;
            if (widthAll <= 0) return;

            // 按当前宽度创建一次渐变 Shader.
            int solidColor = mPaint.getColor();
            LinearGradient shader = new LinearGradient(
                    leftPad, 0,
                    leftPad + widthAll, 0,
                    new int[]{
                            ColorUtils.applyAlpha(solidColor, 0),
                            solidColor,
                            solidColor,
                            ColorUtils.applyAlpha(solidColor, 0)
                    },
                    new float[]{
                            0f,
                            (float) mFadeWidth / widthAll,
                            1f - (float) mFadeWidth / widthAll,
                            1f
                    },
                    Shader.TileMode.CLAMP
            );
            mPaint.setShader(shader);

            for (int i = 0; i < childCnt; i++) {
                View child = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(child);
                if (mPositions.contains(pos)) {
                    float top = child.getBottom() + mMarginTop;
                    float bottom = top + mLineHeight;
                    canvas.drawRect(leftPad,
                            top,
                            parent.getWidth() - rightPad,
                            bottom,
                            mPaint);
                }
            }

            // 清理 shader, 防止影响 RecyclerView 复用.
            mPaint.setShader(null);
        }

        @Override
        public void getItemOffsets(@NotNull Rect outRect,
                                   @NotNull View view,
                                   RecyclerView parent,
                                   RecyclerView.@NotNull State state) {
            if (mPositions.contains(parent.getChildAdapterPosition(view))) {
                outRect.bottom = mMarginTop + mLineHeight + mMarginBottom;
            }
        }

    }

}