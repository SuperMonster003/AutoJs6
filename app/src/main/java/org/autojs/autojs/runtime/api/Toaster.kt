package org.autojs.autojs.runtime.api

import android.util.TypedValue
import android.view.Gravity
import com.hjq.toast.Toaster
import com.hjq.window.EasyWindow
import org.autojs.autojs.AutoJs
import org.autojs.autojs.runtime.ScriptRuntime
import java.lang.ref.WeakReference

// TODO by SuperMonster003 on Oct 27, 2024.
class Toaster(private val scriptRuntime: ScriptRuntime) {

    @JvmOverloads
    fun easy(message: String, duration: Int = 1000) {
        val handler = scriptRuntime.uiHandler
        val application = AutoJs.instance.application
        val windowRef = WeakReference(EasyWindow<EasyWindow<*>>(application))

        handler.post {
            windowRef.get()!!
                .setDuration(duration)
                .setContentView(Toaster.getStyle().createView(application))
                .setAnimStyle(android.R.style.Animation_Translucent)
                .setText(android.R.id.message, message)
                .setGravity(Gravity.BOTTOM)
                .setYOffset(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50f, application.resources.displayMetrics).toInt())
                .also { handler.postDelayed({ it.recycle() }, duration.toLong()) }
                .show()
        }
    }

}
