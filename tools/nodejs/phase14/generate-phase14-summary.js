#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-summary";
const DEFAULT_DOCS_OUTPUT = "docs/nodejs/reports/phase14/PHASE14_SUMMARY.md";
const REQUIRED_TASKS = Object.freeze(
  Array.from({ length: 40 }, (_, index) => `P14-${String(index).padStart(2, "0")}`),
);
const COMPLETED_SURFACE_STATUSES = new Set(["default_promoted", "opt_in_promoted"]);
const OPEN_SURFACE_STATUSES = new Set(["planned", "debug_poc", "provider_poc"]);
const DEFERRED_SURFACE_STATUSES = new Set(["design_locked"]);
const REJECTED_SURFACE_STATUSES = new Set(["rejected"]);
const OPEN_FINAL_BLOCKER_STATUSES = new Set(["open", "decision_required"]);
const DEFERRED_FINAL_BLOCKER_STATUSES = new Set(["optional", "waiting_for_android_artifacts"]);
const REJECTED_FINAL_BLOCKER_STATUSES = new Set(["permanently_unsupported", "intentionally_unsupported"]);
const NON_BLOCKING_REPORT_BLOCKERS = new Set(["future_promotion", "external", "intentional_non_goal"]);
const RELEASE_BLOCKING_REPORT_BLOCKERS = new Set(["release_blocking"]);
const REQUIRED_REPORT_BASENAMES = Object.freeze([
  "phase14-baseline-verification",
  "phase14-surface-manifest-verification",
  "phase14-promotion-contracts",
  "phase14-security-regression-corpus",
  "phase14-packaged-aggregate-smoke",
  "phase14-type-declaration-profiles",
  "phase14-migration-pro-comparison",
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_37_PHASE14_SUMMARY_GATE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/node-v1-2-summary-gate.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NODE_V1_2_SUMMARY_GATE.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
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
  const docsOutput = path.resolve(repoRoot, options.docsOutput || DEFAULT_DOCS_OUTPUT);
  const files = loadFiles(repoRoot);
  const generatedReports = loadGeneratedReports(reportDir);
  const capabilityTruthReport = loadReport(reportDir, "phase13-capability-truth.json");
  const summary = buildSummary(files, generatedReports, capabilityTruthReport);
  const markdown = renderMarkdown(summary);
  const checks = buildChecks(files, generatedReports, capabilityTruthReport, summary, markdown);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => ({
    id: entry.id,
    message: entry.message,
  }));
  const report = {
    schema: "autojs6-node-phase14-summary-report-v1",
    report: REPORT_BASENAME,
    generatedAt: new Date().toISOString(),
    gate: "generateNodePhase14Summary",
    task: "P14-37",
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, {
      path: file.relative,
      exists: file.exists,
    }])),
    generatedReportDir: path.relative(repoRoot, reportDir).replace(/\\/g, "/"),
    docsOutput: path.relative(repoRoot, docsOutput).replace(/\\/g, "/"),
    ...summary,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : summary.summaryGate.ready ? "ready" : "partial",
      summaryDecision: "phase14_summary_gate_ready",
      p14TaskComplete: summary.summaryGate.phase14SummaryTaskComplete,
      zeroOpenFinalBlockers: summary.summaryGate.openFinalBlockers.length === 0,
      zeroPolicyViolations: summary.summaryGate.reportsWithPolicyViolations.length === 0,
      zeroReleaseBlockingReports: summary.summaryGate.releaseBlockingReports.length === 0,
      capabilityTruthImported: summary.summaryGate.capabilityTruthReady,
      futureWorkRecorded: summary.openFutureWork.openTasks.length > 0 &&
        summary.summaryGate.futurePromotionReports.length > 0,
      closeoutPolicyReady: summary.summaryGate.closeoutPolicyReady,
      phase14FullyClosed: summary.openFutureWork.openTasks.length === 0,
      phase14CloseoutTasksRemaining: summary.openFutureWork.closeoutTasks.map((task) => task.id),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(path.dirname(docsOutput), { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), markdown);
  fs.writeFileSync(docsOutput, markdown);
  console.log("Phase 14 summary generation: " + report.decision.status);
  console.log("Phase 14 summary policy violations: " + policyViolations.length);
  console.log("Phase 14 summary open final blockers: " + summary.summaryGate.openFinalBlockers.length);
  console.log("Phase 14 summary release-blocking reports: " + summary.summaryGate.releaseBlockingReports.length);
  console.log("Phase 14 summary future promotion reports: " + summary.summaryGate.futurePromotionReports.length);
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
    .filter((name) => /^phase14-.+\.json$/.test(name))
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

