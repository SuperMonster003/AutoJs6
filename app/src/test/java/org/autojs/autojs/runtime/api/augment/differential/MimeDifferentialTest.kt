package org.autojs.autojs.runtime.api.augment.differential

import org.autojs.autojs.runtime.api.augment.mime.Mime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.autojs.autojs.runtime.api.Mime as ApiMime

/**
 * Obsolete `__mime__.js` against the Kotlin [Mime] augmentable (roadmap P0.5). Both sides take the Java
 * [ApiMime] object as prototype, as `ScriptRuntime.mime` did; the JS side formats parameters with the obsolete
 * util module, which is loaded first.
 */
class MimeDifferentialTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsMime: Any
    private lateinit var ktMime: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsMime = ObsoleteModuleHost(rhino.context, rhino.jsScope).run {
            setRuntimeProperty("mime", ApiMime)
            loadAndDefine("util")
            loadAndDefine("mime")!!
        }
        ktMime = Mime(rhino.ktRuntime).augment(rhino.ktScope.globalThis, ApiMime)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheKotlinModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsMime, rhino.jsScope.globalThis), SurfaceDiff.surface(ktMime, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete mime members missing from the Kotlin module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, tolerateErrorTypes = true)
    }

    companion object {

        /** Renders a returned JsMime (a JS object on one side, a Java object on the other) field by field. */
        private fun fields(call: String) =
            "(function (m) { return [m.raw, m.type, m.subtype, m.mimeType, m.mimeTypeRefined].map(String).concat(JSON.stringify(m.parameters)); })(mime($call))"

        private val EXPRESSIONS = listOf(
            "typeof mime",
            fields("'text/plain'"),
            fields("'text/html; charset=UTF-8'"),
            fields("'text/html;charset=UTF-8;q=0.5'"),
            fields("'multipart/form-data; boundary=----x; flag'"),
            fields("'application/json ; charset = utf-8 '"),
            fields("'TEXT/PLAIN'"),
            fields("'image/*'"),
            fields("'*/*'"),
            fields("'application/vnd.api+json'"),
            fields("'text/plain;'"),
            fields("'text/plain; a=1=2'"),
            fields("' text/plain'"),
            "String(mime('text/plain'))",
            "String(mime('text/html; charset=UTF-8'))",
            "mime('text/plain').toString() === String(mime('text/plain'))",
            fields("'text'"),
            "JSON.stringify(mime('text/plain; a=1'))",
            "Object.keys(mime('text/plain')).indexOf('getClass') >= 0",
            "mime('')",
            "mime(123)",
            "mime(null)",
            "mime()",
            "mime('text/plain', 'extra')",
            "mime.TEXT_PLAIN",
            "String(mime.getType('text/html'))",
            "String(mime.getSubtype('text/html'))",
        )

        private const val TO_STRING_REASON = "Kotlin wraps the formatted parameters in quotes ('{ charset: 'UTF-8' }', " +
                "with unbalanced nested quotes); restore the obsolete format (P2.2)"

        private const val JS_OBJECT_REASON = "the obsolete JsMime was a plain JS object with enumerable, JSON-serializable fields (as the d.ts " +
                "declares them); Kotlin returns a Java object, so JSON.stringify yields its toString text and Object.keys lists Java members " +
                "(P2.2: return a JS object again or give it toJSON / enumerable fields)"

        /** Differences between the obsolete module and the current Kotlin module, see ROADMAP appendix E. */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "String(mime('text/plain'))",
                js = "\"JsMime {\\n  type: 'text',\\n  subtype: 'plain',\\n  mimeType: 'text/plain',\\n  mimeTypeRefined: 'text/plain',\\n  parameters: {},\\n  raw: 'text/plain',\\n}\"",
                kt = "\"JsMime {\\n  type: 'text',\\n  subtype: 'plain',\\n  mimeType: 'text/plain',\\n  mimeTypeRefined: 'text/plain',\\n  parameters: '{}',\\n  raw: 'text/plain',\\n}\"",
                reason = TO_STRING_REASON,
            ),
            DifferentialCases.KnownDifference(
                "String(mime('text/html; charset=UTF-8'))",
                js = "\"JsMime {\\n  type: 'text',\\n  subtype: 'html',\\n  mimeType: 'text/html; charset=UTF-8',\\n  mimeTypeRefined: 'text/html',\\n  parameters: { charset: 'UTF-8' },\\n  raw: 'text/html; charset=UTF-8',\\n}\"",
                kt = "\"JsMime {\\n  type: 'text',\\n  subtype: 'html',\\n  mimeType: 'text/html; charset=UTF-8',\\n  mimeTypeRefined: 'text/html',\\n  parameters: '{ charset: 'UTF-8' }',\\n  raw: 'text/html; charset=UTF-8',\\n}\"",
                reason = TO_STRING_REASON,
            ),
            DifferentialCases.KnownDifference(
                "JSON.stringify(mime('text/plain; a=1'))",
                js = "\"{\\\"raw\\\":\\\"text/plain; a=1\\\",\\\"type\\\":\\\"text\\\",\\\"subtype\\\":\\\"plain\\\",\\\"mimeType\\\":\\\"text/plain; a=1\\\",\\\"mimeTypeRefined\\\":\\\"text/plain\\\",\\\"parameters\\\":{\\\"a\\\":\\\"1\\\"}}\"",
                kt = "\"\\\"JsMime {\\\\n  type: 'text',\\\\n  subtype: 'plain',\\\\n  mimeType: 'text/plain; a=1',\\\\n  mimeTypeRefined: 'text/plain',\\\\n  parameters: '{ a: '1' }',\\\\n  raw: 'text/plain; a=1',\\\\n}\\\"\"",
                reason = JS_OBJECT_REASON,
            ),
            DifferentialCases.KnownDifference(
                "Object.keys(mime('text/plain')).indexOf('getClass') >= 0",
                js = "false",
                kt = "true",
                reason = JS_OBJECT_REASON,
            ),
            DifferentialCases.KnownDifference(
                "mime(123)",
                js = "throws TypeError",
                kt = "\"JsMime {\\n  type: '123',\\n  subtype: '*',\\n  mimeType: '123/*',\\n  mimeTypeRefined: '123/*',\\n  parameters: '{}',\\n  raw: '123',\\n}\"",
                reason = "the d.ts takes a string; the obsolete module threw TypeError, Kotlin coerces 123 to '123' and parses it as '123/*'; " +
                        "restore the TypeError (P2.2)",
            ),
            DifferentialCases.KnownDifference(
                "mime('text/plain', 'extra')",
                js = "{\"raw\":\"text/plain\",\"type\":\"text\",\"subtype\":\"plain\",\"mimeType\":\"text/plain\",\"mimeTypeRefined\":\"text/plain\",\"parameters\":{}}",
                kt = "throws(java) RuntimeException",
                reason = "surplus arguments: ignored by the obsolete module, rejected by Kotlin (appendix C Q7)",
            ),
        )

    }

}
