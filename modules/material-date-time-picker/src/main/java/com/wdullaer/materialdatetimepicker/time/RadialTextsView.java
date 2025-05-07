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

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
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

/**
 * A view to show a series of numbers in a circular pattern.
 */
public class RadialTextsView extends View {
    private final static String TAG = "RadialTextsView";

    private final Paint mPaint = new Paint();
    private final Paint mSelectedPaint = new Paint();
    private final Paint mInactivePaint = new Paint();

    private boolean mDrawValuesReady;
    private boolean mIsInitialized;

    private int selection = -1;

    private SelectionValidator mValidator;

    private Typeface mTypefaceLight;
    private Typeface mTypefaceRegular;
    private String[] mTexts;
    private String[] mInnerTexts;
    private boolean mIs24HourMode;
    private boolean mHasInnerCircle;
    private float mCircleRadiusMultiplier;
    private float mAmPmCircleRadiusMultiplier;
    private float mNumbersRadiusMultiplier;
    private float mInnerNumbersRadiusMultiplier;
    private float mTextSizeMultiplier;
    private float mInnerTextSizeMultiplier;

    private int mXCenter;
    private int mYCenter;
    private float mCircleRadius;
    private boolean mTextGridValuesDirty;
    private float mTextSize;
    private float mInnerTextSize;
    private float[] mTextGridHeights;
    private float[] mTextGridWidths;
    private float[] mInnerTextGridHeights;
    private float[] mInnerTextGridWidths;

    private float mAnimationRadiusMultiplier;
    private float mTransitionMidRadiusMultiplier;
    private float mTransitionEndRadiusMultiplier;
    ObjectAnimator mDisappearAnimator;
    ObjectAnimator mReappearAnimator;
    private InvalidateUpdateListener mInvalidateUpdateListener;

    public RadialTextsView(Context context) {
        super(context);
        mIsInitialized = false;
    }

    public void initialize(Context context, String[] texts, String[] innerTexts,
            TimePickerController controller, SelectionValidator validator, boolean disappearsOut) {
        if (mIsInitialized) {
            Log.e(TAG, "This RadialTextsView may only be initialized once.");
            return;
        }
        Resources res = context.getResources();

        // Set up the paint.
        int textColorRes = controller.isThemeDark() ? R.color.mdtp_white : R.color.mdtp_numbers_text_color;
        mPaint.setColor(ContextCompat.getColor(context, textColorRes));
        String typefaceFamily = res.getString(R.string.mdtp_radial_numbers_typeface);
        mTypefaceLight = Typeface.create(typefaceFamily, Typeface.NORMAL);
        String typefaceFamilyRegular = res.getString(R.string.mdtp_sans_serif);
        mTypefaceRegular = Typeface.create(typefaceFamilyRegular, Typeface.NORMAL);
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);

        // Set up the selected paint
        int selectedTextColor = ContextCompat.getColor(context, R.color.mdtp_white);
        mSelectedPaint.setColor(selectedTextColor);
        mSelectedPaint.setAntiAlias(true);
        mSelectedPaint.setTextAlign(Align.CENTER);

        // Set up the inactive paint
        int inactiveColorRes = controller.isThemeDark() ? R.color.mdtp_date_picker_text_disabled_dark_theme
                : R.color.mdtp_date_picker_text_disabled;
        mInactivePaint.setColor(ContextCompat.getColor(context, inactiveColorRes));
        mInactivePaint.setAntiAlias(true);
        mInactivePaint.setTextAlign(Align.CENTER);

        mTexts = texts;
        mInnerTexts = innerTexts;
        mIs24HourMode = controller.is24HourMode();
        mHasInnerCircle = (innerTexts != null);

        // Calculate the radius for the main circle.
        if (mIs24HourMode || controller.getVersion() != TimePickerDialog.Version.VERSION_1) {
            mCircleRadiusMultiplier = Float.parseFloat(
                    res.getString(R.string.mdtp_circle_radius_multiplier_24HourMode));
        } else {
            mCircleRadiusMultiplier = Float.parseFloat(
                    res.getString(R.string.mdtp_circle_radius_multiplier));
            mAmPmCircleRadiusMultiplier =
                    Float.parseFloat(res.getString(R.string.mdtp_ampm_circle_radius_multiplier));
        }

