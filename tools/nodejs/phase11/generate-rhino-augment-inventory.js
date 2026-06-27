#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { scan } = require("./scan-rhino-augment-inventory.js");
const {
  DEFAULT_STATUS_MANIFEST,
  loadStatusManifest,
  applyStatusManifest,
} = require("./rhino-compat-status-manifest.js");

const INVENTORY_SCHEMA = "autojs6-node-rhino-augment-inventory-v1";
const STATUS_VOCABULARY = [
  "supported",
  "supported_async",
  "renamed",
  "partial",
  "blocked_by_design",
  "unsupported",
];

const ASSIGNMENT_KINDS = {
  selfAssignmentProperties: "self_property",
  globalAssignmentProperties: "global_property",
  selfAssignmentFunctions: "self_function",
  globalAssignmentFunctions: "global_function",
  selfAssignmentJavaClasses: "java_class",
  globalAssignmentJavaClasses: "java_class",
  selfAssignmentGetters: "getter",
  selfAssignmentGettersAndSetters: "getter_setter",
  globalAssignmentGetters: "getter",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const scanReport = scan(repoRoot);
  const statusManifest = loadStatusManifest(repoRoot, options.statusManifest || DEFAULT_STATUS_MANIFEST);
  const inventory = applyStatusManifest(
    buildInventory(scanReport),
    statusManifest,
    { requireManifest: options.requireStatusManifest },
  );

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, "rhino-augment-source-scan.json"), JSON.stringify(scanReport, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, "rhino-augment-api-inventory.json"), JSON.stringify(inventory, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, "rhino-augment-api-inventory.md"), renderMarkdown(inventory, scanReport));

  console.log(`Rhino augment inventory modules: ${inventory.modules.length}`);
  console.log(`Rhino augment inventory API entries: ${inventory.totals.apiEntryCount}`);
  console.log(`Rhino augment source scan unresolved receivers: ${scanReport.totals.unresolvedReceiverCount}`);

  if (options.failOnUnresolved && scanReport.totals.unresolvedReceiverCount > 0) {
    process.exitCode = 1;
  }
}

function buildInventory(scanReport) {
  const modules = scanReport.modules.map((module) => toInventoryModule(module));
  const apiEntryCount = modules.reduce((sum, module) => sum + module.exports.entries.length, 0);
  return {
    schema: INVENTORY_SCHEMA,
    schemaVersion: 1,
    generatedAt: scanReport.generatedAt,
    source: {
      augmentRoot: scanReport.source.augmentRoot,
      scriptRuntime: scanReport.source.scriptRuntime,
      scannerVersion: "scan-rhino-augment-inventory.js",
      notes: [
        `sourceScanSchema=${scanReport.schema}`,
        `scanner=${scanReport.source.scanner}`,
      ],
    },
    statusVocabulary: STATUS_VOCABULARY,
    modules,
    totals: {
      moduleCount: modules.length,
      apiEntryCount,
      byStatus: countByStatus(modules),
    },
  };
}

function toInventoryModule(scanModule) {
  const withDollarPrefix = scanModule.registration.withDollarPrefix === true;
  const moduleKeys = moduleKeysFor(scanModule.key || scanModule.receiver, withDollarPrefix);
  const entries = [
    moduleObjectEntry(scanModule, withDollarPrefix),
    ...assignmentEntries(scanModule),
  ];
  return withoutUndefined({
    id: scanModule.id,
    key: scanModule.key || scanModule.receiver,
    displayName: scanModule.receiver,
    className: scanModule.className || scanModule.receiver,
    packageName: scanModule.packageName || "unknown",
    source: scanModule.source || {
      file: scanModule.registration.site.file,
      line: scanModule.registration.site.line,
    },
    registration: withoutUndefined({
      registered: true,
      site: scanModule.registration.site,
      order: scanModule.order,
      target: scanModule.registration.target,
      method: scanModule.registration.method,
      withDollarPrefix,
      additionalAttributes: scanModule.registration.additionalAttributes || [],
      proto: scanModule.registration.protoExpressions || [],
    }),
    augmentable: scanModule.augmentable || {
      baseClass: "unknown",
      traits: [],
      construction: "unknown",
      requiresScriptRuntime: false,
      originatesKeyName: false,
      simpleGetterProxyInterface: false,
    },
    exports: {
      moduleKeys,
      entries,
    },
    migration: seedMigration(scanModule, "module"),
    notes: scanModule.className ? [] : [`Unresolved receiver: ${scanModule.receiver}`],
  });
}

function moduleObjectEntry(scanModule, withDollarPrefix) {
  const key = scanModule.key || scanModule.receiver;
  const kind = moduleObjectKind(scanModule);
  return {
    id: `${scanModule.id}.__module`,
    name: key,
    exposedName: key,
    kind,
    assignment: "augment_key",
    source: scanModule.source || scanModule.registration.site,
    attributes: {
      rhinoFlags: ["PERMANENT"],
      dollarAlias: withDollarPrefix,
    },
    aliases: withDollarPrefix ? [`$${key}`] : [],
    migration: seedMigration(scanModule, "api"),
    risks: risksFor(scanModule),
    notes: [
      `Registered by ${scanModule.registration.method} at ${scanModule.registration.site.file}:${scanModule.registration.site.line}.`,
    ],
  };
}

