#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-2417-security-probes-v1";
const TARGET_VERSION = "24.17.0";
const SELECTOR =
  "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase8Node2417SecurityFixProbesStaySafe";
const SOURCE_FILE =
  "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt";
const CORE_SOURCE_FILE =
  "app/src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt";
const FS_SOURCE_FILE =
  "app/src/androidTest/java/org/autojs/autojs/engine/NodeFsPromisesInstrumentationTest.kt";

const PROBES = [
  {
    id: "nul_hostname_rejected",
    description: "NUL-containing hostnames are rejected before network dispatch.",
    sourceMarkers: ["phase8Node2417SecurityFixProbesStaySafe", "\"nul_hostname\"", "ERR_INVALID_URL"],
    evidenceMarkers: ["security.phase8.nul_hostname=", "ERR_INVALID_URL"],
  },
  {
    id: "tls_hostname_normalization_policy",
    description: "Raw TLS remains policy-denied; no raw TLS surface is exposed just to test hostname normalization.",
    sourceMarkers: ["\"tls_hostname_policy\"", "require(\"tls\").connect"],
    evidenceMarkers: ["security.phase8.tls_hostname_policy=", "ERR_AUTOJS6_NETWORK_DISABLED"],
  },
  {
    id: "proxy_tunnel_credential_redaction",
    description: "Credential-bearing HTTPS/proxy-style URLs are denied without leaking userinfo or path secrets.",
    sourceMarkers: ["\"https_credentials_redacted\"", "user:pass", "leaked=false"],
    evidenceMarkers: ["security.phase8.https_credentials_redacted=", "leaked=false"],
  },
  {
    id: "http2_origin_state_bounded",
    description: "HTTP/2 stays unavailable, so origin state cannot be created from user scripts.",
    sourceMarkers: ["\"http2_origin_state\"", "require(\"http2\")"],
    evidenceMarkers: ["security.phase8.http2_origin_state="],
  },
  {
    id: "webcrypto_excessive_output_rejected",
    description: "WebCrypto random output over 65,536 bytes is rejected.",
    sourceMarkers: ["\"webcrypto_quota\"", "getRandomValues(new Uint8Array(65537))"],
    supplementalMarkers: ["randomQuotaError"],
    supplementalSource: CORE_SOURCE_FILE,
    evidenceMarkers: ["security.phase8.webcrypto_quota=", "QuotaExceededError"],
  },
  {
    id: "filehandle_scoped_fs_policy",
    description: "FileHandle open/read remains scoped and rejects path escape attempts.",
    sourceMarkers: ["\"filehandle_escape\"", "fsp.open(\"../escape.txt\""],
    supplementalMarkers: ["scopedPromisesFileHandleStreamsAndFileUrlsWork", "scopedPromisesRejectEscapesAndMissingFiles"],
    supplementalSource: FS_SOURCE_FILE,
    evidenceMarkers: ["security.phase8.filehandle_escape=", "ERR_AUTOJS6_FS_PATH_ESCAPE"],
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const sourceText = readOptional(path.join(repoRoot, SOURCE_FILE));
  const candidateSecurityReport = options.candidateSecurityReport
    ? path.resolve(options.candidateSecurityReport)
    : findLatestCandidateSecurityReport(repoRoot);
  const candidateText = candidateSecurityReport ? readOptional(candidateSecurityReport) : "";
  const candidateSelectorPassed = candidateText ? selectorPassed(candidateText) : false;
  const probes = PROBES.map((probe) => evaluateProbe(
    repoRoot,
    sourceText,
    probe,
    candidateSecurityReport,
    candidateText,
    candidateSelectorPassed,
  ));
  const missingSourceCoverage = probes
    .filter((probe) => probe.sourceStatus !== "covered")
    .map((probe) => ({ probe: probe.id, reason: probe.sourceReason }));
  const missingCandidateEvidence = probes
    .filter((probe) => probe.candidateEvidence.status !== "covered")
    .map((probe) => ({ probe: probe.id, reason: probe.candidateEvidence.reason }));
  const decision = {
    status: missingSourceCoverage.length === 0 && missingCandidateEvidence.length === 0 ? "ready" : "blocked",
    sourcePlanReady: missingSourceCoverage.length === 0,
    candidateRuntimeReady: missingCandidateEvidence.length === 0,
    selector: SELECTOR,
    summary: missingSourceCoverage.length === 0
      ? "Phase 8 security probes are present in the instrumentation corpus; candidate runtime evidence is still required before promotion."
      : "Phase 8 security probes are incomplete in the instrumentation corpus.",
    blockers: [
      ...missingSourceCoverage.map((item) => ({ area: `source.${item.probe}`, reason: item.reason })),
      ...missingCandidateEvidence.map((item) => ({ area: `candidate.${item.probe}`, reason: item.reason })),
    ],
  };
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    targetVersion: `v${normalizeVersion(options.targetVersion || TARGET_VERSION)}`,
    sourceFile: SOURCE_FILE,
    selector: SELECTOR,
    candidateSecurityReport: candidateSecurityReport ? relativePath(repoRoot, candidateSecurityReport) : null,
    candidateSelectorPassed,
    probes,
    decision,
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "node-24.17-security-probes.json");
  const markdownPath = path.join(reportDir, "node-24.17-security-probes.md");
  const markdown = renderMarkdown(report);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, markdown);
  if (options.docReport) {
    const docPath = path.resolve(options.docReport);
    fs.mkdirSync(path.dirname(docPath), { recursive: true });
    fs.writeFileSync(docPath, markdown);
  }

  console.log("Node 24.17 security probes JSON: " + jsonPath);
  console.log("Node 24.17 security probes Markdown: " + markdownPath);
  console.log("Node 24.17 security probes decision: " + decision.status);
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
    "Usage: node tools/nodejs/patch-upgrade/generate-node2417-security-probes.js [options]",
    "",
    "Options:",
    "  --target-version <version>             Candidate Node version. Defaults to 24.17.0.",
    "  --candidate-security-report <file>     JUnit XML or text output from the candidate security selector.",
    "  --report-dir <dir>                     Output directory. Defaults to build/reports/nodejs.",
    "  --doc-report <file>                    Also write Markdown to a tracked docs path.",
    "  --fail-on-blocked                      Exit non-zero unless source and candidate evidence are complete.",
  ].join("\n"));
}

