package org.autojs.build

import org.autojs.build.Utils.getOrNull
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.kotlin.dsl.extra
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.text.RegexOption.IGNORE_CASE

class Versions @JvmOverloads constructor(
    private val project: Project,
    filePath: String = "${project.rootDir}/version.properties",
) {
    private val gradle = project.gradle

    private val bp = BuildProperties.loadFrom(filePath)

    private val javaVersionCurrentInt = JavaVersion.current().majorVersion.toInt()
    private val javaVersionMinSupported: Int = bp.requireInt("JAVA_VERSION_MIN_SUPPORTED")
    private val javaVersionMaxSupported: Int = bp.requireInt("JAVA_VERSION_MAX_SUPPORTED")

    private val javaVersionInt: Int = System.getProperty("gradle.java.version.select").toInt()
    private val javaVersionInfoSuffix: String = System.getProperty("gradle.java.version.suffix", "")

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

    val javaVersion: JavaVersion
        get() = JavaVersion.toVersion(javaVersionInt)

    val javaVersionString: String
        get() = javaVersion.toString()

    operator fun get(propertyName: String) = bp[propertyName]

    operator fun get(propertyInfo: List<String>) = bp[propertyInfo]

    fun showInfo() {
        val title = "Version information for ${project.rootProject.name} app library"

        val infoVerName = "Version name: $appVersionName"
        val infoVerCode = "Version code: ${if (isBuildNumberAutoIncremented) "${appVersionCode + 1} [auto-incremented]" else appVersionCode}"
        val infoVerSdk = "SDK versions: min [$sdkVersionMin] / target [$sdkVersionTarget] / compile [$sdkVersionCompile]"
        val infoVerJdk = "JDK versions: min [$javaVersionMinSupported] / select [$javaVersionInt] / current [$javaVersionCurrentInt] / max [$javaVersionMaxSupported]"
        val infoVerJava = "Java version: $javaVersion${
            when {
                gradle.extra.getOrNull<Boolean>("isHideConsoleInfoHintSuffix") == true -> ""
                else -> javaVersionInfoSuffix
            }
        }"

        val maxLength = arrayOf(title, infoVerName, infoVerCode, infoVerSdk, infoVerJdk, infoVerJava).maxOf { it.length }

        arrayOf(
            "=".repeat(maxLength),
            title,
            "-".repeat(maxLength),
            infoVerName,
            infoVerCode,
            infoVerSdk,
            infoVerJdk,
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

}
