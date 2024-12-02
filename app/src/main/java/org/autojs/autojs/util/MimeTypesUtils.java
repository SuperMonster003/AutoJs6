package org.autojs.autojs.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.annotation.ReservedForCompatibility;
import org.autojs.autojs.runtime.api.Mime;

/**
 * Created by Stardust on Feb 12, 2018.
 * Modified by SuperMonster003 as of Apr 11, 2024.
 */
@ReservedForCompatibility
public class MimeTypesUtils {

    @Nullable
    @ReservedForCompatibility
    public static String fromFile(String path) {
        return Mime.fromFile(path);
    }

    @NonNull
    @ReservedForCompatibility
    public static String fromFileOr(String path, @Nullable String defaultType) {
        return Mime.fromFileOr(path, defaultType);
    }

}
