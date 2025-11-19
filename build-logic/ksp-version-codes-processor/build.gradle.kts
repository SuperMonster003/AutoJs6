import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())

    implementation("com.google.devtools.ksp:symbol-processing-api:${System.getProperty("com.google.devtools.ksp")}")
    implementation(libs.kotlin.csv.jvm)
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
