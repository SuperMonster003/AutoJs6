#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-dual-engine-compat-tests-verification";
const TESTS_DIR = "docs/nodejs/dual-engine-compat";
const REQUIRED_STATUSES = Object.freeze(["supported_async", "blocked_by_design"]);
const REQUIRED_CASES = Object.freeze([
  "toast-log",
  "clipboard-roundtrip",
  "sleep-yield",
  "threads-start",
  "ui-run",
  "floaty-window",
]);
const NODE_EXPECTATIONS = Object.freeze({
  "toast-log": ["compat.toastLog(", "await compat.toastLog("],
  "clipboard-roundtrip": ["compat.setClip(", "compat.getClip(", "await compat.setClip(", "await compat.getClip("],
  "sleep-yield": ["compat.sleep(", "await compat.sleep("],
  "threads-start": ["compat.blockedByDesign(", "compat.errorCodes.blockedByDesign", "\"threads.start\""],
  "ui-run": ["compat.blockedByDesign(", "compat.errorCodes.blockedByDesign", "\"ui.run\""],
  "floaty-window": ["compat.blockedByDesign(", "compat.errorCodes.blockedByDesign", "\"floaty.window\""],
});
const RHINO_EXPECTATIONS = Object.freeze({
  "toast-log": ["toastLog("],
  "clipboard-roundtrip": ["setClip(", "getClip("],
  "sleep-yield": ["sleep("],
  "threads-start": ["threads.start(", ".join("],
  "ui-run": ["ui.run("],
  "floaty-window": ["floaty.window(", ".close("],
});
const BLOCKED_RUNTIME_SURFACES = Object.freeze([
  "compat.threads",
  "compat.ui",
  "compat.floaty",
  "require(\"threads\")",
  "require(\"ui\")",
  "require(\"floaty\")",
]);

