#!/usr/bin/env node
"use strict";

const crypto = require("crypto");
const fs = require("fs");
const https = require("https");
const path = require("path");
const zlib = require("zlib");
const { fileURLToPath, pathToFileURL } = require("url");

const LOCKFILE_NAME = "autojs6-lock.json";
const REPORT_BASENAME = "phase10-package-manager-boundary";
const DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
const DEFAULT_MAX_TARBALL_BYTES = 16 * 1024 * 1024;
const DEFAULT_MAX_UNPACKED_BYTES = 32 * 1024 * 1024;
const DEFAULT_MAX_FILES = 1000;
const DISABLED_LIFECYCLE_SCRIPTS = new Set(["preinstall", "install", "postinstall", "prepare"]);
const UNSUPPORTED_REQUIRE_PATTERNS = [
  { id: "child_process", pattern: /\brequire\s*\(\s*["'](?:node:)?child_process["']\s*\)/ },
  { id: "raw_network", pattern: /\brequire\s*\(\s*["'](?:node:)?(?:net|tls|dns|dgram)["']\s*\)/ },
  { id: "worker_threads", pattern: /\brequire\s*\(\s*["'](?:node:)?worker_threads["']\s*\)/ },
  { id: "node_wasi", pattern: /\brequire\s*\(\s*["'](?:node:)?wasi["']\s*\)/ },
  { id: "private_binding", pattern: /\bprocess\.(?:binding|dlopen|_linkedBinding)\s*\(/ },
];

class PmError extends Error {
  constructor(code, message, details = {}) {
    super(message);
    this.name = "AutoJs6PmError";
    this.code = code;
    this.details = details;
  }
}

class AutoJs6PackageManager {
  constructor(options) {
    this.registry = options.registry;
    this.cacheDir = options.cacheDir;
    this.registryAllowlist = new Set(options.registryAllowlist || ["registry.npmjs.org"]);
    this.maxResponseBytes = options.maxResponseBytes || DEFAULT_MAX_RESPONSE_BYTES;
    this.maxTarballBytes = options.maxTarballBytes || DEFAULT_MAX_TARBALL_BYTES;
    this.maxUnpackedBytes = options.maxUnpackedBytes || DEFAULT_MAX_UNPACKED_BYTES;
    this.maxFiles = options.maxFiles || DEFAULT_MAX_FILES;
  }

  async installProject(sourceProjectRoot, outputProjectRoot) {
    const sourcePackageJson = path.join(sourceProjectRoot, "package.json");
    if (!fs.existsSync(sourcePackageJson)) {
      throw new PmError("ERR_AUTOJS6_PM_PACKAGE_JSON_MISSING", "Project package.json is required.", {
        projectRoot: sourceProjectRoot,
      });
    }
    replaceDir(outputProjectRoot);
    copyFileInto(sourcePackageJson, path.join(outputProjectRoot, "package.json"));
    const rootPackage = readJson(sourcePackageJson);
    const lock = createLock(rootPackage, {
      operation: "install",
      androidRuntimeInstall: false,
      hostManaged: true,
    });
    const diagnostics = [];
    await this.installDependencySet(
      outputProjectRoot,
      outputProjectRoot,
      rootPackage.dependencies || {},
      lock,
      diagnostics,
      {
        requester: "",
        optional: false,
        depth: 0,
      },
    );
    await this.installDependencySet(
      outputProjectRoot,
      outputProjectRoot,
      rootPackage.optionalDependencies || {},
      lock,
      diagnostics,
      {
        requester: "",
        optional: true,
        depth: 0,
      },
    );
    lock.diagnostics = diagnostics;
    writeJson(path.join(outputProjectRoot, LOCKFILE_NAME), lock);
    return lock;
  }

  async installPackage(projectRoot, name, range) {
    const sourceRoot = projectRoot + `.source-${process.pid}-${Date.now()}`;
    fs.mkdirSync(sourceRoot, { recursive: true });
    const packageJsonPath = path.join(projectRoot, "package.json");
    const packageJson = fs.existsSync(packageJsonPath)
      ? readJson(packageJsonPath)
      : {
          name: "autojs6-pm-ad-hoc-project",
          version: "0.0.0",
          dependencies: {},
        };
    packageJson.dependencies = packageJson.dependencies || {};
    packageJson.dependencies[name] = range;
    writeJson(path.join(sourceRoot, "package.json"), packageJson);
    try {
      return await this.installProject(sourceRoot, projectRoot);
    } finally {
      removeDir(sourceRoot);
    }
  }

  async updatePackage(projectRoot, name) {
    const packageJsonPath = path.join(projectRoot, "package.json");
    const packageJson = readJson(packageJsonPath);
    const range = packageJson.dependencies && packageJson.dependencies[name];
    if (!range) {
      throw new PmError("ERR_AUTOJS6_PM_DEPENDENCY_NOT_DECLARED", `Dependency ${name} is not declared.`, {
        name,
      });
    }
    const sourceRoot = projectRoot + `.source-${process.pid}-${Date.now()}`;
    fs.mkdirSync(sourceRoot, { recursive: true });
    writeJson(path.join(sourceRoot, "package.json"), packageJson);
    try {
      return await this.installProject(sourceRoot, projectRoot);
    } finally {
      removeDir(sourceRoot);
    }
  }

  async installFromTarball(projectRoot, tarballPath, name) {
    const outputRoot = path.join(projectRoot, "node_modules", ...name.split("/"));
    const tarballBuffer = fs.readFileSync(tarballPath);
    const integrity = integritySha512(tarballBuffer);
    const stagingDir = outputRoot + `.staging-${process.pid}-${Date.now()}`;
    safeExtractTgz(tarballBuffer, stagingDir, {
      maxFiles: this.maxFiles,
      maxUnpackedBytes: this.maxUnpackedBytes,
    });
    const packageJson = readJson(path.join(stagingDir, "package.json"));
    if (packageJson.name !== name) {
      removeDir(stagingDir);
      throw new PmError("ERR_AUTOJS6_PM_PACKAGE_NAME_MISMATCH", "Tarball package name does not match install target.", {
        expected: name,
        actual: packageJson.name,
      });
    }
    const compatibility = analyzePackage(stagingDir);
    if (compatibility.blocking) {
      removeDir(stagingDir);
      throw new PmError("ERR_AUTOJS6_PM_COMPATIBILITY_BLOCKED", "Tarball package violates Safe Node package policy.", {
        issues: compatibility.issues,
      });
    }
    atomicReplace(stagingDir, outputRoot);
    const lockPath = path.join(projectRoot, LOCKFILE_NAME);
    const lock = fs.existsSync(lockPath)
      ? readJson(lockPath)
      : createLock(readJson(path.join(projectRoot, "package.json")), {
          operation: "installFromTarball",
          androidRuntimeInstall: false,
          hostManaged: true,
        });
    const relative = toPosix(path.relative(projectRoot, outputRoot));
    lock.packages[relative] = {
      name,
      version: packageJson.version,
      requested: "tarball",
      resolved: pathToFileURL(path.resolve(tarballPath)).href,
      integrity,
      dependencies: packageJson.dependencies || {},
      optionalDependencies: packageJson.optionalDependencies || {},
      peerDependencies: packageJson.peerDependencies || {},
      compatibility,
      contentHash: packageTreeHash(outputRoot),
    };
    writeJson(lockPath, lock);
    return lock;
  }

  async installDependencySet(projectRoot, parentDir, dependencies, lock, diagnostics, context) {
    for (const [name, range] of Object.entries(dependencies)) {
      await this.installDependency(projectRoot, parentDir, name, range, lock, diagnostics, context);
    }
  }

  async installDependency(projectRoot, parentDir, name, range, lock, diagnostics, context) {
    const metadata = this.registry[name];
    if (!metadata) {
      if (context.optional) {
        diagnostics.push({
          level: "info",
          code: "ERR_AUTOJS6_PM_OPTIONAL_DEPENDENCY_UNAVAILABLE",
          package: name,
          range,
          requester: context.requester,
        });
        return null;
      }
      throw new PmError("ERR_AUTOJS6_PM_PACKAGE_NOT_FOUND", `Package metadata is unavailable: ${name}`, {
        name,
        range,
      });
    }
    const version = resolveVersion(metadata, range);
    if (!version) {
      if (context.optional) {
        diagnostics.push({
          level: "info",
          code: "ERR_AUTOJS6_PM_OPTIONAL_DEPENDENCY_RANGE_UNAVAILABLE",
          package: name,
          range,
          requester: context.requester,
        });
        return null;
      }
      throw new PmError("ERR_AUTOJS6_PM_VERSION_NOT_FOUND", `No version of ${name} satisfies ${range}.`, {
        name,
        range,
      });
    }
    const versionMetadata = metadata.versions[version];
    const packageDir = path.join(parentDir, "node_modules", ...name.split("/"));
    const relative = toPosix(path.relative(projectRoot, packageDir));
    if (lock.packages[relative]) {
      return lock.packages[relative];
    }
    const tarball = await this.fetchTarball(versionMetadata.dist.tarball);
    verifyIntegrity(tarball, versionMetadata.dist.integrity);
    const stagingDir = packageDir + `.staging-${process.pid}-${Date.now()}`;
    safeExtractTgz(tarball, stagingDir, {
      maxFiles: this.maxFiles,
      maxUnpackedBytes: this.maxUnpackedBytes,
    });
    const packageJson = readJson(path.join(stagingDir, "package.json"));
    if (packageJson.name !== name || packageJson.version !== version) {
      removeDir(stagingDir);
      throw new PmError("ERR_AUTOJS6_PM_PACKAGE_ID_MISMATCH", "Package tarball metadata does not match registry metadata.", {
        expected: { name, version },
        actual: { name: packageJson.name, version: packageJson.version },
      });
    }
    const compatibility = analyzePackage(stagingDir);
    if (compatibility.blocking) {
      removeDir(stagingDir);
      throw new PmError("ERR_AUTOJS6_PM_COMPATIBILITY_BLOCKED", `Package ${name}@${version} violates Safe Node package policy.`, {
        package: name,
        version,
        issues: compatibility.issues,
      });
    }
    atomicReplace(stagingDir, packageDir);
    const packageEntry = {
      name,
      version,
      requested: range,
      resolved: versionMetadata.dist.tarball,
      integrity: versionMetadata.dist.integrity,
      requester: context.requester,
      dependencies: packageJson.dependencies || {},
      optionalDependencies: packageJson.optionalDependencies || {},
      peerDependencies: packageJson.peerDependencies || {},
      compatibility,
      contentHash: packageTreeHash(packageDir),
    };
    lock.packages[relative] = packageEntry;
    for (const [peerName, peerRange] of Object.entries(packageJson.peerDependencies || {})) {
      diagnostics.push({
        level: "warning",
        code: "ERR_AUTOJS6_PM_PEER_DEPENDENCY_DIAGNOSTIC_ONLY",
        package: name,
        peer: peerName,
        range: peerRange,
      });
    }
    await this.installDependencySet(projectRoot, packageDir, packageJson.dependencies || {}, lock, diagnostics, {
      requester: relative,
      optional: false,
      depth: context.depth + 1,
    });
    await this.installDependencySet(projectRoot, packageDir, packageJson.optionalDependencies || {}, lock, diagnostics, {
      requester: relative,
      optional: true,
      depth: context.depth + 1,
    });
    return packageEntry;
  }

  async fetchTarball(tarballUrl) {
    if (tarballUrl.startsWith("file:")) {
      const file = fileURLToPath(tarballUrl);
      const stat = fs.statSync(file);
      if (stat.size > this.maxTarballBytes) {
        throw new PmError("ERR_AUTOJS6_PM_TARBALL_TOO_LARGE", "Tarball exceeds configured maximum size.", {
          tarballUrl,
          bytes: stat.size,
          maxBytes: this.maxTarballBytes,
        });
      }
      return fs.readFileSync(file);
    }
    const parsed = new URL(tarballUrl);
    if (parsed.protocol !== "https:") {
      throw new PmError("ERR_AUTOJS6_PM_REGISTRY_PROTOCOL_DISABLED", "Only https registry tarball URLs are allowed.", {
        tarballUrl,
      });
    }
    if (!this.registryAllowlist.has(parsed.hostname)) {
      throw new PmError("ERR_AUTOJS6_PM_REGISTRY_NOT_ALLOWED", "Registry host is not allowlisted.", {
        host: parsed.hostname,
      });
    }
    const cacheFile = path.join(this.cacheDir, sha256Text(tarballUrl) + ".tgz");
    if (fs.existsSync(cacheFile)) {
      return fs.readFileSync(cacheFile);
    }
    const buffer = await downloadHttps(parsed, {
      timeoutMs: 15000,
      maxBytes: this.maxResponseBytes,
    });
    fs.mkdirSync(this.cacheDir, { recursive: true });
    fs.writeFileSync(cacheFile, buffer);
    return buffer;
  }

  list(projectRoot) {
    const lock = readJson(path.join(projectRoot, LOCKFILE_NAME));
    return Object.entries(lock.packages)
      .filter(([relative]) => relative !== "")
      .map(([relative, entry]) => ({
        path: relative,
        name: entry.name,
        version: entry.version,
        status: entry.compatibility.status,
      }))
      .sort((a, b) => a.path.localeCompare(b.path));
  }

  verify(projectRoot) {
    const lock = readJson(path.join(projectRoot, LOCKFILE_NAME));
    const issues = [];
    for (const [relative, entry] of Object.entries(lock.packages)) {
      if (relative === "") {
        continue;
      }
      const packageDir = path.join(projectRoot, ...relative.split("/"));
      if (!fs.existsSync(packageDir)) {
        issues.push({
          code: "ERR_AUTOJS6_PM_LOCK_ENTRY_MISSING_ON_DISK",
          path: relative,
        });
        continue;
      }
      const actual = packageTreeHash(packageDir);
      if (actual !== entry.contentHash) {
        issues.push({
          code: "ERR_AUTOJS6_PM_PACKAGE_CONTENT_HASH_MISMATCH",
          path: relative,
          expected: entry.contentHash,
          actual,
        });
      }
      const compatibility = analyzePackage(packageDir);
      if (compatibility.blocking) {
        issues.push({
          code: "ERR_AUTOJS6_PM_COMPATIBILITY_BLOCKED",
          path: relative,
          issues: compatibility.issues,
        });
      }
    }
    return {
      ok: issues.length === 0,
      packages: Object.keys(lock.packages).length - 1,
      issues,
    };
  }

  remove(projectRoot, name) {
    const lockPath = path.join(projectRoot, LOCKFILE_NAME);
    const lock = readJson(lockPath);
    const removed = [];
    for (const [relative, entry] of Object.entries({ ...lock.packages })) {
      if (relative !== "" && entry.name === name) {
        removeDir(path.join(projectRoot, ...relative.split("/")));
        delete lock.packages[relative];
        removed.push(relative);
      }
    }
    writeJson(lockPath, lock);
    return {
      removed,
      ok: removed.length > 0,
    };
  }

  prune(projectRoot) {
    const lockPath = path.join(projectRoot, LOCKFILE_NAME);
    const lock = readJson(lockPath);
    const removed = [];
    const nodeModulesRoot = path.join(projectRoot, "node_modules");
    if (!fs.existsSync(nodeModulesRoot)) {
      return { ok: true, removed };
    }
    for (const packageDir of listInstalledPackageDirs(nodeModulesRoot)) {
      const relative = toPosix(path.relative(projectRoot, packageDir));
      if (!lock.packages[relative]) {
        removeDir(packageDir);
        removed.push(relative);
      }
    }
    return {
      ok: true,
      removed,
    };
  }
}

async function main() {
  const argv = process.argv.slice(2);
  const command = argv[0] || "verify-fixture";
  const options = parseArgs(argv.slice(1));
  if (command === "--help" || command === "-h") {
    printHelp();
    return;
  }
  if (command !== "verify-fixture") {
    throw new Error(`Unsupported command: ${command}`);
  }
  await verifyFixture(options);
}

async function verifyFixture(options) {
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const fixtureRoot = path.join(repoRoot, "sample/nodejs/autojs6-pm-fixture");
  const workRoot = path.resolve(options.workRoot || path.join(repoRoot, "build/tmp/nodejs/phase10/autojs6-pm"));
  removeDir(workRoot);
  fs.mkdirSync(workRoot, { recursive: true });
  const registry = buildFixtureRegistry(fixtureRoot, path.join(workRoot, "registry"));
  const manager = new AutoJs6PackageManager({
    registry,
    cacheDir: path.join(workRoot, "cache"),
    registryAllowlist: ["registry.autojs6.invalid"],
  });

  const installedProject = path.join(workRoot, "installed-project");
  const lock = await manager.installProject(path.join(fixtureRoot, "project"), installedProject);
  const list = manager.list(installedProject);
  const verify = manager.verify(installedProject);
  const removeProject = path.join(workRoot, "remove-project");
  copyDir(installedProject, removeProject);
  const remove = manager.remove(removeProject, "left-pad-lite");
  const prune = manager.prune(removeProject);
  const updateProject = path.join(workRoot, "update-project");
  copyDir(installedProject, updateProject);
  const updateLock = await manager.updatePackage(updateProject, "left-pad-lite");
  const tarballProject = path.join(workRoot, "tarball-project");
  fs.mkdirSync(tarballProject, { recursive: true });
  writeJson(path.join(tarballProject, "package.json"), {
    name: "autojs6-pm-direct-tarball",
    version: "0.0.0",
    dependencies: {},
  });
  const directTarball = fileURLToPath(registry["case-fold-lite"].versions["1.0.0"].dist.tarball);
  await manager.installFromTarball(tarballProject, directTarball, "case-fold-lite");
  const directVerify = manager.verify(tarballProject);
  const negativeTests = await runNegativeTests(manager, registry, workRoot, fixtureRoot);

  const policyViolations = [];
  if (!verify.ok) {
    policyViolations.push("installed fixture did not verify cleanly");
  }
  if (!directVerify.ok) {
    policyViolations.push("installFromTarball fixture did not verify cleanly");
  }
  if (!remove.ok || remove.removed.length !== 1 || prune.removed.length !== 0) {
    policyViolations.push("remove/prune operations did not preserve expected lockfile state");
  }
  if (list.length !== 3) {
    policyViolations.push(`expected 3 installed package entries, got ${list.length}`);
  }
  const leftPad = lock.packages["node_modules/left-pad-lite"];
  if (!leftPad || leftPad.version !== "1.1.0") {
    policyViolations.push("caret semver did not resolve left-pad-lite to 1.1.0");
  }
  const slugify = lock.packages["node_modules/slugify-lite"];
  if (!slugify || slugify.version !== "2.1.3") {
    policyViolations.push("tilde semver did not resolve slugify-lite to 2.1.3");
  }
  if (!lock.packages["node_modules/slugify-lite/node_modules/case-fold-lite"]) {
    policyViolations.push("nested node_modules dependency was not installed");
  }
  for (const test of negativeTests) {
    if (!test.passed) {
      policyViolations.push(`negative test failed: ${test.name}`);
    }
  }

  const report = {
    schema: "autojs6-node-phase10-package-manager-boundary-v1",
    generatedAt: new Date().toISOString(),
    task: "S10-21..S10-27 AutoJs6 Package Manager host-managed MVP",
    gate: "verifyNodePhase10Autojs6PmFixture",
    status: policyViolations.length === 0 ? "ready" : "failed",
    packageManager: {
      name: "autojs6-pm",
      mode: "host_managed_partial",
      androidRuntimeInstall: false,
      fullNpmCliCompatible: false,
      lifecycleScriptsExecuted: false,
      nativeAddonsEnabled: false,
      binExecutionEnabled: false,
      registryNetworkInGate: false,
    },
    supportedNow: [
      "local registry metadata fixture",
      "exact, caret, tilde, comparator range, and dist-tag resolution",
      "nested node_modules dependency layout",
      "sha512 integrity verification",
      "safe tgz extraction with Tar Slip and link rejection",
      "autojs6-lock.json generation",
      "install, installFromTarball, update, remove, list, verify, prune operations",
      "dependency compatibility analyzer for lifecycle scripts, native addons, raw network, child_process, worker_threads, node:wasi, and private bindings",
      "offline cache-ready tarball fetch path",
    ],
    deferred: [
      "Android runtime registry install UI",
      "online registry gate against npmjs.org",
      "hoisting",
      "package-manager plugin hooks",
      "full npm CLI behavior",
      "peer dependency installation",
    ],
    operations: {
      install: {
        ok: verify.ok,
        packages: list.length,
        lockfile: toPosix(path.relative(repoRoot, path.join(installedProject, LOCKFILE_NAME))),
      },
      list,
      verify,
      remove,
      prune,
      update: {
        ok: Boolean(updateLock.packages["node_modules/left-pad-lite"]),
        package: "left-pad-lite",
        version: updateLock.packages["node_modules/left-pad-lite"] && updateLock.packages["node_modules/left-pad-lite"].version,
      },
      installFromTarball: {
        ok: directVerify.ok,
        package: "case-fold-lite",
      },
    },
    compatibilityDiagnostics: lock.diagnostics,
    negativeTests,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "ready" : "failed",
      safeNodeProfilePreserved: true,
      scopedFsLoosened: false,
      moduleSourcesBudgetChanged: false,
      fakeProviderPromoted: false,
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  writeJson(path.join(reportDir, `${REPORT_BASENAME}.json`), report);
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderPackageManagerMarkdown(report));
  console.log(`Phase 10 package manager boundary decision: ${report.decision.status}`);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

async function runNegativeTests(manager, registry, workRoot, fixtureRoot) {
  const tests = [];
  await expectFailure(tests, "integrity mismatch", "ERR_AUTOJS6_PM_INTEGRITY_MISMATCH", async () => {
    const mutated = JSON.parse(JSON.stringify(registry));
    mutated["left-pad-lite"].versions["1.1.0"].dist.integrity = "sha512-" + Buffer.from("wrong").toString("base64");
    const badManager = new AutoJs6PackageManager({
      registry: mutated,
      cacheDir: path.join(workRoot, "bad-cache"),
    });
    await badManager.installProject(path.join(fixtureRoot, "project"), path.join(workRoot, "bad-integrity"));
  });
  await expectFailure(tests, "tar path escape", "ERR_AUTOJS6_PM_TAR_PATH_ESCAPE", async () => {
    const tarball = createMaliciousTarSlipTgz();
    safeExtractTgz(tarball, path.join(workRoot, "tar-slip"), {
      maxFiles: DEFAULT_MAX_FILES,
      maxUnpackedBytes: DEFAULT_MAX_UNPACKED_BYTES,
    });
  });
  await expectFailure(tests, "lifecycle script disabled", "ERR_AUTOJS6_PM_COMPATIBILITY_BLOCKED", async () => {
    await manager.installPackage(path.join(workRoot, "unsafe-script-project"), "unsafe-postinstall-lite", "1.0.0");
  });
  await expectFailure(tests, "native addon disabled", "ERR_AUTOJS6_PM_COMPATIBILITY_BLOCKED", async () => {
    await manager.installPackage(path.join(workRoot, "native-addon-project"), "native-addon-lite", "1.0.0");
  });
  await expectFailure(tests, "registry protocol disabled", "ERR_AUTOJS6_PM_REGISTRY_PROTOCOL_DISABLED", async () => {
    await manager.fetchTarball("http://registry.autojs6.invalid/package.tgz");
  });
  return tests;
}

async function expectFailure(tests, name, expectedCode, fn) {
  try {
    await fn();
    tests.push({
      name,
      expectedCode,
      passed: false,
      actualCode: null,
    });
  } catch (error) {
    tests.push({
      name,
      expectedCode,
      passed: error && error.code === expectedCode,
      actualCode: error && error.code ? error.code : String(error),
    });
  }
}

function buildFixtureRegistry(fixtureRoot, tarballRoot) {
  const registryRoot = path.join(fixtureRoot, "registry");
  const registry = {};
  for (const packageName of fs.readdirSync(registryRoot)) {
    const packageRoot = path.join(registryRoot, packageName);
    const versions = {};
    for (const version of fs.readdirSync(packageRoot)) {
      const sourceRoot = path.join(packageRoot, version, "package");
      const packageJson = readJson(path.join(sourceRoot, "package.json"));
      const tarballDir = path.join(tarballRoot, packageName, version);
      fs.mkdirSync(tarballDir, { recursive: true });
      const tarballFile = path.join(tarballDir, `${packageName}-${version}.tgz`);
      createPackageTgz(sourceRoot, tarballFile);
      const tarball = fs.readFileSync(tarballFile);
      versions[version] = {
        name: packageJson.name,
        version: packageJson.version,
        main: packageJson.main,
        type: packageJson.type,
        dependencies: packageJson.dependencies || {},
        optionalDependencies: packageJson.optionalDependencies || {},
        peerDependencies: packageJson.peerDependencies || {},
        dist: {
          tarball: pathToFileURL(tarballFile).href,
          integrity: integritySha512(tarball),
        },
      };
    }
    const sorted = Object.keys(versions).sort(compareVersions);
    registry[packageName] = {
      name: packageName,
      "dist-tags": {
        latest: sorted[sorted.length - 1],
      },
      versions,
    };
  }
  return registry;
}

function resolveVersion(metadata, range) {
  const distTag = metadata["dist-tags"] && metadata["dist-tags"][range];
  if (distTag) {
    return distTag;
  }
  const versions = Object.keys(metadata.versions).sort(compareVersions).reverse();
  return versions.find((version) => satisfiesRange(version, range)) || null;
}

function satisfiesRange(version, range) {
  const trimmed = String(range || "").trim();
  if (trimmed === "" || trimmed === "*") {
    return true;
  }
  if (/^\d+\.\d+\.\d+$/.test(trimmed)) {
    return version === trimmed;
  }
  if (trimmed.startsWith("^")) {
    const min = parseVersion(trimmed.slice(1));
    const current = parseVersion(version);
    const upper = min.major === 0
      ? { major: 0, minor: min.minor + 1, patch: 0 }
      : { major: min.major + 1, minor: 0, patch: 0 };
    return compareParsed(current, min) >= 0 && compareParsed(current, upper) < 0;
  }
  if (trimmed.startsWith("~")) {
    const min = parseVersion(trimmed.slice(1));
    const current = parseVersion(version);
    const upper = { major: min.major, minor: min.minor + 1, patch: 0 };
    return compareParsed(current, min) >= 0 && compareParsed(current, upper) < 0;
  }
  const comparators = trimmed.split(/\s+/).filter(Boolean);
  if (comparators.length > 1 || /^[<>]=?/.test(comparators[0])) {
    return comparators.every((comparator) => compareWithComparator(version, comparator));
  }
  return false;
}

function compareWithComparator(version, comparator) {
  const match = /^(>=|>|<=|<|=)?(\d+\.\d+\.\d+)$/.exec(comparator);
  if (!match) {
    return false;
  }
  const op = match[1] || "=";
  const comparison = compareVersions(version, match[2]);
  if (op === "=") return comparison === 0;
  if (op === ">=") return comparison >= 0;
  if (op === ">") return comparison > 0;
  if (op === "<=") return comparison <= 0;
  if (op === "<") return comparison < 0;
  return false;
}

function compareVersions(a, b) {
  return compareParsed(parseVersion(a), parseVersion(b));
}

function parseVersion(value) {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(String(value));
  if (!match) {
    throw new PmError("ERR_AUTOJS6_PM_UNSUPPORTED_SEMVER", `Unsupported semver value: ${value}`, {
      value,
    });
  }
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
  };
}

function compareParsed(a, b) {
  if (a.major !== b.major) return a.major - b.major;
  if (a.minor !== b.minor) return a.minor - b.minor;
  return a.patch - b.patch;
}

function analyzePackage(packageDir) {
  const packageJson = readJson(path.join(packageDir, "package.json"));
  const issues = [];
  const scripts = packageJson.scripts || {};
  for (const scriptName of Object.keys(scripts)) {
    if (DISABLED_LIFECYCLE_SCRIPTS.has(scriptName)) {
      issues.push({
        code: "ERR_AUTOJS6_PM_LIFECYCLE_SCRIPT_DISABLED",
        severity: "unsafe",
        path: "package.json",
        detail: scriptName,
      });
    }
  }
  for (const file of walkFiles(packageDir, { skipNodeModules: true })) {
    const relative = toPosix(path.relative(packageDir, file));
    const stat = fs.lstatSync(file);
    if (stat.isSymbolicLink()) {
      issues.push({
        code: "ERR_AUTOJS6_PM_SYMLINK_DISABLED",
        severity: "unsafe",
        path: relative,
      });
      continue;
    }
    if (relative.endsWith(".node") || relative.endsWith(".so") || relative.endsWith(".dll") || relative.endsWith(".dylib")) {
      issues.push({
        code: "ERR_AUTOJS6_PM_NATIVE_ADDON_DISABLED",
        severity: "unsafe",
        path: relative,
      });
    }
    if (path.basename(relative) === "binding.gyp") {
      issues.push({
        code: "ERR_AUTOJS6_PM_NODE_GYP_DISABLED",
        severity: "unsafe",
        path: relative,
      });
    }
    if (/\.(?:cjs|mjs|js|json)$/.test(relative)) {
      const text = fs.readFileSync(file, "utf8");
      for (const pattern of UNSUPPORTED_REQUIRE_PATTERNS) {
        if (pattern.pattern.test(text)) {
          issues.push({
            code: "ERR_AUTOJS6_PM_UNSUPPORTED_CAPABILITY",
            severity: "unsupported",
            path: relative,
            capability: pattern.id,
          });
        }
      }
    }
  }
  const requiredCapabilities = Array.from(new Set(issues.map((issue) => issue.capability).filter(Boolean))).sort();
  const blocking = issues.some((issue) => issue.severity === "unsafe" || issue.severity === "unsupported");
  return {
    status: blocking ? "unsafe" : requiredCapabilities.length > 0 ? "capability_required" : "compatible",
    blocking,
    entryFormat: packageJson.type === "module" ? "esm" : "commonjs",
    requiredCapabilities,
    issues,
  };
}

function createLock(rootPackage, options) {
  return {
    schema: "autojs6-lock-v1",
    lockfileVersion: 1,
    generatedAt: new Date().toISOString(),
    compatibleProfileVersion: "node-profile-v1.1-phase10",
    installationOptions: {
      scripts: false,
      binLinks: false,
      nativeAddons: false,
      symlinks: false,
      androidRuntimeInstall: options.androidRuntimeInstall === true,
      hostManaged: options.hostManaged === true,
      operation: options.operation,
    },
    packages: {
      "": {
        name: rootPackage.name || "",
        version: rootPackage.version || "",
        dependencies: rootPackage.dependencies || {},
        optionalDependencies: rootPackage.optionalDependencies || {},
      },
    },
    diagnostics: [],
  };
}

function safeExtractTgz(tarballBuffer, targetDir, options) {
  let tarBuffer;
  try {
    tarBuffer = zlib.gunzipSync(tarballBuffer);
  } catch (error) {
    throw new PmError("ERR_AUTOJS6_PM_TARBALL_DECODE_FAILED", "Tarball is not a valid gzip archive.", {
      message: error.message,
    });
  }
  removeDir(targetDir);
  fs.mkdirSync(targetDir, { recursive: true });
  let offset = 0;
  let files = 0;
  let totalBytes = 0;
  while (offset + 512 <= tarBuffer.length) {
    const header = tarBuffer.subarray(offset, offset + 512);
    offset += 512;
    if (header.every((byte) => byte === 0)) {
      break;
    }
    const rawName = readTarString(header, 0, 100);
    const prefix = readTarString(header, 345, 155);
    const name = prefix ? `${prefix}/${rawName}` : rawName;
    const typeflag = String.fromCharCode(header[156] || 48);
    const size = readTarOctal(header, 124, 12);
    const data = tarBuffer.subarray(offset, offset + size);
    offset += Math.ceil(size / 512) * 512;
    if (typeflag === "1" || typeflag === "2") {
      throw new PmError("ERR_AUTOJS6_PM_TAR_LINK_DISABLED", "Tarball link entries are disabled.", {
        entry: name,
      });
    }
    if (typeflag !== "0" && typeflag !== "\0" && typeflag !== "5") {
      throw new PmError("ERR_AUTOJS6_PM_TAR_ENTRY_UNSUPPORTED", "Tarball contains an unsupported entry type.", {
        entry: name,
        typeflag,
      });
    }
    const stripped = stripPackagePrefix(name);
    if (!stripped) {
      continue;
    }
    assertSafeRelativePath(stripped);
    const target = path.resolve(targetDir, ...stripped.split("/"));
    const targetRoot = path.resolve(targetDir);
    if (target !== targetRoot && !target.startsWith(targetRoot + path.sep)) {
      throw new PmError("ERR_AUTOJS6_PM_TAR_PATH_ESCAPE", "Tarball entry escapes the extraction root.", {
        entry: name,
      });
    }
    if (typeflag === "5") {
      fs.mkdirSync(target, { recursive: true });
      continue;
    }
    files += 1;
    totalBytes += size;
    if (files > options.maxFiles) {
      throw new PmError("ERR_AUTOJS6_PM_TARBALL_FILE_LIMIT", "Tarball exceeds configured file count.", {
        files,
        maxFiles: options.maxFiles,
      });
    }
    if (totalBytes > options.maxUnpackedBytes) {
      throw new PmError("ERR_AUTOJS6_PM_TARBALL_UNPACKED_SIZE_LIMIT", "Tarball exceeds configured unpacked size.", {
        bytes: totalBytes,
        maxBytes: options.maxUnpackedBytes,
      });
    }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, data);
  }
}

function createPackageTgz(sourceRoot, outputFile) {
  const files = walkFiles(sourceRoot, { skipNodeModules: false })
    .map((file) => toPosix(path.relative(sourceRoot, file)))
    .sort();
  const chunks = [];
  for (const relative of files) {
    const file = path.join(sourceRoot, ...relative.split("/"));
    const data = fs.readFileSync(file);
    chunks.push(createTarHeader(`package/${relative}`, data.length, "0"));
    chunks.push(data);
    const padding = (512 - (data.length % 512)) % 512;
    if (padding > 0) {
      chunks.push(Buffer.alloc(padding));
    }
  }
  chunks.push(Buffer.alloc(1024));
  fs.writeFileSync(outputFile, zlib.gzipSync(Buffer.concat(chunks), { mtime: 0 }));
}

function createMaliciousTarSlipTgz() {
  const data = Buffer.from("escape\n", "utf8");
  return zlib.gzipSync(Buffer.concat([
    createTarHeader("package/../escape.txt", data.length, "0"),
    data,
    Buffer.alloc((512 - (data.length % 512)) % 512),
    Buffer.alloc(1024),
  ]), { mtime: 0 });
}

function createTarHeader(name, size, typeflag) {
  const header = Buffer.alloc(512, 0);
  writeTarString(header, 0, 100, name);
  writeTarOctal(header, 100, 8, 0o644);
  writeTarOctal(header, 108, 8, 0);
  writeTarOctal(header, 116, 8, 0);
  writeTarOctal(header, 124, 12, size);
  writeTarOctal(header, 136, 12, 0);
  for (let index = 148; index < 156; index += 1) {
    header[index] = 0x20;
  }
  header[156] = typeflag.charCodeAt(0);
  writeTarString(header, 257, 6, "ustar");
  writeTarString(header, 263, 2, "00");
  let checksum = 0;
  for (const byte of header) {
    checksum += byte;
  }
  const checksumText = checksum.toString(8).padStart(6, "0");
  writeTarString(header, 148, 8, checksumText + "\0 ");
  return header;
}

function writeTarString(buffer, offset, length, value) {
  Buffer.from(String(value)).copy(buffer, offset, 0, Math.min(Buffer.byteLength(String(value)), length));
}

function writeTarOctal(buffer, offset, length, value) {
  const text = value.toString(8).padStart(length - 1, "0") + "\0";
  writeTarString(buffer, offset, length, text);
}

function readTarString(buffer, offset, length) {
  const slice = buffer.subarray(offset, offset + length);
  const zero = slice.indexOf(0);
  return slice.subarray(0, zero >= 0 ? zero : slice.length).toString("utf8");
}

function readTarOctal(buffer, offset, length) {
  const text = readTarString(buffer, offset, length).trim();
  return text ? parseInt(text, 8) : 0;
}

function stripPackagePrefix(name) {
  const normalized = toPosix(name);
  if (normalized === "package") {
    return "";
  }
  if (normalized.startsWith("package/")) {
    return normalized.slice("package/".length);
  }
  return normalized;
}

function assertSafeRelativePath(relative) {
  const normalized = toPosix(relative);
  if (normalized.startsWith("/") || /^[A-Za-z]:\//.test(normalized)) {
    throw new PmError("ERR_AUTOJS6_PM_TAR_PATH_ESCAPE", "Absolute tarball paths are disabled.", {
      entry: relative,
    });
  }
  if (normalized.split("/").some((part) => part === "..")) {
    throw new PmError("ERR_AUTOJS6_PM_TAR_PATH_ESCAPE", "Parent-directory tarball paths are disabled.", {
      entry: relative,
    });
  }
}

function verifyIntegrity(buffer, expected) {
  const actual = integritySha512(buffer);
  if (actual !== expected) {
    throw new PmError("ERR_AUTOJS6_PM_INTEGRITY_MISMATCH", "Tarball integrity does not match registry metadata.", {
      expected,
      actual,
    });
  }
}

function integritySha512(buffer) {
  return "sha512-" + crypto.createHash("sha512").update(buffer).digest("base64");
}

function packageTreeHash(packageDir) {
  const hash = crypto.createHash("sha256");
  for (const file of walkFiles(packageDir, { skipNodeModules: true }).sort()) {
    const relative = toPosix(path.relative(packageDir, file));
    hash.update(relative);
    hash.update("\0");
    hash.update(fs.readFileSync(file));
    hash.update("\0");
  }
  return "sha256-" + hash.digest("hex");
}

function walkFiles(root, options) {
  if (!fs.existsSync(root)) {
    return [];
  }
  const output = [];
  const entries = fs.readdirSync(root, { withFileTypes: true });
  for (const entry of entries) {
    const file = path.join(root, entry.name);
    if (options.skipNodeModules && entry.isDirectory() && entry.name === "node_modules") {
      continue;
    }
    if (entry.isDirectory()) {
      output.push(...walkFiles(file, options));
    } else {
      output.push(file);
    }
  }
  return output;
}

function listInstalledPackageDirs(nodeModulesRoot) {
  const output = [];
  if (!fs.existsSync(nodeModulesRoot)) {
    return output;
  }
  for (const entry of fs.readdirSync(nodeModulesRoot, { withFileTypes: true })) {
    const first = path.join(nodeModulesRoot, entry.name);
    if (!entry.isDirectory()) {
      continue;
    }
    if (entry.name.startsWith("@")) {
      for (const scoped of fs.readdirSync(first, { withFileTypes: true })) {
        if (scoped.isDirectory()) {
          output.push(path.join(first, scoped.name));
        }
      }
    } else {
      output.push(first);
    }
  }
  return output;
}

function downloadHttps(url, options) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, { timeout: options.timeoutMs }, (response) => {
      if (response.statusCode < 200 || response.statusCode >= 300) {
        response.resume();
        reject(new PmError("ERR_AUTOJS6_PM_REGISTRY_HTTP_STATUS", "Registry returned a non-success HTTP status.", {
          statusCode: response.statusCode,
        }));
        return;
      }
      const chunks = [];
      let bytes = 0;
      response.on("data", (chunk) => {
        bytes += chunk.length;
        if (bytes > options.maxBytes) {
          request.destroy(new PmError("ERR_AUTOJS6_PM_REGISTRY_RESPONSE_TOO_LARGE", "Registry response exceeds size limit.", {
            bytes,
            maxBytes: options.maxBytes,
          }));
          return;
        }
        chunks.push(chunk);
      });
      response.on("end", () => resolve(Buffer.concat(chunks)));
    });
    request.on("timeout", () => {
      request.destroy(new PmError("ERR_AUTOJS6_PM_REGISTRY_TIMEOUT", "Registry request timed out."));
    });
    request.on("error", reject);
  });
}

function copyFileInto(source, target) {
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(source, target);
}

function copyDir(source, target) {
  replaceDir(target);
  fs.cpSync(source, target, { recursive: true });
}

function replaceDir(target) {
  removeDir(target);
  fs.mkdirSync(target, { recursive: true });
}

function removeDir(target) {
  fs.rmSync(target, { recursive: true, force: true });
}

function atomicReplace(stagingDir, targetDir) {
  removeDir(targetDir);
  fs.mkdirSync(path.dirname(targetDir), { recursive: true });
  fs.renameSync(stagingDir, targetDir);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + "\n");
}

function sha256Text(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function toPosix(value) {
  return String(value).replace(/\\/g, "/");
}

function renderPackageManagerMarkdown(report) {
  const lines = [
    "# Phase 10 Package Manager Boundary",
    "",
    `- Decision: ${report.decision.status}`,
    `- Mode: ${report.packageManager.mode}`,
    `- Android runtime install: ${report.packageManager.androidRuntimeInstall}`,
    `- Full npm CLI compatible: ${report.packageManager.fullNpmCliCompatible}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Supported Now",
    "",
  ];
  report.supportedNow.forEach((item) => lines.push(`- ${item}`));
  lines.push("", "## Deferred", "");
  report.deferred.forEach((item) => lines.push(`- ${item}`));
  lines.push("", "## Installed Packages", "", "| Package | Version | Path | Status |", "| --- | --- | --- | --- |");
  report.operations.list.forEach((entry) => {
    lines.push(`| ${entry.name} | ${entry.version} | ${entry.path} | ${entry.status} |`);
  });
  lines.push("", "## Negative Tests", "", "| Test | Expected | Actual | Passed |", "| --- | --- | --- | --- |");
  report.negativeTests.forEach((test) => {
    lines.push(`| ${test.name} | ${test.expectedCode} | ${test.actualCode} | ${test.passed ? "yes" : "no"} |`);
  });
  lines.push("", "## Policy Violations", "");
  if (report.policyViolations.length === 0) {
    lines.push("None.");
  } else {
    report.policyViolations.forEach((violation) => lines.push(`- ${violation}`));
  }
  return lines.join("\n") + "\n";
}

function parseArgs(argv) {
  const options = {};
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
  console.log("Usage: node autojs6-pm.js verify-fixture [--repo-root dir] [--report-dir dir] [--work-root dir]");
}

main().catch((error) => {
  if (error && error.code) {
    console.error(`${error.code}: ${error.message}`);
    if (error.details) {
      console.error(JSON.stringify(error.details, null, 2));
    }
  } else {
    console.error(error && error.stack ? error.stack : String(error));
  }
  process.exit(1);
});
