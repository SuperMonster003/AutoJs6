#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-image-ocr-promotion";
const REQUIRED_READY_EVIDENCE = Object.freeze([
  "phase14_advanced_image_operations_contract",
  "phase14_image_invalid_input_quota_boundary",
  "phase14_packaged_image_ocr_evidence_contract",
  "phase14_packaged_ocr_model_policy",
  "phase13_screenshot_image_ocr_import",
  "phase15_media_projection_dependency_gate",
  "phase15_promotion_contracts",
  "phase15_disclosure_rollback_contract",
  "phase15_surface_manifest_mapping",
]);
const REQUIRED_BLOCKERS = Object.freeze([
  "image_advanced_transform_provider_archive",
  "image_matching_color_provider_archive",
  "image_temp_handle_quota_leak_stress_archive",
  "image_packaged_scoped_output_archive",
  "packaged_ocr_model_absence_archive",
  "packaged_ocr_explicit_provider_mode_archive",
  "packaged_image_ocr_quota_leak_stress_archive",
  "packaged_capture_ocr_disclosure_archive",
  "image_ocr_safe_downgrade_archive",
  "phase15_security_type_doc_sync",
]);
const IMAGE_BLOCKERS = Object.freeze(REQUIRED_BLOCKERS.slice(0, 4));
const OCR_BLOCKERS = Object.freeze(REQUIRED_BLOCKERS.slice(4, 8));

