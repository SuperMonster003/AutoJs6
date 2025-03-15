package org.autojs.autojs.runtime.api.augment.ui

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.core.ui.attribute.ViewAttributeDelegate
import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.core.ui.inflater.InflateContext
import org.autojs.autojs.core.ui.inflater.LayoutInflaterDelegate
import org.autojs.autojs.core.ui.inflater.inflaters.ViewGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater
import org.autojs.autojs.core.ui.nativeview.NativeView
import org.autojs.autojs.core.ui.widget.JsListView
import org.autojs.autojs.execution.ScriptExecuteActivity
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.jsSanitize
import org.autojs.autojs.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_OBJECT_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.AugmentableProxy
import org.autojs.autojs.runtime.api.augment.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.runtime.api.augment.jsox.Numberx
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.initNewBaseFunction
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.NativeWith
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.xmlimpl.XML
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.autojs.autojs.runtime.api.UI as ApiUI

@Suppress("unused", "UNUSED_PARAMETER")
class UI(private val scriptRuntime: ScriptRuntime) : AugmentableProxy(scriptRuntime) {

    override val key = super.key.lowercase()

    private val mProperties = ConcurrentHashMap<String, Any?>()

    private val mWidgetConstructor = object : BaseFunction() {

        override fun getFunctionName() = WIDGET_KEY

        override fun construct(cx: Context?, scope: Scriptable, args: Array<out Any?>) = createObject(cx, scope).also {
            require(it is NativeObject) {
                "Bad implementation of call as constructor, name=${getFunctionName()} in ${javaClass.name}"
            }
            assignWidgetProperties(it)
        }
    }.apply { initNewBaseFunction(this) }

    override val selfAssignmentProperties = listOf<Pair<Any, Any?>>(
        WIDGET_KEY to mWidgetConstructor,
    )

    override val selfAssignmentFunctions = listOf(
        "run",
        ::__inflate__.name,
        ::inflate.name,
        ::useAndroidLayout.name,
        ::isUiThread.name,
        ::post.name,
        ::layout.name,
        ::layoutFile.name,
        ::registerWidget.name,
        ::setContentView.name,
        ::statusBarColor.name,
        ::statusBarAppearanceLight.name,
        ::statusBarAppearanceLightBy.name,
        ::backgroundColor.name,
        ::navigationBarColor.name,
        ::navigationBarAppearanceLight.name,
        ::navigationBarAppearanceLightBy.name,
        ::findById.name,
        ::findByStringId.name,
        ::findView.name,
        ::finish.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "R" to Supplier { scriptRuntime.topLevelScope.prop("R").jsUnwrapped() },
        "__widgets__" to Supplier { scriptRuntime.ui.widgets },
        "emitter" to Supplier {
            val activity = getActivity(scriptRuntime)
            (activity as? ScriptExecuteActivity)?.eventEmitter
        },
    )

    init {
        bind(scriptRuntime.ui, scriptRuntime.topLevelScope)
        initWidgetConstructor(mWidgetConstructor)
    }

    override fun get(augmented: ScriptableObject, key: String): Any? {
        return when (val augmentedValue = getCore(augmented, key)) {
            NOT_FOUND -> scriptRuntime.ui.get(key, augmented)
            else -> augmentedValue
        }
    }

    override fun set(augmented: ScriptableObject, key: String, value: Any?) = undefined {
        scriptRuntime.ui.put(key, augmented, value)
    }

    private fun bind(ui: ApiUI, scope: Scriptable) {
        ui.bindingContext = scope
        ui.layoutInflater.layoutInflaterDelegate = UIProxyLayoutInflaterDelegate()

        ui.defineProperty(PROXY_OBJECT_KEY, newNativeObject().also { o ->
            o.defineProp(PROXY_GETTER_KEY, newBaseFunction(PROXY_GETTER_KEY, { getterArgList ->
                val (keyArg) = getterArgList
                val key = coerceString(keyArg)
                val rawValue = mProperties[key]
                when {
                    rawValue.isJsNullish() && scriptRuntime.ui.view != null -> {
                        when (val view = findById(scriptRuntime, arrayOf(key))) {
                            null -> rawValue
                            else -> view
                        }
                    }
                    else -> rawValue
                }
            }, NOT_CONSTRUCTABLE))
            o.defineProp(PROXY_SETTER_KEY, newBaseFunction(PROXY_SETTER_KEY, { setterArgList ->
                val (keyArg, value) = setterArgList
                val key = coerceString(keyArg)
                value?.let { mProperties[key] = it } ?: mProperties.remove(key)
            }, NOT_CONSTRUCTABLE))
        }, READONLY or PERMANENT)
    }

