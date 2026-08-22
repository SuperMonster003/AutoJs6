// @Hint: declared here, applied by the modules.
//  ! These plugins used to arrive through the settings buildscript classpath, which the
//  ! platform-versions plugin replaces. Declaring them once here keeps the version in a
//  ! single place and leaves the module scripts untouched, which Groovy modules require:
//  ! their plugins block accepts string literals only, so a computed version cannot go there.
//  ! zh-CN: 这些插件原先经由 settings buildscript classpath 提供, 现已被 platform-versions 插件取代.
//  ! 在此声明一次可使版本只出现在一处, 且模块脚本无须改动 -- 这对 Groovy 模块是必需的:
//  ! 它们的 plugins 块只接受字符串字面量, 无法写入运行时计算出的版本.
plugins {
    id("com.android.application") version System.getProperty("gradle.agp.version") apply false
    id("com.android.library") version System.getProperty("gradle.agp.version") apply false
    id("com.google.devtools.ksp") version System.getProperty("gradle.ksp.version") apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version System.getProperty("gradle.kotlin.version") apply false
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.

// @Hint by SuperMonster003 on Aug 16, 2023.
//  ! Blocks "buildscript" and "plugins" have been moved to "settings.gradle.kts".
//  ! zh-CN: 代码块 "buildscript" 以及 "plugins" 已迁移至 "settings.gradle.kts".

data class PythonGateTestSummary(
    val task: String,
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
)

fun pythonGateSha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return java.util.HexFormat.of().formatHex(digest.digest())
}

fun pythonGateSourceFingerprint(root: File, files: Collection<File>): Pair<String, Int> {
    val canonicalRoot = root.canonicalFile.toPath()
    val entries = files
        .filter(File::isFile)
        .map(File::getCanonicalFile)
        .distinctBy(File::getPath)
        .map { file ->
            val path = file.toPath()
            check(path.startsWith(canonicalRoot)) { "Python release source escaped the host tree: $file" }
            canonicalRoot.relativize(path).toString().replace(File.separatorChar, '/') to file
        }
        .sortedBy { (path, _) -> path }
    check(entries.isNotEmpty()) { "Python release source fingerprint scope is empty" }

    val digest = java.security.MessageDigest.getInstance("SHA-256")
    entries.forEach { (path, file) ->
        val record = "$path\u0000${file.length()}\u0000${pythonGateSha256(file)}\n"
        digest.update(record.toByteArray(Charsets.UTF_8))
    }
    return java.util.HexFormat.of().formatHex(digest.digest()) to entries.size
}

fun pythonGateGit(root: File, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git", "-C", root.absolutePath) + arguments)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "Unable to establish current host Git identity: git ${arguments.joinToString(" ")} exited $exitCode"
    }
    return output.trimEnd()
}

