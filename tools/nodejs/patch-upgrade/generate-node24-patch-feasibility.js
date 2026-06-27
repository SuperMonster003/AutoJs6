#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node24-patch-upgrade-feasibility-v1";
const DEFAULT_TARGET_VERSION = "24.17.0";
const DEFAULT_TARGET_CODENAME = "Krypton";
const DEFAULT_TARGET_RELEASE_URL = "https://nodejs.org/en/blog/release/v24.17.0";
const NDK_VERSION = "26.1.10909125";
const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];
const SYMBOL_SAMPLE_LIMIT = 16;
const NATIVE_NODE_CAPABILITIES =
  "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt";

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const targetVersion = normalizeVersion(options.targetVersion || DEFAULT_TARGET_VERSION);
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs/patch-upgrade"));
  const docReport = options.docReport ? path.resolve(options.docReport) : null;
  const tools = findAndroidTools(repoRoot);
  const runtime = readRuntimeConstants(repoRoot);
  const currentHeaderDir = normalizeHeaderDir(
    path.join(repoRoot, "app/src/main/cpp", `node-v${runtime.nodeHeaderVersion}`, "include/node"),
  );
  const currentJniLibsDir = path.join(repoRoot, "app/src/main/jniLibs");
  const candidateHeadersDir = options.candidateHeaders
    ? normalizeHeaderDir(path.resolve(options.candidateHeaders))
    : null;
  const candidateJniLibsDir = options.candidateJniLibs
    ? path.resolve(options.candidateJniLibs)
    : null;

  const currentLibraries = inspectJniLibs(currentJniLibsDir, tools);
  const candidateLibraries = candidateJniLibsDir
    ? inspectJniLibs(candidateJniLibsDir, tools)
    : inspectMissingCandidateLibs(candidateJniLibsDir);

  const current = {
    version: runtime.nodeHeaderVersion,
    expectedProcessVersionPrefix: runtime.nodeVersionExpectedPrefix,
    headers: inspectHeaders(currentHeaderDir),
    jniLibsDir: relativePath(repoRoot, currentJniLibsDir),
    libraries: publicLibraryMap(currentLibraries),
    compatibilityProfile: summarizeCompatibilityProfile(
      path.join(repoRoot, "app/build/reports/nodejs/compatibility-profile.json"),
      repoRoot,
    ),
    performance: summarizeLatestPerformanceReport(repoRoot),
    differential: summarizeDifferentialReport(
      path.join(repoRoot, "build/reports/nodejs/differential/modern-core-differential.json"),
      repoRoot,
    ),
    packagedApk: summarizePackagedBaseline(runtime.nodeVersionExpectedPrefix),
  };

  const candidate = {
    version: targetVersion,
    source: {
      releaseUrl: options.targetReleaseUrl || DEFAULT_TARGET_RELEASE_URL,
      codename: options.targetCodename || DEFAULT_TARGET_CODENAME,
      selectedAt: new Date().toISOString(),
    },
    headers: candidateHeadersDir ? inspectHeaders(candidateHeadersDir) : missingHeaders(),
    jniLibsDir: candidateJniLibsDir ? relativePath(repoRoot, candidateJniLibsDir) : null,
    libraries: publicLibraryMap(candidateLibraries),
    compatibilityProfile: options.candidateCompatibilityProfile
      ? summarizeCompatibilityProfile(path.resolve(options.candidateCompatibilityProfile), repoRoot)
      : missingEvidence("candidate compatibility profile was not supplied"),
    performance: options.candidatePerformanceReport
      ? summarizePerformanceReport(path.resolve(options.candidatePerformanceReport), repoRoot)
      : missingEvidence("candidate performance report was not supplied"),
    differential: options.candidateDifferentialReport
      ? summarizeDifferentialReport(path.resolve(options.candidateDifferentialReport), repoRoot)
      : missingEvidence("candidate CJS/ESM differential report was not supplied"),
    packagedApk: options.candidatePackagedReport
      ? summarizePackagedReport(path.resolve(options.candidatePackagedReport), targetVersion, repoRoot)
      : missingEvidence("candidate packaged APK smoke report was not supplied"),
  };

  const comparison = buildComparison(current, candidate, currentLibraries, candidateLibraries);
  const decision = buildDecision(targetVersion, runtime, comparison);
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    target: {
      currentVersion: runtime.nodeHeaderVersion,
      candidateVersion: targetVersion,
      candidateReleaseUrl: candidate.source.releaseUrl,
      defaultRuntimeUnchanged: true,
    },
    tools: summarizeTools(tools),
    inputs: {
      candidateHeaders: candidateHeadersDir ? relativePath(repoRoot, candidateHeadersDir) : null,
      candidateJniLibs: candidateJniLibsDir ? relativePath(repoRoot, candidateJniLibsDir) : null,
      candidateCompatibilityProfile: options.candidateCompatibilityProfile || null,
      candidatePerformanceReport: options.candidatePerformanceReport || null,
      candidateDifferentialReport: options.candidateDifferentialReport || null,
      candidatePackagedReport: options.candidatePackagedReport || null,
    },
    current,
    candidate,
    comparison,
    decision,
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "node24-patch-upgrade-feasibility.json");
  const markdownPath = path.join(reportDir, "node24-patch-upgrade-feasibility.md");
  const markdown = renderMarkdown(report);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, markdown);
  if (docReport) {
    fs.mkdirSync(path.dirname(docReport), { recursive: true });
    fs.writeFileSync(docReport, markdown);
  }

  console.log("Node 24 patch feasibility JSON: " + jsonPath);
  console.log("Node 24 patch feasibility Markdown: " + markdownPath);
  if (docReport) {
    console.log("Node 24 patch feasibility doc: " + docReport);
  }
  console.log("Node 24 patch feasibility decision: " + decision.goNoGo);

  if (options.failOnNoGo && decision.goNoGo === "no-go") {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-no-go"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      printHelp();
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = toCamelCase(rawKey);
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

function printHelp() {
  console.log([
    "Usage: node tools/nodejs/patch-upgrade/generate-node24-patch-feasibility.js [options]",
    "",
    "Options:",
    "  --target-version <version>                  Candidate Node 24 patch. Defaults to 24.17.0.",
    "  --target-release-url <url>                  Source URL for the selected candidate release.",
    "  --candidate-jni-libs <dir>                  Directory containing <abi>/libnode.so candidate files.",
    "  --candidate-headers <dir>                   Candidate include/node directory or Node source include root.",
    "  --candidate-compatibility-profile <json>    Candidate compatibility profile JSON.",
    "  --candidate-performance-report <json>       Candidate performance JSON.",
    "  --candidate-differential-report <json>      Candidate modern core differential JSON.",
    "  --candidate-packaged-report <file>          Candidate packaged APK smoke text/JSON report.",
    "  --report-dir <dir>                          Output directory. Defaults to build/reports/nodejs/patch-upgrade.",
    "  --doc-report <file>                         Also write the Markdown report to a tracked docs path.",
    "  --fail-on-no-go                             Exit non-zero when the report decision is no-go.",
  ].join("\n"));
}

function toCamelCase(value) {
  return value.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
}

function parseBoolean(value) {
  return ["1", "true", "yes"].includes(String(value).toLowerCase());
}

function normalizeVersion(version) {
  return String(version).replace(/^v/, "");
}

function readRuntimeConstants(repoRoot) {
  const file = path.join(repoRoot, NATIVE_NODE_CAPABILITIES);
  const text = readText(file);
  return {
    source: NATIVE_NODE_CAPABILITIES,
    nodeHeaderVersion: findStringConstant(text, "NODE_HEADER_VERSION"),
    nodeVersionExpectedPrefix: findStringConstant(text, "NODE_VERSION_EXPECTED_PREFIX"),
  };
}

function findStringConstant(text, name) {
  const match = text.match(new RegExp(`${name}\\s*=\\s*"([^"]+)"`));
  if (!match) {
    throw new Error(`Missing ${name} in ${NATIVE_NODE_CAPABILITIES}`);
  }
  return match[1];
}

function normalizeHeaderDir(dir) {
  if (!dir) {
    return dir;
  }
  const candidates = [
    dir,
    path.join(dir, "include/node"),
    path.join(dir, "include"),
    path.join(dir, "node"),
  ];
  return candidates.find((candidate) => isFile(path.join(candidate, "node_version.h"))) || dir;
}

function inspectHeaders(headerDir) {
  const versionHeader = path.join(headerDir, "node_version.h");
  if (!isFile(versionHeader)) {
    return {
      present: false,
      path: headerDir,
      reason: "node_version.h is missing",
    };
  }
  const text = readText(versionHeader);
  const major = findDefineInt(text, "NODE_MAJOR_VERSION");
  const minor = findDefineInt(text, "NODE_MINOR_VERSION");
  const patch = findDefineInt(text, "NODE_PATCH_VERSION");
  return {
    present: true,
    path: headerDir,
    nodeVersion: [major, minor, patch].filter((value) => value !== null).join("."),
    nodeModuleVersion: findDefineInt(text, "NODE_MODULE_VERSION"),
    napiVersion: findDefineInt(text, "NODE_API_SUPPORTED_VERSION_MAX"),
    nodeSharedMode: findDefineString(text, "NODE_SHARED_MODE"),
    headerCount: countHeaderFiles(headerDir),
  };
}

function missingHeaders() {
  return {
    present: false,
    path: null,
    reason: "candidate headers were not supplied",
  };
}

function findDefineInt(text, name) {
  const match = text.match(new RegExp(`#define\\s+${name}\\s+(\\d+)`));
  return match ? Number(match[1]) : null;
}

function findDefineString(text, name) {
  const match = text.match(new RegExp(`#define\\s+${name}\\s+(.+)`));
  return match ? match[1].trim() : null;
}

function countHeaderFiles(headerDir) {
  if (!isDirectory(headerDir)) {
    return 0;
  }
  return fs.readdirSync(headerDir)
    .filter((file) => file.endsWith(".h"))
    .length;
}

function inspectJniLibs(root, tools) {
  const result = {};
  for (const abi of REQUIRED_ABIS) {
    result[abi] = inspectLibrary(path.join(root, abi, "libnode.so"), tools);
  }
  return result;
}

function inspectMissingCandidateLibs() {
  const result = {};
  for (const abi of REQUIRED_ABIS) {
    result[abi] = {
      present: false,
      path: null,
      reason: "candidate jniLibs directory was not supplied",
    };
  }
  return result;
}

function inspectLibrary(file, tools) {
  if (!isFile(file)) {
    return {
      present: false,
      path: file,
      reason: "libnode.so is missing",
    };
  }
  const stat = fs.statSync(file);
  const publicInfo = {
    present: true,
    path: file,
    sizeBytes: stat.size,
    sha256: sha256(file),
    buildId: null,
    needed: [],
    sections: {
      debugInfo: false,
      symtab: false,
      dynsym: false,
    },
    symbols: {
      definedCount: null,
      undefinedCount: null,
      definedSample: [],
      undefinedSample: [],
    },
    toolStatus: tools ? "available" : "missing-ndk-tools",
  };
  if (!tools) {
    publicInfo._symbolSets = {
      defined: new Set(),
      undefined: new Set(),
    };
    return publicInfo;
  }

  publicInfo.needed = parseNeeded(tools, file);
  publicInfo.sections = parseSections(tools, file);
  publicInfo.buildId = parseBuildId(tools, file);
  const defined = parseSymbols(tools, file, ["-D", "--defined-only"]);
  const undefinedSymbols = parseSymbols(tools, file, ["-D", "--undefined-only"]);
  publicInfo.symbols = {
    definedCount: defined.size,
    undefinedCount: undefinedSymbols.size,
    definedSample: Array.from(defined).slice(0, SYMBOL_SAMPLE_LIMIT),
    undefinedSample: Array.from(undefinedSymbols).slice(0, SYMBOL_SAMPLE_LIMIT),
  };
  publicInfo._symbolSets = {
    defined,
    undefined: undefinedSymbols,
  };
  return publicInfo;
}

function publicLibraryMap(libraries) {
  const result = {};
  for (const [abi, info] of Object.entries(libraries)) {
    const copy = { ...info };
    delete copy._symbolSets;
    result[abi] = copy;
  }
  return result;
}

function findAndroidTools(repoRoot) {
  const sdkDir = findSdkDir(repoRoot);
  if (!sdkDir) {
    return null;
  }
  const ndkDir = path.join(sdkDir, "ndk", NDK_VERSION);
  const prebuiltRoot = path.join(ndkDir, "toolchains/llvm/prebuilt");
  if (!isDirectory(prebuiltRoot)) {
    return null;
  }
  const hostPrefix = hostTagPrefix();
  const prebuiltDir = fs.readdirSync(prebuiltRoot)
    .map((entry) => path.join(prebuiltRoot, entry))
    .find((entry) => isDirectory(entry) && path.basename(entry).startsWith(hostPrefix));
  if (!prebuiltDir) {
    return null;
  }
  const exe = process.platform === "win32" ? ".exe" : "";
  const readelf = path.join(prebuiltDir, "bin", `llvm-readelf${exe}`);
  const nm = path.join(prebuiltDir, "bin", `llvm-nm${exe}`);
  if (!isFile(readelf) || !isFile(nm)) {
    return null;
  }
  return {
    ndkVersion: NDK_VERSION,
    ndkDir,
    readelf,
    nm,
  };
}

function findSdkDir(repoRoot) {
  const localProperties = path.join(repoRoot, "local.properties");
  if (isFile(localProperties)) {
    const text = readText(localProperties);
    for (const line of text.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed.startsWith("sdk.dir=")) {
        const value = unescapeJavaProperty(trimmed.slice("sdk.dir=".length));
        if (isDirectory(value)) {
          return value;
        }
      }
    }
  }
  return ["ANDROID_HOME", "ANDROID_SDK_ROOT"]
    .map((name) => process.env[name])
    .filter(Boolean)
    .find((value) => isDirectory(value)) || null;
}

