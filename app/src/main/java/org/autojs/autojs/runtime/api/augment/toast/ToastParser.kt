package org.autojs.autojs.runtime.api.augment.toast

import org.autojs.autojs.AutoJs
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.Undefined

class ToastParser(private val msg: Any?, long: Any? = null, forcible: Any? = null) {

    private val badge = object {
        val long = Regex("^l(ong)?$", RegexOption.IGNORE_CASE)
        val short = Regex("^s(hort)?$", RegexOption.IGNORE_CASE)
        val forcible = Regex("^f(orcible)?$", RegexOption.IGNORE_CASE)
    }

    var isLong = false
    var isForcible = false

    init {
        parseIsLong(long)
        parseIsForcible(forcible)
    }

    fun show(scriptRuntime: ScriptRuntime) {
        if (isForcible) Toast.dismissAllRhino(scriptRuntime)
        val niceMsg = when {
            Undefined.isUndefined(msg) || msg == NOT_FOUND -> "undefined"
            msg == null -> "null"
            else -> coerceString(msg, msg.toString())
        }
        AutoJs.instance.uiHandler.toast(scriptRuntime, niceMsg, isLong)
    }

    private fun parseIsLong(o: Any?) = when (o) {
        is Boolean -> o
        is Number -> Context.toBoolean(o)
        is String -> when {
            badge.long.matches(o) -> true
            badge.short.matches(o) -> false
            badge.forcible.matches(o) -> isLong.also { this.isForcible = true }
            else -> throw IllegalArgumentException("Argument isLong ${o.jsBrief()} for Toast.Parser is invalid")
        }
        else -> isLong
    }.also { isLong = it }

    private fun parseIsForcible(o: Any?) = when (o) {
        is Boolean -> o
        is Number -> Context.toBoolean(o)
        is String -> when {
            badge.forcible.matches(o) -> true
            badge.long.matches(o) -> isForcible.also { this.isLong = true }
            badge.short.matches(o) -> isForcible.also { this.isLong = false }
            else -> throw IllegalArgumentException("Argument isForcible ${o.jsBrief()} for Toast.Parser is invalid")
        }
        else -> isForcible
    }.also { isForcible = it }

}