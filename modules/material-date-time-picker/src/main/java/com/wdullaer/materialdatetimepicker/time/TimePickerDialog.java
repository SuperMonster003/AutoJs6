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
 * limitations under the License
 */

package com.wdullaer.materialdatetimepicker.time;

import android.animation.ObjectAnimator;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.wdullaer.materialdatetimepicker.HapticFeedbackController;
import com.wdullaer.materialdatetimepicker.R;
import com.wdullaer.materialdatetimepicker.Utils;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout.OnValueSelectedListener;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Dialog to set a time.
 */
public class TimePickerDialog extends AppCompatDialogFragment implements OnValueSelectedListener, TimePickerController {
    private static final String TAG = "TimePickerDialog";

    public enum Version {
        VERSION_1,
        VERSION_2
    }

    private static final String KEY_INITIAL_TIME = "initial_time";
    private static final String KEY_IS_24_HOUR_VIEW = "is_24_hour_view";
    private static final String KEY_TITLE = "dialog_title";
    private static final String KEY_CURRENT_ITEM_SHOWING = "current_item_showing";
    private static final String KEY_IN_KB_MODE = "in_kb_mode";
    private static final String KEY_TYPED_TIMES = "typed_times";
    private static final String KEY_THEME_DARK = "theme_dark";
    private static final String KEY_THEME_DARK_CHANGED = "theme_dark_changed";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_VIBRATE = "vibrate";
    private static final String KEY_DISMISS = "dismiss";
    private static final String KEY_ENABLE_SECONDS = "enable_seconds";
    private static final String KEY_ENABLE_MINUTES = "enable_minutes";
    private static final String KEY_OK_RESID = "ok_resid";
    private static final String KEY_OK_STRING = "ok_string";
    private static final String KEY_OK_COLOR = "ok_color";
    private static final String KEY_CANCEL_RESID = "cancel_resid";
    private static final String KEY_CANCEL_STRING = "cancel_string";
    private static final String KEY_CANCEL_COLOR = "cancel_color";
    private static final String KEY_VERSION = "version";
    private static final String KEY_TIMEPOINTLIMITER = "timepoint_limiter";
    private static final String KEY_LOCALE = "locale";

    public static final int HOUR_INDEX = 0;
    public static final int MINUTE_INDEX = 1;
    public static final int SECOND_INDEX = 2;
    public static final int AM = 0;
    public static final int PM = 1;

    // Delay before starting the pulse animation, in ms.
    private static final int PULSE_ANIMATOR_DELAY = 300;

    private OnTimeSetListener mCallback;
    private DialogInterface.OnCancelListener mOnCancelListener;
    private DialogInterface.OnDismissListener mOnDismissListener;

    private HapticFeedbackController mHapticFeedbackController;

    private Button mCancelButton;
    private Button mOkButton;
    private TextView mHourView;
    private TextView mHourSpaceView;
    private TextView mMinuteView;
    private TextView mMinuteSpaceView;
    private TextView mSecondView;
    private TextView mSecondSpaceView;
    private TextView mAmTextView;
    private TextView mPmTextView;
    private View mAmPmLayout;
    private RadialPickerLayout mTimePicker;

    private int mSelectedColor;
    private int mUnselectedColor;
    private String mAmText;
    private String mPmText;

    private boolean mAllowAutoAdvance;
    private Timepoint mInitialTime;
    private boolean mIs24HourMode;
    private String mTitle;
    private boolean mThemeDark;
    private boolean mThemeDarkChanged;
    private boolean mVibrate;
    private Integer mAccentColor = null;
    private boolean mDismissOnPause;
    private boolean mEnableSeconds;
    private boolean mEnableMinutes;
    private int mOkResid;
    private String mOkString;
    private Integer mOkColor = null;
    private int mCancelResid;
    private String mCancelString;
    private Integer mCancelColor = null;
    private Version mVersion;
    private DefaultTimepointLimiter mDefaultLimiter = new DefaultTimepointLimiter();
    private TimepointLimiter mLimiter = mDefaultLimiter;
    private Locale mLocale = Locale.getDefault();

    // For hardware IME input.
    private char mPlaceholderText;
    private String mDoublePlaceholderText;
    private String mDeletedKeyFormat;
    private boolean mInKbMode;
    private ArrayList<Integer> mTypedTimes;
    private Node mLegalTimesTree;
    private int mAmKeyCode;
    private int mPmKeyCode;

    // Accessibility strings.
    private String mHourPickerDescription;
    private String mSelectHours;
    private String mMinutePickerDescription;
    private String mSelectMinutes;
    private String mSecondPickerDescription;
    private String mSelectSeconds;

    /**
     * The callback interface used to indicate the user is done filling in
     * the time (they clicked on the 'Set' button).
     */
    public interface OnTimeSetListener {

