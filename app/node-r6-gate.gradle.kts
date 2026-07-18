import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec

val nodeR6ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

private fun nodeR6PluginProjectRoot(): File {
    val explicit = providers.gradleProperty("autojs.nodejs.pluginProjectDir")
        .orElse(providers.gradleProperty("autojs.nodejs.plugin.root"))
        .orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(rootProject::file)
    if (explicit != null) {
        return explicit.canonicalFile
    }
    val workspace = rootProject.projectDir.parentFile
    return listOf(
        workspace.resolve("AutoJs6-Plugin-NodeJs-Runtime"),
        workspace.resolve("autojs6-plugin-nodejs"),
    ).firstOrNull(File::isDirectory)?.canonicalFile
        ?: workspace.resolve("AutoJs6-Plugin-NodeJs-Runtime").canonicalFile
}

private fun Exec.configureNodeR6ProductionEvidenceCommand(failOnBlocked: Boolean) {
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/verify-production-evidence.js")
    val pluginRoot = nodeR6PluginProjectRoot()
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("version.properties"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPLETION_ROADMAP.md"))
    outputs.files(
        nodeR6ReportDir.map { it.file("r6-production-evidence-gate.json") },
        nodeR6ReportDir.map { it.file("r6-production-evidence-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        buildList {
            add("node")
            add(scriptFile.asFile.absolutePath)
            add("--repo-root")
            add(rootProject.projectDir.absolutePath)
            add("--plugin-root")
            add(pluginRoot.absolutePath)
            add("--report-dir")
            add(nodeR6ReportDir.get().asFile.absolutePath)
            if (failOnBlocked) {
                add("--fail-on-blocked")
            }
        },
    )
}

private fun requiredNodeR6GradleProperty(name: String, taskName: String): String =
    providers.gradleProperty(name)
        .orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: throw GradleException("Task $taskName requires an explicit -P$name=<value> Gradle property.")

private fun registerNodeR6ProductionSoakTask(
    taskName: String,
    kind: String,
    durationLabel: String,
) {
    tasks.register<Exec>(taskName) {
        group = "verification"
        description = "Runs and archives the fixed-duration $durationLabel R6 persistent required-plugin production soak."
        val runnerFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/run-production-soak.js")
        val instrumentationFile = rootProject.layout.projectDirectory.file(
            "app/src/androidTest/java/org/autojs/autojs/engine/NodeR6PersistentPluginSoakInstrumentationTest.kt",
        )
        val verifierFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/verify-production-evidence.js")
        val pluginRoot = nodeR6PluginProjectRoot()
        inputs.files(runnerFile, verifierFile, instrumentationFile)
        outputs.upToDateWhen { false }
        workingDir = rootProject.projectDir
        doFirst {
            val serial = requiredNodeR6GradleProperty("autojs.nodejs.r6.soak.serial", taskName)
            commandLine(
                "node",
                runnerFile.asFile.absolutePath,
                "--repo-root",
                rootProject.projectDir.absolutePath,
                "--plugin-root",
                pluginRoot.absolutePath,
                "--kind",
                kind,
                "--serial",
                serial,
            )
        }
    }
}

tasks.register<Exec>("verifyNodeR6ProductionEvidenceContract") {
    group = "verification"
    description = "Verifies accepted and fail-closed R6 device/soak evidence contract fixtures without producing release evidence."
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/test-production-evidence-contract.js")
    val verifierFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/verify-production-evidence.js")
    inputs.files(scriptFile, verifierFile)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine("node", scriptFile.asFile.absolutePath)
}

tasks.register<Exec>("generateNodeR6ProductionEvidenceStatus") {
    group = "verification"
    description = "Writes the current R6 production-evidence freshness decision without hiding missing device or soak reports."
    dependsOn("verifyNodeR6ProductionEvidenceContract")
    configureNodeR6ProductionEvidenceCommand(failOnBlocked = false)
}

tasks.register<Exec>("verifyNodeR6ProductionEvidenceGate") {
    group = "verification"
    description = "Strictly verifies current persistent-plugin lifecycle, device matrix, packaged, ABI, crash, and soak evidence."
    dependsOn("verifyNodeR6ProductionEvidenceContract")
    configureNodeR6ProductionEvidenceCommand(failOnBlocked = true)
}

tasks.register<Exec>("collectNodeR6DeviceEvidence") {
    group = "verification"
    description = "Collects and archives one fixed-contract R6 production device target from explicit target and serial properties."
    val runnerFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/run-device-evidence.js")
    val verifierFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/verify-production-evidence.js")
    val pluginRoot = nodeR6PluginProjectRoot()
    inputs.files(runnerFile, verifierFile)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    doFirst {
        val targetProperty = "autojs.nodejs.r6.device.target"
        val serialProperty = "autojs.nodejs.r6.device.serial"
        val target = requiredNodeR6GradleProperty(targetProperty, name)
        val serial = requiredNodeR6GradleProperty(serialProperty, name)
        val acceptedTargets = setOf("sony-arm64", "sony-armeabi-v7a", "second-oem-arm64", "emulator-x86_64")
        if (target !in acceptedTargets) {
            throw GradleException(
                "Task $name requires -P$targetProperty to be one of ${acceptedTargets.joinToString()}.",
            )
        }
        commandLine(
            "node",
            runnerFile.asFile.absolutePath,
            "--repo-root",
            rootProject.projectDir.absolutePath,
            "--plugin-root",
            pluginRoot.absolutePath,
            "--target",
            target,
            "--serial",
            serial,
        )
    }
}

registerNodeR6ProductionSoakTask(
    taskName = "collectNodeR6QuickSoak8hEvidence",
    kind = "quick",
    durationLabel = "8-hour quick",
)

registerNodeR6ProductionSoakTask(
    taskName = "collectNodeR6FinalSoak24hEvidence",
    kind = "final",
    durationLabel = "24-hour final",
)

tasks.register<Exec>("verifyNodeR6PersistentPluginSoakSmoke") {
    group = "verification"
    description = "Runs the focused, explicitly non-archivable R6 persistent required-plugin soak smoke."
    val runnerFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/run-production-soak.js")
    val instrumentationFile = rootProject.layout.projectDirectory.file(
        "app/src/androidTest/java/org/autojs/autojs/engine/NodeR6PersistentPluginSoakInstrumentationTest.kt",
    )
    val verifierFile = rootProject.layout.projectDirectory.file("tools/nodejs/r6/verify-production-evidence.js")
    val pluginRoot = nodeR6PluginProjectRoot()
    val smokeDurationMs = providers.gradleProperty("autojs.nodejs.r6.soak.smokeDurationMs")
        .orElse("30000")
        .get()
    inputs.files(runnerFile, verifierFile, instrumentationFile)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    doFirst {
        val serial = requiredNodeR6GradleProperty("autojs.nodejs.r6.soak.serial", name)
        commandLine(
            "node",
            runnerFile.asFile.absolutePath,
            "--repo-root",
            rootProject.projectDir.absolutePath,
            "--plugin-root",
            pluginRoot.absolutePath,
            "--smoke",
            "--smoke-duration-ms",
            smokeDurationMs,
            "--serial",
            serial,
            "--no-archive",
        )
    }
}
