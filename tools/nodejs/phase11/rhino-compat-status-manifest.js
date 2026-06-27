#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const STATUS_MANIFEST_SCHEMA = "autojs6-node-rhino-compat-status-manifest-v1";
const DEFAULT_STATUS_MANIFEST = "docs/nodejs/rhino-compat/status-manifest.json";
const STATUS_VOCABULARY = [
  "supported",
  "supported_async",
  "renamed",
  "partial",
  "blocked_by_design",
  "unsupported",
];

const BLOCKED_MODULE_PATTERNS = [
  /^util\.java$/,
  /^threads$/,
  /^automator\.root_automator$/,
];

function buildStatusManifest(inventory, gap) {
  const gapByModule = new Map((gap ? gap.modules : []).map((entry) => [entry.id, entry]));
  const modules = inventory.modules.map((module) => {
    const gapModule = gapByModule.get(module.id) || null;
    const moduleDecision = decideModuleStatus(module, gapModule);
    return {
      id: module.id,
      status: moduleDecision.status,
      asyncMode: moduleDecision.asyncMode,
      nodeModules: moduleDecision.nodeModules,
      bridgeModules: moduleDecision.bridgeModules,
      decision: moduleDecision.decision,
      notes: moduleDecision.notes,
      apiEntries: module.exports.entries.map((entry) => {
        const apiDecision = decideApiStatus(module, entry, gapModule, moduleDecision);
        return {
          id: entry.id,
          status: apiDecision.status,
          asyncMode: apiDecision.asyncMode,
          nodeModule: apiDecision.nodeModule,
          bridgeModule: apiDecision.bridgeModule,
          nodeName: apiDecision.nodeName,
          decision: apiDecision.decision,
          notes: apiDecision.notes,
        };
      }),
    };
  });
  return {
    schema: STATUS_MANIFEST_SCHEMA,
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    inventorySchema: inventory.schema,
    statusVocabulary: STATUS_VOCABULARY,
    policy: {
      explicitApiEntryCoverageRequired: true,
      wildcardStatusAllowed: false,
      defaultStatusForbidden: true,
    },
    modules,
    totals: {
      moduleCount: modules.length,
      apiEntryCount: modules.reduce((sum, module) => sum + module.apiEntries.length, 0),
      byStatus: countManifestStatuses(modules),
    },
  };
}

function loadStatusManifest(repoRoot, manifestPath) {
  const resolved = resolveManifestPath(repoRoot, manifestPath);
  if (!fs.existsSync(resolved)) {
    return null;
  }
  return {
    path: relativePath(repoRoot, resolved),
    manifest: JSON.parse(fs.readFileSync(resolved, "utf8").replace(/^\uFEFF/, "")),
  };
}

function applyStatusManifest(inventory, manifestInfo, options = {}) {
  if (!manifestInfo || !manifestInfo.manifest) {
    if (options.requireManifest) {
      throw new Error("Rhino compat status manifest is required but was not found.");
    }
    return inventory;
  }
  const manifest = manifestInfo.manifest;
  const validation = validateStatusManifest(inventory, manifest);
  if (!validation.valid) {
    const summary = [
      ...validation.invalidStatuses.map((entry) => `invalid status ${entry.status} at ${entry.path}`),
      ...validation.missingModules.map((id) => `missing module ${id}`),
      ...validation.extraModules.map((id) => `extra module ${id}`),
      ...validation.missingEntries.slice(0, 20).map((id) => `missing API ${id}`),
      ...validation.extraEntries.slice(0, 20).map((id) => `extra API ${id}`),
    ];
    throw new Error("Rhino compat status manifest coverage failed:\n" + summary.join("\n"));
  }

  const moduleMap = new Map(manifest.modules.map((module) => [module.id, module]));
  inventory.modules.forEach((module) => {
    const manifestModule = moduleMap.get(module.id);
    module.migration = migrationFromManifestRecord(manifestModule);
    const entryMap = new Map(manifestModule.apiEntries.map((entry) => [entry.id, entry]));
    module.exports.entries.forEach((entry) => {
      entry.migration = migrationFromManifestRecord(entryMap.get(entry.id));
    });
  });
  inventory.source.notes = [
    ...(inventory.source.notes || []),
    `statusManifest=${manifestInfo.path || DEFAULT_STATUS_MANIFEST}`,
    `statusManifestSchema=${manifest.schema}`,
  ];
  inventory.statusManifest = {
    schema: manifest.schema,
    path: manifestInfo.path || DEFAULT_STATUS_MANIFEST,
    moduleCount: manifest.modules.length,
    apiEntryCount: manifest.totals ? manifest.totals.apiEntryCount : 0,
  };
  inventory.totals.byStatus = countInventoryStatuses(inventory.modules);
  return inventory;
}

