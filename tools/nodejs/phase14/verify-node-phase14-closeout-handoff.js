#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-closeout-handoff";
const FUTURE_BACKLOG_TASKS = Object.freeze([
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
const SOURCE_REPORTS = Object.freeze([
  ["summary", "phase14-summary.json"],
  ["rcChecklist", "phase14-release-candidate-checklist.json"],
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_39_CLOSEOUT_HANDOFF.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/node-v1-2-closeout-handoff.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NODE_V1_2_CLOSEOUT_HANDOFF.md",
  closeoutBreakdown: "docs/nodejs/reports/phase14/PHASE14_CLOSEOUT_BREAKDOWN.md",
  futureBacklog: "docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json",
  nextBranchHandoff: "docs/nodejs/reports/phase14/PHASE14_NEXT_BRANCH_HANDOFF.md",
  summaryDoc: "docs/nodejs/reports/phase14/PHASE14_SUMMARY.md",
  rcChecklist: "docs/nodejs/releases/NODE_PROFILE_V1_2_RC_CHECKLIST.md",
  releaseNotesDraft: "docs/nodejs/releases/NODE_PROFILE_V1_2_RELEASE_NOTES_DRAFT.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  blockerRegistryDoc: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 closeout handoff: " + report.decision.status);
  console.log("Phase 14 closeout handoff blockers: " + report.blockers.length);
  console.log("Phase 14 closeout handoff policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([key, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([key, {
      relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
      exists: fs.existsSync(file),
      text: read(file),
    }]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const summary = parseJson(files.summary.text);
  const rcChecklistReport = parseJson(files.rcChecklist.text);
  const backlog = parseJson(files.futureBacklog.text);
  const blockerRegistry = parseJson(files.blockerRegistry.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const docsSurface = (manifest.surfaces || []).find((entry) => entry.id === "docs.examples.final_gate") || null;
  const summaryOpenTasks = Array.isArray(summary.openFutureWork && summary.openFutureWork.openTasks)
    ? summary.openFutureWork.openTasks.map((task) => task.id).sort()
    : [];
  const backlogTasks = Array.isArray(backlog.tasks) ? backlog.tasks.map((task) => task.id).sort() : [];
  const finalBlockers = Array.isArray(blockerRegistry.blockers) ? blockerRegistry.blockers : [];
  const openFinalBlockers = finalBlockers.filter((entry) => ["open", "decision_required"].includes(entry.status));
  const scenarios = [
    scenario("plan_and_source_reports", "P14-39 plan and source reports are ready", [
      value("planSchema", plan.schema === "autojs6-node-phase14-closeout-handoff-plan-v1"),
      value("planTask", plan.task === "P14-39"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "phase14_closeout_handoff_ready"),
      value("gradleTask", plan.gradleTask === ":app:verifyNodePhase14CloseoutHandoff"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("summaryReady", statusOf(summary) === "ready"),
      value("rcReady", statusOf(rcChecklistReport) === "ready"),
    ]),
    scenario("summary_closeout_state", "Summary records P14-39 complete and keeps future backlog visible", [
      value("p14TaskComplete", Array.isArray(summary.tasks) &&
        summary.tasks.some((task) => task.id === "P14-39" && task.status === "completed")),
      value("handoffReady", summary.decision && summary.decision.phase14CloseoutHandoffReady === true),
      value("zeroOpenFinal", summary.decision && summary.decision.zeroOpenFinalBlockers === true),
      value("zeroPolicy", summary.decision && summary.decision.zeroPolicyViolations === true),
      value("zeroReleaseBlocking", summary.decision && summary.decision.zeroReleaseBlockingReports === true),
      value("openTasksMatchBacklog", sameSet(summaryOpenTasks, FUTURE_BACKLOG_TASKS)),
    ]),
    scenario("closeout_docs", "Closeout breakdown and handoff note expose the release-gating closeout state", [
      token(files.closeoutBreakdown, "Status: v1.2 closeout handoff ready"),
      token(files.closeoutBreakdown, ":app:verifyNodePhase14CloseoutHandoff"),
      token(files.closeoutBreakdown, "| Open final blockers | 0 |"),
      token(files.closeoutBreakdown, "| Release-blocking reports | 0 |"),
      token(files.closeoutBreakdown, "| Future promotion reports with blockers | 19 |"),
      token(files.nextBranchHandoff, "feature/nodejs-runtime-v1.3"),
      token(files.nextBranchHandoff, "v1.3 future promotion backlog"),
      token(files.nextBranchHandoff, "do not promote raw Android object parity"),
    ]),
    scenario("future_backlog_export", "Future backlog export exactly carries the v1.3 provider backlog", [
      value("backlogSchema", backlog.schema === "autojs6-node-phase14-future-backlog-v1"),
      value("backlogTask", backlog.task === "P14-39"),
      value("backlogClassification", backlog.classification === "v1.3 future promotion backlog"),
      value("backlogTasks", sameSet(backlogTasks, FUTURE_BACKLOG_TASKS)),
      value("reportCount", Array.isArray(backlog.futurePromotionReports) && backlog.futurePromotionReports.length === 19),
      value("noP14_39", !backlogTasks.includes("P14-39")),
    ]),
    scenario("final_blocker_registry_update", "Final blocker registry records Phase 14 without reopening blockers", [
      value("registryCloseout", blockerRegistry.phase14Closeout &&
        blockerRegistry.phase14Closeout.status === "ready" &&
        blockerRegistry.phase14Closeout.openFinalBlockers === 0 &&
        blockerRegistry.phase14Closeout.releaseBlockingReports === 0),
      value("openFinalBlockers", openFinalBlockers.length === 0),
      token(files.blockerRegistryDoc, "Phase 14 closeout update"),
      token(files.blockerRegistryDoc, "Phase 14 did not reopen final blockers"),
    ]),
    scenario("docs_and_gradle_wiring", "Docs, manifest, Testing, CI, Security, Error Catalog, and Gradle expose P14-39", [
      value("surfaceEvidence", docsSurface &&
        (docsSurface.tasks || []).includes("P14-39") &&
        (docsSurface.evidence || []).includes("docs/nodejs/phase14/node-v1-2-closeout-handoff.json") &&
        (docsSurface.evidence || []).includes("docs/nodejs/reports/phase14/PHASE14_CLOSEOUT_BREAKDOWN.md") &&
        (docsSurface.evidence || []).includes(":app:verifyNodePhase14CloseoutHandoff")),
      token(files.surfaceManifestDoc, "P14-39 closes Phase 14 for v1.2 release-gating handoff"),
      token(files.phase14Tasks, "- [x] P14-39: Close Phase 14 and prepare the next branch handoff."),
      token(files.phase14Tasks, "Closeout handoff gate deployed:"),
      token(files.phase14Report, "P14-39 decision: `phase14_closeout_handoff_ready`"),
      token(files.phase14Readme, ":app:verifyNodePhase14CloseoutHandoff"),
      token(files.testing, "Phase 14 v1.2 closeout handoff gate"),
      token(files.ciGates, "verifyNodePhase14CloseoutHandoff"),
      token(files.securityModel, "P14-39 records the v1.2 closeout handoff"),
      token(files.errorCatalog, "P14-39 closeout handoff"),
      token(files.gradle14, "verifyNodePhase14CloseoutHandoff"),
      token(files.gradle14, "verify-node-phase14-closeout-handoff.js"),
    ]),
  ];
  const checks = [
    check("plan-and-source-reports", scenarios[0].status === "ready", "P14-39 plan and source reports must be ready."),
    check("summary-closeout-state", scenarios[1].status === "ready", "Summary must record P14-39 complete with future backlog visible."),
    check("closeout-docs", scenarios[2].status === "ready", "Closeout docs must expose release-gating handoff state."),
    check("future-backlog-export", scenarios[3].status === "ready", "Future backlog export must match open provider backlog."),
    check("final-blocker-registry-update", scenarios[4].status === "ready", "Final blocker registry must record Phase 14 without reopened blockers."),
    check("docs-and-gradle-wiring", scenarios[5].status === "ready", "Docs and Gradle must expose the P14-39 closeout gate."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({
    id: entry.id,
    message: entry.message,
  }));
  return {
    schema: "autojs6-node-phase14-closeout-handoff-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-39",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, {
      path: file.relative,
      exists: file.exists,
    }])),
    checks,
    scenarios,
    releaseGatingState: {
      openFinalBlockers: openFinalBlockers.length,
      policyViolations: summary.policyViolations ? summary.policyViolations.length : 0,
      releaseBlockingReports: summary.summaryGate && Array.isArray(summary.summaryGate.releaseBlockingReports)
        ? summary.summaryGate.releaseBlockingReports.length
        : -1,
      futureBacklogTasks: backlogTasks,
    },
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      closeoutDecision: "phase14_closeout_handoff_ready",
      p14TaskComplete: policyViolations.length === 0,
      summaryGateReady: statusOf(summary) === "ready",
      rcChecklistReady: statusOf(rcChecklistReport) === "ready",
      finalBlockersReopened: openFinalBlockers.length > 0,
      futureBacklogExported: sameSet(backlogTasks, FUTURE_BACKLOG_TASKS),
      nextBranch: "feature/nodejs-runtime-v1.3",
      runtimeAuthorityChanged: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Closeout Handoff",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Closeout decision: \`${report.decision.closeoutDecision}\``,
    `- Summary gate ready: \`${report.decision.summaryGateReady}\``,
    `- RC checklist ready: \`${report.decision.rcChecklistReady}\``,
    `- Final blockers reopened: \`${report.decision.finalBlockersReopened}\``,
    `- Future backlog exported: \`${report.decision.futureBacklogExported}\``,
    `- Next branch: \`${report.decision.nextBranch}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Release-Gating State",
    "",
    `- Open final blockers: ${report.releaseGatingState.openFinalBlockers}`,
    `- Policy violations: ${report.releaseGatingState.policyViolations}`,
    `- Release-blocking reports: ${report.releaseGatingState.releaseBlockingReports}`,
    `- Future backlog tasks: ${report.releaseGatingState.futureBacklogTasks.map((task) => `\`${task}\``).join(", ")}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return { id, description, status: missing.length === 0 ? "ready" : "missing_evidence", checks, missing };
}

function value(id, ok) {
  return { id, ok: Boolean(ok), message: `missing value check: ${id}` };
}

function token(file, expected) {
  return {
    id: `${file.relative}:${expected}`,
    ok: file.exists && file.text.includes(expected),
    message: `missing evidence token: ${expected} in ${file.relative}`,
  };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
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

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase14-closeout-handoff.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
