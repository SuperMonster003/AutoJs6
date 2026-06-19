#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const SCHEMA = "autojs6-node-dual-runtime-ab-gate-v1";
const CURRENT_SLOT = "node24_5";
const CANDIDATE_SLOT = "node24_current";
const CURRENT_VERSION = "24.5.0";
const DEFAULT_CANDIDATE_VERSION = "24.17.0";
const REQUIRED_ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs/patch-upgrade"));
  const docReport = options.docReport ? path.resolve(options.docReport) : null;
  const runtimeSlot = normalizeSlot(options.runtimeSlot || CURRENT_SLOT);
  const candidateVersion = normalizeVersion(options.targetVersion || DEFAULT_CANDIDATE_VERSION);

  const currentEvidence = currentSlotEvidence(repoRoot);
  const candidateEvidence = candidateSlotEvidence(repoRoot, options, candidateVersion);
  const slots = [
    buildSlot(CURRENT_SLOT, CURRENT_VERSION, runtimeSlot === CURRENT_SLOT, currentEvidence),
    buildSlot(CANDIDATE_SLOT, candidateVersion, runtimeSlot === CANDIDATE_SLOT, candidateEvidence),
  ];
  const suiteMatrix = buildSuiteMatrix(slots);
  const decision = buildDecision(slots, suiteMatrix);
  const report = {
    schema: SCHEMA,
    generatedAt: new Date().toISOString(),
    policy: {
      selectorProperty: "autojs.nodejs.runtimeSlot",
      allowedSlots: [CURRENT_SLOT, CANDIDATE_SLOT],
      selectedSlot: runtimeSlot,
      buildOnly: true,
      userProjectSelectable: false,
      arbitraryNativeRuntimeSelectable: false,
    },
    slots,
    suiteMatrix,
    decision,
  };

  fs.mkdirSync(reportDir, { recursive: true });
  const jsonPath = path.join(reportDir, "node-dual-runtime-ab-gate.json");
  const markdownPath = path.join(reportDir, "node-dual-runtime-ab-gate.md");
  const markdown = renderMarkdown(report);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(markdownPath, markdown);
  if (docReport) {
    fs.mkdirSync(path.dirname(docReport), { recursive: true });
    fs.writeFileSync(docReport, markdown);
  }

  console.log("Node dual-runtime A/B gate JSON: " + jsonPath);
  console.log("Node dual-runtime A/B gate Markdown: " + markdownPath);
  if (docReport) {
    console.log("Node dual-runtime A/B gate doc: " + docReport);
  }
  console.log("Node dual-runtime A/B gate decision: " + decision.status);

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
    "Usage: node tools/nodejs/patch-upgrade/generate-dual-runtime-ab-gate.js [options]",
    "",
    "Options:",
    "  --runtime-slot <slot>              Build-selected slot: node24_5 or node24_current.",
    "  --target-version <version>         Candidate Node 24 patch. Defaults to 24.17.0.",
    "  --candidate-jni-libs <dir>         Candidate jniLibs root containing <abi>/libnode.so.",
    "  --candidate-headers <dir>          Candidate include/node directory or Node source include root.",
    "  --candidate-compatibility <file>   Candidate compatibility corpus report.",
    "  --candidate-security <file>        Candidate security corpus report.",
    "  --candidate-packaged <file>        Candidate packaged smoke report.",
    "  --candidate-memory <file>          Candidate memory stress report.",
    "  --candidate-performance <file>     Candidate performance benchmark report.",
    "  --report-dir <dir>                 Output directory. Defaults to build/reports/nodejs/patch-upgrade.",
    "  --doc-report <file>                Also write the Markdown report to a tracked docs path.",
    "  --fail-on-blocked                  Exit non-zero unless both slots have complete evidence.",
  ].join("\n"));
}

function normalizeSlot(value) {
  const slot = String(value).trim().toLowerCase().replace(/-/g, "_");
  if (![CURRENT_SLOT, CANDIDATE_SLOT].includes(slot)) {
    throw new Error("Unsupported runtime slot: " + value);
  }
  return slot;
}

function normalizeVersion(value) {
  return String(value).replace(/^v/, "");
}

function currentSlotEvidence(repoRoot) {
  return {
    artifacts: {
      headers: evidenceFile(repoRoot, "app/src/main/cpp/node-v24.5.0/include/node/node.h"),
      jniLibs: evidenceJniLibs(repoRoot, "app/src/main/jniLibs"),
    },
    suites: {
      compatibilityCorpus: evidenceFile(repoRoot, "app/build/reports/nodejs/compatibility-profile.json"),
      securityCorpus: evidenceFile(repoRoot, "build/reports/nodejs/v1.1-beta-summary.json"),
      packagedSmoke: evidenceFile(repoRoot, "app/build/reports/embedded-node/packaged-smoke-aggregate.txt"),
      memoryStress: evidenceLatest(
        repoRoot,
        [
          "app/build/reports/nodejs/perf",
          "build/reports/nodejs/node-profile-v1-ga/performance",
        ],
        /^embedded-node-memory-leak-stress-.*\.json$/,
      ),
      performanceBenchmark: evidenceLatest(
        repoRoot,
        [
          "app/build/reports/nodejs/perf",
          "build/reports/nodejs/node-profile-v1-ga/performance",
        ],
        /^embedded-node-performance-.*\.json$/,
      ),
    },
  };
}

