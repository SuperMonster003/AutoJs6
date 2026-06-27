#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase12-baseline-verification";
const EXPECTED_RHINO_MODULES = 73;
const EXPECTED_RHINO_API_ENTRIES = 879;
const PHASE11_TASK_COUNT = 32;
const NEXT_PHASE_TASKS = Object.freeze([
  "P12-01",
  "P12-02",
  "P12-03",
  "P12-04",
  "P12-05",
  "P12-06",
  "P12-07",
  "P12-08",
  "P12-09",
]);

const FILES = {
  phase11Tasks: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  phase11Summary: "docs/nodejs/reports/phase11/PHASE11A_SUMMARY.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  finalBlockers: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-phase12-baseline-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatPhase12Baseline",
    expected: {
      rhinoModules: EXPECTED_RHINO_MODULES,
      rhinoApiEntries: EXPECTED_RHINO_API_ENTRIES,
      phase11TaskCount: PHASE11_TASK_COUNT,
      nextPhaseTasks: NEXT_PHASE_TASKS,
    },
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      phase11Complete: checkById(checks, "phase11-all-tasks-complete"),
      phase12Ready: checkById(checks, "phase12-task-plan-ready"),
      manifestCovered: checkById(checks, "status-manifest-counts"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 12 Rhino compat baseline verification: " + report.decision.status);
  console.log("Phase 12 baseline policy violations: " + policyViolations.length);
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
  const manifest = JSON.parse(files.statusManifest.text);
  const phase11Completed = Array.from(files.phase11Tasks.text.matchAll(/- \[x\]\s+(S11-\d+)/g)).map((match) => match[1]);
  const phase12Tasks = Array.from(files.phase12Tasks.text.matchAll(/- \[[ x]\]\s+(P12-\d+[A-Z]?)/g)).map((match) => match[1]);
  const apiEntryCount = (manifest.modules || []).reduce((sum, module) => sum + ((module.apiEntries || []).length), 0);
  const openRequiredBlockers = JSON.parse(files.finalBlockers.text)
    .blockers
    .filter((blocker) => blocker.classification === "required_internal" && blocker.status === "open")
    .map((blocker) => blocker.id);

  return [
    check(
      "phase11-all-tasks-complete",
      phase11Completed.length === PHASE11_TASK_COUNT &&
        phase11Completed.includes("S11-00") &&
        phase11Completed.includes("S11-31"),
      "Phase 11A task plan must keep S11-00 through S11-31 complete.",
    ),
    check(
      "phase11-summary-current-totals",
      includesAll(files.phase11Summary.text, [
        "Rhino augment modules: 73",
        "Rhino API entries: 879",
        "Status: S11-31 published summary.",
      ]),
      "Phase 11A summary must expose the expected module/API totals and published status.",
    ),
    check(
      "phase11-summary-next-phase-anchors",
      NEXT_PHASE_TASKS.every((id) => files.phase11Summary.text.includes(id)),
      "Phase 11A summary must keep P12-01 through P12-09 anchors for next-phase planning.",
    ),
    check(
      "phase12-task-plan-ready",
      files.phase12Tasks.text.includes("# Phase 12: Rhino Compatibility Runtime Promotion Tasks") &&
        phase12Tasks.includes("P12-00") &&
        phase12Tasks.includes("P12-35") &&
        phase12Tasks.length >= 36,
      "Phase 12 task plan must exist and enumerate P12-00 through P12-35.",
    ),
    check(
      "status-manifest-counts",
      Array.isArray(manifest.modules) &&
        manifest.modules.length === EXPECTED_RHINO_MODULES &&
        apiEntryCount === EXPECTED_RHINO_API_ENTRIES,
      "Status manifest must cover 73 Rhino modules and 879 API entries.",
    ),
    check(
      "matrix-counts",
      includesAll(files.matrix.text, [
        "- Rhino modules: 73",
        "- Rhino API entries: 879",
        "### events.keys",
        "### barcode",
        "### floaty",
        "### tasks",
      ]),
      "Compatibility matrix must expose Phase 12 source modules and expected totals.",
    ),
    check(
      "loader-explicit-compat-policy",
      includesAll(files.loaderDesign.text, [
        'require("autojs6:compat")',
        "explicit",
        "default",
        "global",
      ]),
      "Compat loader design must preserve explicit opt-in and no default global policy.",
    ),
    check(
      "final-required-blockers-visible",
      includesAll(files.finalBlockers.text, [
        "crash_evidence_automation",
        "android_side_package_installation",
        "packaged_long_running",
        "second_execution_slot",
        "full_heavy_resource_stress",
      ]) &&
        openRequiredBlockers.length === 5,
      "Final blocker registry must still expose the five open required_internal blockers.",
    ),
  ];
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 12 Rhino Compat Baseline Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-phase12-baseline.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) {
      index += 1;
    }
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

main();
