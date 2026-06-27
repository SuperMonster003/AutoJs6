#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REQUIRED_TOKENS = [
  {
    file: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
    tokens: [
      "autojs6-bridge-provider-registry-v2",
      "autojs6-node-bridge-provider-v2",
      "autojs6-bridge-provider-v2",
      "statusCode",
      "phase10WireName",
      "permission_required",
      "healthCheck",
      "activeResources",
      "lastError",
      "packagedSupport",
      "NodeBridgePackagedSupportStatus",
    ],
  },
  {
    file: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
    tokens: [
      "statusCode=",
      "packagedSupport",
      "packaged:",
    ],
  },
  {
    file: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeProviderRegistryInstrumentationTest.kt",
    tokens: [
      "PROVIDER_SCHEMA",
      "REGISTRY_VERSION",
      "statusVocabulary",
      "activeResources",
      "packagedSupport",
      "lastError",
    ],
  },
  {
    file: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
    tokens: [
      "PROVIDER_SCHEMA",
      "REGISTRY_VERSION",
      "statusCode",
      "packagedSupport",
      "activeResources",
      "lastError",
    ],
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const checks = REQUIRED_TOKENS.map((entry) => verifyTokens(repoRoot, entry));
  const policyViolations = checks.flatMap((check) =>
    check.missingTokens.map((token) => `${check.file} missing token: ${token}`),
  );
  const report = {
    schema: "autojs6-node-phase10-provider-registry-v2-gate",
    generatedAt: new Date().toISOString(),
    task: "S10-11 Provider Registry v2",
    gate: "verifyNodePhase10ProviderRegistryV2",
    statusVocabulary: [
      "unavailable",
      "fake",
      "available",
      "permission_required",
      "degraded",
      "failed",
    ],
    requiredFields: [
      "version",
      "healthCheck",
      "requiredCapabilities",
      "requiredAndroidPermissions",
      "activeResources",
      "lastError",
      "packagedSupport",
    ],
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      liveProviderResultsRequiredForPromotion: true,
      fakeProviderResultsPromoted: false,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-provider-registry-v2", report, renderMarkdown);
  console.log("Phase 10 provider registry v2 decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function verifyTokens(repoRoot, entry) {
  const fullPath = path.join(repoRoot, entry.file);
  const text = fs.existsSync(fullPath) ? fs.readFileSync(fullPath, "utf8") : "";
  const missingTokens = entry.tokens.filter((token) => !text.includes(token));
  return {
    file: entry.file,
    exists: fs.existsSync(fullPath),
    requiredTokens: entry.tokens,
    missingTokens,
    status: missingTokens.length === 0 ? "ready" : "failed",
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Provider Registry v2",
    "",
    `- Decision: ${report.decision.status}`,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Fake provider promotion: ${report.decision.fakeProviderResultsPromoted}`,
    "",
    "| File | Status | Missing Tokens |",
    "| --- | --- | --- |",
  ];
  for (const check of report.checks) {
    lines.push(`| ${check.file} | ${check.status} | ${check.missingTokens.join(", ") || "None"} |`);
  }
  lines.push("", "## Required Fields", "");
  report.requiredFields.forEach((field) => lines.push(`- ${field}`));
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
      console.log("Usage: node verify-provider-registry-v2.js [--repo-root dir] [--report-dir dir]");
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
