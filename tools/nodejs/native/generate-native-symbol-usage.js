#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-native-symbol-usage-v1";
const SOURCE_ROOT = "app/src/main/cpp";
const SOURCE_EXTENSIONS = new Set([".c", ".cc", ".cpp", ".cxx", ".h", ".hpp"]);
const IGNORED_SOURCE_DIRS = new Set(["node-v24.5.0"]);
const SOURCE_TOKEN_LIMIT = 400;
const BINARY_SYMBOL_LIMIT = 200;

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const sourceRoot = path.resolve(options.sourceRoot || path.join(repoRoot, SOURCE_ROOT));
  const tools = findAndroidTools(repoRoot);
  const sourceFiles = listSourceFiles(sourceRoot);
  const sourceReferences = scanSourceReferences(repoRoot, sourceFiles);
  const dlsymReferences = scanDlsymReferences(repoRoot, sourceFiles);
  const binaryInspection = inspectBridgeBinaries(repoRoot, tools);
  const classification = buildClassification(sourceReferences, dlsymReferences, binaryInspection);
  const decision = buildDecision(classification, binaryInspection);
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    task: "S9-01 Native Integration Boundary Map",
    inputs: {
      sourceRoot: relativePath(repoRoot, sourceRoot),
      sourceFiles: sourceFiles.map((file) => relativePath(repoRoot, file)),
    },
    tools: summarizeTools(tools),
    summary: {
      sourceFileCount: sourceFiles.length,
      sourceReferenceCount: sourceReferences.length,
      dlsymReferenceCount: dlsymReferences.length,
      binaryLibraryCount: binaryInspection.libraries.length,
      classifiedItemCount: classification.items.length,
      privateOrUnstableCount: classification.items.filter((item) => item.category === "private_or_unstable_node_v8_boundary").length,
    },
    sourceReferences,
    dlsymReferences,
    binaryInspection,
    classification,
    decision,
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "native-symbol-usage.json");
  const markdownPath = path.join(reportDir, "native-symbol-usage.md");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderMarkdown(report));
  console.log("Native symbol usage JSON: " + jsonPath);
  console.log("Native symbol usage Markdown: " + markdownPath);
  console.log("Native symbol usage decision: " + decision.status);
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
    "Usage: node tools/nodejs/native/generate-native-symbol-usage.js [options]",
    "",
    "Options:",
    "  --repo-root <dir>",
    "  --source-root <dir>",
    "  --report-dir <dir>",
    "  --fail-on-blocked",
  ].join("\n"));
}

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function listSourceFiles(root) {
  const result = [];
  function visit(dir) {
    if (!fs.existsSync(dir)) {
      return;
    }
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (!IGNORED_SOURCE_DIRS.has(entry.name)) {
          visit(fullPath);
        }
      } else if (entry.isFile() && SOURCE_EXTENSIONS.has(path.extname(entry.name))) {
        result.push(fullPath);
      }
    }
  }
  visit(root);
  return result.sort();
}

function scanSourceReferences(repoRoot, files) {
  const references = new Map();
  const patterns = [
    { family: "node_cpp", regex: /\bnode::[A-Za-z_][A-Za-z0-9_:]*/g },
    { family: "v8_cpp", regex: /\bv8::[A-Za-z_][A-Za-z0-9_:]*/g },
    { family: "libuv", regex: /\buv_[A-Za-z_][A-Za-z0-9_]*/g },
    { family: "libuv", regex: /\bUV_[A-Za-z_][A-Za-z0-9_]*/g },
    { family: "libuv", regex: /\buv_(?:loop|handle|run|walk|close|is|handle_type)[A-Za-z0-9_]*\b/g },
    { family: "jni", regex: /\b(?:JNIEXPORT|JNICALL|JNIEnv|jobject|jstring|jobjectArray|jclass|jmethodID)\b/g },
    { family: "dynamic_loader", regex: /\b(?:dlopen|dlsym|dlerror|dlclose|RTLD_[A-Z_]+)\b/g },
  ];
  for (const file of files) {
    const text = fs.readFileSync(file, "utf8");
    const rel = relativePath(repoRoot, file);
    for (const pattern of patterns) {
      for (const match of text.matchAll(pattern.regex)) {
        const token = match[0];
        const key = pattern.family + "\u0000" + token;
        const existing = references.get(key) || {
          token,
          family: pattern.family,
          category: classifySourceToken(token),
          evidence: [],
        };
        if (existing.evidence.length < SOURCE_TOKEN_LIMIT) {
          existing.evidence.push({
            file: rel,
            line: lineNumberAt(text, match.index),
          });
        }
        references.set(key, existing);
      }
    }
  }
  return Array.from(references.values()).sort(compareToken);
}

