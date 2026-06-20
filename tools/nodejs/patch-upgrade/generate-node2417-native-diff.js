#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-2417-native-diff-v1";
const DEFAULT_TARGET_VERSION = "24.17.0";
const NDK_VERSION = "26.1.10909125";
const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];
const ALLOWED_LIBNODE_NEEDED = new Set(["libc++_shared.so", "libc.so", "libdl.so", "liblog.so", "libm.so"]);
const REQUIRED_EXPORTS = [
  "node_module_register",
  "_ZN4node5StartEiPPc",
];
const SYMBOL_SAMPLE_LIMIT = 20;
const PAGE_ALIGNMENT_BYTES = 0x4000;

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const targetVersion = normalizeVersion(options.targetVersion || DEFAULT_TARGET_VERSION);
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const currentJniLibs = path.resolve(options.currentJniLibs || path.join(repoRoot, "app/src/main/jniLibs"));
  const candidateJniLibs = options.candidateJniLibs ? path.resolve(options.candidateJniLibs) : null;
  const candidateHeaders = options.candidateHeaders ? normalizeHeaderDir(path.resolve(options.candidateHeaders)) : null;
  const tools = findAndroidTools(repoRoot);

  const currentLibraries = inspectLibraries(currentJniLibs, tools);
  const candidateLibraries = candidateJniLibs
    ? inspectLibraries(candidateJniLibs, tools)
    : missingLibraries("candidate jniLibs were not supplied");
  const bridgeLibraries = inspectBridgeLibraries(repoRoot, tools);
  const headerEvidence = candidateHeaders ? inspectHeaders(candidateHeaders) : missingEvidence("candidate headers were not supplied");
  const comparisons = buildComparisons(currentLibraries, candidateLibraries, bridgeLibraries, tools);
  const decision = buildDecision(comparisons, headerEvidence, tools);
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    target: {
      currentVersion: "24.5.0",
      candidateVersion: targetVersion,
      defaultRuntimeUnchanged: true,
      selectorProperty: "autojs.nodejs.runtimeSlot",
      candidateSlot: "node24_current",
    },
    inputs: {
      currentJniLibs: relativePath(repoRoot, currentJniLibs),
      candidateJniLibs: candidateJniLibs ? relativePath(repoRoot, candidateJniLibs) : null,
      candidateHeaders: candidateHeaders ? relativePath(repoRoot, candidateHeaders) : null,
      candidateSourceCommit: options.candidateSourceCommit || null,
      candidateBuildNotes: options.candidateBuildNotes || null,
    },
    buildIntake: buildIntake(targetVersion, candidateLibraries, headerEvidence, options, tools),
    tools: summarizeTools(tools),
    current: publicLibraryMap(currentLibraries),
    candidate: publicLibraryMap(candidateLibraries),
    bridge: publicLibraryMap(bridgeLibraries),
    candidateHeaders: headerEvidence,
    comparisons,
    decision,
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "node-24.17-native-diff.json");
  const markdownPath = path.join(reportDir, "node-24.17-native-diff.md");
  const markdown = renderMarkdown(report);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, markdown);
  if (options.docReport) {
    const docPath = path.resolve(options.docReport);
    fs.mkdirSync(path.dirname(docPath), { recursive: true });
    fs.writeFileSync(docPath, markdown);
  }

  console.log("Node 24.17 native diff JSON: " + jsonPath);
  console.log("Node 24.17 native diff Markdown: " + markdownPath);
  console.log("Node 24.17 native diff decision: " + decision.status);
  if (options.failOnBlocked && decision.status !== "ready") {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
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

function printHelp() {
  console.log([
    "Usage: node tools/nodejs/patch-upgrade/generate-node2417-native-diff.js [options]",
    "",
    "Options:",
    "  --target-version <version>          Candidate Node version. Defaults to 24.17.0.",
    "  --current-jni-libs <dir>            Current jniLibs root. Defaults to app/src/main/jniLibs.",
    "  --candidate-jni-libs <dir>          Candidate jniLibs root containing <abi>/libnode.so.",
    "  --candidate-headers <dir>           Candidate include/node directory or Node source root.",
    "  --candidate-source-commit <sha>     Candidate source commit or fork revision.",
    "  --candidate-build-notes <text>      Build/toolchain notes to record in the intake report.",
    "  --report-dir <dir>                  Output directory. Defaults to build/reports/nodejs.",
    "  --doc-report <file>                 Also write Markdown to a tracked docs path.",
    "  --fail-on-blocked                   Exit non-zero unless all native checks are ready.",
  ].join("\n"));
}

function buildIntake(targetVersion, candidateLibraries, headerEvidence, options, tools) {
  return {
    targetVersion: `v${targetVersion}`,
    androidAbis: REQUIRED_ABIS,
    ndkVersion: NDK_VERSION,
    compiler: tools?.readelf ? path.basename(path.dirname(tools.readelf)) : null,
    cmakeFlags: [
      "-DAUTOJS6_NODE_RUNTIME_SLOT=node24_current",
      "-DAUTOJS6_NODE_INCLUDE_DIR=<candidate include/node>",
      "-DAUTOJS6_NODE_LIBRARY_ROOT=<candidate jniLibs>",
      "-DAUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=ON",
    ],
    sourceCommit: options.candidateSourceCommit || null,
    buildNotes: options.candidateBuildNotes || null,
    headers: headerEvidence,
    libnode: Object.fromEntries(REQUIRED_ABIS.map((abi) => {
      const lib = candidateLibraries[abi] || missingEvidence("candidate libnode missing");
      return [abi, {
        present: lib.present === true,
        sha256: lib.sha256 || null,
        buildId: lib.buildId || null,
        sizeBytes: lib.sizeBytes || null,
        needed: lib.needed || [],
        processVersionEvidence: null,
      }];
    })),
  };
}

function inspectLibraries(root, tools) {
  const result = {};
  for (const abi of REQUIRED_ABIS) {
    result[abi] = inspectLibrary(path.join(root, abi, "libnode.so"), tools);
  }
  return result;
}

function missingLibraries(reason) {
  return Object.fromEntries(REQUIRED_ABIS.map((abi) => [abi, missingEvidence(reason)]));
}

function inspectBridgeLibraries(repoRoot, tools) {
  const result = {};
  for (const abi of REQUIRED_ABIS) {
    const candidates = [
      path.join(repoRoot, "app/build/intermediates/cmake/appDebug/obj", abi, "libautojs6-node.so"),
      ...findFiles(path.join(repoRoot, "app/build/intermediates/cxx"), abi, "libautojs6-node.so"),
    ];
    const file = candidates.find(isFile);
    result[abi] = file ? inspectLibrary(file, tools, "libautojs6-node.so") : missingEvidence("libautojs6-node.so has not been built for " + abi);
  }
  return result;
}

function findFiles(root, abi, name) {
  if (!isDirectory(root)) {
    return [];
  }
  const found = [];
  const stack = [root];
  while (stack.length > 0) {
    const dir = stack.pop();
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (entry.isFile() && entry.name === name && full.split(path.sep).includes(abi)) {
        found.push(full);
      }
    }
  }
  return found.sort();
}

