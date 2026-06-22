#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { scan } = require("./scan-rhino-augment-inventory.js");
const { buildInventory } = require("./generate-rhino-augment-inventory.js");
const {
  DEFAULT_STATUS_MANIFEST,
  loadStatusManifest,
  applyStatusManifest,
} = require("./rhino-compat-status-manifest.js");

const GAP_SCHEMA = "autojs6-node-rhino-node-compat-gap-v1";
const BRIDGE_PROTOCOL = "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt";
const TYPES_ROOT = "docs/nodejs/types/autojs6-node";
const NODE_BRIDGE_SOURCES = "app/src/main/cpp/node_bridge_sources.cpp";
const DOC_EVIDENCE_FILES = [
  "docs/nodejs/COMPATIBILITY_PROFILE.md",
  "docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md",
  "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
];

const RHINO_NODE_ALIASES = {
  auto: ["accessibility"],
  automator: ["accessibility"],
  selector: ["accessibility"],
  images: ["image", "images"],
  image: ["image", "images"],
  ocr: ["ocr"],
  barcode: ["image"],
  qrcode: ["image"],
  app: ["app"],
  autojs: ["engines"],
  shell: ["shell"],
  timers: ["timers"],
  events: ["events"],
  dialogs: ["dialogs"],
  files: ["fs", "fs/promises"],
  engines: ["engines"],
  storages: ["storage", "storages"],
  storage: ["storage", "storages"],
  sqlite: ["database", "sqlite"],
  database: ["database", "sqlite"],
  notice: ["notifications"],
  notifications: ["notifications"],
  sensors: ["sensors"],
  ui: ["ui"],
  device: ["device", "clipboard"],
  clipboard: ["clipboard"],
  web: ["fetch", "websocket"],
  websocket: ["websocket"],
  web_socket: ["websocket"],
  tasks: ["work_manager"],
  work_manager: ["work_manager"],
  media: ["media_projection"],
  media_projection: ["media_projection"],
  java: ["java"],
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
  const bridge = parseBridgeModules(path.join(repoRoot, BRIDGE_PROTOCOL));
  const types = parseTypeDeclarations(path.join(repoRoot, TYPES_ROOT));
  const runtime = parseRuntimeEvidence(path.join(repoRoot, NODE_BRIDGE_SOURCES), bridge.modules, types.modules);
  const docs = parseDocsEvidence(repoRoot, DOC_EVIDENCE_FILES, bridge.modules, types.modules);
  const gap = buildGapReport(inventory, bridge, types, runtime, docs);

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, "rhino-node-compat-gap.json"), JSON.stringify(gap, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, "rhino-node-compat-gap.md"), renderMarkdown(gap));

  console.log(`Rhino Node compat gap modules: ${gap.summary.rhinoModuleCount}`);
  console.log(`Rhino Node compat gap API entries: ${gap.summary.rhinoApiEntryCount}`);
  console.log(`Rhino modules with bridge evidence: ${gap.summary.rhinoModulesWithBridgeEvidence}`);
  console.log(`Rhino modules with type evidence: ${gap.summary.rhinoModulesWithTypeEvidence}`);
}

function buildGapReport(inventory, bridge, types, runtime, docs) {
  const modules = inventory.modules.map((module) => moduleGap(module, bridge, types, runtime, docs));
  const noNodeSurface = modules.filter((entry) => entry.coverageStatus === "no_node_surface").map((entry) => entry.id);
  return {
    schema: GAP_SCHEMA,
    generatedAt: new Date().toISOString(),
    inputs: {
      inventorySchema: inventory.schema,
      bridgeProtocol: BRIDGE_PROTOCOL,
      typesRoot: TYPES_ROOT,
      runtimeSources: NODE_BRIDGE_SOURCES,
      docs: DOC_EVIDENCE_FILES,
    },
    summary: {
      rhinoModuleCount: modules.length,
      rhinoApiEntryCount: inventory.totals.apiEntryCount,
      bridgeModuleCount: bridge.modules.length,
      typeModuleCount: types.modules.length,
      runtimeEvidenceModuleCount: runtime.modules.length,
      rhinoModulesWithBridgeEvidence: modules.filter((entry) => entry.nodeEvidence.bridge.present).length,
      rhinoModulesWithTypeEvidence: modules.filter((entry) => entry.nodeEvidence.types.present).length,
      rhinoModulesWithRuntimeEvidence: modules.filter((entry) => entry.nodeEvidence.runtime.present).length,
      rhinoModulesWithDocsEvidence: modules.filter((entry) => entry.nodeEvidence.docs.present).length,
      rhinoModulesWithoutNodeSurface: noNodeSurface.length,
    },
    bridge,
    types,
    runtime,
    modules,
    noNodeSurface,
  };
}

