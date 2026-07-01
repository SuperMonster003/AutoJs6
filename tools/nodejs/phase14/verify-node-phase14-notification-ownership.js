#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-notification-ownership";
const REQUIRED_LIVE_METHODS = Object.freeze(["notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings"]);
const REQUIRED_MATRIX_ROWS = Object.freeze([
  "live_post_cancel_owned_resource",
  "permission_status_settings_handoff",
  "manifest_capability_denials",
  "future_packaged_post_cancel",
  "future_foreground_disclosure_stop_action",
  "future_advanced_notification_decision",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "notification_packaged_post_cancel_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK post/cancel/cancelAll archive proving package identity ownership, permission handling, relaunch, task removal cleanup, and Safe downgrade.",
  },
  {
    id: "notification_foreground_disclosure_stop_action_archive",
    classification: "future_promotion",
    requiredEvidence: "Foreground disclosure and stop-action archive for ongoing packaged notifications and long-running or scheduled Node work.",
  },
  {
    id: "notification_permission_denial_device_matrix_archive",
    classification: "future_promotion",
    requiredEvidence: "Android 12 and Android 15+ permission-denied/granted matrix for POST_NOTIFICATIONS, disabled notifications, settings round-trip, and denial copy.",
  },
  {
    id: "notification_advanced_action_group_style_decision_archive",
    classification: "future_promotion",
    requiredEvidence: "Stable-denial or promotion archive for notification actions, groups, styles, inline reply, remote input, listeners, RemoteViews, and DND mutation.",
  },
  {
    id: "notification_rollback_cleanup_archive",
    classification: "future_promotion",
    requiredEvidence: "Rollback cleanup archive for owned notification ids, channels, foreground disclosure metadata, and packaged downgrade behavior.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_22_NOTIFICATION_OWNERSHIP.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/notification-ownership-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NOTIFICATION_OWNERSHIP.md",
  p13GeneratedReport: "phase13-notifications-settings-power-manager.json",
  p13Report: "docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
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
  console.log("Phase 14 notification ownership: " + report.decision.status);
  console.log("Phase 14 notification blockers: " + report.blockers.length);
  console.log("Phase 14 notification policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const isGenerated = relative.endsWith(".json") && !relative.includes("/");
      const file = isGenerated ? path.join(reportDir, relative) : path.join(repoRoot, relative);
      return [
        key,
        {
          relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
          exists: fs.existsSync(file),
          text: read(file),
        },
      ];
    }),
  );
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const ownership = plan.ownershipContract || {};
  const disclosure = plan.foregroundDisclosureContract || {};
  const advanced = plan.advancedNotificationBoundary || {};
  const matrixRows = Array.isArray(plan.evidenceMatrix) ? plan.evidenceMatrix : [];
  const matrixIds = matrixRows.map((entry) => entry.id);
  const p13Generated = parseJson(files.p13GeneratedReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const disclosureFields = ((contracts.packagedHighPrivilegeDisclosure || {}).requiredFields || []);
  const scenarios = [
    scenario("notification_ownership_contract", "P14-22 records owned notification post/cancel evidence without packaged promotion", [
      value("schema", plan.schema === "autojs6-node-phase14-notification-ownership-plan-v1"),
      value("task", plan.task === "P14-22"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-25", "P14-04", "P14-21"])),
      value("providerName", ownership.providerName === "notifications"),
      value("capability", ownership.capability === "notifications"),
      value("settingsCapability", ownership.settingsCapability === "notifications.settings"),
      value("androidPermission", ownership.androidPermission === "android.permission.POST_NOTIFICATIONS"),
      value("resourceType", ownership.resourceType === "NOTIFICATION"),
      value("liveMethods", sameSet(ownership.liveMethods || [], REQUIRED_LIVE_METHODS)),
      value("maxActiveNotifications", ownership.maxActiveNotifications === 32),
      value("livePostCancelEvidence", ownership.currentLivePostCancelEvidence === true),
      value("permissionDenialEvidence", ownership.currentPermissionDenialEvidence === true),
      value("foregroundDisclosureNotReady", ownership.foregroundDisclosureStopActionReady === false),
      value("packagedPostCancelNotReady", ownership.packagedPostCancelReady === false),
      value("matrixRows", sameSet(matrixIds, REQUIRED_MATRIX_ROWS)),
      token(files.planDoc, "`NOTIFICATION` resource"),
    ]),
    scenario("p13_notification_settings_imported", "P13-25 notification/settings report and JSON-safe provider state are imported", [
      value("p13GeneratedPresent", files.p13GeneratedReport.exists),
      value("p13GeneratedPartial", statusOf(p13Generated) === "partial"),
      value("p13NotificationSettingsReady", (p13Generated.decision || {}).notificationSettingsReady === true),
      value("p13PackagedSettingsPowerReady", (p13Generated.decision || {}).packagedSettingsPowerReady === true),
      token(files.p13Report, "`notifications.getPermissionStatus()` returns a JSON-safe permission snapshot"),
      token(files.p13Report, "P13-25 v1.1 stable-denies notification actions"),
      token(files.notificationsTypes, "notify(options: NotifyOptions"),
      token(files.notificationsTypes, "cancel(id: NotificationId"),
      token(files.notificationsTypes, "cancelAll(options?"),
      token(files.notificationsTypes, "getPermissionStatus(options?"),
      token(files.notificationsTypes, "openNotificationSettings(options?"),
    ]),
    scenario("live_post_cancel_owned_resource", "Live Android provider owns notification resources and cleans them up", [
      token(files.protocol, "private val notificationHandles = ConcurrentHashMap<Int, NodeBridgeResourceHandle>()"),
      token(files.protocol, "NodeBridgeResourceType.NOTIFICATION"),
      token(files.protocol, "NotificationManagerCompat.from(appContext).notify(descriptor.id, descriptor.toNotification(appContext))"),
      token(files.protocol, "notificationHandles.remove(descriptor.id)?.release(\"notification.update\")"),
      token(files.protocol, "notificationHandles.remove(id)?.close(\"notification.cancel\")"),
      token(files.protocol, "notificationHandles.values.toList().forEach { handle ->"),
      token(files.protocol, "ControlledNotificationPolicy.MAX_ACTIVE_NOTIFICATIONS"),
      token(files.notificationsTest, "androidProviderPostsOrReportsNotificationPermissionAndTracksOwnedResource"),
      token(files.notificationsTest, "assertEquals(\"1\", activeDiagnostics[\"embedded_script.bridge_resource.active_count\"])"),
      token(files.notificationsTest, "activeTypeCount(activeDiagnostics, NodeBridgeResourceType.NOTIFICATION)"),
      token(files.notificationsTest, "assertNotificationResource(activeDiagnostics, channel = \"node-test\", ongoing = \"true\")"),
      token(files.notificationsTest, "provider.destroy(NodeBridgeError.processDead(\"notifications provider destroy\"))"),
    ]),
    scenario("permission_and_manifest_denials", "Notification permission and manifest denial branches are stable", [
      token(files.permissionManifest, "const val NOTIFICATIONS = \"notifications\""),
      token(files.permissionManifest, "const val NOTIFICATIONS_SETTINGS = \"notifications.settings\""),
      token(files.permissionManifest, "const val ANDROID_PERMISSION_POST_NOTIFICATIONS = \"android.permission.POST_NOTIFICATIONS\""),
      token(files.permissionManifest, "NodeBridgeModules.NOTIFICATIONS -> when (method)"),
      token(files.protocol, "AutoJs6 notifications bridge requires android.permission.POST_NOTIFICATIONS."),
      token(files.protocol, "private fun notificationPermissionStatus(): JSONObject"),
      token(files.protocol, "postNotificationsPermissionRequired"),
      token(files.notificationsTest, "notifications.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|notifications|0|0"),
      token(files.notificationsTest, "notifications.settings.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|notifications.settings|0|0"),
      token(files.conformanceTest, "postNotificationsPermissionRequired: true"),
      token(files.packagedSmokeTest, "packaged.notifications_power.action_or_denial=true"),
    ]),
    scenario("foreground_disclosure_and_advanced_boundaries", "Foreground disclosure, stop action, and advanced notification features remain future work", [
      value("disclosureRequired", disclosure.requiredBeforePackagedPromotion === true),
      value("stopActionRequired", disclosure.stopActionRequired === true),
      value("sharedDisclosureFields", includesAll(disclosureFields, ["manifestMetadata", "foregroundNotificationOwnership", "stopAction", "taskRemovalCleanup", "rollbackBehavior"])),
      value("advancedDecisionRequired", advanced.stableDeniedOrFutureDecisionRequired === true),
      value("blockedFeatureRemoteViews", includesAll(advanced.blockedFeatures || [], ["actions", "RemoteViews", "notification listener handles", "Do Not Disturb mutation"])),
      value("rawObjectsDenied", includesAll(advanced.rawObjectsDenied || [], ["Notification", "PendingIntent", "RemoteViews", "StatusBarNotification"])),
      token(files.providerRegistry, "\"blockedMethods\" to \"actions,listener,groups,styles,inline-reply,remote-input,do-not-disturb,exact-alarm,wakelock\""),
      token(files.providerRegistry, "\"rawObjectPolicy\" to \"no raw Notification"),
      token(files.planDoc, "This gate does not promote packaged notification post/cancel behavior"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-22 gate", [
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-22") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/notification-ownership-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14NotificationOwnership")),
      token(files.providerRegistry, "\"phase14NotificationOwnershipPromotion\" to \"P14-22 notification ownership and foreground disclosure evidence gate\""),
      token(files.providerRegistry, "\"notificationOwnershipPolicy\" to \"ResourceRegistry_owned_ids_cancel_unknown_noop_no_raw_handles\""),
      token(files.surfaceManifestDoc, "P14-22 adds the notification ownership and foreground disclosure gate"),
      token(files.phase14Tasks, "Notification ownership gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14NotificationOwnership"),
      token(files.testing, ":app:verifyNodePhase14NotificationOwnership"),
      token(files.ciGates, ":app:verifyNodePhase14NotificationOwnership"),
      token(files.gradle14, "verifyNodePhase14NotificationOwnership"),
      token(files.gradle14, "verify-node-phase14-notification-ownership.js"),
      token(files.securityModel, "P14-22 records notification ownership and foreground disclosure evidence"),
      token(files.errorCatalog, "P14-22 notification ownership denial sync"),
      token(files.compatibilityProfile, "P14-22 records notification ownership and foreground disclosure evidence"),
    ]),
    scenario("promotion_archives_open", "Packaged post/cancel, foreground disclosure, permission matrix, advanced decision, and rollback archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p13-generated-report-present", files.p13GeneratedReport.exists, "The P13-25 generated report must exist before P14-22 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-notification-ownership-plan-v1", "P14-22 notification ownership plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Notification Ownership"), "P14-22 notification ownership documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-22") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/notification-ownership-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14NotificationOwnership"),
      "The pro.tasks_device_media surface must include the P14-22 evidence and Gradle gate.",
    ),
    check(
      "raw-notification-objects-stay-denied",
      !files.nodeBridgeSources.text.includes("RemoteViews") &&
        !files.nodeBridgeSources.text.includes("StatusBarNotification") &&
        !files.nodeBridgeSources.text.includes("NotificationListener"),
      "P14-22 must not expose raw RemoteViews, StatusBarNotification, or notification listener APIs through Node sources.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-notification-ownership-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-22",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      p13NotificationEvidenceImported: scenarioById(scenarios, "p13_notification_settings_imported").status === "ready",
      livePostCancelOwnershipReady: scenarioById(scenarios, "live_post_cancel_owned_resource").status === "ready",
      permissionManifestDenialsReady: scenarioById(scenarios, "permission_and_manifest_denials").status === "ready",
      foregroundDisclosureStopActionReady: false,
      packagedPostCancelReady: false,
      advancedNotificationDecisionReady: false,
      rollbackCleanupReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Notification Ownership",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P13 notification evidence imported: \`${report.decision.p13NotificationEvidenceImported}\``,
    `- Live post/cancel ownership ready: \`${report.decision.livePostCancelOwnershipReady}\``,
    `- Permission/manifest denials ready: \`${report.decision.permissionManifestDenialsReady}\``,
    `- Foreground disclosure/stop action ready: \`${report.decision.foregroundDisclosureStopActionReady}\``,
    `- Packaged post/cancel ready: \`${report.decision.packagedPostCancelReady}\``,
    `- Future promotion blockers: \`${report.decision.futurePromotionBlockers}\``,
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
  lines.push("", "## Blockers", "", "| Blocker | Classification | Required evidence |", "| --- | --- | --- |");
  report.blockers.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.classification}\` | ${entry.requiredEvidence} |`);
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

function scenario(id, description, checks, blockerIds = []) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return {
    id,
    description,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    missing,
    blockerIds,
  };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
}

function value(id, ok) {
  return check(id, ok, `failed contract check: ${id}`);
}

function token(file, expected) {
  return check(`token:${file.relative}:${expected}`, file.exists && file.text.includes(expected), `missing evidence token: ${expected}`);
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function parseJson(text) {
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function sameSet(actual, expected) {
  return Array.isArray(actual) && actual.length === expected.length && expected.every((entry) => actual.includes(entry));
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((entry) => actual.includes(entry));
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
      options.repoRoot = args[index + 1];
      index += 1;
    } else if (arg === "--report-dir") {
      options.reportDir = args[index + 1];
      index += 1;
    }
  }
  return options;
}

main();