function inspectLibrary(file, tools, expectedName = "libnode.so") {
  if (!isFile(file)) {
    return missingEvidence(`${expectedName} is missing: ${file}`);
  }
  const stat = fs.statSync(file);
  const info = {
    present: true,
    path: file,
    sizeBytes: stat.size,
    sha256: sha256(file),
    elfHeader: null,
    soname: null,
    needed: [],
    buildId: null,
    loadAlignment: [],
    pageAligned: null,
    symbols: {
      definedCount: null,
      undefinedCount: null,
      definedSample: [],
      undefinedSample: [],
    },
    toolStatus: tools ? "available" : "missing-ndk-tools",
    _symbols: {
      defined: new Set(),
      undefined: new Set(),
    },
  };
  if (!tools) {
    return info;
  }
  info.elfHeader = parseElfHeader(runTool(tools.readelf, ["-h", "--wide", file]).lines);
  const dynamic = parseDynamic(runTool(tools.readelf, ["-d", "--wide", file]).lines);
  info.soname = dynamic.soname;
  info.needed = dynamic.needed;
  info.buildId = parseBuildId(runTool(tools.readelf, ["-n", "--wide", file]).lines);
  info.loadAlignment = parseLoadAlignment(runTool(tools.readelf, ["-l", "--wide", file]).lines);
  info.pageAligned = info.loadAlignment.length > 0 && info.loadAlignment.every((value) => value >= PAGE_ALIGNMENT_BYTES && value % PAGE_ALIGNMENT_BYTES === 0);
  const defined = parseSymbols(runTool(tools.nm, ["-D", "--defined-only", file]).lines);
  const undefinedSymbols = parseSymbols(runTool(tools.nm, ["-D", "--undefined-only", file]).lines);
  info.symbols = {
    definedCount: defined.size,
    undefinedCount: undefinedSymbols.size,
    definedSample: Array.from(defined).slice(0, SYMBOL_SAMPLE_LIMIT),
    undefinedSample: Array.from(undefinedSymbols).slice(0, SYMBOL_SAMPLE_LIMIT),
  };
  info._symbols = {
    defined,
    undefined: undefinedSymbols,
  };
  return info;
}

