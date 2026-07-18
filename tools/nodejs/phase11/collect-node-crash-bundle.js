#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const childProcess = require("child_process");

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const bundleDir = path.resolve(options.bundleDir || path.join(reportDir, "final-crash-evidence-bundle"));
  const jniLibsRoot = path.resolve(options.jniLibsRoot || path.join(repoRoot, "app/src/main/jniLibs"));
  const attachmentsDir = path.join(bundleDir, "attachments");
  fs.mkdirSync(attachmentsDir, { recursive: true });

  const attachments = [
    copyAttachment(repoRoot, attachmentsDir, "logcat", options.logcat),
    copyAttachment(repoRoot, attachmentsDir, "tombstone", options.tombstone),
    copyAttachment(repoRoot, attachmentsDir, "bugreport", options.bugreport),
  ].filter(Boolean);
  const crashMarker = loadCrashMarker(repoRoot, bundleDir, options.crashMarker, options.safeCrashMarker);
  const resourceSnapshot = loadResourceSnapshot(repoRoot, bundleDir, options.resourceSnapshot);
  const timelineTail = loadTimelineTail(repoRoot, bundleDir, options.timelineTail || options.timeline);

  const manifest = {
    schema: "autojs6-node-final-crash-evidence-bundle-v1",
    generatedAt: new Date().toISOString(),
    generatedBy: "collectNodeCrashBundle",
    git: gitInfo(repoRoot),
    runtimeDescriptor: {
      runtimeSlot: options.runtimeSlot || "node24_5",
      adapter: options.adapter || "legacy_jni",
      node2417Lane: "external_runtime_maintenance",
    },
    crash: {
      scenario: options.scenario || "manual_or_not_reproduced",
      markerFile: crashMarker.path || crashMarker.source || "",
      crashInjected: parseBoolean(options.crashInjected || "false"),
    },
    crashMarker,
    automaticCollection: parseBoolean(options.automaticCollection || "false"),
    attachments,
    nativeLibraryHashes: collectNativeLibraryHashes(repoRoot, jniLibsRoot),
    symbolArchiveReports: collectExistingReports(repoRoot, [
      "app/build/reports/native-node/native-symbol-archive-appDebug.txt",
      "app/build/reports/native-node/native-deps-appDebug.txt",
      "app/build/reports/native-node/native-abi-size-appDebug.txt",
    ]),
    resourceSnapshot,
    timelineTail,
    symbolization: {
      implemented: true,
      report: relativePath(repoRoot, path.join(bundleDir, "symbolization.json")),
      tool: "symbolizeNodeCrash",
    },
    verification: {
      report: relativePath(repoRoot, path.join(reportDir, "final-crash-bundle-verification.json")),
      tool: "verifyNodeCrashBundle",
    },
    chaosInjection: {
      implemented: false,
      scenarios: [
        "SIGABRT",
        "native exception boundary",
        "V8 fatal error simulation",
        "service process kill",
        "watchdog hang",
        "adapter invalid handle",
        "destroy during pending callback",
        "crash during packaged execution",
      ],
    },
  };

  fs.mkdirSync(bundleDir, { recursive: true });
  writeJsonFile(path.join(bundleDir, "manifest.json"), manifest, repoRoot);
  fs.writeFileSync(path.join(bundleDir, "README.md"), renderBundleReadme(manifest));

  const report = {
    schema: "autojs6-node-crash-bundle-collection-v1",
    generatedAt: new Date().toISOString(),
    gate: "collectNodeCrashBundle",
    bundle: {
      directory: relativePath(repoRoot, bundleDir),
      manifest: relativePath(repoRoot, path.join(bundleDir, "manifest.json")),
      attachments: attachments.length,
      nativeLibraryCount: manifest.nativeLibraryHashes.length,
    },
    decision: {
      status: "collected",
      automaticCollection: manifest.automaticCollection,
      safeNodeProfilePreserved: true,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "final-crash-bundle-collection", report, renderCollectionMarkdown);
  console.log("Crash evidence bundle collected: " + report.bundle.manifest);
}

const REQUIRED_RESOURCE_ENTRIES = [
  "ResourceRegistry",
  "pending bridge calls",
  "timers",
  "open files",
  "image handles",
  "network requests",
  "WebSockets",
  "sensors",
  "UI handles",
  "workers",
  "execution queue",
];

function loadCrashMarker(repoRoot, bundleDir, file, safeCrashMarker) {
  const marker = {
    implemented: false,
    source: file || "",
    path: "",
    sha256: "",
    sizeBytes: 0,
    allowedSignalHandlerWork: ["write preopened fd", "record fixed-size marker"],
    forbiddenSignalHandlerWork: ["JNI", "malloc", "JSON", "formatted logging", "Java callbacks"],
  };
  if (!file) {
    return marker;
  }
  const absolute = path.resolve(file);
  if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
    marker.missing = true;
    return marker;
  }
  const target = path.join(bundleDir, "crash-marker.bin");
  fs.mkdirSync(bundleDir, { recursive: true });
  fs.copyFileSync(absolute, target);
  marker.path = relativePath(repoRoot, target);
  marker.sha256 = sha256(target);
  marker.sizeBytes = fs.statSync(target).size;
  marker.implemented = parseBoolean(safeCrashMarker || "false");
  return marker;
}

