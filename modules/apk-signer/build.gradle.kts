plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "com.mcal.apksigner"
    version = "1.1-template (11)"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            java.directories.add("src/main/java")
        }
    }

    lint {
        targetSdk = versions.sdkVersionTarget
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)

    // @Hint by SuperMonster003 on Mar 14, 2025.
    //  ! Dependency "prov" contains "core" (zh-CN: 依赖 "prov" 已包含 "core"):
    //  ! |    \--- com.madgag.spongycastle:prov:1.58.0.0
    //  ! |         +--- com.madgag.spongycastle:core:1.58.0.0
    //  ! |         \--- junit:junit:4.12 (*)
    //  # implementation("com.madgag.spongycastle:core:1.58.0.0")
    implementation(libs.prov)

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.espresso.core)
}
