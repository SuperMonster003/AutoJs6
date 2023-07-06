package org.autojs.autojs.core.ui.xml

import android.os.Build
import android.view.View
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import android.widget.Space
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.widget.*
import org.autojs.autojs.core.ui.widget.JsCheckBox
import org.autojs.autojs.core.ui.widget.JsSwitch
import org.autojs.autojs.core.ui.xml.AttributeHandler.AttrNameRouter
import org.autojs.autojs.core.ui.xml.AttributeHandler.DimenHandler
import org.autojs.autojs.core.ui.xml.AttributeHandler.IdHandler
import org.autojs.autojs.core.ui.xml.AttributeHandler.MappedAttributeHandler
import org.autojs.autojs.core.ui.xml.AttributeHandler.OrientationHandler
import org.autojs.autojs.core.ui.xml.NodeHandler.MapNameHandler
import org.autojs.autojs.core.ui.xml.NodeHandler.NameRouter
import org.autojs.autojs.core.ui.xml.NodeHandler.VerticalHandler
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Created by Stardust on 2017/5/14.
 * Modified by SuperMonster003 as of Feb 5, 2022.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
object XmlConverter {

    private val NODE_HANDLER: NodeHandler = NameRouter()
        .handler("vertical", VerticalHandler(JsLinearLayout::class.java.name))
        .defaultHandler(
            MapNameHandler()
                .map("actionmenu", JsActionMenuView::class.java.name)
                .map("appbar", JsAppBarLayout::class.java.name)
                .map(arrayOf("button", "btn"), JsButton::class.java.name)
                .map("canvas", JsCanvasView::class.java.name)
                .map("card", JsCardView::class.java.name)
                .map("calendar", JsCalendarView::class.java.name)
                .map("checkbox", JsCheckBox::class.java.name)
                .map("checkedtext", JsCheckedTextView::class.java.name)
                .map("chronometer", JsChronometer::class.java.name)
                .map("console", JsConsoleView::class.java.name)
                .map("datepicker", JsDatePicker::class.java.name)
                .map("drawer", JsDrawerLayout::class.java.name)
                .map(arrayOf("input", "edittext"), JsEditText::class.java.name)
                .map("fab", JsFloatingActionButton::class.java.name)
                .map("frame", JsFrameLayout::class.java.name)
                .map("grid", JsGridView::class.java.name)
                .map("imagebutton", JsImageButton::class.java.name)
                .map(arrayOf("image", "img"), JsImageView::class.java.name)
                .map(arrayOf("linear", "horizontal"), JsLinearLayout::class.java.name)
                .map("list", JsListView::class.java.name)
                .map("numberpicker", JsNumberPicker::class.java.name)
                .map("progressbar", JsProgressBar::class.java.name)
                .map("quickcontactbadge", JsQuickContactBadge::class.java.name)
                .map(arrayOf("radio", "radiobutton"), JsRadioButton::class.java.name)
                .map(arrayOf("radiogroup", "radios"), JsRadioGroup::class.java.name)
                .map("ratingbar", JsRatingBar::class.java.name)
                .map("relative", JsRelativeLayout::class.java.name)
                .map("scroll", JsScrollView::class.java.name)
                .map("search", JsSearchView::class.java.name)
                .map("seekbar", JsSeekBar::class.java.name)
                .map("spinner", JsSpinner::class.java.name)
                .map("switch", JsSwitch::class.java.name)
                .map(arrayOf("tabs", "tab"), JsTabLayout::class.java.name)
                .map("textclock", JsTextClock::class.java.name)
                .map("textswitcher", JsTextSwitcher::class.java.name)
                .map("timepicker", JsTimePicker::class.java.name)
                .map("togglebutton", JsToggleButton::class.java.name)
                .map("toolbar", JsToolbar::class.java.name)
                .map("video", JsVideoView::class.java.name)
                .map("viewflipper", JsViewFlipper::class.java.name)
                .map("viewpager", JsViewPager::class.java.name)
                .map("viewswitcher", JsViewSwitcher::class.java.name)
                .map(arrayOf("webview", "web"), JsWebView::class.java.name)
                .map(
                    "text",
                    // @Reference to TonyJiangWJ/Auto.js on Mar 20, 2022
                    when (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        true -> JsTextViewLegacy::class.java.name
                        else -> JsTextView::class.java.name
                    }
                )
                .map("space", Space::class.java.name)
                .map("view", View::class.java.name)
        )

