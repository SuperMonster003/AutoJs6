#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-baseline-verification";
const EXPECTED_VERSION = Object.freeze({
  VERSION_NAME: "6.7.1 Alpha4",
  VERSION_BUILD: "3923",
  MIN_SDK_VERSION: "24",
  TARGET_SDK_VERSION: "36",
  COMPILE_SDK_VERSION: "36",
});
const EXPECTED_PHASE12_TASKS = Object.freeze([
  ...Array.from({ length: 12 }, (_, index) => `P12-${String(index).padStart(2, "0")}`),
  "P12-12A",
  "P12-12B",
  ...Array.from({ length: 23 }, (_, index) => `P12-${String(index + 13).padStart(2, "0")}`),
]);
const EXPECTED_P13_TASKS = Object.freeze(Array.from({ length: 58 }, (_, index) => `P13-${String(index).padStart(2, "0")}`));
const EXPECTED_OPEN_REQUIRED_BLOCKERS = Object.freeze([
  "crash_evidence_automation",
  "android_side_package_installation",
  "packaged_long_running",
  "second_execution_slot",
  "full_heavy_resource_stress",
]);

const FILES = {
  version: "version.properties",
  phase12Summary: "docs/nodejs/reports/phase12/PHASE12_SUMMARY.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  phase12Manifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  finalBlockers: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  p13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  integrationPlan: "docs/nodejs/INTEGRATION_PLAN.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  pro9Comparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-phase13-baseline-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13Baseline",
    expected: {
      version: EXPECTED_VERSION,
      phase12Tasks: EXPECTED_PHASE12_TASKS,
      phase13Tasks: EXPECTED_P13_TASKS,
      openRequiredBlockers: EXPECTED_OPEN_REQUIRED_BLOCKERS,
    },
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      versionLocked: checkById(checks, "version-properties-current"),
      phase12Complete: checkById(checks, "phase12-summary-complete"),
      phase13Ready: checkById(checks, "phase13-task-plan-ready"),
      blockersImported: checkById(checks, "required-blockers-imported"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 baseline verification: " + report.decision.status);
  console.log("Phase 13 baseline policy violations: " + policyViolations.length);
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
  const version = parseProperties(files.version.text);
  const phase12Manifest = JSON.parse(files.phase12Manifest.text);
  const finalBlockers = JSON.parse(files.finalBlockers.text);
  const p12Tasks = parseTaskIds(files.phase12Tasks.text, "P12");
  const p13Tasks = parseTaskIds(files.p13Tasks.text, "P13");
  const openRequiredBlockers = (finalBlockers.blockers || [])
    .filter((blocker) => blocker.classification === "required_internal" && blocker.status === "open")
    .map((blocker) => blocker.id)
    .sort();
  const phase12StatusCounts = countBy(phase12Manifest.surfaces || [], (surface) => surface.status);
  const p13TaskDuplicates = duplicateValues(p13Tasks);

  return [
    check(
      "version-properties-current",
      Object.entries(EXPECTED_VERSION).every(([key, value]) => version[key] === value),
      "version.properties must match the Phase 13 documented baseline.",
    ),
    check(
      "phase12-summary-complete",
      includesAll(files.phase12Summary.text, [
        "Status: P12-35 Phase 12 summary published.",
        "- Total tasks: 37",
        "- Completed tasks: 36",
        "- Not selected branch tasks: 1",
        "- Open tasks: 0",
        "## Phase 13 Recommendations",
      ]),
      "Phase 12 summary must be published and carry Phase 13 recommendations.",
    ),
    check(
      "phase12-task-checklist-complete",
      EXPECTED_PHASE12_TASKS.every((task) => p12Tasks.includes(task)) &&
        files.phase12Tasks.text.includes("- [x] P12-35:") &&
        files.phase12Tasks.text.includes("- [x] P12-34:"),
      "Phase 12 task checklist must enumerate P12-00 through P12-35 and keep final tasks complete.",
    ),
    check(
      "phase12-surface-counts",
      phase12Manifest.schema === "autojs6-node-rhino-compat-phase12-surface-manifest-v1" &&
        (phase12Manifest.surfaces || []).length === 13 &&
        phase12StatusCounts.promoted === 9 &&
        phase12StatusCounts.provider_poc === 3 &&
        phase12StatusCounts.design_locked === 1,
      "Phase 12 surface manifest must retain 13 surfaces with 9 promoted, 3 provider_poc, and 1 design_locked.",
    ),
    check(
      "required-blockers-imported",
      sameSet(openRequiredBlockers, EXPECTED_OPEN_REQUIRED_BLOCKERS) &&
        EXPECTED_OPEN_REQUIRED_BLOCKERS.every((id) => files.p13Tasks.text.includes(id)),
      "Phase 13 checklist must import the five open required_internal blockers from the final registry.",
    ),
    check(
      "phase13-task-plan-ready",
      files.p13Tasks.text.includes("# Phase 13: Auto.js Pro And Desktop Node Parity Expansion Tasks") &&
        p13Tasks.length === EXPECTED_P13_TASKS.length &&
        p13TaskDuplicates.length === 0 &&
        EXPECTED_P13_TASKS.every((task) => p13Tasks.includes(task)),
      "Phase 13 task plan must enumerate P13-00 through P13-57 exactly once.",
    ),
    check(
      "phase13-profile-model-visible",
      includesAll(files.p13Tasks.text, [
        "safe_default",
        "pro_compat_opt_in",
        "desktop_compat_opt_in",
        "debug_unsafe_lab",
        "permanently_unsupported",
      ]),
      "Phase 13 checklist must define the profile promotion vocabulary.",
    ),
    check(
      "integration-plan-points-to-phase13",
      includesAll(files.integrationPlan.text, [
        "PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
        "Auto.js Pro compatibility",
        "desktop Node.js compatibility",
        "explicit opt-in profiles",
      ]),
      "Integration plan must point to Phase 13 and describe opt-in Pro/Desktop parity.",
    ),
    check(
      "compatibility-docs-still-current",
      includesAll(files.compatibilityProfile.text, [
        "AutoJs6 Safe Node Profile",
        "NodeRuntimeCapabilities",
        "Feature Flag Registry",
      ]) &&
        includesAll(files.pro9Comparison.text, [
          "`VERSION_BUILD=3923`",
          "Auto.js Pro v9",
          "Safe Node Profile",
        ]),
      "Compatibility profile and Pro 9 comparison must remain available as Phase 13 inputs.",
    ),
  ];
}

function parseProperties(text) {
  const output = {};
  text.split(/\r?\n/).forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      return;
    }
    const eq = trimmed.indexOf("=");
    if (eq >= 0) {
      output[trimmed.slice(0, eq)] = trimmed.slice(eq + 1);
    }
  });
  return output;
}

function parseTaskIds(text, prefix) {
  const pattern = new RegExp(`- \\[[ x]\\]\\s+(${prefix}-\\d{2}[A-Z]?)`, "g");
  return Array.from(text.matchAll(pattern)).map((match) => match[1]);
}

function duplicateValues(values) {
  const counts = countBy(values, (value) => value);
  return Object.keys(counts).filter((value) => counts[value] > 1);
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function sameSet(actual, expected) {
  return actual.length === expected.length && expected.every((value) => actual.includes(value));
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
    "# Phase 13 Baseline Verification",
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
      console.log("Usage: node verify-node-phase13-baseline.js [--repo-root dir] [--report-dir dir]");
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
