package com.stardust.autojs.core.ui.widget;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.stardust.autojs.R;

/**
 * Created by SuperMonster003 on May 30, 2022.
 */

public class CustomSnackbar extends BaseTransientBottomBar<CustomSnackbar> {

    private final View mContent;

    protected CustomSnackbar(@NonNull Context context, @NonNull ViewGroup parent, @NonNull View content, @NonNull com.google.android.material.snackbar.ContentViewCallback contentViewCallback) {
        super(context, parent, content, contentViewCallback);
        mContent = content;
    }

    @NonNull
    public static CustomSnackbar make(@NonNull View view, @NonNull CharSequence text, @Duration int duration) {
        return makeInternal(/* context= */ null, view, text, duration);
    }

    @SuppressWarnings("SameParameterValue")
    private static CustomSnackbar makeInternal(@Nullable Context context, @NonNull View view, @NonNull CharSequence text, @Duration int duration) {
        final ViewGroup parent = findSuitableParent(view);
        if (parent == null) {
            throw new IllegalArgumentException("No suitable parent found from the given view. Please provide a valid view.");
        }

        if (context == null) {
            context = parent.getContext();
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        final CustomSnackbarContentLayout content = (CustomSnackbarContentLayout) inflater.inflate(R.layout.snackbar_custom, parent, false);
        final CustomSnackbar snackbar = new CustomSnackbar(context, parent, content, content);

        snackbar.setText(text);
        snackbar.setDuration(duration);

        return snackbar;
    }

    // @Copied by SuperMonster003 on May 30, 2022.
    //  ! From: com.stardust.autojs.core.ui.widget.CustomSnackbar.findSuitableParent
    @Nullable
    private static ViewGroup findSuitableParent(View view) {
        ViewGroup fallback = null;
        do {
            if (view instanceof CoordinatorLayout) {
                // We've found a CoordinatorLayout, use it
                return (ViewGroup) view;
            } else if (view instanceof FrameLayout) {
                if (view.getId() == android.R.id.content) {
                    // If we've hit the decor content view, then we didn't find a CoL in the
                    // hierarchy, so use it.
                    return (ViewGroup) view;
                } else {
                    // It's not the content view but we'll use it as our fallback
                    fallback = (ViewGroup) view;
                }
            }

            if (view != null) {
                // Else, we will loop and crawl up the view hierarchy and try to find a parent
                final ViewParent parent = view.getParent();
                view = parent instanceof View ? (View) parent : null;
            }
        } while (view != null);

        // If we reach here then we didn't find a CoL or a suitable content view so we'll fallback
        return fallback;
    }

    @NonNull
    public CustomSnackbar setText(@NonNull CharSequence message) {
        ((CustomSnackbarContentLayout) mContent).getCustomTextView().setText(message);
        return this;
    }

    public CustomSnackbar setActionOne(@StringRes int resId, View.OnClickListener listener) {
        return setActionOne(getContext().getText(resId), listener);
    }

    public CustomSnackbar setActionTwo(@StringRes int resId, View.OnClickListener listener) {
        return setActionTwo(getContext().getText(resId), listener);
    }

    public CustomSnackbar setActionOne(@Nullable CharSequence text, @Nullable final View.OnClickListener listener) {
        return setAction(text, listener, ((CustomSnackbarContentLayout) mContent).getActionOneView());
    }

    public CustomSnackbar setActionTwo(@Nullable CharSequence text, @Nullable final View.OnClickListener listener) {
        return setAction(text, listener, ((CustomSnackbarContentLayout) mContent).getActionTwoView());
    }

    @NonNull
    public CustomSnackbar setAction(@StringRes int resId, View.OnClickListener listener, Button view) {
        return setAction(getContext().getText(resId), listener, view);
    }

    @NonNull
    public CustomSnackbar setAction(@Nullable CharSequence text, @Nullable final View.OnClickListener listener, Button view) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
        view.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(v);
            }
            // Now dismiss the Snackbar
            dispatchDismiss(BaseCallback.DISMISS_EVENT_ACTION);
        });
        return this;
    }

}
