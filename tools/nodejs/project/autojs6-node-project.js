#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const KNOWN_CAPABILITIES = new Set([
  "toast",
  "app",
  "app.launch",
  "app.settings",
  "app.activity",
  "app.query",
  "dialogs",
  "engines",
  "engines.exec",
  "accessibility",
  "screen_capture",
  "image",
  "ocr",
  "barcode",
  "media",
  "media.audio",
  "media.metadata",
  "media.recording",
  "storage",
  "notifications",
  "notifications.settings",
  "sensors",
  "ui",
  "clipboard",
  "device",
  "device.power",
  "shell",
  "shell.root",
  "shell.shizuku",
  "network",
  "raw_network",
  "work_manager",
  "java_interop"
]);

const CAPABILITY_ALIASES = new Map([
  ["media_projection", "screen_capture"],
  ["media.projection", "screen_capture"],
  ["screen.capture", "screen_capture"],
  ["screencapture", "screen_capture"],
  ["engines:exec", "engines.exec"],
  ["engine.exec", "engines.exec"],
  ["app-query", "app.query"],
  ["app:query", "app.query"],
  ["package-query", "app.query"],
  ["package.query", "app.query"],
  ["shell:root", "shell.root"],
  ["shell-shizuku", "shell.shizuku"],
  ["shell:shizuku", "shell.shizuku"],
  ["storages", "storage"],
  ["notification", "notifications"],
  ["notification.settings", "notifications.settings"],
  ["notifications:settings", "notifications.settings"],
  ["notifications-settings", "notifications.settings"],
  ["device-power", "device.power"],
  ["device:power", "device.power"],
  ["power-manager", "device.power"],
  ["power_manager", "device.power"],
  ["java", "java_interop"],
  ["raw.network", "raw_network"],
  ["raw-network", "raw_network"],
  ["raw.node.network", "raw_network"],
  ["raw-node-network", "raw_network"],
  ["raw_node_network_modules", "raw_network"],
  ["qrcode", "barcode"],
  ["qr_code", "barcode"],
  ["audio", "media.audio"],
  ["media:audio", "media.audio"],
  ["media-audio", "media.audio"],
  ["mediainfo", "media.metadata"],
  ["media.info", "media.metadata"],
  ["media-info", "media.metadata"],
  ["media_info", "media.metadata"],
  ["media:metadata", "media.metadata"],
  ["recorder", "media.recording"],
  ["media.record", "media.recording"],
  ["media:recording", "media.recording"],
  ["media-recording", "media.recording"],
  ["audio.recording", "media.recording"]
]);

const SUPPORTED_ENTRY_EXTENSIONS = new Set([".js", ".cjs", ".mjs", ".ts", ".cts", ".mts"]);
const MAX_DEPENDENCY_SCAN_ENTRIES = 5000;
const MAX_COMPATIBILITY_SOURCE_BYTES = 256 * 1024;
const UNSAFE_DEPENDENCY_SCRIPT_NAMES = new Set([
  "preinstall",
  "install",
  "postinstall",
  "prepare",
  "prepack",
  "postpack"
]);
const NATIVE_EXTENSIONS = new Set([".node", ".so", ".dll", ".dylib", ".a", ".o"]);
const JAVASCRIPT_SOURCE_EXTENSIONS = new Set([".js", ".cjs", ".mjs", ".ts", ".cts", ".mts"]);
const KNOWN_NODE_BUILTINS = new Set([
  "assert",
  "assert/strict",
  "async_hooks",
  "buffer",
  "child_process",
  "cluster",
  "console",
  "constants",
  "crypto",
  "dgram",
  "diagnostics_channel",
  "dns",
  "dns/promises",
  "events",
  "fs",
  "fs/promises",
  "http",
  "https",
  "inspector",
  "module",
  "net",
  "os",
  "path",
  "perf_hooks",
  "process",
  "punycode",
  "querystring",
  "readline",
  "repl",
  "stream",
  "stream/consumers",
  "stream/promises",
  "stream/web",
  "string_decoder",
  "test",
  "timers",
  "timers/promises",
  "tls",
  "tty",
  "url",
  "util",
  "util/types",
  "v8",
  "vm",
  "wasi",
  "worker_threads",
  "zlib"
]);
const COMPATIBILITY_STATUS_RANK = new Map([
  ["compatible", 0],
  ["compatible with capability", 1],
  ["experimental", 2],
  ["unsupported", 3],
  ["unsafe", 4]
]);
const NODE_BUILTIN_CAPABILITIES = new Map([
  ["http", "raw_network"],
  ["https", "raw_network"],
  ["net", "raw_network"],
  ["tls", "raw_network"],
  ["dns", "raw_network"],
  ["dns/promises", "raw_network"],
  ["dgram", "raw_network"],
  ["child_process", "child_process"],
  ["worker_threads", "worker_threads"],
  ["vm", "vm"],
  ["inspector", "inspector"]
]);
const BRIDGE_MODULE_CAPABILITIES = new Map([
  ["toast", "toast"],
  ["app", "app"],
  ["dialogs", "dialogs"],
  ["engines", "engines"],
  ["accessibility", "accessibility"],
  ["image", "image"],
  ["images", "image"],
  ["ocr", "ocr"],
  ["barcode", "barcode"],
  ["storage", "storage"],
  ["storages", "storage"],
  ["notifications", "notifications"],
  ["sensors", "sensors"],
  ["ui", "ui"],
  ["clipboard", "clipboard"],
  ["device", "device"],
  ["shell", "shell"],
  ["work_manager", "work_manager"]
]);

