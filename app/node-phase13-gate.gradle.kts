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

tasks.register<Exec>("collectNodePhase13CrashEvidence") {
    group = "verification"
    description = "Collects and verifies Phase 13 crash evidence automation readiness."

    dependsOn("generateNodePhase13CapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/collect-crash-evidence.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/App.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeCrashEvidenceStoreInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAndroidRuntimePolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-crash-evidence-automation.json") },
        nodePhase13ReportDir.map { it.file("phase13-crash-evidence-automation.md") },
        nodePhase13ReportDir.map { it.file("phase13-crash-evidence-bundle/manifest.json") },
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

tasks.register<Exec>("verifyNodePhase13SecondExecutionSlotScheduler") {
    group = "verification"
    description = "Verifies Phase 13 second execution slot scheduler readiness."

    dependsOn("collectNodePhase13CrashEvidence")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-second-execution-slot-scheduler.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_06_SECOND_EXECUTION_SLOT_SCHEDULER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_08_FULL_HEAVY_RESOURCE_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectJavaScriptFileSource.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedProjectRunner.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/inrt/launch/AssetsProjectLauncher.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedTestHarness.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-second-execution-slot-scheduler.json") },
        nodePhase13ReportDir.map { it.file("phase13-second-execution-slot-scheduler.md") },
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

tasks.register<Exec>("verifyNodePhase13PackagedLongRunningService") {
    group = "verification"
    description = "Verifies Phase 13 packaged long-running service readiness."

    dependsOn("verifyNodePhase13SecondExecutionSlotScheduler")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-packaged-long-running-service.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_07_PACKAGED_LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/long-running-service/project.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedProjectRunner.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedJavaScriptEngine.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeProjectLaunchDiagnostics.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/inrt/launch/AssetsProjectLauncher.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/project/ProjectConfig.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectJavaScriptFileSource.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeExecutionModePolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-packaged-long-running-service.json") },
        nodePhase13ReportDir.map { it.file("phase13-packaged-long-running-service.md") },
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

tasks.register<Exec>("verifyNodePhase13FullHeavyResourceStress") {
    group = "verification"
    description = "Verifies Phase 13 full heavy resource stress readiness."

    dependsOn("verifyNodePhase13PackagedLongRunningService")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-full-heavy-resource-stress.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/archive-full-heavy-resource-soak.js"))
    inputs.dir(rootProject.layout.projectDirectory.dir("tools/nodejs/phase13/fixtures/full-heavy-resource-stress"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_08_FULL_HEAVY_RESOURCE_STRESS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-soak-gate.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePhase7ResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/PERFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/quick-soak-8h.json"),
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/final-soak-24h.json"),
    )
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-full-heavy-resource-stress.json") },
        nodePhase13ReportDir.map { it.file("phase13-full-heavy-resource-stress.md") },
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

tasks.register<Exec>("verifyNodePhase13AndroidPackageInstallManager") {
    group = "verification"
    description = "Verifies Phase 13 Android-side package install manager readiness."

    dependsOn("verifyNodePhase13FullHeavyResourceStress")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-android-package-install-manager.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/package_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-android-package-install-manager.json") },
        nodePhase13ReportDir.map { it.file("phase13-android-package-install-manager.md") },
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

tasks.register<Exec>("verifyNodePhase13ProEntryDirectives") {
    group = "verification"
    description = "Verifies Phase 13 Pro-style Node entry directive readiness."

    dependsOn("verifyNodePhase13AndroidPackageInstallManager")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-pro-entry-directives.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_11_PRO_ENTRY_DIRECTIVES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/JavaScriptSource.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeEntryDirective.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectJavaScriptFileSource.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedProjectRunner.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeProjectLaunchDiagnostics.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/inrt/launch/AssetsProjectLauncher.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/project/ProjectConfig.java"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-pro-entry-directives.json") },
        nodePhase13ReportDir.map { it.file("phase13-pro-entry-directives.md") },
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

tasks.register<Exec>("verifyNodePhase13AutoJsGlobalV1") {
    group = "verification"
    description = "Verifies Phase 13 \$autojs global v1 readiness."

    dependsOn("verifyNodePhase13ProEntryDirectives")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-autojs-global-v1.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_12_AUTOJS_GLOBAL_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-autojs-global-v1.json") },
        nodePhase13ReportDir.map { it.file("phase13-autojs-global-v1.md") },
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

tasks.register<Exec>("verifyNodePhase13JavaInteropAllowlist") {
    group = "verification"
    description = "Verifies Phase 13 Java interop allowlist progress."

    dependsOn("verifyNodePhase13AutoJsGlobalV1")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-java-interop-allowlist.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_13_JAVA_INTEROP_ALLOWLIST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/JAVA_INTEROP.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeJavaInteropBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-java-interop-allowlist.json") },
        nodePhase13ReportDir.map { it.file("phase13-java-interop-allowlist.md") },
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

tasks.register<Exec>("verifyNodePhase13RhinoInstallJavaProxy") {
    group = "verification"
    description = "Verifies Phase 13 rhino.install Java proxy progress."

    dependsOn("verifyNodePhase13JavaInteropAllowlist")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-rhino-install-java-proxy.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_14_RHINO_INSTALL_JAVA_PROXY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_SHIM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/JAVA_INTEROP.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-rhino-install-java-proxy.json") },
        nodePhase13ReportDir.map { it.file("phase13-rhino-install-java-proxy.md") },
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

tasks.register<Exec>("verifyNodePhase13RhinoImportHelpersDecision") {
    group = "verification"
    description = "Verifies Phase 13 Rhino importClass/importPackage decision."

    dependsOn("verifyNodePhase13RhinoInstallJavaProxy")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-rhino-import-helpers-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_15_IMPORT_HELPERS_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_SHIM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/JAVA_INTEROP.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-rhino-import-helpers-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-rhino-import-helpers-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13RhinoJavaAdapterDecision") {
    group = "verification"
    description = "Verifies Phase 13 Rhino JavaAdapter / defineClass replacement decision."

    dependsOn("verifyNodePhase13RhinoImportHelpersDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-rhino-java-adapter-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_16_JAVA_ADAPTER_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_SHIM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/JAVA_INTEROP.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeJavaInteropBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-rhino-java-adapter-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-rhino-java-adapter-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13LiveUiProviderPromotion") {
    group = "verification"
    description = "Verifies Phase 13 live UI provider promotion progress."

    dependsOn("verifyNodePhase13RhinoJavaAdapterDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-live-ui-provider-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeUiProvider.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeUiBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-live-ui-provider-promotion.json") },
        nodePhase13ReportDir.map { it.file("phase13-live-ui-provider-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase13OverlayProviderPromotion") {
    group = "verification"
    description = "Verifies Phase 13 overlay provider promotion progress."

    dependsOn("verifyNodePhase13LiveUiProviderPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-overlay-provider-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/AndroidManifest.xml"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/project/ProjectConfig.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOverlayBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui_overlay.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/OVERLAY_PROVIDER_V1.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase12/verify-rhino-compat-overlay-live-gate.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/PHASE12_SUMMARY.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-overlay-provider-promotion.json") },
        nodePhase13ReportDir.map { it.file("phase13-overlay-provider-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase13AccessibilitySelectorPromotion") {
    group = "verification"
    description = "Verifies Phase 13 accessibility selector promotion progress."

    dependsOn("verifyNodePhase13OverlayProviderPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-accessibility-selector-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_SELECTOR_DSL_BRIDGE_GAPS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/DEVICE_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-accessibility-selector-promotion.json") },
        nodePhase13ReportDir.map { it.file("phase13-accessibility-selector-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase13ScreenshotImageOcrPromotion") {
    group = "verification"
    description = "Verifies Phase 13 screenshot, image, and OCR promotion progress."

    dependsOn("verifyNodePhase13AccessibilitySelectorPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeScreenCaptureRealDeviceSmokeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media_projection.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ocr.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/DEVICE_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.json") },
        nodePhase13ReportDir.map { it.file("phase13-screenshot-image-ocr-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase13TaskDatabaseProvider") {
    group = "verification"
    description = "Verifies Phase 13 daily/weekly task database provider progress."

    dependsOn("verifyNodePhase13ScreenshotImageOcrPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-task-database-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/storage/database/TimedTaskDatabase.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-task-database-provider.json") },
        nodePhase13ReportDir.map { it.file("phase13-task-database-provider.md") },
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

tasks.register<Exec>("verifyNodePhase13IntentBroadcastProvider") {
    group = "verification"
    description = "Verifies Phase 13 IntentTask/broadcast task provider progress."

    dependsOn("verifyNodePhase13TaskDatabaseProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-intent-broadcast-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/augment/tasks/Tasks.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/timing/IntentTask.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/external/receiver/BaseBroadcastReceiver.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/external/receiver/StaticBroadcastReceiver.java"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/external/receiver/DynamicBroadcastReceivers.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BROADCAST_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RELEASE_CHECKLIST.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-intent-broadcast-provider.json") },
        nodePhase13ReportDir.map { it.file("phase13-intent-broadcast-provider.md") },
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

tasks.register<Exec>("verifyNodePhase13AppIntentBroadcastParity") {
    group = "verification"
    description = "Verifies Phase 13 app/intent/broadcast Pro parity progress."

    dependsOn("verifyNodePhase13IntentBroadcastProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-app-intent-broadcast-parity.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/augment/app/App.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/app.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BROADCAST_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-app-intent-broadcast-parity.json") },
        nodePhase13ReportDir.map { it.file("phase13-app-intent-broadcast-parity.md") },
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

tasks.register<Exec>("verifyNodePhase13ShellRootShizukuCapability") {
    group = "verification"
    description = "Verifies Phase 13 shell/root/Shizuku capability progress."

    dependsOn("verifyNodePhase13AppIntentBroadcastParity")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-shell-root-shizuku-capability.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/shell.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-shell-root-shizuku-capability.json") },
        nodePhase13ReportDir.map { it.file("phase13-shell-root-shizuku-capability.md") },
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

tasks.register<Exec>("verifyNodePhase13NotificationsSettingsPowerManager") {
    group = "verification"
    description = "Verifies Phase 13 notifications/settings/power manager progress."

    dependsOn("verifyNodePhase13ShellRootShizukuCapability")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-notifications-settings-power-manager.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeDeviceBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/notifications.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/device.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/project/autojs6-node-project.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-notifications-settings-power-manager.json") },
        nodePhase13ReportDir.map { it.file("phase13-notifications-settings-power-manager.md") },
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

tasks.register<Exec>("verifyNodePhase13MediaRecorderMediainfoProvider") {
    group = "verification"
    description = "Verifies Phase 13 media/recorder/mediainfo provider progress."

    dependsOn("verifyNodePhase13NotificationsSettingsPowerManager")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-media-recorder-mediainfo-provider.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mediainfo.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/recorder.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/project/autojs6-node-project.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.json") },
        nodePhase13ReportDir.map { it.file("phase13-media-recorder-mediainfo-provider.md") },
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

tasks.register<Exec>("verifyNodePhase13UtilityHeavyRhinoModules") {
    group = "verification"
    description = "Verifies Phase 13 utility-heavy Rhino module progress."

    dependsOn("verifyNodePhase13MediaRecorderMediainfoProvider")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-utility-heavy-rhino-modules.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_27_UTILITY_HEAVY_RHINO_MODULES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeUtilityBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/jsox.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-utility-heavy-rhino-modules.json") },
        nodePhase13ReportDir.map { it.file("phase13-utility-heavy-rhino-modules.md") },
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

tasks.register<Exec>("generateNodeBuiltinDesktopParityMatrix") {
    group = "verification"
    description = "Generates the Phase 13 desktop Node builtin parity matrix."

    dependsOn("generateNodePhase13CapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js")
    val docsOutput = rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("node-builtin-parity.json") },
        nodePhase13ReportDir.map { it.file("node-builtin-parity.md") },
        docsOutput,
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
        "--docs-output",
        docsOutput.asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase13HttpHttpsClientParity") {
    group = "verification"
    description = "Verifies Phase 13 HTTP/HTTPS controlled client parity progress."

    dependsOn("generateNodeBuiltinDesktopParityMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-http-https-client-parity.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_29_HTTP_HTTPS_CLIENT_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHttpDesktopParityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHttpClientFacadeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHttpStreamSemanticsInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHttpsClientFacadeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHttpAgentFacadeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRawNetworkPolicyInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/http.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/https.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-http-https-client-parity.json") },
        nodePhase13ReportDir.map { it.file("phase13-http-https-client-parity.md") },
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

tasks.register<Exec>("verifyNodePhase13NetTlsDnsStagedCompatibility") {
    group = "verification"
    description = "Verifies Phase 13 net/tls/dns staged compatibility progress."

    dependsOn("verifyNodePhase13HttpHttpsClientParity")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-net-tls-dns-staged-compatibility.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_30_NET_TLS_DNS_STAGED_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNetTlsDnsStagedParityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRawNetworkPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/net.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/tls.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/dns.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-net-tls-dns-staged-compatibility.json") },
        nodePhase13ReportDir.map { it.file("phase13-net-tls-dns-staged-compatibility.md") },
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

tasks.register<Exec>("verifyNodePhase13DgramUdpDecision") {
    group = "verification"
    description = "Verifies Phase 13 dgram UDP capability decision."

    dependsOn("verifyNodePhase13NetTlsDnsStagedCompatibility")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-dgram-udp-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_31_DGRAM_UDP_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeDgramUdpDecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRawNetworkPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-dgram-udp-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-dgram-udp-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13FilesystemProfileDecision") {
    group = "verification"
    description = "Verifies Phase 13 filesystem profile expansion decision."

    dependsOn("verifyNodePhase13DgramUdpDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-filesystem-profile-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_32_FILESYSTEM_PROFILE_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemProfileDecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeScopedFsFuzzerInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSafeFileOperationsInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFileHandleAdvancedInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-filesystem-profile-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-filesystem-profile-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13FilesystemAdvancedApis") {
    group = "verification"
    description = "Verifies Phase 13 desktop filesystem advanced API decision."

    dependsOn("verifyNodePhase13FilesystemProfileDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-filesystem-advanced-apis.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_33_FILESYSTEM_ADVANCED_APIS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemAdvancedDecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemProfileDecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFileHandleAdvancedInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFdApiInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOpendirInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFsWatchInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFileHandleWatcherFuzzerInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFsPromisesInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSafeFileOperationsInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeScopedFsFuzzerInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-filesystem-advanced-apis.json") },
        nodePhase13ReportDir.map { it.file("phase13-filesystem-advanced-apis.md") },
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

tasks.register<Exec>("verifyNodePhase13WorkerThreadsOptInPromotion") {
    group = "verification"
    description = "Verifies Phase 13 worker_threads opt-in promotion decision."

    dependsOn("verifyNodePhase13FilesystemAdvancedApis")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-worker-threads-opt-in-promotion.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("app/build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/WORKER_EXPERIMENTAL_EVALUATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-worker-threads-opt-in-promotion.json") },
        nodePhase13ReportDir.map { it.file("phase13-worker-threads-opt-in-promotion.md") },
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

tasks.register<Exec>("verifyNodePhase13ProcessWorkerReplacementDecision") {
    group = "verification"
    description = "Verifies Phase 13 process-worker replacement decision."

    dependsOn("verifyNodePhase13WorkerThreadsOptInPromotion")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-process-worker-replacement-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase12/RHINO_COMPUTE_PROCESS_WORKER_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-process-worker-replacement-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-process-worker-replacement-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13VmModuleDecision") {
    group = "verification"
    description = "Verifies Phase 13 vm module decision."

    dependsOn("verifyNodePhase13ProcessWorkerReplacementDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-vm-module-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_36_VM_MODULE_DECISION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeVmPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeVmDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/VM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-vm-module-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-vm-module-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13InspectorDebugProfileDecision") {
    group = "verification"
    description = "Verifies Phase 13 inspector debug profile decision."

    dependsOn("verifyNodePhase13VmModuleDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-inspector-debug-profile-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_37_INSPECTOR_DEBUG_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeModuleSafeSubsetInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/INSPECTOR.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/debug/verify-phase9-inspector-policy.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-inspector-debug-profile-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-inspector-debug-profile-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13ControlledWasiProfileDecision") {
    group = "verification"
    description = "Verifies Phase 13 controlled WASI profile decision."

    dependsOn("verifyNodePhase13InspectorDebugProfileDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-controlled-wasi-profile-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_38_CONTROLLED_WASI_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasiPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/wasm/verify-phase9-wasm-wasi-policy.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-controlled-wasi-profile-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-controlled-wasi-profile-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13NativeAddonLoadingDecision") {
    group = "verification"
    description = "Verifies Phase 13 native addon loading decision."

    dependsOn("verifyNodePhase13ControlledWasiProfileDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-native-addon-loading-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_39_NATIVE_ADDON_LOADING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNativeAddonPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNativeAddonPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/project/autojs6-node-project.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/plugin/verify-phase9-plugin-policy.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/PLUGIN_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/CHAOS_SECURITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-native-addon-loading-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-native-addon-loading-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13ChildProcessScopedRunnerDecision") {
    group = "verification"
    description = "Verifies Phase 13 child_process scoped runner decision."

    dependsOn("verifyNodePhase13NativeAddonLoadingDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-child-process-scoped-runner-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_40_CHILD_PROCESS_SCOPED_RUNNER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeChildProcessPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/project/autojs6-node-project.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-child-process-scoped-runner-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-child-process-scoped-runner-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13EsmLoaderParityDecision") {
    group = "verification"
    description = "Verifies Phase 13 ESM loader parity decision."

    dependsOn("verifyNodePhase13ChildProcessScopedRunnerDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-esm-loader-parity-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_41_ESM_LOADER_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEsmLoaderPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEsmExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSourceMapStackTraceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/ESM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/ESM_EXPERIMENTAL_EVALUATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/V1_1_ESM_PROMOTION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-esm-loader-parity-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-esm-loader-parity-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13PackageManagerAndroidDecision") {
    group = "verification"
    description = "Verifies Phase 13 Android package manager decision."

    dependsOn("verifyNodePhase13EsmLoaderParityDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-package-manager-android-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_42_PACKAGE_MANAGER_ANDROID.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-android-package-install-manager.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/package_manager.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-package-manager-android-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-package-manager-android-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13StdlibConformanceDecision") {
    group = "verification"
    description = "Verifies Phase 13 stdlib conformance decision."

    dependsOn("verifyNodePhase13PackageManagerAndroidDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-stdlib-conformance-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_43_STDLIB_CONFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeStdlibPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/assets/npm_compat_real/manifest.json"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeModernCoreDifferentialInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/assets/node_modern_core_differential/manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-stdlib-conformance-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-stdlib-conformance-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13ProcessParityDecision") {
    group = "verification"
    description = "Verifies Phase 13 process/env/argv parity decision."

    dependsOn("verifyNodePhase13StdlibConformanceDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-process-parity-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_44_PROCESS_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-node-builtin-parity-matrix.js"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessParityPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeModuleCompileCacheInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeModernCoreDifferentialInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/assets/node_modern_core_differential/manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-process-parity-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-process-parity-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13PackagedCapabilityMetadataDecision") {
    group = "verification"
    description = "Verifies Phase 13 packaged APK capability metadata decision."

    dependsOn("verifyNodePhase13ProcessParityDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-packaged-capability-metadata-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_45_PACKAGED_CAPABILITY_METADATA.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-packaged-capability-metadata-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-packaged-capability-metadata-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13PluginSystemParityDecision") {
    group = "verification"
    description = "Verifies Phase 13 plugin system parity decision."

    dependsOn("verifyNodePhase13PackagedCapabilityMetadataDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-plugin-system-parity-decision.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_46_PLUGIN_SYSTEM_PARITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemParityPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PLUGIN_SYSTEM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/PLUGIN_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-plugin-system-parity-decision.json") },
        nodePhase13ReportDir.map { it.file("phase13-plugin-system-parity-decision.md") },
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

tasks.register<Exec>("verifyNodePhase13DependencyCompatibilityCorpus") {
    group = "verification"
    description = "Verifies Phase 13 dependency compatibility corpus."

    dependsOn("verifyNodePhase13PluginSystemParityDecision")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-dependency-compatibility-corpus.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_47_DEPENDENCY_COMPATIBILITY_CORPUS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/assets/npm_compat_real/manifest.json"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/differential/run-differential-tests.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-dependency-compatibility-corpus.json") },
        nodePhase13ReportDir.map { it.file("phase13-dependency-compatibility-corpus.md") },
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

tasks.register<Exec>("verifyNodePhase13PackageLockIntegrityPolicy") {
    group = "verification"
    description = "Verifies Phase 13 package lock and integrity policy."

    dependsOn("verifyNodePhase13DependencyCompatibilityCorpus")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-package-lock-integrity-policy.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_48_PACKAGE_LOCK_INTEGRITY_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodePackageIntegrityPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_39_NATIVE_ADDON_LOADING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PLUGIN_SYSTEM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/PLUGIN_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-package-lock-integrity-policy.json") },
        nodePhase13ReportDir.map { it.file("phase13-package-lock-integrity-policy.md") },
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

tasks.register<Exec>("verifyNodePhase13ProfileSelectionDiagnostics") {
    group = "verification"
    description = "Verifies Phase 13 profile selection UX and diagnostics."

    dependsOn("verifyNodePhase13PackageLockIntegrityPolicy")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-profile-selection-diagnostics.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_49_PROFILE_SELECTION_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeProfileSelectionDiagnostics.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProfileSelectionDiagnosticsPhase13DecisionInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-profile-selection-diagnostics.json") },
        nodePhase13ReportDir.map { it.file("phase13-profile-selection-diagnostics.md") },
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

tasks.register<Exec>("verifyNodePhase13RelaxedCapabilitySecurityCorpus") {
    group = "verification"
    description = "Verifies Phase 13 relaxed-capability security corpus."

    dependsOn("verifyNodePhase13ProfileSelectionDiagnostics")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-relaxed-capability-security-corpus.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_50_RELAXED_CAPABILITY_SECURITY_CORPUS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeHttpDesktopParityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNetTlsDnsStagedParityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemAdvancedDecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeJavaInteropBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeChildProcessPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNativeAddonPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInspectorPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasiPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/CAPABILITY_RELAXATION_RUBRIC.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-relaxed-capability-security-corpus.json") },
        nodePhase13ReportDir.map { it.file("phase13-relaxed-capability-security-corpus.md") },
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

tasks.register<Exec>("verifyNodePhase13ProfileRollbackPlaybook") {
    group = "verification"
    description = "Verifies Phase 13 per-profile rollback playbook."

    dependsOn("verifyNodePhase13RelaxedCapabilitySecurityCorpus")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-profile-rollback-playbook.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_51_PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("gradle.properties"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeProfileRollbackPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeProfileRollbackPlaybookPhase13DecisionInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-profile-rollback-playbook.json") },
        nodePhase13ReportDir.map { it.file("phase13-profile-rollback-playbook.md") },
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

tasks.register<Exec>("verifyNodePhase13PrivacyDisclosurePolicy") {
    group = "verification"
    description = "Verifies Phase 13 privacy and disclosure policy."

    dependsOn("verifyNodePhase13ProfileRollbackPlaybook")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-privacy-disclosure-policy.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_52_PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodePrivacyDisclosurePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePrivacyDisclosurePolicyPhase13DecisionInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/autojs_global.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-privacy-disclosure-policy.json") },
        nodePhase13ReportDir.map { it.file("phase13-privacy-disclosure-policy.md") },
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

tasks.register<Exec>("verifyNodePhase13ProParityExamples") {
    group = "verification"
    description = "Verifies Phase 13 Pro parity example catalog."

    dependsOn("verifyNodePhase13PrivacyDisclosurePolicy", "verifyNodeExamples")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-pro-parity-examples.js")
    inputs.file(scriptFile)
    inputs.dir(rootProject.layout.projectDirectory.dir("sample/nodejs/pro-parity-suite"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/examples.json"))
    inputs.file(project.layout.projectDirectory.file("node-examples.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_53_PRO_PARITY_EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-pro-parity-examples.json") },
        nodePhase13ReportDir.map { it.file("phase13-pro-parity-examples.md") },
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

tasks.register<Exec>("verifyNodePhase13DesktopParityExamples") {
    group = "verification"
    description = "Verifies Phase 13 desktop Node parity example catalog."

    dependsOn("verifyNodePhase13ProParityExamples", "verifyNodeExamples")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-desktop-parity-examples.js")
    inputs.file(scriptFile)
    inputs.dir(rootProject.layout.projectDirectory.dir("sample/nodejs/desktop-parity-suite"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/examples.json"))
    inputs.file(project.layout.projectDirectory.file("node-examples.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_54_DESKTOP_PARITY_EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-desktop-parity-examples.json") },
        nodePhase13ReportDir.map { it.file("phase13-desktop-parity-examples.md") },
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

tasks.register<Exec>("verifyNodePhase13TypeDeclarationProfiles") {
    group = "verification"
    description = "Verifies Phase 13 profile-aware TypeScript declarations."

    dependsOn("verifyNodePhase13DesktopParityExamples", "verifyAutoJs6NodeTypeDeclarations", "typeCheckNodeTypescriptSmoke")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-type-declaration-profiles.js")
    inputs.file(scriptFile)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/types/autojs6-node"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/tsconfig.json"))
    inputs.file(project.layout.projectDirectory.file("node-examples.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/examples.json"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/desktop-parity-suite/examples.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_55_TYPE_DECLARATION_PROFILES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-type-declaration-profiles.json") },
        nodePhase13ReportDir.map { it.file("phase13-type-declaration-profiles.md") },
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

tasks.register<Exec>("verifyNodePhase13MigrationPro9Comparison") {
    group = "verification"
    description = "Verifies Phase 13 migration and Pro9 comparison docs."

    dependsOn("verifyNodePhase13TypeDeclarationProfiles")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/verify-migration-pro9-comparison.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/profile_capabilities.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/pro-parity-suite/examples.json"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/desktop-parity-suite/examples.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/P13_56_MIGRATION_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-migration-pro9-comparison.json") },
        nodePhase13ReportDir.map { it.file("phase13-migration-pro9-comparison.md") },
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

tasks.register<Exec>("generateNodePhase13Summary") {
    group = "verification"
    description = "Generates the Phase 13 summary and partial final gate report."

    dependsOn("verifyNodePhase13MigrationPro9Comparison")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase13/generate-phase13-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/phase13-surface-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase13-gate.gradle.kts"))
    inputs.file(nodePhase13ReportDir.map { it.file("phase13-capability-truth.json") })
    inputs.file(nodePhase13ReportDir.map { it.file("phase13-stdlib-conformance-decision.json") })
    inputs.file(nodePhase13ReportDir.map { it.file("phase13-migration-pro9-comparison.json") })
    outputs.files(
        nodePhase13ReportDir.map { it.file("phase13-summary.json") },
        nodePhase13ReportDir.map { it.file("phase13-summary.md") },
        rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_SUMMARY.md"),
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
        "--docs-output",
        rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase13/PHASE13_SUMMARY.md").asFile.absolutePath,
    )
}
