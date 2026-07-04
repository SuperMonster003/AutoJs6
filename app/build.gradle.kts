@file:Suppress("SpellCheckingInspection")

import com.android.build.api.variant.FilterConfiguration
import groovy.json.JsonOutput
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.RegexOption.IGNORE_CASE

plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("idea")
}

idea {
    module {
        excludeDirs.addAll(
            listOf(
                file("$rootDir/app/src/main/java/com/stardust/"),
                file("$rootDir/app/src/main/assets/modules/obsolete/"),
                file("$rootDir/app/src/main/assets-app/js-beautify/"),
            )
        )
    }
}

val globalApplicationId = "org.autojs.autojs6"

val flavorDimension = "channel"
val flavorNameApp = "app"
val flavorNameInrt = "inrt"
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val buildActionAssemble = "assemble"
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
val nodeJsRuntimeBackend = providers.gradleProperty("autojs.nodejs.backend").orElse("embedded").get()
    .trim()
    .lowercase()
    .let { if (it in setOf("rhino", "native", "embedded", "auto")) it else "embedded" }
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
val nodeJsReleaseFlavor = if (isInrtTaskRequested && !isFullNodeInrtTemplateTaskRequested) {
    "rhinoOnly"
} else {
    nodeJsConfiguredReleaseFlavor
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
    ?: rootProject.projectDir.parentFile?.resolve("autojs6-plugin-nodejs")
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
val effectiveNodeJsRuntimeBackend = if (nodeJsReleaseFlavor == "rhinoOnly") "rhino" else nodeJsRuntimeBackend
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
val nodeJsEsmExperimentalEnabled = providers.gradleProperty("autojs.nodejs.esm.experimental").orElse("true").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsDynamicImportLocalEnabled = providers.gradleProperty("autojs.nodejs.dynamicImport.local").orElse("true").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsDynamicImportExperimentalEnabled = providers.gradleProperty("autojs.nodejs.dynamicImport.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsDynamicImportEnabled = nodeJsDynamicImportLocalEnabled || nodeJsDynamicImportExperimentalEnabled
val nodeJsNetworkExperimentalEnabled = providers.gradleProperty("autojs.nodejs.network.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsRawNetworkModulesExperimentalEnabled = providers.gradleProperty("autojs.nodejs.rawNetworkModules.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsWorkerThreadsExperimentalEnabled = providers.gradleProperty("autojs.nodejs.worker_threads.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsChildProcessExperimentalEnabled = providers.gradleProperty("autojs.nodejs.child_process.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsJavaInteropExperimentalEnabled = providers.gradleProperty("autojs.nodejs.javaInterop.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
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
val nodeJsReleaseTaskRequested = taskNames.any { rawTaskName ->
    val taskName = rawTaskName.substringAfterLast(':')
    taskName.contains("Release") &&
            (
                    taskName.startsWith("assemble", ignoreCase = true) ||
                            taskName.startsWith("bundle", ignoreCase = true) ||
                            taskName.startsWith("package", ignoreCase = true) ||
                            taskName.startsWith("publish", ignoreCase = true) ||
                            taskName.startsWith("verify", ignoreCase = true) ||
                            taskName.startsWith("generate", ignoreCase = true)
                    )
}
val nodeJsReleaseForbiddenExperimentalFlags = linkedMapOf(
    "autojs.nodejs.dynamicImport.experimental" to nodeJsDynamicImportExperimentalEnabled,
    "autojs.nodejs.network.experimental" to nodeJsNetworkExperimentalEnabled,
    "autojs.nodejs.rawNetworkModules.experimental" to nodeJsRawNetworkModulesExperimentalEnabled,
    "autojs.nodejs.worker_threads.experimental" to nodeJsWorkerThreadsExperimentalEnabled,
    "autojs.nodejs.child_process.experimental" to nodeJsChildProcessExperimentalEnabled,
    "autojs.nodejs.javaInterop.experimental" to nodeJsJavaInteropExperimentalEnabled,
    "autojs.nodejs.profile.pro.enabled" to nodeJsProfileProEnabled,
    "autojs.nodejs.profile.desktop.enabled" to nodeJsProfileDesktopEnabled,
    "autojs.nodejs.profile.debugUnsafeLab.enabled" to nodeJsProfileDebugUnsafeLabEnabled,
    "autojs.nodejs.relaxedCapabilities.enabled" to nodeJsRelaxedCapabilitiesEnabled,
)
if (nodeJsReleaseTaskRequested) {
    val enabledForbiddenFlags = nodeJsReleaseForbiddenExperimentalFlags.filterValues { it }.keys
    if (enabledForbiddenFlags.isNotEmpty()) {
        throw GradleException(
            "Release Node builds cannot enable experimental feature flag(s): ${enabledForbiddenFlags.joinToString()}. " +
                    "Use a debug experimental build for validation and keep release builds on the Safe Node Profile defaults."
        )
    }
}
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
val nodeJsV11AlphaInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
    "org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest",
    "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
    "org.autojs.autojs.engine.NodeWebPlatformGlobalsInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest",
    "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
    "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest",
    "org.autojs.autojs.engine.NodeStreamMvpInstrumentationTest",
    "org.autojs.autojs.engine.NodeZlibBufferInstrumentationTest",
    "org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest",
    "org.autojs.autojs.engine.NodeFdApiInstrumentationTest",
    "org.autojs.autojs.engine.NodeOpendirInstrumentationTest",
    "org.autojs.autojs.engine.NodeFsWatchInstrumentationTest",
    "org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeConformanceInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgePermissionManifestInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeConcurrencyLimitsInstrumentationTest",
    "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest",
    "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeShellBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk",
) + if (nodeJsWorkerThreadsExperimentalEnabled) {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk",
    )
} else {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
    )
}
val nodeJsV11BetaInstrumentationClasses = (
        listOf(
            "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
            "org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest",
            "org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest",
            "org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest",
            "org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest",
            "org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest",
            "org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest",
            "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
            "org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest",
            "org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest",
            "org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest",
            "org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest",
            "org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest",
            "org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest",
        ) + nodeJsV11AlphaInstrumentationClasses
        ).distinct()
val nodeJsPackagedEsmGateInstrumentationClasses = listOf(
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunFullCompatibilityEmbeddedNodePackagedApk",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunDynamicImportLocalEmbeddedNodePackagedApk",
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk",
)
val nodeJsPackagedParityGateInstrumentationClasses = nodeJsPackagedEsmGateInstrumentationClasses
val nodeJsRhinoCompatPackagedSmokeInstrumentationClasses = listOf(
    "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRhinoCompatEmbeddedNodePackagedApk",
)
val nodeJsRhinoDualEngineDeviceEvidenceInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.RhinoNodeDualEngineCompatInstrumentationTest",
)
val nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing",
    "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest#mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsRhinoCompatSelectorFacadeUsesFakeTransport",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing",
    "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderPersistsTransactionsAndDeniesEscapes",
    "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderRegistersAndClosesResources",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchPolicyHardeningUsesFakeTransport",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderStaysDisabledInNormalBuilds",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderStaysDisabledInNormalBuilds",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase5BridgeNetworkUiAndShellBoundariesStayDenied",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase7CapabilityBypassDeniesBeforeProviderDispatch",
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase7ProviderPolicyAndPluginEscapesStayDeniedBeforeDispatch",
)
val nodeJsLiveProviderSummaryTaskName = "generateNodeLiveProviderSummary"
val nodeJsLiveProviderGateCases = listOf(
    mapOf(
        "provider" to "registry",
        "className" to "org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest",
        "methodName" to "providerRegistryDefinesPhase7LiveProviderSurface",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; freezes live provider surface metadata",
    ),
    mapOf(
        "provider" to "registry",
        "className" to "org.autojs.autojs.engine.NodeBridgeProviderRegistryInstrumentationTest",
        "methodName" to "androidHealthReflectsCurrentProviderAvailabilityWithoutPromotingFutureProviders",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; records provider health states",
    ),
    mapOf(
        "provider" to "doctor",
        "className" to "org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest",
        "methodName" to "generatesPrivacyRedactedTextAndJsonReports",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; records Doctor provider-health reporting",
    ),
    mapOf(
        "provider" to "fetch",
        "className" to "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest",
        "methodName" to "jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "fetch",
        "className" to "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest",
        "methodName" to "androidFetchProviderCancelsActiveLiveRequestOnDestroy",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "websocket",
        "className" to "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest",
        "methodName" to "jsWebSocketUsesAndroidLiveProviderWhenNetworkExperimentEnabled",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "websocket",
        "className" to "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest",
        "methodName" to "androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; pass -Pautojs.nodejs.network.experimental=true",
    ),
    mapOf(
        "provider" to "ocr",
        "className" to "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest",
        "methodName" to "kotlinOcrLiveProviderRecognizesMlKitImageHandle",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when MLKit OCR provider is unavailable and JUnit records the reason",
    ),
    mapOf(
        "provider" to "ocr",
        "className" to "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest",
        "methodName" to "androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; validates live provider unknown-handle failure",
    ),
    mapOf(
        "provider" to "storage",
        "className" to "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest",
        "methodName" to "androidStorageProviderPersistsAndScopesByWorkingDirectory",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "storage",
        "className" to "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest",
        "methodName" to "androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "notifications",
        "className" to "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest",
        "methodName" to "androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; permission-denied path is an explicit pass condition",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; unavailable sensor path is asserted in-test",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderOnceObservesLiveSensorEventWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when no supported Android sensor is available and JUnit records the reason",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when no supported Android sensor is available and JUnit records the reason",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderEnforcesLiveSubscriptionLimitWhenAvailable",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when no supported Android sensor is available and JUnit records the reason",
    ),
    mapOf(
        "provider" to "sensors",
        "className" to "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest",
        "methodName" to "androidProviderReportsUnavailableWhenSensorManagerMissing",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; validates explicit unavailable failure",
    ),
    mapOf(
        "provider" to "accessibility",
        "className" to "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest",
        "methodName" to "providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
        "interaction" to "pre-run setup",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when the focused accessibility flag or service is not enabled and JUnit records the reason",
    ),
    mapOf(
        "provider" to "accessibility",
        "className" to "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest",
        "methodName" to "androidAccessibilitySelectorV2RejectsUntilProviderIsConnected",
        "interaction" to "none",
        "skipAllowed" to "true",
        "skipPolicy" to "may skip only when the accessibility provider is already connected and the real smoke covers the live path",
    ),
    mapOf(
        "provider" to "image",
        "className" to "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest",
        "methodName" to "androidImageExpansionRejectsUntilProviderIsConnected",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run; validates fail-closed live image provider behavior",
    ),
    mapOf(
        "provider" to "ui",
        "className" to "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
        "methodName" to "jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "ui",
        "className" to "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
        "methodName" to "androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "ui",
        "className" to "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest",
        "methodName" to "androidUiProviderRejectsUnsupportedDescriptorsAndForeignHandles",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerLiveProviderCancelPreventsDelayedExecution",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
    mapOf(
        "provider" to "work_manager",
        "className" to "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest",
        "methodName" to "androidWorkManagerProviderRequiresExplicitCapability",
        "interaction" to "none",
        "skipAllowed" to "false",
        "skipPolicy" to "must run",
    ),
)
val nodeJsLiveProviderGateInstrumentationClasses = nodeJsLiveProviderGateCases.map { testCase ->
    "${testCase.getValue("className")}#${testCase.getValue("methodName")}"
}
val nodeJsLiveFetchGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderCancelsActiveLiveRequestOnDestroy",
)
val nodeJsLiveWebSocketGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#jsWebSocketUsesAndroidLiveProviderWhenNetworkExperimentEnabled",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
)
val nodeJsLiveOcrGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
)
val nodeJsLiveStorageNotificationsGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest#androidStorageProviderPersistsAndScopesByWorkingDirectory",
    "org.autojs.autojs.engine.NodeStorageBridgeInstrumentationTest#androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation",
    "org.autojs.autojs.engine.NodeNotificationsBridgeInstrumentationTest#androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource",
)
val nodeJsLiveSensorsGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderOnceObservesLiveSensorEventWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderEnforcesLiveSubscriptionLimitWhenAvailable",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderReportsUnavailableWhenSensorManagerMissing",
)
val nodeJsLiveAccessibilityImageGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest#providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#androidAccessibilitySelectorV2RejectsUntilProviderIsConnected",
    "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest#androidImageExpansionRejectsUntilProviderIsConnected",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#androidOcrRejectsUnknownImageHandleWithoutLeakingResources",
)
val nodeJsLiveUiGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderRejectsUnsupportedDescriptorsAndForeignHandles",
)
val nodeJsLiveWorkManagerGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerProviderSchedulesLiveMetadataAndEnforcesPathPolicy",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderCancelPreventsDelayedExecution",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerProviderRequiresExplicitCapability",
)
val nodeJsLongRunningGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeAndroidRuntimePolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeExecutionModePolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeInteractiveLongRunningInstrumentationTest",
    "org.autojs.autojs.engine.NodeLongRunningStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeRuntimeBudgetPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
)
val nodeJsWorkerGateInstrumentationClasses = if (nodeJsWorkerThreadsExperimentalEnabled) {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk",
    )
} else {
    listOf(
        "org.autojs.autojs.engine.NodeWorkerThreadsBootstrapInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest",
    )
}
val nodeJsModernCoreGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
    "org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest",
    "org.autojs.autojs.engine.NodeFeatureFlagRegistryInstrumentationTest",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest",
    "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
    "org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest",
    "org.autojs.autojs.engine.NodeStreamMvpInstrumentationTest",
    "org.autojs.autojs.engine.NodeZlibBufferInstrumentationTest",
    "org.autojs.autojs.engine.NodeCryptoExpansionInstrumentationTest",
    "org.autojs.autojs.engine.NodeProcessSweepInstrumentationTest",
    "org.autojs.autojs.engine.NodeOsPrivacyInstrumentationTest",
    "org.autojs.autojs.engine.NodeModuleSafeSubsetInstrumentationTest",
    "org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeVmDesignGateInstrumentationTest",
    "org.autojs.autojs.ui.settings.NodeDoctorReportGeneratorInstrumentationTest",
)
val nodeJsPhase7ResourceStressGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodePhase7ResourceStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeLongRunningStressInstrumentationTest",
    "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderCancelsActiveLiveRequestOnDestroy",
    "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderClosesActiveLiveConnectionOnDestroy",
    "org.autojs.autojs.engine.NodeOcrBridgeInstrumentationTest#kotlinOcrLiveProviderRecognizesMlKitImageHandle",
    "org.autojs.autojs.engine.NodeSensorsBridgeInstrumentationTest#androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable",
    "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
    "org.autojs.autojs.engine.NodeWorkManagerBridgeInstrumentationTest#androidWorkManagerLiveProviderExecutesPackagedProjectEntryAndPersistsResult",
    "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
)
val nodeJsSecurityGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
)
val nodeJs2417SecurityProbeInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase8Node2417SecurityFixProbesStaySafe",
)
val nodeJsTypeScriptGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeTypeScriptInstrumentationTest",
)
val nodeJsDeveloperExperienceGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeTypeScriptInstrumentationTest",
    "org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest#nodeTestEmbeddedSubsetRunsSingleRuntimeTap",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest#nodeTestEmbeddedSubsetReportsFailuresTimeoutsAndLateAsyncActivity",
    "org.autojs.autojs.engine.NodeCoreBuiltinCompatibilityInstrumentationTest#partialEsmImportsNodeTestEmbeddedSubset",
    "org.autojs.autojs.engine.NodePluginSystemInstrumentationTest#pluginLifecycleResourcesAndDebugReload",
)
val nodeJsRealNpmGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
)
val nodeJsPluginUiGateInstrumentationClasses = (
        listOf(
            "org.autojs.autojs.engine.NodePluginSystemInstrumentationTest",
            "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk",
        ) + nodeJsLiveUiGateInstrumentationClasses
        ).distinct()
