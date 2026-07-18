package org.autojs.autojs.engine

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context as RhinoContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Opt-in, real-device acceptance evidence for the R4 request-preparation SLO.
 *
 * This test intentionally exercises [NativeNodeEmbeddedJavaScriptEngine] with
 * real [JavaScriptFileSource] entries. It is not part of the default connected
 * test suite because it creates a 4000-file corpus and performs 186 executions.
 */
class NodeR4AcceptanceInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        assumeTrue(
            "Pass -e $ARG_ACCEPTANCE true to run the R4 acceptance harness.",
            acceptanceEnabled(),
        )
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("R4 acceptance")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("R4 runtime-warm acceptance")
        assumeTrue(
            "R4 paired acceptance includes static ESM and requires " +
                    "BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED=true.",
            BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
        )
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        if (!acceptanceEnabled()) {
            return
        }
        runCatching { RhinoContext.exit() }
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun pairedBaselineAndLargeCorpusMeetR4WarmSlo() {
        val fixture = createFixturePair()
        val samples = mutableListOf<RawSample>()
        try {
            CASES.forEach { case ->
                fixture.roots.forEach { root ->
                    samples += runScenario(root, case)
                }
            }

            val report = buildReport(fixture, samples)
            val reportFile = writeReport(report)
            val failedCriteria = report
                .getJSONObject("criteria")
                .getJSONArray("checks")
                .objectSequence()
                .filterNot { it.getBoolean("passed") }
                .map { it.getString("id") }
                .toList()

            println("node.r4.acceptance.report=${reportFile.absolutePath}")
            assertTrue(
                "R4 acceptance failed criteria=$failedCriteria report=${reportFile.absolutePath}",
                failedCriteria.isEmpty(),
            )
        } finally {
            fixture.roots.forEach { root -> root.directory.deleteRecursively() }
        }
    }

    private fun runScenario(root: FixtureRoot, case: AcceptanceCase): List<RawSample> {
        val samples = ArrayList<RawSample>(PRIMER_COUNT + WARM_SAMPLE_COUNT)
        samples += executeFile(root, case, SamplePhase.PRIMER, 1)
        repeat(WARM_SAMPLE_COUNT) { index ->
            samples += executeFile(root, case, SamplePhase.RUNTIME_WARM, index + 1)
        }
        return samples
    }

    private fun executeFile(
        root: FixtureRoot,
        case: AcceptanceCase,
        phase: SamplePhase,
        ordinal: Int,
    ): RawSample {
        val entry = File(root.directory, case.entryRelativePath)
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val engine = NativeNodeEmbeddedJavaScriptEngine(context).apply {
            setTag(ScriptEngine.TAG_WORKING_DIRECTORY, root.directory.canonicalPath)
        }
        val execution = runCatching { engine.doExecution(JavaScriptFileSource(entry)) }
        val wallMs = TimeUnit.NANOSECONDS
            .toMillis(SystemClock.elapsedRealtimeNanos() - startedAt)
            .coerceAtLeast(0L)
        val diagnostics = engine.lastRequestPreparationDiagnostics
        val nativeValues = engine.lastEmbeddedScriptResultNativeValues
        return RawSample(
            sequenceIndex = 0,
            rootProfile = root.profile,
            case = case,
            phase = phase,
            ordinal = ordinal,
            entryRelativePath = case.entryRelativePath,
            wallMs = wallMs,
            preparationMs = diagnostics.elapsedMs,
            executionSucceeded = execution.exceptionOrNull() == null && execution.getOrNull() == 0,
            executionReturnValue = execution.getOrNull()?.toString(),
            executionExceptionClass = execution.exceptionOrNull()?.javaClass?.name,
            pid = engine.lastEmbeddedScriptResultPid.takeIf { it > 0 }
                ?: nativeValues[PROCESS_RUNTIME_PID_KEY]?.toIntOrNull()?.takeIf { it > 0 },
            diagnostics = diagnostics,
            nativeValues = nativeValues,
        )
    }

    private fun buildReport(fixture: FixturePair, unorderedSamples: List<RawSample>): JSONObject {
        val samples = unorderedSamples.mapIndexed { index, sample ->
            sample.copy(sequenceIndex = index + 1)
        }
        val scenarios = CASES.flatMap { case ->
            RootProfile.entries.map { profile ->
                val warm = samples.filter {
                    it.case == case && it.rootProfile == profile && it.phase == SamplePhase.RUNTIME_WARM
                }
                ScenarioDistribution(
                    rootProfile = profile,
                    case = case,
                    sampleCount = warm.size,
                    wallMs = Distribution.from(warm.map(RawSample::wallMs)),
                    preparationMs = Distribution.from(warm.map(RawSample::preparationMs)),
                    zeroScanSampleCount = warm.count(RawSample::hasZeroLegacyDirectoryScan),
                )
            }
        }
        val comparisons = CASES.map { case ->
            val baseline = scenarios.single {
                it.case == case && it.rootProfile == RootProfile.BASELINE
            }
            val large = scenarios.single {
                it.case == case && it.rootProfile == RootProfile.LARGE_CORPUS
            }
            CorpusComparison(
                case = case,
                baselinePreparationP95Ms = baseline.preparationMs.p95,
                largePreparationP95Ms = large.preparationMs.p95,
            )
        }
        val continuity = CASES.flatMap { case ->
            RootProfile.entries.map { profile ->
                ScenarioContinuity.from(
                    rootProfile = profile,
                    case = case,
                    samples = samples.filter { it.case == case && it.rootProfile == profile },
                )
            }
        }
        val checks = buildCriteria(fixture, samples, scenarios, comparisons, continuity)
        val availableResultKeys = samples.flatMap { it.nativeValues.keys }.toSortedSet()
        val resultKeyAvailability = JSONObject()
            .put("requiredIdentityKeys", JSONArray(RESULT_IDENTITY_KEYS))
            .put("nativePidKey", PROCESS_RUNTIME_PID_KEY)
            .put("pidSourcePreference", "engine_result_then_native_values")
            .put("pidPresentSampleCount", samples.count { it.pid != null })
            .put("pidMissingSampleIndices", JSONArray(samples.filter { it.pid == null }.map { it.sequenceIndex }))
            .put("availableRequiredKeys", JSONArray(RESULT_IDENTITY_KEYS.filter(availableResultKeys::contains)))
            .put("missingRequiredKeys", JSONArray(RESULT_IDENTITY_KEYS.filterNot(availableResultKeys::contains)))
            .put("availableNativeValueKeys", JSONArray(availableResultKeys.toList()))

        return JSONObject()
            .put("schema", REPORT_SCHEMA)
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("device", deviceJson())
            .put("build", buildJson())
            .put("fixture", fixture.toJson())
            .put(
                "samplePlan",
                JSONObject()
                    .put("primerCountPerScenario", PRIMER_COUNT)
                    .put("runtimeWarmSampleCountPerScenario", WARM_SAMPLE_COUNT)
                    .put("scenarioCount", scenarios.size)
                    .put("expectedTotalExecutions", scenarios.size * (PRIMER_COUNT + WARM_SAMPLE_COUNT))
                    .put("actualTotalExecutions", samples.size)
                    .put("percentileMethod", "nearest_rank"),
            )
            .put(
                "distributions",
                JSONObject()
                    .put("scenarios", JSONArray(scenarios.map(ScenarioDistribution::toJson)))
                    .put("largeVsBaseline", JSONArray(comparisons.map(CorpusComparison::toJson)))
                    .put("runtimeContinuity", JSONArray(continuity.map(ScenarioContinuity::toJson))),
            )
            .put(
                "criteria",
                JSONObject()
                    .put("overallPassed", checks.all(Criterion::passed))
                    .put("checks", JSONArray(checks.map(Criterion::toJson))),
            )
            .put("resultKeyAvailability", resultKeyAvailability)
            .put("rawSamples", JSONArray(samples.map(RawSample::toJson)))
    }

    private fun buildCriteria(
        fixture: FixturePair,
        samples: List<RawSample>,
        scenarios: List<ScenarioDistribution>,
        comparisons: List<CorpusComparison>,
        continuity: List<ScenarioContinuity>,
    ): List<Criterion> {
        val expectedSampleCount = CASES.size * RootProfile.entries.size * (PRIMER_COUNT + WARM_SAMPLE_COUNT)

        return listOf(
            Criterion(
                id = "fixture_large_corpus_file_count",
                threshold = ">=$MIN_LARGE_CORPUS_FILE_COUNT unrelated JS-like files",
                observed = fixture.large.unrelatedFileCount.toString(),
                passed = fixture.large.unrelatedFileCount >= MIN_LARGE_CORPUS_FILE_COUNT,
            ),
            Criterion(
                id = "sample_plan_complete",
                threshold = "$expectedSampleCount total executions; $WARM_SAMPLE_COUNT warm samples per scenario",
                observed = "total=${samples.size},scenarioWarm=${scenarios.map(ScenarioDistribution::sampleCount)}",
                passed = samples.size == expectedSampleCount && scenarios.all { it.sampleCount == WARM_SAMPLE_COUNT },
            ),
            Criterion(
                id = "all_executions_succeeded",
                threshold = "all engine file executions return 0 without exception",
                observed = "succeeded=${samples.count(RawSample::executionSucceeded)}/${samples.size}",
                passed = samples.all(RawSample::executionSucceeded),
            ),
            Criterion(
                id = "on_demand_provider_decision",
                threshold = "every sample reports decision=$ON_DEMAND_PROVIDER_DECISION",
                observed = "matched=${samples.count(RawSample::hasOnDemandProviderDecision)}/${samples.size}",
                passed = samples.all(RawSample::hasOnDemandProviderDecision),
            ),
            Criterion(
                id = "zero_legacy_directory_scan",
                threshold = "engine/runtime legacy scan flags=false and all traversal/read counters=0 for every sample",
                observed = "matched=${samples.count(RawSample::hasZeroLegacyDirectoryScan)}/${samples.size}",
                passed = samples.all(RawSample::hasZeroLegacyDirectoryScan),
            ),
            Criterion(
                id = "process_pool_slot_constant",
                threshold = "one non-empty $PROCESS_POOL_SELECTED_SLOT_KEY within each scenario",
                observed = continuity.joinToString { "${it.id}=${it.poolSlots}" },
                passed = continuity.all(ScenarioContinuity::poolSlotConstant),
            ),
            Criterion(
                id = "process_session_generation_constant",
                threshold = "one numeric $PROCESS_SESSION_GENERATION_KEY within each scenario",
                observed = continuity.joinToString { "${it.id}=${it.sessionGenerations}" },
                passed = continuity.all(ScenarioContinuity::sessionGenerationConstant),
            ),
            Criterion(
                id = "process_runtime_generation_constant",
                threshold = "one numeric $PROCESS_RUNTIME_GENERATION_KEY within each scenario",
                observed = continuity.joinToString { "${it.id}=${it.runtimeGenerations}" },
                passed = continuity.all(ScenarioContinuity::runtimeGenerationConstant),
            ),
            Criterion(
                id = "process_pid_constant",
                threshold = "one present PID within each scenario",
                observed = continuity.joinToString { "${it.id}=${it.pids}" },
                passed = continuity.all(ScenarioContinuity::pidConstant),
            ),
            Criterion(
                id = "process_runtime_execution_sequence_consecutive",
                threshold = "$PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY advances by exactly 1 within each scenario",
                observed = continuity.joinToString {
                    "${it.id}=${it.executionSequences.firstOrNull()}..${it.executionSequences.lastOrNull()}"
                },
                passed = continuity.all(ScenarioContinuity::executionSequenceConsecutive),
            ),
            Criterion(
                id = "runtime_warm_reused",
                threshold = "$PROCESS_RUNTIME_REUSED_KEY=true for all measured warm samples in each scenario",
                observed = continuity.joinToString { "${it.id}=${it.reusedWarmSampleCount}/$WARM_SAMPLE_COUNT" },
                passed = continuity.all(ScenarioContinuity::warmSamplesReused),
            ),
            Criterion(
                id = "request_preparation_p95",
                threshold = "nearest-rank p95 <=${REQUEST_PREPARATION_P95_MAX_MS}ms for every scenario",
                observed = scenarios.joinToString { "${it.rootProfile.id}/${it.case.id}=${it.preparationMs.p95}ms" },
                passed = scenarios.all { it.preparationMs.p95 <= REQUEST_PREPARATION_P95_MAX_MS },
            ),
            Criterion(
                id = "large_vs_baseline_request_preparation_p95_delta",
                threshold = "absolute large-vs-baseline p95 delta <=${LARGE_CORPUS_PREPARATION_DELTA_MAX_MS}ms per case",
                observed = comparisons.joinToString { "${it.case.id}=${it.absoluteDeltaMs}ms" },
                passed = comparisons.all { it.absoluteDeltaMs <= LARGE_CORPUS_PREPARATION_DELTA_MAX_MS },
            ),
            Criterion(
                id = "runtime_warm_wall_p95",
                threshold = "nearest-rank p95 <=${RUNTIME_WARM_WALL_P95_MAX_MS}ms for every scenario",
                observed = scenarios.joinToString { "${it.rootProfile.id}/${it.case.id}=${it.wallMs.p95}ms" },
                passed = scenarios.all { it.wallMs.p95 <= RUNTIME_WARM_WALL_P95_MAX_MS },
            ),
        )
    }

    private fun createFixturePair(): FixturePair {
        val fixtureId = System.nanoTime()
        val baseline = createFixtureRoot(
            profile = RootProfile.BASELINE,
            directory = File(context.cacheDir, "node-r4-acceptance-$fixtureId-baseline"),
            unrelatedFileCount = 0,
        )
        return try {
            val large = createFixtureRoot(
                profile = RootProfile.LARGE_CORPUS,
                directory = File(context.cacheDir, "node-r4-acceptance-$fixtureId-large"),
                unrelatedFileCount = MIN_LARGE_CORPUS_FILE_COUNT,
            )
            FixturePair(baseline = baseline, large = large)
        } catch (throwable: Throwable) {
            baseline.directory.deleteRecursively()
            throw throwable
        }
    }

    private fun createFixtureRoot(
        profile: RootProfile,
        directory: File,
        unrelatedFileCount: Int,
    ): FixtureRoot {
        check(directory.mkdirs()) { "Failed to create R4 acceptance root: ${directory.absolutePath}" }
        writeAcceptanceEntries(directory)
        repeat(unrelatedFileCount) { index ->
            val branch = "unrelated/shard-${index % 64}/nested-${index % 8}"
            val extension = UNRELATED_EXTENSIONS[index % UNRELATED_EXTENSIONS.size]
            val file = File(directory, "$branch/candidate-$index.$extension")
            file.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
            val source = if (extension == "json") {
                "{\"unrelated\":$index}\n"
            } else {
                "// R4 unrelated JS-like fixture $index\n"
            }
            file.writeText(source, StandardCharsets.UTF_8)
        }
        return FixtureRoot(
            profile = profile,
            directory = directory.canonicalFile,
            unrelatedFileCount = unrelatedFileCount,
        )
    }

    private fun writeAcceptanceEntries(root: File) {
        File(root, AcceptanceCase.MODULE_FREE.entryRelativePath).writeText(
            """
            "nodejs";
            const values = [1, 2, 3];
            let total = 0;
            function add(value) { total += value; }
            values.forEach(add);
            const expression = total * 2;
            if (expression !== 12) throw new Error("module-free expression failed");
            console.log("r4.module-free.first=" + expression);
            console.log("r4.module-free.second=true");
            Promise.resolve().then(async () => {
              await Promise.resolve();
              if (total !== 6) throw new Error("module-free async failed");
            }).catch((error) => setImmediate(() => { throw error; }));
            setTimeout(() => {}, 0);
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(root, "single-cjs-child.cjs").writeText(
            "module.exports = { value: 42 };\n",
            StandardCharsets.UTF_8,
        )
        File(root, AcceptanceCase.STATIC_COMMON_JS.entryRelativePath).writeText(
            """
            "nodejs";
            const child = require("./single-cjs-child.cjs");
            if (child.value !== 42) throw new Error("single static CommonJS failed");
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(root, "single-esm-child.mjs").writeText(
            "export const value = 42;\n",
            StandardCharsets.UTF_8,
        )
        File(root, AcceptanceCase.STATIC_ESM.entryRelativePath).writeText(
            """
            import { value } from "./single-esm-child.mjs";
            if (value !== 42) throw new Error("single static ESM failed");
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
    }

    private fun writeReport(report: JSONObject): File {
        val reportDirectory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "nodejs/r4-acceptance",
        )
        check(reportDirectory.mkdirs() || reportDirectory.isDirectory) {
            "Failed to create R4 acceptance report directory: ${reportDirectory.absolutePath}"
        }
        val reportFile = File(reportDirectory, "node-r4-acceptance-${System.currentTimeMillis()}.json")
        reportFile.writeText(report.toString(2), StandardCharsets.UTF_8)
        return reportFile
    }

    private fun deviceJson(): JSONObject = JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("brand", Build.BRAND)
        .put("model", Build.MODEL)
        .put("device", Build.DEVICE)
        .put("product", Build.PRODUCT)
        .put("sdkInt", Build.VERSION.SDK_INT)
        .put("release", Build.VERSION.RELEASE)
        .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))

    private fun buildJson(): JSONObject = JSONObject()
        .put("applicationId", BuildConfig.APPLICATION_ID)
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("buildType", BuildConfig.BUILD_TYPE)
        .put("nodeReleaseFlavor", BuildConfig.NODEJS_RELEASE_FLAVOR)
        .put("embeddedScriptExecutionEnabled", BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED)
        .put("persistentRuntimeBuildEnabled", BuildConfig.NODEJS_EMBEDDED_PERSISTENT_RUNTIME_ENABLED)
        .put("persistentRuntimeEffectiveEnabled", NodeEmbeddedProcessSessionManager.isEnabled())
        .put("esmExperimentalEnabled", BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED)
        .put("acceptanceArgument", acceptanceEnabled())

    private fun acceptanceEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString(ARG_ACCEPTANCE)?.equals("true", ignoreCase = true) == true

    private enum class RootProfile(val id: String) {
        BASELINE("baseline"),
        LARGE_CORPUS("large_corpus"),
    }

    private enum class AcceptanceCase(val id: String, val entryRelativePath: String) {
        MODULE_FREE("module_free", "module-free-entry.cjs"),
        STATIC_COMMON_JS("single_static_commonjs", "single-static-cjs-entry.cjs"),
        STATIC_ESM("single_static_esm", "single-static-esm-entry.mjs"),
    }

    private enum class SamplePhase(val id: String) {
        PRIMER("primer"),
        RUNTIME_WARM("runtime_warm"),
    }

    private data class FixtureRoot(
        val profile: RootProfile,
        val directory: File,
        val unrelatedFileCount: Int,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("profile", profile.id)
            .put("unrelatedJsLikeFileCount", unrelatedFileCount)
            .put("fixtureEntryCount", CASES.size)
            .put("fixtureDependencyCount", 2)
            .put("totalFixtureFileCount", unrelatedFileCount + CASES.size + 2)
    }

    private data class FixturePair(
        val baseline: FixtureRoot,
        val large: FixtureRoot,
    ) {
        val roots: List<FixtureRoot>
            get() = listOf(baseline, large)

        fun toJson(): JSONObject = JSONObject()
            .put("pairedRoots", JSONArray(roots.map(FixtureRoot::toJson)))
            .put("cases", JSONArray(CASES.map { case ->
                JSONObject()
                    .put("id", case.id)
                    .put("entryRelativePath", case.entryRelativePath)
                    .put("dependencyCount", if (case == AcceptanceCase.MODULE_FREE) 0 else 1)
            }))
            .put("unrelatedExtensions", JSONArray(UNRELATED_EXTENSIONS))
    }

    private data class RawSample(
        val sequenceIndex: Int,
        val rootProfile: RootProfile,
        val case: AcceptanceCase,
        val phase: SamplePhase,
        val ordinal: Int,
        val entryRelativePath: String,
        val wallMs: Long,
        val preparationMs: Long,
        val executionSucceeded: Boolean,
        val executionReturnValue: String?,
        val executionExceptionClass: String?,
        val pid: Int?,
        val diagnostics: NativeNodeEmbeddedJavaScriptEngine.RequestPreparationDiagnostics,
        val nativeValues: Map<String, String>,
    ) {
        val hasOnDemandProviderDecision: Boolean
            get() = diagnostics.prepared &&
                    diagnostics.encryptedRuntimeModuleScan.decision == ON_DEMAND_PROVIDER_DECISION &&
                    nativeValues[ENGINE_SCAN_DECISION_KEY] == ON_DEMAND_PROVIDER_DECISION

        val hasZeroLegacyDirectoryScan: Boolean
            get() {
                val scan = diagnostics.encryptedRuntimeModuleScan
                return !scan.required &&
                        !scan.performed &&
                        scan.status == "skipped" &&
                        scan.examinedPathCount == 0 &&
                        scan.walkedFileCount == 0 &&
                        scan.candidateFileCount == 0 &&
                        scan.headerProbeCount == 0 &&
                        scan.headerBytesRead == 0L &&
                        scan.headerReadFailureCount == 0 &&
                        scan.fullFileReadCount == 0 &&
                        scan.fullFileBytesRead == 0L &&
                        scan.fullFileReadFailureCount == 0 &&
                        scan.prunedDirectoryCount == 0 &&
                        scan.outsidePathRejectedCount == 0 &&
                        scan.duplicateCanonicalFileCount == 0 &&
                        scan.walkFailureCount == 0 &&
                        scan.oversizedCiphertextCount == 0 &&
                        scan.cleartextSizeLimitRejectedCount == 0 &&
                        scan.decryptFailureCount == 0 &&
                        scan.encryptedModuleCount == 0 &&
                        scan.retainedEncryptedModuleCount == 0 &&
                        scan.encryptedModuleSourceBytes == 0L &&
                        scan.encryptedModuleBudgetRejectedCount == 0 &&
                        !scan.traversalLimitReached &&
                        !scan.candidateLimitReached &&
                        scan.identityReadFailureCount == 0 &&
                        scan.identityMismatchCount == 0 &&
                        !scan.outputCountBudgetExhausted &&
                        !scan.outputBytesBudgetExhausted &&
                        nativeValues[ENGINE_SCAN_REQUIRED_KEY] == "false" &&
                        nativeValues[ENGINE_SCAN_PERFORMED_KEY] == "false" &&
                        nativeValues[ENGINE_LEGACY_SCAN_KEY] == "false" &&
                        nativeValues[ENGINE_SCAN_WALKED_FILE_COUNT_KEY] == "0" &&
                        nativeValues[ENGINE_SCAN_CANDIDATE_FILE_COUNT_KEY] == "0" &&
                        nativeValues[MODULE_PROVIDER_LEGACY_SCAN_KEY] == "false"
            }

        fun toJson(): JSONObject {
            val scan = diagnostics.encryptedRuntimeModuleScan
            val requiredValues = RESULT_IDENTITY_KEYS.associateWith { nativeValues[it] }
            return JSONObject()
                .put("sequenceIndex", sequenceIndex)
                .put("rootProfile", rootProfile.id)
                .put("case", case.id)
                .put("phase", phase.id)
                .put("ordinal", ordinal)
                .put("entryRelativePath", entryRelativePath)
                .put("wallMs", wallMs)
                .put("requestPreparationMs", preparationMs)
                .put("executionSucceeded", executionSucceeded)
                .put("executionReturnValue", executionReturnValue ?: JSONObject.NULL)
                .put("executionExceptionClass", executionExceptionClass ?: JSONObject.NULL)
                .put("onDemandProviderDecision", hasOnDemandProviderDecision)
                .put("zeroLegacyDirectoryScan", hasZeroLegacyDirectoryScan)
                .put(
                    "requestPreparation",
                    JSONObject()
                        .put("prepared", diagnostics.prepared)
                        .put("rootProfile", rootProfile.id)
                        .put("entryRelativePath", entryRelativePath)
                        .put("sourceFileName", File(diagnostics.sourceName).name)
                        .put("esmEntry", diagnostics.isEsmEntry)
                        .put("elapsedMs", diagnostics.elapsedMs)
                        .put(
                            "commonJs",
                            JSONObject()
                                .put("moduleCount", diagnostics.commonJs.moduleCount)
                                .put("staticRequireCount", diagnostics.commonJs.staticRequireCount)
                                .put(
                                    "dynamicRequireIgnoredCount",
                                    diagnostics.commonJs.dynamicRequireIgnoredCount,
                                )
                                .put(
                                    "unresolvedStaticRequireCount",
                                    diagnostics.commonJs.unresolvedStaticRequireCount,
                                )
                                .put("sourceLevelEsmSyntaxCount", diagnostics.commonJs.sourceLevelEsmSyntaxCount)
                                .put("dynamicImportCount", diagnostics.commonJs.dynamicImportCount)
                                .put("trivialModuleFreeEntry", diagnostics.commonJs.trivialModuleFreeEntry)
                                .put("complete", diagnostics.commonJs.complete)
                                .put("incompleteReason", diagnostics.commonJs.incompleteReason ?: JSONObject.NULL)
                                .put("elapsedMs", diagnostics.commonJs.elapsedMs)
                                .put(
                                    "moduleSourceFileNames",
                                    JSONArray(diagnostics.commonJs.moduleSourceNames.map { File(it).name }),
                                ),
                        ),
                )
                .put(
                    "legacyDirectoryScan",
                    JSONObject()
                        .put("required", scan.required)
                        .put("performed", scan.performed)
                        .put("decision", scan.decision)
                        .put("status", scan.status)
                        .put("engineNativeFlag", nativeValues[ENGINE_LEGACY_SCAN_KEY] ?: JSONObject.NULL)
                        .put("runtimeNativeFlag", nativeValues[MODULE_PROVIDER_LEGACY_SCAN_KEY] ?: JSONObject.NULL)
                        .put("elapsedMs", scan.elapsedMs)
                        .put("examinedPathCount", scan.examinedPathCount)
                        .put("walkedFileCount", scan.walkedFileCount)
                        .put("candidateFileCount", scan.candidateFileCount)
                        .put("headerProbeCount", scan.headerProbeCount)
                        .put("headerBytesRead", scan.headerBytesRead)
                        .put("headerReadFailureCount", scan.headerReadFailureCount)
                        .put("fullFileReadCount", scan.fullFileReadCount)
                        .put("fullFileBytesRead", scan.fullFileBytesRead)
                        .put("fullFileReadFailureCount", scan.fullFileReadFailureCount)
                        .put("prunedDirectoryCount", scan.prunedDirectoryCount)
                        .put("outsidePathRejectedCount", scan.outsidePathRejectedCount)
                        .put("duplicateCanonicalFileCount", scan.duplicateCanonicalFileCount)
                        .put("walkFailureCount", scan.walkFailureCount)
                        .put("oversizedCiphertextCount", scan.oversizedCiphertextCount)
                        .put("cleartextSizeLimitRejectedCount", scan.cleartextSizeLimitRejectedCount)
                        .put("decryptFailureCount", scan.decryptFailureCount)
                        .put("encryptedModuleCount", scan.encryptedModuleCount)
                        .put("retainedEncryptedModuleCount", scan.retainedEncryptedModuleCount)
                        .put("encryptedModuleSourceBytes", scan.encryptedModuleSourceBytes)
                        .put("encryptedModuleBudgetRejectedCount", scan.encryptedModuleBudgetRejectedCount)
                        .put("traversalLimitReached", scan.traversalLimitReached)
                        .put("candidateLimitReached", scan.candidateLimitReached)
                        .put("identityReadFailureCount", scan.identityReadFailureCount)
                        .put("identityMismatchCount", scan.identityMismatchCount)
                        .put("outputCountBudgetExhausted", scan.outputCountBudgetExhausted)
                        .put("outputBytesBudgetExhausted", scan.outputBytesBudgetExhausted),
                )
                .put(
                    "runtimeIdentity",
                    JSONObject()
                        .put("values", JSONObject(requiredValues))
                        .put("pid", pid ?: JSONObject.NULL)
                        .put(
                            "missingKeys",
                            JSONArray(RESULT_IDENTITY_KEYS.filter { nativeValues[it].isNullOrBlank() }),
                        ),
                )
                .put(
                    "moduleProvider",
                    JSONObject()
                        .put("requestCount", nativeLongOrNull(MODULE_PROVIDER_REQUEST_COUNT_KEY))
                        .put("resolvedSourceCount", nativeLongOrNull(MODULE_PROVIDER_RESOLVED_COUNT_KEY))
                        .put("decryptedSourceCount", nativeLongOrNull(MODULE_PROVIDER_DECRYPTED_COUNT_KEY))
                        .put("sourceBytes", nativeLongOrNull(MODULE_PROVIDER_SOURCE_BYTES_KEY))
                        .put("elapsedMs", nativeLongOrNull(MODULE_PROVIDER_ELAPSED_MS_KEY))
                        .put("denialReason", nativeValues[MODULE_PROVIDER_DENIAL_REASON_KEY] ?: JSONObject.NULL)
                        .put("legacyDirectoryScanRan", nativeValues[MODULE_PROVIDER_LEGACY_SCAN_KEY] ?: JSONObject.NULL),
                )
        }

        private fun nativeLongOrNull(key: String): Any = nativeValues[key]?.toLongOrNull() ?: JSONObject.NULL
    }

    private data class Distribution(
        val count: Int,
        val minimum: Long,
        val p50: Long,
        val p95: Long,
        val maximum: Long,
        val mean: Double,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("count", count)
            .put("minimum", minimum)
            .put("p50", p50)
            .put("p95", p95)
            .put("maximum", maximum)
            .put("mean", mean)
            .put("percentileMethod", "nearest_rank")

        companion object {
            fun from(values: List<Long>): Distribution {
                require(values.isNotEmpty()) { "Cannot build a distribution without samples." }
                val sorted = values.sorted()
                return Distribution(
                    count = sorted.size,
                    minimum = sorted.first(),
                    p50 = nearestRank(sorted, 0.50),
                    p95 = nearestRank(sorted, 0.95),
                    maximum = sorted.last(),
                    mean = sorted.average(),
                )
            }

            private fun nearestRank(sortedValues: List<Long>, percentile: Double): Long {
                val oneBasedRank = ceil(percentile * sortedValues.size).toInt().coerceIn(1, sortedValues.size)
                return sortedValues[oneBasedRank - 1]
            }
        }
    }

    private data class ScenarioDistribution(
        val rootProfile: RootProfile,
        val case: AcceptanceCase,
        val sampleCount: Int,
        val wallMs: Distribution,
        val preparationMs: Distribution,
        val zeroScanSampleCount: Int,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("rootProfile", rootProfile.id)
            .put("case", case.id)
            .put("sampleCount", sampleCount)
            .put("wallMs", wallMs.toJson())
            .put("requestPreparationMs", preparationMs.toJson())
            .put("zeroLegacyDirectoryScanSampleCount", zeroScanSampleCount)
    }

    private data class ScenarioContinuity(
        val rootProfile: RootProfile,
        val case: AcceptanceCase,
        val sampleCount: Int,
        val primerSampleCount: Int,
        val warmSampleCount: Int,
        val poolSlotPresentSampleCount: Int,
        val poolSlots: List<String>,
        val sessionGenerationPresentSampleCount: Int,
        val sessionGenerations: List<Long>,
        val runtimeGenerationPresentSampleCount: Int,
        val runtimeGenerations: List<Long>,
        val pidPresentSampleCount: Int,
        val pids: List<Int>,
        val executionSequencePresentSampleCount: Int,
        val executionSequences: List<Long>,
        val reusedWarmSampleCount: Int,
    ) {
        val id: String
            get() = "${rootProfile.id}/${case.id}"

        val poolSlotConstant: Boolean
            get() = poolSlotPresentSampleCount == sampleCount && poolSlots.size == 1

        val sessionGenerationConstant: Boolean
            get() = sessionGenerationPresentSampleCount == sampleCount && sessionGenerations.size == 1

        val runtimeGenerationConstant: Boolean
            get() = runtimeGenerationPresentSampleCount == sampleCount && runtimeGenerations.size == 1

        val pidConstant: Boolean
            get() = pidPresentSampleCount == sampleCount && pids.size == 1

        val executionSequenceConsecutive: Boolean
            get() = executionSequencePresentSampleCount == sampleCount &&
                    executionSequences.zipWithNext().all { (previous, next) -> next == previous + 1L }

        val warmSamplesReused: Boolean
            get() = warmSampleCount == WARM_SAMPLE_COUNT && reusedWarmSampleCount == warmSampleCount

        fun toJson(): JSONObject = JSONObject()
            .put("rootProfile", rootProfile.id)
            .put("case", case.id)
            .put("sampleCount", sampleCount)
            .put("primerSampleCount", primerSampleCount)
            .put("warmSampleCount", warmSampleCount)
            .put("poolSlotPresentSampleCount", poolSlotPresentSampleCount)
            .put("poolSlots", JSONArray(poolSlots))
            .put("poolSlotConstant", poolSlotConstant)
            .put("sessionGenerationPresentSampleCount", sessionGenerationPresentSampleCount)
            .put("sessionGenerations", JSONArray(sessionGenerations))
            .put("sessionGenerationConstant", sessionGenerationConstant)
            .put("runtimeGenerationPresentSampleCount", runtimeGenerationPresentSampleCount)
            .put("runtimeGenerations", JSONArray(runtimeGenerations))
            .put("runtimeGenerationConstant", runtimeGenerationConstant)
            .put("pidPresentSampleCount", pidPresentSampleCount)
            .put("pids", JSONArray(pids))
            .put("pidConstant", pidConstant)
            .put("executionSequencePresentSampleCount", executionSequencePresentSampleCount)
            .put("executionSequenceFirst", executionSequences.firstOrNull() ?: JSONObject.NULL)
            .put("executionSequenceLast", executionSequences.lastOrNull() ?: JSONObject.NULL)
            .put("executionSequenceConsecutive", executionSequenceConsecutive)
            .put("reusedWarmSampleCount", reusedWarmSampleCount)
            .put("warmSamplesReused", warmSamplesReused)

        companion object {
            fun from(
                rootProfile: RootProfile,
                case: AcceptanceCase,
                samples: List<RawSample>,
            ): ScenarioContinuity {
                val ordered = samples.sortedBy(RawSample::sequenceIndex)
                val poolSlots = ordered.mapNotNull {
                    it.nativeValues[PROCESS_POOL_SELECTED_SLOT_KEY]?.takeIf(String::isNotBlank)
                }
                val sessionGenerations = ordered.mapNotNull {
                    it.nativeValues[PROCESS_SESSION_GENERATION_KEY]?.toLongOrNull()
                }
                val runtimeGenerations = ordered.mapNotNull {
                    it.nativeValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull()
                }
                val pids = ordered.mapNotNull(RawSample::pid)
                val executionSequences = ordered.mapNotNull {
                    it.nativeValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
                }
                val warm = ordered.filter { it.phase == SamplePhase.RUNTIME_WARM }
                return ScenarioContinuity(
                    rootProfile = rootProfile,
                    case = case,
                    sampleCount = ordered.size,
                    primerSampleCount = ordered.count { it.phase == SamplePhase.PRIMER },
                    warmSampleCount = warm.size,
                    poolSlotPresentSampleCount = poolSlots.size,
                    poolSlots = poolSlots.distinct(),
                    sessionGenerationPresentSampleCount = sessionGenerations.size,
                    sessionGenerations = sessionGenerations.distinct(),
                    runtimeGenerationPresentSampleCount = runtimeGenerations.size,
                    runtimeGenerations = runtimeGenerations.distinct(),
                    pidPresentSampleCount = pids.size,
                    pids = pids.distinct(),
                    executionSequencePresentSampleCount = executionSequences.size,
                    executionSequences = executionSequences,
                    reusedWarmSampleCount = warm.count {
                        it.nativeValues[PROCESS_RUNTIME_REUSED_KEY] == "true"
                    },
                )
            }
        }
    }

    private data class CorpusComparison(
        val case: AcceptanceCase,
        val baselinePreparationP95Ms: Long,
        val largePreparationP95Ms: Long,
    ) {
        val signedDeltaMs: Long
            get() = largePreparationP95Ms - baselinePreparationP95Ms

        val absoluteDeltaMs: Long
            get() = abs(signedDeltaMs)

        fun toJson(): JSONObject = JSONObject()
            .put("case", case.id)
            .put("baselineRequestPreparationP95Ms", baselinePreparationP95Ms)
            .put("largeCorpusRequestPreparationP95Ms", largePreparationP95Ms)
            .put("signedDeltaMs", signedDeltaMs)
            .put("absoluteDeltaMs", absoluteDeltaMs)
            .put("thresholdMs", LARGE_CORPUS_PREPARATION_DELTA_MAX_MS)
            .put("passed", absoluteDeltaMs <= LARGE_CORPUS_PREPARATION_DELTA_MAX_MS)
    }

    private data class Criterion(
        val id: String,
        val threshold: String,
        val observed: String,
        val passed: Boolean,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("threshold", threshold)
            .put("observed", observed)
            .put("passed", passed)
    }

    private fun JSONArray.objectSequence(): Sequence<JSONObject> = sequence {
        for (index in 0 until length()) {
            yield(getJSONObject(index))
        }
    }

    private companion object {
        const val ARG_ACCEPTANCE = "autojs.nodejs.r4.acceptance"
        const val REPORT_SCHEMA = "autojs6-node-r4-acceptance-v1"
        const val PRIMER_COUNT = 1
        const val WARM_SAMPLE_COUNT = 30
        const val MIN_LARGE_CORPUS_FILE_COUNT = 4_000
        const val REQUEST_PREPARATION_P95_MAX_MS = 50L
        const val LARGE_CORPUS_PREPARATION_DELTA_MAX_MS = 20L
        const val RUNTIME_WARM_WALL_P95_MAX_MS = 400L
        const val ON_DEMAND_PROVIDER_DECISION = "on_demand_provider"

        const val ENGINE_SCAN_DECISION_KEY = "embedded_script.engine_encrypted_runtime_scan.decision"
        const val ENGINE_SCAN_REQUIRED_KEY = "embedded_script.engine_encrypted_runtime_scan.required"
        const val ENGINE_SCAN_PERFORMED_KEY = "embedded_script.engine_encrypted_runtime_scan.performed"
        const val ENGINE_LEGACY_SCAN_KEY =
            "embedded_script.engine_encrypted_runtime_scan.legacy_directory_scan_ran"
        const val ENGINE_SCAN_WALKED_FILE_COUNT_KEY =
            "embedded_script.engine_encrypted_runtime_scan.walked_file_count"
        const val ENGINE_SCAN_CANDIDATE_FILE_COUNT_KEY =
            "embedded_script.engine_encrypted_runtime_scan.candidate_file_count"
        const val MODULE_PROVIDER_LEGACY_SCAN_KEY =
            "embedded_script.module_provider.legacy_directory_scan_ran"
        const val MODULE_PROVIDER_REQUEST_COUNT_KEY = "embedded_script.module_provider.request_count"
        const val MODULE_PROVIDER_RESOLVED_COUNT_KEY = "embedded_script.module_provider.resolved_source_count"
        const val MODULE_PROVIDER_DECRYPTED_COUNT_KEY = "embedded_script.module_provider.decrypted_source_count"
        const val MODULE_PROVIDER_SOURCE_BYTES_KEY = "embedded_script.module_provider.source_bytes"
        const val MODULE_PROVIDER_ELAPSED_MS_KEY = "embedded_script.module_provider.elapsed_ms"
        const val MODULE_PROVIDER_DENIAL_REASON_KEY = "embedded_script.module_provider.denial_reason"

        const val PROCESS_POOL_SELECTED_SLOT_KEY = "embedded_script.process_pool.selected_slot_id"
        const val PROCESS_SESSION_GENERATION_KEY = "embedded_script.process_session.generation"
        const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        const val PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY = "process_runtime.execution_sequence"
        const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        const val PROCESS_RUNTIME_PID_KEY = "process_runtime.pid"

        val RESULT_IDENTITY_KEYS = listOf(
            PROCESS_POOL_SELECTED_SLOT_KEY,
            PROCESS_SESSION_GENERATION_KEY,
            PROCESS_RUNTIME_GENERATION_KEY,
            PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY,
            PROCESS_RUNTIME_REUSED_KEY,
        )
        val CASES = AcceptanceCase.entries.toList()
        val UNRELATED_EXTENSIONS = listOf("js", "cjs", "mjs", "ts", "json")
    }
}
