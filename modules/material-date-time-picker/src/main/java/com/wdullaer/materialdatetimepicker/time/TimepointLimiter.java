package com.wdullaer.materialdatetimepicker.time;

import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("WeakerAccess")
public interface TimepointLimiter extends Parcelable {
    /**
     * isOutOfRange indicates whether a particular timepoint is selectable or not
     * It is called multiple times in the rendering path, so it should be fast
     *
     * The index parameter indicates which picker is currently visible. This is necessary because
     * you typically only want to compare with a resolution up to the visible component. (The
     * implementation should ensure that 8 is selectable, if any valid timepoint with 8 as the hour
     * is selectable, when the hour picker is showing)
     *
     * Similarly the overall resolution of the picker is passed in, because it can impact the
     * comparisons an implementation does (especially when comparing with a disabled list)
     *
     * The scope of this method is most likely too broad, which makes it hard to reason about. It is
     * one of the main reasons the DefaultTimeLimiter implementation of this contains extensive
     * example and generate tests. The default implementation should cover 90% of use cases, but if
     * I ever notice that a substantial amount of people are trying to implement this themselves, it
     * might need to be redesigned.
     *
     * @param point A timepoint to validate
     * @param index The currently showing picker (hour, minute, second)
     * @param resolution The overall resolution of the picker
     * @return whether the Timepoint is out of range or selectable
     */
    boolean isOutOfRange(@Nullable Timepoint point, int index, @NonNull Timepoint.TYPE resolution);

    /**
     * isAmDisabled ndicates whether any times before midday are selectable
     * This method is called when the picker is initialized or when the user clicks / taps the AM or
     * PM buttons.
     * This means that it's result can't be updated when the picker is already being rendered
     * @return true if the AM selector should be disabled
     */
    boolean isAmDisabled();

    /**
     * isPmDisabled ndicates whether any times after midday are selectable
     * This method is called when the picker is initialized or when the user clicks / taps the AM or
     * PM buttons.
     * This means that it's result can't be updated when the picker is already being rendered
     * @return true if the PM selector should be disabled
     */
    boolean isPmDisabled();

    /**
     * roundToNearest returns the nearest selectable timepoint given a particular input
     * It is called whenever the user touches the screen, which means it can get called very
     * frequently if the user performs a drag operation
     *
     * Both the currently showing picker and the overall resolution are passed in, for similar
     * reasons as in isOutOfRange
     *
     * @param time the proposed selection
     * @param type the currently showing picker (hour, minute, second)
     * @param resolution the overall resolution of the picker
     * @return a selectable timepoint
     */
    @NonNull Timepoint roundToNearest(
            @NonNull Timepoint time,
            @Nullable Timepoint.TYPE type,
            @NonNull Timepoint.TYPE resolution
    );
}