package org.autojs.autojs.ui.edit.completion;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
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

    private void init() {
        setAdapter(new CodeCompletionAdapter());
        setLayoutManager(new WrapContentLinearLayoutManager(getContext(), HORIZONTAL, false));
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
        int iconResId = mCodeCompletions.getIconResId(position);
        if (iconResId == 0) {
            textView.setText(mCodeCompletions.getHint(position));
            textView.setContentDescription(null);
            textView.setMinWidth(0);
            textView.setPadding(
                    dp(ITEM_HORIZONTAL_PADDING_DP),
                    0,
                    dp(ITEM_HORIZONTAL_PADDING_DP),
                    0
            );
            textView.setCompoundDrawables(null, null, null, null);
            return;
        }

        Drawable icon = ContextCompat.getDrawable(getContext(), iconResId);
        textView.setText("");
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

    private static int dp(int value) {
        return Math.round(DisplayUtils.dpToPx(value));
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
