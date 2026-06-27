#!/usr/bin/env node
"use strict";

const fs = require("fs");
const os = require("os");
const path = require("path");
const vm = require("vm");

const REPORT_BASENAME = "rhino-compat-files-facade-verification";
const REQUIRED_METHODS = ["path", "join", "read", "write", "exists", "listDir", "remove", "copy", "move"];

const FILES = {
  runtime: "app/src/main/cpp/node_bridge_sources.cpp",
  filesTypes: "docs/nodejs/types/autojs6-node/files.d.ts",
  compatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  indexTypes: "docs/nodejs/types/autojs6-node/index.d.ts",
  typeReadme: "docs/nodejs/types/autojs6-node/README.md",
  design: "docs/nodejs/design/RHINO_COMPAT_LOADER.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
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
  const filesBlock = extractFilesBlock(files.runtime.text);
  const compatBlock = extractCompatBlock(files.runtime.text);
  const checks = [
    ...buildStaticChecks(files, filesBlock, compatBlock),
    ...buildDynamicChecks(filesBlock, compatBlock),
  ];
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-files-facade-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodeRhinoCompatFilesFacade",
    methods: REQUIRED_METHODS,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      runtimeFacadePresent: checkById(checks, "runtime-files-facade"),
      explicitGlobalInstallWorks: checkById(checks, "dynamic-explicit-global-files"),
      pathTraversalRejected: checkById(checks, "dynamic-path-traversal-rejected"),
      scopedReadWriteWorks: checkById(checks, "dynamic-read-write"),
    },
  };
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat files facade verification: " + report.decision.status);
  console.log("Rhino compat files facade methods: " + REQUIRED_METHODS.join(", "));
  console.log("Rhino compat files facade policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildStaticChecks(files, filesBlock, compatBlock) {
  const manifest = JSON.parse(files.statusManifest.text);
  const filesModule = (manifest.modules || []).find((module) => module.id === "files");
  const entries = new Map((filesModule ? filesModule.apiEntries : []).map((entry) => [entry.id, entry]));
  return [
    check("runtime-files-facade", filesBlock.includes("function __autojs6_limited_files()"), "runtime must define __autojs6_limited_files"),
    check("runtime-files-methods", REQUIRED_METHODS.every((name) => filesBlock.includes(`${name}:`) || filesBlock.includes(`${name}(`)), "runtime files facade must expose the S11-10 method set"),
    check("runtime-files-scoped-paths", filesBlock.includes("__autojs6_validate_fs_path") && filesBlock.includes("Migration: AutoJs6 Node files compatibility"), "runtime files facade must use scoped path validation and migration hints"),
    check("runtime-require-files", files.runtime.text.includes('name === "files"') && files.runtime.text.includes("return __autojs6_limited_files();"), "runtime must resolve and require the files module"),
    check("runtime-compat-files", compatBlock.includes("files: __autojs6_limited_files()") && compatBlock.includes('"files"'), "autojs6:compat must expose files and list it as an installable global"),
    check("runtime-status-s11-10", /implementedTasks:\s*Object\.freeze\(\[[^\]]*"S11-10"/.test(compatBlock) && !/pendingTasks:\s*Object\.freeze\(\[[^\]]*"S11-10"/.test(compatBlock), "compat status must mark S11-10 implemented and remove it from pending tasks"),
    check("types-files-module", files.filesTypes.text.includes('declare module "files"') && REQUIRED_METHODS.every((name) => files.filesTypes.text.includes(`${name}(`)), "files.d.ts must declare the S11-10 methods"),
    check("types-index-files", files.indexTypes.text.includes('./files.d.ts'), "index.d.ts must reference files.d.ts"),
    check("types-compat-files", files.compatTypes.text.includes('import files = require("files")') && files.compatTypes.text.includes("readonly files: files.FilesModule"), "rhino_compat.d.ts must expose compat.files"),
    check("types-readme-files", files.typeReadme.text.includes("`files`"), "type README must list files"),
    check("design-doc-files", files.design.text.includes("S11-10") && files.design.text.includes("require(\"files\")") && files.design.text.includes("workingDirectory"), "design doc must document the scoped files facade"),
    check("status-files-module", filesModule && filesModule.status === "partial" && filesModule.nodeModules.includes("files"), "status manifest must mark files module partial through files"),
    check("status-files-path", statusOf(entries, "files.path") === "supported", "status manifest must mark files.path supported"),
    check("status-files-join", statusOf(entries, "files.join") === "supported", "status manifest must mark files.join supported"),
    check("status-files-toFile", statusOf(entries, "files.toFile") === "blocked_by_design", "status manifest must mark files.toFile blocked_by_design"),
    check("task-plan-s11-10-complete", /\[x\]\s+S11-10/.test(files.taskPlan.text), "Phase 11 task plan must mark S11-10 complete"),
    check("gradle-task", files.gradle.text.includes("verifyNodeRhinoCompatFilesFacade") && files.gradle.text.includes("verify-rhino-compat-files-facade.js"), "Gradle must register verifyNodeRhinoCompatFilesFacade"),
  ];
}

function buildDynamicChecks(filesBlock, compatBlock) {
  if (!filesBlock || !compatBlock) {
    return [check("dynamic-blocks", false, "runtime files or compat block could not be extracted")];
  }
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "autojs6-files-"));
  try {
    const harness = createHarness(filesBlock, compatBlock, root);
    const checks = [];
    checks.push(check("dynamic-no-default-files", !("files" in harness.globalObject), "require autojs6:compat must not install files globally by default"));
    checks.push(check("dynamic-status", harness.compat.status().implementedTasks.includes("S11-10") && harness.compat.status().availableGlobalNames.includes("files"), "compat status must expose S11-10 and files"));
    harness.files.write("a.txt", "hello");
    checks.push(check("dynamic-read-write", harness.files.read("a.txt") === "hello" && harness.files.exists("a.txt"), "files.write/read/exists must operate inside the scoped root"));
    checks.push(check("dynamic-path", path.isAbsolute(harness.files.path("a.txt")) && inside(harness.files.path("a.txt"), root), "files.path must resolve to a scoped absolute path"));
    checks.push(check("dynamic-listDir", harness.files.listDir(".").includes("a.txt"), "files.listDir must return scoped directory entries"));
    checks.push(check("dynamic-copy", harness.files.copy("a.txt", "nested/b.txt") === true && fs.existsSync(path.join(root, "nested", "b.txt")), "files.copy must copy and create destination parents inside scope"));
    checks.push(check("dynamic-move", harness.files.move("nested/b.txt", "nested/c.txt") === true && fs.existsSync(path.join(root, "nested", "c.txt")), "files.move must rename inside scope"));
    checks.push(check("dynamic-remove", harness.files.remove("nested/c.txt") === true && !fs.existsSync(path.join(root, "nested", "c.txt")), "files.remove must remove scoped files"));
    checks.push(check("dynamic-ensureDir", harness.files.ensureDir("more/file.txt") === true && fs.existsSync(path.join(root, "more")), "files.ensureDir must create scoped parent directories"));
    checks.push(check("dynamic-path-traversal-rejected", throwsWithHint(() => harness.files.read("../outside.txt"), "ERR_AUTOJS6_FS_PATH_ESCAPE"), "files.read must reject escaping relative paths with a migration hint"));
    checks.push(check("dynamic-absolute-outside-rejected", throwsWithHint(() => harness.files.exists(path.join(path.dirname(root), "outside.txt")), "ERR_AUTOJS6_FS_PATH_ESCAPE"), "files.exists must reject absolute paths outside the scoped root"));
    checks.push(check("dynamic-toFile-blocked", throwsCode(() => harness.files.toFile("a.txt"), "ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN"), "files.toFile must be blocked by design"));
    checks.push(check("dynamic-open-unsupported", throwsCode(() => harness.files.open("a.txt"), "ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED"), "files.open must fail with the stable unsupported code"));
    const install = harness.compat.install({ globals: true });
    checks.push(check("dynamic-explicit-global-files", install.globals.includes("files") && harness.globalObject.files === harness.files, "compat.install({ globals: true }) must install the files object explicitly"));
    return checks;
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}

function createHarness(filesBlock, compatBlock, root) {
  const source = `
    (function() {
      const __fs = require("fs");
      const __path = require("path");
      const __root = ${JSON.stringify(path.resolve(root))};
      let __autojs6_limited_files_cache = null;
      let __autojs6_limited_rhino_compat_cache = null;
      let __autojs6_rhino_compat_facade_cache = null;
      const __pureUtilityStubs = {
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
        storage: Object.freeze({ create() { return {}; }, open() { return {}; } }),
        storages: Object.freeze({ create() { return {}; }, open() { return {}; } }),
        database: Object.freeze({ open() { return Promise.resolve({ close() { return Promise.resolve(true); } }); } }),
        sqlite: Object.freeze(function sqlite() { return Promise.resolve({ close() { return Promise.resolve(true); } }); }),
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
        }),
        images: Object.freeze({
          captureScreen() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
          readImage() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
          saveImage() { return Promise.resolve(undefined); },
          clip() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
          resize() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
          grayscale() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
          threshold() { return Promise.resolve(Object.freeze({ id: "image", width: 1, height: 1 })); },
          findImage() { return Promise.resolve(null); },
          matchTemplate() { return Promise.resolve([]); },
          findColor() { return Promise.resolve(null); },
          findMultiColors() { return Promise.resolve(null); },
          getSize() { return Object.freeze({ width: 1, height: 1 }); },
          recycle() { return Promise.resolve(undefined); }
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
      function __autojs6_invalid_arg_type(message) {
        return __autojs6_error(message, "ERR_INVALID_ARG_TYPE");
      }
      function __autojs6_path_module() {
        return __path;
      }
      function __autojs6_fs_module() {
        return __fs;
      }
      function __autojs6_sensitive_path() {
        return false;
      }
      function __autojs6_fs_root() {
        return { root: __root, realRoot: __fs.realpathSync(__root), cwd: __root };
      }
      function __inside(target, rootValue) {
        const relative = __path.relative(rootValue, target);
        return relative === "" || (!!relative && !relative.startsWith("..") && !__path.isAbsolute(relative));
      }
      function __autojs6_path_value_display(value) {
        return String(value).replace(/\\u0000/g, "<NUL>");
      }
      function __autojs6_scoped_fs_policy_error(message, pathValue, operation, code, reason) {
        const error = __autojs6_error(message, code || "EPERM", reason || "ERR_AUTOJS6_FS_SCOPED_PATH");
        __autojs6_define_error_property(error, "path", __autojs6_path_value_display(pathValue));
        __autojs6_define_error_property(error, "syscall", String(operation || "").split(/\\s+/)[0]);
        return error;
      }
      function __autojs6_validate_fs_path(pathValue, operation, options) {
        const text = String(pathValue);
        if (text.indexOf("\\u0000") >= 0) {
          throw __autojs6_scoped_fs_policy_error("NUL path", pathValue, operation, "EPERM", "ERR_AUTOJS6_FS_NUL_BYTE");
        }
        if (__path.isAbsolute(text) && !(options && options.allowAbsolute)) {
          throw __autojs6_scoped_fs_policy_error("absolute path denied", pathValue, operation, "EPERM", "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
        }
        const resolved = __path.resolve(__root, text || ".");
        if (!__inside(resolved, __root)) {
          throw __autojs6_scoped_fs_policy_error("path escapes working directory", pathValue, operation, "EPERM", "ERR_AUTOJS6_FS_PATH_ESCAPE");
        }
        if (options && options.rejectRoot && resolved === __root) {
          throw __autojs6_scoped_fs_policy_error("cannot operate on workingDirectory root", pathValue, operation, "EPERM", "ERR_AUTOJS6_FS_SCOPED_PATH");
        }
        if (__fs.existsSync(resolved)) {
          const real = __fs.realpathSync(resolved);
          if (!__inside(real, __fs.realpathSync(__root))) {
            throw __autojs6_scoped_fs_policy_error("real path escapes working directory", pathValue, operation, "EPERM", "ERR_AUTOJS6_FS_REALPATH_ESCAPE");
          }
          return resolved;
        }
        if (options && options.mustExist) {
          throw __autojs6_error("path does not exist for " + operation + ": " + text, "ENOENT");
        }
        if (options && options.checkParent) {
          let parent = __path.dirname(resolved);
          while (parent && parent !== __path.dirname(parent) && !__fs.existsSync(parent)) {
            parent = __path.dirname(parent);
          }
          if (!__inside(parent, __root)) {
            throw __autojs6_scoped_fs_policy_error("parent escapes working directory", pathValue, operation, "EPERM", "ERR_AUTOJS6_FS_PATH_ESCAPE");
          }
        }
        return resolved;
      }
      function __autojs6_limited_toast() {
        return { showToast() { return Promise.resolve(); } };
      }
      function __autojs6_limited_clipboard() {
        return { setText() { return Promise.resolve(); }, getText() { return Promise.resolve(""); } };
      }
      function __autojs6_limited_base64() {
        return __pureUtilityStubs.base64;
      }
      function __autojs6_limited_colors() {
        return __pureUtilityStubs.colors;
      }
      function __autojs6_limited_formatter() {
        return __pureUtilityStubs.formatter;
      }
      function __autojs6_limited_converter() {
        return __pureUtilityStubs.converter;
      }
      function __autojs6_limited_mime() {
        return __pureUtilityStubs.mime;
      }
      function __autojs6_limited_nanoid() {
        return __pureUtilityStubs.nanoid;
      }
      function __autojs6_limited_util() {
        return __pureUtilityStubs.util;
      }
      function __autojs6_limited_opencc() {
        return __pureUtilityStubs.opencc;
      }
      function __autojs6_limited_pinyin() {
        return __pureUtilityStubs.pinyin;
      }
      function __autojs6_limited_pinyin4j() {
        return __pureUtilityStubs.pinyin4j;
      }
      function __autojs6_limited_storage(moduleName) {
        return moduleName === "storages" ? __pureUtilityStubs.storages : __pureUtilityStubs.storage;
      }
      function __autojs6_limited_database(moduleName) {
        return moduleName === "sqlite" ? __pureUtilityStubs.sqlite : __pureUtilityStubs.database;
      }
      function __autojs6_limited_console() {
        return __pureUtilityStubs.console;
      }
      function __autojs6_limited_timers() {
        return __pureUtilityStubs.timers;
      }
      function __autojs6_limited_app() {
        return __pureUtilityStubs.app;
      }
      function __autojs6_limited_device() {
        return __pureUtilityStubs.device;
      }
      function __autojs6_limited_shell() {
        return __pureUtilityStubs.shell;
      }
      function __autojs6_limited_image() {
        return __pureUtilityStubs.images;
      }
      function __autojs6_limited_ocr() {
        return Object.freeze({
          recognize() { return Promise.resolve([]); },
          recognizeText() { return Promise.resolve(""); },
          detectTextBounds() { return Promise.resolve([]); }
        });
      }
      function __autojs6_define_global(name, value) {
        Object.defineProperty(globalThis, name, { value, configurable: true, enumerable: false, writable: false });
      }
      ${filesBlock}
      ${compatBlock}
      return {
        files: __autojs6_limited_files(),
        compat: __autojs6_limited_rhino_compat(),
        globalObject: globalThis
      };
    })()
  `;
  return vm.runInNewContext(source, {
    Error,
    Math,
    Number,
    Object,
    Promise,
    RegExp,
    String,
    console: { log() {} },
    globalThis: {},
    require,
    setTimeout,
  });
}

function statusOf(entries, id) {
  const entry = entries.get(id);
  return entry ? entry.status : "";
}

function throwsCode(action, expectedCode) {
  try {
    action();
    return false;
  } catch (error) {
    return error && (error.code === expectedCode || error.autojs6Code === expectedCode);
  }
}

function throwsWithHint(action, expectedAutoJs6Code) {
  try {
    action();
    return false;
  } catch (error) {
    const message = error && error.message ? String(error.message) : String(error);
    return !!(
      error &&
      (error.autojs6Code === expectedAutoJs6Code || error.code === expectedAutoJs6Code) &&
      message.includes("Migration: AutoJs6 Node files compatibility")
    );
  }
}

function inside(target, rootValue) {
  const relative = path.relative(path.resolve(rootValue), path.resolve(target));
  return relative === "" || (!!relative && !relative.startsWith("..") && !path.isAbsolute(relative));
}

function extractFilesBlock(runtime) {
  const start = runtime.indexOf("function __autojs6_files_is_nullish");
  const end = runtime.indexOf("  function __autojs6_buffer_constructor_required", start);
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

function check(id, passed, message) {
  return { id, passed: passed === true, message };
}

function checkById(checks, id) {
  const item = checks.find((checkItem) => checkItem.id === id);
  return item ? item.passed : false;
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Files Facade Verification",
    "",
    `- Decision: ${report.decision.status}`,
    `- Methods: ${report.methods.map((name) => `\`${name}\``).join(", ")}`,
    `- Checks: ${report.checks.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Decisions",
    "",
    `- Runtime facade present: ${report.decision.runtimeFacadePresent}`,
    `- Explicit global install works: ${report.decision.explicitGlobalInstallWorks}`,
    `- Path traversal rejected: ${report.decision.pathTraversalRejected}`,
    `- Scoped read/write works: ${report.decision.scopedReadWriteWorks}`,
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
      console.log("Usage: node verify-rhino-compat-files-facade.js [--repo-root dir] [--report-dir dir]");
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
