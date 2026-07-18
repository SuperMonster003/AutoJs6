#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];
const PAGE_SIZE_16K = 16384;
const SOURCE_LIBRARIES = [
  { name: "libnode.so", role: "runtime" },
  { name: "libc++_shared.so", role: "cxx_runtime" },
];
const BUILT_LIBRARIES = [
  { name: "libautojs6-node.so", role: "adapter_surface" },
];
const PUBLIC_ANDROID_DEPS = new Set([
  "libandroid.so",
  "libc.so",
  "libdl.so",
  "liblog.so",
  "libm.so",
  "libz.so",
  "libc++_shared.so",
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const jniLibsRoot = path.resolve(options.jniLibsRoot || path.join(repoRoot, "app/src/main/jniLibs"));
  const cxxRoot = path.resolve(options.cxxRoot || path.join(repoRoot, "app/build/intermediates/cxx"));
  const mode = options.mode || "all";
  const tools = findAndroidTools(repoRoot);
  const artifacts = collectArtifacts(repoRoot, jniLibsRoot, cxxRoot, tools);
  const reports = buildReports(artifacts, tools);

  fs.mkdirSync(reportDir, { recursive: true });
  if (mode === "all" || mode === "alignment") {
    writeReport(reportDir, "native-16kb-alignment", reports.alignment, renderAlignmentMarkdown);
  }
  if (mode === "all" || mode === "manifest") {
    writeReport(reportDir, "runtime-artifact-manifest", reports.manifest, renderManifestMarkdown);
  }
  if (mode === "all" || mode === "integrity") {
    writeReport(reportDir, "runtime-artifact-integrity", reports.integrity, renderIntegrityMarkdown);
  }
  const decision = mode === "alignment"
    ? reports.alignment.decision
    : mode === "manifest"
      ? reports.manifest.decision
      : mode === "integrity"
        ? reports.integrity.decision
        : reports.integrity.decision;
  console.log(`Runtime artifact ${mode} decision: ${decision.status}`);
  if (decision.status === "blocked" || (options.failOnBootstrapOnly && decision.status !== "ready")) {
    process.exitCode = 1;
  }
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-bootstrap-only"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/nodejs/native/verify-runtime-artifacts.js [--repo-root dir] [--jni-libs-root dir] [--cxx-root dir] [--report-dir dir] [--mode all|alignment|manifest|integrity]");
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
  if (options.mode && !["all", "alignment", "manifest", "integrity"].includes(options.mode)) {
    throw new Error("Unsupported mode: " + options.mode);
  }
  return options;
}

function collectArtifacts(repoRoot, jniLibsRoot, cxxRoot, tools) {
  const artifacts = [];
  for (const abi of REQUIRED_ABIS) {
    for (const library of SOURCE_LIBRARIES) {
      const file = sourceLibrary(jniLibsRoot, abi, library.name) || latestBuiltLibrary(cxxRoot, abi, library.name);
      artifacts.push(inspectArtifact(repoRoot, tools, abi, library, file));
    }
    for (const library of BUILT_LIBRARIES) {
      artifacts.push(inspectArtifact(repoRoot, tools, abi, library, latestBuiltLibrary(cxxRoot, abi, library.name)));
    }
  }
  return artifacts;
}

function inspectArtifact(repoRoot, tools, abi, library, file) {
  const artifact = {
    abi,
    name: library.name,
    role: library.role,
    path: file ? relativePath(repoRoot, file) : null,
    available: !!file,
    size: file ? fs.statSync(file).size : null,
    sha256: file ? sha256(file) : null,
    buildId: null,
    needed: [],
    loadSegments: [],
    textRelocations: false,
    privateDependencies: [],
    alignment16kb: {
      compatible: false,
      violations: [],
    },
  };
  if (!file || !tools) {
    if (!tools) {
      artifact.alignment16kb.violations.push("Android NDK llvm-readelf was not found");
    }
    return artifact;
  }
  artifact.buildId = parseBuildId(tools, file);
  artifact.needed = parseNeeded(tools, file);
  artifact.loadSegments = parseLoadSegments(tools, file);
  artifact.textRelocations = hasTextRelocations(tools, file);
  artifact.privateDependencies = artifact.needed.filter((entry) => !PUBLIC_ANDROID_DEPS.has(entry));
  artifact.alignment16kb = evaluate16KbAlignment(artifact.loadSegments);
  return artifact;
}

