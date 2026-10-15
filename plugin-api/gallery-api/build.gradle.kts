plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.gallery.api"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        // Exported Binder contracts remain consumable by plugins on the audited SDK 36 platform.
        aarMetadata { minCompileSdk = 36 }
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

dependencies {
    api(project(":plugin-api:common-plugin-api"))
    testImplementation("junit:junit:4.13.2")
}
