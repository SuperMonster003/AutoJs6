#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-desktop-parity-examples";
const EXAMPLE_NAME = "desktop-parity-suite";
const REQUIRED_SMOKE_EXAMPLES = Object.freeze(["esm-loader", "npm-dependency"]);
const REQUIRED_EXAMPLES = Object.freeze([
  ["esm-loader", ["esm_loader"]],
  ["worker-threads", ["worker_threads"]],
  ["http-https", ["network", "http", "https"]],
  ["raw-network", ["raw_network"]],
  ["fs-advanced", ["fs.advanced", "scoped_fs"]],
  ["wasi", ["wasi"]],
  ["inspector", ["inspector"]],
  ["npm-dependency", ["package_manager", "pure_js_npm"]],
]);

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_54_DESKTOP_PARITY_EXAMPLES.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  rootExamplesManifest: "sample/nodejs/examples.json",
  exampleReadme: "sample/nodejs/desktop-parity-suite/README.md",
  exampleProject: "sample/nodejs/desktop-parity-suite/project.json",
  examplePackage: "sample/nodejs/desktop-parity-suite/package.json",
  exampleManifest: "sample/nodejs/desktop-parity-suite/examples.json",
  exampleMain: "sample/nodejs/desktop-parity-suite/main.cjs",
  exampleExpectedOutput: "sample/nodejs/desktop-parity-suite/expected-output.txt",
  exampleSmokeMain: "sample/nodejs/desktop-parity-suite/smoke.cjs",
  exampleSmokeExpectedOutput: "sample/nodejs/desktop-parity-suite/smoke-output.txt",
  safeDenials: "sample/nodejs/desktop-parity-suite/snippets/safe-denials.cjs",
  nodeExampleInstrumentationTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeExampleProjectInstrumentationTest.kt",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(repoRoot, files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 desktop parity examples: " + report.decision.status);
  console.log("Phase 13 desktop parity example blockers: " + report.blockers.length);
  console.log("Phase 13 desktop parity example policy violations: " + report.policyViolations.length);
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

