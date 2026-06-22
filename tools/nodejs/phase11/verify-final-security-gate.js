#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const FILES = {
  phase10SecurityCorpus: "build/reports/nodejs/phase10-security-corpus.json",
  phase10SecurityScript: "tools/nodejs/phase10/verify-security-corpus.js",
  finalGate: "app/node-phase11-gate.gradle.kts",
  phase10Gate: "app/node-phase10-gate.gradle.kts",
  finalDefinition: "docs/nodejs/final/FINAL_INTEGRATION_DEFINITION.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
};

const SOURCE_CHECKS = [
  ["phase10_security_corpus_gate", "phase10Gate", "verifyNodePhase10SecurityCorpus"],
  ["final_security_status_task", "finalGate", "generateNodeFinalSecurityStatus"],
  ["final_security_gate_task", "finalGate", "verifyNodeFinalSecurityGate"],
  ["final_security_definition", "finalDefinition", "Security/fuzz v2 covers"],
  ["security_model_release_gate", "securityModel", "Release Security Gate"],
  ["testing_phase10_security", "testing", "verifyNodePhase10SecurityCorpus"],
  ["ci_phase10_security", "ciGates", "verifyNodePhase10SecurityCorpus"],
];

const REQUIRED_ATTACK_SURFACES = 11;
const PERMANENT_DENIALS = [
  "native_addon",
  "unrestricted_child_process",
  "raw_network",
  "private_node_bindings",
  "production_inspector",
];

