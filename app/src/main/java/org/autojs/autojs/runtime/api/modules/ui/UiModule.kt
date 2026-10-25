package org.autojs.autojs.runtime.api.modules.ui

import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.core.ui.inflater.InflateContext
import org.autojs.autojs.execution.ScriptExecuteActivity
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptProxyGet
import org.autojs.autojs.runtime.api.modules.ScriptProxyObject
import org.autojs.autojs.runtime.api.modules.ScriptProxySet
import org.autojs.autojs.runtime.api.modules.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.modules.compose.ComposeNode
import org.autojs.autojs.runtime.api.modules.compose.ComposeNodeNativeObject
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.ViewUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.xml.XMLObject
import org.w3c.dom.Document
import android.content.Context as AndroidContext

/**
 * The `ui` module on the new script module layer (roadmap P3.2), a proxy module as the current `augment/ui/UI`
 * (`AugmentableProxy.proxying`): the members in the current order, the runtime's `ui` (`runtime/api/UI`) as prototype,
 * a read of a name the members do not hold going to `runtime.ui` (its properties, `ui.view`, and the views found by id
 * through the getter that the current module binds), a write going to `runtime.ui` as well. `ui` and `$ui` are
 * configurable, as the names the current proxy mounts.
 *
 * Installing the module constructs the current module once (T3's eager exception): it binds `runtime.ui` (the binding
 * context, the layout inflater's delegate for registered widgets and Android layouts, the getter of views by id) and
 * creates the `Widget` constructor, which layouts inflated without reading `ui` (floaty windows, for example) rely on.
 * Inflating, showing content and the window functions stay with the current module's companion (to be moved out before
 * P5.2). `__autojs6Tsx` and `__autojs6TsxFragment` keep their names, attributes and enumerability (R1), the global
 * `isUiThread` is the global module's (decision a of P3.2), `ui.isUiThread` stays on this module.
 *
 * Arguments follow the real signatures (T4): wrong types are TypeErrors (a layout that is neither a string, an XML
 * literal, a DOM document nor a TSX element; an action that is not a function; a color that is no omni color),
 * unusable values RangeErrors (an empty widget name, a fraction for a delay, a color name that does not exist, an
 * unknown option key), surplus arguments TypeErrors (appendix C Q7). Colors are the omni colors of `s13n.color`, the
 * `Color` object of the colors module is still the `augment` package's `ColorNativeObject` (Q20).
 *
 * zh-CN: 新脚本模块层上的 ui 模块 (P3.2), 与现有 augment/ui/UI (AugmentableProxy.proxying) 一样是代理模块: 成员顺序同现有模块, 原型为运行时的 ui
 * (runtime/api/UI), 读取成员中没有的名称时交给 runtime.ui (其属性, ui.view, 以及经现有模块绑定的 getter 按 id 找到的控件), 写入同样交给 runtime.ui. ui 与 $ui
 * 可配置, 同现有代理挂载的名称. 安装模块时构造一次现有模块 (T3 的 eager 例外): 绑定 runtime.ui (绑定上下文, 布局填充器处理已注册控件与 Android 布局的代理,
 * 按 id 查找控件的 getter) 并创建 Widget 构造函数, 不读取 ui 而填充的布局 (例如悬浮窗) 依赖这些. 填充布局, 显示内容与窗口函数仍由现有模块的伴生对象完成
 * (P5.2 前迁出). __autojs6Tsx 与 __autojs6TsxFragment 的名称, 特性与可枚举性照旧 (R1), 全局 isUiThread 属于 global 模块 (P3.2 决定 a), ui.isUiThread
 * 仍在本模块. 参数按真实签名 (T4): 类型不符为 TypeError (布局不是字符串, XML 字面量, DOM 文档或 TSX 元素; 动作不是函数; 颜色不是通用颜色), 不可用的值为
 * RangeError (空的控件名, 小数延时, 不存在的颜色名, 未知的选项键), 多余参数为 TypeError (附录 C Q7). 颜色为 s13n.color 的通用颜色; colors 模块的 Color 对象
 * 仍是 augment 包的 ColorNativeObject (Q20).
 */
@ScriptModule(name = "ui", attributes = ScriptAttributes.EMPTY, proxy = true)
class UiModule(private val runtime: ScriptRuntime) {

    private lateinit var legacy: UI

