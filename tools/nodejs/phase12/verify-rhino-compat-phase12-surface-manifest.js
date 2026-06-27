#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase12-surface-manifest-verification";
const VALID_STATUSES = Object.freeze(["planned", "design_locked", "provider_poc", "promoted", "rejected"]);
const REQUIRED_TASKS = Object.freeze(
  Array.from({ length: 36 }, (_, index) => `P12-${String(index).padStart(2, "0")}`)
    .filter((task) => task !== "P12-12")
    .concat(["P12-12A", "P12-12B"]),
);
const REQUIRED_SURFACES = Object.freeze([
  "phase12.baseline",
  "phase12.surface_manifest",
  "events.keys",
  "input_observer",
  "ui.compat",
  "floaty.overlay",
  "tasks.compat",
  "threads.replacement",
  "rhino.json_only_shim",
  "barcode.qrcode",
  "unsupported.utility_triage",
  "dual_engine.device_evidence",
  "phase12.summary",
]);

const FILES = {
  manifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  manifestDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  types: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const manifest = JSON.parse(files.manifest.text);
  const statusCounts = countBy(manifest.surfaces || [], (surface) => surface.status);
  const report = {
    schema: "autojs6-node-rhino-compat-phase12-surface-manifest-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatPhase12SurfaceManifest",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    statusCounts,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      manifestComplete: checkById(checks, "manifest-covers-required-surfaces"),
      taskCoverage: checkById(checks, "manifest-covers-phase12-tasks"),
      promotedEvidence: checkById(checks, "promoted-surfaces-have-evidence"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 12 surface manifest verification: " + report.decision.status);
  console.log("Phase 12 surface manifest policy violations: " + policyViolations.length);
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
  const manifest = JSON.parse(files.manifest.text);
  const surfaces = manifest.surfaces || [];
  const surfaceById = new Map(surfaces.map((surface) => [surface.id, surface]));
  const taskCoverage = new Map();
  surfaces.forEach((surface) => {
    (surface.tasks || []).forEach((task) => taskCoverage.set(task, surface.id));
  });
  const promoted = surfaces.filter((surface) => surface.status === "promoted");
  return [
    check(
      "schema",
      manifest.schema === "autojs6-node-rhino-compat-phase12-surface-manifest-v1" &&
        manifest.phase === "Phase 12: Rhino Compatibility Runtime Promotion",
      "Manifest must use the Phase 12 surface schema and phase label.",
    ),
    check(
      "valid-statuses",
      surfaces.length > 0 && surfaces.every((surface) => VALID_STATUSES.includes(surface.status)),
      "Every surface must use a valid Phase 12 execution status.",
    ),
    check(
      "manifest-covers-required-surfaces",
      REQUIRED_SURFACES.every((id) => surfaceById.has(id)),
      "Manifest must cover all required Phase 12 surface groups.",
    ),
    check(
      "manifest-covers-phase12-tasks",
      REQUIRED_TASKS.every((task) => taskCoverage.has(task)) &&
        ["P12-12A", "P12-12B"].every((task) => taskCoverage.has(task)),
      "Manifest must map every P12 task, including branch tasks P12-12A/P12-12B, to a surface.",
    ),
    check(
      "surface-docs",
      includesAll(files.manifestDocs.text, [
        "# Phase 12 Surface Manifest",
        "`planned`",
        "`design_locked`",
        "`provider_poc`",
        "`promoted`",
        "`rejected`",
      ]),
      "Manifest documentation must define the Phase 12 status vocabulary.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatPhase12SurfaceManifest") &&
        files.gradle.text.includes("verify-rhino-compat-phase12-surface-manifest.js"),
      "Gradle must register the Phase 12 surface manifest verifier.",
    ),
    check(
      "events-keys-promoted-entry",
      surfaceById.has("events.keys") &&
        surfaceById.get("events.keys").status === "promoted" &&
        includesAll(JSON.stringify(surfaceById.get("events.keys")), [
          "autojs6:compat",
          "rhino_compat.d.ts",
          "verifyNodeRhinoCompatEventsKeysFacade",
        ]),
      "events.keys must be recorded as a promoted surface with runtime, type, and gate evidence.",
    ),
    check(
      "promoted-surfaces-have-evidence",
      promoted.length >= 3 &&
        promoted.every((surface) => Array.isArray(surface.evidence) && surface.evidence.length > 0),
      "Every promoted surface must include evidence paths or gates.",
    ),
    check(
      "runtime-promoted-surface-appears",
      files.runtime.text.includes("__autojs6_rhino_compat_key_constants") &&
        files.types.text.includes("RhinoEventKeysCompatModule") &&
        /events\.keys\s*\|\s*keys\s*\|\s*supported/.test(files.matrix.text),
      "Promoted events.keys surface must be visible in runtime, types, and matrix.",
    ),
  ];
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
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
    "# Phase 12 Surface Manifest Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Status Counts",
    "",
  ];
  Object.keys(report.statusCounts).sort().forEach((status) => {
    lines.push(`- \`${status}\`: ${report.statusCounts[status]}`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
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
      console.log("Usage: node verify-rhino-compat-phase12-surface-manifest.js [--repo-root dir] [--report-dir dir]");
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
