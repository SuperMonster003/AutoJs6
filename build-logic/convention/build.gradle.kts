plugins {
    `kotlin-dsl` /* kotlin("jvm") */
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())

    implementation(libs.apache.commons.compress)
    implementation(libs.tukaani.xz)
}

gradlePlugin {
    plugins {
        register("utils") {
            id = "org.autojs.build.utils"
            implementationClass = "org.autojs.build.UtilsPlugin"
            displayName = "AutoJs6 Build Utils Plugin"
            description = "Provides utilities for downloading, extracting archives, and version helpers."
        }
        register("versions") {
            id = "org.autojs.build.versions"
            implementationClass = "org.autojs.build.VersionsPlugin"
            displayName = "AutoJs6 Versions Plugin"
            description = "Provides version helpers."
        }
        register("signs") {
            id = "org.autojs.build.signs"
            implementationClass = "org.autojs.build.SignsPlugin"
            displayName = "AutoJs6 Signs Plugin"
            description = "Provides signing helpers."
        }
        register("properties") {
            id = "org.autojs.build.properties"
            implementationClass = "org.autojs.build.PropertiesPlugin"
            displayName = "AutoJs6 Properties Plugin"
            description = "Provides properties helpers."
        }
        register("jvmConvention") {
            id = "org.autojs.build.jvm-convention"
            implementationClass = "org.autojs.build.JvmConventionPlugin"
            displayName = "AutoJs6 JVM Convention Plugin"
            description = "Configures Java/Kotlin targets for Android modules using central Versions."
        }
        register("localAarRegisterConvention") {
            id = "org.autojs.build.local-arr-register-convention"
            implementationClass = "org.autojs.build.LocalAarRegisterConventionPlugin"
            displayName = "AutoJs6 Local AAR Register Convention Plugin"
            description = "Provides local AAR register helpers."
        }
    }
}

(gradle.extra["jdk"] as Int).let { jdk ->
    kotlin { jvmToolchain(jdk) }
    java { toolchain.languageVersion.set(JavaLanguageVersion.of(jdk)) }
}
