plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.compose.api"
    compileSdk = versions.sdkVersionCompile
    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { buildConfig = false }
}
