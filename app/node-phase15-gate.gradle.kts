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
