#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-threads-policy-verification";
const MODULE_DECISION = "s11_25_threads_migration_policy";
const ENTRY_DECISIONS = Object.freeze({
  "threads.__module": "s11_25_no_threads_module",
  "threads.start": "s11_25_shared_runtime_thread_blocked",
  "threads.pool": "s11_25_thread_pool_executor_blocked",
  "threads.interrupt": "s11_25_raw_thread_handle_blocked",
  "threads.disposable": "s11_25_blocking_disposable_blocked",
  "threads.sync": "s11_25_synchronizer_lock_blocked",
});

const FILES = {
  design: "docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  workerPolicy: "docs/nodejs/releases/V1_1_WORKER_POLICY.md",
  workerDesign: "docs/nodejs/design/WORKER_THREADS.md",
  longRunningDesign: "docs/nodejs/design/LONG_RUNNING_SERVICE.md",
  isolationPolicy: "docs/nodejs/reports/phase9/ISOLATION_POLICY.md",
  workerWasiBoundary: "docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  rhinoThreadsAugment: "app/src/main/java/org/autojs/autojs/runtime/api/augment/threads/Threads.kt",
  rhinoThreadsApi: "app/src/main/java/org/autojs/autojs/runtime/api/Threads.kt",
  rhinoVolatileDispose: "app/src/main/java/org/autojs/autojs/runtime/api/augment/threads/VolatileDisposeNativeObject.kt",
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
    schema: "autojs6-node-rhino-compat-threads-policy-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatThreadsPolicy",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      threadsBlockedByDesign: checkById(checks, "status-threads-module"),
      noThreadsFacade: checkById(checks, "no-runtime-threads-facade"),
      workerThreadsDefaultDenied: checkById(checks, "worker-default-denial"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat threads policy verification: " + report.decision.status);
  console.log("Rhino compat threads module: blocked_by_design with async/process/work_manager migration");
  console.log("Rhino compat threads policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const threadsModule = moduleById.get("threads");
  const entries = threadsModule ? threadsModule.apiEntries || [] : [];
  const matrixThreadsRow = findMatrixRow(files.matrix.text, "threads");
  const manifestTotalsMatch = totalsMatch(manifest.totals, countManifestStatuses(manifest));

  return [
    check(
      "rhino-source-shape",
      includesAll(files.rhinoThreadsAugment.text, [
        "fun start",
        "fun pool",
        "fun sync",
        "fun disposable",
        "TimerThread",
        "ThreadPoolExecutor",
        "Synchronizer",
        "VolatileDisposeNativeObject",
      ]) &&
        includesAll(files.rhinoThreadsApi.text, [
          "ScriptRuntime",
          "TimerThread",
          "ThreadPoolExecutor",
          "AtomicLong",
          "ReentrantLock",
        ]) &&
        includesAll(files.rhinoVolatileDispose.text, [
          "blockedGet",
          "blockedGetOrThrow",
          "setAndNotify",
          "ReentrantLock",
        ]),
      "Rhino source evidence must still show shared ScriptRuntime threads, ThreadPoolExecutor, Synchronizer, and blocking VolatileDispose semantics",
    ),
    check(
      "design-core-boundary",
      includesAll(files.design.text, [
        "S11-25",
        "Do not add `require(\"threads\")`",
        "`compat.threads`",
        "global `threads`",
        "global `sync`",
        "default `worker_threads`",
        "shared `ScriptRuntime`",
        "`ThreadPoolExecutor`",
        "`Synchronizer`",
        "`VolatileDisposeNativeObject`",
      ]),
      "threads migration policy must block Rhino thread facade, global sync, default worker_threads, and shared runtime/raw Java objects",
    ),
    check(
      "design-api-mapping",
      includesAll(files.design.text, [
        "## API Mapping",
        "`threads.__module`",
        "`threads.start(fn)`",
        "`threads.pool(options)`",
        "`threads.interrupt(thread)`",
        "`threads.disposable()`",
        "`sync(fn, lock)` / `threads.sync`",
        "blocked_by_design",
        "`AbortController`",
        "`engines.execScriptFile()`",
        "`work_manager`",
      ]),
      "threads migration policy must map each Rhino threads API to blocked_by_design and named alternatives",
    ),
    check(
      "design-examples",
      includesAll(files.design.text, [
        "### `threads.start(fn)`",
        "### `threads.disposable()`",
        "### `sync(fn, lock)`",
        "### `threads.pool(options)`",
        "Promise.race",
        "createAsyncCounter",
        "engines.execScriptFile",
        "workManager.scheduleOnce",
      ]),
      "threads migration policy must include examples for start, disposable, sync, pool, engines, and work_manager",
    ),
    check(
      "status-threads-module",
      threadsModule &&
        threadsModule.status === "blocked_by_design" &&
        threadsModule.asyncMode === "not_applicable" &&
        threadsModule.decision === MODULE_DECISION &&
        includesAll(threadsModule.nodeModules || [], ["engines", "timers/promises", "work_manager"]) &&
        includesAll(threadsModule.bridgeModules || [], ["engines", "work_manager"]),
      "status manifest must mark threads as S11-25 blocked_by_design with async/process/work_manager migration targets",
    ),
    check(
      "status-threads-entries",
      Object.entries(ENTRY_DECISIONS).every(([id, decision]) => entryIs(entries, id, decision)) &&
        Object.keys(ENTRY_DECISIONS).every((id) => entriesFor(entries, id).length === 1) &&
        entries.every((entry) => entry.status === "blocked_by_design" && entry.asyncMode === "not_applicable" && !entry.nodeModule && !entry.bridgeModule && !entry.nodeName),
      "status manifest must keep all six threads API entries blocked_by_design with S11-25 decisions and no direct node/bridge mapping",
    ),
    check(
      "status-totals",
      manifestTotalsMatch,
      "status manifest totals must match the actual module and API entry status counts",
    ),
    check(
      "matrix-threads-row",
      /\|\s*28\s*\|\s*threads\s*\|\s*threads\s*\|\s*blocked_by_design\s*\|[^|]*engines[^|]*timers\/promises[^|]*work_manager/.test(matrixThreadsRow) &&
        /blocked_by_design:6/.test(matrixThreadsRow) &&
        /provider_availability/.test(matrixThreadsRow),
      "generated matrix must show S11-25 threads as blocked_by_design:6 with engines, timers/promises, and work_manager alternatives",
    ),
    check(
      "migration-guide-s11-25",
      includesAll(files.migration.text, [
        "## Threads And Async Work",
        "`threads.start()`",
        "`threads.pool()`",
        "`threads.disposable()`",
        "`sync(fn, lock)`",
        "`worker_threads` remains disabled by default",
        "`AbortController`",
        "`engines.execScriptFile()`",
        "`work_manager`",
        "RHINO_THREADS_MIGRATION_POLICY.md",
      ]),
      "migration guide must document S11-25 thread migration examples and worker default denial",
    ),
    check(
      "security-model-s11-25",
      includesAll(files.securityModel.text, [
        "S11-25",
        "`threads.start()`",
        "`threads.pool()`",
        "`threads.disposable()`",
        "`sync(fn, lock)`",
        "`blocked_by_design`",
        "`ScriptRuntime`",
        "`ThreadPoolExecutor`",
        "`Synchronizer`",
        "`VolatileDispose`",
        "`compat.threads`",
        "`worker_threads`",
        "`AbortController`",
        "`work_manager`",
        "RHINO_THREADS_MIGRATION_POLICY.md",
      ]),
      "security model must record S11-25 blocked Rhino threads semantics and allowed alternatives",
    ),
    check(
      "worker-policy-s11-25",
      includesAll(files.workerPolicy.text, [
        "S11-25",
        "Rhino `threads` migration",
        "`worker_threads` is not the default replacement",
        "`threads.start()`",
        "`threads.pool()`",
        "`threads.disposable()`",
        "`sync(fn,",
        "`AbortController`",
        "`work_manager`",
        "RHINO_THREADS_MIGRATION_POLICY.md",
      ]),
      "worker policy must state that worker_threads is not a default Rhino threads replacement",
    ),
    check(
      "existing-worker-boundaries",
        includesAll(files.workerDesign.text, [
        "worker_threads.experimental=false",
        "disabled in normal AutoJs6 Safe Node Profile runs",
        "pure JavaScript computation",
        "without claiming runnable worker support",
      ]) &&
        includesAll(files.workerWasiBoundary.text, [
          "`worker_threads` is still default disabled",
          "`require(\"worker_threads\")`",
          "`require(\"node:worker_threads\")`",
          "fail closed",
        ]) &&
        includesAll(files.isolationPolicy.text, [
          "`worker_threads` stays disabled by default",
          "future process-worker API",
          "must not claim exact `worker_threads` semantics",
        ]) &&
        includesAll(files.longRunningDesign.text, [
          "`worker_computation`",
          "reserved",
        ]),
      "existing worker/isolation/long-running docs must continue to support S11-25 non-default worker policy",
    ),
    check(
      "loader-design-s11-25",
      includesAll(files.loaderDesign.text, [
        "S11-25",
        "does not add `compat.threads`",
        "`require(\"threads\")`",
        "global `threads`",
        "global `sync`",
        "`threads.disposable()`",
        "`sync(fn, lock)`",
        "default `worker_threads`",
        "no `threads`, `sync`, or `worker_threads` compat type surface",
      ]),
      "compat loader design must record S11-25 as design-only with no threads runtime/type facade",
    ),
    check(
      "worker-default-denial",
      includesAll(files.runtime.text, [
        "__autojs6_worker_threads_experimental_enabled",
        "autojs.nodejs.worker_threads.experimental",
        "__autojs6_worker_threads_disabled_error",
        "ERR_AUTOJS6_WORKER_DISABLED",
        "name === \"worker_threads\"",
      ]) &&
        includesAll(files.workerPolicy.text, [
          "autojs.nodejs.worker_threads.experimental=false",
          "keep `worker_threads` outside the stable surface",
          "do not expose a release opt-in switch to users",
        ]),
      "runtime and release docs must keep worker_threads disabled by default with explicit experimental/native-availability gates",
    ),
    check(
      "no-runtime-threads-facade",
      !files.runtime.text.includes("function __autojs6_limited_threads") &&
        !files.runtime.text.includes("function __autojs6_rhino_compat_threads") &&
        !files.runtime.text.includes("compat.threads") &&
        !files.runtime.text.includes("name === \"threads\"") &&
        !files.runtime.text.includes("name === \"node:threads\"") &&
        !files.runtime.text.includes("compat.sync") &&
        !/\bthreads\b/.test(files.compatTypes.text) &&
        !/\bsync\s*[:(]/.test(files.compatTypes.text),
      "S11-25 must not promote a runtime threads facade, global sync, or rhino_compat type surface",
    ),
    check(
      "task-plan-s11-25-complete",
      /\[x\]\s+S11-25/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("RHINO_THREADS_MIGRATION_POLICY.md") &&
        files.taskPlan.text.includes("verifyNodeRhinoCompatThreadsPolicy"),
      "Phase 11 task plan must mark S11-25 complete and name the policy gate",
    ),
    check(
      "gradle-task",
      files.gradle.text.includes("verifyNodeRhinoCompatThreadsPolicy") &&
        files.gradle.text.includes("verify-rhino-compat-threads-policy.js") &&
        files.gradle.text.includes("RHINO_THREADS_MIGRATION_POLICY.md") &&
        files.gradle.text.includes("V1_1_WORKER_POLICY.md"),
      "Gradle must register verifyNodeRhinoCompatThreadsPolicy with policy docs as inputs",
    ),
  ];
}

function entriesFor(entries, id) {
  return entries.filter((entry) => entry.id === id);
}

function entryIs(entries, id, decision) {
  const matching = entriesFor(entries, id);
  if (matching.length === 0) {
    return false;
  }
  return matching.every((entry) => entry.status === "blocked_by_design" && entry.decision === decision);
}

function countManifestStatuses(manifest) {
  const counts = {};
  (manifest.modules || []).forEach((module) => {
    counts[module.status] = (counts[module.status] || 0) + 1;
    (module.apiEntries || []).forEach((entry) => {
      counts[entry.status] = (counts[entry.status] || 0) + 1;
    });
  });
  return counts;
}

function totalsMatch(actual, expected) {
  if (!actual || typeof actual !== "object") {
    return false;
  }
  const keys = new Set([...Object.keys(actual), ...Object.keys(expected)]);
  for (const key of keys) {
    if ((actual[key] || 0) !== (expected[key] || 0)) {
      return false;
    }
  }
  return true;
}

function findMatrixRow(matrix, moduleId) {
  const escaped = moduleId.replace(/\./g, "\\.");
  const pattern = new RegExp(`\\|\\s*\\d+\\s*\\|\\s*${escaped}\\s*\\|`);
  return matrix.split(/\r?\n/).find((line) => pattern.test(line)) || "";
}

function includesAll(textOrArray, values) {
  return values.every((value) => textOrArray.includes(value));
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
    "# Rhino Compat Threads Policy Verification",
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
      console.log("Usage: node verify-rhino-compat-threads-policy.js [--repo-root dir] [--report-dir dir]");
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
