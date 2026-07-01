val nodePhase14ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("verifyNodePhase14Baseline") {
    group = "verification"
    description = "Verifies the Phase 14 v1.2 baseline imported from the Phase 13 closeout."

    dependsOn("generateNodePhase13Summary")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-baseline.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("version.properties"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_CLOSEOUT_BREAKDOWN.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-summary.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-baseline-verification.json") },
        nodePhase14ReportDir.map { it.file("phase14-baseline-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase14ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase14SurfaceManifest") {
    group = "verification"
    description = "Verifies the Phase 14 v1.2 surface manifest."

    dependsOn("verifyNodePhase14Baseline")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-surface-manifest.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/phase14_surface_manifest.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-surface-manifest-verification.json") },
        nodePhase14ReportDir.map { it.file("phase14-surface-manifest-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase14ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase14PromotionContracts") {
    group = "verification"
    description = "Verifies the Phase 14 v1.2 shared promotion contracts."

    dependsOn("verifyNodePhase14SurfaceManifest")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-promotion-contracts.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-promotion-contracts.json") },
        nodePhase14ReportDir.map { it.file("phase14-promotion-contracts.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase14ReportDir.get().asFile.absolutePath,
    )
}