    private val ATTRIBUTE_HANDLER: AttributeHandler = AttrNameRouter()
        .handler("w", DimenHandler("width"))
        .handler("h", DimenHandler("height"))
        .handler("size", DimenHandler("textSize"))
        .handler("id", IdHandler())
        .handler("vertical", OrientationHandler())
        .handler("margin", DimenHandler("layout_margin"))
        .handler("marginLeft", DimenHandler("layout_marginLeft"))
        .handler("marginRight", DimenHandler("layout_marginRight"))
        .handler("marginTop", DimenHandler("layout_marginTop"))
        .handler("marginBottom", DimenHandler("layout_marginBottom"))
        .handler("marginStart", DimenHandler("layout_marginStart"))
        .handler("marginEnd", DimenHandler("layout_marginEnd"))
        .handler("marginVertical", DimenHandler("layout_marginVertical"))
        .handler("marginHorizontal", DimenHandler("layout_marginHorizontal"))
        .handler("alignParentBottom", DimenHandler("layout_alignParentBottom"))
        .handler("alignParentTop", DimenHandler("layout_alignParentTop"))
        .handler("alignParentLeft", DimenHandler("layout_alignParentLeft"))
        .handler("alignParentStart", DimenHandler("layout_alignParentStart"))
        .handler("alignParentRight", DimenHandler("layout_alignParentRight"))
        .handler("alignParentEnd", DimenHandler("layout_alignParentEnd"))
        .handler("centerHorizontal", DimenHandler("layout_centerHorizontal"))
        .handler("centerVertical", DimenHandler("layout_centerVertical"))
        .handler("centerInParent", DimenHandler("layout_centerInParent"))
        .handler("below", DimenHandler("layout_below"))
        .handler("above", DimenHandler("layout_above"))
        .handler("toLeftOf", DimenHandler("layout_toLeftOf"))
        .handler("toRightOf", DimenHandler("layout_toRightOf"))
        .handler("alignBottom", DimenHandler("layout_alignBottom"))
        .handler("alignTop", DimenHandler("layout_alignTop"))
        .handler("alignLeft", DimenHandler("layout_alignLeft"))
        .handler("alignStart", DimenHandler("layout_alignStart"))
        .handler("alignRight", DimenHandler("layout_alignRight"))
        .handler("alignEnd", DimenHandler("layout_alignEnd"))
        .defaultHandler(MappedAttributeHandler().mapName("align", "layout_gravity"))

    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun convertToAndroidLayout(xml: String?): String {
        return convertToAndroidLayout(InputSource(StringReader(xml)))
    }

    @Throws(ParserConfigurationException::class, IOException::class, SAXException::class)
    fun convertToAndroidLayout(source: InputSource?): String {
        val layoutXml = StringBuilder()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(source)
        handleNode(document.firstChild, "xmlns:android=\"http://schemas.android.com/apk/res/android\"", layoutXml)
        return layoutXml.toString()
    }

    private fun handleNode(node: Node, namespace: String, layoutXml: StringBuilder) {
        val nodeName = node.nodeName
        val mappedNodeName = NODE_HANDLER.handleNode(node, namespace, layoutXml)
        handleText(nodeName, node.textContent, layoutXml)
        handleAttributes(nodeName, node.attributes, layoutXml)
        layoutXml.append(">\n")
        handleChildren(node.childNodes, layoutXml)
        layoutXml.append("</").append(mappedNodeName).append(">\n")
    }

    private fun handleText(nodeName: String, textContent: String?, layoutXml: StringBuilder) {
        if (!textContent.isNullOrEmpty()) {
            if (nodeName == "text" || nodeName == "button" || nodeName == "input") {
                layoutXml.append("android:text=\"").append(textContent).append("\"\n")
            }
        }
    }

    private fun handleChildren(nodes: NodeList?, layoutXml: StringBuilder) {
        nodes ?: return
        val len = nodes.length
        for (i in 0 until len) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                handleNode(node, "", layoutXml)
            }
        }
    }

    private fun handleAttributes(nodeName: String, attributes: NamedNodeMap?, layoutXml: StringBuilder) {
        attributes ?: return
        val len = attributes.length
        for (i in 0 until len) {
            val attr = attributes.item(i)
            handleAttribute(nodeName, attr, layoutXml)
        }
    }

    private fun handleAttribute(nodeName: String, attr: Node, layoutXml: StringBuilder) {
        ATTRIBUTE_HANDLER.handle(nodeName, attr, layoutXml)
    }

}