function loadResourceSnapshot(repoRoot, bundleDir, file) {
  const target = path.join(bundleDir, "resource-snapshot.json");
  const base = {
    schema: "autojs6-node-final-crash-resource-snapshot-v1",
    implemented: false,
    source: file || "",
    entries: [],
    requiredEntries: REQUIRED_RESOURCE_ENTRIES,
    summary: {},
  };
  const snapshot = file ? normalizeResourceSnapshot(repoRoot, file, base) : base;
  writeJsonFile(target, snapshot, repoRoot);
  return {
    implemented: snapshot.implemented,
    path: relativePath(repoRoot, target),
    source: snapshot.source || "",
    entryCount: Array.isArray(snapshot.entries) ? snapshot.entries.length : 0,
    requiredEntryCount: REQUIRED_RESOURCE_ENTRIES.length,
  };
}

function normalizeResourceSnapshot(repoRoot, file, base) {
  const absolute = path.resolve(file);
  if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
    return {
      ...base,
      missing: true,
    };
  }
  const payload = readJson(absolute);
  if (payload.schema === base.schema) {
    return {
      ...base,
      ...payload,
      source: relativePath(repoRoot, absolute),
      implemented: payload.implemented === true,
      requiredEntries: Array.isArray(payload.requiredEntries) ? payload.requiredEntries : REQUIRED_RESOURCE_ENTRIES,
      entries: Array.isArray(payload.entries) ? payload.entries : [],
    };
  }
  const nativeValues = payload.nativeValues && typeof payload.nativeValues === "object"
    ? payload.nativeValues
    : payload;
  const dump = parseJsonValue(nativeValues["embedded_script.bridge_resource.dump_json"], []);
  const activeTypes = parseJsonValue(nativeValues["embedded_script.bridge_resource.active_types"], {});
  const cleanupSummary = parseJsonValue(nativeValues["embedded_script.bridge_resource.last_cleanup_summary"], {});
  const hasRegistryDiagnostics = Object.keys(nativeValues)
    .some((key) => key.startsWith("embedded_script.bridge_resource."));
  return {
    ...base,
    source: relativePath(repoRoot, absolute),
    implemented: hasRegistryDiagnostics || payload.implemented === true,
    entries: Array.isArray(dump) ? dump : [],
    summary: {
      ownerExecutionId: nativeValues["embedded_script.bridge_resource.owner_execution_id"] || "",
      activeCount: numberOrNull(nativeValues["embedded_script.bridge_resource.active_count"]),
      maxActiveCount: numberOrNull(nativeValues["embedded_script.bridge_resource.max_active_count"]),
      createdCount: numberOrNull(nativeValues["embedded_script.bridge_resource.created_count"]),
      closedCount: numberOrNull(nativeValues["embedded_script.bridge_resource.closed_count"]),
      destroyClosedCount: numberOrNull(nativeValues["embedded_script.bridge_resource.destroy_closed_count"]),
      leakCount: numberOrNull(nativeValues["embedded_script.bridge_resource.leak_count"]),
      closeErrorCount: numberOrNull(nativeValues["embedded_script.bridge_resource.close_error_count"]),
      activeTypes,
      cleanupSummary,
    },
  };
}

function loadTimelineTail(repoRoot, bundleDir, file) {
  const target = path.join(bundleDir, "timeline-tail.json");
  const base = {
    schema: "autojs6-node-final-crash-timeline-tail-v1",
    implemented: false,
    source: file || "",
    eventCount: 0,
    requiredEventCount: 256,
    events: [],
  };
  const timeline = file ? normalizeTimelineTail(repoRoot, file, base) : base;
  writeJsonFile(target, timeline, repoRoot);
  return {
    implemented: timeline.implemented,
    path: relativePath(repoRoot, target),
    source: timeline.source || "",
    eventCount: timeline.eventCount || 0,
    requiredEventCount: timeline.requiredEventCount || 256,
  };
}

function normalizeTimelineTail(repoRoot, file, base) {
  const absolute = path.resolve(file);
  if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
    return {
      ...base,
      missing: true,
    };
  }
  const payload = readJson(absolute);
  if (payload.schema === base.schema) {
    const events = Array.isArray(payload.events) ? payload.events : [];
    return {
      ...base,
      ...payload,
      source: relativePath(repoRoot, absolute),
      implemented: payload.implemented === true,
      eventCount: Number(payload.eventCount || events.length),
      events: events.slice(-256),
    };
  }
  const nativeValues = payload.nativeValues && typeof payload.nativeValues === "object"
    ? payload.nativeValues
    : payload;
  const rawEvents = Array.isArray(payload)
    ? payload
    : Array.isArray(payload.events)
      ? payload.events
      : parseJsonValue(nativeValues["embedded_script.timeline_json"], []);
  const events = Array.isArray(rawEvents) ? rawEvents : [];
  return {
    ...base,
    source: relativePath(repoRoot, absolute),
    implemented: events.length > 0 || payload.implemented === true,
    eventCount: events.length,
    events: events.slice(-256),
  };
}

