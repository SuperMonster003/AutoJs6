@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.InflateException
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import android.widget.Space
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.inflater.inflaters.*
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.core.ui.widget.*
import org.autojs.autojs.core.ui.xml.XmlConverter
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

open class DynamicLayoutInflater {

    private var mViewAttrSetters: MutableMap<String, ViewInflater<*>> = HashMap()
    private var mViewCreators: MutableMap<String, ViewCreator<*>> = HashMap()

    @get:ScriptInterface
    @set:ScriptInterface
    var layoutInflaterDelegate: LayoutInflaterDelegate = LayoutInflaterDelegate.NO_OP

    var context: Context? = GlobalAppContext.get()
    val resourceParser: ResourceParser
    var inflateFlags = 0

    constructor(resourceParser: ResourceParser) {
        this.resourceParser = resourceParser
        registerViewAttrSetters()
    }

    constructor(inflater: DynamicLayoutInflater) {
        resourceParser = inflater.resourceParser
        context = inflater.context
        mViewAttrSetters = HashMap(inflater.mViewAttrSetters)
        mViewCreators = HashMap(inflater.mViewCreators)
    }

    protected fun registerViewAttrSetters() {
        registerViewAttrSetter(JsActionMenuView::class.java, JsActionMenuViewInflater(resourceParser))
        registerViewAttrSetter(JsAppBarLayout::class.java, JsAppBarLayoutInflater(resourceParser))
        registerViewAttrSetter(JsButton::class.java, JsButtonInflater(resourceParser))
        registerViewAttrSetter(JsCanvasView::class.java, JsCanvasViewInflater(resourceParser))
        registerViewAttrSetter(JsCardView::class.java, JsCardViewInflater(resourceParser))
        registerViewAttrSetter(JsCalendarView::class.java, JsCalendarViewInflater(resourceParser))
        registerViewAttrSetter(JsCheckBox::class.java, JsCheckBoxInflater(resourceParser))
        registerViewAttrSetter(JsCheckedTextView::class.java, JsCheckedTextViewInflater(resourceParser))
        registerViewAttrSetter(JsChronometer::class.java, JsChronometerInflater(resourceParser))
        registerViewAttrSetter(JsConsoleView::class.java, JsConsoleViewInflater(resourceParser))
        registerViewAttrSetter(JsDatePicker::class.java, JsDatePickerInflater(resourceParser))
        registerViewAttrSetter(JsDrawerLayout::class.java, JsDrawerLayoutInflater(resourceParser))
        registerViewAttrSetter(JsEditText::class.java, JsEditTextInflater(resourceParser))
        registerViewAttrSetter(JsFloatingActionButton::class.java, JsFloatingActionButtonInflater(resourceParser))
        registerViewAttrSetter(JsFrameLayout::class.java, JsFrameLayoutInflater(resourceParser))
        registerViewAttrSetter(JsGridView::class.java, JsGridViewInflater<JsGridView>(resourceParser))
        registerViewAttrSetter(JsImageButton::class.java, JsImageButtonInflater(resourceParser))
        registerViewAttrSetter(JsImageView::class.java, JsImageViewInflater(resourceParser))
        registerViewAttrSetter(JsImageSwitcher::class.java, JsImageSwitcherInflater(resourceParser))
        registerViewAttrSetter(JsLinearLayout::class.java, JsLinearLayoutInflater(resourceParser))
        registerViewAttrSetter(JsListView::class.java, JsListViewInflater<JsListView>(resourceParser))
        registerViewAttrSetter(JsNumberPicker::class.java, JsNumberPickerInflater(resourceParser))
        registerViewAttrSetter(JsProgressBar::class.java, JsProgressBarInflater(resourceParser))
        registerViewAttrSetter(JsQuickContactBadge::class.java, JsQuickContactBadgeInflater(resourceParser))
        registerViewAttrSetter(JsRadioButton::class.java, JsRadioButtonInflater(resourceParser))
        registerViewAttrSetter(JsRadioGroup::class.java, JsRadioGroupInflater(resourceParser))
        registerViewAttrSetter(JsRatingBar::class.java, JsRatingBarInflater(resourceParser))
        registerViewAttrSetter(JsRelativeLayout::class.java, JsRelativeLayoutInflater(resourceParser))
        registerViewAttrSetter(JsScrollView::class.java, JsScrollViewInflater(resourceParser))
        registerViewAttrSetter(JsSearchView::class.java, JsSearchViewInflater(resourceParser))
        registerViewAttrSetter(JsSeekBar::class.java, JsSeekBarInflater(resourceParser))
        registerViewAttrSetter(JsSpinner::class.java, JsSpinnerInflater(resourceParser))
        registerViewAttrSetter(JsSwitch::class.java, JsSwitchInflater(resourceParser))
        registerViewAttrSetter(JsTabLayout::class.java, JsTabLayoutInflater(resourceParser))
        registerViewAttrSetter(JsTextClock::class.java, JsTextClockInflater(resourceParser))
        registerViewAttrSetter(JsTextSwitcher::class.java, JsTextSwitcherInflater(resourceParser))
        registerViewAttrSetter(JsTimePicker::class.java, JsTimePickerInflater(resourceParser))
        registerViewAttrSetter(JsToggleButton::class.java, JsToggleButtonInflater(resourceParser))
        registerViewAttrSetter(JsToolbar::class.java, JsToolbarInflater(resourceParser))
        registerViewAttrSetter(JsVideoView::class.java, JsVideoViewInflater(resourceParser))
        registerViewAttrSetter(JsViewFlipper::class.java, JsViewFlipperInflater(resourceParser))
        registerViewAttrSetter(JsViewPager::class.java, JsViewPagerInflater(resourceParser))
        registerViewAttrSetter(JsViewSwitcher::class.java, JsViewSwitcherInflater(resourceParser))
        registerViewAttrSetter(JsWebView::class.java, JsWebViewInflater(resourceParser))

        when (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            true -> registerViewAttrSetter(JsTextViewLegacy::class.java, JsTextViewLegacyInflater(resourceParser))
            else -> registerViewAttrSetter(JsTextView::class.java, JsTextViewInflater(resourceParser))
        }

        // TODO by SuperMonster003 on Jun 8, 2023.
        //  ! Android XML like menu, shape, paths and so forth.
        //  ! Not easy as expected.

        // registerViewAttrSetter("menu", JsMenuInflater(resourceParser))

        registerViewAttrSetter(Space::class.java, SpaceInflater(resourceParser))
        registerViewAttrSetter(ViewGroup::class.java, ViewGroupInflater<ViewGroup>(resourceParser))
        registerViewAttrSetter(View::class.java, BaseViewInflater<View>(resourceParser))
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
            c = c.superclass
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

    protected fun doCreateView(context: InflateContext?, node: Node?, viewName: String, parent: ViewGroup?, attrs: HashMap<String, String>): View {
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
                context?.let { ctx -> return creator.create(ctx, attrs, parent) }
            }
            val clazz = Class.forName(niceName)
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