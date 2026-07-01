#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-packaged-image-ocr-evidence";
const RAW_DENIALS = Object.freeze([
  "TextRecognizer",
  "OCR engine",
  "ML Kit Text",
  "Paddle model",
  "Rapid model",
  "plugin OCR service",
  "Bitmap",
  "Mat",
  "ImageWrapper",
  "MediaProjection",
  "native pointer",
  "model path",
  "Java object",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "packaged_ocr_model_absence_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged APK run where ML Kit/Paddle/Rapid/plugin OCR model assets or services are absent, producing stable unavailable/degraded bridge errors without leaking OCR/image resources.",
  },
  {
    id: "packaged_ocr_explicit_provider_mode_archive",
    classification: "future_promotion",
    requiredEvidence: "Explicit packaged OCR provider-mode evidence for mlkit success and Paddle/Rapid/plugin stable-denied lookup diagnostics.",
  },
  {
    id: "packaged_image_ocr_quota_leak_stress_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged image/OCR temp-handle quota, repeated read/OCR/recycle loops, provider destroy, timeout, and leak/stress archive with active image and OCR resources returning to zero.",
  },
  {
    id: "packaged_capture_ocr_disclosure_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged capture-to-OCR evidence with MediaProjection foreground disclosure, stop action, task removal/relaunch cleanup, and missing-capability denial.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_15_PACKAGED_IMAGE_OCR_EVIDENCE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_PACKAGED_IMAGE_OCR_EVIDENCE.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p13Report: "docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md",
  p14ImageReport: "docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  ocrTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt",
  imageExpansionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt",
  concurrencyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  ocrTypes: "docs/nodejs/types/autojs6-node/ocr.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
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
  console.log("Phase 14 packaged image/OCR evidence: " + report.decision.status);
  console.log("Phase 14 packaged image/OCR blockers: " + report.blockers.length);
  console.log("Phase 14 packaged image/OCR policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const generatedReports = {
    p13ScreenshotGeneratedReport: "phase13-screenshot-image-ocr-promotion.json",
    p14AdvancedImageGeneratedReport: "phase14-advanced-image-operations.json",
  };
  Object.entries(generatedReports).forEach(([key, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      key,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const plan = parseJson(files.planJson.text);
  const contract = plan.operationContract || {};
  const p13Generated = parseJson(files.p13ScreenshotGeneratedReport.text);
  const p14AdvancedImageGenerated = parseJson(files.p14AdvancedImageGeneratedReport.text);
  const p13Decision = p13Generated.decision || {};
  const p14AdvancedImageDecision = p14AdvancedImageGenerated.decision || {};
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const scenarios = [
    scenario("provider_lookup_policy", "ML Kit optional mode and Paddle/Rapid/plugin stable-denied lookup policy are recorded", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-packaged-image-ocr-evidence-plan-v1"),
      checkTokenValue("task", plan.task === "P14-15"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("modules", sameSet(contract.modules || [], ["image", "images", "ocr", "autojs6:compat"])),
      checkTokenValue("defaultOcrEngine", contract.defaultOcrEngine === "mlkit-chinese"),
      checkTokenValue("explicitOcrModes", sameSet(contract.explicitOcrModes || [], ["mlkit"])),
      checkTokenValue("stableDeniedProviderFamilies", sameSet(contract.stableDeniedProviderFamilies || [], ["paddle", "rapid", "plugin"])),
      checkTokenValue("modelAbsenceStableDenied", contract.modelAbsenceStableDenied === true),
      token(files.providerRegistry, '"defaultEngine" to "mlkit-chinese"'),
      token(files.providerRegistry, '"paddleEmbeddedVariant" to paddleVariant'),
      token(files.providerRegistry, '"paddlePluginServiceCount" to queryPaddleOcrPluginServiceCount(appContext).toString()'),
      token(files.providerRegistry, '"fallbackPromotion" to "blocked_until_Paddle_Rapid_plugin_model_selection_and_packaged_asset_policy_evidence"'),
      token(files.rhinoCompatTypes, 'tap(mode?: "mlkit"): "mlkit";'),
      token(files.rhinoCompatTypes, 'readonly mode: "mlkit";'),
      token(files.p13Report, "Paddle/Rapid/plugin provider selection stable-denied"),
    ]),
    scenario("live_mlkit_imported", "P13-20 live ML Kit OCR over provider-owned image handles is imported", [
      checkTokenValue("p13_status", p13Generated.status === "partial" || p13Decision.status === "partial"),
      checkTokenValue("p13_image_read_recycle_ready", p13Decision.imageHandleLiveReadRecycleReady === true),
      checkTokenValue("p13_ocr_mlkit_ready", p13Decision.ocrLiveMlKitReady === true),
      token(files.providerRegistry, '"liveMethods" to "recognize,recognizeText,detectTextBounds"'),
      token(files.providerRegistry, '"cleanupPolicy" to "OCR consumes provider-owned image handles; unknown or recycled handles reject and active OCR/image resources return to zero"'),
      token(files.ocrTest, "kotlinOcrLiveProviderRecognizesMlKitImageHandle"),
      token(files.ocrTest, "MLKit OCR provider must be available for live OCR gate"),
      token(files.ocrTest, 'assertEquals("0", diagnostics["embedded_script.image_active_handle_count"])'),
      token(files.ocrTest, 'assertEquals("0", diagnostics["embedded_script.ocr_active_operation_count"])'),
      token(files.protocol, "NodeBridgeResourceType.OCR_OPERATION"),
      token(files.protocol, "ocrDiagnostics.recordDispatch()"),
      token(files.protocol, "InputImage.fromBitmap(ocrInput.bitmap, 0)"),
    ]),
    scenario("packaged_fake_smoke_imported", "Packaged fake-provider bridge smoke proves OCR module shape, capability declarations, and pending cleanup", [
      token(files.packagedSmokeTest, 'const ocr = require("ocr");'),
      token(files.packagedSmokeTest, "const ocrResult = await ocr.recognize("),
      token(files.packagedSmokeTest, 'ocrResult[0].text === "packaged"'),
      token(files.packagedSmokeTest, 'request.permissions.indexOf("ocr") >= 0 && request.permissions.indexOf("image") >= 0'),
      token(files.packagedSmokeTest, 'console.log("packaged.v1_1_bridge.ocr=" + ocrOk);'),
      token(files.packagedSmokeTest, 'console.log("packaged.v1_1_bridge.pending=" + bridge.__test.pendingCount());'),
      token(files.packagedSmokeTest, '"packaged.v1_1_bridge.ocr=true"'),
      token(files.packagedSmokeTest, '"packaged.v1_1_bridge.permissions=true"'),
      token(files.packagedSmokeTest, '"packaged.v1_1_bridge.pending=0"'),
    ]),
    scenario("quota_and_unknown_handle_boundary", "Image/OCR quota, recycle, unknown/recycled handle denial, and active-resource cleanup are anchored", [
      checkTokenValue("temporaryImageHandlesQuotaBound", contract.temporaryImageHandlesQuotaBound === true),
      checkTokenValue("recycleRequiredAfterCompatPathInput", contract.recycleRequiredAfterCompatPathInput === true),
      checkTokenValue("p14_advanced_image_quota_ready", p14AdvancedImageDecision.invalidInputAndQuotaReady === true),
      token(files.ocrTest, "androidOcrRejectsUnknownImageHandleWithoutLeakingResources"),
      token(files.ocrTest, "ocr.android.denied=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST/ERR_AUTOJS6_BRIDGE_INVALID_REQUEST/ocr/recognizeText"),
      token(files.ocrTest, 'result.nativeValues["embedded_script.bridge_resource.active_count"]?.let { assertEquals("0", it) }'),
      token(files.concurrencyTest, "jsImageHandleLimitAllowsRecycleThenReuse"),
      token(files.concurrencyTest, "bridge.limit.image=1|image:one.png"),
      token(files.p14ImageReport, "image_temp_handle_quota_leak_stress_archive"),
    ]),
    scenario("packaged_model_absence_policy", "Packaged model absence remains stable-denied until archived evidence exists", [
      checkTokenValue("ocr_packaged_support", (contract.packagedSupport || {}).ocr === "provider-dependent"),
      checkTokenValue("image_packaged_support", (contract.packagedSupport || {}).image === "degraded"),
      checkTokenValue("packagedCaptureRequiresMediaProjectionPromotion", contract.packagedCaptureRequiresMediaProjectionPromotion === true),
      token(files.providerRegistry, "P13-20 partial ML Kit OCR provider; packaged promotion requires model/fallback policy, image-handle assets, and capture/input evidence"),
      token(files.providerRegistry, 'packagedDegraded('),
      token(files.providerRegistry, '"packagedPromotion" to "blocked_until_packaged_image_assets_capture_permission_and_processing_evidence"'),
      token(files.compatibilityProfile, "packaged model policy and broader image-provider ownership remain future work"),
      token(files.compatibilityProfile, "packaged capture/OCR behavior"),
      token(files.securityModel, "packaged capture/OCR behavior behind later gates"),
    ]),
    scenario("raw_object_boundary", "Raw OCR engines, model artifacts, image objects, and Java objects stay outside Node", [
      checkTokenValue("rawDenials", includesAll(contract.rawObjectsDenied || [], RAW_DENIALS)),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw TextRecognizer, OCR engine, ML Kit Text, Paddle/Rapid model, Bitmap, Mat, ImageWrapper, or Java object crosses the bridge"'),
      token(files.securityModel, "JavaScript must not receive raw OCR engines, barcode scanners, raw ML Kit `Barcode`, model paths, native pointers, `Bitmap`, `Mat`, `ImageWrapper`, or Java objects."),
      token(files.compatibilityProfile, "no raw OCR engine, model path, native pointer, `Bitmap`, `Mat`, `ImageWrapper`, or Java object crosses into Node"),
      checkTokenValue("ocr_types_no_raw_android_classes", !containsAny(files.ocrTypes.text, [
        "TextRecognizer",
        "InputImage",
        "android.graphics.Bitmap",
        "org.opencv",
        "ImageWrapper",
      ])),
      checkTokenValue("image_types_no_model_paths", !files.imageTypes.text.includes("modelPath")),
    ]),
    scenario("promotion_archives_open", "Packaged model absence, explicit provider modes, quota/leak stress, and capture/disclosure archives remain future work", [
      token(files.planDoc, "Packaged APK run where ML Kit/Paddle/Rapid/plugin OCR model assets or services are absent"),
      token(files.planDoc, "Explicit packaged OCR provider-mode evidence"),
      token(files.planDoc, "Packaged image/OCR temp-handle quota"),
      token(files.planDoc, "Packaged capture-to-OCR evidence"),
      token(files.phase14Report, "packaged_ocr_model_absence_archive"),
      token(files.phase14Report, "packaged_ocr_explicit_provider_mode_archive"),
      token(files.phase14Report, "packaged_image_ocr_quota_leak_stress_archive"),
      token(files.phase14Report, "packaged_capture_ocr_disclosure_archive"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-15 gate", [
      token(files.gradle14, "verifyNodePhase14PackagedImageOcrEvidence"),
      token(files.gradle14, "verify-node-phase14-packaged-image-ocr-evidence.js"),
      token(files.phase14Tasks, "Packaged image/OCR evidence gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14PackagedImageOcrEvidence"),
      token(files.testing, ":app:verifyNodePhase14PackagedImageOcrEvidence"),
      token(files.ciGates, ":app:verifyNodePhase14PackagedImageOcrEvidence"),
      token(files.surfaceManifestDoc, "P14-15 adds the packaged image/OCR evidence contract"),
      token(files.surfaceManifest, "docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json"),
    ]),
  ];
  const checks = [
    check("phase13-screenshot-generated-report-present", files.p13ScreenshotGeneratedReport.exists, "The P13-20 screenshot/image/OCR report must be generated before the P14-15 gate."),
    check("phase14-advanced-image-generated-report-present", files.p14AdvancedImageGeneratedReport.exists, "The P14-14 advanced image report must be generated before the P14-15 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-packaged-image-ocr-evidence-plan-v1", "P14-15 packaged image/OCR evidence plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Packaged Image OCR Evidence"), "P14-15 packaged image/OCR documentation must exist."),
    check(
      "contract-model-and-denials",
      contract.defaultOcrEngine === "mlkit-chinese" &&
        sameSet(contract.explicitOcrModes || [], ["mlkit"]) &&
        sameSet(contract.stableDeniedProviderFamilies || [], ["paddle", "rapid", "plugin"]) &&
        includesAll(contract.rawObjectsDenied || [], RAW_DENIALS),
      "P14-15 must reserve ML Kit-only OCR mode and keep non-promoted OCR providers/raw objects denied.",
    ),
    check(
      "surface-manifest-evidence",
      proSurface &&
        proSurface.status === "provider_poc" &&
        (proSurface.tasks || []).includes("P14-15") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/packaged-image-ocr-evidence-plan.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14PackagedImageOcrEvidence"),
      "The pro.ui_automation_media surface must include the P14-15 evidence and Gradle gate.",
    ),
  ];
  const policyViolations = checks
    .filter((entry) => !entry.ok)
    .map((entry) => ({ id: entry.id, message: entry.message }));
  const blockers = OPEN_BLOCKERS.map((entry) => ({ ...entry }));
  return {
    schema: "autojs6-node-phase14-packaged-image-ocr-evidence-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "partial" : "failed",
    task: "P14-15",
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
      modelLookupPolicyReady: scenarioById(scenarios, "provider_lookup_policy").status === "ready",
      liveMlKitEvidenceImported: scenarioById(scenarios, "live_mlkit_imported").status === "ready",
      packagedFakeSmokeImported: scenarioById(scenarios, "packaged_fake_smoke_imported").status === "ready",
      modelAbsencePolicyReady: scenarioById(scenarios, "packaged_model_absence_policy").status === "ready",
      quotaAndUnknownHandleBoundaryReady: scenarioById(scenarios, "quota_and_unknown_handle_boundary").status === "ready",
      rawObjectBoundaryReady: scenarioById(scenarios, "raw_object_boundary").status === "ready",
      packagedModelArchiveReady: false,
      explicitProviderModeArchiveReady: false,
      quotaLeakStressArchiveReady: false,
      captureDisclosureArchiveReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: blockers.length,
      proSurfacePromoted: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Packaged Image OCR Evidence",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- Packaged model archive ready: \`${report.decision.packagedModelArchiveReady}\``,
    `- Explicit provider-mode archive ready: \`${report.decision.explicitProviderModeArchiveReady}\``,
    `- Quota/leak stress archive ready: \`${report.decision.quotaLeakStressArchiveReady}\``,
    `- Capture/disclosure archive ready: \`${report.decision.captureDisclosureArchiveReady}\``,
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

function checkTokenValue(id, ok) {
  return check(id, ok, `failed contract check: ${id}`);
}

function token(file, value) {
  return check(`token:${file.relative}:${value}`, file.exists && file.text.includes(value), `missing evidence token: ${value}`);
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
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  return expected.every((entry) => actual.includes(entry));
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((entry) => actual.includes(entry));
}

function containsAny(text, values) {
  return values.some((value) => text.includes(value));
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
