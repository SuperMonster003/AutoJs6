import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure

private fun nodeJsAndroidConfigExtraName(name: String): String = "nodeJsRuntimeConfig.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> nodeJsAndroidConfigExtra(name: String): T =
    extensions.extraProperties.get(nodeJsAndroidConfigExtraName(name)) as T

private val flavorNameApp = nodeJsAndroidConfigExtra<String>("flavorNameApp")
private val flavorNameInrt = nodeJsAndroidConfigExtra<String>("flavorNameInrt")
private val isAppAssembleTaskRequested = nodeJsAndroidConfigExtra<Boolean>("isAppAssembleTaskRequested")
private val isInrtAssembleTaskRequested = nodeJsAndroidConfigExtra<Boolean>("isInrtAssembleTaskRequested")
private val isInrtTaskRequested = nodeJsAndroidConfigExtra<Boolean>("isInrtTaskRequested")
private val nodeJsIdentityProfile = nodeJsAndroidConfigExtra<String>("nodeJsIdentityProfile")
private val nodeJsApplicationIdSuffix = nodeJsAndroidConfigExtra<String>("nodeJsApplicationIdSuffix")
private val nodeJsEffectiveApplicationId = nodeJsAndroidConfigExtra<String>("nodeJsEffectiveApplicationId")
private val nodeJsAppLabel = nodeJsAndroidConfigExtra<String>("nodeJsAppLabel")
private val nodeJsReleaseFlavor = nodeJsAndroidConfigExtra<String>("nodeJsReleaseFlavor")
private val effectiveNodeJsRuntimeBackend = nodeJsAndroidConfigExtra<String>("effectiveNodeJsRuntimeBackend")
private val isNodeJsNativeRuntimeRequested = nodeJsAndroidConfigExtra<Boolean>("isNodeJsNativeRuntimeRequested")
private val isNodeJsHostBridgeBuildRequested = nodeJsAndroidConfigExtra<Boolean>("isNodeJsHostBridgeBuildRequested")
private val nodeJsBridgeNativeRuntimeAbis = nodeJsAndroidConfigExtra<List<String>>("nodeJsBridgeNativeRuntimeAbis")
private val nodeJsRuntimeSlot = nodeJsAndroidConfigExtra<String>("nodeJsRuntimeSlot")
private val nodeJsRuntimeSlotTargetVersion = nodeJsAndroidConfigExtra<String>("nodeJsRuntimeSlotTargetVersion")
private val nodeJsRuntimeSlotGeneratedJniLibsDir = nodeJsAndroidConfigExtra<Provider<Directory>>("nodeJsRuntimeSlotGeneratedJniLibsDir")
private val nodeJsRuntimeSlotHeaderDir = nodeJsAndroidConfigExtra<File>("nodeJsRuntimeSlotHeaderDir")
private val nodeJsRuntimeSlotJniLibsSourceDir = nodeJsAndroidConfigExtra<File>("nodeJsRuntimeSlotJniLibsSourceDir")
private val nodeJsAndroidTestExamplesRootDir = nodeJsAndroidConfigExtra<File>("nodeJsAndroidTestExamplesRootDir")
private val nodeJsEmbeddedProbeLevel = nodeJsAndroidConfigExtra<Int>("nodeJsEmbeddedProbeLevel")
private val nodeJsEmbeddedProbeTimeoutMs = nodeJsAndroidConfigExtra<Long>("nodeJsEmbeddedProbeTimeoutMs")
private val nodeJsEmbeddedProbeLifecycleEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsEmbeddedProbeLifecycleEnabled")
private val nodeJsEmbeddedScriptEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsEmbeddedScriptEnabled")
private val nodeJsEmbeddedPersistentRuntimeEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsEmbeddedPersistentRuntimeEnabled")
private val nodeJsEsmExperimentalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsEsmExperimentalEnabled")
private val nodeJsDynamicImportLocalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsDynamicImportLocalEnabled")
private val nodeJsDynamicImportEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsDynamicImportEnabled")
private val nodeJsNetworkExperimentalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsNetworkExperimentalEnabled")
private val nodeJsRawNetworkModulesExperimentalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsRawNetworkModulesExperimentalEnabled")
private val nodeJsWorkerThreadsExperimentalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsWorkerThreadsExperimentalEnabled")
private val nodeJsChildProcessExperimentalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsChildProcessExperimentalEnabled")
private val nodeJsJavaInteropExperimentalEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsJavaInteropExperimentalEnabled")
private val nodeJsProfileProEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileProEnabled")
private val nodeJsProfileDesktopEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileDesktopEnabled")
private val nodeJsProfileDebugUnsafeLabEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileDebugUnsafeLabEnabled")
private val nodeJsRelaxedCapabilitiesEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsRelaxedCapabilitiesEnabled")
private val nodeJsTimelineTraceEnabled = nodeJsAndroidConfigExtra<Boolean>("nodeJsTimelineTraceEnabled")
private val nodeJsTestDeviceProfile = nodeJsAndroidConfigExtra<String?>("nodeJsTestDeviceProfile")
private val nodeJsFuzzerBudget = nodeJsAndroidConfigExtra<String>("nodeJsFuzzerBudget")
private val nodeJsFuzzerReplayRunnerClass = nodeJsAndroidConfigExtra<String?>("nodeJsFuzzerReplayRunnerClass")
private val nodeJsFuzzerReplaySeed = nodeJsAndroidConfigExtra<String?>("nodeJsFuzzerReplaySeed")
private val nodeJsBridgeCmakeFile = nodeJsAndroidConfigExtra<File?>("nodeJsBridgeCmakeFile")
private val nodeJsRequestedFocusedGate = nodeJsAndroidConfigExtra<String?>("nodeJsRequestedFocusedGate")
private val nodeJsFocusedGateInstrumentationClassArgument =
    nodeJsAndroidConfigExtra<String?>("nodeJsFocusedGateInstrumentationClassArgument")
private val nodeJsTestDeviceProfileClasses = nodeJsAndroidConfigExtra<List<String>>("nodeJsTestDeviceProfileClasses")

private fun String.nodeJsAndroidCapitalized(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun File.nodeJsAndroidCmakePath(): String = absolutePath.replace('\\', '/')

extensions.configure<ApplicationExtension>("android") {
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
            if (!isNodeJsNativeRuntimeRequested) {
                val allNativeLibAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")
                excludes += allNativeLibAbis.flatMap { abi ->
                    listOf("lib/$abi/**", "**/$abi/**")
                }
            } else if (isNodeJsHostBridgeBuildRequested) {
                val unsupportedNodeJsAbis =
                    listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") - nodeJsBridgeNativeRuntimeAbis.toSet()
                excludes += unsupportedNodeJsAbis.flatMap { abi ->
                    listOf("lib/$abi/**", "**/$abi/**")
                }
            }
        }
    }

    if (isNodeJsHostBridgeBuildRequested && nodeJsBridgeNativeRuntimeAbis.isNotEmpty()) {
        externalNativeBuild {
            cmake {
                path = nodeJsBridgeCmakeFile ?: file("src/main/cpp/CMakeLists.txt")
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
    tasks.matching { it.name == "lintVitalAnalyze${flavorNameInrt.nodeJsAndroidCapitalized()}Release" }.configureEach {
        enabled = false
    }
}
