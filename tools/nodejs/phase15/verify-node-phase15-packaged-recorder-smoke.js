#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-packaged-recorder-smoke";
const REQUIRED_STRESS_ROWS = Object.freeze([
  "packaged_status_and_denial_boundary",
  "future_packaged_start_stop_positive",
  "future_task_removal_relaunch_crash_cleanup",
  "future_quota_temp_output_cleanup",
  "future_permission_denial_matrix",
]);
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_packaged_recorder_media_stress_contract",
  "phase15_recorder_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "packaged_recorder_start_stop_positive_archive",
  "packaged_recorder_task_removal_relaunch_archive",
  "packaged_recorder_quota_temp_cleanup_archive",
  "packaged_recorder_denial_without_capability_archive",
  "packaged_recorder_foreground_disclosure_stop_archive",
  "packaged_recorder_safe_downgrade_archive",
  "packaged_recorder_raw_object_denial_security_archive",
  "phase15_task_device_media_doc_sync",
]);

const FILES = {
  plan: "docs/nodejs/phase15/packaged-recorder-smoke-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_PACKAGED_RECORDER_SMOKE.md",
  taskReport: "docs/nodejs/reports/phase15/P15_20_PACKAGED_RECORDER_SMOKE.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  p15RecorderReport: "build/reports/nodejs/phase15-recorder-promotion.json",
  phase14Plan: "docs/nodejs/phase14/packaged-recorder-media-stress-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_RECORDER_MEDIA_STRESS.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_25_PACKAGED_RECORDER_MEDIA_STRESS.md",
  phase14Generated: "build/reports/nodejs/phase14-packaged-recorder-media-stress.json",
  p13Generated: "build/reports/nodejs/phase13-media-recorder-mediainfo-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  mediaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
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
  console.log("Phase 15 packaged recorder smoke: " + report.decision.status);
  console.log("Phase 15 packaged recorder blockers: " + report.blockers.length);
  console.log("Phase 15 packaged recorder policy violations: " + report.policyViolations.length);
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
  const p15RecorderReport = parseJson(files.p15RecorderReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-20") || {};
  const recorderContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "recorder") || {};
  const recorderAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "recorder") || {};
  const boundary = plan.packagedBoundaryContract || {};
  const rows = Array.isArray(plan.stressMatrix) ? plan.stressMatrix : [];
  const rowIds = rows.map((entry) => entry.id);
  const openBlockers = Array.isArray(plan.openBlockers) ? plan.openBlockers : [];
  const phase14Decision = phase14Report.decision || {};
  const p13Decision = p13Report.decision || {};
  const p15RecorderDecision = p15RecorderReport.decision || {};
  const scenarios = [
    scenario("phase14_packaged_stress_imported", "P14-25 packaged recorder/media stress evidence is imported", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-packaged-recorder-media-stress-plan-v1"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_p13_boundary_imported", phase14Decision.p13PackagedBoundaryImported === true),
      value("phase14_recorder_provider_imported", phase14Decision.p14RecorderProviderImported === true),
      value("phase14_current_boundary_ready", phase14Decision.currentPackagedBoundaryReady === true),
      value("phase14_positive_not_ready", phase14Decision.positivePackagedStartStopReady === false),
      value("phase14_foreground_not_ready", phase14Decision.foregroundDisclosureVisibleReady === false),
      value("phase14_quota_not_ready", phase14Decision.quotaTempCleanupReady === false),
      value("phase14_future_blockers", phase14Decision.futurePromotionBlockers === 5),
      token(files.phase14TaskReport, "P14-25 deploys the packaged recorder and media stress evidence gate"),
      token(files.phase14TaskReport, "`packaged_recorder_start_stop_positive_archive`"),
      token(files.phase14PlanDoc, "This gate does not promote packaged recorder start/stop"),
      token(files.phase14PlanDoc, "`recorder.getStatus()` returns"),
    ]),
    scenario("phase15_recorder_dependency", "P15-19 recorder dependency remains partial and unpromoted", [
      value("p15_recorder_status", statusOf(p15RecorderReport) === "partial"),
      value("p15_recorder_not_promoted", p15RecorderDecision.recorderPromoted === false),
      value("p15_recorder_runtime_authority", p15RecorderDecision.runtimeAuthorityChanged === false),
      token(files.p15RecorderReport, "\"recorderPromoted\": false"),
      token(files.p15RecorderReport, "\"phase15_task_device_media_doc_sync\""),
    ]),
    scenario("packaged_boundary_imported", "Packaged recorder boundary smoke imports status, denial, and exact capability evidence", [
      value("provider_name", boundary.providerName === "recorder"),
      value("capability", boundary.capability === "media.recording"),
      value("current_boundary_ready", boundary.currentPackagedBoundaryReady === true),
      value("current_status_ready", boundary.currentPackagedStatusReady === true),
      value("current_start_denied", boundary.currentPackagedStartDenied === true),
      value("missing_capability_denied", boundary.currentPackagedMissingCapabilityDenied === true),
      value("positive_start_stop_not_ready", boundary.positivePackagedStartStopReady === false),
      value("foreground_not_ready", boundary.foregroundDisclosureVisibleReady === false),
      value("deterministic_stop_not_ready", boundary.deterministicStopReady === false),
      value("task_removal_not_ready", boundary.taskRemovalCleanupReady === false),
      value("relaunch_crash_not_ready", boundary.relaunchCrashRecoveryReady === false),
      value("quota_not_ready", boundary.quotaAndTempCleanupReady === false),
      value("safe_downgrade_not_ready", boundary.safeDowngradeReady === false),
      value("raw_object_not_ready", boundary.rawObjectDenialReady === false),
      value("stress_rows", sameSet(rowIds, REQUIRED_STRESS_ROWS)),
      value("p13_packaged_media_ready", p13Decision.packagedMediaReady === true),
      value("p13_recorder_start_stop_not_ready", p13Decision.recorderStartStopReady === false),
      token(files.p13Report, "Packaged APK smoke coverage now exercises declared `media`, `media.audio`,"),
      token(files.p13Report, "stable recorder start denial; and packaged exact-capability denial"),
      token(files.packagedSmokeTest, "buildPackagedMediaRecorderBridgeApk"),
      token(files.packagedSmokeTest, "buildPackagedMediaRecorderBridgeDenialApk"),
      token(files.packagedSmokeTest, "MEDIA_RECORDER_BRIDGE_NODE_PERMISSIONS"),
      token(files.packagedSmokeTest, "packaged.media_recorder.ready=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_status=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder.recorder_denial=true"),
      token(files.packagedSmokeTest, "packaged.media_recorder_denial.recording=true"),
      token(files.packagedSmokeTest, "android.permission.RECORD_AUDIO"),
      token(files.packagedSmokeTest, "android.permission.FOREGROUND_SERVICE_MICROPHONE"),
      token(files.mediaTest, "androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed"),
      token(files.protocol, "blocked_until_privacy_reviewed_recorder_provider"),
      token(files.permissionManifest, "const val MEDIA_RECORDING = \"media.recording\""),
    ]),
    scenario("future_positive_packaged_requirements", "Positive packaged recorder stress remains future work with disclosure, deterministic stop, lifecycle, quota, denial, and Safe downgrade evidence", [
      value("positive_row_future", rowStatus(rows, "future_packaged_start_stop_positive") === "future_promotion"),
      value("lifecycle_row_future", rowStatus(rows, "future_task_removal_relaunch_crash_cleanup") === "future_promotion"),
      value("quota_row_future", rowStatus(rows, "future_quota_temp_output_cleanup") === "future_promotion"),
      value("denial_row_future", rowStatus(rows, "future_permission_denial_matrix") === "future_promotion"),
      token(files.providerRegistry, "\"phase14PackagedRecorderMediaStress\" to \"P14-25 packaged recorder/media stress evidence gate\""),
      token(files.providerRegistry, "\"recorderPackagedStressPolicy\" to \"packaged_start_stop_task_removal_relaunch_crash_quota_temp_cleanup_required_before_promotion\""),
      token(files.providerRegistry, "\"mediaPackagedStressPolicy\" to \"current_packaged_media_recorder_stress_is_boundary_only_no_microphone_capture\""),
      token(files.phase14TaskReport, "Positive packaged start/stop, foreground disclosure, task removal, relaunch"),
      token(files.planDoc, "metadata mismatch diagnostics"),
      token(files.errorCatalog, "P14-25 packaged recorder/media stress denial sync"),
      token(files.securityModel, "P14-25 records packaged recorder/media stress evidence as partial"),
      token(files.compatibilityProfile, "P14-25 records packaged recorder/media stress evidence as partial"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep packaged recorder behind packaged, disclosure, rollback, security, type, docs, Safe downgrade, and raw-object bars", [
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
        "relaunchBehavior",
        "crashRecoveryBehavior",
        "safeDowngradeDisclosure",
      ])),
    ]),
    scenario("raw_object_boundary", "Packaged recorder keeps raw recorder objects and raw file descriptors unavailable", [
      value("raw_media_recorder_not_exposed", !files.nodeBridgeSources.text.includes("MediaRecorder")),
      value("raw_audio_record_not_exposed", !files.nodeBridgeSources.text.includes("AudioRecord")),
      value("raw_playback_capture_not_exposed", !files.nodeBridgeSources.text.includes("AudioPlaybackCaptureConfiguration")),
      value("raw_pfd_not_exposed", !files.nodeBridgeSources.text.includes("ParcelFileDescriptor")),
      value("packaged_positive_start_not_exposed", !files.nodeBridgeSources.text.includes("packaged_recorder_start")),
    ]),
    scenario("promotion_blockers_open", "Positive start/stop, lifecycle, quota, denial, disclosure, Safe downgrade, security, and docs sync remain future work", [
      value("open_blockers", sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS)),
      value("open_blockers_non_release", openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.planDoc, id)),
      ...REQUIRED_BLOCKERS.map((id) => token(files.taskReport, id)),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-20 packaged recorder smoke gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_backlog", sameSet(p15Mapping.sourceBacklogTasks || [], ["P14-25"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "packaged_stress_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:installAndRunNodeV13PackagedRecorderSmoke"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "installAndRunNodeV13PackagedRecorderSmoke"),
      token(files.phase15Gradle, "verify-node-phase15-packaged-recorder-smoke.js"),
      token(files.phase15Readme, ":app:installAndRunNodeV13PackagedRecorderSmoke"),
      token(files.testing, ":app:installAndRunNodeV13PackagedRecorderSmoke"),
      token(files.ciGates, ":app:installAndRunNodeV13PackagedRecorderSmoke"),
      token(files.phase15Tasks, "phase15-packaged-recorder-smoke.json"),
      token(files.phase15Tasks, "packaged_recorder_safe_downgrade_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-packaged-recorder-smoke-plan-v1" &&
        plan.task === "P15-20" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-25"]) &&
        plan.providerId === "recorder" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-20 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-20 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "packaged-boundary-imported",
      sameSet(rowIds, REQUIRED_STRESS_ROWS) &&
        boundary.currentPackagedBoundaryReady === true &&
        boundary.currentPackagedStatusReady === true &&
        boundary.currentPackagedStartDenied === true &&
        boundary.positivePackagedStartStopReady === false,
      "P15-20 must import packaged status/denial boundary while keeping positive start/stop unpromoted.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet(openBlockers.map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-20 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-packaged-recorder-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.p13PackagedBoundaryImported === true &&
        phase14Decision.currentPackagedBoundaryReady === true &&
        phase14Decision.positivePackagedStartStopReady === false,
      "P15-20 requires P14-25 packaged recorder/media stress report to be partial with positive start/stop archives still open.",
    ),
    check(
      "phase15-recorder-dependency-partial",
      statusOf(p15RecorderReport) === "partial" &&
        p15RecorderDecision.recorderPromoted === false &&
        p15RecorderDecision.runtimeAuthorityChanged === false,
      "P15-20 depends on P15-19 recorder promotion remaining partial and unpromoted.",
    ),
    check(
      "raw-packaged-recorder-objects-stay-denied",
      !files.nodeBridgeSources.text.includes("MediaRecorder") &&
        !files.nodeBridgeSources.text.includes("AudioRecord") &&
        !files.nodeBridgeSources.text.includes("AudioPlaybackCaptureConfiguration") &&
        !files.nodeBridgeSources.text.includes("ParcelFileDescriptor"),
      "P15-20 must not expose raw MediaRecorder, AudioRecord, playback capture, or file descriptor APIs through Node sources.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-20 evidence scenarios must be ready while packaged recorder archives remain explicit blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("installAndRunNodeV13PackagedRecorderSmoke") &&
        files.phase15Gradle.text.includes("verify-node-phase15-packaged-recorder-smoke.js") &&
        files.phase15Readme.text.includes(":app:installAndRunNodeV13PackagedRecorderSmoke") &&
        files.testing.text.includes(":app:installAndRunNodeV13PackagedRecorderSmoke") &&
        files.ciGates.text.includes(":app:installAndRunNodeV13PackagedRecorderSmoke"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-20 packaged recorder smoke gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-20: Add packaged recorder and media stress evidence.") &&
        files.phase15Tasks.text.includes("phase15-packaged-recorder-smoke.json") &&
        files.phase15Tasks.text.includes("packaged_recorder_start_stop_positive_archive"),
      "P15-20 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-packaged-recorder-smoke-v1",
    generatedAt: new Date().toISOString(),
    gate: "installAndRunNodeV13PackagedRecorderSmoke",
    task: "P15-20",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    packagedBoundaryContract: boundary,
    stressMatrix: rows,
    scenarios,
    checks,
    policyViolations,
    blockers: openBlockers.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      phase14PackagedRecorderStressReady: scenarioById(scenarios, "phase14_packaged_stress_imported").status === "ready",
      recorderDependencyReady: statusOf(p15RecorderReport) === "partial" && p15RecorderDecision.recorderPromoted === false,
      currentPackagedBoundaryReady: true,
      positivePackagedStartStopReady: false,
      foregroundDisclosureVisibleReady: false,
      deterministicStopReady: false,
      taskRemovalCleanupReady: false,
      relaunchCrashRecoveryReady: false,
      quotaTempCleanupReady: false,
      denialWithoutCapabilityReady: true,
      safeDowngradeReady: false,
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: openBlockers.length,
      packagedRecorderPromoted: false,
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

function rowStatus(rows, id) {
  const row = rows.find((entry) => entry.id === id);
  return row ? row.status : "missing";
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 Packaged Recorder Smoke",
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
      console.log("Usage: node verify-node-phase15-packaged-recorder-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
