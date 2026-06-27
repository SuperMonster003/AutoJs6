#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-relaxed-capability-security-corpus";

const EXPECTED_CAPABILITIES = [
  "raw_network",
  "filesystem_relaxation",
  "java_interop",
  "child_process",
  "native_addon",
  "inspector",
  "wasi",
];

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_50_RELAXED_CAPABILITY_SECURITY_CORPUS.md",
  corpus: "docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  bridgeManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  securityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  httpDesktopTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpDesktopParityInstrumentationTest.kt",
  netTlsDnsTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNetTlsDnsStagedParityInstrumentationTest.kt",
  fsAdvancedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFilesystemAdvancedDecisionInstrumentationTest.kt",
  javaInteropTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeJavaInteropBridgeInstrumentationTest.kt",
  childProcessTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeChildProcessPhase13DecisionInstrumentationTest.kt",
  nativeAddonTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNativeAddonPhase13DecisionInstrumentationTest.kt",
  inspectorTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorPhase13DecisionInstrumentationTest.kt",
  wasiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasiPhase13DecisionInstrumentationTest.kt",
  wasmWasiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWasmWasiInstrumentationTest.kt",
  packageIntegrityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackageLockIntegrityPolicyPhase13DecisionInstrumentationTest.kt",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  relaxationRubric: "docs/nodejs/security/CAPABILITY_RELAXATION_RUBRIC.md",
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
  console.log("Phase 13 relaxed capability security corpus: " + report.decision.status);
  console.log("Phase 13 relaxed capability blockers: " + report.blockers.length);
  console.log("Phase 13 relaxed capability policy violations: " + report.policyViolations.length);
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
  const corpus = parseJson(files.corpus);
  const surfaceEntry = surface(files.surfaceManifest.text, "security.rollback_privacy");
  const capabilityMap = new Map(
    Array.isArray(corpus && corpus.capabilities)
      ? corpus.capabilities.map((entry) => [entry.id, entry])
      : [],
  );
  const capabilityChecks = EXPECTED_CAPABILITIES.map((id) => capabilityEvidence(id, capabilityMap.get(id)));
  const allCapabilityEvidencePresent = capabilityChecks.every(
    (entry) =>
      entry.present &&
      entry.hasPositive &&
      entry.hasNegative &&
      entry.hasAbuse &&
      entry.hasCi &&
      entry.hasRuntimeGuards &&
      entry.hasPromotionPreconditions &&
      entry.hasRollback,
  );
  const focusedBlock = files.securityTest.text;
  const runtimeBlock = block(files.nodeBridgeSources.text, "function __autojs6_profile()", "  function __autojs6_profile_profile_order()");

  const scenarios = [
    scenario("corpus_descriptor", "Relaxed capability corpus records required positive, negative, abuse, CI, runtime guard, promotion precondition, and rollback evidence for every high-risk surface", [
      token(files.corpus, '"schema": "autojs6-node-relaxed-capability-security-corpus-v1"'),
      token(files.corpus, '"task": "P13-50"'),
      token(files.corpus, '"profilePromotion": false'),
      token(files.corpus, '"rawCapabilitiesPromoted": false'),
      token(files.corpus, '"runtimeGuards"'),
      token(files.corpus, '"promotionPreconditions"'),
      ...EXPECTED_CAPABILITIES.map((id) => fact(files.corpus.relative, id, capabilityChecks.find((entry) => entry.id === id).present)),
      ...capabilityChecks.flatMap((entry) => [
        fact(files.corpus.relative, `${entry.id}.positiveTests`, entry.hasPositive),
        fact(files.corpus.relative, `${entry.id}.negativeTests`, entry.hasNegative),
        fact(files.corpus.relative, `${entry.id}.abuseCases`, entry.hasAbuse),
        fact(files.corpus.relative, `${entry.id}.ciEvidence`, entry.hasCi),
        fact(files.corpus.relative, `${entry.id}.runtimeGuards`, entry.hasRuntimeGuards),
        fact(files.corpus.relative, `${entry.id}.promotionPreconditions`, entry.hasPromotionPreconditions),
        fact(files.corpus.relative, `${entry.id}.rollback`, entry.hasRollback),
      ]),
    ], [
      "Corpus entries are evidence descriptors; P13-51/P13-52 still need rollback UX, privacy prompts, and runtime promotion controls.",
    ]),
    scenario("focused_security_regression", "NodeSecurityRegressionInstrumentationTest includes P13-50 focused positive and negative relaxed-capability probes", [
      token(files.securityTest, "phase13RelaxedCapabilityCorpusCoversNetworkFsChildNativeInspectorAndWasi"),
      token(files.securityTest, "phase13JavaInteropRelaxedCapabilityCorpusKeepsAllowlistAndDenials"),
      token(files.securityTest, "security.p13_50.raw_network_positive="),
      token(files.securityTest, "security.p13_50.fs_positive="),
      token(files.securityTest, "security.p13_50.child_positive="),
      token(files.securityTest, "security.p13_50.native_positive="),
      token(files.securityTest, "security.p13_50.inspector_positive="),
      token(files.securityTest, "security.p13_50.wasi_positive="),
      token(files.securityTest, "java.lang.Runtime"),
      token(files.securityTest, "java.lang.reflect.Method"),
      token(files.securityTest, "java.lang.System"),
      token(files.securityTest, "NodeRuntimeErrorCodes.NETWORK_DISABLED"),
      token(files.securityTest, "NodeRuntimeErrorCodes.FS_REALPATH_ESCAPE"),
      token(files.securityTest, "NodeRuntimeErrorCodes.JAVA_REFLECTION_DENIED"),
    ]),
    scenario("companion_security_evidence", "Existing focused tests continue to provide per-capability evidence around the P13-50 corpus", [
      token(files.httpDesktopTest, "class NodeHttpDesktopParityInstrumentationTest"),
      token(files.netTlsDnsTest, "class NodeNetTlsDnsStagedParityInstrumentationTest"),
      token(files.fsAdvancedTest, "class NodeFilesystemAdvancedDecisionInstrumentationTest"),
      token(files.javaInteropTest, "kotlinJavaInteropProviderIsDisabledByDefaultAndAllowlistedWhenEnabled"),
      token(files.childProcessTest, "childProcessProfileKeepsBuiltinDeniedAndShellSeparate"),
      token(files.nativeAddonTest, "nativeAddonProfileKeepsNodeFilesAndDlopenDenied"),
      token(files.inspectorTest, "inspectorProfileKeepsDebugToolingDeniedAndDeferred"),
      token(files.wasiTest, "wasiProfileKeepsRawWasiDeniedAndControlledFacadeDeferred"),
      token(files.wasmWasiTest, "class NodeWasmWasiInstrumentationTest"),
      token(files.packageIntegrityTest, "packageIntegrityProfileRecordsUnifiedPolicyAndFailClosedBoundaries"),
    ]),
    scenario("runtime_fail_closed_policy", "Runtime profile metadata still records relaxed capabilities as blocked or controlled without silently promoting raw authority", [
      token(files.nodeBridgeSources, "raw_node_network_modules: Object.freeze({"),
      token(files.nodeBridgeSources, "network: Object.freeze({"),
      token(files.nodeBridgeSources, "child_process: Object.freeze({"),
      token(files.nodeBridgeSources, "native_addon: Object.freeze({"),
      token(files.nodeBridgeSources, "inspector: Object.freeze({"),
      token(files.nodeBridgeSources, "java_interop: Object.freeze({"),
      token(files.nodeBridgeSources, "filesystemProfile: Object.freeze({"),
      token(files.nodeBridgeSources, "wasiProfile: Object.freeze({"),
      token(files.nodeBridgeSources, "rawSockets: false"),
      token(files.nodeBridgeSources, "rawHostPaths: false"),
      token(files.nodeBridgeSources, "rawFdNumbers: false"),
      token(files.nodeBridgeSources, "rawProcessHandles: false"),
      token(files.nodeBridgeSources, 'rawDlopen: "denied"'),
      token(files.nodeBridgeSources, "downloadExec: false"),
      token(files.nodeBridgeSources, 'network: "denied"'),
      token(files.nodeBridgeSources, 'processExit: "denied"'),
      token(files.runtimeCapabilities, "nativeAddon"),
      token(files.bridgeManifest, "JAVA_INTEROP"),
    ], [
      "Safe-default raw network modules, expanded filesystem roots, child_process, native addons, inspector, raw WASI, and release Java interop remain unpromoted.",
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-50", [
      token(files.phase13Tasks, "P13-50 partial landing"),
      token(files.p13Report, "P13-50 records relaxed-capability security corpus as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13RelaxedCapabilitySecurityCorpus"),
      token(files.testingDoc, "verifyNodePhase13RelaxedCapabilitySecurityCorpus"),
      token(files.ciGatesDoc, "verifyNodePhase13RelaxedCapabilitySecurityCorpus"),
      token(files.compatibilityProfile, "P13-50 records relaxed-capability security corpus as partial"),
      token(files.securityModel, "P13-50 records relaxed-capability security corpus as partial"),
      token(files.proComparison, "P13-50 records relaxed-capability security corpus as partial"),
      token(files.proAutoxNotes, "P13-50 records relaxed-capability security corpus as partial"),
      token(files.capabilityMatrix, "P13-50 relaxed capability security corpus"),
      token(files.relaxationRubric, "RELAXED_CAPABILITY_SECURITY_CORPUS.json"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
    ], [
      "P13-50 does not close rollback, privacy disclosure, or user prompt requirements for future relaxed capability promotion.",
    ]),
  ];

  const forbiddenPromotions = [
    "rawSockets: true",
    "rawHostPaths: true",
    "rawFdNumbers: true",
    "rawProcessHandles: true",
    "rawNativeHandles: true",
    "downloadExec: true",
    "unsignedNativeAddonLoad: true",
    "profilePromotion: true",
    "userSelectable: true",
    'rawDlopen: "available"',
    'processExit: "available"',
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-50"), "P13-50 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-50 report must exist."),
    check("corpus-present", files.corpus.exists && Boolean(corpus), "P13-50 relaxed capability corpus JSON must exist and parse."),
    check(
      "corpus-schema",
      corpus && corpus.schema === "autojs6-node-relaxed-capability-security-corpus-v1" && corpus.task === "P13-50" && corpus.status === "partial",
      "corpus must declare the P13-50 schema, task, and partial status.",
    ),
    check(
      "corpus-policy",
      corpus && corpus.policy && corpus.policy.defaultProfile === "safe_default" && corpus.policy.profilePromotion === false && corpus.policy.rawCapabilitiesPromoted === false,
      "corpus policy must keep safe_default as default and must not promote raw capabilities.",
    ),
    check(
      "corpus-capability-evidence",
      allCapabilityEvidencePresent,
      "each relaxed capability must include positiveTests, negativeTests, abuseCases, ciEvidence, runtimeGuards, promotionPreconditions, and rollback evidence.",
    ),
    check("surface-present", Boolean(surfaceEntry), "`security.rollback_privacy` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)) && (surfaceEntry.evidence || []).some((entry) => entry.includes("RELAXED_CAPABILITY_SECURITY_CORPUS.json")),
      "`security.rollback_privacy` surface must reference P13-50 corpus/report evidence.",
    ),
    check(
      "focused-instrumentation-present",
      focusedBlock.includes("phase13RelaxedCapabilityCorpusCoversNetworkFsChildNativeInspectorAndWasi") &&
        focusedBlock.includes("phase13JavaInteropRelaxedCapabilityCorpusKeepsAllowlistAndDenials") &&
        focusedBlock.includes("security.p13_50.ready=true"),
      "focused P13-50 instrumentation tests must be present.",
    ),
    check(
      "runtime-fail-closed",
      runtimeBlock.includes("rawSockets: false") &&
        runtimeBlock.includes("rawHostPaths: false") &&
        runtimeBlock.includes("rawFdNumbers: false") &&
        runtimeBlock.includes("rawProcessHandles: false") &&
        runtimeBlock.includes('rawDlopen: "denied"') &&
        runtimeBlock.includes("downloadExec: false") &&
        runtimeBlock.includes('processExit: "denied"'),
      "runtime profile metadata must keep raw sockets, raw paths, raw fds, raw process/native handles, dlopen, download-exec, and WASI process exit fail-closed.",
    ),
    check(
      "no-relaxed-capability-promotion",
      forbiddenPromotions.every((tokenValue) => !runtimeBlock.includes(tokenValue)),
      "P13-50 must not promote raw relaxed capabilities while recording the corpus.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-relaxed-capability-security-corpus-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13RelaxedCapabilitySecurityCorpus",
    task: "P13-50",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "security.rollback_privacy",
      status: "missing",
      evidence: [],
    },
    capabilities: capabilityChecks,
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      corpusDescriptorReady: allCapabilityEvidencePresent,
      focusedSecurityRegressionReady: scenarioById(scenarios, "focused_security_regression").status === "ready",
      companionEvidenceReady: scenarioById(scenarios, "companion_security_evidence").status === "ready",
      runtimeFailClosed: checks.find((entry) => entry.id === "runtime-fail-closed").passed,
      rawCapabilitiesPromoted: false,
      rollbackUxReady: false,
      privacyDisclosureReady: false,
    },
  };
}

