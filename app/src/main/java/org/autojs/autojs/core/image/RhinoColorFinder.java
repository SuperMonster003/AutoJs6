package org.autojs.autojs.core.image;

import android.graphics.Color;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptableObject;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.Arrays;

/**
 * Created by SuperMonster003 on Jan 9, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Jan 9, 2024.
public class RhinoColorFinder extends ColorFinder {

    public static final int DEFAULT_COLOR_THRESHOLD = 4;

    public RhinoColorFinder(ScreenMetrics screenMetrics) {
        super(screenMetrics);
    }

    private Rect buildRegion(ImageWrapper imageWrapper, NativeObject options) {
        if (!ScriptableObject.hasProperty(options, "region")) {
            return null;
        }
        NativeArray region = (NativeArray) ScriptableObject.getProperty(options, "region");
        Number xRaw = (Number) getOrNull(region, 0);
        int x = xRaw != null ? xRaw.intValue() : 0;
        Number yRaw = (Number) getOrNull(region, 1);
        int y = yRaw != null ? yRaw.intValue() : 0;
        Number widthRaw = (Number) getOrNull(region, 2);
        int width = widthRaw != null ? widthRaw.intValue() : imageWrapper.getWidth() - x;
        Number heightRaw = (Number) getOrNull(region, 3);
        int height = heightRaw != null ? heightRaw.intValue() : imageWrapper.getHeight() - y;
        if (x >= 0 && y >= 0 && x + width <= imageWrapper.getWidth() && y + height <= imageWrapper.getHeight()) {
            return new Rect(x, y, width, height);
        }
        throw new IllegalArgumentException("out of region: " +
                "region = [" + Arrays.toString(new Integer[]{x, y, width, height}) + "], " +
                "image.size = [" + imageWrapper.getWidth() + ", " + imageWrapper.getHeight() + ']');
    }

    private Object getOrNull(NativeArray nativeArray, int index) {
        return nativeArray.has(index, nativeArray) ? nativeArray.get(index) : null;
    }

    private int parseColor(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o instanceof String) {
            return Color.parseColor((String) o);
        }
        throw new IllegalArgumentException("illegal color: " + ScriptRuntime.toString(o));
    }

    public Point findColorRhino(ImageWrapper img, Object color, ScriptableObject options) {
        if (options instanceof NativeObject opt) {
            int threshold;
            if (ScriptableObject.hasProperty(opt, "threshold")) {
                threshold = ScriptRuntime.toInt32(ScriptableObject.getProperty(opt, "threshold"));
            } else if (ScriptableObject.hasProperty(opt, "similarity")) {
                threshold = (int) ((1d - ((Number) ScriptableObject.getProperty(opt, "similarity")).doubleValue()) * 255d);
            } else {
                threshold = DEFAULT_COLOR_THRESHOLD;
            }
            return super.findPointByColor(img, parseColor(color), threshold, buildRegion(img, opt));
        }
        throw new IllegalArgumentException("argument 'options' of findMultiColors() must be a object");
    }

    public Point findMultiColorsRhino(ImageWrapper img, Object color, ScriptableObject paths, ScriptableObject options) {
        if (!(paths instanceof NativeArray)) {
            throw new IllegalArgumentException("argument 'paths' of findMultiColors() must be a array");
        }
        if (options instanceof NativeObject opt) {
            NativeArray pathList = (NativeArray) paths;
            int[] array = new int[(int) (pathList.getLength() * 3)];
            int len = (int) pathList.getLength();
            for (int i = 0; i < len; i++) {
                NativeArray path = (NativeArray) pathList.get(i);
                array[i * 3] = ((Number) path.get(0)).intValue();
                array[i * 3 + 1] = ((Number) path.get(1)).intValue();
                if (path.get(2) == null) {
                    throw new IllegalArgumentException("element at index 2 of color array is null");
                }
                array[i * 3 + 2] = parseColor(path.get(2));
            }
            int threshold = ScriptableObject.hasProperty(opt, "threshold") ? ScriptRuntime.toInt32(ScriptableObject.getProperty(opt, "threshold")) : DEFAULT_COLOR_THRESHOLD;
            return super.findPointByColors(img, parseColor(color), threshold, buildRegion(img, opt), array);
        }
        throw new IllegalArgumentException("argument 'options' of findMultiColors() must be a object");
    }

}
