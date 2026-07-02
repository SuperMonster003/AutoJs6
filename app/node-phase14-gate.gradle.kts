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

tasks.register<Exec>("verifyNodePhase14OverlayPermissionOemMatrix") {
    group = "verification"
    description = "Verifies the Phase 14 overlay permission and OEM evidence matrix gate."

    dependsOn("verifyNodePhase14OverlayProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-overlay-permission-oem-matrix.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/overlay-permission-oem-matrix.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_OVERLAY_PERMISSION_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-overlay-provider-promotion.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-overlay-provider.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-overlay-permission-oem-matrix.json") },
        nodePhase14ReportDir.map { it.file("phase14-overlay-permission-oem-matrix.md") },
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

tasks.register<Exec>("verifyNodePhase14PackagedOverlayPositiveSmoke") {
    group = "verification"
    description = "Verifies the Phase 14 packaged overlay positive smoke fixture gate."

    dependsOn("verifyNodePhase14OverlayPermissionOemMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-packaged-overlay-positive-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_10_PACKAGED_OVERLAY_POSITIVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_OVERLAY_POSITIVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_08_OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_09_OVERLAY_PERMISSION_OEM_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/overlay-permission-oem-matrix.json"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-overlay-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-overlay-permission-oem-matrix.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-packaged-overlay-positive-smoke.json") },
        nodePhase14ReportDir.map { it.file("phase14-packaged-overlay-positive-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase14AccessibilityLifecycle") {
    group = "verification"
    description = "Verifies the Phase 14 packaged accessibility lifecycle fixture gate."

    dependsOn("verifyNodePhase14PackagedOverlayPositiveSmoke", "verifyNodePhase13AccessibilitySelectorPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-accessibility-lifecycle.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/accessibility-lifecycle-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_ACCESSIBILITY_LIFECYCLE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-accessibility-selector-promotion.js"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-accessibility-selector-promotion.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-overlay-positive-smoke.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-accessibility-lifecycle.json") },
        nodePhase14ReportDir.map { it.file("phase14-accessibility-lifecycle.md") },
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

tasks.register<Exec>("verifyNodePhase14AccessibilitySelectorActionMatrix") {
    group = "verification"
    description = "Verifies the Phase 14 accessibility selector/action real-device matrix gate."

    dependsOn(
        "verifyNodePhase14AccessibilityLifecycle",
        "verifyNodePhase13AccessibilitySelectorPromotion",
        "verifyNodePhase13ShellRootShizukuCapability",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-accessibility-selector-action-matrix.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_12_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/accessibility-selector-action-matrix.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-accessibility-selector-promotion.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-accessibility-lifecycle.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilityRealDeviceSmokeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySmokeActivity.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/shell.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/DEVICE_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-accessibility-selector-promotion.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-shell-root-shizuku-capability.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-accessibility-lifecycle.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-accessibility-selector-action-matrix.json") },
        nodePhase14ReportDir.map { it.file("phase14-accessibility-selector-action-matrix.md") },
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

tasks.register<Exec>("verifyNodePhase14MediaProjectionProvider") {
    group = "verification"
    description = "Verifies the Phase 14 Activity-owned MediaProjection provider contract gate."

    dependsOn(
        "verifyNodePhase14AccessibilitySelectorActionMatrix",
        "verifyNodePhase13ScreenshotImageOcrPromotion",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-media-projection-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/media-projection-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_MEDIA_PROJECTION_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeScreenCaptureRealDeviceSmokeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media_projection.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/DEVICE_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-accessibility-selector-action-matrix.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-media-projection-provider.json") },
        nodePhase14ReportDir.map { it.file("phase14-media-projection-provider.md") },
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

tasks.register<Exec>("verifyNodePhase14AdvancedImageOperations") {
    group = "verification"
    description = "Verifies the Phase 14 advanced image operations contract gate."

    dependsOn(
        "verifyNodePhase14MediaProjectionProvider",
        "verifyNodePhase13ScreenshotImageOcrPromotion",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-advanced-image-operations.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/advanced-image-operations-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_ADVANCED_IMAGE_OPERATIONS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-media-projection-provider.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media_projection.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-media-projection-provider.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-advanced-image-operations.json") },
        nodePhase14ReportDir.map { it.file("phase14-advanced-image-operations.md") },
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

tasks.register<Exec>("verifyNodePhase14PackagedImageOcrEvidence") {
    group = "verification"
    description = "Verifies the Phase 14 packaged image/OCR quota and model evidence gate."

    dependsOn(
        "verifyNodePhase14AdvancedImageOperations",
        "verifyNodePhase13ScreenshotImageOcrPromotion",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-packaged-image-ocr-evidence.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_15_PACKAGED_IMAGE_OCR_EVIDENCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_IMAGE_OCR_EVIDENCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ocr.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-advanced-image-operations.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-packaged-image-ocr-evidence.json") },
        nodePhase14ReportDir.map { it.file("phase14-packaged-image-ocr-evidence.md") },
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

tasks.register<Exec>("verifyNodePhase14UiAutomationMediaDocSync") {
    group = "verification"
    description = "Verifies the Phase 14 UI/automation/media docs, examples, declarations, release matrix, and error guidance sync gate."

    dependsOn(
        "verifyNodePhase14PackagedImageOcrEvidence",
        "verifyNodePhase13ProParityExamples",
        "verifyNodePhase13TypeDeclarationProfiles",
        "verifyNodePhase13MigrationPro9Comparison",
        "verifyAutoJs6NodeTypeDeclarations",
        "verifyNodeExamples",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-ui-automation-media-doc-sync.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_16_UI_AUTOMATION_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/ui-automation-media-doc-sync.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_UI_AUTOMATION_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_UI_AUTOMATION_MEDIA_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
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
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-live-ui-provider-packaged.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-overlay-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-overlay-permission-oem-matrix.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-overlay-positive-smoke.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-accessibility-lifecycle.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-accessibility-selector-action-matrix.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-media-projection-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-advanced-image-operations.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-image-ocr-evidence.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-ui-automation-media-doc-sync.json") },
        nodePhase14ReportDir.map { it.file("phase14-ui-automation-media-doc-sync.md") },
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

tasks.register<Exec>("verifyNodePhase14TaskDatabaseProvider") {
    group = "verification"
    description = "Verifies the Phase 14 JSON-safe task database mutation provider contract gate."

    dependsOn(
        "verifyNodePhase14UiAutomationMediaDocSync",
        "verifyNodePhase13TaskDatabaseProvider",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-task-database-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-database-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-task-database-provider.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-task-database-provider.json") },
        nodePhase14ReportDir.map { it.file("phase14-task-database-provider.md") },
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

tasks.register<Exec>("verifyNodePhase14TaskSchemaMigrationRollback") {
    group = "verification"
    description = "Verifies the Phase 14 task schema migration and rollback evidence contract gate."

    dependsOn("verifyNodePhase14TaskDatabaseProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-task-schema-migration-rollback.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-schema-migration-rollback-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_SCHEMA_MIGRATION_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-database-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeTimedTaskSnapshots.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-database-provider.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-task-schema-migration-rollback.json") },
        nodePhase14ReportDir.map { it.file("phase14-task-schema-migration-rollback.md") },
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

tasks.register<Exec>("verifyNodePhase14PackagedTaskSchedulingSmoke") {
    group = "verification"
    description = "Verifies the Phase 14 packaged task scheduling smoke evidence contract gate."

    dependsOn(
        "verifyNodePhase14TaskSchemaMigrationRollback",
        "verifyNodePhase13PackagedCapabilityMetadataDecision",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-packaged-task-scheduling-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_19_PACKAGED_TASK_SCHEDULING_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_TASK_SCHEDULING_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-database-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-schema-migration-rollback-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-database-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-schema-migration-rollback.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-packaged-task-scheduling-smoke.json") },
        nodePhase14ReportDir.map { it.file("phase14-packaged-task-scheduling-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase14IntentBroadcastProvider") {
    group = "verification"
    description = "Verifies the Phase 14 JSON-safe IntentTask/broadcast provider contract gate."

    dependsOn(
        "verifyNodePhase14PackagedTaskSchedulingSmoke",
        "verifyNodePhase13IntentBroadcastProvider",
        "verifyNodePhase13AppIntentBroadcastParity",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-intent-broadcast-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_20_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/intent-broadcast-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-database-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-schema-migration-rollback-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-task-scheduling-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_17_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_18_TASK_SCHEMA_MIGRATION_ROLLBACK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_19_PACKAGED_TASK_SCHEDULING_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BROADCAST_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeIntentTaskSnapshots.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/timing/IntentTask.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/external/receiver/BaseBroadcastReceiver.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/external/receiver/StaticBroadcastReceiver.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/external/receiver/DynamicBroadcastReceivers.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-intent-broadcast-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-database-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-schema-migration-rollback.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-task-scheduling-smoke.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-intent-broadcast-provider.json") },
        nodePhase14ReportDir.map { it.file("phase14-intent-broadcast-provider.md") },
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

tasks.register<Exec>("verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke") {
    group = "verification"
    description = "Verifies the Phase 14 packaged broadcast trigger/remove smoke contract gate."

    dependsOn(
        "verifyNodePhase14IntentBroadcastProvider",
        "verifyNodePhase13PackagedCapabilityMetadataDecision",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-packaged-broadcast-trigger-remove-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_21_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/intent-broadcast-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_20_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-intent-broadcast-provider.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-packaged-broadcast-trigger-remove-smoke.json") },
        nodePhase14ReportDir.map { it.file("phase14-packaged-broadcast-trigger-remove-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase14NotificationOwnership") {
    group = "verification"
    description = "Verifies the Phase 14 notification ownership and foreground disclosure evidence gate."

    dependsOn(
        "verifyNodePhase14PackagedBroadcastTriggerRemoveSmoke",
        "verifyNodePhase13NotificationsSettingsPowerManager",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-notification-ownership.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_22_NOTIFICATION_OWNERSHIP.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/notification-ownership-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NOTIFICATION_OWNERSHIP.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/notifications.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-notifications-settings-power-manager.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-notification-ownership.json") },
        nodePhase14ReportDir.map { it.file("phase14-notification-ownership.md") },
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

tasks.register<Exec>("verifyNodePhase14SettingsPowerOemBackgroundMatrix") {
    group = "verification"
    description = "Verifies the Phase 14 settings/power OEM and background-launch matrix gate."

    dependsOn(
        "verifyNodePhase14NotificationOwnership",
        "verifyNodePhase13NotificationsSettingsPowerManager",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-settings-power-oem-background-matrix.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_23_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/settings-power-oem-background-matrix.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeDeviceBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/device.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-notifications-settings-power-manager.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-notification-ownership.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-settings-power-oem-background-matrix.json") },
        nodePhase14ReportDir.map { it.file("phase14-settings-power-oem-background-matrix.md") },
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

tasks.register<Exec>("verifyNodePhase14RecorderProvider") {
    group = "verification"
    description = "Verifies the Phase 14 real recorder session promotion gate."

    dependsOn(
        "verifyNodePhase14SettingsPowerOemBackgroundMatrix",
        "verifyNodePhase13MediaRecorderMediainfoProvider",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-recorder-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_24_RECORDER_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/recorder-provider-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_RECORDER_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-settings-power-oem-background-matrix.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-recorder-provider.json") },
        nodePhase14ReportDir.map { it.file("phase14-recorder-provider.md") },
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

tasks.register<Exec>("verifyNodePhase14PackagedRecorderMediaStress") {
    group = "verification"
    description = "Verifies the Phase 14 packaged recorder/media stress evidence gate."

    dependsOn(
        "verifyNodePhase14RecorderProvider",
        "verifyNodePhase13MediaRecorderMediainfoProvider",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-packaged-recorder-media-stress.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_25_PACKAGED_RECORDER_MEDIA_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/packaged-recorder-media-stress-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PACKAGED_RECORDER_MEDIA_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-recorder-provider.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-packaged-recorder-media-stress.json") },
        nodePhase14ReportDir.map { it.file("phase14-packaged-recorder-media-stress.md") },
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

tasks.register<Exec>("verifyNodePhase14MediaPlaybackMediaStoreBoundary") {
    group = "verification"
    description = "Verifies the Phase 14 media playback and MediaStore boundary decision gate."

    dependsOn(
        "verifyNodePhase14PackagedRecorderMediaStress",
        "verifyNodePhase13MediaRecorderMediainfoProvider",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-media-playback-mediastore-boundary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_26_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mediainfo.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-recorder-media-stress.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-media-playback-mediastore-boundary.json") },
        nodePhase14ReportDir.map { it.file("phase14-media-playback-mediastore-boundary.md") },
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

tasks.register<Exec>("verifyNodePhase14TaskDeviceMediaDocSync") {
    group = "verification"
    description = "Verifies the Phase 14 task/device/media docs, examples, declarations, and release matrix sync gate."

    dependsOn("verifyNodePhase14MediaPlaybackMediaStoreBoundary")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-task-device-media-doc-sync.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_27_TASK_DEVICE_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/task-device-media-doc-sync.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_DEVICE_MEDIA_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_TASK_DEVICE_MEDIA_MIGRATION_COOKBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_TASK_DEVICE_MEDIA_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
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
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/notifications.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/device.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mediainfo.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/snippets/tasks-work-manager.cjs"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/snippets/notifications-power.cjs"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/snippets/media-recorder.cjs"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-database-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-task-schema-migration-rollback.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-task-scheduling-smoke.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-intent-broadcast-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-broadcast-trigger-remove-smoke.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-notification-ownership.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-settings-power-oem-background-matrix.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-recorder-provider.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-packaged-recorder-media-stress.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-media-playback-mediastore-boundary.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-task-device-media-doc-sync.json") },
        nodePhase14ReportDir.map { it.file("phase14-task-device-media-doc-sync.md") },
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

tasks.register<Exec>("verifyNodePhase14WorkerNativeAvailability") {
    group = "verification"
    description = "Verifies the Phase 14 worker_threads native availability audit and v1.2 intentional non-support decision."

    dependsOn(
        "verifyNodePhase14TaskDeviceMediaDocSync",
        "verifyNodePhase13WorkerThreadsOptInPromotion",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-worker-native-availability.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_28_WORKER_NATIVE_AVAILABILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/worker-native-availability-audit.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_WORKER_NATIVE_AVAILABILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/V1_1_WORKER_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/CMakeLists.txt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_core.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.files(listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
    ).map { abi ->
        project.layout.projectDirectory.file("src/main/jniLibs/$abi/libnode.so")
    })
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-worker-threads-opt-in-promotion.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-worker-native-availability.json") },
        nodePhase14ReportDir.map { it.file("phase14-worker-native-availability.md") },
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

tasks.register<Exec>("verifyNodePhase14WorkerRunnablePackagedSmoke") {
    group = "verification"
    description = "Verifies the Phase 14 conditional worker runnable and packaged smoke gate."

    dependsOn("verifyNodePhase14WorkerNativeAvailability")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-worker-runnable-packaged-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_29_WORKER_RUNNABLE_PACKAGED_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/worker-runnable-packaged-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_WORKER_RUNNABLE_PACKAGED_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-native-availability.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-worker-runnable-packaged-smoke.json") },
        nodePhase14ReportDir.map { it.file("phase14-worker-runnable-packaged-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase14WorkerResourceRssStress") {
    group = "verification"
    description = "Verifies the Phase 14 conditional worker resource and RSS stress gate."

    dependsOn("verifyNodePhase14WorkerRunnablePackagedSmoke")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-worker-resource-rss-stress.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_30_WORKER_RESOURCE_RSS_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/worker-resource-rss-stress-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_WORKER_RESOURCE_RSS_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-native-availability.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-runnable-packaged-smoke.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-worker-resource-rss-stress.json") },
        nodePhase14ReportDir.map { it.file("phase14-worker-resource-rss-stress.md") },
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

tasks.register<Exec>("verifyNodePhase14ProcessWorkerReplacementDecision") {
    group = "verification"
    description = "Verifies the Phase 14 process-worker replacement decision and no-facade v1.2 boundary."

    dependsOn(
        "verifyNodePhase14WorkerResourceRssStress",
        "verifyNodePhase13ProcessWorkerReplacementDecision",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-process-worker-replacement-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_31_PROCESS_WORKER_REPLACEMENT_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/process-worker-replacement-decision.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_PROCESS_WORKER_REPLACEMENT_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-process-worker-replacement-decision.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-native-availability.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-runnable-packaged-smoke.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-resource-rss-stress.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-process-worker-replacement-decision.json") },
        nodePhase14ReportDir.map { it.file("phase14-process-worker-replacement-decision.md") },
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

tasks.register<Exec>("verifyNodePhase14DesktopRuntimeDocSync") {
    group = "verification"
    description = "Verifies the Phase 14 desktop runtime docs and v1.2 release-policy sync gate."

    dependsOn("verifyNodePhase14ProcessWorkerReplacementDecision")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-desktop-runtime-doc-sync.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_32_DESKTOP_RUNTIME_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/desktop-runtime-doc-sync.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_DESKTOP_RUNTIME_DOC_SYNC.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_DESKTOP_RUNTIME_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_DESKTOP_RUNTIME_MIGRATION_COOKBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-native-availability.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-runnable-packaged-smoke.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-worker-resource-rss-stress.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-process-worker-replacement-decision.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-desktop-runtime-doc-sync.json") },
        nodePhase14ReportDir.map { it.file("phase14-desktop-runtime-doc-sync.md") },
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

tasks.register<Exec>("verifyNodePhase14SecurityRegressionCorpus") {
    group = "verification"
    description = "Verifies the Phase 14 v1.2 security regression corpus expansion gate."

    dependsOn("verifyNodePhase14DesktopRuntimeDocSync")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-security-regression-corpus.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_33_SECURITY_REGRESSION_CORPUS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/security-regression-corpus-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SECURITY_REGRESSION_CORPUS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-promotion-contracts.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    listOf(
        "phase14-live-ui-provider-packaged.json",
        "phase14-overlay-provider.json",
        "phase14-overlay-permission-oem-matrix.json",
        "phase14-packaged-overlay-positive-smoke.json",
        "phase14-accessibility-lifecycle.json",
        "phase14-accessibility-selector-action-matrix.json",
        "phase14-media-projection-provider.json",
        "phase14-advanced-image-operations.json",
        "phase14-packaged-image-ocr-evidence.json",
        "phase14-ui-automation-media-doc-sync.json",
        "phase14-task-database-provider.json",
        "phase14-task-schema-migration-rollback.json",
        "phase14-packaged-task-scheduling-smoke.json",
        "phase14-intent-broadcast-provider.json",
        "phase14-packaged-broadcast-trigger-remove-smoke.json",
        "phase14-notification-ownership.json",
        "phase14-settings-power-oem-background-matrix.json",
        "phase14-recorder-provider.json",
        "phase14-packaged-recorder-media-stress.json",
        "phase14-media-playback-mediastore-boundary.json",
        "phase14-task-device-media-doc-sync.json",
        "phase14-worker-native-availability.json",
        "phase14-worker-runnable-packaged-smoke.json",
        "phase14-worker-resource-rss-stress.json",
        "phase14-process-worker-replacement-decision.json",
        "phase14-desktop-runtime-doc-sync.json",
    ).forEach { reportName ->
        inputs.file(nodePhase14ReportDir.map { it.file(reportName) })
    }
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-security-regression-corpus.json") },
        nodePhase14ReportDir.map { it.file("phase14-security-regression-corpus.md") },
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

tasks.register<Exec>("installAndRunNodeV12PackagedSmoke") {
    group = "verification"
    description = "Runs the Phase 14 Node v1.2 packaged aggregate smoke contract gate."

    dependsOn("verifyNodePhase14SecurityRegressionCorpus")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/install-and-run-node-v12-packaged-smoke.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_34_NODE_V1_2_PACKAGED_AGGREGATE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/node-v1-2-packaged-aggregate-smoke-plan.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NODE_V1_2_PACKAGED_AGGREGATE_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    listOf(
        "phase14-live-ui-provider-packaged.json",
        "phase14-packaged-overlay-positive-smoke.json",
        "phase14-accessibility-lifecycle.json",
        "phase14-packaged-image-ocr-evidence.json",
        "phase14-packaged-task-scheduling-smoke.json",
        "phase14-packaged-broadcast-trigger-remove-smoke.json",
        "phase14-notification-ownership.json",
        "phase14-settings-power-oem-background-matrix.json",
        "phase14-packaged-recorder-media-stress.json",
        "phase14-worker-runnable-packaged-smoke.json",
        "phase14-security-regression-corpus.json",
    ).forEach { reportName ->
        inputs.file(nodePhase14ReportDir.map { it.file(reportName) })
    }
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-packaged-aggregate-smoke.json") },
        nodePhase14ReportDir.map { it.file("phase14-packaged-aggregate-smoke.md") },
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

tasks.register<Exec>("verifyNodePhase14TypeDeclarationProfiles") {
    group = "verification"
    description = "Verifies the Phase 14 Node v1.2 TypeScript declaration profile matrix."

    dependsOn(
        "installAndRunNodeV12PackagedSmoke",
        "verifyNodePhase13TypeDeclarationProfiles",
        "verifyAutoJs6NodeTypeDeclarations",
        "typeCheckNodeTypescriptSmoke",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-type-declaration-profiles.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_35_TYPE_DECLARATION_PROFILES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/node-v1-2-type-declaration-profiles.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NODE_V1_2_TYPE_DECLARATION_PROFILES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/profile_capabilities.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/package.json"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("node-examples.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    listOf(
        "phase13-type-declaration-profiles.json",
        "phase14-ui-automation-media-doc-sync.json",
        "phase14-task-device-media-doc-sync.json",
        "phase14-desktop-runtime-doc-sync.json",
        "phase14-security-regression-corpus.json",
        "phase14-packaged-aggregate-smoke.json",
    ).forEach { reportName ->
        inputs.file(nodePhase14ReportDir.map { it.file(reportName) })
    }
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-type-declaration-profiles.json") },
        nodePhase14ReportDir.map { it.file("phase14-type-declaration-profiles.md") },
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

tasks.register<Exec>("verifyNodePhase14MigrationProComparison") {
    group = "verification"
    description = "Verifies the Phase 14 Node v1.2 migration and Pro comparison docs claim sync."

    dependsOn(
        "verifyNodePhase14TypeDeclarationProfiles",
        "verifyNodePhase13MigrationPro9Comparison",
    )

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-migration-pro-comparison.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_36_MIGRATION_PRO_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/node-v1-2-migration-pro-comparison.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NODE_V1_2_MIGRATION_PRO_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/profile_capabilities.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    listOf(
        "phase13-migration-pro9-comparison.json",
        "phase14-ui-automation-media-doc-sync.json",
        "phase14-task-device-media-doc-sync.json",
        "phase14-desktop-runtime-doc-sync.json",
        "phase14-security-regression-corpus.json",
        "phase14-packaged-aggregate-smoke.json",
        "phase14-type-declaration-profiles.json",
    ).forEach { reportName ->
        inputs.file(nodePhase14ReportDir.map { it.file(reportName) })
    }
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-migration-pro-comparison.json") },
        nodePhase14ReportDir.map { it.file("phase14-migration-pro-comparison.md") },
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

tasks.register<Exec>("generateNodePhase14Summary") {
    group = "verification"
    description = "Generates the Phase 14 v1.2 summary and closeout policy gate report."

    dependsOn("verifyNodePhase14MigrationProComparison")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase14/generate-phase14-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_37_PHASE14_SUMMARY_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/node-v1-2-summary-gate.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NODE_V1_2_SUMMARY_GATE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase13-capability-truth.json") })
    listOf(
        "phase14-accessibility-lifecycle.json",
        "phase14-accessibility-selector-action-matrix.json",
        "phase14-advanced-image-operations.json",
        "phase14-baseline-verification.json",
        "phase14-desktop-runtime-doc-sync.json",
        "phase14-intent-broadcast-provider.json",
        "phase14-live-ui-provider-packaged.json",
        "phase14-media-playback-mediastore-boundary.json",
        "phase14-media-projection-provider.json",
        "phase14-migration-pro-comparison.json",
        "phase14-notification-ownership.json",
        "phase14-overlay-permission-oem-matrix.json",
        "phase14-overlay-provider.json",
        "phase14-packaged-aggregate-smoke.json",
        "phase14-packaged-broadcast-trigger-remove-smoke.json",
        "phase14-packaged-image-ocr-evidence.json",
        "phase14-packaged-overlay-positive-smoke.json",
        "phase14-packaged-recorder-media-stress.json",
        "phase14-packaged-task-scheduling-smoke.json",
        "phase14-process-worker-replacement-decision.json",
        "phase14-promotion-contracts.json",
        "phase14-recorder-provider.json",
        "phase14-security-regression-corpus.json",
        "phase14-settings-power-oem-background-matrix.json",
        "phase14-surface-manifest-verification.json",
        "phase14-task-database-provider.json",
        "phase14-task-device-media-doc-sync.json",
        "phase14-task-schema-migration-rollback.json",
        "phase14-type-declaration-profiles.json",
        "phase14-ui-automation-media-doc-sync.json",
        "phase14-worker-native-availability.json",
        "phase14-worker-resource-rss-stress.json",
        "phase14-worker-runnable-packaged-smoke.json",
    ).forEach { reportName ->
        inputs.file(nodePhase14ReportDir.map { it.file(reportName) })
    }
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-summary.json") },
        nodePhase14ReportDir.map { it.file("phase14-summary.md") },
        rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_SUMMARY.md"),
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
        "--docs-output",
        rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_SUMMARY.md").asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase14ReleaseCandidateChecklist") {
    group = "verification"
    description = "Verifies the Phase 14 Node v1.2 release candidate checklist gate."

    dependsOn("generateNodePhase14Summary")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-release-candidate-checklist.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_38_RELEASE_CANDIDATE_CHECKLIST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/node-v1-2-rc-checklist.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NODE_V1_2_RC_CHECKLIST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_RC_CHECKLIST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_RELEASE_NOTES_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    listOf(
        "phase14-summary.json",
        "phase14-security-regression-corpus.json",
        "phase14-packaged-aggregate-smoke.json",
        "phase14-type-declaration-profiles.json",
        "phase14-migration-pro-comparison.json",
        "phase14-ui-automation-media-doc-sync.json",
        "phase14-task-device-media-doc-sync.json",
        "phase14-desktop-runtime-doc-sync.json",
    ).forEach { reportName ->
        inputs.file(nodePhase14ReportDir.map { it.file(reportName) })
    }
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-release-candidate-checklist.json") },
        nodePhase14ReportDir.map { it.file("phase14-release-candidate-checklist.md") },
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

tasks.register<Exec>("verifyNodePhase14CloseoutHandoff") {
    group = "verification"
    description = "Verifies the Phase 14 Node v1.2 closeout and next-branch handoff gate."

    dependsOn("verifyNodePhase14ReleaseCandidateChecklist")

    val scriptFile =
        rootProject.layout.projectDirectory.file("tools/nodejs/phase14/verify-node-phase14-closeout-handoff.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/P14_39_CLOSEOUT_HANDOFF.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/node-v1-2-closeout-handoff.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_NODE_V1_2_CLOSEOUT_HANDOFF.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_CLOSEOUT_BREAKDOWN.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_NEXT_BRANCH_HANDOFF.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase14/PHASE14_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_RC_CHECKLIST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_2_RELEASE_NOTES_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/phase14-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase14-gate.gradle.kts"))
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-summary.json") })
    inputs.file(nodePhase14ReportDir.map { it.file("phase14-release-candidate-checklist.json") })
    outputs.files(
        nodePhase14ReportDir.map { it.file("phase14-closeout-handoff.json") },
        nodePhase14ReportDir.map { it.file("phase14-closeout-handoff.md") },
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
