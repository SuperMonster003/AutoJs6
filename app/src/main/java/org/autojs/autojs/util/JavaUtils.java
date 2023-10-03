package org.autojs.autojs.util;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

/**
 * Created by SuperMonster003 on May 4, 2022.
 * Mainly for JavaScript modules.
 */
public class JavaUtils {

    @NonNull
    public static Class<?> getClass(@NonNull Class<?> clazz) {
        return clazz;
    }

    @NonNull
    public static Class<?> getClass(@NonNull Object o) {
        return o.getClass();
    }

    @NonNull
    public static String getClassName(@NonNull Class<?> Clazz) {
        return Clazz.getName();
    }

    @NonNull
    public static String getClassName(@NonNull Object o) {
        return o.getClass().getName();
    }

    public static Object reflect(Class<?> clazz, String name) throws NoSuchFieldException, IllegalAccessException {
        return reflect(clazz, name, null);
    }

    public static Object reflect(Class<?> clazz, String name, Object obj) throws NoSuchFieldException, IllegalAccessException {
        Field filed = clazz.getDeclaredField(name);
        filed.setAccessible(true);
        return filed.get(obj);
    }

}