function buildReports(artifacts, tools) {
  const generatedAt = new Date().toISOString();
  const unavailable = artifacts.filter((artifact) => !artifact.available);
  const badAlignmentArtifacts = artifacts.filter((artifact) => artifact.available && !artifact.alignment16kb.compatible)
    .map((artifact) => `${artifact.abi}/${artifact.name}`);
  const textRelocs = artifacts.filter((artifact) => artifact.textRelocations)
    .map((artifact) => `${artifact.abi}/${artifact.name}`);
  const privateDeps = artifacts.flatMap((artifact) =>
    artifact.privateDependencies.map((dep) => `${artifact.abi}/${artifact.name}: ${dep}`));
  const missingBuildIds = artifacts.filter((artifact) => artifact.available && !artifact.buildId)
    .map((artifact) => `${artifact.abi}/${artifact.name}`);

  const hardBlockers = []
    .concat(tools ? [] : ["Android NDK llvm-readelf tools were not found"])
    .concat(unavailable.map((artifact) => `missing artifact: ${artifact.abi}/${artifact.name}`));
  const compatibilityGaps = []
    .concat(badAlignmentArtifacts.map((entry) => `not 16 KB aligned: ${entry}`))
    .concat(textRelocs.map((entry) => `TEXTREL detected: ${entry}`))
    .concat(privateDeps.map((entry) => `private or unexpected dependency: ${entry}`));

  const integrityWarnings = missingBuildIds.length > 0
    ? [`Build ID is missing for current artifact(s): ${missingBuildIds.join(", ")}`]
    : [];
  const compatibilityWarnings = compatibilityGaps.length > 0
    ? [`current artifacts are not fully 16 KB/runtime-integrity ready: ${compatibilityGaps.join("; ")}`]
    : [];
  const manifestStatus = hardBlockers.length === 0
    ? missingBuildIds.length === 0 && compatibilityGaps.length === 0 ? "ready" : "bootstrap_only"
    : "blocked";
  const alignmentStatus = hardBlockers.length === 0
    ? compatibilityGaps.length === 0 ? "ready" : "bootstrap_only"
    : "blocked";
  const integrityStatus = hardBlockers.length === 0
    ? missingBuildIds.length === 0 && compatibilityGaps.length === 0 ? "ready" : "bootstrap_only"
    : "blocked";
  return {
    alignment: {
      schema: "autojs6-node-native-16kb-alignment-v1",
      generatedAt,
      task: "S9-09 16 KB ELF Alignment",
      requiredPageSize: PAGE_SIZE_16K,
      artifacts,
      decision: {
        status: alignmentStatus,
        blockers: hardBlockers,
        warnings: compatibilityWarnings,
      },
    },
    manifest: {
      schema: "autojs6-node-runtime-artifact-manifest-v1",
      generatedAt,
      task: "S9-11 Runtime Artifact Manifest",
      runtimeSlot: "node24_5",
      runtimeVersion: "24.5.0",
      supportedAndroidApis: {
        minSdk: 24,
      },
      supportedPageSizes: [4096, 16384],
      adapterAbiVersion: 1,
      abis: REQUIRED_ABIS,
      artifacts,
      decision: {
        status: manifestStatus,
        blockers: hardBlockers,
        warnings: integrityWarnings.concat(compatibilityWarnings),
      },
    },
    integrity: {
      schema: "autojs6-node-runtime-artifact-integrity-v1",
      generatedAt,
      task: "S9-12 Runtime Artifact Integrity Verification",
      checks: {
        sha256: artifacts.every((artifact) => !!artifact.sha256),
        buildId: missingBuildIds.length === 0,
        alignment16kb: compatibilityGaps.length === 0,
        textRelocations: textRelocs.length === 0,
        publicDependenciesOnly: privateDeps.length === 0,
      },
      artifacts,
      decision: {
        status: integrityStatus,
        blockers: hardBlockers,
        warnings: integrityWarnings.concat(compatibilityWarnings),
      },
    },
  };
}

function evaluate16KbAlignment(loadSegments) {
  const violations = [];
  if (loadSegments.length === 0) {
    violations.push("ELF LOAD segments were not found");
  }
  for (const segment of loadSegments) {
    if (segment.align < PAGE_SIZE_16K) {
      violations.push(`LOAD alignment ${toHex(segment.align)} is smaller than 0x4000`);
    }
    if ((segment.offset % PAGE_SIZE_16K) !== (segment.virtualAddress % PAGE_SIZE_16K)) {
      violations.push(`LOAD offset ${toHex(segment.offset)} and vaddr ${toHex(segment.virtualAddress)} are not congruent modulo 0x4000`);
    }
  }
  return {
    compatible: violations.length === 0,
    violations,
  };
}

function sourceLibrary(jniLibsRoot, abi, name) {
  const file = path.join(jniLibsRoot, abi, name);
  return fs.existsSync(file) ? file : null;
}

function latestBuiltLibrary(cxxRoot, abi, name) {
  if (!fs.existsSync(cxxRoot)) {
    return null;
  }
  return walkFiles(cxxRoot)
    .filter((file) => path.basename(file) === name && file.split(/[\\/]/).includes(abi))
    .map((file) => ({ file, mtime: fs.statSync(file).mtimeMs }))
    .sort((a, b) => b.mtime - a.mtime)
    .map((entry) => entry.file)[0] || null;
}

