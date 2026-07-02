#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-summary";
const DEFAULT_DOCS_OUTPUT = "docs/nodejs/reports/phase15/PHASE15_SUMMARY.md";
const DEFAULT_BACKLOG_OUTPUT = "docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json";
const DEFAULT_HANDOFF_OUTPUT = "docs/nodejs/reports/phase15/PHASE15_NEXT_BRANCH_HANDOFF.md";
const NEXT_BRANCH = "feature/nodejs-runtime-v1.4";
const REQUIRED_TASKS = Object.freeze(
  Array.from({ length: 28 }, (_, index) => `P15-${String(index).padStart(2, "0")}`),
);
const NON_BLOCKING_REPORT_BLOCKERS = new Set(["future_promotion", "external", "intentional_non_goal"]);
const REQUIRED_CORE_REPORTS = Object.freeze([
  "phase15-baseline-verification",
  "phase15-surface-manifest-verification",
  "phase15-promotion-contracts",
  "phase15-disclosure-rollback-audit",
  "phase15-ui-automation-media-doc-sync",
  "phase15-task-device-media-doc-sync",
  "phase15-security-regression-corpus",
  "phase15-packaged-aggregate-smoke",
  "phase15-type-declaration-profiles",
  "phase15-migration-pro-comparison",
]);
const REQUIRED_REPORT_BASENAMES = Object.freeze([
  "phase15-accessibility-promotion",
  "phase15-baseline-verification",
  "phase15-disclosure-rollback-audit",
  "phase15-image-ocr-promotion",
  "phase15-intent-broadcast-promotion",
  "phase15-media-projection-promotion",
  "phase15-mediastore-playback-decision",
  "phase15-migration-pro-comparison",
  "phase15-notification-promotion",
  "phase15-overlay-oem-matrix",
  "phase15-overlay-provider-promotion",
  "phase15-packaged-aggregate-smoke",
  "phase15-packaged-broadcast-smoke",
  "phase15-packaged-live-ui-promotion",
  "phase15-packaged-overlay-smoke",
  "phase15-packaged-recorder-smoke",
  "phase15-packaged-task-smoke",
  "phase15-promotion-contracts",
  "phase15-recorder-promotion",
  "phase15-security-regression-corpus",
  "phase15-settings-power-promotion",
  "phase15-surface-manifest-verification",
  "phase15-task-database-promotion",
  "phase15-task-device-media-doc-sync",
  "phase15-task-schema-rollback",
  "phase15-type-declaration-profiles",
  "phase15-ui-automation-media-doc-sync",
]);

