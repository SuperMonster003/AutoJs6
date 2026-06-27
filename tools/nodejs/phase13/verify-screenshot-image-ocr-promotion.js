#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-screenshot-image-ocr-promotion";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  bridgeSmokeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt",
  imageExpansionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt",
  ocrTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt",
  screenCaptureSmoke: "app/src/androidTest/java/org/autojs/autojs/engine/NodeScreenCaptureRealDeviceSmokeInstrumentationTest.kt",
  concurrencyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt",
  mediaProjectionTypes: "docs/nodejs/types/autojs6-node/media_projection.d.ts",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  ocrTypes: "docs/nodejs/types/autojs6-node/ocr.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  phase11Tasks: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  phase11CleanupSecurity: "docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  deviceMatrix: "docs/nodejs/DEVICE_MATRIX.md",
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
  console.log("Phase 13 screenshot/image/OCR promotion: " + report.decision.status);
  console.log("Phase 13 screenshot/image/OCR promotion blockers: " + report.blockers.length);
  console.log("Phase 13 screenshot/image/OCR promotion policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.ui_automation_media");
  const scenarios = [
    scenario("screen_capture_boundary", "MediaProjection API shape is reserved while live bridge prompting remains fail-closed", [
      token(files.protocol, 'val mediaProjectionMethods = setOf("requestScreenCapture", "nextImage", "stop")'),
      token(files.protocol, "private fun dispatchMediaProjection"),
      token(files.protocol, 'requirement = "a MediaProjection capability provider"'),
      token(files.providerRegistry, "MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE"),
      token(files.providerRegistry, '"phase13Promotion" to "P13-20 partial screenshot/image/OCR promotion evidence gate"'),
      token(files.providerRegistry, '"optionalSmoke" to "NodeScreenCaptureRealDeviceSmokeInstrumentationTest"'),
      token(files.providerRegistry, '"permissionPromptPolicy" to "Activity-owned ScreenCaptureRequester smoke exists; Node bridge provider must not prompt directly until promoted"'),
      token(files.nodeBridgeSources, "function __autojs6_limited_media_projection()"),
      token(files.bridgeSmokeTest, "mediaProjectionAndImageModulesRejectWithStableCodeUntilProvidersAreConnected"),
      token(files.screenCaptureSmoke, "capturesOneFrameAfterUserGrantsScreenCapturePermission"),
      token(files.mediaProjectionTypes, 'declare module "media_projection"'),
      token(files.typeSmoke, "const capturer: mediaProjection.ScreenCapturer = await mediaProjection.requestScreenCapture"),
      token(files.deviceMatrix, "profile: `screen_capture`"),
    ], [
      "Implement an Activity-owned MediaProjection provider for Node bridge requestScreenCapture/nextImage/stop.",
      "Add packaged grant/denial behavior, foreground disclosure, stop action, task-removal cleanup, and relaunch/crash evidence.",
    ]),
    scenario("image_handle_surface", "Image/images expose typed opaque handles, fake full API coverage, and live scoped read/recycle only", [
      token(files.protocol, "val imageMethods = setOf("),
      token(files.protocol, '"captureScreen",'),
      token(files.protocol, '"findMultiColors",'),
      token(files.protocol, "private fun dispatchImageRead"),
      token(files.protocol, "private fun dispatchImageRecycle"),
      token(files.providerRegistry, '"liveMethods" to "readImage,recycle"'),
      token(files.providerRegistry, '"blockedMethods" to "captureScreen,saveImage,clip,resize,grayscale,threshold,findImage,matchTemplate,findColor,findMultiColors"'),
      token(files.providerRegistry, '"advancedProcessingPromotion" to "blocked_until_owned_image_processing_provider_for_save_transform_match_color"'),
      token(files.nodeBridgeSources, "function __autojs6_limited_image(moduleName)"),
      token(files.nodeBridgeSources, "function __autojs6_track_image_creation(moduleName, methodName, factory)"),
      token(files.imageExpansionTest, "jsImageExpansionUsesFakeTransportAndBlocksShadowing"),
      token(files.imageExpansionTest, "image.expand.recycle.count=1"),
      token(files.imageExpansionTest, "androidImageExpansionRejectsUntilProviderIsConnected"),
      token(files.concurrencyTest, "jsImageHandleLimitAllowsRecycleThenReuse"),
      token(files.imageTypes, 'declare module "image"'),
      token(files.typeSmoke, "const matches: readonly image.ImageMatch[] = await image.matchTemplate"),
    ], [
      "Promote real save/clip/resize/grayscale/threshold/findImage/matchTemplate/findColor/findMultiColors providers.",
      "Add packaged scoped asset, output path, quota, and leak/stress evidence for image processing.",
    ]),
    scenario("ocr_live_mlkit_provider", "OCR runs through provider-owned image handles and ML Kit live evidence", [
      token(files.protocol, 'val ocrMethods = setOf("recognize", "recognizeText", "detectTextBounds")'),
      token(files.protocol, "private fun dispatchOcr"),
      token(files.providerRegistry, '"defaultEngine" to "mlkit-chinese"'),
      token(files.providerRegistry, '"liveMethods" to "recognize,recognizeText,detectTextBounds"'),
      token(files.providerRegistry, '"fallbackPromotion" to "blocked_until_Paddle_Rapid_plugin_model_selection_and_packaged_asset_policy_evidence"'),
      token(files.providerRegistry, '"cleanupPolicy" to "OCR consumes provider-owned image handles; unknown or recycled handles reject and active OCR/image resources return to zero"'),
      token(files.nodeBridgeSources, "function __autojs6_limited_ocr()"),
      token(files.ocrTest, "kotlinOcrLiveProviderRecognizesMlKitImageHandle"),
      token(files.ocrTest, "embedded_script.ocr_live_dispatch_count"),
      token(files.ocrTest, "androidOcrRejectsUnknownImageHandleWithoutLeakingResources"),
      token(files.ocrTypes, 'declare module "ocr"'),
      token(files.typeSmoke, "const ocrResults: readonly ocr.OcrResult[] = await ocr.recognize"),
    ], [
      "Add capture/path/temp-handle positive and denial evidence for packaged OCR workflows.",
    ]),
    scenario("rhino_compat_media_image_ocr", "Explicit Rhino compat wrappers route through the same controlled providers", [
      token(files.nodeBridgeSources, "function __autojs6_rhino_compat_request_screen_capture(options)"),
      token(files.nodeBridgeSources, "function __autojs6_rhino_compat_capture_screen(pathOrOptions, maybeOptions)"),
      token(files.nodeBridgeSources, "function __autojs6_rhino_compat_ocr()"),
      token(files.rhinoCompatTypes, "export interface RhinoImagesCompatModule"),
      token(files.rhinoCompatTypes, "export interface RhinoOcrCompatFunction"),
      token(files.typeSmoke, "const compatTemplate = await rhinoCompat.images.read"),
      token(files.typeSmoke, "const compatScreenRequested: boolean = await rhinoCompat.requestScreenCapture(true);"),
      token(files.typeSmoke, 'const compatOcrMode: "mlkit" = rhinoCompat.ocr.tap("mlkit");'),
      token(files.phase11Tasks, "S11-18"),
      token(files.phase11Tasks, "S11-19"),
      token(files.phase11Tasks, "S11-20"),
      token(files.phase11CleanupSecurity, "`image` | `verified_zero_after_recycle_or_stop`"),
    ], [
      "Add packaged compat-image and compat-OCR positive/negative smoke after live providers are promoted.",
    ]),
    scenario("ocr_fallback_policy_boundary", "Paddle/Rapid/plugin OCR provider selection stays stable-denied for v1.1", [
      token(files.p13Report, "Paddle/Rapid/plugin model"),
      token(files.p13Report, "stay stable-denied"),
      token(files.p13Report, "separate provider-selector gate"),
      token(files.phase13Tasks, "Paddle/Rapid/plugin OCR provider selection"),
      token(files.providerRegistry, '"fallbackPromotion" to "blocked_until_Paddle_Rapid_plugin_model_selection_and_packaged_asset_policy_evidence"'),
      token(files.rhinoCompatTypes, 'tap(mode: "mlkit"): "mlkit";'),
      token(files.typeSmoke, 'const compatOcrMode: "mlkit" = rhinoCompat.ocr.tap("mlkit");'),
    ]),
    scenario("security_packaged_raw_boundary", "Raw media/image/OCR objects remain blocked and packaged promotion is explicit", [
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw MediaProjection, ImageReader, ScreenCapturer, Image, Bitmap, or ImageWrapper crosses the bridge"'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw Bitmap, Mat, ImageWrapper, OpenCV object, native pointer, or filesystem path handle crosses the bridge"'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw TextRecognizer, OCR engine, ML Kit Text, Paddle/Rapid model, Bitmap, Mat, ImageWrapper, or Java object crosses the bridge"'),
      token(files.permissionManifest, 'const val SCREEN_CAPTURE = "screen_capture"'),
      token(files.permissionManifest, 'const val IMAGE = "image"'),
      token(files.permissionManifest, 'const val OCR = "ocr"'),
      token(files.imageExpansionTest, "image.expand.invalid.requests=0"),
      token(files.ocrTest, "ocr.manifest.denied="),
      token(files.securityModel, "JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy."),
    ], [
      "Add packaged capability metadata and disclosure denial evidence for screen capture and OCR/image access.",
    ]),
    scenario("raw_object_profile_boundary", "Raw Bitmap/OpenCV/MediaProjection object parity stays stable-denied for v1.1", [
      token(files.p13Report, "Raw Bitmap/OpenCV/MediaProjection object parity stays stable-denied"),
      token(files.p13Report, "provider-owned opaque handles"),
      token(files.phase13Tasks, "raw Bitmap/OpenCV/"),
      token(files.phase13Tasks, "MediaProjection object parity stay stable-denied for P13-20 v1.1"),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw MediaProjection, ImageReader, ScreenCapturer, Image, Bitmap, or ImageWrapper crosses the bridge"'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw Bitmap, Mat, ImageWrapper, OpenCV object, native pointer, or filesystem path handle crosses the bridge"'),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw TextRecognizer, OCR engine, ML Kit Text, Paddle/Rapid model, Bitmap, Mat, ImageWrapper, or Java object crosses the bridge"'),
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-20 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-20 partial screenshot/image/OCR promotion gate"),
      token(files.gradleGate, "verifyNodePhase13ScreenshotImageOcrPromotion"),
      token(files.testingDoc, "verifyNodePhase13ScreenshotImageOcrPromotion"),
      token(files.ciGatesDoc, "verifyNodePhase13ScreenshotImageOcrPromotion"),
      token(files.compatibilityProfile, "P13-20 records the current screenshot/image/OCR promotion state"),
      token(files.securityModel, "P13-20 records screenshot/image/OCR promotion as partial"),
      token(files.proComparison, "P13-20 records screenshot/image/OCR as partial"),
      token(files.proAutoxNotes, "P13-20 records screenshot/image/OCR as partial"),
      token(files.capabilityMatrix, "P13-20 screenshot/image/OCR promotion"),
      token(files.typesReadme, "P13-20 records the partial screenshot/image/OCR promotion gate"),
      token(files.deviceMatrix, "P13-20 keeps screen_capture smoke manual"),
      token(files.surfaceManifestDoc, "phase13-screenshot-image-ocr-promotion"),
    ], [
      "Keep P13-20 checklist open until MediaProjection, advanced image, packaged OCR/image smoke, and packaged disclosure evidence land.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-20"), "P13-20 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-20 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.ui_automation_media` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.ui_automation_media` must show partial/poc state, not planned or promoted.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must not be promoted by the P13-20 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-screenshot-image-ocr-promotion")),
      "`pro.ui_automation_media` surface must reference P13-20 evidence.",
    ),
    check(
      "providers-not-overclaimed",
      files.providerRegistry.text.includes("MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE") &&
        files.providerRegistry.text.includes("IMAGE_PROVIDER -> NodeBridgeProviderStatus.DEGRADED") &&
        files.providerRegistry.text.includes("blocked_until_owned_image_processing_provider_for_save_transform_match_color"),
      "`media_projection` must remain unavailable and `image` degraded until provider ownership evidence lands.",
    ),
    check(
      "raw-objects-not-promoted",
      !files.nodeBridgeSources.text.includes("loadNativeBitmap") &&
        !files.protocol.text.includes("rawBitmap") &&
        !files.protocol.text.includes("rawMediaProjection"),
      "P13-20 must not expose raw Bitmap/OpenCV/MediaProjection objects.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-screenshot-image-ocr-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ScreenshotImageOcrPromotion",
    task: "P13-20",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.ui_automation_media",
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
      screenCaptureBridgeProviderReady: false,
      imageHandleLiveReadRecycleReady: scenarioById(scenarios, "image_handle_surface").status !== "missing_evidence",
      ocrLiveMlKitReady: scenarioById(scenarios, "ocr_live_mlkit_provider").status !== "missing_evidence",
      rhinoCompatImageOcrReady: scenarioById(scenarios, "rhino_compat_media_image_ocr").status !== "missing_evidence",
      rawObjectProfileReady: false,
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
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(stripBom(text));
    return (manifest.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Screenshot/Image/OCR Promotion",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
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
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|");
}

main();
