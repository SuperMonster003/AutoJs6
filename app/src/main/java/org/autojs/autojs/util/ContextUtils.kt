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
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

}