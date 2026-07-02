#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-packaged-broadcast-smoke";
const CURRENT_BOUNDARY_MARKERS = Object.freeze([
  "packaged.intent_broadcast.ready=true",
  "packaged.intent_broadcast.compat_rejected=true",
  "packaged.intent_broadcast.future_modules_denied=true",
  "packaged.intent_broadcast.raw_globals_absent=true",
  "packaged.intent_broadcast.broadcast_channel_js_only=true",
  "packaged.intent_broadcast.surface=true",
  "packaged.intent_broadcast.metadata_denied=true",
]);
const REQUIRED_POSITIVE_MARKERS = Object.freeze([
  "packaged.broadcast_trigger.install=true",
  "packaged.broadcast_trigger.register=true",
  "packaged.broadcast_trigger.payload_valid=true",
  "packaged.broadcast_trigger.delivered=true",
  "packaged.broadcast_trigger.remove_cleanup=true",
  "packaged.broadcast_trigger.relaunch=true",
  "packaged.broadcast_trigger.crash_recovery=true",
  "packaged.broadcast_trigger.undeclared_capability_denied=true",
]);
const REQUIRED_MATRIX_ROWS = Object.freeze([
  "current_packaged_boundary_denial_smoke",
  "future_packaged_local_custom_trigger",
  "future_packaged_remove_cleanup",
  "future_packaged_relaunch_crash_recovery",
  "future_undeclared_capability_and_exported_denial",
  "future_metadata_mismatch_safe_downgrade",
]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_packaged_broadcast_trigger_remove_contract",
  "phase15_intent_broadcast_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "packaged_broadcast_trigger_positive_archive",
  "packaged_broadcast_remove_cleanup_archive",
  "packaged_broadcast_relaunch_crash_recovery_archive",
  "packaged_broadcast_undeclared_capability_denial_archive",
  "packaged_broadcast_exported_receiver_policy_archive",
  "packaged_broadcast_metadata_mismatch_archive",
  "packaged_broadcast_safe_downgrade_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/packaged-broadcast-smoke-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_PACKAGED_BROADCAST_SMOKE.md",
  taskReport: "docs/nodejs/reports/phase15/P15_16_PACKAGED_BROADCAST_SMOKE.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15IntentBroadcastReport: "build/reports/nodejs/phase15-intent-broadcast-promotion.json",
  phase14Plan: "docs/nodejs/phase14/packaged-broadcast-trigger-remove-smoke-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_21_PACKAGED_BROADCAST_TRIGGER_REMOVE_SMOKE.md",
  phase14Generated: "build/reports/nodejs/phase14-packaged-broadcast-trigger-remove-smoke.json",
  p14IntentGenerated: "build/reports/nodejs/phase14-intent-broadcast-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
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
  console.log("Phase 15 packaged broadcast smoke: " + report.decision.status);
  console.log("Phase 15 packaged broadcast blockers: " + report.blockers.length);
  console.log("Phase 15 packaged broadcast policy violations: " + report.policyViolations.length);
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
  const phase14Plan = parseJson(files.phase14Plan.text);
  const phase14Report = parseJson(files.phase14Generated.text);
  const p14IntentReport = parseJson(files.p14IntentGenerated.text);
  const p15IntentBroadcastReport = parseJson(files.p15IntentBroadcastReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-16") || {};
  const intentBroadcastContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "intent_broadcast") || {};
  const intentBroadcastAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "intent_broadcast") || {};
  const smoke = plan.smokeContract || {};
  const metadata = plan.packagedMetadataContract || {};
  const cleanup = plan.cleanupContract || {};
  const matrixRows = Array.isArray(plan.triggerRemoveMatrix) ? plan.triggerRemoveMatrix : [];
  const matrixIds = matrixRows.map((entry) => entry.id);
  const currentMarkers = Array.isArray(smoke.currentBoundaryMarkers) ? smoke.currentBoundaryMarkers : [];
  const positiveMarkers = Array.isArray(smoke.requiredPositiveMarkers) ? smoke.requiredPositiveMarkers : [];
  const phase14Decision = phase14Report.decision || {};
  const p15IntentBroadcastDecision = p15IntentBroadcastReport.decision || {};
  const scenarios = [
    scenario("phase14_packaged_broadcast_contract_imported", "P14-21 packaged broadcast trigger/remove contract is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-packaged-broadcast-trigger-remove-smoke-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_boundary_imported", phase14Decision.currentPackagedBoundaryImported === true),
      value("phase14_p14_20_imported", phase14Decision.p14IntentProviderContractImported === true),
      value("phase14_metadata_cleanup_ready", phase14Decision.packagedMetadataExportCleanupPolicyReady === true),
      value("phase14_runtime_not_promoted", phase14Decision.runtimeTasksBroadcastPromoted === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-21 deploys the packaged broadcast trigger/remove smoke evidence contract."),
      token(files.phase14TaskReport, "`packaged_broadcast_trigger_positive_archive`"),
      token(files.phase14PlanDoc, "`packaged.intent_broadcast.ready=true`"),
      token(files.phase14PlanDoc, "`packaged.broadcast_trigger.remove_cleanup=true`"),
    ]),
    scenario("phase15_intent_broadcast_dependency", "P15-15 intent broadcast promotion dependency remains partial and unpromoted", [
      value("p15_intent_status", statusOf(p15IntentBroadcastReport) === "partial"),
      value("p15_intent_not_promoted", p15IntentBroadcastDecision.intentBroadcastPromoted === false),
      value("p15_intent_runtime_authority", p15IntentBroadcastDecision.runtimeAuthorityChanged === false),
      value("p15_intent_descriptor_ready", p15IntentBroadcastDecision.descriptorContractReady === true),
      value("p15_intent_manifest_ready", p15IntentBroadcastDecision.manifestPayloadRemovePolicyReady === true),
      token(files.p15IntentBroadcastReport, "\"intentBroadcastPromoted\": false"),
      token(files.p15IntentBroadcastReport, "\"intent_broadcast_packaged_trigger_remove_smoke_archive\""),
    ]),
    scenario("current_packaged_boundary_imported", "Current packaged boundary denial markers remain the imported packaged evidence", [
      value("future_provider", smoke.futureProvider === "tasks.broadcast"),
      value("current_boundary", smoke.currentPackagedBoundary === "embedded-node-packaged-intent-broadcast-boundary-smoke"),
      value("current_boundary_imported", smoke.currentBoundaryEvidenceImported === true),
      value("positive_not_promoted", smoke.positiveTriggerRemovePromoted === false),
      value("current_boundary_markers", sameSet(currentMarkers, CURRENT_BOUNDARY_MARKERS)),
      value("positive_markers_reserved", sameSet(positiveMarkers, REQUIRED_POSITIVE_MARKERS)),
      value("matrix_rows", sameSet(matrixIds, REQUIRED_MATRIX_ROWS)),
      token(files.packagedSmokeTest, "installAndRunIntentBroadcastBoundaryEmbeddedNodePackagedApk"),
      token(files.packagedSmokeTest, "PackagedNodeIntentBroadcastBoundarySmoke"),
      token(files.packagedSmokeTest, "\"tasks.broadcast\""),
      token(files.packagedSmokeTest, "android.permission.RECEIVE_BOOT_COMPLETED"),
      ...CURRENT_BOUNDARY_MARKERS.map((marker) => token(files.packagedSmokeTest, marker)),
      token(files.p13Report, "Packaged boundary: `EmbeddedNodePackagedApkSmokeTest#installAndRunIntentBroadcastBoundaryEmbeddedNodePackagedApk`"),
    ]),
    scenario("packaged_metadata_cleanup_policy", "Packaged metadata, exported receiver, metadata mismatch, cleanup, rollback, and Safe downgrade stay owner-scoped", [
      value("metadata_opt_in_required", metadata.metadataOptInRequired === true),
      value("current_capabilities_denied", sameSet(metadata.capabilitiesDeniedNow || [], ["tasks", "tasks.intent", "tasks.broadcast", "tasks.boot", "tasks.package_events"])),
      value("boot_permission_denied", metadata.bootPermissionDeniedNow === "android.permission.RECEIVE_BOOT_COMPLETED"),
      value("receiver_patch_report_required", metadata.receiverPatchReportRequired === true),
      value("default_exported_false", metadata.defaultExported === false),
      value("exported_receiver_denied", metadata.exportedReceiverAllowedNow === false),
      value("metadata_mismatch_policy", metadata.metadataMismatchPolicy === "deny_before_receiver_patch_or_dispatch"),
      value("remove_owned_descriptor", cleanup.removeOwnedDescriptor === true),
      value("unregister_dynamic_receiver", cleanup.unregisterDynamicReceiver === true),
      value("remove_owned_manifest_patch", cleanup.removeOwnedManifestPatch === true),
      value("cancel_pending_executions", cleanup.cancelPendingExecutions === true),
      value("prevent_post_remove_delivery", cleanup.preventPostRemoveDelivery === true),
      value("preserve_legacy_rows", cleanup.preserveLegacyRhinoRows === true),
      value("deny_cross_package_remove", cleanup.denyCrossPackageRemove === true),
      token(files.providerRegistry, "\"packagedBroadcastSmokePolicy\" to \"imports_packaged_intent_broadcast_boundary_positive_trigger_remove_not_promoted\""),
      token(files.providerRegistry, "\"packagedBroadcastCleanupPolicy\" to \"owned_descriptor_receiver_patch_pending_execution_cleanup_required_before_promotion\""),
      token(files.securityModel, "P14-21 records the packaged broadcast trigger/remove smoke contract"),
      token(files.errorCatalog, "P14-21 packaged broadcast trigger/remove denial sync"),
      token(files.compatibilityProfile, "P14-21 records the packaged broadcast trigger/remove smoke contract"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep packaged broadcast smoke behind disclosure, rollback, denial, type, security, and docs bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("intent_contract_provider_poc", intentBroadcastContract.currentState === "provider_poc"),
      value("intent_contract_p15_task", includesAll(intentBroadcastContract.p15Tasks || [], ["P15-16"])),
      value("intent_contract_backlog", includesAll(intentBroadcastContract.sourceBacklogTasks || [], ["P14-21"])),
      value("intent_contract_evidence_bars", includesAll(intentBroadcastContract.requiredEvidence || [], [
        "implementation_gate",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("intent_audit_provider_poc", intentBroadcastAudit.currentState === "provider_poc"),
      value("intent_audit_disable_flag", intentBroadcastAudit.disableFlag === "node.provider.intent_broadcast.enabled"),
      value("intent_audit_diagnostic", intentBroadcastAudit.diagnosticCode === "NODE_PROVIDER_INTENT_BROADCAST_DISABLED"),
      value("intent_audit_safe_downgrade", intentBroadcastAudit.safeDowngrade === "remove package-owned receivers and deny broadcast trigger authority"),
      value("intent_audit_disclosure_fields", includesAll(intentBroadcastAudit.requiredDisclosureFields || [], [
        "manifestMetadata",
        "foregroundNotificationOwnership",
        "stopAction",
        "taskRemovalCleanup",
        "safeDowngradeDisclosure",
      ])),
    ]),
    scenario("current_runtime_boundary", "P15-16 keeps tasks.broadcast runtime authority and future capabilities unavailable", [
      value("tasks_broadcast_not_exposed", !files.protocol.text.includes('const val TASKS_BROADCAST = "tasks.broadcast"') && !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') && !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast")),
      value("future_capabilities_not_accepted", !files.permissionManifest.text.includes("TASKS_BROADCAST") && !files.permissionManifest.text.includes('"tasks.broadcast"')),
      value("p14_intent_runtime_not_promoted", ((p14IntentReport.decision || {}).runtimeTasksBroadcastPromoted === false)),
    ]),
    scenario("promotion_blockers_open", "Positive trigger, cleanup, relaunch/crash, denial, exported receiver, metadata mismatch, Safe downgrade, and docs sync remain future work", [
      value("open_blockers", sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", (plan.openBlockers || []).every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-16 packaged broadcast smoke gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-21"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "packaged_smoke_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:installAndRunNodeV13PackagedBroadcastSmoke"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "installAndRunNodeV13PackagedBroadcastSmoke"),
      token(files.phase15Gradle, "verify-node-phase15-packaged-broadcast-smoke.js"),
      token(files.phase15Readme, ":app:installAndRunNodeV13PackagedBroadcastSmoke"),
      token(files.testing, ":app:installAndRunNodeV13PackagedBroadcastSmoke"),
      token(files.ciGates, ":app:installAndRunNodeV13PackagedBroadcastSmoke"),
      token(files.phase15Tasks, "phase15-packaged-broadcast-smoke.json"),
      token(files.phase15Tasks, "packaged_broadcast_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-packaged-broadcast-smoke-plan-v1" &&
        plan.task === "P15-16" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-21"]) &&
        plan.providerId === "intent_broadcast" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-16 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-16 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "smoke-matrix-covers-required-rows",
      sameSet(matrixIds, REQUIRED_MATRIX_ROWS) &&
        sameSet(currentMarkers, CURRENT_BOUNDARY_MARKERS) &&
        sameSet(positiveMarkers, REQUIRED_POSITIVE_MARKERS),
      "P15-16 smoke matrix must cover current packaged boundary denial plus future trigger/remove/relaunch/denial/Safe downgrade rows.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-16 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-packaged-broadcast-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.currentPackagedBoundaryImported === true &&
        phase14Decision.runtimeTasksBroadcastPromoted === false,
      "P15-16 requires P14-21 packaged broadcast smoke report to be partial with runtime authority closed.",
    ),
    check(
      "phase15-intent-broadcast-dependency-partial",
      statusOf(p15IntentBroadcastReport) === "partial" &&
        p15IntentBroadcastDecision.intentBroadcastPromoted === false &&
        p15IntentBroadcastDecision.runtimeAuthorityChanged === false,
      "P15-16 depends on P15-15 intent broadcast promotion remaining partial and unpromoted.",
    ),
    check(
      "runtime-boundary-still-closed",
      !files.protocol.text.includes('const val TASKS_BROADCAST = "tasks.broadcast"') &&
        !files.permissionManifest.text.includes("TASKS_BROADCAST") &&
        !files.nodeBridgeSources.text.includes('if (name === "tasks.broadcast")') &&
        !files.nodeBridgeSources.text.includes("__autojs6_limited_tasks_broadcast"),
      "P15-16 must not expose tasks.broadcast or accept future broadcast capabilities yet.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-16 evidence scenarios must be ready while packaged broadcast archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("installAndRunNodeV13PackagedBroadcastSmoke") &&
        files.phase15Gradle.text.includes("verify-node-phase15-packaged-broadcast-smoke.js") &&
        files.phase15Readme.text.includes(":app:installAndRunNodeV13PackagedBroadcastSmoke") &&
        files.testing.text.includes(":app:installAndRunNodeV13PackagedBroadcastSmoke") &&
        files.ciGates.text.includes(":app:installAndRunNodeV13PackagedBroadcastSmoke"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-16 packaged broadcast smoke gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-16: Add packaged broadcast trigger/remove smoke.") &&
        files.phase15Tasks.text.includes("phase15-packaged-broadcast-smoke.json") &&
        files.phase15Tasks.text.includes("packaged_broadcast_trigger_positive_archive"),
      "P15-16 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-packaged-broadcast-smoke-v1",
    generatedAt: new Date().toISOString(),
    gate: "installAndRunNodeV13PackagedBroadcastSmoke",
    task: "P15-16",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    smokeContract: smoke,
    packagedMetadataContract: metadata,
    cleanupContract: cleanup,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14PackagedBroadcastContractReady: scenarioById(scenarios, "phase14_packaged_broadcast_contract_imported").status === "ready",
      intentBroadcastDependencyReady: statusOf(p15IntentBroadcastReport) === "partial" && p15IntentBroadcastDecision.intentBroadcastPromoted === false,
      currentPackagedBoundaryImported: true,
      packagedMetadataCleanupPolicyReady: scenarioById(scenarios, "packaged_metadata_cleanup_policy").status === "ready",
      positiveTriggerRemoveSmokeReady: false,
      relaunchCrashRecoveryReady: false,
      undeclaredCapabilityDenialReady: false,
      exportedReceiverPolicyReady: false,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      packagedBroadcastSmokePromoted: false,
    },
  };
}

function scenario(id, title, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return {
    id,
    title,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    remaining: missing,
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

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Packaged Broadcast Smoke",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
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
  return expectedSorted.every((entry, index) => actualSorted[index] === entry);
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((entry) => values.includes(entry));
  }
  return requiredValues.every((entry) => String(values).includes(entry));
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
      console.log("Usage: node verify-node-phase15-packaged-broadcast-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
