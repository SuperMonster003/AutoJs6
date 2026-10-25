package org.autojs.autojs.runtime.api.modules.mediainfo

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MediainfoSnapshotRhinoInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun settleBefore() {
        service.stopAll()
        awaitNoRegisteredEngines("before MediaInfo Rhino test")
    }

    @After
    fun settleAfter() {
        service.stopAll()
        awaitNoRegisteredEngines("after MediaInfo Rhino test")
    }

    @Test
    fun publicRhinoApiKeepsReadAndNegotiatesSnapshotV2Explicitly() {
        assumeTrue("A MediaInfo plugin is not installed", MediainfoPluginHost.queryServiceCount(context) > 0)

        val workDir = freshWorkDir()
        try {
            File(workDir, FIXTURE_NAME).writeBytes(minimalPcmWave())
            InstrumentationRegistry.getInstrumentation().context.assets.open("mediainfo-two-audio.mka").use { input ->
                File(workDir, "two-audio.mka").outputStream().use { input.copyTo(it) }
            }
            val marker = File(workDir, MARKER_NAME)
            val startedEngineClass = runViaScriptEngineService(workDir, marker)

            assertTrue(
                "Expected the production Rhino engine, but started ${startedEngineClass.name}",
                RhinoJavaScriptEngine::class.java.isAssignableFrom(startedEngineClass),
            )

            val result = JSONObject(marker.readText())
            assertEquals("autojs6-plugin-mediainfo-snapshot-v1", result.getString("v1Schema"))
            assertEquals("autojs6-plugin-mediainfo-snapshot-v2", result.getString("v2Schema"))
            assertEquals(FIXTURE_NAME, result.getString("fileName"))
            assertEquals("MediaInfoLib", result.getString("engineName"))
            assertTrue(result.getString("engineVersion").isNotBlank())
            assertTrue(result.getInt("audioTracks") > 0)
            assertEquals("", result.getString("inform"))
            assertEquals("autojs6-plugin-mediainfo-snapshot-v1", result.getString("defaultSchema"))
            assertTrue(result.getBoolean("advertisesV2"))
            assertTrue(result.getBoolean("legacyReadStillWorks"))
            assertEquals(2, result.getInt("streamCount"))
            assertEquals("16000", result.getString("secondRate"))
            assertEquals("Hz", result.getString("secondUnit").trim())
            assertEquals("16000", result.getString("accessorRate"))
            assertTrue(result.getBoolean("invalidIndexRejected"))
            assertTrue(result.getString("report"), result.getString("report").contains(File(workDir, FIXTURE_NAME).absolutePath))
            assertTrue(result.getBoolean("paddedSchemaRejected"))
            assertTrue(
                result.getString("paddedSchemaMessage"),
                result.getString("paddedSchemaMessage").contains("Unsupported MediaInfo snapshot schema"),
            )
            assertFalse(result.has("infoParameters"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun runViaScriptEngineService(workDir: File, marker: File): Class<*> {
        val completed = CountDownLatch(1)
        val startedEngineClass = AtomicReference<Class<*>>()
        val failure = AtomicReference<Throwable>()
        val sourceFile = File(workDir, SCRIPT_NAME).apply { writeText(RHINO_SOURCE) }
        val source = JavaScriptFileSource(sourceFile)
        assertEquals(JavaScriptSource.ENGINE, source.engineName)

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                startedEngineClass.set(execution.engine.javaClass)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }

        val execution = try {
            service.execute(
                source,
                listener,
                ExecutionConfig(workingDirectory = workDir.absolutePath),
            )
        } catch (error: Throwable) {
            throw AssertionError("Failed to start MediaInfo Rhino script", error)
        }

        if (!completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            service.stopAll()
            throw AssertionError("Timed out waiting for MediaInfo Rhino script")
        }
        awaitEngineRemoved(execution.id)
        failure.get()?.let { error ->
            throw AssertionError("MediaInfo Rhino script failed", error)
        }
        assertTrue("The Rhino script completed without writing its result marker", marker.isFile)
        return checkNotNull(startedEngineClass.get()) {
            "ScriptEngineService completed without reporting the started engine"
        }
    }

    private fun freshWorkDir(): File =
        File(context.cacheDir, "mediainfo-snapshot-rhino-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create MediaInfo Rhino test directory: $absolutePath" }
        }

    private fun awaitEngineRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MS)
        }
        throw AssertionError("Engine $executionId remained registered after MediaInfo Rhino script")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty() && service.scriptExecutions.isEmpty()) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MS)
        }
        throw AssertionError(
            "$label left registered engines ${service.engines.map { engine -> engine.id }} " +
                    "or tracked executions ${service.scriptExecutions}",
        )
    }

    private fun minimalPcmWave(): ByteArray {
        val pcm = byteArrayOf(0, 0, 0, 0)
        return ByteArray(44 + pcm.size).also { bytes ->
            fun putAscii(offset: Int, value: String) {
                value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
            }
            fun putShortLe(offset: Int, value: Int) {
                bytes[offset] = (value and 0xff).toByte()
                bytes[offset + 1] = (value ushr 8 and 0xff).toByte()
            }
            fun putIntLe(offset: Int, value: Int) {
                repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8) and 0xff).toByte() }
            }
            putAscii(0, "RIFF")
            putIntLe(4, 36 + pcm.size)
            putAscii(8, "WAVE")
            putAscii(12, "fmt ")
            putIntLe(16, 16)
            putShortLe(20, 1)
            putShortLe(22, 1)
            putIntLe(24, 8_000)
            putIntLe(28, 16_000)
            putShortLe(32, 2)
            putShortLe(34, 16)
            putAscii(36, "data")
            putIntLe(40, pcm.size)
            pcm.copyInto(bytes, 44)
        }
    }

    companion object {
        private const val FIXTURE_NAME = "sample.wav"
        private const val MARKER_NAME = "result.json"
        private const val SCRIPT_NAME = "mediainfo-snapshot-rhino.js"
        private const val EXECUTION_TIMEOUT_SECONDS = 60L
        private const val ENGINE_REMOVAL_TIMEOUT_MS = 5_000L
        private const val ENGINE_REMOVAL_POLL_MS = 25L

        private val RHINO_SOURCE =
            """
            "rhino";
            var legacy = mediainfo.read("./$FIXTURE_NAME");
            var capabilities = mediainfo.capabilities();
            let streams = mediainfo.read("two-audio.mka");
            let invalidIndexRejected = false;
            try { mediainfo.get("two-audio.mka", "audio", "SamplingRate", {streamNumber: 0.5}); }
            catch (error) { invalidIndexRejected = true; }
            var v1 = mediainfo.snapshot("./$FIXTURE_NAME", {
              includeInform: false,
              includeSections: true
            });
            var v2 = mediainfo.snapshot("./$FIXTURE_NAME", {
              schema: mediainfo.SNAPSHOT_SCHEMA_V2,
              includeInform: false,
              includeSections: true
            });
            var paddedSchemaRejected = false;
            var paddedSchemaMessage = "";
            try {
              mediainfo.snapshot("./$FIXTURE_NAME", {
                schema: " " + mediainfo.SNAPSHOT_SCHEMA_V2
              });
            } catch (error) {
              paddedSchemaRejected = true;
              paddedSchemaMessage = String(error);
            }
            files.write("./$MARKER_NAME", JSON.stringify({
              v1Schema: v1.schema,
              streamCount: mediainfo.countGet("two-audio.mka", "audio"),
              secondRate: mediainfo.get("two-audio.mka", "audio", "SamplingRate", {streamNumber: 1}),
              secondUnit: mediainfo.get("two-audio.mka", "audio", "SamplingRate", {streamNumber: 1, infoKind: "MEASURE"}),
              accessorRate: streams.audio("SamplingRate", {streamNumber: 1}),
              invalidIndexRejected: invalidIndexRejected,
              report: legacy.inform,
              v2Schema: v2.schema,
              fileName: v2.file.name,
              engineName: v2.engine.name,
              engineVersion: v2.engine.version,
              audioTracks: v2.tracks.audio.length,
              inform: v2.inform,
              defaultSchema: capabilities.defaultSnapshotSchema,
              advertisesV2: capabilities.snapshotSchemas.indexOf(mediainfo.SNAPSHOT_SCHEMA_V2) >= 0,
              legacyReadStillWorks: typeof legacy.inform === "string" && typeof legacy.audio === "function",
              paddedSchemaRejected: paddedSchemaRejected,
              paddedSchemaMessage: paddedSchemaMessage
            }));
            """.trimIndent()
    }
}
