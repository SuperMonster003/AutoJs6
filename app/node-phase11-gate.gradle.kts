val nodePhase11ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("generateNodeRhinoAugmentInventory") {
    group = "verification"
    description = "Generates the Phase 11A Rhino augment API inventory from ScriptRuntime augment registrations."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-augment-api-inventory.json") },
        nodePhase11ReportDir.map { it.file("rhino-augment-api-inventory.md") },
        nodePhase11ReportDir.map { it.file("rhino-augment-source-scan.json") },
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
        "--require-status-manifest",
        "--fail-on-unresolved",
    )
}

tasks.register<Exec>("generateNodeRhinoNodeCompatGap") {
    group = "verification"
    description = "Generates the Phase 11A Rhino augment to Node bridge/types/docs compatibility gap report."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-node-compat-gap.js")
    val inventoryGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(inventoryGeneratorFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/types/autojs6-node"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-node-compat-gap.json") },
        nodePhase11ReportDir.map { it.file("rhino-node-compat-gap.md") },
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
        "--require-status-manifest",
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatInventory") {
    group = "verification"
    description = "Verifies Phase 11A Rhino augment inventory, status manifest coverage, and Node bridge/types/docs evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-inventory.js")
    val statusManifestFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/rhino-compat-status-manifest.js")
    val inventoryGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val gapGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-node-compat-gap.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(statusManifestFile)
    inputs.file(inventoryGeneratorFile)
    inputs.file(gapGeneratorFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/types/autojs6-node"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-inventory-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-inventory-verification.md") },
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
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
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
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
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

