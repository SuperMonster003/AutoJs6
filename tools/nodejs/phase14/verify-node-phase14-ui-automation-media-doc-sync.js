#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-ui-automation-media-doc-sync";
const SOURCE_REPORTS = Object.freeze([
  ["p14LiveUi", "phase14-live-ui-provider-packaged.json"],
  ["p14OverlayProvider", "phase14-overlay-provider.json"],
  ["p14OverlayMatrix", "phase14-overlay-permission-oem-matrix.json"],
  ["p14PackagedOverlay", "phase14-packaged-overlay-positive-smoke.json"],
  ["p14AccessibilityLifecycle", "phase14-accessibility-lifecycle.json"],
  ["p14AccessibilityMatrix", "phase14-accessibility-selector-action-matrix.json"],
  ["p14MediaProjection", "phase14-media-projection-provider.json"],
  ["p14AdvancedImage", "phase14-advanced-image-operations.json"],
  ["p14PackagedImageOcr", "phase14-packaged-image-ocr-evidence.json"],
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_16_UI_AUTOMATION_MEDIA_DOC_SYNC.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/ui-automation-media-doc-sync.json",
  planDoc: "docs/nodejs/phase14/PHASE14_UI_AUTOMATION_MEDIA_DOC_SYNC.md",
  releaseMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_2_UI_AUTOMATION_MEDIA_MATRIX.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  proExamples: "sample/nodejs/pro-parity-suite/examples.json",
  proReadme: "sample/nodejs/pro-parity-suite/README.md",
  tsSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  uiTypes: "docs/nodejs/types/autojs6-node/ui.d.ts",
  overlayTypes: "docs/nodejs/types/autojs6-node/ui_overlay.d.ts",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  mediaProjectionTypes: "docs/nodejs/types/autojs6-node/media_projection.d.ts",
  imageTypes: "docs/nodejs/types/autojs6-node/image.d.ts",
  imagesTypes: "docs/nodejs/types/autojs6-node/images.d.ts",
  ocrTypes: "docs/nodejs/types/autojs6-node/ocr.d.ts",
  mediaTypes: "docs/nodejs/types/autojs6-node/media.d.ts",
  mediainfoTypes: "docs/nodejs/types/autojs6-node/mediainfo.d.ts",
  recorderTypes: "docs/nodejs/types/autojs6-node/recorder.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
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
  console.log("Phase 14 UI/automation/media doc sync: " + report.decision.status);
  console.log("Phase 14 UI/automation/media doc sync blockers: " + report.blockers.length);
  console.log("Phase 14 UI/automation/media doc sync policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([key, basename]) => {
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
  const proExamples = parseJson(files.proExamples.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const generatedReports = SOURCE_REPORTS.map(([key]) => {
    const parsed = parseJson(files[key].text);
    return {
      id: key,
      exists: files[key].exists,
      status: parsed.status || (parsed.decision || {}).status || "missing",
    };
  });
  const smokeExamples = ((proExamples.smokeSubset || {}).examples || []).slice().sort();
  const exampleIds = (proExamples.examples || []).map((entry) => entry.id);
  const scenarios = [
    scenario("source_gate_imports", "P14-07 through P14-15 generated reports are imported as partial/provider evidence", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-ui-automation-media-doc-sync-v1"),
      checkTokenValue("task", plan.task === "P14-16"),
      checkTokenValue("status", plan.status === "ready"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc_not_promoted"),
      checkTokenValue("sourceGates", sameSet(plan.sourceGates || [], ["P14-07", "P14-08", "P14-09", "P14-10", "P14-11", "P14-12", "P14-13", "P14-14", "P14-15"])),
      ...generatedReports.map((entry) => check(`generated:${entry.id}`, entry.exists && entry.status === "partial", `${entry.id} generated report must exist with partial status`)),
    ]),
    scenario("declaration_sync", "TypeScript declaration docs and smoke cover UI, automation, media, image, OCR, and compat modules", [
      token(files.typeReadme, "P14-16 docs sync"),
      token(files.typeReadme, "`provider_poc_not_promoted`"),
      token(files.uiTypes, "declare module \"ui\""),
      token(files.overlayTypes, "declare module \"ui.overlay\""),
      token(files.accessibilityTypes, "declare module \"accessibility\""),
      token(files.mediaProjectionTypes, "declare module \"media_projection\""),
      token(files.imageTypes, "declare module \"image\""),
      token(files.imagesTypes, "declare module \"images\""),
      token(files.ocrTypes, "declare module \"ocr\""),
      token(files.mediaTypes, "declare module \"media\""),
      token(files.mediainfoTypes, "declare module \"mediainfo\""),
      token(files.recorderTypes, "declare module \"recorder\""),
      token(files.rhinoCompatTypes, "readonly images: RhinoImagesCompatModule;"),
      token(files.rhinoCompatTypes, "readonly recorder: recorderModule.RecorderModule;"),
      token(files.tsSmoke, "import overlay = require(\"ui.overlay\");"),
      token(files.tsSmoke, "import mediaProjection = require(\"media_projection\");"),
      token(files.tsSmoke, "import recorder = require(\"recorder\");"),
    ]),
    scenario("example_policy_sync", "Pro examples keep only promoted smoke paths runnable and high-risk snippets static or denial-oriented", [
      checkTokenValue("smokeSubset", sameSet(smokeExamples, ["tasks-work-manager", "ui-layout"])),
      checkTokenValue("highRiskExamplesPresent", includesAll(exampleIds, ["overlay-floaty", "media-recorder", "screenshot-ocr", "accessibility-selector"])),
      token(files.examplesDoc, "P14-16 docs sync"),
      token(files.examplesDoc, "overlay, media/recorder, screenshot/OCR, and accessibility"),
      token(files.examplesDoc, "static, guarded, or denial-oriented"),
      token(files.proReadme, "global install, overlay, notifications/power, media/recorder, screenshot/OCR,"),
      token(files.proExamples, "Runs already-covered Activity-owned UI layout and WorkManager one-shot scheduling/cancel paths without enabling Java, Rhino, overlay, screenshot, OCR, or accessibility authority."),
      token(files.proExamples, "\"id\": \"screenshot-ocr\""),
      token(files.proExamples, "\"packagedBehavior\": \"packaged capture/OCR requires disclosure manifest and permission review\""),
    ]),
    scenario("release_matrix_sync", "v1.2 release matrix records provider POC state without promotion", [
      token(files.releaseMatrix, "Node Profile v1.2 UI Automation Media Matrix"),
      token(files.releaseMatrix, "P14-16 docs sync"),
      token(files.releaseMatrix, "`provider_poc_not_promoted`"),
      token(files.releaseMatrix, "static-or-denied"),
      token(files.releaseMatrix, "P14-07"),
      token(files.releaseMatrix, "P14-15"),
      token(files.releaseMatrix, "P13-26, P14-24, P14-25"),
    ]),
    scenario("compat_security_error_sync", "Compatibility profile, security model, and error catalog expose the same denial boundary", [
      token(files.compatibilityProfile, "P14-16 docs sync"),
      token(files.compatibilityProfile, "`provider_poc_not_promoted`"),
      token(files.securityModel, "P14-16 docs sync"),
      token(files.securityModel, "`provider_poc_not_promoted`"),
      token(files.errorCatalog, "P14-16 UI/automation/media denial sync"),
      token(files.errorCatalog, "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      token(files.errorCatalog, "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"),
    ]),
    scenario("surface_and_gradle_wiring", "Phase 14 manifest, docs, and Gradle expose the P14-16 gate", [
      checkTokenValue("surfaceEvidence", proSurface &&
        (proSurface.tasks || []).includes("P14-16") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/ui-automation-media-doc-sync.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14UiAutomationMediaDocSync")),
      token(files.surfaceManifestDoc, "P14-16 adds the docs/examples/declarations sync gate"),
      token(files.phase14Tasks, "- [x] P14-16: Sync UI/automation/media docs, examples, and declarations."),
      token(files.phase14Tasks, "UI/automation/media docs sync gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14UiAutomationMediaDocSync"),
      token(files.testing, ":app:verifyNodePhase14UiAutomationMediaDocSync"),
      token(files.ciGates, ":app:verifyNodePhase14UiAutomationMediaDocSync"),
      token(files.gradle14, "verifyNodePhase14UiAutomationMediaDocSync"),
      token(files.gradle14, "verify-node-phase14-ui-automation-media-doc-sync.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-ui-automation-media-doc-sync-v1", "P14-16 doc sync plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 UI Automation Media Doc Sync"), "P14-16 doc sync documentation must exist."),
    check("release-matrix-present", files.releaseMatrix.exists && files.releaseMatrix.text.includes("Node Profile v1.2 UI Automation Media Matrix"), "P14-16 v1.2 release matrix must exist."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-16 sync scenarios must be ready."),
  ];
  const policyViolations = checks
    .filter((entry) => !entry.ok)
    .map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-ui-automation-media-doc-sync-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-16",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      promotionState: "provider_poc_not_promoted",
      p14TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists && entry.status === "partial"),
      declarationsSynced: scenarioById(scenarios, "declaration_sync").status === "ready",
      examplesSynced: scenarioById(scenarios, "example_policy_sync").status === "ready",
      releaseMatrixSynced: scenarioById(scenarios, "release_matrix_sync").status === "ready",
      compatibilitySecurityErrorDocsSynced: scenarioById(scenarios, "compat_security_error_sync").status === "ready",
      runtimeAuthorityChanged: false,
      proSurfacePromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: 0,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 UI Automation Media Doc Sync",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- P14 task complete: \`${report.decision.p14TaskComplete}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
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
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return {
    id,
    description,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    missing,
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