function moduleGap(module, bridge, types, runtime, docs) {
  const candidates = candidateModuleNames(module);
  const bridgeMatches = candidates.filter((name) => bridge.methodMap[name]);
  const typeMatches = candidates.filter((name) => types.methodMap[name]);
  const runtimeMatches = candidates.filter((name) => runtime.moduleSet.has(name));
  const docsMatches = candidates.filter((name) => docs.moduleMap[name] && docs.moduleMap[name].length > 0);
  const bridgeMethods = unique(bridgeMatches.flatMap((name) => bridge.methodMap[name] || []));
  const typeMethods = unique(typeMatches.flatMap((name) => types.methodMap[name] || []));
  const apiEntries = module.exports.entries.map((entry) => apiGap(entry, bridgeMethods, typeMethods));
  const exactBridgeMatches = apiEntries.filter((entry) => entry.bridgeMethodMatch).length;
  const exactTypeMatches = apiEntries.filter((entry) => entry.typeMethodMatch).length;
  const coverageStatus = coverageStatusFor(bridgeMatches, typeMatches, runtimeMatches);

  return {
    id: module.id,
    key: module.key,
    displayName: module.displayName,
    source: module.source,
    candidates,
    rhinoApiEntryCount: module.exports.entries.length,
    exactBridgeMethodMatches: exactBridgeMatches,
    exactTypeMethodMatches: exactTypeMatches,
    coverageStatus,
    nodeEvidence: {
      bridge: {
        present: bridgeMatches.length > 0,
        modules: bridgeMatches,
        methods: bridgeMethods,
      },
      types: {
        present: typeMatches.length > 0,
        modules: typeMatches,
        methods: typeMethods,
      },
      runtime: {
        present: runtimeMatches.length > 0,
        modules: runtimeMatches,
      },
      docs: {
        present: docsMatches.length > 0,
        modules: docsMatches,
        files: unique(docsMatches.flatMap((name) => docs.moduleMap[name] || [])),
      },
    },
    apiEntries,
  };
}

function apiGap(entry, bridgeMethods, typeMethods) {
  return {
    id: entry.id,
    exposedName: entry.exposedName,
    kind: entry.kind,
    migrationStatus: entry.migration.status,
    bridgeMethodMatch: bridgeMethods.includes(entry.exposedName),
    typeMethodMatch: typeMethods.includes(entry.exposedName),
  };
}

function coverageStatusFor(bridgeMatches, typeMatches, runtimeMatches) {
  if (bridgeMatches.length > 0 && typeMatches.length > 0) {
    return "bridge_and_types";
  }
  if (bridgeMatches.length > 0) {
    return "bridge_only";
  }
  if (typeMatches.length > 0) {
    return "types_only";
  }
  if (runtimeMatches.length > 0) {
    return "runtime_only";
  }
  return "no_node_surface";
}

function candidateModuleNames(module) {
  const candidates = new Set();
  [
    module.key,
    module.id,
    module.id.split(".").pop(),
    module.displayName,
  ].forEach((name) => addCandidate(candidates, name));
  Array.from(candidates).forEach((name) => {
    const aliases = RHINO_NODE_ALIASES[name] || RHINO_NODE_ALIASES[name.replace(/_/g, "")];
    if (aliases) {
      aliases.forEach((alias) => addCandidate(candidates, alias));
    }
  });
  return Array.from(candidates).sort();
}

