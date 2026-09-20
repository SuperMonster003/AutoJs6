package org.autojs.autojs.core.plugin.epub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.plugin.epub.api.EpubCapabilityKeys
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real two-step reader launch with the installed EPUB plugin (plugin roadmap P5.4, decisions D12
 * and D32): [EpubPluginHost.openReader] from a foreground host Activity, the plugin's reader
 * Activity started by the host with the session token, the `open`, `progress`, `error` and
 * `close` events through [EpubReaderBridge] with one generation and increasing sequence numbers,
 * `state`, `goTo`, `navigate`, `setPreferences` and `bookmarks` over the real Binder, the refusal
 * codes of the session, and `close(finish = true)` ending the reader.
 *
 * Skipped when no EPUB plugin is installed or the plugin does not advertise `reader-session`.
 *
 * ```
 * .\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.core.plugin.epub.EpubReaderRoundTripTest"
 * ```
 */
class EpubReaderRoundTripTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    private val workDir: File
        get() = File(context.cacheDir, "epub-reader-round-trip").apply { mkdirs() }

    private var foreground: Activity? = null

    @After
    fun tearDown() {
        foreground?.let { activity ->
            instrumentation.runOnMainSync { activity.finish() }
        }
        foreground = null
    }

    @Test
    fun readerSessionRoundTripThroughThePluginReader(): Unit = runBlocking {
        pluginPackageOrSkip()
        val features = EpubPluginHost.getCapabilities(context).getStringArray(EpubCapabilityKeys.FEATURES)?.toList().orEmpty()
        assumeTrue("the plugin advertises no reader session", EpubContract.FEATURE_READER_SESSION in features)

        val file = EpubFixture.write(File(workDir, "reader.epub"))
        val host = startForeground()
        val sink = RecordingSink()
        val options = EpubReaderOptions(
            href = EpubFixture.CHAPTER2_HREF,
            preferencesJson = """{"fontSize": 1.5, "theme": "dark", "bogus": 1}""",
        )
        val client = EpubPluginHost.openReader(host, file.path, options, sink)
        try {
            assertEquals(32, client.token.length)
            assertTrue(client.token.all { it in '0'..'9' || it in 'a'..'f' })

            val open = sink.await("open", timeoutMillis = OPEN_TIMEOUT_MS) { it.type == EpubContract.EVENT_OPEN }
            Log.i(TAG, "open: $open")
            assertEquals(true, open.visible)
            assertEquals(EpubFixture.TITLE, open.title)
            assertEquals(EpubFixture.CHAPTER2_HREF, open.href)
            assertNotNull(open.locatorJson)
            assertTrue((open.positions ?: 0) > 0)

            val unsupported = sink.await("unsupported preference") { it.type == EpubContract.EVENT_ERROR }
            assertEquals(EpubErrorCodes.UNSUPPORTED_PREFERENCE, unsupported.errorCode)
            assertTrue(unsupported.errorMessage.orEmpty(), unsupported.errorMessage.orEmpty().contains("bogus"))

            val state = client.state()
            Log.i(TAG, "state: $state")
            assertEquals(client.token, state.token)
            assertTrue(state.visible)
            assertEquals(EpubFixture.CHAPTER2_HREF, state.href)
            assertEquals(1, state.index)
            assertEquals(open.positions, state.positions)
            assertNotNull(state.locatorJson)

            var mark = sink.size
            client.navigate(EpubContract.DIRECTION_PREVIOUS_CHAPTER)
            val chapter1 = sink.await("previous chapter", after = mark) {
                it.type == EpubContract.EVENT_PROGRESS && it.href == EpubFixture.CHAPTER1_HREF
            }
            Log.i(TAG, "previous chapter: $chapter1")
            val chapter1Locator = requireNotNull(chapter1.locatorJson)
            assertEquals(EpubFixture.CHAPTER1_HREF, JSONObject(chapter1Locator).getString(EpubContract.FIELD_HREF))

            mark = sink.size
            client.goTo(progression = 1.0)
            val end = sink.await("progression 1.0", after = mark) {
                it.type == EpubContract.EVENT_PROGRESS && it.href == EpubFixture.CHAPTER2_HREF
            }
            Log.i(TAG, "progression 1.0: $end")
            assertNotNull(end.progression)

            mark = sink.size
            client.goTo(locatorJson = chapter1Locator)
            val back = sink.await("locator", after = mark) {
                it.type == EpubContract.EVENT_PROGRESS && it.href == EpubFixture.CHAPTER1_HREF
            }
            Log.i(TAG, "locator: $back")

            mark = sink.size
            client.goTo(href = EpubFixture.CHAPTER2_HREF)
            sink.await("href", after = mark) { it.type == EpubContract.EVENT_PROGRESS && it.href == EpubFixture.CHAPTER2_HREF }
            assertEquals(EpubFixture.CHAPTER2_HREF, client.state().href)

            mark = sink.size
            client.setPreferences("""{"lineHeight": 1.5, "nope": true}""")
            val refusedKey = sink.await("second unsupported preference", after = mark) { it.type == EpubContract.EVENT_ERROR }
            assertEquals(EpubErrorCodes.UNSUPPORTED_PREFERENCE, refusedKey.errorCode)
            assertTrue(refusedKey.errorMessage.orEmpty(), refusedKey.errorMessage.orEmpty().contains("nope"))

            val bookmarks = JSONArray(client.bookmarks())
            Log.i(TAG, "bookmarks: ${bookmarks.length()}")

            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.navigate(99) })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.goTo() })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.goTo(progression = 1.5) })
            assertEquals(EpubErrorCodes.RESOURCE_NOT_FOUND, codeOf { client.goTo(href = "OEBPS/missing.xhtml") })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.goTo(locatorJson = "not json") })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.setPreferences("""{"theme": "neon"}""") })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.setPreferences("""{"fontSize": 9}""") })
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { client.setPreferences("[]") })

            assertEquals(0L, client.bridge.staleCount + client.bridge.reorderedCount + client.bridge.malformedCount)
            assertFalse(client.isClosed)

            mark = sink.size
            client.close(finish = true)
            val close = sink.await("close", after = mark) { it.type == EpubContract.EVENT_CLOSE }
            assertEquals(EpubContract.REASON_HOST, close.reason)
            assertTrue(client.isClosed)
            assertTrue(client.bridge.isClosed)
            assertEquals(1, sink.events.count { it.type == EpubContract.EVENT_CLOSE })
            assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { client.state() })
            Log.i(TAG, "events: ${sink.events.size} delivered=${client.bridge.deliveredCount} dropped=${sink.dropped}")
        } finally {
            runCatching { client.close(finish = true) }
        }
    }

    private suspend fun pluginPackageOrSkip(): String {
        assumeTrue("the EPUB plugin is not installed", EpubPluginHost.queryServiceCount(context) > 0)
        val discovered = EpubPluginHost.discover(context)
        val target = discovered.firstOrNull { it.pluginInfo != null }
            ?: throw AssertionError("no usable EPUB plugin: ${discovered.map { "${it.serviceInfo.packageName}: ${it.error?.message}" }}")
        val info = requireNotNull(target.pluginInfo)
        Log.i(TAG, "plugin ${target.serviceInfo.packageName} ${info.versionName} (${info.versionCode})")
        return target.serviceInfo.packageName
    }

    /** A host Activity in the foreground, so the host may start the plugin's reader Activity. */
    private fun startForeground(): Activity {
        val intent = Intent(context, AboutActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return instrumentation.startActivitySync(intent).also { foreground = it }
    }

    private inline fun codeOf(block: () -> Any?): String {
        try {
            block()
        } catch (e: EpubPluginException) {
            return e.error.code
        }
        fail("expected an EpubPluginException")
        throw AssertionError()
    }

    private class RecordingSink : EpubReaderSink {
        val events = CopyOnWriteArrayList<EpubReaderEvent>()
        val dropped = CopyOnWriteArrayList<String>()

        val size: Int get() = events.size

        override fun onEvent(event: EpubReaderEvent) {
            events += event
        }

        override fun onDropped(reason: String, generation: Long, seq: Long) {
            dropped += "$reason $generation/$seq"
        }

        fun await(what: String, after: Int = 0, timeoutMillis: Long = EVENT_TIMEOUT_MS, predicate: (EpubReaderEvent) -> Boolean): EpubReaderEvent {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (true) {
                val snapshot = events.toList()
                for (index in after until snapshot.size) {
                    if (predicate(snapshot[index])) return snapshot[index]
                }
                if (System.currentTimeMillis() >= deadline) {
                    throw AssertionError("no $what event within $timeoutMillis ms; events: ${snapshot.map { it.type + (it.href?.let { h -> "@$h" } ?: "") }} dropped: $dropped")
                }
                Thread.sleep(100)
            }
        }
    }

    companion object {
        private const val TAG = "EpubReaderRoundTripTest"
        private const val OPEN_TIMEOUT_MS = 30_000L
        private const val EVENT_TIMEOUT_MS = 10_000L
    }
}
