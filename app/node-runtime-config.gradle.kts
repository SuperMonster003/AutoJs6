import kotlin.text.RegexOption.IGNORE_CASE

private fun nodeJsRuntimeConfigExtraName(name: String): String = "nodeJsRuntimeConfig.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> nodeJsRuntimeConfigInput(name: String): T =
    extensions.extraProperties.get(nodeJsRuntimeConfigExtraName(name)) as T

private val globalApplicationId = nodeJsRuntimeConfigInput<String>("globalApplicationId")
private val flavorNameApp = nodeJsRuntimeConfigInput<String>("flavorNameApp")
private val flavorNameInrt = nodeJsRuntimeConfigInput<String>("flavorNameInrt")
private val buildActionAssemble = nodeJsRuntimeConfigInput<String>("buildActionAssemble")

val taskNameAssembleInrtFullNodeRelease = "assembleInrtFullNodeRelease"
val taskNameEmbeddedNodeRefreshFullInrtTemplateApk = "embeddedNodeRefreshFullInrtTemplateApk"

fun normalizeNodeJsHeaderDir(root: File): File {
    return listOf(
        root,
        root.resolve("include/node"),
        root.resolve("include"),
        root.resolve("node"),
    ).firstOrNull { it.resolve("node.h").isFile } ?: root
}

fun File.nodeJsCmakePath(): String = absolutePath.replace('\\', '/')

val taskNames: List<String> = gradle.startParameter.taskNames

fun isGradleTaskRequested(taskName: String): Boolean =
    taskNames.any { requested ->
        requested == taskName || requested == ":$flavorNameApp:$taskName" || requested.endsWith(":$taskName")
    }

val isFullNodeInrtTemplateTaskRequested =
    isGradleTaskRequested(taskNameAssembleInrtFullNodeRelease) ||
            isGradleTaskRequested(taskNameEmbeddedNodeRefreshFullInrtTemplateApk)
