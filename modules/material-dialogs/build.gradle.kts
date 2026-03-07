plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "com.afollestad.materialdialogs"
    version = "0.9.6.0 (179) Mod"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("progress-proguard.txt")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.annotation)
    implementation(libs.recyclerview)
    implementation(libs.materialprogressbar)
}
