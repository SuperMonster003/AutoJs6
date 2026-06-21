import org.gradle.api.tasks.Exec

val nodeNativeModernizationReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")
val nodeNativeSymbolUsageJson = nodeNativeModernizationReportDir.map { it.file("native-symbol-usage.json") }
val nodeNativeSymbolUsageMarkdown = nodeNativeModernizationReportDir.map { it.file("native-symbol-usage.md") }
val nodeRuntimeAdapterAbiJson = nodeNativeModernizationReportDir.map { it.file("runtime-adapter-abi-v1.json") }
val nodeRuntimeAdapterAbiMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-adapter-abi-v1.md") }
val nodeRuntimeAdapterExportJson = nodeNativeModernizationReportDir.map { it.file("runtime-adapter-export.json") }
val nodeRuntimeAdapterExportMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-adapter-export.md") }
val nodeRuntimeBuildPlanJson = nodeNativeModernizationReportDir.map { it.file("runtime-build-plan.json") }
val nodeRuntimeBuildPlanMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-build-plan.md") }
val nodeNative16KbAlignmentJson = nodeNativeModernizationReportDir.map { it.file("native-16kb-alignment.json") }
val nodeNative16KbAlignmentMarkdown = nodeNativeModernizationReportDir.map { it.file("native-16kb-alignment.md") }
val nodeRuntimeArtifactManifestJson = nodeNativeModernizationReportDir.map { it.file("runtime-artifact-manifest.json") }
val nodeRuntimeArtifactManifestMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-artifact-manifest.md") }
val nodeRuntimeArtifactIntegrityJson = nodeNativeModernizationReportDir.map { it.file("runtime-artifact-integrity.json") }
val nodeRuntimeArtifactIntegrityMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-artifact-integrity.md") }
val nodeRuntimeDistributionPolicyJson = nodeNativeModernizationReportDir.map { it.file("runtime-distribution-policy.json") }
val nodeRuntimeDistributionPolicyMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-distribution-policy.md") }
val nodeRuntimeArtifactGateJson = nodeNativeModernizationReportDir.map { it.file("runtime-artifact-gate.json") }
val nodeRuntimeArtifactGateMarkdown = nodeNativeModernizationReportDir.map { it.file("runtime-artifact-gate.md") }
val nodePhase9PerformancePolicyJson = nodeNativeModernizationReportDir.map { it.file("phase9-performance-policy.json") }
val nodePhase9PerformancePolicyMarkdown = nodeNativeModernizationReportDir.map { it.file("phase9-performance-policy.md") }
val nodePhase9InspectorPolicyJson = nodeNativeModernizationReportDir.map { it.file("phase9-inspector-policy.json") }
val nodePhase9InspectorPolicyMarkdown = nodeNativeModernizationReportDir.map { it.file("phase9-inspector-policy.md") }
val nodePhase9IsolationPolicyJson = nodeNativeModernizationReportDir.map { it.file("phase9-isolation-policy.json") }
val nodePhase9IsolationPolicyMarkdown = nodeNativeModernizationReportDir.map { it.file("phase9-isolation-policy.md") }
val nodePhase9PluginPolicyJson = nodeNativeModernizationReportDir.map { it.file("phase9-plugin-policy.json") }
val nodePhase9PluginPolicyMarkdown = nodeNativeModernizationReportDir.map { it.file("phase9-plugin-policy.md") }
val nodePhase9WasmWasiPolicyJson = nodeNativeModernizationReportDir.map { it.file("phase9-wasm-wasi-policy.json") }
val nodePhase9WasmWasiPolicyMarkdown = nodeNativeModernizationReportDir.map { it.file("phase9-wasm-wasi-policy.md") }
val nodePhase9NativeCrashPipelineJson = nodeNativeModernizationReportDir.map { it.file("phase9-native-crash-pipeline.json") }
val nodePhase9NativeCrashPipelineMarkdown = nodeNativeModernizationReportDir.map { it.file("phase9-native-crash-pipeline.md") }

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

