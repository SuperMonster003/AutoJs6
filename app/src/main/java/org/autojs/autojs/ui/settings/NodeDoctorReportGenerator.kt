package org.autojs.autojs.ui.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgeProviderRegistry
import org.autojs.autojs.engine.NodeJsRuntimePluginBridge
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.autojs.engine.NodePrivacyDisclosurePolicy
import org.autojs.autojs.engine.NodeProfileSelectionDiagnostics
import org.autojs.autojs.engine.NodeRuntimeCapabilities
import org.autojs.autojs.script.NodeProjectRuntimeCompatibilityDescriptor
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime

/**
 * Produces a host-side diagnostic report for the external Node.js runtime plugin.
 *
 * The historical [includeNativeProbe] parameter is retained for source compatibility. It now
 * controls the plugin probe; the host no longer loads or probes libnode itself.
 */
object NodeDoctorReportGenerator {

    data class ReportFiles(
        val textFile: File,
        val jsonFile: File,
    )

    fun generate(
        context: Context,
        includeNativeProbe: Boolean = true,
        projectRoot: File? = null,
    ): ReportFiles {
        val appContext = context.applicationContext
        val generatedAt = ZonedDateTime.now().toString()
        val reportJson = buildJson(appContext, generatedAt, includeNativeProbe, projectRoot)
        val reportDir = File(appContext.filesDir, REPORT_DIR_NAME).apply { mkdirs() }
        val textFile = File(reportDir, "node-doctor-report.txt")
        val jsonFile = File(reportDir, "node-doctor-report.json")
        textFile.writeText(buildText(reportJson), Charsets.UTF_8)
        jsonFile.writeText(reportJson.toString(2) + "\n", Charsets.UTF_8)
        return ReportFiles(textFile = textFile, jsonFile = jsonFile)
    }

    private fun buildJson(
        context: Context,
        generatedAt: String,
        includeRuntimeProbe: Boolean,
        projectRoot: File?,
    ) = JSONObject().apply {
        put("schema", "autojs6-node-doctor-device-v4")
        put("generatedAt", generatedAt)
        put("privacy", privacyJson())
        put("app", appJson(context))
        put("runtime", runtimeJson())
        put("runtimePlugin", runtimePluginJson(context, includeRuntimeProbe))
        put("featureFlags", featureFlagsJson())
        put("profileSelection", profileSelectionJson(projectRoot))
        put("privacyDisclosurePolicy", NodePrivacyDisclosurePolicy.toJsonObject())
        put("capabilityProfile", capabilityProfileJson())
        put("bridgeCapabilities", bridgeCapabilitiesJson())
        put("bridgeProviders", NodeBridgeProviderRegistry.toJsonArray(NodeBridgeProviderRegistry.androidHealth(context)))
        put("packagedTemplate", packagedTemplateJson(context))
        put("lastNodeError", lastErrorJson())
    }

    private fun privacyJson() = JSONObject().apply {
        put("scriptContentsIncluded", false)
        put("environmentValuesIncluded", false)
        put("pathsIncluded", false)
        put("notes", "User script contents, sensitive environment values, and local paths are omitted.")
    }

    private fun appJson(context: Context) = JSONObject().apply {
        put("packageName", context.packageName)
        put("versionName", BuildConfig.VERSION_NAME)
        put("versionCode", BuildConfig.VERSION_CODE)
        put("buildType", BuildConfig.BUILD_TYPE)
        put("debug", BuildConfig.DEBUG)
    }

