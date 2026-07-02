#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-ui-automation-media-doc-sync";
const REQUIRED_SOURCE_GATES = Object.freeze([
  "P15-04",
  "P15-05",
  "P15-06",
  "P15-07",
  "P15-08",
  "P15-09",
  "P15-10",
]);
const SOURCE_REPORTS = Object.freeze([
  ["p15LiveUi", "phase15-packaged-live-ui-promotion.json", "P15-04"],
  ["p15OverlayProvider", "phase15-overlay-provider-promotion.json", "P15-05"],
  ["p15OverlayMatrix", "phase15-overlay-oem-matrix.json", "P15-06"],
  ["p15PackagedOverlay", "phase15-packaged-overlay-smoke.json", "P15-07"],
  ["p15Accessibility", "phase15-accessibility-promotion.json", "P15-08"],
  ["p15MediaProjection", "phase15-media-projection-promotion.json", "P15-09"],
  ["p15ImageOcr", "phase15-image-ocr-promotion.json", "P15-10"],
]);

const FILES = {
  planJson: "docs/nodejs/phase15/ui-automation-media-doc-sync.json",
  planDoc: "docs/nodejs/phase15/PHASE15_UI_AUTOMATION_MEDIA_DOC_SYNC.md",
  taskReport: "docs/nodejs/reports/phase15/P15_11_UI_AUTOMATION_MEDIA_DOC_SYNC.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  releaseSync: "docs/nodejs/releases/NODE_PROFILE_V1_3_UI_AUTOMATION_MEDIA_SYNC.md",
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
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
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
  console.log("Phase 15 UI/automation/media doc sync: " + report.decision.status);
  console.log("Phase 15 UI/automation/media doc sync blockers: " + report.blockers.length);
  console.log("Phase 15 UI/automation/media doc sync policy violations: " + report.policyViolations.length);
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
  const p14DocSync = path.join(reportDir, "phase14-ui-automation-media-doc-sync.json");
  entries.push([
    "p14DocSync",
    {
      relative: path.relative(repoRoot, p14DocSync).replace(/\\/g, "/"),
      exists: fs.existsSync(p14DocSync),
      text: read(p14DocSync),
    },
  ]);
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const plan = parseJson(files.planJson.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const proExamples = parseJson(files.proExamples.text);
  const p14DocSync = parseJson(files.p14DocSync.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-11") || {};
  const sourceReports = SOURCE_REPORTS.map(([key, basename, task]) => {
    const report = parseJson(files[key].text);
    return {
      id: key,
      basename,
      task,
      exists: files[key].exists,
      status: statusOf(report),
      policyViolations: Array.isArray(report.policyViolations) ? report.policyViolations.length : 999,
      releaseBlockingBlockers: Number((report.decision || {}).releaseBlockingBlockers || 0),
      futurePromotionBlockers: Number((report.decision || {}).futurePromotionBlockers || 0),
      runtimeAuthorityChanged: (report.decision || {}).runtimeAuthorityChanged,
      unpromoted: reportKeepsUnpromoted(report),
    };
  });
  const smokeExamples = ((proExamples.smokeSubset || {}).examples || []).slice().sort();
  const exampleIds = (proExamples.examples || []).map((entry) => entry.id);
  const scenarios = [
    scenario("source_gate_imports", "P15-04 through P15-10 generated reports are imported as partial provider evidence", [
      value("plan_schema", plan.schema === "autojs6-node-phase15-ui-automation-media-doc-sync-v1"),
      value("task", plan.task === "P15-11"),
      value("status", plan.status === "ready"),
      value("promotion_state", plan.promotionState === "provider_poc_not_promoted"),
      value("source_gates", sameSet(plan.sourceGates || [], REQUIRED_SOURCE_GATES)),
      ...sourceReports.map((entry) =>
        value(`generated:${entry.id}`, entry.exists && entry.task === taskFromReport(files, entry.id, entry.task) && entry.status === "partial"),
      ),
      ...sourceReports.map((entry) =>
        value(`policy:${entry.id}`, entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      ),
      ...sourceReports.map((entry) =>
        value(`unpromoted:${entry.id}`, entry.runtimeAuthorityChanged === false && entry.unpromoted),
      ),
      value("p14_doc_sync_ready", files.p14DocSync.exists && statusOf(p14DocSync) === "ready" && (p14DocSync.decision || {}).p14TaskComplete === true),
    ]),
    scenario("declaration_sync", "TypeScript declaration docs and smoke cover UI, automation, media, image, OCR, and compat modules", [
      token(files.typeReadme, "P15-11 docs sync records the v1.3 `pro.ui_overlay_automation_media` declaration state"),
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
      value("smoke_subset", sameSet(smokeExamples, ["tasks-work-manager", "ui-layout"])),
      value("high_risk_examples_present", includesAll(exampleIds, ["overlay-floaty", "media-recorder", "screenshot-ocr", "accessibility-selector"])),
      token(files.examplesDoc, "P15-11 docs sync keeps this policy unchanged for the v1.3"),
      token(files.examplesDoc, "P15-04 through P15-10"),
      token(files.examplesDoc, "`provider_poc_not_promoted`"),
      token(files.examplesDoc, "static, guarded, or denial-oriented"),
      token(files.proReadme, "global install, overlay, notifications/power, media/recorder, screenshot/OCR,"),
      token(files.proExamples, "Runs already-covered Activity-owned UI layout and WorkManager one-shot scheduling/cancel paths without enabling Java, Rhino, overlay, screenshot, OCR, or accessibility authority."),
      token(files.proExamples, "\"id\": \"screenshot-ocr\""),
      token(files.proExamples, "\"packagedBehavior\": \"packaged capture/OCR requires disclosure manifest and permission review\""),
    ]),
    scenario("release_sync", "v1.3 release-facing text records future-only provider state without promotion", [
      value("release_state", (plan.releaseNoteState || {}).current === "future_only"),
      value("no_promoted_providers", Array.isArray((plan.releaseNoteState || {}).promotedProviders) && plan.releaseNoteState.promotedProviders.length === 0),
      token(files.releaseSync, "Node Profile v1.3 UI Automation Media Sync"),
      token(files.releaseSync, "P15-11"),
      token(files.releaseSync, "`provider_poc_not_promoted`"),
      token(files.releaseSync, "No P15-04 through P15-10 provider is promoted"),
      token(files.releaseSync, "`P15-04`"),
      token(files.releaseSync, "`P15-10`"),
    ]),
    scenario("compat_security_error_sync", "Compatibility profile, security model, and error catalog expose the same denial boundary", [
      token(files.compatibilityProfile, "P15-11 docs sync records the v1.3 UI/automation/media declaration, example, release-note, security, and error-catalog state as `provider_poc_not_promoted`"),
      token(files.securityModel, "P15-11 docs sync keeps the v1.3 UI/automation/media release-facing state at"),
      token(files.securityModel, "`provider_poc_not_promoted`"),
      token(files.errorCatalog, "P15-11 UI/automation/media denial sync"),
      token(files.errorCatalog, "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      token(files.errorCatalog, "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"),
    ]),
    scenario("surface_and_gradle_wiring", "Phase 15 manifest, docs, and Gradle expose the P15-11 gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.ui_overlay_automation_media"),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "doc_type_release_sync"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15UiAutomationMediaDocSync"),
      value("surface_mapping_ready", p15Mapping.status === "ready"),
      token(files.phase15Gradle, "verifyNodePhase15UiAutomationMediaDocSync"),
      token(files.phase15Gradle, "verify-node-phase15-ui-automation-media-doc-sync.js"),
      token(files.phase15Tasks, "- [x] P15-11: Sync UI/automation/media docs, examples, declarations, and release notes."),
      token(files.phase15Tasks, "UI/automation/media docs sync gate deployed:"),
      token(files.phase15Readme, ":app:verifyNodePhase15UiAutomationMediaDocSync"),
      token(files.testing, ":app:verifyNodePhase15UiAutomationMediaDocSync"),
      token(files.ciGates, ":app:verifyNodePhase15UiAutomationMediaDocSync"),
    ]),
  ];
  const checks = [
    check(
      "plan-json-present",
      files.planJson.exists &&
        plan.schema === "autojs6-node-phase15-ui-automation-media-doc-sync-v1" &&
        plan.task === "P15-11" &&
        plan.status === "ready" &&
        plan.runtimeAuthorityChanged === false,
      "P15-11 doc sync plan JSON must exist and must not change runtime authority.",
    ),
    check(
      "plan-doc-present",
      files.planDoc.exists && files.planDoc.text.includes("Phase 15 UI Automation Media Doc Sync"),
      "P15-11 doc sync documentation must exist.",
    ),
    check(
      "release-sync-present",
      files.releaseSync.exists && files.releaseSync.text.includes("Node Profile v1.3 UI Automation Media Sync"),
      "P15-11 v1.3 release-facing sync file must exist.",
    ),
    check(
      "source-reports-partial",
      sourceReports.every((entry) => entry.exists && entry.status === "partial" && entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0 && entry.runtimeAuthorityChanged === false && entry.unpromoted),
      "P15-04 through P15-10 generated reports must remain partial, unpromoted, and policy-clean.",
    ),
    check(
      "phase14-doc-sync-ready",
      files.p14DocSync.exists && statusOf(p14DocSync) === "ready" && (p14DocSync.decision || {}).p14TaskComplete === true,
      "P15-11 must import the ready P14-16 UI/automation/media docs sync report.",
    ),
    check(
      "all-scenarios-ready",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-11 sync scenarios must be ready.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-ui-automation-media-doc-sync-report-v1",
    report: REPORT_BASENAME,
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15UiAutomationMediaDocSync",
    task: "P15-11",
    status: policyViolations.length === 0 ? "ready" : "failed",
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    sourceReports,
    checks,
    scenarios,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      promotionState: "provider_poc_not_promoted",
      p15TaskComplete: policyViolations.length === 0,
      sourceReportsImported: sourceReports.every((entry) => entry.exists && entry.status === "partial"),
      sourceReportsPolicyClean: sourceReports.every((entry) => entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      declarationsSynced: scenarioById(scenarios, "declaration_sync").status === "ready",
      examplesSynced: scenarioById(scenarios, "example_policy_sync").status === "ready",
      releaseNotesSynced: scenarioById(scenarios, "release_sync").status === "ready",
      compatibilitySecurityErrorDocsSynced: scenarioById(scenarios, "compat_security_error_sync").status === "ready",
      runtimeAuthorityChanged: false,
      proSurfacePromoted: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: 0,
      sourceFuturePromotionBlockers: sourceReports.reduce((sum, entry) => sum + entry.futurePromotionBlockers, 0),
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 15 UI Automation Media Doc Sync",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Source future promotion blockers: ${report.decision.sourceFuturePromotionBlockers}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Source Reports",
    "",
    "| Report | Task | Status | Policy violations | Future blockers |",
    "| --- | --- | --- | --- | --- |",
  ];
  report.sourceReports.forEach((entry) => {
    lines.push(`| \`${entry.basename}\` | \`${entry.task}\` | \`${entry.status}\` | ${entry.policyViolations} | ${entry.futurePromotionBlockers} |`);
  });
  lines.push("", "## Scenarios", "", "| Scenario | Status | Missing evidence |", "| --- | --- | --- |");
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(missing)} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  return {
    id,
    description,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    missing,
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

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function taskFromReport(files, reportId, fallbackTask) {
  const report = parseJson((files[reportId] || {}).text || "");
  return report.task || fallbackTask;
}

function reportKeepsUnpromoted(report) {
  const decision = report.decision || {};
  return Object.entries(decision)
    .filter(([key]) => key.endsWith("Promoted") || key === "proSurfacePromoted")
    .every(([, value]) => value === false);
}

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((entry, index) => actualSorted[index] === entry);
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((entry) => actual.includes(entry));
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
      console.log("Usage: node verify-node-phase15-ui-automation-media-doc-sync.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
