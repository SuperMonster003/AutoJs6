package org.autojs.autojs.core.plugin.typescript

import android.Manifest
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.AppOps
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.console.StackFrameParser
import org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.exception.ScriptCompilationDiagnosticSeverity
import org.autojs.autojs.runtime.exception.ScriptCompilationException
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.autojs.ui.log.StackFrameTargetResolver
import org.autojs.autojs.ui.main.MainActivity
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

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

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
    fun dispatchesRealClickAndSwipeFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.real-gesture.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withGestureTarget { target ->
                withSource(
                    name = "real-gesture.ts",
                    source = """
                        declare const java: any
                        if (!click(${target.clickX}, ${target.clickY})) {
                            throw new Error("Coordinate click was not dispatched")
                        }
                        if (!swipe(
                            ${target.swipeStartX},
                            ${target.swipeStartY},
                            ${target.swipeEndX},
                            ${target.swipeEndY},
                            500,
                        )) {
                            throw new Error("Coordinate swipe was not dispatched")
                        }
                        java.lang.System.setProperty("$markerKey", "dispatched")
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Real TypeScript gesture smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("dispatched", System.getProperty(markerKey))
                    assertTrue(
                        "The controlled button did not receive the TypeScript click",
                        target.clicked.await(GESTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                    assertTrue(
                        "The controlled surface did not receive the TypeScript swipe",
                        target.swiped.await(GESTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                }
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
    fun returnsFalseWhenScreenCaptureConsentIsDenied() {
        val markerKey = "autojs6.typescript.capture-denied.${System.nanoTime()}"
        System.clearProperty(markerKey)
        assertFalse("PROJECT_MEDIA must not bypass the denial smoke", AppOps.isProjectMediaAccessGranted(context))
        try {
            withMediaProjectionConsent(MediaProjectionConsent.DENY) {
                withSource(
                    name = "capture-denied.ts",
                    source = """
                        declare const java: any
                        const granted: boolean = requestScreenCapture()
                        if (granted) {
                            throw new Error("Screen capture consent unexpectedly succeeded")
                        }
                        java.lang.System.setProperty("$markerKey", "denied")
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Denied screen-capture TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("denied", System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun capturesAndInspectsScreenAfterAutomatedConsent() {
        val markerKey = "autojs6.typescript.capture.${System.nanoTime()}"
        System.clearProperty(markerKey)
        assertFalse("PROJECT_MEDIA must not bypass the consent smoke", AppOps.isProjectMediaAccessGranted(context))
        try {
            withMediaProjectionConsent(MediaProjectionConsent.ALLOW) {
                withSource(
                    name = "capture-screen.ts",
                    source = """
                        declare const java: any
                        if (!requestScreenCapture()) {
                            throw new Error("Screen capture consent was not granted")
                        }
                        const captured: ImageWrapper = captureScreen()
                        try {
                            const captureWidth: number = captured.width
                            const captureHeight: number = captured.height
                            if (captureWidth <= 0 || captureHeight <= 0) {
                                throw new Error("Captured frame has invalid dimensions")
                            }
                            const centerPixel: number = images.pixel(
                                captured,
                                Math.floor(captureWidth / 2),
                                Math.floor(captureHeight / 2),
                            )
                            java.lang.System.setProperty(
                                "$markerKey",
                                String(captureWidth) + "x" + String(captureHeight) + ":" + String(centerPixel),
                            )
                        } finally {
                            if (!images.recycle(captured)) {
                                throw new Error("Captured frame recycle failed")
                            }
                        }
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("Real screen-capture TypeScript smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    val marker = System.getProperty(markerKey).orEmpty()
                    assertTrue("Captured frame marker is malformed: $marker", CAPTURE_MARKER.matches(marker))
                }
            }
            assertTrue(
                "Screen-capture foreground service remained active after script teardown",
                waitUntil(SERVICE_TEARDOWN_TIMEOUT_MILLIS) {
                    !ScreenCapturerForegroundService.isForegroundStarted()
                },
            )
        } finally {
            context.stopService(Intent(context, ScreenCapturerForegroundService::class.java))
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun runsStringXmlUiFromStrictTypeScript() {
        val markerKey = "autojs6.typescript.ui.${System.nanoTime()}"
        System.clearProperty(markerKey)
        try {
            withAccessibilityNodeClick(UI_BUTTON_TEXT) {
                withSource(
                    name = "string-xml-ui.ts",
                    source = """
                        "ui";
                        declare const java: any
                        ui.layout(
                            '<vertical gravity="center" padding="24">' +
                                '<text id="status" text="TypeScript UI ready" textSize="20sp" />' +
                                '<button id="action" text="$UI_BUTTON_TEXT" />' +
                            '</vertical>',
                        )
                        const tsUiStatusView: UI.View | null = ui.findView("status")
                        const tsUiActionView: UI.View | null = ui.findView("action")
                        if (tsUiStatusView === null || tsUiActionView === null) {
                            throw new Error("String XML views were not inflated")
                        }
                        tsUiActionView.click(() => {
                            tsUiStatusView.setText("TypeScript UI clicked")
                            java.lang.System.setProperty("$markerKey", "clicked")
                            ui.finish()
                        })
                    """.trimIndent(),
                ) { source ->
                    val terminal = executeAndAwait(source)

                    terminal.failure.get()?.let { error ->
                        throw AssertionError("String XML TypeScript UI smoke failed", error)
                    }
                    assertTrue(terminal.succeeded.get())
                    assertEquals("clicked", System.getProperty(markerKey))
                }
            }
        } finally {
            System.clearProperty(markerKey)
        }
    }

    @Test
    fun typeErrorsNeverFallBackToRawRhinoExecution() {
        val markerKey = "autojs6.typescript.rejected.${System.nanoTime()}"
        val console = AutoJs.instance.globalConsole
        System.clearProperty(markerKey)
        console.clear()
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
                assertTrue(
                    "Expected ScriptCompilationException, got " +
                        (failure?.javaClass?.name ?: "no failure"),
                    failure is ScriptCompilationException,
                )
                failure as ScriptCompilationException
                assertEquals(
                    context.getString(R.string.error_typescript_compilation_failed),
                    failure.message,
                )
                val diagnostic = failure.diagnostics.single { it.code == "TS2322" }
                assertEquals(source.name, diagnostic.sourceName)
                assertEquals(ScriptCompilationDiagnosticSeverity.ERROR, diagnostic.severity)
                assertTrue(diagnostic.lineNumber != null && diagnostic.lineNumber > 0)
                assertTrue(diagnostic.columnNumber != null && diagnostic.columnNumber > 0)

                val consoleMessage = awaitConsoleMessage("TS2322")
                assertTrue(consoleMessage.endsWith(failure.consoleMessage))
                assertFalse(consoleMessage.contains(TypeScriptDiagnosticsException::class.java.name))
                val link = StackFrameParser.parse(consoleMessage).single()
                assertEquals(source.name, link.sourceName)
                assertEquals(diagnostic.lineNumber!! - 1, link.lineIndex)
                assertEquals(diagnostic.columnNumber!! - 1, link.columnNumber)
                assertTrue(
                    StackFrameTargetResolver.matchesCurrentFile(
                        link.sourceName,
                        source.absolutePath,
                    ),
                )
                assertEquals(null, System.getProperty(markerKey))
                assertEquals(null, terminal.execution.engine)
            }
        } finally {
            console.clear()
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

    private fun withGestureTarget(block: (GestureTarget) -> Unit) {
        ensureAccessibilityServiceStarted("real gesture target")
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.waitForIdleSync()
        assertAccessibilityServiceOperational("real gesture target")

        val clicked = CountDownLatch(1)
        val swiped = CountDownLatch(1)
        lateinit var overlay: FrameLayout
        lateinit var clickTarget: Button
        lateinit var swipeTarget: View
        instrumentation.runOnMainSync {
            fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

            overlay = FrameLayout(activity).apply {
                background = ColorDrawable(Color.WHITE)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            clickTarget = Button(activity).apply {
                text = "TypeScript click target"
                setOnClickListener { clicked.countDown() }
            }
            overlay.addView(
                clickTarget,
                FrameLayout.LayoutParams(dp(280), dp(72), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = dp(96)
                },
            )

            var downY = Float.NaN
            swipeTarget = View(activity).apply {
                background = ColorDrawable(Color.rgb(32, 96, 192))
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> downY = event.rawY
                        MotionEvent.ACTION_UP -> {
                            if (!downY.isNaN() && kotlin.math.abs(event.rawY - downY) >= dp(80)) {
                                swiped.countDown()
                            }
                            view.performClick()
                        }
                    }
                    true
                }
            }
            overlay.addView(
                swipeTarget,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360), Gravity.BOTTOM).apply {
                    leftMargin = dp(48)
                    rightMargin = dp(48)
                    bottomMargin = dp(96)
                },
            )
            (activity.window.decorView as ViewGroup).addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250L)

        try {
            val target = IntArray(6)
            instrumentation.runOnMainSync {
                val clickLocation = IntArray(2)
                clickTarget.getLocationOnScreen(clickLocation)
                target[0] = clickLocation[0] + clickTarget.width / 2
                target[1] = clickLocation[1] + clickTarget.height / 2

                val swipeLocation = IntArray(2)
                swipeTarget.getLocationOnScreen(swipeLocation)
                target[2] = swipeLocation[0] + swipeTarget.width / 2
                target[3] = swipeLocation[1] + swipeTarget.height * 3 / 4
                target[4] = swipeLocation[0] + swipeTarget.width / 2
                target[5] = swipeLocation[1] + swipeTarget.height / 4
            }
            assertTrue("Click target was not laid out", target[0] > 0 && target[1] > 0)
            assertTrue("Swipe target was not laid out", target[2] > 0 && target[3] > target[5])
            block(
                GestureTarget(
                    clickX = target[0],
                    clickY = target[1],
                    swipeStartX = target[2],
                    swipeStartY = target[3],
                    swipeEndX = target[4],
                    swipeEndY = target[5],
                    clicked = clicked,
                    swiped = swiped,
                ),
            )
        } finally {
            instrumentation.runOnMainSync {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }

    private fun withMediaProjectionConsent(
        consent: MediaProjectionConsent,
        block: () -> Unit,
    ) {
        withAccessibilityAutomation(
            description = "media-projection ${consent.name.lowercase()}",
            action = {
                val initialButtonId = when (consent) {
                    MediaProjectionConsent.ALLOW -> POSITIVE_BUTTON_ID
                    MediaProjectionConsent.DENY -> NEGATIVE_BUTTON_ID
                }
                waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { node ->
                    node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                        node.viewIdResourceName == initialButtonId
                }

                if (consent == MediaProjectionConsent.ALLOW) {
                    findAccessibilityNode { node ->
                        node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                            node.viewIdResourceName in SCREEN_SHARE_MODE_IDS
                    }?.let { spinner ->
                        assertTrue("Could not open the screen-share mode selector", clickNodeOrAncestor(spinner))
                        val entireScreen = waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { node ->
                            node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                                node.viewIdResourceName == SPINNER_ITEM_ID &&
                                node.text?.toString().isEntireScreenLabel()
                        }
                        assertTrue("Could not select entire-screen projection", clickNodeOrAncestor(entireScreen))
                    }
                }

                val terminalButton = waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { node ->
                    node.packageName?.toString() == SYSTEM_UI_PACKAGE &&
                        node.viewIdResourceName == initialButtonId
                }
                assertTrue("Could not click the media-projection consent button", clickNodeOrAncestor(terminalButton))
            },
            block = block,
        )
    }

    private fun withAccessibilityNodeClick(text: String, block: () -> Unit) {
        withAccessibilityAutomation(
            description = "node text '$text'",
            action = {
                val node = waitForAccessibilityNode(ACCESSIBILITY_TIMEOUT_MILLIS) { candidate ->
                    candidate.packageName?.toString() == context.packageName &&
                        candidate.text?.toString()?.equals(text, ignoreCase = true) == true
                }
                assertTrue("Could not click accessibility node '$text'", clickNodeOrAncestor(node))
            },
            block = block,
        )
    }

    private fun withAccessibilityAutomation(
        description: String,
        action: () -> Unit,
        block: () -> Unit,
    ) {
        warmUpAccessibilityService(description)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val thread = Thread({
            try {
                action()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }, "typescript-accessibility-${System.nanoTime()}").apply {
            isDaemon = true
            start()
        }
        try {
            block()
            assertTrue(
                "Accessibility automation did not complete: $description",
                completed.await(ACCESSIBILITY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            failure.get()?.let { error ->
                throw AssertionError("Accessibility automation failed: $description", error)
            }
        } finally {
            thread.interrupt()
            thread.join(2_000L)
        }
    }

    private fun warmUpAccessibilityService(description: String) {
        ensureAccessibilityServiceStarted(description)
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        instrumentation.waitForIdleSync()
        try {
            assertAccessibilityServiceOperational(description)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
            instrumentation.waitForIdleSync()
        }
    }

    private fun assertAccessibilityServiceOperational(description: String) {
        assertTrue(
            "AutoJs6 accessibility service did not start for $description",
            AccessibilityService.waitForStarted(ACCESSIBILITY_TIMEOUT_MILLIS),
        )
        assertTrue(
            "AutoJs6 accessibility service did not become operational for $description",
            AccessibilityService.waitForOperational(ACCESSIBILITY_TIMEOUT_MILLIS),
        )
    }

    private fun ensureAccessibilityServiceStarted(description: String) {
        if (AccessibilityService.hasInstance()) return

        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val enabledServices = services.split(':').filter(String::isNotBlank)
        assertTrue(
            "AutoJs6 accessibility service is not enabled for $description",
            AUTOJS_ACCESSIBILITY_SERVICE in enabledServices,
        )
        val servicesWithoutAutoJs = enabledServices
            .filterNot(AUTOJS_ACCESSIBILITY_SERVICE::equals)
            .joinToString(":")
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        var detached = false
        try {
            assertTrue(
                "Could not refresh the AutoJs6 accessibility service for $description",
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    servicesWithoutAutoJs,
                ),
            )
            detached = true
            SystemClock.sleep(ACCESSIBILITY_BINDING_REFRESH_MILLIS)
        } finally {
            if (detached) {
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    services,
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1,
                )
            }
            automation.dropShellPermissionIdentity()
        }
        assertTrue(
            "AutoJs6 accessibility service did not start for $description",
            AccessibilityService.waitForStarted(ACCESSIBILITY_TIMEOUT_MILLIS),
        )
    }

    private fun waitForAccessibilityNode(
        timeoutMillis: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            findAccessibilityNode(predicate)?.let { return it }
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        throw AssertionError("Accessibility node was not found within ${timeoutMillis}ms")
    }

    private fun findAccessibilityNode(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val service = AccessibilityService.instance ?: return null
        val roots = service.windows.mapNotNull { window -> window.root }.ifEmpty {
            listOfNotNull(service.rootInActiveWindow)
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach(queue::addLast)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            visited += 1
            if (predicate(node)) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        repeat(MAX_ACCESSIBILITY_ANCESTORS) {
            val current = candidate ?: return false
            if (current.isEnabled && current.isClickable &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            candidate = current.parent
        }
        return false
    }

    private fun String?.isEntireScreenLabel(): Boolean {
        val label = this?.trim().orEmpty()
        return label.equals("整个屏幕", ignoreCase = true) ||
            label.contains("entire screen", ignoreCase = true)
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        return predicate()
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

    private fun awaitConsoleMessage(fragment: String): String {
        val console = AutoJs.instance.globalConsole
        val deadline = SystemClock.uptimeMillis() + CONSOLE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            synchronized(console.logEntries) {
                console.logEntries.lastOrNull { entry -> fragment in entry.content }?.let { entry ->
                    return entry.content.toString()
                }
            }
            SystemClock.sleep(50)
        }
        fail("Console did not contain $fragment")
        error("Unreachable")
    }

    private data class ExecutionTerminal(
        val execution: ScriptExecution,
        val succeeded: AtomicBoolean,
        val failure: AtomicReference<Throwable>,
    )

    private data class GestureTarget(
        val clickX: Int,
        val clickY: Int,
        val swipeStartX: Int,
        val swipeStartY: Int,
        val swipeEndX: Int,
        val swipeEndY: Int,
        val clicked: CountDownLatch,
        val swiped: CountDownLatch,
    )

    private enum class MediaProjectionConsent {
        ALLOW,
        DENY,
    }

    private companion object {
        const val EXECUTION_TIMEOUT_SECONDS = 90L
        const val CONSOLE_TIMEOUT_MILLIS = 5_000L
        const val HTTP_TIMEOUT_MILLIS = 10_000
        const val ACCESSIBILITY_TIMEOUT_MILLIS = 30_000L
        const val ACCESSIBILITY_POLL_MILLIS = 100L
        const val ACCESSIBILITY_BINDING_REFRESH_MILLIS = 250L
        const val GESTURE_TIMEOUT_SECONDS = 5L
        const val SERVICE_TEARDOWN_TIMEOUT_MILLIS = 10_000L
        const val MAX_ACCESSIBILITY_NODES = 512
        const val MAX_ACCESSIBILITY_ANCESTORS = 4
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val AUTOJS_ACCESSIBILITY_SERVICE =
            "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        const val POSITIVE_BUTTON_ID = "android:id/button1"
        const val NEGATIVE_BUTTON_ID = "android:id/button2"
        const val SPINNER_ITEM_ID = "android:id/text1"
        const val UI_BUTTON_TEXT = "Finish TypeScript UI"
        val SCREEN_SHARE_MODE_IDS = setOf(
            "com.android.systemui:id/screen_share_mode_spinner",
            "com.android.systemui:id/screen_share_mode_options",
        )
        val CAPTURE_MARKER = Regex("[1-9][0-9]*x[1-9][0-9]*:-?[0-9]+")
    }
}
