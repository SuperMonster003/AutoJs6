package org.autojs.autojs.runtime.api.augment.gallery

import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.plugin.gallery.api.GalleryErrorCodes
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

/** Bare Rhino: the `GalleryError` constructor and the Kotlin-to-JavaScript error mapping without a ScriptRuntime (3-Maple Image roadmap M6, 10.1). */
class GalleryJsErrorsTest {

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
        val constructor = GalleryJsErrors.constructor(context, scope)
        assertSame(constructor, GalleryJsErrors.constructor(context, scope))
        ScriptableObject.putProperty(scope, "GalleryError", constructor)
        val result = evaluate(
            """
            var e = new GalleryError('boom', 'FILE_NOT_FOUND');
            var plain = GalleryError('x');
            JSON.stringify({
                isError: e instanceof Error,
                isGalleryError: e instanceof GalleryError,
                plainIsGalleryError: plain instanceof GalleryError,
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
            """{"isError":true,"isGalleryError":true,"plainIsGalleryError":true,"name":"GalleryError","message":"boom","code":"FILE_NOT_FOUND",""" +
                """"text":"GalleryError [FILE_NOT_FOUND]: boom","keys":["code"],"stack":"string","defaults":["INTERNAL","x"],"ownName":false}""",
            result,
        )
    }

    @Test
    fun pluginFailuresBecomeGalleryErrorInstancesWithHiddenJavaException() {
        val cause = GalleryPluginException(GalleryError(GalleryErrorCodes.DECODE_FAILED, "truncated image"))
        val value = GalleryJsErrors.toJs(context, scope, cause) as Scriptable
        assertTrue(isGalleryError(value))
        assertEquals(GalleryErrorCodes.DECODE_FAILED, ScriptableObject.getProperty(value, "code"))
        assertEquals("truncated image", ScriptableObject.getProperty(value, "message"))
        assertSame(cause, Context.jsToJava(ScriptableObject.getProperty(value, "javaException"), Throwable::class.java))
        assertFalse("javaException stays hidden from enumeration", (value as ScriptableObject).ids.contains("javaException"))
    }

    @Test
    fun hostAndGuardFailuresMapToCodesWhileScriptValuesPassThrough() {
        val guard = GalleryJsErrors.toJs(context, scope, WrappedIllegalArgumentException("gallery.view expects one or two arguments")) as Scriptable
        assertTrue(isGalleryError(guard))
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(guard, "code"))
        assertTrue(ScriptableObject.getProperty(guard, "message").toString().contains("expects one or two arguments"))

        val internal = GalleryJsErrors.toJs(context, scope, IllegalStateException("broken")) as Scriptable
        assertEquals(GalleryErrorCodes.INTERNAL, ScriptableObject.getProperty(internal, "code"))
        val argument = GalleryJsErrors.toJs(context, scope, IllegalArgumentException("bad")) as Scriptable
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(argument, "code"))

        val thrown = NativeObject()
        assertSame(thrown, GalleryJsErrors.toJs(context, scope, JavaScriptException(thrown, "script.js", 1)))

        val ecma = try {
            evaluate("noSuchVariable.x")
            throw AssertionError("ReferenceError expected")
        } catch (e: EcmaError) {
            e
        }
        val wrapped = GalleryJsErrors.toJs(context, scope, ecma) as Scriptable
        assertFalse("script errors keep their own class", isGalleryError(wrapped))
        assertEquals("ReferenceError", ScriptableObject.getProperty(wrapped, "name"))
    }

    @Test
    fun jsExceptionCarriesTheGalleryErrorAndKeepsScriptExceptions() {
        val failure = GalleryJsErrors.failure(GalleryErrorCodes.SESSION_CLOSED, "the viewer is closed")
        assertEquals(GalleryErrorCodes.SESSION_CLOSED, failure.code)
        val exception = GalleryJsErrors.jsException(context, scope, failure)
        val value = exception.value as Scriptable
        assertTrue(isGalleryError(value))
        assertEquals("the viewer is closed", ScriptableObject.getProperty(value, "message"))
        assertNotNull(exception.message)

        val own = JavaScriptException("text", "script.js", 3)
        assertSame(own, GalleryJsErrors.jsException(context, scope, own))

        val unknown = GalleryJsErrors.failure("NOPE", "odd")
        assertEquals(GalleryErrorCodes.INTERNAL, unknown.code)
        assertTrue(unknown.error.message.contains("unknown error code NOPE"))
    }

    private fun isGalleryError(value: Scriptable): Boolean =
        GalleryJsErrors.constructor(context, scope).hasInstance(value)

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "test.js", 1, null)
}
