#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase12-summary";
const DEFAULT_DOCS_OUTPUT = "docs/nodejs/reports/phase12/PHASE12_SUMMARY.md";
const REQUIRED_TASKS = Object.freeze([
  ...Array.from({ length: 12 }, (_, index) => `P12-${String(index).padStart(2, "0")}`),
  "P12-12A",
  "P12-12B",
  ...Array.from({ length: 23 }, (_, index) => `P12-${String(index + 13).padStart(2, "0")}`),
]);

const FILES = {
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  pro9: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  testing: "docs/nodejs/TESTING.md",
  phase12DocsSync: "docs/nodejs/reports/phase12/RHINO_DEVELOPER_DOCS_SYNC.md",
};

const REJECTED_DECISIONS = Object.freeze([
  {
    id: "threads.rhino_parity",
    status: "blocked_by_design",
    evidence: "docs/nodejs/reports/phase12/RHINO_THREADS_MIGRATION_COOKBOOK.md",
    summary: "Exact Rhino threads, sync, shared runtime objects, raw Java Thread handles, and default worker_threads parity remain blocked.",
  },
  {
    id: "floaty.raw_window_parity",
    status: "blocked_by_design",
    evidence: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
    summary: "Exact floaty.window/rawWindow raw View wrapper parity is a non-goal; future work must use the JSON-only overlay provider contract.",
  },
  {
    id: "tasks.daily_weekly_intent",
    status: "unsupported",
    evidence: "docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md",
    summary: "Daily/weekly user task database access and IntentTask/broadcast trigger parity stay rejected until dedicated providers exist.",
  },
  {
    id: "rhino.install_globals",
    status: "unsupported",
    evidence: "docs/nodejs/reports/phase12/RHINO_SHIM_DISABLED_PATH.md",
    summary: "rhino.install(), require(\"node:rhino\"), compat.rhino, and default Rhino globals remain unavailable in Node.",
  },
  {
    id: "s13n.utility_siblings",
    status: "unsupported",
    evidence: "docs/nodejs/reports/phase12/RHINO_S13N_BYTES_PROMOTION.md",
    summary: "s13n.color/point/throwable/time are stable unsupported while only s13n.bytes is promoted.",
  },
]);

