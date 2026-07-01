#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-worker-resource-rss-stress";
const FUTURE_PROMOTION_ITEMS = Object.freeze([
  {
    id: "native_available_worker_resource_archive",
    classification: "future_promotion",
    requiredEvidence: "Run worker resource stress with worker.preflight.status=available on selected target ABIs/devices.",
  },
  {
    id: "worker_heap_fd_metric_archive",
    classification: "future_promotion",
    requiredEvidence: "Archive worker-specific RSS, Java heap, native heap, fd count, thread count, queue, active worker, and active WorkerPool deltas.",
  },
  {
    id: "worker_no_orphan_recovery_archive",
    classification: "future_promotion",
    requiredEvidence: "Prove no orphan workers, orphan pools, pending messages, or unrecoverable runtime after exception, timeout, parent completion, and crash recovery.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_30_WORKER_RESOURCE_RSS_STRESS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/worker-resource-rss-stress-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_WORKER_RESOURCE_RSS_STRESS.md",
  p14NativeAvailabilityReport: "phase14-worker-native-availability.json",
  p14RunnablePackagedReport: "phase14-worker-runnable-packaged-smoke.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  workerDesignDoc: "docs/nodejs/design/WORKER_THREADS.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 worker resource/RSS stress: " + report.decision.status);
  console.log("Phase 14 worker resource/RSS blockers: " + report.blockers.length);
  console.log("Phase 14 worker resource/RSS policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const generated = relative.endsWith(".json") && !relative.includes("/");
      const file = generated ? path.join(reportDir, relative) : path.join(repoRoot, relative);
      return [
        key,
        {
          relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
          exists: fs.existsSync(file),
          text: read(file),
        },
      ];
    }),
  );
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const p14Native = parseJson(files.p14NativeAvailabilityReport.text);
  const p14Runnable = parseJson(files.p14RunnablePackagedReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const workerSurface = (manifest.surfaces || []).find((entry) => entry.id === "desktop.worker_runtime") || null;
  const scenarios = [
    scenario("source_gate_imports", "P14-28 and P14-29 reports are imported before resource stress can promote", [
      value("schema", plan.schema === "autojs6-node-phase14-worker-resource-rss-stress-v1"),
      value("task", plan.task === "P14-30"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "conditional_skip_native_unavailable"),
      value("promotionState", plan.promotionState === "worker_resource_unproven"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P14-28", "P14-29"])),
      value("nativeUnavailable", (plan.nativePrecondition || {}).nativeAvailable === false),
      value("p14NativeReady", files.p14NativeAvailabilityReport.exists && statusOf(p14Native) === "ready"),
      value("p14RunnableReady", files.p14RunnablePackagedReport.exists && statusOf(p14Runnable) === "ready"),
      value("p14NativeRunnableFalse", (p14Native.decision || {}).nativeRunnableProofReady === false),
      value("p14RunnableExecutedFalse", (p14Runnable.decision || {}).runnableSmokeExecuted === false),
      value("p14PackagedExecutedFalse", (p14Runnable.decision || {}).packagedSmokeExecuted === false),
      token(files.workerSupport, "runnable worker tests remain promotion gates"),
    ]),
    scenario("sequential_and_pool_resource_anchors", "Sequential Worker and WorkerPool stress source anchors are present", [
      token(files.workerStressTest, "fun createAndTerminateWorkersSequentiallyDoesNotGrowResourceCounters()"),
      token(files.workerStressTest, "assumeNativeWorkerThreadsAvailable(\"worker_threads sequential resource stress\")"),
      token(files.workerStressTest, "worker.resource.sequence=0|1|100|100|0|0"),
      token(files.workerStressTest, "assertEquals(\"100\", result.nativeValues[\"embedded_script.worker_created_count\"]"),
      token(files.workerStressTest, "assertResourceGrowthBounded(before, after)"),
      token(files.workerStressTest, "fun workerPoolStressDoesNotLeakAfterFailuresTimeoutsAndTransfers()"),
      token(files.workerStressTest, "worker.resource.pool.stress=14850|true|21|33"),
      token(files.workerStressTest, "worker.resource.pool.transfer=true|16|7|11"),
      token(files.workerStressTest, "worker.resource.pool.failures="),
      token(files.workerStressTest, "worker.resource.pool.shutdown=0|0|104|1|1|0"),
      token(files.workerStressTest, "assertEquals(\"0\", result.nativeValues[\"embedded_script.worker_pool_active_count\"]"),
    ]),
    scenario("cleanup_exception_timeout_anchors", "Cleanup, exception recovery, timeout recovery, and pending message anchors are present", [
      token(files.workerStressTest, "fun workerPoolIsClosedDuringParentCompletionCleanup()"),
      token(files.workerStressTest, "worker.resource.pool.cleanup.before=1|1"),
      token(files.workerStressTest, "assertEquals(\"2\", result.nativeValues[\"embedded_script.worker_cleanup_requested_count\"]"),
      token(files.workerStressTest, "fun workerExceptionDoesNotPoisonNextRun()"),
      token(files.workerStressTest, "worker.resource.throw=true|"),
      token(files.workerStressTest, "worker.resource.recovery=v24.5.0"),
      token(files.workerStressTest, "fun infiniteLoopWorkerTimeoutDoesNotPoisonNextRun()"),
      token(files.workerStressTest, "ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT"),
      token(files.workerStressTest, "worker.resource.timeout.recovery=v24.5.0"),
      token(files.workerStressTest, "fun pendingWorkerMessageIsRejectedDuringParentCompletionCleanup()"),
      token(files.workerStressTest, "worker.resource.cleanup.error="),
      token(files.workerStressTest, "worker_cleanup_pending_message_rejected_count"),
    ]),
    scenario("rss_thread_metric_boundary", "RSS and thread metrics are source-anchored while heap/fd worker proof remains future work", [
      token(files.workerStressTest, "rssKb = readStatusLongKb(pid, \"VmRSS:\")"),
      token(files.workerStressTest, "threadCount = File(\"/proc/$pid/task\").safeCountChildren()"),
      token(files.workerStressTest, "thread count grew too much"),
      token(files.workerStressTest, "RSS grew too much"),
      token(files.workerStressTest, "MAX_THREAD_DELTA = 16"),
      token(files.workerStressTest, "MAX_RSS_DELTA_KB = 262_144L"),
      value("planJavaNativeFdFuture", (plan.stressState || {}).javaHeapNativeHeapFdWorkerProofReady === false),
      token(files.planDoc, "RSS, Java heap, native heap, fd count, thread count"),
      token(files.workerDesignDoc, "coarse RSS/thread snapshots"),
    ]),
    scenario("authority_stays_disabled", "Resource stress source anchors do not grant worker runtime authority", [
      value("resourceStressNotExecuted", (plan.stressState || {}).resourceStressExecuted === false),
      value("authorityUnchanged", (plan.stressState || {}).runtimeAuthorityChanged === false),
      value("defaultNotPromoted", (plan.stressState || {}).defaultWorkerThreadsPromoted === false),
      value("projectPackageNotPromoted", (plan.stressState || {}).projectOrPackagedOptInPromoted === false),
      token(files.runtimeCapabilities, "const val WORKER_THREADS_EXPERIMENTAL_DEFAULT = false"),
      token(files.runtimeCapabilities, "projectJsonOverrideAllowed = false"),
      token(files.runtimeCapabilities, "packagedApkOverrideAllowed = false"),
      token(files.compatibilityProfile, "P14-30 records the worker resource/RSS stress gate as conditional"),
      token(files.securityModel, "P14-30 records the worker resource/RSS stress gate as conditional"),
      token(files.errorCatalog, "P14-30 worker resource/RSS stress decision"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-30 gate", [
      value("surfaceEvidence", workerSurface &&
        workerSurface.status === "design_locked" &&
        (workerSurface.tasks || []).includes("P14-30") &&
        (workerSurface.evidence || []).includes("docs/nodejs/phase14/worker-resource-rss-stress-plan.json") &&
        (workerSurface.evidence || []).includes(":app:verifyNodePhase14WorkerResourceRssStress")),
      token(files.surfaceManifestDoc, "P14-30 adds the conditional worker resource/RSS stress gate"),
      token(files.phase14Tasks, "- [x] P14-30: Add worker resource and RSS stress if native support exists."),
      token(files.phase14Tasks, "Worker resource/RSS stress gate deployed:"),
      token(files.phase14Report, "P14-30 decision: `conditional_skip_native_unavailable`"),
      token(files.phase14Readme, ":app:verifyNodePhase14WorkerResourceRssStress"),
      token(files.testing, ":app:verifyNodePhase14WorkerResourceRssStress"),
      token(files.ciGates, ":app:verifyNodePhase14WorkerResourceRssStress"),
      token(files.gradle14, "verifyNodePhase14WorkerResourceRssStress"),
      token(files.gradle14, "verify-node-phase14-worker-resource-rss-stress.js"),
    ]),
  ];
  const checks = [
    check("source-reports-ready", files.p14NativeAvailabilityReport.exists && statusOf(p14Native) === "ready" &&
      files.p14RunnablePackagedReport.exists && statusOf(p14Runnable) === "ready",
    "P14-28 and P14-29 generated reports must exist with ready status."),
    check("conditional-skip", (plan.nativePrecondition || {}).nativeAvailable === false &&
      (plan.stressState || {}).resourceStressExecuted === false,
    "P14-30 must remain conditional skipped while native workers are unavailable."),
    check("authority-not-promoted", (plan.stressState || {}).runtimeAuthorityChanged === false &&
      (plan.stressState || {}).defaultWorkerThreadsPromoted === false &&
      (plan.stressState || {}).projectOrPackagedOptInPromoted === false,
    "P14-30 must not promote default, project, or packaged worker authority."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-30 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-worker-resource-rss-stress-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-30",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    blockers: [],
    futurePromotionItems: FUTURE_PROMOTION_ITEMS,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      p14TaskComplete: policyViolations.length === 0,
      workerDecision: "conditional_skip_native_unavailable",
      nativeAvailable: false,
      sourceAnchorsPresent: scenarios.slice(1, 4).every((entry) => entry.status === "ready"),
      resourceStressExecuted: false,
      rssThreadSourceAnchorsPresent: true,
      javaHeapNativeHeapFdWorkerProofReady: false,
      runtimeAuthorityChanged: false,
      defaultWorkerThreadsPromoted: false,
      projectOrPackagedOptInPromoted: false,
      workerAuthorityDisabledUntilNativePackagedResourceGatesPass: true,
      futurePromotionItemCount: FUTURE_PROMOTION_ITEMS.length,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Worker Resource RSS Stress",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Worker decision: \`${report.decision.workerDecision}\``,
    `- Native available: \`${report.decision.nativeAvailable}\``,
    `- Source anchors present: \`${report.decision.sourceAnchorsPresent}\``,
    `- Resource stress executed: \`${report.decision.resourceStressExecuted}\``,
    `- RSS/thread source anchors present: \`${report.decision.rssThreadSourceAnchorsPresent}\``,
    `- Java/native heap/fd worker proof ready: \`${report.decision.javaHeapNativeHeapFdWorkerProofReady}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  lines.push("", "## Future Promotion Items", "");
  report.futurePromotionItems.forEach((entry) => {
    lines.push(`- \`${entry.id}\`: ${entry.requiredEvidence}`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return { id, description, status: missing.length === 0 ? "ready" : "missing_evidence", checks, missing };
}

function value(id, ok) {
  return { id, ok: Boolean(ok), message: `missing value check: ${id}` };
}

function token(file, expected) {
  return {
    id: `${file.relative}:${expected}`,
    ok: file.exists && file.text.includes(expected),
    message: `missing evidence token: ${expected} in ${file.relative}`,
  };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    }
  }
  return options;
}

main();
