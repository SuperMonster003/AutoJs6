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
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ExpandableLinearLayout extends LinearLayout implements ExpandableLayout {

    private int duration;
    private TimeInterpolator interpolator = new LinearInterpolator();
    /**
     * Default state of expanse
     *
     * @see #defaultChildIndex
     * @see #defaultPosition
     */
    private boolean defaultExpanded;
    /**
     * You cannot define {@link #defaultExpanded}, {@link #defaultChildIndex}
     * and {@link #defaultPosition} at the same time.
     * {@link #defaultPosition} has priority over {@link #defaultExpanded}
     * and {@link #defaultChildIndex} if you set them at the same time.
     * <p/>
     * <p/>
     * Priority
     * {@link #defaultPosition} > {@link #defaultChildIndex} > {@link #defaultExpanded}
     */
    private int defaultChildIndex;
    private int defaultPosition;
    /**
     * The close position is width from left of layout if orientation is horizontal.
     * The close position is height from top of layout if orientation is vertical.
     */
    private int closePosition = 0;

    private ExpandableLayoutListener listener;
    private ExpandableSavedState savedState;
    private boolean isExpanded;
    private int layoutSize = 0;
    private boolean inRecyclerView = false;
    private boolean isArranged = false;
    private boolean isCalculatedSize = false;
    private boolean isAnimating = false;
    /**
     * State of expanse in recycler view.
     */
    private boolean recyclerExpanded = false;
    /**
     * view size of children
     **/
    private List<Integer> childSizeList = new ArrayList<>();
    private ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener;

    public ExpandableLinearLayout(final Context context) {
        this(context, null);
    }

    public ExpandableLinearLayout(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpandableLinearLayout(final Context context, final AttributeSet attrs,
                                  final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ExpandableLinearLayout(final Context context, final AttributeSet attrs,
                                  final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr);
    }

    private void init(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.expandableLayout, defStyleAttr, 0);
        duration = a.getInteger(R.styleable.expandableLayout_ael_duration, DEFAULT_DURATION);
        defaultExpanded = a.getBoolean(R.styleable.expandableLayout_ael_expanded, DEFAULT_EXPANDED);
        defaultChildIndex = a.getInteger(R.styleable.expandableLayout_ael_defaultChildIndex,
                Integer.MAX_VALUE);
        defaultPosition = a.getDimensionPixelSize(R.styleable.expandableLayout_ael_defaultPosition,
                Integer.MIN_VALUE);
        final int interpolatorType = a.getInteger(R.styleable.expandableLayout_ael_interpolator,
                Utils.LINEAR_INTERPOLATOR);
        a.recycle();
        interpolator = Utils.createInterpolator(interpolatorType);
        isExpanded = defaultExpanded;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!isCalculatedSize) {
            // calculate a size of children
            childSizeList.clear();
            final int childCount = getChildCount();

            if (childCount > 0) {
                int sumSize = 0;
                View view;
                LayoutParams params;
                for (int i = 0; i < childCount; i++) {
                    view = getChildAt(i);
                    params = (LayoutParams) view.getLayoutParams();

                    if (0 < i) {
                        sumSize = childSizeList.get(i - 1);
                    }
                    childSizeList.add(
                            (isVertical()
                                    ? view.getMeasuredHeight() + params.topMargin + params.bottomMargin
                                    : view.getMeasuredWidth() + params.leftMargin + params.rightMargin
                            ) + sumSize);
                }
                layoutSize = childSizeList.get(childCount - 1) +
                        (isVertical()
                                ? getPaddingTop() + getPaddingBottom()
                                : getPaddingLeft() + getPaddingRight()
                        );
                isCalculatedSize = true;
            } else {
                throw new IllegalStateException("The expandableLinearLayout must have at least one child");
            }
        }

        if (isArranged) return;

        // adjust default position if a user set a value.
        if (!defaultExpanded) {
            setLayoutSize(closePosition);
        }
        if (inRecyclerView) {
            setLayoutSize(recyclerExpanded ? layoutSize : closePosition);
        }
        final int childNumbers = childSizeList.size();
        if (childNumbers > defaultChildIndex && childNumbers > 0) {
            moveChild(defaultChildIndex, 0, null);
        }
        if (defaultPosition > 0 && layoutSize >= defaultPosition && layoutSize > 0) {
            move(defaultPosition, 0, null);
        }
        isArranged = true;

        if (savedState == null) return;
        setLayoutSize(savedState.getSize());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable parcelable = super.onSaveInstanceState();
        final ExpandableSavedState ss = new ExpandableSavedState(parcelable);
        ss.setSize(getCurrentPosition());
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
    public void toggle(final long duration, final @Nullable TimeInterpolator interpolator) {
        if (closePosition < getCurrentPosition()) {
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

        createExpandAnimator(getCurrentPosition(), layoutSize, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expand(final long duration, final @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration <= 0) {
            move(layoutSize, duration, interpolator);
            return;
        }
        createExpandAnimator(getCurrentPosition(), layoutSize, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collapse() {
        if (isAnimating) return;

        createExpandAnimator(getCurrentPosition(), closePosition, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collapse(final long duration, final @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration <= 0) {
            move(closePosition, duration, interpolator);
            return;
        }
        createExpandAnimator(getCurrentPosition(), closePosition, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDuration(final int duration) {
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
    public void setExpanded(final boolean expanded) {
        if (inRecyclerView) recyclerExpanded = expanded;

        final int currentPosition = getCurrentPosition();
        if ((expanded && (currentPosition == layoutSize))
                || (!expanded && currentPosition == closePosition)) return;

        isExpanded = expanded;
        setLayoutSize(expanded ? layoutSize : closePosition);
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
     * Initializes this layout.
     */
    public void initLayout() {
        closePosition = 0;
        layoutSize = 0;
        isArranged = false;
        isCalculatedSize = false;
        savedState = null;

        if (isVertical()) {
            measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.UNSPECIFIED));
        } else {
            measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        }
    }

    /**
     * @param position
     *
     * @see #move(int, long, TimeInterpolator)
     */
    public void move(int position) {
        move(position, duration, interpolator);
    }

    /**
     * Moves to position.
     * Sets 0 to duration if you want to move immediately.
     *
     * @param position
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    public void move(int position, long duration, @Nullable TimeInterpolator interpolator) {
        if (isAnimating || 0 > position || layoutSize < position) return;

        if (duration <= 0) {
            isExpanded = position > closePosition;
            setLayoutSize(position);
            requestLayout();
            notifyListeners();
            return;
        }
        createExpandAnimator(getCurrentPosition(), position, duration,
                interpolator == null ? this.interpolator : interpolator).start();
    }

    /**
     * @param index child view index
     *
     * @see #moveChild(int, long, TimeInterpolator)
     */
    public void moveChild(int index) {
        moveChild(index, duration, interpolator);
    }

    /**
     * Moves to bottom(VERTICAL) or right(HORIZONTAL) of child view
     * Sets 0 to duration if you want to move immediately.
     *
     * @param index        index child view index
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    public void moveChild(int index, long duration, @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        final int destination = getChildPosition(index) +
                (isVertical() ? getPaddingBottom() : getPaddingRight());
        if (duration <= 0) {
            isExpanded = destination > closePosition;
            setLayoutSize(destination);
            requestLayout();
            notifyListeners();
            return;
        }
        createExpandAnimator(getCurrentPosition(), destination,
                duration, interpolator == null ? this.interpolator : interpolator).start();
    }

    /**
     * Gets the width from left of layout if orientation is horizontal.
     * Gets the height from top of layout if orientation is vertical.
     *
     * @param index index of child view
     *
     * @return position from top or left
     */
    public int getChildPosition(final int index) {
        if (0 > index || childSizeList.size() <= index) {
            throw new IllegalArgumentException("There aren't the view having this index.");
        }
        return childSizeList.get(index);
    }

    /**
     * Gets the width from left of layout if orientation is horizontal.
     * Gets the height from top of layout if orientation is vertical.
     *
     * @return
     *
     * @see #closePosition
     */
    public int getClosePosition() {
        return closePosition;
    }

    /**
     * Sets the close position directly.
     *
     * @param position
     *
     * @see #closePosition
     * @see #setClosePositionIndex(int)
     */
    public void setClosePosition(final int position) {
        this.closePosition = position;
    }

    /**
     * Gets the current position.
     *
     * @return
     */
    public int getCurrentPosition() {
        return isVertical() ? getMeasuredHeight() : getMeasuredWidth();
    }

    /**
     * Sets close position using index of child view.
     *
     * @param childIndex
     *
     * @see #closePosition
     * @see #setClosePosition(int)
     */
    public void setClosePositionIndex(final int childIndex) {
        this.closePosition = getChildPosition(childIndex);
    }

    /**
     * Set true if expandable layout is used in recycler view.
     *
     * @param inRecyclerView
     */
    public void setInRecyclerView(final boolean inRecyclerView) {
        this.inRecyclerView = inRecyclerView;
    }

    private boolean isVertical() {
        return getOrientation() == LinearLayout.VERTICAL;
    }

    private void setLayoutSize(int size) {
        if (isVertical()) {
            getLayoutParams().height = size;
        } else {
            getLayoutParams().width = size;
        }
    }

    /**
     * Creates value animator.
     * Expand the layout if {@param to} is bigger than {@param from}.
     * Collapse the layout if {@param from} is bigger than {@param to}.
     *
     * @param from
     * @param to
     * @param duration
     * @param interpolator
     *
     * @return
     */
    private ValueAnimator createExpandAnimator(
            final int from, final int to, final long duration, final TimeInterpolator interpolator) {
        final ValueAnimator valueAnimator = ValueAnimator.ofInt(from, to);
        valueAnimator.setDuration(duration);
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animator) {
                if (isVertical()) {
                    getLayoutParams().height = (int) animator.getAnimatedValue();
                } else {
                    getLayoutParams().width = (int) animator.getAnimatedValue();
                }
                requestLayout();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                isAnimating = true;
                if (listener == null) return;

                listener.onAnimationStart();
                if (layoutSize == to) {
                    listener.onPreOpen();
                    return;
                }
                if (closePosition == to) {
                    listener.onPreClose();
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                isAnimating = false;
                isExpanded = to > closePosition;

                if (listener == null) return;

                listener.onAnimationEnd();
                if (to == layoutSize) {
                    listener.onOpened();
                    return;
                }
                if (to == closePosition) {
                    listener.onClosed();
                }
            }
        });
        return valueAnimator;
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