val nodeJsPhase7GateInstrumentationClasses = (
        nodeJsPackagedEsmGateInstrumentationClasses +
                nodeJsLiveProviderGateInstrumentationClasses +
                nodeJsLongRunningGateInstrumentationClasses +
                nodeJsWorkerGateInstrumentationClasses +
                nodeJsModernCoreGateInstrumentationClasses +
                nodeJsPhase7ResourceStressGateInstrumentationClasses +
                nodeJsSecurityGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses +
                nodeJsPluginUiGateInstrumentationClasses
        ).distinct()
val nodeJs2417UpgradeGateInstrumentationClasses = (
        listOf(
            "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
            "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
            "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
            "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
            "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
            "org.autojs.autojs.engine.NodeFsPromisesInstrumentationTest",
            "org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest",
            "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
        ) +
                nodeJs2417SecurityProbeInstrumentationClasses +
                nodeJsPackagedEsmGateInstrumentationClasses +
                nodeJsLiveProviderGateInstrumentationClasses +
                nodeJsLongRunningGateInstrumentationClasses +
                nodeJsWorkerGateInstrumentationClasses +
                nodeJsModernCoreGateInstrumentationClasses
        ).distinct()
val nodeJsPhase8HttpGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeHttpClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpsClientFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpStreamSemanticsInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpAgentFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeUndiciFacadeInstrumentationTest",
    "org.autojs.autojs.engine.NodeHttpNpmCorpusInstrumentationTest",
)
val nodeJsPhase8AdvancedFsGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeFileHandleAdvancedInstrumentationTest",
    "org.autojs.autojs.engine.NodeFdApiInstrumentationTest",
    "org.autojs.autojs.engine.NodeOpendirInstrumentationTest",
    "org.autojs.autojs.engine.NodeFsWatchInstrumentationTest",
    "org.autojs.autojs.engine.NodeSafeFileOperationsInstrumentationTest",
    "org.autojs.autojs.engine.NodeFileHandleWatcherFuzzerInstrumentationTest",
)
val nodeJsPhase8PerformanceGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeModuleCompileCacheInstrumentationTest",
    "org.autojs.autojs.engine.NodeRuntimePrewarmPoolInstrumentationTest",
    "org.autojs.autojs.engine.NodePerformanceMatrixInstrumentationTest",
)
val nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest",
    "org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest",
    "org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest",
    "org.autojs.autojs.engine.NodeBridgeRpcFuzzerInstrumentationTest",
)
val nodeJsPhase9WasmWasiGateInstrumentationClasses = listOf(
    "org.autojs.autojs.engine.NodeWasmWasiInstrumentationTest",
)
val nodeJsPhase9GateInstrumentationClasses = (
        nodeJsPhase9WasmWasiGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses
        ).distinct()
val nodeJsWorkerWasiGateInstrumentationClasses = (
        nodeJsWorkerGateInstrumentationClasses +
                nodeJsPhase9WasmWasiGateInstrumentationClasses
        ).distinct()
val nodeJsPhase10GateInstrumentationClasses = (
        nodeJsPackagedParityGateInstrumentationClasses +
                nodeJsWorkerWasiGateInstrumentationClasses +
                nodeJsDeveloperExperienceGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses
        ).distinct()
val nodeJsPhase8SecurityStressGateInstrumentationClasses = (
        nodeJs2417SecurityProbeInstrumentationClasses +
                nodeJsTypeScriptGateInstrumentationClasses +
                listOf(
                    "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
                    "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
                ) +
                nodeJsPhase8HttpGateInstrumentationClasses +
                nodeJsPhase8AdvancedFsGateInstrumentationClasses +
                nodeJsPhase8PerformanceGateInstrumentationClasses +
                nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses
        ).distinct()
val nodeJsPhase8GateInstrumentationClasses = (
        nodeJs2417SecurityProbeInstrumentationClasses +
                nodeJsTypeScriptGateInstrumentationClasses +
                listOf(
                    "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
                    "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
                    "org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest",
                    "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
                ) +
                nodeJsPhase8HttpGateInstrumentationClasses +
                nodeJsPhase8AdvancedFsGateInstrumentationClasses +
                nodeJsPhase8PerformanceGateInstrumentationClasses +
                nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses +
                nodeJsRealNpmGateInstrumentationClasses
        ).distinct()
