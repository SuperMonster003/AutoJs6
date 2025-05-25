package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.runtime.api.Resolvable
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable

/**
 * Created by Stardust on Nov 29, 2017.
 * Modified by SuperMonster003 as of Jan 21, 2023.
 * Transformed by SuperMonster003 on May 26, 2023.
 */
class JsWebView : EventWebView {

    @JvmField
    var events: EventEmitter? = null

    @JvmField
    var jsBridge: Scriptable? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Escape string according to Rhino's rules and wrap with single quotes.
     * zh-CN: 将字符串按 Rhino 的规则做转义, 并包裹单引号.
     */
    override fun escapeToStr(src: String) = "'${ScriptRuntime.escapeString(src, '\'')}'"

    override fun newPromise(): Resolvable = ScriptPromiseAdapter()

    override fun onError(t: Throwable) = AutoJs.instance.globalConsole.error(t)

}