tasks.register<Exec>("verifyNodeRuntimeAdapterExport") {
    group = "verification"
    description = "Verifies the Phase 9 runtime adapter ABI export across built app debug native ABIs."

    dependsOn("externalNativeBuildAppDebug")
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-adapter-export.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.buildDirectory.dir("intermediates/cxx"))
    outputs.files(nodeRuntimeAdapterExportJson, nodeRuntimeAdapterExportMarkdown)
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

tasks.register<Exec>("verifyNode16KbNativeAlignment") {
    group = "verification"
    description = "Verifies Phase 9 16 KB ELF alignment for current Node runtime native artifacts."

    dependsOn("externalNativeBuildAppDebug")
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-artifacts.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.dir(project.layout.buildDirectory.dir("intermediates/cxx"))
    outputs.files(nodeNative16KbAlignmentJson, nodeNative16KbAlignmentMarkdown)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodeNativeModernizationReportDir.get().asFile.absolutePath,
        "--mode",
        "alignment",
    )
}

tasks.register<Exec>("generateNodeRuntimeArtifactManifest") {
    group = "verification"
    description = "Generates the Phase 9 runtime artifact manifest for current Node runtime native artifacts."

    dependsOn("externalNativeBuildAppDebug")
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-artifacts.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.dir(project.layout.buildDirectory.dir("intermediates/cxx"))
    outputs.files(nodeRuntimeArtifactManifestJson, nodeRuntimeArtifactManifestMarkdown)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodeNativeModernizationReportDir.get().asFile.absolutePath,
        "--mode",
        "manifest",
    )
}

tasks.register<Exec>("verifyNodeRuntimeArtifactIntegrity") {
    group = "verification"
    description = "Verifies Phase 9 runtime artifact integrity metadata for current Node runtime native artifacts."

    dependsOn("externalNativeBuildAppDebug")
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-artifacts.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.dir(project.layout.buildDirectory.dir("intermediates/cxx"))
    outputs.files(nodeRuntimeArtifactIntegrityJson, nodeRuntimeArtifactIntegrityMarkdown)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodeNativeModernizationReportDir.get().asFile.absolutePath,
        "--mode",
        "integrity",
    )
}