function parseBuildId(tools, file) {
  return runTool([tools.readelf, "-n", "--wide", file])
    .lines
    .map((line) => {
      const match = line.match(/Build ID:\s*([0-9A-Fa-f]+)/);
      return match ? match[1] : null;
    })
    .find(Boolean) || null;
}

function parseNeeded(tools, file) {
  return runTool([tools.readelf, "-d", "--wide", file])
    .lines
    .map((line) => {
      const match = line.match(/Shared library: \[(.+)]/);
      return match ? match[1] : null;
    })
    .filter(Boolean)
    .sort();
}

function parseLoadSegments(tools, file) {
  return runTool([tools.readelf, "-l", "--wide", file])
    .lines
    .filter((line) => /^\s*LOAD\s+/.test(line))
    .map((line) => {
      const parts = line.trim().split(/\s+/).filter(Boolean);
      return {
        offset: parseHex(parts[1]),
        virtualAddress: parseHex(parts[2]),
        align: parseHex(parts[parts.length - 1]),
        raw: line.trim(),
      };
    })
    .filter((entry) => Number.isFinite(entry.offset) && Number.isFinite(entry.virtualAddress) && Number.isFinite(entry.align));
}

function hasTextRelocations(tools, file) {
  return runTool([tools.readelf, "-d", "--wide", file]).lines.some((line) => line.includes("TEXTREL"));
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
  if (!fs.existsSync(readelf)) {
    return null;
  }
  return { readelf };
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
  const text = (result.stdout || "") + (result.stderr || "");
  return {
    status: result.status,
    lines: text.split(/\r?\n/).filter(Boolean),
  };
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

function parseHex(value) {
  return parseInt(String(value || "").replace(/^0x/i, ""), 16);
}

function toHex(value) {
  return "0x" + value.toString(16);
}

function writeReport(reportDir, baseName, report, renderer) {
  const jsonPath = path.join(reportDir, `${baseName}.json`);
  const markdownPath = path.join(reportDir, `${baseName}.md`);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, renderer(report));
  console.log(`${baseName} JSON: ${jsonPath}`);
  console.log(`${baseName} Markdown: ${markdownPath}`);
}

function renderAlignmentMarkdown(report) {
  const lines = commonHeader("AutoJs6 Node Native 16 KB Alignment", report);
  lines.push("| ABI | Library | 16 KB | TEXTREL | Private Deps | Path |");
  lines.push("| --- | --- | --- | --- | --- | --- |");
  for (const artifact of report.artifacts) {
    lines.push(`| ${artifact.abi} | ${artifact.name} | ${artifact.alignment16kb.compatible} | ${artifact.textRelocations} | ${artifact.privateDependencies.join(", ") || "none"} | ${artifact.path || "missing"} |`);
  }
  appendDecision(lines, report.decision);
  return lines.join("\n");
}

function renderManifestMarkdown(report) {
  const lines = commonHeader("AutoJs6 Node Runtime Artifact Manifest", report);
  lines.push(`- runtime slot: ${report.runtimeSlot}`);
  lines.push(`- runtime version: ${report.runtimeVersion}`);
  lines.push(`- adapter ABI: ${report.adapterAbiVersion}`);
  lines.push(`- page sizes: ${report.supportedPageSizes.join(", ")}`);
  lines.push("");
  lines.push("| ABI | Library | Role | Size | Build ID | SHA-256 |");
  lines.push("| --- | --- | --- | ---: | --- | --- |");
  for (const artifact of report.artifacts) {
    lines.push(`| ${artifact.abi} | ${artifact.name} | ${artifact.role} | ${artifact.size || 0} | ${artifact.buildId || "missing"} | ${artifact.sha256 || "missing"} |`);
  }
  appendDecision(lines, report.decision);
  return lines.join("\n");
}

function renderIntegrityMarkdown(report) {
  const lines = commonHeader("AutoJs6 Node Runtime Artifact Integrity", report);
  lines.push("## Checks");
  lines.push("");
  for (const [key, value] of Object.entries(report.checks)) {
    lines.push(`- ${key}: ${value}`);
  }
  appendDecision(lines, report.decision);
  return lines.join("\n");
}

function commonHeader(title, report) {
  return [
    `# ${title}`,
    "",
    `Generated: ${report.generatedAt}`,
    "",
    `Decision: \`${report.decision.status}\``,
    "",
  ];
}

function appendDecision(lines, decision) {
  if (decision.blockers && decision.blockers.length > 0) {
    lines.push("");
    lines.push("## Blockers");
    lines.push("");
    decision.blockers.forEach((issue) => lines.push(`- ${issue}`));
  }
  if (decision.warnings && decision.warnings.length > 0) {
    lines.push("");
    lines.push("## Warnings");
    lines.push("");
    decision.warnings.forEach((warning) => lines.push(`- ${warning}`));
  }
  lines.push("");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

main();
