#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-threads-migration-cookbook-verification";
const P12_21_NOTE = "P12-21 threads migration cookbook landed";
const P12_22_NEXT = "Proceed to P12-22 compute/process-worker replacement API decision";
const P12_23_NEXT = "Proceed to P12-23 Rhino JSON-only shim POC gate";

const THREAD_CASE_IDS = Object.freeze([
  "threads-start",
  "threads-disposable",
  "threads-sync-queue",
]);

const THREAD_API_IDS = Object.freeze([
  "threads.__module",
  "threads.disposable",
  "threads.interrupt",
  "threads.pool",
  "threads.start",
  "threads.sync",
]);

const FILES = {
  cookbook: "docs/nodejs/reports/phase12/RHINO_THREADS_MIGRATION_COOKBOOK.md",
  dualEngineManifest: "docs/nodejs/dual-engine-compat/manifest.json",
  threadsStartNode: "docs/nodejs/dual-engine-compat/cases/threads-start.node.cjs",
  threadsStartRhino: "docs/nodejs/dual-engine-compat/cases/threads-start.rhino.js",
  threadsDisposableNode: "docs/nodejs/dual-engine-compat/cases/threads-disposable.node.cjs",
  threadsDisposableRhino: "docs/nodejs/dual-engine-compat/cases/threads-disposable.rhino.js",
  threadsSyncNode: "docs/nodejs/dual-engine-compat/cases/threads-sync-queue.node.cjs",
  threadsSyncRhino: "docs/nodejs/dual-engine-compat/cases/threads-sync-queue.rhino.js",
  dualEngineVerifier: "tools/nodejs/phase11/verify-rhino-dual-engine-compat-tests.js",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  matrixGenerator: "tools/nodejs/phase11/generate-rhino-compat-matrix.js",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  surfaceDocs: "docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  threadsPolicy: "docs/nodejs/design/RHINO_THREADS_MIGRATION_POLICY.md",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  p12_20Verifier: "tools/nodejs/phase12/verify-rhino-compat-threads-maturity-review.js",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files, repoRoot);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-threads-migration-cookbook-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatThreadsMigrationCookbook",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      cookbookComplete: checkById(checks, "cookbook-required-patterns"),
      dualEngineCases: checkById(checks, "dual-engine-thread-cases"),
      nextPhase: checkById(checks, "matrix-next-step-p12-22"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat threads migration cookbook verification: " + report.decision.status);
  console.log("Rhino compat threads migration policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildChecks(files, repoRoot) {
  const statusManifest = JSON.parse(files.statusManifest.text);
  const threadsModule = (statusManifest.modules || []).find((module) => module.id === "threads");
  const threadEntries = threadsModule ? threadsModule.apiEntries || [] : [];
  const entryById = new Map(threadEntries.map((entry) => [entry.id, entry]));
  const dualEngineManifest = JSON.parse(files.dualEngineManifest.text);
  const cases = Array.isArray(dualEngineManifest.cases) ? dualEngineManifest.cases : [];
  const caseById = new Map(cases.map((entry) => [entry.id, entry]));
  const threadCases = THREAD_CASE_IDS.map((id) => caseById.get(id)).filter(Boolean);
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const threadsSurface = (surfaceManifest.surfaces || []).find((surface) => surface.id === "threads.replacement");
  const matrixThreadsRow = findMatrixRow(files.matrix.text, "threads");
  const syntaxTargets = [
    files.threadsStartNode,
    files.threadsStartRhino,
    files.threadsDisposableNode,
    files.threadsDisposableRhino,
    files.threadsSyncNode,
    files.threadsSyncRhino,
    files.dualEngineVerifier,
    { relative: "tools/nodejs/phase12/verify-rhino-compat-threads-migration-cookbook.js" },
  ];
  const syntaxResults = syntaxTargets.map((item) => ({
    relative: item.relative,
    result: checkSyntax(path.join(repoRoot, item.relative)),
  }));
  const threadStatusCounts = countStatuses(threadEntries);
  const blockedRuntimeSnippets = [
    files.threadsStartNode.text,
    files.threadsDisposableNode.text,
    files.threadsSyncNode.text,
  ];

  return [
    check(
      "cookbook-required-patterns",
      includesAll(files.cookbook.text, [
        "Status: P12-21 migration cookbook",
        "does not depend on default `worker_threads`",
        "does not introduce `require(\"threads\")`",
        "`compat.threads`",
        "global `threads`",
        "global `sync`",
        "`threads.start(fn)`",
        "`async` / `await`",
        "bounded `engines` slots",
        "`work_manager`",
        "`threads.interrupt()`",
        "`AbortController`",
        "`timeoutMs`",
        "script stop",
        "`threads.pool()`",
        "bounded async queue",
        "`threads.disposable()`",
        "`Promise`, `EventEmitter`, or a MessageChannel-style handoff",
        "`sync(fn, lock)`",
        "single-owner state",
        "Promise queue",
        "P12-22 may evaluate a separately named compute/process-worker replacement API",
      ]),
      "Cookbook must cover the required Rhino threads migration patterns and default-off worker policy.",
    ),
    check(
      "cookbook-no-default-worker-code",
      !includesAny(files.cookbook.text, [
        "require(\"worker_threads\")",
        "require(\"node:worker_threads\")",
        "from \"worker_threads\"",
        "from \"node:worker_threads\"",
        "new Worker(",
      ]),
      "Cookbook examples must not depend on default worker_threads or Worker construction.",
    ),
    check(
      "dual-engine-thread-cases",
      THREAD_CASE_IDS.every((id) => caseById.has(id)) &&
        threadCases.length === THREAD_CASE_IDS.length &&
        threadCases.every((entry) =>
          entry.status === "blocked_by_design" &&
          entry.expected &&
          entry.expected.node === "blocked_by_design_marker" &&
          entry.devicePlan &&
          entry.devicePlan.mode === "policy_only" &&
          Array.isArray(entry.devicePlan.assertions) &&
          entry.devicePlan.assertions.length >= 2,
        ) &&
        (caseById.get("threads-disposable").statusManifestIds || []).includes("threads.disposable") &&
        (caseById.get("threads-sync-queue").statusManifestIds || []).includes("threads.sync"),
      "Dual-engine corpus must include threads-start plus at least two P12-21 blocked/migrated cases.",
    ),
    check(
      "dual-engine-snippet-shape",
      includesAll(files.threadsDisposableRhino.text, [
        "threads.disposable(",
        "threads.start(",
        ".setAndNotify(",
        ".blockedGet(",
      ]) &&
        includesAll(files.threadsSyncRhino.text, ["sync(", "threads.start(", ".join("]) &&
        blockedRuntimeSnippets.every((text) =>
          text.startsWith("\"nodejs\";") &&
          includesAll(text, [
            "require(\"autojs6:compat\")",
            "compat.blockedByDesign(",
            "compat.errorCodes.blockedByDesign",
            "async function main()",
            "main().catch",
          ]) &&
          !includesAny(text, [
            "compat.threads",
            "require(\"threads\")",
            "require(\"worker_threads\")",
            "require(\"node:worker_threads\")",
          ]),
        ) &&
        files.threadsDisposableNode.text.includes("\"threads.disposable\"") &&
        files.threadsSyncNode.text.includes("\"threads.sync\""),
      "P12-21 snippets must show legacy Rhino calls and Node blocked_by_design marker helpers without exposing blocked runtime surfaces.",
    ),
    check(
      "dual-engine-verifier-expanded",
      includesAll(files.dualEngineVerifier.text, [
        "\"threads-disposable\"",
        "\"threads-sync-queue\"",
        "threads.disposable",
        "threads.sync",
        ".blockedGet(",
        ".setAndNotify(",
        "sync(",
      ]),
      "Dual-engine verifier must require the P12-21 thread cases and snippet expectations.",
    ),
    check(
      "syntax-fixtures-and-verifiers",
      syntaxResults.every(({ result }) => result.ok),
      `P12-21 snippets and verifiers must pass node --check${formatSyntaxFailures(syntaxResults)}`,
    ),
    check(
      "status-manifest-p12-21",
      threadsModule &&
        threadsModule.status === "blocked_by_design" &&
        includesAll(JSON.stringify(threadsModule), [
          P12_21_NOTE,
          "async/await",
          "AbortController",
          "bounded async queue",
          "Promise queue",
          "MessageChannel",
          "worker_threads default-off",
        ]) &&
        THREAD_API_IDS.every((id) => entryById.has(id)) &&
        threadEntries.length === THREAD_API_IDS.length &&
        threadStatusCounts.blocked_by_design === THREAD_API_IDS.length &&
        threadEntries.every((entry) =>
          entry.status === "blocked_by_design" &&
          entry.asyncMode === "not_applicable" &&
          entry.nodeModule === null &&
          entry.bridgeModule === null &&
          entry.nodeName === null,
        ),
      "Status manifest must record P12-21 while keeping all threads API entries blocked_by_design with no node/bridge mapping.",
    ),
    check(
      "matrix-next-step-p12-22",
      includesAll(files.matrixGenerator.text, [
        P12_21_NOTE,
        P12_22_NEXT,
        P12_23_NEXT,
      ]) &&
        includesAll(files.p12_20Verifier.text, [
          P12_22_NEXT,
          P12_23_NEXT,
          "P12_21_NEXT",
        ]) &&
        includesAll(matrixThreadsRow, [
          "| 28 | threads | threads | blocked_by_design |",
          "engines",
          "timers/promises",
          "work_manager",
          "blocked_by_design:6",
          "provider_availability",
        ]) &&
        (matrixThreadsRow.includes(P12_22_NEXT) || matrixThreadsRow.includes(P12_23_NEXT)),
      "Generated matrix must advance threads from P12-21 toward P12-22/P12-23 while preserving blocked_by_design status.",
    ),
    check(
      "surface-manifest-p12-21",
      threadsSurface &&
        threadsSurface.status === "design_locked" &&
        (threadsSurface.tasks || []).includes("P12-21") &&
        includesAll(JSON.stringify(threadsSurface), [
          "P12-21 publishes the migration cookbook",
          "RHINO_THREADS_MIGRATION_COOKBOOK.md",
          "docs/nodejs/dual-engine-compat/manifest.json",
          "threads-disposable.node.cjs",
          "threads-sync-queue.node.cjs",
          "verify-rhino-compat-threads-migration-cookbook.js",
          "verifyNodeRhinoCompatThreadsMigrationCookbook",
        ]) &&
        includesAll(files.surfaceDocs.text, [
          "threads.replacement",
          "P12-21 publishes",
          "threads-disposable",
          "threads-sync-queue",
          "P12-22",
        ]),
      "Phase 12 surface manifest must include cookbook, dual-engine, and gate evidence.",
    ),
    check(
      "migration-doc-p12-21",
      includesAll(files.migration.text, [
        "P12-21 adds the Rhino threads migration cookbook",
        "RHINO_THREADS_MIGRATION_COOKBOOK.md",
        "threads-disposable",
        "threads-sync-queue",
        "verifyNodeRhinoCompatThreadsMigrationCookbook",
      ]) &&
        includesAll(files.threadsPolicy.text, [
          "P12-21 cookbook",
          "async",
          "AbortController",
          "work_manager",
        ]),
      "Migration guide and threads policy must point script authors to the P12-21 cookbook.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-21/.test(files.phase12Tasks.text) &&
        includesAll(files.phase12Tasks.text, [
          "RHINO_THREADS_MIGRATION_COOKBOOK.md",
          "threads-disposable",
          "threads-sync-queue",
          "bounded async queue",
          "MessageChannel",
          "blocked_by_design",
          "verify-rhino-compat-threads-migration-cookbook.js",
          "verifyNodeRhinoCompatThreadsMigrationCookbook",
        ]),
      "Phase 12 task plan must mark P12-21 complete with cookbook, dual-engine, status, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatThreadsMigrationCookbook") &&
        files.gradle.text.includes("verify-rhino-compat-threads-migration-cookbook.js") &&
        files.gradle.text.includes("RHINO_THREADS_MIGRATION_COOKBOOK.md") &&
        files.gradle.text.includes("threads-disposable.node.cjs") &&
        files.gradle.text.includes("verifyNodeRhinoCompatThreadsMaturityReview"),
      "Gradle must register verifyNodeRhinoCompatThreadsMigrationCookbook with cookbook and dual-engine inputs.",
    ),
  ];
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

function countStatuses(entries) {
  const counts = {};
  for (const entry of entries) {
    counts[entry.status] = (counts[entry.status] || 0) + 1;
  }
  return counts;
}

function findMatrixRow(markdown, moduleId) {
  return markdown.split(/\r?\n/).find((line) => line.includes(`| ${moduleId} |`)) || "";
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function includesAny(text, values) {
  return values.some((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function formatSyntaxFailures(results) {
  const failures = results.filter(({ result }) => !result.ok);
  if (failures.length === 0) {
    return "";
  }
  return ": " + failures.map(({ relative, result }) => `${relative}: ${result.message}`).join(" | ");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Threads Migration Cookbook Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
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
      console.log("Usage: node verify-rhino-compat-threads-migration-cookbook.js [--repo-root dir] [--report-dir dir]");
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
