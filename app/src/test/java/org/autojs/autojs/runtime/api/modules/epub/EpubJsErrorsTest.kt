package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.core.plugin.epub.EpubError
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.plugin.epub.api.EpubErrorCodes
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

/** Bare Rhino: the `EpubError` constructor and the Kotlin-to-JavaScript error mapping without a ScriptRuntime (plugin roadmap P6.1). */
class EpubJsErrorsTest {

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
        val constructor = EpubJsErrors.constructor(context, scope)
        assertSame(constructor, EpubJsErrors.constructor(context, scope))
        ScriptableObject.putProperty(scope, "EpubError", constructor)
        val result = evaluate(
            """
            var e = new EpubError('boom', 'FILE_NOT_FOUND');
            var plain = EpubError('x');
            JSON.stringify({
                isError: e instanceof Error,
                isEpubError: e instanceof EpubError,
                plainIsEpubError: plain instanceof EpubError,
                name: e.name,
                message: e.message,
                code: e.code,
                text: String(e),
                keys: Object.keys(e),
                stack: typeof e.stack,
                defaults: [plain.code, plain.message],
                ownName: Object.prototype.hasOwnProperty.call(e, 'name'),
            })
            """.trimIndent(),
        )
        assertEquals(
            """{"isError":true,"isEpubError":true,"plainIsEpubError":true,"name":"EpubError","message":"boom","code":"FILE_NOT_FOUND",""" +
                """"text":"EpubError [FILE_NOT_FOUND]: boom","keys":["code"],"stack":"string","defaults":["INTERNAL","x"],"ownName":false}""",
            result,
        )
    }

    @Test
    fun pluginFailuresBecomeEpubErrorInstancesWithHiddenJavaException() {
        val cause = EpubPluginException(EpubError(EpubErrorCodes.RESOURCE_NOT_FOUND, "no such resource"))
        val value = EpubJsErrors.toJs(context, scope, cause) as Scriptable
        assertTrue(isEpubError(value))
        assertEquals(EpubErrorCodes.RESOURCE_NOT_FOUND, ScriptableObject.getProperty(value, "code"))
        assertEquals("no such resource", ScriptableObject.getProperty(value, "message"))
        assertSame(cause, Context.jsToJava(ScriptableObject.getProperty(value, "javaException"), Throwable::class.java))
        assertFalse("javaException stays hidden from enumeration", (value as ScriptableObject).ids.contains("javaException"))
    }

    @Test
    fun hostAndGuardFailuresMapToCodesWhileScriptValuesPassThrough() {
        val guard = EpubJsErrors.toJs(context, scope, WrappedIllegalArgumentException("epub.open expects one argument")) as Scriptable
        assertTrue(isEpubError(guard))
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(guard, "code"))
        assertTrue(ScriptableObject.getProperty(guard, "message").toString().contains("expects one argument"))

        val internal = EpubJsErrors.toJs(context, scope, IllegalStateException("broken")) as Scriptable
        assertEquals(EpubErrorCodes.INTERNAL, ScriptableObject.getProperty(internal, "code"))
        val argument = EpubJsErrors.toJs(context, scope, IllegalArgumentException("bad")) as Scriptable
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(argument, "code"))

        val thrown = NativeObject()
        assertSame(thrown, EpubJsErrors.toJs(context, scope, JavaScriptException(thrown, "script.js", 1)))

        val ecma = try {
            evaluate("noSuchVariable.x")
            throw AssertionError("ReferenceError expected")
        } catch (e: EcmaError) {
            e
        }
        val wrapped = EpubJsErrors.toJs(context, scope, ecma) as Scriptable
        assertFalse("script errors keep their own class", isEpubError(wrapped))
        assertEquals("ReferenceError", ScriptableObject.getProperty(wrapped, "name"))
    }

    @Test
    fun jsExceptionCarriesTheEpubErrorAndKeepsScriptExceptions() {
        val failure = EpubJsErrors.failure(EpubErrorCodes.SESSION_CLOSED, "the book is closed")
        assertEquals(EpubErrorCodes.SESSION_CLOSED, failure.code)
        val exception = EpubJsErrors.jsException(context, scope, failure)
        val value = exception.value as Scriptable
        assertTrue(isEpubError(value))
        assertEquals("the book is closed", ScriptableObject.getProperty(value, "message"))
        assertNotNull(exception.message)

        val own = JavaScriptException("text", "script.js", 3)
        assertSame(own, EpubJsErrors.jsException(context, scope, own))

        val unknown = EpubJsErrors.failure("NOPE", "odd")
        assertEquals(EpubErrorCodes.INTERNAL, unknown.code)
        assertTrue(unknown.error.message.contains("unknown error code NOPE"))
    }

    private fun isEpubError(value: Scriptable): Boolean =
        EpubJsErrors.constructor(context, scope).hasInstance(value)

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "test.js", 1, null)
}
