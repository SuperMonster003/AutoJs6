#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const sections = [
    hardenedIsolatedRuntimeSection(files),
    capabilityBrokerSection(files),
    processPoolSection(files),
    crashContainmentSection(files),
    resourceLimitsSection(files),
    multiInstanceConsistencySection(files),
    workerPartialPromotionSection(files),
  ];
  const blockers = sections.flatMap((section) => section.blockers.map((blocker) => `${section.id}: ${blocker}`));
  const warnings = sections.flatMap((section) => section.warnings.map((warning) => `${section.id}: ${warning}`));
  const nonReady = sections.filter((section) => section.status !== "ready").map((section) => `${section.id}=${section.status}`);
  const decision = {
    status: blockers.length > 0 ? "blocked" : nonReady.length > 0 ? "bootstrap_only" : "ready",
    blockers,
    warnings: warnings.concat(nonReady.map((entry) => `section is not fully production-ready: ${entry}`)),
  };
  const report = {
    schema: "autojs6-node-phase9-isolation-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-27..S9-33 Isolation, Process Pool, Crash, Resource, And Worker Policy",
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-isolation-policy", report, renderMarkdown);
  console.log("Phase 9 isolation policy decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    manifest: "app/src/main/AndroidManifest.xml",
    executionPolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
    processPoolPolicy: "app/src/main/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicy.kt",
    brokerPolicy: "app/src/main/java/org/autojs/autojs/engine/NodeCapabilityBrokerPolicy.kt",
    hardenedTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeHardenedIsolatedRuntimePocInstrumentationTest.kt",
    brokerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeCapabilityBrokerV2InstrumentationTest.kt",
    processPoolTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeEmbeddedProcessPoolPolicyInstrumentationTest.kt",
    workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
    workerDesignTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsDesignGateInstrumentationTest.kt",
    phase8Hardened: "docs/nodejs/reports/phase8/HARDENED_ISOLATED_RUNTIME_POC.md",
    phase8Broker: "docs/nodejs/reports/phase8/CAPABILITY_BROKER_V2.md",
    phase8ProcessPool: "docs/nodejs/reports/phase8/PROCESS_POOL_POLICY_GATE.md",
    phase8Worker: "docs/nodejs/reports/phase8/WORKER_PARTIAL_PROMOTION_RECHECK.md",
    hardenedDesign: "docs/nodejs/design/HARDENED_ISOLATED_RUNTIME.md",
    brokerDesign: "docs/nodejs/design/CAPABILITY_BROKER_V2.md",
    processPoolDesign: "docs/nodejs/design/PROCESS_POOL.md",
    multiInstanceDesign: "docs/nodejs/design/MULTI_INSTANCE.md",
    workerDesign: "docs/nodejs/design/WORKER_THREADS.md",
    compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
    phase9Policy: "docs/nodejs/reports/phase9/ISOLATION_POLICY.md",
  };
  const loaded = {};
  for (const [key, relativePath] of Object.entries(paths)) {
    loaded[key] = {
      relativePath,
      text: readIfExists(path.join(repoRoot, relativePath)),
    };
  }
  return loaded;
}

function hardenedIsolatedRuntimeSection(files) {
  const checks = [
    checkTokens("execution-mode-policy", files.executionPolicy, [
      "MODE_NODE_SANDBOXED",
      'status = "design_gated"',
      "hardened_sandbox_broker_only",
      "Binder capability broker",
    ]),
    checkTokens("manifest-isolated-service", files.manifest, [
      "NativeNodeSandboxedScriptService",
      'android:exported="false"',
      'android:isolatedProcess="true"',
      ':nodejs_sandboxed',
    ]),
    checkTokens("focused-test", files.hardenedTest, [
      "NodeHardenedIsolatedRuntimePocInstrumentationTest",
      "nodeSandboxedModeIsRecognizedButDesignGated",
      "sandboxedServiceManifestIsIsolatedAndNotExported",
      "directScriptNodeSandboxedRequestRejectsBeforeUserCode",
      "NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION",
      "should-not-run.txt",
    ]),
    checkTokens("phase8-report", files.phase8Hardened, [
      "Status: manifest and policy POC complete",
      "no script execution in the isolated process",
      "no bridge provider availability in `node_sandboxed`",
      "Binder capability broker",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-27 Hardened Isolated Runtime",
      "Current state: partial",
      "Direct script requests reject before user code can run",
      "formal execution mode is still deferred",
    ]),
  ];
  return makeSection({
    id: "hardened-isolated-runtime",
    task: "S9-27 Hardened Isolated Runtime Formal Mode",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "The manifest and rejection boundary are present, but node_sandboxed has no executable route until the Binder broker exists.",
    ],
  });
}