const FILES = {
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Report: "docs/nodejs/reports/phase15/P15_27_PHASE15_SUMMARY_HANDOFF.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  planJson: "docs/nodejs/phase15/node-v1-3-summary-handoff.json",
  planDoc: "docs/nodejs/phase15/PHASE15_NODE_V1_3_SUMMARY_HANDOFF.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase15/PHASE15_SURFACE_MANIFEST.md",
  phase14FutureBacklog: "docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  gradle15: "app/node-phase15-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const docsOutput = path.resolve(repoRoot, options.docsOutput || DEFAULT_DOCS_OUTPUT);
  const backlogOutput = path.resolve(repoRoot, options.backlogOutput || DEFAULT_BACKLOG_OUTPUT);
  const handoffOutput = path.resolve(repoRoot, options.handoffOutput || DEFAULT_HANDOFF_OUTPUT);
  const files = loadFiles(repoRoot);
  const generatedReports = loadGeneratedReports(reportDir);
  const summary = buildSummary(files, generatedReports);
  const markdown = renderMarkdown(summary);
  const backlog = buildBacklog(summary);
  const handoff = renderHandoff(summary, backlog);
  const checks = buildChecks(files, generatedReports, summary, markdown, backlog, handoff);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => ({
    id: entry.id,
    message: entry.message,
  }));
  const report = {
    schema: "autojs6-node-phase15-summary-report-v1",
    report: REPORT_BASENAME,
    generatedAt: new Date().toISOString(),
    gate: "generateNodePhase15Summary",
    task: "P15-27",
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, {
      path: file.relative,
      exists: file.exists,
    }])),
    generatedReportDir: path.relative(repoRoot, reportDir).replace(/\\/g, "/"),
    docsOutput: path.relative(repoRoot, docsOutput).replace(/\\/g, "/"),
    backlogOutput: path.relative(repoRoot, backlogOutput).replace(/\\/g, "/"),
    handoffOutput: path.relative(repoRoot, handoffOutput).replace(/\\/g, "/"),
    ...summary,
    checks,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : "ready",
      summaryDecision: "phase15_summary_handoff_ready",
      p15TaskComplete: summary.summaryGate.phase15SummaryTaskComplete,
      zeroOpenV13ReleaseBlockers: summary.summaryGate.releaseBlockingReports.length === 0,
      zeroPolicyViolations: summary.summaryGate.reportsWithPolicyViolations.length === 0,
      zeroReleaseBlockingReports: summary.summaryGate.releaseBlockingReports.length === 0,
      futureBacklogVisible: summary.openFutureWork.futurePromotionReports.length > 0,
      nextBranch: NEXT_BRANCH,
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(path.dirname(docsOutput), { recursive: true });
  fs.mkdirSync(path.dirname(backlogOutput), { recursive: true });
  fs.mkdirSync(path.dirname(handoffOutput), { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), markdown);
  fs.writeFileSync(docsOutput, markdown);
  fs.writeFileSync(backlogOutput, JSON.stringify(backlog, null, 2) + "\n");
  fs.writeFileSync(handoffOutput, handoff);
  console.log("Phase 15 summary generation: " + report.decision.status);
  console.log("Phase 15 summary policy violations: " + policyViolations.length);
  console.log("Phase 15 summary release-blocking reports: " + summary.summaryGate.releaseBlockingReports.length);
  console.log("Phase 15 summary future promotion reports: " + summary.openFutureWork.futurePromotionReports.length);
  console.log("Phase 15 next branch handoff: " + NEXT_BRANCH);
  if (policyViolations.length > 0) {
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

function loadGeneratedReports(reportDir) {
  if (!fs.existsSync(reportDir)) {
    return [];
  }
  return fs.readdirSync(reportDir)
    .filter((name) => /^phase15-.+\.json$/.test(name))
    .filter((name) => name !== `${REPORT_BASENAME}.json`)
    .sort()
    .map((name) => loadReport(reportDir, name));
}

function loadReport(reportDir, name) {
  const file = path.join(reportDir, name);
  const exists = fs.existsSync(file);
  const parsed = exists ? parseJson(read(file)) : {};
  const blockers = Array.isArray(parsed.blockers) ? parsed.blockers : [];
  const policyViolations = Array.isArray(parsed.policyViolations) ? parsed.policyViolations : [];
  const releaseBlockingBlockers = blockers.filter((blocker) => isReleaseBlockingBlocker(blocker));
  const nonBlockingBlockers = blockers.filter((blocker) => isNonBlockingBlocker(blocker));
  return {
    name,
    exists,
    basename: name.replace(/\.json$/, ""),
    schema: parsed.schema || null,
    gate: parsed.gate || null,
    task: parsed.task || null,
    status: statusOf(parsed),
    policyViolations: policyViolations.length,
    policyViolationDetails: policyViolations,
    blockers: blockers.length,
    blockerDetails: blockers,
    blockerClassifications: countBy(blockers, (blocker) => blocker.classification || "missing"),
    releaseBlockingBlockers,
    nonBlockingBlockers,
  };
}

function buildSummary(files, generatedReports) {
  const manifest = parseJson(files.surfaceManifest.text);
  const plan = parseJson(files.planJson.text);
  const phase14FutureBacklog = parseJson(files.phase14FutureBacklog.text);
  const taskMappings = manifest.taskMappings || [];
  const tasks = parseTasks(files.phase15Tasks.text, taskMappings);
  const reportsByTask = new Map();
  generatedReports.forEach((report) => {
    if (report.task && !reportsByTask.has(report.task)) {
      reportsByTask.set(report.task, report);
    }
  });
  const summaryGate = buildSummaryGate(tasks, generatedReports);
  const taskCounts = countTasks(tasks);
  const taskMappingCounts = {
    total: taskMappings.length,
    ready: taskMappings.filter((entry) => entry.status === "ready").length,
    planned: taskMappings.filter((entry) => entry.status === "planned").length,
    byStatus: countBy(taskMappings, (entry) => entry.status),
  };
  const openFutureWork = {
    openTasks: tasks.filter((task) => task.status === "open"),
    importedPhase14BacklogTasks: Array.isArray((manifest.importedBacklog || {}).tasks)
      ? manifest.importedBacklog.tasks
      : [],
    phase14FutureBacklogTasks: Array.isArray(phase14FutureBacklog.tasks)
      ? phase14FutureBacklog.tasks.map((task) => task.id)
      : [],
    futurePromotionReports: summaryGate.futurePromotionReports.map(reportSummary),
    stableDeniedCarryForward: summaryGate.futurePromotionReports
      .filter((report) => report.status === "ready" && report.blockers > 0)
      .map(reportSummary),
  };
  const generatedReportCounts = {
    total: generatedReports.length,
    byStatus: countBy(generatedReports, (report) => report.status),
    withPolicyViolations: generatedReports.filter((report) => report.policyViolations > 0).length,
    withBlockers: generatedReports.filter((report) => report.blockers > 0).length,
  };
  return {
    phase: manifest.phase || "Phase 15: Node v1.3 Provider Promotion",
    branch: manifest.branch || "feature/nodejs-runtime-v1.3",
    plan,
    profileVocabulary: manifest.profileVocabulary || [],
    riskVocabulary: manifest.riskVocabulary || [],
    taskCounts,
    taskMappingCounts,
    tasks,
    reportsByTask: Object.fromEntries([...reportsByTask.entries()].map(([task, report]) => [task, reportSummary(report)])),
    generatedReports: generatedReports.map(reportSummary),
    generatedReportCounts,
    openFutureWork,
    summaryGate,
  };
}

function buildSummaryGate(tasks, generatedReports) {
  const p15SummaryTask = tasks.find((task) => task.id === "P15-27") || null;
  const reportsWithPolicyViolations = generatedReports.filter((report) => report.policyViolations > 0);
  const reportsWithBlockers = generatedReports.filter((report) => report.blockers > 0);
  const failedReports = generatedReports.filter((report) => report.status === "failed");
  const futurePromotionReports = reportsWithBlockers.filter((report) =>
    report.releaseBlockingBlockers.length === 0 &&
      report.blockerDetails.length === report.nonBlockingBlockers.length);
  const releaseBlockingReports = generatedReports.filter((report) =>
    report.status === "failed" ||
      report.policyViolations > 0 ||
      report.releaseBlockingBlockers.length > 0 ||
      report.blockerDetails.some((blocker) => !blocker.classification));
  const reportBasenames = new Set(generatedReports.map((report) => report.basename));
  const requiredReportsPresent = REQUIRED_REPORT_BASENAMES.every((basename) => reportBasenames.has(basename));
  const coreReportsReady = REQUIRED_CORE_REPORTS.every((basename) => {
    const report = generatedReports.find((entry) => entry.basename === basename);
    return report &&
      ["passed", "ready"].includes(report.status) &&
      report.policyViolations === 0 &&
      report.releaseBlockingBlockers.length === 0;
  });
  const ready =
    Boolean(p15SummaryTask && p15SummaryTask.status === "completed") &&
    requiredReportsPresent &&
    coreReportsReady &&
    reportsWithPolicyViolations.length === 0 &&
    releaseBlockingReports.length === 0 &&
    futurePromotionReports.length > 0;
  return {
    status: ready ? "ready" : "partial",
    ready,
    phase15SummaryTaskComplete: Boolean(p15SummaryTask && p15SummaryTask.status === "completed"),
    requiredReportsPresent,
    coreReportsReady,
    reportsWithPolicyViolations: reportsWithPolicyViolations.map(reportSummary),
    reportsWithBlockers: reportsWithBlockers.map(reportSummary),
    releaseBlockingReports: releaseBlockingReports.map(reportSummary),
    futurePromotionReports: futurePromotionReports.map(reportSummary),
    failedReports: failedReports.map(reportSummary),
  };
}

function parseTasks(text, taskMappings) {
  const taskById = new Map();
  const lines = text.split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const match = /^- \[([ x])\]\s+(P15-\d{2}):\s+(.+)$/.exec(lines[index]);
    if (match && !taskById.has(match[2])) {
      taskById.set(match[2], {
        id: match[2],
        checked: match[1] === "x",
        title: match[3].trim(),
      });
    }
  }
  const mappingByTask = new Map((taskMappings || []).map((entry) => [entry.task, entry]));
  return REQUIRED_TASKS.map((taskId) => {
    const task = taskById.get(taskId);
    const mapping = mappingByTask.get(taskId) || {};
    return {
      id: taskId,
      status: task && task.checked ? "completed" : "open",
      title: task ? task.title : "missing from checklist",
      ownerSurface: mapping.ownerSurface || null,
      manifestStatus: mapping.status || null,
      gate: mapping.gate || null,
      sourceBacklogTasks: mapping.sourceBacklogTasks || [],
      risk: mapping.risk || null,
    };
  });
}

