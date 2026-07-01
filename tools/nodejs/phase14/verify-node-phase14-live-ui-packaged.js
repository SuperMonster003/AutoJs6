#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-live-ui-provider-packaged";
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "packaged_task_relaunch",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK task relaunch smoke with the UI handle closed or restored intentionally.",
  },
  {
    id: "packaged_crash_recovery",
    classification: "future_promotion",
    requiredEvidence: "Packaged provider crash recovery and circuit-breaker evidence with no leaked UI handles.",
  },
  {
    id: "packaged_user_visible_disclosure",
    classification: "future_promotion",
    requiredEvidence: "Reviewed packaged disclosure copy for live UI launch, stop, relaunch, and cleanup behavior.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  promotionContractsDoc: "docs/nodejs/phase14/PHASE14_PROMOTION_CONTRACTS.md",
  phase13Report: "docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md",
  phase13Verifier: "tools/nodejs/phase13/verify-live-ui-provider-promotion.js",
  phase13Gradle: "app/node-phase13-gate.gradle.kts",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  uiDesign: "docs/nodejs/design/UI_BRIDGE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  privacyPolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
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
  console.log("Phase 14 packaged live UI provider: " + report.decision.status);
  console.log("Phase 14 packaged live UI provider blockers: " + report.blockers.length);
  console.log("Phase 14 packaged live UI provider policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const phase13Generated = path.join(reportDir, "phase13-live-ui-provider-promotion.json");
  entries.push([
    "phase13GeneratedReport",
    {
      relative: path.relative(repoRoot, phase13Generated).replace(/\\/g, "/"),
      exists: fs.existsSync(phase13Generated),
      text: read(phase13Generated),
    },
  ]);
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const scenarios = [
    scenario("phase13_packaged_capability_boundary", "P13 packaged capability denial evidence is imported", [
      token(files.phase13Report, "Packaged capability boundary:"),
      token(files.phase13GeneratedReport, '"packagedCapabilityBoundaryReady": true'),
      token(files.packagedTest, "installAndRunUiCapabilityBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedTest, 'denied.code === "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"'),
      token(files.packagedTest, "packaged.ui_boundary.capability_denied=true"),
      token(files.packagedTest, "packaged.ui_boundary.metadata_denied=true"),
      token(files.packagedTest, "packaged.ui_boundary.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.ui_boundary.pending=0"),
    ]),
    scenario("phase13_packaged_entry_close", "P13 packaged UI entry/update/close smoke evidence is imported", [
      token(files.phase13Report, "Packaged entry/close smoke:"),
      token(files.phase13GeneratedReport, '"packagedEntryCloseReady": true'),
      token(files.packagedTest, "installAndRunUiBridgeEmbeddedNodePackagedApk"),
      token(files.packagedTest, "packaged.ui_bridge.ready=true"),
      token(files.packagedTest, "packaged.ui_bridge.shown=true"),
      token(files.packagedTest, "packaged.ui_bridge.update=true"),
      token(files.packagedTest, "packaged.ui_bridge.close=true"),
      token(files.packagedTest, "packaged.ui_bridge.raw_globals_absent=true"),
      token(files.packagedTest, "packaged.ui_bridge.pending=0"),
    ]),
    scenario("phase14_disclosure_and_rollback_contract", "Phase 14 live UI uses the shared disclosure and rollback contract", [
      token(files.promotionContractsJson, '"live_ui"'),
      token(files.promotionContractsJson, '"taskRemovalCleanup"'),
      token(files.promotionContractsJson, '"relaunchBehavior"'),
      token(files.promotionContractsJson, '"crashRecoveryBehavior"'),
      token(files.promotionContractsJson, '"privacyDisclosure"'),
      token(files.promotionContractsJson, '"rollbackBehavior"'),
      token(files.promotionContractsDoc, "`live_ui`"),
      token(files.privacyPolicy, "`live_ui`"),
      token(files.rollbackPlaybook, "`bridge_provider_crash`"),
      token(files.rollbackPlaybook, "`safe_default`"),
    ]),
    scenario("phase14_provider_poc_boundary", "Provider remains POC and must not claim v1.2 opt-in promotion", [
      token(files.providerRegistry, "blocked_until_live_packaged_task_relaunch_crash_disclosure_evidence"),
      token(files.uiDesign, "P14-07 records a partial packaged live UI gate"),
      token(files.securityModel, "P14-07 imports this state as a `provider_poc`"),
      token(files.phase14Report, "Status: partial"),
      token(files.phase14Report, "promotionState: provider_poc"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("phase14_docs_and_gradle_gate", "Phase 14 docs and Gradle wiring expose the P14-07 gate", [
      token(files.gradle14, "verifyNodePhase14LiveUiProviderPackaged"),
      token(files.gradle14, "verify-node-phase14-live-ui-packaged.js"),
      token(files.phase14Tasks, "Partial gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14LiveUiProviderPackaged"),
      token(files.testing, ":app:verifyNodePhase14LiveUiProviderPackaged"),
      token(files.ciGates, ":app:verifyNodePhase14LiveUiProviderPackaged"),
      token(files.surfaceManifestDoc, "`pro.ui_automation_media` | `provider_poc`"),
    ]),
  ];
  const checks = [
    check("phase13-generated-report-present", files.phase13GeneratedReport.exists, "The Phase 13 live UI provider report must be generated before the P14-07 gate."),
    check("phase14-report-present", files.phase14Report.exists && files.phase14Report.text.includes("P14-07"), "P14-07 report must exist."),
    check(
      "surface-provider-poc-not-promoted",
      proSurface &&
        proSurface.status === "provider_poc" &&
        !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must stay provider_poc, not opt-in or default promoted.",
    ),
    check(
      "surface-evidence",
      proSurface &&
        includesAll(proSurface.evidence || [], [
          "docs/nodejs/reports/phase14/P14_07_LIVE_UI_PROVIDER_PACKAGED.md",
          "tools/nodejs/phase14/verify-node-phase14-live-ui-packaged.js",
          ":app:verifyNodePhase14LiveUiProviderPackaged",
          "build/reports/nodejs/phase14-live-ui-provider-packaged.json",
          "build/reports/nodejs/phase14-live-ui-provider-packaged.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-07 report, verifier, Gradle gate, and output reports.",
    ),
    check(
      "contracts-cover-live-ui-packaged-requirements",
      includesAll(disclosure.requiredForSurfaces || [], ["live_ui"]) &&
        includesAll(disclosure.requiredFields || [], [
          "taskRemovalCleanup",
          "relaunchBehavior",
          "crashRecoveryBehavior",
          "privacyDisclosure",
          "rollbackBehavior",
        ]),
      "Phase 14 promotion contracts must cover live_ui packaged cleanup, relaunch, crash recovery, disclosure, and rollback fields.",
    ),
    check(
      "task-plan-keeps-p14-07-open",
      files.phase14Tasks.text.includes("- [ ] P14-07:") &&
        files.phase14Tasks.text.includes("Partial gate deployed:") &&
        files.phase14Tasks.text.includes("packaged_task_relaunch") &&
        files.phase14Tasks.text.includes("packaged_crash_recovery") &&
        files.phase14Tasks.text.includes("packaged_user_visible_disclosure"),
      "P14-07 must remain unchecked while the partial gate records the three open packaged blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14LiveUiProviderPackaged") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14LiveUiProviderPackaged") &&
        files.testing.text.includes(":app:verifyNodePhase14LiveUiProviderPackaged") &&
        files.ciGates.text.includes(":app:verifyNodePhase14LiveUiProviderPackaged"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-07 packaged live UI gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-live-ui-provider-packaged-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14LiveUiProviderPackaged",
    task: "P14-07",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.ui_automation_media",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({
      ...entry,
      status: "open",
    })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      packagedCapabilityBoundaryReady: scenarioById(scenarios, "phase13_packaged_capability_boundary").status === "ready",
      packagedEntryCloseReady: scenarioById(scenarios, "phase13_packaged_entry_close").status === "ready",
      contractsReady: scenarioById(scenarios, "phase14_disclosure_and_rollback_contract").status === "ready",
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: OPEN_BLOCKERS.length,
      proSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function parseJson(text) {
  try {
    return JSON.parse(stripBom(text || "{}"));
  } catch (_) {
    return {};
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Live UI Provider",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "None.")} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  lines.push("", "## Open Blockers", "", "| Blocker | Classification | Required Evidence |", "| --- | --- | --- |");
  report.blockers.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.classification}\` | ${escapeTable(entry.requiredEvidence)} |`);
  });
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase14-live-ui-packaged.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

main();
