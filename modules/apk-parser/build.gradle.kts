plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "net.dongliu.apk.parser"
    version = "6"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
        multiDexEnabled = true
    }

    lint {
        targetSdk = versions.sdkVersionTarget
    }
}

dependencies {
    implementation(libs.bcprov.jdk15on)
    implementation(libs.bcpkix.jdk15on)
    implementation(libs.annotation)
}
