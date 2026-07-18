#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { resolvePhase13SourceFile } = require("./source-topology");

const REPORT_BASENAME = "phase13-autojs-global-v1";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_12_AUTOJS_GLOBAL_V1.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridge: "app/src/main/cpp/node_bridge_sources.cpp",
  focusedTest: "app/src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt",
  typesIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
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
  console.log("Phase 13 $autojs global v1: " + report.decision.status);
  console.log("Phase 13 $autojs global v1 blockers: " + report.blockers.length);
  console.log("Phase 13 $autojs global v1 policy violations: " + report.policyViolations.length);
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
    scenario("runtime_global", "$autojs is installed before user code as a frozen global", [
      token(files.nodeBridge, "let __autojs6_autojs_global_cache = null"),
      token(files.nodeBridge, "function __autojs6_autojs_global()"),
      token(files.nodeBridge, 'schema: "autojs6-node-autojs-global-v1"'),
      token(files.nodeBridge, 'Object.defineProperty(globalThis, "$autojs"'),
      token(files.nodeBridge, "__autojs6_install_autojs_global();"),
      token(files.focusedTest, "autojsGlobalV1ExposesFrozenDiagnosticsAndGatedJavaFacade"),
      token(files.focusedTest, "autojs.global=object/false/false/false/true"),
    ]),
    scenario("safe_android_context", "Safe profile exposes only a read-only Android Context descriptor", [
      token(files.nodeBridge, "function __autojs6_autojs_android_context_descriptor(host)"),
      token(files.nodeBridge, 'schema: "autojs6-node-autojs-android-context-v1"'),
      token(files.nodeBridge, "Raw Android Context is not exposed in the Safe Node Profile."),
      token(files.focusedTest, "autojs.context=false/true/android.content.Context/string/true"),
      token(files.securityModel, "$autojs.androidContext"),
    ]),
    scenario("java_gated_entry", "$autojs.java routes through the existing Java interop gate", [
      token(files.nodeBridge, "function __autojs6_autojs_java_facade()"),
      token(files.nodeBridge, "findClass: __autojs6_autojs_java_find_class"),
      token(files.nodeBridge, "policy: __autojs6_java_policy_snapshot()"),
      token(files.nodeBridge, "__autojs6_limited_java().type(className)"),
      token(files.nodeBridge, "__autojs6_java_interop_disabled_error()"),
      token(files.focusedTest, "autojs.java.codes=ERR_AUTOJS6_JAVA_INTEROP_DISABLED/ERR_AUTOJS6_JAVA_INTEROP_DISABLED/ERR_AUTOJS6_JAVA_INTEROP_DISABLED"),
    ]),
    scenario("profile_type_docs", "Profile and TypeScript declarations describe the v1 shape", [
      token(files.typesIndex, "autojs_global.d.ts"),
      token(files.typesGlobal, "declare const $autojs"),
      token(files.typesGlobal, 'declare module "autojs6:profile"'),
      token(files.typesGlobal, "AutoJsJavaFacade"),
      token(files.typeSmoke, 'import autojsProfile = require("autojs6:profile")'),
      token(files.typeSmoke, "$autojs.java.policy"),
      token(files.typesReadme, "P13-12 adds the read-only `$autojs` global v1"),
    ]),
    scenario("docs_gate", "Docs and Gradle gate record P13-12 evidence", [
      token(files.phase13Tasks, "P13-12"),
      token(files.phase13Tasks, "verifyNodePhase13AutoJsGlobalV1"),
      token(files.p13Report, "Status: ready"),
      token(files.testingDoc, "verifyNodePhase13AutoJsGlobalV1"),
      token(files.ciGatesDoc, "verifyNodePhase13AutoJsGlobalV1"),
      token(files.compatibilityProfile, "`$autojs` global v1"),
      token(files.proComparison, "P13-12"),
      token(files.proAutoxNotes, "$autojs global v1"),
      token(files.capabilityMatrix, "`$autojs` global v1"),
      token(files.surfaceManifestDoc, "phase13-autojs-global-v1"),
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-12"), "P13-12 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-12 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.entry_java_rhino` surface must exist."),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.entry_java_rhino` must not be promoted by the diagnostic-only $autojs v1 gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-autojs-global-v1")),
      "`pro.entry_java_rhino` surface must reference P13-12 evidence.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-autojs-global-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13AutoJsGlobalV1",
    task: "P13-12",
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
      runtimeGlobalReady: scenarioById(scenarios, "runtime_global").status === "ready",
      safeAndroidContextReady: scenarioById(scenarios, "safe_android_context").status === "ready",
      javaGatedEntryReady: scenarioById(scenarios, "java_gated_entry").status === "ready",
      typeDeclarationsReady: scenarioById(scenarios, "profile_type_docs").status === "ready",
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
    "# Phase 13 $autojs Global V1",
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

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function stripBom(value) {
  return String(value || "").replace(/^\uFEFF/, "");
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-autojs-global-v1.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
  const normalized = String(value).trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(normalized)) return true;
  if (["0", "false", "no", "off"].includes(normalized)) return false;
  throw new Error("Invalid boolean value: " + value);
}

main();
