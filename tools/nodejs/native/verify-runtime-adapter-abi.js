#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-runtime-adapter-abi-check-v1";
const DEFAULT_HEADER = "app/src/main/cpp/node_runtime_api_v1.h";
const FORBIDDEN_TOKENS = [
  "std::",
  "std::string",
  "node::",
  "v8::",
  "uv_",
  "JNIEnv",
  "jobject",
  "jstring",
  "jclass",
];
const REQUIRED_TOKENS = [
  "AUTOJS_NODE_RUNTIME_API_ABI_VERSION",
  "AutoJsNodeHandle",
  "AutoJsNodeCreateOptions",
  "AutoJsNodeExecutionRequest",
  "AutoJsNodeExecutionResult",
  "AutoJsNodeDiagnostics",
  "AutoJsNodeRuntimeApiV1",
  "get_node_version",
  "get_runtime_descriptor",
  "create",
  "start",
  "execute",
  "request_stop",
  "destroy",
  "post_bridge_result",
  "get_diagnostics",
  "autojs_node_get_runtime_api_v1",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const headerPath = path.resolve(options.header || path.join(repoRoot, DEFAULT_HEADER));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const headerText = fs.existsSync(headerPath) ? fs.readFileSync(headerPath, "utf8") : "";
  const issues = [];
  if (!headerText) {
    issues.push("runtime adapter ABI header is missing");
  }
  for (const token of REQUIRED_TOKENS) {
    if (!headerText.includes(token)) {
      issues.push("required ABI token is missing: " + token);
    }
  }
  for (const token of FORBIDDEN_TOKENS) {
    if (headerText.includes(token)) {
      issues.push("forbidden C++/JNI boundary token appears in C ABI header: " + token);
    }
  }
  if (!/#ifdef __cplusplus[\s\S]*extern "C"[\s\S]*#endif/.test(headerText)) {
    issues.push("header must expose an extern \"C\" block for C++ callers");
  }
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    task: "S9-02 Versioned C ABI Runtime Adapter",
    header: relativePath(repoRoot, headerPath),
    requirements: {
      noStdStringAcrossAbi: !headerText.includes("std::string"),
      noV8HandleAcrossAbi: !headerText.includes("v8::"),
      noNodeEnvironmentAcrossAbi: !headerText.includes("node::"),
      noJniAcrossAbi: !/JNIEnv|jobject|jstring|jclass/.test(headerText),
      explicitStructSizeFields: [
        "AutoJsNodeCreateOptions",
        "AutoJsNodeExecutionRequest",
        "AutoJsNodeExecutionResult",
        "AutoJsNodeDiagnostics",
        "AutoJsNodeRuntimeApiV1",
      ].every((name) => structHasField(headerText, name, "struct_size")),
    },
    issues,
    decision: {
      status: issues.length === 0 ? "ready" : "blocked",
      blockers: issues,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "runtime-adapter-abi-v1.json");
  const markdownPath = path.join(reportDir, "runtime-adapter-abi-v1.md");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderMarkdown(report));
  console.log("Runtime adapter ABI JSON: " + jsonPath);
  console.log("Runtime adapter ABI Markdown: " + markdownPath);
  console.log("Runtime adapter ABI decision: " + report.decision.status);
  if (report.issues.length > 0) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/native/verify-runtime-adapter-abi.js [--repo-root dir] [--header file] [--report-dir dir]");
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

function structHasField(text, structName, fieldName) {
  const match = text.match(new RegExp("typedef\\s+struct\\s+" + structName + "\\s*\\{([\\s\\S]*?)\\}\\s*" + structName + "\\s*;"));
  return !!match && match[1].includes(fieldName);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Runtime Adapter ABI V1 Check");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Header: \`${report.header}\``);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("## Requirements");
  lines.push("");
  for (const [key, value] of Object.entries(report.requirements)) {
    lines.push(`- ${key}: ${value}`);
  }
  if (report.issues.length > 0) {
    lines.push("");
    lines.push("## Issues");
    lines.push("");
    report.issues.forEach((issue) => lines.push(`- ${issue}`));
  }
  lines.push("");
  return lines.join("\n");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

main();
