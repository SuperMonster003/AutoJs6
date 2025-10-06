import java.util.Properties

plugins {
    `kotlin-dsl` /* kotlin("jvm") */
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
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
