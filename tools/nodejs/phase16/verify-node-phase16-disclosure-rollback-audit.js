#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase16-disclosure-rollback-audit";
const REQUIRED_DISCLOSURE_FIELDS = Object.freeze([
  "highPrivilegePrompt",
  "manifestMetadata",
  "installReviewCopy",
  "runReviewCopy",
  "foregroundNotificationOwnership",
  "stopAction",
  "permissionDeniedDiagnostic",
  "undeclaredCapabilityDiagnostic",
  "privacyDisclosure",
  "taskRemovalCleanup",
  "relaunchBehavior",
  "crashRecoveryBehavior",
  "safeDowngradeDisclosure",
]);
const REQUIRED_ROLLBACK_FIELDS = Object.freeze([
  "disableFlag",
  "disableScope",
  "diagnosticCode",
  "userVisibleMessage",
  "safeFallback",
  "packagedDowngradeBehavior",
  "dataRetention",
  "reEnablePath",
  "nodeDoctorSignal",
  "crashCircuitBreakerMarker",
]);
const REQUIRED_MARKER_FIELDS = Object.freeze([
  "providerId",
  "profileId",
  "capabilityId",
  "packageIdentity",
  "crashCount",
  "firstCrashAt",
  "lastCrashAt",
  "disableReason",
]);
const REQUIRED_PROVIDER_IDS = Object.freeze([
  "live_ui",
  "overlay",
  "accessibility",
  "media_projection",
  "image_ocr",
  "task_database",
  "intent_broadcast",
  "notifications",
  "settings_power",
  "recorder",
  "mediastore_playback",
]);
const REQUIRED_PROVIDER_TASKS = Object.freeze({
  live_ui: ["P16-04"],
  overlay: ["P16-05", "P16-06", "P16-07"],
  accessibility: ["P16-08"],
  media_projection: ["P16-09"],
  image_ocr: ["P16-10"],
  task_database: ["P16-12", "P16-13", "P16-14"],
  intent_broadcast: ["P16-15", "P16-16"],
  notifications: ["P16-17"],
  settings_power: ["P16-18"],
  recorder: ["P16-19", "P16-20"],
  mediastore_playback: ["P16-21"],
});
const REQUIRED_BACKLOG_TASKS = Object.freeze([
  "P15-04",
  "P15-05",
  "P15-06",
  "P15-07",
  "P15-08",
  "P15-09",
  "P15-10",
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
]);