    @Suppress("SameReturnValue")
    inner class UIProxyLayoutInflaterDelegate : LayoutInflaterDelegate {

        override fun beforeConvertXml(inflateContext: InflateContext?, xml: String?): String? = when {
            xml == null -> null
            scriptRuntime.ui.isAndroidLayout == true -> xml
            scriptRuntime.ui.isAndroidLayout == null && "\\bxmlns:\\w+=\"\\w+://|\\b(android|app):\\w+=\".+\"/".toRegex().containsMatchIn(xml) -> xml
            else -> null
        }

        override fun afterConvertXml(inflateContext: InflateContext?, xml: String): String {
            return xml
        }

        override fun beforeInflateView(inflateContext: InflateContext, node: Node, parent: ViewGroup?, attachToParent: Boolean): View? {
            return null
        }

        override fun beforeCreateView(inflateContext: InflateContext, node: Node?, viewName: String, parent: ViewGroup?): View? {
            val widgets = scriptRuntime.ui.widgets
            if (widgets.contains(viewName)) {
                val ctor = widgets.prop(viewName) as NativeFunction
                val widget = withRhinoContext { cx ->
                    ctor.construct(cx, scriptRuntime.topLevelScope, arrayOf())
                } as ScriptableObject
                val f = widget.prop("renderInternal") as BaseFunction
                return __inflateRhinoRuntime__(scriptRuntime, scriptRuntime.ui.layoutInflater.newInflateContext().also { ctx ->
                    ctx.put("root", widget)
                    ctx.put("widget", widget)
                }, callFunction(scriptRuntime, f, widget, widget, arrayOf()), parent, false)
            }
            return null
        }

        override fun beforeInflation(inflateContext: InflateContext, xml: String, parent: ViewGroup?): View? {
            return null
        }

        override fun afterInflation(inflateContext: InflateContext, doInflation: View, xml: String, parent: ViewGroup?): View {
            return doInflation
        }

        override fun afterCreateView(inflateContext: InflateContext, view: View, node: Node?, viewName: String, parent: ViewGroup?): View {
            if (view is JsListView) {
                initListView(scriptRuntime, view)
            }
            val widget = inflateContext.get("widget")
            if (widget is NativeObject) {

                // @Dubious by SuperMonster003 on Jul 17, 2024.
                //  ! I couldn't find any usage of this field
                //  ! whose origin comes from the statement in the module file "__ui__.js".
                //  ! zh-CN:
                //  ! 我找不到这个字段的任何用法, 它来源于 "__ui__.js" 模块文件中的以下声明语句.
                //  !
                //  # `widget.view = view;`
                widget.defineProp("view", view)

                // ViewExtras.getNativeView(scriptRuntime.topLevelScope, view, null, scriptRuntime).defineProp("widget", widget)
                ViewExtras.getNativeView(scriptRuntime.topLevelScope, view, null, scriptRuntime)?.let { nativeView ->
                    nativeView.viewPrototype.widget = widget
                }

                ViewExtras.getViewAttributes(scriptRuntime, view, scriptRuntime.ui.layoutInflater.resourceParser)
                    .setViewAttributeDelegate(object : ViewAttributeDelegate {
                        override fun has(name: String): Boolean {
                            return callFunction(scriptRuntime, widget.prop("hasAttr") as BaseFunction, widget, widget, arrayOf(name)) as Boolean
                        }

                        override fun get(view: View?, name: String, defaultGetter: ViewAttributeDelegate.ViewAttributeGetter?): String {
                            return coerceString(callFunction(scriptRuntime, widget.prop("getAttr") as BaseFunction, widget, widget, arrayOf(view, name, defaultGetter)))
                        }

                        override fun set(view: View?, name: String, value: String?, defaultSetter: ViewAttributeDelegate.ViewAttributeSetter?) {
                            callFunction(scriptRuntime, widget.prop("setAttr") as BaseFunction, widget, widget, arrayOf(view, name, value, defaultSetter))
                        }
                    })
                callFunction(scriptRuntime, widget.prop("notifyViewCreated") as BaseFunction, widget, widget, arrayOf(view))
            }
            return view
        }

        override fun beforeApplyAttributes(inflateContext: InflateContext, view: View, inflater: ViewInflater<View>, attrs: HashMap<String, String>, parent: ViewGroup?): Boolean {
            return false
        }

        override fun beforeApplyAttribute(inflateContext: InflateContext, inflater: ViewInflater<View>, view: View, ns: String?, attrName: String, value: String, parent: ViewGroup?): Boolean {
            val isDynamic = scriptRuntime.ui.layoutInflater.isDynamicValue(value)
            val inflaterFlags = scriptRuntime.ui.layoutInflater.inflateFlags
            return when {
                isDynamic && inflaterFlags == DynamicLayoutInflater.FLAG_IGNORES_DYNAMIC_ATTRS -> true
                !isDynamic && inflaterFlags == DynamicLayoutInflater.FLAG_JUST_DYNAMIC_ATTRS -> true
                else -> {
                    val boundValue = bindValueForApplyingAttribute(scriptRuntime, value)
                    val widget = inflateContext.get("widget")
                    if (widget is NativeObject && callFunction(scriptRuntime, widget.prop("hasAttr") as BaseFunction, widget, widget, arrayOf(attrName)) as Boolean) {
                        callFunction(scriptRuntime, widget.prop("setAttr") as BaseFunction, widget, widget, arrayOf(view, attrName, boundValue, newBaseFunction("setter", {
                            val (viewArg, attrNameArg, valueArg, parentArg) = it
                            inflater.setAttr(viewArg as View, ns, coerceString(attrNameArg), coerceString(valueArg, ""), parentArg as ViewGroup?)
                        }, NOT_CONSTRUCTABLE)))
                    } else {
                        inflater.setAttr(view, ns, attrName, boundValue, parent)
                    }
                    afterApplyAttribute(inflateContext, inflater, view, ns, attrName, boundValue, parent)
                    true
                }
            }
        }

        override fun afterApplyAttribute(inflateContext: InflateContext, inflater: ViewInflater<View>, view: View, ns: String?, attrName: String, value: String, parent: ViewGroup?) {
            /* Empty body. */
        }

        override fun afterApplyAttributes(inflateContext: InflateContext, view: View, inflater: ViewInflater<View>, attrs: HashMap<String, String>, parent: ViewGroup?) {
            inflateContext.remove("widget")
        }

        override fun beforeInflateChildren(inflateContext: InflateContext, inflater: ViewInflater<View>, node: Node, parent: ViewGroup?): Boolean {
            return false
        }

        override fun afterInflateChildren(inflateContext: InflateContext, inflater: ViewInflater<View>, node: Node, parent: ViewGroup?) {
            /* Empty body. */
        }

        override fun beforeApplyPendingAttributesOfChildren(inflateContext: InflateContext, inflater: ViewGroupInflater<*>, view: ViewGroup?): Boolean {
            return false
        }

        override fun afterApplyPendingAttributesOfChildren(inflateContext: InflateContext, inflater: ViewGroupInflater<*>, view: ViewGroup?) {
            /* Empty body. */
        }

        override fun afterInflateView(inflateContext: InflateContext, view: View, node: Node, parent: ViewGroup?, attachToParent: Boolean): View {
            val nativeView = ViewExtras.getNativeView(scriptRuntime.topLevelScope, view, null, scriptRuntime)
            if (nativeView != null) {
                val prototype = nativeView.viewPrototype
                val widget = prototype.widget
                if (widget != null && inflateContext.get("root") != widget) {
                    require(widget is NativeObject) {
                        "Field widget of view prototype must be a JavaScript Object instead of ${widget.jsBrief()}"
                    }
                    callFunction(scriptRuntime, widget.prop("notifyAfterInflation") as BaseFunction, widget, widget, arrayOf(view))
                }
            }
            return view
        }

    }