function capabilityEvidence(id, entry) {
  const arrayHasItems = (name) => Array.isArray(entry && entry[name]) && entry[name].length > 0;
  return {
    id,
    present: Boolean(entry),
    profile: entry && entry.profile,
    risk: entry && entry.risk,
    hasPositive: arrayHasItems("positiveTests"),
    hasNegative: arrayHasItems("negativeTests"),
    hasAbuse: arrayHasItems("abuseCases"),
    hasCi: arrayHasItems("ciEvidence"),
    hasRuntimeGuards: arrayHasItems("runtimeGuards"),
    hasPromotionPreconditions: arrayHasItems("promotionPreconditions"),
    hasRollback: typeof (entry && entry.rollback) === "string" && entry.rollback.trim().length > 0,
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

function fact(file, tokenValue, present) {
  return {
    file,
    token: tokenValue,
    present: Boolean(present),
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

function parseJson(file) {
  if (!file.exists) return null;
  try {
    return JSON.parse(file.text);
  } catch (_) {
    return null;
  }
}

function block(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  if (start < 0) return "";
  const end = text.indexOf(endToken, start);
  return end < 0 ? text.slice(start) : text.slice(start, end);
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
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Relaxed Capability Security Corpus",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Decision: \`${report.decision.status}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: \`${report.policyViolations.length}\``,
    "",
    "## Capabilities",
    "",
    "| Capability | Present | Positive | Negative | Abuse | CI | Runtime Guards | Promotion Preconditions | Rollback |",
    "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ...report.capabilities.map((entry) => `| \`${entry.id}\` | ${yesNo(entry.present)} | ${yesNo(entry.hasPositive)} | ${yesNo(entry.hasNegative)} | ${yesNo(entry.hasAbuse)} | ${yesNo(entry.hasCi)} | ${yesNo(entry.hasRuntimeGuards)} | ${yesNo(entry.hasPromotionPreconditions)} | ${yesNo(entry.hasRollback)} |`),
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
    ...report.scenarios.map((entry) => `| \`${entry.id}\` | \`${entry.status}\` | ${entry.remaining.length} |`),
    "",
    "## Checks",
    "",
    "| Check | Passed |",
    "| --- | --- |",
    ...report.checks.map((entry) => `| \`${entry.id}\` | ${yesNo(entry.passed)} |`),
    "",
  ];
  if (report.blockers.length > 0) {
    lines.push("## Blockers", "", ...report.blockers.map((entry) => `- ${entry}`), "");
  }
  if (report.policyViolations.length > 0) {
    lines.push("## Policy Violations", "", ...report.policyViolations.map((entry) => `- ${entry}`), "");
  }
  return lines.join("\n");
}

function yesNo(value) {
  return value ? "yes" : "no";
}

main();
