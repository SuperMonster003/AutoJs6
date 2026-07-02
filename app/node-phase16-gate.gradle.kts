val nodePhase16ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("verifyNodePhase16Baseline") {
    group = "verification"
    description = "Verifies the Phase 16 Node v1.4 baseline imported from the Phase 15 summary handoff."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase16/verify-node-phase16-baseline.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file(".git"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NEXT_BRANCH_HANDOFF.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/node-v1-4-baseline.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/PHASE16_NODE_V1_4_BASELINE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/README.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase16-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    outputs.files(
        nodePhase16ReportDir.map { it.file("phase16-baseline-verification.json") },
        nodePhase16ReportDir.map { it.file("phase16-baseline-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase16ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase16SurfaceManifest") {
    group = "verification"
    description = "Verifies the Phase 16 Node v1.4 surface manifest."

    dependsOn("verifyNodePhase16Baseline")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase16/verify-node-phase16-surface-manifest.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/phase16-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/PHASE16_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/phase16_surface_manifest.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase16-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(nodePhase16ReportDir.map { it.file("phase16-baseline-verification.json") })
    outputs.files(
        nodePhase16ReportDir.map { it.file("phase16-surface-manifest-verification.json") },
        nodePhase16ReportDir.map { it.file("phase16-surface-manifest-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase16ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase16PromotionContracts") {
    group = "verification"
    description = "Verifies the Phase 16 Node v1.4 opt-in promotion contracts."

    dependsOn("verifyNodePhase16SurfaceManifest")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase16/verify-node-phase16-promotion-contracts.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/node-v1-4-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/PHASE16_NODE_V1_4_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/phase16-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/P16_02_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase16-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(nodePhase16ReportDir.map { it.file("phase16-surface-manifest-verification.json") })
    outputs.files(
        nodePhase16ReportDir.map { it.file("phase16-promotion-contracts.json") },
        nodePhase16ReportDir.map { it.file("phase16-promotion-contracts.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase16ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase16DisclosureRollbackAudit") {
    group = "verification"
    description = "Verifies the Phase 16 Node v1.4 disclosure and rollback audit."

    dependsOn("verifyNodePhase16PromotionContracts")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase16/verify-node-phase16-disclosure-rollback-audit.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/PHASE16_NODE_V1_4_DISCLOSURE_ROLLBACK_AUDIT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/node-v1-4-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/phase16-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/P16_03_DISCLOSURE_ROLLBACK_AUDIT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase16-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(nodePhase16ReportDir.map { it.file("phase16-promotion-contracts.json") })
    outputs.files(
        nodePhase16ReportDir.map { it.file("phase16-disclosure-rollback-audit.json") },
        nodePhase16ReportDir.map { it.file("phase16-disclosure-rollback-audit.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase16ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase16PackagedLiveUiPromotion") {
    group = "verification"
    description = "Verifies the Phase 16 Node v1.4 packaged live UI promotion partial gate."

    dependsOn("verifyNodePhase16DisclosureRollbackAudit")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase16/verify-node-phase16-packaged-live-ui-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/packaged-live-ui-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/PHASE16_PACKAGED_LIVE_UI_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/P16_04_PACKAGED_LIVE_UI_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/phase16-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/node-v1-4-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-live-ui-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_PACKAGED_LIVE_UI_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_04_PACKAGED_LIVE_UI_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase16/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase16-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(nodePhase16ReportDir.map { it.file("phase16-promotion-contracts.json") })
    inputs.file(nodePhase16ReportDir.map { it.file("phase16-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase16ReportDir.map { it.file("phase16-packaged-live-ui-promotion.json") },
        nodePhase16ReportDir.map { it.file("phase16-packaged-live-ui-promotion.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase16ReportDir.get().asFile.absolutePath,
    )
}
