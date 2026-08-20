#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-threads-maturity-review-verification";
const P12_20_NOTE = "P12-20 threads maturity review";
const P12_21_NEXT = "Proceed to P12-21 threads migration cookbook";
const P12_22_NEXT = "Proceed to P12-22 compute/process-worker replacement API decision";
const P12_23_NEXT = "Proceed to P12-23 Rhino JSON-only shim POC gate";

const FILES = {
  report: "docs/nodejs/reports/phase12/RHINO_THREADS_MATURITY_REVIEW.md",
  threadsPolicy: "docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md",
  workerDesign: "docs/nodejs/design/WORKER_THREADS.md",
  workerPolicy: "docs/nodejs/releases/V1_1_WORKER_POLICY.md",
  workerEvaluation: "docs/nodejs/reports/WORKER_PRE_GA_EVALUATION.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerBootstrapTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt",
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
    schema: "autojs6-node-rhino-compat-threads-maturity-review-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatThreadsMaturityReview",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      workerDefaultClosed: checkById(checks, "worker-default-closed"),
      noRhinoParityClaim: checkById(checks, "no-rhino-thread-parity"),
      nextPhase: checkById(checks, "matrix-next-step-p12-21"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat threads maturity review verification: " + report.decision.status);
  console.log("Rhino compat threads maturity policy violations: " + policyViolations.length);
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
  const threadsModule = (statusManifest.modules || []).find((module) => module.id === "threads");
  const threadEntries = threadsModule ? threadsModule.apiEntries || [] : [];
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const threadsSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "threads.replacement");
  const matrixThreadsRow = findMatrixRow(files.matrix.text, "threads");
  const statusCounts = countStatuses(threadEntries);

  return [
    check(
      "report-core-decision",
      includesAll(files.report.text, [
        "Status: P12-20 design-locked maturity review",
        "Phase 12 cannot",
        "`worker_threads` remains disabled by default",
        "autojs.nodejs.worker_threads.experimental",
        "native-availability/preflight",
        "`worker_computation` remains `reserved`",
        "`second_execution_slot` remains open",
        "P12-21 cookbook",
        "P12-22",
        "must not reuse the `threads` name",
      ]),
      "P12-20 report must record a design-locked no-default-worker decision and route follow-up to P12-21/P12-22.",
    ),
    check(
      "report-maturity-buckets",
      includesAll(files.report.text, [
        "Default `worker_threads`",
        "Native availability",
        "Capability isolation",
        "Resource cleanup",
        "Packaged worker",
        "Process/execution slot",
        "Rhino `threads` parity",
        "ERR_AUTOJS6_WORKER_DISABLED",
        "zero active workers",
        "blocked_by_design",
      ]),
      "P12-20 report must cover native availability, capability isolation, cleanup, packaged, process/slot, and Rhino parity buckets.",
    ),
    check(
      "threads-policy-p12-20",
      includesAll(files.threadsPolicy.text, [
        "P12-20 Maturity Review",
        "default `worker_threads` remains closed",
        "native-availability/preflight",
        "runnable worker creation, stress, and packaged worker source graph evidence",
        "`worker_computation` remains reserved",
        "`second_execution_slot` remains open",
        "P12-21 cookbook",
        "P12-22",
        "must not reuse `threads`",
      ]),
      "Rhino threads migration policy must include the P12-20 maturity conclusion.",
    ),
    check(
      "worker-design-p12-20",
      includesAll(files.workerDesign.text, [
        "P12-20 rechecked",
        "Rhino `threads` migration",
        "default migration path",
        "RHINO_THREADS_MATURITY_REVIEW.md",
        "not a default replacement for Rhino `threads`",
        "native-availability gated",
        "must be separately named",
        "same-runtime Rhino parity",
      ]),
      "Worker design doc must link the P12-20 maturity review and keep workers out of the default Rhino migration path.",
    ),
    check(
      "worker-default-closed",
      includesAll(files.runtimeCapabilities.text, [
        "WORKER_THREADS_EXPERIMENTAL_PROPERTY",
        "autojs.nodejs.worker_threads.experimental",
        "WORKER_THREADS_EXPERIMENTAL_DEFAULT = false",
        "id = \"worker_threads\"",
        "projectJsonOverrideAllowed = false",
        "packagedApkOverrideAllowed = false",
        "securityLevel = \"high\"",
        "status = \"experimental\"",
      ]) &&
        includesAll(files.runtime.text, [
          "__autojs6_worker_threads_experimental_enabled",
          "__autojs6_worker_threads_disabled_error",
          "ERR_AUTOJS6_WORKER_DISABLED",
          "name === \"worker_threads\"",
          "name === \"node:worker_threads\"",
          "releaseForbidden: true",
        ]),
      "Runtime capabilities and JS bootstrap must keep worker_threads default-off, experimental, and release-forbidden.",
    ),
    check(
      "execution-slot-status",
      includesAll(files.executionModePolicy.text, [
        "MODE_WORKER_COMPUTATION",
        "mode = MODE_WORKER_COMPUTATION",
        "status = \"reserved\"",
        "supportedLaunchSurfaces = emptySet()",
        "resourceQuotaProfile = \"worker_computation_restricted\"",
        "stopSemantics = \"terminate_worker_then_release_ports\"",
        "Worker computation mode remains behind the worker_threads capability isolation gates.",
      ]) &&
        includesAll(files.blockerRegistry.text, [
          "`second_execution_slot`",
          "Default scheduling",
          "packaged behavior",
          "per-slot crash evidence",
          "stress",
          "remain open",
        ]),
      "worker_computation must stay reserved and second_execution_slot must remain open.",
    ),
    check(
      "worker-preflight-and-gates",
      includesAll(files.workerSupport.text, [
        "probeNativeWorkerThreads",
        "worker.preflight.status=available",
        "worker.preflight.status=unavailable",
        "NodeRuntimeErrorCodes.WORKER_DISABLED",
        "embedded_script.worker_active_count",
        "Native worker_threads unavailable",
      ]) &&
        includesAll(files.workerBootstrapTest.text, ["NodeWorkerThreadsBootstrapInstrumentationTest", "probeNativeWorkerThreads"]) &&
        includesAll(files.workerDesignTest.text, ["NodeWorkerThreadsDesignGateInstrumentationTest", "worker_threads"]) &&
        includesAll(files.workerExperimentalTest.text, ["NodeWorkerThreadsExperimentalInstrumentationTest", "assumeNativeWorkerThreadsAvailable"]) &&
        includesAll(files.workerStressTest.text, ["NodeWorkerThreadsResourceStressInstrumentationTest", "assumeNativeWorkerThreadsAvailable"]) &&
        includesAll(files.packagedSmokeTest.text, ["installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk", "assumeNativeWorkerThreadsAvailable"]),
      "Worker bootstrap/design/experimental/stress/packaged gates must remain native-availability gated.",
    ),
    check(
      "worker-doc-evidence",
      includesAll(files.workerPolicy.text, [
        "Keep `worker_threads` disabled by default",
        "not expose it as a release",
        "native availability",
        "ERR_AUTOJS6_WORKER_DISABLED",
        "S8-44 partial-promotion recheck",
        "packaged worker",
      ]) &&
        includesAll(files.workerEvaluation.text, [
          "Do not promote or expose `worker_threads`",
          "disabled by default",
          "native availability",
          "ERR_AUTOJS6_WORKER_DISABLED",
          "failed with `ERR_AUTOJS6_WORKER_DISABLED`",
          "No worker execution case was proven supported",
        ]),
      "Existing worker release/evaluation docs must still support the P12-20 maturity decision.",
    ),
    check(
      "no-rhino-thread-parity",
      includesAll(files.report.text, [
        "No `require(\"threads\")`",
        "No `require(\"autojs6:compat\").threads`",
        "No global `threads` or global `sync`",
        "No default `worker_threads`",
        "No same-runtime `ScriptRuntime` callback execution",
      ]) &&
        includesAll(files.migration.text, [
          "P12-20 rechecked",
          "native-availability gated",
          "`worker_computation` remains reserved",
          "`second_execution_slot` remains open",
        ]) &&
        !files.runtime.text.includes("function __autojs6_rhino_compat_threads") &&
        !files.runtime.text.includes("compat.threads") &&
        !files.runtime.text.includes("name === \"threads\"") &&
        !files.compatTypes.text.includes("compat.threads") &&
        !/\bthreads\s*:/.test(files.compatTypes.text),
      "P12-20 must not add or document Rhino threads parity, compat.threads, or a threads runtime/type facade.",
    ),
    check(
      "status-manifest-p12-20",
      threadsModule &&
        threadsModule.status === "blocked_by_design" &&
        includesAll(JSON.stringify(threadsModule), [P12_20_NOTE, "worker_threads default-off", "second_execution_slot open"]) &&
        threadEntries.length === 6 &&
        statusCounts.blocked_by_design === 6 &&
        threadEntries.every((entry) =>
          entry.status === "blocked_by_design" &&
          entry.asyncMode === "not_applicable" &&
          entry.nodeModule === null &&
          entry.bridgeModule === null &&
          entry.nodeName === null,
        ),
      "Status manifest must record P12-20 while keeping all threads API entries blocked_by_design with no node/bridge mapping.",
    ),
    check(
      "matrix-next-step-p12-21",
      includesAll(files.matrixGenerator.text, [
        P12_20_NOTE,
        P12_21_NEXT,
        P12_22_NEXT,
        P12_23_NEXT,
      ]) &&
        includesAll(matrixThreadsRow, [
          "| 28 | threads | threads | blocked_by_design |",
          "engines",
          "timers/promises",
          "work_manager",
          "blocked_by_design:6",
          "provider_availability",
        ]) &&
        [P12_21_NEXT, P12_22_NEXT, P12_23_NEXT].some((nextStep) => matrixThreadsRow.includes(nextStep)),
      "Generated matrix must preserve the P12-20 threads handoff and may advance through later threads replacement decisions.",
    ),
    check(
      "surface-manifest",
      threadsSurface &&
        threadsSurface.status === "design_locked" &&
        (threadsSurface.tasks || []).includes("P12-20") &&
        includesAll(JSON.stringify(threadsSurface), [
          "P12-20 design-locks Rhino threads replacement maturity",
          "worker_threads remains default-off",
          "worker_computation remains reserved",
          "second_execution_slot stays open",
          "RHINO_THREADS_MATURITY_REVIEW.md",
          "NodeWorkerThreadsTestSupport.kt",
          "verify-rhino-compat-threads-maturity-review.js",
          "verifyNodeRhinoCompatThreadsMaturityReview",
        ]) &&
        includesAll(files.surfaceDocs.text, ["threads.replacement", "P12-20", "worker_computation", "second_execution_slot"]),
      "Phase 12 surface manifest must design-lock threads.replacement with P12-20 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-20/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_THREADS_MATURITY_REVIEW.md",
          "WORKER_THREADS_EXPERIMENTAL_DEFAULT=false",
          "worker_computation",
          "second_execution_slot",
          "ERR_AUTOJS6_WORKER_DISABLED",
          "blocked_by_design",
          "verify-rhino-compat-threads-maturity-review.js",
          "verifyNodeRhinoCompatThreadsMaturityReview",
        ]),
      "Phase 12 task plan must mark P12-20 complete with maturity review, blocker, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatThreadsMaturityReview") &&
        files.gradle.text.includes("verify-rhino-compat-threads-maturity-review.js") &&
        files.gradle.text.includes("RHINO_THREADS_MATURITY_REVIEW.md") &&
        files.gradle.text.includes("NodeWorkerThreadsTestSupport.kt"),
      "Gradle must register verifyNodeRhinoCompatThreadsMaturityReview with maturity review inputs.",
    ),
  ];
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
    "# Rhino Compat Threads Maturity Review Verification",
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
      console.log("Usage: node verify-rhino-compat-threads-maturity-review.js [--repo-root dir] [--report-dir dir]");
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
