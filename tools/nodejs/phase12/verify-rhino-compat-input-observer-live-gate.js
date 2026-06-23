#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-input-observer-live-gate-verification";

const FILES = {
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  accessibilityService: "app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  instrumentation: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  contract: "docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md",
  eventsDesign: "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
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
    schema: "autojs6-node-rhino-compat-input-observer-live-gate-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatInputObserverLiveGate",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      liveProvider: checkById(checks, "live-provider"),
      cleanup: checkById(checks, "service-destroy-cleanup"),
      packagedPolicy: checkById(checks, "packaged-policy"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat input_observer live gate verification: " + report.decision.status);
  console.log("Rhino compat input_observer live gate policy violations: " + policyViolations.length);
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
      "live-provider",
      includesAll(files.protocol.text, [
        "attachAccessibilityInputObserver",
        "AutoJsAccessibilityService.instance",
        "OnKeyListener",
        "queueKeyEvent",
        "ControlledInputObserverPolicy.keyNameForKeyCode",
        "ControlledInputObserverPolicy.actionNameFor",
        "an operational AutoJs6 accessibility service with key observation enabled",
      ]),
      "P12-06 must register an accessibility OnKeyListener and enqueue only JSON-safe key event snapshots.",
    ),
    check(
      "source-availability",
      includesAll(files.protocol.text, [
        "val livePolicyError = inputObserverLivePolicyError(request)",
        "livePolicyError == null && accessibilityBridgeOperational()",
        "\"accessibility\"",
        "available = accessibilityAvailable",
        "livePolicyError.message",
        "accessibility service is not operational",
      ]),
      "getAvailableSources must expose accessibility live-source availability without claiming it when service is missing.",
    ),
    check(
      "service-destroy-cleanup",
      includesAll(files.accessibilityService.text, [
        "disconnectCallbacks",
        "addDisconnectCallback",
        "removeDisconnectCallback",
        "notifyDisconnectCallbacks",
      ]) &&
        includesAll(files.protocol.text, [
          "AccessibilityServiceCallback",
          "resourceRegistry.close(subscriptionId, \"input_observer.accessibility_service_destroy\")",
          "service.onKeyObserver.removeListener(listener)",
          "AutoJsAccessibilityService.removeDisconnectCallback(disconnectCallback)",
        ]),
      "Accessibility service destroy must close live subscriptions through the same resource cleanup path.",
    ),
    check(
      "raw-event-boundary",
      includesAll(files.protocol.text, [
        ".put(\"key\", keyName)",
        ".put(\"keyCode\", keyCode)",
        ".put(\"action\", actionName)",
        ".put(\"eventTime\", event.eventTime)",
        ".put(\"repeatCount\", event.repeatCount)",
      ]) &&
        !files.runtime.text.includes("KeyEvent") &&
        !files.runtime.text.includes("MotionEvent"),
      "Raw Android KeyEvent/MotionEvent objects must not cross into the JS runtime wrapper.",
    ),
    check(
      "provider-health",
      includesAll(files.providerRegistry.text, [
        "inputObserverStatus",
        "liveSourceAvailable",
        "\"accessibility\"",
        "packagedPolicy",
        "P12-07 cleanup/security gate covers bounded queues",
      ]),
      "Provider health must report live accessibility source state and packaged policy constraints.",
    ),
    check(
      "instrumentation-live-gate",
      includesAll(files.instrumentation.text, [
        "kotlinInputObserverAccessibilityLiveSourceHasStableGate",
        "source\":\"accessibility\"",
        "NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED",
        "NodeBridgeErrorCategory.UNAVAILABLE",
      ]),
      "Instrumentation must cover the accessibility live source success/unavailable gate.",
    ),
    check(
      "packaged-policy",
      includesAll(files.contract.text, ["P12-06", "foreground/user-visible"]) &&
        includesAll(files.securityModel.text, ["P12-06", "foreground/user-visible"]) &&
        includesAll(files.migration.text, ["source: \"accessibility\"", "accessibility service"]),
      "Docs must keep packaged/background live observation constrained until P12-07 smoke or rejection evidence.",
    ),
    check(
      "surface-manifest-live-gate",
      surface &&
        ["provider_poc", "promoted"].includes(surface.status) &&
        (surface.tasks || []).includes("P12-06") &&
        includesAll(JSON.stringify(surface), [
          "source: \\\"accessibility\\\"",
          "verify-rhino-compat-input-observer-live-gate.js",
          "verifyNodeRhinoCompatInputObserverLiveGate",
        ]),
      "Surface manifest must include P12-06 live-gate evidence and accessibility runtime surface.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-06/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatInputObserverLiveGate") &&
        files.phase12Tasks.text.includes("accessibility live source"),
      "Phase 12 task plan must mark P12-06 complete with live-gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatInputObserverLiveGate") &&
        files.gradle.text.includes("verify-rhino-compat-input-observer-live-gate.js") &&
        files.gradle.text.includes("AccessibilityService.kt"),
      "Gradle must register verifyNodeRhinoCompatInputObserverLiveGate.",
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
    "# Rhino Compat Input Observer Live Gate Verification",
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
