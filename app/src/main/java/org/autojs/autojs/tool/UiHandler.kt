package org.autojs.autojs.tool

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import org.autojs.autojs.AutoJs
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptToast
import org.autojs.autojs.util.RhinoUtils.isMainThread

/**
 * Created by Stardust on May 2, 2017.
 * Transformed by SuperMonster003 on Oct 25, 2023.
 */
class UiHandler(val applicationContext: Context) : Handler(Looper.getMainLooper()) {

    fun toast(scriptRuntime: ScriptRuntime, message: String, isLong: Boolean = false) {
        val showRunnable = Runnable {
            val toastLength = if (isLong) LENGTH_LONG else LENGTH_SHORT
            Toast.makeText(AutoJs.instance.application, message, toastLength)
                .also { ScriptToast.add(it, scriptRuntime) }
                .show()
        }
        when {
            isMainThread() -> showRunnable.run()
            else -> this@UiHandler.post(showRunnable)
        }
    }

    fun dismissAllToasts(scriptRuntime: ScriptRuntime) = ScriptToast.dismissAll(scriptRuntime)

}
