#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-inspector-debug-profile-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_37_INSPECTOR_DEBUG_PROFILE.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13InspectorTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorPhase13DecisionInstrumentationTest.kt",
  inspectorDesignGateTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorDesignGateInstrumentationTest.kt",
  inspectorSecurityGateTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt",
  moduleSafeSubsetTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeModuleSafeSubsetInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  inspectorDesignDoc: "docs/nodejs/design/INSPECTOR.md",
  phase9InspectorPolicy: "tools/nodejs/debug/verify-phase9-inspector-policy.js",
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
  console.log("Phase 13 inspector debug profile decision: " + report.decision.status);
  console.log("Phase 13 inspector blockers: " + report.blockers.length);
  console.log("Phase 13 inspector policy violations: " + report.policyViolations.length);
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
    scenario("diagnostic_profile_default_denial", "inspectorProfile records default denial and future debug-session requirements", [
      token(files.nodeBridgeSources, "inspectorProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "disabled_by_default"'),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["debug_unsafe_lab"])'),
      token(files.nodeBridgeSources, 'module: "denied"'),
      token(files.nodeBridgeSources, 'devtools: "deferred"'),
      token(files.nodeBridgeSources, 'inspectorProtocol: "deferred"'),
      token(files.nodeBridgeSources, 'adbForwarding: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'explicitUserAction: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'releaseBuild: "denied"'),
      token(files.nodeBridgeSources, 'sensitivePathRedaction: "not_proven"'),
      token(files.p13InspectorTest, "inspectorProfileKeepsDebugToolingDeniedAndDeferred"),
      token(files.p13InspectorTest, "p13inspector.profile=disabled_by_default|false|debug_unsafe_lab"),
      token(files.typesAutojsGlobal, "export interface InspectorProfile"),
      token(files.typesAutojsGlobal, "readonly inspectorProfile: InspectorProfile"),
      token(files.typescriptSmoke, "autojsProfile.inspectorProfile.adbForwarding"),
    ], []),
    scenario("require_resolve_shadow_denial", "inspector and node:inspector stay denied before local shadow packages", [
      token(files.p13InspectorTest, "p13inspector.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13InspectorTest, "p13inspector.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13InspectorTest, "p13inspector.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13InspectorTest, "p13inspector.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13InspectorTest, "p13inspector.shadowLoaded=false"),
      token(files.inspectorDesignGateTest, "scriptVisibleInspectorSurfacesStayDisabled"),
      token(files.inspectorDesignGateTest, "require(\"node:inspector\")"),
      token(files.inspectorDesignGateTest, "require.resolve(\"node:inspector\")"),
      token(files.inspectorDesignGateTest, "process.getBuiltinModule(\"inspector\")"),
      token(files.moduleSafeSubsetTest, "\"node:inspector\""),
      token(files.securityRegressionTest, "require.inspector"),
    ], []),
    scenario("future_debug_profile_blockers", "debug inspector, DevTools, profiling, and packaged behavior blockers remain explicit", [
      token(files.inspectorDesignDoc, "debug-build-only"),
      token(files.inspectorDesignDoc, "explicit user action"),
      token(files.inspectorDesignDoc, "loopback binding only"),
      token(files.inspectorDesignDoc, "ADB Forward And DevTools Workflow"),
      token(files.inspectorDesignDoc, "DevTools attaches through `127.0.0.1:<host-port>`"),
      token(files.inspectorDesignDoc, "CPU profiles and heap snapshots"),
      token(files.inspectorSecurityGateTest, "releaseAndPackagedMetadataCannotEnableInspector"),
      token(files.inspectorSecurityGateTest, "inspectorPortIsNotLeftListeningAfterScriptStopOrTimeout"),
      token(files.inspectorSecurityGateTest, "profileArtifactsRemainAbsentAndDoNotLeakSensitiveMetadata"),
      token(files.phase9InspectorPolicy, "Phase 9 inspector policy decision"),
      token(files.securityModel, "Future inspector support must be"),
      token(files.desktopMatrix, "| `inspector` | `disabled` |"),
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-37", [
      token(files.phase13Tasks, "P13-37 partial landing"),
      token(files.p13Report, "P13-37 records the v1.1 inspector stable-denial debug-profile decision as ready"),
      token(files.phase13GradleGate, "verifyNodePhase13InspectorDebugProfileDecision"),
      token(files.testingDoc, "verifyNodePhase13InspectorDebugProfileDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13InspectorDebugProfileDecision"),
      token(files.compatibilityProfile, "P13-37 records inspector/devtools debug profile as partial"),
      token(files.securityModel, "P13-37 records inspector/devtools debug profile as partial"),
      token(files.proComparison, "P13-37 records inspector/devtools debug profile as partial"),
      token(files.proAutoxNotes, "P13-37 records inspector/devtools debug profile as partial"),
      token(files.typesReadme, "P13-37 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-37 records inspector debug-profile diagnostics as partial"),
      token(files.builtinMatrixGenerator, "P13-37 records inspector debug-profile diagnostics as partial"),
      token(files.capabilityMatrix, "P13-37 inspector debug profile"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-37"), "P13-37 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-37 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-37 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-37 evidence.",
    ),
    check(
      "inspector-stays-disabled",
      files.desktopMatrix.text.includes("| `inspector` | `disabled` |") &&
        files.runtimeCapabilities.text.includes('id = "inspector"') &&
        files.runtimeCapabilities.text.includes('status = "disabled"') &&
        files.nodeBridgeSources.text.includes("inspectorProfile: Object.freeze({") &&
        files.nodeBridgeSources.text.includes('module: "denied"'),
      "inspector must remain disabled while P13-37 is only a debug-profile decision gate.",
    ),
    check(
      "no-default-inspector-enable",
      files.nodeBridgeSources.text.includes("inspectorProfile: Object.freeze({") &&
        files.nodeBridgeSources.text.includes("defaultEnabled: false") &&
        !files.nodeBridgeSources.text.includes('inspector: Object.freeze({\n        id: "inspector",\n        diagnosticName: "inspector",\n        gradleProperty: null,\n        defaultEnabled: true'),
      "P13-37 must not enable inspector by default.",
    ),
    check(
      "no-inspector-session-runtime-surface",
      !files.nodeBridgeSources.text.includes("function __autojs6_start_inspector") &&
        !files.nodeBridgeSources.text.includes("function __autojs6_inspector_session") &&
        !files.nodeBridgeSources.text.includes("InspectorSession") &&
        !files.nodeBridgeSources.text.includes("inspector.open(") &&
        !files.typesAutojsGlobal.text.includes('declare module "inspector"'),
      "P13-37 must not introduce a callable inspector session or script-visible inspector module.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-inspector-debug-profile-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13InspectorDebugProfileDecision",
    task: "P13-37",
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
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile_default_denial").status === "ready",
      defaultDeniedReady: scenarioById(scenarios, "require_resolve_shadow_denial").status === "ready",
      debugSessionIntroduced: false,
      devtoolsAttachProven: false,
      profilerArtifactsProven: false,
      loopbackAdbForwardingProven: false,
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
    "# Phase 13 Inspector Debug Profile Decision",
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
      console.log("Usage: node verify-inspector-debug-profile-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
