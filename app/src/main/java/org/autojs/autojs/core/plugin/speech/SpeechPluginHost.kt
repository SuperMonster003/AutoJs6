package org.autojs.autojs.core.plugin.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.speech.api.ISpeechPlugin
import org.autojs.plugin.speech.api.ISpeechProgressCallback
import org.autojs.plugin.speech.api.SpeechCapabilityKeys
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import org.autojs.plugin.speech.api.SpeechPluginActions
import org.autojs.plugin.speech.api.SpeechPluginIds
import org.autojs.plugin.speech.api.SpeechResultKeys
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Host client of the speech contract (3-Macaw Speech roadmap D21, D22, `docs/dev/speech-plugin-protocol-v1.md`): any
 * package answering the `org.autojs.plugin.SPEECH` query with the `speech` category and engine may be selected, and the
 * plugin must declare a contract version the host understands plus a `requiresHostVersion` not below the build that
 * ships this module.
 *
 * Every binding carries `BIND_INCLUDE_CAPABILITIES` and `BIND_IMPORTANT` (roadmap D22), so the plugin process that
 * records the microphone inherits the while-in-use capabilities of the host, and from API 34 `BIND_ALLOW_ACTIVITY_STARTS`
 * (roadmap P3.1), so the plugin may open its transparent start activity while the host is visible and start its
 * microphone foreground service from there: Android 14+ refuses that service to a process that is only bound by the
 * visible host, and without it a session goes silent once the host leaves the foreground. Sessions and long operations
 * hold a dedicated binding lease for their whole life; short calls use the pooled binding. Files stay with the host:
 * inputs are opened read-only from the script's paths and checked first, synthesis outputs are staged next to the target
 * and published by [SpeechOutputFile], so the plugin never sees a path. The host keeps no recognition or synthesis
 * implementation of its own.
 *
 * zh-CN: 语音契约的宿主客户端 (3-Macaw Speech 路线图 D21, D22): 任何以 `speech` 类别与引擎应答 `org.autojs.plugin.SPEECH` 查询的包都可被选中,
 * 插件须声明宿主理解的契约版本以及不低于本模块所在构建的 `requiresHostVersion`. 每个绑定都带 `BIND_INCLUDE_CAPABILITIES` 与
 * `BIND_IMPORTANT` (路线图 D22), 使录音的插件进程继承宿主的 while-in-use 能力; API 34 起另带 `BIND_ALLOW_ACTIVITY_STARTS` (路线图 P3.1),
 * 使插件能在宿主可见时打开透明的启动 Activity 并由此启动麦克风前台服务: Android 14+ 拒绝仅被可见宿主绑定的进程启动该服务, 缺少它时宿主一离开
 * 前台会话即被静音. 会话与长操作在整个生命周期内持有专用绑定租约; 短调用使用连接池绑定. 文件由宿主掌握: 输入以只读方式从脚本路径打开并先行检查,
 * 合成输出暂存在目标旁边并由 [SpeechOutputFile] 发布, 因此插件从不接触路径. 宿主自身不保留任何识别或合成实现.
 */
object SpeechPluginHost {

    const val ACTION_SPEECH = SpeechPluginActions.SPEECH

    /** Module label of the selection errors (`error_missing_required_plugin_for_module_label`, roadmap D27). zh-CN: 选择错误使用的模块标签 (路线图 D27). */
    const val LABEL = "3-Macaw Speech"

    /** Binding options of every speech binding (roadmap D22, P3.1); constants, so the JVM tests read them without this object. zh-CN: 每个语音绑定的选项 (路线图 D22, P3.1); 使用常量, JVM 测试读取时无需初始化本对象. */
    internal const val ALLOW_ACTIVITY_STARTS = true
    internal const val INCLUDE_CAPABILITIES = true
    internal const val IMPORTANT = true

    private val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(SpeechPluginIds.ENGINE)

