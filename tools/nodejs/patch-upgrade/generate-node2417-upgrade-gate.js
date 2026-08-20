#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-2417-upgrade-gate-v1";
const DEFAULT_TARGET_VERSION = "24.17.0";

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const targetVersion = normalizeVersion(options.targetVersion || DEFAULT_TARGET_VERSION);
  const inputs = {
    feasibility: path.resolve(options.feasibilityReport || path.join(repoRoot, "build/reports/nodejs/patch-upgrade/node24-patch-upgrade-feasibility.json")),
    nativeDiff: path.resolve(options.nativeDiffReport || path.join(repoRoot, "build/reports/nodejs/node-24.17-native-diff.json")),
    dualRuntimeAB: path.resolve(options.dualRuntimeReport || path.join(repoRoot, "build/reports/nodejs/patch-upgrade/node-dual-runtime-ab-gate.json")),
    securityProbes: path.resolve(options.securityProbeReport || path.join(repoRoot, "build/reports/nodejs/node-24.17-security-probes.json")),
  };
  const reports = {
    feasibility: readReport(repoRoot, inputs.feasibility, "Node 24 patch feasibility"),
    nativeDiff: readReport(repoRoot, inputs.nativeDiff, "Node 24.17 native diff"),
    dualRuntimeAB: readReport(repoRoot, inputs.dualRuntimeAB, "Node dual-runtime A/B"),
    securityProbes: readReport(repoRoot, inputs.securityProbes, "Node 24.17 security probes"),
  };
  const areas = buildAreas(reports);
  const decision = buildDecision(areas, targetVersion);
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    target: {
      currentVersion: "24.5.0",
      candidateVersion: targetVersion,
      promoteDefaultRuntime: false,
      selectorProperty: "autojs.nodejs.runtimeSlot",
      candidateSlot: "node24_current",
      rollbackSlot: "node24_5",
    },
    inputs: Object.fromEntries(Object.entries(inputs).map(([key, value]) => [key, relativePath(repoRoot, value)])),
    areas,
    decision,
    requiredNextSteps: [
      "Supply Node v24.17.0 candidate headers and jniLibs for arm64-v8a, armeabi-v7a, and x86_64.",
      "Run the candidate native diff and feasibility reports with the candidate artifact properties.",
      "Run the same compatibility, security, packaged, memory, and performance suites for node24_5 and node24_current.",
      "Attach the Phase 8 security probe candidate report.",
      "Promote the default runtime only after this gate is ready; keep node24_5 as the rollback selector.",
    ],
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "node-24.17-upgrade-gate.json");
  const markdownPath = path.join(reportDir, "node-24.17-upgrade-gate.md");
  const markdown = renderMarkdown(report);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, markdown);
  if (options.docReport) {
    const docPath = path.resolve(options.docReport);
    fs.mkdirSync(path.dirname(docPath), { recursive: true });
    fs.writeFileSync(docPath, markdown);
  }

  console.log("Node 24.17 upgrade gate JSON: " + jsonPath);
  console.log("Node 24.17 upgrade gate Markdown: " + markdownPath);
  console.log("Node 24.17 upgrade gate decision: " + decision.status);
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
    "Usage: node tools/nodejs/patch-upgrade/generate-node2417-upgrade-gate.js [options]",
    "",
    "Options:",
    "  --target-version <version>          Candidate Node version. Defaults to 24.17.0.",
    "  --feasibility-report <file>         Node 24 patch feasibility JSON.",
    "  --native-diff-report <file>         Node 24.17 native diff JSON.",
    "  --dual-runtime-report <file>        Node dual-runtime A/B JSON.",
    "  --security-probe-report <file>      Node 24.17 security probes JSON.",
    "  --report-dir <dir>                  Output directory. Defaults to build/reports/nodejs.",
    "  --doc-report <file>                 Also write Markdown to a tracked docs path.",
    "  --fail-on-blocked                   Exit non-zero unless the upgrade gate is ready.",
  ].join("\n"));
}

function buildAreas(reports) {
  return [
    buildFeasibilityArea(reports.feasibility),
    buildNativeDiffArea(reports.nativeDiff),
    buildDualRuntimeArea(reports.dualRuntimeAB),
    buildSecurityArea(reports.securityProbes),
  ];
}

function buildFeasibilityArea(report) {
  if (!report.present) {
    return missingArea("patch_feasibility", report.reason);
  }
  const decision = report.json.decision || {};
  const status = decision.goNoGo === "go-for-ab-gate" ? "ready" : "blocked";
  return {
    id: "patch_feasibility",
    label: "S8-01 build intake and static feasibility",
    status,
    report: report.path,
    summary: decision.summary || null,
    blockers: (decision.blockers || []).map((item) => item.reason ? `${item.area}: ${item.reason}` : String(item)),
  };
}