function countTasks(tasks) {
  const byStatus = countBy(tasks, (task) => task.status);
  return {
    total: tasks.length,
    completed: byStatus.completed || 0,
    open: byStatus.open || 0,
    byStatus,
  };
}

function buildBacklog(summary) {
  return {
    schema: "autojs6-node-phase15-future-backlog-v1",
    phase: summary.phase,
    sourceBranch: summary.branch,
    suggestedNextBranch: NEXT_BRANCH,
    classification: "v1.4 future promotion backlog",
    generatedBy: ":app:generateNodePhase15Summary",
    generatedAt: new Date().toISOString(),
    releaseBlockingReports: summary.summaryGate.releaseBlockingReports,
    openTasks: summary.openFutureWork.openTasks.map((task) => ({
      id: task.id,
      ownerSurface: task.ownerSurface,
      sourceBacklogTasks: task.sourceBacklogTasks,
      gate: task.gate,
      risk: task.risk,
      title: task.title,
    })),
    stableDeniedCarryForward: summary.openFutureWork.stableDeniedCarryForward,
    futurePromotionReports: summary.openFutureWork.futurePromotionReports,
    importedPhase14BacklogTasks: summary.openFutureWork.importedPhase14BacklogTasks,
  };
}

function buildChecks(files, generatedReports, summary, markdown, backlog, handoff) {
  const reportBasenames = new Set(generatedReports.map((report) => report.basename));
  const missingReports = REQUIRED_REPORT_BASENAMES.filter((basename) => !reportBasenames.has(basename));
  const p15SummaryTask = summary.tasks.find((task) => task.id === "P15-27") || {};
  return [
    check(
      "plan-json-present",
      files.planJson.exists &&
        summary.plan.schema === "autojs6-node-phase15-summary-handoff-plan-v1" &&
        summary.plan.task === "P15-27" &&
        summary.plan.status === "ready" &&
        summary.plan.decision === "phase15_summary_handoff_ready" &&
        summary.plan.gradleTask === ":app:generateNodePhase15Summary" &&
        summary.plan.runtimeAuthorityChanged === false,
      "P15-27 plan JSON must define the ready summary handoff gate without runtime authority changes.",
    ),
    check(
      "task-coverage",
      summary.tasks.length === REQUIRED_TASKS.length &&
        summary.tasks.every((task) => REQUIRED_TASKS.includes(task.id)) &&
        p15SummaryTask.status === "completed" &&
        p15SummaryTask.ownerSurface === "phase15.summary_handoff",
      "Summary must enumerate P15-00 through P15-27 and mark P15-27 complete under phase15.summary_handoff.",
    ),
    check(
      "generated-reports-present",
      generatedReports.length >= REQUIRED_REPORT_BASENAMES.length &&
        missingReports.length === 0,
      "Summary must import every Phase 15 generated report through P15-26.",
    ),
    check(
      "release-blocking-policy",
      summary.summaryGate.ready &&
        summary.summaryGate.reportsWithPolicyViolations.length === 0 &&
        summary.summaryGate.releaseBlockingReports.length === 0 &&
        summary.summaryGate.coreReportsReady &&
        markdown.includes("- Release-blocking reports: 0") &&
        markdown.includes("- Reports with policy violations: 0"),
      "P15-27 requires zero policy violations and zero release-blocking reports while preserving future-promotion blockers.",
    ),
    check(
      "future-backlog-visible",
      backlog.schema === "autojs6-node-phase15-future-backlog-v1" &&
        backlog.classification === "v1.4 future promotion backlog" &&
        backlog.suggestedNextBranch === NEXT_BRANCH &&
        backlog.openTasks.length === summary.openFutureWork.openTasks.length &&
        backlog.futurePromotionReports.length === summary.openFutureWork.futurePromotionReports.length &&
        backlog.futurePromotionReports.length > 0 &&
        markdown.includes("## Open Future Work") &&
        markdown.includes("future_promotion") &&
        handoff.includes("v1.4 future promotion backlog"),
      "P15-27 must export visible future promotion backlog and next-branch handoff notes.",
    ),
    check(
      "docs-and-gradle-wiring",
      token(files.phase15Tasks, "- [x] P15-27: Generate Phase 15 summary and next branch handoff.") &&
        token(files.phase15Tasks, "Summary handoff gate deployed:") &&
        token(files.phase15Report, "P15-27 decision: `phase15_summary_handoff_ready`") &&
        token(files.phase15Readme, ":app:generateNodePhase15Summary") &&
        token(files.testing, ":app:generateNodePhase15Summary") &&
        token(files.ciGates, ":app:generateNodePhase15Summary") &&
        token(files.securityModel, "P15-27 records the v1.3 summary and next-branch handoff") &&
        token(files.errorCatalog, "P15-27 summary and next-branch handoff") &&
        token(files.gradle15, "generateNodePhase15Summary") &&
        token(files.gradle15, "generate-phase15-summary.js"),
      "Docs and Gradle must expose the P15-27 summary handoff gate.",
    ),
    check(
      "handoff-boundaries",
      handoff.includes("Suggested next branch: `feature/nodejs-runtime-v1.4`") &&
        handoff.includes("do not promote raw Android object parity") &&
        handoff.includes("runtime authority remains unchanged") &&
        handoff.includes("provider_poc_not_promoted") &&
        handoff.includes("host_contract_ready") &&
        handoff.includes("stable_denied"),
      "Next-branch handoff must preserve v1.3 authority boundaries.",
    ),
  ];
}

