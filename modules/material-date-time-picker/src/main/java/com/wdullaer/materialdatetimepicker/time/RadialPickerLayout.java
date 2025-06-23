/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wdullaer.materialdatetimepicker.time;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import com.wdullaer.materialdatetimepicker.R;

import java.util.Calendar;
import java.util.Locale;

/**
 * The primary layout to hold the circular picker, and the am/pm buttons. This view will measure
 * itself to end up as a square. It also handles touches to be passed in to views that need to know
 * when they'd been touched.
 */
public class RadialPickerLayout extends FrameLayout implements OnTouchListener {
    private static final String TAG = "RadialPickerLayout";

    private final int TOUCH_SLOP;
    private final int TAP_TIMEOUT;

    private static final int VISIBLE_DEGREES_STEP_SIZE = 30;
    private static final int HOUR_VALUE_TO_DEGREES_STEP_SIZE = VISIBLE_DEGREES_STEP_SIZE;
    private static final int MINUTE_VALUE_TO_DEGREES_STEP_SIZE = 6;
    private static final int SECOND_VALUE_TO_DEGREES_STEP_SIZE = 6;
    private static final int HOUR_INDEX = TimePickerDialog.HOUR_INDEX;
    private static final int MINUTE_INDEX = TimePickerDialog.MINUTE_INDEX;
    private static final int SECOND_INDEX = TimePickerDialog.SECOND_INDEX;
    private static final int AM = TimePickerDialog.AM;
    private static final int PM = TimePickerDialog.PM;

    private Timepoint mLastValueSelected;

    private TimePickerController mController;
    private OnValueSelectedListener mListener;
    private boolean mTimeInitialized;
    private Timepoint mCurrentTime;
    private boolean mIs24HourMode;
    private int mCurrentItemShowing;

    private CircleView mCircleView;
    private AmPmCirclesView mAmPmCirclesView;
    private RadialTextsView mHourRadialTextsView;
    private RadialTextsView mMinuteRadialTextsView;
    private RadialTextsView mSecondRadialTextsView;
    private RadialSelectorView mHourRadialSelectorView;
    private RadialSelectorView mMinuteRadialSelectorView;
    private RadialSelectorView mSecondRadialSelectorView;
    private View mGrayBox;

    private int[] mSnapPrefer30sMap;
    private boolean mInputEnabled;
    private int mIsTouchingAmOrPm = -1;
    private boolean mDoingMove;
    private boolean mDoingTouch;
    private int mDownDegrees;
    private float mDownX;
    private float mDownY;
    private AccessibilityManager mAccessibilityManager;

    private AnimatorSet mTransition;
    private Handler mHandler = new Handler();

    public interface OnValueSelectedListener {
        void onValueSelected(Timepoint newTime);
        void enablePicker();
        void advancePicker(int index);
    }

    public RadialPickerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnTouchListener(this);
        ViewConfiguration vc = ViewConfiguration.get(context);
        TOUCH_SLOP = vc.getScaledTouchSlop();
        TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
        mDoingMove = false;

        mCircleView = new CircleView(context);
        addView(mCircleView);

        mAmPmCirclesView = new AmPmCirclesView(context);
        addView(mAmPmCirclesView);

        mHourRadialSelectorView = new RadialSelectorView(context);
        addView(mHourRadialSelectorView);
        mMinuteRadialSelectorView = new RadialSelectorView(context);
        addView(mMinuteRadialSelectorView);
        mSecondRadialSelectorView = new RadialSelectorView(context);
        addView(mSecondRadialSelectorView);

        mHourRadialTextsView = new RadialTextsView(context);
        addView(mHourRadialTextsView);
        mMinuteRadialTextsView = new RadialTextsView(context);
        addView(mMinuteRadialTextsView);
        mSecondRadialTextsView = new RadialTextsView(context);
        addView(mSecondRadialTextsView);

        // Prepare mapping to snap touchable degrees to selectable degrees.
        preparePrefer30sMap();

        mLastValueSelected = null;

        mInputEnabled = true;

        mGrayBox = new View(context);
        mGrayBox.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mGrayBox.setBackgroundColor(ContextCompat.getColor(context, R.color.mdtp_transparent_black));
        mGrayBox.setVisibility(View.INVISIBLE);
        addView(mGrayBox);

        mAccessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mTimeInitialized = false;
    }

    public void setOnValueSelectedListener(OnValueSelectedListener listener) {
        mListener = listener;
    }

    /**
     * Initialize the Layout with starting values.
     * @param context A context needed to inflate resources
     * @param locale A Locale to be used when generating strings
     * @param initialTime The initial selection of the Timepicker
     * @param is24HourMode Indicates whether we should render in 24hour mode or with AM/PM selectors
     */
    public void initialize(Context context, Locale locale, TimePickerController timePickerController,
            Timepoint initialTime, boolean is24HourMode) {
        if (mTimeInitialized) {
            Log.e(TAG, "Time has already been initialized.");
            return;
        }

        mController = timePickerController;
        mIs24HourMode = mAccessibilityManager.isTouchExplorationEnabled() || is24HourMode;

        // Initialize the circle and AM/PM circles if applicable.
        mCircleView.initialize(context, mController);
        mCircleView.invalidate();
        if (!mIs24HourMode && mController.getVersion() == TimePickerDialog.Version.VERSION_1) {
            mAmPmCirclesView.initialize(context, locale, mController, initialTime.isAM() ? AM : PM);
            mAmPmCirclesView.invalidate();
        }

        // Create the selection validators
        RadialTextsView.SelectionValidator secondValidator = selection -> {
            Timepoint newTime = new Timepoint(mCurrentTime.getHour(), mCurrentTime.getMinute(), selection);
            return !mController.isOutOfRange(newTime, SECOND_INDEX);
        };
        RadialTextsView.SelectionValidator minuteValidator = selection -> {
            Timepoint newTime = new Timepoint(mCurrentTime.getHour(), selection, mCurrentTime.getSecond());
            return !mController.isOutOfRange(newTime, MINUTE_INDEX);
        };
        RadialTextsView.SelectionValidator hourValidator = selection -> {
            Timepoint newTime = new Timepoint(selection, mCurrentTime.getMinute(), mCurrentTime.getSecond());
            if(!mIs24HourMode && getIsCurrentlyAmOrPm() == PM) newTime.setPM();
            if(!mIs24HourMode && getIsCurrentlyAmOrPm() == AM) newTime.setAM();
            return !mController.isOutOfRange(newTime, HOUR_INDEX);
        };

        // Initialize the hours and minutes numbers.
        int[] hours = {12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        int[] hours_24 = {0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23};
        int[] minutes = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55};
        int[] seconds = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55};
        String[] hoursTexts = new String[12];
        String[] innerHoursTexts = new String[12];
        String[] minutesTexts = new String[12];
        String[] secondsTexts = new String[12];
        for (int i = 0; i < 12; i++) {
            hoursTexts[i] = is24HourMode?
                    String.format(locale, "%02d", hours_24[i]) : String.format(locale, "%d", hours[i]);
            innerHoursTexts[i] = String.format(locale, "%d", hours[i]);
            minutesTexts[i] = String.format(locale, "%02d", minutes[i]);
            secondsTexts[i] = String.format(locale, "%02d", seconds[i]);
        }
        // The version 2 layout has the hours > 12 on the inner circle rather than the outer circle
        // Inner circle and outer circle should be swapped (see #411)
        if (mController.getVersion() == TimePickerDialog.Version.VERSION_2) {
            String[] temp = hoursTexts;
            hoursTexts = innerHoursTexts;
            innerHoursTexts = temp;
        }

        mHourRadialTextsView.initialize(context,
                hoursTexts, (is24HourMode ? innerHoursTexts : null), mController, hourValidator, true);
        mHourRadialTextsView.setSelection(is24HourMode ? initialTime.getHour() : hours[initialTime.getHour() % 12]);
        mHourRadialTextsView.invalidate();
        mMinuteRadialTextsView.initialize(context, minutesTexts, null, mController, minuteValidator, false);
        mMinuteRadialTextsView.setSelection(initialTime.getMinute());
        mMinuteRadialTextsView.invalidate();
        mSecondRadialTextsView.initialize(context, secondsTexts, null, mController, secondValidator, false);
        mSecondRadialTextsView.setSelection(initialTime.getSecond());
        mSecondRadialTextsView.invalidate();

        // Initialize the currently-selected hour and minute.
        mCurrentTime = initialTime;
        int hourDegrees = (initialTime.getHour() % 12) * HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        mHourRadialSelectorView.initialize(context, mController, is24HourMode, true,
                hourDegrees, isHourInnerCircle(initialTime.getHour()));
        int minuteDegrees = initialTime.getMinute() * MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
        mMinuteRadialSelectorView.initialize(context, mController, false, false,
                minuteDegrees, false);
        int secondDegrees = initialTime.getSecond() * SECOND_VALUE_TO_DEGREES_STEP_SIZE;
        mSecondRadialSelectorView.initialize(context, mController, false, false,
                secondDegrees, false);

        mTimeInitialized = true;
    }

    public void setTime(Timepoint time) {
        setItem(HOUR_INDEX, time);
    }

    /**
     * Set either the hour, the minute or the second. Will set the internal value, and set the selection.
     */
    private void setItem(int index, Timepoint time) {
        time = roundToValidTime(time, index);
        mCurrentTime = time;
        reselectSelector(time, false, index);
    }

    /**
     * Check if a given hour appears in the outer circle or the inner circle
     * @return true if the hour is in the inner circle, false if it's in the outer circle.
     */
    private boolean isHourInnerCircle(int hourOfDay) {
        // We'll have the 00 hours on the outside circle.
        boolean isMorning = hourOfDay <= 12 && hourOfDay != 0;
        // In the version 2 layout the circles are swapped
        if (mController.getVersion() != TimePickerDialog.Version.VERSION_1) isMorning = !isMorning;
        return mIs24HourMode && isMorning;
    }

    public int getHours() {
        return mCurrentTime.getHour();
    }

    public int getMinutes() {
        return mCurrentTime.getMinute();
    }

    public int getSeconds() {
        return mCurrentTime.getSecond();
    }

    public Timepoint getTime() {
        return mCurrentTime;
    }

    /**
     * If the hours are showing, return the current hour. If the minutes are showing, return the
     * current minute.
     */
    private int getCurrentlyShowingValue() {
        int currentIndex = getCurrentItemShowing();
        switch(currentIndex) {
            case HOUR_INDEX:
                return mCurrentTime.getHour();
            case MINUTE_INDEX:
                return mCurrentTime.getMinute();
            case SECOND_INDEX:
                return mCurrentTime.getSecond();
            default:
                return -1;
        }
    }

    public int getIsCurrentlyAmOrPm() {
        if (mCurrentTime.isAM()) {
            return AM;
        } else if (mCurrentTime.isPM()) {
            return PM;
        }
        return -1;
    }

    /**
     * Set the internal value as either AM or PM, and update the AM/PM circle displays.
     * @param amOrPm Integer representing AM of PM (use the supplied constants)
     */
    public void setAmOrPm(int amOrPm) {
        mAmPmCirclesView.setAmOrPm(amOrPm);
        mAmPmCirclesView.invalidate();
        Timepoint newSelection = new Timepoint(mCurrentTime);
        if(amOrPm == AM) newSelection.setAM();
        else if(amOrPm == PM) newSelection.setPM();
        newSelection = roundToValidTime(newSelection, HOUR_INDEX);
        reselectSelector(newSelection, false, HOUR_INDEX);
        mCurrentTime = newSelection;
        mListener.onValueSelected(newSelection);
    }

    /**
     * Split up the 360 degrees of the circle among the 60 selectable values. Assigns a larger
     * selectable area to each of the 12 visible values, such that the ratio of space apportioned
     * to a visible value : space apportioned to a non-visible value will be 14 : 4.
     * E.g. the output of 30 degrees should have a higher range of input associated with it than
     * the output of 24 degrees, because 30 degrees corresponds to a visible number on the clock
     * circle (5 on the minutes, 1 or 13 on the hours).
     */
    private void preparePrefer30sMap() {
        // We'll split up the visible output and the non-visible output such that each visible
        // output will correspond to a range of 14 associated input degrees, and each non-visible
        // output will correspond to a range of 4 associate input degrees, so visible numbers
        // are more than 3 times easier to get than non-visible numbers:
        // {354-359,0-7}:0, {8-11}:6, {12-15}:12, {16-19}:18, {20-23}:24, {24-37}:30, etc.
        //
        // If an output of 30 degrees should correspond to a range of 14 associated degrees, then
        // we'll need any input between 24 - 37 to snap to 30. Working out from there, 20-23 should
        // snap to 24, while 38-41 should snap to 36. This is somewhat counter-intuitive, that you
        // can be touching 36 degrees but have the selection snapped to 30 degrees; however, this
        // inconsistency isn't noticeable at such fine-grained degrees, and it affords us the
        // ability to aggressively prefer the visible values by a factor of more than 3:1, which
        // greatly contributes to the selectability of these values.

        // Our input will be 0 through 360.
        mSnapPrefer30sMap = new int[361];

        // The first output is 0, and each following output will increment by 6 {0, 6, 12, ...}.
        int snappedOutputDegrees = 0;
        // Count of how many inputs we've designated to the specified output.
        int count = 1;
        // How many input we expect for a specified output. This will be 14 for output divisible
        // by 30, and 4 for the remaining output. We'll special case the outputs of 0 and 360, so
        // the caller can decide which they need.
        int expectedCount = 8;
        // Iterate through the input.
        for (int degrees = 0; degrees < 361; degrees++) {
            // Save the input-output mapping.
            mSnapPrefer30sMap[degrees] = snappedOutputDegrees;
            // If this is the last input for the specified output, calculate the next output and
            // the next expected count.
            if (count == expectedCount) {
                snappedOutputDegrees += 6;
                if (snappedOutputDegrees == 360) {
                    expectedCount = 7;
                } else if (snappedOutputDegrees % 30 == 0) {
                    expectedCount = 14;
                } else {
                    expectedCount = 4;
                }
                count = 1;
            } else {
                count++;
            }
        }
    }

    /**
     * Returns mapping of any input degrees (0 to 360) to one of 60 selectable output degrees,
     * where the degrees corresponding to visible numbers (i.e. those divisible by 30) will be
     * weighted heavier than the degrees corresponding to non-visible numbers.
     * See {@link #preparePrefer30sMap()} documentation for the rationale and generation of the
     * mapping.
     */
    private int snapPrefer30s(int degrees) {
        if (mSnapPrefer30sMap == null) {
            return -1;
        }
        return mSnapPrefer30sMap[degrees];
    }

    /**
     * Returns mapping of any input degrees (0 to 360) to one of 12 visible output degrees (all
     * multiples of 30), where the input will be "snapped" to the closest visible degrees.
     * @param degrees The input degrees
     * @param forceHigherOrLower The output may be forced to either the higher or lower step, or may
     * be allowed to snap to whichever is closer. Use 1 to force strictly higher, -1 to force
     * strictly lower, and 0 to snap to the closer one.
     * @return output degrees, will be a multiple of 30
     */
    private static int snapOnly30s(int degrees, int forceHigherOrLower) {
        int stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
        int floor = (degrees / stepSize) * stepSize;
        int ceiling = floor + stepSize;
        if (forceHigherOrLower == 1) {
            degrees = ceiling;
        } else if (forceHigherOrLower == -1) {
            if (degrees == floor) {
                floor -= stepSize;
            }
            degrees = floor;
        } else {
            if ((degrees - floor) < (ceiling - degrees)) {
                degrees = floor;
            } else {
                degrees = ceiling;
            }
        }
        return degrees;
    }

    /**
     * Snap the input to a selectable value
     * @param newSelection Timepoint - Time which should be rounded
     * @param currentItemShowing int - The index of the current view
     * @return Timepoint - the rounded value
     */
    private Timepoint roundToValidTime(Timepoint newSelection, int currentItemShowing) {
        switch(currentItemShowing) {
            case HOUR_INDEX:
                return mController.roundToNearest(newSelection, null);
            case MINUTE_INDEX:
                return mController.roundToNearest(newSelection, Timepoint.TYPE.HOUR);
            default:
                return mController.roundToNearest(newSelection, Timepoint.TYPE.MINUTE);
        }
    }

    /**
     * For the currently showing view (either hours, minutes or seconds), re-calculate the position
     * for the selector, and redraw it at that position. The text representing the currently
     * selected value will be redrawn if required.
     * @param newSelection Timpoint - Time which should be selected.
     * @param forceDrawDot The dot in the circle will generally only be shown when the selection
     * @param index The picker to use as a reference. Will be getCurrentItemShow() except when AM/PM is changed
     * is on non-visible values, but use this to force the dot to be shown.
     */
    private void reselectSelector(Timepoint newSelection, boolean forceDrawDot, int index) {
        switch(index) {
            case HOUR_INDEX:
                // The selection might have changed, recalculate the degrees and innerCircle values
                int hour = newSelection.getHour();
                boolean isInnerCircle = isHourInnerCircle(hour);
                int degrees = (hour%12)*360/12;
                if(!mIs24HourMode) hour = hour%12;
                if(!mIs24HourMode && hour == 0) hour += 12;

                mHourRadialSelectorView.setSelection(degrees, isInnerCircle, forceDrawDot);
                mHourRadialTextsView.setSelection(hour);
                // If we rounded the minutes, reposition the minuteSelector too.
                if(newSelection.getMinute() != mCurrentTime.getMinute()) {
                    int minDegrees = newSelection.getMinute() * (360 / 60);
                    mMinuteRadialSelectorView.setSelection(minDegrees, isInnerCircle, forceDrawDot);
                    mMinuteRadialTextsView.setSelection(newSelection.getMinute());
                }
                // If we rounded the seconds, reposition the secondSelector too.
                if(newSelection.getSecond() != mCurrentTime.getSecond()) {
                    int secDegrees = newSelection.getSecond() * (360 / 60);
                    mSecondRadialSelectorView.setSelection(secDegrees, isInnerCircle, forceDrawDot);
                    mSecondRadialTextsView.setSelection(newSelection.getSecond());
                }
                break;
            case MINUTE_INDEX:
                // The selection might have changed, recalculate the degrees
                degrees = newSelection.getMinute() * (360 / 60);

                mMinuteRadialSelectorView.setSelection(degrees, false, forceDrawDot);
                mMinuteRadialTextsView.setSelection(newSelection.getMinute());
                // If we rounded the seconds, reposition the secondSelector too.
                if(newSelection.getSecond() != mCurrentTime.getSecond()) {
                    int secDegrees = newSelection.getSecond()* (360 / 60);
                    mSecondRadialSelectorView.setSelection(secDegrees, false, forceDrawDot);
                    mSecondRadialTextsView.setSelection(newSelection.getSecond());
                }
                break;
            case SECOND_INDEX:
                // The selection might have changed, recalculate the degrees
                degrees = newSelection.getSecond() * (360 / 60);
                mSecondRadialSelectorView.setSelection(degrees, false, forceDrawDot);
                mSecondRadialTextsView.setSelection(newSelection.getSecond());
        }

        // Invalidate the currently showing picker to force a redraw
        switch(getCurrentItemShowing()) {
            case HOUR_INDEX:
                mHourRadialSelectorView.invalidate();
                mHourRadialTextsView.invalidate();
                break;
            case MINUTE_INDEX:
                mMinuteRadialSelectorView.invalidate();
                mMinuteRadialTextsView.invalidate();
                break;
            case SECOND_INDEX:
                mSecondRadialSelectorView.invalidate();
                mSecondRadialTextsView.invalidate();
        }
    }

    private Timepoint getTimeFromDegrees(int degrees, boolean isInnerCircle, boolean forceToVisibleValue) {
        if (degrees == -1) {
            return null;
        }
        int currentShowing = getCurrentItemShowing();

        int stepSize;
        boolean allowFineGrained = !forceToVisibleValue &&
                (currentShowing == MINUTE_INDEX || currentShowing == SECOND_INDEX);
        if (allowFineGrained) {
            degrees = snapPrefer30s(degrees);
        } else {
            degrees = snapOnly30s(degrees, 0);
        }

        switch (currentShowing) {
            case HOUR_INDEX:
                stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
                break;
            case MINUTE_INDEX:
                stepSize = MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
                break;
            default:
                stepSize = SECOND_VALUE_TO_DEGREES_STEP_SIZE;
        }

        // TODO: simplify this logic. Just appending a swap of the values at the end for the v2
        // TODO: layout makes this code rather hard to read
        if (currentShowing == HOUR_INDEX) {
            if (mIs24HourMode) {
                if (degrees == 0 && isInnerCircle) {
                    degrees = 360;
                } else if (degrees == 360 && !isInnerCircle) {
                    degrees = 0;
                }
            } else if (degrees == 0) {
                degrees = 360;
            }
        } else if (degrees == 360 && (currentShowing == MINUTE_INDEX || currentShowing == SECOND_INDEX)) {
            degrees = 0;
        }

        int value = degrees / stepSize;

        if (currentShowing == HOUR_INDEX && mIs24HourMode && !isInnerCircle && degrees != 0) {
            value += 12;
        }

        if (currentShowing == HOUR_INDEX
                && mController.getVersion() != TimePickerDialog.Version.VERSION_1
                && mIs24HourMode) {
            value = (value + 12) % 24;
        }

        Timepoint newSelection;
        switch(currentShowing) {
            case HOUR_INDEX:
                int hour = value;
                if(!mIs24HourMode && getIsCurrentlyAmOrPm() == PM && degrees != 360) hour += 12;
                if(!mIs24HourMode && getIsCurrentlyAmOrPm() == AM && degrees == 360) hour = 0;
                newSelection = new Timepoint(hour, mCurrentTime.getMinute(), mCurrentTime.getSecond());
                break;
            case MINUTE_INDEX:
                newSelection = new Timepoint(mCurrentTime.getHour(), value, mCurrentTime.getSecond());
                break;
            case SECOND_INDEX:
                newSelection = new Timepoint(mCurrentTime.getHour(), mCurrentTime.getMinute(), value);
                break;
            default:
                newSelection = mCurrentTime;
        }

        return newSelection;
    }

    /**
     * Calculate the degrees within the circle that corresponds to the specified coordinates, if
     * the coordinates are within the range that will trigger a selection.
     * @param pointX The x coordinate.
     * @param pointY The y coordinate.
     * @param forceLegal Force the selection to be legal, regardless of how far the coordinates are
     * from the actual numbers.
     * @param isInnerCircle If the selection may be in the inner circle, pass in a size-1 boolean
     * array here, inside which the value will be true if the selection is in the inner circle,
     * and false if in the outer circle.
     * @return Degrees from 0 to 360, if the selection was within the legal range. -1 if not.
     */
    private int getDegreesFromCoords(float pointX, float pointY, boolean forceLegal,
            final Boolean[] isInnerCircle) {
        switch(getCurrentItemShowing()) {
            case HOUR_INDEX:
                return mHourRadialSelectorView.getDegreesFromCoords(
                        pointX, pointY, forceLegal, isInnerCircle);
            case MINUTE_INDEX:
                return mMinuteRadialSelectorView.getDegreesFromCoords(
                        pointX, pointY, forceLegal, isInnerCircle);
            case SECOND_INDEX:
                return mSecondRadialSelectorView.getDegreesFromCoords(
                        pointX, pointY, forceLegal, isInnerCircle);
            default:
                return -1;
        }
    }

    /**
     * Get the item (hours, minutes or seconds) that is currently showing.
     */
    public int getCurrentItemShowing() {
        if (mCurrentItemShowing != HOUR_INDEX && mCurrentItemShowing != MINUTE_INDEX && mCurrentItemShowing != SECOND_INDEX) {
            Log.e(TAG, "Current item showing was unfortunately set to " + mCurrentItemShowing);
            return -1;
        }
        return mCurrentItemShowing;
    }

    /**
     * Set either seconds, minutes or hours as showing.
     * @param animate True to animate the transition, false to show with no animation.
     */
    public void setCurrentItemShowing(int index, boolean animate) {
        if (index != HOUR_INDEX && index != MINUTE_INDEX && index != SECOND_INDEX) {
            Log.e(TAG, "TimePicker does not support view at index "+index);
            return;
        }

        int lastIndex = getCurrentItemShowing();
        mCurrentItemShowing = index;
        reselectSelector(getTime(), true, index);

        if (animate && (index != lastIndex)) {
            ObjectAnimator[] anims = new ObjectAnimator[4];
            if (index == MINUTE_INDEX && lastIndex == HOUR_INDEX) {
                anims[0] = mHourRadialTextsView.getDisappearAnimator();
                anims[1] = mHourRadialSelectorView.getDisappearAnimator();
                anims[2] = mMinuteRadialTextsView.getReappearAnimator();
                anims[3] = mMinuteRadialSelectorView.getReappearAnimator();
            } else if (index == HOUR_INDEX && lastIndex == MINUTE_INDEX){
                anims[0] = mHourRadialTextsView.getReappearAnimator();
                anims[1] = mHourRadialSelectorView.getReappearAnimator();
                anims[2] = mMinuteRadialTextsView.getDisappearAnimator();
                anims[3] = mMinuteRadialSelectorView.getDisappearAnimator();
            } else if (index == MINUTE_INDEX && lastIndex == SECOND_INDEX) {
                anims[0] = mSecondRadialTextsView.getDisappearAnimator();
                anims[1] = mSecondRadialSelectorView.getDisappearAnimator();
                anims[2] = mMinuteRadialTextsView.getReappearAnimator();
                anims[3] = mMinuteRadialSelectorView.getReappearAnimator();
            } else if (index == HOUR_INDEX && lastIndex == SECOND_INDEX) {
                anims[0] = mSecondRadialTextsView.getDisappearAnimator();
                anims[1] = mSecondRadialSelectorView.getDisappearAnimator();
                anims[2] = mHourRadialTextsView.getReappearAnimator();
                anims[3] = mHourRadialSelectorView.getReappearAnimator();
            } else if (index == SECOND_INDEX && lastIndex == MINUTE_INDEX) {
                anims[0] = mSecondRadialTextsView.getReappearAnimator();
                anims[1] = mSecondRadialSelectorView.getReappearAnimator();
                anims[2] = mMinuteRadialTextsView.getDisappearAnimator();
                anims[3] = mMinuteRadialSelectorView.getDisappearAnimator();
            } else if (index == SECOND_INDEX && lastIndex == HOUR_INDEX) {
                anims[0] = mSecondRadialTextsView.getReappearAnimator();
                anims[1] = mSecondRadialSelectorView.getReappearAnimator();
                anims[2] = mHourRadialTextsView.getDisappearAnimator();
                anims[3] = mHourRadialSelectorView.getDisappearAnimator();
            }

            if (anims[0] != null && anims[1] != null && anims[2] != null &&
                anims[3] != null) {
                if (mTransition != null && mTransition.isRunning()) {
                    mTransition.end();
                }
                mTransition = new AnimatorSet();
                mTransition.playTogether(anims);
                mTransition.start();
            } else {
                transitionWithoutAnimation(index);
            }
        } else {
            transitionWithoutAnimation(index);
        }
    }

    private void transitionWithoutAnimation(int index) {
        int hourAlpha = (index == HOUR_INDEX) ? 1 : 0;
        int minuteAlpha = (index == MINUTE_INDEX) ? 1 : 0;
        int secondAlpha = (index == SECOND_INDEX) ? 1 : 0;
        mHourRadialTextsView.setAlpha(hourAlpha);
        mHourRadialSelectorView.setAlpha(hourAlpha);
        mMinuteRadialTextsView.setAlpha(minuteAlpha);
        mMinuteRadialSelectorView.setAlpha(minuteAlpha);
        mSecondRadialTextsView.setAlpha(secondAlpha);
        mSecondRadialSelectorView.setAlpha(secondAlpha);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final float eventX = event.getX();
        final float eventY = event.getY();
        int degrees;
        Timepoint value;
        final Boolean[] isInnerCircle = new Boolean[1];
        isInnerCircle[0] = false;

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mInputEnabled) {
                    return true;
                }

                mDownX = eventX;
                mDownY = eventY;

                mLastValueSelected = null;
                mDoingMove = false;
                mDoingTouch = true;
                // If we're showing the AM/PM, check to see if the user is touching it.
                if (!mIs24HourMode && mController.getVersion() == TimePickerDialog.Version.VERSION_1) {
                    mIsTouchingAmOrPm = mAmPmCirclesView.getIsTouchingAmOrPm(eventX, eventY);
                } else {
                    mIsTouchingAmOrPm = -1;
                }
                if (mIsTouchingAmOrPm == AM || mIsTouchingAmOrPm == PM) {
                    // If the touch is on AM or PM, set it as "touched" after the TAP_TIMEOUT
                    // in case the user moves their finger quickly.
                    mController.tryVibrate();
                    mDownDegrees = -1;
                    mHandler.postDelayed(() -> {
                        mAmPmCirclesView.setAmOrPmPressed(mIsTouchingAmOrPm);
                        mAmPmCirclesView.invalidate();
                    }, TAP_TIMEOUT);
                } else {
                    // If we're in accessibility mode, force the touch to be legal. Otherwise,
                    // it will only register within the given touch target zone.
                    boolean forceLegal = mAccessibilityManager.isTouchExplorationEnabled();
                    // Calculate the degrees that is currently being touched.
                    mDownDegrees = getDegreesFromCoords(eventX, eventY, forceLegal, isInnerCircle);
                    Timepoint selectedTime = getTimeFromDegrees(mDownDegrees, isInnerCircle[0], false);
                    if(mController.isOutOfRange(selectedTime, getCurrentItemShowing())) mDownDegrees = -1;
                    if (mDownDegrees != -1) {
                        // If it's a legal touch, set that number as "selected" after the
                        // TAP_TIMEOUT in case the user moves their finger quickly.
                        mController.tryVibrate();
                        mHandler.postDelayed(() -> {
                            mDoingMove = true;
                            mLastValueSelected = getTimeFromDegrees(mDownDegrees, isInnerCircle[0],
                                    false);
                            mLastValueSelected = roundToValidTime(mLastValueSelected, getCurrentItemShowing());
                            // Redraw
                            reselectSelector(mLastValueSelected, true, getCurrentItemShowing());
                            mListener.onValueSelected(mLastValueSelected);
                        }, TAP_TIMEOUT);
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mInputEnabled) {
                    // We shouldn't be in this state, because input is disabled.
                    Log.e(TAG, "Input was disabled, but received ACTION_MOVE.");
                    return true;
                }

                float dY = Math.abs(eventY - mDownY);
                float dX = Math.abs(eventX - mDownX);

                if (!mDoingMove && dX <= TOUCH_SLOP && dY <= TOUCH_SLOP) {
                    // Hasn't registered down yet, just slight, accidental movement of finger.
                    break;
                }

                // If we're in the middle of touching down on AM or PM, check if we still are.
                // If so, no-op. If not, remove its pressed state. Either way, no need to check
                // for touches on the other circle.
                if (mIsTouchingAmOrPm == AM || mIsTouchingAmOrPm == PM) {
                    mHandler.removeCallbacksAndMessages(null);
                    int isTouchingAmOrPm = mAmPmCirclesView.getIsTouchingAmOrPm(eventX, eventY);
                    if (isTouchingAmOrPm != mIsTouchingAmOrPm) {
                        mAmPmCirclesView.setAmOrPmPressed(-1);
                        mAmPmCirclesView.invalidate();
                        mIsTouchingAmOrPm = -1;
                    }
                    break;
                }

                if (mDownDegrees == -1) {
                    // Original down was illegal, so no movement will register.
                    break;
                }

                // We're doing a move along the circle, so move the selection as appropriate.
                mDoingMove = true;
                mHandler.removeCallbacksAndMessages(null);
                degrees = getDegreesFromCoords(eventX, eventY, true, isInnerCircle);
                if (degrees != -1) {
                    value = roundToValidTime(
                            getTimeFromDegrees(degrees, isInnerCircle[0], false),
                            getCurrentItemShowing()
                    );
                    reselectSelector(value, true, getCurrentItemShowing());
                    if (value != null && (mLastValueSelected == null || !mLastValueSelected.equals(value))) {
                        mController.tryVibrate();
                        mLastValueSelected = value;
                        mListener.onValueSelected(value);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!mInputEnabled) {
                    // If our touch input was disabled, tell the listener to re-enable us.
                    Log.d(TAG, "Input was disabled, but received ACTION_UP.");
                    mListener.enablePicker();
                    return true;
                }

                mHandler.removeCallbacksAndMessages(null);
                mDoingTouch = false;

                // If we're touching AM or PM, set it as selected, and tell the listener.
                if (mIsTouchingAmOrPm == AM || mIsTouchingAmOrPm == PM) {
                    int isTouchingAmOrPm = mAmPmCirclesView.getIsTouchingAmOrPm(eventX, eventY);
                    mAmPmCirclesView.setAmOrPmPressed(-1);
                    mAmPmCirclesView.invalidate();

                    if (isTouchingAmOrPm == mIsTouchingAmOrPm) {
                        mAmPmCirclesView.setAmOrPm(isTouchingAmOrPm);
                        if (getIsCurrentlyAmOrPm() != isTouchingAmOrPm) {
                            Timepoint newSelection = new Timepoint(mCurrentTime);
                            if(mIsTouchingAmOrPm == AM) newSelection.setAM();
                            else if(mIsTouchingAmOrPm == PM) newSelection.setPM();
                            newSelection = roundToValidTime(newSelection, HOUR_INDEX);
                            reselectSelector(newSelection, false, HOUR_INDEX);
                            mCurrentTime = newSelection;
                            mListener.onValueSelected(newSelection);

                        }
                    }
                    mIsTouchingAmOrPm = -1;
                    break;
                }

                // If we have a legal degrees selected, set the value and tell the listener.
                if (mDownDegrees != -1) {
                    degrees = getDegreesFromCoords(eventX, eventY, mDoingMove, isInnerCircle);
                    if (degrees != -1) {
                        value = getTimeFromDegrees(degrees, isInnerCircle[0], !mDoingMove);
                        value = roundToValidTime(value, getCurrentItemShowing());
                        reselectSelector(value, false, getCurrentItemShowing());
                        mCurrentTime = value;
                        mListener.onValueSelected(value);
                        mListener.advancePicker(getCurrentItemShowing());
                    }
                }
                mDoingMove = false;
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * Set touch input as enabled or disabled, for use with keyboard mode.
     */
    public boolean trySettingInputEnabled(boolean inputEnabled) {
        if (mDoingTouch && !inputEnabled) {
            // If we're trying to disable input, but we're in the middle of a touch event,
            // we'll allow the touch event to continue before disabling input.
            return false;
        }

        mInputEnabled = inputEnabled;
        mGrayBox.setVisibility(inputEnabled? View.INVISIBLE : View.VISIBLE);
        return true;
    }

    /**
     * Necessary for accessibility, to ensure we support "scrolling" forward and backward
     * in the circle.
     */
    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Build.VERSION.SDK_INT >= 21) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }
        else {
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
    }

    /**
     * Announce the currently-selected time when launched.
     */
    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Clear the event's current text so that only the current time will be spoken.
            event.getText().clear();
            Calendar time = Calendar.getInstance();
            time.set(Calendar.HOUR, getHours());
            time.set(Calendar.MINUTE, getMinutes());
            time.set(Calendar.SECOND, getSeconds());
            long millis = time.getTimeInMillis();
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (mIs24HourMode) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            String timeString = DateUtils.formatDateTime(getContext(), millis, flags);
            event.getText().add(timeString);
            return true;
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    /**
     * When scroll forward/backward events are received, jump the time to the higher/lower
     * discrete, visible value on the circle.
     */
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }

        int changeMultiplier = 0;
        int forward;
        int backward;
        if (Build.VERSION.SDK_INT >= 16) {
            forward = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            backward = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        } else {
            forward = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
            backward = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
        }
        if (action == forward) {
            changeMultiplier = 1;
        } else if (action == backward) {
            changeMultiplier = -1;
        }
        if (changeMultiplier != 0) {
            int value = getCurrentlyShowingValue();
            int stepSize = 0;
            int currentItemShowing = getCurrentItemShowing();
            if (currentItemShowing == HOUR_INDEX) {
                stepSize = HOUR_VALUE_TO_DEGREES_STEP_SIZE;
                value %= 12;
            } else if (currentItemShowing == MINUTE_INDEX) {
                stepSize = MINUTE_VALUE_TO_DEGREES_STEP_SIZE;
            } else if (currentItemShowing == SECOND_INDEX) {
                stepSize = SECOND_VALUE_TO_DEGREES_STEP_SIZE;
            }

            int degrees = value * stepSize;
            degrees = snapOnly30s(degrees, changeMultiplier);
            value = degrees / stepSize;
            int maxValue = 0;
            int minValue = 0;
            if (currentItemShowing == HOUR_INDEX) {
                if (mIs24HourMode) {
                    maxValue = 23;
                } else {
                    maxValue = 12;
                    minValue = 1;
                }
            } else {
                maxValue = 55;
            }
            if (value > maxValue) {
                // If we scrolled forward past the highest number, wrap around to the lowest.
                value = minValue;
            } else if (value < minValue) {
                // If we scrolled backward past the lowest number, wrap around to the highest.
                value = maxValue;
            }

            Timepoint newSelection;
            switch(currentItemShowing) {
                case HOUR_INDEX:
                    newSelection = new Timepoint(
                            value,
                            mCurrentTime.getMinute(),
                            mCurrentTime.getSecond()
                    );
                    break;
                case MINUTE_INDEX:
                    newSelection = new Timepoint(
                            mCurrentTime.getHour(),
                            value,
                            mCurrentTime.getSecond()
                    );
                    break;
                case SECOND_INDEX:
                    newSelection = new Timepoint(
                            mCurrentTime.getHour(),
                            mCurrentTime.getMinute(),
                            value
                    );
                    break;
                default:
                    newSelection = mCurrentTime;
            }

            setItem(currentItemShowing, newSelection);
            mListener.onValueSelected(newSelection);
            return true;
        }

        return false;
    }
}
