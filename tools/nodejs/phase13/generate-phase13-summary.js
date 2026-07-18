#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-summary";
const DEFAULT_DOCS_OUTPUT = "docs/nodejs/reports/phase13/PHASE13_SUMMARY.md";
const REQUIRED_TASKS = Object.freeze(
  Array.from({ length: 58 }, (_, index) => `P13-${String(index).padStart(2, "0")}`),
);
const REPORT_CONTRACT_ROWS = Object.freeze([
  ["node-builtin-parity", "autojs6-node-builtin-desktop-parity-matrix-v1", "generateNodeBuiltinDesktopParityMatrix", null],
  ["phase13-accessibility-selector-promotion", "autojs6-node-phase13-accessibility-selector-promotion-v1", "verifyNodePhase13AccessibilitySelectorPromotion", "P13-19"],
  ["phase13-android-package-install-manager", "autojs6-node-phase13-android-package-install-manager-v1", "verifyNodePhase13AndroidPackageInstallManager", "P13-09"],
  ["phase13-app-intent-broadcast-parity", "autojs6-node-phase13-app-intent-broadcast-parity-v1", "verifyNodePhase13AppIntentBroadcastParity", "P13-23"],
  ["phase13-autojs-global-v1", "autojs6-node-phase13-autojs-global-v1", "verifyNodePhase13AutoJsGlobalV1", "P13-12"],
  ["phase13-baseline-verification", "autojs6-node-phase13-baseline-verification-v2", "verifyNodePhase13Baseline", null],
  ["phase13-capability-truth", "autojs6-node-phase13-capability-truth-v1", "generateNodePhase13CapabilityTruth", null],
  ["phase13-child-process-scoped-runner-decision", "autojs6-node-phase13-child-process-scoped-runner-decision-v1", "verifyNodePhase13ChildProcessScopedRunnerDecision", "P13-40"],
  ["phase13-controlled-wasi-profile-decision", "autojs6-node-phase13-controlled-wasi-profile-decision-v1", "verifyNodePhase13ControlledWasiProfileDecision", "P13-38"],
  ["phase13-crash-evidence-automation", "autojs6-node-phase13-crash-evidence-automation-v1", "collectNodePhase13CrashEvidence", "P13-05"],
  ["phase13-dependency-compatibility-corpus", "autojs6-node-phase13-dependency-compatibility-corpus-v1", "verifyNodePhase13DependencyCompatibilityCorpus", "P13-47"],
  ["phase13-desktop-parity-examples", "autojs6-node-phase13-desktop-parity-examples-v1", "verifyNodePhase13DesktopParityExamples", "P13-54"],
  ["phase13-dgram-udp-decision", "autojs6-node-phase13-dgram-udp-decision-v1", "verifyNodePhase13DgramUdpDecision", "P13-31"],
  ["phase13-esm-loader-parity-decision", "autojs6-node-phase13-esm-loader-parity-decision-v1", "verifyNodePhase13EsmLoaderParityDecision", "P13-41"],
  ["phase13-filesystem-advanced-apis", "autojs6-node-phase13-filesystem-advanced-apis-v1", "verifyNodePhase13FilesystemAdvancedApis", "P13-33"],
  ["phase13-filesystem-profile-decision", "autojs6-node-phase13-filesystem-profile-decision-v1", "verifyNodePhase13FilesystemProfileDecision", "P13-32"],
  ["phase13-full-heavy-resource-stress", "autojs6-node-phase13-full-heavy-resource-stress-v1", "verifyNodePhase13FullHeavyResourceStress", "P13-08"],
  ["phase13-http-https-client-parity", "autojs6-node-phase13-http-https-client-parity-v1", "verifyNodePhase13HttpHttpsClientParity", "P13-29"],
  ["phase13-inspector-debug-profile-decision", "autojs6-node-phase13-inspector-debug-profile-decision-v1", "verifyNodePhase13InspectorDebugProfileDecision", "P13-37"],
  ["phase13-intent-broadcast-provider", "autojs6-node-phase13-intent-broadcast-provider-v1", "verifyNodePhase13IntentBroadcastProvider", "P13-22"],
  ["phase13-java-interop-allowlist", "autojs6-node-phase13-java-interop-allowlist-v1", "verifyNodePhase13JavaInteropAllowlist", "P13-13"],
  ["phase13-live-ui-provider-promotion", "autojs6-node-phase13-live-ui-provider-promotion-v1", "verifyNodePhase13LiveUiProviderPromotion", "P13-17"],
  ["phase13-media-recorder-mediainfo-provider", "autojs6-node-phase13-media-recorder-mediainfo-provider-v1", "verifyNodePhase13MediaRecorderMediainfoProvider", "P13-26"],
  ["phase13-migration-pro9-comparison", "autojs6-node-phase13-migration-pro9-comparison-v1", "verifyNodePhase13MigrationPro9Comparison", "P13-56"],
  ["phase13-native-addon-loading-decision", "autojs6-node-phase13-native-addon-loading-decision-v1", "verifyNodePhase13NativeAddonLoadingDecision", "P13-39"],
  ["phase13-net-tls-dns-staged-compatibility", "autojs6-node-phase13-net-tls-dns-staged-compatibility-v1", "verifyNodePhase13NetTlsDnsStagedCompatibility", "P13-30"],
  ["phase13-notifications-settings-power-manager", "autojs6-node-phase13-notifications-settings-power-manager-v1", "verifyNodePhase13NotificationsSettingsPowerManager", "P13-25"],
  ["phase13-overlay-provider-promotion", "autojs6-node-phase13-overlay-provider-promotion-v1", "verifyNodePhase13OverlayProviderPromotion", "P13-18"],
  ["phase13-package-lock-integrity-policy", "autojs6-node-phase13-package-lock-integrity-policy-v1", "verifyNodePhase13PackageLockIntegrityPolicy", "P13-48"],
  ["phase13-package-manager-android-decision", "autojs6-node-phase13-package-manager-android-decision-v1", "verifyNodePhase13PackageManagerAndroidDecision", "P13-42"],
  ["phase13-packaged-capability-metadata-decision", "autojs6-node-phase13-packaged-capability-metadata-decision-v1", "verifyNodePhase13PackagedCapabilityMetadataDecision", "P13-45"],
  ["phase13-packaged-long-running-service", "autojs6-node-phase13-packaged-long-running-service-v1", "verifyNodePhase13PackagedLongRunningService", "P13-07"],
  ["phase13-plugin-system-parity-decision", "autojs6-node-phase13-plugin-system-parity-decision-v1", "verifyNodePhase13PluginSystemParityDecision", "P13-46"],
  ["phase13-privacy-disclosure-policy", "autojs6-node-phase13-privacy-disclosure-policy-v1", "verifyNodePhase13PrivacyDisclosurePolicy", "P13-52"],
  ["phase13-pro-entry-directives", "autojs6-node-phase13-pro-entry-directives-v1", "verifyNodePhase13ProEntryDirectives", "P13-11"],
  ["phase13-pro-parity-examples", "autojs6-node-phase13-pro-parity-examples-v1", "verifyNodePhase13ProParityExamples", "P13-53"],
  ["phase13-process-parity-decision", "autojs6-node-phase13-process-parity-decision-v1", "verifyNodePhase13ProcessParityDecision", "P13-44"],
  ["phase13-process-worker-replacement-decision", "autojs6-node-phase13-process-worker-replacement-decision-v1", "verifyNodePhase13ProcessWorkerReplacementDecision", "P13-35"],
  ["phase13-profile-rollback-playbook", "autojs6-node-phase13-profile-rollback-playbook-v1", "verifyNodePhase13ProfileRollbackPlaybook", "P13-51"],
  ["phase13-profile-selection-diagnostics", "autojs6-node-phase13-profile-selection-diagnostics-v1", "verifyNodePhase13ProfileSelectionDiagnostics", "P13-49"],
  ["phase13-relaxed-capability-security-corpus", "autojs6-node-phase13-relaxed-capability-security-corpus-v1", "verifyNodePhase13RelaxedCapabilitySecurityCorpus", "P13-50"],
  ["phase13-rhino-import-helpers-decision", "autojs6-node-phase13-rhino-import-helpers-decision-v1", "verifyNodePhase13RhinoImportHelpersDecision", "P13-15"],
  ["phase13-rhino-install-java-proxy", "autojs6-node-phase13-rhino-install-java-proxy-v1", "verifyNodePhase13RhinoInstallJavaProxy", "P13-14"],
  ["phase13-rhino-java-adapter-decision", "autojs6-node-phase13-rhino-java-adapter-decision-v1", "verifyNodePhase13RhinoJavaAdapterDecision", "P13-16"],
  ["phase13-screenshot-image-ocr-promotion", "autojs6-node-phase13-screenshot-image-ocr-promotion-v1", "verifyNodePhase13ScreenshotImageOcrPromotion", "P13-20"],
  ["phase13-second-execution-slot-scheduler", "autojs6-node-phase13-second-execution-slot-scheduler-v1", "verifyNodePhase13SecondExecutionSlotScheduler", "P13-06"],
  ["phase13-shell-root-shizuku-capability", "autojs6-node-phase13-shell-root-shizuku-capability-v1", "verifyNodePhase13ShellRootShizukuCapability", "P13-24"],
  ["phase13-stdlib-conformance-decision", "autojs6-node-phase13-stdlib-conformance-decision-v1", "verifyNodePhase13StdlibConformanceDecision", "P13-43"],
  ["phase13-surface-manifest-verification", "autojs6-node-phase13-surface-manifest-verification-v1", "verifyNodePhase13SurfaceManifest", null],
  ["phase13-task-database-provider", "autojs6-node-phase13-task-database-provider-v1", "verifyNodePhase13TaskDatabaseProvider", "P13-21"],
  ["phase13-type-declaration-profiles", "autojs6-node-phase13-type-declaration-profiles-v1", "verifyNodePhase13TypeDeclarationProfiles", "P13-55"],
  ["phase13-utility-heavy-rhino-modules", "autojs6-node-phase13-utility-heavy-rhino-modules-v1", "verifyNodePhase13UtilityHeavyRhinoModules", "P13-27"],
  ["phase13-vm-module-decision", "autojs6-node-phase13-vm-module-decision-v1", "verifyNodePhase13VmModuleDecision", "P13-36"],
  ["phase13-worker-threads-opt-in-promotion", "autojs6-node-phase13-worker-threads-opt-in-promotion-v1", "verifyNodePhase13WorkerThreadsOptInPromotion", "P13-34"],
]);
const REPORT_CONTRACTS = Object.freeze(REPORT_CONTRACT_ROWS.map(([basename, schema, gate, task]) =>
  Object.freeze({
    basename,
    schema,
    gate,
    task,
    allowedStatuses: Object.freeze(task === null ? ["passed"] : ["ready", "partial"]),
    requiredArrays: Object.freeze(task === null ? ["policyViolations"] : ["policyViolations", "blockers"]),
    requiredDecisionBooleans: Object.freeze(task === null ? [] : ["phase13TaskComplete", "finalBlockerClosable"]),
  })));
