#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const jniLibsRoot = path.resolve(options.jniLibsRoot || path.join(repoRoot, "app/src/main/jniLibs"));
  const cxxRoot = path.resolve(options.cxxRoot || path.join(repoRoot, "app/build/intermediates/cxx"));
  const bundledCoverage = REQUIRED_ABIS.map((abi) => ({
    abi,
    libnode: exists(jniLibsRoot, `${abi}/libnode.so`),
    libcxx: exists(jniLibsRoot, `${abi}/libc++_shared.so`) || hasBuiltLibrary(cxxRoot, abi, "libc++_shared.so"),
  }));
  const modes = [
    {
      id: "bundled",
      status: bundledCoverage.every((entry) => entry.libnode && entry.libcxx) ? "available" : "blocked",
      detail: "Runtime is packaged inside the base APK from the configured JNI library root.",
    },
    {
      id: "split-by-abi",
      status: "planned",
      detail: "Build-system ABI split support must consume the same runtime manifest before promotion.",
    },
    {
      id: "app-bundle-dynamic-feature",
      status: "planned",
      detail: "Runtime remains application-delivered code; no arbitrary external runtime download.",
    },
    {
      id: "localDeveloperRuntime",
      status: "planned-debug-only",
      detail: "Allowed only for debug/developer builds with app-private signed and read-only artifacts.",
    },
    {
      id: "remoteNativeRuntimeDownload",
      status: "denied",
      detail: "Unrestricted remote native runtime download is not supported.",
    },
  ];
  const blockers = [];
  if (modes.find((mode) => mode.id === "bundled").status !== "available") {
    blockers.push("bundled runtime mode is missing required current-runtime libraries");
  }
  const warnings = modes
    .filter((mode) => mode.status === "planned" || mode.status === "planned-debug-only")
    .map((mode) => `${mode.id} remains ${mode.status}`);
  const report = {
    schema: "autojs6-node-runtime-distribution-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-13 Runtime Distribution Modes",
    requiredAbis: REQUIRED_ABIS,
    jniLibsRoot: relativePath(repoRoot, jniLibsRoot),
    cxxRoot: relativePath(repoRoot, cxxRoot),
    bundledCoverage,
    modes,
    decision: {
      status: blockers.length > 0 ? "blocked" : warnings.length > 0 ? "bootstrap_only" : "ready",
      blockers,
      warnings,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "runtime-distribution-policy", report, renderMarkdown);
  console.log("Runtime distribution policy decision: " + report.decision.status);
  if (report.decision.status === "blocked" || (options.failOnBootstrapOnly && report.decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/native/verify-runtime-distribution-policy.js [--repo-root dir] [--jni-libs-root dir] [--cxx-root dir] [--report-dir dir]");
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

function exists(root, relativePath) {
  return fs.existsSync(path.join(root, relativePath));
}

function hasBuiltLibrary(root, abi, name) {
  if (!fs.existsSync(root)) return false;
  return walkFiles(root).some((file) => path.basename(file) === name && file.split(/[\\/]/).includes(abi));
}

function walkFiles(root) {
  const result = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) stack.push(fullPath);
      else if (entry.isFile()) result.push(fullPath);
    }
  }
  return result;
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function writeReport(reportDir, baseName, report, renderer) {
  const jsonPath = path.join(reportDir, `${baseName}.json`);
  const markdownPath = path.join(reportDir, `${baseName}.md`);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderer(report));
  console.log(`${baseName} JSON: ${jsonPath}`);
  console.log(`${baseName} Markdown: ${markdownPath}`);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Runtime Distribution Policy");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("## Modes");
  lines.push("");
  lines.push("| Mode | Status | Detail |");
  lines.push("| --- | --- | --- |");
  for (const mode of report.modes) {
    lines.push(`| ${mode.id} | ${mode.status} | ${mode.detail} |`);
  }
  lines.push("");
  lines.push("## Bundled Coverage");
  lines.push("");
  lines.push("| ABI | libnode.so | libc++_shared.so |");
  lines.push("| --- | --- | --- |");
  for (const entry of report.bundledCoverage) {
    lines.push(`| ${entry.abi} | ${entry.libnode} | ${entry.libcxx} |`);
  }
  appendDecision(lines, report.decision);
  return lines.join("\n");
}

function appendDecision(lines, decision) {
  if (decision.blockers.length > 0) {
    lines.push("");
    lines.push("## Blockers");
    lines.push("");
    decision.blockers.forEach((issue) => lines.push(`- ${issue}`));
  }
  if (decision.warnings.length > 0) {
    lines.push("");
    lines.push("## Warnings");
    lines.push("");
    decision.warnings.forEach((warning) => lines.push(`- ${warning}`));
  }
  lines.push("");
}

main();
