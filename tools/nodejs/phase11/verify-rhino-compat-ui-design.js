#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-ui-design-verification";
const MODULE_DECISION = "s11_23_rhino_ui_bridge_migration_design";
const PARTIAL_IDS = Object.freeze([
  "ui.__module",
  "ui.layout",
  "ui.layoutFile",
  "ui.finish",
  "ui.keepScreenOn",
]);
const BLOCKED_IDS = Object.freeze([
  "ui.inflate",
  "ui.findById",
  "ui.findByStringId",
  "ui.findView",
  "ui.setContentView",
  "ui.useAndroidLayout",
  "ui.isUiThread",
  "ui.post",
  "ui.run",
  "ui.registerWidget",
  "ui.R",
  "ui.widgets",
  "ui.emitter",
  "ui.root",
]);
const UNSUPPORTED_IDS = Object.freeze([
  "ui.backgroundColor",
  "ui.getNavigationBarHeight",
  "ui.getStatusBarHeight",
  "ui.getVisibleNavigationBarHeight",
  "ui.getVisibleStatusBarHeight",
  "ui.navigationBarColor",
  "ui.navigationBarIconLight",
  "ui.navigationBarIconLightBy",
  "ui.statusBarColor",
  "ui.statusBarIconLight",
  "ui.statusBarIconLightBy",
  "ui.navigationBarHeight",
  "ui.statusBarHeight",
  "ui.visibleNavigationBarHeight",
  "ui.visibleStatusBarHeight",
]);

