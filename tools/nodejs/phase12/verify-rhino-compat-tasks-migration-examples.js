#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-tasks-migration-examples-verification";
const P12_19_NOTE = "P12-19 tasks migration examples and smoke landed";
const P12_20_NEXT = "Proceed to P12-20 threads replacement maturity review";

const FILES = {
  nodeExample: "docs/nodejs/examples/rhino-compat/09-tasks.node.cjs",
  rhinoExample: "docs/nodejs/examples/rhino-compat/09-tasks.rhino.js",
  examplesManifest: "docs/nodejs/examples/rhino-compat/manifest.json",
  examplesReadme: "docs/nodejs/examples/rhino-compat/README.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  examplesVerifier: "tools/nodejs/phase11/verify-rhino-compat-examples.js",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  tasksReport: "docs/nodejs/reports/phase12/RHINO_TASKS_COMPAT.md",
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
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-tasks-migration-examples-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatTasksMigrationExamples",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      examplesCompile: checkById(checks, "syntax-tasks-examples"),
      reportBuckets: checkById(checks, "report-buckets"),
      nextPhase: checkById(checks, "matrix-next-step-p12-20"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat tasks migration examples verification: " + report.decision.status);
  console.log("Rhino compat tasks migration policy violations: " + policyViolations.length);
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

function buildChecks(files, repoRoot) {
  const examplesManifest = JSON.parse(files.examplesManifest.text);
  const statusManifest = JSON.parse(files.statusManifest.text);
  const tasksModule = (statusManifest.modules || []).find((module) => module.id === "tasks");
  const entries = new Map((tasksModule ? tasksModule.apiEntries : []).map((entry) => [entry.id, entry]));
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const tasksSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "tasks.compat");
  const matrixTasksRow = findMatrixRow(files.matrix.text, "tasks");
  const statusCounts = countStatuses(entries);
  const nodeSyntax = checkSyntax(path.join(repoRoot, files.nodeExample.relative));
  const rhinoSyntax = checkSyntax(path.join(repoRoot, files.rhinoExample.relative));
  const tasksExample = (examplesManifest.examples || []).find((entry) => entry.id === "tasks");

  return [
    check(
      "node-example-shape",
      files.nodeExample.text.startsWith("\"nodejs\";") &&
        includesAll(files.nodeExample.text, [
          "require(\"autojs6:compat\")",
          "compat.tasks",
          "async function main()",
          "AUTOJS6_RUN_TASKS_EXAMPLE",
          "tasks.addDisposableTask(",
          "tasks.queryTasks(",
          "tasks.queryTimedTasks(",
          "tasks.getTimedTask(",
          "tasks.removeTask(",
          "tasks.removeTimedTask(",
          "tasks.addDailyTask(",
          "tasks.addWeeklyTask(",
          "tasks.addIntentTask(",
          "tasks.queryIntentTasks(",
          "tasks.getIntentTask(",
          "tasks.removeIntentTask(",
          "tasks.updateTask(",
          "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
          "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
          "main().catch",
        ]),
      "Node tasks example must show supported async disposable APIs, stable unsupported rejections, and blocked updateTask.",
    ),
    check(
      "rhino-example-shape",
      includesAll(files.rhinoExample.text, [
        "tasks.addDisposableTask(",
        "tasks.queryTimedTasks(",
        "tasks.getTimedTask(",
        "tasks.removeTimedTask(",
        "tasks.addDailyTask(",
        "tasks.addWeeklyTask(",
        "tasks.addIntentTask(",
        "tasks.queryIntentTasks(",
        "tasks.getIntentTask(",
        "tasks.removeIntentTask(",
        "tasks.addTask(",
        "tasks.updateTask(",
        "tasks.removeTask(",
        "tasks.daysToTimeFlag(",
        "tasks.timeFlagToDays(",
      ]) && !files.rhinoExample.text.includes("\"nodejs\""),
      "Rhino tasks example must show the legacy task patterns that the Node example buckets.",
    ),
    check(
      "syntax-tasks-examples",
      nodeSyntax.ok && rhinoSyntax.ok,
      `P12-19 tasks examples must pass node --check${nodeSyntax.ok && rhinoSyntax.ok ? "" : ": " + [nodeSyntax.message, rhinoSyntax.message].filter(Boolean).join(" | ")}`,
    ),
    check(
      "example-manifest-docs",
      tasksExample &&
        tasksExample.rhino === "09-tasks.rhino.js" &&
        tasksExample.node === "09-tasks.node.cjs" &&
        tasksExample.status === "static_verified" &&
        Array.isArray(tasksExample.coverage) &&
        tasksExample.coverage.includes("tasks") &&
        tasksExample.coverage.includes("work_manager") &&
        includesAll(files.examplesReadme.text, ["P12-19", "`tasks`", "stable rejection stubs"]) &&
        includesAll(files.examplesDoc.text, ["P12-19 extends", "tasks migration buckets"]) &&
        includesAll(files.migration.text, ["P12-19 adds tasks migration", "verifyNodeRhinoCompatTasksMigrationExamples"]) &&
        includesAll(files.examplesVerifier.text, ["\"tasks\"", "\"work_manager\"", "tasks.addDisposableTask("]),
      "Example manifest, README, docs, migration guide, and static example gate must include the P12-19 tasks pair.",
    ),
    check(
      "report-buckets",
      includesAll(files.tasksReport.text, [
        "Status: P12-19 migration examples and smoke gate",
        "supported_async",
        "unsupported",
        "blocked_by_design",
        "tasks.addDisposableTask",
        "tasks.queryTasks",
        "tasks.queryTimedTasks",
        "tasks.getTimedTask",
        "tasks.removeTask",
        "tasks.removeTimedTask",
        "tasks.addDailyTask",
        "tasks.addWeeklyTask",
        "tasks.addIntentTask",
        "tasks.queryIntentTasks",
        "tasks.getIntentTask",
        "tasks.removeIntentTask",
        "tasks.daysToTimeFlag",
        "tasks.timeFlagToDays",
        "tasks.addTask",
        "tasks.updateTask",
        "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
        "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
        "verifyNodeRhinoCompatTasksMigrationExamples",
      ]),
      "RHINO_TASKS_COMPAT report must bucket supported_async, unsupported, and blocked_by_design task cases.",
    ),
    check(
      "status-manifest-p12-19",
      tasksModule &&
        includesAll(JSON.stringify(tasksModule), [
          P12_19_NOTE,
          "docs/nodejs/examples/rhino-compat/09-tasks",
          "RHINO_TASKS_COMPAT.md",
        ]) &&
        statusCounts.partial === 5 &&
        statusCounts.unsupported === 8 &&
        statusCounts.blocked_by_design === 3,
      "Status manifest must record P12-19 tasks examples without changing task status buckets.",
    ),
    check(
      "matrix-next-step-p12-20",
      includesAll(files.matrixGenerator.text, [
        P12_19_NOTE,
        P12_20_NEXT,
      ]) &&
        includesAll(matrixTasksRow, [
          "| 32 | tasks | tasks | partial |",
          "autojs6:compat",
          "work_manager",
          "partial:5",
          "unsupported:8",
          "blocked_by_design:3",
          P12_20_NEXT,
        ]),
      "Generated matrix must advance tasks from P12-19 to P12-20.",
    ),
    check(
      "surface-manifest",
      tasksSurface &&
        tasksSurface.status === "promoted" &&
        (tasksSurface.tasks || []).includes("P12-19") &&
        includesAll(JSON.stringify(tasksSurface), [
          "P12-19 adds checked tasks migration examples",
          "docs/nodejs/examples/rhino-compat/09-tasks.node.cjs",
          "docs/nodejs/examples/rhino-compat/09-tasks.rhino.js",
          "docs/nodejs/reports/phase12/RHINO_TASKS_COMPAT.md",
          "verify-rhino-compat-tasks-migration-examples.js",
          "verifyNodeRhinoCompatTasksMigrationExamples",
        ]) &&
        includesAll(files.surfaceDocs.text, ["tasks.compat", "P12-19", "RHINO_TASKS_COMPAT.md"]),
      "Phase 12 surface manifest must include P12-19 example/report/gate evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-19/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "docs/nodejs/examples/rhino-compat/09-tasks.node.cjs",
          "docs/nodejs/examples/rhino-compat/09-tasks.rhino.js",
          "docs/nodejs/reports/phase12/RHINO_TASKS_COMPAT.md",
          "supported_async",
          "unsupported",
          "blocked_by_design",
          "verify-rhino-compat-tasks-migration-examples.js",
          "verifyNodeRhinoCompatTasksMigrationExamples",
        ]),
      "Phase 12 task plan must mark P12-19 complete with example, bucket report, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatTasksMigrationExamples") &&
        files.gradle.text.includes("verify-rhino-compat-tasks-migration-examples.js") &&
        files.gradle.text.includes("docs/nodejs/reports/phase12/RHINO_TASKS_COMPAT.md") &&
        files.gradle.text.includes("docs/nodejs/examples/rhino-compat/09-tasks.node.cjs"),
      "Gradle must register verifyNodeRhinoCompatTasksMigrationExamples with example/report inputs.",
    ),
  ];
}

function checkSyntax(file) {
  if (!fs.existsSync(file)) {
    return { ok: false, message: "missing file" };
  }
  const result = childProcess.spawnSync(process.execPath, ["--check", file], {
    encoding: "utf8",
    windowsHide: true,
  });
  return {
    ok: result.status === 0,
    message: String(result.stderr || result.stdout || "").trim(),
  };
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
    "# Rhino Compat Tasks Migration Examples Verification",
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
      console.log("Usage: node verify-rhino-compat-tasks-migration-examples.js [--repo-root dir] [--report-dir dir]");
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
