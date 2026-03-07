plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "com.wdullaer.materialdatetimepicker"
    version = "4.2.3 (54) Mod"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
    testImplementation(libs.junit.quickcheck.core)
    testImplementation(libs.junit.quickcheck.generators)

    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
}