        // Initialize the widths and heights of the grid, and calculate the values for the numbers.
        mTextGridHeights = new float[7];
        mTextGridWidths = new float[7];
        if (mHasInnerCircle) {
            mNumbersRadiusMultiplier = Float.parseFloat(
                    res.getString(R.string.mdtp_numbers_radius_multiplier_outer));
            mInnerNumbersRadiusMultiplier = Float.parseFloat(
                    res.getString(R.string.mdtp_numbers_radius_multiplier_inner));

            // Version 2 layout draws outer circle bigger than inner
            if (controller.getVersion() == TimePickerDialog.Version.VERSION_1) {
                mTextSizeMultiplier = Float.parseFloat(
                        res.getString(R.string.mdtp_text_size_multiplier_outer));
                mInnerTextSizeMultiplier = Float.parseFloat(
                        res.getString(R.string.mdtp_text_size_multiplier_inner));
            } else {
                mTextSizeMultiplier = Float.parseFloat(
                        res.getString(R.string.mdtp_text_size_multiplier_outer_v2));
                mInnerTextSizeMultiplier = Float.parseFloat(
                        res.getString(R.string.mdtp_text_size_multiplier_inner_v2));
            }

            mInnerTextGridHeights = new float[7];
            mInnerTextGridWidths = new float[7];
        } else {
            mNumbersRadiusMultiplier = Float.parseFloat(
                    res.getString(R.string.mdtp_numbers_radius_multiplier_normal));
            mTextSizeMultiplier = Float.parseFloat(
                    res.getString(R.string.mdtp_text_size_multiplier_normal));
        }

        mAnimationRadiusMultiplier = 1;
        mTransitionMidRadiusMultiplier = 1f + (0.05f * (disappearsOut? -1 : 1));
        mTransitionEndRadiusMultiplier = 1f + (0.3f * (disappearsOut? 1 : -1));
        mInvalidateUpdateListener = new InvalidateUpdateListener();

        mValidator = validator;

