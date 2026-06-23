#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-floaty-overlay-decision-verification";

const FILES = {
  decision: "docs/nodejs/design/FLOATY_OVERLAY_PHASE12_DECISION.md",
  rhinoFloaty: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  floatyNode: "docs/nodejs/design/FLOATY_NODE.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-floaty-overlay-decision-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatFloatyOverlayDecision",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      selectedPath: checkById(checks, "decision-provider-path"),
      statusManifest: checkById(checks, "status-manifest-p12-11"),
      surfaceManifest: checkById(checks, "surface-manifest-evidence"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat floaty overlay decision verification: " + report.decision.status);
  console.log("Rhino compat floaty overlay policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files) {
  const statusManifest = JSON.parse(files.statusManifest.text);
  const floatyModule = (statusManifest.modules || []).find((module) => module.id === "floaty");
  const entries = new Map((floatyModule && floatyModule.apiEntries || []).map((entry) => [entry.id, entry]));
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const floatySurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "floaty.overlay");
  const matrixFloatyRow = findMatrixRow(files.matrix.text, "floaty");
  return [
    check(
      "decision-provider-path",
      includesAll(files.decision.text, [
        "P12-11",
        "Selected path: Provider path",
        "Rejection path: not selected for Phase 12",
        "P12-12A",
        "JSON-only overlay provider",
        "permanent non-goal",
      ]) &&
        !files.decision.text.includes("Selected path: Rejection path"),
      "P12-11 decision must select only the Provider path and name P12-12A as the follow-up.",
    ),
    check(
      "decision-acceptance-coverage",
      includesAll(files.decision.text, [
        "Permission:",
        "Foreground disclosure:",
        "Packaged manifest:",
        "closeAll/destroy cleanup:",
        "Raw window object denial:",
        "overlay provider v1 contract",
        "SYSTEM_ALERT_WINDOW",
        "foreground notification",
        "WindowManager.LayoutParams",
        "FloatyService",
        "NativeView",
        "service destroy",
        "permission revocation",
        "process death",
      ]),
      "Decision document must cover permission, foreground disclosure, packaged manifest, closeAll/destroy cleanup, and raw window object denial.",
    ),
    check(
      "docs-updated",
      includesAll(files.rhinoFloaty.text, ["P12-11", "Provider path", "P12-12A", "JSON-only overlay provider", "blocked_by_design"]) &&
        includesAll(files.floatyNode.text, ["P12-11", "Provider path", "P12-12A", "fake-provider facade POC", "permanent non-goal"]) &&
        includesAll(files.migration.text, ["P12-11", "Provider path", "P12-12A", "P12-13", "P12-14"]) &&
        includesAll(files.securityModel.text, ["P12-11", "Provider path", "P12-12A locks", "ui.overlay", "packaged manifest", "cleanup contract"]),
      "Floaty design, migration, and security docs must record the P12-11 Provider path decision.",
    ),
    check(
      "status-manifest-p12-11",
      floatyModule &&
        floatyModule.status === "unsupported" &&
        floatyModule.decision === "s11_24_floaty_overlay_design_gate" &&
        includesAll(JSON.stringify(floatyModule), [
          "P12-11 provider path selected",
          "P12-12A overlay provider v1 contract locked",
          "exact Rhino floaty.window/rawWindow raw-wrapper semantics remain blocked by design",
          "P12-12A defines closeAll",
          "no silent service-process grant/emulation",
        ]) &&
        entryIs(entries, "floaty.window", "blocked_by_design", "s11_24_xml_window_wrapper_blocked") &&
        entryIs(entries, "floaty.rawWindow", "blocked_by_design", "s11_24_raw_overlay_window_blocked") &&
        entryIs(entries, "floaty.getClip", "renamed", "s11_24_clipboard_replacement_no_overlay"),
      "Status manifest must preserve S11 raw-window decisions while adding P12-11 Provider path notes for module, permission, closeAll, and raw windows.",
    ),
    check(
      "matrix-updated",
      includesAll(files.matrixGenerator.text, [
        "P12-12A overlay provider v1 contract locked",
        "Implement P12-13 overlay fake provider POC",
      ]) &&
        includesAll(matrixFloatyRow, [
          "| 53 | floaty | floaty | unsupported |",
          "blocked_by_design:2",
          "renamed:1",
          "unsupported:5",
          "Implement P12-13 overlay fake provider POC",
        ]),
      "Generated matrix must point floaty to the P12-13 provider POC after the P12-11/P12-12A provider path gates.",
    ),
    check(
      "surface-manifest-evidence",
      floatySurface &&
        floatySurface.status === "design_locked" &&
        (floatySurface.tasks || []).includes("P12-11") &&
        (floatySurface.tasks || []).includes("P12-12A") &&
        includesAll(JSON.stringify(floatySurface), [
          "Provider path",
          "P12-12A",
          "FLOATY_OVERLAY_PHASE12_DECISION.md",
          "verify-rhino-compat-floaty-overlay-decision.js",
          "verifyNodeRhinoCompatFloatyOverlayDecision",
        ]) &&
        includesAll(files.surfaceDocs.text, ["Current Design-Locked Surfaces", "floaty.overlay", "P12-11", "Provider path", "P12-12A"]),
      "Phase 12 surface manifest must record P12-11 Provider path evidence without promoting a runtime provider yet.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-11/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("FLOATY_OVERLAY_PHASE12_DECISION.md") &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatFloatyOverlayDecision") &&
        files.phase12Tasks.text.includes("Decision：Provider path") &&
        files.phase12Tasks.text.includes("P12-12A"),
      "Phase 12 task plan must mark P12-11 complete with decision and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatFloatyOverlayDecision") &&
        files.gradle.text.includes("verify-rhino-compat-floaty-overlay-decision.js") &&
        files.gradle.text.includes("FLOATY_OVERLAY_PHASE12_DECISION.md") &&
        files.gradle.text.includes("RHINO_FLOATY_COMPATIBILITY.md"),
      "Gradle must register verifyNodeRhinoCompatFloatyOverlayDecision.",
    ),
  ];
}

function entryIs(entries, id, status, decision) {
  const entry = entries.get(id);
  return Boolean(entry) && entry.status === status && entry.decision === decision;
}

function findMatrixRow(markdown, moduleId) {
  return markdown.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((checkItem) => checkItem.id === id) || null;
}

function includesAll(text, needles) {
  return needles.every((needle) => text.includes(needle));
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-floaty-overlay-decision.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Floaty Overlay Decision Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
  ];
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  return lines.join("\n") + "\n";
}

main();
