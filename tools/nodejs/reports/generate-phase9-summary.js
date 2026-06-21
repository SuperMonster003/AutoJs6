#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const HOST_REPORTS = [
  { id: "native-symbol-usage", path: "native-symbol-usage.json", required: true },
  { id: "runtime-adapter-abi-v1", path: "runtime-adapter-abi-v1.json", required: true },
  { id: "runtime-adapter-export", path: "runtime-adapter-export.json", required: true },
  { id: "runtime-build-plan", path: "runtime-build-plan.json", required: true },
  { id: "node-2417-upgrade-gate", path: "node-24.17-upgrade-gate.json", required: false },
  { id: "native-16kb-alignment", path: "native-16kb-alignment.json", required: true },
  { id: "runtime-artifact-manifest", path: "runtime-artifact-manifest.json", required: true },
  { id: "runtime-artifact-integrity", path: "runtime-artifact-integrity.json", required: true },
  { id: "runtime-distribution-policy", path: "runtime-distribution-policy.json", required: true },
  { id: "runtime-artifact-gate", path: "runtime-artifact-gate.json", required: true },
  { id: "phase9-performance-policy", path: "phase9-performance-policy.json", required: true },
  { id: "phase9-inspector-policy", path: "phase9-inspector-policy.json", required: true },
  { id: "phase9-isolation-policy", path: "phase9-isolation-policy.json", required: true },
  { id: "phase9-wasm-wasi-policy", path: "phase9-wasm-wasi-policy.json", required: true },
  { id: "phase9-plugin-policy", path: "phase9-plugin-policy.json", required: true },
  { id: "phase9-native-crash-pipeline", path: "phase9-native-crash-pipeline.json", required: true },
  { id: "phase9-chaos-security-policy", path: "phase9-chaos-security-policy.json", required: true },
];

const EXPECTED_DEVICE_CLASSES = [
  "org.autojs.autojs.engine.NodeWasmWasiInstrumentationTest",
  "org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest",
];

