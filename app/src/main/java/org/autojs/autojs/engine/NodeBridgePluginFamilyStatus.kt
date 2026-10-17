package org.autojs.autojs.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.center.InstalledPluginMetadataStore
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginHost
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginStateMonitor
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Live status of the plugin families behind bridge modules (MCP roadmap P11 D27): which of their
 * methods a model-facing session can call right now, and why not otherwise. Every call queries the
 * package manager and the plugin center stores afresh; nothing here binds a plugin process, so a
 * plugin installed after the session opened is seen on the next call.
 */
internal object NodeBridgePluginFamilyStatus {

    const val SCHEMA = "autojs6-bridge-app-plugin-capabilities-v1"
    const val STATUS_AVAILABLE = "available"
    const val STATUS_NOT_INSTALLED = "not_installed"
    const val STATUS_DISABLED = "disabled"
    const val STATUS_INCOMPATIBLE = "incompatible"

    class PluginReport(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long?,
        val engine: String?,
        val variant: String?,
        val engineId: String?,
        val enabled: Boolean,
        val authorized: Boolean,
        val compatible: Boolean,
        val requiresHostVersion: Long?,
    )

    class FamilyReport(val status: String, val plugins: List<PluginReport>, val detail: String? = null)

    class Family(val id: String, val methods: List<String>, val query: (Context) -> FamilyReport)

    private fun methodsOf(module: String, methods: Collection<String>): List<String> = methods.sorted().map { "$module.$it" }

    val families: List<Family> = listOf(
        Family("ocr", methodsOf(NodeBridgeModules.OCR, NodeBridgeModules.ocrMethods) + "accessibility.readScreenText") { serviceFamily(it, OcrPluginHost.ACTION_OCR) },
        Family("barcode", methodsOf(NodeBridgeModules.BARCODE, NodeBridgeModules.barcodeMethods)) { serviceFamily(it, BarcodePluginHost.ACTION_BARCODE) },
        Family("pinyin", methodsOf(NodeBridgeModules.PINYIN, NodeBridgeModules.pinyinMethods)) { serviceFamily(it, PinyinPluginHost.ACTION_PINYIN) },
        Family("pinyin4j", methodsOf(NodeBridgeModules.PINYIN4J, NodeBridgeModules.pinyin4jMethods)) { serviceFamily(it, Pinyin4jPluginHost.ACTION_PINYIN4J) },
        Family("opencc", methodsOf(NodeBridgeModules.OPENCC, NodeBridgeModules.openccMethods)) { serviceFamily(it, OpenccPluginHost.ACTION_OPENCC) },
        Family("mediainfo", methodsOf(NodeBridgeModules.MEDIAINFO, NodeBridgeModules.mediainfoMethods)) { serviceFamily(it, MediainfoPluginHost.ACTION_MEDIAINFO) },
        Family("opencv", methodsOf(NodeBridgeModules.IMAGE, listOf(
            "resize", "scale", "grayscale", "threshold", "blur", "gaussianBlur", "medianBlur", "invert",
            "findColor", "findImage", "findMultiColors", "matchTemplate", "findAllPointsForColor",
        )), ::openCvFamily),
        Family("docs", listOf("app.listDocs", "app.readDoc"), ::docsFamily),
    )

    /** The `module.method` keys of every family that is available right now. */
    fun availableMethods(context: Context): Set<String> {
        val appContext = context.applicationContext
        return families.filter { family -> runCatching { family.query(appContext).status }.getOrNull() == STATUS_AVAILABLE }
            .flatMapTo(LinkedHashSet()) { it.methods }
    }

    fun report(context: Context): JSONObject {
        val appContext = context.applicationContext
        val families = JSONArray()
        this.families.forEach { family ->
            val report = runCatching { family.query(appContext) }
                .getOrElse { error -> FamilyReport(STATUS_NOT_INSTALLED, emptyList(), "query failed: ${error.javaClass.simpleName}") }
            families.put(JSONObject().apply {
                put("id", family.id)
                put("status", report.status)
                put("methods", JSONArray(family.methods))
                put("plugins", JSONArray().also { array -> report.plugins.forEach { array.put(it.toJson()) } })
                report.detail?.let { put("detail", it) }
            })
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("hostVersionCode", BuildConfig.VERSION_CODE.toLong())
            .put("pluginCenter", JSONObject().put("component", ComponentName(appContext, PluginCenterActivity::class.java).flattenToString()))
            .put("families", families)
    }

    private fun PluginReport.toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        versionName?.let { put("versionName", it) }
        versionCode?.let { put("versionCode", it) }
        engine?.let { put("engine", it) }
        variant?.let { put("variant", it) }
        engineId?.let { put("engineId", it) }
        put("enabled", enabled)
        put("authorized", authorized)
        put("compatible", compatible)
        requiresHostVersion?.let { put("requiresHostVersion", it) }
    }

