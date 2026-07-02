#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-security-regression-corpus";
const EXPECTED_CAPABILITY_IDS = Object.freeze([
  "live_ui",
  "overlay",
  "accessibility",
  "media_projection",
  "image_ocr",
  "task_database",
  "intent_broadcast",
  "notifications",
  "power_settings",
  "recorder_media",
  "media_playback_mediastore",
  "worker_threads_process_worker",
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
  "path_escape",
  "permission_bypass",
  "lifecycle_cleanup",
  "packaged_metadata_mismatch",
  "safe_downgrade",
]);
const SOURCE_REPORTS = Object.freeze([
  ["P14-07", "phase14-live-ui-provider-packaged.json"],
  ["P14-08", "phase14-overlay-provider.json"],
  ["P14-09", "phase14-overlay-permission-oem-matrix.json"],
  ["P14-10", "phase14-packaged-overlay-positive-smoke.json"],
  ["P14-11", "phase14-accessibility-lifecycle.json"],
  ["P14-12", "phase14-accessibility-selector-action-matrix.json"],
  ["P14-13", "phase14-media-projection-provider.json"],
  ["P14-14", "phase14-advanced-image-operations.json"],
  ["P14-15", "phase14-packaged-image-ocr-evidence.json"],
  ["P14-16", "phase14-ui-automation-media-doc-sync.json"],
  ["P14-17", "phase14-task-database-provider.json"],
  ["P14-18", "phase14-task-schema-migration-rollback.json"],
  ["P14-19", "phase14-packaged-task-scheduling-smoke.json"],
  ["P14-20", "phase14-intent-broadcast-provider.json"],
  ["P14-21", "phase14-packaged-broadcast-trigger-remove-smoke.json"],
  ["P14-22", "phase14-notification-ownership.json"],
  ["P14-23", "phase14-settings-power-oem-background-matrix.json"],
  ["P14-24", "phase14-recorder-provider.json"],
  ["P14-25", "phase14-packaged-recorder-media-stress.json"],
  ["P14-26", "phase14-media-playback-mediastore-boundary.json"],
  ["P14-27", "phase14-task-device-media-doc-sync.json"],
  ["P14-28", "phase14-worker-native-availability.json"],
  ["P14-29", "phase14-worker-runnable-packaged-smoke.json"],
  ["P14-30", "phase14-worker-resource-rss-stress.json"],
  ["P14-31", "phase14-process-worker-replacement-decision.json"],
  ["P14-32", "phase14-desktop-runtime-doc-sync.json"],
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_33_SECURITY_REGRESSION_CORPUS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/security-regression-corpus-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_SECURITY_REGRESSION_CORPUS.md",
  corpus: "docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json",
  relaxedCorpus: "docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json",
  promotionContracts: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
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
  console.log("Phase 14 security regression corpus: " + report.decision.status);
  console.log("Phase 14 security regression corpus blockers: " + report.blockers.length);
  console.log("Phase 14 security regression corpus policy violations: " + report.policyViolations.length);
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
  const corpus = parseJson(files.corpus.text);
  const relaxedCorpus = parseJson(files.relaxedCorpus.text);
  const contracts = parseJson(files.promotionContracts.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const securitySurface = (manifest.surfaces || []).find((entry) => entry.id === "security.packaged_release_gates") || null;
  const capabilities = Array.isArray(corpus.capabilities) ? corpus.capabilities : [];
  const capabilitiesById = Object.fromEntries(capabilities.map((entry) => [entry.id, entry]));
  const generatedReports = SOURCE_REPORTS.map(([task]) => {
    const parsed = parseJson(files[task].text);
    return {
      task,
      exists: files[task].exists,
      status: statusOf(parsed),
    };
  });
  const scenarios = [
    scenario("source_gate_imports", "P14-07 through P14-32 generated reports are imported before P14-33 can pass", [
      value("planSchema", plan.schema === "autojs6-node-phase14-security-regression-corpus-plan-v1"),
      value("planTask", plan.task === "P14-33"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "security_corpus_expanded_no_authority_promoted"),
      value("planSourceGates", sameSet(plan.sourceGates || [], SOURCE_REPORTS.map(([task]) => task))),
      ...generatedReports.map((entry) => check(
        `generated:${entry.task}`,
        entry.exists && ["partial", "ready", "passed", "provider_poc"].includes(entry.status),
        `${entry.task} generated report must exist with partial/ready/passed status`,
      )),
    ]),
    scenario("corpus_shape", "Every tracked v1.2 high-risk capability has positive, negative, abuse, guard, precondition, and rollback entries", [
      value("corpusSchema", corpus.schema === "autojs6-node-profile-v1-2-security-regression-corpus-v1"),
      value("corpusTask", corpus.task === "P14-33"),
      value("corpusStatus", corpus.status === "ready"),
      value("releaseDecision", corpus.releaseDecision === "security_corpus_expanded_no_authority_promoted"),
      value("runtimeAuthority", (corpus.policy || {}).runtimeAuthorityChanged === false),
      value("capabilityIds", sameSet(capabilities.map((entry) => entry.id), EXPECTED_CAPABILITY_IDS)),
      ...EXPECTED_CAPABILITY_IDS.map((id) => check(`capability:${id}`, capabilityReady(capabilitiesById[id]), `${id} must include positive/negative/abuse/guard/precondition/rollback coverage`)),
    ]),
    scenario("boundary_categories", "The corpus covers raw object denial, path escape, permission bypass, lifecycle cleanup, metadata mismatch, and Safe downgrade", [
      value("boundaryCategorySet", sameSet(((corpus.policy || {}).boundaryCategories || []), REQUIRED_BOUNDARY_CATEGORIES)),
      textToken(files.corpus, "raw object"),
      textToken(files.corpus, "path escape"),
      textToken(files.corpus, "permission bypass"),
      textToken(files.corpus, "lifecycle cleanup"),
      textToken(files.corpus, "metadata mismatch"),
      textToken(files.corpus, "Safe downgrade"),
      textToken(files.corpus, "ResourceRegistry"),
      textToken(files.corpus, "packaged aggregate smoke"),
    ]),
    scenario("promotion_contract_and_relaxed_corpus_link", "P14-33 is linked to promotion contracts and the existing relaxed-capability corpus", [
      value("promotionContractDisclosure", includesAll(((contracts.packagedHighPrivilegeDisclosure || {}).requiredFields || []), [
        "manifestMetadata",
        "permissionDeniedDiagnostic",
        "undeclaredCapabilityDiagnostic",
        "rollbackBehavior",
      ])),
      value("promotionContractRawObjects", includesAll(((contracts.packagedHighPrivilegeDisclosure || {}).rawObjectDenials || []), [
        "Context",
        "Activity",
        "MediaProjection",
        "MediaRecorder",
        "Intent",
        "BroadcastReceiver",
        "Notification",
        "WakeLock",
        "TimedTask",
      ])),
      value("relaxedWorkerEntry", ((relaxedCorpus.capabilities || []).find((entry) => entry.id === "worker_threads") || {}).rollback ===
        "Keep autojs.nodejs.worker_threads.experimental=false, keep project/package worker opt-in disabled, and keep node:worker_threads as shape-only until native, packaged, resource, security, and rollback gates pass."),
      value("relaxedChildProcessEntry", (((relaxedCorpus.capabilities || []).find((entry) => entry.id === "child_process") || {}).promotionPreconditions || []).includes(
        "P14-31 process-worker replacement keeps no user-facing facade and no raw process authority for v1.2",
      )),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 manifest, report docs, testing, CI, and Gradle expose the P14-33 gate", [
      value("surfaceEvidence", securitySurface &&
        securitySurface.status === "design_locked" &&
        (securitySurface.tasks || []).includes("P14-33") &&
        (securitySurface.evidence || []).includes("docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json") &&
        (securitySurface.evidence || []).includes(":app:verifyNodePhase14SecurityRegressionCorpus")),
      token(files.surfaceManifestDoc, "P14-33 adds the v1.2 security regression corpus gate"),
      token(files.phase14Tasks, "- [x] P14-33: Expand v1.2 security regression corpus."),
      token(files.phase14Tasks, "Security regression corpus gate deployed:"),
      token(files.phase14Report, "P14-33 decision: `security_corpus_expanded_no_authority_promoted`"),
      token(files.phase14Readme, ":app:verifyNodePhase14SecurityRegressionCorpus"),
      token(files.securityModel, "P14-33 records the v1.2 security regression corpus expansion"),
      token(files.errorCatalog, "P14-33 security regression corpus"),
      token(files.testing, ":app:verifyNodePhase14SecurityRegressionCorpus"),
      token(files.ciGates, ":app:verifyNodePhase14SecurityRegressionCorpus"),
      token(files.gradle14, "verifyNodePhase14SecurityRegressionCorpus"),
      token(files.gradle14, "verify-node-phase14-security-regression-corpus.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-security-regression-corpus-plan-v1", "P14-33 plan JSON must exist."),
    check("corpus-present", files.corpus.exists && corpus.schema === "autojs6-node-profile-v1-2-security-regression-corpus-v1", "P14-33 v1.2 security corpus must exist."),
    check("all-source-reports-present", generatedReports.every((entry) => entry.exists), "P14-07 through P14-32 generated reports must exist."),
    check("all-capabilities-covered", EXPECTED_CAPABILITY_IDS.every((id) => capabilityReady(capabilitiesById[id])), "Every expected P14-33 capability entry must have required case coverage."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-33 scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-security-regression-corpus-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-33",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    capabilityCount: capabilities.length,
    boundaryCategories: (corpus.policy || {}).boundaryCategories || [],
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      securityDecision: "security_corpus_expanded_no_authority_promoted",
      p14TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists),
      capabilityCoverageReady: EXPECTED_CAPABILITY_IDS.every((id) => capabilityReady(capabilitiesById[id])),
      boundaryCoverageReady: scenarioById(scenarios, "boundary_categories").status === "ready",
      runtimeAuthorityChanged: false,
      releaseBlockingSecurityViolations: policyViolations.length,
      nextPackagedGate: "P14-34",
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Security Regression Corpus",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Security decision: \`${report.decision.securityDecision}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- Capability coverage ready: \`${report.decision.capabilityCoverageReady}\``,
    `- Boundary coverage ready: \`${report.decision.boundaryCoverageReady}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Release-blocking security violations: \`${report.decision.releaseBlockingSecurityViolations}\``,
    `- Next packaged gate: \`${report.decision.nextPackagedGate}\``,
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

function capabilityReady(entry) {
  return Boolean(entry) &&
    REQUIRED_CASE_FIELDS.every((field) => Array.isArray(entry[field]) && entry[field].length > 0) &&
    typeof entry.rollback === "string" &&
    entry.rollback.length > 0 &&
    Array.isArray(entry.sourceGates) &&
    entry.sourceGates.length > 0;
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

function textToken(file, expected) {
  return token(file, expected);
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

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((value) => actual.includes(value));
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
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
      console.log("Usage: node verify-node-phase14-security-regression-corpus.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
