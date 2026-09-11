package org.autojs.autojs.engine

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs.core.plugin.python.PythonHostNoticePolicy
import org.autojs.autojs.core.plugin.python.PythonRuntimeExecutionResult
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectLaunchPolicy
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

/**
 * Opt-in U1-R2 acceptance through the public Host engine and the exact real CPython plugin.
 *
 * The test deliberately keeps stdout diagnostic-only. The explicit JSON value remains available
 * through the Host-owned result surface and is printed with a typed prefix. Verified artifacts are
 * also atomically published below the project root and their absolute paths are printed.
 */
class PythonU1R2AcceptanceInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService
    private val console
        get() = AutoJs.instance.globalConsole
    private var serviceStateOwned = false

    @Before
    fun requireExplicitAcceptanceAndRealPlugin() {
        assumeTrue(
            "U1-R2 public acceptance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assumeTrue(
            "U1-R2 acceptance requires the normal AutoJs6 host rather than an INRT package",
            !BuildConfig.isInrt,
        )
        assertTrue("U1-R2 acceptance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("U1-R2 acceptance requires the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real Python Runtime plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        serviceStateOwned = true
        service.stopAll()
        awaitNoRegisteredEngines("before U1-R2 public acceptance")
    }

    @After
    fun settleAfter() {
        if (!serviceStateOwned) return
        service.stopAll()
        awaitNoRegisteredEngines("after U1-R2 public acceptance")
    }

    @Test
    fun publicModuleEngineReturnsExplicitJsonAndExactArtifactWithoutInferringStdout() {
        val projectRoot = freshProjectRoot()
        val token = "U1R2_PUBLIC_${UUID.randomUUID()}"
        val createdEngineClasses = CopyOnWriteArrayList<Class<*>>()
        val lifecycle = object : ScriptEngineManager.EngineLifecycleCallback {
            override fun onEngineCreate(engine: ScriptEngine<*>) {
                createdEngineClasses += engine.javaClass
            }

            override fun onEngineRemove(engine: ScriptEngine<*>) = Unit
        }

        service.registerEngineLifecycleCallback(lifecycle)
        try {
            writeProject(projectRoot, token)
            val launch = admittedProjectLaunch(projectRoot)
            val config = launch.createExecutionConfig()
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                val submitted = service.execute(launch.source, config)
                assertTrue(
                    "U1-R2 module execution did not reach the unified start callback",
                    pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                val execution = requireNotNull(pending.execution.get()) {
                    "U1-R2 module execution started without exposing ScriptExecution"
                }
                assertEquals(submitted.id, execution.id)
                assertEquals("module", execution.config.getArgument(ENTRY_MODE_ARGUMENT))
                if (!pending.completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    execution.engine.forceStop()
                    throw AssertionError("U1-R2 module execution did not reach a Host terminal callback")
                }
                awaitEngineRemoved(execution.id, "U1-R2 public module acceptance")

                assertEquals(1, pending.startCount.get())
                assertEquals(1, pending.successCount.get())
                assertEquals(0, pending.exceptionCount.get())
                assertEquals(null, pending.error.get())
                assertEquals(PythonProjectSource::class.java, pending.sourceClass.get())
                assertEquals(PythonPluginScriptEngine::class.java, pending.engineClass.get())

                val result = pending.result.get() as? PythonRuntimeExecutionResult
                assertNotNull("Public Python engine did not return the Host-owned result type", result)
                requireNotNull(result)
                val expectedStdout = "$token\n$STDOUT_JSON_LOOKALIKE"
                assertEquals(0, result.terminal.exitCode)
                assertEquals(expectedStdout.toByteArray(Charsets.UTF_8).size.toLong(), result.terminal.stdoutBytes)
                assertEquals(EXPECTED_STDERR.toByteArray(Charsets.UTF_8).size.toLong(), result.terminal.stderrBytes)
                assertEquals(EXPECTED_STRUCTURED_JSON, result.structuredJson)
                assertFalse(
                    "A JSON-looking stdout line was incorrectly inferred as the explicit result",
                    result.structuredJson == STDOUT_JSON_LOOKALIKE.trim(),
                )
                assertEquals(1, result.outputArtifacts.size)
                val artifact = result.outputArtifacts.single()
                assertEquals(ARTIFACT_PATH, artifact.path)
                assertEquals(ARTIFACT_BYTES.size.toLong(), artifact.sizeBytes)
                assertEquals(PythonSha256.digest(ARTIFACT_BYTES), artifact.sha256)
                assertArrayEquals(ARTIFACT_BYTES, artifact.toByteArray())
                val mutatedCopy = artifact.toByteArray().also { bytes -> if (bytes.isNotEmpty()) bytes[0] = 0 }
                assertFalse("Mutating a returned artifact copy unexpectedly changed no bytes", mutatedCopy.contentEquals(ARTIFACT_BYTES))
                assertArrayEquals(
                    "Host-owned artifact bytes must be defensive copies",
                    ARTIFACT_BYTES,
                    artifact.toByteArray(),
                )
                val publishedDirectory = File(
                    projectRoot.canonicalFile,
                    "$PUBLISHED_ARTIFACT_ROOT/execution-${execution.id}-${result.terminal.requestId}",
                )
                val publishedArtifact = File(publishedDirectory, ARTIFACT_PATH)
                assertTrue("Published artifact directory is not user-visible", publishedDirectory.isDirectory)
                assertTrue("Published artifact is not a regular file", publishedArtifact.isFile)
                assertArrayEquals(
                    "Published artifact bytes changed after Host verification",
                    ARTIFACT_BYTES,
                    publishedArtifact.readBytes(),
                )
                assertTrue(
                    "Artifact publication left a staging directory",
                    File(projectRoot, PUBLISHED_ARTIFACT_ROOT).listFiles().orEmpty().none { candidate ->
                        candidate.name.startsWith(".")
                    },
                )

                assertConsoleSince(checkpoint, token)
                assertConsoleSince(checkpoint, STDOUT_JSON_LOOKALIKE.trim())
                assertConsoleSince(checkpoint, EXPECTED_STDERR.trim())
                assertConsoleSince(checkpoint, "[result] $EXPECTED_STRUCTURED_JSON")
                assertConsoleSince(checkpoint, "[artifact] ${publishedArtifact.absolutePath}")
                assertPrivateTransportSnapshotsRemoved()
                assertEquals(listOf(PythonPluginScriptEngine::class.java), createdEngineClasses)
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            service.unregisterEngineLifecycleCallback(lifecycle)
            service.stopAll()
            awaitNoRegisteredEngines("during U1-R2 public acceptance cleanup")
            check(projectRoot.deleteRecursively() || !projectRoot.exists()) {
                "Unable to remove U1-R2 acceptance directory: $projectRoot"
            }
        }
    }

    @Test
    fun projectManifestFileStdinFeedsSysStdinWithoutInteractivePrompt() {
        val projectRoot = freshProjectRoot()
        val marker = "M2_STDIN_OK_${UUID.randomUUID()}"
        val expectedStdin = "first line\n雪山输入\nlast line\n"
        val expectedBytes = expectedStdin.toByteArray(Charsets.UTF_8)
        val encodedExpected = Base64.encodeToString(expectedBytes, Base64.NO_WRAP)
        try {
            writeStdinProject(
                projectRoot,
                expectedBytes,
                """
                import base64
                import sys

                expected = base64.b64decode("$encodedExpected")
                actual = sys.stdin.read()
                assert actual.encode("utf-8") == expected
                assert sys.stdin.read() == ""
                print("$marker:" + base64.b64encode(actual.encode("utf-8")).decode("ascii"), flush=True)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val config = launch.createExecutionConfig()
            val configuredStdin = config.getArgument(STDIN_SNAPSHOT_ARGUMENT) as? ByteArray
            assertNotNull("project.json stdin file did not reach ExecutionConfig", configuredStdin)
            assertArrayEquals(expectedBytes, configuredStdin)

            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, config)
                val execution = awaitStarted(pending, "M2 project stdin execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M2 project stdin execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M2 project stdin execution")
                assertEquals(0, result.terminal.exitCode)
                assertConsoleSince(checkpoint, "$marker:$encodedExpected")
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M2 project stdin")
        }
    }

    @Test
    fun firstLowRiskHostCapabilitiesRoundTripThroughProtocol15Broker() {
        val projectRoot = freshProjectRoot()
        val marker = "M3_BROKER_OK_${UUID.randomUUID()}"
        val activity = launchForegroundActivity()
        val clipboard = requireNotNull(context.getSystemService(ClipboardManager::class.java))
        val originalClip = clipboard.primaryClip
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import app, clip, result, toast
                from autojs6.errors import HostCapabilityError

                marker = "$marker"
                toast(marker)
                clip.set(marker)
                observed = clip.get()
                launch_missing = app.launch("org.autojs6.m3.missing")
                launch_app_missing = app.launch_app("AutoJs6 M3 Missing Application")

                try:
                    app.open_url("ftp://invalid-for-m3-acceptance.example")
                    open_url_code = "NO_ERROR"
                except HostCapabilityError as error:
                    open_url_code = error.code

                result.set({
                    "clip": observed,
                    "launchAppMissing": launch_app_missing,
                    "launchMissing": launch_missing,
                    "openUrlCode": open_url_code,
                    "toast": True,
                })
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 host capability broker execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 host capability broker execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 host capability broker execution")
                assertEquals(
                    "{\"clip\":\"$marker\",\"launchAppMissing\":false," +
                        "\"launchMissing\":false,\"openUrlCode\":\"INVALID_ARGUMENT\",\"toast\":true}",
                    result.structuredJson,
                )
                assertEquals(
                    marker,
                    clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString(),
                )
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            if (originalClip == null) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(originalClip)
            }
            instrumentation.runOnMainSync { activity.finish() }
            cleanProjectRoot(projectRoot, "M3 host capability broker")
        }
    }

    @Test
    fun remainingFirstBatchHostCapabilitiesRoundTripThroughProtocol15Broker() {
        val projectRoot = freshProjectRoot()
        val marker = "M3_BROKER_REST_OK_${UUID.randomUUID()}"
        val notificationExpected = canPostPythonNotice()
        val checkpoint = consoleCheckpoint()
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import console, device, notice, result
                from autojs6.errors import HostCapabilityError

                marker = "$marker"
                current = device.info()
                console.log(marker + ":log")
                console.warn(marker + ":warn")
                console.error(marker + ":error")
                try:
                    notice(marker)
                    notice_status = "posted"
                except HostCapabilityError as error:
                    notice_status = error.code

                result.set({"device": current, "notice": notice_status})
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 remaining Host capabilities execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 remaining Host capabilities execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 remaining Host capabilities execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                val device = structured.getJSONObject("device")
                assertEquals("autojs6-python-device-info-v1", device.getString("schema"))
                val battery = device.getJSONObject("battery")
                assertTrue(battery.getDouble("percent") in 0.0..100.0)
                battery.getBoolean("charging")
                val screen = device.getJSONObject("screen")
                screen.getBoolean("on")
                assertTrue(screen.getInt("brightness") >= -1)
                val volume = device.getJSONObject("volume")
                listOf("music", "notification", "alarm").forEach { stream ->
                    val level = volume.getJSONObject(stream)
                    val current = level.getInt("current")
                    val maximum = level.getInt("max")
                    assertTrue("$stream volume is negative", current >= 0)
                    assertTrue("$stream max volume is negative", maximum >= 0)
                    assertTrue("$stream volume exceeds its maximum", current <= maximum)
                }

                assertConsoleLevelSince(checkpoint, "$marker:log", Log.DEBUG)
                assertConsoleLevelSince(checkpoint, "$marker:warn", Log.WARN)
                assertConsoleLevelSince(checkpoint, "$marker:error", Log.ERROR)

                if (notificationExpected) {
                    assertEquals("posted", structured.getString("notice"))
                    val posted = awaitPythonNotice(marker)
                    assertEquals(
                        PythonHostNoticePolicy.tagForExecution(result.terminal.requestId.toString()),
                        posted.tag,
                    )
                    assertEquals(PythonHostNoticePolicy.NOTIFICATION_ID, posted.id)
                } else {
                    assertEquals("PERMISSION_DENIED", structured.getString("notice"))
                }
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cancelPythonNotices(marker)
            cleanProjectRoot(projectRoot, "M3 remaining Host capabilities")
        }
    }

    @Test
    fun hostFilesRoundTripThroughProtocol15BrokerWithoutEscapingProjectRoot() {
        val projectRoot = freshProjectRoot()
        val marker = "M3_HOST_FILES_OK_${UUID.randomUUID()}"
        val seedFile = File(projectRoot, "host-seed.txt")
        val outputFile = File(projectRoot, "host-output.txt")
        try {
            writeSimpleProject(
                projectRoot,
                """
                import os

                from autojs6 import files, result
                from autojs6.errors import HostCapabilityError

                seed = files.read_text("host-seed.txt")
                existed_before = files.exists("host-output.txt")
                files.write_text("host-output.txt", "$marker:" + seed)

                try:
                    files.read_text("missing.txt")
                    missing_code = "NO_ERROR"
                except HostCapabilityError as error:
                    missing_code = error.code

                try:
                    files.exists("../outside.txt")
                    unsafe_path = "NO_ERROR"
                except ValueError:
                    unsafe_path = "VALUE_ERROR"

                entries = files.list()
                result.set({
                    "content": files.read_text("host-output.txt"),
                    "existedAfter": files.exists("host-output.txt"),
                    "existedBefore": existed_before,
                    "isDirectory": files.is_dir("."),
                    "isFile": files.is_file("host-output.txt"),
                    "listed": "host-output.txt" in entries,
                    "missingCode": missing_code,
                    "unsafePath": unsafe_path,
                    "workspaceSeesWrite": os.path.exists("host-output.txt"),
                })
                """.trimIndent() + "\n",
            )
            seedFile.writeText("seed", Charsets.UTF_8)
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 Host files execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 Host files execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 Host files execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("$marker:seed", structured.getString("content"))
                assertFalse(structured.getBoolean("existedBefore"))
                assertTrue(structured.getBoolean("existedAfter"))
                assertTrue(structured.getBoolean("isDirectory"))
                assertTrue(structured.getBoolean("isFile"))
                assertTrue(structured.getBoolean("listed"))
                assertEquals("PATH_NOT_FOUND", structured.getString("missingCode"))
                assertEquals("VALUE_ERROR", structured.getString("unsafePath"))
                assertFalse(
                    "Live Host write unexpectedly mutated the frozen CPython workspace",
                    structured.getBoolean("workspaceSeesWrite"),
                )
                assertTrue("Host file write did not create a regular file", outputFile.isFile)
                assertEquals("$marker:seed", outputFile.readText(Charsets.UTF_8))
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 Host files")
        }
    }

    @Test
    fun boundedAutomatorActionsRoundTripThroughPublicPythonEngine() {
        val projectRoot = freshProjectRoot()
        val activity = launchAccessibilitySmokeActivity()
        try {
            ensureAccessibilityServiceStarted("M3 automator acceptance")
            instrumentation.runOnMainSync {
                activity.window.decorView.sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                )
            }
            assumeTrue(
                "The AutoJs6 accessibility service connected but did not become operational",
                AutoJsAccessibilityService.waitForOperational(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
            )
            val buttonCenter = AtomicReference<IntArray>()
            val swipeCoordinates = AtomicReference<Array<IntArray>>()
            instrumentation.runOnMainSync {
                buttonCenter.set(activity.buttonCenterOnScreen())
                swipeCoordinates.set(activity.swipeCoordinatesOnScreen())
            }
            val button = requireNotNull(buttonCenter.get())
            val swipe = requireNotNull(swipeCoordinates.get())
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import automator, result

                actions = {
                    "click": automator.click(${button[0]}, ${button[1]}),
                    "press": automator.press(${button[0]}, ${button[1]}, 100),
                    "longClick": automator.long_click(${button[0]}, ${button[1]}),
                    "swipe": automator.swipe(
                        ${swipe[0][0]}, ${swipe[0][1]},
                        ${swipe[1][0]}, ${swipe[1][1]},
                        300,
                    ),
                    "back": automator.back(),
                    "home": automator.home(),
                }
                result.set(actions)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 automator execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 automator execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 automator execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                listOf("click", "press", "longClick", "swipe", "back", "home").forEach { action ->
                    assertTrue("M3 automator action '$action' returned false", structured.getBoolean(action))
                }
                assertTrue(
                    "Timed out waiting for Python click/press on the controlled button",
                    NodeAccessibilitySmokeActivity.awaitClick(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
                )
                assertTrue(
                    "Timed out waiting for Python long-click on the controlled button",
                    NodeAccessibilitySmokeActivity.awaitLongClick(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
                )
                assertTrue(
                    "Timed out waiting for Python swipe on the controlled target",
                    NodeAccessibilitySmokeActivity.awaitSwipe(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
                )
                assertEquals(2, NodeAccessibilitySmokeActivity.clickedCount())
                assertEquals(1, NodeAccessibilitySmokeActivity.longClickedCount())
                assertEquals(1, NodeAccessibilitySmokeActivity.swipedCount())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            instrumentation.runOnMainSync {
                if (!activity.isFinishing) activity.finish()
            }
            restoreHostForeground()
            cleanProjectRoot(projectRoot, "M3 automator")
        }
    }

    @Test
    fun boundedSelectorRoundTripsSnapshotFindClickSetTextAndStaleNode() {
        val projectRoot = freshProjectRoot()
        val activity = launchAccessibilitySmokeActivity()
        try {
            ensureAccessibilityServiceStarted("M3 selector acceptance")
            instrumentation.runOnMainSync {
                activity.window.decorView.sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                )
            }
            assumeTrue(
                "The AutoJs6 accessibility service connected but did not become operational",
                AutoJsAccessibilityService.waitForOperational(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
            )
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import result, selector
                from autojs6.errors import HostCapabilityError

                tree = selector.snapshot(max_nodes=128, max_depth=16)
                tree_button = next(node for node in tree["nodes"] if node["text"] == "${NodeAccessibilitySmokeActivity.BUTTON_TEXT}")
                tree_input = next(node for node in tree["nodes"] if node["description"] == "${NodeAccessibilitySmokeActivity.SELECTOR_INPUT_DESC}")
                button = selector.find(text="${NodeAccessibilitySmokeActivity.BUTTON_TEXT}", clickable=True)
                editor = selector.find(
                    description="${NodeAccessibilitySmokeActivity.SELECTOR_INPUT_DESC}",
                    editable=True,
                )
                missing = selector.find(text="AutoJs6 selector definitely missing") is None
                clicked = selector.click(button)
                text_set = selector.set_text(editor, "${NodeAccessibilitySmokeActivity.SELECTOR_INPUT_UPDATED_TEXT}")

                selector.snapshot(max_nodes=1, max_depth=0)
                try:
                    selector.click(button)
                    stale_code = "NO_ERROR"
                    stale_message = ""
                except HostCapabilityError as error:
                    stale_code = error.code
                    stale_message = str(error)

                result.set({
                    "schema": tree["schema"],
                    "truncated": tree["truncated"],
                    "nodeCount": len(tree["nodes"]),
                    "buttonResourceId": tree_button["resourceId"],
                    "inputResourceId": tree_input["resourceId"],
                    "clicked": clicked,
                    "textSet": text_set,
                    "missing": missing,
                    "staleCode": stale_code,
                    "staleMessage": stale_message,
                })
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 selector execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 selector execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 selector execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("autojs6-python-ui-tree-v1", structured.getString("schema"))
                assertFalse(structured.getBoolean("truncated"))
                assertTrue(structured.getInt("nodeCount") >= 4)
                assertEquals(
                    "$HOST_PACKAGE:id/python_selector_button",
                    structured.getString("buttonResourceId"),
                )
                assertEquals(
                    "$HOST_PACKAGE:id/python_selector_input",
                    structured.getString("inputResourceId"),
                )
                assertTrue(structured.getBoolean("clicked"))
                assertTrue(structured.getBoolean("textSet"))
                assertTrue(structured.getBoolean("missing"))
                assertEquals("STALE_NODE", structured.getString("staleCode"))
                assertEquals(
                    "STALE_NODE: Host selector node is stale or no longer retained",
                    structured.getString("staleMessage"),
                )
                assertTrue(
                    "Timed out waiting for the Python selector click",
                    NodeAccessibilitySmokeActivity.awaitClick(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
                )
                assertTrue(
                    "Timed out waiting for the Python selector text action",
                    NodeAccessibilitySmokeActivity.awaitInputText(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
                )
                assertEquals(1, NodeAccessibilitySmokeActivity.clickedCount())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            instrumentation.runOnMainSync {
                if (!activity.isFinishing) activity.finish()
            }
            restoreHostForeground()
            cleanProjectRoot(projectRoot, "M3 selector")
        }
    }

    @Test
    fun boundedScreenCapturePublishesVerifiedPngArtifactThroughPublicPythonEngine() {
        assumeTrue(
            "Python accessibility screen capture requires Android 11 or newer",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        val projectRoot = freshProjectRoot()
        val activity = launchAccessibilitySmokeActivity()
        try {
            ensureAccessibilityServiceStarted("M3 screen capture acceptance")
            instrumentation.runOnMainSync {
                activity.window.decorView.sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                )
            }
            assumeTrue(
                "The AutoJs6 accessibility service connected but did not become operational",
                AutoJsAccessibilityService.waitForOperational(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
            )
            val captureTarget = AtomicReference<IntArray>()
            instrumentation.runOnMainSync {
                captureTarget.set(activity.captureTargetCenterOnScreen())
            }
            writeSimpleProject(
                projectRoot,
                """
                import hashlib
                import os

                from autojs6 import images, result

                target = images.capture_screen(
                    format="png",
                    quality=100,
                    path="$SCREEN_CAPTURE_ARTIFACT_PATH",
                )
                with open(target, "rb") as stream:
                    payload = stream.read()
                result.set({
                    "artifactPath": "$SCREEN_CAPTURE_ARTIFACT_PATH",
                    "byteLength": len(payload),
                    "privatePathAbsolute": os.path.isabs(target),
                    "pngSignature": payload[:8].hex(),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                })
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 screen capture execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 screen capture execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 screen capture execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals(
                    SCREEN_CAPTURE_ARTIFACT_PATH,
                    structured.getString("artifactPath"),
                )
                assertTrue(structured.getBoolean("privatePathAbsolute"))
                assertEquals("89504e470d0a1a0a", structured.getString("pngSignature"))

                val artifact = result.outputArtifacts.single()
                val bytes = artifact.toByteArray()
                assertEquals(SCREEN_CAPTURE_ARTIFACT_PATH, artifact.path)
                assertEquals(bytes.size.toLong(), artifact.sizeBytes)
                assertTrue("Python screen capture is empty", bytes.isNotEmpty())
                assertTrue(
                    "Python screen capture exceeds the 4 MiB contract",
                    bytes.size <= SCREEN_CAPTURE_MAX_BYTES,
                )
                assertEquals(bytes.size, structured.getInt("byteLength"))
                assertEquals(PythonSha256.digest(bytes), artifact.sha256)
                assertEquals(artifact.sha256.toHexString(), structured.getString("sha256"))

                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                    "Python screen capture artifact is not a decodable PNG"
                }
                try {
                    val target = requireNotNull(captureTarget.get())
                    assertTrue(target[0] in 0 until bitmap.width)
                    assertTrue(target[1] in 0 until bitmap.height)
                    val actual = bitmap.getPixel(target[0], target[1])
                    assertTrue(
                        "Captured target pixel ${colorHex(actual)} differs from ${colorHex(NodeAccessibilitySmokeActivity.CAPTURE_TARGET_COLOR)}",
                        colorsNear(
                            NodeAccessibilitySmokeActivity.CAPTURE_TARGET_COLOR,
                            actual,
                            SCREEN_CAPTURE_COLOR_TOLERANCE,
                        ),
                    )
                } finally {
                    bitmap.recycle()
                }
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            instrumentation.runOnMainSync {
                if (!activity.isFinishing) activity.finish()
            }
            restoreHostForeground()
            cleanProjectRoot(projectRoot, "M3 screen capture")
        }
    }

    @Test
    fun boundedScreenColorSearchFindsControlledTargetThroughPublicPythonEngine() {
        assumeTrue(
            "Python accessibility screen color search requires Android 11 or newer",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        val projectRoot = freshProjectRoot()
        val activity = launchAccessibilitySmokeActivity()
        try {
            ensureAccessibilityServiceStarted("M3 screen color acceptance")
            instrumentation.runOnMainSync {
                activity.window.decorView.sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                )
            }
            assumeTrue(
                "The AutoJs6 accessibility service connected but did not become operational",
                AutoJsAccessibilityService.waitForOperational(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
            )
            val captureBounds = AtomicReference<IntArray>()
            instrumentation.runOnMainSync {
                captureBounds.set(activity.captureTargetBoundsOnScreen())
            }
            val bounds = requireNotNull(captureBounds.get())
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import images, result

                match = images.find_color(
                    ${NodeAccessibilitySmokeActivity.CAPTURE_TARGET_COLOR and 0x00ffffff},
                    region=(${bounds[0]}, ${bounds[1]}, ${bounds[2]}, ${bounds[3]}),
                    threshold=$SCREEN_CAPTURE_COLOR_TOLERANCE,
                )
                result.set({"match": None if match is None else list(match)})
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 screen color execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 screen color execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 screen color execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertFalse("Controlled screen color was not found", structured.isNull("match"))
                val match = structured.getJSONArray("match")
                assertEquals(2, match.length())
                assertTrue(match.getInt(0) in bounds[0] until bounds[0] + bounds[2])
                assertTrue(match.getInt(1) in bounds[1] until bounds[1] + bounds[3])
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            instrumentation.runOnMainSync {
                if (!activity.isFinishing) activity.finish()
            }
            restoreHostForeground()
            cleanProjectRoot(projectRoot, "M3 screen color")
        }
    }

    @Test
    fun boundedScreenTemplateSearchFindsControlledTargetThroughPublicPythonEngine() {
        assumeTrue(
            "Python accessibility screen template search requires Android 11 or newer",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        val projectRoot = freshProjectRoot()
        val activity = launchAccessibilitySmokeActivity()
        try {
            ensureAccessibilityServiceStarted("M3 screen template acceptance")
            instrumentation.runOnMainSync {
                activity.window.decorView.sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                )
            }
            assumeTrue(
                "The AutoJs6 accessibility service connected but did not become operational",
                AutoJsAccessibilityService.waitForOperational(AUTOMATOR_EVENT_TIMEOUT_MILLIS),
            )
            val captureBounds = AtomicReference<IntArray>()
            instrumentation.runOnMainSync {
                captureBounds.set(activity.captureTargetBoundsOnScreen())
            }
            val bounds = requireNotNull(captureBounds.get())
            writeSimpleProject(
                projectRoot,
                """
                import base64

                from autojs6 import images, result

                target = base64.b64decode("$SCREEN_TEMPLATE_TARGET_PNG_BASE64")
                absent = base64.b64decode("$SCREEN_TEMPLATE_ABSENT_PNG_BASE64")
                region = (${bounds[0]}, ${bounds[1]}, ${bounds[2]}, ${bounds[3]})
                match = images.find_image(target, region=region, threshold=$SCREEN_CAPTURE_COLOR_TOLERANCE)
                miss = images.find_image(absent, region=region, threshold=0)
                result.set({
                    "match": None if match is None else list(match),
                    "miss": None if miss is None else list(miss),
                    "templateBytes": len(target),
                })
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 screen template execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 screen template execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 screen template execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals(SCREEN_TEMPLATE_TARGET_BYTES, structured.getInt("templateBytes"))
                assertFalse("Controlled screen template was not found", structured.isNull("match"))
                val match = structured.getJSONArray("match")
                assertEquals(2, match.length())
                assertTrue(
                    match.getInt(0) in bounds[0]..(bounds[0] + bounds[2] - SCREEN_TEMPLATE_SIDE),
                )
                assertTrue(
                    match.getInt(1) in bounds[1]..(bounds[1] + bounds[3] - SCREEN_TEMPLATE_SIDE),
                )
                assertTrue("Absent screen template unexpectedly matched", structured.isNull("miss"))
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            instrumentation.runOnMainSync {
                if (!activity.isFinishing) activity.finish()
            }
            restoreHostForeground()
            cleanProjectRoot(projectRoot, "M3 screen template")
        }
    }

    @Test
    fun boundedOcrRecognizesControlledImageThroughConfiguredHostEngine() {
        assumeTrue(
            "This acceptance requires an installed Host OCR service",
            hasInstalledOcrService(),
        )
        val projectRoot = freshProjectRoot()
        try {
            val fixture = createOcrFixtureBase64()
            writeSimpleProject(
                projectRoot,
                """
                import base64

                from autojs6 import ocr, result

                image = base64.b64decode("$fixture")
                lines = ocr.recognize(image)
                result.set({"lines": list(lines), "type": type(lines).__name__})
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 OCR execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 OCR execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 OCR execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("tuple", structured.getString("type"))
                val lines = structured.getJSONArray("lines")
                val recognized = buildString {
                    repeat(lines.length()) { index ->
                        if (isNotEmpty()) append(' ')
                        append(lines.getString(index))
                    }
                }.uppercase(Locale.ROOT)
                assertTrue("Controlled OCR fixture did not contain AUTOJS6: $recognized", "AUTOJS6" in recognized)
                assertTrue("Controlled OCR fixture did not contain OCR: $recognized", "OCR" in recognized)
                assertTrue("Controlled OCR fixture did not contain 2026: $recognized", "2026" in recognized)
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 OCR")
        }
    }

    @Test
    fun ocrFailsClosedWithoutAHostEngineThroughPublicPythonEngine() {
        assumeFalse(
            "This acceptance requires all Host OCR services to remain unavailable",
            hasInstalledOcrService(),
        )
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                import base64

                from autojs6 import ocr, result
                from autojs6.errors import CapabilityUnavailableError

                image = base64.b64decode("$SCREEN_TEMPLATE_TARGET_PNG_BASE64")
                try:
                    ocr.recognize(image)
                    outcome = {"error": "NO_ERROR", "message": ""}
                except CapabilityUnavailableError as error:
                    outcome = {"error": type(error).__name__, "message": str(error)}
                result.set(outcome)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 unavailable OCR execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 unavailable OCR execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 unavailable OCR execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("CapabilityUnavailableError", structured.getString("error"))
                assertEquals(
                    "No enabled, authorized, and compatible Host OCR engine is available",
                    structured.getString("message"),
                )
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 unavailable OCR")
        }
    }

    @Test
    fun automatorFailsClosedWithoutAccessibilityThroughPublicPythonEngine() {
        assumeFalse(
            "This acceptance requires the AutoJs6 accessibility service to remain unavailable",
            AutoJsAccessibilityService.hasInstance(),
        )
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import automator, result
                from autojs6.errors import CapabilityUnavailableError

                try:
                    automator.click(1, 1)
                    outcome = {"error": "NO_ERROR", "message": ""}
                except CapabilityUnavailableError as error:
                    outcome = {"error": type(error).__name__, "message": str(error)}
                result.set(outcome)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 unavailable automator execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 unavailable automator execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 unavailable automator execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("CapabilityUnavailableError", structured.getString("error"))
                assertEquals(
                    "AutoJs6 accessibility service is unavailable",
                    structured.getString("message"),
                )
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 unavailable automator")
        }
    }

    @Test
    fun selectorFailsClosedWithoutAccessibilityThroughPublicPythonEngine() {
        assumeFalse(
            "This acceptance requires the AutoJs6 accessibility service to remain unavailable",
            AutoJsAccessibilityService.hasInstance(),
        )
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import result, selector
                from autojs6.errors import CapabilityUnavailableError

                try:
                    selector.snapshot(max_nodes=1, max_depth=0)
                    outcome = {"error": "NO_ERROR", "message": ""}
                except CapabilityUnavailableError as error:
                    outcome = {"error": type(error).__name__, "message": str(error)}
                result.set(outcome)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 unavailable selector execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 unavailable selector execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 unavailable selector execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("CapabilityUnavailableError", structured.getString("error"))
                assertEquals(
                    "AutoJs6 accessibility service is unavailable",
                    structured.getString("message"),
                )
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 unavailable selector")
        }
    }

    @Test
    fun screenCaptureFailsClosedWithoutAccessibilityThroughPublicPythonEngine() {
        assumeTrue(
            "Python accessibility screen capture requires Android 11 or newer",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        assumeFalse(
            "This acceptance requires the AutoJs6 accessibility service to remain unavailable",
            AutoJsAccessibilityService.hasInstance(),
        )
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import images, result
                from autojs6.errors import CapabilityUnavailableError

                try:
                    images.capture_screen()
                    outcome = {"error": "NO_ERROR", "message": ""}
                except CapabilityUnavailableError as error:
                    outcome = {"error": type(error).__name__, "message": str(error)}
                result.set(outcome)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 unavailable screen capture execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 unavailable screen capture execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 unavailable screen capture execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("CapabilityUnavailableError", structured.getString("error"))
                assertEquals(
                    "AutoJs6 accessibility service is unavailable",
                    structured.getString("message"),
                )
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 unavailable screen capture")
        }
    }

    @Test
    fun findColorFailsClosedWithoutAccessibilityThroughPublicPythonEngine() {
        assumeTrue(
            "Python accessibility screen color search requires Android 11 or newer",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        assumeFalse(
            "This acceptance requires the AutoJs6 accessibility service to remain unavailable",
            AutoJsAccessibilityService.hasInstance(),
        )
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import images, result
                from autojs6.errors import CapabilityUnavailableError

                try:
                    images.find_color(0x123456)
                    outcome = {"error": "NO_ERROR", "message": ""}
                except CapabilityUnavailableError as error:
                    outcome = {"error": type(error).__name__, "message": str(error)}
                result.set(outcome)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 unavailable screen color execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 unavailable screen color execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 unavailable screen color execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("CapabilityUnavailableError", structured.getString("error"))
                assertEquals(
                    "AutoJs6 accessibility service is unavailable",
                    structured.getString("message"),
                )
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 unavailable screen color")
        }
    }

    @Test
    fun findImageFailsClosedWithoutAccessibilityThroughPublicPythonEngine() {
        assumeTrue(
            "Python accessibility screen template search requires Android 11 or newer",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
        assumeFalse(
            "This acceptance requires the AutoJs6 accessibility service to remain unavailable",
            AutoJsAccessibilityService.hasInstance(),
        )
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                import base64

                from autojs6 import images, result
                from autojs6.errors import CapabilityUnavailableError

                template = base64.b64decode("$SCREEN_TEMPLATE_TARGET_PNG_BASE64")
                try:
                    images.find_image(template)
                    outcome = {"error": "NO_ERROR", "message": ""}
                except CapabilityUnavailableError as error:
                    outcome = {"error": type(error).__name__, "message": str(error)}
                result.set(outcome)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 unavailable screen template execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 unavailable screen template execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 unavailable screen template execution")
                val structured = JSONObject(requireNotNull(result.structuredJson))
                assertEquals("CapabilityUnavailableError", structured.getString("error"))
                assertEquals(
                    "AutoJs6 accessibility service is unavailable",
                    structured.getString("message"),
                )
                assertTrue(result.outputArtifacts.isEmpty())
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 unavailable screen template")
        }
    }

    @Test
    fun configuredNinetySecondProjectStreamsBeforeTerminalAndCompletes() {
        val projectRoot = freshProjectRoot()
        val tickMarker = "M1_LIVE_TICK_${UUID.randomUUID()}"
        val endMarker = "M1_LIVE_END_${UUID.randomUUID()}"
        try {
            writeSimpleProject(
                projectRoot,
                """
                import time

                for second in range($LONG_SLEEP_SECONDS):
                    print("$tickMarker:{}".format(second), flush=True)
                    time.sleep(1)
                print("$endMarker", flush=True)
                """.trimIndent() + "\n",
                timeoutMillis = LONG_PROJECT_TIMEOUT_MILLIS,
            )
            val launch = admittedProjectLaunch(projectRoot)
            val source = requireNotNull(launch.pythonProjectSource)
            assertEquals(LONG_PROJECT_TIMEOUT_MILLIS, source.timeoutMillis)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M1 live-output execution")
                awaitConsoleSince(checkpoint, "$tickMarker:2", LIVE_OUTPUT_TIMEOUT_SECONDS)
                assertEquals(
                    "Repeated live-output ticks only appeared after the Python execution terminated",
                    1L,
                    pending.completed.count,
                )
                awaitCompleted(
                    pending,
                    execution,
                    "M1 90-second configured execution",
                    LONG_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M1 90-second configured execution")
                assertEquals(0, result.terminal.exitCode)
                assertConsoleSince(checkpoint, "$tickMarker:0")
                assertConsoleSince(checkpoint, "$tickMarker:${LONG_SLEEP_SECONDS - 1}")
                assertConsoleSince(checkpoint, endMarker)
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M1 live-output")
        }
    }

    @Test
    fun outputBeyondLegacyFourMiBLimitCompletesWithFinalMarker() {
        val projectRoot = freshProjectRoot()
        val finalMarker = "M1_LONG_OUTPUT_END_${UUID.randomUUID()}"
        try {
            writeSimpleProject(
                projectRoot,
                """
                import sys

                sys.stdout.write("L" * $LONG_OUTPUT_PAYLOAD_BYTES)
                print("$finalMarker", flush=True)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M1 long-output execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M1 long-output execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M1 long-output execution")
                val expectedBytes = LONG_OUTPUT_PAYLOAD_BYTES.toLong() +
                    finalMarker.toByteArray(Charsets.UTF_8).size + 1L
                assertEquals(expectedBytes, result.terminal.stdoutBytes)
                assertTrue(
                    "Python stdout did not exceed the former 4 MiB Host limit",
                    result.terminal.stdoutBytes > LEGACY_OUTPUT_LIMIT_BYTES,
                )
                assertConsoleSince(checkpoint, finalMarker)
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M1 long-output")
        }
    }

    @Test
    fun stdlibHttpsRequestCompletesThroughPluginInternetPermission() {
        val networkUrl = InstrumentationRegistry.getArguments().getString(ARG_NETWORK_URL).orEmpty()
        assumeTrue(
            "M1 HTTPS smoke requires -e $ARG_NETWORK_URL https://...",
            networkUrl.startsWith("https://"),
        )
        val encodedUrl = Base64.encodeToString(networkUrl.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val projectRoot = freshProjectRoot()
        val marker = "M1_HTTPS_OK_${UUID.randomUUID()}"
        try {
            writeSimpleProject(
                projectRoot,
                """
                import base64
                import urllib.request

                url = base64.b64decode("$encodedUrl").decode("utf-8")
                with urllib.request.urlopen(url, timeout=20) as response:
                    payload = response.read(64)
                    print("$marker:{}:{}".format(response.status, len(payload)), flush=True)
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M1 HTTPS execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M1 HTTPS execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                assertSuccessful(pending, "M1 HTTPS execution")
                assertConsoleSince(checkpoint, "$marker:200:")
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M1 HTTPS")
        }
    }

    @Test
    fun projectLocalRequestsCrossesLegacyWorkspaceLimitsAndCompletesHttps() {
        val arguments = InstrumentationRegistry.getArguments()
        val networkUrl = arguments.getString(ARG_NETWORK_URL).orEmpty()
        val packageArchivePath = arguments.getString(ARG_PROJECT_LOCAL_PACKAGE_ARCHIVE).orEmpty()
        assumeTrue(
            "M4 project-local requests smoke requires -e $ARG_NETWORK_URL https://...",
            networkUrl.startsWith("https://"),
        )
        assumeTrue(
            "M4 project-local requests smoke requires -e $ARG_PROJECT_LOCAL_PACKAGE_ARCHIVE <device-zip>",
            packageArchivePath.isNotBlank(),
        )
        val packageArchive = File(packageArchivePath).canonicalFile
        val externalFilesRoot = requireNotNull(context.getExternalFilesDir(null)).canonicalFile
        assertTrue(
            "M4 package fixture must remain below the target app external-files directory",
            packageArchive.path.startsWith(externalFilesRoot.path + File.separator),
        )
        assertTrue(
            "M4 package fixture is not a bounded regular ZIP: $packageArchive",
            packageArchive.isFile && packageArchive.length() in 1L..M4_PACKAGE_FIXTURE_MAX_ARCHIVE_BYTES,
        )

        val encodedUrl = Base64.encodeToString(networkUrl.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val projectRoot = freshProjectRoot()
        try {
            var projectFileCount = unpackProjectLocalPackageFixture(packageArchive, projectRoot)
            val paddingRoot = File(projectRoot, "m4_workspace_padding").apply {
                check(mkdirs()) { "Unable to create M4 workspace padding directory: $this" }
            }
            writeRepeatedBytes(
                File(paddingRoot, "expanded-zeroes.bin"),
                M4_EXPANDED_ZERO_BYTES,
                random = null,
            )
            projectFileCount++
            writeRepeatedBytes(
                File(paddingRoot, "compressed-random.bin"),
                M4_INCOMPRESSIBLE_BYTES,
                random = Random(M4_RANDOM_SEED),
            )
            projectFileCount++
            while (projectFileCount < M4_TARGET_ARCHIVE_FILES) {
                File(paddingRoot, "entry-${projectFileCount.toString().padStart(4, '0')}.txt")
                    .writeText("m4-$projectFileCount\n", Charsets.UTF_8)
                projectFileCount++
            }
            writeSimpleProject(
                projectRoot,
                """
                import base64
                import importlib.metadata
                import os
                from pathlib import Path

                import certifi
                import charset_normalizer
                import idna
                import requests
                import urllib3
                from autojs6 import result

                root = Path.cwd().resolve()
                request_source = Path(requests.__file__).resolve()
                assert os.path.commonpath((str(root), str(request_source))) == str(root)
                url = base64.b64decode("$encodedUrl").decode("utf-8")
                response = requests.get(url, timeout=20)
                result.set({
                    "bytes": len(response.content),
                    "certifi": importlib.metadata.version("certifi"),
                    "charsetNormalizer": importlib.metadata.version("charset-normalizer"),
                    "idna": importlib.metadata.version("idna"),
                    "requests": importlib.metadata.version("requests"),
                    "status": response.status_code,
                    "urllib3": importlib.metadata.version("urllib3"),
                    "workspaceFiles": $projectFileCount,
                })
                """.trimIndent() + "\n",
            )

            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M4 project-local requests execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M4 project-local requests execution",
                    LONG_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M4 project-local requests execution")
                val root = JSONObject(requireNotNull(result.structuredJson))
                assertEquals(200, root.getInt("status"))
                assertTrue("M4 HTTPS response was empty", root.getInt("bytes") > 0)
                assertEquals(M4_REQUESTS_VERSION, root.getString("requests"))
                assertEquals(M4_URLLIB3_VERSION, root.getString("urllib3"))
                assertEquals(M4_CERTIFI_VERSION, root.getString("certifi"))
                assertEquals(M4_CHARSET_NORMALIZER_VERSION, root.getString("charsetNormalizer"))
                assertEquals(M4_IDNA_VERSION, root.getString("idna"))
                assertTrue(
                    "M4 fixture did not cross the legacy workspace entry limit",
                    root.getInt("workspaceFiles") > LEGACY_WORKSPACE_ENTRY_LIMIT,
                )
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M4 project-local requests")
        }
    }

    @Test
    fun forceStopRetiresProviderProcessAndImmediateNextRunSucceeds() {
        val firstRoot = freshProjectRoot()
        val secondRoot = freshProjectRoot()
        val firstMarker = "M1_STOP_FIRST_${UUID.randomUUID()}"
        val secondMarker = "M1_STOP_SECOND_${UUID.randomUUID()}"
        try {
            writeSimpleProject(
                firstRoot,
                """
                import os
                import time

                print("$firstMarker:{}".format(os.getpid()), flush=True)
                while True:
                    time.sleep(1)
                """.trimIndent() + "\n",
            )
            val firstLaunch = admittedProjectLaunch(firstRoot)
            val firstPending = PendingProjectExecution(firstRoot.canonicalPath)
            val firstCheckpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(firstPending.listener)
            val firstPid: Int
            try {
                service.execute(firstLaunch.source, firstLaunch.createExecutionConfig())
                val execution = awaitStarted(firstPending, "M1 force-stop execution")
                awaitConsoleSince(firstCheckpoint, firstMarker, LIVE_OUTPUT_TIMEOUT_SECONDS)
                firstPid = consolePidSince(firstCheckpoint, firstMarker)
                execution.engine.forceStop()
                awaitCompleted(
                    firstPending,
                    execution,
                    "M1 force-stop execution",
                    STOP_TIMEOUT_SECONDS,
                )
                assertEquals(1, firstPending.startCount.get())
                assertEquals(0, firstPending.successCount.get())
                assertEquals(1, firstPending.exceptionCount.get())
            } finally {
                service.unregisterGlobalScriptExecutionListener(firstPending.listener)
            }

            writeSimpleProject(
                secondRoot,
                """
                import os

                print("$secondMarker:{}".format(os.getpid()), flush=True)
                """.trimIndent() + "\n",
            )
            val secondLaunch = admittedProjectLaunch(secondRoot)
            val secondPending = PendingProjectExecution(secondRoot.canonicalPath)
            val secondCheckpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(secondPending.listener)
            try {
                service.execute(secondLaunch.source, secondLaunch.createExecutionConfig())
                val execution = awaitStarted(secondPending, "M1 immediate restart execution")
                awaitCompleted(
                    secondPending,
                    execution,
                    "M1 immediate restart execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                assertSuccessful(secondPending, "M1 immediate restart execution")
                assertConsoleSince(secondCheckpoint, secondMarker)
                val secondPid = consolePidSince(secondCheckpoint, secondMarker)
                assertTrue(
                    "Python provider PID did not change after force-stop: $firstPid",
                    firstPid != secondPid,
                )
            } finally {
                service.unregisterGlobalScriptExecutionListener(secondPending.listener)
            }
        } finally {
            cleanProjectRoot(firstRoot, "M1 force-stop first")
            cleanProjectRoot(secondRoot, "M1 force-stop second")
        }
    }

    @Test
    fun unattendedInputFailsWithEofInsteadOfWaitingForUi() {
        val projectRoot = freshProjectRoot()
        val prompt = "M1_UNATTENDED_INPUT_${UUID.randomUUID()}"
        try {
            writeSimpleProject(
                projectRoot,
                """
                print("M1_UNATTENDED_BEFORE", flush=True)
                input("$prompt")
                raise AssertionError("unattended input unexpectedly returned")
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            val checkpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M1 unattended-input execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M1 unattended-input execution",
                    STOP_TIMEOUT_SECONDS,
                )
                assertEquals(1, pending.startCount.get())
                assertEquals(0, pending.successCount.get())
                assertEquals(1, pending.exceptionCount.get())
                assertNotNull("Unattended input failed without a Host exception", pending.error.get())
                assertConsoleSince(checkpoint, prompt)
                assertConsoleSince(checkpoint, "EOFError")
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M1 unattended-input")
        }
    }

    @Test
    fun editorAndExplorerForegroundEntriesAcceptVisibleAndHiddenDialogInput() {
        val root = File(context.cacheDir, "python-m2-interactive-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "Unable to create M2 interactive-input directory: $this" }
        }
        val activity = launchForegroundActivity()
        try {
            val editorFile = File(root, "editor.py").apply {
                writeText(
                    """
                    from autojs6 import result

                    reply = input("$EDITOR_PROMPT")
                    result.set({"entry": "editor", "matched": reply == "$EDITOR_REPLY"})
                    """.trimIndent() + "\n",
                    Charsets.UTF_8,
                )
            }
            runForegroundFileEntry(
                activity = activity,
                sourceFile = editorFile,
                label = "M2 editor interactive input",
                prompt = EDITOR_PROMPT,
                reply = EDITOR_REPLY,
                password = false,
                expectedStructuredJson = EXPECTED_EDITOR_INPUT_JSON,
            ) {
                Scripts.runWithBroadcastSenderInteractive(
                    context = activity,
                    file = editorFile,
                    workingDirectory = root.canonicalPath,
                    overriddenFullPath = editorFile.canonicalPath,
                )
            }

            val explorerFile = File(root, "explorer.py").apply {
                writeText(
                    """
                    import getpass
                    from autojs6 import result

                    reply = getpass.getpass("$EXPLORER_PROMPT")
                    result.set({"entry": "explorer", "matched": reply == "$EXPLORER_REPLY"})
                    """.trimIndent() + "\n",
                    Charsets.UTF_8,
                )
            }
            runForegroundFileEntry(
                activity = activity,
                sourceFile = explorerFile,
                label = "M2 Explorer hidden interactive input",
                prompt = EXPLORER_PROMPT,
                reply = EXPLORER_REPLY,
                password = true,
                expectedStructuredJson = EXPECTED_EXPLORER_INPUT_JSON,
            ) {
                Scripts.runInteractive(activity, ScriptFile(explorerFile.path))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            assertTrue(
                "Unable to remove M2 interactive-input directory: $root",
                !root.exists() || root.deleteRecursively(),
            )
        }
    }

    @Test
    fun foregroundDialogsRoundTripThroughProtocol15Broker() {
        val root = File(context.cacheDir, "python-m3-dialogs-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "Unable to create M3 dialog directory: $this" }
        }
        val sourceFile = File(root, "dialogs.py").apply {
            writeText(
                """
                from autojs6 import dialogs, result

                dialogs.alert("$DIALOG_ALERT_TEXT", title="$DIALOG_ALERT_TITLE")
                confirmed = dialogs.confirm("$DIALOG_CONFIRM_TEXT", title="$DIALOG_CONFIRM_TITLE")
                reply = dialogs.prompt(
                    "$DIALOG_PROMPT_TEXT",
                    default="$DIALOG_PROMPT_DEFAULT",
                    title="$DIALOG_PROMPT_TITLE",
                )
                selected = dialogs.select(
                    ["$DIALOG_SELECT_FIRST", "$DIALOG_SELECT_SECOND"],
                    title="$DIALOG_SELECT_TITLE",
                )
                result.set({
                    "alert": True,
                    "confirmed": confirmed,
                    "reply": reply,
                    "selected": selected,
                })
                """.trimIndent() + "\n",
                Charsets.UTF_8,
            )
        }
        val activity = launchForegroundActivity()
        val pending = PendingFileExecution(sourceFile.canonicalPath)
        service.registerGlobalScriptExecutionListener(pending.listener)
        try {
            val submitted = AtomicReference<ScriptExecution?>()
            instrumentation.runOnMainSync {
                submitted.set(Scripts.runInteractive(activity, ScriptFile(sourceFile.path)))
            }
            assertNotNull("M3 foreground dialog execution was not submitted", submitted.get())
            assertTrue(
                "M3 foreground dialog execution did not reach the unified start callback",
                pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val execution = requireNotNull(pending.execution.get()) {
                "M3 foreground dialog execution started without ScriptExecution"
            }
            assertEquals(submitted.get()?.id, execution.id)

            clickForegroundDialogPositiveButton(
                DIALOG_ALERT_TITLE,
                DIALOG_ALERT_TEXT,
            )
            clickForegroundDialogPositiveButton(
                DIALOG_CONFIRM_TITLE,
                DIALOG_CONFIRM_TEXT,
            )
            replyToForegroundPrompt(DIALOG_PROMPT_TEXT, DIALOG_PROMPT_REPLY, password = false)
            clickForegroundDialogItem(DIALOG_SELECT_TITLE, DIALOG_SELECT_SECOND)

            awaitCompleted(
                pending,
                execution,
                "M3 foreground dialog execution",
                SMOKE_EXECUTION_TIMEOUT_SECONDS,
            )
            awaitEngineRemoved(execution.id, "M3 foreground dialog execution")
            assertEquals(1, pending.startCount.get())
            assertEquals(1, pending.successCount.get())
            assertEquals(0, pending.exceptionCount.get())
            assertEquals(null, pending.error.get())
            val result = pending.result.get() as? PythonRuntimeExecutionResult
            assertNotNull("M3 foreground dialogs did not return the Host-owned result type", result)
            assertEquals(EXPECTED_DIALOG_RESULT_JSON, requireNotNull(result).structuredJson)
            assertPrivateTransportSnapshotsRemoved()
        } finally {
            service.unregisterGlobalScriptExecutionListener(pending.listener)
            instrumentation.runOnMainSync { activity.finish() }
            assertTrue(
                "Unable to remove M3 foreground dialog directory: $root",
                !root.exists() || root.deleteRecursively(),
            )
        }
    }

    @Test
    fun backgroundDialogsFailClosedWithoutOpeningUi() {
        val projectRoot = freshProjectRoot()
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import dialogs, result
                from autojs6.errors import HostCapabilityError

                try:
                    dialogs.alert("must not open")
                    code = "NO_ERROR"
                except HostCapabilityError as error:
                    code = error.code
                result.set({"code": code})
                """.trimIndent() + "\n",
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 background dialog rejection")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 background dialog rejection",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 background dialog rejection")
                assertEquals("{\"code\":\"INTERACTIVE_NOT_ALLOWED\"}", result.structuredJson)
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 background dialog rejection")
        }
    }

    @Test
    fun hostEnginesExposeSafeCurrentMetadataAndLaunchScopedJavaScript() {
        val projectRoot = freshProjectRoot()
        val childMarker = File(projectRoot, ENGINE_CHILD_MARKER_PATH)
        try {
            writeSimpleProject(
                projectRoot,
                """
                from autojs6 import engines, result
                from autojs6.errors import HostCapabilityError

                current = engines.current()
                child = engines.run("child.js")
                try:
                    engines.run("nested.py")
                    nested_code = "NO_ERROR"
                except HostCapabilityError as error:
                    nested_code = error.code
                result.set({
                    "child": child,
                    "current": current,
                    "nestedCode": nested_code,
                })
                """.trimIndent() + "\n",
            )
            File(projectRoot, "child.js").writeText(
                "files.write(files.join(files.cwd(), \"$ENGINE_CHILD_MARKER_PATH\"), \"$ENGINE_CHILD_MARKER_TEXT\");\n",
                Charsets.UTF_8,
            )
            File(projectRoot, "nested.py").writeText(
                "raise AssertionError('nested Python must not start')\n",
                Charsets.UTF_8,
            )
            val launch = admittedProjectLaunch(projectRoot)
            val pending = PendingProjectExecution(projectRoot.canonicalPath)
            service.registerGlobalScriptExecutionListener(pending.listener)
            try {
                service.execute(launch.source, launch.createExecutionConfig())
                val execution = awaitStarted(pending, "M3 engines current/run execution")
                awaitCompleted(
                    pending,
                    execution,
                    "M3 engines current/run execution",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(pending, "M3 engines current/run execution")
                val structuredJson = requireNotNull(result.structuredJson) {
                    "M3 engines current/run execution did not expose a structured result"
                }
                assertFalse(
                    "M3 engines current metadata leaked the execution root",
                    structuredJson.contains(projectRoot.canonicalPath, ignoreCase = true),
                )
                val root = JSONObject(structuredJson)
                assertEquals("NESTED_PYTHON_NOT_ALLOWED", root.getString("nestedCode"))

                val current = root.getJSONObject("current")
                assertEquals("autojs6-python-engine-info-v1", current.getString("schema"))
                assertEquals(execution.id, current.getInt("id"))
                assertEquals("python", current.getString("engineName"))
                assertEquals(SIMPLE_ENTRY_POINT, current.getString("entryPoint"))
                assertTrue(current.getBoolean("project"))
                assertTrue(current.getLong("startedAtMillis") > 0L)
                val sourceName = current.getString("sourceName")
                assertTrue("M3 current engine source name is blank", sourceName.isNotBlank())
                assertFalse("M3 current engine source name exposed a path", '/' in sourceName || '\\' in sourceName)

                val child = root.getJSONObject("child")
                assertEquals("autojs6-python-engine-launch-v1", child.getString("schema"))
                assertEquals("child.js", child.getString("path"))
                assertFalse("Nested Python unexpectedly launched", child.getString("engineName") == "python")
                assertTrue(child.getString("sourceName").isNotBlank())
                val childExecutionId = child.getInt("id")
                assertTrue("M3 child engine ID is invalid", childExecutionId >= 0)
                awaitFileContent(
                    childMarker,
                    ENGINE_CHILD_MARKER_TEXT,
                    "M3 scoped JavaScript child",
                )
                awaitEngineRemoved(childExecutionId, "M3 scoped JavaScript child")
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(pending.listener)
            }
        } finally {
            cleanProjectRoot(projectRoot, "M3 engines current/run")
        }
    }

    @Test
    fun hostEngineStopSelfRetiresProviderAndPreventsFollowingCode() {
        val firstRoot = freshProjectRoot()
        val secondRoot = freshProjectRoot()
        val firstMarker = "M3_STOP_SELF_FIRST_${UUID.randomUUID()}"
        val secondMarker = "M3_STOP_SELF_SECOND_${UUID.randomUUID()}"
        try {
            writeSimpleProject(
                firstRoot,
                """
                import os
                from autojs6 import engines, files

                print("$firstMarker:{}".format(os.getpid()), flush=True)
                files.write_text("$ENGINE_STOP_BEFORE_PATH", "before")
                engines.stop_self()
                files.write_text("$ENGINE_STOP_AFTER_PATH", "after")
                """.trimIndent() + "\n",
            )
            val firstLaunch = admittedProjectLaunch(firstRoot)
            val firstPending = PendingProjectExecution(firstRoot.canonicalPath)
            val firstCheckpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(firstPending.listener)
            val firstPid: Int
            try {
                service.execute(firstLaunch.source, firstLaunch.createExecutionConfig())
                val execution = awaitStarted(firstPending, "M3 engines stop-self execution")
                awaitConsoleSince(firstCheckpoint, firstMarker, LIVE_OUTPUT_TIMEOUT_SECONDS)
                firstPid = consolePidSince(firstCheckpoint, firstMarker)
                awaitCompleted(
                    firstPending,
                    execution,
                    "M3 engines stop-self execution",
                    STOP_TIMEOUT_SECONDS,
                )
                assertEquals(1, firstPending.startCount.get())
                assertEquals(
                    "M3 stop-self must produce exactly one Host terminal callback",
                    1,
                    firstPending.successCount.get() + firstPending.exceptionCount.get(),
                )
                awaitFileContent(
                    File(firstRoot, ENGINE_STOP_BEFORE_PATH),
                    "before",
                    "M3 engines stop-self before marker",
                )
                assertFalse(
                    "Python code after engines.stop_self() unexpectedly executed",
                    File(firstRoot, ENGINE_STOP_AFTER_PATH).exists(),
                )
            } finally {
                service.unregisterGlobalScriptExecutionListener(firstPending.listener)
            }

            writeSimpleProject(
                secondRoot,
                """
                import os
                from autojs6 import result

                print("$secondMarker:{}".format(os.getpid()), flush=True)
                result.set({"restarted": True})
                """.trimIndent() + "\n",
            )
            val secondLaunch = admittedProjectLaunch(secondRoot)
            val secondPending = PendingProjectExecution(secondRoot.canonicalPath)
            val secondCheckpoint = consoleCheckpoint()
            service.registerGlobalScriptExecutionListener(secondPending.listener)
            try {
                service.execute(secondLaunch.source, secondLaunch.createExecutionConfig())
                val execution = awaitStarted(secondPending, "M3 engines stop-self restart")
                awaitCompleted(
                    secondPending,
                    execution,
                    "M3 engines stop-self restart",
                    SMOKE_EXECUTION_TIMEOUT_SECONDS,
                )
                val result = assertSuccessful(secondPending, "M3 engines stop-self restart")
                assertEquals("{\"restarted\":true}", result.structuredJson)
                assertConsoleSince(secondCheckpoint, secondMarker)
                val secondPid = consolePidSince(secondCheckpoint, secondMarker)
                assertTrue(
                    "Python provider PID did not change after engines.stop_self(): $firstPid",
                    firstPid != secondPid,
                )
                assertPrivateTransportSnapshotsRemoved()
            } finally {
                service.unregisterGlobalScriptExecutionListener(secondPending.listener)
            }
        } finally {
            cleanProjectRoot(firstRoot, "M3 engines stop-self first")
            cleanProjectRoot(secondRoot, "M3 engines stop-self restart")
        }
    }

    private fun runForegroundFileEntry(
        activity: Activity,
        sourceFile: File,
        label: String,
        prompt: String,
        reply: String,
        password: Boolean,
        expectedStructuredJson: String,
        launch: () -> ScriptExecution?,
    ) {
        val pending = PendingFileExecution(sourceFile.canonicalPath)
        val checkpoint = consoleCheckpoint()
        service.registerGlobalScriptExecutionListener(pending.listener)
        try {
            val submitted = AtomicReference<ScriptExecution?>()
            instrumentation.runOnMainSync { submitted.set(launch()) }
            assertNotNull("$label was not submitted", submitted.get())
            assertTrue(
                "$label did not reach the unified start callback",
                pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val execution = requireNotNull(pending.execution.get()) { "$label started without ScriptExecution" }
            assertEquals(submitted.get()?.id, execution.id)
            replyToForegroundPrompt(prompt, reply, password)
            awaitCompleted(pending, execution, label, SMOKE_EXECUTION_TIMEOUT_SECONDS)
            awaitEngineRemoved(execution.id, label)

            assertEquals(1, pending.startCount.get())
            assertEquals(1, pending.successCount.get())
            assertEquals(0, pending.exceptionCount.get())
            assertEquals(null, pending.error.get())
            val result = pending.result.get() as? PythonRuntimeExecutionResult
            assertNotNull("$label did not return the Host-owned result type", result)
            assertEquals(expectedStructuredJson, requireNotNull(result).structuredJson)
            assertConsoleSince(checkpoint, prompt)
            assertFalse(
                "$label leaked the submitted reply to the global console",
                consoleContainsSince(checkpoint, reply),
            )
            assertPrivateTransportSnapshotsRemoved()
        } finally {
            service.unregisterGlobalScriptExecutionListener(pending.listener)
        }
    }

    private fun launchForegroundActivity(): LogActivity = instrumentation.startActivitySync(
        Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    ).let { activity ->
        instrumentation.waitForIdleSync()
        activity as LogActivity
    }

    private fun launchAccessibilitySmokeActivity(): NodeAccessibilitySmokeActivity {
        NodeAccessibilitySmokeActivity.reset()
        return instrumentation.startActivitySync(
            Intent(context, NodeAccessibilitySmokeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).let { activity ->
            instrumentation.waitForIdleSync()
            SystemClock.sleep(AUTOMATOR_ACTIVITY_SETTLE_MILLIS)
            activity as NodeAccessibilitySmokeActivity
        }
    }

    private fun restoreHostForeground() {
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(HOST_PACKAGE)) {
            "Unable to resolve the canonical AutoJs6 launcher"
        }
        context.startActivity(
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        instrumentation.waitForIdleSync()
    }

    private fun replyToForegroundPrompt(prompt: String, reply: String, password: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(INPUT_DIALOG_TIMEOUT_SECONDS)
        var lastTree = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val nodes = accessibilityTree(root)
                lastTree = nodes.joinToString(" | ") { node ->
                    "${node.className}:${node.text}:${node.isPassword}:${node.isClickable}"
                }.takeLast(MAX_CONSOLE_DIAGNOSTIC_CHARS)
                val promptVisible = nodes.any { node -> node.text?.toString() == prompt }
                val input = nodes.firstOrNull { node ->
                    node.className?.toString() == "android.widget.EditText" && node.isEditable
                }
                val confirm = findPositiveDialogButton(nodes)
                if (promptVisible && input != null && confirm != null) {
                    assertEquals("Unexpected password visibility for prompt '$prompt'", password, input.isPassword)
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
                    }
                    assertTrue(
                        "Unable to enter the bounded reply for prompt '$prompt'",
                        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments),
                    )
                    SystemClock.sleep(INPUT_DIALOG_SETTLE_MILLIS)
                    assertTrue(
                        "Unable to confirm the bounded reply for prompt '$prompt'",
                        confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    )
                    return
                }
            }
            SystemClock.sleep(INPUT_DIALOG_POLL_MILLIS)
        }
        throw AssertionError("Foreground Python input dialog did not become actionable for '$prompt': $lastTree")
    }

    private fun clickForegroundDialogPositiveButton(title: String, text: String) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(INPUT_DIALOG_TIMEOUT_SECONDS)
        var lastTree = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val nodes = accessibilityTree(root)
                lastTree = accessibilitySummary(nodes)
                val titleVisible = nodes.any { node -> node.text?.toString() == title }
                val textVisible = nodes.any { node -> node.text?.toString() == text }
                val button = findPositiveDialogButton(nodes)
                if (titleVisible && textVisible && button != null) {
                    assertTrue(
                        "Unable to click the positive button in foreground dialog '$title'",
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    )
                    SystemClock.sleep(INPUT_DIALOG_SETTLE_MILLIS)
                    return
                }
            }
            SystemClock.sleep(INPUT_DIALOG_POLL_MILLIS)
        }
        throw AssertionError("Foreground dialog '$title' did not become actionable: $lastTree")
    }

    private fun findPositiveDialogButton(
        nodes: List<AccessibilityNodeInfo>,
    ): AccessibilityNodeInfo? = nodes.firstOrNull { node ->
        node.isClickable &&
            (
                node.viewIdResourceName?.substringAfterLast('/') == "md_buttonDefaultPositive" ||
                    node.text?.toString() == context.getString(R.string.dialog_button_confirm)
            )
    }

    private fun clickForegroundDialogItem(title: String, itemText: String) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(INPUT_DIALOG_TIMEOUT_SECONDS)
        var lastTree = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val nodes = accessibilityTree(root)
                lastTree = accessibilitySummary(nodes)
                val titleVisible = nodes.any { node -> node.text?.toString() == title }
                val item = nodes.firstOrNull { node -> node.text?.toString() == itemText }
                if (titleVisible && item != null && clickNodeOrAncestor(item)) {
                    SystemClock.sleep(INPUT_DIALOG_SETTLE_MILLIS)
                    return
                }
            }
            SystemClock.sleep(INPUT_DIALOG_POLL_MILLIS)
        }
        throw AssertionError("Foreground dialog item '$itemText' in '$title' did not become actionable: $lastTree")
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            val candidate = current ?: return false
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = candidate.parent
        }
        return false
    }

    private fun accessibilitySummary(nodes: List<AccessibilityNodeInfo>): String = nodes.joinToString(" | ") { node ->
        "${node.className}:${node.viewIdResourceName}:${node.text}:${node.isPassword}:${node.isClickable}"
    }.takeLast(MAX_CONSOLE_DIAGNOSTIC_CHARS)

    private fun accessibilityTree(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            nodes += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return nodes
    }

    private fun writeProject(projectRoot: File, token: String) {
        File(projectRoot, "project.json").writeText(
            """{"type":"python","entryMode":"module","main":"$PACKAGE_NAME.main"}""",
            Charsets.UTF_8,
        )
        val packageRoot = File(projectRoot, PACKAGE_NAME).apply {
            check(mkdirs()) { "Unable to create U1-R2 package directory: $this" }
        }
        File(packageRoot, "__init__.py").writeText("", Charsets.UTF_8)
        File(packageRoot, "relative_value.py").writeText("VALUE = \"relative-r2\"\n", Charsets.UTF_8)
        File(projectRoot, ENTRY_POINT).writeText(
            """
            import sys
            from autojs6 import artifacts, result
            import ssl, sqlite3, ctypes
            assert ssl.OPENSSL_VERSION
            assert sqlite3.connect(":memory:").execute("select 16 * 1024").fetchone()[0] == 16384
            assert ctypes.CDLL("libc.so").getpagesize() in (4096, 16384, 65536)
            from .relative_value import VALUE

            assert __name__ == "__main__"
            assert __package__ == "$PACKAGE_NAME"
            assert __spec__ is not None
            assert __spec__.name == "$PACKAGE_NAME.main"
            assert VALUE == "relative-r2"

            print("$token", flush=True)
            print('$STDOUT_JSON_LOOKALIKE_ESCAPED', flush=True)
            sys.stderr.write("$STDERR_MARKER\n")
            sys.stderr.flush()
            artifact_path = artifacts.path("$ARTIFACT_PATH")
            with open(artifact_path, "wb") as output:
                output.write(b"U1-R2 artifact bytes\x00\xff")
            result.set({
                "artifact": "$ARTIFACT_PATH",
                "module": __spec__.name,
                "package": __package__,
                "relative": VALUE,
                "stdoutIsResult": False,
            })
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun writeSimpleProject(projectRoot: File, source: String, timeoutMillis: Long? = null) {
        val timeoutProperty = timeoutMillis?.let { value -> ",\"timeout\":$value" }.orEmpty()
        File(projectRoot, "project.json").writeText(
            "{\"type\":\"python\",\"main\":\"$SIMPLE_ENTRY_POINT\"$timeoutProperty}",
            Charsets.UTF_8,
        )
        File(projectRoot, SIMPLE_ENTRY_POINT).writeText(source, Charsets.UTF_8)
    }

    private fun writeStdinProject(projectRoot: File, stdin: ByteArray, source: String) {
        File(projectRoot, "project.json").writeText(
            "{\"type\":\"python\",\"main\":\"$SIMPLE_ENTRY_POINT\"," +
                "\"stdin\":{\"file\":\"$STDIN_FILE_PATH\"}}",
            Charsets.UTF_8,
        )
        File(projectRoot, STDIN_FILE_PATH).apply {
            val fixtureDirectory = requireNotNull(parentFile)
            check(fixtureDirectory.mkdirs()) {
                "Unable to create stdin fixture directory: $fixtureDirectory"
            }
            writeBytes(stdin)
        }
        File(projectRoot, SIMPLE_ENTRY_POINT).writeText(source, Charsets.UTF_8)
    }

    private fun unpackProjectLocalPackageFixture(archive: File, projectRoot: File): Int {
        val canonicalRoot = projectRoot.canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator
        var entries = 0
        var files = 0
        var expandedBytes = 0L
        ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                check(entries <= M4_PACKAGE_FIXTURE_MAX_ENTRIES) {
                    "M4 package fixture exceeds its entry limit"
                }
                val name = entry.name
                check(name.isNotEmpty() && '\\' !in name && !name.startsWith('/')) {
                    "M4 package fixture contains an invalid path"
                }
                val destination = File(canonicalRoot, name).canonicalFile
                check(destination.path.startsWith(rootPrefix)) {
                    "M4 package fixture path escapes the project root"
                }
                if (entry.isDirectory) {
                    check(destination.isDirectory || destination.mkdirs()) {
                        "Unable to create M4 package fixture directory: $destination"
                    }
                } else {
                    val parent = requireNotNull(destination.parentFile)
                    check(parent.isDirectory || parent.mkdirs()) {
                        "Unable to create M4 package fixture parent: $parent"
                    }
                    check(!destination.exists() && destination.createNewFile()) {
                        "M4 package fixture contains a duplicate path: $name"
                    }
                    FileOutputStream(destination, false).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            expandedBytes = Math.addExact(expandedBytes, count.toLong())
                            check(expandedBytes <= M4_PACKAGE_FIXTURE_MAX_UNCOMPRESSED_BYTES) {
                                "M4 package fixture exceeds its expanded-size limit"
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    files++
                }
                zip.closeEntry()
            }
        }
        check(files > 0) { "M4 package fixture contains no regular files" }
        return files
    }

    private fun writeRepeatedBytes(file: File, length: Int, random: Random?) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        FileOutputStream(file, false).buffered().use { output ->
            while (remaining > 0) {
                random?.nextBytes(buffer) ?: buffer.fill(0)
                val count = minOf(buffer.size, remaining)
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    private fun admittedProjectLaunch(projectRoot: File): ScriptLaunchSourceFactory.Launch {
        val admission = requireNotNull(PythonProjectLaunchPolicy.inspect(projectRoot, null)) {
            "Python project was not admitted: $projectRoot"
        }
        return ScriptLaunchSourceFactory.createPythonProjectLaunch(
            admission,
            projectRoot.canonicalPath,
        )
    }

    private fun freshProjectRoot(): File = File(
        context.cacheDir,
        "python-u1r2-public-${UUID.randomUUID()}",
    ).apply {
        check(mkdirs()) { "Unable to create U1-R2 acceptance directory: $this" }
    }

    private fun assertConsoleSince(checkpoint: Int, expected: String) {
        assertTrue(
            "Global console did not contain '$expected' after checkpoint $checkpoint:\n" +
                consoleExcerptSince(checkpoint),
            consoleContainsSince(checkpoint, expected),
        )
    }

    private fun assertConsoleLevelSince(checkpoint: Int, expected: String, expectedLevel: Int) {
        val entry = synchronized(console.logEntries) {
            console.logEntries.firstOrNull { candidate ->
                candidate.id >= checkpoint && candidate.content.toString().contains(expected)
            }
        }
        assertNotNull(
            "Global console did not contain '$expected' after checkpoint $checkpoint:\n" +
                consoleExcerptSince(checkpoint),
            entry,
        )
        assertEquals("Global console level for '$expected'", expectedLevel, requireNotNull(entry).level)
    }

    private fun canPostPythonNotice(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(PythonHostNoticePolicy.CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun awaitPythonNotice(marker: String): StatusBarNotification {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        val deadline = SystemClock.elapsedRealtime() + NOTICE_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            manager.activeNotifications.firstOrNull { notification ->
                notificationText(notification) == marker
            }?.let { return it }
            SystemClock.sleep(CONSOLE_POLL_MILLIS)
        }
        throw AssertionError("AutoJs6 Python notification did not become active for marker $marker")
    }

    private fun cancelPythonNotices(marker: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications
            .filter { notification ->
                notification.tag?.startsWith(PythonHostNoticePolicy.TAG_PREFIX) == true &&
                    notificationText(notification) == marker
            }
            .forEach { notification -> manager.cancel(notification.tag, notification.id) }
    }

    private fun notificationText(notification: StatusBarNotification): String? =
        notification.notification.extras
            ?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?: notification.notification.extras
                ?.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()

    private fun awaitConsoleSince(checkpoint: Int, expected: String, timeoutSeconds: Long) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(timeoutSeconds)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (consoleContainsSince(checkpoint, expected)) return
            SystemClock.sleep(CONSOLE_POLL_MILLIS)
        }
        throw AssertionError(
            "Global console did not contain '$expected' within $timeoutSeconds seconds:\n" +
                consoleExcerptSince(checkpoint),
        )
    }

    private fun awaitFileContent(file: File, expected: String, label: String) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(LIVE_OUTPUT_TIMEOUT_SECONDS)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (file.isFile && runCatching { file.readText(Charsets.UTF_8) }.getOrNull() == expected) return
            SystemClock.sleep(CONSOLE_POLL_MILLIS)
        }
        throw AssertionError("$label did not publish $file with the expected UTF-8 text")
    }

    private fun consoleContainsSince(checkpoint: Int, expected: String): Boolean =
        synchronized(console.logEntries) {
            console.logEntries.any { entry ->
                entry.id >= checkpoint && entry.content.toString().contains(expected)
            }
        }

    private fun consoleExcerptSince(checkpoint: Int): String = synchronized(console.logEntries) {
        console.logEntries
            .asSequence()
            .filter { entry -> entry.id >= checkpoint }
            .map { entry -> entry.content.toString() }
            .joinToString("\n")
            .takeLast(MAX_CONSOLE_DIAGNOSTIC_CHARS)
    }

    private fun consolePidSince(checkpoint: Int, marker: String): Int {
        val expression = Regex("${Regex.escape(marker)}:(\\d+)")
        val text = synchronized(console.logEntries) {
            console.logEntries
                .asSequence()
                .filter { entry -> entry.id >= checkpoint }
                .joinToString("\n") { entry -> entry.content.toString() }
        }
        return requireNotNull(expression.find(text)?.groupValues?.get(1)?.toIntOrNull()) {
            "Global console did not expose a provider PID for $marker: ${text.takeLast(MAX_CONSOLE_DIAGNOSTIC_CHARS)}"
        }
    }

    private fun consoleCheckpoint(): Int = synchronized(console.logEntries) {
        (console.logEntries.maxOfOrNull { entry -> entry.id } ?: -1) + 1
    }

    private fun assertPrivateTransportSnapshotsRemoved() {
        val directory = File(context.noBackupFilesDir, "python-runtime-preview")
        val leftovers = directory.listFiles().orEmpty().filter { file ->
            TRANSPORT_SNAPSHOT_PREFIXES.any { prefix -> file.name.startsWith(prefix) }
        }
        assertTrue("U1-R2 public execution left private Host snapshots: $leftovers", leftovers.isEmpty())
    }

    private fun awaitEngineRemoved(executionId: Int, label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("Engine $executionId remained registered after $label: ${service.engines}")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty()) {
                assertTrue("$label left tracked script executions", service.scriptExecutions.isEmpty())
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("$label left engines=${service.engines}, executions=${service.scriptExecutions}")
    }

    private fun awaitStarted(pending: PendingProjectExecution, label: String): ScriptExecution {
        assertTrue(
            "$label did not reach the unified start callback",
            pending.started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        return requireNotNull(pending.execution.get()) { "$label started without ScriptExecution" }
    }

    private fun awaitCompleted(
        pending: PendingProjectExecution,
        execution: ScriptExecution,
        label: String,
        timeoutSeconds: Long,
    ) {
        if (!pending.completed.await(timeoutSeconds, TimeUnit.SECONDS)) {
            execution.engine.forceStop()
            throw AssertionError("$label did not reach a Host terminal callback")
        }
        awaitEngineRemoved(execution.id, label)
    }

    private fun awaitCompleted(
        pending: PendingFileExecution,
        execution: ScriptExecution,
        label: String,
        timeoutSeconds: Long,
    ) {
        if (!pending.completed.await(timeoutSeconds, TimeUnit.SECONDS)) {
            execution.engine.forceStop()
            throw AssertionError("$label did not reach a Host terminal callback")
        }
        pending.error.get()?.let { error ->
            throw AssertionError("$label failed through the public Host engine").apply { initCause(error) }
        }
    }

    private fun assertSuccessful(
        pending: PendingProjectExecution,
        label: String,
    ): PythonRuntimeExecutionResult {
        assertEquals("$label start callback count", 1, pending.startCount.get())
        assertEquals("$label success callback count", 1, pending.successCount.get())
        assertEquals("$label exception callback count", 0, pending.exceptionCount.get())
        assertEquals("$label exposed a Host error", null, pending.error.get())
        return requireNotNull(pending.result.get() as? PythonRuntimeExecutionResult) {
            "$label did not return PythonRuntimeExecutionResult"
        }
    }

    private fun assertSuccessful(
        pending: PendingFileExecution,
        label: String,
    ): PythonRuntimeExecutionResult {
        assertEquals("$label start callback count", 1, pending.startCount.get())
        assertEquals("$label success callback count", 1, pending.successCount.get())
        assertEquals("$label exception callback count", 0, pending.exceptionCount.get())
        assertEquals("$label exposed a Host error", null, pending.error.get())
        return requireNotNull(pending.result.get() as? PythonRuntimeExecutionResult) {
            "$label did not return PythonRuntimeExecutionResult"
        }
    }

    private fun cleanProjectRoot(projectRoot: File, label: String) {
        service.stopAll()
        awaitNoRegisteredEngines("during $label cleanup")
        check(projectRoot.deleteRecursively() || !projectRoot.exists()) {
            "Unable to remove $label directory: $projectRoot"
        }
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun hasInstalledOcrService(): Boolean = context.packageManager
        .queryIntentServices(Intent(OCR_PLUGIN_ACTION), PackageManager.MATCH_ALL)
        .any { resolved ->
            resolved.serviceInfo?.let { serviceInfo ->
                serviceInfo.enabled && serviceInfo.applicationInfo.enabled
            } == true
        }

    private fun createOcrFixtureBase64(): String {
        val bitmap = Bitmap.createBitmap(OCR_FIXTURE_WIDTH, OCR_FIXTURE_HEIGHT, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = OCR_FIXTURE_TEXT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(OCR_FIXTURE_TEXT, OCR_FIXTURE_LEFT, OCR_FIXTURE_BASELINE, paint)
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode the controlled OCR fixture"
            }
            val bytes = output.toByteArray()
            try {
                check(bytes.size in 1..OCR_FIXTURE_MAX_BYTES) {
                    "Controlled OCR fixture exceeds the Python image upload bound"
                }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                bytes.fill(0)
            }
        } finally {
            bitmap.eraseColor(Color.TRANSPARENT)
            bitmap.recycle()
        }
    }

    private fun ensureAccessibilityServiceStarted(description: String) {
        if (AutoJsAccessibilityService.hasInstance()) return

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
            AutoJsAccessibilityService.waitForStarted(AUTOMATOR_SERVICE_TIMEOUT_MILLIS),
        )
    }

    private fun colorsNear(expected: Int, actual: Int, tolerance: Int): Boolean =
        kotlin.math.abs(Color.red(expected) - Color.red(actual)) <= tolerance &&
            kotlin.math.abs(Color.green(expected) - Color.green(actual)) <= tolerance &&
            kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) <= tolerance &&
            kotlin.math.abs(Color.alpha(expected) - Color.alpha(actual)) <= tolerance

    private fun colorHex(color: Int): String = "#%08X".format(color)

    private inner class PendingProjectExecution(private val expectedProjectRoot: String) {
        val execution = AtomicReference<ScriptExecution?>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val startCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val engineClass = AtomicReference<Class<*>?>()
        val sourceClass = AtomicReference<Class<*>?>()
        val result = AtomicReference<Any?>()
        val error = AtomicReference<Throwable?>()

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                startCount.incrementAndGet()
                engineClass.compareAndSet(null, execution.engine.javaClass)
                sourceClass.compareAndSet(null, execution.source.javaClass)
                started.countDown()
            }

            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                successCount.incrementAndGet()
                result.set(value)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, throwable: Throwable) {
                if (!matches(execution)) return
                this@PendingProjectExecution.execution.compareAndSet(null, execution)
                exceptionCount.incrementAndGet()
                error.set(throwable)
                completed.countDown()
            }
        }

        private fun matches(execution: ScriptExecution): Boolean {
            val source = execution.source as? PythonProjectSource ?: return false
            return runCatching { source.projectRoot.canonicalPath }
                .getOrElse { source.projectRoot.absolutePath } == expectedProjectRoot
        }
    }

    private inner class PendingFileExecution(private val expectedPath: String) {
        val execution = AtomicReference<ScriptExecution?>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val startCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val result = AtomicReference<Any?>()
        val error = AtomicReference<Throwable?>()

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                if (!matches(execution)) return
                this@PendingFileExecution.execution.compareAndSet(null, execution)
                startCount.incrementAndGet()
                started.countDown()
            }

            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                if (!matches(execution)) return
                this@PendingFileExecution.execution.compareAndSet(null, execution)
                successCount.incrementAndGet()
                result.set(value)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, throwable: Throwable) {
                if (!matches(execution)) return
                this@PendingFileExecution.execution.compareAndSet(null, execution)
                exceptionCount.incrementAndGet()
                error.set(throwable)
                completed.countDown()
            }
        }

        private fun matches(execution: ScriptExecution): Boolean {
            val source = execution.source as? PythonFileSource ?: return false
            return runCatching { source.file.canonicalPath }.getOrElse { source.file.absolutePath } == expectedPath
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.u1r2.public.enabled"
        const val ARG_NETWORK_URL = "autojs.python.m1.networkUrl"
        const val ARG_PROJECT_LOCAL_PACKAGE_ARCHIVE = "autojs.python.m4.packageArchive"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val OCR_PLUGIN_ACTION = "org.autojs.plugin.PADDLE_OCR"
        const val AUTOJS_ACCESSIBILITY_SERVICE =
            "$HOST_PACKAGE/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        const val EDITOR_PROMPT = "M2_EDITOR_INPUT> "
        const val EDITOR_REPLY = "editor-visible-reply"
        const val EXPLORER_PROMPT = "M2_EXPLORER_PASSWORD> "
        const val EXPLORER_REPLY = "explorer-hidden-reply"
        const val EXPECTED_EDITOR_INPUT_JSON = "{\"entry\":\"editor\",\"matched\":true}"
        const val EXPECTED_EXPLORER_INPUT_JSON = "{\"entry\":\"explorer\",\"matched\":true}"
        const val DIALOG_ALERT_TITLE = "M3 Alert"
        const val DIALOG_ALERT_TEXT = "M3 alert text"
        const val DIALOG_CONFIRM_TITLE = "M3 Confirm"
        const val DIALOG_CONFIRM_TEXT = "M3 confirm text"
        const val DIALOG_PROMPT_TITLE = "M3 Prompt"
        const val DIALOG_PROMPT_TEXT = "M3 prompt text"
        const val DIALOG_PROMPT_DEFAULT = "M3 default"
        const val DIALOG_PROMPT_REPLY = "M3 typed reply"
        const val DIALOG_SELECT_TITLE = "M3 Select"
        const val DIALOG_SELECT_FIRST = "M3 first"
        const val DIALOG_SELECT_SECOND = "M3 second"
        const val EXPECTED_DIALOG_RESULT_JSON =
            "{\"alert\":true,\"confirmed\":true,\"reply\":\"$DIALOG_PROMPT_REPLY\",\"selected\":1}"
        const val ENGINE_CHILD_MARKER_PATH = "engine-child-marker.txt"
        const val ENGINE_CHILD_MARKER_TEXT = "child-ran"
        const val ENGINE_STOP_BEFORE_PATH = "before-stop.txt"
        const val ENGINE_STOP_AFTER_PATH = "after-stop.txt"
        const val ENTRY_MODE_ARGUMENT = "autojs6.python.entryMode"
        const val PACKAGE_NAME = "u1r2case"
        const val ENTRY_POINT = "$PACKAGE_NAME/main.py"
        const val SIMPLE_ENTRY_POINT = "main.py"
        const val STDIN_FILE_PATH = "fixtures/stdin.txt"
        const val STDIN_SNAPSHOT_ARGUMENT = "autojs6.python.stdinSnapshot"
        const val PUBLISHED_ARTIFACT_ROOT = ".python-artifacts"
        const val ARTIFACT_PATH = "reports/u1-r2.bin"
        const val SCREEN_CAPTURE_ARTIFACT_PATH = "screens/python-capture.png"
        const val SCREEN_CAPTURE_MAX_BYTES = 4 * 1024 * 1024
        const val SCREEN_CAPTURE_COLOR_TOLERANCE = 2
        const val SCREEN_TEMPLATE_SIDE = 3
        const val SCREEN_TEMPLATE_TARGET_BYTES = 73
        const val SCREEN_TEMPLATE_TARGET_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAEElEQVR4nGMQMgmDIAYsLABP/gV9MIEa5AAAAABJRU5ErkJggg=="
        const val SCREEN_TEMPLATE_ABSENT_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAEUlEQVR4nGP4d2cXBDFgYQEAXp0XNXCQT6cAAAAASUVORK5CYII="
        const val OCR_FIXTURE_TEXT = "AUTOJS6 OCR 2026"
        const val OCR_FIXTURE_WIDTH = 1_200
        const val OCR_FIXTURE_HEIGHT = 320
        const val OCR_FIXTURE_TEXT_SIZE = 112f
        const val OCR_FIXTURE_LEFT = 32f
        const val OCR_FIXTURE_BASELINE = 205f
        const val OCR_FIXTURE_MAX_BYTES = 1024 * 1024
        const val STDERR_MARKER = "U1R2_STDERR_DIAGNOSTIC"
        const val STDOUT_JSON_LOOKALIKE = "{\"stdout\":\"diagnostic-only\"}\n"
        const val STDOUT_JSON_LOOKALIKE_ESCAPED = "{\\\"stdout\\\":\\\"diagnostic-only\\\"}"
        const val EXPECTED_STDERR = "$STDERR_MARKER\n"
        const val EXPECTED_STRUCTURED_JSON =
            "{\"artifact\":\"$ARTIFACT_PATH\",\"module\":\"$PACKAGE_NAME.main\"," +
                "\"package\":\"$PACKAGE_NAME\",\"relative\":\"relative-r2\",\"stdoutIsResult\":false}"
        val ARTIFACT_BYTES = "U1-R2 artifact bytes".toByteArray(Charsets.UTF_8) + byteArrayOf(0, -1)

        val TRANSPORT_SNAPSHOT_PREFIXES = listOf("source-", "workspace-", "stdin-", "host-capabilities-")
        const val START_TIMEOUT_SECONDS = 20L
        const val EXECUTION_TIMEOUT_SECONDS = 90L
        const val LIVE_OUTPUT_TIMEOUT_SECONDS = 30L
        const val STOP_TIMEOUT_SECONDS = 30L
        const val SMOKE_EXECUTION_TIMEOUT_SECONDS = 90L
        const val LONG_EXECUTION_TIMEOUT_SECONDS = 150L
        const val LONG_SLEEP_SECONDS = 90
        const val LONG_PROJECT_TIMEOUT_MILLIS = 120_000L
        const val LEGACY_OUTPUT_LIMIT_BYTES = 4L * 1024L * 1024L
        const val LEGACY_WORKSPACE_ENTRY_LIMIT = 1_024
        const val M4_TARGET_ARCHIVE_FILES = 1_100
        const val M4_EXPANDED_ZERO_BYTES = 33 * 1024 * 1024
        const val M4_INCOMPRESSIBLE_BYTES = 17 * 1024 * 1024
        const val M4_PACKAGE_FIXTURE_MAX_ARCHIVE_BYTES = 8L * 1024L * 1024L
        const val M4_PACKAGE_FIXTURE_MAX_UNCOMPRESSED_BYTES = 16L * 1024L * 1024L
        const val M4_PACKAGE_FIXTURE_MAX_ENTRIES = 2_048
        const val M4_RANDOM_SEED = 0x4D345041434B4147L
        const val M4_REQUESTS_VERSION = "2.34.2"
        const val M4_URLLIB3_VERSION = "2.7.0"
        const val M4_CERTIFI_VERSION = "2026.7.22"
        const val M4_CHARSET_NORMALIZER_VERSION = "3.4.9"
        const val M4_IDNA_VERSION = "3.18"
        const val LONG_OUTPUT_PAYLOAD_BYTES = 4 * 1024 * 1024 + 256 * 1024
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 50L
        const val CONSOLE_POLL_MILLIS = 50L
        const val INPUT_DIALOG_TIMEOUT_SECONDS = 30L
        const val INPUT_DIALOG_POLL_MILLIS = 100L
        const val INPUT_DIALOG_SETTLE_MILLIS = 100L
        const val NOTICE_TIMEOUT_MILLIS = 5_000L
        const val AUTOMATOR_SERVICE_TIMEOUT_MILLIS = 30_000L
        const val ACCESSIBILITY_BINDING_REFRESH_MILLIS = 750L
        const val AUTOMATOR_ACTIVITY_SETTLE_MILLIS = 500L
        const val AUTOMATOR_EVENT_TIMEOUT_MILLIS = 5_000L
        const val MAX_CONSOLE_DIAGNOSTIC_CHARS = 4_096
    }
}
