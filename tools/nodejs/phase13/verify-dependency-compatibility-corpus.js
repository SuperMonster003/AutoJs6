#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-dependency-compatibility-corpus";
const REQUIRED_REASON_KINDS = ["missing_builtin", "native_addon", "postinstall", "network", "fs"];

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_47_DEPENDENCY_COMPATIBILITY_CORPUS.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  realNpmManifest: "app/src/androidTest/assets/npm_compat_real/manifest.json",
  realNpmTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeRealNpmCompatInstrumentationTest.kt",
  differentialRunner: "tools/nodejs/differential/run-differential-tests.js",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  phase13Summary: "docs/nodejs/reports/phase13/PHASE13_SUMMARY.md",
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
  console.log("Phase 13 dependency compatibility corpus decision: " + report.decision.status);
  console.log("Phase 13 dependency compatibility corpus blockers: " + report.blockers.length);
  console.log("Phase 13 dependency compatibility corpus policy violations: " + report.policyViolations.length);
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
  const manifest = parseJson(files.realNpmManifest.text);
  const compatibility = manifest && manifest.phase13Compatibility || {};
  const fixtureNames = new Set((manifest && manifest.fixtures || []).map((fixture) => fixture.name));
  const categories = Array.isArray(compatibility.categories) ? compatibility.categories : [];
  const targetedDeltas = Array.isArray(compatibility.targetedFixtureDeltas) ? compatibility.targetedFixtureDeltas : [];
  const reasonKinds = new Set(categories.map((category) => category.reasonKind));
  const surfaceEntry = surface(files.surfaceManifest.text, "packaging.plugins_distribution");
  const targetedDeltaRows = targetedDeltas.map((delta) => ({
    area: String(delta && delta.area || ""),
    fixtures: Array.isArray(delta && delta.fixtures) ? delta.fixtures.map(String) : [],
    passDelta: Number(delta && delta.passDelta),
    notes: String(delta && delta.notes || ""),
  }));
  const categoryRows = categories.map((category) => ({
    id: String(category.id || ""),
    title: String(category.title || ""),
    reasonKind: String(category.reasonKind || ""),
    fixtures: Array.isArray(category.fixtures) ? category.fixtures.map(String) : [],
    packages: Array.isArray(category.packages)
      ? category.packages.map((row) => ({
        name: String(row && row.name || ""),
        reason: String(row && row.reason || ""),
      }))
      : [],
  }));

  const scenarios = [
    scenario("manifest_taxonomy", "Manifest classifies dependency compatibility blockers by package and reason", [
      token(files.realNpmManifest, '"phase13Compatibility"'),
      token(files.realNpmManifest, '"schema": "autojs6-phase13-dependency-compatibility-corpus-v1"'),
      token(files.realNpmManifest, '"reasonKind": "missing_builtin"'),
      token(files.realNpmManifest, '"reasonKind": "native_addon"'),
      token(files.realNpmManifest, '"reasonKind": "postinstall"'),
      token(files.realNpmManifest, '"reasonKind": "network"'),
      token(files.realNpmManifest, '"reasonKind": "fs"'),
      token(files.realNpmManifest, '"packages"'),
      token(files.realNpmManifest, '"reason"'),
      token(files.realNpmManifest, '"fixtureCount": 53'),
      token(files.realNpmManifest, '"targetedFixtureDeltas"'),
      token(files.realNpmManifest, '"area": "stdlib"'),
      token(files.realNpmManifest, '"stdlib-digest-stream-package"'),
      token(files.realNpmManifest, '"perf-async-hooks-package"'),
      token(files.realNpmManifest, '"url-buffer-codec-package"'),
      token(files.realNpmManifest, '"passDelta": 3'),
    ]),
    scenario("instrumentation_and_differential", "Instrumentation and differential reports keep the taxonomy measurable", [
      token(files.realNpmTest, "assertPhase13CompatibilityMetadata"),
      token(files.realNpmTest, "PHASE13_REQUIRED_REASON_KINDS"),
      token(files.realNpmTest, "PHASE13_TARGETED_DELTA_AREAS"),
      token(files.realNpmTest, "fixtureMetadataIsCompleteAndSafe"),
      token(files.realNpmTest, "EXPECTED_FIXTURE_COUNT = 53"),
      token(files.differentialRunner, "phase13Compatibility: summarizePhase13CompatibilityCategories"),
      token(files.differentialRunner, "summarizePhase13CompatibilityCategories"),
      token(files.differentialRunner, "readAutoJsConnectedResults"),
      token(files.differentialRunner, "--autojs-connected-results <dir|file>"),
      token(files.differentialRunner, "Phase 13 Dependency Compatibility Categories"),
      token(files.differentialRunner, "renderPassRate"),
    ], [
      "CI still needs to schedule connected AutoJs6 real npm corpus artifacts before the host differential report.",
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-47", [
      token(files.phase13Tasks, "P13-47 partial landing"),
      token(files.p13Report, "P13-47 records dependency compatibility corpus as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13DependencyCompatibilityCorpus"),
      token(files.testingDoc, "verifyNodePhase13DependencyCompatibilityCorpus"),
      token(files.ciGatesDoc, "verifyNodePhase13DependencyCompatibilityCorpus"),
      token(files.compatibilityProfile, "P13-47 records dependency compatibility corpus as partial"),
      token(files.securityModel, "P13-47 records dependency compatibility corpus as partial"),
      token(files.proComparison, "P13-47 records dependency compatibility corpus as partial"),
      token(files.proAutoxNotes, "P13-47 records dependency compatibility corpus as partial"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-47 dependency compatibility corpus"),
      token(files.phase13Summary, "P13-47 records dependency compatibility corpus as partial with 53 fixed offline fixtures"),
      token(files.phase13Summary, "stdlib targetedFixtureDeltas +3"),
      token(files.phase13Summary, "differential pass-rate rows"),
    ]),
  ];

  const categoryProblems = [];
  for (const kind of REQUIRED_REASON_KINDS) {
    if (!reasonKinds.has(kind)) {
      categoryProblems.push(`missing required reason kind: ${kind}`);
    }
  }
  for (const category of categoryRows) {
    if (!category.id || !category.title || !category.reasonKind) {
      categoryProblems.push(`category has missing id/title/reasonKind: ${JSON.stringify(category)}`);
    }
    if (category.fixtures.length === 0) {
      categoryProblems.push(`category ${category.id} has no fixtures`);
    }
    for (const fixture of category.fixtures) {
      if (!fixtureNames.has(fixture)) {
        categoryProblems.push(`category ${category.id} references unknown fixture: ${fixture}`);
      }
    }
    if (category.packages.length === 0) {
      categoryProblems.push(`category ${category.id} has no package reason rows`);
    }
    for (const row of category.packages) {
      if (!row.name || !row.reason) {
        categoryProblems.push(`category ${category.id} has incomplete package reason row`);
      }
    }
  }
  const deltaProblems = [];
  const stdlibDelta = targetedDeltaRows.find((delta) => delta.area === "stdlib");
  if (!stdlibDelta) {
    deltaProblems.push("missing targeted fixture delta area: stdlib");
  } else {
    const requiredStdlibFixtures = ["stdlib-digest-stream-package", "perf-async-hooks-package", "url-buffer-codec-package"];
    if (stdlibDelta.passDelta !== 3) {
      deltaProblems.push("stdlib targeted fixture delta must record passDelta 3");
    }
    for (const fixture of requiredStdlibFixtures) {
      if (!stdlibDelta.fixtures.includes(fixture)) {
        deltaProblems.push(`stdlib targeted fixture delta missing fixture: ${fixture}`);
      }
    }
  }
  for (const delta of targetedDeltaRows) {
    if (!delta.area || !delta.notes || !Number.isFinite(delta.passDelta) || delta.passDelta < 0) {
      deltaProblems.push(`targeted fixture delta is incomplete: ${JSON.stringify(delta)}`);
    }
    for (const fixture of delta.fixtures) {
      if (!fixtureNames.has(fixture)) {
        deltaProblems.push(`targeted fixture delta ${delta.area} references unknown fixture: ${fixture}`);
      }
    }
  }

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-47"), "P13-47 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-47 report must exist."),
    check("manifest-parseable", Boolean(manifest), "real npm corpus manifest must parse as JSON."),
    check("fixture-count", fixtureNames.size === 53 && compatibility.fixtureCount === 53, "real npm corpus must keep the 53-fixture baseline."),
    check("required-reason-kinds", categoryProblems.length === 0, "phase13Compatibility must cover required package reason categories."),
    check("targeted-deltas", deltaProblems.length === 0, "phase13Compatibility must record targeted fixture deltas."),
    check("surface-present", Boolean(surfaceEntry), "`packaging.plugins_distribution` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`packaging.plugins_distribution` surface must reference P13-47 evidence.",
    ),
    check(
      "no-unsafe-corpus-content",
      !files.realNpmManifest.text.includes(".node\"") &&
        !files.realNpmManifest.text.includes('"postinstall":') &&
        files.realNpmTest.text.includes("native addon must not be vendored") &&
        files.realNpmTest.text.includes("package install script must not be present"),
      "P13-47 must not add unsafe native addon or lifecycle script payloads to the fixed corpus.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message).concat(categoryProblems).concat(deltaProblems);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-dependency-compatibility-corpus-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13DependencyCompatibilityCorpus",
    task: "P13-47",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "packaging.plugins_distribution",
      status: "missing",
      evidence: [],
    },
    fixtureCount: fixtureNames.size,
    requiredReasonKinds: REQUIRED_REASON_KINDS,
    targetedDeltas: targetedDeltaRows,
    categories: categoryRows,
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      taxonomyReady: scenarioById(scenarios, "manifest_taxonomy").status === "ready",
      targetedDeltasReady: deltaProblems.length === 0,
      passRateDeltasInSummary: scenarioById(scenarios, "docs_gate_and_surface").status === "ready",
      differentialCategoryReportingReady: scenarioById(scenarios, "instrumentation_and_differential").status !== "missing_evidence",
      liveRegistryDriftInDefaultCi: false,
      unsafePackageExecutionPromoted: false,
      registryDriftTestingPromoted: false,
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
  return scenarios.find((entry) => entry.id === id);
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Dependency Compatibility Corpus",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Fixtures: ${report.fixtureCount}`,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Categories",
    "",
    "| Category | Reason kind | Fixtures | Package reason rows |",
    "| --- | --- | --- | --- |",
  ];
  for (const category of report.categories) {
    lines.push([
      category.title || category.id,
      category.reasonKind,
      category.fixtures.join(", "),
      category.packages.map((row) => `${row.name}: ${row.reason}`).join("; "),
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  }
  lines.push("", "## Targeted Fixture Deltas", "");
  lines.push("| Area | Fixtures | Pass delta | Notes |");
  lines.push("| --- | --- | --- | --- |");
  for (const delta of report.targetedDeltas) {
    lines.push([
      delta.area,
      delta.fixtures.join(", "),
      delta.passDelta,
      delta.notes,
    ].map(markdownCell).join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  }
  lines.push("", "## Scenarios", "");
  for (const scenarioItem of report.scenarios) {
    lines.push(`- \`${scenarioItem.id}\`: \`${scenarioItem.status}\` - ${scenarioItem.title}`);
    for (const remaining of scenarioItem.remaining) {
      lines.push(`  - ${remaining}`);
    }
  }
  lines.push("", "## Checks", "");
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("");
  return lines.join("\n");
}

function markdownCell(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
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
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[index + 1];
      index += 1;
    } else if (arg === "--report-dir") {
      options.reportDir = args[index + 1];
      index += 1;
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-dependency-compatibility-corpus.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
