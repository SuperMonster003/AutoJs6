package org.autojs.autojs.core.plugin.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.terminal.api.ITerminalCallback
import org.autojs.plugin.terminal.api.ITerminalPlugin
import org.autojs.plugin.terminal.api.TerminalActions
import org.autojs.plugin.terminal.api.TerminalCapabilityKeys
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.autojs.plugin.terminal.api.TerminalIds
import java.io.Closeable

/**
 * Host wrapper for the terminal Binder contract (plugin roadmap P1.2, decisions D3 / D25): any
 * package answering the `org.autojs.plugin.TERMINAL` query with the `terminal` category and
 * engine may be selected, and the plugin must declare a contract version the host understands plus
 * a `requiresHostVersion` not below the build that ships `plugin-api/terminal-api`.
 *
 * Every method returns promptly on the plugin side (appendix B.3), so the pooled binding of
 * [AidlPluginHost] serves them all; output subscriptions (P4.2) add their own leases later.
 * Failures carry the contract codes as [TerminalPluginException].
 */
object TerminalPluginHost {

    const val ACTION_TERMINAL = TerminalActions.SERVICE_ACTION

    private const val LABEL = "terminal plugin"

    private val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(TerminalIds.ENGINE)

    private val host = AidlPluginHost(
        action = ACTION_TERMINAL,
        category = TerminalActions.SERVICE_CATEGORY,
        tag = "TerminalPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> ITerminalPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = defaultEnabled,
        preBindValidator = ::validateService,
    )

