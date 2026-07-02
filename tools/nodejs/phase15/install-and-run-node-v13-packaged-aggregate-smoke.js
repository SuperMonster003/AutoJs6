#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-packaged-aggregate-smoke";
const SOURCE_REPORTS = Object.freeze([
  ["P15-04", "phase15-packaged-live-ui-promotion.json", "partial"],
  ["P15-07", "phase15-packaged-overlay-smoke.json", "partial"],
  ["P15-08", "phase15-accessibility-promotion.json", "partial"],
  ["P15-09", "phase15-media-projection-promotion.json", "partial"],
  ["P15-10", "phase15-image-ocr-promotion.json", "partial"],
  ["P15-14", "phase15-packaged-task-smoke.json", "partial"],
  ["P15-16", "phase15-packaged-broadcast-smoke.json", "partial"],
  ["P15-17", "phase15-notification-promotion.json", "partial"],
  ["P15-18", "phase15-settings-power-promotion.json", "partial"],
  ["P15-20", "phase15-packaged-recorder-smoke.json", "partial"],
  ["P15-21", "phase15-mediastore-playback-decision.json", "ready"],
  ["P15-23", "phase15-security-regression-corpus.json", "ready"],
]);
const REQUIRED_SUITES = Object.freeze([
  "packaged_live_ui",
  "packaged_overlay",
  "packaged_accessibility",
  "packaged_media_projection",
  "packaged_image_ocr",
  "packaged_task_scheduling",
  "packaged_broadcast",
  "packaged_notifications_power",
  "packaged_recorder_media",
  "packaged_mediastore_playback_denial",
  "packaged_security_corpus",
]);

