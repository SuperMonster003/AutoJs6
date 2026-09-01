package org.autojs.autojs.apkbuilder.template

import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateCapabilityKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import kotlin.math.ceil

/**
 * Explicitly opted-in G2 stability batch over the real host -> Binder -> plugin path.
 *
 * A qualification driver runs this class once on each core API tier. Every tier must request at
 * least 30 successful directory builds and 30 successful single-file builds. Exactly one tier also
 * requests at least 10 cancellation injections in PREPARE, BUILD, and SIGN. Ordinary connected
 * tests skip this class unless all identity and safety arguments are supplied.
 */
@RunWith(AndroidJUnit4::class)
class RemoteApkBuildStabilityBatchDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun runsRealBinderStabilityAndCancellationBatch() {
        val qualification = requireQualificationEnvironment()
        val previousGate = Pref.getBoolean(R.string.key_apk_builder_remote_build_enabled, false)
        assertFalse("G2 qualification must start with the host gate disabled", previousGate)

        val root = File(context.cacheDir, "$PRIVATE_ROOT_PREFIX-${qualification.tier}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create the G2 qualification root." }
        }
        val directorySource = File(root, "directory-source")
        val singleFileSource = File(root, "single-file-source.js")
        val outputDir = File(root, "output").apply { check(mkdirs()) }
        val output = File(outputDir, "current.apk")
        val failures = mutableListOf<SafeFailure>()
        val successMetrics = mutableListOf<AttemptMetric>()
        val cancellationMetrics = mutableListOf<CancellationMetric>()

        var primaryFailure: Throwable? = null
        try {
            Pref.putBooleanSync(R.string.key_apk_builder_remote_build_enabled, true)

            val warmup = BuildSpec.create(
                tier = qualification.tier,
                sourceKind = SourceKind.DIRECTORY,
                iteration = 0,
                directorySource = directorySource,
                singleFileSource = singleFileSource,
            )
            runSuccessfulAttempt(warmup, output, outputDir)
            assertCleanAfterAttempt(outputDir, qualification.externalPluginProbes)

            val hostFdBaseline = hostFdCount()
            val pluginBaseline = pluginProcessSnapshot(qualification.externalPluginProbes)
            awaitExternalPluginProbe(qualification, "baseline", pluginBaseline.pid)
            var maxHostFds = hostFdBaseline
            var maxPluginFds = pluginBaseline.fdCount
            var ordinal = 0

            repeat(qualification.iterationsPerSource) { zeroBased ->
                val iteration = zeroBased + 1
                SourceKind.entries.forEach { sourceKind ->
                    ordinal += 1
                    val spec = BuildSpec.create(
                        tier = qualification.tier,
                        sourceKind = sourceKind,
                        iteration = iteration,
                        directorySource = directorySource,
                        singleFileSource = singleFileSource,
                    )
                    runCatching {
                        runSuccessfulAttempt(spec, output, outputDir)
                    }.onSuccess { metric ->
                        successMetrics += metric
                    }.onFailure { error ->
                        failures += SafeFailure(ordinal, "success-${sourceKind.logName}", error.javaClass.simpleName)
                    }
                    val snapshot = assertStableProcessAndCleanup(
                        outputDir = outputDir,
                        hostFdBaseline = hostFdBaseline,
                        pluginBaseline = pluginBaseline,
                        externalPluginProbes = qualification.externalPluginProbes,
                    )
                    maxHostFds = maxOf(maxHostFds, snapshot.hostFdCount)
                    maxPluginFds = maxOf(maxPluginFds, snapshot.plugin.fdCount)
                    Log.i(
                        TAG,
                        "attempt=$ordinal tier=${qualification.tier} kind=${sourceKind.logName} " +
                            "result=${if (failures.lastOrNull()?.ordinal == ordinal) "failed" else "succeeded"} " +
                            "host_fds=${snapshot.hostFdCount} plugin_fds=${snapshot.plugin.fdCount}",
                    )
                    if (ordinal % EXTERNAL_PROBE_ATTEMPT_INTERVAL == 0) {
                        awaitExternalPluginProbe(qualification, "attempt-$ordinal", snapshot.plugin.pid)
                    }
                }
            }

            CancelPhase.entries.forEach { phase ->
                repeat(qualification.cancellationsFor(phase)) { zeroBased ->
                    ordinal += 1
                    val iteration = zeroBased + 1
                    val spec = BuildSpec.create(
                        tier = qualification.tier,
                        sourceKind = if (iteration % 2 == 0) SourceKind.DIRECTORY else SourceKind.FILE,
                        iteration = qualification.iterationsPerSource + phase.ordinal * MAX_CANCEL_COUNT + iteration,
                        directorySource = directorySource,
                        singleFileSource = singleFileSource,
                    )
                    runCatching {
                        runCancellationAttempt(spec, phase, output, outputDir)
                    }.onSuccess { metric ->
                        cancellationMetrics += metric
                    }.onFailure { error ->
                        failures += SafeFailure(ordinal, "cancel-${phase.logName}", error.javaClass.simpleName)
                    }
                    val snapshot = assertStableProcessAndCleanup(
                        outputDir = outputDir,
                        hostFdBaseline = hostFdBaseline,
                        pluginBaseline = pluginBaseline,
                        externalPluginProbes = qualification.externalPluginProbes,
                    )
                    maxHostFds = maxOf(maxHostFds, snapshot.hostFdCount)
                    maxPluginFds = maxOf(maxPluginFds, snapshot.plugin.fdCount)
                    Log.i(
                        TAG,
                        "attempt=$ordinal tier=${qualification.tier} cancel=${phase.logName} " +
                            "result=${if (failures.lastOrNull()?.ordinal == ordinal) "failed" else "cancelled"} " +
                            "host_fds=${snapshot.hostFdCount} plugin_fds=${snapshot.plugin.fdCount}",
                    )
                }
            }

            assertStableProcessAndCleanup(
                outputDir = outputDir,
                hostFdBaseline = hostFdBaseline,
                pluginBaseline = pluginBaseline,
                externalPluginProbes = qualification.externalPluginProbes,
            )
            awaitExternalPluginProbe(qualification, "final", pluginBaseline.pid)
            val finalSnapshot = awaitFdBaseline(
                hostFdBaseline = hostFdBaseline,
                pluginBaseline = pluginBaseline,
                externalPluginProbes = qualification.externalPluginProbes,
            )

            val expectedSuccesses = qualification.iterationsPerSource * SourceKind.entries.size
            assertEquals("Not all requested successful attempts completed.", expectedSuccesses, successMetrics.size)
            assertEquals(
                "Not all requested cancellation attempts completed.",
                qualification.totalCancellations,
                cancellationMetrics.size,
            )
            assertTrue(
                "G2 batch failures: ${failures.joinToString { it.safeLabel }}",
                failures.isEmpty(),
            )

            val successDurations = successMetrics.map(AttemptMetric::elapsedMillis)
            val cancelDurations = cancellationMetrics.map(CancellationMetric::terminalLatencyMillis)
            Log.i(
                TAG,
                "M3_G2_STABILITY_RESULT tier=${qualification.tier} warmup=1 " +
                    "directory_success=${successMetrics.count { it.sourceKind == SourceKind.DIRECTORY }} " +
                    "file_success=${successMetrics.count { it.sourceKind == SourceKind.FILE }} " +
                    "prepare_cancel=${cancellationMetrics.count { it.phase == CancelPhase.PREPARE }} " +
                    "build_cancel=${cancellationMetrics.count { it.phase == CancelPhase.BUILD }} " +
                    "sign_cancel=${cancellationMetrics.count { it.phase == CancelPhase.SIGN }} failures=0 " +
                    "success_p50_ms=${percentile(successDurations, 50)} " +
                    "success_p95_ms=${percentile(successDurations, 95)} " +
                    "success_max_ms=${successDurations.maxOrNull() ?: 0L} " +
                    "cancel_max_ms=${cancelDurations.maxOrNull() ?: 0L} " +
                    "host_fd_baseline=$hostFdBaseline host_fd_max=$maxHostFds " +
                    "host_fd_final=${finalSnapshot.hostFdCount} " +
                    "plugin_pid=${pluginBaseline.pid} plugin_fd_baseline=${pluginBaseline.fdCount} " +
                    "plugin_fd_max=$maxPluginFds plugin_fd_final=${finalSnapshot.plugin.fdCount} " +
                    "host_staging_entries=0 plugin_workspace_entries=0 " +
                    "external_plugin_probes=${if (qualification.externalPluginProbes) 1 else 0}",
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            runCatching { Pref.putBooleanSync(R.string.key_apk_builder_remote_build_enabled, previousGate) }
                .onFailure { cleanupFailure = it }
            runCatching { root.deleteRecursively() }
                .onFailure { error -> cleanupFailure = appendFailure(cleanupFailure, error) }
            context.cacheDir.listFiles()
                .orEmpty()
                .filter { file -> file.name.startsWith(EXTERNAL_PROBE_ACK_PREFIX) }
                .forEach { file ->
                    runCatching { check(file.delete()) { "Unable to delete an external probe acknowledgement." } }
                        .onFailure { error -> cleanupFailure = appendFailure(cleanupFailure, error) }
                }
            if (primaryFailure == null && cleanupFailure != null) {
                throw cleanupFailure
            }
            if (primaryFailure != null && cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun runSuccessfulAttempt(
        spec: BuildSpec,
        output: File,
        outputDir: File,
    ): AttemptMetric {
        prepareSource(spec)
        check(!output.exists() || output.delete()) { "Unable to remove the preceding output." }
        RemoteApkBuildDiagnostics.resetForTest()
        val progress = CopyOnWriteArrayList<ApkBuildProgress>()
        val startedAt = SystemClock.elapsedRealtime()
        val outcome = RemoteApkBuildClient.buildBlocking(
            context = context,
            projectConfig = spec.config,
            outApk = output,
            cancelSignal = AtomicBoolean(false),
            progressListener = RemoteApkBuildClient.ProgressListener(progress::add),
            preferredPluginPackageName = PLUGIN_PACKAGE,
        )
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        try {
            check(outcome.status == ApkBuildResult.STATUS_OK) {
                "Remote build status was not OK; " +
                    "warningCount=${outcome.warnings.size}, errorCount=${outcome.errors.size}, " +
                    "actionable=${outcome.warnings.isNotEmpty() || outcome.errors.isNotEmpty()}."
            }
            check(outcome.errors.isEmpty()) { "Successful remote build returned an error." }
            check(output.isFile && output.length() > MIN_OUTPUT_BYTES) { "Successful output is missing or too small." }
            check(progress.any { it.step == ApkBuildProgress.STEP_PREPARE }) { "PREPARE progress is missing." }
            check(progress.any { it.step == ApkBuildProgress.STEP_BUILD }) { "BUILD progress is missing." }
            check(progress.any { it.step == ApkBuildProgress.STEP_SIGN }) { "SIGN progress is missing." }
            assertOutputIdentityAndIsolation(spec, output)
            assertSingleDiagnostic(RemoteApkBuildDiagnosticStatus.SUCCEEDED)
            return AttemptMetric(spec.sourceKind, elapsed, output.length())
        } finally {
            output.delete()
            check(outputDir.listFiles().orEmpty().isEmpty()) { "Output transport left a temporary artifact." }
        }
    }

    private fun runCancellationAttempt(
        spec: BuildSpec,
        phase: CancelPhase,
        output: File,
        outputDir: File,
    ): CancellationMetric {
        prepareSource(spec)
        output.writeBytes(OLD_OUTPUT_SENTINEL)
        RemoteApkBuildDiagnostics.resetForTest()
        val cancelSignal = AtomicBoolean(false)
        val triggeredAt = AtomicLong(0L)
        val progress = CopyOnWriteArrayList<ApkBuildProgress>()
        try {
            val outcome = RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = spec.config,
                outApk = output,
                cancelSignal = cancelSignal,
                progressListener = RemoteApkBuildClient.ProgressListener { update ->
                    progress += update
                    if (phase.matches(update) && cancelSignal.compareAndSet(false, true)) {
                        triggeredAt.set(SystemClock.elapsedRealtime())
                    }
                },
                preferredPluginPackageName = PLUGIN_PACKAGE,
            )
            val terminalAt = SystemClock.elapsedRealtime()
            check(triggeredAt.get() > 0L) { "The requested cancellation phase was never observed." }
            check(outcome.status == ApkBuildResult.STATUS_CANCELLED) { "Cancellation did not return STATUS_CANCELLED." }
            val latency = terminalAt - triggeredAt.get()
            check(latency in 0..MAX_CANCEL_TERMINAL_MILLIS) { "Cancellation exceeded the terminal latency budget." }
            check(output.readBytes().contentEquals(OLD_OUTPUT_SENTINEL)) { "Cancellation replaced the preceding output." }
            assertSingleDiagnostic(RemoteApkBuildDiagnosticStatus.CANCELLED)
            return CancellationMetric(phase, latency)
        } finally {
            output.delete()
            check(outputDir.listFiles().orEmpty().isEmpty()) { "Cancellation left an output transport artifact." }
        }
    }

    private fun prepareSource(spec: BuildSpec) {
        if (spec.sourceKind == SourceKind.DIRECTORY) {
            spec.source.deleteRecursively()
            check(spec.source.mkdirs()) { "Unable to recreate the directory source." }
            File(spec.source, "project.json").writeText(spec.config.toJson(false), Charsets.UTF_8)
            File(spec.source, "main.js").writeText("console.log('g2 directory');\n", Charsets.UTF_8)
            File(spec.source, spec.markerFileName).writeText(spec.markerValue, Charsets.UTF_8)
        } else {
            spec.source.parentFile?.mkdirs()
            spec.source.writeText("console.log('g2 single file');\n", Charsets.UTF_8)
        }
    }

    @Suppress("DEPRECATION")
    private fun assertOutputIdentityAndIsolation(spec: BuildSpec, output: File) {
        val archiveInfo = context.packageManager.getPackageArchiveInfo(
            output.absolutePath,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES,
        ) ?: error("PackageManager could not parse the output APK.")
        check(archiveInfo.packageName == spec.packageName) { "Output package identity differs from the request." }
        check(archiveInfo.versionName == spec.versionName) { "Output versionName differs from the request." }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        check(versionCode == spec.versionCode.toLong()) { "Output versionCode differs from the request." }

        ZipFile(output).use { apk ->
            val configEntry = apk.getEntry(PROJECT_CONFIG_ENTRY) ?: error("Output project.json is missing.")
            val packagedConfig = apk.getInputStream(configEntry).bufferedReader().use { it.readText() }
            val json = JSONObject(packagedConfig)
            check(json.getString("name") == spec.appName) { "A prior app name leaked into the output." }
            check(json.getString("packageName") == spec.packageName) { "A prior package name leaked into project.json." }
            check(json.getString("versionName") == spec.versionName) { "A prior versionName leaked into project.json." }
            check(json.getInt("versionCode") == spec.versionCode) { "A prior versionCode leaked into project.json." }
            val expectedBuildNumber = if (spec.sourceKind == SourceKind.DIRECTORY) {
                spec.buildNumber + 1L
            } else {
                spec.versionCode.toLong()
            }
            check(json.getJSONObject("build").getLong("number") == expectedBuildNumber) {
                "Build-number advancement did not match the current request."
            }
            check(json.getString("main") == "main.js") { "The packaged main script does not match the source form." }
            val markerEntries = apk.entries().asSequence()
                .map { entry -> entry.name }
                .filter { name -> name.startsWith(MARKER_ENTRY_PREFIX) }
                .toList()
            if (spec.sourceKind == SourceKind.DIRECTORY) {
                check(markerEntries == listOf("assets/project/${spec.markerFileName}")) {
                    "Directory output contains a marker from another session."
                }
                val markerEntry = requireNotNull(apk.getEntry(markerEntries.single()))
                val marker = apk.getInputStream(markerEntry).bufferedReader().use { it.readText() }
                check(marker == spec.markerValue) { "Directory marker content came from another session." }
            } else {
                check(markerEntries.isEmpty()) { "Single-file output contains a prior directory marker." }
            }
        }
    }

    private fun assertSingleDiagnostic(expectedStatus: RemoteApkBuildDiagnosticStatus) {
        val diagnostics = RemoteApkBuildDiagnostics.snapshot()
        check(diagnostics.size == 1) { "A build emitted ${diagnostics.size} structured terminal diagnostics." }
        val event = diagnostics.single()
        check(event.status == expectedStatus) { "Structured diagnostic status differs from the outcome." }
        if (expectedStatus == RemoteApkBuildDiagnosticStatus.SUCCEEDED) {
            check(event.phase == RemoteApkBuildDiagnosticPhase.COMPLETE) { "Successful diagnostic phase is not COMPLETE." }
            check(event.errorCategory == RemoteApkBuildErrorCategory.NONE) { "Successful diagnostic has an error category." }
        } else {
            check(event.errorCategory == RemoteApkBuildErrorCategory.CANCELLED) {
                "Cancelled diagnostic does not use the CANCELLED category."
            }
        }
    }

    private fun assertStableProcessAndCleanup(
        outputDir: File,
        hostFdBaseline: Int,
        pluginBaseline: PluginProcessSnapshot,
        externalPluginProbes: Boolean,
    ): ProcessSnapshot {
        check(waitUntil(CLEANUP_TIMEOUT_MILLIS) {
            hostStagingEntryCount() == 0 &&
                (externalPluginProbes || pluginWorkspaceEntryCount() == 0)
        }) { "Host or plugin workspace did not become empty." }
        check(outputDir.listFiles().orEmpty().isEmpty()) { "Qualification output directory is not empty." }
        val hostFds = hostFdCount()
        val plugin = pluginProcessSnapshot(externalPluginProbes)
        check(plugin.pid == pluginBaseline.pid) { "The plugin process changed during the batch." }
        check(hostFds <= hostFdBaseline + FD_TRANSIENT_TOLERANCE) { "Host FD count grew beyond the transient budget." }
        check(externalPluginProbes || plugin.fdCount <= pluginBaseline.fdCount + FD_TRANSIENT_TOLERANCE) {
            "Plugin FD count grew beyond the transient budget."
        }
        return ProcessSnapshot(hostFds, plugin)
    }

    private fun awaitFdBaseline(
        hostFdBaseline: Int,
        pluginBaseline: PluginProcessSnapshot,
        externalPluginProbes: Boolean,
    ): ProcessSnapshot {
        var latest = ProcessSnapshot(hostFdCount(), pluginProcessSnapshot(externalPluginProbes))
        val returnedToBaseline = waitUntil(CLEANUP_TIMEOUT_MILLIS) {
            val hostFds = hostFdCount()
            val plugin = pluginProcessSnapshot(externalPluginProbes)
            check(plugin.pid == pluginBaseline.pid) {
                "The plugin process changed while waiting for the final FD baseline."
            }
            latest = ProcessSnapshot(hostFds, plugin)
            hostFds <= hostFdBaseline &&
                (externalPluginProbes || plugin.fdCount <= pluginBaseline.fdCount)
        }
        check(returnedToBaseline) {
            "FD counts did not return to the post-warmup baselines within the cleanup window; " +
                "hostBaseline=$hostFdBaseline, hostLatest=${latest.hostFdCount}, " +
                "pluginBaseline=${pluginBaseline.fdCount}, pluginLatest=${latest.plugin.fdCount}."
        }
        return latest
    }

    private fun assertCleanAfterAttempt(outputDir: File, externalPluginProbes: Boolean) {
        check(waitUntil(CLEANUP_TIMEOUT_MILLIS) {
            hostStagingEntryCount() == 0 &&
                (externalPluginProbes || pluginWorkspaceEntryCount() == 0)
        }) { "Warmup host or plugin workspace did not become empty." }
        check(outputDir.listFiles().orEmpty().isEmpty()) { "Warmup output directory is not empty." }
    }

    private fun awaitExternalPluginProbe(
        qualification: QualificationArguments,
        checkpoint: String,
        pluginPid: Int,
    ) {
        if (!qualification.externalPluginProbes) return
        val acknowledgement = File(
            context.cacheDir,
            "$EXTERNAL_PROBE_ACK_PREFIX-${qualification.tier}-$checkpoint",
        )
        check(!acknowledgement.exists() || acknowledgement.delete()) {
            "Unable to clear a stale external probe acknowledgement."
        }
        Log.i(
            TAG,
            "M3_G2_EXTERNAL_PROBE_REQUIRED tier=${qualification.tier} " +
                "checkpoint=$checkpoint plugin_pid=$pluginPid",
        )
        check(waitUntil(EXTERNAL_PROBE_TIMEOUT_MILLIS) { acknowledgement.isFile }) {
            "The external plugin probe did not acknowledge checkpoint $checkpoint."
        }
        check(acknowledgement.delete()) { "Unable to consume the external probe acknowledgement." }
    }

    private fun hostStagingEntryCount(): Int =
        File(context.cacheDir, HOST_STAGING_DIRECTORY).listFiles().orEmpty().size

    private fun pluginWorkspaceEntryCount(): Int {
        val output = executeShellCommand(
            "run-as $PLUGIN_PACKAGE ls -A cache/$PLUGIN_WORKSPACE_DIRECTORY 2>/dev/null",
        ).trim()
        return if (output.isEmpty()) 0 else output.lineSequence().count { it.isNotBlank() }
    }

    private fun hostFdCount(): Int =
        File("/proc/self/fd").listFiles()?.size ?: error("Unable to inspect the host FD table.")

    private fun pluginProcessSnapshot(externalPluginProbes: Boolean): PluginProcessSnapshot {
        val pids = executeShellCommand("pidof $PLUGIN_PACKAGE")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        check(pids.size == 1) { "Expected exactly one plugin process." }
        val pid = pids.single().toInt()
        if (externalPluginProbes) return PluginProcessSnapshot(pid, EXTERNAL_FD_SENTINEL)
        val descriptors = executeShellCommand("run-as $PLUGIN_PACKAGE ls -1 /proc/$pid/fd")
            .lineSequence()
            .count { it.isNotBlank() }
        return PluginProcessSnapshot(pid, descriptors)
    }

    private fun requireQualificationEnvironment(): QualificationArguments {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "G2 stability qualification is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("G2 qualification requires a debug host build", BuildConfig.DEBUG)
        assertEquals("Qualification must target the canonical host", HOST_PACKAGE, context.packageName)
        assertEquals(
            "Qualification requires the canonical APK Builder package",
            PLUGIN_PACKAGE,
            arguments.getString(ARG_PLUGIN_PACKAGE),
        )

        val expectedSerial = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the exact device ro.serialno through -e $ARG_DEVICE_SERIAL", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)

        val tier = arguments.getString(ARG_TIER).orEmpty().trim()
        assertTrue("G2 tier must be a safe package segment", SAFE_TIER.matches(tier))
        val externalPluginProbes = arguments.getString(ARG_EXTERNAL_PLUGIN_PROBES) == "true"
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            assertTrue(
                "API 24 requires externally acknowledged plugin workspace and FD probes.",
                externalPluginProbes,
            )
        } else {
            assertFalse(
                "External plugin probes are restricted to the API 24 UiAutomation compatibility path.",
                externalPluginProbes,
            )
        }
        val iterations = requiredIntArgument(arguments.getString(ARG_ITERATIONS_PER_SOURCE), ARG_ITERATIONS_PER_SOURCE, 30, 100)
        val prepareCancellations = requiredIntArgument(
            arguments.getString(ARG_PREPARE_CANCELLATIONS),
            ARG_PREPARE_CANCELLATIONS,
            0,
            MAX_CANCEL_COUNT,
        )
        val buildCancellations = requiredIntArgument(
            arguments.getString(ARG_BUILD_CANCELLATIONS),
            ARG_BUILD_CANCELLATIONS,
            0,
            MAX_CANCEL_COUNT,
        )
        val signCancellations = requiredIntArgument(
            arguments.getString(ARG_SIGN_CANCELLATIONS),
            ARG_SIGN_CANCELLATIONS,
            0,
            MAX_CANCEL_COUNT,
        )
        val cancellationCounts = listOf(prepareCancellations, buildCancellations, signCancellations)
        assertTrue(
            "A cancellation tier must request either zero of every phase or at least ten of every phase.",
            cancellationCounts.all { it == 0 } || cancellationCounts.all { it >= 10 },
        )

        try {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real APK Builder plugin is not installed.").also { it.initCause(error) }
        }
        assertEquals(
            "The debug host and APK Builder plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(HOST_PACKAGE, PLUGIN_PACKAGE),
        )
        val discovered = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.discover(context)
        }.single { item -> item.serviceInfo.packageName == PLUGIN_PACKAGE }
        assertEquals(null, discovered.error)
        val capabilities = requireNotNull(requireNotNull(discovered.pluginInfo).capabilities)
        assertTrue(
            "Qualification plugin does not advertise remote-build support",
            capabilities.getBoolean(ApkBuilderTemplateCapabilityKeys.SUPPORTS_REMOTE_BUILD, false),
        )

        return QualificationArguments(
            tier = tier,
            iterationsPerSource = iterations,
            prepareCancellations = prepareCancellations,
            buildCancellations = buildCancellations,
            signCancellations = signCancellations,
            externalPluginProbes = externalPluginProbes,
        )
    }

    private fun requiredIntArgument(raw: String?, name: String, minimum: Int, maximum: Int): Int {
        val value = raw?.trim()?.toIntOrNull() ?: throw AssertionError("Pass an integer through -e $name.")
        assertTrue("$name must be in $minimum..$maximum", value in minimum..maximum)
        return value
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(CLEANUP_POLL_MILLIS)
        }
        return condition()
    }

    private fun percentile(values: List<Long>, percentile: Int): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val index = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun appendFailure(existing: Throwable?, next: Throwable): Throwable {
        if (existing == null) return next
        existing.addSuppressed(next)
        return existing
    }

    private enum class SourceKind(val logName: String) {
        DIRECTORY("directory"),
        FILE("file"),
    }

    private enum class CancelPhase(val logName: String) {
        PREPARE("prepare") {
            override fun matches(progress: ApkBuildProgress): Boolean =
                progress.step == ApkBuildProgress.STEP_PREPARE
        },
        BUILD("build") {
            override fun matches(progress: ApkBuildProgress): Boolean =
                progress.step == ApkBuildProgress.STEP_BUILD
        },
        SIGN("sign") {
            override fun matches(progress: ApkBuildProgress): Boolean =
                progress.step == ApkBuildProgress.STEP_SIGN
        };

        abstract fun matches(progress: ApkBuildProgress): Boolean
    }

    private data class QualificationArguments(
        val tier: String,
        val iterationsPerSource: Int,
        val prepareCancellations: Int,
        val buildCancellations: Int,
        val signCancellations: Int,
        val externalPluginProbes: Boolean,
    ) {
        val totalCancellations: Int
            get() = prepareCancellations + buildCancellations + signCancellations

        fun cancellationsFor(phase: CancelPhase): Int = when (phase) {
            CancelPhase.PREPARE -> prepareCancellations
            CancelPhase.BUILD -> buildCancellations
            CancelPhase.SIGN -> signCancellations
        }
    }

    private data class BuildSpec(
        val sourceKind: SourceKind,
        val source: File,
        val config: ProjectConfig,
        val appName: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val buildNumber: Long,
        val markerFileName: String,
        val markerValue: String,
    ) {
        companion object {
            fun create(
                tier: String,
                sourceKind: SourceKind,
                iteration: Int,
                directorySource: File,
                singleFileSource: File,
            ): BuildSpec {
                val kindIndex = sourceKind.ordinal + 1
                val appName = "M3 G2 $tier ${sourceKind.logName} $iteration"
                val packageName = "org.autojs.autojs6.m3g2.$tier.${sourceKind.logName}.i$iteration"
                val versionName = "2.$kindIndex.$iteration"
                val versionCode = kindIndex * 10_000 + iteration
                val buildNumber = versionCode.toLong()
                val source = if (sourceKind == SourceKind.DIRECTORY) directorySource else singleFileSource
                val markerFileName = "session-marker-${sourceKind.logName}-$iteration.txt"
                val markerValue = "M3_G2_SESSION_${tier}_${sourceKind.logName}_$iteration"
                val config = ProjectConfig()
                    .setName(appName)
                    .setPackageName(packageName)
                    .setVersionName(versionName)
                    .setVersionCode(versionCode)
                    .setMainScriptFileName(if (sourceKind == SourceKind.DIRECTORY) "main.js" else source.name)
                    .setProjectType("rhino")
                    .setSourcePath(source.absolutePath)
                    .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
                    .setLibs(emptyList())
                    .setPermissions(if (sourceKind == SourceKind.DIRECTORY) listOf("android.permission.INTERNET") else emptyList())
                    .also { projectConfig ->
                        projectConfig.buildInfo.apply {
                            buildId = "M3-G2-${tier.uppercase()}-${sourceKind.logName.uppercase()}-$iteration"
                            this.buildNumber = buildNumber
                            buildTime = buildNumber
                        }
                    }
                return BuildSpec(
                    sourceKind = sourceKind,
                    source = source,
                    config = config,
                    appName = appName,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    buildNumber = buildNumber,
                    markerFileName = markerFileName,
                    markerValue = markerValue,
                )
            }
        }
    }

    private data class AttemptMetric(
        val sourceKind: SourceKind,
        val elapsedMillis: Long,
        val outputBytes: Long,
    )

    private data class CancellationMetric(
        val phase: CancelPhase,
        val terminalLatencyMillis: Long,
    )

    private data class SafeFailure(
        val ordinal: Int,
        val operation: String,
        val category: String,
    ) {
        val safeLabel: String
            get() = "$ordinal/$operation/$category"
    }

    private data class PluginProcessSnapshot(
        val pid: Int,
        val fdCount: Int,
    )

    private data class ProcessSnapshot(
        val hostFdCount: Int,
        val plugin: PluginProcessSnapshot,
    )

    private companion object {
        const val TAG = "RemoteApkBuildStability"
        const val ARG_ENABLED = "autojs.apkBuilder.stability.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.apkBuilder.stability.serial"
        const val ARG_PLUGIN_PACKAGE = "autojs.apkBuilder.stability.pluginPackage"
        const val ARG_TIER = "autojs.apkBuilder.stability.tier"
        const val ARG_ITERATIONS_PER_SOURCE = "autojs.apkBuilder.stability.iterationsPerSource"
        const val ARG_PREPARE_CANCELLATIONS = "autojs.apkBuilder.stability.prepareCancellations"
        const val ARG_BUILD_CANCELLATIONS = "autojs.apkBuilder.stability.buildCancellations"
        const val ARG_SIGN_CANCELLATIONS = "autojs.apkBuilder.stability.signCancellations"
        const val ARG_EXTERNAL_PLUGIN_PROBES = "autojs.apkBuilder.stability.externalPluginProbes"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val PLUGIN_PACKAGE = ApkBuilderTemplatePluginIds.PACKAGE_NAME
        const val HOST_STAGING_DIRECTORY = "apk-remote-build-input"
        const val PLUGIN_WORKSPACE_DIRECTORY = "remote-apk-build"
        const val PRIVATE_ROOT_PREFIX = "m3-g2-stability-private"
        const val PROJECT_CONFIG_ENTRY = "assets/project/project.json"
        const val MARKER_ENTRY_PREFIX = "assets/project/session-marker-"
        const val MIN_OUTPUT_BYTES = 1_000_000L
        const val MAX_CANCEL_COUNT = 20
        const val MAX_CANCEL_TERMINAL_MILLIS = 5_000L
        const val CLEANUP_TIMEOUT_MILLIS = 10_000L
        const val EXTERNAL_PROBE_TIMEOUT_MILLIS = 60_000L
        const val CLEANUP_POLL_MILLIS = 100L
        const val FD_TRANSIENT_TOLERANCE = 2
        const val EXTERNAL_PROBE_ATTEMPT_INTERVAL = 30
        const val EXTERNAL_FD_SENTINEL = -1
        const val EXTERNAL_PROBE_ACK_PREFIX = "m3-g2-external-probe-ack"
        val SAFE_TIER = Regex("[a-z][a-z0-9]{2,15}")
        val OLD_OUTPUT_SENTINEL = "M3_G2_OLD_OUTPUT_MUST_SURVIVE_CANCEL".toByteArray(Charsets.UTF_8)
    }
}
