#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-esm-loader-parity-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_41_ESM_LOADER_PARITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13EsmLoaderTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEsmLoaderPhase13DecisionInstrumentationTest.kt",
  esmExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEsmExperimentalInstrumentationTest.kt",
  sourceMapTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSourceMapStackTraceInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  designEsm: "docs/nodejs/design/ESM.md",
  esmEvaluation: "docs/nodejs/reports/ESM_EXPERIMENTAL_EVALUATION.md",
  v11EsmPromotion: "docs/nodejs/releases/V1_1_ESM_PROMOTION.md",
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
  console.log("Phase 13 ESM loader parity decision: " + report.decision.status);
  console.log("Phase 13 ESM loader blockers: " + report.blockers.length);
  console.log("Phase 13 ESM loader policy violations: " + report.policyViolations.length);
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
  const esmLoaderProfile = block(files.nodeBridgeSources.text, "esmLoaderProfile: Object.freeze({", "      }),");
  const scenarios = [
    scenario("diagnostic_profile", "esmLoaderProfile records the current partial default and unpromoted loader boundaries", [
      token(files.nodeBridgeSources, "esmLoaderProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: __autojs6_esm_experimental_enabled ? "partial_default" : "disabled_by_request"'),
      token(files.nodeBridgeSources, "defaultEnabled: true"),
      token(files.nodeBridgeSources, "requestEnabled: __autojs6_esm_experimental_enabled"),
      token(files.nodeBridgeSources, "dynamicImportEnabled: __autojs6_dynamic_import_experimental_enabled"),
      token(files.nodeBridgeSources, 'implementation: "autojs6_managed_transform"'),
      token(files.nodeBridgeSources, 'packageExports: "partial"'),
      token(files.nodeBridgeSources, 'packageImports: "partial"'),
      token(files.nodeBridgeSources, 'loaderHooks: "denied"'),
      token(files.nodeBridgeSources, 'networkImports: "denied"'),
      token(files.nodeBridgeSources, 'fileUrlImports: "denied"'),
      token(files.nodeBridgeSources, 'dataUrlImports: "inline_js_json_partial"'),
      token(files.nodeBridgeSources, 'rawNodeLoader: false'),
      token(files.p13EsmLoaderTest, "esmLoaderProfileRecordsPartialParityAndDeniedBoundaries"),
      token(files.p13EsmLoaderTest, "inline_js_json_partial"),
      token(files.p13EsmLoaderTest, "p13esm.dynamic=dep|true|true|inline-data"),
      token(files.typesAutojsGlobal, "export interface EsmLoaderProfile"),
      token(files.typesAutojsGlobal, "readonly esmLoaderProfile: EsmLoaderProfile"),
      token(files.typescriptSmoke, "autojsProfile.esmLoaderProfile.dynamicImport"),
    ], []),
    scenario("local_loader_parity", "Local ESM parity has focused device coverage for entries, imports, package conditions, interop, JSON attributes, and TLA", [
      token(files.p13EsmLoaderTest, "p13esm.entry=dep|alias-esm|pkg-esm|json-attr|legacy-cjs|tla|true|true"),
      token(files.esmExperimentalTest, "partialMjsEntrySupportsLocalStaticImportAndTopLevelAwaitByDefault"),
      token(files.esmExperimentalTest, "partialPackageTypeModuleEntryRunsAsEsmByDefault"),
      token(files.esmExperimentalTest, "experimentalCommonJsRequireMjsSupportsSyncGraphAndRejectsAsyncGraph"),
      token(files.esmExperimentalTest, "experimentalEsmJsonImportAttributesUseCommonJsJsonCache"),
      token(files.esmExperimentalTest, "experimentalImportMetaExposesScopedMetadataAndResolve"),
      token(files.esmExperimentalTest, "experimentalPackageExportsConditionsSplitByImporterMode"),
      token(files.esmExperimentalTest, "experimentalPackageImportsConditionsSplitByImporterMode"),
      token(files.esmExperimentalTest, "experimentalPackageAutojs6ConditionPreservesPackageKeyOrderAndPolicy"),
      token(files.esmExperimentalTest, "experimentalNestedPackageImportAndSelfReferenceUseEsmConditions"),
      token(files.designEsm, "ESM is partial and enabled by default for Node Profile v1.1"),
    ], []),
    scenario("controlled_data_url_and_denied_boundaries", "Controlled JS/JSON data URL imports are partial while raw loader hooks, network/file URL imports, path escapes, disabled builtins, and native addon targets stay denied", [
      token(files.p13EsmLoaderTest, "p13esm.dynamic=dep|true|true"),
      token(files.esmExperimentalTest, "partialEsmSupportsControlledDataUrlImports"),
      token(files.esmExperimentalTest, "esm.dataUrl=inline-js|named|b64|inline-json|dynamic-js|dynamic-json|true|true|true"),
      token(files.esmExperimentalTest, "localDynamicImportRejectsUrlAndAbsoluteSpecifiers"),
      token(files.esmExperimentalTest, "localDynamicImportCannotEscapeWorkingDirectory"),
      token(files.esmExperimentalTest, "experimentalPackageNullBlockingAndUnsafeTargetsFail"),
      token(files.securityRegressionTest, "dynamic import network URL"),
      token(files.designEsm, "custom loaders and `node:module` loader hooks"),
      token(files.designEsm, "controlled `data:` URL imports"),
      token(files.designEsm, "imports outside `workingDirectory`"),
      token(files.sourceMapTest, "externalSourceMapOutsideWorkingDirectoryIsDenied"),
    ], [
      "Native Node ESM loader hooks and raw node:module loader authority remain denied.",
      "Network, file:, and unrestricted URL imports remain denied.",
      "Custom condition sets, import maps, and raw loader registration remain unimplemented.",
    ]),
    scenario("packaged_and_stack_evidence", "Packaged ESM and stack/source evidence are present but still scoped to focused gates", [
      token(files.packagedSmokeTest, "installAndRunExperimentalEsmEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "installAndRunDynamicImportLocalEmbeddedNodePackagedApk"),
      token(files.testingDoc, "verifyNodePackagedEsmGate"),
      token(files.testingDoc, "NodeSourceMapStackTraceInstrumentationTest"),
      token(files.v11EsmPromotion, "Promote the controlled ESM subset to Node Profile v1.1 partial support"),
      token(files.esmEvaluation, "service cancellation/destroy behavior while ESM linking or evaluation is pending"),
    ], [
      "Packaged encrypted package exports/imports graphs and broader dynamic-import package graphs remain design-gated.",
      "Pending top-level-await/linking cancellation and service-destroy cleanup need a dedicated stress gate.",
      "ESM source-map parity is not yet equivalent to full desktop DevTools/source-map behavior.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, surface manifest, type docs, and Gradle gate record P13-41", [
      token(files.phase13Tasks, "P13-41 partial landing"),
      token(files.p13Report, "P13-41 records ESM loader parity as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13EsmLoaderParityDecision"),
      token(files.testingDoc, "verifyNodePhase13EsmLoaderParityDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13EsmLoaderParityDecision"),
      token(files.compatibilityProfile, "P13-41 records ESM loader parity as partial"),
      token(files.securityModel, "P13-41 records ESM loader parity as partial"),
      token(files.proComparison, "P13-41 records ESM loader parity as partial"),
      token(files.proAutoxNotes, "P13-41 records ESM loader parity as partial"),
      token(files.typesReadme, "P13-41 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-41 ESM loader parity"),
    ], [
      "P13-41 remains partial until raw loader hook decisions, packaged encrypted graph parity, broader ESM package corpus, and pending-evaluation cleanup evidence close.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-41"), "P13-41 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-41 report must exist."),
    check("surface-present", Boolean(modulesSurface), "`desktop.modules_npm_process` surface must exist."),
    check(
      "surface-evidence",
      modulesSurface && (modulesSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.modules_npm_process` surface must reference P13-41 evidence.",
    ),
    check(
      "esm-feature-stays-partial-default",
      files.runtimeCapabilities.text.includes('id = "esm"') &&
        files.runtimeCapabilities.text.includes("ESM_EXPERIMENTAL_DEFAULT = true") &&
        files.runtimeCapabilities.text.includes('status = "partial"') &&
        files.runtimeCapabilities.text.includes('id = "dynamic_import"') &&
        files.runtimeCapabilities.text.includes("DYNAMIC_IMPORT_LOCAL_DEFAULT = true"),
      "ESM and local dynamic import feature flags must stay partial default-on, not unrestricted desktop loader parity.",
    ),
    check(
      "profile-fail-closed-boundaries",
      esmLoaderProfile.includes('loaderHooks: "denied"') &&
        esmLoaderProfile.includes('networkImports: "denied"') &&
        esmLoaderProfile.includes('fileUrlImports: "denied"') &&
        esmLoaderProfile.includes('dataUrlImports: "inline_js_json_partial"') &&
        esmLoaderProfile.includes('workingDirectoryEscape: "denied"') &&
        esmLoaderProfile.includes("rawNodeLoader: false") &&
        esmLoaderProfile.includes("customConditions: false"),
      "esmLoaderProfile must keep raw loader, network/file/unrestricted URL imports, escapes, and custom conditions fail-closed while recording controlled data URL import parity.",
    ),
    check(
      "no-raw-loader-promotion",
      !files.nodeBridgeSources.text.includes("initializeImportMetaObject") &&
        !files.nodeBridgeSources.text.includes("resolveForImportMeta") &&
        !files.nodeBridgeSources.text.includes("registerLoader(") &&
        !files.nodeBridgeSources.text.includes("esmLoaderProfile.loaderHooks = \"partial\"") &&
        !files.nodeBridgeSources.text.includes("rawNodeLoader: true"),
      "P13-41 must not introduce raw Node loader hooks or mark raw loader authority enabled.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-esm-loader-parity-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13EsmLoaderParityDecision",
    task: "P13-41",
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
      localLoaderParityReady: scenarioById(scenarios, "local_loader_parity").status === "ready",
      controlledDataUrlAndDeniedBoundariesReady: scenarioById(scenarios, "controlled_data_url_and_denied_boundaries").status !== "missing_evidence",
      packagedAndStackEvidenceReady: scenarioById(scenarios, "packaged_and_stack_evidence").status !== "missing_evidence",
      rawLoaderHooksIntroduced: false,
      networkImportsPromoted: false,
      packagedEncryptedGraphPromoted: false,
      desktopFullEsmParityPromoted: false,
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
    "# Phase 13 ESM Loader Parity Decision",
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
      console.log("Usage: node verify-esm-loader-parity-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
