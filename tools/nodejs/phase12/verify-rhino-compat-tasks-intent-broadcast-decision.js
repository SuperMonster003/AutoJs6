#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-tasks-intent-broadcast-decision-verification";
const P12_18_NOTE = "P12-18 IntentTask/broadcast provider rejected";

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  workManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkManagerBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  decisionDoc: "docs/nodejs/design/TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md",
  broadcastDesign: "docs/nodejs/design/BROADCAST_TASKS.md",
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
    schema: "autojs6-node-rhino-compat-tasks-intent-broadcast-decision-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTasksIntentBroadcastDecision",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      providerRejected: checkById(checks, "decision-doc-provider-rejected"),
      runtimeStableErrors: checkById(checks, "runtime-stable-errors"),
      statusManifest: checkById(checks, "status-manifest-p12-18"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat tasks intent/broadcast decision verification: " + report.decision.status);
  console.log("Rhino compat tasks intent/broadcast policy violations: " + policyViolations.length);
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
        "Status: P12-18 rejected provider promotion",
        "addIntentTask(options?)",
        "queryIntentTasks(options?)",
        "getIntentTask(id, options?)",
        "removeIntentTask(id, options?)",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "Rejected Runtime Authority",
        "BroadcastReceiver",
        "IntentFilter",
        "PendingIntent",
        "custom broadcast authority",
        "boot, user-unlocked, package-event, startup, exact-alarm",
        "Migration Guidance",
      ]),
      "Decision doc must reject IntentTask/broadcast provider promotion, define stable errors, and include migration guidance.",
    ),
    check(
      "future-provider-preconditions",
      includesAll(files.decisionDoc.text, [
        "Capability split",
        "Task ownership",
        "Receiver lifecycle",
        "Trigger descriptor schema",
        "Trigger payload serialization",
        "Custom broadcast authority",
        "Packaged APK behavior",
        "Background policy",
        "Error policy",
      ]),
      "Decision doc must list receiver, trigger payload, custom authority, packaged, and background-policy preconditions.",
    ),
    check(
      "broadcast-design-p12-18",
      includesAll(files.broadcastDesign.text, [
        "P12-18 rejects provider promotion",
        "addIntentTask()",
        "queryIntentTasks()",
        "getIntentTask()",
        "removeIntentTask()",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "receiver cleanup",
        "TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md",
      ]),
      "BROADCAST_TASKS design gate must record the P12-18 stable-rejection decision.",
    ),
    check(
      "runtime-stable-errors",
      includesAll(compatTasksRuntime, [
        "addIntentTask: function(options)",
        "__autojs6_rhino_compat_tasks_reject_intent(\"tasks.addIntentTask\")",
        "queryIntentTasks: function(options)",
        "__autojs6_rhino_compat_tasks_reject_intent(\"tasks.queryIntentTasks\")",
        "getIntentTask: function(taskId, options)",
        "__autojs6_rhino_compat_tasks_reject_intent(\"tasks.getIntentTask\")",
        "removeIntentTask: function(taskId, options)",
        "__autojs6_rhino_compat_tasks_reject_intent(\"tasks.removeIntentTask\")",
      ]) &&
        !compatTasksRuntime.includes("registerReceiver") &&
        !compatTasksRuntime.includes("DynamicBroadcastReceivers") &&
        !compatTasksRuntime.includes("IntentTaskManager"),
      "Runtime must expose P12-18 rejection stubs without registering receivers or touching IntentTask providers.",
    ),
    check(
      "runtime-status-and-tests",
      files.runtime.text.includes('"P12-18"') &&
        includesAll(files.workManagerTest.text, [
          "compat.tasks.addIntentTask",
          "compat.tasks.queryIntentTasks",
          "compat.tasks.getIntentTask",
          "compat.tasks.removeIntentTask",
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
          "requests.length === beforeRejected",
        ]) &&
        includesAll(files.conformanceTest.text, [
          "compat.tasks.addIntentTask",
          "compat.tasks.queryIntentTasks",
          "compat.tasks.getIntentTask",
          "compat.tasks.removeIntentTask",
          "rejected stubs must not dispatch bridge requests",
        ]),
      "Runtime status and instrumentation must cover P12-18 stable rejections and no-dispatch behavior.",
    ),
    check(
      "types",
      includesAll(files.types.text, [
        "addIntentTask(options?: unknown): Promise<never>",
        "queryIntentTasks(options?: unknown): Promise<never>",
        "getIntentTask(id: string | number",
        "removeIntentTask(id: string | number",
      ]) &&
        includesAll(files.typeReadme.text, ["P12-18", "addIntentTask()", "queryIntentTasks()", "global `tasks`"]),
      "Type declarations and README must expose P12-18 rejected methods without require(\"tasks\") or global tasks.",
    ),
    check(
      "docs-updated",
      includesAll(files.compatTasks.text, ["P12-18 Intent/Broadcast Decision", "TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"]) &&
        includesAll(files.rhinoTasks.text, ["P12-18", "compat.tasks.addIntentTask()", "TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"]) &&
        includesAll(files.workManager.text, ["P12-18", "IntentTask", "broadcast receiver provider"]) &&
        includesAll(files.loaderDesign.text, ["P12-18 adds", "addIntentTask()", "custom broadcast authority"]) &&
        includesAll(files.migration.text, ["P12-18", "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED", "raw trigger payloads"]) &&
        includesAll(files.securityModel.text, ["P12-18 keeps `IntentTask`", "fail before bridge dispatch"]),
      "Design, loader, migration, WorkManager, and security docs must record the P12-18 decision.",
    ),
    check(
      "status-manifest-p12-18",
      tasksModule &&
        includesAll(JSON.stringify(tasksModule), [P12_18_NOTE, "stable addIntentTask/queryIntentTasks/getIntentTask/removeIntentTask unsupported stubs"]) &&
        ["tasks.addIntentTask", "tasks.queryIntentTasks", "tasks.getIntentTask", "tasks.removeIntentTask"].every((id) => {
          const entry = entries.get(id);
          return entry &&
            entry.status === "unsupported" &&
            entry.asyncMode === "promise" &&
            entry.nodeModule === "autojs6:compat" &&
            entry.bridgeModule === null &&
            entry.nodeName === id.replace(/^tasks\./, "tasks.") &&
            entry.decision === "p12_18_intent_broadcast_provider_rejected";
        }) &&
        statusCounts.partial === 5 &&
        statusCounts.unsupported === 8 &&
        statusCounts.blocked_by_design === 3,
      "Status manifest must record P12-18 IntentTask/broadcast rejection while preserving task status buckets.",
    ),
    check(
      "matrix-next-step",
      includesAll(files.matrixGenerator.text, [
        P12_18_NOTE,
        "Proceed to P12-19 tasks migration examples and smoke",
      ]) &&
        includesAll(matrixTasksRow, [
          "| 32 | tasks | tasks | partial |",
          "autojs6:compat",
          "work_manager",
          "partial:5",
          "unsupported:8",
          "blocked_by_design:3",
        ]) &&
        (matrixTasksRow.includes("Proceed to P12-19 tasks migration examples and smoke") ||
          matrixTasksRow.includes("Proceed to P12-20 threads replacement maturity review")),
      "Generated matrix must advance tasks from P12-18 to P12-19.",
    ),
    check(
      "surface-manifest",
      tasksSurface &&
        tasksSurface.status === "promoted" &&
        (tasksSurface.tasks || []).includes("P12-18") &&
        includesAll(JSON.stringify(tasksSurface), [
          "P12-18 rejects IntentTask/broadcast provider promotion",
          "TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md",
          "BROADCAST_TASKS.md",
          "verify-rhino-compat-tasks-intent-broadcast-decision.js",
          "verifyNodeRhinoCompatTasksIntentBroadcastDecision",
        ]) &&
        includesAll(files.surfaceDocs.text, ["tasks.compat", "P12-18", "custom broadcast authority"]),
      "Phase 12 surface manifest must include P12-18 tasks.compat evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-18/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatTasksIntentBroadcastDecision") &&
        files.phase12Tasks.text.includes("TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md") &&
        files.phase12Tasks.text.includes("ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED") &&
        files.phase12Tasks.text.includes("custom broadcast authority"),
      "Phase 12 task plan must mark P12-18 complete with decision and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatTasksIntentBroadcastDecision") &&
        files.gradle.text.includes("verify-rhino-compat-tasks-intent-broadcast-decision.js") &&
        files.gradle.text.includes("TASKS_INTENT_BROADCAST_COMPATIBILITY_DECISION.md"),
      "Gradle must register verifyNodeRhinoCompatTasksIntentBroadcastDecision.",
    ),
  ];
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
    "# Rhino Compat Tasks Intent/Broadcast Decision Verification",
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
      console.log("Usage: node verify-rhino-compat-tasks-intent-broadcast-decision.js [--repo-root dir] [--report-dir dir]");
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
