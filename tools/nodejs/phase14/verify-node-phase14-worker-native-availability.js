#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-worker-native-availability";
const TARGET_ABIS = Object.freeze(["arm64-v8a", "armeabi-v7a", "x86_64"]);
const UNSUPPORTED_ABIS = Object.freeze(["x86"]);
const FUTURE_PROMOTION_ITEMS = Object.freeze([
  {
    id: "native_available_target_abi_archive",
    classification: "future_promotion",
    requiredEvidence: "Archive worker.preflight.status=available on every ABI/device selected for v1.2 worker promotion.",
  },
  {
    id: "runnable_worker_lifecycle_archive",
    classification: "future_promotion",
    requiredEvidence: "Archive runnable Worker lifecycle, message, termination, timeout, exception, cleanup, nested-worker denial, and packaged behavior.",
  },
  {
    id: "worker_resource_rss_crash_archive",
    classification: "future_promotion",
    requiredEvidence: "Archive repeated Worker/WorkerPool resource, RSS, fd, thread, cleanup, and crash-recovery stress on a native-worker-capable target.",
  },
  {
    id: "desktop_worker_release_policy_archive",
    classification: "future_promotion",
    requiredEvidence: "Decide whether native workers belong to desktop_compat_opt_in or stay confined to debug_unsafe_lab.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_28_WORKER_NATIVE_AVAILABILITY.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/worker-native-availability-audit.json",
  planDoc: "docs/nodejs/phase14/PHASE14_WORKER_NATIVE_AVAILABILITY.md",
  p13WorkerReport: "docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md",
  p13WorkerPolicy: "docs/nodejs/releases/V1_1_WORKER_POLICY.md",
  p13GeneratedWorkerReport: "phase13-worker-threads-opt-in-promotion.json",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  buildGradle: "app/build.gradle.kts",
  cmake: "app/src/main/cpp/CMakeLists.txt",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  runtimeBridge: "app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedRuntimeBridge.kt",
  nodeBridgeCore: "app/src/main/cpp/node_bridge_core.cpp",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  workerSupport: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsTestSupport.kt",
  workerBootstrapTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsBootstrapInstrumentationTest.kt",
  workerExperimentalTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsExperimentalInstrumentationTest.kt",
  workerStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWorkerThreadsResourceStressInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  workerDesignDoc: "docs/nodejs/design/WORKER_THREADS.md",
  desktopMatrix: "docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  errorCatalog: "docs/nodejs/ERROR_CATALOG.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(repoRoot, files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 worker native availability: " + report.decision.status);
  console.log("Phase 14 worker native availability blockers: " + report.blockers.length);
  console.log("Phase 14 worker native availability policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const generated = relative.endsWith(".json") && !relative.includes("/");
      const file = generated ? path.join(reportDir, relative) : path.join(repoRoot, relative);
      return [
        key,
        {
          relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
          exists: fs.existsSync(file),
          text: read(file),
        },
      ];
    }),
  );
}

function buildReport(repoRoot, files) {
  const plan = parseJson(files.planJson.text);
  const p13Worker = parseJson(files.p13GeneratedWorkerReport.text);
  const manifest = parseJson(files.surfaceManifest.text);
  const workerSurface = (manifest.surfaces || []).find((entry) => entry.id === "desktop.worker_runtime") || null;
  const artifacts = TARGET_ABIS.map((abi) => artifactInfo(repoRoot, abi));
  const unsupportedArtifacts = UNSUPPORTED_ABIS.map((abi) => artifactInfo(repoRoot, abi));
  const scenarios = [
    scenario("runtime_abi_artifacts", "Node 24.5 target ABI artifacts and unsupported x86 boundary are audited", [
      value("schema", plan.schema === "autojs6-node-phase14-worker-native-availability-audit-v1"),
      value("task", plan.task === "P14-28"),
      value("status", plan.status === "ready"),
      value("decision", plan.decision === "intentional_non_support_refreshed"),
      value("promotionState", plan.promotionState === "native_worker_unavailable"),
      value("runtimeSlot", (plan.nodeRuntime || {}).runtimeSlot === "node24_5"),
      value("nodeVersion", (plan.nodeRuntime || {}).nodeVersion === "24.5.0"),
      value("targetAbis", sameSet((plan.nodeRuntime || {}).targetAbis || [], TARGET_ABIS)),
      value("unsupportedAbis", sameSet((plan.nodeRuntime || {}).unsupportedAbis || [], UNSUPPORTED_ABIS)),
      ...artifacts.map((entry) => value(`artifact:${entry.abi}`, entry.exists && entry.size > 0)),
      ...unsupportedArtifacts.map((entry) => value(`unsupportedAbsent:${entry.abi}`, !entry.exists)),
      token(files.buildGradle, 'val nodeJsReleaseSupportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")'),
      token(files.buildGradle, 'val nodeJsRuntimeSlot = providers.gradleProperty("autojs.nodejs.runtimeSlot").orElse("node24_5").get()'),
      token(files.buildGradle, "x86 must remain fail-fast until libnode.so, libautojs6-node.so, and device coverage are explicitly supported."),
      token(files.cmake, 'set(NODE_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/node-v24.5.0/include/node")'),
      token(files.cmake, 'set(NODE_LIBRARY_PATH "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so")'),
    ]),
    scenario("p13_preflight_import", "P13-34 unavailable native preflight is imported as the v1.2 audit source", [
      value("p13GeneratedExists", files.p13GeneratedWorkerReport.exists),
      value("p13StatusPartial", statusOf(p13Worker) === "partial"),
      value("p13UnavailableArchived", ((p13Worker.decision || {}).currentUnavailableGateEvidenceArchived === true)),
      value("p13NativeRunnableRequired", ((p13Worker.decision || {}).nativeRunnableEvidenceRequired === true)),
      value("p13DefaultNotPromoted", ((p13Worker.decision || {}).defaultWorkerThreadsPromoted === false)),
      value("p13ProjectPackageNotPromoted", ((p13Worker.decision || {}).projectOrPackagedOptInPromoted === false)),
      token(files.p13WorkerReport, "QV710AF65F / XQ-AT72 / Android 12"),
      token(files.p13WorkerReport, "tests=16, failures=0,"),
      token(files.p13WorkerReport, "worker.preflight.status=unavailable"),
      token(files.p13WorkerReport, "ERR_AUTOJS6_WORKER_DISABLED"),
      token(files.p13WorkerPolicy, "worker_threads native surface unavailable; shape-only facade reports nativeAvailable=false."),
      token(files.workerSupport, "worker.preflight.status=available"),
      token(files.workerSupport, "worker.preflight.status=unavailable"),
      token(files.workerSupport, "worker.preflight.error=ERR_AUTOJS6_WORKER_DISABLED|true"),
      token(files.workerSupport, "runnable worker tests remain promotion gates"),
    ]),
    scenario("android_loader_and_crash_signatures", "Android loader behavior and crash-signature classifications stay explicit", [
      token(files.runtimeBridge, 'loadLibrary("c++_shared")'),
      token(files.runtimeBridge, "loadLibrary(NativeNodeRuntimeBridge.NODE_LIBRARY_NAME)"),
      token(files.runtimeBridge, "loadLibrary(NativeNodeRuntimeBridge.BRIDGE_LIBRARY_NAME)"),
      token(files.runtimeBridge, "libnode.so failed to load"),
      token(files.runtimeBridge, "libautojs6-node.so failed to load"),
      token(files.runtimeBridge, "Build.SUPPORTED_ABIS.toList()"),
      token(files.runtimeBridge, "libnodeHandleAvailable"),
      token(files.nodeBridgeCore, "std::string classifyDlopenFailure"),
      token(files.nodeBridgeCore, "does not match the current process ABI"),
      token(files.nodeBridgeCore, "is missing from the APK for the current ABI"),
      token(files.nodeBridgeCore, "libc++_shared.so is missing from the APK for the current ABI"),
      token(files.nodeBridgeCore, "or one of its dependencies is missing symbol"),
      token(files.nodeBridgeCore, "dlopen(kNodeLibraryName, RTLD_NOW | RTLD_GLOBAL)"),
      token(files.nodeBridgeCore, '"libnode.handle.available"'),
      token(files.planDoc, "ABI mismatch, missing `libnode.so`"),
    ]),
    scenario("default_denial_no_runtime_promotion", "Default, project, packaged, and process-worker authority remain denied", [
      token(files.runtimeCapabilities, 'const val WORKER_THREADS_EXPERIMENTAL_PROPERTY = "autojs.nodejs.worker_threads.experimental"'),
      token(files.runtimeCapabilities, "const val WORKER_THREADS_EXPERIMENTAL_DEFAULT = false"),
      token(files.runtimeCapabilities, "id = \"worker_threads\""),
      token(files.runtimeCapabilities, "projectJsonOverrideAllowed = false"),
      token(files.runtimeCapabilities, "packagedApkOverrideAllowed = false"),
      token(files.nodeBridgeSources, "workerThreadsProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: __autojs6_worker_threads_experimental_enabled ? "experimental_opt_in_preflight" : "disabled_by_default"'),
      token(files.nodeBridgeSources, "nativeAvailable: false"),
      token(files.nodeBridgeSources, "processWorkerReplacementProfile: Object.freeze({"),
      token(files.workerBootstrapTest, "workerFacade.nativeAvailable === false"),
      token(files.workerExperimentalTest, "nested"),
      token(files.workerStressTest, "assertResourceGrowthBounded"),
      token(files.packagedSmokeTest, "packaged.worker_threads.esm.ready=true"),
      token(files.workerDesignDoc, "Runnable coverage is skipped when the preflight reports native workers unavailable"),
      token(files.desktopMatrix, "| `worker_threads` | `disabled` |"),
      token(files.compatibilityProfile, "P14-28 refreshes the v1.2 worker native availability audit"),
      token(files.securityModel, "P14-28 refreshes the v1.2 worker native availability audit"),
      token(files.errorCatalog, "P14-28 worker native availability decision"),
    ]),
    scenario("surface_docs_and_gradle_wiring", "Phase 14 docs, manifest, and Gradle expose the P14-28 gate", [
      value("surfaceEvidence", workerSurface &&
        workerSurface.status === "design_locked" &&
        (workerSurface.tasks || []).includes("P14-28") &&
        (workerSurface.evidence || []).includes("docs/nodejs/phase14/worker-native-availability-audit.json") &&
        (workerSurface.evidence || []).includes(":app:verifyNodePhase14WorkerNativeAvailability")),
      token(files.surfaceManifestDoc, "P14-28 adds the native worker availability audit"),
      token(files.phase14Tasks, "- [x] P14-28: Audit native `worker_threads` availability on target ABIs."),
      token(files.phase14Tasks, "Worker native availability audit deployed:"),
      token(files.phase14Report, "v1.2 worker decision: `intentional_non_support_refreshed`"),
      token(files.phase14Readme, ":app:verifyNodePhase14WorkerNativeAvailability"),
      token(files.testing, ":app:verifyNodePhase14WorkerNativeAvailability"),
      token(files.ciGates, ":app:verifyNodePhase14WorkerNativeAvailability"),
      token(files.gradle14, "verifyNodePhase14WorkerNativeAvailability"),
      token(files.gradle14, "verify-node-phase14-worker-native-availability.js"),
    ]),
  ];
  const checks = [
    check("all-target-artifacts-present", artifacts.every((entry) => entry.exists && entry.size > 0), "All target ABI libnode.so artifacts must exist."),
    check("unsupported-x86-absent", unsupportedArtifacts.every((entry) => !entry.exists), "Unsupported x86 libnode.so must remain absent."),
    check("p13-generated-report-present", files.p13GeneratedWorkerReport.exists && statusOf(p13Worker) === "partial", "P13-34 generated report must exist with partial status."),
    check("intentional-non-support-ready", (plan.v1_2Decision || {}).runtimeAuthorityChanged === false &&
      (plan.v1_2Decision || {}).runnableWorkerProofReady === false &&
      (plan.v1_2Decision || {}).defaultWorkerThreadsPromoted === false,
    "P14-28 must refresh intentional non-support without promoting runtime authority."),
    check("all-scenarios-ready", scenarios.every((entry) => entry.status === "ready"), "All P14-28 audit scenarios must be ready."),
  ];
  const policyViolations = checks.filter((entry) => !entry.ok).map((entry) => ({ id: entry.id, message: entry.message }));
  return {
    schema: "autojs6-node-phase14-worker-native-availability-report-v1",
    report: REPORT_BASENAME,
    status: policyViolations.length === 0 ? "ready" : "failed",
    task: "P14-28",
    generatedAt: new Date().toISOString(),
    inputs: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, { path: file.relative, exists: file.exists }])),
    targetAbiArtifacts: artifacts,
    unsupportedAbiArtifacts: unsupportedArtifacts,
    checks,
    scenarios,
    blockers: [],
    futurePromotionItems: FUTURE_PROMOTION_ITEMS,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      v1_2WorkerDecision: "intentional_non_support_refreshed",
      promotionState: "native_worker_unavailable",
      p14TaskComplete: policyViolations.length === 0,
      runtimeAuthorityChanged: false,
      targetAbiArtifactsPresent: artifacts.every((entry) => entry.exists && entry.size > 0),
      unsupportedX86Absent: unsupportedArtifacts.every((entry) => !entry.exists),
      p13UnavailablePreflightImported: ((p13Worker.decision || {}).currentUnavailableGateEvidenceArchived === true),
      nativeRunnableProofReady: false,
      packagedWorkerProofReady: false,
      resourceRssProofReady: false,
      canProceedToRunnableWorkerPromotion: false,
      p14_29AndP14_30Condition: "nativeAvailable=true",
      defaultWorkerThreadsPromoted: false,
      projectOrPackagedOptInPromoted: false,
      processWorkerFacadePromoted: false,
      futurePromotionItemCount: FUTURE_PROMOTION_ITEMS.length,
    },
  };
}

