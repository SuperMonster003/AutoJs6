package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.plugin.yolo.api.IYoloCallback
import org.autojs.plugin.yolo.api.IYoloProvider
import org.autojs.plugin.yolo.api.IYoloSession
import org.autojs.plugin.yolo.api.YoloCodec
import org.autojs.plugin.yolo.api.YoloCapabilities
import org.autojs.plugin.yolo.api.YoloDetection
import org.autojs.plugin.yolo.api.YoloDevice
import org.autojs.plugin.yolo.api.YoloErrorCode
import org.autojs.plugin.yolo.api.YoloModelArtifact
import org.autojs.plugin.yolo.api.YoloModelFormat
import org.autojs.plugin.yolo.api.YoloNegotiation
import org.autojs.plugin.yolo.api.YoloOpenSessionFailureCodec
import org.autojs.plugin.yolo.api.YoloOpenSessionRequest
import org.autojs.plugin.yolo.api.YoloRequestId
import org.autojs.plugin.yolo.api.YoloSha256
import org.autojs.plugin.yolo.api.YoloTask
import org.autojs.plugin.yolo.api.YoloValidation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Opt-in R5 descriptor-integrity acceptance against the independently installed NCNN provider.
 *
 * The three hostile calls deliberately bypass [YoloPluginHost]'s locally generated declarations,
 * while retaining its exact selected component and negotiated capabilities. This proves that the
 * remote provider independently rejects regular descriptors whose stat length or SHA-256 differs
 * from the versioned request. The final inference and reopen use only the production Host path.
 */
class YoloRealProviderR5DescriptorAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun hostileRegularDescriptorsFailVersionedThenProductionHostDetectsAndReopens() {
        assumeTrue(
            "R5 YOLO descriptor instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertEquals(HOST_PACKAGE, context.packageName)

        val resultFile = File(context.filesDir, RESULT_FILE_NAME)
        assertTrue("Unable to remove stale R5 descriptor result", !resultFile.exists() || resultFile.delete())
        val fixtureRoot = File(context.filesDir, FIXTURE_DIRECTORY_NAME)
        val manifest = fixtureRoot.requireFixture("model-manifest-v1.json")
        val param = fixtureRoot.requireFixture("model.ncnn.param")
        val bin = fixtureRoot.requireFixture("model.ncnn.bin")
        val imageFile = fixtureRoot.requireFixture("bus.jpg")
        val expectedFile = fixtureRoot.requireFixture("expected-bus-detections.json")
        val fixtureArtifacts = listOf(
            FixtureArtifact("manifest", manifest),
            FixtureArtifact("ncnn-param", param),
            FixtureArtifact("ncnn-bin", bin),
        )

        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, PROVIDER_PACKAGE)
        assertTrue("The installed provider has no signer fingerprint", fingerprints.isNotEmpty())
        val state = PluginStateSnapshot.capture(context, PROVIDER_PACKAGE, fingerprints)
        var binding: BoundProvider? = null
        var firstSession: YoloPluginHost.Session? = null
        var reopenedSession: YoloPluginHost.Session? = null
        var primaryFailure: Throwable? = null
        try {
            fingerprints.forEach { fingerprint ->
                PluginAuthorizationStore.grant(context, PROVIDER_PACKAGE, fingerprint)
            }
            PluginEnableStore.setEnabled(context, PROVIDER_PACKAGE, true)
            assertTrue(PluginAuthorizationStore.isGranted(context, PROVIDER_PACKAGE, fingerprints))
            assertTrue(PluginEnableStore.isEnabled(context, PROVIDER_PACKAGE, defaultEnabled = false))

            val target = runBlocking {
                YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT)
            }
            assertEquals(PROVIDER_COMPONENT, target.component)
            assertTrue("Provider unexpectedly shares the Host UID", target.providerUid != context.applicationInfo.uid)
            assertTrue(
                "The real provider does not advertise the required decoder",
                DECODER_ID in target.capabilities.decoderIds,
            )

