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
const OPEN_BLOCKER_STATUSES = new Set(["open", "decision_required", "waiting_for_android_artifacts"]);
const DEFERRED_BLOCKER_STATUSES = new Set(["optional"]);
const REJECTED_BLOCKER_STATUSES = new Set(["permanently_unsupported"]);
const PHASE13_REOPENED_BLOCKER_OVERRIDES = new Map([
  [
    "raw_network",
    "P13 reopened raw_network as a gated Desktop/relaxed-capability track; keep the registry status for P12 history.",
  ],
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
      status: policyViolations.length === 0 ? "partial" : "failed",
      phase13TaskComplete: false,
      taskCoverage: checkById(checks, "task-coverage"),
      promotedSurfaceEvidence: checkById(checks, "promoted-surface-evidence"),
      openBlockersNotCompleted: checkById(checks, "open-blockers-not-completed"),
      profileTruthPresent: checkById(checks, "profile-truth-report"),
      conformanceReportsPresent: checkById(checks, "conformance-reports"),
      finalBlockerClosable: false,
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
  if (policyViolations.length > 0 || (options.failOnOpenBlockers && summary.blockerCounts.open > 0)) {
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
    generatedReportCounts: {
      total: generatedReports.length,
      byStatus: reportStatusCounts,
      withPolicyViolations: generatedReports.filter((report) => report.policyViolations > 0).length,
      withBlockers: generatedReports.filter((report) => report.blockers > 0).length,
    },
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
    let phase13Override = "";
    if (blocker.status === "closed") {
      summaryStatus = "completed";
    } else if (DEFERRED_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "deferred";
    } else if (REJECTED_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "rejected";
    } else if (OPEN_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "open";
    }
    if (PHASE13_REOPENED_BLOCKER_OVERRIDES.has(blocker.id)) {
      summaryStatus = "open";
      phase13Override = PHASE13_REOPENED_BLOCKER_OVERRIDES.get(blocker.id);
    }
    return {
      id: blocker.id,
      classification: blocker.classification,
      status: blocker.status,
      summaryStatus,
      phase13Override,
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

function buildChecks(files, generatedReports, summary, markdown) {
  const openBlockersMarkedCompleted = summary.blockers.filter((blocker) =>
    OPEN_BLOCKER_STATUSES.has(blocker.status) && blocker.summaryStatus === "completed");
  const missingTaskSurface = summary.tasks.filter((task) => !task.surface);
  const promotedWithoutEvidence = summary.promotedSurfaces.filter((surface) => surface.evidence.length === 0);
  const docsFinalSurface = [...summary.openSurfaces, ...summary.deferredSurfaces, ...summary.promotedSurfaces]
    .find((surface) => surface.id === "docs.examples.final_gate");
  const reportBasenames = new Set(generatedReports.map((report) => report.basename));
  return [
    check(
      "task-coverage",
      summary.tasks.length === REQUIRED_TASKS.length &&
        summary.tasks.every((task) => REQUIRED_TASKS.includes(task.id)) &&
        missingTaskSurface.length === 0 &&
        summary.tasks.some((task) => task.id === "P13-57" && task.status === "open"),
      "Summary must enumerate P13-00 through P13-57, map every task to a surface, and keep P13-57 open until all Phase 13 blockers close.",
    ),
    check(
      "status-vocabulary",
      markdown.includes("completed") &&
        markdown.includes("open") &&
        markdown.includes("deferred") &&
        markdown.includes("rejected") &&
        summary.surfaceCounts.open > 0 &&
        summary.blockerCounts.open > 0 &&
        summary.blockerCounts.rejected > 0,
      "Generated summary must list completed/open/deferred/rejected status categories without collapsing open work.",
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
        summary.blockers.some((blocker) => blocker.id === "crash_evidence_automation" && blocker.summaryStatus === "completed") &&
        summary.blockers.some((blocker) => blocker.id === "full_heavy_resource_stress" && blocker.summaryStatus === "open") &&
        summary.blockers.some((blocker) => blocker.id === "worker_threads" && blocker.summaryStatus === "open") &&
        summary.blockers.some((blocker) =>
          blocker.id === "raw_network" &&
          blocker.status === "permanently_unsupported" &&
          blocker.summaryStatus === "open" &&
          blocker.phase13Override.includes("P13 reopened raw_network")),
      "Open and decision-required blockers must remain open while closed required blockers are counted completed.",
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
      files.phase13Tasks.text.includes("P13-57 partial landing") &&
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
    "Status: P13-57 summary generator published as a partial final gate.",
    "",
    "This summary is generated from the Phase 13 task checklist, Phase 13 surface manifest, final blocker registry, profile truth report, and generated Phase 13 conformance reports. Open blockers remain open and are not counted as completed.",
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
    const override = blocker.phase13Override ? ` Phase 13 override: ${blocker.phase13Override}` : "";
    lines.push(`| \`${blocker.id}\` | \`${blocker.summaryStatus}\` | \`${blocker.status}\` | \`${blocker.classification}\` | ${escapeTable(blocker.requiredResolution + override)} |`);
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
