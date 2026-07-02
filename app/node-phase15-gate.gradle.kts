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

tasks.register<Exec>("verifyNodePhase15SurfaceManifest") {
    group = "verification"
    description = "Verifies the Phase 15 Node v1.3 surface manifest."

    dependsOn("verifyNodePhase15Baseline")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-surface-manifest.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/phase15_surface_manifest.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-baseline-verification.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-surface-manifest-verification.json") },
        nodePhase15ReportDir.map { it.file("phase15-surface-manifest-verification.md") },
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

tasks.register<Exec>("verifyNodePhase15PromotionContracts") {
    group = "verification"
    description = "Verifies the Phase 15 Node v1.3 promotion contracts."

    dependsOn("verifyNodePhase15SurfaceManifest")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-promotion-contracts.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_NODE_V1_3_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-surface-manifest-verification.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") },
        nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.md") },
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

tasks.register<Exec>("verifyNodePhase15DisclosureRollbackAudit") {
    group = "verification"
    description = "Verifies the Phase 15 Node v1.3 disclosure and rollback audit."

    dependsOn("verifyNodePhase15PromotionContracts")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-disclosure-rollback-audit.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_NODE_V1_3_DISCLOSURE_ROLLBACK_AUDIT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") },
        nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.md") },
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

tasks.register<Exec>("verifyNodePhase15PackagedLiveUiPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 packaged live UI promotion evidence gate."

    dependsOn("verifyNodePhase15DisclosureRollbackAudit", "verifyNodePhase14LiveUiProviderPackaged")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-packaged-live-ui-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-live-ui-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_PACKAGED_LIVE_UI_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_04_PACKAGED_LIVE_UI_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-live-ui-provider-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-live-ui-provider-packaged.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-packaged-live-ui-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-packaged-live-ui-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15OverlayProviderPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 Android overlay provider promotion evidence gate."

    dependsOn("verifyNodePhase15PackagedLiveUiPromotion", "verifyNodePhase14OverlayProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-overlay-provider-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/overlay-provider-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_OVERLAY_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_05_OVERLAY_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui_overlay.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-overlay-provider-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-overlay-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-live-ui-promotion.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-overlay-provider-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-overlay-provider-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15OverlayOemMatrix") {
    group = "verification"
    description = "Verifies the Phase 15 Android overlay permission and OEM matrix promotion evidence gate."

    dependsOn("verifyNodePhase15OverlayProviderPromotion", "verifyNodePhase14OverlayPermissionOemMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-overlay-oem-matrix.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/overlay-oem-matrix-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_OVERLAY_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_06_OVERLAY_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/overlay-provider-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/overlay-permission-oem-matrix.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-overlay-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-overlay-permission-oem-matrix.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-overlay-provider-promotion.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-overlay-oem-matrix.json") },
        nodePhase15ReportDir.map { it.file("phase15-overlay-oem-matrix.md") },
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

tasks.register<Exec>("installAndRunNodeV13PackagedOverlaySmoke") {
    group = "verification"
    description = "Verifies the Phase 15 packaged overlay positive smoke promotion evidence gate."

    dependsOn("verifyNodePhase15OverlayOemMatrix", "verifyNodePhase14PackagedOverlayPositiveSmoke")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-packaged-overlay-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-overlay-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_PACKAGED_OVERLAY_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_07_PACKAGED_OVERLAY_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/overlay-provider-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_OVERLAY_POSITIVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_10_PACKAGED_OVERLAY_POSITIVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-packaged-overlay-positive-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-overlay-provider-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-overlay-oem-matrix.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-packaged-overlay-smoke.json") },
        nodePhase15ReportDir.map { it.file("phase15-packaged-overlay-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase15AccessibilityPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 accessibility lifecycle and selector/action promotion evidence gate."

    dependsOn("installAndRunNodeV13PackagedOverlaySmoke", "verifyNodePhase14AccessibilitySelectorActionMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-accessibility-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/accessibility-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_ACCESSIBILITY_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_08_ACCESSIBILITY_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/accessibility-lifecycle-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/accessibility-selector-action-matrix.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_12_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilityRealDeviceSmokeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySmokeActivity.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/shell.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-accessibility-selector-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-shell-root-shizuku-capability.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-accessibility-lifecycle.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-accessibility-selector-action-matrix.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-accessibility-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-accessibility-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15MediaProjectionPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 Activity-owned MediaProjection promotion evidence gate."

    dependsOn("verifyNodePhase15AccessibilityPromotion", "verifyNodePhase14MediaProjectionProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-media-projection-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/media-projection-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_MEDIA_PROJECTION_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_09_MEDIA_PROJECTION_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/media-projection-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_MEDIA_PROJECTION_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeScreenCaptureRealDeviceSmokeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media_projection.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/DEVICE_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-media-projection-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-accessibility-promotion.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-media-projection-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-media-projection-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15ImageOcrPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 advanced image and packaged OCR promotion evidence gate."

    dependsOn(
        "verifyNodePhase15MediaProjectionPromotion",
        "verifyNodePhase14AdvancedImageOperations",
        "verifyNodePhase14PackagedImageOcrEvidence",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-image-ocr-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/image-ocr-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_10_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/advanced-image-operations-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_15_PACKAGED_IMAGE_OCR_EVIDENCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ocr.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-advanced-image-operations.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-packaged-image-ocr-evidence.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-media-projection-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-image-ocr-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-image-ocr-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15UiAutomationMediaDocSync") {
    group = "verification"
    description = "Verifies the Phase 15 UI, automation, media docs, examples, declarations, and release sync gate."

    dependsOn("verifyNodePhase15ImageOcrPromotion", "verifyNodePhase14UiAutomationMediaDocSync")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-ui-automation-media-doc-sync.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/ui-automation-media-doc-sync.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_UI_AUTOMATION_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_11_UI_AUTOMATION_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_3_UI_AUTOMATION_MEDIA_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/examples.json"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui_overlay.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media_projection.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/images.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ocr.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mediainfo.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-ui-automation-media-doc-sync.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-live-ui-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-overlay-provider-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-overlay-oem-matrix.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-overlay-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-accessibility-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-media-projection-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-image-ocr-promotion.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-ui-automation-media-doc-sync.json") },
        nodePhase15ReportDir.map { it.file("phase15-ui-automation-media-doc-sync.md") },
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

tasks.register<Exec>("verifyNodePhase15TaskDatabasePromotion") {
    group = "verification"
    description = "Verifies the Phase 15 JSON-safe task database mutation promotion evidence gate."

    dependsOn("verifyNodePhase15UiAutomationMediaDocSync", "verifyNodePhase14TaskDatabaseProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-task-database-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/task-database-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_TASK_DATABASE_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_12_TASK_DATABASE_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-database-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-task-database-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-task-database-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-ui-automation-media-doc-sync.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-task-database-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-task-database-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15TaskSchemaRollback") {
    group = "verification"
    description = "Verifies the Phase 15 task schema migration and rollback evidence gate."

    dependsOn("verifyNodePhase15TaskDatabasePromotion", "verifyNodePhase14TaskSchemaMigrationRollback")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-task-schema-rollback.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/task-schema-rollback-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_TASK_SCHEMA_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_13_TASK_SCHEMA_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-schema-migration-rollback-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_SCHEMA_MIGRATION_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-task-database-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-task-schema-migration-rollback.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-task-database-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-task-schema-rollback.json") },
        nodePhase15ReportDir.map { it.file("phase15-task-schema-rollback.md") },
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

tasks.register<Exec>("installAndRunNodeV13PackagedTaskSmoke") {
    group = "verification"
    description = "Verifies the Phase 15 packaged task scheduling smoke evidence gate."

    dependsOn("verifyNodePhase15TaskSchemaRollback", "verifyNodePhase14PackagedTaskSchedulingSmoke")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-packaged-task-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-task-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_PACKAGED_TASK_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_14_PACKAGED_TASK_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_TASK_SCHEDULING_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_19_PACKAGED_TASK_SCHEDULING_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-task-database-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-task-schema-migration-rollback.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-packaged-task-scheduling-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-task-database-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-task-schema-rollback.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-packaged-task-smoke.json") },
        nodePhase15ReportDir.map { it.file("phase15-packaged-task-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase15IntentBroadcastPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 JSON-safe IntentTask and broadcast provider promotion evidence gate."

    dependsOn("installAndRunNodeV13PackagedTaskSmoke", "verifyNodePhase14IntentBroadcastProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-intent-broadcast-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/intent-broadcast-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_INTENT_BROADCAST_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_15_INTENT_BROADCAST_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-task-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/intent-broadcast-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_20_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BROADCAST_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeIntentTaskSnapshots.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/timing/IntentTask.java"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/external/receiver/BaseBroadcastReceiver.java"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/external/receiver/StaticBroadcastReceiver.java"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/external/receiver/DynamicBroadcastReceivers.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-intent-broadcast-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-intent-broadcast-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-task-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-intent-broadcast-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-intent-broadcast-promotion.md") },
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

tasks.register<Exec>("installAndRunNodeV13PackagedBroadcastSmoke") {
    group = "verification"
    description = "Verifies the Phase 15 packaged broadcast trigger/remove smoke evidence gate."

    dependsOn("verifyNodePhase15IntentBroadcastPromotion", "verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-packaged-broadcast-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-broadcast-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_PACKAGED_BROADCAST_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_16_PACKAGED_BROADCAST_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/intent-broadcast-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_21_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-intent-broadcast-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-packaged-broadcast-trigger-remove-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-intent-broadcast-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-packaged-broadcast-smoke.json") },
        nodePhase15ReportDir.map { it.file("phase15-packaged-broadcast-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase15NotificationPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 notification ownership and foreground disclosure promotion evidence gate."

    dependsOn("installAndRunNodeV13PackagedBroadcastSmoke", "verifyNodePhase14NotificationOwnership")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-notification-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/notification-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_NOTIFICATION_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_17_NOTIFICATION_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-broadcast-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/notification-ownership-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NOTIFICATION_OWNERSHIP.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_22_NOTIFICATION_OWNERSHIP.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/notifications.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-notifications-settings-power-manager.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-notification-ownership.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-broadcast-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-notification-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-notification-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15SettingsPowerPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 settings/power OEM and background-launch promotion evidence gate."

    dependsOn("verifyNodePhase15NotificationPromotion", "verifyNodePhase14SettingsPowerOemBackgroundMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-settings-power-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/settings-power-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_SETTINGS_POWER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_18_SETTINGS_POWER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/notification-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/settings-power-oem-background-matrix.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_23_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeDeviceBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/device.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-notifications-settings-power-manager.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-settings-power-oem-background-matrix.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-notification-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-settings-power-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-settings-power-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase15RecorderPromotion") {
    group = "verification"
    description = "Verifies the Phase 15 real recorder session promotion evidence gate."

    dependsOn("verifyNodePhase15SettingsPowerPromotion", "verifyNodePhase14RecorderProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-recorder-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/recorder-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_RECORDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_19_RECORDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/settings-power-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/recorder-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_RECORDER_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_24_RECORDER_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-recorder-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-settings-power-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-recorder-promotion.json") },
        nodePhase15ReportDir.map { it.file("phase15-recorder-promotion.md") },
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

tasks.register<Exec>("installAndRunNodeV13PackagedRecorderSmoke") {
    group = "verification"
    description = "Verifies the Phase 15 packaged recorder/media stress evidence gate."

    dependsOn("verifyNodePhase15RecorderPromotion", "verifyNodePhase14PackagedRecorderMediaStress")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-packaged-recorder-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-recorder-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_PACKAGED_RECORDER_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_20_PACKAGED_RECORDER_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/recorder-promotion-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-recorder-media-stress-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_RECORDER_MEDIA_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_25_PACKAGED_RECORDER_MEDIA_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-packaged-recorder-media-stress.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-recorder-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-packaged-recorder-smoke.json") },
        nodePhase15ReportDir.map { it.file("phase15-packaged-recorder-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase15MediaStorePlaybackDecision") {
    group = "verification"
    description = "Verifies the Phase 15 MediaStore and playback stable-denial boundary decision."

    dependsOn("installAndRunNodeV13PackagedRecorderSmoke", "verifyNodePhase14MediaPlaybackMediaStoreBoundary")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-mediastore-playback-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/mediastore-playback-decision-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_MEDIASTORE_PLAYBACK_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_21_MEDIASTORE_PLAYBACK_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/packaged-recorder-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_26_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mediainfo.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/profile_capabilities.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-media-playback-mediastore-boundary.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-recorder-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-promotion-contracts.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-disclosure-rollback-audit.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-mediastore-playback-decision.json") },
        nodePhase15ReportDir.map { it.file("phase15-mediastore-playback-decision.md") },
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

tasks.register<Exec>("verifyNodePhase15TaskDeviceMediaDocSync") {
    group = "verification"
    description = "Verifies the Phase 15 task, device, and media docs/examples/declarations sync gate."

    dependsOn("verifyNodePhase15MediaStorePlaybackDecision", "verifyNodePhase14TaskDeviceMediaDocSync")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase15/verify-node-phase15-task-device-media-doc-sync.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/task-device-media-doc-sync.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/PHASE15_TASK_DEVICE_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_3_TASK_DEVICE_MEDIA_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/P15_22_TASK_DEVICE_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase15/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase15/phase15-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/notifications.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/device.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mediainfo.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/profile_capabilities.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/examples.json"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/snippets/tasks-work-manager.cjs"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/snippets/notifications-power.cjs"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/snippets/media-recorder.cjs"))
    inputs.file(project.layout.projectDirectory.file("node-phase15-gate.gradle.kts"))
    inputs.file(nodePhase15ReportDir.map { it.file("phase14-task-device-media-doc-sync.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-task-database-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-task-schema-rollback.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-task-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-intent-broadcast-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-broadcast-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-notification-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-settings-power-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-recorder-promotion.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-packaged-recorder-smoke.json") })
    inputs.file(nodePhase15ReportDir.map { it.file("phase15-mediastore-playback-decision.json") })
    outputs.files(
        nodePhase15ReportDir.map { it.file("phase15-task-device-media-doc-sync.json") },
        nodePhase15ReportDir.map { it.file("phase15-task-device-media-doc-sync.md") },
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