tasks.register<Exec>("verifyNodeRuntimeDistributionPolicy") {
    group = "verification"
    description = "Verifies Phase 9 runtime distribution policy for current and planned runtime modes."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-distribution-policy.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(nodeRuntimeDistributionPolicyJson, nodeRuntimeDistributionPolicyMarkdown)
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

tasks.register<Exec>("verifyNodeRuntimeArtifactGate") {
    group = "verification"
    description = "Runs the Phase 9 runtime artifact aggregate gate."

    dependsOn(
        "verifyNodeRuntimeAdapterExport",
        "verifyNodeRuntimeBuildPlan",
        "verifyNode16KbNativeAlignment",
        "generateNodeRuntimeArtifactManifest",
        "verifyNodeRuntimeArtifactIntegrity",
        "verifyNodeRuntimeDistributionPolicy",
    )
    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/native/generate-runtime-artifact-gate.js")
    inputs.file(scriptFile)
    inputs.files(
        nodeRuntimeAdapterExportJson,
        nodeRuntimeBuildPlanJson,
        nodeNative16KbAlignmentJson,
        nodeRuntimeArtifactManifestJson,
        nodeRuntimeArtifactIntegrityJson,
        nodeRuntimeDistributionPolicyJson,
    )
    outputs.files(nodeRuntimeArtifactGateJson, nodeRuntimeArtifactGateMarkdown)
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

tasks.register<Exec>("verifyNodePhase9PerformancePolicy") {
    group = "verification"
    description = "Verifies Phase 9 compile-cache, snapshot, prewarm, memory-pressure, and performance policy evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/performance/verify-phase9-performance-policy.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_lifecycle.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeModuleCompileCacheInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRuntimePrewarmPoolInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePerformanceMatrixInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/MODULE_COMPILE_CACHE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/RUNTIME_PREWARM_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/PERFORMANCE_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BOOTSTRAP_STARTUP_SNAPSHOT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/BOOTSTRAP_STARTUP_SNAPSHOT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/PERFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/runtime-build.lock.json"))
    outputs.files(nodePhase9PerformancePolicyJson, nodePhase9PerformancePolicyMarkdown)
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

tasks.register<Exec>("verifyNodePhase9InspectorPolicy") {
    group = "verification"
    description = "Verifies Phase 9 inspector, DevTools, profiling, diagnostic report, and inspector security policy evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/debug/verify-phase9-inspector-policy.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_lifecycle.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/INSPECTOR.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/INSPECTOR_DESIGN_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/DEVTOOLS_ADB_FORWARD_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/INSPECTOR_PROFILING_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/INSPECTOR_SECURITY_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    outputs.files(nodePhase9InspectorPolicyJson, nodePhase9InspectorPolicyMarkdown)
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

tasks.register<Exec>("verifyNodePhase9WasmWasiPolicy") {
    group = "verification"
    description = "Verifies Phase 9 WebAssembly loading, raw WASI denial, and future controlled WASI policy evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/wasm/verify-phase9-wasm-wasi-policy.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    outputs.files(nodePhase9WasmWasiPolicyJson, nodePhase9WasmWasiPolicyMarkdown)
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

tasks.register<Exec>("verifyNodePhase9IsolationPolicy") {
    group = "verification"
    description = "Verifies Phase 9 isolation, broker, process-pool, crash, resource-limit, and worker policy evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/isolation/verify-phase9-isolation-policy.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/AndroidManifest.xml"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeCapabilityBrokerPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHardenedIsolatedRuntimePocInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeCapabilityBrokerV2InstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/HARDENED_ISOLATED_RUNTIME_POC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/CAPABILITY_BROKER_V2.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/PROCESS_POOL_POLICY_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/HARDENED_ISOLATED_RUNTIME.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/CAPABILITY_BROKER_V2.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/ISOLATION_POLICY.md"))
    outputs.files(nodePhase9IsolationPolicyJson, nodePhase9IsolationPolicyMarkdown)
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

tasks.register<Exec>("verifyNodePhase9PluginPolicy") {
    group = "verification"
    description = "Verifies Phase 9 WASM plugin package, native service plugin, and plugin ABI lifecycle policy evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/plugin/verify-phase9-plugin-policy.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PLUGIN_SYSTEM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/PLUGIN_POLICY.md"))
    outputs.files(nodePhase9PluginPolicyJson, nodePhase9PluginPolicyMarkdown)
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

tasks.register<Exec>("verifyNodePhase9NativeCrashPipeline") {
    group = "verification"
    description = "Verifies Phase 9 native crash pipeline, symbolication, and crash evidence policy."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/stability/verify-phase9-native-crash-pipeline.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/NATIVE_CRASH_PIPELINE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CRASH_INTAKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MAINTENANCE_PLAN.md"))
    inputs.file(project.layout.projectDirectory.file("native-node-deps-report.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-artifacts.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/generate-runtime-artifact-gate.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-distribution-policy.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/ISOLATION_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/INSPECTOR_POLICY.md"))
    outputs.files(nodePhase9NativeCrashPipelineJson, nodePhase9NativeCrashPipelineMarkdown)
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

tasks.register("verifyNodePhase9NativeModernizationPlan") {
    group = "verification"
    description = "Runs Phase 9 native modernization planning checks without changing the default runtime."

    dependsOn(
        "generateNodeNativeSymbolUsageReport",
        "verifyNodeRuntimeAdapterAbi",
        "verifyNodeRuntimeAdapterExport",
        "verifyNodeRuntimeBuildPlan",
        "verifyNode16KbNativeAlignment",
        "generateNodeRuntimeArtifactManifest",
        "verifyNodeRuntimeArtifactIntegrity",
        "verifyNodeRuntimeDistributionPolicy",
        "verifyNodeRuntimeArtifactGate",
        "verifyNodePhase9PerformancePolicy",
        "verifyNodePhase9InspectorPolicy",
        "verifyNodePhase9IsolationPolicy",
        "verifyNodePhase9PluginPolicy",
        "verifyNodePhase9WasmWasiPolicy",
        "verifyNodePhase9NativeCrashPipeline",
    )
}