function scanDlsymReferences(repoRoot, files) {
  const references = new Map();
  const constPattern = /const\s+char\s*\*\s*const\s+(k[A-Za-z0-9_]*Symbol)\s*=\s*"([^"]+)"/g;
  const inlinePattern = /dlsym\s*\([^,]+,\s*"([^"]+)"\s*\)/g;
  for (const file of files) {
    const text = fs.readFileSync(file, "utf8");
    const rel = relativePath(repoRoot, file);
    for (const match of text.matchAll(constPattern)) {
      const token = match[2];
      const key = token;
      const existing = references.get(key) || {
        symbol: token,
        constant: match[1],
        category: classifySymbolName(token),
        evidence: [],
      };
      existing.evidence.push({ file: rel, line: lineNumberAt(text, match.index) });
      references.set(key, existing);
    }
    for (const match of text.matchAll(inlinePattern)) {
      const token = match[1];
      const key = token;
      const existing = references.get(key) || {
        symbol: token,
        constant: null,
        category: classifySymbolName(token),
        evidence: [],
      };
      existing.evidence.push({ file: rel, line: lineNumberAt(text, match.index) });
      references.set(key, existing);
    }
  }
  return Array.from(references.values()).sort((a, b) => a.symbol.localeCompare(b.symbol));
}

function inspectBridgeBinaries(repoRoot, tools) {
  const libraries = [];
  const roots = [
    path.join(repoRoot, "app/build/intermediates/cxx"),
    path.join(repoRoot, "app/build/intermediates/merged_native_libs"),
    path.join(repoRoot, "app/build/intermediates/stripped_native_libs"),
  ];
  if (!tools) {
    return {
      status: "skipped",
      detail: "Android NDK llvm-nm/llvm-readelf were not found.",
      libraries,
    };
  }
  const seen = new Set();
  for (const root of roots) {
    if (!fs.existsSync(root)) {
      continue;
    }
    for (const file of walkFiles(root)) {
      if (path.basename(file) !== "libautojs6-node.so") {
        continue;
      }
      const key = file;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      const undefinedSymbols = runTool([tools.nm, "-D", "--undefined-only", file])
        .lines
        .map(parseNmSymbol)
        .filter(Boolean)
        .slice(0, BINARY_SYMBOL_LIMIT);
      const needed = runTool([tools.readelf, "-d", "--wide", file])
        .lines
        .map((line) => {
          const match = line.match(/Shared library: \[(.+)]/);
          return match ? match[1] : null;
        })
        .filter(Boolean);
      libraries.push({
        path: relativePath(repoRoot, file),
        abi: abiFromPath(file),
        needed,
        undefinedSymbols: undefinedSymbols.map((symbol) => ({
          symbol,
          category: classifySymbolName(symbol),
        })),
      });
    }
  }
  return {
    status: libraries.length > 0 ? "inspected" : "missing",
    detail: libraries.length > 0 ? null : "No built libautojs6-node.so outputs were found. Run a native build first for binary evidence.",
    libraries,
  };
}