    private val host = AidlPluginHost(
        action = ACTION_SPEECH,
        category = SpeechPluginActions.SERVICE_CATEGORY,
        tag = "SpeechPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> ISpeechPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = defaultEnabled,
        allowActivityStarts = ALLOW_ACTIVITY_STARTS,
        preBindValidator = ::validateService,
        includeCapabilities = INCLUDE_CAPABILITIES,
        important = IMPORTANT,
    )

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        host.discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    /** Whether a compatible, enabled and authorized speech plugin answers right now; never throws. zh-CN: 当前是否有兼容, 已启用且已授权的语音插件应答; 从不抛出. */
    suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            selectOrThrow(context)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** The advertised capabilities merged with the live `getCapabilities` answer (roadmap appendix B.1). zh-CN: 声明的能力与实时 `getCapabilities` 应答的合并 (路线图附录 B.1). */
    suspend fun capabilities(context: Context): SpeechCapabilities = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        val info = target.pluginInfo
        val advertised = info?.capabilities ?: Bundle()
        val live = try {
            call(context, target, SpeechContract.CALL_TIMEOUT_MS, "getCapabilities") { proxy -> proxy.capabilities } ?: Bundle()
        } catch (e: CancellationException) {
            throw e
        } catch (_: SpeechPluginException) {
            Bundle()
        }
        fun string(key: String): String? = (live.getString(key) ?: advertised.getString(key))?.takeIf { it.isNotBlank() }
        val features = (live.getStringArray(SpeechCapabilityKeys.FEATURES) ?: advertised.getStringArray(SpeechCapabilityKeys.FEATURES))
            ?.filterNotNull()?.filter { it in SpeechContract.FEATURES }.orEmpty()
        SpeechCapabilities(
            contractVersion = baseContractVersion(advertised),
            features = features,
            catalogVersion = intOf(live, SpeechCapabilityKeys.CATALOG_VERSION) ?: intOf(advertised, SpeechCapabilityKeys.CATALOG_VERSION) ?: 0,
            engineName = string(SpeechCapabilityKeys.ENGINE_NAME),
            engineVersion = string(SpeechCapabilityKeys.ENGINE_VERSION),
            ttsEnginePackage = string(SpeechCapabilityKeys.TTS_ENGINE_PACKAGE),
            microphoneGranted = if (live.containsKey(SpeechCapabilityKeys.MICROPHONE_GRANTED)) live.getBoolean(SpeechCapabilityKeys.MICROPHONE_GRANTED) else null,
            packageName = target.serviceInfo.packageName,
            pluginVersionName = info?.versionName,
            pluginVersionCode = info?.versionCode ?: 0L,
        )
    }

    // ------------------------------------------------------------------ models

    suspend fun listModels(context: Context, query: SpeechModelQuery): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        val answer = call(context, target, SpeechContract.CALL_TIMEOUT_MS, "listModels") { proxy -> proxy.listModels(SpeechBundles.modelQuery(query)) }
        SpeechBundles.models(SpeechBundles.answer(answer, "listModels")).take(SpeechLimits.MAX_MODELS)
    }

    suspend fun removeModel(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        requireFeature(peerOf(target), SpeechContract.FEATURE_MODELS)
        val answer = call(context, target, SpeechContract.CALL_TIMEOUT_MS, "removeModel") { proxy -> proxy.removeModel(SpeechBundles.modelRequest(id)) }
        SpeechBundles.answer(answer, "removeModel").getBoolean(SpeechResultKeys.REMOVED)
    }

    suspend fun setDefaultModel(context: Context, kind: String, id: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        requireFeature(peerOf(target), SpeechContract.FEATURE_MODELS)
        val answer = call(context, target, SpeechContract.CALL_TIMEOUT_MS, "setDefaultModel") { proxy -> proxy.setDefaultModel(SpeechBundles.modelRequest(id, kind)) }
        SpeechBundles.model(SpeechBundles.answer(answer, "setDefaultModel").getBundle(SpeechResultKeys.MODEL))
    }

    /** Downloads and installs a catalog model; [onProgress] receives bytes done and total on a Binder thread. zh-CN: 下载并安装目录模型; [onProgress] 在 Binder 线程上接收已完成与总字节数. */
    suspend fun installModel(context: Context, request: SpeechInstallRequest, onProgress: ((Long, Long) -> Unit)? = null): Map<String, Any?> = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        requireFeature(peerOf(target), SpeechContract.FEATURE_MODELS)
        val answer = operation(context, target, "installModel", null, onProgress) { proxy, callback -> proxy.installModel(SpeechBundles.install(request), callback) }
        SpeechBundles.model(answer.getBundle(SpeechResultKeys.MODEL))
    }

    /** Imports a model from a local archive the script may read. zh-CN: 从脚本可读的本地归档导入模型. */
    suspend fun importModel(context: Context, archive: File, request: SpeechImportRequest, onProgress: ((Long, Long) -> Unit)? = null): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            if (!archive.isFile || !archive.canRead()) throw SpeechErrorMapper.failure(SpeechErrorCodes.IO_FAILED, "model archive not found or not readable: ${archive.path}")
            val target = selectForCall(context)
            requireFeature(peerOf(target), SpeechContract.FEATURE_MODELS)
            val descriptor = android.os.ParcelFileDescriptor.open(archive, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val answer = descriptor.use { pfd ->
                operation(context, target, "importModel", null, onProgress) { proxy, callback -> proxy.importModel(pfd, SpeechBundles.importModel(request, archive.name), callback) }
            }
            SpeechBundles.model(answer.getBundle(SpeechResultKeys.MODEL))
        }

    suspend fun refreshCatalog(context: Context): Map<String, Any?> = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        requireFeature(peerOf(target), SpeechContract.FEATURE_MODELS)
        val answer = operation(context, target, "refreshCatalog", SpeechContract.CATALOG_TIMEOUT_MS, null) { proxy, callback -> proxy.refreshCatalog(callback) }
        linkedMapOf(
            FIELD_VERSION to answer.getInt(SpeechResultKeys.CATALOG_VERSION),
            SpeechResultKeys.UPDATED to answer.getBoolean(SpeechResultKeys.UPDATED),
        )
    }

    // ------------------------------------------------------------------ recognition and voice activity

    suspend fun recognizeFile(context: Context, file: File, request: SpeechRecognizeRequest): Map<String, Any?> = withContext(Dispatchers.IO) {
        SpeechAudioInput.open(file).use { input -> recognize(context, input, request) }
    }

    suspend fun recognizePcm(context: Context, pcm: ByteArray, sampleRate: Int, request: SpeechRecognizeRequest): Map<String, Any?> = withContext(Dispatchers.IO) {
        SpeechAudioInput.fromPcm(scratchDirectory(context), pcm, sampleRate).use { input -> recognize(context, input, request) }
    }

    suspend fun detectVoiceFile(context: Context, file: File, request: SpeechVadRequest): Map<String, Any?> = withContext(Dispatchers.IO) {
        SpeechAudioInput.open(file).use { input -> detectVoice(context, input, request) }
    }

    suspend fun detectVoicePcm(context: Context, pcm: ByteArray, sampleRate: Int, request: SpeechVadRequest): Map<String, Any?> = withContext(Dispatchers.IO) {
        SpeechAudioInput.fromPcm(scratchDirectory(context), pcm, sampleRate).use { input -> detectVoice(context, input, request) }
    }

    private suspend fun recognize(context: Context, input: SpeechAudioInput, request: SpeechRecognizeRequest): Map<String, Any?> {
        val target = selectForCall(context)
        requireFeature(peerOf(target), SpeechContract.FEATURE_ASR)
        val answer = call(context, target, SpeechContract.FILE_CALL_TIMEOUT_MS, "recognizeFile") { proxy ->
            proxy.recognizeFile(input.descriptor, SpeechBundles.recognize(request, input.file.name))
        }
        return SpeechBundles.plain(SpeechBundles.answer(answer, "recognizeFile"))
    }

    private suspend fun detectVoice(context: Context, input: SpeechAudioInput, request: SpeechVadRequest): Map<String, Any?> {
        val target = selectForCall(context)
        requireFeature(peerOf(target), SpeechContract.FEATURE_VAD)
        val answer = call(context, target, SpeechContract.FILE_CALL_TIMEOUT_MS, "detectVoice") { proxy ->
            proxy.detectVoice(input.descriptor, SpeechBundles.detectVoice(request, input.file.name))
        }
        return SpeechBundles.plain(SpeechBundles.answer(answer, "detectVoice"))
    }

    // ------------------------------------------------------------------ sessions

    /**
     * Opens a dictation session whose events reach [sink] through a [SpeechEventGate]; the returned proxy owns the
     * dedicated binding (with the host's capabilities) until the session ends.
     * zh-CN: 打开听写会话, 其事件经 [SpeechEventGate] 到达 [sink]; 返回的代理在会话结束前持有专用绑定 (带宿主能力).
     */
    suspend fun openSession(context: Context, request: SpeechListenRequest, sink: SpeechEventSink): SpeechSessionProxy = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        requireFeature(peerOf(target), if (request.isExternal) SpeechContract.FEATURE_ASR else SpeechContract.FEATURE_ASR_STREAMING)
        val gate = SpeechEventGate(sink)
        val callback = SpeechSessionCallbackBinder(gate)
        val leased = lease(context, target) { proxy ->
            proxy.openSession(SpeechBundles.listen(request), callback) ?: throw SpeechErrorMapper.failure(SpeechErrorCodes.INTERNAL, "the plugin opened no session")
        }
        SpeechSessionProxy(leased, gate, request.isExternal)
    }

    /**
     * Opens a wake session: hits reach [wakeSink], the dictation rounds of `thenListen` reach [listenSink] (required with
     * `thenListen`).
     * zh-CN: 打开唤醒会话: 命中到达 [wakeSink], `thenListen` 的听写轮次到达 [listenSink] (有 `thenListen` 时必需).
     */
    suspend fun openWakeSession(
        context: Context,
        request: SpeechWakeRequest,
        wakeSink: SpeechEventSink,
        listenSink: SpeechEventSink?,
    ): SpeechWakeSessionProxy = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        val peer = peerOf(target)
        requireFeature(peer, SpeechContract.FEATURE_KWS)
        if (request.thenListen != null) requireFeature(peer, SpeechContract.FEATURE_ASR_STREAMING)
        val gate = SpeechEventGate(wakeSink)
        val listenGate = if (request.thenListen != null) SpeechEventGate(requireNotNull(listenSink) { "thenListen needs a listen sink" }) else null
        val wakeCallback = SpeechWakeCallbackBinder(gate)
        val listenCallback = listenGate?.let { SpeechSessionCallbackBinder(it, roundChannel = true) }
        val leased = lease(context, target) { proxy ->
            proxy.openWakeSession(SpeechBundles.wake(request), wakeCallback, listenCallback)
                ?: throw SpeechErrorMapper.failure(SpeechErrorCodes.INTERNAL, "the plugin opened no wake session")
        }
        SpeechWakeSessionProxy(leased, gate, listenGate)
    }

    // ------------------------------------------------------------------ synthesis

    /**
     * Synthesizes [request] into [target] (staged, checked and published by the host); answers
     * `{ path, sampleRate, durationMillis, modelId, speakerId, sizeBytes, warnings }` (roadmap appendix B.1). The host
     * waits [SpeechContract.SYNTHESIS_TIMEOUT_MS] for each progress (one per sentence) and for the completion, with no
     * limit on the whole synthesis: the longest text took 405 seconds with AISHELL-3 on a Sony G8441, and slower models
     * run several times longer there.
     * zh-CN: 把 [request] 合成到 [target] (由宿主暂存, 检查并发布); 应答 `{ path, sampleRate, durationMillis, modelId, speakerId, sizeBytes,
     * warnings }` (路线图附录 B.1). 宿主为每次进度 (每句一次) 与完成各等待 [SpeechContract.SYNTHESIS_TIMEOUT_MS], 整个合成不设上限: 最长文本在
     * Sony G8441 上用 AISHELL-3 需 405 秒, 更慢的模型在该机上要长数倍.
     */
    suspend fun synthesizeToFile(
        context: Context,
        request: SpeechSynthesisRequest,
        target: File,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val selected = selectForCall(context)
        requireFeature(peerOf(selected), SpeechContract.FEATURE_TTS)
        val output = SpeechOutputFile.stage(target)
        try {
            val answer = output.openStaging().use { staging ->
                operation(context, selected, "synthesizeToFile", SpeechContract.SYNTHESIS_TIMEOUT_MS, onProgress, renewOnProgress = true) { proxy, callback ->
                    proxy.synthesizeToFile(SpeechBundles.synthesis(request), staging, callback)
                }
            }
            val size = output.publish()
            SpeechBundles.synthesisResult(output.target.path, SpeechBundles.plain(answer), size)
        } catch (t: Throwable) {
            output.discard()
            throw t
        }
    }

    /**
     * Whether [packageName] is installed with a system TTS engine service (`speech.speak`, roadmap D20); a package
     * query, no binding, so it may run on the script or UI thread.
     * zh-CN: [packageName] 是否已安装且带系统 TTS 引擎服务 (`speech.speak`, 路线图 D20); 只查询包管理器, 不绑定, 因此可在脚本线程或 UI 线程上运行.
     */
    fun isTtsEngineInstalled(context: Context, packageName: String): Boolean =
        queryServices(context, Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(packageName)).isNotEmpty()

    /**
     * `PLUGIN_UNAVAILABLE` with the localized missing-plugin message, as every speech call reports it without the
     * plugin (roadmap D27).
     * zh-CN: 带本地化缺插件文案的 `PLUGIN_UNAVAILABLE`, 与缺少插件时每个语音调用的报告一致 (路线图 D27).
     */
    fun missingPlugin(context: Context): SpeechPluginException = SpeechErrorMapper.failure(
        SpeechErrorCodes.PLUGIN_UNAVAILABLE,
        context.getString(R.string.error_missing_required_plugin_for_module_label, LABEL),
    )

    // ------------------------------------------------------------------ selection

    private suspend fun selectForCall(context: Context): AidlPluginHost.Discovered = try {
        selectOrThrow(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SpeechPluginException) {
        throw e
    } catch (t: Throwable) {
        throw SpeechPluginException(discoveryError(context, t), t)
    }

    /**
     * Why no plugin was selected (roadmap D27): none installed (`PLUGIN_UNAVAILABLE` with the host's localized missing
     * plugin message), none enabled and authorized (`PLUGIN_DISABLED`), or none that speaks this contract or accepts this
     * host build (`PLUGIN_INCOMPATIBLE`).
     * zh-CN: 未选中插件的原因 (路线图 D27): 未安装 (`PLUGIN_UNAVAILABLE`, 带宿主本地化的缺插件文案), 没有已启用且已授权的 (`PLUGIN_DISABLED`),
     * 或没有支持本契约或接受本宿主构建的 (`PLUGIN_INCOMPATIBLE`).
     */
    private suspend fun discoveryError(context: Context, cause: Throwable): SpeechError {
        val mapped = SpeechErrorMapper.fromThrowable(cause, SpeechErrorMapper.Phase.DISCOVERY)
        if (mapped.code != SpeechErrorCodes.PLUGIN_UNAVAILABLE) return mapped
        val discovered = try {
            host.discover(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val message = cause.message?.takeIf { it.isNotBlank() } ?: mapped.message
        if (discovered.isEmpty()) return SpeechErrorMapper.normalize(SpeechErrorCodes.PLUGIN_UNAVAILABLE, message)
        val packages = discovered.map { it.serviceInfo.packageName }.distinct()
        val usable = discovered.filter { item ->
            val packageName = item.serviceInfo.packageName
            PluginTrustManager.isAuthorized(context, packageName) && PluginEnableStore.isEnabled(context, packageName, defaultEnabled)
        }
        if (usable.isEmpty()) {
            return SpeechErrorMapper.normalize(SpeechErrorCodes.PLUGIN_DISABLED, "$message (${packages.joinToString()})")
        }
        val unreadable = usable.firstOrNull { it.pluginInfo == null }
        if (unreadable != null && usable.all { it.pluginInfo == null }) {
            val detail = unreadable.error?.message?.takeIf { it.isNotBlank() } ?: "no plugin information"
            return SpeechErrorMapper.normalize(
                SpeechErrorCodes.PLUGIN_INCOMPATIBLE,
                "${unreadable.serviceInfo.packageName} does not answer speech contract version ${SpeechContract.CONTRACT_VERSION}: $detail",
            )
        }
        return SpeechErrorMapper.normalize(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, message)
    }

    internal suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered = host.selectOrThrow(
        context = context,
        engineId = null,
        engine = SpeechPluginIds.ENGINE,
        variant = SpeechPluginIds.VARIANT_DEFAULT,
        label = LABEL,
    )

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.engine == SpeechPluginIds.ENGINE) { "Unexpected speech plugin engine: ${it.engine}" }
        require(it.variant == SpeechPluginIds.VARIANT_DEFAULT) { "Unexpected speech plugin variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "speech plugin capabilities are missing" }
        val contractVersion = baseContractVersion(capabilities)
        require(SpeechContract.supportsContractVersion(contractVersion)) { "Unsupported speech contract version: $contractVersion" }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= SpeechPluginIds.REQUIRED_HOST_VERSION_CODE) {
            "speech plugin required host version $requiredHostVersion is below ${SpeechPluginIds.REQUIRED_HOST_VERSION_CODE}"
        }
    }

    /** The contract version and features of the selected plugin. zh-CN: 选中插件的契约版本与功能. */
    internal fun peerOf(target: AidlPluginHost.Discovered): SpeechPeer {
        val capabilities = target.pluginInfo?.capabilities ?: return SpeechPeer.LEGACY
        val version = baseContractVersion(capabilities).takeIf(SpeechContract::supportsContractVersion) ?: return SpeechPeer.LEGACY
        val features = capabilities.getStringArray(SpeechCapabilityKeys.FEATURES)?.filterNotNull()?.toSet().orEmpty()
        return SpeechPeer(version, features)
    }

    private fun requireFeature(peer: SpeechPeer, feature: String) {
        if (!peer.supports(feature)) {
            throw SpeechErrorMapper.failure(
                SpeechErrorCodes.PLUGIN_INCOMPATIBLE,
                "the installed speech plugin does not offer \"$feature\" yet (it offers: ${peer.features.sorted().joinToString().ifEmpty { "nothing" }})",
            )
        }
    }

    private fun baseContractVersion(capabilities: Bundle): Int = intOf(capabilities, SpeechCapabilityKeys.CONTRACT_VERSION) ?: 0

    private fun intOf(bundle: Bundle, key: String): Int? = PluginCapabilityResolver.coerceLong(
        @Suppress("DEPRECATION") bundle.get(key),
    )?.toInt()

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.exported && serviceInfo.enabled) { "speech plugin service is not exported and enabled" }
        require(serviceInfo.permission == SpeechPluginActions.PLUGIN_PERMISSION) {
            "speech plugin service must require ${SpeechPluginActions.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName)) {
            "speech plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String): Set<ComponentName> {
        val intent = Intent(SpeechPluginActions.SPEECH).apply {
            addCategory(SpeechPluginActions.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(packageName)
        }
        return queryServices(context, intent)
    }

    private fun queryServices(context: Context, intent: Intent): Set<ComponentName> {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { item -> item.serviceInfo?.let { ComponentName(it.packageName, it.name) } }.toSet()
    }

    private fun scratchDirectory(context: Context): File = File(context.applicationContext.cacheDir, "speech")

    // ------------------------------------------------------------------ Binder plumbing

    /** A short call on the pooled binding, bounded by [timeoutMs]; failures leave as [SpeechPluginException]. zh-CN: 连接池绑定上的短调用, 受 [timeoutMs] 限制; 失败以 [SpeechPluginException] 离开. */
    private suspend fun <T> call(context: Context, target: AidlPluginHost.Discovered, timeoutMs: Long, label: String, block: (ISpeechPlugin) -> T): T = try {
        withTimeout(timeoutMs) { host.call(context.applicationContext, target, retryOnRemoteException = false, block = block) }
    } catch (e: TimeoutCancellationException) {
        throw SpeechErrorMapper.failure(SpeechErrorCodes.TIMEOUT, "$label did not answer within $timeoutMs ms", e)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw SpeechErrorMapper.wrap(t, SpeechErrorMapper.Phase.CALL)
    }

    /** A call whose result keeps a dedicated binding lease (sessions, operations). zh-CN: 其结果持有专用绑定租约的调用 (会话, 操作). */
    private suspend fun <T> lease(context: Context, target: AidlPluginHost.Discovered, block: (ISpeechPlugin) -> T): AidlPluginHost.LeasedCall<T> = try {
        withTimeout(SpeechContract.CALL_TIMEOUT_MS) {
            host.callWithDedicatedBindingLease(context.applicationContext, target, retryOnRemoteException = false, block = block)
        }
    } catch (e: TimeoutCancellationException) {
        throw SpeechErrorMapper.failure(SpeechErrorCodes.TIMEOUT, "the speech plugin did not open the request within ${SpeechContract.CALL_TIMEOUT_MS} ms", e)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw SpeechErrorMapper.wrap(t, SpeechErrorMapper.Phase.OPEN)
    }

    private class Started(val proxy: ISpeechPlugin, val opId: Int)

    /**
     * Runs one operation of the progress callback family on a dedicated binding: the plugin answers an operation id at
     * once and completes later; cancellation of the caller (script exit, timeout) cancels the operation in the plugin,
     * and the death of the plugin process fails it with `SESSION_CLOSED`. [timeoutMs] bounds the whole operation, or
     * with [renewOnProgress] the wait for each progress and for the completion.
     * zh-CN: 在专用绑定上运行一个进度回调类操作: 插件立即应答操作 id, 之后完成; 调用方取消 (脚本退出, 超时) 会取消插件中的操作, 插件进程死亡
     * 以 `SESSION_CLOSED` 使其失败. [timeoutMs] 限制整个操作, 带 [renewOnProgress] 时限制每次进度与完成的等待.
     */
    private suspend fun operation(
        context: Context,
        target: AidlPluginHost.Discovered,
        label: String,
        timeoutMs: Long?,
        onProgress: ((Long, Long) -> Unit)?,
        renewOnProgress: Boolean = false,
        start: (ISpeechPlugin, ISpeechProgressCallback) -> Int,
    ): Bundle {
        val callback = SpeechProgressBinder(onProgress)
        val leased = lease(context, target) { proxy -> Started(proxy, start(proxy, callback)) }
        val binder = leased.value.proxy.asBinder()
        val death = IBinder.DeathRecipient { callback.fail(SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "speech plugin process died")) }
        try {
            binder.linkToDeath(death, 0)
        } catch (_: Exception) {
            callback.fail(SpeechErrorMapper.normalize(SpeechErrorCodes.SESSION_CLOSED, "speech plugin process died"))
        }
        try {
            val result = when {
                timeoutMs == null -> callback.await()
                renewOnProgress -> callback.awaitRenewing(timeoutMs)
                else -> withTimeout(timeoutMs) { callback.await() }
            }
            return SpeechBundles.answer(result, label)
        } catch (e: CancellationException) {
            runCatching { leased.value.proxy.cancelOperation(leased.value.opId) }
            if (e is TimeoutCancellationException) {
                val detail = if (renewOnProgress) "reported no progress within $timeoutMs ms" else "did not finish within $timeoutMs ms"
                throw SpeechErrorMapper.failure(SpeechErrorCodes.TIMEOUT, "$label $detail", e)
            }
            throw e
        } finally {
            runCatching { binder.unlinkToDeath(death, 0) }
            runCatching { leased.bindingLease.close() }
        }
    }

    const val FIELD_PATH = "path"
    const val FIELD_VERSION = "version"
}