function validateStatusManifest(inventory, manifest) {
  const invalidStatuses = [];
  if (!manifest || manifest.schema !== STATUS_MANIFEST_SCHEMA) {
    invalidStatuses.push({ path: "schema", status: manifest && manifest.schema });
  }
  const manifestModules = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const inventoryModules = new Map(inventory.modules.map((module) => [module.id, module]));
  const missingModules = [];
  const extraModules = [];
  const missingEntries = [];
  const extraEntries = [];

  inventory.modules.forEach((module) => {
    const manifestModule = manifestModules.get(module.id);
    if (!manifestModule) {
      missingModules.push(module.id);
      return;
    }
    validateStatus(invalidStatuses, `modules.${module.id}`, manifestModule.status);
    const manifestEntries = new Map((manifestModule.apiEntries || []).map((entry) => [entry.id, entry]));
    const inventoryEntries = new Set(module.exports.entries.map((entry) => entry.id));
    module.exports.entries.forEach((entry) => {
      const manifestEntry = manifestEntries.get(entry.id);
      if (!manifestEntry) {
        missingEntries.push(entry.id);
      } else {
        validateStatus(invalidStatuses, `modules.${module.id}.apiEntries.${entry.id}`, manifestEntry.status);
      }
    });
    manifestEntries.forEach((_, id) => {
      if (!inventoryEntries.has(id)) {
        extraEntries.push(id);
      }
    });
  });
  manifestModules.forEach((_, id) => {
    if (!inventoryModules.has(id)) {
      extraModules.push(id);
    }
  });
  return {
    valid: invalidStatuses.length === 0 &&
      missingModules.length === 0 &&
      extraModules.length === 0 &&
      missingEntries.length === 0 &&
      extraEntries.length === 0,
    invalidStatuses,
    missingModules,
    extraModules,
    missingEntries,
    extraEntries,
  };
}

function decideModuleStatus(module, gapModule) {
  const nodeEvidence = gapModule ? gapModule.nodeEvidence : null;
  const bridgeModules = nodeEvidence ? nodeEvidence.bridge.modules : [];
  const typeModules = nodeEvidence ? nodeEvidence.types.modules : [];
  const runtimeModules = nodeEvidence ? nodeEvidence.runtime.modules : [];
  const nodeModules = unique([...bridgeModules, ...typeModules, ...runtimeModules]).sort();
  if (isBlockedModule(module.id)) {
    return {
      status: "blocked_by_design",
      asyncMode: "not_applicable",
      nodeModules,
      bridgeModules,
      decision: "safe_node_profile_boundary",
      notes: ["Raw Java/thread/root automation compatibility is not exposed by default in Node."],
    };
  }
  if (bridgeModules.length > 0 && typeModules.length > 0) {
    return {
      status: "partial",
      asyncMode: "promise",
      nodeModules,
      bridgeModules,
      decision: "node_surface_exists_compat_facade_pending",
      notes: ["Node bridge/types evidence exists, but Rhino facade parity still needs explicit compatibility wrappers."],
    };
  }
  if (bridgeModules.length > 0 || typeModules.length > 0 || runtimeModules.length > 0) {
    return {
      status: "partial",
      asyncMode: bridgeModules.length > 0 ? "promise" : "unknown",
      nodeModules,
      bridgeModules,
      decision: "partial_node_surface_exists",
      notes: ["Some Node evidence exists, but the Rhino module shape is not yet covered."],
    };
  }
  return {
    status: "unsupported",
    asyncMode: "unknown",
    nodeModules,
    bridgeModules,
    decision: "no_node_surface_evidence",
    notes: ["No matching Node bridge/types/runtime surface was found for this Rhino module."],
  };
}

