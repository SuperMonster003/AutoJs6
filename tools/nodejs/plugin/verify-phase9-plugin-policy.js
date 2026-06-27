#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const sections = [
    wasmPluginPackageSection(files),
    nativeServicePluginSection(files),
    pluginAbiLifecycleSection(files),
  ];
  const blockers = sections.flatMap((section) => section.blockers.map((blocker) => `${section.id}: ${blocker}`));
  const warnings = sections.flatMap((section) => section.warnings.map((warning) => `${section.id}: ${warning}`));
  const nonReady = sections.filter((section) => section.status !== "ready").map((section) => `${section.id}=${section.status}`);
  const decision = {
    status: blockers.length > 0 ? "blocked" : nonReady.length > 0 ? "bootstrap_only" : "ready",
    blockers,
    warnings: warnings.concat(nonReady.map((entry) => `section is not fully production-ready: ${entry}`)),
  };
  const report = {
    schema: "autojs6-node-phase9-plugin-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-41..S9-43 WASM Plugin Package, Native Service Plugin, And Plugin ABI Policy",
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-plugin-policy", report, renderMarkdown);
  console.log("Phase 9 plugin policy decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    pluginTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt",
    bridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
    pluginDesign: "docs/nodejs/design/PLUGIN_SYSTEM.md",
    compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
    capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
    wasmPolicy: "docs/nodejs/reports/phase9/WASM_WASI.md",
    phase9Policy: "docs/nodejs/reports/phase9/PLUGIN_POLICY.md",
  };
  const loaded = {};
  for (const [key, relativePath] of Object.entries(paths)) {
    loaded[key] = {
      relativePath,
      text: readIfExists(path.join(repoRoot, relativePath)),
    };
  }
  return loaded;
}

function wasmPluginPackageSection(files) {
  const checks = [
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-41 WASM Plugin Package",
      "Current state: deferred",
      "declared WASM entry",
      "memory/time limits",
      "dependency manifest",
      "package integrity metadata",
      "Raw `node:wasi` stays disabled",
    ]),
    checkTokens("plugin-archive-design", files.pluginDesign, [
      "Future Package Format",
      "autojs6-node-plugin.json",
      "assets/",
      "exact file hashes",
      "native-code declaration, default `false`",
      "The manifest must be parsed before any plugin code is resolved.",
    ]),
    checkTokens("wasm-boundary", files.wasmPolicy, [
      "WebAssembly is allowed as a pure JavaScript/V8 capability",
      "Raw `node:wasi` remains disabled",
      "packaged binary asset manifest and tamper checks",
      "resource limits for module size, memory pages, stdout/stderr, timeout",
    ]),
  ];
  return makeSection({
    id: "wasm-plugin-package",
    task: "S9-41 WASM Plugin Package",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "The current JS-only manager does not load declared WASM plugin entries or signed plugin archives.",
    ],
  });
}

function nativeServicePluginSection(files) {
  const checks = [
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-42 Native Service Plugin Design Gate",
      "Current state: deferred",
      "separately signed Android plugin packages",
      "signature permission",
      "AIDL/Parcelable boundary",
      "capability-scoped RPC",
      "code loaded into the embedded Node process",
    ]),
    checkTokens("native-provider-design", files.pluginDesign, [
      "Signed native provider plugin",
      "Node native addon",
      "no direct Node `.node` loading into the embedded process",
      "native calls happen through a host-owned bridge or isolated service",
      "Native support must not be enabled merely because a plugin archive contains",
    ]),
    checkTokens("plugin-native-denial-test", files.pluginTest, [
      "pluginNativeAddonFileIsRejected",
      "addon.node",
      "plugins.load(\"org.example.native\")",
      "NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED",
    ]),
    checkTokens("compatibility-boundary", files.compatibilityProfile, [
      ".node` files anywhere inside a JS-only plugin directory",
      "native plugin archives",
      "Android provider",
      "plugin adapters",
    ]),
  ];
  return makeSection({
    id: "native-service-plugin",
    task: "S9-42 Native Service Plugin Design Gate",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Native service plugins are design-gated only; direct .node addons and native plugin archives remain disabled.",
    ],
  });
}

