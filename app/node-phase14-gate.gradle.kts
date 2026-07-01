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

tasks.register<Exec>("verifyNodePhase14LiveUiProviderPackaged") {
    group = "verification"
    description = "Verifies the Phase 14 packaged live UI provider POC gate."

    dependsOn("verifyNodePhase14PromotionContracts", "verifyNodePhase13LiveUiProviderPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-live-ui-packaged.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-live-ui-provider-promotion.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-live-ui-provider-promotion.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-live-ui-provider-packaged.json") },
        nodePhase14ReportDir.map { it.file("phase14-live-ui-provider-packaged.md") },
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

tasks.register<Exec>("verifyNodePhase14OverlayProvider") {
    group = "verification"
    description = "Verifies the Phase 14 Android overlay provider POC gate."

    dependsOn("verifyNodePhase14LiveUiProviderPackaged", "verifyNodePhase13OverlayProviderPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-overlay-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-overlay-provider-promotion.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui_overlay.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-overlay-provider-promotion.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-overlay-provider.json") },
        nodePhase14ReportDir.map { it.file("phase14-overlay-provider.md") },
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
