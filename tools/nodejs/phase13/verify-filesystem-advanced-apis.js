#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-filesystem-advanced-apis";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_33_FILESYSTEM_ADVANCED_APIS.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  p13AdvancedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemAdvancedDecisionInstrumentationTest.kt",
  filesystemProfileTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemProfileDecisionInstrumentationTest.kt",
  fileHandleAdvancedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFileHandleAdvancedInstrumentationTest.kt",
  fdApiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFdApiInstrumentationTest.kt",
  opendirTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOpendirInstrumentationTest.kt",
  fsWatchTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFsWatchInstrumentationTest.kt",
  fileHandleWatcherFuzzerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFileHandleWatcherFuzzerInstrumentationTest.kt",
  fsPromisesTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFsPromisesInstrumentationTest.kt",
  safeFileOperationsTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSafeFileOperationsInstrumentationTest.kt",
  scopedFsFuzzerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeScopedFsFuzzerInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  packagedPreflight: "app/embedded-node-packaging-preflight.gradle.kts",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  builtinMatrixGenerator: "tools/nodejs/phase13/generate-node-builtin-parity-matrix.js",
  gradleGate: "app/node-phase13-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 filesystem advanced APIs: " + report.decision.status);
  console.log("Phase 13 filesystem advanced API blockers: " + report.blockers.length);
  console.log("Phase 13 filesystem advanced API policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const desktopSurface = surface(files.surfaceManifest.text, "desktop.network_fs");
  const scenarios = [
    scenario("advanced_surface_profile", "Advanced fs diagnostics are visible without expanding filesystem authority", [
      token(files.nodeBridgeSources, "advancedApis: Object.freeze({"),
      token(files.nodeBridgeSources, 'streams: "partial"'),
      token(files.nodeBridgeSources, 'fileHandle: "partial"'),
      token(files.nodeBridgeSources, 'rawFd: false'),
      token(files.nodeBridgeSources, "watcherLimit: __autojs6_scoped_fs_watcher_limit"),
      token(files.p13AdvancedTest, "advancedFsProfileAndCoreSurfacesStayScopedAndOwned"),
      token(files.p13AdvancedTest, "p13fsadvanced.profile=partial|partial|partial|partial|partial|partial|unsupported"),
      token(files.typesAutojsGlobal, "export interface FilesystemAdvancedApis"),
      token(files.typesAutojsGlobal, "export interface FilesystemProfileLimits"),
      token(files.typescriptSmoke, "autojsProfile.filesystemProfile.advancedApis.watch"),
      token(files.typescriptSmoke, "autojsProfile.filesystemProfile.limits.watcherLimit"),
    ], []),
    scenario("owned_resource_lifecycle", "FileHandle, fd, stream, directory, and watcher surfaces remain scoped and execution-owned", [
      token(files.p13AdvancedTest, "p13fsadvanced.surface=abcdef|stream:abcdef|abcdef|3:bcd|true"),
      token(files.p13AdvancedTest, "assertEquals(0, result.pendingScopedFsStreamCount)"),
      token(files.fileHandleAdvancedTest, "filehandle.denied="),
      token(files.fileHandleAdvancedTest, "filehandle.timeout.recovered=leaked|ok"),
      token(files.fdApiTest, "fdapi.denied="),
      token(files.opendirTest, "opendir.types=function|function|function"),
      token(files.fsWatchTest, "watch.limits="),
      token(files.fsWatchTest, "watch.timeout.recovered=after"),
      token(files.fileHandleWatcherFuzzerTest, "fhwatch.fuzzer.done=true"),
      token(files.fsPromisesTest, "fs.createReadStream"),
      token(files.safeFileOperationsTest, "safe file operations"),
      token(files.scopedFsFuzzerTest, "realpath.symlink_escape"),
    ], [
      "Expose pending watcher/watchFile counts through Kotlin result diagnostics or an equivalent report field.",
    ]),
    scenario("opendir_realpath_packaged_evidence", "Packaged evidence already covers realpath and opendir markers, but not the full advanced fs lifecycle", [
      token(files.packagedSmokeTest, "packaged.fs.realpath=true"),
      token(files.packagedSmokeTest, "packaged.dirent.opendir=true"),
      token(files.packagedSmokeTest, "packaged.url.opendir_url=true"),
      token(files.packagedPreflight, "packaged.fs.realpath=true"),
      token(files.packagedPreflight, "packaged.dirent.opendir=true"),
      token(files.p13Report, "Existing packaged smoke sources and preflight checks already include packaged"),
      token(files.p13Report, "realpath and opendir markers:"),
    ], [
      "Add packaged APK live-smoke coverage and current device-exported evidence for watcher, fd, FileHandle, stream, close-on-destroy, realpath, and opendir behavior.",
      "Re-run advanced-fs suites against each P13-32 expanded-root class once root authority providers are promoted.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-33", [
      token(files.phase13Tasks, "P13-33 partial landing"),
      token(files.p13Report, "P13-33 records desktop fs advanced APIs as partial"),
      token(files.gradleGate, "verifyNodePhase13FilesystemAdvancedApis"),
      token(files.testingDoc, "verifyNodePhase13FilesystemAdvancedApis"),
      token(files.ciGatesDoc, "verifyNodePhase13FilesystemAdvancedApis"),
      token(files.compatibilityProfile, "P13-33 records desktop fs advanced APIs as partial"),
      token(files.securityModel, "P13-33 records desktop fs advanced APIs as partial"),
      token(files.proComparison, "P13-33 records desktop fs advanced APIs as partial"),
      token(files.proAutoxNotes, "P13-33 records desktop fs advanced APIs as partial"),
      token(files.typesReadme, "P13-33 extends the `autojs6:profile.filesystemProfile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial"),
      token(files.builtinMatrixGenerator, "P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial"),
      token(files.capabilityMatrix, "P13-33 advanced filesystem APIs"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-33"), "P13-33 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-33 report must exist."),
    check("surface-present", Boolean(desktopSurface), "`desktop.network_fs` surface must exist."),
    check(
      "surface-partial",
      desktopSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(desktopSurface.status),
      "`desktop.network_fs` must not be promoted by the advanced fs gate.",
    ),
    check(
      "surface-evidence",
      desktopSurface && (desktopSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.network_fs` surface must reference P13-33 evidence.",
    ),
    check(
      "fs-matrix-partial",
      files.desktopMatrix.text.includes("| `fs` | `partial` |") &&
        files.desktopMatrix.text.includes("| `fs/promises` | `partial` |"),
      "Desktop parity matrix must keep fs/fs/promises partial.",
    ),
    check(
      "raw-fd-not-promoted",
      files.nodeBridgeSources.text.includes("rawFd: false") &&
        !files.nodeBridgeSources.text.includes("rawFd: true"),
      "Advanced fs diagnostics must not promote raw native fd exposure.",
    ),
    check(
      "expanded-roots-not-promoted",
      files.nodeBridgeSources.text.includes("additionalRoots: Object.freeze([])") &&
        !files.nodeBridgeSources.text.includes("userAuthorizedRoots: true") &&
        !files.nodeBridgeSources.text.includes("androidSharedStorage: true"),
      "P13-33 must not expand filesystem roots.",
    ),
    check(
      "android-fs-differences-documented",
      files.p13Report.text.includes("## Android Compatibility Notes") &&
        files.p13Report.text.includes("Watcher events may be coalesced") &&
        files.p13Report.text.includes("Recursive `fs.watch` remains denied on Android") &&
        files.p13Report.text.includes("`realpath` resolves after scoped-path policy") &&
        files.desktopMatrix.text.includes("Android watcher coalescing, recursive-watch denial, and realpath differences are documented") &&
        files.builtinMatrixGenerator.text.includes("Android watcher coalescing, recursive-watch denial, and realpath differences are documented"),
      "P13-33 must document Android watcher coalescing, recursive-watch denial, and realpath differences.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-filesystem-advanced-apis-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13FilesystemAdvancedApis",
    task: "P13-33",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: desktopSurface || {
      id: "desktop.network_fs",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      advancedFsDiagnosticExposed: scenarioById(scenarios, "advanced_surface_profile").status === "ready",
      scopedAdvancedCoreEvidenceReady: scenarioById(scenarios, "owned_resource_lifecycle").status !== "missing_evidence",
      packagedRealpathOpendirEvidenceReady: scenarioById(scenarios, "opendir_realpath_packaged_evidence").status !== "missing_evidence",
      packagedAdvancedLifecycleEvidenceReady: false,
      expandedRootAdvancedRerunReady: false,
      androidUnsupportedBehaviorMatrixReady: checkById(checks, "android-fs-differences-documented").passed,
      rawFdPromoted: false,
      desktopSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Phase 13 Filesystem Advanced APIs Verification");
  lines.push("");
  lines.push(`- Task: ${report.task}`);
  lines.push(`- Gate: \`${report.gate}\``);
  lines.push(`- Decision: \`${report.decision.status}\``);
  lines.push(`- Surface: \`${report.surface.id}\` / \`${report.surface.status}\``);
  lines.push(`- Policy violations: ${report.policyViolations.length}`);
  lines.push(`- Remaining blockers: ${report.blockers.length}`);
  lines.push("");
  lines.push("## Scenarios");
  lines.push("");
  for (const scenario of report.scenarios) {
    lines.push(`### ${scenario.id}`);
    lines.push("");
    lines.push(`- Status: \`${scenario.status}\``);
    lines.push(`- ${scenario.title}`);
    if (scenario.remaining.length > 0) {
      lines.push("- Remaining:");
      for (const item of scenario.remaining) {
        lines.push(`  - ${item}`);
      }
    }
    lines.push("");
  }
  lines.push("## Checks");
  lines.push("");
  lines.push("| Check | Result | Message |");
  lines.push("| --- | --- | --- |");
  for (const check of report.checks) {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${check.message} |`);
  }
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push("```json");
  lines.push(JSON.stringify(report.decision, null, 2));
  lines.push("```");
  lines.push("");
  return lines.join("\n");
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === "--repo-root") {
      options.repoRoot = args[++i];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++i];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

main();