function inspectHeaders(headerDir) {
  const versionHeader = path.join(headerDir, "node_version.h");
  if (!isFile(versionHeader)) {
    return missingEvidence("candidate node_version.h is missing: " + headerDir);
  }
  const text = readText(versionHeader);
  return {
    present: true,
    path: headerDir,
    version: [
      findDefineInt(text, "NODE_MAJOR_VERSION"),
      findDefineInt(text, "NODE_MINOR_VERSION"),
      findDefineInt(text, "NODE_PATCH_VERSION"),
    ].join("."),
    nodeModuleVersion: findDefineInt(text, "NODE_MODULE_VERSION"),
    napiVersion: findDefineInt(text, "NODE_API_SUPPORTED_VERSION_MAX"),
  };
}

function buildComparisons(currentLibraries, candidateLibraries, bridgeLibraries, tools) {
  const result = {};
  for (const abi of REQUIRED_ABIS) {
    result[abi] = compareAbi(abi, currentLibraries[abi], candidateLibraries[abi], bridgeLibraries[abi], tools);
  }
  return result;
}

function compareAbi(abi, current, candidate, bridge, tools) {
  const blockers = [];
  const reviewItems = [];
  if (!tools) {
    blockers.push("Android NDK llvm-readelf/llvm-nm were not found");
  }
  if (!current.present) {
    blockers.push("current libnode.so is missing");
  }
  if (!candidate.present) {
    blockers.push(candidate.reason || "candidate libnode.so is missing");
  }
  if (!bridge.present) {
    reviewItems.push(bridge.reason || "libautojs6-node.so bridge artifact is missing");
  }
  if (blockers.length > 0) {
    return { status: "blocked", blockers, reviewItems };
  }

  const currentDefined = current._symbols?.defined || new Set();
  const candidateDefined = candidate._symbols?.defined || new Set();
  const removedExports = Array.from(currentDefined).filter((symbol) => !candidateDefined.has(symbol)).sort();
  const addedExports = Array.from(candidateDefined).filter((symbol) => !currentDefined.has(symbol)).sort();
  const missingRequiredExports = REQUIRED_EXPORTS.filter((symbol) => !candidateDefined.has(symbol));
  const unexpectedNeeded = (candidate.needed || []).filter((name) => !ALLOWED_LIBNODE_NEEDED.has(name)).sort();
  const removedNeeded = (current.needed || []).filter((name) => !(candidate.needed || []).includes(name)).sort();
  const addedNeeded = (candidate.needed || []).filter((name) => !(current.needed || []).includes(name)).sort();
  const bridgeLinksLibnode = bridge.present && (bridge.needed || []).includes("libnode.so");
  const machineChanged = current.elfHeader?.machine && candidate.elfHeader?.machine && current.elfHeader.machine !== candidate.elfHeader.machine;
  const classChanged = current.elfHeader?.class && candidate.elfHeader?.class && current.elfHeader.class !== candidate.elfHeader.class;
  const sonameWrong = candidate.soname && candidate.soname !== "libnode.so";
  const wrongAlignment = candidate.pageAligned !== true;

  if (missingRequiredExports.length > 0) {
    blockers.push("missing required export(s): " + missingRequiredExports.join(", "));
  }
  if (unexpectedNeeded.length > 0) {
    blockers.push("unexpected DT_NEEDED dependency: " + unexpectedNeeded.join(", "));
  }
  if (machineChanged) {
    blockers.push(`ELF machine changed: ${current.elfHeader.machine} -> ${candidate.elfHeader.machine}`);
  }
  if (classChanged) {
    blockers.push(`ELF class changed: ${current.elfHeader.class} -> ${candidate.elfHeader.class}`);
  }
  if (sonameWrong) {
    blockers.push("candidate SONAME is not libnode.so: " + candidate.soname);
  }
  if (wrongAlignment) {
    blockers.push("candidate LOAD segment alignment is not 16 KiB compatible");
  }
  if (bridgeLinksLibnode) {
    blockers.push("libautojs6-node.so gained an implicit DT_NEEDED dependency on libnode.so");
  }
  if (removedExports.length > 0) {
    reviewItems.push("candidate removed dynamic export(s): " + removedExports.slice(0, 8).join(", "));
  }
  if (removedNeeded.length > 0 || addedNeeded.length > 0) {
    reviewItems.push("DT_NEEDED set changed");
  }

  return {
    status: blockers.length === 0 ? (reviewItems.length === 0 ? "ready" : "needs-review") : "blocked",
    blockers,
    reviewItems,
    sizeDeltaBytes: candidate.sizeBytes - current.sizeBytes,
    sizeDeltaPercent: current.sizeBytes > 0 ? Number((((candidate.sizeBytes - current.sizeBytes) / current.sizeBytes) * 100).toFixed(2)) : null,
    currentSha256: current.sha256,
    candidateSha256: candidate.sha256,
    currentBuildId: current.buildId,
    candidateBuildId: candidate.buildId,
    currentNeeded: current.needed || [],
    candidateNeeded: candidate.needed || [],
    addedNeeded,
    removedNeeded,
    addedExportCount: addedExports.length,
    removedExportCount: removedExports.length,
    addedExportSample: addedExports.slice(0, SYMBOL_SAMPLE_LIMIT),
    removedExportSample: removedExports.slice(0, SYMBOL_SAMPLE_LIMIT),
    requiredExports: Object.fromEntries(REQUIRED_EXPORTS.map((symbol) => [symbol, candidateDefined.has(symbol)])),
    elf: {
      current: current.elfHeader,
      candidate: candidate.elfHeader,
      candidateSoname: candidate.soname,
      candidateLoadAlignment: candidate.loadAlignment,
      candidatePageAligned: candidate.pageAligned,
    },
    bridge: {
      present: bridge.present === true,
      path: bridge.path || null,
      needed: bridge.needed || [],
      linksLibnode: bridgeLinksLibnode,
      undefinedCount: bridge.symbols?.undefinedCount ?? null,
    },
  };
}