val nodeJsIdentityProfile = providers.gradleProperty("autojs.nodejs.identity").orElse("production").get()
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
val nodeJsApplicationIdSuffix = when (nodeJsIdentityProfile) {
    "nodejsTest" -> ".nodejs"
    else -> ""
}
val nodeJsEffectiveApplicationId = "$globalApplicationId$nodeJsApplicationIdSuffix"
val nodeJsAppLabel = when (nodeJsIdentityProfile) {
    "nodejsTest" -> "AutoJs6 Node"
    else -> "AutoJs6"
}
val isNodeFuzzerSeedReplayRequested = taskNames.any { it.contains("replayNodeFuzzerSeed", ignoreCase = true) }
val isAppAssembleTaskRequested = taskNames.any { it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble", IGNORE_CASE)) }
val isInrtAssembleTaskRequested = taskNames.any { it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble$flavorNameInrt", IGNORE_CASE)) }
val isInrtTaskRequested = taskNames.any { it.contains(flavorNameInrt, true) }
val isApkBuilderTemplatePluginRefreshRequested = providers.gradleProperty("autojs.apkBuilder.templatePlugin.refreshTemplate")
    .map { it.trim().lowercase() in setOf("true", "1", "yes", "on") }
    .orElse(false)
    .get()
val nodeJsDefaultReleaseFlavor = when {
    isFullNodeInrtTemplateTaskRequested -> "nodeFull"
    else -> "nodePlugin"
}
val nodeJsConfiguredReleaseFlavor = providers.gradleProperty("autojs.nodejs.releaseFlavor").orElse(nodeJsDefaultReleaseFlavor).get()
    .trim()
    .let { requested ->
        when (requested.lowercase()) {
            "nodefull", "node-full", "full" -> "nodeFull"
            "nodemini", "node-mini", "mini" -> "nodeMini"
            "nodeplugin", "node-plugin", "plugin" -> "nodePlugin"
            "rhinoonly", "rhino-only", "rhino" -> "rhinoOnly"
            else -> throw GradleException(
                "Unsupported Embedded Node release flavor '$requested'. " +
                        "Use nodeFull, nodeMini, nodePlugin, or rhinoOnly."
            )
        }
    }
val nodeJsReleaseFlavor = if ((isInrtTaskRequested || isApkBuilderTemplatePluginRefreshRequested) && !isFullNodeInrtTemplateTaskRequested) {
    "rhinoOnly"
} else {
    nodeJsConfiguredReleaseFlavor
}
val isNodeJsReleasePolicyEnforced = taskNames.any { requested ->
    requested.substringAfterLast(':').let { taskName ->
        taskName.contains("release", ignoreCase = true) ||
                taskName.contains("gate", ignoreCase = true)
    }
}
val nodeJsDefaultRuntimeBackend = when (nodeJsReleaseFlavor) {
    "nodePlugin" -> "plugin"
    "nodeFull", "nodeMini" -> "embedded"
    "rhinoOnly" -> "rhino"
    else -> throw GradleException("Unsupported Node.js release flavor '$nodeJsReleaseFlavor'.")
}
val nodeJsRuntimeBackend = providers.gradleProperty("autojs.nodejs.backend").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { requested ->
        when (requested.lowercase()) {
            "rhino" -> "rhino"
            "native", "v8", "node", "nodejs" -> "native"
            "embedded", "embed" -> "embedded"
            "plugin", "node-plugin", "nodejs-plugin" -> "plugin"
            "pluginoptional", "plugin-optional", "plugin_optional",
            "optional-plugin", "node-plugin-optional", "nodejs-plugin-optional" -> "pluginOptional"
            "auto" -> "auto"
            else -> throw GradleException(
                "Unsupported Node.js runtime backend '$requested'. " +
                        "Use rhino, native, embedded, plugin, pluginOptional, or auto."
            )
        }
    }
    ?: nodeJsDefaultRuntimeBackend
if (nodeJsRuntimeBackend == "pluginOptional" && nodeJsReleaseFlavor !in setOf("nodeFull", "nodeMini")) {
    throw GradleException(
        "Node.js runtime backend pluginOptional requires release flavor nodeFull or nodeMini; " +
                "current release flavor is $nodeJsReleaseFlavor."
    )
}
if (isNodeJsReleasePolicyEnforced) {
    val allowedReleaseBackends = when (nodeJsReleaseFlavor) {
        "nodePlugin" -> setOf("plugin")
        "nodeFull", "nodeMini" -> setOf("embedded", "pluginOptional")
        "rhinoOnly" -> setOf("rhino")
        else -> emptySet()
    }
    if (nodeJsRuntimeBackend !in allowedReleaseBackends) {
        throw GradleException(
            "Node.js release/gate task backend '$nodeJsRuntimeBackend' is incompatible with " +
                    "release flavor $nodeJsReleaseFlavor. Allowed backend(s): " +
                    allowedReleaseBackends.joinToString() + "."
        )
    }
}
val nodeJsReleaseSupportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val nodeJsReleaseKnownAbis = nodeJsReleaseSupportedAbis + "x86"
val nodeJsRuntimeSlot = providers.gradleProperty("autojs.nodejs.runtimeSlot").orElse("node24_5").get()
    .trim()
    .lowercase()
    .replace('-', '_')
    .let { requested ->
        when (requested) {
            "node24_5" -> "node24_5"
            "node24_current" -> "node24_current"
            else -> throw GradleException(
                "Unsupported Embedded Node runtime slot '$requested'. Use node24_5 or node24_current."
            )
        }
    }
val nodeJsRuntimeSlotTargetVersion = when (nodeJsRuntimeSlot) {
    "node24_current" -> providers.gradleProperty("autojs.nodejs.patchTargetVersion").orElse("24.17.0").get()
        .trim()
        .removePrefix("v")
    else -> "24.5.0"
}
val nodeJsPatchCandidateJniLibsDir = providers.gradleProperty("autojs.nodejs.patchCandidateJniLibs").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(::file)
val nodeJsPatchCandidateHeadersDir = providers.gradleProperty("autojs.nodejs.patchCandidateHeaders").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { normalizeNodeJsHeaderDir(file(it)) }
val nodeJsConfiguredRuntimeJniLibsDir = providers.gradleProperty("autojs.nodejs.runtimeJniLibs").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(::file)
val nodeJsPluginProjectDirOverride = providers.gradleProperty("autojs.nodejs.pluginProjectDir")
    .orElse(providers.gradleProperty("autojs.nodejs.plugin.root"))
val nodeJsPluginProjectDir = nodeJsPluginProjectDirOverride.orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(rootProject::file)
    ?: rootProject.projectDir.parentFile?.let { workspaceDir ->
        listOf(
            workspaceDir.resolve("AutoJs6-Plugin-NodeJs-Runtime"),
            workspaceDir.resolve("autojs6-plugin-nodejs"),
        ).firstOrNull(File::isDirectory)
            ?: workspaceDir.resolve("AutoJs6-Plugin-NodeJs-Runtime")
    }
val nodeJsExamplesSource = providers.gradleProperty("autojs.nodejs.examplesSource").orElse("host").get()
    .trim()
    .lowercase()
val nodeJsConfiguredExamplesRootDir = providers.gradleProperty("autojs.nodejs.examplesRoot").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(rootProject::file)
val nodeJsHostExamplesRootDir = rootProject.projectDir.resolve("sample/nodejs")
val nodeJsPluginExamplesRootDir = nodeJsPluginProjectDir?.resolve("sample/nodejs")
val nodeJsAndroidTestExamplesRootDir = nodeJsConfiguredExamplesRootDir ?: when (nodeJsExamplesSource) {
    "host", "main", "app" -> nodeJsHostExamplesRootDir
    "plugin", "node-plugin", "nodejs-plugin" -> nodeJsPluginExamplesRootDir
        ?: throw GradleException("Node.js plugin project directory is unavailable for autojs.nodejs.examplesSource=plugin.")
    else -> throw GradleException(
        "Unsupported Node example source '$nodeJsExamplesSource'. Use host or plugin, " +
                "or set -Pautojs.nodejs.examplesRoot=<dir>."
    )
}
if (!nodeJsAndroidTestExamplesRootDir.isDirectory) {
    throw GradleException(
        "Missing Node example assets directory: ${nodeJsAndroidTestExamplesRootDir.absolutePath}. " +
                "Use -Pautojs.nodejs.examplesRoot=<dir> or -Pautojs.nodejs.pluginProjectDir=<dir>."
    )
}
val nodeJsPluginCppDir = nodeJsPluginProjectDir
    ?.resolve("app/src/main/cpp")
    ?.takeIf(File::isDirectory)
val nodeJsBridgeCmakeFile = listOfNotNull(
    nodeJsPluginCppDir?.resolve("CMakeLists.txt"),
    file("src/main/cpp/CMakeLists.txt"),
).firstOrNull(File::isFile)
val nodeJsPluginRuntimeJniLibsDir = nodeJsPluginProjectDir
    ?.resolve("app/src/main/jniLibs")
    ?.takeIf(File::isDirectory)
val nodeJsDefaultRuntimeJniLibsDir = nodeJsConfiguredRuntimeJniLibsDir
    ?: nodeJsPluginRuntimeJniLibsDir
    ?: file("src/main/jniLibs")
val nodeJsRuntimeSlotJniLibsSourceDir = when (nodeJsRuntimeSlot) {
    "node24_current" -> nodeJsPatchCandidateJniLibsDir
        ?: throw GradleException(
            "autojs.nodejs.runtimeSlot=node24_current requires " +
                    "-Pautojs.nodejs.patchCandidateJniLibs=<dir>."
        )
    else -> nodeJsDefaultRuntimeJniLibsDir
}
val nodeJsRuntimeSlotHeaderDir = when (nodeJsRuntimeSlot) {
    "node24_current" -> nodeJsPatchCandidateHeadersDir
        ?: throw GradleException(
            "autojs.nodejs.runtimeSlot=node24_current requires " +
                    "-Pautojs.nodejs.patchCandidateHeaders=<dir>."
        )
    else -> nodeJsPluginCppDir
        ?.resolve("node-v24.5.0/include/node")
        ?.takeIf { it.resolve("node.h").isFile }
        ?: file("src/main/cpp/node-v24.5.0/include/node")
}
if (nodeJsRuntimeSlot == "node24_current") {
    val missingCandidateAbis = nodeJsReleaseSupportedAbis
        .filterNot { nodeJsRuntimeSlotJniLibsSourceDir.resolve("$it/libnode.so").isFile }
    if (missingCandidateAbis.isNotEmpty()) {
        throw GradleException(
            "autojs.nodejs.runtimeSlot=node24_current requires candidate libnode.so for ABI(s): " +
                    missingCandidateAbis.joinToString() +
                    ". Candidate root: ${nodeJsRuntimeSlotJniLibsSourceDir.absolutePath}"
        )
    }
    if (!nodeJsRuntimeSlotHeaderDir.resolve("node.h").isFile) {
        throw GradleException(
            "autojs.nodejs.runtimeSlot=node24_current requires Node headers containing node.h. " +
                    "Resolved header dir: ${nodeJsRuntimeSlotHeaderDir.absolutePath}"
        )
    }
}
val nodeJsReleaseRequestedAbis = providers.gradleProperty("autojs.nodejs.releaseAbis").orNull
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    .orEmpty()
if (nodeJsReleaseFlavor == "nodeFull" && nodeJsReleaseRequestedAbis.isNotEmpty() &&
    nodeJsReleaseRequestedAbis.toSet() != nodeJsReleaseSupportedAbis.toSet()
) {
    throw GradleException(
        "nodeFull must package the full Embedded Node ABI set: ${nodeJsReleaseSupportedAbis.joinToString()}. " +
                "Use -Pautojs.nodejs.releaseFlavor=nodeMini to publish a narrower ABI set."
    )
}
val nodeJsReleaseExpectedAbis = when (nodeJsReleaseFlavor) {
    "nodeFull" -> nodeJsReleaseSupportedAbis
    "nodeMini" -> nodeJsReleaseRequestedAbis.ifEmpty { listOf("arm64-v8a") }
    "nodePlugin" -> emptyList()
    else -> emptyList()
}
val nodeJsBridgeExpectedAbis = when (nodeJsReleaseFlavor) {
    "nodePlugin" -> nodeJsReleaseRequestedAbis.ifEmpty { nodeJsReleaseSupportedAbis }
    else -> nodeJsReleaseExpectedAbis
}
val nodeJsReleaseUnsupportedAbis = (nodeJsReleaseExpectedAbis + nodeJsBridgeExpectedAbis)
    .distinct()
    .filter { it !in nodeJsReleaseSupportedAbis }
if (nodeJsReleaseUnsupportedAbis.isNotEmpty()) {
    throw GradleException(
        "Unsupported Embedded Node release ABI(s) for $nodeJsReleaseFlavor: " +
                nodeJsReleaseUnsupportedAbis.joinToString() +
                ". Supported Embedded Node ABIs are ${nodeJsReleaseSupportedAbis.joinToString()}; x86 is unsupported."
    )
}
if (nodeJsRuntimeSlotJniLibsSourceDir.resolve("x86/libnode.so").isFile) {
    throw GradleException(
        "Unsupported Embedded Node x86 runtime detected at ${nodeJsRuntimeSlotJniLibsSourceDir.resolve("x86/libnode.so").absolutePath}. " +
                "x86 must remain fail-fast until libnode.so, libautojs6-node.so, and device coverage are explicitly supported."
    )
}
val effectiveNodeJsRuntimeBackend = nodeJsRuntimeBackend
val isNodeJsHostBridgeEnabled = providers.gradleProperty("autojs.nodejs.hostBridge.enabled")
    .orElse(if (nodeJsReleaseFlavor == "nodePlugin") "false" else "true")
    .get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsEmbeddedProbeLevel = providers.gradleProperty("autojs.nodejs.embedded.probe.level").orElse("0").get()
    .trim()
    .toIntOrNull()
    ?.coerceIn(0, 209)
    ?: 0
val nodeJsEmbeddedProbeTimeoutMs = providers.gradleProperty("autojs.nodejs.embedded.probe.timeout.ms").orElse("3000").get()
    .trim()
    .toLongOrNull()
    ?.coerceAtLeast(1L)
    ?: 3000L
val nodeJsEmbeddedProbeLifecycleEnabled = providers.gradleProperty("autojs.nodejs.embedded.probe.lifecycle").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsEmbeddedScriptEnabled = providers.gradleProperty("autojs.nodejs.embedded.script.enabled").orElse("true").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsEmbeddedPersistentRuntimeEnabled = providers.gradleProperty("autojs.nodejs.embedded.persistentRuntime").orElse("true").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
// Stable runtime capabilities are part of every build. Runtime permissions,
// allowlists, sandboxing, and resource budgets remain the security boundary.
val nodeJsEsmEnabled = true
val nodeJsDynamicImportEnabled = true
val nodeJsNetworkEnabled = true
val nodeJsRawNetworkModulesEnabled = true
val nodeJsWorkerThreadsEnabled = true
val nodeJsChildProcessEnabled = true
val nodeJsJavaInteropEnabled = true
val nodeJsProfileProEnabled = providers.gradleProperty("autojs.nodejs.profile.pro.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsProfileDesktopEnabled = providers.gradleProperty("autojs.nodejs.profile.desktop.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsProfileDebugUnsafeLabEnabled = providers.gradleProperty("autojs.nodejs.profile.debugUnsafeLab.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsRelaxedCapabilitiesEnabled = providers.gradleProperty("autojs.nodejs.relaxedCapabilities.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsTimelineTraceEnabled = providers.gradleProperty("autojs.nodejs.timelineTrace").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsTestDeviceProfile = providers.gradleProperty("autojs.nodejs.test.deviceProfile").orNull
    ?.trim()
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }
    ?.let { requested ->
        when (requested) {
            "sony", "emulator", "smoke", "accessibility", "screen_capture", "v1_1_alpha", "v1_1_beta", "full" -> requested
            else -> throw GradleException(
                "Unsupported Embedded Node test device profile '$requested'. " +
                        "Use sony, emulator, smoke, accessibility, screen_capture, v1_1_alpha, v1_1_beta, or full."
            )
        }
    }
val nodeJsFuzzerSeedFile = providers.gradleProperty("autojs.nodejs.fuzzerSeedFile").orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val nodeJsFuzzerSeedFileText = nodeJsFuzzerSeedFile
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }
    ?.readText()
private fun String.nodeJsSeedJsonString(name: String): String? =
    Regex(""""$name"\s*:\s*"([^"]*)"""").find(this)?.groupValues?.get(1)
private fun String.nodeJsSeedJsonLong(name: String): String? =
    Regex(""""$name"\s*:\s*([0-9]+)""").find(this)?.groupValues?.get(1)
private fun normalizeNodeJsFuzzerBudget(requested: String): String =
    requested.trim().lowercase().let { normalized ->
        when (normalized) {
            "quick", "normal", "extended" -> normalized
            else -> throw GradleException(
                "Unsupported Embedded Node fuzzer budget '$requested'. Use quick, normal, or extended."
            )
        }
    }
val nodeJsFuzzerReplayClass = nodeJsFuzzerSeedFileText?.nodeJsSeedJsonString("replayClass")
val nodeJsFuzzerReplayMethod = nodeJsFuzzerSeedFileText?.nodeJsSeedJsonString("replayMethod")
    ?.takeUnless { it == "null" || it.isBlank() }
val nodeJsFuzzerReplayBudget = nodeJsFuzzerSeedFileText?.nodeJsSeedJsonString("budget")
val nodeJsFuzzerReplaySeed = nodeJsFuzzerSeedFileText?.nodeJsSeedJsonLong("baseSeed")
val nodeJsFuzzerDefaultBudget = when {
    taskNames.any { it.contains("verifyNodeNightlyGate", ignoreCase = true) || it.contains("verifyNodeReleaseGate", ignoreCase = true) } -> "extended"
    nodeJsTestDeviceProfile == "full" -> "extended"
    nodeJsTestDeviceProfile in setOf("emulator", "smoke") -> "quick"
    else -> "normal"
}
val nodeJsFuzzerBudget = normalizeNodeJsFuzzerBudget(
    providers.gradleProperty("autojs.nodejs.fuzzerBudget").orNull
        ?: nodeJsFuzzerReplayBudget
        ?: nodeJsFuzzerDefaultBudget
)
val nodeJsFuzzerReplayRunnerClass = nodeJsFuzzerReplayClass?.let { replayClass ->
    nodeJsFuzzerReplayMethod?.let { "$replayClass#$it" } ?: replayClass
}
extra.apply {
    set("nodeJsInstrumentation.taskNames", taskNames)
    set("nodeJsInstrumentation.nodeJsNetworkEnabled", nodeJsNetworkEnabled)
    set("nodeJsInstrumentation.nodeJsWorkerThreadsEnabled", nodeJsWorkerThreadsEnabled)
    set("nodeJsInstrumentation.nodeJsTestDeviceProfile", nodeJsTestDeviceProfile)
}

apply(from = "node-instrumentation-gates.gradle.kts")

@Suppress("UNCHECKED_CAST")
fun <T> nodeJsInstrumentationExtra(name: String): T =
    extensions.extraProperties.get("nodeJsInstrumentation.$name") as T

val nodeJsRequestedFocusedGate = nodeJsInstrumentationExtra<String?>("requestedFocusedGate")
val nodeJsFocusedGateInstrumentationClassArgument = nodeJsInstrumentationExtra<String?>("focusedGateInstrumentationClassArgument")
val nodeJsTestDeviceProfileClasses = nodeJsInstrumentationExtra<List<String>>("testDeviceProfileClasses")
val leakCanaryEnabled = providers.gradleProperty("autojs.leakcanary.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val isNodeJsNativeRuntimeRequested = effectiveNodeJsRuntimeBackend in setOf("native", "embedded", "pluginOptional", "auto")
val isNodeJsHostBridgeBuildRequested = isNodeJsNativeRuntimeRequested && isNodeJsHostBridgeEnabled
val isNodeJsRuntimePackagedInHost = isNodeJsNativeRuntimeRequested && nodeJsReleaseFlavor != "nodePlugin"
val nodeJsAvailableNativeRuntimeAbis = nodeJsReleaseKnownAbis
    .filter { nodeJsRuntimeSlotJniLibsSourceDir.resolve("$it/libnode.so").isFile }
val nodeJsBridgeNativeRuntimeAbis = if (isNodeJsHostBridgeBuildRequested) {
    nodeJsBridgeExpectedAbis.filter { it in nodeJsAvailableNativeRuntimeAbis }
} else {
    emptyList()
}
val nodeJsNativeRuntimeAbis = if (isNodeJsRuntimePackagedInHost) {
    nodeJsReleaseExpectedAbis.filter { it in nodeJsAvailableNativeRuntimeAbis }
} else {
    emptyList()
}
val nodeJsMissingBridgeAbis = if (isNodeJsHostBridgeBuildRequested) {
    nodeJsBridgeExpectedAbis.filterNot { it in nodeJsAvailableNativeRuntimeAbis }
} else {
    emptyList()
}
if (nodeJsMissingBridgeAbis.isNotEmpty()) {
    throw GradleException(
        "Embedded Node $nodeJsReleaseFlavor requires bridge build ABI(s) not present in ${nodeJsRuntimeSlotJniLibsSourceDir.absolutePath}: " +
                nodeJsMissingBridgeAbis.joinToString() +
                ". Adjust -Pautojs.nodejs.releaseAbis or provide libnode.so for link-time validation."
    )
}
val nodeJsMissingReleaseAbis = if (isNodeJsRuntimePackagedInHost) {
    nodeJsReleaseExpectedAbis.filterNot { it in nodeJsAvailableNativeRuntimeAbis }
} else {
    emptyList()
}
if (nodeJsMissingReleaseAbis.isNotEmpty()) {
    throw GradleException(
        "Embedded Node $nodeJsReleaseFlavor requires native runtime ABI(s) not present in ${nodeJsRuntimeSlotJniLibsSourceDir.absolutePath}: " +
                nodeJsMissingReleaseAbis.joinToString() +
                ". Adjust -Pautojs.nodejs.releaseAbis or provide libnode.so for the selected ABI set."
    )
}
if (isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty() && nodeJsBridgeCmakeFile == null) {
    throw GradleException(
        "Embedded Node host bridge build requires Node bridge CMake sources. " +
                "Set -Pautojs.nodejs.pluginProjectDir=<autojs6-plugin-nodejs dir> or restore app/src/main/cpp."
    )
}
val nodeJsRuntimeSlotGeneratedJniLibsDir = layout.buildDirectory
    .dir("generated/nodejs/runtime-slot/$nodeJsRuntimeSlot/jniLibs")
val prepareNodeJsRuntimeSlotJniLibs = tasks.register(
    "prepareNodeJsRuntimeSlotJniLibs",
    org.gradle.api.tasks.Copy::class,
) {
    group = "build"
    description = "Prepares build-only Embedded Node native runtime slot jniLibs."

    val outputDirProvider = nodeJsRuntimeSlotGeneratedJniLibsDir
    from(file("src/main/jniLibs")) {
        exclude("*/libnode.so")
    }
    if (isNodeJsRuntimePackagedInHost) {
        from(nodeJsRuntimeSlotJniLibsSourceDir) {
            include("*/libnode.so")
        }
    }
    into(outputDirProvider)
    outputs.dir(outputDirProvider)
    doFirst {
        delete(outputDirProvider.get().asFile)
    }
}
tasks.matching { task ->
    task.name != "prepareNodeJsRuntimeSlotJniLibs" && (
            task.name == "preBuild" ||
                    task.name.contains("JniLib", ignoreCase = true) ||
                    task.name.contains("NativeLib", ignoreCase = true)
            )
}.configureEach {
    dependsOn(prepareNodeJsRuntimeSlotJniLibs)
}


listOf(
    "taskNameAssembleInrtFullNodeRelease" to taskNameAssembleInrtFullNodeRelease,
    "taskNameEmbeddedNodeRefreshFullInrtTemplateApk" to taskNameEmbeddedNodeRefreshFullInrtTemplateApk,
    "isNodeFuzzerSeedReplayRequested" to isNodeFuzzerSeedReplayRequested,
    "isAppAssembleTaskRequested" to isAppAssembleTaskRequested,
    "isInrtAssembleTaskRequested" to isInrtAssembleTaskRequested,
    "isInrtTaskRequested" to isInrtTaskRequested,
    "nodeJsIdentityProfile" to nodeJsIdentityProfile,
    "nodeJsApplicationIdSuffix" to nodeJsApplicationIdSuffix,
    "nodeJsEffectiveApplicationId" to nodeJsEffectiveApplicationId,
    "nodeJsAppLabel" to nodeJsAppLabel,
    "nodeJsReleaseFlavor" to nodeJsReleaseFlavor,
    "nodeJsReleaseExpectedAbis" to nodeJsReleaseExpectedAbis,
    "nodeJsDefaultRuntimeBackend" to nodeJsDefaultRuntimeBackend,
    "nodeJsRuntimeBackend" to nodeJsRuntimeBackend,
    "effectiveNodeJsRuntimeBackend" to effectiveNodeJsRuntimeBackend,
    "isNodeJsReleasePolicyEnforced" to isNodeJsReleasePolicyEnforced,
    "isNodeJsNativeRuntimeRequested" to isNodeJsNativeRuntimeRequested,
    "isNodeJsHostBridgeBuildRequested" to isNodeJsHostBridgeBuildRequested,
    "nodeJsBridgeNativeRuntimeAbis" to nodeJsBridgeNativeRuntimeAbis,
    "nodeJsRuntimeSlot" to nodeJsRuntimeSlot,
    "nodeJsRuntimeSlotTargetVersion" to nodeJsRuntimeSlotTargetVersion,
    "nodeJsRuntimeSlotGeneratedJniLibsDir" to nodeJsRuntimeSlotGeneratedJniLibsDir,
    "nodeJsRuntimeSlotHeaderDir" to nodeJsRuntimeSlotHeaderDir,
    "nodeJsRuntimeSlotJniLibsSourceDir" to nodeJsRuntimeSlotJniLibsSourceDir,
    "nodeJsPluginProjectDir" to nodeJsPluginProjectDir,
    "nodeJsPluginCppDir" to nodeJsPluginCppDir,
    "nodeJsPluginRuntimeJniLibsDir" to nodeJsPluginRuntimeJniLibsDir,
    "nodeJsAndroidTestExamplesRootDir" to nodeJsAndroidTestExamplesRootDir,
    "nodeJsEmbeddedProbeLevel" to nodeJsEmbeddedProbeLevel,
    "nodeJsEmbeddedProbeTimeoutMs" to nodeJsEmbeddedProbeTimeoutMs,
    "nodeJsEmbeddedProbeLifecycleEnabled" to nodeJsEmbeddedProbeLifecycleEnabled,
    "nodeJsEmbeddedScriptEnabled" to nodeJsEmbeddedScriptEnabled,
    "nodeJsEmbeddedPersistentRuntimeEnabled" to nodeJsEmbeddedPersistentRuntimeEnabled,
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
    "nodeJsTimelineTraceEnabled" to nodeJsTimelineTraceEnabled,
    "nodeJsTestDeviceProfile" to nodeJsTestDeviceProfile,
    "nodeJsFuzzerSeedFile" to nodeJsFuzzerSeedFile,
    "nodeJsFuzzerBudget" to nodeJsFuzzerBudget,
    "nodeJsFuzzerReplayRunnerClass" to nodeJsFuzzerReplayRunnerClass,
    "nodeJsFuzzerReplaySeed" to nodeJsFuzzerReplaySeed,
    "nodeJsBridgeCmakeFile" to nodeJsBridgeCmakeFile,
    "nodeJsRequestedFocusedGate" to nodeJsRequestedFocusedGate,
    "nodeJsFocusedGateInstrumentationClassArgument" to nodeJsFocusedGateInstrumentationClassArgument,
    "nodeJsTestDeviceProfileClasses" to nodeJsTestDeviceProfileClasses,
    "leakCanaryEnabled" to leakCanaryEnabled,
).forEach { (name, value) ->
    extensions.extraProperties.set(nodeJsRuntimeConfigExtraName(name), value)
}
