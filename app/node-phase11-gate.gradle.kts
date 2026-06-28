val nodePhase11ReportDir = rootProject.layout.buildDirectory.dir("reports/nodejs")

tasks.register<Exec>("generateNodeRhinoAugmentInventory") {
    group = "verification"
    description = "Generates the Phase 11A Rhino augment API inventory from ScriptRuntime augment registrations."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-augment-api-inventory.json") },
        nodePhase11ReportDir.map { it.file("rhino-augment-api-inventory.md") },
        nodePhase11ReportDir.map { it.file("rhino-augment-source-scan.json") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--require-status-manifest",
        "--fail-on-unresolved",
    )
}

tasks.register<Exec>("generateNodeRhinoNodeCompatGap") {
    group = "verification"
    description = "Generates the Phase 11A Rhino augment to Node bridge/types/docs compatibility gap report."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-node-compat-gap.js")
    val inventoryGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(inventoryGeneratorFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/types/autojs6-node"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-node-compat-gap.json") },
        nodePhase11ReportDir.map { it.file("rhino-node-compat-gap.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--require-status-manifest",
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatInventory") {
    group = "verification"
    description = "Verifies Phase 11A Rhino augment inventory, status manifest coverage, and Node bridge/types/docs evidence."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-inventory.js")
    val statusManifestFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/rhino-compat-status-manifest.js")
    val inventoryGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val gapGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-node-compat-gap.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(statusManifestFile)
    inputs.file(inventoryGeneratorFile)
    inputs.file(gapGeneratorFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/rhino-augment-inventory.schema.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/types/autojs6-node"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-inventory-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-inventory-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodeRhinoCompatMatrix") {
    group = "verification"
    description = "Generates docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md from the Rhino compat inventory and status manifest."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-matrix.js")
    val statusManifestFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/rhino-compat-status-manifest.js")
    val inventoryGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-augment-inventory.js")
    val gapGeneratorFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-node-compat-gap.js")
    val scannerFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/scan-rhino-augment-inventory.js")
    inputs.file(scriptFile)
    inputs.file(statusManifestFile)
    inputs.file(inventoryGeneratorFile)
    inputs.file(gapGeneratorFile)
    inputs.file(scannerFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/types/autojs6-node"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/java/org/autojs/autojs/runtime/api/augment"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    outputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--output",
        rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md").asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatLoader") {
    group = "verification"
    description = "Verifies the Phase 11A explicit Rhino compatibility loader entrypoint and no-default-global policy."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-loader.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeErrorCodes.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/ERROR_CATALOG.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-loader-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-loader-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatBasicFacade") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility basic toast/sleep/clipboard facade."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-basic-facade.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-basic-facade-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-basic-facade-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatFilesFacade") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility scoped files facade."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-files-facade.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/files.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-files-facade-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-files-facade-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatPureUtilities") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility pure utility facades."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-pure-utilities.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/base64.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/colors.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/formatter.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/converter.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/mime.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/nanoid.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/util.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-pure-utilities-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-pure-utilities-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatTextUtilities") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility text utility facades."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-text-utilities.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("app/node-examples.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/opencc.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/pinyin.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/pinyin4j.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-text-utilities-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-text-utilities-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatStorageSqlite") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility storage/sqlite facades."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-storage-sqlite.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/storage.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/storages.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/database.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/sqlite.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/node-examples.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-storage-sqlite-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-storage-sqlite-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatConsoleTimersError") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility console/timers/error debugging facade."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-console-timers-error.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/console.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/timers.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("app/node-examples.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-console-timers-error-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-console-timers-error-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoSelectorDslHandleDesign") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino selector DSL handle design gate."
    mustRunAfter("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-selector-dsl-handle-design.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_SELECTOR_DSL_HANDLE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_SELECTOR_DSL_TYPES_DRAFT.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_SELECTOR_DSL_BRIDGE_GAPS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-selector-dsl-handle-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-selector-dsl-handle-design-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatSelectorAccessibility") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino selector/accessibility compatibility facade."
    mustRunAfter("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-selector-accessibility.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/accessibility.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_SELECTOR_DSL_HANDLE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-selector-accessibility-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-selector-accessibility-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatAppDeviceShell") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino app/device/shell compatibility facade."
    mustRunAfter("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-app-device-shell.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/app.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/device.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/shell.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-app-device-shell-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-app-device-shell-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatImagesFacade") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino images compatibility facade."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-images-facade.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/images.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-images-facade-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-images-facade-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatScreenshotPermission") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino screenshot permission compatibility facade."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-screenshot-permission.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/image.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/media_projection.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-screenshot-permission-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-screenshot-permission-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatOcrBarcodeStrategy") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino OCR compatibility facade and barcode strategy boundary."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-ocr-barcode-strategy.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ocr.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("sample/nodejs/typescript-smoke/src/main.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/README.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-ocr-barcode-strategy-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-ocr-barcode-strategy-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatEventsInputObserverDesign") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino events/keys/input observer compatibility design gate."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-events-input-observer-design.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-events-input-observer-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-events-input-observer-design-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatTasksWorkManagerDesign") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino tasks to WorkManager/user task database migration design gate."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-tasks-work-manager-design.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORK_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BROADCAST_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/work_manager.d.ts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-tasks-work-manager-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-tasks-work-manager-design-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatUiDesign") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino UI compatibility design gate."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-ui-design.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/ui.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-ui-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-ui-design-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatFloatyDesign") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino floaty/overlay compatibility design gate."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-floaty-design.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/FLOATY_NODE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/UI_BRIDGE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/BRIDGE_V1_1_MODULES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-floaty-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-floaty-design-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatThreadsPolicy") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino threads migration policy gate."

    dependsOn("generateNodeRhinoCompatMatrix")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-threads-policy.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/V1_1_WORKER_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/ISOLATION_POLICY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/augment/threads/Threads.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/Threads.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/runtime/api/augment/threads/VolatileDisposeNativeObject.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-threads-policy-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-threads-policy-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoJsonOnlyShimDesign") {
    group = "verification"
    description = "Verifies the Phase 11A explicit Rhino JSON-only shim design gate."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-json-only-shim-design.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_SHIM.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_COMPAT_LOADER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/rhino_compat.d.ts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/types/autojs6-node/index.d.ts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-json-only-shim-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-json-only-shim-design-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatExamples") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compatibility migration examples."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-examples.js")
    inputs.file(scriptFile)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/examples/rhino-compat"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/EXAMPLES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-examples-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-examples-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoDualEngineCompatTests") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino/Node dual-engine compatibility test corpus."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-dual-engine-compat-tests.js")
    inputs.file(scriptFile)
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/nodejs/dual-engine-compat"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_DUAL_ENGINE_COMPAT_TESTS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/MIGRATION_FROM_RHINO.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-dual-engine-compat-tests-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-dual-engine-compat-tests-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatPackagedSmokeHost") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compat packaged APK smoke contract and report schema."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-packaged-smoke.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_COMPAT_PACKAGED_SMOKE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-packaged-smoke-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-packaged-smoke-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register("verifyNodeRhinoCompatPackagedSmoke") {
    group = "verification"
    description = "Runs the focused Phase 11A Rhino compat packaged APK smoke on a connected device."

    dependsOn(
        "verifyNodeRhinoCompatPackagedSmokeHost",
        "connectedAppDebugAndroidTest",
    )
}

