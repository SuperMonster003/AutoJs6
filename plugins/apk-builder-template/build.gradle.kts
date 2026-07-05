import java.security.MessageDigest

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
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
        buildConfigField("String", "TEMPLATE_PACKAGE_NAME", "\"org.autojs.autojs6.inrt\"")
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(layout.buildDirectory.dir("generated/assets/apkBuilderTemplate").get().asFile.absolutePath)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

dependencies {
    implementation(project(":plugin-api:apk-builder-template"))
    implementation(project(":plugin-api:common-plugin-api"))
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

val prepareApkBuilderTemplateAssets = tasks.register("prepareApkBuilderTemplateAssets") {
    group = "build"
    description = "Copies the current AutoJs6 inrt template APK into the APK builder template plugin assets."

    dependsOn(":app:embeddedNodeRefreshInrtTemplateApk")
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

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareApkBuilderTemplateAssets)
}
