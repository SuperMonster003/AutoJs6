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
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
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
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt"))
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
