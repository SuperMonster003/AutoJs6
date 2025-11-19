plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.afollestad.materialdialogs"
    version = "0.9.6.0 (179)"

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
    implementation(libs.appcompat.v7)
    implementation(libs.support.annotations)
    implementation(libs.recyclerview.v7)
    implementation(libs.materialprogressbar)
}
