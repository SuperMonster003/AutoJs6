plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "pxb.android"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }
}

dependencies {
    implementation(libs.annotation)
}
