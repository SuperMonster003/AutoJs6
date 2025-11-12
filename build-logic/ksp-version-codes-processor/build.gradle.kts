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

System.getProperty("gradle.java.version.select").toInt().let { jdk ->
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(jdk))
        }
    }
    kotlin {
        jvmToolchain(jdk)
    }
}

System.getProperty("gradle.jvm.target.effective").let { jvm ->
    java {
        sourceCompatibility = JavaVersion.toVersion(jvm)
        targetCompatibility = JavaVersion.toVersion(jvm)
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvm))
        }
    }
}