    private fun runtimeJson() = JSONObject().apply {
        put("owner", "external-plugin")
        put("hostEmbeddedRuntimePresent", false)
        put("backend", "plugin")
        put("runtimeDefaultsOwner", "external-plugin")
        put("deviceSupportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        put("androidSdk", Build.VERSION.SDK_INT)
        put("device", listOf(Build.MANUFACTURER, Build.MODEL).filter(String::isNotBlank).joinToString(" "))
        put("compatibilityDescriptor", NodeProjectRuntimeCompatibilityDescriptor.currentJson())
    }

    private fun runtimePluginJson(context: Context, includeProbe: Boolean) = JSONObject().apply {
        put("requested", NodeJsRuntimePluginBridge.isRequested())
        put("required", NodeJsRuntimePluginBridge.isRequired())
        put("executionOwner", true)

        val discovery = runCatching { runBlocking { NodeJsRuntimePluginHost.discover(context) } }
        val candidates = discovery.getOrNull().orEmpty()
        put("discoverStatus", if (discovery.isSuccess) "succeeded" else "failed")
        put("candidateCount", candidates.size)
        put("candidates", JSONArray().apply {
            candidates.forEach { candidate ->
                put(JSONObject().apply {
                    put("packageName", candidate.serviceInfo.packageName)
                    put("serviceName", candidate.serviceInfo.name)
                    put("packageVersionCode", candidate.packageVersionCode ?: JSONObject.NULL)
                    put("exported", candidate.serviceInfo.exported)
                    put("permission", candidate.serviceInfo.permission ?: JSONObject.NULL)
                    put("authorizationState", candidate.authorizationState)
                })
            }
        })
        discovery.exceptionOrNull()?.let { put("discoverError", it.message ?: it.javaClass.name) }

        put("probeRequested", includeProbe)
        if (!includeProbe) {
            put("probeStatus", "skipped")
            put("probeError", JSONObject.NULL)
            put("runtimeInfo", JSONObject.NULL)
            return@apply
        }
        val probe = runCatching { runBlocking { NodeJsRuntimePluginHost.probe(context) } }
        put("probeStatus", if (probe.isSuccess) "succeeded" else "failed")
        put("probeError", probe.exceptionOrNull()?.let { it.message ?: it.javaClass.name } ?: JSONObject.NULL)
        put("runtimeInfo", probe.getOrNull()?.let(::runtimePluginInfoJson) ?: JSONObject.NULL)
    }

    private fun runtimePluginInfoJson(runtimeInfo: Bundle) = JSONObject().apply {
        val capabilities = runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty()
        put("contractVersion", runtimeInfo.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, 0))
        put("runtimeSlot", runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT).orEmpty())
        put("nodeVersion", runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION).orEmpty())
        put("nativeLibraryName", runtimeInfo.getString(NodeJsRuntimeContract.KEY_NATIVE_LIBRARY_NAME).orEmpty())
        put("bridgeLibraryName", runtimeInfo.getString(NodeJsRuntimeContract.KEY_BRIDGE_LIBRARY_NAME).orEmpty())
        put("capabilities", JSONArray(capabilities.toList()))
        put("capabilityCount", capabilities.size)
        put("supportsHostBroker", NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER in capabilities)
        put("supportsLiveBridge", NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE in capabilities)
    }

    private fun featureFlagsJson() = JSONObject().apply {
        put("owner", "external-plugin")
        put("hostOverridesSupported", false)
        put("hostDefaultCatalogPresent", false)
        put("note", "Runtime feature values and defaults are reported by the installed plugin.")
    }

    private fun profileSelectionJson(projectRoot: File?): JSONObject {
        return if (projectRoot != null) {
            NodeProfileSelectionDiagnostics.fromWorkingDirectory(projectRoot.absolutePath).toJsonObject()
        } else {
            NodeProfileSelectionDiagnostics.fromManifest(
                NodeBridgePermissionManifest.Manifest.empty(),
                "runtime_default",
            ).toJsonObject()
        }
    }

    private fun capabilityProfileJson() = JSONObject().apply {
        put("owner", "host-request-and-bridge-policy")
        put("routeSuffixes", JSONArray(NodeRuntimeCapabilities.nodeRouteFileSuffixes.sorted()))
        put("scopedFs", true)
        put("commonJs", true)
    }

    private fun bridgeCapabilitiesJson() = JSONObject().apply {
        val networkPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
            listOf(NodeBridgePermissionManifest.NETWORK),
        )
        put("owner", "host")
        put("manifestVersion", 1)
        put("projectManifestSupported", true)
        put("enforcedWhenDeclared", true)
        put("knownCapabilities", JSONArray(NodeBridgePermissionManifest.definedCapabilities))
        put("network", JSONObject().apply {
            put("capability", NodeBridgePermissionManifest.NETWORK)
            put("requiresDeclaration", true)
            put("providerEnabled", true)
            put("requiredAndroidPermissions", JSONArray(networkPermissions))
            put("controlledModules", JSONArray(listOf("fetch", "axios", "websocket")))
            put("rawNodeNetworkModulesEnabled", true)
        })
    }

    private fun packagedTemplateJson(context: Context) = JSONObject().apply {
        val present = TEMPLATE_APK_NAME in runCatching {
            context.assets.list("").orEmpty().toList()
        }.getOrDefault(emptyList())
        put("templateAsset", TEMPLATE_APK_NAME)
        put("present", present)
        put("status", if (present) "present" else "missing")
    }

    private fun lastErrorJson() = JSONObject().apply {
        put("status", "unavailable")
        put("message", "Check the script console or logcat for the last plugin execution failure.")
    }

    private fun buildText(json: JSONObject): String {
        val app = json.getJSONObject("app")
        val runtime = json.getJSONObject("runtime")
        val plugin = json.getJSONObject("runtimePlugin")
        val profile = json.getJSONObject("profileSelection")
        return buildString {
            appendLine("AutoJs6 Node Doctor Report")
            appendLine("Generated at: ${json.getString("generatedAt")}")
            appendLine("Schema: ${json.getString("schema")}")
            appendLine()
            appendLine("Ownership:")
            appendLine("  runtime owner: ${runtime.getString("owner")}")
            appendLine("  host embedded runtime present: ${runtime.getBoolean("hostEmbeddedRuntimePresent")}")
            appendLine("  host package: ${app.getString("packageName")}")
            appendLine("  host version: ${app.getString("versionName")} (${app.getInt("versionCode")})")
            appendLine("  backend: ${runtime.getString("backend")}")
            appendLine()
            appendLine("Runtime Plugin:")
            appendLine("  requested: ${plugin.getBoolean("requested")}")
            appendLine("  required: ${plugin.getBoolean("required")}")
            appendLine("  discover: ${plugin.getString("discoverStatus")} candidates=${plugin.getInt("candidateCount")}")
            appendLine("  probe: ${plugin.getString("probeStatus")}")
            plugin.optString("probeError").takeIf { it.isNotBlank() && it != "null" }?.let {
                appendLine("  probe error: $it")
            }
            plugin.optJSONObject("runtimeInfo")?.let { info ->
                appendLine("  contract: ${info.getInt("contractVersion")}")
                appendLine("  slot: ${info.getString("runtimeSlot")}")
                appendLine("  node: ${info.getString("nodeVersion")}")
                appendLine("  capabilities: ${info.getInt("capabilityCount")}")
                appendLine("  host broker: ${info.getBoolean("supportsHostBroker")}")
                appendLine("  live bridge: ${info.getBoolean("supportsLiveBridge")}")
            }
            appendLine()
            appendLine("Host Policy:")
            appendLine("  profile: ${runtime.getString("profileName")}")
            appendLine("  profile selection: ${profile.getString("status")}")
            appendLine("  effective profile: ${profile.getString("effectiveProfile")}")
            appendLine("  declared capabilities: ${profile.getJSONArray("declaredNodePermissions").join(", ")}")
            appendLine("  bridge capability count: ${json.getJSONObject("bridgeCapabilities").getJSONArray("knownCapabilities").length()}")
            appendLine("  bridge provider count: ${json.getJSONArray("bridgeProviders").length()}")
            appendLine()
            appendLine("Privacy:")
            appendLine("  script contents included: false")
            appendLine("  environment values included: false")
            appendLine("  local paths included: false")
        }
    }

    private const val REPORT_DIR_NAME = "node-doctor"
    private const val TEMPLATE_APK_NAME = "template.apk"
}
