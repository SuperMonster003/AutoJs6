package org.autojs.autojs.core.ui.inflater.util;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Stardust on Nov 3, 2017.
 */
public class Ids {

    private static final AtomicInteger maxId = new AtomicInteger(20161209);
    private static final HashMap<String, Integer> ids = new HashMap<>();

    public static String parseIdName(String idName) {
        if (idName.startsWith("@+id/")) {
            return idName.substring(5);
        } else if (idName.startsWith("@id/")) {
            return idName.substring(4);
        }
        return idName;
    }

    public static int parse(String name) {
        name = parseIdName(name);
        Integer id = ids.get(name);
        if (id == null) {
            id = maxId.incrementAndGet();
            ids.put(name, id);
        }
        return id;
    }
}
