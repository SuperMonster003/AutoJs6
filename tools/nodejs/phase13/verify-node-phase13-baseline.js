#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-baseline-verification";
const SNAPSHOT_SCHEMA = "autojs6-node-phase13-baseline-snapshot-v1";
const SNAPSHOT_PHASE = "Phase 13: Auto.js Pro And Desktop Node Parity Expansion";
const SNAPSHOT_TASK = "P13-01";
const SNAPSHOT_CAPTURED_AT = "2026-06-24";
const SNAPSHOT_SOURCE_REVISION = "8ed1634a51cd931a2fe32f6f1313bfab7724395a";
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
const EXPECTED_INITIAL_OPEN_REQUIRED_BLOCKERS = Object.freeze([
  "android_side_package_installation",
  "crash_evidence_automation",
  "full_heavy_resource_stress",
  "packaged_long_running",
  "second_execution_slot",
]);
const EXPECTED_INITIAL_CLOSED_REQUIRED_BLOCKERS = Object.freeze([
  "adapter_v1_real_execution_path",
]);
const EXPECTED_OPEN_REQUIRED_BLOCKERS = Object.freeze([]);
const EXPECTED_CLOSED_REQUIRED_BLOCKERS = Object.freeze([
  "adapter_v1_real_execution_path",
  "android_side_package_installation",
  "crash_evidence_automation",
  "full_heavy_resource_stress",
  "packaged_long_running",
  "second_execution_slot",
]);