function assignmentEntries(scanModule) {
  const summary = scanModule.assignmentSummary || {};
  const entries = [];
  Object.keys(ASSIGNMENT_KINDS).forEach((assignment) => {
    const names = summary[assignment] && Array.isArray(summary[assignment].names)
      ? summary[assignment].names
      : [];
    names.forEach((name) => {
      entries.push({
        id: `${scanModule.id}.${entrySegment(name)}`,
        name,
        exposedName: name,
        kind: ASSIGNMENT_KINDS[assignment],
        assignment,
        source: scanModule.source || scanModule.registration.site,
        attributes: {
          rhinoFlags: ["PERMANENT"],
          dollarAlias: false,
        },
        migration: seedMigration(scanModule, "api"),
        risks: risksFor(scanModule),
      });
    });
  });
  return entries;
}

function moduleObjectKind(scanModule) {
  const augmentable = scanModule.augmentable || {};
  const traits = augmentable.traits || [];
  if (scanModule.registration.method === "proxying" || augmentable.construction === "proxy_object") {
    return "proxy_accessor";
  }
  if (traits.includes("Versatile") || traits.includes("Invokable")) {
    return "invokable";
  }
  if (traits.includes("Constructable")) {
    return "constructor";
  }
  if (traits.includes("AsEmitter")) {
    return "event_emitter";
  }
  if (traits.includes("JsBuildInObjectExtensible")) {
    return "prototype_extension";
  }
  if (traits.includes("SimpleGetterProxy")) {
    return "simple_getter_proxy";
  }
  return "augment_object";
}

function seedMigration(scanModule, scope) {
  return {
    status: "unsupported",
    asyncMode: scope === "module" ? "not_applicable" : "unknown",
    notes: [
      "Seed status generated from source scan. S11-05 status manifest must replace this with a compatibility decision.",
    ],
  };
}

function risksFor(scanModule) {
  const risks = new Set();
  const augmentable = scanModule.augmentable || {};
  const traits = augmentable.traits || [];
  if (traits.includes("AsEmitter")) {
    risks.add("lifecycle_resource");
    risks.add("event_loop_semantics");
  }
  if (traits.includes("Constructable")) {
    risks.add("raw_android_object");
    risks.add("lifecycle_resource");
  }
  if (scanModule.registration.method === "proxying") {
    risks.add("lifecycle_resource");
    risks.add("thread_model");
  }
  if ((scanModule.registration.protoExpressions || []).length > 0) {
    risks.add("provider_availability");
  }
  if (risks.size === 0) {
    risks.add("none");
  }
  return Array.from(risks).sort();
}

function moduleKeysFor(key, withDollarPrefix) {
  const keys = [key];
  if (withDollarPrefix) {
    keys.push(`$${key}`);
  }
  return keys;
}

function entrySegment(name) {
  const cleaned = String(name).replace(/[^A-Za-z0-9_$]+/g, "_").replace(/^_+|_+$/g, "");
  return cleaned || "entry";
}

function countByStatus(modules) {
  const counts = {};
  modules.forEach((module) => {
    count(counts, module.migration.status);
    module.exports.entries.forEach((entry) => count(counts, entry.migration.status));
  });
  return counts;
}

function count(counts, key) {
  counts[key] = (counts[key] || 0) + 1;
}

function renderMarkdown(inventory, scanReport) {
  const lines = [
    "# Rhino Augment API Inventory",
    "",
    `- Schema: ${inventory.schema}`,
    `- Generated at: ${inventory.generatedAt}`,
    `- Modules: ${inventory.totals.moduleCount}`,
    `- API entries: ${inventory.totals.apiEntryCount}`,
    `- Source scan unresolved receivers: ${scanReport.totals.unresolvedReceiverCount}`,
    "",
    "This is a generated Phase 11A seed inventory. Migration statuses are seeded as `unsupported` until S11-05 adds the status manifest.",
    "",
    "## Registered Modules",
    "",
    "| Order | Id | Key | Receiver | Traits | Entries | Source |",
    "| ---: | --- | --- | --- | --- | ---: | --- |",
  ];
  inventory.modules.forEach((module) => {
    const traits = module.augmentable.traits.length > 0 ? module.augmentable.traits.join(", ") : "-";
    lines.push(
      `| ${module.registration.order} | ${module.id} | ${module.key} | ${module.displayName || "-"} | ${traits} | ${module.exports.entries.length} | ${module.source.file}:${module.source.line || "?"} |`,
    );
  });
  lines.push("", "## Proto References", "");
  if (scanReport.protoReferences.length === 0) {
    lines.push("None.");
  } else {
    lines.push("| Owner | Expression | Resolved Class | Source |", "| --- | --- | --- | --- |");
    scanReport.protoReferences.forEach((entry) => {
      const source = entry.source ? `${entry.source.file}:${entry.source.line || "?"}` : "-";
      lines.push(`| ${entry.ownerModuleId} | ${entry.expression} | ${entry.className || "-"} | ${source} |`);
    });
  }
  lines.push("", "## Status Counts", "");
  Object.keys(inventory.totals.byStatus).sort().forEach((status) => {
    lines.push(`- ${status}: ${inventory.totals.byStatus[status]}`);
  });
  return lines.join("\n") + "\n";
}

function withoutUndefined(value) {
  const result = {};
  Object.keys(value).forEach((key) => {
    if (value[key] !== undefined) {
      result[key] = value[key];
    }
  });
  return result;
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-unresolved", "require-status-manifest"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-rhino-augment-inventory.js [--repo-root dir] [--report-dir dir] [--status-manifest file] [--require-status-manifest] [--fail-on-unresolved]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
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

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

if (require.main === module) {
  main();
}

module.exports = {
  buildInventory,
  renderMarkdown,
};
