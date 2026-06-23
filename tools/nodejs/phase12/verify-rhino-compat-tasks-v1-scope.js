#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-tasks-v1-scope-verification";
const P12_15_NOTE = "P12-15 compat.tasks v1 scope locked";

const FILES = {
  compatTasks: "docs/nodejs/design/COMPAT_TASKS_V1.md",
  rhinoTasks: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  workManager: "docs/nodejs/design/WORK_MANAGER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

const EXPECTED_DECISIONS = new Map([
  ["tasks.__module", ["p12_15_compat_tasks_v1_scope_locked", "p12_16_compat_tasks_v1_facade"]],
  ["tasks.addDailyTask", "p12_15_wall_clock_task_database_deferred"],
  ["tasks.addDisposableTask", ["p12_15_disposable_task_facade_v1", "p12_16_disposable_task_facade"]],
  ["tasks.addIntentTask", "p12_15_intent_task_broadcast_deferred"],
  ["tasks.addTask", "p12_15_raw_task_object_blocked"],
  ["tasks.addWeeklyTask", "p12_15_wall_clock_task_database_deferred"],
  ["tasks.daysToTimeFlag", "p12_15_pure_timeflag_utility_out_of_v1"],
  ["tasks.getIntentTask", "p12_15_intent_task_broadcast_deferred"],
  ["tasks.getTimedTask", ["p12_15_json_task_snapshot_only", "p12_16_json_task_snapshot_query_only"]],
  ["tasks.queryIntentTasks", "p12_15_intent_task_broadcast_deferred"],
  ["tasks.queryTimedTasks", ["p12_15_work_manager_list_node_owned_tasks", "p12_16_query_tasks_node_owned_work"]],
  ["tasks.removeIntentTask", "p12_15_intent_task_broadcast_deferred"],
  ["tasks.removeTask", "p12_15_raw_remove_task_object_blocked_with_id_wrapper"],
  ["tasks.removeTimedTask", ["p12_15_cancel_node_owned_work_task", "p12_16_cancel_node_owned_work_task"]],
  ["tasks.timeFlagToDays", "p12_15_pure_timeflag_utility_out_of_v1"],
  ["tasks.updateTask", "p12_15_raw_task_object_blocked"],
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-tasks-v1-scope-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTasksV1Scope",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      scopeLocked: checkById(checks, "compat-tasks-v1-scope"),
      statusManifest: checkById(checks, "status-manifest-p12-15"),
      noRuntimeFacade: checkById(checks, "no-runtime-facade"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat tasks v1 scope verification: " + report.decision.status);
  console.log("Rhino compat tasks v1 scope policy violations: " + policyViolations.length);
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
  const statusManifest = JSON.parse(files.statusManifest.text);
  const tasksModule = (statusManifest.modules || []).find((module) => module.id === "tasks");
  const entries = new Map((tasksModule ? tasksModule.apiEntries : []).map((entry) => [entry.id, entry]));
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const tasksSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "tasks.compat");
  const matrixTasksRow = findMatrixRow(files.matrix.text, "tasks");
  const statusCounts = countStatuses(entries);

  return [
    check(
      "compat-tasks-v1-scope",
      includesAll(files.compatTasks.text, [
        "P12-15 design-locked",
        "compat.tasks.addDisposableTask",
        "compat.tasks.removeTask(id)",
        "compat.tasks.queryTasks(options?)",
        "work_manager.scheduleOnce",
        "work_manager.cancel",
        "work_manager.list",
        "JSON-safe",
        "TimedTask",
        "IntentTask",
        "P12-16",
      ]),
      "COMPAT_TASKS_V1 must lock disposable add/remove/query over work_manager and hand runtime work to P12-16.",
    ),
    check(
      "non-goals",
      includesAll(files.compatTasks.text, [
        "tasks.addDailyTask()",
        "tasks.addWeeklyTask()",
        "P12-17",
        "tasks.addIntentTask()",
        "P12-18",
        "tasks.addTask(task)",
        "removeTask(task)",
        "updateTask(task)",
        "blocked by design",
        "require(\"tasks\")",
        "global `tasks`",
      ]),
      "P12-15 design must keep daily/weekly, intent/broadcast, raw task objects, require(\"tasks\"), and globals out of v1.",
    ),
    check(
      "docs-updated",
      includesAll(files.rhinoTasks.text, ["P12-15", "COMPAT_TASKS_V1.md", "compat.tasks.addDisposableTask", "P12-16"]) &&
        includesAll(files.workManager.text, ["P12-15", "COMPAT_TASKS_V1.md", "queryTasks(options?)"]) &&
        includesAll(files.migration.text, ["P12-15", "compat.tasks.addDisposableTask", "P12-16"]) &&
        includesAll(files.securityModel.text, ["P12-15", "compat.tasks", "JSON-safe ids and snapshots only"]) &&
        includesAll(files.typeReadme.text, ["P12-15", "P12-16", "module-only facade"]),
      "Design, migration, security, and type README docs must record the P12-15 compat.tasks v1 scope lock.",
    ),
    check(
      "status-manifest-p12-15",
      tasksModule &&
        tasksModule.status === "partial" &&
        includesAll(JSON.stringify(tasksModule), [
          P12_15_NOTE,
          "only disposable one-shot add/remove/query over work_manager",
          "P12-16",
          "P12-17",
          "P12-18",
        ]) &&
        [...EXPECTED_DECISIONS].every(([id, decisions]) => {
          const entry = entries.get(id);
          const acceptedDecisions = Array.isArray(decisions) ? decisions : [decisions];
          return entry && acceptedDecisions.includes(entry.decision);
        }),
      "Status manifest must add the P12-15 module note and give every tasks.* API a P12-15 decision.",
    ),
    check(
      "status-buckets-stable",
      statusCounts.partial === 5 &&
        statusCounts.unsupported === 8 &&
        statusCounts.blocked_by_design === 3 &&
        entryStatus(entries, "tasks.addDisposableTask", "partial", ["scheduleOnce", "tasks.addDisposableTask"]) &&
        entryStatus(entries, "tasks.queryTimedTasks", "partial", ["list", "tasks.queryTasks"]) &&
        entryStatus(entries, "tasks.removeTimedTask", "partial", ["cancel", "tasks.removeTask"]) &&
        entryStatus(entries, "tasks.removeTask", "blocked_by_design", null),
      "P12-15 must keep tasks status buckets stable while narrowing the future compat.tasks v1 facade.",
    ),
    check(
      "matrix-updated",
      includesAll(files.matrixGenerator.text, [
        P12_15_NOTE,
        "Implement P12-16 compat.tasks disposable facade",
        "Proceed to P12-17 daily/weekly user-task database decision",
      ]) &&
        includesAll(matrixTasksRow, [
          "| 32 | tasks | tasks | partial |",
          "work_manager",
          "partial:5",
          "unsupported:8",
          "blocked_by_design:3",
        ]) &&
        (matrixTasksRow.includes("Implement P12-16 compat.tasks disposable facade") ||
          matrixTasksRow.includes("Proceed to P12-17 daily/weekly user-task database decision")),
      "Generated matrix must point tasks to P12-16 or later after P12-15 scope lock.",
    ),
    check(
      "surface-manifest-design-locked",
      tasksSurface &&
        ["design_locked", "promoted"].includes(tasksSurface.status) &&
        (tasksSurface.tasks || []).includes("P12-15") &&
        includesAll(JSON.stringify(tasksSurface), [
          "P12-15 locks compat.tasks v1 scope",
          "COMPAT_TASKS_V1.md",
          "verify-rhino-compat-tasks-v1-scope.js",
          "verifyNodeRhinoCompatTasksV1Scope",
        ]) &&
        includesAll(files.surfaceDocs.text, ["tasks.compat", "P12-15", "P12-16", "P12-17", "P12-18"]),
      "Phase 12 surface manifest must mark tasks.compat design_locked with P12-15 evidence.",
    ),
    check(
      "no-runtime-facade",
      !files.runtime.text.includes("function __autojs6_limited_tasks") &&
        !/__autojs6_rhino_compat_basic_global_names[^\n]+\"tasks\"/.test(files.runtime.text) &&
        includesAll(files.workManagerTypes.text, ["scheduleOnce", "cancel", "list", "WorkTask"]),
      "P12-15 must not expose require(\"tasks\"), task globals, or a separate tasks provider; P12-16 may add module-only compat.tasks within the locked scope.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-15/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatTasksV1Scope") &&
        files.phase12Tasks.text.includes("COMPAT_TASKS_V1.md") &&
        files.phase12Tasks.text.includes("TimedTask") &&
        files.phase12Tasks.text.includes("IntentTask"),
      "Phase 12 task plan must mark P12-15 complete with scope-lock evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatTasksV1Scope") &&
        files.gradle.text.includes("verify-rhino-compat-tasks-v1-scope.js") &&
        files.gradle.text.includes("COMPAT_TASKS_V1.md"),
      "Gradle must register verifyNodeRhinoCompatTasksV1Scope.",
    ),
  ];
}

function entryStatus(entries, id, status, nodeNames) {
  const entry = entries.get(id);
  if (!entry || entry.status !== status) {
    return false;
  }
  const acceptedNodeNames = Array.isArray(nodeNames) ? nodeNames : [nodeNames];
  if (nodeNames !== undefined && !acceptedNodeNames.includes(entry.nodeName)) {
    return false;
  }
  return true;
}

function countStatuses(entries) {
  const counts = { partial: 0, unsupported: 0, blocked_by_design: 0 };
  for (const entry of entries.values()) {
    if (Object.prototype.hasOwnProperty.call(counts, entry.status)) {
      counts[entry.status] += 1;
    }
  }
  return counts;
}

function findMatrixRow(markdown, moduleId) {
  return markdown.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
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

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Tasks V1 Scope Verification",
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
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message || "")} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-tasks-v1-scope.js [--repo-root dir] [--report-dir dir]");
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