function renderMarkdown(summary) {
  const lines = [
    "# Phase 15 Summary",
    "",
    "Status: P15-27 v1.3 summary handoff is ready.",
    "",
    "This summary is generated from the Phase 15 task checklist, Phase 15 surface manifest, imported Phase 14 future backlog, and generated Phase 15 reports. Future promotion blockers remain visible as backlog and are not counted as release-blocking reports unless a report classifies them that way.",
    "",
    "## Summary Gate",
    "",
    `- Status: \`${summary.summaryGate.status}\``,
    `- P15-27 task complete: ${summary.summaryGate.phase15SummaryTaskComplete}`,
    `- Required reports present: ${summary.summaryGate.requiredReportsPresent}`,
    `- Core reports ready: ${summary.summaryGate.coreReportsReady}`,
    `- Release-blocking reports: ${summary.summaryGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.summaryGate.futurePromotionReports.length}`,
    `- Reports with policy violations: ${summary.summaryGate.reportsWithPolicyViolations.length}`,
    `- Suggested next branch: \`${NEXT_BRANCH}\``,
    "",
    "## Task Checklist",
    "",
    `- Total tasks: ${summary.taskCounts.total}`,
    `- Completed tasks: ${summary.taskCounts.completed}`,
    `- Open tasks: ${summary.taskCounts.open}`,
    "",
    "| Task | Status | Owner surface | Manifest status | Gate | Title |",
    "| --- | --- | --- | --- | --- | --- |",
  ];
  summary.tasks.forEach((task) => {
    lines.push(`| \`${task.id}\` | \`${task.status}\` | \`${task.ownerSurface || "-"}\` | \`${task.manifestStatus || "-"}\` | ${task.gate ? `\`${task.gate}\`` : "-"} | ${escapeTable(task.title)} |`);
  });
  lines.push(
    "",
    "## Manifest Task Mapping Status",
    "",
    `- Total task mappings: ${summary.taskMappingCounts.total}`,
    `- Ready mappings: ${summary.taskMappingCounts.ready}`,
    `- Planned mappings: ${summary.taskMappingCounts.planned}`,
    "",
    "| Manifest status | Count |",
    "| --- | ---: |",
  );
  Object.keys(summary.taskMappingCounts.byStatus).sort().forEach((status) => {
    lines.push(`| \`${status}\` | ${summary.taskMappingCounts.byStatus[status]} |`);
  });
  lines.push(
    "",
    "## Generated Phase 15 Reports",
    "",
    `- Total reports: ${summary.generatedReportCounts.total}`,
    `- Reports with policy violations: ${summary.generatedReportCounts.withPolicyViolations}`,
    `- Reports with blockers: ${summary.generatedReportCounts.withBlockers}`,
    `- Release-blocking reports: ${summary.summaryGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.summaryGate.futurePromotionReports.length}`,
    "",
    "| Report | Task | Status | Policy violations | Blockers | Classifications |",
    "| --- | --- | --- | ---: | ---: | --- |",
  );
  summary.generatedReports.forEach((report) => {
    lines.push(`| \`${report.name}\` | \`${report.task || "-"}\` | \`${report.status}\` | ${report.policyViolations} | ${report.blockers} | ${formatClassifications(report.blockerClassifications)} |`);
  });
  lines.push(
    "",
    "## Open Future Work",
    "",
    `- Open tasks: ${summary.openFutureWork.openTasks.length}`,
    `- Imported Phase 14 backlog tasks: ${summary.openFutureWork.importedPhase14BacklogTasks.length}`,
    `- Future promotion reports with blockers: ${summary.openFutureWork.futurePromotionReports.length}`,
    `- Stable-denied carry-forward reports: ${summary.openFutureWork.stableDeniedCarryForward.length}`,
    "",
    "| Task | Owner surface | Source backlog | Gate | Title |",
    "| --- | --- | --- | --- | --- |",
  );
  summary.openFutureWork.openTasks.forEach((task) => {
    const source = task.sourceBacklogTasks.length > 0
      ? task.sourceBacklogTasks.map((entry) => `\`${entry}\``).join(", ")
      : "-";
    lines.push(`| \`${task.id}\` | \`${task.ownerSurface || "-"}\` | ${source} | ${task.gate ? `\`${task.gate}\`` : "-"} | ${escapeTable(task.title)} |`);
  });
  lines.push("", "| Future promotion report | Task | Status | Blocker classifications |", "| --- | --- | --- | --- |");
  summary.openFutureWork.futurePromotionReports.forEach((report) => {
    lines.push(`| \`${report.name}\` | \`${report.task || "-"}\` | \`${report.status}\` | ${formatClassifications(report.blockerClassifications)} |`);
  });
  lines.push(
    "",
    "## Verification",
    "",
    "- Generator: `tools/nodejs/phase15/generate-phase15-summary.js`",
    "- Gradle: `:app:generateNodePhase15Summary`",
    "- Reports: `build/reports/nodejs/phase15-summary.json` and `build/reports/nodejs/phase15-summary.md`",
    "- Canonical docs output: `docs/nodejs/reports/phase15/PHASE15_SUMMARY.md`",
    "- Future backlog export: `docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json`",
    "- Next branch handoff: `docs/nodejs/reports/phase15/PHASE15_NEXT_BRANCH_HANDOFF.md`",
    "- P15-27 requires zero policy violations and zero release-blocking reports. Future-promotion blockers stay visible for the next branch.",
    "",
  );
  return lines.join("\n");
}

