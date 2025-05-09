package org.autojs.autojs.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.Switch;
import androidx.annotation.ColorInt;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;
import org.autojs.autojs.theme.internal.ScrollingViewEdgeGlowColorHelper;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Oct 24, 2016.
 */
public class ThemeColorHelper {

    private static final String TAG = "ThemeColorHelper";
    private static final int[][] SWITCH_STATES = new int[][]{
            new int[]{android.R.attr.state_checked},
            new int[]{-android.R.attr.state_checked},
    };

    private static void setColorPrimary(View v, int themeColor) {
        if (v instanceof ThemeColorMutable) {
            ((ThemeColorMutable) v).setThemeColor(new ThemeColor(themeColor));
            return;
        }
        if (v instanceof AbsListView) {
            ScrollingViewEdgeGlowColorHelper.setEdgeGlowColor((AbsListView) v, themeColor);
            return;
        }
        Log.e(TAG, "Unsupported view: " + v);
    }

    private static void setColorPrimary(ViewGroup viewGroup, int themeColor) {
        setColorPrimary(viewGroup, themeColor, false);
    }

    private static void setColorPrimary(ViewGroup viewGroup, int themeColor, boolean contrastMatters) {
        if (contrastMatters) themeColor = ColorUtils.adjustColorForContrast(viewGroup.getContext().getColor(R.color.window_background), themeColor, 2.3);
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            setColorPrimary(viewGroup.getChildAt(i), themeColor);
        }
    }

    public static void setThemeColorPrimary(TimePickerDialog timePickerDialog, Context context, boolean contrastMatters) {
        setThemeColorPrimary(timePickerDialog, context, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setThemeColorPrimary(TimePickerDialog timePickerDialog, Context context, @ColorInt int color, boolean contrastMatters) {
        if (contrastMatters) {
            color = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), color, 2.3);
        }
        timePickerDialog.setAccentColor(color);
    }

    public static void setThemeColorPrimary(DatePickerDialog datePickerDialog, Context context, boolean contrastMatters) {
        setThemeColorPrimary(datePickerDialog, context, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setThemeColorPrimary(DatePickerDialog datePickerDialog, Context context, @ColorInt int color, boolean contrastMatters) {
        if (contrastMatters) {
            color = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), color, 2.3);
        }
        datePickerDialog.setAccentColor(color);
    }

    public static void setColorPrimary(SwitchCompat switchCompat, int color) {
        setColorPrimary(switchCompat, color, false);
    }

    public static void setColorPrimary(SwitchCompat switchCompat, int color, boolean contrastMatters) {
        int adjustedThumbColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(switchCompat.getContext().getColor(R.color.window_background), color, 2.3)
                : color;
        int adjustedTrackColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(switchCompat.getContext().getColor(R.color.window_background), color, 3.6)
                : color;

        setThumbDrawableTintList(switchCompat.getThumbDrawable(), adjustedThumbColor);
        setTrackDrawableTintList(switchCompat.getTrackDrawable(), adjustedTrackColor, true);
    }

    public static void setColorPrimary(CheckBox checkBox, int color, boolean contrastMatters) {
        int checkedColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(checkBox.getContext().getColor(R.color.window_background), color, 3.6)
                : color;
        int uncheckedColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(checkBox.getContext().getColor(R.color.window_background), color, 2.3)
                : color;
        DrawableCompat.setTintList(DrawableCompat.wrap(checkBox.getButtonDrawable()), new ColorStateList(SWITCH_STATES, new int[]{checkedColor, uncheckedColor}));
    }

    public static void setColorPrimary(Switch sw, int color) {
        setColorPrimary(sw, color, false);
    }

    public static void setColorPrimary(Switch sw, int color, boolean contrastMatters) {
        int adjustedThumbColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(sw.getContext().getColor(R.color.window_background), color, 2.3)
                : color;
        int adjustedTrackColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(sw.getContext().getColor(R.color.window_background), color, 3.6)
                : color;

        setThumbDrawableTintList(sw.getThumbDrawable(), adjustedThumbColor);
        setTrackDrawableTintList(sw.getTrackDrawable(), adjustedTrackColor, false);
    }

    private static void setThumbDrawableTintList(Drawable drawable, int color) {
        int unselectedColor = ViewUtils.isLuminanceLight(color)
                ? Color.parseColor("#616161") // R.color.md_gray_700
                : Color.DKGRAY;
        int[] thumbColors = new int[]{
                color,
                unselectedColor,
        };
        DrawableCompat.setTintList(DrawableCompat.wrap(drawable), new ColorStateList(SWITCH_STATES, thumbColors));
    }

    private static void setTrackDrawableTintList(Drawable drawable, int color, boolean isCompat) {
        int alpha = isCompat ? 0x29 : 0x66;
        int[] trackColors = new int[]{
                makeAlpha(alpha, color),
                makeAlpha(alpha, Color.BLACK),
        };
        DrawableCompat.setTintList(drawable, new ColorStateList(SWITCH_STATES, trackColors));
    }

    private static int makeAlpha(int alpha, int color) {
        return (color & 0xffffff) | (alpha << 24);
    }
    
    public static ColorStateList getThemeColorStateList(Context context) {
        return getColorPrimaryStateList(context, ThemeColorManager.getColorPrimary());
    }

    public static ColorStateList getColorPrimaryStateList(Context context, int color) {
        int background = context.getColor(R.color.window_background);
        int checkedColor = ColorUtils.adjustColorForContrast(background, color, 3.6);
        int uncheckedColor = ColorUtils.adjustColorForContrast(background, color, 2.3);
        return new ColorStateList(SWITCH_STATES, new int[]{checkedColor, uncheckedColor});
    }

    public static void setStatusBarColor(Activity activity, int color) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        ViewUtils.setStatusBarBackgroundColor(activity, color);
    }

    public static void setBackgroundColor(View view, int color) {
        Drawable background = view.getBackground();
        if (background instanceof ShapeDrawable) {
            ((ShapeDrawable) background).getPaint().setColor(color);
        } else if (background instanceof GradientDrawable) {
            ((GradientDrawable) background).setColor(color);
        } else if (background instanceof ColorDrawable) {
            ((ColorDrawable) background).setColor(color);
        } else {
            view.setBackgroundColor(color);
        }
    }
}
