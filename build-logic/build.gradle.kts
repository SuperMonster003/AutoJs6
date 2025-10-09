import java.util.*

plugins {
    `kotlin-dsl` /* kotlin("jvm") */
}

repositories {
    mavenCentral()
    google()
}

gradle.extra["jdk"] = run determineBuildLogicJdk@{
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
}.also { jdk ->
    kotlin { jvmToolchain(jdk) }
    java { toolchain.languageVersion.set(JavaLanguageVersion.of(jdk)) }
}
