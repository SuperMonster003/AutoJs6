#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-promotion-contracts";
const REQUIRED_STATES = Object.freeze(["promoted", "provider_poc", "future_only", "stable_denied", "rejected"]);
const REQUIRED_EVIDENCE = Object.freeze([
  "implementation_gate",
  "connected_device_evidence",
  "packaged_apk_evidence",
  "user_visible_disclosure",
  "rollback_disable_path",
  "security_regression_corpus",
  "typescript_declaration_profile",
  "migration_compat_release_docs",
  "safe_downgrade_behavior",
  "raw_object_denial_regression",
]);
const REQUIRED_PROVIDER_BARS = Object.freeze([
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
const REQUIRED_BACKLOG_TASKS = Object.freeze([
  "P14-07",
  "P14-08",
  "P14-09",
  "P14-10",
  "P14-11",
  "P14-12",
  "P14-13",
  "P14-14",
  "P14-15",
  "P14-17",
  "P14-18",
  "P14-19",
  "P14-20",
  "P14-21",
  "P14-22",
  "P14-23",
  "P14-24",
  "P14-25",
  "P14-26",
]);
const REQUIRED_PROVIDER_TASKS = Object.freeze([
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
const REQUIRED_REPORT_FIELDS = Object.freeze([
  "schema",
  "generatedAt",
  "gate",
  "task",
  "status",
  "checks",
  "policyViolations",
  "blockers",
  "decision",
]);

const FILES = {
  contractsJson: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  contractsDocs: "docs/nodejs/phase15/PHASE15_NODE_V1_3_PROMOTION_CONTRACTS.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  surfaceReport: "build/reports/nodejs/phase15-surface-manifest-verification.json",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const contracts = parseJson(files.contractsJson.text);
  const report = {
    schema: "autojs6-node-phase15-promotion-contracts-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15PromotionContracts",
    task: "P15-02",
    status: policyViolations.length === 0 ? "ready" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    contractCounts: {
      promotionStates: countArray(contracts.promotionStateVocabulary),
      requiredEvidence: countArray(contracts.optInPromotionRequiredEvidence),
      providerPromotionBars: countArray(contracts.providerPromotionBars),
      stateTransitions: countArray(contracts.stateTransitions),
    },
    providerStates: countBy(contracts.providerPromotionBars || [], (entry) => entry.currentState),
    checks,
    policyViolations,
    blockers: [],
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      vocabularyReady: checkById(checks, "promotion-state-vocabulary"),
      optInBarsReady: checkById(checks, "opt-in-promotion-bars"),
      providerBarsReady: checkById(checks, "provider-bars"),
      runtimeAuthorityChanged: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 15 promotion contracts: " + report.decision.status);
  console.log("Phase 15 promotion contract policy violations: " + policyViolations.length);
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
  const contracts = parseJson(files.contractsJson.text);
  const surfaceManifest = parseJson(files.surfaceManifest.text);
  const surfaceReport = parseJson(files.surfaceReport.text);
  const stateIds = (contracts.promotionStateVocabulary || []).map((entry) => entry.id);
  const providerBars = contracts.providerPromotionBars || [];
  const providerIds = providerBars.map((entry) => entry.id);
  const providerBacklog = providerBars.flatMap((entry) => entry.sourceBacklogTasks || []);
  const providerTasks = providerBars.flatMap((entry) => entry.p15Tasks || []);
  const reportConventions = contracts.reportConventions || {};
  const p15Mapping = (surfaceManifest.taskMappings || []).find((entry) => entry.task === "P15-02");
  return [
    check(
      "surface-manifest-passed",
      statusOf(surfaceReport) === "passed" &&
        surfaceReport.decision &&
        surfaceReport.decision.manifestComplete === true &&
        surfaceReport.decision.backlogOwned === true,
      "Phase 15 promotion contracts require the P15-01 surface manifest gate to pass.",
    ),
    check(
      "schema",
      contracts.schema === "autojs6-node-phase15-promotion-contracts-v1" &&
        contracts.phase === "Phase 15: Node v1.3 Provider Promotion" &&
        contracts.task === "P15-02" &&
        contracts.branch === "feature/nodejs-runtime-v1.3",
      "Promotion contracts must use the Phase 15 v1.3 schema, task, and branch.",
    ),
    check(
      "promotion-state-vocabulary",
      sameSet(stateIds, REQUIRED_STATES) &&
        REQUIRED_STATES.every((state) => files.contractsDocs.text.includes("`" + state + "`")),
      "Promotion contracts must define promoted, provider_poc, future_only, stable_denied, and rejected.",
    ),
    check(
      "opt-in-promotion-bars",
      sameSet(contracts.optInPromotionRequiredEvidence || [], REQUIRED_EVIDENCE) &&
        REQUIRED_EVIDENCE.every((item) => files.contractsDocs.text.includes("`" + item + "`")),
      "Opt-in promoted providers must require implementation, device, packaged, disclosure, rollback, security, docs, type, Safe downgrade, and raw-object denial evidence.",
    ),
    check(
      "provider-bars",
      sameSet(providerIds, REQUIRED_PROVIDER_BARS) &&
        sameSet(providerBacklog, REQUIRED_BACKLOG_TASKS) &&
        sameSet(providerTasks, REQUIRED_PROVIDER_TASKS) &&
        providerBars.every((entry) =>
          REQUIRED_STATES.includes(entry.currentState) &&
          entry.currentState !== "promoted" &&
          sameSet(entry.requiredEvidence || [], REQUIRED_EVIDENCE) &&
          typeof entry.rollbackHook === "string" &&
          entry.rollbackHook.length >= 8),
      "Every promotable provider must be mapped to backlog tasks and stay unpromoted until all evidence bars pass.",
    ),
    check(
      "state-transitions",
      Array.isArray(contracts.stateTransitions) &&
        contracts.stateTransitions.some((entry) => entry.from === "provider_poc" && entry.to === "promoted") &&
        contracts.stateTransitions.some((entry) => entry.from === "stable_denied" && entry.to === "provider_poc") &&
        contracts.stateTransitions.every((entry) => REQUIRED_STATES.includes(entry.from) && REQUIRED_STATES.includes(entry.to)),
      "State transitions must define controlled movement toward promoted without skipping evidence bars.",
    ),
    check(
      "report-conventions",
      reportConventions.reportNamePattern === "phase15-<surface-or-task>.json" &&
        reportConventions.markdownMirrorRequired === true &&
        reportConventions.schemaPattern === "autojs6-node-phase15-<surface-or-task>-v1" &&
        includesAll(reportConventions.requiredReportFields || [], REQUIRED_REPORT_FIELDS) &&
        includesAll(reportConventions.statusValues || [], ["passed", "ready", "partial", "failed", "blocked", "archived"]) &&
        includesAll(reportConventions.blockerClassifications || [], ["release_blocking", "future_promotion", "external", "intentional_non_goal"]),
      "Phase 15 report conventions must define deterministic names, mirrors, required fields, statuses, and blocker classifications.",
    ),
    check(
      "guard-rails",
      contracts.guardRails &&
        contracts.guardRails.safeProfileDefault === true &&
        contracts.guardRails.runtimeAuthorityChangedByThisContract === false &&
        contracts.guardRails.rawAndroidObjectParityRejected === true &&
        contracts.guardRails.optInPromotionRequiresAllBars === true &&
        contracts.guardRails.missingEvidenceFallbackState === "future_only" &&
        contracts.guardRails.stableDenialFallbackState === "stable_denied",
      "Promotion contracts must preserve Safe default behavior, raw Android object denial, and fail-closed fallback states.",
    ),
    check(
      "surface-manifest-wiring",
      p15Mapping &&
        p15Mapping.ownerSurface === "phase15.promotion_contracts" &&
        p15Mapping.evidenceClass === "promotion_contract" &&
        p15Mapping.status === "ready" &&
        p15Mapping.gate === ":app:verifyNodePhase15PromotionContracts",
      "Surface manifest must mark P15-02 ready under the promotion contract owner surface.",
    ),
    check(
      "task-plan-wiring",
      includesAll(files.phase15Tasks.text, [
        "- [x] P15-02: Refresh v1.3 opt-in promotion bars.",
        ":app:verifyNodePhase15PromotionContracts",
        "node-v1-3-promotion-contracts.json",
        "phase15-promotion-contracts.json",
      ]),
      "Phase 15 task checklist must mark P15-02 complete and list the promotion contract artifacts.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Readme.text.includes(":app:verifyNodePhase15PromotionContracts") &&
        files.testing.text.includes(":app:verifyNodePhase15PromotionContracts") &&
        files.ciGates.text.includes(":app:verifyNodePhase15PromotionContracts") &&
        files.phase15Gradle.text.includes("verifyNodePhase15PromotionContracts") &&
        files.phase15Gradle.text.includes("verify-node-phase15-promotion-contracts.js") &&
        files.phase15Gradle.text.includes("verifyNodePhase15SurfaceManifest"),
      "README, TESTING, CI_GATES, and Gradle must reference the Phase 15 promotion contract gate.",
    ),
  ];
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Promotion Contracts",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Contract Counts",
    "",
  ];
  Object.keys(report.contractCounts).sort().forEach((key) => {
    lines.push(`- \`${key}\`: ${report.contractCounts[key]}`);
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

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
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
      console.log("Usage: node verify-node-phase15-promotion-contracts.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
