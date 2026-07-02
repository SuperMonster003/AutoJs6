#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-settings-power-promotion";
const REQUIRED_LIVE_METHODS = Object.freeze(["isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings"]);
const REQUIRED_MATRIX_ROWS = Object.freeze([
  "android12_battery_optimization_status_settings",
  "android15_plus_battery_optimization_status_settings",
  "exact_alarm_state_denial",
  "background_launch_restrictions",
  "oem_power_manager_notes",
]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_settings_power_matrix_contract",
  "phase15_notification_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "settings_power_android12_oem_matrix_archive",
  "settings_power_android15_plus_oem_matrix_archive",
  "settings_power_exact_alarm_state_archive",
  "settings_power_background_launch_restriction_archive",
  "settings_power_mutation_prompt_rollback_archive",
  "settings_power_packaged_disclosure_stop_action_archive",
  "settings_power_safe_downgrade_archive",
  "settings_power_raw_object_denial_security_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/settings-power-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_SETTINGS_POWER_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_18_SETTINGS_POWER_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15NotificationReport: "build/reports/nodejs/phase15-notification-promotion.json",
  phase14Plan: "docs/nodejs/phase14/settings-power-oem-background-matrix.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_23_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md",
  phase14Generated: "build/reports/nodejs/phase14-settings-power-oem-background-matrix.json",
  p13Generated: "build/reports/nodejs/phase13-notifications-settings-power-manager.json",
  p13Report: "docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  deviceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeDeviceBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  deviceTypes: "docs/nodejs/types/autojs6-node/device.d.ts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  rollbackPlaybook: "docs/nodejs/security/PROFILE_ROLLBACK_PLAYBOOK.md",
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
  console.log("Phase 15 settings/power promotion: " + report.decision.status);
  console.log("Phase 15 settings/power blockers: " + report.blockers.length);
  console.log("Phase 15 settings/power policy violations: " + report.policyViolations.length);
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
  const p15NotificationReport = parseJson(files.p15NotificationReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-18") || {};
  const settingsPowerContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "settings_power") || {};
  const settingsPowerAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "settings_power") || {};
  const matrix = plan.matrixContract || {};
  const rows = Array.isArray(plan.requiredDeviceRows) ? plan.requiredDeviceRows : [];
  const rowIds = rows.map((entry) => entry.id);
  const mutation = plan.mutationDenialContract || {};
  const openBlockers = Array.isArray(plan.openBlockers) ? plan.openBlockers : [];
  const phase14Decision = phase14Report.decision || {};
  const p15NotificationDecision = p15NotificationReport.decision || {};
  const scenarios = [
    scenario("phase14_settings_power_matrix_imported", "P14-23 settings/power OEM and background-launch matrix is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-settings-power-oem-background-matrix-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_p13_imported", phase14Decision.p13DevicePowerEvidenceImported === true),
      value("phase14_live_status_settings_ready", phase14Decision.liveStatusSettingsDryRunReady === true),
      value("phase14_mutation_flows_denied", phase14Decision.mutationFlowsDenied === true),
      value("phase14_shared_matrix_imported", phase14Decision.sharedDeviceMatrixImported === true),
      value("phase14_exact_alarm_not_ready", phase14Decision.exactAlarmStateReady === false),
      value("phase14_background_launch_not_ready", phase14Decision.backgroundLaunchMatrixReady === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-23 deploys the settings/power OEM and background-launch matrix gate"),
      token(files.phase14TaskReport, "`settings_power_android12_oem_matrix_archive`"),
      token(files.phase14PlanDoc, "This gate does not promote direct power-manager mutation"),
      token(files.phase14PlanDoc, "`device.openBatteryOptimizationSettings({ dryRun: true })`"),
    ]),
    scenario("phase15_notification_dependency", "P15-17 notification dependency remains partial and unpromoted", [
      value("p15_notification_status", statusOf(p15NotificationReport) === "partial"),
      value("p15_notification_not_promoted", p15NotificationDecision.notificationPromotionPromoted === false),
      value("p15_notification_runtime_authority", p15NotificationDecision.runtimeAuthorityChanged === false),
      token(files.p15NotificationReport, "\"notificationPromotionPromoted\": false"),
      token(files.p15NotificationReport, "\"phase15_task_device_media_doc_sync\""),
    ]),
    scenario("device_power_status_settings_boundary", "P15-18 preserves device.power status/settings dry-run and capability denial boundaries", [
      value("provider_name", matrix.providerName === "device"),
      value("capability", matrix.capability === "device"),
      value("power_capability", matrix.powerCapability === "device.power"),
      value("settings_schema", matrix.settingsResultSchema === "autojs6-node-battery-optimization-settings-v1"),
      value("live_methods", sameSet(matrix.currentLiveMethods || [], REQUIRED_LIVE_METHODS)),
      value("battery_status_ready", matrix.batteryOptimizationStatusReady === true),
      value("settings_dry_run_ready", matrix.settingsDryRunReady === true),
      value("exact_alarm_not_ready", matrix.exactAlarmStateReady === false),
      value("background_launch_not_ready", matrix.backgroundLaunchMatrixReady === false),
      value("reviewed_prompt_not_ready", matrix.reviewedPromptAndRollbackReady === false),
      value("matrix_rows", sameSet(rowIds, REQUIRED_MATRIX_ROWS)),
      token(files.protocol, "val deviceMethods = setOf(\"isScreenOn\", \"wakeUp\", \"vibrate\", \"isIgnoringBatteryOptimizations\", \"openBatteryOptimizationSettings\")"),
      token(files.protocol, "private fun isIgnoringBatteryOptimizations(): Boolean"),
      token(files.protocol, "private fun openBatteryOptimizationSettings(dryRun: Boolean): JSONObject"),
      token(files.protocol, "autojs6-node-battery-optimization-settings-v1"),
      token(files.protocol, "Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
      token(files.permissionManifest, "const val DEVICE_POWER = \"device.power\""),
      token(files.permissionManifest, "\"isIgnoringBatteryOptimizations\", \"openBatteryOptimizationSettings\" -> listOf(DEVICE, DEVICE_POWER)"),
      token(files.deviceTest, "NodeBridgeRequest(\"device-power-settings-1\", \"device\", \"openBatteryOptimizationSettings\""),
      token(files.deviceTest, "device.power_permissions=true"),
      token(files.deviceTest, "device.power.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|device.power|0|0"),
      token(files.deviceTypes, "isIgnoringBatteryOptimizations(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;"),
      token(files.deviceTypes, "openBatteryOptimizationSettings(options?: DevicePowerSettingsOptions): Promise<DevicePowerSettingsResult>;"),
      token(files.deviceTypes, "readonly ignoringBatteryOptimizations: boolean;"),
      token(files.packagedSmokeTest, "packaged.notifications_power.device_power=true"),
    ]),
    scenario("p13_device_power_imported", "P13-25 device.power status/settings evidence remains imported", [
      value("p13_status", statusOf(p13Report) === "partial"),
      token(files.p13Report, "`device.isIgnoringBatteryOptimizations()` returns the Android"),
      token(files.p13Report, "`device.openBatteryOptimizationSettings()` returns a JSON-safe"),
      token(files.p13Report, "P13-25 v1.1 stable-denies direct power-manager authority"),
      token(files.conformanceTest, "device.openBatteryOptimizationSettings"),
    ]),
    scenario("mutation_raw_object_denial_boundary", "Power mutation, exact alarm, wake lock, background launch, and raw Android objects remain denied", [
      value("request_ignore_mutation_denied", mutation.requestIgnoreBatteryOptimizationsMutationPromoted === false),
      value("doze_mutation_denied", mutation.directDozeMutationPromoted === false),
      value("wakelock_denied", mutation.wakeLockPromoted === false),
      value("exact_alarm_denied", mutation.exactAlarmSchedulingPromoted === false),
      value("background_launch_denied", mutation.backgroundLaunchBypassPromoted === false),
      value("reviewed_prompt_required", mutation.reviewedPromptRequired === true),
      value("rollback_required", mutation.rollbackPathRequired === true),
      value("stable_error_required", mutation.stableErrorRequired === true),
      value("raw_objects_denied", includesAll(mutation.rawObjectsDenied || [], [
        "PowerManager",
        "WakeLock",
        "AlarmManager",
        "PendingIntent",
        "Intent",
        "Context",
        "Settings Activity result handle",
        "OEM manager",
        "Java object",
      ])),
      token(files.providerRegistry, "\"blockedMethods\" to \"imei,androidId,serial,brand,manufacturer,accounts,macAddress,ipAddress,rawPowerManager,wakelock,ignoreBatteryOptimizationRequest,exactAlarm,backgroundLaunchBypass\""),
      token(files.providerRegistry, "\"powerMutationPolicy\" to \"status_and_settings_dry_run_only_mutation_flows_denied_until_prompt_rollback_device_matrix\""),
      token(files.providerRegistry, "\"exactAlarmPolicy\" to \"state_matrix_only_no_schedule_exact_alarm_or_alarm_manager_authority\""),
      token(files.providerRegistry, "\"phase14SettingsPowerOemBackgroundMatrix\" to \"P14-23 settings/power OEM and background-launch matrix gate\""),
      token(files.providerRegistry, "\"powerOemMatrixPolicy\" to \"android12_android15_battery_optimization_exact_alarm_background_launch_oem_notes_required_before_mutation\""),
      value("raw_power_manager_not_exposed", !files.nodeBridgeSources.text.includes("PowerManager")),
      value("raw_wakelock_not_exposed", !files.nodeBridgeSources.text.includes("WakeLock")),
      value("raw_alarm_manager_not_exposed", !files.nodeBridgeSources.text.includes("AlarmManager")),
      value("request_ignore_not_exposed", !files.nodeBridgeSources.text.includes("requestIgnoreBatteryOptimizations")),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep settings_power behind device, packaged, disclosure, rollback, security, type, docs, Safe downgrade, and raw-object bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("settings_power_contract_provider_poc", settingsPowerContract.currentState === "provider_poc"),
      value("settings_power_contract_p15_task", includesAll(settingsPowerContract.p15Tasks || [], ["P15-18"])),
      value("settings_power_contract_backlog", includesAll(settingsPowerContract.sourceBacklogTasks || [], ["P14-23"])),
      value("settings_power_contract_evidence_bars", includesAll(settingsPowerContract.requiredEvidence || [], [
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
      ])),
      value("settings_power_audit_provider_poc", settingsPowerAudit.currentState === "provider_poc"),
      value("settings_power_audit_disable_flag", settingsPowerAudit.disableFlag === "node.provider.settings_power.enabled"),
      value("settings_power_audit_diagnostic", settingsPowerAudit.diagnosticCode === "NODE_PROVIDER_SETTINGS_POWER_DISABLED"),
      value("settings_power_audit_safe_downgrade", settingsPowerAudit.safeDowngrade === "keep settings and power mutations denied while preserving dry-run status reads"),
      value("settings_power_audit_disclosure_fields", includesAll(settingsPowerAudit.requiredDisclosureFields || [], [
        "foregroundNotificationOwnership",
        "stopAction",
        "taskRemovalCleanup",
        "safeDowngradeDisclosure",
      ])),
      token(files.rollbackPlaybook, "`device.power`"),
    ]),
    scenario("promotion_blockers_open", "Android 12/15+, exact alarm, background launch, OEM, prompt, packaged disclosure, Safe downgrade, security, and docs sync remain future work", [
      value("open_blockers", sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-18 settings/power promotion gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-23"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "device_oem_matrix"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15SettingsPowerPromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15SettingsPowerPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-settings-power-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15SettingsPowerPromotion"),
      token(files.testing, ":app:verifyNodePhase15SettingsPowerPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15SettingsPowerPromotion"),
      token(files.phase15Tasks, "phase15-settings-power-promotion.json"),
      token(files.phase15Tasks, "settings_power_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-settings-power-promotion-plan-v1" &&
        plan.task === "P15-18" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-23"]) &&
        plan.providerId === "settings_power" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-18 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-18 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "matrix-covers-required-rows",
      sameSet(rowIds, REQUIRED_MATRIX_ROWS) &&
        sameSet(matrix.currentLiveMethods || [], REQUIRED_LIVE_METHODS) &&
        matrix.batteryOptimizationStatusReady === true &&
        matrix.settingsDryRunReady === true,
      "P15-18 matrix must cover status/settings dry-run and reserve Android 12/15+, exact-alarm, background-launch, and OEM rows.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-18 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-settings-power-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.p13DevicePowerEvidenceImported === true &&
        phase14Decision.liveStatusSettingsDryRunReady === true &&
        phase14Decision.mutationFlowsDenied === true &&
        phase14Decision.backgroundLaunchMatrixReady === false,
      "P15-18 requires P14-23 settings/power matrix report to be partial with OEM/background-launch archives still open.",
    ),
    check(
      "phase15-notification-dependency-partial",
      statusOf(p15NotificationReport) === "partial" &&
        p15NotificationDecision.notificationPromotionPromoted === false &&
        p15NotificationDecision.runtimeAuthorityChanged === false,
      "P15-18 depends on P15-17 notification promotion remaining partial and unpromoted.",
    ),
    check(
      "raw-power-objects-stay-denied",
      !files.nodeBridgeSources.text.includes("PowerManager") &&
        !files.nodeBridgeSources.text.includes("WakeLock") &&
        !files.nodeBridgeSources.text.includes("AlarmManager") &&
        !files.nodeBridgeSources.text.includes("requestIgnoreBatteryOptimizations"),
      "P15-18 must not expose raw PowerManager, WakeLock, AlarmManager, or request-ignore-battery-optimization mutation APIs through Node sources.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-18 evidence scenarios must be ready while settings/power archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15SettingsPowerPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-settings-power-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15SettingsPowerPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15SettingsPowerPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15SettingsPowerPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-18 settings/power promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-18: Close settings/power OEM and background-launch matrix.") &&
        files.phase15Tasks.text.includes("phase15-settings-power-promotion.json") &&
        files.phase15Tasks.text.includes("settings_power_android12_oem_matrix_archive"),
      "P15-18 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-settings-power-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15SettingsPowerPromotion",
    task: "P15-18",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    matrixContract: matrix,
    mutationDenialContract: mutation,
    scenarios,
    checks,
    policyViolations,
    blockers: openBlockers.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14SettingsPowerMatrixReady: scenarioById(scenarios, "phase14_settings_power_matrix_imported").status === "ready",
      notificationDependencyReady: statusOf(p15NotificationReport) === "partial" && p15NotificationDecision.notificationPromotionPromoted === false,
      liveStatusSettingsDryRunReady: scenarioById(scenarios, "device_power_status_settings_boundary").status === "ready",
      mutationFlowsDenied: scenarioById(scenarios, "mutation_raw_object_denial_boundary").status === "ready",
      android12OemMatrixReady: false,
      android15PlusOemMatrixReady: false,
      exactAlarmStateReady: false,
      backgroundLaunchMatrixReady: false,
      reviewedPromptRollbackReady: false,
      packagedDisclosureStopActionReady: false,
      safeDowngradeReady: false,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: openBlockers.length,
      settingsPowerPromoted: false,
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
    "# Phase 15 Settings/Power Promotion",
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
      console.log("Usage: node verify-node-phase15-settings-power-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
