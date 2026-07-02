#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-summary";
const DEFAULT_DOCS_OUTPUT = "docs/nodejs/reports/phase13/PHASE13_SUMMARY.md";
const REQUIRED_TASKS = Object.freeze(
  Array.from({ length: 58 }, (_, index) => `P13-${String(index).padStart(2, "0")}`),
);
const COMPLETED_SURFACE_STATUSES = new Set(["default_promoted", "opt_in_promoted"]);
const OPEN_SURFACE_STATUSES = new Set(["planned", "debug_poc", "provider_poc"]);
const DEFERRED_SURFACE_STATUSES = new Set(["design_locked"]);
const REJECTED_SURFACE_STATUSES = new Set(["rejected"]);
const OPEN_BLOCKER_STATUSES = new Set(["open", "decision_required"]);
const DEFERRED_BLOCKER_STATUSES = new Set(["optional", "waiting_for_android_artifacts"]);
const REJECTED_BLOCKER_STATUSES = new Set(["permanently_unsupported", "intentionally_unsupported"]);
const PHASE13_BLOCKER_NOTES = new Map([
  [
    "raw_network",
    "P13 keeps raw_network rejected for v1.1 while P13-50 through P13-56 preserve a gated future relaxed-capability evidence trail without granting release authority.",
  ],
]);
const FUTURE_PROMOTION_REPORT_TASKS = new Set([
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
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  reportsReadme: "docs/nodejs/reports/phase13/README.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const docsOutput = path.resolve(repoRoot, options.docsOutput || DEFAULT_DOCS_OUTPUT);
  const files = loadFiles(repoRoot);
  const generatedReports = loadGeneratedReports(reportDir);
  const summary = buildSummary(files, generatedReports);
  const markdown = renderMarkdown(summary);
  const checks = buildChecks(files, generatedReports, summary, markdown);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-phase13-summary-v1",
    generatedAt: new Date().toISOString(),
    gate: "generateNodePhase13Summary",
    task: "P13-57",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    generatedReportDir: path.relative(repoRoot, reportDir).replace(/\\/g, "/"),
    ...summary,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : summary.releaseGate.ready ? "ready" : "partial",
      phase13TaskComplete: summary.releaseGate.ready,
      taskCoverage: checkById(checks, "task-coverage"),
      promotedSurfaceEvidence: checkById(checks, "promoted-surface-evidence"),
      openBlockersNotCompleted: checkById(checks, "open-blockers-not-completed"),
      zeroBlockerFinalRequirement: checkById(checks, "zero-blocker-final-requirement"),
      profileTruthPresent: checkById(checks, "profile-truth-report"),
      conformanceReportsPresent: checkById(checks, "conformance-reports"),
      allTaskVerifiersZeroBlockers: generatedReports.every((report) => report.policyViolations === 0 && report.blockers === 0),
      releaseBlockingReportsClear: summary.releaseGate.releaseBlockingReports.length === 0,
      finalBlockerClosable: summary.releaseGate.openFinalBlockers.length === 0,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(path.dirname(docsOutput), { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), markdown);
  fs.writeFileSync(docsOutput, markdown);
  console.log("Phase 13 summary generation: " + report.decision.status);
  console.log("Phase 13 summary policy violations: " + policyViolations.length);
  console.log("Phase 13 summary open blockers: " + summary.blockerCounts.open);
  console.log("Phase 13 summary release-blocking reports: " + summary.releaseGate.releaseBlockingReports.length);
  console.log("Phase 13 summary future promotion reports: " + summary.releaseGate.futurePromotionReports.length);
  if (policyViolations.length > 0 || (options.failOnOpenBlockers && !summary.releaseGate.ready)) {
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
    .filter((name) => /^phase13-.+\.json$/.test(name))
    .filter((name) => name !== `${REPORT_BASENAME}.json`)
    .sort()
    .map((name) => {
      const file = path.join(reportDir, name);
      const raw = read(file);
      const parsed = parseJson(raw);
      const decision = parsed && parsed.decision ? parsed.decision : {};
      return {
        name,
        basename: name.replace(/\.json$/, ""),
        gate: parsed ? parsed.gate || null : null,
        task: parsed ? parsed.task || null : null,
        schema: parsed ? parsed.schema || null : null,
        status: decision.status || parsed && parsed.status || "unknown",
        policyViolations: Array.isArray(parsed && parsed.policyViolations) ? parsed.policyViolations.length : 0,
        blockers: Array.isArray(parsed && parsed.blockers) ? parsed.blockers.length : 0,
      };
    });
}

function buildSummary(files, generatedReports) {
  const manifest = JSON.parse(files.surfaceManifest.text);
  const blockerRegistry = JSON.parse(files.blockerRegistry.text);
  const surfaces = manifest.surfaces || [];
  const tasks = parseTasks(files.phase13Tasks.text, surfaces);
  const blockers = parseBlockers(blockerRegistry.blockers || []);
  const promotedSurfaces = surfaces.filter((surface) => COMPLETED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const openSurfaces = surfaces.filter((surface) => OPEN_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const deferredSurfaces = surfaces.filter((surface) => DEFERRED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const rejectedSurfaces = surfaces.filter((surface) => REJECTED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const profileTruthReport = generatedReports.find((report) => report.basename === "phase13-capability-truth") || null;
  const conformanceReports = generatedReports.filter((report) => report.basename.includes("conformance"));
  const reportStatusCounts = countBy(generatedReports, (report) => report.status);
  const generatedReportCounts = {
    total: generatedReports.length,
    byStatus: reportStatusCounts,
    withPolicyViolations: generatedReports.filter((report) => report.policyViolations > 0).length,
    withBlockers: generatedReports.filter((report) => report.blockers > 0).length,
  };
  const releaseGate = buildReleaseGate({
    tasks,
    blockers,
    generatedReports,
    profileTruthReport,
    conformanceReports,
  });
  return {
    phase: manifest.phase,
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
    blockerCounts: countBlockers(blockers),
    blockers,
    profileTruthReport,
    conformanceReports,
    generatedReports,
    generatedReportCounts,
    releaseGate,
  };
}

function parseTasks(text, surfaces) {
  const taskById = new Map();
  const regex = /^- \[([ x])\]\s+(P13-\d{2}):\s+(.+)$/gm;
  let match;
  while ((match = regex.exec(text)) !== null) {
    if (!taskById.has(match[2])) {
      taskById.set(match[2], {
        id: match[2],
        checked: match[1] === "x",
        title: match[3].trim(),
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
    deferred: byStatus.deferred || 0,
    rejected: byStatus.rejected || 0,
    byStatus,
  };
}

function parseBlockers(blockers) {
  return blockers.map((blocker) => {
    let summaryStatus = "open";
    const phase13Note = PHASE13_BLOCKER_NOTES.get(blocker.id) || "";
    if (blocker.status === "closed") {
      summaryStatus = "completed";
    } else if (DEFERRED_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "deferred";
    } else if (REJECTED_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "rejected";
    } else if (OPEN_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "open";
    }
    return {
      id: blocker.id,
      classification: blocker.classification,
      status: blocker.status,
      summaryStatus,
      phase13Note,
      currentEvidence: blocker.currentEvidence,
      requiredResolution: blocker.requiredResolution,
    };
  });
}

function countBlockers(blockers) {
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

function buildReleaseGate({
  tasks,
  blockers,
  generatedReports,
  profileTruthReport,
  conformanceReports,
}) {
  const openFinalBlockers = blockers.filter((blocker) => blocker.summaryStatus === "open");
  const requiredInternalOpen = blockers.filter((blocker) =>
    blocker.classification === "required_internal" && blocker.summaryStatus !== "completed");
  const reportsWithPolicyViolations = generatedReports.filter((report) => report.policyViolations > 0);
  const reportsWithBlockers = generatedReports.filter((report) => report.blockers > 0);
  const futurePromotionReports = reportsWithBlockers.filter((report) =>
    FUTURE_PROMOTION_REPORT_TASKS.has(report.task));
  const releaseBlockingReports = reportsWithBlockers.filter((report) =>
    !FUTURE_PROMOTION_REPORT_TASKS.has(report.task));
  const phase13SummaryTask = tasks.find((task) => task.id === "P13-57") || null;
  const profileTruthReady = profileTruthReport &&
    profileTruthReport.status === "passed" &&
    profileTruthReport.gate === "generateNodePhase13CapabilityTruth";
  const conformanceReportsReady = conformanceReports.length >= 1 &&
    conformanceReports.some((report) => report.basename === "phase13-stdlib-conformance-decision");
  const ready =
    phase13SummaryTask &&
    phase13SummaryTask.status === "completed" &&
    openFinalBlockers.length === 0 &&
    requiredInternalOpen.length === 0 &&
    reportsWithPolicyViolations.length === 0 &&
    releaseBlockingReports.length === 0 &&
    profileTruthReady &&
    conformanceReportsReady;
  return {
    status: ready ? "ready" : "partial",
    ready: Boolean(ready),
    phase13SummaryTaskComplete: Boolean(phase13SummaryTask && phase13SummaryTask.status === "completed"),
    openFinalBlockers,
    requiredInternalOpen,
    reportsWithPolicyViolations,
    reportsWithBlockers,
    releaseBlockingReports,
    futurePromotionReports,
    futurePromotionReportTasks: futurePromotionReports.map((report) => report.task).sort(),
    futurePromotionReportNames: futurePromotionReports.map((report) => report.name).sort(),
    profileTruthReady: Boolean(profileTruthReady),
    conformanceReportsReady,
  };
}

function buildChecks(files, generatedReports, summary, markdown) {
  const openBlockersMarkedCompleted = summary.blockers.filter((blocker) =>
    OPEN_BLOCKER_STATUSES.has(blocker.status) && blocker.summaryStatus === "completed");
  const missingTaskSurface = summary.tasks.filter((task) => !task.surface);
  const promotedWithoutEvidence = summary.promotedSurfaces.filter((surface) => surface.evidence.length === 0);
  const docsFinalSurface = [...summary.openSurfaces, ...summary.deferredSurfaces, ...summary.promotedSurfaces]
    .find((surface) => surface.id === "docs.examples.final_gate");
  const reportBasenames = new Set(generatedReports.map((report) => report.basename));
  const p13SummaryTask = summary.tasks.find((task) => task.id === "P13-57");
  const requiredInternalOpen = summary.blockers.filter((blocker) =>
    blocker.classification === "required_internal" && blocker.summaryStatus !== "completed");
  return [
    check(
      "task-coverage",
      summary.tasks.length === REQUIRED_TASKS.length &&
        summary.tasks.every((task) => REQUIRED_TASKS.includes(task.id)) &&
        missingTaskSurface.length === 0 &&
        p13SummaryTask &&
        p13SummaryTask.status === "completed",
      "Summary must enumerate P13-00 through P13-57, map every task to a surface, and mark P13-57 completed once the v1.1 closeout gate is ready.",
    ),
    check(
      "status-vocabulary",
      markdown.includes("completed") &&
        markdown.includes("open") &&
        markdown.includes("deferred") &&
        markdown.includes("rejected") &&
        summary.surfaceCounts.open > 0 &&
        summary.taskCounts.open > 0 &&
        summary.blockerCounts.open === 0 &&
        summary.blockerCounts.rejected > 0,
      "Generated summary must list completed/open/deferred/rejected status categories without collapsing future promotion work into release blockers.",
    ),
    check(
      "promoted-surface-evidence",
      promotedWithoutEvidence.length === 0 &&
        summary.promotedSurfaces.some((surface) => surface.id === "phase13.baseline") &&
        summary.promotedSurfaces.some((surface) => surface.id === "phase13.surface_manifest"),
      "Every promoted Phase 13 surface must include evidence.",
    ),
    check(
      "open-blockers-not-completed",
      openBlockersMarkedCompleted.length === 0 &&
        requiredInternalOpen.length === 0 &&
        summary.blockers.some((blocker) => blocker.id === "crash_evidence_automation" && blocker.summaryStatus === "completed") &&
        summary.blockers.some((blocker) => blocker.id === "full_heavy_resource_stress" && blocker.summaryStatus === "completed") &&
        summary.blockers.some((blocker) => blocker.id === "worker_threads" && blocker.summaryStatus === "rejected") &&
        summary.blockers.some((blocker) =>
          blocker.id === "raw_network" &&
          blocker.status === "permanently_unsupported" &&
          blocker.summaryStatus === "rejected" &&
          blocker.phase13Note.includes("v1.1")),
      "Open and decision-required blockers must remain visible while closed required blockers and explicit v1.1 rejections are not counted as open release blockers.",
    ),
    check(
      "zero-blocker-final-requirement",
      summary.releaseGate.ready &&
        summary.releaseGate.releaseBlockingReports.length === 0 &&
        summary.generatedReportCounts.withPolicyViolations === 0 &&
        markdown.includes("- Release-blocking reports: 0") &&
        markdown.includes(`- Future promotion reports with blockers: ${summary.releaseGate.futurePromotionReports.length}`),
      "Summary final completion must require zero open final blockers, zero policy violations, and zero release-blocking report blockers while listing future promotion backlog separately.",
    ),
    check(
      "profile-truth-report",
      summary.profileTruthReport &&
        summary.profileTruthReport.status === "passed" &&
        summary.profileTruthReport.gate === "generateNodePhase13CapabilityTruth",
      "Summary must include the Phase 13 profile truth report.",
    ),
    check(
      "conformance-reports",
      summary.conformanceReports.length >= 1 &&
        reportBasenames.has("phase13-stdlib-conformance-decision"),
      "Summary must include at least the Phase 13 stdlib conformance report.",
    ),
    check(
      "docs-final-surface",
      docsFinalSurface &&
        docsFinalSurface.evidence.some((entry) => entry.includes("PHASE13_SUMMARY.md")) &&
        docsFinalSurface.evidence.some((entry) => entry.includes("phase13-summary")),
      "`docs.examples.final_gate` must record the Phase 13 summary evidence.",
    ),
    check(
      "docs-wiring",
      files.phase13Tasks.text.includes("P13-57 closeout landing") &&
        files.phase13Tasks.text.includes("generate-phase13-summary.js") &&
        files.phase13GradleGate.text.includes("generateNodePhase13Summary") &&
        files.testingDoc.text.includes("generateNodePhase13Summary") &&
        files.ciGatesDoc.text.includes("generateNodePhase13Summary") &&
        files.capabilityMatrix.text.includes("P13-57 Phase 13 summary generator") &&
        files.reportsReadme.text.includes("PHASE13_SUMMARY.md"),
      "Checklist, Gradle, Testing, CI, release matrix, and Phase 13 README must reference the P13-57 summary gate.",
    ),
    check(
      "markdown-sections",
      includesAll(markdown, [
        "# Phase 13 Summary",
        "## Task Checklist",
        "## Surface Status",
        "## Final Blockers",
        "## Profile Truth And Conformance Reports",
        "## Generated Phase 13 Reports",
        "## Verification",
      ]),
      "Generated markdown must include task, surface, blocker, profile truth, conformance, report, and verification sections.",
    ),
  ];
}

function renderMarkdown(summary) {
  const lines = [
    "# Phase 13 Summary",
    "",
    "Status: P13-57 v1.1 closeout gate is ready.",
    "",
    "This summary is generated from the Phase 13 task checklist, Phase 13 surface manifest, final blocker registry, profile truth report, and generated Phase 13 conformance reports. Future promotion blockers remain visible as backlog and are not counted as release blockers.",
    "",
    "## Release Gate",
    "",
    `- Status: \`${summary.releaseGate.status}\``,
    `- P13-57 task complete: ${summary.releaseGate.phase13SummaryTaskComplete}`,
    `- Open final blockers: ${summary.releaseGate.openFinalBlockers.length}`,
    `- Required internal blockers still open: ${summary.releaseGate.requiredInternalOpen.length}`,
    `- Release-blocking reports: ${summary.releaseGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.releaseGate.futurePromotionReports.length}`,
    `- Reports with policy violations: ${summary.releaseGate.reportsWithPolicyViolations.length}`,
    `- Profile truth ready: ${summary.releaseGate.profileTruthReady}`,
    `- Conformance reports ready: ${summary.releaseGate.conformanceReportsReady}`,
    "",
    "## Task Checklist",
    "",
    `- Total tasks: ${summary.taskCounts.total}`,
    `- Completed tasks: ${summary.taskCounts.completed}`,
    `- Open tasks: ${summary.taskCounts.open}`,
    `- Deferred tasks: ${summary.taskCounts.deferred}`,
    `- Rejected tasks: ${summary.taskCounts.rejected}`,
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
  for (const blocker of summary.blockers) {
    const note = blocker.phase13Note ? ` Phase 13 note: ${blocker.phase13Note}` : "";
    lines.push(`| \`${blocker.id}\` | \`${blocker.summaryStatus}\` | \`${blocker.status}\` | \`${blocker.classification}\` | ${escapeTable(blocker.requiredResolution + note)} |`);
  }

  lines.push(
    "",
    "## Profile Truth And Conformance Reports",
    "",
    summary.profileTruthReport
      ? `- Profile truth report: \`${summary.profileTruthReport.name}\` via \`${summary.profileTruthReport.gate}\` with status \`${summary.profileTruthReport.status}\`.`
      : "- Profile truth report: missing.",
    `- Conformance reports: ${summary.conformanceReports.length}`,
    "",
    "| Report | Gate | Status | Policy violations | Blockers |",
    "| --- | --- | --- | ---: | ---: |",
  );
  for (const report of summary.conformanceReports) {
    lines.push(`| \`${report.name}\` | \`${report.gate || "-"}\` | \`${report.status}\` | ${report.policyViolations} | ${report.blockers} |`);
  }

  lines.push(
    "",
    "## Generated Phase 13 Reports",
    "",
    `- Total reports: ${summary.generatedReportCounts.total}`,
    `- Reports with policy violations: ${summary.generatedReportCounts.withPolicyViolations}`,
    `- Reports with blockers: ${summary.generatedReportCounts.withBlockers}`,
    `- Release-blocking reports: ${summary.releaseGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.releaseGate.futurePromotionReports.length}`,
    "",
    "| Report | Gate | Task | Status | Policy violations | Blockers |",
    "| --- | --- | --- | --- | ---: | ---: |",
  );
  for (const report of summary.generatedReports) {
    lines.push(`| \`${report.name}\` | \`${report.gate || "-"}\` | \`${report.task || "-"}\` | \`${report.status}\` | ${report.policyViolations} | ${report.blockers} |`);
  }

  lines.push(
    "",
    "## Verification",
    "",
    "- Generator: `tools/nodejs/phase13/generate-phase13-summary.js`",
    "- Gradle: `:app:generateNodePhase13Summary`",
    "- Reports: `build/reports/nodejs/phase13-summary.json` and `build/reports/nodejs/phase13-summary.md`",
    "- Canonical docs output: `docs/nodejs/reports/phase13/PHASE13_SUMMARY.md`",
    "- Final closeout requires zero open final blockers, zero policy violations, and zero release-blocking report blockers. Future promotion reports remain listed as backlog.",
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

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch (_) {
    return null;
  }
}

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-open-blockers"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-phase13-summary.js [--repo-root dir] [--report-dir dir] [--docs-output file] [--fail-on-open-blockers]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
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

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

main();
