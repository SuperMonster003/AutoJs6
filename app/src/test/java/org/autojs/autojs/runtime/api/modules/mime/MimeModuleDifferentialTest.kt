package org.autojs.autojs.runtime.api.modules.mime

import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.autojs.autojs.runtime.api.Mime as ApiMime

/**
 * Obsolete `__mime__.js` against `mime` of the new script module layer (roadmap P2.2 / P2.4), mounted the way
 * `ScriptRuntime.augment()` does with the switch on. Both module objects have the [ApiMime] object as prototype; the JS
 * side formats the parameters with the obsolete util module. Error types are compared (D10); the differences left are
 * the read-only fields (documented) and appendix C Q7.
 */
class MimeModuleDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("mime")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val ktMime = rhino.evalKt("mime")
        val report = SurfaceDiff.compare(ScriptModuleExpectations.obsoleteSurface("surface"), SurfaceDiff.surface(ktMime, rhino.ktScope.globalThis))
        println(SurfaceDiff.describe(report, "js", "kt"))
        assertTrue("Obsolete mime members missing from the new module: ${report.onlyInFirst}", report.onlyInFirst.isEmpty())
        assertTrue("typeof differs: ${report.typeMismatches}", report.typeMismatches.isEmpty())
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {

        /** Renders a returned JsMime field by field. */
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
            fields("'text'"),
            fields("'text/plain; 2=b; 1=a'"),
            "(function (s) { return mime(s + '/plain').type; })('text')",
            "String(mime('text/plain'))",
            "String(mime('text/html; charset=UTF-8'))",
            "String(mime('text/plain; my-key=1; q=0.5'))",
            "String(mime(\"text/plain; a=it's; b=\\\"x\\\"\"))",
            "String(mime('text/plain; a=\\\\x\\ty'))",
            "String(mime('multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW; charset=utf-8'))",
            "String(mime('text/plain; aaaaaaaaaa=bbbbbbbbbb; cccccccccc=dddddddddd; eeee=ffff'))",
            "mime('text/plain').toString() === String(mime('text/plain'))",
            "JSON.stringify(mime('text/plain; a=1'))",
            "Object.keys(mime('text/plain'))",
            "Object.keys(mime('text/plain; b=2; a=1').parameters)",
            "(function () { var a = []; for (var k in mime('text/plain')) a.push(k); return a; })()",
            "mime('text/plain').constructor === mime('a/b').constructor",
            "mime('text/plain') instanceof mime('a/b').constructor",
            "Object.getPrototypeOf(mime('text/plain')).toString.call({ type: 't', subtype: 's' })",
            "(function (m) { m.parameters.extra = 'x'; return String(m); })(mime('text/plain'))",
            "Object.getOwnPropertyDescriptor(mime('text/plain'), 'type').writable",
            "Object.getOwnPropertyDescriptor(mime('text/plain'), 'type').enumerable",
            "mime('')",
            "mime('text/')",
            "mime(123)",
            "mime(null)",
            "mime()",
            "mime('text/plain', 'extra')",
            "mime.TEXT_PLAIN",
            "String(mime.getType('text/html'))",
            "String(mime.getSubtype('text/html'))",
        )

        /** Differences between the obsolete module and the new layer (documented behavior and appendix C Q7). */
        private val KNOWN_DIFFERENCES = listOf(
            DifferentialCases.KnownDifference(
                "Object.getOwnPropertyDescriptor(mime('text/plain'), 'type').writable",
                js = "true",
                kt = "false",
                reason = "documented: the JsMime fields are READONLY (api/mime.md; the d.ts class still declares plain public fields); the obsolete module " +
                        "assigned them with Object.assign, the current Kotlin module returned final Java fields",
            ),
            DifferentialCases.KnownDifference(
                "mime('text/plain', 'extra')",
                js = "{\"raw\":\"text/plain\",\"type\":\"text\",\"subtype\":\"plain\",\"mimeType\":\"text/plain\",\"mimeTypeRefined\":\"text/plain\",\"parameters\":{}}",
                kt = "throws TypeError",
                reason = "surplus arguments: ignored by the obsolete module, a TypeError on the new layer (appendix C Q7; documented: one argument)",
            ),
        )

    }

}
