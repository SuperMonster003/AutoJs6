#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-pro-entry-directives";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_11_PRO_ENTRY_DIRECTIVES.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  javaScriptSource: "app/src/main/java/org/autojs/autojs/script/JavaScriptSource.java",
  entryDirective: "app/src/main/java/org/autojs/autojs/script/NodeEntryDirective.kt",
  launchDescriptor: "app/src/main/java/org/autojs/autojs/script/NodeProjectLaunchDescriptor.kt",
  launchSource: "app/src/main/java/org/autojs/autojs/script/NodeProjectJavaScriptFileSource.kt",
  projectRunner: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedProjectRunner.kt",
  diagnostics: "app/src/main/java/org/autojs/autojs/engine/NodeProjectLaunchDiagnostics.kt",
  assetsLauncher: "app/src/main/java/org/autojs/autojs/inrt/launch/AssetsProjectLauncher.kt",
  projectConfig: "app/src/main/java/org/autojs/autojs/project/ProjectConfig.java",
  focusedTest: "app/src/androidTest/java/org/autojs/autojs/engine/EmbeddedScriptMvpInstrumentationTest.kt",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
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
  console.log("Phase 13 Pro entry directives: " + report.decision.status);
  console.log("Phase 13 Pro entry directive blockers: " + report.blockers.length);
  console.log("Phase 13 Pro entry directive policy violations: " + report.policyViolations.length);
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
    scenario("source_directives", "JavaScript source parser recognizes Pro-style Node UI directives", [
      token(files.javaScriptSource, ".put(\"nodejs\", EXECUTION_MODE_NODEJS)"),
      token(files.javaScriptSource, ".put(\"node\", EXECUTION_MODE_NODEJS)"),
      token(files.javaScriptSource, ".put(\"ui\", EXECUTION_MODE_UI)"),
      token(files.javaScriptSource, ".put(\"ui-thread\", EXECUTION_MODE_UI_THREAD)"),
      token(files.javaScriptSource, "tokenString.split(\"\\\\s*[,;|]\\\\s*|\\\\s+\")"),
      token(files.entryDirective, "DIRECTIVE_NODEJS_UI"),
      token(files.entryDirective, "DIRECTIVE_NODEJS_UI_THREAD"),
      token(files.entryDirective, "fromExecutionMode"),
      token(files.focusedTest, "proStyleNodeEntryDirectivesParseToStableMetadata"),
    ]),
    scenario("metadata_chain", "Directive metadata flows through descriptor, source, request, and debug/native values", [
      token(files.launchDescriptor, "entryDirective = json.optNodeEntryDirectiveOrNull()"),
      token(files.launchSource, "nodeProjectEntryDirective"),
      token(files.projectRunner, "entryDirective = NodeEntryDirective.normalize"),
      token(files.projectRunner, "embedded_project_runner.entry_directive"),
      token(files.projectRunner, "entry_directive_ui_activity"),
      token(files.projectRunner, "entry_directive_ui_thread"),
      token(files.diagnostics, "entryDirective="),
      token(files.focusedTest, "nodeProjectLaunchDescriptorFileSourceAndProjectRequestStayAligned"),
    ]),
    scenario("packaged_config", "Packaged project config can provide node.entryDirective without promoting UI lifecycle yet", [
      token(files.projectConfig, '@SerializedName("entryDirective")'),
      token(files.assetsLauncher, "nodeConfig.entryDirective"),
      token(files.assetsLauncher, "Packaged Node entryDirective mismatch"),
      token(files.assetsLauncher, "does not support UI entry directives yet"),
      token(files.assetsLauncher, "entryDirective="),
      token(files.focusedTest, "NodeEntryDirective.DIRECTIVE_NODEJS_UI"),
    ]),
    scenario("ui_provider_boundary", "Entry directives remain separate from the require(\"ui\") provider", [
      token(files.p13Report, "does not promote `require(\"ui\")`"),
      token(files.p13Report, "P13-11 UI-thread dispatch policy:"),
      token(files.p13Report, "not permission to run the V8 isolate on the"),
      token(files.compatibilityProfile, "it does not run the V8 isolate"),
      token(files.compatibilityProfile, "\"nodejs ui\""),
      token(files.proComparison, "\"nodejs ui\""),
      token(files.proComparison, "P13-11"),
    ], [
      "Full Activity lifecycle launch evidence is still required.",
      "Back/Recents behavior for packaged Node UI entries is still required.",
    ]),
    scenario("docs_gate", "Docs and Gradle gate record P13-11 partial progress", [
      token(files.phase13Tasks, "P13-11"),
      token(files.phase13Tasks, "verifyNodePhase13ProEntryDirectives"),
      token(files.p13Report, "Status: partial"),
      token(files.testingDoc, "verifyNodePhase13ProEntryDirectives"),
      token(files.ciGatesDoc, "verifyNodePhase13ProEntryDirectives"),
      token(files.capabilityMatrix, "NodeEntryDirective"),
      token(files.surfaceManifestDoc, "phase13-pro-entry-directives"),
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-11"), "P13-11 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-11 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.entry_java_rhino` surface must exist."),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.entry_java_rhino` must not be promoted while UI lifecycle evidence is missing.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-pro-entry-directives")),
      "`pro.entry_java_rhino` surface must reference P13-11 evidence.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-pro-entry-directives-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ProEntryDirectives",
    task: "P13-11",
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
      sourceDirectiveReady: scenarioById(scenarios, "source_directives").status === "ready",
      metadataChainReady: scenarioById(scenarios, "metadata_chain").status === "ready",
      packagedConfigReady: scenarioById(scenarios, "packaged_config").status === "ready",
      uiLifecycleReady: scenarioById(scenarios, "ui_provider_boundary").status === "ready",
      safeNodeProfilePreserved: true,
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
    "# Phase 13 Pro Entry Directives",
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
      console.log("Usage: node verify-pro-entry-directives.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