function addCandidate(candidates, value) {
  if (!value) {
    return;
  }
  const normalized = normalizeName(value);
  if (normalized) {
    candidates.add(normalized);
  }
}

function parseBridgeModules(file) {
  const text = read(file);
  const objectText = extractObjectBlock(text, "NodeBridgeModules");
  const constants = {};
  let match;
  const constPattern = /const\s+val\s+([A-Z0-9_]+)\s*=\s*"([^"]+)"/g;
  while ((match = constPattern.exec(objectText)) !== null) {
    constants[match[1]] = match[2];
  }
  const methodSets = {};
  const methodsPattern = /val\s+([A-Za-z0-9_]+Methods)\s*=\s*setOf\(([\s\S]*?)\)/g;
  while ((match = methodsPattern.exec(objectText)) !== null) {
    methodSets[match[1]] = stringsIn(match[2]);
  }
  const mapBlock = matchOne(objectText, /supportedMethodsByModule:[\s\S]*?=\s*mapOf\(([\s\S]*?)\)\s*\n\s*fun supports/);
  const methodMap = {};
  if (mapBlock) {
    const mappingPattern = /([A-Z0-9_]+)\s+to\s+([A-Za-z0-9_]+Methods)/g;
    while ((match = mappingPattern.exec(mapBlock)) !== null) {
      const moduleName = constants[match[1]];
      const methods = methodSets[match[2]];
      if (moduleName && methods) {
        methodMap[moduleName] = methods;
      }
    }
  }
  return {
    modules: Object.keys(methodMap).sort(),
    methodMap: sortMethodMap(methodMap),
  };
}

function parseTypeDeclarations(typesRoot) {
  const methodMap = {};
  for (const file of fs.readdirSync(typesRoot).filter((name) => name.endsWith(".d.ts")).sort()) {
    const fullPath = path.join(typesRoot, file);
    const text = read(fullPath);
    const moduleMatches = Array.from(text.matchAll(/declare\s+module\s+"([^"]+)"/g)).map((match) => match[1]);
    moduleMatches.forEach((moduleName) => {
      methodMap[moduleName] = unique([...(methodMap[moduleName] || []), ...moduleMethodsFromTypeText(text)]).sort();
    });
  }
  return {
    modules: Object.keys(methodMap).sort(),
    methodMap,
  };
}

