@file:Suppress("unused")

package org.autojs.build

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

/**
 * Configures host-side Android policy for the external Node.js runtime plugin.
 *
 * The host owns routing, request policy, bridge providers, identity and test assets. It does not
 * configure CMake, copy runtime libraries, or replace the normal Android jniLibs source set.
 */
class NodeAndroidConfigPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.afterEvaluate {
            plugins.withId("com.android.application") {
                configureNodePluginAndroidPolicy(project)
            }
        }
    }

    private fun configureNodePluginAndroidPolicy(project: Project) {
        val flavorNameApp = project.nodeJsAndroidConfigExtra<String>("flavorNameApp")
        val flavorNameInrt = project.nodeJsAndroidConfigExtra<String>("flavorNameInrt")
        val isAppAssembleTaskRequested =
            project.nodeJsAndroidConfigExtra<Boolean>("isAppAssembleTaskRequested")
        val isInrtAssembleTaskRequested =
            project.nodeJsAndroidConfigExtra<Boolean>("isInrtAssembleTaskRequested")
        val nodeJsIdentityProfile = project.nodeJsAndroidConfigExtra<String>("nodeJsIdentityProfile")
        val nodeJsApplicationIdSuffix =
            project.nodeJsAndroidConfigExtra<String>("nodeJsApplicationIdSuffix")
        val nodeJsEffectiveApplicationId =
            project.nodeJsAndroidConfigExtra<String>("nodeJsEffectiveApplicationId")
        val nodeJsAppLabel = project.nodeJsAndroidConfigExtra<String>("nodeJsAppLabel")
        val nodeJsReleaseFlavor = project.nodeJsAndroidConfigExtra<String>("nodeJsReleaseFlavor")
        val effectiveNodeJsRuntimeBackend =
            project.nodeJsAndroidConfigExtra<String>("effectiveNodeJsRuntimeBackend")
        val nodeJsAndroidTestExamplesRootDir =
            project.nodeJsAndroidConfigExtra<File>("nodeJsAndroidTestExamplesRootDir")
        val nodeJsEsmEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsEsmEnabled")
        val nodeJsDynamicImportEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsDynamicImportEnabled")
        val nodeJsNetworkEnabled = project.nodeJsAndroidConfigExtra<Boolean>("nodeJsNetworkEnabled")
        val nodeJsRawNetworkModulesEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsRawNetworkModulesEnabled")
        val nodeJsWorkerThreadsEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsWorkerThreadsEnabled")
        val nodeJsChildProcessEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsChildProcessEnabled")
        val nodeJsJavaInteropEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsJavaInteropEnabled")
        val nodeJsProfileProEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileProEnabled")
        val nodeJsProfileDesktopEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileDesktopEnabled")
        val nodeJsProfileDebugUnsafeLabEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsProfileDebugUnsafeLabEnabled")
        val nodeJsRelaxedCapabilitiesEnabled =
            project.nodeJsAndroidConfigExtra<Boolean>("nodeJsRelaxedCapabilitiesEnabled")
        val nodeJsTestDeviceProfile =
            project.nodeJsAndroidConfigExtra<String?>("nodeJsTestDeviceProfile")
        val providers = project.providers

        project.extensions.configure<ApplicationExtension>("android") {
            defaultConfig {
                val explicitInstrumentationClass =
                    providers.gradleProperty("android.testInstrumentationRunnerArguments.class")
                        .orNull
                        .isNullOrBlank()
                        .not()
                if (nodeJsTestDeviceProfile != null && !explicitInstrumentationClass) {
                    val testClass = when (nodeJsTestDeviceProfile) {
                        "accessibility" ->
                            "org.autojs.autojs.engine.NodeAccessibilityRealDeviceSmokeInstrumentationTest"
                        "screen_capture" ->
                            "org.autojs.autojs.engine.NodeScreenCaptureRealDeviceSmokeInstrumentationTest"
                        else -> error("Unsupported Node.js test device profile: $nodeJsTestDeviceProfile")
                    }
                    testInstrumentationRunnerArguments["class"] = testClass
                    testInstrumentationRunnerArguments["autojs.nodejs.test.deviceProfile"] =
                        nodeJsTestDeviceProfile
                }

                buildConfigField("String", "NODEJS_RUNTIME_BACKEND", "\"$effectiveNodeJsRuntimeBackend\"")
                buildConfigField("String", "NODEJS_RELEASE_FLAVOR", "\"$nodeJsReleaseFlavor\"")
                buildConfigField("boolean", "NODEJS_ESM_ENABLED", "$nodeJsEsmEnabled")
                buildConfigField("boolean", "NODEJS_DYNAMIC_IMPORT_ENABLED", "$nodeJsDynamicImportEnabled")
                buildConfigField("boolean", "NODEJS_NETWORK_ENABLED", "$nodeJsNetworkEnabled")
                buildConfigField(
                    "boolean",
                    "NODEJS_RAW_NETWORK_MODULES_ENABLED",
                    "$nodeJsRawNetworkModulesEnabled",
                )
                buildConfigField("boolean", "NODEJS_WORKER_THREADS_ENABLED", "$nodeJsWorkerThreadsEnabled")
                buildConfigField("boolean", "NODEJS_CHILD_PROCESS_ENABLED", "$nodeJsChildProcessEnabled")
                buildConfigField("boolean", "NODEJS_JAVA_INTEROP_ENABLED", "$nodeJsJavaInteropEnabled")
                buildConfigField("boolean", "NODEJS_PROFILE_PRO_ENABLED", "$nodeJsProfileProEnabled")
                buildConfigField(
                    "boolean",
                    "NODEJS_PROFILE_DESKTOP_ENABLED",
                    "$nodeJsProfileDesktopEnabled",
                )
                buildConfigField(
                    "boolean",
                    "NODEJS_PROFILE_DEBUG_UNSAFE_LAB_ENABLED",
                    "$nodeJsProfileDebugUnsafeLabEnabled",
                )
                buildConfigField(
                    "boolean",
                    "NODEJS_RELAXED_CAPABILITIES_ENABLED",
                    "$nodeJsRelaxedCapabilitiesEnabled",
                )

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
            }

            androidResources {
                if (isAppAssembleTaskRequested) {
                    ignoreAssetsPatterns.addAll(listOf(".idea", "declarations", "sample/declarations"))
                }
            }

            sourceSets {
                getByName("androidTest") {
                    assets.directories.add(nodeJsAndroidTestExamplesRootDir.absolutePath)
                }
            }

            splits {
                abi {
                    isEnable = !isInrtAssembleTaskRequested
                    reset()
                    include("arm64-v8a", "x86_64", "armeabi-v7a", "x86", "armeabi")
                    isUniversalApk = true
                }
            }
        }

        if (isInrtAssembleTaskRequested) {
            project.tasks
                .matching { it.name == "lintVitalAnalyze${flavorNameInrt.nodeJsAndroidCapitalized()}Release" }
                .configureEach { enabled = false }
        }

        project.logger.info(
            "Node.js host policy: identity=$nodeJsIdentityProfile " +
                    "releaseFlavor=$nodeJsReleaseFlavor backend=$effectiveNodeJsRuntimeBackend"
        )
    }
}

private fun nodeJsAndroidConfigExtraName(name: String): String = "nodeJsRuntimeConfig.$name"

@Suppress("UNCHECKED_CAST")
private fun <T> Project.nodeJsAndroidConfigExtra(name: String): T =
    extensions.extraProperties.get(nodeJsAndroidConfigExtraName(name)) as T

private fun String.nodeJsAndroidCapitalized(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
