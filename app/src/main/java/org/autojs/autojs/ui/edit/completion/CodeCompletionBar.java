package org.autojs.autojs.ui.edit.completion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import org.autojs.autojs6.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on Feb 17, 2017.
 * Modified by SuperMonster003 as of Oct 13, 2026.
 */
public class CodeCompletionBar extends RecyclerView {

    private static final int ITEM_HORIZONTAL_PADDING_DP = 12;
    private static final int ICON_ITEM_MIN_WIDTH_DP = 40;
    private static final int ICON_SIZE_DP = 24;
    private static final long LONG_PRESS_REPEAT_INTERVAL_MS = 80;

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
            textView.setForeground(null);
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
        textView.setCompoundDrawables(null, null, null, null);
        textView.setText("");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setContentDescription(mCodeCompletions.getHint(position));
        textView.setMinWidth(dp(ICON_ITEM_MIN_WIDTH_DP));
        textView.setPadding(0, 0, 0, 0);
        if (icon == null) {
            textView.setForeground(null);
            return;
        }

        Drawable wrappedIcon = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(wrappedIcon, mTextColor != 0 ? mTextColor : textView.getCurrentTextColor());
        int iconSize = dp(ICON_SIZE_DP);
        textView.setForeground(new FixedSizeDrawable(wrappedIcon, iconSize));
        textView.setForegroundGravity(Gravity.CENTER);
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

    private static class FixedSizeDrawable extends Drawable {

        private final Drawable mDrawable;
        private final int mSize;

        FixedSizeDrawable(@NonNull Drawable drawable, int size) {
            mDrawable = drawable;
            mSize = size;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int left = bounds.left + (bounds.width() - mSize) / 2;
            int top = bounds.top + (bounds.height() - mSize) / 2;
            mDrawable.setBounds(left, top, left + mSize, top + mSize);
            mDrawable.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            mDrawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mDrawable.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return mSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return mSize;
        }
    }

    /**
     * Places the items from left to right, wraps them into rows and scrolls vertically. Every item is measured to find
     * the rows, but only the items in the visible rows stay attached; the others are measured with one recycled view.
     * Creating a view for every item (the 75 symbols of the Ace symbol bar, two rows of which are visible) took about
     * 220 ms of the editor's first frame on a Helio G85.
     *
     * zh-CN: 从左到右放置项目并换行, 纵向滚动. 为确定各行而测量每一项, 但只保留可见行中的项, 其余项用同一个回收的视图测量.
     * 为每一项创建视图 (Ace 符号栏的 75 个符号, 可见的只有两行) 在 Helio G85 上约占编辑器首帧的 220 ms.
     */
    private static class FlowLayoutManager extends RecyclerView.LayoutManager {

        private final List<Rect> mItemFrames = new ArrayList<>();
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
            mItemFrames.clear();
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
            int visibleTop = mVerticalScrollOffset;
            int visibleBottom = visibleTop + getHeight();

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

                Rect frame = new Rect(x, y, x + childWidth, y + childHeight);
                mItemFrames.add(frame);
                if (isVisible(frame, visibleTop, visibleBottom)) {
                    layoutDecorated(child, frame.left, frame.top - visibleTop, frame.right, frame.bottom - visibleTop);
                } else {
                    removeAndRecycleView(child, recycler);
                }
                x += childWidth;
                rowHeight = Math.max(rowHeight, childHeight);
            }

            mTotalHeight = y + rowHeight + getPaddingBottom();
            int maxScroll = getMaxScroll();
            if (mVerticalScrollOffset > maxScroll) {
                int correction = mVerticalScrollOffset - maxScroll;
                mVerticalScrollOffset = maxScroll;
                offsetChildrenVertical(correction);
                fillVisibleItems(recycler);
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
            fillVisibleItems(recycler);
            return travel;
        }

        // Recycles the attached items that left the visible area and attaches the ones that entered it.
        // zh-CN: 回收离开可见区域的项, 并添加进入可见区域的项.
        private void fillVisibleItems(RecyclerView.Recycler recycler) {
            int visibleTop = mVerticalScrollOffset;
            int visibleBottom = visibleTop + getHeight();
            boolean[] attached = new boolean[mItemFrames.size()];
            for (int index = getChildCount() - 1; index >= 0; index--) {
                View child = getChildAt(index);
                int position = child == null ? NO_POSITION : getPosition(child);
                if (position < 0 || position >= attached.length || !isVisible(mItemFrames.get(position), visibleTop, visibleBottom)) {
                    if (child != null) {
                        removeAndRecycleView(child, recycler);
                    }
                } else {
                    attached[position] = true;
                }
            }
            for (int position = 0; position < attached.length; position++) {
                Rect frame = mItemFrames.get(position);
                if (attached[position] || !isVisible(frame, visibleTop, visibleBottom)) {
                    continue;
                }
                View child = recycler.getViewForPosition(position);
                addView(child);
                measureChildWithMargins(child, 0, 0);
                layoutDecorated(child, frame.left, frame.top - visibleTop, frame.right, frame.bottom - visibleTop);
            }
        }

