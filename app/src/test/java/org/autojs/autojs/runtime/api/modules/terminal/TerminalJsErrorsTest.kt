package org.autojs.autojs.runtime.api.modules.terminal

import org.autojs.autojs.core.plugin.terminal.TerminalError
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Bare Rhino: the `TerminalError` constructor and the Kotlin-to-JavaScript error mapping without a ScriptRuntime (plugin roadmap P4.1, appendix A.4). */
class TerminalJsErrorsTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun constructorIsCreatedOncePerScopeAndBehavesLikeAnErrorSubclass() {
        val constructor = TerminalJsErrors.constructor(context, scope)
        assertSame(constructor, TerminalJsErrors.constructor(context, scope))
        ScriptableObject.putProperty(scope, "TerminalError", constructor)
        val result = evaluate(
            """
            var e = new TerminalError('boom', 'PTY_FAILED', 's-1');
            var plain = TerminalError('x');
            JSON.stringify({
                isError: e instanceof Error,
                isTerminalError: e instanceof TerminalError,
                plainIsTerminalError: plain instanceof TerminalError,
                name: e.name,
                message: e.message,
                code: e.code,
                sessionId: e.sessionId,
                text: String(e),
                keys: Object.keys(e),
                stack: typeof e.stack,
                defaults: [plain.code, plain.message, typeof plain.sessionId],
                ownName: Object.prototype.hasOwnProperty.call(e, 'name'),
            })
            """.trimIndent(),
        )
        assertEquals(
            """{"isError":true,"isTerminalError":true,"plainIsTerminalError":true,"name":"TerminalError","message":"boom","code":"PTY_FAILED","sessionId":"s-1",""" +
                """"text":"TerminalError [PTY_FAILED]: boom","keys":["code","sessionId"],"stack":"string","defaults":["INTERNAL","x","undefined"],"ownName":false}""",
            result,
        )
    }

    @Test
    fun pluginFailuresBecomeTerminalErrorInstancesWithSessionIdAndHiddenJavaException() {
        val cause = TerminalPluginException(TerminalError(TerminalErrorCodes.SESSION_NOT_FOUND, "no such session", "s-7"))
        val value = TerminalJsErrors.toJs(context, scope, cause) as Scriptable
        assertTrue(isTerminalError(value))
        assertEquals(TerminalErrorCodes.SESSION_NOT_FOUND, ScriptableObject.getProperty(value, "code"))
        assertEquals("no such session", ScriptableObject.getProperty(value, "message"))
        assertEquals("s-7", ScriptableObject.getProperty(value, "sessionId"))
        assertSame(cause, Context.jsToJava(ScriptableObject.getProperty(value, "javaException"), Throwable::class.java))
        assertFalse("javaException stays hidden from enumeration", (value as ScriptableObject).ids.contains("javaException"))

        val global = TerminalJsErrors.toJs(context, scope, TerminalPluginException(TerminalError(TerminalErrorCodes.PLUGIN_UNAVAILABLE, "install the plugin"))) as Scriptable
        assertEquals(TerminalErrorCodes.PLUGIN_UNAVAILABLE, ScriptableObject.getProperty(global, "code"))
        assertFalse("no sessionId without a session", ScriptableObject.hasProperty(global, "sessionId"))
    }

    @Test
    fun hostAndGuardFailuresMapToCodesWhileScriptValuesPassThrough() {
        val guard = TerminalJsErrors.toJs(context, scope, WrappedIllegalArgumentException("terminal.close expects one argument")) as Scriptable
        assertTrue(isTerminalError(guard))
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(guard, "code"))
        assertTrue(ScriptableObject.getProperty(guard, "message").toString().contains("expects one argument"))

        val internal = TerminalJsErrors.toJs(context, scope, IllegalStateException("broken")) as Scriptable
        assertEquals(TerminalErrorCodes.INTERNAL, ScriptableObject.getProperty(internal, "code"))
        val argument = TerminalJsErrors.toJs(context, scope, IllegalArgumentException("bad")) as Scriptable
        assertEquals(TerminalErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(argument, "code"))

        val thrown = NativeObject()
        assertSame(thrown, TerminalJsErrors.toJs(context, scope, JavaScriptException(thrown, "script.js", 1)))

        val ecma = try {
            evaluate("noSuchVariable.x")
            throw AssertionError("ReferenceError expected")
        } catch (e: EcmaError) {
            e
        }
        val wrapped = TerminalJsErrors.toJs(context, scope, ecma) as Scriptable
        assertFalse("script errors keep their own class", isTerminalError(wrapped))
        assertEquals("ReferenceError", ScriptableObject.getProperty(wrapped, "name"))
    }

    @Test
    fun jsExceptionCarriesTheTerminalErrorAndKeepsScriptExceptions() {
        val failure = TerminalJsErrors.failure(TerminalErrorCodes.SESSION_CLOSED, "the script is exiting")
        assertEquals(TerminalErrorCodes.SESSION_CLOSED, failure.code)
        val exception = TerminalJsErrors.jsException(context, scope, failure)
        val value = exception.value as Scriptable
        assertTrue(isTerminalError(value))
        assertEquals("the script is exiting", ScriptableObject.getProperty(value, "message"))
        assertNotNull(exception.message)

        val own = JavaScriptException("text", "script.js", 3)
        assertSame(own, TerminalJsErrors.jsException(context, scope, own))

        val withSession = TerminalJsErrors.failure(TerminalErrorCodes.TIMEOUT, "did not exit", "s-3")
        assertEquals("s-3", withSession.error.sessionId)
        assertEquals("s-3", ScriptableObject.getProperty(TerminalJsErrors.jsException(context, scope, withSession).value as Scriptable, "sessionId"))

        val unknown = TerminalJsErrors.failure("NOPE", "odd")
        assertEquals(TerminalErrorCodes.INTERNAL, unknown.code)
        assertTrue(unknown.error.message.contains("unknown error code NOPE"))
    }

    private fun isTerminalError(value: Scriptable): Boolean =
        TerminalJsErrors.constructor(context, scope).hasInstance(value)

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "test.js", 1, null)
}