const ATTACK_BLOCKER_HINTS = {
  multi_instance_cross_slot_access: ["second_execution_slot"],
  worker_bridge_access: ["worker_threads"],
  wasi_preopen_escape: ["controlled_wasi"],
  adapter_malformed_abi_input: ["adapter_v1_real_execution_path"],
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([id, relative]) => [id, read(path.join(repoRoot, relative))]),
  );
  const corpus = readJsonIfExists(path.join(repoRoot, FILES.phase10SecurityCorpus));
  const registry = readJsonIfExists(path.join(repoRoot, FILES.blockerRegistry)) || { blockers: [] };
  const sourceChecks = SOURCE_CHECKS.map(([id, fileId, token]) => ({
    id,
    file: FILES[fileId],
    token,
    present: files[fileId].includes(token),
  }));
  const attacks = corpus && Array.isArray(corpus.attacks)
    ? corpus.attacks.map((attack) => summarizeAttack(attack, registry))
    : [];
  const permanentDenials = PERMANENT_DENIALS.map((id) => {
    const entry = registryEntry(registry, id);
    return {
      id,
      present: Boolean(entry),
      status: entry ? entry.status : "missing",
      classification: entry ? entry.classification : "missing",
      stable: Boolean(entry)
        && entry.classification === "intentional_non_goal"
        && entry.status === "permanently_unsupported",
      currentEvidence: entry ? entry.currentEvidence : "blocker registry entry is missing",
    };
  });
  const blockers = [];
  sourceChecks
    .filter((entry) => !entry.present)
    .forEach((entry) => blockers.push("missing final security source evidence: " + entry.id));
  if (!corpus) {
    blockers.push("Phase 10 security corpus report is missing");
  } else {
    const status = decisionStatus(corpus);
    if (status !== "ready") {
      blockers.push("Phase 10 security corpus is not ready: " + status);
    }
    if ((corpus.attackCount || 0) < REQUIRED_ATTACK_SURFACES) {
      blockers.push(`Phase 10 security corpus has fewer than ${REQUIRED_ATTACK_SURFACES} attack surfaces`);
    }
    if (Array.isArray(corpus.policyViolations) && corpus.policyViolations.length > 0) {
      corpus.policyViolations.forEach((violation) => blockers.push("Phase 10 security policy violation: " + violation));
    }
    if (corpus.decision && corpus.decision.blockedCapabilitiesPromoted !== false) {
      blockers.push("Phase 10 security corpus promoted a blocked capability");
    }
  }
  attacks
    .filter((attack) => !attack.finalCovered)
    .forEach((attack) => blockers.push(`final security attack surface is not covered: ${attack.id} (${attack.status})`));
  permanentDenials
    .filter((entry) => !entry.stable)
    .forEach((entry) => blockers.push("safe profile permanent denial is not stable: " + entry.id));
  const report = {
    schema: "autojs6-node-final-security-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalSecurityGate",
    tasks: ["S11-38", "S11-39"],
    sources: Object.fromEntries(
      Object.entries(FILES).map(([id, relative]) => [id, relativePath(repoRoot, path.join(repoRoot, relative))]),
    ),
    sourceChecks,
    phase10SecurityCorpus: {
      available: Boolean(corpus),
      status: decisionStatus(corpus),
      attackCount: corpus ? corpus.attackCount || 0 : 0,
      statusCounts: corpus ? corpus.statusCounts || {} : {},
      policyViolations: corpus && Array.isArray(corpus.policyViolations) ? corpus.policyViolations : [],
      blockedCapabilitiesPromoted: corpus && corpus.decision ? corpus.decision.blockedCapabilitiesPromoted : null,
    },
    attacks,
    permanentDenials,
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      phase10CorpusReady: decisionStatus(corpus) === "ready",
      requiredAttackSurfacesPresent: attacks.length >= REQUIRED_ATTACK_SURFACES,
      finalAttackSurfacesCovered: attacks.length >= REQUIRED_ATTACK_SURFACES && attacks.every((attack) => attack.finalCovered),
      permanentDenialsStable: permanentDenials.every((entry) => entry.stable),
      blockedCapabilitiesPromoted: corpus && corpus.decision ? corpus.decision.blockedCapabilitiesPromoted === true : null,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-security-gate", report, renderMarkdown);
  console.log("Final security gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function summarizeAttack(attack, registry) {
  const missingEvidence = Array.isArray(attack.missingEvidence) ? attack.missingEvidence : [];
  const blockerHints = (ATTACK_BLOCKER_HINTS[attack.id] || []).map((id) => {
    const entry = registryEntry(registry, id);
    return {
      id,
      status: entry ? entry.status : "missing",
      classification: entry ? entry.classification : "missing",
      currentEvidence: entry ? entry.currentEvidence : "blocker registry entry is missing",
    };
  });
  return {
    id: attack.id,
    title: attack.title,
    status: attack.status,
    finalCovered: attack.status === "covered" && missingEvidence.length === 0,
    evidenceCount: Array.isArray(attack.evidenceMatches) ? attack.evidenceMatches.length : 0,
    missingEvidence,
    blockerHints,
  };
}

function registryEntry(registry, id) {
  return (registry.blockers || []).find((entry) => entry.id === id) || null;
}

function decisionStatus(json) {
  if (!json) return "missing";
  if (json.decision && json.decision.status) return json.decision.status;
  return json.status || "unknown";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Security Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Phase 10 corpus ready: ${report.decision.phase10CorpusReady}`,
    `- Required attack surfaces present: ${report.decision.requiredAttackSurfacesPresent}`,
    `- Final attack surfaces covered: ${report.decision.finalAttackSurfacesCovered}`,
    `- Permanent denials stable: ${report.decision.permanentDenialsStable}`,
    "",
    "## Attack Surfaces",
    "",
    "| Attack | Status | Final covered | Evidence |",
    "| --- | --- | --- | --- |",
  ];
  report.attacks.forEach((attack) => {
    lines.push(`| ${attack.id} | ${attack.status} | ${attack.finalCovered} | ${attack.evidenceCount} |`);
  });
  lines.push(
    "",
    "## Permanent Denials",
    "",
    "| Boundary | Status | Stable |",
    "| --- | --- | --- |",
  );
  report.permanentDenials.forEach((entry) => {
    lines.push(`| ${entry.id} | ${entry.status} | ${entry.stable} |`);
  });
  lines.push("", "## Blockers", "");
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
      console.log("Usage: node verify-final-security-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
