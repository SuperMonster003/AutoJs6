package org.autojs.autojs.core.plugin.r8

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Opt-in cross-APK acceptance for the independently installed, locally signed R8 provider.
 *
 * This test goes through the production exact-component transport and transaction runner. It
 * proves one fresh Binder/PFD compile, host adoption of every canonical artifact, exact semantic
 * cache reuse, and production R8-only class-loader execution. It is deliberately skipped unless
 * the producer names the exact device identity and SDK through instrumentation arguments.
 */
class R8CompilerRealProviderAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun realProviderPublishesFiveArtifactsCacheHitsAndLoadsFixture() = synchronized(SERIAL_LOCK) {
        assumeTrue(
            "Real R8 provider instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        val optInSnapshot = OptInSnapshot.capture()
        val enableSnapshot = PluginEnableSnapshot.capture(context, REAL_PLUGIN_PACKAGE)
        val token = UUID.randomUUID()
        val work = File(context.cacheDir, "r8-real-provider-$token")
        val loaderCache = File(context.codeCacheDir, "r8-real-provider-$token")
        assertTrue("Unable to create the R8 acceptance work directory", work.mkdir())
        assertTrue("Unable to create the R8 acceptance loader directory", loaderCache.mkdir())
        val program = File(work, "program.jar")
        val keepRules = File(work, "keep.pro")
        writeDurably(program, uniqueProgramJar())
        writeDurably(keepRules, KEEP_RULES.toByteArray(StandardCharsets.UTF_8))

        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "r8-real-provider-device").apply { isDaemon = true }
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

