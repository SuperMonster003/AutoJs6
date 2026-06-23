#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-events-keys-facade-verification";
const DECISION = "p12_03_events_keys_constants_facade";
const KEY_CONSTANTS = Object.freeze({
  home: 3,
  HOME: 3,
  menu: 82,
  MENU: 82,
  back: 4,
  BACK: 4,
  volumeUp: 24,
  volume_up: 24,
  VOLUME_UP: 24,
  volumeDown: 25,
  volume_down: 25,
  VOLUME_DOWN: 25,
});

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  eventsDesign: "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-events-keys-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatEventsKeysFacade",
    keyConstants: KEY_CONSTANTS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacade: checkById(checks, "runtime-events-keys-facade"),
      noObserverPromotion: checkById(checks, "no-input-observer-promotion"),
      manifestPromoted: checkById(checks, "status-manifest-promoted"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat events.keys facade verification: " + report.decision.status);
  console.log("Rhino compat events.keys policy violations: " + policyViolations.length);
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
  const manifest = JSON.parse(files.statusManifest.text);
  const modules = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const eventsKeys = modules.get("events.keys");
  const keyEntries = eventsKeys ? eventsKeys.apiEntries || [] : [];
  const runtime = files.runtime.text;
  const keysBlock = extractBetween(runtime, "const __autojs6_rhino_compat_key_constants", "  function __autojs6_rhino_compat_error");
  return [
    check(
      "runtime-key-constant-values",
      Object.entries(KEY_CONSTANTS).every(([key, value]) => keysBlock.includes(`${key}: ${value}`)),
      "Runtime must expose the Rhino events.keys aliases with Android KeyEvent numeric values.",
    ),
    check(
      "runtime-events-keys-facade",
      includesAll(runtime, [
        "__autojs6_rhino_compat_key_constants",
        "__autojs6_rhino_compat_events",
        "keys: __autojs6_rhino_compat_key_constants",
        "events: __autojs6_rhino_compat_events",
        '"P12-03"',
      ]),
      "autojs6:compat must expose compat.keys and compat.events.keys and mark P12-03 implemented.",
    ),
    check(
      "runtime-global-policy",
      runtime.includes('"keys"') &&
        !globalListContains(runtime, "events") &&
        runtime.includes("defaultGlobalInjection: false"),
      "Explicit global install may expose keys but must not install a Rhino events global by default.",
    ),
    check(
      "runtime-node-events-not-overridden",
      runtime.includes('if (name === "events" || name === "node:events")') &&
        runtime.includes('return "events";') &&
        !runtime.includes('name === "events.keys"'),
      "Node core events must remain the events module; no bare events.keys module alias should be registered.",
    ),
    check(
      "types-events-keys",
      includesAll(files.types.text, [
        "RhinoEventKeysCompatModule",
        "readonly keys: RhinoEventKeysCompatModule",
        "readonly events: RhinoEventsCompatModule",
        "readonly volume_down: 25",
      ]),
      "Type declarations must expose compat.keys and compat.events.keys with readonly constants.",
    ),
    check(
      "type-readme-events-keys",
      files.typeReadme.text.includes("P12-03") &&
        files.typeReadme.text.includes("compat.keys") &&
        files.typeReadme.text.includes("compat.events.keys"),
      "Type README must document the P12-03 events.keys type surface.",
    ),
    check(
      "status-manifest-promoted",
      eventsKeys &&
        eventsKeys.status === "supported" &&
        eventsKeys.asyncMode === "sync" &&
        eventsKeys.decision === DECISION &&
        Array.isArray(eventsKeys.nodeModules) &&
        eventsKeys.nodeModules.includes("autojs6:compat") &&
        keyEntries.length === Object.keys(KEY_CONSTANTS).length + 1 &&
        keyEntries.every((entry) => entry.status === "supported" && entry.asyncMode === "sync" && entry.decision === DECISION),
      "Status manifest must promote events.keys and all aliases to supported sync constants.",
    ),
    check(
      "matrix-promoted",
      /\|\s*19\s*\|\s*events\.keys\s*\|\s*keys\s*\|\s*supported\s*\|\s*autojs6:compat\s*\|\s*supported:13/.test(files.matrix.text) &&
        includesAll(files.matrix.text, [
          "### events.keys",
          "- Status: `supported`",
          "- Node surface: autojs6:compat",
          "- API status: supported:13",
        ]),
      "Generated compatibility matrix must show events.keys as supported through autojs6:compat.",
    ),
    check(
      "design-docs-updated",
      includesAll(files.loaderDesign.text, ["P12-03", "compat.events.keys", "compat.keys"]) &&
        includesAll(files.eventsDesign.text, ["P12-03", "compat.events.keys", "compat.keys", "does not add key observers"]),
      "Design docs must document the P12-03 pure-constant facade and preserved observer boundary.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-03/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatEventsKeysFacade"),
      "Phase 12 task plan must mark P12-03 complete with gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatEventsKeysFacade") &&
        files.gradle.text.includes("verify-rhino-compat-events-keys-facade.js"),
      "Gradle must register verifyNodeRhinoCompatEventsKeysFacade.",
    ),
    check(
      "no-input-observer-promotion",
      !runtime.includes("observeKeys") &&
        !runtime.includes("input_observer") &&
        !runtime.includes("compat.events.observe"),
      "P12-03 must not promote input_observer, key listeners, touch listeners, or observer facades.",
    ),
  ];
}

function globalListContains(runtime, name) {
  const match = runtime.match(/__autojs6_rhino_compat_basic_global_names\s*=\s*Object\.freeze\(\[([^\]]*)\]\)/);
  return match ? match[1].includes(`"${name}"`) : false;
}

function extractBetween(text, startNeedle, endNeedle) {
  const start = text.indexOf(startNeedle);
  if (start < 0) {
    return "";
  }
  const end = text.indexOf(endNeedle, start);
  return end > start ? text.slice(start, end) : text.slice(start);
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
    "# Rhino Compat Events.Keys Facade Verification",
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
      console.log("Usage: node verify-rhino-compat-events-keys-facade.js [--repo-root dir] [--report-dir dir]");
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
