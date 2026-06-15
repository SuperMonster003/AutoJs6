@file:Suppress("SpellCheckingInspection")

import com.android.build.api.variant.FilterConfiguration
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
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

val globalNamespace = "org.autojs.autojs6"
val globalApplicationId = "org.autojs.autojs6.nodejs"

val flavorDimension = "channel"
val flavorNameApp = "app"
val flavorNameInrt = "inrt"
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val buildActionAssemble = "assemble"

val taskNames: List<String> = gradle.startParameter.taskNames
val isNodeFuzzerSeedReplayRequested = taskNames.any { it.contains("replayNodeFuzzerSeed", ignoreCase = true) }
val isAppAssembleTaskRequested = taskNames.any { it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble", IGNORE_CASE)) }
val isInrtAssembleTaskRequested = taskNames.any { it.contains(Regex("^(:?$flavorNameApp:)?$buildActionAssemble$flavorNameInrt", IGNORE_CASE)) }
val isInrtTaskRequested = taskNames.any { it.contains(flavorNameInrt, true) }
val nodeJsRuntimeBackend = providers.gradleProperty("autojs.nodejs.backend").orElse("embedded").get()
    .trim()
    .lowercase()
    .let { if (it in setOf("rhino", "native", "embedded", "auto")) it else "embedded" }
val nodeJsReleaseFlavor = providers.gradleProperty("autojs.nodejs.releaseFlavor").orElse("nodeFull").get()
    .trim()
    .let { requested ->
        when (requested.lowercase()) {
            "nodefull", "node-full", "full" -> "nodeFull"
            "nodemini", "node-mini", "mini" -> "nodeMini"
            "rhinoonly", "rhino-only", "rhino" -> "rhinoOnly"
            else -> throw GradleException(
                "Unsupported Embedded Node release flavor '$requested'. " +
                        "Use nodeFull, nodeMini, or rhinoOnly."
            )
        }
    }
val nodeJsReleaseSupportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val nodeJsReleaseKnownAbis = nodeJsReleaseSupportedAbis + "x86"
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
    else -> emptyList()
}
val nodeJsReleaseUnsupportedAbis = nodeJsReleaseExpectedAbis.filter { it !in nodeJsReleaseSupportedAbis }
if (nodeJsReleaseUnsupportedAbis.isNotEmpty()) {
    throw GradleException(
        "Unsupported Embedded Node release ABI(s) for $nodeJsReleaseFlavor: " +
                nodeJsReleaseUnsupportedAbis.joinToString() +
                ". Supported Embedded Node ABIs are ${nodeJsReleaseSupportedAbis.joinToString()}; x86 is unsupported."
    )
}
if (file("src/main/jniLibs/x86/libnode.so").isFile) {
    throw GradleException(
        "Unsupported Embedded Node x86 runtime detected at app/src/main/jniLibs/x86/libnode.so. " +
                "x86 must remain fail-fast until libnode.so, libautojs6-node.so, and device coverage are explicitly supported."
    )
}
val effectiveNodeJsRuntimeBackend = if (nodeJsReleaseFlavor == "rhinoOnly") "rhino" else nodeJsRuntimeBackend
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
val nodeJsEsmExperimentalEnabled = providers.gradleProperty("autojs.nodejs.esm.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsDynamicImportExperimentalEnabled = providers.gradleProperty("autojs.nodejs.dynamicImport.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsNetworkExperimentalEnabled = providers.gradleProperty("autojs.nodejs.network.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsWorkerThreadsExperimentalEnabled = providers.gradleProperty("autojs.nodejs.worker_threads.experimental").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val nodeJsJavaInteropExperimentalEnabled = providers.gradleProperty("autojs.nodejs.javaInterop.experimental").orElse("false").get()
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
    "autojs.nodejs.esm.experimental" to nodeJsEsmExperimentalEnabled,
    "autojs.nodejs.dynamicImport.experimental" to nodeJsDynamicImportExperimentalEnabled,
    "autojs.nodejs.network.experimental" to nodeJsNetworkExperimentalEnabled,
    "autojs.nodejs.worker_threads.experimental" to nodeJsWorkerThreadsExperimentalEnabled,
    "autojs.nodejs.javaInterop.experimental" to nodeJsJavaInteropExperimentalEnabled,
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
            "sony", "emulator", "smoke", "full" -> requested
            else -> throw GradleException(
                "Unsupported Embedded Node test device profile '$requested'. " +
                        "Use sony, emulator, smoke, or full."
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
        "org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest",
        "org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest",
        "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest",
    )
    else -> emptyList()
}
val leakCanaryEnabled = providers.gradleProperty("autojs.leakcanary.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val isNodeJsNativeRuntimeRequested = effectiveNodeJsRuntimeBackend in setOf("native", "embedded", "auto")
val nodeJsAvailableNativeRuntimeAbis = nodeJsReleaseKnownAbis
    .filter { file("src/main/jniLibs/$it/libnode.so").isFile }
val nodeJsNativeRuntimeAbis = if (isNodeJsNativeRuntimeRequested) {
    nodeJsReleaseExpectedAbis.filter { it in nodeJsAvailableNativeRuntimeAbis }
} else {
    emptyList()
}
val nodeJsMissingReleaseAbis = if (isNodeJsNativeRuntimeRequested) {
    nodeJsReleaseExpectedAbis.filterNot { it in nodeJsAvailableNativeRuntimeAbis }
} else {
    emptyList()
}
if (nodeJsMissingReleaseAbis.isNotEmpty()) {
    throw GradleException(
        "Embedded Node $nodeJsReleaseFlavor requires native runtime ABI(s) not present in app/src/main/jniLibs: " +
                nodeJsMissingReleaseAbis.joinToString() +
                ". Adjust -Pautojs.nodejs.releaseAbis or provide libnode.so for the selected ABI set."
    )
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
    //  ! 渚濊禆锟?log4j (绗竴鐗堟湰) 鏄槗鍙楁敾鍑荤殑 (锟?5 椤规紡锟?:
    //  ! - CVE-2022-23307, 璇勫垎: 8.8
    //  ! - CVE-2022-23305, 璇勫垎: 9.8
    //  ! - CVE-2022-23302, 璇勫垎: 8.8
    //  ! - CVE-2021-4104, 璇勫垎: 7.5
    //  ! - CVE-2019-17571, 璇勫垎: 9.8
    //  ! 锟?log4j 绗簩鐗堟湰瑕佹眰瀹夊崜 API 绾у埆涓嶄綆锟?26,
    //  ! 涓庢渶锟?API 绾у埆锟?24 鐨勫綋鍓嶉」鐩棤娉曞吋锟?
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

    // RapidOCR
    implementation(project(":libs:rapidocr"))

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

    // Jieba Analysis (zh-CN: 缁撳反鍒嗚瘝)
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

    // Plugin API: Paddle OCR API
    implementation(project(":plugin-api:paddle-ocr-api"))

    // Plugin API: Paddle OCR Engine
    implementation(project(":plugin-api:paddle-ocr-engine"))
}

dependencies /* MIME */ {
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! Only for Android API 26 (8.0) [O] and above.
    //  ! zh-CN: 浠呴€傜敤浜庡畨锟?API 绾у埆 26 (8.0) [O] 鍙婁互锟?
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
    //  ! 鏌ョ湅 Appcompat 搴撶殑鍙戣鐗堟湰,
    //  ! 鍙锟?https://developer.android.com/jetpack/androidx/releases/appcompat.
    implementation(libs.appcompat)

    // AppCompat for legacy views (such as JsTextViewLegacy)
    implementation(project(":libs:androidx-appcompat-1_0_2")) {
        setVersion("1.0.2")
    }
}

dependencies /* Material Dialogs */ {
    // TODO by SuperMonster003 on Feb 5, 2022.
    //  ! Upgrade to 3.3.0 (more difficult than expected).
    //  ! zh-CN: 鍗囩骇锟?3.3.0 (瀹為檯闅惧害瓒呭嚭棰勬湡杈冨).
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
    //  ! 宸叉棤闇€锟?Kotlin Gradle 椤圭洰涓樉寮忓０鏄庢爣鍑嗗簱 (stdlib).
    //  ! 鐩稿叧渚濊禆宸查粯璁よ娣诲姞.
    //  ! 鍙傞槄 https://kotlinlang.org/docs/gradle.html#dependency-on-the-standard-library.
    // Kotlin
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")

    // @Comment by SuperMonster003 on Jun 1, 2022.
    //  ! Not necessary for current project but worth keeping the trace.
    //  ! zh-CN: 浜庡綋鍓嶉」鐩凡涓嶅啀闇€锟? 浣嗕緷鐒跺€煎緱鐣欏瓨鍏惰釜锟?(浠ュ涓嶆椂涔嬮渶).
    // Google Guava
    // implementation("com.google.guava:guava:31.1-jre")

    // @Comment by SuperMonster003 on Apr 9, 2024.
    //  ! It was ever imported and used for MediaType constants.
    //  ! zh-CN: 鏇剧敤锟?MediaType 甯搁噺鐨勫鍏ュ強浣跨敤.
    // Javax WS RS API (Java API for RESTful Web Services)
    // implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")

    // @Hint by SuperMonster003 on Jan 11, 2026.
    //  ! The AutoJs6 core project has deprecated the Local Broadcast Manager library,
    //  ! replaced by LiveData and SharedFlow.
    //  ! It is only retained for dependencies of certain libraries (such as MQTT).
    //  ! zh-CN:
    //  ! AutoJs6 鏍稿績椤圭洰宸插純锟?Local Broadcast Manager 锟?
    //  ! 锟?LiveData 锟?SharedFlow 鏇夸唬.
    //  ! 浠呯敤浜庢煇浜涘簱 (锟?MQTT) 鐨勪緷璧栬€屼繚锟?
    // Local Broadcast Manager
    implementation(libs.localbroadcastmanager)
}

dependencies /* Reserved for auto append by IDE */ {

}

android {

    namespace = globalNamespace
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = globalApplicationId

        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget

        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val explicitInstrumentationClass =
            providers.gradleProperty("android.testInstrumentationRunnerArguments.class").orNull.isNullOrBlank().not()
        if (nodeJsFuzzerReplayRunnerClass != null && !explicitInstrumentationClass) {
            testInstrumentationRunnerArguments["class"] = nodeJsFuzzerReplayRunnerClass
        } else if (nodeJsTestDeviceProfile != null && !explicitInstrumentationClass) {
            testInstrumentationRunnerArguments["class"] = nodeJsTestDeviceProfileClasses.joinToString(",")
            testInstrumentationRunnerArguments["autojs.nodejs.test.deviceProfile"] = nodeJsTestDeviceProfile
        }
        testInstrumentationRunnerArguments["autojs.nodejs.fuzzerBudget"] = nodeJsFuzzerBudget
        nodeJsFuzzerReplaySeed?.let { testInstrumentationRunnerArguments["autojs.nodejs.fuzzerSeed"] = it }

        multiDexEnabled = true

        if (isNodeJsNativeRuntimeRequested && nodeJsNativeRuntimeAbis.isNotEmpty()) {
            ndk.abiFilters += nodeJsNativeRuntimeAbis
            externalNativeBuild {
                cmake {
                    abiFilters += nodeJsNativeRuntimeAbis
                    arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=${if (nodeJsEmbeddedProbeLifecycleEnabled) "ON" else "OFF"}"
                    arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=${if (nodeJsEmbeddedScriptEnabled) "ON" else "OFF"}"
                    cppFlags += "-std=c++20"
                }
            }
        }

        buildConfigField("String", "VERSION_DATE", "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")
        buildConfigField("String", "VSCODE_EXT_REQUIRED_VERSION", "\"${versions.vscodeExtRequiredVersion}\"")
        buildConfigField("String", "NODEJS_RUNTIME_BACKEND", "\"$effectiveNodeJsRuntimeBackend\"")
        buildConfigField("String", "NODEJS_RELEASE_FLAVOR", "\"$nodeJsReleaseFlavor\"")
        buildConfigField("int", "NODEJS_EMBEDDED_PROBE_LEVEL", "$nodeJsEmbeddedProbeLevel")
        buildConfigField("long", "NODEJS_EMBEDDED_PROBE_TIMEOUT_MS", "${nodeJsEmbeddedProbeTimeoutMs}L")
        buildConfigField("boolean", "NODEJS_EMBEDDED_PROBE_LIFECYCLE_BUILD_ENABLED", "$nodeJsEmbeddedProbeLifecycleEnabled")
        buildConfigField("boolean", "NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED", "$nodeJsEmbeddedScriptEnabled")
        buildConfigField("boolean", "NODEJS_ESM_EXPERIMENTAL_ENABLED", "$nodeJsEsmExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED", "$nodeJsDynamicImportExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_NETWORK_EXPERIMENTAL_ENABLED", "$nodeJsNetworkExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED", "$nodeJsWorkerThreadsExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED", "$nodeJsJavaInteropExperimentalEnabled")
        buildConfigField("boolean", "NODEJS_TIMELINE_TRACE_ENABLED", "$nodeJsTimelineTraceEnabled")
        buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")

        javaCompileOptions {
            annotationProcessorOptions {
                mapOf(
                    "resourcePackageName" to globalNamespace,
                    "androidManifestFile" to ("$projectDir/src/main/AndroidManifest.xml")
                ).let { arguments(it) }
            }
        }
    }

    flavorDimensions.add(flavorDimension)

    productFlavors {

        create(flavorNameApp) {
            dimension = flavorDimension
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName
            buildConfigField("String", "CHANNEL", "\"$flavorNameApp\"")
            buildConfigField("boolean", "is${flavorNameInrt.uppercaseFirstChar()}", "false")
            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameApp,
                    "appName" to "@string/app_name",
                    "intentCategory" to "android.intent.category.LAUNCHER",
                    "intentCategoryInrt" to "android.intent.category.DEFAULT",
                    "authorities" to "$globalApplicationId.fileprovider",
                    "icon" to "@mipmap/ic_app_launcher_adaptive",
                )
            )
        }

        create(flavorNameInrt) {
            dimension = flavorDimension
            applicationIdSuffix = ".$flavorNameInrt"
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
                    "authorities" to "$globalApplicationId.$flavorNameInrt.fileprovider",
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
                //  ! 锟?org.autojs.autojs.ui.project.BuildActivity 鏋勫缓 APK 锟?
                //  ! 杩愯鏃惰祫浜ф枃锟?(runtime assets) 灏嗙敱鍚嶄负 "app" 锟?Gradle flavor 浣滀负婧愬湴鍧€杩涜澶嶅埗.
                ignoreAssetsPatterns.addAll(emptyList())
            }
        }

    }

    sourceSets {

        // @Hint by LZX284 (https://github.com/LZX284) on Nov 15, 2023.
        //  ! The assets file is divided into three directories according to different flavors.
        //  ! But the files are not actually moved to avoid conflicts with the latest modifications.
        //  ! zh-CN (translated by SuperMonster003 on Jul 26, 2024):
        //  ! 璧勪骇鏂囦欢鏍规嵁涓嶅悓锟?Gradle flavor 琚殧绂讳负涓変釜涓嶅悓鐨勭洰锟?
        //  ! 涓嶈繃鐪熷疄鏂囦欢骞舵病鏈夎繘琛岀Щ锟? 浠ラ伩鍏嶄笌鏈€鏂扮殑椤圭洰淇敼鍙戠敓鍐茬獊.
        //  !
        // @Hint by SuperMonster003 on Nov 16, 2023.
        //  ! The assets division idea was accepted, and it wouldn't hurt to try. :)
        //  ! zh-CN: 璧勪骇闅旂鐨勬兂娉曞彲浠ヨ鎺ュ彈, 姣曠珶璇曚竴涓嬩篃鏃犲Θ. [绗戣劯绗﹀彿]

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
            assets.directories.add("$rootDir/sample/nodejs")
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
                "**/prob_emit.txt", // Jieba Analysis (zh-CN: 缁撳反鍒嗚瘝)
                "**/dict-chinese-*.db.gzip", // Jieba Analysis (zh-CN: 缁撳反鍒嗚瘝)
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
            } else {
                val unsupportedNodeJsAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") - nodeJsNativeRuntimeAbis.toSet()
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
        //  ! zh-CN: "android.defaults.buildfeatures.buildconfig=true" 鐨勬浛浠ｆ柟锟?
        buildConfig = true

        // @Archived by SuperMonster003 on Sep 23, 2024.
        //  ! Jetpack Compose
        //  # compose = true
        //  # composeOptions {
        //  #     kotlinCompilerExtensionVersion = "1.5.12"
        //  # }
    }

    if (isNodeJsNativeRuntimeRequested && nodeJsNativeRuntimeAbis.isNotEmpty()) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
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
                isNodeJsNativeRuntimeRequested && nodeJsNativeRuntimeAbis.isNotEmpty() -> nodeJsNativeRuntimeAbis
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
                val outputDir = when (val dir = javaClass.methods.firstOrNull {
                    it.name == "getOutputDir" && it.parameterTypes.isEmpty()
                }?.invoke(this)) {
                    is File -> dir
                    is Directory -> dir.asFile
                    else -> null
                } ?: return@doLast

                val includes = when (isInrtVariant) {
                    true -> listOf(
                        "mlkit-google-ocr-models/**/*",
                        "mlkit_barcode_models/**/*",
                        "models/**/*",
                        "modules/obsolete/**/*",
                        "openccdata/**/*",
                        "project/**/*",
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

tasks.register("printNodeTestDeviceProfile") {
    group = "verification"
    description = "Prints the Embedded Node instrumentation classes selected by autojs.nodejs.test.deviceProfile."

    doLast {
        println("Embedded Node test device profile: ${nodeJsTestDeviceProfile ?: "unset"}")
        println("Embedded Node fuzzer budget: $nodeJsFuzzerBudget")
        if (nodeJsFuzzerSeedFile != null) {
            println("Embedded Node fuzzer seed file: $nodeJsFuzzerSeedFile")
            println("Embedded Node fuzzer replay class: ${nodeJsFuzzerReplayRunnerClass ?: "unavailable"}")
            println("Embedded Node fuzzer replay seed: ${nodeJsFuzzerReplaySeed ?: "unavailable"}")
        }
        if (nodeJsTestDeviceProfileClasses.isEmpty()) {
            println("Embedded Node selected instrumentation classes: none; pass -Pautojs.nodejs.test.deviceProfile=sony|emulator|smoke|full")
        } else {
            println("Embedded Node selected instrumentation classes:")
            nodeJsTestDeviceProfileClasses.forEach { className ->
                println("  $className")
            }
        }
    }
}

tasks.register("replayNodeFuzzerSeed") {
    group = "verification"
    description = "Replays a saved Embedded Node fuzzer seed with connected Android instrumentation."

    dependsOn("connectedAppDebugAndroidTest")

    doFirst {
        val seedFile = nodeJsFuzzerSeedFile
            ?: throw GradleException("Pass -Pautojs.nodejs.fuzzerSeedFile=<build/reports/nodejs/fuzz-seeds/*.json>.")
        if (nodeJsFuzzerReplayRunnerClass.isNullOrBlank() || nodeJsFuzzerReplaySeed.isNullOrBlank()) {
            throw GradleException("Fuzzer seed file '$seedFile' is missing replayClass/baseSeed fields.")
        }
        println("Replaying Embedded Node fuzzer seed: $seedFile")
        println("  class: $nodeJsFuzzerReplayRunnerClass")
        println("  budget: $nodeJsFuzzerBudget")
        println("  seed: $nodeJsFuzzerReplaySeed")
    }
}

if (isNodeFuzzerSeedReplayRequested) {
    tasks.matching { it.name == "connectedAppDebugAndroidTest" }.configureEach {
        doFirst {
            val seedFile = nodeJsFuzzerSeedFile
                ?: throw GradleException("Pass -Pautojs.nodejs.fuzzerSeedFile=<build/reports/nodejs/fuzz-seeds/*.json>.")
            if (nodeJsFuzzerReplayRunnerClass.isNullOrBlank() || nodeJsFuzzerReplaySeed.isNullOrBlank()) {
                throw GradleException("Fuzzer seed file '$seedFile' is missing replayClass/baseSeed fields.")
            }
        }
    }
}

apply(from = "native-node-deps-report.gradle.kts")
apply(from = "embedded-node-packaging-preflight.gradle.kts")
apply(from = "node-performance-report.gradle.kts")
apply(from = "node-compatibility-profile.gradle.kts")
apply(from = "node-dependency-bundler.gradle.kts")
apply(from = "node-doctor-report.gradle.kts")
apply(from = "node-resolver-visualizer.gradle.kts")
apply(from = "node-profile-release-manifest.gradle.kts")
apply(from = "node-error-catalog.gradle.kts")
apply(from = "node-examples.gradle.kts")

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"

        // @Hint by SuperMonster003 on May 18, 2022.
        //  ! Comment or remove this option if you are tired of plenty of warnings. :)
        //  ! zh-CN: 娉ㄩ噴鎴栫Щ闄ゆ閫夐」鍙伩鍏嶈繃澶氳鍛婃秷鎭€犳垚鐨勫洶锟? [绗戣劯绗﹀彿]
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
