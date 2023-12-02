package org.autojs.autojs.util

import android.os.Build
import org.autojs.autojs.apkbuilder.ApkBuilder.Companion.appApkFile
import java.util.stream.Collectors
import java.util.zip.ZipFile

/**
 * Created by SuperMonster003 on Jun 1, 2022.
 */
object AndroidUtils {

    object Abi {
        const val ARM64_V8A = "arm64-v8a"
        const val X86_64 = "x86_64"
        const val MIPS64 = "mips64"
        const val MIPS = "mips"
        const val ARMEABI_V7A = "armeabi-v7a"
        const val X86 = "x86"
        const val ARMEABI = "armeabi"
    }

    // @OrderMatters by SuperMonster003 on Jun 1, 2022.
    private val mFullAbiList = listOf(
        /* 64-bit */ /* Reserved for ARMv9 :) */
        /* 64-bit */ Abi.ARM64_V8A,
        /* 64-bit */ Abi.X86_64,
        /* 64-bit */ Abi.MIPS64, // removed since NDK r17
        /* 32-bit */ Abi.MIPS, // removed since NDK r17
        /* 32-bit */ Abi.ARMEABI_V7A,
        /* 32-bit */ Abi.X86,
        /* 32-bit */ Abi.ARMEABI, // removed since NDK r17
    )

    private fun getFilteredAbiList(reference: List<String>): List<String> {
        return mFullAbiList.stream().filter { o: String -> reference.contains(o) }.collect(Collectors.toList())
    }

    @JvmStatic
    val deviceFilteredAbiList: List<String>
        get() = getFilteredAbiList(listOf(*Build.SUPPORTED_ABIS))

    @JvmField
    val appMainAbi: String = Build.SUPPORTED_ABIS[0]

    @JvmStatic
    val appSupportedAbiList: List<String>
        get() {
            val abis = mutableListOf<String>()

            try {
                val entries = ZipFile(appApkFile.path).entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.contains("/lib")) {
                        val abi = entry.name.replace(Regex(".*lib/(.+?)/.+"), "$1")
                        if (!abis.contains(abi)) {
                            abis.add(abi)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return abis.toList()
        }

}