const FILES = {
  auditJson: "docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json",
  auditDocs: "docs/nodejs/phase16/PHASE16_NODE_V1_4_DISCLOSURE_ROLLBACK_AUDIT.md",
  contractsJson: "docs/nodejs/phase16/node-v1-4-promotion-contracts.json",
  contractsReport: "build/reports/nodejs/phase16-promotion-contracts.json",
  surfaceManifest: "docs/nodejs/phase16/phase16-surface-manifest.json",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  p16Report: "docs/nodejs/reports/phase16/P16_03_DISCLOSURE_ROLLBACK_AUDIT.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  security: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  phase16Gradle: "app/node-phase16-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const audit = parseJson(files.auditJson.text);
  const report = {
    schema: "autojs6-node-phase16-disclosure-rollback-audit-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase16DisclosureRollbackAudit",
    task: "P16-03",
    status: policyViolations.length === 0 ? "passed" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    auditCounts: {
      providers: countArray(audit.providerAudits),
      disclosureFields: countArray(audit.requiredDisclosureFields),
      rollbackFields: countArray(audit.requiredRollbackFields),
      crashMarkerFields: countArray(audit.crashCircuitBreaker && audit.crashCircuitBreaker.markerFields),
    },
    providerStates: countBy(audit.providerAudits || [], (entry) => entry.currentState),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      disclosureCoverageReady: checkById(checks, "disclosure-field-coverage"),
      rollbackCoverageReady: checkById(checks, "rollback-field-coverage"),
      providerAuditReady: checkById(checks, "provider-audit-coverage"),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 16 disclosure rollback audit: " + report.decision.status);
  console.log("Phase 16 disclosure rollback policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files) {
  const audit = parseJson(files.auditJson.text);
  const contracts = parseJson(files.contractsJson.text);
  const contractsReport = parseJson(files.contractsReport.text);
  const surfaceManifest = parseJson(files.surfaceManifest.text);
  const audits = audit.providerAudits || [];
  const auditIds = audits.map((entry) => entry.providerId);
  const auditBacklog = audits.flatMap((entry) => entry.sourceBacklogTasks || []);
  const contractIds = (contracts.providerPromotionBars || []).map((entry) => entry.providerId);
  const promotedContracts = (contracts.providerPromotionBars || []).filter((entry) => entry.currentState === "promoted");
  const p1603Mapping = (surfaceManifest.taskMappings || []).find((entry) => entry.task === "P16-03");
  return [
    check(
      "promotion-contracts-passed",
      statusOf(contractsReport) === "passed" &&
        contractsReport.promotedProviders &&
        contractsReport.promotedProviders.length === 0 &&
        promotedContracts.length === 0,
      "Disclosure rollback audit requires passed P16-02 promotion contracts and no promoted providers.",
    ),
    check(
      "schema",
      audit.schema === "autojs6-node-phase16-disclosure-rollback-audit-v1" &&
        audit.phase === "Phase 16: Node v1.4 Provider Promotion" &&
        audit.task === "P16-03" &&
        audit.branch === "feature/nodejs-runtime-v1.4" &&
        audit.runtimeAuthorityChanged === false,
      "Audit must use the Phase 16 disclosure/rollback schema without changing runtime authority.",
    ),
    check(
      "disclosure-field-coverage",
      sameSet(audit.requiredDisclosureFields || [], REQUIRED_DISCLOSURE_FIELDS) &&
        REQUIRED_DISCLOSURE_FIELDS.every((field) => files.auditDocs.text.includes("`" + field + "`")),
      "Audit must define all required high-privilege disclosure fields.",
    ),
    check(
      "rollback-field-coverage",
      sameSet(audit.requiredRollbackFields || [], REQUIRED_ROLLBACK_FIELDS) &&
        REQUIRED_ROLLBACK_FIELDS.every((field) => files.auditDocs.text.includes("`" + field + "`")),
      "Audit must define all required rollback fields.",
    ),
    check(
      "crash-circuit-breaker",
      audit.crashCircuitBreaker &&
        String(audit.crashCircuitBreaker.threshold || "").includes("3 provider crashes within 24h") &&
        String(audit.crashCircuitBreaker.action || "").includes("fall back") &&
        sameSet(audit.crashCircuitBreaker.markerFields || [], REQUIRED_MARKER_FIELDS) &&
        REQUIRED_MARKER_FIELDS.every((field) => files.auditDocs.text.includes("`" + field + "`")),
      "Audit must define crash circuit breaker threshold, fallback action, and marker fields.",
    ),
    check(
      "provider-audit-coverage",
      sameSet(auditIds, REQUIRED_PROVIDER_IDS) &&
        sameSet(auditIds, contractIds) &&
        sameSet(auditBacklog, REQUIRED_BACKLOG_TASKS) &&
        audits.every((entry) =>
          entry.currentState !== "promoted" &&
          sameSet(entry.p16Tasks || [], REQUIRED_PROVIDER_TASKS[entry.providerId] || []) &&
          sameSet(entry.requiredDisclosureFields || [], REQUIRED_DISCLOSURE_FIELDS) &&
          sameSet(entry.requiredRollbackFields || [], REQUIRED_ROLLBACK_FIELDS) &&
          Array.isArray(entry.disclosureProofPaths) &&
          entry.disclosureProofPaths.length >= 2 &&
          Array.isArray(entry.rollbackProofPaths) &&
          entry.rollbackProofPaths.length >= 2 &&
          String(entry.disableFlag || "").startsWith("node.provider.") &&
          entry.disableScope === "per-provider and per-packaged-project" &&
          String(entry.diagnosticCode || "").startsWith("NODE_PROVIDER_") &&
          entry.foregroundNotificationRequired === true &&
          entry.stopActionRequired === true &&
          entry.crashCircuitBreakerRequired === true),
      "Every promotable provider must declare disclosure proof paths, rollback proof paths, disable flag, diagnostics, Safe fallback, stop action, and crash breaker requirements.",
    ),
    check(
      "surface-manifest-wiring",
      p1603Mapping &&
        p1603Mapping.ownerSurface === "phase16.disclosure_rollback_audit" &&
        p1603Mapping.evidenceClass === "disclosure_rollback_audit" &&
        p1603Mapping.status === "ready" &&
        p1603Mapping.gate === ":app:verifyNodePhase16DisclosureRollbackAudit",
      "Surface manifest must mark P16-03 ready under the disclosure rollback audit owner surface.",
    ),
    check(
      "task-plan-wiring",
      includesAll(files.phase16Tasks.text, [
        "- [x] P16-03: Add v1.4 disclosure and rollback audit gate.",
        ":app:verifyNodePhase16DisclosureRollbackAudit",
        "node-v1-4-disclosure-rollback-audit.json",
        "phase16-disclosure-rollback-audit.json",
      ]),
      "Phase 16 task checklist must mark P16-03 complete and list the disclosure rollback audit artifacts.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase16Readme.text.includes(":app:verifyNodePhase16DisclosureRollbackAudit") &&
        files.p16Report.text.includes(":app:verifyNodePhase16DisclosureRollbackAudit") &&
        files.testing.text.includes(":app:verifyNodePhase16DisclosureRollbackAudit") &&
        files.ciGates.text.includes(":app:verifyNodePhase16DisclosureRollbackAudit") &&
        files.security.text.includes("P16-03") &&
        files.errorCatalog.text.includes("P16-03") &&
        files.phase16Gradle.text.includes("verifyNodePhase16DisclosureRollbackAudit") &&
        files.phase16Gradle.text.includes("verify-node-phase16-disclosure-rollback-audit.js") &&
        files.phase16Gradle.text.includes("verifyNodePhase16PromotionContracts"),
      "README, TESTING, CI_GATES, security/error docs, and Gradle must reference the Phase 16 disclosure rollback audit gate.",
    ),
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 16 Disclosure Rollback Audit",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Audit Counts",
    "",
  ];
  Object.keys(report.auditCounts).sort().forEach((key) => {
    lines.push(`- \`${key}\`: ${report.auditCounts[key]}`);
  });
  lines.push("", "## Provider States", "");
  Object.keys(report.providerStates).sort().forEach((state) => {
    lines.push(`- \`${state}\`: ${report.providerStates[state]}`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- ${entry}`);
    });
  }
  return lines.join("\n") + "\n";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || !Array.isArray(expected) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function countArray(value) {
  return Array.isArray(value) ? value.length : 0;
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const entry = checks.find((checkItem) => checkItem.id === id);
  return entry ? entry.passed : false;
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
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
      console.log("Usage: node verify-node-phase16-disclosure-rollback-audit.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
