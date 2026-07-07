@file:Suppress("SpellCheckingInspection")

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
val apkFileExtension = utils.FILE_EXTENSION_APK
val apkBuilderBuiltInTemplateAsset = "template.apk"
val apkBuilderTemplateDistributionTaskName = "assembleApkBuilderTemplateDistribution"
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
    "sample/declarations/**/*",
    "modules/obsolete/**/*",
)

val inrtMergedAssetPruneIncludes = listOf(
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
    "android-devices.db",
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

apply(from = "app-dependencies.gradle.kts")

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

    val runtimeKitOutputDir = layout.buildDirectory.dir("runtime-kit")
    outputs.dir(runtimeKitOutputDir)
    inputs.file(projectDir.resolve("src/main/assets/default_key_store.bks"))

    doLast {
        val versionName = versions.appVersionName
        val versionCode = versions.appVersionCode
        val normalizedVersion = normalizeRuntimeKitVersion(versionName)
        val gitSha = providers.gradleProperty("autojs.git.sha").orNull
            ?: System.getenv("GITHUB_SHA")
            ?: "local"

        val templateApk = layout.buildDirectory
            .file("outputs/apk/inrt/release/inrt-v$normalizedVersion-universal.apk")
            .get()
            .asFile
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

        val apkBuilderProtocolVersion = 1
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
                appendLine("    \"packageName\": ${(globalApplicationId + ".$flavorNameInrt").toJsonLiteral()},")
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
                appendLine("  \"sourceRepository\": ${(System.getenv("GITHUB_REPOSITORY") ?: "local").toJsonLiteral()},")
                appendLine("  \"gitSha\": ${gitSha.toJsonLiteral()},")
                appendLine("  \"githubRunId\": ${(System.getenv("GITHUB_RUN_ID") ?: "").toJsonLiteral()},")
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

    register<Copy>("appendDigestToReleasedFiles") {
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
