package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.core.plugin.mail.MailError
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** Bare Rhino: the `MailError` constructor and the Kotlin-to-JavaScript error mapping without a ScriptRuntime. */
class MailJsErrorsTest {

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
        val constructor = MailJsErrors.constructor(context, scope)
        assertSame(constructor, MailJsErrors.constructor(context, scope))
        ScriptableObject.putProperty(scope, "MailError", constructor)
        val result = evaluate(
            """
            var e = new MailError('boom', 'AUTH_FAILED', { host: 'imap.test' }, true);
            var plain = MailError('x');
            JSON.stringify({
                isError: e instanceof Error,
                isMailError: e instanceof MailError,
                plainIsMailError: plain instanceof MailError,
                name: e.name,
                message: e.message,
                code: e.code,
                host: e.details.host,
                retryable: e.retryable,
                text: String(e),
                keys: Object.keys(e),
                stack: typeof e.stack,
                defaults: [plain.code, plain.details, plain.retryable],
                ownName: Object.prototype.hasOwnProperty.call(e, 'name'),
            })
            """.trimIndent(),
        )
        assertEquals(
            """{"isError":true,"isMailError":true,"plainIsMailError":true,"name":"MailError","message":"boom","code":"AUTH_FAILED",""" +
                """"host":"imap.test","retryable":true,"text":"MailError [AUTH_FAILED]: boom","keys":["code","details","retryable"],""" +
                """"stack":"string","defaults":["INTERNAL",null,false],"ownName":false}""",
            result,
        )
    }

    @Test
    fun mailErrorsBecomeMailErrorInstancesWithHiddenJavaException() {
        val cause = MailPluginException(MailError(MailErrorCodes.FOLDER_NOT_FOUND, "no such folder", false, """{"folder":"Archive"}"""))
        val value = MailJsErrors.toJs(context, scope, cause) as Scriptable
        assertTrue(isMailError(value))
        assertEquals(MailErrorCodes.FOLDER_NOT_FOUND, ScriptableObject.getProperty(value, "code"))
        assertEquals("no such folder", ScriptableObject.getProperty(value, "message"))
        assertEquals(false, ScriptableObject.getProperty(value, "retryable"))
        val details = ScriptableObject.getProperty(value, "details") as Scriptable
        assertEquals("Archive", ScriptableObject.getProperty(details, "folder"))
        assertSame(cause, Context.jsToJava(ScriptableObject.getProperty(value, "javaException"), Throwable::class.java))
        assertFalse("javaException stays hidden from enumeration", (value as ScriptableObject).ids.contains("javaException"))

        val textual = MailJsErrors.toJs(context, scope, MailPluginException(MailError(MailErrorCodes.TIMEOUT, "slow", true, "plain text"))) as Scriptable
        assertEquals("plain text", ScriptableObject.getProperty(textual, "details"))
        assertEquals(true, ScriptableObject.getProperty(textual, "retryable"))
        val bare = MailJsErrors.toJs(context, scope, MailPluginException(MailError(MailErrorCodes.INTERNAL, "n/a", false))) as Scriptable
        assertNull(ScriptableObject.getProperty(bare, "details"))
    }

    @Test
    fun hostAndGuardFailuresMapToCodesWhileScriptValuesPassThrough() {
        val guard = MailJsErrors.toJs(context, scope, WrappedIllegalArgumentException("mail.connect expects one argument")) as Scriptable
        assertTrue(isMailError(guard))
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(guard, "code"))
        assertTrue(ScriptableObject.getProperty(guard, "message").toString().contains("expects one argument"))

        val internal = MailJsErrors.toJs(context, scope, IllegalStateException("broken")) as Scriptable
        assertEquals(MailErrorCodes.INTERNAL, ScriptableObject.getProperty(internal, "code"))
        assertEquals(false, ScriptableObject.getProperty(internal, "retryable"))

        val thrown = NativeObject()
        assertSame(thrown, MailJsErrors.toJs(context, scope, JavaScriptException(thrown, "script.js", 1)))

        val ecma = try {
            evaluate("noSuchVariable.x")
            throw AssertionError("ReferenceError expected")
        } catch (e: EcmaError) {
            e
        }
        val wrapped = MailJsErrors.toJs(context, scope, ecma) as Scriptable
        assertFalse("script errors keep their own class", isMailError(wrapped))
        assertEquals("ReferenceError", ScriptableObject.getProperty(wrapped, "name"))
    }

    @Test
    fun jsExceptionCarriesTheMailErrorAndKeepsScriptExceptions() {
        val failure = MailJsErrors.failure(MailErrorCodes.NO_DEFAULT_ACCOUNT, "call setDefault first")
        assertEquals(MailErrorCodes.NO_DEFAULT_ACCOUNT, failure.code)
        assertFalse(failure.retryable)
        val exception = MailJsErrors.jsException(context, scope, failure)
        val value = exception.value as Scriptable
        assertTrue(isMailError(value))
        assertEquals("call setDefault first", ScriptableObject.getProperty(value, "message"))
        assertNotNull(exception.message)

        val own = JavaScriptException("text", "script.js", 3)
        assertSame(own, MailJsErrors.jsException(context, scope, own))
    }

    private fun isMailError(value: Scriptable): Boolean =
        MailJsErrors.constructor(context, scope).hasInstance(value)

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "test.js", 1, null)
}
