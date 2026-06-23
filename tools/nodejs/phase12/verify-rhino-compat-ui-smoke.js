#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-ui-smoke-verification";

const FILES = {
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  resourceLeakTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  uiDesign: "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md",
  uiBridge: "docs/nodejs/design/UI_BRIDGE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  report: "docs/nodejs/reports/phase12/RHINO_UI_COMPAT_SMOKE.md",
  surfaceManifest: "docs/nodejs/rhino-compat/phase12-surface-manifest.json",
  phase12Tasks: "docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase12-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const checks = buildChecks(files);
  const policyViolations = checks.filter((checkItem) => !checkItem.passed).map((checkItem) => checkItem.message);
  const report = {
    schema: "autojs6-node-rhino-compat-ui-smoke-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatUiSmoke",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      packagedMarker: checkById(checks, "packaged-compat-ui-marker"),
      fakeTransport: checkById(checks, "instrumentation-fake-transport"),
      uiSessionCleanup: checkById(checks, "ui-session-resource-cleanup"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat UI smoke verification: " + report.decision.status);
  console.log("Rhino compat UI smoke policy violations: " + policyViolations.length);
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

function buildChecks(files) {
  const surfaceManifest = JSON.parse(files.surfaceManifest.text);
  const uiSurface = (surfaceManifest.surfaces || []).find((item) => item.id === "ui.compat");
  const scheduledPolicy = segmentAfter(files.executionModePolicy.text, "mode = MODE_SCHEDULED");
  return [
    check(
      "packaged-compat-ui-marker",
      includesAll(files.packagedTest.text, [
        'request.module === "ui"',
        'request.method === "showLayout"',
        'request.method === "drainEvents"',
        'request.method === "update" || request.method === "batchUpdate" || request.method === "close"',
        "const compatUiHandle = await compat.ui.showLayout",
        "compatUiHandle.on(\"click\"",
        "compatUiHandle.update(",
        "compatUiHandle.batchUpdate(",
        "compatUiHandle.close(",
        "const compatUiOk = compat.ui",
        "packaged.rhino_compat.compat_ui=",
        '"packaged.rhino_compat.compat_ui=true"',
        '"ui",',
        "packaged.rhino_compat.pending=0",
      ]),
      "Packaged Rhino compat smoke must exercise compat.ui show/event/update/batchUpdate/close and publish a zero-pending marker.",
    ),
    check(
      "instrumentation-fake-transport",
      includesAll(files.conformanceTest.text, [
        '["autojs6:compat.ui", compat.ui]',
        "compat.ui.showLayout",
        "compat.ui.update",
        "compat.ui.batchUpdate",
        "compat.ui.close",
        "compat.ui click callback did not run",
        "bridge.conformance.pending=0",
      ]),
      "Android instrumentation fake transport must cover compat.ui event-loop callbacks and pending-call cleanup.",
    ),
    check(
      "ui-session-resource-cleanup",
      includesAll(files.resourceLeakTest.text, [
        "NodeBridgeResourceType.UI_SESSION",
        "activeTypeCount(provider, NodeBridgeResourceType.UI_SESSION)",
        "assertNoActiveBridgeResources(provider, \"ui-$index\")",
        "provider.destroy(NodeBridgeError.processDead(\"bridge resource stress finished\"))",
        "embedded_script.bridge_resource.active_count",
        "embedded_script.bridge_resource.active_types",
      ]) &&
        includesAll(files.protocol.text, [
          "type = NodeBridgeResourceType.UI_SESSION",
          "resourceRegistry.release(sessionId, \"ui.close\")",
          "resourceRegistry.closeAll(destroyReason)",
          "uiSessionIds.clear()",
        ]),
      "Provider stress coverage and protocol cleanup must prove UI_SESSION returns to zero on close and destroy.",
    ),
    check(
      "execution-policy-boundary",
      includesAll(scheduledPolicy, [
        "supportedLaunchSurfaces = setOf(LAUNCH_SURFACE_SCHEDULED_RUNNER)",
        "allowedProviders = listOf(",
        '"work_manager"',
        '"storage"',
      ]) &&
        !scheduledPolicy.includes('"ui"'),
      "Scheduled/background execution policy must not list ui as an allowed provider while live packaged UI remains unpromoted.",
    ),
    check(
      "docs-and-report",
      includesAll(files.report.text, [
        "P12-10",
        "verifyNodeRhinoCompatUiSmoke",
        "show/update/batchUpdate/event/close",
        "UI_SESSION",
        "packaged.rhino_compat.compat_ui=true",
        "packaged.rhino_compat.pending=0",
        "skip reason",
        "signature mismatch",
      ]) &&
        includesAll(files.uiDesign.text, ["P12-10", "packaged Rhino compat smoke", "compat.ui"]) &&
        includesAll(files.uiBridge.text, ["P12-10", "compat.ui", "packaged marker"]) &&
        includesAll(files.securityModel.text, ["P12-10", "compat.ui", "packaged"]),
      "P12-10 docs and report must explain runnable smoke paths, skip reason, cleanup, and packaged boundary.",
    ),
    check(
      "surface-manifest-evidence",
      uiSurface &&
        (uiSurface.tasks || []).includes("P12-10") &&
        includesAll(JSON.stringify(uiSurface), [
          "RHINO_UI_COMPAT_SMOKE.md",
          "verify-rhino-compat-ui-smoke.js",
          "verifyNodeRhinoCompatUiSmoke",
          "EmbeddedNodePackagedApkSmokeTest.kt",
          "NodeBridgeResourceLeakStressInstrumentationTest.kt",
        ]),
      "Phase 12 surface manifest must record P12-10 smoke, packaged, and cleanup evidence.",
    ),
    check(
      "phase12-task-complete",
      /\[x\]\s+P12-10/.test(files.phase12Tasks.text) &&
        files.phase12Tasks.text.includes("RHINO_UI_COMPAT_SMOKE.md") &&
        files.phase12Tasks.text.includes("verifyNodeRhinoCompatUiSmoke") &&
        files.phase12Tasks.text.includes("packaged.rhino_compat.compat_ui=true"),
      "Phase 12 task plan must mark P12-10 complete with report, marker, and gate evidence.",
    ),
    check(
      "gradle-gate",
      files.gradle.text.includes("verifyNodeRhinoCompatUiSmoke") &&
        files.gradle.text.includes("verify-rhino-compat-ui-smoke.js") &&
        files.gradle.text.includes("RHINO_UI_COMPAT_SMOKE.md") &&
        files.gradle.text.includes("EmbeddedNodePackagedApkSmokeTest.kt") &&
        files.gradle.text.includes("NodeBridgeResourceLeakStressInstrumentationTest.kt"),
      "Gradle must register verifyNodeRhinoCompatUiSmoke with packaged, instrumentation, cleanup, and report inputs.",
    ),
  ];
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((checkItem) => checkItem.id === id) || null;
}

function includesAll(text, needles) {
  return needles.every((needle) => text.includes(needle));
}

function segmentAfter(text, marker) {
  const start = text.indexOf(marker);
  if (start < 0) {
    return "";
  }
  const end = text.indexOf("resourceQuotaProfile", start);
  return text.slice(start, end < 0 ? undefined : end);
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-ui-smoke.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat UI Smoke Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Checks",
    "",
  ];
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  return lines.join("\n") + "\n";
}

main();
