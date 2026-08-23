package org.autojs.autojs.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
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
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
                val confirm = nodes.firstOrNull { node ->
                    node.text?.toString() == context.getString(R.string.dialog_button_confirm) && node.isClickable
                }
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
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val EDITOR_PROMPT = "M2_EDITOR_INPUT> "
        const val EDITOR_REPLY = "editor-visible-reply"
        const val EXPLORER_PROMPT = "M2_EXPLORER_PASSWORD> "
        const val EXPLORER_REPLY = "explorer-hidden-reply"
        const val EXPECTED_EDITOR_INPUT_JSON = "{\"entry\":\"editor\",\"matched\":true}"
        const val EXPECTED_EXPLORER_INPUT_JSON = "{\"entry\":\"explorer\",\"matched\":true}"
        const val ENTRY_MODE_ARGUMENT = "autojs6.python.entryMode"
        const val PACKAGE_NAME = "u1r2case"
        const val ENTRY_POINT = "$PACKAGE_NAME/main.py"
        const val SIMPLE_ENTRY_POINT = "main.py"
        const val STDIN_FILE_PATH = "fixtures/stdin.txt"
        const val STDIN_SNAPSHOT_ARGUMENT = "autojs6.python.stdinSnapshot"
        const val PUBLISHED_ARTIFACT_ROOT = ".python-artifacts"
        const val ARTIFACT_PATH = "reports/u1-r2.bin"
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
        const val LONG_OUTPUT_PAYLOAD_BYTES = 4 * 1024 * 1024 + 256 * 1024
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 50L
        const val CONSOLE_POLL_MILLIS = 50L
        const val INPUT_DIALOG_TIMEOUT_SECONDS = 30L
        const val INPUT_DIALOG_POLL_MILLIS = 100L
        const val INPUT_DIALOG_SETTLE_MILLIS = 100L
        const val MAX_CONSOLE_DIAGNOSTIC_CHARS = 4_096
    }
}
