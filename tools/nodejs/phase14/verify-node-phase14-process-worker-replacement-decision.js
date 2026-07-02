#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-process-worker-replacement-decision";
const FUTURE_PROMOTION_ITEMS = Object.freeze([
  {
    id: "native_workerpool_replacement_api_evidence",
    classification: "future_promotion",
    requiredEvidence: "Archive native WorkerPool availability and execution evidence on selected v1.2 target ABIs.",
  },
  {
    id: "execution_slot_accounting_cleanup_evidence",
    classification: "future_promotion",
    requiredEvidence: "Tie any callable replacement API to execution slot accounting, cancellation, cleanup, and service lifecycle ownership.",
  },
  {
    id: "packaged_process_worker_ux_security_evidence",
    classification: "future_promotion",
    requiredEvidence: "Define packaged metadata, disclosure, downgrade, launch UX, security corpus, and rollback behavior before promotion.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_31_PROCESS_WORKER_REPLACEMENT_DECISION.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  decisionJson: "docs/nodejs/phase14/process-worker-replacement-decision.json",
  decisionDoc: "docs/nodejs/phase14/PHASE14_PROCESS_WORKER_REPLACEMENT_DECISION.md",
  p13DecisionDoc: "docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md",
  p13DecisionReport: "phase13-process-worker-replacement-decision.json",
  p14NativeAvailabilityReport: "phase14-worker-native-availability.json",
  p14RunnablePackagedReport: "phase14-worker-runnable-packaged-smoke.json",
  p14ResourceStressReport: "phase14-worker-resource-rss-stress.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  p13ProcessWorkerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest.kt",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  shellTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
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
  console.log("Phase 14 process-worker replacement decision: " + report.decision.status);
  console.log("Phase 14 process-worker blockers: " + report.blockers.length);
  console.log("Phase 14 process-worker policy violations: " + report.policyViolations.length);
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
  const plan = parseJson(files.decisionJson.text);
  const p13Decision = parseJson(files.p13DecisionReport.text);
  const p14Native = parseJson(files.p14NativeAvailabilityReport.text);
  const p14Runnable = parseJson(files.p14RunnablePackagedReport.text);
  const p14Resource = parseJson(files.p14ResourceStressReport.text);
  const p14NativeDecision = p14Native.decision || p14Native.v1_2Decision || {};
  const manifest = parseJson(files.surfaceManifest.text);
  const workerSurface = (manifest.surfaces || []).find((entry) => entry.id === "desktop.worker_runtime") || null;
  const noRuntimeFacade = !includesAny(files.nodeBridgeSources.text, [
    "function __autojs6_process_worker",
    'name === "process_worker"',
    'name === "autojs6:process-worker"',
    "process_worker:",
    "compute_worker:",
  ]);
  const noTypeFacade = !includesAny(files.typesAutojsGlobal.text, [
    'declare module "process_worker"',
    'declare module "autojs6:process-worker"',
    "process_worker(",
    "compute_worker(",
  ]);
  const scenarios = [
    scenario("decision_profile_no_facade", "P13-35 diagnostics remain available while callable process-worker facades stay absent", [
      value("schema", plan.schema === "autojs6-node-phase14-process-worker-replacement-decision-v1"),
      value("task", plan.task === "P14-31"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "no_user_facing_facade_v1_2"),
      value("promotionState", plan.promotionState === "process_worker_facade_closed"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-35", "P14-28", "P14-29", "P14-30"])),
      value("p13DecisionReady", files.p13DecisionReport.exists && statusOf(p13Decision) === "ready"),
      token(files.nodeBridgeSources, "processWorkerReplacementProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "decision_partial"'),
      token(files.nodeBridgeSources, 'api: "not_introduced"'),
      token(files.nodeBridgeSources, 'facade: "not_introduced"'),
      token(files.p13ProcessWorkerTest, "processWorkerReplacementKeepsSeparateFacadeUnintroducedAndProcessDenied"),
      token(files.p13ProcessWorkerTest, "p13processworker.profile=decision_partial|not_introduced|not_introduced"),
      token(files.p13ProcessWorkerTest, "p13processworker.boundary=undefined|undefined|undefined|false|false|false|true|true|partial"),
      token(files.typesAutojsGlobal, "export interface ProcessWorkerReplacementProfile"),
      token(files.typescriptSmoke, "autojsProfile.processWorkerReplacementProfile.preferredPrimitive"),
      value("noRuntimeFacade", noRuntimeFacade),
      value("noTypeFacade", noTypeFacade),
    ]),
    scenario("worker_preconditions_block_replacement_api", "P14 worker gates do not provide runnable/resource evidence for a replacement API", [
      value("p14NativeReady", files.p14NativeAvailabilityReport.exists && statusOf(p14Native) === "ready"),
      value("p14RunnableReady", files.p14RunnablePackagedReport.exists && statusOf(p14Runnable) === "ready"),
      value("p14ResourceReady", files.p14ResourceStressReport.exists && statusOf(p14Resource) === "ready"),
      value("planNativeUnavailable", (plan.workerPreconditions || {}).nativeAvailable === false),
      value("planRunnableFalse", (plan.workerPreconditions || {}).runnableSmokeExecuted === false),
      value("planPackagedFalse", (plan.workerPreconditions || {}).packagedSmokeExecuted === false),
      value("planResourceFalse", (plan.workerPreconditions || {}).resourceStressExecuted === false),
      value("p14NativeUnavailable", (
        p14NativeDecision.runnableWorkerProofReady === false ||
        p14NativeDecision.nativeRunnableProofReady === false
      )),
      value("p14ProcessWorkerNotPromoted", p14NativeDecision.processWorkerFacadePromoted === false),
      value("p14RunnableSkipped", (p14Runnable.decision || {}).workerDecision === "conditional_skip_native_unavailable" &&
        (p14Runnable.decision || {}).runnableSmokeExecuted === false),
      value("p14ResourceSkipped", (p14Resource.decision || {}).workerDecision === "conditional_skip_native_unavailable" &&
        (p14Resource.decision || {}).resourceStressExecuted === false),
      token(files.decisionDoc, "Current worker precondition | `worker.preflight.status=unavailable`"),
      token(files.phase14Report, "Future replacement API requires native, slot-accounting, cleanup/resource"),
    ]),
    scenario("raw_process_and_child_process_boundary", "Raw process, child_process, shell, and worker_computation boundaries remain closed", [
      value("processWorkerFacadeFalse", (plan.v1_2Decision || {}).processWorkerFacadePromoted === false),
      value("computeWorkerFacadeFalse", (plan.v1_2Decision || {}).computeWorkerFacadePromoted === false),
      value("childProcessFalse", (plan.v1_2Decision || {}).childProcessPromoted === false),
      value("rawProcessFalse", (plan.v1_2Decision || {}).rawProcessHandlesPromoted === false),
      value("spawnForkStdioSignalsFalse", (plan.v1_2Decision || {}).spawnForkStdioSignalsPromoted === false),
      value("workerComputationFalse", (plan.v1_2Decision || {}).workerComputationLaunchSurfacePromoted === false),
      token(files.nodeBridgeSources, 'childProcess: "denied"'),
      token(files.nodeBridgeSources, 'shellBridge: "separate_scoped_command_bridge"'),
      token(files.nodeBridgeSources, 'executionMode: "worker_computation_reserved"'),
      token(files.nodeBridgeSources, 'secondExecutionSlot: "closed_by_p13_06"'),
      token(files.nodeBridgeSources, "rawProcessHandles: false"),
      token(files.runtimeCapabilities, 'id = "child_process"'),
      token(files.runtimeCapabilities, 'status = "disabled"'),
      token(files.executionModePolicy, "MODE_WORKER_COMPUTATION"),
      token(files.executionModePolicy, 'status = "reserved"'),
      token(files.executionModePolicy, "supportedLaunchSurfaces = emptySet()"),
      token(files.p13ProcessWorkerTest, "p13processworker.childProcessBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.shellTest, "jsShellModuleIsChildProcessAlternativeAndRequiresDeclaredCapability"),
      token(files.shellTest, "shell.policy.child_process=ERR_AUTOJS6_BUILTIN_DISABLED"),
      token(files.p13DecisionDoc, "P13-40 is a separate child_process scoped-runner track"),
      token(files.decisionDoc, "does not expose raw process handles, `spawn`, `fork`, stdio"),
    ]),
    scenario("workerpool_future_api_requirements", "Any future callable API is explicitly gated by WorkerPool, slot, cleanup, packaged, UX, and security evidence", [
      value("preferredPrimitive", (plan.replacementDirection || {}).preferredPrimitive === "worker_threads.WorkerPool"),
      value("apiNotIntroduced", (plan.replacementDirection || {}).api === "not_introduced"),
      value("facadeNotIntroduced", (plan.replacementDirection || {}).facade === "not_introduced"),
      value("workerComputationReserved", (plan.replacementDirection || {}).executionMode === "worker_computation_reserved"),
      value("packagedNotPromoted", (plan.v1_2Decision || {}).packagedProcessWorkerPromoted === false),
      value("futureRequirements", containsAll(plan.futureApiRequirements || [], [
        "native WorkerPool evidence on selected target ABIs",
        "execution slot accounting tied to the scheduler and service lifecycle",
        "WorkerPool cleanup, cancellation, timeout, and resource/RSS archives",
        "packaged behavior and project metadata disclosure",
        "user-visible launch UX and rollback policy",
        "explicit separation from child_process scoped-runner work",
      ])),
      token(files.workerSupport, "worker.preflight.status=available"),
      token(files.workerSupport, "worker.preflight.status=unavailable"),
      token(files.workerExperimentalTest, "worker.pool.policy=function|1|8|64"),
      token(files.workerStressTest, "worker.resource.pool.shutdown=0|0|104|1|1|0"),
      token(files.workerDesignDoc, "WorkerPool"),
      token(files.decisionDoc, "Future WorkerPool-Compatible API Requirements"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-31 gate", [
      value("surfaceEvidence", workerSurface &&
        workerSurface.status === "design_locked" &&
        (workerSurface.tasks || []).includes("P14-31") &&
        (workerSurface.evidence || []).includes("docs/nodejs/phase14/process-worker-replacement-decision.json") &&
        (workerSurface.evidence || []).includes(":app:verifyNodePhase14ProcessWorkerReplacementDecision")),
      token(files.surfaceManifestDoc, "P14-31 closes the process-worker replacement decision"),
      token(files.phase14Tasks, "- [x] P14-31: Decide process-worker replacement for v1.2."),
      token(files.phase14Tasks, "Process-worker replacement decision gate deployed:"),
      token(files.phase14Report, "P14-31 decision: `no_user_facing_facade_v1_2`"),
      token(files.phase14Readme, ":app:verifyNodePhase14ProcessWorkerReplacementDecision"),
      token(files.compatibilityProfile, "P14-31 closes the v1.2 process-worker replacement decision"),
      token(files.securityModel, "P14-31 closes the v1.2 process-worker replacement decision"),
      token(files.errorCatalog, "P14-31 process-worker replacement decision"),
      token(files.testing, ":app:verifyNodePhase14ProcessWorkerReplacementDecision"),
      token(files.ciGates, ":app:verifyNodePhase14ProcessWorkerReplacementDecision"),
      token(files.gradle14, "verifyNodePhase14ProcessWorkerReplacementDecision"),
      token(files.gradle14, "verify-node-phase14-process-worker-replacement-decision.js"),
    ]),
  ];
  const checks = [
    check("source-reports-ready", files.p13DecisionReport.exists && statusOf(p13Decision) === "ready" &&
      files.p14NativeAvailabilityReport.exists && statusOf(p14Native) === "ready" &&
      files.p14RunnablePackagedReport.exists && statusOf(p14Runnable) === "ready" &&
      files.p14ResourceStressReport.exists && statusOf(p14Resource) === "ready",
    "P13-35, P14-28, P14-29, and P14-30 generated reports must exist with ready status."),
    check("no-user-facing-facade", noRuntimeFacade && noTypeFacade &&
      (plan.v1_2Decision || {}).processWorkerFacadePromoted === false &&
      (plan.v1_2Decision || {}).computeWorkerFacadePromoted === false,
    "P14-31 must not introduce a process_worker or compute_worker facade."),
    check("raw-process-authority-denied", (plan.v1_2Decision || {}).rawProcessHandlesPromoted === false &&
      (plan.v1_2Decision || {}).spawnForkStdioSignalsPromoted === false &&
      (plan.v1_2Decision || {}).childProcessPromoted === false &&
      files.nodeBridgeSources.text.includes('childProcess: "denied"') &&
      files.nodeBridgeSources.text.includes("rawProcessHandles: false"),
    "P14-31 must keep raw process handles, spawn/fork, stdio, signals, and child_process unpromoted."),
    check("worker-preconditions-unmet", (plan.workerPreconditions || {}).nativeAvailable === false &&
      (plan.workerPreconditions || {}).runnableSmokeExecuted === false &&
      (plan.workerPreconditions || {}).packagedSmokeExecuted === false &&
      (plan.workerPreconditions || {}).resourceStressExecuted === false,
    "P14-31 must remain a no-facade decision while native, runnable, packaged, and resource worker evidence is unavailable."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-31 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-process-worker-replacement-decision-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-31",
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
      processWorkerDecision: "no_user_facing_facade_v1_2",
      preferredPrimitive: "worker_threads.WorkerPool",
      sourceReportsReady: checkById(checks, "source-reports-ready").ok,
      diagnosticProfileReady: scenarioById(scenarios, "decision_profile_no_facade").status === "ready",
      callableFacadeIntroduced: false,
      workerPoolReplacementApiPromoted: false,
      childProcessPromoted: false,
      rawProcessHandlesPromoted: false,
      spawnForkStdioSignalsPromoted: false,
      workerComputationLaunchSurfacePromoted: false,
      packagedProcessWorkerPromoted: false,
      runtimeAuthorityChanged: false,
      futurePromotionItemCount: FUTURE_PROMOTION_ITEMS.length,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Process Worker Replacement Decision",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Process-worker decision: \`${report.decision.processWorkerDecision}\``,
    `- Preferred primitive: \`${report.decision.preferredPrimitive}\``,
    `- Callable facade introduced: \`${report.decision.callableFacadeIntroduced}\``,
    `- WorkerPool replacement API promoted: \`${report.decision.workerPoolReplacementApiPromoted}\``,
    `- Child-process authority promoted: \`${report.decision.childProcessPromoted}\``,
    `- Raw process handles promoted: \`${report.decision.rawProcessHandlesPromoted}\``,
    `- Spawn/fork/stdio/signals promoted: \`${report.decision.spawnForkStdioSignalsPromoted}\``,
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

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { ok: false };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
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

function containsAll(actual, expected) {
  return Array.isArray(actual) && expected.every((value) => actual.includes(value));
}

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
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
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase14-process-worker-replacement-decision.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
