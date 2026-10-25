package org.autojs.autojs.runtime.api.modules.sysprops

import android.annotation.SuppressLint
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
object SystemProperties {

    private const val TAG = "SystemProperties"

    private val systemPropertiesClass: Class<*>? by lazy {
        try {
            Class.forName("android.os.SystemProperties")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load android.os.SystemProperties", e)
            null
        }
    }

    private val getMethod: Method? by lazy {
        findMethod("get", String::class.java)
    }

    private val getWithDefaultMethod: Method? by lazy {
        findMethod("get", String::class.java, String::class.java)
    }

    private val getIntMethod: Method? by lazy {
        findMethod("getInt", String::class.java, Int::class.javaPrimitiveType!!)
    }

    private val getBooleanMethod: Method? by lazy {
        findMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType!!)
    }

    private fun findMethod(methodName: String, vararg parameterTypes: Class<*>): Method? {
        return try {
            systemPropertiesClass?.getMethod(methodName, *parameterTypes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find method: $methodName", e)
            null
        }
    }

    @JvmStatic
    fun get(propName: String): String? {
        return try {
            getMethod?.invoke(null, propName) as? String
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get property: $propName", e)
            getByCommand(propName)
        }
    }

    @JvmStatic
    fun get(propName: String, defaultValue: String?): String? {
        return try {
            (getWithDefaultMethod?.invoke(null, propName, defaultValue) as? String) ?: defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get property with default: $propName", e)
            getByCommand(propName).ifEmpty { defaultValue }
        }
    }

    @JvmStatic
    fun getInt(propName: String, defaultValue: Int?): Int? {
        return try {
            (getIntMethod?.invoke(null, propName, defaultValue) as? Int) ?: defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Int property: $propName", e)
            getByCommand(propName).toIntOrNull() ?: defaultValue
        }
    }

    @JvmStatic
    fun getBoolean(propName: String, defaultValue: Boolean?): Boolean? {
        return try {
            (getBooleanMethod?.invoke(null, propName, defaultValue) as? Boolean) ?: defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Boolean property: $propName", e)
            parseBoolean(getByCommand(propName), defaultValue ?: return null)
        }
    }

    @JvmStatic
    fun getAll(): Map<String, String> {
        val systemProperties = linkedMapOf<String, String>()
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("getprop")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: continue
                    val separatorIndex = text.indexOf("]: [")
                    if (separatorIndex > 0 && text.startsWith("[") && text.endsWith("]")) {
                        val key = text.substring(1, separatorIndex)
                        val value = text.substring(separatorIndex + 4, text.length - 1)
                        systemProperties[key] = value
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get all system properties", e)
        } finally {
            process?.destroy()
        }
        return systemProperties
    }

    private fun getByCommand(propName: String): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("getprop", propName))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine().orEmpty()
            }.also {
                process.waitFor()
            }
        } catch (_: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    private fun parseBoolean(value: String, defaultValue: Boolean): Boolean {
        return when (value.trim().lowercase()) {
            "1", "true", "y", "yes", "on" -> true
            "0", "false", "n", "no", "off" -> false
            else -> defaultValue
        }
    }
}