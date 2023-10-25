package org.autojs.autojs.util;

/**
 * Created by Stardust on 2017/4/3.
 * Modified by SuperMonster003 as of Feb 5, 2022.
 */
public class SdkVersionUtils {

    private static final String[] SDK_VERSIONS = {
            /* placeholder */ null,
            /* api: 1 */ "1.0",
            /* api: 2 */ "1.1",
            /* api: 3 */ "1.5",
            /* api: 4 */ "1.6",
            /* api: 5 */ "2.0",
            /* api: 6 */ "2.0.1",
            /* api: 7 */ "2.1.x",
            /* api: 8 */ "2.2.x",
            /* api: 9 */ "2.3",
            /* api: 10 */ "2.3.3",
            /* api: 11 */ "3.0.x",
            /* api: 12 */ "3.1.x",
            /* api: 13 */ "3.2",
            /* api: 14 */ "4.0",
            /* api: 15 */ "4.0.3",
            /* api: 16 */ "4.1",
            /* api: 17 */ "4.2",
            /* api: 18 */ "4.3",
            /* api: 19 */ "4.4.2",
            /* api: 20 */ "4.4W",
            /* api: 21 */ "5.0",
            /* api: 22 */ "5.1",
            /* api: 23 */ "6.0",
            /* api: 24 */ "7.0",
            /* api: 25 */ "7.1",
            /* api: 26 */ "8.0",
            /* api: 27 */ "8.1",
            /* api: 28 */ "9",
            /* api: 29 */ "10",
            /* api: 30 */ "11",
            /* api: 31 */ "12",
            /* api: 32 */ "12.1",
            /* api: 33 */ "13",
            /* api: 34 */ "14"
    };

    public static String sdkIntToString(int i) {
        return i >= 1 && i <= SDK_VERSIONS.length ? SDK_VERSIONS[i] : "Unknown";
    }

}
