#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase16-packaged-overlay-smoke";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_packaged_overlay_fixture_contract",
  "phase15_packaged_overlay_smoke_partial_gate",
  "phase16_overlay_provider_promotion_gate",
  "phase16_overlay_oem_matrix_gate",
  "phase16_promotion_contracts",
  "phase16_disclosure_rollback_contract",
  "phase16_surface_manifest_mapping",
]);
const REQUIRED_NODE_PERMISSIONS = Object.freeze([
  "ui.overlay",
  "ui.overlay.permission",
  "ui.overlay.foreground",
  "ui.overlay.packaged",
]);
const REQUIRED_ANDROID_PERMISSIONS = Object.freeze(["android.permission.SYSTEM_ALERT_WINDOW"]);
const REQUIRED_RUNTIME_MARKERS = Object.freeze([
  "packaged.overlay_positive.ready=true",
  "packaged.overlay_positive.metadata=true",
  "packaged.overlay_positive.permission_snapshot=true",
  "packaged.overlay_positive.permission_granted=true",
  "packaged.overlay_positive.disclosure=true",
  "packaged.overlay_positive.show=true",
  "packaged.overlay_positive.update=true",
  "packaged.overlay_positive.event_drain=true",
  "packaged.overlay_positive.close=true",
  "packaged.overlay_positive.close_all=true",
  "packaged.overlay_positive.stress_sessions=3",
  "packaged.overlay_positive.leak_summary=true",
  "packaged.overlay_positive.raw_globals_absent=true",
  "packaged.overlay_positive.pending=0",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "packaged_overlay_positive_smoke_archive",
  "packaged_overlay_positive_device_archive",
  "packaged_overlay_foreground_disclosure_stop_action",
  "packaged_overlay_task_removal_relaunch_crash",
  "packaged_overlay_event_drain_leak_stress",
  "phase16_security_type_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase16/packaged-overlay-smoke-plan.json",
  planDoc: "docs/nodejs/phase16/PHASE16_PACKAGED_OVERLAY_SMOKE.md",
  taskReport: "docs/nodejs/reports/phase16/P16_07_PACKAGED_OVERLAY_SMOKE.md",
  phase16Tasks: "docs/nodejs/reports/phase16/PHASE16_NODE_V1_4_PROVIDER_PROMOTION_TASKS.md",
  phase16Readme: "docs/nodejs/reports/phase16/README.md",
  surfaceManifest: "docs/nodejs/phase16/phase16-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase16/node-v1-4-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase16-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase16-disclosure-rollback-audit.json",
  overlayProviderPlan: "docs/nodejs/phase16/overlay-provider-promotion-plan.json",
  overlayProviderReport: "build/reports/nodejs/phase16-overlay-provider-promotion.json",
  overlayOemPlan: "docs/nodejs/phase16/overlay-oem-matrix-plan.json",
  overlayOemReport: "build/reports/nodejs/phase16-overlay-oem-matrix.json",
  phase15Plan: "docs/nodejs/phase15/packaged-overlay-smoke-plan.json",
  phase15PlanDoc: "docs/nodejs/phase15/PHASE15_PACKAGED_OVERLAY_SMOKE.md",
  phase15TaskReport: "docs/nodejs/reports/phase15/P15_07_PACKAGED_OVERLAY_SMOKE.md",
  phase14Plan: "docs/nodejs/phase14/packaged-overlay-positive-smoke-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_OVERLAY_POSITIVE_SMOKE.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_10_PACKAGED_OVERLAY_POSITIVE_SMOKE.md",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  phase16Gradle: "app/node-phase16-gate.gradle.kts",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
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
  console.log("Phase 16 packaged overlay smoke: " + report.decision.status);
  console.log("Phase 16 packaged overlay smoke blockers: " + report.blockers.length);
  console.log("Phase 16 packaged overlay smoke policy violations: " + report.policyViolations.length);
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
  const phase15Plan = parseJson(files.phase15Plan.text);
  const phase14Plan = parseJson(files.phase14Plan.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const overlayProviderPlan = parseJson(files.overlayProviderPlan.text);
  const overlayProviderReport = parseJson(files.overlayProviderReport.text);
  const overlayOemPlan = parseJson(files.overlayOemPlan.text);
  const overlayOemReport = parseJson(files.overlayOemReport.text);
  const p16Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P16-07") || {};
  const overlayContract = (contracts.providerPromotionBars || []).find((entry) => entry.providerId === "overlay") || {};
  const overlayAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "overlay") || {};
  const fixture = plan.fixture || {};
  const phase15Fixture = phase15Plan.fixture || {};
  const phase14Fixture = phase14Plan.fixture || {};
  const manual = plan.manualArchiveRequirements || {};
  const scenarios = [
    scenario("phase14_packaged_fixture_imported", "P14-10 packaged overlay fixture and marker contract are imported", [
      checkTokenValue("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-packaged-overlay-positive-smoke-v1"),
      checkTokenValue("phase14_plan_task", phase14Plan.task === "P14-10"),
      checkTokenValue("phase14_plan_status", phase14Plan.status === "partial"),
      checkTokenValue("phase14_plan_poc", phase14Plan.promotionState === "provider_poc"),
      checkTokenValue("fixture_permissions_match", sameSet(fixture.requiredNodePermissions || [], phase14Fixture.requiredNodePermissions || [])),
      checkTokenValue("fixture_markers_match", sameSet(fixture.runtimeMarkers || [], phase14Fixture.runtimeMarkers || [])),
      token(files.phase14PlanDoc, "requiresUserGrantedOverlayPermission"),
      token(files.phase14TaskReport, "packaged_overlay_positive_device_archive"),
    ]),
    scenario("packaged_runtime_contract", "Packaged overlay runtime script covers permission, show/update/drain/close, stress, and cleanup markers", [
      checkTokenValue("node_permissions", includesAll(fixture.requiredNodePermissions || [], REQUIRED_NODE_PERMISSIONS)),
      checkTokenValue("android_permissions", includesAll(fixture.requiredAndroidPermissions || [], REQUIRED_ANDROID_PERMISSIONS)),
      checkTokenValue("runtime_markers", includesAll(fixture.runtimeMarkers || [], REQUIRED_RUNTIME_MARKERS)),
      token(files.packagedTest, "buildOverlayPositiveEmbeddedNodePackagedApk"),
      token(files.packagedTest, "buildPackagedOverlayPositiveSmokeApk"),
      token(files.packagedTest, "PackagedNodeOverlayPositiveSmoke_v1.0.0.apk"),
      token(files.packagedTest, 'extraPermissions = listOf("android.permission.SYSTEM_ALERT_WINDOW")'),
      token(files.packagedTest, "const permission = await overlay.hasPermission"),
      token(files.packagedTest, "const first = await overlay.show"),
      token(files.packagedTest, "await first.update"),
      token(files.packagedTest, "await first.drainEvents"),
      token(files.packagedTest, "await first.close"),
      token(files.packagedTest, "await overlay.closeAll"),
      token(files.packagedTest, "packaged.overlay_positive.stress_sessions=3"),
      token(files.packagedTest, "packaged.overlay_positive.pending=0"),
      token(files.protocol, "foregroundDisclosure"),
    ]),
    scenario("phase15_packaged_overlay_partial_gate", "P15-07 packaged overlay smoke source backlog remains partial", [
      checkTokenValue("phase15_plan_schema", phase15Plan.schema === "autojs6-node-phase15-packaged-overlay-smoke-plan-v1"),
      checkTokenValue("phase15_plan_status", phase15Plan.status === "partial"),
      checkTokenValue("phase15_plan_not_promoted", phase15Plan.promotionState === "provider_poc_not_promoted"),
      checkTokenValue("phase15_fixture_permissions_match", sameSet(fixture.requiredNodePermissions || [], phase15Fixture.requiredNodePermissions || [])),
      checkTokenValue("phase15_fixture_markers_match", sameSet(fixture.runtimeMarkers || [], phase15Fixture.runtimeMarkers || [])),
      token(files.phase15PlanDoc, "phase14_packaged_overlay_runtime_marker_contract"),
      token(files.phase15TaskReport, "Status: partial"),
      token(files.phase15TaskReport, "installAndRunNodeV13PackagedOverlaySmoke"),
      token(files.phase15TaskReport, "packaged_overlay_event_drain_leak_stress"),
    ]),
    scenario("phase16_overlay_dependencies", "P16-05 provider and P16-06 OEM matrix gates remain partial prerequisites", [
      checkTokenValue("overlay_provider_status", statusOf(overlayProviderReport) === "partial"),
      checkTokenValue("overlay_provider_not_promoted", overlayProviderReport.decision && overlayProviderReport.decision.overlayPromoted === false),
      checkTokenValue("overlay_provider_mentions_packaged_smoke", (overlayProviderPlan.requiredPromotionEvidence || []).includes("packaged_overlay_positive_smoke_archive")),
      checkTokenValue("overlay_oem_status", statusOf(overlayOemReport) === "partial"),
      checkTokenValue("overlay_oem_not_promoted", overlayOemReport.decision && overlayOemReport.decision.overlayPromoted === false),
      checkTokenValue("overlay_oem_device_archives_open", overlayOemReport.decision && overlayOemReport.decision.requiredDeviceArchivesReady === false),
      checkTokenValue("overlay_oem_android12_blocker", (overlayOemPlan.requiredPromotionEvidence || []).includes("overlay_android12_granted_device_archive")),
      token(files.overlayProviderReport, "\"packaged_overlay_positive_smoke_archive\""),
      token(files.overlayOemReport, "\"requiredDeviceArchivesReady\": false"),
    ]),
    scenario("manual_archive_requirements_open", "Packaged install-run archives remain future-promotion work", [
      checkTokenValue("manual_device_classes", includesAll(manual.requiredDeviceClasses || [], ["android_12_class", "android_15_plus_class"])),
      checkTokenValue("requires_user_grant", manual.requiresUserGrantedOverlayPermission === true),
      checkTokenValue("requires_disclosure_review", manual.requiresForegroundDisclosureReview === true),
      token(files.phase15TaskReport, "packaged_overlay_positive_device_archive"),
      token(files.phase15TaskReport, "packaged_overlay_foreground_disclosure_stop_action"),
      token(files.phase15TaskReport, "packaged_overlay_task_removal_relaunch_crash"),
      token(files.phase15TaskReport, "packaged_overlay_event_drain_leak_stress"),
    ], REQUIRED_BLOCKERS),
    scenario("phase16_contracts_and_rollback", "Phase 16 contracts keep packaged overlay promotion behind evidence, disclosure, and rollback bars", [
      checkTokenValue("promotion_report_passed", statusOf(promotionReport) === "passed"),
      checkTokenValue("disclosure_report_passed", statusOf(disclosureReport) === "passed"),
      checkTokenValue("overlay_contract_provider_poc", overlayContract.currentState === "provider_poc"),
      checkTokenValue("overlay_contract_p16_task", includesAll(overlayContract.p16Tasks || [], ["P16-05", "P16-06", "P16-07"])),
      checkTokenValue("overlay_contract_packaged_bar", includesAll(overlayContract.requiredEvidence || [], [
        "packaged_apk_evidence",
        "connected_device_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
      ])),
      checkTokenValue("overlay_audit_provider_poc", overlayAudit.currentState === "provider_poc"),
      checkTokenValue("overlay_audit_disable_flag", overlayAudit.disableFlag === "node.provider.overlay.enabled"),
      checkTokenValue("overlay_audit_disclosure_stop", overlayAudit.foregroundNotificationRequired === true && overlayAudit.stopActionRequired === true),
      checkTokenValue("overlay_audit_crash_breaker", overlayAudit.crashCircuitBreakerRequired === true),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 16 docs, surface manifest, and Gradle expose the P16-07 packaged overlay gate", [
      checkTokenValue("surface_mapping_owner", p16Mapping.ownerSurface === "pro.ui_overlay_automation_media"),
      checkTokenValue("surface_mapping_backlog", includesAll(p16Mapping.sourceBacklogTasks || [], ["P15-07"])),
      checkTokenValue("surface_mapping_evidence_class", p16Mapping.evidenceClass === "packaged_smoke_archive"),
      checkTokenValue("surface_mapping_gate", p16Mapping.gate === ":app:installAndRunNodeV14PackagedOverlaySmoke"),
      checkTokenValue("surface_mapping_planned", p16Mapping.status === "planned"),
      token(files.phase16Gradle, "installAndRunNodeV14PackagedOverlaySmoke"),
      token(files.phase16Gradle, "verify-node-phase16-packaged-overlay-smoke.js"),
      token(files.phase16Readme, ":app:installAndRunNodeV14PackagedOverlaySmoke"),
      token(files.testing, ":app:installAndRunNodeV14PackagedOverlaySmoke"),
      token(files.ciGates, ":app:installAndRunNodeV14PackagedOverlaySmoke"),
      token(files.securityModel, "P16-07"),
      token(files.errorCatalog, "P16-07"),
      token(files.phase16Tasks, "phase16-packaged-overlay-smoke.json"),
      token(files.phase16Tasks, "packaged_overlay_event_drain_leak_stress"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase16-packaged-overlay-smoke-plan-v1" &&
        plan.task === "P16-07" &&
        plan.sourceBacklogTask === "P15-07" &&
        plan.providerId === "overlay" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P16-07 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P16-07 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        sameSet(plan.requiredPromotionEvidence || [], REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P16-07 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-packaged-fixture-ready",
      phase14Plan.schema === "autojs6-node-phase14-packaged-overlay-positive-smoke-v1" &&
        phase14Plan.task === "P14-10" &&
        phase14Plan.status === "partial" &&
        sameSet(fixture.requiredNodePermissions || [], phase14Fixture.requiredNodePermissions || []) &&
        sameSet(fixture.runtimeMarkers || [], phase14Fixture.runtimeMarkers || []),
      "P16-07 imports the P14-10 packaged overlay fixture contract without promotion claims.",
    ),
    check(
      "fixture-contract",
      includesAll(fixture.requiredNodePermissions || [], REQUIRED_NODE_PERMISSIONS) &&
        includesAll(fixture.requiredAndroidPermissions || [], REQUIRED_ANDROID_PERMISSIONS) &&
        includesAll(fixture.runtimeMarkers || [], REQUIRED_RUNTIME_MARKERS) &&
        files.packagedTest.text.includes("buildPackagedOverlayPositiveSmokeApk"),
      "P16-07 fixture must import overlay node permissions, SYSTEM_ALERT_WINDOW, runtime markers, and the packaged builder.",
    ),
    check(
      "phase15-packaged-overlay-partial",
      phase15Plan.schema === "autojs6-node-phase15-packaged-overlay-smoke-plan-v1" &&
        phase15Plan.status === "partial" &&
        phase15Plan.promotionState === "provider_poc_not_promoted" &&
        files.phase15TaskReport.text.includes("Status: partial") &&
        files.phase15TaskReport.text.includes("packaged_overlay_positive_device_archive"),
      "P16-07 imports the P15-07 packaged overlay gate as a partial source backlog.",
    ),
    check(
      "phase16-overlay-provider-partial",
      statusOf(overlayProviderReport) === "partial" &&
        overlayProviderReport.decision &&
        overlayProviderReport.decision.overlayPromoted === false &&
        overlayProviderReport.decision.runtimeAuthorityChanged === false,
      "P16-07 depends on the P16-05 overlay provider gate remaining partial and unpromoted.",
    ),
    check(
      "phase16-overlay-oem-partial",
      statusOf(overlayOemReport) === "partial" &&
        overlayOemReport.decision &&
        overlayOemReport.decision.overlayPromoted === false &&
        overlayOemReport.decision.requiredDeviceArchivesReady === false,
      "P16-07 depends on the P16-06 OEM matrix gate remaining partial with device archives open.",
    ),
    check(
      "phase16-contracts-passed",
      statusOf(promotionReport) === "passed" &&
        overlayContract.currentState === "provider_poc" &&
        includesAll(overlayContract.requiredEvidence || [], ["packaged_apk_evidence", "connected_device_evidence", "user_visible_disclosure"]),
      "P16-07 requires passed promotion contracts with overlay still provider_poc.",
    ),
    check(
      "phase16-disclosure-audit-passed",
      statusOf(disclosureReport) === "passed" &&
        overlayAudit.currentState === "provider_poc" &&
        overlayAudit.disableFlag === "node.provider.overlay.enabled" &&
        overlayAudit.diagnosticCode === "NODE_PROVIDER_OVERLAY_DISABLED" &&
        overlayAudit.foregroundNotificationRequired === true &&
        overlayAudit.stopActionRequired === true,
      "P16-07 requires passed disclosure rollback audit for overlay.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready" || entry.status === "partial"),
      "All P16-07 evidence scenarios must be either ready or explicitly partial due to planned packaged/device archives.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase16Gradle.text.includes("installAndRunNodeV14PackagedOverlaySmoke") &&
        files.phase16Gradle.text.includes("verify-node-phase16-packaged-overlay-smoke.js") &&
        files.phase16Readme.text.includes(":app:installAndRunNodeV14PackagedOverlaySmoke") &&
        files.testing.text.includes(":app:installAndRunNodeV14PackagedOverlaySmoke") &&
        files.ciGates.text.includes(":app:installAndRunNodeV14PackagedOverlaySmoke") &&
        files.securityModel.text.includes("P16-07") &&
        files.errorCatalog.text.includes("P16-07"),
      "Gradle, README, TESTING, CI_GATES, security model, and error catalog must reference the P16-07 packaged overlay smoke gate.",
    ),
    check(
      "task-plan-partial",
      files.phase16Tasks.text.includes("- [ ] P16-07: Add packaged overlay positive smoke and stress.") &&
        files.phase16Tasks.text.includes("phase16-packaged-overlay-smoke.json") &&
        files.phase16Tasks.text.includes("packaged_overlay_event_drain_leak_stress"),
      "P16-07 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase16-packaged-overlay-smoke-v1",
    generatedAt: new Date().toISOString(),
    gate: "installAndRunNodeV14PackagedOverlaySmoke",
    task: "P16-07",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    fixture: {
      projectName: fixture.projectName || "missing",
      packageName: fixture.packageName || "missing",
      apkName: fixture.apkName || "missing",
      requiredNodePermissions: fixture.requiredNodePermissions || [],
      requiredAndroidPermissions: fixture.requiredAndroidPermissions || [],
      runtimeMarkers: fixture.runtimeMarkers || [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p16TaskComplete: false,
      packagedFixtureReady: scenarioById(scenarios, "phase14_packaged_fixture_imported").status === "ready",
      runtimePositivePathReady: scenarioById(scenarios, "packaged_runtime_contract").status === "ready",
      requiredDeviceArchivesReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      overlayPromoted: false,
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
    "# Phase 16 Packaged Overlay Smoke",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Fixture",
    "",
    `- Project: \`${report.fixture.projectName}\``,
    `- Package: \`${report.fixture.packageName}\``,
    `- APK: \`${report.fixture.apkName}\``,
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
      console.log("Usage: node verify-node-phase16-packaged-overlay-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
