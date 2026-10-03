package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes as Codes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Real Rhino error objects and script stacks; no ScriptRuntime or Android framework is required. */
class ComposeJsErrorsTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before fun enter() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        ScriptableObject.putProperty(scope, "ComposeError", ComposeJsErrors.constructor(scope))
    }

    @After fun exit() { Context.exit() }

    @Test fun constructorUsesTheCurrentRealmErrorPrototypeAndIsCachedOnlyWithinThatRealm() {
        assertSame(ComposeJsErrors.constructor(scope), ComposeJsErrors.constructor(scope))
        assertEquals("true,true,ComposeError,boom,INVALID_ARGUMENT,27,text,false,string,ComposeError: boom", evaluate("""
            var error = new ComposeError('boom', 'INVALID_ARGUMENT', 27, 'text');
            [error instanceof Error, error instanceof ComposeError, error.name, error.message, error.code,
             error.nodeId, error.prop, error.retryable, typeof error.stack, String(error)].join(',')
        """))
        assertEquals("true,INVALID_ARGUMENT,true,true,true,false", evaluate("""
            var plain = ComposeError('argument');
            [plain instanceof ComposeError, plain.code, plain.nodeId === null, plain.prop === null,
             plain.cause === null, Object.keys(plain).indexOf('message') >= 0].join(',')
        """))
        val otherScope = TopLevelScope().apply { initStandardObjects(context, false) }
        val other = ComposeJsErrors.constructor(otherScope)
        assertNotSame(ComposeJsErrors.constructor(scope), other)
        assertFalse(other.hasInstance(ScriptableObject.getProperty(scope, "error") as Scriptable))
    }

    @Test fun contractAndLocalizedPluginFailuresPreserveTypedDetailsAndMessagePrefix() {
        val value = converted(ComposeUiContractException(Codes.UNKNOWN_PROP, "Text does not support missing", 41, "missing"))
        assertTrue(ComposeJsErrors.constructor(scope).hasInstance(value))
        assertEquals(Codes.UNKNOWN_PROP, property(value, "code"))
        assertEquals(41, property(value, "nodeId"))
        assertEquals("missing", property(value, "prop"))
        assertEquals("Text does not support missing", property(value, "message"))
        assertFalse(property(value, "retryable") as Boolean)

        val message = "请先在插件中心授权 Compose UI"
        val unavailable = converted(ComposeUiError(Codes.PLUGIN_UNAUTHORIZED, message))
        assertEquals(message, property(unavailable, "message"))
        assertTrue(property(unavailable, "retryable") as Boolean)
        assertNull(property(unavailable, "nodeId"))
        assertNull(property(unavailable, "prop"))
    }

    @Test fun onlyPluginSelectionErrorsAreRetryableAndHostFailuresHaveStableCodes() {
        val retryable = setOf(Codes.PLUGIN_UNAVAILABLE, Codes.PLUGIN_DISABLED, Codes.PLUGIN_UNAUTHORIZED, Codes.PLUGIN_INCOMPATIBLE)
        for (code in retryable + listOf(Codes.RENDER_FAILED, Codes.SESSION_CLOSED, Codes.LIMIT_EXCEEDED, Codes.NODE_DETACHED)) {
            assertEquals(code in retryable, property(converted(ComposeUiError(code)), "retryable"))
        }
        val invalid = converted(IllegalArgumentException("Bad Text value"))
        assertEquals(Codes.INVALID_ARGUMENT, property(invalid, "code"))
        val cause = property(invalid, "cause") as Scriptable
        assertEquals("Bad Text value", property(cause, "message"))
        ScriptableObject.putProperty(scope, "hostCause", cause)
        assertEquals(true, evaluate("hostCause instanceof Error"))
        assertEquals(Codes.RENDER_FAILED, property(converted(IllegalStateException("Renderer failed")), "code"))
    }

    @Test fun renderedScriptThrowKeepsItsCauseObjectAndOriginalScriptStack() {
        val failure = try {
            evaluate("function renderCounter() { throw new Error('render body failed'); } renderCounter();", "counter-render.js")
            throw AssertionError("Script throw expected")
        } catch (failure: JavaScriptException) { failure }
        val original = failure.value as Scriptable
        val originalStack = property(original, "stack").toString()
        assertTrue(originalStack, originalStack.contains("counter-render.js"))
        val wrapped = converted(ComposeUiError(Codes.RENDER_FAILED, "Compose render failed", cause = failure))
        assertEquals(Codes.RENDER_FAILED, property(wrapped, "code"))
        assertSame(original, property(wrapped, "cause"))
        assertEquals(originalStack, property(property(wrapped, "cause") as Scriptable, "stack"))
        assertSame(failure, ComposeJsErrors.exception(scope, failure))
    }

    @Test fun nativeRhinoFailureRetainsItsRenderLocationInTheJsCause() {
        val failure = try {
            evaluate("function renderForm() { return missingComposeValue.text; } renderForm();", "form-render.js")
            throw AssertionError("ReferenceError expected")
        } catch (failure: EcmaError) { failure }
        val wrapped = converted(ComposeUiError(Codes.RENDER_FAILED, "Compose render failed", cause = failure))
        val cause = property(wrapped, "cause") as Scriptable
        assertTrue(property(cause, "message").toString().contains("missingComposeValue"))
        val stack = property(cause, "stack").toString()
        assertTrue(stack, stack.contains("form-render.js"))
        assertTrue(stack, stack.contains("renderForm"))
    }

    @Test fun guardConvertsLinkageFailuresButPreservesAnAlreadyThrownScriptValue() {
        val guarded = assertThrows(JavaScriptException::class.java) {
            ComposeJsErrors.guard(scope) { throw NoClassDefFoundError("Missing renderer type") }
        }
        val value = guarded.value as Scriptable
        assertEquals(Codes.RENDER_FAILED, property(value, "code"))
        assertTrue(ComposeJsErrors.constructor(scope).hasInstance(value))
        val thrown = JavaScriptException(context.newObject(scope), "own-script.js", 4)
        val actual = assertThrows(JavaScriptException::class.java) {
            ComposeJsErrors.guard(scope) { throw thrown }
        }
        assertSame(thrown, actual)
    }

    private fun converted(failure: Throwable) = (ComposeJsErrors.exception(scope, failure) as JavaScriptException).value as Scriptable
    private fun property(value: Scriptable, name: String): Any? = ScriptableObject.getProperty(value, name)
    private fun evaluate(source: String, name: String = "compose-errors-test.js"): Any? = context.evaluateString(scope, source, name, 1, null)
}
