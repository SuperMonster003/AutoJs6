#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const childProcess = require("child_process");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const bundleDir = path.resolve(options.bundleDir || path.join(reportDir, "final-crash-evidence-bundle"));
  const manifestPath = path.join(bundleDir, "manifest.json");
  const manifest = fs.existsSync(manifestPath) ? readJson(manifestPath) : null;
  const inputs = resolveInputs(repoRoot, options, manifest);
  const ndk = resolveNdk(repoRoot, options);
  const tools = resolveTools(ndk);
  const rawText = inputs.map((input) => fs.readFileSync(input.path, "utf8")).join("\n");
  const frames = parseNativeFrames(rawText);
  const ndkStack = runNdkStack(tools.ndkStack, options.symbolDir, inputs);

  const status = inputs.length === 0
    ? "no_input"
    : ndkStack.ran && ndkStack.status === 0
      ? "symbolized_with_ndk_stack"
      : frames.length > 0
        ? "parsed_unresolved_frames"
        : "no_native_frames";

  const report = {
    schema: "autojs6-node-crash-symbolization-v1",
    generatedAt: new Date().toISOString(),
    gate: "symbolizeNodeCrash",
    bundle: {
      directory: relativePath(repoRoot, bundleDir),
      manifest: relativePath(repoRoot, manifestPath),
      available: manifest !== null,
    },
    inputs: inputs.map((input) => ({
      kind: input.kind,
      path: relativePath(repoRoot, input.path),
      sizeBytes: fs.statSync(input.path).size,
    })),
    tools,
    frames,
    ndkStack,
    decision: {
      status,
      nativeFramesFound: frames.length,
      safeNodeProfilePreserved: true,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(bundleDir, { recursive: true });
  writeReport(reportDir, "final-crash-symbolization", report, renderMarkdown);
  fs.writeFileSync(path.join(bundleDir, "symbolization.json"), JSON.stringify(report, null, 2) + "\n");
  if (ndkStack.output) {
    fs.writeFileSync(path.join(bundleDir, "symbolization.txt"), ndkStack.output);
  }
  console.log("Crash symbolization decision: " + report.decision.status);
}

function resolveInputs(repoRoot, options, manifest) {
  const explicit = [
    ["tombstone", options.tombstone],
    ["logcat", options.logcat],
    ["dump", options.dump],
  ]
    .filter(([, file]) => file)
    .map(([kind, file]) => ({ kind, path: path.resolve(file) }))
    .filter((input) => fs.existsSync(input.path) && fs.statSync(input.path).isFile());
  if (explicit.length > 0) return explicit;
  if (!manifest || !Array.isArray(manifest.attachments)) return [];
  return manifest.attachments
    .filter((attachment) => attachment.path && ["tombstone", "logcat"].includes(attachment.kind))
    .map((attachment) => ({ kind: attachment.kind, path: path.resolve(repoRoot, attachment.path) }))
    .filter((input) => fs.existsSync(input.path) && fs.statSync(input.path).isFile());
}

function parseNativeFrames(text) {
  if (!text) return [];
  const frames = [];
  const lines = text.split(/\r?\n/);
  const frameRegexes = [
    /^\s*#(\d+)\s+pc\s+([0-9a-fA-F]+)\s+(.+?)(?:\s+\((.*?)\))?\s*$/,
    /\bpc\s+([0-9a-fA-F]+)\s+(\S+\.so)(?:\s+\((.*?)\))?/,
  ];
  lines.forEach((line, index) => {
    let match = line.match(frameRegexes[0]);
    if (match) {
      frames.push({
        line: index + 1,
        index: Number(match[1]),
        pc: match[2],
        library: normalizeLibrary(match[3]),
        symbol: match[4] || "",
        raw: line.trim(),
      });
      return;
    }
    match = line.match(frameRegexes[1]);
    if (match) {
      frames.push({
        line: index + 1,
        index: frames.length,
        pc: match[1],
        library: normalizeLibrary(match[2]),
        symbol: match[3] || "",
        raw: line.trim(),
      });
    }
  });
  return frames.filter((frame) => frame.library.includes("libnode.so") || frame.library.includes("libautojs6-node.so"));
}

function normalizeLibrary(value) {
  const library = value.trim().split(/\s+/)[0];
  return library.replace(/\\/g, "/");
}

function runNdkStack(ndkStack, symbolDir, inputs) {
  if (!ndkStack || !symbolDir || inputs.length === 0 || !fs.existsSync(symbolDir)) {
    return {
      ran: false,
      status: null,
      reason: !ndkStack ? "ndk-stack not found" : !symbolDir ? "symbol directory not provided" : inputs.length === 0 ? "no input" : "symbol directory missing",
      output: "",
    };
  }
  const dump = inputs[0].path;
  const result = childProcess.spawnSync(ndkStack, ["-sym", path.resolve(symbolDir), "-dump", dump], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  return {
    ran: true,
    status: result.status,
    command: `${ndkStack} -sym ${path.resolve(symbolDir)} -dump ${dump}`,
    output: (result.stdout || "") + (result.stderr || ""),
  };
}

function resolveNdk(repoRoot, options) {
  const candidates = [
    options.ndk,
    process.env.ANDROID_NDK_HOME,
    process.env.ANDROID_NDK_ROOT,
    readLocalProperties(repoRoot)["ndk.dir"],
    latestAndroidHomeNdk(process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT),
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || "";
}

function resolveTools(ndk) {
  const ndkStackCandidates = ndk ? [
    path.join(ndk, "ndk-stack.cmd"),
    path.join(ndk, "ndk-stack"),
  ] : [];
  const llvmCandidates = ndk ? [
    path.join(ndk, "toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-symbolizer.exe"),
    path.join(ndk, "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-symbolizer"),
    path.join(ndk, "toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-symbolizer"),
  ] : [];
  return {
    ndk: ndk || "",
    ndkStack: ndkStackCandidates.find((candidate) => fs.existsSync(candidate)) || "",
    llvmSymbolizer: llvmCandidates.find((candidate) => fs.existsSync(candidate)) || "",
  };
}

function readLocalProperties(repoRoot) {
  const file = path.join(repoRoot, "local.properties");
  if (!fs.existsSync(file)) return {};
  const result = {};
  fs.readFileSync(file, "utf8").split(/\r?\n/).forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) return;
    const eq = trimmed.indexOf("=");
    if (eq < 0) return;
    result[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim().replace(/\\\\/g, "\\");
  });
  return result;
}

function latestAndroidHomeNdk(androidHome) {
  if (!androidHome) return "";
  const ndkRoot = path.join(androidHome, "ndk");
  if (!fs.existsSync(ndkRoot)) return "";
  const versions = fs.readdirSync(ndkRoot)
    .map((name) => path.join(ndkRoot, name))
    .filter((file) => fs.statSync(file).isDirectory())
    .sort();
  return versions[versions.length - 1] || "";
}

function renderMarkdown(report) {
  const lines = [
    "# Final Crash Symbolization",
    "",
    `- Decision: ${report.decision.status}`,
    `- Inputs: ${report.inputs.length}`,
    `- Native frames: ${report.frames.length}`,
    `- NDK: ${report.tools.ndk || "<not found>"}`,
    `- ndk-stack: ${report.tools.ndkStack || "<not found>"}`,
    "",
    "| Line | PC | Library | Symbol |",
    "| --- | --- | --- | --- |",
  ];
  report.frames.slice(0, 40).forEach((frame) => {
    lines.push(`| ${frame.line} | ${frame.pc} | ${frame.library} | ${frame.symbol || ""} |`);
  });
  if (report.frames.length === 0) {
    lines.push("| | | | |");
  }
  if (report.ndkStack.reason) {
    lines.push("", `ndk-stack: ${report.ndkStack.reason}`);
  }
  return lines.join("\n") + "\n";
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
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
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node symbolize-node-crash.js [--repo-root dir] [--report-dir dir] [--bundle-dir dir] [--tombstone file] [--logcat file] [--symbol-dir dir] [--ndk dir]");
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