const FILES = {
  plan: "docs/nodejs/phase15/image-ocr-promotion-plan.json",
  planDoc: "docs/nodejs/phase15/PHASE15_IMAGE_OCR_PROMOTION.md",
  taskReport: "docs/nodejs/reports/phase15/P15_10_IMAGE_OCR_PROMOTION.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  promotionContracts: "docs/nodejs/phase15/node-v1-3-promotion-contracts.json",
  promotionReport: "build/reports/nodejs/phase15-promotion-contracts.json",
  disclosureAudit: "docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json",
  disclosureReport: "build/reports/nodejs/phase15-disclosure-rollback-audit.json",
  mediaProjectionReport: "build/reports/nodejs/phase15-media-projection-promotion.json",
  phase14ImagePlan: "docs/nodejs/phase14/advanced-image-operations-plan.json",
  phase14ImageReport: "docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md",
  phase14ImageGenerated: "build/reports/nodejs/phase14-advanced-image-operations.json",
  phase14OcrPlan: "docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json",
  phase14OcrReport: "docs/nodejs/reports/phase14/P14_15_PACKAGED_IMAGE_OCR_EVIDENCE.md",
  phase14OcrGenerated: "build/reports/nodejs/phase14-packaged-image-ocr-evidence.json",
  p13Report: "docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md",
  p13Generated: "build/reports/nodejs/phase13-screenshot-image-ocr-promotion.json",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  imageExpansionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt",
  concurrencyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt",
  ocrTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  ocrTypes: "docs/nodejs/types/autojs6-node/ocr.d.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
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
  console.log("Phase 15 image/OCR promotion: " + report.decision.status);
  console.log("Phase 15 image/OCR blockers: " + report.blockers.length);
  console.log("Phase 15 image/OCR policy violations: " + report.policyViolations.length);
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
  const mediaProjectionReport = parseJson(files.mediaProjectionReport.text);
  const imagePlan = parseJson(files.phase14ImagePlan.text);
  const imageReport = parseJson(files.phase14ImageGenerated.text);
  const ocrPlan = parseJson(files.phase14OcrPlan.text);
  const ocrReport = parseJson(files.phase14OcrGenerated.text);
  const p13Report = parseJson(files.p13Generated.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-10") || {};
  const imageOcrContract = (contracts.providerPromotionBars || []).find((entry) => entry.id === "image_ocr") || {};
  const imageOcrAudit = (disclosureAudit.providerAudits || []).find((entry) => entry.providerId === "image_ocr") || {};
  const operationContract = plan.operationContract || {};
  const imageDecision = imageReport.decision || {};
  const ocrDecision = ocrReport.decision || {};
  const p13Decision = p13Report.decision || {};
  const imageBlockers = (imageReport.blockers || []).map((entry) => entry.id);
  const ocrBlockers = (ocrReport.blockers || []).map((entry) => entry.id);
  const scenarios = [
    scenario("phase14_advanced_image_imported", "P14-14 advanced image operation contract and boundaries are imported", [
      value("image_plan_schema", imagePlan.schema === "autojs6-node-phase14-advanced-image-operations-plan-v1"),
      value("image_report_status", statusOf(imageReport) === "partial"),
      value("image_api_contract_ready", imageDecision.apiContractReady === true),
      value("image_fake_provider_ready", imageDecision.fakeProviderCoverageReady === true),
      value("image_live_read_recycle_imported", imageDecision.liveReadRecycleImported === true),
      value("image_invalid_input_quota_ready", imageDecision.invalidInputAndQuotaReady === true),
      value("image_raw_boundary_ready", imageDecision.rawObjectBoundaryReady === true),
      value("image_live_provider_open", imageDecision.liveAdvancedProviderReady === false),
      value("image_packaged_open", imageDecision.packagedScopedOutputReady === false),
      value("image_blockers_imported", IMAGE_BLOCKERS.every((id) => imageBlockers.includes(id))),
      token(files.imageExpansionTest, "jsImageExpansionUsesFakeTransportAndBlocksShadowing"),
      token(files.concurrencyTest, "jsImageHandleLimitAllowsRecycleThenReuse"),
    ]),
    scenario("phase14_packaged_ocr_imported", "P14-15 packaged image/OCR model policy and smoke evidence are imported", [
      value("ocr_plan_schema", ocrPlan.schema === "autojs6-node-phase14-packaged-image-ocr-evidence-plan-v1"),
      value("ocr_report_status", statusOf(ocrReport) === "partial"),
      value("ocr_model_lookup_ready", ocrDecision.modelLookupPolicyReady === true),
      value("ocr_live_mlkit_imported", ocrDecision.liveMlKitEvidenceImported === true),
      value("ocr_packaged_fake_smoke", ocrDecision.packagedFakeSmokeImported === true),
      value("ocr_model_absence_policy", ocrDecision.modelAbsencePolicyReady === true),
      value("ocr_quota_boundary", ocrDecision.quotaAndUnknownHandleBoundaryReady === true),
      value("ocr_raw_boundary", ocrDecision.rawObjectBoundaryReady === true),
      value("ocr_archives_open", ocrDecision.packagedModelArchiveReady === false && ocrDecision.captureDisclosureArchiveReady === false),
      value("ocr_blockers_imported", OCR_BLOCKERS.every((id) => ocrBlockers.includes(id))),
      token(files.packagedSmokeTest, "ocr"),
      token(files.ocrTest, "kotlinOcrLiveProviderRecognizesMlKitImageHandle"),
    ]),
    scenario("p13_and_media_projection_dependencies", "P13 scoped image/OCR and P15 MediaProjection dependency gates remain partial prerequisites", [
      value("p13_status", statusOf(p13Report) === "partial"),
      value("p13_image_ready", p13Decision.imageHandleLiveReadRecycleReady === true),
      value("p13_ocr_ready", p13Decision.ocrLiveMlKitReady === true),
      value("p13_screen_capture_not_ready", p13Decision.screenCaptureBridgeProviderReady === false),
      value("media_projection_status", statusOf(mediaProjectionReport) === "partial"),
      value("media_projection_not_promoted", mediaProjectionReport.decision && mediaProjectionReport.decision.mediaProjectionPromoted === false),
      token(files.p13Report, "`readImage` plus explicit `recycle`"),
      token(files.mediaProjectionReport, "\"screenCaptureBridgeProviderReady\": false"),
    ]),
    scenario("raw_object_and_provider_mode_boundary", "Image/OCR raw objects and non-promoted OCR provider modes stay denied", [
      value("raw_denials", includesAll(operationContract.rawObjectsDenied || [], [
        "Bitmap",
        "Mat",
        "ImageWrapper",
        "MediaProjection",
        "TextRecognizer",
        "OCR engine",
        "ML Kit Text",
        "Java object",
      ])),
      value("stable_denied_modes", sameSet(operationContract.stableDeniedProviderFamilies || [], ["paddle", "rapid", "plugin"])),
      token(files.providerRegistry, "\"rawObjectPolicy\" to \"no raw Bitmap, Mat, ImageWrapper, OpenCV object, native pointer, or filesystem path handle crosses the bridge\""),
      token(files.securityModel, "JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy."),
      token(files.ocrTypes, "export interface OcrOptions extends AutoJs6Node.BridgeCallOptions"),
      token(files.ocrTypes, "recognize(image: AutoJs6Node.ImageHandleLike, options?: OcrOptions): Promise<readonly OcrResult[]>;"),
      token(files.imageTypes, "matchTemplate("),
    ]),
    scenario("phase15_contracts_and_rollback", "Phase 15 contracts keep image/OCR behind packaged, disclosure, rollback, type, and security bars", [
      value("promotion_report_ready", statusOf(promotionReport) === "ready"),
      value("disclosure_report_ready", statusOf(disclosureReport) === "ready"),
      value("image_ocr_contract_provider_poc", imageOcrContract.currentState === "provider_poc"),
      value("image_ocr_contract_p15_task", includesAll(imageOcrContract.p15Tasks || [], ["P15-10"])),
      value("image_ocr_contract_evidence_bars", includesAll(imageOcrContract.requiredEvidence || [], [
        "connected_device_evidence",
        "packaged_apk_evidence",
        "user_visible_disclosure",
        "rollback_disable_path",
        "safe_downgrade_behavior",
        "raw_object_denial_regression",
      ])),
      value("image_ocr_audit_provider_poc", imageOcrAudit.currentState === "provider_poc"),
      value("image_ocr_audit_disable_flag", imageOcrAudit.disableFlag === "node.provider.image_ocr.enabled"),
      value("image_ocr_audit_diagnostic", imageOcrAudit.diagnosticCode === "NODE_PROVIDER_IMAGE_OCR_DISABLED"),
      value("image_ocr_audit_disclosure_stop", imageOcrAudit.foregroundNotificationRequired === true && imageOcrAudit.stopActionRequired === true),
      value("image_ocr_audit_crash_breaker", imageOcrAudit.crashCircuitBreakerRequired === true),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 15 docs, surface manifest, and Gradle expose the P15-10 image/OCR gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.ui_overlay_automation_media"),
      value("surface_mapping_backlog", includesAll(p15Mapping.sourceBacklogTasks || [], ["P14-14", "P14-15"])),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "packaged_media_archive"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15ImageOcrPromotion"),
      value("surface_mapping_planned", p15Mapping.status === "planned"),
      token(files.phase15Gradle, "verifyNodePhase15ImageOcrPromotion"),
      token(files.phase15Gradle, "verify-node-phase15-image-ocr-promotion.js"),
      token(files.phase15Readme, ":app:verifyNodePhase15ImageOcrPromotion"),
      token(files.testing, ":app:verifyNodePhase15ImageOcrPromotion"),
      token(files.ciGates, ":app:verifyNodePhase15ImageOcrPromotion"),
      token(files.phase15Tasks, "phase15-image-ocr-promotion.json"),
      token(files.phase15Tasks, "packaged_capture_ocr_disclosure_archive"),
    ]),
  ];
  const checks = [
    check(
      "plan-schema",
      plan.schema === "autojs6-node-phase15-image-ocr-promotion-plan-v1" &&
        plan.task === "P15-10" &&
        sameSet(plan.sourceBacklogTasks || [], ["P14-14", "P14-15"]) &&
        plan.providerId === "image_ocr" &&
        plan.status === "partial" &&
        plan.promotionState === "provider_poc_not_promoted" &&
        plan.runtimeAuthorityChanged === false,
      "P15-10 plan must stay partial and must not change runtime authority.",
    ),
    check(
      "ready-evidence-declared",
      sameSet((plan.readyEvidence || []).map((entry) => entry.id), REQUIRED_READY_EVIDENCE) &&
        REQUIRED_READY_EVIDENCE.every((id) => files.planDoc.text.includes(id)),
      "P15-10 plan and docs must declare the imported ready evidence set.",
    ),
    check(
      "promotion-blockers-declared",
      sameSet((plan.openBlockers || []).map((entry) => entry.id), REQUIRED_BLOCKERS) &&
        sameSet(plan.requiredPromotionEvidence || [], REQUIRED_BLOCKERS) &&
        plan.openBlockers.every((entry) => entry.classification === "future_promotion" && entry.releaseBlocking === false) &&
        REQUIRED_BLOCKERS.every((id) => files.taskReport.text.includes(id)),
      "P15-10 must keep future-promotion blockers visible and non-release-blocking.",
    ),
    check(
      "phase14-advanced-image-partial",
      statusOf(imageReport) === "partial" &&
        imageDecision.apiContractReady === true &&
        imageDecision.invalidInputAndQuotaReady === true &&
        imageDecision.rawObjectBoundaryReady === true &&
        imageDecision.liveAdvancedProviderReady === false &&
        imageDecision.packagedScopedOutputReady === false,
      "P15-10 requires P14-14 advanced image report to be partial with live/provider archives still open.",
    ),
    check(
      "phase14-packaged-ocr-partial",
      statusOf(ocrReport) === "partial" &&
        ocrDecision.modelLookupPolicyReady === true &&
        ocrDecision.liveMlKitEvidenceImported === true &&
        ocrDecision.packagedFakeSmokeImported === true &&
        ocrDecision.quotaAndUnknownHandleBoundaryReady === true &&
        ocrDecision.rawObjectBoundaryReady === true &&
        ocrDecision.packagedModelArchiveReady === false &&
        ocrDecision.captureDisclosureArchiveReady === false,
      "P15-10 requires P14-15 packaged OCR report to be partial with packaged archives still open.",
    ),
    check(
      "phase15-media-projection-partial",
      statusOf(mediaProjectionReport) === "partial" &&
        mediaProjectionReport.decision &&
        mediaProjectionReport.decision.mediaProjectionPromoted === false,
      "P15-10 depends on the P15-09 MediaProjection gate remaining partial and unpromoted.",
    ),
    check(
      "phase15-contracts-ready",
      statusOf(promotionReport) === "ready" &&
        imageOcrContract.currentState === "provider_poc" &&
        includesAll(imageOcrContract.requiredEvidence || [], ["packaged_apk_evidence", "connected_device_evidence", "user_visible_disclosure"]),
      "P15-10 requires ready promotion contracts with image_ocr still provider_poc.",
    ),
    check(
      "phase15-disclosure-audit-ready",
      statusOf(disclosureReport) === "ready" &&
        imageOcrAudit.currentState === "provider_poc" &&
        imageOcrAudit.disableFlag === "node.provider.image_ocr.enabled" &&
        imageOcrAudit.diagnosticCode === "NODE_PROVIDER_IMAGE_OCR_DISABLED" &&
        imageOcrAudit.foregroundNotificationRequired === true &&
        imageOcrAudit.stopActionRequired === true,
      "P15-10 requires ready disclosure rollback audit for image_ocr.",
    ),
    check(
      "scenario-evidence-present",
      scenarios.every((entry) => entry.status === "ready" || entry.status === "partial"),
      "All P15-10 evidence scenarios must be either ready or explicitly partial due to planned image/OCR archives.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.phase15Gradle.text.includes("verifyNodePhase15ImageOcrPromotion") &&
        files.phase15Gradle.text.includes("verify-node-phase15-image-ocr-promotion.js") &&
        files.phase15Readme.text.includes(":app:verifyNodePhase15ImageOcrPromotion") &&
        files.testing.text.includes(":app:verifyNodePhase15ImageOcrPromotion") &&
        files.ciGates.text.includes(":app:verifyNodePhase15ImageOcrPromotion"),
      "Gradle, README, TESTING, and CI_GATES must reference the P15-10 image/OCR promotion gate.",
    ),
    check(
      "task-plan-partial",
      files.phase15Tasks.text.includes("- [ ] P15-10: Promote advanced image and OCR packaged evidence.") &&
        files.phase15Tasks.text.includes("phase15-image-ocr-promotion.json") &&
        files.phase15Tasks.text.includes("image_advanced_transform_provider_archive"),
      "P15-10 must remain unchecked while documenting the partial gate and output report.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-image-ocr-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15ImageOcrPromotion",
    task: "P15-10",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    operationContract,
    scenarios,
    checks,
    policyViolations,
    blockers: (plan.openBlockers || []).map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: false,
      advancedImageContractReady: scenarioById(scenarios, "phase14_advanced_image_imported").status === "ready",
      packagedOcrContractReady: scenarioById(scenarios, "phase14_packaged_ocr_imported").status === "ready",
      mediaProjectionDependencyReady: statusOf(mediaProjectionReport) === "partial",
      requiredArchiveEvidenceReady: false,
      runtimeAuthorityChanged: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: Array.isArray(plan.openBlockers) ? plan.openBlockers.length : 0,
      imageOcrPromoted: false,
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
    "# Phase 15 Image OCR Promotion",
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
      console.log("Usage: node verify-node-phase15-image-ocr-promotion.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
