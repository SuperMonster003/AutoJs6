#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-media-recorder-mediainfo-provider";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  mediaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt",
  permissionManifestTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt",
  providerRegistryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
  processSweepTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt",
  mediaTypes: "docs/nodejs/types/autojs6-node/media.d.ts",
  mediainfoTypes: "docs/nodejs/types/autojs6-node/mediainfo.d.ts",
  recorderTypes: "docs/nodejs/types/autojs6-node/recorder.d.ts",
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
  console.log("Phase 13 media/recorder/mediainfo provider: " + report.decision.status);
  console.log("Phase 13 media/recorder/mediainfo provider blockers: " + report.blockers.length);
  console.log("Phase 13 media/recorder/mediainfo provider policy violations: " + report.policyViolations.length);
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
    scenario("media_audio_mediainfo", "media and mediainfo expose JSON-safe read-only provider snapshots", [
      token(files.permissionManifest, 'const val MEDIA = "media"'),
      token(files.permissionManifest, 'const val MEDIA_AUDIO = "media.audio"'),
      token(files.permissionManifest, 'const val MEDIA_METADATA = "media.metadata"'),
      token(files.protocol, 'const val MEDIA = "media"'),
      token(files.protocol, 'const val MEDIAINFO = "mediainfo"'),
      token(files.protocol, 'val mediaMethods = setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo")'),
      token(files.protocol, 'val mediainfoMethods = setOf("read", "get")'),
      token(files.protocol, "private fun dispatchMedia("),
      token(files.protocol, "private fun dispatchMediainfo("),
      token(files.protocol, "autojs6-node-media-audio-stream-v1"),
      token(files.protocol, "autojs6-node-mediainfo-snapshot-v1"),
      token(files.nodeBridgeSources, "function __autojs6_limited_media()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_mediainfo()"),
      token(files.nodeBridgeSources, 'if (name === "mediainfo")'),
      token(files.mediaTypes, 'declare module "media"'),
      token(files.mediainfoTypes, 'declare module "mediainfo"'),
      token(files.mediaTest, "jsMediaModulesUseFakeTransportCompatFacadeAndBlockShadowing"),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
      token(files.conformanceTest, 'expectExports("media", media, ["getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo"])'),
      token(files.typeSmoke, "const mediaInfoSnapshot: mediainfo.Snapshot"),
      token(files.p13Report, "P13-26 v1.1 stable-denies audio playback"),
      token(files.compatibilityProfile, "P13-26 v1.1 stable-denies audio playback"),
      token(files.p13Report, "P13-26 v1.1 stable-denies external MediaStore"),
      token(files.compatibilityProfile, "external MediaStore access"),
    ], [
      "Packaged media asset lookup and real audio/video corpus evidence remain open.",
    ]),
    scenario("recorder_fail_closed", "recorder vocabulary is explicit and recording stays fail-closed", [
      token(files.permissionManifest, 'const val MEDIA_RECORDING = "media.recording"'),
      token(files.permissionManifest, 'const val ANDROID_PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"'),
      token(files.permissionManifest, 'const val ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE'),
      token(files.permissionManifest, "MEDIA_RECORDING to listOf("),
      token(files.permissionManifest, "required == MEDIA_RECORDING"),
      token(files.protocol, 'const val RECORDER = "recorder"'),
      token(files.protocol, 'val recorderMethods = setOf("getStatus", "start", "stop")'),
      token(files.protocol, "private fun recorderStatus(): JSONObject"),
      token(files.protocol, "blocked_until_privacy_reviewed_recorder_provider"),
      token(files.nodeBridgeSources, "function __autojs6_limited_recorder()"),
      token(files.nodeBridgeSources, 'permissions = ["media", "media.recording"];'),
      token(files.recorderTypes, 'declare module "recorder"'),
      token(files.mediaTest, "media.manifest.recording"),
      token(files.mediaTest, "recorder.start"),
      token(files.conformanceTest, 'expectExports("recorder", recorder, ["getStatus", "start", "stop"])'),
      token(files.typeSmoke, "const recorderStart: (options?: recorder.RecorderStartOptions) => Promise<never>"),
      token(files.p13Report, "Screen recording and internal/playback audio capture are stable-denied"),
      token(files.compatibilityProfile, "screen/internal audio capture is stable-denied for v1.1"),
    ], [
      "Real audio recording start/stop/session handles require runtime permission flow, foreground notification disclosure, and cleanup.",
      "Packaged recorder smoke, denial matrix, and privacy disclosure copy remain open.",
    ]),
    scenario("compat_provider_docs", "compat, provider registry, process permission, docs, and project tooling record P13-26", [
      token(files.providerRegistry, 'displayName = "media/mediainfo/recorder"'),
      token(files.providerRegistry, '"phase13Promotion" to "P13-26 partial media/recorder/mediainfo provider evidence gate"'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw MediaPlayer'),
      token(files.providerRegistryTest, 'assertEquals(20, definitions.size)'),
      token(files.processSweepTest, '"media.recording"'),
      token(files.nodeBridgeSources, '"P13-26"'),
      token(files.nodeBridgeSources, 'media: media'),
      token(files.rhinoCompatTypes, "readonly mediainfo: mediainfoModule.MediainfoModule"),
      token(files.projectTool, '"media.recording"'),
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-26 partial media/recorder/mediainfo provider gate"),
      token(files.gradleGate, "verifyNodePhase13MediaRecorderMediainfoProvider"),
      token(files.testingDoc, "verifyNodePhase13MediaRecorderMediainfoProvider"),
      token(files.ciGatesDoc, "verifyNodePhase13MediaRecorderMediainfoProvider"),
      token(files.compatibilityProfile, "P13-26 records media/recorder/mediainfo provider promotion as partial"),
      token(files.securityModel, "P13-26 records media/recorder/mediainfo provider promotion as partial"),
      token(files.proComparison, "P13-26 records media/recorder/mediainfo provider promotion as partial"),
      token(files.proAutoxNotes, "P13-26 records media/recorder/mediainfo provider promotion as partial"),
      token(files.capabilityMatrix, "P13-26 media/recorder/mediainfo provider"),
      token(files.typesReadme, "P13-26 records the partial media/recorder/mediainfo provider gate"),
      token(files.surfaceManifestDoc, "phase13-media-recorder-mediainfo-provider"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-26"), "P13-26 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-26 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must remain partial/poc after P13-26 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-26 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-26 evidence.",
    ),
    check(
      "media-capabilities-exact",
      files.permissionManifest.text.includes("required == MEDIA_AUDIO") &&
        files.permissionManifest.text.includes("required == MEDIA_METADATA") &&
        files.permissionManifest.text.includes("required == MEDIA_RECORDING"),
      "media.audio, media.metadata, and media.recording must be exact capabilities, not implied by media.",
    ),
    check(
      "no-raw-media-modules",
      !files.nodeBridgeSources.text.includes('if (name === "media_player")') &&
        !files.nodeBridgeSources.text.includes('if (name === "mediastore")') &&
        !files.nodeBridgeSources.text.includes('if (name === "audio_manager")'),
      "P13-26 must not expose raw media_player, mediastore, or audio_manager modules.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-media-recorder-mediainfo-provider-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13MediaRecorderMediainfoProvider",
    task: "P13-26",
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
      mediaAudioReady: scenarioById(scenarios, "media_audio_mediainfo").status !== "missing_evidence",
      mediainfoReady: scenarioById(scenarios, "media_audio_mediainfo").status !== "missing_evidence",
      recorderStatusReady: scenarioById(scenarios, "recorder_fail_closed").status !== "missing_evidence",
      recorderStartStopReady: false,
      playbackReady: false,
      mediaStoreReady: false,
      packagedMediaReady: false,
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
