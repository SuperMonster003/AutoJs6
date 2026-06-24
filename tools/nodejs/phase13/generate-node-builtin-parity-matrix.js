#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "node-builtin-parity";
const DEFAULT_DOCS_OUTPUT = "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md";
const DESKTOP_BUILTINS = Object.freeze([
  "assert",
  "assert/strict",
  "async_hooks",
  "buffer",
  "child_process",
  "cluster",
  "console",
  "constants",
  "crypto",
  "dgram",
  "diagnostics_channel",
  "dns",
  "dns/promises",
  "domain",
  "events",
  "fs",
  "fs/promises",
  "http",
  "https",
  "inspector",
  "module",
  "net",
  "node:test",
  "node:test/reporters",
  "os",
  "path",
  "path/posix",
  "path/win32",
  "perf_hooks",
  "process",
  "punycode",
  "querystring",
  "readline",
  "readline/promises",
  "repl",
  "stream",
  "stream/consumers",
  "stream/promises",
  "stream/web",
  "string_decoder",
  "timers",
  "timers/promises",
  "tls",
  "tty",
  "url",
  "util",
  "util/types",
  "v8",
  "vm",
  "wasi",
  "worker_threads",
  "zlib",
  "zlib/promises",
]);

const FILES = {
  capabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  p13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const docsOutput = path.resolve(repoRoot, options.docsOutput || DEFAULT_DOCS_OUTPUT);
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  const preliminaryMarkdown = renderMarkdown({ ...report, checks: [] });
  const checks = buildChecks(files, report, preliminaryMarkdown);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const output = {
    schema: "autojs6-node-builtin-desktop-parity-matrix-v1",
    generatedAt: new Date().toISOString(),
    gate: "generateNodeBuiltinDesktopParityMatrix",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    ...report,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      builtinsCovered: checkById(checks, "builtins-covered"),
      disabledVisible: checkById(checks, "disabled-builtins-visible"),
      networkGated: checkById(checks, "network-builtins-gated"),
    },
  };
  const markdown = renderMarkdown(output);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.mkdirSync(path.dirname(docsOutput), { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(output, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), markdown);
  fs.writeFileSync(docsOutput, markdown);
  console.log("Node builtin desktop parity matrix generation: " + output.decision.status);
  console.log("Node builtin desktop parity policy violations: " + policyViolations.length);
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
  const limited = parseKotlinStringCollection(files.capabilities.text, "limitedBuiltinModuleNames");
  const commonJs = parseKotlinStringCollection(files.capabilities.text, "commonJsBuiltinModuleNames");
  const disabled = parseKotlinStringCollection(files.capabilities.text, "disabledBuiltinModuleNames");
  const network = parseKotlinStringCollection(files.capabilities.text, "networkBuiltinModuleNames");
  const allBuiltins = Array.from(new Set([...DESKTOP_BUILTINS, ...limited, ...commonJs, ...disabled, ...network])).sort();
  const modules = allBuiltins.map((name) => classifyBuiltin(name, { limited, commonJs, disabled, network }));
  return {
    sourceSets: {
      desktopReferenceCount: DESKTOP_BUILTINS.length,
      limitedCount: limited.length,
      commonJsCount: commonJs.length,
      disabledCount: disabled.length,
      networkCount: network.length,
    },
    statusCounts: countBy(modules, (module) => module.status),
    profileCounts: countBy(modules, (module) => module.targetProfile),
    modules,
  };
}

function classifyBuiltin(name, sets) {
  const bareName = name.startsWith("node:") ? name.slice("node:".length) : name;
  const disabled = sets.disabled.includes(bareName) || sets.disabled.includes(name);
  const network = sets.network.includes(bareName) || sets.network.includes(name);
  const limited = sets.limited.includes(bareName) || sets.limited.includes(name);
  const commonJs = sets.commonJs.includes(bareName) || sets.commonJs.includes(name);
  if (disabled) {
    return {
      name,
      status: "disabled",
      targetProfile: highRiskProfileFor(bareName),
      source: sourceTags({ disabled, network, limited, commonJs }),
      nextStep: nextStepForDisabled(bareName),
    };
  }
  if (network) {
    return {
      name,
      status: "limited_network_gated",
      targetProfile: "desktop_compat_opt_in",
      source: sourceTags({ disabled, network, limited, commonJs }),
      nextStep: networkNextStepFor(bareName),
    };
  }
  if (limited) {
    return {
      name,
      status: "partial",
      targetProfile: "safe_default",
      source: sourceTags({ disabled, network, limited, commonJs }),
      nextStep: "Keep current Safe subset unless P13-43 promotes broader desktop conformance.",
    };
  }
  if (commonJs) {
    return {
      name,
      status: "known_not_wrapped",
      targetProfile: "desktop_compat_opt_in",
      source: sourceTags({ disabled, network, limited, commonJs }),
      nextStep: "P13-28+ follow-up must add a wrapper, stable denial, or android_not_applicable decision.",
    };
  }
  return {
    name,
    status: "missing_from_registry",
    targetProfile: "desktop_compat_opt_in",
    source: sourceTags({ disabled, network, limited, commonJs }),
    nextStep: "Add explicit registry entry or document android_not_applicable.",
  };
}

function networkNextStepFor(name) {
  if (name === "http" || name === "https") {
    return "P13-29 records the controlled client subset as partial; raw server/listen, proxy/tunnel, custom TLS/session control, and packaged live-network evidence remain open.";
  }
  return "P13-29/P13-30 must decide raw client/server behavior, limits, and Android permission policy.";
}