function unescapeJavaProperty(value) {
  return value
    .replace(/\\:/g, ":")
    .replace(/\\ /g, " ")
    .replace(/\\\\/g, "\\");
}

function hostTagPrefix() {
  if (process.platform === "win32") {
    return "windows";
  }
  if (process.platform === "darwin") {
    return "darwin";
  }
  return "linux";
}

function parseNeeded(tools, file) {
  const output = runTool(tools.readelf, ["-d", "--wide", file]);
  const pattern = /Shared library: \[(.+)]/;
  return output.lines
    .map((line) => line.match(pattern))
    .filter(Boolean)
    .map((match) => match[1])
    .sort();
}

function parseSections(tools, file) {
  const output = runTool(tools.readelf, ["-S", "--wide", file]);
  return {
    debugInfo: output.lines.some((line) => line.includes(".debug_info") || line.includes(".debug_")),
    symtab: output.lines.some((line) => line.includes(".symtab")),
    dynsym: output.lines.some((line) => line.includes(".dynsym")),
  };
}

function parseBuildId(tools, file) {
  const output = runTool(tools.readelf, ["-n", "--wide", file]);
  const match = output.lines
    .map((line) => line.match(/Build ID:\s*([0-9a-fA-F]+)/))
    .find(Boolean);
  return match ? match[1] : null;
}

