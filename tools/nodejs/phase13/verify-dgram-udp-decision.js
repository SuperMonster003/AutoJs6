#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-dgram-udp-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_31_DGRAM_UDP_DECISION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  p13DgramTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeDgramUdpDecisionInstrumentationTest.kt",
  rawNetworkTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRawNetworkPolicyInstrumentationTest.kt",
  coreBuiltinTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  builtinMatrixGenerator: "tools/nodejs/phase13/generate-node-builtin-parity-matrix.js",
  gradleGate: "app/node-phase13-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 dgram UDP decision: " + report.decision.status);
  console.log("Phase 13 dgram UDP blockers: " + report.blockers.length);
  console.log("Phase 13 dgram UDP policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const desktopSurface = surface(files.surfaceManifest.text, "desktop.network_fs");
  const scenarios = [
    scenario("stable_disabled_runtime_boundary", "dgram stays disabled in runtime capability tables and builtin policy", [
      token(files.runtimeCapabilities, '"dgram"'),
      token(files.runtimeCapabilities, "val disabledBuiltinModuleNames = setOf("),
      token(files.nodeBridgeSources, '"dgram": true'),
      token(files.p13DgramTest, "dgramUdpCapabilityStaysDisabledAndCannotBeShadowed"),
      token(files.p13DgramTest, "p13dgram.codes=require:ERR_AUTOJS6_BUILTIN_DISABLED"),
      token(files.p13DgramTest, "p13dgram.shadow=false"),
      token(files.rawNetworkTest, 'expectSync("dgram", () => require("dgram"), "ERR_AUTOJS6_BUILTIN_DISABLED")'),
      token(files.coreBuiltinTest, 'requireDenied("dgram")'),
      token(files.securityRegressionTest, '"require.dgram"'),
    ], [
      "No shape-only dgram facade is exposed in v1.1 because UDP client/server, bind, connect, recv, and send semantics lack provider evidence.",
    ]),
    scenario("multicast_broadcast_decision", "multicast, broadcast, and UDP server authority remain out of profile", [
      token(files.p13Report, "multicast and broadcast remain unavailable"),
      token(files.p13Report, "No UDP socket is opened"),
      token(files.compatibilityProfile, "P13-31 records dgram UDP capability as stable-denied"),
      token(files.securityModel, "P13-31 records dgram UDP capability as stable-denied"),
      token(files.proComparison, "P13-31 records dgram UDP capability as stable-denied"),
      token(files.proAutoxNotes, "P13-31 records dgram UDP capability as stable-denied"),
    ], [
      "A future UDP promotion must split unicast client, server bind/listen, broadcast, multicast, and source-specific multicast into explicit capability tiers.",
      "A future UDP promotion must prove Android background behavior, INTERNET/multicast permission mapping, packet budgets, and lifecycle cleanup.",
    ]),
    scenario("docs_gate_and_matrix", "docs, desktop matrix, release matrix, and Gradle gate record the P13-31 decision", [
      token(files.phase13Tasks, "P13-31 stable-denied landing"),
      token(files.gradleGate, "verifyNodePhase13DgramUdpDecision"),
      token(files.testingDoc, "verifyNodePhase13DgramUdpDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13DgramUdpDecision"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-31 records UDP/dgram as stable-denied"),
      token(files.builtinMatrixGenerator, "P13-31 records UDP/dgram as stable-denied"),
      token(files.capabilityMatrix, "P13-31 dgram UDP decision"),
    ], [
      "Packaged INTERNET permission proof and fd leak instrumentation remain required before any future UDP promotion.",
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-31"), "P13-31 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-31 report must exist."),
    check("surface-present", Boolean(desktopSurface), "`desktop.network_fs` surface must exist."),
    check(
      "surface-partial",
      desktopSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(desktopSurface.status),
      "`desktop.network_fs` must not be promoted by this decision gate.",
    ),
    check(
      "surface-evidence",
      desktopSurface && (desktopSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.network_fs` surface must reference P13-31 evidence.",
    ),
    check(
      "dgram-disabled-matrix",
      files.desktopMatrix.text.includes("| `dgram` | `disabled` |"),
      "Desktop parity matrix must keep dgram disabled after P13-31.",
    ),
    check(
      "no-limited-dgram-facade",
      !files.nodeBridgeSources.text.includes("function __autojs6_limited_dgram") &&
        !files.nodeBridgeSources.text.includes('if (name === "dgram" || name === "node:dgram")'),
      "P13-31 must not accidentally expose a limited dgram facade.",
    ),
    check(
      "local-shadow-and-raw-socket-denied",
      files.p13DgramTest.text.includes("writeShadowModule(workDir, \"dgram\")") &&
        files.p13DgramTest.text.includes("p13dgram.shadow=false") &&
        files.p13DgramTest.text.includes("process.getBuiltinModule(\"dgram\")") &&
        files.rawNetworkTest.text.includes("resolve_node_dgram") &&
        files.p13Report.text.includes("Local `node_modules/dgram`") &&
        files.p13Report.text.includes("No UDP socket is opened"),
      "P13-31 must keep local dgram shadow bypass and raw UDP socket authority denied until a provider exists.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-dgram-udp-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13DgramUdpDecision",
    task: "P13-31",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: desktopSurface || {
      id: "desktop.network_fs",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      dgramStableDenied: scenarioById(scenarios, "stable_disabled_runtime_boundary").status !== "missing_evidence",
      udpClientPromoted: false,
      udpServerPromoted: false,
      multicastPromoted: false,
      broadcastPromoted: false,
      localShadowAndRawSocketDenied: checkById(checks, "local-shadow-and-raw-socket-denied").passed,
      packagedLiveNetworkReady: false,
      fdLeakProofReady: false,
      desktopSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Phase 13 dgram UDP Decision Verification");
  lines.push("");
  lines.push(`- Task: ${report.task}`);
  lines.push(`- Gate: \`${report.gate}\``);
  lines.push(`- Decision: \`${report.decision.status}\``);
  lines.push(`- Surface: \`${report.surface.id}\` / \`${report.surface.status}\``);
  lines.push(`- Policy violations: ${report.policyViolations.length}`);
  lines.push(`- Remaining blockers: ${report.blockers.length}`);
  lines.push("");
  lines.push("## Scenarios");
  lines.push("");
  for (const scenario of report.scenarios) {
    lines.push(`### ${scenario.id}`);
    lines.push("");
    lines.push(`- Status: \`${scenario.status}\``);
    lines.push(`- ${scenario.title}`);
    if (scenario.remaining.length > 0) {
      lines.push("- Remaining:");
      for (const item of scenario.remaining) {
        lines.push(`  - ${item}`);
      }
    }
    lines.push("");
  }
  lines.push("## Checks");
  lines.push("");
  lines.push("| Check | Result | Message |");
  lines.push("| --- | --- | --- |");
  for (const check of report.checks) {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${check.message} |`);
  }
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push("```json");
  lines.push(JSON.stringify(report.decision, null, 2));
  lines.push("```");
  lines.push("");
  return lines.join("\n");
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === "--repo-root") {
      options.repoRoot = args[++i];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++i];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

main();
