@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.InflateException
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.ui.inflater.inflaters.BaseViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsActionMenuViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsAppBarLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCalendarViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCanvasViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCardViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCheckBoxInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCheckedTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsChronometerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsConsoleViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsDatePickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsDrawerLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsEditTextInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsFloatingActionButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsFrameLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsGridViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsImageButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsImageSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsImageViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsLinearLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsListViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsNumberPickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsProgressBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsQuickContactBadgeInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRadioButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRadioGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRatingBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRelativeLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsScrollViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSearchViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSeekBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSpinnerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSwitchInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTabLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextClockInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextViewLegacyInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTimePickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsToggleButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsToolbarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsVideoViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsViewFlipperInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsViewPagerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsViewSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsWebViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SpaceInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.core.ui.widget.JsActionMenuView
import org.autojs.autojs.core.ui.widget.JsAppBarLayout
import org.autojs.autojs.core.ui.widget.JsButton
import org.autojs.autojs.core.ui.widget.JsCalendarView
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.widget.JsCardView
import org.autojs.autojs.core.ui.widget.JsCheckBox
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import org.autojs.autojs.core.ui.widget.JsChronometer
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.widget.JsDatePicker
import org.autojs.autojs.core.ui.widget.JsDrawerLayout
import org.autojs.autojs.core.ui.widget.JsEditText
import org.autojs.autojs.core.ui.widget.JsFloatingActionButton
import org.autojs.autojs.core.ui.widget.JsFrameLayout
import org.autojs.autojs.core.ui.widget.JsGridView
import org.autojs.autojs.core.ui.widget.JsImageButton
import org.autojs.autojs.core.ui.widget.JsImageSwitcher
import org.autojs.autojs.core.ui.widget.JsImageView
import org.autojs.autojs.core.ui.widget.JsLinearLayout
import org.autojs.autojs.core.ui.widget.JsListView
import org.autojs.autojs.core.ui.widget.JsNumberPicker
import org.autojs.autojs.core.ui.widget.JsProgressBar
import org.autojs.autojs.core.ui.widget.JsQuickContactBadge
import org.autojs.autojs.core.ui.widget.JsRadioButton
import org.autojs.autojs.core.ui.widget.JsRadioGroup
import org.autojs.autojs.core.ui.widget.JsRatingBar
import org.autojs.autojs.core.ui.widget.JsRelativeLayout
import org.autojs.autojs.core.ui.widget.JsScrollView
import org.autojs.autojs.core.ui.widget.JsSearchView
import org.autojs.autojs.core.ui.widget.JsSeekBar
import org.autojs.autojs.core.ui.widget.JsSpinner
import org.autojs.autojs.core.ui.widget.JsSwitch
import org.autojs.autojs.core.ui.widget.JsTabLayout
import org.autojs.autojs.core.ui.widget.JsTextClock
import org.autojs.autojs.core.ui.widget.JsTextSwitcher
import org.autojs.autojs.core.ui.widget.JsTextView
import org.autojs.autojs.core.ui.widget.JsTextViewLegacy
import org.autojs.autojs.core.ui.widget.JsTimePicker
import org.autojs.autojs.core.ui.widget.JsToggleButton
import org.autojs.autojs.core.ui.widget.JsToolbar
import org.autojs.autojs.core.ui.widget.JsVideoView
import org.autojs.autojs.core.ui.widget.JsViewFlipper
import org.autojs.autojs.core.ui.widget.JsViewPager
import org.autojs.autojs.core.ui.widget.JsViewSwitcher
import org.autojs.autojs.core.ui.widget.JsWebView
import org.autojs.autojs.core.ui.xml.XmlConverter
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs6.R
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

open class DynamicLayoutInflater {

    private var mViewAttrSetters: MutableMap<String, ViewInflater<*>> = HashMap()
    private var mViewCreators: MutableMap<String, ViewCreator<*>> = HashMap()

    internal var privateContext: Context? = null

    @get:ScriptInterface
    @set:ScriptInterface
    var layoutInflaterDelegate: LayoutInflaterDelegate = LayoutInflaterDelegate.NO_OP

    var context: Context
        get() = privateContext ?: GlobalAppContext.get()
        set(value) {
            privateContext = value
        }

    val resourceParser: ResourceParser
    var inflateFlags = 0

