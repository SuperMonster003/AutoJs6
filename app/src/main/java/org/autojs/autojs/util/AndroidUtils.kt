package org.autojs.autojs.util

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import org.autojs.autojs.apkbuilder.ApkBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.zip.ZipFile

/**
 * Created by SuperMonster003 on Jun 1, 2022.
 */
object AndroidUtils {

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

    @JvmStatic
    val deviceFilteredAbiList
        get() = mFullAbiList.filter(Build.SUPPORTED_ABIS::contains)

    @JvmStatic
    val appMainAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull { deviceFilteredAbiList.contains(it) } ?: throw IllegalArgumentException(
            "None of the device ABIs is supported as the main ABI: [${Build.SUPPORTED_ABIS.joinToString(",")}]"
        )

    @JvmStatic
    val appSupportedAbiList: List<String>
        get() = try {
            ZipFile(ApkBuilder.appApkFile.path).entries().asSequence().mapNotNull { entry ->
                Regex(".*lib/(.+?)/.+").find(entry.name)?.groupValues?.get(1)
            }.toSet().toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

    object Abi {
        const val ARM64_V8A = "arm64-v8a"
        const val X86_64 = "x86_64"
        const val MIPS64 = "mips64"
        const val MIPS = "mips"
        const val ARMEABI_V7A = "armeabi-v7a"
        const val X86 = "x86"
        const val ARMEABI = "armeabi"
    }

    @SuppressLint("PrivateApi")
    object SystemProperties {

        private val TAG = SystemProperties::class.java.simpleName

        private val getMethod: Method by lazy { findMethod("get", String::class.java) }
        private val getIntMethod: Method by lazy { findMethod("getInt", String::class.java) }
        private val getBooleanMethod: Method by lazy { findMethod("getBoolean", String::class.java) }

        private fun findMethod(methodName: String, vararg parameterTypes: Class<*>): Method {
            return try {
                Class.forName("android.os.SystemProperties").getMethod(methodName, *parameterTypes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to find method: $methodName", e)
                throw NoSuchMethodException("Method $methodName not found in android.os.SystemProperties").initCause(e)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun get(propName: String, defaultValue: String? = null): String? {
            return try {
                getMethod.invoke(null, propName) as? String ?: defaultValue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get property: $propName", e)
                defaultValue
            }
        }

        @JvmStatic
        @JvmOverloads
        fun getInt(propName: String, defaultValue: Int? = null): Int {
            defaultValue ?: throw IllegalArgumentException("A default Int value must be specified when calling $TAG.${::getInt.name}")
            return try {
                getIntMethod.invoke(null, propName, defaultValue) as? Int ?: defaultValue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Int property: $propName", e)
                defaultValue
            }
        }

        @JvmStatic
        @JvmOverloads
        fun getBoolean(propName: String, defaultValue: Boolean? = null): Boolean {
            defaultValue ?: throw IllegalArgumentException("A default Boolean value must be specified when calling $TAG.${::getBoolean.name}")
            return try {
                getBooleanMethod.invoke(null, propName, defaultValue) as? Boolean ?: defaultValue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Boolean property: $propName", e)
                defaultValue
            }
        }

        @JvmStatic
        fun getAll(): Map<String, String> {
            val systemProperties = mutableMapOf<String, String>()
            var process: Process? = null
            var reader: BufferedReader? = null
            try {
                process = Runtime.getRuntime().exec("getprop")
                reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split("]: [")
                    if (parts.size == 2) {
                        val key = parts[0].substring(1)   // 去掉前面的 '['
                        val value = parts[1].substring(0, parts[1].length - 1)  // 去掉后面的 ']'
                        systemProperties[key] = value
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get all system properties", e)
            } finally {
                try {
                    reader?.close()
                    process?.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to close resources", e)
                }
            }
            return systemProperties
        }

    }

}