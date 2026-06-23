#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-examples-verification";
const EXAMPLES_DIR = "docs/nodejs/examples/rhino-compat";
const REQUIRED_IDS = Object.freeze([
  "toast",
  "files",
  "storage",
  "selector",
  "image",
  "ocr",
  "app-device",
  "shell",
]);
const REQUIRED_COVERAGE = Object.freeze([
  "toast",
  "files",
  "storage",
  "selector",
  "image",
  "ocr",
  "app",
  "device",
  "shell",
]);
const NODE_EXPECTATIONS = Object.freeze({
  toast: ["compat.toast(", "compat.toastLog("],
  files: ["compat.files.write(", "compat.files.read(", "compat.files.remove("],
  storage: ["compat.storages.create(", "store.put(", "store.get(", "store.remove("],
  selector: ["compat.text(", ".findOne(", ".click("],
  image: ["compat.images.read(", "compat.images.clip(", "compat.images.findImage(", "compat.images.recycle("],
  ocr: ["compat.requestScreenCapture(", "compat.captureScreen(", "compat.ocr.recognizeText(", "compat.images.recycle("],
  "app-device": ["compat.app.launchPackage(", "compat.device.isScreenOn(", "compat.device.vibrate("],
  shell: ["compat.shell(", "compat.shell.checkAccess("],
});
const RHINO_EXPECTATIONS = Object.freeze({
  toast: ["toast(", "toastLog("],
  files: ["files.write(", "files.read(", "files.remove("],
  storage: ["storages.create(", "store.put(", "store.get(", "store.remove("],
  selector: ["text(", ".findOne(", ".click("],
  image: ["images.read(", "images.clip(", "images.findImage(", "images.recycle("],
  ocr: ["requestScreenCapture(", "captureScreen(", "ocr.recognizeText(", "images.recycle("],
  "app-device": ["app.launchPackage(", "device.isScreenOn(", "device.vibrate("],
  shell: ["shell("],
});

