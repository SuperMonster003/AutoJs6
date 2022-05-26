package com.stardust.autojs.core.ui.inflater.inflaters;

import android.content.res.ColorStateList;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.stardust.autojs.core.ui.inflater.ResourceParser;
import com.stardust.autojs.core.ui.inflater.util.Colors;
import com.stardust.autojs.core.ui.inflater.util.Dimensions;

import java.util.Map;

/**
 * Created by Stardust on 2017/11/29.
 */
public class ProgressBarInflater<V extends ProgressBar> extends BaseViewInflater<V> {

    public ProgressBarInflater(ResourceParser resourceParser) {
        super(resourceParser);
    }

    @Override
    public boolean setAttr(V view, String attr, String value, ViewGroup parent, Map<String, String> attrs) {
        switch (attr) {
            case "animationResolution":
            case "mirrorForRtl":
            case "min":
            case "maxWidth":
            case "maxHeight":
            case "interpolato":
            case "indeterminateOnly":
            case "indeterminateDuration":
            case "indeterminateBehavior":
                Exceptions.unsupports(view, attr, value);
                break;
            case "indeterminate":
                view.setIndeterminate(Boolean.parseBoolean(value));
                break;
            case "indeterminateDrawable":
                view.setIndeterminateDrawable(getDrawables().parse(view, value));
                break;
            case "indeterminateTint":
                view.setIndeterminateTintList(ColorStateList.valueOf(Colors.parse(view, value)));
                break;
            case "indeterminateTintMode":
                view.setIndeterminateTintMode(TINT_MODES.get(value));
                break;
            case "max":
                view.setMax(Integer.parseInt(value));
                break;
            case "minHeigh":
                view.setMinimumHeight(Dimensions.parseToIntPixel(value, view));
                break;
            case "minWidth":
                view.setMinimumWidth(Dimensions.parseToIntPixel(value, view));
                break;
            case "progress":
                view.setProgress(Integer.parseInt(value));
                break;
            case "progressBackgroundTint":
                view.setProgressBackgroundTintList(ColorStateList.valueOf(Colors.parse(view, value)));
                break;
            case "progressBackgroundTintMode":
                view.setProgressBackgroundTintMode(TINT_MODES.get(value));
                break;
            case "progressDrawable":
                view.setProgressDrawable(getDrawables().parse(view, value));
                break;
            case "progressTint":
                view.setProgressTintList(ColorStateList.valueOf(Colors.parse(view, value)));
                break;
            case "progressTintMode":
                view.setProgressTintMode(TINT_MODES.get(value));
                break;
            case "secondaryProgress":
                view.setSecondaryProgress(Integer.parseInt(value));
                break;
            case "secondaryProgressTint":
                view.setSecondaryProgressTintList(ColorStateList.valueOf(Colors.parse(view, value)));
                break;
            case "secondaryProgressTintMode":
                view.setSecondaryProgressTintMode(TINT_MODES.get(value));
                break;
            default:
                return super.setAttr(view, attr, value, parent, attrs);
        }
        return true;
    }
}
