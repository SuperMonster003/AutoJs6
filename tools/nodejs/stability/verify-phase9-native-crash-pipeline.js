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
    runtimeArtifactEvidenceSection(files),
    symbolArchiveSection(files),
    logcatTombstoneSection(files),
    executionDescriptorSection(files),
    symbolicationSection(files),
    automationGapSection(files),
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
    schema: "autojs6-node-phase9-native-crash-pipeline-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-44 Native Crash Pipeline",
    sections,
    decision,
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase9-native-crash-pipeline", report, renderMarkdown);
  console.log("Phase 9 native crash pipeline decision: " + decision.status);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  const paths = {
    phase9Doc: "docs/nodejs/reports/phase9/NATIVE_CRASH_PIPELINE.md",
    crashIntake: "docs/nodejs/CRASH_INTAKE.md",
    nativeCrashDiagnostics: "docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md",
    maintenancePlan: "docs/nodejs/MAINTENANCE_PLAN.md",
    nativeDepsGradle: "app/native-node-deps-report.gradle.kts",
    runtimeArtifactScript: "tools/nodejs/native/verify-runtime-artifacts.js",
    runtimeArtifactGateScript: "tools/nodejs/native/generate-runtime-artifact-gate.js",
    distributionScript: "tools/nodejs/native/verify-runtime-distribution-policy.js",
    isolationPolicy: "docs/nodejs/reports/phase9/ISOLATION_POLICY.md",
    inspectorPolicy: "docs/nodejs/reports/phase9/INSPECTOR_POLICY.md",
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

function runtimeArtifactEvidenceSection(files) {
  const checks = [
    checkTokens("phase9-crash-doc", files.phase9Doc, [
      "runtime artifact manifest",
      "runtime artifact manifest and integrity reports",
      "runtime slot",
      "device ABI",
    ]),
    checkTokens("runtime-artifact-script", files.runtimeArtifactScript, [
      "runtime-artifact-manifest",
      "runtime-artifact-integrity",
      "SHA-256",
      "Build ID",
      "needed",
    ]),
    checkTokens("runtime-artifact-gate", files.runtimeArtifactGateScript, [
      "runtime-artifact-gate",
      "artifact-integrity",
      "distribution-policy",
      "decision",
    ]),
  ];
  return makeSection({
    id: "runtime-artifact-evidence",
    task: "Runtime Manifest, Hash, Build ID, And Distribution Evidence",
    statusWhenPassing: "ready",
    checks,
    warnings: [
      "Current Node 24.5 libnode artifacts may not carry GNU Build IDs; SHA-256 and ABI still provide identity until promoted artifacts include Build IDs.",
    ],
  });
}

function symbolArchiveSection(files) {
  const checks = [
    checkTokens("native-symbol-archive-task", files.nativeDepsGradle, [
      "nativeNodeSymbolArchiveReport",
      "native-symbol-archive",
      "Build ID",
      "sha256",
      "symbol_file",
      ".debug_*",
      ".symtab",
    ]),
    checkTokens("crash-intake-outputs", files.crashIntake, [
      "nativeNodeSymbolArchiveReport",
      "nativeNodeDependencyReportAppDebug",
      "nativeNodeAbiSizeReport",
      "native-symbol-archive-appDebug.txt",
      "native-deps-appDebug.txt",
    ]),
    checkTokens("phase9-crash-doc", files.phase9Doc, [
      "native dependency report",
      "native symbol archive report",
      "per-ABI SHA-256 values",
      "Build ID fields when present",
    ]),
  ];
  return makeSection({
    id: "symbol-archive",
    task: "Native Symbol Archive And Dependency Evidence",
    statusWhenPassing: "ready",
    checks,
    warnings: [],
  });
}

function logcatTombstoneSection(files) {
  const checks = [
    checkTokens("native-crash-diagnostics", files.nativeCrashDiagnostics, [
      "adb logcat -c",
      "logcat-native-crash.txt",
      "adb bugreport",
      "tombstone",
      "/data/tombstones",
      "threadtime",
    ]),
    checkTokens("crash-intake-required-attachments", files.crashIntake, [
      "logcat excerpt covering process start through crash or timeout",
      "native tombstone or Android bugreport excerpt",
      "smallest reproduction",
      "crash window only",
    ]),
    checkTokens("phase9-crash-doc", files.phase9Doc, [
      "logcat window",
      "tombstone text or Android bugreport excerpt",
      "process start through crash or timeout",
    ]),
  ];
  return makeSection({
    id: "logcat-tombstone",
    task: "Logcat, Tombstone, And Bugreport Capture",
    statusWhenPassing: "ready",
    checks,
    warnings: [],
  });
}

function executionDescriptorSection(files) {
  const checks = [
    checkTokens("crash-intake-descriptor", files.crashIntake, [
      "script entry type",
      "selected backend and feature flags",
      "Node Doctor JSON/text report",
      "packaged APK verifier report",
      "package identity",
    ]),
    checkTokens("phase9-crash-doc", files.phase9Doc, [
      "execution descriptor",
      "entry type",
      "package identity",
      "backend",
      "feature flags",
      "project/package metadata",
    ]),
    checkTokens("inspector-diagnostic-gap", files.inspectorPolicy, [
      "process.report.getReport()",
      "Node Doctor text/JSON reports",
      "execution/resource correlation",
    ]),
  ];
  return makeSection({
    id: "execution-descriptor",
    task: "Execution Descriptor And Doctor Evidence",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Crash intake defines descriptor fields, but automatic descriptor capture at crash time remains future work.",
    ],
  });
}

function symbolicationSection(files) {
  const checks = [
    checkTokens("symbolication-doc", files.nativeCrashDiagnostics, [
      "ndk-stack.cmd",
      "llvm-symbolizer",
      "Build ID",
      "SHA-256",
      "symbol_file",
    ]),
    checkTokens("phase9-crash-doc", files.phase9Doc, [
      "Match crash frames by ABI",
      "SHA-256",
      "Build ID",
      "symbol_file",
      "runtime build",
    ]),
    checkTokens("maintenance-closure", files.maintenancePlan, [
      "Native crashes require symbolication",
      "nativeNodeSymbolArchiveReport",
      "before closure",
    ]),
  ];
  return makeSection({
    id: "offline-symbolication",
    task: "Offline Symbolication Instructions",
    statusWhenPassing: "ready",
    checks,
    warnings: [],
  });
}

function automationGapSection(files) {
  const checks = [
    checkTokens("phase9-remaining-work", files.phase9Doc, [
      "one command that collects logcat",
      "active bridge/provider resource snapshots",
      "last embedded runtime timeline events",
      "automatic execution descriptor capture",
      "symbolication wrapper",
      "crash quarantine/retry integration",
    ]),
    checkTokens("isolation-crash-gap", files.isolationPolicy, [
      "Crash Containment And Recovery",
      "Current state: deferred",
      "tombstone/log",
      "quarantine the failed slot",
    ]),
  ];
  return makeSection({
    id: "automation-gap",
    task: "Crash Automation Gap Tracking",
    statusWhenPassing: "partial",
    checks,
    warnings: [
      "Collection and symbolication are documented and verifiable, but not yet a single automated redacted archive command.",
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
    warnings: blockers.length > 0 ? warnings.concat("Required source, tool, or documentation evidence is missing.") : warnings,
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
  lines.push("# AutoJs6 Node Phase 9 Native Crash Pipeline Gate");
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
      console.log("Usage: node tools/nodejs/stability/verify-phase9-native-crash-pipeline.js [--repo-root dir] [--report-dir dir]");
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
