@file:Suppress("SpellCheckingInspection")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "AutoJs6"

// Remove stale PASS receipts before project build scripts are compiled. This
// covers root-script compilation/configuration failures which occur before a
// task graph and therefore cannot be handled by build.gradle.kts.
val requestedPythonGateNames = gradle.startParameter.taskNames
    .map { requested -> requested.substringAfterLast(':') }
    .toSet()
val stalePythonReceipts = buildList {
    if (
        "verifyPythonR0" in requestedPythonGateNames ||
        "verifyPythonR1AndroidHarnessCompileGate" in requestedPythonGateNames ||
        "verifyPythonR1ConformanceGate" in requestedPythonGateNames ||
        "verifyPythonR1DeviceConformanceGate" in requestedPythonGateNames
    ) {
        add(settingsDir.resolve("build/reports/python/r0-gate.json"))
    }
    if (
        "verifyPythonR1AndroidHarnessCompileGate" in requestedPythonGateNames ||
        "verifyPythonR1ConformanceGate" in requestedPythonGateNames ||
        "verifyPythonR1DeviceConformanceGate" in requestedPythonGateNames
    ) {
        add(settingsDir.resolve("build/reports/python/r1-conformance-gate.json"))
        add(settingsDir.resolve("build/reports/python/r1-device-conformance-gate.json"))
    }
}
stalePythonReceipts.forEach { staleReceipt ->
    check(!staleReceipt.exists() || staleReceipt.delete()) {
        "Unable to remove stale Python gate receipt before project configuration: $staleReceipt"
    }
}

pluginManagement {
    // Optional composite for developing the shared build plugins before publication.
    providers.gradleProperty("autojs.buildPlugins.includeBuild").orNull?.let { includeBuild(it) }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("io.github.supermonster003.autojs6-platform-versions") version "1.9.0"
        id("io.github.supermonster003.autojs6-native-alignment") version "1.9.0"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

plugins {
    id("io.github.supermonster003.autojs6-platform-versions")
    // @Hint by SuperMonster003 on Sep 14, 2025.
    //  ! Enable JDK auto-resolution/download capability for build modules.
    //  ! zh-CN: 让构建模块具备 JDK 自动解析/下载能力.
    id("org.gradle.toolchains.foojay-resolver-convention")
}

includeBuild("build-logic")

private val modules = listOf(
    "apk-signer",
    "apk-verifier",
    "apk-parser",
    "android-axml",
    "android-arsc-editor",
    "color-picker",
    "material-dialogs",
    "material-date-time-picker",
    "expandable-layout",
    "expandable-recyclerview",
    "recyclerview-flexibledivider",
)

private val libs = listOf(
    "android-job-simplified-1_4_3",
    "apk-parser-1_0_2",
    "org-opencv-java-4_8_0",

    "jackpal-androidterm-1_0_70",
    "jackpal-androidterm-emulatorview-1_0_42",
    "jackpal-androidterm-libtermexec-1_0",

    "android-spackle-9_0_0",
    "android-assertion-9_0_0",
    "android-plugin-client-sdk-for-locale-9_0_0",

    "markwon-core-4_6_2",
    "markwon-syntax-highlight-4_6_2",

    "root-shell-1_6",
    "r8-compiler-api-0_1_0",
    "r8-compiler-api-0_2_0",
)

private val pluginApi = listOf(
    "compose-ui-api",
    "common-plugin-api",
    "protocol-wire-api",
    "ai-common-api",
    "ai-provider-api",
    "host-capability-api",
    "three-stove-agent-api",
    "dex-compiler-api",
    "jvm-source-api",
    "editor-api",
    "explorer-action-api",
    "lua-runtime-api",
    "bun-runtime-api",
    "quickjs-runtime-api",
    "autojs4-runtime-api",
    "nodejs-api",
    "python-runtime-api",
    "typescript-api",
    "typescript-parser-api",
    "ui-preview-api",
    "apk-builder-template",
    "opencc-api",
    "pinyin-api",
    "pinyin4j-api",
    "mediainfo-api",
    "imagequant-api",
    "offline-docs-api",
    "opencv-api",
    "mlkit-barcode-api",
    "yolo-api",
    "paddle-ocr-api",
    "paddle-ocr-engine",
    "rapid-ocr-engine",
    "screen-color-picker-api",
    "mcp-server-api",
    "mail-api",
    "epub-api",
    "package-archive-parser",
    "installer-api",
    "terminal-api",
    "batch-rename-api",
)

private val plugins = listOf(
    "apk-builder-template",
)

private val pluginConformanceTestApps = listOf(
    "three-stove-agent-conformance",
    "ai-provider-conformance",
    "dex-compiler-conformance",
    "lua-runtime-conformance",
    "mcp-server-conformance",
    "python-runtime-conformance",
    "python-runtime-api-consumer",
    "yolo-provider-conformance",
)

private val codegenFixtureApps = listOf(
    "codegen-fixture",
)

include(
    ":app",
    *modules.map { ":modules:$it" }.toTypedArray(),
    *libs.map { ":libs:$it" }.toTypedArray(),
    *pluginApi.map { ":plugin-api:$it" }.toTypedArray(),
    *plugins.map { ":plugins:$it" }.toTypedArray(),
    *pluginConformanceTestApps.map { ":test-apps:$it" }.toTypedArray(),
    *codegenFixtureApps.map { ":test-apps:$it" }.toTypedArray(),
)

modules.forEach {
    project(":modules:$it").projectDir = File("modules", it)
}

pluginApi.forEach {
    project(":plugin-api:$it").projectDir = File("plugin-api", it)
}

plugins.forEach {
    project(":plugins:$it").projectDir = File("plugins", it)
}

pluginConformanceTestApps.forEach {
    project(":test-apps:$it").projectDir = File("test-apps", it)
}

codegenFixtureApps.forEach {
    project(":test-apps:$it").projectDir = File("test-apps", it)
}