val nodeJsPhase7HostGateNames = listOf(
    "generateDesktopNodeDifferentialReport",
    "generateNodeModernCoreDifferentialReport",
    "verifyNodeDependencyBundlerFixtures",
)
val nodeJsPhase7SummaryTaskName = "generateNodePhase7Summary"
val nodeJsPhase8HostGateNames = listOf(
    "generateNode2417NativeDiffReport",
    "generateNode2417SecurityProbeReport",
    "generateNode2417UpgradeGateReport",
    "verifyNodeProjectWizard",
    "verifyNodeExamples",
    "verifyAutoJs6NodeTypeDeclarations",
    "verifyNodeCompatibilityProfile",
    "verifyNodeDependencyManagerFixture",
    "verifyNodeDependencyBundlerFixtures",
)
val nodeJsPhase8SummaryTaskName = "generateNodePhase8Summary"
val nodeJsPhase9SummaryTaskName = "generateNodePhase9Summary"
val nodeJsPhase7GateCoverageGroups = listOf(
    "packaged_esm_dynamic" to nodeJsPackagedEsmGateInstrumentationClasses,
    "live_providers" to nodeJsLiveProviderGateInstrumentationClasses,
    "long_running" to nodeJsLongRunningGateInstrumentationClasses,
    "worker" to nodeJsWorkerGateInstrumentationClasses,
    "modern_core" to nodeJsModernCoreGateInstrumentationClasses,
    "dependency_sanitizer" to emptyList(),
    "plugin_ui" to nodeJsPluginUiGateInstrumentationClasses,
    "security_corpus" to nodeJsSecurityGateInstrumentationClasses,
    "stress_subset" to nodeJsPhase7ResourceStressGateInstrumentationClasses,
    "real_npm_corpus" to nodeJsRealNpmGateInstrumentationClasses,
)
val nodeJsPhase8GateCoverageGroups = listOf(
    "node_2417_upgrade_boundary" to nodeJs2417SecurityProbeInstrumentationClasses,
    "typescript_runtime" to nodeJsTypeScriptGateInstrumentationClasses,
    "module_semantics" to listOf(
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest",
    ),
    "http_compatibility" to nodeJsPhase8HttpGateInstrumentationClasses,
    "advanced_fs" to nodeJsPhase8AdvancedFsGateInstrumentationClasses,
    "performance_startup" to nodeJsPhase8PerformanceGateInstrumentationClasses,
    "inspector_policy" to listOf(
        "org.autojs.autojs.engine.NodeInspectorDesignGateInstrumentationTest",
        "org.autojs.autojs.engine.NodeInspectorSecurityGateInstrumentationTest",
    ),
    "sandbox_concurrency" to nodeJsPhase8SandboxConcurrencyGateInstrumentationClasses,
    "real_npm_corpus" to nodeJsRealNpmGateInstrumentationClasses,
    "examples_tooling" to emptyList(),
)
val nodeJsFocusedGateInstrumentationClasses = mapOf(
    nodeJsPhase7SummaryTaskName to nodeJsPhase7GateInstrumentationClasses,
    nodeJsPhase8SummaryTaskName to nodeJsPhase8GateInstrumentationClasses,
    nodeJsPhase9SummaryTaskName to nodeJsPhase9GateInstrumentationClasses,
    nodeJsLiveProviderSummaryTaskName to nodeJsLiveProviderGateInstrumentationClasses,
    "verifyNodePackagedEsmGate" to nodeJsPackagedEsmGateInstrumentationClasses,
    "verifyNodePackagedParityGate" to nodeJsPackagedParityGateInstrumentationClasses,
    "verifyNodeRhinoCompatPackagedSmoke" to nodeJsRhinoCompatPackagedSmokeInstrumentationClasses,
    "verifyNodeRhinoCompatDualEngineDeviceEvidence" to nodeJsRhinoDualEngineDeviceEvidenceInstrumentationClasses,
    "verifyNodeRhinoCompatResourceCleanupSecurity" to nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses,
    "verifyNodeLiveProviderGate" to nodeJsLiveProviderGateInstrumentationClasses,
    "verifyNodeLiveFetchGate" to nodeJsLiveFetchGateInstrumentationClasses,
    "verifyNodeLiveWebSocketGate" to nodeJsLiveWebSocketGateInstrumentationClasses,
    "verifyNodeLiveOcrGate" to nodeJsLiveOcrGateInstrumentationClasses,
    "verifyNodeLiveStorageNotificationsGate" to nodeJsLiveStorageNotificationsGateInstrumentationClasses,
    "verifyNodeLiveSensorsGate" to nodeJsLiveSensorsGateInstrumentationClasses,
    "verifyNodeLiveAccessibilityImageGate" to nodeJsLiveAccessibilityImageGateInstrumentationClasses,
    "verifyNodeLiveUiGate" to nodeJsLiveUiGateInstrumentationClasses,
    "verifyNodeLiveWorkManagerGate" to nodeJsLiveWorkManagerGateInstrumentationClasses,
    "verifyNodeLongRunningGate" to nodeJsLongRunningGateInstrumentationClasses,
    "verifyNodeWorkerGate" to nodeJsWorkerGateInstrumentationClasses,
    "verifyNodeModernCoreGate" to nodeJsModernCoreGateInstrumentationClasses,
    "verifyNodePhase7ResourceStressGate" to nodeJsPhase7ResourceStressGateInstrumentationClasses,
    "verifyNodePhase7Gate" to nodeJsPhase7GateInstrumentationClasses,
    "verifyNode2417SecurityProbeGate" to nodeJs2417SecurityProbeInstrumentationClasses,
    "verifyNode2417UpgradeGate" to nodeJs2417UpgradeGateInstrumentationClasses,
    "verifyNodeTypeScriptGate" to nodeJsTypeScriptGateInstrumentationClasses,
    "verifyNodeDeveloperExperienceGate" to nodeJsDeveloperExperienceGateInstrumentationClasses,
    "verifyNodeRealNpmGate" to nodeJsRealNpmGateInstrumentationClasses,
    "verifyNodePhase8SecurityStressGate" to nodeJsPhase8SecurityStressGateInstrumentationClasses,
    "verifyNodePhase8Gate" to nodeJsPhase8GateInstrumentationClasses,
    "verifyNodeWasmWasiGate" to nodeJsPhase9WasmWasiGateInstrumentationClasses,
    "verifyNodeWorkerWasiGate" to nodeJsWorkerWasiGateInstrumentationClasses,
    "verifyNodePhase9Gate" to nodeJsPhase9GateInstrumentationClasses,
    "verifyNodePhase10Gate" to nodeJsPhase10GateInstrumentationClasses,
)
val nodeJsRequestedFocusedGate = taskNames
    .map { rawTaskName -> rawTaskName.substringAfterLast(':') }
    .firstOrNull { taskName -> taskName in nodeJsFocusedGateInstrumentationClasses }
