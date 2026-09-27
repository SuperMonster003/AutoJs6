plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("org.autojs.build.signs")
    id("com.android.application")
}

android {
    namespace = "org.autojs.plugin.three.stove.agent.fake"
    compileSdk = versions.sdkVersionCompile
    defaultConfig {
        applicationId = "org.autojs.plugin.three.stove.agent.fake"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures {
        aidl = true
        buildConfig = false
    }
    // Match the host's optional local signer, otherwise both use Android's debug signer.
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
    packaging { jniLibs.useLegacyPackaging = false }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

dependencies {
    implementation(project(":plugin-api:three-stove-agent-api"))
}