            val direct = bindActionlessExactProvider()
            binding = direct
            assertEquals(
                "The actionless Binder capabilities differ from the selected exact target",
                target.capabilities,
                YoloCodec.decodeCapabilities(requireNotNull(direct.provider.capabilities)),
            )

            val protocolVersion = YoloNegotiation.requireNegotiated(target.capabilities)
            val cpuThreads = minOf(CPU_THREADS, target.capabilities.limits.maxCpuThreads)
            val sessionTimeoutMillis = minOf(
                SESSION_TIMEOUT_MILLIS,
                target.capabilities.limits.maxTimeoutMillis,
            )
            val baseline = fixtureArtifacts.mapIndexed { index, artifact ->
                YoloModelArtifact(
                    role = artifact.role,
                    logicalName = artifact.file.name,
                    sizeBytes = artifact.file.length(),
                    sha256 = sha256(artifact.file),
                    descriptorIndex = index,
                )
            }
            assertTrue("Manifest fixture is too small for both mismatch directions", manifest.length() > 1L)

            val callback = RecordingCallback()
            val hostile = listOf(
                assertInvalidDescriptorFailure(
                    provider = direct.provider,
                    callback = callback,
                    capabilities = target.capabilities,
                    fixtureArtifacts = fixtureArtifacts,
                    request = request(
                        protocolVersion = protocolVersion,
                        cpuThreads = cpuThreads,
                        timeoutMillis = sessionTimeoutMillis,
                        artifacts = baseline.replaceManifest {
                            it.copy(sizeBytes = it.sizeBytes + 1L)
                        },
                    ),
                    caseName = "descriptor-shorter-than-declared",
                    actualSize = manifest.length(),
                    declaredSize = manifest.length() + 1L,
                ),
                assertInvalidDescriptorFailure(
                    provider = direct.provider,
                    callback = callback,
                    capabilities = target.capabilities,
                    fixtureArtifacts = fixtureArtifacts,
                    request = request(
                        protocolVersion = protocolVersion,
                        cpuThreads = cpuThreads,
                        timeoutMillis = sessionTimeoutMillis,
                        artifacts = baseline.replaceManifest {
                            it.copy(sizeBytes = it.sizeBytes - 1L)
                        },
                    ),
                    caseName = "descriptor-longer-than-declared",
                    actualSize = manifest.length(),
                    declaredSize = manifest.length() - 1L,
                ),
                assertInvalidDescriptorFailure(
                    provider = direct.provider,
                    callback = callback,
                    capabilities = target.capabilities,
                    fixtureArtifacts = fixtureArtifacts,
                    request = request(
                        protocolVersion = protocolVersion,
                        cpuThreads = cpuThreads,
                        timeoutMillis = sessionTimeoutMillis,
                        artifacts = baseline.replaceManifest {
                            it.copy(sha256 = differentSha256(it.sha256))
                        },
                    ),
                    caseName = "same-length-bad-sha256",
                    actualSize = manifest.length(),
                    declaredSize = manifest.length(),
                ),
            )
            assertEquals("A rejected session unexpectedly emitted a callback", 0, callback.count.get())
            val idleAfterHostile = awaitProviderIdle("three hostile descriptor failures")

            val model = fixtureArtifacts.map { artifact ->
                YoloPluginHost.ModelArtifactFile(artifact.role, artifact.file.name, artifact.file)
            }
            val openOptions = YoloPluginHost.OpenSessionOptions(
                task = YoloTask.DETECT,
                modelFormat = YoloModelFormat.NCNN,
                device = YoloDevice.CPU,
                cpuThreads = cpuThreads,
                decoderId = DECODER_ID,
                timeoutMillis = sessionTimeoutMillis,
            )
            val first = runBlocking {
                YoloPluginHost.openSession(context, target, model, openOptions)
            }
            firstSession = first
            assertTrue("Production Host retained a model descriptor", first.modelDescriptorsClosedAfterOpen)
            assertTrue("Production Host did not receive a remote session Binder", first.isRemoteBinder)

