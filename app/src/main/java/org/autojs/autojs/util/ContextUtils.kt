package org.autojs.autojs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

object ContextUtils {

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun Context.requireActivity(): Activity {
        return this.findActivity() ?: throw IllegalArgumentException("Context cannot be cast to Activity")
    }

    @JvmStatic
    fun Context.findActivity(): Activity? {
        var context: Context? = this
        while (context !is Activity) {
            context = (context as? ContextWrapper)?.baseContext
        }
        return context as? Activity?
    }

}