package org.autojs.autojs.runtime.api.installer

import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments.Source
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class InstallerScriptArgumentsTest {

    private val cwd = File(System.getProperty("java.io.tmpdir"), "installer-script-source-tests").absoluteFile

    @Test
    fun stringsAndFilesResolveRelativeToTheScriptWithoutOpeningThem() {
        val expected = Source.FilePath(File(cwd, "应用 包 🚀.apk").normalize())
        assertEquals(expected, single("sub/../应用 包 🚀.apk"))
        assertEquals(expected, single(File("sub/../应用 包 🚀.apk")))
        assertEquals(expected, single(File(cwd, "应用 包 🚀.apk")))
        assertEquals(expected, single(File(cwd, "应用 包 🚀.apk").path))
        val withSpaces = "  application.apk  "
        assertEquals(Source.FilePath(File(cwd, withSpaces)), single(withSpaces))
        assertEquals(Source.FilePath(File("package.apk").absoluteFile), InstallerScriptArguments.inspect("package.apk", null))
    }

    @Test
    fun contentUrisArePreservedAndFileUrisBecomeDecodedFiles() {
        val content = "content://com.example.provider/document/%E5%BA%94%E7%94%A8%20a.apk?read=1"
        assertEquals(Source.ContentUri(content), single(content))
        assertEquals(Source.ContentUri("CONTENT://provider/包.apk"), single("CONTENT://provider/包.apk"))
        val file = File(cwd, "应用 空格 +.apk")
        assertEquals(Source.FilePath(file), single(file.toURI().toASCIIString()))
    }

    @Test
    fun singleArrayAndSplitSetRetainDifferentShapes() {
        val single = InstallerScriptArguments.sources("base.apk", cwd.path)
        val batch = InstallerScriptArguments.sources(listOf("base.apk"), cwd.path)
        val splits = InstallerScriptArguments.sources(mapOf("splits" to arrayOf("base.apk", "arm64.apk")), cwd.path)
        assertFalse(single.isBatch)
        assertTrue(batch.isBatch)
        assertEquals(single.items, batch.items)
        assertFalse(splits.isBatch)
        assertEquals(1, splits.items.size)
        assertEquals(2, splits.items.single().size)
        val mixed = InstallerScriptArguments.sources(arrayOf("one.apk", mapOf("splits" to listOf("base.apk", "config.apk"))), cwd.path)
        assertTrue(mixed.isBatch)
        assertEquals(listOf(1, 2), mixed.items.map { it.size })
    }

    @Test
    fun batchAndSplitLimitsAreAppliedIndependently() {
        assertEquals(32, InstallerScriptArguments.sources(List(32) { "app$it.apk" }, cwd.path).items.size)
        assertEquals(64, InstallerScriptArguments.sources(mapOf("splits" to List(64) { "split$it.apk" }), cwd.path).items.single().size)
        val combined = List(32) { item -> mapOf("splits" to List(64) { "item$item-split$it.apk" }) }
        assertEquals(2048, InstallerScriptArguments.sources(combined, cwd.path).items.sumOf { it.size })
        assertTrue(invalid { InstallerScriptArguments.sources(List(33) { "a.apk" }, cwd.path) }.contains("32"))
        assertTrue(invalid { InstallerScriptArguments.sources(mapOf("splits" to List(65) { "a.apk" }), cwd.path) }.contains("64"))
    }

    @Test
    fun emptyAndAmbiguousContainersAreRejected() {
        listOf<Any?>(null, true, 1, emptyList<Any>(), emptyArray<Any>(), emptyMap<String, Any>(), mapOf("splits" to emptyList<String>()), mapOf("splits" to null), mapOf("splits" to "base.apk")).forEach {
            invalid { InstallerScriptArguments.sources(it, cwd.path) }
        }
        listOf<Any>(listOf(listOf("a.apk")), mapOf("splits" to listOf(listOf("a.apk"))), mapOf("splits" to listOf(mapOf("splits" to listOf("a.apk"))))).forEach {
            invalid { InstallerScriptArguments.sources(it, cwd.path) }
        }
        assertTrue(invalid { InstallerScriptArguments.sources(mapOf("splits" to listOf("a.apk"), "other" to true), cwd.path) }.contains("other"))
        invalid { InstallerScriptArguments.sources(mapOf(null to true, "splits" to listOf("a.apk")), cwd.path) }
        invalid { InstallerScriptArguments.sources(mapOf(1 to "a.apk"), cwd.path) }
    }

    @Test
    fun invalidPathsAndUnsupportedSchemesFailBeforeFilesystemAccess() {
        listOf("", " \t ", "a\u0000.apk", "https://example.com/app.apk", "ftp://host/app.apk", "content:", "content:opaque", "content:///a.apk", "content://provider/a%00.apk", "content://provider/a%ZZ.apk", "file:relative.apk", "file://host/a.apk", "file:///a.apk?x=1", "file:///a.apk#fragment").forEach {
            invalid { InstallerScriptArguments.sources(it, cwd.path) }
        }
        invalid { InstallerScriptArguments.sources(File(""), cwd.path) }
        invalid { InstallerScriptArguments.sources("a.apk", "") }
        invalid { InstallerScriptArguments.sources("a.apk", "cwd\u0000") }
    }

    @Test
    fun inspectRefusesBatchAndSplitFormsEvenWithOneEntry() {
        assertEquals(single("app.aab"), InstallerScriptArguments.inspect("app.aab", cwd.path))
        invalid { InstallerScriptArguments.inspect(listOf("a.apk"), cwd.path) }
        invalid { InstallerScriptArguments.inspect(mapOf("splits" to listOf("a.apk")), cwd.path) }
    }

    @Test
    fun packageNamesCannotBecomeArbitraryCommandOrUriText() {
        assertEquals("com.example.app", InstallerScriptArguments.packageName("com.example.app"))
        listOf<Any?>(null, 1, "", "com.example;echo", "content://app", "com.example\u0000", " com.example ", "com").forEach {
            invalid { InstallerScriptArguments.packageName(it) }
        }
    }

    private fun single(value: Any?): Source = InstallerScriptArguments.sources(value, cwd.path).items.single().single()

    private fun invalid(block: () -> Any?): String {
        try {
            block()
        } catch (e: InstallerPluginException) {
            assertEquals(e.message, InstallerErrorCodes.INVALID_ARGUMENT, e.code)
            assertFalse(e.retryable)
            return e.error.message
        }
        fail("expected INVALID_ARGUMENT")
        throw AssertionError()
    }
}
