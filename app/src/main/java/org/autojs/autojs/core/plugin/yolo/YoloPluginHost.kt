package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.yolo.api.IYoloProvider
import org.autojs.plugin.yolo.api.IYoloSession
import org.autojs.plugin.yolo.api.YoloBackend
import org.autojs.plugin.yolo.api.YoloCapabilities
import org.autojs.plugin.yolo.api.YoloCodec
import org.autojs.plugin.yolo.api.YoloContract
import org.autojs.plugin.yolo.api.YoloDetectResult
import org.autojs.plugin.yolo.api.YoloDevice
import org.autojs.plugin.yolo.api.YoloModelFormat
import org.autojs.plugin.yolo.api.YoloNegotiation
import org.autojs.plugin.yolo.api.YoloOpenSessionFailureCodec
import org.autojs.plugin.yolo.api.YoloOpenSessionRequest
import org.autojs.plugin.yolo.api.YoloProviderInfo
import org.autojs.plugin.yolo.api.YoloRequestId
import org.autojs.plugin.yolo.api.YoloTask
import org.autojs.plugin.yolo.api.YoloValidation
import java.io.Closeable
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.UUID

/**
 * Exact-component, default-off Host for the isolated YOLO provider process.
 *
 * Discovery metadata is advisory only. Every session pins the installed package identity and
 * re-reads typed metadata and capabilities from the same dedicated Binder used to open it.
 */
object YoloPluginHost {

    private val providerHost = AidlPluginHost(
        action = YoloContract.SERVICE_ACTION,
        tag = "YoloPluginHost",
        moduleLabel = "YOLO",
        proxyFactory = { binder ->
            requireNotNull(IYoloProvider.Stub.asInterface(binder)) {
                "YOLO provider Binder has no AIDL interface"
            }
        },
        infoReader = { provider ->
            requireRemoteInterface(provider.asBinder(), IYoloProvider.DESCRIPTOR, "provider")
            decodeProviderInfo(provider).toCommonPluginInfo()
        },
        defaultEnabled = false,
        defaultEnabledFor = { context, packageName -> PluginDefaultEnabledPolicy.isEnabledByDefault(context, YoloContract.ENGINE_ID, packageName) },
    )

    data class ProviderIdentity(
        val component: ComponentName,
        val packageUid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerSha256: List<String>,
    )

    class Target internal constructor(
        val identity: ProviderIdentity,
        val providerInfo: YoloProviderInfo,
        val capabilities: YoloCapabilities,
        internal val discovered: AidlPluginHost.Discovered,
    ) {
        val component: ComponentName
            get() = identity.component

        val providerUid: Int
            get() = identity.packageUid
    }

    data class ModelArtifactFile(
        val role: String,
        val logicalName: String,
        val file: File,
    )

    data class OpenSessionOptions(
        val task: YoloTask = YoloTask.DETECT,
        val modelFormat: YoloModelFormat = YoloModelFormat.NCNN,
        val device: YoloDevice = YoloDevice.CPU,
        val cpuThreads: Int = DEFAULT_CPU_THREADS,
        val decoderId: String = DEFAULT_DECODER_ID,
        val timeoutMillis: Long = DEFAULT_OPEN_TIMEOUT_MILLIS,
    )

    data class DetectOptions(
        val confidenceThreshold: Double = DEFAULT_CONFIDENCE_THRESHOLD,
        val iouThreshold: Double = DEFAULT_IOU_THRESHOLD,
        val maxDetections: Int = DEFAULT_MAX_DETECTIONS,
        val timeoutMillis: Long = YoloContract.DEFAULT_TIMEOUT_MILLIS,
        val cancellationGraceMillis: Long = DEFAULT_CANCELLATION_GRACE_MILLIS,
    )

    data class DetectionObservation(
        val requestId: YoloRequestId,
        val sequence: Long,
        val imageDescriptorClosedAfterDispatch: Boolean,
        val callbackUid: Int?,
        val terminalCount: Int,
    )

