package org.autojs.autojs.util;

import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.pio.PFiles;

/**
 * Created by Stardust on 2018/2/12.
 */
public class MimeTypesUtils {

    @Nullable
    public static String fromFile(String path) {
        String ext = PFiles.getExtension(path);
        return android.text.TextUtils.isEmpty(ext) ? "*/*" : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }

    @NonNull
    public static String fromFileOr(String path, String defaultType) {
        String mimeType = fromFile(path);
        return mimeType == null ? defaultType : mimeType;
    }

}
