package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitArgs

/**
 * The `automator` module on the new script module layer (roadmap P3.3): the actions of the accessibility service (`click`,
 * `longClick`, `press`, `swipe`, the gestures, `scrollDown` / `scrollUp`, `input` / `setText`, the global actions `back`,
 * `home`, `recents` ...) and the synchronous automation toolkit (`smartClick`, `clickIfExists`, `waitForIdle`, `retry` ...),
 * 39 of them also global, as with the current module. The work is still done by the current module's functions
 * (`augment/automator/Automator`, `augment/toolkit`, P5.2); a toolkit failure still throws the script's `FlowError` object.
 *
 * Arguments are checked before (T4, D10, appendix C Q65 (2)), accepting what the current module accepts: a call that matches
 * none of the documented forms, a missing target, an argument, element of a point list or stroke, field of a point object
 * or of the toolkit options of another type is a TypeError, a coordinate, index or duration that is NaN, infinite, out of
 * its integer range (or a negative duration) a RangeError ([AutomatorArgs], [ToolkitArgs]; the current module threw
 * IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 automator 模块 (P3.3): 无障碍服务的动作 (click, longClick, press, swipe, 手势, scrollDown / scrollUp, input / setText, 全局动作
 * back, home, recents 等) 与同步自动化工具集 (smartClick, clickIfExists, waitForIdle, retry 等), 其中 39 个同时为全局函数, 同现有模块. 实际工作仍由现有模块
 * 完成 (augment/automator/Automator, augment/toolkit, P5.2 迁出); 工具集失败时仍抛出脚本的 FlowError 对象. 参数先行检查 (T4, D10, 附录 C Q65 (2)), 现有
 * 模块接受的这里同样接受: 不符合任何文档形式的调用, 缺少目标, 参数, 点列表或笔画的元素, 点对象或工具集选项的字段类型不符为 TypeError, 坐标, 索引或时长为
 * NaN, 无穷, 超出整数范围 (或时长为负) 为 RangeError (AutomatorArgs, ToolkitArgs; 现有模块抛 IllegalArgumentException).
 */
