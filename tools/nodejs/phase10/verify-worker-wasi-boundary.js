#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const CHECKS = [
  {
    id: "s10_37_worker_capability_probe",
    status: "blocked_by_native_availability",
    required: [
      ["workerSupport", "probeNativeWorkerThreads"],
      ["workerSupport", "Native worker_threads unavailable"],
      ["workerBootstrapTest", "experimentalRequestReportsNativeAvailabilityOrStableDisabled"],
      ["workerRecheck", "cannot promote `worker_threads`"],
    ],
  },
  {
    id: "s10_38_controlled_worker_mvp",
    status: "blocked",
    required: [
      ["workerDesignTest", "workerThreadsRemainDesignGatedAndDisabledByDefault"],
      ["workerBootstrapTest", "defaultRequestDeniesWorkerThreadsBeforeShadowing"],
      ["buildGradle", "autojs.nodejs.worker_threads.experimental"],
      ["buildGradle", "NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED"],
      ["compatibilityProfile", "`worker_threads` remains disabled by default"],
    ],
  },
  {
    id: "s10_39_message_channel_transfer",
    status: "blocked",
    required: [
      ["workerExperimentalTest", "MessageChannel"],
      ["workerExperimentalTest", "transferList"],
      ["workerRecheck", "MessageChannel/transferList"],
    ],
    blocker: "native worker_threads remains unavailable, so runnable MessageChannel and transfer coverage still skips",
  },
  {
    id: "s10_40_worker_pool_resource_limits",
    status: "blocked",
    required: [
      ["workerResourceStressTest", "WorkerPool"],
      ["workerResourceStressTest", "infiniteLoopWorkerTimeoutDoesNotPoisonNextRun"],
      ["workerResourceStressTest", "pendingWorkerMessageIsRejectedDuringParentCompletionCleanup"],
      ["workerRecheck", "WorkerPool"],
    ],
    blocker: "resource stress remains promotion-gated until native workers run",
  },
  {
    id: "s10_41_controlled_wasi_profile",
    status: "deferred",
    required: [
      ["wasmWasiTest", "rawWasiBuiltinsRemainDisabledAndCannotBeShadowed"],
      ["phase9WasmWasi", "Raw `node:wasi` remains disabled"],
      ["phase9WasmWasi", "Future `autojs6:wasi` must map preopens"],
      ["compatibilityProfile", "Raw `wasi`"],
    ],
    blocker: "autojs6:wasi scoped facade is not implemented; raw node:wasi stays disabled",
  },
  {
    id: "s10_42_wasm_compile_cache_packaged_support",
    status: "partial_wasm_only",
    required: [
      ["wasmWasiTest", "webAssemblyCompileInstantiateAndFileAssetWork"],
      ["wasmWasiTest", "WebAssembly.compile"],
      ["wasmWasiTest", "WebAssembly.instantiate"],
      ["phase9WasmWasi", "WASM compile-cache policy and quota"],
      ["phase9WasmWasi", "packaged binary asset manifest"],
    ],
    blocker: "pure WebAssembly works, but WASM compile cache, packaged .wasm asset manifest, worker+WASM, and controlled WASI module support remain future gates",
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const policyViolations = [];
  const items = CHECKS.map((check) => evaluateCheck(check, files, policyViolations));
  const report = {
    schema: "autojs6-node-phase10-worker-wasi-boundary-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-37..S10-42 Worker And Controlled WASI/WASM Boundary",
    gate: "verifyNodePhase10WorkerWasiBoundary",
    items,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready_with_recorded_blockers" : "failed",
      workerThreadsPromoted: false,
      rawWasiPromoted: false,
      wasmBasicPromoted: true,
      controlledWasiPromoted: false,
      packagedWasmPromoted: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-worker-wasi-boundary", report, renderMarkdown);
  console.log("Phase 10 worker/WASI boundary decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function evaluateCheck(check, files, policyViolations) {
  const evidence = check.required.map(([source, token]) => {
    const file = files[source];
    const matched = Boolean(file && file.text.includes(token));
    if (!matched) {
      policyViolations.push(`${check.id} missing token ${token} in ${source}`);
    }
    return {
      source,
      path: file ? file.path : null,
      token,
      matched,
    };
  });
  return {
    id: check.id,
    status: check.status,
    evidence,
    blocker: check.blocker || null,
    ready: evidence.every((entry) => entry.matched),
  };
}

function loadFiles(repoRoot) {
  return {
    buildGradle: read(repoRoot, "app/build.gradle.kts"),
    workerSupport: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt"),
    workerBootstrapTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt"),
    workerDesignTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"),
    workerExperimentalTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt"),
    workerResourceStressTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt"),
    wasmWasiTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt"),
    workerRecheck: read(repoRoot, "docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md"),
    phase9WasmWasi: read(repoRoot, "docs/nodejs/reports/phase9/WASM_WASI.md"),
    compatibilityProfile: read(repoRoot, "docs/nodejs/COMPATIBILITY_PROFILE.md"),
  };
}

function read(repoRoot, relative) {
  const file = path.join(repoRoot, relative);
  return {
    path: relative,
    text: fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "",
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Worker/WASI Boundary",
    "",
    `- Decision: ${report.decision.status}`,
    `- worker_threads promoted: ${report.decision.workerThreadsPromoted}`,
    `- raw WASI promoted: ${report.decision.rawWasiPromoted}`,
    `- WebAssembly basic promoted: ${report.decision.wasmBasicPromoted}`,
    `- controlled WASI promoted: ${report.decision.controlledWasiPromoted}`,
    `- packaged WASM promoted: ${report.decision.packagedWasmPromoted}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "| Item | Status | Ready | Blocker |",
    "| --- | --- | --- | --- |",
  ];
  report.items.forEach((item) => {
    lines.push(`| ${item.id} | ${item.status} | ${item.ready ? "yes" : "no"} | ${item.blocker || ""} |`);
  });
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
      console.log("Usage: node verify-worker-wasi-boundary.js [--repo-root dir] [--report-dir dir]");
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

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

main();
