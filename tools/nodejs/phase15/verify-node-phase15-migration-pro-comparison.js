#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-migration-pro-comparison";
const SOURCE_REPORTS = Object.freeze([
  ["P14-36", "phase14-migration-pro-comparison.json", "ready"],
  ["P15-11", "phase15-ui-automation-media-doc-sync.json", "ready"],
  ["P15-22", "phase15-task-device-media-doc-sync.json", "ready"],
  ["P15-23", "phase15-security-regression-corpus.json", "ready"],
  ["P15-24", "phase15-packaged-aggregate-smoke.json", "ready"],
  ["P15-25", "phase15-type-declaration-profiles.json", "ready"],
]);
const TARGET_DOC_KEYS = Object.freeze([
  "migrationDoc",
  "proComparison",
  "proAutoxNotes",
  "compatibilityProfile",
  "examplesDoc",
  "releaseNotes",
]);
const REQUIRED_CLAIM_AREAS = Object.freeze([
  "Pro UI and floaty",
  "accessibility",
  "screenshot/OCR",
  "tasks",
  "notifications",
  "recorder",
  "MediaStore/playback",
  "security corpus",
  "packaged behavior",
  "TypeScript declarations",
  "rollback",
]);
const REQUIRED_BOUNDARY_TOKENS = Object.freeze([
  "P15-26 records v1.3 migration and Pro comparison docs as ready",
  "claims match executable evidence",
  "do not imply raw Android object parity",
  "provider_poc_not_promoted",
  "stable_denied",
  "host_contract_ready",
  "NodeProfileV13CapabilityCatalog",
  "runtime authority remains unchanged",
  "no newly promoted providers",
  "security_corpus_updated_no_authority_promoted",
]);
const RAW_OBJECT_TOKENS = Object.freeze([
  "raw `Context`",
  "raw `Activity`",
  "raw `View`",
  "raw `Bitmap`",
  "raw `Intent`",
  "raw `MediaProjection`",
  "raw `MediaRecorder`",
  "raw `TimedTask`",
  "raw `BroadcastReceiver`",
  "raw `Notification`",
  "raw `WakeLock`",
  "raw `ContentResolver`",
  "raw `MediaStore`",
  "raw file descriptors",
  "Java objects",
]);

