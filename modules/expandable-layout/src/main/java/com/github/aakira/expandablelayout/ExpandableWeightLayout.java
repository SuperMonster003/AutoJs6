package com.github.aakira.expandablelayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ExpandableWeightLayout extends RelativeLayout implements ExpandableLayout {

    private int duration;
    private TimeInterpolator interpolator = new LinearInterpolator();
    private boolean defaultExpanded;

    private ExpandableLayoutListener listener;
    private ExpandableSavedState savedState;
    private boolean isExpanded;
    private float layoutWeight = 0.0f;
    private boolean isArranged = false;
    private boolean isCalculatedSize = false;
    private boolean isAnimating = false;
    private ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener;

    public ExpandableWeightLayout(final Context context) {
        this(context, null);
    }

    public ExpandableWeightLayout(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpandableWeightLayout(final Context context, final AttributeSet attrs,
                                  final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ExpandableWeightLayout(final Context context, final AttributeSet attrs,
                                  final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr);
    }

    private void init(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.expandableLayout, defStyleAttr, 0);
        duration = a.getInteger(R.styleable.expandableLayout_ael_duration, DEFAULT_DURATION);
        defaultExpanded = a.getBoolean(R.styleable.expandableLayout_ael_expanded, DEFAULT_EXPANDED);
        final int interpolatorType = a.getInteger(R.styleable.expandableLayout_ael_interpolator,
                Utils.LINEAR_INTERPOLATOR);
        a.recycle();
        interpolator = Utils.createInterpolator(interpolatorType);
        isExpanded = defaultExpanded;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Check this layout using the attribute of weight
        if (!(getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            throw new AssertionError("You must arrange in LinearLayout.");
        }
        if (0 >= getCurrentWeight()) throw new AssertionError("You must set a weight than 0.");

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!isCalculatedSize) {
            layoutWeight = getCurrentWeight();
            isCalculatedSize = true;
        }

        if (isArranged) return;
        setWeight(defaultExpanded ? layoutWeight : 0);
        isArranged = true;

        if (savedState == null) return;
        setWeight(savedState.getWeight());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable parcelable = super.onSaveInstanceState();

        final ExpandableSavedState ss = new ExpandableSavedState(parcelable);
        ss.setWeight(getCurrentWeight());
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (!(state instanceof ExpandableSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        final ExpandableSavedState ss = (ExpandableSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        savedState = ss;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setListener(@NonNull ExpandableLayoutListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toggle() {
        toggle(duration, interpolator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toggle(final long duration, @Nullable final TimeInterpolator interpolator) {
        if (0 < getCurrentWeight()) {
            collapse(duration, interpolator);
        } else {
            expand(duration, interpolator);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expand() {
        if (isAnimating) return;

        createExpandAnimator(0, layoutWeight, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expand(final long duration, @Nullable final TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration <= 0) {
            isExpanded = true;
            setWeight(layoutWeight);
            requestLayout();
            notifyListeners();
            return;
        }
        createExpandAnimator(getCurrentWeight(), layoutWeight, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collapse() {
        if (isAnimating) return;

        createExpandAnimator(getCurrentWeight(), 0, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collapse(final long duration, @Nullable final TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration <= 0) {
            isExpanded = false;
            setWeight(0);
            requestLayout();
            notifyListeners();
            return;
        }
        createExpandAnimator(getCurrentWeight(), 0, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDuration(@NonNull final int duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("Animators cannot have negative duration: " +
                    duration);
        }
        this.duration = duration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExpanded(boolean expanded) {
        final float currentWeight = getCurrentWeight();
        if ((expanded && (currentWeight == layoutWeight))
                || (!expanded && currentWeight == 0)) return;

        isExpanded = expanded;
        setWeight(expanded ? layoutWeight : 0);
        requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInterpolator(@NonNull final TimeInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Sets weight of expandable layout.
     *
     * @param expandWeight expand to this weight by {@link #expand()}
     */
    public void setExpandWeight(final float expandWeight) {
        layoutWeight = expandWeight;
    }

    /**
     * Gets current weight of expandable layout.
     *
     * @return weight
     */
    public float getCurrentWeight() {
        return ((LinearLayout.LayoutParams) getLayoutParams()).weight;
    }

    /**
     * @param weight
     *
     * @see #move(float, long, TimeInterpolator)
     */
    public void move(float weight) {
        move(weight, duration, interpolator);
    }

    /**
     * Change to weight.
     * Sets 0 to duration if you want to move immediately.
     *
     * @param weight
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    public void move(float weight, long duration, @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration <= 0L) {
            isExpanded = weight > 0;
            setWeight(weight);
            requestLayout();
            notifyListeners();
            return;
        }
        createExpandAnimator(getCurrentWeight(), weight, duration, interpolator).start();
    }

    /**
     * Creates value animator.
     * Expand the layout if @param.to is bigger than @param.from.
     * Collapse the layout if @param.from is bigger than @param.to.
     *
     * @param from
     * @param to
     * @param duration
     * @param interpolator TimeInterpolator
     *
     * @return
     */
    private ValueAnimator createExpandAnimator(final float from, final float to, final long duration,
                                               @Nullable final TimeInterpolator interpolator) {
        final ValueAnimator valueAnimator = ValueAnimator.ofFloat(from, to);
        valueAnimator.setDuration(duration);
        valueAnimator.setInterpolator(interpolator == null ? this.interpolator : interpolator);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                setWeight((float) animation.getAnimatedValue());
                requestLayout();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                isAnimating = true;

                if (listener == null) return;

                listener.onAnimationStart();
                if (layoutWeight == to) {
                    listener.onPreOpen();
                    return;
                }
                if (0 == to) {
                    listener.onPreClose();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
                isExpanded = to > 0;

                if (listener == null) return;

                listener.onAnimationEnd();
                if (to == layoutWeight) {
                    listener.onOpened();
                    return;
                }
                if (to == 0) {
                    listener.onClosed();
                }
            }
        });
        return valueAnimator;
    }

    private void setWeight(final float weight) {
        ((LinearLayout.LayoutParams) getLayoutParams()).weight = weight;
    }

    /**
     * Notify listeners
     */
    private void notifyListeners() {
        if (listener == null) return;

        listener.onAnimationStart();
        if (isExpanded) {
            listener.onPreOpen();
        } else {
            listener.onPreClose();
        }
        mGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    getViewTreeObserver().removeGlobalOnLayoutListener(mGlobalLayoutListener);
                } else {
                    getViewTreeObserver().removeOnGlobalLayoutListener(mGlobalLayoutListener);
                }

                listener.onAnimationEnd();
                if (isExpanded) {
                    listener.onOpened();
                } else {
                    listener.onClosed();
                }
            }
        };
        getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
    }
}