val nodeJsFocusedGateInstrumentationClassArgument = nodeJsRequestedFocusedGate?.let { taskName ->
    nodeJsFocusedGateInstrumentationClasses.getValue(taskName).joinToString(",")
}
val generateNodeLiveProviderSummary = tasks.register(nodeJsLiveProviderSummaryTaskName) {
    group = "verification"
    description = "Writes build/reports/nodejs/live-provider-summary.json from the focused live-provider gate results."

    dependsOn("connectedAppDebugAndroidTest")
    outputs.file(layout.buildDirectory.file("reports/nodejs/live-provider-summary.json"))
    outputs.upToDateWhen { false }

    doFirst {
        if (!nodeJsNetworkExperimentalEnabled) {
            throw GradleException("$name requires -Pautojs.nodejs.network.experimental=true so fetch/websocket live providers are covered.")
        }
    }

    doLast {
        val resultDir = layout.buildDirectory
            .dir("outputs/androidTest-results/connected/debug/flavors/app")
            .get()
            .asFile
        val xmlFiles = resultDir
            .walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw GradleException("No connected Android test XML results found under ${resultDir.absolutePath}.")
        }

        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val results = linkedMapOf<String, Map<String, Any?>>()
        val devices = linkedSetOf<String>()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        fun firstChild(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is org.w3c.dom.Element && child.tagName == name) {
                    return child
                }
            }
            return null
        }

        fun childMessage(element: org.w3c.dom.Element): String {
            return element.getAttribute("message")
                .ifBlank { element.textContent.orEmpty().trim() }
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        }

        xmlFiles.forEach { xmlFile ->
            val document = parserFactory.newDocumentBuilder().parse(xmlFile)
            val root = document.documentElement
            totalTests += root.getAttribute("tests").toIntOrNull() ?: 0
            totalFailures += root.getAttribute("failures").toIntOrNull() ?: 0
            totalErrors += root.getAttribute("errors").toIntOrNull() ?: 0
            totalSkipped += root.getAttribute("skipped").toIntOrNull() ?: 0

            val properties = document.getElementsByTagName("property")
            for (index in 0 until properties.length) {
                val property = properties.item(index) as? org.w3c.dom.Element ?: continue
                if (property.getAttribute("name") == "device") {
                    property.getAttribute("value").takeIf { it.isNotBlank() }?.let(devices::add)
                }
            }

            val testCases = document.getElementsByTagName("testcase")
            for (index in 0 until testCases.length) {
                val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
                val className = testCase.getAttribute("classname")
                val methodName = testCase.getAttribute("name")
                val failure = firstChild(testCase, "failure")
                val error = firstChild(testCase, "error")
                val skipped = firstChild(testCase, "skipped")
                val status = when {
                    failure != null -> "failed"
                    error != null -> "error"
                    skipped != null -> "skipped"
                    else -> "passed"
                }
                val message = when {
                    failure != null -> childMessage(failure)
                    error != null -> childMessage(error)
                    skipped != null -> childMessage(skipped)
                    else -> ""
                }
                results["$className#$methodName"] = linkedMapOf(
                    "status" to status,
                    "message" to message,
                    "timeSeconds" to testCase.getAttribute("time").toDoubleOrNull(),
                )
            }
        }

        val policyViolations = mutableListOf<String>()
        val caseSummaries = nodeJsLiveProviderGateCases.map { testCase ->
            val key = "${testCase.getValue("className")}#${testCase.getValue("methodName")}"
            val result = results[key]
            val status = result?.get("status")?.toString() ?: "missing"
            val xmlMessage = result?.get("message")?.toString().orEmpty()
            val skipAllowed = testCase.getValue("skipAllowed").toBooleanStrict()
            val message = if (status == "skipped" && xmlMessage.isBlank() && skipAllowed) {
                testCase.getValue("skipPolicy")
            } else {
                xmlMessage
            }
            val skipReasonSource = when {
                status != "skipped" -> null
                xmlMessage.isNotBlank() -> "junit-xml"
                message.isNotBlank() -> "summary-policy"
                else -> null
            }
            when {
                status == "missing" -> policyViolations += "$key did not appear in connected test results."
                status == "failed" || status == "error" -> policyViolations += "$key ended with $status: $message"
                status == "skipped" && !skipAllowed -> policyViolations += "$key skipped unexpectedly: $message"
                status == "skipped" && message.isBlank() -> policyViolations += "$key skipped without an explicit reason."
            }
            linkedMapOf(
                "provider" to testCase.getValue("provider"),
                "className" to testCase.getValue("className"),
                "methodName" to testCase.getValue("methodName"),
                "status" to status,
                "message" to message.ifBlank { null },
                "xmlMessage" to xmlMessage.ifBlank { null },
                "skipReasonSource" to skipReasonSource,
                "timeSeconds" to result?.get("timeSeconds"),
                "interaction" to testCase.getValue("interaction"),
                "fakeProviderAllowed" to false,
                "skipAllowed" to skipAllowed,
                "skipPolicy" to testCase.getValue("skipPolicy"),
            )
        }
        val coveredProviders = caseSummaries
            .filter { testCase -> testCase["status"] in setOf("passed", "skipped") }
            .map { testCase -> testCase.getValue("provider").toString() }
            .toSortedSet()

        val reportFile = layout.buildDirectory
            .file("reports/nodejs/live-provider-summary.json")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()
        val summary = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to Instant.now().toString(),
            "task" to "S7-16 Live Provider Gate",
            "gate" to "verifyNodeLiveProviderGate",
            "deviceProfile" to nodeJsTestDeviceProfile,
            "devices" to devices.toList(),
            "networkExperimentalRequired" to true,
            "networkExperimentalEnabled" to nodeJsNetworkExperimentalEnabled,
            "fakeProvidersAllowed" to false,
            "skipReasonRequired" to true,
            "resultXml" to xmlFiles.map { file -> file.relativeTo(projectDir).invariantSeparatorsPath },
            "totals" to linkedMapOf(
                "tests" to totalTests,
                "failures" to totalFailures,
                "errors" to totalErrors,
                "skipped" to totalSkipped,
                "expectedCases" to nodeJsLiveProviderGateCases.size,
                "coveredProviders" to coveredProviders.toList(),
            ),
            "policyViolations" to policyViolations,
            "cases" to caseSummaries,
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + System.lineSeparator())
        logger.lifecycle("Node live provider summary written to ${reportFile.absolutePath}")

        if (policyViolations.isNotEmpty()) {
            throw GradleException(
                "Node live provider summary found ${policyViolations.size} policy violation(s). " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}
val generateNodePhase7Summary = tasks.register(nodeJsPhase7SummaryTaskName) {
    group = "verification"
    description = "Writes build/reports/nodejs/phase7-summary.json from the focused Phase 7 gate results."

    dependsOn("connectedAppDebugAndroidTest")
    nodeJsPhase7HostGateNames.forEach { taskName -> dependsOn(taskName) }
    outputs.file(layout.buildDirectory.file("reports/nodejs/phase7-summary.json"))
    outputs.upToDateWhen { false }

    doLast {
        val resultDir = layout.buildDirectory
            .dir("outputs/androidTest-results/connected/debug/flavors/app")
            .get()
            .asFile
        val xmlFiles = resultDir
            .walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw GradleException("No connected Android test XML results found under ${resultDir.absolutePath}.")
        }

        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val devices = linkedSetOf<String>()
        val testResults = linkedMapOf<String, MutableList<Map<String, Any?>>>()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        fun firstChild(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is org.w3c.dom.Element && child.tagName == name) {
                    return child
                }
            }
            return null
        }

        fun childMessage(element: org.w3c.dom.Element): String {
            return element.getAttribute("message")
                .ifBlank { element.textContent.orEmpty().trim() }
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        }

        xmlFiles.forEach { xmlFile ->
            val document = parserFactory.newDocumentBuilder().parse(xmlFile)
            val root = document.documentElement
            totalTests += root.getAttribute("tests").toIntOrNull() ?: 0
            totalFailures += root.getAttribute("failures").toIntOrNull() ?: 0
            totalErrors += root.getAttribute("errors").toIntOrNull() ?: 0
            totalSkipped += root.getAttribute("skipped").toIntOrNull() ?: 0

            val properties = document.getElementsByTagName("property")
            for (index in 0 until properties.length) {
                val property = properties.item(index) as? org.w3c.dom.Element ?: continue
                if (property.getAttribute("name") == "device") {
                    property.getAttribute("value").takeIf { it.isNotBlank() }?.let(devices::add)
                }
            }

            val testCases = document.getElementsByTagName("testcase")
            for (index in 0 until testCases.length) {
                val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
                val className = testCase.getAttribute("classname")
                val methodName = testCase.getAttribute("name")
                val failure = firstChild(testCase, "failure")
                val error = firstChild(testCase, "error")
                val skipped = firstChild(testCase, "skipped")
                val status = when {
                    failure != null -> "failed"
                    error != null -> "error"
                    skipped != null -> "skipped"
                    else -> "passed"
                }
                val message = when {
                    failure != null -> childMessage(failure)
                    error != null -> childMessage(error)
                    skipped != null -> childMessage(skipped)
                    else -> ""
                }
                testResults.getOrPut(className) { mutableListOf() } += linkedMapOf(
                    "selector" to "$className#$methodName",
                    "className" to className,
                    "methodName" to methodName,
                    "status" to status,
                    "message" to message.ifBlank { null },
                    "timeSeconds" to testCase.getAttribute("time").toDoubleOrNull(),
                )
            }
        }

        fun selectorStatus(selector: String): Map<String, Any?> {
            val className = selector.substringBefore("#")
            val methodName = selector.substringAfter("#", "")
            val matches = if (methodName.isBlank()) {
                testResults[className].orEmpty()
            } else {
                testResults[className].orEmpty().filter { result -> result["methodName"] == methodName }
            }
            val status = when {
                matches.isEmpty() -> "missing"
                matches.any { it["status"] == "failed" } -> "failed"
                matches.any { it["status"] == "error" } -> "error"
                matches.all { it["status"] == "skipped" } -> "skipped"
                else -> "passed"
            }
            return linkedMapOf(
                "selector" to selector,
                "status" to status,
                "tests" to matches.size,
                "failed" to matches.count { it["status"] == "failed" },
                "errors" to matches.count { it["status"] == "error" },
                "skipped" to matches.count { it["status"] == "skipped" },
                "messages" to matches.mapNotNull { it["message"] as? String }.filter { it.isNotBlank() }.distinct(),
            )
        }

        val coverageGroups = nodeJsPhase7GateCoverageGroups.map { (area, selectors) ->
            val selectorSummaries = selectors.map(::selectorStatus)
            linkedMapOf(
                "area" to area,
                "kind" to if (area == "dependency_sanitizer") "host" else "instrumentation",
                "expectedSelectors" to selectors.size,
                "coveredSelectors" to selectorSummaries.count { it["status"] in setOf("passed", "skipped") },
                "missingSelectors" to selectorSummaries.filter { it["status"] == "missing" }.map { it["selector"] },
                "failedSelectors" to selectorSummaries.filter { it["status"] in setOf("failed", "error") }.map { it["selector"] },
                "skippedSelectors" to selectorSummaries.filter { it["status"] == "skipped" }.map { it["selector"] },
                "selectors" to selectorSummaries,
            )
        }
        val policyViolations = coverageGroups.flatMap { group ->
            val area = group["area"].toString()
            (group["missingSelectors"] as List<*>).map { selector -> "$area missing $selector" } +
                    (group["failedSelectors"] as List<*>).map { selector -> "$area failed $selector" }
        }

        val reportFile = layout.buildDirectory
            .file("reports/nodejs/phase7-summary.json")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()
        val summary = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to Instant.now().toString(),
            "task" to "S7-49 Phase 7 Functional Gate",
            "gate" to "verifyNodePhase7Gate",
            "deviceProfile" to nodeJsTestDeviceProfile,
            "networkExperimentalEnabled" to nodeJsNetworkExperimentalEnabled,
            "workerThreadsExperimentalEnabled" to nodeJsWorkerThreadsExperimentalEnabled,
            "devices" to devices.toList(),
            "resultXml" to xmlFiles.map { file -> file.relativeTo(projectDir).invariantSeparatorsPath },
            "hostGates" to nodeJsPhase7HostGateNames.map { taskName ->
                linkedMapOf(
                    "task" to taskName,
                    "status" to "passed_if_summary_was_generated",
                )
            },
            "totals" to linkedMapOf(
                "tests" to totalTests,
                "failures" to totalFailures,
                "errors" to totalErrors,
                "skipped" to totalSkipped,
                "expectedInstrumentationSelectors" to nodeJsPhase7GateInstrumentationClasses.size,
            ),
            "coverageGroups" to coverageGroups,
            "policyViolations" to policyViolations,
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + System.lineSeparator())
        logger.lifecycle("Node Phase 7 summary written to ${reportFile.absolutePath}")

        if (policyViolations.isNotEmpty()) {
            throw GradleException(
                "Node Phase 7 summary found ${policyViolations.size} policy violation(s). " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}
val generateNodePhase8Summary = tasks.register(nodeJsPhase8SummaryTaskName) {
    group = "verification"
    description = "Writes build/reports/nodejs/phase8-summary.json from the focused Phase 8 gate results."

    dependsOn("connectedAppDebugAndroidTest")
    nodeJsPhase8HostGateNames.forEach { taskName -> dependsOn(taskName) }
    outputs.file(layout.buildDirectory.file("reports/nodejs/phase8-summary.json"))
    outputs.upToDateWhen { false }

    doLast {
        val resultDir = layout.buildDirectory
            .dir("outputs/androidTest-results/connected/debug/flavors/app")
            .get()
            .asFile
        val xmlFiles = resultDir
            .walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("TEST-") && file.extension == "xml" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw GradleException("No connected Android test XML results found under ${resultDir.absolutePath}.")
        }

        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val devices = linkedSetOf<String>()
        val testResults = linkedMapOf<String, MutableList<Map<String, Any?>>>()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        fun firstChild(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is org.w3c.dom.Element && child.tagName == name) {
                    return child
                }
            }
            return null
        }

        fun childMessage(element: org.w3c.dom.Element): String {
            return element.getAttribute("message")
                .ifBlank { element.textContent.orEmpty().trim() }
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        }

        xmlFiles.forEach { xmlFile ->
            val document = parserFactory.newDocumentBuilder().parse(xmlFile)
            val root = document.documentElement
            totalTests += root.getAttribute("tests").toIntOrNull() ?: 0
            totalFailures += root.getAttribute("failures").toIntOrNull() ?: 0
            totalErrors += root.getAttribute("errors").toIntOrNull() ?: 0
            totalSkipped += root.getAttribute("skipped").toIntOrNull() ?: 0

            val properties = document.getElementsByTagName("property")
            for (index in 0 until properties.length) {
                val property = properties.item(index) as? org.w3c.dom.Element ?: continue
                if (property.getAttribute("name") == "device") {
                    property.getAttribute("value").takeIf { it.isNotBlank() }?.let(devices::add)
                }
            }

            val testCases = document.getElementsByTagName("testcase")
            for (index in 0 until testCases.length) {
                val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
                val className = testCase.getAttribute("classname")
                val methodName = testCase.getAttribute("name")
                val failure = firstChild(testCase, "failure")
                val error = firstChild(testCase, "error")
                val skipped = firstChild(testCase, "skipped")
                val status = when {
                    failure != null -> "failed"
                    error != null -> "error"
                    skipped != null -> "skipped"
                    else -> "passed"
                }
                val message = when {
                    failure != null -> childMessage(failure)
                    error != null -> childMessage(error)
                    skipped != null -> childMessage(skipped)
                    else -> ""
                }
                testResults.getOrPut(className) { mutableListOf() } += linkedMapOf(
                    "selector" to "$className#$methodName",
                    "className" to className,
                    "methodName" to methodName,
                    "status" to status,
                    "message" to message.ifBlank { null },
                    "timeSeconds" to testCase.getAttribute("time").toDoubleOrNull(),
                )
            }
        }

        fun selectorStatus(selector: String): Map<String, Any?> {
            val className = selector.substringBefore("#")
            val methodName = selector.substringAfter("#", "")
            val matches = if (methodName.isBlank()) {
                testResults[className].orEmpty()
            } else {
                testResults[className].orEmpty().filter { result -> result["methodName"] == methodName }
            }
            val status = when {
                matches.isEmpty() -> "missing"
                matches.any { it["status"] == "failed" } -> "failed"
                matches.any { it["status"] == "error" } -> "error"
                matches.all { it["status"] == "skipped" } -> "skipped"
                else -> "passed"
            }
            return linkedMapOf(
                "selector" to selector,
                "status" to status,
                "tests" to matches.size,
                "failed" to matches.count { it["status"] == "failed" },
                "errors" to matches.count { it["status"] == "error" },
                "skipped" to matches.count { it["status"] == "skipped" },
                "messages" to matches.mapNotNull { it["message"] as? String }.filter { it.isNotBlank() }.distinct(),
            )
        }

        val coverageGroups = nodeJsPhase8GateCoverageGroups.map { (area, selectors) ->
            val selectorSummaries = selectors.map(::selectorStatus)
            linkedMapOf(
                "area" to area,
                "kind" to if (selectors.isEmpty()) "host" else "instrumentation",
                "expectedSelectors" to selectors.size,
                "coveredSelectors" to selectorSummaries.count { it["status"] in setOf("passed", "skipped") },
                "missingSelectors" to selectorSummaries.filter { it["status"] == "missing" }.map { it["selector"] },
                "failedSelectors" to selectorSummaries.filter { it["status"] in setOf("failed", "error") }.map { it["selector"] },
                "skippedSelectors" to selectorSummaries.filter { it["status"] == "skipped" }.map { it["selector"] },
                "selectors" to selectorSummaries,
            )
        }
        val policyViolations = coverageGroups.flatMap { group ->
            val area = group["area"].toString()
            (group["missingSelectors"] as List<*>).map { selector -> "$area missing $selector" } +
                    (group["failedSelectors"] as List<*>).map { selector -> "$area failed $selector" }
        }

        val reportFile = layout.buildDirectory
            .file("reports/nodejs/phase8-summary.json")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()
        val summary = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to Instant.now().toString(),
            "task" to "S8-50 Phase 8 Summary And Capability Matrix",
            "gate" to "verifyNodePhase8Gate",
            "deviceProfile" to nodeJsTestDeviceProfile,
            "networkExperimentalEnabled" to nodeJsNetworkExperimentalEnabled,
            "workerThreadsExperimentalEnabled" to nodeJsWorkerThreadsExperimentalEnabled,
            "devices" to devices.toList(),
            "resultXml" to xmlFiles.map { file -> file.relativeTo(projectDir).invariantSeparatorsPath },
            "hostGates" to nodeJsPhase8HostGateNames.map { taskName ->
                linkedMapOf(
                    "task" to taskName,
                    "status" to "passed_if_summary_was_generated",
                )
            },
            "knownBlockedInputs" to listOf(
                "node_24_17_runtime_promotion_requires_android_artifacts",
                "startup_snapshot_runtime_path_remains_design_gated",
                "inspector_devtools_and_profiling_remain_debug_design_gated",
                "worker_threads_partial_promotion_remains_blocked_by_native_availability",
            ),
            "totals" to linkedMapOf(
                "tests" to totalTests,
                "failures" to totalFailures,
                "errors" to totalErrors,
                "skipped" to totalSkipped,
                "expectedInstrumentationSelectors" to nodeJsPhase8GateInstrumentationClasses.size,
            ),
            "coverageGroups" to coverageGroups,
            "policyViolations" to policyViolations,
        )
        reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)) + System.lineSeparator())
        logger.lifecycle("Node Phase 8 summary written to ${reportFile.absolutePath}")

        if (policyViolations.isNotEmpty()) {
            throw GradleException(
                "Node Phase 8 summary found ${policyViolations.size} policy violation(s). " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}
val nodeJsTestDeviceProfileClasses = when (nodeJsTestDeviceProfile) {
    "sony" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
        "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
        "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest",
        "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
    )
    "emulator" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
        "org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest",
    )
    "smoke" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunToastBridgeEmbeddedNodePackagedApk",
    )
    "accessibility" -> listOf(
        "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest",
    )
    "screen_capture" -> listOf(
        "org.autojs.autojs.engine.NodeScreenCaptureRealDeviceSmokeInstrumentationTest",
    )
    "v1_1_alpha" -> nodeJsV11AlphaInstrumentationClasses
    "v1_1_beta" -> nodeJsV11BetaInstrumentationClasses
    "full" -> listOf(
        "org.autojs.autojs.engine.EmbeddedScriptMvpInstrumentationTest",
        "org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest",
        "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest",
        "org.autojs.autojs.engine.NodeBridgeRpcFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest",
        "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest",
        "org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest",
        "org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest",
        "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
        "org.autojs.autojs.engine.NodeModernCoreDifferentialInstrumentationTest",
        "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
    ) + if (nodeJsWorkerThreadsExperimentalEnabled) {
        listOf("org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest")
    } else {
        emptyList()
    } + listOf("org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest")
    else -> emptyList()
}
val leakCanaryEnabled = providers.gradleProperty("autojs.leakcanary.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val isNodeJsNativeRuntimeRequested = effectiveNodeJsRuntimeBackend in setOf("native", "embedded", "auto")
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

utils.registerTemplateApkCopy(project)

dependencies /* Unclassified */ {
    // Compose
    // implementation("androidx.compose.ui:ui-android:1.6.7")

    // Kotlin reflect
    implementation(kotlin("reflect"))

    // AndroidX Core
    implementation(libs.core.ktx)

    // AndroidX Activity
    implementation(libs.activity.ktx)

    // LeakCanary
    if (leakCanaryEnabled) {
        debugImplementation(libs.leakcanary)
    }

    // Android supports
    implementation(libs.cardview)
    implementation(libs.multidex)

    // Material Components
    implementation(libs.material)

    // SwipeRefreshLayout
    implementation(libs.swiperefreshlayout)

    // ConstraintLayout
    implementation(libs.constraintlayout)

    // FlexboxLayout
    implementation(libs.flexbox)

    // Common Markdown
    implementation(libs.commonmark)

    // Flexmark Java HTML to Markdown Extensible Converter
    implementation(libs.flexmark.html2md)

    // Licenses Dialog
    implementation(libs.licensesdialog)

    // Apache Commons
    implementation(libs.commons.lang3)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.adapter.rxjava2)
    implementation(libs.retrofit2.kotlin.coroutines.adapter)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Joda Time
    implementation(libs.joda.time)

    // OkHttp
    implementation(libs.okhttp)

    // Webkit
    implementation(libs.webkit)

    // Gson
    implementation(libs.gson)

    // Zip4j
    implementation(libs.zip4j)

    // FIXME by SuperMonster003 on Aug 14, 2024.
    //  ! Vulnerable dependency (5 vulnerabilities) for log4j (version 1):
    //  ! - CVE-2022-23307, Score: 8.8
    //  ! - CVE-2022-23305, Score: 9.8
    //  ! - CVE-2022-23302, Score: 8.8
    //  ! - CVE-2021-4104, Score: 7.5
    //  ! - CVE-2019-17571, Score: 9.8
    //  ! However, log4j version 2 which requires Android API Level not lower than 26
    //  ! is not compatible with current project with min API Level 24.
    //  !
    //  ! zh-CN:
    //  !
    //  ! 依赖库 log4j (第一版本) 是易受攻击的 (含 5 项漏洞):
    //  ! - CVE-2022-23307, 评分: 8.8
    //  ! - CVE-2022-23305, 评分: 9.8
    //  ! - CVE-2022-23302, 评分: 8.8
    //  ! - CVE-2021-4104, 评分: 7.5
    //  ! - CVE-2019-17571, 评分: 9.8
    //  ! 但 log4j 第二版本要求安卓 API 级别不低于 26,
    //  ! 与最低 API 级别为 24 的当前项目无法兼容.
    // Log4j
    implementation(libs.log4j)

    // Android Logging Log4j
    implementation(libs.android.logging.log4j)

    // Preference
    implementation(libs.preference.ktx)

    // RootShell
    // implementation("com.github.Stericson:RootShell:1.6")
    implementation(project(":libs:root-shell-1_6"))

    // JDeferred
    implementation(libs.jdeferred)

    // Rx
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // Device Names
    implementation(libs.android.device.names)

    // Version Compare
    implementation(libs.versioncompare)

    // Terminal Emulator
    implementation(project(":libs:jackpal-androidterm-libtermexec-1_0"))
    implementation(project(":libs:jackpal-androidterm-emulatorview-1_0_42"))
    implementation(project(":libs:jackpal-androidterm-1_0_70"))

    // Dex
    implementation(files("$rootDir/libs/com-android-dx-1_14.jar"))
    implementation(files("$rootDir/libs/com-legacy-android-dx-1_7_0.jar"))

    // OpenCV
    implementation(project(":libs:org-opencv-4_8_0"))

    // Image Quantization
    implementation(project(":libs:imagequant"))

    // Android Job
    implementation(project(":libs:android-job-simplified-1_4_3"))

    // AndroidX Work Runtime
    implementation(libs.work.runtime)

    // APK Parser
    // implementation("com.jaredrummler:apk-parser:1.0.2")
    // implementation("com.github.AndroidDeveloperLB:apk-parser:6")
    implementation(project(":modules:apk-parser"))

    // Prism4j
    implementation(files("$rootDir/libs/prism4j-2_0_0.jar"))
    implementation(files("$rootDir/libs/prism4j-bundler-2_0_0.jar"))
    implementation(project(":libs:markwon-core-4_6_2"))
    implementation(project(":libs:markwon-syntax-highlight-4_6_2"))

    // Rhino
    implementation(files("$rootDir/libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar"))

    // Tasker Plugin
    implementation(project(":libs:android-spackle-9_0_0"))
    implementation(project(":libs:android-assertion-9_0_0"))
    implementation(project(":libs:android-plugin-client-sdk-for-locale-9_0_0"))

    // JavaMail for Android
    implementation(files("$rootDir/libs/javamail-android/activation.jar"))
    implementation(files("$rootDir/libs/javamail-android/additionnal.jar"))
    implementation(files("$rootDir/libs/javamail-android/mail.jar"))

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // ARSCLib
    implementation(libs.arsclib)

    // Toaster
    implementation(libs.toaster)
    implementation(libs.easywindow)

    // Pinyin4j
    implementation(libs.pinyin4j)

    // Jieba Analysis (zh-CN: 结巴分词)
    // implementation("com.huaban:jieba-analysis:1.0.2")
    implementation(project(":modules:jieba-analysis"))

    // Tiny Sign
    implementation(files("$rootDir/libs/tiny-sign-0_9.jar"))

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.rxjava2)
    ksp(libs.room.compiler)

    // ApkSig
    // implementation("com.android.tools.build:apksig:8.7.3")

    // ApkSigner
    implementation(project(":modules:apk-signer"))

    // Prov
    implementation(libs.prov)

    // MQTT
    implementation(libs.paho.client.mqttv3)
    implementation(libs.paho.android.service)

    // Jsoup
    implementation(libs.jsoup)

    // Material Date Time Picker
    implementation(project(":modules:material-date-time-picker"))

    // ICU4J
    implementation(libs.icu4j)

    // R8
    implementation(libs.r8)

    // Plugin API: Common API
    implementation(project(":plugin-api:common-plugin-api"))

    // Plugin API: Node.js API
    implementation(project(":plugin-api:nodejs-api"))

    // Plugin API: Paddle OCR API
    implementation(project(":plugin-api:paddle-ocr-api"))

    // Plugin API: Paddle OCR Engine
    implementation(project(":plugin-api:paddle-ocr-engine"))

    // Plugin API: Rapid OCR Engine
    implementation(project(":plugin-api:rapid-ocr-engine"))
}

