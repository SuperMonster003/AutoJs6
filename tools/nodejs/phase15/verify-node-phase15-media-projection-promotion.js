#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-media-projection-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_media_projection_provider_contract",
  "phase14_activity_owned_prompt_capture_smoke",
  "phase14_media_projection_raw_object_boundary",
  "phase13_screenshot_image_ocr_import",
  "phase15_accessibility_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_METHODS = Object.freeze(["requestScreenCapture", "nextImage", "stop"]);
const REQUIRED_DEVICE_CLASSES = Object.freeze(["android_12_class", "android_15_plus_class"]);
const REQUIRED_RAW_DENIALS = Object.freeze([
  "MediaProjection",
  "ImageReader",
  "Bitmap",
  "Image",
  "VirtualDisplay",
  "Display",
  "Surface",
  "ScreenCapturer",
  "ImageWrapper",
  "Context",
  "Activity",
  "Intent",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "media_projection_android12_prompt_capture_archive",
  "media_projection_android15_plus_prompt_capture_archive",
  "media_projection_foreground_disclosure_stop_action_archive",
  "media_projection_task_removal_relaunch_crash_archive",
  "media_projection_packaged_apk_evidence_archive",
  "media_projection_safe_downgrade_archive",
  "phase15_security_type_doc_sync",
]);
const PHASE14_BLOCKERS = Object.freeze([
  "media_projection_android12_prompt_capture_archive",
  "media_projection_android15_plus_prompt_capture_archive",
  "media_projection_foreground_disclosure_stop_action_archive",
  "media_projection_task_removal_relaunch_crash_archive",
]);

