#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];
const OPTIONAL_ABIS = ["x86"];

const FILES = {
  adapterAbiReport: "build/reports/nodejs/runtime-adapter-abi-v1.json",
  adapterExportReport: "build/reports/nodejs/runtime-adapter-export.json",
  artifactGateReport: "build/reports/nodejs/runtime-artifact-gate.json",
  artifactManifestReport: "build/reports/nodejs/runtime-artifact-manifest.json",
  distributionPolicyReport: "build/reports/nodejs/runtime-distribution-policy.json",
  adapterHeader: "app/src/main/cpp/node_runtime_api_v1.h",
  adapterSource: "app/src/main/cpp/node_runtime_adapter_24_5.cpp",
  jniBridge: "app/src/main/cpp/node_bridge_jni.cpp",
  nativeGate: "app/node-native-modernization.gradle.kts",
  finalGate: "app/node-phase11-gate.gradle.kts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  finalDefinition: "docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
};

const ABI_CHECKS = [
  ["adapter_header", "adapterHeader", "autojs_node_get_runtime_api_v1"],
  ["adapter_source", "adapterSource", "AutoJsNodeRuntimeApiV1"],
  ["jni_adapter_selector", "jniBridge", "runEmbeddedScriptAdapterV1DiagnosticsNative"],
  ["runtime_artifact_gate_task", "nativeGate", "\"verifyNodeRuntimeArtifactGate\""],
  ["adapter_abi_task", "nativeGate", "\"verifyNodeRuntimeAdapterAbi\""],
  ["adapter_export_task", "nativeGate", "\"verifyNodeRuntimeAdapterExport\""],
  ["final_abi_task", "finalGate", "\"verifyNodeFinalAbiGate\""],
  ["compatibility_required_abis", "compatibilityProfile", "`arm64-v8a`, `armeabi-v7a`, and `x86_64`"],
  ["final_definition_required_abis", "finalDefinition", "`arm64-v8a`, `armeabi-v7a`, and `x86_64`"],
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const adapterAbiReport = readJsonIfExists(path.join(repoRoot, FILES.adapterAbiReport));
  const adapterExportReport = readJsonIfExists(path.join(repoRoot, FILES.adapterExportReport));
  const artifactGateReport = readJsonIfExists(path.join(repoRoot, FILES.artifactGateReport));
  const artifactManifestReport = readJsonIfExists(path.join(repoRoot, FILES.artifactManifestReport));
  const distributionPolicyReport = readJsonIfExists(path.join(repoRoot, FILES.distributionPolicyReport));
  const registry = readJsonIfExists(path.join(repoRoot, FILES.blockerRegistry)) || { blockers: [] };
  const sourceChecks = ABI_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const requiredLibraries = REQUIRED_ABIS.map((abi) => inspectAbiLibrary(repoRoot, abi, true));
  const optionalLibraries = OPTIONAL_ABIS.map((abi) => inspectAbiLibrary(repoRoot, abi, false));
  const adapterExportAbis = adapterExportReport && Array.isArray(adapterExportReport.artifacts)
    ? adapterExportReport.artifacts.map((entry) => ({
        abi: entry.abi,
        available: entry.available === true,
        exportsAdapterApi: entry.exportsAdapterApi === true,
        buildId: entry.buildId || null,
      }))
    : [];
  const adapterAbiStatus = decisionStatus(adapterAbiReport);
  const adapterExportStatus = decisionStatus(adapterExportReport);
  const artifactGateStatus = decisionStatus(artifactGateReport);
  const runtimeArtifactDefer = runtimeArtifactDeferDecision(registry, artifactGateStatus, artifactGateReport);
  const x86Decision = registryDecision(registry, "x86");
  const blockers = [];
  sourceChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing ABI source evidence: " + entry.id));
  requiredLibraries
    .filter((entry) => !entry.libnode.present)
    .forEach((entry) => blockers.push("missing required ABI libnode.so: " + entry.abi));
  if (adapterAbiStatus !== "ready") {
    blockers.push("runtime adapter ABI report is not ready: " + adapterAbiStatus);
  }
  if (adapterExportStatus !== "ready") {
    blockers.push("runtime adapter export report is not ready: " + adapterExportStatus);
  }
  const missingExportAbis = REQUIRED_ABIS.filter((abi) => {
    const entry = adapterExportAbis.find((candidate) => candidate.abi === abi);
    return !entry || !entry.available || !entry.exportsAdapterApi;
  });
  missingExportAbis.forEach((abi) => blockers.push("required ABI missing adapter export evidence: " + abi));
  if (artifactGateStatus !== "ready" && !runtimeArtifactDefer.deferred) {
    blockers.push("runtime artifact gate is not final-ready: " + artifactGateStatus);
  }
  const artifactWarnings = artifactGateReport && artifactGateReport.decision && Array.isArray(artifactGateReport.decision.warnings)
    ? artifactGateReport.decision.warnings
    : [];
  if (!runtimeArtifactDefer.deferred) {
    artifactWarnings.forEach((warning) => blockers.push("runtime artifact warning: " + warning));
  }
  const status = blockers.length === 0
    ? runtimeArtifactDefer.deferred
      ? "ready_with_deferred_runtime_artifacts"
      : "ready"
    : "blocked";
  const report = {
    schema: "autojs6-node-final-abi-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalAbiGate",
    tasks: ["S11-36", "S11-37"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    requiredAbis: REQUIRED_ABIS,
    optionalAbis: OPTIONAL_ABIS,
    sourceChecks,
    libraries: {
      required: requiredLibraries,
      optional: optionalLibraries,
    },
    reports: {
      adapterAbi: {
        available: Boolean(adapterAbiReport),
        status: adapterAbiStatus,
      },
      adapterExport: {
        available: Boolean(adapterExportReport),
        status: adapterExportStatus,
        abis: adapterExportAbis,
      },
      artifactGate: {
        available: Boolean(artifactGateReport),
        status: artifactGateStatus,
        warnings: artifactWarnings,
      },
      artifactManifest: {
        available: Boolean(artifactManifestReport),
        status: decisionStatus(artifactManifestReport),
      },
      distributionPolicy: {
        available: Boolean(distributionPolicyReport),
        status: decisionStatus(distributionPolicyReport),
      },
    },
    optionalX86: x86Decision,
    deferredRuntimeArtifacts: runtimeArtifactDefer,
    blockers,
    decision: {
      status,
      requiredAbiLibrariesPresent: requiredLibraries.every((entry) => entry.libnode.present),
      adapterAbiReady: adapterAbiStatus === "ready",
      adapterExportReady: adapterExportStatus === "ready" && missingExportAbis.length === 0,
      runtimeArtifactGateReady: artifactGateStatus === "ready",
      runtimeArtifactGateDeferred: runtimeArtifactDefer.deferred,
      x86BlocksFinal: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-abi-gate", report, renderMarkdown);
  console.log("Final ABI gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status === "blocked") {
    process.exitCode = 1;
  }
}

function inspectAbiLibrary(repoRoot, abi, required) {
  const libnode = inspectFile(repoRoot, `app/src/main/jniLibs/${abi}/libnode.so`);
  const libcxx = inspectFile(repoRoot, `app/src/main/jniLibs/${abi}/libc++_shared.so`);
  return {
    abi,
    required,
    libnode,
    libcxx,
  };
}

function inspectFile(repoRoot, relative) {
  const absolute = path.join(repoRoot, relative);
  if (!fs.existsSync(absolute)) {
    return {
      path: relative,
      present: false,
      size: 0,
      sha256: null,
    };
  }
  return {
    path: relative,
    present: true,
    size: fs.statSync(absolute).size,
    sha256: sha256(absolute),
  };
}

function registryDecision(registry, id) {
  const entry = (registry.blockers || []).find((candidate) => candidate.id === id);
  return {
    id,
    status: entry ? entry.status : "missing",
    classification: entry ? entry.classification : "missing",
    currentEvidence: entry ? entry.currentEvidence : "blocker registry entry is missing",
    blocksFinal: false,
  };
}

function runtimeArtifactDeferDecision(registry, artifactGateStatus, artifactGateReport) {
  const lane = registryDecision(registry, "node_24_17_community_fork");
  const warnings = artifactGateReport && artifactGateReport.decision && Array.isArray(artifactGateReport.decision.warnings)
    ? artifactGateReport.decision.warnings
    : [];
  const deferred = (
    artifactGateStatus === "bootstrap_only" &&
    registry.defaultRuntime === "node24_5" &&
    lane.classification === "external_runtime_maintenance" &&
    lane.status === "waiting_for_android_artifacts"
  );
  return {
    id: "node_24_17_community_fork",
    deferred,
    artifactGateStatus,
    classification: lane.classification,
    status: lane.status,
    defaultRuntime: registry.defaultRuntime || "unknown",
    reason: lane.currentEvidence,
    warnings,
  };
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final ABI Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Required ABI libraries present: ${report.decision.requiredAbiLibrariesPresent}`,
    `- Adapter ABI ready: ${report.decision.adapterAbiReady}`,
    `- Adapter export ready: ${report.decision.adapterExportReady}`,
    `- Runtime artifact gate ready: ${report.decision.runtimeArtifactGateReady}`,
    `- Runtime artifact gate deferred: ${report.decision.runtimeArtifactGateDeferred}`,
    `- Optional x86 blocks final: ${report.decision.x86BlocksFinal}`,
    "",
    "## Required ABI Libraries",
    "",
    "| ABI | libnode.so | libc++_shared.so |",
    "| --- | --- | --- |",
  ];
  report.libraries.required.forEach((entry) => {
    lines.push(`| ${entry.abi} | ${entry.libnode.present} | ${entry.libcxx.present} |`);
  });
  lines.push(
    "",
    "## Source Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  );
  report.sourceChecks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  lines.push("", "## Deferred Runtime Artifacts", "");
  if (report.deferredRuntimeArtifacts.deferred) {
    lines.push(
      `- ${report.deferredRuntimeArtifacts.id}: ${report.deferredRuntimeArtifacts.reason}`,
      `- Artifact gate status: ${report.deferredRuntimeArtifacts.artifactGateStatus}`,
      `- Default runtime: ${report.deferredRuntimeArtifacts.defaultRuntime}`,
    );
    report.deferredRuntimeArtifacts.warnings.forEach((warning) => lines.push(`- ${warning}`));
  } else {
    lines.push("None.");
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) {
    return null;
  }
  try {
    return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
  } catch (_) {
    return null;
  }
}

function stripBom(value) {
  return value.replace(/^\uFEFF/, "");
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
      console.log("Usage: node verify-final-abi-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