    @ScriptInstall
    fun install(@Suppress("UNUSED_PARAMETER") installation: ScriptModuleInstallation) {
        legacy = UI(runtime)
    }

    @ScriptPrototype
    fun prototype(): Any = runtime.ui

    /** The constructor of custom widgets (`ui.registerWidget`), created when the module is installed. */
    @ScriptProperty(name = "Widget")
    val widget: Any?
        get() = legacy.widgetConstructor

    @ScriptProperty(name = "__autojs6TsxFragment", global = ScriptGlobal.ALSO)
    val tsxFragment: Any = TypeScriptTsxRuntime.FragmentToken

    @ScriptFunction
    fun run(action: Any?): Any? = UI.runRhinoRuntime(runtime, callableOf("ui.run", action))

    @ScriptFunction(name = "__inflate__", attributes = ScriptAttributes.DONTENUM or ScriptAttributes.PERMANENT)
    fun inflateInContext(ctx: Any?, xml: Any?, parent: Any? = null, isAttachedToParent: Any? = null): View {
        val function = "ui.__inflate__"
        val context = ScriptArgs.typed(ctx, InflateContext::class.java, function, "ctx")
        return UI.__inflateRhinoRuntime__(runtime, context, xmlOf(function, xml, tsx = false), parentOf(function, parent), attachedOf(function, isAttachedToParent))
    }

    @ScriptFunction
    fun inflate(xml: Any?, parent: Any? = null, isAttachedToParent: Any? = null): Any {
        val function = "ui.inflate"
        return UI.inflateRhinoRuntime(runtime, xmlOf(function, xml), parentOf(function, parent), attachedOf(function, isAttachedToParent))
    }

    /** `null` detects Android layouts by their attributes, an omitted (or undefined) argument is `true`, as in the current module. */
    @ScriptFunction
    fun useAndroidLayout(enabled: Any? = Undefined.instance) {
        runtime.ui.isAndroidLayout = when {
            enabled == null -> null
            Undefined.isUndefined(enabled) -> true
            else -> ScriptArgs.boolean(enabled, "ui.useAndroidLayout", "enabled")
        }
    }

    @ScriptFunction
    fun isUiThread(): Boolean = RhinoUtils.isUiThread()

    @ScriptFunction(name = "__autojs6Tsx", global = ScriptGlobal.ALSO, arity = [2, -1])
    fun tsx(vararg args: Any?): Any = TypeScriptTsxRuntime.create(args, TypeScriptTsxRuntime.messageResolver(GlobalAppContext.get()), runtime.composeForTsx())

    @ScriptFunction
    fun post(action: Any?, delay: Any? = null): Boolean {
        val function = "ui.post"
        return UI.postRhinoRuntime(runtime, callableOf(function, action), ScriptArgs.longOrNull(delay, function, "delay"))
    }

    @ScriptFunction
    fun layout(xml: Any?) {
        UI.layout(runtime, arrayOf(xmlOf("ui.layout", xml)))
    }

    @ScriptFunction
    fun layoutFile(path: Any?) {
        UI.layoutFile(runtime, arrayOf(ScriptArgs.string(path, "ui.layoutFile", "path")))
    }

    @ScriptFunction
    fun registerWidget(name: Any?, widget: Any?) {
        val function = "ui.registerWidget"
        val niceName = ScriptArgs.string(name, function, "name")
        if (niceName.isEmpty()) throw GlobalValues.rangeError(function, "name", "non-empty string", name)
        if (widget !is Function) throw GlobalValues.typeError(function, "widget", "function", widget)
        runtime.ui.widgets.defineProp(niceName, widget)
    }

    @ScriptFunction
    fun setContentView(view: Any?) {
        UI.setContentViewRhinoRuntime(runtime, ScriptArgs.typed(view, View::class.java, "ui.setContentView", "view"))
    }

    @ScriptFunction
    fun statusBarColor(color: Any?) {
        UI.statusBarColor(runtime, arrayOf(colorOf("ui.statusBarColor", "color", color)))
    }

    @ScriptFunction
    fun statusBarIconLight(isLight: Any? = null) {
        UI.statusBarIconLight(runtime, arrayOf(flag("ui.statusBarIconLight", "isLight", isLight)))
    }

    @ScriptFunction
    fun statusBarIconLightBy(refColor: Any?) {
        UI.statusBarIconLightBy(runtime, arrayOf(colorOf("ui.statusBarIconLightBy", "refColor", refColor)))
    }

