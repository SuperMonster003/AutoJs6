#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const ATTACKS = [
  {
    id: "packaged_esm_dynamic_import_escape",
    task: "S10-53",
    title: "Packaged ESM/dynamic import escape",
    status: "covered",
    evidence: [
      ["packagedParity", "installAndRunExperimentalEsmEmbeddedNodePackagedApk"],
      ["packagedParity", "installAndRunDynamicImportLocalEmbeddedNodePackagedApk"],
      ["packagedParity", "ERR_AUTOJS6_PACKAGED_RUNTIME_VERSION_MISMATCH"],
      ["bridgeSources", "package subpath escapes working directory"],
    ],
  },
  {
    id: "provider_capability_bypass",
    task: "S10-53",
    title: "Provider capability bypass",
    status: "covered",
    evidence: [
      ["bridgeSources", "ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"],
      ["bridgeSources", "capability-denied"],
      ["capabilityTruth", "live_provider"],
    ],
  },
  {
    id: "package_tar_path_escape",
    task: "S10-53",
    title: "Package tar path escape",
    status: "covered",
    evidence: [
      ["autojs6Pm", "ERR_AUTOJS6_PM_TAR_PATH_ESCAPE"],
      ["autojs6Pm", "createMaliciousTarSlipTgz"],
      ["autojs6Pm", "safe tgz extraction with Tar Slip and link rejection"],
    ],
  },
  {
    id: "package_integrity_mismatch",
    task: "S10-53",
    title: "Package integrity mismatch",
    status: "covered",
    evidence: [
      ["autojs6Pm", "ERR_AUTOJS6_PM_INTEGRITY_MISMATCH"],
      ["autojs6Pm", "integrity mismatch"],
      ["autojs6Pm", "verifyIntegrity"],
    ],
  },
  {
    id: "malicious_package_scripts",
    task: "S10-53",
    title: "Malicious package scripts",
    status: "covered",
    evidence: [
      ["autojs6Pm", "lifecycle script disabled"],
      ["autojs6Pm", "ERR_AUTOJS6_PM_COMPATIBILITY_BLOCKED"],
      ["realNpmTest", "FORBIDDEN_PACKAGE_SCRIPTS"],
    ],
  },
  {
    id: "long_running_quota_exhaustion",
    task: "S10-53",
    title: "Long-running quota exhaustion",
    status: "covered",
    evidence: [
      ["longRunning", "s10_31_quota_heartbeat_watchdog"],
      ["longRunning", "Quota"],
      ["longRunning", "heartbeat"],
    ],
  },
  {
    id: "multi_instance_cross_slot_access",
    task: "S10-53",
    title: "Multi-instance cross-slot access",
    status: "blocked_by_policy",
    evidence: [
      ["longRunning", "effectiveMaxActiveSlots remains 1"],
      ["processPoolTest", "one_active_environment_per_slot"],
      ["processPoolPolicy", "effective_max_active_slots"],
    ],
  },
  {
    id: "worker_bridge_access",
    task: "S10-53",
    title: "Worker bridge access",
    status: "blocked_by_policy",
    evidence: [
      ["workerDesignTest", "worker_threads design gate"],
      ["workerDesignTest", "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"],
      ["workerWasi", "worker_threads"],
    ],
  },
  {
    id: "wasi_preopen_escape",
    task: "S10-53",
    title: "WASI preopen escape",
    status: "blocked_by_policy",
    evidence: [
      ["workerWasi", "raw WASI promoted"],
      ["workerWasi", "Future `autojs6:wasi` must map preopens"],
      ["workerWasiDoc", "preopens"],
    ],
  },
  {
    id: "plugin_package_namespace_escape",
    task: "S10-53",
    title: "Plugin/package namespace escape",
    status: "covered",
    evidence: [
      ["pluginTest", "Node JS-only plugin path escape denial"],
      ["pluginDesign", "per-plugin namespace metadata"],
      ["bridgeDesign", "plugin entry path escape denial"],
    ],
  },
  {
    id: "adapter_malformed_abi_input",
    task: "S10-53",
    title: "Adapter malformed ABI input",
    status: "blocked_by_policy",
    evidence: [
      ["adapterAbi", "AutoJsNodeRuntimeApiV1"],
      ["adapterAbi", "forbidden C++/JNI boundary token"],
      ["adapterCutover", "ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED"],
      ["adapterExport", "autojs_node_get_runtime_api_v1"],
    ],
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const nativeCppRoot = resolveNativeCppRoot(repoRoot, options.nativeCppRoot);
  const files = loadEvidence(repoRoot, nativeCppRoot);
  const attacks = ATTACKS.map((attack) => evaluateAttack(attack, files));
  const policyViolations = [];

  for (const attack of attacks) {
    if (attack.missingEvidence.length > 0) {
      policyViolations.push(`${attack.id} missing evidence: ${attack.missingEvidence.join(", ")}`);
    }
    if (attack.status === "covered" && attack.evidenceMatches.length < 2) {
      policyViolations.push(`${attack.id} needs at least two evidence tokens for covered status`);
    }
  }

  const report = {
    schema: "autojs6-node-phase10-security-corpus-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-53 Phase 10 Security Corpus",
    gate: "verifyNodePhase10SecurityCorpus",
    attackCount: attacks.length,
    statusCounts: countBy(attacks, "status"),
    evidenceSources: Object.fromEntries(Object.entries(files).map(([key, value]) => [key, value.path])),
    attacks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      safeNodeProfilePreserved: true,
      blockedCapabilitiesPromoted: false,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-security-corpus", report, renderMarkdown);
  console.log("Phase 10 security corpus decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadEvidence(repoRoot, nativeCppRoot) {
  return {
    packagedParity: read(repoRoot, "tools/nodejs/phase10/generate-packaged-parity-report.js"),
    bridgeSources: readFile(repoRoot, path.join(nativeCppRoot, "node_bridge_sources.cpp")),
    capabilityTruth: read(repoRoot, "tools/nodejs/phase10/generate-capability-truth.js"),
    autojs6Pm: read(repoRoot, "tools/nodejs/phase10/autojs6-pm.js"),
    realNpmTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt"),
    longRunning: read(repoRoot, "tools/nodejs/phase10/verify-long-running-activation.js"),
    processPoolTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt"),
    processPoolPolicy: read(repoRoot, "app/src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt"),
    workerDesignTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt"),
    workerWasi: read(repoRoot, "tools/nodejs/phase10/verify-worker-wasi-boundary.js"),
    workerWasiDoc: read(repoRoot, "docs/nodejs/reports/phase10/WORKER_WASI_BOUNDARY.md"),
    pluginTest: read(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt"),
    pluginDesign: read(repoRoot, "docs/nodejs/design/PLUGIN_SYSTEM.md"),
    bridgeDesign: read(repoRoot, "docs/nodejs/design/BRIDGE_V1_1_MODULES.md"),
    adapterAbi: read(repoRoot, "tools/nodejs/native/verify-runtime-adapter-abi.js"),
    adapterCutover: read(repoRoot, "tools/nodejs/phase10/verify-crash-adapter-cutover.js"),
    adapterExport: read(repoRoot, "tools/nodejs/native/verify-runtime-adapter-export.js"),
  };
}

function evaluateAttack(attack, files) {
  const evidenceMatches = [];
  const missingEvidence = [];
  for (const [source, token] of attack.evidence) {
    const entry = files[source];
    if (entry && entry.text.includes(token)) {
      evidenceMatches.push({ source, path: entry.path, token });
    } else {
      missingEvidence.push(`${source}:${token}`);
    }
  }
  return {
    id: attack.id,
    task: attack.task,
    title: attack.title,
    status: attack.status,
    evidenceMatches,
    missingEvidence,
  };
}

function read(repoRoot, relative) {
  return readFile(repoRoot, path.join(repoRoot, relative));
}

function readFile(repoRoot, file) {
  return {
    path: path.relative(repoRoot, file).replace(/\\/g, "/"),
    text: fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "",
  };
}

function resolveNativeCppRoot(repoRoot, requested) {
  const candidates = [
    requested && path.resolve(requested),
    path.resolve(repoRoot, "../AutoJs6-Plugin-NodeJs-Runtime/app/src/main/cpp"),
    path.join(repoRoot, "app/src/main/cpp"),
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || candidates[candidates.length - 1];
}

function countBy(rows, field) {
  const counts = {};
  for (const row of rows) {
    const value = row[field] || "unknown";
    counts[value] = (counts[value] || 0) + 1;
  }
  return counts;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Security Corpus",
    "",
    `- Decision: ${report.decision.status}`,
    `- Attack surfaces: ${report.attackCount}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "| Attack | Status | Evidence |",
    "| --- | --- | --- |",
  ];
  for (const attack of report.attacks) {
    const evidence = attack.evidenceMatches.map((entry) => `${entry.source}`).join(", ");
    lines.push(`| ${attack.title} | ${attack.status} | ${evidence} |`);
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
      console.log("Usage: node verify-security-corpus.js [--repo-root dir] [--native-cpp-root dir] [--report-dir dir]");
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
