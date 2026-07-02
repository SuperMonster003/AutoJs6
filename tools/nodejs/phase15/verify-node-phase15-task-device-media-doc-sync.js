#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase15-task-device-media-doc-sync";
const REQUIRED_SOURCE_GATES = Object.freeze([
  "P15-12",
  "P15-13",
  "P15-14",
  "P15-15",
  "P15-16",
  "P15-17",
  "P15-18",
  "P15-19",
  "P15-20",
  "P15-21",
]);
const SOURCE_REPORTS = Object.freeze([
  ["p15TaskDatabase", "phase15-task-database-promotion.json", "P15-12", "partial"],
  ["p15TaskSchemaRollback", "phase15-task-schema-rollback.json", "P15-13", "partial"],
  ["p15PackagedTask", "phase15-packaged-task-smoke.json", "P15-14", "partial"],
  ["p15IntentBroadcast", "phase15-intent-broadcast-promotion.json", "P15-15", "partial"],
  ["p15PackagedBroadcast", "phase15-packaged-broadcast-smoke.json", "P15-16", "partial"],
  ["p15Notification", "phase15-notification-promotion.json", "P15-17", "partial"],
  ["p15SettingsPower", "phase15-settings-power-promotion.json", "P15-18", "partial"],
  ["p15Recorder", "phase15-recorder-promotion.json", "P15-19", "partial"],
  ["p15PackagedRecorder", "phase15-packaged-recorder-smoke.json", "P15-20", "partial"],
  ["p15MediaStorePlayback", "phase15-mediastore-playback-decision.json", "P15-21", "ready"],
]);