tasks.register<Exec>("verifyNodeRhinoCompatResourceCleanupSecurityHost") {
    group = "verification"
    description = "Verifies the Phase 11A Rhino compat resource cleanup/security gate contract."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-rhino-compat-resource-cleanup-security.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeUiBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeDatabaseBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeFetchBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWebSocketBridgeInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/rhino-compat/status-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_UI_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("rhino-compat-resource-cleanup-security-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-resource-cleanup-security-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register("verifyNodeRhinoCompatResourceCleanupSecurity") {
    group = "verification"
    description = "Runs the focused Phase 11A Rhino compat resource cleanup/security gate on a connected device."

    dependsOn(
        "verifyNodeRhinoCompatResourceCleanupSecurityHost",
        "connectedAppDebugAndroidTest",
    )
}

tasks.register<Exec>("generateNodeRhinoCompatPhase11Summary") {
    group = "verification"
    description = "Writes the Phase 11A Rhino augment compatibility summary report."

    dependsOn(
        "generateNodeRhinoAugmentInventory",
        "generateNodeRhinoNodeCompatGap",
        "verifyNodeRhinoCompatInventory",
        "generateNodeRhinoCompatMatrix",
        "verifyNodeRhinoCompatLoader",
        "verifyNodeRhinoCompatBasicFacade",
        "verifyNodeRhinoCompatFilesFacade",
        "verifyNodeRhinoCompatPureUtilities",
        "verifyNodeRhinoCompatTextUtilities",
        "verifyNodeRhinoCompatStorageSqlite",
        "verifyNodeRhinoCompatConsoleTimersError",
        "verifyNodeRhinoSelectorDslHandleDesign",
        "verifyNodeRhinoCompatSelectorAccessibility",
        "verifyNodeRhinoCompatAppDeviceShell",
        "verifyNodeRhinoCompatImagesFacade",
        "verifyNodeRhinoCompatScreenshotPermission",
        "verifyNodeRhinoCompatOcrBarcodeStrategy",
        "verifyNodeRhinoCompatEventsInputObserverDesign",
        "verifyNodeRhinoCompatTasksWorkManagerDesign",
        "verifyNodeRhinoCompatUiDesign",
        "verifyNodeRhinoCompatFloatyDesign",
        "verifyNodeRhinoCompatThreadsPolicy",
        "verifyNodeRhinoJsonOnlyShimDesign",
        "verifyNodeRhinoCompatExamples",
        "verifyNodeRhinoDualEngineCompatTests",
        "verifyNodeRhinoCompatPackagedSmokeHost",
        "verifyNodeRhinoCompatResourceCleanupSecurityHost",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-rhino-compat-phase11-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("rhino-augment-api-inventory.json") },
        nodePhase11ReportDir.map { it.file("rhino-node-compat-gap.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-inventory-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-loader-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-basic-facade-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-files-facade-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-pure-utilities-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-text-utilities-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-storage-sqlite-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-console-timers-error-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-selector-dsl-handle-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-selector-accessibility-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-app-device-shell-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-images-facade-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-screenshot-permission-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-ocr-barcode-strategy-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-events-input-observer-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-tasks-work-manager-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-ui-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-floaty-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-threads-policy-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-json-only-shim-design-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-examples-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-dual-engine-compat-tests-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-packaged-smoke-verification.json") },
        nodePhase11ReportDir.map { it.file("rhino-compat-resource-cleanup-security-verification.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("phase11a-summary.json") },
        nodePhase11ReportDir.map { it.file("phase11a-summary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodeFinalCapabilityTruth") {
    group = "verification"
    description = "Writes build/reports/nodejs/final-capability-truth.json without failing on open Phase 11 blockers."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-capability-truth.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeJavaScriptEngineFactory.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-capability-truth.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalCapabilityTruth") {
    group = "verification"
    description = "Strictly verifies final capability truth; fails until required internal Phase 11 blockers are closed."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-capability-truth.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-capability-truth.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("verifyNodeFinalFocusedGateTopology") {
    group = "verification"
    description = "Verifies the Phase 11 final focused gate task topology is registered."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-focused-gate-topology.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodeFinalAdapterStatus") {
    group = "verification"
    description = "Writes the current Phase 11 adapter status without failing on the surface-only adapter blocker."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-adapter-gate.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalAdapterGate") {
    group = "verification"
    description = "Strictly verifies adapter_v1 readiness for final promotion; fails while the adapter remains surface-only."

    dependsOn("verifyNodeRuntimeAdapterAbi", "verifyNodeRuntimeAdapterExport")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-adapter-gate.js")
    inputs.file(scriptFile)
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("collectNodeCrashBundle") {
    group = "verification"
    description = "Collects the Phase 11 Node crash evidence bundle schema and available native artifacts."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js")
    inputs.file(scriptFile)
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-bundle-collection.json") },
        nodePhase11ReportDir.map { it.file("final-crash-bundle-collection.md") },
    )
    outputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("symbolizeNodeCrash") {
    group = "verification"
    description = "Parses and best-effort symbolizes Phase 11 Node crash evidence."

    dependsOn("collectNodeCrashBundle")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js")
    inputs.file(scriptFile)
    inputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-symbolization.json") },
        nodePhase11ReportDir.map { it.file("final-crash-symbolization.md") },
    )
    outputs.file(nodePhase11ReportDir.map { it.file("final-crash-evidence-bundle/symbolization.json") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeCrashBundle") {
    group = "verification"
    description = "Verifies the Phase 11 Node crash evidence bundle schema."

    dependsOn("symbolizeNodeCrash")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js")
    inputs.file(scriptFile)
    inputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-bundle-verification.json") },
        nodePhase11ReportDir.map { it.file("final-crash-bundle-verification.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("generateNodeFinalCrashStatus") {
    group = "verification"
    description = "Writes the current Phase 11 crash evidence status without failing on unimplemented crash automation."

    dependsOn("verifyNodeCrashBundle")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-crash-gate.js")
    inputs.file(scriptFile)
    inputs.files(
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js"),
    )
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CRASH_INTAKE.md"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.md") },
    )
    outputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalCrashGate") {
    group = "verification"
    description = "Strictly verifies automatic crash evidence and chaos injection readiness."

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-crash-gate.js")
    inputs.file(scriptFile)
    inputs.files(
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/collect-node-crash-bundle.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/symbolize-node-crash.js"),
        rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-node-crash-bundle.js"),
    )
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CRASH_INTAKE.md"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.md") },
    )
    outputs.dir(nodePhase11ReportDir.map { it.dir("final-crash-evidence-bundle") })
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalPackageManagerStatus") {
    group = "verification"
    description = "Writes the current Phase 11 package-manager status without failing on Android install blockers."

    dependsOn(
        "verifyNodePackageManagerGate",
        "verifyNodePhase13AndroidPackageInstallManager",
        "generateNodeFinalCapabilityTruth",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-package-manager-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-package-manager-boundary.json") })
    inputs.file(nodePhase11ReportDir.map { it.file("phase13-android-package-install-manager.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalPackageManagerGate") {
    group = "verification"
    description = "Strict final package-manager gate; blocked until Android-side package installation is implemented."

    dependsOn(
        "verifyNodePackageManagerGate",
        "verifyNodePhase13AndroidPackageInstallManager",
        "generateNodeFinalCapabilityTruth",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-package-manager-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/autojs6-pm.js"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PACKAGE_MANAGER.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-package-manager-boundary.json") })
    inputs.file(nodePhase11ReportDir.map { it.file("phase13-android-package-install-manager.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalLongRunningStatus") {
    group = "verification"
    description = "Writes the current Phase 11 long-running status without failing on packaged/stress blockers."

    dependsOn("verifyNodePhase10LongRunningActivation", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-long-running-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedLifecycleStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/LONG_RUNNING_ACTIVATION.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-long-running-activation.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalLongRunningGate") {
    group = "verification"
    description = "Strict final long-running gate; blocked until packaged long-running and full stress are implemented."

    dependsOn("verifyNodePhase10LongRunningActivation", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-long-running-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-long-running-activation.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeRuntimeBudgetPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeLifecycleCheckpointStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeInteractiveLongRunningStatusStore.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeInteractiveLongRunningInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedLifecycleStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/LONG_RUNNING_SERVICE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/LONG_RUNNING_ACTIVATION.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-long-running-activation.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalMultiInstanceStatus") {
    group = "verification"
    description = "Writes the current Phase 11 multi-instance status without failing on open promotion blockers."

    dependsOn("verifyNodeMultiInstanceGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-multi-instance-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalMultiInstanceGate") {
    group = "verification"
    description = "Strict final multi-instance gate; blocked until a second execution slot is promoted."

    dependsOn("verifyNodeMultiInstanceGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-multi-instance-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/PROCESS_POOL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/MULTI_INSTANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalWorkerDecisionStatus") {
    group = "verification"
    description = "Writes the current Phase 11 Worker/WASI decision status without failing on unresolved conditional decisions."

    dependsOn("verifyNodePhase10WorkerWasiBoundary", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-worker-decision-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-worker-wasi-boundary.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalWorkerDecisionGate") {
    group = "verification"
    description = "Strict final Worker/WASI decision gate; blocked until conditional decisions are resolved."

    dependsOn("verifyNodePhase10WorkerWasiBoundary", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-worker-decision-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-worker-wasi-boundary.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/design/WORKER_THREADS.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/reports/phase9/WASM_WASI.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(nodePhase11ReportDir.map { it.file("phase10-worker-wasi-boundary.json") })
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalProviderStatus") {
    group = "verification"
    description = "Writes the current Phase 11 provider status without failing on missing live-provider evidence."

    dependsOn("verifyNodePhase10ProviderRegistryV2", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-provider-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-provider-registry-v2.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-provider-registry-v2.json") },
        nodePhase11ReportDir.map { it.file("live-provider-summary.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalProviderGate") {
    group = "verification"
    description = "Strict final provider stability gate."

    dependsOn("verifyNodePhase10ProviderRegistryV2", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-provider-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-provider-registry-v2.js"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("embedded-node-packaging-preflight.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt"))
    inputs.file(project.layout.projectDirectory.file("src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-provider-registry-v2.json") },
        nodePhase11ReportDir.map { it.file("live-provider-summary.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalAbiStatus") {
    group = "verification"
    description = "Writes the current Phase 11 ABI status without failing on bootstrap-only runtime artifact evidence."

    dependsOn("verifyNodeRuntimeAdapterAbi", "verifyNodeRuntimeArtifactGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-abi-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(project.layout.projectDirectory.file("node-native-modernization.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("runtime-adapter-abi-v1.json") },
        nodePhase11ReportDir.map { it.file("runtime-adapter-export.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-gate.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-manifest.json") },
        nodePhase11ReportDir.map { it.file("runtime-distribution-policy.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalAbiGate") {
    group = "verification"
    description = "Strict final ABI gate for the required Node runtime ABIs."

    dependsOn("verifyNodeRuntimeAdapterAbi", "verifyNodeRuntimeArtifactGate", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-abi-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/COMPATIBILITY_PROFILE.md"))
    inputs.file(project.layout.projectDirectory.file("node-native-modernization.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_api_v1.h"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_runtime_adapter_24_5.cpp"))
    inputs.file(project.layout.projectDirectory.file("src/main/cpp/node_bridge_jni.cpp"))
    inputs.dir(project.layout.projectDirectory.dir("src/main/jniLibs"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("runtime-adapter-abi-v1.json") },
        nodePhase11ReportDir.map { it.file("runtime-adapter-export.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-gate.json") },
        nodePhase11ReportDir.map { it.file("runtime-artifact-manifest.json") },
        nodePhase11ReportDir.map { it.file("runtime-distribution-policy.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalSecurityStatus") {
    group = "verification"
    description = "Writes the current Phase 11 security/fuzz status without failing on policy-blocked final surfaces."

    dependsOn("verifyNodePhase10SecurityCorpus", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-security-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-security-corpus.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-security-corpus.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalSecurityGate") {
    group = "verification"
    description = "Strict final security/fuzz gate."

    dependsOn("verifyNodePhase10SecurityCorpus", "generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-security-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("tools/nodejs/phase10/verify-security-corpus.js"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/SECURITY_MODEL.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(project.layout.projectDirectory.file("node-phase10-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("phase10-security-corpus.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalSoakStatus") {
    group = "verification"
    description = "Writes the current Phase 11 soak status without failing on missing long-duration soak evidence."

    dependsOn("generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-soak-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/PERFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/quick-soak-8h.json"),
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/final-soak-24h.json"),
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalSoakGate") {
    group = "verification"
    description = "Strict final soak gate; blocked until 24-hour soak evidence exists."

    dependsOn("generateNodeFinalCapabilityTruth")

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/verify-final-soak-gate.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.file(project.layout.projectDirectory.file("node-phase11-gate.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedMemoryLeakStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeResourceStressSnapshot.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeLongRunningStressInstrumentationTest.kt"))
    inputs.file(project.layout.projectDirectory.file("src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/TESTING.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/CI_GATES.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/PERFORMANCE.md"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md"))
    inputs.files(
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/quick-soak-8h.json"),
        rootProject.layout.projectDirectory.file("build/reports/nodejs/final-soak/final-soak-24h.json"),
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}

tasks.register<Exec>("generateNodeFinalIntegrationSummary") {
    group = "verification"
    description = "Writes the Phase 11 final integration summary without failing on open blockers."

    dependsOn(
        "generateNodeFinalCapabilityTruth",
        "verifyNodeFinalFocusedGateTopology",
        "generateNodeFinalAdapterStatus",
        "generateNodeFinalCrashStatus",
        "generateNodeFinalPackageManagerStatus",
        "generateNodeFinalLongRunningStatus",
        "generateNodeFinalMultiInstanceStatus",
        "generateNodeFinalWorkerDecisionStatus",
        "generateNodeFinalSoakStatus",
        "generateNodeFinalProviderStatus",
        "generateNodeFinalAbiStatus",
        "generateNodeFinalSecurityStatus",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-integration-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-integration-summary.json") },
        nodePhase11ReportDir.map { it.file("final-integration-summary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyNodeFinalIntegrationGate") {
    group = "verification"
    description = "Strict final graduation gate; fails until Phase 11 required internal blockers are closed."

    dependsOn(
        "generateNodeFinalCapabilityTruth",
        "verifyNodeFinalFocusedGateTopology",
        "generateNodeFinalAdapterStatus",
        "generateNodeFinalCrashStatus",
        "generateNodeFinalPackageManagerStatus",
        "generateNodeFinalLongRunningStatus",
        "generateNodeFinalMultiInstanceStatus",
        "generateNodeFinalWorkerDecisionStatus",
        "generateNodeFinalSoakStatus",
        "generateNodeFinalProviderStatus",
        "generateNodeFinalAbiStatus",
        "generateNodeFinalSecurityStatus",
    )

    val scriptFile = rootProject.layout.projectDirectory.file("tools/nodejs/phase11/generate-final-integration-summary.js")
    inputs.file(scriptFile)
    inputs.file(rootProject.layout.projectDirectory.file("docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json"))
    inputs.files(
        nodePhase11ReportDir.map { it.file("final-capability-truth.json") },
        nodePhase11ReportDir.map { it.file("final-focused-gate-topology.json") },
        nodePhase11ReportDir.map { it.file("final-adapter-gate.json") },
        nodePhase11ReportDir.map { it.file("final-crash-gate.json") },
        nodePhase11ReportDir.map { it.file("final-package-manager-gate.json") },
        nodePhase11ReportDir.map { it.file("final-long-running-gate.json") },
        nodePhase11ReportDir.map { it.file("final-multi-instance-gate.json") },
        nodePhase11ReportDir.map { it.file("final-worker-decision-gate.json") },
        nodePhase11ReportDir.map { it.file("final-soak-gate.json") },
        nodePhase11ReportDir.map { it.file("final-provider-gate.json") },
        nodePhase11ReportDir.map { it.file("final-abi-gate.json") },
        nodePhase11ReportDir.map { it.file("final-security-gate.json") },
    )
    outputs.files(
        nodePhase11ReportDir.map { it.file("final-integration-summary.json") },
        nodePhase11ReportDir.map { it.file("final-integration-summary.md") },
    )
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir
    commandLine(
        "node",
        scriptFile.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--report-dir",
        nodePhase11ReportDir.get().asFile.absolutePath,
        "--fail-on-blocked",
    )
}