function buildSummary(files, generatedReports, capabilityTruthReport) {
  const manifest = parseJson(files.surfaceManifest.text);
  const plan = parseJson(files.planJson.text);
  const blockerRegistry = parseJson(files.blockerRegistry.text);
  const surfaces = manifest.surfaces || [];
  const tasks = parseTasks(files.phase14Tasks.text, surfaces);
  const blockers = parseFinalBlockers(blockerRegistry.blockers || []);
  const promotedSurfaces = surfaces.filter((surface) => COMPLETED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const openSurfaces = surfaces.filter((surface) => OPEN_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const deferredSurfaces = surfaces.filter((surface) => DEFERRED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const rejectedSurfaces = surfaces.filter((surface) => REJECTED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const generatedReportCounts = {
    total: generatedReports.length,
    byStatus: countBy(generatedReports, (report) => report.status),
    withPolicyViolations: generatedReports.filter((report) => report.policyViolations > 0).length,
    withBlockers: generatedReports.filter((report) => report.blockers > 0).length,
  };
  const typeDeclarationReport = generatedReports.find((report) =>
    report.basename === "phase14-type-declaration-profiles") || null;
  const migrationReport = generatedReports.find((report) =>
    report.basename === "phase14-migration-pro-comparison") || null;
  const summaryGate = buildSummaryGate({
    tasks,
    blockers,
    generatedReports,
    capabilityTruthReport,
    typeDeclarationReport,
    migrationReport,
  });
  const openFutureWork = {
    openTasks: tasks.filter((task) => task.status === "open"),
    closeoutTasks: tasks.filter((task) => task.status === "open" && ["P14-38", "P14-39"].includes(task.id)),
    futurePromotionReports: summaryGate.futurePromotionReports.map(reportSummary),
  };
  return {
    phase: manifest.phase || "Phase 14: Node v1.2 Provider Promotion",
    plan,
    profileVocabulary: manifest.profileVocabulary || [],
    statusVocabulary: manifest.statusVocabulary || [],
    taskCounts: countTasks(tasks),
    tasks,
    surfaceCounts: {
      total: surfaces.length,
      completed: promotedSurfaces.length,
      open: openSurfaces.length,
      deferred: deferredSurfaces.length,
      rejected: rejectedSurfaces.length,
      byStatus: countBy(surfaces, (surface) => surface.status),
    },
    promotedSurfaces,
    openSurfaces,
    deferredSurfaces,
    rejectedSurfaces,
    blockerCounts: countFinalBlockers(blockers),
    finalBlockers: blockers,
    capabilityTruthReport,
    typeDeclarationReport,
    migrationReport,
    generatedReports: generatedReports.map(reportSummary),
    generatedReportCounts,
    summaryGate,
    openFutureWork,
  };
}

function parseTasks(text, surfaces) {
  const taskById = new Map();
  const lines = text.split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const match = /^- \[([ x])\]\s+(P14-\d{2}):\s+(.+)$/.exec(lines[index]);
    if (match && !taskById.has(match[2])) {
      const titleParts = [match[3].trim()];
      let lookahead = index + 1;
      while (lookahead < lines.length && /^  [^- ].+/.test(lines[lookahead])) {
        titleParts.push(lines[lookahead].trim());
        lookahead += 1;
      }
      taskById.set(match[2], {
        id: match[2],
        checked: match[1] === "x",
        title: titleParts.join(" "),
      });
    }
  }
  const surfaceByTask = new Map();
  for (const surface of surfaces) {
    for (const task of surface.tasks || []) {
      surfaceByTask.set(task, surface.id);
    }
  }
  return REQUIRED_TASKS.map((taskId) => {
    const task = taskById.get(taskId);
    return {
      id: taskId,
      status: task && task.checked ? "completed" : "open",
      title: task ? task.title : "missing from checklist",
      surface: surfaceByTask.get(taskId) || null,
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

function parseFinalBlockers(blockers) {
  return blockers.map((blocker) => {
    let summaryStatus = "open";
    if (blocker.status === "closed") {
      summaryStatus = "completed";
    } else if (DEFERRED_FINAL_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "deferred";
    } else if (REJECTED_FINAL_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "rejected";
    } else if (OPEN_FINAL_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "open";
    }
    return {
      id: blocker.id,
      classification: blocker.classification,
      status: blocker.status,
      summaryStatus,
      currentEvidence: blocker.currentEvidence,
      requiredResolution: blocker.requiredResolution,
    };
  });
}

function countFinalBlockers(blockers) {
  const bySummaryStatus = countBy(blockers, (blocker) => blocker.summaryStatus);
  const byRegistryStatus = countBy(blockers, (blocker) => blocker.status);
  return {
    total: blockers.length,
    completed: bySummaryStatus.completed || 0,
    open: bySummaryStatus.open || 0,
    deferred: bySummaryStatus.deferred || 0,
    rejected: bySummaryStatus.rejected || 0,
    bySummaryStatus,
    byRegistryStatus,
  };
}

function surfaceSummary(surface) {
  return {
    id: surface.id,
    status: surface.status,
    profiles: surface.profiles || [],
    tasks: surface.tasks || [],
    risk: surface.risk,
    evidence: surface.evidence || [],
    summary: surface.summary || "",
  };
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

function buildSummaryGate({
  tasks,
  blockers,
  generatedReports,
  capabilityTruthReport,
  typeDeclarationReport,
  migrationReport,
}) {
  const p14SummaryTask = tasks.find((task) => task.id === "P14-37") || null;
  const openFinalBlockers = blockers.filter((blocker) => blocker.summaryStatus === "open");
  const requiredInternalOpen = blockers.filter((blocker) =>
    blocker.classification === "required_internal" && blocker.summaryStatus !== "completed");
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
  const capabilityTruthReady =
    capabilityTruthReport.exists &&
    capabilityTruthReport.gate === "generateNodePhase13CapabilityTruth" &&
    capabilityTruthReport.status === "passed" &&
    capabilityTruthReport.policyViolations === 0;
  const typeDeclarationsReady =
    Boolean(typeDeclarationReport) &&
    typeDeclarationReport.status === "ready" &&
    typeDeclarationReport.policyViolations === 0;
  const migrationComparisonReady =
    Boolean(migrationReport) &&
    migrationReport.status === "ready" &&
    migrationReport.policyViolations === 0;
  const closeoutPolicyReady =
    openFinalBlockers.length === 0 &&
    requiredInternalOpen.length === 0 &&
    reportsWithPolicyViolations.length === 0 &&
    releaseBlockingReports.length === 0;
  const ready =
    p14SummaryTask &&
    p14SummaryTask.status === "completed" &&
    closeoutPolicyReady &&
    capabilityTruthReady &&
    typeDeclarationsReady &&
    migrationComparisonReady;
  return {
    status: ready ? "ready" : "partial",
    ready: Boolean(ready),
    phase14SummaryTaskComplete: Boolean(p14SummaryTask && p14SummaryTask.status === "completed"),
    closeoutPolicyReady,
    openFinalBlockers,
    requiredInternalOpen,
    reportsWithPolicyViolations: reportsWithPolicyViolations.map(reportSummary),
    reportsWithBlockers: reportsWithBlockers.map(reportSummary),
    releaseBlockingReports: releaseBlockingReports.map(reportSummary),
    futurePromotionReports: futurePromotionReports.map(reportSummary),
    failedReports: failedReports.map(reportSummary),
    capabilityTruthReady,
    typeDeclarationsReady,
    migrationComparisonReady,
  };
}

function buildChecks(files, generatedReports, capabilityTruthReport, summary, markdown) {
  const taskSurfaceMissing = summary.tasks.filter((task) => !task.surface);
  const reportBasenames = new Set(generatedReports.map((report) => report.basename));
  const docsSurface = [...summary.openSurfaces, ...summary.deferredSurfaces, ...summary.promotedSurfaces]
    .find((surface) => surface.id === "docs.examples.final_gate");
  const promotedWithoutEvidence = summary.promotedSurfaces.filter((surface) => surface.evidence.length === 0);
  const p14SummaryTask = summary.tasks.find((task) => task.id === "P14-37");
  const releaseBlockingBlockerCount = generatedReports.reduce(
    (count, report) => count + report.releaseBlockingBlockers.length,
    0,
  );
  return [
    check(
      "plan-json-present",
      files.planJson.exists &&
        summary.plan.schema === "autojs6-node-phase14-summary-gate-plan-v1" &&
        summary.plan.task === "P14-37" &&
        summary.plan.status === "ready" &&
        summary.plan.decision === "phase14_summary_gate_ready" &&
        summary.plan.gradleTask === ":app:generateNodePhase14Summary" &&
        summary.plan.runtimeAuthorityChanged === false,
      "P14-37 plan JSON must define the ready summary gate without runtime authority changes.",
    ),
    check(
      "task-coverage",
      summary.tasks.length === REQUIRED_TASKS.length &&
        summary.tasks.every((task) => REQUIRED_TASKS.includes(task.id)) &&
        taskSurfaceMissing.length === 0 &&
        p14SummaryTask &&
        p14SummaryTask.status === "completed",
      "Summary must enumerate P14-00 through P14-39, map every task to a surface, and mark P14-37 completed.",
    ),
    check(
      "summary-closeout-policy",
      summary.summaryGate.ready &&
        summary.summaryGate.closeoutPolicyReady &&
        summary.summaryGate.openFinalBlockers.length === 0 &&
        summary.summaryGate.reportsWithPolicyViolations.length === 0 &&
        summary.summaryGate.releaseBlockingReports.length === 0 &&
        releaseBlockingBlockerCount === 0 &&
        markdown.includes("- Open final blockers: 0") &&
        markdown.includes("- Release-blocking reports: 0") &&
        markdown.includes(`- Future promotion reports with blockers: ${summary.summaryGate.futurePromotionReports.length}`),
      "P14-37 closeout policy must require zero open final blockers, zero policy violations, and zero release-blocking reports while keeping future-promotion blockers visible.",
    ),
    check(
      "generated-reports-present",
      generatedReports.length >= 32 &&
        REQUIRED_REPORT_BASENAMES.every((basename) => reportBasenames.has(basename)),
      "Summary must import the core Phase 14 generated reports.",
    ),
    check(
      "capability-truth-present",
      summary.summaryGate.capabilityTruthReady &&
        capabilityTruthReport.name === "phase13-capability-truth.json" &&
        summary.summaryGate.typeDeclarationsReady &&
        summary.summaryGate.migrationComparisonReady,
      "Summary must import Phase 13 capability truth plus the P14-35 type profile and P14-36 migration comparison reports.",
    ),
    check(
      "surface-evidence",
      promotedWithoutEvidence.length === 0 &&
        docsSurface &&
        (docsSurface.tasks || []).includes("P14-37") &&
        docsSurface.evidence.some((entry) => entry.includes("node-v1-2-summary-gate.json")) &&
        docsSurface.evidence.some((entry) => entry.includes("PHASE14_NODE_V1_2_SUMMARY_GATE.md")) &&
        docsSurface.evidence.some((entry) => entry.includes("P14_37_PHASE14_SUMMARY_GATE.md")) &&
        docsSurface.evidence.some((entry) => entry.includes("generate-phase14-summary.js")) &&
        docsSurface.evidence.some((entry) => entry.includes("PHASE14_SUMMARY.md")) &&
        docsSurface.evidence.some((entry) => entry.includes(":app:generateNodePhase14Summary")),
      "`docs.examples.final_gate` must record P14-37 plan, docs, script, Gradle, build report, and canonical summary evidence.",
    ),
    check(
      "future-work-visible",
      summary.openFutureWork.openTasks.length > 0 &&
        summary.openFutureWork.closeoutTasks.length === 2 &&
        summary.openFutureWork.futurePromotionReports.length > 0 &&
        markdown.includes("## Open Future Work") &&
        markdown.includes("P14-38") &&
        markdown.includes("P14-39") &&
        markdown.includes("provider_poc") &&
        markdown.includes("future_promotion"),
      "Summary must keep open P14 tasks, future promotion reports, and final handoff tasks visible.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase14Tasks.text.includes("- [x] P14-37: Create Phase 14 summary generator and gate.") &&
        files.phase14Tasks.text.includes("Phase 14 summary gate deployed:") &&
        files.phase14Report.text.includes("P14-37 decision: `phase14_summary_gate_ready`") &&
        files.phase14Readme.text.includes(":app:generateNodePhase14Summary") &&
        files.testing.text.includes("Phase 14 v1.2 summary gate") &&
        files.ciGates.text.includes("generateNodePhase14Summary") &&
        files.securityModel.text.includes("P14-37 records the Phase 14 summary gate") &&
        files.errorCatalog.text.includes("P14-37 Phase 14 summary") &&
        files.gradle14.text.includes("generateNodePhase14Summary") &&
        files.gradle14.text.includes("generate-phase14-summary.js"),
      "Checklist, report README, Testing, CI, Security, Error Catalog, and Gradle must reference the P14-37 summary gate.",
    ),
    check(
      "markdown-sections",
      includesAll(markdown, [
        "# Phase 14 Summary",
        "## Summary Gate",
        "## Task Checklist",
        "## Surface Status",
        "## Final Blockers",
        "## Capability Truth And Profile Reports",
        "## Generated Phase 14 Reports",
        "## Open Future Work",
        "## Verification",
      ]),
      "Generated markdown must include gate, task, surface, blocker, capability truth, generated reports, future work, and verification sections.",
    ),
  ];
}

function renderMarkdown(summary) {
  const lines = [
    "# Phase 14 Summary",
    "",
    "Status: P14-37 v1.2 summary gate is ready.",
    "",
    "This summary is generated from the Phase 14 task checklist, Phase 14 surface manifest, final blocker registry, Phase 13 capability truth report, and generated Phase 14 reports. Future promotion blockers remain visible as backlog and are not counted as release-blocking reports unless a report classifies them that way.",
    "",
    "## Summary Gate",
    "",
    `- Status: \`${summary.summaryGate.status}\``,
    `- P14-37 task complete: ${summary.summaryGate.phase14SummaryTaskComplete}`,
    `- Closeout policy ready: ${summary.summaryGate.closeoutPolicyReady}`,
    `- Open final blockers: ${summary.summaryGate.openFinalBlockers.length}`,
    `- Required internal blockers still open: ${summary.summaryGate.requiredInternalOpen.length}`,
    `- Release-blocking reports: ${summary.summaryGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.summaryGate.futurePromotionReports.length}`,
    `- Reports with policy violations: ${summary.summaryGate.reportsWithPolicyViolations.length}`,
    `- Phase 13 capability truth ready: ${summary.summaryGate.capabilityTruthReady}`,
    `- P14-35 type declarations ready: ${summary.summaryGate.typeDeclarationsReady}`,
    `- P14-36 migration comparison ready: ${summary.summaryGate.migrationComparisonReady}`,
    `- Phase 14 fully closed: ${summary.openFutureWork.openTasks.length === 0}`,
    "",
    "## Task Checklist",
    "",
    `- Total tasks: ${summary.taskCounts.total}`,
    `- Completed tasks: ${summary.taskCounts.completed}`,
    `- Open tasks: ${summary.taskCounts.open}`,
    "",
    "| Task | Status | Surface | Title |",
    "| --- | --- | --- | --- |",
  ];
  for (const task of summary.tasks) {
    lines.push(`| \`${task.id}\` | \`${task.status}\` | \`${task.surface || "missing"}\` | ${escapeTable(task.title)} |`);
  }

  lines.push(
    "",
    "## Surface Status",
    "",
    `- Total surfaces: ${summary.surfaceCounts.total}`,
    `- Completed surfaces: ${summary.surfaceCounts.completed}`,
    `- Open surfaces: ${summary.surfaceCounts.open}`,
    `- Deferred surfaces: ${summary.surfaceCounts.deferred}`,
    `- Rejected surfaces: ${summary.surfaceCounts.rejected}`,
    "",
    "| Manifest status | Count |",
    "| --- | ---: |",
  );
  for (const status of Object.keys(summary.surfaceCounts.byStatus).sort()) {
    lines.push(`| \`${status}\` | ${summary.surfaceCounts.byStatus[status]} |`);
  }

  pushSurfaceSection(lines, "Promoted Surfaces", summary.promotedSurfaces);
  pushSurfaceSection(lines, "Open Surfaces", summary.openSurfaces);
  pushSurfaceSection(lines, "Deferred Surfaces", summary.deferredSurfaces);
  pushSurfaceSection(lines, "Rejected Surfaces", summary.rejectedSurfaces);

  lines.push(
    "",
    "## Final Blockers",
    "",
    `- Total blockers: ${summary.blockerCounts.total}`,
    `- Completed blockers: ${summary.blockerCounts.completed}`,
    `- Open blockers: ${summary.blockerCounts.open}`,
    `- Deferred blockers: ${summary.blockerCounts.deferred}`,
    `- Rejected blockers: ${summary.blockerCounts.rejected}`,
    "",
    "| Blocker | Summary status | Registry status | Classification | Required resolution |",
    "| --- | --- | --- | --- | --- |",
  );
  for (const blocker of summary.finalBlockers) {
    lines.push(`| \`${blocker.id}\` | \`${blocker.summaryStatus}\` | \`${blocker.status}\` | \`${blocker.classification}\` | ${escapeTable(blocker.requiredResolution)} |`);
  }

  lines.push(
    "",
    "## Capability Truth And Profile Reports",
    "",
    summary.capabilityTruthReport.exists
      ? `- Phase 13 capability truth: \`${summary.capabilityTruthReport.name}\` via \`${summary.capabilityTruthReport.gate || "-"}\` with status \`${summary.capabilityTruthReport.status}\`.`
      : "- Phase 13 capability truth: missing.",
    summary.typeDeclarationReport
      ? `- P14-35 type declaration profile report: \`${summary.typeDeclarationReport.name}\` with status \`${summary.typeDeclarationReport.status}\`.`
      : "- P14-35 type declaration profile report: missing.",
    summary.migrationReport
      ? `- P14-36 migration comparison report: \`${summary.migrationReport.name}\` with status \`${summary.migrationReport.status}\`.`
      : "- P14-36 migration comparison report: missing.",
    "",
    "## Generated Phase 14 Reports",
    "",
    `- Total reports: ${summary.generatedReportCounts.total}`,
    `- Reports with policy violations: ${summary.generatedReportCounts.withPolicyViolations}`,
    `- Reports with blockers: ${summary.generatedReportCounts.withBlockers}`,
    `- Release-blocking reports: ${summary.summaryGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.summaryGate.futurePromotionReports.length}`,
    "",
    "| Report | Gate | Task | Status | Policy violations | Blockers | Classifications |",
    "| --- | --- | --- | --- | ---: | ---: | --- |",
  );
  for (const report of summary.generatedReports) {
    lines.push(`| \`${report.name}\` | \`${report.gate || "-"}\` | \`${report.task || "-"}\` | \`${report.status}\` | ${report.policyViolations} | ${report.blockers} | ${formatCounts(report.blockerClassifications)} |`);
  }

  lines.push(
    "",
    "## Open Future Work",
    "",
    `- Open tasks: ${summary.openFutureWork.openTasks.length}`,
    `- Remaining closeout handoff tasks: ${summary.openFutureWork.closeoutTasks.map((task) => `\`${task.id}\``).join(", ") || "None"}`,
    `- Future promotion reports with blockers: ${summary.openFutureWork.futurePromotionReports.length}`,
    "",
    "| Task | Surface | Title |",
    "| --- | --- | --- |",
  );
  for (const task of summary.openFutureWork.openTasks) {
    lines.push(`| \`${task.id}\` | \`${task.surface || "missing"}\` | ${escapeTable(task.title)} |`);
  }
  lines.push("", "| Future promotion report | Task | Status | Blocker classifications |", "| --- | --- | --- | --- |");
  for (const report of summary.openFutureWork.futurePromotionReports) {
    lines.push(`| \`${report.name}\` | \`${report.task || "-"}\` | \`${report.status}\` | ${formatCounts(report.blockerClassifications)} |`);
  }

  lines.push(
    "",
    "## Verification",
    "",
    "- Generator: `tools/nodejs/phase14/generate-phase14-summary.js`",
    "- Gradle: `:app:generateNodePhase14Summary`",
    "- Reports: `build/reports/nodejs/phase14-summary.json` and `build/reports/nodejs/phase14-summary.md`",
    "- Canonical docs output: `docs/nodejs/reports/phase14/PHASE14_SUMMARY.md`",
    "- P14-37 requires zero open final blockers, zero policy violations, and zero release-blocking reports. P14-38 and P14-39 remain explicit closeout handoff tasks until they are deployed.",
  );
  return lines.join("\n") + "\n";
}

function pushSurfaceSection(lines, title, surfaces) {
  lines.push("", `## ${title}`, "");
  if (surfaces.length === 0) {
    lines.push("- None.");
    return;
  }
  lines.push("| Surface | Status | Risk | Tasks | Evidence | Summary |", "| --- | --- | --- | --- | --- | --- |");
  for (const surface of surfaces) {
    const evidence = surface.evidence.length > 0 ? `${surface.evidence.length} item(s)` : "missing";
    lines.push(`| \`${surface.id}\` | \`${surface.status}\` | \`${surface.risk}\` | ${surface.tasks.map((task) => `\`${task}\``).join(", ")} | ${evidence} | ${escapeTable(surface.summary)} |`);
  }
}

function isReleaseBlockingBlocker(blocker) {
  return RELEASE_BLOCKING_REPORT_BLOCKERS.has(blocker.classification || "") ||
    !blocker.classification ||
    !NON_BLOCKING_REPORT_BLOCKERS.has(blocker.classification);
}

function isNonBlockingBlocker(blocker) {
  return NON_BLOCKING_REPORT_BLOCKERS.has(blocker.classification || "");
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "unknown";
}

function countBy(values, selector) {
  const output = {};
  for (const value of values) {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  }
  return output;
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function formatCounts(counts) {
  const entries = Object.entries(counts || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `\`${key}\`: ${value}`)
    .join("<br>");
}

function escapeTable(value) {
  return String(value || "")
    .replace(/\|/g, "\\|")
    .replace(/\r?\n/g, "<br>");
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
    } else if (arg === "--docs-output") {
      options.docsOutput = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node generate-phase14-summary.js [--repo-root dir] [--report-dir dir] [--docs-output file]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
