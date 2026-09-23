package org.autojs.autojs.rhino.debug

import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScopeObject
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.debug.DebugFrame
import org.mozilla.javascript.debug.DebuggableScript
import org.mozilla.javascript.debug.Debugger

class DimScopeCompatibilityTest {
    @Test
    fun debuggerTracksFramesAndEvaluatesWithTheOriginalThisValue() {
        Context.enter().use { cx ->
            cx.languageVersion = Context.VERSION_ES6
            cx.optimizationLevel = -1
            cx.isGeneratingDebug = true
            val scope = cx.initStandardObjects()
            val dim = Dim()
            dim.setGuiCallback(object : DebugCallbackInternal {
                override fun updateSourceText(sourceInfo: Dim.SourceInfo) = Unit
                override fun enterInterrupt(lastFrame: Dim.StackFrame, threadTitle: String, alertMessage: String?) = Unit
                override fun isGuiEventThread() = false
                override fun dispatchNextGuiEvent() = Unit
                override fun shouldAttachDebugger(engine: RhinoJavaScriptEngine) = true
            })
            val debugType = Dim::class.java.getDeclaredField("IPROXY_DEBUG").apply { isAccessible = true }.getInt(null)
            val proxyClass = Dim::class.java.declaredClasses.single { it.simpleName == "DimIProxy" }
            val proxy = proxyClass.getDeclaredConstructor(Dim::class.java, Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }.newInstance(dim, debugType) as Debugger
            val evaluate = Dim::class.java.getDeclaredMethod("do_eval", Context::class.java, Dim.StackFrame::class.java, String::class.java).apply {
                isAccessible = true
            }
            val contextData = Dim.ContextData()
            var inspected = false
            cx.setDebugger(object : Debugger {
                override fun handleCompilationDone(cx: Context, fnOrScript: DebuggableScript, source: String) =
                    proxy.handleCompilationDone(cx, fnOrScript, source)

                override fun getFrame(cx: Context, fnOrScript: DebuggableScript): DebugFrame? {
                    val frame = proxy.getFrame(cx, fnOrScript) ?: return null
                    return object : DebugFrame {
                        override fun onEnter(cx: Context, scope: VarScope, thisObj: Any?, args: Array<out Any?>) {
                            frame.onEnter(cx, scope, thisObj, args)
                            if (fnOrScript.functionName == "probe") {
                                assertEquals(2, contextData.frameCount())
                                assertEquals("48", evaluate.invoke(null, cx, frame, "value + this"))
                                inspected = true
                            }
                        }

                        override fun onLineChange(cx: Context, lineNumber: Int) = frame.onLineChange(cx, lineNumber)
                        override fun onExceptionThrown(cx: Context, ex: Throwable) = frame.onExceptionThrown(cx, ex)
                        override fun onExit(cx: Context, byThrow: Boolean, resultOrException: Any?) = frame.onExit(cx, byThrow, resultOrException)
                        override fun onDebuggerStatement(cx: Context) = frame.onDebuggerStatement(cx)
                    }
                }
            }, contextData)

            cx.evaluateString(scope, "function probe(value) { 'use strict'; return value; } probe.call(7, 41);", "debug-scope", 1, null)

            assertTrue("The host debugger must receive the new onEnter signature", inspected)
            assertEquals(0, contextData.frameCount())
        }
    }

    @Test
    fun debuggerCanInspectLexicalScopes() {
        Context.enter().use { cx ->
            val parent = cx.initStandardObjects()
            val scope = ScopeObject(parent)
            scope.put("localValue", scope, 42)
            val dim = Dim()
            val getIds = Dim::class.java.getDeclaredMethod("getObjectIdsImpl", Context::class.java, Any::class.java).apply {
                isAccessible = true
            }
            val getProperty = Dim::class.java.getDeclaredMethod("getObjectPropertyImpl", Context::class.java, Any::class.java, Any::class.java).apply {
                isAccessible = true
            }
            val ids = getIds.invoke(dim, cx, scope) as Array<*>
            assertTrue(ids.contains("localValue"))
            assertTrue(ids.contains("__parent__"))
            assertEquals(42, getProperty.invoke(dim, cx, scope, "localValue"))
            assertEquals(parent, getProperty.invoke(dim, cx, scope, "__parent__"))
        }
    }
}