    class Session internal constructor(
        private val delegate: YoloRemoteSession,
    ) : Closeable {
        val component: ComponentName
            get() = delegate.component

        val providerUid: Int
            get() = delegate.providerUid

        val providerInfo: YoloProviderInfo
            get() = delegate.providerInfo

        val capabilities: YoloCapabilities
            get() = delegate.capabilities

        val isClosed: Boolean
            get() = delegate.isClosed

        val isRemoteBinder: Boolean
            get() = delegate.isRemoteBinder

        val modelDescriptorsClosedAfterOpen: Boolean
            get() = delegate.modelDescriptorsClosedAfterOpen

        val lastDetectionObservation: DetectionObservation?
            get() = delegate.lastDetectionObservation

        val lastImageDescriptorClosedAfterDispatch: Boolean
            get() = delegate.lastImageDescriptorClosedAfterDispatch

        val lastTerminalCount: Int
            get() = delegate.lastTerminalCount

        val lastCallbackUid: Int?
            get() = delegate.lastCallbackUid

        suspend fun detect(
            bitmap: Bitmap,
            options: DetectOptions = DetectOptions(),
        ): YoloDetectResult = delegate.detect(bitmap, options)

        suspend fun detect(
            context: Context,
            bitmap: Bitmap,
            options: DetectOptions = DetectOptions(),
        ): YoloDetectResult = delegate.detect(context, bitmap, options)

        override fun close() = delegate.close()
    }

    /** Resolve only the component explicitly chosen by the caller; no engine-only fallback. */
    suspend fun requireExactTarget(
        context: Context,
        component: ComponentName,
    ): Target = requireExactTarget(context, component, requireEnabled = true)

    private suspend fun requireExactTarget(
        context: Context,
        component: ComponentName,
        requireEnabled: Boolean,
    ): Target = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val inspectedBefore = inspectExact(appContext, normalizeComponent(component))
        requireAuthorized(appContext, inspectedBefore.identity.component.packageName, requireEnabled)

        val provisional = AidlPluginHost.Discovered(
            serviceInfo = inspectedBefore.serviceInfo,
            pluginInfo = null,
            error = null,
        )
        val handshake = providerHost.call(
            context = appContext,
            target = provisional,
            enforceHostVersionCompatibility = false,
            enforceAuthorization = true,
            retryOnRemoteException = false,
        ) { provider ->
            requireRemoteInterface(provider.asBinder(), IYoloProvider.DESCRIPTOR, "provider")
            readHandshake(provider)
        }
        validateHandshake(handshake, inspectedBefore.identity)

        val inspectedAfter = inspectExact(appContext, inspectedBefore.identity.component)
        check(inspectedAfter.identity == inspectedBefore.identity) {
            "YOLO provider package identity changed during discovery"
        }
        requireAuthorized(appContext, inspectedAfter.identity.component.packageName, requireEnabled)

