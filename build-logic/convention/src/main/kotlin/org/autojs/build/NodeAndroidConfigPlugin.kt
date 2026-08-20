@file:Suppress("unused")

package org.autojs.build

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import java.io.File

/**
 * Convention plugin: Embedded Node.js related Android DSL configuration for the app module.
 *
 * zh-CN: 约定插件: 为 app 模块配置 Embedded Node.js 相关的 Android DSL.
 *
 * - `id`: "org.autojs.build.node-android-config"
 * - `implementationClass`: "org.autojs.build.NodeAndroidConfigPlugin"
 * - `displayName`: "AutoJs6 Node Android Config Convention Plugin"
 * - `description`: "Configures Embedded Node.js related Android DSL for the app module."
 *
 * Apply this plugin to the app module's `build.gradle.kts`:
 *
 * zh-CN: 在 app 模块的 `build.gradle.kts` 中应用此插件:<br>
 *
 * ```kts
 * plugins {
 *     id("org.autojs.build.node-android-config")
 *     id("com.android.application")
 * }
 * ```
 *
 * @Hint: this plugin must be listed BEFORE "com.android.application" in the `plugins` block.
 *  ! All configuration is deferred via [Project.afterEvaluate], because the
 *  ! "nodeJsRuntimeConfig.*" extra properties this plugin reads are set in the middle of the
 *  ! build script, while the `plugins` block is applied at the very beginning of it.
 *  ! Gradle runs afterEvaluate callbacks in registration order, and AGP creates its variants
 *  ! from such a callback registered while "com.android.application" is being applied.
 *  ! Listing this plugin first therefore guarantees this callback runs before AGP locks the DSL,
 *  ! which reproduces the original timing of `apply(from = "node-android-config.gradle.kts")`
 *  ! (evaluated after the `android { }` block, still before AGP consumed the DSL).
 *  ! zh-CN: 此插件在 `plugins` 块中必须列于 "com.android.application" 之前.
 *  ! 全部配置经由 [Project.afterEvaluate] 延迟执行, 因为本插件读取的 "nodeJsRuntimeConfig.*"
 *  ! extra 属性是在构建脚本中部设置的, 而 `plugins` 块在脚本最开头就已应用.
 *  ! Gradle 按注册顺序执行 afterEvaluate 回调, 而 AGP 在应用 "com.android.application" 时
 *  ! 注册的回调中创建其 variant. 因此将此插件列在前面可保证本回调先于 AGP 锁定 DSL 之前执行,
 *  ! 从而复现 `apply(from = "node-android-config.gradle.kts")` 的原有时机
 *  ! (在 `android { }` 块之后求值, 且仍在 AGP 消费 DSL 之前).
 */
class NodeAndroidConfigPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.afterEvaluate {
            plugins.withId("com.android.application") {
                configureNodeAndroid(project)
            }
        }
    }

    private fun configureNodeAndroid(project: Project) {
        val flavorNameApp = project.nodeJsAndroidConfigExtra<String>("flavorNameApp")
        val flavorNameInrt = project.nodeJsAndroidConfigExtra<String>("flavorNameInrt")
        val isAppAssembleTaskRequested = project.nodeJsAndroidConfigExtra<Boolean>("isAppAssembleTaskRequested")
        val isInrtAssembleTaskRequested = project.nodeJsAndroidConfigExtra<Boolean>("isInrtAssembleTaskRequested")
        @Suppress("UNUSED_VARIABLE")
        val isInrtTaskRequested = project.nodeJsAndroidConfigExtra<Boolean>("isInrtTaskRequested")
        val nodeJsIdentityProfile = project.nodeJsAndroidConfigExtra<String>("nodeJsIdentityProfile")
        val nodeJsApplicationIdSuffix = project.nodeJsAndroidConfigExtra<String>("nodeJsApplicationIdSuffix")
        val nodeJsEffectiveApplicationId = project.nodeJsAndroidConfigExtra<String>("nodeJsEffectiveApplicationId")
        val nodeJsAppLabel = project.nodeJsAndroidConfigExtra<String>("nodeJsAppLabel")
        val nodeJsReleaseFlavor = project.nodeJsAndroidConfigExtra<String>("nodeJsReleaseFlavor")
        val effectiveNodeJsRuntimeBackend = project.nodeJsAndroidConfigExtra<String>("effectiveNodeJsRuntimeBackend")
        val isNodeJsNativeRuntimeRequested = project.nodeJsAndroidConfigExtra<Boolean>("isNodeJsNativeRuntimeRequested")
        val isNodeJsHostBridgeBuildRequested = project.nodeJsAndroidConfigExtra<Boolean>("isNodeJsHostBridgeBuildRequested")
        val nodeJsBridgeNativeRuntimeAbis = project.nodeJsAndroidConfigExtra<List<String>>("nodeJsBridgeNativeRuntimeAbis")
        val nodeJsRuntimeSlot = project.nodeJsAndroidConfigExtra<String>("nodeJsRuntimeSlot")
        val nodeJsRuntimeSlotTargetVersion = project.nodeJsAndroidConfigExtra<String>("nodeJsRuntimeSlotTargetVersion")
        val nodeJsRuntimeSlotGeneratedJniLibsDir = project.nodeJsAndroidConfigExtra<Provider<Directory>>("nodeJsRuntimeSlotGeneratedJniLibsDir")
        val nodeJsRuntimeSlotHeaderDir = project.nodeJsAndroidConfigExtra<File>("nodeJsRuntimeSlotHeaderDir")
        val nodeJsRuntimeSlotJniLibsSourceDir = project.nodeJsAndroidConfigExtra<File>("nodeJsRuntimeSlotJniLibsSourceDir")
        val nodeJsAndroidTestExamplesRootDir = project.nodeJsAndroidConfigExtra<File>("nodeJsAndroidTestExamplesRootDir")
        val nodeJsEmbeddedProbeLevel = project.nodeJsAndroidConfigExtra<Int>("nodeJsEmbeddedProbeLevel")
        val nodeJsEmbeddedProbeTimeoutMs = project.nodeJsAndroidConfigExtra<Long>("nodeJsEmbeddedProbeTimeoutMs")
        val nodeJsEmbeddedProbeLifecycleEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsEmbeddedProbeLifecycleEnabled")
        val nodeJsEmbeddedScriptEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsEmbeddedScriptEnabled")
        val nodeJsEmbeddedPersistentRuntimeEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsEmbeddedPersistentRuntimeEnabled")
        val nodeJsEsmEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsEsmEnabled")
        val nodeJsDynamicImportEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsDynamicImportEnabled")
        val nodeJsNetworkEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsNetworkEnabled")
        val nodeJsRawNetworkModulesEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsRawNetworkModulesEnabled")
        val nodeJsWorkerThreadsEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsWorkerThreadsEnabled")
        val nodeJsChildProcessEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsChildProcessEnabled")
        val nodeJsJavaInteropEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsJavaInteropEnabled")
        val nodeJsProfileProEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileProEnabled")
        val nodeJsProfileDesktopEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileDesktopEnabled")
        val nodeJsProfileDebugUnsafeLabEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileDebugUnsafeLabEnabled")
        val nodeJsRelaxedCapabilitiesEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsRelaxedCapabilitiesEnabled")
        val nodeJsTimelineTraceEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsTimelineTraceEnabled")
        val nodeJsTestDeviceProfile = project.nodeJsAndroidConfigExtra<String?>("nodeJsTestDeviceProfile")
        val nodeJsFuzzerBudget = project.nodeJsAndroidConfigExtra<String>("nodeJsFuzzerBudget")
        val nodeJsFuzzerReplayRunnerClass = project.nodeJsAndroidConfigExtra<String?>("nodeJsFuzzerReplayRunnerClass")
        val nodeJsFuzzerReplaySeed = project.nodeJsAndroidConfigExtra<String?>("nodeJsFuzzerReplaySeed")
        val nodeJsBridgeCmakeFile = project.nodeJsAndroidConfigExtra<File?>("nodeJsBridgeCmakeFile")
        val nodeJsRequestedFocusedGate = project.nodeJsAndroidConfigExtra<String?>("nodeJsRequestedFocusedGate")
        val nodeJsFocusedGateInstrumentationClassArgument =
            project.nodeJsAndroidConfigExtra<String?>("nodeJsFocusedGateInstrumentationClassArgument")
        val nodeJsTestDeviceProfileClasses = project.nodeJsAndroidConfigExtra<List<String>>("nodeJsTestDeviceProfileClasses")

        val providers = project.providers

        project.extensions.configure<ApplicationExtension>("android") {
            defaultConfig {
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

                if (isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty()) {
                    ndk.abiFilters += nodeJsBridgeNativeRuntimeAbis
                    externalNativeBuild {
                        cmake {
                            abiFilters += nodeJsBridgeNativeRuntimeAbis
                            arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=${if (nodeJsEmbeddedProbeLifecycleEnabled) "ON" else "OFF"}"
                            arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=${if (nodeJsEmbeddedScriptEnabled) "ON" else "OFF"}"
                            arguments += "-DAUTOJS6_NODE_RUNTIME_SLOT=$nodeJsRuntimeSlot"
                            arguments += "-DAUTOJS6_NODE_INCLUDE_DIR=${nodeJsRuntimeSlotHeaderDir.nodeJsAndroidCmakePath()}"
                            arguments += "-DAUTOJS6_NODE_LIBRARY_ROOT=${nodeJsRuntimeSlotJniLibsSourceDir.nodeJsAndroidCmakePath()}"
                            cppFlags += "-std=c++20"
                        }
                    }
                }

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
                buildConfigField("boolean", "NODEJS_EMBEDDED_PERSISTENT_RUNTIME_ENABLED", "$nodeJsEmbeddedPersistentRuntimeEnabled")
                buildConfigField("boolean", "NODEJS_ESM_ENABLED", "$nodeJsEsmEnabled")
                buildConfigField("boolean", "NODEJS_DYNAMIC_IMPORT_ENABLED", "$nodeJsDynamicImportEnabled")
                buildConfigField("boolean", "NODEJS_NETWORK_ENABLED", "$nodeJsNetworkEnabled")
                buildConfigField("boolean", "NODEJS_RAW_NETWORK_MODULES_ENABLED", "$nodeJsRawNetworkModulesEnabled")
                buildConfigField("boolean", "NODEJS_WORKER_THREADS_ENABLED", "$nodeJsWorkerThreadsEnabled")
                buildConfigField("boolean", "NODEJS_CHILD_PROCESS_ENABLED", "$nodeJsChildProcessEnabled")
                buildConfigField("boolean", "NODEJS_JAVA_INTEROP_ENABLED", "$nodeJsJavaInteropEnabled")
                buildConfigField("boolean", "NODEJS_PROFILE_PRO_ENABLED", "$nodeJsProfileProEnabled")
                buildConfigField("boolean", "NODEJS_PROFILE_DESKTOP_ENABLED", "$nodeJsProfileDesktopEnabled")
                buildConfigField("boolean", "NODEJS_PROFILE_DEBUG_UNSAFE_LAB_ENABLED", "$nodeJsProfileDebugUnsafeLabEnabled")
                buildConfigField("boolean", "NODEJS_RELAXED_CAPABILITIES_ENABLED", "$nodeJsRelaxedCapabilitiesEnabled")
                buildConfigField("boolean", "NODEJS_TIMELINE_TRACE_ENABLED", "$nodeJsTimelineTraceEnabled")

                resValue("string", "node_identity_application_id", nodeJsEffectiveApplicationId)
                resValue("string", "node_identity_app_label", nodeJsAppLabel)
            }

            productFlavors.getByName(flavorNameApp) {
                if (nodeJsApplicationIdSuffix.isNotEmpty()) {
                    applicationIdSuffix = nodeJsApplicationIdSuffix
                }
                manifestPlaceholders["appName"] = "@string/node_identity_app_label"
            }

            productFlavors.getByName(flavorNameInrt) {
                applicationIdSuffix = "$nodeJsApplicationIdSuffix.$flavorNameInrt"
                if (!isNodeJsNativeRuntimeRequested) {
                    ndk.abiFilters += ""
                }
            }

            androidResources {
                if (isAppAssembleTaskRequested) {
                    ignoreAssetsPatterns.addAll(listOf(".idea", "declarations", "sample/declarations"))
                }
                if (isInrtAssembleTaskRequested) {
                    ignoreAssetsPatterns.addAll(emptyList())
                }
            }

            sourceSets {
                getByName("main") {
                    jniLibs.directories.clear()
                    jniLibs.directories.add(nodeJsRuntimeSlotGeneratedJniLibsDir.get().asFile.absolutePath)
                }
                getByName("androidTest") {
                    assets.directories.add(nodeJsAndroidTestExamplesRootDir.absolutePath)
                }
            }

            packaging {
                jniLibs {
                    val nodeRuntimeOwnedNativeLibraries = listOf(
                        "libnode.so",
                        "libautojs6-node.so",
                    )
                    fun nodeRuntimeExcludes(abis: List<String>): List<String> = abis.flatMap { abi ->
                        nodeRuntimeOwnedNativeLibraries.flatMap { library ->
                            listOf("lib/$abi/$library", "**/$abi/$library")
                        }
                    }
                    if (!isNodeJsNativeRuntimeRequested) {
                        val allNativeLibAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")
                        excludes += nodeRuntimeExcludes(allNativeLibAbis)
                    } else if (isNodeJsHostBridgeBuildRequested) {
                        val unsupportedNodeJsAbis =
                            listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") - nodeJsBridgeNativeRuntimeAbis.toSet()
                        excludes += nodeRuntimeExcludes(unsupportedNodeJsAbis)
                    }
                }
            }

            if (isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty()) {
                externalNativeBuild {
                    cmake {
                        path = nodeJsBridgeCmakeFile ?: project.file("src/main/cpp/CMakeLists.txt")
                    }
                }
            }

            splits {
                abi {
                    isEnable = !isInrtAssembleTaskRequested
                    reset()
                    val splitAbis = when {
                        isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty() -> nodeJsBridgeNativeRuntimeAbis
                        else -> listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86", "armeabi")
                    }
                    include(*splitAbis.toTypedArray())
                    isUniversalApk = true
                }
            }
        }

        if (isInrtAssembleTaskRequested) {
            project.tasks.matching { it.name == "lintVitalAnalyze${flavorNameInrt.nodeJsAndroidCapitalized()}Release" }.configureEach {
                enabled = false
            }
        }
    }

}

private fun nodeJsAndroidConfigExtraName(name: String): String = "nodeJsRuntimeConfig.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> Project.nodeJsAndroidConfigExtra(name: String): T =
    extensions.extraProperties.get(nodeJsAndroidConfigExtraName(name)) as T

private fun String.nodeJsAndroidCapitalized(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun File.nodeJsAndroidCmakePath(): String = absolutePath.replace('\\', '/')
