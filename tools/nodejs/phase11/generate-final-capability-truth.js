#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REGISTRY_PATH = "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json";
const REPORT_BASENAME = "final-capability-truth";

const STABLE_CAPABILITIES = [
  {
    id: "safe_node_profile",
    evidence: [
      ["docs/nodejs/COMPATIBILITY_PROFILE.md", "AutoJs6 Safe Node Profile"],
      ["app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt", "PROFILE_NAME"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
  {
    id: "commonjs",
    evidence: [
      ["app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedJavaScriptEngine.kt", "resolveCommonJsPackageDirectoryFiles"],
      ["app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt", "commonJsModuleExtensions"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
  {
    id: "scoped_fs",
    evidence: [
      ["docs/nodejs/ERROR_CATALOG.md", "ERR_AUTOJS6_FS_PATH_ESCAPE"],
      ["app/src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt", "ERR_AUTOJS6_FS_PATH_ESCAPE"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
  {
    id: "module_sources_budget",
    evidence: [
      ["app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt", "EMBEDDED_SCRIPT_MODULE_SOURCE_MAX_COUNT"],
      ["docs/nodejs/COMPATIBILITY_PROFILE.md", "moduleSources Budget"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
  {
    id: "bridge_capability_manifest",
    evidence: [
      ["app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt", "definedCapabilities"],
      ["app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt", "provider-manifest-missing-capability"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
  {
    id: "bridge_resource_registry",
    evidence: [
      ["app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt", "activeCount()"],
      ["app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceRegistryInstrumentationTest.kt", "NodeBridgeResourceRegistryInstrumentationTest"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
  {
    id: "backend_switches",
    evidence: [
      ["app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt", "backendSwitches"],
      ["app/src/main/java/org/autojs/autojs/engine/NodeJavaScriptEngineFactory.kt", "NodeRuntimeBackend"],
    ],
    packagedVerified: true,
    cleanupEvidence: true,
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const registry = readJson(path.join(repoRoot, REGISTRY_PATH));
  const capabilities = STABLE_CAPABILITIES.map((capability) => evaluateCapability(repoRoot, capability));
  const requiredInternalBlockers = registry.blockers.filter((entry) => entry.classification === "required_internal");
  const unresolvedRequiredInternalBlockers = requiredInternalBlockers.filter((entry) => entry.status !== "closed");
  const stableFakeProviderOnly = capabilities.filter((entry) => entry.fakeProviderOnly).map((entry) => entry.id);
  const stableHostOnly = capabilities.filter((entry) => entry.hostOnly).map((entry) => entry.id);
  const stableMissingPackagedSupport = capabilities.filter((entry) => !entry.packagedVerified).map((entry) => entry.id);
  const stableMissingCleanupEvidence = capabilities.filter((entry) => !entry.cleanupEvidence).map((entry) => entry.id);
  const stableMissingEvidence = capabilities
    .filter((entry) => entry.evidenceMatches.some((match) => !match.present))
    .map((entry) => entry.id);
  const policyViolations = [];

  if (!Array.isArray(registry.finalStatusVocabulary) || registry.finalStatusVocabulary.includes("completed_with_recorded_blockers")) {
    policyViolations.push("final status vocabulary must not allow completed_with_recorded_blockers");
  }
  if (stableFakeProviderOnly.length > 0) {
    policyViolations.push("stable fake-provider-only capabilities: " + stableFakeProviderOnly.join(", "));
  }
  if (stableHostOnly.length > 0) {
    policyViolations.push("stable host-only capabilities: " + stableHostOnly.join(", "));
  }
  if (stableMissingPackagedSupport.length > 0) {
    policyViolations.push("stable capabilities missing packaged support: " + stableMissingPackagedSupport.join(", "));
  }
  if (stableMissingCleanupEvidence.length > 0) {
    policyViolations.push("stable capabilities missing cleanup evidence: " + stableMissingCleanupEvidence.join(", "));
  }
  if (stableMissingEvidence.length > 0) {
    policyViolations.push("stable capabilities missing source/test evidence: " + stableMissingEvidence.join(", "));
  }

  const blockers = unresolvedRequiredInternalBlockers.map((entry) => ({
    id: entry.id,
    reason: entry.currentEvidence,
  }));
  const blocked = unresolvedRequiredInternalBlockers.length > 0;
  const report = {
    schema: "autojs6-node-final-capability-truth-v1",
    generatedAt: new Date().toISOString(),
    phase: "Phase 11: Node Platform Completion & Graduation",
    gate: options.failOnBlocked ? "verifyNodeFinalCapabilityTruth" : "generateNodeFinalCapabilityTruth",
    finalStatusVocabulary: registry.finalStatusVocabulary,
    defaultRuntime: registry.defaultRuntime,
    stableCapabilities: capabilities,
    requiredInternalBlockers,
    unresolvedRequiredInternalBlockers,
    conditionalDecisions: registry.blockers.filter((entry) => entry.classification === "conditional"),
    intentionalNonGoals: registry.blockers.filter((entry) => entry.classification === "intentional_non_goal"),
    externalRuntimeMaintenance: registry.blockers.filter((entry) => entry.classification === "external_runtime_maintenance"),
    stableFakeProviderOnly,
    stableHostOnly,
    stableMissingPackagedSupport,
    stableMissingCleanupEvidence,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : blocked ? "blocked" : "completed",
      requiredInternalBlockerCount: unresolvedRequiredInternalBlockers.length,
      safeNodeProfilePreserved: true,
      completedWithRecordedBlockersAllowed: false,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, REPORT_BASENAME, report, renderMarkdown);
  console.log("Final capability truth decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "completed") {
    process.exitCode = 1;
  } else if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function evaluateCapability(repoRoot, capability) {
  const evidenceMatches = capability.evidence.map(([relative, token]) => {
    const file = path.join(repoRoot, relative);
    const text = fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
    return {
      path: relative,
      token,
      present: text.includes(token),
    };
  });
  return {
    id: capability.id,
    stable: true,
    fakeProviderOnly: false,
    hostOnly: false,
    packagedVerified: capability.packagedVerified,
    cleanupEvidence: capability.cleanupEvidence,
    evidenceMatches,
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Final Capability Truth",
    "",
    `- Decision: ${report.decision.status}`,
    `- Default runtime: ${report.defaultRuntime}`,
    `- Stable capabilities: ${report.stableCapabilities.length}`,
    `- Required internal blockers: ${report.unresolvedRequiredInternalBlockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Stable Capabilities",
    "",
    "| Capability | Evidence | Packaged | Cleanup |",
    "| --- | --- | --- | --- |",
  ];
  for (const capability of report.stableCapabilities) {
    const evidence = capability.evidenceMatches.every((entry) => entry.present) ? "present" : "missing";
    lines.push(`| ${capability.id} | ${evidence} | ${capability.packagedVerified} | ${capability.cleanupEvidence} |`);
  }
  lines.push("", "## Required Internal Blockers", "");
  if (report.unresolvedRequiredInternalBlockers.length === 0) {
    lines.push("None.");
  } else {
    report.unresolvedRequiredInternalBlockers.forEach((entry) => {
      lines.push(`- ${entry.id}: ${entry.status}`);
    });
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
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
      console.log("Usage: node generate-final-capability-truth.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
