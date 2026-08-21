import kotlin.text.RegexOption.IGNORE_CASE

private fun nodeJsRuntimeConfigExtraName(name: String): String = "nodeJsRuntimeConfig.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> nodeJsRuntimeConfigInput(name: String): T =
    extensions.extraProperties.get(nodeJsRuntimeConfigExtraName(name)) as T

private val globalApplicationId = nodeJsRuntimeConfigInput<String>("globalApplicationId")
private val flavorNameApp = nodeJsRuntimeConfigInput<String>("flavorNameApp")
private val flavorNameInrt = nodeJsRuntimeConfigInput<String>("flavorNameInrt")
private val buildActionAssemble = nodeJsRuntimeConfigInput<String>("buildActionAssemble")
private val taskNames = gradle.startParameter.taskNames

private val isAppAssembleTaskRequested = taskNames.any {
    it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble", IGNORE_CASE))
}
private val isInrtAssembleTaskRequested = taskNames.any {
    it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble$flavorNameInrt", IGNORE_CASE))
}
private val isInrtTaskRequested = taskNames.any { it.contains(flavorNameInrt, ignoreCase = true) }
private val isApkBuilderTemplateRefreshRequested =
    providers.gradleProperty("autojs.apkBuilder.templatePlugin.refreshTemplate")
        .map { it.trim().lowercase() in setOf("true", "1", "yes", "on") }
        .orElse(false)
        .get()

private val nodeJsIdentityProfile = providers.gradleProperty("autojs.nodejs.identity")
    .orElse("production")
    .get()
    .trim()
    .lowercase()
    .let { requested ->
        when (requested) {
            "production", "prod", "release" -> "production"
            "nodejstest", "nodejs-test", "nodejs_test", "test" -> "nodejsTest"
            else -> throw GradleException(
                "Unsupported Node.js identity profile '$requested'. Use production or nodejsTest."
            )
        }
    }
private val nodeJsApplicationIdSuffix = if (nodeJsIdentityProfile == "nodejsTest") ".nodejs" else ""
private val nodeJsEffectiveApplicationId = "$globalApplicationId$nodeJsApplicationIdSuffix"
private val nodeJsAppLabel = if (nodeJsIdentityProfile == "nodejsTest") "AutoJs6 Node" else "AutoJs6"

private val configuredNodeJsReleaseFlavor =
    providers.gradleProperty("autojs.nodejs.releaseFlavor")
        .orElse("nodePlugin")
        .get()
        .trim()
        .let { requested ->
            when (requested.lowercase()) {
                "nodeplugin", "node-plugin", "plugin" -> "nodePlugin"
                "rhinoonly", "rhino-only", "rhino" -> "rhinoOnly"
                "nodefull", "node-full", "full", "nodemini", "node-mini", "mini" ->
                    throw GradleException(
                        "Node.js release flavor '$requested' was retired: the host no longer embeds libnode. " +
                                "Use nodePlugin for the external runtime plugin or rhinoOnly for a disabled Node build."
                    )
                else -> throw GradleException(
                    "Unsupported Node.js release flavor '$requested'. Use nodePlugin or rhinoOnly."
                )
            }
        }
private val nodeJsReleaseFlavor =
    if (isInrtTaskRequested || isApkBuilderTemplateRefreshRequested) "rhinoOnly"
    else configuredNodeJsReleaseFlavor

private val configuredNodeJsRuntimeBackend =
    providers.gradleProperty("autojs.nodejs.backend")
        .orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { requested ->
            when (requested.lowercase()) {
                "plugin", "node-plugin", "nodejs-plugin" -> "plugin"
                "rhino" -> "rhino"
                "native", "v8", "node", "nodejs", "embedded", "embed", "auto",
                "pluginoptional", "plugin-optional", "plugin_optional",
                "optional-plugin", "node-plugin-optional", "nodejs-plugin-optional" -> {
                    logger.warn(
                        "Node.js backend '$requested' is retired; resolving it to the external runtime plugin."
                    )
                    "plugin"
                }
                else -> throw GradleException(
                    "Unsupported Node.js runtime backend '$requested'. Use plugin or rhino."
                )
            }
        }
        ?: "plugin"
private val effectiveNodeJsRuntimeBackend =
    if (nodeJsReleaseFlavor == "rhinoOnly") "rhino" else configuredNodeJsRuntimeBackend

