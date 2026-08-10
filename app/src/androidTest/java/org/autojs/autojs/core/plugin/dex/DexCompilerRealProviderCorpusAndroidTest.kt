package org.autojs.autojs.core.plugin.dex

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Explicitly opted-in real-plugin D8 corpus checks.
 *
 * Every input is a raw JAR containing JVM class files. No prebuilt DEX is embedded. The tests use
 * the exact, same-signed production plugin service through [DexCompilerExperimentalHarness], keep
 * host validation and publication in the path, copy the returned generation capability into
 * read-only host storage, and execute it with the real [DexClassLoader].
 *
 * Enable only on a prepared device with:
 * `-e autojs.dexCompiler.realProvider.corpus.enabled true`.
 *
 * This class covers successful Java/Kotlin and single-/multi-dex conversion, one missing-library
 * resolution boundary, and explicit duplicate-loading semantics. It does not claim the wider
 * failure, BUSY, Binder-death, cancellation, timeout, API/ABI, or dependency corpus.
 */
class DexCompilerRealProviderCorpusAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun realD8CompilesJavaAndKotlinSingleDexAndSupportsDefinedDuplicateLoading() {
        val program = javaAndKotlinProgramJar().also { it.assertWithinProtocolBounds() }
        withPublishedArtifact(program) { published ->
            assertEquals(1, published.validation.dexEntryCount)
            assertEquals(2, published.validation.classDefinitionCount)

            copyForClassLoading(published.artifact, "java-kotlin").use { loadable ->
                val firstLoader = loadable.newLoader("first")
                val javaFirst = firstLoader.loadClass(JAVA_FIXTURE_CLASS_NAME)
                assertEquals(42, javaFirst.invokeStaticInt("answer"))
                assertSame(
                    "Repeated loading through one ClassLoader must preserve Class identity",
                    javaFirst,
                    firstLoader.loadClass(JAVA_FIXTURE_CLASS_NAME),
                )

                val kotlinFirst = firstLoader.loadClass(KOTLIN_FIXTURE_CLASS_NAME)
                assertEquals(84, kotlinFirst.invokeStaticInt("kotlinAnswer"))
                assertSame(kotlinFirst, firstLoader.loadClass(KOTLIN_FIXTURE_CLASS_NAME))

                val secondLoader = loadable.newLoader("second")
                val javaSecond = secondLoader.loadClass(JAVA_FIXTURE_CLASS_NAME)
                val kotlinSecond = secondLoader.loadClass(KOTLIN_FIXTURE_CLASS_NAME)
                assertNotSame(
                    "Independent DexClassLoader instances must define independent Class identities",
                    javaFirst,
                    javaSecond,
                )
                assertNotSame(kotlinFirst, kotlinSecond)
                assertEquals(42, javaSecond.invokeStaticInt("answer"))
                assertEquals(84, kotlinSecond.invokeStaticInt("kotlinAnswer"))
            }
        }
    }

    @Test
    fun realD8PublishesMissingDependencyAndArtFailsAtExactArtResolutionBoundary() {
        val program = missingDependencyProgramJar().also { it.assertWithinProtocolBounds() }
        withPublishedArtifact(program) { published ->
            assertEquals(1, published.validation.dexEntryCount)
            assertEquals(2, published.validation.classDefinitionCount)

            copyForClassLoading(published.artifact, "missing-dependency").use { loadable ->
                val loader = loadable.newLoader("resolution")
                assertEquals(
                    7,
                    loader.loadClass(HEALTHY_FIXTURE_CLASS_NAME).invokeStaticInt("answer"),
                )

                val absentFromParent = runCatching {
                    requireNotNull(javaClass.classLoader).loadClass(ABSENT_DEPENDENCY_CLASS_NAME)
                }.exceptionOrNull()
                assertTrue(
                    "The deliberately absent dependency unexpectedly exists in the parent loader",
                    absentFromParent is ClassNotFoundException,
                )

                val failure = requireNotNull(runCatching {
                    Class.forName(MISSING_FIXTURE_CLASS_NAME, true, loader)
                }.exceptionOrNull()) {
                    "ART unexpectedly resolved a class whose direct superclass is absent"
                }
                val failureGraph = failure.failureGraph().toList()
                assertTrue(
                    "Missing dependency failure did not name the exact absent class: $failure",
                    failureGraph.any { error ->
                        error is ClassNotFoundException || error is NoClassDefFoundError
                    } && failureGraph.any { error ->
                        error.message.orEmpty().replace('/', '.').contains(ABSENT_DEPENDENCY_CLASS_NAME)
                    },
                )
            }
        }
    }

    @Test
    fun realD8GeneratesAuditableMultiDexAndLoadsClassesFromPrimaryAndSecondaryDex() {
        val program = multiDexProgramJar().also { it.assertWithinProtocolBounds() }
        assertTrue(
            "The generated class corpus does not cross the DEX method-id boundary",
            program.classEntryCount.toLong() * MULTIDEX_METHODS_PER_CLASS > DEX_MEMBER_ID_LIMIT,
        )

        withPublishedArtifact(program, timeoutMillis = DexCompilerContract.MAX_TIMEOUT_MILLIS) { published ->
            assertTrue(
                "The real D8 plugin did not emit multi-dex output: ${published.validation}",
                published.validation.dexEntryCount >= 2,
            )
            assertEquals(MULTIDEX_CLASS_COUNT, published.validation.classDefinitionCount)
            assertTrue(
                "Multi-dex output exceeds the host's in-memory validation/publication ceiling",
                published.artifact.sizeBytes <= HOST_MAX_OUTPUT_BYTES,
            )

            copyForClassLoading(published.artifact, "multi-dex").use { loadable ->
                val definitionsByDex = readClassDefinitionsByDex(loadable.archive)
                assertEquals(
                    expectedDexEntryNames(published.validation.dexEntryCount),
                    definitionsByDex.keys.toList(),
                )
                assertEquals(
                    MULTIDEX_CLASS_COUNT,
                    definitionsByDex.values.sumOf { descriptors -> descriptors.size },
                )

                val primaryDescriptor = requireNotNull(
                    definitionsByDex.getValue("classes.dex")
                        .firstOrNull { it.startsWith(MULTIDEX_DESCRIPTOR_PREFIX) },
                ) { "classes.dex contains no generated corpus class" }
                val secondaryDescriptor = requireNotNull(
                    definitionsByDex.getValue("classes2.dex")
                        .firstOrNull { it.startsWith(MULTIDEX_DESCRIPTOR_PREFIX) },
                ) { "classes2.dex contains no generated corpus class" }
                assertTrue(primaryDescriptor != secondaryDescriptor)

                val loader = loadable.newLoader("primary-secondary")
                assertEquals(0, loader.loadClass(primaryDescriptor.toBinaryName()).invokeStaticInt("m0"))
                assertEquals(0, loader.loadClass(secondaryDescriptor.toBinaryName()).invokeStaticInt("m0"))
            }
        }
    }

    private fun javaAndKotlinProgramJar(): ProgramJar {
        val kotlinBytes = Base64.decode(KOTLIN_FIXTURE_CLASS_BASE64, Base64.NO_WRAP)
        assertEquals(KOTLIN_FIXTURE_CLASS_SIZE, kotlinBytes.size)
        assertEquals(KOTLIN_FIXTURE_CLASS_SHA256, kotlinBytes.sha256())
        assertTrue(
            "Embedded Kotlin fixture lost its kotlin.Metadata annotation",
            String(kotlinBytes, StandardCharsets.ISO_8859_1).contains("Lkotlin/Metadata;"),
        )
        return buildProgramJar(expectedClassEntries = 2) { zip ->
            val javaBytes = javaClassBytes(
                internalName = JAVA_FIXTURE_INTERNAL_NAME,
                sourceFile = "JavaCorpusFixture.java",
                staticIntMethods = listOf(StaticIntMethod("answer", 42)),
            )
            zip.writeStoredEntry(JAVA_FIXTURE_CLASS_ENTRY, javaBytes)
            zip.writeStoredEntry(KOTLIN_FIXTURE_CLASS_ENTRY, kotlinBytes)
            javaBytes.size.toLong() + kotlinBytes.size
        }
    }

    private fun missingDependencyProgramJar(): ProgramJar =
        buildProgramJar(expectedClassEntries = 2) { zip ->
            val healthy = javaClassBytes(
                internalName = HEALTHY_FIXTURE_INTERNAL_NAME,
                sourceFile = "HealthyAlongsideMissing.java",
                staticIntMethods = listOf(StaticIntMethod("answer", 7)),
            )
            val missing = javaClassBytes(
                internalName = MISSING_FIXTURE_INTERNAL_NAME,
                superInternalName = ABSENT_DEPENDENCY_INTERNAL_NAME,
                sourceFile = "MissingDependencyFixture.java",
                staticIntMethods = listOf(StaticIntMethod("answer", 9)),
            )
            zip.writeStoredEntry(HEALTHY_FIXTURE_CLASS_ENTRY, healthy)
            zip.writeStoredEntry(MISSING_FIXTURE_CLASS_ENTRY, missing)
            healthy.size.toLong() + missing.size
        }

    private fun multiDexProgramJar(): ProgramJar =
        buildProgramJar(expectedClassEntries = MULTIDEX_CLASS_COUNT) { zip ->
            var totalClassBytes = 0L
            repeat(MULTIDEX_CLASS_COUNT) { index ->
                val suffix = index.toString().padStart(4, '0')
                val internalName = "$MULTIDEX_INTERNAL_PREFIX$suffix"
                val bytes = javaClassBytes(
                    internalName = internalName,
                    sourceFile = "Multi$suffix.java",
                    staticIntMethods = MULTIDEX_STATIC_METHODS,
                )
                zip.writeStoredEntry("$internalName.class", bytes)
                totalClassBytes += bytes.size
            }
            totalClassBytes
        }

    private fun buildProgramJar(
        expectedClassEntries: Int,
        writeClasses: (ZipOutputStream) -> Long,
    ): ProgramJar {
        val nonce = UUID.randomUUID().toString().toByteArray(StandardCharsets.US_ASCII)
        var classBytes = 0L
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                classBytes = writeClasses(zip)
                zip.writeStoredEntry("META-INF/autojs6-dex-corpus/${UUID.randomUUID()}.nonce", nonce)
            }
            output.toByteArray()
        }
        return ProgramJar(
            bytes = bytes,
            classEntryCount = expectedClassEntries,
            totalClassBytes = classBytes,
        )
    }

    private fun ProgramJar.assertWithinProtocolBounds() {
        assertTrue(bytes.isNotEmpty())
        assertTrue(
            "Program JAR exceeds the real plugin's 64 MiB input ceiling",
            bytes.size.toLong() <= REAL_PROVIDER_MAX_COMPRESSED_PROGRAM_BYTES,
        )
        assertTrue(
            "Program JAR entry count exceeds the V1 contract",
            classEntryCount + 1 <= DexCompilerContract.MAX_ARCHIVE_ENTRIES,
        )
        assertTrue(
            "Program class bytes exceed the V1 contract",
            totalClassBytes <= DexCompilerContract.MAX_TOTAL_CLASS_BYTES,
        )
        var classes = 0
        var resources = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    assertTrue("Program JAR embeds forbidden prebuilt DEX: ${entry.name}", !entry.name.endsWith(".dex"))
                    if (entry.name.endsWith(".class")) classes++ else resources++
                }
                zip.closeEntry()
            }
        }
        assertEquals(classEntryCount, classes)
        assertEquals(1, resources)
    }

    private fun <T> withPublishedArtifact(
        program: ProgramJar,
        timeoutMillis: Long = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
        block: (DexCompilerExperimentalOutcome.Published) -> T,
    ): T {
        val identity = requireRealProvider()
        val scope = RealHarnessScope()
        var artifact: DexCompilerVerifiedArtifact? = null
        var primaryFailure: Throwable? = null
        try {
            val outcome = scope.runAttempt(identity, program.bytes, timeoutMillis)
            assertTrue("The real D8 plugin did not publish the corpus: $outcome", outcome is DexCompilerExperimentalOutcome.Published)
            outcome as DexCompilerExperimentalOutcome.Published
            assertEquals(identity, outcome.identity)
            artifact = outcome.artifact
            return block(outcome)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            artifact?.let { exact ->
                runCatching {
                    assertTrue("Unable to invalidate the exact corpus cache generation", exact.invalidate())
                    val reopened = exact.openRead()
                    reopened?.close()
                    assertTrue("Invalidated corpus generation can still be opened", reopened == null)
                }.exceptionOrNull()?.let(cleanupFailures::add)
            }
            runCatching { scope.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                val failure = primaryFailure
                if (failure != null) failure.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }

    private inner class RealHarnessScope : Closeable {
        private val worker = boundedExecutor("dex-real-corpus-control", queueCapacity = 64)
        private val blocking = ThreadPoolExecutor(
            0,
            2,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            namedThreadFactory("dex-real-corpus-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        private val drain = boundedExecutor("dex-real-corpus-drain", queueCapacity = 1)
        private val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("dex-real-corpus-deadline"),
        )
        private val store = AndroidDexCompilerArtifactStore(context, drain)
        private val harness = DexCompilerExperimentalHarness(
            transport = AndroidDexCompilerExperimentalTransport(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asDeadlineScheduler(),
            threadProbe = DexCompilerThreadProbe { Looper.myLooper() == Looper.getMainLooper() },
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            hostRuntimeApi = Build.VERSION.SDK_INT,
            verifiedCache = DexCompilerVerifiedCache(store::lookupVerified),
        )

        fun runAttempt(
            identity: DexCompilerPinnedIdentity,
            program: ByteArray,
            timeoutMillis: Long,
        ): DexCompilerExperimentalOutcome {
            val snapshot = store.snapshot(
                input = ByteArrayInputStream(program),
                maximumBytes = DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES,
            )
            val output = try {
                store.outputTransaction()
            } catch (error: Throwable) {
                snapshot.close()
                throw error
            }
            val request = try {
                DexCompilerRuntimeRequestFactory.create(
                    snapshot = snapshot,
                    minApi = Build.VERSION.SDK_INT,
                    timeoutMillis = timeoutMillis,
                )
            } catch (error: Throwable) {
                runCatching { output.rollback() }
                runCatching { output.close() }
                snapshot.close()
                throw error
            }
            val terminal = CountDownLatch(1)
            val outcome = AtomicReference<DexCompilerExperimentalOutcome?>()
            var handle: Closeable? = null
            try {
                handle = harness.start(
                    DexCompilerExperimentalInvocation(
                        request = request,
                        inputSnapshot = snapshot,
                        outputTransaction = output,
                        releaseBuild = !BuildConfig.DEBUG,
                        preferredComponent = REAL_COMPONENT,
                        requestFinalization = DexCompilerRequestFinalization.CAPABILITY_BOUND,
                        requiredIdentity = identity,
                    ),
                ) { value ->
                    if (outcome.compareAndSet(null, value)) terminal.countDown()
                }
                assertTrue(
                    "The real D8 corpus did not reach a terminal host outcome",
                    terminal.await(timeoutMillis + OUTCOME_WAIT_ALLOWANCE_MILLIS, TimeUnit.MILLISECONDS),
                )
                return checkNotNull(outcome.get()) { "The real D8 corpus returned no terminal outcome" }
            } finally {
                handle?.close()
                if (handle == null) {
                    runCatching { output.rollback() }
                    runCatching { output.close() }
                    runCatching { snapshot.close() }
                } else if (outcome.get() == null) {
                    terminal.await(TERMINAL_CLEANUP_WAIT_MILLIS, TimeUnit.MILLISECONDS)
                }
            }
        }

        override fun close() {
            listOf(worker, blocking, drain).forEach(ThreadPoolExecutor::shutdownNow)
            scheduler.shutdownNow()
            listOf(worker, blocking, drain, scheduler).forEach { executor ->
                assertTrue(
                    "Corpus executor did not terminate: $executor",
                    executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                )
            }
        }
    }

    private fun requireRealProvider(): DexCompilerPinnedIdentity {
        assumeTrue(
            "Real D8 corpus instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertTrue("Real D8 corpus instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(HOST_PACKAGE, context.packageName)

        val packageManager = context.packageManager
        val applicationInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(REAL_PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real D8 plugin package is not installed: $REAL_PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(REAL_PLUGIN_PACKAGE, applicationInfo.packageName)
        assertEquals(
            "The real D8 plugin and host must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )

        val transport = AndroidDexCompilerExperimentalTransport(context)
        val candidates = transport.discoverExactAction(DexCompilerContract.SERVICE_ACTION)
            .filter { candidate -> candidate.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals(
            "The real package must expose exactly the expected D8 service",
            listOf(REAL_COMPONENT),
            candidates.map(DexCompilerServiceCandidate::component),
        )
        val candidate = candidates.single()
        assertEquals(DexCompilerContract.SERVICE_ACTION, candidate.declaredAction)
        assertTrue(candidate.exported)
        assertTrue(candidate.applicationEnabled)
        assertTrue(candidate.serviceEnabled)
        assertEquals(DexCompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertTrue(candidate.signerSha256.isNotEmpty())
        assertTrue(candidate.sameSignerAsHost)
        assertTrue(DexCompilerExperimentalPolicy.rejection(candidate, releaseBuild = !BuildConfig.DEBUG) == null)

        val identity = candidate.pin()
        assertEquals(REAL_COMPONENT, identity.component)
        assertEquals(identity, transport.inspectExact(REAL_COMPONENT)?.pin())
        return identity
    }

    private fun copyForClassLoading(
        artifact: DexCompilerVerifiedArtifact,
        label: String,
    ): LoadableArtifact {
        assertTrue(
            "Verified DEX ZIP exceeds the host's 16 MiB in-memory ceiling",
            artifact.sizeBytes in 1L..HOST_MAX_OUTPUT_BYTES,
        )
        val root = File(context.codeCacheDir, "dex-real-corpus-$label-${UUID.randomUUID()}")
        assertTrue("Unable to create corpus class-loader root", root.mkdirs())
        val archive = File(root, "artifact.dex.zip")
        try {
            val source = requireNotNull(artifact.openRead()) {
                "The corpus artifact capability could not be opened"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            source.use { input ->
                FileOutputStream(archive).use { output ->
                    // Android 14+ requires dynamic code to be read-only. The already-open writer
                    // remains valid until fsync/close, while the pathname cannot be reopened for
                    // writing before DexClassLoader adopts it.
                    assertTrue("Unable to make corpus DEX read-only", archive.setReadOnly())
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= artifact.sizeBytes) { "Corpus artifact exceeded its declared size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            assertEquals(artifact.sizeBytes, copied)
            assertEquals(artifact.outputSha256, digest.digest().toLowerHex())
            return LoadableArtifact(root, archive)
        } catch (error: Throwable) {
            root.deleteRecursively()
            throw error
        }
    }

    private inner class LoadableArtifact(
        private val root: File,
        val archive: File,
    ) : Closeable {
        fun newLoader(label: String): DexClassLoader {
            val optimized = File(root, "optimized-$label")
            assertTrue("Unable to create corpus optimization directory", optimized.mkdirs())
            return DexClassLoader(
                archive.absolutePath,
                optimized.absolutePath,
                null,
                requireNotNull(javaClass.classLoader),
            )
        }

        override fun close() {
            assertTrue("Unable to remove corpus class-loader files", !root.exists() || root.deleteRecursively())
        }
    }

    private fun readClassDefinitionsByDex(archive: File): LinkedHashMap<String, Set<String>> {
        val result = linkedMapOf<String, Set<String>>()
        ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertTrue("DEX ZIP contains a directory entry", !entry.isDirectory)
                val dexBytes = ByteArrayOutputStream().use { output ->
                    zip.copyTo(output)
                    output.toByteArray()
                }
                result[entry.name] = parseDexClassDescriptors(entry.name, dexBytes)
                zip.closeEntry()
            }
        }
        return result
    }

    private fun parseDexClassDescriptors(entryName: String, bytes: ByteArray): Set<String> {
        require(bytes.size >= DEX_HEADER_SIZE) { "$entryName is smaller than a DEX header" }
        require(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte()))) {
            "$entryName has invalid DEX magic"
        }
        require(bytes.leUInt(DEX_FILE_SIZE_OFFSET) == bytes.size.toLong()) {
            "$entryName file size does not match its header"
        }
        val stringCount = bytes.leUInt(DEX_STRING_IDS_SIZE_OFFSET).checkedInt("string count")
        val stringOffset = bytes.leUInt(DEX_STRING_IDS_OFFSET_OFFSET).checkedInt("string offset")
        val typeCount = bytes.leUInt(DEX_TYPE_IDS_SIZE_OFFSET).checkedInt("type count")
        val typeOffset = bytes.leUInt(DEX_TYPE_IDS_OFFSET_OFFSET).checkedInt("type offset")
        val classCount = bytes.leUInt(DEX_CLASS_DEFS_SIZE_OFFSET).checkedInt("class count")
        val classOffset = bytes.leUInt(DEX_CLASS_DEFS_OFFSET_OFFSET).checkedInt("class offset")
        bytes.requireRange(stringOffset, stringCount.toLong() * 4L, "$entryName string_ids")
        bytes.requireRange(typeOffset, typeCount.toLong() * 4L, "$entryName type_ids")
        bytes.requireRange(classOffset, classCount.toLong() * DEX_CLASS_DEF_SIZE, "$entryName class_defs")
        bytes.validateDexMap(
            entryName = entryName,
            stringCount = stringCount,
            stringOffset = stringOffset,
            typeCount = typeCount,
            typeOffset = typeOffset,
            classCount = classCount,
            classOffset = classOffset,
        )

        return linkedSetOf<String>().apply {
            repeat(classCount) { index ->
                val classIndex = bytes.leUInt(classOffset + index * DEX_CLASS_DEF_SIZE)
                    .checkedInt("$entryName class index")
                require(classIndex in 0 until typeCount) { "$entryName class index is outside type_ids" }
                val descriptorIndex = bytes.leUInt(typeOffset + classIndex * 4)
                    .checkedInt("$entryName descriptor index")
                require(descriptorIndex in 0 until stringCount) { "$entryName descriptor is outside string_ids" }
                val stringDataOffset = bytes.leUInt(stringOffset + descriptorIndex * 4)
                    .checkedInt("$entryName string_data offset")
                add(bytes.readAsciiDexString(stringDataOffset, entryName))
            }
        }.also { descriptors ->
            require(descriptors.size == classCount) { "$entryName contains duplicate class definitions" }
        }
    }

    private fun ByteArray.readAsciiDexString(offset: Int, entryName: String): String {
        require(offset in indices) { "$entryName string_data offset is outside the file" }
        var cursor = offset
        var declaredUtf16Length = 0
        var shift = 0
        repeat(5) {
            require(cursor in indices) { "$entryName has truncated string_data length" }
            val value = this[cursor++].toInt() and 0xff
            declaredUtf16Length = declaredUtf16Length or ((value and 0x7f) shl shift)
            if (value and 0x80 == 0) {
                val end = indexOf(0.toByte(), cursor)
                require(end >= cursor) { "$entryName has unterminated string_data" }
                val decoded = String(this, cursor, end - cursor, StandardCharsets.UTF_8)
                require(decoded.length == declaredUtf16Length) { "$entryName descriptor length is inconsistent" }
                return decoded
            }
            shift += 7
        }
        error("$entryName has an oversized string_data length")
    }

    private fun ByteArray.indexOf(value: Byte, start: Int): Int {
        for (index in start until size) if (this[index] == value) return index
        return -1
    }

    private fun ByteArray.validateDexMap(
        entryName: String,
        stringCount: Int,
        stringOffset: Int,
        typeCount: Int,
        typeOffset: Int,
        classCount: Int,
        classOffset: Int,
    ) {
        val mapOffset = leUInt(DEX_MAP_OFFSET_OFFSET).checkedInt("$entryName map offset")
        requireRange(mapOffset, 4L, "$entryName map size")
        val mapSize = leUInt(mapOffset).checkedInt("$entryName map size")
        require(mapSize > 0) { "$entryName has an empty map_list" }
        requireRange(
            mapOffset + 4,
            mapSize.toLong() * DEX_MAP_ITEM_SIZE,
            "$entryName map_list",
        )
        val items = linkedMapOf<Int, Pair<Int, Int>>()
        repeat(mapSize) { index ->
            val itemOffset = mapOffset + 4 + index * DEX_MAP_ITEM_SIZE
            val type = leUShort(itemOffset)
            require(leUShort(itemOffset + 2) == 0) { "$entryName map item has nonzero unused bits" }
            val size = leUInt(itemOffset + 4).checkedInt("$entryName map item size")
            val offset = leUInt(itemOffset + 8).checkedInt("$entryName map item offset")
            require(size > 0) { "$entryName map item has zero size" }
            require(offset in indices) { "$entryName map item points outside the file" }
            require(items.put(type, size to offset) == null) { "$entryName repeats map type $type" }
        }
        assertEquals(stringCount to stringOffset, items[DEX_MAP_TYPE_STRING_ID])
        assertEquals(typeCount to typeOffset, items[DEX_MAP_TYPE_TYPE_ID])
        assertEquals(classCount to classOffset, items[DEX_MAP_TYPE_CLASS_DEF])
        assertEquals(1 to mapOffset, items[DEX_MAP_TYPE_MAP_LIST])
    }

    private fun ByteArray.leUShort(offset: Int): Int {
        requireRange(offset, 2L, "little-endian ushort")
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.leUInt(offset: Int): Long {
        requireRange(offset, 4L, "little-endian uint")
        return (this[offset].toLong() and 0xffL) or
            ((this[offset + 1].toLong() and 0xffL) shl 8) or
            ((this[offset + 2].toLong() and 0xffL) shl 16) or
            ((this[offset + 3].toLong() and 0xffL) shl 24)
    }

    private fun ByteArray.requireRange(offset: Int, length: Long, label: String) {
        require(offset >= 0 && length >= 0L && offset.toLong() + length <= size.toLong()) {
            "$label is outside the byte array"
        }
    }

    private fun Long.checkedInt(label: String): Int {
        require(this in 0L..Int.MAX_VALUE.toLong()) { "$label is outside Int range" }
        return toInt()
    }

    private fun javaClassBytes(
        internalName: String,
        sourceFile: String,
        staticIntMethods: List<StaticIntMethod>,
        superInternalName: String = "java/lang/Object",
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(0xcafebabe.toInt())
            output.writeShort(0)
            output.writeShort(52)
            output.writeShort(13 + staticIntMethods.size)
            output.writeUtf8Constant(internalName) // #1
            output.writeByte(7); output.writeShort(1) // #2 Class
            output.writeUtf8Constant(superInternalName) // #3
            output.writeByte(7); output.writeShort(3) // #4 Class
            output.writeUtf8Constant("<init>") // #5
            output.writeUtf8Constant("()V") // #6
            output.writeUtf8Constant("Code") // #7
            output.writeByte(12); output.writeShort(5); output.writeShort(6) // #8 NameAndType
            output.writeByte(10); output.writeShort(4); output.writeShort(8) // #9 Methodref
            output.writeUtf8Constant("SourceFile") // #10
            output.writeUtf8Constant(sourceFile) // #11
            output.writeUtf8Constant("()I") // #12
            staticIntMethods.forEach { method -> output.writeUtf8Constant(method.name) }

            output.writeShort(0x0031) // public final super
            output.writeShort(2)
            output.writeShort(4)
            output.writeShort(0) // interfaces
            output.writeShort(0) // fields
            output.writeShort(1 + staticIntMethods.size)

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

            staticIntMethods.forEachIndexed { index, method ->
                val code = intReturnCode(method.value)
                output.writeShort(0x0009) // public static
                output.writeShort(13 + index)
                output.writeShort(12)
                output.writeShort(1)
                output.writeShort(7)
                output.writeInt(12 + code.size)
                output.writeShort(1)
                output.writeShort(0)
                output.writeInt(code.size)
                output.write(code)
                output.writeShort(0)
                output.writeShort(0)
            }

            output.writeShort(1)
            output.writeShort(10)
            output.writeInt(2)
            output.writeShort(11)
        }
        bytes.toByteArray()
    }

    private fun intReturnCode(value: Int): ByteArray = when (value) {
        -1 -> byteArrayOf(0x02, 0xac.toByte())
        in 0..5 -> byteArrayOf((0x03 + value).toByte(), 0xac.toByte())
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> byteArrayOf(0x10, value.toByte(), 0xac.toByte())
        in Short.MIN_VALUE..Short.MAX_VALUE -> byteArrayOf(
            0x11,
            (value ushr 8).toByte(),
            value.toByte(),
            0xac.toByte(),
        )
        else -> error("Fixture integer constant is too large: $value")
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

    private fun Class<*>.invokeStaticInt(methodName: String): Int =
        (getDeclaredMethod(methodName).invoke(null) as Number).toInt()

    private fun String.toBinaryName(): String {
        require(startsWith('L') && endsWith(';')) { "Not a class descriptor: $this" }
        return substring(1, length - 1).replace('/', '.')
    }

    private fun Throwable.failureGraph(): Sequence<Throwable> = sequence {
        val pending = java.util.ArrayDeque<Throwable>()
        val seen = java.util.IdentityHashMap<Throwable, Boolean>()
        pending.add(this@failureGraph)
        while (pending.isNotEmpty() && seen.size < MAX_FAILURE_GRAPH_NODES) {
            val current = pending.removeFirst()
            if (seen.put(current, true) != null) continue
            yield(current)
            current.cause?.let(pending::addLast)
            current.suppressed.forEach(pending::addLast)
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toLowerHex()

    private fun ByteArray.toLowerHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun expectedDexEntryNames(count: Int): List<String> =
        (1..count).map { index -> if (index == 1) "classes.dex" else "classes$index.dex" }

    private fun boundedExecutor(name: String, queueCapacity: Int): ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity),
        namedThreadFactory(name),
        ThreadPoolExecutor.AbortPolicy(),
    )

    private fun namedThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun ScheduledExecutorService.asDeadlineScheduler(): DexCompilerDeadlineScheduler =
        DexCompilerDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private data class ProgramJar(
        val bytes: ByteArray,
        val classEntryCount: Int,
        val totalClassBytes: Long,
    )

    private data class StaticIntMethod(
        val name: String,
        val value: Int,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.dexCompiler.realProvider.corpus.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.DexCompilerService"
        val REAL_COMPONENT = DexCompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)

        const val JAVA_FIXTURE_INTERNAL_NAME =
            "org/autojs/fixture/dexcompiler/corpus/JavaCorpusFixture"
        const val JAVA_FIXTURE_CLASS_ENTRY = "$JAVA_FIXTURE_INTERNAL_NAME.class"
        const val JAVA_FIXTURE_CLASS_NAME =
            "org.autojs.fixture.dexcompiler.corpus.JavaCorpusFixture"

        const val KOTLIN_FIXTURE_CLASS_ENTRY =
            "org/autojs/fixture/dexcompiler/corpus/KotlinCorpusFixtureKt.class"
        const val KOTLIN_FIXTURE_CLASS_NAME =
            "org.autojs.fixture.dexcompiler.corpus.KotlinCorpusFixtureKt"
        const val KOTLIN_FIXTURE_CLASS_SIZE = 448
        const val KOTLIN_FIXTURE_CLASS_SHA256 =
            "e045305dffe6c0d3e0a87512912b7702cc1c55c5a4795a50813295d37c0e7602"

        // Generated by kotlinc-jvm 1.7.10 with `-jvm-target 1.8` from:
        // package org.autojs.fixture.dexcompiler.corpus
        // fun kotlinAnswer(): Int = 84
        const val KOTLIN_FIXTURE_CLASS_BASE64 =
            "yv66vgAAADQAGQEAO29yZy9hdXRvanMvZml4dHVyZS9kZXhjb21waWxlci9jb3JwdXMvS290bGluQ29ycHVzRml4dHVyZUt0BwABAQAQamF2YS9sYW5nL09iamVjdAcAAwEADGtvdGxpbkFuc3dlcgEAAygpSQEAEUxrb3RsaW4vTWV0YWRhdGE7AQACbXYDAAAAAQMAAAAHAQABawMAAAACAQACeGkDAAAAMAEAAmQxAQAawIAICsCACgIQCArAgBoGEMCAGgIwAcKoBgIBAAJkMgEAAAEAE2RleF9jb21waWxlcl9jb3JwdXMBABZLb3RsaW5Db3JwdXNGaXh0dXJlLmt0AQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAClNvdXJjZUZpbGUBABlSdW50aW1lVmlzaWJsZUFubm90YXRpb25zADEAAgAEAAAAAAABABkABQAGAAEAFQAAABsAAQAAAAAAAxBUrAAAAAEAFgAAAAYAAQAAAAMAAgAXAAAAAgAUABgAAAA0AAEABwAFAAhbAANJAAlJAApJAAkAC0kADAANSQAOAA9bAAFzABAAEVsAA3MABXMAEnMAEw=="

        const val HEALTHY_FIXTURE_INTERNAL_NAME =
            "org/autojs/fixture/dexcompiler/corpus/HealthyAlongsideMissing"
        const val HEALTHY_FIXTURE_CLASS_ENTRY = "$HEALTHY_FIXTURE_INTERNAL_NAME.class"
        const val HEALTHY_FIXTURE_CLASS_NAME =
            "org.autojs.fixture.dexcompiler.corpus.HealthyAlongsideMissing"
        const val MISSING_FIXTURE_INTERNAL_NAME =
            "org/autojs/fixture/dexcompiler/corpus/MissingDependencyFixture"
        const val MISSING_FIXTURE_CLASS_ENTRY = "$MISSING_FIXTURE_INTERNAL_NAME.class"
        const val MISSING_FIXTURE_CLASS_NAME =
            "org.autojs.fixture.dexcompiler.corpus.MissingDependencyFixture"
        const val ABSENT_DEPENDENCY_INTERNAL_NAME =
            "org/autojs/fixture/dexcompiler/corpus/AbsentSuperclass"
        const val ABSENT_DEPENDENCY_CLASS_NAME =
            "org.autojs.fixture.dexcompiler.corpus.AbsentSuperclass"

        const val MULTIDEX_INTERNAL_PREFIX =
            "org/autojs/fixture/dexcompiler/corpus/multidex/Multi"
        const val MULTIDEX_DESCRIPTOR_PREFIX =
            "Lorg/autojs/fixture/dexcompiler/corpus/multidex/Multi"
        const val MULTIDEX_CLASS_COUNT = 7_300
        const val MULTIDEX_STATIC_METHOD_COUNT = 8
        const val MULTIDEX_METHODS_PER_CLASS = MULTIDEX_STATIC_METHOD_COUNT + 1L
        val MULTIDEX_STATIC_METHODS = List(MULTIDEX_STATIC_METHOD_COUNT) { index ->
            StaticIntMethod("m$index", index)
        }

        const val DEX_MEMBER_ID_LIMIT = 65_536L
        const val REAL_PROVIDER_MAX_COMPRESSED_PROGRAM_BYTES = 64L * 1024L * 1024L
        const val HOST_MAX_OUTPUT_BYTES = 16L * 1024L * 1024L
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L
        const val OUTCOME_WAIT_ALLOWANCE_MILLIS = 30_000L
        const val TERMINAL_CLEANUP_WAIT_MILLIS = 5_000L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
        const val MAX_FAILURE_GRAPH_NODES = 64

        const val DEX_HEADER_SIZE = 0x70
        const val DEX_FILE_SIZE_OFFSET = 0x20
        const val DEX_MAP_OFFSET_OFFSET = 0x34
        const val DEX_STRING_IDS_SIZE_OFFSET = 0x38
        const val DEX_STRING_IDS_OFFSET_OFFSET = 0x3c
        const val DEX_TYPE_IDS_SIZE_OFFSET = 0x40
        const val DEX_TYPE_IDS_OFFSET_OFFSET = 0x44
        const val DEX_CLASS_DEFS_SIZE_OFFSET = 0x60
        const val DEX_CLASS_DEFS_OFFSET_OFFSET = 0x64
        const val DEX_CLASS_DEF_SIZE = 32
        const val DEX_MAP_ITEM_SIZE = 12
        const val DEX_MAP_TYPE_STRING_ID = 0x0001
        const val DEX_MAP_TYPE_TYPE_ID = 0x0002
        const val DEX_MAP_TYPE_CLASS_DEF = 0x0006
        const val DEX_MAP_TYPE_MAP_LIST = 0x1000
    }
}
