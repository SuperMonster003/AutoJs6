#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const bundleDir = path.join(reportDir, "final-crash-evidence-bundle");
  const toolScripts = {
    collect: path.join(repoRoot, "tools/nodejs/phase11/collect-node-crash-bundle.js"),
    verify: path.join(repoRoot, "tools/nodejs/phase11/verify-node-crash-bundle.js"),
    symbolize: path.join(repoRoot, "tools/nodejs/phase11/symbolize-node-crash.js"),
  };
  const gradleFile = path.join(repoRoot, "app/node-phase11-gate.gradle.kts");
  const gradleText = read(gradleFile);
  const tooling = {
    collectNodeCrashBundle: fs.existsSync(toolScripts.collect) && gradleText.includes("\"collectNodeCrashBundle\""),
    verifyNodeCrashBundle: fs.existsSync(toolScripts.verify) && gradleText.includes("\"verifyNodeCrashBundle\""),
    symbolizeNodeCrash: fs.existsSync(toolScripts.symbolize) && gradleText.includes("\"symbolizeNodeCrash\""),
  };
  const crashToolsImplemented = Object.values(tooling).every(Boolean);
  fs.mkdirSync(bundleDir, { recursive: true });
  const nativeLibraryHashes = collectNativeLibraryHashes(repoRoot);
  const existingBundle = readJsonIfExists(path.join(bundleDir, "manifest.json"));
  const bundle = buildCrashGateBundle(existingBundle, nativeLibraryHashes, crashToolsImplemented, tooling);
  fs.writeFileSync(path.join(bundleDir, "manifest.json"), JSON.stringify(bundle, null, 2) + "\n");
  fs.writeFileSync(path.join(bundleDir, "README.md"), renderBundleReadme(bundle));

  const blockers = [];
  if (!isImplemented(bundle.crashMarker)) blockers.push("safe native crash marker is not implemented");
  if (bundle.automaticCollection !== true) blockers.push("next-launch crash evidence collection is not automatic");
  if (!isImplemented(bundle.resourceSnapshot)) blockers.push("resource snapshot is not captured for crash/hang evidence");
  if (!isImplemented(bundle.timelineTail)) blockers.push("runtime timeline tail is not captured for crash evidence");
  if (!isImplemented(bundle.symbolization)) blockers.push("crash bundle collection/verification/symbolization tooling is not fully implemented");
  if (!isImplemented(bundle.chaosInjection)) blockers.push("chaos/crash injection gate is not implemented");

  const report = {
    schema: "autojs6-node-final-crash-gate-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeFinalCrashGate",
    tasks: ["S11-13", "S11-14", "S11-15", "S11-16", "S11-17", "S11-18"],
    bundle: {
      directory: relativePath(repoRoot, bundleDir),
      manifest: relativePath(repoRoot, path.join(bundleDir, "manifest.json")),
      nativeLibraryCount: Array.isArray(bundle.nativeLibraryHashes) ? bundle.nativeLibraryHashes.length : 0,
    },
    tooling,
    evidence: {
      crashMarkerImplemented: isImplemented(bundle.crashMarker),
      automaticCollection: bundle.automaticCollection === true,
      resourceSnapshotImplemented: isImplemented(bundle.resourceSnapshot),
      timelineTailImplemented: isImplemented(bundle.timelineTail),
      symbolizationImplemented: isImplemented(bundle.symbolization),
      chaosInjectionImplemented: isImplemented(bundle.chaosInjection),
    },
    blockers,
    decision: {
      status: blockers.length === 0 ? "ready" : "blocked",
      crashInjectionSafeForMainApp: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-crash-gate", report, renderMarkdown);
  console.log("Final crash gate decision: " + report.decision.status);
  if (options.failOnBlocked && report.decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function collectNativeLibraryHashes(repoRoot) {
  const root = path.join(repoRoot, "app/src/main/jniLibs");
  if (!fs.existsSync(root)) return [];
  const entries = [];
  for (const abi of fs.readdirSync(root).sort()) {
    const lib = path.join(root, abi, "libnode.so");
    if (!fs.existsSync(lib)) continue;
    entries.push({
      abi,
      library: "libnode.so",
      sha256: sha256(lib),
      path: relativePath(repoRoot, lib),
    });
  }
  return entries;
}

const REQUIRED_RESOURCE_ENTRIES = [
  "ResourceRegistry",
  "pending bridge calls",
  "timers",
  "open files",
  "image handles",
  "network requests",
  "WebSockets",
  "sensors",
  "UI handles",
  "workers",
  "execution queue",
];

const CHAOS_SCENARIOS = [
  "SIGABRT",
  "native exception boundary",
  "V8 fatal error simulation",
  "service process kill",
  "watchdog hang",
  "adapter invalid handle",
  "destroy during pending callback",
  "crash during packaged execution",
];

function buildCrashGateBundle(existing, nativeLibraryHashes, crashToolsImplemented, tooling) {
  const base = {
    schema: "autojs6-node-final-crash-evidence-bundle-v1",
    generatedAt: new Date().toISOString(),
    generatedBy: "verifyNodeFinalCrashGate",
    crashInjected: false,
    crashMarker: {
      implemented: false,
      allowedSignalHandlerWork: ["write preopened fd", "record fixed-size marker"],
      forbiddenSignalHandlerWork: ["JNI", "malloc", "JSON", "formatted logging", "Java callbacks"],
    },
    automaticCollection: false,
    tombstoneReference: "documented",
    logcatTail: "documented",
    nativeLibraryHashes,
    buildIds: {
      status: "pending_runtime_build_id_archive",
    },
    runtimeDescriptor: {
      runtimeSlot: "node24_5",
      adapter: "legacy_jni",
    },
    resourceSnapshot: {
      implemented: false,
      requiredEntries: REQUIRED_RESOURCE_ENTRIES,
    },
    timelineTail: {
      implemented: false,
      requiredEventCount: 256,
    },
    symbolization: {
      tools: ["symbolizeNodeCrash", "collectNodeCrashBundle", "verifyNodeCrashBundle"],
      implemented: crashToolsImplemented,
      report: "build/reports/nodejs/final-crash-symbolization.json",
    },
    tooling,
    chaosInjection: {
      implemented: false,
      scenarios: CHAOS_SCENARIOS,
    },
  };
  if (!existing || existing.schema !== base.schema) {
    return base;
  }
  return {
    ...base,
    ...existing,
    generatedAt: base.generatedAt,
    nativeLibraryHashes: Array.isArray(existing.nativeLibraryHashes) && existing.nativeLibraryHashes.length > 0
      ? existing.nativeLibraryHashes
      : nativeLibraryHashes,
    runtimeDescriptor: {
      ...base.runtimeDescriptor,
      ...(existing.runtimeDescriptor || {}),
    },
    crashMarker: mergeEvidence(base.crashMarker, existing.crashMarker),
    automaticCollection: existing.automaticCollection === true,
    resourceSnapshot: mergeEvidence(base.resourceSnapshot, existing.resourceSnapshot),
    timelineTail: mergeEvidence(base.timelineTail, existing.timelineTail),
    symbolization: {
      ...base.symbolization,
      ...(typeof existing.symbolization === "object" ? existing.symbolization : {}),
      implemented: isImplemented(existing.symbolization) || crashToolsImplemented,
    },
    tooling,
    chaosInjection: mergeEvidence(base.chaosInjection, existing.chaosInjection),
  };
}

function mergeEvidence(defaults, value) {
  if (!value) {
    return defaults;
  }
  if (typeof value === "string") {
    return {
      ...defaults,
      path: value,
    };
  }
  if (typeof value === "object") {
    return {
      ...defaults,
      ...value,
    };
  }
  return defaults;
}

function isImplemented(value) {
  return value === true || Boolean(value && typeof value === "object" && value.implemented === true);
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) {
    return null;
  }
  try {
    return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
  } catch (_) {
    return null;
  }
}

function stripBom(value) {
  return value.replace(/^\uFEFF/, "");
}

function renderBundleReadme(bundle) {
  return [
    "# Final Crash Evidence Bundle",
    "",
    "This bundle is generated by `verifyNodeFinalCrashGate` as the Phase 11 schema anchor.",
    "It is intentionally marked blocked until automatic crash-time capture and chaos injection exist.",
    "",
    `- Generated: ${bundle.generatedAt}`,
    `- Crash injected: ${bundle.crashInjected}`,
    `- Native libraries: ${bundle.nativeLibraryHashes.length}`,
    "",
  ].join("\n");
}

function renderMarkdown(report) {
  const lines = [
    "# Final Crash Gate",
    "",
    `- Decision: ${report.decision.status}`,
    `- Evidence bundle: ${report.bundle.manifest}`,
    `- Native libraries: ${report.bundle.nativeLibraryCount}`,
    `- Safe crash marker: ${report.evidence.crashMarkerImplemented}`,
    `- Automatic collection: ${report.evidence.automaticCollection}`,
    `- Resource snapshot: ${report.evidence.resourceSnapshotImplemented}`,
    `- Timeline tail: ${report.evidence.timelineTailImplemented}`,
    `- Chaos injection: ${report.evidence.chaosInjectionImplemented}`,
    "",
    "## Blockers",
    "",
  ];
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  return lines.join("\n") + "\n";
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-final-crash-gate.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
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
