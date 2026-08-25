package org.autojs.autojs.core.plugin.r8

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * G7 opt-in acceptance for ART execution, JNI linking, and an ART-produced obfuscated stack trace.
 *
 * Retrace itself deliberately remains outside the Android protocol. The invocation-bound G7
 * verifier consumes the exact mapping and stack bytes emitted in this test's private status receipt
 * and executes the pinned local R8 Retrace tool before it can publish a positive Gate.
 */
class R8CompilerRealProviderG7AndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testContext = instrumentation.context

    @Test
    fun optimizedDexExecutesOnArtLinksJniAndEmitsRetraceInput() = synchronized(SERIAL_LOCK) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "G7 instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        val invocationId = arguments.getString(ARG_INVOCATION_ID).orEmpty().trim()
        val token = runCatching { UUID.fromString(invocationId) }.getOrNull()
        assertTrue("Pass a canonical UUID through -e $ARG_INVOCATION_ID", token != null)
        assertEquals(token.toString(), invocationId)
        val optInSnapshot = OptInSnapshot.capture()
        val enableSnapshot = PluginEnableSnapshot.capture(context, REAL_PLUGIN_PACKAGE)
        val work = File(context.cacheDir, "r8-g7-runtime-$token")
        val loaderCache = File(context.codeCacheDir, "r8-g7-runtime-$token")
        assertTrue("Unable to create the G7 work directory", work.mkdir())
        assertTrue("Unable to create the G7 loader directory", loaderCache.mkdir())
        val program = File(work, "program.jar")
        val keepRules = File(work, "keep.pro")
        testContext.assets.open(PROGRAM_ASSET).use { input ->
            FileOutputStream(program).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        val baseRules = testContext.assets.open(RULE_ASSET).bufferedReader(StandardCharsets.UTF_8).use {
            it.readText()
        }
        writeDurably(keepRules, (baseRules + "# G7 invocation $token\n").toByteArray(StandardCharsets.UTF_8))

        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "r8-g7-runtime-device").apply { isDaemon = true }
        }
        var published: R8CompilerTransactionOutcome.Published? = null
        var primaryFailure: Throwable? = null
        try {
            PluginEnableStore.setEnabled(context, REAL_PLUGIN_PACKAGE, true)
            val environment = requireEnvironment()
            assertEquals(
                R8CompilerSelectionOptions(enabled = true, selectedComponent = REAL_COMPONENT),
                R8CompilerOptInStore.select(REAL_COMPONENT),
            )
            val dispatcher = AndroidR8CompilerHost.createDispatcher(context)
            val minApi = minOf(Build.VERSION.SDK_INT, R8CompilerContract.MAX_SUPPORTED_API)
            val future = executor.submit<G7Result> {
                val outcome = dispatcher.compile(spec(program, keepRules, minApi))
                assertTrue(
                    "The G7 input did not publish through the production dispatcher: ${describe(outcome)}",
                    outcome is R8CompilerHostDispatchOutcome.Published,
                )
                outcome as R8CompilerHostDispatchOutcome.Published
                val transaction = outcome.transaction
                published = transaction
                assertFalse("The invocation-unique G7 input unexpectedly resolved from cache", transaction.cacheHit)
                val artifactEvidence = verifyFiveArtifacts(transaction.artifact)
                val mapping = transaction.artifact.openArtifact(R8ArtifactRole.MAPPING_TEXT).use { it.readBytes() }
                val usage = transaction.artifact.openArtifact(R8ArtifactRole.USAGE_TEXT).use { it.readBytes() }
                val mappingText = mapping.toString(StandardCharsets.UTF_8)
                val usageText = usage.toString(StandardCharsets.UTF_8)
                assertTrue("G7 mapping does not contain the deliberately obfuscated crash class", mappingText.contains("$CRASH_CLASS_NAME -> "))
                assertTrue("G7 usage does not prove removal of the decoy", usageText.contains(REMOVED_CLASS_NAME))

                val dexLoader = AndroidClassLoader(
                    parent = requireNotNull(javaClass.classLoader),
                    cacheDir = loaderCache,
                ).loadR8CompilerDexZip(R8CompilerLoadableDexZip.from(transaction.artifact))
                val runtimeFixture = dexLoader.loadClass(RUNTIME_CLASS_NAME)
                assertEquals("g7-reflection-ok", runtimeFixture.getDeclaredMethod("reflectiveCall").invoke(null))
                assertEquals(
                    "g7-dynamic-ok",
                    runtimeFixture.getDeclaredMethod("dynamicCall", String::class.java)
                        .invoke(null, "G7DynamicTarget"),
                )
                assertEquals(
                    "g7-state:7",
                    runtimeFixture.getDeclaredMethod(
                        "serializationRoundTrip",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                    ).invoke(null, "g7-state", 7),
                )
                assertEquals(
                    "hello,g7",
                    runtimeFixture.getDeclaredMethod("scriptEntry", String::class.java).invoke(null, "g7"),
                )

                val nativeLibrary = File(testContext.applicationInfo.nativeLibraryDir, NATIVE_LIBRARY_FILE)
                assertTrue("The ABI-matched G7 JNI library is missing from the test APK", nativeLibrary.isFile)
                val nativeBridge = dexLoader.loadClass(NATIVE_CLASS_NAME)
                nativeBridge.getDeclaredMethod("load", String::class.java).invoke(null, nativeLibrary.absolutePath)
                assertEquals(
                    "The optimized native method did not link or execute",
                    42L,
                    nativeBridge.getDeclaredMethod(
                        "nativeRoundTrip",
                        Long::class.javaPrimitiveType,
                    ).invoke(null, 35L),
                )
                assertEquals(
                    "g7-jni-static",
                    nativeBridge.getDeclaredMethod("nativeStaticMessage").invoke(null),
                )

                val crashFailure = runCatching {
                    runtimeFixture.getDeclaredMethod("crashForRetrace", String::class.java)
                        .invoke(null, RETRACE_MARKER)
                }.exceptionOrNull()
                assertTrue("The optimized fixture did not throw through reflection", crashFailure is InvocationTargetException)
                val optimizedFailure = (crashFailure as InvocationTargetException).targetException
                assertTrue("The optimized fixture threw the wrong type", optimizedFailure is IllegalStateException)
                assertEquals("$RETRACE_MARKER:g7-obfuscated-crash", optimizedFailure.message)
                assertTrue("The raw ART stack already exposes the original crash class", optimizedFailure.stackTrace.none { it.className == CRASH_CLASS_NAME })
                assertClassAbsent(dexLoader, CRASH_CLASS_NAME)
                assertClassAbsent(dexLoader, REMOVED_CLASS_NAME)
                val obfuscatedStack = optimizedFailure.toRetraceInput().toByteArray(StandardCharsets.UTF_8)

                val transactionRoot = File(context.cacheDir, TRANSACTION_DIRECTORY)
                assertTrue(
                    "The production dispatcher leaked a G7 transaction workspace",
                    transactionRoot.listFiles().orEmpty().isEmpty(),
                )
                G7Result(
                    minApi = minApi,
                    artifact = transaction.artifact,
                    artifactEvidence = artifactEvidence,
                    programSha256 = program.readBytes().sha256(),
                    ruleSha256 = keepRules.readBytes().sha256(),
                    mapping = mapping,
                    obfuscatedStack = obfuscatedStack,
                    nativeLibrarySha256 = nativeLibrary.readBytes().sha256(),
                )
            }
            val result = future.get(ACCEPTANCE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            sendEvidence(environment, result, invocationId)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            executor.shutdownNow()
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching {
                assertTrue(
                    "The G7 executor did not terminate",
                    executor.awaitTermination(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            published?.artifact?.let { artifact ->
                runCatching { deleteExactCacheGeneration(artifact) }
                    .exceptionOrNull()?.let(cleanupFailures::add)
            }
            runCatching { optInSnapshot.restore() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { enableSnapshot.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue("Unable to remove the G7 work directory", work.deleteRecursively())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue("Unable to remove the G7 loader directory", loaderCache.deleteRecursively())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun requireEnvironment(): G7Environment {
        val arguments = InstrumentationRegistry.getArguments()
        assertTrue("G7 requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("G7 must target canonical AutoJs6", HOST_PACKAGE, context.packageName)
        val expectedIdentity = arguments.getString(ARG_DEVICE_IDENTITY).orEmpty().trim()
        assertTrue("Pass the exact device identity through -e $ARG_DEVICE_IDENTITY", expectedIdentity.isNotEmpty())
        val serialProperty = shell("getprop ro.serialno").trim().ifEmpty {
            shell("getprop ro.boot.serialno").trim()
        }
        assertTrue("The G7 device exposed no stable serial property", serialProperty.isNotEmpty())
        val avdName = shell("getprop ro.boot.qemu.avd_name").trim().ifEmpty {
            shell("getprop ro.kernel.qemu.avd_name").trim()
        }
        val actualIdentity = if (avdName.isEmpty()) serialProperty else "$serialProperty@$avdName"
        assertEquals("G7 instrumentation is running on a different device", expectedIdentity, actualIdentity)
        val expectedSdk = arguments.getString(ARG_DEVICE_SDK)?.toIntOrNull()
        assertEquals("Pass a numeric device SDK through -e $ARG_DEVICE_SDK", Build.VERSION.SDK_INT, expectedSdk)

        val packageManager = context.packageManager
        try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(REAL_PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real R8 provider is not installed: $REAL_PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(
            "The real R8 provider and host must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        @Suppress("DEPRECATION")
        val providerPackageInfo = packageManager.getPackageInfo(REAL_PLUGIN_PACKAGE, 0)
        val providerVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            providerPackageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            providerPackageInfo.versionCode.toLong()
        }
        val candidates = AndroidR8CompilerTransport(context)
            .discoverExactAction(R8CompilerContract.SERVICE_ACTION)
            .filter { it.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals(listOf(REAL_COMPONENT), candidates.map(R8CompilerServiceCandidate::component))
        val candidate = candidates.single()
        assertTrue(candidate.exported)
        assertTrue(candidate.applicationEnabled && candidate.serviceEnabled)
        assertEquals(R8CompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertTrue(candidate.signerSha256.isNotEmpty())
        assertTrue(candidate.sameSignerAsHost)
        assertTrue(R8CompilerSelectionPolicy.rejection(candidate) == null)
        assertNotEquals(context.applicationInfo.uid, candidate.packageUid)
        return G7Environment(actualIdentity, candidate, providerVersionCode)
    }

    private fun spec(program: File, keepRules: File, minApi: Int) = R8CompilerHostCompileSpec(
        inputs = R8CompilerHostInputs(
            program = program.asSource(),
            keepRules = listOf(keepRules.asSource()),
        ),
        minApi = minApi,
    )

    private fun File.asSource() = R8CompilerHostSource { FileInputStream(this) }

    private fun describe(outcome: R8CompilerHostDispatchOutcome): String = when (outcome) {
        is R8CompilerHostDispatchOutcome.Failed -> {
            val remote = outcome.transaction?.remoteError
            if (remote == null) {
                "Failed(stage=${outcome.stage}, cause=${outcome.cause})"
            } else {
                "Failed(stage=${outcome.stage}, transactionStage=${outcome.transaction.stage}, " +
                    "remote=${remote.code}/${remote.phase}, message=${remote.message}, " +
                    "diagnostics=${remote.diagnostics.joinToString { diagnostic ->
                        "${diagnostic.severity}:${diagnostic.code}:${diagnostic.message}"
                    }})"
            }
        }
        else -> outcome.toString()
    }

    private fun verifyFiveArtifacts(artifact: R8CompilerStoredArtifact): List<ArtifactEvidence> {
        val result = artifact.result
        assertEquals(R8ArtifactRole.values().toList(), result.artifactIdentities.map { it.role })
        val outputBundleBytes = artifact.openOutputBundle().use { it.readBytes() }
        assertEquals(result.outputBundleSizeBytes, outputBundleBytes.size.toLong())
        assertEquals(result.outputBundleSha256.toHexString(), outputBundleBytes.sha256())
        return R8ArtifactRole.values().map { role ->
            val identity = result.artifactIdentities.single { it.role == role }
            val bytes = artifact.openArtifact(role).use { it.readBytes() }
            assertEquals("$role size changed after adoption", identity.sizeBytes, bytes.size.toLong())
            assertEquals("$role digest changed after adoption", identity.contentSha256.toHexString(), bytes.sha256())
            ArtifactEvidence(role, bytes.size.toLong(), identity.contentSha256.toHexString())
        }
    }

    private fun sendEvidence(environment: G7Environment, result: G7Result, invocationId: String) {
        val artifact = result.artifact
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("roadmapStage", "G7-ART-JNI-RETRACE")
            .put("status", "PASS")
            .put("invocationId", invocationId)
            .put("deviceIdentity", environment.deviceIdentity)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("hostPackage", context.packageName)
            .put("hostVersionCode", BuildConfig.VERSION_CODE)
            .put("hostUid", context.applicationInfo.uid)
            .put("providerPackage", REAL_PLUGIN_PACKAGE)
            .put("providerComponent", "${REAL_COMPONENT.packageName}/${REAL_COMPONENT.className}")
            .put("providerVersionCode", environment.providerVersionCode)
            .put("providerUid", environment.candidate.packageUid)
            .put("providerSignerSha256", JSONArray(environment.candidate.signerSha256.sorted()))
            .put("compilerVersion", artifact.result.compilerVersion)
            .put("runtimeLibraryFingerprint", artifact.result.runtimeLibraryFingerprint.toHexString())
            .put("capabilityFingerprint", artifact.result.capabilityFingerprint.toHexString())
            .put("minApi", result.minApi)
            .put("programSha256", result.programSha256)
            .put("ruleSha256", result.ruleSha256)
            .put("outputBundleSha256", artifact.result.outputBundleSha256.toHexString())
            .put("outputBundleSizeBytes", artifact.result.outputBundleSizeBytes)
            .put("mappingSha256", result.mapping.sha256())
            .put("mappingBase64", Base64.encodeToString(result.mapping, Base64.NO_WRAP))
            .put("obfuscatedStackSha256", result.obfuscatedStack.sha256())
            .put("obfuscatedStackBase64", Base64.encodeToString(result.obfuscatedStack, Base64.NO_WRAP))
            .put("nativeLibrarySha256", result.nativeLibrarySha256)
            .put("crossApkBinder", true)
            .put("freshRemoteCompile", true)
            .put("canonicalFiveArtifacts", true)
            .put("productionR8OnlyLoader", true)
            .put("artExecuted", true)
            .put("reflectionExecuted", true)
            .put("dynamicNameExecuted", true)
            .put("serializationExecuted", true)
            .put("publicEntryExecuted", true)
            .put("jniLinked", true)
            .put("nativeInstanceResult", 42)
            .put("nativeStaticResult", "g7-jni-static")
            .put("removedDecoyAbsent", true)
            .put("rawStackIsObfuscated", true)
            .put("artifacts", JSONArray().apply {
                result.artifactEvidence.forEach { evidence ->
                    put(
                        JSONObject()
                            .put("role", evidence.role.name)
                            .put("sizeBytes", evidence.sizeBytes)
                            .put("sha256", evidence.sha256),
                    )
                }
            })
        instrumentation.sendStatus(0, Bundle().apply {
            putString(STATUS_RESULT_KEY, json.toString())
        })
    }

    private fun deleteExactCacheGeneration(artifact: R8CompilerStoredArtifact) {
        require(SHA256.matches(artifact.cacheKeySha256))
        require(UUID_TEXT.matches(artifact.generation))
        val root = File(context.cacheDir, R8CompilerSemanticCacheKey.CACHE_DIRECTORY).canonicalFile
        val generation = File(root, "entry-${artifact.cacheKeySha256}-${artifact.generation}").canonicalFile
        require(generation.parentFile == root)
        assertTrue(!generation.exists() || generation.deleteRecursively())
    }

    private fun assertClassAbsent(loader: ClassLoader, className: String) {
        val failure = runCatching { loader.loadClass(className) }.exceptionOrNull()
        assertTrue("Class should be absent from optimized DEX: $className", failure is ClassNotFoundException)
    }

    private fun Throwable.toRetraceInput(): String = buildString {
        append(this@toRetraceInput.javaClass.name)
        message?.let { append(": ").append(it) }
        append('\n')
        stackTrace.forEach { frame -> append("\tat ").append(frame).append('\n') }
    }

    private fun writeDurably(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private data class G7Environment(
        val deviceIdentity: String,
        val candidate: R8CompilerServiceCandidate,
        val providerVersionCode: Long,
    )

    private data class ArtifactEvidence(
        val role: R8ArtifactRole,
        val sizeBytes: Long,
        val sha256: String,
    )

    private data class G7Result(
        val minApi: Int,
        val artifact: R8CompilerStoredArtifact,
        val artifactEvidence: List<ArtifactEvidence>,
        val programSha256: String,
        val ruleSha256: String,
        val mapping: ByteArray,
        val obfuscatedStack: ByteArray,
        val nativeLibrarySha256: String,
    )

    private data class OptInSnapshot(
        val options: R8CompilerSelectionOptions,
        val enabledPresent: Boolean,
        val enabled: Boolean?,
        val componentPresent: Boolean,
        val component: String?,
    ) {
        fun restore() {
            val editor = Pref.get().edit()
                .remove(OPT_IN_ENABLED_KEY)
                .remove(OPT_IN_COMPONENT_KEY)
            if (enabledPresent) editor.putBoolean(OPT_IN_ENABLED_KEY, checkNotNull(enabled))
            if (componentPresent) editor.putString(OPT_IN_COMPONENT_KEY, checkNotNull(component))
            check(editor.commit())
            check(R8CompilerOptInStore.read() == options)
        }

        companion object {
            fun capture(): OptInSnapshot {
                val preferences = Pref.get()
                val enabledPresent = preferences.contains(OPT_IN_ENABLED_KEY)
                val componentPresent = preferences.contains(OPT_IN_COMPONENT_KEY)
                val enabled = preferences.all[OPT_IN_ENABLED_KEY]
                val component = preferences.all[OPT_IN_COMPONENT_KEY]
                check(!enabledPresent || enabled is Boolean)
                check(!componentPresent || component is String)
                return OptInSnapshot(
                    options = R8CompilerOptInStore.read(),
                    enabledPresent = enabledPresent,
                    enabled = enabled as? Boolean,
                    componentPresent = componentPresent,
                    component = component as? String,
                )
            }
        }
    }

    private data class PluginEnableSnapshot(
        val present: Boolean,
        val enabled: Boolean,
    ) {
        fun restore(context: Context) {
            val preferences = context.getSharedPreferences(PLUGIN_ENABLE_SP, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            if (present) editor.putBoolean(pluginEnableKey(REAL_PLUGIN_PACKAGE), enabled)
            else editor.remove(pluginEnableKey(REAL_PLUGIN_PACKAGE))
            check(editor.commit())
        }

        companion object {
            fun capture(context: Context, packageName: String): PluginEnableSnapshot {
                val preferences = context.getSharedPreferences(PLUGIN_ENABLE_SP, Context.MODE_PRIVATE)
                val key = pluginEnableKey(packageName)
                return PluginEnableSnapshot(
                    present = preferences.contains(key),
                    enabled = preferences.getBoolean(key, true),
                )
            }
        }
    }

    private companion object {
        val SERIAL_LOCK = Any()
        const val ARG_ENABLED = "autojs.r8Compiler.g7.enabled"
        const val ARG_INVOCATION_ID = "autojs.r8Compiler.g7.invocationId"
        const val ARG_DEVICE_IDENTITY = "autojs.r8Compiler.g7.deviceIdentity"
        const val ARG_DEVICE_SDK = "autojs.r8Compiler.g7.sdk"
        const val STATUS_RESULT_KEY = "autojs.r8Compiler.g7.result"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.r8compiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.R8CompilerService"
        val REAL_COMPONENT = R8CompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        const val OPT_IN_ENABLED_KEY = "r8_compiler_explicit_enabled"
        const val OPT_IN_COMPONENT_KEY = "r8_compiler_explicit_component"
        const val PLUGIN_ENABLE_SP = "plugin_center_enable_state"
        const val TRANSACTION_DIRECTORY = "r8-compiler-transactions-v1"
        const val PROGRAM_ASSET = "r8-g7-program.jar"
        const val RULE_ASSET = "r8-g7-keep.pro"
        const val NATIVE_LIBRARY_FILE = "libr8g7fixture.so"
        const val RUNTIME_CLASS_NAME = "org.autojs.fixture.r8compiler.g7.G7RuntimeFixture"
        const val NATIVE_CLASS_NAME = "org.autojs.fixture.r8compiler.g7.G7NativeBridge"
        const val CRASH_CLASS_NAME = "org.autojs.fixture.r8compiler.g7.G7ObfuscatedCrash"
        const val REMOVED_CLASS_NAME = "org.autojs.fixture.r8compiler.g7.G7RemovedDecoy"
        const val RETRACE_MARKER = "autojs6-g7-retrace"
        const val ACCEPTANCE_TIMEOUT_MINUTES = 5L
        const val EXECUTOR_SHUTDOWN_SECONDS = 5L
        val SHA256 = Regex("[0-9a-f]{64}")
        val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun pluginEnableKey(packageName: String) = "key_\$_enabled_plugin_\$$packageName"
    }
}
