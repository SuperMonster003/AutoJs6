@file:Suppress("SpellCheckingInspection", "AvoidApplyPluginMethod")

import com.android.build.api.variant.FilterConfiguration
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
    id("com.google.devtools.ksp")
    id("idea")
}

val globalApplicationId = "org.autojs.autojs6"

val flavorDimension = "channel"
val flavorNameApp = "app"
val flavorNameInrt = "inrt"
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val buildActionAssemble = "assemble"
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
val leakCanaryEnabled = providers.gradleProperty("autojs.leakcanary.enabled").orElse("false").get()
    .trim()
    .let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }

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
    "junit/**/*",
    "LICENSE-junit.txt",
    "spec.txt",
    "EmojiReference.txt",
)

val appMergedAssetPruneIncludes = listOf(
    "declarations/**/*",
    "default_key_store.bks",
    "docs/**/*",
    "sample/declarations/**/*",
    "modules/obsolete/**/*",
)

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

extra.apply {
    set("nodeJsRuntimeConfig.globalApplicationId", globalApplicationId)
    set("nodeJsRuntimeConfig.flavorNameApp", flavorNameApp)
    set("nodeJsRuntimeConfig.flavorNameInrt", flavorNameInrt)
    set("nodeJsRuntimeConfig.buildActionAssemble", buildActionAssemble)
}

apply(from = "node-runtime-config.gradle.kts")

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