    @ScriptFunction
    fun backgroundColor(color: Any?) {
        UI.backgroundColor(runtime, arrayOf(colorOf("ui.backgroundColor", "color", color)))
    }

    @ScriptFunction
    fun navigationBarColor(color: Any?) {
        UI.navigationBarColor(runtime, arrayOf(colorOf("ui.navigationBarColor", "color", color)))
    }

    @ScriptFunction
    fun navigationBarIconLight(isLight: Any? = null) {
        UI.navigationBarIconLight(runtime, arrayOf(flag("ui.navigationBarIconLight", "isLight", isLight)))
    }

    @ScriptFunction
    fun navigationBarIconLightBy(refColor: Any?) {
        UI.navigationBarIconLightBy(runtime, arrayOf(colorOf("ui.navigationBarIconLightBy", "refColor", refColor)))
    }

    @ScriptFunction
    fun findById(id: Any?): Any? = UI.findByIdRhinoWithRuntime(runtime, ScriptArgs.stringOrNull(id, "ui.findById", "id"))

    @ScriptFunction
    fun findByStringId(view: Any?, id: Any?): Any? {
        val function = "ui.findByStringId"
        return UI.findByStringIdRhinoRuntime(runtime, ScriptArgs.typed(view, View::class.java, function, "view"), ScriptArgs.stringOrNull(id, function, "id"))
    }

    @ScriptFunction
    fun findView(id: Any?): Any? = UI.findByIdRhinoWithRuntime(runtime, ScriptArgs.stringOrNull(id, "ui.findView", "id"))

    @ScriptFunction
    fun finish() {
        UI.finish(runtime, emptyArray())
    }

    @ScriptFunction
    fun keepScreenOn() {
        UI.keepScreenOn(runtime, emptyArray())
    }

    @ScriptFunction
    fun getStatusBarHeight(options: Any? = null): Int = barOptions("ui.getStatusBarHeight", options).let { (withComputed, withDimen, ignoreVisibility) ->
        ViewUtils.getStatusBarHeight(GlobalAppContext.get(), withComputed, withDimen, ignoreVisibility)
    }

    /** `ignoreVisibility` is accepted and ignored, as in the current module. */
    @ScriptFunction
    fun getVisibleStatusBarHeight(options: Any? = null): Int = barOptions("ui.getVisibleStatusBarHeight", options).let { (withComputed, withDimen) ->
        ViewUtils.getStatusBarHeight(GlobalAppContext.get(), withComputed, withDimen, ignoreVisibility = false)
    }

    @ScriptFunction
    fun getNavigationBarHeight(options: Any? = null): Int = barOptions("ui.getNavigationBarHeight", options).let { (withComputed, withDimen, ignoreVisibility) ->
        ViewUtils.getNavigationBarHeight(GlobalAppContext.get(), withComputed, withDimen, ignoreVisibility)
    }

    /** `ignoreVisibility` is accepted and ignored, as in the current module. */
    @ScriptFunction
    fun getVisibleNavigationBarHeight(options: Any? = null): Int = barOptions("ui.getVisibleNavigationBarHeight", options).let { (withComputed, withDimen) ->
        ViewUtils.getNavigationBarHeight(GlobalAppContext.get(), withComputed, withDimen, ignoreVisibility = false)
    }

    @ScriptGetter(name = "R")
    fun resources(): Any? = runtime.augmentedAutojs["R"]

    @ScriptGetter(name = "__widgets__", attributes = ScriptAttributes.DONTENUM or ScriptAttributes.PERMANENT)
    fun widgets(): Any = runtime.ui.widgets

    @ScriptGetter
    val root: Any?
        get() = UiContentHost.of(runtime)?.contentRoot

    /** A preview run has its own emitter (contract v4); otherwise the Activity's. */
    @ScriptGetter
    val emitter: Any?
        get() = UiContentHost.attached(runtime)?.lifecycleEmitter ?: (activity() as? ScriptExecuteActivity)?.eventEmitter

    @ScriptGetter
    val statusBarHeight: Int
        get() = ViewUtils.getStatusBarHeight(barContext())

    @ScriptGetter
    val visibleStatusBarHeight: Int
        get() = ViewUtils.getStatusBarHeight(barContext(), ignoreVisibility = false)

