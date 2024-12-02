package org.autojs.autojs.service

import android.annotation.SuppressLint
import java.lang.reflect.Method

/**
 * Created by SuperMonster003 on Feb 3, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Feb 3, 2024.
@SuppressLint("PrivateApi")
object AccessibilityInteractionClient {

    private val getInstance: Method by lazy {
        getMethod("getInstance")
    }

    private val clearCache: Method by lazy {
        getMethod("clearCache")
    }

    fun clearCache() = runCatching { clearCache.invoke(getInstance.invoke(null)) }.isSuccess

    private fun getMethod(methodName: String): Method {
        return try {
            Class.forName("android.view.accessibility.AccessibilityInteractionClient").getMethod(methodName)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Cannot find ${AccessibilityInteractionClient::class.java.simpleName} class", e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Cannot find method $methodName in ${AccessibilityInteractionClient::class.java.simpleName} class", e)
        }
    }

}