/**
 * Completion channel of one operation: progress reaches [onProgress] on a Binder thread until the operation completes or
 * fails; the outcome settles [await] and [awaitRenewing].
 * zh-CN: 一个操作的完成通道: 操作完成或失败前, 进度在 Binder 线程上到达 [onProgress]; 结果使 [await] 与 [awaitRenewing] 落定.
 */
internal class SpeechProgressBinder(private val onProgress: ((Long, Long) -> Unit)?) : ISpeechProgressCallback.Stub() {

    private val outcome = CompletableDeferred<Bundle>()
    private val deadline = SpeechProgressDeadline()

    override fun onProgress(opId: Int, done: Long, total: Long) {
        deadline.renew()
        if (!outcome.isCompleted) runCatching { onProgress?.invoke(done, total) }
    }

    override fun onCompleted(opId: Int, result: Bundle?) {
        outcome.complete(result ?: Bundle())
    }

    override fun onFailed(opId: Int, error: Bundle?) {
        fail(SpeechErrorMapper.fromBundle(error) ?: SpeechErrorMapper.normalize(SpeechErrorCodes.INTERNAL, "the operation failed"))
    }

    fun fail(error: SpeechError) {
        outcome.completeExceptionally(SpeechPluginException(error))
    }

    suspend fun await(): Bundle = outcome.await()

