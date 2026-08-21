package org.autojs.plugin.yolo.fake.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.yolo.api.IYoloCallback
import org.autojs.plugin.yolo.api.IYoloProvider
import org.autojs.plugin.yolo.api.IYoloSession
import org.autojs.plugin.yolo.api.YoloBackend
import org.autojs.plugin.yolo.api.YoloCapabilities
import org.autojs.plugin.yolo.api.YoloCodec
import org.autojs.plugin.yolo.api.YoloContract
import org.autojs.plugin.yolo.api.YoloDevice
import org.autojs.plugin.yolo.api.YoloModelArtifact
import org.autojs.plugin.yolo.api.YoloModelFormat
import org.autojs.plugin.yolo.api.YoloPixelFormat
import org.autojs.plugin.yolo.api.YoloProtocolVersion
import org.autojs.plugin.yolo.api.YoloProviderInfo
import org.autojs.plugin.yolo.api.YoloResourceLimits
import org.autojs.plugin.yolo.api.YoloTask
import org.autojs.plugin.yolo.api.YoloValidation
import org.autojs.plugin.yolo.fake.FakeYoloScenario
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Test-only remote provider. No scenario switch is reachable from production code or AIDL. */
class FakeYoloProviderService : Service() {
    private lateinit var callerVerifier: FakeYoloCallerVerifier
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakeYoloSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = FakeYoloCallerVerifier(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessions.toList().forEach(RemoteFakeYoloSession::serviceDestroyed)
        sessions.clear()
        super.onDestroy()
    }

    private val binder = object : IYoloProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return YoloCodec.encodeProviderInfo(providerInfo())
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return YoloCodec.encodeCapabilities(CAPABILITIES)
        }

        override fun openSession(
            request: ByteArray?,
            modelDescriptors: Array<out ParcelFileDescriptor>?,
            callback: IYoloCallback?,
        ): IYoloSession {
            val ownerUid = callerVerifier.enforceAllowedCaller()
            val incoming = requireNotNull(modelDescriptors) {
                "Fake YOLO model descriptors are missing"
            }
            try {
                val decoded = YoloCodec.decodeOpenSessionRequest(
                    requireNotNull(request) { "Fake YOLO open request is missing" }.copyOf(),
                    incoming.size,
                )
                YoloValidation.validateOpenSessionRequestAgainst(
                    request = decoded,
                    descriptorCount = incoming.size,
                    capabilities = CAPABILITIES,
                    negotiatedVersion = decoded.protocolVersion,
                )
                val scenario = readScenarioManifest(decoded.artifacts, incoming)
                val session = RemoteFakeYoloSession(
                    context = this@FakeYoloProviderService,
                    ownerUid = ownerUid,
                    scenario = scenario,
                    callback = requireNotNull(callback) { "Fake YOLO callback is missing" },
                    callerVerifier = callerVerifier,
                    capabilities = CAPABILITIES,
                    onFinished = { sessions.remove(it) },
                )
                sessions += session
                return session
            } finally {
                incoming.forEach { runCatching { it.close() } }
            }
        }
    }

    private fun providerInfo(): YoloProviderInfo {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return YoloProviderInfo(
            providerId = "autojs6-yolo-hostile-conformance",
            displayName = "AutoJs6 YOLO Hostile Conformance",
            author = "AutoJs6 Test",
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = versionCode,
            backend = YoloBackend.NCNN,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        ).also(YoloValidation::validateProviderInfo)
    }

    private fun readScenarioManifest(
        artifacts: List<YoloModelArtifact>,
        descriptors: Array<out ParcelFileDescriptor>,
    ): FakeYoloScenario {
        val declaration = artifacts.single { it.role == YoloContract.REQUIRED_MANIFEST_ROLE }
        require(declaration.sizeBytes <= MAX_FAKE_MANIFEST_BYTES) {
            "Fake YOLO manifest exceeds its test limit"
        }
        val descriptor = descriptors[declaration.descriptorIndex]
        require(descriptor.statSize == declaration.sizeBytes) {
            "Fake YOLO manifest descriptor length is invalid"
        }
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val expected = declaration.sizeBytes.toInt()
            val result = ByteArray(expected)
            var offset = 0
            while (offset < result.size) {
                val count = input.read(result, offset, result.size - offset)
                require(count > 0) { "Fake YOLO manifest ended before its declared length" }
                offset += count
            }
            require(input.read() == -1) { "Fake YOLO manifest exceeds its declared length" }
            result
        }
        require(
            MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(bytes),
                declaration.sha256.toByteArray(),
            ),
        ) { "Fake YOLO manifest SHA-256 does not match" }
        return FakeYoloScenario.fromManifest(bytes)
    }

    private companion object {
        const val MAX_FAKE_MANIFEST_BYTES = 4L * 1024L

        val CAPABILITIES = YoloCapabilities(
            protocolMin = YoloProtocolVersion(YoloContract.PROTOCOL_MAJOR, 0),
            protocolMax = YoloProtocolVersion(YoloContract.PROTOCOL_MAJOR, YoloContract.PROTOCOL_MINOR),
            backend = YoloBackend.NCNN,
            backendVersion = "hostile-conformance-v1",
            tasks = listOf(YoloTask.DETECT),
            modelFormats = listOf(YoloModelFormat.NCNN),
            devices = listOf(YoloDevice.CPU),
            pixelFormats = listOf(YoloPixelFormat.RGBA_8888),
            decoderIds = listOf("ultralytics-detect"),
            artifactRoles = listOf(YoloContract.REQUIRED_MANIFEST_ROLE),
            limits = YoloResourceLimits(
                maxModelDescriptors = 1,
                maxModelArtifactBytes = MAX_FAKE_MANIFEST_BYTES,
                maxTotalModelBytes = MAX_FAKE_MANIFEST_BYTES,
                maxImageDimension = YoloContract.MAX_IMAGE_DIMENSION,
                maxImageBytes = YoloContract.MAX_IMAGE_BYTES,
                maxDetections = YoloContract.MAX_DETECTIONS,
                maxCpuThreads = 8,
                maxTimeoutMillis = 2L * 60L * 1_000L,
                maxConcurrentSessions = YoloContract.MAX_CONCURRENT_SESSIONS,
                maxQueuedDetections = YoloContract.MAX_QUEUED_DETECTIONS,
            ),
        ).also(YoloValidation::validateCapabilities)
    }
}

internal class FakeYoloCallerVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val providerPackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val actualUid = Binder.getCallingUid()
        if (actualUid != expectedUid) {
            throw SecurityException("Fake YOLO session caller UID does not match its owner")
        }
        enforceAllowedUid(actualUid)
    }

    @Suppress("DEPRECATION")
    private fun enforceAllowedUid(uid: Int) {
        val hostUid = try {
            packageManager.getApplicationInfo(HOST_PACKAGE, 0).uid
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val packages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        val signaturesMatch = packageManager.checkSignatures(
            providerPackageName,
            HOST_PACKAGE,
        ) == PackageManager.SIGNATURE_MATCH
        val rejection = when {
            HOST_PACKAGE !in packages -> "HOST_PACKAGE_NOT_OWNED"
            hostUid == null -> "HOST_NOT_INSTALLED"
            uid != hostUid -> "HOST_UID_MISMATCH"
            !signaturesMatch -> "SIGNATURE_MISMATCH"
            else -> null
        }
        if (rejection != null) {
            throw SecurityException("Fake YOLO provider caller rejected: $rejection")
        }
    }

    private companion object {
        const val HOST_PACKAGE = "org.autojs.autojs6"
    }
}
