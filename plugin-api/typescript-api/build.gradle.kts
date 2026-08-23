plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.typescript.api"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = true
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }
}

dependencies {
    api(project(":plugin-api:common-plugin-api"))

    testImplementation(libs.junit)
}
