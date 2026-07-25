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
  STATUS_VOCABULARY,
  loadStatusManifest,
  applyStatusManifest,
  validateStatusManifest,
} = require("./rhino-compat-status-manifest.js");

const REPORT_BASENAME = "rhino-compat-inventory-verification";
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
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const scanReport = scan(repoRoot);
  const seedInventory = buildInventory(scanReport);
  const manifestInfo = loadStatusManifest(repoRoot, options.statusManifest || DEFAULT_STATUS_MANIFEST);
  const manifestValidation = manifestInfo
    ? validateStatusManifest(seedInventory, manifestInfo.manifest)
    : missingManifestValidation();
  const inventory = manifestInfo
    ? applyStatusManifest(seedInventory, manifestInfo, { requireManifest: true })
    : seedInventory;
  const bridge = parseBridgeModules(path.join(repoRoot, BRIDGE_PROTOCOL));
  const types = parseTypeDeclarations(path.join(repoRoot, TYPES_ROOT));
  const runtime = parseRuntimeEvidence(path.join(repoRoot, NODE_BRIDGE_SOURCES), bridge.modules, types.modules);
  const docs = parseDocsEvidence(repoRoot, DOC_EVIDENCE_FILES, bridge.modules, types.modules);
  const gap = buildGapReport(inventory, bridge, types, runtime, docs);
  const policyViolations = policyViolationsFor(scanReport, inventory, manifestInfo, manifestValidation, gap);
  const report = {
    schema: "autojs6-node-rhino-compat-inventory-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatInventory",
    inputs: {
      statusManifest: manifestInfo ? manifestInfo.path : options.statusManifest || DEFAULT_STATUS_MANIFEST,
      inventorySchema: inventory.schema,
      gapSchema: gap.schema,
    },
    summary: {
      registeredModuleCount: scanReport.totals.registeredModuleCount,
      protoReferenceCount: scanReport.totals.protoReferenceCount,
      unresolvedReceiverCount: scanReport.totals.unresolvedReceiverCount,
      unresolvedKeyCount: scanReport.totals.unresolvedKeyCount || 0,
      inventoryModuleCount: inventory.totals.moduleCount,
      inventoryApiEntryCount: inventory.totals.apiEntryCount,
      manifestModuleCount: manifestInfo && manifestInfo.manifest.totals ? manifestInfo.manifest.totals.moduleCount : 0,
      manifestApiEntryCount: manifestInfo && manifestInfo.manifest.totals ? manifestInfo.manifest.totals.apiEntryCount : 0,
      bridgeModuleCount: gap.summary.bridgeModuleCount,
      typeModuleCount: gap.summary.typeModuleCount,
      rhinoModulesWithBridgeEvidence: gap.summary.rhinoModulesWithBridgeEvidence,
      rhinoModulesWithTypeEvidence: gap.summary.rhinoModulesWithTypeEvidence,
      rhinoModulesWithoutNodeSurface: gap.summary.rhinoModulesWithoutNodeSurface,
      byStatus: inventory.totals.byStatus,
    },
    manifestValidation,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      explicitApiEntryCoverageRequired: true,
      defaultStatusForbidden: true,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat inventory verification: " + report.decision.status);
  console.log(`Rhino compat inventory modules: ${report.summary.inventoryModuleCount}`);
  console.log(`Rhino compat inventory API entries: ${report.summary.inventoryApiEntryCount}`);
  console.log(`Rhino compat inventory policy violations: ${policyViolations.length}`);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function policyViolationsFor(scanReport, inventory, manifestInfo, manifestValidation, gap) {
  const violations = [];
  if (!manifestInfo) {
    violations.push("status manifest is missing");
  }
  if (!manifestValidation.valid) {
    violations.push("status manifest does not exactly cover inventory modules/API entries");
  }
  if (scanReport.totals.unresolvedReceiverCount > 0) {
    violations.push("source scan has unresolved receivers: " + scanReport.totals.unresolvedReceivers.join(", "));
  }
  if ((scanReport.totals.unresolvedKeyCount || 0) > 0) {
    const expressions = (scanReport.totals.unresolvedKeys || [])
      .map((entry) => `${entry.receiver}: ${entry.expression}`)
      .join(", ");
    violations.push("source scan has unresolved augmentable keys: " + expressions);
  }
  violations.push(...inventoryKeyViolations(inventory));
  if (inventory.totals.moduleCount <= 0 || inventory.totals.apiEntryCount <= 0) {
    violations.push("inventory is empty");
  }
  if (gap.summary.bridgeModuleCount <= 0) {
    violations.push("NodeBridgeModules evidence is empty");
  }
  if (gap.summary.typeModuleCount <= 0) {
    violations.push("Type declaration evidence is empty");
  }
  if (!STATUS_VOCABULARY.every((status) => inventory.statusVocabulary.includes(status))) {
    violations.push("inventory status vocabulary does not match Phase 11A vocabulary");
  }
  return violations;
}

function inventoryKeyViolations(inventory) {
  const violations = [];
  (inventory.modules || []).forEach((module) => {
    const hasKeys = Array.isArray(module.keys);
    const hasMountNames = Array.isArray(module.mountNames);
    if (!hasKeys && !hasMountNames) {
      return;
    }
    if (!hasKeys || !hasMountNames || module.keys.length === 0) {
      violations.push(`module ${module.id} must provide non-empty keys and mountNames together`);
      return;
    }
    const keyNames = module.keys.map((key) => key && key.name);
    if (keyNames.some((name) => typeof name !== "string" || !/\S/.test(name))) {
      violations.push(`module ${module.id} has a blank or invalid key descriptor name`);
      return;
    }
    if (new Set(keyNames).size !== keyNames.length) {
      violations.push(`module ${module.id} has duplicate key descriptor names`);
    }
    if (module.key !== keyNames[0]) {
      violations.push(`module ${module.id} primary key does not match keys[0].name`);
    }
    if (module.registration.withDollarPrefix !== module.keys[0].withDollarPrefix) {
      violations.push(`module ${module.id} registration.withDollarPrefix does not mirror the primary key`);
    }
    const expanded = module.keys.flatMap((key) => key.withDollarPrefix
      ? [key.name, `$${key.name}`]
      : [key.name]);
    if (new Set(expanded).size !== expanded.length) {
      violations.push(`module ${module.id} key descriptors resolve to duplicate mount names`);
    }
    if (!sameArray(module.mountNames, expanded)) {
      violations.push(`module ${module.id} mountNames does not match expanded keys`);
    }
    if (!sameArray(module.exports && module.exports.moduleKeys, module.mountNames)) {
      violations.push(`module ${module.id} exports.moduleKeys does not match mountNames`);
    }
    const moduleEntry = module.exports && Array.isArray(module.exports.entries)
      ? module.exports.entries.find((entry) => entry.id === `${module.id}.__module`)
      : null;
    const expectedAliases = module.mountNames.filter((name) => name !== module.key);
    if (!moduleEntry || moduleEntry.exposedName !== module.key ||
        !sameArray(moduleEntry.aliases || [], expectedAliases)) {
      violations.push(`module ${module.id} module entry aliases do not match non-primary mountNames`);
    }
  });
  return violations;
}

function sameArray(left, right) {
  return Array.isArray(left) && Array.isArray(right) &&
    left.length === right.length &&
    left.every((value, index) => value === right[index]);
}

function missingManifestValidation() {
  return {
    valid: false,
    invalidStatuses: [],
    missingModules: [],
    extraModules: [],
    missingEntries: [],
    extraEntries: [],
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Inventory Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Inventory modules: ${report.summary.inventoryModuleCount}`,
    `- Inventory API entries: ${report.summary.inventoryApiEntryCount}`,
    `- Manifest modules: ${report.summary.manifestModuleCount}`,
    `- Manifest API entries: ${report.summary.manifestApiEntryCount}`,
    `- Bridge modules: ${report.summary.bridgeModuleCount}`,
    `- Type modules: ${report.summary.typeModuleCount}`,
    `- Unresolved augmentable keys: ${report.summary.unresolvedKeyCount}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Status Counts",
    "",
  ];
  Object.keys(report.summary.byStatus).sort().forEach((status) => {
    lines.push(`- ${status}: ${report.summary.byStatus[status]}`);
  });
  lines.push("", "## Manifest Validation", "");
  lines.push(`- Valid: ${report.manifestValidation.valid}`);
  lines.push(`- Missing modules: ${report.manifestValidation.missingModules.length}`);
  lines.push(`- Missing API entries: ${report.manifestValidation.missingEntries.length}`);
  lines.push(`- Extra modules: ${report.manifestValidation.extraModules.length}`);
  lines.push(`- Extra API entries: ${report.manifestValidation.extraEntries.length}`);
  lines.push(`- Invalid statuses: ${report.manifestValidation.invalidStatuses.length}`);
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-inventory.js [--repo-root dir] [--report-dir dir] [--status-manifest file]");
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

if (require.main === module) {
  main();
}

module.exports = {
  inventoryKeyViolations,
};
