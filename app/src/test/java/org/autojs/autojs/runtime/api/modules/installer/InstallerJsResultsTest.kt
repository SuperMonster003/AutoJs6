package org.autojs.autojs.runtime.api.modules.installer

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.installer.api.InstallerContract
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.util.Base64

class InstallerJsResultsTest {
    private lateinit var cx: Context
    private lateinit var scope: TopLevelScope

    @Before fun enter() {
        cx = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(cx, false) }
        ScriptableObject.putProperty(scope, "InstallerError", InstallerJsErrors.constructor(cx, scope))
    }

    @After fun exit() { Context.exit() }

    @Test fun batchFailuresAreRealInstallerErrorsAndSingleItemBatchesStayArrays() {
        val plain = listOf(mapOf("ok" to false, "authorizer" to "root", "error" to mapOf(
            "code" to "INSTALL_FAILED", "message" to "platform failure", "status" to 4,
            "systemMessage" to "INSTALL_FAILED_TEST", "packageName" to "app.test",
        )))
        val value = InstallerJsResults.decorate(cx, scope, RhinoUtils.toJsValue(scope, plain))
        ScriptableObject.putProperty(scope, "result", value)
        assertEquals("true,1,true,true,root,INSTALL_FAILED_TEST", evaluate("[Array.isArray(result),result.length,result[0].error instanceof Error,result[0].error instanceof InstallerError,result[0].error.authorizer,result[0].error.systemMessage].join(',')"))
        val same = evaluate("result[0].error")
        InstallerJsResults.decorate(cx, scope, value)
        assertSame(same, evaluate("result[0].error"))
    }

    @Test fun inspectIconIsDecodedOrOmittedRatherThanLeakingBase64AsBitmap() {
        val result = RhinoUtils.toJsValue(scope, mapOf("format" to "apk", "icon" to "encoded"))
        InstallerJsResults.decorate(cx, scope, result, inspect = true) { assertEquals("encoded", it); "decoded bitmap" }
        ScriptableObject.putProperty(scope, "result", result)
        assertEquals("decoded bitmap", evaluate("String(result.icon)"))
        val malformed = RhinoUtils.toJsValue(scope, mapOf("format" to "apk", "icon" to "malformed"))
        InstallerJsResults.decorate(cx, scope, malformed, inspect = true) { null }
        ScriptableObject.putProperty(scope, "result", malformed)
        assertEquals("apk,undefined", evaluate("[result.format,typeof result.icon].join(',')"))
    }

    @Test fun iconValidationEnforcesEncodingAndPixelBudgetsBeforeAndroidDecode() {
        assertNotNull(InstallerJsResults.iconBytes(ONE_PIXEL_PNG, Base64.getDecoder()::decode))
        assertNull(InstallerJsResults.iconBytes("%%%", Base64.getDecoder()::decode))
        assertNull(InstallerJsResults.iconBytes(Base64.getEncoder().encodeToString(ByteArray(40)), Base64.getDecoder()::decode))
        var decoded = false
        assertNull(InstallerJsResults.iconBytes("a".repeat(InstallerContract.MAX_ICON_BYTES * 2)) { decoded = true; byteArrayOf() })
        assertFalse(decoded)
        val oversized = Base64.getDecoder().decode(ONE_PIXEL_PNG)
        oversized[16] = 0x7f
        oversized[17] = 0xff.toByte()
        oversized[18] = 0xff.toByte()
        oversized[19] = 0xff.toByte()
        assertNull(InstallerJsResults.iconBytes(Base64.getEncoder().encodeToString(oversized), Base64.getDecoder()::decode))
    }

    private fun evaluate(source: String): Any? = cx.evaluateString(scope, source, "test.js", 1, null)

    private companion object {
        const val ONE_PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5N8AAAAASUVORK5CYII="
    }
}
