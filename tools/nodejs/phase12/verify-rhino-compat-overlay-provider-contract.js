#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-overlay-provider-contract-verification";
const CAPABILITIES = Object.freeze([
  "ui.overlay",
  "ui.overlay.permission",
  "ui.overlay.foreground",
  "ui.overlay.packaged",
  "floaty.raw",
  "floaty.touch",
]);
const METHODS = Object.freeze([
  "show",
  "update",
  "drainEvents",
  "close",
  "closeAll",
  "hasPermission",
  "openPermissionSettings",
]);
const EVENT_FIELDS = Object.freeze(["type", "overlayId", "targetId", "eventTime"]);
const LIMITS = Object.freeze([
  "maxActiveOverlays",
  "maxDescriptorBytes",
  "maxDescriptorDepth",
  "maxEventQueueSize",
  "maxDrainBatchSize",
  "maxLifetimeMs",
]);

const FILES = {
  contract: "docs/nodejs/design/OVERLAY_PROVIDER_V1.md",
  schema: "docs/nodejs/schemas/overlay_provider_v1.schema.json",
  fixtures: "docs/nodejs/fixtures/overlay-provider-v1-fixtures.json",
  decision: "docs/nodejs/design/FLOATY_OVERLAY_PHASE12_DECISION.md",
  rhinoFloaty: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  floatyNode: "docs/nodejs/design/FLOATY_NODE.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
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
    schema: "autojs6-node-rhino-compat-overlay-provider-contract-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatOverlayProviderContract",
    capabilities: CAPABILITIES,
    methods: METHODS,
    eventFields: EVENT_FIELDS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      apiContract: checkById(checks, "contract-api"),
      schemaFixture: checkById(checks, "schema-contract"),
      manifestLocked: checkById(checks, "surface-manifest-design-locked"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat overlay provider contract verification: " + report.decision.status);
  console.log("Rhino compat overlay provider policy violations: " + policyViolations.length);
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
  const statusManifest = JSON.parse(files.statusManifest.text);
  const floatyModule = (statusManifest.modules || []).find((module) => module.id === "floaty");
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const floatySurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "floaty.overlay");
  const schemaText = JSON.stringify(schema);
  const fixtureText = JSON.stringify(fixtures);
  const methodProperties = schema.properties && schema.properties.methods && schema.properties.methods.properties
    ? Object.keys(schema.properties.methods.properties)
    : [];
  const capabilityEnums = schema.properties && schema.properties.capabilities && schema.properties.capabilities.items
    ? schema.properties.capabilities.items.enum || []
    : [];
  const limitProperties = schema.$defs && schema.$defs.OverlayLimits && schema.$defs.OverlayLimits.properties
    ? Object.keys(schema.$defs.OverlayLimits.properties)
    : [];
  const overlayEventRequired = schema.$defs && schema.$defs.OverlayEvent
    ? schema.$defs.OverlayEvent.required || []
    : [];
  const matrixFloatyRow = findMatrixRow(files.matrix.text, "floaty");
  return [
    check(
      "contract-api",
      includesAll(files.contract.text, [
        "P12-12A",
        'require("ui.overlay")',
        "P12-13",
        "show(descriptor, options?)",
        "closeAll(options?)",
        "hasPermission(options?)",
        "openPermissionSettings(options?)",
        "OverlayHandle#update",
        "OverlayHandle#drainEvents",
        "OverlayHandle#close",
      ]),
      "Contract doc must lock the ui.overlay module name, async API, and P12-13 fake-provider handoff.",
    ),
    check(
      "contract-descriptor-policy",
      includesAll(files.contract.text, [
        "content",
        "window",
        "disclosure.title",
        "disclosure.text",
        "safe UI JSON subset",
        "Hidden overlays",
        "off-screen placement",
        "fully transparent overlays",
        "WindowManager.LayoutParams",
      ]),
      "Contract doc must define JSON descriptor, disclosure, hidden-overlay rejection, and raw LayoutParams denial.",
    ),
    check(
      "contract-capabilities",
      CAPABILITIES.every((capability) => files.contract.text.includes(capability)) &&
        includesAll(files.contract.text, [
          "base `ui`",
          "never implied by base `ui`",
          "not implied by `ui.overlay`",
          "settings page may open only after an explicit user-triggered API call",
        ]),
      "Contract doc must define explicit overlay capabilities and keep raw/touch capabilities separate.",
    ),
    check(
      "contract-raw-object-denial",
      includesAll(files.contract.text, [
        "Android `WindowManager`",
        "WindowManager.LayoutParams",
        "FloatyService",
        "FloatyWindow",
        "WindowBridge",
        "ViewGroup",
        "NativeView",
        "Context",
        "Activity",
        "Binder",
        "Java objects",
        "Rhino `JsRawWindow`",
      ]),
      "Contract doc must deny raw Android, Java, Rhino, Binder, view, and window objects across the bridge.",
    ),
    check(
      "contract-limits-resource-packaged",
      LIMITS.every((limit) => files.contract.text.includes(limit)) &&
        includesAll(files.contract.text, [
          "NodeBridgeResourceType.OVERLAY_SESSION",
          "owner is the current Node execution id",
          "Close is idempotent",
          "zero active overlay sessions",
          "Packaged APK",
          "SYSTEM_ALERT_WINDOW",
          "Service-process-only hidden overlays are not allowed",
        ]),
      "Contract doc must lock limits, provider-owned resources, cleanup, and packaged policy.",
    ),
    check(
      "contract-type-draft",
      includesAll(files.contract.text, [
        'declare module "ui.overlay"',
        "OverlayDescriptor",
        "OverlayHandle",
        "OverlayPermissionSnapshot",
        "OverlayCloseSummary",
        "openPermissionSettings",
      ]),
      "Contract doc must include a TypeScript declaration draft without promoting runtime types yet.",
    ),
    check(
      "schema-contract",
      schema.properties &&
        schema.properties.schema &&
        schema.properties.schema.const === "autojs6-node-overlay-provider-v1" &&
        schema.properties.module &&
        schema.properties.module.const === "ui.overlay",
      "Schema fixture must identify the ui.overlay provider v1 schema and module.",
    ),
    check(
      "schema-methods",
      METHODS.every((method) => methodProperties.includes(method)) &&
        schemaText.includes('"OverlayDescriptor"') &&
        schemaText.includes('"OverlaySnapshot"') &&
        schemaText.includes('"OverlayPermissionSnapshot"'),
      "Schema must lock all ui.overlay bridge methods and JSON-safe response shapes.",
    ),
    check(
      "schema-capabilities",
      CAPABILITIES.every((capability) => capabilityEnums.includes(capability)) &&
        schemaText.includes('"ui.overlay.permission"') &&
        schemaText.includes('"floaty.raw"') &&
        schemaText.includes('"floaty.touch"'),
      "Schema must enumerate explicit overlay capabilities and reserved raw/touch capabilities.",
    ),
    check(
      "schema-event-fields",
      ["type", "overlayId", "eventTime"].every((field) => overlayEventRequired.includes(field)) &&
        EVENT_FIELDS.every((field) => schemaText.includes(`"${field}"`)),
      "Schema must require JSON overlay event fields and include optional target id.",
    ),
    check(
      "schema-limits",
      LIMITS.every((limit) => limitProperties.includes(limit)) &&
        schemaText.includes('"const":4') &&
        schemaText.includes('"const":65536') &&
        schemaText.includes('"const":16') &&
        schemaText.includes('"const":128') &&
        schemaText.includes('"const":64') &&
        schemaText.includes('"const":3600000'),
      "Schema must lock hard maximums for active overlays, descriptor size/depth, event queues, drains, and lifetime.",
    ),
    check(
      "fixtures",
      fixtures.schema === "autojs6-node-overlay-provider-v1-fixtures" &&
        Array.isArray(fixtures.accepted) &&
        fixtures.accepted.length >= 2 &&
        Array.isArray(fixtures.rejected) &&
        includesAll(fixtureText, [
          "status-panel",
          "readonly-progress",
          "xml-raw-string",
          "executable-attribute",
          "hidden-overlay",
          "missing-disclosure",
          "raw-window-flags",
          "native-view",
          "provider_path_json_descriptor_only",
          "blocked_by_design",
        ]),
      "Fixtures must cover accepted descriptors, rejected unsafe forms, and Rhino migration hints.",
    ),
    check(
      "docs-linked",
      includesAll(files.decision.text, ["P12-12A", "OVERLAY_PROVIDER_V1.md", "overlay provider v1 contract"]) &&
        includesAll(files.rhinoFloaty.text, ["P12-12A", "OVERLAY_PROVIDER_V1.md", "P12-13"]) &&
        includesAll(files.floatyNode.text, ["P12-12A", "OVERLAY_PROVIDER_V1.md", "P12-13"]) &&
        includesAll(files.migration.text, ["P12-12A", "OVERLAY_PROVIDER_V1.md", "P12-13"]) &&
        includesAll(files.securityModel.text, ["P12-12A", "OVERLAY_PROVIDER_V1.md", "ui.overlay"]),
      "Decision, design, migration, and security docs must link the P12-12A provider contract.",
    ),
    check(
      "status-manifest-p12-12a",
      floatyModule &&
        floatyModule.status === "unsupported" &&
        includesAll(JSON.stringify(floatyModule), [
          "P12-12A overlay provider v1 contract locked",
          "P12-13 owns the fake provider POC",
          "ui.overlay",
          "explicit overlay capability",
          "SYSTEM_ALERT_WINDOW",
          "raw-wrapper semantics remain blocked by design",
        ]),
      "Status manifest must record the P12-12A contract while preserving exact Rhino raw-window denials.",
    ),
    check(
      "matrix-updated",
      includesAll(files.matrixGenerator.text, [
        "P12-12A overlay provider v1 contract locked",
        "Implement P12-13 overlay fake provider POC",
        "Complete P12-14 live overlay packaged and cleanup gate",
        "Proceed to P12-15 tasks disposable migration",
      ]) &&
        includesAll(matrixFloatyRow, [
          "| 53 | floaty | floaty | unsupported |",
          "blocked_by_design:2",
          "renamed:1",
          "unsupported:5",
        ]) &&
        (matrixFloatyRow.includes("Implement P12-13 overlay fake provider POC") ||
          matrixFloatyRow.includes("Complete P12-14 live overlay packaged and cleanup gate") ||
          matrixFloatyRow.includes("Proceed to P12-15 tasks disposable migration")),
      "Generated matrix must point floaty to the next provider-path gate after P12-12A locks the provider contract.",
    ),
    check(
      "surface-manifest-design-locked",
      floatySurface &&
        ["design_locked", "provider_poc"].includes(floatySurface.status) &&
        (floatySurface.tasks || []).includes("P12-12A") &&
        includesAll(JSON.stringify(floatySurface), [
          "OVERLAY_PROVIDER_V1.md",
          "overlay_provider_v1.schema.json",
          "overlay-provider-v1-fixtures.json",
          "verify-rhino-compat-overlay-provider-contract.js",
          "verifyNodeRhinoCompatOverlayProviderContract",
        ]) &&
        includesAll(files.surfaceDocs.text, ["floaty.overlay", "P12-12A", "OVERLAY_PROVIDER_V1.md", "P12-13"]),
      "Phase 12 surface manifest must record P12-12A contract evidence while allowing later provider POC promotion.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-12A/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("OVERLAY_PROVIDER_V1.md") &&
        files.phase12Tasks.text.includes("overlay_provider_v1.schema.json") &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatOverlayProviderContract"),
      "Phase 12 task plan must mark P12-12A complete with contract gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatOverlayProviderContract") &&
        files.gradle.text.includes("verify-rhino-compat-overlay-provider-contract.js") &&
        files.gradle.text.includes("OVERLAY_PROVIDER_V1.md") &&
        files.gradle.text.includes("overlay_provider_v1.schema.json") &&
        files.gradle.text.includes("overlay-provider-v1-fixtures.json"),
      "Gradle must register verifyNodeRhinoCompatOverlayProviderContract.",
    ),
  ];
}

function findMatrixRow(markdown, moduleId) {
  return markdown.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Overlay Provider Contract Verification",
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
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-overlay-provider-contract.js [--repo-root dir] [--report-dir dir]");
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