const FILES = {
  planJson: "docs/nodejs/phase15/task-device-media-doc-sync.json",
  planDoc: "docs/nodejs/phase15/PHASE15_TASK_DEVICE_MEDIA_DOC_SYNC.md",
  taskReport: "docs/nodejs/reports/phase15/P15_22_TASK_DEVICE_MEDIA_DOC_SYNC.md",
  phase15Tasks: "docs/nodejs/reports/phase15/PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md",
  phase15Readme: "docs/nodejs/reports/phase15/README.md",
  surfaceManifest: "docs/nodejs/phase15/phase15-surface-manifest.json",
  releaseSync: "docs/nodejs/releases/NODE_PROFILE_V1_3_TASK_DEVICE_MEDIA_SYNC.md",
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
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
  notificationsTypes: "docs/nodejs/types/autojs6-node/notifications.d.ts",
  deviceTypes: "docs/nodejs/types/autojs6-node/device.d.ts",
  mediaTypes: "docs/nodejs/types/autojs6-node/media.d.ts",
  mediainfoTypes: "docs/nodejs/types/autojs6-node/mediainfo.d.ts",
  recorderTypes: "docs/nodejs/types/autojs6-node/recorder.d.ts",
  profileCapabilities: "docs/nodejs/types/autojs6-node/profile_capabilities.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  tasksSnippet: "sample/nodejs/pro-parity-suite/snippets/tasks-work-manager.cjs",
  notificationsPowerSnippet: "sample/nodejs/pro-parity-suite/snippets/notifications-power.cjs",
  mediaRecorderSnippet: "sample/nodejs/pro-parity-suite/snippets/media-recorder.cjs",
  p14DocSync: "build/reports/nodejs/phase14-task-device-media-doc-sync.json",
  phase15Gradle: "app/node-phase15-gate.gradle.kts",
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
  console.log("Phase 15 task/device/media doc sync: " + report.decision.status);
  console.log("Phase 15 task/device/media doc sync blockers: " + report.blockers.length);
  console.log("Phase 15 task/device/media doc sync policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([key, basename]) => {
    const file = path.join(repoRoot, "build/reports/nodejs", basename);
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
  const plan = parseJson(files.planJson.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const proExamples = parseJson(files.proExamples.text);
  const p14DocSync = parseJson(files.p14DocSync.text);
  const p15Mapping = (manifest.taskMappings || []).find((entry) => entry.task === "P15-22") || {};
  const sourceReports = SOURCE_REPORTS.map(([key, basename, task, expectedStatus]) => {
    const report = parseJson(files[key].text);
    const decision = report.decision || {};
    return {
      id: key,
      basename,
      task,
      expectedStatus,
      exists: files[key].exists,
      status: statusOf(report),
      policyViolations: Array.isArray(report.policyViolations) ? report.policyViolations.length : 999,
      releaseBlockingBlockers: Number(decision.releaseBlockingBlockers || 0),
      futurePromotionBlockers: Number(decision.futurePromotionBlockers || 0),
      runtimeAuthorityChanged: decision.runtimeAuthorityChanged,
      unpromoted: reportKeepsUnpromoted(report),
      stableDenied: decision.promotionState === "stable_denied" || decision.stableDenialReady === true,
    };
  });
  const smokeExamples = ((proExamples.smokeSubset || {}).examples || []).slice().sort();
  const exampleIds = (proExamples.examples || []).map((entry) => entry.id);
  const scenarios = [
    scenario("source_gate_imports", "P15-12 through P15-21 generated reports are imported without runtime promotion", [
      value("plan_schema", plan.schema === "autojs6-node-phase15-task-device-media-doc-sync-v1"),
      value("task", plan.task === "P15-22"),
      value("status", plan.status === "ready"),
      value("promotion_state", plan.promotionState === "provider_poc_not_promoted"),
      value("source_gates", sameSet(plan.sourceGates || [], REQUIRED_SOURCE_GATES)),
      ...sourceReports.map((entry) =>
        value(`generated:${entry.id}`, entry.exists && entry.status === entry.expectedStatus),
      ),
      ...sourceReports.map((entry) =>
        value(`policy:${entry.id}`, entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0),
      ),
      ...sourceReports.map((entry) =>
        value(`authority:${entry.id}`, entry.runtimeAuthorityChanged === false && entry.unpromoted),
      ),
      value("p15_21_stable_denied", sourceReports.find((entry) => entry.task === "P15-21").stableDenied === true),
      value("p14_doc_sync_ready", files.p14DocSync.exists && statusOf(p14DocSync) === "ready" && (p14DocSync.decision || {}).p14TaskComplete === true),
    ]),
    scenario("declaration_sync", "TypeScript declaration docs and smoke cover task, notification, device, media, recorder, profile, and compat modules", [
      token(files.typeReadme, "P15-22 docs sync records the v1.3 `pro.tasks_device_media` declaration state"),
      token(files.typeReadme, "`provider_poc_not_promoted` with `mediastore_playback` kept `stable_denied`"),
      token(files.workManagerTypes, 'declare module "work_manager"'),
      token(files.notificationsTypes, 'declare module "notifications"'),
      token(files.deviceTypes, 'declare module "device"'),
      token(files.mediaTypes, 'declare module "media"'),
      token(files.mediainfoTypes, 'declare module "mediainfo"'),
      token(files.recorderTypes, 'declare module "recorder"'),
      token(files.profileCapabilities, 'readonly "media.playback": NodeProfileV12CapabilitySpec<"media.playback"'),
      token(files.profileCapabilities, 'readonly "mediastore": NodeProfileV12CapabilitySpec<"mediastore"'),
      token(files.rhinoCompatTypes, "readonly tasks: RhinoTasksCompatModule;"),
      token(files.rhinoCompatTypes, "readonly device: device.DeviceModule;"),
      token(files.rhinoCompatTypes, "readonly recorder: recorderModule.RecorderModule;"),
      token(files.tsSmoke, "import workManager = require(\"work_manager\");"),
      token(files.tsSmoke, "import notifications = require(\"notifications\");"),
      token(files.tsSmoke, "import device = require(\"device\");"),
      token(files.tsSmoke, "import recorder = require(\"recorder\");"),
    ]),
    scenario("example_policy_sync", "Pro examples keep only safe task/device/media smoke runnable", [
      value("smoke_subset", includesAll(smokeExamples, ["tasks-work-manager"])),
      value("high_risk_examples_present", includesAll(exampleIds, ["notifications-power", "media-recorder"])),
      token(files.examplesDoc, "P15-22 docs sync keeps the same rule for the v1.3 `pro.tasks_device_media`"),
      token(files.examplesDoc, "`tasks-work-manager` remains the only task/device/media focused smoke"),
      token(files.examplesDoc, "`notifications-power` and `media-recorder` remain static, guarded, or"),
      token(files.proReadme, "global install, overlay, notifications/power, media/recorder, screenshot/OCR,"),
      token(files.proExamples, '"id": "tasks-work-manager"'),
      token(files.proExamples, '"id": "notifications-power"'),
      token(files.proExamples, '"id": "media-recorder"'),
      token(files.tasksSnippet, "sample.pro-parity-suite.tasks-work-manager=PASS"),
      token(files.notificationsPowerSnippet, "sample.pro-parity-suite.notifications-power=PASS"),
      token(files.mediaRecorderSnippet, "sample.pro-parity-suite.media-recorder=PASS"),
    ]),
    scenario("release_sync", "v1.3 release-facing text records future-only and stable-denied provider state without promotion", [
      value("release_state", (plan.releaseNoteState || {}).current === "future_only_and_stable_denied"),
      value("no_promoted_providers", Array.isArray((plan.releaseNoteState || {}).promotedProviders) && plan.releaseNoteState.promotedProviders.length === 0),
      value("stable_denied_provider", includesAll((plan.releaseNoteState || {}).stableDeniedProviders || [], ["mediastore_playback"])),
      token(files.releaseSync, "Node Profile v1.3 Task Device Media Sync"),
      token(files.releaseSync, "P15-22"),
      token(files.releaseSync, "`provider_poc_not_promoted`"),
      token(files.releaseSync, "`stable_denied`"),
      token(files.releaseSync, "`P15-12`"),
      token(files.releaseSync, "`P15-21`"),
      token(files.releaseSync, "`tasks-work-manager` as the only task/device/media"),
    ]),
    scenario("compat_security_error_sync", "Compatibility profile, security model, and error catalog expose the same task/device/media denial boundary", [
      token(files.compatibilityProfile, "P15-22 docs sync records the v1.3 `pro.tasks_device_media` declaration"),
      token(files.compatibilityProfile, "`mediastore_playback` kept `stable_denied`"),
      token(files.securityModel, "P15-22 docs sync keeps the v1.3 `pro.tasks_device_media` release-facing state"),
      token(files.securityModel, "`mediastore_playback` stable-denied"),
      token(files.errorCatalog, "P15-22 task/device/media docs sync denial sync"),
      token(files.errorCatalog, "P15-12 through P15-20 remain `provider_poc_not_promoted` partial gates"),
      token(files.errorCatalog, "P15-21 keeps `mediastore_playback` `stable_denied`"),
    ]),
    scenario("surface_and_gradle_wiring", "Phase 15 manifest, docs, and Gradle expose the P15-22 gate", [
      value("surface_mapping_owner", p15Mapping.ownerSurface === "pro.tasks_device_media"),
      value("surface_mapping_evidence_class", p15Mapping.evidenceClass === "doc_type_release_sync"),
      value("surface_mapping_gate", p15Mapping.gate === ":app:verifyNodePhase15TaskDeviceMediaDocSync"),
      value("surface_mapping_ready", p15Mapping.status === "ready"),
      token(files.phase15Gradle, "verifyNodePhase15TaskDeviceMediaDocSync"),
      token(files.phase15Gradle, "verify-node-phase15-task-device-media-doc-sync.js"),
      token(files.phase15Tasks, "- [x] P15-22: Sync task/device/media docs, examples, declarations, and release notes."),
      token(files.phase15Tasks, "Task/device/media docs sync gate deployed:"),
      token(files.phase15Readme, ":app:verifyNodePhase15TaskDeviceMediaDocSync"),
      token(files.testing, ":app:verifyNodePhase15TaskDeviceMediaDocSync"),
      token(files.ciGates, ":app:verifyNodePhase15TaskDeviceMediaDocSync"),
    ]),
  ];
  const checks = [
    check(
      "plan-json-present",
      files.planJson.exists &&
        plan.schema === "autojs6-node-phase15-task-device-media-doc-sync-v1" &&
        plan.task === "P15-22" &&
        plan.status === "ready" &&
        plan.runtimeAuthorityChanged === false,
      "P15-22 doc sync plan JSON must exist and must not change runtime authority.",
    ),
    check(
      "plan-doc-present",
      files.planDoc.exists && files.planDoc.text.includes("Phase 15 Task Device Media Doc Sync"),
      "P15-22 doc sync documentation must exist.",
    ),
    check(
      "release-sync-present",
      files.releaseSync.exists && files.releaseSync.text.includes("Node Profile v1.3 Task Device Media Sync"),
      "P15-22 v1.3 release-facing sync file must exist.",
    ),
    check(
      "source-reports-imported",
      sourceReports.every((entry) => entry.exists && entry.status === entry.expectedStatus && entry.policyViolations === 0 && entry.releaseBlockingBlockers === 0 && entry.runtimeAuthorityChanged === false && entry.unpromoted),
      "P15-12 through P15-21 generated reports must remain policy-clean and unpromoted, with P15-21 ready/stable-denied.",
    ),
    check(
      "phase14-doc-sync-ready",
      files.p14DocSync.exists && statusOf(p14DocSync) === "ready" && (p14DocSync.decision || {}).p14TaskComplete === true,
      "P15-22 must import the ready P14-27 task/device/media docs sync report.",
    ),
    check(
      "all-scenarios-ready",
      scenarios.every((entry) => entry.status === "ready"),
      "All P15-22 sync scenarios must be ready.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase15-task-device-media-doc-sync-report-v1",
    report: REPORT_BASENAME,
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase15TaskDeviceMediaDocSync",
    task: "P15-22",
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
      sourceReportsImported: sourceReports.every((entry) => entry.exists && entry.status === entry.expectedStatus),
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
    "# Phase 15 Task Device Media Doc Sync",
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
    "| Report | Task | Status | Expected | Policy violations | Future blockers |",
    "| --- | --- | --- | --- | --- | --- |",
  ];
  report.sourceReports.forEach((entry) => {
    lines.push(`| \`${entry.basename}\` | \`${entry.task}\` | \`${entry.status}\` | \`${entry.expectedStatus}\` | ${entry.policyViolations} | ${entry.futurePromotionBlockers} |`);
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
      console.log("Usage: node verify-node-phase15-task-device-media-doc-sync.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