function capabilityBrokerSection(files) {
  const checks = [
    checkTokens("broker-policy-contract", files.brokerPolicy, [
      "SCHEMA",
      "DEFAULT_MAX_AUDIT_ROWS",
      "DEFAULT_MAX_REQUEST_BYTES",
      "DEFAULT_MAX_ARGS_BYTES",
      "parameterValidated",
      "sizeLimited",
      "cancellable",
      "audited",
      "executionOwned",
      "ownerExecutionId",
    ]),
    checkTokens("broker-focused-test", files.brokerTest, [
      "NodeCapabilityBrokerV2InstrumentationTest",
      "operationDeclarationsAreExplicitAndExecutionOwned",
      "preflightRejectsUndeclaredInvalidAndOversizedRequests",
      "auditLogIsExecutionOwnedQuotaBoundAndReducesToDiagnostics",
      "androidProviderPublishesBrokerAuditDiagnostics",
    ]),
    checkTokens("phase8-report", files.phase8Broker, [
      "Status: provider-side policy and audit MVP complete",
      "preflight validation",
      "size limits",
      "cancellation metadata",
      "execution ownership",
      "audit rows",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-28 Capability Broker v3",
      "Current state: partial",
      "Capability Broker v3 remains future work",
      "rate limits",
      "resource quota decisions",
      "response-size limits",
      "audit IDs",
    ]),
  ];
  return makeSection({
    id: "capability-broker-v3",
    task: "S9-28 Capability Broker v3",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Provider-side broker v2 is available, but isolated-process Binder broker v3 is not implemented.",
    ],
  });
}

function processPoolSection(files) {
  const checks = [
    checkTokens("process-pool-policy", files.processPoolPolicy, [
      "MODE_PRIMARY_ONLY",
      "DEFAULT_EFFECTIVE_MAX_ACTIVE_SLOTS = 1",
      "queueWhenPoolFullEnabled = false",
      "oneActiveEnvironmentPerSlot = true",
      "sharedV8EnvironmentAllowed = false",
      'crashIsolationPolicy = "per_process_slot_when_enabled"',
    ]),
    checkTokens("process-pool-focused-test", files.processPoolTest, [
      "NodeEmbeddedProcessPoolPolicyInstrumentationTest",
      "processPoolPolicyIsDeviceAwareButFailClosedByDefault",
      "processPoolManifestDeclaresReservedSlots",
      "defaultExecutionPublishesPrimaryProcessPoolDiagnostics",
      "longRunningSlotIsReservedUntilPromotion",
    ]),
    checkTokens("phase8-report", files.phase8ProcessPool, [
      "Status: complete as a default-disabled process-pool policy gate.",
      "effective active slots: `1`",
      "selected slot: `primary`",
      "queue-when-full: disabled until a real pool coordinator exists",
      "one-active-execution-per-process policy",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-29 Multi-process Runtime Pool",
      "Current state: partial",
      "effectiveMaxActiveSlots=1",
      "queueWhenPoolFullEnabled=false",
      "future pool coordinator",
    ]),
  ];
  return makeSection({
    id: "multi-process-runtime-pool",
    task: "S9-29 Multi-process Runtime Pool",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Reserved process slots and diagnostics exist, but the effective active slot count remains one.",
    ],
  });
}

function crashContainmentSection(files) {
  const checks = [
    checkTokens("process-policy-crash-placeholder", files.processPoolPolicy, [
      "crashIsolationPolicy",
      "per_process_slot_when_enabled",
    ]),
    checkTokens("phase8-remaining-work", files.phase8ProcessPool, [
      "crash detection that removes only the failed slot",
      "stress gates proving no cwd, env, module cache, provider, bridge transport",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-30 Crash Containment And Recovery",
      "Current state: deferred",
      "tombstone/log",
      "mark the execution as crashed",
      "quarantine the failed slot",
      "never silently restart",
    ]),
  ];
  return makeSection({
    id: "crash-containment-recovery",
    task: "S9-30 Crash Containment And Recovery",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Only the future crash-isolation policy shape is recorded; tombstone capture, slot quarantine, and replacement are not implemented.",
    ],
  });
}

function resourceLimitsSection(files) {
  const checks = [
    checkTokens("broker-quota-primitives", files.brokerPolicy, [
      "DEFAULT_MAX_AUDIT_ROWS",
      "DEFAULT_MAX_REQUEST_BYTES",
      "DEFAULT_MAX_ARGS_BYTES",
      "NodeBridgeError.resourceLimit",
    ]),
    checkTokens("worker-design-limits", files.workerDesign, [
      "resourceLimits",
      "old generation 64 MB",
      "NodeWorkerThreadsResourceStressInstrumentationTest",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-31 Per-runtime Resource Limits",
      "Current state: deferred",
      "RSS soft/hard caps",
      "V8",
      "ArrayBuffer",
      "file descriptors",
      "CPU duration",
    ]),
  ];
  return makeSection({
    id: "per-runtime-resource-limits",
    task: "S9-31 Per-runtime Resource Limits",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Bridge and worker experimental limits exist, but there is no unified per-runtime RSS, V8, fd, timer, socket, UI-handle, or CPU-duration limiter.",
    ],
  });
}

