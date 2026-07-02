#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-advanced-image-operations";
const TRANSFORM_METHODS = Object.freeze(["saveImage", "clip", "resize", "grayscale", "threshold"]);
const MATCH_COLOR_METHODS = Object.freeze(["findImage", "matchTemplate", "findColor", "findMultiColors"]);
const LIVE_IMPORTED_METHODS = Object.freeze(["readImage", "recycle"]);
const RAW_DENIALS = Object.freeze([
  "Bitmap",
  "Mat",
  "ImageWrapper",
  "OpenCV object",
  "native pointer",
  "filesystem path handle",
  "MediaProjection",
  "ImageReader",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "image_advanced_transform_provider_archive",
    classification: "future_promotion",
    requiredEvidence: "Live Android provider evidence for saveImage, clip, resize, grayscale, and threshold over opaque provider-owned image handles.",
  },
  {
    id: "image_matching_color_provider_archive",
    classification: "future_promotion",
    requiredEvidence: "Live Android provider evidence for findImage, matchTemplate, findColor, and findMultiColors with deterministic JSON point/match snapshots.",
  },
  {
    id: "image_temp_handle_quota_leak_stress_archive",
    classification: "future_promotion",
    requiredEvidence: "Temporary derived-handle quota, repeated recycle, provider destroy, timeout, and leak/stress archive with active image resources returning to zero.",
  },
  {
    id: "image_packaged_scoped_output_archive",
    classification: "future_promotion",
    requiredEvidence: "Packaged scoped input/output path evidence for read, save, transform, matching/color denial or success, relaunch cleanup, and missing-capability denial.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/advanced-image-operations-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_ADVANCED_IMAGE_OPERATIONS.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p13Report: "docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md",
  p13Verifier: "tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js",
  p14MediaProjectionReport: "docs/nodejs/reports/phase14/P14_13_MEDIA_PROJECTION_PROVIDER.md",
  p14MediaProjectionVerifier: "tools/nodejs/phase14/verify-node-phase14-media-projection-provider.js",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  imageExpansionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt",
  concurrencyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConcurrencyLimitsInstrumentationTest.kt",
  ocrTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeOcrBridgeInstrumentationTest.kt",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  mediaProjectionTypes: "docs/nodejs/types/autojs6-node/media_projection.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
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
  console.log("Phase 14 advanced image operations: " + report.decision.status);
  console.log("Phase 14 advanced image operations blockers: " + report.blockers.length);
  console.log("Phase 14 advanced image operations policy violations: " + report.policyViolations.length);
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
    p14MediaProjectionGeneratedReport: "phase14-media-projection-provider.json",
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
  const p14MediaProjectionGenerated = parseJson(files.p14MediaProjectionGeneratedReport.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const p13Decision = p13Generated.decision || {};
  const p14MediaProjectionDecision = p14MediaProjectionGenerated.decision || {};
  const allAdvancedMethods = [...TRANSFORM_METHODS, ...MATCH_COLOR_METHODS];
  const scenarios = [
    scenario("api_contract_and_fake_provider", "Advanced image API shape, fake provider, TypeScript, and Rhino compat facades are covered", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-advanced-image-operations-plan-v1"),
      checkTokenValue("task", plan.task === "P14-14"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("moduleAliases", sameSet(contract.moduleAliases || [], ["image", "images"])),
      checkTokenValue("transformMethods", sameSet(contract.transformMethods || [], TRANSFORM_METHODS)),
      checkTokenValue("matchingAndColorMethods", sameSet(contract.matchingAndColorMethods || [], MATCH_COLOR_METHODS)),
      checkTokenValue("captureDependency", contract.captureDependency === "media_projection"),
      token(files.protocol, "val imageMethods = setOf("),
      ...allAdvancedMethods.map((method) => token(files.protocol, `"${method}",`)),
      token(files.imageExpansionTest, "jsImageExpansionUsesFakeTransportAndBlocksShadowing"),
      token(files.imageExpansionTest, "image.expand.methods=function|function|function|function|function|function|function|function|function|function|function|function|function|undefined"),
      token(files.imageExpansionTest, "image.expand.requests=image:captureScreen|image:readImage|image:saveImage|image:saveImage|image:clip|image:resize|image:grayscale|image:threshold|image:findImage|image:matchTemplate|image:findColor|image:findMultiColors|image:recycle|images:resize"),
      ...allAdvancedMethods.map((method) => token(files.imageTypes, `${method}(`)),
      token(files.imageTypes, "getSize(image: AutoJs6Node.ImageHandleLike): ImageSize;"),
      token(files.typeSmoke, "const matches: readonly image.ImageMatch[] = await image.matchTemplate"),
      token(files.rhinoCompatTypes, "export interface RhinoImagesCompatModule"),
      token(files.nodeBridgeSources, "function __autojs6_limited_image(moduleName)"),
    ]),
    scenario("live_read_recycle_imported", "Scoped readImage/recycle and OCR image-handle evidence are imported from P13-20", [
      checkTokenValue("liveImportedMethods", sameSet(contract.liveImportedMethods || [], LIVE_IMPORTED_METHODS)),
      checkTokenValue("p13_status", p13Generated.status === "partial" || p13Decision.status === "partial"),
      checkTokenValue("p13_image_read_recycle_ready", p13Decision.imageHandleLiveReadRecycleReady === true),
      checkTokenValue("p13_ocr_mlkit_ready", p13Decision.ocrLiveMlKitReady === true),
      token(files.protocol, "private fun dispatchImageRead"),
      token(files.protocol, "private fun dispatchImageRecycle"),
      token(files.protocol, "imageHandles[id] = handle"),
      token(files.providerRegistry, '"liveMethods" to "readImage,recycle"'),
      token(files.providerRegistry, '"cleanupPolicy" to "provider-owned image handles are ResourceRegistry-owned; recycle and provider destroy must return active handle count to zero"'),
      token(files.ocrTest, "kotlinOcrLiveProviderRecognizesMlKitImageHandle"),
      token(files.p13Report, "scoped"),
      token(files.p13Report, "`readImage` plus explicit `recycle`"),
    ]),
    scenario("fail_closed_advanced_live_provider", "Live Android advanced image operations remain fail-closed until provider evidence exists", [
      token(files.providerRegistry, '"blockedMethods" to "captureScreen,saveImage,clip,resize,grayscale,threshold,findImage,matchTemplate,findColor,findMultiColors"'),
      token(files.providerRegistry, '"advancedProcessingPromotion" to "blocked_until_owned_image_processing_provider_for_save_transform_match_color"'),
      token(files.providerRegistry, '"statusReason" to "P13-20 partial gate: scoped readImage/recycle are live for OCR; screenshot and advanced image operations remain fail-closed"'),
      token(files.imageExpansionTest, "androidImageExpansionRejectsUntilProviderIsConnected"),
      token(files.imageExpansionTest, "image.expand.android.denied=captureScreen:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      token(files.p13Report, "advanced image processing providers"),
      token(files.compatibilityProfile, "advanced image operations fail-closed"),
      token(files.p14MediaProjectionReport, "keeps `media_projection` fail-closed"),
      checkTokenValue("p14_media_projection_prereq", p14MediaProjectionDecision.status === "partial"),
      checkTokenValue("p14_media_projection_not_ready", p14MediaProjectionDecision.screenCaptureBridgeProviderReady === false),
    ]),
    scenario("quota_recycle_and_invalid_inputs", "Handle quota, recycle semantics, scoped paths, and invalid inputs are covered before dispatch", [
      checkTokenValue("opaqueHandleOnly", contract.opaqueHandleOnly === true),
      checkTokenValue("derivedHandlesCountAgainstQuota", contract.derivedHandlesCountAgainstQuota === true),
      checkTokenValue("recycleRequiredForDerivedHandles", contract.recycleRequiredForDerivedHandles === true),
      checkTokenValue("scopedOutputPathRequired", contract.scopedOutputPathRequired === true),
      token(files.imageExpansionTest, "jsImageExpansionRejectsInvalidPathsAndHandlesBeforeDispatch"),
      token(files.imageExpansionTest, "image.expand.invalid.requests=0"),
      token(files.imageExpansionTest, "image.expand.recycle.count=1"),
      token(files.imageExpansionTest, "image.expand.pending=0"),
      token(files.concurrencyTest, "jsImageHandleLimitAllowsRecycleThenReuse"),
      token(files.concurrencyTest, "bridge.limit.image=1|image:one.png"),
      token(files.protocol, "imageHandles.size >= bridgeLimits.maxImageHandles"),
      token(files.protocol, "handle.resource.close(\"recycle\")"),
      token(files.securityModel, "Transform operations return new opaque image handles and must count against the per-execution image-handle limit until recycled"),
    ]),
    scenario("raw_object_boundary", "Bitmap, Mat, ImageWrapper, OpenCV, native pointer, and filesystem handles stay outside Node", [
      checkTokenValue("rawDenials", includesAll(contract.rawObjectsDenied || [], RAW_DENIALS)),
      token(files.providerRegistry, '"rawObjectPolicy" to "no raw Bitmap, Mat, ImageWrapper, OpenCV object, native pointer, or filesystem path handle crosses the bridge"'),
      token(files.p13Report, "Raw Bitmap/OpenCV/MediaProjection object parity stays stable-denied"),
      token(files.securityModel, "JavaScript must never receive raw `Bitmap`, `Mat`, `ImageWrapper`, native pointer, or filesystem path handles outside the scoped policy."),
      checkTokenValue("image_types_no_raw_android_classes", !containsAny(files.imageTypes.text, [
        "android.graphics.Bitmap",
        "org.opencv",
        "ImageWrapper",
        "Mat;",
      ])),
      checkTokenValue("node_sources_no_load_native_bitmap", !files.nodeBridgeSources.text.includes("loadNativeBitmap")),
    ]),
    scenario("promotion_archives_open", "Live provider, matching/color, quota/leak, and packaged scoped-output archives remain future work", [
      token(files.planDoc, "Live transform provider archive"),
      token(files.planDoc, "Live matching/color provider archive"),
      token(files.planDoc, "Temporary-handle quota"),
      token(files.planDoc, "Packaged scoped input/output path"),
      token(files.phase14Report, "image_advanced_transform_provider_archive"),
      token(files.phase14Report, "image_matching_color_provider_archive"),
      token(files.phase14Report, "image_temp_handle_quota_leak_stress_archive"),
      token(files.phase14Report, "image_packaged_scoped_output_archive"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-14 gate", [
      token(files.gradle14, "verifyNodePhase14AdvancedImageOperations"),
      token(files.gradle14, "verify-node-phase14-advanced-image-operations.js"),
      token(files.phase14Tasks, "Advanced image operations gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14AdvancedImageOperations"),
      token(files.testing, ":app:verifyNodePhase14AdvancedImageOperations"),
      token(files.ciGates, ":app:verifyNodePhase14AdvancedImageOperations"),
      token(files.surfaceManifestDoc, "P14-14 adds the advanced image operation promotion contract"),
      token(files.surfaceManifest, "docs/nodejs/phase14/advanced-image-operations-plan.json"),
    ]),
  ];
  const checks = [
    check("phase13-screenshot-generated-report-present", files.p13ScreenshotGeneratedReport.exists, "The P13-20 screenshot/image/OCR report must be generated before the P14-14 gate."),
    check("phase14-media-projection-generated-report-present", files.p14MediaProjectionGeneratedReport.exists, "The P14-13 MediaProjection report must be generated before the P14-14 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-advanced-image-operations-plan-v1", "P14-14 advanced image operations plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Advanced Image Operations"), "P14-14 advanced image operations documentation must exist."),
    check(
      "contract-methods-and-denials",
      sameSet(contract.transformMethods || [], TRANSFORM_METHODS) &&
        sameSet(contract.matchingAndColorMethods || [], MATCH_COLOR_METHODS) &&
        includesAll(contract.rawObjectsDenied || [], RAW_DENIALS),
      "P14-14 must reserve transform/matching/color methods and keep raw image/OpenCV objects denied.",
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
          "docs/nodejs/phase14/advanced-image-operations-plan.json",
          "docs/nodejs/phase14/PHASE14_ADVANCED_IMAGE_OPERATIONS.md",
          "docs/nodejs/reports/phase14/P14_14_ADVANCED_IMAGE_OPERATIONS.md",
          "tools/nodejs/phase14/verify-node-phase14-advanced-image-operations.js",
          ":app:verifyNodePhase14AdvancedImageOperations",
          "build/reports/nodejs/phase14-advanced-image-operations.json",
          "build/reports/nodejs/phase14-advanced-image-operations.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-14 plan, docs, verifier, Gradle gate, and output reports.",
    ),
    check(
      "task-plan-keeps-p14-14-open",
      files.phase14Tasks.text.includes("- [ ] P14-14:") &&
        files.phase14Tasks.text.includes("Advanced image operations gate deployed:") &&
        OPEN_BLOCKERS.every((entry) => files.phase14Tasks.text.includes(entry.id)),
      "P14-14 must remain unchecked while the gate records open advanced image operation blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14AdvancedImageOperations") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14AdvancedImageOperations") &&
        files.testing.text.includes(":app:verifyNodePhase14AdvancedImageOperations") &&
        files.ciGates.text.includes(":app:verifyNodePhase14AdvancedImageOperations"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-14 advanced image operations gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-advanced-image-operations-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14AdvancedImageOperations",
    task: "P14-14",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    operationContract: {
      status: plan.status || "missing",
      promotionState: plan.promotionState || "missing",
      moduleAliases: contract.moduleAliases || [],
      transformMethods: contract.transformMethods || [],
      matchingAndColorMethods: contract.matchingAndColorMethods || [],
      liveImportedMethods: contract.liveImportedMethods || [],
      rawObjectsDenied: contract.rawObjectsDenied || [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      apiContractReady: scenarioById(scenarios, "api_contract_and_fake_provider").status === "ready",
      fakeProviderCoverageReady: scenarioById(scenarios, "api_contract_and_fake_provider").status === "ready",
      liveReadRecycleImported: scenarioById(scenarios, "live_read_recycle_imported").status === "ready",
      invalidInputAndQuotaReady: scenarioById(scenarios, "quota_recycle_and_invalid_inputs").status === "ready",
      rawObjectBoundaryReady: scenarioById(scenarios, "raw_object_boundary").status === "ready",
      liveAdvancedProviderReady: false,
      liveMatchingColorProviderReady: false,
      packagedScopedOutputReady: false,
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
    "# Phase 14 Advanced Image Operations",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Operation Contract",
    "",
    `- Modules: ${report.operationContract.moduleAliases.map((entry) => `\`${entry}\``).join(", ")}`,
    `- Transform methods: ${report.operationContract.transformMethods.map((entry) => `\`${entry}\``).join(", ")}`,
    `- Matching/color methods: ${report.operationContract.matchingAndColorMethods.map((entry) => `\`${entry}\``).join(", ")}`,
    `- Imported live methods: ${report.operationContract.liveImportedMethods.map((entry) => `\`${entry}\``).join(", ")}`,
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
      console.log("Usage: node verify-node-phase14-advanced-image-operations.js [--repo-root dir] [--report-dir dir]");
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