function parseSymbols(tools, file, args) {
  const output = runTool(tools.nm, [...args, file]);
  const symbols = output.lines
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const parts = line.split(/\s+/).filter(Boolean);
      return parts[parts.length - 1];
    })
    .filter((symbol) => symbol && symbol !== "U" && symbol !== "w" && symbol !== "v")
    .map((symbol) => symbol.split("@")[0])
    .sort();
  return new Set(symbols);
}

function runTool(command, args) {
  const result = childProcess.spawnSync(command, args, {
    encoding: "utf8",
    maxBuffer: 128 * 1024 * 1024,
  });
  const output = [result.stdout || "", result.stderr || ""].join("\n");
  return {
    status: result.status,
    lines: output.split(/\r?\n/).filter(Boolean),
  };
}

function summarizeCompatibilityProfile(file, repoRoot) {
  if (!isFile(file)) {
    return missingEvidence("compatibility profile is missing: " + relativePath(repoRoot, file));
  }
  const json = readJson(file);
  return {
    present: true,
    path: relativePath(repoRoot, file),
    schema: json.schema || null,
    generatedAt: json.generatedAt || null,
    runtime: {
      profileName: json.runtime?.profileName || null,
      engineVersion: json.runtime?.engineVersion || null,
      nodeHeaderVersion: json.runtime?.nodeHeaderVersion || null,
      nodeVersionExpectedPrefix: json.runtime?.nodeVersionExpectedPrefix || null,
      defaultBackend: json.runtime?.defaultBackend || null,
      routeSuffixes: json.runtime?.routeSuffixes || [],
      commonJsModuleExtensions: json.runtime?.commonJsModuleExtensions || [],
      packageJsonExportConditions: json.runtime?.packageJsonExportConditions || [],
    },
    featureFlags: summarizeFeatureFlags(json.featureFlags),
  };
}