function candidateSlotEvidence(repoRoot, options, candidateVersion) {
  return {
    artifacts: {
      headers: options.candidateHeaders
        ? evidenceHeaderDir(repoRoot, path.resolve(options.candidateHeaders))
        : missingEvidence("candidate headers were not supplied"),
      jniLibs: options.candidateJniLibs
        ? evidenceJniLibs(repoRoot, path.resolve(options.candidateJniLibs))
        : missingEvidence("candidate jniLibs were not supplied"),
    },
    suites: {
      compatibilityCorpus: evidenceOption(repoRoot, options.candidateCompatibility, "candidate compatibility corpus report was not supplied"),
      securityCorpus: evidenceOption(repoRoot, options.candidateSecurity, "candidate security corpus report was not supplied"),
      packagedSmoke: evidenceOption(repoRoot, options.candidatePackaged, `candidate packaged smoke report with packaged.node.version=v${candidateVersion} was not supplied`),
      memoryStress: evidenceOption(repoRoot, options.candidateMemory, "candidate memory stress report was not supplied"),
      performanceBenchmark: evidenceOption(repoRoot, options.candidatePerformance, "candidate performance benchmark report was not supplied"),
    },
  };
}

function buildSlot(id, version, selected, evidence) {
  const blockers = [];
  for (const [area, item] of Object.entries(evidence.artifacts)) {
    if (!item.present) {
      blockers.push({ area: `artifacts.${area}`, reason: item.reason });
    }
  }
  for (const [area, item] of Object.entries(evidence.suites)) {
    if (!item.present) {
      blockers.push({ area: `suites.${area}`, reason: item.reason });
    }
  }
  return {
    id,
    version,
    selected,
    status: blockers.length === 0 ? "ready" : "blocked",
    evidence,
    blockers,
  };
}

function buildSuiteMatrix(slots) {
  const suiteDefinitions = [
    {
      id: "compatibilityCorpus",
      command: ':app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest"',
    },
    {
      id: "securityCorpus",
      command: ':app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest"',
    },
    {
      id: "packagedSmoke",
      command: ':app:verifyEmbeddedNodePackagedDebug',
    },
    {
      id: "memoryStress",
      command: ':app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest"',
    },
    {
      id: "performanceBenchmark",
      command: ':app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedPerformanceInstrumentationTest"',
    },
  ];
  return suiteDefinitions.map((suite) => ({
    ...suite,
    slots: Object.fromEntries(slots.map((slot) => [slot.id, slot.evidence.suites[suite.id]])),
  }));
}

function buildDecision(slots, suiteMatrix) {
  const blockers = slots.flatMap((slot) => slot.blockers.map((blocker) => ({
    slot: slot.id,
    ...blocker,
  })));
  const identicalSuitesReady = suiteMatrix.every((suite) =>
    slots.every((slot) => suite.slots[slot.id] && suite.slots[slot.id].present),
  );
  const status = blockers.length === 0 && identicalSuitesReady ? "ready" : "blocked";
  return {
    status,
    summary: status === "ready"
      ? "Both runtime slots have complete A/B evidence. Runtime selection remains build-only."
      : "Dual-runtime A/B gate is blocked until node24_current artifacts and identical suite evidence are supplied.",
    blockers,
  };
}

function evidenceOption(repoRoot, value, missingReason) {
  return value ? evidenceFile(repoRoot, path.resolve(value)) : missingEvidence(missingReason);
}

function evidenceFile(repoRoot, file) {
  const absolutePath = path.isAbsolute(file) ? file : path.join(repoRoot, file);
  if (!isFile(absolutePath)) {
    return missingEvidence("missing: " + relativePath(repoRoot, absolutePath));
  }
  const stat = fs.statSync(absolutePath);
  return {
    present: true,
    path: relativePath(repoRoot, absolutePath),
    bytes: stat.size,
  };
}

function evidenceHeaderDir(repoRoot, root) {
  const candidates = [
    root,
    path.join(root, "include/node"),
    path.join(root, "include"),
    path.join(root, "node"),
  ];
  const headerDir = candidates.find((candidate) => isFile(path.join(candidate, "node.h")));
  if (!headerDir) {
    return missingEvidence("candidate headers do not contain node.h: " + relativePath(repoRoot, root));
  }
  return evidenceFile(repoRoot, path.join(headerDir, "node.h"));
}