if (apkBuilderIncludeBuiltInTemplate.get()) {
    utils.registerTemplateApkCopy(project)
}

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

    // Version Compare
    implementation(libs.versioncompare)

    // Terminal Emulator
    implementation(project(":libs:jackpal-androidterm-libtermexec-1_0"))
    implementation(project(":libs:jackpal-androidterm-emulatorview-1_0_42"))
    implementation(project(":libs:jackpal-androidterm-1_0_70"))

    // Dex
    implementation(files("$rootDir/libs/com-android-dx-1_14.jar"))
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

    // Plugin API: AI Common API
    implementation(project(":plugin-api:ai-common-api"))

    // Plugin API: AI Text Generation API
    implementation(project(":plugin-api:ai-text-generation-api"))

    // Plugin API: Common API
    implementation(project(":plugin-api:common-plugin-api"))

    // Plugin API: DEX Compiler API
    implementation(project(":plugin-api:dex-compiler-api"))

    // Plugin API: Editor API
    implementation(project(":plugin-api:editor-api"))

    // Plugin API: Explorer Action API
    implementation(project(":plugin-api:explorer-action-api"))

    // Plugin API: Node.js API
    implementation(project(":plugin-api:nodejs-api"))

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
    androidTestImplementation(libs.test.runner)
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

        multiDexEnabled = true

        buildConfigField("String", "VERSION_DATE", "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")
        buildConfigField("String", "VSCODE_EXT_REQUIRED_VERSION", "\"${versions.vscodeExtRequiredVersion}\"")
        buildConfigField("boolean", "APK_BUILDER_BUILT_IN_TEMPLATE_INCLUDED", apkBuilderIncludeBuiltInTemplate.get().toString())
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

    flavorDimensions.add(flavorDimension)

    productFlavors {

        create(flavorNameApp) {
            dimension = flavorDimension
            versionCode = versions.appVersionCode
            versionName = versions.appVersionName
            buildConfigField("String", "CHANNEL", "\"$flavorNameApp\"")
            buildConfigField("boolean", buildConfigFieldNameIsInrt, "false")
            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameApp,
                    "appName" to "@string/app_name",
                    "intentCategory" to "android.intent.category.LAUNCHER",
                    "intentCategoryInrt" to "android.intent.category.DEFAULT",
                    "authorities" to "org.autojs.autojs6.fileprovider",
                    "icon" to "@mipmap/ic_app_launcher_adaptive",
                    "installerEnabled" to "true",
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

            manifestPlaceholders.putAll(
                mapOf(
                    "CHANNEL" to flavorNameInrt,
                    "appName" to "AutoJs6.$flavorNameInrt",
                    "intentCategory" to "android.intent.category.DEFAULT",
                    "intentCategoryInrt" to "android.intent.category.LAUNCHER",
                    "authorities" to "org.autojs.autojs6.$flavorNameInrt.fileprovider",
                    "icon" to "@mipmap/ic_launcher",
                    "installerEnabled" to "false",
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
        }
        getByName(flavorNameInrt) {
            assets.directories.add("src/main/assets-$flavorNameInrt")
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
        abortOnError = false
    }

}

tasks {

}

apply(from = "node-android-config.gradle.kts")

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val mergeAssetsTaskName = "merge${variantName.asGradleTaskNamePart()}Assets"
        val isInrtVariant = variantName.startsWith(flavorNameInrt, ignoreCase = true)

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
    dependsOn("assembleInrtRelease")

    val runtimeKitVersionName = versions.appVersionName
    val runtimeKitVersionCode = versions.appVersionCode
    val normalizedRuntimeKitVersion = normalizeRuntimeKitVersion(runtimeKitVersionName)
    val runtimeKitGitSha = providers.gradleProperty("autojs.git.sha")
        .orElse(providers.environmentVariable("GITHUB_SHA"))
        .orElse("local")
    val runtimeKitSourceRepository = providers.environmentVariable("GITHUB_REPOSITORY")
        .orElse("local")
    val runtimeKitGithubRunId = providers.environmentVariable("GITHUB_RUN_ID")
        .orElse("")
    val runtimeKitTemplateApk = layout.buildDirectory.file(
        "outputs/apk/inrt/release/inrt-v$normalizedRuntimeKitVersion-universal.apk"
    )
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
    inputs.property("runtimeKitGitSha", runtimeKitGitSha)
    inputs.property("runtimeKitSourceRepository", runtimeKitSourceRepository)
    inputs.property("runtimeKitGithubRunId", runtimeKitGithubRunId)
    inputs.file(runtimeKitTemplateApk)
    inputs.files(runtimeApiSourceRoots)
    inputs.file(projectDir.resolve("src/main/assets/default_key_store.bks"))

    doLast {
        val versionName = runtimeKitVersionName
        val versionCode = runtimeKitVersionCode
        val normalizedVersion = normalizedRuntimeKitVersion
        val gitSha = runtimeKitGitSha.get()

        val templateApk = runtimeKitTemplateApk.get().asFile
        require(templateApk.isFile) {
            "Missing INRT universal APK: $templateApk"
        }

        val defaultKeyStore = projectDir.resolve("src/main/assets/default_key_store.bks")
        require(defaultKeyStore.isFile) {
            "Missing default key store: $defaultKeyStore"
        }

        val outputRoot = runtimeKitOutputDir.get().asFile
        val kitDir = outputRoot.resolve("autojs6-runtime-kit-v$normalizedVersion+$versionCode")
        project.delete(kitDir)
        kitDir.mkdirs()

        val templateOut = kitDir.resolve("template.apk")
        templateApk.copyTo(templateOut, overwrite = true)
        val templateSha256 = templateOut.sha256String()
        kitDir.resolve("template.apk.sha256").writeText("$templateSha256\n", Charsets.UTF_8)

        val defaultKeyStoreOut = kitDir.resolve("default_key_store.bks")
        defaultKeyStore.copyTo(defaultKeyStoreOut, overwrite = true)
        val defaultKeyStoreSha256 = defaultKeyStoreOut.sha256String()
        kitDir.resolve("default_key_store.bks.sha256").writeText("$defaultKeyStoreSha256\n", Charsets.UTF_8)

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
        val remoteBuildProtocolVersion = 2
        val runtimeKitId = "autojs6-runtime-v$versionName+$versionCode-$gitSha"

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
                appendLine("    \"sizeBytes\": ${templateOut.length()}")
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
                appendLine("    \"nativeLibManifestHash\": ${nativeLibManifestHash.toJsonLiteral()}")
                appendLine("  },")
                appendLine("  \"compatibility\": {")
                appendLine("    \"minHostVersionCode\": $versionCode,")
                appendLine("    \"maxHostVersionCode\": $versionCode,")
                appendLine("    \"allowPatchVersionMismatch\": false")
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

        val zipFile = outputRoot.resolve("autojs6-runtime-kit-v$normalizedVersion+$versionCode.zip")
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

apply(from = "node-build-tasks.gradle.kts")

apply(from = "node-gradle-scripts.gradle.kts")

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
        enabled = false
    }

    matching {
        it.name in setOf(
            "lintVitalAnalyzeAppRelease",
            "lintVitalReportAppRelease",
            "lintVitalAppRelease",
        )
    }.configureEach {
        onlyIf { !apkBuilderTemplateDistributionRequested.get() }
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
