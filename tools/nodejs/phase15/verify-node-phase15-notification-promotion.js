#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-notification-promotion";
const REQUIRED_LIVE_METHODS = Object.freeze(["notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings"]);
const REQUIRED_MATRIX_ROWS = Object.freeze([
  "live_post_cancel_owned_resource",
  "permission_status_settings_handoff",
  "manifest_capability_denials",
  "future_packaged_post_cancel",
  "future_foreground_disclosure_stop_action",
  "future_permission_device_matrix",
  "future_advanced_notification_decision",
  "future_rollback_cleanup_safe_downgrade",
]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_notification_ownership_contract",
  "phase15_packaged_broadcast_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "notification_packaged_post_cancel_archive",
  "notification_foreground_disclosure_stop_action_archive",
  "notification_permission_denial_device_matrix_archive",
  "notification_advanced_action_group_style_decision_archive",
  "notification_rollback_cleanup_archive",
  "notification_safe_downgrade_archive",
  "notification_raw_object_denial_security_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/notification-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_NOTIFICATION_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_17_NOTIFICATION_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15PackagedBroadcastReport: "build/reports/nodejs/phase15-packaged-broadcast-smoke.json",
  phase14Plan: "docs/nodejs/phase14/notification-ownership-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_NOTIFICATION_OWNERSHIP.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_22_NOTIFICATION_OWNERSHIP.md",
  phase14Generated: "build/reports/nodejs/phase14-notification-ownership.json",
  p13Generated: "build/reports/nodejs/phase13-notifications-settings-power-manager.json",
  p13Report: "docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  notificationsTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  notificationsTypes: "docs/nodejs/types/autojs6-node/notifications.d.ts",
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
  console.log("Phase 15 notification promotion: " + report.decision.status);
  console.log("Phase 15 notification blockers: " + report.blockers.length);
  console.log("Phase 15 notification policy violations: " + report.policyViolations.length);
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
  const p13Report = parseJson(files.p13Generated.text);
  const p15PackagedBroadcastReport = parseJson(files.p15PackagedBroadcastReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-17") || {};
  const notificationContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "notifications") || {};
  const notificationAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "notifications") || {};
  const ownership = plan.ownershipContract || {};
  const disclosure = plan.foregroundDisclosureContract || {};
  const advanced = plan.advancedNotificationBoundary || {};
  const matrixRows = Array.isArray(plan.evidenceMatrix) ? plan.evidenceMatrix : [];
  const matrixIds = matrixRows.map((entry) => entry.id);
  const phase14Decision = phase14Report.decision || {};
  const p15PackagedBroadcastDecision = p15PackagedBroadcastReport.decision || {};
  const scenarios = [
    scenario("phase14_notification_ownership_imported", "P14-22 notification ownership and foreground disclosure contract is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-notification-ownership-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_p13_imported", phase14Decision.p13NotificationEvidenceImported === true),
      value("phase14_live_ownership_ready", phase14Decision.livePostCancelOwnershipReady === true),
      value("phase14_denials_ready", phase14Decision.permissionManifestDenialsReady === true),
      value("phase14_foreground_not_ready", phase14Decision.foregroundDisclosureStopActionReady === false),
      value("phase14_packaged_not_ready", phase14Decision.packagedPostCancelReady === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-22 deploys the notification ownership and foreground disclosure evidence"),
      token(files.phase14TaskReport, "`notification_packaged_post_cancel_archive`"),
      token(files.phase14PlanDoc, "`NOTIFICATION` resource"),
      token(files.phase14PlanDoc, "`RemoteViews`"),
    ]),
    scenario("phase15_packaged_broadcast_dependency", "P15-16 packaged broadcast dependency remains partial and unpromoted", [
      value("p15_packaged_broadcast_status", statusOf(p15PackagedBroadcastReport) === "partial"),
      value("p15_packaged_broadcast_not_promoted", p15PackagedBroadcastDecision.packagedBroadcastSmokePromoted === false),
      value("p15_packaged_broadcast_runtime_authority", p15PackagedBroadcastDecision.runtimeAuthorityChanged === false),
      token(files.p15PackagedBroadcastReport, "\"packagedBroadcastSmokePromoted\": false"),
      token(files.p15PackagedBroadcastReport, "\"phase15_task_device_media_doc_sync\""),
    ]),
    scenario("ownership_permission_and_live_boundary", "P15-17 imports live owned notifications, permission/status handoff, and manifest denials", [
      value("provider_name", ownership.providerName === "notifications"),
      value("capability", ownership.capability === "notifications"),
      value("settings_capability", ownership.settingsCapability === "notifications.settings"),
      value("android_permission", ownership.androidPermission === "android.permission.POST_NOTIFICATIONS"),
      value("resource_type", ownership.resourceType === "NOTIFICATION"),
      value("live_methods", sameSet(ownership.liveMethods || [], REQUIRED_LIVE_METHODS)),
      value("max_active", ownership.maxActiveNotifications === 32),
      value("live_evidence", ownership.currentLivePostCancelEvidence === true),
      value("permission_evidence", ownership.currentPermissionDenialEvidence === true),
      value("foreground_not_ready", ownership.foregroundDisclosureStopActionReady === false),
      value("packaged_not_ready", ownership.packagedPostCancelReady === false),
      value("matrix_rows", sameSet(matrixIds, REQUIRED_MATRIX_ROWS)),
      token(files.protocol, "private val notificationHandles = ConcurrentHashMap<Int, NodeBridgeResourceHandle>()"),
      token(files.protocol, "NodeBridgeResourceType.NOTIFICATION"),
      token(files.protocol, "NotificationManagerCompat.from(appContext).notify(descriptor.id, descriptor.toNotification(appContext))"),
      token(files.protocol, "notificationHandles.remove(descriptor.id)?.release(\"notification.update\")"),
      token(files.protocol, "notificationHandles.remove(id)?.close(\"notification.cancel\")"),
      token(files.protocol, "ControlledNotificationPolicy.MAX_ACTIVE_NOTIFICATIONS"),
      token(files.notificationsTest, "androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource"),
      token(files.notificationsTest, "provider.destroy(NodeBridgeError.processDead(\"notifications provider destroy\"))"),
      token(files.permissionManifest, "const val NOTIFICATIONS = \"notifications\""),
      token(files.permissionManifest, "const val NOTIFICATIONS_SETTINGS = \"notifications.settings\""),
      token(files.permissionManifest, "const val ANDROID_PERMISSION_POST_NOTIFICATIONS = \"android.permission.POST_NOTIFICATIONS\""),
      token(files.notificationsTypes, "notify(options: NotifyOptions"),
      token(files.notificationsTypes, "cancel(id: NotificationId"),
      token(files.notificationsTypes, "getPermissionStatus(options?"),
    ]),
    scenario("p13_notification_settings_imported", "P13 notification/settings/power evidence remains imported", [
      value("p13_status", statusOf(p13Report) === "partial"),
      token(files.p13Report, "`notifications.getPermissionStatus()` returns a JSON-safe permission snapshot"),
      token(files.p13Report, "P13-25 v1.1 stable-denies notification actions"),
      token(files.packagedSmokeTest, "packaged.notifications_power.action_or_denial=true"),
      token(files.conformanceTest, "postNotificationsPermissionRequired: true"),
    ]),
    scenario("foreground_disclosure_rollback_and_advanced_boundary", "Foreground disclosure, stop action, rollback, Safe downgrade, and advanced notification objects remain future work", [
      value("disclosure_required", disclosure.requiredBeforePackagedPromotion === true),
      value("stop_action_required", disclosure.stopActionRequired === true),
      value("foreground_required", disclosure.foregroundNotificationRequired === true),
      value("disclosure_fields", includesAll(disclosure.requiredFields || [], ["title", "text", "channel", "ongoing", "owner", "taskId", "packageIdentity", "stopAction", "rollbackBehavior"])),
      value("advanced_decision_required", advanced.stableDeniedOrFutureDecisionRequired === true),
      value("blocked_features", includesAll(advanced.blockedFeatures || [], ["actions", "RemoteViews", "notification listener handles", "Do Not Disturb mutation"])),
      value("raw_objects_denied", includesAll(advanced.rawObjectsDenied || [], ["Notification", "NotificationChannel", "PendingIntent", "RemoteViews", "StatusBarNotification"])),
      token(files.providerRegistry, "\"blockedMethods\" to \"actions,listener,groups,styles,inline-reply,remote-input,do-not-disturb,exact-alarm,wakelock\""),
      token(files.providerRegistry, "\"rawObjectPolicy\" to \"no raw Notification"),
      token(files.securityModel, "P14-22 records notification ownership and foreground disclosure evidence"),
      token(files.errorCatalog, "P14-22 notification ownership denial sync"),
      token(files.compatibilityProfile, "P14-22 records notification ownership and foreground disclosure evidence"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep notifications behind disclosure, rollback, packaged, permission, security, type, and docs bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("notification_contract_provider_poc", notificationContract.currentState === "provider_poc"),
      value("notification_contract_p15_task", includesAll(notificationContract.p15Tasks || [], ["P15-17"])),
      value("notification_contract_backlog", includesAll(notificationContract.sourceBacklogTasks || [], ["P14-22"])),
      value("notification_contract_evidence_bars", includesAll(notificationContract.requiredEvidence || [], [
        "implementation_gate",
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("notification_audit_provider_poc", notificationAudit.currentState === "provider_poc"),
      value("notification_audit_disable_flag", notificationAudit.disableFlag === "node.provider.notifications.enabled"),
      value("notification_audit_diagnostic", notificationAudit.diagnosticCode === "NODE_PROVIDER_NOTIFICATIONS_DISABLED"),
      value("notification_audit_safe_downgrade", notificationAudit.safeDowngrade === "cancel package-owned notifications and deny foreground disclosure takeover"),
      value("notification_audit_disclosure_fields", includesAll(notificationAudit.requiredDisclosureFields || [], [
        "foregroundNotificationOwnership",
        "stopAction",
        "taskRemovalCleanup",
        "safeDowngradeDisclosure",
      ])),
    ]),
    scenario("runtime_raw_object_boundary", "P15-17 keeps raw notification objects and advanced listener/action authority unavailable", [
      value("raw_remoteviews_not_exposed", !files.nodeBridgeSources.text.includes("RemoteViews")),
      value("status_bar_notification_not_exposed", !files.nodeBridgeSources.text.includes("StatusBarNotification")),
      value("notification_listener_not_exposed", !files.nodeBridgeSources.text.includes("NotificationListener")),
      value("pending_intent_not_exposed", !files.nodeBridgeSources.text.includes("PendingIntent")),
    ]),
    scenario("promotion_blockers_open", "Packaged post/cancel, foreground disclosure, permission matrix, advanced decision, rollback, Safe downgrade, security, and docs sync remain future work", [
      value("open_blockers", sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", (plan.openBlockers || []).every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-17 notification promotion gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-22"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "foreground_disclosure_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15NotificationPromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15NotificationPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-notification-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15NotificationPromotion"),
      token(files.testing, ":app:verifyNodePhase15NotificationPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15NotificationPromotion"),
      token(files.phase15Tasks, "phase15-notification-promotion.json"),
      token(files.phase15Tasks, "notification_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-notification-promotion-plan-v1" &&
        plan.task === "P15-17" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-22"]) &&
        plan.providerId === "notifications" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-17 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-17 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "evidence-matrix-covers-required-rows",
      sameSet(matrixIds, REQUIRED_MATRIX_ROWS) &&
        sameSet(ownership.liveMethods || [], REQUIRED_LIVE_METHODS),
      "P15-17 evidence matrix must cover live ownership, settings handoff, manifest denial, packaged post/cancel, disclosure, permission matrix, advanced decision, and rollback.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-17 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-notification-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.livePostCancelOwnershipReady === true &&
        phase14Decision.permissionManifestDenialsReady === true &&
        phase14Decision.packagedPostCancelReady === false,
      "P15-17 requires P14-22 notification ownership report to be partial with packaged/foreground archives still open.",
    ),
    check(
      "phase15-packaged-broadcast-dependency-partial",
      statusOf(p15PackagedBroadcastReport) === "partial" &&
        p15PackagedBroadcastDecision.packagedBroadcastSmokePromoted === false &&
        p15PackagedBroadcastDecision.runtimeAuthorityChanged === false,
      "P15-17 depends on P15-16 packaged broadcast smoke remaining partial and unpromoted.",
    ),
    check(
      "raw-notification-objects-stay-denied",
      !files.nodeBridgeSources.text.includes("RemoteViews") &&
        !files.nodeBridgeSources.text.includes("StatusBarNotification") &&
        !files.nodeBridgeSources.text.includes("NotificationListener"),
      "P15-17 must not expose raw RemoteViews, StatusBarNotification, or notification listener APIs through Node sources.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-17 evidence scenarios must be ready while notification archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15NotificationPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-notification-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15NotificationPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15NotificationPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15NotificationPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-17 notification promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-17: Promote notification ownership and foreground disclosure behavior.") &&
        files.phase15Tasks.text.includes("phase15-notification-promotion.json") &&
        files.phase15Tasks.text.includes("notification_packaged_post_cancel_archive"),
      "P15-17 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-notification-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15NotificationPromotion",
    task: "P15-17",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    ownershipContract: ownership,
    foregroundDisclosureContract: disclosure,
    advancedNotificationBoundary: advanced,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14NotificationOwnershipReady: scenarioById(scenarios, "phase14_notification_ownership_imported").status === "ready",
      packagedBroadcastDependencyReady: statusOf(p15PackagedBroadcastReport) === "partial" && p15PackagedBroadcastDecision.packagedBroadcastSmokePromoted === false,
      livePostCancelOwnershipReady: scenarioById(scenarios, "ownership_permission_and_live_boundary").status === "ready",
      permissionManifestDenialsReady: true,
      foregroundDisclosureStopActionReady: false,
      packagedPostCancelReady: false,
      advancedNotificationDecisionReady: false,
      rollbackCleanupReady: false,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      notificationPromotionPromoted: false,
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
    "# Phase 15 Notification Promotion",
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
      console.log("Usage: node verify-node-phase15-notification-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
