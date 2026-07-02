#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-worker-runnable-packaged-smoke";
const FUTURE_PROMOTION_ITEMS = Object.freeze([
  {
    id: "native_available_runnable_worker_archive",
    classification: "future_promotion",
    requiredEvidence: "Run the runnable worker smoke with worker.preflight.status=available on the target ABI/device set.",
  },
  {
    id: "native_available_packaged_worker_archive",
    classification: "future_promotion",
    requiredEvidence: "Run the packaged .mjs Worker smoke and archive device-exported packaged output.",
  },
  {
    id: "worker_resource_gate_dependency",
    classification: "future_promotion",
    requiredEvidence: "Keep worker authority disabled until P14-30 resource/RSS stress also passes on native-worker-capable targets.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_29_WORKER_RUNNABLE_PACKAGED_SMOKE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/worker-runnable-packaged-smoke-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_WORKER_RUNNABLE_PACKAGED_SMOKE.md",
  p14NativeAvailabilityReport: "phase14-worker-native-availability.json",
  p13WorkerReport: "docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  appBuildGradle: "app/build.gradle.kts",
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
  console.log("Phase 14 worker runnable/packaged smoke: " + report.decision.status);
  console.log("Phase 14 worker runnable/packaged blockers: " + report.blockers.length);
  console.log("Phase 14 worker runnable/packaged policy violations: " + report.policyViolations.length);
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
  const manifest = parseJson(files.surfaceManifest.text);
  const workerSurface = (manifest.surfaces || []).find((entry) => entry.id === "desktop.worker_runtime") || null;
  const scenarios = [
    scenario("native_precondition_import", "P14-28 native-unavailable decision is imported before runnable smoke can promote", [
      value("schema", plan.schema === "autojs6-node-phase14-worker-runnable-packaged-smoke-v1"),
      value("task", plan.task === "P14-29"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "conditional_skip_native_unavailable"),
      value("promotionState", plan.promotionState === "worker_authority_disabled"),
      value("planNativeUnavailable", (plan.nativePrecondition || {}).nativeAvailable === false),
      value("planCurrentStatus", (plan.nativePrecondition || {}).currentStatus === "worker.preflight.status=unavailable"),
      value("planCurrentError", (plan.nativePrecondition || {}).currentError === "ERR_AUTOJS6_WORKER_DISABLED"),
      value("p14NativeReportExists", files.p14NativeAvailabilityReport.exists),
      value("p14NativeReady", statusOf(p14Native) === "ready"),
      value("p14NativeRunnableProofFalse", (p14Native.decision || {}).nativeRunnableProofReady === false),
      value("p14NativeCanProceedFalse", (p14Native.decision || {}).canProceedToRunnableWorkerPromotion === false),
      token(files.p13WorkerReport, "worker.preflight.status=unavailable"),
      token(files.p13WorkerReport, "ERR_AUTOJS6_WORKER_DISABLED"),
      token(files.workerSupport, "assumeNativeWorkerThreadsAvailable"),
      token(files.workerSupport, "runnable worker tests remain promotion gates"),
    ]),
    scenario("runnable_worker_source_anchors", "Basic Worker lifecycle and entry source anchors are present but conditional", [
      token(files.workerExperimentalTest, "fun experimentalWorkerThreadsRunPureJsWorkersOnly()"),
      token(files.workerExperimentalTest, "assumeNativeWorkerThreadsAvailable(\"worker_threads experimental runnable smoke\")"),
      token(files.workerExperimentalTest, "worker.compute=12|worker-data|json-dep"),
      token(files.workerExperimentalTest, "worker.computeExit=0"),
      token(files.workerExperimentalTest, "worker.terminate="),
      token(files.workerExperimentalTest, "worker.restricted="),
      token(files.workerExperimentalTest, "NodeRuntimeErrorCodes.WORKER_DISABLED"),
      token(files.workerExperimentalTest, "fun experimentalWorkerThreadsSupportCjsAndEsmEntries()"),
      token(files.workerExperimentalTest, "worker.entry.cjs=cjs|17|json-dep"),
      token(files.workerExperimentalTest, "worker.entry.mjs=mjs|17|esm-ok"),
      token(files.workerExperimentalTest, "worker.entry.typeModule=type-module|13"),
      token(files.workerExperimentalTest, "worker.entry.escape="),
    ]),
    scenario("message_pool_exception_timeout_anchors", "Message, WorkerPool, exception, timeout, cleanup, and nested-worker anchors remain native-available targets", [
      token(files.workerExperimentalTest, "fun experimentalWorkerThreadsSupportMessageChannelAndTransferList()"),
      token(files.workerExperimentalTest, "worker.message.policy=65536|32|function|function"),
      token(files.workerExperimentalTest, "worker.message.detached=0"),
      token(files.workerExperimentalTest, "worker.message.cloneError=DataCloneError"),
      token(files.workerExperimentalTest, "worker.message.queueError=DataCloneError"),
      token(files.workerExperimentalTest, "fun experimentalWorkerThreadsSupportControlledWorkerPool()"),
      token(files.workerExperimentalTest, "worker.pool.policy=function|1|8|64"),
      token(files.workerExperimentalTest, "worker.pool.queue="),
      token(files.workerExperimentalTest, "worker.pool.timeout="),
      token(files.workerStressTest, "worker.resource.throw=true|"),
      token(files.workerStressTest, "worker.resource.timeout.recovery=v24.5.0"),
      token(files.workerStressTest, "worker.resource.cleanup.before="),
      token(files.workerDesignDoc, "Runnable coverage is skipped when the preflight reports native workers unavailable"),
    ]),
    scenario("packaged_worker_smoke_anchor", "Packaged .mjs Worker smoke source anchor remains conditional on native availability", [
      token(files.appBuildGradle, "installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "fun installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk()"),
      token(files.packagedSmokeTest, "assumeNativeWorkerThreadsAvailable(\"packaged worker_threads ESM smoke\")"),
      token(files.packagedSmokeTest, "packaged.worker_threads.esm.ready=true"),
      token(files.packagedSmokeTest, "packaged.worker_threads.esm.value=true"),
      token(files.packagedSmokeTest, "packaged.worker_threads.esm.thread=true"),
      token(files.packagedSmokeTest, "packaged.worker_threads.esm.exit=0"),
      token(files.workerDesignDoc, "EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk"),
      token(files.workerDesignDoc, "It skips before APK construction when native worker preflight is unavailable."),
    ]),
    scenario("authority_stays_disabled", "Source-only smoke anchors do not grant worker runtime authority", [
      value("planRunnableNotExecuted", (plan.smokeState || {}).runnableSmokeExecuted === false),
      value("planPackagedNotExecuted", (plan.smokeState || {}).packagedSmokeExecuted === false),
      value("planAuthorityUnchanged", (plan.smokeState || {}).runtimeAuthorityChanged === false),
      value("planDefaultNotPromoted", (plan.smokeState || {}).defaultWorkerThreadsPromoted === false),
      value("planProjectPackageNotPromoted", (plan.smokeState || {}).projectOrPackagedOptInPromoted === false),
      token(files.runtimeCapabilities, "const val WORKER_THREADS_EXPERIMENTAL_DEFAULT = false"),
      token(files.runtimeCapabilities, "projectJsonOverrideAllowed = false"),
      token(files.runtimeCapabilities, "packagedApkOverrideAllowed = false"),
      token(files.compatibilityProfile, "P14-29 records the worker runnable/packaged smoke gate as conditional"),
      token(files.securityModel, "P14-29 records the worker runnable/packaged smoke gate as conditional"),
      token(files.errorCatalog, "P14-29 worker runnable/packaged smoke decision"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-29 gate", [
      value("surfaceEvidence", workerSurface &&
        workerSurface.status === "design_locked" &&
        (workerSurface.tasks || []).includes("P14-29") &&
        (workerSurface.evidence || []).includes("docs/nodejs/phase14/worker-runnable-packaged-smoke-plan.json") &&
        (workerSurface.evidence || []).includes(":app:verifyNodePhase14WorkerRunnablePackagedSmoke")),
      token(files.surfaceManifestDoc, "P14-29 adds the conditional worker runnable/packaged smoke gate"),
      token(files.phase14Tasks, "- [x] P14-29: Add worker runnable and packaged smoke if native support exists."),
      token(files.phase14Tasks, "Worker runnable/packaged smoke gate deployed:"),
      token(files.phase14Report, "P14-29 decision: `conditional_skip_native_unavailable`"),
      token(files.phase14Readme, ":app:verifyNodePhase14WorkerRunnablePackagedSmoke"),
      token(files.testing, ":app:verifyNodePhase14WorkerRunnablePackagedSmoke"),
      token(files.ciGates, ":app:verifyNodePhase14WorkerRunnablePackagedSmoke"),
      token(files.gradle14, "verifyNodePhase14WorkerRunnablePackagedSmoke"),
      token(files.gradle14, "verify-node-phase14-worker-runnable-packaged-smoke.js"),
    ]),
  ];
  const checks = [
    check("p14-native-availability-report-ready", files.p14NativeAvailabilityReport.exists && statusOf(p14Native) === "ready", "P14-28 generated report must exist with ready status."),
    check("conditional-skip", (plan.nativePrecondition || {}).nativeAvailable === false &&
      (plan.smokeState || {}).runnableSmokeExecuted === false &&
      (plan.smokeState || {}).packagedSmokeExecuted === false,
    "P14-29 must remain conditional skipped while native workers are unavailable."),
    check("authority-not-promoted", (plan.smokeState || {}).runtimeAuthorityChanged === false &&
      (plan.smokeState || {}).defaultWorkerThreadsPromoted === false &&
      (plan.smokeState || {}).projectOrPackagedOptInPromoted === false,
    "P14-29 must not promote default, project, or packaged worker authority."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-29 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-worker-runnable-packaged-smoke-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-29",
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
      p14_28NativeAvailabilityImported: files.p14NativeAvailabilityReport.exists && statusOf(p14Native) === "ready",
      workerDecision: "conditional_skip_native_unavailable",
      nativeAvailable: false,
      sourceAnchorsPresent: scenarios.slice(1, 4).every((entry) => entry.status === "ready"),
      runnableSmokeExecuted: false,
      packagedSmokeExecuted: false,
      runtimeAuthorityChanged: false,
      defaultWorkerThreadsPromoted: false,
      projectOrPackagedOptInPromoted: false,
      processWorkerFacadePromoted: false,
      workerAuthorityDisabledUntilPackagedAndResourceGatesPass: true,
      futurePromotionItemCount: FUTURE_PROMOTION_ITEMS.length,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Worker Runnable Packaged Smoke",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Worker decision: \`${report.decision.workerDecision}\``,
    `- Native available: \`${report.decision.nativeAvailable}\``,
    `- Source anchors present: \`${report.decision.sourceAnchorsPresent}\``,
    `- Runnable smoke executed: \`${report.decision.runnableSmokeExecuted}\``,
    `- Packaged smoke executed: \`${report.decision.packagedSmokeExecuted}\``,
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
