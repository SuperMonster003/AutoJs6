package org.autojs.autojs.runtime.api.modules.floaty

import org.autojs.autojs.rhino.ProxyJavaObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.floaty.Floaty
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.xmlimpl.XML

/**
 * The `floaty` module on the new script module layer (roadmap P3.2): `window` / `rawWindow` (a layout as an XML literal
 * or string), the permission functions, `closeAll` and `getClip`, in the order of the current `augment/floaty/Floaty`.
 * The windows are still created and wrapped by the current module (`Floaty.window` / `rawWindow`: the floaty window of
 * `runtime.floaty` behind a `ProxyJavaObject` that finds views by id; to be moved out before P5.2), the other functions
 * call `runtime.floaty` as before.
 *
 * Arguments follow the real signatures (T4, appendix C Q7). A layout that is neither an XML literal nor a string is a
 * TypeError (the current module threw IllegalArgumentException), as is a `getClip` delay that is not a number; a delay
 * that is not an integer of 64 bits is a RangeError (D10).
 *
 * zh-CN: 新脚本模块层上的 floaty 模块 (P3.2): window / rawWindow (布局为 XML 字面量或字符串), 权限函数, closeAll 与 getClip, 顺序同现有 augment/floaty/Floaty.
 * 窗口仍由现有模块创建与包装 (Floaty.window / rawWindow: runtime.floaty 的悬浮窗外包一层按 id 查找控件的 ProxyJavaObject; P5.2 前迁出), 其余函数照旧调用
 * runtime.floaty. 参数按真实签名 (T4, 附录 C Q7). 布局既不是 XML 字面量也不是字符串为 TypeError (现有模块抛 IllegalArgumentException), getClip 的延时不是
 * 数值同为 TypeError; 延时不是 64 位整数为 RangeError (D10).
 */
@ScriptModule(name = "floaty")
class FloatyModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun window(layout: Any?): ProxyJavaObject = Floaty.window(runtime, arrayOf(layout("floaty.window", layout)))

    @ScriptFunction
    fun rawWindow(layout: Any?): ProxyJavaObject = Floaty.rawWindow(runtime, arrayOf(layout("floaty.rawWindow", layout)))

    @ScriptFunction
    fun hasPermission(): Boolean = runtime.floaty.hasPermission()

    @ScriptFunction
    fun checkPermission(): Boolean = runtime.floaty.hasPermission()

    @ScriptFunction
    fun requestPermission() {
        runtime.floaty.requestPermission()
    }

    @ScriptFunction
    fun ensurePermission() {
        runtime.floaty.ensurePermission()
    }

    @ScriptFunction
    fun closeAll() {
        runtime.floaty.closeAll()
    }

    @ScriptFunction
    fun getClip(maxDelayAfterWindowReady: Any? = null): String = when {
        ScriptArgs.isNullish(maxDelayAfterWindowReady) -> runtime.floaty.getClip()
        else -> runtime.floaty.getClip(ScriptArgs.long(maxDelayAfterWindowReady, "floaty.getClip", "maxDelayAfterWindowReady"))
    }

    private fun layout(function: String, layout: Any?): Any = when (val v = if (layout is Wrapper) layout.unwrap() else layout) {
        is XML -> v
        is CharSequence -> v.toString()
        else -> throw GlobalValues.typeError(function, "layout", "XML | string", layout)
    }

}
