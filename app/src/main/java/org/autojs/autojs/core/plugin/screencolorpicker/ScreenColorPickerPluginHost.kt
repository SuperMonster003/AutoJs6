package org.autojs.autojs.core.plugin.screencolorpicker

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.screencolorpicker.api.IScreenColorPickerPlugin
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerContract
import org.autojs.plugin.screencolorpicker.api.ScreenColorPickerStates

/** Strict host wrapper for the official Screen Color Picker Binder contract. */
object ScreenColorPickerPluginHost {

    const val ACTION_SCREEN_COLOR_PICKER = ScreenColorPickerContract.SERVICE_ACTION

    private val host = AidlPluginHost(
        action = ACTION_SCREEN_COLOR_PICKER,
        tag = "ScreenColorPickerHost",
        moduleLabel = "Screen Color Picker",
        proxyFactory = { binder -> IScreenColorPickerPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        preBindValidator = ::validateService,
    )

    suspend fun discover(context: Context) = withContext(Dispatchers.IO) {
        host.discover(context, ScreenColorPickerContract.DEFAULT_PACKAGE_NAME)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        require(packageName == ScreenColorPickerContract.DEFAULT_PACKAGE_NAME) {
            "Unsupported Screen Color Picker package: $packageName"
        }
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = queryContractServices(context).size

    suspend fun getState(context: Context): Int = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy ->
            ScreenColorPickerStates.requireKnown(proxy.getState())
        }
    }

    suspend fun getStartPendingIntent(context: Context): PendingIntent = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy ->
            checkNotNull(proxy.getStartPendingIntent()) {
                "Screen Color Picker returned no start PendingIntent"
            }
        }
    }

    suspend fun stop(context: Context): Boolean = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy -> proxy.stop() }
    }

    private suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return host.selectOrThrow(
            context = context,
            engineId = ScreenColorPickerContract.PLUGIN_ID,
            engine = ScreenColorPickerContract.ENGINE,
            variant = ScreenColorPickerContract.VARIANT_DEFAULT,
            label = "Screen Color Picker",
        )
    }

    private fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.id == ScreenColorPickerContract.PLUGIN_ID) {
            "Unexpected Screen Color Picker plugin id: ${it.id}"
        }
        require(it.engine == ScreenColorPickerContract.ENGINE) {
            "Unexpected Screen Color Picker engine: ${it.engine}"
        }
        require(it.variant == ScreenColorPickerContract.VARIANT_DEFAULT) {
            "Unexpected Screen Color Picker variant: ${it.variant}"
        }
        val capabilities = requireNotNull(it.capabilities) {
            "Screen Color Picker capabilities are missing"
        }
        val contractVersion = capabilities.getInt(
            ScreenColorPickerContract.CAPABILITY_CONTRACT_VERSION,
            0,
        )
        require(contractVersion in ScreenColorPickerContract.MIN_SUPPORTED_VERSION..ScreenColorPickerContract.MAX_SUPPORTED_VERSION) {
            "Unsupported Screen Color Picker contract version: $contractVersion"
        }
        val requiredHostVersion = capabilities.getLong(
            PluginCapabilityKeys.REQUIRES_HOST_VERSION,
            0L,
        )
        require(requiredHostVersion >= ScreenColorPickerContract.REQUIRED_HOST_VERSION_CODE) {
            "Screen Color Picker required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.packageName == ScreenColorPickerContract.DEFAULT_PACKAGE_NAME) {
            "Unsupported Screen Color Picker package: ${serviceInfo.packageName}"
        }
        require(serviceInfo.exported && serviceInfo.enabled) {
            "Screen Color Picker service is not exported and enabled"
        }
        require(serviceInfo.permission == ScreenColorPickerContract.PLUGIN_PERMISSION) {
            "Screen Color Picker service must require ${ScreenColorPickerContract.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context)) {
            "Screen Color Picker service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context): Set<ComponentName> {
        val intent = Intent(ScreenColorPickerContract.SERVICE_ACTION).apply {
            addCategory(ScreenColorPickerContract.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(ScreenColorPickerContract.DEFAULT_PACKAGE_NAME)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { item ->
            item.serviceInfo?.let { service -> ComponentName(service.packageName, service.name) }
        }.toSet()
    }
}
