package org.autojs.autojs.runtime.api.modules.ui

import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.VarScope

class UIWidgetScopeCompatTest {

    private lateinit var cx: Context
    private lateinit var realm: TopLevel
    private val noopFunction = object : BaseFunction() {}

    @Before
    fun enterContext() {
        cx = Context.enter()
        realm = with(RhinoUtils) { cx.getOrCreateStandardObjects() }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun renderInternalCanCallDetachedWidgetRenderFunction() {
        val widget = RhinoUtils.newNativeObject()
        val renderFunction = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: VarScope?, thisObj: Any?, args: Array<out Any?>): Any {
                assertSame(realm, scope)
                assertSame(widget, thisObj)
                return "<text />"
            }
        }
        widget.defineProp("render", renderFunction)

        assertEquals("<text />", UIWidget.renderInternal(cx, widget, emptyArray(), noopFunction))
    }

    @Test
    fun setAttrCanCallDetachedAttrSetterFunction() {
        val widget = RhinoUtils.newNativeObject()
        val attrsObject = RhinoUtils.newNativeObject()
        val attrValue = RhinoUtils.newNativeObject()
        var called = false
        val setterFunction = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: VarScope?, thisObj: Any?, args: Array<out Any?>): Any? {
                assertSame(realm, scope)
                assertSame(attrValue, thisObj)
                assertEquals("text", args[1])
                assertEquals("value", args[2])
                called = true
                return null
            }
        }

        attrValue.defineProp("setter", setterFunction)
        attrsObject.defineProp("text", attrValue)
        widget.defineProp("__attrs__", attrsObject)

        UIWidget.setAttr(cx, widget, arrayOf(null, "text", "value", null), noopFunction)

        assertTrue(called)
    }

    @Test
    fun getAttrCanCallDetachedAttrGetterFunction() {
        val widget = RhinoUtils.newNativeObject()
        val attrsObject = RhinoUtils.newNativeObject()
        val attrValue = RhinoUtils.newNativeObject()
        val getterFunction = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: VarScope?, thisObj: Any?, args: Array<out Any?>): Any {
                assertSame(realm, scope)
                assertSame(attrValue, thisObj)
                assertEquals("text", args[1])
                return "value"
            }
        }

        attrValue.defineProp("getter", getterFunction)
        attrsObject.defineProp("text", attrValue)
        widget.defineProp("__attrs__", attrsObject)

        assertEquals("value", UIWidget.getAttr(cx, widget, arrayOf(null, "text", null), noopFunction))
    }

    @Test
    fun defineAttrCanCallDetachedApplierFunction() {
        val widget = RhinoUtils.newNativeObject()
        val attrsObject = RhinoUtils.newNativeObject()
        var called = false
        val applierFunction = object : BaseFunction() {
            override fun call(cx: Context?, nt: Any?, scope: VarScope?, thisObj: Any?, args: Array<out Any?>): Any? {
                assertSame(realm, scope)
                assertSame(widget, thisObj)
                assertEquals("text", args[1])
                assertEquals("value", args[2])
                called = true
                return null
            }
        }
        widget.defineProp("__attrs__", attrsObject)

        UIWidget.defineAttr(cx, widget, arrayOf("text", applierFunction), noopFunction)
        UIWidget.setAttr(cx, widget, arrayOf(null, "text", "value", null), noopFunction)

        assertEquals("value", widget.prop("text"))
        assertTrue(called)
    }

    @Test
    fun renderInternalAcceptsProxyWrappedFunction() {
        val widget = RhinoUtils.newNativeObject(realm)
        widget.defineProp("tag", "proxy-render")
        val renderFunction = evaluate(
            "new Proxy(function() { return '<' + this.tag + ' />'; }, {})",
        )
        assertTrue(renderFunction is Callable)
        assertFalse(renderFunction is BaseFunction)
        widget.defineProp("render", renderFunction)

        assertEquals("<proxy-render />", Context.toString(UIWidget.renderInternal(cx, widget, emptyArray(), noopFunction)))
    }

    @Test
    fun defineAttrAcceptsProxyWrappedGetterAndSetter() {
        val widget = RhinoUtils.newNativeObject(realm)
        val attrsObject = RhinoUtils.newNativeObject(realm)
        val getter = evaluate(
            "new Proxy(function(view, name) { return name + ':' + this.seen; }, {})",
        )
        val setter = evaluate(
            "new Proxy(function(view, name, value) { this.seen = value; }, {})",
        )
        widget.defineProp("__attrs__", attrsObject)

        UIWidget.defineAttr(cx, widget, arrayOf("text", getter, setter), noopFunction)
        UIWidget.setAttr(cx, widget, arrayOf(null, "text", "value", null), noopFunction)

        assertEquals("text:value", Context.toString(UIWidget.getAttr(cx, widget, arrayOf(null, "text", null), noopFunction)))
    }

    @Test
    fun defineAttrAcceptsProxyWrappedApplier() {
        val widget = RhinoUtils.newNativeObject(realm)
        val attrsObject = RhinoUtils.newNativeObject(realm)
        val applier = evaluate(
            "new Proxy(function(view, name, value) { this.applied = name + ':' + value; }, {})",
        )
        widget.defineProp("__attrs__", attrsObject)

        UIWidget.defineAttr(cx, widget, arrayOf("text", applier), noopFunction)
        UIWidget.setAttr(cx, widget, arrayOf(null, "text", "value", null), noopFunction)

        assertEquals("text:value", Context.toString(widget.prop("applied")))
    }

    private fun evaluate(source: String): Any? =
        cx.evaluateString(realm, source, "UIWidgetScopeCompatTest", 1, null)
}