const FILES = {
  manifest: `${TESTS_DIR}/manifest.json`,
  reportDoc: "docs/nodejs/reports/phase11/RHINO_DUAL_ENGINE_COMPAT_TESTS.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  testing: "docs/nodejs/TESTING.md",
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
  const testsRoot = path.join(repoRoot, TESTS_DIR);
  const manifest = JSON.parse(files.manifest.text);
  const statusManifest = JSON.parse(files.statusManifest.text);
  const statusIndex = indexStatusManifest(statusManifest);
  const checks = buildChecks(files, manifest, statusIndex, testsRoot);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const cases = Array.isArray(manifest.cases) ? manifest.cases : [];
  const statusCounts = countByStatus(cases);
  const report = {
    schema: "autojs6-node-rhino-dual-engine-compat-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoDualEngineCompatTests",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    caseCount: cases.length,
    statusCounts,
    cases: cases.map((entry) => ({
      id: entry.id,
      status: entry.status,
      rhino: entry.rhino,
      node: entry.node,
      statusManifestIds: entry.statusManifestIds,
      expected: entry.expected,
      devicePlanMode: entry.devicePlan && entry.devicePlan.mode,
    })),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      distinguishesSupportedAsync: statusCounts.supported_async > 0,
      distinguishesBlockedByDesign: statusCounts.blocked_by_design > 0,
      snippetsCompile: checkById(checks, "syntax-all-snippets"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino dual-engine compat tests verification: " + report.decision.status);
  console.log("Rhino dual-engine compat status counts: " + JSON.stringify(statusCounts));
  console.log("Rhino dual-engine compat policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files, manifest, statusIndex, testsRoot) {
  const checks = [];
  const cases = Array.isArray(manifest.cases) ? manifest.cases : [];
  const ids = cases.map((entry) => entry.id);
  const duplicateIds = ids.filter((id, index) => ids.indexOf(id) !== index);
  const statuses = new Set(cases.map((entry) => entry.status));
  const caseFiles = [];

  checks.push(check(
    "manifest-schema",
    manifest.schema === "autojs6-rhino-dual-engine-compat-tests-v1" &&
      manifest.task === "S11-28" &&
      cases.length === REQUIRED_CASES.length,
    "manifest must use the S11-28 dual-engine compatibility schema and define the expected number of cases",
  ));
  checks.push(check(
    "manifest-required-cases",
    REQUIRED_CASES.every((id) => ids.includes(id)) && duplicateIds.length === 0,
    "manifest must include each required dual-engine case exactly once",
  ));
  checks.push(check(
    "manifest-required-statuses",
    REQUIRED_STATUSES.every((status) => statuses.has(status)),
    "manifest must include both supported_async and blocked_by_design cases",
  ));
  checks.push(check(
    "manifest-device-plan",
    manifest.deviceInstrumentation &&
      manifest.deviceInstrumentation.status === "planned" &&
      manifest.deviceInstrumentation.resultStatusField === "compatStatus" &&
      includesAll(manifest.deviceInstrumentation.requiredStatuses || [], REQUIRED_STATUSES),
    "manifest must define the device instrumentation result field and required status buckets",
  ));

  for (const entry of cases) {
    const id = String(entry.id || "");
    const status = String(entry.status || "");
    const rhinoRelative = String(entry.rhino || "");
    const nodeRelative = String(entry.node || "");
    const rhinoFile = path.join(testsRoot, rhinoRelative);
    const nodeFile = path.join(testsRoot, nodeRelative);
    const rhinoText = fs.existsSync(rhinoFile) ? read(rhinoFile) : "";
    const nodeText = fs.existsSync(nodeFile) ? read(nodeFile) : "";
    const statusManifestIds = Array.isArray(entry.statusManifestIds) ? entry.statusManifestIds : [];
    caseFiles.push({ id, kind: "rhino", file: rhinoFile, relative: `${TESTS_DIR}/${rhinoRelative}`, text: rhinoText });
    caseFiles.push({ id, kind: "node", file: nodeFile, relative: `${TESTS_DIR}/${nodeRelative}`, text: nodeText });

    checks.push(check(
      `case-${id}-files`,
      id &&
        REQUIRED_STATUSES.includes(status) &&
        rhinoRelative.endsWith(".rhino.js") &&
        nodeRelative.endsWith(".node.cjs") &&
        fs.existsSync(rhinoFile) &&
        fs.existsSync(nodeFile),
      `case '${id}' must have one Rhino snippet, one Node snippet, and a valid status`,
    ));
    checks.push(check(
      `case-${id}-status-manifest`,
      statusManifestIds.length > 0 &&
        statusManifestIds.every((apiId) => statusIndex.has(apiId) && statusIndex.get(apiId).status === status),
      `case '${id}' must reference status-manifest entries with matching status '${status}'`,
    ));
    checks.push(check(
      `case-${id}-node-shape`,
      nodeText.startsWith("\"nodejs\";") &&
        nodeText.includes("require(\"autojs6:compat\")") &&
        nodeText.includes("async function main()") &&
        nodeText.includes("main().catch") &&
        includesAll(nodeText, NODE_EXPECTATIONS[id] || []),
      `case '${id}' Node snippet must use the expected autojs6:compat shape`,
    ));
    checks.push(check(
      `case-${id}-rhino-shape`,
      !rhinoText.includes("\"nodejs\"") &&
        includesAll(rhinoText, RHINO_EXPECTATIONS[id] || []),
      `case '${id}' Rhino snippet must show the expected legacy API calls`,
    ));
    checks.push(check(
      `case-${id}-supported-async-contract`,
      status !== "supported_async" ||
        (nodeText.includes("await ") && !nodeText.includes("compat.blockedByDesign(") && entry.expected && entry.expected.node === "promise_resolves"),
      `case '${id}' supported_async Node snippet must await the compat facade and expect a resolved Promise`,
    ));
    checks.push(check(
      `case-${id}-blocked-contract`,
      status !== "blocked_by_design" ||
        (nodeText.includes("compat.blockedByDesign(") &&
          nodeText.includes("compat.errorCodes.blockedByDesign") &&
          !includesAny(nodeText, BLOCKED_RUNTIME_SURFACES) &&
          entry.expected &&
          entry.expected.node === "blocked_by_design_marker"),
      `case '${id}' blocked_by_design Node snippet must use only the marker helper and avoid exposing blocked runtime surfaces`,
    ));
    checks.push(check(
      `case-${id}-device-plan`,
      entry.devicePlan &&
        typeof entry.devicePlan.mode === "string" &&
        Array.isArray(entry.devicePlan.assertions) &&
        entry.devicePlan.assertions.length >= 2,
      `case '${id}' must include a device instrumentation plan with assertions`,
    ));
  }

  const syntaxResults = caseFiles.map((item) => ({
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
    "all Rhino and Node dual-engine snippets must pass node --check static compilation",
  ));
  checks.push(check(
    "status-counts-distinguish-outcomes",
    countByStatus(cases).supported_async > 0 && countByStatus(cases).blocked_by_design > 0,
    "verification report must distinguish supported_async and blocked_by_design case counts",
  ));
  checks.push(check(
    "report-doc-s11-28",
    includesAll(files.reportDoc.text, [
      "S11-28",
      "host tests",
      "device instrumentation tests",
      "supported_async",
      "blocked_by_design",
      "verifyNodeRhinoDualEngineCompatTests",
      "docs/nodejs/dual-engine-compat/manifest.json",
      "autojs6-node-rhino-dual-engine-compat-verification-v1",
    ]),
    "S11-28 report must describe host tests, device instrumentation tests, statuses, schema, and gate",
  ));
  checks.push(check(
    "migration-guide-s11-28",
    includesAll(files.migration.text, [
      "docs/nodejs/dual-engine-compat",
      "verifyNodeRhinoDualEngineCompatTests",
      "supported_async",
      "blocked_by_design",
    ]),
    "migration guide must link the S11-28 dual-engine compatibility fixtures and gate",
  ));
  checks.push(check(
    "testing-doc-s11-28",
    includesAll(files.testing.text, [
      "verifyNodeRhinoDualEngineCompatTests",
      "RHINO_DUAL_ENGINE_COMPAT_TESTS.md",
      "supported_async",
      "blocked_by_design",
    ]),
    "TESTING.md must document the S11-28 dual-engine compatibility gate",
  ));
  checks.push(check(
    "task-plan-s11-28-complete",
    /\[x\]\s+S11-28/.test(files.taskPlan.text) &&
      files.taskPlan.text.includes("docs/nodejs/dual-engine-compat/") &&
      files.taskPlan.text.includes("verifyNodeRhinoDualEngineCompatTests"),
    "Phase 11 task plan must mark S11-28 complete and name the dual-engine gate",
  ));
  checks.push(check(
    "gradle-task",
    files.gradle.text.includes("verifyNodeRhinoDualEngineCompatTests") &&
      files.gradle.text.includes("verify-rhino-dual-engine-compat-tests.js") &&
      files.gradle.text.includes("docs/nodejs/dual-engine-compat"),
    "Gradle must register verifyNodeRhinoDualEngineCompatTests with the fixture directory as an input",
  ));
  return checks;
}

function indexStatusManifest(statusManifest) {
  const index = new Map();
  const modules = Array.isArray(statusManifest.modules) ? statusManifest.modules : [];
  for (const moduleEntry of modules) {
    const entries = Array.isArray(moduleEntry.apiEntries) ? moduleEntry.apiEntries : [];
    for (const apiEntry of entries) {
      if (apiEntry && typeof apiEntry.id === "string") {
        index.set(apiEntry.id, apiEntry);
      }
    }
  }
  return index;
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

function countByStatus(cases) {
  return cases.reduce((counts, entry) => {
    const status = entry && entry.status;
    if (REQUIRED_STATUSES.includes(status)) {
      counts[status] += 1;
    }
    return counts;
  }, { supported_async: 0, blocked_by_design: 0 });
}

function includesAll(textOrArray, values) {
  if (Array.isArray(textOrArray)) {
    return values.every((value) => textOrArray.includes(value));
  }
  return values.every((value) => String(textOrArray).includes(value));
}

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
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
    "# Rhino Dual-Engine Compat Tests Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Cases: ${report.caseCount}`,
    `- supported_async: ${report.statusCounts.supported_async}`,
    `- blocked_by_design: ${report.statusCounts.blocked_by_design}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Cases",
    "",
    "| ID | Status | Rhino | Node | Status manifest IDs | Device plan |",
    "| --- | --- | --- | --- | --- | --- |",
  ];
  report.cases.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | \`${entry.rhino}\` | \`${entry.node}\` | ${escapeTable((entry.statusManifestIds || []).join(", "))} | \`${entry.devicePlanMode}\` |`);
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
      console.log("Usage: node verify-rhino-dual-engine-compat-tests.js [--repo-root dir] [--report-dir dir]");
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