function summarizeFeatureFlags(flags) {
  if (!Array.isArray(flags)) {
    return {};
  }
  return flags.reduce((acc, flag) => {
    const key = flag.status || "unknown";
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});
}

function summarizeLatestPerformanceReport(repoRoot) {
  const perfDirs = [
    path.join(repoRoot, "app/build/reports/nodejs/perf"),
    path.join(repoRoot, "build/reports/nodejs/node-profile-v1-ga/performance"),
  ];
  const candidates = perfDirs
    .filter((dir) => isDirectory(dir))
    .flatMap((dir) => fs.readdirSync(dir)
      .filter((file) => file.endsWith(".json"))
      .map((file) => path.join(dir, file)))
    .map((file) => {
      try {
        const json = readJson(file);
        return { file, json, mtimeMs: fs.statSync(file).mtimeMs };
      } catch (_error) {
        return null;
      }
    })
    .filter((entry) => entry && entry.json.schema === "autojs6-node-performance-v1")
    .sort((left, right) => right.mtimeMs - left.mtimeMs);
  if (candidates.length === 0) {
    return missingEvidence("no autojs6-node-performance-v1 report was found");
  }
  return summarizePerformanceJson(candidates[0].json, candidates[0].file, repoRoot);
}

function summarizePerformanceReport(file, repoRoot) {
  if (!isFile(file)) {
    return missingEvidence("performance report is missing: " + relativePath(repoRoot, file));
  }
  return summarizePerformanceJson(readJson(file), file, repoRoot);
}