const TEMPLATES = {
  "commonjs-app": {
    description: "CommonJS Safe Node Profile app",
    entry: "main.cjs",
    packageType: "commonjs",
    permissions: [],
    source: (name) => `"nodejs";

const path = require("path");

console.log("${name}:ready");
console.log("cwd=" + process.cwd());
console.log("entry=" + path.basename(__filename));
`
  },
  "esm-app": {
    description: "ESM Safe Node Profile app",
    entry: "main.mjs",
    packageType: "module",
    permissions: [],
    source: (name) => `import path from "path";

console.log("${name}:ready");
console.log("entry=" + path.basename("main.mjs"));
`
  },
  "automation-script": {
    description: "Automation bridge script with explicit app/toast capabilities",
    entry: "main.cjs",
    packageType: "commonjs",
    permissions: ["toast", "app"],
    source: (name) => `"nodejs";

(async () => {
  const toast = require("toast");
  const app = require("app");

  await toast.showToast("${name} ready");
  console.log("host=" + app.packageName);
  console.log("version=" + app.versionName);
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
`
  },
  "screenshot-ocr-script": {
    description: "Screen capture plus OCR bridge starter",
    entry: "main.cjs",
    packageType: "commonjs",
    permissions: ["image", "screen_capture", "ocr"],
    source: () => `"nodejs";

(async () => {
  const image = require("image");
  const ocr = require("ocr");

  const screen = await image.captureScreen();
  try {
    const text = await ocr.recognizeText(screen);
    console.log(text);
  } finally {
    if (screen && typeof screen.recycle === "function") {
      await screen.recycle();
    }
  }
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
`
  },
  "network-fetch-script": {
    description: "Controlled fetch bridge starter",
    entry: "main.cjs",
    packageType: "commonjs",
    permissions: ["network"],
    source: () => `"nodejs";

(async () => {
  const response = await fetch("https://example.com/", { timeoutMs: 5000 });
  console.log("status=" + response.status);
  console.log((await response.text()).slice(0, 80));
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
`
  },
  "typescript-cjs": {
    description: "Lightweight TypeScript CommonJS app using .cts",
    entry: "main.cts",
    packageType: "commonjs",
    permissions: [],
    source: (name) => `"nodejs";

type Label = string;
interface Payload {
  label: Label;
  count: number;
}

const payload: Payload = { label: "${name}", count: 1 };

function format<T>(value: T): string {
  return String(value);
}

console.log("typescript.cjs=" + format(payload.label) + ":" + payload.count);
`
  },
  "typescript-esm": {
    description: "Lightweight TypeScript ESM app using .mts",
    entry: "main.mts",
    packageType: "module",
    permissions: [],
    source: (name) => `type Label = string;

const value: Label = await Promise.resolve("${name}");

export const marker: Label = value;
console.log("typescript.esm=" + marker);
`
  },
  "typescript-automation": {
    description: "TypeScript automation starter with toast/app capabilities",
    entry: "main.ts",
    packageType: "commonjs",
    permissions: ["toast", "app"],
    source: (name) => `"nodejs";

type ToastModule = {
  showToast(message: string): Promise<void>;
};
type AppModule = {
  packageName: string;
  versionName: string;
};

(async () => {
  const toast = require("toast") as ToastModule;
  const app = require("app") as AppModule;

  await toast.showToast("${name} ready");
  console.log("host=" + app.packageName);
  console.log("version=" + app.versionName);
})().catch((error: Error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
`
  },
  "typescript-ocr": {
    description: "TypeScript screen capture plus OCR bridge starter",
    entry: "main.ts",
    packageType: "commonjs",
    permissions: ["image", "screen_capture", "ocr"],
    source: () => `"nodejs";

type ImageHandle = {
  recycle?: () => Promise<void>;
};

(async () => {
  const image = require("image");
  const ocr = require("ocr");

  const screen = await image.captureScreen() as ImageHandle;
  try {
    const text = await ocr.recognizeText(screen);
    console.log(text);
  } finally {
    if (screen && typeof screen.recycle === "function") {
      await screen.recycle();
    }
  }
})().catch((error: Error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
`
  },
  "typescript-long-running-task": {
    description: "TypeScript long-running task starter with explicit cleanup",
    entry: "main.cts",
    packageType: "commonjs",
    permissions: [],
    source: () => `"nodejs";

type TimerHandle = ReturnType<typeof setInterval>;

let ticks = 0;
const timer: TimerHandle = setInterval(() => {
  ticks += 1;
  console.log("long-running.tick=" + ticks);
}, 1000);

function stop(): void {
  clearInterval(timer);
  console.log("long-running.stopped=true");
}

process.once("SIGTERM", stop);
process.once("SIGINT", stop);
`
  },
  "compiled-tsx": {
    description: "Build-time TSX project that runs compiled JavaScript only",
    entry: "main.cjs",
    packageType: "commonjs",
    permissions: [],
    scripts: {
      build: "tsc -p tsconfig.json"
    },
    devDependencies: {
      typescript: "^5.9.0"
    },
    source: () => `"nodejs";

require("./dist/view.js");
`,
    extraFiles: () => ({
      "src/view.tsx": `type Props = {
  text: string;
};

declare namespace JSX {
  interface IntrinsicElements {
    label: Props;
  }
}

function h(tag: string, props: Props): { tag: string; props: Props } {
  return { tag, props };
}

const view = <label text="compiled-tsx" />;
console.log("compiled.tsx.tag=" + view.tag);
console.log("compiled.tsx.text=" + view.props.text);
`,
      "dist/view.js": `"use strict";

function h(tag, props) {
  return { tag, props };
}

const view = h("label", { text: "compiled-tsx" });
console.log("compiled.tsx.tag=" + view.tag);
console.log("compiled.tsx.text=" + view.props.text);
`,
      "tsconfig.json": `{
  "compilerOptions": {
    "target": "ES2022",
    "module": "CommonJS",
    "moduleResolution": "Node",
    "jsx": "react",
    "jsxFactory": "h",
    "outDir": "dist",
    "strict": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*.tsx"]
}
`
    })
  }
};

