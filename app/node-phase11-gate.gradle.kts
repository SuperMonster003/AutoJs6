val nodePhase11ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("generateNodeFinalCapabilityTruth") {
    group = "verification"
    description = "Writes build/reports/nodejs/final-capability-truth.json without failing on open Phase 11 blockers."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-capability-truth.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeJavaScriptEngineFactory.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-capability-truth.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalCapabilityTruth") {
    group = "verification"
    description = "Strictly verifies final capability truth; fails until required internal Phase 11 blockers are closed."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-capability-truth.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-capability-truth.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("verifyNodeFinalFocusedGateTopology") {
    group = "verification"
    description = "Verifies the Phase 11 final focused gate task topology is registered."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-focused-gate-topology.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodeFinalAdapterStatus") {
    group = "verification"
    description = "Writes the current Phase 11 adapter status without failing on the surface-only adapter blocker."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-adapter-gate.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalAdapterGate") {
    group = "verification"
    description = "Strictly verifies adapter_v1 readiness for final promotion; fails while the adapter remains surface-only."

    dependsOn("verifyNodeRuntimeAdapterAbi", "verifyNodeRuntimeAdapterExport")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-adapter-gate.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalCrashStatus") {
    group = "verification"
    description = "Writes the current Phase 11 crash evidence status without failing on unimplemented crash automation."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-crash-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CRASH_INTAKE.md"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.md") },
    )
    outputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalCrashGate") {
    group = "verification"
    description = "Strictly verifies automatic crash evidence and chaos injection readiness."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-crash-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CRASH_INTAKE.md"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.md") },
    )
    outputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register("verifyNodeFinalPackageManagerGate") {
    group = "verification"
    description = "Strict final package-manager gate; blocked until Android-side package installation is implemented."

    dependsOn("verifyNodePackageManagerGate", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalLongRunningGate") {
    group = "verification"
    description = "Strict final long-running gate; blocked until packaged long-running and full stress are implemented."

    dependsOn("verifyNodeLongRunningGate", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalMultiInstanceGate") {
    group = "verification"
    description = "Strict final multi-instance gate; blocked until a second execution slot is promoted."

    dependsOn("verifyNodeMultiInstanceGate", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalWorkerDecisionGate") {
    group = "verification"
    description = "Strict final Worker/WASI decision gate; blocked until conditional decisions are resolved."

    dependsOn("verifyNodeWorkerWasiGate", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalProviderGate") {
    group = "verification"
    description = "Strict final provider stability gate."

    dependsOn("verifyNodeLiveProviderGate", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalAbiGate") {
    group = "verification"
    description = "Strict final ABI gate for the required Node runtime ABIs."

    dependsOn("verifyNodeRuntimeArtifactGate", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalSecurityGate") {
    group = "verification"
    description = "Strict final security/fuzz gate."

    dependsOn("verifyNodePhase10SecurityCorpus", "verifyNodeFinalCapabilityTruth")
}

tasks.register("verifyNodeFinalSoakGate") {
    group = "verification"
    description = "Strict final soak gate; blocked until 24-hour soak evidence exists."

    dependsOn("verifyNodeFinalCapabilityTruth")
}

tasks.register<Exec>("generateNodeFinalIntegrationSummary") {
    group = "verification"
    description = "Writes the Phase 11 final integration summary without failing on open blockers."

    dependsOn(
        "generateNodeFinalCapabilityTruth",
        "verifyNodeFinalFocusedGateTopology",
        "generateNodeFinalAdapterStatus",
        "generateNodeFinalCrashStatus",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-integration-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-integration-summary.json") },
        nodePhase11ReportDir.map { it.file("final-integration-summary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalIntegrationGate") {
    group = "verification"
    description = "Strict final graduation gate; fails until Phase 11 required internal blockers are closed."

    dependsOn(
        "generateNodeFinalCapabilityTruth",
        "verifyNodeFinalFocusedGateTopology",
        "generateNodeFinalAdapterStatus",
        "generateNodeFinalCrashStatus",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-integration-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-integration-summary.json") },
        nodePhase11ReportDir.map { it.file("final-integration-summary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}
