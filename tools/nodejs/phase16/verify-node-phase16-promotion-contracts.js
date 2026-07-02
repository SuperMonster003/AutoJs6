#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const REQUIRED_STATES = [
  "promoted",
  "provider_poc",
  "future_only",
  "stable_denied",
  "rejected",
];

const REQUIRED_EVIDENCE = [
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
];

const REQUIRED_PROVIDER_BARS = [
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
];

const REQUIRED_PROVIDER_TASKS = {
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
};

const REQUIRED_BACKLOG_TASKS = {
  live_ui: ["P15-04"],
  overlay: ["P15-05", "P15-06", "P15-07"],
  accessibility: ["P15-08"],
  media_projection: ["P15-09"],
  image_ocr: ["P15-10"],
  task_database: ["P15-12", "P15-13", "P15-14"],
  intent_broadcast: ["P15-15", "P15-16"],
  notifications: ["P15-17"],
  settings_power: ["P15-18"],
  recorder: ["P15-19", "P15-20"],
  mediastore_playback: ["P15-21"],
};

const FILES = {
  contracts: "docs/nodejs/phase16/node-v1-4-promotion-contracts.json",
  contractsDoc: "docs/nodejs/phase16/PHASE16_NODE_V1_4_PROMOTION_CONTRACTS.md",
  surfaceManifest: "docs/nodejs/phase16/phase16-surface-manifest.json",
  surfaceReport: "build/reports/nodejs/phase16-surface-manifest-verification.json",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  p16Report: "docs/nodejs/reports/phase16/P16_02_PROMOTION_CONTRACTS.md",
  testing: "docs/nodejs/TESTING.md",
  ci: "docs/nodejs/CI_GATES.md",
  security: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  gradle: "app/node-phase16-gate.gradle.kts",
};

function parseArgs(argv) {
  const args = {
    repoRoot: process.cwd(),
    reportDir: path.join(process.cwd(), "build", "reports", "nodejs"),
  };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--repo-root") {
      args.repoRoot = argv[++i];
    } else if (arg === "--report-dir") {
      args.reportDir = argv[++i];
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return args;
}

