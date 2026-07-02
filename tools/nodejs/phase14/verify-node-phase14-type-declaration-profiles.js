#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-type-declaration-profiles";
const SOURCE_REPORTS = Object.freeze([
  ["P13-55", "phase13-type-declaration-profiles.json"],
  ["P14-16", "phase14-ui-automation-media-doc-sync.json"],
  ["P14-27", "phase14-task-device-media-doc-sync.json"],
  ["P14-32", "phase14-desktop-runtime-doc-sync.json"],
  ["P14-33", "phase14-security-regression-corpus.json"],
  ["P14-34", "phase14-packaged-aggregate-smoke.json"],
]);
const REQUIRED_TYPE_HELPERS = Object.freeze([
  "NodeProfileVersionId",
  "NodeProfileV12CapabilityCatalog",
  "NodeProfileV12CapabilitySpec",
  "NodeProfileV12ModeFor",
  "NodeProfileV12StateFor",
  "NodeProfileV12PackagedEvidenceFor",
  "NodeProfileV12FutureOnlyFor",
  "NodeProfileV12StableDeniedCapabilityName",
  "NodeProfileV12DeclarationMatrix",
]);
const REQUIRED_CAPABILITY_ROWS = Object.freeze([
  "ui.live",
  "ui.overlay",
  "accessibility",
  "media_projection",
  "image.advanced",
  "ocr",
  "tasks.database",
  "work_manager",
  "tasks.broadcast",
  "notifications",
  "device.power",
  "recorder.session",
  "media.playback",
  "mediastore",
  "worker_threads",
  "process_worker",
  "packaged.aggregate",
]);
const REQUIRED_SMOKE_TOKENS = Object.freeze([
  'NodeProfileV12ModeFor<"ui.overlay", "pro_compat_opt_in">',
  'NodeProfileV12ModeFor<"media_projection", "pro_compat_opt_in">',
  'NodeProfileV12ModeFor<"work_manager", "pro_compat_opt_in">',
  'NodeProfileV12ModeFor<"worker_threads", "desktop_compat_opt_in">',
  'NodeProfileV12PackagedEvidenceFor<"packaged.aggregate">',
  'NodeProfileV12FutureOnlyFor<"recorder.session">',
  "NodeProfileV12StableDeniedCapabilityName",
  "NodeProfileV12DeclarationMatrix",
]);
const PROMOTED_API_TOKENS = Object.freeze([
  "overlay.show",
  "accessibility.findOne",
  "mediaProjection.requestScreenCapture",
  "image.matchTemplate",
  "workManager.scheduleOnce",
  "notifications.notify",
  "device.openBatteryOptimizationSettings",
  "recorder.getStatus",
]);
const FUTURE_ONLY_TOKENS = Object.freeze([
  "recorder.start",
  "rhinoCompat.tasks.addIntentTask",
  "NodeProfileV12ModeFor<\"worker_threads\", \"desktop_compat_opt_in\">",
  "NodeProfileV12StableDeniedCapabilityName",
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_35_TYPE_DECLARATION_PROFILES.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/node-v1-2-type-declaration-profiles.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NODE_V1_2_TYPE_DECLARATION_PROFILES.md",
  typesIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  typesProfileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typesPackage: "docs/nodejs/types/autojs6-node/package.json",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  typescriptSmokeTsconfig: "sample/nodejs/typescript-smoke/tsconfig.json",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 type declaration profiles: " + report.decision.status);
  console.log("Phase 14 type declaration profile blockers: " + report.blockers.length);
  console.log("Phase 14 type declaration profile policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([task, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      task,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const docsSurface = (manifest.surfaces || []).find((entry) => entry.id === "docs.examples.final_gate") || null;
  const generatedReports = SOURCE_REPORTS.map(([task]) => {
    const parsed = parseJson(files[task].text);
    return {
      task,
      exists: files[task].exists,
      status: statusOf(parsed),
    };
  });
  const scenarios = [
    scenario("plan_and_source_reports", "P14-35 imports the required type, docs, security, and packaged source gates", [
      value("planSchema", plan.schema === "autojs6-node-phase14-type-declaration-profiles-plan-v1"),
      value("planTask", plan.task === "P14-35"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "type_declaration_profiles_v1_2_ready"),
      value("gradleTask", plan.gradleTask === ":app:verifyNodePhase14TypeDeclarationProfiles"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("sourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      ...generatedReports.map((entry) => check(
        `generated:${entry.task}`,
        entry.exists && ["partial", "ready", "passed"].includes(entry.status),
        `${entry.task} generated report must exist with partial/ready/passed status`,
      )),
    ]),
    scenario("v12_declaration_matrix", "profile_capabilities.d.ts exposes v1.2 profile helpers and capability rows", [
      token(files.typesProfileCapabilities, "autojs6-node-profile-v1-2-declaration-matrix-v1"),
      ...REQUIRED_TYPE_HELPERS.map((entry) => token(files.typesProfileCapabilities, entry)),
      ...REQUIRED_CAPABILITY_ROWS.map((entry) => token(files.typesProfileCapabilities, `"${entry}"`)),
      token(files.typesProfileCapabilities, "readonly metadataOnly: true"),
      token(files.typesProfileCapabilities, "readonly grantsAuthority: false"),
      token(files.typesProfileCapabilities, "futureOnly"),
      token(files.typesIndex, "profile_capabilities.d.ts"),
    ]),
    scenario("typescript_smoke_v12_coverage", "TypeScript smoke covers v1.2 promoted, gated, stable-denied, and packaged rows", [
      ...REQUIRED_SMOKE_TOKENS.map((entry) => token(files.typescriptSmoke, entry)),
      ...PROMOTED_API_TOKENS.map((entry) => token(files.typescriptSmoke, entry)),
      ...FUTURE_ONLY_TOKENS.map((entry) => token(files.typescriptSmoke, entry)),
      token(files.nodeExamplesGradle, "NodeProfileV12CapabilityCatalog"),
      token(files.nodeExamplesGradle, "NodeProfileV12DeclarationMatrix"),
    ]),
    scenario("docs_and_gate_wiring", "Docs, manifest, testing, CI, and Gradle expose P14-35", [
      value("surfaceEvidence", docsSurface &&
        (docsSurface.tasks || []).includes("P14-35") &&
        (docsSurface.evidence || []).includes("docs/nodejs/phase14/node-v1-2-type-declaration-profiles.json") &&
        (docsSurface.evidence || []).includes(":app:verifyNodePhase14TypeDeclarationProfiles")),
      token(files.surfaceManifestDoc, "P14-35 adds the v1.2 TypeScript declaration profile matrix"),
      token(files.phase14Tasks, "- [x] P14-35: Update TypeScript declaration profiles for v1.2."),
      token(files.phase14Tasks, "Type declaration profile gate deployed:"),
      token(files.phase14Report, "P14-35 decision: `type_declaration_profiles_v1_2_ready`"),
      token(files.phase14Readme, ":app:verifyNodePhase14TypeDeclarationProfiles"),
      token(files.typesReadme, "P14-35 adds the v1.2 `NodeProfileV12CapabilityCatalog`"),
      token(files.testing, ":app:verifyNodePhase14TypeDeclarationProfiles"),
      token(files.ciGates, ":app:verifyNodePhase14TypeDeclarationProfiles"),
      token(files.securityModel, "P14-35 updates the TypeScript declaration profile matrix"),
      token(files.errorCatalog, "P14-35 type declaration profiles"),
      token(files.gradle14, "verifyNodePhase14TypeDeclarationProfiles"),
      token(files.gradle14, "verify-node-phase14-type-declaration-profiles.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-type-declaration-profiles-plan-v1", "P14-35 plan JSON must exist."),
    check("all-source-reports-present", generatedReports.every((entry) => entry.exists), "P14-35 source reports must exist."),
    check("required-type-helpers-present", REQUIRED_TYPE_HELPERS.every((entry) => files.typesProfileCapabilities.text.includes(entry)), "P14-35 v1.2 type helpers must be declared."),
    check("required-capability-rows-present", REQUIRED_CAPABILITY_ROWS.every((entry) => files.typesProfileCapabilities.text.includes(`"${entry}"`)), "P14-35 v1.2 capability rows must be declared."),
    check("type-smoke-covers-v12", REQUIRED_SMOKE_TOKENS.every((entry) => files.typescriptSmoke.text.includes(entry)), "TypeScript smoke must cover v1.2 profile helpers."),
    check(
      "metadata-only-no-authority",
      files.typesProfileCapabilities.text.includes("readonly metadataOnly: true") &&
        files.typesProfileCapabilities.text.includes("readonly grantsAuthority: false") &&
        files.typescriptSmoke.text.includes("metadataOnly: true") &&
        files.typescriptSmoke.text.includes("grantsAuthority: false"),
      "P14-35 declarations must stay metadata-only and no-authority.",
    ),
    check(
      "future-only-visible",
      files.typesProfileCapabilities.text.includes("NodeProfileV12FutureOnlyCapabilityName") &&
        files.typescriptSmoke.text.includes("NodeProfileV12FutureOnlyFor<\"recorder.session\">") &&
        files.typescriptSmoke.text.includes("NodeProfileV12ModeFor<\"worker_threads\", \"desktop_compat_opt_in\"> = \"denied\"") &&
        files.typescriptSmoke.text.includes("NodeProfileV12StableDeniedCapabilityName"),
      "Future-only and stable-denied methods must remain visibly gated in declarations and smoke.",
    ),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-35 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-type-declaration-profiles-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-35",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    capabilityRows: REQUIRED_CAPABILITY_ROWS,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      typeDeclarationDecision: "type_declaration_profiles_v1_2_ready",
      p14TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      v12DeclarationMatrixReady: scenarioById(scenarios, "v12_declaration_matrix").status === "ready",
      typeSmokeCoversV12Profiles: scenarioById(scenarios, "typescript_smoke_v12_coverage").status === "ready",
      futureOnlyMethodsVisiblyGated: checkById(checks, "future-only-visible").ok,
      runtimeAuthorityChanged: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Type Declaration Profiles",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Type declaration decision: \`${report.decision.typeDeclarationDecision}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- v1.2 declaration matrix ready: \`${report.decision.v12DeclarationMatrixReady}\``,
    `- Type smoke covers v1.2 profiles: \`${report.decision.typeSmokeCoversV12Profiles}\``,
    `- Future-only methods visibly gated: \`${report.decision.futureOnlyMethodsVisiblyGated}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return { id, description, status: missing.length === 0 ? "ready" : "missing_evidence", checks, missing };
}

function value(id, ok) {
  return { id, ok: Boolean(ok), message: `missing value check: ${id}` };
}

function token(file, expected) {
  return {
    id: `${file.relative}:${expected}`,
    ok: file.exists && file.text.includes(expected),
    message: `missing evidence token: ${expected} in ${file.relative}`,
  };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { ok: false };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase14-type-declaration-profiles.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
