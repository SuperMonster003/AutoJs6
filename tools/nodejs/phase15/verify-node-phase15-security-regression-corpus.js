#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-security-regression-corpus";
const REQUIRED_SOURCE_GATES = Object.freeze([
  "P15-04",
  "P15-05",
  "P15-06",
  "P15-07",
  "P15-08",
  "P15-09",
  "P15-10",
  "P15-11",
  "P15-12",
  "P15-13",
  "P15-14",
  "P15-15",
  "P15-16",
  "P15-17",
  "P15-18",
  "P15-19",
  "P15-20",
  "P15-21",
  "P15-22",
]);
const EXPECTED_CAPABILITY_IDS = Object.freeze([
  "live_ui",
  "overlay",
  "accessibility",
  "media_projection",
  "image_ocr",
  "task_database",
  "task_schema_packaged_task",
  "intent_broadcast",
  "notifications_power",
  "recorder_media",
  "mediastore_playback",
  "doc_type_release_sync",
  "packaged_metadata_safe_downgrade",
]);
const REQUIRED_CASE_FIELDS = Object.freeze([
  "positiveCases",
  "negativeCases",
  "abuseCases",
  "runtimeGuards",
  "promotionPreconditions",
]);
const REQUIRED_BOUNDARY_CATEGORIES = Object.freeze([
  "raw_object_denial",
  "permission_bypass",
  "lifecycle_cleanup",
  "packaged_metadata_mismatch",
  "safe_downgrade",
  "cross_package_ownership_denial",
  "foreground_disclosure",
  "stable_denied_boundary",
]);
const SOURCE_REPORTS = Object.freeze([
  ["p15PackagedLiveUi", "phase15-packaged-live-ui-promotion.json", "P15-04", "partial"],
  ["p15OverlayProvider", "phase15-overlay-provider-promotion.json", "P15-05", "partial"],
  ["p15OverlayOemMatrix", "phase15-overlay-oem-matrix.json", "P15-06", "partial"],
  ["p15PackagedOverlay", "phase15-packaged-overlay-smoke.json", "P15-07", "partial"],
  ["p15Accessibility", "phase15-accessibility-promotion.json", "P15-08", "partial"],
  ["p15MediaProjection", "phase15-media-projection-promotion.json", "P15-09", "partial"],
  ["p15ImageOcr", "phase15-image-ocr-promotion.json", "P15-10", "partial"],
  ["p15UiAutomationMediaDocSync", "phase15-ui-automation-media-doc-sync.json", "P15-11", "ready"],
  ["p15TaskDatabase", "phase15-task-database-promotion.json", "P15-12", "partial"],
  ["p15TaskSchemaRollback", "phase15-task-schema-rollback.json", "P15-13", "partial"],
  ["p15PackagedTask", "phase15-packaged-task-smoke.json", "P15-14", "partial"],
  ["p15IntentBroadcast", "phase15-intent-broadcast-promotion.json", "P15-15", "partial"],
  ["p15PackagedBroadcast", "phase15-packaged-broadcast-smoke.json", "P15-16", "partial"],
  ["p15Notification", "phase15-notification-promotion.json", "P15-17", "partial"],
  ["p15SettingsPower", "phase15-settings-power-promotion.json", "P15-18", "partial"],
  ["p15Recorder", "phase15-recorder-promotion.json", "P15-19", "partial"],
  ["p15PackagedRecorder", "phase15-packaged-recorder-smoke.json", "P15-20", "partial"],
  ["p15MediaStorePlayback", "phase15-mediastore-playback-decision.json", "P15-21", "ready"],
  ["p15TaskDeviceMediaDocSync", "phase15-task-device-media-doc-sync.json", "P15-22", "ready"],
]);

