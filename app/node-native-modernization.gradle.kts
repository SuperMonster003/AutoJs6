import org.gradle.api.tasks.Exec

val nodeNativeModernizationReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")
val nodeNativeSymbolUsageJson = nodeNativeModernizationReportDir.map { it.file("native-symbol-usage.json") }
val nodeNativeSymbolUsageMarkdown = nodeNativeModernizationReportDir.map { it.file("native-symbol-usage.md") }
val nodeRuntimeAdapterAbiJson = nodeNativeModernizationReportDir.map { it.file("runtime-adapter-abi-v1.json") }
val nodeRuntimeAdapterAbiMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-adapter-abi-v1.md") }
val nodeRuntimeBuildPlanJson = nodeNativeModernizationReportDir.map { it.file("runtime-build-plan.json") }
val nodeRuntimeBuildPlanMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-build-plan.md") }

tasks.register<Exec>("generateNodeNativeSymbolUsageReport") {
    group = "verification"
    description = "Generates the Phase 9 native integration boundary map report."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/generate-native-symbol-usage.js")
    val sourceDir = project.layout.projectDirectory.dir("src/main/cpp")
    inputs.file(scriptFile)
    inputs.dir(sourceDir)
    outputs.files(nodeNativeSymbolUsageJson, nodeNativeSymbolUsageMarkdown)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodeNativeModernizationReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRuntimeAdapterAbi") {
    group = "verification"
    description = "Verifies the Phase 9 versioned C runtime adapter ABI contract."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-adapter-abi.js")
    val headerFile = project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h")
    inputs.file(scriptFile)
    inputs.file(headerFile)
    outputs.files(nodeRuntimeAdapterAbiJson, nodeRuntimeAdapterAbiMarkdown)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--header",
        headerFile.asFile.absolutePath,
        "--report-dir",
        nodeNativeModernizationReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRuntimeBuildPlan") {
    group = "verification"
    description = "Verifies the Phase 9 reproducible Android Node runtime build plan."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/verify-runtime-build-plan.js")
    val lockFile = rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/runtime-build.lock.json")
    val windowsBuildScript = rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/build-node-runtime.ps1")
    val unixBuildScript = rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/build-node-runtime.sh")
    inputs.file(scriptFile)
    inputs.file(lockFile)
    inputs.file(windowsBuildScript)
    inputs.file(unixBuildScript)
    outputs.files(nodeRuntimeBuildPlanJson, nodeRuntimeBuildPlanMarkdown)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--lock-file",
        lockFile.asFile.absolutePath,
        "--report-dir",
        nodeNativeModernizationReportDir.get().asFile.absolutePath,
    )
}

tasks.register("verifyNodePhase9NativeModernizationPlan") {
    group = "verification"
    description = "Runs Phase 9 native modernization planning checks without changing the default runtime."

    dependsOn(
        "generateNodeNativeSymbolUsageReport",
        "verifyNodeRuntimeAdapterAbi",
        "verifyNodeRuntimeBuildPlan",
    )
}
