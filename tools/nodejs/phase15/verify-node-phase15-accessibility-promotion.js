#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-accessibility-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_accessibility_lifecycle_fixture",
  "phase14_accessibility_disabled_denial_boundary",
  "phase14_accessibility_selector_action_matrix",
  "phase13_accessibility_selector_bridge",
  "phase13_shell_root_shizuku_separation",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_DEVICE_CLASSES = Object.freeze(["android_12_class", "android_15_plus_class"]);
const REQUIRED_BLOCKERS = Object.freeze([
  "accessibility_packaged_service_enabled_archive",
  "accessibility_settings_round_trip_archive",
  "accessibility_task_removal_relaunch_archive",
  "accessibility_disconnect_missing_capability_denial_archive",
  "accessibility_selector_android12_action_archive",
  "accessibility_selector_android15_plus_action_archive",
  "accessibility_selector_global_actions_recents_archive",
  "accessibility_selector_unsupported_tier_diagnostics_archive",
  "phase15_security_type_doc_sync",
]);
const PHASE14_LIFECYCLE_BLOCKERS = Object.freeze([
  "accessibility_packaged_service_enabled_archive",
  "accessibility_settings_round_trip_archive",
  "accessibility_task_removal_relaunch_archive",
  "accessibility_disconnect_missing_capability_denial_archive",
]);
const PHASE14_SELECTOR_BLOCKERS = Object.freeze([
  "accessibility_selector_android12_action_archive",
  "accessibility_selector_android15_plus_action_archive",
  "accessibility_selector_global_actions_recents_archive",
  "accessibility_selector_unsupported_tier_diagnostics_archive",
]);

