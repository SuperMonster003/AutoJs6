package org.autojs.build

import org.autojs.build.Utils.getOrNull
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.kotlin.dsl.extra
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.properties.Delegates
import kotlin.text.RegexOption.IGNORE_CASE

class Versions @JvmOverloads constructor(
    private val project: Project,
    filePath: String = "${project.rootDir}/version.properties",
) {
    private val gradle = project.gradle
    private val logger = project.logger

    private val currentVersionInt = JavaVersion.current().majorVersion.toInt()

    private val bp = BuildProperties.loadFrom(filePath)

    private val javaVersionMinSuggested: Int = bp.requireInt("JAVA_VERSION_MIN_SUGGESTED")
    private val javaVersionMaxSupported: Int = bp.requireInt("JAVA_VERSION_MAX_SUPPORTED")

    private var isBuildNumberAutoIncremented = false
    private val minBuildTimeGap = Utils.hours2Millis(0.75)

    private val isBuildGapEnough
        get() = Date().time - bp["BUILD_TIME"].toLong() > minBuildTimeGap

    val sdkVersionMin = bp.requireInt("MIN_SDK_VERSION")
    val sdkVersionTarget = bp.requireInt("TARGET_SDK_VERSION")
    val sdkVersionTargetInrt = bp.requireInt("TARGET_SDK_VERSION_INRT")
    val sdkVersionCompile = bp.requireInt("COMPILE_SDK_VERSION")
    val appVersionName = bp.requireString("VERSION_NAME")
    val appVersionCode = bp.requireInt("VERSION_BUILD")
    val vscodeExtRequiredVersion = bp.requireString("VSCODE_EXT_REQUIRED_VERSION")

    private val javaVersionMinSupported: Int = bp.requireInt("JAVA_VERSION_MIN_SUPPORTED")

    var javaVersionInt by Delegates.notNull<Int>()
    var javaVersionInfoSuffix by Delegates.notNull<String>()

    val javaVersion: JavaVersion
        get() = JavaVersion.toVersion(javaVersionInt)

    val javaVersionString: String
        get() = javaVersion.toString()

    init {
        validateCurrentVersion()
        determineJavaVersion().also { (javaVersionInt, javaVersionInfoSuffix) ->
            this.javaVersionInt = javaVersionInt
            this.javaVersionInfoSuffix = javaVersionInfoSuffix
        }
    }

    private fun validateCurrentVersion() {
        if (gradle.extra.getOrNull<Boolean>(VALIDATED_EXTRA_KEY) == true) {
            return
        }
        if (currentVersionInt < javaVersionMinSupported) {
            throw GradleException("Current Gradle JDK version [$currentVersionInt] does not meet the minimum requirement which [$javaVersionMinSupported] is needed.")
        }
        if (currentVersionInt < javaVersionMinSuggested) {
            val suffix = if (javaVersionMaxSupported > 0) " (but not higher than [$javaVersionMaxSupported])" else ""
            logger.error("It is recommended to upgrade current Gradle JDK version [$currentVersionInt] to [$javaVersionMinSuggested] or higher$suffix.")
        }
        if (currentVersionInt > javaVersionMaxSupported) {
            val suffix = if (javaVersionMaxSupported > javaVersionMinSuggested) " or lower (but not lower than [$javaVersionMinSuggested])" else ""
            logger.error("It is recommended to downgrade current Gradle JDK version [$currentVersionInt] to [$javaVersionMaxSupported]$suffix, as Gradle may be not compatible with JDK [$currentVersionInt] for now.")
        }
        gradle.extra.set(VALIDATED_EXTRA_KEY, true)
    }

    private fun determineJavaVersion(): Pair<Int, String> {
        var javaVersionInfoSuffix = ""

        gradle.extra.getOrNull<Int>("javaVersionOverriddenByUser")?.let {
            javaVersionInfoSuffix += " [user-specified]"
            return it to javaVersionInfoSuffix
        }

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

        gradle.extra.getOrNull<Int>("javaVersionCoercedByGradle")?.let {
            if (versionInt > it) {
                versionInt = it
                javaVersionInfoSuffix += " [coercive-gradle-downgraded]"
            }
        }
        return versionInt to javaVersionInfoSuffix
    }

    private fun isJvmTargetAvailable(target: Int): Boolean {
        try {
            // Weak dependency with Kotlin plugin: Use reflection to check if JvmTarget is available;
            // if reflection fails, treat it as available (to avoid incorrect downgrading).
            // zh-CN: 与 Kotlin 插件弱依赖: 反射判断 JvmTarget 是否可用; 反射失败则视为可用 (避免误降级).
            val cls = Class.forName("org.jetbrains.kotlin.gradle.dsl.JvmTarget")
            cls.enumConstants?.let { values ->
                return values.any { it?.toString().equals("JVM_$target", true) }
            }
        } catch (e: Throwable) {
            logger.error("Failed to check JVM target availability: $e")
        }
        return true
    }

    operator fun get(propertyName: String) = bp[propertyName]

    operator fun get(propertyInfo: List<String>) = bp[propertyInfo]

    fun showInfo() {
        val title = "Version information for AutoJs6 app library"

        val infoVerName = "Version name: $appVersionName"
        val infoVerCode = "Version code: ${if (isBuildNumberAutoIncremented) "${appVersionCode + 1} [auto-incremented]" else appVersionCode}"
        val infoVerSdk = "SDK versions: min [$sdkVersionMin] / target [$sdkVersionTarget] / compile [$sdkVersionCompile]"
        val infoVerJava = "Java version: $javaVersion${
            when {
                gradle.extra.getOrNull<Boolean>("isHideConsoleInfoHintSuffix") == true -> ""
                else -> javaVersionInfoSuffix
            }
        }"

        val maxLength = arrayOf(title, infoVerName, infoVerCode, infoVerSdk, infoVerJava).maxOf { it.length }

        arrayOf(
            "=".repeat(maxLength),
            title,
            "-".repeat(maxLength),
            infoVerName,
            infoVerCode,
            infoVerSdk,
            infoVerJava,
            "=".repeat(maxLength),
            "",
        ).forEach { println(it) }
    }

    fun handleIfNeeded(project: Project, flavorName: String, targetBuildType: List<String>) {
        project.gradle.taskGraph.whenReady(object : Action<TaskExecutionGraph> {
            override fun execute(taskGraph: TaskExecutionGraph) {
                for (buildType in targetBuildType) {
                    if (taskGraph.hasTask(Utils.getAssembleFullTaskName(project.name, flavorName, buildType))) {
                        return appendToTask(project, flavorName, buildType)
                    }
                }
                return showInfo()
            }
        })
    }

    private fun appendToTask(project: Project, flavorName: String, buildType: String) {
        project.tasks.getByName(Utils.getAssembleTaskName(flavorName, buildType)).doLast {
            updateProperties()
            println()
            showInfo()
        }
    }

    private fun updateProperties() {
        val propsPath = bp.path
        val props = Properties().apply {
            FileInputStream(propsPath).use { load(it) }
        }

        if (isBuildGapEnough) {
            val isBuildAppRelease = gradle.startParameter.taskNames.any {
                it.contains(Regex("^(:?app:)?assemble(app|inrt)release", IGNORE_CASE))
            }
            if (!isBuildAppRelease) {
                props["VERSION_BUILD"] = "${appVersionCode + 1}"
                isBuildNumberAutoIncremented = true
            }
        }
        props["BUILD_TIME"] = "${Date().time}"

        FileOutputStream(propsPath).use { out ->
            props.store(out, null)
        }
    }

    companion object {
        private const val VALIDATED_EXTRA_KEY = "org.autojs.build.Versions.currentValidated"
    }

}
