package org.autojs.autojs.core.ui.inflater.util;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.Files;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Stardust on 2017/11/4.
 */
public class Strings {

    public static String parse(Context context, String str) {
        if (str.startsWith("@string/")) {
            Resources resources = context.getResources();
            int stringId = resources.getIdentifier(str, "string", context.getPackageName());
            return resources.getString(stringId);
        }
        return str;
    }

    public static String parse(View view, String str) {
        return parse(view.getContext(), str);
    }

    @Nullable
    public static String parsePath(@NotNull View view, @NotNull String path) {
        Context context = view.getContext();
        if (path.startsWith("@raw/")) {
            Resources resources = context.getResources();
            int rawId = resources.getIdentifier(path, "raw", context.getPackageName());
            if (rawId == 0) {
                ScriptRuntime.popException("Invalid resource: " + path);
                return null;
            }
            return "android.resource://" + context.getPackageName() + "/" + rawId;
        }
        Files files = AutoJs.getInstance().getRuntime().files;
        String nicePath = files.path(path);
        if (!files.exists(nicePath)) {
            ScriptRuntime.popException("Invalid path: " + path);
            return null;
        }
        return nicePath;
    }

    public static int parseAnimation(@NotNull View view, @NotNull String anim) {
        Context context = view.getContext();
        if (anim.startsWith("@anim/") || anim.startsWith("@android:anim/")) {
            Resources resources = context.getResources();
            int animId = resources.getIdentifier(anim, "anim", context.getPackageName());
            if (animId != 0) {
                return animId;
            }
            ScriptRuntime.popException("Invalid resource: " + anim);
        }
        return 0;
    }

}