fun pythonGateWriteTextAtomically(target: File, text: String) {
    val parent = target.parentFile
    check(parent.isDirectory || parent.mkdirs()) { "Unable to create Python gate output directory: $parent" }
    val temporary = java.nio.file.Files.createTempFile(parent.toPath(), ".${target.name}.", ".tmp")
    try {
        java.io.FileOutputStream(temporary.toFile()).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        java.nio.file.Files.move(
            temporary,
            target.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        java.nio.file.Files.deleteIfExists(temporary)
    }
}

fun isPythonAndroidDeviceExecutionTask(task: org.gradle.api.Task): Boolean {
    val taskType = task.javaClass.name
    return task.name.contains("connected", ignoreCase = true) ||
        task.name.contains("managedDevice", ignoreCase = true) ||
        taskType.contains("DeviceProviderInstrumentTest", ignoreCase = true) ||
        taskType.contains("ManagedDeviceInstrumentationTest", ignoreCase = true) ||
        (
            taskType.contains("InstrumentationTest", ignoreCase = true) &&
                taskType.contains("Device", ignoreCase = true)
            )
}

fun readPythonGateTestSummary(
    task: String,
    directory: File,
    fileFilter: (File) -> Boolean = { true },
): PythonGateTestSummary {
    val resultFiles = directory.walkTopDown()
        .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" && fileFilter(it) }
        .toList()
    check(resultFiles.isNotEmpty()) { "No current JUnit XML evidence for $task under $directory" }
    fun attribute(tag: String, name: String): Int =
        Regex("""\b$name="(\d+)"""").find(tag)?.groupValues?.get(1)?.toInt()
            ?: error("Missing $name in JUnit XML for $task")
    var tests = 0
    var failures = 0
    var errors = 0
    var skipped = 0
    resultFiles.forEach { result ->
        val suiteTag = Regex("""<testsuite\b[^>]*>""").find(result.readText())?.value
            ?: error("Missing testsuite element in $result")
        tests += attribute(suiteTag, "tests")
        failures += attribute(suiteTag, "failures")
        errors += attribute(suiteTag, "errors")
        skipped += attribute(suiteTag, "skipped")
    }
    check(tests > 0 && failures == 0 && errors == 0 && skipped == 0) {
        "Python R1 evidence for $task is incomplete: " +
            "tests=$tests failures=$failures errors=$errors skipped=$skipped"
    }
    return PythonGateTestSummary(task, tests, failures, errors, skipped)
}

val skipCiLintVital = providers.gradleProperty("autojs.ci.skipLintVital")
    .map { value -> value.trim().lowercase() in setOf("true", "1", "yes", "on") }
    .orElse(false)

val pythonR0Receipt = layout.buildDirectory.file("reports/python/r0-gate.json")
val pythonR1Receipt = layout.buildDirectory.file("reports/python/r1-conformance-gate.json")
val pythonR1DeviceReceipt = layout.buildDirectory.file("reports/python/r1-device-conformance-gate.json")
val pythonR1DistributionDirectory = layout.buildDirectory.dir("distributions/python-runtime-r1")
val pythonReleaseApiDistributionDirectory = layout.buildDirectory.dir("distributions/python-runtime-release")
val pythonReleaseApiDistributionReceipt = layout.buildDirectory.file(
    "reports/python/release-api-distribution-gate.json",
)

project(":plugin-api:python-runtime-api").tasks.configureEach {
    if (name == "assembleRelease") {
        mustRunAfter(":plugin-api:protocol-wire-api:assembleRelease")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/jcenter")
        maven("https://maven.aliyun.com/repository/public")
    }

    tasks.configureEach {
        if (skipCiLintVital.get() && name.contains("lintVital", ignoreCase = true)) {
            enabled = false
        }
    }
}

tasks {
    register<Delete>("clean").configure {
        // @Legacy delete(rootProject.buildDir)
        delete(rootProject.layout.buildDirectory)
    }

    register("assembleApkBuilderTemplateDistribution").configure {
        group = "build"
        description = "Builds app release without built-in APK builder template and matching APK builder template plugin release."
        dependsOn(
            ":app:generateRuntimeKit",
            ":app:packageAppRelease",
            ":plugins:apk-builder-template:packageRelease",
        )
    }

    register("preparePythonR1ApiDistribution").configure {
        group = "distribution"
        description = "Assembles the two-file Python Runtime API distribution with hashes and dependency metadata."
        dependsOn(
            ":plugin-api:protocol-wire-api:assembleDebug",
            ":plugin-api:python-runtime-api:assembleDebug",
        )
        val protocolWireAar = project(":plugin-api:protocol-wire-api").layout.buildDirectory.file(
            "outputs/aar/protocol-wire-api-debug.aar",
        )
        val pythonRuntimeAar = project(":plugin-api:python-runtime-api").layout.buildDirectory.file(
            "outputs/aar/python-runtime-api-debug.aar",
        )
        inputs.files(protocolWireAar, pythonRuntimeAar)
        outputs.dir(pythonR1DistributionDirectory)
        doLast {
            val distributionDirectory = pythonR1DistributionDirectory.get().asFile
            delete(distributionDirectory)
            check(distributionDirectory.mkdirs()) {
                "Unable to create Python R1 distribution directory: $distributionDirectory"
            }
            val artifacts = listOf(protocolWireAar.get().asFile, pythonRuntimeAar.get().asFile)
            artifacts.forEach { source ->
                check(source.isFile) { "Missing Python R1 API artifact: $source" }
                source.copyTo(distributionDirectory.resolve(source.name), overwrite = false)
            }
            distributionDirectory.resolve("distribution.json").writeText(
                buildString {
                    appendLine("{")
                    appendLine("  \"schema\": 1,")
                    appendLine("  \"distributionVersion\": \"1.0.0-r1\",")
                    appendLine("  \"pythonRuntimeProtocol\": \"1.0\",")
                    appendLine("  \"dependency\": \"python-runtime-api -> protocol-wire-api\",")
                    appendLine("  \"artifacts\": [")
                    artifacts.forEachIndexed { index, source ->
                        val suffix = if (index == artifacts.lastIndex) "" else ","
                        appendLine(
                            "    {\"name\": \"${source.name}\", \"bytes\": ${source.length()}, " +
                                "\"sha256\": \"${pythonGateSha256(source)}\"}$suffix",
                        )
                    }
                    appendLine("  ]")
                    appendLine("}")
                },
            )
        }
    }

    register("verifyPythonReleaseApiDistributionGate").configure {
        group = "distribution"
        description =
            "Builds and verifies the stable current-tree Python Runtime release API AAR distribution."
        dependsOn(
            ":plugin-api:common-plugin-api:assembleRelease",
            ":plugin-api:protocol-wire-api:assembleRelease",
            ":plugin-api:python-runtime-api:assembleRelease",
        )

        val commonPluginAar = project(":plugin-api:common-plugin-api").layout.buildDirectory.file(
            "outputs/aar/common-plugin-api-release.aar",
        )
        val protocolWireAar = project(":plugin-api:protocol-wire-api").layout.buildDirectory.file(
            "outputs/aar/protocol-wire-api-release.aar",
        )
        val pythonRuntimeAar = project(":plugin-api:python-runtime-api").layout.buildDirectory.file(
            "outputs/aar/python-runtime-api-release.aar",
        )
        val sourceFiles = files(
            file("build.gradle.kts"),
            file("settings.gradle.kts"),
            file("version.properties"),
            fileTree("plugin-api/common-plugin-api") {
                exclude("build/**", ".gradle/**")
            },
            fileTree("plugin-api/protocol-wire-api") {
                exclude("build/**", ".gradle/**")
            },
            fileTree("plugin-api/python-runtime-api") {
                exclude("build/**", ".gradle/**")
            },
        )
        inputs.files(commonPluginAar, protocolWireAar, pythonRuntimeAar, sourceFiles)
        outputs.dir(pythonReleaseApiDistributionDirectory)
        outputs.file(pythonReleaseApiDistributionReceipt)
        outputs.upToDateWhen { false }

        doLast {
            val distributionDirectory = pythonReleaseApiDistributionDirectory.get().asFile
            check(!distributionDirectory.exists() || distributionDirectory.deleteRecursively()) {
                "Unable to remove stale Python release API distribution: $distributionDirectory"
            }
            check(distributionDirectory.mkdirs()) {
                "Unable to create Python release API distribution: $distributionDirectory"
            }

            val sources = listOf(
                commonPluginAar.get().asFile,
                protocolWireAar.get().asFile,
                pythonRuntimeAar.get().asFile,
            )
            val expectedNames = setOf(
                "common-plugin-api-release.aar",
                "protocol-wire-api-release.aar",
                "python-runtime-api-release.aar",
            )
            check(sources.map(File::getName).toSet() == expectedNames) {
                "Python release API source names differ from the exact stable release AAR bundle"
            }
            sources.forEach { source ->
                check(source.isFile) { "Missing Python release API artifact: $source" }
                check(source.length() > 0L) { "Empty Python release API artifact: $source" }
                check("debug" !in source.name.lowercase() && source.name.endsWith("-release.aar")) {
                    "Debug or non-release AAR cannot enter the Python release API distribution: ${source.name}"
                }
                val target = distributionDirectory.resolve(source.name)
                java.nio.file.Files.copy(source.toPath(), target.toPath())
                check(target.length() == source.length() && pythonGateSha256(target) == pythonGateSha256(source)) {
                    "Python release API copy differs from its original AAR: ${source.name}"
                }
            }

            val stagedAars = distributionDirectory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.equals("aar", ignoreCase = true) }
            check(stagedAars.map(File::getName).toSet() == expectedNames && stagedAars.size == expectedNames.size) {
                "Python release API distribution must contain exactly the three release AARs; " +
                    "found=${stagedAars.map(File::getName).sorted()}"
            }

            val head = pythonGateGit(rootDir, "rev-parse", "--verify", "HEAD").lowercase()
            check(head.matches(Regex("[0-9a-f]{40,64}"))) { "Invalid current host Git HEAD identity" }
            val dirty = pythonGateGit(
                rootDir,
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ).isNotEmpty()
            val versionProperties = java.util.Properties().apply {
                file("version.properties").inputStream().buffered().use { input -> load(input) }
            }
            val hostVersionName = versionProperties.getProperty("VERSION_NAME")?.trim().orEmpty()
            check(hostVersionName.isNotEmpty() && hostVersionName.none(Character::isISOControl)) {
                "Current host VERSION_NAME is missing or invalid"
            }
            val hostVersionCode = versionProperties.getProperty("VERSION_BUILD")
                ?.trim()
                ?.toLongOrNull()
                ?: error("Current host VERSION_BUILD is missing or invalid")
            check(hostVersionCode > 0L) { "Current host VERSION_BUILD must be positive" }
            val escapedHostVersionName = hostVersionName
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")

            fun declaredApiProjectDependencies(module: String): Set<String> {
                val buildText = file("plugin-api/$module/build.gradle.kts").readText()
                return Regex("""\bapi\s*\(\s*project\("([^"]+)"\)\s*\)""")
                    .findAll(buildText)
                    .map { match -> match.groupValues[1] }
                    .toSet()
            }
            val declaredDependencies = mapOf(
                "common-plugin-api-release.aar" to declaredApiProjectDependencies("common-plugin-api"),
                "protocol-wire-api-release.aar" to declaredApiProjectDependencies("protocol-wire-api"),
                "python-runtime-api-release.aar" to declaredApiProjectDependencies("python-runtime-api"),
            )
            check(declaredDependencies["common-plugin-api-release.aar"].orEmpty().isEmpty()) {
                "common-plugin-api release dependency graph changed; update the stable distribution manifest"
            }
            check(declaredDependencies["protocol-wire-api-release.aar"].orEmpty().isEmpty()) {
                "protocol-wire-api release dependency graph changed; update the stable distribution manifest"
            }
            check(
                declaredDependencies["python-runtime-api-release.aar"] ==
                    setOf(":plugin-api:protocol-wire-api"),
            ) {
                "python-runtime-api release dependency graph changed; update the stable distribution manifest"
            }
            val (sourceFingerprint, sourceFileCount) = pythonGateSourceFingerprint(
                rootDir,
                sourceFiles.files,
            )
            val artifacts = stagedAars.sortedBy(File::getName)
            val manifest = distributionDirectory.resolve("distribution-manifest.json")
            pythonGateWriteTextAtomically(
                manifest,
                buildString {
                    appendLine("{")
                    appendLine("  \"schema\": \"org.autojs6.python-runtime.release-api-distribution\",")
                    appendLine("  \"schemaVersion\": 1,")
                    appendLine("  \"distributionVersion\": \"1.4.0\",")
                    appendLine("  \"releaseChannel\": \"STABLE\",")
                    appendLine("  \"scope\": \"LOCAL_CURRENT_TREE_RELEASE_AAR_DISTRIBUTION\",")
                    appendLine("  \"published\": false,")
                    appendLine("  \"host\": {")
                    appendLine("    \"versionName\": \"$escapedHostVersionName\",")
                    appendLine("    \"versionCode\": $hostVersionCode,")
                    appendLine("    \"gitCommit\": \"$head\",")
                    appendLine("    \"dirty\": $dirty,")
                    appendLine("    \"sourceFingerprintAlgorithm\": \"SHA-256\",")
                    appendLine("    \"sourceFingerprint\": \"$sourceFingerprint\",")
                    appendLine("    \"sourceFileCount\": $sourceFileCount")
                    appendLine("  },")
                    appendLine("  \"protocol\": {")
                    appendLine("    \"name\": \"python-runtime\",")
                    appendLine("    \"version\": \"1.4\",")
                    appendLine("    \"major\": 1,")
                    appendLine("    \"minor\": 4,")
                    appendLine("    \"taggedWireFormatVersion\": 1")
                    appendLine("  },")
                    appendLine("  \"dependencies\": {")
                    appendLine("    \"packaging\": \"SEPARATE_AARS\",")
                    appendLine("    \"compileEdges\": [")
                    appendLine(
                        "      {\"from\": \"python-runtime-api-release.aar\", " +
                            "\"to\": \"protocol-wire-api-release.aar\"}",
                    )
                    appendLine("    ],")
                    appendLine("    \"independentArtifacts\": [\"common-plugin-api-release.aar\"]")
                    appendLine("  },")
                    appendLine("  \"artifacts\": [")
                    artifacts.forEachIndexed { index, artifact ->
                        val suffix = if (index == artifacts.lastIndex) "" else ","
                        appendLine(
                            "    {\"name\": \"${artifact.name}\", \"bytes\": ${artifact.length()}, " +
                                "\"sha256\": \"${pythonGateSha256(artifact)}\"}$suffix",
                        )
                    }
                    appendLine("  ]")
                    appendLine("}")
                },
            )

            val distributionFiles = distributionDirectory.listFiles().orEmpty().filter(File::isFile)
            check(distributionFiles.map(File::getName).toSet() == expectedNames + manifest.name) {
                "Python release API distribution contains an unexpected or missing file: " +
                    distributionFiles.map(File::getName).sorted()
            }

            val receipt = pythonReleaseApiDistributionReceipt.get().asFile
            pythonGateWriteTextAtomically(
                receipt,
                buildString {
                    appendLine("{")
                    appendLine("  \"schema\": 1,")
                    appendLine("  \"gate\": \"verifyPythonReleaseApiDistributionGate\",")
                    appendLine("  \"status\": \"PASS\",")
                    appendLine("  \"scope\": \"LOCAL_RELEASE_AAR_DISTRIBUTION_ONLY\",")
                    appendLine("  \"hostGitHead\": \"$head\",")
                    appendLine("  \"hostDirty\": $dirty,")
                    appendLine("  \"sourceFingerprint\": \"$sourceFingerprint\",")
                    appendLine(
                        "  \"manifest\": {\"path\": " +
                            "\"build/distributions/python-runtime-release/${manifest.name}\", " +
                            "\"sha256\": \"${pythonGateSha256(manifest)}\"},",
                    )
                    appendLine("  \"claims\": {")
                    appendLine("    \"releaseAarsBuiltAndCopied\": true,")
                    appendLine("    \"published\": false,")
                    appendLine("    \"pluginConsumed\": false,")
                    appendLine("    \"androidRuntimeVerified\": false,")
                    appendLine("    \"deviceVerified\": false")
                    appendLine("  }")
                    appendLine("}")
                },
            )
        }
    }

    val verifyPythonR0HostBoundary = register("verifyPythonR0HostBoundary") {
        group = "verification"
        description = "Verifies the R0 host-process isolation boundary for Python runtime artifacts."
        doLast {
            val violations = mutableListOf<String>()
            fileTree("app") {
                include("**/*.gradle", "**/*.gradle.kts")
                exclude("build/**")
            }.files.forEach { file ->
                val text = file.readText()
                if ("chaquopy" in text.lowercase()) {
                    violations += "forbidden embedded Python runtime dependency in ${file.relativeTo(rootDir)}"
                }
            }
            fileTree("app/src") {
                include("main/**/*.so", "release/**/*.so", "app/**/*.so", "inrt/**/*.so")
            }.files
                .filter { "python" in it.name.lowercase() }
                .forEach { file ->
                    violations += "forbidden embedded Python native library in ${file.relativeTo(rootDir)}"
                }
            check(violations.isEmpty()) {
                violations.joinToString(prefix = "Python R0 host boundary violations:\n", separator = "\n")
            }
        }
    }

    val verifyPythonR1DefaultOffHostBoundary = register("verifyPythonR1DefaultOffHostBoundary") {
        group = "verification"
        description = "Verifies the stable app-only Python host channel and process-isolated CPython boundary."
        doLast {
            val violations = mutableListOf<String>()
            val appBuildFile = file("app/build.gradle.kts")
            val appBuildText = appBuildFile.readText()
            fileTree("app") {
                include("**/*.gradle", "**/*.gradle.kts")
                exclude("build/**")
            }.files.forEach { file ->
                if ("chaquopy" in file.readText().lowercase()) {
                    violations += "embedded Python runtime dependency in ${file.relativeTo(rootDir)}"
                }
            }
            fileTree("app/src") {
                include(
                    "main/**/AndroidManifest.xml",
                    "release/**/AndroidManifest.xml",
                    "app/**/AndroidManifest.xml",
                    "inrt/**/AndroidManifest.xml",
                )
            }.files.forEach { file ->
                if ("org.autojs.plugin.python.RUNTIME" in file.readText()) {
                    violations += "production manifest uses broad Python runtime action visibility in ${file.relativeTo(rootDir)}"
                }
            }
            val mainManifest = file("app/src/main/AndroidManifest.xml")
            val mainManifestText = if (mainManifest.isFile) mainManifest.readText() else ""
            if ("<package android:name=\"io.github.supermonster003.autojs6.plugin.python.runtime\" />" !in mainManifestText) {
                violations += "main manifest is missing exact official Python Runtime package visibility"
            }
            fileTree("app/src") {
                include("main/**/*.so", "release/**/*.so", "app/**/*.so", "inrt/**/*.so")
            }.files
                .filter { "python" in it.name.lowercase() }
                .forEach { file -> violations += "embedded Python native library in ${file.relativeTo(rootDir)}" }

            listOf(
                "autojs.python.preview.enabled",
                "autojs.python.release.candidate.enabled",
                "PYTHON_RUNTIME_PREVIEW_ENABLED",
                "PYTHON_RUNTIME_RELEASE_CANDIDATE_ENABLED",
            ).forEach { staleFlag ->
                if (staleFlag in appBuildText) {
                    violations += "Python host still depends on retired build flag $staleFlag"
                }
            }

            val channelPolicy = file(
                "app/src/main/java/org/autojs/autojs/script/PythonRuntimeChannelPolicy.kt",
            )
            val channelPolicyText = if (channelPolicy.isFile) channelPolicy.readText() else ""
            listOf(
                "fun isEnabled(isInrt: Boolean): Boolean = !isInrt",
                "isInrt = BuildConfig.isInrt",
            ).forEach { marker ->
                if (marker !in channelPolicyText) {
                    violations += "Python host channel policy is missing $marker"
                }
            }
            val previewFeature = file(
                "app/src/main/java/org/autojs/autojs/script/PythonScriptSource.kt",
            )
            if (
                !previewFeature.isFile ||
                "PythonRuntimeChannelPolicy.isEnabledForCurrentBuild" !in previewFeature.readText()
            ) {
                violations += "Python source admission does not use the shared stable host-flavor policy"
            }

            val transport = file(
                "app/src/main/java/org/autojs/autojs/core/plugin/python/PythonRuntimeOfficialTransport.kt",
            )
            val transportText = if (transport.isFile) transport.readText() else ""
            listOf(
                "PluginEnableStore.isEnabled(",
                "pluginEnabled = pluginEnabledBeforeInspection && isPluginEnabled()",
                "requirePluginEnabled()",
                "Intent().setComponent(OFFICIAL_COMPONENT)",
            ).forEach { marker ->
                if (marker !in transportText) {
                    violations += "Python official transport is missing $marker"
                }
            }
            val bindServiceIndex = transportText.indexOf("applicationContext.bindService(")
            val enableCheckIndex = transportText.indexOf("requirePluginEnabled()")
            if (bindServiceIndex < 0 || enableCheckIndex !in 0 until bindServiceIndex) {
                violations += "Python official transport does not check logical enablement before binding"
            }

            val client = file(
                "app/src/main/java/org/autojs/autojs/core/plugin/python/PythonRuntimeClient.kt",
            )
            val clientText = if (client.isFile) client.readText() else ""
            listOf(
                "PluginStateChangeRegistry.addListener(pluginStateChangedListener)",
                "PluginStateChangeRegistry.removeListener(pluginStateChangedListener)",
                "failClosedForPluginStateChange",
                "retireRemoteSession",
            ).forEach { marker ->
                if (marker !in clientText) {
                    violations += "Python runtime lifecycle handling is missing $marker"
                }
            }
            val clientPreconditionIndex = clientText.indexOf("checkProviderPreconditions(installed)")
            val clientBindIndex = clientText.indexOf("transport.bind(")
            if (clientBindIndex < 0 || clientPreconditionIndex !in 0 until clientBindIndex) {
                violations += "Python runtime client may bind before rejecting a logically disabled provider"
            }

            val fixtureRoot = file("test-apps/python-runtime-conformance")
            val fixtureBuild = fixtureRoot.resolve("build.gradle.kts")
            val fixtureManifest = fixtureRoot.resolve("src/main/AndroidManifest.xml")
            check(fixtureBuild.isFile && fixtureManifest.isFile) { "Python R1 conformance fixture is incomplete" }
            val fixtureBuildText = fixtureBuild.readText()
            val fixtureManifestText = fixtureManifest.readText()
            if (
                "abortOnError = true" !in fixtureBuildText ||
                "withBuildType(\"release\")" !in fixtureBuildText ||
                "variant.enable = false" !in fixtureBuildText
            ) {
                violations += "conformance fixture is not fail-closed debug-only"
            }
            listOf(
                "android:testOnly=\"true\"",
                "android:exported=\"true\"",
                "android:permission=\"org.autojs.permission.PLUGIN\"",
                "android:process=\":python_runtime\"",
                "org.autojs.plugin.python.RUNTIME",
            ).forEach { marker ->
                if (marker !in fixtureManifestText) violations += "conformance manifest is missing $marker"
            }
            if (
                "android.permission.INTERNET" in fixtureManifestText ||
                "android.intent.category.LAUNCHER" in fixtureManifestText
            ) {
                violations += "conformance fixture requests network or launcher exposure"
            }
            fileTree(fixtureRoot) {
                include("**/*.gradle", "**/*.gradle.kts", "src/main/**")
                exclude("build/**")
            }.files.filter { it.isFile }.forEach { file ->
                val text = file.readText()
                if ("chaquopy" in text.lowercase() || "com.chaquo.python" in text) {
                    violations += "conformance fixture embeds a Python runtime in ${file.relativeTo(rootDir)}"
                }
            }
            fileTree(fixtureRoot) { include("src/main/**/*.so") }.files.forEach { file ->
                violations += "conformance fixture contains native code in ${file.relativeTo(rootDir)}"
            }

            val consumerRoot = file("test-apps/python-runtime-api-consumer")
            val consumerBuild = consumerRoot.resolve("build.gradle.kts")
            val consumerManifest = consumerRoot.resolve("src/main/AndroidManifest.xml")
            check(consumerBuild.isFile && consumerManifest.isFile) { "Python R1 distribution consumer is incomplete" }
            val consumerBuildText = consumerBuild.readText()
            val consumerBuildCode = consumerBuildText
                .replace(Regex("""(?s)/\*.*?\*/"""), "")
                .lineSequence()
                .joinToString("\n") { line -> line.substringBefore("//") }
            if (
                Regex("""\bproject\s*\(""").containsMatchIn(consumerBuildCode) ||
                Regex("""\bprojects\.""").containsMatchIn(consumerBuildCode)
            ) {
                violations += "distribution consumer has a source-project dependency"
            }
            listOf(
                "abortOnError = true",
                "withBuildType(\"release\")",
                "variant.enable = false",
                "protocol-wire-api-debug.aar",
                "python-runtime-api-debug.aar",
                "preparePythonR1ApiDistribution",
            ).forEach { marker ->
                if (marker !in consumerBuildText) violations += "distribution consumer is missing $marker"
            }
            if ("android:testOnly=\"true\"" !in consumerManifest.readText()) {
                violations += "distribution consumer is not test-only"
            }
            check(violations.isEmpty()) {
                violations.joinToString(prefix = "Python R1 stable host boundary violations:\n", separator = "\n")
            }
        }
    }

    val verifyPythonR1AndroidHarnessCompileGate = register("verifyPythonR1AndroidHarnessCompileGate") {
        group = "verification"
        description =
            "Compiles the Python AndroidTest host harness and instrumentation sources without running a device."
        dependsOn(
            ":app:compileAppDebugKotlin",
            ":app:compileAppDebugAndroidTestKotlin",
            verifyPythonR1DefaultOffHostBoundary,
        )
        doFirst {
            val connectedTasks = gradle.taskGraph.allTasks.filter(::isPythonAndroidDeviceExecutionTask)
            check(connectedTasks.isEmpty()) {
                "Python R1 Android harness compilation must not run AndroidTest device tasks: " +
                    connectedTasks.joinToString { task -> task.path }
            }
        }
        doLast {
            val debugManifest = file("app/src/debug/AndroidManifest.xml")
            check(debugManifest.isFile) { "Missing debug manifest for the Python R1 Android harness" }
            val manifestDocument = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setXIncludeAware(false)
                setExpandEntityReferences(false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }.newDocumentBuilder().parse(debugManifest)
            val queryNodes = manifestDocument.getElementsByTagName("queries")
            val hasExactRuntimeQuery = (0 until queryNodes.length).any { queryIndex ->
                val query = queryNodes.item(queryIndex) as org.w3c.dom.Element
                val intents = query.getElementsByTagName("intent")
                (0 until intents.length).any { intentIndex ->
                    val intent = intents.item(intentIndex) as org.w3c.dom.Element
                    val actions = intent.getElementsByTagName("action")
                    (0 until actions.length).any { actionIndex ->
                        val action = actions.item(actionIndex) as org.w3c.dom.Element
                        action.getAttributeNS(
                            "http://schemas.android.com/apk/res/android",
                            "name",
                        ) == "org.autojs.plugin.python.RUNTIME"
                    }
                }
            }
            check(hasExactRuntimeQuery) {
                "The debug manifest lacks the exact Python runtime <queries><intent><action> entry"
            }

            val androidTestHarnessSource = file(
                "app/src/androidTest/java/org/autojs/autojs/core/plugin/python/" +
                    "AndroidPythonRuntimeConformanceTransport.kt",
            )
            check(androidTestHarnessSource.isFile) {
                "Missing the exact Python AndroidTest transport source"
            }
            val androidTestHarnessText = androidTestHarnessSource.readText()
            listOf(
                "IPythonRuntimeProvider.Stub.asInterface",
                "Intent().setComponent",
                "linkToDeath",
                "PythonRuntimeContract.RUNTIME_ACTION",
            ).forEach { marker ->
                check(marker in androidTestHarnessText) {
                    "Python AndroidTest transport is missing $marker"
                }
            }

            val androidTestSource = file(
                "app/src/androidTest/java/org/autojs/autojs/core/plugin/python/" +
                    "PythonRuntimeAndroidConformanceTest.kt",
            )
            check(androidTestSource.isFile) {
                "Missing the exact Python R1 Android instrumentation source"
            }
            val androidTestText = androidTestSource.readText()
            listOf(
                "@Test",
                "Binder.getCallingUid",
                "ParcelFileDescriptor",
                "autojs.python.r1.conformance.enabled",
                "exactActionDiscoveryBindsWithNullActionAndReturnsValidatedMetadata",
                "realPfdNormalSessionEnforcesCreditsOutputResultAndCallbackUid",
            ).forEach { marker ->
                check(marker in androidTestText) {
                    "Python R1 Android instrumentation source is missing $marker"
                }
            }

            val fakeServiceSource = file(
                "test-apps/python-runtime-conformance/src/main/java/org/autojs/plugin/python/" +
                    "runtime/fake/service/FakePythonRuntimeService.kt",
            )
            val fakeServiceText = if (fakeServiceSource.isFile) fakeServiceSource.readText() else ""
            listOf("intent != null", "intent.action == null", "intent.component == ComponentName").forEach { marker ->
                check(marker in fakeServiceText) {
                    "Python R1 fake service no longer enforces component-only/action-null binding: missing $marker"
                }
            }
        }
    }

    val verifyPythonR1ApiDistributionConsumer = register("verifyPythonR1ApiDistributionConsumer") {
        group = "verification"
        description = "Compiles and tests a consumer using only the staged Python Runtime API AAR distribution."
        dependsOn(
            "preparePythonR1ApiDistribution",
            ":test-apps:python-runtime-api-consumer:testDebugUnitTest",
            ":test-apps:python-runtime-api-consumer:lintDebug",
            ":test-apps:python-runtime-api-consumer:assembleDebug",
        )
        doLast {
            val directory = pythonR1DistributionDirectory.get().asFile
            val metadata = directory.resolve("distribution.json")
            val artifacts = listOf(
                directory.resolve("protocol-wire-api-debug.aar"),
                directory.resolve("python-runtime-api-debug.aar"),
            )
            check(metadata.isFile) { "Missing Python R1 distribution metadata: $metadata" }
            val metadataText = metadata.readText()
            artifacts.forEach { artifact ->
                check(artifact.isFile && artifact.length() > 0L) { "Missing Python R1 distribution artifact: $artifact" }
                check(artifact.name in metadataText && pythonGateSha256(artifact) in metadataText) {
                    "Python R1 distribution metadata does not match ${artifact.name}"
                }
            }
            val consumerProject = project(":test-apps:python-runtime-api-consumer")
            val consumerCompileClasspath = consumerProject.configurations.getByName("debugCompileClasspath")
            val projectComponents = consumerCompileClasspath.incoming
                .resolutionResult
                .allComponents
                .map { component -> component.id }
                .filterIsInstance<org.gradle.api.artifacts.component.ProjectComponentIdentifier>()
                .filter { component -> component.projectPath != consumerProject.path }
            check(projectComponents.isEmpty()) {
                "Python R1 distribution consumer resolved source projects: " +
                    projectComponents.joinToString { component -> component.projectPath }
            }
            val expectedDistributionFiles = artifacts.map(File::getCanonicalFile).toSet()
            val implementationDependencies = consumerProject.configurations
                .getByName("implementation")
                .dependencies
                .toList()
            val nonFileDependencies = implementationDependencies
                .filterNot { dependency ->
                    dependency is org.gradle.api.artifacts.FileCollectionDependency
                }
            check(nonFileDependencies.isEmpty()) {
                "Python R1 distribution consumer implementation must not declare project or external module " +
                    "dependencies: ${nonFileDependencies.map { dependency -> dependency.group + ":" + dependency.name }}"
            }
            val declaredDistributionFiles = implementationDependencies
                .filterIsInstance<org.gradle.api.artifacts.FileCollectionDependency>()
                .flatMap { dependency -> dependency.files.files }
                .map(File::getCanonicalFile)
            check(
                declaredDistributionFiles.size == expectedDistributionFiles.size &&
                    declaredDistributionFiles.toSet() == expectedDistributionFiles
            ) {
                "Python R1 consumer file dependencies differ from the exact staged AAR pair; " +
                    "declared=$declaredDistributionFiles expected=$expectedDistributionFiles"
            }
            val consumerApk = file(
                "test-apps/python-runtime-api-consumer/build/outputs/apk/debug/" +
                    "python-runtime-api-consumer-debug.apk",
            )
            check(consumerApk.isFile && consumerApk.length() > 0L) {
                "Python R1 distribution consumer APK was not assembled: $consumerApk"
            }
        }
    }

    register("verifyPythonR0").configure {
        group = "verification"
        description = "Verifies the build-time Python Runtime v1 protocol baseline without changing host routing."
        dependsOn(
            ":plugin-api:protocol-wire-api:testDebugUnitTest",
            ":plugin-api:protocol-wire-api:assembleDebug",
            ":plugin-api:python-runtime-api:testDebugUnitTest",
            ":plugin-api:python-runtime-api:lintDebug",
            ":plugin-api:python-runtime-api:assembleDebug",
            verifyPythonR0HostBoundary,
        )
        outputs.file(pythonR0Receipt)
        outputs.upToDateWhen { false }
        doLast {
            pythonR0Receipt.get().asFile.apply {
                parentFile.mkdirs()
                writeText(
                    """{
                      "schema": 1,
                      "status": "PASS",
                      "scope": "JVM_STATIC_ONLY",
                      "hostBoundaryChecked": true,
                      "androidBinderRuntime": false,
                      "cpythonExecution": false,
                      "hostRoutingChecked": false,
                      "hostRoutingChangedByGate": false,
                      "deviceAcceptance": false
                    }
                    """.trimIndent() + "\n",
                )
            }
        }
    }

    register("verifyPythonR1ConformanceGate").configure {
        group = "verification"
        description = "Verifies the static/JVM and Android-compile R1 Python conformance checkpoint."
        dependsOn(
            "verifyPythonR0",
            ":plugin-api:protocol-wire-api:lintDebug",
            ":app:testAppDebugUnitTest",
            ":test-apps:python-runtime-conformance:testDebugUnitTest",
            ":test-apps:python-runtime-conformance:lintDebug",
            ":test-apps:python-runtime-conformance:assembleDebug",
            verifyPythonR1ApiDistributionConsumer,
            verifyPythonR1DefaultOffHostBoundary,
            verifyPythonR1AndroidHarnessCompileGate,
        )
        outputs.file(pythonR1Receipt)
        outputs.upToDateWhen { false }
        doLast {
            fun requireExactSuites(
                directory: File,
                expected: Set<String>,
                suitePrefix: String,
                label: String,
            ) {
                val present = directory.listFiles()
                    .orEmpty()
                    .filter { result -> result.isFile && result.name.startsWith(suitePrefix) }
                    .map(File::getName)
                    .toSet()
                check(present == expected) {
                    "Exact $label suites changed; missing=${expected - present}, unexpected=${present - expected}"
                }
            }

            val expectedHostPolicyResults = setOf(
                "TEST-org.autojs.autojs.core.plugin.python.PythonProjectWorkspacePolicyTest.xml",
                "TEST-org.autojs.autojs.core.plugin.python.PythonRuntimeProviderSelectionPolicyTest.xml",
                "TEST-org.autojs.autojs.core.plugin.python.PythonRuntimeHostPlanningTest.xml",
                "TEST-org.autojs.autojs.core.plugin.python.PythonRuntimePluginStateChangePolicyTest.xml",
            )
            val hostPolicyResultDirectory = file("app/build/test-results/testAppDebugUnitTest")
            requireExactSuites(
                hostPolicyResultDirectory,
                expectedHostPolicyResults,
                "TEST-org.autojs.autojs.core.plugin.python.",
                "Python host policy",
            )

            val expectedFakeProviderResults = setOf(
                "TEST-org.autojs.plugin.python.runtime.fake.FakeOwnershipAndSnapshotTest.xml",
                "TEST-org.autojs.plugin.python.runtime.fake.FakePythonFixtureAndCaseTest.xml",
                "TEST-org.autojs.plugin.python.runtime.fake.FakePythonSessionMachineTest.xml",
            )
            val fakeProviderResultDirectory = file(
                "test-apps/python-runtime-conformance/build/test-results/testDebugUnitTest",
            )
            requireExactSuites(
                fakeProviderResultDirectory,
                expectedFakeProviderResults,
                "TEST-org.autojs.plugin.python.runtime.fake.",
                "Python fake provider",
            )

            val expectedConsumerResults = setOf(
                "TEST-org.autojs.plugin.python.runtime.consumer.PythonRuntimeApiDistributionSmokeTest.xml",
            )
            val consumerResultDirectory = file(
                "test-apps/python-runtime-api-consumer/build/test-results/testDebugUnitTest",
            )
            requireExactSuites(
                consumerResultDirectory,
                expectedConsumerResults,
                "TEST-org.autojs.plugin.python.runtime.consumer.",
                "Python API consumer",
            )

            val summaries = listOf(
                readPythonGateTestSummary(
                    ":test-apps:python-runtime-conformance:testDebugUnitTest",
                    fakeProviderResultDirectory,
                ) { result -> result.name in expectedFakeProviderResults },
                readPythonGateTestSummary(
                    ":test-apps:python-runtime-api-consumer:testDebugUnitTest",
                    consumerResultDirectory,
                ) { result -> result.name in expectedConsumerResults },
                readPythonGateTestSummary(
                    ":app:testAppDebugUnitTest (Python policy classes)",
                    hostPolicyResultDirectory,
                ) { result -> result.name in expectedHostPolicyResults },
            )
            val artifacts = listOf(
                pythonR1DistributionDirectory.get().asFile.resolve("protocol-wire-api-debug.aar"),
                pythonR1DistributionDirectory.get().asFile.resolve("python-runtime-api-debug.aar"),
                file(
                    "test-apps/python-runtime-conformance/build/outputs/apk/debug/" +
                        "python-runtime-conformance-debug.apk",
                ),
                file(
                    "test-apps/python-runtime-api-consumer/build/outputs/apk/debug/" +
                        "python-runtime-api-consumer-debug.apk",
                ),
            )
            pythonR1Receipt.get().asFile.apply {
                parentFile.mkdirs()
                writeText(
                    buildString {
                        appendLine("{")
                        appendLine("  \"schema\": 1,")
                        appendLine("  \"gate\": \"verifyPythonR1ConformanceGate\",")
                        appendLine("  \"status\": \"PASS\",")
                        appendLine("  \"scope\": \"JVM_STATIC_ANDROID_COMPILE\",")
                        appendLine("  \"phaseCompletion\": \"STABLE_APP_HOST_COMPILED_DEVICE_NOT_RUN\",")
                        appendLine("  \"claims\": {")
                        appendLine("    \"protocolJvm\": true,")
                        appendLine("    \"fakeProviderJvm\": true,")
                        appendLine("    \"hostPolicyJvm\": true,")
                        appendLine("    \"apiAndFakeProviderAidlCompile\": true,")
                        appendLine("    \"conformanceApkAssembled\": true,")
                        appendLine("    \"distributionConsumerCompiled\": true,")
                        appendLine("    \"defaultOffHostBoundary\": true,")
                        appendLine("    \"androidTestSourcesCompiled\": true,")
                        appendLine("    \"androidTestHostHarness\": true,")
                        appendLine("    \"androidBinderRuntime\": false,")
                        appendLine("    \"realPfdKernelSemantics\": false,")
                        appendLine("    \"callerUidSignerRuntime\": false,")
                        appendLine("    \"fakeProviderDeviceConformance\": false,")
                        appendLine("    \"productionPluginDeviceAcceptance\": false,")
                        appendLine("    \"cpythonExecution\": false,")
                        appendLine("    \"hostRouting\": true,")
                        appendLine("    \"hostRoutingScope\": \"STABLE_APP_ONLY_INRT_UNSUPPORTED\"")
                        appendLine("  },")
                        appendLine("  \"artifacts\": [")
                        artifacts.forEachIndexed { index, artifact ->
                            val suffix = if (index == artifacts.lastIndex) "" else ","
                            appendLine(
                                    "    {\"path\": \"${artifact.relativeTo(rootDir).invariantSeparatorsPath}\", " +
                                    "\"bytes\": ${artifact.length()}, " +
                                    "\"sha256\": \"${pythonGateSha256(artifact)}\"}$suffix",
                            )
                        }
                        appendLine("  ],")
                        appendLine("  \"testSuites\": [")
                        summaries.forEachIndexed { index, summary ->
                            val suffix = if (index == summaries.lastIndex) "" else ","
                            appendLine(
                                "    {\"task\": \"${summary.task}\", \"tests\": ${summary.tests}, " +
                                    "\"failures\": ${summary.failures}, \"errors\": ${summary.errors}, " +
                                    "\"requiredCasesSkipped\": ${summary.skipped}}$suffix",
                            )
                        }
                        appendLine("  ]")
                        appendLine("}")
                    },
                )
            }
        }
    }

    register("verifyPythonR1DeviceConformanceGate").configure {
        group = "verification"
        description =
            "Fail-closed skeleton for one exact-device Python R1 Binder/PFD conformance matrix cell."
        dependsOn("verifyPythonR1ConformanceGate")
        outputs.file(pythonR1DeviceReceipt)
        outputs.upToDateWhen { false }
        doFirst {
            val receipt = pythonR1DeviceReceipt.get().asFile
            check(!receipt.exists() || receipt.delete()) {
                "Unable to remove stale Python R1 device receipt: $receipt"
            }

            val serial = providers.gradleProperty("autojs.python.r1.deviceSerial")
                .orNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: error(
                    "Python R1 device conformance requires " +
                        "-Pautojs.python.r1.deviceSerial=<exact-serial>",
                )
            check(!serial.startsWith("-") && serial.matches(Regex("[A-Za-z0-9._:-]+"))) {
                "Invalid exact Python R1 device serial"
            }

            val expectedApi = providers.gradleProperty("autojs.python.r1.expectedApi")
                .orNull
                ?.trim()
                ?.toIntOrNull()
                ?: error(
                    "Python R1 device conformance requires " +
                        "-Pautojs.python.r1.expectedApi=<integer>",
                )
            check(expectedApi >= 24) { "Python R1 device API must be 24 or newer" }

            val expectedAbi = providers.gradleProperty("autojs.python.r1.expectedAbi")
                .orNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: error(
                    "Python R1 device conformance requires " +
                        "-Pautojs.python.r1.expectedAbi=<exact-abi>",
                )
            check(expectedAbi in setOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")) {
                "Unsupported exact Python R1 device ABI: $expectedAbi"
            }

            val mutationConfirmed = providers.gradleProperty("autojs.python.r1.confirmDeviceMutation")
                .orNull
                ?.trim()
            check(mutationConfirmed == "true") {
                "Python R1 device conformance requires the exact opt-in " +
                    "-Pautojs.python.r1.confirmDeviceMutation=true"
            }

            System.getenv("ANDROID_SERIAL")?.trim()?.takeIf(String::isNotEmpty)?.let { environmentSerial ->
                check(environmentSerial == serial) {
                    "ANDROID_SERIAL differs from -Pautojs.python.r1.deviceSerial; refusing ambiguous device scope"
                }
            }

            val forbiddenTasks = gradle.taskGraph.allTasks.filter(::isPythonAndroidDeviceExecutionTask)
            check(forbiddenTasks.isEmpty()) {
                "Python R1 device conformance must use only exact-serial commands, not Gradle " +
                    "AndroidTest device tasks: ${forbiddenTasks.joinToString { task -> task.path }}"
            }
        }
        doLast {
            throw GradleException(
                "Python R1 device conformance remains fail-closed: the current fixture set does not yet " +
                    "contain the version-update and alternate-caller/signer artifacts required for the " +
                    "complete identity-drift matrix. No ADB, install, instrumentation, or PASS receipt " +
                    "is produced by this skeleton.",
            )
        }
    }
}

gradle.taskGraph.whenReady {
    if (
        hasTask(":verifyPythonR1AndroidHarnessCompileGate") ||
        hasTask(":verifyPythonR1ConformanceGate") ||
        hasTask(":verifyPythonR1DeviceConformanceGate")
    ) {
        val forbiddenDeviceTasks = allTasks.filter(::isPythonAndroidDeviceExecutionTask)
        check(forbiddenDeviceTasks.isEmpty()) {
            "Python R1 gates reject Gradle AndroidTest device tasks before execution: " +
                forbiddenDeviceTasks.joinToString { task -> task.path }
        }
    }
    if (hasTask(":verifyPythonR0")) {
        val receipt = pythonR0Receipt.get().asFile
        check(!receipt.exists() || receipt.delete()) {
            "Unable to remove stale Python R0 gate receipt: $receipt"
        }
    }
    if (
        hasTask(":verifyPythonR1AndroidHarnessCompileGate") ||
        hasTask(":verifyPythonR1ConformanceGate")
    ) {
        val receipt = pythonR1Receipt.get().asFile
        check(!receipt.exists() || receipt.delete()) {
            "Unable to remove stale Python R1 gate receipt: $receipt"
        }
        val deviceReceipt = pythonR1DeviceReceipt.get().asFile
        check(!deviceReceipt.exists() || deviceReceipt.delete()) {
            "Unable to invalidate stale Python R1 device receipt: $deviceReceipt"
        }
    }
    if (hasTask(":verifyPythonR1DeviceConformanceGate")) {
        val receipt = pythonR1DeviceReceipt.get().asFile
        check(!receipt.exists() || receipt.delete()) {
            "Unable to remove stale Python R1 device gate receipt: $receipt"
        }
    }
    if (hasTask(":verifyPythonReleaseApiDistributionGate")) {
        val receipt = pythonReleaseApiDistributionReceipt.get().asFile
        check(!receipt.exists() || receipt.delete()) {
            "Unable to remove stale Python release API distribution receipt: $receipt"
        }
        val distribution = pythonReleaseApiDistributionDirectory.get().asFile
        check(!distribution.exists() || distribution.deleteRecursively()) {
            "Unable to remove stale Python release API distribution: $distribution"
        }
    }
}
