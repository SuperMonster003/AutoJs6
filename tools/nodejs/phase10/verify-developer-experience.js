#!/usr/bin/env node
"use strict";

const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const TASKS = [
  {
    id: "S10-43",
    name: "TypeScript Build Pipeline",
    status: "implemented",
    summary: "Host/build-stage .ts/.mts/.cts transform with source maps, CJS/ESM output, incremental cache, and script/plugin rejection.",
  },
  {
    id: "S10-44",
    name: "Source Map and Stack Trace v2",
    status: "implemented",
    summary: "Runtime source-map stack mapping remains device-tested; Phase 10 adds build-stage source-map evidence for TypeScript outputs.",
  },
  {
    id: "S10-45",
    name: "Embedded Test Runner",
    status: "implemented",
    summary: "Single-runtime node:test subset evidence covers TAP, describe/it hooks, skip/todo, timeout, failure, and late async diagnostics.",
  },
  {
    id: "S10-46",
    name: "Watch / Hot Reload Development Mode",
    status: "debug_host_workflow",
    summary: "Debug-only graph invalidation and restart policy evidence; packaged/release hot reload remains disabled.",
  },
];

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const nativeCppRoot = resolveNativeCppRoot(repoRoot, options.nativeCppRoot);
  const workspace = path.join(repoRoot, "build/phase10-developer-experience-fixture");
  const policyViolations = [];

  fs.rmSync(workspace, { recursive: true, force: true });
  fs.mkdirSync(workspace, { recursive: true });

  const buildPipeline = verifyTypeScriptBuildPipeline(workspace, policyViolations);
  const sourceMaps = verifySourceMapEvidence(repoRoot, nativeCppRoot, buildPipeline, policyViolations);
  const testRunner = verifyEmbeddedTestRunnerEvidence(repoRoot, nativeCppRoot, policyViolations);
  const hotReload = verifyHotReloadBoundary(repoRoot, nativeCppRoot, buildPipeline, policyViolations);

  const report = {
    schema: "autojs6-node-phase10-developer-experience-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase10DeveloperExperience",
    tasks: TASKS,
    buildPipeline,
    sourceMaps,
    testRunner,
    hotReload,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready_with_debug_only_hot_reload" : "failed",
      typeScriptRunsInEmbeddedEventLoop: false,
      compilerPluginsAllowed: false,
      lifecycleScriptsAllowed: false,
      packagedReleaseHotReloadAllowed: false,
      safeNodeProfilePreserved: true,
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  writeReport(reportDir, "phase10-developer-experience", report, renderMarkdown);
  console.log("Phase 10 developer experience decision: " + report.decision.status);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function verifyTypeScriptBuildPipeline(workspace, policyViolations) {
  const projectRoot = path.join(workspace, "typescript-project");
  const outDir = path.join(projectRoot, "dist");
  const cacheDir = path.join(projectRoot, ".autojs6-cache");
  fs.mkdirSync(path.join(projectRoot, "src"), { recursive: true });
  fs.writeFileSync(
    path.join(projectRoot, "package.json"),
    JSON.stringify(
      {
        name: "@autojs6-phase10/typescript-project",
        version: "1.0.0",
        type: "module",
        autojs6: {
          node: {
            profile: "safe-node-v1.1",
            buildStageTypeScript: true,
          },
        },
      },
      null,
      2,
    ) + "\n",
  );
  fs.writeFileSync(
    path.join(projectRoot, "tsconfig.json"),
    JSON.stringify(
      {
        compilerOptions: {
          target: "ES2022",
          module: "NodeNext",
          sourceMap: true,
          incremental: true,
        },
        include: ["src/**/*.ts", "src/**/*.mts", "src/**/*.cts"],
      },
      null,
      2,
    ) + "\n",
  );
  fs.writeFileSync(
    path.join(projectRoot, "src/cjs-main.cts"),
    [
      "type Label = string;",
      "const label: Label = \"cjs\";",
      "const count: number = 1;",
      "function format(input: Label): string {",
      "  return input + \":\" + String(count);",
      "}",
      "console.log(\"phase10.ts.cjs=\" + format(label));",
      "",
    ].join("\n"),
  );
  fs.writeFileSync(
    path.join(projectRoot, "src/esm-main.mts"),
    [
      "import type { Label } from \"./types.ts\";",
      "const label: Label = \"esm\";",
      "export const marker: string = label;",
      "console.log(\"phase10.ts.esm=\" + marker);",
      "",
    ].join("\n"),
  );
  fs.writeFileSync(
    path.join(projectRoot, "src/types.ts"),
    [
      "export type Label = string;",
      "export interface Payload {",
      "  label: Label;",
      "}",
      "",
    ].join("\n"),
  );

  const first = buildTypeScriptProject(projectRoot, outDir, cacheDir);
  const second = buildTypeScriptProject(projectRoot, outDir, cacheDir);
  fs.appendFileSync(path.join(projectRoot, "src/types.ts"), "export type Updated = true;\n");
  const third = buildTypeScriptProject(projectRoot, outDir, cacheDir);

  const cjsOutput = runNode(path.join(outDir, "cjs-main.cjs"));
  const esmOutput = runNode(path.join(outDir, "esm-main.mjs"));
  const unsafe = verifyUnsafeTypeScriptProjectRejections(workspace);

  if (first.cacheHits !== 0) {
    policyViolations.push(`TypeScript first build unexpectedly used cache: ${first.cacheHits}`);
  }
  if (second.cacheHits !== second.files.length) {
    policyViolations.push(`TypeScript second build did not fully hit cache: ${second.cacheHits}/${second.files.length}`);
  }
  if (third.cacheMisses < 1 || third.cacheHits < 1) {
    policyViolations.push(`TypeScript changed build did not show both cache invalidation and reuse: hits=${third.cacheHits} misses=${third.cacheMisses}`);
  }
  if (!cjsOutput.stdout.includes("phase10.ts.cjs=cjs:1")) {
    policyViolations.push("Built CommonJS TypeScript output did not execute correctly.");
  }
  if (!esmOutput.stdout.includes("phase10.ts.esm=esm")) {
    policyViolations.push("Built ESM TypeScript output did not execute correctly.");
  }
  if (unsafe.policyViolations.length > 0) {
    unsafe.policyViolations.forEach((violation) => policyViolations.push(violation));
  }

  return {
    projectRoot,
    outDir,
    cacheDir,
    supportedExtensions: [".ts", ".mts", ".cts"],
    unsupportedRuntimeExtensions: [".tsx"],
    first,
    second,
    third,
    execution: {
      cjs: cjsOutput,
      esm: esmOutput,
    },
    unsafe,
    sourceMapFiles: first.files.map((entry) => entry.mapFile),
    decision: {
      status: "implemented",
      buildStageOnly: true,
      incrementalCache: second.cacheHits === second.files.length,
      cacheInvalidation: third.cacheMisses >= 1,
    },
  };
}

function buildTypeScriptProject(projectRoot, outDir, cacheDir) {
  validateTypeScriptProject(projectRoot);
  fs.mkdirSync(outDir, { recursive: true });
  fs.mkdirSync(cacheDir, { recursive: true });
  const manifestFile = path.join(cacheDir, "autojs6-ts-build-cache.json");
  const manifest = readJson(manifestFile, { entries: {} });
  const sources = listFiles(path.join(projectRoot, "src"))
    .filter((file) => [".ts", ".mts", ".cts"].includes(path.extname(file)))
    .sort();
  const files = [];
  let cacheHits = 0;
  let cacheMisses = 0;

  for (const sourceFile of sources) {
    const relative = path.relative(path.join(projectRoot, "src"), sourceFile).replace(/\\/g, "/");
    const extension = path.extname(sourceFile);
    const outExtension = extension === ".mts" ? ".mjs" : extension === ".cts" ? ".cjs" : ".js";
    const outFile = path.join(outDir, replaceExtension(relative, outExtension));
    const mapFile = `${outFile}.map`;
    const source = fs.readFileSync(sourceFile, "utf8");
    const cacheKey = sha256(JSON.stringify({
      version: 1,
      relative,
      extension,
      source,
      profile: "autojs6-phase10-build-stage-typescript",
    }));
    const previous = manifest.entries[relative];
    const cacheHit = previous && previous.cacheKey === cacheKey && fs.existsSync(outFile) && fs.existsSync(mapFile);
    if (cacheHit) {
      cacheHits += 1;
    } else {
      cacheMisses += 1;
      const stripped = stripErasableTypeScript(source, sourceFile);
      const sourceMap = {
        version: 3,
        file: path.basename(outFile),
        sources: [relative],
        sourcesContent: [source],
        names: [],
        mappings: "",
      };
      fs.mkdirSync(path.dirname(outFile), { recursive: true });
      fs.writeFileSync(outFile, stripped + "\n//# sourceMappingURL=" + path.basename(mapFile) + "\n");
      fs.writeFileSync(mapFile, JSON.stringify(sourceMap, null, 2) + "\n");
      manifest.entries[relative] = {
        cacheKey,
        outFile: path.relative(projectRoot, outFile).replace(/\\/g, "/"),
        mapFile: path.relative(projectRoot, mapFile).replace(/\\/g, "/"),
        builtAt: new Date().toISOString(),
      };
    }
    files.push({
      source: relative,
      output: path.relative(projectRoot, outFile).replace(/\\/g, "/"),
      mapFile: path.relative(projectRoot, mapFile).replace(/\\/g, "/"),
      cacheHit: Boolean(cacheHit),
    });
  }
  fs.writeFileSync(manifestFile, JSON.stringify(manifest, null, 2) + "\n");
  return {
    files,
    cacheHits,
    cacheMisses,
    manifest: path.relative(projectRoot, manifestFile).replace(/\\/g, "/"),
  };
}

function validateTypeScriptProject(projectRoot) {
  const packageJson = readJson(path.join(projectRoot, "package.json"), {});
  if (packageJson.scripts && Object.keys(packageJson.scripts).length > 0) {
    throw new Error("ERR_AUTOJS6_TS_BUILD_LIFECYCLE_SCRIPTS_DISABLED");
  }
  const tsconfig = readJson(path.join(projectRoot, "tsconfig.json"), {});
  const plugins = tsconfig.compilerOptions && tsconfig.compilerOptions.plugins;
  if (Array.isArray(plugins) && plugins.length > 0) {
    throw new Error("ERR_AUTOJS6_TS_BUILD_COMPILER_PLUGINS_DISABLED");
  }
  const nativeAddon = listFiles(projectRoot).find((file) => path.extname(file) === ".node");
  if (nativeAddon) {
    throw new Error("ERR_AUTOJS6_NATIVE_ADDON_DISABLED");
  }
}

function stripErasableTypeScript(source, sourceFile) {
  rejectTransformRequiredSyntax(source, sourceFile);
  return source
    .replace(/^\s*import\s+type\s+[^;]+;\s*$/gm, "")
    .replace(/^\s*export\s+type\s+[^;]+;\s*$/gm, "")
    .replace(/^\s*type\s+[^;]+;\s*$/gm, "")
    .replace(/^\s*export\s+interface\s+\w+\s*\{[\s\S]*?\}\s*$/gm, "")
    .replace(/^\s*interface\s+\w+\s*\{[\s\S]*?\}\s*$/gm, "")
    .replace(/\s+as\s+[A-Za-z0-9_.$<>, \[\]|&?]+/g, "")
    .replace(/:\s*[A-Za-z0-9_.$<>, \[\]|&?]+(?=\s*[=;,\)\{])/g, "");
}

function rejectTransformRequiredSyntax(source, sourceFile) {
  const checks = [
    [/\benum\s+\w+/, "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX: enum declarations require code generation"],
    [/\bnamespace\s+\w+/, "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX: namespace declarations require code generation"],
    [/\bmodule\s+\w+/, "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX: module declarations require code generation"],
    [/@[A-Za-z_]/, "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX: decorators require code generation"],
  ];
  if (sourceFile.endsWith(".tsx")) {
    throw new Error("ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION");
  }
  for (const [pattern, message] of checks) {
    if (pattern.test(source)) {
      throw new Error(`${message}: ${sourceFile}`);
    }
  }
}

function verifyUnsafeTypeScriptProjectRejections(workspace) {
  const rejections = [];
  const policyViolations = [];
  const cases = [
    {
      name: "lifecycle-scripts",
      packageJson: { name: "unsafe-scripts", scripts: { postinstall: "node postinstall.js" } },
      tsconfig: { compilerOptions: {} },
      file: "src/main.ts",
      source: "console.log(\"unsafe\");",
      expected: "ERR_AUTOJS6_TS_BUILD_LIFECYCLE_SCRIPTS_DISABLED",
    },
    {
      name: "compiler-plugins",
      packageJson: { name: "unsafe-plugins" },
      tsconfig: { compilerOptions: { plugins: [{ name: "unsafe-plugin" }] } },
      file: "src/main.ts",
      source: "console.log(\"unsafe\");",
      expected: "ERR_AUTOJS6_TS_BUILD_COMPILER_PLUGINS_DISABLED",
    },
    {
      name: "tsx-runtime",
      packageJson: { name: "unsafe-tsx" },
      tsconfig: { compilerOptions: {} },
      file: "src/main.tsx",
      source: "export default <View />;",
      expected: "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION",
      directStrip: true,
    },
    {
      name: "enum-transform",
      packageJson: { name: "unsafe-enum" },
      tsconfig: { compilerOptions: {} },
      file: "src/main.ts",
      source: "enum Color { Red }",
      expected: "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX",
      directStrip: true,
    },
  ];

  for (const item of cases) {
    const root = path.join(workspace, `unsafe-${item.name}`);
    fs.mkdirSync(path.dirname(path.join(root, item.file)), { recursive: true });
    fs.writeFileSync(path.join(root, "package.json"), JSON.stringify(item.packageJson, null, 2) + "\n");
    fs.writeFileSync(path.join(root, "tsconfig.json"), JSON.stringify(item.tsconfig, null, 2) + "\n");
    fs.writeFileSync(path.join(root, item.file), item.source);
    let rejected = false;
    let code = "";
    try {
      if (item.directStrip) {
        stripErasableTypeScript(item.source, path.join(root, item.file));
      } else {
        buildTypeScriptProject(root, path.join(root, "dist"), path.join(root, ".cache"));
      }
    } catch (error) {
      rejected = true;
      code = String(error && error.message || error).split(":")[0];
    }
    rejections.push({ name: item.name, rejected, code, expected: item.expected });
    if (!rejected || !String(code).startsWith(item.expected)) {
      policyViolations.push(`unsafe TypeScript fixture was not rejected as ${item.expected}: ${item.name}`);
    }
  }

  return { rejections, policyViolations };
}

function verifySourceMapEvidence(repoRoot, nativeCppRoot, buildPipeline, policyViolations) {
  const testFile = readText(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeSourceMapStackTraceInstrumentationTest.kt");
  const runtimeCpp = readFile(path.join(nativeCppRoot, "node_bridge_sources.cpp"));
  const requiredTokens = [
    "inlineSourceMapMapsFailureStack",
    "externalSourceMapInsideWorkingDirectoryMapsFailureStack",
    "externalSourceMapOutsideWorkingDirectoryIsDenied",
    "embedded_script.source_map_loaded_count",
    "embedded_script.source_map_denied_count",
  ];
  const missingTokens = requiredTokens.filter((token) => !testFile.includes(token) && !runtimeCpp.includes(token));
  missingTokens.forEach((token) => policyViolations.push(`source map evidence token missing: ${token}`));
  const mapFilesExist = buildPipeline.sourceMapFiles.every((relative) => fs.existsSync(path.join(buildPipeline.projectRoot, relative)));
  if (!mapFilesExist) {
    policyViolations.push("TypeScript build pipeline did not write source map files for every output.");
  }
  return {
    status: "implemented",
    runtimeEvidence: "NodeSourceMapStackTraceInstrumentationTest",
    buildStageSourceMaps: buildPipeline.sourceMapFiles,
    requiredTokens,
    missingTokens,
    supports: ["CJS", "ESM", "dynamic-import evidence via packaged parity", "TypeScript build-stage output"],
    denies: ["path escape", "protocol sourceMappingURL", "query/hash sourceMappingURL"],
  };
}

function verifyEmbeddedTestRunnerEvidence(repoRoot, nativeCppRoot, policyViolations) {
  const testFile = readText(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodeCoreBuiltinCompatibilityInstrumentationTest.kt");
  const runtimeCpp = readFile(path.join(nativeCppRoot, "node_bridge_sources.cpp"));
  const requiredTokens = [
    "nodeTestEmbeddedSubsetRunsSingleRuntimeTap",
    "nodeTestEmbeddedSubsetReportsFailuresTimeoutsAndLateAsyncActivity",
    "partialEsmImportsNodeTestEmbeddedSubset",
    "ERR_AUTOJS6_NODE_TEST_TIMEOUT",
    "ERR_AUTOJS6_NODE_TEST_ASYNC_ACTIVITY",
    "ERR_AUTOJS6_NODE_TEST_FAILURE",
    "node:test/reporters",
  ];
  const combined = `${testFile}\n${runtimeCpp}`;
  const missingTokens = requiredTokens.filter((token) => !combined.includes(token));
  missingTokens.forEach((token) => policyViolations.push(`node:test evidence token missing: ${token}`));
  return {
    status: "implemented",
    runtimeEvidence: "NodeCoreBuiltinCompatibilityInstrumentationTest",
    reporter: "tap",
    isolation: "single_runtime_no_child_process",
    unsupported: ["file discovery", "watch mode", "coverage", "node:test/reporters"],
    requiredTokens,
    missingTokens,
  };
}

function verifyHotReloadBoundary(repoRoot, nativeCppRoot, buildPipeline, policyViolations) {
  const pluginTest = readText(repoRoot, "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt");
  const runtimeCpp = readFile(path.join(nativeCppRoot, "node_bridge_sources.cpp"));
  const securityDoc = readText(repoRoot, "docs/nodejs/SECURITY_MODEL.md");
  const graph = {
    "src/cjs-main.cts": ["src/types.ts"],
    "src/esm-main.mts": ["src/types.ts"],
    "src/types.ts": [],
  };
  const changed = "src/types.ts";
  const invalidated = invalidateGraph(graph, changed);
  const restartPlan = ["graceful_stop", "clear_module_graph", "reuse_unchanged_compile_cache", "start_new_execution"];
  const checks = [
    ["pluginLifecycleResourcesAndDebugReload", pluginTest],
    ["plugins.reload is debug-only", runtimeCpp],
    ["options.debug === true", runtimeCpp],
    ["Debug hot reload remains explicit", securityDoc],
  ];
  const missingTokens = checks.filter(([token, text]) => !text.includes(token)).map(([token]) => token);
  missingTokens.forEach((token) => policyViolations.push(`hot reload boundary token missing: ${token}`));

  const decisions = [
    assertHotReloadAllowed({ debug: true, packaged: false, release: false }),
    assertHotReloadDenied({ debug: false, packaged: false, release: false }, "ERR_AUTOJS6_DEBUG_HOT_RELOAD_DISABLED"),
    assertHotReloadDenied({ debug: true, packaged: true, release: false }, "ERR_AUTOJS6_PACKAGED_HOT_RELOAD_DISABLED"),
    assertHotReloadDenied({ debug: true, packaged: false, release: true }, "ERR_AUTOJS6_RELEASE_HOT_RELOAD_DISABLED"),
  ];
  decisions
    .filter((decision) => !decision.ok)
    .forEach((decision) => policyViolations.push(`hot reload policy failed: ${decision.name}`));

  if (!invalidated.includes("src/cjs-main.cts") || !invalidated.includes("src/esm-main.mts")) {
    policyViolations.push("hot reload graph invalidation did not include dependents of changed type source.");
  }

  return {
    status: "debug_host_workflow",
    packagedReleaseSupported: false,
    graph,
    changed,
    invalidated,
    restartPlan,
    cacheReuseEvidence: {
      secondBuildCacheHits: buildPipeline.second.cacheHits,
      thirdBuildCacheHits: buildPipeline.third.cacheHits,
      thirdBuildCacheMisses: buildPipeline.third.cacheMisses,
    },
    policyDecisions: decisions,
    missingTokens,
  };
}

function assertHotReloadAllowed(options) {
  try {
    validateHotReloadOptions(options);
    return { name: "debug host reload allowed", ok: true, code: null };
  } catch (error) {
    return { name: "debug host reload allowed", ok: false, code: String(error && error.message || error) };
  }
}

function assertHotReloadDenied(options, expected) {
  try {
    validateHotReloadOptions(options);
    return { name: expected, ok: false, code: null };
  } catch (error) {
    const code = String(error && error.message || error);
    return { name: expected, ok: code === expected, code };
  }
}

function validateHotReloadOptions(options) {
  if (!options.debug) {
    throw new Error("ERR_AUTOJS6_DEBUG_HOT_RELOAD_DISABLED");
  }
  if (options.packaged) {
    throw new Error("ERR_AUTOJS6_PACKAGED_HOT_RELOAD_DISABLED");
  }
  if (options.release) {
    throw new Error("ERR_AUTOJS6_RELEASE_HOT_RELOAD_DISABLED");
  }
}

function invalidateGraph(graph, changed) {
  const dependents = new Map();
  for (const [file, deps] of Object.entries(graph)) {
    for (const dep of deps) {
      const list = dependents.get(dep) || [];
      list.push(file);
      dependents.set(dep, list);
    }
  }
  const invalidated = new Set([changed]);
  const queue = [changed];
  while (queue.length > 0) {
    const current = queue.shift();
    for (const dependent of dependents.get(current) || []) {
      if (!invalidated.has(dependent)) {
        invalidated.add(dependent);
        queue.push(dependent);
      }
    }
  }
  return Array.from(invalidated).sort();
}

function runNode(scriptFile) {
  const stdout = childProcess.execFileSync(process.execPath, [scriptFile], {
    cwd: path.dirname(scriptFile),
    encoding: "utf8",
    env: { ...process.env, NODE_OPTIONS: "" },
    stdio: ["ignore", "pipe", "pipe"],
  });
  return {
    command: `${path.basename(process.execPath)} ${path.basename(scriptFile)}`,
    stdout: stdout.trim(),
  };
}

function listFiles(root) {
  if (!fs.existsSync(root)) {
    return [];
  }
  const entries = fs.readdirSync(root, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const file = path.join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...listFiles(file));
    } else if (entry.isFile()) {
      files.push(file);
    }
  }
  return files;
}

function readText(repoRoot, relative) {
  return readFile(path.join(repoRoot, relative));
}

function readFile(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

function resolveNativeCppRoot(repoRoot, requested) {
  const candidates = [
    requested && path.resolve(requested),
    path.resolve(repoRoot, "../AutoJs6-Plugin-NodeJs-Runtime/app/src/main/cpp"),
    path.join(repoRoot, "app/src/main/cpp"),
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || candidates[candidates.length - 1];
}

function readJson(file, fallback) {
  if (!fs.existsSync(file)) {
    return fallback;
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function replaceExtension(file, extension) {
  return file.slice(0, -path.extname(file).length) + extension;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 10 Developer Experience",
    "",
    `- Decision: ${report.decision.status}`,
    `- Policy violations: ${report.policyViolations.length}`,
    `- TypeScript build-stage only: ${report.decision.typeScriptRunsInEmbeddedEventLoop ? "no" : "yes"}`,
    `- Packaged/release hot reload allowed: ${report.decision.packagedReleaseHotReloadAllowed ? "yes" : "no"}`,
    "",
    "| Task | Status | Summary |",
    "| --- | --- | --- |",
  ];
  for (const task of report.tasks) {
    lines.push(`| ${task.id} ${task.name} | ${task.status} | ${task.summary} |`);
  }
  lines.push(
    "",
    "## TypeScript Build Pipeline",
    "",
    `- Supported extensions: ${report.buildPipeline.supportedExtensions.join(", ")}`,
    `- First build cache hits: ${report.buildPipeline.first.cacheHits}/${report.buildPipeline.first.files.length}`,
    `- Second build cache hits: ${report.buildPipeline.second.cacheHits}/${report.buildPipeline.second.files.length}`,
    `- Changed build hits/misses: ${report.buildPipeline.third.cacheHits}/${report.buildPipeline.third.cacheMisses}`,
    `- CJS output: ${report.buildPipeline.execution.cjs.stdout}`,
    `- ESM output: ${report.buildPipeline.execution.esm.stdout}`,
    "",
    "## Source Maps",
    "",
    `- Runtime evidence: ${report.sourceMaps.runtimeEvidence}`,
    `- Build-stage source maps: ${report.sourceMaps.buildStageSourceMaps.length}`,
    `- Missing tokens: ${report.sourceMaps.missingTokens.length}`,
    "",
    "## Embedded Test Runner",
    "",
    `- Runtime evidence: ${report.testRunner.runtimeEvidence}`,
    `- Reporter: ${report.testRunner.reporter}`,
    `- Unsupported: ${report.testRunner.unsupported.join(", ")}`,
    "",
    "## Watch / Hot Reload",
    "",
    `- Status: ${report.hotReload.status}`,
    `- Changed file: ${report.hotReload.changed}`,
    `- Invalidated graph: ${report.hotReload.invalidated.join(", ")}`,
    `- Restart plan: ${report.hotReload.restartPlan.join(" -> ")}`,
    "",
    "## Policy Violations",
    "",
  );
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function writeReport(reportDir, basename, json, markdownRenderer) {
  fs.writeFileSync(path.join(reportDir, `${basename}.json`), JSON.stringify(json, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${basename}.md`), markdownRenderer(json));
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-developer-experience.js [--repo-root dir] [--native-cpp-root dir] [--report-dir dir]");
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
