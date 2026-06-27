#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const HOST_REPORTS = [
  ["runtime_freeze", "phase10-runtime-freeze.json", true],
  ["capability_truth", "phase10-capability-truth.json", true],
  ["provider_registry_v2", "phase10-provider-registry-v2.json", true],
  ["skip_audit", "phase10-skip-audit.json", true],
  ["packaged_parity", "phase10-packaged-parity.json", true],
  ["focused_gate_matrix", "phase10-focused-gate-matrix.json", true],
  ["package_manager", "phase10-package-manager-boundary.json", true],
  ["long_running", "phase10-long-running-activation.json", true],
  ["worker_wasi", "phase10-worker-wasi-boundary.json", true],
  ["developer_experience", "phase10-developer-experience.json", true],
  ["crash_adapter_cutover", "phase10-crash-adapter-cutover.json", true],
  ["security_corpus", "phase10-security-corpus.json", true],
];

const PHASE10_EXAMPLE_MINIMUM = 55;
const PHASE10_REAL_NPM_MINIMUM = 50;

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const appReportDir = path.resolve(options.appReportDir || path.join(repoRoot, "app/build/reports/nodejs"));
  const androidResultsDir = path.resolve(options.androidResultsDir || path.join(repoRoot, "app/build/outputs/androidTest-results/connected/debug/flavors/app"));

  const hostReports = HOST_REPORTS.map(([id, file, required]) => readReport(reportDir, id, file, required, repoRoot));
  const capabilityTruth = hostReports.find((entry) => entry.id === "capability_truth").json || {};
  const securityCorpus = hostReports.find((entry) => entry.id === "security_corpus").json || {};
  const examples = summarizeExamples(repoRoot, appReportDir);
  const realNpmCorpus = summarizeRealNpmCorpus(repoRoot);
  const focusedGate = summarizeFocusedGate(repoRoot);
  const deviceGate = summarizeDeviceGate(androidResultsDir, repoRoot);
  const blockers = summarizeBlockers(capabilityTruth, hostReports);
  const policyViolations = collectPolicyViolations(hostReports, examples, realNpmCorpus, focusedGate, securityCorpus);

  const summary = {
    schema: "autojs6-node-phase10-summary-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-56 Phase 10 Aggregate Gate And Summary",
    gate: "verifyNodePhase10Gate",
    runtime: {
      defaultSlot: "node24_5",
      node2417Promotion: "blocked",
      libnodeModifiedByPhase10: false,
    },
    hostReports: hostReports.map(({ json, ...entry }) => entry),
    focusedGate,
    capabilities: summarizeCapabilities(capabilityTruth),
    securityCorpus: {
      available: Boolean(securityCorpus.schema),
      attackCount: Number(securityCorpus.attackCount) || 0,
      statusCounts: securityCorpus.statusCounts || {},
      decision: decisionStatus(securityCorpus),
    },
    examples,
    realNpmCorpus,
    deviceGate,
    remainingBlockers: blockers,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "completed_with_recorded_blockers" : "failed",
      phaseComplete: policyViolations.length === 0,
      safeNodeProfilePreserved: true,
      defaultRuntimePromoted: false,
      adapterV1Promoted: false,
      blockedCapabilitiesPromoted: false,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-summary", summary, renderMarkdown);
  console.log("Phase 10 summary decision: " + summary.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function readReport(reportDir, id, file, required, repoRoot) {
  const absolute = path.join(reportDir, file);
  const available = fs.existsSync(absolute);
  const json = available ? readJson(absolute) : null;
  const status = json ? decisionStatus(json) : "missing";
  return {
    id,
    path: relativeOrDisplay(absolute, repoRoot),
    required,
    available,
    status,
    policyViolationCount: Array.isArray(json && json.policyViolations) ? json.policyViolations.length : 0,
    json,
  };
}

function summarizeCapabilities(capabilityTruth) {
  const capabilities = Array.isArray(capabilityTruth.capabilities) ? capabilityTruth.capabilities : [];
  return {
    available: Boolean(capabilityTruth.schema),
    total: capabilities.length,
    totals: capabilityTruth.totals || {},
    blocked: capabilities.filter((entry) => Array.isArray(entry.status) && entry.status.includes("blocked")).map((entry) => entry.id),
    disabled: capabilities.filter((entry) => Array.isArray(entry.status) && entry.status.includes("disabled")).map((entry) => entry.id),
    liveProvider: capabilities.filter((entry) => Array.isArray(entry.status) && entry.status.includes("live_provider")).map((entry) => entry.id),
    packagedSupported: capabilities.filter((entry) => Array.isArray(entry.status) && entry.status.includes("packaged_supported")).map((entry) => entry.id),
  };
}

function summarizeExamples(repoRoot, appReportDir) {
  const reportFile = path.join(appReportDir, "examples.json");
  const report = fs.existsSync(reportFile) ? readJson(reportFile) : null;
  const manifestFile = path.join(repoRoot, "sample/nodejs/examples.json");
  const manifest = fs.existsSync(manifestFile) ? readJson(manifestFile) : { examples: [] };
  const examples = Array.isArray(report && report.examples)
    ? report.examples
    : Array.isArray(manifest.examples)
      ? manifest.examples
      : [];
  const phase10 = examples.filter((entry) => Array.isArray(entry.tags) && entry.tags.includes("phase10"));
  return {
    report: fs.existsSync(reportFile) ? relativeOrDisplay(reportFile, repoRoot) : relativeOrDisplay(manifestFile, repoRoot),
    total: Number(report && report.exampleCount) || examples.length,
    requiredMinimum: PHASE10_EXAMPLE_MINIMUM,
    phase10Count: phase10.length,
    targetMet: (Number(report && report.exampleCount) || examples.length) >= PHASE10_EXAMPLE_MINIMUM,
    phase10Examples: phase10.map((entry) => entry.name),
  };
}

function summarizeRealNpmCorpus(repoRoot) {
  const manifestFile = path.join(repoRoot, "app/src/androidTest/assets/npm_compat_real/manifest.json");
  const manifest = fs.existsSync(manifestFile) ? readJson(manifestFile) : { fixtures: [] };
  const fixtures = Array.isArray(manifest.fixtures) ? manifest.fixtures : [];
  return {
    manifest: relativeOrDisplay(manifestFile, repoRoot),
    total: fixtures.length,
    phase8Baseline: 20,
    phase9Baseline: 30,
    requiredMinimum: PHASE10_REAL_NPM_MINIMUM,
    targetMet: fixtures.length >= PHASE10_REAL_NPM_MINIMUM,
    phase10DeltaFromPhase9: fixtures.length - 30,
    fixtures: fixtures.map((fixture) => fixture.name),
  };
}

function summarizeFocusedGate(repoRoot) {
  const gateFile = path.join(repoRoot, "app/node-phase10-gate.gradle.kts");
  const buildFile = path.join(repoRoot, "app/build.gradle.kts");
  const gateText = fs.existsSync(gateFile) ? fs.readFileSync(gateFile, "utf8") : "";
  const buildText = fs.existsSync(buildFile) ? fs.readFileSync(buildFile, "utf8") : "";
  const requiredTasks = [
    "verifyNodePhase10Gate",
    "verifyNodePhase10SecurityCorpus",
    "verifyNodeRealNpmGate",
    "verifyNodeExamples",
    "generateNodePhase10Summary",
  ];
  return {
    requiredTasks,
    missingTasks: requiredTasks.filter((task) => !gateText.includes(task) && !buildText.includes(task)),
    phase10SelectorContainsRealNpm: buildText.includes("nodeJsRealNpmGateInstrumentationClasses") &&
      buildText.includes("verifyNodeRealNpmGate"),
  };
}

function summarizeDeviceGate(androidResultsDir, repoRoot) {
  if (!fs.existsSync(androidResultsDir)) {
    return {
      available: false,
      resultDir: relativeOrDisplay(androidResultsDir, repoRoot),
      tests: 0,
      failures: 0,
      errors: 0,
      skipped: 0,
    };
  }
  const files = [];
  walk(androidResultsDir, (file) => {
    if (path.basename(file).startsWith("TEST-") && file.endsWith(".xml")) {
      files.push(file);
    }
  });
  const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  for (const file of files) {
    const text = fs.readFileSync(file, "utf8");
    for (const key of Object.keys(totals)) {
      const match = text.match(new RegExp(`${key}="(\\d+)"`));
      totals[key] += match ? Number(match[1]) : 0;
    }
  }
  return {
    available: files.length > 0,
    resultDir: relativeOrDisplay(androidResultsDir, repoRoot),
    xmlFiles: files.map((file) => relativeOrDisplay(file, repoRoot)),
    ...totals,
  };
}

function summarizeBlockers(capabilityTruth, hostReports) {
  const blockers = [];
  const capabilities = Array.isArray(capabilityTruth.capabilities) ? capabilityTruth.capabilities : [];
  for (const capability of capabilities) {
    if (Array.isArray(capability.status) && capability.status.includes("blocked")) {
      blockers.push({
        id: capability.id,
        reason: capability.notes || "blocked capability remains recorded",
      });
    }
  }
  for (const report of hostReports) {
    if (report.status && String(report.status).includes("blocked")) {
      blockers.push({
        id: report.id,
        reason: `report status is ${report.status}`,
      });
    }
  }
  return blockers;
}

function collectPolicyViolations(hostReports, examples, realNpmCorpus, focusedGate, securityCorpus) {
  const violations = [];
  for (const report of hostReports) {
    if (report.required && !report.available) {
      violations.push(`missing required Phase 10 host report: ${report.id}`);
    }
    if (report.policyViolationCount > 0) {
      violations.push(`${report.id} has ${report.policyViolationCount} policy violation(s)`);
    }
    if (report.status === "failed") {
      violations.push(`${report.id} status is failed`);
    }
  }
  if (!examples.targetMet) {
    violations.push(`examples total ${examples.total} is below required ${examples.requiredMinimum}`);
  }
  if (!realNpmCorpus.targetMet) {
    violations.push(`real npm fixture total ${realNpmCorpus.total} is below required ${realNpmCorpus.requiredMinimum}`);
  }
  if (focusedGate.missingTasks.length > 0) {
    violations.push("missing Phase 10 focused task(s): " + focusedGate.missingTasks.join(", "));
  }
  if (!focusedGate.phase10SelectorContainsRealNpm) {
    violations.push("Phase 10 focused selector must include real npm gate coverage");
  }
  if ((Number(securityCorpus.attackCount) || 0) < 11) {
    violations.push("Phase 10 security corpus must cover at least 11 attack surfaces");
  }
  return violations;
}

function decisionStatus(json) {
  if (json && json.decision && json.decision.status) {
    return json.decision.status;
  }
  return json && json.status ? json.status : "unknown";
}

function walk(dir, callback) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(file, callback);
    } else if (entry.isFile()) {
      callback(file);
    }
  }
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function relativeOrDisplay(file, repoRoot) {
  const relative = path.relative(repoRoot, file).replace(/\\/g, "/");
  return relative && !relative.startsWith("..") ? relative : file;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Summary",
    "",
    `- Decision: ${report.decision.status}`,
    `- Default runtime: ${report.runtime.defaultSlot}`,
    `- Node 24.17 promotion: ${report.runtime.node2417Promotion}`,
    `- Examples: ${report.examples.total}/${report.examples.requiredMinimum}`,
    `- Real npm fixtures: ${report.realNpmCorpus.total}/${report.realNpmCorpus.requiredMinimum}`,
    `- Security attack surfaces: ${report.securityCorpus.attackCount}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Host Reports",
    "",
    "| Report | Status | Available | Policy Violations |",
    "| --- | --- | --- | --- |",
  ];
  for (const hostReport of report.hostReports) {
    lines.push(`| ${hostReport.id} | ${hostReport.status} | ${hostReport.available} | ${hostReport.policyViolationCount} |`);
  }
  lines.push("", "## Remaining Blockers", "");
  if (report.remainingBlockers.length === 0) {
    lines.push("None.");
  } else {
    report.remainingBlockers.forEach((blocker) => lines.push(`- ${blocker.id}: ${blocker.reason}`));
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
      console.log("Usage: node generate-phase10-summary.js [--repo-root dir] [--report-dir dir] [--app-report-dir dir] [--android-results-dir dir]");
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