function moduleMethodsFromTypeText(text) {
  const methods = new Set();
  const interfacePattern = /interface\s+[A-Za-z0-9_]*Module\s*\{/g;
  let match;
  while ((match = interfacePattern.exec(text)) !== null) {
    const block = extractBraceBlock(text, text.indexOf("{", match.index));
    const methodPattern = /^\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*\(/gm;
    let methodMatch;
    while ((methodMatch = methodPattern.exec(block)) !== null) {
      methods.add(methodMatch[1]);
    }
  }
  return Array.from(methods);
}

function parseRuntimeEvidence(file, bridgeModules, typeModules) {
  const text = read(file);
  const modules = unique([...bridgeModules, ...typeModules])
    .filter((moduleName) => text.includes(`"${moduleName}"`))
    .sort();
  return {
    modules,
    moduleSet: new Set(modules),
  };
}

function parseDocsEvidence(repoRoot, files, bridgeModules, typeModules) {
  const moduleNames = unique([...bridgeModules, ...typeModules]);
  const moduleMap = {};
  moduleNames.forEach((moduleName) => {
    moduleMap[moduleName] = [];
  });
  files.forEach((relative) => {
    const file = path.join(repoRoot, relative);
    const text = fs.existsSync(file) ? read(file) : "";
    moduleNames.forEach((moduleName) => {
      if (containsModuleToken(text, moduleName)) {
        moduleMap[moduleName].push(relative);
      }
    });
  });
  return { moduleMap };
}

function containsModuleToken(text, moduleName) {
  const escaped = moduleName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(^|[^A-Za-z0-9_])${escaped}([^A-Za-z0-9_]|$)`).test(text);
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Node Compatibility Gap",
    "",
    `- Schema: ${report.schema}`,
    `- Generated at: ${report.generatedAt}`,
    `- Rhino modules: ${report.summary.rhinoModuleCount}`,
    `- Rhino API entries: ${report.summary.rhinoApiEntryCount}`,
    `- Bridge modules: ${report.summary.bridgeModuleCount}`,
    `- Type modules: ${report.summary.typeModuleCount}`,
    `- Rhino modules with bridge evidence: ${report.summary.rhinoModulesWithBridgeEvidence}`,
    `- Rhino modules with type evidence: ${report.summary.rhinoModulesWithTypeEvidence}`,
    `- Rhino modules without Node surface evidence: ${report.summary.rhinoModulesWithoutNodeSurface}`,
    "",
    "## Node Surfaces",
    "",
    "| Module | Bridge Methods | Type Methods | Runtime Source |",
    "| --- | ---: | ---: | --- |",
  ];
  unique([...report.bridge.modules, ...report.types.modules]).sort().forEach((moduleName) => {
    const bridgeMethods = report.bridge.methodMap[moduleName] || [];
    const typeMethods = report.types.methodMap[moduleName] || [];
    const runtime = report.runtime.modules.includes(moduleName) ? "yes" : "no";
    lines.push(`| ${moduleName} | ${bridgeMethods.length} | ${typeMethods.length} | ${runtime} |`);
  });
  lines.push("", "## Rhino Module Coverage", "");
  lines.push("| Rhino Module | Key | Candidates | Coverage | Bridge Matches | Type Matches | Entries |");
  lines.push("| --- | --- | --- | --- | ---: | ---: | ---: |");
  report.modules.forEach((module) => {
    lines.push(
      `| ${module.id} | ${module.key} | ${module.candidates.join(", ")} | ${module.coverageStatus} | ${module.exactBridgeMethodMatches} | ${module.exactTypeMethodMatches} | ${module.rhinoApiEntryCount} |`,
    );
  });
  lines.push("", "## No Node Surface Evidence", "");
  if (report.noNodeSurface.length === 0) {
    lines.push("None.");
  } else {
    report.noNodeSurface.forEach((id) => lines.push(`- ${id}`));
  }
  return lines.join("\n") + "\n";
}

function extractObjectBlock(text, objectName) {
  const start = text.indexOf(`object ${objectName}`);
  if (start < 0) {
    throw new Error(`Unable to find object ${objectName}`);
  }
  const open = text.indexOf("{", start);
  return extractBraceBlock(text, open);
}

function extractBraceBlock(text, open) {
  let depth = 0;
  for (let index = open; index < text.length; index += 1) {
    const ch = text[index];
    if (ch === "{") {
      depth += 1;
    } else if (ch === "}") {
      depth -= 1;
      if (depth === 0) {
        return text.slice(open + 1, index);
      }
    }
  }
  throw new Error("Unclosed brace block");
}

function stringsIn(text) {
  return unique(Array.from(text.matchAll(/"([^"]+)"/g)).map((match) => match[1])).sort();
}

function sortMethodMap(map) {
  const sorted = {};
  Object.keys(map).sort().forEach((key) => {
    sorted[key] = map[key].slice().sort();
  });
  return sorted;
}

function normalizeName(value) {
  return String(value)
    .replace(/^[$]+/, "")
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1_$2")
    .replace(/[^A-Za-z0-9/]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

function unique(values) {
  return Array.from(new Set(values.filter(Boolean)));
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function matchOne(text, pattern) {
  const match = text.match(pattern);
  return match ? match[1] : null;
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["require-status-manifest"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-rhino-node-compat-gap.js [--repo-root dir] [--report-dir dir] [--status-manifest file] [--require-status-manifest]");
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
  buildGapReport,
  parseBridgeModules,
  parseTypeDeclarations,
  parseRuntimeEvidence,
  parseDocsEvidence,
};
