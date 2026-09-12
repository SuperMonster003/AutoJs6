import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.provider.Property

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

val buildTypeRelease = "release"
val apkFileExtension = "apk"
val enableRemoteBuildProperty = "autojs.apkBuilder.templatePlugin.enableRemoteBuild"
val runtimeKitDirProperty = "autojs.apkBuilder.templatePlugin.runtimeKitDir"
val enableRemoteBuild = providers.gradleProperty(enableRemoteBuildProperty)
    .map { value ->
        value.trim().lowercase().let { it in setOf("true", "1", "yes", "on") }
    }
    .orElse(true)

private fun Any.reflectedNoArgMethod(name: String): Any? =
    javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }?.invoke(this)

@Suppress("UNCHECKED_CAST")
private fun Any.reflectedOutputFileNameProperty(): Property<String>? =
    reflectedNoArgMethod("getOutputFileName") as? Property<String>

fun resolveSigningStoreFile(path: String): File {
    val candidate = File(path)
    if (candidate.isAbsolute) {
        return candidate
    }
    return listOf(
        file(path),
        rootProject.file(path),
        rootProject.file("app/$path"),
    ).firstOrNull { it.exists() } ?: file(path)
}

android {
    namespace = "org.autojs.plugin.apkbuilder.template.impl"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "org.autojs.plugin.apkbuilder.template"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        buildConfigField("String", "PLUGIN_ID", "\"autojs6-apk-builder-template\"")
        buildConfigField("String", "HOST_PACKAGE_NAME", "\"org.autojs.autojs6\"")
        buildConfigField("String", "HOST_VERSION_NAME", "\"${versions.appVersionName}\"")
        buildConfigField("long", "HOST_VERSION_CODE", "${versions.appVersionCode}L")
        buildConfigField("int", "PROTOCOL_VERSION", "2")
        buildConfigField("boolean", "ENABLE_REMOTE_BUILD", enableRemoteBuild.get().toString())
        buildConfigField("int", "REMOTE_BUILD_PROTOCOL_VERSION", "2")
        buildConfigField("String", "TEMPLATE_PACKAGE_NAME", "\"org.autojs.autojs6.inrt\"")
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(layout.buildDirectory.dir("generated/assets/apkBuilderTemplate").get().asFile.absolutePath)
        }
    }

    signingConfigs {
        if (signs.isValid) {
            create(buildTypeRelease) {
                storeFile = signs.properties["storeFile"]?.let { resolveSigningStoreFile(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val releaseSigningConfig = if (signs.isValid) signingConfigs.getByName(buildTypeRelease) else null

        release {
            isMinifyEnabled = false
            releaseSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    lint {
        abortOnError = false
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.reflectedOutputFileNameProperty()?.set(
                output.versionName.map { versionName ->
                    val version = versionName.replace("\\s".toRegex(), "-").lowercase()
                    "autojs6-apk-builder-template-v$version-universal.$apkFileExtension"
                }
            )
        }
    }
}

dependencies {
    implementation(project(":plugin-api:apk-builder-template"))
    implementation(project(":plugin-api:common-plugin-api"))
    implementation(project(":modules:android-axml"))
    implementation(project(":modules:android-arsc-editor"))
    implementation(project(":modules:apk-signer"))
    implementation(libs.arsclib)
    implementation(libs.prov)

    testImplementation(libs.junit)
}

fun File.sha256String(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun normalizeRuntimeKitVersion(value: String): String =
    value.replace("\\s+".toRegex(), "-").lowercase()

fun resolveRuntimeKitRoot(candidate: File): File {
    if (candidate.resolve("runtime-kit.json").isFile) {
        return candidate
    }
    val nested = candidate.listFiles()
        ?.filter { it.isDirectory && it.resolve("runtime-kit.json").isFile }
        ?.sortedBy { it.name }
        ?.firstOrNull()
    return nested ?: candidate
}

fun copyDirectoryContents(source: File, target: File) {
    source.walkTopDown()
        .filter { it.isFile }
        .forEach { file ->
            val dest = target.resolve(file.relativeTo(source).path)
            dest.parentFile?.mkdirs()
            file.copyTo(dest, overwrite = true)
        }
}

@Suppress("UNCHECKED_CAST")
fun verifyRuntimeKit(runtimeKitDir: File) {
    val kitJson = runtimeKitDir.resolve("runtime-kit.json")
    val templateApk = runtimeKitDir.resolve("template.apk")
    val templateShaFile = runtimeKitDir.resolve("template.apk.sha256")
    val defaultKeyStore = runtimeKitDir.resolve("default_key_store.bks")
    val defaultKeyStoreShaFile = runtimeKitDir.resolve("default_key_store.bks.sha256")

    require(kitJson.isFile) { "Missing Runtime Kit metadata: $kitJson" }
    require(templateApk.isFile) { "Missing Runtime Kit template APK: $templateApk" }
    require(templateShaFile.isFile) { "Missing Runtime Kit template SHA-256 file: $templateShaFile" }
    require(defaultKeyStore.isFile) { "Missing Runtime Kit default keystore: $defaultKeyStore" }
    require(defaultKeyStoreShaFile.isFile) { "Missing Runtime Kit default keystore SHA-256 file: $defaultKeyStoreShaFile" }

    val meta = JsonSlurper().parse(kitJson) as Map<String, Any?>
    val template = meta["template"] as? Map<String, Any?> ?: emptyMap()
    val defaultKeyStoreMeta = meta["defaultKeyStore"] as? Map<String, Any?> ?: emptyMap()

    val expectedTemplateSha = template["sha256"]?.toString()?.trim()?.lowercase().orEmpty()
    val actualTemplateSha = templateApk.sha256String().lowercase()
    val fileTemplateSha = templateShaFile.readText(Charsets.UTF_8).trim().lowercase()
    require(expectedTemplateSha.isNotEmpty()) { "Runtime Kit metadata does not declare template.sha256" }
    require(expectedTemplateSha == actualTemplateSha) {
        "Runtime Kit template SHA-256 mismatch: json=$expectedTemplateSha actual=$actualTemplateSha"
    }
    require(fileTemplateSha == actualTemplateSha) {
        "Runtime Kit template.apk.sha256 mismatch: file=$fileTemplateSha actual=$actualTemplateSha"
    }
    ZipFile(templateApk).use { zip ->
        listOf("AndroidManifest.xml", "resources.arsc", "classes.dex", "assets/init.js").forEach { entryName ->
            require(zip.getEntry(entryName) != null) {
                "Runtime Kit template APK is missing required entry: $entryName"
            }
        }
    }

    val expectedKeyStoreSha = defaultKeyStoreMeta["sha256"]?.toString()?.trim()?.lowercase().orEmpty()
    val actualKeyStoreSha = defaultKeyStore.sha256String().lowercase()
    val fileKeyStoreSha = defaultKeyStoreShaFile.readText(Charsets.UTF_8).trim().lowercase()
    require(expectedKeyStoreSha.isNotEmpty()) { "Runtime Kit metadata does not declare defaultKeyStore.sha256" }
    require(expectedKeyStoreSha == actualKeyStoreSha) {
        "Runtime Kit default keystore SHA-256 mismatch: json=$expectedKeyStoreSha actual=$actualKeyStoreSha"
    }
    require(fileKeyStoreSha == actualKeyStoreSha) {
        "Runtime Kit default_key_store.bks.sha256 mismatch: file=$fileKeyStoreSha actual=$actualKeyStoreSha"
    }
}

val generatedTemplateAssetsDir = layout.buildDirectory.dir("generated/assets/apkBuilderTemplate")
val refreshTemplateBeforePackagingProperty = "autojs.apkBuilder.templatePlugin.refreshTemplate"
val refreshTemplateBeforePackaging = providers.gradleProperty(refreshTemplateBeforePackagingProperty)
    .map { value ->
        value.trim().lowercase().let { it in setOf("true", "1", "yes", "on") }
    }
    .orElse(false)

val prepareApkBuilderTemplateAssets = tasks.register("prepareApkBuilderTemplateAssets") {
    group = "build"
    description = "Copies a verified AutoJs6 Runtime Kit into the APK builder template plugin assets."

    val defaultRuntimeKitDir = rootProject.file(
        "app/build/runtime-kit/autojs6-runtime-kit-v${normalizeRuntimeKitVersion(versions.appVersionName)}+${versions.appVersionCode}-universal"
    )
    val configuredRuntimeKitDir = providers.gradleProperty(runtimeKitDirProperty).orNull?.let { path ->
        File(path).takeIf { it.isAbsolute } ?: rootProject.file(path)
    }
    val monorepoAppProject = rootProject.findProject(":app")

    if (configuredRuntimeKitDir == null && monorepoAppProject != null) {
        dependsOn(":app:generateRuntimeKit")
    }
    inputs.property(refreshTemplateBeforePackagingProperty, refreshTemplateBeforePackaging)
    inputs.property(runtimeKitDirProperty, configuredRuntimeKitDir?.path ?: "")
    inputs.dir(configuredRuntimeKitDir ?: defaultRuntimeKitDir)
    outputs.dir(generatedTemplateAssetsDir)

    doLast {
        val runtimeKitDir = resolveRuntimeKitRoot(configuredRuntimeKitDir ?: defaultRuntimeKitDir)
        require(runtimeKitDir.isDirectory) {
            "Runtime Kit directory does not exist. Run :app:generateRuntimeKit or pass -P$runtimeKitDirProperty=<dir>: $runtimeKitDir"
        }
        verifyRuntimeKit(runtimeKitDir)

        val assetsRoot = generatedTemplateAssetsDir.get().asFile
        project.delete(assetsRoot)
        val targetRuntimeKitDir = assetsRoot.resolve("runtime-kit").apply { mkdirs() }
        copyDirectoryContents(runtimeKitDir, targetRuntimeKitDir)
    }
}

tasks.matching {
    val taskName = it.name
    taskName.contains("lint", ignoreCase = true) || (taskName.startsWith("merge") && taskName.endsWith("Assets"))
}.configureEach {
    dependsOn(prepareApkBuilderTemplateAssets)
}
