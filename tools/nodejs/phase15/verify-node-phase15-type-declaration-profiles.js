#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-type-declaration-profiles";
const SOURCE_REPORTS = Object.freeze([
  ["P14-35", "phase14-type-declaration-profiles.json", "ready"],
  ["P15-11", "phase15-ui-automation-media-doc-sync.json", "ready"],
  ["P15-22", "phase15-task-device-media-doc-sync.json", "ready"],
  ["P15-23", "phase15-security-regression-corpus.json", "ready"],
  ["P15-24", "phase15-packaged-aggregate-smoke.json", "ready"],
]);
const REQUIRED_TYPE_HELPERS = Object.freeze([
  "NodeProfileV13CapabilityCatalog",
  "NodeProfileV13CapabilitySpec",
  "NodeProfileV13ModeFor",
  "NodeProfileV13StateFor",
  "NodeProfileV13PackagedEvidenceFor",
  "NodeProfileV13FutureOnlyFor",
  "NodeProfileV13StableDeniedCapabilityName",
  "NodeProfileV13DeclarationMatrix",
]);
const REQUIRED_CAPABILITY_ROWS = Object.freeze([
  "ui.live",
  "ui.overlay",
  "accessibility",
  "media_projection",
  "image.advanced",
  "ocr",
  "tasks.database",
  "tasks.schema",
  "work_manager",
  "tasks.broadcast",
  "notifications",
  "device.power",
  "recorder.session",
  "media.playback",
  "mediastore",
  "docs.types.release_sync",
  "security.regression",
  "packaged.aggregate",
]);
const REQUIRED_SMOKE_TOKENS = Object.freeze([
  'NodeProfileV13ModeFor<"ui.overlay", "pro_compat_opt_in">',
  'NodeProfileV13ModeFor<"media_projection", "pro_compat_opt_in">',
  'NodeProfileV13ModeFor<"work_manager", "pro_compat_opt_in">',
  'NodeProfileV13ModeFor<"media.playback", "pro_compat_opt_in">',
  'NodeProfileV13PackagedEvidenceFor<"packaged.aggregate">',
  'NodeProfileV13PackagedEvidenceFor<"security.regression">',
  'NodeProfileV13FutureOnlyFor<"recorder.session">',
  "NodeProfileV13StableDeniedCapabilityName",
  "NodeProfileV13DeclarationMatrix",
]);
const FUTURE_ONLY_TOKENS = Object.freeze([
  "recorder.start",
  "rhinoCompat.tasks.addIntentTask",
  'NodeProfileV13ModeFor<"media.playback", "pro_compat_opt_in"> = "denied"',
  "NodeProfileV13StableDeniedCapabilityName",
]);

