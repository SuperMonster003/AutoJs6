plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("org.autojs.build.signs")
    id("com.android.application")
}

android {
    namespace = "org.autojs.plugin.speech.fake"
    compileSdk = versions.sdkVersionCompile
    defaultConfig {
        applicationId = "org.autojs.plugin.speech.fake"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures {
        buildConfig = false
    }
    // Match the host's optional local signer so the Plugin Center trusts the fake like an official plugin; otherwise
    // both use Android's debug signer.
    // zh-CN: 与宿主可选的本地签名一致, 使插件中心像对待官方插件一样信任该假插件; 否则二者都使用 Android 的调试签名.
    signingConfigs {
        if (signs.isValid) {
            create("hostTest") {
                storeFile = rootProject.file("app").resolve(signs.properties["storeFile"] as String)
                storePassword = signs.properties["storePassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                keyPassword = signs.properties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        debug { if (signs.isValid) signingConfig = signingConfigs.getByName("hostTest") }
    }
    lint {
        abortOnError = false
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

dependencies {
    implementation(project(":plugin-api:speech-api"))
}
