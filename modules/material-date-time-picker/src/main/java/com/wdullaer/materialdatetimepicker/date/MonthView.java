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

package com.wdullaer.materialdatetimepicker.date;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.wdullaer.materialdatetimepicker.R;
import com.wdullaer.materialdatetimepicker.date.MonthAdapter.CalendarDay;

import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * A calendar-like view displaying a specified month and the appropriate selectable day numbers
 * within the specified month.
 */
public abstract class MonthView extends View {

    protected static int DEFAULT_HEIGHT = 32;
    protected static final int DEFAULT_SELECTED_DAY = -1;
    protected static final int DEFAULT_WEEK_START = Calendar.SUNDAY;
    protected static final int DEFAULT_NUM_DAYS = 7;
    protected static final int DEFAULT_NUM_ROWS = 6;
    protected static final int MAX_NUM_ROWS = 6;

    private static final int SELECTED_CIRCLE_ALPHA = 255;

    protected static int DAY_SEPARATOR_WIDTH = 1;
    protected static int MINI_DAY_NUMBER_TEXT_SIZE;
    protected static int MONTH_LABEL_TEXT_SIZE;
    protected static int MONTH_DAY_LABEL_TEXT_SIZE;
    protected static int MONTH_HEADER_SIZE;
    protected static int MONTH_HEADER_SIZE_V2;
    protected static int DAY_SELECTED_CIRCLE_SIZE;
    protected static int DAY_HIGHLIGHT_CIRCLE_SIZE;
    protected static int DAY_HIGHLIGHT_CIRCLE_MARGIN;

    protected DatePickerController mController;

    // affects the padding on the sides of this view
    protected int mEdgePadding = 0;

    private String mDayOfWeekTypeface;
    private String mMonthTitleTypeface;

    protected Paint mMonthNumPaint;
    protected Paint mMonthTitlePaint;
    protected Paint mSelectedCirclePaint;
    protected Paint mMonthDayLabelPaint;

    private final StringBuilder mStringBuilder;

    protected int mMonth;

    protected int mYear;
    // Quick reference to the width of this view, matches parent
    protected int mWidth;
    // The height this view should draw at in pixels, set by height param
    protected int mRowHeight = DEFAULT_HEIGHT;
    // If this view contains the today
    protected boolean mHasToday = false;
    // Which day is selected [0-6] or -1 if no day is selected
    protected int mSelectedDay = -1;
    // Which day is today [0-6] or -1 if no day is today
    protected int mToday = DEFAULT_SELECTED_DAY;
    // Which day of the week to start on [0-6]
    protected int mWeekStart = DEFAULT_WEEK_START;
    // How many days to display
    protected int mNumDays = DEFAULT_NUM_DAYS;
    // The number of days + a spot for week number if it is displayed
    protected int mNumCells = mNumDays;

    private final Calendar mCalendar;
    protected final Calendar mDayLabelCalendar;
    private final MonthViewTouchHelper mTouchHelper;

    protected int mNumRows = DEFAULT_NUM_ROWS;

    // Optional listener for handling day click actions
    protected OnDayClickListener mOnDayClickListener;

    // Whether to prevent setting the accessibility delegate
    private boolean mLockAccessibilityDelegate;

    protected int mDayTextColor;
    protected int mSelectedDayTextColor;
    protected int mMonthDayTextColor;
    protected int mTodayNumberColor;
    protected int mHighlightedDayTextColor;
    protected int mDisabledDayTextColor;
    protected int mMonthTitleColor;

    private SimpleDateFormat weekDayLabelFormatter;

    public MonthView(Context context) {
        this(context, null, null);
    }

