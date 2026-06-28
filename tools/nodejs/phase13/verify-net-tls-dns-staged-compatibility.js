#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-net-tls-dns-staged-compatibility";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_30_NET_TLS_DNS_STAGED_COMPATIBILITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  buildGradle: "app/build.gradle.kts",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  runtimeBridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  p13NetworkTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNetTlsDnsStagedParityInstrumentationTest.kt",
  rawNetworkTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRawNetworkPolicyInstrumentationTest.kt",
  embeddedMvpTest: "app/src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt",
  netTypes: "docs/nodejs/types/autojs6-node/net.d.ts",
  tlsTypes: "docs/nodejs/types/autojs6-node/tls.d.ts",
  dnsTypes: "docs/nodejs/types/autojs6-node/dns.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
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
  console.log("Phase 13 net/tls/dns staged compatibility: " + report.decision.status);
  console.log("Phase 13 net/tls/dns blockers: " + report.blockers.length);
  console.log("Phase 13 net/tls/dns policy violations: " + report.policyViolations.length);
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
    scenario("limited_network_builtin_surface", "reserved net/tls/dns builtins expose only staged compatibility shapes", [
      token(files.nodeBridgeSources, "function __autojs6_limited_net()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_tls()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_dns()"),
      token(files.nodeBridgeSources, "function __autojs6_limited_dns_promises()"),
      token(files.nodeBridgeSources, 'resolveTlsa: __autojs6_limited_dns_disabled_callback("resolveTlsa")'),
      token(files.nodeBridgeSources, 'resolveTlsa: __autojs6_limited_dns_disabled_promise("resolveTlsa")'),
      token(files.nodeBridgeSources, 'if (name === "net" || name === "node:net")'),
      token(files.nodeBridgeSources, 'if (name === "tls" || name === "node:tls")'),
      token(files.nodeBridgeSources, 'if (name === "dns" || name === "node:dns")'),
      token(files.nodeBridgeSources, 'if (name === "dns/promises" || name === "node:dns/promises")'),
      token(files.p13NetworkTest, "netTlsDnsStagedCompatibilityStaysLimitedAndFailClosed"),
      token(files.p13NetworkTest, "p13ntd.net.shape=4|6|0|true|true"),
      token(files.p13NetworkTest, "p13ntd.dns.lookup=127.0.0.1:4,::1:6"),
      token(files.p13NetworkTest, "p13ntd.dns.shape=function|function|function|function|ECANCELLED"),
      token(files.p13NetworkTest, "p13ntd.tls.shape=TLSv1.2|true|false"),
    ], [
      "Real TCP/TLS client sockets remain unpromoted until connection budgets, host policy, Android permission mapping, background behavior, and fd cleanup evidence land.",
      "Server sockets remain staged until listener lifecycle, bind address policy, port ownership, foreground/background behavior, and packaged rollback evidence exist.",
    ]),
    scenario("negative_network_boundary", "default Safe profile raw socket, DNS, server, TLS session, and shadow-bypass paths remain denied", [
      token(files.p13NetworkTest, "p13ntd.net.denials=ERR_AUTOJS6_NETWORK_DISABLED"),
      token(files.p13NetworkTest, "p13ntd.dns.denials=ERR_AUTOJS6_NETWORK_DISABLED"),
      token(files.p13NetworkTest, "p13ntd.tls.denials=false:ERR_AUTOJS6_NETWORK_DISABLED"),
      token(files.p13NetworkTest, "socketHasFd"),
      token(files.rawNetworkTest, "rawNetworkModulesStayDeniedAndCannotBeShadowed"),
      token(files.embeddedMvpTest, "limitedDnsBuiltinSupportsLocalLookupsAndBlocksNetworkResolution"),
      token(files.embeddedMvpTest, "limitedNetBuiltinSupportsIpHelpersAndBlocksNetworkSockets"),
      token(files.embeddedMvpTest, "limitedTlsBuiltinExposesContextMetadataAndBlocksTlsSockets"),
      token(files.compatibilityProfile, "P13-30 records net/tls/dns staged compatibility as partial"),
      token(files.securityModel, "P13-30 records net/tls/dns staged compatibility as partial"),
    ], [
      "DNS remains loopback/IP-literal-only and must not expose Android resolver servers, private search domains, VPN state, or local network topology.",
      "No native fd/socket handle leak proof exists beyond the current shape-only socket close/destroy assertions.",
    ]),
    scenario("raw_network_modules_opt_in", "explicit debug-only raw network module opt-in can route selected builtins to native Node modules", [
      token(files.buildGradle, "autojs.nodejs.rawNetworkModules.experimental"),
      token(files.runtimeCapabilities, "RAW_NETWORK_MODULES_EXPERIMENTAL_PROPERTY"),
      token(files.runtimeBridge, "rawNodeNetworkModulesExperimentalEnabled"),
      token(files.nodeBridgeSources, "__autojs6_raw_node_network_modules_experimental_enabled"),
      token(files.nodeBridgeSources, "function __autojs6_raw_node_network_builtin(name)"),
      token(files.nodeBridgeSources, 'gradleProperty: "autojs.nodejs.rawNetworkModules.experimental"'),
      token(files.nodeBridgeSources, "rawNodeNetworkModules: __autojs6_raw_node_network_modules_experimental_enabled"),
      token(files.p13NetworkTest, "rawNodeNetworkModulesOptInExposesNativeBuiltins"),
      token(files.p13NetworkTest, "rawNodeNetworkModulesExperimentalEnabled = true"),
      token(files.p13NetworkTest, 'dns.resolve("www.github.com"'),
      token(files.p13NetworkTest, "p13ntd.raw.sync=allowed|allowed|allowed"),
      token(files.p13Report, "The external DNS sample is opportunistic evidence"),
      token(files.ciGatesDoc, "raw-network opt-in external DNS sample is opportunistic evidence"),
    ], [
      "The opt-in is build/debug policy-gated and still needs host allow/deny, connection budgets, byte/time limits, lifecycle cleanup, and release/published-profile decisions.",
    ]),
    scenario("types_docs_and_gate", "types, docs, desktop matrix, and Gradle gate record P13-30 partial progress", [
      token(files.netTypes, 'declare module "net"'),
      token(files.netTypes, 'declare module "node:net"'),
      token(files.tlsTypes, 'declare module "tls"'),
      token(files.tlsTypes, 'declare module "node:tls"'),
      token(files.dnsTypes, 'declare module "dns"'),
      token(files.dnsTypes, 'declare module "dns/promises"'),
      token(files.dnsTypes, "export interface TlsaRecord"),
      token(files.dnsTypes, 'resolve<T extends string>(hostname: string, rrtype: T'),
      token(files.dnsTypes, 'export const CANCELLED: "ECANCELLED"'),
      token(files.indexTypes, '/// <reference path="./net.d.ts" />'),
      token(files.indexTypes, '/// <reference path="./tls.d.ts" />'),
      token(files.indexTypes, '/// <reference path="./dns.d.ts" />'),
      token(files.typeSmoke, 'import net = require("net")'),
      token(files.typeSmoke, 'import tls = require("tls")'),
      token(files.typeSmoke, 'import dns = require("dns")'),
      token(files.typeSmoke, 'import dnsPromises = require("dns/promises")'),
      token(files.typeSmoke, 'const dnsAnyRecord: dns.AnyRecord = { type: "TLSA"'),
      token(files.typeSmoke, 'const dnsPromiseTlsa: Promise<readonly dnsPromises.TlsaRecord[]>'),
      token(files.typesReadme, "P13-30 records the partial net/tls/dns staged compatibility gate"),
      token(files.gradleGate, "verifyNodePhase13NetTlsDnsStagedCompatibility"),
      token(files.testingDoc, "verifyNodePhase13NetTlsDnsStagedCompatibility"),
      token(files.ciGatesDoc, "verifyNodePhase13NetTlsDnsStagedCompatibility"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-30 records staged limited facades"),
      token(files.builtinMatrixGenerator, "P13-30 records staged limited facades"),
      token(files.capabilityMatrix, "P13-30 net/tls/dns staged compatibility"),
      token(files.proComparison, "P13-30 records net/tls/dns staged compatibility as partial"),
      token(files.proAutoxNotes, "P13-30 records net/tls/dns staged compatibility as partial"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-30"), "P13-30 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-30 report must exist."),
    check("surface-present", Boolean(desktopSurface), "`desktop.network_fs` surface must exist."),
    check(
      "surface-partial",
      desktopSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(desktopSurface.status),
      "`desktop.network_fs` must not be promoted by this partial gate.",
    ),
    check(
      "surface-evidence",
      desktopSurface && (desktopSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.network_fs` surface must reference P13-30 evidence.",
    ),
    check(
      "network-builtins-remain-limited",
      ["dns", "dns/promises", "net", "tls"].every((name) =>
        files.desktopMatrix.text.includes(`| \`${name}\` | \`limited_network_gated\` |`),
      ),
      "Desktop parity matrix must keep dns, dns/promises, net, and tls limited_network_gated after P13-30.",
    ),
    check(
      "server-listen-fail-closed",
      files.nodeBridgeSources.text.includes('throw __autojs6_limited_net_disabled_error("listen");') &&
        files.nodeBridgeSources.text.includes('throw __autojs6_limited_tls_disabled_error("listen");'),
      "P13-30 must keep net/tls server listen fail-closed.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-net-tls-dns-staged-compatibility-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13NetTlsDnsStagedCompatibility",
    task: "P13-30",
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
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      stagedFacadesReady: scenarioById(scenarios, "limited_network_builtin_surface").status !== "missing_evidence",
      rawNetworkModulesExperimentalOptIn: scenarioById(scenarios, "raw_network_modules_opt_in").status !== "missing_evidence",
      rawSocketAuthorityPromoted: false,
      serverSocketAuthorityPromoted: false,
      dnsNetworkResolutionPromoted: false,
      tlsSessionAuthorityPromoted: false,
      packagedLiveNetworkReady: false,
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
  lines.push("# Phase 13 net/tls/dns Staged Compatibility Verification");
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
