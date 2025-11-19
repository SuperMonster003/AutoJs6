package org.autojs.autojs.tool

import android.os.Handler
import android.os.Looper
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

class CrashHandlerTestFunction : BaseFunction() {

    override fun call(
        cx: Context?,
        scope: Scriptable?,
        thisObj: Scriptable?,
        args: Array<out Any>?,
    ): Undefined {
        val msg = if (!args.isNullOrEmpty()) Context.toString(args[0]) else "CrashHandler test"
        Handler(Looper.getMainLooper()).post {
            throw RuntimeException(msg)
        }
        return UNDEFINED
    }

    override fun getArity(): Int = 1

    override fun getFunctionName(): String = "crash"

}
