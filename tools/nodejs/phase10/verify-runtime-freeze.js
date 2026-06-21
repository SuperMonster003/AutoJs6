#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const lockPath = path.join(repoRoot, "tools/nodejs/phase10/runtime-freeze.lock.json");
  const lock = readJson(lockPath);
  const violations = [];

  const libnode = Object.entries(lock.libnode || {}).map(([abi, expected]) => {
    const file = path.join(repoRoot, expected.path);
    const exists = fs.existsSync(file);
    const actual = exists ? sha256(file) : null;
    const matches = actual === expected.sha256;
    if (!exists) {
      violations.push(`missing libnode.so for ${abi}: ${expected.path}`);
    } else if (!matches) {
      violations.push(`libnode.so hash changed for ${abi}: expected ${expected.sha256}, actual ${actual}`);
    }
    return {
      abi,
      path: expected.path,
      expectedSha256: expected.sha256,
      actualSha256: actual,
      matches,
    };
  });

  const runtimeBuildLockPath = path.join(repoRoot, "tools/nodejs/runtime-build/runtime-build.lock.json");
  const runtimeBuildLock = readJson(runtimeBuildLockPath);
  const runtimeBuild = verifyRuntimeBuildLock(runtimeBuildLock, lock, violations);
  const runtimeCapabilities = verifyRuntimeCapabilities(
    path.join(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt"),
    lock,
    violations,
  );

  const report = {
    schema: "autojs6-node-phase10-runtime-freeze-report-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-01 Phase 10 Scope and Runtime Freeze",
    gate: "verifyNodePhase10RuntimeFreeze",
    defaultRuntimeSlot: lock.defaultRuntimeSlot,
    defaultRuntimeVersion: lock.defaultRuntimeVersion,
    node2417Status: lock.node2417Status,
    node2417PromotionAllowed: lock.node2417PromotionAllowed,
    moduleSources: lock.moduleSources,
    libnode,
    runtimeBuild,
    runtimeCapabilities,
    blockedDefaults: lock.blockedDefaults || [],
    policyViolations: violations,
    decision: {
      status: violations.length === 0 ? "ready" : "failed",
      runtimeFrozen: violations.length === 0,
      node24CurrentPromoted: false,
      libnodeChanged: libnode.some((entry) => !entry.matches),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-runtime-freeze", report, renderMarkdown);
  console.log("Phase 10 runtime freeze decision: " + report.decision.status);
  if (violations.length > 0) {
    process.exitCode = 1;
  }
}

function verifyRuntimeBuildLock(runtimeBuildLock, lock, violations) {
  const promotionDecision = runtimeBuildLock.promotionDecision || {};
  const defaultSlot = promotionDecision.defaultRuntimeSlot || runtimeBuildLock.defaultRuntimeSlot || null;
  const node2417 = promotionDecision.node2417Promotion || runtimeBuildLock.node2417Promotion || null;
  const slots = runtimeBuildLock.runtimeSlots || runtimeBuildLock.slots || {};
  const node245 = slots.node24_5 || {};
  const node245Version = promotionDecision.defaultRuntimeVersion ||
    node245.version ||
    node245.nodeVersion ||
    (runtimeBuildLock.node && runtimeBuildLock.node.currentVersion) ||
    null;

  if (defaultSlot !== lock.defaultRuntimeSlot) {
    violations.push(`default runtime slot drifted: expected ${lock.defaultRuntimeSlot}, actual ${defaultSlot}`);
  }
  if (node245Version && node245Version !== lock.defaultRuntimeVersion) {
    violations.push(`node24_5 runtime version drifted: expected ${lock.defaultRuntimeVersion}, actual ${node245Version}`);
  }
  if (node2417 === "promoted" || node2417 === "ready") {
    violations.push(`Node 24.17 promotion is not allowed during Phase 10: ${node2417}`);
  }
  return {
    path: "tools/nodejs/runtime-build/runtime-build.lock.json",
    defaultRuntimeSlot: defaultSlot,
    node245Version,
    node2417Promotion: node2417,
    status: violations.length === 0 ? "frozen" : "check_report",
  };
}

function verifyRuntimeCapabilities(file, lock, violations) {
  const text = fs.readFileSync(file, "utf8");
  const values = {
    nodeHeaderVersion: extractConst(text, "NODE_HEADER_VERSION"),
    nodeExpectedPrefix: extractConst(text, "NODE_VERSION_EXPECTED_PREFIX"),
    moduleSourceMaxCount: Number(extractConst(text, "RUNTIME_MODULE_SOURCE_MAX_COUNT")),
    moduleSourceMaxBytes: Number(extractConst(text, "RUNTIME_MODULE_SOURCE_MAX_BYTES").replace(/_/g, "")),
    moduleSourcesMaxBytes: Number(extractConst(text, "RUNTIME_MODULE_SOURCES_MAX_BYTES").replace(/_/g, "")),
  };
  if (values.nodeHeaderVersion !== lock.defaultRuntimeVersion) {
    violations.push(`NODE_HEADER_VERSION drifted: expected ${lock.defaultRuntimeVersion}, actual ${values.nodeHeaderVersion}`);
  }
  if (values.nodeExpectedPrefix !== lock.nodeExpectedPrefix) {
    violations.push(`NODE_VERSION_EXPECTED_PREFIX drifted: expected ${lock.nodeExpectedPrefix}, actual ${values.nodeExpectedPrefix}`);
  }
  if (values.moduleSourceMaxCount !== lock.moduleSources.maxCount) {
    violations.push(`moduleSources max count drifted: expected ${lock.moduleSources.maxCount}, actual ${values.moduleSourceMaxCount}`);
  }
  if (values.moduleSourceMaxBytes !== lock.moduleSources.maxSingleBytes) {
    violations.push(`moduleSource max bytes drifted: expected ${lock.moduleSources.maxSingleBytes}, actual ${values.moduleSourceMaxBytes}`);
  }
  if (values.moduleSourcesMaxBytes !== lock.moduleSources.maxTotalBytes) {
    violations.push(`moduleSources total bytes drifted: expected ${lock.moduleSources.maxTotalBytes}, actual ${values.moduleSourcesMaxBytes}`);
  }
  return {
    path: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
    ...values,
  };
}

function extractConst(text, name) {
  const stringMatch = text.match(new RegExp(`const\\s+val\\s+${escapeRegExp(name)}\\s*=\\s*"([^"]+)"`));
  if (stringMatch) {
    return stringMatch[1];
  }
  const numberMatch = text.match(new RegExp(`const\\s+val\\s+${escapeRegExp(name)}\\s*=\\s*([0-9_]+)`));
  if (numberMatch) {
    return numberMatch[1];
  }
  throw new Error(`Missing const val ${name}`);
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Runtime Freeze",
    "",
    `- Decision: ${report.decision.status}`,
    `- Default runtime slot: ${report.defaultRuntimeSlot}`,
    `- Default runtime version: ${report.defaultRuntimeVersion}`,
    `- Node 24.17 status: ${report.node2417Status}`,
    `- Node 24.17 promotion allowed: ${report.node2417PromotionAllowed}`,
    `- moduleSources max count: ${report.moduleSources.maxCount}`,
    `- moduleSource max bytes: ${report.moduleSources.maxSingleBytes}`,
    `- moduleSources total bytes: ${report.moduleSources.maxTotalBytes}`,
    "",
    "## libnode.so Hashes",
    "",
    "| ABI | Match | SHA-256 |",
    "| --- | --- | --- |",
  ];
  for (const entry of report.libnode) {
    lines.push(`| ${entry.abi} | ${entry.matches ? "yes" : "no"} | ${entry.actualSha256 || "<missing>"} |`);
  }
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
      console.log("Usage: node verify-runtime-freeze.js [--repo-root dir] [--report-dir dir]");
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

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

main();
