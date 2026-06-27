#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-packaged-smoke-verification";
const TEST_METHOD = "installAndRunRhinoCompatEmbeddedNodePackagedApk";
const TEST_SELECTOR = "org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#" + TEST_METHOD;
const REQUIRED_SURFACES = Object.freeze(["toast", "files", "storage", "selector", "image"]);
const REQUIRED_PERMISSIONS = Object.freeze(["toast", "storage", "accessibility", "image"]);
const REQUIRED_MARKERS = Object.freeze([
  "packaged.rhino_compat.ready=true",
  "packaged.rhino_compat.loader=true",
  "packaged.rhino_compat.toast=true",
  "packaged.rhino_compat.files=true",
  "packaged.rhino_compat.storage=true",
  "packaged.rhino_compat.selector=true",
  "packaged.rhino_compat.image=true",
  "packaged.rhino_compat.permissions=true",
  "packaged.rhino_compat.cleanup=true",
  "packaged.rhino_compat.pending=0",
]);

const FILES = {
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  appGradle: "app/build.gradle.kts",
  phase11Gradle: "app/node-phase11-gate.gradle.kts",
  reportDoc: "docs/nodejs/reports/phase11/RHINO_COMPAT_PACKAGED_SMOKE.md",
  testing: "docs/nodejs/TESTING.md",
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
    schema: "autojs6-node-rhino-compat-packaged-smoke-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatPackagedSmokeHost",
    deviceGate: "verifyNodeRhinoCompatPackagedSmoke",
    instrumentationSelector: TEST_SELECTOR,
    requiredSurfaces: REQUIRED_SURFACES,
    requiredPermissions: REQUIRED_PERMISSIONS,
    requiredMarkers: REQUIRED_MARKERS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      deviceGateRegistered: checkById(checks, "gradle-device-gate"),
      hostReportReady: checkById(checks, "phase11-host-task"),
      requiredMarkersPresent: checkById(checks, "packaged-runtime-markers"),
      acceptanceCoveragePresent: checkById(checks, "acceptance-surface-coverage"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat packaged smoke verification: " + report.decision.status);
  console.log("Rhino compat packaged smoke selector: " + TEST_SELECTOR);
  console.log("Rhino compat packaged smoke policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files) {
  const kotlin = files.packagedTest.text;
  const appGradle = files.appGradle.text;
  const phase11Gradle = files.phase11Gradle.text;
  return [
    check(
      "packaged-test-methods",
      includesAll(kotlin, [
        "fun buildRhinoCompatEmbeddedNodePackagedApk()",
        `fun ${TEST_METHOD}()`,
        "buildPackagedRhinoCompatApk()",
        "assertInstallAndRunPackagedRhinoCompatSmoke(outputApk)",
      ]),
      "EmbeddedNodePackagedApkSmokeTest must define build and install/run methods for the S11-29 packaged Rhino compat smoke",
    ),
    check(
      "packaged-compat-loader",
      includesAll(kotlin, [
        "require(\"autojs6:compat\")",
        "compat.status()",
        "status.defaultGlobalInjection === false",
        "status.implementedTasks.indexOf(\"S11-20\") >= 0",
      ]),
      "packaged script must load autojs6:compat and verify explicit opt-in loader status",
    ),
    check(
      "acceptance-surface-coverage",
      includesAll(kotlin, [
        "compat.toastLog(",
        "compat.files.write(",
        "compat.files.read(",
        "compat.files.remove(",
        "compat.storages.create(",
        "selectorHandle.findOne(",
        "selectorHandle.click(",
        "compat.images.read(",
        "compat.images.clip(",
        "compat.images.findImage(",
        "compat.images.recycle(",
      ]),
      "packaged script must cover toast, files, storage, selector, and image Rhino compat paths",
    ),
    check(
      "packaged-fake-transport",
      includesAll(kotlin, [
        "bridge.__test.setTransport",
        "request.module === \"toast\"",
        "request.module === \"storage\" || request.module === \"storages\"",
        "request.module === \"accessibility\"",
        "request.module === \"image\" || request.module === \"images\"",
      ]),
      "packaged smoke must use fake bridge transport for deterministic module coverage",
    ),
    check(
      "packaged-node-permissions",
      includesAll(kotlin, REQUIRED_PERMISSIONS.map((permission) => `"${permission}"`)) &&
        kotlin.includes("nodePermissions = RHINO_COMPAT_PACKAGED_NODE_PERMISSIONS") &&
        includesAll(kotlin, [
          "hasPermission(request, \"toast\")",
          "hasPermission(request, \"storage\")",
          "hasPermission(request, \"accessibility\")",
          "hasPermission(request, \"image\")",
          "packaged.rhino_compat.permissions=",
        ]),
      "packaged smoke must declare and assert toast/storage/accessibility/image bridge permissions",
    ),
    check(
      "packaged-resource-cleanup",
      includesAll(kotlin, [
        "const activeImages = new Set();",
        "activeImages.add(id);",
        "activeImages.delete(imageId(args[0]));",
        "const cleanupOk = activeImages.size === 0;",
        "bridge.__test.pendingCount()",
        "packaged.rhino_compat.cleanup=",
        "packaged.rhino_compat.pending=",
      ]),
      "packaged smoke must assert image handle cleanup and zero pending bridge calls",
    ),
    check(
      "packaged-runtime-markers",
      REQUIRED_MARKERS.every((marker) => kotlin.includes(marker)),
      "packaged smoke runtime marker list must include every S11-29 required marker",
    ),
    check(
      "packaged-apk-contract",
      includesAll(kotlin, [
        "RHINO_COMPAT_PACKAGED_PROJECT_NAME",
        "RHINO_COMPAT_PACKAGED_PACKAGE_NAME",
        "RHINO_COMPAT_PACKAGED_APK_NAME",
        "PackagedNodeRhinoCompatSmoke",
        "org.autojs6.packaged.node.rhino.compat.smoke",
        "PackagedNodeRhinoCompatSmoke_v1.0.0.apk",
        "/data/local/tmp/autojs6-packaged-node-rhino-compat-smoke.apk",
      ]),
      "packaged smoke must have stable project/package/APK names and shell install path",
    ),
    check(
      "gradle-focused-selector",
      includesAll(appGradle, [
        "nodeJsRhinoCompatPackagedSmokeInstrumentationClasses",
        TEST_SELECTOR,
        "\"verifyNodeRhinoCompatPackagedSmoke\" to nodeJsRhinoCompatPackagedSmokeInstrumentationClasses",
      ]),
      "app/build.gradle.kts must register the focused instrumentation selector for the S11-29 device gate",
    ),
    check(
      "gradle-device-gate",
      includesAll(phase11Gradle, [
        "verifyNodeRhinoCompatPackagedSmoke",
        "connectedAppDebugAndroidTest",
        "verifyNodeRhinoCompatPackagedSmokeHost",
        "verify-rhino-compat-packaged-smoke.js",
      ]),
      "Phase 11 Gradle script must register both the host contract report and connected-device gate",
    ),
    check(
      "phase11-host-task",
      includesAll(phase11Gradle, [
        "rhino-compat-packaged-smoke-verification.json",
        "rhino-compat-packaged-smoke-verification.md",
        "docs/nodejs/reports/phase11/RHINO_COMPAT_PACKAGED_SMOKE.md",
      ]),
      "Phase 11 host task must write S11-29 packaged smoke JSON and Markdown reports",
    ),
    check(
      "report-doc-s11-29",
      includesAll(files.reportDoc.text, [
        "S11-29",
        "verifyNodeRhinoCompatPackagedSmoke",
        "verifyNodeRhinoCompatPackagedSmokeHost",
        TEST_SELECTOR,
        "compat loader",
        "bridge permission",
        "resource cleanup",
        "toast",
        "files",
        "storage",
        "selector",
        "image",
        "packaged.rhino_compat.pending=0",
      ]),
      "S11-29 report must document commands, selector, coverage, permissions, and cleanup markers",
    ),
    check(
      "testing-doc-s11-29",
      includesAll(files.testing.text, [
        "verifyNodeRhinoCompatPackagedSmoke",
        "verifyNodeRhinoCompatPackagedSmokeHost",
        "RHINO_COMPAT_PACKAGED_SMOKE.md",
        TEST_SELECTOR,
      ]),
      "TESTING.md must document the S11-29 packaged Rhino compat smoke gates",
    ),
    check(
      "task-plan-s11-29-complete",
      /\[x\]\s+S11-29/.test(files.taskPlan.text) &&
        files.taskPlan.text.includes("verifyNodeRhinoCompatPackagedSmoke") &&
        files.taskPlan.text.includes("RHINO_COMPAT_PACKAGED_SMOKE.md"),
      "Phase 11 task plan must mark S11-29 complete and name the packaged smoke gates",
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
    "# Rhino Compat Packaged Smoke Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Device gate: \`${report.deviceGate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Instrumentation selector: \`${report.instrumentationSelector}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Coverage",
    "",
    `- Required surfaces: ${report.requiredSurfaces.map((name) => `\`${name}\``).join(", ")}`,
    `- Required permissions: ${report.requiredPermissions.map((name) => `\`${name}\``).join(", ")}`,
    "",
    "## Runtime Markers",
    "",
    "| Marker |",
    "| --- |",
  ];
  report.requiredMarkers.forEach((marker) => {
    lines.push(`| \`${marker}\` |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
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
      console.log("Usage: node verify-rhino-compat-packaged-smoke.js [--repo-root dir] [--report-dir dir]");
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
