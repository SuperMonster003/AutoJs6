plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${System.getProperty("com.google.devtools.ksp")}")
    implementation(libs.kotlin.csv.jvm)
}

(gradle.extra["jdk"] as Int).let { jdk ->
    kotlin { jvmToolchain(jdk) }
    java { toolchain.languageVersion.set(JavaLanguageVersion.of(jdk)) }
}
