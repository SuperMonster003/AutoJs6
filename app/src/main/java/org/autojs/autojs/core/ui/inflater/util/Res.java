package org.autojs.autojs.core.ui.inflater.util;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;

/**
 * Created by Stardust on Nov 5, 2017.
 */
public class Res {

    public static int parseStyle(View view, String value) {
        return parseStyle(view.getContext(), value);
    }

    public static int parseStyle(Context context, String value) {
        if (value.startsWith("?")) {
            String attribute = value.substring(1);
            String packageName = context.getPackageName();
            if (attribute.startsWith("android:")) {
                packageName = "android";
                attribute = attribute.substring("android:".length());
            }
            if (attribute.startsWith("attr/")) {
                attribute = attribute.substring("attr/".length());
            }
            int attrId = context.getResources().getIdentifier(attribute, "attr", packageName);
            TypedValue typedValue = new TypedValue();
            if (attrId != 0 && context.getTheme().resolveAttribute(attrId, typedValue, true)) {
                return typedValue.resourceId;
            }
            return 0;
        }
        String packageName = context.getPackageName();
        if (value.startsWith("@android:style/")) {
            packageName = "android";
            value = value.substring("@android:style/".length());
        } else if (value.startsWith("@style/")) {
            value = value.substring("@style/".length());
        }
        return context.getResources().getIdentifier(value, "style", packageName);
    }

}
