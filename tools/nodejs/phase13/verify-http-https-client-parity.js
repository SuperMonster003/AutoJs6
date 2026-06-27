#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-http-https-client-parity";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_29_HTTP_HTTPS_CLIENT_PARITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  liveFetchTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFetchBridgeInstrumentationTest.kt",
  p13HttpTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpDesktopParityInstrumentationTest.kt",
  httpClientTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpClientFacadeInstrumentationTest.kt",
  httpStreamTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpStreamSemanticsInstrumentationTest.kt",
  httpsClientTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpsClientFacadeInstrumentationTest.kt",
  httpAgentTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpAgentFacadeInstrumentationTest.kt",
  rawNetworkTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRawNetworkPolicyInstrumentationTest.kt",
  httpNpmCorpusTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpNpmCorpusInstrumentationTest.kt",
  httpNpmCorpusManifest: "app/src/androidTest/assets/http_compat_corpus/manifest.json",
  packagedMetadataTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest.kt",
  packagedMetadataReport: "docs/nodejs/reports/phase13/P13_45_PACKAGED_CAPABILITY_METADATA.md",
  httpTypes: "docs/nodejs/types/autojs6-node/http.d.ts",
  httpsTypes: "docs/nodejs/types/autojs6-node/https.d.ts",
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
  console.log("Phase 13 HTTP/HTTPS client parity: " + report.decision.status);
  console.log("Phase 13 HTTP/HTTPS client blockers: " + report.blockers.length);
  console.log("Phase 13 HTTP/HTTPS client policy violations: " + report.policyViolations.length);
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
    scenario("controlled_client_surface", "http/https client requests stay fetch-backed and capability-gated", [
      token(files.nodeBridgeSources, "function __autojs6_limited_http(protocol)"),
      token(files.nodeBridgeSources, "__autojs6_controlled_fetch(url, fetchInit)"),
      token(files.nodeBridgeSources, 'if (name === "http" || name === "node:http")'),
      token(files.nodeBridgeSources, 'if (name === "https" || name === "node:https")'),
      token(files.httpClientTest, "httpAndHttpsClientFacadeUseControlledFetchTransport"),
      token(files.httpStreamTest, "clientRequestAndIncomingMessageStreamSemanticsStayControlled"),
      token(files.httpsClientTest, "httpsClientFacadeKeepsTlsCustomizationDenied"),
      token(files.httpAgentTest, "limitedAgentShapeDoesNotExposeRawSockets"),
      token(files.p13HttpTest, "httpHttpsClientDesktopParityStaysControlledAndFailClosed"),
      token(files.p13HttpTest, "p13http.client=200|OK|GET"),
      token(files.p13HttpTest, "p13http.redirect=302|/target|manual|200|followed"),
      token(files.liveFetchTest, "jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkExperimentEnabled"),
      token(files.liveFetchTest, "live.fetch.get=200|GET|via=fetch|live"),
      token(files.liveFetchTest, "live.fetch.redirect=200|via=redirect"),
      token(files.liveFetchTest, "live.axios.get=200|GET|via=axios&tag=a&tag=b|axios"),
      token(files.liveFetchTest, "live.http.get=200|GET|via=http|http"),
      token(files.liveFetchTest, "live.fetch.diagnostics=0|8|1|1"),
    ]),
    scenario("negative_security_boundary", "raw network, server/listen, proxy/tunnel, and TLS customization remain denied", [
      token(files.nodeBridgeSources, '"proxyTunnel"'),
      token(files.nodeBridgeSources, '"proxyUrl"'),
      token(files.nodeBridgeSources, '"method.CONNECT"'),
      token(files.nodeBridgeSources, "Certificate customization requires a separate design gate."),
      token(files.p13HttpTest, "ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_POLICY_DENIED"),
      token(files.rawNetworkTest, "rawNetworkModulesStayDeniedAndCannotBeShadowed"),
      token(files.securityModel, "Raw network modules remain denied in the Safe profile"),
      token(files.compatibilityProfile, "P13-29 closes the controlled HTTP/HTTPS client subset for v1.1"),
    ]),
    scenario("desktop_client_package_corpus", "offline npm-shaped client corpus covers redirects, TLS failure, and proxy denial", [
      token(files.httpNpmCorpusManifest, '"name": "redirect-follow-client"'),
      token(files.httpNpmCorpusManifest, '"name": "tls-failure-client"'),
      token(files.httpNpmCorpusManifest, '"name": "proxy-denial-client"'),
      token(files.httpNpmCorpusManifest, "package following redirects"),
      token(files.httpNpmCorpusManifest, "package handling TLS provider failure"),
      token(files.httpNpmCorpusManifest, "package expecting proxy configuration denial"),
      token(files.httpNpmCorpusTest, "EXPECTED_FIXTURE_COUNT = 9"),
      token(files.httpNpmCorpusTest, "GET:http:/redirect-start|GET:http:/redirect-final|GET:https:/tls-fail"),
      token(files.p13Report, "P13-29 expands the offline npm-shaped HTTP corpus"),
      token(files.compatibilityProfile, "It adds nine"),
      token(files.testingDoc, "runs nine offline npm-shaped fixtures"),
    ]),
    scenario("types_docs_and_gate", "types, docs, matrix, and Gradle gate record P13-29 controlled-subset closure", [
      token(files.httpTypes, 'declare module "http"'),
      token(files.httpTypes, 'declare module "node:http"'),
      token(files.httpsTypes, 'declare module "https"'),
      token(files.httpsTypes, 'declare module "node:https"'),
      token(files.indexTypes, '/// <reference path="./http.d.ts" />'),
      token(files.indexTypes, '/// <reference path="./https.d.ts" />'),
      token(files.typeSmoke, 'import http = require("http")'),
      token(files.typeSmoke, 'import https = require("https")'),
      token(files.typesReadme, "P13-29 records the controlled HTTP/HTTPS client subset as ready"),
      token(files.gradleGate, "verifyNodePhase13HttpHttpsClientParity"),
      token(files.testingDoc, "verifyNodePhase13HttpHttpsClientParity"),
      token(files.ciGatesDoc, "verifyNodePhase13HttpHttpsClientParity"),
      token(files.surfaceManifestDoc, "phase13-http-https-client-parity"),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.desktopMatrix, "P13-29 records the controlled client subset"),
      token(files.builtinMatrixGenerator, "P13-29 records the controlled client subset"),
      token(files.capabilityMatrix, "P13-29 HTTP/HTTPS client parity"),
      token(files.proComparison, "P13-29 closes the controlled HTTP/HTTPS client subset for v1.1"),
      token(files.proAutoxNotes, "P13-29 closes the controlled HTTP/HTTPS client subset for v1.1"),
      token(files.packagedMetadataReport, "`network` capability metadata maps to `android.permission.INTERNET` through"),
      token(files.packagedMetadataTest, 'metadata.network.androidPermissions.join(",") === "android.permission.INTERNET"'),
      token(files.packagedMetadataTest, "network_capability_maps_android_internet"),
      token(files.packagedMetadataTest, "p13packaged.ready=true|true"),
      token(files.compatibilityProfile, "packaged builds derive `android.permission.INTERNET` from that capability"),
    ]),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-29"), "P13-29 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-29 report must exist."),
    check("surface-present", Boolean(desktopSurface), "`desktop.network_fs` surface must exist."),
    check(
      "surface-partial",
      desktopSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(desktopSurface.status),
      "`desktop.network_fs` must not be promoted by this partial gate.",
    ),
    check(
      "surface-evidence",
      desktopSurface && (desktopSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.network_fs` surface must reference P13-29 evidence.",
    ),
    check(
      "http-https-limited-network",
      files.desktopMatrix.text.includes("| `http` | `limited_network_gated` |") &&
        files.desktopMatrix.text.includes("| `https` | `limited_network_gated` |"),
      "Desktop parity matrix must keep http/https limited_network_gated after P13-29.",
    ),
    check(
      "no-raw-server-promotion",
      !files.nodeBridgeSources.text.includes("createServer: { value: createServer, enumerable: true, writable: true") &&
        files.nodeBridgeSources.text.includes('throw __autojs6_limited_http_disabled_error(protocol, "listen");'),
      "P13-29 must keep server listen fail-closed.",
    ),
    check(
      "raw-network-semantics-scoped-to-p13-30",
      files.p13Report.text.includes("Full raw socket, DNS lookup, TLS session, and backpressure semantics stay scoped") &&
        files.p13Report.text.includes("to P13-30 or later") &&
        files.p13Report.text.includes("P13-29 only tracks controlled fetch-backed client") &&
        files.p13Report.text.includes("semantics"),
      "P13-29 must keep full raw socket/DNS/TLS/backpressure semantics scoped to P13-30 or later.",
    ),
    check(
      "per-request-proxy-denied",
      files.p13Report.text.includes("Per-request proxy support stays denied in P13-29") &&
        files.p13Report.text.includes("only provider/system-owned") &&
        files.p13Report.text.includes("proxy behavior may exist behind controlled fetch") &&
        files.httpNpmCorpusManifest.text.includes('"name": "proxy-denial-client"'),
      "P13-29 must keep per-request proxy support denied unless a provider-owned design promotes it later.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-http-https-client-parity-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13HttpHttpsClientParity",
    task: "P13-29",
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
      controlledHttpClientReady: scenarioById(scenarios, "controlled_client_surface").status !== "missing_evidence",
      liveOkHttpProviderEvidenceReady: scenarioById(scenarios, "controlled_client_surface").status === "ready",
      rawSocketAuthorityPromoted: false,
      rawNetworkSemanticsScopedToP13_30: checkById(checks, "raw-network-semantics-scoped-to-p13-30").passed,
      perRequestProxyDenied: checkById(checks, "per-request-proxy-denied").passed,
      packagedNetworkPermissionReady: scenarioById(scenarios, "types_docs_and_gate").status === "ready",
      packagedLiveNetworkReady: finalBlockerClosable,
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

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
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
  lines.push("# Phase 13 HTTP/HTTPS Client Parity Verification");
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