function buildDecision(comparisons, headerEvidence, tools) {
  const blockers = [];
  const reviewItems = [];
  if (!tools) {
    blockers.push({ area: "tools", reason: "Android NDK llvm-readelf/llvm-nm were not found" });
  }
  if (!headerEvidence.present) {
    blockers.push({ area: "candidate.headers", reason: headerEvidence.reason });
  }
  for (const [abi, comparison] of Object.entries(comparisons)) {
    for (const reason of comparison.blockers || []) {
      blockers.push({ area: `abi.${abi}`, reason });
    }
    for (const reason of comparison.reviewItems || []) {
      reviewItems.push({ area: `abi.${abi}`, reason });
    }
  }
  return {
    status: blockers.length === 0 && reviewItems.length === 0 ? "ready" : "blocked",
    defaultRuntimeSwitchReady: false,
    summary: blockers.length === 0 && reviewItems.length === 0
      ? "Native diff is ready for the dual-runtime A/B gate. Default runtime remains v24.5.0."
      : "Native diff is blocked; keep v24.5.0 as the default runtime.",
    blockers,
    reviewItems,
  };
}

function parseElfHeader(lines) {
  const values = {};
  for (const line of lines) {
    const match = line.match(/^\s*([^:]+):\s*(.+)$/);
    if (match) {
      values[match[1].trim().toLowerCase()] = match[2].trim();
    }
  }
  return {
    class: values.class || null,
    data: values.data || null,
    osAbi: values["os/abi"] || null,
    type: values.type || null,
    machine: values.machine || null,
    flags: values.flags || null,
  };
}

