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
    inspectorSessionSection(files),
    devtoolsWorkflowSection(files),
    profilingSection(files),
    heapSnapshotSection(files),
    diagnosticReportSection(files),
    securityGateSection(files),
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
    schema: "autojs6-node-phase9-inspector-policy-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-21..S9-26 Inspector And Diagnostics Policy",
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-inspector-policy", report, renderMarkdown);
  console.log("Phase 9 inspector policy decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    capabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
    runtimeBridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
    bridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
    bridgeLifecycle: "app/src/main/cpp/node_bridge_lifecycle.cpp",
    designTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorDesignGateInstrumentationTest.kt",
    securityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeInspectorSecurityGateInstrumentationTest.kt",
    doctorTest: "app/src/androidTest/java/org/autojs/autojs/ui/settings/NodeDoctorReportGeneratorInstrumentationTest.kt",
    doctorGenerator: "app/src/main/java/org/autojs/autojs/ui/settings/NodeDoctorReportGenerator.kt",
    inspectorDesign: "docs/nodejs/design/INSPECTOR.md",
    phase8Design: "docs/nodejs/reports/phase8/INSPECTOR_DESIGN_GATE.md",
    phase8Devtools: "docs/nodejs/reports/phase8/DEVTOOLS_ADB_FORWARD_GATE.md",
    phase8Profiling: "docs/nodejs/reports/phase8/INSPECTOR_PROFILING_GATE.md",
    phase8Security: "docs/nodejs/reports/phase8/INSPECTOR_SECURITY_GATE.md",
    compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
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

function inspectorSessionSection(files) {
  const checks = [
    checkTokens("feature-flag-disabled", files.capabilities, [
      'id = "inspector"',
      "gradleProperty = null",
      "defaultEnabled = false",
      "buildTimeOverrideAllowed = false",
      "projectJsonOverrideAllowed = false",
      "packagedApkOverrideAllowed = false",
      'securityLevel = "high"',
      'status = "disabled"',
    ]),
    checkTokens("startup-env-filter", files.runtimeBridge, [
      "NODE_OPTIONS",
      "NODE_INSPECT_RESUME_ON_START",
      "NODE_STARTUP_ENV_NAMES",
    ]),
    checkTokens("process-debug-surfaces-disabled", files.bridgeSources, [
      '__autojs6_define_disabled_process_api("_debugProcess", false)',
      "process.features",
      "inspector: false",
      "debug: false",
      "allowedNodeEnvironmentFlags",
      "execArgv",
    ]),
    checkTokens("disabled-builtin-policy", files.bridgeSources, [
      "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
      "disabledBuiltins",
      '"inspector": true',
      "process.getBuiltinModule",
    ]),
    checkTokens("design-gate-test", files.designTest, [
      "NodeInspectorDesignGateInstrumentationTest",
      "inspectorFeatureFlagCannotBeEnabledByMetadata",
      "scriptVisibleInspectorSurfacesStayDisabled",
      "require(\"inspector\")",
      "require(\"node:inspector\")",
      "process._debugProcess",
      "process.allowedNodeEnvironmentFlags",
      "process.features.inspector === false",
    ]),
    checkTokens("design-doc", files.inspectorDesign, [
      "Status: disabled",
      "no inspector in normal builds",
      "no inspector in release packaged APKs",
      "debug-build-only",
      "explicit user action",
      "loopback binding only",
    ]),
    checkTokens("phase8-design-report", files.phase8Design, [
      "Status: disabled design gate complete",
      "no debugger TCP or WebSocket endpoint",
      "no build-time, `project.json`, or packaged APK metadata override",
      "S8-38 and later",
    ]),
  ];
  return makeSection({
    id: "debug-only-inspector-session",
    task: "S9-21 Debug-only Inspector Session",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Current evidence proves the disabled policy. A positive debug-only attach implementation still needs endpoint lifecycle, consent UI, loopback port binding, and attach tests.",
    ],
  });
}

function devtoolsWorkflowSection(files) {
  const checks = [
    checkTokens("devtools-doc-deferred", files.phase8Devtools, [
      "Status: deferred by disabled inspector gate",
      "no safe `adb forward`",
      "no Chrome DevTools URL",
      "Bind the device listener only to loopback",
      "adb -s QV710AF65F forward",
      "no packaged release attach path",
    ]),
    checkTokens("design-workflow", files.inspectorDesign, [
      "ADB Forward And DevTools Workflow",
      "no device endpoint",
      "no port",
      "no Chrome DevTools URL",
      "DevTools attaches through `127.0.0.1:<host-port>`",
    ]),
  ];
  return makeSection({
    id: "devtools-adb-workflow",
    task: "S9-22 ADB DevTools Workflow",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "No host command is emitted until a runtime-owned debug endpoint exists.",
    ],
  });
}