function usage() {
  return [
    "Usage:",
    "  node tools/nodejs/project/autojs6-node-project.js templates [--json]",
    "  node tools/nodejs/project/autojs6-node-project.js create --template <name> --out <dir> [--name <project-name>] [--force]",
    "  node tools/nodejs/project/autojs6-node-project.js validate <dir> [--json]",
    "",
    "Templates: " + Object.keys(TEMPLATES).join(", ")
  ].join("\n");
}

function parseArgs(argv) {
  const args = { _: [] };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (!value.startsWith("--")) {
      args._.push(value);
      continue;
    }
    const key = value.slice(2);
    if (key === "json" || key === "force" || key === "help") {
      args[key] = true;
      continue;
    }
    const next = argv[index + 1];
    if (next === undefined || next.startsWith("--")) {
      throw new Error("Missing value for --" + key + ".");
    }
    args[key] = next;
    index += 1;
  }
  return args;
}

function normalizeProjectName(value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "autojs6-node-project";
  }
  return raw
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, "-")
    .replace(/\.{2,}/g, ".")
    .replace(/^[._-]+|[._-]+$/g, "")
    .slice(0, 80) || "autojs6-node-project";
}

function packageNameFor(projectName) {
  const safe = normalizeProjectName(projectName).replace(/^@+/, "").replace(/\//g, "-");
  return "@autojs6-project/" + safe;
}

function writeJson(file, value) {
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + "\n", "utf8");
}

function templateExtraFiles(template, projectName) {
  if (!template.extraFiles) {
    return {};
  }
  return typeof template.extraFiles === "function" ? template.extraFiles(projectName) : template.extraFiles;
}

function writeTemplateFile(root, relativePath, contents) {
  const target = path.join(root, relativePath);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, contents, "utf8");
}

function createProject(args) {
  const templateName = args.template;
  const template = TEMPLATES[templateName];
  if (!template) {
    throw new Error("Unknown template '" + String(templateName || "") + "'. Use one of: " + Object.keys(TEMPLATES).join(", "));
  }
  if (!args.out) {
    throw new Error("create requires --out <dir>.");
  }
  const outDir = path.resolve(args.out);
  const projectName = normalizeProjectName(args.name || path.basename(outDir));
  if (fs.existsSync(outDir)) {
    const existing = fs.readdirSync(outDir);
    if (existing.length > 0 && !args.force) {
      throw new Error("Output directory is not empty: " + outDir + ". Pass --force to overwrite template files.");
    }
  }
  fs.mkdirSync(outDir, { recursive: true });

  writeJson(path.join(outDir, "project.json"), {
    name: projectName,
    type: "node",
    main: template.entry,
    node: {
      permissions: template.permissions
    }
  });
  const packageJson = {
    name: packageNameFor(projectName),
    version: "0.1.0",
    private: true,
    type: template.packageType,
    main: template.entry,
    autojs6: {
      node: {
        permissions: template.permissions
      }
    }
  };
  if (template.scripts) {
    packageJson.scripts = template.scripts;
  }
  if (template.devDependencies) {
    packageJson.devDependencies = template.devDependencies;
  }
  writeJson(path.join(outDir, "package.json"), packageJson);
  writeTemplateFile(outDir, template.entry, template.source(projectName));
  const extraFiles = templateExtraFiles(template, projectName);
  Object.entries(extraFiles).forEach(([relativePath, contents]) => {
    writeTemplateFile(outDir, relativePath, contents);
  });
  fs.writeFileSync(
    path.join(outDir, "README.md"),
    [
      "# " + projectName,
      "",
      "Generated from the AutoJs6 Node `" + templateName + "` template.",
      "",
      "Validate this project with:",
      "",
      "```powershell",
      "node tools/nodejs/project/autojs6-node-project.js validate " + JSON.stringify(outDir),
      "```",
      ""
    ].join("\n"),
    "utf8"
  );

  const report = validateProject(outDir);
  if (!report.ok) {
    const details = report.issues.map((issue) => issue.code + ": " + issue.message).join("; ");
    throw new Error("Generated template failed validation: " + details);
  }
  return {
    ok: true,
    template: templateName,
    name: projectName,
    root: outDir,
    files: ["project.json", "package.json", template.entry, ...Object.keys(extraFiles).sort(), "README.md"]
  };
}

function validateProject(projectRoot) {
  const root = path.resolve(projectRoot || ".");
  const issues = [];
  const projectJsonFile = path.join(root, "project.json");
  const packageJsonFile = path.join(root, "package.json");
  const projectJson = readJson(projectJsonFile, "project.json", issues, true);
  const packageJson = readJson(packageJsonFile, "package.json", issues, true);

  validateProjectJson(projectJson, projectJsonFile, issues);
  validatePackageJson(packageJson, packageJsonFile, issues);
  validateEntry(root, projectJson, packageJson, issues);
  validatePermissions(projectJson, packageJson, issues);
  const dependencyScan = validateDependencyBundle(root, issues);
  const compatibility = analyzeCompatibility(root, projectJson, packageJson, dependencyScan);
  addCompatibilityValidationIssues(compatibility, issues);

  return {
    schema: "autojs6-node-project-validation-v1",
    ok: issues.filter((issue) => issue.severity === "error").length === 0,
    root,
    issueCount: issues.length,
    issues,
    compatibility
  };
}