@ScriptModule(name = "automator")
class AutomatorModule(private val runtime: ScriptRuntime) {

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 4])
    fun click(vararg args: Any?): Boolean {
        AutomatorArgs.clickArgs("automator.click", args)
        return Automator.click(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 4])
    fun longClick(vararg args: Any?): Boolean {
        AutomatorArgs.clickArgs("automator.longClick", args)
        return Automator.longClick(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 3])
    fun press(vararg args: Any?): Boolean {
        AutomatorArgs.pressArgs("automator.press", args)
        return Automator.press(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [2, 5])
    fun swipe(vararg args: Any?): Boolean {
        AutomatorArgs.swipeArgs("automator.swipe", args)
        return Automator.swipe(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, -1])
    fun gesture(vararg args: Any?): Boolean {
        AutomatorArgs.gestureArgs("automator.gesture", args, callback = false)
        return Automator.gesture(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, -1])
    fun gestureAsync(vararg args: Any?) {
        AutomatorArgs.gestureArgs("automator.gestureAsync", args, callback = true)
        Automator.gestureAsync(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, -1])
    fun gestures(vararg args: Any?): Boolean {
        AutomatorArgs.gesturesArgs("automator.gestures", args, callback = false)
        return Automator.gestures(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, -1])
    fun gesturesAsync(vararg args: Any?) {
        AutomatorArgs.gesturesArgs("automator.gesturesAsync", args, callback = true)
        Automator.gesturesAsync(runtime, args)
    }

    @ScriptFunction
    fun isServiceRunning(): Boolean = Automator.isServiceRunning(runtime, NO_ARGS)

    @ScriptFunction
    fun ensureService() {
        Automator.ensureService(runtime, NO_ARGS)
    }

    @ScriptFunction
    fun waitForService(timeout: Any? = null) {
        AutomatorArgs.timeoutArg("automator.waitForService", timeout)
        Automator.waitForService(runtime, if (GlobalValues.isNullish(timeout)) NO_ARGS else arrayOf(timeout))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [0, 4])
    fun scrollDown(vararg args: Any?): Boolean {
        AutomatorArgs.scrollArgs("automator.scrollDown", args)
        return Automator.scrollDown(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [0, 4])
    fun scrollUp(vararg args: Any?): Boolean {
        AutomatorArgs.scrollArgs("automator.scrollUp", args)
        return Automator.scrollUp(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun input(first: Any?, text: Any? = ABSENT): Boolean = text("automator.input", first, text) { Automator.input(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun setText(first: Any?, text: Any? = ABSENT): Boolean = text("automator.setText", first, text) { Automator.setText(runtime, it) }

    @ScriptFunction
    fun captureScreen(): ImageWrapper = Automator.captureScreen(runtime, NO_ARGS)

    @ScriptFunction
    fun lockScreen(): Boolean = Automator.lockScreen(runtime, NO_ARGS)

    @ScriptFunction
    fun takeScreenshot(): Boolean = Automator.takeScreenshot(runtime, NO_ARGS)

    @ScriptFunction(aliases = ["headsetHook"])
    fun headsethook(): Boolean = Automator.headsethook(runtime, NO_ARGS)

    @ScriptFunction
    fun switchToInputMethodWithId(id: String): Boolean = Automator.switchToInputMethodWithId(runtime, arrayOf(id))

    @ScriptFunction
    fun switchToInputMethod(name: String): Boolean = Automator.switchToInputMethod(runtime, arrayOf(name))

    @ScriptFunction
    fun accessibilityButton(): Boolean = Automator.accessibilityButton(runtime, NO_ARGS)

    @ScriptFunction
    fun accessibilityButtonChooser(): Boolean = Automator.accessibilityButtonChooser(runtime, NO_ARGS)

    @ScriptFunction
    fun accessibilityShortcut(): Boolean = Automator.accessibilityShortcut(runtime, NO_ARGS)

    @ScriptFunction
    fun accessibilityAllApps(): Boolean = Automator.accessibilityAllApps(runtime, NO_ARGS)

    @ScriptFunction
    fun dismissNotificationShade(): Boolean = Automator.dismissNotificationShade(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun back(): Boolean = Automator.back(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun home(): Boolean = Automator.home(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun powerDialog(): Boolean = Automator.powerDialog(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun notifications(): Boolean = Automator.notifications(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun quickSettings(): Boolean = Automator.quickSettings(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun recents(): Boolean = Automator.recents(runtime, NO_ARGS)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun splitScreen(): Boolean = Automator.splitScreen(runtime, NO_ARGS)

    // The toolkit (roadmap P4.2, D10): the synchronous forms; the flow forms are the roots of `flow`.
    // zh-CN: 工具集 (Roadmap P4.2, D10): 同步形式; Flow 形式为 flow 的起点.

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun smartClick(vararg args: Any?): Any? = tool("smartClick", args) { Automator.smartClick(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun smartClickBounds(vararg args: Any?): Any? = tool("smartClickBounds", args) { Automator.smartClickBounds(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [0, 1])
    fun waitForIdle(vararg args: Any?): Any? {
        ToolkitArgs.idleArgs("automator.waitForIdle", args)
        return Automator.waitForIdle(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [0, 3])
    fun waitForEvent(vararg args: Any?): Any? {
        ToolkitArgs.eventArgs("automator.waitForEvent", args)
        return Automator.waitForEvent(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [0, 2])
    fun waitForToast(vararg args: Any?): Any? {
        ToolkitArgs.toastArgs("automator.waitForToast", args)
        return Automator.waitForToast(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [0, 2])
    fun waitForNotification(vararg args: Any?): Any? {
        ToolkitArgs.notificationArgs("automator.waitForNotification", args)
        return Automator.waitForNotification(runtime, args)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun clickIfExists(vararg args: Any?): Any? = tool("clickIfExists", args) { Automator.clickIfExists(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun clickBoundsIfExists(vararg args: Any?): Any? = tool("clickBoundsIfExists", args) { Automator.clickBoundsIfExists(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun clickAny(vararg args: Any?): Any? = tool("clickAny", args) { Automator.clickAny(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun clickBoundsAny(vararg args: Any?): Any? = tool("clickBoundsAny", args) { Automator.clickBoundsAny(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun findAny(vararg args: Any?): Any? = tool("findAny", args) { Automator.findAny(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun scrollUntil(vararg args: Any?): Any? = tool("scrollUntil", args) { Automator.scrollUntil(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [2, 3])
    fun typeInto(vararg args: Any?): Any? = tool("typeInto", args) { Automator.typeInto(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun dismissPopups(vararg args: Any?): Any? = tool("dismissPopups", args) { Automator.dismissPopups(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [2, 3])
    fun collectList(vararg args: Any?): Any? = tool("collectList", args) { Automator.collectList(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun launchAndWait(vararg args: Any?): Any? = tool("launchAndWait", args) { Automator.launchAndWait(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun backUntil(vararg args: Any?): Any? = tool("backUntil", args) { Automator.backUntil(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun backToApp(vararg args: Any?): Any? = tool("backToApp", args) { Automator.backToApp(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [2, 3])
    fun toggle(vararg args: Any?): Any? = tool("toggle", args) { Automator.toggle(runtime, it) }

    @ScriptFunction(global = ScriptGlobal.ALSO, arity = [1, 2])
    fun retry(vararg args: Any?): Any? = tool("retry", args) { Automator.retry(runtime, it) }

    private inline fun tool(name: String, args: Array<out Any?>, call: (Array<out Any?>) -> Any?): Any? {
        ToolkitArgs.toolArgs("automator.$name", name, args, callbacks = false)
        return call(args)
    }

    private inline fun text(function: String, first: Any?, text: Any?, call: (Array<out Any?>) -> Boolean): Boolean {
        val args = if (text === ABSENT) arrayOf(first) else arrayOf(first, text)
        AutomatorArgs.textArgs(function, args)
        return call(args)
    }

    private companion object {

        val ABSENT = Any()

        val NO_ARGS = emptyArray<Any?>()

    }

}