private val nodeJsAndroidTestExamplesRootDir =
    providers.gradleProperty("autojs.nodejs.examplesRoot")
        .orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(rootProject::file)
        ?: rootProject.projectDir.resolve("sample/nodejs")
if (!nodeJsAndroidTestExamplesRootDir.isDirectory) {
    throw GradleException(
        "Missing Node example assets directory: ${nodeJsAndroidTestExamplesRootDir.absolutePath}. " +
                "Use -Pautojs.nodejs.examplesRoot=<dir>."
    )
}

// These fields describe host request/bridge policy. Runtime implementation remains plugin-owned.
private val nodeJsEsmEnabled = true
private val nodeJsDynamicImportEnabled = true
private val nodeJsNetworkEnabled = true
private val nodeJsRawNetworkModulesEnabled = true
private val nodeJsWorkerThreadsEnabled = true
private val nodeJsChildProcessEnabled = true
private val nodeJsJavaInteropEnabled = true
private fun booleanNodeProperty(name: String, defaultValue: Boolean = false): Boolean =
    providers.gradleProperty(name)
        .orElse(defaultValue.toString())
        .get()
        .trim()
        .lowercase() in setOf("true", "1", "yes", "on")

private val nodeJsProfileProEnabled = booleanNodeProperty("autojs.nodejs.profile.pro.enabled")
private val nodeJsProfileDesktopEnabled = booleanNodeProperty("autojs.nodejs.profile.desktop.enabled")
private val nodeJsProfileDebugUnsafeLabEnabled =
    booleanNodeProperty("autojs.nodejs.profile.debugUnsafeLab.enabled")
private val nodeJsRelaxedCapabilitiesEnabled =
    booleanNodeProperty("autojs.nodejs.relaxedCapabilities.enabled")
private val nodeJsTestDeviceProfile =
    providers.gradleProperty("autojs.nodejs.test.deviceProfile")
        .orNull
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
        ?.also { requested ->
            if (requested !in setOf("accessibility", "screen_capture")) {
                throw GradleException(
                    "Unsupported Node.js test device profile '$requested'. " +
                            "Use accessibility or screen_capture, or pass an instrumentation class directly."
                )
            }
        }

listOf(
    "isAppAssembleTaskRequested" to isAppAssembleTaskRequested,
    "isInrtAssembleTaskRequested" to isInrtAssembleTaskRequested,
    "nodeJsIdentityProfile" to nodeJsIdentityProfile,
    "nodeJsApplicationIdSuffix" to nodeJsApplicationIdSuffix,
    "nodeJsEffectiveApplicationId" to nodeJsEffectiveApplicationId,
    "nodeJsAppLabel" to nodeJsAppLabel,
    "nodeJsReleaseFlavor" to nodeJsReleaseFlavor,
    "effectiveNodeJsRuntimeBackend" to effectiveNodeJsRuntimeBackend,
    "nodeJsAndroidTestExamplesRootDir" to nodeJsAndroidTestExamplesRootDir,
    "nodeJsEsmEnabled" to nodeJsEsmEnabled,
    "nodeJsDynamicImportEnabled" to nodeJsDynamicImportEnabled,
    "nodeJsNetworkEnabled" to nodeJsNetworkEnabled,
    "nodeJsRawNetworkModulesEnabled" to nodeJsRawNetworkModulesEnabled,
    "nodeJsWorkerThreadsEnabled" to nodeJsWorkerThreadsEnabled,
    "nodeJsChildProcessEnabled" to nodeJsChildProcessEnabled,
    "nodeJsJavaInteropEnabled" to nodeJsJavaInteropEnabled,
    "nodeJsProfileProEnabled" to nodeJsProfileProEnabled,
    "nodeJsProfileDesktopEnabled" to nodeJsProfileDesktopEnabled,
    "nodeJsProfileDebugUnsafeLabEnabled" to nodeJsProfileDebugUnsafeLabEnabled,
    "nodeJsRelaxedCapabilitiesEnabled" to nodeJsRelaxedCapabilitiesEnabled,
    "nodeJsTestDeviceProfile" to nodeJsTestDeviceProfile,
).forEach { (name, value) ->
    extensions.extraProperties.set(nodeJsRuntimeConfigExtraName(name), value)
}