function highRiskProfileFor(name) {
  if (["inspector"].includes(name)) {
    return "debug_unsafe_lab";
  }
  if (["child_process", "vm", "wasi", "worker_threads", "dgram", "zlib/promises"].includes(name)) {
    return "desktop_compat_opt_in";
  }
  return "permanently_unsupported";
}

function nextStepForDisabled(name) {
  switch (name) {
    case "child_process":
      return "P13-40 must design a scoped runner before any opt-in promotion.";
    case "dgram":
      return "P13-31 must decide UDP client/server, multicast, broadcast, and packaged network policy.";
    case "inspector":
      return "P13-37 must keep release denied and validate debug-only ADB-forward behavior.";
    case "vm":
      return "P13-36 must design context isolation, timeout, and escape-denial tests.";
    case "wasi":
      return "P13-38 must define controlled preopens and raw WASI denial.";
    case "worker_threads":
      return "P13-34 must validate worker isolation, message budgets, crash evidence, and packaged behavior.";
    default:
      return "Keep stable denial unless a future Phase 13 task assigns an opt-in profile.";
  }
}

function sourceTags(flags) {
  const tags = [];
  if (flags.limited) tags.push("limited");
  if (flags.commonJs) tags.push("commonjs-known");
  if (flags.disabled) tags.push("disabled");
  if (flags.network) tags.push("network");
  return tags;
}

function parseKotlinStringCollection(text, name) {
  const pattern = new RegExp(`val ${name}\\s*=\\s*(?:listOf|setOf)\\(([^;]*?)\\n\\s*\\)`, "s");
  const match = text.match(pattern);
  if (!match) {
    return [];
  }
  return Array.from(match[1].matchAll(/"([^"]+)"/g)).map((item) => item[1]).sort();
}

function buildChecks(files, report, markdown) {
  const moduleNames = report.modules.map((module) => module.name);
  return [
    check(
      "builtins-covered",
      DESKTOP_BUILTINS.every((name) => moduleNames.includes(name)) && report.modules.length >= DESKTOP_BUILTINS.length,
      "Desktop reference builtins must all appear in the generated parity matrix.",
    ),
    check(
      "disabled-builtins-visible",
      ["child_process", "dgram", "inspector", "vm", "wasi", "worker_threads"].every((name) =>
        report.modules.some((module) => module.name === name && module.status === "disabled")),
      "High-risk disabled builtins must stay visible in the matrix.",
    ),
    check(
      "network-builtins-gated",
      ["dns", "dns/promises", "http", "https", "net", "tls"].every((name) =>
        report.modules.some((module) => module.name === name && module.status === "limited_network_gated")),
      "Network-shaped builtins must be marked limited_network_gated until P13 network promotion tasks land.",
    ),
    check(
      "docs-mention-builtins",
      files.compatibilityProfile.text.includes("Supported and Limited Builtins") &&
        files.compatibilityProfile.text.includes("Registry-disabled builtin module names"),
      "Compatibility profile must keep the existing builtin policy documentation.",
    ),
    check(
      "task-checklist-current",
      files.p13Tasks.text.includes("- [x] P13-28: 建立 Node builtin desktop parity matrix。") &&
        files.p13Tasks.text.includes("generateNodeBuiltinDesktopParityMatrix"),
      "Phase 13 task checklist must record P13-28 evidence.",
    ),
    check(
      "markdown-current",
      markdown.includes("# Node Builtin Desktop Parity Matrix") &&
        markdown.includes("`limited_network_gated`") &&
        markdown.includes("`missing_from_registry`"),
      "Generated markdown must expose status vocabulary and desktop gap state.",
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

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Node Builtin Desktop Parity Matrix",
    "",
    "This matrix is generated from `NodeRuntimeCapabilities.kt` and the Phase 13 desktop parity task plan. It tracks current Safe Node builtin behavior against broader desktop Node.js compatibility goals.",
    "",
    "## Status Vocabulary",
    "",
    "- `partial`: available as a Safe Node subset or wrapper.",
    "- `limited_network_gated`: present for controlled feature detection or facade behavior, but raw network I/O remains gated.",
    "- `disabled`: registry-disabled with stable denial.",
    "- `known_not_wrapped`: known to the CommonJS registry but not currently surfaced as a limited wrapper.",
    "- `missing_from_registry`: in the desktop reference list but not explicitly classified by the current registry.",
    "",
    "## Summary",
    "",
  ];
  Object.keys(report.statusCounts).sort().forEach((status) => {
    lines.push(`- \`${status}\`: ${report.statusCounts[status]}`);
  });
  lines.push("", "## Matrix", "", "| Builtin | Status | Target profile | Source | Next step |", "| --- | --- | --- | --- | --- |");
  report.modules.forEach((module) => {
    lines.push(
      `| \`${module.name}\` | \`${module.status}\` | \`${module.targetProfile}\` | ${module.source.map((tag) => `\`${tag}\``).join(", ") || "-"} | ${escapeTable(module.nextStep)} |`,
    );
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  (report.checks || []).forEach((checkItem) => {
    lines.push(`| \`${checkItem.id}\` | ${checkItem.passed ? "passed" : "failed"} | ${escapeTable(checkItem.message)} |`);
  });
  return lines.join("\n") + "\n";
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
      console.log("Usage: node generate-node-builtin-parity-matrix.js [--repo-root dir] [--report-dir dir] [--docs-output file]");
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
