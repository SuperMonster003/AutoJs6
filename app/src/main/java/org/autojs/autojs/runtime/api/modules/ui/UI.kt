package org.autojs.autojs.runtime.api.modules.ui

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.looper.Loopers
import org.autojs.autojs.core.ui.JsViewHelper
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.core.ui.ViewExtras
import org.autojs.autojs.core.ui.attribute.ViewAttributeDelegate
import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.core.ui.inflater.InflateContext
import org.autojs.autojs.core.ui.inflater.LayoutInflaterDelegate
import org.autojs.autojs.core.ui.inflater.inflaters.ViewGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater
import org.autojs.autojs.core.ui.nativeview.NativeView
import org.autojs.autojs.core.ui.widget.JsListView
import org.autojs.autojs.core.ui.widget.JsWebView
import org.autojs.autojs.execution.ScriptExecuteActivity
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_OBJECT_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_SETTER_KEY
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsSanitize
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.augment.AugmentableProxy
import org.autojs.autojs.runtime.api.modules.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.runtime.api.modules.jsox.Numberx
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.initNewBaseFunction
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.newWithScope
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.NativeWith
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
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

    override val keys = listOf(AugmentableKey("ui"))

    override val scriptErrorPassThroughFunctions = setOf(::__autojs6Tsx.name, ::layout.name, ::inflate.name)

    private val mProperties = ConcurrentHashMap<String, Any>()

    /** The constructor of custom widgets, `ui.Widget` (`ui.registerWidget`). */
    internal val widgetConstructor: BaseFunction
        get() = mWidgetConstructor

    private val mWidgetConstructor = object : BaseFunction() {

        override fun getFunctionName() = WIDGET_KEY

        override fun construct(cx: Context?, nt: Any?, scope: VarScope, args: Array<out Any?>) = createObject(cx, scope).also {
            require(it is NativeObject) {
                "Bad implementation of call as constructor, name=${getFunctionName()} in ${javaClass.name}"
            }
            assignWidgetProperties(scope, it)
        }
    }.apply { initNewBaseFunction(scriptRuntime.topLevelScope, this) }

    override val selfAssignmentProperties = listOf<Pair<Any, Any?>>(
        WIDGET_KEY to mWidgetConstructor,
        ("__autojs6TsxFragment" to TypeScriptTsxRuntime.FragmentToken) to AS_GLOBAL,
    )

    override val selfAssignmentFunctions = listOf(
        "run",
        ::__inflate__.name,
        ::inflate.name,
        ::useAndroidLayout.name,
        ::isUiThread.name to AS_GLOBAL,
        ::__autojs6Tsx.name to AS_GLOBAL,
        ::post.name,
        ::layout.name,
        ::layoutFile.name,
        ::registerWidget.name,
        ::setContentView.name,
        ::statusBarColor.name,
        ::statusBarIconLight.name,
        ::statusBarIconLightBy.name,
        ::backgroundColor.name,
        ::navigationBarColor.name,
        ::navigationBarIconLight.name,
        ::navigationBarIconLightBy.name,
        ::findById.name,
        ::findByStringId.name,
        ::findView.name,
        ::finish.name,
        ::keepScreenOn.name,
        ::getStatusBarHeight.name,
        ::getVisibleStatusBarHeight.name,
        ::getNavigationBarHeight.name,
        ::getVisibleNavigationBarHeight.name,
    )

    override val selfAssignmentGetters = listOf(
        "R" to Supplier { scriptRuntime.augmentedAutojs["R"] },
        "__widgets__" to Supplier { scriptRuntime.ui.widgets },
        "root" to Supplier<Any?> {
            UiContentHost.of(scriptRuntime)?.contentRoot
        },
        "emitter" to Supplier {
            // A preview run has its own emitter (contract v4); otherwise the Activity's.
            UiContentHost.attached(scriptRuntime)?.lifecycleEmitter ?: run {
                val activity = getActivity(scriptRuntime)
                (activity as? ScriptExecuteActivity)?.eventEmitter
            }
        },
        "statusBarHeight" to Supplier {
            val activity = getActivity(scriptRuntime)
            val context = (activity as? ScriptExecuteActivity) ?: globalContext
            ViewUtils.getStatusBarHeight(context)
        },
        "visibleStatusBarHeight" to Supplier {
            val activity = getActivity(scriptRuntime)
            val context = (activity as? ScriptExecuteActivity) ?: globalContext
            ViewUtils.getStatusBarHeight(context, ignoreVisibility = false)
        },
        "navigationBarHeight" to Supplier {
            val activity = getActivity(scriptRuntime)
            val context = (activity as? ScriptExecuteActivity) ?: globalContext
            ViewUtils.getNavigationBarHeight(context)
        },
        "visibleNavigationBarHeight" to Supplier {
            val activity = getActivity(scriptRuntime)
            val context = (activity as? ScriptExecuteActivity) ?: globalContext
            ViewUtils.getNavigationBarHeight(context, ignoreVisibility = false)
        },
    )

    init {
        bind(scriptRuntime.ui, scriptRuntime.topLevelScope.globalThis)
        initWidgetConstructor(scriptRuntime.topLevelScope, mWidgetConstructor)
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

        ui.defineProperty(PROXY_OBJECT_KEY, newNativeObject(scriptRuntime.topLevelScope).also { o ->
            o.defineProp(PROXY_GETTER_KEY, newBaseFunction(scriptRuntime.topLevelScope, PROXY_GETTER_KEY, { getterArgList ->
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
            o.defineProp(PROXY_SETTER_KEY, newBaseFunction(scriptRuntime.topLevelScope, PROXY_SETTER_KEY, { setterArgList ->
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
                val ctor = widgets.prop(viewName) as Function
                val widget = withRhinoContext(scriptRuntime) { cx ->
                    ctor.construct(cx, scriptRuntime.topLevelScope, arrayOf())
                } as ScriptableObject
                val f = widget.prop("renderInternal") as Callable
                return __inflateRhinoRuntime__(scriptRuntime, scriptRuntime.ui.layoutInflater.newInflateContext(inflateContext).also { ctx ->
                    ctx.put("root", widget)
                    ctx.put("widget", widget)
                }, callFunction(scriptRuntime, f, getDeclarationScope(widget), widget, arrayOf()), parent, false)
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
            when (view) {
                is JsListView -> initListView(scriptRuntime, view)
                is JsWebView -> initWebView(scriptRuntime, view)
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
                            return callFunction(scriptRuntime, widget.prop("hasAttr") as Callable, getDeclarationScope(widget), widget, arrayOf(name)) as Boolean
                        }

                        override fun get(view: View?, name: String, defaultGetter: ViewAttributeDelegate.ViewAttributeGetter?): String {
                            return coerceString(callFunction(scriptRuntime, widget.prop("getAttr") as Callable, getDeclarationScope(widget), widget, arrayOf(view, name, defaultGetter)))
                        }

                        override fun set(view: View?, name: String, value: String?, defaultSetter: ViewAttributeDelegate.ViewAttributeSetter?) {
                            callFunction(scriptRuntime, widget.prop("setAttr") as Callable, getDeclarationScope(widget), widget, arrayOf(view, name, value, defaultSetter))
                        }
                    })
                callFunction(scriptRuntime, widget.prop("notifyViewCreated") as Callable, getDeclarationScope(widget), widget, arrayOf(view))
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
                    val boundValue = scriptRuntime.ui.layoutInflater.resourceParser.androidResources.resolveScalar(
                        view.context,
                        bindValueForApplyingAttribute(scriptRuntime, attrName, value),
                    )
                    val widget = inflateContext.get("widget")
                    if (widget is NativeObject && callFunction(scriptRuntime, widget.prop("hasAttr") as Callable, getDeclarationScope(widget), widget, arrayOf(attrName)) as Boolean) {
                        callFunction(scriptRuntime, widget.prop("setAttr") as Callable, getDeclarationScope(widget), widget, arrayOf(view, attrName, boundValue, newBaseFunction(scriptRuntime.topLevelScope, "setter", {
                            val (viewArg, attrNameArg, valueArg, parentArg) = it
                            scriptRuntime.ui.layoutInflater.applyAttributeValue(inflateContext, viewArg as View, inflater, ns, coerceString(attrNameArg), coerceString(valueArg, ""), parentArg as ViewGroup?)
                        }, NOT_CONSTRUCTABLE)))
                    } else {
                        scriptRuntime.ui.layoutInflater.applyAttributeValue(inflateContext, view, inflater, ns, attrName, boundValue, parent)
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
                    callFunction(scriptRuntime, widget.prop("notifyAfterInflation") as Callable, getDeclarationScope(widget), widget, arrayOf(view))
                }
            }
            return view
        }

    }

    @Suppress("FunctionName")
    companion object : ArgumentGuards() {

        private const val WIDGET_KEY = "Widget"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun __autojs6Tsx(
            scriptRuntime: ScriptRuntime,
            args: Array<out Any?>,
        ): Any = TypeScriptTsxRuntime.create(
            args,
            TypeScriptTsxRuntime.messageResolver(globalContext),
            scriptRuntime.composeForTsx(),
        )

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
                "Argument \"ctx\" ${ctx.jsBrief()} for ui.__inflate__ must be a InflateContext instead of ${ctx.jsBrief()}"
            }
            val parentView = parent.jsSanitize()
            require(parentView is ViewGroup?) {
                "Argument \"parentView\" ${parentView.jsBrief()} for ui.__inflate__ must be a ViewGroup instead of ${parentView.jsBrief()}"
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
                "Argument \"parentView\" ${parentView.jsBrief()} for ui.inflate must be a ViewGroup instead of ${parentView.jsBrief()}"
            }
            val activity = getActivity(scriptRuntime)
            scriptRuntime.ui.layoutInflater.context = when {
                // Without an Activity a preview root (if attached) lends its themed context, otherwise the script theme.
                // zh-CN: 没有 Activity 时, 若附着了预览根容器则借用其主题上下文, 否则使用脚本主题.
                activity.isJsNullish() -> UiContentHost.attached(scriptRuntime)?.inflaterContext
                    ?: ContextThemeWrapper(ScriptRuntime.applicationContext, R.style.ScriptTheme)
                activity is ScriptExecuteActivity -> activity
                else -> throw WrappedIllegalArgumentException("Global activity ${activity.jsBrief()} must be a ScriptExecuteActivity")
            }
            val tsxLayout = TypeScriptTsxRuntime.renderOrNull(
                xml, TypeScriptTsxRuntime.messageResolver(globalContext), scriptRuntime.composeForTsx(),
            )
            val inflatedView = scriptRuntime.ui.layoutInflater.inflate(tsxLayout?.xml ?: toXMLString(xml), parentView, coerceBoolean(isAttachedToParent, false))
            tsxLayout?.attachClickHandlers(scriptRuntime, inflatedView)
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
            require(action is Callable) {
                "Argument \"action\" ${action.jsBrief()} for ui.run must be a JavaScript Function"
            }
            return when {
                RhinoUtils.isUiThread() -> callFunction(scriptRuntime, action, null as VarScope?, null, arrayOf())
                else -> {
                    var error: Throwable? = null
                    var result: Any? = null
                    val disposable = scriptRuntime.threads.disposable()
                    scriptRuntime.uiHandler.post {
                        try {
                            result = callFunction(scriptRuntime, action, null as VarScope?, null, arrayOf())
                        } catch (t: Throwable) {
                            error = t
                        } finally {
                            disposable.setAndNotify(true)
                        }
                    }
                    disposable.blockedGet()

                    error?.let { throw it }
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
            require(action is Callable) {
                "Argument \"action\" ${action.jsBrief()} for ui.post must be a JavaScript Function"
            }
            val loopers = scriptRuntime.loopers
            val waitToken = scriptRuntime.timers.timerForCurrentThread?.let {
                loopers.waitWhenIdleToken()
            }

            val uiAction = wrapUiActionForPost(scriptRuntime, action, waitToken)

            return try {
                when {
                    delay.isJsNullish() -> scriptRuntime.uiHandler.post(uiAction)
                    else -> scriptRuntime.uiHandler.postDelayed(uiAction, coerceLongNumber(delay, 0L))
                }.also { posted ->
                    if (!posted) {
                        waitToken?.release()
                    }
                }
            } catch (t: Throwable) {
                waitToken?.release()
                throw t
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun layout(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { xml ->
            ensureUiHost(scriptRuntime) { host ->
                scriptRuntime.ui.layoutInflater.context = host.inflaterContext
                val tsxLayout = TypeScriptTsxRuntime.renderOrNull(
                    xml,
                    TypeScriptTsxRuntime.messageResolver(globalContext),
                    scriptRuntime.composeForTsx(),
                )
                val inflated = scriptRuntime.ui.layoutInflater.inflate(
                    tsxLayout?.xml ?: toXMLString(xml),
                    host.contentParent,
                    false,
                )
                tsxLayout?.attachClickHandlers(scriptRuntime, inflated)
                setContentViewRhinoRuntime(scriptRuntime, inflated)
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun layoutFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { path ->
            ensureUiHost(scriptRuntime) { host ->
                scriptRuntime.ui.layoutInflater.context = host.inflaterContext
                val resolvedPath = scriptRuntime.files.path(coerceString(path))
                    ?: throw WrappedIllegalArgumentException("Android layout file path cannot be resolved")
                scriptRuntime.ui.layoutInflater.inflateFile(
                    resolvedPath,
                    host.contentParent,
                    false,
                ).let { setContentViewRhinoRuntime(scriptRuntime, it) }
            }
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
                "Argument \"name\" ${name.jsBrief()} for ui.registerWidget must be a valid non-empty string"
            }
            require(widget is Function) {
                "Argument \"widget\" ${widget.jsBrief()} for ui.registerWidget must be a JavaScript Function"
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
                "Argument \"view\" ${view.jsBrief()} for ui.setContentView must be a View"
            }
            ensureUiHost(scriptRuntime) { host ->
                scriptRuntime.beforeSetUiContent(view)
                scriptRuntime.ui.view = view
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    host.showContent(view)
                }, NOT_CONSTRUCTABLE))
            }
            return UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun statusBarColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { color ->
            if (showWindowColorOnHost(scriptRuntime, "statusBarColor") { Colors.toIntRhino(color) }) return@ensureArgumentsOnlyOne UNDEFINED
            ensureWindowActivity(scriptRuntime, "statusBarColor") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    Colors.toIntRhino(color).also { ViewUtils.setStatusBarBackgroundColor(activity, it) }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun statusBarIconLight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) { argList ->
            val (isLight) = argList
            ensureWindowActivity(scriptRuntime, "statusBarIconLight") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    ViewUtils.setStatusBarIconLight(activity, coerceBoolean(isLight, true))
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun statusBarIconLightBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { refColor ->
            ensureWindowActivity(scriptRuntime, "statusBarIconLightBy") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    Colors.toIntRhino(refColor).also { ViewUtils.setStatusBarIconLight(activity, ViewUtils.isLuminanceDark(it)) }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun backgroundColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { color ->
            if (showWindowColorOnHost(scriptRuntime, "backgroundColor") { Colors.toIntRhino(Colors.setAlphaRhino(color, 1.0)) }) {
                return@ensureArgumentsOnlyOne UNDEFINED
            }
            ensureWindowActivity(scriptRuntime, "backgroundColor") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    Colors.toIntRhino(Colors.setAlphaRhino(color, 1.0)).also {
                        activity.window.setBackgroundDrawable(it.toDrawable())
                    }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun navigationBarColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { color ->
            if (showWindowColorOnHost(scriptRuntime, "navigationBarColor") { Colors.toIntRhino(color) }) return@ensureArgumentsOnlyOne UNDEFINED
            ensureWindowActivity(scriptRuntime, "navigationBarColor") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    Colors.toIntRhino(color).also { ViewUtils.setNavigationBarBackgroundColor(activity, it) }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun navigationBarIconLight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) { argList ->
            val (isLight) = argList
            ensureWindowActivity(scriptRuntime, "navigationBarIconLight") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        ScriptRuntime.requiresApi(Build.VERSION_CODES.O)
                    } else {
                        ViewUtils.setNavigationBarIconLight(activity, coerceBoolean(isLight, true))
                    }
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun navigationBarIconLightBy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { refColor ->
            ensureWindowActivity(scriptRuntime, "navigationBarIconLightBy") { activity ->
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    Colors.toIntRhino(refColor).also {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            ScriptRuntime.requiresApi(Build.VERSION_CODES.O)
                        } else {
                            ViewUtils.setNavigationBarIconLight(activity, ViewUtils.isLuminanceDark(it))
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
                id.isJsNullish() -> findByIdRhinoWithRuntime(scriptRuntime, null)
                else -> findByIdRhinoWithRuntime(scriptRuntime, coerceString(id))
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
            require(view is View) { "Argument \"view\" ${view.jsBrief()} for ui.findByStringId must be a View" }
            when {
                id.isJsNullish() -> findByStringIdRhinoRuntime(scriptRuntime, view, null)
                else -> findByStringIdRhinoRuntime(scriptRuntime, view, coerceString(id))
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
                id.isJsNullish() -> findByIdRhinoWithRuntime(scriptRuntime, null)
                else -> findByIdRhinoWithRuntime(scriptRuntime, coerceString(id))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun finish(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            UiContentHost.attached(scriptRuntime)?.let { host ->
                host.onWindowFunctionIgnored("finish")
                return@ensureArgumentsIsEmpty UNDEFINED
            }
            val activity = getScriptExecuteActivity(scriptRuntime)
            if (activity == null) {
                require(scriptRuntime.isExiting) { globalContext.getString(R.string.error_activity_is_required_for_ui_exec_mode) }
                return@ensureArgumentsIsEmpty UNDEFINED
            }
            if (!activity.isFinishing && !activity.isDestroyed) {
                runRhinoRuntime(scriptRuntime, newBaseFunction(scriptRuntime.topLevelScope, "action", {
                    activity.finish()
                }, NOT_CONSTRUCTABLE))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun keepScreenOn(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            ensureWindowActivity(scriptRuntime, "keepScreenOn") { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getStatusBarHeight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))
            val withComputed = opt.inquire("withComputed", ::coerceBoolean, true)
            val withDimen = opt.inquire("withDimen", ::coerceBoolean, true)
            val ignoreVisibility = opt.inquire("ignoreVisibility", ::coerceBoolean, true)
            ViewUtils.getStatusBarHeight(globalContext, withComputed, withDimen, ignoreVisibility)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getVisibleStatusBarHeight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))
            val withComputed = opt.inquire("withComputed", ::coerceBoolean, true)
            val withDimen = opt.inquire("withDimen", ::coerceBoolean, true)
            ViewUtils.getStatusBarHeight(globalContext, withComputed, withDimen, ignoreVisibility = false)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getNavigationBarHeight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))
            val withComputed = opt.inquire("withComputed", ::coerceBoolean, true)
            val withDimen = opt.inquire("withDimen", ::coerceBoolean, true)
            val ignoreVisibility = opt.inquire("ignoreVisibility", ::coerceBoolean, true)
            ViewUtils.getNavigationBarHeight(globalContext, withComputed, withDimen, ignoreVisibility)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getVisibleNavigationBarHeight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))
            val withComputed = opt.inquire("withComputed", ::coerceBoolean, true)
            val withDimen = opt.inquire("withDimen", ::coerceBoolean, true)
            ViewUtils.getNavigationBarHeight(globalContext, withComputed, withDimen, ignoreVisibility = false)
        }

        private fun initWidgetConstructor(scope: VarScope, ctor: BaseFunction) {
            val prototype = ctor.prop("prototype") as? ScriptableObject ?: newNativeObject(scope)
            assignWidgetProperties(scope, prototype)
            ctor.defineProp("prototype", prototype, DONTENUM or PERMANENT)
        }

        private fun assignWidgetProperties(scope: VarScope, o: ScriptableObject) {
            o.defineProperty("__attrs__", newNativeObject(scope), DONTENUM or PERMANENT)
            o.defineFunctionProperties(
                scope,
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

        private fun bindValueForApplyingAttribute(scriptRuntime: ScriptRuntime, attrName: String, value: String): String {
            val ctx = scriptRuntime.ui.bindingContext ?: return value
            val niceCtx = when (ctx) {
                is Scriptable -> Context.toObject(ctx, ctx)
                else -> Context.toObject(ctx, scriptRuntime.topLevelScope)
            }
            var tmp = value
            var i = -1
            while (tmp.indexOf("{{", i + 1).also { i = it } >= 0) {
                val j = tmp.indexOf("}}", i + 1)
                if (j < 0) return tmp
                val expression = tmp.slice(i + 2 until j)
                val evaluated = try {
                    UiContentHost.attached(scriptRuntime)?.checkBinding(attrName, expression)?.let { throw it }
                    evalInContext(scriptRuntime, expression, niceCtx)
                } catch (e: Throwable) {
                    // A preview root may keep the raw text and report the failure instead of failing the inflation.
                    // zh-CN: 预览根容器可以保留原文并报告失败, 而不是让 inflate 失败.
                    val host = UiContentHost.attached(scriptRuntime)
                    if (host != null && host.onBindingFailed(attrName, value, e)) return value
                    throw e
                }
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
         * @param scriptRuntime The script runtime whose top-level scope (or the [UiContentHost.bindingScope] of its host) resolves names that ctx does not provide. (zh-CN: 脚本运行时, 其顶层作用域 (或其承载的 [UiContentHost.bindingScope]) 用于解析 ctx 未提供的名称.)
         * @param expression The JavaScript expression to evaluate as a string. (zh-CN: 用于执行的表达式.)
         * @param ctx The context object in which the expression should be evaluated, like the object of a `with` statement. (zh-CN: 提供给参数 expression 的上下文对象, 相当于 `with` 语句的对象.)
         * @return The result of evaluating the expression. (zh-CN: 参数 expression 执行结果.)
         */
        private fun evalInContext(scriptRuntime: ScriptRuntime, expression: String, ctx: Scriptable): Any? {
            // Rhino resolves names along VarScope chains, which a plain object such as a list item is not part of,
            // and callFunction(func, Scriptable, ...) would replace ctx by its declaration scope (the script's top level),
            // so `{{name}}` in a list item template failed with a ReferenceError.
            // A `with` scope keeps the original semantics: names resolve on ctx first, then on the top-level scope.
            // zh-CN: Rhino 沿 VarScope 链解析名称, 列表项这样的普通对象并不在该链上,
            // 而 callFunction(func, Scriptable, ...) 会用 ctx 的声明作用域 (脚本顶层作用域) 取代 ctx,
            // 导致列表项模板中的 `{{name}}` 抛出 ReferenceError.
            // `with` 作用域可保持原有语义: 名称先在 ctx 上解析, 再回退到顶层作用域.
            val withScope = newWithScope(ctx, UiContentHost.attached(scriptRuntime)?.bindingScope ?: scriptRuntime.topLevelScope)
            val iife = object : BaseFunction() {
                override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
                    return cx.evaluateString(scope, expression, "<eval>", 1, null)
                }
            }
            return callFunction(iife, withScope, ctx, emptyArray())
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
                    val arrayObserveFunc = globalArray.prop("observe") as Callable
                    val handlerFunc = newBaseFunction(global, "handler", { args ->
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
                    callFunction(scriptRuntime, arrayObserveFunc, global, globalArray, arrayOf(dataSource, handlerFunc))
                }
            })
        }

        private fun initWebView(scriptRuntime: ScriptRuntime, webView: JsWebView) {
            callFunction(scriptRuntime.js_UiExt, scriptRuntime.topLevelScope, null, arrayOf(webView))
        }

        private fun wrapUiActionForPost(scriptRuntime: ScriptRuntime, action: Callable, waitToken: Loopers.WaitToken?) = Runnable {
            try {
                val isAutoJsUiMode = !getActivity(scriptRuntime).isJsNullish() || UiContentHost.attached(scriptRuntime) != null
                when {
                    isAutoJsUiMode -> {
                        callFunction(scriptRuntime, action, null as VarScope?, null, arrayOf())
                    }
                    else -> {
                        val scope = scriptRuntime.topLevelScope
                        val exitIfErrorFunc = scope.prop("__exitIfError__") as Callable
                        callFunction(scriptRuntime, exitIfErrorFunc, scope, scope.globalThis, arrayOf(newBaseFunction(scope, "action", {
                            callFunction(scriptRuntime, action, null as VarScope?, null, arrayOf())
                        }, NOT_CONSTRUCTABLE)))
                    }
                }
            } finally {
                waitToken?.release()
            }
        }

        private fun getActivity(scriptRuntime: ScriptRuntime): Any? {
            return scriptRuntime.topLevelScope.prop("activity").jsUnwrapped()
        }

        private fun getScriptExecuteActivity(scriptRuntime: ScriptRuntime): ScriptExecuteActivity? {
            val activity = getActivity(scriptRuntime)
            if (activity.isJsNullish()) {
                return null
            }
            require(activity is ScriptExecuteActivity) { "Global activity ${activity.jsBrief()} must be a ScriptExecuteActivity" }
            return activity
        }

        private fun <R> ensureActivity(scriptRuntime: ScriptRuntime, func: (activity: ScriptExecuteActivity) -> R): R {
            val activity = getScriptExecuteActivity(scriptRuntime)
            require(activity != null) { globalContext.getString(R.string.error_activity_is_required_for_ui_exec_mode) }
            return func.invoke(activity)
        }

        /**
         * The content host of `ui.layout` / `ui.setContentView`: a preview root attached to the engine, otherwise the
         * script's Activity; without either the Activity requirement is reported exactly as before.
         * zh-CN: `ui.layout` / `ui.setContentView` 的内容承载: 附着在引擎上的预览根容器, 否则脚本的 Activity; 两者都没有时照旧报告需要 Activity.
         */
        private fun <R> ensureUiHost(scriptRuntime: ScriptRuntime, func: (host: UiContentHost) -> R): R {
            UiContentHost.attached(scriptRuntime)?.let { return func.invoke(it) }
            return ensureActivity(scriptRuntime, func)
        }

        /**
         * A window color function on a host without a window: true when the host showed the color another way (a preview
         * run draws bar strips and paints its root, contract v4); false leaves the call to [ensureWindowActivity].
         * zh-CN: 无窗口承载上的窗口颜色函数: 承载以其它方式呈现时返回 true (预览运行绘制仿状态栏 / 导航栏色条与根背景, 契约 v4); false 交给 [ensureWindowActivity].
         */
        private fun showWindowColorOnHost(scriptRuntime: ScriptRuntime, functionName: String, color: () -> Int): Boolean {
            val attached = UiContentHost.attached(scriptRuntime) ?: return false
            if (attached.hostWindow != null) return false
            return attached.onWindowColor(functionName, color())
        }

        /**
         * A window-level function: on a preview root it is ignored (the host reports it once), otherwise it needs
         * the Activity like before.
         * zh-CN: 窗口级函数: 预览根容器下忽略 (由承载报告一次), 否则照旧需要 Activity.
         */
        private fun ensureWindowActivity(scriptRuntime: ScriptRuntime, functionName: String, func: (activity: ScriptExecuteActivity) -> Unit) {
            val attached = UiContentHost.attached(scriptRuntime)
            if (attached != null && attached.hostWindow == null) {
                attached.onWindowFunctionIgnored(functionName)
                return
            }
            ensureActivity(scriptRuntime, func)
        }

        private fun attrValueConvert(o: Any?): Any? = when (o) {
            is String -> o
            is ColorNativeObject -> Colors.toHexRhino(o)
            is ThemeColor -> o.colorPrimary
            is NativeWith -> attrValueConvert(o.prototype)
            // JavaScript ToString semantics for everything else, so that a numeric
            // expression result such as 18 is spliced in as "18" rather than "18.0".
            // zh-CN: 其余结果使用 JavaScript 的 ToString 语义, 使 18 这样的数值表达式结果拼接为 "18" 而不是 "18.0".
            else -> Context.toString(o)
        }

    }

}
