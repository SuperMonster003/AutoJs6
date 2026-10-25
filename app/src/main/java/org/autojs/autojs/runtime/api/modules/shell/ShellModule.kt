package org.autojs.autojs.runtime.api.modules.shell

import android.view.KeyEvent
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.api.modules.shell.ShellArgs.ABSENT
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

/**
 * The `shell` module on the new script module layer (roadmap P3.4): callable as `execCommand`, with the functions of the
 * current module (the streaming `spawn` and the Auto.js Pro compatible `setDefaultOptions` / `checkAccess` /
 * `isRootAvailable` included) and the global compatibility functions (`Menu` ... `Swipe`), in the current order, served
 * by the current implementation (`augment/shell/Shell`, `ShellProcess`, the per-script defaults; to be moved out before
 * P5.2), so commands, defaults and processes behave as before.
 *
 * This layer adds the signatures (T4) and the argument types of D10 ([ShellArgs]): commands, arguments, options, intents
 * and coordinates of an undocumented type are TypeErrors, an unknown key code or access type a RangeError (the current
 * module threw IllegalArgumentException); fields inside option objects keep the current checks.
 *
 * zh-CN: 新脚本模块层上的 shell 模块 (P3.4): 可调用 (同 execCommand), 函数 (含流式 spawn 与兼容 Auto.js Pro 的 setDefaultOptions / checkAccess /
 * isRootAvailable) 与全局兼容函数 (Menu ... Swipe) 的顺序同现有模块, 由现有实现完成 (augment/shell/Shell, ShellProcess, 每脚本默认选项; P5.2 前迁出),
 * 命令, 默认选项与进程的行为不变. 本层增加签名 (T4) 与 D10 的参数类型 (ShellArgs): 命令, 参数, 选项, Intent 与坐标的类型不在文档之列为 TypeError, 未知的按键码或
 * 权限类型为 RangeError (现有模块抛 IllegalArgumentException); 选项对象内部的字段沿用现有检查.
 */
