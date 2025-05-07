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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.View;

import com.wdullaer.materialdatetimepicker.R;
import com.wdullaer.materialdatetimepicker.Utils;

import java.text.DateFormatSymbols;
import java.util.Locale;

/**
 * Draw the two smaller AM and PM circles next to where the larger circle will be.
 */
public class AmPmCirclesView extends View {
    private static final String TAG = "AmPmCirclesView";

    // Alpha level for selected circle.
    private static final int SELECTED_ALPHA = Utils.SELECTED_ALPHA;
    private static final int SELECTED_ALPHA_THEME_DARK = Utils.SELECTED_ALPHA_THEME_DARK;

    private final Paint mPaint = new Paint();
    private int mSelectedAlpha;
    private int mTouchedColor;
    private int mUnselectedColor;
    private int mAmPmTextColor;
    private int mAmPmSelectedTextColor;
    private int mAmPmDisabledTextColor;
    private int mSelectedColor;
    private float mCircleRadiusMultiplier;
    private float mAmPmCircleRadiusMultiplier;
    private String mAmText;
    private String mPmText;
    private boolean mAmDisabled;
    private boolean mPmDisabled;
    private boolean mIsInitialized;

    private static final int AM = TimePickerDialog.AM;
    private static final int PM = TimePickerDialog.PM;

    private boolean mDrawValuesReady;
    private int mAmPmCircleRadius;
    private int mAmXCenter;
    private int mPmXCenter;
    private int mAmPmYCenter;
    private int mAmOrPm;
    private int mAmOrPmPressed;

    public AmPmCirclesView(Context context) {
        super(context);
        mIsInitialized = false;
    }

    public void initialize(Context context, Locale locale, TimePickerController controller, int amOrPm) {
        if (mIsInitialized) {
            Log.e(TAG, "AmPmCirclesView may only be initialized once.");
            return;
        }

        Resources res = context.getResources();

        if (controller.isThemeDark()) {
            mUnselectedColor = ContextCompat.getColor(context, R.color.mdtp_circle_background_dark_theme);
            mAmPmTextColor = ContextCompat.getColor(context, R.color.mdtp_white);
            mAmPmDisabledTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_disabled_dark_theme);
            mSelectedAlpha = SELECTED_ALPHA_THEME_DARK;
        } else {
            mUnselectedColor = ContextCompat.getColor(context, R.color.mdtp_white);
            mAmPmTextColor = ContextCompat.getColor(context, R.color.mdtp_ampm_text_color);
            mAmPmDisabledTextColor = ContextCompat.getColor(context, R.color.mdtp_date_picker_text_disabled);
            mSelectedAlpha = SELECTED_ALPHA;
        }

        mSelectedColor = controller.getAccentColor();
        mTouchedColor = Utils.darkenColor(mSelectedColor);
        mAmPmSelectedTextColor = ContextCompat.getColor(context, R.color.mdtp_white);

