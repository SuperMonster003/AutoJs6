val nodePhase13ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("verifyNodePhase13Baseline") {
    group = "verification"
    description = "Verifies the Phase 13 baseline inputs from Phase 12 and version.properties."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-node-phase13-baseline.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("version.properties"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/PHASE12_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_PLAN.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-baseline-verification.json") },
        nodePhase13ReportDir.map { it.file("phase13-baseline-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase13ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase13SurfaceManifest") {
    group = "verification"
    description = "Verifies the Phase 13 Pro/Desktop parity surface manifest."

    dependsOn("verifyNodePhase13Baseline")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-node-phase13-surface-manifest.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/phase13_surface_manifest.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_PLAN.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/CAPABILITY_RELAXATION_RUBRIC.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-surface-manifest-verification.json") },
        nodePhase13ReportDir.map { it.file("phase13-surface-manifest-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase13ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodePhase13CapabilityTruth") {
    group = "verification"
    description = "Generates the Phase 13 Safe/Pro/Desktop/Debug capability truth report."

    dependsOn("verifyNodePhase13SurfaceManifest")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-phase13-capability-truth.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-capability-truth.json") },
        nodePhase13ReportDir.map { it.file("phase13-capability-truth.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase13ReportDir.get().asFile.absolutePath,
    )
}
