#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-controlled-wasi-profile-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_38_CONTROLLED_WASI_PROFILE.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13WasiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasiPhase13DecisionInstrumentationTest.kt",
  wasmWasiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt",
  realNpmCompatTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt",
  phase9WasmWasiReport: "docs/nodejs/reports/phase9/WASM_WASI.md",
  phase9WasmWasiPolicy: "tools/nodejs/wasm/verify-phase9-wasm-wasi-policy.js",
  phase10WorkerWasiBoundary: "tools/nodejs/phase10/verify-worker-wasi-boundary.js",
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
  console.log("Phase 13 controlled WASI profile decision: " + report.decision.status);
  console.log("Phase 13 controlled WASI blockers: " + report.blockers.length);
  console.log("Phase 13 controlled WASI policy violations: " + report.policyViolations.length);
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
    scenario("diagnostic_profile_default_denial", "wasiProfile records pure WASM availability, raw WASI denial, and controlled facade requirements", [
      token(files.nodeBridgeSources, "wasiProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "disabled_by_default"'),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["desktop_compat_opt_in"])'),
      token(files.nodeBridgeSources, 'pureWebAssembly: "available"'),
      token(files.nodeBridgeSources, 'rawNodeWasi: "denied"'),
      token(files.nodeBridgeSources, 'controlledFacade: "deferred"'),
      token(files.nodeBridgeSources, 'preopens: "scoped_or_authorized_roots_required"'),
      token(files.nodeBridgeSources, 'authorizedRoots: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'fd: "virtualized_required"'),
      token(files.nodeBridgeSources, 'network: "denied"'),
      token(files.p13WasiTest, "wasiProfileKeepsRawWasiDeniedAndControlledFacadeDeferred"),
      token(files.p13WasiTest, "p13wasi.profile=disabled_by_default|false|desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface WasiProfile"),
      token(files.typesAutojsGlobal, "readonly wasiProfile: WasiProfile"),
      token(files.typescriptSmoke, "autojsProfile.wasiProfile.preopens"),
    ], []),
    scenario("raw_wasi_shadow_denial", "wasi and node:wasi stay denied before local shadow packages", [
      token(files.p13WasiTest, "p13wasi.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13WasiTest, "p13wasi.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13WasiTest, "p13wasi.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13WasiTest, "p13wasi.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
      token(files.p13WasiTest, "p13wasi.shadowLoaded=false"),
      token(files.wasmWasiTest, "rawWasiBuiltinsRemainDisabledAndCannotBeShadowed"),
      token(files.wasmWasiTest, "require(\"node:wasi\")"),
      token(files.wasmWasiTest, "require.resolve(\"node:wasi\")"),
      token(files.wasmWasiTest, "wasi.shadowLoaded=false"),
      token(files.realNpmCompatTest, "controlled WASI facade detection"),
    ], []),
    scenario("future_controlled_wasi_blockers", "preopen broker, fd/env/args, resource, worker, and packaged blockers remain explicit", [
      token(files.phase9WasmWasiReport, "WebAssembly is allowed as a pure JavaScript/V8 capability"),
      token(files.phase9WasmWasiReport, "Raw `node:wasi` remains disabled"),
      token(files.phase9WasmWasiReport, "Future `autojs6:wasi` must map preopens through the scoped-fs broker"),
      token(files.phase9WasmWasiReport, "deny arbitrary host paths, inherited process env, raw fd numbers"),
      token(files.phase9WasmWasiReport, "unrestricted sockets, and process termination"),
      token(files.phase9WasmWasiReport, "resource limits for module size, memory pages, stdout/stderr, timeout"),
      token(files.phase10WorkerWasiBoundary, "controlledWasiPromoted: false"),
      token(files.phase10WorkerWasiBoundary, "rawWasiPromoted: false"),
      token(files.phase9WasmWasiPolicy, "controlled-wasi-facade"),
      token(files.desktopMatrix, "| `wasi` | `disabled` |"),
      token(files.p13Report, "For P13-38 v1.1, pure WebAssembly is the supported runtime path"),
      token(files.phase13Tasks, "For P13-38 v1.1, pure WebAssembly is the supported runtime path"),
      token(files.compatibilityProfile, "pure WebAssembly is the supported runtime path and raw/controlled WASI"),
      token(files.securityModel, "raw/controlled WASI APIs stay stable-denied"),
      token(files.typesReadme, "raw/controlled WASI APIs stay"),
    ], [
      "Implement scoped/authorized preopen mapping with lexical and realpath/symlink escape denial.",
      "Define args, env, virtual fd table, stdio, clock, random, process-exit, and network-denial policies with instrumentation.",
      "Add resource budgets for module size, memory pages, stdout/stderr, timeout, and concurrent instances.",
      "Add packaged `.wasm`/WASI asset manifest, tamper checks, and packaged behavior evidence.",
      "Add hostile WASI corpus and worker/WASM integration evidence before promotion.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, desktop matrix, surface manifest, and Gradle gate record P13-38", [
      token(files.phase13Tasks, "P13-38 partial landing"),
      token(files.p13Report, "P13-38 records controlled WASI profile as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13ControlledWasiProfileDecision"),
      token(files.testingDoc, "verifyNodePhase13ControlledWasiProfileDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13ControlledWasiProfileDecision"),
      token(files.compatibilityProfile, "P13-38 records controlled WASI profile as partial"),
      token(files.securityModel, "P13-38 records controlled WASI profile as partial"),
      token(files.proComparison, "P13-38 records controlled WASI profile as partial"),
      token(files.proAutoxNotes, "P13-38 records controlled WASI profile as partial"),
      token(files.typesReadme, "P13-38 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-38 records controlled WASI diagnostics as partial"),
      token(files.builtinMatrixGenerator, "P13-38 records controlled WASI diagnostics as partial"),
      token(files.capabilityMatrix, "P13-38 controlled WASI profile"),
    ], [
      "P13-38 remains partial until a real controlled WASI facade and preopen/resource/packaged evidence exist.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-38"), "P13-38 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-38 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-38 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-38 evidence.",
    ),
    check(
      "wasi-stays-disabled",
      files.desktopMatrix.text.includes("| `wasi` | `disabled` |") &&
        files.runtimeCapabilities.text.includes('"wasi"') &&
        files.nodeBridgeSources.text.includes("wasiProfile: Object.freeze({") &&
        files.nodeBridgeSources.text.includes('rawNodeWasi: "denied"') &&
        files.nodeBridgeSources.text.includes('module: "denied"'),
      "raw WASI must remain disabled while P13-38 is only a controlled-profile decision gate.",
    ),
    check(
      "no-default-wasi-enable",
      files.nodeBridgeSources.text.includes("wasiProfile: Object.freeze({") &&
        files.nodeBridgeSources.text.includes("defaultEnabled: false"),
      "P13-38 must not enable WASI by default.",
    ),
    check(
      "no-wasi-runtime-facade",
      !files.nodeBridgeSources.text.includes('name === "autojs6:wasi"') &&
        !files.nodeBridgeSources.text.includes("function __autojs6_wasi") &&
        !files.nodeBridgeSources.text.includes("AutoJs6Wasi") &&
        !files.typesAutojsGlobal.text.includes('declare module "wasi"') &&
        !files.typesAutojsGlobal.text.includes('declare module "node:wasi"'),
      "P13-38 must not introduce a callable WASI facade or script-visible raw WASI module.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-controlled-wasi-profile-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ControlledWasiProfileDecision",
    task: "P13-38",
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
      rawWasiDeniedReady: scenarioById(scenarios, "raw_wasi_shadow_denial").status === "ready",
      pureWebAssemblyReady: true,
      controlledFacadeIntroduced: false,
      preopenBrokerProven: false,
      resourceBudgetsProven: false,
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
    "# Phase 13 Controlled WASI Profile Decision",
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
      console.log("Usage: node verify-controlled-wasi-profile-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