const REQUIRED_REPORT_BASENAMES = Object.freeze(REPORT_CONTRACTS.map((contract) => contract.basename));
const REPORT_CONTRACT_BY_NAME = new Map(REPORT_CONTRACTS.map((contract) =>
  [`${contract.basename}.json`, contract]));
const ALLOWED_SUPPLEMENTAL_REPORT_NAMES = new Set([
  `${REPORT_BASENAME}.json`,
  "phase13-full-heavy-resource-stress-archive.json",
]);
const COMPLETED_SURFACE_STATUSES = new Set(["default_promoted", "opt_in_promoted"]);
const OPEN_SURFACE_STATUSES = new Set(["planned", "debug_poc", "provider_poc"]);
const DEFERRED_SURFACE_STATUSES = new Set(["design_locked"]);
const REJECTED_SURFACE_STATUSES = new Set(["rejected"]);
const OPEN_BLOCKER_STATUSES = new Set(["open", "decision_required"]);
const DEFERRED_BLOCKER_STATUSES = new Set(["optional", "waiting_for_android_artifacts"]);
const REJECTED_BLOCKER_STATUSES = new Set(["permanently_unsupported", "intentionally_unsupported"]);
const PHASE13_BLOCKER_NOTES = new Map([
  [
    "raw_network",
    "P13 keeps raw_network rejected for v1.1 while P13-50 through P13-56 preserve a gated future relaxed-capability evidence trail without granting release authority.",
  ],
]);
const FUTURE_PROMOTION_REPORT_TASKS = new Set([
  "P13-17",
  "P13-18",
  "P13-19",
  "P13-20",
  "P13-21",
  "P13-22",
  "P13-25",
  "P13-26",
  "P13-34",
]);

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  reportsReadme: "docs/nodejs/reports/phase13/README.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const docsOutput = path.resolve(repoRoot, options.docsOutput || DEFAULT_DOCS_OUTPUT);
  const reportRun = resolveReportRun(options);
  const files = loadFiles(repoRoot);
  const declaredReportContracts = extractDeclaredReportContracts(files.phase13GradleGate.text);
  const reportSet = loadGeneratedReports(reportDir, reportRun.minimumReportMtimeMs);
  const reportCoverage = buildReportCoverage(reportSet, declaredReportContracts, reportRun);
  const generatedReports = reportSet.reports;
  const summary = buildSummary(files, generatedReports, reportCoverage);
  const markdown = renderMarkdown(summary);
  const checks = buildChecks(files, generatedReports, summary, markdown, reportCoverage);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-phase13-summary-v1",
    generatedAt: new Date().toISOString(),
    gate: "generateNodePhase13Summary",
    task: "P13-57",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    generatedReportDir: path.relative(repoRoot, reportDir).replace(/\\/g, "/"),
    ...summary,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : summary.releaseGate.ready ? "ready" : "partial",
      phase13TaskComplete: summary.releaseGate.ready,
      taskCoverage: checkById(checks, "task-coverage"),
      promotedSurfaceEvidence: checkById(checks, "promoted-surface-evidence"),
      openBlockersNotCompleted: checkById(checks, "open-blockers-not-completed"),
      zeroBlockerFinalRequirement: checkById(checks, "zero-blocker-final-requirement"),
      profileTruthPresent: checkById(checks, "profile-truth-report"),
      conformanceReportsPresent: checkById(checks, "conformance-reports"),
      canonicalReportSetComplete: checkById(checks, "canonical-report-set"),
      allTaskVerifiersZeroBlockers: reportCoverage.ready &&
        generatedReports.every((report) => report.policyViolations === 0 && report.blockers === 0),
      releaseBlockingReportsClear: summary.releaseGate.releaseBlockingReports.length === 0,
      finalBlockerClosable: summary.releaseGate.openFinalBlockers.length === 0,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(path.dirname(docsOutput), { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), markdown);
  fs.writeFileSync(docsOutput, markdown);
  console.log("Phase 13 summary generation: " + report.decision.status);
  console.log("Phase 13 summary policy violations: " + policyViolations.length);
  console.log("Phase 13 summary open blockers: " + summary.blockerCounts.open);
  console.log("Phase 13 summary canonical reports: " +
    `${reportCoverage.present}/${reportCoverage.expected}`);
  console.log("Phase 13 summary missing/invalid/stale reports: " +
    `${reportCoverage.missingReports.length}/${reportCoverage.invalidReports.length}/${reportCoverage.staleReports.length}`);
  console.log("Phase 13 summary release-blocking reports: " + summary.releaseGate.releaseBlockingReports.length);
  console.log("Phase 13 summary future promotion reports: " + summary.releaseGate.futurePromotionReports.length);
  if (policyViolations.length > 0 || (options.failOnOpenBlockers && !summary.releaseGate.ready)) {
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

function loadGeneratedReports(reportDir, minimumReportMtimeMs) {
  const expectedNames = REQUIRED_REPORT_BASENAMES.map((basename) => `${basename}.json`);
  const availableNames = fs.existsSync(reportDir) ? fs.readdirSync(reportDir) : [];
  const availableSet = new Set(availableNames);
  const missingReports = expectedNames.filter((name) => !availableSet.has(name));
  const unexpectedReports = availableNames
    .filter((name) => /^(?:phase13-.+|node-builtin-parity)\.json$/.test(name))
    .filter((name) => !ALLOWED_SUPPLEMENTAL_REPORT_NAMES.has(name) && !expectedNames.includes(name))
    .sort();
  const invalidReports = [];
  const staleReports = [];
  const reports = [];

  for (const name of expectedNames.filter((candidate) => availableSet.has(candidate))) {
    const file = path.join(reportDir, name);
    const basename = name.replace(/\.json$/, "");
    const contract = REPORT_CONTRACT_BY_NAME.get(name);
    const raw = read(file);
    const parsed = parseJson(raw);
    const invalidReasons = [];
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      invalidReasons.push("invalid_json_object");
    } else {
      if (typeof parsed.schema !== "string" || !parsed.schema) {
        invalidReasons.push("missing_schema");
      } else if (parsed.schema !== contract.schema) {
        invalidReasons.push("unexpected_schema");
      }
      if (typeof parsed.gate !== "string" || !parsed.gate) {
        invalidReasons.push("missing_gate");
      } else if (parsed.gate !== contract.gate) {
        invalidReasons.push("unexpected_gate");
      }
      for (const field of contract.requiredArrays) {
        if (!Array.isArray(parsed[field])) {
          invalidReasons.push(`missing_${snakeCase(field)}_array`);
        }
      }
      if (!parsed.decision || typeof parsed.decision !== "object" || typeof parsed.decision.status !== "string") {
        invalidReasons.push("missing_decision_status");
      } else if (!contract.allowedStatuses.includes(parsed.decision.status)) {
        invalidReasons.push("unexpected_decision_status");
      }
      if (contract.task === null) {
        if (parsed.task !== undefined && parsed.task !== null) {
          invalidReasons.push("unexpected_task");
        }
      } else if (parsed.task !== contract.task) {
        invalidReasons.push("unexpected_task");
      }
      for (const field of contract.requiredDecisionBooleans) {
        if (!parsed.decision || typeof parsed.decision[field] !== "boolean") {
          invalidReasons.push(`missing_${snakeCase(field)}_boolean`);
        }
      }
      const decisionStatus = parsed.decision && parsed.decision.status;
      const blockerCount = Array.isArray(parsed.blockers) ? parsed.blockers.length : 0;
      const policyViolationCount = Array.isArray(parsed.policyViolations) ? parsed.policyViolations.length : 0;
      if (decisionStatus === "partial" && blockerCount + policyViolationCount === 0) {
        invalidReasons.push("partial_without_findings");
      }
      if ((decisionStatus === "ready" || decisionStatus === "passed") &&
        blockerCount + policyViolationCount > 0) {
        invalidReasons.push("successful_status_with_findings");
      }
      if (contract.task !== null && (decisionStatus === "ready" || decisionStatus === "partial")) {
        const expectedComplete = decisionStatus === "ready";
        if (parsed.decision.phase13TaskComplete !== expectedComplete) {
          invalidReasons.push("task_completion_status_mismatch");
        }
        if (parsed.decision.finalBlockerClosable !== expectedComplete) {
          invalidReasons.push("blocker_closure_status_mismatch");
        }
      }
      if (!Number.isFinite(Date.parse(parsed.generatedAt))) {
        invalidReasons.push("invalid_generated_at");
      }
    }
    if (invalidReasons.length > 0) {
      invalidReports.push({ name, reasons: invalidReasons });
    }
    const stat = fs.statSync(file);
    const generatedAtMs = parsed ? Date.parse(parsed.generatedAt) : NaN;
    if (minimumReportMtimeMs !== null &&
      (stat.mtimeMs < minimumReportMtimeMs || !Number.isFinite(generatedAtMs) || generatedAtMs < minimumReportMtimeMs)) {
      staleReports.push({
        name,
        mtimeMs: stat.mtimeMs,
        generatedAt: parsed && parsed.generatedAt ? parsed.generatedAt : null,
      });
    }
    const decision = parsed && parsed.decision ? parsed.decision : {};
    reports.push({
      name,
      basename,
      gate: parsed ? parsed.gate || null : null,
      task: parsed ? parsed.task || null : null,
      schema: parsed ? parsed.schema || null : null,
      status: decision.status || parsed && parsed.status || "unknown",
      policyViolations: Array.isArray(parsed && parsed.policyViolations) ? parsed.policyViolations.length : 0,
      blockers: Array.isArray(parsed && parsed.blockers) ? parsed.blockers.length : 0,
      generatedAt: parsed && parsed.generatedAt ? parsed.generatedAt : null,
      mtimeMs: stat.mtimeMs,
      shapeValid: invalidReasons.length === 0,
    });
  }

  return {
    reports,
    expectedNames,
    missingReports,
    invalidReports,
    staleReports,
    unexpectedReports,
    minimumReportMtimeMs,
  };
}