    /** Families whose plugins answer one service action; state comes from the plugin center stores, never from binding. */
    private fun serviceFamily(context: Context, action: String): FamilyReport {
        val services = queryServices(context, action)
        if (services.isEmpty()) return FamilyReport(STATUS_NOT_INSTALLED, emptyList())
        val metadata = InstalledPluginMetadataStore.readAll(context)
        val plugins = services.map { service -> pluginReport(context, service, metadata) }
        return FamilyReport(statusOf(plugins), plugins)
    }

    private fun statusOf(plugins: List<PluginReport>): String = when {
        plugins.any { it.enabled && it.authorized && it.compatible } -> STATUS_AVAILABLE
        plugins.any { it.enabled && it.authorized } -> STATUS_INCOMPATIBLE
        else -> STATUS_DISABLED
    }

    private fun pluginReport(context: Context, service: ServiceInfo, metadata: Map<String, InstalledPluginMetadataStore.Entry>): PluginReport {
        val packageName = service.packageName
        val installed = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val versionCode = installed?.let { PackageInfoCompat.getLongVersionCode(it) }
        // Plugin Center metadata is trusted only while it still describes the installed APK.
        val entry = metadata[packageName]?.takeIf { installed != null && it.matchesInstalledPackage(versionCode, installed.lastUpdateTime) }
        return PluginReport(
            packageName = packageName,
            versionName = installed?.versionName,
            versionCode = versionCode,
            engine = entry?.engine,
            variant = entry?.variant,
            engineId = entry?.engineId,
            enabled = service.isEnabled && service.applicationInfo.enabled && PluginEnableStore.isEnabled(context, packageName, true),
            authorized = PluginTrustManager.isAuthorized(context, packageName),
            compatible = PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), entry?.requiresHostVersion),
            requiresHostVersion = entry?.requiresHostVersion,
        )
    }

    private fun openCvFamily(context: Context): FamilyReport {
        val discovered = OpenCvPluginHost.discover(context)
        if (discovered.isEmpty()) return FamilyReport(STATUS_NOT_INSTALLED, emptyList())
        val plugins = discovered.map { item ->
            val packageName = item.serviceInfo.packageName
            val candidate = item.candidate
            PluginReport(
                packageName = packageName,
                versionName = runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull(),
                versionCode = candidate?.packageVersionCode,
                engine = "opencv",
                variant = candidate?.openCvVersion,
                engineId = null,
                enabled = candidate?.enabled == true,
                authorized = PluginTrustManager.isAuthorized(context, packageName),
                compatible = item.error == null,
                requiresHostVersion = candidate?.requiresHostVersion,
            )
        }
        val status = when {
            OpenCvPluginHost.isAvailable(context) -> STATUS_AVAILABLE
            plugins.any { it.enabled && it.authorized } -> STATUS_INCOMPATIBLE
            else -> STATUS_DISABLED
        }
        return FamilyReport(status, plugins, discovered.firstNotNullOfOrNull { it.error?.code?.name })
    }

    private fun docsFamily(context: Context): FamilyReport {
        val state = OfflineDocsPluginStateMonitor.current(context)
        val status = when (state.status) {
            OfflineDocsPluginStateMonitor.Status.AVAILABLE -> STATUS_AVAILABLE
            OfflineDocsPluginStateMonitor.Status.NOT_INSTALLED -> STATUS_NOT_INSTALLED
            OfflineDocsPluginStateMonitor.Status.DISABLED -> STATUS_DISABLED
            OfflineDocsPluginStateMonitor.Status.INELIGIBLE -> STATUS_INCOMPATIBLE
            // The monitor initializes asynchronously; answer from a direct load instead of reporting "loading".
            OfflineDocsPluginStateMonitor.Status.LOADING -> if (OfflineDocsPluginHost.isAvailable(context)) STATUS_AVAILABLE else STATUS_NOT_INSTALLED
        }
        val plugins = if (status == STATUS_NOT_INSTALLED) emptyList() else listOf(
            PluginReport(
                packageName = state.packageName,
                versionName = runCatching { context.packageManager.getPackageInfo(state.packageName, 0).versionName }.getOrNull(),
                versionCode = state.packageVersionCode,
                engine = "offline-docs",
                variant = state.contentVersion,
                engineId = null,
                enabled = status != STATUS_DISABLED,
                authorized = PluginTrustManager.isAuthorized(context, state.packageName),
                compatible = status != STATUS_INCOMPATIBLE,
                requiresHostVersion = null,
            ),
        )
        return FamilyReport(status, plugins, state.errorCode?.name)
    }

    private fun queryServices(context: Context, action: String): List<ServiceInfo> {
        val intent = Intent(action)
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { it.serviceInfo }.distinctBy { it.packageName }
    }
}