function profilingSection(files) {
  const checks = [
    checkTokens("profiling-doc-deferred", files.phase8Profiling, [
      "Status: deferred by disabled inspector gate",
      "does not generate CPU profiles",
      ".cpuprofile",
      "app-private diagnostics",
      "packaged release denial",
    ]),
    checkTokens("profiling-security-test", files.securityTest, [
      "profileArtifactsRemainAbsentAndDoNotLeakSensitiveMetadata",
      "cpuprofile",
      "profile-summary.json",
      "assertNoInspectorDiagnostics",
    ]),
  ];
  return makeSection({
    id: "cpu-profile",
    task: "S9-23 CPU Profile",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "CPU profile start/stop remains unavailable until the debug-only inspector session exists.",
    ],
  });
}

function heapSnapshotSection(files) {
  const checks = [
    checkTokens("heap-doc-deferred", files.phase8Profiling, [
      "heap snapshots",
      ".heapsnapshot",
      "allocation sampling",
      "redaction checks",
      "explicit export/pull path",
    ]),
    checkTokens("heap-artifact-absence-test", files.securityTest, [
      "heapsnapshot",
      "allocation.json",
      "findProfileArtifacts",
      "S8_40_PROJECT_SECRET_SHOULD_NOT_LEAK",
    ]),
  ];
  return makeSection({
    id: "heap-snapshot-allocation",
    task: "S9-24 Heap Snapshot And Allocation Sampling",
    statusWhenPassing: "deferred",
    checks,
    warnings: [
      "Heap snapshot and allocation sampling remain unavailable until inspector/profiler control and app-private export policy exist.",
    ],
  });
}

function diagnosticReportSection(files) {
  const checks = [
    checkTokens("controlled-process-report", files.bridgeSources, [
      "__autojs6_process_report_get_report",
      "__autojs6_process_report_disabled",
      "process.report.writeReport is disabled",
      "environmentVariables",
      "resourceUsage",
      "javascriptHeap",
    ]),
    checkTokens("doctor-generator-redaction", files.doctorGenerator, [
      "AutoJs6 Node Doctor Report",
      "scriptContentsIncluded",
      "environmentValuesIncluded",
      "pathsRedacted",
      "redactPath",
    ]),
    checkTokens("doctor-test", files.doctorTest, [
      "generatesPrivacyRedactedTextAndJsonReports",
      "AutoJs6 Node Doctor Report",
      "scriptContentsIncluded",
      "environmentValuesIncluded",
      "pathsRedacted",
    ]),
  ];
  return makeSection({
    id: "diagnostic-report",
    task: "S9-25 Node Diagnostic Report",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "A controlled process.report getReport and Node Doctor report exist, but S9-25 still lacks active libuv handle inventory, native Build ID merge, inspector/profiler state, and full execution/resource correlation.",
    ],
  });
}

function securityGateSection(files) {
  const checks = [
    checkTokens("native-startup-hardening", files.bridgeLifecycle, [
      "kDisableNodeOptionsEnv",
      "kNoParseGlobalDebugVariables",
    ]),
    checkTokens("security-test", files.securityTest, [
      "NodeInspectorSecurityGateInstrumentationTest",
      "releaseAndPackagedMetadataCannotEnableInspector",
      "nonLoopbackInspectorHostsRemainInertAndLeaveNoListeningPort",
      "inspectorPortIsNotLeftListeningAfterScriptStopOrTimeout",
      "profileArtifactsRemainAbsentAndDoNotLeakSensitiveMetadata",
      "--inspect=127.0.0.1",
      "--inspect=$host:$port",
    ]),
    checkTokens("security-report", files.phase8Security, [
      "Status: disabled-state security gate complete",
      "release build metadata cannot enable",
      "non-loopback hosts",
      "no listener survives normal script completion",
      "no listener survives script timeout",
      "no profiler artifact is generated",
    ]),
    checkTokens("compatibility-profile", files.compatibilityProfile, [
      "`inspector` is intentionally disabled",
      "S8-40 extends the boundary",
      "ADB forwarding, Chrome DevTools attach, CPU profiles, heap snapshots",
      "future positive implementation must be debug-build-only",
    ]),
  ];
  return makeSection({
    id: "inspector-security",
    task: "S9-26 Inspector Security Gate",
    statusWhenPassing: "ready",
    checks,
    warnings: [
      "Ready here means the disabled-state security gate is ready; a positive inspector feature would need a new security gate before promotion.",
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
  lines.push("# AutoJs6 Node Phase 9 Inspector Policy Gate");
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
      console.log("Usage: node tools/nodejs/debug/verify-phase9-inspector-policy.js [--repo-root dir] [--report-dir dir]");
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