const FILES = {
  plan: "docs/nodejs/phase15/accessibility-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_ACCESSIBILITY_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_08_ACCESSIBILITY_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  phase14LifecyclePlan: "docs/nodejs/phase14/accessibility-lifecycle-plan.json",
  phase14LifecycleReport: "docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md",
  phase14LifecycleGenerated: "build/reports/nodejs/phase14-accessibility-lifecycle.json",
  phase14SelectorMatrix: "docs/nodejs/phase14/accessibility-selector-action-matrix.json",
  phase14SelectorReport: "docs/nodejs/reports/phase14/P14_12_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md",
  phase14SelectorGenerated: "build/reports/nodejs/phase14-accessibility-selector-action-matrix.json",
  p13AccessibilityReport: "docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md",
  p13AccessibilityGenerated: "build/reports/nodejs/phase13-accessibility-selector-promotion.json",
  p13ShellReport: "docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md",
  p13ShellGenerated: "build/reports/nodejs/phase13-shell-root-shizuku-capability.json",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  accessibilityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt",
  realDeviceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilityRealDeviceSmokeInstrumentationTest.kt",
  smokeActivity: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySmokeActivity.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  shellTypes: "docs/nodejs/types/autojs6-node/shell.d.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 15 accessibility promotion: " + report.decision.status);
  console.log("Phase 15 accessibility blockers: " + report.blockers.length);
  console.log("Phase 15 accessibility policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const plan = parseJson(files.plan.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const lifecyclePlan = parseJson(files.phase14LifecyclePlan.text);
  const lifecycleReport = parseJson(files.phase14LifecycleGenerated.text);
  const selectorMatrix = parseJson(files.phase14SelectorMatrix.text);
  const selectorReport = parseJson(files.phase14SelectorGenerated.text);
  const p13AccessibilityReport = parseJson(files.p13AccessibilityGenerated.text);
  const p13ShellReport = parseJson(files.p13ShellGenerated.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-08") || {};
  const accessibilityContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "accessibility") || {};
  const accessibilityAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "accessibility") || {};
  const selectorRows = Array.isArray(selectorMatrix.evidenceRows) ? selectorMatrix.evidenceRows : [];
  const lifecycleBlockers = (lifecycleReport.blockers || []).map((entry) => entry.id);
  const selectorBlockers = (selectorReport.blockers || []).map((entry) => entry.id);
  const scenarios = [
    scenario("phase14_lifecycle_imported", "P14-11 lifecycle fixture, privacy/settings, and disabled denial boundary are imported", [
      checkTokenValue("lifecycle_plan_schema", lifecyclePlan.schema === "autojs6-node-phase14-accessibility-lifecycle-v1"),
      checkTokenValue("lifecycle_plan_status", lifecyclePlan.status === "partial"),
      checkTokenValue("lifecycle_report_status", statusOf(lifecycleReport) === "partial"),
      checkTokenValue("lifecycle_fixture_ready", lifecycleReport.decision && lifecycleReport.decision.packagedFixtureReady === true),
      checkTokenValue("lifecycle_privacy_ready", lifecycleReport.decision && lifecycleReport.decision.privacySettingsContractReady === true),
      checkTokenValue("lifecycle_denial_ready", lifecycleReport.decision && lifecycleReport.decision.disabledDenialBoundaryReady === true),
      checkTokenValue("lifecycle_archives_open", lifecycleReport.decision && lifecycleReport.decision.requiredDeviceArchivesReady === false),
      checkTokenValue("lifecycle_blockers_imported", PHASE14_LIFECYCLE_BLOCKERS.every((id) => lifecycleBlockers.includes(id))),
      token(files.packagedTest, "buildAccessibilityLifecycleEmbeddedNodePackagedApk"),
      token(files.packagedTest, "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      token(files.phase14LifecycleReport, "The task remains unchecked because packaged install-run archives"),
    ]),
    scenario("phase14_selector_matrix_imported", "P14-12 selector/action matrix and required device rows are imported", [
      checkTokenValue("selector_matrix_schema", selectorMatrix.schema === "autojs6-node-phase14-accessibility-selector-action-matrix-v1"),
      checkTokenValue("selector_matrix_status", selectorMatrix.status === "partial"),
      checkTokenValue("selector_report_status", statusOf(selectorReport) === "partial"),
      checkTokenValue("selector_matrix_ready", selectorReport.decision && selectorReport.decision.matrixReady === true),
      checkTokenValue("selector_facade_imported", selectorReport.decision && selectorReport.decision.selectorFacadeImported === true),
      checkTokenValue("selector_real_device_anchor", selectorReport.decision && selectorReport.decision.realDeviceProfileAnchorReady === true),
      checkTokenValue("selector_denial_tiers_ready", selectorReport.decision && selectorReport.decision.denialAndUnsupportedTiersReady === true),
      checkTokenValue("selector_archives_open", selectorReport.decision && selectorReport.decision.requiredDeviceArchivesReady === false),
      checkTokenValue("selector_required_device_classes", sameSet(selectorMatrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("android_12_row_reserved", hasRow(selectorRows, "android_12_class", "missing_device_archive")),
      checkTokenValue("android_15_plus_row_reserved", hasRow(selectorRows, "android_15_plus_class", "missing_device_archive")),
      checkTokenValue("selector_blockers_imported", PHASE14_SELECTOR_BLOCKERS.every((id) => selectorBlockers.includes(id))),
    ], PHASE14_SELECTOR_BLOCKERS),
    scenario("phase13_selector_and_shell_boundaries", "P13 selector bridge and shell/root/Shizuku separation remain imported", [
      checkTokenValue("p13_accessibility_status", statusOf(p13AccessibilityReport) === "partial"),
      checkTokenValue("p13_direct_selector", p13AccessibilityReport.decision && p13AccessibilityReport.decision.directSelectorBridgeReady === true),
      checkTokenValue("p13_live_gate", p13AccessibilityReport.decision && p13AccessibilityReport.decision.liveAccessibilityGateReady === true),
      checkTokenValue("p13_packaged_not_ready", p13AccessibilityReport.decision && p13AccessibilityReport.decision.packagedAccessibilityProviderReady === false),
      checkTokenValue("p13_shell_status", statusOf(p13ShellReport) === "ready"),
      token(files.p13AccessibilityReport, "`swipe`, `gesture`, and `powerDialog` stay stable-denied"),
      token(files.p13ShellReport, "Shizuku execution stays stable-denied"),
      token(files.providerRegistry, "gesture,root,Shizuku,raw input, and shell fallback paths require separate capabilities"),
      token(files.securityModel, "Reserved future input automation labels split gesture authority away"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep accessibility promotion behind evidence, disclosure, and rollback bars", [
      checkTokenValue("promotion_report_ready", statusOf(promotionReport) === "ready"),
      checkTokenValue("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      checkTokenValue("accessibility_contract_provider_poc", accessibilityContract.currentState === "provider_poc"),
      checkTokenValue("accessibility_contract_p15_task", includesAll(accessibilityContract.p15Tasks || [], ["P15-08"])),
      checkTokenValue("accessibility_contract_evidence_bars", includesAll(accessibilityContract.requiredEvidence || [], [
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "raw_object_denial_regression",
      ])),
      checkTokenValue("accessibility_audit_provider_poc", accessibilityAudit.currentState === "provider_poc"),
      checkTokenValue("accessibility_audit_disable_flag", accessibilityAudit.disableFlag === "node.provider.accessibility.enabled"),
      checkTokenValue("accessibility_audit_diagnostic", accessibilityAudit.diagnosticCode === "NODE_PROVIDER_ACCESSIBILITY_DISABLED"),
      checkTokenValue("accessibility_audit_disclosure_stop", accessibilityAudit.foregroundNotificationRequired === true && accessibilityAudit.stopActionRequired === true),
      checkTokenValue("accessibility_audit_crash_breaker", accessibilityAudit.crashCircuitBreakerRequired === true),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-08 accessibility gate", [
      checkTokenValue("surface_mapping_owner", p15Mapping.ownerSurface === "pro.ui_overlay_automation_media"),
      checkTokenValue("surface_mapping_backlog", includesAll(p15Mapping.sourceBacklogTasks || [], ["P14-11", "P14-12"])),
      checkTokenValue("surface_mapping_evidence_class", p15Mapping.evidenceClass === "device_matrix_archive"),
      checkTokenValue("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15AccessibilityPromotion"),
      checkTokenValue("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15AccessibilityPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-accessibility-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15AccessibilityPromotion"),
      token(files.testing, ":app:verifyNodePhase15AccessibilityPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15AccessibilityPromotion"),
      token(files.phase15Tasks, "phase15-accessibility-promotion.json"),
      token(files.phase15Tasks, "accessibility_selector_android15_plus_action_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-accessibility-promotion-plan-v1" &&
        plan.task === "P15-08" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-11", "P14-12"]) &&
        plan.providerId === "accessibility" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-08 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-08 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        sameSet(plan.requiredPromotionEvidence || [], REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-08 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-lifecycle-ready",
      statusOf(lifecycleReport) === "partial" &&
        lifecycleReport.decision &&
        lifecycleReport.decision.packagedFixtureReady === true &&
        lifecycleReport.decision.privacySettingsContractReady === true &&
        lifecycleReport.decision.disabledDenialBoundaryReady === true &&
        lifecycleReport.decision.requiredDeviceArchivesReady === false,
      "P15-08 requires P14-11 lifecycle report to be partial with device archives still open.",
    ),
    check(
      "phase14-selector-matrix-ready",
      statusOf(selectorReport) === "partial" &&
        selectorReport.decision &&
        selectorReport.decision.matrixReady === true &&
        selectorReport.decision.selectorFacadeImported === true &&
        selectorReport.decision.realDeviceProfileAnchorReady === true &&
        selectorReport.decision.denialAndUnsupportedTiersReady === true &&
        selectorReport.decision.requiredDeviceArchivesReady === false,
      "P15-08 requires P14-12 selector/action matrix report to be partial with device archives still open.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        accessibilityContract.currentState === "provider_poc" &&
        includesAll(accessibilityContract.requiredEvidence || [], ["connected_device_evidence", "packaged_apk_evidence", "user_visible_disclosure"]),
      "P15-08 requires ready promotion contracts with accessibility still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        accessibilityAudit.currentState === "provider_poc" &&
        accessibilityAudit.disableFlag === "node.provider.accessibility.enabled" &&
        accessibilityAudit.diagnosticCode === "NODE_PROVIDER_ACCESSIBILITY_DISABLED" &&
        accessibilityAudit.foregroundNotificationRequired === true &&
        accessibilityAudit.stopActionRequired === true,
      "P15-08 requires ready disclosure rollback audit for accessibility.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready" || entry.status === "partial"),
      "All P15-08 evidence scenarios must be either ready or explicitly partial due to planned device archives.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15AccessibilityPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-accessibility-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15AccessibilityPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15AccessibilityPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15AccessibilityPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-08 accessibility promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-08: Close accessibility lifecycle and selector/action matrix.") &&
        files.phase15Tasks.text.includes("phase15-accessibility-promotion.json") &&
        files.phase15Tasks.text.includes("accessibility_selector_android12_action_archive"),
      "P15-08 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-accessibility-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15AccessibilityPromotion",
    task: "P15-08",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    matrix: {
      requiredDeviceClasses: selectorMatrix.requiredDeviceClasses || [],
      evidenceRows: selectorRows.map((row) => ({
        id: row.id,
        deviceClass: row.deviceClass,
        status: row.status,
        reportPath: row.reportPath,
      })),
    },
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      lifecycleFixtureReady: scenarioById(scenarios, "phase14_lifecycle_imported").status === "ready",
      selectorMatrixReady: scenarioById(scenarios, "phase14_selector_matrix_imported").status === "partial",
      requiredDeviceArchivesReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      accessibilityPromoted: false,
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

function checkTokenValue(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Accessibility Promotion",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Matrix Rows",
    "",
    "| Row | Device class | Status | Report path |",
    "| --- | --- | --- | --- |",
  ];
  report.matrix.evidenceRows.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.deviceClass}\` | \`${entry.status}\` | ${escapeTable(entry.reportPath || "")} |`);
  });
  lines.push("", "## Scenarios", "", "| Scenario | Status | Remaining |", "| --- | --- | --- |");
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

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function hasRow(rows, deviceClass, status) {
  return rows.some((row) => row.deviceClass === deviceClass && row.status === status);
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
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
      console.log("Usage: node verify-node-phase15-accessibility-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