    @ScriptGetter
    val navigationBarHeight: Int
        get() = ViewUtils.getNavigationBarHeight(barContext())

    @ScriptGetter
    val visibleNavigationBarHeight: Int
        get() = ViewUtils.getNavigationBarHeight(barContext(), ignoreVisibility = false)

    /** A read of [name] finds the members first, then `runtime.ui`, as the current module. */
    @ScriptProxyGet
    fun read(members: Scriptable, name: String): Any? {
        val member = ScriptProxyObject.member(members, name)
        return if (member !== Scriptable.NOT_FOUND) member else runtime.ui.get(name, members)
    }

    @ScriptProxySet
    fun write(members: Scriptable, name: String, value: Any?) {
        runtime.ui.put(name, members, value)
    }

    private fun activity(): Any? = runtime.topLevelScope.prop("activity").jsUnwrapped()

    private fun barContext(): AndroidContext = activity() as? ScriptExecuteActivity ?: GlobalAppContext.get()

    private fun callableOf(function: String, action: Any?): Callable = ScriptArgs.typed(action, Callable::class.java, function, "action")

    private fun parentOf(function: String, parent: Any?): ViewGroup? = ScriptArgs.typedOrNull(parent, ViewGroup::class.java, function, "parent")

    private fun attachedOf(function: String, isAttachedToParent: Any?): Boolean = ScriptArgs.booleanOrNull(isAttachedToParent, function, "isAttachedToParent") ?: false

    private fun flag(function: String, param: String, value: Any?): Boolean = ScriptArgs.booleanOrNull(value, function, param) ?: true

    /**
     * [xml] as the current module inflates it: a string, an XML literal or a DOM document, and with [tsx] a TSX element or
     * fragment (a Compose node still reaches the TSX runtime, which reports it as before); anything else is a TypeError.
     */
    private fun xmlOf(function: String, xml: Any?, tsx: Boolean = true): Any = when (val raw = unwrapTsxValue(xml)) {
        is CharSequence -> raw.toString()
        is XMLObject, is Document -> raw
        is TypeScriptTsxRuntime.Value, is TypeScriptTsxFragment, is ComposeNode, is ComposeNodeNativeObject -> if (tsx) raw else throw GlobalValues.typeError(function, "xml", XML_TYPES, xml)
        else -> throw GlobalValues.typeError(function, "xml", if (tsx) TSX_TYPES else XML_TYPES, xml)
    }

    /** An omni color, converted as the current module converts it (`Colors.toIntRhino`). */
    private fun colorOf(function: String, param: String, value: Any?): Int = when (val v = if (value is Wrapper) value.unwrap() else value) {
        is ColorNativeObject -> v.color
        is ThemeColor -> ColorUtils.toInt(v)
        is Number -> ColorUtils.toInt(v.toLong())
        is CharSequence -> v.toString().let { text ->
            try {
                ColorUtils.toInt(text)
            } catch (_: Exception) {
                throw GlobalValues.rangeError(function, param, COLOR_TYPES, text)
            }
        }
        else -> throw GlobalValues.typeError(function, param, COLOR_TYPES, value)
    }

    /** The options of the bar height functions: `withComputed`, `withDimen` and `ignoreVisibility`, booleans (default `true`). */
    private fun barOptions(function: String, options: Any?): BooleanArray {
        val o = ScriptArgs.typedOrNull(options, NativeObject::class.java, function, "options") ?: return BooleanArray(BAR_OPTIONS.size) { true }
        o.ids.forEach { key ->
            if ((key as? String) !in BAR_OPTIONS) throw GlobalValues.rangeError(function, "options key", BAR_OPTIONS.joinToString(" | ") { "'$it'" }, key)
        }
        return BooleanArray(BAR_OPTIONS.size) { i ->
            val value = ScriptableObject.getProperty(o, BAR_OPTIONS[i])
            value === Scriptable.NOT_FOUND || flag(function, "options.${BAR_OPTIONS[i]}", value)
        }
    }

    private companion object {

        const val WIDGET = "Widget"

        const val XML_TYPES = "string | XML | Document"
        const val TSX_TYPES = "string | XML | Document | TSX element"
        const val COLOR_TYPES = "ColorHex | ColorInt | ColorName | Color | ThemeColor"

        val BAR_OPTIONS = listOf("withComputed", "withDimen", "ignoreVisibility")

    }

}
