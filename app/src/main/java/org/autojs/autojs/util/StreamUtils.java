package org.autojs.autojs.util;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by SuperMonster003 on Jun 3, 2022.
 */
public class StreamUtils {

    public static void closeSilently(@Nullable Closeable... closeable) {
        if (closeable != null) {
            for (Closeable c : closeable) {
                try {
                    c.close();
                } catch (IOException ignored) {
                    // Ignored.
                }
            }
        }
    }

}
