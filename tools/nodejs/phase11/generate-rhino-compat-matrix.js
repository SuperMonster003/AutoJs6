#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { scan } = require("./scan-rhino-augment-inventory.js");
const { buildInventory } = require("./generate-rhino-augment-inventory.js");
const {
  buildGapReport,
  parseBridgeModules,
  parseTypeDeclarations,
  parseRuntimeEvidence,
  parseDocsEvidence,
} = require("./generate-rhino-node-compat-gap.js");
const {
  DEFAULT_STATUS_MANIFEST,
  loadStatusManifest,
  applyStatusManifest,
} = require("./rhino-compat-status-manifest.js");

const DEFAULT_OUTPUT = "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md";
const BRIDGE_PROTOCOL = "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt";
const TYPES_ROOT = "docs/nodejs/types/autojs6-node";
const NODE_BRIDGE_SOURCES = "app/src/main/cpp/node_bridge_sources.cpp";
const DOC_EVIDENCE_FILES = [
  "docs/nodejs/COMPATIBILITY_PROFILE.md",
  "docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md",
  "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const output = path.resolve(repoRoot, options.output || DEFAULT_OUTPUT);
  const scanReport = scan(repoRoot);
  const manifestInfo = loadStatusManifest(repoRoot, options.statusManifest || DEFAULT_STATUS_MANIFEST);
  const inventory = applyStatusManifest(
    buildInventory(scanReport),
    manifestInfo,
    { requireManifest: true },
  );
  const bridge = parseBridgeModules(path.join(repoRoot, BRIDGE_PROTOCOL));
  const types = parseTypeDeclarations(path.join(repoRoot, TYPES_ROOT));
  const runtime = parseRuntimeEvidence(path.join(repoRoot, NODE_BRIDGE_SOURCES), bridge.modules, types.modules);
  const docs = parseDocsEvidence(repoRoot, DOC_EVIDENCE_FILES, bridge.modules, types.modules);
  const gap = buildGapReport(inventory, bridge, types, runtime, docs);
  const markdown = renderMatrix(inventory, gap);
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, markdown);
  console.log(`Rhino compatibility matrix modules: ${inventory.modules.length}`);
  console.log(`Rhino compatibility matrix API entries: ${inventory.totals.apiEntryCount}`);
  console.log(`Rhino compatibility matrix output: ${path.relative(repoRoot, output).replace(/\\/g, "/")}`);
}

function renderMatrix(inventory, gap) {
  const gapByModule = new Map(gap.modules.map((entry) => [entry.id, entry]));
  const moduleRows = inventory.modules.map((module) => moduleMatrixRow(module, gapByModule.get(module.id)));
  const lines = [
    "# Rhino Augment Compatibility Matrix",
    "",
    "This Phase 11A matrix is generated from the Rhino augment inventory, the checked-in status manifest, and the Node bridge/types/docs gap report. It is a module-level tracking document; API-level details remain in `build/reports/nodejs/rhino-augment-api-inventory.json` and `build/reports/nodejs/rhino-node-compat-gap.json`.",
    "",
    "## Summary",
    "",
    `- Rhino modules: ${inventory.totals.moduleCount}`,
    `- Rhino API entries: ${inventory.totals.apiEntryCount}`,
    `- Node bridge modules: ${gap.summary.bridgeModuleCount}`,
    `- Node type modules: ${gap.summary.typeModuleCount}`,
    `- Modules with bridge evidence: ${gap.summary.rhinoModulesWithBridgeEvidence}`,
    `- Modules with type evidence: ${gap.summary.rhinoModulesWithTypeEvidence}`,
    `- Modules without Node surface evidence: ${gap.summary.rhinoModulesWithoutNodeSurface}`,
    "",
    "## Status Totals",
    "",
  ];
  Object.keys(inventory.totals.byStatus).sort().forEach((status) => {
    lines.push(`- ${status}: ${inventory.totals.byStatus[status]}`);
  });
  lines.push(
    "",
    "## Module Matrix",
    "",
    "| Order | Rhino Module | Key | Status | Node Surface | API Status | Risks | Next Step |",
    "| ---: | --- | --- | --- | --- | --- | --- | --- |",
  );
  moduleRows.forEach((row) => {
    lines.push(`| ${row.order} | ${row.id} | ${row.key} | ${row.status} | ${row.nodeSurface} | ${row.apiStatus} | ${row.risks} | ${row.nextStep} |`);
  });
  lines.push("", "## Module Details", "");
  moduleRows.forEach((row) => {
    lines.push(
      `### ${row.id}`,
      "",
      `- Key: \`${row.key}\``,
      `- Status: \`${row.status}\``,
      `- Node surface: ${row.nodeSurface || "-"}`,
      `- Coverage: \`${row.coverageStatus}\``,
      `- API status: ${row.apiStatus}`,
      `- Risks: ${row.risks}`,
      `- Next step: ${row.nextStep}`,
      "",
    );
  });
    while (lines.length > 0 && lines[lines.length - 1] === "") {
        lines.pop();
    }
    return lines.join("\n") + "\n";
}