function readText(repoRoot, relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function readJson(repoRoot, relativePath) {
  return JSON.parse(readText(repoRoot, relativePath));
}

function includesAll(values, required) {
  return required.every((value) => values.includes(value));
}

function sameSet(actual, expected) {
  return actual.length === expected.length && includesAll(actual, expected);
}

function addIssue(issues, id, message) {
  issues.push({ id, message });
}

function requireText(text, token, issues, id, file) {
  if (!text.includes(token)) {
    addIssue(issues, id, `${file} does not contain ${token}`);
  }
}

function validateSurfaceManifest(surfaceManifest, issues) {
  const p1602 = surfaceManifest.taskMappings.find((mapping) => mapping.task === "P16-02");
  if (!p1602) {
    addIssue(issues, "surface-missing-p16-02", "Surface manifest does not contain P16-02");
    return;
  }
  if (p1602.gate !== ":app:verifyNodePhase16PromotionContracts") {
    addIssue(issues, "surface-gate-mismatch", "P16-02 gate does not match verifyNodePhase16PromotionContracts");
  }
  if (p1602.status !== "ready") {
    addIssue(issues, "surface-status-not-ready", "P16-02 surface manifest status must be ready");
  }
  if (p1602.evidenceClass !== "promotion_contract") {
    addIssue(issues, "surface-evidence-class", "P16-02 evidence class must be promotion_contract");
  }
}

function validateSurfaceReport(surfaceReport, issues) {
  if (statusOf(surfaceReport) !== "passed") {
    addIssue(issues, "surface-report-not-passed", "Phase 16 surface manifest verification report must be passed");
  }
}

function validateContractShape(contracts, issues) {
  if (contracts.schema !== "autojs6-node-phase16-promotion-contracts-v1") {
    addIssue(issues, "schema", "Promotion contract schema mismatch");
  }
  if (contracts.phase !== "Phase 16: Node v1.4 Provider Promotion") {
    addIssue(issues, "phase", "Promotion contract phase mismatch");
  }
  if (contracts.task !== "P16-02") {
    addIssue(issues, "task", "Promotion contract task must be P16-02");
  }
  if (contracts.branch !== "feature/nodejs-runtime-v1.4") {
    addIssue(issues, "branch", "Promotion contract branch must be feature/nodejs-runtime-v1.4");
  }
  if (!sameSet(contracts.promotionStateVocabulary || [], REQUIRED_STATES)) {
    addIssue(issues, "state-vocabulary", "Promotion contract state vocabulary is incomplete or contains unexpected values");
  }
  if (!sameSet(contracts.optInPromotionRequiredEvidence || [], REQUIRED_EVIDENCE)) {
    addIssue(issues, "required-evidence", "Opt-in promotion evidence bars are incomplete or contain unexpected values");
  }
}

function validateStateTransitions(contracts, issues) {
  const transitions = contracts.stateTransitions || [];
  const promotionTransition = transitions.find((transition) => transition.from === "provider_poc" && transition.to === "promoted");
  if (!promotionTransition) {
    addIssue(issues, "promotion-transition-missing", "provider_poc -> promoted transition is missing");
    return;
  }
  if (!includesAll(promotionTransition.requires || [], REQUIRED_EVIDENCE)) {
    addIssue(issues, "promotion-transition-evidence", "provider_poc -> promoted transition does not require all promotion evidence");
  }
  for (const transition of transitions) {
    if (!REQUIRED_STATES.includes(transition.from) || !REQUIRED_STATES.includes(transition.to)) {
      addIssue(issues, "transition-state", `Transition uses unknown state ${transition.from} -> ${transition.to}`);
    }
  }
}

function validateProviderBars(contracts, issues) {
  const bars = contracts.providerPromotionBars || [];
  const providerIds = bars.map((bar) => bar.providerId);
  if (!sameSet(providerIds, REQUIRED_PROVIDER_BARS)) {
    addIssue(issues, "provider-bars", "Provider promotion bars do not match the required provider set");
  }
  for (const providerId of REQUIRED_PROVIDER_BARS) {
    const bar = bars.find((candidate) => candidate.providerId === providerId);
    if (!bar) {
      continue;
    }
    if (!REQUIRED_STATES.includes(bar.currentState)) {
      addIssue(issues, `provider-state-${providerId}`, `${providerId} uses an unknown currentState`);
    }
    if (providerId === "mediastore_playback" && bar.currentState !== "stable_denied") {
      addIssue(issues, "mediastore-state", "mediastore_playback must remain stable_denied in P16-02");
    }
    if (providerId !== "mediastore_playback" && bar.currentState === "promoted") {
      addIssue(issues, `provider-promoted-${providerId}`, `${providerId} cannot be promoted by the P16-02 contract`);
    }
    if (!sameSet(bar.p16Tasks || [], REQUIRED_PROVIDER_TASKS[providerId])) {
      addIssue(issues, `provider-p16-tasks-${providerId}`, `${providerId} does not map to the expected P16 tasks`);
    }
    if (!sameSet(bar.sourceBacklogTasks || [], REQUIRED_BACKLOG_TASKS[providerId])) {
      addIssue(issues, `provider-backlog-${providerId}`, `${providerId} does not map to the expected P15 backlog tasks`);
    }
    if (!includesAll(bar.requiredEvidence || [], REQUIRED_EVIDENCE)) {
      addIssue(issues, `provider-evidence-${providerId}`, `${providerId} does not require every promotion evidence bar`);
    }
    if (typeof bar.rollbackHook !== "string" || bar.rollbackHook.length === 0) {
      addIssue(issues, `provider-rollback-${providerId}`, `${providerId} is missing a rollback hook`);
    }
    if (
      typeof bar.safeDefaultBehavior !== "string" ||
      (!bar.safeDefaultBehavior.includes("deny") && !bar.safeDefaultBehavior.includes("denied"))
    ) {
      addIssue(issues, `provider-safe-default-${providerId}`, `${providerId} must describe Safe default denial behavior`);
    }
  }
}

function validateReportConventions(contracts, issues) {
  const conventions = contracts.reportConventions || {};
  if (conventions.reportNamePrefix !== "phase16-") {
    addIssue(issues, "report-prefix", "Report prefix must be phase16-");
  }
  if (conventions.schemaPattern !== "autojs6-node-phase16-<surface-or-task>-v1") {
    addIssue(issues, "schema-pattern", "Report schema pattern must stay under autojs6-node-phase16");
  }
  if (!includesAll(conventions.requiredProviderFields || [], [
    "providerId",
    "currentState",
    "p16Tasks",
    "sourceBacklogTasks",
    "requiredEvidence",
    "rollbackHook",
    "safeDefaultBehavior",
  ])) {
    addIssue(issues, "required-provider-fields", "Report convention is missing required provider fields");
  }
}

function validateGuardRails(contracts, issues) {
  const guardRails = contracts.guardRails || {};
  for (const key of [
    "safeProfileDefault",
    "noRuntimeAuthorityByDefault",
    "rawAndroidObjectsDenied",
    "packagedApkParityRequired",
    "connectedDeviceEvidenceRequired",
    "rollbackRequiredBeforePromotion",
    "securityCorpusRequiredBeforePromotion",
    "typeDeclarationRequiredBeforePromotion",
    "releaseDocsRequiredBeforePromotion",
  ]) {
    if (guardRails[key] !== true) {
      addIssue(issues, `guardrail-${key}`, `Guard rail ${key} must be true`);
    }
  }
}

function validateDocs(repoRoot, issues) {
  const docs = {
    contractsDoc: readText(repoRoot, FILES.contractsDoc),
    phase16Tasks: readText(repoRoot, FILES.phase16Tasks),
    phase16Readme: readText(repoRoot, FILES.phase16Readme),
    p16Report: readText(repoRoot, FILES.p16Report),
    testing: readText(repoRoot, FILES.testing),
    ci: readText(repoRoot, FILES.ci),
    security: readText(repoRoot, FILES.security),
    errorCatalog: readText(repoRoot, FILES.errorCatalog),
    gradle: readText(repoRoot, FILES.gradle),
  };

  for (const state of REQUIRED_STATES) {
    requireText(docs.contractsDoc, `\`${state}\``, issues, `doc-state-${state}`, FILES.contractsDoc);
  }
  for (const evidence of REQUIRED_EVIDENCE) {
    requireText(docs.contractsDoc, `\`${evidence}\``, issues, `doc-evidence-${evidence}`, FILES.contractsDoc);
  }

  requireText(docs.phase16Tasks, "- [x] P16-02", issues, "task-checkbox", FILES.phase16Tasks);
  requireText(docs.phase16Tasks, "node-v1-4-promotion-contracts.json", issues, "task-contract-artifact", FILES.phase16Tasks);
  requireText(docs.phase16Tasks, "phase16-promotion-contracts.json", issues, "task-report-artifact", FILES.phase16Tasks);
  requireText(docs.phase16Readme, ":app:verifyNodePhase16PromotionContracts", issues, "readme-gate", FILES.phase16Readme);
  requireText(docs.p16Report, ":app:verifyNodePhase16PromotionContracts", issues, "p16-report-gate", FILES.p16Report);
  requireText(docs.testing, ":app:verifyNodePhase16PromotionContracts", issues, "testing-gate", FILES.testing);
  requireText(docs.ci, ":app:verifyNodePhase16PromotionContracts", issues, "ci-gate", FILES.ci);
  requireText(docs.security, "P16-02", issues, "security-p16-02", FILES.security);
  requireText(docs.errorCatalog, "P16-02", issues, "error-p16-02", FILES.errorCatalog);
  requireText(docs.gradle, "verifyNodePhase16PromotionContracts", issues, "gradle-task", FILES.gradle);
  requireText(docs.gradle, "verify-node-phase16-promotion-contracts.js", issues, "gradle-script", FILES.gradle);
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function writeReports(reportDir, report) {
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(
    path.join(reportDir, "phase16-promotion-contracts.json"),
    `${JSON.stringify(report, null, 2)}\n`,
  );
  const markdown = [
    "# Phase 16 Promotion Contracts Verification",
    "",
    `Status: ${report.status}`,
    "",
    `Checked at: ${report.checkedAt}`,
    "",
    `Providers: ${report.providerStates.length}`,
    "",
    "## Provider States",
    "",
    "| Provider | State | Tasks |",
    "| --- | --- | --- |",
    ...report.providerStates.map((provider) => `| ${provider.providerId} | ${provider.currentState} | ${provider.p16Tasks.join(", ")} |`),
    "",
    "## Issues",
    "",
    ...(report.issues.length === 0
      ? ["- None."]
      : report.issues.map((issue) => `- ${issue.id}: ${issue.message}`)),
    "",
  ].join("\n");
  fs.writeFileSync(path.join(reportDir, "phase16-promotion-contracts.md"), markdown);
}

function main() {
  const args = parseArgs(process.argv);
  const repoRoot = path.resolve(args.repoRoot);
  const reportDir = path.resolve(args.reportDir);
  const issues = [];

  const contracts = readJson(repoRoot, FILES.contracts);
  const surfaceManifest = readJson(repoRoot, FILES.surfaceManifest);
  const surfaceReport = readJson(repoRoot, FILES.surfaceReport);

  validateSurfaceReport(surfaceReport, issues);
  validateSurfaceManifest(surfaceManifest, issues);
  validateContractShape(contracts, issues);
  validateStateTransitions(contracts, issues);
  validateProviderBars(contracts, issues);
  validateReportConventions(contracts, issues);
  validateGuardRails(contracts, issues);
  validateDocs(repoRoot, issues);

  const report = {
    schema: "autojs6-node-phase16-promotion-contracts-report-v1",
    phase: "Phase 16: Node v1.4 Provider Promotion",
    task: "P16-02",
    status: issues.length === 0 ? "passed" : "failed",
    checkedAt: new Date().toISOString(),
    inputs: FILES,
    promotedProviders: (contracts.providerPromotionBars || [])
      .filter((provider) => provider.currentState === "promoted")
      .map((provider) => provider.providerId),
    providerStates: (contracts.providerPromotionBars || []).map((provider) => ({
      providerId: provider.providerId,
      currentState: provider.currentState,
      p16Tasks: provider.p16Tasks,
      sourceBacklogTasks: provider.sourceBacklogTasks,
      rollbackHook: provider.rollbackHook,
    })),
    issues,
  };

  writeReports(reportDir, report);

  if (issues.length > 0) {
    console.error(`Phase 16 promotion contract verification failed with ${issues.length} issue(s).`);
    for (const issue of issues) {
      console.error(`- ${issue.id}: ${issue.message}`);
    }
    process.exitCode = 1;
    return;
  }

  console.log(`Phase 16 promotion contract verification passed. Report: ${path.join(reportDir, "phase16-promotion-contracts.json")}`);
}

main();
