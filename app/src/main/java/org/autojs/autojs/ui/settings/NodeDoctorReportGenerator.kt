package org.autojs.autojs.ui.settings

import android.content.Context
import android.os.Build
import org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge
import org.autojs.autojs.engine.NodeRuntimeCapabilities
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

    fun generate(context: Context, includeNativeProbe: Boolean = true): ReportFiles {
        val appContext = context.applicationContext
        val generatedAt = ZonedDateTime.now().toString()
        val probeResult = if (includeNativeProbe) {
            runCatching { NativeNodeEmbeddedRuntimeBridge.probeEmbeddedRuntime(appContext) }
                .getOrNull()
        } else {
            null
        }
        val reportJson = buildJson(appContext, generatedAt, probeResult, includeNativeProbe)
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
    ): JSONObject {
        return JSONObject().apply {
            put("schema", "autojs6-node-doctor-device-v1")
            put("generatedAt", generatedAt)
            put("privacy", privacyJson())
            put("app", appJson(context))
            put("runtime", runtimeJson(probeResult))
            put("featureFlags", featureFlagsJson())
            put("nativeLibraries", nativeLibrariesJson(context, probeResult))
            put("capabilityProfile", capabilityProfileJson())
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
        put("embeddedScriptExecutionEnabled", BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED)
        put("nodeExpectedPrefix", NodeRuntimeCapabilities.NODE_VERSION_EXPECTED_PREFIX)
        put("nodeHeaderVersion", NodeRuntimeCapabilities.NODE_HEADER_VERSION)
        put("nodeObservedVersion", probeResult?.nodeVersion ?: "unavailable")
        put("deviceSupportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        put("selectedAbi", probeResult?.currentAbi ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
        put("androidSdk", Build.VERSION.SDK_INT)
        put("device", listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "))
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
        "worker_threads" -> BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED
        "java_interop" -> BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED
        else -> NodeRuntimeCapabilities.featureFlagsById[id]?.defaultEnabled ?: false
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
        put("esmDefaultEnabled", false)
        put("nativeAddonDefaultEnabled", false)
        put("childProcessDefaultEnabled", false)
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
        val nativeLibraries = json.getJSONObject("nativeLibraries")
        val packagedTemplate = json.getJSONObject("packagedTemplate")
        val lastNodeError = json.getJSONObject("lastNodeError")
        val nativeProbe = json.getJSONObject("nativeProbe")
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