function buildClassification(sourceReferences, dlsymReferences, binaryInspection) {
  const items = [];
  for (const reference of sourceReferences) {
    items.push({
      name: reference.token,
      source: "source",
      family: reference.family,
      category: reference.category,
      evidence: reference.evidence.slice(0, 8),
    });
  }
  for (const reference of dlsymReferences) {
    items.push({
      name: reference.symbol,
      source: "dlsym",
      family: "dynamic_symbol",
      category: reference.category,
      evidence: reference.evidence.slice(0, 8),
    });
  }
  for (const library of binaryInspection.libraries) {
    for (const symbol of library.undefinedSymbols) {
      items.push({
        name: symbol.symbol,
        source: "binary_undefined",
        family: "elf",
        category: symbol.category,
        evidence: [{ file: library.path, line: null }],
      });
    }
  }
  const unique = new Map();
  for (const item of items) {
    const key = item.source + "\u0000" + item.name;
    if (!unique.has(key)) {
      unique.set(key, item);
    }
  }
  const uniqueItems = Array.from(unique.values()).sort((a, b) => {
    const sourceCompare = a.source.localeCompare(b.source);
    return sourceCompare || a.name.localeCompare(b.name);
  });
  const byCategory = {};
  for (const item of uniqueItems) {
    byCategory[item.category] = (byCategory[item.category] || 0) + 1;
  }
  return {
    byCategory,
    items: uniqueItems,
  };
}

function buildDecision(classification, binaryInspection) {
  const blockers = [];
  if (binaryInspection.status !== "inspected") {
    blockers.push("binary inspection is unavailable until libautojs6-node.so has been built");
  }
  const privateCount = classification.items.filter((item) => item.category === "private_or_unstable_node_v8_boundary").length;
  if (privateCount > 0) {
    blockers.push(`${privateCount} private/unstable Node/V8 boundary reference(s) must be hidden behind AutoJsNodeRuntimeApiV1`);
  }
  return {
    status: blockers.length === 0 ? "ready" : "blocked",
    blockers,
  };
}

function classifySourceToken(token) {
  if (token.startsWith("node::")) {
    if (/Environment|IsolateData|IsolateSettings|EmbedderSnapshotData|InspectorParentHandle|ThreadId/.test(token)) {
      return "private_or_unstable_node_v8_boundary";
    }
    return "node_public_embedder_api";
  }
  if (token.startsWith("v8::")) {
    return "v8_public_api";
  }
  if (token.startsWith("uv_") || token.startsWith("UV_")) {
    return "libuv_public_api";
  }
  if (/^(JNIEXPORT|JNICALL|JNIEnv|jobject|jstring|jobjectArray|jclass|jmethodID)$/.test(token)) {
    return "jni_only_symbol";
  }
  if (/^(dlopen|dlsym|dlerror|dlclose|RTLD_)/.test(token)) {
    return "android_dynamic_loader";
  }
  return "unclassified";
}

function classifySymbolName(symbol) {
  if (!symbol) {
    return "unclassified";
  }
  if (symbol.startsWith("Java_") || symbol.includes("JNI")) {
    return "jni_only_symbol";
  }
  if (symbol.startsWith("uv_")) {
    return "libuv_public_api";
  }
  if (symbol.startsWith("_ZN4node") || symbol.includes("node")) {
    if (/Environment|IsolateData|IsolateSettings|EmbedderSnapshotData|InspectorParentHandle|ThreadId/.test(symbol)) {
      return "private_or_unstable_node_v8_boundary";
    }
    return "node_public_embedder_api";
  }
  if (symbol.startsWith("_ZN2v8") || symbol.includes("v8")) {
    return "v8_public_api";
  }
  if (symbol.startsWith("__android") || symbol.startsWith("android_") || symbol.startsWith("dl")) {
    return "android_platform_api";
  }
  if (symbol.includes("__ndk1") || symbol.startsWith("__cxa") || symbol.startsWith("_Z")) {
    return "cxx_runtime_symbol";
  }
  return "unclassified";
}

function findAndroidTools(repoRoot) {
  const sdkDir = findAndroidSdk(repoRoot);
  if (!sdkDir) {
    return null;
  }
  const ndkRoot = path.join(sdkDir, "ndk", "26.1.10909125");
  const prebuiltRoot = path.join(ndkRoot, "toolchains/llvm/prebuilt");
  if (!fs.existsSync(prebuiltRoot)) {
    return null;
  }
  const hostPrefix = process.platform === "win32" ? "windows" : process.platform === "darwin" ? "darwin" : "linux";
  const hostDir = fs.readdirSync(prebuiltRoot, { withFileTypes: true })
    .find((entry) => entry.isDirectory() && entry.name.startsWith(hostPrefix));
  if (!hostDir) {
    return null;
  }
  const exe = process.platform === "win32" ? ".exe" : "";
  const binDir = path.join(prebuiltRoot, hostDir.name, "bin");
  const readelf = path.join(binDir, "llvm-readelf" + exe);
  const nm = path.join(binDir, "llvm-nm" + exe);
  if (!fs.existsSync(readelf) || !fs.existsSync(nm)) {
    return null;
  }
  return { ndkRoot, readelf, nm };
}

