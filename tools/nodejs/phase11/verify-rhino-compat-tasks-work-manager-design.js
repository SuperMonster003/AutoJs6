#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-tasks-work-manager-design-verification";
const MODULE_DECISION = "s11_22_tasks_work_manager_migration_design";
const TASK_MODULE_DECISIONS = ["s11_22_tasks_module_design_gate", "p12_15_compat_tasks_v1_scope_locked"];
const DISPOSABLE_DECISIONS = ["s11_22_disposable_task_schedule_once_candidate", "p12_15_disposable_task_facade_v1"];
const JSON_SNAPSHOT_DECISIONS = ["s11_22_json_task_snapshot_only", "p12_15_json_task_snapshot_only"];
const LIST_DECISIONS = ["s11_22_work_manager_list_node_owned_tasks", "p12_15_work_manager_list_node_owned_tasks"];
const CANCEL_DECISIONS = ["s11_22_cancel_node_owned_work_task", "p12_15_cancel_node_owned_work_task"];
const RAW_TASK_DECISIONS = ["s11_22_raw_task_object_blocked", "p12_15_raw_task_object_blocked", "p12_15_raw_remove_task_object_blocked_with_id_wrapper"];
const WALL_CLOCK_DECISIONS = ["s11_22_wall_clock_task_database_deferred", "p12_15_wall_clock_task_database_deferred"];
const INTENT_DECISIONS = ["s11_22_intent_task_broadcast_deferred", "p12_15_intent_task_broadcast_deferred"];
const TIMEFLAG_DECISIONS = ["s11_22_pure_timeflag_utility_pending", "p12_15_pure_timeflag_utility_out_of_v1"];

