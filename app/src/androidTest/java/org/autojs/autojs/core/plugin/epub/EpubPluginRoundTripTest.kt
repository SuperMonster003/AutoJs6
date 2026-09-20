package org.autojs.autojs.core.plugin.epub

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.epub.api.EpubCapabilityKeys
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Real cross-process Binder round trip with the installed EPUB plugin (plugin roadmap P5.4):
 * [EpubPluginHost.openBook] against a fixture this process writes, every extraction method
 * through [EpubBookClient], a resource export through [EpubOutputSink], the contract error
 * codes for host-side and plugin-side refusals, and a force-stop of the plugin process while a
 * book is open, which the death recipient turns into `SESSION_CLOSED`.
 *
 * Skipped when no EPUB plugin is installed. The plugin's caller guard needs this host to be
 * signed by the plugin's certificate and to be at least the plugin's `requiresHostVersion`.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.core.plugin.epub.EpubPluginRoundTripTest"
 * ```
 */
class EpubPluginRoundTripTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val workDir: File
        get() = File(context.cacheDir, "epub-round-trip").apply { mkdirs() }

    @Test
    fun bookExtractionRoundTripOverTheRealBinder(): Unit = runBlocking {
        val packageName = pluginPackageOrSkip()
        val capabilities = EpubPluginHost.getCapabilities(context)
        assertEquals(EpubContract.CONTRACT_VERSION, capabilities.getInt(EpubCapabilityKeys.CONTRACT_VERSION))
        val features = capabilities.getStringArray(EpubCapabilityKeys.FEATURES)?.toList().orEmpty()
        Log.i(TAG, "plugin $packageName features: $features")
        assertTrue(EpubContract.FEATURE_SEARCH in features)
        assertTrue(EpubContract.FEATURE_RESOURCE_EXPORT in features)
        assertTrue(EpubContract.FEATURE_MARKDOWN in features)

        val file = EpubFixture.write(File(workDir, "round-trip.epub"))
        val book = EpubPluginHost.openBook(context, file.path)
        try {
            val metadata = JSONObject(book.metadata())
            Log.i(TAG, "metadata: $metadata")
            assertEquals(EpubFixture.TITLE, metadata.getString("title"))
            assertEquals(EpubFixture.AUTHOR, metadata.getJSONArray("authors").getString(0))
            assertEquals("en", metadata.getString("language"))
            val positions = book.positions()
            assertEquals(positions, metadata.getInt(EpubContract.FIELD_POSITIONS))
            assertTrue("positions: $positions", positions >= 2)

            val toc = book.toc()
            val tocRows = JSONArray(toc.json)
            Log.i(TAG, "toc: $tocRows")
            assertFalse(toc.hasMore)
            assertEquals(2, tocRows.length())
            assertEquals(EpubFixture.CHAPTER1_HEADING, tocRows.getJSONObject(0).getString("title"))
            assertEquals(EpubFixture.CHAPTER1_HREF, tocRows.getJSONObject(0).getString("href"))
            assertEquals(EpubFixture.CHAPTER2_HREF, tocRows.getJSONObject(1).getString("href"))

            val readingOrder = JSONArray(book.readingOrder().json)
            assertEquals(2, readingOrder.length())
            assertEquals(EpubFixture.CHAPTER1_HREF, readingOrder.getJSONObject(0).getString("href"))

            val first = book.text(index = 0)
            Log.i(TAG, "text[0]: ${first.text.length} chars: ${first.text.take(120)}")
            assertEquals(EpubFixture.CHAPTER1_HREF, first.href)
            assertEquals(0, first.index)
            assertEquals(EpubContract.FORMAT_TEXT, first.format)
            assertFalse(first.hasMore)
            assertTrue(first.text.contains(EpubFixture.CHAPTER1_HEADING))
            assertTrue(first.text.contains("trimmed his ${EpubFixture.SEARCH_WORD}"))
            // Plain text drops images together with their captions (plugin decision D30); Markdown keeps them below.
            assertFalse(first.text.contains("A single dot"))
            assertFalse(first.text.contains("<p>"))

            val byHref = book.text(href = EpubFixture.CHAPTER2_HREF)
            assertEquals(1, byHref.index)
            assertTrue(byHref.text.contains("Keep the light."))

            val window = 9
            var offset = 0
            val pieces = StringBuilder()
            while (true) {
                val chunk = book.text(index = 1, offset = offset, maxChars = window)
                assertEquals(offset, chunk.offset)
                pieces.append(chunk.text)
                offset += chunk.text.length
                if (!chunk.hasMore) break
                assertEquals(window, chunk.text.length)
            }
            assertEquals(byHref.text, pieces.toString())
            val beyond = book.text(index = 1, offset = byHref.text.length + 100, maxChars = window)
            assertEquals("", beyond.text)
            assertFalse(beyond.hasMore)

            val markdown = book.text(href = EpubFixture.CHAPTER1_HREF, format = EpubContract.FORMAT_MARKDOWN)
            Log.i(TAG, "markdown: ${markdown.text}")
            assertEquals(EpubContract.FORMAT_MARKDOWN, markdown.format)
            assertTrue(markdown.text.lines().any { it == "# ${EpubFixture.CHAPTER1_HEADING}" })
            assertTrue(markdown.text.contains("![A single dot](${EpubFixture.IMAGE_HREF})"))
            val quote = book.text(href = EpubFixture.CHAPTER2_HREF, format = EpubContract.FORMAT_MARKDOWN)
            assertTrue(quote.text.lines().any { it.startsWith("> Keep the light.") })

            val hits = book.search(EpubFixture.SEARCH_WORD)
            val hitRows = JSONArray(hits.resultsJson)
            Log.i(TAG, "search: ${hitRows.length()} hits, hasMore=${hits.hasMore}: $hitRows")
            assertEquals(EpubFixture.SEARCH_WORD, hits.query)
            assertTrue(hitRows.length() >= 2)
            val paged = book.search(EpubFixture.SEARCH_WORD, offset = 1, limit = 1)
            assertEquals(1, JSONArray(paged.resultsJson).length())
            assertEquals(1, paged.offset)
            assertEquals(hitRows.getJSONObject(1).toString(), JSONArray(paged.resultsJson).getJSONObject(0).toString())
            assertEquals(0, JSONArray(book.search("zzqqxx").resultsJson).length())

            val exportDir = File(workDir, "export").apply { deleteRecursively(); mkdirs() }
            val chapter = EpubOutputSink.save(book.resource(EpubFixture.CHAPTER1_HREF), EpubOutputSink.resolveTarget(exportDir.path, "chapter1.xhtml", overwrite = false))
            assertEquals(EpubFixture.CHAPTER1_XHTML, chapter.readText())
            val image = EpubOutputSink.save(book.resource(EpubFixture.IMAGE_HREF), EpubOutputSink.resolveTarget(File(exportDir, "dot.png").path, null, overwrite = false))
            assertArrayEquals(EpubFixture.PNG, image.readBytes())
            assertFalse(EpubOutputSink.partFile(image).exists())
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubOutputSink.resolveTarget(File(exportDir, "dot.png").path, null, overwrite = false) })

            assertEquals(EpubErrorCodes.RESOURCE_NOT_FOUND, codeOf { book.resource("OEBPS/images/missing.png") })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { book.resource("") })
            assertEquals(EpubErrorCodes.RESOURCE_NOT_FOUND, codeOf { book.text(href = "OEBPS/missing.xhtml") })
            assertEquals(EpubErrorCodes.RESOURCE_NOT_FOUND, codeOf { book.text(index = 7) })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { book.text(index = -1) })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { book.text(index = 0, format = "pdf") })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { book.search("") })
            assertEquals(EpubErrorCodes.LIMIT_EXCEEDED, codeOf { book.search("x".repeat(EpubContract.MAX_QUERY_LENGTH + 1)) })
        } finally {
            book.close()
        }
        assertTrue(book.isClosed)
        assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { book.metadata() })
        book.close()
    }

    @Test
    fun hostAndPluginRefusalsCarryTheContractCodes(): Unit = runBlocking {
        pluginPackageOrSkip()
        assertEquals(EpubErrorCodes.FILE_NOT_FOUND, codeOf { EpubPluginHost.openBook(context, File(workDir, "missing.epub").path) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubPluginHost.openBook(context, " ") })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubPluginHost.openBook(context, workDir.path) })
        val notes = File(workDir, "notes.txt").apply { writeText("plain text") }
        assertEquals(EpubErrorCodes.NOT_EPUB, codeOf { EpubPluginHost.openBook(context, notes.path) })
        val junk = File(workDir, "junk.epub").apply { writeText("named like a book, not a zip") }
        val refusal = failureOf { EpubPluginHost.openBook(context, junk.path) }
        Log.i(TAG, "plugin refusal: ${refusal.code}: ${refusal.message}")
        assertEquals(EpubErrorCodes.NOT_EPUB, refusal.code)
        assertTrue(refusal.message.isNotBlank())
    }

    @Test
    fun aPluginProcessDeathClosesTheBookOnTheHostSide(): Unit = runBlocking {
        val packageName = pluginPackageOrSkip()
        val file = EpubFixture.write(File(workDir, "death.epub"))
        val book = EpubPluginHost.openBook(context, file.path)
        assertEquals(EpubFixture.TITLE, JSONObject(book.metadata()).getString("title"))
        forceStop(packageName)
        val deadline = System.currentTimeMillis() + 15_000L
        while (!book.isClosed && System.currentTimeMillis() < deadline) delay(100)
        assertTrue("the death recipient closes the client", book.isClosed)
        val failure = failureOf { book.metadata() }
        assertEquals(EpubErrorCodes.SESSION_CLOSED, failure.code)
        assertTrue(failure.message, failure.message.contains("died"))
        book.close()
        // The plugin service comes back for the next book.
        val again = EpubPluginHost.openBook(context, file.path)
        try {
            assertEquals(2, JSONArray(again.readingOrder().json).length())
        } finally {
            again.close()
        }
    }

    private suspend fun pluginPackageOrSkip(): String {
        assumeTrue("the EPUB plugin is not installed", EpubPluginHost.queryServiceCount(context) > 0)
        val discovered = EpubPluginHost.discover(context)
        val target = discovered.firstOrNull { it.pluginInfo != null }
            ?: throw AssertionError("no usable EPUB plugin: ${discovered.map { "${it.serviceInfo.packageName}: ${it.error?.message}" }}")
        val info = requireNotNull(target.pluginInfo)
        Log.i(TAG, "plugin ${target.serviceInfo.packageName} ${info.versionName} (${info.versionCode}) capabilities: ${info.capabilities}")
        return target.serviceInfo.packageName
    }

    private fun forceStop(packageName: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop $packageName")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        Log.i(TAG, "force-stopped $packageName")
    }

    private inline fun failureOf(block: () -> Any?): EpubError {
        try {
            block()
        } catch (e: EpubPluginException) {
            return e.error
        }
        fail("expected an EpubPluginException")
        throw AssertionError()
    }

    private inline fun codeOf(block: () -> Any?): String = failureOf(block).code

    companion object {
        private const val TAG = "EpubPluginRoundTripTest"
    }
}
