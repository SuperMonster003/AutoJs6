plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
    kotlin("plugin.parcelize")
}

android {
    namespace = "org.autojs.plugin.paddle.ocr.api"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }
}