function evaluateProbe(repoRoot, sourceText, probe, candidateSecurityReport, candidateText, candidateSelectorPassed) {
  const supplementalText = probe.supplementalSource
    ? readOptional(path.join(repoRoot, probe.supplementalSource))
    : "";
  const sourceMissing = [
    ...probe.sourceMarkers.filter((marker) => !sourceText.includes(marker)),
    ...(probe.supplementalMarkers || []).filter((marker) => !supplementalText.includes(marker)),
  ];
  const candidateMissing = candidateSecurityReport && !candidateSelectorPassed
    ? probe.evidenceMarkers.filter((marker) => !candidateText.includes(marker))
    : probe.evidenceMarkers;
  return {
    id: probe.id,
    description: probe.description,
    selector: SELECTOR,
    sourceStatus: sourceMissing.length === 0 ? "covered" : "missing",
    sourceReason: sourceMissing.length === 0 ? null : "missing marker(s): " + sourceMissing.join(", "),
    candidateEvidence: {
      status: candidateSelectorPassed || candidateMissing.length === 0 ? "covered" : "missing",
      path: candidateSecurityReport || null,
      reason: candidateSelectorPassed
        ? null
        : candidateSecurityReport
          ? (candidateMissing.length === 0 ? null : "missing candidate evidence marker(s): " + candidateMissing.join(", "))
          : "candidate security report was not supplied",
      selectorPassed: candidateSelectorPassed,
    },
  };
}

function findLatestCandidateSecurityReport(repoRoot) {
  const root = path.join(repoRoot, "app/build/outputs/androidTest-results/connected/debug/flavors/app");
  if (!isDirectory(root)) {
    return null;
  }
  const candidates = [];
  const stack = [root];
  while (stack.length > 0) {
    const dir = stack.pop();
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (entry.isFile() && entry.name.startsWith("TEST-") && entry.name.endsWith(".xml")) {
        const text = readOptional(full);
        if (text.includes("phase8Node2417SecurityFixProbesStaySafe") || text.includes("security.phase8.")) {
          candidates.push({ file: full, mtimeMs: fs.statSync(full).mtimeMs });
        }
      }
    }
  }
  return candidates.sort((left, right) => right.mtimeMs - left.mtimeMs)[0]?.file || null;
}

function selectorPassed(text) {
  const testcasePattern = /<testcase\b[^>]*name="phase8Node2417SecurityFixProbesStaySafe"[\s\S]*?(?:<\/testcase>|\/>)/;
  const match = text.match(testcasePattern);
  if (!match) {
    return false;
  }
  return !/<failure\b|<error\b/.test(match[0]);
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Node 24.17 Security Probes");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push(`- Status: **${report.decision.status}**`);
  lines.push(`- Source plan ready: ${report.decision.sourcePlanReady}`);
  lines.push(`- Candidate runtime ready: ${report.decision.candidateRuntimeReady}`);
  lines.push(`- Selector: \`${report.selector}\``);
  lines.push(`- Candidate report: ${report.candidateSecurityReport || "not supplied"}`);
  lines.push("");
  if (report.decision.blockers.length > 0) {
    lines.push("## Blockers");
    lines.push("");
    for (const blocker of report.decision.blockers) {
      lines.push(`- ${blocker.area}: ${blocker.reason}`);
    }
    lines.push("");
  }
  lines.push("## Probe Matrix");
  lines.push("");
  lines.push("| Probe | Source | Candidate Evidence | Notes |");
  lines.push("| --- | --- | --- | --- |");
  for (const probe of report.probes) {
    lines.push([
      `| ${probe.id}`,
      probe.sourceStatus,
      probe.candidateEvidence.status,
      probe.description,
    ].join(" | ") + " |");
  }
  lines.push("");
  lines.push("## Re-run");
  lines.push("");
  lines.push("```powershell");
  lines.push(".\\gradlew.bat --console=plain :app:generateNode2417SecurityProbeReport");
  lines.push(".\\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest \"-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase8Node2417SecurityFixProbesStaySafe\" \"-Pautojs.nodejs.runtimeSlot=node24_current\" \"-Pautojs.nodejs.patchCandidateJniLibs=D:\\\\path\\\\to\\\\jniLibs\" \"-Pautojs.nodejs.patchCandidateHeaders=D:\\\\path\\\\to\\\\include\\\\node\"");
  lines.push("```");
  lines.push("");
  return lines.join("\n");
}

function readOptional(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_error) {
    return "";
  }
}

function normalizeVersion(value) {
  return String(value).replace(/^v/, "");
}

function parseBoolean(value) {
  return ["1", "true", "yes"].includes(String(value).toLowerCase());
}

function relativePath(repoRoot, file) {
  return file ? path.relative(repoRoot, file).replace(/\\/g, "/") || "." : null;
}

function isDirectory(file) {
  try {
    return fs.statSync(file).isDirectory();
  } catch (_error) {
    return false;
  }
}

main();
