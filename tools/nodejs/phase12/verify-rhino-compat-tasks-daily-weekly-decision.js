#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-tasks-daily-weekly-decision-verification";
const P12_17_NOTE = "P12-17 daily/weekly user-task database provider rejected";

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  decisionDoc: "docs/nodejs/design/TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md",
  compatTasks: "docs/nodejs/design/COMPAT_TASKS_V1.md",
  rhinoTasks: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  workManager: "docs/nodejs/design/WORK_MANAGER.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-tasks-daily-weekly-decision-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTasksDailyWeeklyDecision",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      providerRejected: checkById(checks, "decision-doc-provider-rejected"),
      runtimeStableErrors: checkById(checks, "runtime-stable-errors"),
      statusManifest: checkById(checks, "status-manifest-p12-17"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat tasks daily/weekly decision verification: " + report.decision.status);
  console.log("Rhino compat tasks daily/weekly policy violations: " + policyViolations.length);
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
  const compatTasksRuntime = runtimeFunctionBlock(files.runtime.text, "function __autojs6_rhino_compat_tasks()");

  return [
    check(
      "decision-doc-provider-rejected",
      includesAll(files.decisionDoc.text, [
        "Status: P12-17 rejected provider promotion",
        "addDailyTask(options?)",
        "addWeeklyTask(options?)",
        "queryTimedTasks(options?)",
        "getTimedTask(id, options?)",
        "removeTimedTask(id, options?)",
        "updateTask(task)",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        "work_manager.schedulePeriodic()",
        "must not map mechanically",
        "Migration Guidance",
      ]),
      "Decision doc must reject daily/weekly provider promotion, define stable errors, and include migration guidance.",
    ),
    check(
      "future-provider-preconditions",
      includesAll(files.decisionDoc.text, [
        "Task ownership",
        "Database schema",
        "Migration snapshot",
        "Update/delete policy",
        "Scheduler backend policy",
        "Packaged APK policy",
        "Error policy",
      ]),
      "Decision doc must list provider preconditions for future daily/weekly task database promotion.",
    ),
    check(
      "runtime-stable-errors",
      includesAll(compatTasksRuntime, [
        "addDailyTask: function(options)",
        "__autojs6_rhino_compat_tasks_reject_wall_clock(\"tasks.addDailyTask\")",
        "addWeeklyTask: function(options)",
        "__autojs6_rhino_compat_tasks_reject_wall_clock(\"tasks.addWeeklyTask\")",
        "queryTimedTasks: function(options)",
        "getTimedTask: function(taskId, options)",
        "removeTimedTask: function(taskId, options)",
        "updateTask: function(task)",
        "__autojs6_rhino_compat_tasks_reject_update()",
      ]) &&
        !compatTasksRuntime.includes(".schedulePeriodic(") &&
        !compatTasksRuntime.includes("TimedTaskManager"),
      "Runtime must expose P12-17 aliases/rejection stubs without dispatching daily/weekly provider work.",
    ),
    check(
      "runtime-status-and-tests",
      files.runtime.text.includes('"P12-17"') &&
        includesAll(files.workManagerTest.text, [
          "compat.tasks.addDailyTask",
          "compat.tasks.addWeeklyTask",
          "compat.tasks.updateTask",
          "compat.tasks.queryTimedTasks",
          "compat.tasks.getTimedTask",
          "compat.tasks.removeTimedTask",
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
          "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
          "requests.length === beforeRejected",
        ]) &&
        includesAll(files.conformanceTest.text, [
          "compat.tasks.addDailyTask",
          "compat.tasks.addWeeklyTask",
          "compat.tasks.updateTask",
          "compat.tasks.queryTimedTasks",
          "compat.tasks.getTimedTask",
          "compat.tasks.removeTimedTask",
          "rejected stubs must not dispatch bridge requests",
        ]),
      "Runtime status and instrumentation must cover P12-17 stable rejections and aliases.",
    ),
    check(
      "types",
      includesAll(files.types.text, [
        "addDailyTask(options?: unknown): Promise<never>",
        "addWeeklyTask(options?: unknown): Promise<never>",
        "queryTimedTasks(options?: RhinoTasksQueryOptions)",
        "getTimedTask(id: string | number",
        "removeTimedTask(id: string | number",
        "updateTask(task: unknown): Promise<never>",
      ]) &&
        includesAll(files.typeReadme.text, ["P12-17", "stable rejection stubs", "queryTimedTasks()", "global `tasks`"]),
      "Type declarations and README must expose P12-17 rejected methods and Node-owned aliases without require(\"tasks\") or global tasks.",
    ),
    check(
      "docs-updated",
      includesAll(files.compatTasks.text, ["P12-17 Daily/Weekly Decision", "TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"]) &&
        includesAll(files.rhinoTasks.text, ["P12-17", "compat.tasks.addDailyTask()", "TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"]) &&
        includesAll(files.workManager.text, ["P12-17", "queryTimedTasks()", "user-visible task database provider remains unpromoted"]) &&
        includesAll(files.loaderDesign.text, ["P12-17 adds", "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED", "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"]) &&
        includesAll(files.migration.text, ["P12-17 adds Rhino-name compatibility aliases", "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"]) &&
        includesAll(files.securityModel.text, ["P12-17 keeps daily/weekly", "fail before bridge dispatch"]),
      "Design, loader, migration, WorkManager, and security docs must record the P12-17 decision.",
    ),
    check(
      "status-manifest-p12-17",
      tasksModule &&
        includesAll(JSON.stringify(tasksModule), [P12_17_NOTE, "stable addDailyTask/addWeeklyTask unsupported stubs"]) &&
        entryIs(entries, "tasks.addDailyTask", "unsupported", "p12_17_wall_clock_user_task_database_rejected", "tasks.addDailyTask", "autojs6:compat", null) &&
        entryIs(entries, "tasks.addWeeklyTask", "unsupported", "p12_17_wall_clock_user_task_database_rejected", "tasks.addWeeklyTask", "autojs6:compat", null) &&
        entryIs(entries, "tasks.queryTimedTasks", "partial", "p12_17_query_timed_tasks_node_owned_snapshot_alias", "tasks.queryTimedTasks", "autojs6:compat", "work_manager") &&
        entryIs(entries, "tasks.getTimedTask", "partial", "p12_17_get_timed_task_node_owned_snapshot_alias", "tasks.getTimedTask", "autojs6:compat", "work_manager") &&
        entryIs(entries, "tasks.removeTimedTask", "partial", "p12_17_remove_timed_task_node_owned_id_alias", "tasks.removeTimedTask", "autojs6:compat", "work_manager") &&
        entryIs(entries, "tasks.updateTask", "blocked_by_design", "p12_17_raw_update_task_json_patch_rejected", "tasks.updateTask", "autojs6:compat", null) &&
        statusCounts.partial === 5 &&
        statusCounts.unsupported === 8 &&
        statusCounts.blocked_by_design === 3,
      "Status manifest must record P12-17 task database rejection while preserving task status buckets.",
    ),
    check(
      "matrix-next-step",
      includesAll(files.matrixGenerator.text, [
        P12_17_NOTE,
        "Proceed to P12-18 intent/broadcast task compatibility decision",
      ]) &&
        includesAll(matrixTasksRow, [
          "| 32 | tasks | tasks | partial |",
          "autojs6:compat",
          "work_manager",
          "partial:5",
          "unsupported:8",
          "blocked_by_design:3",
        ]) &&
        (matrixTasksRow.includes("Proceed to P12-18 intent/broadcast task compatibility decision") ||
          matrixTasksRow.includes("Proceed to P12-19 tasks migration examples and smoke") ||
          matrixTasksRow.includes("Proceed to P12-20 threads replacement maturity review")),
      "Generated matrix must advance tasks from P12-17 to P12-18.",
    ),
    check(
      "surface-manifest",
      tasksSurface &&
        tasksSurface.status === "promoted" &&
        (tasksSurface.tasks || []).includes("P12-17") &&
        includesAll(JSON.stringify(tasksSurface), [
          "P12-17 rejects daily/weekly user task database provider promotion",
          "TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md",
          "verify-rhino-compat-tasks-daily-weekly-decision.js",
          "verifyNodeRhinoCompatTasksDailyWeeklyDecision",
        ]) &&
        includesAll(files.surfaceDocs.text, ["tasks.compat", "P12-17", "daily/weekly user task database provider remains rejected"]),
      "Phase 12 surface manifest must include P12-17 tasks.compat evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-17/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatTasksDailyWeeklyDecision") &&
        files.phase12Tasks.text.includes("TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md") &&
        files.phase12Tasks.text.includes("ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED") &&
        files.phase12Tasks.text.includes("schedulePeriodic()"),
      "Phase 12 task plan must mark P12-17 complete with decision and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatTasksDailyWeeklyDecision") &&
        files.gradle.text.includes("verify-rhino-compat-tasks-daily-weekly-decision.js") &&
        files.gradle.text.includes("TASKS_DAILY_WEEKLY_USER_DATABASE_DECISION.md"),
      "Gradle must register verifyNodeRhinoCompatTasksDailyWeeklyDecision.",
    ),
  ];
}

function entryIs(entries, id, status, decision, nodeName, nodeModule, bridgeModule) {
  const entry = entries.get(id);
  return Boolean(entry) &&
    entry.status === status &&
    entry.decision === decision &&
    entry.nodeName === nodeName &&
    entry.nodeModule === nodeModule &&
    entry.bridgeModule === bridgeModule;
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

function runtimeFunctionBlock(text, signature) {
  const start = text.indexOf(signature);
  if (start < 0) {
    return "";
  }
  const next = text.indexOf("  function __autojs6_rhino_compat_facade()", start);
  return text.slice(start, next < 0 ? undefined : next);
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
    "# Rhino Compat Tasks Daily/Weekly Decision Verification",
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
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
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
      console.log("Usage: node verify-rhino-compat-tasks-daily-weekly-decision.js [--repo-root dir] [--report-dir dir]");
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
