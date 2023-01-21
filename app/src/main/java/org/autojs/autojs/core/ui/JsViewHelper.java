package org.autojs.autojs.core.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.autojs.autojs.core.ui.inflater.util.Ids;

/**
 * Created by Stardust on 2017/5/14.
 */
public class JsViewHelper {

    @Nullable
    public static View findViewByStringId(View view, String id) {
        View result = view.findViewById(Ids.parse(id));
        if (result != null)
            return result;
        if (!(view instanceof ViewGroup group)) {
            return null;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            result = findViewByStringId(group.getChildAt(i), id);
            if (result != null)
                return result;
        }
        return null;
    }

}
