@file:Suppress("SpellCheckingInspection", "AvoidApplyPluginMethod")

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration
import org.autojs.build.alignment.ElfLoadAlignment
import org.autojs.build.alignment.VerifyNativePageAlignment
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
    id("io.github.supermonster003.autojs6-native-alignment")
    id("com.google.devtools.ksp")
    id("idea")
}

val globalApplicationId = "org.autojs.autojs6"

// The FileProvider authority of each build goes into the manifest placeholder "authorities" and into this
// BuildConfig field from the same value, so AppFileProvider builds Uris for the provider the app declares.
val buildConfigFieldNameFileProviderAuthority = "FILE_PROVIDER_AUTHORITY"

apply(from = "compose-shared-classpath.gradle.kts")
if (providers.gradleProperty("composeUiSpike").orNull == "true") {
    android.buildTypes.named("debug") {
        val authorities = "org.autojs.autojs6.compose.spike.fileprovider"
        applicationIdSuffix = ".compose.spike"
        manifestPlaceholders["authorities"] = authorities
        // AppFileProvider.AUTHORITY reads this field; a build type field overrides the flavor's one.
        buildConfigField("String", buildConfigFieldNameFileProviderAuthority, "\"$authorities\"")
        manifestPlaceholders["appName"] = "Compose spike host"
    }
}

val flavorDimension = "channel"
val flavorNameApp = "app"
val flavorNameInrt = "inrt"
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val sdkLintVerification = providers.gradleProperty("autojs.sdk.lint.verify")
    .map { value ->
        value.trim().toBooleanStrictOrNull()
            ?: throw GradleException("autojs.sdk.lint.verify must be true or false; was '$value'")
    }
    .orElse(false)
    .get()
val isInrtAssembleTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(':').startsWith("assemble${flavorNameInrt.uppercaseFirstChar()}", ignoreCase = true)
}
val nodeInspectorReleaseAndroidTest = providers.gradleProperty("nodeInspectorReleaseAndroidTest")
    .map(String::toBoolean).orElse(false)
val yoloR6ReleaseAndroidTestProperty = "yoloR6ReleaseAndroidTest"
val yoloR6ReleaseAndroidTest = providers.gradleProperty(yoloR6ReleaseAndroidTestProperty)
    .map { value ->
        when (val normalized = value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> throw GradleException(
                "$yoloR6ReleaseAndroidTestProperty must be one of true/false, 1/0, yes/no, or on/off; was '$normalized'",
            )
        }
    }
    .orElse(false)
val buildConfigFieldNameIsInrt = "is${flavorNameInrt.uppercaseFirstChar()}"
val buildTypeNames = listOf(buildTypeDebug, buildTypeRelease)
val releaseFlavorNames = listOf(flavorNameApp, flavorNameInrt)
val supportedResourceLocales = setOf(
    "ar",
    "en",
    "es",
    "fr",
    "ja",
    "ko",
    "ru",
    "zh",
    "zh-rHK",
    "zh-rTW",
)
val apkFileExtension = utils.FILE_EXTENSION_APK
val apkBuilderBuiltInTemplateAsset = "template.apk"
val apkBuilderTemplateDistributionTaskName = "assembleApkBuilderTemplateDistribution"
val imageQuantNativeLibraryFileName = "libpngquant_bridge.so"
val openCvNativeLibraryFileName = "libopencv_java4.so"
val openCvJavaApiAar = rootDir.resolve("libs/org-opencv-java-4_8_0/opencv-java-4.8.0.aar")
val apkBuilderIncludeBuiltInTemplateProperty = "autojs.apkBuilder.includeBuiltInTemplate"
val apkBuilderTemplateDistributionRequested = providers.provider {
    gradle.startParameter.taskNames.any { taskName ->
        taskName.substringAfterLast(':').equals(apkBuilderTemplateDistributionTaskName, ignoreCase = true)
    }
}
val apkBuilderIncludeBuiltInTemplateByProperty = providers.gradleProperty(apkBuilderIncludeBuiltInTemplateProperty)
    .map { value ->
        value.trim().lowercase().let { it !in setOf("false", "0", "no", "off") }
    }
    .orElse(false)
val apkBuilderIncludeBuiltInTemplate = providers.provider {
    if (apkBuilderTemplateDistributionRequested.get()) {
        false
    } else {
        apkBuilderIncludeBuiltInTemplateByProperty.get()
    }
}
// LeakCanary is included in debug builds by default (docs/memory/ROADMAP.md D5 / T5); release builds never include it.
// zh-CN: debug 构建默认包含 LeakCanary (docs/memory/ROADMAP.md D5 / T5); release 构建从不包含.
val leakCanaryEnabled = providers.gradleProperty("autojs.leakcanary.enabled").orElse("true").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
val jvmSourceExperimentalEnabled = providers.gradleProperty("autojs.jvmSource.experimental")
    .map { value -> value.trim().lowercase() in setOf("true", "1", "yes", "on") }
    .orElse(false)

fun verifyOpenCvPluginSeparation(
    apkDirectory: File,
    javaApiAar: File,
    nativeLibraryFileName: String,
) {
    if (!javaApiAar.isFile) {
        throw GradleException("OpenCV Java API AAR is missing: ${javaApiAar.path}")
    }
    ZipFile(javaApiAar).use { aar ->
        val nativeEntries = aar.entries().asSequence()
            .filterNot { it.isDirectory }
            .map { it.name }
            .filter { it.startsWith("jni/") || it.endsWith("/$nativeLibraryFileName") }
            .toList()
        if (nativeEntries.isNotEmpty()) {
            throw GradleException("OpenCV Java API AAR unexpectedly contains native payloads: $nativeEntries")
        }
    }

    val apkFiles = apkDirectory.walkTopDown()
        .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        .toList()
    if (apkFiles.isEmpty()) {
        throw GradleException("No host APKs were found below ${apkDirectory.path}")
    }
    val violations = linkedMapOf<String, List<String>>()
    apkFiles.forEach { apk ->
        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it.substringAfterLast('/') == nativeLibraryFileName }
                .toList()
            if (entries.isNotEmpty()) {
                violations[apk.name] = entries
            }
        }
    }
    if (violations.isNotEmpty()) {
        throw GradleException("AutoJs6 host APKs still package OpenCV JNI: $violations")
    }
    println("Verified ${apkFiles.size} AutoJs6 host APK(s) without $nativeLibraryFileName")
}

fun verifyImageQuantPluginSeparation(
    apkDirectory: File,
    nativeLibraryFileName: String,
) {
    val bundledLibPngPattern = Regex("libpng(?:16d?)?\\.so(?:\\..+)?")
    val apkFiles = apkDirectory.walkTopDown()
        .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        .toList()
    if (apkFiles.isEmpty()) {
        throw GradleException("No host APKs were found below ${apkDirectory.path}")
    }
    val violations = linkedMapOf<String, List<String>>()
    apkFiles.forEach { apk ->
        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { entryName ->
                    val fileName = entryName.substringAfterLast('/')
                    fileName == nativeLibraryFileName || bundledLibPngPattern.matches(fileName)
                }
                .toList()
            if (entries.isNotEmpty()) {
                violations[apk.name] = entries
            }
        }
    }
    if (violations.isNotEmpty()) {
        throw GradleException("AutoJs6 host APKs still package Image Quantization JNI or private libpng payloads: $violations")
    }
    println("Verified ${apkFiles.size} AutoJs6 host APK(s) without Image Quantization native payloads")
}

