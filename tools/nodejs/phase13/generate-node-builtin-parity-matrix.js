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
  if (bareName === "child_process" && disabled && commonJs) {
    return {
      name,
      status: "experimental",
      targetProfile: "desktop_compat_opt_in",
      source: ["commonjs-known", "native opt-in"],
      nextStep: nextStepForExperimental(bareName),
    };
  }
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
    const nativeVm = bareName === "vm";
    return {
      name,
      status: nativeVm ? "partial_native" : "partial",
      targetProfile: nativeVm ? highRiskProfileFor(bareName) : "safe_default",
      source: sourceTags({ disabled, network, limited, commonJs }),
      nextStep: nextStepForLimited(bareName),
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
    return "P13-29 records the controlled client subset as ready; raw server/listen, proxy/tunnel, custom TLS/session control, and broader packaged install/run network evidence remain future work.";
  }
  if (name === "net" || name === "tls" || name === "dns" || name === "dns/promises") {
    return "P13-30 records staged limited facades as partial and a release-forbidden raw network module opt-in; release/default client sockets, server sockets, network DNS policy, TLS sessions, fd leak proof, and packaged live-network evidence remain open.";
  }
  return "P13-29/P13-30 must decide raw client/server behavior, limits, and Android permission policy.";
}

function nextStepForLimited(name) {
  if (name === "vm") {
    return "P13-36 promotes vm/node:vm to the native Node builtin for desktop npm compatibility; profile diagnostics, shadow-package protection, repeated-script budget, timeout, boundary-probe, and packaged fixture evidence are ready while VM remains a non-sandbox compatibility surface.";
  }
  if (name === "fs" || name === "fs/promises") {
    return "P13-32 records scoped filesystem profile and P13-33 records advanced fs APIs as partial; Safe profile stays working-directory scoped while Android watcher coalescing, recursive-watch denial, and realpath differences are documented; expanded roots, packaged watcher/fd/FileHandle close-on-destroy, and full desktop fs semantics remain future work.";
  }
  if (name === "process") {
    return "P13-44 records process/env/argv parity as partial; argv/env/cwd/timing/resource/report/permission and lifecycle-mapped process.exit(code) evidence is present with stable denials for native process exit, signals, native bindings, raw environment inheritance, and full desktop process parity while packaged lifecycle evidence remains future work.";
  }
  if ([
    "async_hooks",
    "buffer",
    "crypto",
    "os",
    "perf_hooks",
    "readline",
    "readline/promises",
    "stream",
    "stream/consumers",
    "stream/promises",
    "stream/web",
    "tty",
    "url",
    "zlib",
  ].includes(name)) {
    return "P13-43 records stdlib conformance as partial; representative Safe subset coverage, Android difference notes, builtin precedence, and npm/modern-core evidence are present while full desktop parity remains future work.";
  }
  return "Keep current Safe subset unless a future Phase 13 task promotes broader desktop conformance.";
}

function nextStepForExperimental(name) {
  if (name === "child_process") {
    return "P13-40 records child_process scoped-runner diagnostics as partial; Safe/default keeps child_process denied, while debug builds can enable Node's native builtin with `autojs.nodejs.child_process.experimental=true`. App-private allowlisted binaries, bounded stdio, timeout/cancel/kill cleanup, packaged evidence, and hostile corpus coverage remain future work before release/profile promotion.";
  }
  return "Keep experimental builtin opt-ins debug-only until the corresponding Phase 13 promotion gate closes.";
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
      return "P13-40 records child_process scoped-runner diagnostics as partial; Safe/default keeps child_process denied while app-private allowlisted binaries, bounded stdio, timeout/cancel/kill cleanup, packaged evidence, and hostile corpus coverage remain future work.";
    case "dgram":
      return "P13-31 records UDP/dgram as stable-denied for v1.1; unicast client, server bind/listen, multicast, broadcast, packaged INTERNET policy, and fd leak proof remain future work.";
    case "inspector":
      return "P13-37 records inspector debug-profile diagnostics as partial; Safe/default and release keep inspector denied while explicit debug-session UI, loopback ADB forwarding, DevTools attach, profiler artifact redaction, and packaged behavior evidence remain future work.";
    case "vm":
      return "P13-36 records vm module diagnostics as ready for the partial native compatibility surface; Safe/default still treats VM as non-sandboxed compatibility and does not grant extra bridge, process, filesystem, or private binding authority.";
    case "wasi":
      return "P13-38 records controlled WASI diagnostics as partial; pure WebAssembly is the supported v1.1 runtime path while raw and controlled WASI APIs stay stable-denied, with controlled preopens, fd/env/args, resource budgets, worker integration, and packaged behavior remaining future work.";
    case "worker_threads":
      return "P13-34 records worker_threads opt-in diagnostics as partial; default denial, profile diagnostics, and native preflight are covered while native runnable evidence, crash/resource stress, packaged worker smoke, and final Desktop worker policy remain open.";
    case "zlib/promises":
      return "P13-43 records zlib/promises as a stable disabled gap while callback, sync, and stream zlib subsets stay covered.";
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
      ["dgram", "inspector", "wasi", "worker_threads"].every((name) =>
        report.modules.some((module) => module.name === name && module.status === "disabled")),
      "High-risk disabled builtins must stay visible in the matrix.",
    ),
    check(
      "experimental-builtins-visible",
      report.modules.some((module) =>
        module.name === "child_process" &&
          module.status === "experimental" &&
          module.targetProfile === "desktop_compat_opt_in" &&
          module.nextStep.includes("autojs.nodejs.child_process.experimental=true")),
      "Release-forbidden experimental builtins must stay visible with their opt-in property.",
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
    "- `experimental`: available only behind a release-forbidden build-time/debug opt-in.",
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
