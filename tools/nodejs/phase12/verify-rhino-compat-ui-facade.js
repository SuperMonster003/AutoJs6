#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-ui-facade-verification";

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  uiTypes: "docs/nodejs/types/autojs6-node/ui.d.ts",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  instrumentation: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  subsetDoc: "docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md",
  uiDesign: "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-ui-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatUiFacade",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtime: checkById(checks, "runtime-facade"),
      types: checkById(checks, "types-and-smoke"),
      statusManifest: checkById(checks, "status-manifest"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat UI facade verification: " + report.decision.status);
  console.log("Rhino compat UI facade policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files) {
  const statusManifest = JSON.parse(files.statusManifest.text);
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const uiModule = (statusManifest.modules || []).find((item) => item.id === "ui");
  const uiEntries = new Map((uiModule && uiModule.apiEntries || []).map((entry) => [entry.id, entry]));
  const surface = (surfaceManifest.surfaces || []).find((item) => item.id === "ui.compat");
  return [
    check(
      "runtime-facade",
      includesAll(files.runtime.text, [
        '"ui"])',
        '"events.keys", "ui"',
        "const ui = __autojs6_limited_ui();",
        "ui: ui",
        '"P12-09"',
        "P12-09 constrained UI",
      ]),
      "Runtime must expose compat.ui, include ui in explicit global/module status, and record P12-09.",
    ),
    check(
      "runtime-no-raw-ui",
      includesAll(files.runtime.text, [
        "__autojs6_limited_ui_cache = Object.freeze({",
        "showLayout,",
        "state: __autojs6_ui_state",
        "bind: __autojs6_ui_bind",
      ]) &&
        !files.runtime.text.includes("compat.ui.run") &&
        !files.runtime.text.includes("compat.ui.post") &&
        !files.runtime.text.includes("compat.ui.findById") &&
        !files.runtime.text.includes("compat.ui.layout("),
      "compat.ui must reuse the constrained JSON provider and not add run/post/findById/layout raw-view APIs.",
    ),
    check(
      "types-and-smoke",
      includesAll(files.types.text, [
        'import ui = require("ui");',
        "RhinoUiCompatModule = ui.UiModule",
        "readonly ui: RhinoUiCompatModule",
      ]) &&
        includesAll(files.uiTypes.text, [
          "showLayout(layoutJson: LayoutDescriptor",
          "batchUpdate(patches",
          "on(event: string",
          "close(options",
        ]) &&
        includesAll(files.typeSmoke.text, [
          "rhinoCompat.RhinoUiCompatModule",
          "rhinoCompat.ui",
          "compatUi.showLayout",
          "compatUiHandle.on",
          "compatUiHandle.update",
          "compatUiHandle.batchUpdate",
          "compatUiHandle.close",
        ]),
      "Type declarations and TypeScript smoke must cover compat.ui and UiHandle operations.",
    ),
    check(
      "instrumentation-fake-transport",
      includesAll(files.instrumentation.text, [
        'const compat = require("autojs6:compat");',
        '["autojs6:compat.ui", compat.ui]',
        "compat.ui must not install a default global",
        "compat.install({ globals: true })",
        "globalThis.ui, compat.ui",
        "compat.ui.showLayout",
        "compat.ui.update",
        "compat.ui.batchUpdate",
        "compat.ui.close",
        "compat.ui click callback did not run",
      ]),
      "Instrumentation must verify default-global denial, explicit global install, and compat.ui bridge routing.",
    ),
    check(
      "status-manifest",
      uiModule &&
        (uiModule.nodeModules || []).includes("autojs6:compat") &&
        uiModule.decision === "p12_09_compat_ui_constrained_facade_partial" &&
        entryHas(uiEntries, "ui.layout", "supported_async", "autojs6:compat", "ui.showLayout") &&
        entryHas(uiEntries, "ui.finish", "supported_async", "autojs6:compat", "ui.handle.close") &&
        entryHas(uiEntries, "ui.layoutFile", "partial", "autojs6:compat", "ui.showLayout") &&
        entryBlocked(uiEntries, "ui.findById") &&
        entryBlocked(uiEntries, "ui.run") &&
        entryBlocked(uiEntries, "ui.post") &&
        statusManifest.totals &&
        statusManifest.totals.partial === 330 &&
        statusManifest.totals.supported_async === 54,
      "Status manifest must promote JSON layout/finish paths through autojs6:compat.ui while keeping raw/main-thread APIs blocked.",
    ),
    check(
      "docs-updated",
      includesAll(files.subsetDoc.text, ["P12-08", "P12-09", "`autojs6:compat.ui`"]) &&
        includesAll(files.uiDesign.text, ["P12-09", "compat.ui", "no default global"]) &&
        includesAll(files.loaderDesign.text, ["P12-09", "constrained wrapper"]) &&
        includesAll(files.migration.text, ["compat.ui", "global `ui`"]),
      "UI subset, design, loader, and migration docs must describe the P12-09 constrained facade.",
    ),
    check(
      "surface-manifest",
      surface &&
        surface.status === "provider_poc" &&
        (surface.tasks || []).includes("P12-09") &&
        includesAll(JSON.stringify(surface), [
          "autojs6:compat.ui",
          "rhino_compat.d.ts",
          "verify-rhino-compat-ui-facade.js",
          "verifyNodeRhinoCompatUiFacade",
        ]),
      "Phase 12 surface manifest must record P12-09 compat.ui provider POC evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-09/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatUiFacade") &&
        files.phase12Tasks.text.includes("rhino_compat.d.ts") &&
        files.phase12Tasks.text.includes("typescript-smoke"),
      "Phase 12 task plan must mark P12-09 complete with runtime, type, smoke, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatUiFacade") &&
        files.gradle.text.includes("verify-rhino-compat-ui-facade.js") &&
        files.gradle.text.includes("rhino_compat.d.ts") &&
        files.gradle.text.includes("typescript-smoke") &&
        files.gradle.text.includes("NodeBridgeConformanceInstrumentationTest.kt"),
      "Gradle must register verifyNodeRhinoCompatUiFacade.",
    ),
  ];
}

function entryHas(entries, id, status, nodeModule, nodeName) {
  const entry = entries.get(id);
  return Boolean(entry) &&
    entry.status === status &&
    entry.nodeModule === nodeModule &&
    entry.nodeName === nodeName;
}

function entryBlocked(entries, id) {
  const entry = entries.get(id);
  return Boolean(entry) &&
    entry.status === "blocked_by_design" &&
    entry.nodeModule === null &&
    entry.bridgeModule === null &&
    entry.nodeName === null;
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((checkItem) => checkItem.id === id) || null;
}

function includesAll(text, needles) {
  return needles.every((needle) => text.includes(needle));
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    }
  }
  return options;
}

function read(file) {
  return fs.readFileSync(file, "utf8");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat UI Facade Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
  ];
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("");
  return lines.join("\n");
}

main();
