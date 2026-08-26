package org.autojs.autojs.core.plugin.dex

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs6.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * R5.2 process-cold performance fixture for the real production JAR-loading paths.
 *
 * The host-side runner owns APK identity, process restarts, repeated samples, external PSS polling,
 * statistics, and the canonical report. This test owns deterministic program generation, exact
 * route/cache-state assertions, in-process timing, host heap/PSS observations, output validation,
 * and cleanup of only the cache generations/directories created by this fixture.
 *
 * Ordinary instrumentation runs skip every entry unless [ARG_ENABLED] is exactly `true`.
 */
class DexCompilerR5PerformanceAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments
        get() = InstrumentationRegistry.getArguments()

    @Test
    fun prepareDeterministicCorpus() {
        requireBenchmarkEnvironment(requireProvider = false)
        val corpus = requiredCorpus()
        val first = corpus.jarBytes()
        val second = corpus.jarBytes()
        assertArrayEquals("R5.2 corpus generator is not byte deterministic", first, second)

        val directory = corpusDirectory().apply {
            assertTrue("Unable to create the R5.2 corpus directory", isDirectory || mkdirs())
        }
        val target = corpusFile(corpus)
        val temporary = File(directory, "${target.name}-${UUID.randomUUID()}.partial")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(first)
                output.fd.sync()
            }
            if (target.exists()) {
                assertTrue("Unable to replace the previous R5.2 corpus", target.delete())
            }
            assertTrue("Unable to publish the R5.2 corpus", temporary.renameTo(target))
        } finally {
            temporary.delete()
        }

        assertEquals(first.size.toLong(), target.length())
        assertEquals(first.sha256(), target.sha256())
        sendStatus(
            STATUS_CORPUS,
            corpusEvidence(corpus, target).put("deterministicRegeneration", true),
        )
    }

    @Test
    fun primePersistentCache() {
        val route = requiredRoute()
        requireBenchmarkEnvironment(requireProvider = route == BenchmarkRoute.PLUGIN)
        val corpus = requiredPreparedCorpus()
        val savedOptions = snapshotOptInStore()
        try {
            configureRoute(route)
            AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
            DexCompilerRuntimeDiagnostics.resetForTest()
            val cacheDirectory = when (route) {
                BenchmarkRoute.BUILTIN -> builtInPersistentCache(corpus.spec).apply {
                    assertTrue("Unable to reset the built-in R5.2 cache", !exists() || deleteRecursively())
                    assertTrue("Unable to create the built-in R5.2 cache", mkdirs())
                }
                BenchmarkRoute.PLUGIN -> freshLoaderCache("prime-plugin", corpus.spec)
            }

            val operation = runLoadOperation(route, corpus, cacheDirectory)
            when (route) {
                BenchmarkRoute.BUILTIN -> assertEquals(
                    "Built-in cache priming did not use the built-in route",
                    DexCompilerRuntimePath.BUILTIN,
                    operation.adoptedRoute.path,
                )
                BenchmarkRoute.PLUGIN -> assertEquals(
                    "Plugin cache priming unexpectedly reused an existing generation",
                    DexCompilerRuntimePath.PLUGIN,
                    operation.adoptedRoute.path,
                )
            }
            val artifact = when (route) {
                BenchmarkRoute.BUILTIN -> builtInArtifactEvidence(cacheDirectory)
                BenchmarkRoute.PLUGIN -> pluginArtifactEvidence(corpus.file, invalidate = false)
            }
            if (route == BenchmarkRoute.PLUGIN) {
                assertTrue("Unable to remove the plugin priming loader cache", cacheDirectory.deleteRecursively())
            }
            sendStatus(
                STATUS_MANAGEMENT,
                managementEvidence("PRIME", route, corpus, operation.adoptedRoute, artifact),
            )
        } finally {
            restoreOptInStore(savedOptions)
        }
    }

    @Test
    fun cleanupPersistentCache() {
        val route = requiredRoute()
        requireBenchmarkEnvironment(requireProvider = route == BenchmarkRoute.PLUGIN)
        val corpus = requiredPreparedCorpus()
        val savedOptions = snapshotOptInStore()
        try {
            configureRoute(route)
            AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
            val artifact = when (route) {
                BenchmarkRoute.BUILTIN -> {
                    val cache = builtInPersistentCache(corpus.spec)
                    val existed = cache.exists()
                    assertTrue("Unable to remove the built-in R5.2 cache", !existed || cache.deleteRecursively())
                    assertFalse("The built-in R5.2 cache still exists", cache.exists())
                    ArtifactEvidence(
                        outputSizeBytes = 0L,
                        outputSha256 = null,
                        cacheKeySha256 = null,
                        metadataLookupCacheHit = existed,
                        generationInvalidated = existed,
                        loaderCacheFileCount = 0,
                        loaderCacheBytes = 0L,
                    )
                }
                BenchmarkRoute.PLUGIN -> invalidateTrackedPluginArtifact(corpus)
            }
            sendStatus(
                STATUS_MANAGEMENT,
                managementEvidence("CLEANUP", route, corpus, adoptedRoute = null, artifact = artifact),
            )
        } finally {
            restoreOptInStore(savedOptions)
        }
    }

    @Test
    fun cleanupBenchmarkWorkspace() {
        requireBenchmarkEnvironment(requireProvider = false)
        val workspace = File(context.noBackupFilesDir, WORKSPACE_DIRECTORY)
        val existed = workspace.exists()
        assertTrue("Unable to remove the exact R5.2 benchmark workspace", !existed || workspace.deleteRecursively())
        assertFalse("The R5.2 benchmark workspace still exists", workspace.exists())
        sendStatus(
            STATUS_MANAGEMENT,
            JSONObject()
                .put("schemaVersion", STATUS_SCHEMA_VERSION)
                .put("action", "CLEANUP_WORKSPACE")
                .put("route", JSONObject.NULL)
                .put("workspaceExisted", existed)
                .put("workspaceRemoved", true),
        )
    }

    @Test
    fun measureSingleProcessColdSample() {
        val route = requiredRoute()
        val cacheState = requiredCacheState()
        val sampleIndex = arguments.getString(ARG_SAMPLE_INDEX)?.toIntOrNull()
            ?: throw AssertionError("Missing or invalid $ARG_SAMPLE_INDEX")
        assertTrue("R5.2 sample index must be non-negative", sampleIndex >= 0)
        requireBenchmarkEnvironment(requireProvider = route == BenchmarkRoute.PLUGIN)
        val corpus = requiredPreparedCorpus()
        val savedOptions = snapshotOptInStore()
        var loaderCache: File? = null
        try {
            configureRoute(route)
            AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
            loaderCache = when {
                route == BenchmarkRoute.BUILTIN && cacheState == CacheState.CACHE_HIT ->
                    builtInPersistentCache(corpus.spec).also { cache ->
                        assertTrue("Built-in cache-hit sample was not primed", cache.isDirectory)
                        assertNotNull(
                            "Built-in cache-hit sample has no compiled artifact",
                            findBuiltInArtifact(cache),
                        )
                    }
                else -> freshLoaderCache(
                    "sample-${route.name.lowercase(Locale.US)}-${cacheState.name.lowercase(Locale.US)}-$sampleIndex",
                    corpus.spec,
                )
            }
            val builtInArtifactBefore = if (route == BenchmarkRoute.BUILTIN) {
                findBuiltInArtifact(checkNotNull(loaderCache))?.identity()
            } else {
                null
            }

            DexCompilerRuntimeDiagnostics.resetForTest()
            val runtime = Runtime.getRuntime()
            SystemClock.sleep(EXTERNAL_SAMPLE_SETTLE_MILLIS)
            val memoryBefore = MemoryEvidence.capture(runtime)
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "dex-r5-performance-sample")
            }
            val timed = try {
                executor.submit<TimedLoad> {
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    val operation = runLoadOperation(
                        route = route,
                        corpus = corpus,
                        cacheDirectory = checkNotNull(loaderCache),
                        clearLoaderCacheOnInit = !(
                            route == BenchmarkRoute.BUILTIN && cacheState == CacheState.CACHE_HIT
                        ),
                    )
                    TimedLoad(
                        elapsedNanos = SystemClock.elapsedRealtimeNanos() - startedAt,
                        operation = operation,
                    )
                }.get(SAMPLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                assertTrue(
                    "R5.2 sample executor did not terminate",
                    executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS),
                )
            }
            val memoryAfter = MemoryEvidence.capture(runtime)
            val expectedPath = when (route) {
                BenchmarkRoute.BUILTIN -> DexCompilerRuntimePath.BUILTIN
                BenchmarkRoute.PLUGIN -> when (cacheState) {
                    CacheState.COLD -> DexCompilerRuntimePath.PLUGIN
                    CacheState.CACHE_HIT -> DexCompilerRuntimePath.CACHE_HIT
                }
            }
            assertEquals("Timed R5.2 load used the wrong route", expectedPath, timed.operation.adoptedRoute.path)
            assertEquals("Timed R5.2 load unexpectedly reported a fallback hint", null, timed.operation.adoptedRoute.hint)

            val artifact = when (route) {
                BenchmarkRoute.BUILTIN -> builtInArtifactEvidence(checkNotNull(loaderCache))
                BenchmarkRoute.PLUGIN -> pluginArtifactEvidence(
                    corpus.file,
                    invalidate = cacheState == CacheState.COLD,
                    loaderCache = loaderCache,
                )
            }
            val (cacheVerification, cacheArtifactLastModifiedDeltaMillis) = when {
                route == BenchmarkRoute.BUILTIN && cacheState == CacheState.COLD -> {
                    assertEquals("Fresh built-in loader cache unexpectedly contained an artifact", null, builtInArtifactBefore)
                    "EMPTY_FRESH_LOADER_CACHE_AND_BUILTIN_ROUTE" to null
                }
                route == BenchmarkRoute.BUILTIN && cacheState == CacheState.CACHE_HIT -> {
                    val after = requireNotNull(findBuiltInArtifact(checkNotNull(loaderCache))).identity()
                    val before = requireNotNull(builtInArtifactBefore)
                    assertEquals("Built-in cache-hit artifact name changed", before.name, after.name)
                    assertEquals("Built-in cache-hit artifact size changed", before.sizeBytes, after.sizeBytes)
                    assertEquals("Built-in cache-hit artifact bytes changed", before.sha256, after.sha256)
                    "PREEXISTING_ARTIFACT_NAME_SIZE_AND_SHA256_UNCHANGED" to
                        (after.lastModifiedMillis - before.lastModifiedMillis)
                }
                route == BenchmarkRoute.PLUGIN && cacheState == CacheState.COLD -> {
                    assertTrue("Plugin cold sample did not invalidate its exact generation", artifact.generationInvalidated)
                    "PLUGIN_ROUTE_AND_EXACT_GENERATION_INVALIDATED" to null
                }
                else -> "DIAGNOSTIC_CACHE_HIT_ROUTE" to null
            }
            SystemClock.sleep(EXTERNAL_SAMPLE_SETTLE_MILLIS)
            sendStatus(
                STATUS_SAMPLE,
                sampleEvidence(
                    sampleIndex = sampleIndex,
                    route = route,
                    cacheState = cacheState,
                    corpus = corpus,
                    timed = timed,
                    memoryBefore = memoryBefore,
                    memoryAfter = memoryAfter,
                    artifact = artifact,
                    cacheVerification = cacheVerification,
                    cacheArtifactLastModifiedDeltaMillis = cacheArtifactLastModifiedDeltaMillis,
                ),
            )
        } finally {
            if (!(route == BenchmarkRoute.BUILTIN && cacheState == CacheState.CACHE_HIT)) {
                loaderCache?.let { cache ->
                    assertTrue("Unable to remove the transient R5.2 loader cache", !cache.exists() || cache.deleteRecursively())
                }
            }
            restoreOptInStore(savedOptions)
        }
    }

    private fun runLoadOperation(
        route: BenchmarkRoute,
        corpus: PreparedCorpus,
        cacheDirectory: File,
        clearLoaderCacheOnInit: Boolean = true,
    ): LoadOperation {
        val loader = AndroidClassLoader(
            parent = requireNotNull(javaClass.classLoader),
            cacheDir = cacheDirectory,
            clearCacheOnInit = clearLoaderCacheOnInit,
        )
        val dexLoader = when (route) {
            BenchmarkRoute.BUILTIN -> loader.loadJar(corpus.file)
            BenchmarkRoute.PLUGIN -> loader.loadJarWithExperimentalCompiler(corpus.file)
        }
        val fixture = dexLoader.loadClass(corpus.spec.firstClassName)
        val answer = fixture.getDeclaredMethod(ANSWER_METHOD).invoke(null) as? Int
        assertEquals("R5.2 fixture returned the wrong value", EXPECTED_ANSWER, answer)
        val adoptedRoute = requireNotNull(DexCompilerRuntimeDiagnostics.latest()) {
            "R5.2 load did not record an adopted route"
        }
        return LoadOperation(adoptedRoute = adoptedRoute, answer = checkNotNull(answer))
    }

    private fun pluginArtifactEvidence(
        program: File,
        invalidate: Boolean,
        loaderCache: File? = null,
        requireCacheHit: Boolean = true,
    ): ArtifactEvidence {
        val coordinator = requireNotNull(AndroidDexCompilerRuntime.coordinatorOrNull()) {
            "The production DEX compiler coordinator is unavailable"
        }
        return when (val decision = coordinator.compile(program)) {
            is DexCompilerRuntimeDecision.Published -> {
                try {
                    if (requireCacheHit) {
                        assertTrue("Post-load plugin metadata lookup did not hit the verified cache", decision.cacheHit)
                    }
                    val artifact = decision.artifact
                    val size = artifact.sizeBytes
                    val sha256 = artifact.outputSha256
                    val cacheKey = artifact.cacheKeySha256
                    trackPluginCacheKey(program, cacheKey)
                    val invalidated = if (invalidate) {
                        assertTrue("Unable to invalidate the exact R5.2 plugin generation", artifact.invalidate())
                        val reopened = artifact.openRead()
                        reopened?.close()
                        assertEquals("Invalidated R5.2 plugin generation can still be opened", null, reopened)
                        forgetPluginCacheKey(program)
                        true
                    } else {
                        false
                    }
                    ArtifactEvidence(
                        outputSizeBytes = size,
                        outputSha256 = sha256,
                        cacheKeySha256 = cacheKey,
                        metadataLookupCacheHit = decision.cacheHit,
                        generationInvalidated = invalidated,
                        loaderCacheFileCount = loaderCache?.directFiles()?.size ?: 0,
                        loaderCacheBytes = loaderCache?.directFiles()?.sumOf(File::length) ?: 0L,
                    )
                } finally {
                    decision.fallbackInput.close()
                }
            }
            is DexCompilerRuntimeDecision.UseLocal -> {
                decision.fallbackInput?.close()
                throw AssertionError("R5.2 plugin metadata lookup unexpectedly selected local D8: $decision")
            }
            DexCompilerRuntimeDecision.Cancelled ->
                throw AssertionError("R5.2 plugin metadata lookup was unexpectedly cancelled")
        }
    }

    private fun invalidateTrackedPluginArtifact(corpus: PreparedCorpus): ArtifactEvidence {
        val keyFile = pluginCacheKeyFile(corpus.file)
        if (!keyFile.isFile) {
            return ArtifactEvidence(
                outputSizeBytes = 0L,
                outputSha256 = null,
                cacheKeySha256 = null,
                metadataLookupCacheHit = false,
                generationInvalidated = false,
                loaderCacheFileCount = 0,
                loaderCacheBytes = 0L,
            )
        }
        val cacheKey = keyFile.readText(Charsets.US_ASCII).trim()
        assertTrue("Tracked R5.2 plugin cache key is malformed", LOWER_SHA256.matches(cacheKey))
        val drain = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dex-r5-performance-cache-drain")
        }
        return try {
            val artifact = AndroidDexCompilerArtifactStore(context, drain).lookupVerified(cacheKey)
            val invalidated = artifact?.let { tracked ->
                assertTrue("Unable to invalidate the tracked R5.2 plugin generation", tracked.invalidate())
                true
            } ?: false
            ArtifactEvidence(
                outputSizeBytes = artifact?.sizeBytes ?: 0L,
                outputSha256 = artifact?.outputSha256,
                cacheKeySha256 = cacheKey,
                metadataLookupCacheHit = artifact != null,
                generationInvalidated = invalidated,
                loaderCacheFileCount = 0,
                loaderCacheBytes = 0L,
            )
        } finally {
            assertTrue("Unable to remove the tracked R5.2 plugin cache key", keyFile.delete())
            drain.shutdownNow()
            assertTrue(
                "R5.2 cache drain executor did not terminate",
                drain.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS),
            )
        }
    }

    private fun trackPluginCacheKey(program: File, cacheKey: String) {
        assertTrue("R5.2 plugin cache key is malformed", LOWER_SHA256.matches(cacheKey))
        val directory = pluginCacheKeyDirectory().apply {
            assertTrue("Unable to create the R5.2 plugin-key directory", isDirectory || mkdirs())
        }
        val target = pluginCacheKeyFile(program)
        val temporary = File(directory, "${target.name}-${UUID.randomUUID()}.partial")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(cacheKey.toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
            if (target.exists()) {
                assertTrue("Unable to replace the tracked R5.2 plugin cache key", target.delete())
            }
            assertTrue("Unable to publish the tracked R5.2 plugin cache key", temporary.renameTo(target))
        } finally {
            temporary.delete()
        }
    }

    private fun forgetPluginCacheKey(program: File) {
        val keyFile = pluginCacheKeyFile(program)
        assertTrue("Unable to remove the tracked R5.2 plugin cache key", !keyFile.exists() || keyFile.delete())
    }

    private fun builtInArtifactEvidence(cacheDirectory: File): ArtifactEvidence {
        val artifact = requireNotNull(findBuiltInArtifact(cacheDirectory)) {
            "Built-in R5.2 load produced no persistent DEX artifact"
        }
        val files = cacheDirectory.directFiles()
        return ArtifactEvidence(
            outputSizeBytes = artifact.length(),
            outputSha256 = artifact.sha256(),
            cacheKeySha256 = null,
            metadataLookupCacheHit = true,
            generationInvalidated = false,
            loaderCacheFileCount = files.size,
            loaderCacheBytes = files.sumOf(File::length),
        )
    }

    private fun findBuiltInArtifact(cacheDirectory: File): File? = cacheDirectory.directFiles().singleOrNull { file ->
        !file.name.startsWith("safe_") && (file.extension == "dex" || file.extension == "jar")
    }

    private fun configureRoute(route: BenchmarkRoute) {
        when (route) {
            BenchmarkRoute.BUILTIN -> DexCompilerOptInStore.disable()
            BenchmarkRoute.PLUGIN -> {
                val identity = requireRealProvider()
                assertEquals(
                    DexCompilerOptInOptions(enabled = true, selectedComponent = identity.component),
                    DexCompilerOptInStore.select(identity.component),
                )
            }
        }
    }

    private fun requireRealProvider(): DexCompilerPinnedIdentity {
        val packageManager = context.packageManager
        try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(REAL_PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real DEX compiler package is not installed: $REAL_PLUGIN_PACKAGE", error)
        }
        assertEquals(
            "The R5.2 provider and host must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        val candidates = AndroidDexCompilerExperimentalTransport(context)
            .discoverExactAction(org.autojs.plugin.dexcompiler.api.DexCompilerContract.SERVICE_ACTION)
            .filter { candidate -> candidate.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals("The real package must expose exactly one R5.2 provider", 1, candidates.size)
        val candidate = candidates.single()
        assertEquals(REAL_COMPONENT, candidate.component)
        assertTrue("The R5.2 provider is not exported", candidate.exported)
        assertTrue("The R5.2 provider is disabled", candidate.applicationEnabled && candidate.serviceEnabled)
        assertTrue("The R5.2 provider signer differs from the host", candidate.sameSignerAsHost)
        assertEquals(
            "The R5.2 provider is rejected by production policy",
            null,
            DexCompilerExperimentalPolicy.rejection(candidate, releaseBuild = !BuildConfig.DEBUG),
        )
        return candidate.pin()
    }

    private fun requireBenchmarkEnvironment(requireProvider: Boolean) {
        assumeTrue(
            "R5.2 performance instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertEquals("R5.2 performance fixture requires the canonical host", HOST_PACKAGE, context.packageName)
        assertTrue("R5.2 performance fixture requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertTrue("R5.2 performance fixture is defined only through API 36", Build.VERSION.SDK_INT <= 36)
        if (requireProvider) requireRealProvider()
    }

    private fun requiredCorpus(): CorpusSpec {
        val id = arguments.getString(ARG_CORPUS) ?: throw AssertionError("Missing $ARG_CORPUS")
        return CorpusSpec.entries.singleOrNull { it.id == id }
            ?: throw AssertionError("Unknown R5.2 corpus: $id")
    }

    private fun requiredPreparedCorpus(): PreparedCorpus {
        val spec = requiredCorpus()
        val file = corpusFile(spec)
        assertTrue("R5.2 corpus was not prepared: ${spec.id}", file.isFile)
        val size = file.length()
        assertTrue("R5.2 corpus is empty: ${spec.id}", size > 0L)
        return PreparedCorpus(spec, file, size, file.sha256())
    }

    private fun requiredRoute(): BenchmarkRoute {
        val value = arguments.getString(ARG_ROUTE) ?: throw AssertionError("Missing $ARG_ROUTE")
        return enumValues<BenchmarkRoute>().singleOrNull { it.name == value }
            ?: throw AssertionError("Unknown R5.2 route: $value")
    }

    private fun requiredCacheState(): CacheState {
        val value = arguments.getString(ARG_CACHE_STATE) ?: throw AssertionError("Missing $ARG_CACHE_STATE")
        return enumValues<CacheState>().singleOrNull { it.name == value }
            ?: throw AssertionError("Unknown R5.2 cache state: $value")
    }

    private fun corpusDirectory(): File = File(context.noBackupFilesDir, "$WORKSPACE_DIRECTORY/corpora")

    private fun corpusFile(corpus: CorpusSpec): File = File(corpusDirectory(), "${corpus.id}.jar")

    private fun pluginCacheKeyDirectory(): File =
        File(context.noBackupFilesDir, "$WORKSPACE_DIRECTORY/plugin-cache-keys")

    private fun pluginCacheKeyFile(program: File): File =
        File(pluginCacheKeyDirectory(), "${program.nameWithoutExtension}.sha256")

    private fun builtInPersistentCache(corpus: CorpusSpec): File =
        File(context.noBackupFilesDir, "$WORKSPACE_DIRECTORY/cache/builtin/${corpus.id}")

    private fun freshLoaderCache(label: String, corpus: CorpusSpec): File =
        File(context.noBackupFilesDir, "$WORKSPACE_DIRECTORY/cache/transient/$label-${corpus.id}-${UUID.randomUUID()}")
            .apply { assertTrue("Unable to create a transient R5.2 loader cache", mkdirs()) }

    private fun snapshotOptInStore(): OptInStoreSnapshot {
        val preferences = Pref.get()
        val values = preferences.all
        val enabledPresent = preferences.contains(OPT_IN_ENABLED_KEY)
        val componentPresent = preferences.contains(OPT_IN_COMPONENT_KEY)
        val enabled = values[OPT_IN_ENABLED_KEY]
        val component = values[OPT_IN_COMPONENT_KEY]
        check(!enabledPresent || enabled is Boolean) { "Unexpected DEX compiler opt-in enabled value" }
        check(!componentPresent || component is String) { "Unexpected DEX compiler opt-in component value" }
        return OptInStoreSnapshot(
            options = DexCompilerOptInStore.read(),
            enabledPresent = enabledPresent,
            enabled = enabled as? Boolean,
            componentPresent = componentPresent,
            component = component as? String,
        )
    }

    private fun restoreOptInStore(snapshot: OptInStoreSnapshot) {
        val editor = Pref.get().edit()
            .remove(OPT_IN_ENABLED_KEY)
            .remove(OPT_IN_COMPONENT_KEY)
        if (snapshot.enabledPresent) editor.putBoolean(OPT_IN_ENABLED_KEY, checkNotNull(snapshot.enabled))
        if (snapshot.componentPresent) editor.putString(OPT_IN_COMPONENT_KEY, checkNotNull(snapshot.component))
        check(editor.commit()) { "Unable to restore DEX compiler opt-in preferences" }
        check(DexCompilerOptInStore.read() == snapshot.options) {
            "Restored DEX compiler opt-in state does not match its saved value"
        }
    }

    private fun corpusEvidence(corpus: CorpusSpec, file: File): JSONObject = JSONObject()
        .put("schemaVersion", STATUS_SCHEMA_VERSION)
        .put("generatorVersion", CORPUS_GENERATOR_VERSION)
        .put("corpusId", corpus.id)
        .put("classCount", corpus.classCount)
        .put("staticMethodsPerClass", corpus.staticMethodsPerClass)
        .put("declaredMethodCount", corpus.declaredMethodCount)
        .put("inputSizeBytes", file.length())
        .put("inputSha256", file.sha256())

    private fun managementEvidence(
        action: String,
        route: BenchmarkRoute,
        corpus: PreparedCorpus,
        adoptedRoute: DexCompilerLastRoute?,
        artifact: ArtifactEvidence,
    ): JSONObject = JSONObject()
        .put("schemaVersion", STATUS_SCHEMA_VERSION)
        .put("action", action)
        .put("route", route.name)
        .put("corpus", corpus.toJson())
        .put("adoptedPath", adoptedRoute?.path?.name ?: JSONObject.NULL)
        .put("artifact", artifact.toJson())

    private fun sampleEvidence(
        sampleIndex: Int,
        route: BenchmarkRoute,
        cacheState: CacheState,
        corpus: PreparedCorpus,
        timed: TimedLoad,
        memoryBefore: MemoryEvidence,
        memoryAfter: MemoryEvidence,
        artifact: ArtifactEvidence,
        cacheVerification: String,
        cacheArtifactLastModifiedDeltaMillis: Long?,
    ): JSONObject = JSONObject()
        .put("schemaVersion", STATUS_SCHEMA_VERSION)
        .put("sampleIndex", sampleIndex)
        .put("route", route.name)
        .put("cacheState", cacheState.name)
        .put("cacheExpectedHit", cacheState == CacheState.CACHE_HIT)
        .put("cacheStateVerified", true)
        .put("cacheVerification", cacheVerification)
        .put(
            "loaderCacheInitialization",
            if (route == BenchmarkRoute.BUILTIN && cacheState == CacheState.CACHE_HIT) {
                "PRESERVED_FOR_PREEXISTING_ARTIFACT"
            } else {
                "CLEARED_ON_CONSTRUCTION"
            },
        )
        .put(
            "cacheArtifactLastModifiedDeltaMillis",
            cacheArtifactLastModifiedDeltaMillis ?: JSONObject.NULL,
        )
        .put("processState", "HOST_AND_PROVIDER_FORCE_STOPPED_BEFORE_SAMPLE")
        .put("timingBoundary", "LOAD_JAR_THROUGH_CLASS_RESOLUTION_AND_STATIC_INVOCATION")
        .put("elapsedNanos", timed.elapsedNanos)
        .put("elapsedMillis", timed.elapsedNanos / 1_000_000.0)
        .put("adoptedPath", timed.operation.adoptedRoute.path.name)
        .put("adoptedHint", timed.operation.adoptedRoute.hint?.name ?: JSONObject.NULL)
        .put("result", timed.operation.answer)
        .put("corpus", corpus.toJson())
        .put("memory", JSONObject()
            .put("before", memoryBefore.toJson())
            .put("after", memoryAfter.toJson()))
        .put("artifact", artifact.toJson())
        .put("host", JSONObject()
            .put("packageName", context.packageName)
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE))
        .put("device", JSONObject()
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("primaryAbi", Build.SUPPORTED_ABIS.first())
            .put("model", Build.MODEL)
            .put("buildFingerprintSha256", Build.FINGERPRINT.toByteArray().sha256()))

    private fun sendStatus(key: String, value: JSONObject) {
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString(key, value.toString()) },
        )
    }

    private data class PreparedCorpus(
        val spec: CorpusSpec,
        val file: File,
        val sizeBytes: Long,
        val sha256: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("generatorVersion", CORPUS_GENERATOR_VERSION)
            .put("corpusId", spec.id)
            .put("classCount", spec.classCount)
            .put("staticMethodsPerClass", spec.staticMethodsPerClass)
            .put("declaredMethodCount", spec.declaredMethodCount)
            .put("inputSizeBytes", sizeBytes)
            .put("inputSha256", sha256)
    }

    private data class TimedLoad(
        val elapsedNanos: Long,
        val operation: LoadOperation,
    )

    private data class LoadOperation(
        val adoptedRoute: DexCompilerLastRoute,
        val answer: Int,
    )

    private data class FileIdentity(
        val name: String,
        val sizeBytes: Long,
        val sha256: String,
        val lastModifiedMillis: Long,
    )

    private data class ArtifactEvidence(
        val outputSizeBytes: Long,
        val outputSha256: String?,
        val cacheKeySha256: String?,
        val metadataLookupCacheHit: Boolean,
        val generationInvalidated: Boolean,
        val loaderCacheFileCount: Int,
        val loaderCacheBytes: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("outputSizeBytes", outputSizeBytes)
            .put("outputSha256", outputSha256 ?: JSONObject.NULL)
            .put("cacheKeySha256", cacheKeySha256 ?: JSONObject.NULL)
            .put("metadataLookupCacheHit", metadataLookupCacheHit)
            .put("generationInvalidated", generationInvalidated)
            .put("loaderCacheFileCount", loaderCacheFileCount)
            .put("loaderCacheBytes", loaderCacheBytes)
    }

    private data class MemoryEvidence(
        val hostPssKb: Long,
        val javaHeapUsedBytes: Long,
        val nativeHeapAllocatedBytes: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("hostPssKb", hostPssKb)
            .put("javaHeapUsedBytes", javaHeapUsedBytes)
            .put("nativeHeapAllocatedBytes", nativeHeapAllocatedBytes)

        companion object {
            fun capture(runtime: Runtime): MemoryEvidence = MemoryEvidence(
                hostPssKb = Debug.getPss(),
                javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            )
        }
    }

    private data class OptInStoreSnapshot(
        val options: DexCompilerOptInOptions,
        val enabledPresent: Boolean,
        val enabled: Boolean?,
        val componentPresent: Boolean,
        val component: String?,
    )

    private enum class BenchmarkRoute {
        BUILTIN,
        PLUGIN,
    }

    private enum class CacheState {
        COLD,
        CACHE_HIT,
    }

    private enum class CorpusSpec(
        val id: String,
        val packageSegment: String,
        val classCount: Int,
        val staticMethodsPerClass: Int,
    ) {
        TINY("java8-1x8", "tiny", 1, 8),
        LIBRARY("java8-128x8", "library", 128, 8),
        BULK("java8-2048x8", "bulk", 2_048, 8),
        ;

        val declaredMethodCount: Int
            get() = classCount * (staticMethodsPerClass + 1)

        val firstClassName: String
            get() = internalName(0).replace('/', '.')

        fun jarBytes(): ByteArray = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                repeat(classCount) { index ->
                    val internalName = internalName(index)
                    zip.writeStoredEntry(
                        "$internalName.class",
                        classBytes(
                            internalName = internalName,
                            sourceFileName = "C${index.toString().padStart(4, '0')}.java",
                            staticMethodCount = staticMethodsPerClass,
                        ),
                    )
                }
            }
            output.toByteArray()
        }

        private fun internalName(index: Int): String =
            "org/autojs/fixture/dexcompiler/performance/$packageSegment/C${index.toString().padStart(4, '0')}"

        companion object {
            private fun classBytes(
                internalName: String,
                sourceFileName: String,
                staticMethodCount: Int,
            ): ByteArray = ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { data ->
                    val methodNames = List(staticMethodCount) { index ->
                        if (index == 0) ANSWER_METHOD else "method${index.toString().padStart(2, '0')}"
                    }
                    data.writeInt(0xcafebabe.toInt())
                    data.writeShort(0)
                    data.writeShort(52)
                    data.writeShort(13 + methodNames.size)
                    data.writeUtf8Constant(internalName) // 1
                    data.writeByte(7)
                    data.writeShort(1) // 2 Class(this)
                    data.writeUtf8Constant("java/lang/Object") // 3
                    data.writeByte(7)
                    data.writeShort(3) // 4 Class(Object)
                    data.writeUtf8Constant("<init>") // 5
                    data.writeUtf8Constant("()V") // 6
                    data.writeUtf8Constant("Code") // 7
                    data.writeByte(12)
                    data.writeShort(5)
                    data.writeShort(6) // 8 NameAndType(Object.<init>)
                    data.writeByte(10)
                    data.writeShort(4)
                    data.writeShort(8) // 9 Methodref(Object.<init>)
                    data.writeUtf8Constant("SourceFile") // 10
                    data.writeUtf8Constant(sourceFileName) // 11
                    data.writeUtf8Constant("()I") // 12
                    methodNames.forEach { methodName -> data.writeUtf8Constant(methodName) } // 13...

                    data.writeShort(0x0031) // public final super
                    data.writeShort(2)
                    data.writeShort(4)
                    data.writeShort(0) // interfaces
                    data.writeShort(0) // fields
                    data.writeShort(methodNames.size + 1)

                    data.writeShort(0x0001) // public constructor
                    data.writeShort(5)
                    data.writeShort(6)
                    data.writeShort(1)
                    data.writeShort(7)
                    data.writeInt(17)
                    data.writeShort(1)
                    data.writeShort(1)
                    data.writeInt(5)
                    data.writeByte(0x2a) // aload_0
                    data.writeByte(0xb7) // invokespecial
                    data.writeShort(9)
                    data.writeByte(0xb1) // return
                    data.writeShort(0)
                    data.writeShort(0)

                    methodNames.indices.forEach { index ->
                        data.writeShort(0x0009) // public static
                        data.writeShort(13 + index)
                        data.writeShort(12)
                        data.writeShort(1)
                        data.writeShort(7)
                        data.writeInt(15)
                        data.writeShort(1)
                        data.writeShort(0)
                        data.writeInt(3)
                        data.writeByte(0x10) // bipush
                        data.writeByte(EXPECTED_ANSWER)
                        data.writeByte(0xac) // ireturn
                        data.writeShort(0)
                        data.writeShort(0)
                    }

                    data.writeShort(1)
                    data.writeShort(10)
                    data.writeInt(2)
                    data.writeShort(11)
                }
                output.toByteArray()
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
        }
    }

    private fun File.directFiles(): List<File> = listFiles().orEmpty().filter(File::isFile).sortedBy(File::getName)

    private fun File.identity(): FileIdentity = FileIdentity(
        name = name,
        sizeBytes = length(),
        sha256 = sha256(),
        lastModifiedMillis = lastModified(),
    )

    private fun File.sha256(): String = FileInputStream(this).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().toLowerHex()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toLowerHex()

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(Locale.US, byte.toInt() and 0xff)
    }

    private companion object {
        const val ARG_ENABLED = "autojs.dexCompiler.r5.performance.enabled"
        const val ARG_CORPUS = "autojs.dexCompiler.r5.performance.corpus"
        const val ARG_ROUTE = "autojs.dexCompiler.r5.performance.route"
        const val ARG_CACHE_STATE = "autojs.dexCompiler.r5.performance.cacheState"
        const val ARG_SAMPLE_INDEX = "autojs.dexCompiler.r5.performance.sampleIndex"
        const val STATUS_CORPUS = "autojs.dex.r5.performance.corpus"
        const val STATUS_MANAGEMENT = "autojs.dex.r5.performance.management"
        const val STATUS_SAMPLE = "autojs.dex.r5.performance.sample"
        const val STATUS_SCHEMA_VERSION = 1
        const val CORPUS_GENERATOR_VERSION = 1
        const val WORKSPACE_DIRECTORY = "dex-r5-performance-v1"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.DexCompilerService"
        val REAL_COMPONENT = DexCompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        const val OPT_IN_ENABLED_KEY = "dex_compiler_experimental_enabled"
        const val OPT_IN_COMPONENT_KEY = "dex_compiler_experimental_component"
        const val ANSWER_METHOD = "answer"
        const val EXPECTED_ANSWER = 42
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L
        const val EXTERNAL_SAMPLE_SETTLE_MILLIS = 750L
        const val SAMPLE_TIMEOUT_SECONDS = 180L
        const val EXECUTOR_TERMINATION_SECONDS = 5L
        val LOWER_SHA256 = Regex("[0-9a-f]{64}")
    }
}