fun verifyOfflineDocsPluginSeparation(apkDirectory: File) {
    val apkFiles = apkDirectory.walkTopDown()
        .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        .toList()
    if (apkFiles.isEmpty()) {
        throw GradleException("No host APKs were found below ${apkDirectory.path}")
    }
    val violations = linkedMapOf<String, List<String>>()
    apkFiles.forEach { apk ->
        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it == "assets/docs" || it.startsWith("assets/docs/") }
                .toList()
            if (entries.isNotEmpty()) {
                violations[apk.name] = entries
            }
        }
    }
    if (violations.isNotEmpty()) {
        throw GradleException("AutoJs6 host APKs still package Offline Docs assets: $violations")
    }
    println("Verified ${apkFiles.size} AutoJs6 host APK(s) without assets/docs")
}

val resourcePickFirstPatterns = listOf(
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
)

val resourceExcludePatterns = listOf(
    "com/**/*",
    "frameworks/**/*",
    // prism4j-bundler ships its Prism_*.java language templates and GrammarLocator template as resources;
    // nothing reads them at runtime. Patterns are java.nio glob patterns: "languages/**/*" would only match
    // entries nested at least two levels deep, so the single-level form is used.
    // zh-CN: prism4j-bundler 把 Prism_*.java 语言模板与 GrammarLocator 模板打包为资源, 运行时无消费者.
    // 模式是 java.nio glob: "languages/**/*" 只匹配至少两层深的条目, 因此使用单层形式.
    "languages/*",
    "GrammarLocator.template.java",
    // kotlinx-coroutines debug probes.
    // zh-CN: kotlinx-coroutines 调试探针.
    "DebugProbesKt.bin",
    // Script module metadata generated by ksp-script-modules-processor for the d.ts / documentation tools; not read at runtime.
    // zh-CN: ksp-script-modules-processor 为 d.ts / 文档工具生成的脚本模块元数据, 运行时不读取.
    "autojs6/script-modules.json",
)

// Only the zh-Hans and en release histories ship inside the APK (size roadmap P6.5, D14); the other
// languages are fetched online and cached, with the en asset as the offline fallback (see
// ReleaseHistoryRepository.readLocalSampleCandidates). doc/CHANGELOG.md duplicates zh-Hans and is not read.
// The generator and the repository files are unchanged; the pruning happens at asset merge time.
// zh-CN: APK 只内置 zh-Hans 与 en 两份版本历史 (体积 roadmap P6.5, D14); 其余语言在线获取并缓存, 离线以 en 资产回退
// (见 ReleaseHistoryRepository.readLocalSampleCandidates). doc/CHANGELOG.md 与 zh-Hans 内容相同且无代码读取.
// 生成脚本与仓库文件不变, 剪除发生在资产合并期.
val bundledChangelogLanguageTags = listOf("zh-Hans", "en")
val prunedChangelogAssets = listOf("ar", "es", "fr", "ja", "ko", "ru", "zh-Hans", "zh-Hant-HK", "zh-Hant-TW", "en")
    .filterNot { it in bundledChangelogLanguageTags }
    .map { "doc/CHANGELOG-$it.md" } + "doc/CHANGELOG.md"

val appMergedAssetPruneIncludes = listOf(
    "declarations/**/*",
    "default_key_store.bks",
    "docs/**/*",
    "sample/declarations/**/*",
    "modules/obsolete/**/*",
) + prunedChangelogAssets

val inrtMergedAssetPruneIncludes = listOf(
    "default_key_store.bks",
    "doc/**/*",
    "docs/**/*",
    "editor/**/*",
    "fonts/**/*",
    "indices/**/*",
    "js-beautify/**/*",
    "mlkit-google-ocr-models/**/*",
    "models/**/*",
    "modules/**/*.d.ts",
    "modules/**/*.md",
    "modules/obsolete/**/*",
    "project/**/*",
    "sample/**/*",
    "stored-locales/**/*",
    "autojs.keystore",
)

private fun String.asGradleTaskNamePart(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun Any.reflectedNoArgMethod(name: String): Any? =
    javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }?.invoke(this)

private fun Any.reflectedOutputDir(): File? =
    when (val dir = reflectedNoArgMethod("getOutputDir")) {
        is File -> dir
        is Directory -> dir.asFile
        else -> null
    }

@Suppress("UNCHECKED_CAST")
private fun Any.reflectedOutputFileNameProperty(): Property<String>? =
    reflectedNoArgMethod("getOutputFileName") as? Property<String>

private data class RuntimeKitDigestEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val minLoadAlign: Long? = null,
)

private fun normalizeRuntimeKitVersion(value: String): String =
    value.replace("\\s+".toRegex(), "-").lowercase()

