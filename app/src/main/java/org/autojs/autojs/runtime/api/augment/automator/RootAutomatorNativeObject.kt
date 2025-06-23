package org.autojs.autojs.runtime.api.augment.automator

import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.autojs.autojs.core.inputevent.RootAutomator as CoreRootAutomator
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

@Suppress("unused")
class RootAutomatorNativeObject(scriptRuntime: ScriptRuntime, waitForReady: Any? = false) : NativeObject() {

    private val mRootAutomatorObject: Scriptable = run {
        val rootAutomator = when (waitForReady) {
            is Number -> CoreRootAutomator(ScriptRuntime.applicationContext, waitForReady.toLong())
            else -> CoreRootAutomator(ScriptRuntime.applicationContext, Context.toBoolean(waitForReady))
        }
        RhinoScriptRuntime.toObject(scriptRuntime.topLevelScope, rootAutomator)
    }

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("__ra__", mRootAutomatorObject, READONLY or DONTENUM or PERMANENT)
    }

    override fun has(name: String?): Boolean {
        return mRootAutomatorObject.has(name) || super.has(name)
    }

    override fun get(name: String, start: Scriptable): Any? {
        // @Hint by SuperMonster003 on Jun 16, 2024.
        //  ! Here is the name filter from the legacy JavaScript __RootAutomator__.js module,
        //  ! which is not taken into consideration due to its lack of necessity:
        //  ! zh-CN:
        //  ! 这是原始 JavaScript 模块 __RootAutomator__.js 中的名称过滤器,
        //  ! 这个过滤器因缺乏必要性而没有在此处应用.
        //  !
        //  # [
        //  #     'sendEvent', 'touch', 'setScreenMetrics', 'touchX', 'touchY',
        //  #     'sendSync', 'sendMtSync', 'tap', 'swipe', 'press', 'longPress',
        //  #     'touchDown', 'touchUp', 'touchMove', 'getDefaultId', 'setDefaultId', 'exit',
        //  # ]
        return when (val o = mRootAutomatorObject.prop(name)) {
            is BaseFunction -> withRhinoContext { cx ->
                BoundFunction(cx, mRootAutomatorObject, o, mRootAutomatorObject, arrayOf())
            }
            else -> super.get(name, start)
        }
    }

}
