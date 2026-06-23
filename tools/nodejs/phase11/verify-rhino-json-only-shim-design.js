#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-json-only-shim-design-verification";

const FILES = {
  design: "docs/nodejs/design/RHINO_JSON_ONLY_SHIM.md",
  shimDesign: "docs/nodejs/design/RHINO_SHIM.md",
  loaderDesign: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  migration: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  integrationStatus: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
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
    schema: "autojs6-node-rhino-json-only-shim-design-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoJsonOnlyShimDesign",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      explicitRunShapeDocumented: checkById(checks, "design-api-shape"),
      jsonOnlyBoundaryDocumented: checkById(checks, "design-json-only-boundary"),
      noRuntimeRhinoModule: checkById(checks, "no-runtime-rhino-module"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino JSON-only shim design verification: " + report.decision.status);
  console.log("Rhino JSON-only shim: design-only explicit rhino.run gate");
  console.log("Rhino JSON-only shim policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  return [
    check(
      "design-core-decision",
      includesAll(files.design.text, [
        "S11-26",
        "No runtime module is enabled",
        "const rhino = require(\"rhino\")",
        "rhino.run",
        "Current Node Profile v1.1 behavior remains unchanged",
        "no `require(\"rhino\")`",
        "no `rhino.run()`",
        "no `rhino.install()`",
        "no `compat.rhino`",
        "no Rhino globals installed into V8",
      ]),
      "S11-26 design must be explicit that rhino.run is only a future JSON-only candidate and current runtime stays disabled",
    ),
    check(
      "design-api-shape",
      includesAll(files.design.text, [
        "type JsonValue",
        "interface RhinoRunRequest",
        "source?: string",
        "path?: string",
        "args?: JsonValue",
        "timeoutMs: number",
        "interface RhinoRunResult",
        "schema: \"autojs6-rhino-run-result-v1\"",
        "Exactly one of `source` or `path`",
        "`timeoutMs` is required",
      ]),
      "S11-26 design must define the explicit RhinoRunRequest/RhinoRunResult shape with required timeout and source/path exclusivity",
    ),
    check(
      "design-json-only-boundary",
      includesAll(files.design.text, [
        "JSON-safe snapshot",
        "`undefined`",
        "functions",
        "symbols",
        "`bigint`",
        "`Date`, `RegExp`, `Map`, `Set`",
        "`ArrayBuffer`, typed arrays, `Buffer`",
        "image handles",
        "UI handles",
        "accessibility node handles",
        "Java objects",
        "Rhino `Scriptable`",
        "Android objects",
        "cycles",
        "excessive depth",
        "oversized serialized payloads",
        "Non-JSON return values fail",
      ]),
      "S11-26 design must define JSON-safe input/output and reject functions, handles, raw objects, cycles, and oversized payloads",
    ),
    check(
      "design-error-shape",
      includesAll(files.design.text, [
        "ERR_AUTOJS6_RHINO_SHIM_DISABLED",
        "ERR_AUTOJS6_RHINO_SHIM_INVALID_REQUEST",
        "ERR_AUTOJS6_RHINO_SHIM_TIMEOUT",
        "ERR_AUTOJS6_RHINO_SHIM_CANCELLED",
        "ERR_AUTOJS6_RHINO_SHIM_JSON_DENIED",
        "ERR_AUTOJS6_RHINO_SHIM_JAVA_OBJECT_DENIED",
        "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED",
        "Errors visible to Node must be JSON-safe objects",
      ]),
      "S11-26 design must reserve stable JSON-safe Rhino shim error shapes",
    ),
    check(
      "design-engine-boundary",
      includesAll(files.design.text, [
        "must never execute Rhino inside the V8 isolate",
        "script manager owns engine creation",
        "dedicated compatibility process",
        "Node submits a JSON request envelope",
        "execution id",
        "timeout",
        "Parent Node stop",
        "service destroy",
        "cancel the Rhino run",
        "The Node side receives only JSON snapshots",
        "No Java object",
        "file descriptor crosses into Node",
      ]),
      "S11-26 design must define the script-manager/process boundary and cancellation/resource cleanup rules",
    ),
    check(
      "design-packaging-policy",
      includesAll(files.design.text, [
        "Permission And Packaging Policy",
        "must not grant new authority",
        "packaged APKs must opt in through explicit metadata",
        "missing metadata fails closed",
        "no Android manifest entry",
        "source text contains `require(\"rhino\")`",
        "package diagnostics",
      ]),
      "S11-26 design must require explicit packaged metadata and fail-closed package behavior",
    ),
    check(
      "design-resolution-policy",
      includesAll(files.design.text, [
        "Module Resolution Policy",
        "`require(\"rhino\")` remains unavailable",
        "controlled AutoJs module",
        "`node_modules/rhino` package cannot shadow it",
        "There is no `node:rhino` alias",
      ]),
      "S11-26 design must document current unavailability and future controlled-module/shadowing policy",
    ),
    check(
      "design-poc-gate-plan",
      includesAll(files.design.text, [
        "## POC Gate Plan",
        "default Safe Node Profile still denies `require(\"rhino\")`",
        "`require(\"node:rhino\")` is unavailable",
        "local `node_modules/rhino` shadowing behavior",
        "`rhino.install()` rejects",
        "does not mutate `globalThis`",
        "remain unavailable in V8",
        "accepts only JSON-safe requests",
        "success returns a JSON-safe `RhinoRunResult`",
        "timeouts cancel Rhino execution",
        "packaged APKs without explicit shim metadata fail closed",
        "diagnostics record mixed-engine runs",
      ]),
      "S11-26 design must include a concrete future POC gate plan",
    ),
    check(
      "legacy-shim-doc-s11-26",
      includesAll(files.shimDesign.text, [
        "S11-26",
        "RHINO_JSON_ONLY_SHIM.md",
        "explicit and JSON-only",
        "rhino.run",
        "current profile still does not expose `require(\"rhino\")`",
        "`rhino.install()`",
        "`compat.rhino`",
        "JSON-only request validation",
      ]),
      "RHINO_SHIM must point to the S11-26 JSON-only shim design and keep current runtime disabled",
    ),
    check(
      "migration-guide-s11-26",
      includesAll(files.migration.text, [
        "S11-26",
        "rhino.run({ source, timeoutMs, args })",
        "RHINO_JSON_ONLY_SHIM.md",
        "not a current runtime feature",
        "const rhino = require(\"rhino\"); // not available",
        "must accept and return only JSON-safe values",
        "must not install Rhino globals into V8",
        "must reject Java objects",
      ]),
      "migration guide must document S11-26 as a future JSON-only shim plan, not a current runtime feature",
    ),
    check(
      "security-model-s11-26",
      includesAll(files.securityModel.text, [
        "S11-26",
        "rhino.run({ source, timeoutMs, args })",
        "current Safe Node Profile still has no `require(\"rhino\")`",
        "`rhino.install()`",
        "`compat.rhino`",
        "execute outside the V8 isolate",
        "accept and return only JSON-safe snapshots",
        "reject Java objects",
        "Rhino `Scriptable`",
        "explicit packaged metadata",
        "RHINO_JSON_ONLY_SHIM.md",
      ]),
      "security model must record S11-26 JSON-only shim boundary and no current runtime exposure",
    ),
    check(
      "loader-design-s11-26",
      includesAll(files.loaderDesign.text, [
        "S11-26",
        "explicit Rhino JSON-only shim design",
        "without adding `require(\"rhino\")` or `rhino.run()`",
        "does not add `require(\"rhino\")`",
        "`rhino.install()`",
        "`compat.rhino`",
        "no `rhino`, `rhino.run`, `rhino.install`, or `compat.rhino` type surface",
      ]),
      "compat loader design must record S11-26 as design-only with no runtime/type Rhino module",
    ),
    check(
      "comparison-source-s11-26",
      includesAll(files.integrationStatus.text, [
        "const rhino = require(\"rhino\")",
        "const result = await rhino.run",
        "timeoutMs: 5000",
        "只返回 JSON",
        "不在 V8 中安装 Rhino globals",
        "packaged APK 需要显式 metadata opt-in",
      ]),
      "Pro 9 comparison status must still contain the original explicit JSON-only shim recommendation that S11-26 formalizes",
    ),
    check(
      "no-runtime-rhino-module",
      !files.runtime.text.includes("function __autojs6_limited_rhino()") &&
        !files.runtime.text.includes("function __autojs6_rhino_json_only_shim") &&
        !files.runtime.text.includes("name === \"rhino\"") &&
        !files.runtime.text.includes("name === \"node:rhino\"") &&
        !files.runtime.text.includes("compat.rhino") &&
        !files.runtime.text.includes("rhino.run") &&
        !files.runtime.text.includes("rhino.install"),
      "S11-26 must not add runtime require(\"rhino\"), rhino.run, rhino.install, or compat.rhino",
    ),
    check(
      "no-type-rhino-module",
      !files.compatTypes.text.includes("declare module \"rhino\"") &&
        !files.indexTypes.text.includes("declare module \"rhino\"") &&
        !files.compatTypes.text.includes("rhino.run") &&
        !files.indexTypes.text.includes("rhino.run") &&
        !files.compatTypes.text.includes("readonly rhino") &&
        !files.compatTypes.text.includes("compat.rhino") &&
        !files.indexTypes.text.includes("compat.rhino"),
      "S11-26 must not add TypeScript declarations for rhino, rhino.run, rhino.install, or compat.rhino",
    ),
    check(
      "task-plan-s11-26-complete",
      /\[x\]\s+S11-26/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("RHINO_JSON_ONLY_SHIM.md") &&
        files.taskPlan.text.includes("verifyNodeRhinoJsonOnlyShimDesign"),
      "Phase 11 task plan must mark S11-26 complete and name the design gate",
    ),
    check(
      "gradle-task",
      files.gradle.text.includes("verifyNodeRhinoJsonOnlyShimDesign") &&
        files.gradle.text.includes("verify-rhino-json-only-shim-design.js") &&
        files.gradle.text.includes("RHINO_JSON_ONLY_SHIM.md") &&
        files.gradle.text.includes("RHINO_SHIM.md"),
      "Gradle must register verifyNodeRhinoJsonOnlyShimDesign with shim design docs as inputs",
    ),
  ];
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino JSON-Only Shim Design Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
    "| Check | Result | Message |",
    "| --- | --- | --- |",
  ];
  report.checks.forEach((check) => {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${escapeTable(check.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-json-only-shim-design.js [--repo-root dir] [--report-dir dir]");
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
