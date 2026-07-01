#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-media-playback-mediastore-boundary";
const CURRENT_METHODS = Object.freeze([
  "media.getAudioStreamVolume",
  "media.getAudioStreamMaxVolume",
  "media.getAudioStreamInfo",
  "mediainfo.read",
  "mediainfo.get",
]);
const DENIED_ROWS = Object.freeze([
  "media_playback_lifecycle",
  "media_playlist_queue",
  "media_scan_file",
  "media_store_query_mutation",
  "broad_media_library_permissions",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "media_playback_scoped_provider_decision_archive",
    classification: "future_promotion",
    requiredEvidence: "Decision archive for scoped playback provider or stable denial, covering exact capabilities, source ownership, lifecycle handles, audio focus, background behavior, packaged evidence, and rollback.",
  },
  {
    id: "media_playlist_queue_denial_archive",
    classification: "future_promotion",
    requiredEvidence: "Playlist/queue stable-denial or scoped-provider archive proving bounded descriptors, owner-scoped handles, and cleanup.",
  },
  {
    id: "mediastore_scoped_query_mutation_decision_archive",
    classification: "future_promotion",
    requiredEvidence: "MediaStore scoped query/mutation decision archive covering exact capabilities, Android permission matrix, owner-scoped rows, privacy behavior, packaged evidence, and rollback cleanup.",
  },
  {
    id: "broad_media_library_permission_denial_archive",
    classification: "future_promotion",
    requiredEvidence: "Broad media-library permission denial archive for READ_MEDIA_* and MANAGE_EXTERNAL_STORAGE, including user disclosure and downgrade behavior if any future exception is proposed.",
  },
  {
    id: "packaged_playback_mediastore_denial_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK denial archive proving playback, playlist, scanFile, MediaStore, broad storage, raw handles, and metadata mismatch stay denied without a full provider gate.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_26_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md",
  p13GeneratedReport: "phase13-media-recorder-mediainfo-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  packagedRecorderStressGenerated: "phase14-packaged-recorder-media-stress.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  mediaTypes: "docs/nodejs/types/autojs6-node/media.d.ts",
  mediainfoTypes: "docs/nodejs/types/autojs6-node/mediainfo.d.ts",
  mediaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt",
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
  console.log("Phase 14 media playback/MediaStore boundary: " + report.decision.status);
  console.log("Phase 14 media playback/MediaStore blockers: " + report.blockers.length);
  console.log("Phase 14 media playback/MediaStore policy violations: " + report.policyViolations.length);
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
  const boundary = plan.boundaryDecision || {};
  const future = plan.futureProviderRequirements || {};
  const stableDenied = Array.isArray(plan.stableDeniedSurfaces) ? plan.stableDeniedSurfaces : [];
  const stableDeniedIds = stableDenied.map((entry) => entry.id);
  const p13Generated = parseJson(files.p13GeneratedReport.text);
  const packagedStress = parseJson(files.packagedRecorderStressGenerated.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
  const scenarios = [
    scenario("media_playback_mediastore_boundary_contract", "P14-26 records playback, playlist, scanFile, MediaStore, and broad storage as stable-denied/future-only", [
      value("schema", plan.schema === "autojs6-node-phase14-media-playback-mediastore-boundary-plan-v1"),
      value("task", plan.task === "P14-26"),
      value("status", plan.status === "partial"),
      value("promotionState", plan.promotionState === "provider_poc"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P13-26", "P14-04", "P14-25"])),
      value("providerName", boundary.providerName === "media"),
      value("currentMethods", sameSet(boundary.currentPromotedMethods || [], CURRENT_METHODS)),
      value("currentCapabilityScope", sameSet(boundary.currentCapabilityScope || [], ["media", "media.audio", "media.metadata"])),
      value("playbackDenied", boundary.playbackPromoted === false),
      value("playlistDenied", boundary.playlistPromoted === false),
      value("scanFileDenied", boundary.scanFilePromoted === false),
      value("mediaStoreQueryDenied", boundary.mediaStoreQueryPromoted === false),
      value("mediaStoreMutationDenied", boundary.mediaStoreMutationPromoted === false),
      value("broadStorageDenied", boundary.broadStoragePermissionPromoted === false),
      value("futureProviderGateRequired", boundary.futureScopedProviderAllowedOnlyWithNewGate === true),
      value("stableDeniedRows", sameSet(stableDeniedIds, DENIED_ROWS)),
      token(files.planDoc, "This gate keeps playback, playlists, media scanning, external MediaStore access"),
    ]),
    scenario("p13_stable_denial_imported", "P13-26 playback and external MediaStore stable denial is imported", [
      value("p13GeneratedPresent", files.p13GeneratedReport.exists),
      value("p13GeneratedPartial", statusOf(p13Generated) === "partial"),
      value("p13PlaybackNotReady", (p13Generated.decision || {}).playbackReady === false),
      value("p13MediaStoreNotReady", (p13Generated.decision || {}).mediaStoreReady === false),
      token(files.p13Report, "P13-26 v1.1 stable-denies audio playback"),
      token(files.p13Report, "playlists, and playback focus"),
      token(files.p13Report, "P13-26 v1.1 stable-denies external MediaStore query/insert/update/delete"),
      token(files.p13Report, "broad media-library permissions, and mutable media collection access"),
      token(files.p13Report, "No raw `MediaPlayer`, `AudioManager`, `MediaRecorder`"),
      token(files.securityModel, "Playback and MediaStore access are stable-denied for v1.1"),
    ]),
    scenario("current_media_surface_stays_read_only", "Current media and mediainfo declarations expose read-only status/metadata only", [
      token(files.protocol, 'val mediaMethods = setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo")'),
      token(files.protocol, 'val mediainfoMethods = setOf("read", "get")'),
      token(files.nodeBridgeSources, "function __autojs6_limited_media()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_mediainfo()"),
      token(files.mediaTypes, "getAudioStreamVolume(stream?: AudioStreamName"),
      token(files.mediaTypes, "getAudioStreamMaxVolume(stream?: AudioStreamName"),
      token(files.mediaTypes, "getAudioStreamInfo(stream?: AudioStreamName"),
      token(files.mediainfoTypes, "read(path: string, options?: ReadOptions): Promise<Snapshot>;"),
      token(files.mediainfoTypes, "get(path: string, streamKind: StreamKind"),
      value("mediaTypesNoPlay", !files.mediaTypes.text.includes("play(") && !files.mediaTypes.text.includes("scanFile") && !files.mediaTypes.text.includes("playlist")),
      value("noMediaStoreTypes", !files.mediaTypes.text.includes("MediaStore") && !files.mediainfoTypes.text.includes("MediaStore")),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
    ]),
    scenario("future_scoped_provider_requirements", "Any future playback or MediaStore provider must define exact capabilities and ownership/privacy evidence", [
      value("exactCapabilitiesRequired", future.exactCapabilitiesRequired === true),
      value("candidateCapabilities", includesAll(future.candidateCapabilities || [], ["media.playback", "media.library", "media.library.mutate"])),
      value("jsonDescriptorsOnly", future.jsonDescriptorsOnly === true),
      value("ownerScopedHandlesRequired", future.ownerScopedHandlesRequired === true),
      value("activityOrForegroundOwnershipRequired", future.activityOrForegroundOwnershipRequired === true),
      value("androidPermissionMatrixRequired", future.androidPermissionMatrixRequired === true),
      value("packagedPositiveAndDenialEvidenceRequired", future.packagedPositiveAndDenialEvidenceRequired === true),
      value("safeDowngradeRequired", future.safeDowngradeRequired === true),
      value("rawObjectsDenied", includesAll(future.rawObjectsDenied || [], [
        "MediaPlayer",
        "AudioTrack",
        "AudioFocusRequest",
        "MediaStore cursor",
        "ContentResolver",
        "ParcelFileDescriptor",
        "MediaScannerConnection",
        "playlist handle",
      ])),
      value("packagedStressImported", files.packagedRecorderStressGenerated.exists && statusOf(packagedStress) === "partial"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-26 gate", [
      value("surfaceEvidence", proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-26") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14MediaPlaybackMediaStoreBoundary")),
      token(files.providerRegistry, "\"phase14MediaPlaybackMediaStoreBoundary\" to \"P14-26 media playback and MediaStore boundary decision gate\""),
      token(files.providerRegistry, "\"mediaPlaybackPolicy\" to \"v1_2_stable_denied_until_scoped_provider_audio_focus_lifecycle_packaged_evidence\""),
      token(files.providerRegistry, "\"mediaStoreBoundaryPolicy\" to \"query_insert_update_delete_scanFile_broad_media_library_permissions_stable_denied\""),
      token(files.surfaceManifestDoc, "P14-26 adds the media playback/MediaStore boundary gate"),
      token(files.phase14Tasks, "Media playback/MediaStore boundary gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14MediaPlaybackMediaStoreBoundary"),
      token(files.testing, ":app:verifyNodePhase14MediaPlaybackMediaStoreBoundary"),
      token(files.ciGates, ":app:verifyNodePhase14MediaPlaybackMediaStoreBoundary"),
      token(files.gradle14, "verifyNodePhase14MediaPlaybackMediaStoreBoundary"),
      token(files.gradle14, "verify-node-phase14-media-playback-mediastore-boundary.js"),
      token(files.errorCatalog, "P14-26 media playback/MediaStore denial sync"),
      token(files.compatibilityProfile, "P14-26 records media playback and MediaStore boundary evidence"),
    ]),
    scenario("promotion_archives_open", "Playback, playlist, MediaStore, broad permission, and packaged denial archives remain future work", [
      ...OPEN_BLOCKERS.flatMap((entry) => [
        token(files.planDoc, entry.id),
        token(files.phase14Report, entry.id),
      ]),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
  ];
  const checks = [
    check("p13-generated-report-present", files.p13GeneratedReport.exists, "The P13-26 generated report must exist before P14-26 runs."),
    check("p14-packaged-recorder-media-stress-report-present", files.packagedRecorderStressGenerated.exists, "The P14-25 generated report must exist before P14-26 runs."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-media-playback-mediastore-boundary-plan-v1", "P14-26 boundary plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Media Playback MediaStore Boundary"), "P14-26 boundary documentation must exist."),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-26") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14MediaPlaybackMediaStoreBoundary"),
      "The pro.tasks_device_media surface must include the P14-26 evidence and Gradle gate.",
    ),
    check(
      "raw-media-playback-mediastore-stay-denied",
      !files.nodeBridgeSources.text.includes('if (name === "media_player")') &&
        !files.nodeBridgeSources.text.includes('if (name === "mediastore")') &&
        !files.nodeBridgeSources.text.includes('if (name === "media_store")') &&
        !files.mediaTypes.text.includes("play(") &&
        !files.mediaTypes.text.includes("scanFile") &&
        !files.mediaTypes.text.includes("playlist"),
      "P14-26 must not expose raw media_player, MediaStore, playback, scanFile, or playlist APIs through Node sources or declarations.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-media-playback-mediastore-boundary-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-26",
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
      p13StableDenialImported: scenarioById(scenarios, "p13_stable_denial_imported").status === "ready",
      currentMediaReadOnlyReady: scenarioById(scenarios, "current_media_surface_stays_read_only").status === "ready",
      futureScopedProviderRequirementsReady: scenarioById(scenarios, "future_scoped_provider_requirements").status === "ready",
      playbackPromoted: false,
      playlistPromoted: false,
      scanFilePromoted: false,
      mediaStorePromoted: false,
      broadStoragePermissionPromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Media Playback MediaStore Boundary",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- P13 stable denial imported: \`${report.decision.p13StableDenialImported}\``,
    `- Current media read-only ready: \`${report.decision.currentMediaReadOnlyReady}\``,
    `- Future scoped provider requirements ready: \`${report.decision.futureScopedProviderRequirementsReady}\``,
    `- Playback promoted: \`${report.decision.playbackPromoted}\``,
    `- Playlist promoted: \`${report.decision.playlistPromoted}\``,
    `- MediaStore promoted: \`${report.decision.mediaStorePromoted}\``,
    `- Broad storage permission promoted: \`${report.decision.broadStoragePermissionPromoted}\``,
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
