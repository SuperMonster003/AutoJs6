@file:Suppress("ObjectLiteralToLambda")

import java.util.*

plugins {
    `kotlin-dsl` /* kotlin("jvm") */
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.apache.commons.compress)
    implementation(libs.tukaani.xz)

    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("buildUtils", object : Action<PluginDeclaration> {
            override fun execute(t: PluginDeclaration) {
                t.id = "org.autojs.build.utils"
                t.implementationClass = "org.autojs.build.UtilsPlugin"
                t.displayName = "AutoJs6 Build Utils Plugin"
                t.description = "Provides utilities for downloading, extracting archives, and version helpers."
            }
        })
        create("buildVersions", object : Action<PluginDeclaration> {
            override fun execute(t: PluginDeclaration) {
                t.id = "org.autojs.build.versions"
                t.implementationClass = "org.autojs.build.VersionsPlugin"
                t.displayName = "AutoJs6 Versions Plugin"
                t.description = "Provides version helpers."
            }
        })
        create("buildSigns", object : Action<PluginDeclaration> {
            override fun execute(t: PluginDeclaration) {
                t.id = "org.autojs.build.signs"
                t.implementationClass = "org.autojs.build.SignsPlugin"
                t.displayName = "AutoJs6 Signs Plugin"
                t.description = "Provides signing helpers."
            }
        })
        create("buildProperties", object : Action<PluginDeclaration> {
            override fun execute(t: PluginDeclaration) {
                t.id = "org.autojs.build.properties"
                t.implementationClass = "org.autojs.build.PropertiesPlugin"
                t.displayName = "AutoJs6 Properties Plugin"
                t.description = "Provides properties helpers."
            }
        })
        create("jvmConvention", object : Action<PluginDeclaration> {
            override fun execute(t: PluginDeclaration) {
                t.id = "org.autojs.build.jvm-convention"
                t.implementationClass = "org.autojs.build.JvmConventionPlugin"
                t.displayName = "AutoJs6 JVM Convention Plugin"
                t.description = "Configures Java/Kotlin targets for Android modules using central Versions."
            }
        })
    }
}

run determineBuildSrcJdk@{
    val propsFile: File = rootDir.parentFile.resolve("version.properties")
    val minSupported: Int = Properties().let { props ->
        require(propsFile.isFile) {
            "version.properties not found in root directory"
        }
        propsFile.inputStream().use { props.load(it) }
        val minSupportedVersion = props.getProperty("JAVA_VERSION_MIN_SUPPORTED")
        require(minSupportedVersion != null) {
            "version.properties does not contain \"JAVA_VERSION_MIN_SUPPORTED\""
        }
        minSupportedVersion.toInt()
    }
    val current = JavaVersion.current().majorVersion.toIntOrNull() ?: minSupported

    fun tryAdjustByKotlinJvmTarget(sourceVersion: Int): Int {
        return runCatching {
            val cls = Class.forName("org.jetbrains.kotlin.gradle.dsl.JvmTarget")
            val enumConstants = cls.enumConstants ?: return sourceVersion
            var tmpVersion = sourceVersion
            while (tmpVersion > minSupported) {
                val wanted = "JVM_$tmpVersion"
                if (enumConstants.any { it?.toString().equals(wanted, ignoreCase = true) }) {
                    return tmpVersion
                }
                tmpVersion -= 1
            }
            return@runCatching sourceVersion
        }.getOrDefault(sourceVersion)
    }

    tryAdjustByKotlinJvmTarget(maxOf(current, minSupported)).also {
        println("Toolchain: selected [$it] / current [$current] / min [$minSupported]")
    }
}.let { jdk ->

    kotlin {
        jvmToolchain(jdk)
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(jdk))
    }

}
