#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];
const LIBRARY_NAME = "libautojs6-node.so";
const EXPORT_SYMBOL = "autojs_node_get_runtime_api_v1";

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const cxxRoot = path.resolve(options.cxxRoot || path.join(repoRoot, "app/build/intermediates/cxx"));
  const tools = findAndroidTools(repoRoot);
  const artifacts = REQUIRED_ABIS.map((abi) => inspectAbi(repoRoot, cxxRoot, tools, abi));
  const issues = [];
  if (!tools) {
    issues.push("Android NDK llvm-nm/llvm-readelf tools were not found");
  }
  for (const artifact of artifacts) {
    if (!artifact.available) {
      issues.push(`missing native output for ${artifact.abi}`);
      continue;
    }
    if (!artifact.exportsAdapterApi) {
      issues.push(`missing ${EXPORT_SYMBOL} export for ${artifact.abi}`);
    }
    if (!artifact.sha256) {
      issues.push(`missing SHA-256 for ${artifact.abi}`);
    }
    if (!artifact.buildId) {
      issues.push(`missing Build ID for ${artifact.abi}`);
    }
  }
  const report = {
    schema: "autojs6-node-runtime-adapter-export-check-v1",
    generatedAt: new Date().toISOString(),
    task: "S9-03 Node 24.5 Adapter Surface Export",
    requiredAbis: REQUIRED_ABIS,
    libraryName: LIBRARY_NAME,
    exportSymbol: EXPORT_SYMBOL,
    cxxRoot: relativePath(repoRoot, cxxRoot),
    artifacts,
    decision: {
      status: issues.length === 0 ? "ready" : "blocked",
      blockers: issues,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "runtime-adapter-export.json");
  const markdownPath = path.join(reportDir, "runtime-adapter-export.md");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderMarkdown(report));
  console.log("Runtime adapter export JSON: " + jsonPath);
  console.log("Runtime adapter export Markdown: " + markdownPath);
  console.log("Runtime adapter export decision: " + report.decision.status);
  if (issues.length > 0) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/native/verify-runtime-adapter-export.js [--repo-root dir] [--cxx-root dir] [--report-dir dir]");
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

function inspectAbi(repoRoot, cxxRoot, tools, abi) {
  const file = latestLibrary(cxxRoot, abi);
  const artifact = {
    abi,
    path: file ? relativePath(repoRoot, file) : null,
    available: !!file,
    size: file ? fs.statSync(file).size : null,
    sha256: file ? sha256(file) : null,
    buildId: null,
    exportsAdapterApi: false,
  };
  if (!file || !tools) {
    return artifact;
  }
  const definedSymbols = runTool([tools.nm, "-D", "--defined-only", file])
    .lines
    .map(parseNmSymbol)
    .filter(Boolean)
    .map((symbol) => symbol.replace(/@.+$/, ""));
  artifact.exportsAdapterApi = definedSymbols.includes(EXPORT_SYMBOL);
  artifact.buildId = runTool([tools.readelf, "-n", "--wide", file])
    .lines
    .map((line) => {
      const match = line.match(/Build ID:\s*([0-9A-Fa-f]+)/);
      return match ? match[1] : null;
    })
    .find(Boolean) || null;
  return artifact;
}

function latestLibrary(cxxRoot, abi) {
  if (!fs.existsSync(cxxRoot)) {
    return null;
  }
  return walkFiles(cxxRoot)
    .filter((file) => path.basename(file) === LIBRARY_NAME && file.split(/[\\/]/).includes(abi))
    .map((file) => ({ file, mtime: fs.statSync(file).mtimeMs }))
    .sort((a, b) => b.mtime - a.mtime)
    .map((entry) => entry.file)[0] || null;
}

function walkFiles(root) {
  const result = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile()) {
        result.push(fullPath);
      }
    }
  }
  return result;
}

function sha256(file) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(file));
  return hash.digest("hex");
}

function parseNmSymbol(line) {
  const parts = line.trim().split(/\s+/).filter(Boolean);
  return parts.length > 0 ? parts[parts.length - 1] : null;
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
  return { readelf, nm };
}

function findAndroidSdk(repoRoot) {
  const localProperties = path.join(repoRoot, "local.properties");
  if (fs.existsSync(localProperties)) {
    const match = fs.readFileSync(localProperties, "utf8").match(/^sdk\.dir=(.+)$/m);
    if (match) {
      return decodeJavaPropertiesPath(match[1]);
    }
  }
  return process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || null;
}

function decodeJavaPropertiesPath(value) {
  return value.trim().replace(/\\:/g, ":").replace(/\\\\/g, "\\");
}

function runTool(command) {
  const result = childProcess.spawnSync(command[0], command.slice(1), {
    encoding: "utf8",
    windowsHide: true,
  });
  return {
    status: result.status,
    lines: (result.stdout || "").split(/\r?\n/).filter(Boolean),
    stderr: result.stderr || "",
  };
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# AutoJs6 Node Runtime Adapter Export Check");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push(`Decision: \`${report.decision.status}\``);
  lines.push("");
  lines.push("| ABI | Export | Build ID | SHA-256 | Path |");
  lines.push("| --- | --- | --- | --- | --- |");
  for (const artifact of report.artifacts) {
    lines.push(`| ${artifact.abi} | ${artifact.exportsAdapterApi} | ${artifact.buildId || "missing"} | ${artifact.sha256 || "missing"} | ${artifact.path || "missing"} |`);
  }
  if (report.decision.blockers.length > 0) {
    lines.push("");
    lines.push("## Blockers");
    lines.push("");
    report.decision.blockers.forEach((issue) => lines.push(`- ${issue}`));
  }
  lines.push("");
  return lines.join("\n");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

main();
