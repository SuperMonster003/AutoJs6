#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-baseline-verification";
const EXPECTED_BRANCH = "feature/nodejs-runtime-v1.3";
const EXPECTED_P15_TASKS = Object.freeze(
  Array.from({ length: 28 }, (_, index) => `P15-${String(index).padStart(2, "0")}`),
);
const EXPECTED_BACKLOG_TASKS = Object.freeze([
  "P14-07",
  "P14-08",
  "P14-09",
  "P14-10",
  "P14-11",
  "P14-12",
  "P14-13",
  "P14-14",
  "P14-15",
  "P14-17",
  "P14-18",
  "P14-19",
  "P14-20",
  "P14-21",
  "P14-22",
  "P14-23",
  "P14-24",
  "P14-25",
  "P14-26",
]);

const FILES = {
  gitHead: ".git/HEAD",
  phase14CloseoutReport: "build/reports/nodejs/phase14-closeout-handoff.json",
  phase14SummaryReport: "build/reports/nodejs/phase14-summary.json",
  phase14CloseoutDoc: "docs/nodejs/reports/phase14/PHASE14_CLOSEOUT_BREAKDOWN.md",
  phase14FutureBacklog: "docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json",
  phase14NextBranchHandoff: "docs/nodejs/reports/phase14/PHASE14_NEXT_BRANCH_HANDOFF.md",
  finalBlockers: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  baselinePlan: "docs/nodejs/phase15/node-v1-3-baseline.json",
  baselineDoc: "docs/nodejs/phase15/PHASE15_NODE_V1_3_BASELINE.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
  appGradle: "app/build.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const closeout = parseJson(files.phase14CloseoutReport.text);
  const summary = parseJson(files.phase14SummaryReport.text);
  const backlog = parseJson(files.phase14FutureBacklog.text);
  const blockerRegistry = parseJson(files.finalBlockers.text);
  const p15Tasks = parseTaskIds(files.phase15Tasks.text, "P15");
  const report = {
    schema: "autojs6-node-phase15-baseline-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15Baseline",
    task: "P15-00",
    expected: {
      branch: EXPECTED_BRANCH,
      phase15Tasks: EXPECTED_P15_TASKS,
      importedPhase14BacklogTasks: EXPECTED_BACKLOG_TASKS,
    },
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    branch: currentBranch(files.gitHead.text),
    phase14Closeout: {
      status: statusOf(closeout),
      nextBranch: closeout.decision && closeout.decision.nextBranch,
      runtimeAuthorityChanged: closeout.decision && closeout.decision.runtimeAuthorityChanged,
      finalBlockersReopened: closeout.decision && closeout.decision.finalBlockersReopened,
    },
    phase14Summary: {
      status: statusOf(summary),
      completedTasks: summary.taskCounts && summary.taskCounts.completed,
      openTasks: summary.taskCounts && summary.taskCounts.open,
      futurePromotionReports: summary.summaryGate &&
        Array.isArray(summary.summaryGate.futurePromotionReports) ?
        summary.summaryGate.futurePromotionReports.length :
        null,
    },
    finalBlockerCounts: countFinalBlockers(blockerRegistry.blockers || []),
    phase15TaskCounts: {
      total: p15Tasks.length,
      duplicates: duplicateValues(p15Tasks),
    },
    importedBacklog: {
      classification: backlog.classification,
      taskCount: Array.isArray(backlog.tasks) ? backlog.tasks.length : 0,
      reportCount: Array.isArray(backlog.futurePromotionReports) ? backlog.futurePromotionReports.length : 0,
      tasks: Array.isArray(backlog.tasks) ? backlog.tasks.map((task) => task.id) : [],
    },
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      phase14CloseoutReady: checkById(checks, "phase14-closeout-ready"),
      branchReady: checkById(checks, "branch-ready"),
      backlogImported: checkById(checks, "phase14-backlog-imported"),
      phase15PlanReady: checkById(checks, "phase15-task-plan-ready"),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 15 baseline verification: " + report.decision.status);
  console.log("Phase 15 baseline policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
  files.gitHead = { relative: ".git/HEAD", text: readGitHead(repoRoot) };
  return files;
}

function buildChecks(files) {
  const branch = currentBranch(files.gitHead.text);
  const closeout = parseJson(files.phase14CloseoutReport.text);
  const summary = parseJson(files.phase14SummaryReport.text);
  const backlog = parseJson(files.phase14FutureBacklog.text);
  const blockerRegistry = parseJson(files.finalBlockers.text);
  const baselinePlan = parseJson(files.baselinePlan.text);
  const p15Tasks = parseTaskIds(files.phase15Tasks.text, "P15");
  const p15Duplicates = duplicateValues(p15Tasks);
  const backlogTasks = Array.isArray(backlog.tasks) ? backlog.tasks.map((task) => task.id) : [];
  const openFinalBlockers = (blockerRegistry.blockers || [])
    .filter((blocker) => ["open", "decision_required"].includes(blocker.status))
    .map((blocker) => blocker.id);
  return [
    check(
      "branch-ready",
      branch === EXPECTED_BRANCH,
      `Current branch must be ${EXPECTED_BRANCH}.`,
    ),
    check(
      "phase14-closeout-ready",
      statusOf(closeout) === "ready" &&
        closeout.decision &&
        closeout.decision.closeoutDecision === "phase14_closeout_handoff_ready" &&
        closeout.decision.nextBranch === EXPECTED_BRANCH &&
        closeout.decision.finalBlockersReopened === false &&
        closeout.decision.runtimeAuthorityChanged === false,
      "Phase 15 baseline requires a ready Phase 14 closeout handoff pointing to v1.3.",
    ),
    check(
      "phase14-summary-ready",
      statusOf(summary) === "ready" &&
        summary.decision &&
        summary.decision.phase14CloseoutHandoffReady === true &&
        summary.decision.zeroOpenFinalBlockers === true &&
        summary.decision.zeroPolicyViolations === true &&
        summary.decision.zeroReleaseBlockingReports === true,
      "Generated Phase 14 summary must remain ready with closeout handoff state.",
    ),
    check(
      "phase14-closeout-doc-current",
      includesAll(files.phase14CloseoutDoc.text, [
        "Status: v1.2 closeout handoff ready",
        "`verifyNodePhase14CloseoutHandoff` | `ready`",
        "Future promotion reports with blockers | 19",
      ]),
      "Phase 14 closeout breakdown must describe the ready v1.2 handoff.",
    ),
    check(
      "phase14-backlog-imported",
      backlog.schema === "autojs6-node-phase14-future-backlog-v1" &&
        backlog.classification === "v1.3 future promotion backlog" &&
        backlog.nextBranch === EXPECTED_BRANCH &&
        sameSet(backlogTasks, EXPECTED_BACKLOG_TASKS) &&
        Array.isArray(backlog.futurePromotionReports) &&
        backlog.futurePromotionReports.length === EXPECTED_BACKLOG_TASKS.length,
      "Phase 15 must import the 19 Phase 14 future backlog tasks for v1.3.",
    ),
    check(
      "phase14-next-branch-handoff-current",
      includesAll(files.phase14NextBranchHandoff.text, [
        EXPECTED_BRANCH,
        "v1.3 future promotion backlog",
        "provider_poc_not_promoted",
        "do not promote raw Android object parity",
      ]),
      "Phase 14 next-branch handoff must point to the v1.3 backlog policy.",
    ),
    check(
      "final-blockers-not-reopened",
      openFinalBlockers.length === 0 &&
        blockerRegistry.phase14Closeout &&
        blockerRegistry.phase14Closeout.status === "ready" &&
        blockerRegistry.phase14Closeout.openFinalBlockers === 0,
      "Final blocker registry must keep final blockers closed before Phase 15 starts.",
    ),
    check(
      "baseline-plan-ready",
      baselinePlan.schema === "autojs6-node-phase15-baseline-plan-v1" &&
        baselinePlan.task === "P15-00" &&
        baselinePlan.status === "ready" &&
        baselinePlan.branch === EXPECTED_BRANCH &&
        baselinePlan.runtimeAuthorityChanged === false,
      "P15-00 baseline plan must describe the v1.3 baseline gate.",
    ),
    check(
      "phase15-task-plan-ready",
      files.phase15Tasks.text.includes("# Phase 15 Node v1.3 Provider Promotion Tasks") &&
        p15Tasks.length === EXPECTED_P15_TASKS.length &&
        p15Duplicates.length === 0 &&
        EXPECTED_P15_TASKS.every((task) => p15Tasks.includes(task)) &&
        files.phase15Tasks.text.includes("- [x] P15-00: Record Phase 15 baseline") &&
        EXPECTED_BACKLOG_TASKS.every((task) => files.phase15Tasks.text.includes(task)),
      "Phase 15 task plan must enumerate P15-00 through P15-27 and import every Phase 14 backlog task.",
    ),
    check(
      "phase15-readme-and-gradle-wiring",
      files.phase15Readme.text.includes(":app:verifyNodePhase15Baseline") &&
        files.phase15Gradle.text.includes("verifyNodePhase15Baseline") &&
        files.phase15Gradle.text.includes("verify-node-phase15-baseline.js") &&
        files.appGradle.text.includes("node-phase15-gate.gradle.kts"),
      "Phase 15 README, Gradle gate, and app build must reference the baseline gate.",
    ),
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Baseline Verification",
    "",
    `Status: ${report.decision.status}`,
    "",
    "## Decision",
    "",
    `- Branch ready: \`${report.decision.branchReady}\``,
    `- Phase 14 closeout ready: \`${report.decision.phase14CloseoutReady}\``,
    `- Backlog imported: \`${report.decision.backlogImported}\``,
    `- Phase 15 plan ready: \`${report.decision.phase15PlanReady}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Imported Backlog",
    "",
    `- Classification: \`${report.importedBacklog.classification}\``,
    `- Tasks: ${report.importedBacklog.tasks.map((task) => `\`${task}\``).join(", ")}`,
    "",
    "## Checks",
    "",
    "| Check | Passed | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.passed}\` | ${escapeTable(entry.message)} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- ${entry}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function currentBranch(headText) {
  const trimmed = (headText || "").trim();
  const prefix = "ref: refs/heads/";
  return trimmed.startsWith(prefix) ? trimmed.slice(prefix.length) : trimmed;
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
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const entry = checks.find((checkItem) => checkItem.id === id);
  return entry ? entry.passed : false;
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch {
    return "";
  }
}

function readGitHead(repoRoot) {
  const dotGit = path.join(repoRoot, ".git");
  const dotGitText = read(dotGit).trim();
  if (dotGitText.startsWith("gitdir:")) {
    const gitDirRaw = dotGitText.slice("gitdir:".length).trim();
    const gitDir = path.isAbsolute(gitDirRaw) ? gitDirRaw : path.resolve(repoRoot, gitDirRaw);
    return read(path.join(gitDir, "HEAD"));
  }
  return read(path.join(dotGit, "HEAD"));
}

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\r?\n/g, "<br>");
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase15-baseline.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
