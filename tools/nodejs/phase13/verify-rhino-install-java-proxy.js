#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-rhino-install-java-proxy";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_14_RHINO_INSTALL_JAVA_PROXY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridge: "app/src/main/cpp/node_bridge_sources.cpp",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  focusedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt",
  rhinoTypes: "docs/nodejs/types/autojs6-node/rhino.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  rhinoDesign: "docs/nodejs/design/RHINO_SHIM.md",
  javaDesign: "docs/nodejs/design/JAVA_INTEROP.md",
  javaAdapterReport: "docs/nodejs/reports/phase13/P13_16_JAVA_ADAPTER_DECISION.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
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
  console.log("Phase 13 rhino.install Java proxy: " + report.decision.status);
  console.log("Phase 13 rhino.install Java proxy blockers: " + report.blockers.length);
  console.log("Phase 13 rhino.install Java proxy policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.entry_java_rhino");
  const scenarios = [
    scenario("default_denial", "Default Safe profile keeps rhino.install denied and rhino shadowing reserved", [
      token(files.nodeBridge, "function __autojs6_rhino_install_unsupported_error()"),
      token(files.nodeBridge, "explicit experimental Java proxy install requires the Java interop gate"),
      token(files.focusedTest, "rhinoShimFacadeIsDisabledByDefault"),
      token(files.focusedTest, "rhino.install=ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED"),
      token(files.focusedTest, 'require.resolve("rhino"), "rhino"'),
      token(files.focusedTest, "globalThis.Packages, undefined"),
    ]),
    scenario("java_proxy_install", "Explicit experimental rhino.install installs Packages/java/android Java allowlist proxies", [
      token(files.nodeBridge, "const __autojs6_rhino_java_global_names = Object.freeze([\"Packages\", \"java\", \"android\"])"),
      token(files.nodeBridge, "function __autojs6_rhino_java_proxy(pathValue)"),
      token(files.nodeBridge, "function __autojs6_rhino_install(options)"),
      token(files.nodeBridge, 'schema: "autojs6-rhino-install-java-proxy-v1"'),
      token(files.nodeBridge, "return __autojs6_java_call_static(className, methodName"),
      token(files.nodeBridge, "return __autojs6_java_new(path"),
      token(files.focusedTest, "rhinoInstallExperimentalJavaProxyUsesAllowlistWhenJavaInteropEnabled"),
      token(files.focusedTest, "rhino.install.results=9|00000000-0000-0000-0000-000000000004|java-rhino-rect|1|8|5|1|true"),
    ]),
    scenario("security_boundary", "Rhino Java globals remain proxies, not raw Java/Rhino objects", [
      token(files.focusedTest, "rhino.install.globals=function|function|function|undefined|undefined|undefined"),
      token(files.focusedTest, "rhino.install.denied=ERR_AUTOJS6_JAVA_CLASS_DENIED|true"),
      token(files.providerRegistry, "P13-14 Packages/java/android proxies route only through the Java allowlist experiment"),
      token(files.securityModel, "P13-14"),
      token(files.rhinoDesign, "P13-14"),
      token(files.javaDesign, "no generic method invocation, reflection bridge, or property traversal exists"),
    ]),
    scenario("types_docs", "Types and smoke sample expose explicit experimental install result only", [
      token(files.rhinoTypes, "InstallOptions"),
      token(files.rhinoTypes, "InstallResult"),
      token(files.rhinoTypes, 'readonly installed: readonly ["Packages", "java", "android"]'),
      token(files.typeSmoke, "rhino.InstallResult"),
      token(files.typeSmoke, 'rhino.install({ experimental: true, target: {} })'),
      token(files.typesReadme, "P13-14"),
    ]),
    scenario("docs_gate", "Docs and Gradle gate record P13-14 partial evidence", [
      token(files.phase13Tasks, "P13-14"),
      token(files.phase13Tasks, "verifyNodePhase13RhinoInstallJavaProxy"),
      token(files.p13Report, "Status: partial"),
      token(files.gradleGate, "verifyNodePhase13RhinoInstallJavaProxy"),
      token(files.testingDoc, "verifyNodePhase13RhinoInstallJavaProxy"),
      token(files.ciGatesDoc, "verifyNodePhase13RhinoInstallJavaProxy"),
      token(files.compatibilityProfile, "P13-14"),
      token(files.proComparison, "P13-14"),
      token(files.proAutoxNotes, "P13-14"),
      token(files.capabilityMatrix, "rhino.install Java proxy"),
      token(files.surfaceManifestDoc, "phase13-rhino-install-java-proxy"),
      token(files.p13Report, "The P13-16 v1.1 replacement decision is satisfied for this gate"),
      token(files.javaAdapterReport, "P13-16 v1.1 closure is a stable-denial decision"),
      token(files.compatibilityProfile, "Its P13-16 dependency is satisfied by the stable-denial"),
    ], [
      "A real Pro profile runtime selector remains unimplemented.",
      "Packaged APK policy remains fail-closed.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-14"), "P13-14 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-14 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.entry_java_rhino` surface must exist."),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.entry_java_rhino` must not be promoted while Rhino install remains partial.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-rhino-install-java-proxy")),
      "`pro.entry_java_rhino` surface must reference P13-14 evidence.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-rhino-install-java-proxy-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13RhinoInstallJavaProxy",
    task: "P13-14",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.entry_java_rhino",
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
      defaultDenialPreserved: scenarioById(scenarios, "default_denial").status === "ready",
      javaProxyInstallReady: scenarioById(scenarios, "java_proxy_install").status === "ready",
      securityBoundaryReady: scenarioById(scenarios, "security_boundary").status === "ready",
      typesReady: scenarioById(scenarios, "types_docs").status === "ready",
      safeNodeProfilePreserved: true,
      proSurfacePromoted: false,
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
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(stripBom(text));
    return (manifest.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 rhino.install Java Proxy",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "None.")} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|");
}

main();
