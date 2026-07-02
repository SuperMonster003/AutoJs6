#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-release-candidate-checklist";
const SOURCE_REPORTS = Object.freeze([
  ["summary", "phase14-summary.json"],
  ["security", "phase14-security-regression-corpus.json"],
  ["packaged", "phase14-packaged-aggregate-smoke.json"],
  ["types", "phase14-type-declaration-profiles.json"],
  ["migration", "phase14-migration-pro-comparison.json"],
  ["uiDocs", "phase14-ui-automation-media-doc-sync.json"],
  ["taskDocs", "phase14-task-device-media-doc-sync.json"],
  ["desktopDocs", "phase14-desktop-runtime-doc-sync.json"],
]);
const REQUIRED_RC_SECTIONS = Object.freeze([
  "Required v1.2 RC Blockers",
  "Host Gates",
  "Connected-Device Gates",
  "Packaged APK Gates",
  "Docs, Examples, And TypeScript",
  "Release Notes",
  "v1.3 Future Promotion Backlog",
]);
const REQUIRED_RELEASE_NOTE_TOKENS = Object.freeze([
  "provider_poc_not_promoted",
  "worker_intentionally_unsupported_v1_2",
  "host_contract_ready",
  "phase14_summary_gate_ready",
  "Safe profile remains default",
  "do not imply raw Android object parity",
  "v1.3 future promotion backlog",
  "not release tag approval",
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_38_RELEASE_CANDIDATE_CHECKLIST.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/node-v1-2-rc-checklist.json",
  planDoc: "docs/nodejs/phase14/PHASE14_NODE_V1_2_RC_CHECKLIST.md",
  rcChecklist: "docs/nodejs/releases/NODE_PROFILE_V1_2_RC_CHECKLIST.md",
  releaseNotesDraft: "docs/nodejs/releases/NODE_PROFILE_V1_2_RELEASE_NOTES_DRAFT.md",
  summaryDoc: "docs/nodejs/reports/phase14/PHASE14_SUMMARY.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
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
  console.log("Phase 14 release candidate checklist: " + report.decision.status);
  console.log("Phase 14 release candidate checklist blockers: " + report.blockers.length);
  console.log("Phase 14 release candidate checklist policy violations: " + report.policyViolations.length);
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
  const plan = parseJson(files.planJson.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const docsSurface = (manifest.surfaces || []).find((entry) => entry.id === "docs.examples.final_gate") || null;
  const sourceReports = SOURCE_REPORTS.map(([key]) => {
    const parsed = parseJson(files[key].text);
    return {
      key,
      exists: files[key].exists,
      status: statusOf(parsed),
      task: parsed.task || null,
      policyViolations: Array.isArray(parsed.policyViolations) ? parsed.policyViolations.length : 0,
      blockers: Array.isArray(parsed.blockers) ? parsed.blockers.length : 0,
      decision: parsed.decision || {},
      parsed,
    };
  });
  const summary = parseJson(files.summary.text);
  const futureBacklogCount = summary.summaryGate && Array.isArray(summary.summaryGate.futurePromotionReports)
    ? summary.summaryGate.futurePromotionReports.length
    : 0;
  const openTasks = Array.isArray(summary.openFutureWork && summary.openFutureWork.openTasks)
    ? summary.openFutureWork.openTasks.map((task) => task.id)
    : [];
  const scenarios = [
    scenario("plan_and_source_reports", "P14-38 plan and required source reports are ready", [
      value("planSchema", plan.schema === "autojs6-node-phase14-rc-checklist-plan-v1"),
      value("planTask", plan.task === "P14-38"),
      value("planStatus", plan.status === "ready"),
      value("planDecision", plan.decision === "release_candidate_checklist_ready"),
      value("gradleTask", plan.gradleTask === ":app:verifyNodePhase14ReleaseCandidateChecklist"),
      value("runtimeAuthority", plan.runtimeAuthorityChanged === false),
      ...sourceReports.map((entry) => check(
        `report:${entry.key}`,
        entry.exists && ["ready", "passed"].includes(entry.status),
        `${entry.key} source report must exist with ready/passed status`,
      )),
    ]),
    scenario("summary_closeout_boundary", "Summary gate remains ready while future work is separated", [
      value("summaryReady", statusOf(summary) === "ready"),
      value("summaryDecision", summary.decision && summary.decision.summaryDecision === "phase14_summary_gate_ready"),
      value("p14TaskComplete", Array.isArray(summary.tasks) &&
        summary.tasks.some((task) => task.id === "P14-38" && task.status === "completed")),
      value("zeroOpenFinal", summary.decision && summary.decision.zeroOpenFinalBlockers === true),
      value("zeroPolicy", summary.decision && summary.decision.zeroPolicyViolations === true),
      value("zeroReleaseBlocking", summary.decision && summary.decision.zeroReleaseBlockingReports === true),
      value("futureBacklogVisible", futureBacklogCount >= 19 && openTasks.includes("P14-39")),
    ]),
    scenario("rc_checklist_sections", "RC checklist separates required blockers from future promotion backlog", [
      ...REQUIRED_RC_SECTIONS.map((entry) => token(files.rcChecklist, entry)),
      token(files.rcChecklist, ":app:generateNodePhase14Summary"),
      token(files.rcChecklist, ":app:verifyNodePhase14ReleaseCandidateChecklist"),
      token(files.rcChecklist, ":app:verifyAutoJs6NodeTypeDeclarations"),
      token(files.rcChecklist, ":app:typeCheckNodeTypescriptSmoke"),
      token(files.rcChecklist, ":app:verifyNodeExamples"),
      token(files.rcChecklist, ":app:installAndRunNodeV12PackagedSmoke"),
      token(files.rcChecklist, "not release tag approval"),
      token(files.rcChecklist, "v1.3 future promotion backlog"),
    ]),
    scenario("release_notes_boundaries", "Release notes draft carries v1.2 status labels without overclaiming authority", [
      ...REQUIRED_RELEASE_NOTE_TOKENS.map((entry) => token(files.releaseNotesDraft, entry)),
      check(
        "no-raw-object-parity-overclaim",
        !files.releaseNotesDraft.text.includes("raw Android object parity is available") &&
          !files.releaseNotesDraft.text.includes("raw Android object parity promoted"),
        "release notes draft must not claim raw Android object parity is available",
      ),
    ]),
    scenario("docs_and_gradle_wiring", "Docs, manifest, Testing, CI, Security, Error Catalog, and Gradle expose P14-38", [
      value("surfaceEvidence", docsSurface &&
        (docsSurface.tasks || []).includes("P14-38") &&
        (docsSurface.evidence || []).includes("docs/nodejs/phase14/node-v1-2-rc-checklist.json") &&
        (docsSurface.evidence || []).includes("docs/nodejs/releases/NODE_PROFILE_V1_2_RC_CHECKLIST.md") &&
        (docsSurface.evidence || []).includes(":app:verifyNodePhase14ReleaseCandidateChecklist")),
      token(files.surfaceManifestDoc, "P14-38 adds the v1.2 release candidate checklist"),
      token(files.phase14Tasks, "- [x] P14-38: Create v1.2 release candidate checklist."),
      token(files.phase14Tasks, "Release candidate checklist gate deployed:"),
      token(files.phase14Report, "P14-38 decision: `release_candidate_checklist_ready`"),
      token(files.phase14Readme, ":app:verifyNodePhase14ReleaseCandidateChecklist"),
      token(files.testing, "Phase 14 v1.2 release candidate checklist gate"),
      token(files.ciGates, "verifyNodePhase14ReleaseCandidateChecklist"),
      token(files.securityModel, "P14-38 records the v1.2 release candidate checklist"),
      token(files.errorCatalog, "P14-38 release candidate checklist"),
      token(files.gradle14, "verifyNodePhase14ReleaseCandidateChecklist"),
      token(files.gradle14, "verify-node-phase14-release-candidate-checklist.js"),
    ]),
  ];
  const checks = [
    check("plan-json-present", scenarios[0].status === "ready", "P14-38 plan and source report inputs must be ready."),
    check("summary-closeout-boundary", scenarios[1].status === "ready", "P14-38 must import a ready summary with future work separated."),
    check("rc-checklist-sections", scenarios[2].status === "ready", "RC checklist must include required gates and future backlog sections."),
    check("release-notes-boundaries", scenarios[3].status === "ready", "Release notes draft must carry v1.2 status labels without authority overclaims."),
    check("docs-and-gradle-wiring", scenarios[4].status === "ready", "Docs and Gradle must expose the P14-38 checklist gate."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({
    id: entry.id,
    message: entry.message,
  }));
  return {
    schema: "autojs6-node-phase14-release-candidate-checklist-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-38",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, {
      path: file.relative,
      exists: file.exists,
    }])),
    checks,
    scenarios,
    sourceReports: sourceReports.map((entry) => ({
      key: entry.key,
      status: entry.status,
      task: entry.task,
      policyViolations: entry.policyViolations,
      blockers: entry.blockers,
    })),
    releaseBlockers: [],
    futureBacklog: {
      futurePromotionReports: futureBacklogCount,
      openTasks,
    },
    blockers: [],
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      rcChecklistDecision: "release_candidate_checklist_ready",
      p14TaskComplete: policyViolations.length === 0,
      summaryGateReady: scenarioById(scenarios, "summary_closeout_boundary").status === "ready",
      requiredV12BlockersSeparated: scenarioById(scenarios, "rc_checklist_sections").status === "ready",
      futureBacklogSeparated: futureBacklogCount >= 19,
      releaseNotesDraftReady: scenarioById(scenarios, "release_notes_boundaries").status === "ready",
      releaseTagApproval: false,
      runtimeAuthorityChanged: false,
    },
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Release Candidate Checklist",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- RC checklist decision: \`${report.decision.rcChecklistDecision}\``,
    `- Summary gate ready: \`${report.decision.summaryGateReady}\``,
    `- Required v1.2 blockers separated: \`${report.decision.requiredV12BlockersSeparated}\``,
    `- Future backlog separated: \`${report.decision.futureBacklogSeparated}\``,
    `- Release notes draft ready: \`${report.decision.releaseNotesDraftReady}\``,
    `- Release tag approval: \`${report.decision.releaseTagApproval}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    "",
    "## Source Reports",
    "",
    "| Source | Task | Status | Policy violations | Blockers |",
    "| --- | --- | --- | ---: | ---: |",
  ];
  report.sourceReports.forEach((entry) => {
    lines.push(`| \`${entry.key}\` | \`${entry.task || "-"}\` | \`${entry.status}\` | ${entry.policyViolations} | ${entry.blockers} |`);
  });
  lines.push(
    "",
    "## Future Backlog",
    "",
    `- Future promotion reports: ${report.futureBacklog.futurePromotionReports}`,
    `- Open tasks: ${report.futureBacklog.openTasks.map((task) => `\`${task}\``).join(", ") || "None"}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Missing evidence |",
    "| --- | --- | --- |",
  );
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

function statusOf(report) {
  return report.status || (report.decision && report.decision.status) || "missing";
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

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
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
      console.log("Usage: node verify-node-phase14-release-candidate-checklist.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
