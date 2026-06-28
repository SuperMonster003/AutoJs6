#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-notifications-settings-power-manager";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  notificationsTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNotificationsBridgeInstrumentationTest.kt",
  deviceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeDeviceBridgeInstrumentationTest.kt",
  permissionManifestTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt",
  providerRegistryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
  notificationsTypes: "docs/nodejs/types/autojs6-node/notifications.d.ts",
  deviceTypes: "docs/nodejs/types/autojs6-node/device.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  shellRootShizukuReport: "docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md",
  projectTool: "tools/nodejs/project/autojs6-node-project.js",
  gradleGate: "app/node-phase13-gate.gradle.kts",
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
  console.log("Phase 13 notifications/settings/power manager: " + report.decision.status);
  console.log("Phase 13 notifications/settings/power manager blockers: " + report.blockers.length);
  console.log("Phase 13 notifications/settings/power manager policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
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
  const proSurface = surface(files.surfaceManifest.text, "pro.tasks_app_shell_media_utils");
  const scenarios = [
    scenario("notification_settings_status", "notifications exposes status and user-visible settings evidence", [
      token(files.permissionManifest, 'const val NOTIFICATIONS_SETTINGS = "notifications.settings"'),
      token(files.permissionManifest, '"notification.settings" to NOTIFICATIONS_SETTINGS'),
      token(files.permissionManifest, '"openSettings" -> listOf(NOTIFICATIONS, NOTIFICATIONS_SETTINGS)'),
      token(files.protocol, 'val notificationsMethods = setOf("notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings")'),
      token(files.protocol, '"getPermissionStatus" ->'),
      token(files.protocol, '"openSettings" ->'),
      token(files.protocol, "private fun notificationPermissionStatus(): JSONObject"),
      token(files.protocol, "private fun openNotificationSettings(dryRun: Boolean): JSONObject"),
      token(files.protocol, "private fun notificationSettingsIntent(): Intent"),
      token(files.nodeBridgeSources, "function getPermissionStatus(options)"),
      token(files.nodeBridgeSources, "function openSettings(options)"),
      token(files.nodeBridgeSources, "openNotificationSettings: openSettings"),
      token(files.notificationsTypes, "NotificationPermissionStatus"),
      token(files.notificationsTypes, "NotificationSettingsResult"),
      token(files.notificationsTest, "notifications.settings_permissions=true"),
      token(files.conformanceTest, 'expectExports("notifications", notifications, ["notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings", "openNotificationSettings"])'),
      token(files.typeSmoke, "const notificationStatus: notifications.NotificationPermissionStatus"),
    ], [
      "Notification actions, listener callbacks, groups, styles, remote input, and richer layouts remain blocked.",
      "Direct Do-Not-Disturb, exact-alarm, foreground-disclosure, and packaged notification action evidence remain blocked.",
    ]),
    scenario("device_power_status", "device exposes explicit battery optimization status/settings evidence", [
      token(files.permissionManifest, 'const val DEVICE_POWER = "device.power"'),
      token(files.permissionManifest, '"power-manager" to DEVICE_POWER'),
      token(files.permissionManifest, '"isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings" -> listOf(DEVICE, DEVICE_POWER)'),
      token(files.permissionManifest, "required == NOTIFICATIONS_SETTINGS ||"),
      token(files.permissionManifest, "required == DEVICE_POWER ||"),
      token(files.protocol, 'val deviceMethods = setOf("isScreenOn", "wakeUp", "vibrate", "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings")'),
      token(files.protocol, '"isIgnoringBatteryOptimizations" -> runCatching'),
      token(files.protocol, '"openBatteryOptimizationSettings" -> runCatching'),
      token(files.protocol, "private fun isIgnoringBatteryOptimizations(): Boolean"),
      token(files.protocol, "private fun openBatteryOptimizationSettings(dryRun: Boolean): JSONObject"),
      token(files.nodeBridgeSources, "function isIgnoringBatteryOptimizations(options)"),
      token(files.nodeBridgeSources, "function openBatteryOptimizationSettings(options)"),
      token(files.nodeBridgeSources, 'permissions: ["device", "device.power"]'),
      token(files.deviceTypes, "DevicePowerSettingsResult"),
      token(files.deviceTest, "device.power.manifest.denied"),
      token(files.conformanceTest, 'expectExports("device", device, ["isScreenOn", "wakeUp", "vibrate", "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings"])'),
      token(files.typeSmoke, "const compatBatteryIgnored: boolean = await rhinoCompat.device.isIgnoringBatteryOptimizations"),
    ], [
      "Wake locks, direct Doze mutation, request-ignore-battery-optimizations flow, and power-save callbacks remain blocked.",
      "OEM power-manager matrices, packaged disclosure, and background launch behavior still need device evidence.",
    ]),
    scenario("provider_registry_boundary", "provider registry and tests keep the boundary JSON-only and auditable", [
      token(files.providerRegistry, '"settingsCapability" to NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS'),
      token(files.providerRegistry, '"powerCapability" to NodeBridgePermissionManifest.DEVICE_POWER'),
      token(files.providerRegistry, '"phase13Promotion" to "P13-25 partial notifications/settings/power manager evidence gate"'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw Notification'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw PowerManager'),
      token(files.providerRegistryTest, 'assertEquals(20, definitions.size)'),
      token(files.providerRegistryTest, 'id = "device"'),
      token(files.permissionManifestTest, "NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS"),
      token(files.permissionManifestTest, "NodeBridgePermissionManifest.DEVICE_POWER"),
      token(files.deviceTest, "device.sensitive_absent=true"),
      token(files.notificationsTest, "assertNotificationResource"),
      token(files.p13Report, "`Notification`, `NotificationCompat.Builder`, `NotificationManager`"),
      token(files.p13Report, "`PendingIntent`, `RemoteViews`, `Intent`, `PowerManager`, `WakeLock`"),
      token(files.projectTool, '"notifications.settings"'),
      token(files.projectTool, '"device.power"'),
    ], [
      "Notification and power-manager APIs still need packaged install/run evidence before profile promotion.",
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-25 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-25 partial notifications/settings/power manager gate"),
      token(files.gradleGate, "verifyNodePhase13NotificationsSettingsPowerManager"),
      token(files.testingDoc, "verifyNodePhase13NotificationsSettingsPowerManager"),
      token(files.ciGatesDoc, "verifyNodePhase13NotificationsSettingsPowerManager"),
      token(files.compatibilityProfile, "P13-25 records notifications/settings/power manager promotion as partial"),
      token(files.securityModel, "P13-25 records notifications/settings/power manager promotion as partial"),
      token(files.proComparison, "P13-25 records notifications/settings/power manager promotion as partial"),
      token(files.proAutoxNotes, "P13-25 records notifications/settings/power manager promotion as partial"),
      token(files.capabilityMatrix, "P13-25 notifications/settings/power manager"),
      token(files.typesReadme, "P13-25 records the partial notifications/settings/power manager gate"),
      token(files.surfaceManifestDoc, "phase13-notifications-settings-power-manager"),
      token(files.shellRootShizukuReport, "Status: ready"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-25"), "P13-25 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-25 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must remain partial/poc after P13-25 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-25 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-25 evidence.",
    ),
    check(
      "settings-capability-exact",
      files.permissionManifest.text.includes("required == NOTIFICATIONS_SETTINGS ||") &&
        files.permissionManifest.text.includes("required == DEVICE_POWER ||"),
      "notifications.settings and device.power must be exact capabilities, not implied by their parent tokens.",
    ),
    check(
      "no-raw-power-or-settings-module",
      !files.nodeBridgeSources.text.includes('if (name === "power_manager")') &&
        !files.nodeBridgeSources.text.includes('if (name === "settings")') &&
        !files.nodeBridgeSources.text.includes('if (name === "power")'),
      "P13-25 must not expose raw require(\"settings\"), require(\"power\"), or require(\"power_manager\") modules.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-notifications-settings-power-manager-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13NotificationsSettingsPowerManager",
    task: "P13-25",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.tasks_app_shell_media_utils",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      notificationSettingsReady: scenarioById(scenarios, "notification_settings_status").status !== "missing_evidence",
      devicePowerSettingsReady: scenarioById(scenarios, "device_power_status").status !== "missing_evidence",
      advancedNotificationActionsReady: false,
      directPowerManagerReady: false,
      packagedSettingsPowerReady: false,
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
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function surface(text, id) {
  try {
    const parsed = JSON.parse(text);
    return (parsed.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [];
  lines.push(`# ${report.gate}`);
  lines.push("");
  lines.push(`- Task: ${report.task}`);
  lines.push(`- Status: ${report.decision.status}`);
  lines.push(`- Final blocker closable: ${report.decision.finalBlockerClosable}`);
  lines.push(`- Policy violations: ${report.policyViolations.length}`);
  lines.push(`- Blockers: ${report.blockers.length}`);
  lines.push("");
  lines.push("## Scenarios");
  report.scenarios.forEach((entry) => {
    lines.push("");
    lines.push(`### ${entry.id}`);
    lines.push("");
    lines.push(`- ${entry.title}`);
    lines.push(`- Status: ${entry.status}`);
    lines.push(`- Missing/planned remaining: ${entry.remaining.length}`);
    entry.remaining.forEach((item) => lines.push(`  - ${item}`));
  });
  if (report.policyViolations.length > 0) {
    lines.push("");
    lines.push("## Policy Violations");
    report.policyViolations.forEach((item) => lines.push(`- ${item}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const result = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      result.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      result.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      result.failOnBlocked = true;
    }
  }
  return result;
}

main();