private fun String.toJsonLiteral(): String = buildString {
    append('"')
    this@toJsonLiteral.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    append("\\u%04x".format(ch.code))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

private fun File.sha256String(bufferSize: Int = 256 * 1024): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun zipEntrySha256(zipFile: ZipFile, entry: ZipEntry): String {
    val digest = MessageDigest.getInstance("SHA-256")
    zipFile.getInputStream(entry).use { input ->
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun collectSourceDigestEntries(rootDir: File, relativePaths: List<String>): List<RuntimeKitDigestEntry> {
    return relativePaths
        .flatMap { relativePath ->
            val source = rootDir.resolve(relativePath)
            when {
                source.isFile -> listOf(source)
                source.isDirectory -> source.walkTopDown().filter { it.isFile }.toList()
                else -> emptyList()
            }
        }
        .filterNot { it.name.endsWith(".apk", ignoreCase = true) }
        .map { file ->
            RuntimeKitDigestEntry(
                path = file.relativeTo(rootDir).invariantSeparatorsPath,
                sizeBytes = file.length(),
                sha256 = file.sha256String(),
            )
        }
        .sortedBy { it.path }
}

private fun collectZipDigestEntries(apkFile: File, accept: (String) -> Boolean): List<RuntimeKitDigestEntry> {
    return ZipFile(apkFile).use { zip ->
        zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .filter { accept(it.name) }
            .map { entry ->
                RuntimeKitDigestEntry(
                    path = entry.name,
                    sizeBytes = entry.size,
                    sha256 = zipEntrySha256(zip, entry),
                    minLoadAlign = if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                        zip.getInputStream(entry).use { ElfLoadAlignment.parse(it.readNBytes(ElfLoadAlignment.PREFIX_SIZE)).minLoadAlign }
                    } else null,
                )
            }
            .sortedBy { it.path }
            .toList()
    }
}

private fun combinedDigest(entries: List<RuntimeKitDigestEntry>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.forEach { entry ->
        digest.update(entry.path.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(entry.sizeBytes.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(entry.sha256.toByteArray(Charsets.UTF_8))
        digest.update('\n'.code.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun writeDigestManifestJson(
    output: File,
    kind: String,
    entries: List<RuntimeKitDigestEntry>,
) {
    output.writeText(
        buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"kind\": ${kind.toJsonLiteral()},")
            appendLine("  \"hash\": ${combinedDigest(entries).toJsonLiteral()},")
            appendLine("  \"entries\": [")
            entries.forEachIndexed { index, entry ->
                append("    {")
                append("\"path\": ${entry.path.toJsonLiteral()}, ")
                append("\"sizeBytes\": ${entry.sizeBytes}, ")
                append("\"sha256\": ${entry.sha256.toJsonLiteral()}")
                entry.minLoadAlign?.let { append(", \"minLoadAlign\": $it") }
                append("}")
                if (index != entries.lastIndex) append(",")
                appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        },
        Charsets.UTF_8,
    )
}

private fun writePublicApiText(output: File, entries: List<RuntimeKitDigestEntry>) {
    output.writeText(
        buildString {
            appendLine("AutoJs6 Runtime Kit public API manifest")
            appendLine("schemaVersion=1")
            appendLine("hash=${combinedDigest(entries)}")
            appendLine()
            entries.forEach { entry ->
                appendLine("${entry.sha256}  ${entry.sizeBytes}  ${entry.path}")
            }
        },
        Charsets.UTF_8,
    )
}

idea {
    module {
        excludeDirs.addAll(
            listOf(
                file("$rootDir/app/src/main/assets/modules/obsolete/"),
                file("$rootDir/app/src/main/assets-app/js-beautify/"),
            )
        )
    }
}

if (apkBuilderIncludeBuiltInTemplate.get()) {
    utils.registerTemplateApkCopy(project)
}

ksp {
    // Room schema snapshots (HistoryDatabase has exportSchema = true) for migration regression tests.
    // zh-CN: Room schema 快照 (HistoryDatabase 已开启 exportSchema), 用于迁移回归测试.
    arg("room.schemaLocation", "$projectDir/schemas")
    // Source locations in the generated autojs6/script-modules.json are written relative to the repository root.
    // zh-CN: 生成的 autojs6/script-modules.json 中的源码位置相对于仓库根目录.
    arg("scriptModules.projectDir", rootDir.path)
}

// The unit test compilation sees the main script module registry (generated.ScriptModules) on its classpath, so its
// own registry of test fixtures gets another name (docs/augment/ROADMAP.md P1.2).
// zh-CN: 单元测试编译能在 classpath 上看到主源码的脚本模块注册表 (generated.ScriptModules), 测试夹具的注册表因此使用另一个名称 (ROADMAP P1.2).
tasks.withType<com.google.devtools.ksp.gradle.KspAATask>().configureEach {
    if (name.contains("UnitTest")) {
        commandLineArgumentProviders.add(CommandLineArgumentProvider { listOf("scriptModules.registry=TestScriptModules") })
    }
}

// The local Rhino build is a fat jar that bundles JLine (org.jline.**, native terminal helpers including
// Windows DLLs) for Rhino's interactive shell. The host only uses ShellContextFactory and the debugger
// API and interpreter. Exclude the JVM-only optimizer (MethodHandle bytecode requires API 26)
// and JFR instrumentation as well, so the runtime remains compatible with minSdk 24.
// zh-CN: 本地 Rhino 构建是打包了 JLine (org.jline.**, 含 Windows DLL 的原生终端助手) 的 fat jar, JLine 只供
// Rhino 交互式 shell 使用. 宿主使用 ShellContextFactory, 调试器与解释器; 派生运行时 jar 去掉 JLine,
// JVM 专用优化编译器与 JFR 监测类, 保持 minSdk 24 兼容性. 完整原始 jar 供编译与桌面 JVM 使用.
val rhinoJar = file("$rootDir/libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar")
val rhinoRuntimeExcludes = listOf(
    "org/jline/**", "META-INF/maven/org.jline/**", "META-INF/native-image/**", "META-INF/services/org/jline/**",
    "org/mozilla/javascript/optimizer/**",
) + listOf(
    "JFREmitter", "ClassCompileEvent", "InterpreterCompileEvent", "ObjectsInitEvent",
    "ParseEvent", "SafeObjectsInitEvent", "ScriptExecEvent", "TestEvent",
).map { "org/mozilla/javascript/$it*.class" }
val slimRhinoJar = tasks.register<Jar>("slimRhinoJar") {
    description = "Derives the Android Rhino interpreter jar without JVM-only components."
    inputs.property("runtimeExcludes", rhinoRuntimeExcludes)
    from(zipTree(rhinoJar)) {
        exclude(rhinoRuntimeExcludes)
    }
    includeEmptyDirs = false
    archiveFileName.set(rhinoJar.name.removeSuffix(".jar") + "-slim.jar")
    destinationDirectory.set(layout.buildDirectory.dir("slim-libs"))
}

dependencies /* Unclassified */ {
    // Loaded libraries share the host's kotlin.jvm.internal.Reflection factory through parent delegation.
    // Bundle the matching reflection implementation and its metadata so it does not fall back to ClassReference.
    // zh-CN: 外部库沿用宿主的 Kotlin 反射工厂, 必须保留配套实现及元数据, 避免退化为 ClassReference.
    implementation(kotlin("reflect"))


    // Compose
    // implementation("androidx.compose.ui:ui-android:1.6.7")

    // AndroidX Core
    implementation(libs.core.ktx)

    // AndroidX Activity
    implementation(libs.activity.ktx)

    // Compose UI Q1(b): shared AndroidX versions are the same in app/inrt and debug/release.
    // The renderer remains external; no Compose runtime is added to the host.
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.savedstate.ktx)
    implementation(libs.emoji2)
    implementation(libs.window)
    // V1 contract is provided by the host parent loader. Rendering remains in the separate plugin APK.
    implementation(project(":plugin-api:compose-ui-api"))

    // LeakCanary (debug builds of both flavors, app and inrt). When it is excluded, the debug LeakCanarySetup still compiles
    // against it and turns into a no-op at run time.
    // zh-CN: LeakCanary (app 与 inrt 两种风味的 debug 构建). 排除时 debug 的 LeakCanarySetup 仍针对它编译, 运行时变为空操作.
    if (leakCanaryEnabled) {
        debugImplementation(libs.leakcanary)
    } else {
        debugCompileOnly(libs.leakcanary)
    }

    // Android supports
    implementation(libs.cardview)

    // Material Components
    implementation(libs.material)

    // SwipeRefreshLayout
    implementation(libs.swiperefreshlayout)

    // ExifInterface (photo metadata of the Batch Rename variables)
    implementation(libs.exifinterface)

    // ConstraintLayout
    implementation(libs.constraintlayout)

    // FlexboxLayout
    implementation(libs.flexbox)

    // Common Markdown (only the core parser and the heading-anchor extension are used;
    // the JitPack aggregate coordinate also pulled commonmark-test-util and therefore JUnit into the APK)
    // zh-CN: Common Markdown (只使用核心解析器与 heading-anchor 扩展;
    // JitPack 聚合坐标还会把 commonmark-test-util 及 JUnit 带入 APK)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.heading.anchor)

    // Licenses Dialog
    implementation(libs.licensesdialog)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.adapter.rxjava2)

    // Glide
    implementation(libs.glide)

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

    // Version Compare
    implementation(libs.versioncompare)

    // ZXing Android Embedded (QR code scanning for VSCode extension connection)
    implementation(libs.zxing.android.embedded)

    // Terminal Emulator
    implementation(project(":libs:jackpal-androidterm-libtermexec-1_0"))
    implementation(project(":libs:jackpal-androidterm-emulatorview-1_0_42"))
    implementation(project(":libs:jackpal-androidterm-1_0_70"))

    // Legacy dx, API 24 / 25 only: D8 (libs.r8, used on API 26+) needs java.nio.file, which those API levels lack
    // without the nio desugar variant; see docs/dev/evidence/apk-size-p5-dexer-20260918.md (size roadmap Q10).
    // zh-CN: 仅供 API 24 / 25 的旧版 dx: D8 (libs.r8, API 26+ 使用) 需要 java.nio.file, 没有 nio 脱糖变体时这两个 API
    // 级别不具备; 见 docs/dev/evidence/apk-size-p5-dexer-20260918.md (体积 roadmap Q10).
    implementation(files("$rootDir/libs/com-legacy-android-dx-1_7_0.jar"))

    // OpenCV Java API. The native runtime is supplied by the official OpenCV plugin.
    implementation(project(":libs:org-opencv-java-4_8_0"))

    // Android Job
    implementation(project(":libs:android-job-simplified-1_4_3"))

    // AndroidX Work Runtime
    implementation(libs.work.runtime)

    // Prism4j
    implementation(files("$rootDir/libs/prism4j-2_0_0.jar"))
    implementation(files("$rootDir/libs/prism4j-bundler-2_0_0.jar"))
    implementation(project(":libs:markwon-core-4_6_2"))
    implementation(project(":libs:markwon-syntax-highlight-4_6_2"))

    // Rhino
    // Compile against the full jar (IDE resolution, test compilation) but package and run the derived jar
    // without the JVM-only console, optimizer, and JFR components (see slimRhinoJar above).
    // zh-CN: 编译使用完整 jar (IDE 解析, 测试编译), 打包与运行使用去掉 JVM 专用组件的派生 jar (见上方 slimRhinoJar).
    compileOnly(files(rhinoJar))
    runtimeOnly(files(slimRhinoJar.map { it.archiveFile }))

    // Tasker Plugin
    implementation(project(":libs:android-spackle-9_0_0"))
    implementation(project(":libs:android-assertion-9_0_0"))
    implementation(project(":libs:android-plugin-client-sdk-for-locale-9_0_0"))

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Toaster
    implementation(libs.toaster)
    implementation(libs.easywindow)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.rxjava2)
    ksp(libs.room.compiler)

    // MQTT
    implementation(libs.paho.client.mqttv3)
    implementation(libs.paho.android.service)

    // Jsoup
    implementation(libs.jsoup)

    // Universal Charset Detector
    implementation(libs.juniversalchardet)

    // Material Date Time Picker
    implementation(project(":modules:material-date-time-picker"))

    // R8
    implementation(libs.r8)

    // Restricted Unsafe atomics used by dynamically loaded JVM libraries on Android 9+.
    implementation(libs.hiddenapibypass)

    // APK Signature Verification (apksig + keystore reading; the signer, CertCreator and SpongyCastle stay in
    // modules/apk-signer for the APK builder plugin)
    // zh-CN: APK 签名校验 (apksig + 密钥库读取; 签名器, CertCreator 与 SpongyCastle 留在 modules/apk-signer 供 APK 构建插件使用)
    implementation(project(":modules:apk-verifier"))

    // Plugin API: host capability broker (shared)
    implementation(project(":plugin-api:host-capability-api"))

    // Plugin API: 3-Stove Agent
    implementation(project(":plugin-api:three-stove-agent-api"))

    // Plugin API: AI Common API
    implementation(project(":plugin-api:ai-common-api"))

    // Plugin API: AI Provider API
    implementation(project(":plugin-api:ai-provider-api"))

    // Plugin API: Common API
    implementation(project(":plugin-api:common-plugin-api"))

    // Plugin API: DEX Compiler API
    implementation(project(":plugin-api:dex-compiler-api"))

    // Independent frozen R8 Compiler API
    implementation(project(":plugin-api:protocol-wire-api"))
    implementation(project(":libs:r8-compiler-api-0_2_0"))

    // Plugin API: JVM Source API
    implementation(project(":plugin-api:jvm-source-api"))

    // Plugin API: Editor API
    implementation(project(":plugin-api:editor-api"))

    // Plugin API: Explorer Action API
    implementation(project(":plugin-api:explorer-action-api"))

    // Plugin API: Lua Runtime API
    implementation(project(":plugin-api:lua-runtime-api"))

    // Plugin API: Bun Runtime API
    implementation(project(":plugin-api:bun-runtime-api"))

    // Plugin API: QuickJS Runtime API
    implementation(project(":plugin-api:quickjs-runtime-api"))

    // Plugin API: AutoJs4 Runtime API
    implementation(project(":plugin-api:autojs4-runtime-api"))

    // Plugin API: Node.js API
    implementation(project(":plugin-api:nodejs-api"))

    // Plugin API: Python Runtime API
    implementation(project(":plugin-api:python-runtime-api"))

    // Plugin API: TypeScript Compiler API
    implementation(project(":plugin-api:typescript-api"))

    // Plugin API: APK Builder Template API
    implementation(project(":plugin-api:apk-builder-template"))

    // Plugin API: OpenCC API
    implementation(project(":plugin-api:opencc-api"))

    // Plugin API: Pinyin API
    implementation(project(":plugin-api:pinyin-api"))

    // Plugin API: Pinyin4j API
    implementation(project(":plugin-api:pinyin4j-api"))

    // Plugin API: MediaInfo API
    implementation(project(":plugin-api:mediainfo-api"))
    implementation(project(":plugin-api:imagequant-api"))
    implementation(project(":plugin-api:offline-docs-api"))
    implementation(project(":plugin-api:opencv-api"))

    // Plugin API: ML Kit Barcode API
    implementation(project(":plugin-api:mlkit-barcode-api"))

    // Plugin API: Paddle OCR API
    implementation(project(":plugin-api:paddle-ocr-api"))

    // Plugin API: Paddle OCR Engine
    implementation(project(":plugin-api:paddle-ocr-engine"))

    // Plugin API: Rapid OCR Engine
    implementation(project(":plugin-api:rapid-ocr-engine"))

    // Plugin API: YOLO API
    implementation(project(":plugin-api:yolo-api"))

    // Plugin API: Screen Color Picker API
    implementation(project(":plugin-api:screen-color-picker-api"))

    // Plugin API: MCP Server API
    implementation(project(":plugin-api:mcp-server-api"))

    // Plugin API: Mail API
    implementation(project(":plugin-api:mail-api"))

    // Shared Android package archive parser (host app + 3-Setup Installer plugin).
    // zh-CN: 宿主与 3-Setup Installer 插件共用的安装包解析器.
    implementation(project(":plugin-api:package-archive-parser"))

    // Plugin API: Installer API (3-Setup Installer)
    implementation(project(":plugin-api:installer-api"))

    // Plugin API: EPUB API
    implementation(project(":plugin-api:epub-api"))

    // Plugin API: Terminal API (3-Shell Terminal)
    implementation(project(":plugin-api:terminal-api"))

    // Plugin API: ui-mode layout preview for the 3-Block Compose designer (roadmap P10.2)
    // zh-CN: 供 3-Block Compose 设计器使用的 ui 模式布局预览契约 (路线图 P10.2)
    implementation(project(":plugin-api:ui-preview-api"))

    // Plugin API: Batch Rename (files.batchRename and the file manager rename actions)
    // zh-CN: 批量重命名插件契约 (files.batchRename 与文件管理器重命名动作)
    implementation(project(":plugin-api:batch-rename-api"))
    implementation(project(":plugin-api:gallery-api"))

    // Plugin API: self-described plugin tools (MCP Server roadmap P12, docs/dev/plugin-tool-protocol-v1.md)
    // zh-CN: 插件自描述工具契约 (MCP Server 路线图 P12)
    implementation(project(":plugin-api:plugin-tool-api"))

    // Plugin API: speech (3-Macaw Speech roadmap D21, docs/dev/speech-plugin-protocol-v1.md)
    // zh-CN: 语音插件契约 (3-Macaw Speech 路线图 D21)
    implementation(project(":plugin-api:speech-api"))
}

dependencies /* MIME */ {
    // @Hint by SuperMonster003 on Oct 5, 2023.
    //  ! Only for Android API 26 (8.0) [O] and above.
    //  ! zh-CN: 仅适用于安卓 API 级别 26 (8.0) [O] 及以上
    // Apache Tika Core
    // implementation("org.apache.tika:tika-core:2.9.2")

    // MIME Util
    // implementation("eu.medsea.mimeutil:mime-util:2.1.3")
    implementation(files("$rootDir/libs/mime-util-2_1_3.jar"))
}

dependencies /* Test */ {
    testImplementation(libs.junit)
    // BKS keystores in JVM tests need a provider named "BC" (Android ships one, the desktop JDK does not).
    // zh-CN: JVM 测试中的 BKS 密钥库需要名为 "BC" 的 provider (Android 自带, 桌面 JDK 没有).
    testImplementation(libs.bcprov.jdk15on)
    // A real org.json for JVM tests (android.jar only has stubs that throw "Stub!"); the APK keeps the Android one (docs/settings/ROADMAP.md Q14).
    // zh-CN: JVM 测试用的真实 org.json (android.jar 中只有抛出 "Stub!" 的桩); APK 仍用 Android 自带的实现 (docs/settings/ROADMAP.md Q14).
    testImplementation(libs.org.json)
    androidTestImplementation(libs.bcpkix.jdk15on)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    // Memory gate (docs/memory/ROADMAP.md T4): shortest strong reference path from a heap dump, same version as LeakCanary.
    // zh-CN: 内存门禁 (docs/memory/ROADMAP.md T4): 从堆转储中找最短强引用链, 与 LeakCanary 同版本.
    androidTestImplementation(libs.leakcanary.shark.android)

    // Rhino is compileOnly for the main source set; tests need it on their compile classpath too.
    // zh-CN: Rhino 在主源码集是 compileOnly, 测试的编译类路径也需要它.
    testCompileOnly(files(rhinoJar))
    androidTestCompileOnly(files(rhinoJar))
}

dependencies /* Annotations */ {
    implementation(libs.annotation)

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
    // Async Layout Inflater
    implementation(libs.asynclayoutinflater)

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

dependencies /* Desugar */ {
    // The update checker used to depend on github-api (org.kohsuke) together with Jackson Databind,
    // Commons IO and Commons Lang3; it now talks to the GitHub REST API through OkHttp + Gson
    // (network/github/GitHubReleaseClient), see docs/dev/apk-size-reduction-roadmap.md P2.
    // zh-CN: 更新检查曾依赖 github-api (org.kohsuke) 及 Jackson Databind, Commons IO, Commons Lang3;
    // 现在经 OkHttp + Gson 直接访问 GitHub REST API (network/github/GitHubReleaseClient), 见 docs/dev/apk-size-reduction-roadmap.md P2.

    // Desugar (java.time and other Java 8+ library APIs on API 24 / 25)
    // zh-CN: Desugar (API 24 / 25 上的 java.time 等 Java 8+ 类库 API)
    coreLibraryDesugaring(libs.desugar)
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

    // Script module layer (docs/augment/ROADMAP.md P1.1): the annotations are only needed at compile time; the processor
    // generates the module bindings for main and for the unit test fixtures.
    // zh-CN: 脚本模块层 (ROADMAP P1.1): 注解只在编译期需要; 处理器为主源码与单元测试夹具生成模块绑定.
    compileOnly(libs.ksp.script.modules.annotations)
    testCompileOnly(libs.ksp.script.modules.annotations)
    ksp(libs.ksp.script.modules.processor)
    kspTest(libs.ksp.script.modules.processor)
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
    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = applicationId

        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget

        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true

        buildConfigField("String", "VERSION_DATE", "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")
        buildConfigField("String", "VSCODE_EXT_REQUIRED_VERSION", "\"${versions.vscodeExtRequiredVersion}\"")
        val composeSharedRecords = rootProject.file("locks/host-shared-deps.lock").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }.sortedBy { it.substringBefore('=') }
        val composeSharedFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(composeSharedRecords.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        buildConfigField("String", "COMPOSE_UI_SHARED_FINGERPRINT", "\"$composeSharedFingerprint\"")
        buildConfigField("boolean", "APK_BUILDER_BUILT_IN_TEMPLATE_INCLUDED", apkBuilderIncludeBuiltInTemplate.get().toString())
        buildConfigField("boolean", "JVM_SOURCE_EXPERIMENTAL_ENABLED", jvmSourceExperimentalEnabled.get().toString())
        buildConfigField("boolean", buildConfigFieldNameIsInrt, "false")

        javaCompileOptions {
            annotationProcessorOptions {
                mapOf(
                    "resourcePackageName" to (this@defaultConfig.applicationId ?: globalApplicationId),
                    "androidManifestFile" to ("$projectDir/src/main/AndroidManifest.xml")
                ).let { arguments(it) }
            }
        }
    }

    // Instrument the signed Release host when requested; normal developer tests stay on Debug.
    if (yoloR6ReleaseAndroidTest.get() || nodeInspectorReleaseAndroidTest.get()) {
        testBuildType = buildTypeRelease
    }
    if (nodeInspectorReleaseAndroidTest.get()) {
        // Other device suites reference components that exist only in src/debug.
        // This explicit Release acceptance target compiles the Inspector suite and its harness.
        val inspectorTestDirectory = layout.buildDirectory.dir("generated/sources/nodeInspectorAndroidTest")
        val prepareInspectorTests = tasks.register<Sync>("prepareNodeInspectorReleaseAndroidTest") {
            from("src/androidTest/java") {
                include("**/NodeInspectorInstrumentationTest.kt", "**/NodePluginTestHarness.kt",
                    "**/NodeStartupMeasurementInstrumentationTest.kt")
            }
            into(inspectorTestDirectory)
        }
        sourceSets.getByName("androidTest") {
            java.setSrcDirs(listOf(inspectorTestDirectory))
            kotlin.setSrcDirs(listOf(inspectorTestDirectory))
            manifest.srcFile("src/androidTestInspector/AndroidManifest.xml")
        }
        tasks.matching { it.name.endsWith("ReleaseAndroidTestBuild") }.configureEach {
            dependsOn(prepareInspectorTests)
        }
        // The test APK shares a class loader with the host. A test-only, shrunken j$ copy
        // otherwise shadows DateTimeFormatter methods used by the host's Rhino engine.
        afterEvaluate {
            tasks.named<com.android.build.gradle.internal.tasks.L8DexDesugarLibTask>("l8DexDesugarLibAppReleaseAndroidTest").configure {
                keepRulesConfigurations.add("-keep class j$.** { *; }")
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
            buildConfigField("boolean", buildConfigFieldNameIsInrt, "false")
            val authorities = "org.autojs.autojs6.fileprovider"
            buildConfigField("String", buildConfigFieldNameFileProviderAuthority, "\"$authorities\"")
            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameApp,
                    "appName" to "@string/app_name",
                    "intentCategory" to "android.intent.category.LAUNCHER",
                    "intentCategoryInrt" to "android.intent.category.DEFAULT",
                    "authorities" to authorities,
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
            buildConfigField("boolean", buildConfigFieldNameIsInrt, "true")
            val authorities = "org.autojs.autojs6.$flavorNameInrt.fileprovider"
            buildConfigField("String", buildConfigFieldNameFileProviderAuthority, "\"$authorities\"")

            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameInrt,
                    "appName" to "AutoJs6.$flavorNameInrt",
                    "intentCategory" to "android.intent.category.DEFAULT",
                    "intentCategoryInrt" to "android.intent.category.LAUNCHER",
                    "authorities" to authorities,
                    "icon" to "@mipmap/ic_launcher",
                )
            )

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
        }
        getByName("release") {
            java.directories.add("src/release/java")
        }
        getByName("debug") {
            java.directories.add("src/debug/java")
        }
        getByName(flavorNameApp) {
            assets.directories.add("src/main/assets-$flavorNameApp")
            manifest.srcFile(if (versions.sdkVersionTarget >= 37) "src/appTarget37/AndroidManifest.xml" else "src/app/AndroidManifest.xml")
        }
        getByName(flavorNameInrt) {
            assets.directories.add("src/main/assets-$flavorNameInrt")
            if (versions.sdkVersionTargetInrt >= 37) {
                manifest.srcFile("src/target37/AndroidManifest.xml")
            }
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

    androidResources {
        localeFilters.addAll(supportedResourceLocales)
    }

    splits {
        abi {
            isEnable = !isInrtAssembleTaskRequested
            reset()
            include("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
        val niceSigningConfig = if (signs.isValid) signingConfigs.getByName(buildTypeRelease) else null

        release {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }

        debug {
            isMinifyEnabled = getByName(buildTypeRelease).isMinifyEnabled
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    packaging {
        resources.pickFirsts.addAll(resourcePickFirstPatterns)
        resources.excludes.addAll(resourceExcludePatterns)

        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = sdkLintVerification
    }

}

tasks {

}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val mergeAssetsTaskName = "merge${variantName.asGradleTaskNamePart()}Assets"
        val isInrtVariant = variantName.startsWith(flavorNameInrt, ignoreCase = true)

        val variantApkDirectory = variant.artifacts.get(SingleArtifact.APK)
        // The shared plugin registers tasks in afterEvaluate; apply its defaults first.
        // IDE APKs can live under intermediates/apk; apkDirectory is @Internal and needs its producer wired explicitly.
        // zh-CN: 在共享插件延迟注册任务后再覆盖默认目录; IDE APK 目录需显式关联生成任务.
        afterEvaluate {
            tasks.named<VerifyNativePageAlignment>("verify${variantName.asGradleTaskNamePart()}NativePageAlignment") {
                apkDirectory.set(variantApkDirectory)
                dependsOn(variantApkDirectory)
            }
        }

        tasks.matching { it.name == mergeAssetsTaskName }.configureEach {
            val includeBuiltInTemplate = apkBuilderIncludeBuiltInTemplate.get()
            inputs.property(apkBuilderIncludeBuiltInTemplateProperty, includeBuiltInTemplate)

            doLast {
                val includes = if (isInrtVariant) {
                    inrtMergedAssetPruneIncludes
                } else {
                    appMergedAssetPruneIncludes + if (includeBuiltInTemplate) {
                        emptyList()
                    } else {
                        listOf(apkBuilderBuiltInTemplateAsset)
                    }
                }

                listOfNotNull(
                    reflectedOutputDir(),
                    layout.buildDirectory.dir("intermediates/assets/$variantName/$mergeAssetsTaskName").get().asFile,
                )
                    .map { it.absoluteFile.normalize() }
                    .distinctBy { it.path }
                    .filter { it.isDirectory }
                    .forEach { outputDir ->
                        if (includes.isNotEmpty()) {
                            delete(
                                fileTree(outputDir) {
                                    include(includes)
                                }
                            )
                        }
                        if (!isInrtVariant && !includeBuiltInTemplate) {
                            delete(outputDir.resolve(apkBuilderBuiltInTemplateAsset))
                        }
                    }
            }
        }

        variant.outputs.forEach { output ->
            val architecture = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"

            output.reflectedOutputFileNameProperty()?.set(
                variant.applicationId.zip(output.versionName) { appId, versionName ->
                    val autojs = appId.substringAfterLast('.')
                    val version = versionName.replace("\\s".toRegex(), "-")
                    "$autojs-v$version-$architecture.$apkFileExtension".lowercase()
                }
            )
        }
    }
}

tasks.register("generateRuntimeKit") {
    group = "build"
    description = "Builds the AutoJs6 Runtime Kit consumed by the standalone APK Builder template plugin."
    dependsOn("assembleInrtRelease", "verifyInrtReleaseNativePageAlignment")

    val runtimeKitVersionName = versions.appVersionName
    val runtimeKitVersionCode = versions.appVersionCode
    val runtimeKitCompatibilityMinHostVersionCode = providers
        .gradleProperty("autojs.runtimeKit.compatibility.minHostVersionCode")
        .map { value ->
            value.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid Runtime Kit minimum host versionCode: $value")
        }
        .orElse(runtimeKitVersionCode)
    val runtimeKitCompatibilityMaxHostVersionCode = providers
        .gradleProperty("autojs.runtimeKit.compatibility.maxHostVersionCode")
        .map { value ->
            value.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid Runtime Kit maximum host versionCode: $value")
        }
        .orElse(runtimeKitVersionCode)
    val runtimeKitAllowPatchVersionMismatch = providers
        .gradleProperty("autojs.runtimeKit.compatibility.allowPatchVersionMismatch")
        .map { value ->
            when (value.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> throw IllegalArgumentException(
                    "Invalid Runtime Kit allowPatchVersionMismatch value: $value"
                )
            }
        }
        .orElse(false)
    val normalizedRuntimeKitVersion = normalizeRuntimeKitVersion(runtimeKitVersionName)
    val runtimeKitGitSha = providers.gradleProperty("autojs.git.sha")
        .orElse(providers.environmentVariable("GITHUB_SHA"))
        .orElse("local")
    val runtimeKitSourceRepository = providers.environmentVariable("GITHUB_REPOSITORY")
        .orElse("local")
    val runtimeKitGithubRunId = providers.environmentVariable("GITHUB_RUN_ID")
        .orElse("")
    val runtimeKitVariants = linkedMapOf(
        "universal" to listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
        "arm64-v8a" to listOf("arm64-v8a"),
        "armeabi-v7a" to listOf("armeabi-v7a"),
        "x86_64" to listOf("x86_64"),
        "x86" to listOf("x86"),
    )
    val runtimeKitTemplateApks = runtimeKitVariants.keys.associateWith { variant ->
        layout.buildDirectory.file(
            "outputs/apk/inrt/release/inrt-v$normalizedRuntimeKitVersion-$variant.apk"
        )
    }
    val runtimeApiSourceRoots = listOf(
        rootDir.resolve("app/src/main/assets/declarations"),
        rootDir.resolve("app/src/main/assets/modules"),
        rootDir.resolve("app/src/main/java/org/autojs/autojs/runtime/api"),
        rootDir.resolve("app/src/main/java/org/autojs/autojs/runtime/api/augment"),
        rootDir.resolve("plugin-api/apk-builder-template/src/main"),
        rootDir.resolve("plugin-api/common-plugin-api/src/main"),
    )
    val runtimeKitOutputDir = layout.buildDirectory.dir("runtime-kit")
    outputs.dir(runtimeKitOutputDir)
    inputs.property("runtimeKitVersionName", runtimeKitVersionName)
    inputs.property("runtimeKitVersionCode", runtimeKitVersionCode)
    inputs.property("runtimeKitCompatibilityMinHostVersionCode", runtimeKitCompatibilityMinHostVersionCode)
    inputs.property("runtimeKitCompatibilityMaxHostVersionCode", runtimeKitCompatibilityMaxHostVersionCode)
    inputs.property("runtimeKitAllowPatchVersionMismatch", runtimeKitAllowPatchVersionMismatch)
    inputs.property("runtimeKitGitSha", runtimeKitGitSha)
    inputs.property("runtimeKitSourceRepository", runtimeKitSourceRepository)
    inputs.property("runtimeKitGithubRunId", runtimeKitGithubRunId)
    inputs.files(runtimeKitTemplateApks.values)
    inputs.files(runtimeApiSourceRoots)
    inputs.file(projectDir.resolve("src/main/assets/default_key_store.bks"))

    doLast {
        val versionName = runtimeKitVersionName
        val versionCode = runtimeKitVersionCode
        val minHostVersionCode = runtimeKitCompatibilityMinHostVersionCode.get()
        val maxHostVersionCode = runtimeKitCompatibilityMaxHostVersionCode.get()
        val allowPatchVersionMismatch = runtimeKitAllowPatchVersionMismatch.get()
        require(minHostVersionCode > 0) {
            "Runtime Kit minimum host versionCode must be positive: $minHostVersionCode"
        }
        require(maxHostVersionCode >= minHostVersionCode) {
            "Runtime Kit host compatibility range is invalid: $minHostVersionCode..$maxHostVersionCode"
        }
        require(versionCode in minHostVersionCode..maxHostVersionCode) {
            "Runtime Kit built-for host $versionCode is outside its compatibility range " +
                    "$minHostVersionCode..$maxHostVersionCode"
        }
        require(minHostVersionCode == maxHostVersionCode || allowPatchVersionMismatch) {
            "A widened Runtime Kit host range requires allowPatchVersionMismatch=true: " +
                    "$minHostVersionCode..$maxHostVersionCode"
        }
        val normalizedVersion = normalizedRuntimeKitVersion
        val gitSha = runtimeKitGitSha.get()

        val defaultKeyStore = projectDir.resolve("src/main/assets/default_key_store.bks")
        require(defaultKeyStore.isFile) {
            "Missing default key store: $defaultKeyStore"
        }

        val outputRoot = runtimeKitOutputDir.get().asFile
        project.delete(outputRoot)
        outputRoot.mkdirs()

        val runtimeApiEntries = collectSourceDigestEntries(
            rootDir = rootDir,
            relativePaths = listOf(
                "app/src/main/assets/declarations",
                "app/src/main/assets/modules",
                "app/src/main/java/org/autojs/autojs/runtime/api",
                "app/src/main/java/org/autojs/autojs/runtime/api/augment",
                "plugin-api/apk-builder-template/src/main",
                "plugin-api/common-plugin-api/src/main",
            ),
        )
        val runtimeApiHash = combinedDigest(runtimeApiEntries)

        runtimeKitVariants.forEach { (variantSlug, supportedAbis) ->
            val templateApk = runtimeKitTemplateApks.getValue(variantSlug).get().asFile
            require(templateApk.isFile) {
                "Missing INRT $variantSlug APK: $templateApk"
            }

            val variantName = "inrt-$variantSlug"
            val supportedAbisJson = supportedAbis.joinToString(prefix = "[", postfix = "]") {
                it.toJsonLiteral()
            }
            val kitBaseName = "autojs6-runtime-kit-v$normalizedVersion+$versionCode-$variantSlug"
            val kitDir = outputRoot.resolve(kitBaseName)
            kitDir.mkdirs()

            val templateOut = kitDir.resolve("template.apk")
            templateApk.copyTo(templateOut, overwrite = true)
            val templateSha256 = templateOut.sha256String()
            kitDir.resolve("template.apk.sha256").writeText("$templateSha256\n", Charsets.UTF_8)

            val defaultKeyStoreOut = kitDir.resolve("default_key_store.bks")
            defaultKeyStore.copyTo(defaultKeyStoreOut, overwrite = true)
            val defaultKeyStoreSha256 = defaultKeyStoreOut.sha256String()
            kitDir.resolve("default_key_store.bks.sha256").writeText("$defaultKeyStoreSha256\n", Charsets.UTF_8)

            writePublicApiText(kitDir.resolve("public-api.txt"), runtimeApiEntries)

            val scriptEngineEntries = collectZipDigestEntries(templateOut) { name ->
                name.matches(Regex("classes\\d*\\.dex")) ||
                        name == "assets/init.js" ||
                        name.startsWith("assets/modules/") ||
                        name.startsWith("assets/declarations/")
            }
            val scriptEngineHash = combinedDigest(scriptEngineEntries)

            val resourceContractEntries = collectZipDigestEntries(templateOut) { name ->
                name == "AndroidManifest.xml" ||
                        name == "resources.arsc" ||
                        name == "assets/init.js" ||
                        name.startsWith("res/")
            }
            val resourcesContractHash = combinedDigest(resourceContractEntries)

            val assetEntries = collectZipDigestEntries(templateOut) { name ->
                name.startsWith("assets/")
            }
            writeDigestManifestJson(kitDir.resolve("assets-manifest.json"), "template-assets", assetEntries)

            val nativeLibEntries = collectZipDigestEntries(templateOut) { name ->
                name.startsWith("lib/") && name.endsWith(".so")
            }
            val nativeLibManifestHash = combinedDigest(nativeLibEntries)
            writeDigestManifestJson(kitDir.resolve("native-libs.json"), "template-native-libs", nativeLibEntries)

            val apkBuilderProtocolVersion = 2
            val remoteBuildProtocolVersion = 3
            val runtimeKitId = "autojs6-runtime-v$versionName+$versionCode-$gitSha-$variantSlug"

            kitDir.resolve("build-contract.json").writeText(
                buildString {
                    appendLine("{")
                    appendLine("  \"schemaVersion\": 1,")
                    appendLine("  \"apkBuilderProtocolVersion\": $apkBuilderProtocolVersion,")
                    appendLine("  \"remoteBuildProtocolVersion\": $remoteBuildProtocolVersion,")
                    appendLine("  \"runtimeApiLevel\": $versionCode,")
                    appendLine("  \"runtimeApiHash\": ${runtimeApiHash.toJsonLiteral()},")
                    appendLine("  \"scriptEngineHash\": ${scriptEngineHash.toJsonLiteral()},")
                    appendLine("  \"resourcesContractHash\": ${resourcesContractHash.toJsonLiteral()},")
                    appendLine("  \"nativeLibPageAlignment\": 16384,")
                    appendLine("  \"nativeLibManifestHash\": ${nativeLibManifestHash.toJsonLiteral()}")
                    appendLine("}")
                },
                Charsets.UTF_8,
            )

            kitDir.resolve("runtime-kit.json").writeText(
                buildString {
                    appendLine("{")
                    appendLine("  \"schemaVersion\": 1,")
                    appendLine("  \"runtimeKitId\": ${runtimeKitId.toJsonLiteral()},")
                    appendLine("  \"host\": {")
                    appendLine("    \"packageName\": ${globalApplicationId.toJsonLiteral()},")
                    appendLine("    \"versionName\": ${versionName.toJsonLiteral()},")
                    appendLine("    \"versionCode\": $versionCode,")
                    appendLine("    \"gitSha\": ${gitSha.toJsonLiteral()}")
                    appendLine("  },")
                    appendLine("  \"template\": {")
                    appendLine("    \"fileName\": \"template.apk\",")
                    appendLine("    \"packageName\": ${("$globalApplicationId.$flavorNameInrt").toJsonLiteral()},")
                    appendLine("    \"sha256\": ${templateSha256.toJsonLiteral()},")
                    appendLine("    \"sizeBytes\": ${templateOut.length()},")
                    appendLine("    \"variant\": ${variantName.toJsonLiteral()},")
                    appendLine("    \"supportedAbis\": $supportedAbisJson")
                    appendLine("  },")
                    appendLine("  \"defaultKeyStore\": {")
                    appendLine("    \"fileName\": \"default_key_store.bks\",")
                    appendLine("    \"sha256\": ${defaultKeyStoreSha256.toJsonLiteral()},")
                    appendLine("    \"sizeBytes\": ${defaultKeyStoreOut.length()}")
                    appendLine("  },")
                    appendLine("  \"contract\": {")
                    appendLine("    \"apkBuilderProtocolVersion\": $apkBuilderProtocolVersion,")
                    appendLine("    \"remoteBuildProtocolVersion\": $remoteBuildProtocolVersion,")
                    appendLine("    \"runtimeApiLevel\": $versionCode,")
                    appendLine("    \"runtimeApiHash\": ${runtimeApiHash.toJsonLiteral()},")
                    appendLine("    \"scriptEngineHash\": ${scriptEngineHash.toJsonLiteral()},")
                    appendLine("    \"resourcesContractHash\": ${resourcesContractHash.toJsonLiteral()},")
                    appendLine("    \"nativeLibPageAlignment\": 16384,")
                    appendLine("    \"nativeLibManifestHash\": ${nativeLibManifestHash.toJsonLiteral()}")
                    appendLine("  },")
                    appendLine("  \"compatibility\": {")
                    appendLine("    \"minHostVersionCode\": $minHostVersionCode,")
                    appendLine("    \"maxHostVersionCode\": $maxHostVersionCode,")
                    appendLine("    \"allowPatchVersionMismatch\": $allowPatchVersionMismatch")
                    appendLine("  }")
                    appendLine("}")
                },
                Charsets.UTF_8,
            )

            kitDir.resolve("provenance.json").writeText(
                buildString {
                    appendLine("{")
                    appendLine("  \"schemaVersion\": 1,")
                    appendLine("  \"sourceRepository\": ${runtimeKitSourceRepository.get().toJsonLiteral()},")
                    appendLine("  \"gitSha\": ${gitSha.toJsonLiteral()},")
                    appendLine("  \"githubRunId\": ${runtimeKitGithubRunId.get().toJsonLiteral()},")
                    appendLine("  \"templateApkSource\": ${templateApk.relativeTo(projectDir).invariantSeparatorsPath.toJsonLiteral()}")
                    appendLine("}")
                },
                Charsets.UTF_8,
            )

            val zipFile = outputRoot.resolve("$kitBaseName.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                kitDir.walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.relativeTo(kitDir).invariantSeparatorsPath }
                    .forEach { file ->
                        val entryName = file.relativeTo(kitDir).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
            }

            println("Runtime Kit directory: $kitDir")
            println("Runtime Kit archive: $zipFile")
        }
    }
}

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"

        // @Hint by SuperMonster003 on May 18, 2022.
        //  ! Comment or remove this option if you are tired of plenty of warnings. :)
        //  ! zh-CN: 注释或移除此选项可避免过多警告消息造成的困扰. [笑脸符号]
        // options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    matching {
        it.name in setOf(
            "lintVitalAnalyzeAppRelease",
            "lintVitalAnalyzeInrtRelease",
        )
    }.configureEach {
        enabled = sdkLintVerification
    }

    matching {
        it.name in setOf(
            "lintVitalAnalyzeAppRelease",
            "lintVitalReportAppRelease",
            "lintVitalAppRelease",
        )
    }.configureEach {
        onlyIf { sdkLintVerification || !apkBuilderTemplateDistributionRequested.get() }
    }

    val openCvSeparationTasks = listOf(
        "AppDebug" to "app/debug",
        "AppRelease" to "app/release",
    ).map { (variantTitle, outputPath) ->
        register("verifyOpenCvPluginSeparation$variantTitle") {
            group = "verification"
            description = "Verifies that $variantTitle host APKs use the external OpenCV runtime"
            dependsOn("assemble$variantTitle")
            inputs.file(openCvJavaApiAar)

            doLast {
                verifyOpenCvPluginSeparation(
                    apkDirectory = layout.buildDirectory.dir("outputs/apk/$outputPath").get().asFile,
                    javaApiAar = openCvJavaApiAar,
                    nativeLibraryFileName = openCvNativeLibraryFileName,
                )
            }
        }
    }

    register("verifyOpenCvPluginSeparation") {
        group = "verification"
        description = "Verifies that debug and release host APKs do not package OpenCV JNI"
        dependsOn(openCvSeparationTasks)
    }

    val imageQuantSeparationTasks = listOf(
        "AppDebug" to "app/debug",
        "AppRelease" to "app/release",
    ).map { (variantTitle, outputPath) ->
        register("verifyImageQuantPluginSeparation$variantTitle") {
            group = "verification"
            description = "Verifies that $variantTitle host APKs use the external Image Quantization runtime"
            dependsOn("assemble$variantTitle")

            doLast {
                verifyImageQuantPluginSeparation(
                    apkDirectory = layout.buildDirectory.dir("outputs/apk/$outputPath").get().asFile,
                    nativeLibraryFileName = imageQuantNativeLibraryFileName,
                )
            }
        }
    }

    register("verifyImageQuantPluginSeparation") {
        group = "verification"
        description = "Verifies that debug and release host APKs do not package Image Quantization JNI or libpng"
        dependsOn(imageQuantSeparationTasks)
    }

    val offlineDocsSeparationTasks = listOf(
        "AppDebug" to "app/debug",
        "AppRelease" to "app/release",
    ).map { (variantTitle, outputPath) ->
        register("verifyOfflineDocsPluginSeparation$variantTitle") {
            group = "verification"
            description = "Verifies that $variantTitle host APKs use the external Offline Docs plugin"
            dependsOn("assemble$variantTitle")

            doLast {
                verifyOfflineDocsPluginSeparation(
                    apkDirectory = layout.buildDirectory.dir("outputs/apk/$outputPath").get().asFile,
                )
            }
        }
    }

    register("verifyOfflineDocsPluginSeparation") {
        group = "verification"
        description = "Verifies that debug and release host APKs do not package Offline Docs assets"
        dependsOn(offlineDocsSeparationTasks)
    }

    register<Copy>("appendDigestToReleasedFiles") {
        description = "Appends CRC32 digest to release APK filenames for integrity verification"
        releaseFlavorNames.forEach { flavorName ->
            val src = "$flavorName/$buildTypeRelease"
            val dst = "${src}s"

            if (!file(src).isDirectory) {
                return@forEach
            }

            from(src); into(dst); include("*.$apkFileExtension")

            rename { name ->
                utils.digestCRC32(file("${src}/$name")).let { digest ->
                    name.replace(Regex("^(.+?)(\\.$apkFileExtension)$"), "$1-$digest$2")
                }
            }

            doLast { println("Destination: ${file(dst)}") }
        }
    }
}

versions.handleIfNeeded(project, flavorNameApp, buildTypeNames)