function renderHandoff(summary, backlog) {
  const lines = [
    "# Phase 15 Next Branch Handoff",
    "",
    `Suggested next branch: \`${NEXT_BRANCH}\``,
    "",
    "Status: handoff ready.",
    "",
    "Start the next branch from the committed Phase 15 summary state after `:app:generateNodePhase15Summary` reports `ready`.",
    "",
    "## Carry Forward",
    "",
    "- Use `docs/nodejs/reports/phase15/PHASE15_FUTURE_BACKLOG.json` as the v1.4 future promotion backlog.",
    "- Keep Safe profile as the default profile.",
    "- Keep `provider_poc_not_promoted` reports out of release notes as promoted runtime authority until provider gates are reopened and closed with device, packaged, disclosure, rollback, security, TypeScript, docs, and migration evidence.",
    "- Keep `host_contract_ready` packaged aggregate smoke separate from real device install-run release approval.",
    "- Keep `stable_denied` MediaStore/playback boundaries denied until a future scoped provider gate supplies exact capabilities, privacy, permissions, packaged behavior, rollback, Safe downgrade, and raw-object denial evidence.",
    "- runtime authority remains unchanged.",
    "- do not promote raw Android object parity.",
    "",
    "## v1.4 future promotion backlog",
    "",
    `- Open Phase 15 tasks: ${backlog.openTasks.length}`,
    `- Future promotion reports with blockers: ${backlog.futurePromotionReports.length}`,
    `- Imported Phase 14 backlog tasks retained: ${backlog.importedPhase14BacklogTasks.length}`,
    "",
    "## First v1.4 Deployment Candidates",
    "",
    "1. Reopen the Pro UI/overlay/automation/media lane from P15-04 through P15-10, starting with packaged live UI and overlay evidence gaps.",
    "2. Reopen the Pro task/device/media lane from P15-12 through P15-20, starting with task database ownership and schema rollback.",
    "3. Revisit P15-21 MediaStore/playback only as a scoped provider design, preserving the stable-denied default until evidence is complete.",
    "4. Convert any provider POC to opt-in promotion only after docs, types, release notes, security corpus, packaged evidence, and rollback behavior all agree.",
    "",
    "## Source Summary",
    "",
    `- Release-blocking reports: ${summary.summaryGate.releaseBlockingReports.length}`,
    `- Reports with policy violations: ${summary.summaryGate.reportsWithPolicyViolations.length}`,
    `- Future promotion reports: ${summary.summaryGate.futurePromotionReports.length}`,
    "",
  ];
  return lines.join("\n");
}