function summarizePerformanceJson(json, file, repoRoot) {
  const metrics = json.metrics || {};
  return {
    present: true,
    path: relativePath(repoRoot, file),
    schema: json.schema || null,
    generatedAtEpochMs: json.generatedAtEpochMs || null,
    device: {
      manufacturer: json.device?.manufacturer || null,
      model: json.device?.model || null,
      sdkInt: json.device?.sdkInt || null,
      primaryAbi: json.device?.primaryAbi || null,
    },
    processVersion: findNestedValue(json, "embedded_script.node_version") || null,
    metrics: {
      serviceProcessStartTimeMs: metrics.serviceProcessStartTimeMs ?? null,
      libnodeLoadMs: metrics.libnodeLoadMs ?? null,
      libnodeLoadWarmMs: metrics.libnodeLoadWarmMs ?? null,
      firstScriptExecutionWallMs: metrics.firstScriptExecutionWallMs ?? null,
      warmScriptExecutionWallMs: metrics.warmScriptExecutionWallMs ?? null,
      coldRunWallMs: metrics.coldRunWallMs ?? null,
      warmRunWallMs: metrics.warmRunWallMs ?? null,
      appRssDeltaAfterFirstScriptKb: metrics.appRssDeltaAfterFirstScriptKb ?? null,
      appRssDeltaAfterDestroyKb: metrics.appRssDeltaAfterDestroyKb ?? null,
    },
  };
}

function summarizeDifferentialReport(file, repoRoot) {
  if (!isFile(file)) {
    return missingEvidence("modern core differential report is missing: " + relativePath(repoRoot, file));
  }
  const json = readJson(file);
  return {
    present: true,
    path: relativePath(repoRoot, file),
    generatedAt: json.generatedAt || null,
    desktopNode: json.desktopNode || null,
    summary: json.summary || null,
  };
}

function summarizePackagedBaseline(expectedPrefix) {
  return {
    present: true,
    source: "packaging smoke markers",
    expectedMarker: `packaged.node.version=${expectedPrefix}`,
    note: "Current packaged behavior is covered by the existing packaged smoke gate; this task does not rebuild a candidate APK.",
  };
}

function summarizePackagedReport(file, targetVersion, repoRoot) {
  if (!isFile(file)) {
    return missingEvidence("packaged report is missing: " + relativePath(repoRoot, file));
  }
  const text = readText(file);
  const marker = `packaged.node.version=v${targetVersion}`;
  return {
    present: true,
    path: relativePath(repoRoot, file),
    expectedMarker: marker,
    markerFound: text.includes(marker),
  };
}

function missingEvidence(reason) {
  return {
    present: false,
    reason,
  };
}

function buildComparison(current, candidate, currentLibraries, candidateLibraries) {
  const headerStatus = compareHeaders(current.headers, candidate.headers);
  const libraryComparisons = {};
  for (const abi of REQUIRED_ABIS) {
    libraryComparisons[abi] = compareLibraries(currentLibraries[abi], candidateLibraries[abi]);
  }
  return {
    headers: headerStatus,
    libraries: libraryComparisons,
    dependencies: compareEvidence(
      "DT_NEEDED dependencies",
      allPresent(candidateLibraries),
      "blocked until all candidate ABI libnode.so files are supplied",
    ),
    processVersions: compareProcessVersions(current, candidate),
    startupAndRss: comparePerformance(current.performance, candidate.performance),
    cjsEsmBehavior: compareDifferential(current.differential, candidate.differential),
    packagedApkBehavior: comparePackaged(current.packagedApk, candidate.packagedApk),
  };
}

function compareHeaders(current, candidate) {
  if (!candidate.present) {
    return {
      status: "blocked",
      reason: candidate.reason,
      currentNodeModuleVersion: current.nodeModuleVersion ?? null,
      candidateNodeModuleVersion: null,
    };
  }
  return {
    status: current.nodeModuleVersion === candidate.nodeModuleVersion ? "same-node-module-version" : "needs-review",
    currentVersion: current.nodeVersion,
    candidateVersion: candidate.nodeVersion,
    currentNodeModuleVersion: current.nodeModuleVersion ?? null,
    candidateNodeModuleVersion: candidate.nodeModuleVersion ?? null,
    currentNapiVersion: current.napiVersion ?? null,
    candidateNapiVersion: candidate.napiVersion ?? null,
  };
}

