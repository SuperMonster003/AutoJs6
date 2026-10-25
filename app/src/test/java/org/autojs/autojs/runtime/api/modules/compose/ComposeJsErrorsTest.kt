package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.core.plugin.compose.ComposeUiError
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
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

        val message = "请先在插件中心授权 3-Block Compose"
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

    @Test fun localizedPrefixesKeepComponentDetailsAndPluginCenterMessagesVerbatim() {
        ComposeJsErrors.installLocalizer(scope) { it.localizedMessage { "未知 Compose 属性." } }
        val failure = ComposeUiContractException(Codes.UNKNOWN_PROP, "Text.missing: Unknown property", 41, "missing")
        val localized = converted(failure)
        val message = property(localized, "message").toString()
        assertTrue(message, message.startsWith("未知 Compose 属性."))
        assertTrue(message, message.contains("Text.missing"))
        assertTrue(message, message.contains("nodeId=41"))
        assertEquals("missing", property(localized, "prop"))
        assertEquals(41, property(localized, "nodeId"))
        assertEquals(Codes.UNKNOWN_PROP, property(localized, "code"))
        assertEquals("Text.missing: Unknown property", failure.message)

        val prefix = "请先在插件中心授权 3-Block Compose"
        assertEquals(prefix, property(converted(ComposeUiError(Codes.PLUGIN_UNAUTHORIZED, prefix)), "message"))
        val otherScope = TopLevelScope().apply { initStandardObjects(context, false) }
        val independent = (ComposeJsErrors.exception(otherScope, failure) as JavaScriptException).value as Scriptable
        assertEquals(failure.message, property(independent, "message"))
    }

    @Test fun everyFrozenErrorCodeKeepsMetadataWithItsLocalizedMessage() {
        val codes = Codes::class.java.declaredFields.filter { it.type == String::class.java }.map { it.get(null) as String }
        assertEquals(18, codes.size)
        ComposeJsErrors.installLocalizer(scope) { error -> error.localizedMessage { "localized ${error.code}" } }
        for (code in codes) {
            val value = converted(ComposeUiError(code, nodeId = 17, prop = "text"))
            assertEquals(code, property(value, "code"))
            assertEquals(17, property(value, "nodeId"))
            assertEquals("text", property(value, "prop"))
            val message = property(value, "message").toString()
            assertTrue(message, message.startsWith("localized $code"))
            assertTrue(message, message.contains("prop=text"))
            assertEquals(code.startsWith("PLUGIN_"), property(value, "retryable"))
        }
        ComposeJsErrors.installLocalizer(scope) { "Updated locale" }
        assertEquals("Updated locale", property(converted(ComposeUiError(Codes.INVALID_ARGUMENT)), "message"))
    }

    @Test fun nestedContractCausesPreserveTheOriginalScriptObjectAndStack() {
        val scriptFailure = try {
            evaluate("function buildInvalidForm() { throw new Error('invalid form'); } buildInvalidForm();", "nested-form.js")
            throw AssertionError("Script throw expected")
        } catch (failure: JavaScriptException) { failure }
        val hostFailure = IllegalArgumentException("conversion failed", scriptFailure)
        val contract = ComposeUiContractException(Codes.INVALID_ARGUMENT, "Text.style: Invalid value", 25, "style", hostFailure)
        val error = converted(contract)
        val nativeCause = property(error, "cause") as Scriptable
        assertEquals(Codes.INVALID_ARGUMENT, property(nativeCause, "code"))
        assertEquals(25, property(nativeCause, "nodeId"))
        assertEquals("style", property(nativeCause, "prop"))
        val conversionCause = property(nativeCause, "cause") as Scriptable
        assertEquals("conversion failed", property(conversionCause, "message"))
        assertSame(scriptFailure.value, property(conversionCause, "cause"))
        val scriptCause = property(conversionCause, "cause") as Scriptable
        assertTrue(property(scriptCause, "stack").toString().contains("nested-form.js"))
    }

    @Test(timeout = 5000) fun cyclicHostCausesRemainInspectableWithoutRecursiveConversion() {
        val outer = IllegalStateException("outer")
        val inner = IllegalArgumentException("inner")
        outer.initCause(inner)
        inner.initCause(outer)
        val value = converted(ComposeUiError(Codes.RENDERER_FAILED, cause = outer))
        val convertedOuter = property(value, "cause") as Scriptable
        val convertedInner = property(convertedOuter, "cause") as Scriptable
        assertEquals("outer", property(convertedOuter, "message"))
        assertEquals("inner", property(convertedInner, "message"))
        assertSame(convertedOuter, property(convertedInner, "cause"))
    }

    @Test fun engineInterruptionDoesNotBecomeAUserFacingComposeFailure() {
        val stop = ScriptInterruptedException()
        assertSame(stop, ComposeJsErrors.exception(scope, stop))
        val wrapped = IllegalStateException("stopping", stop)
        assertSame(wrapped, ComposeJsErrors.exception(scope, wrapped))
        val checked = InterruptedException("stopping")
        val converted = ComposeJsErrors.exception(scope, checked)
        assertTrue(converted is ScriptInterruptedException)
        assertSame(checked, converted.cause)
    }

    @Test fun unavailableLocalizationDoesNotReplaceTheOriginalFailure() {
        ComposeJsErrors.installLocalizer(scope) { throw IllegalStateException("Missing test resource") }
        val value = converted(ComposeUiContractException(Codes.UNKNOWN_COMPONENT, "MissingWidget: Unknown component"))
        assertEquals(Codes.UNKNOWN_COMPONENT, property(value, "code"))
        assertEquals("MissingWidget: Unknown component", property(value, "message"))
    }

    private fun converted(failure: Throwable) = (ComposeJsErrors.exception(scope, failure) as JavaScriptException).value as Scriptable
    private fun property(value: Scriptable, name: String): Any? = ScriptableObject.getProperty(value, name)
    private fun evaluate(source: String, name: String = "compose-errors-test.js"): Any? = context.evaluateString(scope, source, name, 1, null)
}
