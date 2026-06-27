#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-input-observer-contract-verification";
const CAPABILITIES = Object.freeze([
  "input_observer",
  "input_observer.keys",
  "input_observer.touch",
  "input_observer.intercept",
]);
const METHODS = Object.freeze(["observeKeys", "drainEvents", "close", "getAvailableSources"]);
const EVENT_FIELDS = Object.freeze(["source", "key", "keyCode", "action", "eventTime", "repeatCount"]);
const LIMITS = Object.freeze(["maxActiveSubscriptions", "maxQueueSize", "maxDrainBatchSize", "maxEventBytes", "maxTimeoutMs"]);

const FILES = {
  contract: "docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md",
  schema: "docs/nodejs/schemas/input_observer_provider_v1.schema.json",
  eventsDesign: "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
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
    schema: "autojs6-node-rhino-compat-input-observer-contract-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatInputObserverContract",
    capabilities: CAPABILITIES,
    methods: METHODS,
    eventFields: EVENT_FIELDS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      apiContract: checkById(checks, "contract-api"),
      schemaFixture: checkById(checks, "schema-fixture"),
      manifestLocked: checkById(checks, "surface-manifest-design-locked"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat input_observer contract verification: " + report.decision.status);
  console.log("Rhino compat input_observer policy violations: " + policyViolations.length);
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
  const manifest = JSON.parse(files.surfaceManifest.text);
  const surface = (manifest.surfaces || []).find((item) => item.id === "input_observer");
  const schemaText = JSON.stringify(schema);
  const schemaMethods = schema.properties && schema.properties.methods && schema.properties.methods.properties
    ? Object.keys(schema.properties.methods.properties)
    : [];
  const keyEventRequired = schema.$defs && schema.$defs.InputObserverKeyEvent
    ? schema.$defs.InputObserverKeyEvent.required || []
    : [];
  const limitProperties = schema.$defs && schema.$defs.InputObserverLimits && schema.$defs.InputObserverLimits.properties
    ? Object.keys(schema.$defs.InputObserverLimits.properties)
    : [];
  return [
    check(
      "contract-api",
      includesAll(files.contract.text, [
        "P12-04",
        'require("input_observer")',
        "observeKeys",
        "drainEvents",
        "close",
        "getAvailableSources",
        "Promise<InputObserverSubscription>",
        "P12-05",
        "fake provider",
      ]),
      "Contract doc must lock the input_observer module name, async API, and P12-05 fake-provider handoff.",
    ),
    check(
      "contract-event-schema",
      EVENT_FIELDS.every((field) => files.contract.text.includes(field)) &&
        includesAll(files.contract.text, ["type", "key", "volume_up", "keyCode", "eventTime", "repeatCount"]),
      "Contract doc must define the required key event JSON snapshot fields.",
    ),
    check(
      "contract-capabilities",
      CAPABILITIES.every((capability) => files.contract.text.includes(capability)) &&
        files.contract.text.includes("intercept` must be separate") &&
        files.contract.text.includes("No Android manifest permission is injected automatically"),
      "Contract doc must define the capability diff and keep input_observer.intercept separate.",
    ),
    check(
      "contract-limits",
      LIMITS.every((limit) => files.contract.text.includes(limit)) &&
        includesAll(files.contract.text, ["4", "8", "64", "256", "32", "128", "2048", "4096", "10000", "60000"]),
      "Contract doc must lock subscription, queue, drain, event-size, timeout, and close limits.",
    ),
    check(
      "contract-resource-and-packaged-policy",
      includesAll(files.contract.text, [
        "NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION",
        "owner is the current Node execution id",
        "close() is idempotent",
        "zero active input observer subscriptions",
        "foreground/user-visible",
        "Packaged APK",
        "Service-process-only background key/touch observation is not allowed",
        "Hidden overlay/touch capture is not allowed",
      ]),
      "Contract doc must define resource cleanup and packaged/background denial policy.",
    ),
    check(
      "contract-raw-object-denial",
      includesAll(files.contract.text, [
        "Android `KeyEvent`",
        "MotionEvent",
        "listener",
        "Handler",
        "Looper",
        "Context",
        "Java object",
        "Thread",
        "MainThreadProxy",
      ]),
      "Contract doc must deny raw Android, Java, Rhino, listener, and thread objects across the bridge.",
    ),
    check(
      "contract-type-draft",
      includesAll(files.contract.text, [
        'declare module "input_observer"',
        "InputObserverKeyDescriptor",
        "InputObserverKeyEvent",
        "InputObserverSubscription",
        "drainEvents(options?: InputObserverDrainOptions)",
        "close(options?: InputObserverDrainOptions)",
      ]),
      "Contract doc must include a TypeScript declaration draft without promoting the runtime .d.ts yet.",
    ),
    check(
      "schema-fixture",
      schema.schema === "autojs6-node-input-observer-provider-v1" ||
        (schema.examples || []).some((example) => example.schema === "autojs6-node-input-observer-provider-v1"),
      "Schema fixture must identify the input_observer provider v1 schema.",
    ),
    check(
      "schema-module-methods",
      schema.properties &&
        schema.properties.module &&
        schema.properties.module.const === "input_observer" &&
        METHODS.every((method) => schemaMethods.includes(method)),
      "Schema fixture must lock the input_observer module and all bridge methods.",
    ),
    check(
      "schema-capabilities",
      CAPABILITIES.every((capability) => schemaText.includes(capability)) &&
        schemaText.includes('"input_observer.intercept"'),
      "Schema fixture must enumerate all input_observer capability names including intercept.",
    ),
    check(
      "schema-event-fields",
      EVENT_FIELDS.every((field) => keyEventRequired.includes(field)) &&
        keyEventRequired.includes("type") &&
        schemaText.includes('"fake"') &&
        schemaText.includes('"accessibility"'),
      "Schema fixture must require JSON key event fields and source ids.",
    ),
    check(
      "schema-limits",
      LIMITS.every((limit) => limitProperties.includes(limit)) &&
        schemaText.includes('"const":8') &&
        schemaText.includes('"const":256') &&
        schemaText.includes('"const":128') &&
        schemaText.includes('"const":4096') &&
        schemaText.includes('"const":60000'),
      "Schema fixture must lock hard maximums for subscriptions, queues, drains, event bytes, and timeouts.",
    ),
    check(
      "events-design-linked",
      includesAll(files.eventsDesign.text, [
        "P12-04",
        "INPUT_OBSERVER_PROVIDER_V1.md",
        "source",
        "key",
        "keyCode",
        "action",
        "eventTime",
        "repeatCount",
      ]),
      "Existing Rhino events/input observer design must link to the P12-04 contract and canonical event fields.",
    ),
    check(
      "loader-design-linked",
      includesAll(files.loaderDesign.text, [
        "P12-04",
        "input_observer provider v1 contract",
        "does not add `require(\"input_observer\")`",
      ]),
      "Compat loader design must record that P12-04 is contract-only and does not add the runtime module.",
    ),
    check(
      "surface-manifest-design-locked",
      surface &&
        ["design_locked", "provider_poc", "promoted"].includes(surface.status) &&
        (surface.tasks || []).includes("P12-04") &&
        includesAll(JSON.stringify(surface), [
          "INPUT_OBSERVER_PROVIDER_V1.md",
          "input_observer_provider_v1.schema.json",
          "verifyNodeRhinoCompatInputObserverContract",
        ]),
      "Phase 12 surface manifest must record input_observer P12-04 as design-locked with evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-04/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatInputObserverContract") &&
        files.phase12Tasks.text.includes("INPUT_OBSERVER_PROVIDER_V1.md"),
      "Phase 12 task plan must mark P12-04 complete with contract gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatInputObserverContract") &&
        files.gradle.text.includes("verify-rhino-compat-input-observer-contract.js") &&
        files.gradle.text.includes("INPUT_OBSERVER_PROVIDER_V1.md") &&
        files.gradle.text.includes("input_observer_provider_v1.schema.json"),
      "Gradle must register verifyNodeRhinoCompatInputObserverContract.",
    ),
  ];
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
    "# Rhino Compat Input Observer Contract Verification",
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
      console.log("Usage: node verify-rhino-compat-input-observer-contract.js [--repo-root dir] [--report-dir dir]");
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