function compareLibraries(current, candidate) {
  if (!current.present) {
    return {
      status: "blocked",
      reason: "current baseline libnode.so is missing",
    };
  }
  if (!candidate.present) {
    return {
      status: "blocked",
      reason: candidate.reason,
      currentSizeBytes: current.sizeBytes,
      candidateSizeBytes: null,
    };
  }
  const currentDefined = current._symbolSets?.defined || new Set();
  const candidateDefined = candidate._symbolSets?.defined || new Set();
  const currentUndefined = current._symbolSets?.undefined || new Set();
  const candidateUndefined = candidate._symbolSets?.undefined || new Set();
  const removedExports = diffSet(currentDefined, candidateDefined);
  const addedExports = diffSet(candidateDefined, currentDefined);
  const removedRequired = diffSet(currentUndefined, candidateUndefined);
  const addedRequired = diffSet(candidateUndefined, currentUndefined);
  const addedNeeded = diffList(candidate.needed || [], current.needed || []);
  const removedNeeded = diffList(current.needed || [], candidate.needed || []);
  const sizeDeltaBytes = candidate.sizeBytes - current.sizeBytes;
  const status = removedExports.length > 0 || addedNeeded.length > 0 || removedNeeded.length > 0
    ? "needs-review"
    : "compared";
  return {
    status,
    currentSizeBytes: current.sizeBytes,
    candidateSizeBytes: candidate.sizeBytes,
    sizeDeltaBytes,
    sizeDeltaPercent: current.sizeBytes > 0 ? Number(((sizeDeltaBytes / current.sizeBytes) * 100).toFixed(2)) : null,
    currentSha256: current.sha256,
    candidateSha256: candidate.sha256,
    currentBuildId: current.buildId,
    candidateBuildId: candidate.buildId,
    addedDependencies: addedNeeded,
    removedDependencies: removedNeeded,
    addedExportCount: addedExports.length,
    removedExportCount: removedExports.length,
    addedRequiredSymbolCount: addedRequired.length,
    removedRequiredSymbolCount: removedRequired.length,
    addedExportSample: addedExports.slice(0, SYMBOL_SAMPLE_LIMIT),
    removedExportSample: removedExports.slice(0, SYMBOL_SAMPLE_LIMIT),
    addedRequiredSymbolSample: addedRequired.slice(0, SYMBOL_SAMPLE_LIMIT),
    removedRequiredSymbolSample: removedRequired.slice(0, SYMBOL_SAMPLE_LIMIT),
  };
}

function compareProcessVersions(current, candidate) {
  if (!candidate.compatibilityProfile.present && !candidate.performance.present) {
    return {
      status: "blocked",
      currentExpectedPrefix: current.expectedProcessVersionPrefix,
      candidateExpectedPrefix: `v${candidate.version}`,
      reason: "candidate process.versions evidence was not supplied",
    };
  }
  return {
    status: "compared",
    currentExpectedPrefix: current.expectedProcessVersionPrefix,
    candidateExpectedPrefix: candidate.compatibilityProfile.runtime?.nodeVersionExpectedPrefix || `v${candidate.version}`,
    currentObservedVersion: current.performance.processVersion || null,
    candidateObservedVersion: candidate.performance.processVersion || null,
  };
}

function comparePerformance(current, candidate) {
  if (!candidate.present) {
    return {
      status: "blocked",
      reason: candidate.reason,
      currentMetrics: current.metrics || null,
    };
  }
  const metrics = {};
  for (const key of Object.keys(current.metrics || {})) {
    const currentValue = current.metrics[key];
    const candidateValue = candidate.metrics?.[key] ?? null;
    metrics[key] = {
      current: currentValue,
      candidate: candidateValue,
      delta: currentValue !== null && candidateValue !== null ? candidateValue - currentValue : null,
    };
  }
  return {
    status: "compared",
    currentDevice: current.device || null,
    candidateDevice: candidate.device || null,
    metrics,
  };
}

function compareDifferential(current, candidate) {
  if (!candidate.present) {
    return {
      status: "blocked",
      reason: candidate.reason,
      currentSummary: current.summary || null,
    };
  }
  return {
    status: "compared",
    currentSummary: current.summary || null,
    candidateSummary: candidate.summary || null,
  };
}

function comparePackaged(current, candidate) {
  if (!candidate.present) {
    return {
      status: "blocked",
      reason: candidate.reason,
      current: current,
    };
  }
  return {
    status: candidate.markerFound ? "compared" : "needs-review",
    current,
    candidate,
  };
}

function compareEvidence(name, ready, blockedReason) {
  return ready
    ? { status: "available", name }
    : { status: "blocked", name, reason: blockedReason };
}

function allPresent(libraries) {
  return REQUIRED_ABIS.every((abi) => libraries[abi]?.present);
}

