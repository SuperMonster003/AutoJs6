package org.autojs.autojs.util;

import androidx.annotation.Nullable;

import java.io.Closeable;

/**
 * Created by SuperMonster003 on Jun 3, 2022.
 * Modified by SuperMonster003 as of Feb 2, 2026.
 */
public class StreamUtils {

    public static void closeSilently(@Nullable Closeable... closeable) {
        if (closeable != null) {
            for (Closeable c : closeable) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                    /* Ignored. */
                }
            }
        }
    }

}
