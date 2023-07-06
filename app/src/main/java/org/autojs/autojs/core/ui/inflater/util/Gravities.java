package org.autojs.autojs.core.ui.inflater.util;

import android.view.Gravity;

import org.autojs.autojs.pref.Language;

/**
 * Created by Stardust on 2017/11/3.
 */
public class Gravities {

    public static int parse(String g) {
        int gravity = Gravity.NO_GRAVITY;
        String[] parts = g.toLowerCase(Language.getPrefLanguage().getLocale()).split("[|]");
        for (String part : parts) {
            switch (part) {
                case "center" -> gravity = gravity | Gravity.CENTER;
                case "left", "textStart", "start" -> gravity = gravity | Gravity.START;
                case "right", "textEnd", "end" -> gravity = gravity | Gravity.END;
                case "top" -> gravity = gravity | Gravity.TOP;
                case "bottom" -> gravity = gravity | Gravity.BOTTOM;
                case "center_horizontal" -> gravity = gravity | Gravity.CENTER_HORIZONTAL;
                case "center_vertical" -> gravity = gravity | Gravity.CENTER_VERTICAL;
            }
        }
        return gravity;
    }



}