function buildDecision(targetVersion, runtime, comparison) {
  const blockers = [];
  collectBlockers("headers", comparison.headers, blockers);
  for (const [abi, item] of Object.entries(comparison.libraries)) {
    collectBlockers(`libraries.${abi}`, item, blockers);
  }
  collectBlockers("processVersions", comparison.processVersions, blockers);
  collectBlockers("startupAndRss", comparison.startupAndRss, blockers);
  collectBlockers("cjsEsmBehavior", comparison.cjsEsmBehavior, blockers);
  collectBlockers("packagedApkBehavior", comparison.packagedApkBehavior, blockers);
  const reviewItems = [];
  collectReviewItems("headers", comparison.headers, reviewItems);
  for (const [abi, item] of Object.entries(comparison.libraries)) {
    collectReviewItems(`libraries.${abi}`, item, reviewItems);
  }
  collectReviewItems("packagedApkBehavior", comparison.packagedApkBehavior, reviewItems);
  const goNoGo = blockers.length === 0 && reviewItems.length === 0 ? "go-for-ab-gate" : "no-go";
  return {
    goNoGo,
    readyForDefaultRuntimeSwitch: false,
    defaultRuntimeVersion: runtime.nodeHeaderVersion,
    candidateVersion: targetVersion,
    summary: goNoGo === "go-for-ab-gate"
      ? `Candidate v${targetVersion} has enough static evidence for the next A/B gate. Default runtime remains v${runtime.nodeHeaderVersion}.`
      : `NO-GO for switching to v${targetVersion}. Default runtime remains v${runtime.nodeHeaderVersion}.`,
    blockers,
    reviewItems,
    requiredNextSteps: [
      `Build Node v${targetVersion} from the same Android fork/toolchain.`,
      "Provide candidate headers and jniLibs for arm64-v8a, armeabi-v7a, and x86_64.",
      "Run this report with candidate artifact and evidence properties.",
      "Run the focused modern core, packaged APK, performance, and compatibility gates against the candidate.",
      "Only consider default runtime changes after a separate dual-runtime A/B gate passes.",
    ],
  };
}

function collectBlockers(name, item, blockers) {
  if (item && item.status === "blocked") {
    blockers.push({
      area: name,
      reason: item.reason || "blocked",
    });
  }
}

function collectReviewItems(name, item, reviewItems) {
  if (item && item.status === "needs-review") {
    reviewItems.push({
      area: name,
      reason: item.reason || "static comparison needs review",
    });
  }
}

function summarizeTools(tools) {
  if (!tools) {
    return {
      ndkVersion: NDK_VERSION,
      available: false,
      reason: "Android NDK llvm-readelf/llvm-nm were not found",
    };
  }
  return {
    ndkVersion: tools.ndkVersion,
    available: true,
    ndkDir: tools.ndkDir,
    readelf: tools.readelf,
    nm: tools.nm,
  };
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Node 24.x Patch Upgrade Feasibility");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push(`- Result: **${report.decision.goNoGo}**`);
  lines.push(`- Summary: ${report.decision.summary}`);
  lines.push(`- Default runtime switch ready: ${report.decision.readyForDefaultRuntimeSwitch}`);
  lines.push(`- Current runtime: v${report.target.currentVersion}`);
  lines.push(`- Candidate runtime: v${report.target.candidateVersion}`);
  lines.push(`- Candidate source: ${report.target.candidateReleaseUrl}`);
  lines.push("");
  if (report.decision.blockers.length > 0) {
    lines.push("## Blockers");
    lines.push("");
    for (const blocker of report.decision.blockers) {
      lines.push(`- ${blocker.area}: ${blocker.reason}`);
    }
    lines.push("");
  }
  if (report.decision.reviewItems.length > 0) {
    lines.push("## Review Items");
    lines.push("");
    for (const item of report.decision.reviewItems) {
      lines.push(`- ${item.area}: ${item.reason}`);
    }
    lines.push("");
  }
  lines.push("## Current Baseline");
  lines.push("");
  lines.push(`- Runtime constants: ${NATIVE_NODE_CAPABILITIES}`);
  lines.push(`- Expected process version prefix: ${report.current.expectedProcessVersionPrefix}`);
  lines.push(`- Header Node module version: ${valueOrDash(report.current.headers.nodeModuleVersion)}`);
  lines.push(`- Compatibility profile: ${report.current.compatibilityProfile.path || report.current.compatibilityProfile.reason}`);
  lines.push(`- Performance report: ${report.current.performance.path || report.current.performance.reason}`);
  lines.push("");
  lines.push("| ABI | Size | SHA-256 | Build ID | DT_NEEDED | Defined | Undefined |");
  lines.push("| --- | ---: | --- | --- | --- | ---: | ---: |");
  for (const abi of REQUIRED_ABIS) {
    const lib = report.current.libraries[abi];
    lines.push(renderLibraryRow(abi, lib));
  }
  lines.push("");
  lines.push("## Candidate Artifacts");
  lines.push("");
  lines.push(`- Headers: ${report.inputs.candidateHeaders || "not supplied"}`);
  lines.push(`- jniLibs: ${report.inputs.candidateJniLibs || "not supplied"}`);
  lines.push("");
  lines.push("| ABI | Status | Size | SHA-256 | DT_NEEDED | Defined | Undefined |");
  lines.push("| --- | --- | ---: | --- | --- | ---: | ---: |");
  for (const abi of REQUIRED_ABIS) {
    const lib = report.candidate.libraries[abi];
    lines.push(renderCandidateLibraryRow(abi, lib));
  }
  lines.push("");
  lines.push("## Comparison Matrix");
  lines.push("");
  lines.push("| Area | Status | Notes |");
  lines.push("| --- | --- | --- |");
  lines.push(renderStatusRow("ABI / headers", report.comparison.headers));
  for (const abi of REQUIRED_ABIS) {
    lines.push(renderStatusRow(`libnode.so ${abi}`, report.comparison.libraries[abi]));
  }
  lines.push(renderStatusRow("process.versions", report.comparison.processVersions));
  lines.push(renderStatusRow("startup / RSS", report.comparison.startupAndRss));
  lines.push(renderStatusRow("CJS / ESM behavior", report.comparison.cjsEsmBehavior));
  lines.push(renderStatusRow("packaged APK behavior", report.comparison.packagedApkBehavior));
  lines.push("");
  lines.push("## Required Next Steps");
  lines.push("");
  for (const step of report.decision.requiredNextSteps) {
    lines.push(`- ${step}`);
  }
  lines.push("");
  lines.push("## Re-run");
  lines.push("");
  lines.push("```powershell");
  lines.push(".\\gradlew.bat --console=plain :app:generateNode24PatchUpgradeFeasibilityReport");
  lines.push(".\\gradlew.bat --console=plain :app:generateNode24PatchUpgradeFeasibilityReport \"-Pautojs.nodejs.patchTargetVersion=24.17.0\" \"-Pautojs.nodejs.patchCandidateJniLibs=D:\\\\path\\\\to\\\\jniLibs\" \"-Pautojs.nodejs.patchCandidateHeaders=D:\\\\path\\\\to\\\\include\\\\node\"");
  lines.push("```");
  lines.push("");
  lines.push("This report is informational by default. It records a no-go decision without failing the Gradle task unless the script is run with `--fail-on-no-go`.");
  lines.push("");
  return lines.join("\n");
}

