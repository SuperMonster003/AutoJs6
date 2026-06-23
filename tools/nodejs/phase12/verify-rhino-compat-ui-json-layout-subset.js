#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-ui-json-layout-subset-verification";
const SUPPORTED_TYPES = Object.freeze([
  "Column",
  "Row",
  "Text",
  "Button",
  "Input",
  "Image",
  "ScrollView",
  "List",
  "Switch",
  "Checkbox",
  "Progress",
  "Tabs",
  "Tab",
  "Vertical",
  "Horizontal",
]);
const REQUIRED_API_STATUSES = Object.freeze({
  "ui.layout": "partial_json_descriptor_only",
  "ui.layoutFile": "planned_json_file_only",
  "ui.findById": "blocked_by_design",
  "ui.run": "blocked_by_design",
  "ui.post": "blocked_by_design",
});

const FILES = {
  subsetDoc: "docs/nodejs/design/RHINO_UI_JSON_LAYOUT_SUBSET.md",
  schema: "docs/nodejs/schemas/rhino_ui_json_layout_subset.schema.json",
  fixtures: "docs/nodejs/fixtures/rhino-ui-json-layout-subset-fixtures.json",
  uiDesign: "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md",
  uiBridge: "docs/nodejs/design/UI_BRIDGE.md",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  provider: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeUiProvider.kt",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
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
    schema: "autojs6-node-rhino-compat-ui-json-layout-subset-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatUiJsonLayoutSubset",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      schema: checkById(checks, "schema-contract"),
      fixtures: checkById(checks, "fixtures"),
      runtimeAlignment: checkById(checks, "runtime-provider-alignment"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat UI JSON layout subset verification: " + report.decision.status);
  console.log("Rhino compat UI JSON layout subset policy violations: " + policyViolations.length);
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
  const schema = JSON.parse(files.schema.text);
  const fixtures = JSON.parse(files.fixtures.text);
  const manifest = JSON.parse(files.surfaceManifest.text);
  const surface = (manifest.surfaces || []).find((item) => item.id === "ui.compat");
  const schemaText = JSON.stringify(schema);
  const fixtureText = JSON.stringify(fixtures);
  const migration = schema.properties && schema.properties.rhinoApiMigration
    ? schema.properties.rhinoApiMigration.properties || {}
    : {};
  const limits = schema.properties && schema.properties.limits
    ? schema.properties.limits.properties || {}
    : {};
  return [
    check(
      "subset-doc-boundary",
      includesAll(files.subsetDoc.text, [
        "P12-08",
        "schema-only",
        "ui.showLayout",
        "UiHandle.update",
        "UiHandle.batchUpdate",
        "UiHandle.on",
        "UiHandle.close",
        "ui.run(fn)",
        "ui.post(fn, delay)",
        "blocked_by_design",
        "NativeView",
        "WebView",
      ]),
      "P12-08 doc must lock JSON-only UI migration boundaries and blocked Rhino APIs.",
    ),
    check(
      "schema-contract",
      schema.schema === "autojs6-node-rhino-ui-json-layout-subset-v1" &&
        schemaText.includes("ui.showLayout") &&
        schemaText.includes("UiHandle.update") &&
        schemaText.includes("future autojs6:compat.ui.showLayout") &&
        SUPPORTED_TYPES.every((type) => schemaText.includes(`"${type}"`)),
      "Schema must identify the UI JSON layout subset, entrypoints, and supported component aliases.",
    ),
    check(
      "schema-limits",
      limits.maxJsonChars && limits.maxJsonChars.const === 32768 &&
        limits.maxDepth && limits.maxDepth.const === 16 &&
        limits.maxObjectKeys && limits.maxObjectKeys.const === 64 &&
        limits.maxArrayLength && limits.maxArrayLength.const === 128 &&
        limits.maxBatchPatches && limits.maxBatchPatches.const === 64 &&
        limits.maxDrainEvents && limits.maxDrainEvents.const === 64 &&
        limits.maxEventQueueSize && limits.maxEventQueueSize.const === 128,
      "Schema must lock descriptor, batch, drain, and event queue limits.",
    ),
    check(
      "schema-rhino-api-status",
      Object.entries(REQUIRED_API_STATUSES).every(([api, status]) =>
        migration[api] && migration[api].const === status,
      ),
      "Schema must record ui.layout/ui.layoutFile migration status and keep ui.findById/ui.run/ui.post blocked.",
    ),
    check(
      "schema-raw-object-denial",
      includesAll(files.subsetDoc.text, [
        "XML raw strings",
        "executable attributes",
        "functions",
        "symbols",
        "bigint",
        "undefined",
        "cyclic objects",
        "non-plain object",
        "Android `View`",
        "Activity",
        "Context",
        "Window",
        "Handler",
      ]) &&
        schemaText.includes('"propertyNames"') &&
        schemaText.includes("^on[A-Za-z]") &&
        schemaText.includes('"WebView"') &&
        schemaText.includes('"not"'),
      "P12-08 must reject raw strings, executable attributes, non-JSON objects, raw Android objects, and WebView.",
    ),
    check(
      "fixtures",
      fixtures.schema === "autojs6-node-rhino-ui-json-layout-subset-fixtures-v1" &&
        Array.isArray(fixtures.accepted) &&
        fixtures.accepted.length >= 2 &&
        Array.isArray(fixtures.rejected) &&
        fixtures.rejected.length >= 4 &&
        Array.isArray(fixtures.migrationHints) &&
        fixtures.migrationHints.length >= 5 &&
        fixtureText.includes("simple-form") &&
        fixtureText.includes("tabs-and-list") &&
        fixtureText.includes("xml-raw-string") &&
        fixtureText.includes("executable-attribute") &&
        fixtureText.includes("webview-security-gate") &&
        fixtureText.includes("raw-native-view"),
      "Fixtures must include accepted, rejected, and migration-hint examples for the UI subset.",
    ),
    check(
      "runtime-provider-alignment",
      includesAll(files.runtime.text, [
        "__autojs6_ui_validate_json",
        "depth > 16",
        "value.length > 128",
        "keys.length > 64",
        "text.length > 32768",
        "descriptor must use plain JSON objects",
        "descriptor must not contain cycles",
        "showLayout",
        "batchUpdate patch array exceeds 64 entries",
      ]) &&
        includesAll(files.provider.text, [
          "TYPE_WEB_VIEW",
          "requires a separate security gate",
          "supportedTypes",
          "MAX_DRAIN_EVENTS = 64",
          "MAX_BATCH_PATCHES = 64",
          "MAX_EVENT_QUEUE_SIZE = 128",
        ]),
      "Schema must align with existing JS preflight and Kotlin provider validation limits.",
    ),
    check(
      "existing-design-linked",
      includesAll(files.uiDesign.text, [
        "ui.layout(...)",
        "ui.layoutFile(path)",
        "ui.findById(...)",
        "ui.run(fn)",
        "ui.post(fn)",
        "blocked_by_design",
      ]) &&
        includesAll(files.uiBridge.text, [
          "JSON descriptors passed to `showLayout`",
          "treat `ui.run()` and `ui.post()` as blocked by design",
          "reject `NativeView`",
        ]),
      "Existing S11-23 UI designs must remain linked to the JSON-only subset boundary.",
    ),
    check(
      "surface-manifest",
      surface &&
        ["design_locked", "provider_poc", "promoted"].includes(surface.status) &&
        (surface.tasks || []).includes("P12-08") &&
        includesAll(JSON.stringify(surface), [
          "RHINO_UI_JSON_LAYOUT_SUBSET.md",
          "rhino_ui_json_layout_subset.schema.json",
          "rhino-ui-json-layout-subset-fixtures.json",
          "verifyNodeRhinoCompatUiJsonLayoutSubset",
        ]),
      "Phase 12 surface manifest must record P12-08 UI schema evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-08/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("RHINO_UI_JSON_LAYOUT_SUBSET.md") &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatUiJsonLayoutSubset"),
      "Phase 12 task plan must mark P12-08 complete with schema, fixture, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatUiJsonLayoutSubset") &&
        files.gradle.text.includes("verify-rhino-compat-ui-json-layout-subset.js") &&
        files.gradle.text.includes("RHINO_UI_JSON_LAYOUT_SUBSET.md") &&
        files.gradle.text.includes("rhino_ui_json_layout_subset.schema.json") &&
        files.gradle.text.includes("rhino-ui-json-layout-subset-fixtures.json"),
      "Gradle must register verifyNodeRhinoCompatUiJsonLayoutSubset.",
    ),
  ];
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
    "# Rhino Compat UI JSON Layout Subset Verification",
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