function readJson(file, label, issues, required) {
  if (!fs.existsSync(file)) {
    if (required) {
      issue(issues, "PROJECT_FILE_MISSING", file, label + " is required.", "Create " + label + " at the project root.");
    }
    return null;
  }
  try {
    const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      issue(issues, "JSON_OBJECT_REQUIRED", file, label + " must be a JSON object.", "Replace the file contents with a JSON object.");
      return null;
    }
    return parsed;
  } catch (error) {
    issue(issues, "JSON_PARSE_ERROR", file, label + " could not be parsed: " + error.message, "Fix invalid JSON syntax.");
    return null;
  }
}

function validateProjectJson(projectJson, file, issues) {
  if (!projectJson) return;
  const type = projectJson.type || projectJson.projectType;
  if (type !== "node") {
    issue(issues, "PROJECT_JSON_TYPE", file, "project.json must declare type or projectType as 'node'.", "Set { \"type\": \"node\" }.");
  }
  if (projectJson.name !== undefined && !nonEmptyString(projectJson.name)) {
    issue(issues, "PROJECT_NAME_INVALID", file, "project.json name must be a non-empty string.", "Use a stable project name such as 'my-node-app'.");
  }
  if (projectJson.main !== undefined && !nonEmptyString(projectJson.main)) {
    issue(issues, "PROJECT_MAIN_INVALID", file, "project.json main must be a non-empty string.", "Point main to a .js, .cjs, .mjs, .ts, .cts, or .mts entry file.");
  }
}

function validatePackageJson(packageJson, file, issues) {
  if (!packageJson) return;
  if (!validPackageName(packageJson.name)) {
    issue(issues, "PACKAGE_NAME_INVALID", file, "package.json name must be a valid lowercase npm package name.", "Use a name such as 'my-node-app' or '@autojs6-project/my-app'.");
  }
  if (packageJson.version !== undefined && !nonEmptyString(packageJson.version)) {
    issue(issues, "PACKAGE_VERSION_INVALID", file, "package.json version must be a non-empty string when present.", "Use a semantic version such as '0.1.0'.");
  }
  if (packageJson.type !== undefined && packageJson.type !== "commonjs" && packageJson.type !== "module") {
    issue(issues, "PACKAGE_TYPE_UNSUPPORTED", file, "package.json type must be 'commonjs' or 'module'.", "Remove package.json type or set it to 'commonjs' or 'module'.");
  }
  if (packageJson.main !== undefined && !nonEmptyString(packageJson.main)) {
    issue(issues, "PACKAGE_MAIN_INVALID", file, "package.json main must be a non-empty string when present.", "Point main to a .js, .cjs, .mjs, .ts, .cts, or .mts entry file.");
  }
}

function validateEntry(root, projectJson, packageJson, issues) {
  const entry = projectJson && nonEmptyString(projectJson.main)
    ? projectJson.main
    : packageJson && nonEmptyString(packageJson.main)
      ? packageJson.main
      : "";
  if (!entry) {
    issue(issues, "ENTRY_DECLARATION_MISSING", root, "No Node entry file was declared.", "Set project.json main or package.json main to a .js, .cjs, .mjs, .ts, .cts, or .mts file.");
    return;
  }
  const entryPath = safeRelativePath(root, entry, "ENTRY_PATH_UNSAFE", issues, "Node entry");
  if (!entryPath) return;
  const extension = path.extname(entryPath).toLowerCase();
  if (extension === ".node") {
    issue(issues, "ENTRY_NATIVE_ADDON_DISABLED", entryPath, "Native addon entry files are disabled.", "Use a JavaScript or TypeScript .js, .cjs, .mjs, .ts, .cts, or .mts entry file.");
  } else if (extension === ".tsx") {
    issue(issues, "ENTRY_TYPESCRIPT_TSX_UNSUPPORTED", entryPath, "TypeScript TSX entry files are not supported by lightweight type stripping.", "Compile TSX to JavaScript before packaging or running it.");
  } else if (!SUPPORTED_ENTRY_EXTENSIONS.has(extension)) {
    issue(issues, "ENTRY_EXTENSION_UNSUPPORTED", entryPath, "Unsupported Node entry extension '" + extension + "'.", "Use .js, .cjs, .mjs, .ts, .cts, or .mts.");
  }
  if (!fs.existsSync(entryPath) || !fs.statSync(entryPath).isFile()) {
    issue(issues, "ENTRY_FILE_MISSING", entryPath, "Declared Node entry file is missing.", "Create " + path.relative(root, entryPath) + " or update project.json main.");
  }
}

function validatePermissions(projectJson, packageJson, issues) {
  const sources = [];
  if (projectJson && projectJson.node && typeof projectJson.node === "object") {
    sources.push(["project.json:node.permissions", projectJson.node.permissions]);
    sources.push(["project.json:node.bridgePermissions", projectJson.node.bridgePermissions]);
  }
  const autojs6 = packageJson && packageJson.autojs6 && typeof packageJson.autojs6 === "object"
    ? packageJson.autojs6
    : null;
  if (autojs6) {
    sources.push(["package.json:autojs6.permissions", autojs6.permissions]);
    sources.push(["package.json:autojs6.bridgePermissions", autojs6.bridgePermissions]);
    if (autojs6.node && typeof autojs6.node === "object") {
      sources.push(["package.json:autojs6.node.permissions", autojs6.node.permissions]);
      sources.push(["package.json:autojs6.node.bridgePermissions", autojs6.node.bridgePermissions]);
    }
  }

  for (const [label, value] of sources) {
    if (value === undefined || value === null) continue;
    if (!Array.isArray(value)) {
      issue(issues, "PERMISSIONS_SHAPE", label, label + " must be a JSON string array.", "Use an array such as [\"toast\", \"app\"].");
      continue;
    }
    value.forEach((item, index) => {
      if (typeof item !== "string" || item.trim() === "") {
        issue(issues, "PERMISSIONS_SHAPE", label, label + "[" + index + "] must be a non-empty string.", "Remove empty entries or replace them with valid capability names.");
        return;
      }
      const normalized = normalizeCapability(item);
      if (!isKnownCapability(normalized)) {
        issue(
          issues,
          "UNKNOWN_PERMISSION",
          label,
          "Unknown Node bridge capability '" + item + "'.",
          "Use a known capability such as toast, app, app.launch, app.query, image, ocr, barcode, network, raw_network, storage, shell, or plugin.<id>."
        );
      }
    });
  }
}

