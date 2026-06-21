val nodePhase10ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("verifyNodePhase10RuntimeFreeze") {
    group = "verification"
    description = "Verifies Phase 10 keeps node24_5 as default and does not change libnode.so hashes or moduleSources budgets."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-runtime-freeze.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/runtime-freeze.lock.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/runtime-build.lock.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
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
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
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
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
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
        "verifyNodePhase9IsolationPolicy",
    )
}

tasks.register("verifyNodeWorkerWasiGate") {
    group = "verification"
    description = "Runs the focused Phase 10 worker/WASI boundary gate on a connected Sony/full profile."

    dependsOn(
        "generateNodePhase10CapabilityTruth",
        "verifyNodeWorkerGate",
        "verifyNodeWasmWasiGate",
        "verifyNodePhase9WasmWasiPolicy",
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
    description = "Runs the Phase 10 first-wave aggregate gate without promoting blocked capabilities."

    dependsOn(
        "verifyNodePhase10FocusedGateMatrix",
        "verifyNodePhase10ProviderRegistryV2",
        "verifyNodePackagedParityGate",
        "verifyNodePackageManagerGate",
        "verifyNodeMultiInstanceGate",
        "verifyNodeAdapterV1Gate",
    )
}
