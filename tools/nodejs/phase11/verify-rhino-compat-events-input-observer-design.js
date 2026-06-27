#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-events-input-observer-design-verification";
const EVENT_DECISION = "s11_21_raw_rhino_event_emitter_blocked";
const KEY_CONSTANT_DECISION = "s11_21_key_constants_pending_facade";
const PROPOSED_CAPABILITIES = Object.freeze([
  "input_observer",
  "input_observer.keys",
  "input_observer.touch",
  "input_observer.intercept",
]);

const FILES = {
  design: "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  resourceRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
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
    schema: "autojs6-node-rhino-compat-events-input-observer-design-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatEventsInputObserverDesign",
    proposedCapabilities: PROPOSED_CAPABILITIES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      eventsBlockedByDesign: checkById(checks, "status-events-blocked"),
      keyConstantsPendingFacade: checkById(checks, "status-events-keys-pending"),
      noRuntimeCapabilityPromotion: checkById(checks, "no-runtime-capability-promotion"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat events/input observer design verification: " + report.decision.status);
  console.log("Rhino compat proposed capabilities: " + PROPOSED_CAPABILITIES.join(", "));
  console.log("Rhino compat events/input observer policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const eventsModule = moduleById.get("events");
  const eventsKeysModule = moduleById.get("events.keys");
  const eventsEntries = eventsModule ? eventsModule.apiEntries || [] : [];
  const keyEntries = eventsKeysModule ? eventsKeysModule.apiEntries || [] : [];
  const matrixEventsRow = findMatrixRow(files.matrix.text, "events");
  const matrixKeysRow = findMatrixRow(files.matrix.text, "events.keys");

  return [
    check(
      "design-core-boundary",
      includesAll(files.design.text, [
        "S11-21",
        "events.__asEmitter__()",
        "blocked_by_design",
        "node:events",
        "Java EventEmitter",
        "ScriptableObject",
        "Thread",
        "MainThreadProxy",
      ]),
      "design doc must block raw Rhino events emitter semantics and point pure JS events to node:events",
    ),
    check(
      "design-input-observer-provider",
      includesAll(files.design.text, [
        "input_observer",
        "observeKeys",
        "observeTouch",
        "drainEvents",
        "close()",
        "NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION",
        "ResourceRegistry",
      ]),
      "design doc must define the future handle-based input_observer provider and cleanup model",
    ),
    check(
      "design-capability-diff",
      PROPOSED_CAPABILITIES.every((capability) => files.design.text.includes(capability)) &&
        files.design.text.includes("S11-21 does not change `NodeBridgePermissionManifest.definedCapabilities`"),
      "design doc must record the proposed capability manifest diff without promoting it in S11-21",
    ),
    check(
      "design-packaged-background-boundary",
      includesAll(files.design.text, [
        "Packaged APK",
        "foreground/user-visible",
        "Service-process-only background key/touch observation is not allowed",
        "Hidden overlay/touch capture is not allowed",
        "input_observer.intercept",
      ]),
      "design doc must define packaged/background and key interception boundaries",
    ),
    check(
      "status-events-blocked",
      eventsModule &&
        eventsModule.status === "blocked_by_design" &&
        eventsModule.asyncMode === "not_applicable" &&
        eventsModule.decision === EVENT_DECISION &&
        Array.isArray(eventsModule.nodeModules) &&
        eventsModule.nodeModules.includes("node:events") &&
        Array.isArray(eventsModule.bridgeModules) &&
        eventsModule.bridgeModules.length === 0 &&
        eventsEntries.length === 2 &&
        eventsEntries.every((entry) => entry.status === "blocked_by_design" && entry.asyncMode === "not_applicable" && entry.decision === EVENT_DECISION),
      "status manifest must mark Rhino events/asEmitter as S11-21 blocked_by_design with node:events as pure-JS migration target",
    ),
    check(
      "status-events-keys-pending",
      eventsKeysModule &&
        eventsKeysModule.status === "unsupported" &&
        eventsKeysModule.asyncMode === "sync" &&
        eventsKeysModule.decision === KEY_CONSTANT_DECISION &&
        keyEntries.length === 13 &&
        keyEntries.every((entry) => entry.status === "unsupported" && entry.asyncMode === "sync" && entry.decision === KEY_CONSTANT_DECISION),
      "status manifest must keep events.keys unsupported while recording the S11-21 pure-constant pending-facade decision",
    ),
    check(
      "matrix-events-row",
      /\|\s*18\s*\|\s*events\s*\|\s*events\s*\|\s*blocked_by_design\s*\|[^|]*node:events/.test(matrixEventsRow) &&
        /blocked_by_design:2/.test(matrixEventsRow),
      "generated matrix must show events as blocked_by_design with node:events surface and two blocked APIs",
    ),
    check(
      "matrix-events-keys-row",
      /\|\s*19\s*\|\s*events\.keys\s*\|\s*keys\s*\|\s*unsupported\s*\|/.test(matrixKeysRow) &&
        /unsupported:13/.test(matrixKeysRow),
      "generated matrix must keep events.keys unsupported with thirteen pending constant APIs",
    ),
    check(
      "security-model-s11-21",
      includesAll(files.securityModel.text, [
        "S11-21",
        "input_observer",
        "events.__asEmitter__()",
        "Java `EventEmitter`",
        "ResourceRegistry-owned subscription cleanup",
        "canonical capability list remains unchanged",
        "silent service-process key/touch listeners",
      ]),
      "security model must document S11-21 event/input observer boundaries and unchanged canonical capabilities",
    ),
    check(
      "migration-guide-s11-21",
      includesAll(files.migration.text, [
        "### Key And Touch Events",
        "events.observeKey()",
        "events.setKeyInterceptionEnabled",
        "events.observeTouch()",
        "node:events",
        "input_observer",
      ]),
      "migration guide must explain pure node:events migration and design-gated Android key/touch observation",
    ),
    check(
      "loader-design-s11-21",
      includesAll(files.loaderDesign.text, [
        "S11-21",
        "does not add `compat.events`",
        "events.__asEmitter__()",
        "input_observer",
        "no `events`",
        "`keys`",
        "`input_observer`",
        "compat type surface",
      ]),
      "compat loader design must record S11-21 as design-only with no events/keys runtime facade",
    ),
    check(
      "task-plan-s11-21-complete",
      /\[x\]\s+S11-21/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("RHINO_EVENTS_INPUT_OBSERVER.md") &&
        files.taskPlan.text.includes("verifyNodeRhinoCompatEventsInputObserverDesign"),
      "Phase 11 task plan must mark S11-21 complete and name the design gate",
    ),
    check(
      "gradle-task",
      files.gradle.text.includes("verifyNodeRhinoCompatEventsInputObserverDesign") &&
        files.gradle.text.includes("verify-rhino-compat-events-input-observer-design.js") &&
        files.gradle.text.includes("RHINO_EVENTS_INPUT_OBSERVER.md"),
      "Gradle must register verifyNodeRhinoCompatEventsInputObserverDesign with the design doc as an input",
    ),
    check(
      "no-runtime-capability-promotion",
      !/\bINPUT_OBSERVER\b/.test(files.permissionManifest.text) &&
        !files.permissionManifest.text.includes("input_observer") &&
        !/\bINPUT_OBSERVER_SUBSCRIPTION\b/.test(files.resourceRegistry.text) &&
        !files.runtime.text.includes("input_observer") &&
        !files.runtime.text.includes("compat.events") &&
        !files.runtime.text.includes("compat.keys"),
      "S11-21 must not promote input_observer capabilities, resource types, or runtime compat events/keys facades",
    ),
  ];
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
    "# Rhino Compat Events/Input Observer Design Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Proposed capabilities: ${report.proposedCapabilities.map((name) => `\`${name}\``).join(", ")}`,
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
      console.log("Usage: node verify-rhino-compat-events-input-observer-design.js [--repo-root dir] [--report-dir dir]");
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