const FILES = {
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Report: "docs/nodejs/reports/phase15/P15_24_NODE_V1_3_PACKAGED_AGGREGATE_SMOKE.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  planJson: "docs/nodejs/phase15/node-v1-3-packaged-aggregate-smoke-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_NODE_V1_3_PACKAGED_AGGREGATE_SMOKE.md",
  securityCorpus: "docs/nodejs/security/NODE_PROFILE_V1_3_SECURITY_REGRESSION_CORPUS.json",
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
  console.log("Phase 15 packaged aggregate smoke: " + report.decision.status);
  console.log("Phase 15 packaged aggregate smoke blockers: " + report.blockers.length);
  console.log("Phase 15 packaged aggregate smoke policy violations: " + report.policyViolations.length);
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
  const securityCorpus = parseJson(files.securityCorpus.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-24") || {};
  const suites = Array.isArray(plan.smokeSuites) ? plan.smokeSuites : [];
  const suitesById = Object.fromEntries(suites.map((entry) => [entry.id, entry]));
  const generatedReports = SOURCE_REPORTS.map(([task, basename, expectedStatus]) => {
    const parsed = parseJson(files[task].text);
    const decision = parsed.decision || {};
    return {
      task,
      basename,
      exists: files[task].exists,
      status: statusOf(parsed),
      expectedStatus,
      policyViolations: Array.isArray(parsed.policyViolations) ? parsed.policyViolations.length : 999,
      releaseBlockingBlockers: Number(decision.releaseBlockingBlockers || 0),
      futurePromotionBlockers: Number(decision.futurePromotionBlockers || 0),
      runtimeAuthorityChanged: decision.runtimeAuthorityChanged,
      unpromoted: reportKeepsUnpromoted(parsed),
      stableDenied: decision.promotionState === "stable_denied" || decision.stableDenialReady === true,
    };
  });
  const scenarios = [
    scenario("aggregate_plan", "P15-24 defines a deterministic aggregate packaged smoke entry", [
      value("schema", plan.schema === "autojs6-node-phase15-packaged-aggregate-smoke-plan-v1"),
      value("task", plan.task === "P15-24"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "packaged_aggregate_smoke_contract_ready"),
      value("gradleTask", plan.gradleTask === ":app:installAndRunNodeV13PackagedAggregateSmoke"),
      value("runMode", plan.runMode === "host_contract_ready"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      value("realDeviceArchive", plan.realDeviceInstallRunArchiveAttached === false),
      value("sourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      value("reportArtifacts", includesAll(plan.reportArtifacts || [], [
        "build/reports/nodejs/phase15-packaged-aggregate-smoke.json",
        "build/reports/nodejs/phase15-packaged-aggregate-smoke.md",
      ])),
    ]),
    scenario("source_reports", "Packaged-capable source reports, stable-denied boundary, and P15-23 security corpus are available", [
      ...generatedReports.map((entry) =>
        value(`generated:${entry.task}`, entry.exists && entry.status === entry.expectedStatus),
      ),
      ...generatedReports.map((entry) =>
        value(`policy:${entry.task}`, entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      ),
      ...generatedReports.map((entry) =>
        value(`authority:${entry.task}`, entry.runtimeAuthorityChanged === false && entry.unpromoted),
      ),
      value("p15_21_stable_denied", generatedReports.find((entry) => entry.task === "P15-21").stableDenied === true),
    ]),
    scenario("suite_contracts", "Every aggregate suite defines positive or conditional path plus denial, downgrade, cleanup, and report coverage", [
      value("suiteIds", sameSet(suites.map((entry) => entry.id), REQUIRED_SUITES)),
      ...REQUIRED_SUITES.map((id) => value(`suite:${id}`, suiteReady(suitesById[id]))),
      token(files.securityCorpus, "packaged_metadata_safe_downgrade"),
      token(files.securityCorpus, "P15-24 packaged aggregate smoke"),
      token(files.securityCorpus, "Safe downgrade"),
      value("security_corpus_ready", securityCorpus.status === "ready" && (securityCorpus.policy || {}).runtimeAuthorityChanged === false),
    ]),
    scenario("docs_and_gradle_wiring", "Docs, manifest, and Gradle expose installAndRunNodeV13PackagedAggregateSmoke", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "security.types.packaged_migration"),
      value("surface_mapping_risk", p15Mapping.risk === "critical"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:installAndRunNodeV13PackagedAggregateSmoke"),
      value("surface_mapping_status_planned", p15Mapping.status === "planned"),
      value("surface_mapping_rollback", p15Mapping.rollbackHook === "block_promotion_on_packaged_aggregate_failure"),
      token(files.phase15Tasks, "- [x] P15-24: Add v1.3 packaged aggregate smoke."),
      token(files.phase15Tasks, "Packaged aggregate smoke gate deployed:"),
      token(files.phase15Report, "P15-24 decision: `packaged_aggregate_smoke_contract_ready`"),
      token(files.phase15Readme, ":app:installAndRunNodeV13PackagedAggregateSmoke"),
      token(files.testing, ":app:installAndRunNodeV13PackagedAggregateSmoke"),
      token(files.ciGates, ":app:installAndRunNodeV13PackagedAggregateSmoke"),
      token(files.securityModel, "P15-24 records the v1.3 packaged aggregate smoke contract"),
      token(files.errorCatalog, "P15-24 packaged aggregate smoke"),
      token(files.gradle15, "installAndRunNodeV13PackagedAggregateSmoke"),
      token(files.gradle15, "install-and-run-node-v13-packaged-aggregate-smoke.js"),
    ]),
  ];
  const checks = [
    check(
      "plan-json-present",
      files.planJson.exists &&
        plan.schema === "autojs6-node-phase15-packaged-aggregate-smoke-plan-v1" &&
        plan.task === "P15-24" &&
        plan.status === "ready" &&
        plan.runtimeAuthorityChanged === false,
      "P15-24 packaged aggregate smoke plan JSON must exist and must not change runtime authority.",
    ),
    check(
      "source-reports-imported",
      generatedReports.every((entry) => entry.exists && entry.status === entry.expectedStatus && entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0 && entry.runtimeAuthorityChanged === false && entry.unpromoted),
      "P15-24 source reports must exist, remain policy-clean, and remain unpromoted.",
    ),
    check(
      "all-suites-ready",
      REQUIRED_SUITES.every((id) => suiteReady(suitesById[id])),
      "Every P15-24 aggregate suite must be ready.",
    ),
    check(
      "critical-manifest-remains-planned",
      p15Mapping.ownerSurface === "security.types.packaged_migration" &&
        p15Mapping.risk === "critical" &&
        p15Mapping.status === "planned" &&
        p15Mapping.rollbackHook === "block_promotion_on_packaged_aggregate_failure",
      "P15-24 critical manifest mapping must remain planned with a packaged aggregate rollback hook.",
    ),
    check(
      "all-scenarios-ready",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-24 scenarios must be ready.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-packaged-aggregate-smoke-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P15-24",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    suiteCount: suites.length,
    runMode: plan.runMode || "missing",
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      packagedDecision: "packaged_aggregate_smoke_contract_ready",
      p15TaskComplete: policyViolations.length === 0,
      deterministicGradleEntryReady: true,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      sourceReportsPolicyClean: generatedReports.every((entry) => entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      suiteContractsReady: REQUIRED_SUITES.every((id) => suiteReady(suitesById[id])),
      realDeviceInstallRunArchiveAttached: false,
      criticalManifestRemainsPlanned: p15Mapping.status === "planned",
      runtimeAuthorityChanged: false,
      proSurfacePromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: 0,
      sourceFuturePromotionBlockers: generatedReports.reduce((sum, entry) => sum + entry.futurePromotionBlockers, 0),
      nextTypeGate: "P15-25",
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Packaged Aggregate Smoke",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Packaged decision: \`${report.decision.packagedDecision}\``,
    `- Deterministic Gradle entry ready: \`${report.decision.deterministicGradleEntryReady}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- Source reports policy clean: \`${report.decision.sourceReportsPolicyClean}\``,
    `- Suite contracts ready: \`${report.decision.suiteContractsReady}\``,
    `- Real device install-run archive attached: \`${report.decision.realDeviceInstallRunArchiveAttached}\``,
    `- Critical manifest remains planned: \`${report.decision.criticalManifestRemainsPlanned}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Next type gate: \`${report.decision.nextTypeGate}\``,
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

function suiteReady(entry) {
  return Boolean(entry) &&
    typeof entry.positivePath === "string" &&
    entry.positivePath.length > 0 &&
    entry.undeclaredCapabilityDenial === true &&
    entry.packagedMetadataMismatchDenial === true &&
    entry.safeDowngradeRequired === true &&
    entry.cleanupRequired === true &&
    typeof entry.currentExecutionState === "string" &&
    entry.currentExecutionState.length > 0 &&
    typeof entry.stableDeniedBoundaryRequired === "boolean";
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

function reportKeepsUnpromoted(report) {
  const decision = report.decision || {};
  const decisionContract = report.decisionContract || {};
  const promotedEntries = Object.entries({ ...decisionContract, ...decision })
    .filter(([key]) => key.endsWith("Promoted") || key === "proSurfacePromoted");
  return promotedEntries.length === 0 || promotedEntries.every(([, value]) => value === false);
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

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((value) => actual.includes(value));
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
      console.log("Usage: node install-and-run-node-v13-packaged-aggregate-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
