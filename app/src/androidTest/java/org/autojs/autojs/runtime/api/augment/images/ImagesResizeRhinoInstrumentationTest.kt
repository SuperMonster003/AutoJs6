package org.autojs.autojs.runtime.api.augment.images

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** End-to-end Rhino coverage for the public images.resize argument contract. */
class ImagesResizeRhinoInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun settleBefore() {
        service.stopAll()
        awaitNoRegisteredEngines("before images.resize Rhino test")
    }

    @After
    fun settleAfter() {
        service.stopAll()
        awaitNoRegisteredEngines("after images.resize Rhino test")
    }

    @Test
    fun scalarWidthAndHeightAreCatchableAndArraySizeStillWorksInSameRhinoProcess() {
        assumeTrue("An eligible OpenCV plugin is not installed", OpenCvPluginHost.isAvailable(context))

        val workDir = freshWorkDir()
        try {
            writeFixturePng(File(workDir, FIXTURE_NAME))
            val marker = File(workDir, MARKER_NAME)
            val startedEngineClass = runViaScriptEngineService(workDir, marker)

            assertTrue(
                "Expected the production Rhino engine, but started ${startedEngineClass.name}",
                RhinoJavaScriptEngine::class.java.isAssignableFrom(startedEngineClass),
            )

            val result = JSONObject(marker.readText())
            assertTrue("The scalar size call was not rejected by the script API", result.getBoolean("caught"))
            val message = result.getString("message")
            if (DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get()) {
                // The new script module layer (on by default since docs/augment/ROADMAP.md P5.1) rejects the size as a
                // TypeError naming the function and the argument (D10), in the language of the app.
                assertTrue(message, message.startsWith("TypeError") && message.contains("\"images.resize\"") && message.contains("\"size\""))
            } else {
                assertTrue(message, message.contains("images.resize(image, [width, height], interpolation?)"))
            }
            assertEquals(7, result.getInt("width"))
            assertEquals(5, result.getInt("height"))
            assertTrue("The script did not continue after catching the resize error", result.getBoolean("continued"))
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
            throw AssertionError("Failed to start images.resize regression script", error)
        }

        if (!completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            service.stopAll()
            throw AssertionError("Timed out waiting for images.resize regression script")
        }
        awaitEngineRemoved(execution.id)
        failure.get()?.let { error ->
            throw AssertionError("images.resize regression script failed", error)
        }
        assertTrue("The Rhino script completed without writing its result marker", marker.isFile)
        return checkNotNull(startedEngineClass.get()) {
            "ScriptEngineService completed without reporting the started engine"
        }
    }

    private fun writeFixturePng(file: File) {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(
                intArrayOf(
                    Color.RED,
                    Color.GREEN,
                    Color.BLUE,
                    Color.CYAN,
                    Color.MAGENTA,
                    Color.YELLOW,
                ),
                0,
                3,
                0,
                0,
                3,
                2,
            )
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Failed to encode images.resize test fixture"
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun freshWorkDir(): File =
        File(context.cacheDir, "images-resize-rhino-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create images.resize test directory: $absolutePath" }
        }

    private fun awaitEngineRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MS)
        }
        throw AssertionError("Engine $executionId remained registered after images.resize regression script")
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

    companion object {
        private const val FIXTURE_NAME = "source.png"
        private const val MARKER_NAME = "result.json"
        private const val SCRIPT_NAME = "images-resize-regression.js"
        private const val EXECUTION_TIMEOUT_SECONDS = 30L
        private const val ENGINE_REMOVAL_TIMEOUT_MS = 5_000L
        private const val ENGINE_REMOVAL_POLL_MS = 25L

        private val RHINO_SOURCE =
            """
            "rhino";
            var source = images.read("./$FIXTURE_NAME");
            if (source === null) {
              throw new Error("Failed to read images.resize test fixture");
            }
            var resized = null;
            var caught = false;
            var message = "";
            try {
              try {
                images.resize(source, 320, 240);
              } catch (error) {
                caught = true;
                message = String(error);
              }
              if (!caught) {
                throw new Error("images.resize accepted scalar width and height arguments");
              }
              resized = images.resize(source, [7, 5]);
              if (resized.getWidth() !== 7 || resized.getHeight() !== 5) {
                throw new Error("images.resize returned unexpected dimensions");
              }
              files.write("./$MARKER_NAME", JSON.stringify({
                caught: caught,
                message: message,
                width: resized.getWidth(),
                height: resized.getHeight(),
                continued: true
              }));
            } finally {
              if (resized !== null) {
                resized.recycle();
              }
              source.recycle();
            }
            """.trimIndent()
    }
}
