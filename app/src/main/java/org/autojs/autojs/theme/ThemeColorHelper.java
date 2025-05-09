package org.autojs.autojs.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import com.afollestad.materialdialogs.internal.MDTintHelper;
import com.google.android.material.textfield.TextInputLayout;
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

    public static void setThemeColorPrimary(Menu menu, Context context, @ColorInt int backgroundColor, @IdRes int checkedIdRes) {
        setColorPrimary(menu, context, backgroundColor, ThemeColorManager.getColorPrimary(), true, checkedIdRes);
    }

    public static void setThemeColorPrimary(Menu menu, Context context, boolean contrastMatters, @IdRes int checkedIdRes) {
        setColorPrimary(menu, context, ThemeColorManager.getColorPrimary(), contrastMatters, checkedIdRes);
    }

    public static void setColorPrimary(Menu menu, Context ctx, @ColorInt int color, boolean contrastMatters, @IdRes int checkedIdRes) {
        int bg = ctx.getColor(R.color.window_background);
        setColorPrimary(menu, ctx, bg, color, contrastMatters, checkedIdRes);
    }

    public static void setColorPrimary(Menu menu, Context ctx, @ColorInt int backgroundColor, @ColorInt int referenceColor, boolean contrastMatters, @IdRes int checkedIdRes) {
        int checkedColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(backgroundColor, referenceColor, 2.3f)
                : referenceColor;
        int uncheckedColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(backgroundColor, referenceColor, 1.9f, 0.6f)
                : ColorUtils.applyAlpha(referenceColor, 0.6f);

        Drawable checked = tintDrawable(
                AppCompatResources.getDrawable(ctx, R.drawable.btn_radio_on_mtrl).mutate(),
                checkedColor);

        Drawable unchecked = tintDrawable(
                AppCompatResources.getDrawable(ctx, R.drawable.btn_radio_off_mtrl).mutate(),
                uncheckedColor);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setIcon(item.getItemId() == checkedIdRes ? checked : unchecked);
        }
    }

    private static Drawable tintDrawable(Drawable drawable, int color) {
        Drawable wrapped = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(wrapped, color);
        return wrapped;
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

    public static void setThemeColorPrimary(SeekBar seekBar, boolean contrastMatters) {
        setColorPrimary(seekBar, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setColorPrimary(SeekBar seekBar, int color, boolean contrastMatters) {
        int primaryColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(seekBar.getContext().getColor(R.color.window_background), color, 2.3)
                : color;
        int secondaryColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(seekBar.getContext().getColor(R.color.window_background), color, 1.9)
                : color;
        seekBar.setThumbTintList(ColorStateList.valueOf(primaryColor));
        seekBar.setProgressTintList(ColorStateList.valueOf(secondaryColor));
    }

    public static void setThemeColorPrimary(CheckBox checkBox, int backgroundColor) {
        setColorPrimary(checkBox, backgroundColor, ThemeColorManager.getColorPrimary());
    }

    public static void setColorPrimary(CheckBox checkBox, int backgroundColor, int referenceColor) {
        int checkedColor = ColorUtils.adjustColorForContrast(backgroundColor, referenceColor, 2.3);
        int uncheckedColor = ColorUtils.adjustColorForContrast(backgroundColor, referenceColor, 1.9, 0.6);
        DrawableCompat.setTintList(DrawableCompat.wrap(checkBox.getButtonDrawable()), new ColorStateList(SWITCH_STATES, new int[]{checkedColor, uncheckedColor}));
    }

    public static void setColorPrimary(CheckBox checkBox, int color, boolean contrastMatters) {
        var button = ((CompoundButton) checkBox);
        setColorPrimary(button, color, contrastMatters);
    }

    public static void setThemeColorPrimary(CompoundButton button, boolean contrastMatters) {
        setColorPrimary(button, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setColorPrimary(CompoundButton button, int color, boolean contrastMatters) {
        int checkedColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(button.getContext().getColor(R.color.window_background), color, 2.3)
                : color;
        int uncheckedColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(button.getContext().getColor(R.color.window_background), color, 1.9, 0.6)
                : ColorUtils.applyAlpha(color, 0.6);
        DrawableCompat.setTintList(DrawableCompat.wrap(button.getButtonDrawable()), new ColorStateList(SWITCH_STATES, new int[]{checkedColor, uncheckedColor}));
    }

    public static void setThemeColorPrimary(AppCompatImageView imageView, boolean contrastMatters) {
        setColorPrimary(imageView, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setColorPrimary(AppCompatImageView imageView, int color, boolean contrastMatters) {
        if (contrastMatters) {
            color = ColorUtils.adjustColorForContrast(imageView.getContext().getColor(R.color.window_background), color, 2.3);
        }
        Drawable drawable = imageView.getDrawable();
        if (drawable != null) {
            // noinspection deprecation
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
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

    public static void setThemeColorPrimary(TextInputLayout textInputLayout, boolean contrastMatters) {
        setColorPrimary(textInputLayout, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setColorPrimary(TextInputLayout textInputLayout, int color, boolean contrastMatters) {
        EditText editText = textInputLayout.getEditText();
        if (editText != null) {
            setThemeColorPrimary(editText, contrastMatters);
        }
        int tintColor = contrastMatters
                ? ColorUtils.adjustColorForContrast(textInputLayout.getContext().getColor(R.color.window_background), color, 3.6)
                : color;
        textInputLayout.setHintTextColor(ColorStateList.valueOf(tintColor));
    }

    public static void setThemeColorPrimary(EditText editText, boolean contrastMatters) {
        setColorPrimary(editText, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setColorPrimary(EditText editText, int color, boolean contrastMatters) {
        if (contrastMatters) {
            color = ColorUtils.adjustColorForContrast(editText.getContext().getColor(R.color.window_background), color, 2.3);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Drawable textCursorDrawable = editText.getTextCursorDrawable();
            textCursorDrawable.setTint(color);
            editText.setTextCursorDrawable(textCursorDrawable);

            Drawable textSelectHandle = editText.getTextSelectHandle();
            textSelectHandle.setTint(color);
            editText.setTextSelectHandle(textSelectHandle);

            Drawable textSelectHandleLeft = editText.getTextSelectHandleLeft();
            textSelectHandleLeft.setTint(color);
            editText.setTextSelectHandleLeft(textSelectHandleLeft);

            Drawable textSelectHandleRight = editText.getTextSelectHandleRight();
            textSelectHandleRight.setTint(color);
            editText.setTextSelectHandleRight(textSelectHandleRight);
        }
        MDTintHelper.setTint(editText, color);
    }

    public static void setThemeColorPrimary(TextView textView, boolean contrastMatters) {
        setColorPrimary(textView, ThemeColorManager.getColorPrimary(), contrastMatters);
    }

    public static void setColorPrimary(TextView textView, int color, boolean contrastMatters) {
        Context context = textView.getContext();
        if (contrastMatters) {
            color = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), color, 2.3);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textView.setTextSelectHandle(tintDrawable(textView.getTextSelectHandle(), color));
            textView.setTextSelectHandleLeft(tintDrawable(textView.getTextSelectHandleLeft(), color));
            textView.setTextSelectHandleRight(tintDrawable(textView.getTextSelectHandleRight(), color));
        }
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
        int checkedColor = ColorUtils.adjustColorForContrast(background, color, 2.3);
        int uncheckedColor = ColorUtils.adjustColorForContrast(background, color, 1.9, 0.6);
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
