package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.Closeable

/** Native implementation serializes mutations on main; geometry reads are safe on the script thread. */
internal interface ComposeFloatyControl : Closeable {
    val x: Int
    val y: Int
    val width: Int
    val height: Int
    fun setPosition(x: Int, y: Int)
    fun setSize(width: Int, height: Int)
    fun requestFocus()
    fun disableFocus()
    fun setTouchable(touchable: Boolean)
    fun setAdjustEnabled(enabled: Boolean)
}

/** Script facade delegates window behavior and exposes the ordinary Compose session unchanged. */
internal class ComposeFloatyWindow(
    private val owner: Compose,
    private val session: ComposeScriptSession,
    private val control: ComposeFloatyControl,
    private val raw: Boolean,
) : NativeObject() {
    init {
        RhinoUtils.initNativeObjectPrototype(owner.scope, this)
        defineProperty("session", session.js, READONLY or PERMANENT)
        defineProperty("root", { ScriptableObject.getProperty(session.js, "root") }, null, READONLY or PERMANENT)
        for (name in listOf("update", "post", "showSnackbar", "on", "isClosed")) {
            defineProperty(name, ScriptableObject.getProperty(session.js, name), READONLY or PERMANENT)
        }
        method("setPosition", 2) { args -> control.setPosition(coordinate(args[0], "x"), coordinate(args[1], "y")); this }
        method("setSize", 2) { args -> control.setSize(dimension(args[0], "width"), dimension(args[1], "height")); this }
        method("getX", 0) { control.x }
        method("getY", 0) { control.y }
        method("getWidth", 0) { control.width }
        method("getHeight", 0) { control.height }
        method("requestFocus", 0) { control.requestFocus(); this }
        method("disableFocus", 0) { control.disableFocus(); this }
        method("setTouchable", 1) { control.setTouchable(boolean(it[0], "touchable")); this }
        method("setAdjustEnabled", 1) {
            if (raw) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Raw windows have no adjustment controls", prop = "adjustEnabled")
            control.setAdjustEnabled(boolean(it[0], "adjustEnabled")); this
        }
        defineProperty("close", RhinoUtils.newBaseFunction(owner.scope, "close", { args ->
            owner.guardError { composeArguments(args, 0, name = "close"); session.close(); Undefined.instance }
        }, RhinoUtils.NOT_CONSTRUCTABLE), READONLY or PERMANENT)
        preventExtensions()
    }
    override fun getClassName() = "ComposeFloatyWindow"
    private fun method(name: String, count: Int, action: (Array<out Any?>) -> Any?) {
        defineProperty(name, RhinoUtils.newBaseFunction(owner.scope, name, { args -> owner.guardError {
            owner.checkThread()
            if (session.isClosed) throw ComposeUiContractException(ComposeUiErrorCodes.SESSION_CLOSED)
            composeArguments(args, count, name = name)
            action(args)
        } }, RhinoUtils.NOT_CONSTRUCTABLE), READONLY or PERMANENT)
    }
    private fun coordinate(value: Any?, name: String): Int = owner.values.integer("Floaty", name, value, Int.MIN_VALUE, Int.MAX_VALUE)
    private fun dimension(value: Any?, name: String): Int = owner.values.integer("Floaty", name, value, -2, Int.MAX_VALUE)
    private fun boolean(value: Any?, name: String): Boolean = value as? Boolean
        ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = name)
}
