#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-stdlib-conformance-decision";
const P13_MODULES = Object.freeze([
  "crypto",
  "stream",
  "stream/promises",
  "stream/consumers",
  "stream/web",
  "zlib",
  "buffer",
  "url",
  "os",
  "readline",
  "readline/promises",
  "tty",
  "perf_hooks",
  "async_hooks",
]);

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_43_STDLIB_CONFORMANCE.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase13/generate-node-builtin-parity-matrix.js",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13StdlibTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeStdlibPhase13DecisionInstrumentationTest.kt",
  coreBuiltinTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt",
  realNpmCompatTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt",
  realNpmManifest: "app/src/androidTest/assets/npm_compat_real/manifest.json",
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
  console.log("Phase 13 stdlib conformance decision: " + report.decision.status);
  console.log("Phase 13 stdlib blockers: " + report.blockers.length);
  console.log("Phase 13 stdlib policy violations: " + report.policyViolations.length);
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
  const stdlibProfile = block(files.nodeBridgeSources.text, "stdlibProfile: Object.freeze({", "      filesystemProfile: Object.freeze({");
  const scenarios = [
    scenario("diagnostic_profile", "stdlibProfile records Safe stdlib subset coverage and fail-closed desktop gaps", [
      token(files.nodeBridgeSources, "stdlibProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "safe_subset_partial"'),
      token(files.nodeBridgeSources, "defaultEnabled: true"),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["safe_default", "desktop_compat_opt_in"])'),
      token(files.nodeBridgeSources, 'conformanceSet: "node24_safe_stdlib_subset"'),
      token(files.nodeBridgeSources, 'processGetBuiltinModule: "controlled_partial"'),
      token(files.nodeBridgeSources, 'androidDifferences: "documented_stable"'),
      token(files.nodeBridgeSources, 'resourceLimits: "bounded_callbacks_streams_crypto_zlib"'),
      token(files.nodeBridgeSources, 'npmCorpus: "real_npm_phase9_30_fixture_baseline"'),
      token(files.nodeBridgeSources, 'desktopDifferential: "modern_core_s7_36_partial"'),
      token(files.nodeBridgeSources, "rawDesktopParity: false"),
      token(files.nodeBridgeSources, "rawNativeHandles: false"),
      token(files.nodeBridgeSources, "unrestrictedNetwork: false"),
      token(files.nodeBridgeSources, "broadTerminal: false"),
      token(files.p13StdlibTest, "stdlibProfileAndRepresentativeModulesRecordPartialConformance"),
      token(files.p13StdlibTest, "p13stdlib.profile=safe_subset_partial|true|safe_default,desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface StdlibProfile"),
      token(files.typesAutojsGlobal, "readonly stdlibProfile: StdlibProfile"),
      token(files.typescriptSmoke, "autojsProfile.stdlibProfile.conformanceModules"),
    ], []),
    scenario("representative_module_conformance", "Representative device coverage exercises the P13-43 stdlib modules without widening unsafe APIs", [
      token(files.p13StdlibTest, "const crypto = require(\"crypto\")"),
      token(files.p13StdlibTest, "const stream = require(\"stream\")"),
      token(files.p13StdlibTest, "const zlib = require(\"zlib\")"),
      token(files.p13StdlibTest, "const { Buffer, transcode } = require(\"buffer\")"),
      token(files.p13StdlibTest, "const url = require(\"url\")"),
      token(files.p13StdlibTest, "const os = require(\"os\")"),
      token(files.p13StdlibTest, "const readline = require(\"readline\")"),
      token(files.p13StdlibTest, "const tty = require(\"tty\")"),
      token(files.p13StdlibTest, "const perfHooks = require(\"perf_hooks\")"),
      token(files.p13StdlibTest, "const asyncHooks = require(\"async_hooks\")"),
      token(files.p13StdlibTest, "process.getBuiltinModule(\"crypto\")"),
      token(files.p13StdlibTest, "process.getBuiltinModule(\"left-pad\") === undefined"),
      token(files.p13StdlibTest, "process.getBuiltinModule(\"dgram\")"),
      token(files.p13StdlibTest, "requireDenied(\"zlib/promises\")"),
      token(files.p13StdlibTest, "p13stdlib.modules=true|true|true|true|true|true|true|true|true|true"),
      token(files.coreBuiltinTest, "lowRiskCoreBuiltinsExposeAliasesShadowsAndDenials"),
      token(files.coreBuiltinTest, "asyncLocalStoragePropagatesAcrossSafeAsyncBoundariesAndCleansUp"),
      token(files.coreBuiltinTest, "perfHooksAndDiagnosticsChannelExposeSafeRuntimeTelemetry"),
      token(files.coreBuiltinTest, "promiseAndWebPlatformSweepMatchesNode24SupportedSubset"),
    ], [
      "Full desktop stdlib parity is not claimed; P13-43 records representative Safe subset conformance only.",
      "More module-by-module hostile inputs, Android terminal/OS edge cases, and packaged lifecycle evidence remain future work.",
    ]),
    scenario("matrix_corpus_and_differential", "Builtin matrix, real npm corpus, and modern-core differential keep stdlib evidence connected to package compatibility", [
      token(files.runtimeCapabilities, "val limitedBuiltinModuleNames = listOf("),
      ...P13_MODULES.map((name) => token(files.runtimeCapabilities, `"${name}"`)),
      token(files.matrixGenerator, "P13-43 records stdlib conformance as partial"),
      token(files.desktopMatrix, "P13-43 records stdlib conformance as partial"),
      token(files.realNpmCompatTest, "private const val EXPECTED_FIXTURE_COUNT = 50"),
      token(files.realNpmCompatTest, "private const val PHASE9_FIXTURE_COUNT = 30"),
      token(files.realNpmManifest, '"crypto hash utility"'),
      token(files.realNpmManifest, '"stream utility"'),
      token(files.realNpmManifest, '"zlib buffer utility"'),
      token(files.realNpmManifest, '"URL utility"'),
      token(files.realNpmManifest, '"AsyncLocalStorage"'),
      token(files.realNpmManifest, '"WebCrypto"'),
      token(files.modernCoreTest, "modernCoreDifferentialCorpusPassesAndEmitsAutoJsResults"),
      token(files.modernCoreManifest, '"area": "AsyncLocalStorage"'),
      token(files.modernCoreManifest, '"area": "perf_hooks"'),
      token(files.modernCoreManifest, '"area": "stream/promises"'),
      token(files.modernCoreManifest, '"area": "WebCrypto"'),
    ], [
      "Real npm corpus growth is not increased in P13-43; existing Phase 9 baseline remains the evidence floor.",
      "A future corpus expansion still needs additional stdlib-dependent packages and pass-rate deltas.",
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-43", [
      token(files.phase13Tasks, "P13-43 partial landing"),
      token(files.p13Report, "P13-43 records Node stdlib conformance as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13StdlibConformanceDecision"),
      token(files.testingDoc, "verifyNodePhase13StdlibConformanceDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13StdlibConformanceDecision"),
      token(files.compatibilityProfile, "P13-43 records Node stdlib conformance as partial"),
      token(files.securityModel, "P13-43 records Node stdlib conformance as partial"),
      token(files.proComparison, "P13-43 records Node stdlib conformance as partial"),
      token(files.proAutoxNotes, "P13-43 records Node stdlib conformance as partial"),
      token(files.typesReadme, "P13-43 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-43 stdlib conformance"),
    ], [
      "P13-43 remains partial until every stdlib module has broader conformance, Android differences, and package-corpus deltas closed.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-43"), "P13-43 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-43 report must exist."),
    check("surface-present", Boolean(modulesSurface), "`desktop.modules_npm_process` surface must exist."),
    check(
      "surface-evidence",
      modulesSurface && (modulesSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.modules_npm_process` surface must reference P13-43 evidence.",
    ),
    check(
      "profile-modules-covered",
      P13_MODULES.every((name) => stdlibProfile.includes(`"${name}"`) || stdlibProfile.includes(`${name}: "partial"`)),
      "stdlibProfile must list every P13-43 representative module.",
    ),
    check(
      "profile-fail-closed-boundaries",
      stdlibProfile.includes('processGetBuiltinModule: "controlled_partial"') &&
        stdlibProfile.includes('"zlib/promises": "disabled"') &&
        stdlibProfile.includes("rawDesktopParity: false") &&
        stdlibProfile.includes("rawNativeHandles: false") &&
        stdlibProfile.includes("unrestrictedNetwork: false") &&
        stdlibProfile.includes("broadTerminal: false"),
      "stdlibProfile must keep raw desktop parity, native handles, unrestricted network, broad terminal, raw process.getBuiltinModule bypass behavior, and zlib/promises fail-closed.",
    ),
    check(
      "no-unsafe-stdlib-promotion",
      !files.nodeBridgeSources.text.includes("rawDesktopParity: true") &&
        !files.nodeBridgeSources.text.includes("rawNativeHandles: true") &&
        !files.nodeBridgeSources.text.includes("unrestrictedNetwork: true") &&
        !files.nodeBridgeSources.text.includes('"zlib/promises": "partial"'),
      "P13-43 must not mark full desktop stdlib parity, raw native handles, unrestricted network, or zlib/promises as promoted.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-stdlib-conformance-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13StdlibConformanceDecision",
    task: "P13-43",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: modulesSurface || {
      id: "desktop.modules_npm_process",
      status: "missing",
      evidence: [],
    },
    modules: P13_MODULES,
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile").status === "ready",
      representativeModulesReady: scenarioById(scenarios, "representative_module_conformance").status !== "missing_evidence",
      matrixCorpusDifferentialReady: scenarioById(scenarios, "matrix_corpus_and_differential").status !== "missing_evidence",
      rawDesktopParityPromoted: false,
      processGetBuiltinModuleControlledPartial: true,
      zlibPromisesPromoted: false,
      npmCorpusExpandedInP13_43: false,
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

function block(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  if (start < 0) return "";
  const end = text.indexOf(endToken, start);
  if (end < 0) return text.slice(start);
  return text.slice(start, end + endToken.length);
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Stdlib Conformance Decision",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Modules",
    "",
    report.modules.map((name) => `\`${name}\``).join(", "),
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
      console.log("Usage: node verify-stdlib-conformance-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
