#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-storage-sqlite-verification";
const STORAGE_SQLITE_MODULES = ["storage", "storages", "database", "sqlite"];
const RHINO_GLOBALS = ["storages", "sqlite"];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  storageTypes: "docs/nodejs/types/autojs6-node/storage.d.ts",
  storagesTypes: "docs/nodejs/types/autojs6-node/storages.d.ts",
  databaseTypes: "docs/nodejs/types/autojs6-node/database.d.ts",
  sqliteTypes: "docs/nodejs/types/autojs6-node/sqlite.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  nodeExamplesGradle: "app/node-examples.gradle.kts",
  typeSmokeSource: "sample/nodejs/typescript-smoke/src/main.ts",
  design: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  matrix: "docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  gradle: "app/node-phase11-gate.gradle.kts",
};

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const storageSqliteBlock = extractStorageSqliteBlock(files.runtime.text);
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, storageSqliteBlock, compatBlock),
    ...(await buildDynamicChecks(storageSqliteBlock, compatBlock)),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-storage-sqlite-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatStorageSqlite",
    modules: STORAGE_SQLITE_MODULES,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacadePresent: checkById(checks, "runtime-storage-sqlite-facades"),
      storageIsolationWorks: checkById(checks, "dynamic-storage-isolation"),
      sqliteCallableWorks: checkById(checks, "dynamic-sqlite-callable"),
      databaseCloseWorks: checkById(checks, "dynamic-database-close"),
      transactionErrorsStable: checkById(checks, "dynamic-transaction-errors"),
      explicitGlobalInstallWorks: checkById(checks, "dynamic-explicit-global-storage-sqlite"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat storage/sqlite verification: " + report.decision.status);
  console.log("Rhino compat storage/sqlite modules: " + STORAGE_SQLITE_MODULES.join(", "));
  console.log("Rhino compat storage/sqlite policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, storageSqliteBlock, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const moduleById = new Map((manifest.modules || []).map((module) => [module.id, module]));
  const statusEntry = (moduleId, entryId) => {
    const module = moduleById.get(moduleId);
    const entry = module && (module.apiEntries || []).find((item) => item.id === entryId);
    return entry ? entry.status : "";
  };
  return [
    check("runtime-storage-sqlite-facades", storageSqliteBlock.includes("function __autojs6_limited_storage") && storageSqliteBlock.includes("function __autojs6_limited_database"), "runtime must define storage/storages and database/sqlite facade functions"),
    check("runtime-storage-rhino-aliases", storageSqliteBlock.includes("putSync: put") && storageSqliteBlock.includes("removeSync: remove") && storageSqliteBlock.includes("clearSync: clear") && storageSqliteBlock.includes("selfRemoveSync: selfRemove"), "storage facade must expose async-compatible Rhino sync aliases"),
    check("runtime-storage-unsupported-enumeration", storageSqliteBlock.includes('function names()') && storageSqliteBlock.includes('function all()') && storageSqliteBlock.includes("storages.names") && storageSqliteBlock.includes("storages.all"), "storage namespace enumeration must have stable unsupported paths"),
    check("runtime-sqlite-callable", storageSqliteBlock.includes("const sqlite = function") && storageSqliteBlock.includes('Object.defineProperty(sqlite, "open"'), "sqlite facade must be callable and keep sqlite.open"),
    check("runtime-require-storage-sqlite", STORAGE_SQLITE_MODULES.every((name) => files.runtime.text.includes(`name === "${name}"`)), "runtime must resolve and require S11-13 storage/sqlite modules"),
    check("runtime-compat-storage-sqlite", RHINO_GLOBALS.every((name) => compatBlock.includes(`"${name}"`)) && compatBlock.includes("storage: __autojs6_limited_storage") && compatBlock.includes("sqlite: __autojs6_limited_database"), "autojs6:compat must expose and install S11-13 storage/sqlite globals"),
    check("runtime-status-s11-13", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-13"/.test(compatBlock) && !/pendingTasks:\s*Object\.freeze\(\[[^\]]*"S11-13"/.test(compatBlock), "compat status must mark S11-13 implemented and remove it from pending tasks"),
    check("types-storage-aliases", files.storageTypes.text.includes("putSync(") && files.storageTypes.text.includes("removeSync(") && files.storageTypes.text.includes("names(): never") && files.storageTypes.text.includes("all(): never"), "storage types must expose async-compatible aliases and unsupported enumeration"),
    check("types-sqlite-callable", files.sqliteTypes.text.includes("interface SQLiteModule") && files.sqliteTypes.text.includes("(name: string") && files.sqliteTypes.text.includes("open(name: string"), "sqlite types must declare callable sqlite plus sqlite.open"),
    check("types-compat-storage-sqlite", STORAGE_SQLITE_MODULES.every((name) => files.compatTypes.text.includes(`readonly ${name}:`)), "rhino_compat.d.ts must expose S11-13 storage/sqlite properties"),
    check("types-index-storage-sqlite", STORAGE_SQLITE_MODULES.every((name) => files.indexTypes.text.includes(`./${name}.d.ts`)), "index.d.ts must reference S11-13 related type declarations"),
    check("type-gate-storage-sqlite", ["database", "sqlite"].every((name) => files.nodeExamplesGradle.text.includes(`"${name}"`)), "verifyAutoJs6NodeTypeDeclarations must list database/sqlite declarations"),
    check("type-smoke-storage-sqlite", ["database", "sqlite"].every((name) => files.typeSmokeSource.text.includes(`require("${name}")`)) && files.typeSmokeSource.text.includes("transaction(") && files.typeSmokeSource.text.includes(".close("), "typescript-smoke sample must import and exercise database/sqlite declarations"),
    check("status-storages", moduleById.get("storages") && moduleById.get("storages").status === "partial" && statusEntry("storages", "storages.create") === "supported_async" && statusEntry("storages", "storages.names") === "unsupported" && statusEntry("storages", "storages.removeSync") === "supported_async", "status manifest must mark storages as partial with async mappings and unsupported enumeration"),
    check("status-sqlite", moduleById.get("sqlite") && moduleById.get("sqlite").status === "supported_async" && statusEntry("sqlite", "sqlite.__module") === "supported_async" && statusEntry("sqlite", "sqlite.open") === "supported_async", "status manifest must mark sqlite callable/open facade supported_async"),
    check("type-readme-s11-13", files.typeReadme.text.includes("S11-13") && files.typeReadme.text.includes("storages") && files.typeReadme.text.includes("sqlite"), "type README must document S11-13 storage/sqlite modules"),
    check("design-doc-s11-13", files.design.text.includes("S11-13") && files.design.text.includes("storages") && files.design.text.includes("sqlite") && files.design.text.includes("transaction"), "design doc must document S11-13 storage/sqlite facade and transaction path"),
    check("matrix-s11-13", /\| \d+ \| storages \| storages \| partial \|/.test(files.matrix.text) && /\| \d+ \| sqlite \| sqlite \| supported_async \|/.test(files.matrix.text), "compat matrix must reflect S11-13 storage/sqlite modules"),
    check("task-plan-s11-13-complete", /\[x\]\s+S11-13/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-13 complete"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatStorageSqlite") && files.gradle.text.includes("verify-rhino-compat-storage-sqlite.js"), "Gradle must register verifyNodeRhinoCompatStorageSqlite"),
  ];
}

async function buildDynamicChecks(storageSqliteBlock, compatBlock) {
  if (!storageSqliteBlock || !compatBlock) {
    return [check("dynamic-blocks", false, "runtime storage/sqlite or compat block could not be extracted")];
  }
  const harness = createHarness(storageSqliteBlock, compatBlock);
  const checks = [];
  checks.push(check("dynamic-no-default-storage-sqlite", !("storages" in harness.globalObject) && !("sqlite" in harness.globalObject), "require autojs6:compat must not install S11-13 globals by default"));
  checks.push(check("dynamic-status", harness.compat.status().implementedTasks.includes("S11-13") && !harness.compat.status().pendingTasks.includes("S11-13"), "compat status must expose S11-13 as implemented"));

  const alpha = harness.storages.create("alpha");
  const beta = harness.storages.create("beta");
  await alpha.put("key", "a");
  await beta.put("key", "b");
  await alpha.putSync("sync", true);
  await alpha.removeSync("sync");
  await harness.storages.removeSync("alpha");
  const storageCalls = harness.calls.filter((item) => item.moduleName === "storages");
  checks.push(check("dynamic-storage-isolation", storageCalls.some((item) => item.args[0] === "alpha" && item.methodName === "put") && storageCalls.some((item) => item.args[0] === "beta" && item.methodName === "put") && storageCalls.some((item) => item.args[0] === "alpha" && item.methodName === "clear"), "storages facade must keep store names isolated and map removeSync through async clear"));
  checks.push(check("dynamic-storage-unsupported-enumeration", throwsCode(() => harness.storages.names(), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED") && throwsCode(() => harness.storages.all(), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"), "storages.names/all must return stable unsupported errors"));

  const db = await harness.sqlite("compat_db");
  await db.exec("create table t (id integer)", []);
  const tx = await db.transaction([
    { type: "run", sql: "insert into t (id) values (?)", params: [1] },
    { type: "get", sql: "select id from t where id = ?", params: [1] },
  ]);
  checks.push(check("dynamic-sqlite-callable", db.name === "compat_db" && Array.isArray(tx) && tx.length === 2 && harness.calls.some((item) => item.moduleName === "sqlite" && item.methodName === "open"), "sqlite facade must be callable and route through sqlite.open bridge"));
  const closed = await db.close();
  const closedAgain = await db.close();
  checks.push(check("dynamic-database-close", closed === true && closedAgain === false && throwsCode(() => db.exec("select 1"), "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST"), "database handles must close once and reject later operations"));
  checks.push(check("dynamic-transaction-errors", throwsCode(() => harness.database.open("../escape"), "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED") && throwsCode(() => db.transaction(function noop() {}), "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST") && throwsCode(() => db.transaction([{ type: "drop", sql: "select 1" }]), "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST"), "database path and transaction error paths must be stable"));

  const install = harness.compat.install({ globals: true });
  checks.push(check("dynamic-explicit-global-storage-sqlite", RHINO_GLOBALS.every((name) => install.globals.includes(name)) && harness.globalObject.storages === harness.storages && harness.globalObject.sqlite === harness.sqlite, "compat.install({ globals: true }) must explicitly install S11-13 globals"));
  return checks;
}

function createHarness(storageSqliteBlock, compatBlock) {
  const source = `
    (function() {
      const __calls = [];
      let __autojs6_limited_storage_cache = null;
      let __autojs6_limited_storages_cache = null;
      let __autojs6_limited_database_cache = null;
      let __autojs6_limited_sqlite_cache = null;
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const __moduleStubs = {
        files: Object.freeze({ path() { return null; } }),
        base64: Object.freeze({ encode() { return ""; }, decode() { return ""; } }),
        colors: Object.freeze({ rgb() { return 0; } }),
        formatter: Object.freeze({ bytes() { return ""; } }),
        converter: Object.freeze({ bytes() { return 0; } }),
        mime: Object.freeze(function mime() { return Object.freeze({ type: "", subtype: "", parameters: Object.freeze({}) }); }),
        nanoid: Object.freeze(function nanoid() { return ""; }),
        util: Object.freeze({ format(value) { return String(value); }, inspect(value) { return String(value); } }),
        opencc: Object.freeze(function opencc() { return ""; }),
        pinyin: Object.freeze(function pinyin() { return []; }),
        pinyin4j: Object.freeze(function pinyin4j() { return ""; }),
        console: Object.freeze({ log() {}, info() {}, warn() {}, error() {}, verbose() {}, print() {}, show() { return this; } }),
        timers: Object.freeze({
          setTimeout() { return { type: "timeout" }; },
          clearTimeout() {},
          setInterval() { return { type: "interval" }; },
          clearInterval() {},
          setImmediate() { return { type: "immediate" }; },
          clearImmediate() {}
        }),
        app: Object.freeze({
          packageName: "org.autojs.autojs6",
          versionName: "6.7.1",
          versionCode: 3924,
          launchPackage() { return Promise.resolve(true); },
          launchApp() { return Promise.resolve(true); },
          openAppSetting() { return Promise.resolve(undefined); },
          startActivity() { return Promise.resolve(undefined); }
        }),
        device: Object.freeze({
          sdkInt: 36,
          width: 1080,
          height: 2400,
          density: 3,
          isScreenOn() { return Promise.resolve(true); },
          wakeUp() { return Promise.resolve(undefined); },
          vibrate() { return Promise.resolve(undefined); }
        }),
        shell: Object.freeze({
          exec(command) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(command), stderr: "" })); },
          execFile(path) { return Promise.resolve(Object.freeze({ code: 0, stdout: String(path), stderr: "" })); },
          checkAccess(options) { return Object.freeze({ shell: true, root: !!(options && options.root === true), shizuku: false, missingCapabilities: Object.freeze([]) }); },
          setDefaultTimeout(ms) { return Number(ms); }
        })
      };
      function __autojs6_define_error_property(error, name, value) {
        Object.defineProperty(error, name, { value, configurable: true, enumerable: true, writable: true });
      }
      function __autojs6_error(message, code, autojs6Code) {
        const error = new Error(message);
        __autojs6_define_error_property(error, "code", code);
        if (autojs6Code || (typeof code === "string" && code.indexOf("ERR_AUTOJS6_") === 0)) {
          __autojs6_define_error_property(error, "autojs6Code", autojs6Code || code);
        }
        return error;
      }
      function __autojs6_bridge_error(message, code, moduleName, methodName) {
        const error = __autojs6_error(message, code);
        __autojs6_define_error_property(error, "module", moduleName);
        __autojs6_define_error_property(error, "method", methodName);
        return error;
      }
      function __autojs6_bridge_timeout_ms(value) {
        return value === undefined || value === null ? 5000 : Number(value);
      }
      function __autojs6_bridge_signal_from_options() {
        return undefined;
      }
      function __autojs6_call_autojs(moduleName, methodName, args, options) {
        __calls.push({ moduleName, methodName, args: Array.isArray(args) ? args.slice() : [], options });
        if (moduleName === "storage" || moduleName === "storages") {
          if (methodName === "get") return Promise.resolve({ found: args[1] === "present", value: "value" });
          if (methodName === "keys") return Promise.resolve(["present", "other"]);
          if (methodName === "contains") return Promise.resolve(args[1] === "present");
          return Promise.resolve(true);
        }
        if (moduleName === "database" || moduleName === "sqlite") {
          if (methodName === "open") return Promise.resolve({ id: moduleName + "-handle", name: args[0].name });
          if (methodName === "run") return Promise.resolve({ rowsAffected: 1, insertId: null });
          if (methodName === "get") return Promise.resolve({ id: 1 });
          if (methodName === "all") return Promise.resolve([{ id: 1 }]);
          if (methodName === "transaction") {
            return Promise.resolve(args[1].map(function(operation) {
              if (operation.type === "run") return { rowsAffected: 1, insertId: null };
              if (operation.type === "get") return { id: 1 };
              if (operation.type === "all") return [{ id: 1 }];
              return undefined;
            }));
          }
          if (methodName === "close") return Promise.resolve(true);
        }
        return Promise.resolve(undefined);
      }
      function __autojs6_limited_files() { return __moduleStubs.files; }
      function __autojs6_limited_base64() { return __moduleStubs.base64; }
      function __autojs6_limited_colors() { return __moduleStubs.colors; }
      function __autojs6_limited_formatter() { return __moduleStubs.formatter; }
      function __autojs6_limited_converter() { return __moduleStubs.converter; }
      function __autojs6_limited_mime() { return __moduleStubs.mime; }
      function __autojs6_limited_nanoid() { return __moduleStubs.nanoid; }
      function __autojs6_limited_util() { return __moduleStubs.util; }
      function __autojs6_limited_opencc() { return __moduleStubs.opencc; }
      function __autojs6_limited_pinyin() { return __moduleStubs.pinyin; }
      function __autojs6_limited_pinyin4j() { return __moduleStubs.pinyin4j; }
      function __autojs6_limited_console() { return __moduleStubs.console; }
      function __autojs6_limited_timers() { return __moduleStubs.timers; }
      function __autojs6_limited_app() { return __moduleStubs.app; }
      function __autojs6_limited_device() { return __moduleStubs.device; }
      function __autojs6_limited_shell() { return __moduleStubs.shell; }
      function __autojs6_limited_toast() { return { showToast() { return Promise.resolve(); } }; }
      function __autojs6_limited_clipboard() { return { setText() { return Promise.resolve(); }, getText() { return Promise.resolve(""); } }; }
      function __autojs6_define_global(name, value) {
        Object.defineProperty(globalThis, name, { value, configurable: true, enumerable: false, writable: false });
      }
      ${storageSqliteBlock}
      ${compatBlock}
      return {
        storage: __autojs6_limited_storage("storage"),
        storages: __autojs6_limited_storage("storages"),
        database: __autojs6_limited_database("database"),
        sqlite: __autojs6_limited_database("sqlite"),
        compat: __autojs6_limited_rhino_compat(),
        calls: __calls,
        globalObject: globalThis
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Array,
    Error,
    JSON,
    Math,
    Number,
    Object,
    Promise,
    RegExp,
    String,
    console: { log() {} },
    globalThis: {},
    setTimeout,
  });
}

function extractStorageSqliteBlock(runtime) {
  const start = runtime.indexOf("function __autojs6_storage_options");
  const end = runtime.indexOf("  function __autojs6_notifications_options", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function extractCompatBlock(runtime) {
  const start = runtime.indexOf("const __autojs6_rhino_compat_error_codes");
  const end = runtime.indexOf("  function __autojs6_limited_work_manager()", start);
  if (start < 0 || end <= start) {
    return "";
  }
  return runtime.slice(start, end);
}

function throwsCode(action, expectedCode) {
  try {
    const result = action();
    if (result && typeof result.then === "function") {
      return false;
    }
    return false;
  } catch (error) {
    return error && (error.code === expectedCode || error.autojs6Code === expectedCode);
  }
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
    "# Rhino Compat Storage/SQLite Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Modules: ${report.modules.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Runtime facade present: ${report.decision.runtimeFacadePresent}`,
    `- Storage isolation works: ${report.decision.storageIsolationWorks}`,
    `- SQLite callable works: ${report.decision.sqliteCallableWorks}`,
    `- Database close works: ${report.decision.databaseCloseWorks}`,
    `- Transaction errors stable: ${report.decision.transactionErrorsStable}`,
    `- Explicit global install works: ${report.decision.explicitGlobalInstallWorks}`,
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
      console.log("Usage: node verify-rhino-compat-storage-sqlite.js [--repo-root dir] [--report-dir dir]");
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

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