function validateDependencyBundle(root, issues) {
  const scan = {
    installed: false,
    packageCount: 0,
    fileCount: 0,
    totalBytes: 0,
    nativeAddonCount: 0,
    lifecycleScriptCount: 0,
    packageExportsCount: 0,
    packageImportsCount: 0,
    packageExportConditions: new Set(),
    packageFormats: new Map(),
    unsupportedPackageManagerLayout: false
  };
  const nodeModules = path.join(root, "node_modules");
  if (fs.existsSync(path.join(root, ".pnp.cjs")) ||
      fs.existsSync(path.join(root, ".pnp.loader.mjs")) ||
      fs.existsSync(path.join(root, ".yarn", "install-state.gz")) ||
      fs.existsSync(path.join(nodeModules, ".pnpm")) ||
      fs.existsSync(path.join(nodeModules, ".store"))) {
    scan.unsupportedPackageManagerLayout = true;
    issue(
      issues,
      "UNSUPPORTED_PACKAGE_MANAGER_LAYOUT",
      root,
      "Project uses an unsupported package-manager layout such as Yarn Plug'n'Play or pnpm virtual store.",
      "Use a flat npm node_modules layout prepared with the Safe Node Profile npm ci gate."
    );
  }
  if (!fs.existsSync(nodeModules)) return scan;
  scan.installed = true;
  walk(nodeModules, issues, (file, stat) => {
    const relative = path.relative(root, file).replace(/\\/g, "/");
    if (stat.isSymbolicLink()) {
      issue(issues, "DEPENDENCY_SYMLINK_UNSUPPORTED", file, "Dependency bundle contains a symlink: " + relative, "Replace symlinks with real files before packaging.");
      return false;
    }
    if (stat.isDirectory() && path.basename(file) === ".bin") {
      issue(issues, "DEPENDENCY_BIN_ENTRY_UNSUPPORTED", file, "Dependency bundle contains node_modules/.bin entries.", "Remove generated .bin shims before bundling dependencies.");
      return false;
    }
    if (stat.isFile()) {
      const name = path.basename(file);
      const extension = path.extname(name).toLowerCase();
      scan.fileCount += 1;
      scan.totalBytes += stat.size;
      if (NATIVE_EXTENSIONS.has(extension)) {
        scan.nativeAddonCount += 1;
        issue(issues, "NATIVE_ADDON_DISABLED", file, "Dependency bundle contains native file " + relative + ".", "Use pure JavaScript dependencies only; .node and native libraries are disabled.");
      }
      if (name === "binding.gyp") {
        issue(issues, "NODE_GYP_ARTIFACT_UNSUPPORTED", file, "Dependency bundle contains binding.gyp.", "Remove native addon packages or replace them with pure JavaScript alternatives.");
      }
      if (name === "package.json") {
        validateDependencyPackageJson(file, issues, scan);
      }
    }
    return true;
  });
  return scan;
}

function validateDependencyPackageJson(file, issues, scan) {
  const json = readJson(file, "dependency package.json", issues, false);
  if (!json) return;
  scan.packageCount += 1;
  recordPackageFormat(scan, json);
  if (json.exports !== undefined) {
    scan.packageExportsCount += 1;
    collectPackageExportConditions(json.exports, scan.packageExportConditions);
  }
  if (json.imports !== undefined) {
    scan.packageImportsCount += 1;
    collectPackageExportConditions(json.imports, scan.packageExportConditions);
  }
  const scripts = json.scripts && typeof json.scripts === "object" && !Array.isArray(json.scripts)
    ? json.scripts
    : {};
  for (const scriptName of Object.keys(scripts)) {
    if (UNSAFE_DEPENDENCY_SCRIPT_NAMES.has(scriptName)) {
      scan.lifecycleScriptCount += 1;
      issue(
        issues,
        "DEPENDENCY_LIFECYCLE_SCRIPT_UNSUPPORTED",
        file,
        "Dependency package declares unsafe lifecycle script '" + scriptName + "'.",
        "Remove lifecycle scripts before packaging or vendor a sanitized dependency bundle."
      );
    }
  }
  if (json.gypfile === true) {
    issue(issues, "NODE_GYP_ARTIFACT_UNSUPPORTED", file, "Dependency package declares gypfile=true.", "Use pure JavaScript dependencies only.");
  }
}