function reportSummary(report) {
  return {
    name: report.name,
    gate: report.gate,
    task: report.task,
    status: report.status,
    policyViolations: report.policyViolations,
    blockers: report.blockers,
    blockerClassifications: report.blockerClassifications,
  };
}

function isReleaseBlockingBlocker(blocker) {
  return !isNonBlockingBlocker(blocker);
}

function isNonBlockingBlocker(blocker) {
  return Boolean(blocker && NON_BLOCKING_REPORT_BLOCKERS.has(blocker.classification));
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function token(file, tokenValue) {
  return file.exists && file.text.includes(tokenValue);
}

function countBy(values, keyFn) {
  return values.reduce((counts, value) => {
    const key = keyFn(value);
    counts[key] = (counts[key] || 0) + 1;
    return counts;
  }, {});
}

function formatClassifications(classifications) {
  const entries = Object.entries(classifications || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `\`${key}\`: ${value}`)
    .join(", ");
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
    } else if (arg === "--docs-output") {
      options.docsOutput = args[++index];
    } else if (arg === "--backlog-output") {
      options.backlogOutput = args[++index];
    } else if (arg === "--handoff-output") {
      options.handoffOutput = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node generate-phase15-summary.js [--repo-root dir] [--report-dir dir] [--docs-output file] [--backlog-output file] [--handoff-output file]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
