plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.tool.api"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        // Exported Binder contracts remain consumable by plugins on the audited SDK 36 platform.
        aarMetadata { minCompileSdk = 36 }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Maven Central (MCP roadmap D58), see ../maven-central.gradle.kts.
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

extra["mavenArtifactId"] = "autojs6-plugin-tool-api"
extra["mavenName"] = "AutoJs6 Plugin Tool API"
extra["mavenDescription"] = "Contract of AutoJs6 plugins that describe their own tools for MCP, agents and scripts, with an optional provider base class."
extra["mavenVersion"] = "1.0.0"
apply(from = "../maven-central.gradle.kts")

dependencies {
    api(project(":plugin-api:common-plugin-api"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
}
