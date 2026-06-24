#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-vm-module-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_36_VM_MODULE_DECISION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13VmTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeVmPhase13DecisionInstrumentationTest.kt",
  vmDesignGateTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeVmDesignGateInstrumentationTest.kt",
  coreBuiltinTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  vmDesignDoc: "docs/nodejs/design/VM.md",
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
  console.log("Phase 13 vm module decision: " + report.decision.status);
  console.log("Phase 13 vm blockers: " + report.blockers.length);
  console.log("Phase 13 vm policy violations: " + report.policyViolations.length);
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
    scenario("diagnostic_profile_default_denial", "vmProfile records default denial and future enablement requirements", [
      token(files.nodeBridgeSources, "vmProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "disabled_by_default"'),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["desktop_compat_opt_in", "debug_unsafe_lab"])'),
      token(files.nodeBridgeSources, 'module: "denied"'),
      token(files.nodeBridgeSources, 'timeout: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'privateBindings: "denied"'),
      token(files.nodeBridgeSources, 'hostObjects: "denied"'),
      token(files.p13VmTest, "vmProfileKeepsModuleDeniedAndEnablementDeferred"),
      token(files.p13VmTest, "p13vm.profile=disabled_by_default|false|desktop_compat_opt_in,debug_unsafe_lab"),
      token(files.typesAutojsGlobal, "export interface VmProfile"),
      token(files.typesAutojsGlobal, "readonly vmProfile: VmProfile"),
      token(files.typescriptSmoke, "autojsProfile.vmProfile.timeout"),
    ], []),
    scenario("require_resolve_shadow_denial", "vm and node:vm stay denied before local shadow packages", [
      token(files.p13VmTest, "p13vm.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13VmTest, "p13vm.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13VmTest, "p13vm.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13VmTest, "p13vm.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13VmTest, "p13vm.shadowLoaded=false"),
      token(files.vmDesignGateTest, "vmRemainsDesignGatedAndDisabledByDefault"),
      token(files.vmDesignGateTest, "require(\"node:vm\")"),
      token(files.vmDesignGateTest, "require.resolve(\"node:vm\")"),
      token(files.coreBuiltinTest, "requireDenied(\"node:vm\")"),
      token(files.packagedSmokeTest, 'moduleBuiltin.isBuiltin("node:vm")'),
    ], []),
    scenario("future_enablement_blockers", "context isolation, timeout, memory, bridge, process, and packaged blockers remain explicit", [
      token(files.vmDesignDoc, "no access to raw Android or AutoJs bridge objects"),
      token(files.vmDesignDoc, "no bypass of scoped filesystem"),
      token(files.vmDesignDoc, "no access to disabled builtins or process internals"),
      token(files.vmDesignDoc, "deterministic timeout and lifecycle cancellation"),
      token(files.vmDesignDoc, "memory and compiled-script limits"),
      token(files.vmDesignDoc, "packaged APK behavior and diagnostics"),
      token(files.securityModel, "`vm` remains disabled because a second JavaScript context is not a security boundary"),
      token(files.runtimeCapabilities, 'id = "vm"'),
      token(files.runtimeCapabilities, 'status = "disabled"'),
      token(files.desktopMatrix, "| `vm` | `disabled` |"),
    ], [
      "Design and implement a limited vm.Script or context surface only after timeout, memory, global leakage, bridge leakage, and process exposure are proven safe.",
      "Add positive/negative instrumentation for context cleanup, memory budgets, and disabled-builtin escape attempts before any opt-in promotion.",
      "Add packaged APK behavior and diagnostics evidence for the final Desktop/Debug profile.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-36", [
      token(files.phase13Tasks, "P13-36 partial landing"),
      token(files.p13Report, "P13-36 records `vm` module promotion as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13VmModuleDecision"),
      token(files.testingDoc, "verifyNodePhase13VmModuleDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13VmModuleDecision"),
      token(files.compatibilityProfile, "P13-36 records `vm` module promotion as partial"),
      token(files.securityModel, "P13-36 records `vm` module promotion as partial"),
      token(files.proComparison, "P13-36 records `vm` module promotion as partial"),
      token(files.proAutoxNotes, "P13-36 records `vm` module promotion as partial"),
      token(files.typesReadme, "P13-36 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-36 records vm module diagnostics as partial"),
      token(files.builtinMatrixGenerator, "P13-36 records vm module diagnostics as partial"),
      token(files.capabilityMatrix, "P13-36 vm module decision"),
    ], [
      "P13-36 remains partial until a limited VM API and escape/timeout/memory/packaged evidence are complete.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-36"), "P13-36 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-36 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-36 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-36 evidence.",
    ),
    check(
      "vm-stays-disabled",
      files.desktopMatrix.text.includes("| `vm` | `disabled` |") &&
        files.runtimeCapabilities.text.includes('id = "vm"') &&
        files.runtimeCapabilities.text.includes('status = "disabled"') &&
        files.nodeBridgeSources.text.includes('module: "denied"'),
      "vm must remain disabled while P13-36 is only a decision/profile gate.",
    ),
    check(
      "no-default-vm-enable",
      files.nodeBridgeSources.text.includes("defaultEnabled: false") &&
        !files.nodeBridgeSources.text.includes('vm: Object.freeze({\n        id: "vm",\n        diagnosticName: "vm",\n        gradleProperty: null,\n        defaultEnabled: true'),
      "P13-36 must not enable vm by default.",
    ),
    check(
      "no-vm-script-runtime-surface",
      !files.nodeBridgeSources.text.includes("class AutoJs6VmScript") &&
        !files.nodeBridgeSources.text.includes("function __autojs6_vm_script") &&
        !files.nodeBridgeSources.text.includes('Script: function') &&
        !files.typesAutojsGlobal.text.includes('declare module "vm"'),
      "P13-36 must not introduce a callable vm.Script runtime surface.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-vm-module-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13VmModuleDecision",
    task: "P13-36",
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
      callableVmScriptIntroduced: false,
      contextIsolationProven: false,
      timeoutPolicyProven: false,
      memoryBudgetProven: false,
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

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 VM Module Decision",
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
      console.log("Usage: node verify-vm-module-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
