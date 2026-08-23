package org.autojs.autojs.core.plugin.typescript

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.autojs6.R
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeScriptCompilerPluginClientDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compilesUtf8SingleFileThroughRealPluginBinder() {
        withSource(
            name = "你好.ts",
            source = """
                const message: string = "host-client-ok"
                toast(message)
            """.trimIndent(),
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Emitted)
            output as TypeScriptCompilationOutput.Emitted
            assertTrue(output.javaScript.contains("host-client-ok"))
            assertTrue(output.sourceMap.contains("\"version\":3"))
            assertTrue(output.entryModulePath.endsWith("你好.js"))
            assertFalse(output.diagnostics.any(TypeScriptDiagnostic::isError))
        }
    }

    @Test
    fun returnsBoundedDiagnosticsWithoutExecutableOutput() {
        withSource(
            name = "diagnostic.ts",
            source = "const count: number = \"not-a-number\"",
        ) { source ->
            val output = runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(source)
            }

            assertTrue(output is TypeScriptCompilationOutput.Diagnostics)
            output as TypeScriptCompilationOutput.Diagnostics
            assertEquals(1, output.diagnostics.count(TypeScriptDiagnostic::isError))
            assertTrue(
                output.diagnostics.any { diagnostic ->
                    diagnostic.category == TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR &&
                        diagnostic.uri?.endsWith("/diagnostic.ts") == true &&
                        diagnostic.range != null
                },
            )
        }
    }

    @Test
    fun routesCompiledTypeScriptIntoRhinoExecution() {
        val markerKey = "autojs6.typescript.execution.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "hello.ts",
                source = """
                    declare const java: any
                    const message: string = "TypeScript says hello"
                    toast(message)
                    java.lang.System.setProperty("$markerKey", "executed")
                """.trimIndent(),
            ) { source ->
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                assertTrue(launch.source is TypeScriptFileSource)
                val terminal = executeAndAwait(launch.source, source.parent.orEmpty())

                terminal.failure.get()?.let { error ->
                    throw AssertionError("Compiled TypeScript execution failed", error)
                }
                assertTrue(terminal.succeeded.get())
                assertTrue(terminal.execution.engine is LoopBasedJavaScriptEngine)
                assertEquals("executed", System.getProperty(markerKey))
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun exposesDeviceAndGestureApisToStrictTypeScript() {
        val markerKey = "autojs6.typescript.device-gesture.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "device-and-gesture.ts",
                source = """
                    declare const java: any
                    const snapshotWidth: number = device.width
                    const snapshotHeight: number = device.height
                    const snapshotSdk: number = device.sdkInt
                    const snapshotModel: string = device.model
                    const clickFunction: (x: number, y: number) => boolean = click
                    const swipeFunction: (
                        x1: number,
                        y1: number,
                        x2: number,
                        y2: number,
                        duration: number,
                    ) => boolean = swipe
                    const automatorClick: (x: number, y: number) => boolean = automator.click
                    const automatorSwipe: (
                        x1: number,
                        y1: number,
                        x2: number,
                        y2: number,
                        duration: number,
                    ) => boolean = automator.swipe
                    if (
                        snapshotWidth <= 0 ||
                        snapshotHeight <= 0 ||
                        snapshotSdk <= 0 ||
                        snapshotModel.length === 0
                    ) {
                        throw new Error("Invalid device snapshot")
                    }
                    if (
                        typeof clickFunction !== "function" ||
                        typeof swipeFunction !== "function" ||
                        typeof automatorClick !== "function" ||
                        typeof automatorSwipe !== "function"
                    ) {
                        throw new Error("Gesture API is unavailable")
                    }
                    java.lang.System.setProperty("$markerKey", String(snapshotSdk))
                """.trimIndent(),
            ) { source ->
                val terminal = executeAndAwait(source)

                terminal.failure.get()?.let { error ->
                    throw AssertionError("Device and gesture TypeScript smoke failed", error)
                }
                assertTrue(terminal.succeeded.get())
                assertTrue(System.getProperty(markerKey)?.toIntOrNull()?.let { it > 0 } == true)
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun performsLocalHttpRequestFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.http.${System.nanoTime()}"
        val expectedBody = "typescript-http-ok"
        System.clearProperty(markerKey)
        try {
            withSingleRequestHttpServer(expectedBody) { url ->
                withSource(
                    name = "http.ts",
                    source = """
                        declare const java: any
                        const response: Http.WrappedResponse = http.get(
                            ${JSONObject.quote(url)},
                            { timeout: 5000 },
                        )
                        if (response.statusCode !== 200) {
                            throw new Error("Unexpected HTTP status: " + response.statusCode)
                        }
                        const body: string = response.body.string()
                        if (body !== ${JSONObject.quote(expectedBody)}) {
                            throw new Error("Unexpected HTTP body: " + body)
                        }
                        java.lang.System.setProperty("$markerKey", body)
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("HTTP TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals(expectedBody, System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun readsAndInspectsImageFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.images.${System.nanoTime()}"
        val expectedColor = Color.rgb(18, 52, 86)
        System.clearProperty(markerKey)
        try {
            withPng(expectedColor) { imageFile ->
                withSource(
                    name = "images.ts",
                    source = """
                        declare const java: any
                        const image: ImageWrapper | null = images.read(
                            ${JSONObject.quote(imageFile.absolutePath)},
                            true,
                        )
                        if (image === null) {
                            throw new Error("Image could not be read")
                        }
                        try {
                            if (image.width !== 2 || image.height !== 2) {
                                throw new Error("Unexpected image dimensions")
                            }
                            const pixel: number = images.pixel(image, 0, 0)
                            if (pixel !== $expectedColor) {
                                throw new Error("Unexpected image pixel: " + pixel)
                            }
                            java.lang.System.setProperty("$markerKey", String(pixel))
                        } finally {
                            if (!images.recycle(image)) {
                                throw new Error("Image recycle failed")
                            }
                        }
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Images TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals(expectedColor, System.getProperty(markerKey)?.toIntOrNull())
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun typeErrorsNeverFallBackToRawRhinoExecution() {
        val markerKey = "autojs6.typescript.rejected.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withSource(
                name = "rejected.ts",
                source = """
                    declare const java: any
                    const count: number = "not-a-number"
                    java.lang.System.setProperty("$markerKey", "must-not-run")
                """.trimIndent(),
            ) { source ->
                val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
                val terminal = executeAndAwait(launch.source, source.parent.orEmpty())
                val failure = terminal.failure.get()

                assertFalse(terminal.succeeded.get())
                assertTrue(failure is TypeScriptDiagnosticsException)
                assertTrue(failure?.message?.contains("TS2322") == true)
                assertEquals(null, System.getProperty(markerKey))
                assertEquals(null, terminal.execution.engine)
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun rejectsTsxWithLocalizedSingleFileProfileError() {
        withSource(
            name = "component.tsx",
            source = "const view = <text>Hello</text>",
        ) { source ->
            val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
            try {
                AutoJs.instance.scriptEngineService.execute(
                    launch.source,
                    ExecutionConfig(workingDirectory = source.parent.orEmpty()),
                )
                fail("Ordinary single-file TSX must be rejected before engine creation")
            } catch (error: IllegalArgumentException) {
                assertEquals(
                    context.getString(R.string.error_typescript_source_not_executable, source.name),
                    error.message,
                )
            }
        }
    }

    private fun withSource(name: String, source: String, block: (File) -> Unit) {
        val root = File(context.cacheDir, "typescript-client-${System.nanoTime()}")
        check(root.mkdirs())
        try {
            val sourceFile = File(root, name)
            sourceFile.writeText(source, Charsets.UTF_8)
            block(sourceFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withPng(color: Int, block: (File) -> Unit) {
        val imageFile = File(context.cacheDir, "typescript-image-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        try {
            FileOutputStream(imageFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            block(imageFile)
        } finally {
            bitmap.recycle()
            imageFile.delete()
        }
    }

    private fun withSingleRequestHttpServer(responseBody: String, block: (String) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val served = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val serverThread = Thread({
            try {
                server.accept().use { socket ->
                    socket.soTimeout = HTTP_TIMEOUT_MILLIS
                    val input = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val bodyBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                    val headers = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/plain; charset=utf-8\r\n")
                        append("Content-Length: ${bodyBytes.size}\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }.toByteArray(StandardCharsets.US_ASCII)
                    socket.getOutputStream().use { output ->
                        output.write(headers)
                        output.write(bodyBytes)
                        output.flush()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                served.countDown()
            }
        }, "typescript-http-smoke").apply {
            isDaemon = true
            start()
        }
        try {
            block("http://127.0.0.1:${server.localPort}/typescript-smoke")
            assertTrue(
                "Local HTTP smoke server did not handle the request",
                served.await(HTTP_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS),
            )
            failure.get()?.let { error ->
                throw AssertionError("Local HTTP smoke server failed", error)
            }
        } finally {
            server.close()
            serverThread.join(HTTP_TIMEOUT_MILLIS.toLong())
        }
    }

    private fun executeAndAwait(source: File): ExecutionTerminal {
        val launch = ScriptLaunchSourceFactory.createFileLaunch(source)
        assertTrue(launch.source is TypeScriptFileSource)
        return executeAndAwait(launch.source, source.parent.orEmpty())
    }

    private fun executeAndAwait(
        source: org.autojs.autojs.script.ScriptSource,
        workingDirectory: String,
    ): ExecutionTerminal {
        val completed = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                succeeded.set(true)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            source,
            listener,
            ExecutionConfig(workingDirectory = workingDirectory),
        )
        assertTrue(
            "TypeScript execution did not reach a terminal state",
            completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        return ExecutionTerminal(execution, succeeded, failure)
    }

    private data class ExecutionTerminal(
        val execution: ScriptExecution,
        val succeeded: AtomicBoolean,
        val failure: AtomicReference<Throwable>,
    )

    private companion object {
        const val EXECUTION_TIMEOUT_SECONDS = 90L
        const val HTTP_TIMEOUT_MILLIS = 10_000
    }
}