function analyzeCompatibility(root, projectJson, packageJson, dependencyScan) {
  const declaredCapabilities = collectDeclaredCapabilities(projectJson, packageJson);
  const signals = [];
  const requiredBuiltins = new Map();
  const capabilityRequirements = new Map();
  const entry = analyzeEntryFormat(root, projectJson, packageJson, signals);
  const sourceFiles = collectCompatibilitySourceFiles(root, signals);

  for (const file of sourceFiles) {
    const text = readSourcePrefix(file);
    const relative = path.relative(root, file).replace(/\\/g, "/");
    analyzeSourceText(text, relative, requiredBuiltins, capabilityRequirements, signals);
  }

  if (dependencyScan.nativeAddonCount > 0) {
    signal(signals, "native-addon", "unsafe", "NATIVE_ADDON_DISABLED", "node_modules", "Dependency graph contains native addon or native binary files.");
  }
  if (dependencyScan.lifecycleScriptCount > 0) {
    signal(signals, "postinstall", "unsafe", "DEPENDENCY_LIFECYCLE_SCRIPT_UNSUPPORTED", "node_modules", "Dependency graph contains install/postinstall lifecycle scripts.");
  }
  if (dependencyScan.unsupportedPackageManagerLayout) {
    signal(signals, "package-manager-layout", "unsupported", "UNSUPPORTED_PACKAGE_MANAGER_LAYOUT", root, "Dependency graph uses an unsupported package-manager layout.");
  }
  if (dependencyScan.packageExportsCount > 0 || dependencyScan.packageImportsCount > 0) {
    signal(
      signals,
      "package-exports",
      "experimental",
      "PACKAGE_EXPORTS_CONDITIONS_PARTIAL",
      "node_modules",
      "Dependency graph uses package exports/imports conditions covered by the v1.1 partial resolver gate."
    );
  }
  if (dependencyScan.fileCount > MAX_DEPENDENCY_SCAN_ENTRIES) {
    signal(signals, "graph-size", "unsupported", "DEPENDENCY_BUNDLE_TOO_LARGE", "node_modules", "Dependency graph exceeds the project wizard scan budget.");
  }

  for (const [capability, modules] of capabilityRequirements) {
    const declared = declaredCapabilities.has(capability);
    const baseStatus = capability === "raw_network" || capability === "child_process" || capability === "vm" || capability === "inspector"
      ? "unsupported"
      : capability === "worker_threads"
        ? "experimental"
        : "compatible with capability";
    const status = !declared && baseStatus === "compatible with capability" ? "unsupported" : baseStatus;
    signal(
      signals,
      "capability",
      status,
      declared ? "CAPABILITY_REQUIRED" : "CAPABILITY_DECLARATION_MISSING",
      root,
      "Project requires capability '" + capability + "' from " + Array.from(modules).sort().join(", ") + ".",
      { capability, declared, modules: Array.from(modules).sort() }
    );
  }

  const status = signals.reduce(
    (current, item) => worseCompatibilityStatus(current, item.status),
    "compatible"
  );
  const packageFormats = {};
  for (const [format, count] of dependencyScan.packageFormats) {
    packageFormats[format] = count;
  }
  return {
    schema: "autojs6-node-dependency-compatibility-v1",
    status,
    entry,
    declaredCapabilities: Array.from(declaredCapabilities).sort(),
    requiredBuiltins: Array.from(requiredBuiltins.values())
      .map((item) => ({
        name: item.name,
        files: Array.from(item.files).sort()
      }))
      .sort((left, right) => left.name.localeCompare(right.name)),
    capabilityRequirements: Array.from(capabilityRequirements.entries())
      .map(([capability, modules]) => ({
        capability,
        declared: declaredCapabilities.has(capability),
        modules: Array.from(modules).sort()
      }))
      .sort((left, right) => left.capability.localeCompare(right.capability)),
    dependencyGraph: {
      installed: dependencyScan.installed,
      packageCount: dependencyScan.packageCount,
      fileCount: dependencyScan.fileCount,
      totalBytes: dependencyScan.totalBytes,
      packageFormats,
      packageExportsCount: dependencyScan.packageExportsCount,
      packageImportsCount: dependencyScan.packageImportsCount,
      packageExportConditions: Array.from(dependencyScan.packageExportConditions).sort()
    },
    apkBuilder: {
      gate: "validate-before-directory-packaging",
      packagedAssetRoot: "assets/project",
      reportFile: "dependency-compatibility.json"
    },
    signals: signals.sort((left, right) => {
      const statusDelta = COMPATIBILITY_STATUS_RANK.get(right.status) - COMPATIBILITY_STATUS_RANK.get(left.status);
      return statusDelta || left.code.localeCompare(right.code) || String(left.file).localeCompare(String(right.file));
    })
  };
}

function analyzeEntryFormat(root, projectJson, packageJson, signals) {
  const entryValue = projectJson && nonEmptyString(projectJson.main)
    ? projectJson.main
    : packageJson && nonEmptyString(packageJson.main)
      ? packageJson.main
      : "";
  const extension = path.extname(String(entryValue || "")).toLowerCase();
  const packageType = packageJson && packageJson.type === "module" ? "module" : "commonjs";
  const format = extension === ".mjs" ||
    extension === ".mts" ||
    ((extension === ".js" || extension === ".ts") && packageType === "module")
    ? "esm"
    : extension === ".cjs" || extension === ".cts" || extension === ".js" || extension === ".ts"
      ? "cjs"
      : "unknown";
  if (format === "esm") {
    signal(signals, "module-format", "experimental", "ESM_ENTRY_PARTIAL", entryValue || root, "ESM entry support is partial in Node Profile v1.1.");
  }
  return {
    path: entryValue,
    extension,
    packageType,
    format
  };
}

