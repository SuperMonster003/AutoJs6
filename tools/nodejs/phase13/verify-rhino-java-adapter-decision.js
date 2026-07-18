#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { resolvePhase13SourceFile } = require("./source-topology");

const REPORT_BASENAME = "phase13-rhino-java-adapter-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_16_JAVA_ADAPTER_DECISION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridge: "app/src/main/cpp/node_bridge_sources.cpp",
  rhinoTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt",
  javaTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeJavaInteropBridgeInstrumentationTest.kt",
  rhinoTypes: "docs/nodejs/types/autojs6-node/rhino.d.ts",
  autojsTypes: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  rhinoDesign: "docs/nodejs/design/RHINO_SHIM.md",
  javaDesign: "docs/nodejs/design/JAVA_INTEROP.md",
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
  console.log("Phase 13 JavaAdapter replacement decision: " + report.decision.status);
  console.log("Phase 13 JavaAdapter replacement decision blockers: " + report.blockers.length);
  console.log("Phase 13 JavaAdapter replacement decision policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = resolvePhase13SourceFile(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const proSurface = surface(files.surfaceManifest.text, "pro.entry_java_rhino");
  const scenarios = [
    scenario("stable_java_adapter_error", "rhino.JavaAdapter has a stable explicit denial and no global install", [
      token(files.nodeBridge, 'javaAdapterUnsupported: "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED"'),
      token(files.nodeBridge, "function __autojs6_rhino_java_adapter_unsupported_error()"),
      token(files.nodeBridge, "function JavaAdapter()"),
      token(files.rhinoTest, "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED"),
      token(files.rhinoTest, "profile.errorCodes.rhinoJavaAdapterUnsupported"),
      token(files.rhinoTest, "globalThis.JavaAdapter, undefined"),
      token(files.rhinoTest, "rhino.JavaAdapter=ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED"),
    ]),
    scenario("define_class_boundary", "java.defineClass and $autojs.java.defineClass are reserved stable denials", [
      token(files.nodeBridge, "function __autojs6_java_define_class_unsupported()"),
      token(files.nodeBridge, "defineClass: __autojs6_java_define_class_unsupported"),
      token(files.javaTest, "java.defineClass"),
      token(files.javaTest, "autojsJava.defineClass"),
      token(files.javaTest, "ERR_AUTOJS6_JAVA_METHOD_DENIED|ERR_AUTOJS6_JAVA_METHOD_DENIED"),
      token(files.autojsTypes, "defineClass(descriptor?: unknown, options?: BridgeCallOptions): never"),
    ]),
    scenario("migration_decision_docs", "Docs close the P13-16 v1.1 stable-denial boundary and future provider work", [
      token(files.p13Report, "Status: ready"),
      token(files.p13Report, "$java.defineClass"),
      token(files.p13Report, "proxy-interface"),
      token(files.p13Report, "bounded lifecycle"),
      token(files.p13Report, "P13-16 v1.1 closure is a stable-denial decision"),
      token(files.p13Report, "Future Provider Work"),
      token(files.rhinoDesign, "P13-16"),
      token(files.javaDesign, "P13-16"),
      token(files.compatibilityProfile, "P13-16"),
      token(files.securityModel, "P13-16"),
    ]),
    scenario("types_and_smoke", "Type declarations and smoke sample expose reserved signatures only", [
      token(files.rhinoTypes, 'ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED'),
      token(files.rhinoTypes, "JavaAdapter(...args: readonly unknown[]): never"),
      token(files.typeSmoke, "rhinoJavaAdapterDenied"),
      token(files.typeSmoke, "javaDefineClassDenied"),
      token(files.typeSmoke, "autojsJavaDefineClassDenied"),
      token(files.typesReadme, "P13-16"),
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record the P13-16 decision", [
      token(files.phase13Tasks, "P13-16"),
      token(files.phase13Tasks, "verifyNodePhase13RhinoJavaAdapterDecision"),
      token(files.gradleGate, "verifyNodePhase13RhinoJavaAdapterDecision"),
      token(files.testingDoc, "verifyNodePhase13RhinoJavaAdapterDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13RhinoJavaAdapterDecision"),
      token(files.proComparison, "P13-16"),
      token(files.proAutoxNotes, "P13-16"),
      token(files.capabilityMatrix, "JavaAdapter replacement decision"),
      token(files.surfaceManifestDoc, "phase13-rhino-java-adapter-decision"),
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-16"), "P13-16 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-16 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.entry_java_rhino` surface must exist."),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.entry_java_rhino` must not be promoted by the JavaAdapter decision.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-rhino-java-adapter-decision")),
      "`pro.entry_java_rhino` surface must reference P13-16 evidence.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-rhino-java-adapter-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13RhinoJavaAdapterDecision",
    task: "P13-16",
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
      javaAdapterUnsupported: scenarioById(scenarios, "stable_java_adapter_error").status === "ready",
      defineClassDesignGated: scenarioById(scenarios, "define_class_boundary").status === "ready",
      docsReady: scenarioById(scenarios, "migration_decision_docs").status !== "missing_evidence",
      typesReady: scenarioById(scenarios, "types_and_smoke").status === "ready",
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
    "# Phase 13 JavaAdapter Replacement Decision",
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
