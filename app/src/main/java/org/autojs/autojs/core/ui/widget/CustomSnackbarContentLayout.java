package org.autojs.autojs.core.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.ContentViewCallback;

import org.autojs.autojs6.R;

/**
 * Created by SuperMonster003 on May 30, 2022.
 */
public class CustomSnackbarContentLayout extends LinearLayout implements ContentViewCallback {

    public CustomSnackbarContentLayout(@NonNull Context context) {
        super(context);
    }

    public CustomSnackbarContentLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public Button getActionOneView() {
        return findViewById(R.id.snackbar_action_one);
    }

    public Button getActionTwoView() {
        return findViewById(R.id.snackbar_action_two);
    }

    public TextView getCustomTextView() {
        return findViewById(R.id.snackbar_text);
    }

    @Override
    public void animateContentIn(int delay, int duration) {
        TextView mTextView = getCustomTextView();
        Button mActionOneView = getActionOneView();
        Button mActionTwoView = getActionTwoView();

        mTextView.setAlpha(0f);
        mTextView.animate().alpha(1f).setDuration(duration).setStartDelay(delay).start();

        if (mActionOneView.getVisibility() == VISIBLE) {
            mActionOneView.setAlpha(0f);
            mActionOneView.animate().alpha(1f).setDuration(duration).setStartDelay(delay).start();
        }

        if (mActionTwoView.getVisibility() == VISIBLE) {
            mActionTwoView.setAlpha(0f);
            mActionTwoView.animate().alpha(1f).setDuration(duration).setStartDelay(delay).start();
        }
    }

    @Override
    public void animateContentOut(int delay, int duration) {
        TextView mTextView = getCustomTextView();
        Button mActionOneView = getActionOneView();
        Button mActionTwoView = getActionTwoView();

        mTextView.setAlpha(1f);
        mTextView.animate().alpha(0f).setDuration(duration).setStartDelay(delay).start();

        if (mActionOneView.getVisibility() == VISIBLE) {
            mActionOneView.setAlpha(1f);
            mActionOneView.animate().alpha(0f).setDuration(duration).setStartDelay(delay).start();
        }

        if (mActionTwoView.getVisibility() == VISIBLE) {
            mActionTwoView.setAlpha(1f);
            mActionTwoView.animate().alpha(0f).setDuration(duration).setStartDelay(delay).start();
        }
    }

}