function buildReportCoverage(reportSet, declaredReportContracts, reportRun) {
  const declaredReports = declaredReportContracts.contracts.map((contract) => contract.name);
  const declaredSet = new Set(declaredReports);
  const expectedSet = new Set(reportSet.expectedNames);
  const missingGradleDeclarations = reportSet.expectedNames.filter((name) => !declaredSet.has(name));
  const unexpectedGradleDeclarations = declaredReports.filter((name) => !expectedSet.has(name));
  const mismatchedGradleDeclarations = REPORT_CONTRACTS.flatMap((expected) => {
    const name = `${expected.basename}.json`;
    const matches = declaredReportContracts.contracts.filter((declared) => declared.name === name);
    if (matches.length !== 1 || matches[0].gate === expected.gate) {
      return [];
    }
    return [{ name, expectedGate: expected.gate, declaredGate: matches[0].gate }];
  });
  const ready =
    reportSet.reports.length === reportSet.expectedNames.length &&
    reportSet.missingReports.length === 0 &&
    reportSet.invalidReports.length === 0 &&
    reportSet.staleReports.length === 0 &&
    reportSet.unexpectedReports.length === 0 &&
    missingGradleDeclarations.length === 0 &&
    unexpectedGradleDeclarations.length === 0 &&
    mismatchedGradleDeclarations.length === 0 &&
    declaredReportContracts.duplicateReports.length === 0;
  return {
    ready,
    expected: reportSet.expectedNames.length,
    present: reportSet.reports.length,
    expectedReports: reportSet.expectedNames,
    declaredReports,
    missingReports: reportSet.missingReports,
    invalidReports: reportSet.invalidReports,
    staleReports: reportSet.staleReports,
    unexpectedReports: reportSet.unexpectedReports,
    missingGradleDeclarations,
    unexpectedGradleDeclarations,
    mismatchedGradleDeclarations,
    duplicateGradleDeclarations: declaredReportContracts.duplicateReports,
    minimumReportMtimeMs: reportSet.minimumReportMtimeMs,
    reportRunMarker: reportRun.marker,
  };
}

