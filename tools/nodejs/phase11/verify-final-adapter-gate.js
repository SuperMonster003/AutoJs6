#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FINAL_OPERATIONS = [
  "create_runtime",
  "execute_script",
  "execute_commonjs",
  "execute_module",
  "poll_runtime",
  "request_stop",
  "force_stop",
  "get_state",
  "get_diagnostics",
  "destroy_runtime",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const headerPath = path.join(repoRoot, "app/src/main/cpp/node_runtime_api_v1.h");
  const adapterPath = path.join(repoRoot, "app/src/main/cpp/node_runtime_adapter_24_5.cpp");
  const jniPath = path.join(repoRoot, "app/src/main/cpp/node_bridge_jni.cpp");
  const header = read(headerPath);
  const adapter = read(adapterPath);
  const jni = read(jniPath);
  const publicAbiLeaks = ["node::", "v8::", "uv_", "JNIEnv", "jobject", "jstring", "std::"]
    .filter((token) => header.includes(token));
  const operations = FINAL_OPERATIONS.map((operation) => ({
    operation,
    inHeader: header.includes(operation),
    inAdapter: adapter.includes(operation) || adapter.includes(toCamel(operation)),
  }));
  const adapterSurfaceOnly = adapter.includes("\"status\":\"surface_only\"") ||
    adapter.includes("AUTOJS_NODE_RESULT_UNAVAILABLE") ||
    adapter.includes("ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED");
  const selectorReady = jni.includes("adapter_v1") && jni.includes("legacy_jni");
  const blockers = [];
  operations.filter((entry) => !entry.inHeader).forEach((entry) => {
    blockers.push("missing final ABI operation in header: " + entry.operation);
  });
  if (publicAbiLeaks.length > 0) {
    blockers.push("public runtime ABI leaks forbidden native tokens: " + publicAbiLeaks.join(", "));
  }
  if (adapterSurfaceOnly) {
    blockers.push("Node 24.5 adapter remains surface_only and returns unavailable for execution");
  }
  if (!selectorReady) {
    blockers.push("JNI execution selector for adapter_v1/legacy_jni is not implemented");
  }
  const report = {
    schema: "autojs6-node-final-adapter-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalAdapterGate",
    tasks: ["S11-05", "S11-06", "S11-07", "S11-08", "S11-09", "S11-10", "S11-11", "S11-12"],
    header: relativePath(repoRoot, headerPath),
    adapter: relativePath(repoRoot, adapterPath),
    operations,
    publicAbiLeaks,
    adapterSurfaceOnly,
    selectorReady,
    legacyJniFallbackRequired: true,
    defaultRuntimePath: adapterSurfaceOnly ? "legacy_jni" : "adapter_v1",
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      adapterV1DefaultAllowed: blockers.length === 0,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-adapter-gate", report, renderMarkdown);
  console.log("Final adapter gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Final Adapter Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Default runtime path: ${report.defaultRuntimePath}`,
    `- Adapter v1 default allowed: ${report.decision.adapterV1DefaultAllowed}`,
    `- Public ABI leaks: ${report.publicAbiLeaks.length}`,
    "",
    "| Operation | Header | Adapter Source |",
    "| --- | --- | --- |",
  ];
  report.operations.forEach((entry) => {
    lines.push(`| ${entry.operation} | ${entry.inHeader} | ${entry.inAdapter} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function toCamel(value) {
  return value.replace(/_([a-z])/g, (_, ch) => ch.toUpperCase());
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-final-adapter-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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

main();