function collectNativeLibraryHashes(repoRoot, jniLibsRoot) {
  const roots = [
    jniLibsRoot,
    path.join(repoRoot, "app/build/intermediates/merged_native_libs"),
  ];
  const entries = [];
  const seen = new Set();
  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    walk(root)
      .filter((file) => path.basename(file) === "libnode.so" || path.basename(file) === "libautojs6-node.so")
      .sort()
      .forEach((file) => {
        const relative = relativePath(repoRoot, file);
        if (seen.has(relative)) return;
        seen.add(relative);
        entries.push({
          abi: inferAbi(file),
          library: path.basename(file),
          sha256: sha256(file),
          path: relative,
        });
      });
  }
  return entries;
}

function collectExistingReports(repoRoot, relatives) {
  return relatives
    .map((relative) => {
      const file = path.join(repoRoot, relative);
      if (!fs.existsSync(file)) return null;
      return {
        path: relative,
        sha256: sha256(file),
        sizeBytes: fs.statSync(file).size,
      };
    })
    .filter(Boolean);
}

function copyAttachment(repoRoot, attachmentsDir, kind, file) {
  if (!file) return null;
  const absolute = path.resolve(file);
  if (!fs.existsSync(absolute) || !fs.statSync(absolute).isFile()) {
    return {
      kind,
      source: file,
      copied: false,
      missing: true,
    };
  }
  const targetName = sanitizeFileName(kind + "-" + path.basename(absolute));
  const target = path.join(attachmentsDir, targetName);
  fs.copyFileSync(absolute, target);
  return {
    kind,
    source: relativePath(repoRoot, absolute),
    path: relativePath(repoRoot, target),
    copied: true,
    sha256: sha256(target),
    sizeBytes: fs.statSync(target).size,
  };
}

function walk(root) {
  const result = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const absolute = path.join(root, entry.name);
    if (entry.isDirectory()) {
      result.push(...walk(absolute));
    } else if (entry.isFile()) {
      result.push(absolute);
    }
  }
  return result;
}

function inferAbi(file) {
  const parts = file.split(/[\\/]/);
  return parts.find((part) => ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"].includes(part)) || "unknown";
}

function gitInfo(repoRoot) {
  return {
    commit: runGit(repoRoot, ["rev-parse", "--short=12", "HEAD"]),
    branch: runGit(repoRoot, ["branch", "--show-current"]),
  };
}

function runGit(repoRoot, args) {
  const result = childProcess.spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
  });
  return result.status === 0 ? result.stdout.trim() : "";
}

function writeJsonFile(file, json, repoRoot) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(json, null, 2) + "\n");
  return relativePath(repoRoot, file);
}

function readJson(file) {
  return JSON.parse(stripBom(fs.readFileSync(file, "utf8")));
}

function stripBom(value) {
  return value.replace(/^\uFEFF/, "");
}

function parseJsonValue(value, fallback) {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  if (typeof value === "object") {
    return value;
  }
  try {
    return JSON.parse(String(value));
  } catch (_) {
    return fallback;
  }
}

function numberOrNull(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function renderBundleReadme(manifest) {
  return [
    "# Final Crash Evidence Bundle",
    "",
    "Generated by `collectNodeCrashBundle` for Phase 11 crash diagnostics.",
    "",
    `- Runtime slot: ${manifest.runtimeDescriptor.runtimeSlot}`,
    `- Adapter: ${manifest.runtimeDescriptor.adapter}`,
    `- Attachments: ${manifest.attachments.length}`,
    `- Native libraries: ${manifest.nativeLibraryHashes.length}`,
    `- Automatic collection: ${manifest.automaticCollection}`,
    "",
    "The bundle is schema-valid evidence only. It remains blocked for final graduation until safe crash markers, automatic next-launch collection, timeline tail capture, resource snapshots, and chaos injection are implemented.",
    "",
  ].join("\n");
}

function renderCollectionMarkdown(report) {
  return [
    "# Final Crash Bundle Collection",
    "",
    `- Decision: ${report.decision.status}`,
    `- Manifest: ${report.bundle.manifest}`,
    `- Attachments: ${report.bundle.attachments}`,
    `- Native libraries: ${report.bundle.nativeLibraryCount}`,
    "",
  ].join("\n");
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function sanitizeFileName(value) {
  return value.replace(/[^a-zA-Z0-9._-]/g, "_");
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["crash-injected", "safe-crash-marker", "automatic-collection"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node collect-node-crash-bundle.js [--repo-root dir] [--jni-libs-root dir] [--report-dir dir] [--bundle-dir dir] [--logcat file] [--tombstone file] [--bugreport file] [--resource-snapshot file] [--timeline-tail file]");
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

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

main();
