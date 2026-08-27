package org.autojs.autojs.core.plugin.r8

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** G10 opt-in device Gate for the public Rhino retrace entry and mapping provenance checks. */
class R8CompilerRealProviderG10AndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testContext = instrumentation.context
    private val scriptEngineService
        get() = AutoJs.instance.scriptEngineService

    @Test
    fun productionRhinoRetracesRealArtCrashAndRejectsTamperedMapping() = synchronized(SERIAL_LOCK) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "G10 instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        val invocationId = arguments.getString(ARG_INVOCATION_ID).orEmpty().trim()
        val token = runCatching { UUID.fromString(invocationId) }.getOrNull()
        assertTrue("Pass a canonical UUID through -e $ARG_INVOCATION_ID", token != null)
        assertEquals(token.toString(), invocationId)

        val optInSnapshot = OptInSnapshot.capture()
        val enableSnapshot = PluginEnableSnapshot.capture(context, REAL_PLUGIN_PACKAGE)
        val work = File(context.cacheDir, "r8-g10-device-$token")
        val loaderCache = File(context.codeCacheDir, "r8-g10-device-$token")
        assertTrue("Unable to create the G10 work directory", work.mkdir())
        assertTrue("Unable to create the G10 loader directory", loaderCache.mkdir())
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
        writeDurably(keepRules, (baseRules + "# G10 invocation $token\n").toByteArray(StandardCharsets.UTF_8))

        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "r8-g10-device-gate").apply { isDaemon = true }
        }
        var published: R8CompilerTransactionOutcome.Published? = null
        var primaryFailure: Throwable? = null
        try {
            scriptEngineService.stopAll()
            awaitNoRegisteredEngines("before G10 device Gate")
            PluginEnableStore.setEnabled(context, REAL_PLUGIN_PACKAGE, true)
            val environment = requireEnvironment()
            assertEquals(
                R8CompilerSelectionOptions(enabled = true, selectedComponent = REAL_COMPONENT),
                R8CompilerOptInStore.select(REAL_COMPONENT),
            )
            val future = executor.submit<G10Result> {
                val dispatcher = AndroidR8CompilerHost.createDispatcher(context)
                val minApi = minOf(Build.VERSION.SDK_INT, R8CompilerContract.MAX_SUPPORTED_API)
                val outcome = dispatcher.compile(spec(program, keepRules, minApi))
                assertTrue(
                    "The G10 fixture did not publish through the production dispatcher: ${describe(outcome)}",
                    outcome is R8CompilerHostDispatchOutcome.Published,
                )
                outcome as R8CompilerHostDispatchOutcome.Published
                val transaction = outcome.transaction
                published = transaction
                assertFalse("The invocation-unique G10 input unexpectedly resolved from cache", transaction.cacheHit)

                val reportDirectory = R8CompilerArtifactExporter.export(
                    transaction.artifact,
                    File(work, "reports"),
                )
                assertEquals(
                    setOf("mapping.txt", "seeds.txt", "usage.txt", "retrace-metadata.bin"),
                    reportDirectory.listFiles().orEmpty().map { it.name }.toSet(),
                )
                assertFalse("The G10 report export leaked DEX", File(reportDirectory, "dex.zip").exists())
                val mappingFile = File(reportDirectory, "mapping.txt")
                val metadataFile = File(reportDirectory, "retrace-metadata.bin")
                val mapping = mappingFile.readBytes()
                val metadata = metadataFile.readBytes()
                val mappingText = mapping.toString(StandardCharsets.UTF_8)
                val obfuscatedClass = mappingText.lineSequence()
                    .single { it.startsWith("$CRASH_CLASS_NAME -> ") }
                    .substringAfter(" -> ")
                    .removeSuffix(":")
                assertTrue("The G10 mapping did not obfuscate the crash class", obfuscatedClass != CRASH_CLASS_NAME)
                assertTrue("The G10 mapping lacks a verifiable hash", mappingText.contains("# pg_map_hash: SHA-256 "))

                val dexLoader = AndroidClassLoader(
                    parent = requireNotNull(javaClass.classLoader),
                    cacheDir = loaderCache,
                ).loadR8CompilerDexZip(R8CompilerLoadableDexZip.from(transaction.artifact))
                val runtimeFixture = dexLoader.loadClass(RUNTIME_CLASS_NAME)
                val marker = "$RETRACE_MARKER_PREFIX$token"
                val crashFailure = runCatching {
                    runtimeFixture.getDeclaredMethod("crashForRetrace", String::class.java)
                        .invoke(null, marker)
                }.exceptionOrNull()
                assertTrue("The optimized G10 fixture did not throw through reflection", crashFailure is InvocationTargetException)
                val optimizedFailure = (crashFailure as InvocationTargetException).targetException
                assertTrue("The optimized G10 fixture threw the wrong type", optimizedFailure is IllegalStateException)
                assertEquals("$marker:g7-obfuscated-crash", optimizedFailure.message)
                val obfuscatedStack = optimizedFailure.toRetraceInput().toByteArray(StandardCharsets.UTF_8)
                val obfuscatedStackText = obfuscatedStack.toString(StandardCharsets.UTF_8)
                assertTrue(
                    "The ART stack does not contain the obfuscated crash frame",
                    obfuscatedStackText.contains("\tat $obfuscatedClass."),
                )
                assertFalse(
                    "The raw ART stack already exposes the original crash class",
                    obfuscatedStackText.contains(CRASH_CLASS_NAME),
                )
                val stackFile = File(work, "obfuscated-stack.txt")
                writeDurably(stackFile, obfuscatedStack)

                val positiveScript = runRhino(
                    work,
                    "g10-retrace-positive.js",
                    """
                    "rhino";
                    let stack = files.read("./obfuscated-stack.txt");
                    let retraced = runtime.retraceR8Stack(
                        stack,
                        "./reports/mapping.txt",
                        "./reports/retrace-metadata.bin"
                    );
                    files.write("./retraced-stack.txt", retraced);
                    files.write("./positive-complete.marker", "ok");
                    """.trimIndent(),
                )
                assertTrue(
                    "runtime.retraceR8Stack did not execute through production Rhino",
                    RhinoJavaScriptEngine::class.java.isAssignableFrom(positiveScript.engineClass),
                )
                positiveScript.failure?.let {
                    throw AssertionError("The positive G10 Rhino script failed", it)
                }
                assertEquals("ok", File(work, "positive-complete.marker").readText())
                val retraced = File(work, "retraced-stack.txt").readBytes()
                val retracedText = retraced.toString(StandardCharsets.UTF_8)
                assertFalse("Retrace output contains CR bytes", retracedText.contains('\r'))
                assertTrue("Retrace did not restore the crash class and method", retracedText.contains("$CRASH_CLASS_NAME.explode"))
                assertTrue("Retrace did not restore the crash source line", retracedText.contains("G7ObfuscatedCrash.java:8"))
                assertTrue("Retrace did not preserve the kept caller", retracedText.contains("$RUNTIME_CLASS_NAME.crashForRetrace"))
                assertTrue("Retrace lost the invocation marker", retracedText.contains(marker))
                assertFalse("Retrace left the target frame obfuscated", retracedText.contains("\tat $obfuscatedClass."))

                val tamperedMappingFile = File(reportDirectory, "mapping-tampered.txt")
                writeDurably(
                    tamperedMappingFile,
                    mapping + "# G10 deliberate mapping digest mismatch\n".toByteArray(StandardCharsets.UTF_8),
                )
                val negativeScript = runRhino(
                    work,
                    "g10-retrace-tampered.js",
                    """
                    "rhino";
                    let stack = files.read("./obfuscated-stack.txt");
                    let retraced = runtime.retraceR8Stack(
                        stack,
                        "./reports/mapping-tampered.txt",
                        "./reports/retrace-metadata.bin"
                    );
                    files.write("./tampered-retraced-stack.txt", retraced);
                    """.trimIndent(),
                )
                assertTrue(
                    "The tampered mapping check did not execute through production Rhino",
                    RhinoJavaScriptEngine::class.java.isAssignableFrom(negativeScript.engineClass),
                )
                val negativeFailure = assertNotNull(
                    "A mapping/metadata digest mismatch was accepted",
                    negativeScript.failure,
                ).let { negativeScript.failure!! }
                assertTrue(
                    "The tampered mapping did not fail at the explicit R8-only route: $negativeFailure",
                    negativeFailure.hasCause<R8RetraceExplicitException>(),
                )
                assertFalse(
                    "The failed tampered mapping script published output",
                    File(work, "tampered-retraced-stack.txt").exists(),
                )

                assertTransactionRootEmpty("r8-compiler-transactions-v1")
                assertTransactionRootEmpty("r8-retrace-transactions-v1")
                G10Result(
                    minApi = minApi,
                    artifact = transaction.artifact,
                    programSha256 = program.readBytes().sha256(),
                    ruleSha256 = keepRules.readBytes().sha256(),
                    mappingSha256 = mapping.sha256(),
                    mappingByteLength = mapping.size,
                    metadataSha256 = metadata.sha256(),
                    metadataByteLength = metadata.size,
                    obfuscatedStackSha256 = obfuscatedStack.sha256(),
                    obfuscatedStackByteLength = obfuscatedStack.size,
                    retracedStackSha256 = retraced.sha256(),
                    retracedStackByteLength = retraced.size,
                    scriptEngineClass = positiveScript.engineClass.name,
                    marker = marker,
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
                    "The G10 executor did not terminate",
                    executor.awaitTermination(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                scriptEngineService.stopAll()
                awaitNoRegisteredEngines("after G10 device Gate")
            }.exceptionOrNull()?.let(cleanupFailures::add)
            published?.artifact?.let { artifact ->
                runCatching { deleteExactCacheGeneration(artifact) }
                    .exceptionOrNull()?.let(cleanupFailures::add)
            }
            runCatching { optInSnapshot.restore() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { enableSnapshot.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue("Unable to remove the G10 work directory", work.deleteRecursively())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue("Unable to remove the G10 loader directory", loaderCache.deleteRecursively())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun requireEnvironment(): G10Environment {
        val arguments = InstrumentationRegistry.getArguments()
        assertTrue("G10 requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("G10 must target canonical AutoJs6", HOST_PACKAGE, context.packageName)
        val expectedIdentity = arguments.getString(ARG_DEVICE_IDENTITY).orEmpty().trim()
        assertTrue("Pass the exact device identity through -e $ARG_DEVICE_IDENTITY", expectedIdentity.isNotEmpty())
        val serialProperty = shell("getprop ro.serialno").trim().ifEmpty {
            shell("getprop ro.boot.serialno").trim()
        }
        assertTrue("The G10 device exposed no stable serial property", serialProperty.isNotEmpty())
        val avdName = shell("getprop ro.boot.qemu.avd_name").trim().ifEmpty {
            shell("getprop ro.kernel.qemu.avd_name").trim()
        }
        val actualIdentity = if (avdName.isEmpty()) serialProperty else "$serialProperty@$avdName"
        assertEquals("G10 instrumentation is running on a different device", expectedIdentity, actualIdentity)
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
        return G10Environment(actualIdentity, candidate, providerVersionCode)
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
                    "remote=${remote.code}/${remote.phase}, message=${remote.message})"
            }
        }
        else -> outcome.toString()
    }

    private fun runRhino(work: File, sourceName: String, sourceText: String): RhinoExecution {
        val completed = CountDownLatch(1)
        val startedEngineClass = AtomicReference<Class<*>>()
        val failure = AtomicReference<Throwable>()
        val sourceFile = File(work, sourceName)
        writeDurably(sourceFile, sourceText.toByteArray(StandardCharsets.UTF_8))
        val source = JavaScriptFileSource(sourceFile)
        assertEquals(JavaScriptSource.ENGINE, source.engineName)
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                startedEngineClass.set(execution.engine.javaClass)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = scriptEngineService.execute(
            source,
            listener,
            ExecutionConfig(workingDirectory = work.absolutePath),
        )
        if (!completed.await(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            scriptEngineService.stopAll()
            throw AssertionError("Timed out waiting for G10 Rhino execution: $sourceName")
        }
        awaitEngineRemoved(execution.id)
        return RhinoExecution(
            engineClass = checkNotNull(startedEngineClass.get()) {
                "G10 script execution did not report its engine: $sourceName"
            },
            failure = failure.get(),
        )
    }

    private fun awaitEngineRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (scriptEngineService.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("G10 engine $executionId remained registered")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (scriptEngineService.engines.isEmpty() && scriptEngineService.scriptExecutions.isEmpty()) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError(
            "$label left engines=${scriptEngineService.engines.map { it.id }} " +
                "executions=${scriptEngineService.scriptExecutions}",
        )
    }

    private fun sendEvidence(environment: G10Environment, result: G10Result, invocationId: String) {
        val artifact = result.artifact
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("roadmapStage", "G10-DEVICE-RETRACE")
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
            .put("compileCapabilityFingerprint", artifact.result.capabilityFingerprint.toHexString())
            .put("minApi", result.minApi)
            .put("programSha256", result.programSha256)
            .put("ruleSha256", result.ruleSha256)
            .put("outputBundleSha256", artifact.result.outputBundleSha256.toHexString())
            .put("outputBundleSizeBytes", artifact.result.outputBundleSizeBytes)
            .put("mappingSha256", result.mappingSha256)
            .put("mappingByteLength", result.mappingByteLength)
            .put("metadataSha256", result.metadataSha256)
            .put("metadataByteLength", result.metadataByteLength)
            .put("obfuscatedStackSha256", result.obfuscatedStackSha256)
            .put("obfuscatedStackByteLength", result.obfuscatedStackByteLength)
            .put("retracedStackSha256", result.retracedStackSha256)
            .put("retracedStackByteLength", result.retracedStackByteLength)
            .put("scriptEngineClass", result.scriptEngineClass)
            .put("invocationMarkerSha256", result.marker.toByteArray(StandardCharsets.UTF_8).sha256())
            .put("crossApkBinder", true)
            .put("freshRemoteCompile", true)
            .put("artCrashExecuted", true)
            .put("rawStackIsObfuscated", true)
            .put("publicRhinoEntryExecuted", true)
            .put("providerRetraceExecuted", true)
            .put("mappingHashVerified", true)
            .put("originalCrashClassRestored", true)
            .put("originalCrashMethodRestored", true)
            .put("originalSourceLineRestored", true)
            .put("tamperedMappingRejected", true)
            .put("noFallbackObserved", true)
            .put("verifiedReportExport", true)
            .put("dexExported", false)
            .put(
                "exportedReports",
                JSONArray(listOf("mapping.txt", "seeds.txt", "usage.txt", "retrace-metadata.bin")),
            )
        instrumentation.sendStatus(0, Bundle().apply {
            putString(STATUS_RESULT_KEY, json.toString())
        })
    }

    private fun assertTransactionRootEmpty(name: String) {
        val root = File(context.cacheDir, name)
        assertTrue(
            "The production dispatcher leaked a G10 transaction workspace: $name",
            root.listFiles().orEmpty().isEmpty(),
        )
    }

    private fun deleteExactCacheGeneration(artifact: R8CompilerStoredArtifact) {
        require(SHA256.matches(artifact.cacheKeySha256))
        require(UUID_TEXT.matches(artifact.generation))
        val root = File(context.cacheDir, R8CompilerSemanticCacheKey.CACHE_DIRECTORY).canonicalFile
        val generation = File(root, "entry-${artifact.cacheKeySha256}-${artifact.generation}").canonicalFile
        require(generation.parentFile == root)
        assertTrue(!generation.exists() || generation.deleteRecursively())
    }

    private fun Throwable.toRetraceInput(): String = buildString {
        append(this@toRetraceInput.javaClass.name)
        message?.let { append(": ").append(it) }
        append('\n')
        stackTrace.forEach { frame -> append("\tat ").append(frame).append('\n') }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        val seen = HashSet<Throwable>()
        var current: Throwable? = this
        while (current != null && seen.add(current)) {
            if (current is T) return true
            current = current.cause
        }
        return false
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

    private data class G10Environment(
        val deviceIdentity: String,
        val candidate: R8CompilerServiceCandidate,
        val providerVersionCode: Long,
    )

    private data class RhinoExecution(
        val engineClass: Class<*>,
        val failure: Throwable?,
    )

    private data class G10Result(
        val minApi: Int,
        val artifact: R8CompilerStoredArtifact,
        val programSha256: String,
        val ruleSha256: String,
        val mappingSha256: String,
        val mappingByteLength: Int,
        val metadataSha256: String,
        val metadataByteLength: Int,
        val obfuscatedStackSha256: String,
        val obfuscatedStackByteLength: Int,
        val retracedStackSha256: String,
        val retracedStackByteLength: Int,
        val scriptEngineClass: String,
        val marker: String,
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
        const val ARG_ENABLED = "autojs.r8Compiler.g10.enabled"
        const val ARG_INVOCATION_ID = "autojs.r8Compiler.g10.invocationId"
        const val ARG_DEVICE_IDENTITY = "autojs.r8Compiler.g10.deviceIdentity"
        const val ARG_DEVICE_SDK = "autojs.r8Compiler.g10.sdk"
        const val STATUS_RESULT_KEY = "autojs.r8Compiler.g10.result"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.r8compiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.R8CompilerService"
        val REAL_COMPONENT = R8CompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        const val OPT_IN_ENABLED_KEY = "r8_compiler_explicit_enabled"
        const val OPT_IN_COMPONENT_KEY = "r8_compiler_explicit_component"
        const val PLUGIN_ENABLE_SP = "plugin_center_enable_state"
        const val PROGRAM_ASSET = "r8-g7-program.jar"
        const val RULE_ASSET = "r8-g7-keep.pro"
        const val RUNTIME_CLASS_NAME = "org.autojs.fixture.r8compiler.g7.G7RuntimeFixture"
        const val CRASH_CLASS_NAME = "org.autojs.fixture.r8compiler.g7.G7ObfuscatedCrash"
        const val RETRACE_MARKER_PREFIX = "autojs6-g10-retrace-"
        const val ACCEPTANCE_TIMEOUT_MINUTES = 7L
        const val EXECUTOR_SHUTDOWN_SECONDS = 5L
        const val SCRIPT_TIMEOUT_SECONDS = 180L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 5_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        val SHA256 = Regex("[0-9a-f]{64}")
        val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun pluginEnableKey(packageName: String) = "key_\$_enabled_plugin_\$$packageName"
    }
}
