val nodePhase12ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("verifyNodeRhinoCompatPhase12Baseline") {
    group = "verification"
    description = "Verifies the Phase 12 Rhino compatibility baseline inputs from Phase 11A."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-phase12-baseline.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("phase12-baseline-verification.json") },
        nodePhase12ReportDir.map { it.file("phase12-baseline-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatPhase12SurfaceManifest") {
    group = "verification"
    description = "Verifies the Phase 12 Rhino compatibility promoted surface manifest."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-phase12-surface-manifest.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("phase12-surface-manifest-verification.json") },
        nodePhase12ReportDir.map { it.file("phase12-surface-manifest-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatEventsKeysFacade") {
    group = "verification"
    description = "Verifies the Phase 12 Rhino events.keys pure constants compatibility facade."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-events-keys-facade.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-events-keys-facade-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-events-keys-facade-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatInputObserverContract") {
    group = "verification"
    description = "Verifies the Phase 12 input_observer provider v1 contract gate."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-input-observer-contract.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/input_observer_provider_v1.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-contract-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-contract-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatInputObserverFakeProvider") {
    group = "verification"
    description = "Verifies the Phase 12 input_observer fake provider POC gate."

    dependsOn("verifyNodeRhinoCompatInputObserverContract")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-input-observer-fake-provider.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/input_observer.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-fake-provider-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-fake-provider-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatInputObserverLiveGate") {
    group = "verification"
    description = "Verifies the Phase 12 input_observer accessibility live-source gate."

    dependsOn("verifyNodeRhinoCompatInputObserverFakeProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-input-observer-live-gate.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-live-gate-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-live-gate-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatInputObserverSecurity") {
    group = "verification"
    description = "Verifies the Phase 12 input_observer cleanup/security/packaged policy gate."

    dependsOn("verifyNodeRhinoCompatInputObserverLiveGate")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-input-observer-security.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_INPUT_OBSERVER_SECURITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-security-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-input-observer-security-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatUiJsonLayoutSubset") {
    group = "verification"
    description = "Verifies the Phase 12 Rhino UI JSON layout subset schema gate."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-ui-json-layout-subset.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/rhino_ui_json_layout_subset.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/fixtures/rhino-ui-json-layout-subset-fixtures.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeUiProvider.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-ui-json-layout-subset-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-ui-json-layout-subset-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatUiFacade") {
    group = "verification"
    description = "Verifies the Phase 12 constrained compat.ui facade gate."

    dependsOn("verifyNodeRhinoCompatUiJsonLayoutSubset")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-ui-facade.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-ui-facade-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-ui-facade-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatUiSmoke") {
    group = "verification"
    description = "Verifies the Phase 12 compat.ui device and packaged smoke evidence."

    dependsOn("verifyNodeRhinoCompatUiFacade")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-ui-smoke.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-ui-smoke-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-ui-smoke-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatFloatyOverlayDecision") {
    group = "verification"
    description = "Verifies the Phase 12 floaty/overlay path decision gate."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-floaty-overlay-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_OVERLAY_PHASE12_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-floaty-overlay-decision-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-floaty-overlay-decision-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatOverlayProviderContract") {
    group = "verification"
    description = "Verifies the Phase 12 overlay provider v1 contract gate."

    dependsOn("verifyNodeRhinoCompatFloatyOverlayDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-overlay-provider-contract.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/schemas/overlay_provider_v1.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/fixtures/overlay-provider-v1-fixtures.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_OVERLAY_PHASE12_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-overlay-provider-contract-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-overlay-provider-contract-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatOverlayFakeProvider") {
    group = "verification"
    description = "Verifies the Phase 12 ui.overlay fake-provider POC gate."

    dependsOn("verifyNodeRhinoCompatOverlayProviderContract")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-overlay-fake-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui_overlay.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-overlay-fake-provider-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-overlay-fake-provider-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatOverlayLiveGate") {
    group = "verification"
    description = "Verifies the Phase 12 ui.overlay Android live-gate and packaged cleanup policy."

    dependsOn("verifyNodeRhinoCompatOverlayFakeProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-overlay-live-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui_overlay.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/AndroidManifest.xml"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/java/org/autojs/autojs/project/ProjectConfig.java"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-overlay-live-gate-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-overlay-live-gate-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatTasksV1Scope") {
    group = "verification"
    description = "Verifies the Phase 12 compat.tasks v1 scope lock over work_manager."

    dependsOn("verifyNodeRhinoCompatOverlayLiveGate")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-tasks-v1-scope.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/COMPAT_TASKS_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-v1-scope-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-v1-scope-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatTasksDisposableFacade") {
    group = "verification"
    description = "Verifies the Phase 12 compat.tasks disposable-task facade over work_manager."

    dependsOn("verifyNodeRhinoCompatTasksV1Scope")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-tasks-disposable-facade.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/COMPAT_TASKS_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-disposable-facade-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-disposable-facade-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatTasksDailyWeeklyDecision") {
    group = "verification"
    description = "Verifies the Phase 12 compat.tasks daily/weekly user task database decision."

    dependsOn("verifyNodeRhinoCompatTasksDisposableFacade")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-tasks-daily-weekly-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/COMPAT_TASKS_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-daily-weekly-decision-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-daily-weekly-decision-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatTasksIntentBroadcastDecision") {
    group = "verification"
    description = "Verifies the Phase 12 compat.tasks IntentTask and broadcast trigger decision."

    dependsOn("verifyNodeRhinoCompatTasksDailyWeeklyDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-tasks-intent-broadcast-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("app/src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BROADCAST_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/COMPAT_TASKS_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/phase12-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase12-gate.gradle.kts"))
    outputs.files(
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-intent-broadcast-decision-verification.json") },
        nodePhase12ReportDir.map { it.file("rhino-compat-tasks-intent-broadcast-decision-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase12ReportDir.get().asFile.absolutePath,
    )
}