function buildNativeDiffArea(report) {
  if (!report.present) {
    return missingArea("native_diff", report.reason);
  }
  const decision = report.json.decision || {};
  return {
    id: "native_diff",
    label: "S8-02 native ABI/export diff",
    status: decision.status === "ready" ? "ready" : "blocked",
    report: report.path,
    summary: decision.summary || null,
    blockers: (decision.blockers || []).map((item) => item.reason ? `${item.area}: ${item.reason}` : String(item)),
  };
}

function buildDualRuntimeArea(report) {
  if (!report.present) {
    return missingArea("dual_runtime_ab", report.reason);
  }
  const decision = report.json.decision || {};
  return {
    id: "dual_runtime_ab",
    label: "S8-03 dual-runtime A/B evidence",
    status: decision.status === "ready" ? "ready" : "blocked",
    report: report.path,
    summary: decision.summary || null,
    blockers: (decision.blockers || []).map((item) => item.reason ? `${item.slot}.${item.area}: ${item.reason}` : String(item)),
  };
}

function buildSecurityArea(report) {
  if (!report.present) {
    return missingArea("security_probes", report.reason);
  }
  const decision = report.json.decision || {};
  return {
    id: "security_probes",
    label: "S8-04 security fix probes",
    status: decision.status === "ready" ? "ready" : "blocked",
    report: report.path,
    summary: decision.summary || null,
    blockers: (decision.blockers || []).map((item) => item.reason ? `${item.area}: ${item.reason}` : String(item)),
  };
}

function missingArea(id, reason) {
  return {
    id,
    label: id,
    status: "blocked",
    report: null,
    summary: reason,
    blockers: [reason],
  };
}

function buildDecision(areas, targetVersion) {
  const blockers = areas.flatMap((area) => (area.blockers || []).map((reason) => ({
    area: area.id,
    reason,
  })));
  const status = areas.every((area) => area.status === "ready") ? "ready" : "blocked";
  return {
    status,
    promoteDefaultRuntime: false,
    targetVersion: `v${targetVersion}`,
    summary: status === "ready"
      ? `Node v${targetVersion} is ready for an explicit default-runtime promotion commit.`
      : `Node v${targetVersion} upgrade gate is blocked; keep v24.5.0 as default.`,
    blockers,
  };
}

function readReport(repoRoot, file, label) {
  if (!fs.existsSync(file)) {
    return {
      present: false,
      path: relativePath(repoRoot, file),
      reason: `${label} report is missing: ${relativePath(repoRoot, file)}`,
    };
  }
  try {
    return {
      present: true,
      path: relativePath(repoRoot, file),
      json: JSON.parse(fs.readFileSync(file, "utf8")),
    };
  } catch (error) {
    return {
      present: false,
      path: relativePath(repoRoot, file),
      reason: `${label} report is unreadable: ${error.message}`,
    };
  }
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Node 24.17 Upgrade Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push(`- Status: **${report.decision.status}**`);
  lines.push(`- Summary: ${report.decision.summary}`);
  lines.push(`- Promote default runtime: ${report.decision.promoteDefaultRuntime}`);
  lines.push(`- Candidate slot: \`${report.target.candidateSlot}\``);
  lines.push(`- Rollback slot: \`${report.target.rollbackSlot}\``);
  lines.push("");
  if (report.decision.blockers.length > 0) {
    lines.push("## Blockers");
    lines.push("");
    for (const blocker of report.decision.blockers) {
      lines.push(`- ${blocker.area}: ${blocker.reason}`);
    }
    lines.push("");
  }
  lines.push("## Area Matrix");
  lines.push("");
  lines.push("| Area | Status | Report | Summary |");
  lines.push("| --- | --- | --- | --- |");
  for (const area of report.areas) {
    lines.push(`| ${area.label} | ${area.status} | ${area.report || "-"} | ${area.summary || "-"} |`);
  }
  lines.push("");
  lines.push("## Required Next Steps");
  lines.push("");
  for (const step of report.requiredNextSteps) {
    lines.push(`- ${step}`);
  }
  lines.push("");
  lines.push("## Re-run");
  lines.push("");
  lines.push("```powershell");
  lines.push(".\\gradlew.bat --console=plain :app:generateNode2417UpgradeGateReport");
  lines.push(".\\gradlew.bat --console=plain :app:verifyNode2417UpgradeGate \"-Pautojs.nodejs.test.deviceProfile=sony\"");
  lines.push("```");
  lines.push("");
  return lines.join("\n");
}

function relativePath(repoRoot, file) {
  return file ? path.relative(repoRoot, file).replace(/\\/g, "/") || "." : null;
}

function normalizeVersion(value) {
  return String(value).replace(/^v/, "");
}

function parseBoolean(value) {
  return ["1", "true", "yes"].includes(String(value).toLowerCase());
}

main();