dependencies /* MIME */ {
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! Only for Android API 26 (8.0) [O] and above.
    //  ! zh-CN: 仅适用于安卓 API 级别 26 (8.0) [O] 及以上.
    // Apache Tika Core
    // implementation("org.apache.tika:tika-core:2.9.2")

    // MIME Util
    // implementation("eu.medsea.mimeutil:mime-util:2.1.3")
    implementation(files("$rootDir/libs/mime-util-2_1_3.jar"))
}

dependencies /* Test */ {
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.junit.jupiter)
}

dependencies /* Annotations */ {
    // Android Annotations
    implementation(libs.android.annotations.api)
    implementation(libs.annotation)
    ksp(libs.android.annotations)

    // JCIP Annotations
    implementation(libs.jcip.annotations)

    // EventBus
    implementation(libs.eventbus)
}

dependencies /* AppCompat */ {
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! To check the releases for Appcompat library,
    //  ! visit https://developer.android.com/jetpack/androidx/releases/appcompat.
    //  ! zh-CN:
    //  ! 查看 Appcompat 库的发行版本,
    //  ! 可访问 https://developer.android.com/jetpack/androidx/releases/appcompat.
    implementation(libs.appcompat)

    // AppCompat for legacy views (such as JsTextViewLegacy)
    implementation(project(":libs:androidx-appcompat-1_0_2")) {
        setVersion("1.0.2")
    }
}

