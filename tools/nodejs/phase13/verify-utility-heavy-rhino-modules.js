#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-utility-heavy-rhino-modules";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_27_UTILITY_HEAVY_RHINO_MODULES.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  utilityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeUtilityBridgeInstrumentationTest.kt",
  jsoxTypes: "docs/nodejs/types/autojs6-node/jsox.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  rhinoMatrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  gradleGate: "app/node-phase13-gate.gradle.kts",
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
  console.log("Phase 13 utility-heavy Rhino modules: " + report.decision.status);
  console.log("Phase 13 utility-heavy Rhino modules blockers: " + report.blockers.length);
  console.log("Phase 13 utility-heavy Rhino modules policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.tasks_app_shell_media_utils");
  const status = parseJson(files.statusManifest.text, {});
  const jsoxMathxStatus = moduleStatus(status, "jsox.mathx");
  const jsoxStatus = moduleStatus(status, "jsox");
  const scenarios = [
    scenario("pure_jsox_mathx_runtime", "jsox and jsox.mathx expose the pure Mathx facade without Android providers", [
      token(files.nodeBridgeSources, "let __autojs6_limited_jsox_mathx_cache = null"),
      token(files.nodeBridgeSources, "let __autojs6_limited_jsox_cache = null"),
      token(files.nodeBridgeSources, "function __autojs6_limited_jsox_mathx()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_jsox()"),
      token(files.nodeBridgeSources, 'if (name === "jsox")'),
      token(files.nodeBridgeSources, 'if (name === "jsox.mathx")'),
      token(files.nodeBridgeSources, "jsox: __autojs6_limited_jsox()"),
      token(files.nodeBridgeSources, "default global Math/prototype mutation is disabled"),
      token(files.nodeBridgeSources, 'nodeModules: Object.freeze(["jsox", "jsox.mathx"])'),
      token(files.nodeBridgeSources, '"P13-27"'),
    ], [
      "Only Mathx is promoted; Arrayx and Numberx still need separate pure helper gates.",
      "Raw Android/OpenCV point/rect parity for Mathx.dist remains out of this gate.",
    ]),
    scenario("types_and_instrumentation", "types, conformance, and focused instrumentation cover jsox", [
      token(files.jsoxTypes, 'declare module "jsox"'),
      token(files.jsoxTypes, 'declare module "jsox.mathx"'),
      token(files.jsoxTypes, "export interface MathxModule"),
      token(files.jsoxTypes, "export interface JsoxModule"),
      token(files.indexTypes, '/// <reference path="./jsox.d.ts" />'),
      token(files.rhinoCompatTypes, 'import jsox = require("jsox")'),
      token(files.rhinoCompatTypes, "readonly jsox: jsox.JsoxModule"),
      token(files.typeSmoke, 'import jsox = require("jsox")'),
      token(files.typeSmoke, "const compatMathxMedian: number = rhinoCompat.jsox.mathx.median"),
      token(files.conformanceTest, 'expectExports("jsox.mathx", jsoxMathx'),
      token(files.conformanceTest, 'expectExports("autojs6:compat.jsox.mathx", compat.jsox.mathx'),
      token(files.utilityTest, "class NodeUtilityBridgeInstrumentationTest"),
      token(files.utilityTest, "jsJsoxMathxPureFacadeBlocksShadowingAndDefaultGlobalMutation"),
      token(files.utilityTest, "jsox.values=6|2.5|2.5|2.5|0.666666666667"),
      token(files.utilityTest, "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"),
    ], [
      "Packaged APK resource smoke remains open because this batch is provider-free and resource-free.",
    ]),
    scenario("docs_manifest_and_gate", "docs, status manifest, surface manifest, and Gradle gate record P13-27", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-27 partial utility-heavy Rhino modules gate"),
      token(files.surfaceManifestDoc, "phase13-utility-heavy-rhino-modules"),
      token(files.gradleGate, "verifyNodePhase13UtilityHeavyRhinoModules"),
      token(files.testingDoc, "verifyNodePhase13UtilityHeavyRhinoModules"),
      token(files.ciGatesDoc, "verifyNodePhase13UtilityHeavyRhinoModules"),
      token(files.compatibilityProfile, "P13-27 records utility-heavy Rhino modules promotion as partial"),
      token(files.securityModel, "P13-27 records utility-heavy Rhino modules promotion as partial"),
      token(files.proComparison, "P13-27 records utility-heavy Rhino modules promotion as partial"),
      token(files.proAutoxNotes, "P13-27 records utility-heavy Rhino modules promotion as partial"),
      token(files.capabilityMatrix, "P13-27 utility-heavy Rhino modules"),
      token(files.typesReadme, "P13-27 records the partial utility-heavy Rhino modules gate"),
      token(files.rhinoMatrix, "P13-27 promotes the pure Mathx facade subset"),
      token(files.statusManifest, '"decision": "p13_27_pure_js_mathx_facade"'),
    ], [
      "P13-27 remains open until resource-heavy and raw-object utility modules receive dedicated gates.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-27"), "P13-27 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-27 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must remain partial/poc after P13-27 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-27 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-27 evidence.",
    ),
    check(
      "status-manifest-partial",
      jsoxMathxStatus && jsoxMathxStatus.status === "partial" && jsoxStatus && jsoxStatus.status === "partial",
      "`jsox.mathx` and `jsox` must be partial in the Rhino status manifest.",
    ),
    check(
      "no-resource-heavy-runtime-promotion",
      !files.nodeBridgeSources.text.includes('if (name === "zip")') &&
        !files.nodeBridgeSources.text.includes('if (name === "canvas")') &&
        !files.nodeBridgeSources.text.includes('if (name === "sysprops")') &&
        !files.nodeBridgeSources.text.includes('if (name === "jsox.arrayx")') &&
        !files.nodeBridgeSources.text.includes('if (name === "jsox.numberx")'),
      "P13-27 must not expose zip, canvas, sysprops, jsox.arrayx, or jsox.numberx before dedicated gates land.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-utility-heavy-rhino-modules-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13UtilityHeavyRhinoModules",
    task: "P13-27",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.tasks_app_shell_media_utils",
      status: "missing",
      evidence: [],
    },
    statusManifest: {
      jsox: jsoxStatus ? jsoxStatus.status : "missing",
      jsoxMathx: jsoxMathxStatus ? jsoxMathxStatus.status : "missing",
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      jsoxMathxReady: scenarioById(scenarios, "pure_jsox_mathx_runtime").status !== "missing_evidence",
      resourceBackedUtilitiesReady: false,
      rawAndroidUtilityParityReady: false,
      proSurfacePromoted: false,
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

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function parseJson(text, fallback) {
  try {
    return JSON.parse(text);
  } catch (_) {
    return fallback;
  }
}

function moduleStatus(manifest, id) {
  return (manifest.modules || []).find((entry) => entry.id === id);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Phase 13 Utility-Heavy Rhino Modules Verification");
  lines.push("");
  lines.push(`- Gate: \`${report.gate}\``);
  lines.push(`- Task: \`${report.task}\``);
  lines.push(`- Decision: \`${report.decision.status}\``);
  lines.push(`- Surface status: \`${report.surface.status}\``);
  lines.push(`- Policy violations: ${report.policyViolations.length}`);
  lines.push(`- Blockers: ${report.blockers.length}`);
  lines.push("");
  lines.push("## Scenarios");
  report.scenarios.forEach((scenario) => {
    lines.push("");
    lines.push(`### ${scenario.id}`);
    lines.push("");
    lines.push(`Status: \`${scenario.status}\``);
    scenario.checks.forEach((entry) => {
      lines.push(`- ${entry.present ? "[x]" : "[ ]"} \`${entry.file}\` contains \`${entry.token}\``);
    });
    if (scenario.remaining.length > 0) {
      lines.push("");
      lines.push("Remaining:");
      scenario.remaining.forEach((item) => lines.push(`- ${item}`));
    }
  });
  if (report.policyViolations.length > 0) {
    lines.push("");
    lines.push("## Policy Violations");
    report.policyViolations.forEach((item) => lines.push(`- ${item}`));
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
    if (arg === "--repo-root") options.repoRoot = args[++index];
    else if (arg === "--report-dir") options.reportDir = args[++index];
    else if (arg === "--fail-on-blocked") options.failOnBlocked = true;
  }
  return options;
}

if (require.main === module) {
  main();
}
