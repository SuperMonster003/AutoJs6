#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-input-observer-security-verification";

const FILES = {
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  accessibilityService: "app/src/main/java/org/autojs/autojs/core/accessibility/AccessibilityService.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  instrumentation: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInputObserverBridgeInstrumentationTest.kt",
  report: "docs/nodejs/reports/phase12/RHINO_INPUT_OBSERVER_SECURITY.md",
  contract: "docs/nodejs/design/INPUT_OBSERVER_PROVIDER_V1.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
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
    schema: "autojs6-node-rhino-compat-input-observer-security-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatInputObserverSecurity",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      cleanup: checkById(checks, "cleanup-diagnostics"),
      queueOverflow: checkById(checks, "bounded-queue-overflow"),
      packagedRejection: checkById(checks, "packaged-background-rejection"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat input_observer security verification: " + report.decision.status);
  console.log("Rhino compat input_observer security policy violations: " + policyViolations.length);
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
      "cleanup-diagnostics",
      includesAll(files.protocol.text, [
        "embedded_script.input_observer_pending_event_count",
        "embedded_script.input_observer_dropped_event_count",
        "embedded_script.input_observer_live_listener_count",
        "pendingEventCount()",
        "droppedEventCount()",
        "liveListenerCount()",
        "markLiveListenerAttached()",
        "markLiveListenerDetached()",
      ]) &&
        includesAll(files.accessibilityService.text, [
          "disconnectCallbacks",
          "notifyDisconnectCallbacks",
        ]),
      "Provider must expose cleanup diagnostics and clear live listeners through service disconnect callbacks.",
    ),
    check(
      "bounded-queue-overflow",
      includesAll(files.protocol.text, [
        "while (events.size >= maxQueueSize)",
        "droppedCount.incrementAndGet()",
        "descriptor.keys.forEach",
        "descriptor.actions.forEach",
        ".put(\"pendingEventCount\", pendingEventCount())",
        ".put(\"droppedCount\", droppedCount.get())",
      ]),
      "Queue overflow must be bounded, drop oldest events, and report dropped/pending counts.",
    ),
    check(
      "unsafe-observer-denials",
      includesAll(files.runtime.text, [
        "input_observer.\" + methodName + \" requires the separate input_observer.intercept provider gate",
        "source must be fake or accessibility",
      ]) &&
        includesAll(files.protocol.text, [
          "input_observer key interception requires the separate input_observer.intercept provider gate",
          "inputObserverInvalidSourceError",
          "val inputObserverMethods = setOf(\"observeKeys\", \"drainEvents\", \"close\", \"getAvailableSources\")",
        ]) &&
        !files.protocol.text.includes("ShellKeyObserver") &&
        !files.protocol.text.includes("InputEventObserver") &&
        !files.protocol.text.includes("DisplayOverOtherAppsPermission") &&
        !files.protocol.text.includes("Floaty"),
      "Input observer must reject intercept/touch/unknown sources and avoid shell, input-event, or overlay fallback paths.",
    ),
    check(
      "packaged-background-rejection",
      includesAll(files.protocol.text, [
        "inputObserverLivePolicyError",
        "MODE_ONE_SHOT",
        "MODE_INTERACTIVE_LONG_RUNNING",
        "LAUNCH_SURFACE_INTERACTIVE_SESSION",
        "active one-shot script or an interactive visible session",
      ]) &&
        includesAll(files.executionModePolicy.text, [
          "\"input_observer\"",
          "MODE_SCHEDULED",
          "scheduled_runner",
        ]) &&
        includesAll(files.providerRegistry.text, [
          "scheduled/background launch surfaces reject accessibility live observation",
          "an active one-shot script or interactive visible session",
        ]),
      "Accessibility live observation must reject scheduled/background launch surfaces and document packaged policy.",
    ),
    check(
      "instrumentation-security",
      includesAll(files.instrumentation.text, [
        "kotlinInputObserverCleanupDiagnosticsAndQueueOverflowAreBounded",
        "kotlinInputObserverRejectsUnsafeObserverModesAndBackgroundLiveSource",
        "embedded_script.input_observer_pending_event_count",
        "embedded_script.input_observer_dropped_event_count",
        "embedded_script.input_observer_live_listener_count",
        "intercept",
        "source\":\"shell\"",
        "MODE_SCHEDULED",
      ]),
      "Instrumentation must cover cleanup diagnostics, bounded overflow, unsafe path denial, and scheduled live-source rejection.",
    ),
    check(
      "security-report",
      includesAll(files.report.text, [
        "Packaged Rejection Report",
        "scheduled/background",
        "stable bridge denial",
        "shell/root",
        "hidden overlay",
        "verifyNodeRhinoCompatInputObserverSecurity",
      ]),
      "P12-07 must publish a security report with packaged/background rejection evidence.",
    ),
    check(
      "docs-updated",
      includesAll(files.contract.text, ["P12-07", "packaged"]) &&
        includesAll(files.securityModel.text, ["P12-07", "scheduled/background"]) &&
        includesAll(files.migration.text, ["key interception", "touch observation"]),
      "Contract, security, and migration docs must reflect the P12-07 security/packaged boundary.",
    ),
    check(
      "surface-manifest-evidence",
      surface &&
        (surface.tasks || []).includes("P12-07") &&
        includesAll(JSON.stringify(surface), [
          "RHINO_INPUT_OBSERVER_SECURITY.md",
          "verify-rhino-compat-input-observer-security.js",
          "verifyNodeRhinoCompatInputObserverSecurity",
        ]),
      "Surface manifest must include P12-07 security evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-07/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatInputObserverSecurity") &&
        files.phase12Tasks.text.includes("RHINO_INPUT_OBSERVER_SECURITY.md"),
      "Phase 12 task plan must mark P12-07 complete with security gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatInputObserverSecurity") &&
        files.gradle.text.includes("verify-rhino-compat-input-observer-security.js") &&
        files.gradle.text.includes("RHINO_INPUT_OBSERVER_SECURITY.md"),
      "Gradle must register verifyNodeRhinoCompatInputObserverSecurity.",
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
    "# Rhino Compat Input Observer Security Verification",
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
