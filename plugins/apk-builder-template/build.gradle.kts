import java.security.MessageDigest
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
val enableRemoteBuild = providers.gradleProperty(enableRemoteBuildProperty)
    .map { value ->
        value.trim().lowercase().let { it in setOf("true", "1", "yes", "on") }
    }
    .orElse(false)

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
        buildConfigField("int", "PROTOCOL_VERSION", "1")
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
    implementation(files("$rootDir/libs/tiny-sign-0_9.jar"))
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

val generatedTemplateAssetsDir = layout.buildDirectory.dir("generated/assets/apkBuilderTemplate")
val sourceTemplateApk = rootProject.layout.projectDirectory.file("app/src/main/assets-app/template.apk")
val refreshTemplateBeforePackagingProperty = "autojs.apkBuilder.templatePlugin.refreshTemplate"
val refreshTemplateBeforePackaging = providers.gradleProperty(refreshTemplateBeforePackagingProperty)
    .map { value ->
        value.trim().lowercase().let { it in setOf("true", "1", "yes", "on") }
    }
    .orElse(false)

val prepareApkBuilderTemplateAssets = tasks.register("prepareApkBuilderTemplateAssets") {
    group = "build"
    description = "Copies the current AutoJs6 inrt template APK into the APK builder template plugin assets."

    if (refreshTemplateBeforePackaging.get()) {
        dependsOn(":app:embeddedNodeRefreshInrtTemplateApk")
    }
    inputs.property(refreshTemplateBeforePackagingProperty, refreshTemplateBeforePackaging)
    inputs.file(sourceTemplateApk)
    outputs.dir(generatedTemplateAssetsDir)

    doLast {
        val templateDir = generatedTemplateAssetsDir.get().asFile.resolve("template").apply {
            mkdirs()
        }
        val targetApk = templateDir.resolve("template.apk")
        sourceTemplateApk.asFile.copyTo(targetApk, overwrite = true)
        templateDir.resolve("template.sha256").writeText(targetApk.sha256String() + "\n", Charsets.UTF_8)
    }
}

tasks.matching {
    val taskName = it.name
    taskName.contains("lint", ignoreCase = true) || (taskName.startsWith("merge") && taskName.endsWith("Assets"))
}.configureEach {
    dependsOn(prepareApkBuilderTemplateAssets)
}
