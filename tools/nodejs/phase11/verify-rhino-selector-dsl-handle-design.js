#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-selector-dsl-handle-design-verification";

const REQUIRED_BRIDGE_METHODS = [
  "findOne",
  "findAll",
  "click",
  "longClick",
  "setText",
  "scrollForward",
  "scrollBackward",
  "back",
  "home",
  "recentApps",
];

const REQUIRED_BUILDERS = [
  "text",
  "desc",
  "id",
  "className",
  "textContains",
  "descContains",
  "textMatches",
  "descMatches",
  "idMatches",
  "classNameMatches",
  "clickable",
  "enabled",
  "depth",
  "boundsInside",
  "boundsContains",
];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  bridgeProtocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  design: "docs/nodejs/design/RHINO_SELECTOR_DSL_HANDLE.md",
  typesDraft: "docs/nodejs/reports/phase11/RHINO_SELECTOR_DSL_TYPES_DRAFT.md",
  bridgeGaps: "docs/nodejs/reports/phase11/RHINO_SELECTOR_DSL_BRIDGE_GAPS.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
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
    schema: "autojs6-node-rhino-selector-dsl-handle-design-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoSelectorDslHandleDesign",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    requiredBridgeMethods: REQUIRED_BRIDGE_METHODS,
    requiredBuilders: REQUIRED_BUILDERS,
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      designOnly: checkById(checks, "design-status-design-only"),
      asyncBoundarySpecified: checkById(checks, "design-async-boundary"),
      bridgeMethodsCovered: REQUIRED_BRIDGE_METHODS.every((method) => checkById(checks, `bridge-method-${method}`)),
      typesDraftPresent: checkById(checks, "types-draft-handle"),
      gapListPresent: checkById(checks, "gap-list-no-new-bridge"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino selector DSL handle design verification: " + report.decision.status);
  console.log("Rhino selector DSL required bridge methods: " + REQUIRED_BRIDGE_METHODS.join(", "));
  console.log("Rhino selector DSL policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const manifest = JSON.parse(files.statusManifest.text);
  const selectorModule = (manifest.modules || []).find((module) => module.id === "selector");
  const automatorModule = (manifest.modules || []).find((module) => module.id === "automator");
  const checks = [
    check("design-status-design-only", files.design.text.includes("Status: design gate only") && files.design.text.includes("S11-15 does not enable new runtime behavior"), "selector handle design must be explicitly design-only"),
    check("design-async-boundary", files.design.text.includes("synchronous JavaScript handle") && files.design.text.includes("Promise-returning methods") && files.design.text.includes("must be awaited"), "design must define sync builder and async execution boundary"),
    check("design-no-raw-node", ["AccessibilityNodeInfo", "UiObject", "UiSelector", "Java object"].every((term) => files.design.text.includes(term)), "design must deny raw Android/Rhino/Java object leakage"),
    check("design-timeout-overload", files.design.text.includes("findOne(timeout)") && files.design.text.includes("{ timeoutMs: timeout }"), "design must specify Rhino numeric timeout normalization"),
    check("design-s11-16-notes", files.design.text.includes("Required JavaScript work") && files.design.text.includes("route `find()` to `accessibility.findAll()`"), "design must provide S11-16 implementation notes"),
    check("types-draft-handle", files.typesDraft.text.includes("interface RhinoSelectorHandle") && files.typesDraft.text.includes('readonly type: "autojs6.rhino.selector.handle"') && files.typesDraft.text.includes("readonly descriptor: accessibility.SelectorDescriptor"), "types draft must define the Rhino selector handle shape"),
    check("types-draft-builders", REQUIRED_BUILDERS.every((name) => files.typesDraft.text.includes(`${name}(`)), "types draft must cover required selector builder and chain methods"),
    check("types-draft-promises", ["findOne", "find(", "findAll", "click", "longClick", "setText", "scrollForward", "scrollBackward", "exists"].every((name) => files.typesDraft.text.includes(`${name}`)) && files.typesDraft.text.includes("Promise<"), "types draft must declare Promise-returning execution methods"),
    check("types-draft-not-active", !files.typesDraft.relative.endsWith(".d.ts") && !files.typesDraft.text.includes("declare module \"selector\""), "types draft must remain non-active until S11-16 implements runtime"),
    check("gap-list-no-new-bridge", files.bridgeGaps.text.includes("No new bridge method") && files.bridgeGaps.text.includes("S11-16 MVP"), "bridge gap list must state whether S11-16 needs new bridge methods"),
    check("gap-list-deferred-raw", files.bridgeGaps.text.includes("pickup()") && files.bridgeGaps.text.includes("detect()") && files.bridgeGaps.text.includes("live node handles"), "bridge gap list must defer raw UiObject/live-handle semantics"),
    check("runtime-selector-descriptor", files.runtime.text.includes('const __autojs6_accessibility_selector_type = "autojs6.accessibility.selector"') && files.runtime.text.includes("Object.freeze(selector)") && files.runtime.text.includes("findAll"), "runtime evidence must show existing frozen selector descriptors and findAll path"),
    check("types-accessibility-descriptor", files.accessibilityTypes.text.includes("interface SelectorDescriptor") && files.accessibilityTypes.text.includes("readonly type: \"autojs6.accessibility.selector\"") && files.accessibilityTypes.text.includes("findOne(selector: SelectorDescriptor"), "accessibility declarations must expose descriptor and query/action methods"),
    check("status-selector-partial", selectorModule && selectorModule.status === "partial" && selectorModule.apiEntries.some((entry) => entry.id === "selector.pickup" && entry.status === "partial"), "status manifest must keep selector as partial pending facade implementation"),
    check("status-automator-core", automatorModule && automatorModule.apiEntries.some((entry) => entry.id === "automator.click" && entry.status === "supported_async") && automatorModule.apiEntries.some((entry) => entry.id === "automator.longClick" && entry.status === "supported_async"), "status manifest must reflect existing automator bridge methods for S11-16 action wrappers"),
    check("matrix-selector-partial", /\| \d+ \| selector \| selector \| partial \| accessibility \|/.test(files.matrix.text), "compat matrix must show selector backed by accessibility surface"),
    check("task-plan-s11-15-complete", /\[x\]\s+S11-15/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-15 complete"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoSelectorDslHandleDesign") && files.gradle.text.includes("verify-rhino-selector-dsl-handle-design.js"), "Gradle must register verifyNodeRhinoSelectorDslHandleDesign"),
  ];
  REQUIRED_BRIDGE_METHODS.forEach((method) => {
    checks.push(check(
      `bridge-method-${method}`,
      files.bridgeProtocol.text.includes(`"${method}"`) && files.runtime.text.includes(method),
      `existing accessibility bridge/runtime must expose ${method}`,
    ));
  });
  REQUIRED_BUILDERS.forEach((builder) => {
    checks.push(check(
      `runtime-builder-${builder}`,
      files.runtime.text.includes(`${builder}: function`) || files.runtime.text.includes(`${builder}: function(`),
      `runtime accessibility module must expose selector builder ${builder}`,
    ));
  });
  return checks;
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
    "# Rhino Selector DSL Handle Design Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Bridge methods: ${report.requiredBridgeMethods.map((name) => `\`${name}\``).join(", ")}`,
    `- Builders: ${report.requiredBuilders.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Design only: ${report.decision.designOnly}`,
    `- Async boundary specified: ${report.decision.asyncBoundarySpecified}`,
    `- Bridge methods covered: ${report.decision.bridgeMethodsCovered}`,
    `- Types draft present: ${report.decision.typesDraftPresent}`,
    `- Gap list present: ${report.decision.gapListPresent}`,
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
      console.log("Usage: node verify-rhino-selector-dsl-handle-design.js [--repo-root dir] [--report-dir dir]");
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
