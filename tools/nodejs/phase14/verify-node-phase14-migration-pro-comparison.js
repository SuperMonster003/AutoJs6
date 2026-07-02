#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-migration-pro-comparison";
const SOURCE_REPORTS = Object.freeze([
  ["P13-56", "phase13-migration-pro9-comparison.json"],
  ["P14-16", "phase14-ui-automation-media-doc-sync.json"],
  ["P14-27", "phase14-task-device-media-doc-sync.json"],
  ["P14-32", "phase14-desktop-runtime-doc-sync.json"],
  ["P14-33", "phase14-security-regression-corpus.json"],
  ["P14-34", "phase14-packaged-aggregate-smoke.json"],
  ["P14-35", "phase14-type-declaration-profiles.json"],
]);
const TARGET_DOC_KEYS = Object.freeze([
  "migrationDoc",
  "proComparison",
  "proAutoxNotes",
  "compatibilityProfile",
  "examplesDoc",
]);
const REQUIRED_CLAIM_AREAS = Object.freeze([
  "Pro UI and floaty",
  "accessibility",
  "screenshot/OCR",
  "tasks",
  "notifications",
  "recorder",
  "worker policy",
  "packaged behavior",
  "rollback",
]);
const REQUIRED_BOUNDARY_TOKENS = Object.freeze([
  "claims match executable evidence",
  "do not imply raw Android object parity",
  "provider_poc_not_promoted",
  "worker_intentionally_unsupported_v1_2",
  "host_contract_ready",
  "NodeProfileV12CapabilityCatalog",
  "runtime authority remains unchanged",
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
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_36_MIGRATION_PRO_COMPARISON.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/node-v1-2-migration-pro-comparison.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NODE_V1_2_MIGRATION_PRO_COMPARISON.md",
  migrationDoc: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  typesProfileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
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
  console.log("Phase 14 migration and Pro comparison: " + report.decision.status);
  console.log("Phase 14 migration and Pro comparison blockers: " + report.blockers.length);
  console.log("Phase 14 migration and Pro comparison policy violations: " + report.policyViolations.length);
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
  const targetDocs = TARGET_DOC_KEYS.map((key) => files[key]);
  const scenarios = [
    scenario("plan_and_source_reports", "P14-36 imports the required migration, docs, security, packaged, and type gates", [
      value("planSchema", plan.schema === "autojs6-node-phase14-migration-pro-comparison-plan-v1"),
      value("planTask", plan.task === "P14-36"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "migration_pro_comparison_v1_2_ready"),
      value("gradleTask", plan.gradleTask === ":app:verifyNodePhase14MigrationProComparison"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("sourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      ...generatedReports.map((entry) => check(
        `generated:${entry.task}`,
        entry.exists && ["partial", "ready", "passed"].includes(entry.status),
        `${entry.task} generated report must exist with partial/ready/passed status`,
      )),
    ]),
    scenario("target_doc_claim_sync", "Migration, Pro comparison, Pro/AutoX, compatibility, and examples docs carry v1.2 claim boundaries", [
      ...targetDocs.flatMap((file) => [
        token(file, "P14-36 records v1.2 migration and Pro comparison docs as ready"),
        ...REQUIRED_CLAIM_AREAS.map((entry) => token(file, entry)),
        ...REQUIRED_BOUNDARY_TOKENS.map((entry) => token(file, entry)),
      ]),
      token(files.typesProfileCapabilities, "NodeProfileV12CapabilityCatalog"),
      token(files.typesProfileCapabilities, "readonly grantsAuthority: false"),
    ]),
    scenario("raw_object_parity_denial", "Target docs explicitly reject raw Android object parity claims", [
      ...targetDocs.flatMap((file) => RAW_OBJECT_TOKENS.map((entry) => token(file, entry))),
      ...targetDocs.map((file) => check(
        `no-overclaim:${file.relative}`,
        !file.text.includes("raw Android object parity is available") &&
          !file.text.includes("raw Android object parity promoted"),
        `${file.relative} must not claim raw Android object parity is available`,
      )),
    ]),
    scenario("docs_and_gradle_wiring", "Docs, manifest, testing, CI, security, error catalog, and Gradle expose P14-36", [
      value("surfaceEvidence", docsSurface &&
        (docsSurface.tasks || []).includes("P14-36") &&
        (docsSurface.evidence || []).includes("docs/nodejs/phase14/node-v1-2-migration-pro-comparison.json") &&
        (docsSurface.evidence || []).includes(":app:verifyNodePhase14MigrationProComparison")),
      token(files.surfaceManifestDoc, "P14-36 records v1.2 migration and Pro comparison claim sync"),
      token(files.phase14Tasks, "- [x] P14-36: Update migration and Pro comparison docs for v1.2."),
      token(files.phase14Tasks, "Migration and Pro comparison gate deployed:"),
      token(files.phase14Report, "P14-36 decision: `migration_pro_comparison_v1_2_ready`"),
      token(files.phase14Readme, ":app:verifyNodePhase14MigrationProComparison"),
      token(files.testing, ":app:verifyNodePhase14MigrationProComparison"),
      token(files.ciGates, ":app:verifyNodePhase14MigrationProComparison"),
      token(files.securityModel, "P14-36 records v1.2 migration and Pro comparison claim sync"),
      token(files.errorCatalog, "P14-36 migration and Pro comparison"),
      token(files.gradle14, "verifyNodePhase14MigrationProComparison"),
      token(files.gradle14, "verify-node-phase14-migration-pro-comparison.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-migration-pro-comparison-plan-v1", "P14-36 plan JSON must exist."),
    check("all-source-reports-present", generatedReports.every((entry) => entry.exists), "P14-36 source reports must exist."),
    check("claim-areas-covered", targetDocs.every((file) => REQUIRED_CLAIM_AREAS.every((entry) => file.text.includes(entry))), "Every P14-36 target doc must cover required claim areas."),
    check("claim-boundaries-covered", targetDocs.every((file) => REQUIRED_BOUNDARY_TOKENS.every((entry) => file.text.includes(entry))), "Every P14-36 target doc must cover v1.2 evidence boundary labels."),
    check("raw-object-denial-covered", targetDocs.every((file) => RAW_OBJECT_TOKENS.every((entry) => file.text.includes(entry))), "Every P14-36 target doc must reject raw Android object parity."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-36 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-migration-pro-comparison-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-36",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    targetDocs: TARGET_DOC_KEYS.map((key) => files[key].relative),
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      migrationDecision: "migration_pro_comparison_v1_2_ready",
      p14TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      claimsMatchExecutableEvidence: scenarioById(scenarios, "target_doc_claim_sync").status === "ready",
      docsImplyRawAndroidObjectParity: false,
      runtimeAuthorityChanged: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Migration And Pro Comparison",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Migration decision: \`${report.decision.migrationDecision}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- Claims match executable evidence: \`${report.decision.claimsMatchExecutableEvidence}\``,
    `- Docs imply raw Android object parity: \`${report.decision.docsImplyRawAndroidObjectParity}\``,
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
      console.log("Usage: node verify-node-phase14-migration-pro-comparison.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
