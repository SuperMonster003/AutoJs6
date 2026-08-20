val nodePhase10ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")
val nodePhase10NativeCppDir = (
        if (extensions.extraProperties.has("nodeJsRuntimeConfig.nodeJsPluginCppDir")) {
            extensions.extraProperties.get("nodeJsRuntimeConfig.nodeJsPluginCppDir") as? File
        } else {
            null
        }
        ) ?: project.file("src/main/cpp")
val nodePhase10RuntimeJniLibsDir = (
        if (extensions.extraProperties.has("nodeJsRuntimeConfig.nodeJsPluginRuntimeJniLibsDir")) {
            extensions.extraProperties.get("nodeJsRuntimeConfig.nodeJsPluginRuntimeJniLibsDir") as? File
        } else {
            null
        }
        ) ?: project.file("src/main/jniLibs")

tasks.register<Exec>("verifyNodePhase10RuntimeFreeze") {
    group = "verification"
    description = "Verifies Phase 10 keeps node24_5 as default and does not change libnode.so hashes or moduleSources budgets."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-runtime-freeze.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/runtime-freeze.lock.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/runtime-build.lock.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.dir(nodePhase10RuntimeJniLibsDir)
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-runtime-freeze.json") },
        nodePhase10ReportDir.map { it.file("phase10-runtime-freeze.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--jni-libs-root",
        nodePhase10RuntimeJniLibsDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodePhase10CapabilityTruth") {
    group = "verification"
    description = "Writes build/reports/nodejs/phase10-capability-truth.json from Phase 10 runtime and test evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/generate-capability-truth.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFetchBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWebSocketBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeStorageBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSensorsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilityRealDeviceSmokeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeUiBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-developer-experience.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-crash-adapter-cutover.js"))
    inputs.file(nodePhase10NativeCppDir.resolve("node_runtime_adapter_24_5.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-capability-truth.json") },
        nodePhase10ReportDir.map { it.file("phase10-capability-truth.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--native-cpp-root",
        nodePhase10NativeCppDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10ProviderRegistryV2") {
    group = "verification"
    description = "Verifies the Phase 10 Provider Registry v2 metadata, Doctor wiring, and instrumentation coverage."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-provider-registry-v2.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-provider-registry-v2.json") },
        nodePhase10ReportDir.map { it.file("phase10-provider-registry-v2.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodePhase10SkipAudit") {
    group = "verification"
    description = "Writes build/reports/nodejs/phase10-skip-audit.json by scanning unresolved skip/assume/ignore sites."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/generate-skip-audit.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/androidTest/java"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-skip-audit.json") },
        nodePhase10ReportDir.map { it.file("phase10-skip-audit.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10PackagedParityHost") {
    group = "verification"
    description = "Verifies packaged runtime descriptor, template freshness checks, ESM/dynamic-import markers, and APK fixture cache key inputs."

    dependsOn("verifyNodePhase10RuntimeFreeze")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/generate-packaged-parity-report.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/assets-app/template.apk"))
    inputs.dir(nodePhase10RuntimeJniLibsDir)
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-packaged-parity.json") },
        nodePhase10ReportDir.map { it.file("phase10-packaged-parity.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--jni-libs-root",
        nodePhase10RuntimeJniLibsDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10FocusedGateMatrix") {
    group = "verification"
    description = "Verifies the Phase 10 focused gate names are registered and use scoped selectors where needed."

    dependsOn(
        "verifyNodePhase10RuntimeFreeze",
        "generateNodePhase10CapabilityTruth",
        "verifyNodePhase10ProviderRegistryV2",
        "generateNodePhase10SkipAudit",
        "verifyNodePhase10PackagedParityHost",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-focused-gate-matrix.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-native-modernization.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-focused-gate-matrix.json") },
        nodePhase10ReportDir.map { it.file("phase10-focused-gate-matrix.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10Autojs6PmFixture") {
    group = "verification"
    description = "Verifies the Phase 10 autojs6-pm host-managed pure-JS package manager fixture and safety boundary."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js")
    inputs.file(scriptFile)
    inputs.dir(rootProject.layout.projectDirectory.dir("sample/nodejs/autojs6-pm-fixture"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-package-manager-boundary.json") },
        nodePhase10ReportDir.map { it.file("phase10-package-manager-boundary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "verify-fixture",
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10LongRunningActivation") {
    group = "verification"
    description = "Verifies the Phase 10 long-running, scheduled, queue, checkpoint, and multi-instance boundary report."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("node-instrumentation-gates.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionQueuePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.dir(project.layout.projectDirectory.dir("src/androidTest/java/org/autojs/autojs/engine"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-long-running-activation.json") },
        nodePhase10ReportDir.map { it.file("phase10-long-running-activation.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register("verifyNodePackageManagerGate") {
    group = "verification"
    description = "Runs the current Phase 10 package-manager boundary gate using safe dependency manager and bundler fixtures."

    dependsOn(
        "generateNodePhase10CapabilityTruth",
        "verifyNodePhase10Autojs6PmFixture",
        "verifyNodeDependencyManagerFixture",
        "verifyNodeDependencyBundlerFixtures",
    )
}

tasks.register("verifyNodeMultiInstanceGate") {
    group = "verification"
    description = "Runs the current Phase 10 multi-instance boundary gate without promoting additional live execution slots."

    dependsOn(
        "generateNodePhase10CapabilityTruth",
        "verifyNodePhase10LongRunningActivation",
        "verifyNodePhase9IsolationPolicy",
    )
}

tasks.register<Exec>("verifyNodePhase10WorkerWasiBoundary") {
    group = "verification"
    description = "Verifies the Phase 10 worker_threads, WASM, and controlled WASI boundary report."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("node-runtime-config.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-android-config.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsStableInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-worker-wasi-boundary.json") },
        nodePhase10ReportDir.map { it.file("phase10-worker-wasi-boundary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register("verifyNodeWorkerWasiGate") {
    group = "verification"
    description = "Runs the focused Phase 10 worker/WASI boundary gate on a connected Sony/full profile."

    dependsOn(
        "generateNodePhase10CapabilityTruth",
        "verifyNodePhase10WorkerWasiBoundary",
        "verifyNodeWorkerGate",
        "verifyNodeWasmWasiGate",
        "verifyNodePhase9WasmWasiPolicy",
    )
}

tasks.register<Exec>("verifyNodePhase10DeveloperExperience") {
    group = "verification"
    description = "Verifies the Phase 10 TypeScript, source-map, node:test, and debug-only hot-reload developer experience report."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-developer-experience.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeTypeScriptInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSourceMapStackTraceInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt"))
    inputs.file(nodePhase10NativeCppDir.resolve("node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-developer-experience.json") },
        nodePhase10ReportDir.map { it.file("phase10-developer-experience.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--native-cpp-root",
        nodePhase10NativeCppDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10CrashAdapterCutover") {
    group = "verification"
    description = "Verifies the Phase 10 crash evidence bundle schema and adapter v1 cutover blockers without switching the default runtime path."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-crash-adapter-cutover.js")
    inputs.file(scriptFile)
    inputs.file(nodePhase10NativeCppDir.resolve("node_runtime_api_v1.h"))
    inputs.file(nodePhase10NativeCppDir.resolve("node_runtime_adapter_24_5.cpp"))
    inputs.file(nodePhase10NativeCppDir.resolve("node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-adapter-abi.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-adapter-export.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/NATIVE_CRASH_PIPELINE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CRASH_INTAKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.dir(nodePhase10RuntimeJniLibsDir)
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-crash-adapter-cutover.json") },
        nodePhase10ReportDir.map { it.file("phase10-crash-adapter-cutover.md") },
    )
    outputs.dir(nodePhase10ReportDir.map { it.dir("phase10-crash-evidence-bundle") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--native-cpp-root",
        nodePhase10NativeCppDir.absolutePath,
        "--jni-libs-root",
        nodePhase10RuntimeJniLibsDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodePhase10SecurityCorpus") {
    group = "verification"
    description = "Verifies the Phase 10 security corpus attack-surface evidence without promoting blocked capabilities."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-security-corpus.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/generate-packaged-parity-report.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/generate-capability-truth.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-crash-adapter-cutover.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-adapter-abi.js"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/native/verify-runtime-adapter-export.js"))
    inputs.file(nodePhase10NativeCppDir.resolve("node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PLUGIN_SYSTEM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BRIDGE_V1_1_MODULES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-security-corpus.json") },
        nodePhase10ReportDir.map { it.file("phase10-security-corpus.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--native-cpp-root",
        nodePhase10NativeCppDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodePhase10Summary") {
    group = "verification"
    description = "Writes the Phase 10 aggregate summary from available host reports, example metadata, real npm corpus, and focused device results."

    mustRunAfter("connectedAppDebugAndroidTest")

    dependsOn(
        "verifyNodePhase10RuntimeFreeze",
        "generateNodePhase10CapabilityTruth",
        "verifyNodePhase10ProviderRegistryV2",
        "generateNodePhase10SkipAudit",
        "verifyNodePhase10PackagedParityHost",
        "verifyNodePhase10FocusedGateMatrix",
        "verifyNodePhase10Autojs6PmFixture",
        "verifyNodePhase10LongRunningActivation",
        "verifyNodePhase10WorkerWasiBoundary",
        "verifyNodePhase10DeveloperExperience",
        "verifyNodePhase10CrashAdapterCutover",
        "verifyNodePhase10SecurityCorpus",
        "verifyNodeExamples",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/generate-phase10-summary.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/assets/npm_compat_real/manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/examples.json"))
    inputs.files(
        nodePhase10ReportDir.map { it.file("phase10-runtime-freeze.json") },
        nodePhase10ReportDir.map { it.file("phase10-capability-truth.json") },
        nodePhase10ReportDir.map { it.file("phase10-provider-registry-v2.json") },
        nodePhase10ReportDir.map { it.file("phase10-skip-audit.json") },
        nodePhase10ReportDir.map { it.file("phase10-packaged-parity.json") },
        nodePhase10ReportDir.map { it.file("phase10-focused-gate-matrix.json") },
        nodePhase10ReportDir.map { it.file("phase10-package-manager-boundary.json") },
        nodePhase10ReportDir.map { it.file("phase10-long-running-activation.json") },
        nodePhase10ReportDir.map { it.file("phase10-worker-wasi-boundary.json") },
        nodePhase10ReportDir.map { it.file("phase10-developer-experience.json") },
        nodePhase10ReportDir.map { it.file("phase10-crash-adapter-cutover.json") },
        nodePhase10ReportDir.map { it.file("phase10-security-corpus.json") },
        project.layout.buildDirectory.file("reports/nodejs/examples.json"),
    )
    inputs.dir(project.layout.buildDirectory.dir("outputs/androidTest-results/connected/debug/flavors/app"))
    outputs.files(
        nodePhase10ReportDir.map { it.file("phase10-summary.json") },
        nodePhase10ReportDir.map { it.file("phase10-summary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase10ReportDir.get().asFile.absolutePath,
        "--app-report-dir",
        project.layout.buildDirectory.dir("reports/nodejs").get().asFile.absolutePath,
        "--android-results-dir",
        project.layout.buildDirectory.dir("outputs/androidTest-results/connected/debug/flavors/app").get().asFile.absolutePath,
    )
}

tasks.register("verifyNodeAdapterV1Gate") {
    group = "verification"
    description = "Runs the Phase 10 adapter v1 boundary gate without switching the default runtime execution path."

    dependsOn(
        "verifyNodePhase10RuntimeFreeze",
        "verifyNodeRuntimeAdapterAbi",
        "verifyNodeRuntimeAdapterExport",
    )
}

tasks.register("verifyNodeCrashAdapterCutoverGate") {
    group = "verification"
    description = "Runs the Phase 10 crash evidence bundle and adapter cutover gate while keeping legacy JNI as the default path."

    dependsOn(
        "verifyNodePhase9NativeCrashPipeline",
        "verifyNodePhase10CrashAdapterCutover",
        "verifyNodeAdapterV1Gate",
    )
}

tasks.register("verifyNodeRealNpmGate") {
    group = "verification"
    description = "Runs the focused real npm compatibility corpus gate on a connected device."

    dependsOn("connectedAppDebugAndroidTest")
}

tasks.register("verifyNodePackagedParityGate") {
    group = "verification"
    description = "Runs the Phase 10 packaged APK parity host checks and focused packaged ESM/dynamic-import Sony gate."

    dependsOn(
        "verifyNodePhase10PackagedParityHost",
        "verifyNodePackagedEsmGate",
    )
}

tasks.register("verifyNodePhase10Gate") {
    group = "verification"
    description = "Runs the Phase 10 aggregate gate without promoting blocked capabilities."

    dependsOn(
        "verifyNodePhase10FocusedGateMatrix",
        "verifyNodePhase10ProviderRegistryV2",
        "verifyNodePackagedParityGate",
        "verifyNodePackageManagerGate",
        "verifyNodePhase10LongRunningActivation",
        "verifyNodeMultiInstanceGate",
        "verifyNodePhase10WorkerWasiBoundary",
        "verifyNodeDeveloperExperienceGate",
        "verifyNodeCrashAdapterCutoverGate",
        "verifyNodePhase10SecurityCorpus",
        "verifyNodeRealNpmGate",
        "verifyNodeExamples",
        "generateNodePhase10Summary",
    )
}
