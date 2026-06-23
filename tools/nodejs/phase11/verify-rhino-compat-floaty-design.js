#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-floaty-design-verification";
const MODULE_DECISION = "s11_24_floaty_overlay_design_gate";
const PERMISSION_DECISION = "s11_24_overlay_permission_provider_deferred";
const SESSION_DECISION = "s11_24_overlay_session_provider_deferred";
const RAW_WINDOW_DECISION = "s11_24_raw_overlay_window_blocked";
const XML_WINDOW_DECISION = "s11_24_xml_window_wrapper_blocked";
const CLIPBOARD_DECISION = "s11_24_clipboard_replacement_no_overlay";

const UNSUPPORTED_IDS = Object.freeze([
  "floaty.__module",
  "floaty.closeAll",
  "floaty.ensurePermission",
  "floaty.hasPermission",
  "floaty.requestPermission",
]);
const BLOCKED_IDS = Object.freeze([
  "floaty.rawWindow",
  "floaty.window",
]);

const FILES = {
  design: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  floatyDesign: "docs/nodejs/design/FLOATY_NODE.md",
  uiBridgeDesign: "docs/nodejs/design/UI_BRIDGE.md",
  bridgeModulesDesign: "docs/nodejs/design/BRIDGE_V1_1_MODULES.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
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
    schema: "autojs6-node-rhino-compat-floaty-design-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatFloatyDesign",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      floatyModuleDesignGated: checkById(checks, "status-floaty-module"),
      rawOverlayObjectsBlocked: checkById(checks, "status-window-apis-blocked"),
      noRuntimeFloatyFacade: checkById(checks, "no-runtime-floaty-facade"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat floaty design verification: " + report.decision.status);
  console.log("Rhino compat floaty module: overlay provider design gate");
  console.log("Rhino compat floaty policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const floatyModule = moduleById.get("floaty");
  const entries = floatyModule ? floatyModule.apiEntries || [] : [];
  const matrixFloatyRow = findMatrixRow(files.matrix.text, "floaty");
  const manifestTotalsMatch = totalsMatch(manifest.totals, countManifestStatuses(manifest));

  return [
    check(
      "design-core-boundary",
      includesAll(files.design.text, [
        "S11-24",
        "Do not add `require(\"floaty\")`",
        "`require(\"ui.overlay\")`",
        "`compat.floaty`",
        "global `floaty`",
        "FLOATY_NODE.md",
        "SYSTEM_ALERT_WINDOW",
      ]),
      "design doc must keep Rhino floaty behind the overlay design gate with no runtime facade",
    ),
    check(
      "design-api-mapping",
      includesAll(files.design.text, [
        "## API Mapping",
        "`floaty.window(xml)`",
        "`floaty.rawWindow(xml)`",
        "blocked_by_design",
        "`floaty.getClip()`",
        "renamed",
        "`require(\"clipboard\").getText()`",
        "`require(\"autojs6:compat\").getClip()`",
      ]),
      "design doc must map window/rawWindow as blocked and getClip as clipboard migration",
    ),
    check(
      "design-provider-gap-list",
      includesAll(files.design.text, [
        "## Provider Gap List",
        "`ui.overlay` or `floaty`",
        "Android overlay permission",
        "foreground service",
        "packaged APK manifest injection",
        "JSON-only layout descriptor",
        "opaque `FloatyHandle`",
        "bounded `drainEvents`",
        "provider-owned resource registry",
        "permission revocation",
        "WindowManager",
        "LayoutParams",
        "FloatyService",
        "NativeView",
      ]),
      "design doc must include the S11-24 provider gap list for permission, disclosure, handles, event drain, packaged behavior, and raw-object denial",
    ),
    check(
      "design-close-cleanup",
      includesAll(files.design.text, [
        "## Close And Cleanup Model",
        "`handle.close()`",
        "script stop",
        "service destroy",
        "notification stop",
        "permission revocation",
        "process death",
        "stale handle",
      ]),
      "design doc must make close paths auditable and idempotent",
    ),
    check(
      "floaty-node-s11-24",
      includesAll(files.floatyDesign.text, [
        "Task S11-24",
        "RHINO_FLOATY_COMPATIBILITY.md",
        "floaty.window(xml)",
        "floaty.rawWindow(xml)",
        "floaty.getClip()",
        "clipboard",
        "overlay provider",
      ]),
      "FLOATY_NODE must reference the S11-24 Rhino compatibility boundary",
    ),
    check(
      "status-floaty-module",
      floatyModule &&
        floatyModule.status === "unsupported" &&
        floatyModule.asyncMode === "promise" &&
        floatyModule.decision === MODULE_DECISION &&
        floatyModule.nodeModules.includes("autojs6:compat") &&
        floatyModule.nodeModules.includes("clipboard") &&
        floatyModule.bridgeModules.includes("clipboard"),
      "status manifest must mark floaty as an S11-24 unsupported overlay design gate with clipboard replacement evidence",
    ),
    check(
      "status-permission-and-close-deferred",
      entryIs(entries, "floaty.__module", "unsupported", "s11_24_overlay_module_deferred") &&
        entryIs(entries, "floaty.closeAll", "unsupported", SESSION_DECISION) &&
        ["floaty.ensurePermission", "floaty.hasPermission", "floaty.requestPermission"].every((id) => entryIs(entries, id, "unsupported", PERMISSION_DECISION)) &&
        UNSUPPORTED_IDS.every((id) => entriesFor(entries, id).every((entry) => entry.asyncMode === "promise")),
      "status manifest must keep module, permission, and closeAll APIs deferred as async provider work",
    ),
    check(
      "status-window-apis-blocked",
      entryIs(entries, "floaty.rawWindow", "blocked_by_design", RAW_WINDOW_DECISION) &&
        entryIs(entries, "floaty.window", "blocked_by_design", XML_WINDOW_DECISION) &&
        BLOCKED_IDS.every((id) => entriesFor(entries, id).every((entry) => entry.asyncMode === "not_applicable" && !entry.nodeModule && !entry.bridgeModule)),
      "status manifest must block rawWindow/window exact Rhino overlay wrapper semantics by design",
    ),
    check(
      "status-getclip-renamed",
      entryIs(entries, "floaty.getClip", "renamed", CLIPBOARD_DECISION, "getText") &&
        entriesFor(entries, "floaty.getClip").every((entry) => entry.asyncMode === "promise" && entry.nodeModule === "clipboard" && entry.bridgeModule === "clipboard"),
      "status manifest must migrate floaty.getClip to clipboard.getText without hidden overlay windows",
    ),
    check(
      "status-floaty-api-counts",
      countStatuses(entries).unsupported === 5 &&
        countStatuses(entries).blocked_by_design === 2 &&
        countStatuses(entries).renamed === 1,
      "status manifest must split floaty APIs as 5 unsupported, 2 blocked_by_design, and 1 renamed",
    ),
    check(
      "status-totals",
      manifestTotalsMatch,
      "status manifest totals must match the actual module and API entry status counts",
    ),
    check(
      "matrix-floaty-row",
      /\|\s*53\s*\|\s*floaty\s*\|\s*floaty\s*\|\s*unsupported\s*\|[^|]*autojs6:compat[^|]*clipboard/.test(matrixFloatyRow) &&
        /blocked_by_design:2/.test(matrixFloatyRow) &&
        /renamed:1/.test(matrixFloatyRow) &&
        /unsupported:5/.test(matrixFloatyRow),
      "generated matrix must show S11-24 floaty split: 5 unsupported, 2 blocked_by_design, 1 renamed",
    ),
    check(
      "security-model-s11-24",
      includesAll(files.securityModel.text, [
        "S11-24",
        "`require(\"floaty\")`",
        "`require(\"ui.overlay\")`",
        "`compat.floaty`",
        "`SYSTEM_ALERT_WINDOW`",
        "`floaty.window()`",
        "`floaty.rawWindow()`",
        "`WindowManager`",
        "`LayoutParams`",
        "`NativeView`",
        "foreground disclosure",
        "auditable close paths",
        "`floaty.getClip()`",
      ]),
      "security model must document S11-24 floaty/overlay permission, disclosure, raw-object, and close-path boundaries",
    ),
    check(
      "migration-guide-s11-24",
      includesAll(files.migration.text, [
        "### Floaty And Overlay",
        "floaty.window",
        "floaty.rawWindow()",
        "require(\"floaty\")",
        "require(\"ui.overlay\")",
        "compat.floaty",
        "clipboard.getText",
        "WindowManager.LayoutParams",
        "NativeView",
        "RHINO_FLOATY_COMPATIBILITY.md",
      ]),
      "migration guide must explain current Node floaty absence and clipboard replacement for floaty.getClip",
    ),
    check(
      "loader-design-s11-24",
      includesAll(files.loaderDesign.text, [
        "S11-24",
        "does not add `compat.floaty`",
        "require(\"floaty\")",
        "require(\"ui.overlay\")",
        "clipboard.getText()",
        "compat.getClip()",
        "no `floaty` or `ui.overlay` compat type surface",
      ]),
      "compat loader design must record S11-24 as design-only with no floaty runtime/type facade",
    ),
    check(
      "ui-bridge-s11-24",
      includesAll(files.uiBridgeDesign.text, [
        "S11-24",
        "RHINO_FLOATY_COMPATIBILITY.md",
        "floaty.window()",
        "floaty.rawWindow()",
        "floaty.getClip()",
        "clipboard",
        "no base `ui` capability may imply overlay authority",
      ]),
      "UI bridge design must keep floaty outside the base ui capability and reference the S11-24 mapping",
    ),
    check(
      "bridge-modules-s11-24",
      includesAll(files.bridgeModulesDesign.text, [
        "S11-24",
        "RHINO_FLOATY_COMPATIBILITY.md",
        "floaty.window()",
        "floaty.rawWindow()",
        "floaty.getClip()",
        "controlled clipboard surface",
      ]),
      "BRIDGE_V1_1_MODULES must reference the S11-24 Rhino floaty migration details",
    ),
    check(
      "task-plan-s11-24-complete",
      /\[x\]\s+S11-24/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("RHINO_FLOATY_COMPATIBILITY.md") &&
        files.taskPlan.text.includes("verifyNodeRhinoCompatFloatyDesign"),
      "Phase 11 task plan must mark S11-24 complete and name the design gate",
    ),
    check(
      "gradle-task",
      files.gradle.text.includes("verifyNodeRhinoCompatFloatyDesign") &&
        files.gradle.text.includes("verify-rhino-compat-floaty-design.js") &&
        files.gradle.text.includes("RHINO_FLOATY_COMPATIBILITY.md") &&
        files.gradle.text.includes("FLOATY_NODE.md"),
      "Gradle must register verifyNodeRhinoCompatFloatyDesign with the design docs as inputs",
    ),
    check(
      "no-runtime-floaty-facade",
      !files.runtime.text.includes("function __autojs6_limited_floaty") &&
        !files.runtime.text.includes("function __autojs6_rhino_compat_floaty") &&
        !files.runtime.text.includes("compat.floaty") &&
        !files.runtime.text.includes("name === \"floaty\"") &&
        !files.compatTypes.text.includes("floaty") &&
        !files.permissionManifest.text.includes("\"floaty\"") &&
        !/\bFLOATY\b/.test(files.permissionManifest.text) &&
        !/\bFLOATY\b/.test(files.resourceRegistry.text),
      "S11-24/P12 must not promote a Rhino floaty runtime facade, compat types, raw floaty capability, or raw floaty resource registry type",
    ),
  ];
}

function entriesFor(entries, id) {
  return entries.filter((entry) => entry.id === id);
}

function entryIs(entries, id, status, decision, nodeName) {
  const matching = entriesFor(entries, id);
  if (matching.length === 0) {
    return false;
  }
  return matching.every((entry) => {
    if (entry.status !== status || entry.decision !== decision) {
      return false;
    }
    if (nodeName !== undefined && entry.nodeName !== nodeName) {
      return false;
    }
    return true;
  });
}

function countStatuses(entries) {
  return entries.reduce((counts, entry) => {
    counts[entry.status] = (counts[entry.status] || 0) + 1;
    return counts;
  }, {});
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
    "# Rhino Compat Floaty Design Verification",
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
      console.log("Usage: node verify-rhino-compat-floaty-design.js [--repo-root dir] [--report-dir dir]");
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
