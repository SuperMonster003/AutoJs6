#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-recorder-provider";
const REQUIRED_CURRENT_METHODS = Object.freeze(["getStatus"]);
const REQUIRED_FUTURE_METHODS = Object.freeze(["start", "stop", "pause", "resume", "getStatus"]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "recorder_runtime_permission_prompt_archive",
    classification: "future_promotion",
    requiredEvidence: "Runtime RECORD_AUDIO prompt and denial/grant archive covering interactive and packaged flows, stable errors, Android permission metadata, and Safe downgrade behavior.",
  },
  {
    id: "recorder_foreground_disclosure_stop_action_archive",
    classification: "future_promotion",
    requiredEvidence: "Foreground microphone disclosure and stop-action archive proving user-visible ownership, notification cleanup, task removal behavior, and rollback cleanup.",
  },
  {
    id: "recorder_temp_output_ownership_cleanup_archive",
    classification: "future_promotion",
    requiredEvidence: "Scoped temporary output archive proving owner-scoped paths, no raw file descriptors, delete-on-stop/delete-on-destroy behavior, quota enforcement, and failure cleanup.",
  },
  {
    id: "recorder_crash_cleanup_session_resource_archive",
    classification: "future_promotion",
    requiredEvidence: "ResourceRegistry session archive proving provider destroy, process death, app relaunch, crash recovery, and Safe downgrade stop owned sessions without touching external media.",
  },
  {
    id: "recorder_packaged_start_stop_stress_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged recorder start/stop stress archive covering relaunch, task removal, missing capability, missing Android permission, denied prompt, quota, and temporary file cleanup.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_24_RECORDER_PROVIDER.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/recorder-provider-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_RECORDER_PROVIDER.md",
  p13GeneratedReport: "phase13-media-recorder-mediainfo-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  settingsPowerGenerated: "phase14-settings-power-oem-background-matrix.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
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
  console.log("Phase 14 recorder provider: " + report.decision.status);
  console.log("Phase 14 recorder blockers: " + report.blockers.length);
  console.log("Phase 14 recorder policy violations: " + report.policyViolations.length);
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
  const recorder = plan.recorderContract || {};
  const session = plan.sessionContract || {};
  const failClosed = plan.failClosedContract || {};
  const p13Generated = parseJson(files.p13GeneratedReport.text);
  const settingsPower = parseJson(files.settingsPowerGenerated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const scenarios = [
    scenario("recorder_provider_contract", "P14-24 records real recorder session promotion requirements without enabling start/stop", [
      value("schema", plan.schema === "autojs6-node-phase14-recorder-provider-plan-v1"),
      value("task", plan.task === "P14-24"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-26", "P14-04", "P14-23"])),
      value("providerName", recorder.providerName === "recorder"),
      value("capability", recorder.capability === "media.recording"),
      value("baseCapability", recorder.baseCapability === "media"),
      value("androidPermissions", includesAll(recorder.androidPermissions || [], [
        "android.permission.RECORD_AUDIO",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE",
      ])),
      value("statusSchema", recorder.statusSchema === "autojs6-node-recorder-status-v1"),
      value("currentMethods", sameSet(recorder.currentLiveMethods || [], REQUIRED_CURRENT_METHODS)),
      value("futureMethods", sameSet(recorder.futureSessionMethods || [], REQUIRED_FUTURE_METHODS)),
      value("statusReady", recorder.currentStatusEvidenceReady === true),
      value("startStopNotPromoted", recorder.currentStartStopPromoted === false),
      value("packagedBoundaryReady", recorder.currentPackagedBoundaryReady === true),
      value("screenRecordingNotPromoted", recorder.screenRecordingPromoted === false),
      value("internalAudioCaptureNotPromoted", recorder.internalAudioCapturePromoted === false),
      token(files.planDoc, "This gate does not promote real microphone capture yet"),
    ]),
    scenario("p13_recorder_evidence_imported", "P13-26 recorder status, packaged boundary, and fail-closed evidence are imported", [
      value("p13GeneratedPresent", files.p13GeneratedReport.exists),
      value("p13GeneratedPartial", statusOf(p13Generated) === "partial"),
      value("p13RecorderStatusReady", (p13Generated.decision || {}).recorderStatusReady === true),
      value("p13RecorderStartStopNotReady", (p13Generated.decision || {}).recorderStartStopReady === false),
      value("p13PackagedMediaReady", (p13Generated.decision || {}).packagedMediaReady === true),
      token(files.p13Report, "`recorder.getStatus()`"),
      token(files.p13Report, "`recorder.start()` and `recorder.stop()` stay fail-closed"),
      token(files.p13Report, "Real recorder sessions with runtime permission flow, foreground disclosure"),
      token(files.p13Report, "No raw `MediaPlayer`, `AudioManager`, `MediaRecorder`"),
      token(files.privacyDisclosurePolicy, "`media_recording`"),
      token(files.privacyDisclosurePolicy, "`RECORD_AUDIO` and microphone foreground service"),
    ]),
    scenario("current_recorder_fail_closed", "Current runtime exposes status and denies start/stop before real session promotion", [
      token(files.protocol, 'val recorderMethods = setOf("getStatus", "start", "stop")'),
      token(files.protocol, "private fun dispatchRecorder(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit)"),
      token(files.protocol, '"getStatus" -> callback(NodeBridgeResponse.success(request.id, recorderStatus().toString()))'),
      token(files.protocol, '"start", "stop" -> callback('),
      token(files.protocol, "a privacy-reviewed audio recorder provider with foreground disclosure and cleanup evidence"),
      token(files.protocol, "private fun recorderStatus(): JSONObject"),
      token(files.protocol, "blocked_until_privacy_reviewed_recorder_provider"),
      token(files.permissionManifest, 'const val MEDIA_RECORDING = "media.recording"'),
      token(files.permissionManifest, 'const val ANDROID_PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"'),
      token(files.permissionManifest, "ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE"),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
      token(files.mediaTest, "assertFalse(start.toString(), start.ok)"),
      token(files.mediaTest, "media.manifest.recording=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.recording|0|0"),
      token(files.recorderTypes, "start(options?: RecorderStartOptions): Promise<never>;"),
      token(files.recorderTypes, "stop(options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_status=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_denial=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder_denial.recording=true"),
      token(files.conformanceTest, 'expectExports("recorder", recorder, ["getStatus", "start", "stop"])'),
      token(files.conformanceTest, 'await expectRejectCode("recorder.start"'),
    ]),
    scenario("session_promotion_boundary", "Future session promotion requires permission, disclosure, resources, output ownership, and cleanup", [
      value("sessionResourceType", session.sessionResourceType === "MEDIA_RECORDER"),
      value("resourceRegistryRequired", session.resourceRegistryRequired === true),
      value("opaqueSessionIdRequired", session.opaqueSessionIdRequired === true),
      value("runtimePromptRequired", session.runtimePromptRequired === true),
      value("foregroundDisclosureRequired", session.foregroundDisclosureRequired === true),
      value("stopActionRequired", session.stopActionRequired === true),
      value("tempOutputOwnershipRequired", session.tempOutputOwnershipRequired === true),
      value("crashCleanupRequired", session.crashCleanupRequired === true),
      value("providerDestroyCleanupRequired", session.providerDestroyCleanupRequired === true),
      value("taskRemovalCleanupRequired", session.taskRemovalCleanupRequired === true),
      value("pauseResumeOptional", session.pauseResumeOptional === true),
      value("safeDowngradeAction", session.safeDowngradeAction === "stop_owned_sessions_delete_temp_outputs_disable_promoted_recorder_start"),
      value("startStopStayDenied", failClosed.startStopStayDenied === true),
      value("currentDeniedReason", failClosed.currentDeniedReason === "blocked_until_privacy_reviewed_recorder_provider"),
      value("startDeniedCode", failClosed.startDeniedCode === "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      value("missingCapabilityDeniedCode", failClosed.missingCapabilityDeniedCode === "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"),
      value("rawObjectsDenied", includesAll(failClosed.rawObjectsDenied || [], [
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
      token(files.providerRegistry, "\"recorderTempOutputPolicy\" to \"future_temp_outputs_must_be_owned_scoped_and_deleted_on_stop_destroy_crash_rollback\""),
    ]),
    scenario("shared_disclosure_and_surface_docs", "Shared disclosure contracts, Phase 14 manifest, docs, and Gradle expose the P14-24 gate", [
      value("recorderDisclosureRequired", includesAll(disclosure.requiredForSurfaces || [], ["recorder"])),
      value("sharedDisclosureFields", includesAll(disclosure.requiredFields || [], ["foregroundNotificationOwnership", "stopAction", "taskRemovalCleanup", "crashRecoveryBehavior", "rollbackBehavior"])),
      value("rawMediaRecorderDenied", includesAll(disclosure.rawObjectDenials || [], ["MediaRecorder", "WakeLock"])),
      value("settingsPowerImported", files.settingsPowerGenerated.exists && statusOf(settingsPower) === "partial"),
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-24") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/recorder-provider-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14RecorderProvider")),
      token(files.surfaceManifestDoc, "P14-24 adds the real recorder session promotion gate"),
      token(files.phase14Tasks, "Recorder provider gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14RecorderProvider"),
      token(files.testing, ":app:verifyNodePhase14RecorderProvider"),
      token(files.ciGates, ":app:verifyNodePhase14RecorderProvider"),
      token(files.gradle14, "verifyNodePhase14RecorderProvider"),
      token(files.gradle14, "verify-node-phase14-recorder-provider.js"),
      token(files.securityModel, "P14-24 records real recorder session promotion evidence"),
      token(files.errorCatalog, "P14-24 recorder denial sync"),
      token(files.compatibilityProfile, "P14-24 records real recorder session promotion evidence"),
    ]),
    scenario("promotion_archives_open", "Runtime prompt, disclosure, output ownership, crash cleanup, and packaged start/stop stress archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p13-generated-report-present", files.p13GeneratedReport.exists, "The P13-26 generated report must exist before P14-24 runs."),
    check("p14-settings-power-report-present", files.settingsPowerGenerated.exists, "The P14-23 generated report must exist before P14-24 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-recorder-provider-plan-v1", "P14-24 recorder provider plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Recorder Provider"), "P14-24 recorder provider documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-24") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/recorder-provider-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14RecorderProvider"),
      "The pro.tasks_device_media surface must include the P14-24 evidence and Gradle gate.",
    ),
    check(
      "raw-recorder-objects-stay-denied",
      !files.nodeBridgeSources.text.includes('if (name === "media_recorder")') &&
        !files.nodeBridgeSources.text.includes("AudioRecord") &&
        !files.nodeBridgeSources.text.includes("AudioPlaybackCaptureConfiguration") &&
        !files.nodeBridgeSources.text.includes("ParcelFileDescriptor"),
      "P14-24 must not expose raw recorder, AudioRecord, playback capture, or file descriptor APIs through Node sources.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-recorder-provider-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-24",
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
      p13RecorderEvidenceImported: scenarioById(scenarios, "p13_recorder_evidence_imported").status === "ready",
      currentRecorderFailClosed: scenarioById(scenarios, "current_recorder_fail_closed").status === "ready",
      sessionPromotionBoundaryReady: scenarioById(scenarios, "session_promotion_boundary").status === "ready",
      runtimePermissionPromptReady: false,
      foregroundDisclosureStopActionReady: false,
      tempOutputOwnershipReady: false,
      crashCleanupReady: false,
      packagedStartStopStressReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      recorderStartStopPromoted: false,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Recorder Provider",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P13 recorder evidence imported: \`${report.decision.p13RecorderEvidenceImported}\``,
    `- Current recorder fail-closed: \`${report.decision.currentRecorderFailClosed}\``,
    `- Session promotion boundary ready: \`${report.decision.sessionPromotionBoundaryReady}\``,
    `- Runtime permission prompt ready: \`${report.decision.runtimePermissionPromptReady}\``,
    `- Foreground disclosure/stop action ready: \`${report.decision.foregroundDisclosureStopActionReady}\``,
    `- Temp output ownership ready: \`${report.decision.tempOutputOwnershipReady}\``,
    `- Crash cleanup ready: \`${report.decision.crashCleanupReady}\``,
    `- Packaged start/stop stress ready: \`${report.decision.packagedStartStopStressReady}\``,
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