const FILES = {
  design: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  workManagerDesign: "docs/nodejs/design/WORK_MANAGER.md",
  broadcastDesign: "docs/nodejs/design/BROADCAST_TASKS.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const checks = buildChecks(files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-tasks-work-manager-design-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTasksWorkManagerDesign",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      tasksMigrationDesigned: checkById(checks, "status-tasks-module"),
      rawTaskObjectsBlocked: checkById(checks, "status-raw-task-objects"),
      noRuntimeTasksFacade: checkById(checks, "no-runtime-tasks-facade"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat tasks/work_manager design verification: " + report.decision.status);
  console.log("Rhino compat tasks migration module: tasks -> work_manager design gate");
  console.log("Rhino compat tasks/work_manager policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const tasksModule = moduleById.get("tasks");
  const entries = new Map((tasksModule ? tasksModule.apiEntries : []).map((entry) => [entry.id, entry]));
  const matrixTasksRow = findMatrixRow(files.matrix.text, "tasks");

  return [
    check(
      "design-core-boundary",
      includesAll(files.design.text, [
        "S11-22",
        "does not add `require(\"tasks\")`",
        "require(\"work_manager\")",
        "TimedTask",
        "IntentTask",
        "one-shot bridge call",
      ]),
      "design doc must state S11-22 is design-only and must not treat persistent Rhino tasks as one-shot bridge calls",
    ),
    check(
      "design-migration-table",
      includesAll(files.design.text, [
        "## Migration Table",
        "tasks.addDisposableTask",
        "workManager.scheduleOnce",
        "tasks.addDailyTask",
        "tasks.addWeeklyTask",
        "schedulePeriodic()",
        "tasks.addIntentTask",
        "BROADCAST_TASKS.md",
        "tasks.updateTask",
        "blocked_by_design",
      ]),
      "design doc must include a migration table covering disposable, daily/weekly, intent, and raw task object APIs",
    ),
    check(
      "design-provider-split",
      includesAll(files.design.text, [
        "Future Provider Split",
        "`work_manager`",
        "`tasks`",
        "`tasks.intent`",
        "`tasks.boot`",
        "proposals only",
        "NodeBridgePermissionManifest.definedCapabilities",
      ]),
      "design doc must keep work_manager as the only current capability and mark future task capabilities as proposals",
    ),
    check(
      "work-manager-design-s11-22",
      includesAll(files.workManagerDesign.text, [
        "S11-22",
        "RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
        "no Rhino `tasks` compatibility facade yet",
        "raw `TimedTask` or `IntentTask`",
        "Rhino Tasks Migration Gate",
      ]),
      "WORK_MANAGER design must reference the S11-22 Rhino tasks migration gate",
    ),
    check(
      "status-tasks-module",
      tasksModule &&
        tasksModule.status === "partial" &&
        tasksModule.asyncMode === "promise" &&
        tasksModule.decision === MODULE_DECISION &&
        tasksModule.nodeModules.includes("work_manager") &&
        tasksModule.bridgeModules.includes("work_manager"),
      "status manifest must mark tasks as a partial S11-22 tasks-to-work_manager migration design",
    ),
    check(
      "status-work-manager-partial-apis",
      entryIs(entries, "tasks.__module", "partial", TASK_MODULE_DECISIONS, "work_manager") &&
        entryIs(entries, "tasks.addDisposableTask", "partial", DISPOSABLE_DECISIONS, "scheduleOnce") &&
        entryIs(entries, "tasks.getTimedTask", "partial", JSON_SNAPSHOT_DECISIONS, "list") &&
        entryIs(entries, "tasks.queryTimedTasks", "partial", LIST_DECISIONS, "list") &&
        entryIs(entries, "tasks.removeTimedTask", "partial", CANCEL_DECISIONS, "cancel"),
      "status manifest must identify the WorkManager-compatible partial tasks APIs",
    ),
    check(
      "status-wall-clock-deferred",
      entryIs(entries, "tasks.addDailyTask", "unsupported", WALL_CLOCK_DECISIONS) &&
        entryIs(entries, "tasks.addWeeklyTask", "unsupported", WALL_CLOCK_DECISIONS),
      "status manifest must keep daily/weekly wall-clock tasks deferred rather than mapping them to periodic WorkManager",
    ),
    check(
      "status-intent-deferred",
      ["tasks.addIntentTask", "tasks.getIntentTask", "tasks.queryIntentTasks", "tasks.removeIntentTask"].every((id) => entryIs(entries, id, "unsupported", INTENT_DECISIONS)),
      "status manifest must keep IntentTask and broadcast-triggered APIs deferred",
    ),
    check(
      "status-raw-task-objects",
      ["tasks.addTask", "tasks.removeTask", "tasks.updateTask"].every((id) => {
        const entry = entries.get(id);
        return entry &&
          entry.status === "blocked_by_design" &&
          entry.asyncMode === "not_applicable" &&
          RAW_TASK_DECISIONS.includes(entry.decision) &&
          !entry.nodeModule &&
          !entry.bridgeModule;
      }),
      "status manifest must block raw TimedTask/IntentTask object APIs by design",
    ),
    check(
      "status-timeflag-pending",
      ["tasks.daysToTimeFlag", "tasks.timeFlagToDays"].every((id) => {
        const entry = entries.get(id);
        return entry &&
          entry.status === "unsupported" &&
          entry.asyncMode === "sync" &&
          TIMEFLAG_DECISIONS.includes(entry.decision);
      }),
      "status manifest must keep pure time-flag utilities pending without scheduler provider access",
    ),
    check(
      "matrix-tasks-row",
      /\|\s*tasks\s*\|\s*tasks\s*\|\s*partial\s*\|[^|]*work_manager/.test(matrixTasksRow) &&
        /blocked_by_design:3/.test(matrixTasksRow) &&
        /partial:5/.test(matrixTasksRow) &&
        /unsupported:8/.test(matrixTasksRow),
      "generated matrix must show S11-22 tasks API split: 5 partial, 8 unsupported, 3 blocked_by_design",
    ),
    check(
      "security-model-s11-22",
      includesAll(files.securityModel.text, [
        "S11-22",
        "tasks.addDisposableTask()",
        "tasks.addDailyTask()",
        "tasks.addWeeklyTask()",
        "tasks.addIntentTask()",
        "raw `TimedTask`",
        "raw `IntentTask`",
        "compat.tasks",
      ]),
      "security model must document S11-22 tasks migration boundaries",
    ),
    check(
      "migration-guide-s11-22",
      includesAll(files.migration.text, [
        "### Scheduled Tasks",
        "tasks.addDisposableTask",
        "workManager.scheduleOnce",
        "tasks.addDailyTask",
        "workManager.schedulePeriodic()",
        "tasks.addIntentTask()",
        "TimedTask",
        "IntentTask",
        "RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
      ]),
      "migration guide must document scheduled task migration and deferred daily/weekly/intent boundaries",
    ),
    check(
      "loader-design-s11-22",
      includesAll(files.loaderDesign.text, [
        "S11-22",
        "does not add `compat.tasks`",
        "require(\"tasks\")",
        "raw `TimedTask`/`IntentTask`",
        "S11-22 is design-only and intentionally adds no `tasks` compat type surface",
      ]),
      "compat loader design must record S11-22 as design-only with no tasks runtime/type facade",
    ),
    check(
      "work-manager-types",
      includesAll(files.workManagerTypes.text, [
        "scheduleOnce",
        "schedulePeriodic",
        "cancel",
        "list",
        "WorkTask",
        "runHistory",
      ]),
      "work_manager type declarations must still expose the controlled scheduled-task MVP",
    ),
    check(
      "broadcast-design-reference",
      includesAll(files.broadcastDesign.text, [
        "IntentTask",
        "work_manager",
        "Task 66 does not implement boot or broadcast-triggered Node tasks",
      ]),
      "broadcast design must remain the reference for IntentTask/startup/boot/package/custom trigger migration",
    ),
    check(
      "task-plan-s11-22-complete",
      /\[x\]\s+S11-22/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("RHINO_TASKS_WORK_MANAGER_MIGRATION.md") &&
        files.taskPlan.text.includes("verifyNodeRhinoCompatTasksWorkManagerDesign"),
      "Phase 11 task plan must mark S11-22 complete and name the design gate",
    ),
    check(
      "gradle-task",
      files.gradle.text.includes("verifyNodeRhinoCompatTasksWorkManagerDesign") &&
        files.gradle.text.includes("verify-rhino-compat-tasks-work-manager-design.js") &&
        files.gradle.text.includes("RHINO_TASKS_WORK_MANAGER_MIGRATION.md"),
      "Gradle must register verifyNodeRhinoCompatTasksWorkManagerDesign with the design doc as an input",
    ),
    check(
      "no-runtime-tasks-facade",
      !/\bconst\s+tasks\s*=/.test(files.runtime.text) &&
        !files.runtime.text.includes("compat.tasks") &&
        !files.runtime.text.includes("function __autojs6_limited_tasks") &&
        !files.runtime.text.includes("function __autojs6_rhino_compat_tasks") &&
        !files.compatTypes.text.includes("tasks:") &&
        !/\bTASKS\b/.test(files.permissionManifest.text) &&
        !files.permissionManifest.text.includes('"tasks"'),
      "S11-22 must not promote runtime compat.tasks, require(\"tasks\"), types, or a canonical tasks capability",
    ),
  ];
}

function entryIs(entries, id, status, decisions, nodeName) {
  const entry = entries.get(id);
  const acceptedDecisions = Array.isArray(decisions) ? decisions : [decisions];
  if (!entry || entry.status !== status || !acceptedDecisions.includes(entry.decision)) {
    return false;
  }
  if (nodeName !== undefined && entry.nodeName !== nodeName) {
    return false;
  }
  return true;
}

function findMatrixRow(matrix, moduleId) {
  const escaped = moduleId.replace(/\./g, "\\.");
  const pattern = new RegExp(`\\|\\s*\\d+\\s*\\|\\s*${escaped}\\s*\\|`);
  return matrix.split(/\r?\n/).find((line) => pattern.test(line)) || "";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Tasks/WorkManager Design Verification",
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
  report.checks.forEach((check) => {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${escapeTable(check.message)} |`);
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
      console.log("Usage: node verify-rhino-compat-tasks-work-manager-design.js [--repo-root dir] [--report-dir dir]");
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
