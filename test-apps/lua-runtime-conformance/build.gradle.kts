plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

android {
    namespace = "org.autojs.plugin.lua.runtime.fake"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "org.autojs.plugin.lua.runtime.fake"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    lint {
        abortOnError = false
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.enable = false
    }
}

dependencies {
    implementation(project(":plugin-api:lua-runtime-api"))

    testImplementation(libs.junit)
}
