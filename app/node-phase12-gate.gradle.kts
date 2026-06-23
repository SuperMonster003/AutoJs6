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