function parseDynamic(lines) {
  const needed = [];
  let soname = null;
  for (const line of lines) {
    const neededMatch = line.match(/Shared library: \[(.+)]/);
    if (neededMatch) {
      needed.push(neededMatch[1]);
    }
    const sonameMatch = line.match(/Library soname: \[(.+)]/);
    if (sonameMatch) {
      soname = sonameMatch[1];
    }
  }
  return { needed: needed.sort(), soname };
}

function parseBuildId(lines) {
  const match = lines.map((line) => line.match(/Build ID:\s*([0-9a-fA-F]+)/)).find(Boolean);
  return match ? match[1] : null;
}

function parseLoadAlignment(lines) {
  return lines
    .map((line) => line.trim())
    .filter((line) => line.startsWith("LOAD"))
    .map((line) => line.split(/\s+/).filter(Boolean).at(-1))
    .map((value) => Number.parseInt(value, 16))
    .filter((value) => Number.isFinite(value));
}

function parseSymbols(lines) {
  return new Set(lines
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.split(/\s+/).filter(Boolean).at(-1))
    .filter((symbol) => symbol && symbol !== "U" && symbol !== "w")
    .map((symbol) => symbol.split("@")[0])
    .sort());
}

function normalizeHeaderDir(dir) {
  return [
    dir,
    path.join(dir, "include/node"),
    path.join(dir, "include"),
    path.join(dir, "node"),
  ].find((candidate) => isFile(path.join(candidate, "node.h")) || isFile(path.join(candidate, "node_version.h"))) || dir;
}

function findAndroidTools(repoRoot) {
  const sdkDir = findSdkDir(repoRoot);
  if (!sdkDir) {
    return null;
  }
  const prebuiltRoot = path.join(sdkDir, "ndk", NDK_VERSION, "toolchains/llvm/prebuilt");
  if (!isDirectory(prebuiltRoot)) {
    return null;
  }
  const hostPrefix = process.platform === "win32" ? "windows" : process.platform === "darwin" ? "darwin" : "linux";
  const hostDir = fs.readdirSync(prebuiltRoot)
    .map((entry) => path.join(prebuiltRoot, entry))
    .find((entry) => isDirectory(entry) && path.basename(entry).startsWith(hostPrefix));
  if (!hostDir) {
    return null;
  }
  const exe = process.platform === "win32" ? ".exe" : "";
  const readelf = path.join(hostDir, "bin", `llvm-readelf${exe}`);
  const nm = path.join(hostDir, "bin", `llvm-nm${exe}`);
  return isFile(readelf) && isFile(nm) ? { ndkVersion: NDK_VERSION, readelf, nm } : null;
}

function findSdkDir(repoRoot) {
  const localProperties = path.join(repoRoot, "local.properties");
  if (isFile(localProperties)) {
    for (const line of readText(localProperties).split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed.startsWith("sdk.dir=")) {
        const value = unescapeJavaProperty(trimmed.slice("sdk.dir=".length));
        if (isDirectory(value)) {
          return value;
        }
      }
    }
  }
  return ["ANDROID_HOME", "ANDROID_SDK_ROOT"].map((name) => process.env[name]).find((value) => value && isDirectory(value)) || null;
}

function unescapeJavaProperty(value) {
  return value.replace(/\\:/g, ":").replace(/\\ /g, " ").replace(/\\\\/g, "\\");
}

function summarizeTools(tools) {
  return tools ? {
    available: true,
    ndkVersion: tools.ndkVersion,
    readelf: tools.readelf,
    nm: tools.nm,
  } : {
    available: false,
    ndkVersion: NDK_VERSION,
    reason: "Android NDK llvm-readelf/llvm-nm were not found",
  };
}