const FILES = {
  design: "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md",
  uiBridgeDesign: "docs/nodejs/design/UI_BRIDGE.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  uiTypes: "docs/nodejs/types/autojs6-node/ui.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const checks = buildChecks(files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-ui-design-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatUiDesign",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      uiModuleDesigned: checkById(checks, "status-ui-module"),
      rawUiObjectsBlocked: checkById(checks, "status-raw-ui-blocked"),
      noCompatUiFacade: checkById(checks, "no-compat-ui-facade"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat UI design verification: " + report.decision.status);
  console.log("Rhino compat UI module: require(\"ui\") JSON provider design gate");
  console.log("Rhino compat UI policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const uiModule = moduleById.get("ui");
  const entries = uiModule ? uiModule.apiEntries || [] : [];
  const matrixUiRow = findMatrixRow(files.matrix.text, "ui");
  const compatFactory = extractBetween(files.runtime.text, "function __autojs6_limited_rhino_compat()", "function __autojs6_limited_ui()");
  const manifestTotalsMatch = totalsMatch(manifest.totals, countManifestStatuses(manifest));

  return [
    check(
      "design-core-boundary",
      includesAll(files.design.text, [
        "S11-23",
        "Do not add `compat.ui`",
        "`require(\"ui\")`",
        "`ui.showLayout(layoutJson, options?)`",
        "`UiHandle.update(patch)`",
        "`UiHandle.batchUpdate(patches)`",
        "`UiHandle.on(event, callback)`",
        "`UiHandle.close()`",
        "UI_SESSION",
      ]),
      "design doc must keep require(\"ui\") as the current Node UI migration target and avoid compat.ui/global ui",
    ),
    check(
      "design-thread-model",
      includesAll(files.design.text, [
        "Node event loop",
        "Android main thread",
        "drainEvents",
        "`ui.run(fn)`",
        "`ui.post(fn)`",
        "blocked_by_design",
        "JSON event snapshots",
      ]),
      "design doc must define the Node event loop / Android main thread relationship and block ui.run/ui.post",
    ),
    check(
      "design-raw-object-denial",
      includesAll(files.design.text, [
        "NativeView",
        "Android `View`",
        "`ui.inflate(...)`",
        "`ui.findById(...)`",
        "`ui.findView(...)`",
        "`ui.root`",
        "`ui.R`",
        "`ui.emitter`",
      ]),
      "design doc must reject Rhino NativeView/View lookup, resource, widget, and Activity emitter leakage",
    ),
    check(
      "design-sample-migration",
      includesAll(files.design.text, [
        "## Migration Example",
        "`ui.layout(...)`",
        "ui.showLayout",
        "handle.on(\"click\"",
        "handle.update",
        "handle.close",
      ]),
      "design doc must include a sample migration from Rhino UI to Node showLayout/UiHandle",
    ),
    check(
      "status-ui-module",
      uiModule &&
        uiModule.status === "partial" &&
        uiModule.asyncMode === "promise" &&
        uiModule.decision === MODULE_DECISION &&
        uiModule.nodeModules.includes("ui") &&
        uiModule.bridgeModules.includes("ui"),
      "status manifest must mark ui as an S11-23 partial require(\"ui\") migration design",
    ),
    check(
      "status-ui-partial-apis",
      PARTIAL_IDS.every((id) => entriesFor(entries, id).every((entry) => entry.status === "partial" && entry.asyncMode === "promise" && entry.nodeModule === "ui" && entry.bridgeModule === "ui")) &&
        entriesFor(entries, "ui.__module")[0]?.nodeName === "ui" &&
        entriesFor(entries, "ui.layout")[0]?.nodeName === "showLayout" &&
        entriesFor(entries, "ui.layoutFile")[0]?.nodeName === "showLayout" &&
        entriesFor(entries, "ui.finish")[0]?.nodeName === "close",
      "status manifest must keep only the JSON-compatible ui entry, layout, finish, and keepScreenOn candidates as partial",
    ),
    check(
      "status-raw-ui-blocked",
      entriesFor(entries, "ui.inflate").length === 2 &&
        BLOCKED_IDS.every((id) => entriesFor(entries, id).length > 0) &&
        BLOCKED_IDS.every((id) => entriesFor(entries, id).every((entry) => entry.status === "blocked_by_design" && entry.asyncMode === "not_applicable" && !entry.nodeModule && !entry.bridgeModule)),
      "status manifest must block raw NativeView/View lookup, widget registry, Activity emitter, and main-thread JS APIs by design",
    ),
    check(
      "status-window-apis-deferred",
      UNSUPPORTED_IDS.every((id) => entriesFor(entries, id).length === 1) &&
        UNSUPPORTED_IDS.every((id) => entriesFor(entries, id).every((entry) => entry.status === "unsupported" && entry.nodeModule === "ui" && entry.bridgeModule === "ui")),
      "status manifest must keep status/navigation/background window helper APIs deferred until provider support exists",
    ),
    check(
      "status-totals",
      manifestTotalsMatch,
      "status manifest totals must match the actual module and API entry status counts",
    ),
    check(
      "matrix-ui-row",
      /\|\s*29\s*\|\s*ui\s*\|\s*ui\s*\|\s*partial\s*\|[^|]*ui/.test(matrixUiRow) &&
        /blocked_by_design:15/.test(matrixUiRow) &&
        /partial:5/.test(matrixUiRow) &&
        /unsupported:15/.test(matrixUiRow),
      "generated matrix must show S11-23 ui API split: 5 partial, 15 unsupported, 15 blocked_by_design",
    ),
    check(
      "ui-bridge-design-s11-23",
      includesAll(files.uiBridgeDesign.text, [
        "Task S11-23",
        "RHINO_UI_COMPATIBILITY.md",
        "showLayout",
        "UiHandle.on",
        "drainEvents",
        "Node event loop",
        "`ui.run()`",
        "`ui.post()`",
        "`NativeView`",
      ]),
      "UI_BRIDGE design must reference the S11-23 Rhino UI mapping and thread/raw-object boundaries",
    ),
    check(
      "security-model-s11-23",
      includesAll(files.securityModel.text, [
        "S11-23",
        "`ui.layout()`",
        "`require(\"ui\").showLayout()`",
        "`UiHandle.update()`",
        "`UiHandle.batchUpdate()`",
        "`ui.run()`",
        "`ui.post()`",
        "Node event loop",
        "`drainEvents`",
        "`NativeView`",
      ]),
      "security model must document S11-23 UI compatibility boundaries",
    ),
    check(
      "migration-guide-s11-23",
      includesAll(files.migration.text, [
        "### UI Scripts",
        "ui.layout",
        "ui.run",
        "ui.post",
        "ui.showLayout",
        "handle.on",
        "handle.update",
        "handle.batchUpdate",
        "NativeView",
        "RHINO_UI_COMPATIBILITY.md",
      ]),
      "migration guide must include a Rhino UI to Node showLayout sample and raw-object/thread warnings",
    ),
    check(
      "loader-design-s11-23",
      includesAll(files.loaderDesign.text, [
        "S11-23",
        "does not add `compat.ui`",
        "global `ui`",
        "showLayout",
        "UiHandle.on",
        "batchUpdate",
        "NativeView",
        "no `ui` compat type surface",
      ]),
      "compat loader design must record S11-23 as design-only with no ui compat runtime/type facade",
    ),
    check(
      "ui-types-current-surface",
      includesAll(files.uiTypes.text, [
        "declare module \"ui\"",
        "showLayout",
        "UiHandle",
        "on(event: string",
        "update(patch",
        "batchUpdate(patches",
        "close(options",
        "UiEvent",
      ]) &&
        !files.uiTypes.text.includes("NativeView") &&
        !files.uiTypes.text.includes("Activity") &&
        !files.uiTypes.text.includes("ViewGroup"),
      "ui type declarations must expose the JSON UiHandle surface and no raw Android/Rhino UI objects",
    ),
    check(
      "runtime-ui-current-surface",
      includesAll(files.runtime.text, [
        "function __autojs6_limited_ui()",
        "function showLayout(layoutJson, options)",
        "\"showLayout\"",
        "\"drainEvents\"",
        "function update(patch, options)",
        "function batchUpdate(patches, options)",
        "function close(options)",
        "function on(eventName, callback, options)",
        "__autojs6_ui_state",
        "__autojs6_ui_bind",
      ]),
      "runtime must still expose the current require(\"ui\") JSON provider surface",
    ),
    check(
      "provider-resource-protocol",
      files.resourceRegistry.text.includes("UI_SESSION(\"ui\")") &&
        includesAll(files.protocol.text, [
          "showLayout",
          "batchUpdate",
          "drainEvents",
          "close",
        ]),
      "UI provider protocol must include resource registry ownership and show/update/drain/close methods",
    ),
    check(
      "no-compat-ui-facade",
      compatFactory.length > 0 &&
        !files.runtime.text.includes("compat.ui") &&
        !/\bui\s*:/.test(compatFactory) &&
        !files.compatTypes.text.includes("readonly ui") &&
        !files.compatTypes.text.includes("import ui") &&
        !files.compatTypes.text.includes("ui:"),
      "S11-23 must not promote compat.ui, a rhino_compat ui type surface, or a compat object ui property",
    ),
    check(
      "task-plan-s11-23-complete",
      /\[x\]\s+S11-23/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("RHINO_UI_COMPATIBILITY.md") &&
        files.taskPlan.text.includes("verifyNodeRhinoCompatUiDesign"),
      "Phase 11 task plan must mark S11-23 complete and name the design gate",
    ),
    check(
      "gradle-task",
      files.gradle.text.includes("verifyNodeRhinoCompatUiDesign") &&
        files.gradle.text.includes("verify-rhino-compat-ui-design.js") &&
        files.gradle.text.includes("RHINO_UI_COMPATIBILITY.md"),
      "Gradle must register verifyNodeRhinoCompatUiDesign with the design doc as an input",
    ),
  ];
}

function entriesFor(entries, id) {
  return entries.filter((entry) => entry.id === id);
}

function countManifestStatuses(manifest) {
  const counts = {};
  (manifest.modules || []).forEach((module) => {
    counts[module.status] = (counts[module.status] || 0) + 1;
    (module.apiEntries || []).forEach((entry) => {
      counts[entry.status] = (counts[entry.status] || 0) + 1;
    });
  });
  return counts;
}

function totalsMatch(actual, expected) {
  if (!actual || typeof actual !== "object") {
    return false;
  }
  const keys = new Set([...Object.keys(actual), ...Object.keys(expected)]);
  for (const key of keys) {
    if ((actual[key] || 0) !== (expected[key] || 0)) {
      return false;
    }
  }
  return true;
}

function findMatrixRow(matrix, moduleId) {
  const escaped = moduleId.replace(/\./g, "\\.");
  const pattern = new RegExp(`\\|\\s*\\d+\\s*\\|\\s*${escaped}\\s*\\|`);
  return matrix.split(/\r?\n/).find((line) => pattern.test(line)) || "";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function extractBetween(text, startNeedle, endNeedle) {
  const start = text.indexOf(startNeedle);
  if (start < 0) {
    return "";
  }
  const end = text.indexOf(endNeedle, start + startNeedle.length);
  return end < 0 ? text.slice(start) : text.slice(start, end);
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat UI Design Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((check) => {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${escapeTable(check.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-ui-design.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) {
      index += 1;
    }
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

main();
