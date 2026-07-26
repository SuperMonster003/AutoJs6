package org.autojs.autojs.core.ui.inflater.util;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Stardust on Nov 3, 2017.
 */
public class Ids {

    // View.setTag(int, Object) rejects framework and package-less IDs (package byte 0x00/0x01).
    // A private package byte keeps script-generated IDs valid for both View IDs and keyed tags.
    private static final AtomicInteger maxId = new AtomicInteger(0x02000000);
    private static final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();

    public static String parseIdName(String idName) {
        if (idName.startsWith("@+id/")) {
            return idName.substring(5);
        } else if (idName.startsWith("@id/")) {
            return idName.substring(4);
        }
        return idName;
    }

    public static int parse(String name) {
        if (name.startsWith("@android:id/")) {
            String fieldName = name.substring("@android:id/".length());
            try {
                Field field = android.R.id.class.getField(fieldName);
                return field.getInt(null);
            } catch (ReflectiveOperationException ignored) {
                name = fieldName;
            }
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException ignored) {
            // Continue with a symbolic ID.
        }
        name = parseIdName(name);
        return ids.computeIfAbsent(name, key -> maxId.incrementAndGet());
    }
}
