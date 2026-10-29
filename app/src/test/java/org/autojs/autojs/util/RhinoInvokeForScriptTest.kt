package org.autojs.autojs.util

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.lang.reflect.Method

/**
 * api-coverage Q116: a host member that calls a Kotlin function reflectively (`Color#toHex` and the other Color members
 * proxied by ColorNativeObject, images.getSimilarity, the client options of http) used to let the
 * [java.lang.reflect.InvocationTargetException] reach Rhino, and the interpreter failed with a ClassCastException past
 * the script's try / catch. [RhinoUtils.invokeForScript] throws the target exception as Rhino's own Java members do.
 *
 * zh-CN: api-coverage Q116: 反射调用 Kotlin 函数的宿主成员 (ColorNativeObject 代理的 `Color#toHex` 等成员, images.getSimilarity,
 * http 的 client 选项) 曾让 InvocationTargetException 到达 Rhino, 解释器以 ClassCastException 失败并越过脚本的 try / catch.
 * [RhinoUtils.invokeForScript] 按 Rhino 自身 Java 成员的方式抛出目标异常.
 */
class RhinoInvokeForScriptTest {

    private lateinit var cx: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun setUp() {
        cx = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(cx, false) }
        define("member") { args -> RhinoUtils.invokeForScript(TARGET, null, args) }
        define("rawMember") { args -> TARGET.invoke(null, args) }
    }

    @After
    fun tearDown() = Context.exit()

    @Test
    fun aJavaExceptionIsCaughtWithItsTypeAndMessage() {
        assertEquals("JavaException|java.lang.IllegalArgumentException: bad value|java.lang.IllegalArgumentException: bad value", caught("iae"))
        assertEquals("JavaException|java.lang.Exception: checked one|java.lang.Exception: checked one", caught("checked"))
    }

    @Test
    fun aRhinoExceptionIsCaughtAsIs() {
        val result = caught("rhino")
        assertTrue(result, result.startsWith("InternalError|") && result.contains("wrapped one") && result.endsWith("|none"))
    }

    @Test
    fun aJavaErrorStillPassesTheScript() {
        assertThrows(NotImplementedError::class.java) { eval("try { member('error') } catch (e) { 'caught' }") }
    }

    @Test
    fun theResultIsReturnedAsIs() {
        assertEquals(42.0, Context.toNumber(eval("member('ok')")), 0.0)
    }

    @Test
    fun theUnwrappedCallFailsPastTheTryCatch() {
        // The defect this guards against: the interpreter itself fails, the catch block never runs.
        assertThrows(ClassCastException::class.java) { eval("try { rawMember('iae') } catch (e) { 'caught' }") }
    }

    private fun caught(kind: String): String = eval(
        """
        (function () {
            try {
                member('$kind');
                return 'not thrown';
            } catch (e) {
                return e.name + '|' + e.message + '|' + (e.javaException ? String(e.javaException) : 'none');
            }
        })()
        """
    ).toString()

    private fun eval(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    private fun define(name: String, body: (Array<out Any?>) -> Any?) {
        ScriptableObject.putProperty(scope.globalThis, name, RhinoUtils.newBaseFunction(scope, name, body, RhinoUtils.NOT_CONSTRUCTABLE))
    }

    @Suppress("unused")
    object Target {

        @JvmStatic
        fun run(args: Array<Any?>): Any = when (args.firstOrNull()) {
            "iae" -> throw IllegalArgumentException("bad value")
            "checked" -> throw Exception("checked one")
            "rhino" -> throw WrappedIllegalArgumentException("wrapped one")
            "error" -> throw NotImplementedError("not here")
            else -> 42
        }

    }

    private companion object {
        val TARGET: Method = Target::class.java.getMethod("run", Array<Any>::class.java)
    }

}
