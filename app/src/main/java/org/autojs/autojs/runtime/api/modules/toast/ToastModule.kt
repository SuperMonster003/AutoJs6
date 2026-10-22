package org.autojs.autojs.runtime.api.modules.toast

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.toast.Toast
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.Wrapper

/**
 * The `toast` module on the new script module layer (roadmap P3.2): `toast(text, isLong?, isForcible?)` and
 * `toast.dismissAll()`. The toast is still shown by the current module (`augment/toast/Toast.call`, which queues it on
 * `ScriptToast`; to be moved out before P5.2), called with the checked arguments.
 *
 * Arguments follow the real signature (T4, appendix C Q7): `toast()` and `toast(undefined)` show an empty toast. `isLong`
 * and `isForcible` take a boolean, a number (its truth value) or one of the documented words `'long'` / `'l'` /
 * `'short'` / `'s'` / `'forcible'` / `'f'` (any case, in either place, as before); nullish keeps the default. Another word
 * is a RangeError (the current module threw IllegalArgumentException), another type a TypeError (the current module
 * ignored it, D10). A word built by concatenation counts as a word (the current module ignored a ConsString).
 *
 * zh-CN: 新脚本模块层上的 toast 模块 (P3.2): toast(text, isLong?, isForcible?) 与 toast.dismissAll(). 消息框仍由现有模块显示 (augment/toast/Toast.call,
 * 经 ScriptToast 排队; P5.2 前迁出), 传入检查后的参数. 参数按真实签名 (T4, 附录 C Q7): toast() 与 toast(undefined) 显示空消息. isLong 与 isForcible 接受
 * boolean, number (取其真值) 或文档所列的 'long' / 'l' / 'short' / 's' / 'forcible' / 'f' (不分大小写, 两个位置均可, 同以前); nullish 保持默认. 其他单词为
 * RangeError (现有模块抛 IllegalArgumentException), 其他类型为 TypeError (现有模块忽略, D10). 拼接得到的单词同样有效 (现有模块忽略 ConsString).
 */
@ScriptModule(name = "toast")
class ToastModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(text: Any? = "", isLong: Any? = null, isForcible: Any? = null) {
        Toast.call(runtime, arrayOf(text, flag("isLong", isLong), flag("isForcible", isForcible)))
    }

    @ScriptFunction
    fun dismissAll() {
        Toast.dismissAll(runtime, emptyArray())
    }

    private fun flag(param: String, value: Any?): Any? = when (val v = if (value is Wrapper) value.unwrap() else value) {
        null -> null
        is Boolean, is Number -> v
        is CharSequence -> v.toString().also { if (!WORDS.matches(it)) throw GlobalValues.rangeError(MODULE, param, EXPECTED_WORDS, it) }
        else -> if (ScriptArgs.isNullish(v)) null else throw GlobalValues.typeError(MODULE, param, "boolean | number | string", value)
    }

    private companion object {
        const val MODULE = "toast"
        const val EXPECTED_WORDS = "'long' | 'l' | 'short' | 's' | 'forcible' | 'f'"
        val WORDS = Regex("^(l(ong)?|s(hort)?|f(orcible)?)$", RegexOption.IGNORE_CASE)
    }

}
