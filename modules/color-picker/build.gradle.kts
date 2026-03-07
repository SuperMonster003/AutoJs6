plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "com.jaredrummler.android.colorpicker"
    version = "1.1.0"
    resourcePrefix = "cpv_"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
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
    implementation(libs.preference.ktx)
}
