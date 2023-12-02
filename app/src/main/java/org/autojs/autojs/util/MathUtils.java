package org.autojs.autojs.util;

/**
 * Created by Stardust on Nov 26, 2017.
 */
public class MathUtils {

    public static int min(int... ints) {
        int min = ints[0];
        for (int i = 1; i < ints.length; i++) {
            min = Math.min(ints[i], min);
        }
        return min;
    }
}