        /**
         * @param view      The view associated with this listener.
         * @param hourOfDay The hour that was set.
         * @param minute    The minute that was set.
         * @param second    The second that was set
         */
        void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second);
    }

    public TimePickerDialog() {
        // Empty constructor required for dialog fragment.
    }

    /**
     * Create a new TimePickerDialog instance with a given intial selection
     *
     * @param callback     How the parent is notified that the time is set.
     * @param hourOfDay    The initial hour of the dialog.
     * @param minute       The initial minute of the dialog.
     * @param second       The initial second of the dialog.
     * @param is24HourMode True to render 24 hour mode, false to render AM / PM selectors.
     * @return a new TimePickerDialog instance.
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public static TimePickerDialog newInstance(OnTimeSetListener callback,
                                               int hourOfDay, int minute, int second, boolean is24HourMode) {
        TimePickerDialog ret = new TimePickerDialog();
        ret.initialize(callback, hourOfDay, minute, second, is24HourMode);
        return ret;
    }

    /**
     * Create a new TimePickerDialog instance with a given initial selection
     *
     * @param callback     How the parent is notified that the time is set.
     * @param hourOfDay    The initial hour of the dialog.
     * @param minute       The initial minute of the dialog.
     * @param is24HourMode True to render 24 hour mode, false to render AM / PM selectors.
     * @return a new TimePickerDialog instance.
     */
    public static TimePickerDialog newInstance(OnTimeSetListener callback,
                                               int hourOfDay, int minute, boolean is24HourMode) {
        return TimePickerDialog.newInstance(callback, hourOfDay, minute, 0, is24HourMode);
    }

    /**
     * Create a new TimePickerDialog instance initialized to the current system time
     *
     * @param callback     How the parent is notified that the time is set.
     * @param is24HourMode True to render 24 hour mode, false to render AM / PM selectors.
     * @return a new TimePickerDialog instance.
     */
    @SuppressWarnings({"unused", "SameParameterValue", "WeakerAccess"})
    public static TimePickerDialog newInstance(OnTimeSetListener callback, boolean is24HourMode) {
        Calendar now = Calendar.getInstance();
        return TimePickerDialog.newInstance(callback, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), is24HourMode);
    }

    public void initialize(OnTimeSetListener callback,
                           int hourOfDay, int minute, int second, boolean is24HourMode) {
        mCallback = callback;

        mInitialTime = new Timepoint(hourOfDay, minute, second);
        mIs24HourMode = is24HourMode;
        mInKbMode = false;
        mTitle = "";
        mThemeDark = false;
        mThemeDarkChanged = false;
        mVibrate = true;
        mDismissOnPause = false;
        mEnableSeconds = false;
        mEnableMinutes = true;
        mOkResid = R.string.mdtp_ok;
        mCancelResid = R.string.mdtp_cancel;
        mVersion = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? Version.VERSION_1 : Version.VERSION_2;
        // Throw away the current TimePicker, which might contain old state if the dialog instance is reused
        mTimePicker = null;
    }

    /**
     * Set a title. NOTE: this will only take effect with the next onCreateView
     */
    public void setTitle(String title) {
        mTitle = title;
    }

    @SuppressWarnings("unused")
    public String getTitle() {
        return mTitle;
    }

    /**
     * Set a dark or light theme. NOTE: this will only take effect for the next onCreateView.
     */
    public void setThemeDark(boolean dark) {
        mThemeDark = dark;
        mThemeDarkChanged = true;
    }

    /**
     * Set the accent color of this dialog
     *
     * @param color the accent color you want
     */
    @SuppressWarnings("unused")
    public void setAccentColor(String color) {
        mAccentColor = Color.parseColor(color);
    }

    /**
     * Set the accent color of this dialog
     *
     * @param color the accent color you want
     */
    public void setAccentColor(@ColorInt int color) {
        mAccentColor = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Set the text color of the OK button
     *
     * @param color the color you want
     */
    @SuppressWarnings("unused")
    public void setOkColor(String color) {
        mOkColor = Color.parseColor(color);
    }

    /**
     * Set the text color of the OK button
     *
     * @param color the color you want
     */
    @SuppressWarnings("unused")
    public void setOkColor(@ColorInt int color) {
        mOkColor = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Set the text color of the Cancel button
     *
     * @param color the color you want
     */
    @SuppressWarnings("unused")
    public void setCancelColor(String color) {
        mCancelColor = Color.parseColor(color);
    }

    /**
     * Set the text color of the Cancel button
     *
     * @param color the color you want
     */
    @SuppressWarnings("unused")
    public void setCancelColor(@ColorInt int color) {
        mCancelColor = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public boolean isThemeDark() {
        return mThemeDark;
    }

    @Override
    public boolean is24HourMode() {
        return mIs24HourMode;
    }

    @Override
    public int getAccentColor() {
        return mAccentColor;
    }

    /**
     * Set whether the device should vibrate when touching fields
     *
     * @param vibrate true if the device should vibrate when touching a field
     */
    public void vibrate(boolean vibrate) {
        mVibrate = vibrate;
    }

    /**
     * Set whether the picker should dismiss itself when it's pausing or whether it should try to survive an orientation change
     *
     * @param dismissOnPause true if the picker should dismiss itself
     */
    public void dismissOnPause(boolean dismissOnPause) {
        mDismissOnPause = dismissOnPause;
    }

    /**
     * Set whether an additional picker for seconds should be shown
     * Will enable minutes picker as well if seconds picker should be shown
     *
     * @param enableSeconds true if the seconds picker should be shown
     */
    public void enableSeconds(boolean enableSeconds) {
        if (enableSeconds) mEnableMinutes = true;
        mEnableSeconds = enableSeconds;
    }

    /**
     * Set whether the picker for minutes should be shown
     * Will disable seconds if minutes are disbled
     *
     * @param enableMinutes true if minutes picker should be shown
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void enableMinutes(boolean enableMinutes) {
        if (!enableMinutes) mEnableSeconds = false;
        mEnableMinutes = enableMinutes;
    }

    @SuppressWarnings("unused")
    public void setMinTime(int hour, int minute, int second) {
        setMinTime(new Timepoint(hour, minute, second));
    }

    @SuppressWarnings("WeakerAccess")
    public void setMinTime(Timepoint minTime) {
        mDefaultLimiter.setMinTime(minTime);
    }

    @SuppressWarnings("unused")
    public void setMaxTime(int hour, int minute, int second) {
        setMaxTime(new Timepoint(hour, minute, second));
    }

    @SuppressWarnings("WeakerAccess")
    public void setMaxTime(Timepoint maxTime) {
        mDefaultLimiter.setMaxTime(maxTime);
    }

    /**
     * Pass in an array of Timepoints which are the only possible selections.
     * Try to specify Timepoints only up to the resolution of your picker (i.e. do not add seconds
     * if the resolution of the picker is minutes)
     *
     * @param selectableTimes Array of Timepoints which are the only valid selections in the picker
     */
    @SuppressWarnings("WeakerAccess")
    public void setSelectableTimes(Timepoint[] selectableTimes) {
        mDefaultLimiter.setSelectableTimes(selectableTimes);
    }

    /**
     * Pass in an array of Timepoints that cannot be selected. These take precedence over
     * {@link TimePickerDialog#setSelectableTimes(Timepoint[])}
     * Be careful when using this without selectableTimes: rounding to a valid Timepoint is a
     * very expensive operation if a lot of consecutive Timepoints are disabled
     * Try to specify Timepoints only up to the resolution of your picker (i.e. do not add seconds
     * if the resolution of the picker is minutes)
     *
     * @param disabledTimes Array of Timepoints which are disabled in the resulting picker
     */
    public void setDisabledTimes(Timepoint[] disabledTimes) {
        mDefaultLimiter.setDisabledTimes(disabledTimes);
    }

    /**
     * Set the interval for selectable times in the TimePickerDialog
     * This is a convenience wrapper around {@link TimePickerDialog#setSelectableTimes(Timepoint[])}
     * The interval for all three time components can be set independently
     * If you are not using the seconds / minutes picker, set the respective item to 60 for
     * better performance.
     *
     * @param hourInterval   The interval between 2 selectable hours ([1,24])
     * @param minuteInterval The interval between 2 selectable minutes ([1,60])
     * @param secondInterval The interval between 2 selectable seconds ([1,60])
     */
    public void setTimeInterval(@IntRange(from = 1, to = 24) int hourInterval,
                                @IntRange(from = 1, to = 60) int minuteInterval,
                                @IntRange(from = 1, to = 60) int secondInterval) {
        List<Timepoint> timepoints = new ArrayList<>();

        int hour = 0;
        while (hour < 24) {
            int minute = 0;
            while (minute < 60) {
                int second = 0;
                while (second < 60) {
                    timepoints.add(new Timepoint(hour, minute, second));
                    second += secondInterval;
                }
                minute += minuteInterval;
            }
            hour += hourInterval;
        }
        setSelectableTimes(timepoints.toArray(new Timepoint[timepoints.size()]));
    }

    /**
     * Set the interval for selectable times in the TimePickerDialog
     * This is a convenience wrapper around setSelectableTimes
     * The interval for all three time components can be set independently
     * If you are not using the seconds / minutes picker, set the respective item to 60 for
     * better performance.
     *
     * @param hourInterval   The interval between 2 selectable hours ([1,24])
     * @param minuteInterval The interval between 2 selectable minutes ([1,60])
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public void setTimeInterval(@IntRange(from = 1, to = 24) int hourInterval,
                                @IntRange(from = 1, to = 60) int minuteInterval) {
        setTimeInterval(hourInterval, minuteInterval, 60);
    }

    /**
     * Set the interval for selectable times in the TimePickerDialog
     * This is a convenience wrapper around setSelectableTimes
     * The interval for all three time components can be set independently
     * If you are not using the seconds / minutes picker, set the respective item to 60 for
     * better performance.
     *
     * @param hourInterval The interval between 2 selectable hours ([1,24])
     */
    @SuppressWarnings("unused")
    public void setTimeInterval(@IntRange(from = 1, to = 24) int hourInterval) {
        setTimeInterval(hourInterval, 60);
    }

    public void setOnTimeSetListener(OnTimeSetListener callback) {
        mCallback = callback;
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
        mOnCancelListener = onCancelListener;
    }

    @SuppressWarnings("unused")
    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        mOnDismissListener = onDismissListener;
    }

    /**
     * Set the time that will be shown when the picker opens for the first time
     * Overrides the value given in newInstance()
     *
     * @param hourOfDay the hour of the day
     * @param minute    the minute of the hour
     * @param second    the second of the minute
     * @deprecated in favor of {@link #setInitialSelection(int, int, int)}
     */
    @Deprecated
    public void setStartTime(int hourOfDay, int minute, int second) {
        mInitialTime = roundToNearest(new Timepoint(hourOfDay, minute, second));
        mInKbMode = false;
    }

    /**
     * Set the time that will be shown when the picker opens for the first time
     * Overrides the value given in newInstance
     *
     * @param hourOfDay the hour of the day
     * @param minute    the minute of the hour
     * @deprecated in favor of {@link #setInitialSelection(int, int)}
     */
    @SuppressWarnings({"unused", "deprecation"})
    @Deprecated
    public void setStartTime(int hourOfDay, int minute) {
        setStartTime(hourOfDay, minute, 0);
    }

    /**
     * Set the time that will be shown when the picker opens for the first time
     * Overrides the value given in newInstance()
     *
     * @param hourOfDay the hour of the day
     * @param minute    the minute of the hour
     * @param second    the second of the minute
     */
    @SuppressWarnings("WeakerAccess")
    public void setInitialSelection(int hourOfDay, int minute, int second) {
        setInitialSelection(new Timepoint(hourOfDay, minute, second));
    }

    /**
     * Set the time that will be shown when the picker opens for the first time
     * Overrides the value given in newInstance
     *
     * @param hourOfDay the hour of the day
     * @param minute    the minute of the hour
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setInitialSelection(int hourOfDay, int minute) {
        setInitialSelection(hourOfDay, minute, 0);
    }

    /**
     * Set the time that will be shown when the picker opens for the first time
     * Overrides the value given in newInstance()
     *
     * @param time the Timepoint selected when the Dialog opens
     */
    @SuppressWarnings("WeakerAccess")
    public void setInitialSelection(Timepoint time) {
        mInitialTime = roundToNearest(time);
        mInKbMode = false;
    }

    /**
     * Set the label for the Ok button (max 12 characters)
     *
     * @param okString A literal String to be used as the Ok button label
     */
    @SuppressWarnings("unused")
    public void setOkText(String okString) {
        mOkString = okString;
    }

    /**
     * Set the label for the Ok button (max 12 characters)
     *
     * @param okResid A resource ID to be used as the Ok button label
     */
    @SuppressWarnings("unused")
    public void setOkText(@StringRes int okResid) {
        mOkString = null;
        mOkResid = okResid;
    }

    /**
     * Set the label for the Cancel button (max 12 characters)
     *
     * @param cancelString A literal String to be used as the Cancel button label
     */
    @SuppressWarnings("unused")
    public void setCancelText(String cancelString) {
        mCancelString = cancelString;
    }

    /**
     * Set the label for the Cancel button (max 12 characters)
     *
     * @param cancelResid A resource ID to be used as the Cancel button label
     */
    @SuppressWarnings("unused")
    public void setCancelText(@StringRes int cancelResid) {
        mCancelString = null;
        mCancelResid = cancelResid;
    }

    /**
     * Set which layout version the picker should use
     *
     * @param version The version to use
     */
    public void setVersion(Version version) {
        mVersion = version;
    }

    /**
     * Pass in a custom implementation of TimeLimiter
     * Disables setSelectableTimes, setDisabledTimes, setTimeInterval, setMinTime and setMaxTime
     *
     * @param limiter A custom implementation of TimeLimiter
     */
    @SuppressWarnings("unused")
    public void setTimepointLimiter(TimepointLimiter limiter) {
        mLimiter = limiter;
    }

    @Override
    public Version getVersion() {
        return mVersion;
    }

    /**
     * Get a reference to the OnTimeSetListener callback
     *
     * @return OnTimeSetListener the callback
     */
    @SuppressWarnings("unused")
    public OnTimeSetListener getOnTimeSetListener() {
        return mCallback;
    }

    /**
     * Set the Locale which will be used to generate various strings throughout the picker
     *
     * @param locale Locale
     */
    @SuppressWarnings("unused")
    public void setLocale(Locale locale) {
        mLocale = locale;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(AppCompatDialogFragment.STYLE_NO_TITLE, 0);
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_INITIAL_TIME)
            && savedInstanceState.containsKey(KEY_IS_24_HOUR_VIEW)) {
            mInitialTime = savedInstanceState.getParcelable(KEY_INITIAL_TIME);
            mIs24HourMode = savedInstanceState.getBoolean(KEY_IS_24_HOUR_VIEW);
            mInKbMode = savedInstanceState.getBoolean(KEY_IN_KB_MODE);
            mTitle = savedInstanceState.getString(KEY_TITLE);
            mThemeDark = savedInstanceState.getBoolean(KEY_THEME_DARK);
            mThemeDarkChanged = savedInstanceState.getBoolean(KEY_THEME_DARK_CHANGED);
            if (savedInstanceState.containsKey(KEY_ACCENT)) mAccentColor = savedInstanceState.getInt(KEY_ACCENT);
            mVibrate = savedInstanceState.getBoolean(KEY_VIBRATE);
            mDismissOnPause = savedInstanceState.getBoolean(KEY_DISMISS);
            mEnableSeconds = savedInstanceState.getBoolean(KEY_ENABLE_SECONDS);
            mEnableMinutes = savedInstanceState.getBoolean(KEY_ENABLE_MINUTES);
            mOkResid = savedInstanceState.getInt(KEY_OK_RESID);
            mOkString = savedInstanceState.getString(KEY_OK_STRING);
            if (savedInstanceState.containsKey(KEY_OK_COLOR)) mOkColor = savedInstanceState.getInt(KEY_OK_COLOR);
            if (mOkColor == Integer.MAX_VALUE) mOkColor = null;
            mCancelResid = savedInstanceState.getInt(KEY_CANCEL_RESID);
            mCancelString = savedInstanceState.getString(KEY_CANCEL_STRING);
            if (savedInstanceState.containsKey(KEY_CANCEL_COLOR)) mCancelColor = savedInstanceState.getInt(KEY_CANCEL_COLOR);
            mVersion = (Version) savedInstanceState.getSerializable(KEY_VERSION);
            mLimiter = savedInstanceState.getParcelable(KEY_TIMEPOINTLIMITER);
            mLocale = (Locale) savedInstanceState.getSerializable(KEY_LOCALE);

            /*
            If the user supplied a custom limiter, we need to create a new default one to prevent
            null pointer exceptions on the configuration methods
            If the user did not supply a custom limiter we need to ensure both mDefaultLimiter
            and mLimiter are the same reference, so that the config methods actually
            affect the behaviour of the picker (in the unlikely event the user reconfigures
            the picker when it is shown)
             */
            mDefaultLimiter = mLimiter instanceof DefaultTimepointLimiter
                    ? (DefaultTimepointLimiter) mLimiter
                    : new DefaultTimepointLimiter();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        int viewRes = mVersion == Version.VERSION_1 ? R.layout.mdtp_time_picker_dialog : R.layout.mdtp_time_picker_dialog_v2;
        View view = inflater.inflate(viewRes, container, false);
        KeyboardListener keyboardListener = new KeyboardListener();
        view.findViewById(R.id.mdtp_time_picker_dialog).setOnKeyListener(keyboardListener);

        // If an accent color has not been set manually, get it from the context
        if (mAccentColor == null) {
            mAccentColor = Utils.getAccentColorFromThemeIfAvailable(getActivity());
        }

        // if theme mode has not been set by java code, check if it is specified in Style.xml
        if (!mThemeDarkChanged) {
            mThemeDark = Utils.isDarkTheme(getActivity(), mThemeDark);
        }

        Resources res = getResources();
        Context context = requireActivity();
        mHourPickerDescription = res.getString(R.string.mdtp_hour_picker_description);
        mSelectHours = res.getString(R.string.mdtp_select_hours);
        mMinutePickerDescription = res.getString(R.string.mdtp_minute_picker_description);
        mSelectMinutes = res.getString(R.string.mdtp_select_minutes);
        mSecondPickerDescription = res.getString(R.string.mdtp_second_picker_description);
        mSelectSeconds = res.getString(R.string.mdtp_select_seconds);
        mSelectedColor = ContextCompat.getColor(context, R.color.mdtp_white);
        mUnselectedColor = ContextCompat.getColor(context, R.color.mdtp_accent_color_focused);

        mHourView = view.findViewById(R.id.mdtp_hours);
        mHourView.setOnKeyListener(keyboardListener);
        mHourSpaceView = view.findViewById(R.id.mdtp_hour_space);
        mMinuteSpaceView = view.findViewById(R.id.mdtp_minutes_space);
        mMinuteView = view.findViewById(R.id.mdtp_minutes);
        mMinuteView.setOnKeyListener(keyboardListener);
        mSecondSpaceView = view.findViewById(R.id.mdtp_seconds_space);
        mSecondView = view.findViewById(R.id.mdtp_seconds);
        mSecondView.setOnKeyListener(keyboardListener);
        mAmTextView = view.findViewById(R.id.mdtp_am_label);
        mAmTextView.setOnKeyListener(keyboardListener);
        mPmTextView = view.findViewById(R.id.mdtp_pm_label);
        mPmTextView.setOnKeyListener(keyboardListener);
        mAmPmLayout = view.findViewById(R.id.mdtp_ampm_layout);
        String[] amPmTexts = new DateFormatSymbols(mLocale).getAmPmStrings();
        mAmText = amPmTexts[0];
        mPmText = amPmTexts[1];

        mHapticFeedbackController = new HapticFeedbackController(getActivity());

        if (mTimePicker != null) {
            mInitialTime = new Timepoint(mTimePicker.getHours(), mTimePicker.getMinutes(), mTimePicker.getSeconds());
        }

        mInitialTime = roundToNearest(mInitialTime);

        mTimePicker = view.findViewById(R.id.mdtp_time_picker);
        mTimePicker.setOnValueSelectedListener(this);
        mTimePicker.setOnKeyListener(keyboardListener);
        mTimePicker.initialize(getActivity(), mLocale, this, mInitialTime, mIs24HourMode);

        int currentItemShowing = HOUR_INDEX;
        if (savedInstanceState != null &&
            savedInstanceState.containsKey(KEY_CURRENT_ITEM_SHOWING)) {
            currentItemShowing = savedInstanceState.getInt(KEY_CURRENT_ITEM_SHOWING);
        }
        setCurrentItemShowing(currentItemShowing, false, true, true);
        mTimePicker.invalidate();

        mHourView.setOnClickListener(v -> {
            setCurrentItemShowing(HOUR_INDEX, true, false, true);
            tryVibrate();
        });
        mMinuteView.setOnClickListener(v -> {
            setCurrentItemShowing(MINUTE_INDEX, true, false, true);
            tryVibrate();
        });
        mSecondView.setOnClickListener(view1 -> {
            setCurrentItemShowing(SECOND_INDEX, true, false, true);
            tryVibrate();
        });

        mOkButton = view.findViewById(R.id.mdtp_ok);
        mOkButton.setOnClickListener(v -> {
            if (mInKbMode && isTypedTimeFullyLegal()) {
                finishKbMode(false);
            } else {
                tryVibrate();
            }
            notifyOnDateListener();
            dismiss();
        });
        mOkButton.setOnKeyListener(keyboardListener);
        mOkButton.setTypeface(ResourcesCompat.getFont(context, R.font.robotomedium));
        if (mOkString != null) mOkButton.setText(mOkString);
        else mOkButton.setText(mOkResid);

        mCancelButton = view.findViewById(R.id.mdtp_cancel);
        mCancelButton.setOnClickListener(v -> {
            tryVibrate();
            if (getDialog() != null) getDialog().cancel();
        });
        mCancelButton.setTypeface(ResourcesCompat.getFont(context, R.font.robotomedium));
        if (mCancelString != null) mCancelButton.setText(mCancelString);
        else mCancelButton.setText(mCancelResid);
        mCancelButton.setVisibility(isCancelable() ? View.VISIBLE : View.GONE);

        // Enable or disable the AM/PM view.
        if (mIs24HourMode) {
            mAmPmLayout.setVisibility(View.GONE);
        } else {
            OnClickListener listener = v -> {
                // Don't do anything if either AM or PM are disabled
                if (isAmDisabled() || isPmDisabled()) return;

                tryVibrate();
                int amOrPm = mTimePicker.getIsCurrentlyAmOrPm();
                if (amOrPm == AM) {
                    amOrPm = PM;
                } else if (amOrPm == PM) {
                    amOrPm = AM;
                }
                mTimePicker.setAmOrPm(amOrPm);
            };
            mAmTextView.setVisibility(View.GONE);
            mPmTextView.setVisibility(View.VISIBLE);
            mAmPmLayout.setOnClickListener(listener);
            if (mVersion == Version.VERSION_2) {
                mAmTextView.setText(mAmText);
                mPmTextView.setText(mPmText);
                mAmTextView.setVisibility(View.VISIBLE);
            }
            updateAmPmDisplay(mInitialTime.isAM() ? AM : PM);

        }

        // Disable seconds picker
        if (!mEnableSeconds) {
            mSecondView.setVisibility(View.GONE);
            view.findViewById(R.id.mdtp_separator_seconds).setVisibility(View.GONE);
        }

        // Disable minutes picker
        if (!mEnableMinutes) {
            mMinuteSpaceView.setVisibility(View.GONE);
            view.findViewById(R.id.mdtp_separator).setVisibility(View.GONE);
        }

        // Center stuff depending on what's visible
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        // Landscape layout is radically different
        if (isLandscape) {
            if (!mEnableMinutes && !mEnableSeconds) {
                // Just the hour
                // Put the hour above the center
                RelativeLayout.LayoutParams paramsHour = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsHour.addRule(RelativeLayout.ABOVE, R.id.mdtp_center_view);
                paramsHour.addRule(RelativeLayout.CENTER_HORIZONTAL);
                mHourSpaceView.setLayoutParams(paramsHour);
                if (mIs24HourMode) {
                    // Hour + Am/Pm indicator
                    // Put the am / pm indicator next to the hour
                    RelativeLayout.LayoutParams paramsAmPm = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    paramsAmPm.addRule(RelativeLayout.RIGHT_OF, R.id.mdtp_hour_space);
                    mAmPmLayout.setLayoutParams(paramsAmPm);
                }
            } else if (!mEnableSeconds && mIs24HourMode) {
                // Hour + Minutes
                // Put the separator above the center
                RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsSeparator.addRule(RelativeLayout.CENTER_HORIZONTAL);
                paramsSeparator.addRule(RelativeLayout.ABOVE, R.id.mdtp_center_view);
                TextView separatorView = view.findViewById(R.id.mdtp_separator);
                separatorView.setLayoutParams(paramsSeparator);
            } else if (!mEnableSeconds) {
                // Hour + Minutes + Am/Pm indicator
                // Put separator above the center
                RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsSeparator.addRule(RelativeLayout.CENTER_HORIZONTAL);
                paramsSeparator.addRule(RelativeLayout.ABOVE, R.id.mdtp_center_view);
                TextView separatorView = view.findViewById(R.id.mdtp_separator);
                separatorView.setLayoutParams(paramsSeparator);
                // Put the am/pm indicator below the separator
                RelativeLayout.LayoutParams paramsAmPm = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsAmPm.addRule(RelativeLayout.CENTER_IN_PARENT);
                paramsAmPm.addRule(RelativeLayout.BELOW, R.id.mdtp_center_view);
                mAmPmLayout.setLayoutParams(paramsAmPm);
            } else if (mIs24HourMode) {
                // Hour + Minutes + Seconds
                // Put the separator above the center
                RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsSeparator.addRule(RelativeLayout.CENTER_HORIZONTAL);
                paramsSeparator.addRule(RelativeLayout.ABOVE, R.id.mdtp_seconds_space);
                TextView separatorView = view.findViewById(R.id.mdtp_separator);
                separatorView.setLayoutParams(paramsSeparator);
                // Center the seconds
                RelativeLayout.LayoutParams paramsSeconds = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsSeconds.addRule(RelativeLayout.CENTER_IN_PARENT);
                mSecondSpaceView.setLayoutParams(paramsSeconds);
            } else {
                // Hour + Minutes + Seconds + Am/Pm Indicator
                // Put the seconds on the center
                RelativeLayout.LayoutParams paramsSeconds = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsSeconds.addRule(RelativeLayout.CENTER_IN_PARENT);
                mSecondSpaceView.setLayoutParams(paramsSeconds);
                // Put the separator above the seconds
                RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsSeparator.addRule(RelativeLayout.CENTER_HORIZONTAL);
                paramsSeparator.addRule(RelativeLayout.ABOVE, R.id.mdtp_seconds_space);
                TextView separatorView = view.findViewById(R.id.mdtp_separator);
                separatorView.setLayoutParams(paramsSeparator);
                // Put the Am/Pm indicator below the seconds
                RelativeLayout.LayoutParams paramsAmPm = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                paramsAmPm.addRule(RelativeLayout.CENTER_HORIZONTAL);
                paramsAmPm.addRule(RelativeLayout.BELOW, R.id.mdtp_seconds_space);
                mAmPmLayout.setLayoutParams(paramsAmPm);
            }
        } else if (mIs24HourMode && !mEnableSeconds && mEnableMinutes) {
            // center first separator
            RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            );
            paramsSeparator.addRule(RelativeLayout.CENTER_IN_PARENT);
            TextView separatorView = view.findViewById(R.id.mdtp_separator);
            separatorView.setLayoutParams(paramsSeparator);
        } else if (!mEnableMinutes && !mEnableSeconds) {
            // center the hour
            RelativeLayout.LayoutParams paramsHour = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            );
            paramsHour.addRule(RelativeLayout.CENTER_IN_PARENT);
            mHourSpaceView.setLayoutParams(paramsHour);

            if (!mIs24HourMode) {
                RelativeLayout.LayoutParams paramsAmPm = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                );
                paramsAmPm.addRule(RelativeLayout.RIGHT_OF, R.id.mdtp_hour_space);
                paramsAmPm.addRule(RelativeLayout.ALIGN_BASELINE, R.id.mdtp_hour_space);
                mAmPmLayout.setLayoutParams(paramsAmPm);
            }
        } else if (mEnableSeconds) {
            // link separator to minutes
            final View separator = view.findViewById(R.id.mdtp_separator);
            RelativeLayout.LayoutParams paramsSeparator = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            );
            paramsSeparator.addRule(RelativeLayout.LEFT_OF, R.id.mdtp_minutes_space);
            paramsSeparator.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
            separator.setLayoutParams(paramsSeparator);

            if (!mIs24HourMode) {
                // center minutes
                RelativeLayout.LayoutParams paramsMinutes = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                );
                paramsMinutes.addRule(RelativeLayout.CENTER_IN_PARENT);
                mMinuteSpaceView.setLayoutParams(paramsMinutes);
            } else {
                // move minutes to right of center
                RelativeLayout.LayoutParams paramsMinutes = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                );
                paramsMinutes.addRule(RelativeLayout.RIGHT_OF, R.id.mdtp_center_view);
                mMinuteSpaceView.setLayoutParams(paramsMinutes);
            }
        }

        mAllowAutoAdvance = true;
        setHour(mInitialTime.getHour(), true);
        setMinute(mInitialTime.getMinute());
        setSecond(mInitialTime.getSecond());

        // Set up for keyboard mode.
        mDoublePlaceholderText = res.getString(R.string.mdtp_time_placeholder);
        mDeletedKeyFormat = res.getString(R.string.mdtp_deleted_key);
        mPlaceholderText = mDoublePlaceholderText.charAt(0);
        mAmKeyCode = mPmKeyCode = -1;
        generateLegalTimesTree();
        if (mInKbMode && savedInstanceState != null) {
            mTypedTimes = savedInstanceState.getIntegerArrayList(KEY_TYPED_TIMES);
            tryStartingKbMode(-1);
            mHourView.invalidate();
        } else if (mTypedTimes == null) {
            mTypedTimes = new ArrayList<>();
        }

        // Set the title (if any)
        TextView timePickerHeader = view.findViewById(R.id.mdtp_time_picker_header);
        if (!mTitle.isEmpty()) {
            timePickerHeader.setVisibility(TextView.VISIBLE);
            timePickerHeader.setText(mTitle);
        }

        // Set the theme at the end so that the initialize()s above don't counteract the theme.
        timePickerHeader.setBackgroundColor(Utils.darkenColor(mAccentColor));
        view.findViewById(R.id.mdtp_time_display_background).setBackgroundColor(mAccentColor);
        view.findViewById(R.id.mdtp_time_display).setBackgroundColor(mAccentColor);

        // Button text can have a different color
        if (mOkColor == null) mOkColor = mAccentColor;
        mOkButton.setTextColor(mOkColor);
        if (mCancelColor == null) mCancelColor = mAccentColor;
        mCancelButton.setTextColor(mCancelColor);

        if (getDialog() == null) {
            view.findViewById(R.id.mdtp_done_background).setVisibility(View.GONE);
        }

        int circleBackground = ContextCompat.getColor(context, R.color.mdtp_circle_background);
        int backgroundColor = ContextCompat.getColor(context, R.color.mdtp_background_color);
        int darkBackgroundColor = ContextCompat.getColor(context, R.color.mdtp_light_gray);
        int lightGray = ContextCompat.getColor(context, R.color.mdtp_light_gray);

        mTimePicker.setBackgroundColor(mThemeDark ? lightGray : circleBackground);
        view.findViewById(R.id.mdtp_time_picker_dialog).setBackgroundColor(mThemeDark ? darkBackgroundColor : backgroundColor);
        return view;
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewGroup viewGroup = (ViewGroup) getView();
        if (viewGroup != null) {
            viewGroup.removeAllViewsInLayout();
            View view = onCreateView(requireActivity().getLayoutInflater(), viewGroup, null);
            viewGroup.addView(view);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mHapticFeedbackController.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHapticFeedbackController.stop();
        if (mDismissOnPause) dismiss();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (mOnCancelListener != null) mOnCancelListener.onCancel(dialog);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mOnDismissListener != null) mOnDismissListener.onDismiss(dialog);
    }

    @Override
    public void tryVibrate() {
        if (mVibrate) mHapticFeedbackController.tryVibrate();
    }

    private void updateAmPmDisplay(int amOrPm) {
        if (mVersion == Version.VERSION_2) {
            if (amOrPm == AM) {
                mAmTextView.setTextColor(mSelectedColor);
                mPmTextView.setTextColor(mUnselectedColor);
                Utils.tryAccessibilityAnnounce(mTimePicker, mAmText);
            } else {
                mAmTextView.setTextColor(mUnselectedColor);
                mPmTextView.setTextColor(mSelectedColor);
                Utils.tryAccessibilityAnnounce(mTimePicker, mPmText);
            }
        } else {
            if (amOrPm == AM) {
                mPmTextView.setText(mAmText);
                Utils.tryAccessibilityAnnounce(mTimePicker, mAmText);
                mPmTextView.setContentDescription(mAmText);
            } else if (amOrPm == PM) {
                mPmTextView.setText(mPmText);
                Utils.tryAccessibilityAnnounce(mTimePicker, mPmText);
                mPmTextView.setContentDescription(mPmText);
            } else {
                mPmTextView.setText(mDoublePlaceholderText);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mTimePicker != null) {
            outState.putParcelable(KEY_INITIAL_TIME, mTimePicker.getTime());
            outState.putBoolean(KEY_IS_24_HOUR_VIEW, mIs24HourMode);
            outState.putInt(KEY_CURRENT_ITEM_SHOWING, mTimePicker.getCurrentItemShowing());
            outState.putBoolean(KEY_IN_KB_MODE, mInKbMode);
            if (mInKbMode) {
                outState.putIntegerArrayList(KEY_TYPED_TIMES, mTypedTimes);
            }
            outState.putString(KEY_TITLE, mTitle);
            outState.putBoolean(KEY_THEME_DARK, mThemeDark);
            outState.putBoolean(KEY_THEME_DARK_CHANGED, mThemeDarkChanged);
            if (mAccentColor != null) outState.putInt(KEY_ACCENT, mAccentColor);
            outState.putBoolean(KEY_VIBRATE, mVibrate);
            outState.putBoolean(KEY_DISMISS, mDismissOnPause);
            outState.putBoolean(KEY_ENABLE_SECONDS, mEnableSeconds);
            outState.putBoolean(KEY_ENABLE_MINUTES, mEnableMinutes);
            outState.putInt(KEY_OK_RESID, mOkResid);
            outState.putString(KEY_OK_STRING, mOkString);
            if (mOkColor != null) outState.putInt(KEY_OK_COLOR, mOkColor);
            outState.putInt(KEY_CANCEL_RESID, mCancelResid);
            outState.putString(KEY_CANCEL_STRING, mCancelString);
            if (mCancelColor != null) outState.putInt(KEY_CANCEL_COLOR, mCancelColor);
            outState.putSerializable(KEY_VERSION, mVersion);
            outState.putParcelable(KEY_TIMEPOINTLIMITER, mLimiter);
            outState.putSerializable(KEY_LOCALE, mLocale);
        }
    }

    /**
     * Called by the picker for updating the header display.
     */
    @Override
    public void onValueSelected(Timepoint newValue) {
        setHour(newValue.getHour(), false);
        mTimePicker.setContentDescription(mHourPickerDescription + ": " + newValue.getHour());
        setMinute(newValue.getMinute());
        mTimePicker.setContentDescription(mMinutePickerDescription + ": " + newValue.getMinute());
        setSecond(newValue.getSecond());
        mTimePicker.setContentDescription(mSecondPickerDescription + ": " + newValue.getSecond());
        if (!mIs24HourMode) updateAmPmDisplay(newValue.isAM() ? AM : PM);
    }

    @Override
    public void advancePicker(int index) {
        if (!mAllowAutoAdvance) return;
        if (index == HOUR_INDEX && mEnableMinutes) {
            setCurrentItemShowing(MINUTE_INDEX, true, true, false);

            String announcement = mSelectHours + ". " + mTimePicker.getMinutes();
            Utils.tryAccessibilityAnnounce(mTimePicker, announcement);
        } else if (index == MINUTE_INDEX && mEnableSeconds) {
            setCurrentItemShowing(SECOND_INDEX, true, true, false);

            String announcement = mSelectMinutes + ". " + mTimePicker.getSeconds();
            Utils.tryAccessibilityAnnounce(mTimePicker, announcement);
        }
    }

    @Override
    public void enablePicker() {
        if (!isTypedTimeFullyLegal()) mTypedTimes.clear();
        finishKbMode(true);
    }

    public boolean isOutOfRange(Timepoint current) {
        return isOutOfRange(current, SECOND_INDEX);
    }

    @Override
    public boolean isOutOfRange(Timepoint current, int index) {
        return mLimiter.isOutOfRange(current, index, getPickerResolution());
    }

    @Override
    public boolean isAmDisabled() {
        return mLimiter.isAmDisabled();
    }

    @Override
    public boolean isPmDisabled() {
        return mLimiter.isPmDisabled();
    }

    /**
     * Round a given Timepoint to the nearest valid Timepoint
     *
     * @param time Timepoint - The timepoint to round
     * @return Timepoint - The nearest valid Timepoint
     */
    private Timepoint roundToNearest(@NonNull Timepoint time) {
        return roundToNearest(time, null);
    }

    @Override
    public Timepoint roundToNearest(@NonNull Timepoint time, @Nullable Timepoint.TYPE type) {
        return mLimiter.roundToNearest(time, type, getPickerResolution());
    }

    /**
     * Get the configured resolution of the current picker in terms of Timepoint components
     *
     * @return Timepoint.TYPE (hour, minute or second)
     */
    @NonNull
    Timepoint.TYPE getPickerResolution() {
        if (mEnableSeconds) return Timepoint.TYPE.SECOND;
        if (mEnableMinutes) return Timepoint.TYPE.MINUTE;
        return Timepoint.TYPE.HOUR;
    }

    private void setHour(int value, boolean announce) {
        String format;
        if (mIs24HourMode) {
            format = "%02d";
        } else {
            format = "%d";
            value = value % 12;
            if (value == 0) {
                value = 12;
            }
        }

        CharSequence text = String.format(mLocale, format, value);
        mHourView.setText(text);
        mHourSpaceView.setText(text);
        if (announce) {
            Utils.tryAccessibilityAnnounce(mTimePicker, text);
        }
    }

    private void setMinute(int value) {
        if (value == 60) {
            value = 0;
        }
        CharSequence text = String.format(mLocale, "%02d", value);
        Utils.tryAccessibilityAnnounce(mTimePicker, text);
        mMinuteView.setText(text);
        mMinuteSpaceView.setText(text);
    }

    private void setSecond(int value) {
        if (value == 60) {
            value = 0;
        }
        CharSequence text = String.format(mLocale, "%02d", value);
        Utils.tryAccessibilityAnnounce(mTimePicker, text);
        mSecondView.setText(text);
        mSecondSpaceView.setText(text);
    }

    // Show either Hours or Minutes.
    private void setCurrentItemShowing(int index, boolean animateCircle, boolean delayLabelAnimate,
                                       boolean announce) {
        mTimePicker.setCurrentItemShowing(index, animateCircle);

        TextView labelToAnimate;
        switch (index) {
            case HOUR_INDEX:
                int hours = mTimePicker.getHours();
                if (!mIs24HourMode) {
                    hours = hours % 12;
                }
                mTimePicker.setContentDescription(mHourPickerDescription + ": " + hours);
                if (announce) {
                    Utils.tryAccessibilityAnnounce(mTimePicker, mSelectHours);
                }
                labelToAnimate = mHourView;
                break;
            case MINUTE_INDEX:
                int minutes = mTimePicker.getMinutes();
                mTimePicker.setContentDescription(mMinutePickerDescription + ": " + minutes);
                if (announce) {
                    Utils.tryAccessibilityAnnounce(mTimePicker, mSelectMinutes);
                }
                labelToAnimate = mMinuteView;
                break;
            default:
                int seconds = mTimePicker.getSeconds();
                mTimePicker.setContentDescription(mSecondPickerDescription + ": " + seconds);
                if (announce) {
                    Utils.tryAccessibilityAnnounce(mTimePicker, mSelectSeconds);
                }
                labelToAnimate = mSecondView;
        }

        int hourColor = (index == HOUR_INDEX) ? mSelectedColor : mUnselectedColor;
        int minuteColor = (index == MINUTE_INDEX) ? mSelectedColor : mUnselectedColor;
        int secondColor = (index == SECOND_INDEX) ? mSelectedColor : mUnselectedColor;
        mHourView.setTextColor(hourColor);
        mMinuteView.setTextColor(minuteColor);
        mSecondView.setTextColor(secondColor);

        ObjectAnimator pulseAnimator = Utils.getPulseAnimator(labelToAnimate, 0.85f, 1.1f);
        if (delayLabelAnimate) {
            pulseAnimator.setStartDelay(PULSE_ANIMATOR_DELAY);
        }
        pulseAnimator.start();
    }

    /**
     * For keyboard mode, processes key events.
     *
     * @param keyCode the pressed key.
     * @return true if the key was successfully processed, false otherwise.
     */
    private boolean processKeyUp(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (mInKbMode) {
                if (isTypedTimeFullyLegal()) {
                    finishKbMode(true);
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mInKbMode) {
                if (!isTypedTimeFullyLegal()) {
                    return true;
                }
                finishKbMode(false);
            }
            if (mCallback != null) {
                mCallback.onTimeSet(this,
                        mTimePicker.getHours(), mTimePicker.getMinutes(), mTimePicker.getSeconds());
            }
            dismiss();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (mInKbMode) {
                if (!mTypedTimes.isEmpty()) {
                    int deleted = deleteLastTypedKey();
                    String deletedKeyStr;
                    if (deleted == getAmOrPmKeyCode(AM)) {
                        deletedKeyStr = mAmText;
                    } else if (deleted == getAmOrPmKeyCode(PM)) {
                        deletedKeyStr = mPmText;
                    } else {
                        deletedKeyStr = String.format(mLocale, "%d", getValFromKeyCode(deleted));
                    }
                    Utils.tryAccessibilityAnnounce(mTimePicker,
                            String.format(mDeletedKeyFormat, deletedKeyStr));
                    updateDisplay(true);
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_1
                   || keyCode == KeyEvent.KEYCODE_2 || keyCode == KeyEvent.KEYCODE_3
                   || keyCode == KeyEvent.KEYCODE_4 || keyCode == KeyEvent.KEYCODE_5
                   || keyCode == KeyEvent.KEYCODE_6 || keyCode == KeyEvent.KEYCODE_7
                   || keyCode == KeyEvent.KEYCODE_8 || keyCode == KeyEvent.KEYCODE_9
                   || (!mIs24HourMode &&
                       (keyCode == getAmOrPmKeyCode(AM) || keyCode == getAmOrPmKeyCode(PM)))) {
            if (!mInKbMode) {
                if (mTimePicker == null) {
                    // Something's wrong, because time picker should definitely not be null.
                    Log.e(TAG, "Unable to initiate keyboard mode, TimePicker was null.");
                    return true;
                }
                mTypedTimes.clear();
                tryStartingKbMode(keyCode);
                return true;
            }
            // We're already in keyboard mode.
            if (addKeyIfLegal(keyCode)) {
                updateDisplay(false);
            }
            return true;
        }
        return false;
    }

    /**
     * Try to start keyboard mode with the specified key, as long as the timepicker is not in the
     * middle of a touch-event.
     *
     * @param keyCode The key to use as the first press. Keyboard mode will not be started if the
     *                key is not legal to start with. Or, pass in -1 to get into keyboard mode without a starting
     *                key.
     */
    private void tryStartingKbMode(int keyCode) {
        if (mTimePicker.trySettingInputEnabled(false) &&
            (keyCode == -1 || addKeyIfLegal(keyCode))) {
            mInKbMode = true;
            mOkButton.setEnabled(false);
            updateDisplay(false);
        }
    }

    private boolean addKeyIfLegal(int keyCode) {
        // If we're in 24hour mode, we'll need to check if the input is full. If in AM/PM mode,
        // we'll need to see if AM/PM have been typed.
        int textSize = 6;
        if (mEnableMinutes && !mEnableSeconds) textSize = 4;
        if (!mEnableMinutes && !mEnableSeconds) textSize = 2;
        if ((mIs24HourMode && mTypedTimes.size() == textSize) ||
            (!mIs24HourMode && isTypedTimeFullyLegal())) {
            return false;
        }

        mTypedTimes.add(keyCode);
        if (!isTypedTimeLegalSoFar()) {
            deleteLastTypedKey();
            return false;
        }

        int val = getValFromKeyCode(keyCode);
        Utils.tryAccessibilityAnnounce(mTimePicker, String.format(mLocale, "%d", val));
        // Automatically fill in 0's if AM or PM was legally entered.
        if (isTypedTimeFullyLegal()) {
            if (!mIs24HourMode && mTypedTimes.size() <= (textSize - 1)) {
                mTypedTimes.add(mTypedTimes.size() - 1, KeyEvent.KEYCODE_0);
                mTypedTimes.add(mTypedTimes.size() - 1, KeyEvent.KEYCODE_0);
            }
            mOkButton.setEnabled(true);
        }

        return true;
    }

    /**
     * Traverse the tree to see if the keys that have been typed so far are legal as is,
     * or may become legal as more keys are typed (excluding backspace).
     */
    private boolean isTypedTimeLegalSoFar() {
        Node node = mLegalTimesTree;
        for (int keyCode : mTypedTimes) {
            node = node.canReach(keyCode);
            if (node == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the time that has been typed so far is completely legal, as is.
     */
    private boolean isTypedTimeFullyLegal() {
        if (mIs24HourMode) {
            // For 24-hour mode, the time is legal if the hours and minutes are each legal. Note:
            // getEnteredTime() will ONLY call isTypedTimeFullyLegal() when NOT in 24hour mode.
            Boolean[] enteredZeros = {false, false, false};
            int[] values = getEnteredTime(enteredZeros);
            return (values[0] >= 0 && values[1] >= 0 && values[1] < 60 && values[2] >= 0 && values[2] < 60);
        } else {
            // For AM/PM mode, the time is legal if it contains an AM or PM, as those can only be
            // legally added at specific times based on the tree's algorithm.
            return (mTypedTimes.contains(getAmOrPmKeyCode(AM)) ||
                    mTypedTimes.contains(getAmOrPmKeyCode(PM)));
        }
    }

    private int deleteLastTypedKey() {
        int deleted = mTypedTimes.remove(mTypedTimes.size() - 1);
        if (!isTypedTimeFullyLegal()) {
            mOkButton.setEnabled(false);
        }
        return deleted;
    }

    /**
     * Get out of keyboard mode. If there is nothing in typedTimes, revert to TimePicker's time.
     *
     * @param updateDisplays If true, update the displays with the relevant time.
     */
    private void finishKbMode(boolean updateDisplays) {
        mInKbMode = false;
        if (!mTypedTimes.isEmpty()) {
            Boolean[] enteredZeros = {false, false, false};
            int[] values = getEnteredTime(enteredZeros);
            mTimePicker.setTime(new Timepoint(values[0], values[1], values[2]));
            if (!mIs24HourMode) {
                mTimePicker.setAmOrPm(values[3]);
            }
            mTypedTimes.clear();
        }
        if (updateDisplays) {
            updateDisplay(false);
            mTimePicker.trySettingInputEnabled(true);
        }
    }

    /**
     * Update the hours, minutes, seconds and AM/PM displays with the typed times. If the typedTimes
     * is empty, either show an empty display (filled with the placeholder text), or update from the
     * timepicker's values.
     *
     * @param allowEmptyDisplay if true, then if the typedTimes is empty, use the placeholder text.
     *                          Otherwise, revert to the timepicker's values.
     */
    private void updateDisplay(boolean allowEmptyDisplay) {
        if (!allowEmptyDisplay && mTypedTimes.isEmpty()) {
            int hour = mTimePicker.getHours();
            int minute = mTimePicker.getMinutes();
            int second = mTimePicker.getSeconds();
            setHour(hour, true);
            setMinute(minute);
            setSecond(second);
            if (!mIs24HourMode) {
                updateAmPmDisplay(hour < 12 ? AM : PM);
            }
            setCurrentItemShowing(mTimePicker.getCurrentItemShowing(), true, true, true);
            mOkButton.setEnabled(true);
        } else {
            Boolean[] enteredZeros = {false, false, false};
            int[] values = getEnteredTime(enteredZeros);
            String hourFormat = enteredZeros[0] ? "%02d" : "%2d";
            String minuteFormat = (enteredZeros[1]) ? "%02d" : "%2d";
            String secondFormat = (enteredZeros[1]) ? "%02d" : "%2d";
            String hourStr = (values[0] == -1) ? mDoublePlaceholderText :
                    String.format(hourFormat, values[0]).replace(' ', mPlaceholderText);
            String minuteStr = (values[1] == -1) ? mDoublePlaceholderText :
                    String.format(minuteFormat, values[1]).replace(' ', mPlaceholderText);
            String secondStr = (values[2] == -1) ? mDoublePlaceholderText :
                    String.format(secondFormat, values[1]).replace(' ', mPlaceholderText);
            mHourView.setText(hourStr);
            mHourSpaceView.setText(hourStr);
            mHourView.setTextColor(mUnselectedColor);
            mMinuteView.setText(minuteStr);
            mMinuteSpaceView.setText(minuteStr);
            mMinuteView.setTextColor(mUnselectedColor);
            mSecondView.setText(secondStr);
            mSecondSpaceView.setText(secondStr);
            mSecondView.setTextColor(mUnselectedColor);
            if (!mIs24HourMode) {
                updateAmPmDisplay(values[3]);
            }
        }
    }

    private static int getValFromKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_0:
                return 0;
            case KeyEvent.KEYCODE_1:
                return 1;
            case KeyEvent.KEYCODE_2:
                return 2;
            case KeyEvent.KEYCODE_3:
                return 3;
            case KeyEvent.KEYCODE_4:
                return 4;
            case KeyEvent.KEYCODE_5:
                return 5;
            case KeyEvent.KEYCODE_6:
                return 6;
            case KeyEvent.KEYCODE_7:
                return 7;
            case KeyEvent.KEYCODE_8:
                return 8;
            case KeyEvent.KEYCODE_9:
                return 9;
            default:
                return -1;
        }
    }

    /**
     * Get the currently-entered time, as integer values of the hours, minutes and seconds typed.
     *
     * @param enteredZeros A size-2 boolean array, which the caller should initialize, and which
     *                     may then be used for the caller to know whether zeros had been explicitly entered as either
     *                     hours of minutes. This is helpful for deciding whether to show the dashes, or actual 0's.
     * @return A size-3 int array. The first value will be the hours, the second value will be the
     * minutes, and the third will be either TimePickerDialog.AM or TimePickerDialog.PM.
     */
    @NonNull
    private int[] getEnteredTime(@NonNull Boolean[] enteredZeros) {
        int amOrPm = -1;
        int startIndex = 1;
        if (!mIs24HourMode && isTypedTimeFullyLegal()) {
            int keyCode = mTypedTimes.get(mTypedTimes.size() - 1);
            if (keyCode == getAmOrPmKeyCode(AM)) {
                amOrPm = AM;
            } else if (keyCode == getAmOrPmKeyCode(PM)) {
                amOrPm = PM;
            }
            startIndex = 2;
        }
        int minute = -1;
        int hour = -1;
        int second = 0;
        int shift = mEnableSeconds ? 2 : 0;
        for (int i = startIndex; i <= mTypedTimes.size(); i++) {
            int val = getValFromKeyCode(mTypedTimes.get(mTypedTimes.size() - i));
            if (mEnableSeconds) {
                if (i == startIndex) {
                    second = val;
                } else if (i == startIndex + 1) {
                    second += 10 * val;
                    if (val == 0) enteredZeros[2] = true;
                }
            }
            if (mEnableMinutes) {
                if (i == startIndex + shift) {
                    minute = val;
                } else if (i == startIndex + shift + 1) {
                    minute += 10 * val;
                    if (val == 0) enteredZeros[1] = true;
                } else if (i == startIndex + shift + 2) {
                    hour = val;
                } else if (i == startIndex + shift + 3) {
                    hour += 10 * val;
                    if (val == 0) enteredZeros[0] = true;
                }
            } else {
                if (i == startIndex + shift) {
                    hour = val;
                } else if (i == startIndex + shift + 1) {
                    hour += 10 * val;
                    if (val == 0) enteredZeros[0] = true;
                }
            }
        }

        return new int[]{hour, minute, second, amOrPm};
    }

    /**
     * Get the keycode value for AM and PM in the current language.
     */
    private int getAmOrPmKeyCode(int amOrPm) {
        // Cache the codes.
        if (mAmKeyCode == -1 || mPmKeyCode == -1) {
            // Find the first character in the AM/PM text that is unique.
            KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            char amChar;
            char pmChar;
            for (int i = 0; i < Math.max(mAmText.length(), mPmText.length()); i++) {
                amChar = mAmText.toLowerCase(mLocale).charAt(i);
                pmChar = mPmText.toLowerCase(mLocale).charAt(i);
                if (amChar != pmChar) {
                    KeyEvent[] events = kcm.getEvents(new char[]{amChar, pmChar});
                    // There should be 4 events: a down and up for both AM and PM.
                    if (events != null && events.length == 4) {
                        mAmKeyCode = events[0].getKeyCode();
                        mPmKeyCode = events[2].getKeyCode();
                    } else {
                        Log.e(TAG, "Unable to find keycodes for AM and PM.");
                    }
                    break;
                }
            }
        }
        if (amOrPm == AM) {
            return mAmKeyCode;
        } else if (amOrPm == PM) {
            return mPmKeyCode;
        }

        return -1;
    }

    /**
     * Create a tree for deciding what keys can legally be typed.
     */
    private void generateLegalTimesTree() {
        // Create a quick cache of numbers to their keycodes.
        int k0 = KeyEvent.KEYCODE_0;
        int k1 = KeyEvent.KEYCODE_1;
        int k2 = KeyEvent.KEYCODE_2;
        int k3 = KeyEvent.KEYCODE_3;
        int k4 = KeyEvent.KEYCODE_4;
        int k5 = KeyEvent.KEYCODE_5;
        int k6 = KeyEvent.KEYCODE_6;
        int k7 = KeyEvent.KEYCODE_7;
        int k8 = KeyEvent.KEYCODE_8;
        int k9 = KeyEvent.KEYCODE_9;

        // The root of the tree doesn't contain any numbers.
        mLegalTimesTree = new Node();

        // In case we're only allowing hours
        if (!mEnableMinutes && mIs24HourMode) {
            // The first digit may be 0-1
            Node firstDigit = new Node(k0, k1);
            mLegalTimesTree.addChild(firstDigit);

            // When the first digit is 0-1, the second digit may be 0-9
            Node secondDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
            firstDigit.addChild(secondDigit);

            // The first digit may be 2
            firstDigit = new Node(k2);
            mLegalTimesTree.addChild(firstDigit);

            // When the first digit is 2, the second digit may be 0-3
            secondDigit = new Node(k0, k1, k2, k3);
            firstDigit.addChild(secondDigit);
            return;
        }
        // noinspection ConstantConditions
        if (!mEnableMinutes && !mIs24HourMode) {
            // We'll need to use the AM/PM node a lot.
            // Set up AM and PM to respond to "a" and "p".
            Node ampm = new Node(getAmOrPmKeyCode(AM), getAmOrPmKeyCode(PM));

            // The first digit may be 1
            Node firstDigit = new Node(k1);
            mLegalTimesTree.addChild(firstDigit);

            // If the first digit is 1, the second one may be am/pm 1pm
            firstDigit.addChild(ampm);
            // If the first digit is 1, the second digit may be 0-2
            Node secondDigit = new Node(k0, k1, k2);
            firstDigit.addChild(secondDigit);
            secondDigit.addChild(ampm);

            // The first digit may be 2-9
            firstDigit = new Node(k2, k3, k4, k5, k6, k7, k8, k9);
            mLegalTimesTree.addChild(firstDigit);
            firstDigit.addChild(ampm);
            return;
        }

        // In case minutes are allowed
        if (mIs24HourMode) {
            // We'll be re-using these nodes, so we'll save them.
            Node minuteFirstDigit = new Node(k0, k1, k2, k3, k4, k5);
            Node minuteSecondDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
            // The first digit must be followed by the second digit.
            minuteFirstDigit.addChild(minuteSecondDigit);

            if (mEnableSeconds) {
                Node secondsFirstDigit = new Node(k0, k1, k2, k3, k4, k5);
                Node secondsSecondDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
                secondsFirstDigit.addChild(secondsSecondDigit);

                // Minutes can be followed by seconds.
                minuteSecondDigit.addChild(secondsFirstDigit);
            }

            // The first digit may be 0-1.
            Node firstDigit = new Node(k0, k1);
            mLegalTimesTree.addChild(firstDigit);

            // When the first digit is 0-1, the second digit may be 0-5.
            Node secondDigit = new Node(k0, k1, k2, k3, k4, k5);
            firstDigit.addChild(secondDigit);
            // We may now be followed by the first minute digit. E.g. 00:09, 15:58.
            secondDigit.addChild(minuteFirstDigit);

            // When the first digit is 0-1, and the second digit is 0-5, the third digit may be 6-9.
            Node thirdDigit = new Node(k6, k7, k8, k9);
            // The time must now be finished. E.g. 0:55, 1:08.
            secondDigit.addChild(thirdDigit);

            // When the first digit is 0-1, the second digit may be 6-9.
            secondDigit = new Node(k6, k7, k8, k9);
            firstDigit.addChild(secondDigit);
            // We must now be followed by the first minute digit. E.g. 06:50, 18:20.
            secondDigit.addChild(minuteFirstDigit);

            // The first digit may be 2.
            firstDigit = new Node(k2);
            mLegalTimesTree.addChild(firstDigit);

            // When the first digit is 2, the second digit may be 0-3.
            secondDigit = new Node(k0, k1, k2, k3);
            firstDigit.addChild(secondDigit);
            // We must now be followed by the first minute digit. E.g. 20:50, 23:09.
            secondDigit.addChild(minuteFirstDigit);

            // When the first digit is 2, the second digit may be 4-5.
            secondDigit = new Node(k4, k5);
            firstDigit.addChild(secondDigit);
            // We must now be followd by the last minute digit. E.g. 2:40, 2:53.
            secondDigit.addChild(minuteSecondDigit);

            // The first digit may be 3-9.
            firstDigit = new Node(k3, k4, k5, k6, k7, k8, k9);
            mLegalTimesTree.addChild(firstDigit);
            // We must now be followed by the first minute digit. E.g. 3:57, 8:12.
            firstDigit.addChild(minuteFirstDigit);
        } else {
            // We'll need to use the AM/PM node a lot.
            // Set up AM and PM to respond to "a" and "p".
            Node ampm = new Node(getAmOrPmKeyCode(AM), getAmOrPmKeyCode(PM));

            // Seconds will be used a few times as well, if enabled.
            Node secondsFirstDigit = new Node(k0, k1, k2, k3, k4, k5);
            Node secondsSecondDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
            secondsSecondDigit.addChild(ampm);
            secondsFirstDigit.addChild(secondsSecondDigit);

            // The first hour digit may be 1.
            Node firstDigit = new Node(k1);
            mLegalTimesTree.addChild(firstDigit);
            // We'll allow quick input of on-the-hour times. E.g. 1pm.
            firstDigit.addChild(ampm);

            // When the first digit is 1, the second digit may be 0-2.
            Node secondDigit = new Node(k0, k1, k2);
            firstDigit.addChild(secondDigit);
            // Also for quick input of on-the-hour times. E.g. 10pm, 12am.
            secondDigit.addChild(ampm);

            // When the first digit is 1, and the second digit is 0-2, the third digit may be 0-5.
            Node thirdDigit = new Node(k0, k1, k2, k3, k4, k5);
            secondDigit.addChild(thirdDigit);
            // The time may be finished now. E.g. 1:02pm, 1:25am.
            thirdDigit.addChild(ampm);

            // When the first digit is 1, the second digit is 0-2, and the third digit is 0-5,
            // the fourth digit may be 0-9.
            Node fourthDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
            thirdDigit.addChild(fourthDigit);
            // The time must be finished now, when seconds are disabled. E.g. 10:49am, 12:40pm.
            fourthDigit.addChild(ampm);

            // When the first digit is 1, the second digit is 0-2, and the third digit is 0-5,
            // and fourth digit is 0-9, we may add seconds if enabled.
            if (mEnableSeconds) {
                // The time must be finished now. E.g. 10:49:01am, 12:40:59pm.
                fourthDigit.addChild(secondsFirstDigit);
            }

            // When the first digit is 1, and the second digit is 0-2, the third digit may be 6-9.
            thirdDigit = new Node(k6, k7, k8, k9);
            secondDigit.addChild(thirdDigit);
            // The time must be finished now. E.g. 1:08am, 1:26pm.
            thirdDigit.addChild(ampm);

            // When the first digit is 1, and the second digit is 0-2, and the third digit is 6-9,
            // we may add seconds is enabled.
            if (mEnableSeconds) {
                // The time must be finished now. E.g. 1:08:01am, 1:26:59pm.
                thirdDigit.addChild(secondsFirstDigit);
            }

            // When the first digit is 1, the second digit may be 3-5.
            secondDigit = new Node(k3, k4, k5);
            firstDigit.addChild(secondDigit);

            // When the first digit is 1, and the second digit is 3-5, the third digit may be 0-9.
            thirdDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
            secondDigit.addChild(thirdDigit);
            // The time must be finished now if seconds are disabled. E.g. 1:39am, 1:50pm.
            thirdDigit.addChild(ampm);

            // When the first digit is 1, and the second digit is 3-5, and the third digit is 0-9,
            // we may add seconds if enabled.
            if (mEnableSeconds) {
                // The time must be finished now. E.g. 1:39:01am, 1:50:59pm.
                thirdDigit.addChild(secondsFirstDigit);
            }

            // The hour digit may be 2-9.
            firstDigit = new Node(k2, k3, k4, k5, k6, k7, k8, k9);
            mLegalTimesTree.addChild(firstDigit);
            // We'll allow quick input of on-the-hour-times. E.g. 2am, 5pm.
            firstDigit.addChild(ampm);

            // When the first digit is 2-9, the second digit may be 0-5.
            secondDigit = new Node(k0, k1, k2, k3, k4, k5);
            firstDigit.addChild(secondDigit);

            // When the first digit is 2-9, and the second digit is 0-5, the third digit may be 0-9.
            thirdDigit = new Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9);
            secondDigit.addChild(thirdDigit);
            // The time must be finished now. E.g. 2:57am, 9:30pm.
            thirdDigit.addChild(ampm);

            // When the first digit is 2-9, and the second digit is 0-5, and third digit is 0-9, we
            // may add seconds if enabled.
            if (mEnableSeconds) {
                // The time must be finished now. E.g. 2:57:01am, 9:30:59pm.
                thirdDigit.addChild(secondsFirstDigit);
            }
        }
    }

    /**
     * Simple node class to be used for traversal to check for legal times.
     * mLegalKeys represents the keys that can be typed to get to the node.
     * mChildren are the children that can be reached from this node.
     */
    private static class Node {
        private int[] mLegalKeys;
        private ArrayList<Node> mChildren;

        public Node(int... legalKeys) {
            mLegalKeys = legalKeys;
            mChildren = new ArrayList<>();
        }

        public void addChild(Node child) {
            mChildren.add(child);
        }

        public boolean containsKey(int key) {
            for (int legalKey : mLegalKeys) {
                if (legalKey == key) return true;
            }
            return false;
        }

        public Node canReach(int key) {
            if (mChildren == null) {
                return null;
            }
            for (Node child : mChildren) {
                if (child.containsKey(key)) {
                    return child;
                }
            }
            return null;
        }
    }

    private class KeyboardListener implements OnKeyListener {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return processKeyUp(keyCode);
            }
            return false;
        }
    }

    public void notifyOnDateListener() {
        if (mCallback != null) {
            mCallback.onTimeSet(this, mTimePicker.getHours(), mTimePicker.getMinutes(), mTimePicker.getSeconds());
        }
    }

    public Timepoint getSelectedTime() {
        return mTimePicker.getTime();
    }
}
