package org.autojs.autojs.core.plugin.mail

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.mail.api.IMailPlugin
import org.autojs.plugin.mail.api.MailActions
import org.autojs.plugin.mail.api.MailCapabilityKeys
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailIds

/** How long a refused `openSession` waits for the plugin's `closed` status on the oneway callback. */
private const val REFUSAL_TIMEOUT_MS = 3_000L

/** The `triggers` document of a plugin without background watches. */
private const val EMPTY_TRIGGERS = "{\"triggers\":[]}"

/**
 * Host wrapper for the mail Binder contract (roadmap P1.2, decisions D1 and D19): the plugin is
 * enabled by default, any package answering the `org.autojs.plugin.MAIL` query with the `mail`
 * category and engine may be selected, and the plugin must declare a contract version the host
 * understands plus a `requiresHostVersion` not below the build that ships this module.
 */
object MailPluginHost {

    const val ACTION_MAIL = MailActions.SERVICE_ACTION

    private const val LABEL = "Mail plugin"

    private val signedHost = createHost(ACTION_MAIL, MailActions.PLUGIN_PERMISSION)
    private val packagedHost = createHost(MailActions.PACKAGED_SERVICE_ACTION, MailActions.PACKAGED_PERMISSION)

    internal fun isPackaged(context: Context): Boolean =
        context.packageName != "org.autojs.autojs6" ||
            context.checkSelfPermission(MailActions.PLUGIN_PERMISSION) != PackageManager.PERMISSION_GRANTED

    private fun hostFor(context: Context) = if (isPackaged(context)) packagedHost else signedHost

    private fun createHost(action: String, permission: String) = AidlPluginHost(
        action = action,
        category = MailActions.SERVICE_CATEGORY,
        tag = "MailPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> IMailPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(MailIds.ENGINE),
        preBindValidator = { context, service -> validateService(context, service, action, permission) },
    )

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        hostFor(context).discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        hostFor(context).probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = hostFor(context).queryServiceCount(context)

    suspend fun getCapabilities(context: Context): Bundle = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        hostFor(context).call(context, target) { proxy -> proxy.getCapabilities() ?: Bundle() }
    }

    /** Provider preset table as JSON (`[]` when the plugin returns nothing). */
    suspend fun listProviders(context: Context): String = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        hostFor(context).call(context, target) { proxy -> MailBundles.string(proxy.listProviders(), MailContract.KEY_PROVIDERS_JSON) ?: "[]" }
    }

    /** Saved accounts without secrets as JSON (`[]` when the plugin returns nothing). */
    suspend fun listSavedAccounts(context: Context): String = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        hostFor(context).call(context, target) { proxy -> MailBundles.string(proxy.listSavedAccounts(), MailContract.KEY_ACCOUNTS_JSON) ?: "[]" }
    }

    /**
     * The background watches the plugin keeps (contract version 2, mail roadmap P8) as the
     * `triggers` document; the empty document when the plugin predates version 2 or does not
     * offer the `backgroundWatch` feature, so a version 1 plugin never sees the new call.
     */
    suspend fun listTriggers(context: Context): String = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        hostFor(context).call(context, target) { proxy ->
            when {
                !supportsBackgroundWatch(proxy.getCapabilities()) -> EMPTY_TRIGGERS
                else -> MailBundles.string(proxy.listTriggers(), MailContract.KEY_TRIGGERS_JSON) ?: EMPTY_TRIGGERS
            }
        }
    }

    /** True when [capabilities] declare contract version 2 or later and the `backgroundWatch` feature. */
    fun supportsBackgroundWatch(capabilities: Bundle?): Boolean {
        capabilities ?: return false
        val version = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(MailCapabilityKeys.CONTRACT_VERSION),
        )?.toInt() ?: 0
        return supportsBackgroundWatch(version, capabilities.getStringArray(MailCapabilityKeys.FEATURES)?.filterNotNull().orEmpty())
    }

    internal fun supportsBackgroundWatch(contractVersion: Int, features: Collection<String>): Boolean =
        contractVersion >= MailContract.TRIGGER_CONTRACT_VERSION && MailContract.FEATURE_BACKGROUND_WATCH in features

    /**
     * Opens a session on a dedicated binding lease. The returned port owns the lease and the death
     * recipient; [MailSessionClient] closes it and reopens through [sessionOpener]. A port opened
     * after the caller was cancelled (the script stopped) is closed again (Q59).
     */
    suspend fun openSession(context: Context, account: MailAccountSpec, events: MailSessionEvents): MailSessionPort =
        AidlPluginHost.withContextAbandoningLate(Dispatchers.IO, MailSessionPort::close) {
            account.validate()?.let { throw MailPluginException(it) }
            val target = selectOrThrow(context)
            val callback = MailSessionCallbackBinder(events)
            val leased = hostFor(context).callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
                proxy.openSession(MailBundles.accountToBundle(account, BuildConfig.VERSION_CODE.toLong()), callback)
                    ?: throw refusal(callback)
            }
            BinderMailSessionPort(leased, events)
        }

    /**
     * The error of a refused `openSession`: the plugin reports it through the oneway session
     * callback, which may land after the null return, so the opener waits briefly for it.
     */
    private fun refusal(callback: MailSessionCallbackBinder): Exception {
        val status = callback.awaitClosed(REFUSAL_TIMEOUT_MS)
        val error = status?.lastError
            ?: return IllegalStateException("mail plugin refused the account" + (status?.reason?.let { ": $it" } ?: ""))
        return MailPluginException(error)
    }

    fun sessionOpener(context: Context): MailSessionOpener {
        val appContext = context.applicationContext
        return MailSessionOpener { account, events -> openSession(appContext, account, events) }
    }

    fun newClient(context: Context, account: MailAccountSpec, tokenProvider: (suspend () -> String)? = null): MailSessionClient {
        return MailSessionClient(account, sessionOpener(context), tokenProvider)
    }

    private suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return hostFor(context).selectOrThrow(
            context = context,
            engineId = null,
            engine = MailIds.ENGINE,
            variant = MailIds.VARIANT_DEFAULT,
            label = LABEL,
        )
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.engine == MailIds.ENGINE) { "Unexpected mail plugin engine: ${it.engine}" }
        require(it.variant == MailIds.VARIANT_DEFAULT) { "Unexpected mail plugin variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "Mail plugin capabilities are missing" }
        val contractVersion = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(MailCapabilityKeys.CONTRACT_VERSION),
        )?.toInt() ?: 0
        require(MailContract.supportsContractVersion(contractVersion)) {
            "Unsupported mail contract version: $contractVersion"
        }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= MailIds.REQUIRED_HOST_VERSION_CODE) {
            "Mail plugin required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, serviceInfo: ServiceInfo, action: String, permission: String) {
        require(serviceInfo.exported && serviceInfo.enabled) {
            "Mail plugin service is not exported and enabled"
        }
        require(serviceInfo.permission == permission) {
            "Mail plugin service must require $permission"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName, action)) {
            "Mail plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String, action: String): Set<ComponentName> {
        val intent = Intent(action).apply {
            addCategory(MailActions.SERVICE_CATEGORY)
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
