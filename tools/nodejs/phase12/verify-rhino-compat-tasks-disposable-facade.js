#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-tasks-disposable-facade-verification";
const P12_16_NOTE = "P12-16 compat.tasks disposable facade landed";

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  compatTasks: "docs/nodejs/design/COMPAT_TASKS_V1.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  rhinoTasks: "docs/nodejs/design/RHINO_TASKS_WORK_MANAGER_MIGRATION.md",
  workManager: "docs/nodejs/design/WORK_MANAGER.md",
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
    schema: "autojs6-node-rhino-compat-tasks-disposable-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTasksDisposableFacade",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacade: checkById(checks, "runtime-facade"),
      statusManifest: checkById(checks, "status-manifest-p12-16"),
      noRawTasks: checkById(checks, "no-require-or-global-tasks"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat tasks disposable facade verification: " + report.decision.status);
  console.log("Rhino compat tasks disposable facade policy violations: " + policyViolations.length);
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

  return [
    check(
      "runtime-facade",
      includesAll(files.runtime.text, [
        "function __autojs6_rhino_compat_tasks()",
        "addDisposableTask: function(options)",
        "__autojs6_limited_work_manager().scheduleOnce(descriptor)",
        "removeTask: function(taskId, options)",
        "__autojs6_limited_work_manager().cancel(id, input)",
        "queryTasks: function(options)",
        "__autojs6_limited_work_manager().list(input)",
        '"P12-16"',
        "tasks: tasks",
      ]),
      "Runtime must expose module-only compat.tasks addDisposableTask/removeTask/queryTasks over work_manager.",
    ),
    check(
      "runtime-validation",
      includesAll(files.runtime.text, [
        "requires exactly one of path, scriptPath, or entry",
        "requires exactly one of date, time, or delayMs",
        "requires a future date/time",
        "does not accept option",
        "raw TimedTask/IntentTask objects are not accepted",
        "task.type === \"once\"",
      ]),
      "Runtime must validate disposable task descriptors, reject raw task objects, and query one-shot snapshots only.",
    ),
    check(
      "no-require-or-global-tasks",
      !files.runtime.text.includes("function __autojs6_limited_tasks") &&
        !/__autojs6_rhino_compat_basic_global_names[^\n]+\"tasks\"/.test(files.runtime.text),
      "Runtime must not expose require(\"tasks\") or install a global tasks name.",
    ),
    check(
      "types",
      includesAll(files.types.text, [
        "RhinoTasksCompatModule",
        "RhinoDisposableTaskOptions",
        "addDisposableTask(options: RhinoDisposableTaskOptions): Promise<string>",
        "removeTask(id: string",
        "Promise<boolean>",
        "queryTasks(options?: RhinoTasksQueryOptions)",
        "readonly tasks: RhinoTasksCompatModule",
      ]) &&
        includesAll(files.typeReadme.text, ["P12-16", "module-only facade", "does not expose `require(\"tasks\")`"]),
      "Type declarations and README must expose compat.tasks but not require(\"tasks\") or a global tasks surface.",
    ),
    check(
      "instrumentation",
      includesAll(files.workManagerTest.text, [
        "compat.tasks.addDisposableTask",
        "compat.tasks.queryTasks",
        "compat.tasks.removeTask",
        "compat.tasks.global=false",
        "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION",
      ]) &&
        includesAll(files.conformanceTest.text, [
          "autojs6:compat.tasks",
          "compat.tasks.addDisposableTask",
          "compat.tasks.queryTasks",
          "compat.tasks.removeTask",
          "compat.install({ globals: true }) must not install tasks global",
        ]),
      "Instrumentation must cover fake transport routing, strict validation, no global tasks, and conformance exports.",
    ),
    check(
      "docs-updated",
      includesAll(files.compatTasks.text, ["P12-16 implements", "addDisposableTask", "removeTask(id)", "queryTasks(options?)"]) &&
        includesAll(files.loaderDesign.text, ["P12-16 adds `compat.tasks`", "module-only disposable facade", "does not add `require(\"tasks\")`"]) &&
        includesAll(files.rhinoTasks.text, ["P12-16", "require(\"autojs6:compat\").tasks"]) &&
        includesAll(files.workManager.text, ["P12-16 implements", "compat.tasks"]) &&
        includesAll(files.migration.text, ["P12-16 exposes", "compat.tasks.addDisposableTask"]) &&
        includesAll(files.securityModel.text, ["P12-16 implements", "JSON-safe ids and snapshots only"]),
      "Design, loader, migration, security, and WorkManager docs must record the P12-16 facade.",
    ),
    check(
      "status-manifest-p12-16",
      tasksModule &&
        tasksModule.nodeModules.includes("autojs6:compat") &&
        includesAll(JSON.stringify(tasksModule), [
          P12_16_NOTE,
          "addDisposableTask, removeTask(id), and queryTasks",
          "global tasks absent",
        ]) &&
        entryIs(entries, "tasks.__module", "partial", "p12_16_compat_tasks_v1_facade", "tasks") &&
        entryIs(entries, "tasks.addDisposableTask", "partial", "p12_16_disposable_task_facade", "tasks.addDisposableTask") &&
        entryIs(entries, "tasks.queryTimedTasks", "partial", ["p12_16_query_tasks_node_owned_work", "p12_17_query_timed_tasks_node_owned_snapshot_alias"], ["tasks.queryTasks", "tasks.queryTimedTasks"]) &&
        entryIs(entries, "tasks.removeTimedTask", "partial", ["p12_16_cancel_node_owned_work_task", "p12_17_remove_timed_task_node_owned_id_alias"], ["tasks.removeTask", "tasks.removeTimedTask"]) &&
        entryIs(entries, "tasks.removeTask", "blocked_by_design", "p12_15_raw_remove_task_object_blocked_with_id_wrapper", null),
      "Status manifest must record P12-16 compat.tasks promotion while keeping raw removeTask(task) blocked.",
    ),
    check(
      "matrix-updated",
      includesAll(files.matrixGenerator.text, [
        P12_16_NOTE,
        "Proceed to P12-17 daily/weekly user-task database decision",
      ]) &&
        includesAll(matrixTasksRow, [
          "| 32 | tasks | tasks | partial |",
          "autojs6:compat",
          "work_manager",
        ]) &&
        (matrixTasksRow.includes("Proceed to P12-17 daily/weekly user-task database decision") ||
          matrixTasksRow.includes("Proceed to P12-18 intent/broadcast task compatibility decision")),
      "Generated matrix must move tasks to P12-17 after P12-16 lands.",
    ),
    check(
      "surface-manifest-promoted",
      tasksSurface &&
        tasksSurface.status === "promoted" &&
        (tasksSurface.tasks || []).includes("P12-16") &&
        includesAll(JSON.stringify(tasksSurface), [
          "P12-16 promotes",
          "node_bridge_sources.cpp",
          "rhino_compat.d.ts",
          "NodeWorkManagerBridgeInstrumentationTest.kt",
          "verify-rhino-compat-tasks-disposable-facade.js",
          "verifyNodeRhinoCompatTasksDisposableFacade",
        ]) &&
        includesAll(files.surfaceDocs.text, ["tasks.compat", "P12-16", "promotes", "global `tasks`"]),
      "Phase 12 surface manifest must promote tasks.compat with runtime/type/test/gate evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-16/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatTasksDisposableFacade") &&
        files.phase12Tasks.text.includes("ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN") &&
        files.phase12Tasks.text.includes("global `tasks`"),
      "Phase 12 task plan must mark P12-16 complete with disposable facade evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatTasksDisposableFacade") &&
        files.gradle.text.includes("verify-rhino-compat-tasks-disposable-facade.js") &&
        files.gradle.text.includes("NodeWorkManagerBridgeInstrumentationTest.kt"),
      "Gradle must register verifyNodeRhinoCompatTasksDisposableFacade.",
    ),
  ];
}

function entryIs(entries, id, status, decision, nodeName) {
  const entry = entries.get(id);
  const acceptedDecisions = Array.isArray(decision) ? decision : [decision];
  if (!entry || entry.status !== status || !acceptedDecisions.includes(entry.decision)) {
    return false;
  }
  const acceptedNodeNames = Array.isArray(nodeName) ? nodeName : [nodeName];
  if (nodeName !== undefined && !acceptedNodeNames.includes(entry.nodeName)) {
    return false;
  }
  return true;
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
    "# Rhino Compat Tasks Disposable Facade Verification",
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
      console.log("Usage: node verify-rhino-compat-tasks-disposable-facade.js [--repo-root dir] [--report-dir dir]");
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
