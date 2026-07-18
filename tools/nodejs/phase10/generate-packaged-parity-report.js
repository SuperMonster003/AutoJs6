#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REQUIRED_DESCRIPTOR_FIELDS = [
  "bootstrap",
  "commonJsLoader",
  "esmLoader",
  "dynamicImport",
  "builtinProfile",
  "bridgeProtocol",
  "capabilityManifest",
  "moduleSourcesSchema",
];

const REQUIRED_PACKAGED_TESTS = [
  "installAndRunFullCompatibilityEmbeddedNodePackagedApk",
  "installAndRunExperimentalEsmEmbeddedNodePackagedApk",
  "installAndRunDynamicImportLocalEmbeddedNodePackagedApk",
  "installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const jniLibsRoot = resolveJniLibsRoot(repoRoot, options.jniLibsRoot);
  const violations = [];
  const descriptor = inspectDescriptor(repoRoot, violations);
  const packagedTests = inspectPackagedTests(repoRoot, violations);
  const cacheKey = buildFixtureCacheKey(repoRoot, jniLibsRoot, violations);

  const report = {
    schema: "autojs6-node-phase10-packaged-parity-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-04/S10-06/S10-07/S10-10 Packaged Runtime Parity",
    gate: "verifyNodePhase10PackagedParityHost",
    descriptor,
    packagedTests,
    fixtureCacheKey: cacheKey,
    policyViolations: violations,
    decision: {
      status: violations.length === 0 ? "ready" : "failed",
      schemaBumpRequiredForPhase10: false,
      descriptorVersionVectorCoversPhase10V2Fields: violations.length === 0,
      staleTemplateFailFastCovered: packagedTests.markers.staleTemplateFailFast,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-packaged-parity", report, renderMarkdown);
  console.log("Phase 10 packaged parity host decision: " + report.decision.status);
  if (violations.length > 0) {
    process.exitCode = 1;
  }
}

function inspectDescriptor(repoRoot, violations) {
  const file = path.join(repoRoot, "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt");
  const text = fs.readFileSync(file, "utf8");
  const schema = extractStringConst(text, "SCHEMA");
  const versions = Object.fromEntries(REQUIRED_DESCRIPTOR_FIELDS.map((field) => {
    const regex = new RegExp(`"${escapeRegExp(field)}"\\s+to\\s+([A-Z0-9_]+)`);
    const match = text.match(regex);
    return [field, match ? extractStringConst(text, match[1]) : null];
  }));
  for (const [field, value] of Object.entries(versions)) {
    if (!value) {
      violations.push(`descriptor is missing versions.${field}`);
    }
  }
  for (const token of [
    "VERSION_MISMATCH_CODE",
    "MODULE_SOURCES_SCHEMA_VERSION",
    "NodeBridgePermissionManifest.definedCapabilities.size",
    "moduleSourcesMaxCount",
    "moduleSourceMaxBytes",
    "moduleSourcesMaxBytes",
  ]) {
    if (!text.includes(token)) {
      violations.push(`descriptor is missing required token: ${token}`);
    }
  }
  return {
    path: "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt",
    schema,
    phase10DescriptorShape: "v2-version-vector-on-v1-compatible-schema",
    versionFields: versions,
    versionMismatchCode: extractStringConst(text, "VERSION_MISMATCH_CODE"),
    providerRegistrySchema: "not_yet_declared",
  };
}

function inspectPackagedTests(repoRoot, violations) {
  const file = path.join(repoRoot, "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt");
  const text = fs.readFileSync(file, "utf8");
  const required = REQUIRED_PACKAGED_TESTS.map((method) => {
    const present = text.includes(`fun ${method}(`);
    if (!present) {
      violations.push(`missing packaged parity test method: ${method}`);
    }
    return { method, present };
  });
  const markers = {
    mjsEntry: text.includes("main.mjs"),
    typeModule: text.includes("\"type\":\"module\"") || text.includes("\"type\": \"module\""),
    staticImport: text.includes("import dep, { named, explode } from \"./lib.mjs\""),
    topLevelAwait: text.includes("await Promise.all"),
    dynamicImportMjs: text.includes("import(\"./dynamic-target.mjs\")"),
    dynamicImportCjs: text.includes("import(\"./dynamic-legacy.cjs\")") || text.includes("packaged.dynamic_import.cjs"),
    allowedBuiltinImport: text.includes("node:path") || text.includes("path.basename"),
    disabledBuiltinDenied: text.includes("disabledBuiltin") || text.includes("ERR_AUTOJS6_BUILTIN_MODULE_DISABLED"),
    pathEscapeDenied: text.includes("../../outside.mjs"),
    sourceMapOrSourceName: text.includes("sourceName") || text.includes("source map"),
    staleTemplateFailFast: text.includes("Packaged smoke template is stale") && text.includes("missingRequiredMarkers"),
    descriptorMismatch: text.includes("NodeProjectRuntimeCompatibilityDescriptor.VERSION_MISMATCH_CODE") ||
      text.includes("ERR_AUTOJS6_PACKAGED_RUNTIME_VERSION_MISMATCH"),
  };
  for (const [marker, present] of Object.entries(markers)) {
    if (!present) {
      violations.push(`packaged parity marker missing: ${marker}`);
    }
  }
  return {
    path: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
    required,
    markers,
  };
}

function buildFixtureCacheKey(repoRoot, jniLibsRoot, violations) {
  const inputs = [
    { input: "app/src/main/assets-app/template.apk", target: path.join(repoRoot, "app/src/main/assets-app/template.apk") },
    { input: path.relative(repoRoot, jniLibsRoot).replace(/\\/g, "/"), target: jniLibsRoot, nativeLibraries: true },
    { input: "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt", target: path.join(repoRoot, "app/src/main/java/org/autojs/autojs/script/NodeProjectRuntimeCompatibilityDescriptor.kt") },
    { input: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt", target: path.join(repoRoot, "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt") },
  ];
  const parts = [];
  for (const input of inputs) {
    const target = input.target;
    if (!fs.existsSync(target)) {
      parts.push({ input: input.input, nativeLibraries: Boolean(input.nativeLibraries), available: false, hash: null });
      continue;
    }
    const hash = fs.statSync(target).isDirectory() ? hashDirectory(target) : sha256(target);
    parts.push({ input: input.input, nativeLibraries: Boolean(input.nativeLibraries), available: true, hash });
  }
  if (!parts.some((entry) => entry.nativeLibraries && entry.available)) {
    violations.push("fixture cache key cannot include native library hashes because the configured jniLibs root is missing");
  }
  const keyHash = crypto.createHash("sha256")
    .update(JSON.stringify(parts))
    .update(JSON.stringify({
      runtimeSlot: "node24_5",
      esmPartial: true,
      dynamicImportLocal: true,
      moduleSourcesMaxCount: 64,
    }))
    .digest("hex");
  return {
    status: "key_material_ready",
    key: keyHash,
    inputs: parts,
    requiredInvalidators: [
      "template hash",
      "manifest patch hash",
      "native library hashes",
      "runtime assets hash",
      "fixture input hash",
      "feature flags",
    ],
    installLaunchStillRequired: true,
  };
}

function resolveJniLibsRoot(repoRoot, requested) {
  const candidates = [
    requested && path.resolve(requested),
    path.resolve(repoRoot, "../AutoJs6-Plugin-NodeJs-Runtime/app/src/main/jniLibs"),
    path.join(repoRoot, "app/src/main/jniLibs"),
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || candidates[candidates.length - 1];
}

function hashDirectory(dir) {
  const hash = crypto.createHash("sha256");
  for (const file of walk(dir).sort()) {
    const relative = path.relative(dir, file).replace(/\\/g, "/");
    hash.update(relative);
    hash.update("\0");
    hash.update(fs.readFileSync(file));
    hash.update("\0");
  }
  return hash.digest("hex");
}

function walk(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const child = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(child));
    } else {
      files.push(child);
    }
  }
  return files;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Packaged Parity",
    "",
    `- Decision: ${report.decision.status}`,
    `- Descriptor schema: ${report.descriptor.schema}`,
    `- Descriptor shape: ${report.descriptor.phase10DescriptorShape}`,
    `- Fixture cache key status: ${report.fixtureCacheKey.status}`,
    `- Fixture cache key: ${report.fixtureCacheKey.key}`,
    "",
    "## Packaged Markers",
    "",
    "| Marker | Present |",
    "| --- | --- |",
  ];
  for (const [marker, present] of Object.entries(report.packagedTests.markers)) {
    lines.push(`| ${marker} | ${present ? "yes" : "no"} |`);
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function extractStringConst(text, name) {
  const match = text.match(new RegExp(`const\\s+val\\s+${escapeRegExp(name)}\\s*=\\s*"([^"]+)"`));
  return match ? match[1] : null;
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-packaged-parity-report.js [--repo-root dir] [--jni-libs-root dir] [--report-dir dir]");
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

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

main();
