#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase16-baseline-verification";
const EXPECTED_BRANCH = "feature/nodejs-runtime-v1.4";
const EXPECTED_P16_TASKS = Object.freeze(
  Array.from({ length: 28 }, (_, index) => `P16-${String(index).padStart(2, "0")}`),
);
const EXPECTED_BACKLOG_TASKS = Object.freeze([
  "P15-04",
  "P15-05",
  "P15-06",
  "P15-07",
  "P15-08",
  "P15-09",
  "P15-10",
  "P15-12",
  "P15-13",
  "P15-14",
  "P15-15",
  "P15-16",
  "P15-17",
  "P15-18",
  "P15-19",
  "P15-20",
  "P15-21",
]);

const FILES = {
  gitHead: ".git/HEAD",
  phase15SummaryReport: "build/reports/nodejs/phase15-summary.json",
  phase15SummaryDoc: "docs/nodejs/reports/phase15/PHASE15_SUMMARY.md",
  phase15FutureBacklog: "docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json",
  phase15NextBranchHandoff: "docs/nodejs/reports/phase15/PHASE15_NEXT_BRANCH_HANDOFF.md",
  baselinePlan: "docs/nodejs/phase16/node-v1-4-baseline.json",
  baselineDoc: "docs/nodejs/phase16/PHASE16_NODE_V1_4_BASELINE.md",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  phase16Gradle: "app/node-phase16-gate.gradle.kts",
  appGradle: "app/build.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const summary = parseJson(files.phase15SummaryReport.text);
  const backlog = parseJson(files.phase15FutureBacklog.text);
  const p16Tasks = parseTaskIds(files.phase16Tasks.text, "P16");
  const report = {
    schema: "autojs6-node-phase16-baseline-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase16Baseline",
    task: "P16-00",
    expected: {
      branch: EXPECTED_BRANCH,
      phase16Tasks: EXPECTED_P16_TASKS,
      importedPhase15BacklogTasks: EXPECTED_BACKLOG_TASKS,
    },
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    branch: currentBranch(files.gitHead.text),
    phase15Summary: phase15SummaryState(summary, files.phase15SummaryDoc.text),
    importedBacklog: {
      classification: backlog.classification,
      suggestedNextBranch: backlog.suggestedNextBranch,
      openTaskCount: Array.isArray(backlog.openTasks) ? backlog.openTasks.length : 0,
      futurePromotionReportCount: Array.isArray(backlog.futurePromotionReports) ? backlog.futurePromotionReports.length : 0,
      tasks: futurePromotionTaskIds(backlog),
    },
    phase16TaskCounts: {
      total: p16Tasks.length,
      duplicates: duplicateValues(p16Tasks),
    },
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      phase15SummaryReady: checkById(checks, "phase15-summary-ready"),
      branchReady: checkById(checks, "branch-ready"),
      backlogImported: checkById(checks, "phase15-backlog-imported"),
      phase16PlanReady: checkById(checks, "phase16-task-plan-ready"),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 16 baseline verification: " + report.decision.status);
  console.log("Phase 16 baseline policy violations: " + policyViolations.length);
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
  const summary = parseJson(files.phase15SummaryReport.text);
  const backlog = parseJson(files.phase15FutureBacklog.text);
  const baselinePlan = parseJson(files.baselinePlan.text);
  const p16Tasks = parseTaskIds(files.phase16Tasks.text, "P16");
  const p16Duplicates = duplicateValues(p16Tasks);
  const backlogTasks = futurePromotionTaskIds(backlog);
  return [
    check(
      "branch-ready",
      branch === EXPECTED_BRANCH,
      `Current branch must be ${EXPECTED_BRANCH}.`,
    ),
    check(
      "phase15-summary-ready",
      phase15SummaryReady(summary, files.phase15SummaryDoc.text),
      "Phase 16 baseline requires a ready Phase 15 summary handoff pointing to v1.4.",
    ),
    check(
      "phase15-summary-doc-current",
      includesAll(files.phase15SummaryDoc.text, [
        "Status: P15-27 v1.3 summary handoff is ready.",
        "Release-blocking reports: 0",
        "Reports with policy violations: 0",
        "feature/nodejs-runtime-v1.4",
      ]),
      "Phase 15 summary docs must describe the ready v1.4 handoff baseline.",
    ),
    check(
      "phase15-backlog-imported",
      backlog.schema === "autojs6-node-phase15-future-backlog-v1" &&
        backlog.classification === "v1.4 future promotion backlog" &&
        backlog.suggestedNextBranch === EXPECTED_BRANCH &&
        Array.isArray(backlog.releaseBlockingReports) &&
        backlog.releaseBlockingReports.length === 0 &&
        sameSet(backlogTasks, EXPECTED_BACKLOG_TASKS),
      "Phase 16 must import the 17 Phase 15 future promotion reports for v1.4.",
    ),
    check(
      "phase15-next-branch-handoff-current",
      includesAll(files.phase15NextBranchHandoff.text, [
        EXPECTED_BRANCH,
        "v1.4 future promotion backlog",
        "provider_poc_not_promoted",
        "host_contract_ready",
        "stable_denied",
        "do not promote raw Android object parity",
      ]),
      "Phase 15 next-branch handoff must point to the v1.4 backlog policy.",
    ),
    check(
      "baseline-plan-ready",
      baselinePlan.schema === "autojs6-node-phase16-baseline-plan-v1" &&
        baselinePlan.task === "P16-00" &&
        baselinePlan.status === "ready" &&
        baselinePlan.branch === EXPECTED_BRANCH &&
        baselinePlan.runtimeAuthorityChanged === false,
      "P16-00 baseline plan must describe the v1.4 baseline gate.",
    ),
    check(
      "phase16-task-plan-ready",
      files.phase16Tasks.text.includes("# Phase 16 Node v1.4 Provider Promotion Tasks") &&
        p16Tasks.length === EXPECTED_P16_TASKS.length &&
        p16Duplicates.length === 0 &&
        EXPECTED_P16_TASKS.every((task) => p16Tasks.includes(task)) &&
        files.phase16Tasks.text.includes("- [x] P16-00: Record Phase 16 baseline") &&
        EXPECTED_BACKLOG_TASKS.every((task) => files.phase16Tasks.text.includes(task)),
      "Phase 16 task plan must enumerate P16-00 through P16-27 and import every Phase 15 backlog task.",
    ),
    check(
      "phase16-readme-and-gradle-wiring",
      files.phase16Readme.text.includes(":app:verifyNodePhase16Baseline") &&
        files.phase16Gradle.text.includes("verifyNodePhase16Baseline") &&
        files.phase16Gradle.text.includes("verify-node-phase16-baseline.js") &&
        files.appGradle.text.includes("node-phase16-gate.gradle.kts"),
      "Phase 16 README, Gradle gate, and app build must reference the baseline gate.",
    ),
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 16 Baseline Verification",
    "",
    `Status: ${report.decision.status}`,
    "",
    "## Decision",
    "",
    `- Branch ready: \`${report.decision.branchReady}\``,
    `- Phase 15 summary ready: \`${report.decision.phase15SummaryReady}\``,
    `- Backlog imported: \`${report.decision.backlogImported}\``,
    `- Phase 16 plan ready: \`${report.decision.phase16PlanReady}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Imported Backlog",
    "",
    `- Classification: \`${report.importedBacklog.classification}\``,
    `- Suggested next branch: \`${report.importedBacklog.suggestedNextBranch}\``,
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

function futurePromotionTaskIds(backlog) {
  return Array.isArray(backlog.futurePromotionReports)
    ? backlog.futurePromotionReports.map((report) => report.task).filter(Boolean)
    : [];
}

function phase15SummaryReady(summary, summaryDocText) {
  const reportReady =
    statusOf(summary) === "ready" &&
    summary.decision &&
    summary.decision.summaryDecision === "phase15_summary_handoff_ready" &&
    summary.decision.nextBranch === EXPECTED_BRANCH &&
    summary.decision.zeroPolicyViolations === true &&
    summary.decision.zeroReleaseBlockingReports === true &&
    summary.decision.runtimeAuthorityChanged === false;
  const docsReady = includesAll(summaryDocText, [
    "Status: P15-27 v1.3 summary handoff is ready.",
    "Release-blocking reports: 0",
    "Reports with policy violations: 0",
    EXPECTED_BRANCH,
  ]);
  return reportReady || docsReady;
}

function phase15SummaryState(summary, summaryDocText) {
  return {
    status: statusOf(summary),
    docsReady: phase15SummaryReady(summary, summaryDocText),
    nextBranch: summary.decision && summary.decision.nextBranch,
    zeroPolicyViolations: summary.decision && summary.decision.zeroPolicyViolations,
    zeroReleaseBlockingReports: summary.decision && summary.decision.zeroReleaseBlockingReports,
    runtimeAuthorityChanged: summary.decision && summary.decision.runtimeAuthorityChanged,
  };
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
      console.log("Usage: node verify-node-phase16-baseline.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
