#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-baseline-verification";
const EXPECTED_VERSION = Object.freeze({
  VERSION_NAME: "6.7.1 Alpha4",
  VERSION_BUILD: "3923",
  MIN_SDK_VERSION: "24",
  TARGET_SDK_VERSION: "36",
  COMPILE_SDK_VERSION: "36",
});
const EXPECTED_P14_TASKS = Object.freeze(Array.from({ length: 40 }, (_, index) => `P14-${String(index).padStart(2, "0")}`));
const EXPECTED_PHASE13_FUTURE_TASKS = Object.freeze([
  "P13-17",
  "P13-18",
  "P13-19",
  "P13-20",
  "P13-21",
  "P13-22",
  "P13-25",
  "P13-26",
  "P13-34",
]);

const FILES = {
  version: "version.properties",
  phase13SummaryDoc: "docs/nodejs/reports/phase13/PHASE13_SUMMARY.md",
  phase13SummaryJson: "build/reports/nodejs/phase13-summary.json",
  phase13Closeout: "docs/nodejs/reports/phase13/PHASE13_CLOSEOUT_BREAKDOWN.md",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  finalBlockers: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const phase13Summary = JSON.parse(files.phase13SummaryJson.text);
  const finalBlockers = JSON.parse(files.finalBlockers.text);
  const p14Tasks = parseTaskIds(files.phase14Tasks.text, "P14");
  const report = {
    schema: "autojs6-node-phase14-baseline-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14Baseline",
    expected: {
      version: EXPECTED_VERSION,
      phase14Tasks: EXPECTED_P14_TASKS,
      importedPhase13FutureTasks: EXPECTED_PHASE13_FUTURE_TASKS,
    },
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    phase13ReleaseGate: {
      status: phase13Summary.releaseGate && phase13Summary.releaseGate.status,
      openFinalBlockers: countArray(phase13Summary.releaseGate && phase13Summary.releaseGate.openFinalBlockers),
      requiredInternalOpen: countArray(phase13Summary.releaseGate && phase13Summary.releaseGate.requiredInternalOpen),
      releaseBlockingReports: countArray(phase13Summary.releaseGate && phase13Summary.releaseGate.releaseBlockingReports),
      futurePromotionReports: countArray(phase13Summary.releaseGate && phase13Summary.releaseGate.futurePromotionReports),
      futurePromotionReportTasks: (phase13Summary.releaseGate && phase13Summary.releaseGate.futurePromotionReportTasks) || [],
    },
    finalBlockerCounts: countFinalBlockers(finalBlockers.blockers || []),
    phase14TaskCounts: {
      total: p14Tasks.length,
      duplicates: duplicateValues(p14Tasks),
    },
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      phase13CloseoutReady: checkById(checks, "phase13-summary-release-gate-ready"),
      phase14PlanReady: checkById(checks, "phase14-task-plan-ready"),
      blockersImported: checkById(checks, "phase13-future-backlog-imported"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 baseline verification: " + report.decision.status);
  console.log("Phase 14 baseline policy violations: " + policyViolations.length);
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
  const phase13Summary = JSON.parse(files.phase13SummaryJson.text);
  const finalBlockers = JSON.parse(files.finalBlockers.text);
  const p14Tasks = parseTaskIds(files.phase14Tasks.text, "P14");
  const p14Duplicates = duplicateValues(p14Tasks);
  const requiredInternalOpen = (finalBlockers.blockers || [])
    .filter((blocker) => blocker.classification === "required_internal" && blocker.status !== "closed")
    .map((blocker) => blocker.id)
    .sort();
  const futureTasks = ((phase13Summary.releaseGate && phase13Summary.releaseGate.futurePromotionReportTasks) || []).slice().sort();
  return [
    check(
      "version-properties-current",
      Object.entries(EXPECTED_VERSION).every(([key, value]) => version[key] === value),
      "version.properties must match the v1.2 branch baseline inherited from v1.1.",
    ),
    check(
      "phase13-summary-release-gate-ready",
      phase13Summary.schema === "autojs6-node-phase13-summary-v1" &&
        phase13Summary.releaseGate &&
        phase13Summary.releaseGate.status === "ready" &&
        countArray(phase13Summary.releaseGate.openFinalBlockers) === 0 &&
        countArray(phase13Summary.releaseGate.requiredInternalOpen) === 0 &&
        countArray(phase13Summary.releaseGate.releaseBlockingReports) === 0 &&
        countArray(phase13Summary.releaseGate.futurePromotionReports) === EXPECTED_PHASE13_FUTURE_TASKS.length,
      "Phase 14 baseline requires the generated Phase 13 summary release gate to remain ready with only future-promotion blockers.",
    ),
    check(
      "phase13-summary-doc-current",
      includesAll(files.phase13SummaryDoc.text, [
        "Status: P13-57 v1.1 closeout gate is ready.",
        "- Status: `ready`",
        "- Open final blockers: 0",
        "- Release-blocking reports: 0",
        "- Future promotion reports with blockers: 9",
      ]),
      "Canonical Phase 13 summary markdown must still describe the v1.1 ready closeout state.",
    ),
    check(
      "phase13-closeout-breakdown-current",
      includesAll(files.phase13Closeout.text, [
        "Status: v1.1 closeout gate is ready",
        "`generateNodePhase13Summary` | `ready`",
        "Future promotion reports with blockers | 9",
      ]),
      "Phase 13 closeout breakdown must record the ready v1.1 gate and future-promotion backlog.",
    ),
    check(
      "final-required-blockers-closed",
      requiredInternalOpen.length === 0,
      "Required internal final blockers must stay closed before Phase 14 promotion work starts.",
    ),
    check(
      "phase13-future-backlog-imported",
      sameSet(futureTasks, EXPECTED_PHASE13_FUTURE_TASKS) &&
        EXPECTED_PHASE13_FUTURE_TASKS.every((task) => files.phase14Tasks.text.includes(task)),
      "Phase 14 task plan must import the nine Phase 13 future-promotion task ids.",
    ),
    check(
      "phase14-task-plan-ready",
      files.phase14Tasks.text.includes("# Phase 14 Node v1.2 Provider Promotion Tasks") &&
        p14Tasks.length === EXPECTED_P14_TASKS.length &&
        p14Duplicates.length === 0 &&
        EXPECTED_P14_TASKS.every((task) => p14Tasks.includes(task)),
      "Phase 14 task plan must enumerate P14-00 through P14-39 exactly once.",
    ),
    check(
      "phase14-profile-and-closeout-vocabulary",
      includesAll(files.phase14Tasks.text, [
        "safe_default",
        "pro_compat_opt_in",
        "desktop_compat_opt_in",
        "debug_unsafe_lab",
        "permanently_unsupported",
        "release_blocking",
        "future_promotion",
        "generateNodePhase14Summary",
      ]),
      "Phase 14 plan must define profile vocabulary, blocker classification, and the final summary gate.",
    ),
    check(
      "phase14-readme-points-to-checklist",
      includesAll(files.phase14Readme.text, [
        "Phase 14 Reports",
        "PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
        "v1.2 promotion work",
      ]),
      "Phase 14 README must point to the primary v1.2 checklist.",
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
  const pattern = new RegExp(`- \\[[ x]\\]\\s+(${prefix}-\\d{2})`, "g");
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

function countArray(value) {
  return Array.isArray(value) ? value.length : 0;
}

function countFinalBlockers(blockers) {
  const counts = {};
  blockers.forEach((blocker) => {
    const key = `${blocker.classification}:${blocker.status}`;
    counts[key] = (counts[key] || 0) + 1;
  });
  return counts;
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
    "# Phase 14 Baseline Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Phase 13 release gate: \`${report.phase13ReleaseGate.status}\``,
    `- Phase 13 future-promotion tasks: ${report.phase13ReleaseGate.futurePromotionReportTasks.map((task) => `\`${task}\``).join(", ")}`,
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
      console.log("Usage: node verify-node-phase14-baseline.js [--repo-root dir] [--report-dir dir]");
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
