package org.autojs.autojs.core.plugin.jvm

import android.content.ComponentName
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Fail-closed lifecycle consumer scaffold. The trusted raw orchestration producer is intentionally
 * not deployed yet, so these exact selectors cannot pass or upgrade caller-supplied lifecycle JSON
 * into canonical evidence. Once that producer exists, this consumer will verify its bounded audit
 * record, restored fixture, and a fresh production-worker recovery.
 */
class JvmSourceR2LifecycleInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun stopExistingScripts() {
        service.stopAll()
        awaitNoExecutions("before R2 lifecycle selector")
    }

    @After
    fun stopSelectorScripts() {
        service.stopAll()
        awaitNoExecutions("after R2 lifecycle selector")
    }

    @Test
    fun legacyPrivateLoaderTerminationAndRecovery() = runRepresentative(
        expectedApi = 24,
        expectedSelector = "$CLASS_NAME#legacyPrivateLoaderTerminationAndRecovery",
        requiredScenarios = LEGACY_SCENARIOS,
        destructiveRestorationRequired = false,
    )

    @Test
    fun inMemoryTransitionTerminationAndRecovery() = runRepresentative(
        expectedApi = 26,
        expectedSelector = "$CLASS_NAME#inMemoryTransitionTerminationAndRecovery",
        requiredScenarios = LEGACY_SCENARIOS,
        destructiveRestorationRequired = false,
    )

    @Test
    fun fullLifecycleIdentityDriftAndRecovery() = runRepresentative(
        expectedApi = 34,
        expectedSelector = "$CLASS_NAME#fullLifecycleIdentityDriftAndRecovery",
        requiredScenarios = FULL_SCENARIOS,
        destructiveRestorationRequired = true,
    )

    private fun runRepresentative(
        expectedApi: Int,
        expectedSelector: String,
        requiredScenarios: Set<String>,
        destructiveRestorationRequired: Boolean,
    ) {
        check(TRUSTED_RAW_PRODUCER_SOURCE_STATUS == "deployed") {
            "RUNTIME_EVIDENCE_PENDING: trusted raw lifecycle orchestration producer source is not deployed"
        }
        assertEquals("Lifecycle evidence must run on its exact representative API", expectedApi, Build.VERSION.SDK_INT)
        assertTrue(
            "Canonical lifecycle evidence requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )
        val arguments = InstrumentationRegistry.getArguments()
        val inputBytes = decodeBounded(arguments.getString(ARG_INPUT_BASE64))
        assertEquals(sha256(inputBytes), requireSha256(arguments.getString(ARG_INPUT_SHA256), ARG_INPUT_SHA256))
        val inputText = inputBytes.toString(Charsets.UTF_8)
        require(!inputText.contains(PROTECTED_SOAK_SERIAL, ignoreCase = true)) {
            "Protected soak device identity must not appear in lifecycle evidence"
        }
        val input = JSONObject(inputText)
        requireExactKeys(
            input,
            "lifecycle envelope",
            "schemaVersion", "evidenceType", "canonicalRunId", "collectedAtUtc", "sourceTreeSha256",
            "apiLevel", "abi", "buildFingerprint", "selector", "requiredScenarios", "events",
            "preFixture", "postFixture", "restoreAudit",
        )
        assertEquals(1, input.getInt("schemaVersion"))
        assertEquals(INPUT_TYPE, input.getString("evidenceType"))
        val canonicalRunId = input.getString("canonicalRunId").also { UUID.fromString(it) }
        requireFreshUtc(input.getString("collectedAtUtc"))
        val sourceTreeSha256 = requireSha256(input.getString("sourceTreeSha256"), "sourceTreeSha256")
        assertEquals(expectedApi, input.getInt("apiLevel"))
        assertEquals(Build.SUPPORTED_ABIS.firstOrNull().orEmpty(), input.getString("abi"))
        assertEquals(Build.FINGERPRINT, input.getString("buildFingerprint"))
        assertEquals(expectedSelector, input.getString("selector"))

        val scenarioArray = input.getJSONArray("requiredScenarios")
        val scenarios = scenarioArray.strings()
        assertEquals(requiredScenarios, scenarios.toSet())
        assertEquals(requiredScenarios.size, scenarios.size)

        val preFixture = input.getJSONObject("preFixture")
        val postFixture = input.getJSONObject("postFixture")
        validateFixtureSnapshot(preFixture)
        validateFixtureSnapshot(postFixture)
        assertFixtureRestored(preFixture, postFixture)
        assertFixtureMatchesCurrent(postFixture)
        validateRestoreAudit(input.getJSONObject("restoreAudit"), destructiveRestorationRequired)

        val events = input.getJSONArray("events")
        assertEquals(requiredScenarios.size, events.length())
        var hardStopObserved = false
        var previousRecoveryIdentity: ProcessGeneration? = null
        val observedScenarios = linkedSetOf<String>()
        repeat(events.length()) { index ->
            val event = events.getJSONObject(index)
            requireExactKeys(
                event,
                "lifecycle event ${index + 1}",
                "sequence", "scenario", "result", "terminalReason", "compilerPid", "workerGeneration",
                "workerPid", "hardStopObserved", "hostHandlesAfterTerminal", "recoveryCompilerPid",
                "recoveryWorkerGeneration", "recoveryWorkerPid", "freshGenerationRecovery",
            )
            assertEquals(index + 1, event.getInt("sequence"))
            val scenario = event.getString("scenario")
            assertTrue("Unexpected or duplicate lifecycle scenario: $scenario", scenario in requiredScenarios && observedScenarios.add(scenario))
            assertEquals("passed", event.getString("result"))
            val terminalReason = event.getString("terminalReason")
            require(STABLE_REASON.matches(terminalReason)) { "Unstable terminal reason for $scenario" }
            assertEquals("Terminal reason does not match scenario $scenario", TERMINAL_REASONS.getValue(scenario), terminalReason)
            val compilerPid = event.getInt("compilerPid")
            val workerGeneration = event.getLong("workerGeneration")
            val workerPid = event.getInt("workerPid")
            require(compilerPid >= 0 && workerGeneration >= 0L && workerPid >= 0)
            assertEquals(0L, event.getLong("hostHandlesAfterTerminal"))
            assertTrue(event.getBoolean("freshGenerationRecovery"))
            val recovery = ProcessGeneration(
                event.getInt("recoveryCompilerPid"),
                event.getLong("recoveryWorkerGeneration"),
                event.getInt("recoveryWorkerPid"),
            )
            require(recovery.compilerPid > 0 && recovery.workerGeneration > 0L && recovery.workerPid > 0)
            if (compilerPid > 0 && workerGeneration > 0L) {
                assertTrue(
                    "Recovery reused the affected compiler/generation for $scenario",
                    recovery.compilerPid != compilerPid || recovery.workerGeneration > workerGeneration,
                )
            }
            previousRecoveryIdentity?.let { previous ->
                assertTrue(
                    "Orchestration recovery identities are not monotonic",
                    recovery.compilerPid != previous.compilerPid || recovery.workerGeneration > previous.workerGeneration,
                )
            }
            previousRecoveryIdentity = recovery
            if (event.getBoolean("hardStopObserved")) hardStopObserved = true
            if (scenario == "hardStop") assertTrue(event.getBoolean("hardStopObserved"))
        }
        assertEquals(requiredScenarios, observedScenarios)
        assertTrue("Lifecycle corpus did not observe a hard stop", hardStopObserved)
        val priorRecovery = checkNotNull(previousRecoveryIdentity)

        requireProductionProviderBoundary()
        assertEquals(PRODUCTION_PROVIDER, JvmSourceProviderSelection.getExact(context))
        val directory = File(context.cacheDir, "jvm-source-r2-recovery-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val sourceFile = File(directory, "Main.java").apply { writeText(RECOVERY_SOURCE, Charsets.UTF_8) }
        val recoveryResult = try {
            executeAndAwait { listener ->
                JvmSourceExplicitRunner.runJvmSourceExplicit(
                    service,
                    sourceFile,
                    listener,
                    ExecutionConfig(workingDirectory = directory.absolutePath),
                )
            }
        } finally {
            assertTrue("Unable to remove lifecycle recovery source fixture", directory.deleteRecursively())
        }
        recoveryResult.error?.let { throw AssertionError("Fresh production recovery failed", it) }
        val runtime = recoveryResult.result as? RemoteJvmScriptResult
            ?: throw AssertionError("Fresh recovery did not return RemoteJvmScriptResult")
        assertEquals("true", runtime.resultJson)
        assertEquals(PROVIDER_PACKAGE, runtime.providerPackageName)
        assertEquals(PRODUCTION_PROVIDER.flattenToString(), runtime.providerComponent)
        assertEquals(EXPECTED_LOADER.getValue(expectedApi), runtime.loaderKind)
        assertTrue(runtime.dexVersion in JvmDexRuntimeProfile.admittedVersions(expectedApi))
        assertTrue(runtime.workerGeneration > 0L && runtime.workerPid > 0 && runtime.compilerPid > 0)
        assertTrue(
            "Final recovery reused the orchestration compiler/generation identity",
            runtime.compilerPid != priorRecovery.compilerPid || runtime.workerGeneration > priorRecovery.workerGeneration,
        )

        val observation = JSONObject()
            .put("schemaVersion", 1)
            .put("evidenceType", OUTPUT_TYPE)
            .put("canonicalRunId", canonicalRunId)
            .put("collectedAtUtc", Instant.now().toString())
            .put("sourceTreeSha256", sourceTreeSha256)
            .put("apiLevel", expectedApi)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("buildFingerprint", Build.FINGERPRINT)
            .put("selector", expectedSelector)
            .put("requiredScenariosPassed", JSONArray(scenarios))
            .put("orchestrationInputSha256", sha256(inputBytes))
            .put("workerGeneration", runtime.workerGeneration)
            .put("workerPid", runtime.workerPid)
            .put("compilerPid", runtime.compilerPid)
            .put("terminalReason", "FRESH_GENERATION_RECOVERY")
            .put("hardStopObserved", hardStopObserved)
            .put("hostHandlesAfterTerminal", 0)
            .put("freshGenerationRecovery", true)
            .put("loaderKind", runtime.loaderKind)
            .put("dexVersion", runtime.dexVersion)
            .put("hostApkSha256", postFixture.getString("hostApkSha256"))
            .put("providerApkSha256", postFixture.getString("providerApkSha256"))
            .put("destructiveFixtureRestored", true)
            .put("result", "observed")
            .put("selectorCompletionMustComeFromRunner", true)
        val line = "$OUTPUT_PREFIX$observation"
        Log.i(TAG, line)
        println(line)
    }

    private fun validateFixtureSnapshot(snapshot: JSONObject) {
        requireExactKeys(
            snapshot,
            "fixture snapshot",
            "capturedAtUtc", "hostPackageName", "hostVersionCode", "hostVersionName", "hostApkSha256",
            "hostSignerSha256", "providerPackageName", "providerComponent", "providerVersionCode",
            "providerVersionName", "providerApkSha256", "providerSignerSha256",
            "providerApplicationEnabled", "providerServiceEnabled", "selectedComponent",
        )
        requireFreshUtc(snapshot.getString("capturedAtUtc"))
        assertEquals(context.packageName, snapshot.getString("hostPackageName"))
        assertEquals(PROVIDER_PACKAGE, snapshot.getString("providerPackageName"))
        assertEquals(PRODUCTION_PROVIDER.flattenToString(), snapshot.getString("providerComponent"))
        assertTrue(snapshot.getBoolean("providerApplicationEnabled"))
        assertTrue(snapshot.getBoolean("providerServiceEnabled"))
        assertEquals(PRODUCTION_PROVIDER.flattenToString(), snapshot.getString("selectedComponent"))
        requireSha256(snapshot.getString("hostApkSha256"), "hostApkSha256")
        requireSha256(snapshot.getString("providerApkSha256"), "providerApkSha256")
        requireSignerArray(snapshot.getJSONArray("hostSignerSha256"), "hostSignerSha256")
        requireSignerArray(snapshot.getJSONArray("providerSignerSha256"), "providerSignerSha256")
    }

    private fun assertFixtureRestored(before: JSONObject, after: JSONObject) {
        val stableKeys = before.keys().asSequence().toSet() - "capturedAtUtc"
        stableKeys.forEach { key ->
            val beforeValue = before.get(key)
            val afterValue = after.get(key)
            if (beforeValue is JSONArray && afterValue is JSONArray) {
                assertEquals("Fixture field was not restored: $key", beforeValue.strings(), afterValue.strings())
            } else {
                assertEquals("Fixture field was not restored: $key", beforeValue, afterValue)
            }
        }
    }

    private fun assertFixtureMatchesCurrent(snapshot: JSONObject) {
        val host = packageInfo(context.packageName)
        val provider = packageInfo(PROVIDER_PACKAGE)
        val serviceInfo = context.packageManager.getServiceInfo(PRODUCTION_PROVIDER, PackageManager.GET_META_DATA)
        assertEquals(packageVersionCode(host), snapshot.getLong("hostVersionCode"))
        assertEquals(host.versionName, snapshot.getString("hostVersionName"))
        assertEquals(installedApkSha256(context.packageName), snapshot.getString("hostApkSha256"))
        assertEquals(currentSignerDigests(context.packageName), snapshot.getJSONArray("hostSignerSha256").strings().toSet())
        assertEquals(packageVersionCode(provider), snapshot.getLong("providerVersionCode"))
        assertEquals(provider.versionName, snapshot.getString("providerVersionName"))
        assertEquals(installedApkSha256(PROVIDER_PACKAGE), snapshot.getString("providerApkSha256"))
        assertEquals(currentSignerDigests(PROVIDER_PACKAGE), snapshot.getJSONArray("providerSignerSha256").strings().toSet())
        assertTrue(checkNotNull(provider.applicationInfo) { "Installed provider applicationInfo is unavailable" }.enabled)
        assertTrue(serviceInfo.enabled)
        assertEquals(PRODUCTION_PROVIDER, JvmSourceProviderSelection.getExact(context))
        assertEquals(currentSignerDigests(context.packageName), currentSignerDigests(PROVIDER_PACKAGE))
    }

    private fun validateRestoreAudit(audit: JSONObject, required: Boolean) {
        requireExactKeys(
            audit,
            "restore audit",
            "snapshotCapturedBefore", "destructiveActionsPerformed", "restorationRequired",
            "providerUpdateRestored", "providerDisableRestored", "providerUninstallRestored",
            "selectionRestored", "verificationPassed",
        )
        assertTrue(audit.getBoolean("snapshotCapturedBefore"))
        assertEquals(required, audit.getBoolean("destructiveActionsPerformed"))
        assertEquals(required, audit.getBoolean("restorationRequired"))
        assertEquals(required, audit.getBoolean("providerUpdateRestored"))
        assertEquals(required, audit.getBoolean("providerDisableRestored"))
        assertEquals(required, audit.getBoolean("providerUninstallRestored"))
        assertTrue(audit.getBoolean("selectionRestored"))
        assertTrue(audit.getBoolean("verificationPassed"))
    }

    private fun requireProductionProviderBoundary() {
        val packageManager = context.packageManager
        val serviceInfo = packageManager.getServiceInfo(PRODUCTION_PROVIDER, PackageManager.GET_META_DATA)
        assertTrue(serviceInfo.enabled && serviceInfo.exported)
        assertEquals(JvmSourceContract.PLUGIN_PERMISSION, serviceInfo.permission)
        assertEquals(COMPILER_PROCESS, serviceInfo.processName)
        assertEquals(PackageManager.SIGNATURE_MATCH, packageManager.checkSignatures(context.packageName, PROVIDER_PACKAGE))
    }

    private fun executeAndAwait(start: (ScriptExecutionListener) -> ScriptExecution): ExecutionOutcome {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Any?>()
        val error = AtomicReference<Throwable?>()
        val listener = object : ScriptExecutionListener {
            override fun onStart(execution: ScriptExecution) = Unit
            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                result.set(value)
                completed.countDown()
            }
            override fun onException(execution: ScriptExecution, failure: Throwable) {
                error.set(failure)
                completed.countDown()
            }
        }
        val execution = try {
            start(listener)
        } catch (failure: Throwable) {
            return ExecutionOutcome(null, failure)
        }
        assertTrue("R2 lifecycle recovery timed out", completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val deadline = SystemClock.elapsedRealtime() + CLEANUP_TIMEOUT_MILLIS
        while (!execution.engine.isDestroyed && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue("R2 lifecycle engine was not destroyed", execution.engine.isDestroyed)
        assertNull("R2 lifecycle execution was not removed", service.getScriptExecution(execution.id))
        return ExecutionOutcome(result.get(), error.get())
    }

    private fun awaitNoExecutions(label: String) {
        val deadline = SystemClock.elapsedRealtime() + CLEANUP_TIMEOUT_MILLIS
        while (service.scriptExecutions.isNotEmpty() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue("Script executions remained $label", service.scriptExecutions.isEmpty())
    }

    private fun decodeBounded(encoded: String?): ByteArray {
        require(!encoded.isNullOrBlank() && encoded.length <= MAX_BASE64_CHARS) {
            "$ARG_INPUT_BASE64 is missing or exceeds its encoded bound"
        }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size in 1..MAX_INPUT_BYTES) { "$ARG_INPUT_BASE64 exceeds its decoded bound" }
        return bytes
    }

    private fun requireExactKeys(value: JSONObject, label: String, vararg keys: String) {
        val actualKeys = value.keys().asSequence().toSet()
        require(actualKeys == keys.toSet()) { "$label keys differ: $actualKeys" }
    }

    private fun requireFreshUtc(value: String) {
        val instant = Instant.parse(value)
        val now = Instant.now()
        require(!instant.isBefore(now.minus(Duration.ofHours(24)))) { "Lifecycle evidence is stale" }
        require(!instant.isAfter(now.plus(Duration.ofMinutes(5)))) { "Lifecycle evidence is future-dated" }
    }

    private fun requireSha256(value: String?, label: String): String {
        require(value != null && SHA256.matches(value)) { "$label is not a lowercase SHA-256" }
        return value
    }

    private fun requireSignerArray(value: JSONArray, label: String) {
        val signers = value.strings()
        require(
            signers.isNotEmpty() && signers.distinct().size == signers.size && signers == signers.sorted() &&
                signers.all(SHA256::matches),
        ) {
            "$label is empty, duplicated, or malformed"
        }
    }

    private fun installedApkSha256(packageName: String): String {
        val applicationInfo = checkNotNull(packageInfo(packageName).applicationInfo) {
            "Installed package applicationInfo is unavailable: $packageName"
        }
        val source = File(checkNotNull(applicationInfo.sourceDir) { "Installed APK path is unavailable: $packageName" })
        require(source.isFile && source.length() in 1L..MAX_APK_BYTES) { "Installed APK is absent or exceeds its bound" }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileInputStream(source).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total = Math.addExact(total, read.toLong())
                require(total <= MAX_APK_BYTES)
                digest.update(buffer, 0, read)
            }
        }
        require(total == source.length()) { "Installed APK changed while hashing" }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo = context.packageManager.getPackageInfo(
        packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
    )

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun currentSignerDigests(packageName: String): Set<String> {
        val info = packageInfo(packageName)
        val signatures = if (Build.VERSION.SDK_INT >= 28) checkNotNull(info.signingInfo).apkContentsSigners
        else checkNotNull(info.signatures)
        return signatures.map { sha256(it.toByteArray()) }.toSortedSet()
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class ProcessGeneration(val compilerPid: Int, val workerGeneration: Long, val workerPid: Int)
    private data class ExecutionOutcome(val result: Any?, val error: Throwable?)

    private companion object {
        const val CLASS_NAME = "org.autojs.autojs.core.plugin.jvm.JvmSourceR2LifecycleInstrumentationTest"
        const val TAG = "JvmSourceR2Lifecycle"
        const val PROVIDER_PACKAGE = "org.autojs.plugin.jvmsource.java"
        const val PROVIDER_SERVICE = "org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService"
        const val COMPILER_PROCESS = "$PROVIDER_PACKAGE:compiler"
        const val ARG_INPUT_BASE64 = "r2.lifecycle.orchestrationBase64"
        const val ARG_INPUT_SHA256 = "r2.lifecycle.orchestrationSha256"
        const val INPUT_TYPE = "r2-lifecycle-orchestration-input"
        const val OUTPUT_TYPE = "r2-lifecycle-runtime-observation"
        const val OUTPUT_PREFIX = "JVM_SOURCE_R2_LIFECYCLE_OBSERVATION="
        const val TRUSTED_RAW_PRODUCER_SOURCE_STATUS = "RUNTIME_EVIDENCE_PENDING"
        const val PROTECTED_SOAK_SERIAL = "QV710AF65F"
        const val MAX_INPUT_BYTES = 128 * 1024
        const val MAX_BASE64_CHARS = 192 * 1024
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
        const val EXECUTION_TIMEOUT_SECONDS = 45L
        const val CLEANUP_TIMEOUT_MILLIS = 5_000L
        val SHA256 = Regex("[0-9a-f]{64}")
        val STABLE_REASON = Regex("[A-Z][A-Z0-9_]{0,63}")
        val PRODUCTION_PROVIDER = ComponentName(PROVIDER_PACKAGE, PROVIDER_SERVICE)
        val LEGACY_SCENARIOS = setOf(
            "cancel", "timeout", "workerDeath", "hardStop", "handleCleanup", "freshGenerationRecovery",
        )
        val FULL_SCENARIOS = setOf(
            "success", "compileError", "runtimeError", "cancel", "timeout", "providerDeath", "workerDeath",
            "hostDeath", "providerUpdate", "providerDisabled", "providerUninstalled", "identityRevalidation",
            "hardStop", "handleCleanup", "freshGenerationRecovery",
        )
        val TERMINAL_REASONS = mapOf(
            "success" to "SUCCESS",
            "compileError" to "COMPILATION_ERROR",
            "runtimeError" to "RUNTIME_ERROR",
            "cancel" to "CANCELLED",
            "timeout" to "TIMEOUT",
            "providerDeath" to "PROVIDER_DIED",
            "workerDeath" to "WORKER_DIED",
            "hostDeath" to "HOST_DIED",
            "providerUpdate" to "PROVIDER_UPDATED",
            "providerDisabled" to "PROVIDER_DISABLED",
            "providerUninstalled" to "PROVIDER_UNINSTALLED",
            "identityRevalidation" to "IDENTITY_REVALIDATION_FAILED",
            "hardStop" to "HARD_STOPPED",
            "handleCleanup" to "RESOURCES_RELEASED",
            "freshGenerationRecovery" to "RECOVERED",
        )
        val EXPECTED_LOADER = mapOf(
            24 to "PRIVATE_DEX_CLASS_LOADER",
            26 to "IN_MEMORY_DEX_CLASS_LOADER",
            34 to "IN_MEMORY_DEX_CLASS_LOADER",
        )
        val RECOVERY_SOURCE = """
            public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                @Override
                public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {
                    return Boolean.TRUE;
                }
            }
        """.trimIndent()
    }
}
