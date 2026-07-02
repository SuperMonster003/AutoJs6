#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-packaged-recorder-media-stress";
const REQUIRED_STRESS_ROWS = Object.freeze([
  "packaged_status_and_denial_boundary",
  "future_packaged_start_stop_positive",
  "future_task_removal_relaunch_crash_cleanup",
  "future_quota_temp_output_cleanup",
  "future_permission_denial_matrix",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "packaged_recorder_start_stop_positive_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged recorder positive start/stop archive with visible microphone foreground disclosure, stop action, JSON-safe session/status, and owned resource cleanup.",
  },
  {
    id: "packaged_recorder_task_removal_relaunch_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged recorder task removal, relaunch, and provider/process crash archive proving deterministic stop and temporary output cleanup.",
  },
  {
    id: "packaged_recorder_quota_temp_cleanup_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged recorder quota and scoped temporary output archive proving no raw file descriptor, no broad media-library permission, and cleanup on stop/destroy/failure/rollback.",
  },
  {
    id: "packaged_recorder_denial_without_capability_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged recorder denial archive for missing media.recording, missing Android permission, denied prompt, and metadata mismatch before bridge dispatch.",
  },
  {
    id: "packaged_recorder_foreground_disclosure_stop_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged recorder microphone foreground disclosure and stop-action archive proving user-visible ownership and deterministic stop behavior.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_25_PACKAGED_RECORDER_MEDIA_STRESS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/packaged-recorder-media-stress-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_RECORDER_MEDIA_STRESS.md",
  p13GeneratedReport: "phase13-media-recorder-mediainfo-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  recorderProviderGenerated: "phase14-recorder-provider.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  mediaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
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
  console.log("Phase 14 packaged recorder/media stress: " + report.decision.status);
  console.log("Phase 14 packaged recorder/media blockers: " + report.blockers.length);
  console.log("Phase 14 packaged recorder/media policy violations: " + report.policyViolations.length);
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
  const boundary = plan.packagedBoundaryContract || {};
  const rows = Array.isArray(plan.stressMatrix) ? plan.stressMatrix : [];
  const rowIds = rows.map((entry) => entry.id);
  const p13Generated = parseJson(files.p13GeneratedReport.text);
  const recorderProvider = parseJson(files.recorderProviderGenerated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const scenarios = [
    scenario("packaged_stress_contract", "P14-25 records packaged recorder/media stress requirements without start/stop promotion", [
      value("schema", plan.schema === "autojs6-node-phase14-packaged-recorder-media-stress-plan-v1"),
      value("task", plan.task === "P14-25"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-26", "P14-04", "P14-24"])),
      value("providerName", boundary.providerName === "recorder"),
      value("capability", boundary.capability === "media.recording"),
      value("currentPackagedBoundaryReady", boundary.currentPackagedBoundaryReady === true),
      value("currentPackagedStatusReady", boundary.currentPackagedStatusReady === true),
      value("currentPackagedStartDenied", boundary.currentPackagedStartDenied === true),
      value("currentPackagedMissingCapabilityDenied", boundary.currentPackagedMissingCapabilityDenied === true),
      value("positivePackagedStartStopNotReady", boundary.positivePackagedStartStopReady === false),
      value("foregroundDisclosureVisibleNotReady", boundary.foregroundDisclosureVisibleReady === false),
      value("deterministicStopNotReady", boundary.deterministicStopReady === false),
      value("taskRemovalCleanupNotReady", boundary.taskRemovalCleanupReady === false),
      value("relaunchCrashRecoveryNotReady", boundary.relaunchCrashRecoveryReady === false),
      value("quotaAndTempCleanupNotReady", boundary.quotaAndTempCleanupReady === false),
      value("stressRows", sameSet(rowIds, REQUIRED_STRESS_ROWS)),
      token(files.planDoc, "This gate does not promote packaged recorder start/stop"),
    ]),
    scenario("p13_packaged_media_recorder_imported", "P13-26 packaged media/recorder boundary smoke is imported", [
      value("p13GeneratedPresent", files.p13GeneratedReport.exists),
      value("p13GeneratedPartial", statusOf(p13Generated) === "partial"),
      value("p13PackagedMediaReady", (p13Generated.decision || {}).packagedMediaReady === true),
      value("p13RecorderStartStopNotReady", (p13Generated.decision || {}).recorderStartStopReady === false),
      token(files.p13Report, "Packaged APK smoke coverage now exercises declared `media`, `media.audio`,"),
      token(files.p13Report, "stable recorder start denial; and packaged exact-capability denial"),
      token(files.packagedSmokeTest, "buildPackagedMediaRecorderBridgeApk"),
      token(files.packagedSmokeTest, "buildPackagedMediaRecorderBridgeDenialApk"),
      token(files.packagedSmokeTest, "MEDIA_RECORDER_BRIDGE_NODE_PERMISSIONS"),
      token(files.packagedSmokeTest, "packaged.media_recorder.ready=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_status=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_denial=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder_denial.recording=true"),
      token(files.packagedSmokeTest, "assertInstallAndRunPackagedMediaRecorderBridgeSmoke"),
      token(files.packagedSmokeTest, "assertInstallAndRunPackagedMediaRecorderBridgeDenialSmoke"),
    ]),
    scenario("p14_recorder_provider_imported", "P14-24 recorder provider promotion contract is imported", [
      value("recorderProviderGeneratedPresent", files.recorderProviderGenerated.exists),
      value("recorderProviderPartial", statusOf(recorderProvider) === "partial"),
      value("p14CurrentRecorderFailClosed", (recorderProvider.decision || {}).currentRecorderFailClosed === true),
      value("p14PackagedStressNotReady", (recorderProvider.decision || {}).packagedStartStopStressReady === false),
      token(files.providerRegistry, "\"phase14RecorderProviderPromotion\" to \"P14-24 real recorder session promotion gate\""),
      token(files.providerRegistry, "\"recorderPackagedStressPolicy\" to \"packaged_start_stop_task_removal_relaunch_crash_quota_temp_cleanup_required_before_promotion\""),
      token(files.providerRegistry, "\"mediaPackagedStressPolicy\" to \"current_packaged_media_recorder_stress_is_boundary_only_no_microphone_capture\""),
      token(files.protocol, "blocked_until_privacy_reviewed_recorder_provider"),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
    ]),
    scenario("future_packaged_positive_requirements", "Positive packaged recorder stress remains future work with visible disclosure and deterministic stop", [
      value("recorderDisclosureRequired", includesAll(disclosure.requiredForSurfaces || [], ["recorder"])),
      value("sharedDisclosureFields", includesAll(disclosure.requiredFields || [], ["foregroundNotificationOwnership", "stopAction", "taskRemovalCleanup", "relaunchBehavior", "crashRecoveryBehavior", "rollbackBehavior"])),
      value("rawMediaRecorderDenied", includesAll(disclosure.rawObjectDenials || [], ["MediaRecorder", "Intent"])),
      token(files.phase14Report, "Positive packaged start/stop, foreground disclosure, task removal, relaunch"),
      token(files.planDoc, "deterministic stop behavior"),
      token(files.errorCatalog, "P14-25 packaged recorder/media stress denial sync"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-25 gate", [
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-25") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-recorder-media-stress-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedRecorderMediaStress")),
      token(files.surfaceManifestDoc, "P14-25 adds the packaged recorder/media stress gate"),
      token(files.phase14Tasks, "Packaged recorder/media stress gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14PackagedRecorderMediaStress"),
      token(files.testing, ":app:verifyNodePhase14PackagedRecorderMediaStress"),
      token(files.ciGates, ":app:verifyNodePhase14PackagedRecorderMediaStress"),
      token(files.gradle14, "verifyNodePhase14PackagedRecorderMediaStress"),
      token(files.gradle14, "verify-node-phase14-packaged-recorder-media-stress.js"),
      token(files.securityModel, "P14-25 records packaged recorder/media stress evidence"),
      token(files.compatibilityProfile, "P14-25 records packaged recorder/media stress evidence"),
    ]),
    scenario("promotion_archives_open", "Positive start/stop, lifecycle, quota, denial, and disclosure archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p13-generated-report-present", files.p13GeneratedReport.exists, "The P13-26 generated report must exist before P14-25 runs."),
    check("p14-recorder-provider-report-present", files.recorderProviderGenerated.exists, "The P14-24 generated report must exist before P14-25 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-packaged-recorder-media-stress-plan-v1", "P14-25 packaged recorder/media stress plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Packaged Recorder Media Stress"), "P14-25 packaged recorder/media stress documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-25") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-recorder-media-stress-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedRecorderMediaStress"),
      "The pro.tasks_device_media surface must include the P14-25 evidence and Gradle gate.",
    ),
    check(
      "packaged-positive-start-stop-not-promoted",
      !files.nodeBridgeSources.text.includes("packaged_recorder_start") &&
        !files.nodeBridgeSources.text.includes('if (name === "media_recorder")') &&
        !files.nodeBridgeSources.text.includes("AudioPlaybackCaptureConfiguration"),
      "P14-25 must not expose packaged recorder start/stop, raw media_recorder, or playback capture APIs through Node sources.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-packaged-recorder-media-stress-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-25",
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
      p13PackagedBoundaryImported: scenarioById(scenarios, "p13_packaged_media_recorder_imported").status === "ready",
      p14RecorderProviderImported: scenarioById(scenarios, "p14_recorder_provider_imported").status === "ready",
      currentPackagedBoundaryReady: true,
      positivePackagedStartStopReady: false,
      foregroundDisclosureVisibleReady: false,
      deterministicStopReady: false,
      taskRemovalCleanupReady: false,
      relaunchCrashRecoveryReady: false,
      quotaTempCleanupReady: false,
      denialWithoutCapabilityReady: true,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      packagedRecorderPromoted: false,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Recorder Media Stress",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P13 packaged boundary imported: \`${report.decision.p13PackagedBoundaryImported}\``,
    `- P14 recorder provider imported: \`${report.decision.p14RecorderProviderImported}\``,
    `- Current packaged boundary ready: \`${report.decision.currentPackagedBoundaryReady}\``,
    `- Positive packaged start/stop ready: \`${report.decision.positivePackagedStartStopReady}\``,
    `- Foreground disclosure visible ready: \`${report.decision.foregroundDisclosureVisibleReady}\``,
    `- Deterministic stop ready: \`${report.decision.deterministicStopReady}\``,
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