function pluginAbiLifecycleSection(files) {
  const checks = [
    checkTokens("manager-focused-test", files.pluginTest, [
      "NodePluginSystemInstrumentationTest",
      "jsOnlyPluginLoadsFromProjectPluginsDirectory",
      "pluginManagerInstallEnableConfigureExportsAndUnload",
      "pluginLifecycleResourcesAndDebugReload",
      "plugins.install(\"org.example.manager\"",
      "plugins.start(\"org.example.lifecycle\")",
      "plugins.stop(\"org.example.lifecycle\")",
      "plugins.unload(\"org.example.lifecycle\")",
      "plugins.reload(\"org.example.lifecycle\", { debug: true })",
      "plugins.capabilities(\"org.example.lifecycle\")",
    ]),
    checkTokens("native-bootstrap-surface", files.bridgeSources, [
      "__autojs6_plugin_registry",
      "__autojs6_plugin_resource_registry",
      "__autojs6_plugin_lifecycle_context",
      "__autojs6_plugin_start_record",
      "__autojs6_plugin_stop_record",
      "__autojs6_plugin_unload_record",
      "__autojs6_plugin_capability_subset",
    ]),
    checkTokens("capability-matrix", files.capabilityMatrix, [
      "| Plugin system | manager-v1 | JS-only local lifecycle manager |",
      "NodePluginSystemInstrumentationTest",
      "Exact `plugin.<id>` opt-in",
      "plugin-owned JS resource registry",
      "no download/signature/native/provider expansion",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-43 Plugin ABI And Lifecycle",
      "Current state: partial",
      "local manager-v1 ABI surface",
      "capability negotiation record",
      "health check",
      "timeout",
      "cancellation",
      "compatibility matrix",
    ]),
  ];
  return makeSection({
    id: "plugin-abi-lifecycle",
    task: "S9-43 Plugin ABI And Lifecycle",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "The JS-only manager ABI is partial; signed-plugin protocol versioning, negotiation records, health checks, cancellation, packaged verification, and update policy remain future work.",
    ],
  });
}

function makeSection({ id, task, statusWhenPassing, checks, warnings }) {
  const blockers = checks
    .filter((check) => !check.ok)
    .map((check) => `${check.id} missing ${check.missing.join(", ")}`);
  return {
    id,
    task,
    status: blockers.length > 0 ? "blocked" : statusWhenPassing,
    checks,
    blockers,
    warnings: blockers.length > 0 ? warnings.concat("Required source, test, or documentation evidence is missing.") : warnings,
  };
}

function checkTokens(id, file, tokens) {
  const missing = [];
  if (!file.text) {
    missing.push(`file:${file.relativePath}`);
  } else {
    for (const token of tokens) {
      if (!file.text.includes(token)) {
        missing.push(token);
      }
    }
  }
  return {
    id,
    path: file.relativePath,
    ok: missing.length === 0,
    missing,
  };
}

function readIfExists(file) {
  if (!fs.existsSync(file)) {
    return "";
  }
  return fs.readFileSync(file, "utf8");
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
  lines.push("# AutoJs6 Node Phase 9 Plugin Policy Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("| Section | Task | Status |");
  lines.push("| --- | --- | --- |");
  for (const section of report.sections) {
    lines.push(`| ${section.id} | ${section.task} | ${section.status} |`);
  }
  for (const section of report.sections) {
    lines.push("");
    lines.push(`## ${section.id}`);
    lines.push("");
    lines.push("| Check | OK | Path |");
    lines.push("| --- | --- | --- |");
    for (const check of section.checks) {
      lines.push(`| ${check.id} | ${check.ok} | ${check.path} |`);
    }
    appendIssues(lines, "Blockers", section.blockers);
    appendIssues(lines, "Warnings", section.warnings);
  }
  appendIssues(lines, "Decision Blockers", report.decision.blockers);
  appendIssues(lines, "Decision Warnings", report.decision.warnings);
  lines.push("");
  return lines.join("\n");
}

function appendIssues(lines, title, issues) {
  if (!issues || issues.length === 0) {
    return;
  }
  lines.push("");
  lines.push(`### ${title}`);
  lines.push("");
  issues.forEach((issue) => lines.push(`- ${issue}`));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/plugin/verify-phase9-plugin-policy.js [--repo-root dir] [--report-dir dir]");
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

main();