        mTextGridValuesDirty = true;
        mIsInitialized = true;
    }

    /**
     * Set the value of the selected text. Depending on the theme this will be rendered differently
     * @param selection The text which is currently selected
     */
    protected void setSelection(int selection) {
        this.selection = selection;
    }

    /**
     * Allows for smoother animation.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Used by the animation to move the numbers in and out.
     */
    @SuppressWarnings("unused")
    public void setAnimationRadiusMultiplier(float animationRadiusMultiplier) {
        mAnimationRadiusMultiplier = animationRadiusMultiplier;
        mTextGridValuesDirty = true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        int viewWidth = getWidth();
        if (viewWidth == 0 || !mIsInitialized) {
            return;
        }

        if (!mDrawValuesReady) {
            mXCenter = getWidth() / 2;
            mYCenter = getHeight() / 2;
            mCircleRadius = Math.min(mXCenter, mYCenter) * mCircleRadiusMultiplier;
            if (!mIs24HourMode) {
                // We'll need to draw the AM/PM circles, so the main circle will need to have
                // a slightly higher center. To keep the entire view centered vertically, we'll
                // have to push it up by half the radius of the AM/PM circles.
                float amPmCircleRadius = mCircleRadius * mAmPmCircleRadiusMultiplier;
                mYCenter -= amPmCircleRadius *0.75;
            }

            mTextSize = mCircleRadius * mTextSizeMultiplier;
            if (mHasInnerCircle) {
                mInnerTextSize = mCircleRadius * mInnerTextSizeMultiplier;
            }

            // Because the text positions will be static, pre-render the animations.
            renderAnimations();

            mTextGridValuesDirty = true;
            mDrawValuesReady = true;
        }

        // Calculate the text positions, but only if they've changed since the last onDraw.
        if (mTextGridValuesDirty) {
            float numbersRadius =
                    mCircleRadius * mNumbersRadiusMultiplier * mAnimationRadiusMultiplier;

            // Calculate the positions for the 12 numbers in the main circle.
            calculateGridSizes(numbersRadius, mXCenter, mYCenter,
                    mTextSize, mTextGridHeights, mTextGridWidths);
            if (mHasInnerCircle) {
                // If we have an inner circle, calculate those positions too.
                float innerNumbersRadius =
                        mCircleRadius * mInnerNumbersRadiusMultiplier * mAnimationRadiusMultiplier;
                calculateGridSizes(innerNumbersRadius, mXCenter, mYCenter,
                        mInnerTextSize, mInnerTextGridHeights, mInnerTextGridWidths);
            }
            mTextGridValuesDirty = false;
        }

        // Draw the texts in the pre-calculated positions.
        drawTexts(canvas, mTextSize, mTypefaceLight, mTexts, mTextGridWidths, mTextGridHeights);
        if (mHasInnerCircle) {
            drawTexts(canvas, mInnerTextSize, mTypefaceRegular, mInnerTexts,
                    mInnerTextGridWidths, mInnerTextGridHeights);
        }
    }

    /**
     * Using the trigonometric Unit Circle, calculate the positions that the text will need to be
     * drawn at based on the specified circle radius. Place the values in the textGridHeights and
     * textGridWidths parameters.
     */
    private void calculateGridSizes(float numbersRadius, float xCenter, float yCenter,
            float textSize, float[] textGridHeights, float[] textGridWidths) {
        /*
         * The numbers need to be drawn in a 7x7 grid, representing the points on the Unit Circle.
         */
        float offset1 = numbersRadius;
        // cos(30) = a / r => r * cos(30) = a => r * √3/2 = a
        float offset2 = numbersRadius * ((float) Math.sqrt(3)) / 2f;
        // sin(30) = o / r => r * sin(30) = o => r / 2 = a
        float offset3 = numbersRadius / 2f;
        mPaint.setTextSize(textSize);
        mSelectedPaint.setTextSize(textSize);
        mInactivePaint.setTextSize(textSize);
        // We'll need yTextBase to be slightly lower to account for the text's baseline.
        yCenter -= (mPaint.descent() + mPaint.ascent()) / 2;

        textGridHeights[0] = yCenter - offset1;
        textGridWidths[0] = xCenter - offset1;
        textGridHeights[1] = yCenter - offset2;
        textGridWidths[1] = xCenter - offset2;
        textGridHeights[2] = yCenter - offset3;
        textGridWidths[2] = xCenter - offset3;
        textGridHeights[3] = yCenter;
        textGridWidths[3] = xCenter;
        textGridHeights[4] = yCenter + offset3;
        textGridWidths[4] = xCenter + offset3;
        textGridHeights[5] = yCenter + offset2;
        textGridWidths[5] = xCenter + offset2;
        textGridHeights[6] = yCenter + offset1;
        textGridWidths[6] = xCenter + offset1;
    }

    private Paint[] assignTextColors(String[] texts) {
        Paint[] paints = new Paint[texts.length];
        for(int i=0;i<texts.length;i++) {
            int text = Integer.parseInt(texts[i]);
            if(text == selection) paints[i] = mSelectedPaint;
            else if(mValidator.isValidSelection(text)) paints[i] = mPaint;
            else paints[i] = mInactivePaint;
        }
        return paints;
    }

    /**
     * Draw the 12 text values at the positions specified by the textGrid parameters.
     */
    private void drawTexts(Canvas canvas, float textSize, Typeface typeface, String[] texts,
            float[] textGridWidths, float[] textGridHeights) {
        mPaint.setTextSize(textSize);
        mPaint.setTypeface(typeface);
        Paint[] textPaints = assignTextColors(texts);
        canvas.drawText(texts[0], textGridWidths[3], textGridHeights[0], textPaints[0]);
        canvas.drawText(texts[1], textGridWidths[4], textGridHeights[1], textPaints[1]);
        canvas.drawText(texts[2], textGridWidths[5], textGridHeights[2], textPaints[2]);
        canvas.drawText(texts[3], textGridWidths[6], textGridHeights[3], textPaints[3]);
        canvas.drawText(texts[4], textGridWidths[5], textGridHeights[4], textPaints[4]);
        canvas.drawText(texts[5], textGridWidths[4], textGridHeights[5], textPaints[5]);
        canvas.drawText(texts[6], textGridWidths[3], textGridHeights[6], textPaints[6]);
        canvas.drawText(texts[7], textGridWidths[2], textGridHeights[5], textPaints[7]);
        canvas.drawText(texts[8], textGridWidths[1], textGridHeights[4], textPaints[8]);
        canvas.drawText(texts[9], textGridWidths[0], textGridHeights[3], textPaints[9]);
        canvas.drawText(texts[10], textGridWidths[1], textGridHeights[2], textPaints[10]);
        canvas.drawText(texts[11], textGridWidths[2], textGridHeights[1], textPaints[11]);
    }

    /**
     * Render the animations for appearing and disappearing.
     */
    private void renderAnimations() {
        Keyframe kf0, kf1, kf2, kf3;
        float midwayPoint = 0.2f;
        int duration = 500;

        // Set up animator for disappearing.
        kf0 = Keyframe.ofFloat(0f, 1);
        kf1 = Keyframe.ofFloat(midwayPoint, mTransitionMidRadiusMultiplier);
        kf2 = Keyframe.ofFloat(1f, mTransitionEndRadiusMultiplier);
        PropertyValuesHolder radiusDisappear = PropertyValuesHolder.ofKeyframe(
                "animationRadiusMultiplier", kf0, kf1, kf2);

        kf0 = Keyframe.ofFloat(0f, 1f);
        kf1 = Keyframe.ofFloat(1f, 0f);
        PropertyValuesHolder fadeOut = PropertyValuesHolder.ofKeyframe("alpha", kf0, kf1);

        mDisappearAnimator = ObjectAnimator.ofPropertyValuesHolder(
                this, radiusDisappear, fadeOut).setDuration(duration);
        mDisappearAnimator.addUpdateListener(mInvalidateUpdateListener);


        // Set up animator for reappearing.
        float delayMultiplier = 0.25f;
        float transitionDurationMultiplier = 1f;
        float totalDurationMultiplier = transitionDurationMultiplier + delayMultiplier;
        int totalDuration = (int) (duration * totalDurationMultiplier);
        float delayPoint = (delayMultiplier * duration) / totalDuration;
        midwayPoint = 1 - (midwayPoint * (1 - delayPoint));

        kf0 = Keyframe.ofFloat(0f, mTransitionEndRadiusMultiplier);
        kf1 = Keyframe.ofFloat(delayPoint, mTransitionEndRadiusMultiplier);
        kf2 = Keyframe.ofFloat(midwayPoint, mTransitionMidRadiusMultiplier);
        kf3 = Keyframe.ofFloat(1f, 1);
        PropertyValuesHolder radiusReappear = PropertyValuesHolder.ofKeyframe(
                "animationRadiusMultiplier", kf0, kf1, kf2, kf3);

        kf0 = Keyframe.ofFloat(0f, 0f);
        kf1 = Keyframe.ofFloat(delayPoint, 0f);
        kf2 = Keyframe.ofFloat(1f, 1f);
        PropertyValuesHolder fadeIn = PropertyValuesHolder.ofKeyframe("alpha", kf0, kf1, kf2);

        mReappearAnimator = ObjectAnimator.ofPropertyValuesHolder(
                this, radiusReappear, fadeIn).setDuration(totalDuration);
        mReappearAnimator.addUpdateListener(mInvalidateUpdateListener);
    }

    public ObjectAnimator getDisappearAnimator() {
        if (!mIsInitialized || !mDrawValuesReady || mDisappearAnimator == null) {
            Log.e(TAG, "RadialTextView was not ready for animation.");
            return null;
        }

        return mDisappearAnimator;
    }

    public ObjectAnimator getReappearAnimator() {
        if (!mIsInitialized || !mDrawValuesReady || mReappearAnimator == null) {
            Log.e(TAG, "RadialTextView was not ready for animation.");
            return null;
        }

        return mReappearAnimator;
    }

    private class InvalidateUpdateListener implements AnimatorUpdateListener {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            RadialTextsView.this.invalidate();
        }
    }

    interface SelectionValidator {
        boolean isValidSelection(int selection);
    }
}