tasks.register<Exec>("collectNodeCrashBundle") {
    group = "verification"
    description = "Collects the Phase 11 Node crash evidence bundle schema and available native artifacts."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-bundle-collection.json") },
        nodePhase11ReportDir.map { it.file("final-crash-bundle-collection.md") },
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

tasks.register<Exec>("symbolizeNodeCrash") {
    group = "verification"
    description = "Parses and best-effort symbolizes Phase 11 Node crash evidence."

    dependsOn("collectNodeCrashBundle")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js")
    inputs.file(scriptFile)
    inputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-symbolization.json") },
        nodePhase11ReportDir.map { it.file("final-crash-symbolization.md") },
    )
    outputs.file(nodePhase11ReportDir.map { it.file("final-crash-evidence-bundle/symbolization.json") })
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

tasks.register<Exec>("verifyNodeCrashBundle") {
    group = "verification"
    description = "Verifies the Phase 11 Node crash evidence bundle schema."

    dependsOn("symbolizeNodeCrash")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js")
    inputs.file(scriptFile)
    inputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-bundle-verification.json") },
        nodePhase11ReportDir.map { it.file("final-crash-bundle-verification.md") },
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

tasks.register<Exec>("generateNodeFinalCrashStatus") {
    group = "verification"
    description = "Writes the current Phase 11 crash evidence status without failing on unimplemented crash automation."

    dependsOn("verifyNodeCrashBundle")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-crash-gate.js")
    inputs.file(scriptFile)
    inputs.files(
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js"),
    )
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
    inputs.files(
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js"),
    )
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

tasks.register<Exec>("generateNodeFinalPackageManagerStatus") {
    group = "verification"
    description = "Writes the current Phase 11 package-manager status without failing on Android install blockers."

    dependsOn("verifyNodePackageManagerGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-package-manager-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-package-manager-boundary.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalPackageManagerGate") {
    group = "verification"
    description = "Strict final package-manager gate; blocked until Android-side package installation is implemented."

    dependsOn("verifyNodePackageManagerGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-package-manager-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-package-manager-boundary.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.md") },
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

tasks.register<Exec>("generateNodeFinalLongRunningStatus") {
    group = "verification"
    description = "Writes the current Phase 11 long-running status without failing on packaged/stress blockers."

    dependsOn("verifyNodePhase10LongRunningActivation", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-long-running-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedLifecycleStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/LONG_RUNNING_ACTIVATION.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-long-running-activation.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalLongRunningGate") {
    group = "verification"
    description = "Strict final long-running gate; blocked until packaged long-running and full stress are implemented."

    dependsOn("verifyNodePhase10LongRunningActivation", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-long-running-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedLifecycleStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/LONG_RUNNING_ACTIVATION.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-long-running-activation.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.md") },
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

tasks.register<Exec>("generateNodeFinalMultiInstanceStatus") {
    group = "verification"
    description = "Writes the current Phase 11 multi-instance status without failing on open promotion blockers."

    dependsOn("verifyNodeMultiInstanceGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-multi-instance-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalMultiInstanceGate") {
    group = "verification"
    description = "Strict final multi-instance gate; blocked until a second execution slot is promoted."

    dependsOn("verifyNodeMultiInstanceGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-multi-instance-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.md") },
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

tasks.register<Exec>("generateNodeFinalWorkerDecisionStatus") {
    group = "verification"
    description = "Writes the current Phase 11 Worker/WASI decision status without failing on unresolved conditional decisions."

    dependsOn("verifyNodePhase10WorkerWasiBoundary", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-worker-decision-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-worker-wasi-boundary.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalWorkerDecisionGate") {
    group = "verification"
    description = "Strict final Worker/WASI decision gate; blocked until conditional decisions are resolved."

    dependsOn("verifyNodePhase10WorkerWasiBoundary", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-worker-decision-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-worker-wasi-boundary.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.md") },
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

tasks.register<Exec>("generateNodeFinalProviderStatus") {
    group = "verification"
    description = "Writes the current Phase 11 provider status without failing on missing live-provider evidence."

    dependsOn("verifyNodePhase10ProviderRegistryV2", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-provider-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-provider-registry-v2.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-provider-registry-v2.json") },
        nodePhase11ReportDir.map { it.file("live-provider-summary.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalProviderGate") {
    group = "verification"
    description = "Strict final provider stability gate."

    dependsOn("verifyNodePhase10ProviderRegistryV2", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-provider-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-provider-registry-v2.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-provider-registry-v2.json") },
        nodePhase11ReportDir.map { it.file("live-provider-summary.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.md") },
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

tasks.register<Exec>("generateNodeFinalAbiStatus") {
    group = "verification"
    description = "Writes the current Phase 11 ABI status without failing on bootstrap-only runtime artifact evidence."

    dependsOn("verifyNodeRuntimeAdapterAbi", "verifyNodeRuntimeArtifactGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-abi-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(project.layout.projectDirectory.file("node-native-modernization.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("runtime-adapter-abi-v1.json") },
        nodePhase11ReportDir.map { it.file("runtime-adapter-export.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-gate.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-manifest.json") },
        nodePhase11ReportDir.map { it.file("runtime-distribution-policy.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalAbiGate") {
    group = "verification"
    description = "Strict final ABI gate for the required Node runtime ABIs."

    dependsOn("verifyNodeRuntimeAdapterAbi", "verifyNodeRuntimeArtifactGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-abi-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(project.layout.projectDirectory.file("node-native-modernization.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("runtime-adapter-abi-v1.json") },
        nodePhase11ReportDir.map { it.file("runtime-adapter-export.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-gate.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-manifest.json") },
        nodePhase11ReportDir.map { it.file("runtime-distribution-policy.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.md") },
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

tasks.register<Exec>("generateNodeFinalSecurityStatus") {
    group = "verification"
    description = "Writes the current Phase 11 security/fuzz status without failing on policy-blocked final surfaces."

    dependsOn("verifyNodePhase10SecurityCorpus", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-security-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-security-corpus.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-security-corpus.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalSecurityGate") {
    group = "verification"
    description = "Strict final security/fuzz gate."

    dependsOn("verifyNodePhase10SecurityCorpus", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-security-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-security-corpus.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-security-corpus.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.md") },
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

tasks.register<Exec>("generateNodeFinalSoakStatus") {
    group = "verification"
    description = "Writes the current Phase 11 soak status without failing on missing long-duration soak evidence."

    dependsOn("generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-soak-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/PERFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/quick-soak-8h.json"),
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/final-soak-24h.json"),
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.md") },
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

tasks.register<Exec>("verifyNodeFinalSoakGate") {
    group = "verification"
    description = "Strict final soak gate; blocked until 24-hour soak evidence exists."

    dependsOn("generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-soak-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/PERFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/quick-soak-8h.json"),
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/final-soak-24h.json"),
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.md") },
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

tasks.register<Exec>("generateNodeFinalIntegrationSummary") {
    group = "verification"
    description = "Writes the Phase 11 final integration summary without failing on open blockers."

    dependsOn(
        "generateNodeFinalCapabilityTruth",
        "verifyNodeFinalFocusedGateTopology",
        "generateNodeFinalAdapterStatus",
        "generateNodeFinalCrashStatus",
        "generateNodeFinalPackageManagerStatus",
        "generateNodeFinalLongRunningStatus",
        "generateNodeFinalMultiInstanceStatus",
        "generateNodeFinalWorkerDecisionStatus",
        "generateNodeFinalSoakStatus",
        "generateNodeFinalProviderStatus",
        "generateNodeFinalAbiStatus",
        "generateNodeFinalSecurityStatus",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-integration-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
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
        "generateNodeFinalPackageManagerStatus",
        "generateNodeFinalLongRunningStatus",
        "generateNodeFinalMultiInstanceStatus",
        "generateNodeFinalWorkerDecisionStatus",
        "generateNodeFinalSoakStatus",
        "generateNodeFinalProviderStatus",
        "generateNodeFinalAbiStatus",
        "generateNodeFinalSecurityStatus",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-integration-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
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
