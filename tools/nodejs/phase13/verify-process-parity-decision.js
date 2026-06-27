#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-process-parity-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_44_PROCESS_PARITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase13/generate-node-builtin-parity-matrix.js",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13ProcessTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessParityPhase13DecisionInstrumentationTest.kt",
  processSweepTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt",
  embeddedMvpTest: "app/src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt",
  inspectorDesignTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorDesignGateInstrumentationTest.kt",
  inspectorSecurityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt",
  moduleCompileCacheTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeModuleCompileCacheInstrumentationTest.kt",
  modernCoreTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeModernCoreDifferentialInstrumentationTest.kt",
  modernCoreManifest: "app/src/androidTest/assets/node_modern_core_differential/manifest.json",
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
  console.log("Phase 13 process parity decision: " + report.decision.status);
  console.log("Phase 13 process parity blockers: " + report.blockers.length);
  console.log("Phase 13 process parity policy violations: " + report.policyViolations.length);
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
  const modulesSurface = surface(files.surfaceManifest.text, "desktop.modules_npm_process");
  const processProfile = block(files.nodeBridgeSources.text, "processParityProfile: Object.freeze({", "      filesystemProfile: Object.freeze({");
  const scenarios = [
    scenario("diagnostic_profile", "processParityProfile records Safe process subset coverage and fail-closed desktop gaps", [
      token(files.nodeBridgeSources, "processParityProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "safe_process_subset_partial"'),
      token(files.nodeBridgeSources, 'argv: "entry_and_project_runner_partial"'),
      token(files.nodeBridgeSources, 'env: "controlled_string_map"'),
      token(files.nodeBridgeSources, 'envMutation: "script_local_string_coercing_mutable"'),
      token(files.nodeBridgeSources, 'cwd: "scoped_working_directory"'),
      token(files.nodeBridgeSources, 'chdir: "scoped_relative_directory_only"'),
      token(files.nodeBridgeSources, 'exit: "lifecycle_mapped_process_exit"'),
      token(files.nodeBridgeSources, 'signals: "denied_no_android_signal_delivery"'),
      token(files.nodeBridgeSources, 'permission: "autojs6_readonly_capability_query"'),
      token(files.nodeBridgeSources, 'report: "getReport_only_write_denied"'),
      token(files.nodeBridgeSources, "rawDesktopParity: false"),
      token(files.nodeBridgeSources, "rawEnvInheritance: false"),
      token(files.nodeBridgeSources, "rawSignals: false"),
      token(files.nodeBridgeSources, "nativeBindings: false"),
      token(files.p13ProcessTest, "processParityProfileRecordsSafeSubsetAndStableDenials"),
      token(files.p13ProcessTest, "p13process.profile=safe_process_subset_partial|true|safe_default,desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface ProcessParityProfile"),
      token(files.typesAutojsGlobal, "readonly processParityProfile: ProcessParityProfile"),
      token(files.typescriptSmoke, "autojsProfile.processParityProfile.envDefaultKeys"),
    ], []),
    scenario("runtime_behavior", "Focused and existing instrumentation cover argv/env/cwd/process lifecycle behavior without widening unsafe APIs", [
      token(files.p13ProcessTest, 'const processBuiltin = require("process")'),
      token(files.p13ProcessTest, 'const nodeProcessBuiltin = require("node:process")'),
      token(files.p13ProcessTest, 'process.env.P13_PROCESS_MUTABLE = "changed"'),
      token(files.p13ProcessTest, "process.env.P13_PROCESS_NUMBER = 7"),
      token(files.p13ProcessTest, 'Object.defineProperty(process.env, "P13_PROCESS_DEFINED"'),
      token(files.p13ProcessTest, 'process.chdir("sub")'),
      token(files.p13ProcessTest, "process.exitCode = 44"),
      token(files.p13ProcessTest, "process.report.getReport"),
      token(files.p13ProcessTest, "process.permission.query()"),
      token(files.p13ProcessTest, "process.allowedNodeEnvironmentFlags"),
      token(files.p13ProcessTest, "process.umask(0)"),
      token(files.p13ProcessTest, "processExitMapsToEmbeddedLifecycleResult"),
      token(files.p13ProcessTest, "p13process.exit.zero.before"),
      token(files.p13ProcessTest, "p13process.exit.seven.before"),
      token(files.p13ProcessTest, "p13process.exit.async.before"),
      token(files.p13ProcessTest, "ERR_AUTOJS6_PROCESS_EXIT"),
      token(files.processSweepTest, "processCompatibilityAndDangerousApiBoundariesWork"),
      token(files.processSweepTest, "processPermissionReadonlyFacadeReflectsManifestAndScopedFsPolicy"),
      token(files.processSweepTest, "process.sweep.exitCode=23"),
      token(files.embeddedMvpTest, "nodeProjectRunnerRunsEntryWithArgvEnvCwdShebangAndEscaping"),
      token(files.embeddedMvpTest, 'argv = listOf("--name", "AutoJs6", "--quoted", quotedArg)'),
      token(files.embeddedMvpTest, "env = mapOf("),
      token(files.inspectorDesignTest, "NODE_INSPECT_RESUME_ON_START"),
      token(files.inspectorSecurityTest, "NODE_OPTIONS"),
      token(files.moduleCompileCacheTest, "NODE_COMPILE_CACHE"),
    ], [
      "Add packaged lifecycle evidence for argv/env/cwd/process-exit behavior.",
    ]),
    scenario("matrix_modern_core_and_docs", "Builtin matrix, modern-core differential, docs, release matrix, surface manifest, and Gradle gate record P13-44", [
      token(files.runtimeCapabilities, '"process"'),
      token(files.matrixGenerator, "P13-44 records process/env/argv parity as partial"),
      token(files.desktopMatrix, "P13-44 records process/env/argv parity as partial"),
      token(files.modernCoreTest, "modernCoreDifferentialCorpusPassesAndEmitsAutoJsResults"),
      token(files.modernCoreManifest, '"area": "process.permission shape"'),
      token(files.phase13Tasks, "P13-44 partial landing"),
      token(files.p13Report, "P13-44 records process/env/argv parity as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13ProcessParityDecision"),
      token(files.testingDoc, "verifyNodePhase13ProcessParityDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13ProcessParityDecision"),
      token(files.compatibilityProfile, "P13-44 records process/env/argv parity as partial"),
      token(files.securityModel, "P13-44 records process/env/argv parity as partial"),
      token(files.proComparison, "P13-44 records process/env/argv parity as partial"),
      token(files.proAutoxNotes, "P13-44 records process/env/argv parity as partial"),
      token(files.typesReadme, "P13-44 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-44 process/env/argv parity"),
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-44"), "P13-44 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-44 report must exist."),
    check("surface-present", Boolean(modulesSurface), "`desktop.modules_npm_process` surface must exist."),
    check(
      "surface-evidence",
      modulesSurface && (modulesSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.modules_npm_process` surface must reference P13-44 evidence.",
    ),
    check(
      "profile-covered",
      [
        'argv: "entry_and_project_runner_partial"',
        'env: "controlled_string_map"',
        'cwd: "scoped_working_directory"',
        'resourceUsage: "partial_runtime_snapshot"',
        'permission: "autojs6_readonly_capability_query"',
        'report: "getReport_only_write_denied"',
      ].every((value) => processProfile.includes(value)),
      "processParityProfile must list argv, env, cwd, resource, permission, and report subset coverage.",
    ),
    check(
      "profile-fail-closed-boundaries",
      processProfile.includes('exit: "lifecycle_mapped_process_exit"') &&
        processProfile.includes('signals: "denied_no_android_signal_delivery"') &&
        processProfile.includes('"native_process_exit"') &&
        processProfile.includes("rawDesktopParity: false") &&
        processProfile.includes("rawEnvInheritance: false") &&
        processProfile.includes("rawSignals: false") &&
        processProfile.includes("rawProcessHandles: false") &&
        processProfile.includes("nativeBindings: false"),
      "processParityProfile must keep raw desktop parity, raw env inheritance, signals, process handles, native process exit, and native bindings fail-closed.",
    ),
    check(
      "no-unsafe-process-promotion",
      !files.nodeBridgeSources.text.includes('exit: "partial"') &&
        !files.nodeBridgeSources.text.includes('signals: "partial"') &&
        !files.nodeBridgeSources.text.includes("rawDesktopParity: true") &&
        !files.nodeBridgeSources.text.includes("rawEnvInheritance: true") &&
        !files.nodeBridgeSources.text.includes("rawSignals: true") &&
        !files.nodeBridgeSources.text.includes("rawProcessHandles: true") &&
        !files.nodeBridgeSources.text.includes("nativeBindings: true"),
      "P13-44 must not mark native process exit, signals, env inheritance, handles, native bindings, or full desktop parity as promoted.",
    ),
    check(
      "full-desktop-process-boundary-decided",
      files.p13Report.text.includes("P13-44 is a Safe process subset only") &&
        files.p13Report.text.includes("does not claim full desktop process") &&
        files.p13Report.text.includes("parity") &&
        processProfile.includes("rawDesktopParity: false"),
      "P13-44 must decide and document the full desktop process parity boundary.",
    ),
    check(
      "signal-and-native-exit-stable-denied",
      files.p13Report.text.includes("Android signal delivery and native/raw process termination are stable-denied") &&
        files.p13Report.text.includes("never calls native process exit") &&
        processProfile.includes('signals: "denied_no_android_signal_delivery"') &&
        processProfile.includes('"native_process_exit"') &&
        processProfile.includes("rawSignals: false"),
      "P13-44 must keep Android signal delivery and native process termination stable-denied.",
    ),
    check(
      "inherited-env-and-exit-policy-finalized",
      files.p13Report.text.includes("Inherited env is not raw host inheritance") &&
        files.p13Report.text.includes("Scripts receive controlled request") &&
        files.p13Report.text.includes("values plus safe defaults") &&
        files.p13Report.text.includes("`process.exit(code)` remains lifecycle-mapped") &&
        processProfile.includes('env: "controlled_string_map"') &&
        processProfile.includes("rawEnvInheritance: false") &&
        processProfile.includes('exit: "lifecycle_mapped_process_exit"'),
      "P13-44 must finalize inherited environment and process exit policy.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-process-parity-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ProcessParityDecision",
    task: "P13-44",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: modulesSurface || {
      id: "desktop.modules_npm_process",
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
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile").status === "ready",
      runtimeBehaviorReady: scenarioById(scenarios, "runtime_behavior").status !== "missing_evidence",
      fullDesktopProcessBoundaryDecided: checkById(checks, "full-desktop-process-boundary-decided").passed,
      signalAndNativeExitStableDenied: checkById(checks, "signal-and-native-exit-stable-denied").passed,
      inheritedEnvAndProcessExitPolicyFinalized: checkById(checks, "inherited-env-and-exit-policy-finalized").passed,
      rawProcessExitPromoted: false,
      rawSignalsPromoted: false,
      rawEnvInheritancePromoted: false,
      nativeBindingsPromoted: false,
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

function block(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  if (start < 0) return "";
  const end = text.indexOf(endToken, start);
  if (end < 0) return text.slice(start);
  return text.slice(start, end + endToken.length);
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Process Parity Decision",
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
      console.log("Usage: node verify-process-parity-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
