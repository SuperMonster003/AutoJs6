#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-task-device-media-doc-sync";
const SOURCE_REPORTS = Object.freeze([
  ["p14TaskDatabase", "phase14-task-database-provider.json"],
  ["p14TaskSchemaRollback", "phase14-task-schema-migration-rollback.json"],
  ["p14PackagedTaskScheduling", "phase14-packaged-task-scheduling-smoke.json"],
  ["p14IntentBroadcast", "phase14-intent-broadcast-provider.json"],
  ["p14PackagedBroadcast", "phase14-packaged-broadcast-trigger-remove-smoke.json"],
  ["p14NotificationOwnership", "phase14-notification-ownership.json"],
  ["p14SettingsPowerMatrix", "phase14-settings-power-oem-background-matrix.json"],
  ["p14RecorderProvider", "phase14-recorder-provider.json"],
  ["p14PackagedRecorderStress", "phase14-packaged-recorder-media-stress.json"],
  ["p14MediaPlaybackMediaStore", "phase14-media-playback-mediastore-boundary.json"],
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_27_TASK_DEVICE_MEDIA_DOC_SYNC.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/task-device-media-doc-sync.json",
  planDoc: "docs/nodejs/phase14/PHASE14_TASK_DEVICE_MEDIA_DOC_SYNC.md",
  releaseMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_2_TASK_DEVICE_MEDIA_MATRIX.md",
  migrationCookbook: "docs/nodejs/phase14/PHASE14_TASK_DEVICE_MEDIA_MIGRATION_COOKBOOK.md",
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
  workManagerTypes: "docs/nodejs/types/autojs6-node/work_manager.d.ts",
  notificationsTypes: "docs/nodejs/types/autojs6-node/notifications.d.ts",
  deviceTypes: "docs/nodejs/types/autojs6-node/device.d.ts",
  mediaTypes: "docs/nodejs/types/autojs6-node/media.d.ts",
  mediainfoTypes: "docs/nodejs/types/autojs6-node/mediainfo.d.ts",
  recorderTypes: "docs/nodejs/types/autojs6-node/recorder.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  tasksSnippet: "sample/nodejs/pro-parity-suite/snippets/tasks-work-manager.cjs",
  notificationsPowerSnippet: "sample/nodejs/pro-parity-suite/snippets/notifications-power.cjs",
  mediaRecorderSnippet: "sample/nodejs/pro-parity-suite/snippets/media-recorder.cjs",
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
  console.log("Phase 14 task/device/media doc sync: " + report.decision.status);
  console.log("Phase 14 task/device/media doc sync blockers: " + report.blockers.length);
  console.log("Phase 14 task/device/media doc sync policy violations: " + report.policyViolations.length);
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
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.tasks_device_media") || null;
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
    scenario("source_gate_imports", "P14-17 through P14-26 generated reports are imported as partial/provider evidence", [
      value("schema", plan.schema === "autojs6-node-phase14-task-device-media-doc-sync-v1"),
      value("task", plan.task === "P14-27"),
      value("status", plan.status === "ready"),
      value("promotionState", plan.promotionState === "provider_poc_not_promoted"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P14-17", "P14-18", "P14-19", "P14-20", "P14-21", "P14-22", "P14-23", "P14-24", "P14-25", "P14-26"])),
      ...generatedReports.map((entry) => check(`generated:${entry.id}`, entry.exists && entry.status === "partial", `${entry.id} generated report must exist with partial status`)),
    ]),
    scenario("declaration_sync", "TypeScript declaration docs and smoke cover task, notification, device, media, recorder, and compat modules", [
      token(files.typeReadme, "P14-27 docs sync records the v1.2 `pro.tasks_device_media` declaration state"),
      token(files.typeReadme, "`provider_poc_not_promoted`"),
      token(files.workManagerTypes, 'declare module "work_manager"'),
      token(files.workManagerTypes, "scheduleOnce(descriptor: ScheduleOnceDescriptor): Promise<string>;"),
      token(files.workManagerTypes, "schedulePeriodic(descriptor: SchedulePeriodicDescriptor): Promise<string>;"),
      token(files.notificationsTypes, 'declare module "notifications"'),
      token(files.notificationsTypes, "notify(options: NotifyOptions"),
      token(files.notificationsTypes, "openNotificationSettings(options?"),
      token(files.deviceTypes, 'declare module "device"'),
      token(files.deviceTypes, "openBatteryOptimizationSettings(options?: DevicePowerSettingsOptions)"),
      token(files.mediaTypes, 'declare module "media"'),
      token(files.mediainfoTypes, 'declare module "mediainfo"'),
      token(files.recorderTypes, 'declare module "recorder"'),
      token(files.recorderTypes, "start(options?: RecorderStartOptions): Promise<never>;"),
      token(files.rhinoCompatTypes, "readonly tasks: RhinoTasksCompatModule;"),
      token(files.rhinoCompatTypes, "readonly device: device.DeviceModule;"),
      token(files.rhinoCompatTypes, "readonly recorder: recorderModule.RecorderModule;"),
      token(files.tsSmoke, "import workManager = require(\"work_manager\");"),
      token(files.tsSmoke, "import notifications = require(\"notifications\");"),
      token(files.tsSmoke, "import device = require(\"device\");"),
      token(files.tsSmoke, "import recorder = require(\"recorder\");"),
    ]),
    scenario("example_and_migration_sync", "Pro examples and migration cookbook keep only safe task/device/media smoke runnable", [
      value("smokeSubsetIncludesTasks", includesAll(smokeExamples, ["tasks-work-manager"])),
      value("highRiskExamplesPresent", includesAll(exampleIds, ["notifications-power", "media-recorder"])),
      token(files.examplesDoc, "P14-27 docs sync keeps the same rule"),
      token(files.examplesDoc, "`tasks-work-manager` remains the only task/device/media focused smoke"),
      token(files.proReadme, "P14-27 docs sync keeps `tasks-work-manager`"),
      token(files.proExamples, '"id": "tasks-work-manager"'),
      token(files.proExamples, '"id": "notifications-power"'),
      token(files.proExamples, '"id": "media-recorder"'),
      token(files.tasksSnippet, "sample.pro-parity-suite.tasks-work-manager=PASS"),
      token(files.notificationsPowerSnippet, "sample.pro-parity-suite.notifications-power=PASS"),
      token(files.mediaRecorderSnippet, "sample.pro-parity-suite.media-recorder=PASS"),
      token(files.migrationCookbook, "Phase 14 Task Device Media Migration Cookbook"),
      token(files.migrationCookbook, "`sample/nodejs/pro-parity-suite:tasks-work-manager` remains the focused"),
      token(files.migrationCookbook, "`sample/nodejs/pro-parity-suite:notifications-power` documents"),
      token(files.migrationCookbook, "`sample/nodejs/pro-parity-suite:media-recorder` documents"),
    ]),
    scenario("release_matrix_sync", "v1.2 task/device/media release matrix records provider POC state without promotion", [
      token(files.releaseMatrix, "Node Profile v1.2 Task Device Media Matrix"),
      token(files.releaseMatrix, "P14-27 docs sync"),
      token(files.releaseMatrix, "`provider_poc_not_promoted`"),
      token(files.releaseMatrix, "P14-17"),
      token(files.releaseMatrix, "P14-26"),
      token(files.releaseMatrix, "stable-denied / future-only"),
      token(files.releaseMatrix, "`tasks-work-manager` remains the only task/device/media focused smoke path"),
    ]),
    scenario("compat_security_error_sync", "Compatibility profile, security model, and error catalog expose the same denial boundary", [
      token(files.compatibilityProfile, "P14-27 docs sync records the v1.2 `pro.tasks_device_media`"),
      token(files.compatibilityProfile, "`provider_poc_not_promoted`"),
      token(files.securityModel, "P14-27 docs sync records the v1.2 `pro.tasks_device_media`"),
      token(files.securityModel, "`provider_poc_not_promoted`"),
      token(files.errorCatalog, "P14-27 task/device/media docs sync denial sync"),
      token(files.errorCatalog, "`tasks-work-manager` remains the only task/device/media focused Pro smoke"),
      token(files.errorCatalog, "`notifications-power` and `media-recorder` remain migration evidence"),
    ]),
    scenario("surface_and_gradle_wiring", "Phase 14 manifest, docs, and Gradle expose the P14-27 gate", [
      value("surfaceEvidence", proSurface &&
        (proSurface.tasks || []).includes("P14-27") &&
        (proSurface.evidence || []).includes("docs/nodejs/phase14/task-device-media-doc-sync.json") &&
        (proSurface.evidence || []).includes(":app:verifyNodePhase14TaskDeviceMediaDocSync")),
      token(files.surfaceManifestDoc, "P14-27 adds the task/device/media docs/examples/declarations sync gate"),
      token(files.phase14Tasks, "- [x] P14-27: Sync task/device/media docs, examples, and declarations."),
      token(files.phase14Tasks, "Task/device/media docs sync gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14TaskDeviceMediaDocSync"),
      token(files.testing, ":app:verifyNodePhase14TaskDeviceMediaDocSync"),
      token(files.ciGates, ":app:verifyNodePhase14TaskDeviceMediaDocSync"),
      token(files.gradle14, "verifyNodePhase14TaskDeviceMediaDocSync"),
      token(files.gradle14, "verify-node-phase14-task-device-media-doc-sync.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-task-device-media-doc-sync-v1", "P14-27 doc sync plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Task Device Media Doc Sync"), "P14-27 doc sync documentation must exist."),
    check("release-matrix-present", files.releaseMatrix.exists && files.releaseMatrix.text.includes("Node Profile v1.2 Task Device Media Matrix"), "P14-27 v1.2 release matrix must exist."),
    check("migration-cookbook-present", files.migrationCookbook.exists && files.migrationCookbook.text.includes("Phase 14 Task Device Media Migration Cookbook"), "P14-27 migration cookbook must exist."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-27 sync scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-task-device-media-doc-sync-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-27",
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
      examplesSynced: scenarioById(scenarios, "example_and_migration_sync").status === "ready",
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
    "# Phase 14 Task Device Media Doc Sync",
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

function value(id, ok) {
  return check(id, ok, `failed contract check: ${id}`);
}

function token(file, expected) {
  return check(`token:${file.relative}:${expected}`, file.exists && file.text.includes(expected), `missing evidence token: ${expected}`);
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
  return Array.isArray(actual) && actual.length === expected.length && expected.every((entry) => actual.includes(entry));
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
