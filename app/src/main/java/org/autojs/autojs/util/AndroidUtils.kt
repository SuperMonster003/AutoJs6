package org.autojs.autojs.util

import android.os.Build
import java.util.stream.Collectors

/**
 * Created by SuperMonster003 on Jun 1, 2022.
 */
object AndroidUtils {

    // @OrderMatters by SuperMonster003 on Jun 1, 2022.
    private val mFullAbiList = listOf(
        /* 64-bit */ /* Reserved for ARMv9 :) */
        /* 64-bit */ "arm64-v8a",
        /* 64-bit */ "x86_64",
        /* 64-bit */ "mips64", // removed since NDK r17
        /* 32-bit */ "mips", // removed since NDK r17
        /* 32-bit */ "armeabi-v7a",
        /* 32-bit */ "x86",
        /* 32-bit */ "armeabi" // removed since NDK r17
    )

    private fun getFilteredAbiList(reference: List<String>): List<String> {
        return mFullAbiList.stream().filter { o: String -> reference.contains(o) }.collect(Collectors.toList())
    }

    @JvmStatic
    val deviceFilteredAbiList: List<String>
        get() = getFilteredAbiList(listOf(*Build.SUPPORTED_ABIS))

}