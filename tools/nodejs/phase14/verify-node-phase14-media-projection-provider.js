#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-media-projection-provider";
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
const REQUIRED_SCENARIOS = Object.freeze([
  "request prompt grant and denial",
  "one-frame capture through ScreenCapturer",
  "nextImage returns opaque image handle only",
  "stop releases projection and foreground service",
  "foreground disclosure and stop action",
  "task removal cleanup",
  "package relaunch behavior",
  "crash recovery behavior",
  "raw MediaProjection/ImageReader/Bitmap/display object absence",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "media_projection_android12_prompt_capture_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived Android 12 class screen_capture profile run with prompt grant/denial, one-frame capture, stop, and raw object absence.",
  },
  {
    id: "media_projection_android15_plus_prompt_capture_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived Android 15+ class screen_capture profile run with prompt grant/denial, one-frame capture, stop, and raw object absence.",
  },
  {
    id: "media_projection_foreground_disclosure_stop_action_archive",
    classification: "future_promotion",
    requiredEvidence: "Foreground disclosure ownership and user-visible stop action behavior while capture is active.",
  },
  {
    id: "media_projection_task_removal_relaunch_crash_archive",
    classification: "future_promotion",
    requiredEvidence: "Task removal cleanup, package relaunch behavior, and crash recovery evidence for active or recently stopped capture sessions.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  providerPlanJson: "docs/nodejs/phase14/media-projection-provider-plan.json",
  providerPlanDoc: "docs/nodejs/phase14/PHASE14_MEDIA_PROJECTION_PROVIDER.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p13Report: "docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md",
  p13Verifier: "tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  bridgeSmokeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt",
  screenCaptureSmoke: "app/src/androidTest/java/org/autojs/autojs/engine/NodeScreenCaptureRealDeviceSmokeInstrumentationTest.kt",
  mediaProjectionTypes: "docs/nodejs/types/autojs6-node/media_projection.d.ts",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  deviceMatrix: "docs/nodejs/DEVICE_MATRIX.md",
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
  console.log("Phase 14 MediaProjection provider: " + report.decision.status);
  console.log("Phase 14 MediaProjection provider blockers: " + report.blockers.length);
  console.log("Phase 14 MediaProjection provider policy violations: " + report.policyViolations.length);
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
    p14SelectorMatrixGeneratedReport: "phase14-accessibility-selector-action-matrix.json",
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
  const contracts = parseJson(files.promotionContractsJson.text);
  const plan = parseJson(files.providerPlanJson.text);
  const providerContract = plan.providerContract || {};
  const archiveRequirements = plan.manualArchiveRequirements || {};
  const p13Generated = parseJson(files.p13ScreenshotGeneratedReport.text);
  const p14SelectorMatrixGenerated = parseJson(files.p14SelectorMatrixGeneratedReport.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const sharedMatrix = contracts.deviceEvidenceMatrix || {};
  const p13Decision = p13Generated.decision || {};
  const p14SelectorDecision = p14SelectorMatrixGenerated.decision || {};
  const scenarios = [
    scenario("provider_contract", "MediaProjection provider contract is reserved but not promoted", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-media-projection-provider-plan-v1"),
      checkTokenValue("task", plan.task === "P14-13"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("module", providerContract.module === "media_projection"),
      checkTokenValue("capability", providerContract.capability === "screen_capture"),
      checkTokenValue("methods", sameSet(providerContract.methods || [], REQUIRED_METHODS)),
      checkTokenValue("activityOwnedPromptRequired", providerContract.activityOwnedPromptRequired === true),
      checkTokenValue("mustNotPromptFromEmbeddedService", providerContract.mustNotPromptFromEmbeddedService === true),
      checkTokenValue("opaqueHandleOnly", providerContract.opaqueHandleOnly === true),
      token(files.protocol, 'val mediaProjectionMethods = setOf("requestScreenCapture", "nextImage", "stop")'),
      token(files.protocol, "private fun dispatchMediaProjection"),
      token(files.protocol, 'requirement = "a MediaProjection capability provider"'),
      token(files.providerRegistry, "MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE"),
      token(files.permissionManifest, 'const val SCREEN_CAPTURE = "screen_capture"'),
      token(files.mediaProjectionTypes, "requestScreenCapture(options?: ScreenCaptureOptions): Promise<ScreenCapturer>;"),
      token(files.mediaProjectionTypes, "nextImage(options?: AutoJs6Node.BridgeCallOptions): Promise<AutoJs6Node.ImageHandle>;"),
      token(files.mediaProjectionTypes, "stop(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;"),
    ]),
    scenario("activity_owned_smoke_import", "Optional screen_capture smoke validates the existing Activity-owned prompt/capture path", [
      token(files.screenCaptureSmoke, "capturesOneFrameAfterUserGrantsScreenCapturePermission"),
      token(files.screenCaptureSmoke, "launchTargetActivity"),
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
      token(files.providerRegistry, '"optionalSmoke" to "NodeScreenCaptureRealDeviceSmokeInstrumentationTest"'),
      token(files.providerRegistry, '"permissionPromptPolicy" to "Activity-owned ScreenCaptureRequester smoke exists; Node bridge provider must not prompt directly until promoted"'),
      token(files.testing, "Optional real-device `screen_capture` smoke"),
      token(files.deviceMatrix, "profile: `screen_capture`"),
    ]),
    scenario("raw_object_boundary", "Raw MediaProjection, frame, bitmap, and display objects stay outside Node", [
      checkTokenValue("rawDenials", includesAll(providerContract.rawObjectsDenied || [], REQUIRED_RAW_DENIALS)),
      token(files.p13Report, "Raw boundary: raw `MediaProjection`, `ImageReader`, `ScreenCapturer`"),
      token(files.p13Report, "Raw Bitmap/OpenCV/MediaProjection object parity stays stable-denied"),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw MediaProjection, ImageReader, ScreenCapturer, Image, Bitmap, or ImageWrapper crosses the bridge"'),
      token(files.securityModel, "expose a raw `MediaProjection`"),
      token(files.securityModel, "JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy."),
      checkTokenValue("media_projection_types_no_raw_android_classes", !containsAny(files.mediaProjectionTypes.text, [
        "android.media.projection.MediaProjection",
        "ImageReader",
        "Bitmap",
        "VirtualDisplay",
        "Surface",
        "Display",
      ])),
      checkTokenValue("protocol_no_raw_media_projection_import", !files.protocol.text.includes("android.media.projection.MediaProjection")),
      checkTokenValue("protocol_no_image_reader_import", !files.protocol.text.includes("ImageReader")),
    ]),
    scenario("device_archives_open", "Required Android 12/15+, disclosure, stop action, and lifecycle archives remain future work", [
      checkTokenValue("requiredDeviceClasses", sameSet(archiveRequirements.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("sharedDeviceClasses", sameSet(sharedMatrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("requiredScenarios", includesAll(archiveRequirements.requiredScenarios || [], REQUIRED_SCENARIOS)),
      token(files.providerPlanDoc, "Android 12 class `screen_capture` run"),
      token(files.providerPlanDoc, "Android 15+ class `screen_capture` run"),
      token(files.providerPlanDoc, "Foreground disclosure and stop action"),
      token(files.providerPlanDoc, "Task removal cleanup, relaunch behavior, and crash recovery"),
      token(files.phase14Report, "media_projection_android12_prompt_capture_archive"),
      token(files.phase14Report, "media_projection_android15_plus_prompt_capture_archive"),
      token(files.phase14Report, "media_projection_foreground_disclosure_stop_action_archive"),
      token(files.phase14Report, "media_projection_task_removal_relaunch_crash_archive"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("phase13_imports", "P13-20 screenshot/image/OCR evidence is imported without promoting MediaProjection", [
      checkTokenValue("p13_status", p13Generated.status === "partial" || p13Decision.status === "partial"),
      checkTokenValue("p13_screen_capture_provider_not_ready", p13Decision.screenCaptureBridgeProviderReady === false),
      checkTokenValue("p13_image_read_recycle_ready", p13Decision.imageHandleLiveReadRecycleReady === true),
      checkTokenValue("p13_ocr_mlkit_ready", p13Decision.ocrLiveMlKitReady === true),
      checkTokenValue("p13_policy_violations", Array.isArray(p13Generated.policyViolations) && p13Generated.policyViolations.length === 0),
      token(files.p13Report, "Status: partial"),
      token(files.p13Report, "Activity-owned MediaProjection provider"),
      token(files.p13Verifier, "screen_capture_boundary"),
      token(files.p13Verifier, "screenCaptureBridgeProviderReady"),
      token(files.bridgeSmokeTest, "mediaProjectionAndImageModulesRejectWithStableCodeUntilProvidersAreConnected"),
      token(files.nodeBridgeSources, "function __autojs6_limited_media_projection()"),
      checkTokenValue("p14_selector_matrix_prereq", p14SelectorDecision.status === "partial"),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-13 gate", [
      token(files.gradle14, "verifyNodePhase14MediaProjectionProvider"),
      token(files.gradle14, "verify-node-phase14-media-projection-provider.js"),
      token(files.phase14Tasks, "MediaProjection provider gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14MediaProjectionProvider"),
      token(files.testing, ":app:verifyNodePhase14MediaProjectionProvider"),
      token(files.ciGates, ":app:verifyNodePhase14MediaProjectionProvider"),
      token(files.surfaceManifestDoc, "P14-13 adds the Activity-owned MediaProjection provider contract"),
      token(files.surfaceManifest, "docs/nodejs/phase14/media-projection-provider-plan.json"),
    ]),
  ];
  const checks = [
    check("phase13-screenshot-generated-report-present", files.p13ScreenshotGeneratedReport.exists, "The P13-20 screenshot/image/OCR report must be generated before the P14-13 gate."),
    check("phase14-selector-matrix-generated-report-present", files.p14SelectorMatrixGeneratedReport.exists, "The P14-12 accessibility selector/action report must be generated before the P14-13 gate."),
    check("provider-plan-json-present", files.providerPlanJson.exists && plan.schema === "autojs6-node-phase14-media-projection-provider-plan-v1", "P14-13 provider plan JSON must exist."),
    check("provider-plan-doc-present", files.providerPlanDoc.exists && files.providerPlanDoc.text.includes("Phase 14 MediaProjection Provider"), "P14-13 provider plan documentation must exist."),
    check(
      "contract-methods-and-denials",
      sameSet(providerContract.methods || [], REQUIRED_METHODS) &&
        providerContract.activityOwnedPromptRequired === true &&
        providerContract.mustNotPromptFromEmbeddedService === true &&
        includesAll(providerContract.rawObjectsDenied || [], REQUIRED_RAW_DENIALS),
      "P14-13 must reserve requestScreenCapture/nextImage/stop and keep raw Android objects denied.",
    ),
    check(
      "manual-archive-requirements",
      sameSet(archiveRequirements.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES) &&
        includesAll(archiveRequirements.requiredScenarios || [], REQUIRED_SCENARIOS),
      "P14-13 must declare Android 12/15+ screen_capture archive requirements.",
    ),
    check(
      "surface-provider-poc-not-promoted",
      proSurface &&
        proSurface.status === "provider_poc" &&
        !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must stay provider_poc, not opt-in or default promoted.",
    ),
    check(
      "surface-evidence",
      proSurface &&
        includesAll(proSurface.evidence || [], [
          "docs/nodejs/phase14/media-projection-provider-plan.json",
          "docs/nodejs/phase14/PHASE14_MEDIA_PROJECTION_PROVIDER.md",
          "docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md",
          "tools/nodejs/phase14/verify-node-phase14-media-projection-provider.js",
          ":app:verifyNodePhase14MediaProjectionProvider",
          "build/reports/nodejs/phase14-media-projection-provider.json",
          "build/reports/nodejs/phase14-media-projection-provider.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-13 plan, docs, verifier, Gradle gate, and output reports.",
    ),
    check(
      "task-plan-keeps-p14-13-open",
      files.phase14Tasks.text.includes("- [ ] P14-13:") &&
        files.phase14Tasks.text.includes("MediaProjection provider gate deployed:") &&
        OPEN_BLOCKERS.every((entry) => files.phase14Tasks.text.includes(entry.id)),
      "P14-13 must remain unchecked while the gate records open MediaProjection promotion blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14MediaProjectionProvider") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14MediaProjectionProvider") &&
        files.testing.text.includes(":app:verifyNodePhase14MediaProjectionProvider") &&
        files.ciGates.text.includes(":app:verifyNodePhase14MediaProjectionProvider"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-13 MediaProjection provider gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-media-projection-provider-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14MediaProjectionProvider",
    task: "P14-13",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    providerContract: {
      status: plan.status || "missing",
      promotionState: plan.promotionState || "missing",
      module: providerContract.module || "missing",
      capability: providerContract.capability || "missing",
      methods: providerContract.methods || [],
      activityOwnedPromptRequired: providerContract.activityOwnedPromptRequired === true,
      rawObjectsDenied: providerContract.rawObjectsDenied || [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      providerContractReady: scenarioById(scenarios, "provider_contract").status === "ready",
      activityOwnedSmokeImported: scenarioById(scenarios, "activity_owned_smoke_import").status === "ready",
      rawObjectBoundaryReady: scenarioById(scenarios, "raw_object_boundary").status === "ready",
      requiredDeviceArchivesReady: false,
      foregroundDisclosureReady: false,
      lifecycleRecoveryReady: false,
      screenCaptureBridgeProviderReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: OPEN_BLOCKERS.length,
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

function checkTokenValue(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function sameSet(values, requiredValues) {
  return Array.isArray(values) &&
    values.length === requiredValues.length &&
    requiredValues.every((value) => values.includes(value));
}

function containsAny(text, needles) {
  return needles.some((needle) => String(text).includes(needle));
}

function parseJson(text) {
  try {
    return JSON.parse(stripBom(text || "{}"));
  } catch (_) {
    return {};
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 MediaProjection Provider",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Provider Contract",
    "",
    `- Module: \`${report.providerContract.module}\``,
    `- Capability: \`${report.providerContract.capability}\``,
    `- Methods: ${report.providerContract.methods.map((entry) => `\`${entry}\``).join(", ")}`,
    `- Activity-owned prompt required: ${report.providerContract.activityOwnedPromptRequired}`,
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

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase14-media-projection-provider.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

main();