function analyzeSourceText(text, relative, requiredBuiltins, capabilityRequirements, signals) {
  const moduleNames = new Set();
  const requirePattern = /require\s*\(\s*["'](?:node:)?([^"']+)["']\s*\)/g;
  const importFromPattern = /(?:from|import)\s*["'](?:node:)?([^"']+)["']/g;
  let match;
  while ((match = requirePattern.exec(text)) !== null) {
    moduleNames.add(match[1]);
  }
  while ((match = importFromPattern.exec(text)) !== null) {
    moduleNames.add(match[1]);
  }
  for (const moduleName of moduleNames) {
    if (KNOWN_NODE_BUILTINS.has(moduleName) || NODE_BUILTIN_CAPABILITIES.has(moduleName)) {
      mapAddSet(requiredBuiltins, moduleName, "name", "files", relative);
    }
    if (NODE_BUILTIN_CAPABILITIES.has(moduleName)) {
      const capability = NODE_BUILTIN_CAPABILITIES.get(moduleName);
      mapAddSet(capabilityRequirements, capability, null, null, moduleName);
    }
    if (BRIDGE_MODULE_CAPABILITIES.has(moduleName)) {
      const capability = BRIDGE_MODULE_CAPABILITIES.get(moduleName);
      mapAddSet(capabilityRequirements, capability, null, null, moduleName);
    }
  }
  if (/\bfetch\s*\(/.test(text)) {
    mapAddSet(capabilityRequirements, "network", null, null, "fetch");
  }
  if (/\bprocess\.(binding|dlopen)\s*\(/.test(text)) {
    signal(signals, "native-process-api", "unsafe", "PROCESS_NATIVE_API_DISABLED", relative, "process.binding/process.dlopen are disabled.");
  }
  if (/\.node["']/.test(text)) {
    signal(signals, "native-addon", "unsafe", "NATIVE_ADDON_DISABLED", relative, "Source references a native .node addon.");
  }
  const fsOutsideScopePattern = /(?:\b(?:fs|fsp)\s*|require\s*\(\s*["'](?:node:)?fs(?:\/promises)?["']\s*\))\.\s*(?:readFile|readFileSync|writeFile|writeFileSync|open|openSync|mkdir|mkdirSync|readdir|readdirSync|stat|statSync)\s*\(\s*["'](?:\/|\\|[A-Za-z]:|\.\.\/|\.\.\\)/;
  if (fsOutsideScopePattern.test(text)) {
    signal(signals, "fs-scope", "unsafe", "FS_OUTSIDE_SCOPE", relative, "Source contains a direct fs call with an absolute or escaping literal path.");
  }
}

function addCompatibilityValidationIssues(compatibility, issues) {
  for (const item of compatibility.signals) {
    if (item.status !== "unsafe" && item.status !== "unsupported") {
      continue;
    }
    if (item.code === "CAPABILITY_REQUIRED") {
      continue;
    }
    if (issues.some((existing) => existing.code === item.code && existing.file === String(item.file))) {
      continue;
    }
    issue(
      issues,
      item.code,
      item.file,
      item.message,
      item.hint || "Use a Safe Node Profile compatible dependency or declare a supported bridge capability."
    );
  }
}

function collectDeclaredCapabilities(projectJson, packageJson) {
  const values = [];
  function addPermissions(value) {
    if (Array.isArray(value)) {
      value.forEach((item) => values.push(normalizeCapability(item)));
    }
  }
  if (projectJson && projectJson.node && typeof projectJson.node === "object") {
    addPermissions(projectJson.node.permissions);
    addPermissions(projectJson.node.bridgePermissions);
  }
  const autojs6 = packageJson && packageJson.autojs6 && typeof packageJson.autojs6 === "object"
    ? packageJson.autojs6
    : null;
  if (autojs6) {
    addPermissions(autojs6.permissions);
    addPermissions(autojs6.bridgePermissions);
    if (autojs6.node && typeof autojs6.node === "object") {
      addPermissions(autojs6.node.permissions);
      addPermissions(autojs6.node.bridgePermissions);
    }
  }
  return new Set(values.filter(Boolean));
}

function collectCompatibilitySourceFiles(root, signals) {
  const files = [];
  walk(root, [], (file, stat) => {
    const name = path.basename(file);
    if (stat.isDirectory() && (name === ".git" || name === "build" || name === ".gradle")) {
      return false;
    }
    if (stat.isFile() && JAVASCRIPT_SOURCE_EXTENSIONS.has(path.extname(name).toLowerCase())) {
      files.push(file);
    }
    return true;
  });
  if (files.length > MAX_DEPENDENCY_SCAN_ENTRIES) {
    signal(signals, "graph-size", "unsupported", "DEPENDENCY_BUNDLE_TOO_LARGE", root, "Project source scan exceeded " + MAX_DEPENDENCY_SCAN_ENTRIES + " JavaScript entries.");
  }
  return files.slice(0, MAX_DEPENDENCY_SCAN_ENTRIES);
}

function readSourcePrefix(file) {
  const stat = fs.statSync(file);
  const bytes = Math.min(stat.size, MAX_COMPATIBILITY_SOURCE_BYTES);
  const fd = fs.openSync(file, "r");
  try {
    const buffer = Buffer.alloc(bytes);
    fs.readSync(fd, buffer, 0, bytes, 0);
    return buffer.toString("utf8");
  } finally {
    fs.closeSync(fd);
  }
}

function recordPackageFormat(scan, json) {
  const format = json.type === "module" ? "esm" : "cjs";
  scan.packageFormats.set(format, (scan.packageFormats.get(format) || 0) + 1);
}

function collectPackageExportConditions(value, output) {
  if (!value || typeof value !== "object") {
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectPackageExportConditions(item, output));
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if (!key.startsWith(".") && !key.startsWith("#") && key !== "types") {
      output.add(key);
    }
    collectPackageExportConditions(child, output);
  }
}

function signal(signals, category, status, code, file, message, extra) {
  signals.push({
    category,
    status,
    code,
    file: String(file),
    message,
    hint: compatibilityHint(code, status),
    ...(extra || {})
  });
}

function mapAddSet(map, key, objectKey, setKey, value) {
  if (objectKey && setKey) {
    if (!map.has(key)) {
      map.set(key, { [objectKey]: key, [setKey]: new Set() });
    }
    map.get(key)[setKey].add(value);
    return;
  }
  if (!map.has(key)) {
    map.set(key, new Set());
  }
  map.get(key).add(value);
}

function worseCompatibilityStatus(left, right) {
  return COMPATIBILITY_STATUS_RANK.get(right) > COMPATIBILITY_STATUS_RANK.get(left) ? right : left;
}

function compatibilityHint(code, status) {
  if (code === "CAPABILITY_DECLARATION_MISSING") {
    return "Declare the required Safe Node Profile capability in project.json or package.json.";
  }
  if (code === "ESM_ENTRY_PARTIAL" || code === "PACKAGE_EXPORTS_CONDITIONS_PARTIAL") {
    return "Keep this project on the v1.1 partial support gate and retain CommonJS fallback coverage where possible.";
  }
  if (status === "unsafe") {
    return "Remove the unsafe native/process/fs behavior before packaging.";
  }
  if (status === "unsupported") {
    return "Replace this dependency or API use with a Safe Node Profile supported surface.";
  }
  return "No action required unless the target gate disallows this compatibility level.";
}

function walk(root, issues, visitor) {
  const stack = [root];
  let visited = 0;
  while (stack.length > 0) {
    const current = stack.pop();
    visited += 1;
    if (visited > MAX_DEPENDENCY_SCAN_ENTRIES) {
      issue(
        issues,
        "DEPENDENCY_BUNDLE_TOO_LARGE",
        root,
        "Dependency bundle scan exceeded " + MAX_DEPENDENCY_SCAN_ENTRIES + " entries.",
        "Prune node_modules or vendor only required pure JavaScript files before packaging."
      );
      return;
    }
    const stat = fs.lstatSync(current);
    const shouldDescend = visitor(current, stat);
    if (!shouldDescend || !stat.isDirectory()) {
      continue;
    }
    for (const child of fs.readdirSync(current)) {
      stack.push(path.join(current, child));
    }
  }
}

function safeRelativePath(root, value, code, issues, label) {
  if (typeof value !== "string" || value.trim() === "") {
    issue(issues, code, root, label + " path must be a non-empty relative path.", "Use a relative path such as main.cjs.");
    return null;
  }
  if (value.includes("\u0000")) {
    issue(issues, code, root, label + " path contains a NUL byte.", "Remove NUL bytes from the path.");
    return null;
  }
  if (/^[A-Za-z][A-Za-z0-9+.-]*:/.test(value) || path.isAbsolute(value)) {
    issue(issues, code, root, label + " path must not be absolute or protocol-based: " + value, "Use a project-relative path such as main.cjs.");
    return null;
  }
  const resolved = path.resolve(root, value);
  const relative = path.relative(root, resolved);
  if (relative === "" || relative.startsWith("..") || path.isAbsolute(relative)) {
    issue(issues, code, root, label + " path escapes the project root: " + value, "Keep entry and dependency paths inside the project root.");
    return null;
  }
  return resolved;
}

function issue(issues, code, file, message, hint) {
  issues.push({
    severity: "error",
    code,
    file: String(file),
    message,
    hint
  });
}

function normalizeCapability(value) {
  const normalized = String(value || "").trim().toLowerCase().replace(/:/g, ".");
  return CAPABILITY_ALIASES.get(normalized) || normalized;
}

function isKnownCapability(value) {
  return KNOWN_CAPABILITIES.has(value) || /^plugin\.[a-z0-9][a-z0-9._-]*$/.test(value);
}

function nonEmptyString(value) {
  return typeof value === "string" && value.trim() !== "";
}

function validPackageName(value) {
  if (!nonEmptyString(value) || value.length > 214 || /\s/.test(value) || /[A-Z]/.test(value)) {
    return false;
  }
  if (value.startsWith("@")) {
    const parts = value.split("/");
    return parts.length === 2 && validPackageNamePart(parts[0].slice(1)) && validPackageNamePart(parts[1]);
  }
  return validPackageNamePart(value);
}

function validPackageNamePart(value) {
  return /^[a-z0-9][a-z0-9._~-]*$/.test(value) && !value.startsWith(".") && !value.startsWith("_");
}

function printTemplates(json) {
  const rows = Object.entries(TEMPLATES).map(([name, template]) => ({
    name,
    description: template.description,
    entry: template.entry,
    packageType: template.packageType,
    permissions: template.permissions,
    files: ["project.json", "package.json", template.entry, ...Object.keys(templateExtraFiles(template, name)).sort(), "README.md"]
  }));
  if (json) {
    console.log(JSON.stringify({ schema: "autojs6-node-project-templates-v1", templates: rows }, null, 2));
  } else {
    rows.forEach((row) => {
      console.log(row.name + " - " + row.description);
    });
  }
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const command = args._[0];
  if (!command || args.help) {
    console.log(usage());
    process.exit(command ? 0 : 1);
  }
  if (command === "templates") {
    printTemplates(args.json === true);
    return;
  }
  if (command === "create") {
    const report = createProject(args);
    if (args.json) {
      console.log(JSON.stringify(report, null, 2));
    } else {
      console.log("Created AutoJs6 Node project at " + report.root);
    }
    return;
  }
  if (command === "validate") {
    const report = validateProject(args._[1] || ".");
    if (args.json) {
      console.log(JSON.stringify(report, null, 2));
    } else if (report.ok) {
      console.log("AutoJs6 Node project is valid: " + report.root);
    } else {
      report.issues.forEach((item) => {
        console.error(item.code + ": " + item.message);
        console.error("  file: " + item.file);
        console.error("  hint: " + item.hint);
      });
    }
    process.exit(report.ok ? 0 : 1);
  }
  throw new Error("Unknown command '" + command + "'.\n" + usage());
}

try {
  main();
} catch (error) {
  console.error(error.message || String(error));
  process.exit(1);
}
