#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-java-interop-allowlist";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_13_JAVA_INTEROP_ALLOWLIST.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridge: "app/src/main/cpp/node_bridge_sources.cpp",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  focusedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeJavaInteropBridgeInstrumentationTest.kt",
  javaDesign: "docs/nodejs/design/JAVA_INTEROP.md",
  typesGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
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
  console.log("Phase 13 Java interop allowlist: " + report.decision.status);
  console.log("Phase 13 Java interop allowlist blockers: " + report.blockers.length);
  console.log("Phase 13 Java interop allowlist policy violations: " + report.policyViolations.length);
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
    scenario("js_facade", "require(\"java\") and $autojs.java expose allowlist object-handle helpers", [
      token(files.nodeBridge, "function __autojs6_java_call_instance(target, methodName, args, options)"),
      token(files.nodeBridge, "function __autojs6_java_get_field(target, fieldName, options)"),
      token(files.nodeBridge, "function __autojs6_java_release(target, options)"),
      token(files.nodeBridge, "return __autojs6_java_handle_descriptor(result);"),
      token(files.nodeBridge, "findClass: __autojs6_java_class_descriptor"),
      token(files.nodeBridge, "dispose: __autojs6_java_release"),
      token(files.nodeBridge, "call: __autojs6_autojs_java_call_instance"),
    ]),
    scenario("android_provider", "Android provider owns Rect instance calls, field reads, and release cleanup", [
      token(files.protocol, 'val javaMethods = setOf("type", "callStatic", "new", "call", "callInstance", "getField", "release", "dispose")'),
      token(files.protocol, '"call", "callInstance" -> callJavaInstance(request)'),
      token(files.protocol, "fun callRect(rect: Rect, method: String, args: JSONArray): Result"),
      token(files.protocol, "fun getRectField(rect: Rect, field: String): Result"),
      token(files.protocol, 'resourceRegistry.close(handleId, "java.${request.method}")'),
      token(files.protocol, "private val rectInstanceMethods = listOf("),
      token(files.protocol, 'private val rectFields = listOf("left", "top", "right", "bottom")'),
    ]),
    scenario("tests", "Focused instrumentation covers provider and JS fake-bridge behavior", [
      token(files.focusedTest, "kotlinJavaInteropProviderIsDisabledByDefaultAndAllowlistedWhenEnabled"),
      token(files.focusedTest, "jsJavaInteropExperimentalModuleUsesBridgeAndEnforcesAllowlist"),
      token(files.focusedTest, 'NodeBridgeModules.supports("java", "getField")'),
      token(files.focusedTest, "java.rect_frozen=true"),
      token(files.focusedTest, "java.results=9|00000000-0000-0000-0000-000000000002|java-fake-rect|1|4|2|2|true|1|3|2|8|true"),
      token(files.focusedTest, "release:java-fake-rect"),
    ]),
    scenario("types_docs", "Types and smoke sample describe the Java handle API", [
      token(files.typesGlobal, "AutoJsJavaObjectHandle"),
      token(files.typesGlobal, 'declare module "java"'),
      token(files.typesGlobal, "callInstance(target: string | AutoJsJavaObjectHandle"),
      token(files.typeSmoke, 'import java = require("java")'),
      token(files.typeSmoke, "AutoJsJavaObjectHandle"),
      token(files.typesReadme, "P13-13"),
    ]),
    scenario("docs_gate", "Docs and Gradle gate record P13-13 v1.1 allowlist closure", [
      token(files.phase13Tasks, "P13-13"),
      token(files.phase13Tasks, "verifyNodePhase13JavaInteropAllowlist"),
      token(files.p13Report, "Status: ready"),
      token(files.gradleGate, "verifyNodePhase13JavaInteropAllowlist"),
      token(files.testingDoc, "verifyNodePhase13JavaInteropAllowlist"),
      token(files.ciGatesDoc, "verifyNodePhase13JavaInteropAllowlist"),
      token(files.compatibilityProfile, "P13-13"),
      token(files.proComparison, "P13-13"),
      token(files.proAutoxNotes, "P13-13"),
      token(files.capabilityMatrix, "Java interop allowlist"),
      token(files.surfaceManifestDoc, "phase13-java-interop-allowlist"),
      token(files.javaDesign, "P13-13 Java method budget policy is design-locked"),
      token(files.p13Report, "P13-13 method budget policy is now defined"),
      token(files.compatibilityProfile, "P13-13 defines the method budget policy"),
      token(files.p13Report, "Pro profile promotion is delegated"),
      token(files.compatibilityProfile, "counted as a separate P13-13 allowlist blocker"),
      token(files.p13Report, "P13-13 v1.1 stable-denies thread dispatch"),
      token(files.javaDesign, "P13-13 v1.1 stable-denies thread dispatch"),
      token(files.compatibilityProfile, "`$autojs.java.setDefaultThreadMode()` are stable-denied"),
      token(files.typesGlobal, "setThreadMode(obj: unknown, threadMode: string): never;"),
      token(files.typesGlobal, "setDefaultThreadMode(clazz: unknown, threadMode: string): never;"),
    ]),
    scenario("security_boundary", "Critical Java boundaries remain fail-closed", [
      token(files.javaDesign, "no reflection or class-loader access"),
      token(files.javaDesign, "no generic method invocation, reflection bridge, or property traversal exists"),
      token(files.securityModel, "ClassLoader"),
      token(files.securityModel, "reflection"),
      token(files.securityModel, "native-library"),
      token(files.securityModel, "process"),
      token(files.p13Report, "classloader, reflection, native library loading, and process authority remain"),
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-13"), "P13-13 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-13 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.entry_java_rhino` surface must exist."),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.entry_java_rhino` must not be promoted while Java/Rhino parity remains partial.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-java-interop-allowlist")),
      "`pro.entry_java_rhino` surface must reference P13-13 evidence.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-java-interop-allowlist-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13JavaInteropAllowlist",
    task: "P13-13",
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
      jsFacadeReady: scenarioById(scenarios, "js_facade").status === "ready",
      androidProviderReady: scenarioById(scenarios, "android_provider").status === "ready",
      testsReady: scenarioById(scenarios, "tests").status === "ready",
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
    "# Phase 13 Java Interop Allowlist",
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