@ScriptModule(name = "shell")
class ShellModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(command: Any?, arguments: Any? = ABSENT, withRoot: Any? = ABSENT): AbstractShell.Result =
        Shell.execCommand(runtime, ShellArgs.command("shell", command, arguments, withRoot))

    @ScriptFunction
    fun execCommand(command: Any?, arguments: Any? = ABSENT, withRoot: Any? = ABSENT): AbstractShell.Result =
        Shell.execCommand(runtime, ShellArgs.command("shell.execCommand", command, arguments, withRoot))

    @ScriptFunction
    fun getCommand(command: Any?, arguments: Any? = ABSENT, withRoot: Any? = ABSENT): String =
        Shell.getCommand(runtime, ShellArgs.command("shell.getCommand", command, arguments, withRoot))

    @ScriptFunction
    fun fromIntent(intent: Any?): String {
        val value = if (intent is Wrapper) intent.unwrap() else intent
        if (value !is android.content.Intent && value !is NativeObject) throw GlobalValues.typeError("shell.fromIntent", "intent", "android.content.Intent | Object", intent)
        return Shell.fromIntent(runtime, arrayOf(intent))
    }

    @ScriptFunction
    fun kill(app: Any?): Boolean = Shell.kill(runtime, arrayOf(app))

    @ScriptFunction
    fun currentPackage(): String = Shell.currentPackageRhino()

    @ScriptFunction
    fun currentActivity(): String = Shell.currentActivityRhino()

    @ScriptFunction
    fun currentComponent(): String = Shell.currentComponentRhino()

    @ScriptFunction
    fun spawn(command: Any?, options: Any? = ABSENT): ShellProcess = Shell.spawn(runtime, ShellArgs.spawn("shell.spawn", command, options))

    @ScriptFunction
    fun setDefaultOptions(options: Any?) {
        if (!isNullish(options) && options !is NativeObject) throw GlobalValues.typeError("shell.setDefaultOptions", "options", "Object | null", options)
        Shell.setDefaultOptions(runtime, arrayOf(options))
    }

    @ScriptFunction
    fun checkAccess(type: String): Boolean {
        if (type != "root" && type != "adb") throw GlobalValues.rangeError("shell.checkAccess", "type", "'root' | 'adb'", type)
        return Shell.checkAccess(runtime, arrayOf(type))
    }

    @ScriptFunction
    fun isRootAvailable(): Boolean = Shell.isRootAvailable(runtime, emptyArray())

    @ScriptFunction(name = "Menu", global = ScriptGlobal.ONLY)
    fun menu() = key(KeyEvent.KEYCODE_MENU)

    @ScriptFunction(name = "Home", global = ScriptGlobal.ONLY)
    fun home() = key(KeyEvent.KEYCODE_HOME)

    @ScriptFunction(name = "Back", global = ScriptGlobal.ONLY)
    fun back() = key(KeyEvent.KEYCODE_BACK)

    @ScriptFunction(name = "Up", global = ScriptGlobal.ONLY)
    fun up() = key(KeyEvent.KEYCODE_DPAD_UP)

    @ScriptFunction(name = "Down", global = ScriptGlobal.ONLY)
    fun down() = key(KeyEvent.KEYCODE_DPAD_DOWN)

    @ScriptFunction(name = "Left", global = ScriptGlobal.ONLY)
    fun left() = key(KeyEvent.KEYCODE_DPAD_LEFT)

    @ScriptFunction(name = "Right", global = ScriptGlobal.ONLY)
    fun right() = key(KeyEvent.KEYCODE_DPAD_RIGHT)

    @ScriptFunction(name = "OK", global = ScriptGlobal.ONLY)
    fun ok() = key(KeyEvent.KEYCODE_DPAD_CENTER)

    @ScriptFunction(name = "VolumeUp", global = ScriptGlobal.ONLY)
    fun volumeUp() = key(KeyEvent.KEYCODE_VOLUME_UP)

    @ScriptFunction(name = "VolumeDown", global = ScriptGlobal.ONLY)
    fun volumeDown() = key(KeyEvent.KEYCODE_VOLUME_DOWN)

    @ScriptFunction(name = "Power", global = ScriptGlobal.ONLY)
    fun power() = key(KeyEvent.KEYCODE_POWER)

    @ScriptFunction(name = "Camera", global = ScriptGlobal.ONLY)
    fun camera() = key(KeyEvent.KEYCODE_CAMERA)

    @ScriptFunction(name = "Text", global = ScriptGlobal.ONLY)
    fun text(text: String) {
        Shell.Text(runtime, arrayOf(text))
    }

    @ScriptFunction(name = "Input", global = ScriptGlobal.ONLY)
    fun input(text: String) {
        Shell.Input(runtime, arrayOf(text))
    }

    @ScriptFunction(name = "Tap", global = ScriptGlobal.ONLY)
    fun tap(x: Any?, y: Any?) {
        Shell.Tap(runtime, arrayOf(GlobalValues.int("Tap", "x", x), GlobalValues.int("Tap", "y", y)))
    }

    @ScriptFunction(name = "Screencap", global = ScriptGlobal.ONLY)
    fun screencap(path: String) {
        Shell.Screencap(runtime, arrayOf(path))
    }

    /** A key by its code or name (`KEYCODE_BACK`, `back`, `0x04`); an unknown one is a RangeError. */
    @ScriptFunction(name = "KeyCode", global = ScriptGlobal.ONLY)
    fun keyCode(keyCode: Any?) {
        val value = if (keyCode is Wrapper) keyCode.unwrap() else keyCode
        if (value !is Number && value !is CharSequence) throw GlobalValues.typeError("KeyCode", "keyCode", "number | string", keyCode)
        val raw = org.mozilla.javascript.ScriptRuntime.toString(value)
        if (keyCodeOf(raw) == KeyEvent.KEYCODE_UNKNOWN) throw GlobalValues.rangeError("KeyCode", "keyCode", "an Android key code or key name", keyCode)
        Shell.KeyCode(runtime, arrayOf(raw))
    }

    @ScriptFunction(name = "SetScreenMetrics", global = ScriptGlobal.ONLY)
    fun setScreenMetrics(width: Any?, height: Any?) {
        Shell.SetScreenMetrics(runtime, arrayOf(GlobalValues.int("SetScreenMetrics", "width", width), GlobalValues.int("SetScreenMetrics", "height", height)))
    }

    @ScriptFunction(name = "Swipe", global = ScriptGlobal.ONLY)
    fun swipe(x1: Any?, y1: Any?, x2: Any?, y2: Any?, duration: Any? = null) {
        val points = listOf("x1" to x1, "y1" to y1, "x2" to x2, "y2" to y2).map { (name, value) -> GlobalValues.int("Swipe", name, value) }
        val args = if (isNullish(duration)) points else points + GlobalValues.int("Swipe", "duration", duration)
        Shell.Swipe(runtime, args.toTypedArray())
    }

    private fun key(code: Int) {
        Shell.KeyCode(runtime, arrayOf(code))
    }

    private companion object {

        const val KEY_CODE_LABEL_PREFIX = "KEYCODE_"

        /** The key code of [raw], as the current module parses it. */
        fun keyCodeOf(raw: String): Int {
            val upperCased = raw.uppercase()
            return when {
                raw.startsWith(KEY_CODE_LABEL_PREFIX) -> KeyEvent.keyCodeFromString(upperCased)
                raw.startsWith("0x", true) -> upperCased.substring(2).toIntOrNull(16) ?: KeyEvent.KEYCODE_UNKNOWN
                else -> KeyEvent.keyCodeFromString(upperCased).takeUnless { it == KeyEvent.KEYCODE_UNKNOWN }
                    ?: KeyEvent.keyCodeFromString(KEY_CODE_LABEL_PREFIX + upperCased)
            }
        }

    }

}