const FILES = {
  plan: "docs/nodejs/phase15/media-projection-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_MEDIA_PROJECTION_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_09_MEDIA_PROJECTION_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  accessibilityReport: "build/reports/nodejs/phase15-accessibility-promotion.json",
  phase14Plan: "docs/nodejs/phase14/media-projection-provider-plan.json",
  phase14PlanDoc: "docs/nodejs/phase14/PHASE14_MEDIA_PROJECTION_PROVIDER.md",
  phase14TaskReport: "docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md",
  phase14GeneratedReport: "build/reports/nodejs/phase14-media-projection-provider.json",
  p13Report: "docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md",
  p13GeneratedReport: "build/reports/nodejs/phase13-screenshot-image-ocr-promotion.json",
  screenCaptureSmoke: "app/src/androidTest/java/org/autojs/autojs/engine/NodeScreenCaptureRealDeviceSmokeInstrumentationTest.kt",
  bridgeSmokeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  mediaProjectionTypes: "docs/nodejs/types/autojs6-node/media_projection.d.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  deviceMatrix: "docs/nodejs/DEVICE_MATRIX.md",
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
  console.log("Phase 15 MediaProjection promotion: " + report.decision.status);
  console.log("Phase 15 MediaProjection blockers: " + report.blockers.length);
  console.log("Phase 15 MediaProjection policy violations: " + report.policyViolations.length);
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
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContracts.text);
  const disclosureAudit = parseJson(files.disclosureAudit.text);
  const promotionReport = parseJson(files.promotionReport.text);
  const disclosureReport = parseJson(files.disclosureReport.text);
  const accessibilityReport = parseJson(files.accessibilityReport.text);
  const phase14Plan = parseJson(files.phase14Plan.text);
  const phase14Report = parseJson(files.phase14GeneratedReport.text);
  const p13Report = parseJson(files.p13GeneratedReport.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-09") || {};
  const mediaContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "media_projection") || {};
  const mediaAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "media_projection") || {};
  const providerContract = plan.providerContract || {};
  const phase14Contract = phase14Plan.providerContract || {};
  const archiveRequirements = plan.manualArchiveRequirements || {};
  const phase14Blockers = (phase14Report.blockers || []).map((entry) => entry.id);
  const p13Decision = p13Report.decision || {};
  const phase14Decision = phase14Report.decision || {};
  const scenarios = [
    scenario("phase14_media_projection_imported", "P14-13 MediaProjection contract is imported without promotion claims", [
      value("phase14_plan_schema", phase14Plan.schema === "autojs6-node-phase14-media-projection-provider-plan-v1"),
      value("phase14_plan_status", phase14Plan.status === "partial"),
      value("phase14_report_status", statusOf(phase14Report) === "partial"),
      value("phase14_contract_ready", phase14Decision.providerContractReady === true),
      value("phase14_smoke_imported", phase14Decision.activityOwnedSmokeImported === true),
      value("phase14_raw_boundary_ready", phase14Decision.rawObjectBoundaryReady === true),
      value("phase14_archives_open", phase14Decision.requiredDeviceArchivesReady === false),
      value("phase14_provider_not_ready", phase14Decision.screenCaptureBridgeProviderReady === false),
      value("phase14_blockers_imported", PHASE14_BLOCKERS.every((id) => phase14Blockers.includes(id))),
      value("contract_methods_match", sameSet(providerContract.methods || [], phase14Contract.methods || [])),
      value("raw_denials_match", sameSet(providerContract.rawObjectsDenied || [], phase14Contract.rawObjectsDenied || [])),
      token(files.phase14TaskReport, "The task remains unchecked because Android 12 class and Android 15+ class"),
    ]),
    scenario("activity_owned_prompt_capture_path", "Activity-owned prompt/capture path exists but remains optional evidence", [
      token(files.screenCaptureSmoke, "capturesOneFrameAfterUserGrantsScreenCapturePermission"),
      token(files.screenCaptureSmoke, "MainActivity::class.java"),
      token(files.screenCaptureSmoke, "ScreenCaptureRequester"),
      token(files.screenCaptureSmoke, "permission.resultCode == Activity.RESULT_OK"),
      token(files.screenCaptureSmoke, "ScreenCapturer("),
      token(files.screenCaptureSmoke, "captureOneFrame"),
      token(files.screenCaptureSmoke, "ImageWrapper.toBitmap(frame)"),
      token(files.screenCaptureSmoke, "bitmap.recycle()"),
      token(files.screenCaptureSmoke, "frame.close()"),
      token(files.screenCaptureSmoke, "currentCapturer.release()"),
      token(files.screenCaptureSmoke, "ScreenCapturerForegroundService"),
      token(files.providerRegistry, '"permissionPromptPolicy" to "Activity-owned ScreenCaptureRequester smoke exists; Node bridge provider must not prompt directly until promoted"'),
      token(files.deviceMatrix, "profile: `screen_capture`"),
    ]),
    scenario("raw_object_and_fail_closed_boundary", "Raw objects and provider authority remain denied until promotion evidence closes", [
      value("raw_denials", includesAll(providerContract.rawObjectsDenied || [], REQUIRED_RAW_DENIALS)),
      token(files.p13Report, "Raw boundary: raw `MediaProjection`, `ImageReader`, `ScreenCapturer`"),
      token(files.p13Report, "Raw Bitmap/OpenCV/MediaProjection object parity stays stable-denied"),
      token(files.providerRegistry, "MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE"),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw MediaProjection, ImageReader, ScreenCapturer, Image, Bitmap, or ImageWrapper crosses the bridge"'),
      token(files.protocol, 'val mediaProjectionMethods = setOf("requestScreenCapture", "nextImage", "stop")'),
      token(files.protocol, "private fun dispatchMediaProjection"),
      token(files.permissionManifest, 'const val SCREEN_CAPTURE = "screen_capture"'),
      token(files.bridgeSmokeTest, "mediaProjectionAndImageModulesRejectWithStableCodeUntilProvidersAreConnected"),
      token(files.nodeBridgeSources, "function __autojs6_limited_media_projection()"),
      token(files.securityModel, "expose a raw `MediaProjection`"),
      value("types_no_raw_android_classes", !containsAny(files.mediaProjectionTypes.text, [
        "android.media.projection.MediaProjection",
        "ImageReader",
        "Bitmap",
        "VirtualDisplay",
        "Surface",
        "Display",
      ])),
    ]),
    scenario("phase13_and_phase15_dependencies", "P13 screenshot/image/OCR and P15 accessibility dependency gates remain partial prerequisites", [
      value("p13_status", statusOf(p13Report) === "partial"),
      value("p13_screen_capture_not_ready", p13Decision.screenCaptureBridgeProviderReady === false),
      value("p13_image_ready", p13Decision.imageHandleLiveReadRecycleReady === true),
      value("p13_ocr_ready", p13Decision.ocrLiveMlKitReady === true),
      value("accessibility_status", statusOf(accessibilityReport) === "partial"),
      value("accessibility_not_promoted", accessibilityReport.decision && accessibilityReport.decision.accessibilityPromoted === false),
      token(files.accessibilityReport, "\"runtimeAuthorityChanged\": false"),
      token(files.p13Report, "Activity-owned MediaProjection provider"),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep MediaProjection behind prompt, disclosure, rollback, packaged, and raw-object bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("media_contract_provider_poc", mediaContract.currentState === "provider_poc"),
      value("media_contract_p15_task", includesAll(mediaContract.p15Tasks || [], ["P15-09"])),
      value("media_contract_evidence_bars", includesAll(mediaContract.requiredEvidence || [], [
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("media_audit_provider_poc", mediaAudit.currentState === "provider_poc"),
      value("media_audit_disable_flag", mediaAudit.disableFlag === "node.provider.media_projection.enabled"),
      value("media_audit_diagnostic", mediaAudit.diagnosticCode === "NODE_PROVIDER_MEDIA_PROJECTION_DISABLED"),
      value("media_audit_disclosure_stop", mediaAudit.foregroundNotificationRequired === true && mediaAudit.stopActionRequired === true),
      value("media_audit_crash_breaker", mediaAudit.crashCircuitBreakerRequired === true),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-09 MediaProjection gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.ui_overlay_automation_media"),
      value("surface_mapping_backlog", includesAll(p15Mapping.sourceBacklogTasks || [], ["P14-13"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "permission_lifecycle_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15MediaProjectionPromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15MediaProjectionPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-media-projection-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15MediaProjectionPromotion"),
      token(files.testing, ":app:verifyNodePhase15MediaProjectionPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15MediaProjectionPromotion"),
      token(files.phase15Tasks, "phase15-media-projection-promotion.json"),
      token(files.phase15Tasks, "media_projection_android15_plus_prompt_capture_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-media-projection-promotion-plan-v1" &&
        plan.task === "P15-09" &&
        plan.sourceBacklogTask === "P14-13" &&
        plan.providerId === "media_projection" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-09 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-09 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        sameSet(plan.requiredPromotionEvidence || [], REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-09 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "contract-methods-and-boundary",
      sameSet(providerContract.methods || [], REQUIRED_METHODS) &&
        providerContract.activityOwnedPromptRequired === true &&
        providerContract.mustNotPromptFromEmbeddedService === true &&
        providerContract.opaqueHandleOnly === true &&
        includesAll(providerContract.rawObjectsDenied || [], REQUIRED_RAW_DENIALS),
      "P15-09 must reserve requestScreenCapture/nextImage/stop with Activity-owned prompt and raw object denial.",
    ),
    check(
      "manual-archive-requirements",
      sameSet(archiveRequirements.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES) &&
        archiveRequirements.requiresUserApprovedPrompt === true &&
        includesAll(archiveRequirements.requiredScenarios || [], [
          "request prompt grant and denial",
          "one-frame capture through ScreenCapturer",
          "foreground disclosure and stop action",
          "task removal cleanup",
          "crash recovery behavior",
          "Safe downgrade after provider disable",
        ]),
      "P15-09 must declare Android 12/15+, prompt/capture, disclosure, lifecycle, crash, and Safe downgrade archives.",
    ),
    check(
      "phase14-media-projection-partial",
      statusOf(phase14Report) === "partial" &&
        phase14Decision.providerContractReady === true &&
        phase14Decision.activityOwnedSmokeImported === true &&
        phase14Decision.rawObjectBoundaryReady === true &&
        phase14Decision.requiredDeviceArchivesReady === false &&
        phase14Decision.screenCaptureBridgeProviderReady === false,
      "P15-09 requires P14-13 MediaProjection gate to be partial with device archives still open.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        mediaContract.currentState === "provider_poc" &&
        includesAll(mediaContract.requiredEvidence || [], ["connected_device_evidence", "packaged_apk_evidence", "user_visible_disclosure"]),
      "P15-09 requires ready promotion contracts with media_projection still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        mediaAudit.currentState === "provider_poc" &&
        mediaAudit.disableFlag === "node.provider.media_projection.enabled" &&
        mediaAudit.diagnosticCode === "NODE_PROVIDER_MEDIA_PROJECTION_DISABLED" &&
        mediaAudit.foregroundNotificationRequired === true &&
        mediaAudit.stopActionRequired === true,
      "P15-09 requires ready disclosure rollback audit for media_projection.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready" || entry.status === "partial"),
      "All P15-09 evidence scenarios must be either ready or explicitly partial due to planned prompt/capture archives.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15MediaProjectionPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-media-projection-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15MediaProjectionPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15MediaProjectionPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15MediaProjectionPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-09 MediaProjection promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-09: Promote Activity-owned MediaProjection provider.") &&
        files.phase15Tasks.text.includes("phase15-media-projection-promotion.json") &&
        files.phase15Tasks.text.includes("media_projection_android12_prompt_capture_archive"),
      "P15-09 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-media-projection-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15MediaProjectionPromotion",
    task: "P15-09",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    providerContract: {
      module: providerContract.module || "missing",
      capability: providerContract.capability || "missing",
      methods: providerContract.methods || [],
      activityOwnedPromptRequired: providerContract.activityOwnedPromptRequired === true,
      opaqueHandleOnly: providerContract.opaqueHandleOnly === true,
      rawObjectsDenied: providerContract.rawObjectsDenied || [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      providerContractReady: scenarioById(scenarios, "phase14_media_projection_imported").status === "ready",
      activityOwnedSmokeImported: scenarioById(scenarios, "activity_owned_prompt_capture_path").status === "ready",
      rawObjectBoundaryReady: scenarioById(scenarios, "raw_object_and_fail_closed_boundary").status === "ready",
      requiredDeviceArchivesReady: false,
      foregroundDisclosureReady: false,
      lifecycleRecoveryReady: false,
      screenCaptureBridgeProviderReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      mediaProjectionPromoted: false,
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
    "# Phase 15 MediaProjection Promotion",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Provider Contract",
    "",
    `- Module: \`${report.providerContract.module}\``,
    `- Capability: \`${report.providerContract.capability}\``,
    `- Methods: ${report.providerContract.methods.map((entry) => `\`${entry}\``).join(", ")}`,
    `- Activity-owned prompt required: ${report.providerContract.activityOwnedPromptRequired}`,
    `- Opaque handle only: ${report.providerContract.opaqueHandleOnly}`,
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

function containsAny(text, needles) {
  return needles.some((needle) => String(text).includes(needle));
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
      console.log("Usage: node verify-node-phase15-media-projection-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
