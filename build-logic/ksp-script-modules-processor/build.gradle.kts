import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:${System.getProperty("com.google.devtools.ksp")}")
    implementation(project(":ksp-script-modules-annotations"))
}

System.getProperty("gradle.java.version.min.supported").let { minJdk ->
    java {
        sourceCompatibility = JavaVersion.toVersion(minJdk)
        targetCompatibility = JavaVersion.toVersion(minJdk)
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(minJdk))
        }
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(minJdk))
        }
        jvmToolchain(minJdk.toInt())
    }
}
