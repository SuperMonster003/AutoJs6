val nodePhase15ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("verifyNodePhase15Baseline") {
    group = "verification"
    description = "Verifies the Phase 15 Node v1.3 baseline imported from the Phase 14 closeout handoff."

    dependsOn("verifyNodePhase14CloseoutHandoff")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-baseline.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file(".git"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_CLOSEOUT_BREAKDOWN.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NEXT_BRANCH_HANDOFF.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-baseline.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_NODE_V1_3_BASELINE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-closeout-handoff.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-summary.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-baseline-verification.json") },
        nodePhase15ReportDir.map { it.file("phase15-baseline-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase15ReportDir.get().asFile.absolutePath,
    )
}