        private static boolean isVisible(Rect frame, int visibleTop, int visibleBottom) {
            return frame.bottom > visibleTop && frame.top < visibleBottom;
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
            itemView.setOnTouchListener(new CompletionItemTouchListener(itemView.getContext()));
        }
    }

    private class CompletionItemTouchListener implements View.OnTouchListener {

        private final long mLongPressDelayMs;
        private final int mTouchSlop;

        private float mDownX;
        private float mDownY;
        private boolean mTracking;
        private boolean mLongPressed;
        private boolean mRepeating;
        private Runnable mPendingLongPress;
        private Runnable mPendingRepeat;

        CompletionItemTouchListener(Context context) {
            mLongPressDelayMs = (long) (ViewConfiguration.getLongPressTimeout() * 1.25);
            mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mTracking = true;
                    mLongPressed = false;
                    mRepeating = false;
                    v.setPressed(true);
                    cancelPending(v);
                    mPendingLongPress = () -> fireLongPress(v);
                    v.postDelayed(mPendingLongPress, mLongPressDelayMs);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mTracking) {
                        float dx = Math.abs(event.getX() - mDownX);
                        float dy = Math.abs(event.getY() - mDownY);
                        if (dx > mTouchSlop || dy > mTouchSlop) {
                            cancelGesture(v);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    cancelPending(v);
                    v.setPressed(false);
                    boolean shouldClick = mTracking && !mLongPressed;
                    mTracking = false;
                    mRepeating = false;
                    if (shouldClick) {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelGesture(v);
                    return true;
                default:
                    return false;
            }
        }

        private void fireLongPress(View view) {
            if (!mTracking || !view.isPressed()) {
                return;
            }
            int position = getBindingAdapterPosition(view);
            if (!isValidPosition(position) || mOnHintClickListener == null) {
                cancelGesture(view);
                return;
            }
            if (!mOnHintClickListener.shouldRepeatHintLongClick(mCodeCompletions, position)) {
                mLongPressed = view.performLongClick();
                return;
            }
            mLongPressed = true;
            mRepeating = true;
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (mOnHintClickListener.onHintLongClickRepeat(mCodeCompletions, position)) {
                scheduleRepeat(view);
            } else {
                mRepeating = false;
            }
        }

        private void scheduleRepeat(View view) {
            cancelRepeat(view);
            mPendingRepeat = () -> {
                if (!mTracking || !mRepeating || !view.isPressed() || mOnHintClickListener == null) {
                    return;
                }
                int position = getBindingAdapterPosition(view);
                if (!isValidPosition(position) ||
                        !mOnHintClickListener.shouldRepeatHintLongClick(mCodeCompletions, position) ||
                        !mOnHintClickListener.onHintLongClickRepeat(mCodeCompletions, position)) {
                    mRepeating = false;
                    return;
                }
                scheduleRepeat(view);
            };
            view.postDelayed(mPendingRepeat, LONG_PRESS_REPEAT_INTERVAL_MS);
        }

        private int getBindingAdapterPosition(View view) {
            RecyclerView.ViewHolder holder = getChildViewHolder(view);
            return holder == null ? RecyclerView.NO_POSITION : holder.getBindingAdapterPosition();
        }

        private boolean isValidPosition(int position) {
            return position >= 0 && mCodeCompletions != null && position < mCodeCompletions.size();
        }

        private void cancelPending(View view) {
            if (mPendingLongPress != null) {
                view.removeCallbacks(mPendingLongPress);
                mPendingLongPress = null;
            }
            cancelRepeat(view);
        }

        private void cancelRepeat(View view) {
            if (mPendingRepeat != null) {
                view.removeCallbacks(mPendingRepeat);
                mPendingRepeat = null;
            }
        }

        private void cancelGesture(View view) {
            cancelPending(view);
            view.setPressed(false);
            mTracking = false;
            mLongPressed = false;
            mRepeating = false;
        }
    }

    public interface OnHintClickListener {
        void onHintClick(CodeCompletions completions, int pos);

        void onHintLongClick(CodeCompletions completions, int pos);

        default boolean shouldRepeatHintLongClick(CodeCompletions completions, int pos) {
            return false;
        }

        default boolean onHintLongClickRepeat(CodeCompletions completions, int pos) {
            onHintLongClick(completions, pos);
            return true;
        }
    }
}
