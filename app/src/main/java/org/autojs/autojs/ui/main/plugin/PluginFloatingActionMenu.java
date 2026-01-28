package org.autojs.autojs.ui.main.plugin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
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
import org.jetbrains.annotations.NotNull;

/**
 * Created by SuperMonster003 on Jan 17, 2026.
 */
public class PluginFloatingActionMenu extends FrameLayout implements View.OnClickListener {

    private static final int[] ICONS = {
            R.drawable.ic_add_black_48dp,
            R.drawable.ic_add_black_48dp};

    private static final int[] LABELS = {
            R.string.text_install_from_local_file,
            R.string.text_install_from_url};

    private static final int ANIMATION_INTERVAL = 30;
    private static final int ANIMATION_DURATION = 250;

    private final Interpolator mInterpolator = new FastOutSlowInInterpolator();
    private final PublishSubject<Boolean> mState = PublishSubject.create();

    private View mOverlay = null;
    private FloatingActionButton[] mFabs;
    private View[] mFabContainers;
    private boolean mExpanded = false;
    private OnFloatingActionButtonClickListener mOnFloatingActionButtonClickListener;

    private View mToggleFab;
    private int[] mToggleFabLoc;

    public PluginFloatingActionMenu(@NonNull Context context) {
        super(context);
        init();
    }

    public PluginFloatingActionMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PluginFloatingActionMenu(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public PublishSubject<Boolean> getState() {
        return mState;
    }

    public void expand() {
        showOverlay();
        setVisibility(VISIBLE);
        int h = mFabs[0].getHeight();
        for (int i = 0; i < mFabContainers.length; i++) {
            animateY(mFabContainers[i], -(h + ANIMATION_INTERVAL) * (i + 1), null);
            rotate(mFabs[i]);
        }
        mExpanded = true;
        mState.onNext(true);
    }

    public void collapse() {
        hideOverlay();
        animateY(mFabContainers[0], 0, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(INVISIBLE);
            }
        });
        for (int i = 1; i < mFabContainers.length; i++) {
            animateY(mFabContainers[i], 0, null);
            rotate(mFabs[i]);
        }
        mExpanded = false;
        mState.onNext(false);
    }

    public void setOnFloatingActionButtonClickListener(OnFloatingActionButtonClickListener listener) {
        mOnFloatingActionButtonClickListener = listener;
    }

    public void setToggleFab(@Nullable View toggleFab, int @NotNull [] locFab) {
        mToggleFab = toggleFab;
        mToggleFabLoc = locFab;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) {
            return;
        }
        int h = mFabContainers[0].getMeasuredHeight();
        setMeasuredDimension(getMeasuredWidth(), (h + ANIMATION_INTERVAL) * mFabs.length + h);
    }

    @Override
    public void onClick(View v) {
        collapse();
        if (mOnFloatingActionButtonClickListener != null) {
            mOnFloatingActionButtonClickListener.onClick((FloatingActionButton) v, (int) v.getTag());
        }
    }

    private void init() {
        buildFabs(ICONS, LABELS);
    }

    private void rotate(FloatingActionButton fab) {
        fab.setRotation(0);
        fab.animate()
                .rotation(360)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(mInterpolator)
                .start();
    }

    private void animateY(View view, float y, Animator.AnimatorListener l) {
        view.animate()
                .translationY(y)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(mInterpolator)
                .setListener(l)
                .start();
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

    private void showOverlay() {
        if (mOverlay != null) {
            if (mOverlay.getVisibility() != VISIBLE) {
                mOverlay.setVisibility(View.VISIBLE);
            }
            return;
        }
        Context context = getContext();
        if (!(context instanceof Activity activity)) return;
        if (!(activity.findViewById(android.R.id.content) instanceof ViewGroup root)) return;

        PluginFloatingActionMenu thisMenu = this;

        mOverlay = new View(context) {
            private final Rect menuRectOnScreen = new Rect();
            private final Rect toggleFabRectOnScreen = new Rect();

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int[] locMenu = new int[2];
                thisMenu.getLocationOnScreen(locMenu);
                menuRectOnScreen.set(
                        locMenu[0],
                        locMenu[1],
                        locMenu[0] + thisMenu.getWidth(),
                        locMenu[1] + thisMenu.getHeight()
                );

                boolean hasToggleFab = (mToggleFab != null) && mToggleFab.isShown();
                if (hasToggleFab) {
                    toggleFabRectOnScreen.set(
                            mToggleFabLoc[0],
                            mToggleFabLoc[1],
                            mToggleFabLoc[0] + mToggleFab.getWidth(),
                            mToggleFabLoc[1] + mToggleFab.getHeight()
                    );
                } else {
                    toggleFabRectOnScreen.setEmpty();
                }

                int rawX = (int) event.getRawX();
                int rawY = (int) event.getRawY();

                boolean inToggleFab = hasToggleFab && toggleFabRectOnScreen.contains(rawX, rawY);
                if (inToggleFab) {
                    MotionEvent forwarded = MotionEvent.obtain(event);
                    forwarded.offsetLocation(-toggleFabRectOnScreen.left, -toggleFabRectOnScreen.top);
                    mToggleFab.dispatchTouchEvent(forwarded);
                    forwarded.recycle();
                    return true;
                }

                boolean inMenu = menuRectOnScreen.contains(rawX, rawY);
                if (inMenu) {
                    MotionEvent forwarded = MotionEvent.obtain(event);
                    forwarded.offsetLocation(-menuRectOnScreen.left, -menuRectOnScreen.top);
                    thisMenu.dispatchTouchEvent(forwarded);
                    forwarded.recycle();
                    return true;
                }

                // Collapse for external touches, avoid breaking button click chains.
                // zh-CN: 对外部触摸进行收起, 避免破坏按钮点击链路.
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    collapse();
                }
                // Do not consume external events, pass them to lower layers such as lists for continued processing.
                // zh-CN: 不消耗外部事件, 交给下层列表等继续处理.
                return false;
            }
        };

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mOverlay.setLayoutParams(params);
        mOverlay.setBackgroundColor(Color.TRANSPARENT);

        root.addView(mOverlay);
    }

    private void hideOverlay() {
        if (mOverlay != null) mOverlay.setVisibility(View.GONE);
    }

    public interface OnFloatingActionButtonClickListener {
        void onClick(FloatingActionButton button, int pos);
    }

}