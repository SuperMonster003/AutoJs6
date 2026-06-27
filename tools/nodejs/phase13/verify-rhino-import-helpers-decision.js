#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-rhino-import-helpers-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_15_IMPORT_HELPERS_DECISION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridge: "app/src/main/cpp/node_bridge_sources.cpp",
  focusedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt",
  rhinoTypes: "docs/nodejs/types/autojs6-node/rhino.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  rhinoDesign: "docs/nodejs/design/RHINO_SHIM.md",
  javaDesign: "docs/nodejs/design/JAVA_INTEROP.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
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
  console.log("Phase 13 Rhino import helpers decision: " + report.decision.status);
  console.log("Phase 13 Rhino import helpers decision blockers: " + report.blockers.length);
  console.log("Phase 13 Rhino import helpers decision policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.entry_java_rhino");
  const scenarios = [
    scenario("stable_errors", "importClass/importPackage have stable explicit module errors and no globals", [
      token(files.nodeBridge, 'importUnsupported: "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED"'),
      token(files.nodeBridge, "function __autojs6_rhino_import_unsupported_error(apiName)"),
      token(files.nodeBridge, "function importClass()"),
      token(files.nodeBridge, "function importPackage()"),
      token(files.focusedTest, "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED"),
      token(files.focusedTest, "globalThis.importClass, undefined"),
      token(files.focusedTest, "globalThis.importPackage, undefined"),
    ]),
    scenario("assignment_migration", "Docs direct users to assignment-style P13-14 Java proxy migration", [
      token(files.p13Report, "Status: ready"),
      token(files.p13Report, "const scope = {}"),
      token(files.p13Report, "rhino.install({ experimental: true, target: scope })"),
      token(files.rhinoDesign, "P13-15"),
      token(files.javaDesign, "P13-15"),
      token(files.compatibilityProfile, "P13-15"),
      token(files.securityModel, "P13-15"),
    ]),
    scenario("types_and_smoke", "Types and smoke sample expose only denied helper signatures", [
      token(files.rhinoTypes, 'ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED'),
      token(files.rhinoTypes, "importClass(value?: unknown, options?: unknown): never"),
      token(files.rhinoTypes, "importPackage(value?: unknown, options?: unknown): never"),
      token(files.typeSmoke, "rhinoImportClassDenied"),
      token(files.typeSmoke, "rhinoImportPackageDenied"),
      token(files.typesReadme, "P13-15"),
    ]),
    scenario("docs_gate", "Phase 13 docs and gate record the P13-15 decision", [
      token(files.phase13Tasks, "P13-15"),
      token(files.phase13Tasks, "verifyNodePhase13RhinoImportHelpersDecision"),
      token(files.gradleGate, "verifyNodePhase13RhinoImportHelpersDecision"),
      token(files.testingDoc, "verifyNodePhase13RhinoImportHelpersDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13RhinoImportHelpersDecision"),
      token(files.proComparison, "P13-15"),
      token(files.proAutoxNotes, "P13-15"),
      token(files.capabilityMatrix, "Rhino import helpers decision"),
      token(files.surfaceManifestDoc, "phase13-rhino-import-helpers-decision"),
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-15"), "P13-15 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-15 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.entry_java_rhino` surface must exist."),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.entry_java_rhino` must not be promoted by the import helper decision.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-rhino-import-helpers-decision")),
      "`pro.entry_java_rhino` surface must reference P13-15 evidence.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-rhino-import-helpers-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13RhinoImportHelpersDecision",
    task: "P13-15",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.entry_java_rhino",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      importHelpersUnsupported: true,
      stableErrorReady: scenarioById(scenarios, "stable_errors").status === "ready",
      migrationDocsReady: scenarioById(scenarios, "assignment_migration").status === "ready",
      typesReady: scenarioById(scenarios, "types_and_smoke").status === "ready",
      proSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return {
    id,
    title,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    remaining: missing,
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
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(stripBom(text));
    return (manifest.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Rhino Import Helpers Decision",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "None.")} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|");
}

main();