function publicLibraryMap(libraries) {
  return Object.fromEntries(Object.entries(libraries).map(([abi, info]) => {
    const copy = { ...info };
    delete copy._symbols;
    return [abi, copy];
  }));
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Node 24.17 Native Diff");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push(`- Status: **${report.decision.status}**`);
  lines.push(`- Summary: ${report.decision.summary}`);
  lines.push(`- Default runtime switch ready: ${report.decision.defaultRuntimeSwitchReady}`);
  lines.push(`- Candidate slot: \`${report.target.candidateSlot}\``);
  lines.push(`- Candidate jniLibs: ${report.inputs.candidateJniLibs || "not supplied"}`);
  lines.push(`- Candidate headers: ${report.inputs.candidateHeaders || "not supplied"}`);
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
  lines.push("## Build Intake");
  lines.push("");
  lines.push(`- Target: ${report.buildIntake.targetVersion}`);
  lines.push(`- NDK: ${report.buildIntake.ndkVersion}`);
  lines.push(`- Source commit: ${report.buildIntake.sourceCommit || "not supplied"}`);
  lines.push(`- Build notes: ${report.buildIntake.buildNotes || "not supplied"}`);
  lines.push("");
  lines.push("| ABI | Candidate | Size | SHA-256 | Build ID | DT_NEEDED | Page Align |");
  lines.push("| --- | --- | ---: | --- | --- | --- | --- |");
  for (const abi of REQUIRED_ABIS) {
    const lib = report.candidate[abi];
    lines.push(renderCandidateRow(abi, lib));
  }
  lines.push("");
  lines.push("## ABI Diff Matrix");
  lines.push("");
  lines.push("| ABI | Status | Size Delta | Removed Exports | Added DT_NEEDED | Removed DT_NEEDED | Bridge Links libnode |");
  lines.push("| --- | --- | ---: | ---: | --- | --- | --- |");
  for (const abi of REQUIRED_ABIS) {
    const comparison = report.comparisons[abi];
    lines.push([
      `| ${abi}`,
      comparison.status,
      formatBytes(comparison.sizeDeltaBytes),
      valueOrDash(comparison.removedExportCount),
      (comparison.addedNeeded || []).join(", ") || "-",
      (comparison.removedNeeded || []).join(", ") || "-",
      valueOrDash(comparison.bridge?.linksLibnode),
    ].join(" | ") + " |");
  }
  lines.push("");
  lines.push("## Re-run");
  lines.push("");
  lines.push("```powershell");
  lines.push(".\\gradlew.bat --console=plain :app:generateNode2417NativeDiffReport");
  lines.push(".\\gradlew.bat --console=plain :app:generateNode2417NativeDiffReport \"-Pautojs.nodejs.patchCandidateJniLibs=D:\\\\path\\\\to\\\\jniLibs\" \"-Pautojs.nodejs.patchCandidateHeaders=D:\\\\path\\\\to\\\\include\\\\node\"");
  lines.push("```");
  lines.push("");
  return lines.join("\n");
}

function renderCandidateRow(abi, lib) {
  if (!lib.present) {
    return `| ${abi} | missing | - | - | - | - | - |`;
  }
  return [
    `| ${abi}`,
    "present",
    formatBytes(lib.sizeBytes),
    shortHash(lib.sha256),
    valueOrDash(lib.buildId),
    (lib.needed || []).join(", ") || "-",
    valueOrDash(lib.pageAligned),
  ].join(" | ") + " |";
}

function runTool(command, args) {
  const result = childProcess.spawnSync(command, args, {
    encoding: "utf8",
    maxBuffer: 128 * 1024 * 1024,
  });
  return {
    status: result.status,
    lines: [result.stdout || "", result.stderr || ""].join("\n").split(/\r?\n/).filter(Boolean),
  };
}

function findDefineInt(text, name) {
  const match = text.match(new RegExp(`#define\\s+${name}\\s+(\\d+)`));
  return match ? Number(match[1]) : null;
}

function missingEvidence(reason) {
  return { present: false, reason };
}

function parseBoolean(value) {
  return ["1", "true", "yes"].includes(String(value).toLowerCase());
}

function normalizeVersion(value) {
  return String(value).replace(/^v/, "");
}

function shortHash(value) {
  return value ? value.slice(0, 16) : "-";
}

function valueOrDash(value) {
  return value === null || value === undefined || value === "" ? "-" : String(value);
}

function formatBytes(value) {
  return value === null || value === undefined ? "-" : Number(value).toLocaleString("en-US");
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function readText(file) {
  return fs.readFileSync(file, "utf8");
}

function relativePath(repoRoot, file) {
  return file ? path.relative(repoRoot, file).replace(/\\/g, "/") || "." : null;
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
