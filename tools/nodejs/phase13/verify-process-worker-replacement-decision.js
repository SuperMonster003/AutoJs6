#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-process-worker-replacement-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13ProcessWorkerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest.kt",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  shellTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt",
  phase12DecisionReport: "docs/nodejs/reports/phase12/RHINO_COMPUTE_PROCESS_WORKER_DECISION.md",
  workerDesignDoc: "docs/nodejs/design/WORKER_THREADS.md",
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
  console.log("Phase 13 process-worker replacement decision: " + report.decision.status);
  console.log("Phase 13 process-worker blockers: " + report.blockers.length);
  console.log("Phase 13 process-worker policy violations: " + report.policyViolations.length);
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
    scenario("diagnostic_profile_no_facade", "process-worker replacement has diagnostics but no callable facade", [
      token(files.nodeBridgeSources, "processWorkerReplacementProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "decision_partial"'),
      token(files.nodeBridgeSources, 'api: "not_introduced"'),
      token(files.nodeBridgeSources, 'facade: "not_introduced"'),
      token(files.p13ProcessWorkerTest, "processWorkerReplacementKeepsSeparateFacadeUnintroducedAndProcessDenied"),
      token(files.p13ProcessWorkerTest, "p13processworker.profile=decision_partial|not_introduced|not_introduced"),
      token(files.p13ProcessWorkerTest, "p13processworker.boundary=undefined|undefined|undefined|false|false|false|true|true|partial"),
      token(files.typesAutojsGlobal, "export interface ProcessWorkerReplacementProfile"),
      token(files.typesAutojsGlobal, "readonly processWorkerReplacementProfile: ProcessWorkerReplacementProfile"),
      token(files.typescriptSmoke, "autojsProfile.processWorkerReplacementProfile.preferredPrimitive"),
    ], []),
    scenario("worker_pool_replacement_path", "WorkerPool remains the preferred replacement primitive while native availability gates runnable claims", [
      token(files.nodeBridgeSources, 'preferredPrimitive: "worker_threads.WorkerPool"'),
      token(files.nodeBridgeSources, 'workerThreads: "request_local_experimental_preflight"'),
      token(files.nodeBridgeSources, 'executionMode: "worker_computation_reserved"'),
      token(files.nodeBridgeSources, 'secondExecutionSlot: "not_closed"'),
      token(files.executionModePolicy, "MODE_WORKER_COMPUTATION"),
      token(files.executionModePolicy, 'status = "reserved"'),
      token(files.executionModePolicy, "supportedLaunchSurfaces = emptySet()"),
      token(files.workerSupport, "worker.preflight.status=available"),
      token(files.workerSupport, "worker.preflight.status=unavailable"),
      token(files.workerExperimentalTest, "worker.pool.policy=function|1|8|64"),
      token(files.workerStressTest, "worker.resource.pool.shutdown=0|0|104|1|1|0"),
      token(files.phase12DecisionReport, "Defer the compute/process-worker replacement API"),
      token(files.workerDesignDoc, "P12-22 records the current compute/process-worker decision"),
    ], [
      "Runnable process-worker replacement remains blocked on native worker availability and archived WorkerPool evidence on target ABIs.",
      "No user-facing process-worker launch surface exists until worker_computation and second execution slot promotion evidence are current.",
    ]),
    scenario("child_process_shell_boundary", "child_process stays default-denied and shell remains a separate scoped command bridge", [
      token(files.nodeBridgeSources, 'childProcess: "denied"'),
      token(files.nodeBridgeSources, 'shellBridge: "separate_scoped_command_bridge"'),
      token(files.runtimeCapabilities, 'id = "child_process"'),
      token(files.runtimeCapabilities, 'status = "disabled"'),
      token(files.p13ProcessWorkerTest, "p13processworker.childProcessBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.shellTest, "jsShellModuleIsChildProcessAlternativeAndRequiresDeclaredCapability"),
      token(files.shellTest, "shell.policy.child_process=ERR_AUTOJS6_BUILTIN_DISABLED"),
      token(files.securityModel, "The `shell` bridge is the controlled AutoJs6 replacement for Node `child_process`"),
      token(files.desktopMatrix, "| `child_process` | `experimental` | `desktop_compat_opt_in` |"),
      token(files.desktopMatrix, "Safe/default keeps child_process denied"),
    ], [
      "P13-40 must separately design any scoped child_process runner; P13-35 does not promote raw spawn, fork, stdio streams, signals, or process handles.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-35", [
      token(files.phase13Tasks, "P13-35 partial process-worker replacement gate"),
      token(files.p13Report, "P13-35 records process-worker replacement as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13ProcessWorkerReplacementDecision"),
      token(files.testingDoc, "verifyNodePhase13ProcessWorkerReplacementDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13ProcessWorkerReplacementDecision"),
      token(files.compatibilityProfile, "P13-35 records process-worker replacement as partial"),
      token(files.securityModel, "P13-35 records process-worker replacement as partial"),
      token(files.proComparison, "P13-35 records process-worker replacement as partial"),
      token(files.proAutoxNotes, "P13-35 records process-worker replacement as partial"),
      token(files.typesReadme, "P13-35 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-35 process-worker replacement"),
    ], [
      "P13-35 remains partial until WorkerPool native evidence, process-worker packaging, slot accounting, cleanup, and UX are ready.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-35"), "P13-35 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-35 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-35 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-35 evidence.",
    ),
    check(
      "no-process-worker-runtime-facade",
      !includesAny(files.nodeBridgeSources.text, [
        "function __autojs6_process_worker",
        'name === "process_worker"',
        'name === "autojs6:process-worker"',
        "process_worker:",
        "compute_worker:",
      ]) &&
        !includesAny(files.typesAutojsGlobal.text, [
          'declare module "process_worker"',
          'declare module "autojs6:process-worker"',
          "process_worker(",
          "compute_worker(",
        ]),
      "P13-35 must not add a callable process_worker or compute_worker facade.",
    ),
    check(
      "child-process-stays-default-denied",
      files.desktopMatrix.text.includes("| `child_process` | `experimental` | `desktop_compat_opt_in` |") &&
        files.desktopMatrix.text.includes("Safe/default keeps child_process denied") &&
        files.runtimeCapabilities.text.includes('id = "child_process"') &&
        files.runtimeCapabilities.text.includes('status = "disabled"'),
      "child_process must remain default-denied while process-worker replacement is only a decision profile.",
    ),
    check(
      "worker-computation-stays-reserved",
      files.executionModePolicy.text.includes('mode = MODE_WORKER_COMPUTATION') &&
        files.executionModePolicy.text.includes('status = "reserved"') &&
        files.executionModePolicy.text.includes("supportedLaunchSurfaces = emptySet()"),
      "worker_computation must stay reserved with no supported launch surface.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-process-worker-replacement-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ProcessWorkerReplacementDecision",
    task: "P13-35",
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
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile_no_facade").status === "ready",
      workerPoolPreferred: files.nodeBridgeSources.text.includes('preferredPrimitive: "worker_threads.WorkerPool"'),
      callableFacadeIntroduced: false,
      childProcessPromoted: false,
      workerComputationPromoted: false,
      secondExecutionSlotClosed: false,
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
  return scenarios.find((entry) => entry.id === id);
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Process Worker Replacement Decision",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Scenarios",
    "",
  ];
  for (const scenarioItem of report.scenarios) {
    lines.push(`- \`${scenarioItem.id}\`: \`${scenarioItem.status}\` - ${scenarioItem.title}`);
    for (const remaining of scenarioItem.remaining) {
      lines.push(`  - ${remaining}`);
    }
  }
  lines.push("", "## Checks", "");
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
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
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[index + 1];
      index += 1;
    } else if (arg === "--report-dir") {
      options.reportDir = args[index + 1];
      index += 1;
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-process-worker-replacement-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
