#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-worker-threads-opt-in-promotion";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  buildGradle: "app/build.gradle.kts",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13WorkerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsPhase13DecisionInstrumentationTest.kt",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerBootstrapTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt",
  workerDesignTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  workerDesignDoc: "docs/nodejs/design/WORKER_THREADS.md",
  workerEvaluation: "docs/nodejs/reports/WORKER_EXPERIMENTAL_EVALUATION.md",
  workerPhase8Recheck: "docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  builtinMatrixGenerator: "tools/nodejs/phase13/generate-node-builtin-parity-matrix.js",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 worker_threads opt-in promotion: " + report.decision.status);
  console.log("Phase 13 worker_threads blockers: " + report.blockers.length);
  console.log("Phase 13 worker_threads policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const executionSurface = surface(files.surfaceManifest.text, "desktop.execution_runtime");
  const scenarios = [
    scenario("default_denial_and_profile", "worker_threads stays disabled by default and reports explicit profile diagnostics", [
      token(files.nodeBridgeSources, "workerThreadsProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: __autojs6_worker_threads_experimental_enabled ? "experimental_opt_in_preflight" : "disabled_by_default"'),
      token(files.nodeBridgeSources, "requestLocalOptIn: __autojs6_worker_threads_experimental_enabled"),
      token(files.nodeBridgeSources, 'bridgeModules: "denied"'),
      token(files.nodeBridgeSources, "nestedWorkers: false"),
      token(files.nodeBridgeSources, "rawNativeHandles: false"),
      token(files.p13WorkerTest, "workerThreadsProfileKeepsDefaultDeniedAndExperimentalPreflightExplicit"),
      token(files.p13WorkerTest, "p13worker.default=false|false|false|false|true|disabled_by_default"),
      token(files.workerBootstrapTest, "worker.bootstrap.profile=false|false|false|false|true"),
      token(files.workerDesignTest, "worker.profile=false/false/false/true"),
      token(files.runtimeCapabilities, "WORKER_THREADS_EXPERIMENTAL_PROPERTY"),
      token(files.typesAutojsGlobal, "export interface WorkerThreadsProfile"),
      token(files.typescriptSmoke, "autojsProfile.workerThreadsProfile.policy.maxWorkers"),
    ], []),
    scenario("experimental_preflight_and_isolation", "request-local experimental worker support is native-preflight gated and isolated", [
      token(files.workerSupport, "worker.preflight.status=available"),
      token(files.workerSupport, "worker.preflight.status=unavailable"),
      token(files.workerSupport, "runnable worker tests remain promotion gates"),
      token(files.workerExperimentalTest, "worker.profile=true|true|true|true|false"),
      token(files.workerExperimentalTest, "worker.entry.cjs=cjs|17|json-dep"),
      token(files.workerExperimentalTest, "worker.profile.fs=workingDirectory|workingDirectory|denied|denied|denied|denied|denied"),
      token(files.workerExperimentalTest, "worker.pool.policy=function|1|8|64"),
      token(files.workerExperimentalTest, "worker.message.policy=65536|32|function|function"),
      token(files.workerStressTest, "worker.resource.sequence=0|1|100|100|0|0"),
      token(files.workerStressTest, "worker.resource.pool.shutdown=0|0|104|1|1|0"),
      token(files.packagedSmokeTest, "packaged.worker_threads.esm.ready=true"),
      token(files.buildGradle, "installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk"),
      token(files.workerDesignDoc, "Runnable coverage is skipped when the preflight reports native workers unavailable"),
      token(files.p13Report, "QV710AF65F / XQ-AT72 / Android 12"),
      token(files.p13Report, "tests=16, failures=0,"),
      token(files.p13Report, "worker.preflight.status=unavailable"),
      token(files.testingDoc, "Current QV710AF65F P13-34 gate evidence"),
    ], [
      "Native-runnable opt-in promotion requires `worker.preflight.status=available` on target ABIs/devices.",
      "Promote packaged worker smoke from source markers/native-unavailable skip to native-available device-exported evidence.",
    ]),
    scenario("resource_crash_packaged_blockers", "resource stress, timeout cleanup, and packaged behavior remain required before opt-in promotion", [
      token(files.workerStressTest, "worker.resource.timeout.recovery=v24.5.0"),
      token(files.workerStressTest, "worker.resource.cleanup.error="),
      token(files.workerStressTest, "worker.resource.throw=true|"),
      token(files.workerStressTest, "assertResourceGrowthBounded"),
      token(files.workerDesignDoc, "focused instrumentation for 50 sequential worker create/terminate cycles, worker exceptions, infinite-loop worker timeout recovery, and parent-completion cleanup"),
      token(files.p13Report, "native worker availability"),
      token(files.p13Report, "packaged `.mjs` worker smoke"),
    ], [
      "Add current crash evidence bundle references for worker startup failure, worker exception, timeout, parent cleanup, and packaged worker failure modes.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-34", [
      token(files.phase13Tasks, "P13-34 partial landing"),
      token(files.p13Report, "P13-34 records `worker_threads` opt-in promotion as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13WorkerThreadsOptInPromotion"),
      token(files.testingDoc, "verifyNodePhase13WorkerThreadsOptInPromotion"),
      token(files.ciGatesDoc, "verifyNodePhase13WorkerThreadsOptInPromotion"),
      token(files.compatibilityProfile, "P13-34 records `worker_threads` opt-in promotion as partial"),
      token(files.securityModel, "P13-34 records `worker_threads` opt-in promotion as partial"),
      token(files.proComparison, "P13-34 records `worker_threads` opt-in promotion as partial"),
      token(files.proAutoxNotes, "P13-34 records `worker_threads` opt-in promotion as partial"),
      token(files.typesReadme, "P13-34 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-34 records worker_threads opt-in diagnostics as partial"),
      token(files.builtinMatrixGenerator, "P13-34 records worker_threads opt-in diagnostics as partial"),
      token(files.capabilityMatrix, "P13-34 worker_threads opt-in promotion"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-34"), "P13-34 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-34 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-34 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-34 evidence.",
    ),
    check(
      "matrix-stays-disabled",
      files.desktopMatrix.text.includes("| `worker_threads` | `disabled` |") &&
        files.desktopMatrix.text.includes("P13-34 records worker_threads opt-in diagnostics as partial"),
      "Desktop parity matrix must keep worker_threads disabled in Safe/default and point at P13-34.",
    ),
    check(
      "feature-flag-not-project-packaged",
      files.runtimeCapabilities.text.includes("id = \"worker_threads\"") &&
        files.runtimeCapabilities.text.includes("projectJsonOverrideAllowed = false") &&
        files.runtimeCapabilities.text.includes("packagedApkOverrideAllowed = false"),
      "worker_threads must stay build/request experimental, not project/package promoted.",
    ),
    check(
      "no-default-worker-enable",
      files.nodeBridgeSources.text.includes("defaultEnabled: false") &&
        !files.nodeBridgeSources.text.includes("defaultEnabled: true,\n        requestLocalOptIn: __autojs6_worker_threads_experimental_enabled"),
      "P13-34 must not enable worker_threads by default.",
    ),
    check(
      "worker-policy-boundaries-documented",
      files.p13Report.text.includes("## Worker Policy Boundary") &&
        files.p13Report.text.includes("Nested workers remain denied for P13-34 v1.1") &&
        files.p13Report.text.includes("SharedArrayBuffer and Atomics are not promoted as cross-worker compatibility") &&
        files.p13Report.text.includes("Resource limits stay explicit and conservative") &&
        files.p13Report.text.includes("Android thread and RSS behavior remains evidence-gated") &&
        files.nodeBridgeSources.text.includes("nestedWorkers: false") &&
        files.nodeBridgeSources.text.includes("maxWorkers: 1") &&
        files.nodeBridgeSources.text.includes("maxOldGenerationSizeMb: 64") &&
        files.nodeBridgeSources.text.includes("stackSizeMb: 8"),
      "P13-34 must document nested worker, SharedArrayBuffer/Atomics, resource limit, thread, and RSS policy boundaries.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-worker-threads-opt-in-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13WorkerThreadsOptInPromotion",
    task: "P13-34",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: executionSurface || {
      id: "desktop.execution_runtime",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      defaultDeniedReady: scenarioById(scenarios, "default_denial_and_profile").status === "ready",
      profileDiagnosticExposed: files.nodeBridgeSources.text.includes("workerThreadsProfile: Object.freeze({"),
      requestLocalExperimentalPreflightReady: scenarioById(scenarios, "experimental_preflight_and_isolation").status !== "missing_evidence",
      workerPolicyBoundariesDocumented: checkById(checks, "worker-policy-boundaries-documented").passed,
      currentUnavailableGateEvidenceArchived: files.p13Report.text.includes("QV710AF65F / XQ-AT72 / Android 12") &&
        files.p13Report.text.includes("worker.preflight.status=unavailable"),
      nativeRunnableEvidenceRequired: true,
      packagedWorkerEvidenceReady: false,
      crashEvidenceReady: false,
      defaultWorkerThreadsPromoted: false,
      projectOrPackagedOptInPromoted: false,
      desktopSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Phase 13 Worker Threads Opt-In Promotion Verification");
  lines.push("");
  lines.push(`- Task: ${report.task}`);
  lines.push(`- Gate: \`${report.gate}\``);
  lines.push(`- Decision: \`${report.decision.status}\``);
  lines.push(`- Surface: \`${report.surface.id}\` / \`${report.surface.status}\``);
  lines.push(`- Policy violations: ${report.policyViolations.length}`);
  lines.push(`- Remaining blockers: ${report.blockers.length}`);
  lines.push("");
  lines.push("## Scenarios");
  lines.push("");
  for (const scenario of report.scenarios) {
    lines.push(`### ${scenario.id}`);
    lines.push("");
    lines.push(`- Status: \`${scenario.status}\``);
    lines.push(`- ${scenario.title}`);
    if (scenario.remaining.length > 0) {
      lines.push("- Remaining:");
      for (const item of scenario.remaining) {
        lines.push(`  - ${item}`);
      }
    }
    lines.push("");
  }
  lines.push("## Checks");
  lines.push("");
  lines.push("| Check | Result | Message |");
  lines.push("| --- | --- | --- |");
  for (const check of report.checks) {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${check.message} |`);
  }
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push("```json");
  lines.push(JSON.stringify(report.decision, null, 2));
  lines.push("```");
  lines.push("");
  return lines.join("\n");
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === "--repo-root") {
      options.repoRoot = args[++i];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++i];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

main();
