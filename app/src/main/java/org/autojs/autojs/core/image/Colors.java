package org.autojs.autojs.core.image;

import android.graphics.Color;
import android.os.Build;

import androidx.annotation.ColorInt;

import org.autojs.autojs.util.ColorUtils;

/**
 * Created by Stardust on 2017/12/31.
 */
public class Colors {

    public int rgb(int red, int green, int blue) {
        return Color.rgb(red, green, blue);
    }

    public int argb(int alpha, int red, int green, int blue) {
        return Color.argb(alpha, red, green, blue);
    }

    /**
     * @see <a href="https://www.w3.org/TR/WCAG20/#relativeluminancedef">W3C Recommendation</a>
     */
    public double luminance(@ColorInt int color) {
        // return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        //         ? Color.luminance(Color.pack(color))
        //         : Color.luminance(color);
        return androidx.core.graphics.ColorUtils.calculateLuminance(color);
    }

    public int parseColor(String colorString) {
        return Color.parseColor(colorString);
    }

    public String toString(int color) {
        return ColorUtils.toString(color);
    }

    public void RGBToHSV(int red, int green, int blue, float[] hsv) {
        Color.RGBToHSV(red, green, blue, hsv);
    }

    public void colorToHSV(int color, float[] hsv) {
        Color.colorToHSV(color, hsv);
    }

    public int HSVToColor(float[] hsv) {
        return Color.HSVToColor(hsv);
    }

    public int HSVToColor(int alpha, float[] hsv) {
        return Color.HSVToColor(alpha, hsv);
    }

    public boolean equals(int c1, int c2) {
        return (c1 & 0xffffff) == (c2 & 0xffffff);
    }

    public boolean equals(int c1, String c2) {
        return equals(c1, parseColor(c2));
    }

    public boolean equals(String c1, int c2) {
        return equals(parseColor(c1), c2);
    }

    public boolean equals(String c1, String c2) {
        return equals(parseColor(c1), parseColor(c2));
    }

}