            val bitmap = requireNotNull(BitmapFactory.decodeFile(imageFile.absolutePath)) {
                "Unable to decode the externally staged bus image"
            }
            val expected = ExpectedFixture.parse(expectedFile)
            val detected = try {
                assertEquals(expected.width, bitmap.width)
                assertEquals(expected.height, bitmap.height)
                runBlocking {
                    first.detect(
                        context,
                        bitmap,
                        YoloPluginHost.DetectOptions(
                            confidenceThreshold = expected.confidenceThreshold,
                            iouThreshold = expected.iouThreshold,
                            maxDetections = expected.maxDetections,
                            timeoutMillis = minOf(
                                DETECT_TIMEOUT_MILLIS,
                                target.capabilities.limits.maxTimeoutMillis,
                            ),
                        ),
                    )
                }
            } finally {
                bitmap.recycle()
            }
            val comparisons = expected.compare(detected.detections)
            assertTrue("Production Host retained the image descriptor", first.lastImageDescriptorClosedAfterDispatch)
            assertEquals("Exactly one terminal callback is required", 1, first.lastTerminalCount)
            assertEquals("Terminal callback came from an unexpected UID", target.providerUid, first.lastCallbackUid)

            first.close()
            first.close()
            assertTrue("Production session did not close idempotently", first.isClosed)
            val idleAfterFirstClose = awaitProviderIdle("first production session close")

            val reopened = runBlocking {
                YoloPluginHost.openSession(context, target, model, openOptions)
            }
            reopenedSession = reopened
            assertTrue("Reopened production session did not use a remote Binder", reopened.isRemoteBinder)
            assertTrue("Reopened production session retained a model descriptor", reopened.modelDescriptorsClosedAfterOpen)
            reopened.close()
            reopened.close()
            assertTrue("Reopened production session did not close idempotently", reopened.isClosed)
            val idleAfterReopenClose = awaitProviderIdle("reopened production session close")