dependencies /* Material Dialogs */ {
    // TODO by SuperMonster003 on Feb 5, 2022.
    //  ! Upgrade to 3.3.0 (more difficult than expected).
    //  ! zh-CN: 升级至 3.3.0 (实际难度超出预期较多).
    //  # val configuration: (ExternalModuleDependency).() -> Unit = {
    //  #     version {
    //  #         prefer("0.9.6.0")
    //  #         because("Not ready to update to version 3.3.0 yet")
    //  #     }
    //  # }
    //  # configuration.let { cfg ->
    //  #     implementation("com.afollestad.material-dialogs:core", cfg)
    //  #     implementation("com.afollestad.material-dialogs:commons", cfg)
    //  # }
    // Material Dialogs
    implementation(project(":modules:material-dialogs"))
    implementation(libs.materialprogressbar)
}

dependencies /* Layout */ {
    // Expandable Layout
    // implementation("com.github.aakira:expandable-layout:1.6.0")
    // implementation(project(":libs:expandable-layout-1_6_0"))
    implementation(project(":modules:expandable-layout"))

    implementation(project(":modules:expandable-recyclerview"))

    implementation(project(":modules:recyclerview-flexibledivider"))
}

dependencies /* View */ {
    // RoundedImageView
    implementation(libs.roundedimageview)

    // CircleImageView
    implementation(libs.circleimageview)

    // Animated SVG
    implementation(libs.animated.svg.view)
}

dependencies /* GitHub API */ {
    // GitHub API
    implementation(files("$rootDir/libs/github-api-1_306.jar"))

    // @Hint by SuperMonster003 on Sep 25, 2025.
    //  ! Strict version: "2.8.0".
    //  ! Compatibility for Android API Level < 26 (Android 8.0) [O].
    //  ! Exception on newer versions: 'NoClassDefFoundError: org.apache.commons.io.IObuildUtils'.
    // Commons IO
    implementation(libs.commons.io)

    // Jackson Databind
    implementation(libs.jackson.databind)

    // Desugar
    coreLibraryDesugaring(libs.desugar)
}

dependencies /* MLKit */ {
    // OCR
    implementation(libs.text.recognition.chinese)

    // Barcode
    implementation(libs.barcode.scanning)
}

dependencies /* OpenCC */ {
    // OpenCC
    // implementation("com.github.qichuan:android-opencc:1.2.0")
    implementation(libs.opencc)
}

dependencies /* Auto.js Extensions */ {
    // Settings Compat
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation("com.github.hyb1996:settingscompat:1.1.5")

    // Enhanced Floaty
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation("com.github.hyb1996:EnhancedFloaty:0.31")

    // MultiLevelListView
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation("com.github.hyb1996:android-multi-level-listview:1.1")

    // Auto.js APK Builder
    // @Integrated by SuperMonster003 on Mar 30, 2023.
    //  # implementation(project(":libs:Autojs-ApkBuilder-1_0_3"))

    // Extracted from com.github.hyb1996:MutableTheme:1.0.0
    implementation(libs.recyclerview)
    implementation(libs.circularreveal)
    // @Legacy com.jrummyapps:colorpicker:2.1.7
    // @Integrated by SuperMonster003 on Mar 25, 2025.
    //  # implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation(project(":modules:color-picker"))
}

dependencies /* Build Logic */ {
    // Version Codes Generator
    ksp(libs.ksp.version.codes.processor)
}

dependencies /* Archived */ {
    // @Comment by SuperMonster003 on May 19, 2022.
    //  ! It is no longer necessary to declare a dependency on the stdlib library in any Kotlin Gradle project.
    //  ! The dependency is added by default.
    //  ! See https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library.
    //  ! zh-CN:
    //  ! 已无需在 Kotlin Gradle 项目中显式声明标准库 (stdlib).
    //  ! 相关依赖已默认被添加.
    //  ! 参阅 https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library.
    // Kotlin
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")

    // @Comment by SuperMonster003 on Jun 1, 2022.
    //  ! Not necessary for current project but worth keeping the trace.
    //  ! zh-CN: 于当前项目已不再需要, 但依然值得留存其踪迹 (以备不时之需).
    // Google Guava
    // implementation("com.google.guava:guava:31.1-jre")

    // @Comment by SuperMonster003 on Apr 9, 2024.
    //  ! It was ever imported and used for MediaType constants.
    //  ! zh-CN: 曾用于 MediaType 常量的导入及使用.
    // Javax WS RS API (Java API for RESTful Web Services)
    // implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")

    // @Hint by SuperMonster003 on Jan 11, 2026.
    //  ! The AutoJs6 core project has deprecated the Local Broadcast Manager library,
    //  ! replaced by LiveData and SharedFlow.
    //  ! It is only retained for dependencies of certain libraries (such as MQTT).
    //  ! zh-CN:
    //  ! AutoJs6 核心项目已弃用 Local Broadcast Manager 库,
    //  ! 由 LiveData 及 SharedFlow 替代.
    //  ! 仅用于某些库 (如 MQTT) 的依赖而保留.
    // Local Broadcast Manager
    implementation(libs.localbroadcastmanager)
}

dependencies /* Reserved for auto append by IDE */ {

}

