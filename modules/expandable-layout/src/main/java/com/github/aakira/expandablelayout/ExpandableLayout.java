package com.github.aakira.expandablelayout;

import android.animation.TimeInterpolator;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface ExpandableLayout {

    /**
     * Duration of expand animation
     */
    int DEFAULT_DURATION = 300;
    /**
     * Visibility of the layout when the layout attaches
     */
    boolean DEFAULT_EXPANDED = false;
    /**
     * Orientation of child views
     */
    int HORIZONTAL = 0;
    /**
     * Orientation of child views
     */
    int VERTICAL = 1;

    /**
     * Orientation of layout
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({HORIZONTAL, VERTICAL})
    @interface Orientation {
    }

    /**
     * Starts animation the state of the view to the inverse of its current state.
     */
    void toggle();

    /**
     * Starts animation the state of the view to the inverse of its current state.
     *
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    void toggle(final long duration, @Nullable final TimeInterpolator interpolator);

    /**
     * Starts expand animation.
     */
    void expand();

    /**
     * Starts expand animation.
     *
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    void expand(final long duration, @Nullable final TimeInterpolator interpolator);

    /**
     * Starts collapse animation.
     */
    void collapse();

    /**
     * Starts collapse animation.
     *
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    void collapse(final long duration, @Nullable final TimeInterpolator interpolator);

    /**
     * Sets the expandable layout listener.
     *
     * @param listener ExpandableLayoutListener
     */
    void setListener(@NonNull final ExpandableLayoutListener listener);

    /**
     * Sets the length of the animation.
     * The default duration is 300 milliseconds.
     *
     * @param duration
     */
    void setDuration(final int duration);

    /**
     * Sets state of expanse.
     *
     * @param expanded The layout is visible if expanded is true
     */
    void setExpanded(final boolean expanded);

    /**
     * Gets state of expanse.
     *
     * @return true if the layout is visible
     */
    boolean isExpanded();

    /**
     * The time interpolator used in calculating the elapsed fraction of this animation. The
     * interpolator determines whether the animation runs with linear or non-linear motion,
     * such as acceleration and deceleration.
     * The default value is  {@link android.view.animation.AccelerateDecelerateInterpolator}
     *
     * @param interpolator
     */
    void setInterpolator(@NonNull final TimeInterpolator interpolator);
}