function buildReport(repoRoot, files) {
  const rootManifest = parseJson(files.rootExamplesManifest);
  const exampleManifest = parseJson(files.exampleManifest);
  const smokeSubset = exampleManifest.smokeSubset || {};
  const project = parseJson(files.exampleProject);
  const packageJson = parseJson(files.examplePackage);
  const surfaceEntry = surface(files.surfaceManifest.text, "docs.examples.final_gate");
  const entries = Array.isArray(exampleManifest.examples) ? exampleManifest.examples : [];
  const entryById = new Map(entries.map((entry) => [entry.id, entry]));
  const requiredIds = REQUIRED_EXAMPLES.map(([id]) => id);
  const smokeSubsetExamples = list(smokeSubset.examples);
  const projectPermissions = list(project.node && project.node.permissions);
  const snippetScenarios = REQUIRED_EXAMPLES.map(([id, capabilities]) => {
    const entry = entryById.get(id);
    const snippetFile = entry && typeof entry.file === "string"
      ? path.join(repoRoot, "sample/nodejs/desktop-parity-suite", entry.file)
      : "";
    const snippet = snippetFile ? read(snippetFile) : "";
    const expectedOutput = `sample.${EXAMPLE_NAME}.${id}=PASS`;
    const safeExpectedOutput = `sample.${EXAMPLE_NAME}.safe-denial.${id}=DENIED`;
    const safeProfile = entry && entry.safeProfile || {};
    const checks = [
      token(files.exampleManifest, `"id": "${id}"`),
      token(files.exampleReadme, entry ? entry.file : `snippets/${id}.cjs`),
      token(files.exampleExpectedOutput, expectedOutput),
      token(files.exampleExpectedOutput, safeExpectedOutput),
      token(files.safeDenials, safeExpectedOutput),
      { file: entry ? path.relative(repoRoot, snippetFile).replace(/\\/g, "/") : "missing", token: '"nodejs";', present: snippet.trimStart().startsWith('"nodejs";') },
      { file: entry ? path.relative(repoRoot, snippetFile).replace(/\\/g, "/") : "missing", token: expectedOutput, present: snippet.includes(expectedOutput) },
      { file: entry ? path.relative(repoRoot, snippetFile).replace(/\\/g, "/") : "missing", token: ".skipped=", present: snippet.includes(".skipped=") },
      ...capabilities.map((capability) => token(files.exampleProject, `"${capability}"`)),
      {
        file: files.exampleManifest.relative,
        token: "safeProfile.profile=safe_default",
        present: safeProfile.profile === "safe_default" && safeProfile.expectedOutput === safeExpectedOutput,
      },
    ];
    const missing = checks.filter((checkItem) => !checkItem.present).map((checkItem) => `missing evidence token: ${checkItem.token}`);
    return {
      id,
      title: entry && entry.title ? entry.title : id,
      status: missing.length === 0 ? "ready" : "missing_evidence",
      targetProfile: entry && entry.targetProfile || "",
      requiredCapabilities: capabilities,
      expectedOutput,
      safeProfile,
      packagedBehavior: entry && entry.packagedBehavior || "",
      skipReason: entry && entry.skipReason || "",
      checks,
      remaining: missing,
    };
  });
  const smokeSubsetReady =
    smokeSubset.entry === "smoke.cjs" &&
    smokeSubset.expectedOutput === "smoke-output.txt" &&
    smokeSubset.profile === "safe_default" &&
    REQUIRED_SMOKE_EXAMPLES.every((id) => smokeSubsetExamples.includes(id)) &&
    smokeSubsetExamples.length === REQUIRED_SMOKE_EXAMPLES.length &&
    files.exampleSmokeMain.text.trimStart().startsWith('"nodejs";') &&
    files.exampleSmokeMain.text.includes(`sample.${EXAMPLE_NAME}.smoke.catalog=${REQUIRED_SMOKE_EXAMPLES.length}`) &&
    REQUIRED_SMOKE_EXAMPLES.every((id) =>
      files.exampleSmokeMain.text.includes(`sample.${EXAMPLE_NAME}.${id}=PASS`) &&
      files.exampleSmokeExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.${id}=PASS`)) &&
    files.exampleSmokeMain.text.includes(`sample.${EXAMPLE_NAME}.smoke=PASS`) &&
    files.exampleSmokeExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.smoke=PASS`) &&
    files.nodeExampleInstrumentationTest.text.includes("desktopParitySafeSubsetExamplesRun") &&
    files.nodeExampleInstrumentationTest.text.includes('runExample("desktop-parity-suite", "smoke.cjs")');

  const checks = [
    check("required-files", Object.values(files).every((file) => file.exists), "All P13-54 source, doc, and gate files must exist."),
    check(
      "root-example-manifest",
      Array.isArray(rootManifest.examples) &&
        rootManifest.examples.some((entry) =>
          entry.name === EXAMPLE_NAME &&
          entry.status === "partial" &&
          list(entry.tags).includes("phase13") &&
          list(entry.tags).includes("desktop-parity") &&
          entry.mainAppSmoke === false &&
          entry.packagedCompatible === false),
      "`sample/nodejs/examples.json` must register desktop-parity-suite as a non-smoke partial Phase 13 example.",
    ),
    check(
      "example-schema",
      exampleManifest.schema === "autojs6-node-desktop-parity-examples-v1" &&
        exampleManifest.phase === "P13-54" &&
        exampleManifest.status === "partial" &&
        entries.length === REQUIRED_EXAMPLES.length,
      "`desktop-parity-suite/examples.json` must use the P13-54 desktop parity example schema and contain the required entries.",
    ),
    check(
      "required-entry-ids",
      requiredIds.every((id) => entryById.has(id)) &&
        new Set(entries.map((entry) => entry.id)).size === entries.length,
      "The desktop parity catalog must include each required example id exactly once.",
    ),
    check(
      "entry-metadata",
      entries.every((entry) =>
        typeof entry.file === "string" &&
        Array.isArray(entry.requiredCapabilities) &&
        entry.requiredCapabilities.length > 0 &&
        entry.targetProfile === "desktop_compat_opt_in" &&
        typeof entry.expectedOutput === "string" &&
        entry.expectedOutput.endsWith("=PASS") &&
        entry.safeProfile &&
        entry.safeProfile.profile === "safe_default" &&
        typeof entry.safeProfile.expectedOutput === "string" &&
        entry.safeProfile.expectedOutput.endsWith("=DENIED") &&
        typeof entry.safeProfile.behavior === "string" &&
        entry.safeProfile.behavior.length >= 24 &&
        typeof entry.packagedBehavior === "string" &&
        entry.packagedBehavior.length >= 24 &&
        typeof entry.skipReason === "string" &&
        entry.skipReason.length >= 16),
      "Each desktop parity example must declare target profile, required capabilities, expected output, Safe denial behavior, packaged behavior, and skip reason.",
    ),
    check(
      "project-metadata",
      project.name === EXAMPLE_NAME &&
        project.type === "node" &&
        project.main === "main.cjs" &&
        project.node &&
        project.node.profile === "desktop_compat_opt_in" &&
        REQUIRED_EXAMPLES.flatMap(([, capabilities]) => capabilities).every((capability) => projectPermissions.includes(capability)) &&
        project.example &&
        typeof project.example.packagedSupport === "string" &&
        project.example.packagedSupport.includes("metadata-only"),
      "`project.json` must describe the Desktop opt-in profile, required permissions, and metadata-only packaged behavior.",
    ),
    check(
      "package-json",
      packageJson.name === "@autojs6-sample/desktop-parity-suite" &&
        packageJson.type === "commonjs" &&
        packageJson.main === "main.cjs",
      "`package.json` must keep the standard sample scope and CommonJS entry.",
    ),
    check(
      "main-catalog-runner",
      files.exampleMain.text.trimStart().startsWith('"nodejs";') &&
        files.exampleMain.text.includes("examples.json") &&
        files.exampleMain.text.includes("entry.expectedOutput") &&
        files.exampleMain.text.includes("entry.safeProfile.expectedOutput") &&
        files.exampleMain.text.includes(`sample.${EXAMPLE_NAME}=PASS`),
      "`main.cjs` must be a Node catalog runner that prints desktop expected markers and Safe denial markers.",
    ),
    check(
      "expected-output",
      files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.catalog=${REQUIRED_EXAMPLES.length}`) &&
        files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}=PASS`) &&
        requiredIds.every((id) =>
          files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.${id}=PASS`) &&
          files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.safe-denial.${id}=DENIED`)),
      "`expected-output.txt` must list the catalog count, each desktop marker, each Safe denial marker, and final PASS marker.",
    ),
    check(
      "smoke-subset",
      smokeSubsetReady,
      "`desktop-parity-suite` must include a focused runtime smoke subset for already-promoted desktop-safe examples.",
    ),
    check(
      "example-policy-links",
      entries.every((entry) =>
        entry.targetProfile === "desktop_compat_opt_in" &&
        Array.isArray(entry.requiredCapabilities) &&
        entry.requiredCapabilities.length > 0 &&
        entry.safeProfile &&
        entry.safeProfile.profile === "safe_default" &&
        typeof entry.packagedBehavior === "string" &&
        entry.packagedBehavior.length >= 24) &&
        files.exampleReadme.text.includes("Package integrity and rollback guidance") &&
        includesAll(files.p13Report.text, ["profile metadata", "capability requirements", "integrity policy", "rollback guidance"]),
      "Every desktop parity example must link profile metadata, capability requirements, integrity policy, and rollback guidance.",
    ),
    check(
      "non-promoted-snippets-static",
      exampleManifest.status === "partial" &&
        files.exampleMain.text.includes("entry.expectedOutput") &&
        files.exampleMain.text.includes("entry.safeProfile.expectedOutput") &&
        rootManifest.examples.some((entry) => entry.name === EXAMPLE_NAME && entry.mainAppSmoke === false) &&
        smokeSubset.profile === "safe_default" &&
        REQUIRED_SMOKE_EXAMPLES.every((id) => smokeSubsetExamples.includes(id)) &&
        !smokeSubsetExamples.includes("worker-threads") &&
        !smokeSubsetExamples.includes("http-https") &&
        !smokeSubsetExamples.includes("raw-network") &&
        !smokeSubsetExamples.includes("fs-advanced") &&
        !smokeSubsetExamples.includes("wasi") &&
        !smokeSubsetExamples.includes("inspector"),
      "Non-promoted desktop snippets must remain static/catalog-backed and clearly marked outside the Safe smoke subset.",
    ),
    check(
      "readme-acceptance",
      files.exampleReadme.text.includes("Required profile") &&
        files.exampleReadme.text.includes("Safe profile behavior") &&
        files.exampleReadme.text.includes("Packaged behavior") &&
        files.exampleReadme.text.includes("Skip reason") &&
        includesAll(files.exampleReadme.text, ["ESM", "worker_threads", "http/https", "raw network", "advanced fs", "WASI", "inspector", "npm dependency"]) &&
        requiredIds.every((id) => files.exampleReadme.text.includes(`snippets/${id}.cjs`)),
      "`README.md` must document target profile, Safe denial behavior, packaged behavior, and unavailable/skip conditions for every snippet.",
    ),
    check(
      "snippet-scenarios",
      snippetScenarios.every((scenario) => scenario.status === "ready"),
      "Every snippet must have a node directive, PASS marker, skip path, README entry, project permission, and Safe denial marker.",
    ),
    check(
      "gradle-gates",
      files.nodeExamplesGradle.text.includes('"desktop-parity"') &&
        files.phase13GradleGate.text.includes("verifyNodePhase13DesktopParityExamples") &&
        files.phase13GradleGate.text.includes("verify-desktop-parity-examples.js") &&
        files.phase13GradleGate.text.includes("verifyNodePhase13ProParityExamples") &&
        files.phase13GradleGate.text.includes("verifyNodeExamples"),
      "Gradle must recognize desktop parity tags and register the P13-54 verifier after P13-53 and `verifyNodeExamples`.",
    ),
    check(
      "docs-and-surface-evidence",
      includesAll(files.phase13Tasks.text, ["P13-54 partial landing", "safe subset smoke", "verifyNodePhase13DesktopParityExamples"]) &&
        includesAll(files.p13Report.text, ["P13-54 records the v1.1 desktop Node parity example catalog as ready", "focused runtime smoke subset", REPORT_BASENAME]) &&
        includesAll(files.examplesDoc.text, [EXAMPLE_NAME, "Phase 13 desktop Node parity examples"]) &&
        includesAll(files.testingDoc.text, ["verifyNodePhase13DesktopParityExamples", "desktopParitySafeSubsetExamplesRun"]) &&
        includesAll(files.ciGatesDoc.text, ["verifyNodePhase13DesktopParityExamples", "desktopParitySafeSubsetExamplesRun"]) &&
        files.proComparison.text.includes("P13-54 records desktop Node parity examples as partial") &&
        files.proAutoxNotes.text.includes("P13-54 records desktop Node parity examples as partial") &&
        includesAll(files.capabilityMatrix.text, ["P13-54 desktop Node parity examples", "safe subset smoke"]) &&
        files.surfaceManifest.text.includes(REPORT_BASENAME) &&
        files.surfaceManifestDoc.text.includes(REPORT_BASENAME),
      "Docs, Pro/AutoX comparison, release matrix, and surface manifest must record the P13-54 example gate.",
    ),
    check(
      "surface-entry",
      Boolean(surfaceEntry) &&
        surfaceEntry.status === "planned" &&
        Array.isArray(surfaceEntry.tasks) &&
        surfaceEntry.tasks.includes("P13-54") &&
        Array.isArray(surfaceEntry.evidence) &&
        surfaceEntry.evidence.some((entry) => entry.includes(REPORT_BASENAME)) &&
        surfaceEntry.evidence.some((entry) => entry.includes("sample/nodejs/desktop-parity-suite")),
      "`docs.examples.final_gate` must keep P13-54 mapped and reference desktop parity example evidence.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = snippetScenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-desktop-parity-examples-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13DesktopParityExamples",
    task: "P13-54",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "docs.examples.final_gate",
      status: "missing",
      evidence: [],
    },
    examples: snippetScenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      catalogReady: snippetScenarios.every((entry) => entry.status === "ready"),
      examplePolicyLinksReady: checkById(checks, "example-policy-links").passed,
      defaultSmokeEnabled: false,
      smokeSubsetEnabled: smokeSubsetReady,
      smokeSubsetExamples,
      highRiskSnippetsStatic: true,
      nonPromotedSnippetsStatic: checkById(checks, "non-promoted-snippets-static").passed,
      packagedRuntimePromoted: false,
      grantsAuthority: false,
      targetProfile: "desktop_compat_opt_in",
      safeProfileDenialsCovered: snippetScenarios.every((entry) => entry.safeProfile && entry.safeProfile.profile === "safe_default"),
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Desktop Parity Examples",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Examples",
    "",
  ];
  for (const example of report.examples) {
    lines.push(`- \`${example.id}\`: \`${example.status}\` - ${example.title}`);
    lines.push(`  - Target profile: \`${example.targetProfile}\``);
    lines.push(`  - Required capabilities: ${example.requiredCapabilities.map((capability) => `\`${capability}\``).join(", ")}`);
    lines.push(`  - Expected output: \`${example.expectedOutput}\``);
    lines.push(`  - Safe denial output: \`${example.safeProfile.expectedOutput}\``);
    lines.push(`  - Safe behavior: ${example.safeProfile.behavior}`);
    lines.push(`  - Packaged behavior: ${example.packagedBehavior}`);
    lines.push(`  - Skip reason: ${example.skipReason}`);
    for (const remaining of example.remaining) {
      lines.push(`  - ${remaining}`);
    }
  }
  lines.push("", "## Checks", "");
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  lines.push("", "## Smoke Subset", "");
  lines.push(`- Enabled: ${report.decision.smokeSubsetEnabled ? "yes" : "no"}`);
  lines.push(`- Examples: ${report.decision.smokeSubsetExamples.map((id) => `\`${id}\``).join(", ")}`);
  lines.push("- Entry: `sample/nodejs/desktop-parity-suite/smoke.cjs`");
  lines.push("- Expected output: `sample/nodejs/desktop-parity-suite/smoke-output.txt`");
  lines.push("- Instrumentation: `NodeExampleProjectInstrumentationTest#desktopParitySafeSubsetExamplesRun`");
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("", "## Remaining Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    for (const blocker of report.blockers) {
      lines.push(`- ${blocker}`);
    }
  }
  return lines.join("\n") + "\n";
}

function parseJson(file) {
  try {
    return file.exists ? JSON.parse(file.text) : {};
  } catch (error) {
    return { parseError: error.message };
  }
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
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

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function list(value) {
  return Array.isArray(value) ? value.map(String) : [];
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-desktop-parity-examples.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
