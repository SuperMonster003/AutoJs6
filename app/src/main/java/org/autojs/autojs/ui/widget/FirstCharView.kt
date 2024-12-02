package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import org.autojs.autojs.theme.ThemeColorManagerCompat;
import org.autojs.autojs6.R;

@SuppressWarnings("UnusedReturnValue")
public class FirstCharView extends TextView {

    private GradientDrawable mBackground;

    private final int COLOR_RES_DAY_NIGHT = R.color.day_night;
    private final int COLOR_RES_NIGHT_DAY = R.color.night_day;
    private final int COLOR_TRANSPARENT = Color.TRANSPARENT;

    private GradientDrawable background() {
        if (mBackground == null) {
            mBackground = (GradientDrawable) getBackground();
        }
        return mBackground;
    }

    public FirstCharView(Context context) {
        super(context);
    }

    public FirstCharView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FirstCharView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FirstCharView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private int convertColorResToInt(@ColorRes int colorRes) {
        return getContext().getColor(colorRes);
    }

    private int getStrokeWidth() {
        return getContext().getResources().getDimensionPixelSize(R.dimen.first_char_view_stroke_width);
    }

    public FirstCharView setIconText(CharSequence text) {
        setText(text);
        return this;
    }

    public FirstCharView setIconTextColor(@ColorInt int color) {
        setTextColor(color);
        return this;
    }

    public FirstCharView setIconTextColorRes(@ColorRes int colorRes) {
        setTextColor(convertColorResToInt(colorRes));
        return this;
    }

    public FirstCharView setIconTextThemeColor() {
        return setIconTextColor(ThemeColorManagerCompat.getColorPrimary());
    }

    public FirstCharView setIconTextColorDayNight() {
        return setIconTextColorRes(COLOR_RES_DAY_NIGHT);
    }

    public FirstCharView setIconTextColorNightDay() {
        return setIconTextColorRes(COLOR_RES_NIGHT_DAY);
    }

    public FirstCharView setStrokeColor(@ColorInt int color) {
        background().setStroke(getStrokeWidth(), color);
        return this;
    }

    public FirstCharView setStrokeColorRes(@ColorRes int colorRes) {
        return setStrokeColor(convertColorResToInt(colorRes));
    }

    public FirstCharView setStrokeThemeColor() {
        return setStrokeColor(ThemeColorManagerCompat.getColorPrimary());
    }

    public FirstCharView setStrokeColorDayNight() {
        return setStrokeColorRes(COLOR_RES_DAY_NIGHT);
    }

    public FirstCharView setStrokeColorNightDay() {
        return setStrokeColorRes(COLOR_RES_NIGHT_DAY);
    }

    public FirstCharView setFillColor(@ColorInt int color) {
        background().setColor(color);
        return this;
    }

    public FirstCharView setFillColorRes(@ColorRes int colorRes) {
        return setFillColor(convertColorResToInt(colorRes));
    }

    public FirstCharView setFillThemeColor() {
        return setFillColor(ThemeColorManagerCompat.getColorPrimary());
    }

    public FirstCharView setFillTransparent() {
        return setFillColor(COLOR_TRANSPARENT);
    }

    public FirstCharView setFillColorDayNight() {
        return setFillColorRes(COLOR_RES_DAY_NIGHT);
    }

    public FirstCharView setFillColorNightDay() {
        return setFillColorRes(COLOR_RES_NIGHT_DAY);
    }

}
