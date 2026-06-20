#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-runtime-build-lock-v1";
const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const lockFile = path.resolve(options.lockFile || path.join(repoRoot, "tools/nodejs/runtime-build/runtime-build.lock.json"));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const issues = [];
  const warnings = [];
  if (!fs.existsSync(lockFile)) {
    issues.push("runtime build lock file is missing");
  }
  const lock = fs.existsSync(lockFile) ? JSON.parse(fs.readFileSync(lockFile, "utf8")) : {};
  if (lock.schema !== SCHEMA) {
    issues.push(`runtime build lock schema must be ${SCHEMA}`);
  }
  for (const abi of REQUIRED_ABIS) {
    if (!lock.abis || !lock.abis[abi]) {
      issues.push("missing ABI build lock entry: " + abi);
    }
  }
  if (!lock.node || !lock.node.targetVersion) {
    issues.push("missing node.targetVersion");
  }
  if (!lock.node || !lock.node.sourceCommit) {
    issues.push("missing node.sourceCommit");
  }
  if (!lock.node || !lock.node.nodejsMobileForkCommit) {
    issues.push("missing node.nodejsMobileForkCommit");
  }
  if (!lock.toolchain || !lock.toolchain.ndkVersion) {
    issues.push("missing toolchain.ndkVersion");
  }
  if (!lock.toolchain || !lock.toolchain.clangVersion) {
    issues.push("missing toolchain.clangVersion");
  }
  if (!lock.toolchain || !lock.toolchain.pythonVersion) {
    issues.push("missing toolchain.pythonVersion");
  }
  if (!lock.runtimeConfig || !lock.runtimeConfig.icu || !lock.runtimeConfig.openssl || !lock.runtimeConfig.libuv) {
    issues.push("runtimeConfig must lock ICU, OpenSSL, and libuv settings");
  }
  if (!lock.linker || !lock.linker.versionScript) {
    issues.push("missing linker.versionScript");
  }
  if (!Array.isArray(lock.configureArgs) || lock.configureArgs.length === 0) {
    issues.push("configureArgs must be a non-empty array");
  }
  if (lock.abis) {
    for (const abi of REQUIRED_ABIS) {
      const entry = lock.abis[abi];
      if (entry && (!Array.isArray(entry.cflags) || !Array.isArray(entry.ldflags))) {
        issues.push("ABI build lock entry must include cflags and ldflags arrays: " + abi);
      }
    }
  }
  const placeholders = collectPlaceholders(lock);
  if (placeholders.length > 0) {
    warnings.push("external artifact fields still require maintainer values: " + placeholders.join(", "));
  }
  const requiredScripts = [
    "tools/nodejs/runtime-build/build-node-runtime.ps1",
    "tools/nodejs/runtime-build/build-node-runtime.sh",
    "tools/nodejs/runtime-build/container/Dockerfile",
  ];
  for (const script of requiredScripts) {
    if (!fs.existsSync(path.join(repoRoot, script))) {
      issues.push("missing runtime build script: " + script);
    }
  }
  const decision = {
    status: issues.length === 0 && placeholders.length === 0 ? "ready" : issues.length === 0 ? "bootstrap_only" : "blocked",
    blockers: issues,
    warnings,
  };
  const report = {
    schema: "autojs6-node-runtime-build-plan-check-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-04 Reproducible Android Node Build Environment",
    lockFile: relativePath(repoRoot, lockFile),
    requiredAbis: REQUIRED_ABIS,
    lockSummary: {
      targetVersion: lock.node && lock.node.targetVersion,
      currentVersion: lock.node && lock.node.currentVersion,
      ndkVersion: lock.toolchain && lock.toolchain.ndkVersion,
      clangVersion: lock.toolchain && lock.toolchain.clangVersion,
      pythonVersion: lock.toolchain && lock.toolchain.pythonVersion,
      pageSizes: lock.toolchain && lock.toolchain.pageSizeBytes,
    },
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "runtime-build-plan.json");
  const markdownPath = path.join(reportDir, "runtime-build-plan.md");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderMarkdown(report));
  console.log("Runtime build plan JSON: " + jsonPath);
  console.log("Runtime build plan Markdown: " + markdownPath);
  console.log("Runtime build plan decision: " + decision.status);
  if (issues.length > 0 || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/runtime-build/verify-runtime-build-plan.js [--repo-root dir] [--lock-file file] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? ["1", "true", "yes", "on"].includes(arg.slice(eq + 1).toLowerCase()) : true;
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

function collectPlaceholders(value, pathParts = []) {
  const result = [];
  if (value === "TBD" || value === "EXTERNAL") {
    result.push(pathParts.join("."));
  } else if (Array.isArray(value)) {
    value.forEach((entry, index) => result.push(...collectPlaceholders(entry, pathParts.concat(String(index)))));
  } else if (value && typeof value === "object") {
    Object.entries(value).forEach(([key, entry]) => result.push(...collectPlaceholders(entry, pathParts.concat(key))));
  }
  return result;
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Runtime Build Plan Check");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("## Summary");
  lines.push("");
  lines.push(`- target Node: ${report.lockSummary.targetVersion || "missing"}`);
  lines.push(`- current Node: ${report.lockSummary.currentVersion || "missing"}`);
  lines.push(`- NDK: ${report.lockSummary.ndkVersion || "missing"}`);
  lines.push(`- Clang: ${report.lockSummary.clangVersion || "missing"}`);
  lines.push(`- Python: ${report.lockSummary.pythonVersion || "missing"}`);
  lines.push(`- page sizes: ${(report.lockSummary.pageSizes || []).join(", ")}`);
  if (report.decision.blockers.length > 0) {
    lines.push("");
    lines.push("## Blockers");
    lines.push("");
    report.decision.blockers.forEach((issue) => lines.push(`- ${issue}`));
  }
  if (report.decision.warnings.length > 0) {
    lines.push("");
    lines.push("## Warnings");
    lines.push("");
    report.decision.warnings.forEach((warning) => lines.push(`- ${warning}`));
  }
  lines.push("");
  return lines.join("\n");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

main();