    public MonthView(Context context, AttributeSet attr, DatePickerController controller) {
        super(context, attr);
        mController = controller;
        Resources res = context.getResources();

        mDayLabelCalendar = Calendar.getInstance(mController.getTimeZone(), mController.getLocale());
        mCalendar = Calendar.getInstance(mController.getTimeZone(), mController.getLocale());

        mDayOfWeekTypeface = res.getString(R.string.mdtp_day_of_week_label_typeface);
        mMonthTitleTypeface = res.getString(R.string.mdtp_sans_serif);

        boolean darkTheme = mController != null && mController.isThemeDark();
        if (darkTheme) {
            mDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_normal_dark_theme);
            mMonthDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_month_day_dark_theme);
            mDisabledDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_disabled_dark_theme);
            mHighlightedDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_highlighted_dark_theme);
        } else {
            mDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_normal);
            mMonthDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_month_day);
            mDisabledDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_disabled);
            mHighlightedDayTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_highlighted);
        }
        mSelectedDayTextColor = ContextCompat.getColor(context, R.color.mdtp_white);
        mTodayNumberColor = mController.getAccentColor();
        mMonthTitleColor = ContextCompat.getColor(context, R.color.mdtp_white);

        mStringBuilder = new StringBuilder(50);

        MINI_DAY_NUMBER_TEXT_SIZE = res.getDimensionPixelSize(R.dimen.mdtp_day_number_size);
        MONTH_LABEL_TEXT_SIZE = res.getDimensionPixelSize(R.dimen.mdtp_month_label_size);
        MONTH_DAY_LABEL_TEXT_SIZE = res.getDimensionPixelSize(R.dimen.mdtp_month_day_label_text_size);
        MONTH_HEADER_SIZE = res.getDimensionPixelOffset(R.dimen.mdtp_month_list_item_header_height);
        MONTH_HEADER_SIZE_V2 = res.getDimensionPixelOffset(R.dimen.mdtp_month_list_item_header_height_v2);
        DAY_SELECTED_CIRCLE_SIZE = mController.getVersion() == DatePickerDialog.Version.VERSION_1
                ? res.getDimensionPixelSize(R.dimen.mdtp_day_number_select_circle_radius)
                : res.getDimensionPixelSize(R.dimen.mdtp_day_number_select_circle_radius_v2);
        DAY_HIGHLIGHT_CIRCLE_SIZE = res
                .getDimensionPixelSize(R.dimen.mdtp_day_highlight_circle_radius);
        DAY_HIGHLIGHT_CIRCLE_MARGIN = res
                .getDimensionPixelSize(R.dimen.mdtp_day_highlight_circle_margin);

        if (mController.getVersion() == DatePickerDialog.Version.VERSION_1) {
            mRowHeight = (res.getDimensionPixelOffset(R.dimen.mdtp_date_picker_view_animator_height)
                    - getMonthHeaderSize()) / MAX_NUM_ROWS;
        } else {
            mRowHeight = (res.getDimensionPixelOffset(R.dimen.mdtp_date_picker_view_animator_height_v2)
                    - getMonthHeaderSize() - MONTH_DAY_LABEL_TEXT_SIZE * 2) / MAX_NUM_ROWS;
        }

        mEdgePadding = mController.getVersion() == DatePickerDialog.Version.VERSION_1
                ? 0
                : context.getResources().getDimensionPixelSize(R.dimen.mdtp_date_picker_view_animator_padding_v2);

        // Set up accessibility components.
        mTouchHelper = getMonthViewTouchHelper();
        ViewCompat.setAccessibilityDelegate(this, mTouchHelper);
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        mLockAccessibilityDelegate = true;

        // Sets up any standard paints that will be used
        initView();
    }

    protected MonthViewTouchHelper getMonthViewTouchHelper() {
        return new MonthViewTouchHelper(this);
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        // Workaround for a JB MR1 issue where accessibility delegates on
        // top-level ListView items are overwritten.
        if (!mLockAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        }
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        mOnDayClickListener = listener;
    }

    @Override
    public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        // First right-of-refusal goes the touch exploration helper.
        return mTouchHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                final int day = getDayFromLocation(event.getX(), event.getY());
                if (day >= 0) {
                    onDayClick(day);
                }
                break;
        }
        return true;
    }

    /**
     * Sets up the text and style properties for painting. Override this if you
     * want to use a different paint.
     */
    protected void initView() {
        mMonthTitlePaint = new Paint();
        if (mController.getVersion() == DatePickerDialog.Version.VERSION_1)
            mMonthTitlePaint.setFakeBoldText(true);
        mMonthTitlePaint.setAntiAlias(true);
        mMonthTitlePaint.setTextSize(MONTH_LABEL_TEXT_SIZE);
        mMonthTitlePaint.setTypeface(Typeface.create(mMonthTitleTypeface, Typeface.BOLD));
        mMonthTitlePaint.setColor(mDayTextColor);
        mMonthTitlePaint.setTextAlign(Align.CENTER);
        mMonthTitlePaint.setStyle(Style.FILL);

        mSelectedCirclePaint = new Paint();
        mSelectedCirclePaint.setFakeBoldText(true);
        mSelectedCirclePaint.setAntiAlias(true);
        mSelectedCirclePaint.setColor(mTodayNumberColor);
        mSelectedCirclePaint.setTextAlign(Align.CENTER);
        mSelectedCirclePaint.setStyle(Style.FILL);
        mSelectedCirclePaint.setAlpha(SELECTED_CIRCLE_ALPHA);

        mMonthDayLabelPaint = new Paint();
        mMonthDayLabelPaint.setAntiAlias(true);
        mMonthDayLabelPaint.setTextSize(MONTH_DAY_LABEL_TEXT_SIZE);
        mMonthDayLabelPaint.setColor(mMonthDayTextColor);
        mMonthTitlePaint.setTypeface(Typeface.create(mDayOfWeekTypeface, Typeface.BOLD));
        mMonthDayLabelPaint.setStyle(Style.FILL);
        mMonthDayLabelPaint.setTextAlign(Align.CENTER);
        mMonthDayLabelPaint.setFakeBoldText(true);

        mMonthNumPaint = new Paint();
        mMonthNumPaint.setAntiAlias(true);
        mMonthNumPaint.setTextSize(MINI_DAY_NUMBER_TEXT_SIZE);
        mMonthNumPaint.setStyle(Style.FILL);
        mMonthNumPaint.setTextAlign(Align.CENTER);
        mMonthNumPaint.setFakeBoldText(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawMonthTitle(canvas);
        drawMonthDayLabels(canvas);
        drawMonthNums(canvas);
    }

    private int mDayOfWeekStart = 0;

    /**
     * Sets all the parameters for displaying this week. The only required
     * parameter is the week number. Other parameters have a default value and
     * will only update if a new value is included, except for focus month,
     * which will always default to no focus month if no value is passed in.
     */
    public void setMonthParams(int selectedDay, int year, int month, int weekStart) {
        if (month == -1 && year == -1) {
            throw new InvalidParameterException("You must specify month and year for this view");
        }

        mSelectedDay = selectedDay;

        // Allocate space for caching the day numbers and focus values
        mMonth = month;
        mYear = year;

        // Figure out what day today is
        //final Time today = new Time(Time.getCurrentTimezone());
        //today.setToNow();
        final Calendar today = Calendar.getInstance(mController.getTimeZone(), mController.getLocale());
        mHasToday = false;
        mToday = -1;

        mCalendar.set(Calendar.MONTH, mMonth);
        mCalendar.set(Calendar.YEAR, mYear);
        mCalendar.set(Calendar.DAY_OF_MONTH, 1);
        mDayOfWeekStart = mCalendar.get(Calendar.DAY_OF_WEEK);

        if (weekStart != -1) {
            mWeekStart = weekStart;
        } else {
            mWeekStart = mCalendar.getFirstDayOfWeek();
        }

        mNumCells = mCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < mNumCells; i++) {
            final int day = i + 1;
            if (sameDay(day, today)) {
                mHasToday = true;
                mToday = day;
            }
        }
        mNumRows = calculateNumRows();

        // Invalidate cached accessibility information.
        mTouchHelper.invalidateRoot();
    }

    @SuppressWarnings("unused")
    public void setSelectedDay(int day) {
        mSelectedDay = day;
    }

    private int calculateNumRows() {
        int offset = findDayOffset();
        int dividend = (offset + mNumCells) / mNumDays;
        int remainder = (offset + mNumCells) % mNumDays;
        return (dividend + (remainder > 0 ? 1 : 0));
    }

    private boolean sameDay(int day, Calendar today) {
        return mYear == today.get(Calendar.YEAR) &&
                mMonth == today.get(Calendar.MONTH) &&
                day == today.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mRowHeight * mNumRows + getMonthHeaderSize());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;

        // Invalidate cached accessibility information.
        mTouchHelper.invalidateRoot();
    }

    public int getMonth() {
        return mMonth;
    }

    public int getYear() {
        return mYear;
    }

    /**
     * @return The height in pixels of a row of day labels
     */
    public int getMonthHeight() {
        int scaleFactor = mController.getVersion() == DatePickerDialog.Version.VERSION_1 ? 2 : 3;
        return getMonthHeaderSize() - MONTH_DAY_LABEL_TEXT_SIZE * scaleFactor;
    }

    /**
     * @return The width in pixels of a day label
     */
    public int getCellWidth() {
        return (mWidth - mEdgePadding * 2) / mNumDays;
    }

    /**
     * @return The left / right padding used when calculating day number positions
     */
    public int getEdgePadding() {
        return mEdgePadding;
    }

    /**
     * A wrapper to the MonthHeaderSize to allow override it in children
     */
    protected int getMonthHeaderSize() {
        return mController.getVersion() == DatePickerDialog.Version.VERSION_1
                ? MONTH_HEADER_SIZE
                : MONTH_HEADER_SIZE_V2;
    }

    @NonNull
    private String getMonthAndYearString() {
        Locale locale = mController.getLocale();
        String pattern = "MMMM yyyy";

        if (Build.VERSION.SDK_INT < 18) pattern = getContext().getResources().getString(R.string.mdtp_date_v1_monthyear);
        else pattern = DateFormat.getBestDateTimePattern(locale, pattern);

        SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
        formatter.setTimeZone(mController.getTimeZone());
        formatter.applyLocalizedPattern(pattern);
        mStringBuilder.setLength(0);
        return formatter.format(mCalendar.getTime());
    }

    protected void drawMonthTitle(Canvas canvas) {
        int x = mWidth / 2;
        int y = mController.getVersion() == DatePickerDialog.Version.VERSION_1
                ? (getMonthHeaderSize() - MONTH_DAY_LABEL_TEXT_SIZE) / 2
                : getMonthHeaderSize() / 2 - MONTH_DAY_LABEL_TEXT_SIZE;
        canvas.drawText(getMonthAndYearString(), x, y, mMonthTitlePaint);
    }

    protected void drawMonthDayLabels(Canvas canvas) {
        int y = getMonthHeaderSize() - (MONTH_DAY_LABEL_TEXT_SIZE / 2);
        int dayWidthHalf = (mWidth - mEdgePadding * 2) / (mNumDays * 2);

        for (int i = 0; i < mNumDays; i++) {
            int x = (2 * i + 1) * dayWidthHalf + mEdgePadding;

            int calendarDay = (i + mWeekStart) % mNumDays;
            mDayLabelCalendar.set(Calendar.DAY_OF_WEEK, calendarDay);
            String weekString = getWeekDayLabel(mDayLabelCalendar);
            canvas.drawText(weekString, x, y, mMonthDayLabelPaint);
        }
    }

    /**
     * Draws the week and month day numbers for this week. Override this method
     * if you need different placement.
     *
     * @param canvas The canvas to draw on
     */
    protected void drawMonthNums(Canvas canvas) {
        int y = (((mRowHeight + MINI_DAY_NUMBER_TEXT_SIZE) / 2) - DAY_SEPARATOR_WIDTH)
                + getMonthHeaderSize();
        // TODO: look at the calculations used by the framework picker to properly align this with the buttons
        final int dayWidthHalf = (mWidth - mEdgePadding * 2) / (mNumDays * 2);
        int j = findDayOffset();
        for (int dayNumber = 1; dayNumber <= mNumCells; dayNumber++) {
            final int x = (2 * j + 1) * dayWidthHalf + mEdgePadding;

            int yRelativeToDay = (mRowHeight + MINI_DAY_NUMBER_TEXT_SIZE) / 2 - DAY_SEPARATOR_WIDTH;

            final int startX = x - dayWidthHalf;
            final int stopX = x + dayWidthHalf;
            final int startY = y - yRelativeToDay;
            final int stopY = startY + mRowHeight;

            drawMonthDay(canvas, mYear, mMonth, dayNumber, x, y, startX, stopX, startY, stopY);

            j++;
            if (j == mNumDays) {
                j = 0;
                y += mRowHeight;
            }
        }
    }

    /**
     * This method should draw the month day.  Implemented by sub-classes to allow customization.
     *
     * @param canvas The canvas to draw on
     * @param year   The year of this month day
     * @param month  The month of this month day
     * @param day    The day number of this month day
     * @param x      The default x position to draw the day number
     * @param y      The default y position to draw the day number
     * @param startX The left boundary of the day number rect
     * @param stopX  The right boundary of the day number rect
     * @param startY The top boundary of the day number rect
     * @param stopY  The bottom boundary of the day number rect
     */
    public abstract void drawMonthDay(Canvas canvas, int year, int month, int day,
                                      int x, int y, int startX, int stopX, int startY, int stopY);

    protected int findDayOffset() {
        return (mDayOfWeekStart < mWeekStart ? (mDayOfWeekStart + mNumDays) : mDayOfWeekStart)
                - mWeekStart;
    }


    /**
     * Calculates the day that the given x position is in, accounting for week
     * number. Returns the day or -1 if the position wasn't in a day.
     *
     * @param x The x position of the touch event
     * @return The day number, or -1 if the position wasn't in a day
     */
    public int getDayFromLocation(float x, float y) {
        final int day = getInternalDayFromLocation(x, y);
        if (day < 1 || day > mNumCells) {
            return -1;
        }
        return day;
    }

    /**
     * Calculates the day that the given x position is in, accounting for week
     * number.
     *
     * @param x The x position of the touch event
     * @return The day number
     */
    protected int getInternalDayFromLocation(float x, float y) {
        int dayStart = mEdgePadding;
        if (x < dayStart || x > mWidth - mEdgePadding) {
            return -1;
        }
        // Selection is (x - start) / (pixels/day) == (x -s) * day / pixels
        int row = (int) (y - getMonthHeaderSize()) / mRowHeight;
        int column = (int) ((x - dayStart) * mNumDays / (mWidth - dayStart - mEdgePadding));

        int day = column - findDayOffset() + 1;
        day += row * mNumDays;
        return day;
    }

    /**
     * Called when the user clicks on a day. Handles callbacks to the
     * {@link OnDayClickListener} if one is set.
     * <p/>
     * If the day is out of the range set by minDate and/or maxDate, this is a no-op.
     *
     * @param day The day that was clicked
     */
    private void onDayClick(int day) {
        // If the min / max date are set, only process the click if it's a valid selection.
        if (mController.isOutOfRange(mYear, mMonth, day)) {
            return;
        }


        if (mOnDayClickListener != null) {
            mOnDayClickListener.onDayClick(this, new CalendarDay(mYear, mMonth, day, mController.getTimeZone()));
        }

        // This is a no-op if accessibility is turned off.
        mTouchHelper.sendEventForVirtualView(day, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    /**
     * @param year as an int
     * @param month as an int
     * @param day as an int
     * @return true if the given date should be highlighted
     */
    protected boolean isHighlighted(int year, int month, int day) {
        return mController.isHighlighted(year, month, day);
    }

    /**
     * Return a 1 or 2 letter String for use as a weekday label
     *
     * @param day The day for which to generate a label
     * @return The weekday label
     */
    private String getWeekDayLabel(Calendar day) {
        Locale locale = mController.getLocale();

        // Localised short version of the string is not available on API < 18
        if (Build.VERSION.SDK_INT < 18) {
            String dayName = new SimpleDateFormat("E", locale).format(day.getTime());
            String dayLabel = dayName.toUpperCase(locale).substring(0, 1);

            // Chinese labels should be fetched right to left
            if (locale.equals(Locale.CHINA) || locale.equals(Locale.CHINESE) || locale.equals(Locale.SIMPLIFIED_CHINESE) || locale.equals(Locale.TRADITIONAL_CHINESE)) {
                int len = dayName.length();
                dayLabel = dayName.substring(len - 1, len);
            }

            // Most hebrew labels should select the second to last character
            if (locale.getLanguage().equals("he") || locale.getLanguage().equals("iw")) {
                if (mDayLabelCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                    int len = dayName.length();
                    dayLabel = dayName.substring(len - 2, len - 1);
                } else {
                    // I know this is duplication, but it makes the code easier to grok by
                    // having all hebrew code in the same block
                    dayLabel = dayName.toUpperCase(locale).substring(0, 1);
                }
            }

            // Catalan labels should be two digits in lowercase
            if (locale.getLanguage().equals("ca"))
                dayLabel = dayName.toLowerCase().substring(0, 2);

            // Correct single character label in Spanish is X
            if (locale.getLanguage().equals("es") && day.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY)
                dayLabel = "X";

            return dayLabel;
        }
        // Getting the short label is a one liner on API >= 18
        if (weekDayLabelFormatter == null) {
            weekDayLabelFormatter = new SimpleDateFormat("EEEEE", locale);
        }
        return weekDayLabelFormatter.format(day.getTime());
    }

    /**
     * @return The date that has accessibility focus, or {@code null} if no date
     * has focus
     */
    public CalendarDay getAccessibilityFocus() {
        final int day = mTouchHelper.getAccessibilityFocusedVirtualViewId();
        if (day >= 0) {
            return new CalendarDay(mYear, mMonth, day, mController.getTimeZone());
        }
        return null;
    }

    /**
     * Clears accessibility focus within the view. No-op if the view does not
     * contain accessibility focus.
     */
    public void clearAccessibilityFocus() {
        mTouchHelper.clearFocusedVirtualView();
    }

    /**
     * Attempts to restore accessibility focus to the specified date.
     *
     * @param day The date which should receive focus
     * @return {@code false} if the date is not valid for this month view, or
     * {@code true} if the date received focus
     */
    public boolean restoreAccessibilityFocus(CalendarDay day) {
        if ((day.year != mYear) || (day.month != mMonth) || (day.day > mNumCells)) {
            return false;
        }
        mTouchHelper.setFocusedVirtualView(day.day);
        return true;
    }

    /**
     * Provides a virtual view hierarchy for interfacing with an accessibility
     * service.
     */
    protected class MonthViewTouchHelper extends ExploreByTouchHelper {
        private static final String DATE_FORMAT = "dd MMMM yyyy";

        private final Rect mTempRect = new Rect();
        private final Calendar mTempCalendar = Calendar.getInstance(mController.getTimeZone());

        MonthViewTouchHelper(View host) {
            super(host);
        }

        void setFocusedVirtualView(int virtualViewId) {
            getAccessibilityNodeProvider(MonthView.this).performAction(
                    virtualViewId, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null);
        }

        void clearFocusedVirtualView() {
            final int focusedVirtualView = getAccessibilityFocusedVirtualViewId();
            if (focusedVirtualView != ExploreByTouchHelper.INVALID_ID) {
                getAccessibilityNodeProvider(MonthView.this).performAction(
                        focusedVirtualView,
                        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                        null);
            }
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            final int day = getDayFromLocation(x, y);
            if (day >= 0) {
                return day;
            }
            return ExploreByTouchHelper.INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            for (int day = 1; day <= mNumCells; day++) {
                virtualViewIds.add(day);
            }
        }

        @Override
        protected void onPopulateEventForVirtualView(int virtualViewId, @NonNull AccessibilityEvent event) {
            event.setContentDescription(getItemDescription(virtualViewId));
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId,
                                                    @NonNull AccessibilityNodeInfoCompat node) {
            getItemBounds(virtualViewId, mTempRect);

            node.setContentDescription(getItemDescription(virtualViewId));
            node.setBoundsInParent(mTempRect);
            node.addAction(AccessibilityNodeInfo.ACTION_CLICK);

            // Flag non-selectable dates as disabled
            node.setEnabled(!mController.isOutOfRange(mYear, mMonth, virtualViewId));

            if (virtualViewId == mSelectedDay) {
                node.setSelected(true);
            }

        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action,
                                                        Bundle arguments) {
            switch (action) {
                case AccessibilityNodeInfo.ACTION_CLICK:
                    onDayClick(virtualViewId);
                    return true;
            }

            return false;
        }

        /**
         * Calculates the bounding rectangle of a given time object.
         *
         * @param day  The day to calculate bounds for
         * @param rect The rectangle in which to store the bounds
         */
        void getItemBounds(int day, Rect rect) {
            final int offsetX = mEdgePadding;
            final int offsetY = getMonthHeaderSize();
            final int cellHeight = mRowHeight;
            final int cellWidth = ((mWidth - (2 * mEdgePadding)) / mNumDays);
            final int index = ((day - 1) + findDayOffset());
            final int row = (index / mNumDays);
            final int column = (index % mNumDays);
            final int x = (offsetX + (column * cellWidth));
            final int y = (offsetY + (row * cellHeight));

            rect.set(x, y, (x + cellWidth), (y + cellHeight));
        }

        /**
         * Generates a description for a given time object. Since this
         * description will be spoken, the components are ordered by descending
         * specificity as DAY MONTH YEAR.
         *
         * @param day The day to generate a description for
         * @return A description of the time object
         */
        CharSequence getItemDescription(int day) {
            mTempCalendar.set(mYear, mMonth, day);
            return DateFormat.format(DATE_FORMAT, mTempCalendar.getTimeInMillis());
        }
    }

    /**
     * Handles callbacks when the user clicks on a time object.
     */
    public interface OnDayClickListener {
        void onDayClick(MonthView view, CalendarDay day);
    }
}