const FILES = {
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Report: "docs/nodejs/reports/phase15/P15_25_TYPE_DECLARATION_PROFILES.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  planJson: "docs/nodejs/phase15/node-v1-3-type-declaration-profiles.json",
  planDoc: "docs/nodejs/phase15/PHASE15_NODE_V1_3_TYPE_DECLARATION_PROFILES.md",
  typesIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  typesProfileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typesPackage: "docs/nodejs/types/autojs6-node/package.json",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  typescriptSmokeTsconfig: "sample/nodejs/typescript-smoke/tsconfig.json",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  gradle15: "app/node-phase15-gate.gradle.kts",
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
  console.log("Phase 15 type declaration profiles: " + report.decision.status);
  console.log("Phase 15 type declaration profile blockers: " + report.blockers.length);
  console.log("Phase 15 type declaration profile policy violations: " + report.policyViolations.length);
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
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-25") || {};
  const generatedReports = SOURCE_REPORTS.map(([task, , expectedStatus]) => {
    const parsed = parseJson(files[task].text);
    const decision = parsed.decision || {};
    return {
      task,
      exists: files[task].exists,
      status: statusOf(parsed),
      expectedStatus,
      policyViolations: Array.isArray(parsed.policyViolations) ? parsed.policyViolations.length : 999,
      releaseBlockingBlockers: Number(decision.releaseBlockingBlockers || 0),
      runtimeAuthorityChanged: decision.runtimeAuthorityChanged,
    };
  });
  const scenarios = [
    scenario("plan_and_source_reports", "P15-25 imports v1.2 type profiles plus v1.3 docs, security, and packaged source gates", [
      value("planSchema", plan.schema === "autojs6-node-phase15-type-declaration-profiles-plan-v1"),
      value("planTask", plan.task === "P15-25"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "type_declaration_profiles_v1_3_ready"),
      value("gradleTask", plan.gradleTask === ":app:verifyNodePhase15TypeDeclarationProfiles"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("sourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      ...generatedReports.map((entry) =>
        value(`generated:${entry.task}`, entry.exists && entry.status === entry.expectedStatus),
      ),
      ...generatedReports.map((entry) =>
        value(`policy:${entry.task}`, entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      ),
    ]),
    scenario("v13_declaration_matrix", "profile_capabilities.d.ts exposes v1.3 profile helpers and capability rows", [
      token(files.typesProfileCapabilities, "autojs6-node-profile-v1-3-declaration-matrix-v1"),
      token(files.typesProfileCapabilities, 'export type NodeProfileVersionId = "v1.1" | "v1.2" | "v1.3"'),
      ...REQUIRED_TYPE_HELPERS.map((entry) => token(files.typesProfileCapabilities, entry)),
      ...REQUIRED_CAPABILITY_ROWS.map((entry) => token(files.typesProfileCapabilities, `"${entry}"`)),
      token(files.typesProfileCapabilities, "readonly metadataOnly: true"),
      token(files.typesProfileCapabilities, "readonly grantsAuthority: false"),
      token(files.typesProfileCapabilities, "security_corpus_ready_no_authority"),
      token(files.typesProfileCapabilities, "host_contract_ready"),
      token(files.typesIndex, "profile_capabilities.d.ts"),
    ]),
    scenario("typescript_smoke_v13_coverage", "TypeScript smoke covers v1.3 provider POC, docs, security, packaged, and stable-denied rows", [
      ...REQUIRED_SMOKE_TOKENS.map((entry) => token(files.typescriptSmoke, entry)),
      ...FUTURE_ONLY_TOKENS.map((entry) => token(files.typescriptSmoke, entry)),
      token(files.typescriptSmoke, "NodeProfileV13StateFor<\"security.regression\">"),
      token(files.typescriptSmoke, "metadataOnly: true"),
      token(files.typescriptSmoke, "grantsAuthority: false"),
    ]),
    scenario("docs_and_gate_wiring", "Docs, manifest, testing, CI, and Gradle expose P15-25", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "security.types.packaged_migration"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15TypeDeclarationProfiles"),
      value("surface_mapping_status", p15Mapping.status === "ready"),
      value("surface_mapping_risk", p15Mapping.risk === "medium"),
      token(files.phase15Tasks, "- [x] P15-25: Update TypeScript declaration authority profile for v1.3."),
      token(files.phase15Tasks, "Type declaration profile gate deployed:"),
      token(files.phase15Report, "P15-25 decision: `type_declaration_profiles_v1_3_ready`"),
      token(files.phase15Readme, ":app:verifyNodePhase15TypeDeclarationProfiles"),
      token(files.typesReadme, "P15-25 adds the v1.3 `NodeProfileV13CapabilityCatalog`"),
      token(files.testing, ":app:verifyNodePhase15TypeDeclarationProfiles"),
      token(files.ciGates, ":app:verifyNodePhase15TypeDeclarationProfiles"),
      token(files.securityModel, "P15-25 updates the TypeScript declaration profile matrix"),
      token(files.errorCatalog, "P15-25 type declaration profiles"),
      token(files.gradle15, "verifyNodePhase15TypeDeclarationProfiles"),
      token(files.gradle15, "verify-node-phase15-type-declaration-profiles.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase15-type-declaration-profiles-plan-v1", "P15-25 plan JSON must exist."),
    check("all-source-reports-present", generatedReports.every((entry) => entry.exists), "P15-25 source reports must exist."),
    check("required-type-helpers-present", REQUIRED_TYPE_HELPERS.every((entry) => files.typesProfileCapabilities.text.includes(entry)), "P15-25 v1.3 type helpers must be declared."),
    check("required-capability-rows-present", REQUIRED_CAPABILITY_ROWS.every((entry) => files.typesProfileCapabilities.text.includes(`"${entry}"`)), "P15-25 v1.3 capability rows must be declared."),
    check("type-smoke-covers-v13", REQUIRED_SMOKE_TOKENS.every((entry) => files.typescriptSmoke.text.includes(entry)), "TypeScript smoke must cover v1.3 profile helpers."),
    check(
      "metadata-only-no-authority",
      files.typesProfileCapabilities.text.includes("readonly metadataOnly: true") &&
        files.typesProfileCapabilities.text.includes("readonly grantsAuthority: false") &&
        files.typescriptSmoke.text.includes("metadataOnly: true") &&
        files.typescriptSmoke.text.includes("grantsAuthority: false"),
      "P15-25 declarations must stay metadata-only and no-authority.",
    ),
    check(
      "future-only-and-stable-denied-visible",
      files.typesProfileCapabilities.text.includes("NodeProfileV13FutureOnlyCapabilityName") &&
        files.typescriptSmoke.text.includes("NodeProfileV13FutureOnlyFor<\"recorder.session\">") &&
        files.typescriptSmoke.text.includes("NodeProfileV13ModeFor<\"media.playback\", \"pro_compat_opt_in\"> = \"denied\"") &&
        files.typescriptSmoke.text.includes("NodeProfileV13StableDeniedCapabilityName"),
      "Future-only and stable-denied methods must remain visibly gated in declarations and smoke.",
    ),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P15-25 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-type-declaration-profiles-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P15-25",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    capabilityRows: REQUIRED_CAPABILITY_ROWS,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      typeDeclarationDecision: "type_declaration_profiles_v1_3_ready",
      p15TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      v13DeclarationMatrixReady: scenarioById(scenarios, "v13_declaration_matrix").status === "ready",
      typeSmokeCoversV13Profiles: scenarioById(scenarios, "typescript_smoke_v13_coverage").status === "ready",
      futureOnlyAndStableDeniedMethodsVisiblyGated: checkById(checks, "future-only-and-stable-denied-visible").passed,
      runtimeAuthorityChanged: false,
      proSurfacePromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: 0,
      nextMigrationGate: "P15-26",
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Type Declaration Profiles",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Type declaration decision: \`${report.decision.typeDeclarationDecision}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- v1.3 declaration matrix ready: \`${report.decision.v13DeclarationMatrixReady}\``,
    `- Type smoke covers v1.3 profiles: \`${report.decision.typeSmokeCoversV13Profiles}\``,
    `- Future-only and stable-denied methods visibly gated: \`${report.decision.futureOnlyAndStableDeniedMethodsVisiblyGated}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Next migration gate: \`${report.decision.nextMigrationGate}\``,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(missing)} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- ${entry}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return { id, description, status: missing.length === 0 ? "ready" : "missing_evidence", checks, missing };
}

function token(file, tokenValue) {
  return {
    file: file.relative,
    token: tokenValue,
    present: file.exists && file.text.includes(tokenValue),
  };
}

function value(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
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

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\r?\n/g, "<br>");
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
      console.log("Usage: node verify-node-phase15-type-declaration-profiles.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