function moduleMatrixRow(module, gapModule) {
  const statusCounts = countEntryStatuses(module);
  const nodeSurface = nodeSurfaceFor(module, gapModule);
  const risks = riskSummary(module);
  return {
    order: module.registration.order,
    id: module.id,
    key: module.key,
    status: module.migration.status,
    nodeSurface,
    coverageStatus: gapModule ? gapModule.coverageStatus : "unknown",
    apiStatus: statusSummary(statusCounts),
    risks,
    nextStep: nextStepFor(module, gapModule),
  };
}

function nodeSurfaceFor(module, gapModule) {
  const modules = new Set();
  if (Array.isArray(module.migration.nodeModules)) {
    module.migration.nodeModules.forEach((name) => modules.add(name));
  }
  if (Array.isArray(module.migration.bridgeModules)) {
    module.migration.bridgeModules.forEach((name) => modules.add(name));
  }
  if (module.migration.nodeModule) {
    modules.add(module.migration.nodeModule);
  }
  if (module.migration.bridgeModule) {
    modules.add(module.migration.bridgeModule);
  }
  if (gapModule) {
    [
      ...gapModule.nodeEvidence.bridge.modules,
      ...gapModule.nodeEvidence.types.modules,
      ...gapModule.nodeEvidence.runtime.modules,
    ].forEach((name) => modules.add(name));
  }
  return Array.from(modules).sort().join(", ") || "-";
}

function countEntryStatuses(module) {
  const counts = {};
  module.exports.entries.forEach((entry) => {
    counts[entry.migration.status] = (counts[entry.migration.status] || 0) + 1;
  });
  return counts;
}

function statusSummary(counts) {
  return Object.keys(counts).sort().map((status) => `${status}:${counts[status]}`).join(", ");
}

function riskSummary(module) {
  const risks = new Set();
  module.exports.entries.forEach((entry) => (entry.risks || []).forEach((risk) => risks.add(risk)));
  risks.delete("none");
  return Array.from(risks).sort().join(", ") || "none";
}

function nextStepFor(module, gapModule) {
  if (
    module.id === "floaty" &&
    (module.migration.notes || []).some((note) => note.includes("P12-11 rejection path selected"))
  ) {
    return "Complete P12-12B negative runtime gate; keep exact Rhino floaty/window as a Node non-goal.";
  }
  if (module.migration.status === "blocked_by_design") {
    return "Keep denial documented and covered by policy tests.";
  }
  if (module.migration.status === "unsupported") {
    return gapModule && gapModule.coverageStatus !== "no_node_surface"
      ? "Decide explicit mapping or mark blocked-by-design."
      : "Design a Node facade or record a permanent non-goal.";
  }
  if (module.migration.status === "partial") {
    return "Implement explicit compat facade and promote mapped APIs one by one.";
  }
  if (module.migration.status === "renamed") {
    return "Document the replacement name and add migration examples.";
  }
  if (module.migration.status === "supported_async") {
    return "Add compat examples and dual-engine tests.";
  }
  return "Review status manifest decision.";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-rhino-compat-matrix.js [--repo-root dir] [--output file] [--status-manifest file]");
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