            writeResult(
                resultFile = resultFile,
                target = target,
                direct = direct,
                hostile = hostile,
                idleAfterHostile = idleAfterHostile,
                idleAfterFirstClose = idleAfterFirstClose,
                idleAfterReopenClose = idleAfterReopenClose,
                session = first,
                detected = detected.detections,
                comparisons = comparisons,
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { reopenedSession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { firstSession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { binding?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { state.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun request(
        protocolVersion: org.autojs.plugin.yolo.api.YoloProtocolVersion,
        cpuThreads: Int,
        timeoutMillis: Long,
        artifacts: List<YoloModelArtifact>,
    ) = YoloOpenSessionRequest(
        requestId = YoloRequestId.fromUuid(UUID.randomUUID()),
        protocolVersion = protocolVersion,
        task = YoloTask.DETECT,
        modelFormat = YoloModelFormat.NCNN,
        device = YoloDevice.CPU,
        cpuThreads = cpuThreads,
        decoderId = DECODER_ID,
        timeoutMillis = timeoutMillis,
        artifacts = artifacts,
    )

    private fun assertInvalidDescriptorFailure(
        provider: IYoloProvider,
        callback: IYoloCallback,
        capabilities: YoloCapabilities,
        fixtureArtifacts: List<FixtureArtifact>,
        request: YoloOpenSessionRequest,
        caseName: String,
        actualSize: Long,
        declaredSize: Long,
    ): FailureObservation {
        YoloValidation.validateOpenSessionRequestAgainst(
            request = request,
            descriptorCount = fixtureArtifacts.size,
            capabilities = capabilities,
            negotiatedVersion = request.protocolVersion,
        )
        val descriptors = fixtureArtifacts.map { artifact ->
            ParcelFileDescriptor.open(artifact.file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.toTypedArray()
        var unexpectedSession: IYoloSession? = null
        var caught: IllegalArgumentException? = null
        try {
            unexpectedSession = provider.openSession(
                YoloCodec.encodeOpenSessionRequest(request),
                descriptors,
                callback,
            )
        } catch (error: IllegalArgumentException) {
            caught = error
        } finally {
            descriptors.forEach { descriptor -> runCatching { descriptor.close() } }
        }
        unexpectedSession?.let { session ->
            runCatching { session.close() }
            fail("$caseName unexpectedly opened a remote session")
        }
        val hostDescriptorsClosed = descriptors.all { descriptor ->
            runCatching { !descriptor.fileDescriptor.valid() }.getOrDefault(true)
        }
        assertTrue(
            "$caseName left a Host-owned descriptor open",
            hostDescriptorsClosed,
        )
        val exception = requireNotNull(caught) { "$caseName did not fail synchronously" }
        val decoded = requireNotNull(YoloOpenSessionFailureCodec.decode(exception)) {
            "$caseName did not return a versioned open-session failure"
        }
        assertEquals("$caseName returned the wrong stable code", YoloErrorCode.INVALID_REQUEST, decoded.code)
        return FailureObservation(
            caseName = caseName,
            actualSize = actualSize,
            declaredSize = declaredSize,
            code = decoded.code,
            message = decoded.message,
            versioned = exception.message?.startsWith(YoloOpenSessionFailureCodec.MESSAGE_PREFIX) == true,
            hostDescriptorsClosed = hostDescriptorsClosed,
        )
    }

    private fun bindActionlessExactProvider(): BoundProvider {
        val connected = CountDownLatch(1)
        val provider = AtomicReference<IYoloProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (name != PROVIDER_COMPONENT) {
                    failure.compareAndSet(null, SecurityException("Unexpected YOLO provider component: $name"))
                } else {
                    provider.compareAndSet(null, IYoloProvider.Stub.asInterface(service))
                }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit

            override fun onBindingDied(name: ComponentName) {
                failure.compareAndSet(null, IllegalStateException("YOLO provider binding died before connection"))
                connected.countDown()
            }

            override fun onNullBinding(name: ComponentName) {
                failure.compareAndSet(null, IllegalStateException("YOLO provider returned a null binding"))
                connected.countDown()
            }
        }
        val intent = Intent().apply {
            component = PROVIDER_COMPONENT
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        check(intent.action == null) { "R5 direct provider bind must remain actionless" }
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw IllegalStateException("Unable to bind the exact real YOLO provider")
        }
        try {
            if (!connected.await(BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw TimeoutException("Timed out binding the exact real YOLO provider")
            }
            failure.get()?.let { throw it }
            val remote = checkNotNull(provider.get()) { "YOLO provider connected without an interface" }
            assertEquals(IYoloProvider.DESCRIPTOR, remote.asBinder().interfaceDescriptor)
            assertTrue("Direct provider Binder is not alive", remote.asBinder().isBinderAlive)
            assertTrue(
                "Expected a remote Binder proxy for the isolated provider",
                remote.asBinder().queryLocalInterface(IYoloProvider.DESCRIPTOR) == null,
            )
            return BoundProvider(context, connection, remote)
        } catch (error: Throwable) {
            runCatching { context.unbindService(connection) }
            throw error
        }
    }

    /** Waits on the provider's release-safe diagnostics predicate; no fixed cleanup sleep. */
    private fun awaitProviderIdle(label: String): ProviderIdleSnapshot {
        val deadline = SystemClock.elapsedRealtime() + PROVIDER_IDLE_TIMEOUT_MILLIS
        var lastSnapshot: ProviderIdleSnapshot? = null
        while (true) {
            val output = executeShellCommand("dumpsys activity service ${PROVIDER_COMPONENT.flattenToShortString()}")
            val line = output.lineSequence()
                .map { candidate -> candidate.trim() }
                .firstOrNull { candidate -> candidate.startsWith(PROVIDER_DIAGNOSTICS_PREFIX) }
            if (line != null) {
                val json = JSONObject(line.removePrefix(PROVIDER_DIAGNOSTICS_PREFIX))
                assertEquals(1, json.getInt("schemaVersion"))
                val snapshot = ProviderIdleSnapshot(
                    activeSessions = json.getInt("activeSessions"),
                    sessionGate = json.getBoolean("sessionGate"),
                    activeNativeHandles = json.getInt("activeNativeHandles"),
                    modelDirCount = json.getInt("modelDirCount"),
                )
                lastSnapshot = snapshot
                if (snapshot.isIdle) return snapshot
            }
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                throw AssertionError("Provider did not become idle after $label; last=$lastSnapshot")
            }
            CountDownLatch(1).await(minOf(PROVIDER_IDLE_POLL_MILLIS, remaining), TimeUnit.MILLISECONDS)
        }
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }
    }

    private fun writeResult(
        resultFile: File,
        target: YoloPluginHost.Target,
        direct: BoundProvider,
        hostile: List<FailureObservation>,
        idleAfterHostile: ProviderIdleSnapshot,
        idleAfterFirstClose: ProviderIdleSnapshot,
        idleAfterReopenClose: ProviderIdleSnapshot,
        session: YoloPluginHost.Session,
        detected: List<YoloDetection>,
        comparisons: List<DetectionComparison>,
    ) {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("roadmapStage", "R5-DESCRIPTOR")
            .put("status", "PASS")
            .put("hostPackage", HOST_PACKAGE)
            .put("providerPackage", PROVIDER_PACKAGE)
            .put("component", target.component.flattenToString())
            .put("providerUid", target.providerUid)
            .put("actionlessExactComponentBind", true)
            .put("providerInterfaceDescriptor", direct.provider.asBinder().interfaceDescriptor)
            .put("remoteProviderBinder", true)
            .put("hostileDescriptors", JSONArray().apply {
                hostile.forEach { observation -> put(observation.toJson()) }
            })
            .put("idleAfterHostileDescriptors", idleAfterHostile.toJson())
            .put("production", JSONObject()
                .put("remoteSessionBinder", session.isRemoteBinder)
                .put("modelDescriptorsClosedAfterOpen", session.modelDescriptorsClosedAfterOpen)
                .put("imageDescriptorClosedAfterDispatch", session.lastImageDescriptorClosedAfterDispatch)
                .put("terminalCount", session.lastTerminalCount)
                .put("idempotentClose", session.isClosed)
                .put("reopenSucceeded", true)
                .put("idleAfterFirstClose", idleAfterFirstClose.toJson())
                .put("idleAfterReopenClose", idleAfterReopenClose.toJson())
                .put("detections", JSONArray().apply {
                    detected.forEach { detection ->
                        put(JSONObject()
                            .put("classId", detection.classId)
                            .put("label", detection.label)
                            .put("confidence", detection.confidence)
                            .put("bounds", JSONArray(listOf(
                                detection.bounds.left,
                                detection.bounds.top,
                                detection.bounds.right,
                                detection.bounds.bottom,
                            ))))
                    }
                })
                .put("comparisons", JSONArray().apply {
                    comparisons.forEach { comparison ->
                        put(JSONObject()
                            .put("classId", comparison.classId)
                            .put("label", comparison.label)
                            .put("iou", comparison.iou)
                            .put("confidenceDelta", comparison.confidenceDelta))
                    }
                }))
        resultFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    private fun List<YoloModelArtifact>.replaceManifest(
        transform: (YoloModelArtifact) -> YoloModelArtifact,
    ): List<YoloModelArtifact> = map { artifact ->
        if (artifact.role == "manifest") transform(artifact) else artifact
    }

    private fun sha256(file: File): YoloSha256 {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return YoloSha256.fromBytes(digest.digest())
    }

    private fun differentSha256(value: YoloSha256): YoloSha256 {
        val bytes = value.toByteArray()
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        return YoloSha256.fromBytes(bytes)
    }

    private fun File.requireFixture(name: String): File {
        val child = File(this, name)
        assertTrue("Externally staged fixture is missing: $child", child.isFile)
        assertTrue("Externally staged fixture is empty: $child", child.length() > 0L)
        return child
    }

    private data class FixtureArtifact(val role: String, val file: File)

    private data class FailureObservation(
        val caseName: String,
        val actualSize: Long,
        val declaredSize: Long,
        val code: YoloErrorCode,
        val message: String,
        val versioned: Boolean,
        val hostDescriptorsClosed: Boolean,
    ) {
        fun toJson() = JSONObject()
            .put("case", caseName)
            .put("actualSize", actualSize)
            .put("declaredSize", declaredSize)
            .put("code", code.name)
            .put("wireCode", code.wireCode)
            .put("message", message)
            .put("versionedFailure", versioned)
            .put("hostDescriptorsClosed", hostDescriptorsClosed)
    }

    private data class ProviderIdleSnapshot(
        val activeSessions: Int,
        val sessionGate: Boolean,
        val activeNativeHandles: Int,
        val modelDirCount: Int,
    ) {
        val isIdle: Boolean
            get() = activeSessions == 0 && !sessionGate && activeNativeHandles == 0 && modelDirCount == 0

        fun toJson() = JSONObject()
            .put("activeSessions", activeSessions)
            .put("sessionGate", sessionGate)
            .put("activeNativeHandles", activeNativeHandles)
            .put("modelDirCount", modelDirCount)
    }

    private class RecordingCallback : IYoloCallback.Stub() {
        val count = AtomicInteger(0)

        override fun onDetected(result: ByteArray?) {
            count.incrementAndGet()
        }

        override fun onFailed(error: ByteArray?) {
            count.incrementAndGet()
        }

        override fun onCancelled(cancellation: ByteArray?) {
            count.incrementAndGet()
        }
    }

    private class BoundProvider(
        private val context: Context,
        private val connection: ServiceConnection,
        val provider: IYoloProvider,
    ) : Closeable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) context.unbindService(connection)
        }
    }

    private data class ExpectedFixture(
        val width: Int,
        val height: Int,
        val confidenceThreshold: Double,
        val iouThreshold: Double,
        val maxDetections: Int,
        val minimumBoxIou: Double,
        val maximumConfidenceDelta: Double,
        val detections: List<ExpectedDetection>,
    ) {
        fun compare(actual: List<YoloDetection>): List<DetectionComparison> {
            assertEquals("Detection count differs from the locked fixture", detections.size, actual.size)
            val unmatched = actual.indices.toMutableSet()
            return detections.mapIndexed { expectedIndex, expected ->
                val candidate = requireNotNull(unmatched
                    .asSequence()
                    .filter { index -> actual[index].classId == expected.classId && actual[index].label == expected.label }
                    .map { index -> index to expected.iou(actual[index]) }
                    .maxByOrNull { it.second }) {
                    "No class/label match for expected detection $expectedIndex"
                }
                unmatched.remove(candidate.first)
                val found = actual[candidate.first]
                val confidenceDelta = abs(found.confidence - expected.confidence)
                assertTrue("Detection $expectedIndex IoU ${candidate.second} is below $minimumBoxIou", candidate.second >= minimumBoxIou)
                assertTrue(
                    "Detection $expectedIndex confidence delta $confidenceDelta exceeds $maximumConfidenceDelta",
                    confidenceDelta <= maximumConfidenceDelta,
                )
                DetectionComparison(expected.classId, expected.label, candidate.second, confidenceDelta)
            }.also {
                assertTrue("Actual result contains unmatched detections: $unmatched", unmatched.isEmpty())
            }
        }

        companion object {
            fun parse(file: File): ExpectedFixture {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                assertEquals(1, root.getInt("schemaVersion"))
                val image = root.getJSONObject("image")
                val options = root.getJSONObject("options")
                val comparison = root.getJSONObject("comparison")
                val array = root.getJSONArray("detections")
                return ExpectedFixture(
                    width = image.getInt("width"),
                    height = image.getInt("height"),
                    confidenceThreshold = options.getDouble("confidenceThreshold"),
                    iouThreshold = options.getDouble("iouThreshold"),
                    maxDetections = options.getInt("maxDetections"),
                    minimumBoxIou = comparison.getDouble("minimumBoxIou"),
                    maximumConfidenceDelta = comparison.getDouble("maximumConfidenceDelta"),
                    detections = (0 until array.length()).map { index ->
                        val item = array.getJSONObject(index)
                        val bounds = item.getJSONArray("bounds")
                        ExpectedDetection(
                            classId = item.getInt("classId"),
                            label = item.getString("label"),
                            confidence = item.getDouble("confidence"),
                            left = bounds.getDouble(0),
                            top = bounds.getDouble(1),
                            right = bounds.getDouble(2),
                            bottom = bounds.getDouble(3),
                        )
                    },
                )
            }
        }
    }

    private data class ExpectedDetection(
        val classId: Int,
        val label: String,
        val confidence: Double,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    ) {
        fun iou(actual: YoloDetection): Double {
            val intersectionWidth = max(0.0, min(right, actual.bounds.right) - max(left, actual.bounds.left))
            val intersectionHeight = max(0.0, min(bottom, actual.bounds.bottom) - max(top, actual.bounds.top))
            val intersection = intersectionWidth * intersectionHeight
            val expectedArea = max(0.0, right - left) * max(0.0, bottom - top)
            val actualArea = max(0.0, actual.bounds.right - actual.bounds.left) *
                max(0.0, actual.bounds.bottom - actual.bounds.top)
            val union = expectedArea + actualArea - intersection
            return if (union > 0.0) intersection / union else 0.0
        }
    }

    private data class DetectionComparison(
        val classId: Int,
        val label: String,
        val iou: Double,
        val confidenceDelta: Double,
    )

    private data class StoredBoolean(val present: Boolean, val value: Boolean)

    private data class PluginStateSnapshot(
        val packageName: String,
        val enable: StoredBoolean,
        val authorizations: Map<String, StoredBoolean>,
    ) {
        fun restore(context: Context) {
            val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
            val enableEditor = enablePreferences.edit().remove(enableKey(packageName))
            if (enable.present) enableEditor.putBoolean(enableKey(packageName), enable.value)
            check(enableEditor.commit()) { "Unable to restore YOLO provider enable state" }

            val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
            val authorizationEditor = authorizationPreferences.edit()
            authorizations.forEach { (fingerprint, stored) ->
                val key = authorizationKey(packageName, fingerprint)
                authorizationEditor.remove(key)
                if (stored.present) authorizationEditor.putBoolean(key, stored.value)
            }
            check(authorizationEditor.commit()) { "Unable to restore YOLO provider authorization state" }
        }

        companion object {
            fun capture(context: Context, packageName: String, fingerprints: List<String>): PluginStateSnapshot {
                val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
                val enabledKey = enableKey(packageName)
                val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
                return PluginStateSnapshot(
                    packageName = packageName,
                    enable = StoredBoolean(
                        present = enablePreferences.contains(enabledKey),
                        value = enablePreferences.getBoolean(enabledKey, false),
                    ),
                    authorizations = fingerprints.associateWith { fingerprint ->
                        val key = authorizationKey(packageName, fingerprint)
                        StoredBoolean(
                            present = authorizationPreferences.contains(key),
                            value = authorizationPreferences.getBoolean(key, false),
                        )
                    },
                )
            }
        }
    }

    companion object {
        private const val ARG_ENABLED = "yoloR5DescriptorEnabled"
        private const val HOST_PACKAGE = "org.autojs.autojs6"
        private const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
        private const val FIXTURE_DIRECTORY_NAME = "yolo-r5-fixture"
        private const val RESULT_FILE_NAME = "yolo-r5-descriptor-result.json"
        private const val ENABLE_SP_NAME = "plugin_center_enable_state"
        private const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
        private const val DECODER_ID = "ultralytics-detect"
        private const val CPU_THREADS = 4
        private const val BIND_TIMEOUT_MILLIS = 10_000L
        private const val SESSION_TIMEOUT_MILLIS = 120_000L
        private const val DETECT_TIMEOUT_MILLIS = 120_000L
        private const val PROVIDER_IDLE_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_IDLE_POLL_MILLIS = 50L
        private const val HASH_BUFFER_BYTES = 64 * 1024
        private const val PROVIDER_DIAGNOSTICS_PREFIX = "AUTOJS6_YOLO_DIAGNOSTICS_V1="
        private val PROVIDER_COMPONENT = ComponentName(
            PROVIDER_PACKAGE,
            "$PROVIDER_PACKAGE.provider.YoloProviderService",
        )

        private fun enableKey(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"
        private fun authorizationKey(packageName: String, fingerprint: String) =
            "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
    }
}
