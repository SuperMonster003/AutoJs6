#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-loader-verification";
const ENTRYPOINT = "autojs6:compat";
const ERROR_CODES = [
  "ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION",
  "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED",
  "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN",
];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  errorRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeErrorCodes.kt",
  typeIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeDeclaration: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  design: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const checks = buildChecks(files);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-loader-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatLoader",
    entrypoint: ENTRYPOINT,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      explicitOptIn: checkById(checks, "runtime-status-explicit-opt-in"),
      defaultGlobalInjectionDenied: checkById(checks, "runtime-stub-no-global-writes"),
      bareRhinoCompatAliasDenied: checkById(checks, "runtime-no-bare-rhino-compat-alias"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat loader verification: " + report.decision.status);
  console.log("Rhino compat loader entrypoint: " + ENTRYPOINT);
  console.log("Rhino compat loader policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const runtime = files.runtime.text;
  const compatBlock = extractCompatBlock(runtime);
  const checks = [
    check("runtime-resolve-entrypoint", runtime.includes(`name === "${ENTRYPOINT}"`) && runtime.includes(`return "${ENTRYPOINT}";`), "runtime require.resolve must resolve autojs6:compat"),
    check("runtime-require-entrypoint", runtime.includes(`return __autojs6_limited_rhino_compat();`), "runtime require must return the Rhino compat loader stub"),
    check("runtime-stub-function", runtime.includes("function __autojs6_limited_rhino_compat()"), "runtime must define __autojs6_limited_rhino_compat"),
    check("runtime-status-explicit-opt-in", compatBlock.includes('mode: "explicit-opt-in"') && compatBlock.includes("defaultGlobalInjection: false"), "runtime status must declare explicit opt-in and no default global injection"),
    check("runtime-install-noop-stub", compatBlock.includes("installed: false") && compatBlock.includes("S11-08 installs the explicit loader stub only"), "S11-08 install() must remain a non-installing stub"),
    check("runtime-stub-no-global-writes", !/__autojs6_define_global\s*\(|globalThis\s*\.|globalThis\s*\[|global\s*\[/.test(compatBlock), "compat loader stub must not write globals during require"),
    check("runtime-no-bare-rhino-compat-alias", !runtime.includes('name === "rhino-compat"') && !runtime.includes("name === 'rhino-compat'"), "runtime must not register a bare rhino-compat alias"),
    check("types-declare-entrypoint", files.typeDeclaration.text.includes(`declare module "${ENTRYPOINT}"`), "Type declarations must declare autojs6:compat"),
    check("types-index-reference", files.typeIndex.text.includes("./rhino_compat.d.ts"), "index.d.ts must reference rhino_compat.d.ts"),
    check("types-readme-entrypoint", files.typeReadme.text.includes(ENTRYPOINT), "Type README must document autojs6:compat"),
    check("design-entrypoint", files.design.text.includes(`require("${ENTRYPOINT}")`), "design note must document the selected require entrypoint"),
    check("design-no-default-global", files.design.text.includes("does not mutate `globalThis`") || files.design.text.includes("must not mutate `globalThis`"), "design note must document no default global mutation"),
    check("task-plan-s11-08-complete", /\[x\]\s+S11-08/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-08 complete"),
  ];
  ERROR_CODES.forEach((code) => {
    checks.push(check(`runtime-error-code-${code}`, runtime.includes(code), `runtime must expose ${code}`));
    checks.push(check(`registry-error-code-${code}`, files.errorRegistry.text.includes(code), `NodeRuntimeErrorCodes must register ${code}`));
    checks.push(check(`catalog-error-code-${code}`, files.errorCatalog.text.includes(`## ${code}`), `ERROR_CATALOG.md must document ${code}`));
    checks.push(check(`types-error-code-${code}`, files.typeDeclaration.text.includes(code), `Type declaration must expose ${code}`));
    checks.push(check(`design-error-code-${code}`, files.design.text.includes(code), `design note must describe ${code}`));
  });
  return checks;
}

function extractCompatBlock(runtime) {
  const start = runtime.indexOf("const __autojs6_rhino_compat_error_codes");
  const end = runtime.indexOf("  function __autojs6_limited_work_manager()", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function check(id, passed, message) {
  return { id, passed: passed === true, message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Loader Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Entrypoint: \`${report.entrypoint}\``,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Explicit opt-in: ${report.decision.explicitOptIn}`,
    `- Default global injection denied: ${report.decision.defaultGlobalInjectionDenied}`,
    `- Bare rhino-compat alias denied: ${report.decision.bareRhinoCompatAliasDenied}`,
    "",
    "## Checks",
    "",
    "| Check | Result |",
    "| --- | --- |",
  ];
  report.checks.forEach((checkItem) => {
    lines.push(`| ${checkItem.id} | ${checkItem.passed ? "pass" : "fail"} |`);
  });
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-loader.js [--repo-root dir] [--report-dir dir]");
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