function renderLibraryRow(abi, lib) {
  if (!lib.present) {
    return `| ${abi} | missing | - | - | - | - | - |`;
  }
  return [
    `| ${abi}`,
    formatBytes(lib.sizeBytes),
    shortHash(lib.sha256),
    valueOrDash(lib.buildId),
    (lib.needed || []).join(", ") || "-",
    valueOrDash(lib.symbols?.definedCount),
    valueOrDash(lib.symbols?.undefinedCount),
  ].join(" | ") + " |";
}

function renderCandidateLibraryRow(abi, lib) {
  if (!lib.present) {
    return `| ${abi} | missing | - | - | - | - | - |`;
  }
  return [
    `| ${abi}`,
    "present",
    formatBytes(lib.sizeBytes),
    shortHash(lib.sha256),
    (lib.needed || []).join(", ") || "-",
    valueOrDash(lib.symbols?.definedCount),
    valueOrDash(lib.symbols?.undefinedCount),
  ].join(" | ") + " |";
}

function renderStatusRow(name, item) {
  return `| ${name} | ${item.status} | ${item.reason || summarizeStatusNotes(item)} |`;
}

function summarizeStatusNotes(item) {
  if (item.sizeDeltaBytes !== undefined) {
    return `size delta ${formatBytes(item.sizeDeltaBytes)}; removed exports ${item.removedExportCount}`;
  }
  if (item.currentExpectedPrefix) {
    return `${item.currentExpectedPrefix} -> ${item.candidateExpectedPrefix}`;
  }
  return "-";
}

function valueOrDash(value) {
  return value === null || value === undefined || value === "" ? "-" : String(value);
}

function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) {
    return "-";
  }
  return Number(bytes).toLocaleString("en-US");
}

function shortHash(hash) {
  return hash ? hash.slice(0, 16) : "-";
}

function diffSet(left, right) {
  return Array.from(left)
    .filter((value) => !right.has(value))
    .sort();
}

function diffList(left, right) {
  const rightSet = new Set(right);
  return left.filter((value) => !rightSet.has(value)).sort();
}

function findNestedValue(value, key) {
  if (!value || typeof value !== "object") {
    return null;
  }
  if (Object.prototype.hasOwnProperty.call(value, key) && value[key]) {
    return value[key];
  }
  for (const child of Object.values(value)) {
    const found = findNestedValue(child, key);
    if (found) {
      return found;
    }
  }
  return null;
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function readText(file) {
  return fs.readFileSync(file, "utf8");
}

function readJson(file) {
  return JSON.parse(readText(file));
}

function relativePath(repoRoot, file) {
  if (!file) {
    return null;
  }
  return path.relative(repoRoot, file).replace(/\\/g, "/") || ".";
}

function isFile(file) {
  try {
    return fs.statSync(file).isFile();
  } catch (_error) {
    return false;
  }
}

function isDirectory(file) {
  try {
    return fs.statSync(file).isDirectory();
  } catch (_error) {
    return false;
  }
}

main();