const FILES = {
  planJson: "docs/nodejs/phase15/security-regression-corpus-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_SECURITY_REGRESSION_CORPUS.md",
  corpus: "docs/nodejs/security/NODE_PROFILE_V1_3_SECURITY_REGRESSION_CORPUS.json",
  phase14Corpus: "docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json",
  taskReport: "docs/nodejs/reports/phase15/P15_23_SECURITY_REGRESSION_CORPUS.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
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
  console.log("Phase 15 security regression corpus: " + report.decision.status);
  console.log("Phase 15 security regression corpus blockers: " + report.blockers.length);
  console.log("Phase 15 security regression corpus policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([key, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      key,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  const p14Security = path.join(reportDir, "phase14-security-regression-corpus.json");
  entries.push([
    "p14SecurityReport",
    {
      relative: path.relative(repoRoot, p14Security).replace(/\\/g, "/"),
      exists: fs.existsSync(p14Security),
      text: read(p14Security),
    },
  ]);
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const corpus = parseJson(files.corpus.text);
  const phase14Corpus = parseJson(files.phase14Corpus.text);
  const p14SecurityReport = parseJson(files.p14SecurityReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-23") || {};
  const capabilities = Array.isArray(corpus.capabilities) ? corpus.capabilities : [];
  const capabilitiesById = Object.fromEntries(capabilities.map((entry) => [entry.id, entry]));
  const sourceReports = SOURCE_REPORTS.map(([key, basename, task, expectedStatus]) => {
    const sourceReport = parseJson(files[key].text);
    const decision = sourceReport.decision || {};
    return {
      id: key,
      basename,
      task,
      expectedStatus,
      exists: files[key].exists,
      status: statusOf(sourceReport),
      policyViolations: Array.isArray(sourceReport.policyViolations) ? sourceReport.policyViolations.length : 999,
      releaseBlockingBlockers: Number(decision.releaseBlockingBlockers || 0),
      futurePromotionBlockers: Number(decision.futurePromotionBlockers || 0),
      runtimeAuthorityChanged: decision.runtimeAuthorityChanged,
      unpromoted: reportKeepsUnpromoted(sourceReport),
      stableDenied: decision.promotionState === "stable_denied" || decision.stableDenialReady === true,
    };
  });
  const sourceGateUnion = Array.from(new Set(capabilities.flatMap((entry) => entry.sourceGates || []).filter((entry) => entry !== "P15-23")));
  const scenarios = [
    scenario("source_gate_imports", "P15-04 through P15-22 generated reports are imported without runtime promotion", [
      value("plan_schema", plan.schema === "autojs6-node-phase15-security-regression-corpus-plan-v1"),
      value("task", plan.task === "P15-23"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "security_corpus_updated_no_authority_promoted"),
      value("runtime_authority", plan.runtimeAuthorityChanged === false),
      value("no_newly_promoted", plan.noNewlyPromotedProviders === true),
      value("source_gates", sameSet(plan.sourceGates || [], REQUIRED_SOURCE_GATES)),
      ...sourceReports.map((entry) => value(`generated:${entry.id}`, entry.exists && entry.status === entry.expectedStatus)),
      ...sourceReports.map((entry) => value(`policy:${entry.id}`, entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0)),
      ...sourceReports.map((entry) => value(`authority:${entry.id}`, entry.runtimeAuthorityChanged === false && entry.unpromoted)),
      value("p15_21_stable_denied", sourceReports.find((entry) => entry.task === "P15-21").stableDenied === true),
    ]),
    scenario("corpus_shape", "Every tracked v1.3 provider family has positive, negative, abuse, guard, precondition, and rollback entries", [
      value("corpus_schema", corpus.schema === "autojs6-node-profile-v1-3-security-regression-corpus-v1"),
      value("corpus_task", corpus.task === "P15-23"),
      value("corpus_status", corpus.status === "ready"),
      value("release_decision", corpus.releaseDecision === "security_corpus_updated_no_authority_promoted"),
      value("runtime_authority", ((corpus.policy || {}).runtimeAuthorityChanged) === false),
      value("critical_manifest_status", ((corpus.policy || {}).criticalManifestStatus) === "planned"),
      value("no_newly_promoted", ((corpus.policy || {}).noNewlyPromotedProviders) === true),
      value("capability_ids", sameSet(capabilities.map((entry) => entry.id), EXPECTED_CAPABILITY_IDS)),
      value("source_gate_union", sameSet(sourceGateUnion, REQUIRED_SOURCE_GATES)),
      ...EXPECTED_CAPABILITY_IDS.map((id) => value(`capability:${id}`, capabilityReady(capabilitiesById[id]))),
    ]),
    scenario("boundary_categories", "The corpus covers raw object denial, permission bypass, cleanup, metadata mismatch, Safe downgrade, ownership, disclosure, and stable-denied boundaries", [
      value("boundary_category_set", sameSet(((corpus.policy || {}).boundaryCategories || []), REQUIRED_BOUNDARY_CATEGORIES)),
      token(files.corpus, "raw object"),
      token(files.corpus, "permission bypass"),
      token(files.corpus, "lifecycle cleanup"),
      token(files.corpus, "metadata mismatch"),
      token(files.corpus, "Safe downgrade"),
      token(files.corpus, "cross-package"),
      token(files.corpus, "foreground disclosure"),
      token(files.corpus, "stable-denied"),
    ]),
    scenario("upstream_security_baseline", "P15-23 imports the ready P14-33 security corpus before updating v1.3 coverage", [
      value("phase14_corpus_schema", phase14Corpus.schema === "autojs6-node-profile-v1-2-security-regression-corpus-v1"),
      value("phase14_corpus_status", phase14Corpus.status === "ready"),
      value("p14_report_ready", files.p14SecurityReport.exists && statusOf(p14SecurityReport) === "ready" && (p14SecurityReport.decision || {}).p14TaskComplete === true),
      value("plan_upstream", plan.upstreamCorpus === "P14-33"),
      value("corpus_imported_baseline", includesAll((corpus.policy || {}).importedBaseline || [], ["P14-33"])),
    ]),
    scenario("contracts_and_critical_policy", "Promotion contracts and disclosure audit still require security, disclosure, rollback, types, docs, packaged, and Safe downgrade evidence", [
      value("contract_security_bar", promotionBarIncludesSecurity(contracts)),
      value("contract_safe_downgrade_bar", textIncludes(files.promotionContracts.text, "Safe downgrade")),
      value(
        "disclosure_audit_status",
        disclosureAudit.status === "ready" ||
          (disclosureAudit.task === "P15-03" && disclosureAudit.runtimeAuthorityChanged === false),
      ),
      value("p15_mapping_owner", p15Mapping.ownerSurface === "security.types.packaged_migration"),
      value("p15_mapping_risk", p15Mapping.risk === "critical"),
      value("p15_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15SecurityRegressionCorpus"),
      value("p15_mapping_status_planned", p15Mapping.status === "planned"),
      value("p15_mapping_rollback", p15Mapping.rollbackHook === "block_promotion_on_security_regression"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 15 docs, TESTING, CI, report, and Gradle expose the P15-23 gate", [
      token(files.phase15Gradle, "verifyNodePhase15SecurityRegressionCorpus"),
      token(files.phase15Gradle, "verify-node-phase15-security-regression-corpus.js"),
      token(files.phase15Tasks, "- [x] P15-23: Update v1.3 security regression corpus."),
      token(files.phase15Tasks, "Security regression corpus gate deployed:"),
      token(files.taskReport, "P15-23 decision: `security_corpus_updated_no_authority_promoted`"),
      token(files.phase15Readme, ":app:verifyNodePhase15SecurityRegressionCorpus"),
      token(files.securityModel, "P15-23 records the v1.3 security regression corpus update"),
      token(files.errorCatalog, "P15-23 security regression corpus"),
      token(files.testing, ":app:verifyNodePhase15SecurityRegressionCorpus"),
      token(files.ciGates, ":app:verifyNodePhase15SecurityRegressionCorpus"),
    ]),
  ];
  const checks = [
    check(
      "plan-json-present",
      files.planJson.exists &&
        plan.schema === "autojs6-node-phase15-security-regression-corpus-plan-v1" &&
        plan.task === "P15-23" &&
        plan.status === "ready" &&
        plan.runtimeAuthorityChanged === false,
      "P15-23 security regression plan JSON must exist and must not change runtime authority.",
    ),
    check(
      "corpus-present",
      files.corpus.exists &&
        corpus.schema === "autojs6-node-profile-v1-3-security-regression-corpus-v1" &&
        corpus.status === "ready" &&
        ((corpus.policy || {}).runtimeAuthorityChanged) === false,
      "P15-23 v1.3 security corpus must exist and must not change runtime authority.",
    ),
    check(
      "source-reports-imported",
      sourceReports.every((entry) => entry.exists && entry.status === entry.expectedStatus && entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0 && entry.runtimeAuthorityChanged === false && entry.unpromoted),
      "P15-04 through P15-22 generated reports must remain policy-clean and unpromoted, with P15-21 ready/stable-denied.",
    ),
    check(
      "phase14-security-ready",
      files.p14SecurityReport.exists && statusOf(p14SecurityReport) === "ready" && (p14SecurityReport.decision || {}).p14TaskComplete === true,
      "P15-23 must import the ready P14-33 security regression report.",
    ),
    check(
      "critical-manifest-remains-planned",
      p15Mapping.ownerSurface === "security.types.packaged_migration" &&
        p15Mapping.risk === "critical" &&
        p15Mapping.status === "planned" &&
        p15Mapping.rollbackHook === "block_promotion_on_security_regression",
      "P15-23 critical manifest mapping must remain planned with a security-regression rollback hook.",
    ),
    check(
      "all-capabilities-covered",
      EXPECTED_CAPABILITY_IDS.every((id) => capabilityReady(capabilitiesById[id])) && sameSet(sourceGateUnion, REQUIRED_SOURCE_GATES),
      "Every expected P15-23 capability entry must have required case coverage and source-gate coverage.",
    ),
    check(
      "all-scenarios-ready",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-23 security corpus scenarios must be ready.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-security-regression-corpus-report-v1",
    report: REPORT_BASENAME,
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15SecurityRegressionCorpus",
    task: "P15-23",
    status: policyViolations.length === 0 ? "ready" : "failed",
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    sourceReports,
    capabilityCount: capabilities.length,
    boundaryCategories: (corpus.policy || {}).boundaryCategories || [],
    checks,
    scenarios,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      promotionState: "security_corpus_updated_no_authority_promoted",
      p15TaskComplete: policyViolations.length === 0,
      sourceReportsImported: sourceReports.every((entry) => entry.exists && entry.status === entry.expectedStatus),
      sourceReportsPolicyClean: sourceReports.every((entry) => entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      capabilityCoverageReady: EXPECTED_CAPABILITY_IDS.every((id) => capabilityReady(capabilitiesById[id])),
      boundaryCoverageReady: scenarioById(scenarios, "boundary_categories").status === "ready",
      p14SecurityBaselineImported: files.p14SecurityReport.exists && statusOf(p14SecurityReport) === "ready",
      criticalManifestRemainsPlanned: p15Mapping.status === "planned",
      noNewlyPromotedProviders: true,
      runtimeAuthorityChanged: false,
      proSurfacePromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: 0,
      sourceFuturePromotionBlockers: sourceReports.reduce((sum, entry) => sum + entry.futurePromotionBlockers, 0),
      nextPackagedGate: "P15-24",
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Security Regression Corpus",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Critical manifest remains planned: \`${report.decision.criticalManifestRemainsPlanned}\``,
    `- Source future promotion blockers: ${report.decision.sourceFuturePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Source Reports",
    "",
    "| Report | Task | Status | Expected | Policy violations | Future blockers |",
    "| --- | --- | --- | --- | --- | --- |",
  ];
  report.sourceReports.forEach((entry) => {
    lines.push(`| \`${entry.basename}\` | \`${entry.task}\` | \`${entry.status}\` | \`${entry.expectedStatus}\` | ${entry.policyViolations} | ${entry.futurePromotionBlockers} |`);
  });
  lines.push("", "## Boundary Categories", "");
  report.boundaryCategories.forEach((entry) => {
    lines.push(`- \`${entry}\``);
  });
  lines.push("", "## Scenarios", "", "| Scenario | Status | Missing evidence |", "| --- | --- | --- |");
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(missing)} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function capabilityReady(entry) {
  return Boolean(entry) &&
    REQUIRED_CASE_FIELDS.every((field) => Array.isArray(entry[field]) && entry[field].length > 0) &&
    typeof entry.rollback === "string" &&
    entry.rollback.length > 0 &&
    Array.isArray(entry.sourceGates) &&
    entry.sourceGates.length > 0 &&
    Array.isArray(entry.boundaryCategories) &&
    entry.boundaryCategories.length > 0;
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return {
    id,
    description,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    missing,
  };
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

function promotionBarIncludesSecurity(contracts) {
  const text = JSON.stringify(contracts);
  return text.includes("security") && text.includes("TypeScript") && text.includes("packaged") && text.includes("raw-object");
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((entry, index) => actualSorted[index] === entry);
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((entry) => actual.includes(entry));
}

function textIncludes(text, tokenValue) {
  return String(text || "").includes(tokenValue);
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
      console.log("Usage: node verify-node-phase15-security-regression-corpus.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
