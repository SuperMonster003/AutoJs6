#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-child-process-scoped-runner-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_40_CHILD_PROCESS_SCOPED_RUNNER.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13ChildProcessTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeChildProcessPhase13DecisionInstrumentationTest.kt",
  processWorkerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest.kt",
  shellTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt",
  processSweepTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  projectAnalyzer: "tools/nodejs/project/autojs6-node-project.js",
  dependencyManager: "tools/nodejs/phase10/autojs6-pm.js",
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
  console.log("Phase 13 child_process scoped runner decision: " + report.decision.status);
  console.log("Phase 13 child_process blockers: " + report.blockers.length);
  console.log("Phase 13 child_process policy violations: " + report.policyViolations.length);
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
  const childProcessFeature = block(files.nodeBridgeSources.text, "child_process: Object.freeze({", "      })");
  const childProcessProfile = block(files.nodeBridgeSources.text, "childProcessProfile: Object.freeze({", "      }),");
  const scenarios = [
    scenario("diagnostic_profile_default_denial", "childProcessProfile records default denial and scoped-runner requirements", [
      token(files.nodeBridgeSources, "childProcessProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "disabled_by_default"'),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["desktop_compat_opt_in"])'),
      token(files.nodeBridgeSources, 'module: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"'),
      token(files.nodeBridgeSources, 'nodePrefix: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"'),
      token(files.nodeBridgeSources, 'scopedRunner: __autojs6_child_process_experimental_enabled ? "native_node_builtin" : "deferred"'),
      token(files.nodeBridgeSources, 'shellBridge: "separate_scoped_command_bridge"'),
      token(files.nodeBridgeSources, 'androidShellProvider: "available_with_shell_capability"'),
      token(files.nodeBridgeSources, 'appPrivateBinaries: __autojs6_child_process_experimental_enabled ? "native_node_policy" : "required_before_enablement"'),
      token(files.nodeBridgeSources, 'allowlistedBinaries: __autojs6_child_process_experimental_enabled ? "native_node_policy" : "required_before_enablement"'),
      token(files.nodeBridgeSources, 'arbitraryPaths: __autojs6_child_process_experimental_enabled ? "native_node_policy" : "denied"'),
      token(files.nodeBridgeSources, 'processHandles: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"'),
      token(files.nodeBridgeSources, "rawProcessHandles: __autojs6_child_process_experimental_enabled"),
      token(files.p13ChildProcessTest, "childProcessProfileKeepsBuiltinDeniedAndShellSeparate"),
      token(files.p13ChildProcessTest, "p13child.profile=disabled_by_default|false|desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface ChildProcessProfile"),
      token(files.typesAutojsGlobal, "readonly childProcessProfile: ChildProcessProfile"),
      token(files.typescriptSmoke, "autojsProfile.childProcessProfile.scopedRunner"),
    ], []),
    scenario("require_resolve_shadow_denial", "child_process and node:child_process stay denied before local shadow packages", [
      token(files.p13ChildProcessTest, "p13child.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13ChildProcessTest, "p13child.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13ChildProcessTest, "p13child.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13ChildProcessTest, "p13child.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13ChildProcessTest, "p13child.shadowLoaded=false"),
      token(files.processWorkerTest, "p13processworker.childProcessBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.securityRegressionTest, "require.child_process"),
      token(files.packagedSmokeTest, "packaged.dynamic_import.child_process_denied=true"),
      token(files.desktopMatrix, "Safe/default keeps child_process denied"),
    ], []),
    scenario("experimental_native_opt_in", "Debug/Desktop compatibility builds can route child_process to the native builtin", [
      token(files.nodeBridgeSources, 'if ((name === "child_process" || name === "node:child_process") && __autojs6_child_process_experimental_enabled)'),
      token(files.nodeBridgeSources, 'child_process: Object.freeze({'),
      token(files.nodeBridgeSources, 'gradleProperty: "autojs.nodejs.child_process.experimental"'),
      token(files.nodeBridgeSources, "enabled: __autojs6_child_process_experimental_enabled"),
      token(files.nodeBridgeSources, "buildTimeOverrideAllowed: true"),
      token(files.nodeBridgeSources, "releaseForbidden: true"),
      token(files.p13ChildProcessTest, "childProcessExperimentalOptInExposesNativeBuiltin"),
      token(files.p13ChildProcessTest, "childProcessExperimentalEnabled = true"),
      token(files.p13ChildProcessTest, "p13child.opt.profile=experimental_opt_in_native|native_available|native_available|true|true|false"),
      token(files.p13ChildProcessTest, "p13child.opt.require=true|true|function|function|child_process|child_process|true|true|child-ok"),
      token(files.desktopMatrix, "| `child_process` | `experimental` | `desktop_compat_opt_in` |"),
      token(files.desktopMatrix, "native opt-in"),
    ], [
      "Native child_process opt-in remains debug/build-time only until a scoped runner has app-private allowlists, bounded stdio, cleanup, packaged evidence, and hostile corpus coverage.",
    ]),
    scenario("shell_bridge_separate_alternative", "shell remains the current separately permissioned scoped command alternative", [
      token(files.p13ChildProcessTest, "p13child.shell=function|function|undefined|undefined|0|file-ok|2|shell|shell|bin|40|'bin/tool.sh' '--arg'|0"),
      token(files.shellTest, "jsShellModuleIsChildProcessAlternativeAndRequiresDeclaredCapability"),
      token(files.shellTest, "shell.policy.child_process=ERR_AUTOJS6_BUILTIN_DISABLED"),
      token(files.shellTest, "shell.policy.permissions=shell"),
      token(files.shellTest, "shell.policy.manifest_denied"),
      token(files.processSweepTest, "permission.has(\"child_process\")"),
      token(files.securityRegressionTest, "security.phase5.shell_child_process"),
      token(files.securityModel, "The `shell` bridge is the controlled AutoJs6 replacement for Node `child_process`"),
      token(files.compatibilityProfile, "migration mapping for Safe profile scripts: `child_process.exec()` -> `require(\"shell\").exec()`"),
    ], []),
    scenario("future_scoped_runner_blockers", "Future child_process opt-in requires app-private allowlist, stdio/cancel/cleanup, and packaged evidence", [
      token(files.projectAnalyzer, '["child_process", "child_process"]'),
      token(files.projectAnalyzer, "PROCESS_NATIVE_API_DISABLED"),
      token(files.dependencyManager, '{ id: "child_process", pattern: /\\brequire\\s*\\(\\s*["\'](?:node:)?child_process["\']\\s*\\)/ }'),
      token(files.dependencyManager, "dependency compatibility analyzer for lifecycle scripts, native addons, raw network, child_process"),
      token(files.compatibilityProfile, "`child_process.spawn()`, `fork()`, streaming stdio, and interactive shells are not supported"),
      token(files.securityModel, "JavaScript never receives a `Process`, file descriptor, stream, signal handle, or raw Android shell object"),
      token(files.p13Report, "For P13-40 v1.1, arbitrary executable paths"),
      token(files.p13Report, "stable-denied"),
      token(files.phase13Tasks, "For P13-40 v1.1, arbitrary executable paths"),
      token(files.phase13Tasks, "file descriptors, streams, and signals stay stable-denied"),
    ], [
      "Design the scoped child_process API shape, including which exec/execFile/spawn-style calls are allowed.",
      "Define app-private binary packaging, allowlist metadata, hash/integrity checks, and path resolution.",
      "Implement bounded stdin/stdout/stderr, timeout, cancellation, kill, and resource cleanup semantics.",
      "Add provider-side cleanup and process accounting evidence for normal exit, timeout, cancellation, app stop, and crash.",
      "Add packaged APK metadata, disclosure, install/run smoke, hostile corpus, and rollback controls before promotion.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-40", [
      token(files.phase13Tasks, "P13-40 partial landing"),
      token(files.p13Report, "P13-40 records child_process scoped runner as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13ChildProcessScopedRunnerDecision"),
      token(files.testingDoc, "verifyNodePhase13ChildProcessScopedRunnerDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13ChildProcessScopedRunnerDecision"),
      token(files.compatibilityProfile, "P13-40 records child_process scoped runner as partial"),
      token(files.securityModel, "P13-40 records child_process scoped runner as partial"),
      token(files.proComparison, "P13-40 records child_process scoped runner as partial"),
      token(files.proAutoxNotes, "P13-40 records child_process scoped runner as partial"),
      token(files.typesReadme, "P13-40 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, "P13-40 records child_process scoped-runner diagnostics"),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-40 records child_process scoped-runner diagnostics as partial"),
      token(files.builtinMatrixGenerator, "P13-40 records child_process scoped-runner diagnostics as partial"),
      token(files.capabilityMatrix, "P13-40 child_process scoped runner"),
    ], [
      "P13-40 remains partial until a real scoped child_process runner has allowlisted app-private binaries, stdio/cancel/cleanup, packaged evidence, and hostile corpus coverage.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-40"), "P13-40 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-40 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-40 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-40 evidence.",
    ),
    check(
      "child-process-feature-debug-opt-in-only",
      childProcessFeature.includes("defaultEnabled: false") &&
        childProcessFeature.includes("enabled: __autojs6_child_process_experimental_enabled") &&
        childProcessFeature.includes("buildTimeOverrideAllowed: true") &&
        childProcessFeature.includes("projectJsonOverrideAllowed: false") &&
        childProcessFeature.includes("packagedApkOverrideAllowed: false") &&
        childProcessFeature.includes("buildOnly: true") &&
        childProcessFeature.includes("debugOnly: true") &&
        childProcessFeature.includes("projectAllowed: false") &&
        childProcessFeature.includes("packagedAllowed: false") &&
        childProcessFeature.includes("releaseForbidden: true") &&
        childProcessFeature.includes('status: "experimental"') &&
        files.runtimeCapabilities.text.includes('id = "child_process"') &&
        files.runtimeCapabilities.text.includes('status = "disabled"'),
      "child_process native builtin opt-in must stay build-time/debug-only and release-forbidden.",
    ),
    check(
      "child-process-profile-default-fail-closed",
      childProcessProfile.includes("defaultEnabled: false") &&
        childProcessProfile.includes('module: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"') &&
        childProcessProfile.includes('nodePrefix: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"') &&
        childProcessProfile.includes('arbitraryPaths: __autojs6_child_process_experimental_enabled ? "native_node_policy" : "denied"') &&
        childProcessProfile.includes('processHandles: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"') &&
        childProcessProfile.includes("rawProcessHandles: __autojs6_child_process_experimental_enabled"),
      "childProcessProfile must report default fail-closed state and explicit experimental native state.",
    ),
    check(
      "safe-default-builtin-denial-present",
      files.nodeBridgeSources.text.includes('"child_process": true') &&
        files.nodeBridgeSources.text.includes('if (name === "child_process" && __autojs6_child_process_experimental_enabled)') &&
        files.desktopMatrix.text.includes("Safe/default keeps child_process denied"),
      "child_process must remain denied by default while the native builtin is debug opt-in.",
    ),
    check(
      "dangerous-process-shapes-rejected",
      childProcessProfile.includes('arbitraryPaths: __autojs6_child_process_experimental_enabled ? "native_node_policy" : "denied"') &&
        childProcessProfile.includes('detached: "denied"') &&
        childProcessProfile.includes('shellOperators: __autojs6_child_process_experimental_enabled ? "native_shell_option" : "denied"') &&
      childProcessProfile.includes('processHandles: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"') &&
        childProcessProfile.includes('fdStreams: __autojs6_child_process_experimental_enabled ? "native_available" : "denied"') &&
        files.p13Report.text.includes("Arbitrary paths, detached processes, shell operators") &&
        files.p13Report.text.includes("raw file descriptors, streams, and signals") &&
        files.p13Report.text.includes("package-managed downloaded") &&
        files.p13Report.text.includes("binaries, shell operators") &&
        files.compatibilityProfile.text.includes("spawn()`, `fork()`, streaming stdio, and interactive shells are not supported") &&
        files.securityModel.text.includes("JavaScript never receives a `Process`, file descriptor, stream, signal handle, or raw Android shell object"),
      "P13-40 must keep arbitrary paths, downloaded binaries, shell operators, detached processes, and inherited handles rejected in the Safe/default boundary.",
    ),
    check(
      "no-scoped-runner-types-or-runtime-facade",
      !files.typesAutojsGlobal.text.includes('declare module "child_process"') &&
        !files.typesAutojsGlobal.text.includes('declare module "node:child_process"') &&
        !files.nodeBridgeSources.text.includes("ChildProcessRunner") &&
        !files.nodeBridgeSources.text.includes("childProcessProfile.enabled"),
      "P13-40 must not introduce a supported scoped runner or child_process type declaration.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-child-process-scoped-runner-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ChildProcessScopedRunnerDecision",
    task: "P13-40",
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
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile_default_denial").status === "ready",
      defaultDeniedReady: scenarioById(scenarios, "require_resolve_shadow_denial").status === "ready",
      experimentalNativeOptInReady: scenarioById(scenarios, "experimental_native_opt_in").status !== "missing_evidence",
      shellBridgeAlternativeReady: scenarioById(scenarios, "shell_bridge_separate_alternative").status === "ready",
      scopedRunnerIntroduced: false,
      dangerousProcessShapesRejected: checkById(checks, "dangerous-process-shapes-rejected").passed,
      appPrivateBinaryAllowlistReady: false,
      stdioCancelCleanupReady: false,
      packagedBehaviorPromoted: false,
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

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
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

function block(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  if (start < 0) return "";
  const end = text.indexOf(endToken, start);
  if (end < 0) return text.slice(start);
  return text.slice(start, end + endToken.length);
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Child Process Scoped Runner Decision",
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
      console.log("Usage: node verify-child-process-scoped-runner-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
