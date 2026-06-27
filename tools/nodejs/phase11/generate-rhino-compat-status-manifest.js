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
  buildStatusManifest,
  validateStatusManifest,
} = require("./rhino-compat-status-manifest.js");

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
  const output = path.resolve(repoRoot, options.output || DEFAULT_STATUS_MANIFEST);
  const scanReport = scan(repoRoot);
  const inventory = buildInventory(scanReport);
  const bridge = parseBridgeModules(path.join(repoRoot, BRIDGE_PROTOCOL));
  const types = parseTypeDeclarations(path.join(repoRoot, TYPES_ROOT));
  const runtime = parseRuntimeEvidence(path.join(repoRoot, NODE_BRIDGE_SOURCES), bridge.modules, types.modules);
  const docs = parseDocsEvidence(repoRoot, DOC_EVIDENCE_FILES, bridge.modules, types.modules);
  const gap = buildGapReport(inventory, bridge, types, runtime, docs);
  const manifest = buildStatusManifest(inventory, gap);
  const validation = validateStatusManifest(inventory, manifest);
  if (!validation.valid) {
    throw new Error("Generated status manifest failed self-validation.");
  }

  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, JSON.stringify(manifest, null, 2) + "\n");
  console.log(`Rhino compat status manifest modules: ${manifest.totals.moduleCount}`);
  console.log(`Rhino compat status manifest API entries: ${manifest.totals.apiEntryCount}`);
  console.log(`Rhino compat status manifest output: ${path.relative(repoRoot, output).replace(/\\/g, "/")}`);
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-rhino-compat-status-manifest.js [--repo-root dir] [--output file]");
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
