#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-pro-parity-examples";
const EXAMPLE_NAME = "pro-parity-suite";
const REQUIRED_EXAMPLES = Object.freeze([
  ["java-interop", ["java_interop"]],
  ["rhino-install", ["rhino", "java_interop"]],
  ["ui-layout", ["ui"]],
  ["overlay-floaty", ["ui.overlay", "ui.overlay.permission"]],
  ["tasks-work-manager", ["work_manager"]],
  ["screenshot-ocr", ["screen_capture", "image", "ocr"]],
  ["accessibility-selector", ["accessibility"]],
]);

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_53_PRO_PARITY_EXAMPLES.md",
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
  exampleReadme: "sample/nodejs/pro-parity-suite/README.md",
  exampleProject: "sample/nodejs/pro-parity-suite/project.json",
  examplePackage: "sample/nodejs/pro-parity-suite/package.json",
  exampleManifest: "sample/nodejs/pro-parity-suite/examples.json",
  exampleMain: "sample/nodejs/pro-parity-suite/main.cjs",
  exampleExpectedOutput: "sample/nodejs/pro-parity-suite/expected-output.txt",
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
  console.log("Phase 13 Pro parity examples: " + report.decision.status);
  console.log("Phase 13 Pro parity example blockers: " + report.blockers.length);
  console.log("Phase 13 Pro parity example policy violations: " + report.policyViolations.length);
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
  const project = parseJson(files.exampleProject);
  const packageJson = parseJson(files.examplePackage);
  const surfaceEntry = surface(files.surfaceManifest.text, "docs.examples.final_gate");
  const entries = Array.isArray(exampleManifest.examples) ? exampleManifest.examples : [];
  const entryById = new Map(entries.map((entry) => [entry.id, entry]));
  const requiredIds = REQUIRED_EXAMPLES.map(([id]) => id);
  const projectPermissions = list(project.node && project.node.permissions);
  const snippetScenarios = REQUIRED_EXAMPLES.map(([id, capabilities]) => {
    const entry = entryById.get(id);
    const snippetFile = entry && typeof entry.file === "string"
      ? path.join(repoRoot, "sample/nodejs/pro-parity-suite", entry.file)
      : "";
    const snippet = snippetFile ? read(snippetFile) : "";
    const expectedOutput = `sample.${EXAMPLE_NAME}.${id}=PASS`;
    const checks = [
      token(files.exampleManifest, `"id": "${id}"`),
      token(files.exampleReadme, entry ? entry.file : `snippets/${id}.cjs`),
      token(files.exampleExpectedOutput, expectedOutput),
      { file: entry ? path.relative(repoRoot, snippetFile).replace(/\\/g, "/") : "missing", token: '"nodejs";', present: snippet.trimStart().startsWith('"nodejs";') },
      { file: entry ? path.relative(repoRoot, snippetFile).replace(/\\/g, "/") : "missing", token: expectedOutput, present: snippet.includes(expectedOutput) },
      { file: entry ? path.relative(repoRoot, snippetFile).replace(/\\/g, "/") : "missing", token: ".skipped=", present: snippet.includes(".skipped=") || id === "ui-layout" },
      ...capabilities.map((capability) => token(files.exampleProject, `"${capability}"`)),
    ];
    const missing = checks.filter((checkItem) => !checkItem.present).map((checkItem) => `missing evidence token: ${checkItem.token}`);
    return {
      id,
      title: entry && entry.title ? entry.title : id,
      status: missing.length === 0 ? "ready" : "missing_evidence",
      requiredCapabilities: capabilities,
      expectedOutput,
      packagedBehavior: entry && entry.packagedBehavior || "",
      skipReason: entry && entry.skipReason || "",
      checks,
      remaining: missing,
    };
  });

  const checks = [
    check("required-files", Object.values(files).every((file) => file.exists), "All P13-53 source, doc, and gate files must exist."),
    check(
      "root-example-manifest",
      Array.isArray(rootManifest.examples) &&
        rootManifest.examples.some((entry) =>
          entry.name === EXAMPLE_NAME &&
          entry.status === "partial" &&
          list(entry.tags).includes("phase13") &&
          list(entry.tags).includes("pro-parity") &&
          entry.mainAppSmoke === false &&
          entry.packagedCompatible === false),
      "`sample/nodejs/examples.json` must register pro-parity-suite as a non-smoke partial Phase 13 example.",
    ),
    check(
      "example-schema",
      exampleManifest.schema === "autojs6-node-pro-parity-examples-v1" &&
        exampleManifest.phase === "P13-53" &&
        exampleManifest.status === "partial" &&
        entries.length === REQUIRED_EXAMPLES.length,
      "`pro-parity-suite/examples.json` must use the P13-53 Pro parity example schema and contain the required entries.",
    ),
    check(
      "required-entry-ids",
      requiredIds.every((id) => entryById.has(id)) &&
        new Set(entries.map((entry) => entry.id)).size === entries.length,
      "The Pro parity catalog must include each required example id exactly once.",
    ),
    check(
      "entry-metadata",
      entries.every((entry) =>
        typeof entry.file === "string" &&
        Array.isArray(entry.requiredCapabilities) &&
        entry.requiredCapabilities.length > 0 &&
        entry.targetProfile === "pro_compat_opt_in" &&
        typeof entry.expectedOutput === "string" &&
        entry.expectedOutput.endsWith("=PASS") &&
        typeof entry.packagedBehavior === "string" &&
        entry.packagedBehavior.length >= 24 &&
        typeof entry.skipReason === "string" &&
        entry.skipReason.length >= 16),
      "Each Pro parity example must declare required capabilities, expected output, packaged behavior, and skip reason.",
    ),
    check(
      "project-metadata",
      project.name === EXAMPLE_NAME &&
        project.type === "node" &&
        project.main === "main.cjs" &&
        project.node &&
        project.node.profile === "pro_compat_opt_in" &&
        REQUIRED_EXAMPLES.flatMap(([, capabilities]) => capabilities).every((capability) => projectPermissions.includes(capability)) &&
        project.example &&
        typeof project.example.packagedSupport === "string" &&
        project.example.packagedSupport.includes("metadata-only"),
      "`project.json` must describe the Pro opt-in profile, required permissions, and metadata-only packaged behavior.",
    ),
    check(
      "package-json",
      packageJson.name === "@autojs6-sample/pro-parity-suite" &&
        packageJson.type === "commonjs" &&
        packageJson.main === "main.cjs",
      "`package.json` must keep the standard sample scope and CommonJS entry.",
    ),
    check(
      "main-catalog-runner",
      files.exampleMain.text.trimStart().startsWith('"nodejs";') &&
        files.exampleMain.text.includes("examples.json") &&
        files.exampleMain.text.includes("entry.expectedOutput") &&
        files.exampleMain.text.includes(`sample.${EXAMPLE_NAME}=PASS`),
      "`main.cjs` must be a Node catalog runner that prints manifest-backed expected markers.",
    ),
    check(
      "expected-output",
      files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.catalog=${REQUIRED_EXAMPLES.length}`) &&
        files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}=PASS`) &&
        requiredIds.every((id) => files.exampleExpectedOutput.text.includes(`sample.${EXAMPLE_NAME}.${id}=PASS`)),
      "`expected-output.txt` must list the catalog count, each snippet marker, and final PASS marker.",
    ),
    check(
      "readme-acceptance",
      files.exampleReadme.text.includes("$autojs.java") &&
        files.exampleReadme.text.includes("rhino.install") &&
        files.exampleReadme.text.includes("Required capabilities") &&
        files.exampleReadme.text.includes("Packaged behavior") &&
        files.exampleReadme.text.includes("Skip reason") &&
        requiredIds.every((id) => files.exampleReadme.text.includes(`snippets/${id}.cjs`)),
      "`README.md` must document capabilities, packaged behavior, and unavailable/skip conditions for every snippet.",
    ),
    check(
      "snippet-scenarios",
      snippetScenarios.every((scenario) => scenario.status === "ready"),
      "Every snippet must have a node directive, PASS marker, skip path, README entry, and project permission.",
    ),
    check(
      "gradle-gates",
      files.nodeExamplesGradle.text.includes('"phase13"') &&
        files.nodeExamplesGradle.text.includes('"pro-parity"') &&
        files.phase13GradleGate.text.includes("verifyNodePhase13ProParityExamples") &&
        files.phase13GradleGate.text.includes("verify-pro-parity-examples.js") &&
        files.phase13GradleGate.text.includes("verifyNodeExamples"),
      "Gradle must recognize Phase 13 example tags and register the P13-53 verifier after `verifyNodeExamples`.",
    ),
    check(
      "docs-and-surface-evidence",
      includesAll(files.phase13Tasks.text, ["P13-53 partial landing", "verifyNodePhase13ProParityExamples"]) &&
        includesAll(files.p13Report.text, ["P13-53 records Pro parity examples as partial", REPORT_BASENAME]) &&
        includesAll(files.examplesDoc.text, [EXAMPLE_NAME, "Phase 13 Pro parity examples"]) &&
        files.testingDoc.text.includes("verifyNodePhase13ProParityExamples") &&
        files.ciGatesDoc.text.includes("verifyNodePhase13ProParityExamples") &&
        files.proComparison.text.includes("P13-53 records Pro parity examples as partial") &&
        files.proAutoxNotes.text.includes("P13-53 records Pro parity examples as partial") &&
        files.capabilityMatrix.text.includes("P13-53 Pro parity examples") &&
        files.surfaceManifest.text.includes(REPORT_BASENAME) &&
        files.surfaceManifestDoc.text.includes(REPORT_BASENAME),
      "Docs, Pro/AutoX comparison, release matrix, and surface manifest must record the P13-53 example gate.",
    ),
    check(
      "surface-entry",
      Boolean(surfaceEntry) &&
        surfaceEntry.status === "planned" &&
        Array.isArray(surfaceEntry.tasks) &&
        surfaceEntry.tasks.includes("P13-53") &&
        Array.isArray(surfaceEntry.evidence) &&
        surfaceEntry.evidence.some((entry) => entry.includes(REPORT_BASENAME)) &&
        surfaceEntry.evidence.some((entry) => entry.includes("sample/nodejs/pro-parity-suite")),
      "`docs.examples.final_gate` must keep P13-53 mapped and reference Pro parity example evidence.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = [
    ...snippetScenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`)),
    "live packaged Pro examples remain gated until reviewed prompt/disclosure UI, provider promotion, and packaged APK evidence land",
    "pro_compat_opt_in metadata remains diagnostic-only and does not grant Java/UI/overlay/accessibility/screenshot/OCR authority by itself",
    "default runtime smoke does not execute high-risk snippets; P13-53 is a static catalog and documentation gate",
  ];
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-pro-parity-examples-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ProParityExamples",
    task: "P13-53",
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
      phase13TaskComplete: false,
      finalBlockerClosable,
      catalogReady: snippetScenarios.every((entry) => entry.status === "ready"),
      defaultSmokeEnabled: false,
      packagedRuntimePromoted: false,
      grantsAuthority: false,
      targetProfile: "pro_compat_opt_in",
    },
  };
}

function parseJson(file) {
  try {
    return file.exists ? JSON.parse(file.text) : {};
  } catch (error) {
    return { parseError: error.message };
  }
}

function scenarioLine(scenario) {
  return `- \`${scenario.id}\`: \`${scenario.status}\` - ${scenario.title}`;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Pro Parity Examples",
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
    lines.push(scenarioLine(example));
    lines.push(`  - Required capabilities: ${example.requiredCapabilities.map((capability) => `\`${capability}\``).join(", ")}`);
    lines.push(`  - Expected output: \`${example.expectedOutput}\``);
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
      console.log("Usage: node verify-pro-parity-examples.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