    /** [await] that fails with `TimeoutCancellationException` once [windowMs] passed without progress. zh-CN: 连续 [windowMs] 无进度即以 `TimeoutCancellationException` 失败的 [await]. */
    suspend fun awaitRenewing(windowMs: Long): Bundle = deadline.await(windowMs, outcome)
}

/**
 * The renewable deadline of an operation that reports progress (roadmap P4.3): the wait for the outcome fails with
 * `TimeoutCancellationException` once a whole window passed since the start or the last [renew], however long the
 * operation runs in total. [clockMillis] is a monotonic clock (replaced in the JVM tests).
 * zh-CN: 报告进度的操作的可续期截止时间 (路线图 P4.3): 自开始或最近一次 [renew] 起整整一个窗口没有动静时, 对结果的等待以
 * `TimeoutCancellationException` 失败, 不论操作总共运行多久. [clockMillis] 为单调时钟 (JVM 测试中替换).
 */
internal class SpeechProgressDeadline(private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {

    @Volatile
    private var lastActivity = clockMillis()

    fun renew() {
        lastActivity = clockMillis()
    }

    suspend fun <T> await(windowMs: Long, outcome: Deferred<T>): T {
        while (true) {
            val remaining = windowMs - (clockMillis() - lastActivity)
            try {
                return withTimeout(remaining.coerceAtLeast(1)) { outcome.await() }
            } catch (e: TimeoutCancellationException) {
                if (clockMillis() - lastActivity >= windowMs) throw e
            }
        }
    }
}