const PHASE9_EXAMPLES = [
  "node-runtime-info",
  "startup-snapshot",
  "heap-snapshot",
  "multi-instance",
  "sandboxed-script",
  "wasm-basic",
  "wasi-scoped-fs",
  "wasm-worker",
  "wasm-plugin",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const appReportDir = path.resolve(options.appReportDir || path.join(repoRoot, "app/build/reports/nodejs"));
  const androidResultsDir = path.resolve(
    options.androidResultsDir || path.join(
      repoRoot,
      "app/build/outputs/androidTest-results/connected/debug/flavors/app",
    ),
  );

  const hostReports = HOST_REPORTS.map((entry) => readHostReport(reportDir, entry, repoRoot));
  const hostReportsById = Object.fromEntries(hostReports.map((entry) => [entry.id, entry]));
  const rawHostJsonById = Object.fromEntries(hostReports.map((entry) => [entry.id, entry.json || null]));
  const examples = summarizeExamples(appReportDir, repoRoot);
  const npmCorpus = summarizeNpmCorpus(repoRoot);
  const deviceGate = summarizeDeviceGate(androidResultsDir, repoRoot);
  const runtimeComparison = summarizeRuntimeComparison(rawHostJsonById);
  const pageSizeValidation = summarizePageSizes(rawHostJsonById);
  const startupPerformance = summarizeStartupPerformance(rawHostJsonById, appReportDir, repoRoot);
  const capabilityMatrix = buildCapabilityMatrix(hostReportsById, examples, npmCorpus);
  const remainingBlockers = buildRemainingBlockers(hostReportsById, runtimeComparison, pageSizeValidation);
  const policyViolations = collectPolicyViolations(hostReports, deviceGate, examples, npmCorpus);

  const summary = {
    schema: "autojs6-node-phase9-summary-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-50 Phase 9 Summary",
    gate: "verifyNodePhase9Gate",
    deviceProfile: options.deviceProfile || null,
    defaultRuntimeSlot: runtimeComparison.defaultRuntimeSlot,
    runtimeComparison,
    pageSizeValidation,
    startupPerformance,
    npmCorpus,
    examples,
    deviceGate,
    hostReports: hostReports.map(stripHostJson),
    capabilityMatrix,
    remainingBlockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : "completed_with_recorded_blockers",
      phaseComplete: policyViolations.length === 0,
      defaultRuntimePromoted: false,
      node2417Promotion: "blocked",
      safeNodeProfilePreserved: true,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-summary", summary, renderMarkdown);
  console.log("Phase 9 summary decision: " + summary.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function readHostReport(reportDir, entry, repoRoot) {
  const file = path.join(reportDir, entry.path);
  if (!fs.existsSync(file)) {
    return {
      id: entry.id,
      path: entry.path,
      required: entry.required,
      available: false,
      status: "missing",
      blockerCount: entry.required ? 1 : 0,
      warningCount: 0,
      blockers: entry.required ? [`missing required report: ${entry.path}`] : [],
      warnings: [],
      json: null,
    };
  }
  const json = readJson(file);
  const sectionStatuses = summarizeSectionStatuses(json);
  const blockers = extractReportIssues(json, "blockers");
  const warnings = extractReportIssues(json, "warnings");
  return {
    id: entry.id,
    path: relativePath(file, repoRoot),
    required: entry.required,
    available: true,
    schema: json.schema || null,
    task: json.task || null,
    status: extractReportStatus(json),
    sectionStatusCounts: sectionStatuses,
    blockerCount: blockers.length,
    warningCount: warnings.length,
    blockers,
    warnings,
    json,
  };
}

function extractReportStatus(json) {
  if (json && json.decision && json.decision.status) {
    return json.decision.status;
  }
  if (json && typeof json.status === "string") {
    return json.status;
  }
  if (json && Array.isArray(json.sections)) {
    const statuses = new Set(json.sections.map((section) => section.status).filter(Boolean));
    if (statuses.size === 1) {
      return Array.from(statuses)[0];
    }
    return statuses.size > 0 ? "mixed" : "unknown";
  }
  return "unknown";
}

function summarizeSectionStatuses(json) {
  const counts = {};
  const sections = Array.isArray(json && json.sections)
    ? json.sections
    : Array.isArray(json && json.reports)
      ? json.reports
      : [];
  for (const section of sections) {
    const status = section.status || "unknown";
    counts[status] = (counts[status] || 0) + 1;
  }
  return counts;
}

function extractReportIssues(json, field) {
  const issues = [];
  if (!json) {
    return issues;
  }
  if (json.decision && Array.isArray(json.decision[field])) {
    issues.push(...json.decision[field].map(issueToString));
  }
  if (Array.isArray(json.sections)) {
    for (const section of json.sections) {
      if (Array.isArray(section[field])) {
        issues.push(...section[field].map((issue) => `${section.id}: ${issueToString(issue)}`));
      }
    }
  }
  if (Array.isArray(json.reports)) {
    for (const report of json.reports) {
      if (Array.isArray(report[field])) {
        issues.push(...report[field].map((issue) => `${report.id}: ${issueToString(issue)}`));
      }
    }
  }
  return unique(issues);
}

function summarizeExamples(appReportDir, repoRoot) {
  const reportFile = path.join(appReportDir, "examples.json");
  const report = fs.existsSync(reportFile) ? readJson(reportFile) : null;
  const examples = Array.isArray(report && report.examples) ? report.examples : [];
  const byName = new Map(examples.map((entry) => [entry.name, entry]));
  const phase9Examples = PHASE9_EXAMPLES.map((name) => byName.get(name)).filter(Boolean);
  const missing = PHASE9_EXAMPLES.filter((name) => !byName.has(name));
  const statusCounts = {};
  for (const example of examples) {
    const status = example.status || "unknown";
    statusCounts[status] = (statusCounts[status] || 0) + 1;
  }
  return {
    report: fs.existsSync(reportFile) ? relativePath(reportFile, repoRoot) : "app/build/reports/nodejs/examples.json",
    available: Boolean(report),
    total: Number(report && report.exampleCount) || examples.length,
    phase8BaselineTotal: 33,
    phase9Added: phase9Examples.length,
    deltaFromPhase8: (Number(report && report.exampleCount) || examples.length) - 33,
    missingPhase9Examples: missing,
    statusCounts,
    phase9Examples: phase9Examples.map((entry) => ({
      name: entry.name,
      status: entry.status || "unknown",
      tags: entry.tags || [],
      mainAppSmoke: Boolean(entry.mainAppSmoke),
      packagedCompatible: Boolean(entry.packagedCompatible),
    })),
  };
}

function summarizeNpmCorpus(repoRoot) {
  const manifestFile = path.join(repoRoot, "app/src/androidTest/assets/npm_compat_real/manifest.json");
  const manifest = fs.existsSync(manifestFile) ? readJson(manifestFile) : null;
  const fixtures = Array.isArray(manifest && manifest.fixtures) ? manifest.fixtures : [];
  return {
    manifest: relativePath(manifestFile, repoRoot),
    available: Boolean(manifest),
    phase8BaselineFixtures: 20,
    phase9Fixtures: fixtures.length,
    deltaFromPhase8: fixtures.length - 20,
    requiredMinimum: 30,
    fixtures: fixtures.map((fixture) => fixture.name).filter(Boolean),
  };
}

function summarizeDeviceGate(androidResultsDir, repoRoot) {
  const xmlFiles = latestXmlFiles(androidResultsDir);
  const parsed = xmlFiles.map(parseAndroidTestXml);
  const devices = unique(parsed.flatMap((entry) => entry.devices));
  const testcases = parsed.flatMap((entry) => entry.testcases);
  const classSummaries = EXPECTED_DEVICE_CLASSES.map((className) => summarizeClass(className, testcases));
  const totals = classSummaries.reduce(
    (acc, entry) => {
      acc.tests += entry.tests;
      acc.failures += entry.failures;
      acc.errors += entry.errors;
      acc.skipped += entry.skipped;
      return acc;
    },
    { tests: 0, failures: 0, errors: 0, skipped: 0 },
  );
  return {
    resultXml: xmlFiles.map((file) => relativePath(file, repoRoot)),
    devices,
    expectedClasses: EXPECTED_DEVICE_CLASSES,
    totals,
    classSummaries,
    missingClasses: classSummaries.filter((entry) => entry.status === "missing").map((entry) => entry.className),
    failedClasses: classSummaries
      .filter((entry) => entry.failures > 0 || entry.errors > 0 || entry.skipped > 0)
      .map((entry) => entry.className),
  };
}

function summarizeClass(className, testcases) {
  const matches = testcases.filter((testcase) => testcase.className === className);
  if (matches.length === 0) {
    return {
      className,
      status: "missing",
      tests: 0,
      failures: 0,
      errors: 0,
      skipped: 0,
      methods: [],
    };
  }
  const failures = matches.filter((testcase) => testcase.status === "failed").length;
  const errors = matches.filter((testcase) => testcase.status === "error").length;
  const skipped = matches.filter((testcase) => testcase.status === "skipped").length;
  return {
    className,
    status: failures > 0 ? "failed" : errors > 0 ? "error" : skipped > 0 ? "skipped" : "passed",
    tests: matches.length,
    failures,
    errors,
    skipped,
    methods: matches.map((testcase) => ({
      name: testcase.methodName,
      status: testcase.status,
      timeSeconds: testcase.timeSeconds,
    })),
  };
}

function latestXmlFiles(androidResultsDir) {
  if (!fs.existsSync(androidResultsDir)) {
    return [];
  }
  const files = fs.readdirSync(androidResultsDir)
    .filter((name) => name.startsWith("TEST-") && name.endsWith(".xml"))
    .map((name) => path.join(androidResultsDir, name))
    .filter((file) => fs.statSync(file).isFile());
  if (files.length === 0) {
    return [];
  }
  const latestMtime = Math.max(...files.map((file) => fs.statSync(file).mtimeMs));
  return files
    .filter((file) => latestMtime - fs.statSync(file).mtimeMs <= 120000)
    .sort();
}

function parseAndroidTestXml(file) {
  const text = fs.readFileSync(file, "utf8");
  const devices = unique(Array.from(text.matchAll(/<property\b([^>]*)\/?>/g))
    .map((match) => parseAttributes(match[1]))
    .filter((attrs) => attrs.name === "device" && attrs.value)
    .map((attrs) => attrs.value));
  const testcases = [];
  const testcasePattern = /<testcase\b([^>]*)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  for (const match of text.matchAll(testcasePattern)) {
    const attrs = parseAttributes(match[1]);
    const body = match[2] || "";
    const status = body.includes("<failure")
      ? "failed"
      : body.includes("<error")
        ? "error"
        : body.includes("<skipped")
          ? "skipped"
          : "passed";
    testcases.push({
      className: attrs.classname || attrs.className || "",
      methodName: attrs.name || "",
      status,
      timeSeconds: attrs.time ? Number(attrs.time) : null,
    });
  }
  return { file, devices, testcases };
}

function parseAttributes(raw) {
  const attrs = {};
  const pattern = /([A-Za-z_:][-A-Za-z0-9_:.]*)=(["'])(.*?)\2/g;
  for (const match of raw.matchAll(pattern)) {
    attrs[match[1]] = decodeXml(match[3]);
  }
  return attrs;
}

function summarizeRuntimeComparison(reports) {
  const buildPlan = reports["runtime-build-plan"];
  const upgradeGate = reports["node-2417-upgrade-gate"];
  const lock = buildPlan && buildPlan.lockSummary || {};
  const target = upgradeGate && upgradeGate.target || {};
  const decision = upgradeGate && upgradeGate.decision || {};
  return {
    currentRuntime: lock.currentVersion || target.currentVersion || "24.5.0",
    candidateRuntime: lock.targetVersion || target.candidateVersion || "24.17.0",
    defaultRuntimeSlot: lock.defaultRuntimeSlot || "node24_5",
    rollbackRuntimeSlot: target.rollbackSlot || "node24_5",
    candidateSlot: target.candidateSlot || "node24_current",
    upstreamTag: lock.upstreamTag || "v24.17.0",
    upstreamCommit: lock.upstreamCommit || "413e874773eef1e27a8397ddc949dbbe19cadb31",
    androidArtifact: lock.androidArtifact || "nodejs-mobile-24.05-android.zip",
    node2417Promotion: {
      status: decision.status || lock.node2417Promotion || "blocked",
      promoteDefaultRuntime: Boolean(decision.promoteDefaultRuntime),
      summary: decision.summary || "Node 24.17 Android runtime artifacts are not currently producible; keep Node 24.5 as default.",
      blockerCount: Array.isArray(decision.blockers) ? decision.blockers.length : 0,
    },
  };
}

function summarizePageSizes(reports) {
  const alignment = reports["native-16kb-alignment"] || {};
  const artifactGate = reports["runtime-artifact-gate"] || {};
  const artifacts = Array.isArray(alignment.artifacts) ? alignment.artifacts : [];
  const available = artifacts.filter((artifact) => artifact.available !== false);
  const compatible16 = available.filter((artifact) => artifact.alignment16kb && artifact.alignment16kb.compatible);
  const incompatible16 = available.filter((artifact) => artifact.alignment16kb && artifact.alignment16kb.compatible === false);
  const compatible4 = available.filter((artifact) => {
    const segments = Array.isArray(artifact.loadSegments) ? artifact.loadSegments : [];
    return segments.length === 0 || segments.every((segment) => Number(segment.align) >= 4096);
  });
  return {
    requiredPageSizes: [4096, 16384],
    fourKbCompatibleArtifacts: compatible4.length,
    sixteenKbCompatibleArtifacts: compatible16.length,
    checkedArtifacts: available.length,
    sixteenKbIncompatibleArtifacts: incompatible16.map((artifact) => `${artifact.abi}/${artifact.name}`),
    decision: {
      alignmentStatus: extractReportStatus(alignment),
      artifactGateStatus: extractReportStatus(artifactGate),
      warnings: unique([
        ...extractReportIssues(alignment, "warnings"),
        ...extractReportIssues(artifactGate, "warnings"),
      ]),
    },
  };
}

function summarizeStartupPerformance(reports, appReportDir, repoRoot) {
  const performancePolicy = reports["phase9-performance-policy"] || {};
  const matrixFile = newestFile(path.join(appReportDir, "perf"), /^phase8-performance-matrix-.*\.json$/);
  const matrix = matrixFile ? readJson(matrixFile) : null;
  const modes = Array.isArray(matrix && matrix.modes) ? matrix.modes : [];
  const compileCache = modes.find((mode) => mode.modeId === "compile-cache-only");
  const prewarm = modes.find((mode) => mode.modeId === "compile-cache-plus-process-prewarm");
  const compileWall = compileCache && compileCache.metrics && Number(compileCache.metrics.wallMs);
  const prewarmWall = prewarm && prewarm.metrics && Number(prewarm.metrics.wallMs);
  const wallDeltaMs = Number.isFinite(compileWall) && Number.isFinite(prewarmWall) ? prewarmWall - compileWall : null;
  return {
    policyStatus: extractReportStatus(performancePolicy),
    source: matrixFile ? relativePath(matrixFile, repoRoot) : null,
    numericSloStatus: "partial",
    modes: modes.map((mode) => ({
      modeId: mode.modeId,
      supported: Boolean(mode.supported),
      status: mode.status || (mode.supported ? "measured" : "unavailable"),
      wallMs: numericMetric(mode.metrics, "wallMs"),
      nativeLoadMs: numericMetric(mode.metrics, "nativeLoadMs"),
      environmentCreateMs: numericMetric(mode.metrics, "environmentCreateMs"),
      firstModuleMs: numericMetric(mode.metrics, "firstModuleMs"),
      firstBridgeCallMs: numericMetric(mode.metrics, "firstBridgeCallMs"),
      serviceRssKb: numericMetric(mode.metrics, "serviceRssKb"),
    })),
    compileCacheOnlyWallMs: Number.isFinite(compileWall) ? compileWall : null,
    compileCachePlusProcessPrewarmWallMs: Number.isFinite(prewarmWall) ? prewarmWall : null,
    processPrewarmDeltaMs: wallDeltaMs,
    processPrewarmDeltaPercent: wallDeltaMs !== null && compileWall > 0
      ? Number(((wallDeltaMs / compileWall) * 100).toFixed(1))
      : null,
    remainingWork: [
      "startup snapshot generation and validated loading",
      "reusable clean Node/V8 runtime pooling",
      "packaged launch-to-marker metric",
      "enforced Phase 9 numeric SLO thresholds",
    ],
  };
}

function buildCapabilityMatrix(reports, examples, npmCorpus) {
  return [
    capability("Node 24.5 default runtime", "stable", "ready", ["runtime-build-plan"], "Default remains node24_5."),
    capability("Versioned C ABI adapter surface", "partial", combinedStatus(reports, ["runtime-adapter-abi-v1", "runtime-adapter-export"]), ["runtime-adapter-abi-v1", "runtime-adapter-export", "native-symbol-usage"], "ABI/export evidence is ready; native private boundary migration remains recorded."),
    capability("Node 24.17 promotion", "experimental", "blocked", ["runtime-build-plan", "node-2417-upgrade-gate"], "Android libnode.so artifacts are not producible in this phase."),
    capability("Runtime artifact manifest/integrity/distribution", "partial", reports["runtime-artifact-gate"] && reports["runtime-artifact-gate"].status || "unknown", ["runtime-artifact-gate"], "Build IDs, full 16 KB readiness, split, and dynamic modes remain planned."),
    capability("16 KB page-size readiness", "partial", reports["native-16kb-alignment"] && reports["native-16kb-alignment"].status || "unknown", ["native-16kb-alignment"], "Current runtime artifacts are checked; some libraries are still 4 KB aligned."),
    capability("Compile cache", "stable", sectionStatus(reports["phase9-performance-policy"], "compile-cache"), ["phase9-performance-policy"], "Production-safe app-private compile-cache policy is evidenced."),
    capability("Startup snapshot", "experimental", sectionStatus(reports["phase9-performance-policy"], "startup-snapshot"), ["phase9-performance-policy"], "Snapshot remains disabled until generated artifacts and isolation gates exist."),
    capability("Process prewarm and performance SLO", "partial", sectionStatus(reports["phase9-performance-policy"], "process-prewarm"), ["phase9-performance-policy"], "Process-only prewarm evidence exists; reusable runtime pool and numeric SLOs remain partial."),
    capability("Inspector, CPU profile, heap snapshot", "experimental", reports["phase9-inspector-policy"] && reports["phase9-inspector-policy"].status || "unknown", ["phase9-inspector-policy"], "Release bypass denial is ready; positive debug tooling remains deferred."),
    capability("Isolation, broker, process pool", "partial", reports["phase9-isolation-policy"] && reports["phase9-isolation-policy"].status || "unknown", ["phase9-isolation-policy"], "POC/policy evidence exists; Binder broker, crash recovery, resource limits, and worker promotion remain blocked or deferred."),
    capability("WebAssembly basic loading", "stable", sectionStatus(reports["phase9-wasm-wasi-policy"], "wasm-module-loading"), ["phase9-wasm-wasi-policy", "verifyNodeWasmWasiGate"], "Pure WebAssembly compile/instantiate and scoped .wasm loading pass."),
    capability("Controlled WASI facade", "experimental", sectionStatus(reports["phase9-wasm-wasi-policy"], "controlled-wasi-facade"), ["phase9-wasm-wasi-policy"], "Raw node:wasi stays disabled; autojs6:wasi facade remains future work."),
    capability("WASM plugin/native service plugin", "experimental", reports["phase9-plugin-policy"] && reports["phase9-plugin-policy"].status || "unknown", ["phase9-plugin-policy"], "JS-only plugin manager remains the active surface; WASM/native service plugin paths are gated."),
    capability("Native crash pipeline", "partial", reports["phase9-native-crash-pipeline"] && reports["phase9-native-crash-pipeline"].status || "unknown", ["phase9-native-crash-pipeline"], "Crash evidence and symbolication docs exist; full automation remains partial."),
    capability("Chaos/security corpus", "partial", reports["phase9-chaos-security-policy"] && reports["phase9-chaos-security-policy"].status || "unknown", ["phase9-chaos-security-policy"], "Security boundaries are recorded; several chaos injections remain deferred."),
    capability("Real npm corpus", npmCorpus.phase9Fixtures >= npmCorpus.requiredMinimum ? "stable" : "partial", npmCorpus.phase9Fixtures >= npmCorpus.requiredMinimum ? "ready" : "partial", ["NodeRealNpmCompatInstrumentationTest"], `${npmCorpus.phase9Fixtures} fixtures, delta ${npmCorpus.deltaFromPhase8} from Phase 8.`),
    capability("Phase 9 user examples", examples.missingPhase9Examples.length === 0 ? "stable" : "partial", examples.missingPhase9Examples.length === 0 ? "ready" : "partial", ["verifyNodeExamples"], `${examples.total} examples total, ${examples.phase9Added} Phase 9 examples added.`),
  ];
}

function capability(name, level, status, evidence, notes) {
  return { name, level, status: status || "unknown", evidence, notes };
}

function combinedStatus(reports, ids) {
  const statuses = ids.map((id) => reports[id] && reports[id].status).filter(Boolean);
  if (statuses.length === 0) {
    return "missing";
  }
  if (statuses.includes("blocked")) {
    return "blocked";
  }
  if (statuses.includes("bootstrap_only")) {
    return "bootstrap_only";
  }
  if (statuses.includes("partial") || statuses.includes("deferred")) {
    return "partial";
  }
  return statuses.every((status) => status === "ready") ? "ready" : "mixed";
}

function sectionStatus(report, sectionId) {
  const json = report && report.json;
  const section = json && Array.isArray(json.sections)
    ? json.sections.find((entry) => entry.id === sectionId)
    : null;
  return section && section.status || "unknown";
}

function buildRemainingBlockers(reports, runtimeComparison, pageSizeValidation) {
  const blockers = [
    {
      id: "node2417-android-artifacts",
      status: "blocked",
      evidence: ["runtime-build-plan", "node-2417-upgrade-gate"],
      summary: "Node 24.17 Android libnode.so artifacts are not producible yet; default runtime remains node24_5.",
    },
    {
      id: "native-boundary-migration",
      status: reports["native-symbol-usage"] && reports["native-symbol-usage"].status || "unknown",
      evidence: ["native-symbol-usage", "runtime-adapter-abi-v1"],
      summary: "Private/unstable Node/V8 references still need to move fully behind AutoJsNodeRuntimeApiV1.",
    },
    {
      id: "runtime-artifact-16kb-integrity",
      status: pageSizeValidation.decision.artifactGateStatus,
      evidence: ["native-16kb-alignment", "runtime-artifact-gate"],
      summary: "Some current artifacts are not fully 16 KB aligned and libnode Build IDs are missing.",
    },
    {
      id: "startup-snapshot-slo",
      status: sectionStatus(reports["phase9-performance-policy"], "startup-snapshot"),
      evidence: ["phase9-performance-policy"],
      summary: "Startup snapshot artifacts, packaged metrics, reusable runtime pool, and numeric SLO thresholds remain deferred.",
    },
    {
      id: "inspector-debug-tooling",
      status: reports["phase9-inspector-policy"] && reports["phase9-inspector-policy"].status || "unknown",
      evidence: ["phase9-inspector-policy"],
      summary: "Positive debug Inspector, ADB DevTools, CPU profile, and heap snapshot sessions remain deferred while release bypass denial stays ready.",
    },
    {
      id: "isolation-pool-worker",
      status: reports["phase9-isolation-policy"] && reports["phase9-isolation-policy"].status || "unknown",
      evidence: ["phase9-isolation-policy"],
      summary: "Binder broker, multi-process pool, crash recovery, resource limits, multi-instance consistency, and worker promotion remain incomplete.",
    },
    {
      id: "wasi-and-wasm-production",
      status: reports["phase9-wasm-wasi-policy"] && reports["phase9-wasm-wasi-policy"].status || "unknown",
      evidence: ["phase9-wasm-wasi-policy"],
      summary: "WASM compile cache, controlled WASI facade, resource limits, worker integration, build templates, and full security corpus remain future work.",
    },
    {
      id: "plugin-extension-platform",
      status: reports["phase9-plugin-policy"] && reports["phase9-plugin-policy"].status || "unknown",
      evidence: ["phase9-plugin-policy"],
      summary: "WASM plugin packages and native service plugin protocol remain design-gated; raw .node addon support remains denied.",
    },
    {
      id: "chaos-security-depth",
      status: reports["phase9-chaos-security-policy"] && reports["phase9-chaos-security-policy"].status || "unknown",
      evidence: ["phase9-chaos-security-policy"],
      summary: "Some failure injections and security-corpus paths remain partial or deferred.",
    },
  ];
  return blockers.map((entry) => ({
    ...entry,
    promotionImpact: entry.id === "node2417-android-artifacts" ? runtimeComparison.node2417Promotion.summary : undefined,
  }));
}

function collectPolicyViolations(hostReports, deviceGate, examples, npmCorpus) {
  const violations = [];
  for (const report of hostReports) {
    if (report.required && !report.available) {
      violations.push(`missing required host report: ${report.id}`);
    }
  }
  if (deviceGate.resultXml.length === 0) {
    violations.push("missing connected Android test XML for Phase 9 gate");
  }
  for (const className of deviceGate.missingClasses) {
    violations.push(`missing Phase 9 instrumentation class result: ${className}`);
  }
  for (const className of deviceGate.failedClasses) {
    violations.push(`non-passing Phase 9 instrumentation class result: ${className}`);
  }
  if (!examples.available) {
    violations.push("missing Node examples report");
  }
  for (const name of examples.missingPhase9Examples) {
    violations.push(`missing Phase 9 user example: ${name}`);
  }
  if (!npmCorpus.available) {
    violations.push("missing real npm corpus manifest");
  } else if (npmCorpus.phase9Fixtures < npmCorpus.requiredMinimum) {
    violations.push(`real npm corpus has ${npmCorpus.phase9Fixtures} fixture(s), expected at least ${npmCorpus.requiredMinimum}`);
  }
  return violations;
}

function stripHostJson(entry) {
  const clone = { ...entry };
  delete clone.json;
  return clone;
}

function newestFile(directory, pattern) {
  if (!fs.existsSync(directory)) {
    return null;
  }
  const files = fs.readdirSync(directory)
    .filter((name) => pattern.test(name))
    .map((name) => path.join(directory, name))
    .filter((file) => fs.statSync(file).isFile())
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  return files[0] || null;
}

function numericMetric(metrics, key) {
  if (!metrics || metrics[key] === null || metrics[key] === undefined || metrics[key] === "") {
    return null;
  }
  const value = Number(metrics[key]);
  return Number.isFinite(value) ? value : null;
}

function issueToString(issue) {
  if (typeof issue === "string") {
    return issue;
  }
  if (issue && typeof issue === "object") {
    return Object.entries(issue).map(([key, value]) => `${key}=${value}`).join(", ");
  }
  return String(issue);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function writeReport(reportDir, baseName, report, renderer) {
  const jsonPath = path.join(reportDir, `${baseName}.json`);
  const markdownPath = path.join(reportDir, `${baseName}.md`);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderer(report));
  console.log(`${baseName} JSON: ${jsonPath}`);
  console.log(`${baseName} Markdown: ${markdownPath}`);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Phase 9 Summary");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("## Runtime");
  lines.push("");
  lines.push(`- Default runtime slot: \`${report.defaultRuntimeSlot}\``);
  lines.push(`- Current runtime: Node ${report.runtimeComparison.currentRuntime}`);
  lines.push(`- Candidate runtime: Node ${report.runtimeComparison.candidateRuntime}`);
  lines.push(`- Node 24.17 promotion: \`${report.runtimeComparison.node2417Promotion.status}\``);
  lines.push("");
  lines.push("## Device Gate");
  lines.push("");
  lines.push("| Class | Status | Tests | Failures | Errors | Skipped |");
  lines.push("| --- | --- | ---: | ---: | ---: | ---: |");
  for (const entry of report.deviceGate.classSummaries) {
    lines.push(`| ${entry.className} | ${entry.status} | ${entry.tests} | ${entry.failures} | ${entry.errors} | ${entry.skipped} |`);
  }
  lines.push("");
  lines.push("## Capability Matrix");
  lines.push("");
  lines.push("| Capability | Level | Status | Notes |");
  lines.push("| --- | --- | --- | --- |");
  for (const entry of report.capabilityMatrix) {
    lines.push(`| ${entry.name} | ${entry.level} | ${entry.status} | ${entry.notes} |`);
  }
  lines.push("");
  lines.push("## Deltas");
  lines.push("");
  lines.push(`- npm corpus: ${report.npmCorpus.phase8BaselineFixtures} -> ${report.npmCorpus.phase9Fixtures} (${signed(report.npmCorpus.deltaFromPhase8)}).`);
  lines.push(`- examples: ${report.examples.phase8BaselineTotal} -> ${report.examples.total} (${signed(report.examples.deltaFromPhase8)}).`);
  if (report.startupPerformance.processPrewarmDeltaMs !== null) {
    lines.push(
      `- process prewarm wall time: ${report.startupPerformance.compileCacheOnlyWallMs} ms -> ` +
        `${report.startupPerformance.compileCachePlusProcessPrewarmWallMs} ms ` +
        `(${signed(report.startupPerformance.processPrewarmDeltaMs)} ms, ${signed(report.startupPerformance.processPrewarmDeltaPercent)}%).`,
    );
  } else {
    lines.push("- process prewarm wall time: not measured in current pulled reports.");
  }
  lines.push("");
  lines.push("## Page Size");
  lines.push("");
  lines.push(`- 4 KB compatible checked artifacts: ${report.pageSizeValidation.fourKbCompatibleArtifacts}/${report.pageSizeValidation.checkedArtifacts}`);
  lines.push(`- 16 KB compatible checked artifacts: ${report.pageSizeValidation.sixteenKbCompatibleArtifacts}/${report.pageSizeValidation.checkedArtifacts}`);
  if (report.pageSizeValidation.sixteenKbIncompatibleArtifacts.length > 0) {
    lines.push(`- 16 KB incompatible artifacts: ${report.pageSizeValidation.sixteenKbIncompatibleArtifacts.join(", ")}`);
  }
  lines.push("");
  lines.push("## Remaining Blockers");
  lines.push("");
  for (const blocker of report.remainingBlockers) {
    lines.push(`- ${blocker.id} (${blocker.status}): ${blocker.summary}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("");
    lines.push("## Policy Violations");
    lines.push("");
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  lines.push("");
  return lines.join("\n");
}

function signed(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "n/a";
  }
  const number = Number(value);
  return number > 0 ? `+${number}` : String(number);
}

function unique(values) {
  return Array.from(new Set(values.filter((value) => value !== null && value !== undefined && value !== "")));
}

function relativePath(file, root) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function decodeXml(value) {
  return value
    .replace(/&quot;/g, "\"")
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/reports/generate-phase9-summary.js [--repo-root dir] [--report-dir dir] [--app-report-dir dir] [--android-results-dir dir] [--device-profile name]");
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