const FILES = {
  baselineSnapshot: "docs/nodejs/phase13/phase13-baseline-snapshot.json",
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
  const snapshot = JSON.parse(files.baselineSnapshot.text);
  const currentVersion = parseProperties(files.version.text);
  const checks = buildChecks(files, snapshot, currentVersion);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-phase13-baseline-verification-v2",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13Baseline",
    historicalBaseline: snapshot,
    current: {
      version: selectProperties(currentVersion, Object.keys(EXPECTED_VERSION)),
    },
    expected: {
      version: EXPECTED_VERSION,
      phase12Tasks: EXPECTED_PHASE12_TASKS,
      phase13Tasks: EXPECTED_P13_TASKS,
      historicalOpenRequiredBlockers: EXPECTED_INITIAL_OPEN_REQUIRED_BLOCKERS,
      historicalClosedRequiredBlockers: EXPECTED_INITIAL_CLOSED_REQUIRED_BLOCKERS,
      currentOpenRequiredBlockers: EXPECTED_OPEN_REQUIRED_BLOCKERS,
      currentClosedRequiredBlockers: EXPECTED_CLOSED_REQUIRED_BLOCKERS,
    },
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      versionLocked: checkById(checks, "historical-version-baseline-locked"),
      historicalSnapshotLocked: checkById(checks, "historical-baseline-snapshot"),
      currentVersionNonRegressing: checkById(checks, "current-version-not-regressed"),
      compatibilityDocsCurrent: checkById(checks, "compatibility-docs-current"),
      historicalBlockersLocked: checkById(checks, "historical-required-blockers-locked"),
      currentBlockersResolved: checkById(checks, "current-required-blocker-state"),
      phase12Complete: checkById(checks, "phase12-summary-complete"),
      phase13Ready: checkById(checks, "phase13-task-plan-ready"),
      blockersImported: checkById(checks, "current-required-blocker-state"),
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

function buildChecks(files, snapshot, currentVersion) {
  const snapshotVersion = snapshot && snapshot.version ? snapshot.version : {};
  const phase12Manifest = JSON.parse(files.phase12Manifest.text);
  const finalBlockers = JSON.parse(files.finalBlockers.text);
  const p12Tasks = parseTaskIds(files.phase12Tasks.text, "P12");
  const p13Tasks = parseTaskIds(files.p13Tasks.text, "P13");
  const requiredInternalBlockers = (finalBlockers.blockers || [])
    .filter((blocker) => blocker.classification === "required_internal");
  const unresolvedRequiredBlockers = requiredInternalBlockers
    .filter((blocker) => blocker.status !== "closed");
  const closedRequiredBlockers = requiredInternalBlockers
    .filter((blocker) => blocker.status === "closed")
    .map((blocker) => blocker.id)
    .sort();
  const phase12StatusCounts = countBy(phase12Manifest.surfaces || [], (surface) => surface.status);
  const p13TaskDuplicates = duplicateValues(p13Tasks);

  return [
    check(
      "historical-baseline-snapshot",
      snapshot.schema === SNAPSHOT_SCHEMA &&
        snapshot.phase === SNAPSHOT_PHASE &&
        snapshot.task === SNAPSHOT_TASK &&
        snapshot.capturedAt === SNAPSHOT_CAPTURED_AT &&
        snapshot.sourceRevision === SNAPSHOT_SOURCE_REVISION &&
        objectFieldsEqual(snapshotVersion, EXPECTED_VERSION) &&
        snapshot.phase12 && snapshot.phase12.taskCount === 37 &&
        snapshot.phase12.completedTaskCount === 36 &&
        snapshot.phase12.notSelectedTaskCount === 1 &&
        snapshot.phase12.openTaskCount === 0 &&
        snapshot.phase12.surfaceCounts && snapshot.phase12.surfaceCounts.total === 13 &&
        snapshot.phase12.surfaceCounts.promoted === 9 &&
        snapshot.phase12.surfaceCounts.providerPoc === 3 &&
        snapshot.phase12.surfaceCounts.designLocked === 1 &&
        snapshot.phase13 && snapshot.phase13.firstTask === "P13-00" &&
        snapshot.phase13.lastTask === "P13-57" && snapshot.phase13.taskCount === 58 &&
        snapshot.requiredInternalBlockers &&
        sameSet(snapshot.requiredInternalBlockers.open || [], EXPECTED_INITIAL_OPEN_REQUIRED_BLOCKERS) &&
        sameSet(snapshot.requiredInternalBlockers.closed || [], EXPECTED_INITIAL_CLOSED_REQUIRED_BLOCKERS) &&
        Array.isArray(snapshot.sourceFiles) &&
        snapshot.sourceFiles.includes(FILES.version) && snapshot.sourceFiles.includes(FILES.p13Tasks) &&
        typeof snapshot.immutabilityPolicy === "string" && snapshot.immutabilityPolicy.includes("must not rewrite"),
      "The immutable Phase 13 entry snapshot must retain its original version, task, surface, blocker, source, and revision metadata.",
    ),
    check(
      "historical-version-baseline-locked",
      includesAll(files.p13Tasks.text, [
        `盘点日期：${SNAPSHOT_CAPTURED_AT}`,
        `\`VERSION_NAME=${snapshotVersion.VERSION_NAME}\``,
        `\`VERSION_BUILD=${snapshotVersion.VERSION_BUILD}\``,
        `\`MIN_SDK_VERSION=${snapshotVersion.MIN_SDK_VERSION}\``,
        `\`TARGET_SDK_VERSION=${snapshotVersion.TARGET_SDK_VERSION}\``,
        `\`COMPILE_SDK_VERSION=${snapshotVersion.COMPILE_SDK_VERSION}\``,
        FILES.baselineSnapshot,
        ":app:verifyNodePhase13Baseline",
      ]),
      "The frozen Phase 13 task-document section must match the immutable entry snapshot.",
    ),
    check(
      "historical-required-blockers-locked",
      snapshot.requiredInternalBlockers &&
        sameSet(snapshot.requiredInternalBlockers.open || [], EXPECTED_INITIAL_OPEN_REQUIRED_BLOCKERS) &&
        sameSet(snapshot.requiredInternalBlockers.closed || [], EXPECTED_INITIAL_CLOSED_REQUIRED_BLOCKERS) &&
        EXPECTED_INITIAL_OPEN_REQUIRED_BLOCKERS.every((id) => files.p13Tasks.text.includes(id)),
      "The immutable entry snapshot and Phase 13 task document must retain the required_internal blocker state imported at Phase 13 start.",
    ),
    check(
      "current-version-not-regressed",
      currentVersionAtLeastSnapshot(currentVersion, snapshotVersion),
      "The live version/build and target/compile SDK must not regress behind the Phase 13 entry snapshot, and minSdk must remain compatible.",
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
      "current-required-blocker-state",
      unresolvedRequiredBlockers.length === 0 &&
        EXPECTED_CLOSED_REQUIRED_BLOCKERS.every((id) => closedRequiredBlockers.includes(id)),
      "The current blocker registry must contain all six expected Phase 13 required_internal blockers and keep every current required_internal blocker closed without rewriting historical entry state.",
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
      "compatibility-docs-current",
      includesAll(files.compatibilityProfile.text, [
        "AutoJs6 Safe Node Profile",
        "NodeRuntimeCapabilities",
        "Feature Flag Registry",
      ]) &&
        includesAll(files.pro9Comparison.text, [
          `\`VERSION_NAME=${currentVersion.VERSION_NAME}\``,
          `\`VERSION_BUILD=${currentVersion.VERSION_BUILD}\``,
          `\`MIN_SDK_VERSION=${currentVersion.MIN_SDK_VERSION}\``,
          `\`TARGET_SDK_VERSION=${currentVersion.TARGET_SDK_VERSION}\``,
          `\`COMPILE_SDK_VERSION=${currentVersion.COMPILE_SDK_VERSION}\``,
          "Auto.js Pro v9",
          "Safe Node Profile",
        ]),
      "Compatibility profile must remain available and the current Pro 9 comparison metadata must match live version.properties.",
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

function selectProperties(properties, keys) {
  return Object.fromEntries(keys.map((key) => [key, properties[key] || ""]));
}

function objectFieldsEqual(actual, expected) {
  return actual && Object.entries(expected).every(([key, value]) => actual[key] === value);
}

function currentVersionAtLeastSnapshot(current, baseline) {
  const currentBuild = unsignedInteger(current.VERSION_BUILD);
  const baselineBuild = unsignedInteger(baseline.VERSION_BUILD);
  const currentMinSdk = unsignedInteger(current.MIN_SDK_VERSION);
  const baselineMinSdk = unsignedInteger(baseline.MIN_SDK_VERSION);
  const currentTargetSdk = unsignedInteger(current.TARGET_SDK_VERSION);
  const baselineTargetSdk = unsignedInteger(baseline.TARGET_SDK_VERSION);
  const currentCompileSdk = unsignedInteger(current.COMPILE_SDK_VERSION);
  const baselineCompileSdk = unsignedInteger(baseline.COMPILE_SDK_VERSION);
  return versionNameAtLeast(current.VERSION_NAME, baseline.VERSION_NAME) &&
    currentBuild !== null && baselineBuild !== null && currentBuild >= baselineBuild &&
    currentMinSdk !== null && baselineMinSdk !== null && currentMinSdk === baselineMinSdk &&
    currentTargetSdk !== null && baselineTargetSdk !== null && currentTargetSdk >= baselineTargetSdk &&
    currentCompileSdk !== null && baselineCompileSdk !== null && currentCompileSdk >= baselineCompileSdk &&
    currentCompileSdk >= currentTargetSdk;
}

function versionNameAtLeast(current, baseline) {
  const currentParts = versionTriplet(current);
  const baselineParts = versionTriplet(baseline);
  if (!currentParts || !baselineParts) {
    return false;
  }
  for (let index = 0; index < currentParts.length; index += 1) {
    if (currentParts[index] !== baselineParts[index]) {
      return currentParts[index] > baselineParts[index];
    }
  }
  return true;
}

function versionTriplet(value) {
  const match = /^(\d+)\.(\d+)\.(\d+)(?:\s|$)/.exec(String(value || "").trim());
  return match ? match.slice(1).map(Number) : null;
}

function unsignedInteger(value) {
  const text = String(value || "").trim();
  return /^\d+$/.test(text) ? Number(text) : null;
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