function multiInstanceConsistencySection(files) {
  const checks = [
    checkTokens("process-pool-primary-only-test", files.processPoolTest, [
      "defaultExecutionPublishesPrimaryProcessPoolDiagnostics",
      "embedded_script.process_pool.enabled",
      "embedded_script.process_pool.effective_max_active_slots",
    ]),
    checkTokens("multi-instance-design", files.multiInstanceDesign, [
      "Process Pool Policy Gate",
      "NodeEmbeddedProcessPoolPolicy",
      "primary_only",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-32 Multi-instance Consistency Tests",
      "Current state: deferred",
      "two short scripts",
      "short plus",
      "native crash while another execution continues",
      "queue cancellation",
      "memory pressure",
    ]),
  ];
  return makeSection({
    id: "multi-instance-consistency",
    task: "S9-32 Multi-instance Consistency Tests",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Current tests prove primary-only diagnostics and reserved slots, not independent concurrent Node environments.",
    ],
  });
}

function workerPartialPromotionSection(files) {
  const checks = [
    checkTokens("worker-native-preflight", files.workerSupport, [
      "assumeNativeWorkerThreadsAvailable",
      "worker_threads native availability preflight",
      "Native worker_threads unavailable",
      "runnable worker tests remain promotion gates",
    ]),
    checkTokens("worker-default-denial", files.workerDesignTest, [
      "NodeWorkerThreadsDesignGateInstrumentationTest",
      "worker_threads design gate",
      "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
      "node_modules/worker_threads",
    ]),
    checkTokens("worker-phase8-recheck", files.phase8Worker, [
      "Status: rechecked, not promoted.",
      "cannot promote `worker_threads`",
      "execution as unavailable",
      "experimental worker code remains an internal unblock/debug gate only",
      "packaged `.mjs` worker smoke",
    ]),
    checkTokens("worker-design", files.workerDesign, [
      "Status: experimental design gate, blocked",
      "autojs.nodejs.worker_threads.experimental=false",
      "native `worker_threads` module",
      "Runnable coverage is skipped when the preflight reports native workers unavailable",
    ]),
    checkTokens("phase9-policy", files.phase9Policy, [
      "S9-33 Worker Partial Promotion",
      "Current state: blocked",
      "does not expose usable native worker execution",
      "must not claim exact `worker_threads` semantics",
    ]),
  ];
  return makeSection({
    id: "worker-partial-promotion",
    task: "S9-33 Worker Partial Promotion",
    statusWhenPassing: "blocked",
    checks,
    warnings: [
      "This is a policy block, not a missing-evidence failure. Native worker availability must change before promotion can restart.",
    ],
  });
}

function makeSection({ id, task, statusWhenPassing, checks, warnings }) {
  const blockers = checks
    .filter((check) => !check.ok)
    .map((check) => `${check.id} missing ${check.missing.join(", ")}`);
  return {
    id,
    task,
    status: blockers.length > 0 ? "blocked" : statusWhenPassing,
    checks,
    blockers,
    warnings: blockers.length > 0 ? warnings.concat("Required source, test, or documentation evidence is missing.") : warnings,
  };
}

function checkTokens(id, file, tokens) {
  const missing = [];
  if (!file.text) {
    missing.push(`file:${file.relativePath}`);
  } else {
    for (const token of tokens) {
      if (!file.text.includes(token)) {
        missing.push(token);
      }
    }
  }
  return {
    id,
    path: file.relativePath,
    ok: missing.length === 0,
    missing,
  };
}

function readIfExists(file) {
  if (!fs.existsSync(file)) {
    return "";
  }
  return fs.readFileSync(file, "utf8");
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
  lines.push("# AutoJs6 Node Phase 9 Isolation Policy Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("| Section | Task | Status |");
  lines.push("| --- | --- | --- |");
  for (const section of report.sections) {
    lines.push(`| ${section.id} | ${section.task} | ${section.status} |`);
  }
  for (const section of report.sections) {
    lines.push("");
    lines.push(`## ${section.id}`);
    lines.push("");
    lines.push("| Check | OK | Path |");
    lines.push("| --- | --- | --- |");
    for (const check of section.checks) {
      lines.push(`| ${check.id} | ${check.ok} | ${check.path} |`);
    }
    appendIssues(lines, "Blockers", section.blockers);
    appendIssues(lines, "Warnings", section.warnings);
  }
  appendIssues(lines, "Decision Blockers", report.decision.blockers);
  appendIssues(lines, "Decision Warnings", report.decision.warnings);
  lines.push("");
  return lines.join("\n");
}

function appendIssues(lines, title, issues) {
  if (!issues || issues.length === 0) {
    return;
  }
  lines.push("");
  lines.push(`### ${title}`);
  lines.push("");
  issues.forEach((issue) => lines.push(`- ${issue}`));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/isolation/verify-phase9-isolation-policy.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? ["1", "true", "yes", "on"].includes(arg.slice(eq + 1).toLowerCase()) : true;
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

main();
