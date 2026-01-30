package org.autojs.autojs.runtime.api.augment.toast

import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptToast
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.Undefined
import android.content.Context as AndroidContext
import org.mozilla.javascript.Context as RhinoContext

/**
 * Created by SuperMonster003 on Dec 2, 2024.
 * Modified by SuperMonster003 as of Jan 30, 2026.
 */
class ToastParser(private val context: AndroidContext, private val msg: Any?, isLong: Any? = null, isForcible: Any? = null) {

    private val badge = object {
        val long = Regex("^l(ong)?$", RegexOption.IGNORE_CASE)
        val short = Regex("^s(hort)?$", RegexOption.IGNORE_CASE)
        val forcible = Regex("^f(orcible)?$", RegexOption.IGNORE_CASE)
    }

    var isLong = false
    var isForcible = false

    init {
        parseIsLong(isLong)
        parseIsForcible(isForcible)
    }

    fun show(scriptRuntime: ScriptRuntime) {
        if (this@ToastParser.isForcible) ScriptToast.dismissAll(scriptRuntime)
        val niceMsg = when {
            Undefined.isUndefined(msg) || msg == NOT_FOUND -> "undefined"
            msg == null -> "null"
            else -> RhinoContext.toString(msg)
        }
        ScriptToast.enqueueToast(context, scriptRuntime, niceMsg, this@ToastParser.isLong)
    }

    private fun parseIsLong(o: Any?) = when (o) {
        is Boolean -> o
        is Number -> RhinoContext.toBoolean(o)
        is String -> when {
            badge.long.matches(o) -> true
            badge.short.matches(o) -> false
            badge.forcible.matches(o) -> this@ToastParser.isLong.also { this.isForcible = true }
            else -> throw IllegalArgumentException("Argument \"isLong\" ${o.jsBrief()} for Toast.Parser is invalid")
        }
        else -> this@ToastParser.isLong
    }.also { this@ToastParser.isLong = it }

    private fun parseIsForcible(o: Any?) = when (o) {
        is Boolean -> o
        is Number -> RhinoContext.toBoolean(o)
        is String -> when {
            badge.forcible.matches(o) -> true
            badge.long.matches(o) -> this@ToastParser.isForcible.also { this.isLong = true }
            badge.short.matches(o) -> this@ToastParser.isForcible.also { this.isLong = false }
            else -> throw IllegalArgumentException("Argument \"isForcible\" ${o.jsBrief()} for Toast.Parser is invalid")
        }
        else -> this@ToastParser.isForcible
    }.also { this@ToastParser.isForcible = it }

}