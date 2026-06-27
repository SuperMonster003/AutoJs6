#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-filesystem-profile-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_32_FILESYSTEM_PROFILE_DECISION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  p13FilesystemTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemProfileDecisionInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  scopedFsFuzzerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeScopedFsFuzzerInstrumentationTest.kt",
  safeFileOperationsTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSafeFileOperationsInstrumentationTest.kt",
  fileHandleAdvancedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFileHandleAdvancedInstrumentationTest.kt",
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
  console.log("Phase 13 filesystem profile decision: " + report.decision.status);
  console.log("Phase 13 filesystem profile blockers: " + report.blockers.length);
  console.log("Phase 13 filesystem profile policy violations: " + report.policyViolations.length);
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
    scenario("safe_profile_scoped_boundary", "Safe profile keeps scoped working-directory filesystem semantics", [
      token(files.nodeBridgeSources, "scopedFs: true"),
      token(files.nodeBridgeSources, "filesystemProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'mode: "scoped_working_directory"'),
      token(files.nodeBridgeSources, "additionalRoots: Object.freeze([])"),
      token(files.p13FilesystemTest, "filesystemProfileKeepsSafeScopeAndRejectsUnpromotedRoots"),
      token(files.p13FilesystemTest, "p13fs.profile=true|true|scoped_working_directory"),
      token(files.p13FilesystemTest, "/sdcard/AutoJs6/p13-32.txt"),
      token(files.p13FilesystemTest, "realpathEscape"),
      token(files.securityRegressionTest, "security.scoped_fs.ready=true"),
      token(files.scopedFsFuzzerTest, "read.symlink_escape"),
      token(files.safeFileOperationsTest, "statfs absolute"),
      token(files.fileHandleAdvancedTest, "filehandle.denied="),
      token(files.typesAutojsGlobal, "export interface FilesystemProfile"),
      token(files.typescriptSmoke, "autojsProfile.filesystemProfile.additionalRoots"),
    ], []),
    scenario("expanded_roots_deferred", "Pro/Desktop filesystem roots require explicit authority before promotion", [
      token(files.p13Report, "No user-authorized root"),
      token(files.p13Report, "SAF"),
      token(files.p13Report, "app-private"),
      token(files.compatibilityProfile, "P13-32 records filesystem profile expansion as partial"),
      token(files.securityModel, "P13-32 records filesystem profile expansion as partial"),
      token(files.proComparison, "P13-32 records filesystem profile expansion as partial"),
      token(files.proAutoxNotes, "P13-32 records filesystem profile expansion as partial"),
    ], [
      "Implement a filesystem root authority provider for user-authorized directories, SAF/document roots, and app-private roots.",
      "Define manifest/project metadata, user grant persistence, revocation, diagnostics, and rollback behavior for expanded roots.",
      "Re-run symlink, realpath, `..`, NUL, `/proc`, `/sys`, and `/dev` audits against every promoted root class.",
    ]),
    scenario("docs_gate_and_matrix", "docs, desktop matrix, release matrix, and Gradle gate record P13-32", [
      token(files.phase13Tasks, "P13-32 partial filesystem profile gate"),
      token(files.gradleGate, "verifyNodePhase13FilesystemProfileDecision"),
      token(files.testingDoc, "verifyNodePhase13FilesystemProfileDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13FilesystemProfileDecision"),
      token(files.typesReadme, "P13-32 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-32 records scoped filesystem profile"),
      token(files.builtinMatrixGenerator, "P13-32 records scoped filesystem profile"),
      token(files.capabilityMatrix, "P13-32 filesystem profile decision"),
    ], [
      "P13-33 must separately decide advanced desktop fs APIs that are still unavailable or platform-limited.",
      "Packaged expanded-root smoke evidence is still required before any Pro/Desktop root promotion.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-32"), "P13-32 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-32 report must exist."),
    check("surface-present", Boolean(desktopSurface), "`desktop.network_fs` surface must exist."),
    check(
      "surface-partial",
      desktopSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(desktopSurface.status),
      "`desktop.network_fs` must not be promoted by the filesystem profile decision gate.",
    ),
    check(
      "surface-evidence",
      desktopSurface && (desktopSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.network_fs` surface must reference P13-32 evidence.",
    ),
    check(
      "fs-matrix-partial",
      files.desktopMatrix.text.includes("| `fs` | `partial` |") &&
        files.desktopMatrix.text.includes("| `fs/promises` | `partial` |"),
      "Desktop parity matrix must keep fs/fs/promises partial, not promoted to full desktop filesystem.",
    ),
    check(
      "additional-roots-empty",
      files.nodeBridgeSources.text.includes("additionalRoots: Object.freeze([])") &&
        !files.nodeBridgeSources.text.includes("userAuthorizedRoots: true") &&
        !files.nodeBridgeSources.text.includes("androidSharedStorage: true"),
      "P13-32 must not silently enable extra filesystem roots.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-filesystem-profile-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13FilesystemProfileDecision",
    task: "P13-32",
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
      safeProfileScoped: scenarioById(scenarios, "safe_profile_scoped_boundary").status === "ready",
      filesystemProfileDiagnosticExposed: files.nodeBridgeSources.text.includes("filesystemProfile: Object.freeze({"),
      expandedRootsPromoted: false,
      userAuthorizedRootsPromoted: false,
      safRootsPromoted: false,
      appPrivateRootsPromoted: false,
      androidSharedStoragePromoted: false,
      packagedExpandedRootEvidenceReady: false,
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
  lines.push("# Phase 13 Filesystem Profile Decision Verification");
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
