#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-recorder-promotion";
const REQUIRED_CURRENT_METHODS = Object.freeze(["getStatus"]);
const REQUIRED_FUTURE_METHODS = Object.freeze(["start", "stop", "pause", "resume", "getStatus"]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_recorder_provider_contract",
  "phase15_settings_power_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "recorder_runtime_permission_prompt_archive",
  "recorder_foreground_disclosure_stop_action_archive",
  "recorder_temp_output_ownership_cleanup_archive",
  "recorder_crash_cleanup_session_resource_archive",
  "recorder_packaged_start_stop_stress_archive",
  "recorder_safe_downgrade_archive",
  "recorder_raw_object_denial_security_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/recorder-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_RECORDER_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_19_RECORDER_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15SettingsPowerReport: "build/reports/nodejs/phase15-settings-power-promotion.json",
  phase14Plan: "docs/nodejs/phase14/recorder-provider-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_RECORDER_PROVIDER.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_24_RECORDER_PROVIDER.md",
  phase14Generated: "build/reports/nodejs/phase14-recorder-provider.json",
  p13Generated: "build/reports/nodejs/phase13-media-recorder-mediainfo-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  mediaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  recorderTypes: "docs/nodejs/types/autojs6-node/recorder.d.ts",
  privacyDisclosurePolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
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
  console.log("Phase 15 recorder promotion: " + report.decision.status);
  console.log("Phase 15 recorder blockers: " + report.blockers.length);
  console.log("Phase 15 recorder policy violations: " + report.policyViolations.length);
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
  const p15SettingsPowerReport = parseJson(files.p15SettingsPowerReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-19") || {};
  const recorderContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "recorder") || {};
  const recorderAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "recorder") || {};
  const recorder = plan.recorderContract || {};
  const session = plan.sessionContract || {};
  const failClosed = plan.failClosedContract || {};
  const openBlockers = Array.isArray(plan.openBlockers) ? plan.openBlockers : [];
  const phase14Decision = phase14Report.decision || {};
  const p13Decision = p13Report.decision || {};
  const p15SettingsPowerDecision = p15SettingsPowerReport.decision || {};
  const scenarios = [
    scenario("phase14_recorder_provider_imported", "P14-24 recorder provider contract is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-recorder-provider-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_p13_imported", phase14Decision.p13RecorderEvidenceImported === true),
      value("phase14_fail_closed", phase14Decision.currentRecorderFailClosed === true),
      value("phase14_session_boundary", phase14Decision.sessionPromotionBoundaryReady === true),
      value("phase14_runtime_prompt_not_ready", phase14Decision.runtimePermissionPromptReady === false),
      value("phase14_foreground_not_ready", phase14Decision.foregroundDisclosureStopActionReady === false),
      value("phase14_packaged_stress_not_ready", phase14Decision.packagedStartStopStressReady === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-24 deploys the real recorder session promotion gate"),
      token(files.phase14TaskReport, "`recorder_runtime_permission_prompt_archive`"),
      token(files.phase14PlanDoc, "This gate does not promote real microphone capture yet"),
      token(files.phase14PlanDoc, "`recorder.getStatus()`"),
    ]),
    scenario("phase15_settings_power_dependency", "P15-18 settings/power dependency remains partial and unpromoted", [
      value("p15_settings_power_status", statusOf(p15SettingsPowerReport) === "partial"),
      value("p15_settings_power_not_promoted", p15SettingsPowerDecision.settingsPowerPromoted === false),
      value("p15_settings_power_runtime_authority", p15SettingsPowerDecision.runtimeAuthorityChanged === false),
      token(files.p15SettingsPowerReport, "\"settingsPowerPromoted\": false"),
      token(files.p15SettingsPowerReport, "\"phase15_task_device_media_doc_sync\""),
    ]),
    scenario("recorder_status_fail_closed_boundary", "P15-19 preserves recorder status and start/stop denial boundaries", [
      value("provider_name", recorder.providerName === "recorder"),
      value("capability", recorder.capability === "media.recording"),
      value("base_capability", recorder.baseCapability === "media"),
      value("android_permissions", includesAll(recorder.androidPermissions || [], [
        "android.permission.RECORD_AUDIO",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE",
      ])),
      value("status_schema", recorder.statusSchema === "autojs6-node-recorder-status-v1"),
      value("current_methods", sameSet(recorder.currentLiveMethods || [], REQUIRED_CURRENT_METHODS)),
      value("future_methods", sameSet(recorder.futureSessionMethods || [], REQUIRED_FUTURE_METHODS)),
      value("status_ready", recorder.currentStatusEvidenceReady === true),
      value("start_stop_not_promoted", recorder.currentStartStopPromoted === false),
      value("packaged_boundary_ready", recorder.currentPackagedBoundaryReady === true),
      value("screen_recording_not_promoted", recorder.screenRecordingPromoted === false),
      value("internal_audio_not_promoted", recorder.internalAudioCapturePromoted === false),
      value("p13_recorder_status_ready", p13Decision.recorderStatusReady === true),
      value("p13_recorder_start_stop_not_ready", p13Decision.recorderStartStopReady === false),
      token(files.protocol, "val recorderMethods = setOf(\"getStatus\", \"start\", \"stop\")"),
      token(files.protocol, "private fun dispatchRecorder(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit)"),
      token(files.protocol, "\"getStatus\" -> callback(NodeBridgeResponse.success(request.id, recorderStatus().toString()))"),
      token(files.protocol, "\"start\", \"stop\" -> callback("),
      token(files.protocol, "a privacy-reviewed audio recorder provider with foreground disclosure and cleanup evidence"),
      token(files.protocol, "blocked_until_privacy_reviewed_recorder_provider"),
      token(files.permissionManifest, "const val MEDIA_RECORDING = \"media.recording\""),
      token(files.permissionManifest, "const val ANDROID_PERMISSION_RECORD_AUDIO = \"android.permission.RECORD_AUDIO\""),
      token(files.permissionManifest, "ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE"),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
      token(files.mediaTest, "media.manifest.recording=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.recording|0|0"),
      token(files.recorderTypes, "getStatus(options?: AutoJs6Node.BridgeCallOptions): Promise<RecorderStatus>;"),
      token(files.recorderTypes, "start(options?: RecorderStartOptions): Promise<never>;"),
      token(files.recorderTypes, "stop(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_status=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_denial=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder_denial.recording=true"),
      token(files.conformanceTest, "expectExports(\"recorder\", recorder, [\"getStatus\", \"start\", \"stop\"])"),
      token(files.conformanceTest, "await expectRejectCode(\"recorder.start\""),
    ]),
    scenario("session_disclosure_cleanup_boundary", "Future recorder sessions require prompts, disclosure, scoped resources, cleanup, and Safe downgrade", [
      value("session_resource_type", session.sessionResourceType === "MEDIA_RECORDER"),
      value("resource_registry_required", session.resourceRegistryRequired === true),
      value("opaque_session_required", session.opaqueSessionIdRequired === true),
      value("runtime_prompt_required", session.runtimePromptRequired === true),
      value("foreground_required", session.foregroundDisclosureRequired === true),
      value("stop_action_required", session.stopActionRequired === true),
      value("temp_output_required", session.tempOutputOwnershipRequired === true),
      value("crash_cleanup_required", session.crashCleanupRequired === true),
      value("destroy_cleanup_required", session.providerDestroyCleanupRequired === true),
      value("task_removal_required", session.taskRemovalCleanupRequired === true),
      value("safe_downgrade_action", session.safeDowngradeAction === "stop_owned_sessions_delete_temp_outputs_disable_promoted_recorder_start"),
      value("fail_closed_start_stop", failClosed.startStopStayDenied === true),
      value("fail_closed_reason", failClosed.currentDeniedReason === "blocked_until_privacy_reviewed_recorder_provider"),
      value("stable_error_required", failClosed.stableErrorRequired === true),
      value("raw_objects_denied", includesAll(failClosed.rawObjectsDenied || [], [
        "MediaRecorder",
        "AudioRecord",
        "AudioPlaybackCaptureConfiguration",
        "MediaProjection",
        "ParcelFileDescriptor",
        "file descriptor",
        "ForegroundService",
      ])),
      token(files.providerRegistry, "\"phase14RecorderProviderPromotion\" to \"P14-24 real recorder session promotion gate\""),
      token(files.providerRegistry, "\"recorderSessionPolicy\" to \"real_session_requires_record_audio_prompt_foreground_disclosure_resource_handle_temp_output_crash_cleanup\""),
      token(files.providerRegistry, "\"recorderStartStopPolicy\" to \"start_stop_stay_denied_until_runtime_permission_session_cleanup_packaged_stress\""),
      token(files.providerRegistry, "\"recorderForegroundDisclosurePolicy\" to \"microphone_foreground_disclosure_and_stop_action_required_before_start\""),
      token(files.providerRegistry, "\"recorderTempOutputPolicy\" to \"future_temp_outputs_must_be_owned_scoped_and_deleted_on_stop_destroy_crash_rollback\""),
      token(files.privacyDisclosurePolicy, "`media_recording`"),
      token(files.privacyDisclosurePolicy, "`media.recording`"),
      token(files.privacyDisclosurePolicy, "`RECORD_AUDIO` and microphone foreground service"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep recorder behind device, packaged, disclosure, rollback, security, type, docs, Safe downgrade, and raw-object bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("recorder_contract_provider_poc", recorderContract.currentState === "provider_poc"),
      value("recorder_contract_p15_tasks", includesAll(recorderContract.p15Tasks || [], ["P15-19", "P15-20"])),
      value("recorder_contract_backlog", includesAll(recorderContract.sourceBacklogTasks || [], ["P14-24", "P14-25"])),
      value("recorder_contract_evidence_bars", includesAll(recorderContract.requiredEvidence || [], [
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
      value("recorder_audit_provider_poc", recorderAudit.currentState === "provider_poc"),
      value("recorder_audit_disable_flag", recorderAudit.disableFlag === "node.provider.recorder.enabled"),
      value("recorder_audit_diagnostic", recorderAudit.diagnosticCode === "NODE_PROVIDER_RECORDER_DISABLED"),
      value("recorder_audit_safe_downgrade", recorderAudit.safeDowngrade === "stop active recorder sessions, clean temp output, and deny new captures"),
      value("recorder_audit_disclosure_fields", includesAll(recorderAudit.requiredDisclosureFields || [], [
        "foregroundNotificationOwnership",
        "stopAction",
        "taskRemovalCleanup",
        "crashRecoveryBehavior",
        "safeDowngradeDisclosure",
      ])),
    ]),
    scenario("raw_object_boundary", "Raw recorder objects and raw file descriptors remain unavailable to Node", [
      value("raw_media_recorder_not_exposed", !files.nodeBridgeSources.text.includes("MediaRecorder")),
      value("raw_audio_record_not_exposed", !files.nodeBridgeSources.text.includes("AudioRecord")),
      value("raw_playback_capture_not_exposed", !files.nodeBridgeSources.text.includes("AudioPlaybackCaptureConfiguration")),
      value("raw_pfd_not_exposed", !files.nodeBridgeSources.text.includes("ParcelFileDescriptor")),
      token(files.providerRegistry, "\"rawObjectPolicy\" to \"no raw MediaPlayer, AudioManager, MediaRecorder"),
      token(files.securityModel, "P14-24 records real recorder session promotion evidence as partial"),
      token(files.errorCatalog, "P14-24 recorder denial sync"),
      token(files.compatibilityProfile, "P14-24 records real recorder session promotion evidence as partial"),
    ]),
    scenario("promotion_blockers_open", "Runtime prompt, foreground disclosure, output ownership, crash cleanup, packaged stress, Safe downgrade, security, and docs sync remain future work", [
      value("open_blockers", sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-19 recorder promotion gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-24"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "permission_lifecycle_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15RecorderPromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15RecorderPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-recorder-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15RecorderPromotion"),
      token(files.testing, ":app:verifyNodePhase15RecorderPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15RecorderPromotion"),
      token(files.phase15Tasks, "phase15-recorder-promotion.json"),
      token(files.phase15Tasks, "recorder_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-recorder-promotion-plan-v1" &&
        plan.task === "P15-19" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-24"]) &&
        plan.providerId === "recorder" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-19 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-19 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "recorder-status-fail-closed",
      sameSet(recorder.currentLiveMethods || [], REQUIRED_CURRENT_METHODS) &&
        sameSet(recorder.futureSessionMethods || [], REQUIRED_FUTURE_METHODS) &&
        recorder.currentStatusEvidenceReady === true &&
        recorder.currentStartStopPromoted === false,
      "P15-19 must keep recorder status available and start/stop unpromoted.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-19 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-recorder-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.p13RecorderEvidenceImported === true &&
        phase14Decision.currentRecorderFailClosed === true &&
        phase14Decision.sessionPromotionBoundaryReady === true &&
        phase14Decision.runtimePermissionPromptReady === false,
      "P15-19 requires P14-24 recorder provider report to be partial with prompt/session archives still open.",
    ),
    check(
      "phase15-settings-power-dependency-partial",
      statusOf(p15SettingsPowerReport) === "partial" &&
        p15SettingsPowerDecision.settingsPowerPromoted === false &&
        p15SettingsPowerDecision.runtimeAuthorityChanged === false,
      "P15-19 depends on P15-18 settings/power promotion remaining partial and unpromoted.",
    ),
    check(
      "raw-recorder-objects-stay-denied",
      !files.nodeBridgeSources.text.includes("MediaRecorder") &&
        !files.nodeBridgeSources.text.includes("AudioRecord") &&
        !files.nodeBridgeSources.text.includes("AudioPlaybackCaptureConfiguration") &&
        !files.nodeBridgeSources.text.includes("ParcelFileDescriptor"),
      "P15-19 must not expose raw MediaRecorder, AudioRecord, playback capture, or file descriptor APIs through Node sources.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-19 evidence scenarios must be ready while recorder archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15RecorderPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-recorder-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15RecorderPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15RecorderPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15RecorderPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-19 recorder promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-19: Promote real recorder session provider.") &&
        files.phase15Tasks.text.includes("phase15-recorder-promotion.json") &&
        files.phase15Tasks.text.includes("recorder_runtime_permission_prompt_archive"),
      "P15-19 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-recorder-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15RecorderPromotion",
    task: "P15-19",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    recorderContract: recorder,
    sessionContract: session,
    failClosedContract: failClosed,
    scenarios,
    checks,
    policyViolations,
    blockers: openBlockers.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14RecorderProviderReady: scenarioById(scenarios, "phase14_recorder_provider_imported").status === "ready",
      settingsPowerDependencyReady: statusOf(p15SettingsPowerReport) === "partial" && p15SettingsPowerDecision.settingsPowerPromoted === false,
      recorderStatusReady: scenarioById(scenarios, "recorder_status_fail_closed_boundary").status === "ready",
      startStopFailClosed: true,
      sessionPromotionBoundaryReady: scenarioById(scenarios, "session_disclosure_cleanup_boundary").status === "ready",
      runtimePermissionPromptReady: false,
      foregroundDisclosureStopActionReady: false,
      tempOutputOwnershipReady: false,
      crashCleanupReady: false,
      packagedStartStopStressReady: false,
      safeDowngradeReady: false,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: openBlockers.length,
      recorderPromoted: false,
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
    "# Phase 15 Recorder Promotion",
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
      console.log("Usage: node verify-node-phase15-recorder-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
