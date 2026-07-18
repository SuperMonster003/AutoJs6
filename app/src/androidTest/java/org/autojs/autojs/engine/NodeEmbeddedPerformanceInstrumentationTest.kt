package org.autojs.autojs.engine

import android.content.ContentValues
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class NodeEmbeddedPerformanceInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun embeddedStartupAndMemoryReportIsWritten() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded Node performance benchmark")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("embedded Node persistent-runtime performance benchmark")

        val samplePlan = performanceSamplePlan()
        val cycles = mutableListOf<PerformanceCycle>()
        val snapshots = mutableListOf<ProcessSnapshot>()
        val beforeScript = processSnapshot("beforeScript", Process.myPid()).also(snapshots::add)

        repeat(samplePlan.coldCycles) { cycleIndex ->
            if (cycleIndex > 0) {
                NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            }
            snapshots += processSnapshot("cycle${cycleIndex + 1}.beforeCold.app", Process.myPid())

            val cold = runMeasured("cold-${cycleIndex + 1}")
            NodeEmbeddedTestHarness.assertSucceeded("cold performance run ${cycleIndex + 1}", cold.result)
            assertClientResultDeliveryIsNonBlocking(cold)
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterCold.app", Process.myPid())
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterCold.service", cold.result.pid)

            val runtimeWarmRuns = mutableListOf<MeasuredRun>()
            var previous = cold
            repeat(samplePlan.runtimeWarmSamplesPerCold) { warmIndex ->
                val runtimeWarm = runMeasured("runtime-warm-${cycleIndex + 1}-${warmIndex + 1}")
                NodeEmbeddedTestHarness.assertSucceeded(
                    "runtime-warm performance run ${cycleIndex + 1}.${warmIndex + 1}",
                    runtimeWarm.result,
                )
                assertClientResultDeliveryIsNonBlocking(runtimeWarm)
                assertSameProcessPoolSlot(cold, runtimeWarm)
                assertRuntimeWarmReuse(cold, previous, runtimeWarm)
                runtimeWarmRuns += runtimeWarm
                previous = runtimeWarm
            }
            val lastRuntimeWarm = runtimeWarmRuns.last()
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterRuntimeWarm.app", Process.myPid())
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterRuntimeWarm.service", lastRuntimeWarm.result.pid)
            cycles += PerformanceCycle(cycleIndex + 1, cold, runtimeWarmRuns)
        }

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        val afterDestroy = processSnapshot("afterDestroy", Process.myPid()).also(snapshots::add)

        cycles.flatMap { it.runs }.forEach { run ->
            assertTrue("${run.label} did not print perf payload\n${run.result.stdout}", run.nodePayload != null)
            assertPhaseTimings(run)
        }

        val report = buildReport(
            beforeScript = beforeScript,
            afterDestroy = afterDestroy,
            samplePlan = samplePlan,
            cycles = cycles,
            snapshots = snapshots,
        )
        val reportFile = writeReports(report)

        assertThresholds(report)
        assertTrue("performance report was not written: ${reportFile.absolutePath}", reportFile.isFile && reportFile.length() > 0L)
        println("node.performance.report=${reportFile.absolutePath}")
        println("node.performance.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/perf")
    }

    private fun runMeasured(label: String): MeasuredRun {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = performanceScript(label),
            sourceName = "<embedded-node-performance-$label.js>",
            workingDirectory = File(context.cacheDir, "embedded-node-performance").apply { mkdirs() },
            timeoutMs = SCRIPT_TIMEOUT_MS,
        )
        val wallMs = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAt).coerceAtLeast(0L)
        return MeasuredRun(
            label = label,
            wallMs = wallMs,
            result = result,
            nodePayload = parseNodePayload(result.stdout),
            clientServicePhaseTimings = NodeEmbeddedProcessSessionManager.clientPhaseTimings(
                result.nativeValues,
            ),
        )
    }

    private fun buildReport(
        beforeScript: ProcessSnapshot,
        afterDestroy: ProcessSnapshot,
        samplePlan: PerformanceSamplePlan,
        cycles: List<PerformanceCycle>,
        snapshots: List<ProcessSnapshot>,
    ): JSONObject {
        val coldRuns = cycles.map { it.cold }
        val runtimeWarmRuns = cycles.flatMap { it.runtimeWarm }
        val cold = coldRuns.first()
        val runtimeWarm = runtimeWarmRuns.first()
        val afterFirstScript = snapshots.first { it.label == "cycle1.afterCold.app" }
        val coldLoadMs = actualRuntimeReadyTiming(
            cold,
            NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY,
        )
        val runtimeWarmLoadMs = actualRuntimeReadyTiming(
            runtimeWarm,
            NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY,
        )
        val appRssDeltaAfterFirstScript = afterFirstScript.rssKb?.minusOrNull(beforeScript.rssKb)
        val appRssDeltaAfterDestroy = afterDestroy.rssKb?.minusOrNull(beforeScript.rssKb)
        val appThreadDeltaAfterDestroy = afterDestroy.threadCount?.minusOrNull(beforeScript.threadCount)
        val appFdDeltaAfterDestroy = afterDestroy.fdCount?.minusOrNull(beforeScript.fdCount)

        return JSONObject()
            .put("schema", "autojs6-node-performance-v4")
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put("reportPull", reportPullJson())
            .put("samplePlan", samplePlan.toJson())
            .put(
                "metrics",
                JSONObject()
                    .put("serviceProcessStartTimeMs", cold.wallMs)
                    .put("serviceProcessStartTimeNote", "Measured as cold runScript wall time, including service start, bind, native load, script execution, and result return.")
                    .put("libnodeLoadMs", coldLoadMs ?: JSONObject.NULL)
                    .put("libnodeLoadRuntimeWarmMs", runtimeWarmLoadMs ?: JSONObject.NULL)
                    .put("libnodeLoadMeasurable", coldLoadMs != null || runtimeWarmLoadMs != null)
                    .put("firstScriptExecutionWallMs", cold.result.elapsedMs)
                    .put("runtimeWarmScriptExecutionWallMs", runtimeWarm.result.elapsedMs)
                    .put("coldRunWallMs", cold.wallMs)
                    .put("runtimeWarmRunWallMs", runtimeWarm.wallMs)
                    .put("appRssDeltaAfterFirstScriptKb", appRssDeltaAfterFirstScript ?: JSONObject.NULL)
                    .put("appRssDeltaAfterDestroyKb", appRssDeltaAfterDestroy ?: JSONObject.NULL)
                    .put("appThreadDeltaAfterDestroy", appThreadDeltaAfterDestroy ?: JSONObject.NULL)
                    .put("appFdDeltaAfterDestroy", appFdDeltaAfterDestroy ?: JSONObject.NULL),
            )
            .put("percentiles", percentileReport(coldRuns, runtimeWarmRuns))
            .put("phaseTimings", phaseTimingReport(coldRuns, runtimeWarmRuns))
            .put("snapshots", JSONArray(snapshots.map { it.toJson() }))
            .put("cycles", JSONArray(cycles.map { it.toJson() }))
            .put("runs", JSONArray(cycles.flatMap { it.runs }.map { it.toJson() }))
            .put(
                "thresholds",
                JSONObject()
                    .put("enabled", instrumentationBoolean(ARG_FAIL_ON_THRESHOLD, false))
                    .put("maxColdWallMs", instrumentationLong(ARG_MAX_COLD_WALL_MS))
                    .put("maxRuntimeWarmWallMs", runtimeWarmWallThreshold())
                    .put("maxColdP50Ms", instrumentationLong(ARG_MAX_COLD_P50_MS))
                    .put("maxRuntimeWarmP50Ms", instrumentationLong(ARG_MAX_RUNTIME_WARM_P50_MS))
                    .put("maxRuntimeWarmP95Ms", instrumentationLong(ARG_MAX_RUNTIME_WARM_P95_MS))
                    .put("maxResultToReturnP95Ms", instrumentationLong(ARG_MAX_RESULT_TO_RETURN_P95_MS))
                    .put("maxAppRssDeltaAfterDestroyKb", instrumentationLong(ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB))
                    .put("maxServiceRssKb", instrumentationLong(ARG_MAX_SERVICE_RSS_KB)),
            )
    }

    private fun percentileReport(coldRuns: List<MeasuredRun>, runtimeWarmRuns: List<MeasuredRun>): JSONObject =
        JSONObject()
            .put("method", "nearest-rank")
            .put("cold", runDistributionJson(coldRuns))
            .put("runtimeWarm", runDistributionJson(runtimeWarmRuns))

    private fun phaseTimingReport(
        coldRuns: List<MeasuredRun>,
        runtimeWarmRuns: List<MeasuredRun>,
    ): JSONObject = JSONObject()
        .put("schema", "autojs6-node-performance-phase-distributions-v2")
        .put("method", "nearest-rank")
        .put("additive", false)
        .put(
            "interpretation",
            "Totals, boundary aggregates, host segments and nested native metrics overlap. " +
                "Never sum phase distributions to reconstruct wall time.",
        )
        .put(
            "clientService",
            JSONObject()
                .put("schema", NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_SCHEMA)
                .put("transportSchemaKey", NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_SCHEMA_KEY)
                .put("transportJsonKey", NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_JSON_KEY)
                .put(
                    "scope",
                    "Client/session/service work measured from direct embedded-service request acquisition through callback return.",
                )
                .put("additive", false)
                .put("totals", JSONArray(NodeEmbeddedProcessSessionManager.PhaseTiming.totalNames))
                .put("segments", JSONArray(NodeEmbeddedProcessSessionManager.PhaseTiming.segmentNames))
                .put(
                    "boundaryAggregates",
                    JSONArray(NodeEmbeddedProcessSessionManager.PhaseTiming.boundaryAggregateNames),
                )
                .put(
                    "overlap",
                    JSONObject()
                        .put(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.CLIENT_ATTEMPT_TOTAL,
                            "End-to-end runScriptAttempt total containing session acquisition, request transport, service/native execution, callback handling and client cleanup.",
                        )
                        .put(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.SESSION_ACQUIRE,
                            JSONArray(
                                listOf(
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.SESSION_DISCOVER,
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.SESSION_DECLARED,
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.SESSION_BIND,
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.SESSION_PROCESS_READY,
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.SESSION_RUNTIME_READY,
                                ),
                            ),
                        )
                        .put(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.CALLBACK_TO_RETURN,
                            JSONArray(
                                listOf(
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.RESULT_PAYLOAD_DECODE,
                                    NodeEmbeddedProcessSessionManager.PhaseTiming.CLEANUP,
                                ),
                            ),
                        )
                        .put(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.NATIVE,
                            "Host wait around runtime-executor work; nativeLifecycle metrics below are nested inside it.",
                        ),
                )
                .put(
                    "payloadTiming",
                    JSONObject()
                        .put(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.RESULT_PAYLOAD_ENCODE,
                            "Service-local Bundle field population, native-value filtering/truncation and String-array construction. Android parcel serialization and Binder transfer are excluded.",
                        )
                        .put(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.RESULT_PAYLOAD_DECODE,
                            "Client work from result-callback entry through Bundle field parsing and EmbeddedScriptResult construction. Binder transfer is excluded; lazy Bundle unparceling triggered in the callback may be included.",
                        ),
                )
                .put("cold", clientServicePhaseModeReport(coldRuns))
                .put("runtimeWarm", clientServicePhaseModeReport(runtimeWarmRuns)),
        )
        .put(
            "nativeLifecycle",
            JSONObject()
                .put("schema", NATIVE_PHASE_TIMING_SCHEMA)
                .put("transportSchemaKey", NATIVE_PHASE_TIMING_SCHEMA_KEY)
                .put(
                    "scope",
                    "Nested native adapter lifecycle timings. Missing, skipped and not-applicable values are never synthesized as zero or included as samples.",
                )
                .put("additive", false)
                .put(
                    "nesting",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("parent", "timing.load_environment.ms")
                                .put(
                                    "children",
                                    JSONArray(
                                        listOf(
                                            "timing.bootstrap.ms",
                                            "timing.module_preload.ms",
                                        ),
                                    ),
                                )
                                .put(
                                    "meaning",
                                    "Measured bootstrap and eager module-preload work execute inside Node LoadEnvironment; these child values must not be added to LoadEnvironment.",
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("startsWithin", "timing.load_environment.ms")
                                .put("interval", "timing.script_execution.ms")
                                .put("mayOverlap", "timing.spin_event_loop.ms")
                                .put(
                                    "meaning",
                                    "Entry-to-terminal scriptExecution starts during LoadEnvironment and may remain active across SpinEventLoop for asynchronous scripts. LoadEnvironment, scriptExecution and SpinEventLoop can overlap and must not be added together.",
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("parent", "timing.total.ms")
                                .put("meaning", "Native lifecycle total overlaps setup, LoadEnvironment, SpinEventLoop and teardown metrics."),
                        ),
                )
                .put("statusKeys", nativePhaseStatusKeyReport())
                .put("cold", nativePhaseModeReport(coldRuns))
                .put("runtimeWarm", nativePhaseModeReport(runtimeWarmRuns)),
        )
        .put(
            "engine",
            JSONObject()
                .put("schema", "autojs6-node-engine-phase-timing-v1")
                .put(
                    "scope",
                    "AutoJs6 JavaScript-engine request preparation before the embedded service call.",
                )
                .put("harness", "direct_embedded_service")
                .put(
                    "coverageNote",
                    "This benchmark bypasses the JavaScript engine. Engine phases are reported only when supplied by the caller; unavailable samples are missing, not zero.",
                )
                .put("cold", enginePhaseModeReport(coldRuns))
                .put("runtimeWarm", enginePhaseModeReport(runtimeWarmRuns)),
        )
        .put(
            "hostPreparation",
            JSONObject()
                .put("schema", "autojs6-node-host-preparation-phase-timing-v1")
                .put(
                    "scope",
                    "Android host-side preparation, including compile-cache setup/pruning and native library load only for the acquire that actually initializes the process runtime.",
                )
                .put(
                    "coverageNote",
                    "Missing samples remain missing and are never synthesized as zero.",
                )
                .put("cold", hostPreparationPhaseModeReport(coldRuns))
                .put("runtimeWarm", hostPreparationPhaseModeReport(runtimeWarmRuns)),
        )

    private fun clientServicePhaseModeReport(runs: List<MeasuredRun>): JSONObject = JSONObject()
        .put(
            "totals",
            phaseModeReport(runs, NodeEmbeddedProcessSessionManager.PhaseTiming.totalNames) { run, name ->
                run.clientServicePhaseTimings[name]
            },
        )
        .put(
            "segments",
            phaseModeReport(runs, NodeEmbeddedProcessSessionManager.PhaseTiming.segmentNames) { run, name ->
                run.clientServicePhaseTimings[name]
            },
        )
        .put(
            "boundaryAggregates",
            phaseModeReport(
                runs,
                NodeEmbeddedProcessSessionManager.PhaseTiming.boundaryAggregateNames,
            ) { run, name -> run.clientServicePhaseTimings[name] },
        )
        .put(
            "harnessOutsideClientAttempt",
            distributionJson(
                runs.mapNotNull { run ->
                    run.clientServicePhaseTimings[
                        NodeEmbeddedProcessSessionManager.PhaseTiming.CLIENT_ATTEMPT_TOTAL
                    ]?.let { totalMs -> (run.wallMs - totalMs).coerceAtLeast(0L) }
                },
                includeSamples = false,
            ).put(
                "meaning",
                "Benchmark wall time minus clientAttemptTotal. This is the only reported unaccounted gap; nested child phases are deliberately not subtracted from one another.",
            ),
        )

    private fun nativePhaseModeReport(runs: List<MeasuredRun>): JSONObject = JSONObject().apply {
        NATIVE_PHASE_METRICS.forEach { metric ->
            val samples = runs.mapNotNull { run -> nativeTimingSample(run, metric) }
            val statusExcluded = runs.count { run -> nativeTimingStatusExcluded(run, metric) }
            val missing = (runs.size - samples.size - statusExcluded).coerceAtLeast(0)
            put(
                metric.key,
                distributionJson(samples, includeSamples = false)
                    .put("missing", missing)
                    .put("statusExcluded", statusExcluded),
            )
        }
    }

    private fun nativePhaseStatusKeyReport(): JSONObject = JSONObject().apply {
        NATIVE_PHASE_METRICS.forEach { metric ->
            metric.statusKey?.let { statusKey ->
                put(
                    metric.key,
                    JSONObject()
                        .put("key", statusKey)
                        .put("measuredStatuses", JSONArray(metric.measuredStatuses.sorted())),
                )
            }
        }
    }

    private fun nativeTimingSample(run: MeasuredRun, metric: NativePhaseMetric): Long? {
        return metric.sample(run.result.nativeValues)
    }

    private fun nativeTimingStatusExcluded(run: MeasuredRun, metric: NativePhaseMetric): Boolean {
        return metric.statusExcluded(run.result.nativeValues)
    }

    private fun enginePhaseModeReport(runs: List<MeasuredRun>): JSONObject =
        phaseModeReport(runs, ENGINE_PHASE_TIMING_KEYS) { run, name ->
            run.result.nativeValues[name]?.toLongOrNull()?.takeIf { it >= 0L }
        }

    private fun hostPreparationPhaseModeReport(runs: List<MeasuredRun>): JSONObject =
        phaseModeReport(runs, HOST_PREPARATION_PHASE_TIMING_KEYS) { run, name ->
            when (name) {
                NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY ->
                    actualRuntimeReadyTiming(run, name)
                "timing.compile_cache_prepare.ms" -> run.result.nativeValues[name]
                    ?.toLongOrNull()
                    ?.takeIf {
                        it >= 0L &&
                            run.result.nativeValues["embedded_script.compile_cache.prepare_status"] == "ready"
                    }
                "timing.compile_cache_prune.ms" -> run.result.nativeValues[name]
                    ?.toLongOrNull()
                    ?.takeIf {
                        it >= 0L &&
                            run.result.nativeValues["embedded_script.compile_cache.prune_status"] == "executed"
                    }
                else -> null
            }
        }

    private fun actualRuntimeReadyTiming(run: MeasuredRun, key: String): Long? {
        if (
            run.result.nativeValues[
                NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZED_DURING_ACQUIRE_KEY
            ] != "true"
        ) {
            return null
        }
        return run.result.nativeValues[key]?.toLongOrNull()?.takeIf { it >= 0L }
    }

    private fun phaseModeReport(
        runs: List<MeasuredRun>,
        names: List<String>,
        value: (MeasuredRun, String) -> Long?,
    ): JSONObject = JSONObject().apply {
        names.forEach { name ->
            val values = runs.mapNotNull { run -> value(run, name) }
            put(
                name,
                distributionJson(values, includeSamples = false)
                    .put("missing", (runs.size - values.size).coerceAtLeast(0)),
            )
        }
    }

    private fun runDistributionJson(runs: List<MeasuredRun>): JSONObject =
        JSONObject()
            .put("sampleCount", runs.size)
            .put("wallMs", distributionJson(runs.map { it.wallMs }))
            .put("resultElapsedMs", distributionJson(runs.map { it.result.elapsedMs }))
            .put(
                "resultToReturnMs",
                distributionJson(runs.mapNotNull { it.result.nativeValues[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull() }),
            )

    private fun distributionJson(values: List<Long>, includeSamples: Boolean = true): JSONObject {
        val sorted = values.sorted()
        return JSONObject()
            .put("count", sorted.size)
            .put("min", sorted.firstOrNull() ?: JSONObject.NULL)
            .put("p50", nearestRank(sorted, 50) ?: JSONObject.NULL)
            .put("p95", nearestRank(sorted, 95) ?: JSONObject.NULL)
            .put("max", sorted.lastOrNull() ?: JSONObject.NULL)
            .put("mean", if (sorted.isEmpty()) JSONObject.NULL else sorted.average())
            .apply {
                if (includeSamples) {
                    put("samples", JSONArray(values))
                }
            }
    }

    private fun nearestRank(sorted: List<Long>, percentile: Int): Long? {
        if (sorted.isEmpty()) return null
        val rank = ((sorted.size * percentile + 99) / 100).coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun assertPhaseTimings(run: MeasuredRun) {
        val values = run.result.nativeValues
        assertTrue(
            "${run.label} phase timing schema is missing or unsupported: $values",
            values[NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_SCHEMA_KEY] ==
                NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_SCHEMA,
        )
        assertTrue(
            "${run.label} compact phase timing JSON is missing: $values",
            !values[NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_JSON_KEY].isNullOrBlank(),
        )
        NodeEmbeddedProcessSessionManager.PhaseTiming.orderedNames.forEach { phase ->
            val durationMs = run.clientServicePhaseTimings[phase]
            assertTrue(
                "${run.label} phase timing is missing or negative: " +
                    "phase=$phase timings=${run.clientServicePhaseTimings}",
                durationMs != null && durationMs >= 0L,
            )
        }
        val callbackToReturnMs = run.clientServicePhaseTimings[
            NodeEmbeddedProcessSessionManager.PhaseTiming.CALLBACK_TO_RETURN
        ]
        val legacyResultToReturnMs = values[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull()
        assertTrue(
            "${run.label} callback-to-return phase disagrees with the compatibility metric: " +
                "phase=$callbackToReturnMs legacy=$legacyResultToReturnMs",
            callbackToReturnMs == legacyResultToReturnMs,
        )
        assertTrue(
            "${run.label} native lifecycle timing schema is missing or unsupported: $values",
            values[NATIVE_PHASE_TIMING_SCHEMA_KEY] == NATIVE_PHASE_TIMING_SCHEMA,
        )
        assertTrue(
            "${run.label} native lifecycle timings are unavailable: $values",
            values[NATIVE_PHASE_TIMING_STATUS_KEY] == "available",
        )
        val requiredMetrics = REQUIRED_NATIVE_PHASE_KEYS + if (run.label.startsWith("cold-")) {
            REQUIRED_COLD_NATIVE_PHASE_KEYS
        } else {
            emptySet()
        }
        requiredMetrics.forEach { key ->
            val metric = NATIVE_PHASE_METRICS_BY_KEY.getValue(key)
            val durationMs = nativeTimingSample(run, metric)
            val status = metric.statusKey?.let(values::get)
            assertTrue(
                "${run.label} required native phase is missing, skipped or not applicable: " +
                    "phase=$key statusKey=${metric.statusKey} status=$status values=$values",
                durationMs != null,
            )
        }
        val runtimeInitializedDuringAcquire = values[
            NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZED_DURING_ACQUIRE_KEY
        ] == "true"
        if (run.label.startsWith("cold-")) {
            assertTrue(
                "${run.label} did not record actual process-runtime initialization: $values",
                runtimeInitializedDuringAcquire,
            )
            assertTrue(
                "${run.label} actual runtime initialization did not retain native load timing: $values",
                actualRuntimeReadyTiming(
                    run,
                    NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY,
                ) != null,
            )
        } else {
            assertTrue(
                "${run.label} replayed process-runtime initialization state: $values",
                !runtimeInitializedDuringAcquire,
            )
            assertTrue(
                "${run.label} replayed cold process-runtime/native-load timing: $values",
                values[NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZE_TIMING_KEY] == null &&
                    values[NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY] == null,
            )
        }
    }

    private fun writeReports(report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/perf").apply { mkdirs() }
        val reportFile = File(reportDir, "embedded-node-performance-${System.currentTimeMillis()}.json")
        report
            .put("devicePrivateReportPath", reportFile.absolutePath)
            .put("devicePublicReportPath", "$DEVICE_REPORT_DIR/${reportFile.name}")
        val reportText = report.toString(2)
        reportFile.writeText(reportText)
        writePublicReport(reportFile.name, reportText)
        return reportFile
    }

    private fun writePublicReport(fileName: String, reportText: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AutoJs6/nodejs/perf")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(reportText.toByteArray(Charsets.UTF_8))
                } ?: return false
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val reportDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "AutoJs6/nodejs/perf",
                ).apply { mkdirs() }
                File(reportDir, fileName).writeText(reportText)
                true
            }
        }.getOrDefault(false)

    private fun assertThresholds(report: JSONObject) {
        if (!instrumentationBoolean(ARG_FAIL_ON_THRESHOLD, false)) {
            return
        }
        val metrics = report.getJSONObject("metrics")
        assertBelow("cold run wall time", metrics.optNullableLong("coldRunWallMs"), instrumentationLong(ARG_MAX_COLD_WALL_MS))
        assertBelow(
            "runtime-warm run wall time",
            metrics.optNullableLong("runtimeWarmRunWallMs"),
            runtimeWarmWallThreshold(),
        )
        val percentiles = report.getJSONObject("percentiles")
        val cold = percentiles.getJSONObject("cold")
        val runtimeWarm = percentiles.getJSONObject("runtimeWarm")
        assertBelow(
            "cold wall p50",
            cold.getJSONObject("wallMs").optNullableLong("p50"),
            instrumentationLong(ARG_MAX_COLD_P50_MS),
        )
        assertBelow(
            "runtime-warm wall p50",
            runtimeWarm.getJSONObject("wallMs").optNullableLong("p50"),
            instrumentationLong(ARG_MAX_RUNTIME_WARM_P50_MS),
        )
        assertBelow(
            "runtime-warm wall p95",
            runtimeWarm.getJSONObject("wallMs").optNullableLong("p95"),
            instrumentationLong(ARG_MAX_RUNTIME_WARM_P95_MS),
        )
        assertBelow(
            "cold result-to-return p95",
            cold.getJSONObject("resultToReturnMs").optNullableLong("p95"),
            instrumentationLong(ARG_MAX_RESULT_TO_RETURN_P95_MS),
        )
        assertBelow(
            "runtime-warm result-to-return p95",
            runtimeWarm.getJSONObject("resultToReturnMs").optNullableLong("p95"),
            instrumentationLong(ARG_MAX_RESULT_TO_RETURN_P95_MS),
        )
        assertBelow(
            "app RSS delta after destroy",
            metrics.optNullableLong("appRssDeltaAfterDestroyKb"),
            instrumentationLong(ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB),
        )
        val maxServiceRssKb = instrumentationLong(ARG_MAX_SERVICE_RSS_KB)
        if (maxServiceRssKb != null) {
            val snapshots = report.getJSONArray("snapshots")
            for (index in 0 until snapshots.length()) {
                val snapshot = snapshots.getJSONObject(index)
                if (snapshot.optString("label").contains("service", ignoreCase = true)) {
                    assertBelow(
                        "${snapshot.optString("label")} RSS",
                        snapshot.optNullableLong("rssKb"),
                        maxServiceRssKb,
                    )
                }
            }
        }
    }

    private fun assertBelow(label: String, actual: Long?, threshold: Long?) {
        if (actual == null || threshold == null) {
            return
        }
        assertTrue("$label exceeded threshold: actual=$actual threshold=$threshold", actual <= threshold)
    }

    private fun assertClientResultDeliveryIsNonBlocking(run: MeasuredRun) {
        val resultToReturnMs = run.result.nativeValues[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull()
        val processSettleDeferred = run.result.nativeValues[CLIENT_PROCESS_SETTLE_DEFERRED_KEY]?.toBooleanStrictOrNull()
        assertTrue(
            "${run.label} result-to-return timing is missing: ${run.result.nativeValues}",
            resultToReturnMs != null,
        )
        assertTrue(
            "${run.label} result delivery blocked for process settling: ${resultToReturnMs}ms",
            resultToReturnMs!! <= MAX_CLIENT_RESULT_TO_RETURN_MS,
        )
        assertTrue(
            "${run.label} still uses the one-shot process-settle safety window: ${run.result.nativeValues}",
            processSettleDeferred == false,
        )
    }

    private fun assertSameProcessPoolSlot(cold: MeasuredRun, warm: MeasuredRun) {
        val coldSlot = cold.result.nativeValues[PROCESS_POOL_SELECTED_SLOT_KEY]
        val warmSlot = warm.result.nativeValues[PROCESS_POOL_SELECTED_SLOT_KEY]
        assertTrue("cold process-pool slot is missing: ${cold.result.nativeValues}", !coldSlot.isNullOrBlank())
        assertTrue("warm process-pool slot is missing: ${warm.result.nativeValues}", !warmSlot.isNullOrBlank())
        assertTrue("cold/warm runs used different process-pool slots: cold=$coldSlot warm=$warmSlot", coldSlot == warmSlot)
    }

    private fun assertRuntimeWarmReuse(cold: MeasuredRun, previous: MeasuredRun, warm: MeasuredRun) {
        val coldValues = cold.result.nativeValues
        val previousValues = previous.result.nativeValues
        val warmValues = warm.result.nativeValues
        assertTrue("cold/runtime-warm runs used different service pids: cold=${cold.result.pid} warm=${warm.result.pid}", cold.result.pid == warm.result.pid)

        val coldSessionGeneration = coldValues[PROCESS_SESSION_GENERATION_KEY]?.toLongOrNull()
        val warmSessionGeneration = warmValues[PROCESS_SESSION_GENERATION_KEY]?.toLongOrNull()
        assertTrue("cold session generation is missing: $coldValues", coldSessionGeneration != null)
        assertTrue("runtime-warm session generation is missing: $warmValues", warmSessionGeneration != null)
        assertTrue(
            "cold/runtime-warm session generations differ: cold=$coldSessionGeneration warm=$warmSessionGeneration",
            coldSessionGeneration == warmSessionGeneration,
        )

        val coldRuntimeGeneration = coldValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull()
        val warmRuntimeGeneration = warmValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull()
        assertTrue("cold process runtime generation is missing: $coldValues", coldRuntimeGeneration != null)
        assertTrue("runtime-warm process runtime generation is missing: $warmValues", warmRuntimeGeneration != null)
        assertTrue(
            "cold/runtime-warm process runtime generations differ: cold=$coldRuntimeGeneration warm=$warmRuntimeGeneration",
            coldRuntimeGeneration == warmRuntimeGeneration,
        )

        val coldInitCount = coldValues[PROCESS_RUNTIME_INIT_COUNT_KEY]?.toLongOrNull()
        val warmInitCount = warmValues[PROCESS_RUNTIME_INIT_COUNT_KEY]?.toLongOrNull()
        assertTrue("cold process runtime init count is missing: $coldValues", coldInitCount != null)
        assertTrue("runtime-warm process runtime init count is missing: $warmValues", warmInitCount != null)
        assertTrue("process runtime initialized more than once: cold=$coldInitCount warm=$warmInitCount", coldInitCount == 1L && warmInitCount == coldInitCount)

        val previousSequence = previousValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
        val warmSequence = warmValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
        assertTrue("previous process runtime execution sequence is missing: $previousValues", previousSequence != null)
        assertTrue("runtime-warm process runtime execution sequence is missing: $warmValues", warmSequence != null)
        assertTrue(
            "process runtime execution sequence did not advance once: previous=$previousSequence warm=$warmSequence",
            previousSequence != null && warmSequence == previousSequence + 1L,
        )
        assertTrue("runtime-warm execution did not reuse the process runtime: $warmValues", warmValues[PROCESS_RUNTIME_REUSED_KEY] == "true")
        assertTrue("previous execution teardown was not clean: $previousValues", previousValues[PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY] == "true")
        assertTrue("runtime-warm execution teardown was not clean: $warmValues", warmValues[PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY] == "true")
    }

    private fun performanceScript(label: String): String = """
        "nodejs";
        const memory = process.memoryUsage();
        const resource = typeof process.resourceUsage === "function" ? process.resourceUsage() : {};
        const perf = globalThis.performance;
        const payload = {
          label: "$label",
          node: process.version,
          versions: process.versions,
          arch: process.arch,
          platform: process.platform,
          uptimeMs: Math.round(process.uptime() * 1000),
          perfNowMs: perf && typeof perf.now === "function" ? Math.round(perf.now()) : null,
          memory: {
            rss: memory.rss,
            heapTotal: memory.heapTotal,
            heapUsed: memory.heapUsed,
            external: memory.external,
            arrayBuffers: memory.arrayBuffers
          },
          resourceUsage: resource
        };
        console.log("$NODE_PAYLOAD_PREFIX" + JSON.stringify(payload));
    """.trimIndent()

    private fun parseNodePayload(stdout: String): JSONObject? =
        stdout
            .lineSequence()
            .mapNotNull { line ->
                val markerIndex = line.indexOf(NODE_PAYLOAD_PREFIX)
                if (markerIndex < 0) null else line.substring(markerIndex + NODE_PAYLOAD_PREFIX.length).trim()
            }
            .firstOrNull()
            ?.let { JSONObject(it) }

    private fun processSnapshot(label: String, pid: Int): ProcessSnapshot {
        val proc = File("/proc/$pid")
        val memoryInfo = if (pid == Process.myPid()) {
            Debug.MemoryInfo().also(Debug::getMemoryInfo)
        } else {
            null
        }
        return ProcessSnapshot(
            label = label,
            pid = pid,
            readable = pid > 0 && proc.exists(),
            rssKb = readStatusLongKb(pid, "VmRSS:"),
            vmSizeKb = readStatusLongKb(pid, "VmSize:"),
            threadCount = File(proc, "task").safeCountChildren(),
            fdCount = File(proc, "fd").safeCountChildren(),
            totalPssKb = memoryInfo?.totalPss,
            dalvikPssKb = memoryInfo?.dalvikPss,
            nativePssKb = memoryInfo?.nativePss,
            otherPssKb = memoryInfo?.otherPss,
        )
    }

    private fun readStatusLongKb(pid: Int, prefix: String): Long? =
        runCatching {
            File("/proc/$pid/status")
                .readLines()
                .firstOrNull { it.startsWith(prefix) }
                ?.split(Regex("""\s+"""))
                ?.firstOrNull { it.toLongOrNull() != null }
                ?.toLongOrNull()
        }.getOrNull()

    private fun File.safeCountChildren(): Int? =
        runCatching { listFiles()?.size }.getOrNull()

    private fun deviceJson(): JSONObject =
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("brand", Build.BRAND)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("primaryAbi", Build.SUPPORTED_ABIS.firstOrNull() ?: JSONObject.NULL)

    private fun reportPullJson(): JSONObject =
        JSONObject()
            .put("deviceDir", DEVICE_REPORT_DIR)
            .put("hostDir", "app/build/reports/nodejs/perf")
            .put("gradleTask", ":app:pullNodeEmbeddedPerformanceReports")
            .put("overrideProperty", "autojs.nodejs.perf.deviceDir")

    private fun performanceSamplePlan(): PerformanceSamplePlan {
        val arguments = InstrumentationRegistry.getArguments()
        val coldCycles = arguments.getString(ARG_COLD_CYCLES)
            ?.toIntOrNull()
            ?: DEFAULT_COLD_CYCLES
        val warmSamplesPerCold = arguments.getString(ARG_RUNTIME_WARM_SAMPLES_PER_COLD)
            ?.toIntOrNull()
            ?: if (coldCycles == 1) DEFAULT_RUNTIME_WARM_SAMPLES else 1
        require(coldCycles in 1..MAX_COLD_CYCLES) {
            "$ARG_COLD_CYCLES must be in 1..$MAX_COLD_CYCLES, actual=$coldCycles"
        }
        require(warmSamplesPerCold in 1..MAX_RUNTIME_WARM_SAMPLES_PER_COLD) {
            "$ARG_RUNTIME_WARM_SAMPLES_PER_COLD must be in 1..$MAX_RUNTIME_WARM_SAMPLES_PER_COLD, actual=$warmSamplesPerCold"
        }
        return PerformanceSamplePlan(coldCycles, warmSamplesPerCold)
    }

    private fun instrumentationBoolean(name: String, defaultValue: Boolean): Boolean =
        InstrumentationRegistry.getArguments()
            .getString(name)
            ?.let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
            ?: defaultValue

    private fun instrumentationLong(name: String): Long? =
        InstrumentationRegistry.getArguments().getString(name)?.toLongOrNull()

    private fun runtimeWarmWallThreshold(): Long? =
        instrumentationLong(ARG_MAX_RUNTIME_WARM_WALL_MS)
            ?: instrumentationLong(ARG_LEGACY_MAX_WARM_WALL_MS)

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private fun Long.minusOrNull(other: Long?): Long? =
        other?.let { this - it }

    private fun Int.minusOrNull(other: Int?): Int? =
        other?.let { this - it }

    private data class PerformanceSamplePlan(
        val coldCycles: Int,
        val runtimeWarmSamplesPerCold: Int,
    ) {
        val totalExecutions: Int
            get() = coldCycles * (runtimeWarmSamplesPerCold + 1)

        fun toJson(): JSONObject =
            JSONObject()
                .put("coldCycles", coldCycles)
                .put("runtimeWarmSamplesPerCold", runtimeWarmSamplesPerCold)
                .put("coldSampleCount", coldCycles)
                .put("runtimeWarmSampleCount", coldCycles * runtimeWarmSamplesPerCold)
                .put("totalExecutions", totalExecutions)
                .put("defaultPlan", coldCycles == DEFAULT_COLD_CYCLES && runtimeWarmSamplesPerCold == DEFAULT_RUNTIME_WARM_SAMPLES)
                .put(
                    "coldPercentileNote",
                    if (coldCycles == 1) {
                        "Single cold sample; set $ARG_COLD_CYCLES to 30 for an acceptance-grade cold percentile."
                    } else {
                        "Each cold sample starts after explicit persistent-session retirement."
                    },
                )
    }

    private data class PerformanceCycle(
        val index: Int,
        val cold: MeasuredRun,
        val runtimeWarm: List<MeasuredRun>,
    ) {
        val runs: List<MeasuredRun>
            get() = listOf(cold) + runtimeWarm

        fun toJson(): JSONObject =
            JSONObject()
                .put("index", index)
                .put("cold", cold.toJson())
                .put("runtimeWarm", JSONArray(runtimeWarm.map { it.toJson() }))
    }

    private data class NativePhaseMetric(
        val key: String,
        val statusKey: String? = null,
        val measuredStatuses: Set<String> = emptySet(),
    ) {
        fun sample(nativeValues: Map<String, String>): Long? {
            val durationMs = nativeValues[key]?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val requiredStatusKey = statusKey ?: return durationMs
            val status = nativeValues[requiredStatusKey]?.lowercase()?.takeIf(String::isNotBlank)
                ?: return null
            return durationMs.takeIf { status in measuredStatuses }
        }

        fun statusExcluded(nativeValues: Map<String, String>): Boolean {
            val requiredStatusKey = statusKey ?: return false
            val status = nativeValues[requiredStatusKey]?.lowercase()?.takeIf(String::isNotBlank)
                ?: return false
            return status !in measuredStatuses
        }
    }

    private data class MeasuredRun(
        val label: String,
        val wallMs: Long,
        val result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        val nodePayload: JSONObject?,
        val clientServicePhaseTimings: Map<String, Long>,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("label", label)
                .put("wallMs", wallMs)
                .put("resultElapsedMs", result.elapsedMs)
                .put("succeeded", result.succeeded)
                .put("timedOut", result.timedOut)
                .put("processName", result.processName)
                .put("pid", result.pid)
                .put("sourceName", result.sourceName)
                .put("exitCode", result.exitCode ?: JSONObject.NULL)
                .put("errorCode", result.errorCode ?: JSONObject.NULL)
                .put("errorName", result.errorName ?: JSONObject.NULL)
                .put("errorMessage", result.errorMessage ?: JSONObject.NULL)
                .put("node", nodePayload ?: JSONObject.NULL)
                .put(
                    "phaseTimings",
                    JSONObject()
                        .put("clientService", JSONObject(clientServicePhaseTimings))
                        .put("nativeLifecycle", presentNativeTimings())
                        .put("engine", presentTimings(ENGINE_PHASE_TIMING_KEYS))
                        .put("hostPreparation", presentTimings(HOST_PREPARATION_PHASE_TIMING_KEYS)),
                )
                .put("nativeValues", JSONObject(result.nativeValues.toSortedMap()))

        private fun presentTimings(names: List<String>): JSONObject = JSONObject().apply {
            names.forEach { name ->
                result.nativeValues[name]?.toLongOrNull()?.takeIf { it >= 0L }?.let { put(name, it) }
            }
        }

        private fun presentNativeTimings(): JSONObject = JSONObject().apply {
            NATIVE_PHASE_METRICS.forEach { metric ->
                metric.sample(result.nativeValues)?.let { put(metric.key, it) }
            }
        }
    }

    private data class ProcessSnapshot(
        val label: String,
        val pid: Int,
        val readable: Boolean,
        val rssKb: Long?,
        val vmSizeKb: Long?,
        val threadCount: Int?,
        val fdCount: Int?,
        val totalPssKb: Int?,
        val dalvikPssKb: Int?,
        val nativePssKb: Int?,
        val otherPssKb: Int?,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("label", label)
                .put("pid", pid)
                .put("readable", readable)
                .put("rssKb", rssKb ?: JSONObject.NULL)
                .put("vmSizeKb", vmSizeKb ?: JSONObject.NULL)
                .put("threadCount", threadCount ?: JSONObject.NULL)
                .put("fdCount", fdCount ?: JSONObject.NULL)
                .put("totalPssKb", totalPssKb ?: JSONObject.NULL)
                .put("dalvikPssKb", dalvikPssKb ?: JSONObject.NULL)
                .put("nativePssKb", nativePssKb ?: JSONObject.NULL)
                .put("otherPssKb", otherPssKb ?: JSONObject.NULL)
    }

    private companion object {
        private const val CLIENT_RESULT_TO_RETURN_KEY = "embedded_script.client.result_to_return_ms"
        private const val CLIENT_PROCESS_SETTLE_DEFERRED_KEY = "embedded_script.client.process_settle_deferred"
        private const val PROCESS_POOL_SELECTED_SLOT_KEY = "embedded_script.process_pool.selected_slot_id"
        private const val PROCESS_SESSION_GENERATION_KEY = "embedded_script.process_session.generation"
        private const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        private const val PROCESS_RUNTIME_INIT_COUNT_KEY = "process_runtime.init_count"
        private const val PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY = "process_runtime.execution_sequence"
        private const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        private const val PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY = "process_runtime.teardown_clean"
        private const val NATIVE_PHASE_TIMING_SCHEMA = "autojs6-node-native-lifecycle-timing-v1"
        private const val NATIVE_PHASE_TIMING_SCHEMA_KEY = "embedded_script.phase_timing.schema"
        private const val NATIVE_PHASE_TIMING_STATUS_KEY = "embedded_script.phase_timing.status"
        private val NATIVE_PHASE_METRICS = listOf(
            NativePhaseMetric("timing.execution_source_build.ms"),
            NativePhaseMetric(
                NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZE_TIMING_KEY,
                NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZED_DURING_ACQUIRE_KEY,
                setOf("true"),
            ),
            NativePhaseMetric("timing.load.ms"),
            NativePhaseMetric("timing.symbols.ms"),
            NativePhaseMetric("timing.initialize.ms", "initialize.status", setOf("done")),
            NativePhaseMetric("timing.platform_create.ms", "platform.create.status", setOf("done")),
            NativePhaseMetric(
                "timing.v8_initialize_platform.ms",
                "v8.initialize_platform.status",
                setOf("done"),
            ),
            NativePhaseMetric("timing.v8_initialize.ms", "v8.initialize.status", setOf("done")),
            NativePhaseMetric("timing.uv_loop_init.ms", "uv.loop.init.status", setOf("done")),
            NativePhaseMetric("timing.allocator_create.ms", "allocator.create.status", setOf("done")),
            NativePhaseMetric("timing.isolate_create.ms", "isolate.create.status", setOf("done")),
            NativePhaseMetric("timing.isolate_data_create.ms", "isolate_data.create.status", setOf("done")),
            NativePhaseMetric("timing.environment_create.ms", "environment.create.status", setOf("done")),
            NativePhaseMetric("timing.load_environment.ms", "load_environment.status", setOf("done")),
            NativePhaseMetric("timing.bootstrap_script.ms", "bootstrap_script.status", setOf("done", "completed")),
            NativePhaseMetric(
                "timing.bootstrap.ms",
                "embedded_script.phase.bootstrap.status",
                setOf("completed"),
            ),
            NativePhaseMetric(
                "timing.module_preload.ms",
                "embedded_script.phase.module_preload.status",
                setOf("done", "completed"),
            ),
            NativePhaseMetric(
                "timing.script_execution.ms",
                "embedded_script.phase.script_execution.status",
                setOf("completed"),
            ),
            NativePhaseMetric("timing.spin_event_loop.ms", "spin_event_loop.status", setOf("done")),
            NativePhaseMetric("timing.completion_drain.ms", "completion_drain.status", setOf("done")),
            NativePhaseMetric("timing.embedded_script_result.ms", "embedded_script.status", setOf("done")),
            NativePhaseMetric("timing.js_result.ms", "js_result.status", setOf("done")),
            NativePhaseMetric("timing.output_envelope.ms", "output_envelope.status", setOf("done")),
            NativePhaseMetric("timing.stdout_capture.ms", "stdout_capture.status", setOf("done")),
            NativePhaseMetric("timing.environment_free.ms", "environment.free.status", setOf("done")),
            NativePhaseMetric("timing.isolate_data_free.ms", "isolate_data.free.status", setOf("done")),
            NativePhaseMetric("timing.isolate_unregister.ms", "isolate.unregister.status", setOf("done")),
            NativePhaseMetric("timing.isolate_dispose.ms", "isolate.dispose.status", setOf("done")),
            NativePhaseMetric("timing.uv_run.ms", "uv.run.status", setOf("done")),
            NativePhaseMetric("timing.uv_close.ms", "uv.close.status", setOf("done")),
            NativePhaseMetric("timing.uv_loop_close.ms", "uv.loop.close.status", setOf("done")),
            NativePhaseMetric("timing.teardown.ms", "teardown.status", setOf("done")),
            NativePhaseMetric(
                "timing.process_runtime_shutdown.ms",
                "process_runtime.shutdown.status",
                setOf("done"),
            ),
            NativePhaseMetric("timing.total.ms"),
        )
        private val NATIVE_PHASE_METRICS_BY_KEY = NATIVE_PHASE_METRICS.associateBy(NativePhaseMetric::key)
        private val REQUIRED_NATIVE_PHASE_KEYS = setOf(
            "timing.execution_source_build.ms",
            "timing.uv_loop_init.ms",
            "timing.isolate_create.ms",
            "timing.environment_create.ms",
            "timing.load_environment.ms",
            "timing.bootstrap.ms",
            "timing.script_execution.ms",
            "timing.spin_event_loop.ms",
            "timing.embedded_script_result.ms",
            "timing.environment_free.ms",
            "timing.isolate_dispose.ms",
            "timing.uv_loop_close.ms",
            "timing.total.ms",
        )
        private val REQUIRED_COLD_NATIVE_PHASE_KEYS = setOf(
            NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZE_TIMING_KEY,
        )
        private val ENGINE_PHASE_TIMING_KEYS = listOf(
            "timing.engine_request_preparation.ms",
            "timing.engine_commonjs_preload.ms",
            "timing.engine_encrypted_runtime_scan.ms",
        )
        private val HOST_PREPARATION_PHASE_TIMING_KEYS = listOf(
            NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY,
            "timing.compile_cache_prepare.ms",
            "timing.compile_cache_prune.ms",
        )
        private const val MAX_CLIENT_RESULT_TO_RETURN_MS = 250L
        private const val SCRIPT_TIMEOUT_MS = 10_000L
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/perf"
        private const val NODE_PAYLOAD_PREFIX = "perf.node="
        private const val ARG_FAIL_ON_THRESHOLD = "autojs.nodejs.perf.failOnThreshold"
        private const val ARG_MAX_COLD_WALL_MS = "autojs.nodejs.perf.maxColdWallMs"
        private const val ARG_MAX_RUNTIME_WARM_WALL_MS = "autojs.nodejs.perf.maxRuntimeWarmWallMs"
        private const val ARG_LEGACY_MAX_WARM_WALL_MS = "autojs.nodejs.perf.maxWarmWallMs"
        private const val ARG_MAX_COLD_P50_MS = "autojs.nodejs.perf.maxColdP50Ms"
        private const val ARG_MAX_RUNTIME_WARM_P50_MS = "autojs.nodejs.perf.maxRuntimeWarmP50Ms"
        private const val ARG_MAX_RUNTIME_WARM_P95_MS = "autojs.nodejs.perf.maxRuntimeWarmP95Ms"
        private const val ARG_MAX_RESULT_TO_RETURN_P95_MS = "autojs.nodejs.perf.maxResultToReturnP95Ms"
        private const val ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB = "autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb"
        private const val ARG_MAX_SERVICE_RSS_KB = "autojs.nodejs.perf.maxServiceRssKb"
        private const val ARG_COLD_CYCLES = "autojs.nodejs.perf.coldCycles"
        private const val ARG_RUNTIME_WARM_SAMPLES_PER_COLD = "autojs.nodejs.perf.runtimeWarmSamplesPerCold"
        private const val DEFAULT_COLD_CYCLES = 1
        private const val DEFAULT_RUNTIME_WARM_SAMPLES = 29
        private const val MAX_COLD_CYCLES = 50
        private const val MAX_RUNTIME_WARM_SAMPLES_PER_COLD = 100
    }
}
