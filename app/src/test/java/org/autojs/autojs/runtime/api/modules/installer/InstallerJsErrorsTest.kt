package org.autojs.autojs.runtime.api.modules.installer

import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

class InstallerJsErrorsTest {
    private lateinit var cx: Context
    private lateinit var scope: TopLevelScope

    @Before fun enter() {
        cx = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(cx, false) }
        ScriptableObject.putProperty(scope, "InstallerError", InstallerJsErrors.constructor(cx, scope))
    }

    @After fun exit() { Context.exit() }

    @Test fun constructorUsesTheScopesErrorPrototypeAndKeepsMetadataReadonly() {
        assertSame(InstallerJsErrors.constructor(cx, scope), InstallerJsErrors.constructor(cx, scope))
        assertEquals(
            "true,true,InstallerError,INSTALL_FAILED,4,platform detail,app.test,root,true,string",
            evaluate("""
                var e = new InstallerError('failed', 'INSTALL_FAILED', 4, 'platform detail', 'app.test', 'root', true);
                e.code = 'changed'; e.status = 99;
                [e instanceof Error, e instanceof InstallerError, e.name, e.code, e.status,
                 e.systemMessage, e.packageName, e.authorizer, e.retryable, typeof e.stack].join(',')
            """),
        )
        assertEquals("true,INTERNAL,undefined,false", evaluate("var e = InstallerError('x'); [e instanceof InstallerError,e.code,typeof e.status,e.retryable].join(',')"))
    }

    @Test fun hostErrorsPreservePlatformFieldsAndHideTheirJavaException() {
        val failure = InstallerPluginException(InstallerError(InstallerErrorCodes.INSTALL_FAILED, "cannot install", false, 4, "INSTALL_FAILED_TEST", "app.test", "shizuku"))
        val result = InstallerJsErrors.toJs(cx, scope, failure) as Scriptable
        assertTrue(InstallerJsErrors.constructor(cx, scope).hasInstance(result))
        assertEquals(4, ScriptableObject.getProperty(result, "status"))
        assertEquals("INSTALL_FAILED_TEST", ScriptableObject.getProperty(result, "systemMessage"))
        assertEquals("shizuku", ScriptableObject.getProperty(result, "authorizer"))
        assertSame(failure, Context.jsToJava(ScriptableObject.getProperty(result, "javaException"), Throwable::class.java))
        assertFalse(result.ids.contains("javaException"))
    }

    @Test fun argumentGuardsAreInstallerErrorsButScriptThrowsKeepTheirOwnIdentity() {
        val result = InstallerJsErrors.toJs(cx, scope, WrappedIllegalArgumentException("bad options")) as Scriptable
        assertEquals(InstallerErrorCodes.INVALID_ARGUMENT, ScriptableObject.getProperty(result, "code"))
        val thrown = cx.newObject(scope)
        assertSame(thrown, InstallerJsErrors.toJs(cx, scope, JavaScriptException(thrown, "test.js", 1)))
        val error = try { evaluate("missingVariable.value"); throw AssertionError() } catch (error: EcmaError) { error }
        val wrapped = InstallerJsErrors.toJs(cx, scope, error) as Scriptable
        assertEquals("ReferenceError", ScriptableObject.getProperty(wrapped, "name"))
        assertFalse(InstallerJsErrors.constructor(cx, scope).hasInstance(wrapped))
    }

    @Test fun thrownAndRejectedFailuresUseTheSameInstallerErrorClass() {
        val failure = InstallerJsErrors.failure(InstallerErrorCodes.PLUGIN_UNAVAILABLE, "missing plugin")
        val error = InstallerJsErrors.jsException(cx, scope, failure)
        assertTrue(InstallerJsErrors.constructor(cx, scope).hasInstance(error.value as Scriptable))
        assertEquals(InstallerErrorCodes.PLUGIN_UNAVAILABLE, ScriptableObject.getProperty(error.value as Scriptable, "code"))
        assertSame(error, InstallerJsErrors.jsException(cx, scope, error))
    }

    private fun evaluate(source: String): Any? = cx.evaluateString(scope, source, "test.js", 1, null)
}
