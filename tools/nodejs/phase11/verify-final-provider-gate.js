#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  phase10RegistryReport: "build/reports/nodejs/phase10-provider-registry-v2.json",
  liveProviderSummary: "build/reports/nodejs/live-provider-summary.json",
  phase10Tool: "tools/nodejs/phase10/verify-provider-registry-v2.js",
  buildGradle: "app/build.gradle.kts",
  packagingPreflight: "app/embedded-node-packaging-preflight.gradle.kts",
  phase10Gate: "app/node-phase10-gate.gradle.kts",
  finalGate: "app/node-phase11-gate.gradle.kts",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  doctor: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
  registryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
  doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  finalDefinition: "docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md",
};

const REGISTRY_CHECKS = [
  ["phase10_tool", "phase10Tool", "autojs6-node-phase10-provider-registry-v2-gate"],
  ["phase10_task", "phase10Gate", "\"verifyNodePhase10ProviderRegistryV2\""],
  ["live_provider_gate", "packagingPreflight", "verifyNodeLiveProviderGate"],
  ["live_provider_summary_task", "buildGradle", "generateNodeLiveProviderSummary"],
  ["final_provider_task", "finalGate", "\"verifyNodeFinalProviderGate\""],
  ["provider_schema", "providerRegistry", "autojs6-node-bridge-provider-v2"],
  ["provider_health", "providerRegistry", "healthCheck"],
  ["provider_packaged_support", "providerRegistry", "packagedSupport"],
  ["provider_active_resources", "providerRegistry", "activeResources"],
  ["provider_last_error", "providerRegistry", "lastError"],
  ["doctor_packaged_support", "doctor", "packagedSupport"],
  ["registry_test", "registryTest", "providerRegistryDefinesPhase7LiveProviderSurface"],
  ["doctor_test", "doctorTest", "generatesPrivacyRedactedTextAndJsonReports"],
  ["testing_doc", "testingDoc", "verifyNodeLiveProviderGate"],
  ["ci_doc", "ciGatesDoc", "verifyNodeLiveProviderGate"],
  ["final_definition", "finalDefinition", "Stable providers are not fake-provider-only"],
];

const EXPECTED_LIVE_PROVIDERS = [
  "registry",
  "doctor",
  "fetch",
  "websocket",
  "ocr",
  "storage",
  "notifications",
  "sensors",
  "accessibility",
  "image",
  "ui",
  "work_manager",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const registryReport = readJsonIfExists(path.join(repoRoot, FILES.phase10RegistryReport));
  const liveSummary = readJsonIfExists(path.join(repoRoot, FILES.liveProviderSummary));
  const registryChecks = REGISTRY_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const registryStatus = decisionStatus(registryReport);
  const liveStatus = evaluateLiveSummary(liveSummary);
  const blockers = [];
  registryChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing provider registry evidence: " + entry.id));
  if (!registryReport) {
    blockers.push("Phase 10 provider registry v2 report is missing");
  } else if (registryStatus !== "ready") {
    blockers.push("Phase 10 provider registry v2 report is not ready: " + registryStatus);
  }
  if (!liveSummary) {
    blockers.push("live-provider summary is missing");
  }
  liveStatus.policyViolations.forEach((violation) => blockers.push("live-provider policy violation: " + violation));
  liveStatus.missingProviders.forEach((provider) => blockers.push("live-provider summary missing provider: " + provider));
  if (liveSummary && liveSummary.fakeProvidersAllowed !== false) {
    blockers.push("live-provider gate must not allow fake providers");
  }
  if (liveSummary && liveSummary.networkExperimentalEnabled !== true) {
    blockers.push("live-provider gate must run with network experimental provider coverage enabled");
  }
  const report = {
    schema: "autojs6-node-final-provider-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalProviderGate",
    tasks: ["S11-34", "S11-35"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    registry: {
      reportAvailable: Boolean(registryReport),
      reportStatus: registryStatus,
      status: registryChecks.every((entry) => entry.present) && registryStatus === "ready" ? "ready" : "missing_evidence",
      checks: registryChecks,
    },
    liveProvider: liveStatus,
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      registryReady: registryChecks.every((entry) => entry.present) && registryStatus === "ready",
      liveProviderEvidenceReady: liveStatus.ready,
      fakeProviderOnlyPromoted: false,
      hostOnlyPromoted: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-provider-gate", report, renderMarkdown);
  console.log("Final provider gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function evaluateLiveSummary(summary) {
  if (!summary) {
    return {
      reportAvailable: false,
      ready: false,
      deviceProfile: "missing",
      networkExperimentalEnabled: false,
      fakeProvidersAllowed: null,
      expectedCases: 0,
      coveredProviders: [],
      missingProviders: EXPECTED_LIVE_PROVIDERS,
      policyViolations: [],
    };
  }
  const policyViolations = Array.isArray(summary.policyViolations) ? summary.policyViolations : [];
  const totals = summary.totals || {};
  const coveredProviders = Array.isArray(totals.coveredProviders) ? totals.coveredProviders : [];
  const covered = new Set(coveredProviders);
  const missingProviders = EXPECTED_LIVE_PROVIDERS.filter((provider) => !covered.has(provider));
  const expectedCases = Number(totals.expectedCases || 0);
  return {
    reportAvailable: true,
    ready: policyViolations.length === 0 &&
      missingProviders.length === 0 &&
      summary.fakeProvidersAllowed === false &&
      summary.networkExperimentalEnabled === true,
    deviceProfile: summary.deviceProfile || "unknown",
    devices: Array.isArray(summary.devices) ? summary.devices : [],
    networkExperimentalEnabled: summary.networkExperimentalEnabled === true,
    fakeProvidersAllowed: summary.fakeProvidersAllowed,
    expectedCases,
    coveredProviders,
    missingProviders,
    policyViolations,
  };
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Provider Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Registry ready: ${report.decision.registryReady}`,
    `- Live provider evidence ready: ${report.decision.liveProviderEvidenceReady}`,
    `- Live provider summary: ${report.liveProvider.reportAvailable}`,
    `- Fake-provider-only promoted: ${report.decision.fakeProviderOnlyPromoted}`,
    "",
    "## Registry Checks",
    "",
    "| Check | Present | File |",
    "| --- | --- | --- |",
  ];
  report.registry.checks.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.present} | ${entry.file} |`);
  });
  lines.push(
    "",
    "## Live Provider Summary",
    "",
    `- Device profile: ${report.liveProvider.deviceProfile}`,
    `- Network experimental enabled: ${report.liveProvider.networkExperimentalEnabled}`,
    `- Fake providers allowed: ${report.liveProvider.fakeProvidersAllowed}`,
    `- Expected cases: ${report.liveProvider.expectedCases}`,
    `- Covered providers: ${report.liveProvider.coveredProviders.join(", ") || "None"}`,
    `- Missing providers: ${report.liveProvider.missingProviders.join(", ") || "None"}`,
    "",
    "## Blockers",
    "",
  );
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
      console.log("Usage: node verify-final-provider-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