function extractDeclaredReportContracts(text) {
  const taskMatches = [...text.matchAll(/tasks\.register<Exec>\("([^"]+)"\)\s*\{/g)];
  const contracts = [];
  for (let index = 0; index < taskMatches.length; index += 1) {
    const taskMatch = taskMatches[index];
    const gate = taskMatch[1];
    if (gate === "generateNodePhase13Summary") {
      continue;
    }
    const bodyEnd = index + 1 < taskMatches.length ? taskMatches[index + 1].index : text.length;
    const body = text.slice(taskMatch.index, bodyEnd);
    const outputRegex = /it\.file\("([^/"\\]+\.json)"\)/g;
    let outputMatch;
    while ((outputMatch = outputRegex.exec(body)) !== null) {
      contracts.push({ name: outputMatch[1], gate });
    }
  }
  const counts = countBy(contracts, (contract) => contract.name);
  const duplicateReports = Object.entries(counts)
    .filter(([, count]) => count > 1)
    .map(([name]) => name)
    .sort();
  return {
    contracts: contracts.sort((left, right) => left.name.localeCompare(right.name)),
    duplicateReports,
  };
}

function buildSummary(files, generatedReports, reportCoverage) {
  const manifest = JSON.parse(files.surfaceManifest.text);
  const blockerRegistry = JSON.parse(files.blockerRegistry.text);
  const surfaces = manifest.surfaces || [];
  const tasks = parseTasks(files.phase13Tasks.text, surfaces);
  const blockers = parseBlockers(blockerRegistry.blockers || []);
  const promotedSurfaces = surfaces.filter((surface) => COMPLETED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const openSurfaces = surfaces.filter((surface) => OPEN_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const deferredSurfaces = surfaces.filter((surface) => DEFERRED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const rejectedSurfaces = surfaces.filter((surface) => REJECTED_SURFACE_STATUSES.has(surface.status)).map(surfaceSummary);
  const profileTruthReport = generatedReports.find((report) =>
    report.basename === "phase13-capability-truth" && report.shapeValid) || null;
  const conformanceReports = generatedReports.filter((report) =>
    report.basename.includes("conformance") && report.shapeValid);
  const reportStatusCounts = countBy(generatedReports, (report) => report.status);
  const generatedReportCounts = {
    total: generatedReports.length,
    byStatus: reportStatusCounts,
    withPolicyViolations: generatedReports.filter((report) => report.policyViolations > 0).length,
    withBlockers: generatedReports.filter((report) => report.blockers > 0).length,
  };
  const releaseGate = buildReleaseGate({
    tasks,
    blockers,
    generatedReports,
    profileTruthReport,
    conformanceReports,
    reportCoverageReady: reportCoverage.ready,
  });
  return {
    phase: manifest.phase,
    profileVocabulary: manifest.profileVocabulary || [],
    statusVocabulary: manifest.statusVocabulary || [],
    taskCounts: countTasks(tasks),
    tasks,
    surfaceCounts: {
      total: surfaces.length,
      completed: promotedSurfaces.length,
      open: openSurfaces.length,
      deferred: deferredSurfaces.length,
      rejected: rejectedSurfaces.length,
      byStatus: countBy(surfaces, (surface) => surface.status),
    },
    promotedSurfaces,
    openSurfaces,
    deferredSurfaces,
    rejectedSurfaces,
    blockerCounts: countBlockers(blockers),
    blockers,
    profileTruthReport,
    conformanceReports,
    generatedReports,
    generatedReportCounts,
    reportCoverage,
    releaseGate,
  };
}

function parseTasks(text, surfaces) {
  const taskById = new Map();
  const regex = /^- \[([ x])\]\s+(P13-\d{2}):\s+(.+)$/gm;
  let match;
  while ((match = regex.exec(text)) !== null) {
    if (!taskById.has(match[2])) {
      taskById.set(match[2], {
        id: match[2],
        checked: match[1] === "x",
        title: match[3].trim(),
      });
    }
  }
  const surfaceByTask = new Map();
  for (const surface of surfaces) {
    for (const task of surface.tasks || []) {
      surfaceByTask.set(task, surface.id);
    }
  }
  return REQUIRED_TASKS.map((taskId) => {
    const task = taskById.get(taskId);
    return {
      id: taskId,
      status: task && task.checked ? "completed" : "open",
      title: task ? task.title : "missing from checklist",
      surface: surfaceByTask.get(taskId) || null,
    };
  });
}

function countTasks(tasks) {
  const byStatus = countBy(tasks, (task) => task.status);
  return {
    total: tasks.length,
    completed: byStatus.completed || 0,
    open: byStatus.open || 0,
    deferred: byStatus.deferred || 0,
    rejected: byStatus.rejected || 0,
    byStatus,
  };
}

function parseBlockers(blockers) {
  return blockers.map((blocker) => {
    let summaryStatus = "open";
    const phase13Note = PHASE13_BLOCKER_NOTES.get(blocker.id) || "";
    if (blocker.status === "closed") {
      summaryStatus = "completed";
    } else if (DEFERRED_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "deferred";
    } else if (REJECTED_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "rejected";
    } else if (OPEN_BLOCKER_STATUSES.has(blocker.status)) {
      summaryStatus = "open";
    }
    return {
      id: blocker.id,
      classification: blocker.classification,
      status: blocker.status,
      summaryStatus,
      phase13Note,
      currentEvidence: blocker.currentEvidence,
      requiredResolution: blocker.requiredResolution,
    };
  });
}

function countBlockers(blockers) {
  const bySummaryStatus = countBy(blockers, (blocker) => blocker.summaryStatus);
  const byRegistryStatus = countBy(blockers, (blocker) => blocker.status);
  return {
    total: blockers.length,
    completed: bySummaryStatus.completed || 0,
    open: bySummaryStatus.open || 0,
    deferred: bySummaryStatus.deferred || 0,
    rejected: bySummaryStatus.rejected || 0,
    bySummaryStatus,
    byRegistryStatus,
  };
}

function surfaceSummary(surface) {
  return {
    id: surface.id,
    status: surface.status,
    profiles: surface.profiles || [],
    tasks: surface.tasks || [],
    risk: surface.risk,
    evidence: surface.evidence || [],
    summary: surface.summary || "",
  };
}

function buildReleaseGate({
  tasks,
  blockers,
  generatedReports,
  profileTruthReport,
  conformanceReports,
  reportCoverageReady,
}) {
  const openFinalBlockers = blockers.filter((blocker) => blocker.summaryStatus === "open");
  const requiredInternalOpen = blockers.filter((blocker) =>
    blocker.classification === "required_internal" && blocker.summaryStatus !== "completed");
  const reportsWithPolicyViolations = generatedReports.filter((report) => report.policyViolations > 0);
  const reportsWithBlockers = generatedReports.filter((report) => report.blockers > 0);
  const futurePromotionReports = reportsWithBlockers.filter((report) =>
    FUTURE_PROMOTION_REPORT_TASKS.has(report.task));
  const releaseBlockingReports = reportsWithBlockers.filter((report) =>
    !FUTURE_PROMOTION_REPORT_TASKS.has(report.task));
  const phase13SummaryTask = tasks.find((task) => task.id === "P13-57") || null;
  const profileTruthReady = profileTruthReport &&
    profileTruthReport.status === "passed" &&
    profileTruthReport.gate === "generateNodePhase13CapabilityTruth";
  const conformanceReportsReady = conformanceReports.length >= 1 &&
    conformanceReports.some((report) => report.basename === "phase13-stdlib-conformance-decision");
  const ready =
    phase13SummaryTask &&
    phase13SummaryTask.status === "completed" &&
    openFinalBlockers.length === 0 &&
    requiredInternalOpen.length === 0 &&
    reportsWithPolicyViolations.length === 0 &&
    releaseBlockingReports.length === 0 &&
    profileTruthReady &&
    conformanceReportsReady &&
    reportCoverageReady;
  return {
    status: ready ? "ready" : "partial",
    ready: Boolean(ready),
    phase13SummaryTaskComplete: Boolean(phase13SummaryTask && phase13SummaryTask.status === "completed"),
    openFinalBlockers,
    requiredInternalOpen,
    reportsWithPolicyViolations,
    reportsWithBlockers,
    releaseBlockingReports,
    futurePromotionReports,
    futurePromotionReportTasks: futurePromotionReports.map((report) => report.task).sort(),
    futurePromotionReportNames: futurePromotionReports.map((report) => report.name).sort(),
    profileTruthReady: Boolean(profileTruthReady),
    conformanceReportsReady,
    reportCoverageReady,
  };
}

function buildChecks(files, generatedReports, summary, markdown, reportCoverage) {
  const openBlockersMarkedCompleted = summary.blockers.filter((blocker) =>
    OPEN_BLOCKER_STATUSES.has(blocker.status) && blocker.summaryStatus === "completed");
  const missingTaskSurface = summary.tasks.filter((task) => !task.surface);
  const promotedWithoutEvidence = summary.promotedSurfaces.filter((surface) => surface.evidence.length === 0);
  const docsFinalSurface = [...summary.openSurfaces, ...summary.deferredSurfaces, ...summary.promotedSurfaces]
    .find((surface) => surface.id === "docs.examples.final_gate");
  const reportBasenames = new Set(generatedReports.map((report) => report.basename));
  const p13SummaryTask = summary.tasks.find((task) => task.id === "P13-57");
  const requiredInternalOpen = summary.blockers.filter((blocker) =>
    blocker.classification === "required_internal" && blocker.summaryStatus !== "completed");
  return [
    check(
      "canonical-report-set",
      reportCoverage.ready,
      `Summary must consume the complete fresh canonical set of ${REQUIRED_REPORT_BASENAMES.length} valid Phase 13 JSON reports declared by Gradle.`,
    ),
    check(
      "task-coverage",
      summary.tasks.length === REQUIRED_TASKS.length &&
        summary.tasks.every((task) => REQUIRED_TASKS.includes(task.id)) &&
        missingTaskSurface.length === 0 &&
        p13SummaryTask &&
        p13SummaryTask.status === "completed",
      "Summary must enumerate P13-00 through P13-57, map every task to a surface, and mark P13-57 completed once the v1.1 closeout gate is ready.",
    ),
    check(
      "status-vocabulary",
      markdown.includes("completed") &&
        markdown.includes("open") &&
        markdown.includes("deferred") &&
        markdown.includes("rejected") &&
        summary.surfaceCounts.open > 0 &&
        summary.taskCounts.open > 0 &&
        summary.blockerCounts.open === 0 &&
        summary.blockerCounts.rejected > 0,
      "Generated summary must list completed/open/deferred/rejected status categories without collapsing future promotion work into release blockers.",
    ),
    check(
      "promoted-surface-evidence",
      promotedWithoutEvidence.length === 0 &&
        summary.promotedSurfaces.some((surface) => surface.id === "phase13.baseline") &&
        summary.promotedSurfaces.some((surface) => surface.id === "phase13.surface_manifest"),
      "Every promoted Phase 13 surface must include evidence.",
    ),
    check(
      "open-blockers-not-completed",
      openBlockersMarkedCompleted.length === 0 &&
        requiredInternalOpen.length === 0 &&
        summary.blockers.some((blocker) => blocker.id === "crash_evidence_automation" && blocker.summaryStatus === "completed") &&
        summary.blockers.some((blocker) => blocker.id === "full_heavy_resource_stress" && blocker.summaryStatus === "completed") &&
        summary.blockers.some((blocker) => blocker.id === "worker_threads" && blocker.summaryStatus === "rejected") &&
        summary.blockers.some((blocker) =>
          blocker.id === "raw_network" &&
          blocker.status === "permanently_unsupported" &&
          blocker.summaryStatus === "rejected" &&
          blocker.phase13Note.includes("v1.1")),
      "Open and decision-required blockers must remain visible while closed required blockers and explicit v1.1 rejections are not counted as open release blockers.",
    ),
    check(
      "zero-blocker-final-requirement",
      summary.releaseGate.ready &&
        summary.releaseGate.releaseBlockingReports.length === 0 &&
        summary.generatedReportCounts.withPolicyViolations === 0 &&
        markdown.includes("- Release-blocking reports: 0") &&
        markdown.includes(`- Future promotion reports with blockers: ${summary.releaseGate.futurePromotionReports.length}`),
      "Summary final completion must require zero open final blockers, zero policy violations, and zero release-blocking report blockers while listing future promotion backlog separately.",
    ),
    check(
      "profile-truth-report",
      summary.profileTruthReport &&
        summary.profileTruthReport.status === "passed" &&
        summary.profileTruthReport.gate === "generateNodePhase13CapabilityTruth",
      "Summary must include the Phase 13 profile truth report.",
    ),
    check(
      "conformance-reports",
      summary.conformanceReports.length >= 1 &&
        reportBasenames.has("phase13-stdlib-conformance-decision"),
      "Summary must include at least the Phase 13 stdlib conformance report.",
    ),
    check(
      "docs-final-surface",
      docsFinalSurface &&
        docsFinalSurface.evidence.some((entry) => entry.includes("PHASE13_SUMMARY.md")) &&
        docsFinalSurface.evidence.some((entry) => entry.includes("phase13-summary")),
      "`docs.examples.final_gate` must record the Phase 13 summary evidence.",
    ),
    check(
      "docs-wiring",
      files.phase13Tasks.text.includes("P13-57 closeout landing") &&
        files.phase13Tasks.text.includes("generate-phase13-summary.js") &&
        files.phase13GradleGate.text.includes("generateNodePhase13Summary") &&
        files.testingDoc.text.includes("generateNodePhase13Summary") &&
        files.ciGatesDoc.text.includes("generateNodePhase13Summary") &&
        files.capabilityMatrix.text.includes("P13-57 Phase 13 summary generator") &&
        files.reportsReadme.text.includes("PHASE13_SUMMARY.md"),
      "Checklist, Gradle, Testing, CI, release matrix, and Phase 13 README must reference the P13-57 summary gate.",
    ),
    check(
      "markdown-sections",
      includesAll(markdown, [
        "# Phase 13 Summary",
        "## Task Checklist",
        "## Surface Status",
        "## Final Blockers",
        "## Profile Truth And Conformance Reports",
        "## Generated Phase 13 Reports",
        "## Verification",
      ]),
      "Generated markdown must include task, surface, blocker, profile truth, conformance, report, and verification sections.",
    ),
  ];
}

function renderMarkdown(summary) {
  const lines = [
    "# Phase 13 Summary",
    "",
    `Status: P13-57 v1.1 closeout gate is \`${summary.releaseGate.status}\`.`,
    "",
    "This summary is generated from the Phase 13 task checklist, Phase 13 surface manifest, final blocker registry, profile truth report, and generated Phase 13 conformance reports. Future promotion blockers remain visible as backlog and are not counted as release blockers.",
    "",
    "## Release Gate",
    "",
    `- Status: \`${summary.releaseGate.status}\``,
    `- P13-57 task complete: ${summary.releaseGate.phase13SummaryTaskComplete}`,
    `- Open final blockers: ${summary.releaseGate.openFinalBlockers.length}`,
    `- Required internal blockers still open: ${summary.releaseGate.requiredInternalOpen.length}`,
    `- Release-blocking reports: ${summary.releaseGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.releaseGate.futurePromotionReports.length}`,
    `- Reports with policy violations: ${summary.releaseGate.reportsWithPolicyViolations.length}`,
    `- Profile truth ready: ${summary.releaseGate.profileTruthReady}`,
    `- Conformance reports ready: ${summary.releaseGate.conformanceReportsReady}`,
    `- Canonical report set ready: ${summary.releaseGate.reportCoverageReady}`,
    "",
    "## Task Checklist",
    "",
    `- Total tasks: ${summary.taskCounts.total}`,
    `- Completed tasks: ${summary.taskCounts.completed}`,
    `- Open tasks: ${summary.taskCounts.open}`,
    `- Deferred tasks: ${summary.taskCounts.deferred}`,
    `- Rejected tasks: ${summary.taskCounts.rejected}`,
    "",
    "| Task | Status | Surface | Title |",
    "| --- | --- | --- | --- |",
  ];
  for (const task of summary.tasks) {
    lines.push(`| \`${task.id}\` | \`${task.status}\` | \`${task.surface || "missing"}\` | ${escapeTable(task.title)} |`);
  }

  lines.push(
    "",
    "## Surface Status",
    "",
    `- Total surfaces: ${summary.surfaceCounts.total}`,
    `- Completed surfaces: ${summary.surfaceCounts.completed}`,
    `- Open surfaces: ${summary.surfaceCounts.open}`,
    `- Deferred surfaces: ${summary.surfaceCounts.deferred}`,
    `- Rejected surfaces: ${summary.surfaceCounts.rejected}`,
    "",
    "| Manifest status | Count |",
    "| --- | ---: |",
  );
  for (const status of Object.keys(summary.surfaceCounts.byStatus).sort()) {
    lines.push(`| \`${status}\` | ${summary.surfaceCounts.byStatus[status]} |`);
  }

  pushSurfaceSection(lines, "Promoted Surfaces", summary.promotedSurfaces);
  pushSurfaceSection(lines, "Open Surfaces", summary.openSurfaces);
  pushSurfaceSection(lines, "Deferred Surfaces", summary.deferredSurfaces);
  pushSurfaceSection(lines, "Rejected Surfaces", summary.rejectedSurfaces);

  lines.push(
    "",
    "## Final Blockers",
    "",
    `- Total blockers: ${summary.blockerCounts.total}`,
    `- Completed blockers: ${summary.blockerCounts.completed}`,
    `- Open blockers: ${summary.blockerCounts.open}`,
    `- Deferred blockers: ${summary.blockerCounts.deferred}`,
    `- Rejected blockers: ${summary.blockerCounts.rejected}`,
    "",
    "| Blocker | Summary status | Registry status | Classification | Required resolution |",
    "| --- | --- | --- | --- | --- |",
  );
  for (const blocker of summary.blockers) {
    const note = blocker.phase13Note ? ` Phase 13 note: ${blocker.phase13Note}` : "";
    lines.push(`| \`${blocker.id}\` | \`${blocker.summaryStatus}\` | \`${blocker.status}\` | \`${blocker.classification}\` | ${escapeTable(blocker.requiredResolution + note)} |`);
  }

  lines.push(
    "",
    "## Profile Truth And Conformance Reports",
    "",
    summary.profileTruthReport
      ? `- Profile truth report: \`${summary.profileTruthReport.name}\` via \`${summary.profileTruthReport.gate}\` with status \`${summary.profileTruthReport.status}\`.`
      : "- Profile truth report: missing.",
    `- Conformance reports: ${summary.conformanceReports.length}`,
    "",
    "| Report | Gate | Status | Policy violations | Blockers |",
    "| --- | --- | --- | ---: | ---: |",
  );
  for (const report of summary.conformanceReports) {
    lines.push(`| \`${report.name}\` | \`${report.gate || "-"}\` | \`${report.status}\` | ${report.policyViolations} | ${report.blockers} |`);
  }

  lines.push(
    "",
    "## Generated Phase 13 Reports",
    "",
    `- Total reports: ${summary.generatedReportCounts.total}`,
    `- Canonical reports expected: ${summary.reportCoverage.expected}`,
    `- Canonical reports present: ${summary.reportCoverage.present}`,
    `- Missing canonical reports: ${summary.reportCoverage.missingReports.length}`,
    `- Invalid canonical reports: ${summary.reportCoverage.invalidReports.length}`,
    `- Stale canonical reports: ${summary.reportCoverage.staleReports.length}`,
    `- Unexpected canonical-shaped reports: ${summary.reportCoverage.unexpectedReports.length}`,
    `- Reports with policy violations: ${summary.generatedReportCounts.withPolicyViolations}`,
    `- Reports with blockers: ${summary.generatedReportCounts.withBlockers}`,
    `- Release-blocking reports: ${summary.releaseGate.releaseBlockingReports.length}`,
    `- Future promotion reports with blockers: ${summary.releaseGate.futurePromotionReports.length}`,
    "",
    "| Report | Gate | Task | Status | Policy violations | Blockers |",
    "| --- | --- | --- | --- | ---: | ---: |",
  );
  for (const report of summary.generatedReports) {
    lines.push(`| \`${report.name}\` | \`${report.gate || "-"}\` | \`${report.task || "-"}\` | \`${report.status}\` | ${report.policyViolations} | ${report.blockers} |`);
  }

  lines.push(
    "",
    "## Verification",
    "",
    "- Generator: `tools/nodejs/phase13/generate-phase13-summary.js`",
    "- Gradle: `:app:generateNodePhase13Summary`",
    "- Reports: `build/reports/nodejs/phase13-summary.json` and `build/reports/nodejs/phase13-summary.md`",
    "- Canonical docs output: `docs/nodejs/reports/phase13/PHASE13_SUMMARY.md`",
    "- Final closeout requires the complete fresh canonical report set, zero open final blockers, zero policy violations, and zero release-blocking report blockers. Future promotion reports remain listed as backlog.",
  );
  return lines.join("\n") + "\n";
}

function pushSurfaceSection(lines, title, surfaces) {
  lines.push("", `## ${title}`, "");
  if (surfaces.length === 0) {
    lines.push("- None.");
    return;
  }
  lines.push("| Surface | Status | Risk | Tasks | Evidence | Summary |", "| --- | --- | --- | --- | --- | --- |");
  for (const surface of surfaces) {
    const evidence = surface.evidence.length > 0 ? `${surface.evidence.length} item(s)` : "missing";
    lines.push(`| \`${surface.id}\` | \`${surface.status}\` | \`${surface.risk}\` | ${surface.tasks.map((task) => `\`${task}\``).join(", ")} | ${evidence} | ${escapeTable(surface.summary)} |`);
  }
}

function countBy(values, selector) {
  const output = {};
  for (const value of values) {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  }
  return output;
}

function snakeCase(value) {
  return String(value).replace(/([a-z0-9])([A-Z])/g, "$1_$2").toLowerCase();
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch (_) {
    return null;
  }
}

function escapeTable(value) {
  return String(value || "").replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function resolveReportRun(options) {
  if (options.reportRunMarker && options.minimumReportMtimeMs !== undefined) {
    throw new Error("Use either --report-run-marker or --minimum-report-mtime-ms, not both.");
  }
  if (!options.reportRunMarker) {
    return {
      minimumReportMtimeMs: parseMinimumReportMtime(options.minimumReportMtimeMs),
      marker: null,
    };
  }
  const markerFile = path.resolve(options.reportRunMarker);
  const marker = parseJson(read(markerFile));
  if (!marker || marker.schema !== "autojs6-node-phase13-summary-run-v1" ||
    !Number.isSafeInteger(marker.startedAtMs) || marker.startedAtMs < 0 ||
    !Number.isFinite(Date.parse(marker.startedAt)) || Date.parse(marker.startedAt) !== marker.startedAtMs) {
    throw new Error(`Invalid Phase 13 summary run marker: ${markerFile}`);
  }
  return {
    minimumReportMtimeMs: marker.startedAtMs,
    marker: {
      file: markerFile,
      schema: marker.schema,
      startedAt: marker.startedAt,
      startedAtMs: marker.startedAtMs,
    },
  };
}

function parseMinimumReportMtime(value) {
  if (value === undefined) {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0) {
    throw new Error("--minimum-report-mtime-ms must be a non-negative integer.");
  }
  return parsed;
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-open-blockers"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-phase13-summary.js [--repo-root dir] [--report-dir dir] [--docs-output file] [--report-run-marker file | --minimum-report-mtime-ms epoch] [--fail-on-open-blockers]");
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