const FILES = {
  readme: `${EXAMPLES_DIR}/README.md`,
  manifest: `${EXAMPLES_DIR}/manifest.json`,
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const examplesRoot = path.join(repoRoot, EXAMPLES_DIR);
  const manifest = JSON.parse(files.manifest.text);
  const checks = buildChecks(files, manifest, examplesRoot);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const rows = Array.isArray(manifest.examples)
    ? manifest.examples.map((entry) => ({
      id: entry.id,
      rhino: entry.rhino,
      node: entry.node,
      coverage: entry.coverage,
      status: entry.status,
    }))
    : [];
  const report = {
    schema: "autojs6-node-rhino-compat-examples-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatExamples",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    exampleCount: rows.length,
    examples: rows,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      requiredCoveragePresent: checkById(checks, "manifest-required-coverage"),
      snippetsCompile: checkById(checks, "syntax-all-snippets"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat examples verification: " + report.decision.status);
  console.log("Rhino compat examples: " + rows.map((row) => row.id).join(", "));
  console.log("Rhino compat example policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files, manifest, examplesRoot) {
  const checks = [];
  const examples = Array.isArray(manifest.examples) ? manifest.examples : [];
  const ids = examples.map((entry) => entry.id);
  const duplicateIds = ids.filter((id, index) => ids.indexOf(id) !== index);
  const coverage = new Set(examples.flatMap((entry) => Array.isArray(entry.coverage) ? entry.coverage : []));
  const exampleFiles = [];

  checks.push(check(
    "manifest-schema",
    manifest.schema === "autojs6-rhino-compat-examples-v1" && manifest.task === "S11-27" && examples.length === REQUIRED_IDS.length,
    "manifest must use the S11-27 Rhino compat examples schema and define the expected number of examples",
  ));
  checks.push(check(
    "manifest-required-ids",
    REQUIRED_IDS.every((id) => ids.includes(id)) && duplicateIds.length === 0,
    "manifest must include each required example id exactly once",
  ));
  checks.push(check(
    "manifest-required-coverage",
    REQUIRED_COVERAGE.every((name) => coverage.has(name)),
    "manifest coverage must include toast, files, storage, selector, image, ocr, app, device, and shell",
  ));
  checks.push(check(
    "readme-core",
    includesAll(files.readme.text, [
      "S11-27",
      "manifest.json",
      "verifyNodeRhinoCompatExamples",
      "*.rhino.js",
      "*.node.cjs",
      "node --check",
      "toast",
      "files",
      "storage",
      "selector",
      "image",
      "ocr",
      "app-device",
      "shell",
    ]),
    "README must describe the S11-27 Rhino compat example suite, manifest, coverage, and gate",
  ));

  for (const entry of examples) {
    const id = String(entry.id || "");
    const rhinoRelative = String(entry.rhino || "");
    const nodeRelative = String(entry.node || "");
    const rhinoFile = path.join(examplesRoot, rhinoRelative);
    const nodeFile = path.join(examplesRoot, nodeRelative);
    const rhinoText = fs.existsSync(rhinoFile) ? read(rhinoFile) : "";
    const nodeText = fs.existsSync(nodeFile) ? read(nodeFile) : "";
    exampleFiles.push({ id, kind: "rhino", file: rhinoFile, relative: `${EXAMPLES_DIR}/${rhinoRelative}`, text: rhinoText });
    exampleFiles.push({ id, kind: "node", file: nodeFile, relative: `${EXAMPLES_DIR}/${nodeRelative}`, text: nodeText });

    checks.push(check(
      `example-${id}-files`,
      id &&
        entry.status === "static_verified" &&
        rhinoRelative.endsWith(".rhino.js") &&
        nodeRelative.endsWith(".node.cjs") &&
        fs.existsSync(rhinoFile) &&
        fs.existsSync(nodeFile),
      `example '${id}' must have static_verified Rhino and Node snippet files`,
    ));
    checks.push(check(
      `example-${id}-node-shape`,
      nodeText.startsWith("\"nodejs\";") &&
        nodeText.includes("require(\"autojs6:compat\")") &&
        nodeText.includes("async function main()") &&
        nodeText.includes("main().catch") &&
        includesAll(nodeText, NODE_EXPECTATIONS[id] || []),
      `example '${id}' Node snippet must use the expected autojs6:compat APIs and async error handling`,
    ));
    checks.push(check(
      `example-${id}-rhino-shape`,
      !rhinoText.includes("\"nodejs\"") &&
        includesAll(rhinoText, RHINO_EXPECTATIONS[id] || []),
      `example '${id}' Rhino snippet must show the expected legacy API calls`,
    ));
  }

  const syntaxResults = exampleFiles.map((item) => ({
    item,
    result: checkSyntax(item.file),
  }));
  syntaxResults.forEach(({ item, result }) => {
    checks.push(check(
      `syntax-${item.id}-${item.kind}`,
      result.ok,
      `${item.relative} must pass node --check${result.ok ? "" : ": " + result.message}`,
    ));
  });
  checks.push(check(
    "syntax-all-snippets",
    syntaxResults.every(({ result }) => result.ok),
    "all Rhino and Node migration snippets must pass node --check static compilation",
  ));
  checks.push(check(
    "migration-guide-s11-27",
    includesAll(files.migration.text, [
      "docs/nodejs/examples/rhino-compat",
      "verifyNodeRhinoCompatExamples",
      "toast/files/storage/selector/image/ocr/app/device/shell",
    ]),
    "migration guide must link the S11-27 Rhino compat example suite and gate",
  ));
  checks.push(check(
    "examples-doc-s11-27",
    includesAll(files.examplesDoc.text, [
      "Rhino Compat Migration Examples",
      "docs/nodejs/examples/rhino-compat",
      "verifyNodeRhinoCompatExamples",
      "toast/files/storage/selector/image/ocr/app/device/shell",
    ]),
    "EXAMPLES.md must document the S11-27 Rhino compat migration examples",
  ));
  checks.push(check(
    "task-plan-s11-27-complete",
    /\[x\]\s+S11-27/.test(files.taskPlan.text) &&
      files.taskPlan.text.includes("docs/nodejs/examples/rhino-compat/") &&
      files.taskPlan.text.includes("verifyNodeRhinoCompatExamples"),
    "Phase 11 task plan must mark S11-27 complete and name the example gate",
  ));
  checks.push(check(
    "gradle-task",
    files.gradle.text.includes("verifyNodeRhinoCompatExamples") &&
      files.gradle.text.includes("verify-rhino-compat-examples.js") &&
      files.gradle.text.includes("docs/nodejs/examples/rhino-compat"),
    "Gradle must register verifyNodeRhinoCompatExamples with the example directory as an input",
  ));
  return checks;
}

function checkSyntax(file) {
  if (!fs.existsSync(file)) {
    return { ok: false, message: "missing file" };
  }
  const result = childProcess.spawnSync(process.execPath, ["--check", file], {
    encoding: "utf8",
    windowsHide: true,
  });
  return {
    ok: result.status === 0,
    message: String(result.stderr || result.stdout || "").trim(),
  };
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Examples Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Examples: ${report.exampleCount}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Examples",
    "",
    "| ID | Rhino | Node | Coverage | Status |",
    "| --- | --- | --- | --- | --- |",
  ];
  report.examples.forEach((example) => {
    lines.push(`| \`${example.id}\` | \`${example.rhino}\` | \`${example.node}\` | ${escapeTable((example.coverage || []).join(", "))} | \`${example.status}\` |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((check) => {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${escapeTable(check.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-examples.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
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

main();