    /** `readTranscript` answer: the newest text, and whether older text was cut to honor the byte limit. */
    data class Transcript(val text: String, val truncated: Boolean)

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        host.discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    /** Whether a selectable, enabled and authorized plugin answers the query right now (no binding). */
    suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { selectOrThrow(context) }.isSuccess
    }

    suspend fun getCapabilities(context: Context): Bundle = call(context, TerminalErrorMapper.Phase.OPEN) { proxy ->
        proxy.getCapabilities() ?: Bundle()
    }

    suspend fun listSessions(context: Context): List<TerminalJson.Session> = call(context) { proxy ->
        TerminalJson.decodeSessions(proxy.listSessions())
    }

    suspend fun openSession(context: Context, request: TerminalJson.OpenRequest): TerminalJson.Session {
        val json = TerminalJson.encodeOpenRequest(request)
        return call(context) { proxy -> TerminalJson.decodeSession(proxy.openSession(json)) }
    }

    suspend fun closeSession(context: Context, sessionId: String): Boolean = call(context) { proxy ->
        proxy.closeSession(requireSessionId(sessionId))
    }

    suspend fun closeAllSessions(context: Context): Int = call(context) { proxy -> proxy.closeAllSessions() }

    suspend fun writeInput(context: Context, sessionId: String, data: ByteArray) {
        if (data.size > TerminalContract.MAX_INPUT_BYTES) {
            throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, "input is longer than ${TerminalContract.MAX_INPUT_BYTES} bytes", sessionId))
        }
        call(context) { proxy -> proxy.writeInput(requireSessionId(sessionId), data) }
    }

    suspend fun readTranscript(context: Context, sessionId: String, maxBytes: Int = TerminalContract.MAX_TRANSCRIPT_BYTES): Transcript {
        if (maxBytes <= 0 || maxBytes > TerminalContract.MAX_TRANSCRIPT_BYTES) {
            throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, "maxBytes must be within 1..${TerminalContract.MAX_TRANSCRIPT_BYTES}", sessionId))
        }
        return call(context) { proxy ->
            val bundle = proxy.readTranscript(requireSessionId(sessionId), maxBytes) ?: Bundle()
            TerminalErrorMapper.fromBundle(bundle)?.let { throw TerminalPluginException(it) }
            Transcript(bundle.getString(TerminalContract.KEY_TEXT).orEmpty(), bundle.getBoolean(TerminalContract.KEY_TRUNCATED, false))
        }
    }

    suspend fun getEnvironment(context: Context): TerminalJson.Environment = call(context) { proxy ->
        TerminalJson.decodeEnvironment(proxy.getEnvironment())
    }

    /** Registers [callback] on the pooled binding; it stays valid until the plugin process or the idle binding goes away (D26). */
    suspend fun registerCallback(context: Context, callback: ITerminalCallback) = call(context) { proxy -> proxy.registerCallback(callback) }

    suspend fun unregisterCallback(context: Context, callback: ITerminalCallback) = call(context) { proxy -> proxy.unregisterCallback(callback) }

    /** An output subscription (B.1): the plugin's pipe read end for one session and the id that cancels it. The receiver owns the read end. */
    data class OutputSubscription(val id: String, val readEnd: ParcelFileDescriptor)

    /** `subscribeOutput` for [sessionId]; [fromStart] replays the current transcript first (B.2). Plugin roadmap P4.2. */
    suspend fun subscribeOutput(context: Context, sessionId: String, fromStart: Boolean = false): OutputSubscription = call(context) { proxy ->
        val options = JsonObject().apply { addProperty(TerminalContract.FIELD_FROM_START, fromStart) }.toString()
        val bundle = proxy.subscribeOutput(requireSessionId(sessionId), options) ?: Bundle()
        TerminalErrorMapper.fromBundle(bundle)?.let { throw TerminalPluginException(it) }
        val id = bundle.getString(TerminalContract.KEY_SUBSCRIPTION_ID)?.takeIf { it.isNotBlank() }
            ?: throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INTERNAL, "subscribeOutput returned no subscription id", sessionId))
        val readEnd = BundleCompat.getParcelable(bundle, TerminalContract.KEY_FD, ParcelFileDescriptor::class.java)
            ?: throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INTERNAL, "subscribeOutput returned no pipe", sessionId))
        OutputSubscription(id, readEnd)
    }

    suspend fun unsubscribeOutput(context: Context, subscriptionId: String) = call(context) { proxy -> proxy.unsubscribeOutput(subscriptionId) }

    /**
     * Registers [callback] over a dedicated binding and returns its lease: the plugin stays bound,
     * so the callback and the output subscriptions of this client survive the idle unbinding of
     * the pooled connection (D26), until the lease is closed. Plugin roadmap P4.2.
     */
    suspend fun registerCallbackLeased(context: Context, callback: ITerminalCallback): Closeable = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        try {
            host.callWithDedicatedBindingLease(context, target) { proxy -> proxy.registerCallback(callback) }.bindingLease
        } catch (e: TerminalPluginException) {
            throw e
        } catch (t: Throwable) {
            throw TerminalPluginException(TerminalErrorMapper.fromThrowable(t, TerminalErrorMapper.Phase.CALL), t)
        }
    }

    private suspend fun <T> call(context: Context, phase: TerminalErrorMapper.Phase = TerminalErrorMapper.Phase.CALL, block: (ITerminalPlugin) -> T): T = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        try {
            host.call(context, target, block = block)
        } catch (e: TerminalPluginException) {
            throw e
        } catch (t: Throwable) {
            throw TerminalPluginException(TerminalErrorMapper.fromThrowable(t, phase), t)
        }
    }

    private suspend fun selectForCall(context: Context): AidlPluginHost.Discovered = try {
        selectOrThrow(context)
    } catch (e: TerminalPluginException) {
        throw e
    } catch (t: Throwable) {
        throw TerminalPluginException(discoveryError(context, t), t)
    }

    /** `PLUGIN_UNAVAILABLE` whose message says "disabled" when a package answers the query but the user disabled every one of them. */
    private suspend fun discoveryError(context: Context, cause: Throwable): TerminalError {
        val mapped = TerminalErrorMapper.fromThrowable(cause, TerminalErrorMapper.Phase.DISCOVERY)
        if (mapped.code != TerminalErrorCodes.PLUGIN_UNAVAILABLE) return mapped
        val packages = runCatching { host.discover(context).map { it.serviceInfo.packageName }.distinct() }.getOrDefault(emptyList())
        if (packages.isEmpty() || packages.any { PluginEnableStore.isEnabled(context, it, defaultEnabled) }) return mapped
        return TerminalErrorMapper.normalize(TerminalErrorCodes.PLUGIN_UNAVAILABLE, "terminal plugin is disabled: " + packages.joinToString())
    }

    internal suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered = host.selectOrThrow(
        context = context,
        engineId = null,
        engine = TerminalIds.ENGINE,
        variant = TerminalIds.VARIANT_DEFAULT,
        label = LABEL,
    )

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.engine == TerminalIds.ENGINE) { "Unexpected terminal plugin engine: ${it.engine}" }
        require(it.variant == TerminalIds.VARIANT_DEFAULT) { "Unexpected terminal plugin variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "terminal plugin capabilities are missing" }
        val contractVersion = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(TerminalCapabilityKeys.CONTRACT_VERSION),
        )?.toInt() ?: 0
        require(TerminalContract.supportsContractVersion(contractVersion)) {
            "Unsupported terminal contract version: $contractVersion"
        }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= TerminalIds.REQUIRED_HOST_VERSION_CODE) {
            "terminal plugin required host version is missing or invalid"
        }
    }

    private fun requireSessionId(sessionId: String): String {
        if (sessionId.isBlank()) {
            throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, "sessionId is blank"))
        }
        return sessionId
    }

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.exported && serviceInfo.enabled) {
            "terminal plugin service is not exported and enabled"
        }
        require(serviceInfo.permission == TerminalActions.PLUGIN_PERMISSION) {
            "terminal plugin service must require ${TerminalActions.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName)) {
            "terminal plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String): Set<ComponentName> {
        val intent = Intent(TerminalActions.SERVICE_ACTION).apply {
            addCategory(TerminalActions.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(packageName)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { item ->
            item.serviceInfo?.let { service -> ComponentName(service.packageName, service.name) }
        }.toSet()
    }
}