function artifactInfo(repoRoot, abi) {
  const relative = `app/src/main/jniLibs/${abi}/libnode.so`;
  const file = path.join(repoRoot, relative);
  if (!fs.existsSync(file)) {
    return { abi, path: relative, exists: false, size: 0, sha256: null };
  }
  const data = fs.readFileSync(file);
  return {
    abi,
    path: relative,
    exists: true,
    size: data.length,
    sha256: crypto.createHash("sha256").update(data).digest("hex"),
  };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Worker Native Availability",
    "",
    `Status: ${report.status}`,
    "",
    "## Decision",
    "",
    `- v1.2 worker decision: \`${report.decision.v1_2WorkerDecision}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Runtime authority changed: \`${report.decision.runtimeAuthorityChanged}\``,
    `- Target ABI artifacts present: \`${report.decision.targetAbiArtifactsPresent}\``,
    `- Unsupported x86 absent: \`${report.decision.unsupportedX86Absent}\``,
    `- Native runnable proof ready: \`${report.decision.nativeRunnableProofReady}\``,
    `- Can proceed to runnable worker promotion: \`${report.decision.canProceedToRunnableWorkerPromotion}\``,
    "",
    "## Target ABI Artifacts",
    "",
    "| ABI | Exists | Size | SHA-256 |",
    "| --- | --- | ---: | --- |",
  ];
  report.targetAbiArtifacts.forEach((entry) => {
    lines.push(`| \`${entry.abi}\` | \`${entry.exists}\` | ${entry.size} | \`${entry.sha256 || ""}\` |`);
  });
  lines.push("", "## Scenarios", "", "| Scenario | Status | Missing evidence |", "| --- | --- | --- |");
  report.scenarios.forEach((entry) => {
    const missing = entry.missing.length > 0 ? entry.missing.join("<br>") : "None.";
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${missing} |`);
  });
  lines.push("", "## Future Promotion Items", "");
  report.futurePromotionItems.forEach((entry) => {
    lines.push(`- \`${entry.id}\`: ${entry.requiredEvidence}`);
  });
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    report.policyViolations.forEach((entry) => {
      lines.push(`- \`${entry.id}\`: ${entry.message}`);
    });
  }
  lines.push("");
  return lines.join("\n");
}

function scenario(id, description, checks) {
  const missing = checks.filter((entry) => !entry.ok).map((entry) => entry.message);
  return {
    id,
    description,
    status: missing.length === 0 ? "ready" : "missing_evidence",
    checks,
    missing,
  };
}

function value(id, ok) {
  return { id, ok: Boolean(ok), message: `missing value check: ${id}` };
}

function token(file, expected) {
  return {
    id: `${file.relative}:${expected}`,
    ok: file.exists && file.text.includes(expected),
    message: `missing evidence token: ${expected} in ${file.relative}`,
  };
}

function check(id, ok, message) {
  return { id, ok: Boolean(ok), message };
}

function parseJson(text) {
  if (!text || !text.trim()) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function statusOf(report) {
  return report.status || (report.decision || {}).status || "missing";
}

function sameSet(actual, expected) {
  if (!Array.isArray(actual) || actual.length !== expected.length) {
    return false;
  }
  const actualSorted = actual.slice().sort();
  const expectedSorted = expected.slice().sort();
  return expectedSorted.every((value, index) => actualSorted[index] === value);
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    }
  }
  return options;
}

main();
