plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

android {
    namespace = "org.autojs.plugin.yolo.fake"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "org.autojs.plugin.yolo.fake"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }

        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = false
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }

    lint {
        abortOnError = false
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = false
    }
}

dependencies {
    implementation(project(":plugin-api:yolo-api"))
}