function decideApiStatus(module, entry, gapModule, moduleDecision) {
  const gapEntry = gapModule ? gapModule.apiEntries.find((candidate) => candidate.id === entry.id) : null;
  const bridgeModules = gapModule ? gapModule.nodeEvidence.bridge.modules : [];
  const nodeModules = moduleDecision.nodeModules || [];
  const bridgeModule = bridgeModules[0] || null;
  if (moduleDecision.status === "blocked_by_design") {
    return {
      status: "blocked_by_design",
      asyncMode: "not_applicable",
      nodeModule: nodeModules[0] || null,
      bridgeModule,
      nodeName: null,
      decision: moduleDecision.decision,
      notes: moduleDecision.notes,
    };
  }
  if (gapEntry && (gapEntry.bridgeMethodMatch || gapEntry.typeMethodMatch)) {
    return {
      status: "supported_async",
      asyncMode: "promise",
      nodeModule: nodeModules[0] || null,
      bridgeModule,
      nodeName: entry.exposedName,
      decision: "exact_node_method_match",
      notes: ["Rhino exposed name matches an existing Node bridge/type method; facade shape still controls sync-vs-async compatibility."],
    };
  }
  if (moduleDecision.status === "partial") {
    return {
      status: "partial",
      asyncMode: moduleDecision.asyncMode || "unknown",
      nodeModule: nodeModules[0] || null,
      bridgeModule,
      nodeName: null,
      decision: "module_surface_exists_api_mapping_pending",
      notes: ["A related Node surface exists, but this Rhino API needs an explicit mapping decision."],
    };
  }
  return {
    status: "unsupported",
    asyncMode: "unknown",
    nodeModule: nodeModules[0] || null,
    bridgeModule,
    nodeName: null,
    decision: "no_node_api_mapping",
    notes: ["No matching Node API mapping exists yet."],
  };
}

function migrationFromManifestRecord(record) {
  return withoutNullish({
    status: record.status,
    nodeModules: record.nodeModules,
    bridgeModules: record.bridgeModules,
    nodeModule: record.nodeModule || (record.nodeModules && record.nodeModules[0]) || undefined,
    bridgeModule: record.bridgeModule || (record.bridgeModules && record.bridgeModules[0]) || undefined,
    nodeName: record.nodeName,
    asyncMode: record.asyncMode,
    decision: record.decision,
    notes: record.notes || [],
  });
}

function isBlockedModule(id) {
  return BLOCKED_MODULE_PATTERNS.some((pattern) => pattern.test(id));
}

function validateStatus(invalidStatuses, path, status) {
  if (!STATUS_VOCABULARY.includes(status)) {
    invalidStatuses.push({ path, status });
  }
}

function countManifestStatuses(modules) {
  const counts = {};
  modules.forEach((module) => {
    increment(counts, module.status);
    module.apiEntries.forEach((entry) => increment(counts, entry.status));
  });
  return counts;
}

function countInventoryStatuses(modules) {
  const counts = {};
  modules.forEach((module) => {
    increment(counts, module.migration.status);
    module.exports.entries.forEach((entry) => increment(counts, entry.migration.status));
  });
  return counts;
}

function increment(counts, key) {
  counts[key] = (counts[key] || 0) + 1;
}

function resolveManifestPath(repoRoot, manifestPath) {
  return path.resolve(repoRoot, manifestPath || DEFAULT_STATUS_MANIFEST);
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function unique(values) {
  return Array.from(new Set(values.filter(Boolean)));
}

function withoutNullish(value) {
  const result = {};
  Object.keys(value).forEach((key) => {
    if (value[key] !== null && value[key] !== undefined) {
      result[key] = value[key];
    }
  });
  return result;
}

module.exports = {
  STATUS_MANIFEST_SCHEMA,
  DEFAULT_STATUS_MANIFEST,
  STATUS_VOCABULARY,
  buildStatusManifest,
  loadStatusManifest,
  applyStatusManifest,
  validateStatusManifest,
};
