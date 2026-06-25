package org.autojs.autojs.ui.settings

import android.content.Context
import android.os.Build
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgeProviderRegistry
import org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge
import org.autojs.autojs.engine.NodePrivacyDisclosurePolicy
import org.autojs.autojs.engine.NodeProfileRollbackPolicy
import org.autojs.autojs.engine.NodeProfileSelectionDiagnostics
import org.autojs.autojs.engine.NodeRuntimeCapabilities
import org.autojs.autojs.script.NodeProjectRuntimeCompatibilityDescriptor
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime

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
        val probeResult = if (includeNativeProbe) {
            runCatching { NativeNodeEmbeddedRuntimeBridge.probeEmbeddedRuntime(appContext) }
                .getOrNull()
        } else {
            null
        }
        val reportJson = buildJson(appContext, generatedAt, probeResult, includeNativeProbe, projectRoot)
        val reportText = buildText(appContext, generatedAt, reportJson)
        val reportDir = File(appContext.filesDir, REPORT_DIR_NAME).apply { mkdirs() }
        val textFile = File(reportDir, "node-doctor-report.txt")
        val jsonFile = File(reportDir, "node-doctor-report.json")
        textFile.writeText(reportText, Charsets.UTF_8)
        jsonFile.writeText(reportJson.toString(2) + "\n", Charsets.UTF_8)
        return ReportFiles(textFile = textFile, jsonFile = jsonFile)
    }

    private fun buildJson(
        context: Context,
        generatedAt: String,
        probeResult: NativeNodeEmbeddedRuntimeBridge.ProbeResult?,
        includeNativeProbe: Boolean,
        projectRoot: File?,
    ): JSONObject {
        return JSONObject().apply {
            put("schema", "autojs6-node-doctor-device-v1")
            put("generatedAt", generatedAt)
            put("privacy", privacyJson())
            put("app", appJson(context))
            put("runtime", runtimeJson(probeResult))
            put("compileCache", compileCacheJson(context))
            put("featureFlags", featureFlagsJson())
            put("profileSelection", profileSelectionJson(projectRoot))
            put("profileRollbackPolicy", NodeProfileRollbackPolicy.toJsonObject())
            put("privacyDisclosurePolicy", NodePrivacyDisclosurePolicy.toJsonObject())
            put("nativeLibraries", nativeLibrariesJson(context, probeResult))
            put("capabilityProfile", capabilityProfileJson())
            put("bridgeCapabilities", bridgeCapabilitiesJson())
            put("bridgeProviders", bridgeProvidersJson(context))
            put("packagedTemplate", packagedTemplateJson(context))
            put("lastNodeError", lastErrorJson())
            put(
                "nativeProbe",
                JSONObject().apply {
                    put("requested", includeNativeProbe)
                    put("status", probeResult?.let { if (it.succeeded) "succeeded" else "failed" } ?: "skipped")
                    put("errorMessage", probeResult?.errorMessage ?: JSONObject.NULL)
                    put("probeLevel", probeResult?.probeLevel ?: JSONObject.NULL)
                },
            )
        }
    }

    private fun privacyJson() = JSONObject().apply {
        put("scriptContentsIncluded", false)
        put("environmentValuesIncluded", false)
        put("pathsRedacted", true)
        put("notes", "User script contents and sensitive environment values are intentionally omitted.")
    }

    private fun appJson(context: Context) = JSONObject().apply {
        put("packageName", context.packageName)
        put("versionName", BuildConfig.VERSION_NAME)
        put("versionCode", BuildConfig.VERSION_CODE)
        put("buildType", BuildConfig.BUILD_TYPE)
        put("debug", BuildConfig.DEBUG)
    }

    private fun runtimeJson(probeResult: NativeNodeEmbeddedRuntimeBridge.ProbeResult?) = JSONObject().apply {
        put("profileName", NodeRuntimeCapabilities.PROFILE_NAME)
        put("engineVersion", NodeRuntimeCapabilities.ENGINE_VERSION)
        put("backend", BuildConfig.NODEJS_RUNTIME_BACKEND)
        put("runtimeSlot", BuildConfig.NODEJS_RUNTIME_SLOT)
        put("runtimeSlotTargetVersion", BuildConfig.NODEJS_RUNTIME_SLOT_TARGET_VERSION)
        put("runtimeSlotBuildOnly", BuildConfig.NODEJS_RUNTIME_SLOT_BUILD_ONLY)
        put("runtimeSlotUserSelectable", BuildConfig.NODEJS_RUNTIME_SLOT_USER_SELECTABLE)
        put("embeddedScriptExecutionEnabled", BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED)
        put("nodeExpectedPrefix", NodeRuntimeCapabilities.NODE_VERSION_EXPECTED_PREFIX)
        put("nodeHeaderVersion", NodeRuntimeCapabilities.NODE_HEADER_VERSION)
        put("nodeObservedVersion", probeResult?.nodeVersion ?: "unavailable")
        put("deviceSupportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        put("selectedAbi", probeResult?.currentAbi ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
        put("androidSdk", Build.VERSION.SDK_INT)
        put("device", listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "))
        put("compatibilityDescriptor", NodeProjectRuntimeCompatibilityDescriptor.currentJson())
    }

    private fun compileCacheJson(context: Context) = JSONObject().apply {
        put("schema", NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_SCHEMA_VERSION)
        put("status", if (BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED) "enabled" else "disabled")
        put("defaultEnabled", BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED)
        put("appPrivateOnly", true)
        put("userControlExposed", false)
        put("baseDir", NativeNodeEmbeddedRuntimeBridge.compileCacheBaseDir(context).absolutePath.redactPath(context))
        put("maxBytes", NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_MAX_BYTES)
        put("maxProjectDirs", NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_MAX_PROJECT_DIRS)
        put("runtimeApi", "node:module.enableCompileCache")
        put("location", "app-private-code-cache")
        put("hitMissStatus", "embedded-script diagnostics report status; custom-loader hit/miss counters are unavailable")
        put("diagnosticPrefix", "embedded_script.compile_cache")
    }

    private fun featureFlagsJson() = JSONArray().apply {
        NodeRuntimeCapabilities.featureFlags.forEach { flag ->
            put(
                JSONObject().apply {
                    put("id", flag.id)
                    put("diagnosticName", flag.diagnosticName)
                    put("status", flag.status)
                    put("securityLevel", flag.securityLevel)
                    put("effectiveEnabled", featureFlagEnabled(flag.id))
                    put("buildOnly", flag.buildOnly)
                    put("debugOnly", flag.debugOnly)
                    put("projectAllowed", flag.projectAllowed)
                    put("packagedAllowed", flag.packagedAllowed)
                    put("releaseForbidden", flag.releaseForbidden)
                },
            )
        }
    }

    private fun featureFlagEnabled(id: String): Boolean = when (id) {
        "esm" -> BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED
        "dynamic_import" -> BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED
        "network" -> BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED
        "raw_node_network_modules" -> BuildConfig.NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED
        "worker_threads" -> BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED
        "java_interop" -> BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED
        else -> NodeRuntimeCapabilities.featureFlagsById[id]?.defaultEnabled ?: false
    }

    private fun profileSelectionJson(projectRoot: File?): JSONObject {
        val diagnostics = if (projectRoot != null) {
            NodeProfileSelectionDiagnostics.fromWorkingDirectory(projectRoot.absolutePath)
        } else {
            NodeProfileSelectionDiagnostics.fromManifest(NodeBridgePermissionManifest.Manifest.empty(), "runtime_default")
        }
        return diagnostics.toJsonObject()
    }

    private fun nativeLibrariesJson(
        context: Context,
        probeResult: NativeNodeEmbeddedRuntimeBridge.ProbeResult?,
    ) = JSONObject().apply {
        put("nativeLibraryDir", probeResult?.nativeLibraryDir?.redactPath(context) ?: "unavailable")
        put("libnodeLoaded", probeResult?.libnodeLoaded ?: JSONObject.NULL)
        put("libnodeLoadError", probeResult?.libnodeLoadError ?: JSONObject.NULL)
        put("libnodeSha256", probeResult?.libnodeSha256 ?: JSONObject.NULL)
        put("bridgeLoaded", probeResult?.bridgeLoaded ?: JSONObject.NULL)
        put("bridgeLoadError", probeResult?.bridgeLoadError ?: JSONObject.NULL)
        put("bridgeSha256", probeResult?.bridgeSha256 ?: JSONObject.NULL)
        put("libcxxLoaded", probeResult?.libcxxLoaded ?: JSONObject.NULL)
        put("libcxxLoadError", probeResult?.libcxxLoadError ?: JSONObject.NULL)
        put("libnodeHandleAvailable", probeResult?.libnodeHandleAvailable ?: JSONObject.NULL)
        put("libnodeHandleError", probeResult?.libnodeHandleError ?: JSONObject.NULL)
    }

    private fun capabilityProfileJson() = JSONObject().apply {
        put("backendSwitches", JSONArray(NodeRuntimeCapabilities.backendSwitches.sorted()))
        put("routeSuffixes", JSONArray(NodeRuntimeCapabilities.nodeRouteFileSuffixes.sorted()))
        put("commonJsExtensions", JSONArray(NodeRuntimeCapabilities.commonJsModuleExtensions.sorted()))
        put("directBuiltins", JSONArray(NodeRuntimeCapabilities.directBuiltinAliases.keys.sorted()))
        put("limitedBuiltins", JSONArray(NodeRuntimeCapabilities.limitedBuiltinModuleNames.sorted()))
        put("disabledBuiltins", JSONArray(NodeRuntimeCapabilities.disabledBuiltinModuleNames.sorted()))
        put("networkBuiltins", JSONArray(NodeRuntimeCapabilities.networkBuiltinModuleNames.sorted()))
        put("disabledProcessApis", JSONArray(NodeRuntimeCapabilities.disabledProcessApis.sorted()))
        put("scopedFs", true)
        put("commonJs", true)
        put("esmDefaultEnabled", NodeRuntimeCapabilities.featureFlagsById.getValue("esm").defaultEnabled)
        put("nativeAddonDefaultEnabled", false)
        put("childProcessDefaultEnabled", false)
    }

    private fun bridgeCapabilitiesJson() = JSONObject().apply {
        put("manifestVersion", 1)
        put("projectManifestSupported", true)
        put("enforcedWhenDeclared", true)
        put(
            "declarationKeys",
            JSONArray(
                listOf(
                    "project.json:node.permissions",
                    "package.json:autojs6.node.permissions",
                ),
            ),
        )
        put("knownCapabilities", JSONArray(NodeBridgePermissionManifest.definedCapabilities))
        put("network", networkCapabilityJson())
    }

    private fun networkCapabilityJson(): JSONObject {
        val requiredAndroidPermissions = NodeBridgePermissionManifest.androidPermissionsForCapabilities(
            listOf(NodeBridgePermissionManifest.NETWORK),
        )
        return JSONObject().apply {
            put("capability", NodeBridgePermissionManifest.NETWORK)
            put("requiresDeclaration", true)
            put("providerFeatureFlag", "autojs.nodejs.network.experimental")
            put("providerEnabled", BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)
            put("requiredAndroidPermissions", JSONArray(requiredAndroidPermissions))
            put(
                "requiresAndroidInternet",
                NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET in requiredAndroidPermissions,
            )
            put("controlledModules", JSONArray(listOf("fetch", "axios", "websocket")))
            put("rawNodeNetworkModulesEnabled", BuildConfig.NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED)
        }
    }

    private fun bridgeProvidersJson(context: Context): JSONArray {
        return NodeBridgeProviderRegistry.toJsonArray(NodeBridgeProviderRegistry.androidHealth(context))
    }

    private fun packagedTemplateJson(context: Context) = JSONObject().apply {
        val assets = runCatching { context.assets.list("").orEmpty().toList() }.getOrDefault(emptyList())
        val present = TEMPLATE_APK_NAME in assets
        put("templateAsset", TEMPLATE_APK_NAME)
        put("present", present)
        put("status", if (present) "present" else "missing")
        put("note", "Device report checks asset presence only; host packaged verifier performs full APK structure checks.")
    }

    private fun lastErrorJson() = JSONObject().apply {
        put("status", "unavailable")
        put("message", "No persistent UI last-error store is available yet; check script console/logcat for the last execution failure.")
    }

    private fun buildText(context: Context, generatedAt: String, json: JSONObject): String {
        val runtime = json.getJSONObject("runtime")
        val compileCache = json.getJSONObject("compileCache")
        val profileSelection = json.getJSONObject("profileSelection")
        val profileRollbackPolicy = json.getJSONObject("profileRollbackPolicy")
        val privacyDisclosurePolicy = json.getJSONObject("privacyDisclosurePolicy")
        val nativeLibraries = json.getJSONObject("nativeLibraries")
        val packagedTemplate = json.getJSONObject("packagedTemplate")
        val lastNodeError = json.getJSONObject("lastNodeError")
        val nativeProbe = json.getJSONObject("nativeProbe")
        val disclosureSurfaces = privacyDisclosurePolicy.getJSONArray("surfaces")
        val promptRequiredSurfaces = (0 until disclosureSurfaces.length()).count { index ->
            disclosureSurfaces.getJSONObject(index).getBoolean("requiresUserPrompt")
        }
        val foregroundDisclosureSurfaces = (0 until disclosureSurfaces.length()).count { index ->
            disclosureSurfaces.getJSONObject(index).getBoolean("requiresForegroundDisclosure")
        }
        return buildString {
            appendLine("AutoJs6 Node Doctor Report")
            appendLine("Generated at: $generatedAt")
            appendLine("Schema: ${json.getString("schema")}")
            appendLine()
            appendLine("Privacy:")
            appendLine("  script contents included: false")
            appendLine("  environment values included: false")
            appendLine("  paths redacted: true")
            appendLine()
            appendLine("Runtime:")
            appendLine("  package: ${context.packageName}")
            appendLine("  app version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("  backend: ${runtime.getString("backend")}")
            appendLine("  profile: ${runtime.getString("profileName")}")
            appendLine("  engine: ${runtime.getString("engineVersion")}")
            appendLine("  expected Node: ${runtime.getString("nodeExpectedPrefix")}")
            appendLine("  observed Node: ${runtime.getString("nodeObservedVersion")}")
            appendLine("  selected ABI: ${runtime.getString("selectedAbi")}")
            appendLine("  supported ABIs: ${runtime.getJSONArray("deviceSupportedAbis").join(", ")}")
            val compatibilityDescriptor = runtime.getJSONObject("compatibilityDescriptor")
            val runtimeVersions = compatibilityDescriptor.getJSONObject("versions")
            appendLine()
            appendLine("Runtime Compatibility:")
            appendLine("  schema: ${compatibilityDescriptor.getString("schema")}")
            appendLine("  profile: ${compatibilityDescriptor.getString("nodeProfile")}")
            appendLine("  bootstrap: ${runtimeVersions.getString("bootstrap")}")
            appendLine("  commonjs loader: ${runtimeVersions.getString("commonJsLoader")}")
            appendLine("  esm loader: ${runtimeVersions.getString("esmLoader")}")
            appendLine("  dynamic import: ${runtimeVersions.getString("dynamicImport")}")
            appendLine("  bridge protocol: ${runtimeVersions.getString("bridgeProtocol")}")
            appendLine("  moduleSources schema: ${runtimeVersions.getString("moduleSourcesSchema")}")
            appendLine()
            appendLine("Profile Selection:")
            appendLine("  status: ${profileSelection.getString("status")}")
            appendLine("  effective profile: ${profileSelection.getString("effectiveProfile")}")
            appendLine("  requested profiles: ${profileSelection.getJSONArray("requestedProfiles").join(", ")}")
            appendLine("  selection mode: ${profileSelection.getString("selectionMode")}")
            appendLine("  profile promotion: ${profileSelection.getBoolean("profilePromotion")}")
            appendLine("  user selectable: ${profileSelection.getBoolean("userSelectable")}")
            appendLine("  declared node permissions: ${profileSelection.getJSONArray("declaredNodePermissions").join(", ")}")
            appendLine("  high-risk capabilities: ${profileSelection.getJSONArray("highRiskCapabilities").length()}")
            appendLine("  blocked profile promotions: ${profileSelection.getJSONArray("blockedProfilePromotions").join(", ")}")
            appendLine()
            appendLine("Profile Rollback Policy:")
            appendLine("  status: ${profileRollbackPolicy.getString("status")}")
            appendLine("  rollback target: ${profileRollbackPolicy.getString("rollbackTargetProfile")}")
            appendLine("  crash fallback: ${profileRollbackPolicy.getString("crashFallbackProfile")}")
            appendLine("  stress fallback: ${profileRollbackPolicy.getString("stressFallbackProfile")}")
            appendLine("  profile promotion: ${profileRollbackPolicy.getBoolean("profilePromotion")}")
            appendLine("  independent profile disable: ${profileRollbackPolicy.getBoolean("independentProfileDisable")}")
            appendLine("  profile switches: ${profileRollbackPolicy.getJSONObject("profileKillSwitches").keys().asSequence().joinToString(", ")}")
            appendLine("  capability switches: ${profileRollbackPolicy.getJSONObject("capabilityKillSwitches").keys().asSequence().joinToString(", ")}")
            appendLine("  packaged downgrade: ${profileRollbackPolicy.getJSONObject("packagedDowngrade").getString("strategy")}")
            appendLine()
            appendLine("Privacy Disclosure Policy:")
            appendLine("  status: ${privacyDisclosurePolicy.getString("status")}")
            appendLine("  effective profile: ${privacyDisclosurePolicy.getString("effectiveProfile")}")
            appendLine("  metadata only: ${privacyDisclosurePolicy.getBoolean("metadataOnly")}")
            appendLine("  grants authority: ${privacyDisclosurePolicy.getBoolean("grantsAuthority")}")
            appendLine("  implicit high privilege denied: ${privacyDisclosurePolicy.getBoolean("implicitHighPrivilegeDenied")}")
            appendLine("  packaged implicit high privilege denied: ${privacyDisclosurePolicy.getBoolean("packagedImplicitHighPrivilegeDenied")}")
            appendLine("  surfaces: ${disclosureSurfaces.length()}")
            appendLine("  prompt-required surfaces: $promptRequiredSurfaces")
            appendLine("  foreground-disclosure surfaces: $foregroundDisclosureSurfaces")
            appendLine("  packaged default action: ${privacyDisclosurePolicy.getJSONObject("packagedPolicy").getString("defaultAction")}")
            appendLine()
            appendLine("Compile Cache:")
            appendLine("  status: ${compileCache.getString("status")}")
            appendLine("  app-private only: ${compileCache.getBoolean("appPrivateOnly")}")
            appendLine("  user control exposed: ${compileCache.getBoolean("userControlExposed")}")
            appendLine("  base dir: ${compileCache.getString("baseDir")}")
            appendLine("  quota: ${compileCache.getLong("maxBytes")} bytes / ${compileCache.getInt("maxProjectDirs")} project dirs")
            appendLine("  hit/miss: ${compileCache.getString("hitMissStatus")}")
            appendLine()
            appendLine("Native Libraries:")
            appendLine("  native dir: ${nativeLibraries.getString("nativeLibraryDir")}")
            appendLine("  libnode loaded: ${nativeLibraries.opt("libnodeLoaded")}")
            appendLine("  libnode sha256: ${nativeLibraries.opt("libnodeSha256")}")
            appendLine("  bridge loaded: ${nativeLibraries.opt("bridgeLoaded")}")
            appendLine("  bridge sha256: ${nativeLibraries.opt("bridgeSha256")}")
            appendLine("  libc++ loaded: ${nativeLibraries.opt("libcxxLoaded")}")
            appendLine()
            appendLine("Feature Flags:")
            val flags = json.getJSONArray("featureFlags")
            for (index in 0 until flags.length()) {
                val flag = flags.getJSONObject(index)
                appendLine(
                    "  ${flag.getString("id")}: enabled=${flag.getBoolean("effectiveEnabled")} " +
                            "status=${flag.getString("status")} security=${flag.getString("securityLevel")}",
                )
            }
            appendLine()
            appendLine("Capability Profile:")
            appendLine("  CommonJS: true")
            appendLine("  scoped fs: true")
            appendLine("  direct builtins: ${json.getJSONObject("capabilityProfile").getJSONArray("directBuiltins").length()}")
            appendLine("  limited builtins: ${json.getJSONObject("capabilityProfile").getJSONArray("limitedBuiltins").length()}")
            appendLine("  disabled builtins: ${json.getJSONObject("capabilityProfile").getJSONArray("disabledBuiltins").join(", ")}")
            appendLine()
            val bridgeCapabilities = json.getJSONObject("bridgeCapabilities")
            val networkCapability = bridgeCapabilities.getJSONObject("network")
            appendLine("Bridge Capabilities:")
            appendLine("  manifest supported: ${bridgeCapabilities.getBoolean("projectManifestSupported")}")
            appendLine("  enforced when declared: ${bridgeCapabilities.getBoolean("enforcedWhenDeclared")}")
            appendLine(
                "  network capability: requires declaration=${networkCapability.getBoolean("requiresDeclaration")} " +
                        "provider enabled=${networkCapability.getBoolean("providerEnabled")}",
            )
            appendLine("  network Android permissions: ${networkCapability.getJSONArray("requiredAndroidPermissions").join(", ")}")
            appendLine("  controlled network modules: ${networkCapability.getJSONArray("controlledModules").join(", ")}")
            appendLine("  raw Node network modules enabled: ${networkCapability.getBoolean("rawNodeNetworkModulesEnabled")}")
            appendLine()
            appendLine("Bridge Providers:")
            val bridgeProviders = json.getJSONArray("bridgeProviders")
            for (index in 0 until bridgeProviders.length()) {
                val provider = bridgeProviders.getJSONObject(index)
                appendLine(
                    "  ${provider.getString("displayName")}: status=${provider.getString("status")} " +
                            "statusCode=${provider.optString("statusCode", provider.getString("status"))} " +
                            "version=${provider.getString("version")} active=${provider.getInt("activeResourceCount")}",
                )
                provider.optJSONObject("packagedSupport")?.let { packagedSupport ->
                    appendLine(
                        "    packaged: ${packagedSupport.optString("status", "unknown")} " +
                                packagedSupport.optString("notes").takeIf { it.isNotBlank() }.orEmpty(),
                    )
                }
                val details = provider.optJSONObject("details")
                if (details != null && details.length() > 0) {
                    appendLine(
                        "    details: " + details.keys().asSequence()
                            .map { key -> "$key=${details.optString(key)}" }
                            .joinToString(", "),
                    )
                }
            }
            appendLine()
            appendLine("Packaged Template:")
            appendLine("  ${packagedTemplate.getString("templateAsset")}: ${packagedTemplate.getString("status")}")
            appendLine()
            appendLine("Last Node Error:")
            appendLine("  status: ${lastNodeError.getString("status")}")
            appendLine("  message: ${lastNodeError.getString("message")}")
            appendLine()
            appendLine("Native Probe:")
            appendLine("  requested: ${nativeProbe.getBoolean("requested")}")
            appendLine("  status: ${nativeProbe.getString("status")}")
            appendLine("  level: ${nativeProbe.opt("probeLevel")}")
        }
    }

    private fun String.redactPath(context: Context): String {
        val replacements = listOf(
            context.applicationInfo.dataDir to "<app-data>",
            context.filesDir.absolutePath to "<app-files>",
            context.cacheDir.absolutePath to "<app-cache>",
        )
        return replacements.fold(this) { acc, (path, label) ->
            if (path.isNullOrBlank()) acc else acc.replace(path, label)
        }
    }

    private const val REPORT_DIR_NAME = "node-doctor"
    private const val TEMPLATE_APK_NAME = "template.apk"
}
