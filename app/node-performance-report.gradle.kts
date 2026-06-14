import java.io.File
import java.util.Properties

private fun findNodePerformanceSdkDir(rootDir: File): File? {
    val localProperties = rootDir.resolve("local.properties")
    if (localProperties.isFile) {
        val properties = Properties()
        localProperties.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")?.takeIf(String::isNotBlank)?.let { return File(it) }
    }
    return listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .asSequence()
        .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        .map(::File)
        .firstOrNull(File::isDirectory)
}

private fun findNodePerformanceAdb(rootDir: File): File? {
    val exe = if (System.getProperty("os.name").contains("win", ignoreCase = true)) "adb.exe" else "adb"
    return findNodePerformanceSdkDir(rootDir)
        ?.resolve("platform-tools/$exe")
        ?.takeIf(File::isFile)
}

val nodePerformanceReportDeviceDir = providers.gradleProperty("autojs.nodejs.perf.deviceDir")
    .orElse("/sdcard/Download/AutoJs6/nodejs/perf")
val nodePerformanceReportHostDir = layout.buildDirectory.dir("reports/nodejs/perf")

tasks.register("pullNodeEmbeddedPerformanceReports") {
    group = "verification"
    description = "Pulls Embedded Node performance JSON reports from the connected device."

    outputs.dir(nodePerformanceReportHostDir)
    outputs.upToDateWhen { false }

    doLast {
        val deviceDir = nodePerformanceReportDeviceDir.get()
        val outputDir = nodePerformanceReportHostDir.get().asFile
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val adb = findNodePerformanceAdb(rootProject.projectDir)
            ?: throw GradleException(
                "Unable to locate adb. Set sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT."
            )
        val process = ProcessBuilder(adb.absolutePath, "pull", "$deviceDir/.", outputDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val outputText = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                """
                Failed to pull Embedded Node performance reports from device.
                Device dir: $deviceDir
                Host dir: ${outputDir.absolutePath}
                Exit code: $exitCode
                adb output:
                $outputText
                Run NodeEmbeddedPerformanceInstrumentationTest first, or pass -Pautojs.nodejs.perf.deviceDir=<device-dir>.
                """.trimIndent()
            )
        }
        val reports = outputDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .toList()
        if (reports.isEmpty()) {
            throw GradleException(
                "No Embedded Node performance JSON reports were pulled from $deviceDir into ${outputDir.absolutePath}."
            )
        }
        logger.lifecycle("Pulled ${reports.size} Embedded Node performance report(s) to ${outputDir.absolutePath}")
    }
}
