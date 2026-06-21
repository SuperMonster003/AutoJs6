#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REQUIRED_MANIFEST_FIELDS = [
  "schema",
  "generatedAt",
  "runtimeDescriptor",
  "crashMarker",
  "automaticCollection",
  "nativeLibraryHashes",
  "resourceSnapshot",
  "timelineTail",
  "symbolization",
  "chaosInjection",
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const bundleDir = path.resolve(options.bundleDir || path.join(reportDir, "final-crash-evidence-bundle"));
  const manifestPath = path.join(bundleDir, "manifest.json");
  const manifest = fs.existsSync(manifestPath) ? readJson(manifestPath) : null;
  const policyViolations = [];
  const warnings = [];

  if (!manifest) {
    policyViolations.push("missing crash evidence bundle manifest");
  } else {
    if (manifest.schema !== "autojs6-node-final-crash-evidence-bundle-v1") {
      policyViolations.push("unexpected manifest schema: " + manifest.schema);
    }
    for (const field of REQUIRED_MANIFEST_FIELDS) {
      if (!(field in manifest)) {
        policyViolations.push("missing manifest field: " + field);
      }
    }
    if (!Array.isArray(manifest.nativeLibraryHashes) || manifest.nativeLibraryHashes.length === 0) {
      warnings.push("no native library hashes were recorded");
    }
    if (!manifest.symbolization || manifest.symbolization.implemented !== true) {
      warnings.push("symbolization tooling is not marked implemented in the bundle");
    }
    if (!isImplemented(manifest.crashMarker)) {
      warnings.push("safe crash marker is not implemented");
    } else {
      checkManifestFile(repoRoot, bundleDir, manifest.crashMarker, "crash marker", policyViolations);
    }
    if (manifest.automaticCollection !== true) {
      warnings.push("next-launch crash evidence collection is not automatic");
    }
    const resourceSnapshot = readManifestArtifact(repoRoot, bundleDir, manifest.resourceSnapshot, "resource snapshot", policyViolations);
    if (!isImplemented(manifest.resourceSnapshot)) {
      warnings.push("resource snapshot is schema-only");
    } else if (resourceSnapshot && resourceSnapshot.schema !== "autojs6-node-final-crash-resource-snapshot-v1") {
      policyViolations.push("unexpected resource snapshot schema: " + resourceSnapshot.schema);
    }
    const timelineTail = readManifestArtifact(repoRoot, bundleDir, manifest.timelineTail, "timeline tail", policyViolations);
    if (!isImplemented(manifest.timelineTail)) {
      warnings.push("runtime timeline tail is schema-only");
    } else if (timelineTail && timelineTail.schema !== "autojs6-node-final-crash-timeline-tail-v1") {
      policyViolations.push("unexpected timeline tail schema: " + timelineTail.schema);
    }
    if (!isImplemented(manifest.chaosInjection)) {
      warnings.push("chaos injection is not implemented");
    }
  }

  const attachmentChecks = checkAttachments(repoRoot, manifest);
  policyViolations.push(...attachmentChecks.policyViolations);
  warnings.push(...attachmentChecks.warnings);

  const report = {
    schema: "autojs6-node-crash-bundle-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeCrashBundle",
    bundle: {
      directory: relativePath(repoRoot, bundleDir),
      manifest: relativePath(repoRoot, manifestPath),
      available: manifest !== null,
    },
    warnings,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready_with_recorded_blockers" : "failed",
      schemaValid: policyViolations.length === 0,
      finalCrashGateReady: false,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-crash-bundle-verification", report, renderMarkdown);
  console.log("Crash bundle verification decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function checkAttachments(repoRoot, manifest) {
  const policyViolations = [];
  const warnings = [];
  if (!manifest || !Array.isArray(manifest.attachments)) {
    return { policyViolations, warnings };
  }
  for (const attachment of manifest.attachments) {
    if (attachment.missing) {
      warnings.push("attachment source missing: " + attachment.source);
      continue;
    }
    if (!attachment.path) {
      policyViolations.push("attachment is missing copied path: " + attachment.kind);
      continue;
    }
    const file = path.resolve(repoRoot, attachment.path);
    if (!fs.existsSync(file)) {
      policyViolations.push("copied attachment is missing: " + attachment.path);
    }
  }
  return { policyViolations, warnings };
}

function renderMarkdown(report) {
  const lines = [
    "# Final Crash Bundle Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Manifest: ${report.bundle.manifest}`,
    `- Warnings: ${report.warnings.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Warnings",
    "",
  ];
  if (report.warnings.length === 0) {
    lines.push("None.");
  } else {
    report.warnings.forEach((warning) => lines.push(`- ${warning}`));
  }
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function readJson(file) {
  return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
}

function stripBom(value) {
  return value.replace(/^\uFEFF/, "");
}

function isImplemented(value) {
  return value === true || Boolean(value && typeof value === "object" && value.implemented === true);
}

function readManifestArtifact(repoRoot, bundleDir, value, label, policyViolations) {
  const artifactPath = manifestArtifactPath(repoRoot, bundleDir, value);
  if (!artifactPath) {
    return null;
  }
  if (!fs.existsSync(artifactPath) || !fs.statSync(artifactPath).isFile()) {
    policyViolations.push(`${label} artifact is missing: ${relativePath(repoRoot, artifactPath)}`);
    return null;
  }
  try {
    return readJson(artifactPath);
  } catch (error) {
    policyViolations.push(`${label} artifact is not valid JSON: ${error.message}`);
    return null;
  }
}

function checkManifestFile(repoRoot, bundleDir, value, label, policyViolations) {
  const artifactPath = manifestArtifactPath(repoRoot, bundleDir, value);
  if (!artifactPath) {
    policyViolations.push(`${label} artifact path is missing`);
    return;
  }
  if (!fs.existsSync(artifactPath) || !fs.statSync(artifactPath).isFile()) {
    policyViolations.push(`${label} artifact is missing: ${relativePath(repoRoot, artifactPath)}`);
  }
}

function manifestArtifactPath(repoRoot, bundleDir, value) {
  if (!value) {
    return "";
  }
  if (typeof value === "string") {
    return path.resolve(repoRoot, value);
  }
  if (typeof value === "object" && value.path) {
    return path.resolve(repoRoot, value.path);
  }
  if (typeof value === "object" && value.file) {
    return path.resolve(bundleDir, value.file);
  }
  return "";
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
      console.log("Usage: node verify-node-crash-bundle.js [--repo-root dir] [--report-dir dir] [--bundle-dir dir]");
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
