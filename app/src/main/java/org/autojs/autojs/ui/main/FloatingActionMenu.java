package org.autojs.autojs.ui.main;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import io.reactivex.subjects.PublishSubject;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Sep 24, 2017.
 */
public class FloatingActionMenu extends FrameLayout implements View.OnClickListener {

    public interface OnFloatingActionButtonClickListener {
        void onClick(FloatingActionButton button, int pos);
    }

    private static final int[] ICONS = {
            R.drawable.ic_floating_action_menu_dir,
            R.drawable.ic_floating_action_menu_file,
            R.drawable.ic_file_download_white_cropped,
            R.drawable.ic_project_white};

    private static final int[] LABELS = {
            R.string.text_directory,
            R.string.text_file,
            R.string.text_import,
            R.string.text_project};

    private View mOverlay = null;

    private FloatingActionButton[] mFabs;
    private View[] mFabContainers;
    private boolean mExpanded = false;
    private boolean mShouldCheckExpanded = true;
    private final int mInterval = 30;
    private final int mDuration = 250;
    private final Interpolator mInterpolator = new FastOutSlowInInterpolator();
    private final PublishSubject<Boolean> mState = PublishSubject.create();
    private OnFloatingActionButtonClickListener mOnFloatingActionButtonClickListener;

    public FloatingActionMenu(@NonNull Context context) {
        super(context);
        init();
    }

    public FloatingActionMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatingActionMenu(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        buildFabs(ICONS, LABELS);
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public boolean shouldCheckExpanded() {
        return mShouldCheckExpanded;
    }

    public void expand() {
        showOverlay();
        setVisibility(VISIBLE);
        int h = mFabs[0].getHeight();
        for (int i = 0; i < mFabContainers.length; i++) {
            animateY(mFabContainers[i], -(h + mInterval) * (i + 1), null);
            rotate(mFabs[i]);
        }
        mExpanded = true;
        mState.onNext(true);
    }

    private void rotate(FloatingActionButton fab) {
        fab.setRotation(0);
        fab.animate()
                .rotation(360)
                .setDuration(mDuration)
                .setInterpolator(mInterpolator)
                .start();
    }

    private void animateY(View view, float y, Animator.AnimatorListener l) {
        view.animate()
                .translationY(y)
                .setDuration(mDuration)
                .setInterpolator(mInterpolator)
                .setListener(l)
                .start();
    }

    public void collapse() {
        hideOverlay();
        animateY(mFabContainers[0], 0, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(INVISIBLE);
                mShouldCheckExpanded = true;
            }
        });
        for (int i = 1; i < mFabContainers.length; i++) {
            animateY(mFabContainers[i], 0, null);
            rotate(mFabs[i]);
        }
        mExpanded = false;
        mState.onNext(false);
    }

    @SuppressWarnings("SameParameterValue")
    private void buildFabs(int[] icons, int[] labels) {
        if (icons.length != labels.length) {
            throw new IllegalArgumentException("icons.length = " + icons.length + " is not equal to labels.length = " + labels.length);
        }
        mFabs = new FloatingActionButton[icons.length];
        TextView[] mLabels = new TextView[icons.length];
        mFabContainers = new View[icons.length];
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < icons.length; i++) {
            mFabContainers[i] = inflater.inflate(R.layout.item_floating_action_menu, this, false);
            mFabs[i] = mFabContainers[i].findViewById(R.id.floating_action_button);
            mFabs[i].setImageResource(icons[i]);
            mFabs[i].setOnClickListener(this);
            mFabs[i].setTag(i);
            mLabels[i] = mFabContainers[i].findViewById(R.id.label);
            mLabels[i].setText(labels[i]);
            addView(mFabContainers[i]);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        // Call super.onMeasure to measure children and width
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) {
            return;
        }
        int h = mFabContainers[0].getMeasuredHeight();
        setMeasuredDimension(getMeasuredWidth(), (h + mInterval) * mFabs.length + h);
    }

    public PublishSubject<Boolean> getState() {
        return mState;
    }

    @Override
    public void onClick(View v) {
        // collapse();
        if (mOnFloatingActionButtonClickListener != null) {
            mOnFloatingActionButtonClickListener.onClick((FloatingActionButton) v, (int) v.getTag());
        }
    }

    public void setOnFloatingActionButtonClickListener(OnFloatingActionButtonClickListener onFloatingActionButtonClickListener) {
        mOnFloatingActionButtonClickListener = onFloatingActionButtonClickListener;
    }

    private void showOverlay() {
        if (mOverlay != null && mOverlay.getVisibility() == VISIBLE) return;

        Context context = getContext();

        if (!(context instanceof Activity activity)) return;

        if (!(activity.findViewById(android.R.id.content) instanceof ViewGroup root)) return;

        mOverlay = new View(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                mShouldCheckExpanded = false;
                collapse();
                root.post(() -> root.removeView(this));
                var replay = MotionEvent.obtain(event);
                root.post(() -> activity.getWindow().getDecorView().dispatchTouchEvent(replay));
                return false;
            }
        };

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mOverlay.setLayoutParams(params);
        mOverlay.setBackgroundColor(Color.TRANSPARENT);

        root.addView(mOverlay);
    }

    private void hideOverlay() {
        if (mOverlay == null) return;
        if (!(mOverlay.getParent() instanceof ViewGroup parent)) return;
        parent.removeView(mOverlay);
        mOverlay = null;
    }

}