            val future = executor.submit<AcceptanceResult> {
                val firstOutcome = dispatcher.compile(spec(program, keepRules, minApi))
                assertTrue(
                    "The fresh real-R8 input did not publish through the production dispatcher: " +
                        describe(firstOutcome),
                    firstOutcome is R8CompilerHostDispatchOutcome.Published,
                )
                firstOutcome as R8CompilerHostDispatchOutcome.Published
                val first = firstOutcome.transaction
                published = first
                assertFalse("The unique first R8 input unexpectedly resolved from cache", first.cacheHit)
                val artifactEvidence = verifyFiveArtifacts(first.artifact)

                var secondOutcome: R8CompilerHostDispatchOutcome? = null
                val answer = R8CompilerExplicitRuntimeRoute.dispatch(
                    programPath = program.absolutePath,
                    keepRulePaths = arrayOf(keepRules.absolutePath),
                    orderedClasspathPaths = emptyArray(),
                    consumerRulePaths = emptyArray(),
                    consumerRuleClasspathOrdinals = IntArray(0),
                    minApi = minApi,
                    resolvePath = { path -> path },
                    compile = { repeatSpec ->
                        dispatcher.compile(repeatSpec).also { secondOutcome = it }
                    },
                    loadPublished = { loadable ->
                        val fixture = AndroidClassLoader(
                            parent = requireNotNull(javaClass.classLoader),
                            cacheDir = loaderCache,
                        ).loadR8CompilerDexZip(loadable).loadClass(FIXTURE_CLASS_NAME)
                        assertEquals(FIXTURE_CLASS_NAME, fixture.name)
                        fixture.getDeclaredMethod("answer").invoke(null) as Int
                    },
                )
                assertEquals("The R8-optimized fixture returned an unexpected value", 42, answer)
                val second = secondOutcome
                assertTrue(
                    "The production R8-only load route did not publish: $second",
                    second is R8CompilerHostDispatchOutcome.Published,
                )
                second as R8CompilerHostDispatchOutcome.Published
                assertTrue("The identical production R8 load did not use the verified cache", second.transaction.cacheHit)
                assertEquals(first.artifact.cacheKeySha256, second.transaction.artifact.cacheKeySha256)
                assertEquals(first.artifact.generation, second.transaction.artifact.generation)
                assertEquals(
                    first.artifact.result.artifactIdentities.map { it.contentSha256.toHexString() },
                    second.transaction.artifact.result.artifactIdentities.map { it.contentSha256.toHexString() },
                )

                val transactionRoot = File(context.cacheDir, TRANSACTION_DIRECTORY)
                assertTrue(
                    "The production dispatcher leaked an R8 transaction workspace",
                    transactionRoot.listFiles().orEmpty().isEmpty(),
                )
                AcceptanceResult(
                    minApi = minApi,
                    artifact = first.artifact,
                    artifactEvidence = artifactEvidence,
                    answer = answer,
                )
            }
            val result = future.get(ACCEPTANCE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            sendEvidence(environment, result)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            executor.shutdownNow()
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching {
                assertTrue(
                    "The R8 acceptance executor did not terminate",
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
                assertTrue("Unable to remove the R8 acceptance work directory", work.deleteRecursively())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue("Unable to remove the R8 acceptance loader directory", loaderCache.deleteRecursively())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun requireEnvironment(): AcceptanceEnvironment {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real R8 provider instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Real R8 provider instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Real R8 instrumentation must target canonical AutoJs6", HOST_PACKAGE, context.packageName)

        val expectedIdentity = arguments.getString(ARG_DEVICE_IDENTITY).orEmpty().trim()
        assertTrue("Pass the exact device identity through -e $ARG_DEVICE_IDENTITY", expectedIdentity.isNotEmpty())
        val serialProperty = shell("getprop ro.serialno").trim().ifEmpty {
            shell("getprop ro.boot.serialno").trim()
        }
        assertTrue("The device exposed no stable serial property", serialProperty.isNotEmpty())
        val avdName = shell("getprop ro.boot.qemu.avd_name").trim().ifEmpty {
            shell("getprop ro.kernel.qemu.avd_name").trim()
        }
        val actualIdentity = if (avdName.isEmpty()) serialProperty else "$serialProperty@$avdName"
        assertEquals("Instrumentation is running on a different device", expectedIdentity, actualIdentity)
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

        val candidates = AndroidR8CompilerTransport(context)
            .discoverExactAction(R8CompilerContract.SERVICE_ACTION)
            .filter { it.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals(
            "The real R8 package must expose exactly the expected service",
            listOf(REAL_COMPONENT),
            candidates.map(R8CompilerServiceCandidate::component),
        )
        val candidate = candidates.single()
        assertEquals(R8CompilerContract.SERVICE_ACTION, candidate.declaredAction)
        assertTrue("The real R8 provider is not exported", candidate.exported)
        assertTrue("The real R8 provider is disabled", candidate.applicationEnabled && candidate.serviceEnabled)
        assertEquals(R8CompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertTrue("The real R8 provider has no signer identity", candidate.signerSha256.isNotEmpty())
        assertTrue("The host/provider signer check did not pass", candidate.sameSignerAsHost)
        assertTrue(
            "The real R8 provider is rejected by the production selection policy",
            R8CompilerSelectionPolicy.rejection(candidate) == null,
        )
        assertNotEquals("The R8 provider unexpectedly shares the host UID", context.applicationInfo.uid, candidate.packageUid)
        return AcceptanceEnvironment(actualIdentity, candidate)
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
            if (role == R8ArtifactRole.DEX_ZIP ||
                role == R8ArtifactRole.MAPPING_TEXT ||
                role == R8ArtifactRole.RETRACE_METADATA
            ) {
                assertTrue("$role must not be empty", bytes.isNotEmpty())
            }
            ArtifactEvidence(role, bytes.size.toLong(), identity.contentSha256.toHexString())
        }
    }

    private fun sendEvidence(environment: AcceptanceEnvironment, result: AcceptanceResult) {
        val artifact = result.artifact
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("roadmapStage", "G6-REAL-R8-HAPPY")
            .put("status", "PASS")
            .put("deviceIdentity", environment.deviceIdentity)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("hostPackage", context.packageName)
            .put("hostVersionCode", BuildConfig.VERSION_CODE)
            .put("hostUid", context.applicationInfo.uid)
            .put("providerPackage", REAL_PLUGIN_PACKAGE)
            .put("providerComponent", "${REAL_COMPONENT.packageName}/${REAL_COMPONENT.className}")
            .put("providerUid", environment.candidate.packageUid)
            .put("providerVersionCode", environment.candidate.versionCode)
            .put("providerSignerSha256", JSONArray(environment.candidate.signerSha256.sorted()))
            .put("compilerVersion", artifact.result.compilerVersion)
            .put("minApi", result.minApi)
            .put("crossApkBinder", true)
            .put("canonicalPfdTransaction", true)
            .put("freshRemoteCompile", true)
            .put("verifiedCacheHit", true)
            .put("fiveArtifactsVerified", true)
            .put("productionR8OnlyLoader", true)
            .put("fixtureClass", FIXTURE_CLASS_NAME)
            .put("fixtureAnswer", result.answer)
            .put("cacheKeySha256", artifact.cacheKeySha256)
            .put("generation", artifact.generation)
            .put("outputBundleSizeBytes", artifact.result.outputBundleSizeBytes)
            .put("outputBundleSha256", artifact.result.outputBundleSha256.toHexString())
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
        require(SHA256.matches(artifact.cacheKeySha256)) { "Invalid R8 cache key during cleanup" }
        require(UUID_TEXT.matches(artifact.generation)) { "Invalid R8 cache generation during cleanup" }
        val root = File(context.cacheDir, R8CompilerSemanticCacheKey.CACHE_DIRECTORY).canonicalFile
        val generation = File(
            root,
            "entry-${artifact.cacheKeySha256}-${artifact.generation}",
        ).canonicalFile
        require(generation.parentFile == root) { "R8 cache cleanup escaped its private root" }
        assertTrue(
            "Unable to remove the exact R8 acceptance cache generation",
            !generation.exists() || generation.deleteRecursively(),
        )
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

    private fun uniqueProgramJar(): ByteArray {
        val classBytes = fixtureClassBytes("RealR8ProviderFixture-${UUID.randomUUID()}.java")
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeStoredEntry(FIXTURE_CLASS_ENTRY, classBytes)
            }
            output.toByteArray()
        }
    }

    /** A valid Java 8 class with a public constructor and static answer() method. */
    private fun fixtureClassBytes(sourceFileName: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(0xcafebabe.toInt())
            output.writeShort(0)
            output.writeShort(52)
            output.writeShort(14)
            output.writeUtf8Constant(FIXTURE_INTERNAL_NAME) // #1
            output.writeByte(7); output.writeShort(1) // #2 Class
            output.writeUtf8Constant("java/lang/Object") // #3
            output.writeByte(7); output.writeShort(3) // #4 Class
            output.writeUtf8Constant("<init>") // #5
            output.writeUtf8Constant("()V") // #6
            output.writeUtf8Constant("Code") // #7
            output.writeByte(12); output.writeShort(5); output.writeShort(6) // #8 NameAndType
            output.writeByte(10); output.writeShort(4); output.writeShort(8) // #9 Methodref
            output.writeUtf8Constant("answer") // #10
            output.writeUtf8Constant("()I") // #11
            output.writeUtf8Constant("SourceFile") // #12
            output.writeUtf8Constant(sourceFileName) // #13

            output.writeShort(0x0031) // public final super
            output.writeShort(2)
            output.writeShort(4)
            output.writeShort(0) // interfaces
            output.writeShort(0) // fields
            output.writeShort(2) // methods

            output.writeShort(0x0001) // public <init>()V
            output.writeShort(5)
            output.writeShort(6)
            output.writeShort(1)
            output.writeShort(7)
            output.writeInt(17)
            output.writeShort(1)
            output.writeShort(1)
            output.writeInt(5)
            output.write(byteArrayOf(0x2a, 0xb7.toByte(), 0x00, 0x09, 0xb1.toByte()))
            output.writeShort(0)
            output.writeShort(0)

            output.writeShort(0x0009) // public static answer()I
            output.writeShort(10)
            output.writeShort(11)
            output.writeShort(1)
            output.writeShort(7)
            output.writeInt(15)
            output.writeShort(1)
            output.writeShort(0)
            output.writeInt(3)
            output.write(byteArrayOf(0x10, 42, 0xac.toByte()))
            output.writeShort(0)
            output.writeShort(0)

            output.writeShort(1) // class attributes
            output.writeShort(12)
            output.writeInt(2)
            output.writeShort(13)
        }
        bytes.toByteArray()
    }

    private fun DataOutputStream.writeUtf8Constant(value: String) {
        writeByte(1)
        writeUTF(value)
    }

    private fun ZipOutputStream.writeStoredEntry(name: String, bytes: ByteArray) {
        val checksum = CRC32().apply { update(bytes) }.value
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = checksum
            time = ZIP_DOS_EPOCH_MILLIS
            extra = byteArrayOf()
            comment = null
        })
        write(bytes)
        closeEntry()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private data class AcceptanceEnvironment(
        val deviceIdentity: String,
        val candidate: R8CompilerServiceCandidate,
    )

    private data class ArtifactEvidence(
        val role: R8ArtifactRole,
        val sizeBytes: Long,
        val sha256: String,
    )

    private data class AcceptanceResult(
        val minApi: Int,
        val artifact: R8CompilerStoredArtifact,
        val artifactEvidence: List<ArtifactEvidence>,
        val answer: Int,
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
            check(editor.commit()) { "Unable to restore the R8 opt-in preference state" }
            check(R8CompilerOptInStore.read() == options) { "Restored R8 opt-in state changed" }
        }

        companion object {
            fun capture(): OptInSnapshot {
                val preferences = Pref.get()
                val enabledPresent = preferences.contains(OPT_IN_ENABLED_KEY)
                val componentPresent = preferences.contains(OPT_IN_COMPONENT_KEY)
                val enabled = preferences.all[OPT_IN_ENABLED_KEY]
                val component = preferences.all[OPT_IN_COMPONENT_KEY]
                check(!enabledPresent || enabled is Boolean) { "Unexpected R8 enabled preference type" }
                check(!componentPresent || component is String) { "Unexpected R8 component preference type" }
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
            check(editor.commit()) { "Unable to restore the plugin enable state" }
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
        const val ARG_ENABLED = "autojs.r8Compiler.realProvider.enabled"
        const val ARG_DEVICE_IDENTITY = "autojs.r8Compiler.realProvider.deviceIdentity"
        const val ARG_DEVICE_SDK = "autojs.r8Compiler.realProvider.sdk"
        const val STATUS_RESULT_KEY = "autojs.r8Compiler.realProvider.result"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.r8compiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.R8CompilerService"
        val REAL_COMPONENT = R8CompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        const val OPT_IN_ENABLED_KEY = "r8_compiler_explicit_enabled"
        const val OPT_IN_COMPONENT_KEY = "r8_compiler_explicit_component"
        const val PLUGIN_ENABLE_SP = "plugin_center_enable_state"
        const val TRANSACTION_DIRECTORY = "r8-compiler-transactions-v1"
        const val FIXTURE_INTERNAL_NAME = "org/autojs/fixture/r8compiler/RealProviderFixture"
        const val FIXTURE_CLASS_ENTRY = "$FIXTURE_INTERNAL_NAME.class"
        const val FIXTURE_CLASS_NAME = "org.autojs.fixture.r8compiler.RealProviderFixture"
        const val KEEP_RULES =
            "-keep class org.autojs.fixture.r8compiler.RealProviderFixture { public *; }\n" +
                "-dontwarn java.lang.Object\n"
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L
        const val ACCEPTANCE_TIMEOUT_MINUTES = 5L
        const val EXECUTOR_SHUTDOWN_SECONDS = 5L
        val SHA256 = Regex("[0-9a-f]{64}")
        val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun pluginEnableKey(packageName: String) = "key_\$_enabled_plugin_\$$packageName"
    }
}
