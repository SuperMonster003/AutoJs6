package com.afollestad.materialdialogs.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import com.afollestad.materialdialogs.R;
import com.afollestad.materialdialogs.util.DialogUtils;

import java.lang.reflect.Field;

/** Tints widgets */
@SuppressLint("PrivateResource")
public class MDTintHelper {

    public static void setTint(@NonNull RadioButton radioButton, @NonNull ColorStateList colors) {
        radioButton.setButtonTintList(colors);
    }

    public static void setTint(@NonNull RadioButton radioButton, @ColorInt int color) {
        final int disabledColor = DialogUtils.getDisabledColor(radioButton.getContext());
        ColorStateList sl =
                new ColorStateList(
                        new int[][]{
                                new int[]{android.R.attr.state_enabled, -android.R.attr.state_checked},
                                new int[]{android.R.attr.state_enabled, android.R.attr.state_checked},
                                new int[]{-android.R.attr.state_enabled, -android.R.attr.state_checked},
                                new int[]{-android.R.attr.state_enabled, android.R.attr.state_checked}
                        },
                        new int[]{
                                DialogUtils.resolveColor(radioButton.getContext(), android.R.attr.colorControlNormal),
                                color,
                                disabledColor,
                                disabledColor
                        });
        setTint(radioButton, sl);
    }

    public static void setTint(@NonNull CheckBox box, @NonNull ColorStateList colors) {
        box.setButtonTintList(colors);
    }

    public static void setTint(@NonNull CheckBox box, @ColorInt int color) {
        final int disabledColor = DialogUtils.getDisabledColor(box.getContext());
        ColorStateList sl =
                new ColorStateList(
                        new int[][]{
                                new int[]{android.R.attr.state_enabled, -android.R.attr.state_checked},
                                new int[]{android.R.attr.state_enabled, android.R.attr.state_checked},
                                new int[]{-android.R.attr.state_enabled, -android.R.attr.state_checked},
                                new int[]{-android.R.attr.state_enabled, android.R.attr.state_checked}
                        },
                        new int[]{
                                DialogUtils.resolveColor(box.getContext(), android.R.attr.colorControlNormal),
                                color,
                                disabledColor,
                                disabledColor
                        });
        setTint(box, sl);
    }

    public static void setTint(@NonNull SeekBar seekBar, @ColorInt int color) {
        ColorStateList s1 = ColorStateList.valueOf(color);
        seekBar.setThumbTintList(s1);
        seekBar.setProgressTintList(s1);
    }

    public static void setTint(@NonNull ProgressBar progressBar, @ColorInt int color) {
        setTint(progressBar, color, false);
    }

    private static void setTint(
            @NonNull ProgressBar progressBar, @ColorInt int color, boolean skipIndeterminate) {
        ColorStateList sl = ColorStateList.valueOf(color);
        progressBar.setProgressTintList(sl);
        progressBar.setSecondaryProgressTintList(sl);
        if (!skipIndeterminate) {
            progressBar.setIndeterminateTintList(sl);
        }
    }

    private static ColorStateList createEditTextColorStateList(@NonNull Context context, @ColorInt int color) {
        int[][] states = new int[3][];
        int[] colors = new int[3];
        int i = 0;
        states[i] = new int[]{-android.R.attr.state_enabled};
        colors[i] = DialogUtils.resolveColor(context, android.R.attr.colorControlNormal);
        i++;
        states[i] = new int[]{-android.R.attr.state_pressed, -android.R.attr.state_focused};
        colors[i] = DialogUtils.resolveColor(context, android.R.attr.colorControlNormal);
        i++;
        states[i] = new int[]{};
        colors[i] = color;
        return new ColorStateList(states, colors);
    }

    public static void setTint(@NonNull EditText editText, @ColorInt int color) {
        ColorStateList editTextColorStateList =
                createEditTextColorStateList(editText.getContext(), color);
        if (editText instanceof AppCompatEditText) {
            // noinspection RestrictedApi
            ((AppCompatEditText) editText).setSupportBackgroundTintList(editTextColorStateList);
        } else {
            editText.setBackgroundTintList(editTextColorStateList);
        }
        setCursorTint(editText, color);
    }

    private static void setCursorTint(@NonNull EditText editText, @ColorInt int color) {
        try {
            Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            fCursorDrawableRes.setAccessible(true);
            int mCursorDrawableRes = fCursorDrawableRes.getInt(editText);
            Field fEditor = TextView.class.getDeclaredField("mEditor");
            fEditor.setAccessible(true);
            Object editor = fEditor.get(editText);
            Class<?> clazz = editor.getClass();
            Field fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");
            fCursorDrawable.setAccessible(true);
            Drawable[] drawables = new Drawable[2];
            drawables[0] = ContextCompat.getDrawable(editText.getContext(), mCursorDrawableRes);
            drawables[1] = ContextCompat.getDrawable(editText.getContext(), mCursorDrawableRes);
            drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
            drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
            fCursorDrawable.set(editor, drawables);
        } catch (NoSuchFieldException e1) {
            Log.d("MDTintHelper", "Device issue with cursor tinting: " + e1.getMessage());
            e1.printStackTrace();
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }
}