function findAndroidSdk(repoRoot) {
  const localProperties = path.join(repoRoot, "local.properties");
  if (fs.existsSync(localProperties)) {
    const match = fs.readFileSync(localProperties, "utf8").match(/^sdk\.dir=(.+)$/m);
    if (match) {
      return match[1].trim().replace(/\\:/g, ":");
    }
  }
  return process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || null;
}

function summarizeTools(tools) {
  if (!tools) {
    return { available: false };
  }
  return {
    available: true,
    ndkRoot: tools.ndkRoot,
    readelf: tools.readelf,
    nm: tools.nm,
  };
}

function runTool(command) {
  const result = childProcess.spawnSync(command[0], command.slice(1), {
    encoding: "utf8",
    windowsHide: true,
  });
  const text = (result.stdout || "") + (result.stderr || "");
  return {
    exitCode: result.status,
    lines: text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean),
  };
}

function parseNmSymbol(line) {
  const parts = line.trim().split(/\s+/);
  return parts[parts.length - 1] || null;
}

function walkFiles(root) {
  const result = [];
  function visit(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        visit(fullPath);
      } else if (entry.isFile()) {
        result.push(fullPath);
      }
    }
  }
  if (fs.existsSync(root)) {
    visit(root);
  }
  return result;
}

function abiFromPath(file) {
  const normalized = file.replace(/\\/g, "/");
  return ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"].find((abi) => normalized.includes("/" + abi + "/")) || "unknown";
}

function lineNumberAt(text, index) {
  return text.slice(0, index).split(/\r?\n/).length;
}

function compareToken(a, b) {
  const familyCompare = a.family.localeCompare(b.family);
  return familyCompare || a.token.localeCompare(b.token);
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Native Symbol Usage");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push("## Summary");
  lines.push("");
  lines.push(`- source files: ${report.summary.sourceFileCount}`);
  lines.push(`- source references: ${report.summary.sourceReferenceCount}`);
  lines.push(`- dlsym references: ${report.summary.dlsymReferenceCount}`);
  lines.push(`- binary libraries inspected: ${report.summary.binaryLibraryCount}`);
  lines.push(`- private or unstable references: ${report.summary.privateOrUnstableCount}`);
  lines.push(`- decision: ${report.decision.status}`);
  if (report.decision.blockers.length > 0) {
    lines.push("");
    lines.push("## Blockers");
    lines.push("");
    report.decision.blockers.forEach((blocker) => lines.push(`- ${blocker}`));
  }
  lines.push("");
  lines.push("## Category Counts");
  lines.push("");
  lines.push("| Category | Count |");
  lines.push("| --- | ---: |");
  Object.keys(report.classification.byCategory).sort().forEach((category) => {
    lines.push(`| \`${category}\` | ${report.classification.byCategory[category]} |`);
  });
  lines.push("");
  lines.push("## Private / Unstable Boundary References");
  lines.push("");
  const privateItems = report.classification.items.filter((item) => item.category === "private_or_unstable_node_v8_boundary");
  if (privateItems.length === 0) {
    lines.push("None.");
  } else {
    lines.push("| Reference | Source | Evidence |");
    lines.push("| --- | --- | --- |");
    privateItems.slice(0, 80).forEach((item) => {
      const evidence = item.evidence.map((entry) => `${entry.file}${entry.line ? ":" + entry.line : ""}`).join("<br>");
      lines.push(`| \`${escapePipe(item.name)}\` | ${item.source} | ${evidence} |`);
    });
  }
  lines.push("");
  lines.push("## Binary Inspection");
  lines.push("");
  lines.push(`Status: \`${report.binaryInspection.status}\``);
  if (report.binaryInspection.detail) {
    lines.push("");
    lines.push(report.binaryInspection.detail);
  }
  lines.push("");
  return lines.join("\n");
}

function escapePipe(value) {
  return String(value).replace(/\|/g, "\\|");
}

main();
