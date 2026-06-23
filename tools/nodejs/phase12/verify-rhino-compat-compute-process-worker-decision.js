#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-compute-process-worker-decision-verification";
const P12_22_NOTE = "P12-22 compute/process-worker replacement decision deferred";
const P12_23_NEXT = "Proceed to P12-23 Rhino JSON-only shim POC gate";

const THREAD_API_IDS = Object.freeze([
  "threads.__module",
  "threads.disposable",
  "threads.interrupt",
  "threads.pool",
  "threads.start",
  "threads.sync",
]);

const FILES = {
  decisionReport: "docs/nodejs/reports/phase12/RHINO_COMPUTE_PROCESS_WORKER_DECISION.md",
  maturityReport: "docs/nodejs/reports/phase12/RHINO_THREADS_MATURITY_REVIEW.md",
  cookbook: "docs/nodejs/reports/phase12/RHINO_THREADS_MIGRATION_COOKBOOK.md",
  threadsPolicy: "docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md",
  workerDesign: "docs/nodejs/design/WORKER_THREADS.md",
  workerPolicy: "docs/nodejs/releases/V1_1_WORKER_POLICY.md",
  workerEvaluation: "docs/nodejs/reports/WORKER_EXPERIMENTAL_EVALUATION.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.md",
  blockerRegistryJson: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerDesignTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  p12_20Verifier: "tools/nodejs/phase12/verify-rhino-compat-threads-maturity-review.js",
  p12_21Verifier: "tools/nodejs/phase12/verify-rhino-compat-threads-migration-cookbook.js",
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
    schema: "autojs6-node-rhino-compat-compute-process-worker-decision-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatComputeProcessWorkerDecision",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      apiDeferred: checkById(checks, "decision-report-deferred"),
      blockersPreserved: checkById(checks, "blockers-preserved"),
      nextPhase: checkById(checks, "matrix-next-step-p12-23"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat compute/process-worker decision verification: " + report.decision.status);
  console.log("Rhino compat compute/process-worker policy violations: " + policyViolations.length);
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
  const statusManifest = JSON.parse(files.statusManifest.text);
  const threadsModule = (statusManifest.modules || []).find((module) => module.id === "threads");
  const threadEntries = threadsModule ? threadsModule.apiEntries || [] : [];
  const threadStatusCounts = countStatuses(threadEntries);
  const blockerRegistry = JSON.parse(files.blockerRegistryJson.text);
  const blockers = new Map((blockerRegistry.blockers || []).map((entry) => [entry.id, entry]));
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const threadsSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "threads.replacement");
  const matrixThreadsRow = findMatrixRow(files.matrix.text, "threads");
  const decisionSyntax = checkSyntax(path.join(repoRoot, "tools/nodejs/phase12/verify-rhino-compat-compute-process-worker-decision.js"));

  return [
    check(
      "decision-report-deferred",
      includesAll(files.decisionReport.text, [
        "Status: P12-22 deferred replacement API decision",
        "AutoJs6 does not promote a new runtime API",
        "does not use default `worker_threads`",
        "Defer the compute/process-worker replacement API",
        "No Phase 12 `compute_worker`, `process_worker`, `worker`, or `threads`",
        "`worker_computation` remains `reserved`",
        "`second_execution_slot` remains open",
        "`threads.__module`, `threads.start`, `threads.pool`, `threads.interrupt`",
        "Future work may evaluate a separately named API",
        "must not reuse the `threads` name",
        "same-runtime Rhino thread parity",
      ]),
      "P12-22 decision report must explicitly defer the replacement API and preserve Rhino threads denial.",
    ),
    check(
      "future-gate-checklist",
      includesAll(files.decisionReport.text, [
        "Worker/process permissions",
        "Scoped script path",
        "Message size and clone policy",
        "Cleanup and lifecycle",
        "Nested worker denial",
        "Bridge module isolation",
        "Resource stress",
        "Packaged behavior",
        "`second_execution_slot` relation",
        "worker/process permissions, scoped script path, message size, cleanup",
        "nested worker denial, and bridge module isolation remain mandatory gates",
      ]),
      "P12-22 report must define the required gates for any future worker/process API.",
    ),
    check(
      "no-runtime-surface-promoted",
      !includesAny(files.runtime.text, [
        "compute_worker",
        "process_worker",
        "compat.threads",
        "function __autojs6_rhino_compat_threads",
        "name === \"threads\"",
      ]) &&
        !includesAny(files.compatTypes.text, [
          "compat.threads",
          "compute_worker",
          "process_worker",
          "RhinoThreads",
          "threads:",
        ]),
      "P12-22 must not add a compute/process-worker or Rhino threads runtime/type facade.",
    ),
    check(
      "worker-default-and-native-gates",
      includesAll(files.runtimeCapabilities.text, [
        "WORKER_THREADS_EXPERIMENTAL_PROPERTY",
        "autojs.nodejs.worker_threads.experimental",
        "WORKER_THREADS_EXPERIMENTAL_DEFAULT = false",
        "id = \"worker_threads\"",
        "projectJsonOverrideAllowed = false",
        "packagedApkOverrideAllowed = false",
        "status = \"experimental\"",
        "securityLevel = \"high\"",
      ]) &&
        includesAll(files.workerSupport.text, [
          "probeNativeWorkerThreads",
          "worker.preflight.status=available",
          "worker.preflight.status=unavailable",
          "NodeRuntimeErrorCodes.WORKER_DISABLED",
          "embedded_script.worker_active_count",
          "policy.maxMessageBytes",
          "policy.maxQueuedMessages",
        ]) &&
        includesAll(files.workerDesignTest.text, [
          "workerThreadsRemainDesignGatedAndDisabledByDefault",
          "require(\"worker_threads\")",
          "require(\"node:worker_threads\")",
          "require.resolve(\"worker_threads\")",
        ]),
      "Worker capability/default-denial/native-preflight gates must remain in place.",
    ),
    check(
      "worker-computation-reserved",
      includesAll(files.executionModePolicy.text, [
        "MODE_WORKER_COMPUTATION",
        "mode = MODE_WORKER_COMPUTATION",
        "status = \"reserved\"",
        "supportedLaunchSurfaces = emptySet()",
        "allowedProviders = emptyList()",
        "resourceQuotaProfile = \"worker_computation_restricted\"",
        "stopSemantics = \"terminate_worker_then_release_ports\"",
        "Worker computation mode remains behind the worker_threads capability isolation gates.",
      ]),
      "worker_computation execution mode must remain reserved with no supported launch surface.",
    ),
    check(
      "worker-isolation-and-stress-gates",
      includesAll(files.workerDesign.text, [
        "Forbidden Worker Access",
        "Bridge modules are intentionally parent-thread capabilities",
        "maximum message size: 65536 bytes",
        "maximum queued messages: 32",
        "denial of AutoJs bridge modules",
        "nested workers",
        "Packaged APK Policy",
        "P12-22 records the current compute/process-worker decision",
      ]) &&
        includesAll(files.workerExperimentalTest.text, [
          "MessageChannel",
          "maxMessageBytes",
          "maxQueuedMessages",
          "nested",
          "WorkerPool",
        ]) &&
        includesAll(files.workerStressTest.text, [
          "ResourceStress",
          "WorkerPool",
          "worker_threads completion cleanup",
        ]) &&
        includesAll(files.packagedSmokeTest.text, [
          "installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk",
          "assumeNativeWorkerThreadsAvailable",
        ]),
      "Worker design and tests must retain isolation, message, cleanup, stress, nested-denial, and packaged gates.",
    ),
    check(
      "blockers-preserved",
      blockers.get("second_execution_slot") &&
        blockers.get("second_execution_slot").status === "open" &&
        blockers.get("worker_threads") &&
        blockers.get("worker_threads").status === "decision_required" &&
        includesAll(files.blockerRegistry.text, [
          "`second_execution_slot`",
          "Default scheduling",
          "packaged behavior",
          "per-slot crash evidence",
          "stress",
          "remain open",
          "`worker_threads`",
        ]),
      "P12-22 must keep second_execution_slot open and must not resolve the conditional worker_threads decision.",
    ),
    check(
      "worker-docs-p12-22",
      includesAll(files.threadsPolicy.text, [
        "P12-22 Compute/Process Worker Decision",
        "does not promote a compute/process-worker",
        "replacement API",
        "worker_computation",
        "second_execution_slot",
        "worker/process permissions",
        "bridge module",
        "isolation",
      ]) &&
        includesAll(files.workerPolicy.text, [
          "Latest P12-22 compute/process-worker decision: deferred for Phase 12",
          "script-author worker/process runtime API is promoted",
          "native worker",
          "availability",
          "nested worker denial",
          "bridge isolation",
        ]) &&
        includesAll(files.workerEvaluation.text, [
          "Latest P12-22 compute/process-worker decision: deferred for Phase 12",
          "not add a Phase 12 compute/process-worker runtime API",
          "message-size",
          "bridge module isolation",
          "second_execution_slot",
        ]) &&
        includesAll(files.migration.text, [
          "P12-22 explicitly defers",
          "RHINO_COMPUTE_PROCESS_WORKER_DECISION.md",
          "No `compute_worker`, `process_worker`, `worker`, `threads`, or",
          "bridge module isolation",
        ]),
      "Migration, policy, worker design, worker release, and evaluation docs must record the P12-22 deferral.",
    ),
    check(
      "status-manifest-p12-22",
      threadsModule &&
        threadsModule.status === "blocked_by_design" &&
        includesAll(JSON.stringify(threadsModule), [
          P12_22_NOTE,
          "no Phase 12 runtime API is promoted",
          "future APIs must not reuse threads or worker_threads names",
          "worker_computation remains reserved",
          "second_execution_slot remains open",
          "scoped script path",
          "message size",
          "nested worker denial",
          "bridge module isolation",
        ]) &&
        THREAD_API_IDS.every((id) => threadEntries.some((entry) => entry.id === id)) &&
        threadEntries.length === THREAD_API_IDS.length &&
        threadStatusCounts.blocked_by_design === THREAD_API_IDS.length &&
        threadEntries.every((entry) =>
          entry.status === "blocked_by_design" &&
          entry.asyncMode === "not_applicable" &&
          entry.nodeModule === null &&
          entry.bridgeModule === null &&
          entry.nodeName === null,
        ),
      "Status manifest must record P12-22 while keeping all threads API entries blocked_by_design with no node/bridge mapping.",
    ),
    check(
      "matrix-next-step-p12-23",
      includesAll(files.matrixGenerator.text, [
        P12_22_NOTE,
        P12_23_NEXT,
      ]) &&
        includesAll(files.p12_20Verifier.text, [P12_23_NEXT]) &&
        includesAll(files.p12_21Verifier.text, [P12_23_NEXT]) &&
        includesAll(matrixThreadsRow, [
          "| 28 | threads | threads | blocked_by_design |",
          "engines",
          "timers/promises",
          "work_manager",
          "blocked_by_design:6",
          "provider_availability",
          P12_23_NEXT,
        ]),
      "Generated matrix must advance threads from P12-22 to P12-23 while preserving blocked_by_design status.",
    ),
    check(
      "surface-manifest-p12-22",
      threadsSurface &&
        threadsSurface.status === "design_locked" &&
        (threadsSurface.tasks || []).includes("P12-22") &&
        includesAll(JSON.stringify(threadsSurface), [
          "P12-22 defers a compute/process-worker replacement API",
          "RHINO_COMPUTE_PROCESS_WORKER_DECISION.md",
          "verify-rhino-compat-compute-process-worker-decision.js",
          "verifyNodeRhinoCompatComputeProcessWorkerDecision",
          "worker/process permissions",
          "second_execution_slot gates",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "threads.replacement",
          "P12-22 defers",
          "compute/process-worker",
          "bridge module isolation",
          "second_execution_slot",
        ]),
      "Phase 12 surface manifest must include the P12-22 decision, evidence, and gate.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-22/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_COMPUTE_PROCESS_WORKER_DECISION.md",
          "compute_worker",
          "process_worker",
          "worker/process permissions",
          "scoped script path",
          "message size",
          "nested worker denial",
          "bridge module isolation",
          "second_execution_slot",
          "verify-rhino-compat-compute-process-worker-decision.js",
          "verifyNodeRhinoCompatComputeProcessWorkerDecision",
        ]),
      "Phase 12 task plan must mark P12-22 complete with decision, blocker, and gate evidence.",
    ),
    check(
      "syntax-verifier",
      decisionSyntax.ok,
      `P12-22 verifier must pass node --check${decisionSyntax.ok ? "" : ": " + decisionSyntax.message}`,
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatComputeProcessWorkerDecision") &&
        files.gradle.text.includes("verify-rhino-compat-compute-process-worker-decision.js") &&
        files.gradle.text.includes("RHINO_COMPUTE_PROCESS_WORKER_DECISION.md") &&
        files.gradle.text.includes("verifyNodeRhinoCompatThreadsMigrationCookbook"),
      "Gradle must register verifyNodeRhinoCompatComputeProcessWorkerDecision with decision inputs.",
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
  const counts = {};
  for (const entry of entries) {
    counts[entry.status] = (counts[entry.status] || 0) + 1;
  }
  return counts;
}

function findMatrixRow(markdown, moduleId) {
  return markdown.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
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
    "# Rhino Compat Compute/Process Worker Decision Verification",
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
      console.log("Usage: node verify-rhino-compat-compute-process-worker-decision.js [--repo-root dir] [--report-dir dir]");
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