        Target(
            identity = inspectedAfter.identity,
            providerInfo = handshake.providerInfo,
            capabilities = handshake.capabilities,
            discovered = AidlPluginHost.Discovered(
                serviceInfo = inspectedAfter.serviceInfo,
                pluginInfo = handshake.providerInfo.toCommonPluginInfo(),
                error = null,
            ),
        )
    }

    /**
     * Plugin Center entry point: a package is accepted only when it declares one YOLO service.
     * Plugin Center keeps the package logically disabled until this probe succeeds, so the probe
     * requires authorization and the exact-component handshake but not the saved enable state;
     * every runtime entry point still requires it.
     * zh-CN: 插件中心入口. 探测成功前插件中心保持逻辑禁用, 因此这里只要求授权与精确组件握手,
     * 不要求已保存的启用状态; 所有运行时入口仍然要求.
     */
    suspend fun probePackage(
        context: Context,
        packageName: String,
    ): Target = withContext(Dispatchers.IO) {
        require(packageName.isNotBlank()) { "YOLO provider package name is blank" }
        val appContext = context.applicationContext
        requireAuthorized(appContext, packageName, requireEnabled = false)
        val services = queryYoloServices(appContext, packageName)
        require(services.size == 1) {
            "Expected exactly one YOLO service in $packageName; found ${services.size}"
        }
        requireExactTarget(appContext, services.single().toComponentName(), requireEnabled = false)
    }

    /**
     * Opens a session on a dedicated binding. Model descriptors remain Host-owned only until the
     * synchronous Binder call returns, then are closed before this function returns.
     */
    suspend fun openSession(
        context: Context,
        target: Target,
        modelArtifacts: List<ModelArtifactFile>,
        options: OpenSessionOptions = OpenSessionOptions(),
    ): Session = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        requireEnabledAndAuthorized(appContext, target.component.packageName)
        val current = inspectExact(appContext, target.component)
        check(current.identity == target.identity) {
            "YOLO provider package identity changed before session open"
        }

        val prepared = YoloModelTransport.prepare(
            modelArtifacts.map { artifact ->
                YoloModelTransport.Source(
                    role = artifact.role,
                    logicalName = artifact.logicalName,
                    file = artifact.file,
                )
            },
        )
        val callback = YoloCallbackBridge(target.providerUid)
        var leased: AidlPluginHost.LeasedCall<OpenedRemoteSession>? = null
        var modelDescriptorsClosed = false
        try {
            leased = providerHost.callWithDedicatedBindingLease(
                context = appContext,
                target = target.discovered,
                enforceHostVersionCompatibility = false,
                enforceAuthorization = true,
                retryOnRemoteException = false,
            ) { provider ->
                requireRemoteInterface(provider.asBinder(), IYoloProvider.DESCRIPTOR, "provider")
                val handshake = readHandshake(provider)
                validateHandshake(handshake, target.identity)
                check(handshake.providerInfo == target.providerInfo) {
                    "YOLO provider metadata changed after selection"
                }
                check(handshake.capabilities == target.capabilities) {
                    "YOLO provider capabilities changed after selection"
                }

                val negotiatedVersion = YoloNegotiation.requireNegotiated(handshake.capabilities)
                val request = YoloOpenSessionRequest(
                    requestId = YoloRequestId.fromUuid(UUID.randomUUID()),
                    protocolVersion = negotiatedVersion,
                    task = options.task,
                    modelFormat = options.modelFormat,
                    device = options.device,
                    cpuThreads = options.cpuThreads,
                    decoderId = options.decoderId,
                    timeoutMillis = options.timeoutMillis,
                    artifacts = prepared.artifacts,
                )
                YoloValidation.validateOpenSessionRequestAgainst(
                    request = request,
                    descriptorCount = prepared.descriptors.size,
                    capabilities = handshake.capabilities,
                    negotiatedVersion = negotiatedVersion,
                )

                val remote = try {
                    requireNotNull(
                        provider.openSession(
                            YoloCodec.encodeOpenSessionRequest(request),
                            prepared.descriptors,
                            callback,
                        ),
                    ) { "YOLO provider returned a null session" }
                } catch (error: IllegalArgumentException) {
                    val failure = YoloOpenSessionFailureCodec.decode(error)
                    if (failure == null) throw error
                    throw YoloSessionOpenException(failure.code, failure.message, error)
                }

                try {
                    requireRemoteInterface(remote.asBinder(), IYoloSession.DESCRIPTOR, "session")
                    OpenedRemoteSession(
                        remote = remote,
                        providerInfo = handshake.providerInfo,
                        capabilities = handshake.capabilities,
                    )
                } catch (error: Throwable) {
                    runCatching { remote.close() }
                    throw error
                }
            }
        } finally {
            prepared.close()
            modelDescriptorsClosed = prepared.descriptors.all(::isClosed)
        }

        val openedLease = checkNotNull(leased)
        if (!modelDescriptorsClosed) {
            runCatching { openedLease.value.remote.close() }
            runCatching { openedLease.bindingLease.close() }
            error("Host YOLO model descriptors remained open after session creation")
        }
        try {
            val inspectedAfter = inspectExact(appContext, target.component)
            check(inspectedAfter.identity == target.identity) {
                "YOLO provider package identity changed while opening the session"
            }
            requireEnabledAndAuthorized(appContext, target.component.packageName)
            Session(
                YoloRemoteSession.create(
                    applicationContext = appContext,
                    component = target.component,
                    providerUid = target.providerUid,
                    providerInfo = openedLease.value.providerInfo,
                    capabilities = openedLease.value.capabilities,
                    remote = openedLease.value.remote,
                    callback = callback,
                    bindingLease = openedLease.bindingLease,
                    modelDescriptorsClosedAfterOpen = modelDescriptorsClosed,
                ),
            )
        } catch (error: Throwable) {
            runCatching { openedLease.value.remote.close() }
            runCatching { openedLease.bindingLease.close() }
            throw error
        }
    }

    private fun readHandshake(provider: IYoloProvider): ProviderHandshake {
        val providerInfo = decodeProviderInfo(provider)
        val capabilities = YoloCodec.decodeCapabilities(
            requireNotNull(provider.capabilities) { "YOLO provider capabilities are missing" },
        )
        YoloValidation.validateCapabilitiesAgainstInfo(capabilities, providerInfo)
        return ProviderHandshake(providerInfo, capabilities)
    }

    private fun decodeProviderInfo(provider: IYoloProvider): YoloProviderInfo =
        YoloCodec.decodeProviderInfo(
            requireNotNull(provider.providerInfo) { "YOLO provider metadata is missing" },
        )

    private fun validateHandshake(
        handshake: ProviderHandshake,
        identity: ProviderIdentity,
    ) {
        val info = handshake.providerInfo
        val capabilities = handshake.capabilities
        YoloValidation.validateCapabilitiesAgainstInfo(capabilities, info)
        check(info.backend == YoloBackend.NCNN) { "R2 accepts the NCNN YOLO backend only" }
        check(info.versionCode == identity.versionCode) {
            "YOLO provider metadata version does not match its installed package"
        }
        check(info.supportedAbis.any(Build.SUPPORTED_ABIS.toSet()::contains)) {
            "YOLO provider does not support this device ABI"
        }
        val hostVersion = BuildConfig.VERSION_CODE.toLong()
        val minHostVersionCode = info.minHostVersionCode
        val maxHostVersionCode = info.maxHostVersionCode
        check(minHostVersionCode == null || hostVersion >= minHostVersionCode) {
            "YOLO provider requires host version $minHostVersionCode; current is $hostVersion"
        }
        check(maxHostVersionCode == null || hostVersion <= maxHostVersionCode) {
            "YOLO provider supports host versions only through $maxHostVersionCode; current is $hostVersion"
        }
        YoloNegotiation.requireNegotiated(capabilities)
    }

    private fun requireEnabledAndAuthorized(context: Context, packageName: String) {
        requireAuthorized(context, packageName, requireEnabled = true)
    }

    private fun requireAuthorized(context: Context, packageName: String, requireEnabled: Boolean) {
        if (requireEnabled) {
            val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(context, YoloContract.ENGINE_ID, packageName)
            check(PluginEnableStore.isEnabled(context, packageName, defaultEnabled)) {
                "YOLO provider $packageName is disabled; enable it in Plugin Center"
            }
        }
        check(PluginTrustManager.isAuthorized(context, packageName)) {
            "YOLO provider is not authorized: $packageName"
        }
    }

    private fun inspectExact(context: Context, component: ComponentName): InspectedProvider {
        val packageManager = context.packageManager
        val exact = queryYoloServices(context, component.packageName).filter { service ->
            service.toComponentName() == component
        }
        require(exact.size == 1) {
            "Expected exactly one YOLO service for explicit component $component; found ${exact.size}"
        }
        val serviceInfo = exact.single()
        require(serviceInfo.exported) { "YOLO provider service is not exported: $component" }
        require(serviceInfo.enabled) { "YOLO provider service is disabled: $component" }
        require(serviceInfo.permission == YoloContract.PLUGIN_PERMISSION) {
            "YOLO provider service must require ${YoloContract.PLUGIN_PERMISSION}"
        }

        val packageFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES.toLong()
        }
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                component.packageName,
                PackageManager.PackageInfoFlags.of(packageFlags),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(component.packageName, packageFlags.toInt())
        }
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                component.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(component.packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        require(applicationInfo.enabled) { "YOLO provider application is disabled" }
        require(serviceInfo.applicationInfo.uid == applicationInfo.uid) {
            "YOLO provider service UID changed while inspecting the package"
        }
        require(applicationInfo.uid != context.applicationInfo.uid) {
            "YOLO provider must execute under a UID separate from the Host"
        }
        require(component.packageName != context.packageName) {
            "YOLO provider must be installed as a separate application"
        }
        require(
            packageManager.checkSignatures(context.packageName, component.packageName) ==
                PackageManager.SIGNATURE_MATCH,
        ) { "YOLO provider and Host APK signatures do not match" }

        val signers = PluginSignatureUtils.getSha256Fingerprints(context, component.packageName)
            .sorted()
        require(signers.isNotEmpty()) { "YOLO provider has no current APK signer" }
        val identity = ProviderIdentity(
            component = component,
            packageUid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = Collections.unmodifiableList(ArrayList(signers)),
        )
        require(identity.packageUid >= 0) { "YOLO provider UID is invalid" }
        require(identity.versionCode > 0L) { "YOLO provider package version is invalid" }
        require(identity.lastUpdateTime >= 0L) { "YOLO provider update time is invalid" }
        return InspectedProvider(serviceInfo, identity)
    }

    private fun queryYoloServices(context: Context, packageName: String): List<ServiceInfo> {
        val intent = Intent(YoloContract.SERVICE_ACTION).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(packageName)
        }
        val queryFlags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(queryFlags),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, queryFlags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun YoloProviderInfo.toCommonPluginInfo(): PluginInfo = PluginInfo().also { common ->
        common.name = displayName
        common.author = author
        common.versionName = versionName
        common.versionCode = versionCode
        common.id = providerId
        common.engine = YoloContract.ENGINE_ID
        common.variant = backend.name.lowercase()
        common.supportedAbis = supportedAbis.toTypedArray()
        common.capabilities = Bundle().apply {
            minHostVersionCode?.let { putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, it) }
            putString("backend", backend.name.lowercase())
            putBoolean("supportsDetect", true)
            putBoolean("supportsSharedSession", true)
            putBoolean("supportsRawImage", true)
        }
    }

    private fun normalizeComponent(component: ComponentName): ComponentName {
        require(component.packageName.isNotBlank()) { "YOLO provider package name is blank" }
        require(component.className.isNotBlank()) { "YOLO provider service class is blank" }
        return if (component.className.startsWith('.')) {
            ComponentName(component.packageName, component.packageName + component.className)
        } else {
            component
        }
    }

    private fun ServiceInfo.toComponentName(): ComponentName = normalizeComponent(
        ComponentName(packageName, name),
    )

    private fun requireRemoteInterface(binder: IBinder, descriptor: String, label: String) {
        check(binder.isBinderAlive) { "YOLO $label Binder is not alive" }
        check(binder.interfaceDescriptor == descriptor) {
            "YOLO $label Binder descriptor mismatch"
        }
        check(binder.queryLocalInterface(descriptor) == null) {
            "YOLO $label Binder unexpectedly resolved in the Host process"
        }
    }

    private fun isClosed(descriptor: android.os.ParcelFileDescriptor): Boolean =
        runCatching { !descriptor.fileDescriptor.valid() }.getOrDefault(true)

    private data class ProviderHandshake(
        val providerInfo: YoloProviderInfo,
        val capabilities: YoloCapabilities,
    )

    private data class InspectedProvider(
        val serviceInfo: ServiceInfo,
        val identity: ProviderIdentity,
    )

    internal data class OpenedRemoteSession(
        val remote: IYoloSession,
        val providerInfo: YoloProviderInfo,
        val capabilities: YoloCapabilities,
    )

    const val DEFAULT_DECODER_ID = "ultralytics-detect"
    const val DEFAULT_CPU_THREADS = 4
    const val DEFAULT_OPEN_TIMEOUT_MILLIS = 120_000L
    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25
    const val DEFAULT_IOU_THRESHOLD = 0.45
    const val DEFAULT_MAX_DETECTIONS = 100
    const val DEFAULT_CANCELLATION_GRACE_MILLIS = 1_000L
}

class YoloSessionOpenException(
    val code: org.autojs.plugin.yolo.api.YoloErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
