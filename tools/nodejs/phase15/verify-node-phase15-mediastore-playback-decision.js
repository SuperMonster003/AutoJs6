#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-mediastore-playback-decision";
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
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_mediastore_playback_boundary",
  "phase15_packaged_recorder_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "media_playback_scoped_provider_decision_archive",
  "media_playlist_queue_denial_archive",
  "mediastore_scoped_query_mutation_decision_archive",
  "broad_media_library_permission_denial_archive",
  "packaged_playback_mediastore_denial_archive",
  "mediastore_playback_safe_downgrade_archive",
  "mediastore_playback_raw_object_denial_security_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/mediastore-playback-decision-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_MEDIASTORE_PLAYBACK_DECISION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_21_MEDIASTORE_PLAYBACK_DECISION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15PackagedRecorderReport: "build/reports/nodejs/phase15-packaged-recorder-smoke.json",
  phase14Plan: "docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_26_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md",
  phase14Generated: "build/reports/nodejs/phase14-media-playback-mediastore-boundary.json",
  p13Generated: "build/reports/nodejs/phase13-media-recorder-mediainfo-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  mediaTypes: "docs/nodejs/types/autojs6-node/media.d.ts",
  mediainfoTypes: "docs/nodejs/types/autojs6-node/mediainfo.d.ts",
  profileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
  mediaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt",
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
  console.log("Phase 15 MediaStore/playback decision: " + report.decision.status);
  console.log("Phase 15 MediaStore/playback future blockers: " + report.blockers.length);
  console.log("Phase 15 MediaStore/playback policy violations: " + report.policyViolations.length);
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
  const p15PackagedRecorderReport = parseJson(files.p15PackagedRecorderReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-21") || {};
  const mediastoreContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "mediastore_playback") || {};
  const mediastoreAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "mediastore_playback") || {};
  const decision = plan.decision || {};
  const future = plan.futureProviderRequirements || {};
  const stableDenied = Array.isArray(plan.stableDeniedSurfaces) ? plan.stableDeniedSurfaces : [];
  const stableDeniedIds = stableDenied.map((entry) => entry.id);
  const openBlockers = Array.isArray(plan.openBlockers) ? plan.openBlockers : [];
  const phase14Decision = phase14Report.decision || {};
  const p13Decision = p13Report.decision || {};
  const p15PackagedRecorderDecision = p15PackagedRecorderReport.decision || {};
  const scenarios = [
    scenario("phase14_boundary_imported", "P14-26 MediaStore/playback boundary decision is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-media-playback-mediastore-boundary-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_p13_imported", phase14Decision.p13StableDenialImported === true),
      value("phase14_read_only_ready", phase14Decision.currentMediaReadOnlyReady === true),
      value("phase14_future_requirements_ready", phase14Decision.futureScopedProviderRequirementsReady === true),
      value("phase14_playback_denied", phase14Decision.playbackPromoted === false),
      value("phase14_mediastore_denied", phase14Decision.mediaStorePromoted === false),
      value("phase14_broad_storage_denied", phase14Decision.broadStoragePermissionPromoted === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-26 deploys the media playback and MediaStore boundary decision gate"),
      token(files.phase14TaskReport, "`media_playback_scoped_provider_decision_archive`"),
      token(files.phase14PlanDoc, "This gate keeps playback, playlists, media scanning, external MediaStore access"),
      token(files.phase14PlanDoc, "`media.getAudioStreamVolume()`"),
    ]),
    scenario("phase15_packaged_recorder_dependency", "P15-20 packaged recorder dependency remains partial and unpromoted", [
      value("p15_packaged_recorder_status", statusOf(p15PackagedRecorderReport) === "partial"),
      value("p15_packaged_recorder_not_promoted", p15PackagedRecorderDecision.packagedRecorderPromoted === false),
      value("p15_packaged_recorder_runtime_authority", p15PackagedRecorderDecision.runtimeAuthorityChanged === false),
      token(files.p15PackagedRecorderReport, "\"packagedRecorderPromoted\": false"),
      token(files.p15PackagedRecorderReport, "\"phase15_task_device_media_doc_sync\""),
    ]),
    scenario("stable_denial_decision", "P15-21 chooses stable denial without changing runtime authority", [
      value("decision_result", decision.result === "keep_stable_denied"),
      value("current_methods", sameSet(decision.currentAllowedSurface || [], CURRENT_METHODS)),
      value("current_capabilities", sameSet(decision.currentCapabilityScope || [], ["media", "media.audio", "media.metadata"])),
      value("playback_denied", decision.playbackPromoted === false),
      value("playlist_denied", decision.playlistPromoted === false),
      value("scanfile_denied", decision.scanFilePromoted === false),
      value("mediastore_query_denied", decision.mediaStoreQueryPromoted === false),
      value("mediastore_mutation_denied", decision.mediaStoreMutationPromoted === false),
      value("broad_storage_denied", decision.broadStoragePermissionPromoted === false),
      value("future_gate_required", decision.futureScopedProviderAllowedOnlyWithNewGate === true),
      value("stable_denied_rows", sameSet(stableDeniedIds, DENIED_ROWS)),
      value("stable_rows_status", stableDenied.every((entry) => entry.status === "stable_denied")),
    ]),
    scenario("current_media_surface_read_only", "Current media and mediainfo runtime/type surface stays read-only", [
      value("p13_playback_not_ready", p13Decision.playbackReady === false),
      value("p13_mediastore_not_ready", p13Decision.mediaStoreReady === false),
      token(files.protocol, 'val mediaMethods = setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo")'),
      token(files.protocol, 'val mediainfoMethods = setOf("read", "get")'),
      token(files.nodeBridgeSources, "function __autojs6_limited_media()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_mediainfo()"),
      token(files.mediaTypes, "getAudioStreamVolume(stream?: AudioStreamName"),
      token(files.mediaTypes, "getAudioStreamMaxVolume(stream?: AudioStreamName"),
      token(files.mediaTypes, "getAudioStreamInfo(stream?: AudioStreamName"),
      token(files.mediainfoTypes, "read(path: string, options?: ReadOptions): Promise<Snapshot>;"),
      token(files.mediainfoTypes, "get(path: string, streamKind: StreamKind"),
      value("media_types_no_playback", !files.mediaTypes.text.includes("play(") && !files.mediaTypes.text.includes("scanFile") && !files.mediaTypes.text.includes("playlist")),
      value("types_no_mediastore", !files.mediaTypes.text.includes("MediaStore") && !files.mediainfoTypes.text.includes("MediaStore")),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
    ]),
    scenario("future_provider_requirements", "Future playback or MediaStore providers require exact capabilities, ownership, packaged evidence, rollback, and raw-object denial", [
      value("exact_capabilities_required", future.exactCapabilitiesRequired === true),
      value("candidate_capabilities", includesAll(future.candidateCapabilities || [], ["media.playback", "media.library", "media.library.mutate"])),
      value("json_descriptors_only", future.jsonDescriptorsOnly === true),
      value("owner_scoped_handles", future.ownerScopedHandlesRequired === true),
      value("activity_or_foreground", future.activityOrForegroundOwnershipRequired === true),
      value("permission_matrix", future.androidPermissionMatrixRequired === true),
      value("packaged_evidence", future.packagedPositiveAndDenialEvidenceRequired === true),
      value("safe_downgrade", future.safeDowngradeRequired === true),
      value("raw_objects_denied", includesAll(future.rawObjectsDenied || [], [
        "MediaPlayer",
        "AudioTrack",
        "AudioFocusRequest",
        "MediaStore cursor",
        "ContentResolver",
        "ParcelFileDescriptor",
        "MediaScannerConnection",
        "playlist handle",
      ])),
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("contract_stable_denied", mediastoreContract.currentState === "stable_denied"),
      value("contract_p15_task", sameSet(mediastoreContract.p15Tasks || [], ["P15-21"])),
      value("contract_backlog", sameSet(mediastoreContract.sourceBacklogTasks || [], ["P14-26"])),
      value("contract_evidence_bars", includesAll(mediastoreContract.requiredEvidence || [], [
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
      value("audit_stable_denied", mediastoreAudit.currentState === "stable_denied"),
      value("audit_disable_flag", mediastoreAudit.disableFlag === "node.provider.mediastore_playback.enabled"),
      value("audit_diagnostic", mediastoreAudit.diagnosticCode === "NODE_PROVIDER_MEDIASTORE_PLAYBACK_DENIED"),
      value("audit_safe_downgrade", mediastoreAudit.safeDowngrade === "keep MediaStore and playback mutations denied with scoped status diagnostics"),
      token(files.profileCapabilities, 'readonly "media.playback": NodeProfileV12CapabilitySpec<"media.playback"'),
      token(files.profileCapabilities, 'readonly "mediastore": NodeProfileV12CapabilitySpec<"mediastore"'),
      token(files.profileCapabilities, '"stable_denied", "denial_declaration", "stable_denied", "P14-26"'),
    ]),
    scenario("runtime_boundary_closed", "Runtime does not expose playback, scanFile, MediaStore, broad storage, or raw media handles", [
      value("no_media_player_module", !files.nodeBridgeSources.text.includes('if (name === "media_player")')),
      value("no_mediastore_module", !files.nodeBridgeSources.text.includes('if (name === "mediastore")')),
      value("no_media_store_module", !files.nodeBridgeSources.text.includes('if (name === "media_store")')),
      value("no_playback_capability", !files.permissionManifest.text.includes("MEDIA_PLAYBACK") && !files.permissionManifest.text.includes("media.playback")),
      value("no_mediastore_capability", !files.permissionManifest.text.includes("MEDIASTORE") && !files.permissionManifest.text.includes("mediastore")),
      token(files.providerRegistry, "\"phase14MediaPlaybackMediaStoreBoundary\" to \"P14-26 media playback and MediaStore boundary decision gate\""),
      token(files.providerRegistry, "\"mediaPlaybackPolicy\" to \"v1_2_stable_denied_until_scoped_provider_audio_focus_lifecycle_packaged_evidence\""),
      token(files.providerRegistry, "\"mediaStoreBoundaryPolicy\" to \"query_insert_update_delete_scanFile_broad_media_library_permissions_stable_denied\""),
      token(files.providerRegistry, "\"mediaFutureCapabilityPolicy\" to \"future_media_playback_media_library_media_library_mutate_capabilities_require_new_gate\""),
      token(files.errorCatalog, "P14-26 media playback/MediaStore denial sync"),
      token(files.securityModel, "P14-26 records media playback and MediaStore boundary evidence as partial"),
      token(files.compatibilityProfile, "P14-26 records media playback and MediaStore boundary evidence as partial"),
    ]),
    scenario("promotion_blockers_open", "Future scoped-provider archives remain visible without blocking the stable-denied P15-21 decision", [
      value("open_blockers", sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-21 MediaStore/playback decision gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-26"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "boundary_decision_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15MediaStorePlaybackDecision"),
      value("surface_mapping_ready", p15Mapping.status === "ready"),
      token(files.phase15Gradle, "verifyNodePhase15MediaStorePlaybackDecision"),
      token(files.phase15Gradle, "verify-node-phase15-mediastore-playback-decision.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15MediaStorePlaybackDecision"),
      token(files.testing, ":app:verifyNodePhase15MediaStorePlaybackDecision"),
      token(files.ciGates, ":app:verifyNodePhase15MediaStorePlaybackDecision"),
      token(files.phase15Tasks, "- [x] P15-21: Decide scoped MediaStore and playback promotion boundary."),
      token(files.phase15Tasks, "phase15-mediastore-playback-decision.json"),
      token(files.phase15Tasks, "mediastore_playback_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-mediastore-playback-decision-plan-v1" &&
        plan.task === "P15-21" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-26"]) &&
        plan.providerId === "mediastore_playback" &&
        plan.status === "ready" &&
        plan.promotionState === "stable_denied" &&
        plan.runtimeAuthorityChanged === false,
      "P15-21 plan must record a ready stable-denied decision without changing runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-21 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "stable-denial-decision",
      decision.result === "keep_stable_denied" &&
        sameSet(decision.currentAllowedSurface || [], CURRENT_METHODS) &&
        decision.playbackPromoted === false &&
        decision.mediaStoreQueryPromoted === false &&
        decision.mediaStoreMutationPromoted === false &&
        decision.futureScopedProviderAllowedOnlyWithNewGate === true,
      "P15-21 must choose stable denial while keeping read-only media/mediainfo calls as the only allowed surface.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-21 must keep future scoped-provider blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-boundary-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.currentMediaReadOnlyReady === true &&
        phase14Decision.playbackPromoted === false &&
        phase14Decision.mediaStorePromoted === false,
      "P15-21 requires P14-26 MediaStore/playback boundary report to remain partial with playback and MediaStore denied.",
    ),
    check(
      "phase15-packaged-recorder-dependency-partial",
      statusOf(p15PackagedRecorderReport) === "partial" &&
        p15PackagedRecorderDecision.packagedRecorderPromoted === false &&
        p15PackagedRecorderDecision.runtimeAuthorityChanged === false,
      "P15-21 depends on P15-20 packaged recorder/media stress remaining partial and unpromoted.",
    ),
    check(
      "runtime-boundary-still-closed",
      !files.nodeBridgeSources.text.includes('if (name === "media_player")') &&
        !files.nodeBridgeSources.text.includes('if (name === "mediastore")') &&
        !files.nodeBridgeSources.text.includes('if (name === "media_store")') &&
        !files.mediaTypes.text.includes("play(") &&
        !files.mediaTypes.text.includes("scanFile") &&
        !files.mediaTypes.text.includes("playlist"),
      "P15-21 must not expose playback, scanFile, MediaStore, or playlist APIs through Node sources or declarations.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-21 evidence scenarios must be ready while future scoped-provider archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15MediaStorePlaybackDecision") &&
        files.phase15Gradle.text.includes("verify-node-phase15-mediastore-playback-decision.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15MediaStorePlaybackDecision") &&
        files.testing.text.includes(":app:verifyNodePhase15MediaStorePlaybackDecision") &&
        files.ciGates.text.includes(":app:verifyNodePhase15MediaStorePlaybackDecision"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-21 MediaStore/playback decision gate.",
    ),
    check(
      "task-plan-ready",
      files.phase15Tasks.text.includes("- [x] P15-21: Decide scoped MediaStore and playback promotion boundary.") &&
        files.phase15Tasks.text.includes("phase15-mediastore-playback-decision.json") &&
        files.phase15Tasks.text.includes("mediastore_playback_safe_downgrade_archive"),
      "P15-21 must be checked as a completed stable-denied boundary decision with report outputs documented.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-mediastore-playback-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15MediaStorePlaybackDecision",
    task: "P15-21",
    status: policyViolations.length === 0 ? "ready" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    decisionContract: decision,
    stableDeniedSurfaces: stableDenied,
    futureProviderRequirements: future,
    scenarios,
    checks,
    policyViolations,
    blockers: openBlockers.map((entry) => ({ ...entry, status: "future" })),
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      promotionState: "stable_denied",
      p15TaskComplete: policyViolations.length === 0,
      phase14BoundaryReady: scenarioById(scenarios, "phase14_boundary_imported").status === "ready",
      packagedRecorderDependencyReady: statusOf(p15PackagedRecorderReport) === "partial" && p15PackagedRecorderDecision.packagedRecorderPromoted === false,
      currentMediaReadOnlyReady: scenarioById(scenarios, "current_media_surface_read_only").status === "ready",
      futureScopedProviderRequirementsReady: scenarioById(scenarios, "future_provider_requirements").status === "ready",
      playbackPromoted: false,
      playlistPromoted: false,
      scanFilePromoted: false,
      mediaStorePromoted: false,
      broadStoragePermissionPromoted: false,
      stableDenialReady: true,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: openBlockers.length,
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
    "# Phase 15 MediaStore Playback Decision",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Playback promoted: \`${report.decision.playbackPromoted}\``,
    `- MediaStore promoted: \`${report.decision.mediaStorePromoted}\``,
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
  lines.push("", "## Future Scoped-Provider Blockers", "", "| Blocker | Classification | Required Evidence |", "| --- | --- | --- |");
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
      console.log("Usage: node verify-node-phase15-mediastore-playback-decision.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