const DEVICE_PACKAGED_EVIDENCE = Object.freeze([
  {
    id: "ui.compat",
    evidence: "docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md",
    summary: "UI compat has fake/device instrumentation and packaged smoke markers, while live packaged UI remains unpromoted.",
  },
  {
    id: "floaty.overlay",
    evidence: "docs/nodejs/reports/phase12/RHINO_OVERLAY_LIVE_GATE.md",
    summary: "Overlay live gate records permission/settings dry-run, SYSTEM_ALERT_WINDOW packaged metadata, foreground disclosure, and closeAll cleanup evidence.",
  },
  {
    id: "rhino.json_only_shim",
    evidence: "docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md",
    summary: "Rhino JSON-only shim packaged metadata is fail-closed in Phase 12.",
  },
  {
    id: "barcode.qrcode",
    evidence: "docs/nodejs/reports/phase12/RHINO_BARCODE_QRCODE_EXAMPLES.md",
    summary: "Barcode/qrcode has checked migration examples and packaged provider-dependent smoke policy evidence.",
  },
  {
    id: "dual_engine.device_evidence",
    evidence: "docs/nodejs/reports/phase12/RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md",
    summary: "Dual-engine fake device evidence covers selected Rhino/Node compatibility cases and required result fields.",
  },
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const docsOutput = path.resolve(repoRoot, options.docsOutput || DEFAULT_DOCS_OUTPUT);
  const files = loadFiles(repoRoot);
  const summary = buildSummary(files);
  const markdown = renderMarkdown(summary);
  const checks = buildChecks(files, summary, markdown);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-phase12-summary-v1",
    generatedAt: new Date().toISOString(),
    gate: "generateNodeRhinoCompatPhase12Summary",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    ...summary,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      taskCoverage: checkById(checks, "task-coverage"),
      surfaceCounts: checkById(checks, "surface-counts"),
      phase13Evidence: checkById(checks, "phase13-evidence"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(path.dirname(docsOutput), { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), markdown);
  fs.writeFileSync(docsOutput, markdown);
  console.log("Rhino compat Phase 12 summary generation: " + report.decision.status);
  console.log("Rhino compat Phase 12 summary policy violations: " + policyViolations.length);
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

function buildSummary(files) {
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const blockerRegistry = JSON.parse(files.blockerRegistry.text);
  const tasks = parseTasks(files.phase12Tasks.text, surfaceManifest.surfaces || []);
  const surfaces = surfaceManifest.surfaces || [];
  const surfaceStatusCounts = countBy(surfaces, (surface) => surface.status);
  const deferredStatuses = new Set(["planned", "design_locked", "provider_poc"]);
  const promotedSurfaces = surfaces.filter((surface) => surface.status === "promoted").map(surfaceSummary);
  const rejectedSurfaces = surfaces.filter((surface) => surface.status === "rejected").map(surfaceSummary);
  const deferredSurfaces = surfaces.filter((surface) => deferredStatuses.has(surface.status)).map(surfaceSummary);
  const openBlockers = (blockerRegistry.blockers || []).filter((blocker) =>
    ["open", "decision_required", "optional", "waiting_for_android_artifacts"].includes(blocker.status));
  const phase13Recommendations = buildPhase13Recommendations(deferredSurfaces, openBlockers);
  return {
    phase: surfaceManifest.phase,
    taskCoverage: summarizeTaskCoverage(tasks),
    tasks,
    surfaceCounts: {
      total: surfaces.length,
      promoted: promotedSurfaces.length,
      rejected: rejectedSurfaces.length,
      deferred: deferredSurfaces.length,
      byStatus: surfaceStatusCounts,
    },
    promotedSurfaces,
    rejectedSurfaces,
    deferredSurfaces,
    rejectedDecisions: REJECTED_DECISIONS,
    deviceAndPackagedEvidence: DEVICE_PACKAGED_EVIDENCE,
    remainingBlockers: openBlockers.map((blocker) => ({
      id: blocker.id,
      classification: blocker.classification,
      status: blocker.status,
      currentEvidence: blocker.currentEvidence,
      requiredResolution: blocker.requiredResolution,
    })),
    phase13Recommendations,
  };
}

function parseTasks(text, surfaces) {
  const taskById = new Map();
  const regex = /^- \[([ x])\]\s+(P12-\d{2}[A-Z]?):\s+(.+)$/gm;
  let match;
  while ((match = regex.exec(text)) !== null) {
    taskById.set(match[2], {
      id: match[2],
      checked: match[1] === "x",
      title: match[3].trim(),
    });
  }
  const p12_12A = taskById.get("P12-12A");
  const surfaceByTask = new Map();
  surfaces.forEach((surface) => {
    (surface.tasks || []).forEach((task) => surfaceByTask.set(task, surface.id));
  });
  return REQUIRED_TASKS.map((taskId) => {
    const task = taskById.get(taskId);
    const status = task && task.checked
      ? "completed"
      : taskId === "P12-12B" && p12_12A && p12_12A.checked
        ? "not_selected"
        : "open";
    return {
      id: taskId,
      status,
      title: task ? task.title : "missing from checklist",
      surface: surfaceByTask.get(taskId) || null,
    };
  });
}

function summarizeTaskCoverage(tasks) {
  const byStatus = countBy(tasks, (task) => task.status);
  return {
    total: tasks.length,
    completed: byStatus.completed || 0,
    notSelected: byStatus.not_selected || 0,
    open: byStatus.open || 0,
    byStatus,
  };
}

function surfaceSummary(surface) {
  return {
    id: surface.id,
    status: surface.status,
    tasks: surface.tasks || [],
    evidence: surface.evidence || [],
    summary: surface.summary || "",
  };
}

function buildPhase13Recommendations(deferredSurfaces, blockers) {
  const recommendations = [];
  deferredSurfaces.forEach((surface) => {
    if (surface.id === "ui.compat") {
      recommendations.push(recommendation(surface.id, surface.evidence, "Promote live packaged UI only after Activity lifecycle, Back/Recents, disclosure, and cleanup gates close over the existing P12-10 evidence."));
    } else if (surface.id === "floaty.overlay") {
      recommendations.push(recommendation(surface.id, surface.evidence, "Continue the provider path with explicit overlay permission, foreground disclosure, live show/update/drainEvents/close behavior, and no raw floaty window parity."));
    } else if (surface.id === "rhino.json_only_shim") {
      recommendations.push(recommendation(surface.id, surface.evidence, "Keep the JSON-only Rhino shim default-disabled unless packaged metadata, timeout, destroy, and crash evidence are promoted together."));
    } else if (surface.id === "threads.replacement") {
      recommendations.push(recommendation(surface.id, surface.evidence, "Split any worker/process replacement from Rhino thread parity and require scoped paths, message budgets, cleanup, nested-worker denial, and second execution slot evidence."));
    }
  });
  blockers
    .filter((blocker) => blocker.classification === "required_internal" && blocker.status === "open")
    .forEach((blocker) => {
      recommendations.push({
        source: blocker.id,
        evidence: blocker.currentEvidence,
        recommendation: blocker.requiredResolution,
      });
    });
  blockers
    .filter((blocker) => blocker.classification === "conditional" && blocker.status === "decision_required")
    .forEach((blocker) => {
      recommendations.push({
        source: blocker.id,
        evidence: blocker.currentEvidence,
        recommendation: blocker.requiredResolution,
      });
    });
  return recommendations;
}

function recommendation(source, evidence, text) {
  return {
    source,
    evidence: Array.isArray(evidence) && evidence.length > 0 ? evidence.slice(0, 3).join("; ") : "no evidence recorded",
    recommendation: text,
  };
}

function buildChecks(files, summary, markdown) {
  const phase12SummarySurface = summary.promotedSurfaces.find((surface) => surface.id === "phase12.summary");
  return [
    check(
      "task-coverage",
      summary.tasks.length === REQUIRED_TASKS.length &&
        summary.tasks.every((task) => task.surface) &&
        summary.taskCoverage.open === 0 &&
        summary.taskCoverage.completed >= 36 &&
        summary.taskCoverage.notSelected === 1 &&
        summary.tasks.some((task) => task.id === "P12-35" && task.status === "completed"),
      "Phase 12 summary must enumerate P12-00 through P12-35, with P12-12B recorded as not_selected after the Provider path and no open tasks.",
    ),
    check(
      "surface-counts",
      summary.surfaceCounts.promoted > 0 &&
        Object.prototype.hasOwnProperty.call(summary.surfaceCounts, "rejected") &&
        Object.prototype.hasOwnProperty.call(summary.surfaceCounts, "deferred") &&
        summary.surfaceCounts.deferred === summary.deferredSurfaces.length &&
        phase12SummarySurface &&
        phase12SummarySurface.evidence.includes("docs/nodejs/reports/phase12/PHASE12_SUMMARY.md"),
      "Summary must include promoted/rejected/deferred surface counts and record phase12.summary as promoted with report evidence.",
    ),
    check(
      "rejected-decisions",
      summary.rejectedDecisions.length >= 5 &&
        includesAll(JSON.stringify(summary.rejectedDecisions), [
          "threads.rhino_parity",
          "floaty.raw_window_parity",
          "tasks.daily_weekly_intent",
          "rhino.install_globals",
          "s13n.utility_siblings",
        ]),
      "Summary must list rejected or blocked decisions with concrete evidence sources.",
    ),
    check(
      "device-packaged-evidence",
      summary.deviceAndPackagedEvidence.length >= 5 &&
        includesAll(JSON.stringify(summary.deviceAndPackagedEvidence), [
          "RHINO_UI_COMPAT_SMOKE.md",
          "RHINO_OVERLAY_LIVE_GATE.md",
          "RHINO_SHIM_PACKAGED_METADATA_GATE.md",
          "RHINO_BARCODE_QRCODE_EXAMPLES.md",
          "RHINO_DUAL_ENGINE_DEVICE_EVIDENCE.md",
        ]),
      "Summary must list device and packaged evidence from actual Phase 12 reports.",
    ),
    check(
      "phase13-evidence",
      summary.phase13Recommendations.length >= 6 &&
        summary.phase13Recommendations.every((entry) => entry.evidence && entry.evidence !== "no evidence recorded") &&
        includesAll(JSON.stringify(summary.phase13Recommendations), [
          "ui.compat",
          "floaty.overlay",
          "rhino.json_only_shim",
          "threads.replacement",
          "crash_evidence_automation",
          "worker_threads",
        ]),
      "Phase 13 recommendations must be derived from deferred surfaces or blocker registry evidence.",
    ),
    check(
      "source-docs",
      includesAll(files.phase12Tasks.text, ["[x] P12-35", "generate-rhino-compat-phase12-summary.js"]) &&
        includesAll(files.surfaceDocs.text, ["`phase12.summary`", "generateNodeRhinoCompatPhase12Summary"]) &&
        includesAll(files.surfaceManifest.text, ["\"id\": \"phase12.summary\"", "\"status\": \"promoted\""]),
      "Checklist and surface manifest docs must mark P12-35/phase12.summary promoted with generator evidence.",
    ),
    check(
      "markdown",
      includesAll(markdown, [
        "# Phase 12 Rhino Compatibility Summary",
        "## Task Coverage",
        "P12-35",
        "not_selected",
        "## Surface Counts",
        "## Rejected Decisions",
        "## Device And Packaged Evidence",
        "## Phase 13 Recommendations",
      ]),
      "Generated markdown summary must include task coverage, surface counts, rejected decisions, evidence, and Phase 13 recommendations.",
    ),
  ];
}

function renderMarkdown(summary) {
  const lines = [
    "# Phase 12 Rhino Compatibility Summary",
    "",
    "Status: P12-35 Phase 12 summary published.",
    "",
    "This summary is generated from the Phase 12 task checklist, Phase 12 surface manifest, and final blocker registry.",
    "",
    "## Task Coverage",
    "",
    `- Total tasks: ${summary.taskCoverage.total}`,
    `- Completed tasks: ${summary.taskCoverage.completed}`,
    `- Not selected branch tasks: ${summary.taskCoverage.notSelected}`,
    `- Open tasks: ${summary.taskCoverage.open}`,
    "",
    "| Task | Status | Surface | Title |",
    "| --- | --- | --- | --- |",
  ];
  summary.tasks.forEach((task) => {
    lines.push(`| \`${task.id}\` | \`${task.status}\` | \`${task.surface || "missing"}\` | ${escapeTable(task.title)} |`);
  });

  lines.push(
    "",
    "## Surface Counts",
    "",
    `- Total surfaces: ${summary.surfaceCounts.total}`,
    `- Promoted surfaces: ${summary.surfaceCounts.promoted}`,
    `- Rejected surfaces: ${summary.surfaceCounts.rejected}`,
    `- Deferred surfaces: ${summary.surfaceCounts.deferred}`,
    "",
    "| Status | Count |",
    "| --- | ---: |",
  );
  Object.keys(summary.surfaceCounts.byStatus).sort().forEach((status) => {
    lines.push(`| \`${status}\` | ${summary.surfaceCounts.byStatus[status]} |`);
  });

  pushSurfaceSection(lines, "Promoted Surfaces", summary.promotedSurfaces);
  pushSurfaceSection(lines, "Rejected Surfaces", summary.rejectedSurfaces);
  pushSurfaceSection(lines, "Deferred Surfaces", summary.deferredSurfaces);

  lines.push("", "## Rejected Decisions", "", "| Decision | Status | Evidence | Summary |", "| --- | --- | --- | --- |");
  summary.rejectedDecisions.forEach((decision) => {
    lines.push(`| \`${decision.id}\` | \`${decision.status}\` | \`${decision.evidence}\` | ${escapeTable(decision.summary)} |`);
  });

  lines.push("", "## Device And Packaged Evidence", "", "| Surface | Evidence | Summary |", "| --- | --- | --- |");
  summary.deviceAndPackagedEvidence.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.evidence}\` | ${escapeTable(entry.summary)} |`);
  });

  lines.push("", "## Remaining Blockers", "", "| Blocker | Classification | Status | Required Resolution |", "| --- | --- | --- | --- |");
  summary.remainingBlockers.forEach((blocker) => {
    lines.push(`| \`${blocker.id}\` | \`${blocker.classification}\` | \`${blocker.status}\` | ${escapeTable(blocker.requiredResolution)} |`);
  });

  lines.push("", "## Phase 13 Recommendations", "", "| Source | Evidence | Recommendation |", "| --- | --- | --- |");
  summary.phase13Recommendations.forEach((entry) => {
    lines.push(`| \`${entry.source}\` | ${escapeTable(entry.evidence)} | ${escapeTable(entry.recommendation)} |`);
  });

  lines.push(
    "",
    "## Verification",
    "",
    "- Generator: `tools/nodejs/phase12/generate-rhino-compat-phase12-summary.js`",
    "- Gradle: `:app:generateNodeRhinoCompatPhase12Summary`",
    "- Reports: `build/reports/nodejs/phase12-summary.json` and `build/reports/nodejs/phase12-summary.md`",
  );
  return lines.join("\n") + "\n";
}

function pushSurfaceSection(lines, title, surfaces) {
  lines.push("", `## ${title}`, "");
  if (surfaces.length === 0) {
    lines.push("- None.");
    return;
  }
  lines.push("| Surface | Status | Tasks | Summary |", "| --- | --- | --- | --- |");
  surfaces.forEach((surface) => {
    lines.push(`| \`${surface.id}\` | \`${surface.status}\` | ${surface.tasks.map((task) => `\`${task}\``).join(", ")} | ${escapeTable(surface.summary)} |`);
  });
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function includesAll(textOrArray, values) {
  return values.every((value) => textOrArray.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
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
      console.log("Usage: node generate-rhino-compat-phase12-summary.js [--repo-root dir] [--report-dir dir] [--docs-output file]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const key = arg.slice(2).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
    index += 1;
  }
  return options;
}

main();
