#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-capability-truth";

const FILES = {
  manifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  capabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  profileDocs: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  p13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  const checks = buildChecks(files, report);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const output = {
    schema: "autojs6-node-phase13-capability-truth-v1",
    generatedAt: new Date().toISOString(),
    gate: "generateNodePhase13CapabilityTruth",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    ...report,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      featureFlagsParsed: checkById(checks, "feature-flags-parsed"),
      profilesCovered: checkById(checks, "profiles-covered"),
      highRiskDeniedByDefault: checkById(checks, "high-risk-denied-by-default"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(output, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(output));
  console.log("Phase 13 capability truth generation: " + output.decision.status);
  console.log("Phase 13 capability truth policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const manifest = JSON.parse(files.manifest.text);
  const featureFlags = parseFeatureFlags(files.capabilities.text);
  const surfaces = manifest.surfaces || [];
  const profiles = manifest.profileVocabulary.map((profile) => {
    const ownedSurfaces = surfaces.filter((surface) => (surface.profiles || []).includes(profile));
    return {
      id: profile,
      surfaceCount: ownedSurfaces.length,
      statusCounts: countBy(ownedSurfaces, (surface) => surface.status),
      riskCounts: countBy(ownedSurfaces, (surface) => surface.risk),
      surfaces: ownedSurfaces.map((surface) => ({
        id: surface.id,
        status: surface.status,
        risk: surface.risk,
        tasks: surface.tasks,
        summary: surface.summary,
      })),
    };
  });
  return {
    phase: manifest.phase,
    runtimeIdentity: parseRuntimeIdentity(files.capabilities.text),
    featureFlags,
    featureFlagCounts: {
      total: featureFlags.length,
      enabledByDefault: featureFlags.filter((flag) => flag.defaultEnabled).length,
      disabledByDefault: featureFlags.filter((flag) => !flag.defaultEnabled).length,
      byStatus: countBy(featureFlags, (flag) => flag.status),
      bySecurity: countBy(featureFlags, (flag) => flag.securityLevel),
    },
    profiles,
  };
}

function parseRuntimeIdentity(text) {
  return {
    profileName: findConst(text, "PROFILE_NAME"),
    engineVersion: findConst(text, "ENGINE_VERSION"),
    nodeHeaderVersion: findConst(text, "NODE_HEADER_VERSION"),
    nodeVersionExpectedPrefix: findConst(text, "NODE_VERSION_EXPECTED_PREFIX"),
  };
}

function findConst(text, name) {
  const match = text.match(new RegExp(`const val ${name}\\s*=\\s*"([^"]+)"`));
  return match ? match[1] : null;
}

function parseFeatureFlags(text) {
  const flags = [];
  const pattern = /NodeRuntimeFeatureFlag\(\s*id\s*=\s*"([^"]+)",\s*diagnosticName\s*=\s*"([^"]+)",\s*gradleProperty\s*=\s*([^,\n]+),\s*defaultEnabled\s*=\s*([^,\n]+),\s*buildTimeOverrideAllowed\s*=\s*(true|false),\s*projectJsonOverrideAllowed\s*=\s*(true|false),\s*packagedApkOverrideAllowed\s*=\s*(true|false),\s*securityLevel\s*=\s*"([^"]+)",\s*status\s*=\s*"([^"]+)"/g;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    flags.push({
      id: match[1],
      diagnosticName: match[2],
      gradleProperty: parseGradleProperty(match[3].trim(), text),
      defaultEnabled: parseBooleanExpression(match[4].trim(), text),
      buildTimeOverrideAllowed: match[5] === "true",
      projectJsonOverrideAllowed: match[6] === "true",
      packagedApkOverrideAllowed: match[7] === "true",
      securityLevel: match[8],
      status: match[9],
      releaseForbidden: match[9] === "experimental" || ["high", "critical"].includes(match[8]),
    });
  }
  return flags;
}

function parseGradleProperty(value, text) {
  if (value === "null") {
    return null;
  }
  const direct = value.match(/^"([^"]+)"$/);
  if (direct) {
    return direct[1];
  }
  const constName = value.match(/^([A-Z0-9_]+)$/);
  if (constName) {
    const match = text.match(new RegExp(`const val ${constName[1]}\\s*=\\s*"([^"]+)"`));
    return match ? match[1] : constName[1];
  }
  return value;
}

function parseBooleanExpression(value, text) {
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  const constName = value.match(/^([A-Z0-9_]+)$/);
  if (constName) {
    const match = text.match(new RegExp(`const val ${constName[1]}\\s*=\\s*(true|false)`));
    if (match) {
      return match[1] === "true";
    }
  }
  return null;
}

function buildChecks(files, report) {
  const profileIds = report.profiles.map((profile) => profile.id);
  const documentedDefaultOnHighRisk = new Set(["vm"]);
  const riskyEnabledByDefault = report.featureFlags.filter((flag) =>
    flag.defaultEnabled &&
      ["high", "critical"].includes(flag.securityLevel) &&
      !documentedDefaultOnHighRisk.has(flag.id));
  const requiredProfiles = [
    "safe_default",
    "pro_compat_opt_in",
    "desktop_compat_opt_in",
    "debug_unsafe_lab",
    "permanently_unsupported",
  ];
  return [
    check(
      "runtime-identity",
      report.runtimeIdentity.profileName === "AutoJs6 Safe Node Profile" &&
        report.runtimeIdentity.nodeHeaderVersion === "24.5.0",
      "Capability truth must capture the current Safe Node runtime identity.",
    ),
    check(
      "feature-flags-parsed",
      report.featureFlags.length >= 13 &&
        report.featureFlags.some((flag) => flag.id === "network") &&
        report.featureFlags.some((flag) => flag.id === "worker_threads") &&
        report.featureFlags.some((flag) => flag.id === "java_interop") &&
        report.featureFlags.some((flag) => flag.id === "child_process"),
      "Capability truth must parse current NodeRuntimeCapabilities feature flags.",
    ),
    check(
      "profiles-covered",
      requiredProfiles.every((profile) => profileIds.includes(profile)),
      "Capability truth must cover every Phase 13 profile vocabulary entry.",
    ),
    check(
      "high-risk-denied-by-default",
      riskyEnabledByDefault.length === 0,
      "High and critical security feature flags must remain disabled by default unless a documented promotion gate explicitly defaults them on.",
    ),
    check(
      "profile-docs-current",
      includesAll(files.profileDocs.text, [
        "Phase 13 Profile Model",
        "safe_default",
        "pro_compat_opt_in",
        "desktop_compat_opt_in",
        "debug_unsafe_lab",
      ]),
      "Compatibility profile docs must describe the Phase 13 profile model.",
    ),
    check(
      "task-checklist-current",
      files.p13Tasks.text.includes("- [x] P13-10: 发布 Phase 13 capability truth report") &&
        files.p13Tasks.text.includes("generateNodePhase13CapabilityTruth"),
      "Phase 13 task checklist must record P13-10 evidence.",
    ),
  ];
}

function countBy(values, selector) {
  const output = {};
  values.forEach((value) => {
    const key = selector(value);
    output[key] = (output[key] || 0) + 1;
  });
  return output;
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Capability Truth",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Phase: ${report.phase}`,
    `- Runtime profile: \`${report.runtimeIdentity.profileName}\``,
    `- Engine version: \`${report.runtimeIdentity.engineVersion}\``,
    `- Node baseline: \`${report.runtimeIdentity.nodeVersionExpectedPrefix}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Feature Flags",
    "",
    `- Total: ${report.featureFlagCounts.total}`,
    `- Enabled by default: ${report.featureFlagCounts.enabledByDefault}`,
    `- Disabled by default: ${report.featureFlagCounts.disabledByDefault}`,
    "",
    "| Flag | Default | Security | Status | Build | Project | Packaged | Gradle property |",
    "| --- | --- | --- | --- | --- | --- | --- | --- |",
  ];
  report.featureFlags.forEach((flag) => {
    lines.push(
      `| \`${flag.id}\` | ${flag.defaultEnabled} | \`${flag.securityLevel}\` | \`${flag.status}\` | ${flag.buildTimeOverrideAllowed} | ${flag.projectJsonOverrideAllowed} | ${flag.packagedApkOverrideAllowed} | ${flag.gradleProperty ? `\`${flag.gradleProperty}\`` : "-"} |`,
    );
  });
  lines.push("", "## Profiles", "");
  report.profiles.forEach((profile) => {
    lines.push(`### ${profile.id}`, "");
    lines.push(`- Surfaces: ${profile.surfaceCount}`);
    lines.push(`- Status counts: ${formatCounts(profile.statusCounts)}`);
    lines.push(`- Risk counts: ${formatCounts(profile.riskCounts)}`);
    lines.push("");
    lines.push("| Surface | Status | Risk | Tasks |");
    lines.push("| --- | --- | --- | --- |");
    profile.surfaces.forEach((surface) => {
      lines.push(`| \`${surface.id}\` | \`${surface.status}\` | \`${surface.risk}\` | ${surface.tasks.map((task) => `\`${task}\``).join(", ")} |`);
    });
    lines.push("");
  });
  lines.push("## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function formatCounts(counts) {
  const keys = Object.keys(counts).sort();
  if (keys.length === 0) {
    return "-";
  }
  return keys.map((key) => `\`${key}\`=${counts[key]}`).join(", ");
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node generate-node-phase13-capability-truth.js [--repo-root dir] [--report-dir dir]");
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
