package org.autojs.autojs.ui.edit.completion;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;
import org.autojs.autojs.groundwork.WrapContentLinearLayoutManager;
import org.autojs.autojs.model.autocomplete.CodeCompletions;
import org.autojs.autojs.util.DisplayUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Feb 17, 2017.
 * Modified by SuperMonster003 as of Mar 8, 2026.
 */
public class CodeCompletionBar extends RecyclerView {

    private static final int ITEM_HORIZONTAL_PADDING_DP = 12;
    private static final int ICON_ITEM_HORIZONTAL_PADDING_DP = 10;
    private static final int ICON_ITEM_MIN_WIDTH_DP = 40;
    private static final int ICON_SIZE_DP = 20;

    private int mTextColor;
    private int mFixedItemWidthPx;
    private int mFixedItemHeightPx;
    private CodeCompletions mCodeCompletions;
    private OnHintClickListener mOnHintClickListener;
    private final OnClickListener mOnCodeCompletionItemClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = getChildViewHolder(v).getBindingAdapterPosition();
            if (position >= 0 && position < mCodeCompletions.size()) {
                if (mOnHintClickListener != null) {
                    mOnHintClickListener.onHintClick(mCodeCompletions, position);
                }
            }
        }
    };

    private final OnLongClickListener mOnCodeCompletionItemLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            int position = getChildViewHolder(v).getBindingAdapterPosition();
            if (position < 0 || position >= mCodeCompletions.size())
                return false;
            if (mOnHintClickListener != null) {
                mOnHintClickListener.onHintLongClick(mCodeCompletions, position);
            }
            return true;
        }
    };

    public CodeCompletionBar(Context context) {
        super(context);
        init();
    }

    public CodeCompletionBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CodeCompletionBar(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setOnHintClickListener(OnHintClickListener onHintClickListener) {
        mOnHintClickListener = onHintClickListener;
    }

    public void setCodeCompletions(CodeCompletions codeCompletions) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            post(() -> setCodeCompletions(codeCompletions));
        } else {
            mCodeCompletions = codeCompletions;
            getAdapter().notifyDataSetChanged();
        }
    }

    public CodeCompletions getCodeCompletions() {
        return mCodeCompletions;
    }

    public void setTextColor(int textColor) {
        mTextColor = textColor;
        getAdapter().notifyDataSetChanged();
    }

    public void setFixedItemSizeDp(int widthDp, int heightDp) {
        mFixedItemWidthPx = widthDp > 0 ? dp(widthDp) : 0;
        mFixedItemHeightPx = heightDp > 0 ? dp(heightDp) : 0;
        getAdapter().notifyDataSetChanged();
    }

    public void setItemHeightDp(int heightDp) {
        mFixedItemHeightPx = heightDp > 0 ? dp(heightDp) : 0;
        getAdapter().notifyDataSetChanged();
    }

    public void resetItemSize() {
        mFixedItemWidthPx = 0;
        mFixedItemHeightPx = 0;
        getAdapter().notifyDataSetChanged();
    }

    public void useHorizontalLayout() {
        setLayoutManager(new WrapContentLinearLayoutManager(getContext(), HORIZONTAL, false));
    }

    public void useFlowLayout() {
        setLayoutManager(new FlowLayoutManager());
    }

    private void init() {
        setAdapter(new CodeCompletionAdapter());
        useHorizontalLayout();
    }

    private class CodeCompletionAdapter extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.input_method_enhance_bar_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            TextView textView = ((TextView) holder.itemView);
            if (mTextColor != 0) {
                textView.setTextColor(mTextColor);
            }
            bindCompletionItem(textView, position);
        }

        @Override
        public int getItemCount() {
            return mCodeCompletions == null ? 0 : mCodeCompletions.size();
        }
    }

    private void bindCompletionItem(TextView textView, int position) {
        applyItemLayoutParams(textView);
        int iconResId = mCodeCompletions.getIconResId(position);
        if (iconResId == 0) {
            textView.setText(mCodeCompletions.getHint(position));
            textView.setContentDescription(null);
            textView.setMinWidth(0);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFixedItemWidthPx > 0 ? 15 : 16);
            int horizontalPadding = mFixedItemWidthPx > 0 ? 0 : dp(ITEM_HORIZONTAL_PADDING_DP);
            textView.setPadding(
                    horizontalPadding,
                    0,
                    horizontalPadding,
                    0
            );
            textView.setCompoundDrawables(null, null, null, null);
            return;
        }

        Drawable icon = ContextCompat.getDrawable(getContext(), iconResId);
        textView.setText("");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setContentDescription(mCodeCompletions.getHint(position));
        textView.setMinWidth(dp(ICON_ITEM_MIN_WIDTH_DP));
        textView.setPadding(
                dp(ICON_ITEM_HORIZONTAL_PADDING_DP),
                0,
                dp(ICON_ITEM_HORIZONTAL_PADDING_DP),
                0
        );
        if (icon == null) {
            textView.setCompoundDrawables(null, null, null, null);
            return;
        }

        Drawable wrappedIcon = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(wrappedIcon, mTextColor != 0 ? mTextColor : textView.getCurrentTextColor());
        int iconSize = dp(ICON_SIZE_DP);
        wrappedIcon.setBounds(0, 0, iconSize, iconSize);
        textView.setCompoundDrawables(wrappedIcon, null, null, null);
        textView.setCompoundDrawablePadding(0);
    }

    private void applyItemLayoutParams(TextView textView) {
        ViewGroup.LayoutParams params = textView.getLayoutParams();
        if (params == null) {
            params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
        params.width = mFixedItemWidthPx > 0 ? mFixedItemWidthPx : ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = mFixedItemHeightPx > 0 ? mFixedItemHeightPx : ViewGroup.LayoutParams.MATCH_PARENT;
        textView.setLayoutParams(params);
    }

    private static int dp(int value) {
        return Math.round(DisplayUtils.dpToPx(value));
    }

    private static class FlowLayoutManager extends RecyclerView.LayoutManager {

        private int mVerticalScrollOffset;
        private int mTotalHeight;

        @Override
        public RecyclerView.LayoutParams generateDefaultLayoutParams() {
            return new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        @Override
        public boolean canScrollVertically() {
            return true;
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            detachAndScrapAttachedViews(recycler);
            if (state.getItemCount() == 0 || getWidth() <= 0 || getHeight() <= 0) {
                mVerticalScrollOffset = 0;
                mTotalHeight = 0;
                return;
            }

            int left = getPaddingLeft();
            int right = getWidth() - getPaddingRight();
            int x = left;
            int y = getPaddingTop();
            int rowHeight = 0;

            for (int i = 0; i < state.getItemCount(); i++) {
                View child = recycler.getViewForPosition(i);
                addView(child);
                measureChildWithMargins(child, 0, 0);

                int childWidth = getDecoratedMeasuredWidth(child);
                int childHeight = getDecoratedMeasuredHeight(child);
                if (x > left && x + childWidth > right) {
                    x = left;
                    y += rowHeight;
                    rowHeight = 0;
                }

                layoutDecorated(
                        child,
                        x,
                        y - mVerticalScrollOffset,
                        x + childWidth,
                        y + childHeight - mVerticalScrollOffset
                );
                x += childWidth;
                rowHeight = Math.max(rowHeight, childHeight);
            }

            mTotalHeight = y + rowHeight + getPaddingBottom();
            int maxScroll = getMaxScroll();
            if (mVerticalScrollOffset > maxScroll) {
                int correction = mVerticalScrollOffset - maxScroll;
                mVerticalScrollOffset = maxScroll;
                offsetChildrenVertical(correction);
            }
        }

        @Override
        public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
            int maxScroll = getMaxScroll();
            int travel = Math.max(-mVerticalScrollOffset, Math.min(dy, maxScroll - mVerticalScrollOffset));
            if (travel == 0) {
                return 0;
            }
            mVerticalScrollOffset += travel;
            offsetChildrenVertical(-travel);
            return travel;
        }

        private int getMaxScroll() {
            return Math.max(0, mTotalHeight - getHeight());
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(mOnCodeCompletionItemClickListener);
            itemView.setOnLongClickListener(mOnCodeCompletionItemLongClickListener);
            itemView.setOnTouchListener(
                    new ViewUtils.DelayedLongPressTouchListener(
                            itemView.getContext(),
                            (long) (ViewConfiguration.getLongPressTimeout() * 1.25)
                    )
            );
        }
    }

    public interface OnHintClickListener {
        void onHintClick(CodeCompletions completions, int pos);

        void onHintLongClick(CodeCompletions completions, int pos);
    }
}