function evidenceJniLibs(repoRoot, root) {
  const absoluteRoot = path.isAbsolute(root) ? root : path.join(repoRoot, root);
  const missing = REQUIRED_ABIS.filter((abi) => !isFile(path.join(absoluteRoot, abi, "libnode.so")));
  if (missing.length > 0) {
    return missingEvidence("missing libnode.so for ABI(s): " + missing.join(", "));
  }
  return {
    present: true,
    root: relativePath(repoRoot, absoluteRoot),
    abis: REQUIRED_ABIS,
  };
}

function evidenceLatest(repoRoot, relativeDirs, pattern) {
  const candidates = relativeDirs
    .map((dir) => path.join(repoRoot, dir))
    .filter(isDirectory)
    .flatMap((dir) => fs.readdirSync(dir)
      .filter((name) => pattern.test(name))
      .map((name) => path.join(dir, name)))
    .map((file) => ({ file, mtimeMs: fs.statSync(file).mtimeMs }))
    .sort((left, right) => right.mtimeMs - left.mtimeMs);
  if (candidates.length === 0) {
    return missingEvidence("no matching report found");
  }
  return evidenceFile(repoRoot, candidates[0].file);
}

function missingEvidence(reason) {
  return {
    present: false,
    reason,
  };
}

function renderMarkdown(report) {
  const lines = [];
  lines.push("# Node Dual-runtime A/B Gate");
  lines.push("");
  lines.push(`Generated: ${report.generatedAt}`);
  lines.push("");
  lines.push("## Decision");
  lines.push("");
  lines.push(`- Status: **${report.decision.status}**`);
  lines.push(`- Summary: ${report.decision.summary}`);
  lines.push(`- Selector property: \`${report.policy.selectorProperty}\``);
  lines.push(`- Allowed slots: ${report.policy.allowedSlots.map((slot) => `\`${slot}\``).join(", ")}`);
  lines.push(`- Selected slot: \`${report.policy.selectedSlot}\``);
  lines.push(`- Build-only: ${report.policy.buildOnly}`);
  lines.push(`- User project selectable: ${report.policy.userProjectSelectable}`);
  lines.push(`- Arbitrary native runtime selectable: ${report.policy.arbitraryNativeRuntimeSelectable}`);
  lines.push("");
  lines.push("## Slots");
  lines.push("");
  lines.push("| Slot | Version | Selected | Status | Blockers |");
  lines.push("| --- | --- | --- | --- | --- |");
  for (const slot of report.slots) {
    lines.push(`| \`${slot.id}\` | v${slot.version} | ${slot.selected} | ${slot.status} | ${slot.blockers.length} |`);
  }
  lines.push("");
  lines.push("## Identical Suite Matrix");
  lines.push("");
  lines.push("| Suite | node24_5 | node24_current | Command |");
  lines.push("| --- | --- | --- | --- |");
  for (const suite of report.suiteMatrix) {
    lines.push([
      `| ${suite.id}`,
      evidenceLabel(suite.slots[CURRENT_SLOT]),
      evidenceLabel(suite.slots[CANDIDATE_SLOT]),
      `\`${suite.command}\` |`,
    ].join(" | "));
  }
  lines.push("");
  if (report.decision.blockers.length > 0) {
    lines.push("## Blockers");
    lines.push("");
    for (const blocker of report.decision.blockers) {
      lines.push(`- ${blocker.slot}.${blocker.area}: ${blocker.reason}`);
    }
    lines.push("");
  }
  lines.push("## Re-run");
  lines.push("");
  lines.push("```powershell");
  lines.push(".\\gradlew.bat --console=plain :app:generateNodeDualRuntimeABReport");
  lines.push(".\\gradlew.bat --console=plain :app:verifyNodeDualRuntimeABGate \"-Pautojs.nodejs.test.deviceProfile=sony\"");
  lines.push("```");
  lines.push("");
  lines.push("`verifyNodeDualRuntimeABGate` is intended for candidate validation and should be run only after `node24_current` artifacts and matching suite reports exist.");
  lines.push("");
  return lines.join("\n");
}

function evidenceLabel(evidence) {
  if (!evidence || !evidence.present) {
    return "missing";
  }
  return evidence.path || evidence.root || "present";
}

function relativePath(repoRoot, file) {
  return path.relative(repoRoot, file).replace(/\\/g, "/") || ".";
}

function parseBoolean(value) {
  return ["1", "true", "yes"].includes(String(value).toLowerCase());
}

function isFile(file) {
  try {
    return fs.statSync(file).isFile();
  } catch (_error) {
    return false;
  }
}

function isDirectory(file) {
  try {
    return fs.statSync(file).isDirectory();
  } catch (_error) {
    return false;
  }
}

main();
