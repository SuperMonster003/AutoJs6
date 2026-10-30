package org.autojs.autojs.core.plugin.ocr

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SharedMemory
import android.os.SystemClock.uptimeMillis
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.PluginBindRefusedException
import org.autojs.autojs.core.plugin.isPluginBindRefused
import org.autojs.autojs.core.plugin.center.InstalledPluginMetadataStore
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginServicePreflight
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.paddle.ocr.api.IOcrPlugin
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.autojs.plugin.paddle.ocr.api.PaddleOcrPluginCapabilityKeys
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Host for OCR AIDL plugins.
 *
 * The action string and AIDL API package still use their original Paddle OCR names for compatibility
 * with installed Paddle/Rapid OCR plugins. Engine selection is done through [PluginInfo.engine].
 *
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 26, 2026.
 */
object OcrPluginHost {

    private const val TAG = "OcrPluginHost"

    // Legacy action shared by OCR plugins. Keep the string stable for installed Paddle/Rapid plugins.
    const val ACTION_OCR = "org.autojs.plugin.PADDLE_OCR"

    val AUTOMATIC_ENGINE_ORDER: List<String> = listOf("mlkit-ocr", "paddle-ocr", "rapid-ocr")

    /** Reads current installation and enablement without starting or binding plugin processes. */
    fun availableEngines(context: Context): List<String> {
        val metadata = InstalledPluginMetadataStore.readAll(context)
        val engines = queryOcrServiceCandidates(context).flatMap { candidate ->
            val service = candidate.serviceInfo ?: return@flatMap emptyList()
            val packageName = service.packageName
            if (!service.enabled || !service.applicationInfo.enabled ||
                !PluginEnableStore.isEnabled(context, packageName, true) ||
                !PluginTrustManager.isAuthorized(context, packageName)
            ) return@flatMap emptyList()

            val cached = metadata[packageName]?.let { entry ->
                val installed = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
                entry.takeIf {
                    installed != null && it.matchesInstalledPackage(PackageInfoCompat.getLongVersionCode(installed), installed.lastUpdateTime)
                }
            }
            if (!PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), cached?.requiresHostVersion)) {
                return@flatMap emptyList()
            }
            // Live metadata from Plugin Center also supports older plugins without engine categories.
            cached?.engine?.let { return@flatMap listOf(it) }
            AUTOMATIC_ENGINE_ORDER.filter { candidate.filter?.hasCategory(it) == true }
        }.toSet()
        return AUTOMATIC_ENGINE_ORDER.filter { it in engines }
    }

    private const val DEFAULT_BIND_TIMEOUT_MS = 60_000L
    private const val DEFAULT_CALL_TIMEOUT_MS = 60_000L
    private const val IDLE_UNBIND_MS = 30_000L
    private const val RETRY_WAKE_DELAY_MS = 300L
    private const val MODULE_LABEL_OCR_PLUGIN = "OCR plugin"
    private val SERVICE_QUERY_RETRY_DELAYS_MS = longArrayOf(150L, 300L, 600L, 1000L, 1500L)

    private val externalServiceFlag = runCatching { ServiceInfo::class.java.getField("FLAG_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val bindExternalServiceFlag = runCatching { Context::class.java.getField("BIND_EXTERNAL_SERVICE").getInt(null) }.getOrNull() ?: 0
    private val poolHandler = Handler(Looper.getMainLooper())
    private val poolLock = Any()
    private val connectionPool = LinkedHashMap<ComponentName, PooledConnection>()

    data class Discovered(
        val serviceInfo: ServiceInfo,
        val pluginInfo: PluginInfo?,
        val error: Throwable?,
    )

    suspend fun discover(context: Context, packageName: String? = null): List<Discovered> {
        return discoverServices(context, queryOcrServices(context, packageName))
    }

    private suspend fun discoverServices(context: Context, services: List<ServiceInfo>): List<Discovered> {
        Log.i(TAG, "discover: services=${services.size}")
        return services.map { svc ->
            Log.i(TAG, "discover: ${svc.packageName}/${svc.name}")
            val result = runCatching {
                PluginServicePreflight.verify(svc)
                withService(context, svc, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() }
            }
            val info = result.getOrNull()
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.w(TAG, "getInfo failed: ${error.message}")
            }
            Discovered(svc, info, error)
        }
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo {
        if (!PluginTrustManager.isAuthorized(context, packageName)) {
            error("Plugin not authorized: $packageName")
        }
        val serviceInfo = queryFirstOcrServiceWithRetry(context, packageName)
            ?: error("No OCR service found for package: $packageName")
        PluginServicePreflight.verify(serviceInfo)
        return withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS) { it.getInfo() }
    }

    private suspend fun queryFirstOcrServiceWithRetry(context: Context, packageName: String): ServiceInfo? {
        queryOcrServices(context, packageName).firstOrNull()?.let { return it }
        SERVICE_QUERY_RETRY_DELAYS_MS.forEach { delayMs ->
            delay(delayMs)
            queryOcrServices(context, packageName).firstOrNull()?.let { return it }
        }
        return null
    }

    suspend fun recognizeText(
        context: Context,
        target: Discovered,
        bitmap: Bitmap,
        options: OcrOptions = OcrOptions(),
        callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): List<String> {
        if (!PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        ensureHostVersionCompatible(context, target)
        ensureRawSupport(target, options)
        val start = uptimeMillis()
        return withImagePfdService(context, target.serviceInfo, bitmap, options, callTimeoutMs, start) { proxy, pfd ->
            val lines = proxy.recognizeText(pfd, options)
            Log.i(TAG, "recognizeTextAidl: got ${lines.size} lines")
            lines
        }
    }

    suspend fun detect(
        context: Context,
        target: Discovered,
        bitmap: Bitmap,
        options: OcrOptions = OcrOptions(),
        callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): List<OcrResult> {
        if (!PluginTrustManager.isAuthorized(context, target.serviceInfo.packageName)) {
            error("Plugin not authorized: ${target.serviceInfo.packageName}")
        }
        ensureHostVersionCompatible(context, target)
        ensureRawSupport(target, options)
        val start = uptimeMillis()
        return withImagePfdService(context, target.serviceInfo, bitmap, options, callTimeoutMs, start) { proxy, pfd ->
            val results = proxy.detect(pfd, options)
            Log.i(TAG, "detectAidl: got ${results.size} items")
            results
        }
    }

    suspend fun select(
        context: Context,
        // e.g. "paddle-ocr-pp-ocrv5" or "rapid-ocr"
        engineId: String? = null,
        // e.g. "paddle-ocr" or "rapid-ocr"
        engine: String? = null,
        // e.g. "v5"
        variant: String? = null,
        // e.g. "ja", compared with the plugin's modelProfile capability
        profile: String? = null,
    ): Discovered? {
        return selectDetailed(context, engineId, engine, variant, profile).selected
    }

    suspend fun selectOrThrow(
        context: Context,
        // e.g. "paddle-ocr-pp-ocrv5" or "rapid-ocr"
        engineId: String? = null,
        // e.g. "paddle-ocr" or "rapid-ocr"
        engine: String? = null,
        // e.g. "v5"
        variant: String? = null,
        // e.g. "ja", compared with the plugin's modelProfile capability
        profile: String? = null,
        moduleLabel: String = MODULE_LABEL_OCR_PLUGIN,
        automatic: Boolean = false,
    ): Discovered {
        val result = selectDetailed(context, engineId, engine, variant, profile, automatic)
        return result.selected ?: throw buildSelectionFailure(context, result.discovered, engineId, engine, variant, profile, moduleLabel)
    }

    private suspend fun selectDetailed(
        context: Context,
        engineId: String?,
        engine: String?,
        variant: String?,
        profile: String?,
        automatic: Boolean = false,
    ): SelectionResult {
        suspend fun discoverCandidates(): List<Discovered> {
            if (!automatic) return discover(context)
            val services = queryOcrServices(context).filter { service ->
                PluginEnableStore.isEnabled(context, service.packageName, true) &&
                    PluginTrustManager.isAuthorized(context, service.packageName)
            }
            return discoverServices(context, services)
        }
        var discovered = discoverCandidates()
        var selected = selectFrom(discovered, context, engineId, engine, variant, profile, automatic)
        if (selected == null && discovered.any { it.error != null }) {
            recoverFailedDiscoveries(context, discovered)
            discovered = discoverCandidates()
            selected = selectFrom(discovered, context, engineId, engine, variant, profile, automatic)
        }
        // The chosen package shows which plugin served a script call (paddle-ocr-v3 P2.2).
        // zh-CN: 选中的包名表明脚本调用由哪个插件处理 (paddle-ocr-v3 P2.2).
        selected?.let { Log.i(TAG, "select: ${it.serviceInfo.packageName} (variant ${it.pluginInfo?.variant}) for ${describeCriteria(engineId, engine, variant, profile)}") }
        return SelectionResult(selected, discovered)
    }

    private fun describeCriteria(engineId: String?, engine: String?, variant: String?, profile: String?): String {
        return listOfNotNull(
            engineId?.let { "engineId=$it" },
            engine?.let { "engine=$it" },
            variant?.let { "variant=$it" },
            profile?.let { "profile=$it" },
        ).joinToString(" ").ifEmpty { "no criteria" }
    }

    private fun selectFrom(
        discovered: List<Discovered>,
        context: Context,
        engineId: String?,
        engine: String?,
        variant: String?,
        profile: String?,
        automatic: Boolean = false,
    ): Discovered? {
        if (automatic) {
            return AUTOMATIC_ENGINE_ORDER.firstNotNullOfOrNull { candidateEngine ->
                // Keep Rapid's public variant aliases when selecting before engine dispatch.
                val candidateVariant = if (candidateEngine == "rapid-ocr") when (variant) {
                    "v3" -> "pp-ocrv3"
                    "v6" -> "pp-ocrv6"
                    else -> variant
                } else variant
                if (engine != null && engine != candidateEngine) null
                else selectFrom(discovered, context, engineId, candidateEngine, candidateVariant, profile)
            }
        }
        val list = discovered
            .filter { it.pluginInfo != null }
            .filter { PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
            .filter { isHostVersionCompatible(it.pluginInfo) }
        if (list.isEmpty()) return null
        if (hasExplicitSelectionCriteria(engineId, engine, variant, profile)) {
            val candidates = list.filter { d ->
                d.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant, profile) } == true
            }
            if (candidates.isEmpty()) return null
            return selectByPriorityWithKeys(
                candidates = candidates,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = engine ?: candidates.mapNotNull { it.pluginInfo?.engine }.distinct().singleOrNull(),
                    engineId = engineId ?: inferCommonEngineFamily(candidates),
                ),
            ) ?: selectDefault(candidates)
        }
        val singleEngine = list.mapNotNull { it.pluginInfo?.engine }.distinct().singleOrNull()
        if (!singleEngine.isNullOrBlank()) {
            selectByPriorityWithKeys(
                candidates = list,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = singleEngine,
                    engineId = inferCommonEngineFamily(list),
                ),
            )?.let { return it }
        }
        inferCommonEngineFamily(list)?.let { family ->
            selectByPriorityWithKeys(
                candidates = list,
                keys = PluginVariantPriorityStore.buildPriorityKeys(
                    engine = family,
                    engineId = family,
                ),
            )?.let { return it }
        }
        return selectDefault(list)
    }

    private fun selectDefault(candidates: List<Discovered>): Discovered? {
        return OcrPluginSelection.selectDefault(
            candidates = candidates,
            pluginInfoOf = { it.pluginInfo },
            packageNameOf = { it.serviceInfo.packageName },
        )
    }

    private fun selectByPriorityWithKeys(
        candidates: List<Discovered>,
        keys: List<String>,
    ): Discovered? {
        if (candidates.isEmpty()) return null
        val byPkg = candidates.associateBy { it.serviceInfo.packageName }
        val fallbackOrder = PluginVariantPriorityStore.getBestOrderForPackages(
            byPkg.keys,
        )
        if (fallbackOrder.isNotEmpty()) {
            fallbackOrder.firstNotNullOfOrNull { byPkg[it] }?.let { return it }
        }

        keys.forEach { key ->
            PluginVariantPriorityStore.selectByPriority(
                engine = key,
                candidates = candidates,
                packageNameOf = { it.serviceInfo.packageName },
                variantOf = { it.pluginInfo?.variant },
            )?.let { selected ->
                val order = PluginVariantPriorityStore.getOrder(key)
                if (order.isNotEmpty()) {
                    return selected
                }
            }
        }
        return PluginVariantPriorityStore.selectByPriority(
            engine = keys.firstOrNull(),
            candidates = candidates,
            packageNameOf = { it.serviceInfo.packageName },
            variantOf = { it.pluginInfo?.variant },
            isRecommendedDefault = { OcrPluginSelection.isRecommendedDefault(it.pluginInfo) },
        )
    }

    private fun inferCommonEngineFamily(candidates: List<Discovered>): String? {
        if (candidates.isEmpty()) return null
        val families = candidates.mapNotNull { candidate ->
            PluginVariantPriorityStore.normalizeFamilyKey(candidate.pluginInfo?.id)
                ?: PluginVariantPriorityStore.normalizeFamilyKey(candidate.pluginInfo?.engine)
        }.distinct()
        return families.singleOrNull()
    }

    private fun buildSelectionFailure(
        context: Context,
        discovered: List<Discovered>,
        engineId: String?,
        engine: String?,
        variant: String?,
        profile: String?,
        moduleLabel: String,
    ): IllegalStateException {
        val selectionLabel = describeSelection(engineId, engine, variant, profile, moduleLabel)
        val installedProfiles = profile?.let {
            OcrPluginSelection.installedProfiles(discovered.mapNotNull { item -> item.pluginInfo }, engineId, engine, variant)
        }
        if (discovered.isEmpty()) {
            return buildUnmatchedSelectionFailure(context, discovered, selectionLabel, engine, installedProfiles)
        }
        val scopedInfoItems = discovered.filter { item ->
            item.pluginInfo?.let { matchesSelectionCriteria(it, engineId, engine, variant, profile) } == true
        }
        if (scopedInfoItems.isEmpty() && hasExplicitSelectionCriteria(engineId, engine, variant, profile)) {
            return buildUnmatchedSelectionFailure(context, discovered, selectionLabel, engine, installedProfiles)
        }
        val variantList = scopedInfoItems
            .mapNotNull { item ->
                item.pluginInfo?.let { info ->
                    info.variant?.takeIf { it.isNotBlank() }
                        ?: info.id?.takeIf { it.isNotBlank() }
                        ?: info.engine?.takeIf { it.isNotBlank() }
                }
            }
            .distinct()
            .joinToString("/")
            .ifBlank { "*" }
        val fallback = context.getString(
            R.string.error_no_available_enabled_plugin_variants_found,
            selectionLabel,
            variantList,
        )

        val infoItems = scopedInfoItems
        if (infoItems.isEmpty()) {
            val failure = discovered.firstNotNullOfOrNull { item ->
                item.error?.let { item to it }
            }
            if (failure != null) {
                val (item, error) = failure
                val message = "${fallback}: ${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${error.message ?: error}"
                return IllegalStateException(message, error)
            }
            return IllegalStateException(fallback)
        }

        val authorizedItems = infoItems.filter { PluginTrustManager.isAuthorized(context, it.serviceInfo.packageName) }
        if (authorizedItems.isEmpty()) {
            return IllegalStateException(
                context.getString(R.string.error_plugin_is_not_authorized_in_plugin_center, infoItems.first().serviceInfo.packageName)
            )
        }

        val enabledItems = authorizedItems.filter {
            PluginEnableStore.isEnabled(context, it.serviceInfo.packageName, true)
        }
        if (enabledItems.isEmpty()) {
            return IllegalStateException(
                context.getString(R.string.error_plugin_is_not_enabled_in_plugin_center, authorizedItems.first().serviceInfo.packageName)
            )
        }

        val compatibleItems = enabledItems.filter { isHostVersionCompatible(it.pluginInfo) }
        if (compatibleItems.isEmpty()) {
            val requiredVersion = enabledItems
                .mapNotNull { PluginCapabilityResolver.resolveRequiresHostVersion(it.pluginInfo?.capabilities) }
                .maxOrNull()
                ?: 0L
            return IllegalStateException(
                context.getString(
                    R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                    requiredVersion,
                    BuildConfig.VERSION_CODE.toLong(),
                )
            )
        }

        return IllegalStateException(fallback)
    }

    private fun buildUnmatchedSelectionFailure(
        context: Context,
        discovered: List<Discovered>,
        selectionLabel: String,
        engine: String?,
        // Listed when a profile was requested (D23).
        installedProfiles: List<String>?,
    ): IllegalStateException {
        // Failed discovery cannot establish that the requested plugin is missing.
        val failures = discovered.filter { it.error != null }
        val message = if (failures.isEmpty()) {
            context.getString(R.string.error_no_matching_ocr_plugin_for_selection, selectionLabel)
        } else {
            val details = failures.joinToString("; ") { item ->
                "${item.serviceInfo.packageName}/${item.serviceInfo.name}: ${item.error?.message ?: item.error}"
            }
            context.getString(R.string.error_failed_to_query_ocr_plugins_for_selection, selectionLabel, details)
        }
        val parts = mutableListOf(message)
        if (installedProfiles != null) {
            val profiles = installedProfiles.joinToString(", ").ifEmpty { context.getString(R.string.text_none) }
            parts += context.getString(R.string.text_installed_ocr_plugin_profiles, profiles)
        }
        val detectedPlugins = discovered.mapNotNull { item ->
            item.pluginInfo?.let { info ->
                val name = info.name.ifBlank { item.serviceInfo.packageName }
                val selection = describeSelection(info.id, info.engine, info.variant, OcrPluginSelection.declaredModelProfile(info), name)
                "$name [$selection; package=${item.serviceInfo.packageName}]"
            }
        }.distinct()
        if (detectedPlugins.isNotEmpty()) {
            parts += context.getString(R.string.text_detected_ocr_plugins, detectedPlugins.joinToString("; "))
        }
        if (engine == null || engine == "mlkit-ocr") {
            parts += context.getString(R.string.hint_ocr_default_engine_and_switching)
        }
        return IllegalStateException(parts.joinToString("\n"), failures.firstOrNull()?.error)
    }

    private fun describeSelection(engineId: String?, engine: String?, variant: String?, profile: String?, moduleLabel: String): String {
        // The engine option can override the caller's module, so its label must follow the request.
        val label = when (engine) {
            "mlkit-ocr" -> "ML Kit OCR"
            "paddle-ocr" -> "Paddle OCR"
            "rapid-ocr" -> "Rapid OCR"
            null -> engineId ?: moduleLabel
            else -> engine
        }
        val criteria = listOfNotNull(
            engine?.let { "engine=$it" },
            engineId?.let { "engineId=$it" },
            variant?.let { "variant=$it" },
            profile?.let { "profile=$it" },
        )
        return if (criteria.isEmpty()) label else "$label (${criteria.joinToString(", ")})"
    }

    private fun matchesSelectionCriteria(
        pluginInfo: PluginInfo,
        engineId: String?,
        engine: String?,
        variant: String?,
        profile: String?,
    ): Boolean {
        return OcrPluginSelection.matches(pluginInfo, engineId, engine, variant, profile)
    }

    private fun hasExplicitSelectionCriteria(
        engineId: String?,
        engine: String?,
        variant: String?,
        profile: String?,
    ): Boolean {
        return OcrPluginSelection.hasExplicitCriteria(engineId, engine, variant, profile)
    }

    private fun isHostVersionCompatible(pluginInfo: PluginInfo?): Boolean {
        val requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(pluginInfo?.capabilities)
        return PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), requiresHostVersion)
    }

    private fun ensureHostVersionCompatible(context: Context, target: Discovered) {
        val requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(target.pluginInfo?.capabilities)
        if (PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), requiresHostVersion)) {
            return
        }
        throw IllegalStateException(
            context.getString(
                R.string.hint_plugin_requires_higher_host_version_with_required_and_current,
                requiresHostVersion ?: 0L,
                BuildConfig.VERSION_CODE.toLong(),
            )
        )
    }

    private suspend fun recoverFailedDiscoveries(context: Context, discovered: List<Discovered>) {
        var didWake = false
        discovered
            .filter { it.error != null }
            .forEach { item ->
                val error = item.error!!
                invalidateService(item.serviceInfo, error)
                // Refused bindings were already woken inside the bind (PluginAutoWake); wake only enabled and authorized plugins here.
                // zh-CN: 被拒的绑定已在绑定内部唤醒 (PluginAutoWake); 这里只唤醒已启用并已授权的插件.
                val packageName = item.serviceInfo.packageName
                if (!error.isPluginBindRefused() && PluginAutoWake.isEligible(context, packageName)) {
                    didWake = PluginWakeManager.tryAutoWakeIfNeeded(context, packageName) || didWake
                }
            }
        if (didWake) {
            delay(RETRY_WAKE_DELAY_MS)
        }
    }

    private suspend fun <T> withImagePfdService(
        context: Context,
        serviceInfo: ServiceInfo,
        bitmap: Bitmap,
        options: OcrOptions,
        callTimeoutMs: Long,
        start: Long,
        block: suspend (IOcrPlugin, ParcelFileDescriptor) -> T,
    ): T {
        return try {
            withImagePfdServiceOnce(context, serviceInfo, bitmap, options, callTimeoutMs, start, block)
        } catch (e: RemoteException) {
            invalidateService(serviceInfo, e)
            try {
                withImagePfdServiceOnce(context, serviceInfo, bitmap, options, callTimeoutMs, start, block)
            } catch (retryError: RemoteException) {
                invalidateService(serviceInfo, retryError)
                throw retryError
            }
        }
    }

    private suspend fun <T> withImagePfdServiceOnce(
        context: Context,
        serviceInfo: ServiceInfo,
        bitmap: Bitmap,
        options: OcrOptions,
        callTimeoutMs: Long,
        start: Long,
        block: suspend (IOcrPlugin, ParcelFileDescriptor) -> T,
    ): T {
        return createTempPfd(bitmap, options).use { pfd ->
            withService(context, serviceInfo, DEFAULT_BIND_TIMEOUT_MS, retryOnRemoteException = false) { proxy ->
                val remain = callTimeoutMs - (uptimeMillis() - start)
                if (remain <= 0) error("AIDL call timeout in ${callTimeoutMs / 1000} seconds")
                block(proxy, pfd)
            }
        }
    }

    // Convert temporary file to read-only FD.
    // zh-CN: 临时文件转换为只读 FD.
    private fun createTempPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val useRaw = options.extras?.getBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, false) == true
        if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return runCatching { createRawPfd(bmp, options) }
                .getOrElse {
                    Log.w(TAG, "raw image transfer failed: ${it.message}")
                    disableRawExtras(options)
                    createEncodedPfd(bmp, options)
                }
        }
        if (useRaw) {
            disableRawExtras(options)
        }
        return createEncodedPfd(bmp, options)
    }

    private fun createEncodedPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val (format, quality) = resolveEncodeOptions(bmp, options)
        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    require(bmp.compress(format, quality, out)) { "Failed to encode bitmap" }
                }
            }.onFailure { t ->
                Log.e(TAG, "encode bitmap failed: ${t.message}")
            }
        }
        return readFd
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun createRawPfd(bmp: Bitmap, options: OcrOptions): ParcelFileDescriptor {
        val src = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val shouldRecycle = src !== bmp
        val rowBytes = src.rowBytes
        val size = rowBytes * src.height

        val extras = options.extras ?: Bundle().also { options.extras = it }
        extras.putBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, true)
        extras.putInt(PaddleOcrOptionExtraKeys.RAW_WIDTH, src.width)
        extras.putInt(PaddleOcrOptionExtraKeys.RAW_HEIGHT, src.height)
        extras.putInt(PaddleOcrOptionExtraKeys.RAW_STRIDE, rowBytes)
        extras.putString(PaddleOcrOptionExtraKeys.RAW_CONFIG, Bitmap.Config.ARGB_8888.name)

        runCatching {
            val shm = SharedMemory.create("ocr_ipc_raw", size)
            val buffer = shm.mapReadWrite()
            buffer.order(ByteOrder.nativeOrder())
            src.copyPixelsToBuffer(buffer)
            SharedMemory.unmap(buffer)

            val pfd = dupSharedMemoryFd(shm)
            shm.close()
            if (pfd != null) {
                if (shouldRecycle) {
                    src.recycle()
                }
                return pfd
            }
        }.onFailure { t ->
            Log.w(TAG, "shared memory unavailable, fallback to raw pipe: ${t.message}")
        }

        return createRawPipePfd(src, size, shouldRecycle)
    }

    private fun createRawPipePfd(
        bmp: Bitmap,
        size: Int,
        shouldRecycle: Boolean,
    ): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]
        val raw = ByteArray(size)
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buffer)

        if (shouldRecycle) {
            bmp.recycle()
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    out.write(raw, 0, size)
                }
            }.onFailure { t ->
                Log.e(TAG, "write raw pipe failed: ${t.message}")
            }
        }
        return readFd
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun dupSharedMemoryFd(shm: SharedMemory): ParcelFileDescriptor? {
        val method = shm.javaClass.methods.firstOrNull { it.name == "getFdDup" && it.parameterCount == 0 }
            ?: shm.javaClass.methods.firstOrNull { it.name == "getFileDescriptor" && it.parameterCount == 0 }
        val result = method?.let { runCatching { it.invoke(shm) }.getOrNull() }
        return when (result) {
            is ParcelFileDescriptor -> result
            is FileDescriptor -> ParcelFileDescriptor.dup(result)
            else -> null
        }
    }

    private fun resolveEncodeOptions(bmp: Bitmap, options: OcrOptions): Pair<CompressFormat, Int> {
        val extras = options.extras
        val formatName = extras?.getString(PaddleOcrOptionExtraKeys.IMAGE_FORMAT)?.trim()?.lowercase().orEmpty()
        val quality = extras?.getInt(PaddleOcrOptionExtraKeys.IMAGE_QUALITY, 100) ?: 100
        val resolvedQuality = quality.coerceIn(1, 100)
        // WEBP is required on Android versions before API 30.
        // zh-CN: API 30 之前的 Android 版本仍需使用 WEBP.
        @Suppress("DEPRECATION")
        val resolvedFormat = when (formatName) {
            "png" -> CompressFormat.PNG
            "jpg", "jpeg" -> CompressFormat.JPEG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CompressFormat.WEBP_LOSSY else CompressFormat.WEBP
            "webp_lossless" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CompressFormat.WEBP_LOSSLESS else CompressFormat.WEBP
            else -> if (bmp.hasAlpha()) CompressFormat.PNG else CompressFormat.JPEG
        }
        return resolvedFormat to resolvedQuality
    }

    private fun disableRawExtras(options: OcrOptions) {
        val extras = options.extras ?: return
        extras.remove(PaddleOcrOptionExtraKeys.RAW_IMAGE)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_WIDTH)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_HEIGHT)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_STRIDE)
        extras.remove(PaddleOcrOptionExtraKeys.RAW_CONFIG)
    }

    private fun ensureRawSupport(target: Discovered, options: OcrOptions) {
        val extras = options.extras ?: return
        if (!extras.getBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, false)) return
        val supportsRaw = target.pluginInfo?.capabilities?.getBoolean(PaddleOcrPluginCapabilityKeys.SUPPORTS_RAW_IMAGE, false) == true
        if (!supportsRaw) {
            disableRawExtras(options)
        }
    }

    private fun queryOcrServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        return queryOcrServiceCandidates(context, packageName).mapNotNull { it.serviceInfo }
    }

    private fun queryOcrServiceCandidates(context: Context, packageName: String? = null): List<ResolveInfo> {
        val pm = context.packageManager
        val intent = Intent(ACTION_OCR).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER.toLong()))
        } else {
            pm.queryIntentServices(intent, PackageManager.GET_RESOLVED_FILTER)
        }
    }

    private fun buildBindFlags(serviceInfo: ServiceInfo): Int {
        var flags = Context.BIND_AUTO_CREATE
        if (externalServiceFlag != 0 && bindExternalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0) {
            flags = flags or bindExternalServiceFlag
        }
        return flags
    }

    private fun buildBindFailureMessage(context: Context, serviceInfo: ServiceInfo, cn: ComponentName, reason: String? = null): String {
        val pm = context.packageManager
        val perm = serviceInfo.permission
        val hasPerm = perm.isNullOrBlank() || pm.checkPermission(perm, context.packageName) == PackageManager.PERMISSION_GRANTED
        val appInfo = serviceInfo.applicationInfo
        val appStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val isExternal = externalServiceFlag != 0 && (serviceInfo.flags and externalServiceFlag) != 0
        val reasonText = reason?.let { " | reason=$it" } ?: ""
        return "bindService failed: $cn. exported=${serviceInfo.exported} enabled=${serviceInfo.enabled} appEnabled=${appInfo.enabled} stopped=$appStopped perm=$perm hasPerm=$hasPerm external=$isExternal flags=0x${Integer.toHexString(serviceInfo.flags)}$reasonText"
    }

    private suspend fun <T> withService(
        context: Context,
        serviceInfo: ServiceInfo,
        bindTimeoutMs: Long = DEFAULT_BIND_TIMEOUT_MS,
        retryOnRemoteException: Boolean = true,
        block: suspend (IOcrPlugin) -> T,
    ): T {
        val appCtx = context.applicationContext
        val entry = getOrCreateEntry(appCtx, serviceInfo)
        val proxy = PluginAutoWake.retryRefusedBind(entry.appCtx, entry.serviceInfo.packageName) { awaitProxy(entry, bindTimeoutMs) }
        return try {
            block(proxy)
        } catch (e: RemoteException) {
            if (retryOnRemoteException) {
                retryAfterBinderFailure(entry, bindTimeoutMs, e, block)
            } else {
                throw e
            }
        } finally {
            touch(entry)
        }
    }

    private fun invalidateService(serviceInfo: ServiceInfo, error: Throwable) {
        val cn = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val entry = synchronized(poolLock) { connectionPool[cn] }
        if (entry != null) {
            invalidateEntry(entry, error)
        }
    }

    private suspend fun <T> retryAfterBinderFailure(
        entry: PooledConnection,
        bindTimeoutMs: Long,
        error: RemoteException,
        block: suspend (IOcrPlugin) -> T,
    ): T {
        invalidateEntry(entry, error)
        val proxy = PluginAutoWake.retryRefusedBind(entry.appCtx, entry.serviceInfo.packageName) { awaitProxy(entry, bindTimeoutMs) }
        return try {
            block(proxy)
        } catch (retryError: RemoteException) {
            invalidateEntry(entry, retryError)
            throw retryError
        }
    }

    private fun touch(entry: PooledConnection) {
        synchronized(entry.lock) {
            entry.lastUsed = uptimeMillis()
        }
        scheduleIdleUnbind(entry)
    }

    private suspend fun awaitProxy(entry: PooledConnection, bindTimeoutMs: Long): IOcrPlugin =
        suspendCancellableCoroutine { cont ->
            // Serialize registration and cancellation with ServiceConnection callbacks.
            // zh-CN: 绑定注册和取消与 ServiceConnection 回调在同一线程执行.
            poolHandler.post {
                if (!cont.isActive) return@post
                var shouldBind = false
                synchronized(entry.lock) {
                    entry.lastUsed = uptimeMillis()
                    cancelIdleUnbindLocked(entry)
                    val existing = entry.proxy
                    if (existing != null && entry.bound && entry.isBinderAliveLocked()) {
                        cont.resume(existing)
                        return@post
                    }
                    if (existing != null) {
                        entry.clearConnectionLocked()
                    }
                    entry.waiters.add(cont)
                    if (!entry.connecting) {
                        entry.connecting = true
                        shouldBind = true
                    }
                }
                if (shouldBind) {
                    startBind(entry, bindTimeoutMs)
                }
            }
            cont.invokeOnCancellation {
                poolHandler.post {
                    val abandoned = synchronized(entry.lock) {
                        entry.waiters.remove(cont)
                        if (entry.waiters.isEmpty() && entry.connecting) {
                            entry.connecting = false
                            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                            entry.bindTimeoutRunnable = null
                            entry.clearConnectionLocked()
                            true
                        } else false
                    }
                    if (abandoned) {
                        runCatching { entry.appCtx.unbindService(entry.connection) }
                    } else {
                        scheduleIdleUnbind(entry)
                    }
                }
            }
        }

    private fun startBind(entry: PooledConnection, bindTimeoutMs: Long) {
        val intent = Intent().setComponent(entry.cn)
        val bindFlags = buildBindFlags(entry.serviceInfo)
        var ok = try {
            Log.i(TAG, "bindService: ${entry.cn}")
            entry.appCtx.bindService(intent, entry.connection, bindFlags)
        } catch (se: SecurityException) {
            failWaiters(
                entry,
                IllegalStateException(
                    "bindService SecurityException: ${entry.cn}. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.",
                    se
                )
            )
            return
        }
        if (!ok && bindFlags != Context.BIND_AUTO_CREATE) {
            ok = try {
                entry.appCtx.bindService(intent, entry.connection, Context.BIND_AUTO_CREATE)
            } catch (se: SecurityException) {
                failWaiters(
                    entry,
                    IllegalStateException(
                        "bindService SecurityException: ${entry.cn}. Please make sure the plugin declares <uses-permission android:name=\"org.autojs.permission.PLUGIN\"/> and the Service uses this permission.",
                        se
                    )
                )
                return
            }
        }
        if (!ok) {
            val msg = buildBindFailureMessage(entry.appCtx, entry.serviceInfo, entry.cn)
            Log.e(TAG, msg)
            failWaiters(entry, PluginBindRefusedException(entry.serviceInfo.packageName, msg))
            return
        }

        val timeout = Runnable {
            val pending = synchronized(entry.lock) {
                entry.connecting = false
                val list = entry.waiters.toList()
                entry.waiters.clear()
                entry.bindTimeoutRunnable = null
                list
            }
            if (pending.isNotEmpty()) {
                Log.e(TAG, "bindService timeout: ${entry.cn}")
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resumeWithException(TimeoutException("bindService timeout: ${entry.cn}"))
                    }
                }
                runCatching { entry.appCtx.unbindService(entry.connection) }
            }
        }
        synchronized(entry.lock) {
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = timeout
        }
        poolHandler.postDelayed(timeout, bindTimeoutMs)
    }

    private fun invalidateEntry(entry: PooledConnection, error: Throwable) {
        val shouldUnbind = synchronized(entry.lock) {
            entry.clearConnectionLocked()
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = null
            true
        }
        if (shouldUnbind) {
            poolHandler.post {
                runCatching { entry.appCtx.unbindService(entry.connection) }
                Log.w(TAG, "invalidateEntry: ${entry.cn} | ${error.message}")
            }
        }
    }

    private fun failWaiters(entry: PooledConnection, error: Throwable) {
        val pending = synchronized(entry.lock) {
            entry.connecting = false
            entry.bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.bindTimeoutRunnable = null
            val list = entry.waiters.toList()
            entry.waiters.clear()
            list
        }
        pending.forEach { waiter ->
            if (!waiter.isCompleted) {
                waiter.resumeWithException(error)
            }
        }
    }

    private fun scheduleIdleUnbind(entry: PooledConnection) {
        val runnable = Runnable {
            val shouldUnbind = synchronized(entry.lock) {
                val idleEnough = uptimeMillis() - entry.lastUsed >= IDLE_UNBIND_MS
                idleEnough && entry.bound && entry.proxy != null && !entry.connecting
            }
            if (shouldUnbind) {
                runCatching { entry.appCtx.unbindService(entry.connection) }
                synchronized(entry.lock) {
                    entry.clearConnectionLocked()
                }
            }
        }
        synchronized(entry.lock) {
            entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
            entry.idleRunnable = runnable
        }
        poolHandler.postDelayed(runnable, IDLE_UNBIND_MS)
    }

    private fun cancelIdleUnbindLocked(entry: PooledConnection) {
        entry.idleRunnable?.let { poolHandler.removeCallbacks(it) }
        entry.idleRunnable = null
    }

    private fun getOrCreateEntry(appCtx: Context, serviceInfo: ServiceInfo): PooledConnection {
        val cn = ComponentName(serviceInfo.packageName, serviceInfo.name)
        synchronized(poolLock) {
            return connectionPool.getOrPut(cn) {
                PooledConnection(appCtx, serviceInfo)
            }
        }
    }

    private class PooledConnection(
        val appCtx: Context,
        val serviceInfo: ServiceInfo,
    ) {
        val cn: ComponentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val lock = Any()
        val waiters = ArrayList<CancellableContinuation<IOcrPlugin>>()
        var proxy: IOcrPlugin? = null
        var binder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var bound: Boolean = false
        var connecting: Boolean = false
        var lastUsed: Long = 0L
        var idleRunnable: Runnable? = null
        var bindTimeoutRunnable: Runnable? = null

        fun isBinderAliveLocked(): Boolean {
            val activeBinder = binder ?: proxy?.asBinder()
            return activeBinder?.isBinderAlive == true
        }

        fun clearConnectionLocked() {
            val currentBinder = binder
            val currentDeathRecipient = deathRecipient
            if (currentBinder != null && currentDeathRecipient != null) {
                runCatching { currentBinder.unlinkToDeath(currentDeathRecipient, 0) }
            }
            binder = null
            deathRecipient = null
            proxy = null
            bound = false
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "onServiceConnected: $name")
                if (!synchronized(lock) { connecting }) {
                    runCatching { appCtx.unbindService(this) }
                    return
                }
                val proxy = IOcrPlugin.Stub.asInterface(binder)
                val deathRecipient = IBinder.DeathRecipient {
                    Log.w(TAG, "binderDied: $name")
                    handleDisconnect(name, "binderDied", shouldUnbind = true)
                }
                try {
                    binder.linkToDeath(deathRecipient, 0)
                } catch (e: RemoteException) {
                    Log.w(TAG, "linkToDeath failed: $name", e)
                    handleDisconnect(name, "linkToDeath", shouldUnbind = true)
                    return
                }
                val pending = synchronized(lock) {
                    clearConnectionLocked()
                    this@PooledConnection.binder = binder
                    this@PooledConnection.deathRecipient = deathRecipient
                    this@PooledConnection.proxy = proxy
                    bound = true
                    connecting = false
                    lastUsed = uptimeMillis()
                    bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                    bindTimeoutRunnable = null
                    val list = waiters.toList()
                    waiters.clear()
                    list
                }
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resume(proxy)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "onServiceDisconnected: $name")
                handleDisconnect(name, "onServiceDisconnected", shouldUnbind = true)
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(TAG, "onBindingDied: $name")
                handleDisconnect(name, "onBindingDied", shouldUnbind = true)
            }

            override fun onNullBinding(name: ComponentName) {
                Log.w(TAG, "onNullBinding: $name")
                handleDisconnect(name, "onNullBinding", shouldUnbind = true)
            }
        }

        private fun handleDisconnect(name: ComponentName, reason: String, shouldUnbind: Boolean = false) {
            val pending = synchronized(lock) {
                clearConnectionLocked()
                connecting = false
                bindTimeoutRunnable?.let { poolHandler.removeCallbacks(it) }
                bindTimeoutRunnable = null
                idleRunnable?.let { poolHandler.removeCallbacks(it) }
                idleRunnable = null
                val list = waiters.toList()
                waiters.clear()
                list
            }
            if (pending.isNotEmpty()) {
                val error = IllegalStateException("$reason: $name")
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) {
                        waiter.resumeWithException(error)
                    }
                }
            }
            if (shouldUnbind) {
                poolHandler.post {
                    runCatching { appCtx.unbindService(connection) }
                }
            }
        }
    }

    private data class SelectionResult(
        val selected: Discovered?,
        val discovered: List<Discovered>,
    )

    private class TimeoutException(msg: String) : RuntimeException(msg)

}