    @Suppress("FunctionName")
    companion object {

        private const val WIDGET_KEY = "Widget"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun __inflate__(scriptRuntime: ScriptRuntime, args: Array<out Any?>): View = ensureArgumentsLengthInRange(args, 2..4) { argList ->
            val (ctx, xml, parent, isAttachedToParent) = argList
            __inflateRhinoRuntime__(scriptRuntime, ctx, xml, parent, isAttachedToParent)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun __inflateRhinoRuntime__(scriptRuntime: ScriptRuntime, ctx: Any?, xml: Any?, parent: Any?, isAttachedToParent: Any?): View {
            require(ctx is InflateContext) {
                "Augment ctx for ui.__inflate__ must be a InflateContext instead of ${ctx.jsBrief()}"
            }
            val parentView = parent.jsSanitize()
            require(parentView is ViewGroup?) {
                "Augment parentView for ui.__inflate__ must be a ViewGroup instead of ${parentView.jsBrief()}"
            }
            return scriptRuntime.ui.layoutInflater.inflate(ctx, toXMLString(xml), parentView, coerceBoolean(isAttachedToParent, false))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun inflate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeView = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (xml, parent, isAttachedToParent) = argList
            inflateRhinoRuntime(scriptRuntime, xml, parent, isAttachedToParent)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun inflateRhinoRuntime(scriptRuntime: ScriptRuntime, xml: Any?, parent: Any? = null, isAttachedToParent: Any? = false): NativeView {
            val parentView = parent.jsSanitize()
            require(parentView is ViewGroup?) {
                "Augment parentView for ui.inflate must be a ViewGroup instead of ${parentView.jsBrief()}"
            }
            val activity = getActivity(scriptRuntime)
            scriptRuntime.ui.layoutInflater.context = when {
                activity.isJsNullish() -> ContextThemeWrapper(ScriptRuntime.applicationContext, R.style.ScriptTheme)
                activity is ScriptExecuteActivity -> activity
                else -> throw WrappedIllegalArgumentException("Global activity ${activity.jsBrief()} must be a ScriptExecuteActivity")
            }
            val inflatedView = scriptRuntime.ui.layoutInflater.inflate(toXMLString(xml), parentView, coerceBoolean(isAttachedToParent, false))
            return ViewExtras.getNativeView(scriptRuntime.topLevelScope, inflatedView, inflatedView::class.java, scriptRuntime)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun useAndroidLayout(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) { argList ->
            val (b) = argList
            scriptRuntime.ui.isAndroidLayout = when {
                b == null -> null
                Undefined.isUndefined(b) -> true
                else -> coerceBoolean(b, true)
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun run(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { action ->
            runRhinoRuntime(scriptRuntime, action)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun runRhinoRuntime(scriptRuntime: ScriptRuntime, action: Any?): Any? {
            require(action is BaseFunction) {
                "Argument action for ui.run must be a JavaScript Function instead of ${action.jsBrief()}"
            }
            return when {
                RhinoUtils.isUiThread() -> callFunction(scriptRuntime, action, arrayOf())
                else -> {
                    var error: Exception? = null
                    var result: Any? = null
                    val disposable = scriptRuntime.threads.disposable()
                    scriptRuntime.uiHandler.post {
                        try {
                            result = callFunction(scriptRuntime, action, arrayOf())
                        } catch (e: Exception) {
                            error = e
                        } finally {
                            disposable.setAndNotify(true)
                        }
                    }
                    disposable.blockedGet()

                    error?.let {
                        scriptRuntime.console.warn("${error.jsBrief()} occurred in `ui.run()`")
                        scriptRuntime.console.warn(it.message)
                        scriptRuntime.console.warn(it.stackTraceToString())
                        throw it
                    }
                    result
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isUiThread(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            RhinoUtils.isUiThread()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun post(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (action, delay) = argList
            postRhinoRuntime(scriptRuntime, action, delay)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun postRhinoRuntime(scriptRuntime: ScriptRuntime, action: Any?, delay: Any? = null): Boolean {
            require(action is BaseFunction) {
                "Argument action for ui.post must be a JavaScript Function instead of ${action.jsBrief()}"
            }
            return when {
                delay.isJsNullish() -> scriptRuntime.uiHandler.post(wrapUiAction(scriptRuntime, action))
                else -> scriptRuntime.uiHandler.postDelayed(wrapUiAction(scriptRuntime, action), coerceLongNumber(delay, 0L))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun layout(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { xml ->
            ensureActivity(scriptRuntime) { activity ->
                scriptRuntime.ui.layoutInflater.context = activity
                scriptRuntime.ui.layoutInflater.inflate(
                    toXMLString(xml),
                    activity.window.decorView as ViewGroup,
                    false,
                ).let { setContentViewRhinoRuntime(scriptRuntime, it) }
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun layoutFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { path ->
            layout(scriptRuntime, arrayOf(scriptRuntime.files.read(coerceString(path))))
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun registerWidget(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) { argList ->
            val (name, widget) = argList
            registerWidgetRhinoRuntime(scriptRuntime, name, widget)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun registerWidgetRhinoRuntime(scriptRuntime: ScriptRuntime, name: Any?, widget: Any?): Undefined {
            val niceName = coerceString(name, "")
            require(niceName.isNotEmpty()) {
                "Argument name for ui.registerWidget must be a valid non-empty string"
            }
            require(widget is BaseFunction) {
                "Argument widget for ui.registerWidget must be a JavaScript Function instead of ${widget.jsBrief()}"
            }
            scriptRuntime.ui.widgets.defineProp(niceName, widget)
            return UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setContentView(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { view ->
            setContentViewRhinoRuntime(scriptRuntime, view)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setContentViewRhinoRuntime(scriptRuntime: ScriptRuntime, view: Any?): Undefined {
            require(view is View) {
                "Argument view for ui.setContentView must be a View instead of ${view.jsBrief()}"
            }
            ensureActivity(scriptRuntime) { activity ->
                scriptRuntime.ui.view = view
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    activity.setContentView(view)
                }, NOT_CONSTRUCTABLE))
            }
            return UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun statusBarColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { color ->
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    Colors.toIntRhino(color).also { ViewUtils.setStatusBarBackgroundColor(activity, it) }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun statusBarAppearanceLight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) { argList ->
            val (isLight) = argList
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    ViewUtils.setStatusBarAppearanceLight(activity, coerceBoolean(isLight, true))
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun statusBarAppearanceLightBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { refColor ->
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    Colors.toIntRhino(refColor).also { ViewUtils.setStatusBarAppearanceLight(activity, ColorUtils.isLuminanceDark(it)) }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun backgroundColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { color ->
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    Colors.toIntRhino(Colors.setAlphaRhino(color, 1.0)).also {
                        activity.window.setBackgroundDrawable(ColorDrawable(it))
                    }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun navigationBarColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { color ->
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    Colors.toIntRhino(color).also { ViewUtils.setNavigationBarBackgroundColor(activity, it) }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun navigationBarAppearanceLight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) { argList ->
            val (isLight) = argList
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        ScriptRuntime.requiresApi(Build.VERSION_CODES.O)
                    } else {
                        ViewUtils.setNavigationBarAppearanceLight(activity, coerceBoolean(isLight, true))
                    }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun navigationBarAppearanceLightBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { refColor ->
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    Colors.toIntRhino(refColor).also {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            ScriptRuntime.requiresApi(Build.VERSION_CODES.O)
                        } else {
                            ViewUtils.setNavigationBarAppearanceLight(activity, ColorUtils.isLuminanceDark(it))
                        }
                    }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findById(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { id ->
            when {
                id.isJsNullish() -> {
                    findByIdRhinoWithRuntime(scriptRuntime, null)
                }
                else -> {
                    // require(id is String) { "Argument id for ui.findById must be a string instead of ${id.jsBrief()}" }
                    findByIdRhinoWithRuntime(scriptRuntime, coerceString(id))
                }
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun findByIdRhinoWithRuntime(scriptRuntime: ScriptRuntime, id: String?): NativeView? {
            val view = scriptRuntime.ui.view ?: return null
            return findByStringIdRhinoRuntime(scriptRuntime, view, id)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findByStringId(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLength(args, 2) { argList ->
            val (view, id) = argList
            require(view is View) { "Argument view for ui.findByStringId must be a View instead of ${view.jsBrief()}" }
            when {
                id.isJsNullish() -> {
                    findByStringIdRhinoRuntime(scriptRuntime, view, null)
                }
                else -> {
                    // require(id is String) { "Argument id for ui.findByStringId must be a string instead of ${id.jsBrief()}" }
                    findByStringIdRhinoRuntime(scriptRuntime, view, coerceString(id))
                }
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun findByStringIdRhinoRuntime(scriptRuntime: ScriptRuntime, view: View, id: String?): NativeView? {
            val foundView = JsViewHelper.findViewByStringId(view, id) ?: return null
            return ViewExtras.getNativeView(scriptRuntime.topLevelScope, foundView, foundView::class.java, scriptRuntime)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findView(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { id ->
            when {
                id.isJsNullish() -> {
                    findByIdRhinoWithRuntime(scriptRuntime, null)
                }
                else -> {
                    // require(id is String) { "Argument id for ui.findView must be a string instead of ${id.jsBrief()}" }
                    findByIdRhinoWithRuntime(scriptRuntime, coerceString(id))
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun finish(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            ensureActivity(scriptRuntime) { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction("action", {
                    activity.finish()
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        private fun initWidgetConstructor(ctor: BaseFunction) {
            val prototype = ctor.prop("prototype") as? ScriptableObject ?: newNativeObject()
            assignWidgetProperties(prototype)
            ctor.defineProp("prototype", prototype, DONTENUM or PERMANENT)
        }

        private fun assignWidgetProperties(o: ScriptableObject) {
            o.defineProperty("__attrs__", newNativeObject(), DONTENUM or PERMANENT)
            o.defineFunctionProperties(
                arrayOf(
                    "renderInternal",
                    "defineAttr",
                    "hasAttr",
                    "setAttr",
                    "getAttr",
                    "notifyViewCreated",
                    "notifyAfterInflation",
                ), UIWidget.javaClass, PERMANENT
            )
        }

        private fun bindValueForApplyingAttribute(scriptRuntime: ScriptRuntime, value: String): String {
            val ctx = scriptRuntime.ui.bindingContext ?: return value
            val niceCtx = ctx as? Scriptable
                ?: Context.javaToJS(ctx, scriptRuntime.topLevelScope) as? Scriptable
                ?: scriptRuntime.topLevelScope
            var tmp = value
            var i = -1
            while (tmp.indexOf("{{", i + 1).also { i = it } >= 0) {
                val j = tmp.indexOf("}}", i + 1)
                if (j < 0) return tmp
                val evaluated = evalInContext(tmp.slice(i + 2 until j), niceCtx)
                tmp = tmp.slice(0 until i) + attrValueConvert(evaluated) + tmp.slice(j + 2 until tmp.length)
            }
            return tmp
        }

        private fun toXMLString(xml: Any?): String = when {
            xml.isJsNullish() -> ""
            xml is XML -> xml.toXMLString()
            xml is Document -> {
                val transformer = TransformerFactory.newInstance().newTransformer()
                val writer = StringWriter()
                transformer.transform(DOMSource(xml as Node), StreamResult(writer))
                writer.toString()
            }
            else -> xml.toString()
        }

        /**
         * This method is used to execute a given JavaScript expression within a specified context
         * and return the result of that execution.
         *
         * zh-CN: 此方法用于在指定的上下文中执行所给定的 JavaScript 表达式, 并返回执行结果.
         *
         * The core logic of this method comes from the following JavaScript code.
         *
         * zh-CN: 该方法的核心逻辑来源于以下 JavaScript 代码:
         *
         * ```JavaScript
         * function evalInContext(expression, ctx) {
         *     return __exitIfError__(() => {
         *         with (ctx) {
         *             return (/* @IIFE */ function () {
         *                 return eval(expression);
         *             })();
         *         }
         *     });
         * }
         * ```
         *
         * @param expression The JavaScript expression to evaluate as a string. (zh-CN: 用于执行的表达式.)
         * @param ctx The context (scriptable scope) in which the expression should be evaluated. (zh-CN: 提供给参数 expression 的作用于上下文.)
         * @return The result of evaluating the expression. (zh-CN: 参数 expression 执行结果.)
         */
        private fun evalInContext(expression: String, ctx: Scriptable): Any? {
            val iife = object : BaseFunction() {
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                    return cx.evaluateString(scope, expression, "<eval>", 1, null)
                }
            }
            return callFunction(iife, ctx, ctx, emptyArray())
        }

        private fun initListView(scriptRuntime: ScriptRuntime, list: JsListView) {
            list.setDataSourceAdapter(object : JsListView.DataSourceAdapter {
                override fun getItemCount(dataSource: Any?) = when (dataSource) {
                    is NativeArray -> dataSource.length.toInt()
                    else -> 0
                }

                override fun getItem(dataSource: Any?, i: Int) = when (dataSource) {
                    is NativeArray -> dataSource[i]
                    else -> null
                }

                override fun setDataSource(dataSource: Any?) {
                    if (dataSource !is NativeArray) return
                    val adapter = list.adapter ?: return
                    val global = scriptRuntime.topLevelScope
                    val globalArray = global.prop("Array") as ScriptableObject
                    val arrayObserveFunc = globalArray.prop("observe") as BaseFunction
                    val handlerFunc = newBaseFunction("handler", { args ->
                        val (changes) = args
                        require(changes is NativeArray) {
                            "A JavaScript array is required"
                        }
                        changes.forEach { change ->
                            require(change is ScriptableObject) {
                                "Each element in this JavaScript array must be a ScriptableObject instead of ${change.jsBrief()}"
                            }
                            when (change.prop("type")) {
                                "splice" -> {
                                    val removed = change.prop("removed")
                                    if (removed is NativeArray) {
                                        val len = removed.length.toInt()
                                        if (len > 0) {
                                            adapter.notifyItemRangeRemoved(coerceIntNumber(change.prop("index")), len)
                                        }
                                    }
                                    val addedCount = coerceIntNumber(change.prop("addedCount"))
                                    if (addedCount > 0) {
                                        adapter.notifyItemRangeInserted(coerceIntNumber(change.prop("index")), addedCount)
                                    }
                                }
                                "update" -> {
                                    try {
                                        adapter.notifyItemChanged(Numberx.parseAnyRhino(change.prop("name")).toInt())
                                    } catch (_: Exception) {
                                        /* Ignored. */
                                    }
                                }
                            }
                        }
                    }, NOT_CONSTRUCTABLE)
                    withRhinoContext { cx ->
                        arrayObserveFunc.call(cx, global, globalArray, arrayOf(dataSource, handlerFunc))
                    }
                }
            })
        }

        private fun wrapUiAction(scriptRuntime: ScriptRuntime, action: BaseFunction) = Runnable {
            when {
                !getActivity(scriptRuntime).isJsNullish() -> callFunction(scriptRuntime, action, scriptRuntime.topLevelScope, arrayOf())
                else -> withRhinoContext { cx ->
                    val scope = scriptRuntime.topLevelScope
                    val func = scope.prop("__exitIfError__") as BaseFunction
                    func.call(cx, scope, scope, arrayOf(newBaseFunction("action", {
                        callFunction(scriptRuntime, action, arrayOf())
                    }, NOT_CONSTRUCTABLE)))
                }
            }
        }

        private fun getActivity(scriptRuntime: ScriptRuntime): Any? {
            return scriptRuntime.topLevelScope.prop("activity").jsUnwrapped()
        }

        private fun <R> ensureActivity(scriptRuntime: ScriptRuntime, func: (activity: ScriptExecuteActivity) -> R): R {
            val activity = getActivity(scriptRuntime)
            require(!activity.isJsNullish()) { globalContext.getString(org.autojs.autojs6.R.string.error_activity_is_required_for_ui_exec_mode) }
            require(activity is ScriptExecuteActivity) { "Global activity ${activity.jsBrief()} must be a ScriptExecuteActivity" }
            return func.invoke(activity)
        }

        private fun attrValueConvert(o: Any?): Any? = when (o) {
            is String -> o
            is ColorNativeObject -> Colors.toHexRhino(o)
            is ThemeColor -> o.colorPrimary
            is NativeWith -> attrValueConvert(o.prototype)
            else -> o
        }

    }

}
