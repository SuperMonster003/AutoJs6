package org.autojs.autojs.runtime.api.augment.ui

import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

class UIWidgetScopeCompatTest {

    private lateinit var cx: Context
    private val noopFunction = object : BaseFunction() {}

    @Before
    fun enterContext() {
        cx = Context.enter()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun renderInternalCanCallDetachedWidgetRenderFunction() {
        val widget = RhinoUtils.newNativeObject()
        val renderFunction = object : BaseFunction() {
            override fun call(cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                assertSame(RhinoUtils.standardObjects, scope)
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
            override fun call(cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                assertSame(RhinoUtils.standardObjects, scope)
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
            override fun call(cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                assertSame(RhinoUtils.standardObjects, scope)
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
            override fun call(cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any? {
                assertSame(RhinoUtils.standardObjects, scope)
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
}
