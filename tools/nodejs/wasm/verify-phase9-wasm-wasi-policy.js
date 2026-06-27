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
    wasmLoadingSection(files),
    wasmCompileCacheSection(files),
    wasiFacadeSection(files),
    wasmResourceLimitsSection(files),
    wasmWorkerSection(files),
    wasmBuildWorkflowSection(files),
    wasmSecurityCorpusSection(files),
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
    schema: "autojs6-node-phase9-wasm-wasi-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-34..S9-40 WebAssembly And Controlled WASI Policy",
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-wasm-wasi-policy", report, renderMarkdown);
  console.log("Phase 9 WASM/WASI policy decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    wasmTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt",
    capabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
    bridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
    compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
    wasmDoc: "docs/nodejs/reports/phase9/WASM_WASI.md",
    testingDoc: "docs/nodejs/TESTING.md",
    ciGatesDoc: "docs/nodejs/CI_GATES.md",
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

function wasmLoadingSection(files) {
  const checks = [
    checkTokens("device-smoke-test", files.wasmTest, [
      "NodeWasmWasiInstrumentationTest",
      "webAssemblyCompileInstantiateAndFileAssetWork",
      "WebAssembly.compile",
      "WebAssembly.instantiate",
      "WebAssembly.Module",
      "WebAssembly.validate",
      "fs.readFileSync(\"./add.wasm\")",
      "ADD_WASM_BYTES",
      "wasm.basic=true",
    ]),
    checkTokens("compatibility-profile", files.compatibilityProfile, [
      "WebAssembly is available as a pure JavaScript/V8 capability",
      "`WebAssembly.compile`",
      "`WebAssembly.instantiate`",
      "scoped `.wasm` file loading",
    ]),
    checkTokens("phase9-doc", files.wasmDoc, [
      "S9-34 through S9-40",
      "WebAssembly.compile",
      "WebAssembly.instantiate",
      ".wasm",
      "existing scoped `fs` path",
    ]),
    checkTokens("gate-docs", files.testingDoc, [
      "verifyNodeWasmWasiGate",
      "WebAssembly.compile",
      "scoped `.wasm` file loading",
    ]),
  ];
  return makeSection({
    id: "wasm-module-loading",
    task: "S9-34 WebAssembly Module Loading",
    statusWhenPassing: "ready",
    checks,
    warnings: [
      "ESM WebAssembly import and packaged binary asset manifest coverage remain outside this first focused gate.",
    ],
  });
}

function wasmCompileCacheSection(files) {
  const checks = [
    checkTokens("doc-remaining-work", files.wasmDoc, [
      "WASM compile-cache policy and quota",
      "Later Phase 9 WASM/WASI work still needs",
    ]),
  ];
  return makeSection({
    id: "wasm-compile-cache",
    task: "S9-35 WASM Compile Cache",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "No persisted WASM compile-cache exists yet; future cache keys must include V8/runtime identity, ABI, wasm hash, compile options, feature flags, and app-private quota state.",
    ],
  });
}

function wasiFacadeSection(files) {
  const checks = [
    checkTokens("raw-wasi-disabled-test", files.wasmTest, [
      "rawWasiBuiltinsRemainDisabledAndCannotBeShadowed",
      "NodeRuntimeCapabilities.disabledBuiltinModuleNames.contains(\"wasi\")",
      "require(\"wasi\")",
      "require(\"node:wasi\")",
      "require.resolve(\"wasi\")",
      "require.resolve(\"node:wasi\")",
      "wasi.shadowLoaded=false",
    ]),
    checkTokens("capability-registry", files.capabilities, [
      "\"wasi\"",
      "disabledBuiltinModuleNames",
    ]),
    checkTokens("native-disabled-builtins", files.bridgeSources, [
      '"wasi": true',
      "disabledBuiltins",
      "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
    ]),
    checkTokens("facade-policy-doc", files.wasmDoc, [
      "Raw `node:wasi` remains disabled",
      "Future `autojs6:wasi` must map preopens through the scoped-fs broker",
      "deny arbitrary host paths",
      "inherited process env",
      "raw fd numbers",
      "unrestricted sockets",
      "process termination",
    ]),
  ];
  return makeSection({
    id: "controlled-wasi-facade",
    task: "S9-36 autojs6:wasi Facade",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Raw WASI denial is ready, but the controlled autojs6:wasi facade is not implemented.",
    ],
  });
}

function wasmResourceLimitsSection(files) {
  const checks = [
    checkTokens("resource-limit-doc", files.wasmDoc, [
      "resource limits for module size, memory pages, stdout/stderr, timeout, and",
      "concurrent instances",
    ]),
  ];
  return makeSection({
    id: "wasm-resource-limits",
    task: "S9-37 WASM Resource Limits",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Current scripts still rely on execution timeout and existing runtime limits; WASM-specific module byte, memory page, table, stdout/stderr, and concurrency caps remain future work.",
    ],
  });
}

function wasmWorkerSection(files) {
  const checks = [
    checkTokens("worker-doc", files.wasmDoc, [
      "worker/process isolation for heavy modules",
    ]),
    checkTokens("worker-status-profile", files.compatibilityProfile, [
      "`worker_threads` remains disabled by default",
      "worker_threads",
    ]),
  ];
  return makeSection({
    id: "wasm-worker-integration",
    task: "S9-38 WASM Worker Integration",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "WASM worker integration remains blocked behind worker/process isolation promotion.",
    ],
  });
}

function wasmBuildWorkflowSection(files) {
  const checks = [
    checkTokens("build-workflow-doc", files.wasmDoc, [
      "Rust, AssemblyScript, and WASI build workflow templates",
    ]),
  ];
  return makeSection({
    id: "wasm-build-workflow",
    task: "S9-39 Rust / AssemblyScript Build Workflow",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Rust wasm32-wasip1, wasm32-unknown-unknown, and AssemblyScript templates are not scaffolded yet.",
    ],
  });
}

function wasmSecurityCorpusSection(files) {
  const checks = [
    checkTokens("security-corpus-doc", files.wasmDoc, [
      "WASM/WASI security corpus",
    ]),
    checkTokens("raw-wasi-shadow-denial", files.wasmTest, [
      "node_modules/wasi",
      "wasi.shadowLoaded=false",
      "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
    ]),
    checkTokens("ci-doc", files.ciGatesDoc, [
      "verifyNodeWasmWasiGate",
      "raw `wasi` / `node:wasi` remains disabled",
    ]),
  ];
  return makeSection({
    id: "wasm-wasi-security-corpus",
    task: "S9-40 WASM/WASI Security Corpus",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Current security corpus covers raw WASI denial and shadowing. Preopen escape, symlink escape, malformed binary, stale cache, timeout, stdout, and packaged asset tamper cases remain future work.",
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
  lines.push("# AutoJs6 Node Phase 9 WASM/WASI Policy Gate");
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
      console.log("Usage: node tools/nodejs/wasm/verify-phase9-wasm-wasi-policy.js [--repo-root dir] [--report-dir dir]");
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
