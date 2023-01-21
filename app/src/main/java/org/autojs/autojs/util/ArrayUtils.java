package org.autojs.autojs.util;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;

/**
 * Created by Stardust on 2017/5/8.
 */
public class ArrayUtils {

    @NonNull
    @Contract(pure = true)
    public static Integer[] box(@NonNull int[] array) {
        Integer[] box = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            box[i] = array[i];
        }
        return box;
    }


    @NonNull
    @Contract(pure = true)
    public static int[] unbox(@NonNull Integer[] array) {
        int[] unbox = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            unbox[i] = array[i];
        }
        return unbox;
    }

    @NonNull
    public static String[] toStringArray(@NonNull List<?> list) {
        int i = 0;
        String[] str = new String[list.size()];
        for (Object o : list) {
            str[i] = o == null ? null : o.toString();
        }
        return str;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public static <T> T[] merge(@NonNull T[] a1, @NonNull T[] a2) {
        T[] a = (T[]) Array.newInstance(Objects.requireNonNull(a1.getClass().getComponentType()), a1.length + a2.length);
        System.arraycopy(a1, 0, a, 0, a1.length);
        System.arraycopy(a2, 0, a, a1.length, a2.length);
        return a;
    }

}
