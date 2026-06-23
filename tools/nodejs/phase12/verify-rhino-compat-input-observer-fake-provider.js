#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-input-observer-fake-provider-verification";
const METHODS = Object.freeze(["observeKeys", "drainEvents", "close", "getAvailableSources"]);
const CAPABILITIES = Object.freeze(["INPUT_OBSERVER", "INPUT_OBSERVER_KEYS", "INPUT_OBSERVER_TOUCH", "INPUT_OBSERVER_INTERCEPT"]);

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  types: "docs/nodejs/types/autojs6-node/input_observer.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  contract: "docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md",
  eventsDesign: "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  instrumentation: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt",
  conformance: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  providerRegistryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
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
    schema: "autojs6-node-rhino-compat-input-observer-fake-provider-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatInputObserverFakeProvider",
    methods: METHODS,
    capabilities: CAPABILITIES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeSurface: checkById(checks, "runtime-module"),
      bridgeRegistry: checkById(checks, "kotlin-bridge-registry"),
      fakeTransportTest: checkById(checks, "instrumentation-fake-provider"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat input_observer fake provider verification: " + report.decision.status);
  console.log("Rhino compat input_observer fake provider policy violations: " + policyViolations.length);
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
  const manifest = JSON.parse(files.surfaceManifest.text);
  const surface = (manifest.surfaces || []).find((item) => item.id === "input_observer");
  return [
    check(
      "runtime-module",
      includesAll(files.runtime.text, [
        "__autojs6_limited_input_observer",
        "__autojs6_input_observer_policy",
        'if (name === "input_observer")',
        'return __autojs6_limited_input_observer();',
        '"input_observer"',
        '"observeKeys"',
        '"drainEvents"',
        '"getAvailableSources"',
        '"input_observer.keys"',
      ]),
      "Runtime must expose require(\"input_observer\") with observeKeys/drainEvents/close/getAvailableSources and scoped permissions.",
    ),
    check(
      "runtime-json-safe-handle",
      includesAll(files.runtime.text, [
        "__autojs6_input_observer_freeze_event",
        "Object.freeze",
        "keyCode",
        "eventTime",
        "repeatCount",
        "state.closed",
      ]) &&
        !files.runtime.text.includes("KeyEvent") &&
        !files.runtime.text.includes("MotionEvent"),
      "Runtime wrapper must return frozen JSON-safe events and opaque subscription handles without raw Android event types.",
    ),
    check(
      "kotlin-bridge-registry",
      includesAll(files.protocol.text, [
        "const val INPUT_OBSERVER = \"input_observer\"",
        "val inputObserverMethods = setOf(\"observeKeys\", \"drainEvents\", \"close\", \"getAvailableSources\")",
        "dispatchInputObserver",
        "ControlledInputObserverSubscription",
        "descriptor.source == \"fake\"",
        "attachAccessibilityInputObserver",
      ]),
      "Kotlin bridge registry must register input_observer methods and keep the fake source path available.",
    ),
    check(
      "permission-manifest",
      CAPABILITIES.every((name) => files.permissionManifest.text.includes(`const val ${name}`)) &&
        includesAll(files.permissionManifest.text, [
          "NodeBridgeModules.INPUT_OBSERVER",
          "getAvailableSources",
          "listOf(INPUT_OBSERVER, INPUT_OBSERVER_KEYS)",
        ]),
      "Permission manifest must define input_observer capabilities and require key capability for observe/drain/close.",
    ),
    check(
      "resource-registry",
      files.resourceRegistry.text.includes("INPUT_OBSERVER_SUBSCRIPTION") &&
        files.protocol.text.includes("NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION") &&
        files.protocol.text.includes("inputObserverSubscriptions.clear()"),
      "Resource registry must own input_observer subscription cleanup.",
    ),
    check(
      "provider-registry",
      includesAll(files.providerRegistry.text, [
        "INPUT_OBSERVER_PROVIDER",
        "NodeBridgeModules.INPUT_OBSERVER",
        "NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS",
        "availableSource",
        "fake",
        "liveSource",
        "accessibility",
      ]),
      "Provider registry must report fake and accessibility source health for input_observer.",
    ),
    check(
      "types",
      includesAll(files.types.text, [
        'declare module "input_observer"',
        "InputObserverKeyEvent",
        "InputObserverSubscription",
        "observeKeys",
        "getAvailableSources",
        "readonly keyCode: number",
        "readonly eventTime: number",
      ]) &&
        files.typeReadme.text.includes("`input_observer` is a P12 provider POC"),
      "Type declarations and README must expose the input_observer POC surface.",
    ),
    check(
      "instrumentation-fake-provider",
      includesAll(files.instrumentation.text, [
        "NodeInputObserverBridgeInstrumentationTest",
        "kotlinInputObserverRegistryManifestAndFakeProviderAreStable",
        "jsInputObserverUsesFakeTransportAndBlocksShadowing",
        "jsInputObserverRequiresManifestCapabilityBeforeDispatch",
        "bridge.__test.setTransport",
        "NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED",
        "input_observer.pending=0",
      ]),
      "Instrumentation test must cover fake transport, shadowing, lifecycle close, and manifest denial.",
    ),
    check(
      "test-registry-updates",
      files.conformance.text.includes("NodeBridgeModules.INPUT_OBSERVER") &&
        files.providerRegistryTest.text.includes("input_observer") &&
        files.providerRegistryTest.text.includes("NodeBridgeProviderStatus.DEGRADED") &&
        files.providerRegistryTest.text.includes("NodeBridgeProviderStatus.AVAILABLE"),
      "Existing conformance/provider registry tests must include input_observer.",
    ),
    check(
      "docs-updated",
      includesAll(files.contract.text, ["P12-05", "fake provider"]) &&
        includesAll(files.eventsDesign.text, ["P12-05", "fake provider"]) &&
        includesAll(files.loaderDesign.text, ["P12-05", 'require("input_observer")']) &&
        includesAll(files.securityModel.text, ["P12-05", "fake-source"]) &&
        includesAll(files.migration.text, ["P12-05", "input_observer", "fake"]),
      "Design, security, and migration docs must document fake-source POC and live-source boundary.",
    ),
    check(
      "surface-manifest-provider-poc",
      surface &&
        ["provider_poc", "promoted"].includes(surface.status) &&
        (surface.tasks || []).includes("P12-05") &&
        includesAll(JSON.stringify(surface), [
          "input_observer.d.ts",
          "NodeInputObserverBridgeInstrumentationTest",
          "verifyNodeRhinoCompatInputObserverFakeProvider",
        ]),
      "Surface manifest must mark input_observer as provider_poc with P12-05 evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-05/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatInputObserverFakeProvider") &&
        files.phase12Tasks.text.includes("NodeInputObserverBridgeInstrumentationTest"),
      "Phase 12 task plan must mark P12-05 complete with fake-provider evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatInputObserverFakeProvider") &&
        files.gradle.text.includes("verify-rhino-compat-input-observer-fake-provider.js") &&
        files.gradle.text.includes("NodeInputObserverBridgeInstrumentationTest.kt"),
      "Gradle must register verifyNodeRhinoCompatInputObserverFakeProvider.",
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
    "# Rhino Compat Input Observer Fake Provider Verification",
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
      console.log("Usage: node verify-rhino-compat-input-observer-fake-provider.js [--repo-root dir] [--report-dir dir]");
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
