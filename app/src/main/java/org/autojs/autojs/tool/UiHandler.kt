package org.autojs.autojs.tool

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.runtime.api.ScriptToast

/**
 * Created by Stardust on 2017/5/2.
 * Transformed by SuperMonster003 on Oct 25, 2023.
 */
class UiHandler(val context: Context) : Handler(Looper.getMainLooper()) {

    @ScriptInterface
    @JvmOverloads
    fun toast(message: String?, isLong: Boolean = false) {
        post {
            message?.let {
                val rawToast = Toast.makeText(context, it, if (isLong) LENGTH_LONG else LENGTH_SHORT)
                when (Looper.getMainLooper() == Looper.myLooper()) {
                    true -> addAndShow(rawToast)
                    else -> GlobalAppContext.post { addAndShow(rawToast) }
                }
            }
        }
    }

    @ScriptInterface
    @JvmOverloads
    fun toast(resId: Int, isLong: Boolean = false) = toast(context.getString(resId), isLong)

    @ScriptInterface
    fun dismissAllToasts() = ScriptToast.dismissAll(AutoJs.instance.runtime)

    private fun addAndShow(rawToast: Toast) {
        rawToast.show()
        ScriptToast.add(rawToast, AutoJs.instance.runtime)
    }

}
