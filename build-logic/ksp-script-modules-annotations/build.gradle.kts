import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Annotations of the script module layer (docs/augment/ROADMAP.md T2), shared by the host (compile time only) and
// ksp-script-modules-processor. No dependencies besides the Kotlin standard library.
// zh-CN: 脚本模块层的注解 (ROADMAP T2), 由宿主 (仅编译期) 与 ksp-script-modules-processor 共用, 除 Kotlin 标准库外无依赖.

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
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
