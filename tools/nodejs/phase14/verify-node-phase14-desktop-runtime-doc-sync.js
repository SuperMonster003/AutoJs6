#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-desktop-runtime-doc-sync";
const SOURCE_REPORTS = Object.freeze([
  ["p14WorkerNative", "phase14-worker-native-availability.json"],
  ["p14WorkerRunnable", "phase14-worker-runnable-packaged-smoke.json"],
  ["p14WorkerResource", "phase14-worker-resource-rss-stress.json"],
  ["p14ProcessWorker", "phase14-process-worker-replacement-decision.json"],
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_32_DESKTOP_RUNTIME_DOC_SYNC.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/desktop-runtime-doc-sync.json",
  planDoc: "docs/nodejs/phase14/PHASE14_DESKTOP_RUNTIME_DOC_SYNC.md",
  releaseMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_2_DESKTOP_RUNTIME_MATRIX.md",
  migrationCookbook: "docs/nodejs/phase14/PHASE14_DESKTOP_RUNTIME_MIGRATION_COOKBOOK.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  autojsTypes: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  tsSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  workerDesignDoc: "docs/nodejs/design/WORKER_THREADS.md",
  examplesDoc: "docs/nodejs/EXAMPLES.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  securityCorpus: "docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 desktop runtime doc sync: " + report.decision.status);
  console.log("Phase 14 desktop runtime doc sync blockers: " + report.blockers.length);
  console.log("Phase 14 desktop runtime doc sync policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  SOURCE_REPORTS.forEach(([key, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      key,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const plan = parseJson(files.planJson.text);
  const corpus = parseJson(files.securityCorpus.text);
  const sourceReports = Object.fromEntries(
    SOURCE_REPORTS.map(([key]) => [key, parseJson(files[key].text)]),
  );
  const workerSurface = (manifest.surfaces || []).find((entry) => entry.id === "desktop.worker_runtime") || null;
  const workerCorpus = (corpus.capabilities || []).find((entry) => entry.id === "worker_threads") || null;
  const childProcessCorpus = (corpus.capabilities || []).find((entry) => entry.id === "child_process") || null;
  const generatedReports = SOURCE_REPORTS.map(([key]) => {
    const parsed = sourceReports[key];
    return {
      id: key,
      exists: files[key].exists,
      status: statusOf(parsed),
    };
  });
  const p14NativeDecision = sourceReports.p14WorkerNative.decision || {};
  const p14RunnableDecision = sourceReports.p14WorkerRunnable.decision || {};
  const p14ResourceDecision = sourceReports.p14WorkerResource.decision || {};
  const p14ProcessDecision = sourceReports.p14ProcessWorker.decision || {};
  const scenarios = [
    scenario("source_gate_imports", "P14-28 through P14-31 generated reports are imported as the v1.2 desktop runtime truth", [
      value("schema", plan.schema === "autojs6-node-phase14-desktop-runtime-doc-sync-v1"),
      value("task", plan.task === "P14-32"),
      value("status", plan.status === "ready"),
      value("promotionState", plan.promotionState === "desktop_runtime_docs_synced"),
      value("releaseDecision", plan.releaseDecision === "worker_intentionally_unsupported_v1_2"),
      value("sourceGates", sameSet(plan.sourceGates || [], ["P14-28", "P14-29", "P14-30", "P14-31"])),
      ...generatedReports.map((entry) => check(`generated:${entry.id}`, entry.exists && entry.status === "ready", `${entry.id} generated report must exist with ready status`)),
      value("nativeIntentionalNonSupport", p14NativeDecision.v1_2WorkerDecision === "intentional_non_support_refreshed"),
      value("nativeRunnableProofFalse", p14NativeDecision.nativeRunnableProofReady === false),
      value("runnableConditionalSkip", p14RunnableDecision.workerDecision === "conditional_skip_native_unavailable" &&
        p14RunnableDecision.runnableSmokeExecuted === false &&
        p14RunnableDecision.packagedSmokeExecuted === false),
      value("resourceConditionalSkip", p14ResourceDecision.workerDecision === "conditional_skip_native_unavailable" &&
        p14ResourceDecision.resourceStressExecuted === false),
      value("processNoFacade", p14ProcessDecision.processWorkerDecision === "no_user_facing_facade_v1_2" &&
        p14ProcessDecision.callableFacadeIntroduced === false),
    ]),
    scenario("release_matrix_and_policy_sync", "Release matrix, worker design, and migration cookbook record intentional non-support", [
      token(files.releaseMatrix, "Node Profile v1.2 Desktop Runtime Matrix"),
      token(files.releaseMatrix, "`worker_intentionally_unsupported_v1_2`"),
      token(files.releaseMatrix, "P14-28"),
      token(files.releaseMatrix, "P14-31"),
      token(files.releaseMatrix, "No runnable worker smoke counts for v1.2 release."),
      token(files.planDoc, "The v1.2 desktop runtime release decision is"),
      token(files.planDoc, "`worker_intentionally_unsupported_v1_2`"),
      token(files.migrationCookbook, "Phase 14 Desktop Runtime Migration Cookbook"),
      token(files.migrationCookbook, "Do not migrate stable scripts to runnable `worker_threads` for v1.2."),
      token(files.migrationCookbook, "P14-31 closes process-worker replacement for v1.2"),
      token(files.workerDesignDoc, "P14-32 records the v1.2 desktop runtime release policy"),
      token(files.workerDesignDoc, "`worker_intentionally_unsupported_v1_2`"),
    ]),
    scenario("declaration_and_example_sync", "Type declaration references and examples keep worker/process APIs diagnostic or guarded", [
      token(files.typeReadme, "P14-32 records the v1.2 desktop runtime declaration sync"),
      token(files.typeReadme, "`worker_intentionally_unsupported_v1_2`"),
      token(files.autojsTypes, "export interface WorkerThreadsProfile"),
      token(files.autojsTypes, "export interface ProcessWorkerReplacementProfile"),
      token(files.autojsTypes, "readonly workerThreadsProfile: WorkerThreadsProfile;"),
      token(files.autojsTypes, "readonly processWorkerReplacementProfile: ProcessWorkerReplacementProfile;"),
      token(files.tsSmoke, "autojsProfile.workerThreadsProfile.status"),
      token(files.tsSmoke, "autojsProfile.processWorkerReplacementProfile.preferredPrimitive"),
      token(files.examplesDoc, "P14-32 docs sync keeps the v1.2 desktop runtime examples guarded"),
      token(files.examplesDoc, "`worker_threads`, WorkerPool, process-worker, and raw `child_process` examples"),
    ]),
    scenario("compat_security_error_sync", "Compatibility, security, error, and security corpus docs share the same denial boundary", [
      token(files.compatibilityProfile, "P14-32 records the v1.2 desktop runtime doc/release sync"),
      token(files.compatibilityProfile, "`worker_intentionally_unsupported_v1_2`"),
      token(files.securityModel, "P14-32 records the v1.2 desktop runtime doc/release sync"),
      token(files.securityModel, "`worker_intentionally_unsupported_v1_2`"),
      token(files.errorCatalog, "P14-32 desktop runtime doc sync"),
      token(files.errorCatalog, "`worker_intentionally_unsupported_v1_2`"),
      value("workerSecurityCorpus", workerCorpus &&
        includesAll(workerCorpus.ciEvidence || [], [
          "verifyNodePhase14WorkerNativeAvailability",
          "verifyNodePhase14WorkerRunnablePackagedSmoke",
          "verifyNodePhase14WorkerResourceRssStress",
          "verifyNodePhase14DesktopRuntimeDocSync",
        ]) &&
        includesAll(workerCorpus.promotionPreconditions || [], [
          "P14-32 desktop runtime doc sync keeps worker_intentionally_unsupported_v1_2 until native, packaged, resource, UX, security, and rollback evidence exists",
        ])),
      value("childProcessSecurityCorpus", childProcessCorpus &&
        includesAll(childProcessCorpus.promotionPreconditions || [], [
          "P14-31 process-worker replacement keeps no user-facing facade and no raw process authority for v1.2",
        ])),
    ]),
    scenario("migration_docs_sync", "Pro/AutoX and Pro9 comparison migration docs reflect v1.2 desktop runtime policy", [
      token(files.proAutoxNotes, "P14-32 records the v1.2 desktop runtime doc/release sync"),
      token(files.proAutoxNotes, "`worker_intentionally_unsupported_v1_2`"),
      token(files.proComparison, "P14-32 records the v1.2 desktop runtime doc/release sync"),
      token(files.proComparison, "`worker_intentionally_unsupported_v1_2`"),
    ]),
    scenario("surface_and_gradle_wiring", "Phase 14 manifest, README, docs, and Gradle expose the P14-32 gate", [
      value("surfaceEvidence", workerSurface &&
        workerSurface.status === "design_locked" &&
        (workerSurface.tasks || []).includes("P14-32") &&
        (workerSurface.evidence || []).includes("docs/nodejs/phase14/desktop-runtime-doc-sync.json") &&
        (workerSurface.evidence || []).includes(":app:verifyNodePhase14DesktopRuntimeDocSync")),
      token(files.surfaceManifestDoc, "P14-32 adds the desktop runtime docs/release policy sync gate"),
      token(files.phase14Tasks, "- [x] P14-32: Sync desktop runtime docs and release policy."),
      token(files.phase14Tasks, "Desktop runtime docs/release policy sync gate deployed:"),
      token(files.phase14Report, "P14-32 release decision: `worker_intentionally_unsupported_v1_2`"),
      token(files.phase14Readme, ":app:verifyNodePhase14DesktopRuntimeDocSync"),
      token(files.testing, ":app:verifyNodePhase14DesktopRuntimeDocSync"),
      token(files.ciGates, ":app:verifyNodePhase14DesktopRuntimeDocSync"),
      token(files.gradle14, "verifyNodePhase14DesktopRuntimeDocSync"),
      token(files.gradle14, "verify-node-phase14-desktop-runtime-doc-sync.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-desktop-runtime-doc-sync-v1", "P14-32 doc sync plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Desktop Runtime Doc Sync"), "P14-32 doc sync documentation must exist."),
    check("release-matrix-present", files.releaseMatrix.exists && files.releaseMatrix.text.includes("Node Profile v1.2 Desktop Runtime Matrix"), "P14-32 v1.2 release matrix must exist."),
    check("migration-cookbook-present", files.migrationCookbook.exists && files.migrationCookbook.text.includes("Phase 14 Desktop Runtime Migration Cookbook"), "P14-32 migration cookbook must exist."),
    check("source-reports-ready", generatedReports.every((entry) => entry.exists && entry.status === "ready"), "P14-28 through P14-31 generated reports must exist with ready status."),
    check("release-policy-intentional-non-support", plan.releaseDecision === "worker_intentionally_unsupported_v1_2" &&
      p14NativeDecision.nativeRunnableProofReady === false &&
      p14RunnableDecision.runnableSmokeExecuted === false &&
      p14ResourceDecision.resourceStressExecuted === false &&
      p14ProcessDecision.callableFacadeIntroduced === false,
    "P14-32 must keep worker/process support intentionally unsupported unless source gates provide positive evidence."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-32 sync scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-desktop-runtime-doc-sync-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-32",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    checks,
    scenarios,
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      releaseDecision: "worker_intentionally_unsupported_v1_2",
      p14TaskComplete: policyViolations.length === 0,
      sourceReportsImported: generatedReports.every((entry) => entry.exists && entry.status === "ready"),
      releaseMatrixSynced: scenarioById(scenarios, "release_matrix_and_policy_sync").status === "ready",
      declarationsSynced: scenarioById(scenarios, "declaration_and_example_sync").status === "ready",
      compatibilitySecurityErrorDocsSynced: scenarioById(scenarios, "compat_security_error_sync").status === "ready",
      migrationDocsSynced: scenarioById(scenarios, "migration_docs_sync").status === "ready",
      runtimeAuthorityChanged: false,
      runnableWorkerSupportPromoted: false,
      packagedWorkerSupportPromoted: false,
      processWorkerFacadePromoted: false,
      rawProcessAuthorityPromoted: false,
      releaseBlockingBlockers: 0,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Desktop Runtime Doc Sync",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- Release decision: \`${report.decision.releaseDecision}\``,
    `- Source reports imported: \`${report.decision.sourceReportsImported}\``,
    `- Release matrix synced: \`${report.decision.releaseMatrixSynced}\``,
    `- Declarations synced: \`${report.decision.declarationsSynced}\``,
    `- Compatibility/security/error docs synced: \`${report.decision.compatibilitySecurityErrorDocsSynced}\``,
    `- Migration docs synced: \`${report.decision.migrationDocsSynced}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Runnable worker support promoted: \`${report.decision.runnableWorkerSupportPromoted}\``,
    `- Process-worker facade promoted: \`${report.decision.processWorkerFacadePromoted}\``,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return { id, description, status: missing.length === 0 ? "ready" : "missing_evidence", checks, missing };
}

function value(id, ok) {
  return { id, ok: Boolean(ok), message: `missing value check: ${id}` };
}

function token(file, expected) {
  return {
    id: `${file.relative}:${expected}`,
    ok: file.exists && file.text.includes(expected),
    message: `missing evidence token: ${expected} in ${file.relative}`,
  };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function includesAll(actual, expected) {
  return Array.isArray(actual) && expected.every((value) => actual.includes(value));
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-node-phase14-desktop-runtime-doc-sync.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