        String typefaceFamily = res.getString(R.string.mdtp_sans_serif);
        Typeface tf = Typeface.create(typefaceFamily, Typeface.NORMAL);
        mPaint.setTypeface(tf);
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);

        mCircleRadiusMultiplier =
                Float.parseFloat(res.getString(R.string.mdtp_circle_radius_multiplier));
        mAmPmCircleRadiusMultiplier =
                Float.parseFloat(res.getString(R.string.mdtp_ampm_circle_radius_multiplier));
        String[] amPmTexts = new DateFormatSymbols(locale).getAmPmStrings();
        mAmText = amPmTexts[0];
        mPmText = amPmTexts[1];

        mAmDisabled = controller.isAmDisabled();
        mPmDisabled = controller.isPmDisabled();

        setAmOrPm(amOrPm);
        mAmOrPmPressed = -1;

        mIsInitialized = true;
    }

    public void setAmOrPm(int amOrPm) {
        mAmOrPm = amOrPm;
    }

    public void setAmOrPmPressed(int amOrPmPressed) {
        mAmOrPmPressed = amOrPmPressed;
    }

    /**
     * Calculate whether the coordinates are touching the AM or PM circle.
     */
    public int getIsTouchingAmOrPm(float xCoord, float yCoord) {
        if (!mDrawValuesReady) {
            return -1;
        }

        int squaredYDistance = (int) ((yCoord - mAmPmYCenter)*(yCoord - mAmPmYCenter));

        int distanceToAmCenter =
                (int) Math.sqrt((xCoord - mAmXCenter)*(xCoord - mAmXCenter) + squaredYDistance);
        if (distanceToAmCenter <= mAmPmCircleRadius && !mAmDisabled) {
            return AM;
        }

        int distanceToPmCenter =
                (int) Math.sqrt((xCoord - mPmXCenter)*(xCoord - mPmXCenter) + squaredYDistance);
        if (distanceToPmCenter <= mAmPmCircleRadius && !mPmDisabled) {
            return PM;
        }

        // Neither was close enough.
        return -1;
    }

    @Override
    public void onDraw(Canvas canvas) {
        int viewWidth = getWidth();
        if (viewWidth == 0 || !mIsInitialized) {
            return;
        }

        if (!mDrawValuesReady) {
            int layoutXCenter = getWidth() / 2;
            int layoutYCenter = getHeight() / 2;
            int circleRadius =
                    (int) (Math.min(layoutXCenter, layoutYCenter) * mCircleRadiusMultiplier);
            mAmPmCircleRadius = (int) (circleRadius * mAmPmCircleRadiusMultiplier);
            layoutYCenter += mAmPmCircleRadius*0.75;
            int textSize = mAmPmCircleRadius * 3 / 4;
            mPaint.setTextSize(textSize);

            // Line up the vertical center of the AM/PM circles with the bottom of the main circle.
            mAmPmYCenter = layoutYCenter - mAmPmCircleRadius / 2 + circleRadius;
            // Line up the horizontal edges of the AM/PM circles with the horizontal edges
            // of the main circle.
            mAmXCenter = layoutXCenter - circleRadius + mAmPmCircleRadius;
            mPmXCenter = layoutXCenter + circleRadius - mAmPmCircleRadius;

            mDrawValuesReady = true;
        }

        // We'll need to draw either a lighter blue (for selection), a darker blue (for touching)
        // or white (for not selected).
        int amColor = mUnselectedColor;
        int amAlpha = 255;
        int amTextColor = mAmPmTextColor;
        int pmColor = mUnselectedColor;
        int pmAlpha = 255;
        int pmTextColor = mAmPmTextColor;

        if (mAmOrPm == AM) {
            amColor = mSelectedColor;
            amAlpha = mSelectedAlpha;
            amTextColor = mAmPmSelectedTextColor;
        } else if (mAmOrPm == PM) {
            pmColor = mSelectedColor;
            pmAlpha = mSelectedAlpha;
            pmTextColor = mAmPmSelectedTextColor;
        }
        if (mAmOrPmPressed == AM) {
            amColor = mTouchedColor;
            amAlpha = mSelectedAlpha;
        } else if (mAmOrPmPressed == PM) {
            pmColor = mTouchedColor;
            pmAlpha = mSelectedAlpha;
        }
        if (mAmDisabled) {
            amColor = mUnselectedColor;
            amTextColor = mAmPmDisabledTextColor;
        }
        if (mPmDisabled) {
            pmColor = mUnselectedColor;
            pmTextColor = mAmPmDisabledTextColor;
        }

        // Draw the two circles.
        mPaint.setColor(amColor);
        mPaint.setAlpha(amAlpha);
        canvas.drawCircle(mAmXCenter, mAmPmYCenter, mAmPmCircleRadius, mPaint);
        mPaint.setColor(pmColor);
        mPaint.setAlpha(pmAlpha);
        canvas.drawCircle(mPmXCenter, mAmPmYCenter, mAmPmCircleRadius, mPaint);

        // Draw the AM/PM texts on top.
        mPaint.setColor(amTextColor);
        int textYCenter = mAmPmYCenter - (int) (mPaint.descent() + mPaint.ascent()) / 2;
        canvas.drawText(mAmText, mAmXCenter, textYCenter, mPaint);
        mPaint.setColor(pmTextColor);
        canvas.drawText(mPmText, mPmXCenter, textYCenter, mPaint);
    }
}