android {

    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = applicationId

        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget

        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val explicitInstrumentationClass =
            providers.gradleProperty("android.testInstrumentationRunnerArguments.class").orNull.isNullOrBlank().not()
        if (nodeJsFocusedGateInstrumentationClassArgument != null && !explicitInstrumentationClass) {
            testInstrumentationRunnerArguments["class"] = nodeJsFocusedGateInstrumentationClassArgument
            testInstrumentationRunnerArguments["autojs.nodejs.test.deviceProfile"] = "sony"
            if (nodeJsRequestedFocusedGate == "verifyNodeLiveAccessibilityImageGate") {
                testInstrumentationRunnerArguments["autojs.nodejs.accessibilitySmoke"] = "true"
            }
        } else if (nodeJsFuzzerReplayRunnerClass != null && !explicitInstrumentationClass) {
            testInstrumentationRunnerArguments["class"] = nodeJsFuzzerReplayRunnerClass
        } else if (nodeJsTestDeviceProfile != null && !explicitInstrumentationClass) {
            testInstrumentationRunnerArguments["class"] = nodeJsTestDeviceProfileClasses.joinToString(",")
            testInstrumentationRunnerArguments["autojs.nodejs.test.deviceProfile"] = nodeJsTestDeviceProfile
        }
        if (nodeJsTestDeviceProfile in setOf("v1_1_alpha", "v1_1_beta")) {
            fun alphaStressArgument(name: String, defaultValue: String): String =
                providers.gradleProperty(name).orElse(defaultValue).get()
            val defaultLeakIterations = if (nodeJsTestDeviceProfile == "v1_1_beta") "20" else "12"
            val defaultImageIterations = if (nodeJsTestDeviceProfile == "v1_1_beta") "8" else "6"
            val defaultFaultIterations = if (nodeJsTestDeviceProfile == "v1_1_beta") "5" else "3"
            val defaultBridgeStressIterations = if (nodeJsTestDeviceProfile == "v1_1_beta") "12" else "8"
            testInstrumentationRunnerArguments["autojs.nodejs.leak.iterations"] =
                alphaStressArgument("autojs.nodejs.leak.iterations", defaultLeakIterations)
            testInstrumentationRunnerArguments["autojs.nodejs.leak.imageIterations"] =
                alphaStressArgument("autojs.nodejs.leak.imageIterations", defaultImageIterations)
            testInstrumentationRunnerArguments["autojs.nodejs.leak.faultIterations"] =
                alphaStressArgument("autojs.nodejs.leak.faultIterations", defaultFaultIterations)
            testInstrumentationRunnerArguments["autojs.nodejs.bridgeStress.iterations"] =
                alphaStressArgument("autojs.nodejs.bridgeStress.iterations", defaultBridgeStressIterations)
        }
        testInstrumentationRunnerArguments["autojs.nodejs.fuzzerBudget"] = nodeJsFuzzerBudget
        nodeJsFuzzerReplaySeed?.let { testInstrumentationRunnerArguments["autojs.nodejs.fuzzerSeed"] = it }

        multiDexEnabled = true

        if (isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty()) {
            ndk.abiFilters += nodeJsBridgeNativeRuntimeAbis
            externalNativeBuild {
                cmake {
                    abiFilters += nodeJsBridgeNativeRuntimeAbis
                    arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=${if (nodeJsEmbeddedProbeLifecycleEnabled) "ON" else "OFF"}"
                    arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=${if (nodeJsEmbeddedScriptEnabled) "ON" else "OFF"}"
                    arguments += "-DAUTOJS6_NODE_RUNTIME_SLOT=$nodeJsRuntimeSlot"
                    arguments += "-DAUTOJS6_NODE_INCLUDE_DIR=${nodeJsRuntimeSlotHeaderDir.nodeJsCmakePath()}"
                    arguments += "-DAUTOJS6_NODE_LIBRARY_ROOT=${nodeJsRuntimeSlotJniLibsSourceDir.nodeJsCmakePath()}"
                    cppFlags += "-std=c++20"
                }
            }
        }

        buildConfigField("String", "VERSION_DATE", "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")
        buildConfigField("String", "VSCODE_EXT_REQUIRED_VERSION", "\"${versions.vscodeExtRequiredVersion}\"")
        buildConfigField("String", "NODEJS_IDENTITY_PROFILE", "\"$nodeJsIdentityProfile\"")
        buildConfigField("String", "NODEJS_APPLICATION_ID_SUFFIX", "\"$nodeJsApplicationIdSuffix\"")
        buildConfigField("String", "NODEJS_RUNTIME_BACKEND", "\"$effectiveNodeJsRuntimeBackend\"")
        buildConfigField("String", "NODEJS_RUNTIME_SLOT", "\"$nodeJsRuntimeSlot\"")
        buildConfigField("String", "NODEJS_RUNTIME_SLOT_TARGET_VERSION", "\"$nodeJsRuntimeSlotTargetVersion\"")
        buildConfigField("boolean", "NODEJS_RUNTIME_SLOT_BUILD_ONLY", "true")
        buildConfigField("boolean", "NODEJS_RUNTIME_SLOT_USER_SELECTABLE", "false")
        buildConfigField("String", "NODEJS_RELEASE_FLAVOR", "\"$nodeJsReleaseFlavor\"")
        buildConfigField("int", "NODEJS_EMBEDDED_PROBE_LEVEL", "$nodeJsEmbeddedProbeLevel")
        buildConfigField("long", "NODEJS_EMBEDDED_PROBE_TIMEOUT_MS", "${nodeJsEmbeddedProbeTimeoutMs}L")
        buildConfigField("boolean", "NODEJS_EMBEDDED_PROBE_LIFECYCLE_BUILD_ENABLED", "$nodeJsEmbeddedProbeLifecycleEnabled")
        buildConfigField("boolean", "NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED", "$nodeJsEmbeddedScriptEnabled")
        buildConfigField("boolean", "NODEJS_ESM_EXPERIMENTAL_ENABLED", "$nodeJsEsmExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED", "$nodeJsDynamicImportLocalEnabled")
        buildConfigField("boolean", "NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED", "$nodeJsDynamicImportEnabled")
        buildConfigField("boolean", "NODEJS_NETWORK_EXPERIMENTAL_ENABLED", "$nodeJsNetworkExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED", "$nodeJsRawNetworkModulesExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED", "$nodeJsWorkerThreadsExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_CHILD_PROCESS_EXPERIMENTAL_ENABLED", "$nodeJsChildProcessExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED", "$nodeJsJavaInteropExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_PROFILE_PRO_ENABLED", "$nodeJsProfileProEnabled")
        buildConfigField("boolean", "NODEJS_PROFILE_DESKTOP_ENABLED", "$nodeJsProfileDesktopEnabled")
        buildConfigField("boolean", "NODEJS_PROFILE_DEBUG_UNSAFE_LAB_ENABLED", "$nodeJsProfileDebugUnsafeLabEnabled")
        buildConfigField("boolean", "NODEJS_RELAXED_CAPABILITIES_ENABLED", "$nodeJsRelaxedCapabilitiesEnabled")
        buildConfigField("boolean", "NODEJS_TIMELINE_TRACE_ENABLED", "$nodeJsTimelineTraceEnabled")
        buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")
        resValue("string", "node_identity_application_id", nodeJsEffectiveApplicationId)
        resValue("string", "node_identity_app_label", nodeJsAppLabel)

        javaCompileOptions {
            annotationProcessorOptions {
                mapOf(
                    "resourcePackageName" to (this@defaultConfig.applicationId ?: globalApplicationId),
                    "androidManifestFile" to ("$projectDir/src/main/AndroidManifest.xml")
                ).let { arguments(it) }
            }
        }
    }

    flavorDimensions.add(flavorDimension)

    productFlavors {

        create(flavorNameApp) {
            dimension = flavorDimension
            if (nodeJsApplicationIdSuffix.isNotEmpty()) {
                applicationIdSuffix = nodeJsApplicationIdSuffix
            }
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName
            buildConfigField("String", "CHANNEL", "\"$flavorNameApp\"")
            buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")
            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameApp,
                    "appName" to "@string/node_identity_app_label",
                    "intentCategory" to "android.intent.category.LAUNCHER",
                    "intentCategoryInrt" to "android.intent.category.DEFAULT",
                    "authorities" to "org.autojs.autojs6.fileprovider",
                    "icon" to "@mipmap/ic_app_launcher_adaptive",
                )
            )
        }

        create(flavorNameInrt) {
            dimension = flavorDimension
            applicationIdSuffix = "$nodeJsApplicationIdSuffix.$flavorNameInrt"
            minSdk = versions.sdkVersionMin
            targetSdk = versions.sdkVersionTargetInrt
            compileSdk = versions.sdkVersionCompile
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName

            buildConfigField("String", "CHANNEL", "\"$flavorNameInrt\"")
            buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "true")

            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameInrt,
                    "appName" to "AutoJs6.$flavorNameInrt",
                    "intentCategory" to "android.intent.category.DEFAULT",
                    "intentCategoryInrt" to "android.intent.category.LAUNCHER",
                    "authorities" to "org.autojs.autojs6.$flavorNameInrt.fileprovider",
                    "icon" to "@mipmap/ic_launcher",
                )
            )

            // @Reference to kkevsekk1/AutoX (https://github.com/kkevsekk1/AutoX) by SuperMonster003 on Nov 16, 2023.
            //  ! https://github.com/kkevsekk1/AutoX/blob/a6d482189291b460c3be60970b74c5321d26e457/inrt/build.gradle.kts#L93
            // noinspection ChromeOsAbiSupport
            if (!isNodeJsNativeRuntimeRequested) {
                ndk.abiFilters += ""
            }
        }

        androidResources {
            if (isAppAssembleTaskRequested) {
                ignoreAssetsPatterns.addAll(listOf(".idea", "declarations", "sample/declarations"))
            }
            if (isInrtAssembleTaskRequested) {
                // @Hint by SuperMonster003 on Oct 16, 2023.
                //  ! Runtime assets will be copied from flavor "app"
                //  ! while building an apk on org.autojs.autojs.ui.project.BuildActivity.
                //  ! zh-CN:
                //  ! 类 org.autojs.autojs.ui.project.BuildActivity 构建 APK 时,
                //  ! 运行时资产文件 (runtime assets) 将由名为 "app" 的 Gradle flavor 作为源地址进行复制.
                ignoreAssetsPatterns.addAll(emptyList())
            }
        }

    }

    sourceSets {

        // @Hint by LZX284 (https://github.com/LZX284) on Nov 15, 2023.
        //  ! The assets file is divided into three directories according to different flavors.
        //  ! But the files are not actually moved to avoid conflicts with the latest modifications.
        //  ! zh-CN (translated by SuperMonster003 on Jul 26, 2024):
        //  ! 资产文件根据不同的 Gradle flavor 被隔离为三个不同的目录.
        //  ! 不过真实文件并没有进行移动, 以避免与最新的项目修改发生冲突.
        //  !
        // @Hint by SuperMonster003 on Nov 16, 2023.
        //  ! The assets division idea was accepted, and it wouldn't hurt to try. :)
        //  ! zh-CN: 资产隔离的想法可以被接受, 毕竟试一下也无妨. [笑脸符号]

        // @Archived by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 7, 2026.
        //  # getByName("main") {
        //  #     assets.srcDirs("src/main/assets")
        //  # }
        //  # getByName("release") {
        //  #     java.srcDirs("src/release/java")
        //  # }
        //  # getByName("debug") {
        //  #     java.srcDirs("src/debug/java")
        //  # }
        //  # getByName(flavorNameApp) {
        //  #     assets.srcDirs("src/main/assets-$flavorNameApp")
        //  # }
        //  # getByName(flavorNameInrt) {
        //  #     assets.srcDirs("src/main/assets-$flavorNameInrt")
        //  # }
        //  !
        getByName("main") {
            assets.directories.add("src/main/assets")
            jniLibs.setSrcDirs(listOf(nodeJsRuntimeSlotGeneratedJniLibsDir.get().asFile))
        }
        getByName("release") {
            java.directories.add("src/release/java")
        }
        getByName("debug") {
            java.directories.add("src/debug/java")
        }
        getByName(flavorNameApp) {
            assets.directories.add("src/main/assets-$flavorNameApp")
        }
        getByName(flavorNameInrt) {
            assets.directories.add("src/main/assets-$flavorNameInrt")
        }
        getByName("androidTest") {
            assets.directories.add(nodeJsAndroidTestExamplesRootDir.absolutePath)
        }

    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        listOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.*",
            "META-INF/LICENSE-notice.*",
            "META-INF/license.*",
            "META-INF/NOTICE",
            "META-INF/NOTICE.*",
            "META-INF/notice.*",
            "META-INF/ASL2.0",
            "META-INF/*.kotlin_module",
            "lib/x86/libc++_shared.so",
            "lib/x86_64/libc++_shared.so",
            "lib/armeabi-v7a/libc++_shared.so",
            "lib/arm64-v8a/libc++_shared.so",
            "lib/armeabi/libc++_shared.so",
        ).let { resources.pickFirsts.addAll(it) }

        listOf(
            "com/**/*",
            "frameworks/**/*",
            "junit/**/*",
            "LICENSE-junit.txt",
            "spec.txt",
            "EmojiReference.txt",
        ).let { resources.excludes.addAll(it) }

        if (isInrtTaskRequested) {
            listOf(
                "**/prob_emit.txt", // Jieba Analysis (zh-CN: 结巴分词)
                "**/dict-chinese-*.db.gzip", // Jieba Analysis (zh-CN: 结巴分词)
            ).let { resources.excludes.addAll(it) }
        }

        jniLibs {
            // @Reference to kkevsekk1/AutoX (https://github.com/kkevsekk1/AutoX) by SuperMonster003 on Nov 16, 2023.
            //  ! https://github.com/kkevsekk1/AutoX/blob/a6d482189291b460c3be60970b74c5321d26e457/inrt/build.gradle.kts#L91
            if (!isNodeJsNativeRuntimeRequested) {
                val allNativeLibAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")
                excludes += allNativeLibAbis.flatMap { abi ->
                    listOf("lib/$abi/**", "**/$abi/**")
                }
            } else if (isNodeJsHostBridgeBuildRequested) {
                val unsupportedNodeJsAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") - nodeJsBridgeNativeRuntimeAbis.toSet()
                excludes += unsupportedNodeJsAbis.flatMap { abi ->
                    listOf("lib/$abi/**", "**/$abi/**")
                }
            }
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        if (signs.isValid) {
            create(buildTypeRelease) {
                storeFile = signs.properties["storeFile"]?.let { file(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val proguardFiles = arrayOf<Any>(
            // @Archived by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 7, 2026.
            //  # getDefaultProguardFile("proguard-android.txt"),
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        val niceSigningConfig = takeIf { signs.isValid }?.let {
            signingConfigs.getByName(buildTypeRelease)
        }
        debug {
            isMinifyEnabled = getByName(buildTypeRelease).isMinifyEnabled
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        viewBinding = true

        // @Hint by SuperMonster003 on Aug 14, 2023.
        //  ! Substitution of "android.defaults.buildfeatures.buildconfig=true"
        //  ! zh-CN: "android.defaults.buildfeatures.buildconfig=true" 的替代方案
        buildConfig = true
        resValues = true

        // @Archived by SuperMonster003 on Sep 23, 2024.
        //  ! Jetpack Compose
        //  # compose = true
        //  # composeOptions {
        //  #     kotlinCompilerExtensionVersion = "1.5.12"
        //  # }
    }

    if (isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty()) {
        externalNativeBuild {
            cmake {
                path = nodeJsBridgeCmakeFile ?: file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    splits {
        // Configures multiple APKs based on ABI.
        abi {
            // Enables building multiple APKs per ABI.
            isEnable = !isInrtAssembleTaskRequested
            // By default, all ABIs are included, so use reset() and include to specify that we only
            // want APKs for x86 and x86_64.
            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()
            // Specifies a list of ABIs that Gradle should create APKs for.
            val splitAbis = when {
                isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty() -> nodeJsBridgeNativeRuntimeAbis
                else -> listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86", "armeabi")
            }
            include(*splitAbis.toTypedArray())
            // Specifies that we do not want to also generate a universal APK that includes all ABIs.
            isUniversalApk = true
        }
    }

}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val mergeAssetsTaskName = "merge${variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Assets"
        val isInrtVariant = variantName.startsWith(flavorNameInrt, ignoreCase = true)

        tasks.matching { it.name == mergeAssetsTaskName }.configureEach {
            doLast {
                val reflectedOutputDir = when (val dir = javaClass.methods.firstOrNull {
                    it.name == "getOutputDir" && it.parameterTypes.isEmpty()
                }?.invoke(this)) {
                    is File -> dir
                    is Directory -> dir.asFile
                    else -> null
                }

                val includes = when (isInrtVariant) {
                    true -> listOf(
                        "doc/**/*",
                        "docs/**/*",
                        "editor/**/*",
                        "fonts/**/*",
                        "indices/**/*",
                        "js-beautify/**/*",
                        "mlkit-google-ocr-models/**/*",
                        "mlkit_barcode_models/**/*",
                        "models/**/*",
                        "modules/**/*.d.ts",
                        "modules/**/*.md",
                        "modules/obsolete/**/*",
                        "openccdata/**/*",
                        "project/**/*",
                        "sample/**/*",
                        "stored-locales/**/*",
                        "android-devices.db",
                        "autojs.keystore",
                        "**/prob_emit.txt", // Jieba Analysis
                        "**/dict-chinese-*.db.gzip", // Jieba Analysis
                    )
                    else -> listOf(
                        "declarations/**/*",
                        "sample/declarations/**/*",
                        "modules/obsolete/**/*",
                    )
                }

                listOfNotNull(
                    reflectedOutputDir,
                    layout.buildDirectory.dir("intermediates/assets/$variantName/$mergeAssetsTaskName").get().asFile,
                )
                    .map { it.absoluteFile.normalize() }
                    .distinctBy { it.path }
                    .filter { it.isDirectory }
                    .forEach { outputDir ->
                        delete(
                            fileTree(
                                mapOf(
                                    "dir" to outputDir,
                                    "includes" to includes,
                                )
                            )
                        )
                    }
            }
        }

        variant.outputs.forEach { output ->
            val architecture = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val outputFileNameProperty = output.javaClass.methods.firstOrNull {
                it.name == "getOutputFileName" && it.parameterTypes.isEmpty()
            }?.invoke(output) as? Property<*>

            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as? Property<String>)?.set(
                variant.applicationId.zip(output.versionName) { appId, versionName ->
                    val autojs = appId.substringAfterLast('.')
                    val version = versionName.replace("\\s".toRegex(), "-")
                    val extension = utils.FILE_EXTENSION_APK
                    "$autojs-v$version-$architecture.$extension".lowercase()
                }
            )
        }
    }
}

extra.apply {
    set("nodeJsBuild.taskNameAssembleInrtFullNodeRelease", taskNameAssembleInrtFullNodeRelease)
    set("nodeJsBuild.isNodeFuzzerSeedReplayRequested", isNodeFuzzerSeedReplayRequested)
    set("nodeJsBuild.nodeJsReleaseFlavor", nodeJsReleaseFlavor)
    set("nodeJsBuild.nodeJsReleaseExpectedAbis", nodeJsReleaseExpectedAbis)
    set("nodeJsBuild.effectiveNodeJsRuntimeBackend", effectiveNodeJsRuntimeBackend)
    set("nodeJsBuild.isNodeJsNativeRuntimeRequested", isNodeJsNativeRuntimeRequested)
    set("nodeJsBuild.isNodeJsHostBridgeBuildRequested", isNodeJsHostBridgeBuildRequested)
    set("nodeJsBuild.nodeJsTestDeviceProfile", nodeJsTestDeviceProfile)
    set("nodeJsBuild.nodeJsFuzzerBudget", nodeJsFuzzerBudget)
    set("nodeJsBuild.nodeJsFuzzerSeedFile", nodeJsFuzzerSeedFile)
    set("nodeJsBuild.nodeJsFuzzerReplayRunnerClass", nodeJsFuzzerReplayRunnerClass)
    set("nodeJsBuild.nodeJsFuzzerReplaySeed", nodeJsFuzzerReplaySeed)
    set("nodeJsBuild.nodeJsTestDeviceProfileClasses", nodeJsTestDeviceProfileClasses)
    set("nodeJsBuild.apkFileExtension", utils.FILE_EXTENSION_APK)
}

apply(from = "node-build-tasks.gradle.kts")

if (isInrtAssembleTaskRequested) {
    tasks.matching { it.name == "lintVitalAnalyzeInrtRelease" }.configureEach {
        enabled = false
    }
}

apply(from = "node-gradle-scripts.gradle.kts")

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"

        // @Hint by SuperMonster003 on May 18, 2022.
        //  ! Comment or remove this option if you are tired of plenty of warnings. :)
        //  ! zh-CN: 注释或移除此选项可避免过多警告消息造成的困扰. [笑脸符号]
        // options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    register<Copy>("appendDigestToReleasedFiles") {
        listOf(flavorNameApp, flavorNameInrt).forEach { flavorName ->
            val src = "$flavorName/$buildTypeRelease"
            val dst = "${src}s"
            val ext = utils.FILE_EXTENSION_APK

            if (!file(src).isDirectory) {
                return@forEach
            }

            from(src); into(dst); include("*.$ext")

            rename { name ->
                utils.digestCRC32(file("${src}/$name")).let { digest ->
                    name.replace(Regex("^(.+?)(\\.$ext)$"), "$1-$digest$2")
                }
            }

            doLast { println("Destination: ${file(dst)}") }
        }
    }
}

extra {
    versions.handleIfNeeded(project, flavorNameApp, listOf(buildTypeDebug, buildTypeRelease))
}