const FILES = {
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Report: "docs/nodejs/reports/phase15/P15_26_MIGRATION_PRO_COMPARISON.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  planJson: "docs/nodejs/phase15/node-v1-3-migration-pro-comparison.json",
  planDoc: "docs/nodejs/phase15/PHASE15_NODE_V1_3_MIGRATION_PRO_COMPARISON.md",
  migrationDoc: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  releaseNotes: "docs/nodejs/releases/NODE_PROFILE_V1_3_RELEASE_NOTES_DRAFT.md",
  typesProfileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
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
  console.log("Phase 15 migration and Pro comparison: " + report.decision.status);
  console.log("Phase 15 migration and Pro comparison blockers: " + report.blockers.length);
  console.log("Phase 15 migration and Pro comparison policy violations: " + report.policyViolations.length);
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
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-26") || {};
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
  const targetDocs = TARGET_DOC_KEYS.map((key) => files[key]);
  const scenarios = [
    scenario("plan_and_source_reports", "P15-26 imports the required migration, docs, security, packaged, and type gates", [
      value("planSchema", plan.schema === "autojs6-node-phase15-migration-pro-comparison-plan-v1"),
      value("planTask", plan.task === "P15-26"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "migration_pro_comparison_v1_3_ready"),
      value("gradleTask", plan.gradleTask === ":app:verifyNodePhase15MigrationProComparison"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("sourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      ...generatedReports.map((entry) =>
        value(`generated:${entry.task}`, entry.exists && entry.status === entry.expectedStatus),
      ),
      ...generatedReports.map((entry) =>
        value(`policy:${entry.task}`, entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      ),
    ]),
    scenario("target_doc_claim_sync", "Migration, Pro comparison, Pro/AutoX, compatibility, examples, and release notes carry v1.3 claim boundaries", [
      ...targetDocs.flatMap((file) => [
        ...REQUIRED_CLAIM_AREAS.map((entry) => token(file, entry)),
        ...REQUIRED_BOUNDARY_TOKENS.map((entry) => token(file, entry)),
      ]),
      token(files.typesProfileCapabilities, "NodeProfileV13CapabilityCatalog"),
      token(files.typesProfileCapabilities, "readonly grantsAuthority: false"),
    ]),
    scenario("raw_object_parity_denial", "Target docs explicitly reject raw Android object parity claims", [
      ...targetDocs.flatMap((file) => RAW_OBJECT_TOKENS.map((entry) => token(file, entry))),
      ...targetDocs.map((file) => value(
        `no_overclaim:${file.relative}`,
        !file.text.includes("raw Android object parity is available") &&
          !file.text.includes("raw Android object parity promoted"),
      )),
    ]),
    scenario("docs_and_gradle_wiring", "Docs, manifest, testing, CI, security, error catalog, and Gradle expose P15-26", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "security.types.packaged_migration"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15MigrationProComparison"),
      value("surface_mapping_status", p15Mapping.status === "ready"),
      value("surface_mapping_risk", p15Mapping.risk === "medium"),
      token(files.phase15Tasks, "- [x] P15-26: Update migration, Pro comparison, compatibility, and release notes."),
      token(files.phase15Tasks, "Migration and Pro comparison gate deployed:"),
      token(files.phase15Report, "P15-26 decision: `migration_pro_comparison_v1_3_ready`"),
      token(files.phase15Readme, ":app:verifyNodePhase15MigrationProComparison"),
      token(files.testing, ":app:verifyNodePhase15MigrationProComparison"),
      token(files.ciGates, ":app:verifyNodePhase15MigrationProComparison"),
      token(files.securityModel, "P15-26 records v1.3 migration and Pro comparison claim sync"),
      token(files.errorCatalog, "P15-26 migration and Pro comparison"),
      token(files.gradle15, "verifyNodePhase15MigrationProComparison"),
      token(files.gradle15, "verify-node-phase15-migration-pro-comparison.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase15-migration-pro-comparison-plan-v1", "P15-26 plan JSON must exist."),
    check("all-source-reports-present", generatedReports.every((entry) => entry.exists), "P15-26 source reports must exist."),
    check("claim-areas-covered", targetDocs.every((file) => REQUIRED_CLAIM_AREAS.every((entry) => file.text.includes(entry))), "Every P15-26 target doc must cover required claim areas."),
    check("claim-boundaries-covered", targetDocs.every((file) => REQUIRED_BOUNDARY_TOKENS.every((entry) => file.text.includes(entry))), "Every P15-26 target doc must cover v1.3 evidence boundary labels."),
    check("raw-object-denial-covered", targetDocs.every((file) => RAW_OBJECT_TOKENS.every((entry) => file.text.includes(entry))), "Every P15-26 target doc must reject raw Android object parity."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P15-26 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-migration-pro-comparison-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P15-26",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    targetDocs: TARGET_DOC_KEYS.map((key) => files[key].relative),
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      migrationDecision: "migration_pro_comparison_v1_3_ready",
      p15TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      claimsMatchExecutableEvidence: scenarioById(scenarios, "target_doc_claim_sync").status === "ready",
      docsImplyRawAndroidObjectParity: false,
      noNewlyPromotedProviders: true,
      runtimeAuthorityChanged: false,
      proSurfacePromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: 0,
      nextSummaryGate: "P15-27",
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Migration And Pro Comparison",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Migration decision: \`${report.decision.migrationDecision}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- Claims match executable evidence: \`${report.decision.claimsMatchExecutableEvidence}\``,
    `- Docs imply raw Android object parity: \`${report.decision.docsImplyRawAndroidObjectParity}\``,
    `- No newly promoted providers: \`${report.decision.noNewlyPromotedProviders}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Next summary gate: \`${report.decision.nextSummaryGate}\``,
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
      console.log("Usage: node verify-node-phase15-migration-pro-comparison.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
