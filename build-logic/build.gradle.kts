import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    `kotlin-dsl` /* kotlin("jvm") */
}

repositories {
    mavenCentral()
    google()
}

run determineBuildLogicJdk@{

    val props = Properties().also { props ->
        val propsFile: File = rootDir.parentFile.resolve("version.properties")
        require(propsFile.isFile) { "version.properties not found in root directory" }
        propsFile.inputStream().use { props.load(it) }
    }

    fun Properties.requireIntProperty(key: String): Int = getProperty(key).also {
        requireNotNull(it) { "version.properties does not contain \"$key\"" }
    }.toInt()

    val javaVersionMinSupported = props.requireIntProperty("JAVA_VERSION_MIN_SUPPORTED").also {
        System.setProperty("gradle.java.version.min.supported", "$it")
    }
    val javaVersionMaxSupported = props.requireIntProperty("JAVA_VERSION_MAX_SUPPORTED").also {
        System.setProperty("gradle.java.version.max.supported", "$it")
    }
    val javaVersionMinSuggested = props.requireIntProperty("JAVA_VERSION_MIN_SUGGESTED").also {
        System.setProperty("gradle.java.version.min.suggested", "$it")
    }

    val javaVersionCurrentInt = JavaVersion.current().majorVersion.toInt()

    fun validateCurrentVersion(versionInt: Int, name: String) {
        if (versionInt < javaVersionMinSupported) {
            throw GradleException("Current \"$name\" version [$versionInt] does not meet the minimum requirement which [$javaVersionMinSupported] is needed.")
        }
        if (versionInt < javaVersionMinSuggested) {
            val suffix = if (javaVersionMaxSupported > 0) " (but not higher than [$javaVersionMaxSupported])" else ""
            logger.error("It is recommended to upgrade current \"$name\" version [$versionInt] to [$javaVersionMinSuggested] or higher$suffix.")
        }
        if (versionInt > javaVersionMaxSupported) {
            val suffix = if (javaVersionMaxSupported > javaVersionMinSuggested) " or lower (but not lower than [$javaVersionMinSuggested])" else ""
            logger.error("It is recommended to downgrade current \"$name\" version [$versionInt] to [$javaVersionMaxSupported]$suffix, as Gradle may be not compatible with JDK [$versionInt] for now.")
        }
    }

    validateCurrentVersion(versionInt = javaVersionCurrentInt, name = "Gradle JDK")

    fun isJvmTargetAvailable(target: Int): Boolean {
        try {
            // Weak dependency with Kotlin plugin: Use reflection to check if JvmTarget is available;
            // if reflection fails, treat it as available (to avoid incorrect downgrading).
            // zh-CN: 与 Kotlin 插件弱依赖: 反射判断 JvmTarget 是否可用; 反射失败则视为可用 (避免误降级).
            JvmTarget::class.java.enumConstants?.let { values ->
                return values.any { it?.toString().equals("JVM_$target", true) }
            }
        } catch (e: Throwable) {
            logger.error("Failed to check JVM target availability: $e")
        }
        return true
    }

    val (versionInt, versionSuffix) = run determineJavaVersion@{
        System.getProperty("gradle.java.version.overridden.by.user").toIntOrNull()?.let {
            validateCurrentVersion(versionInt = it, name = "User Specified Java Version")
            return@determineJavaVersion it to " [user-specified]"
        }

        var javaVersionInfoSuffix = ""
        var versionInt = JavaVersion.current().majorVersion.toInt()

        run tryAdjustJavaVersionByKotlinJvmTarget@{
            var isJvmCoercive = false

            while (versionInt > javaVersionMinSupported) {
                if (isJvmTargetAvailable(versionInt)) {
                    break
                }
                versionInt -= 1
                isJvmCoercive = true
            }

            if (isJvmCoercive) {
                javaVersionInfoSuffix += " [coercive-jvm-downgraded]"
            }
        }

        System.getProperty("gradle.java.version.coerced.by.gradle").toIntOrNull()?.let {
            if (versionInt > it) {
                versionInt = it
                javaVersionInfoSuffix += " [coercive-gradle-downgraded]"
            }
        }
        return@determineJavaVersion versionInt to javaVersionInfoSuffix
    }

    System.setProperty("gradle.java.version.select", "$versionInt")
    System.setProperty("gradle.java.version.suffix", versionSuffix)

    println("JDK versions: min [$javaVersionMinSupported] / select [$versionInt] / current [$javaVersionCurrentInt] / max [$javaVersionMaxSupported]")

    val (jvmTargetSelectInt, jvmTargetMaxInt) = run determineJvmTarget@{
        var selected = versionInt
        while (selected > javaVersionMinSupported) {
            if (isJvmTargetAvailable(selected)) {
                break
            }
            selected -= 1
        }

        var max = javaVersionMinSupported
        while (isJvmTargetAvailable(max)) {
            max += 1
        }
        max -= 1

        return@determineJvmTarget selected to max
    }

    System.setProperty("gradle.jvm.target.effective", "$jvmTargetSelectInt")

    println("JVM target: min [${javaVersionMinSupported}] / select [$jvmTargetSelectInt] / max [$jvmTargetMaxInt]")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(versionInt))
        }
    }

    kotlin {
        jvmToolchain(versionInt)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget("$jvmTargetSelectInt"))
        }
    }

    return@determineBuildLogicJdk versionInt

}