    constructor(resourceParser: ResourceParser, scriptRuntime: ScriptRuntime) {
        this.resourceParser = resourceParser
        registerViewAttrSetters(scriptRuntime)
    }

    constructor(inflater: DynamicLayoutInflater) {
        resourceParser = inflater.resourceParser
        context = inflater.context
        mViewAttrSetters = HashMap(inflater.mViewAttrSetters)
        mViewCreators = HashMap(inflater.mViewCreators)
    }

    protected fun registerViewAttrSetters(scriptRuntime: ScriptRuntime) {
        registerViewAttrSetter(JsActionMenuView::class.java, JsActionMenuViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsAppBarLayout::class.java, JsAppBarLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsButton::class.java, JsButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCanvasView::class.java, JsCanvasViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCardView::class.java, JsCardViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCalendarView::class.java, JsCalendarViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCheckBox::class.java, JsCheckBoxInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCheckedTextView::class.java, JsCheckedTextViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsChronometer::class.java, JsChronometerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsConsoleView::class.java, JsConsoleViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsDatePicker::class.java, JsDatePickerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsDrawerLayout::class.java, JsDrawerLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsEditText::class.java, JsEditTextInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsFloatingActionButton::class.java, JsFloatingActionButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsFrameLayout::class.java, JsFrameLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsGridView::class.java, JsGridViewInflater<JsGridView>(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsImageButton::class.java, JsImageButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsImageView::class.java, JsImageViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsImageSwitcher::class.java, JsImageSwitcherInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsLinearLayout::class.java, JsLinearLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsListView::class.java, JsListViewInflater<JsListView>(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsNumberPicker::class.java, JsNumberPickerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsProgressBar::class.java, JsProgressBarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsQuickContactBadge::class.java, JsQuickContactBadgeInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRadioButton::class.java, JsRadioButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRadioGroup::class.java, JsRadioGroupInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRatingBar::class.java, JsRatingBarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRelativeLayout::class.java, JsRelativeLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsScrollView::class.java, JsScrollViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSearchView::class.java, JsSearchViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSeekBar::class.java, JsSeekBarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSpinner::class.java, JsSpinnerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSwitch::class.java, JsSwitchInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTabLayout::class.java, JsTabLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTextClock::class.java, JsTextClockInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTextSwitcher::class.java, JsTextSwitcherInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTimePicker::class.java, JsTimePickerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsToggleButton::class.java, JsToggleButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsToolbar::class.java, JsToolbarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsVideoView::class.java, JsVideoViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsViewFlipper::class.java, JsViewFlipperInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsViewPager::class.java, JsViewPagerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsViewSwitcher::class.java, JsViewSwitcherInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsWebView::class.java, JsWebViewInflater(scriptRuntime, resourceParser))

        when (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            true -> registerViewAttrSetter(JsTextViewLegacy::class.java, JsTextViewLegacyInflater(scriptRuntime, resourceParser))
            else -> registerViewAttrSetter(JsTextView::class.java, JsTextViewInflater(scriptRuntime, resourceParser))
        }

        // TODO by SuperMonster003 on Jun 8, 2023.
        //  ! Support Android XML like menu, shape, paths and so forth.
        //  ! Not easy as expected.
        //  ! zh-CN:
        //  ! 支持安卓 XML (如 menu, shape, paths 等).
        //  ! 并不像预期的那么容易.

        // registerViewAttrSetter("menu", JsMenuInflater(resourceParser))

        registerViewAttrSetter(Space::class.java, SpaceInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(ViewGroup::class.java, ViewGroupInflater<ViewGroup>(scriptRuntime, resourceParser))
        registerViewAttrSetter(View::class.java, BaseViewInflater<View>(scriptRuntime, resourceParser))
    }

    fun registerViewAttrSetter(clazz: Class<*>, inflater: ViewInflater<*>) {
        registerViewAttrSetter(clazz.name, inflater)
    }

    fun registerViewAttrSetter(className: String, inflater: ViewInflater<*>) {
        mViewAttrSetters[className] = inflater
        inflater.getCreator()?.let { mViewCreators[className] = it }
    }

    @JvmOverloads
    fun inflate(xml: String, parent: ViewGroup? = null, attachToParent: Boolean = parent != null): View {
        val context = newInflateContext()
        return inflate(context, xml, parent, attachToParent)
    }

    fun inflate(context: InflateContext, xml: String, parent: ViewGroup?, attachToParent: Boolean): View {
        layoutInflaterDelegate.beforeInflation(context, xml, parent)?.let { return it }
        val niceXml = convertXml(context, xml)
        return layoutInflaterDelegate.afterInflation(context, doInflation(context, niceXml, parent, attachToParent), niceXml, parent)
    }

    fun newInflateContext() = InflateContext()

    protected fun doInflation(context: InflateContext, xml: String, parent: ViewGroup?, attachToParent: Boolean): View {
        return try {
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val db = dbf.newDocumentBuilder()
            val document = db.parse(ByteArrayInputStream(xml.toByteArray()))
            inflate(context, document.documentElement, parent, attachToParent)
        } catch (e: Exception) {
            throw InflateException(e)
        }
    }

    protected fun convertXml(context: InflateContext?, xml: String?): String {
        return layoutInflaterDelegate.beforeConvertXml(context, xml) ?: try {
            layoutInflaterDelegate.afterConvertXml(context, XmlConverter.convertToAndroidLayout(xml))
        } catch (e: Exception) {
            throw InflateException(e)
        }
    }

    fun inflate(context: InflateContext, node: Node, parent: ViewGroup?, attachToParent: Boolean): View {
        val view = doInflation(context, node, parent, attachToParent)
        (view as? ShouldCallOnFinishInflate)?.onFinishDynamicInflate()
        return view
    }

    protected fun doInflation(context: InflateContext, node: Node, parent: ViewGroup?, attachToParent: Boolean): View {
        var view = layoutInflaterDelegate.beforeInflateView(context, node, parent, attachToParent)
        if (view != null) {
            return view
        }
        val attrs = getAttributesMap(node)
        view = doCreateView(context, node, node.nodeName, parent, attrs)
        if (view is EmptyView) {
            return view
        }
        if (parent != null) {
            parent.addView(view) // have to add to parent to generate layout params
            if (!attachToParent) {
                parent.removeView(view)
            }
        }
        val inflater = applyAttributes(context, view, attrs, parent)
        if (view is ViewGroup && node.hasChildNodes()) {
            inflateChildren(context, inflater, node, view)
            if (inflater is ViewGroupInflater<*>) {
                @Suppress("UNCHECKED_CAST")
                applyPendingAttributesOfChildren(context, inflater as ViewGroupInflater<ViewGroup>, view)
            }
        }
        return layoutInflaterDelegate.afterInflateView(context, view, node, parent, attachToParent)
    }

    protected fun applyPendingAttributesOfChildren(context: InflateContext, inflater: ViewGroupInflater<ViewGroup>, view: ViewGroup?) {
        if (!layoutInflaterDelegate.beforeApplyPendingAttributesOfChildren(context, inflater, view)) {
            view?.let { inflater.applyPendingAttributesOfChildren(it) }
            layoutInflaterDelegate.afterApplyPendingAttributesOfChildren(context, inflater, view)
        }
    }

    fun applyAttributes(context: InflateContext, view: View, attrs: HashMap<String, String>, parent: ViewGroup?): ViewInflater<View> {
        val inflater = getViewInflater(view)
        if (!layoutInflaterDelegate.beforeApplyAttributes(context, view, inflater, attrs, parent)) {
            applyAttributes(context, view, inflater, attrs, parent)
            layoutInflaterDelegate.afterApplyAttributes(context, view, inflater, attrs, parent)
        }
        return inflater
    }

    fun getViewInflater(view: View): ViewInflater<View> {
        var setter = mViewAttrSetters[view.javaClass.name]
        var c: Class<*> = view.javaClass
        while (setter == null && c != View::class.java) {
            c = c.superclass as Class<*>
            setter = mViewAttrSetters[c.name]
        }
        @Suppress("UNCHECKED_CAST")
        return setter as ViewInflater<View>
    }

    protected fun inflateChildren(context: InflateContext, inflater: ViewInflater<View>, node: Node, parent: ViewGroup?) {
        if (layoutInflaterDelegate.beforeInflateChildren(context, inflater, node, parent)) {
            return
        }
        if (inflater.inflateChildren(this, node, parent)) {
            return
        }
        inflateChildren(context, node, parent)
        layoutInflaterDelegate.afterInflateChildren(context, inflater, node, parent)
    }

    fun inflateChildren(context: InflateContext, node: Node, parent: ViewGroup?) {
        val nodeList = node.childNodes
        for (i in 0 until nodeList.length) {
            val currentNode = nodeList.item(i)
            if (currentNode.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            inflate(context, currentNode, parent, true)
        }
    }

    protected fun doCreateView(context: InflateContext, node: Node?, viewName: String, parent: ViewGroup?, attrs: HashMap<String, String>): View {
        val view = layoutInflaterDelegate.beforeCreateView(context, node, viewName, parent)
        return view ?: layoutInflaterDelegate.afterCreateView(context, createViewForName(viewName, attrs, parent), node, viewName, parent)
    }

    fun createViewForName(name: String, attrs: HashMap<String, String>, parent: ViewGroup?): View {
        var niceName = name
        val androidWidgetPrefixBlacklist = listOf(
            "menu", "item", "shape", "paths", "set", "selector", "merge", "view",
        )
        return try {
            if (niceName == "View") {
                return View(context)
            }
            if (!niceName.contains(".") && !androidWidgetPrefixBlacklist.contains(niceName)) {
                niceName = "android.widget.$niceName"
            }
            val creator = mViewCreators[niceName]
            if (creator != null) {
                return creator.create(context, attrs, parent)
            }
            val clazz: Class<*> = runCatching { Class.forName(niceName) }.getOrElse {
                throw WrappedRuntimeException(context.getString(R.string.error_unknown_element_name, name))
            }
            val style = attrs["style"]
            if (style == null) {
                clazz.getConstructor(Context::class.java).newInstance(context) as View
            } else {
                val styleRes = Res.parseStyle(context, style)
                clazz.getConstructor(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(context, null, 0, styleRes) as View
            }
        } catch (e: Exception) {
            throw InflateException(e)
        }
    }

    fun getAttributesMap(currentNode: Node): HashMap<String, String> {
        val attributeMap = currentNode.attributes
        val attributeCount = attributeMap.length
        val attributes = HashMap<String, String>(attributeCount)
        for (j in 0 until attributeCount) {
            val attr = attributeMap.item(j)
            val nodeName = attr.nodeName
            attributes[nodeName] = attr.nodeValue
        }
        return attributes
    }

    private fun applyAttributes(context: InflateContext, view: View, setter: ViewInflater<View>?, attrs: Map<String, String>, parent: ViewGroup?) {
        if (setter != null) {
            for ((key, value) in attrs) {
                val attr = key.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (attr.size) {
                    1 -> applyAttribute(context, setter, view, null, attr[0], value, parent)
                    2 -> applyAttribute(context, setter, view, attr[0], attr[1], value, parent)
                    else -> throw InflateException("Illegal attr name: $key")
                }
            }
            setter.applyPendingAttributes(view, parent)
        } else {
            Log.e(LOG_TAG, "cannot set attributes for view: " + view.javaClass)
        }
    }

    protected fun applyAttribute(context: InflateContext, inflater: ViewInflater<View>, view: View, ns: String?, attrName: String, value: String, parent: ViewGroup?) {
        if (layoutInflaterDelegate.beforeApplyAttribute(context, inflater, view, ns, attrName, value, parent)) {
            return
        }
        val isDynamic = isDynamicValue(value)
        if (isDynamic && isIgnoresDynamicFlags || !isDynamic && isJustDynamicFlags) {
            return
        }
        inflater.setAttr(view, ns, attrName, value, parent)
        layoutInflaterDelegate.afterApplyAttribute(context, inflater, view, ns, attrName, value, parent)
    }

    private val isJustDynamicFlags: Boolean
        get() = inflateFlags == FLAG_JUST_DYNAMIC_ATTRS

    private val isIgnoresDynamicFlags: Boolean
        get() = inflateFlags == FLAG_IGNORES_DYNAMIC_ATTRS

    fun isDynamicValue(value: String): Boolean {
        val i = value.indexOf("{{")
        return i >= 0 && value.indexOf("}}", i + 1) >= 0
    }

    companion object {
        const val FLAG_DEFAULT = 0
        const val FLAG_IGNORES_DYNAMIC_ATTRS = 1
        const val FLAG_JUST_DYNAMIC_ATTRS = 2
        private const val LOG_TAG = "DynamicLayoutInflater"
    }
}