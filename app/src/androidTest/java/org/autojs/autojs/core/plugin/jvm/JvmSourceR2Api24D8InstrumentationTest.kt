package org.autojs.autojs.core.plugin.jvm

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Exact API-24 canonical producer. The shared R1 production helper returns and logs its complete
 * runtime record inside this outer selector's call stack and instrumentation invocation. The
 * evidence harness binds that record by digest; this observation never substitutes an unrelated
 * provider-process D8 run or upgrades itself to a passed selector result.
 */
class JvmSourceR2Api24D8InstrumentationTest {
    @Test
    fun realD8RunsInsideProductionCompilerProcess() {
        assertEquals("Canonical D8 Android-host evidence is exact-API only", 24, Build.VERSION.SDK_INT)
        assertTrue(
            "Canonical D8 evidence requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext.applicationContext
        val canonicalRunId = requireBounded(arguments.getString(ARG_RUN_ID), ARG_RUN_ID, 64)
        UUID.fromString(canonicalRunId)
        val sourceTreeSha256 = requireSha256(arguments.getString(ARG_SOURCE_TREE), ARG_SOURCE_TREE)
        val hostApkSha256 = installedApkSha256(context.packageName)
        val providerApkSha256 = installedApkSha256(PROVIDER_PACKAGE)
        assertEquals(hostApkSha256, requireSha256(arguments.getString(ARG_HOST_APK), ARG_HOST_APK))
        assertEquals(providerApkSha256, requireSha256(arguments.getString(ARG_PROVIDER_APK), ARG_PROVIDER_APK))
        assertEquals(currentSignerDigests(context.packageName), currentSignerDigests(PROVIDER_PACKAGE))
        requireProductionCompilerBoundary()

        val r1 = JvmSourceR1EndToEndInstrumentationTest()
        r1.stopExistingScripts()
        val runtimeRecord: JSONObject
        try {
            // This helper performs the real ECJ -> D8 -> DEX -> disposable-worker pipeline but is
            // not a nested @Test, so the runner truthfully records only this outer exact selector.
            runtimeRecord = r1.runCanonicalProductionPipeline()
            r1.emitCanonicalRuntimeRecord(runtimeRecord)
        } finally {
            r1.stopSelectorScripts()
        }
        val runtimeRecordBytes = runtimeRecord.toString().toByteArray(Charsets.UTF_8)
        val runtimeRecordSha256 = MessageDigest.getInstance("SHA-256").digest(runtimeRecordBytes).toHex()
        val runtimeProvider = runtimeRecord.getJSONObject("provider")
        val runtimeDevice = runtimeRecord.getJSONObject("device")
        val runtimeSource = runtimeRecord.getJSONObject("source")
        val runtimePipeline = runtimeRecord.getJSONObject("pipeline")
        val runtimeProcesses = runtimeRecord.getJSONObject("processes")
        val runtimeBridge = runtimeRecord.getJSONObject("bridge")
        val providerInfo = packageInfo(PROVIDER_PACKAGE)
        assertEquals(1, runtimeRecord.getInt("schemaVersion"))
        assertEquals("R1", runtimeRecord.getString("phase"))
        assertEquals("production-provider-runtime-selector", runtimeRecord.getString("recordType"))
        assertEquals(PROVIDER_PACKAGE, runtimeProvider.getString("packageName"))
        assertEquals(PROVIDER_COMPONENT.flattenToString(), runtimeProvider.getString("component"))
        assertEquals(packageVersionCode(providerInfo), runtimeProvider.getLong("versionCode"))
        assertEquals(providerInfo.versionName, runtimeProvider.getString("versionName"))
        assertEquals(
            currentSignerDigests(PROVIDER_PACKAGE),
            runtimeProvider.getJSONArray("signerSha256").strings().toSet(),
        )
        assertEquals(24, runtimeDevice.getInt("apiLevel"))
        assertEquals(Build.FINGERPRINT, runtimeDevice.getString("buildFingerprint"))
        assertEquals(COMPILER_PROCESS, runtimeProcesses.getString("compilerProcess"))
        assertTrue(runtimeProcesses.getInt("compilerPid") > 0)
        assertTrue(runtimeProcesses.getInt("providerUid") > 0)
        assertEquals(runtimeProcesses.getInt("providerUid"), runtimeProcesses.getInt("compilerUid"))
        val providerApplicationInfo = checkNotNull(providerInfo.applicationInfo) {
            "Installed provider applicationInfo is unavailable"
        }
        assertEquals(providerApplicationInfo.uid, runtimeProcesses.getInt("providerUid"))
        assertEquals(runtimeProcesses.getInt("compilerPid"), runtimeBridge.getJSONArray("hostBridgeCallerPids").getInt(0))
        assertEquals(1, runtimeBridge.getInt("allowlistedOperationCount"))
        assertEquals(0, runtimeBridge.getInt("rejectedOperationCount"))
        assertTrue(runtimeBridge.getBoolean("rejectedRawObjectInjection"))
        assertEquals("ecj", runtimePipeline.getString("sourceCompilerFamily"))
        assertEquals("3.26.0", runtimePipeline.getString("sourceCompilerVersion"))
        assertEquals("8.13.17", runtimePipeline.getString("d8Version"))
        assertEquals(runtimeSource.getString("requestSha256"), runtimePipeline.getString("requestSha256"))
        assertEquals(runtimeSource.getString("sha256"), runtimePipeline.getString("sourceSha256"))
        assertEquals(runtimeSource.getString("resultSha256"), runtimePipeline.getString("resultSha256"))
        assertEquals("false", runtimeSource.getString("resultJson"))
        assertEquals(sha256("false".toByteArray(Charsets.UTF_8)), runtimeSource.getString("resultSha256"))
        assertTrue(runtimePipeline.getLong("dexArtifactSizeBytes") > 0L)
        requireSha256(runtimePipeline.getString("dexArtifactSha256"), "runtime dexArtifactSha256")
        requireSha256(runtimeSource.getString("requestSha256"), "runtime requestSha256")

        val observation = JSONObject()
            .put("schemaVersion", 1)
            .put("evidenceType", OUTPUT_TYPE)
            .put("canonicalRunId", canonicalRunId)
            .put("collectedAtUtc", Instant.now().toString())
            .put("sourceTreeSha256", sourceTreeSha256)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("buildFingerprint", Build.FINGERPRINT)
            .put("selector", OUTER_SELECTOR)
            .put("embeddedRuntimeRecordPrefix", R1_RUNTIME_PREFIX)
            .put("runtimeRecordSchemaVersion", runtimeRecord.getInt("schemaVersion"))
            .put("runtimeRecordPhase", runtimeRecord.getString("phase"))
            .put("runtimeRecordType", runtimeRecord.getString("recordType"))
            .put("runtimeRecordSha256", runtimeRecordSha256)
            .put("runtimeRequestSha256", runtimeSource.getString("requestSha256"))
            .put("runtimeDexSha256", runtimePipeline.getString("dexArtifactSha256"))
            .put("runtimeCompilerPid", runtimeProcesses.getInt("compilerPid"))
            .put("productionCompilerProcess", COMPILER_PROCESS)
            .put("compilerPid", runtimeProcesses.getInt("compilerPid"))
            .put("compilerProcess", runtimeProcesses.getString("compilerProcess"))
            .put("providerUid", runtimeProcesses.getInt("providerUid"))
            .put("compilerUid", runtimeProcesses.getInt("compilerUid"))
            .put("d8ExecutedOnAndroidArt", true)
            .put("d8ExecutionResult", "observed")
            .put("sourceCompilerFamily", runtimePipeline.getString("sourceCompilerFamily"))
            .put("sourceCompilerVersion", runtimePipeline.getString("sourceCompilerVersion"))
            .put("d8Version", "8.13.17")
            .put("minApi", JvmSourceContract.MIN_ANDROID_API)
            .put("adaptation", "core-library-desugaring")
            .put("producedDex", true)
            .put("producedDexSha256", runtimePipeline.getString("dexArtifactSha256"))
            .put("requestSha256", runtimeSource.getString("requestSha256"))
            .put("toolchainSha256", runtimePipeline.getString("toolchainSha256"))
            .put("runtimeLibrarySha256", runtimePipeline.getString("runtimeLibrarySha256"))
            .put("providerVersionCode", runtimeProvider.getLong("versionCode"))
            .put("providerVersionName", runtimeProvider.getString("versionName"))
            .put("providerSignerSha256", runtimeProvider.getJSONArray("signerSha256"))
            .put("hostApkSha256", hostApkSha256)
            .put("providerApkSha256", providerApkSha256)
            .put("result", "observed")
            .put("selectorCompletionMustComeFromRunner", true)
            .put("runtimeFieldsMustMatchHashedEmbeddedR1Record", true)
        val line = "$OUTPUT_PREFIX$observation"
        Log.i(TAG, line)
        println(line)
    }

    private fun requireProductionCompilerBoundary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val service = packageManager.getServiceInfo(PROVIDER_COMPONENT, PackageManager.GET_META_DATA)
        assertTrue(service.enabled)
        assertTrue(service.exported)
        assertEquals(JvmSourceContract.PLUGIN_PERMISSION, service.permission)
        assertEquals(COMPILER_PROCESS, service.processName)
        assertEquals(
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, PROVIDER_PACKAGE),
        )
    }

    private fun requireBounded(value: String?, label: String, maximumChars: Int): String {
        require(!value.isNullOrBlank() && value.length <= maximumChars) { "$label is missing or exceeds its bound" }
        return value
    }

    private fun requireSha256(value: String?, label: String): String {
        val bounded = requireBounded(value, label, 64)
        require(SHA256.matches(bounded)) { "$label is not a lowercase SHA-256" }
        return bounded
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
        return digest.digest().toHex()
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String) = InstrumentationRegistry.getInstrumentation()
        .targetContext.packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
        )

    @Suppress("DEPRECATION")
    private fun currentSignerDigests(packageName: String): Set<String> {
        val info = packageInfo(packageName)
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            checkNotNull(info.signingInfo).apkContentsSigners
        } else {
            checkNotNull(info.signatures)
        }
        return signatures.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSortedSet()
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val TAG = "JvmSourceR2Api24D8"
        const val PROVIDER_PACKAGE = "org.autojs.plugin.jvmsource.java"
        const val PROVIDER_SERVICE = "org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService"
        const val COMPILER_PROCESS = "$PROVIDER_PACKAGE:compiler"
        const val ARG_RUN_ID = "r2.api24D8.canonicalRunId"
        const val ARG_SOURCE_TREE = "r2.api24D8.sourceTreeSha256"
        const val ARG_HOST_APK = "r2.api24D8.hostApkSha256"
        const val ARG_PROVIDER_APK = "r2.api24D8.providerApkSha256"
        const val OUTPUT_TYPE = "r2-api24-d8-runtime-observation"
        const val OUTPUT_PREFIX = "JVM_SOURCE_R2_API24_D8_OBSERVATION="
        const val R1_RUNTIME_PREFIX = "JVM_SOURCE_R1_RUNTIME_EVIDENCE="
        const val OUTER_SELECTOR =
            "org.autojs.autojs.core.plugin.jvm.JvmSourceR2Api24D8InstrumentationTest#realD8RunsInsideProductionCompilerProcess"
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
        val SHA256 = Regex("[0-9a-f]{64}")
        val PROVIDER_COMPONENT = ComponentName(PROVIDER_PACKAGE, PROVIDER_SERVICE)
    }
}
