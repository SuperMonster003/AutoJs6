#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-settings-power-oem-background-matrix";
const REQUIRED_LIVE_METHODS = Object.freeze(["isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings"]);
const REQUIRED_MATRIX_ROWS = Object.freeze([
  "android12_battery_optimization_status_settings",
  "android15_plus_battery_optimization_status_settings",
  "exact_alarm_state_denial",
  "background_launch_restrictions",
  "oem_power_manager_notes",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "settings_power_android12_oem_matrix_archive",
    classification: "future_promotion",
    requiredEvidence: "Android 12 class device archive covering battery optimization status, settings dry-run/round-trip, capabilities, package identity, denial copy, OEM notes, task removal, crash recovery, and rollback behavior.",
  },
  {
    id: "settings_power_android15_plus_oem_matrix_archive",
    classification: "future_promotion",
    requiredEvidence: "Android 15+ class device archive covering background launch restrictions, battery optimization status/settings behavior, capabilities, package identity, denial copy, OEM notes, task removal, crash recovery, and rollback behavior.",
  },
  {
    id: "settings_power_exact_alarm_state_archive",
    classification: "future_promotion",
    requiredEvidence: "Exact-alarm state observation or stable denial archive proving AlarmManager, PendingIntent, and exact scheduling mutation authority do not cross into Node.",
  },
  {
    id: "settings_power_background_launch_restriction_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged and interactive background-launch restriction archive proving Android 15+ restrictions, denial diagnostics, and foreground disclosure handoff behavior.",
  },
  {
    id: "settings_power_mutation_prompt_rollback_archive",
    classification: "future_promotion",
    requiredEvidence: "Reviewed prompt copy and rollback archive for any future request-ignore-battery-optimizations, direct power-manager, wake-lock, exact-alarm, or background-launch mutation flow.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_23_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/settings-power-oem-background-matrix.json",
  planDoc: "docs/nodejs/phase14/PHASE14_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md",
  p13GeneratedReport: "phase13-notifications-settings-power-manager.json",
  p13Report: "docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md",
  notificationOwnershipGenerated: "phase14-notification-ownership.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  deviceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeDeviceBridgeInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  deviceTypes: "docs/nodejs/types/autojs6-node/device.d.ts",
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
  console.log("Phase 14 settings/power OEM matrix: " + report.decision.status);
  console.log("Phase 14 settings/power blockers: " + report.blockers.length);
  console.log("Phase 14 settings/power policy violations: " + report.policyViolations.length);
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
  const matrix = plan.matrixContract || {};
  const mutation = plan.mutationDenialContract || {};
  const rows = Array.isArray(plan.requiredDeviceRows) ? plan.requiredDeviceRows : [];
  const rowIds = rows.map((entry) => entry.id);
  const p13Generated = parseJson(files.p13GeneratedReport.text);
  const notificationOwnership = parseJson(files.notificationOwnershipGenerated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const deviceMatrix = contracts.deviceEvidenceMatrix || {};
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const scenarios = [
    scenario("settings_power_matrix_contract", "P14-23 records settings/power OEM and background-launch matrix requirements without promotion", [
      value("schema", plan.schema === "autojs6-node-phase14-settings-power-oem-background-matrix-v1"),
      value("task", plan.task === "P14-23"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-25", "P14-04", "P14-22"])),
      value("providerName", matrix.providerName === "device"),
      value("capability", matrix.capability === "device"),
      value("powerCapability", matrix.powerCapability === "device.power"),
      value("settingsResultSchema", matrix.settingsResultSchema === "autojs6-node-battery-optimization-settings-v1"),
      value("liveMethods", sameSet(matrix.currentLiveMethods || [], REQUIRED_LIVE_METHODS)),
      value("mutationAuthorityDenied", matrix.currentMutationAuthority === "not_exposed"),
      value("batteryStatusReady", matrix.batteryOptimizationStatusReady === true),
      value("settingsDryRunReady", matrix.settingsDryRunReady === true),
      value("exactAlarmStateNotReady", matrix.exactAlarmStateReady === false),
      value("backgroundMatrixNotReady", matrix.backgroundLaunchMatrixReady === false),
      value("oemNotesNotReady", matrix.oemNotesReady === false),
      value("android15RestrictionMatrixNotReady", matrix.android15RestrictionMatrixReady === false),
      value("reviewedPromptAndRollbackNotReady", matrix.reviewedPromptAndRollbackReady === false),
      value("matrixRows", sameSet(rowIds, REQUIRED_MATRIX_ROWS)),
      token(files.planDoc, "This gate does not promote direct power-manager mutation"),
    ]),
    scenario("p13_device_power_imported", "P13-25 device.power status/settings evidence is imported", [
      value("p13GeneratedPresent", files.p13GeneratedReport.exists),
      value("p13GeneratedPartial", statusOf(p13Generated) === "partial"),
      value("p13DevicePowerSettingsReady", (p13Generated.decision || {}).devicePowerSettingsReady === true),
      value("p13PackagedSettingsPowerReady", (p13Generated.decision || {}).packagedSettingsPowerReady === true),
      token(files.p13Report, "`device.isIgnoringBatteryOptimizations()` returns the Android"),
      token(files.p13Report, "`device.openBatteryOptimizationSettings()` returns a JSON-safe"),
      token(files.p13Report, "P13-25 v1.1 stable-denies direct power-manager authority"),
      token(files.deviceTypes, "isIgnoringBatteryOptimizations(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;"),
      token(files.deviceTypes, "openBatteryOptimizationSettings(options?: DevicePowerSettingsOptions): Promise<DevicePowerSettingsResult>;"),
      token(files.deviceTypes, "readonly ignoringBatteryOptimizations: boolean;"),
    ]),
    scenario("live_device_power_status_settings", "Live device provider exposes status/settings dry-run and manifest denial only", [
      token(files.protocol, "val deviceMethods = setOf(\"isScreenOn\", \"wakeUp\", \"vibrate\", \"isIgnoringBatteryOptimizations\", \"openBatteryOptimizationSettings\")"),
      token(files.protocol, "private fun isIgnoringBatteryOptimizations(): Boolean"),
      token(files.protocol, "private fun openBatteryOptimizationSettings(dryRun: Boolean): JSONObject"),
      token(files.protocol, "autojs6-node-battery-optimization-settings-v1"),
      token(files.protocol, "Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
      token(files.permissionManifest, "const val DEVICE_POWER = \"device.power\""),
      token(files.permissionManifest, "\"isIgnoringBatteryOptimizations\", \"openBatteryOptimizationSettings\" -> listOf(DEVICE, DEVICE_POWER)"),
      token(files.deviceTest, "assertEquals(NodeBridgePermissionManifest.DEVICE_POWER, NodeBridgePermissionManifest.normalizeCapability(\"power-manager\"))"),
      token(files.deviceTest, "NodeBridgeRequest(\"device-power-settings-1\", \"device\", \"openBatteryOptimizationSettings\""),
      token(files.deviceTest, "device.power_permissions=true"),
      token(files.deviceTest, "device.power.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|device.power|0|0"),
    ]),
    scenario("mutation_flows_stay_denied", "Power mutation, exact alarm, wake lock, and background-launch bypass authority stay denied", [
      value("requestIgnoreBatteryOptimizationsDenied", mutation.requestIgnoreBatteryOptimizationsMutationPromoted === false),
      value("directDozeDenied", mutation.directDozeMutationPromoted === false),
      value("wakeLockDenied", mutation.wakeLockPromoted === false),
      value("exactAlarmSchedulingDenied", mutation.exactAlarmSchedulingPromoted === false),
      value("backgroundLaunchBypassDenied", mutation.backgroundLaunchBypassPromoted === false),
      value("reviewedPromptRequired", mutation.reviewedPromptRequired === true),
      value("rollbackPathRequired", mutation.rollbackPathRequired === true),
      value("stableErrorRequired", mutation.stableErrorRequired === true),
      value("rawObjectsDenied", includesAll(mutation.rawObjectsDenied || [], ["PowerManager", "WakeLock", "AlarmManager", "PendingIntent", "Intent", "Context", "OEM manager"])),
      token(files.providerRegistry, "\"blockedMethods\" to \"imei,androidId,serial,brand,manufacturer,accounts,macAddress,ipAddress,rawPowerManager,wakelock,ignoreBatteryOptimizationRequest,exactAlarm,backgroundLaunchBypass\""),
      token(files.providerRegistry, "\"powerMutationPolicy\" to \"status_and_settings_dry_run_only_mutation_flows_denied_until_prompt_rollback_device_matrix\""),
      token(files.providerRegistry, "\"exactAlarmPolicy\" to \"state_matrix_only_no_schedule_exact_alarm_or_alarm_manager_authority\""),
    ]),
    scenario("shared_device_matrix_and_disclosure_policy", "Shared Phase 14 device matrix and disclosure contracts cover power settings", [
      value("requiredDeviceClasses", sameSet(deviceMatrix.requiredDeviceClasses || [], ["android_12_class", "android_15_plus_class"])),
      value("requiredFields", includesAll(deviceMatrix.requiredFields || [], ["backgroundStartBehavior", "settingsRoundTripBehavior", "foregroundDisclosureBehavior", "oemQuirks", "reportPath"])),
      value("powerSettingsDisclosure", includesAll(disclosure.requiredForSurfaces || [], ["power_settings"])),
      value("sharedDisclosureFields", includesAll(disclosure.requiredFields || [], ["installReviewCopy", "runReviewCopy", "foregroundNotificationOwnership", "rollbackBehavior"])),
      value("rawWakeLockDenied", includesAll(disclosure.rawObjectDenials || [], ["WakeLock", "Intent"])),
      value("notificationOwnershipImported", files.notificationOwnershipGenerated.exists && statusOf(notificationOwnership) === "partial"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-23 gate", [
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-23") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/settings-power-oem-background-matrix.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14SettingsPowerOemBackgroundMatrix")),
      token(files.providerRegistry, "\"phase14SettingsPowerOemBackgroundMatrix\" to \"P14-23 settings/power OEM and background-launch matrix gate\""),
      token(files.providerRegistry, "\"powerOemMatrixPolicy\" to \"android12_android15_battery_optimization_exact_alarm_background_launch_oem_notes_required_before_mutation\""),
      token(files.surfaceManifestDoc, "P14-23 adds the settings/power OEM and background-launch matrix gate"),
      token(files.phase14Tasks, "Settings/power OEM and background-launch matrix gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14SettingsPowerOemBackgroundMatrix"),
      token(files.testing, ":app:verifyNodePhase14SettingsPowerOemBackgroundMatrix"),
      token(files.ciGates, ":app:verifyNodePhase14SettingsPowerOemBackgroundMatrix"),
      token(files.gradle14, "verifyNodePhase14SettingsPowerOemBackgroundMatrix"),
      token(files.gradle14, "verify-node-phase14-settings-power-oem-background-matrix.js"),
      token(files.securityModel, "P14-23 records settings/power OEM and background-launch matrix evidence"),
      token(files.errorCatalog, "P14-23 settings/power denial sync"),
      token(files.compatibilityProfile, "P14-23 records settings/power OEM and background-launch matrix evidence"),
    ]),
    scenario("promotion_archives_open", "Android 12/15+, exact alarm, background launch, OEM, prompt, and rollback archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p13-generated-report-present", files.p13GeneratedReport.exists, "The P13-25 generated report must exist before P14-23 runs."),
    check("p14-notification-ownership-report-present", files.notificationOwnershipGenerated.exists, "The P14-22 generated report must exist before P14-23 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-settings-power-oem-background-matrix-v1", "P14-23 settings/power matrix plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Settings/Power OEM Background Matrix"), "P14-23 settings/power matrix documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-23") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/settings-power-oem-background-matrix.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14SettingsPowerOemBackgroundMatrix"),
      "The pro.tasks_device_media surface must include the P14-23 evidence and Gradle gate.",
    ),
    check(
      "raw-power-objects-stay-denied",
      !files.nodeBridgeSources.text.includes("PowerManager") &&
        !files.nodeBridgeSources.text.includes("WakeLock") &&
        !files.nodeBridgeSources.text.includes("AlarmManager") &&
        !files.nodeBridgeSources.text.includes("requestIgnoreBatteryOptimizations"),
      "P14-23 must not expose raw power-manager, wake-lock, AlarmManager, or battery optimization mutation APIs through Node sources.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-settings-power-oem-background-matrix-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-23",
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
      p13DevicePowerEvidenceImported: scenarioById(scenarios, "p13_device_power_imported").status === "ready",
      liveStatusSettingsDryRunReady: scenarioById(scenarios, "live_device_power_status_settings").status === "ready",
      mutationFlowsDenied: scenarioById(scenarios, "mutation_flows_stay_denied").status === "ready",
      sharedDeviceMatrixImported: scenarioById(scenarios, "shared_device_matrix_and_disclosure_policy").status === "ready",
      android12OemMatrixReady: false,
      android15PlusOemMatrixReady: false,
      exactAlarmStateReady: false,
      backgroundLaunchMatrixReady: false,
      reviewedPromptRollbackReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Settings/Power OEM Background Matrix",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P13 device.power evidence imported: \`${report.decision.p13DevicePowerEvidenceImported}\``,
    `- Live status/settings dry-run ready: \`${report.decision.liveStatusSettingsDryRunReady}\``,
    `- Mutation flows denied: \`${report.decision.mutationFlowsDenied}\``,
    `- Shared device matrix imported: \`${report.decision.sharedDeviceMatrixImported}\``,
    `- Android 12 OEM matrix ready: \`${report.decision.android12OemMatrixReady}\``,
    `- Android 15+ OEM matrix ready: \`${report.decision.android15PlusOemMatrixReady}\``,
    `- Exact-alarm state ready: \`${report.decision.exactAlarmStateReady}\``,
    `- Background-launch matrix ready: \`${report.decision.backgroundLaunchMatrixReady}\``,
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
