plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.compose.api"
    compileSdk = versions.sdkVersionCompile
    defaultConfig {
        minSdk = versions.sdkVersionMin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { buildConfig = false }
}

dependencies {
    api(project(":plugin-api:common-plugin-api"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("compose.api.root", rootProject.projectDir.absolutePath)
    systemProperty("compose.api.reportDir", layout.buildDirectory.dir("reports/contract").get().asFile.absolutePath)
}
