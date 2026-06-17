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
  "dialogs",
  "engines",
  "engines.exec",
  "accessibility",
  "screen_capture",
  "image",
  "ocr",
  "storage",
  "notifications",
  "sensors",
  "ui",
  "clipboard",
  "device",
  "shell",
  "shell.root",
  "network",
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
  ["shell:root", "shell.root"],
  ["storages", "storage"],
  ["java", "java_interop"]
]);

const SUPPORTED_ENTRY_EXTENSIONS = new Set([".js", ".cjs", ".mjs"]);
const MAX_DEPENDENCY_SCAN_ENTRIES = 5000;
const UNSAFE_DEPENDENCY_SCRIPT_NAMES = new Set([
  "preinstall",
  "install",
  "postinstall",
  "prepare",
  "prepack",
  "postpack"
]);
const NATIVE_EXTENSIONS = new Set([".node", ".so", ".dll", ".dylib", ".a", ".o"]);

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
  writeJson(path.join(outDir, "package.json"), {
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
  });
  fs.writeFileSync(path.join(outDir, template.entry), template.source(projectName), "utf8");
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
    files: ["project.json", "package.json", template.entry, "README.md"]
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
  validateDependencyBundle(root, issues);

  return {
    schema: "autojs6-node-project-validation-v1",
    ok: issues.filter((issue) => issue.severity === "error").length === 0,
    root,
    issueCount: issues.length,
    issues
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
    issue(issues, "PROJECT_MAIN_INVALID", file, "project.json main must be a non-empty string.", "Point main to a .js, .cjs, or .mjs entry file.");
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
    issue(issues, "PACKAGE_MAIN_INVALID", file, "package.json main must be a non-empty string when present.", "Point main to a .js, .cjs, or .mjs entry file.");
  }
}

function validateEntry(root, projectJson, packageJson, issues) {
  const entry = projectJson && nonEmptyString(projectJson.main)
    ? projectJson.main
    : packageJson && nonEmptyString(packageJson.main)
      ? packageJson.main
      : "";
  if (!entry) {
    issue(issues, "ENTRY_DECLARATION_MISSING", root, "No Node entry file was declared.", "Set project.json main or package.json main to a .js, .cjs, or .mjs file.");
    return;
  }
  const entryPath = safeRelativePath(root, entry, "ENTRY_PATH_UNSAFE", issues, "Node entry");
  if (!entryPath) return;
  const extension = path.extname(entryPath).toLowerCase();
  if (extension === ".node") {
    issue(issues, "ENTRY_NATIVE_ADDON_DISABLED", entryPath, "Native addon entry files are disabled.", "Use a JavaScript .js, .cjs, or .mjs entry file.");
  } else if (!SUPPORTED_ENTRY_EXTENSIONS.has(extension)) {
    issue(issues, "ENTRY_EXTENSION_UNSUPPORTED", entryPath, "Unsupported Node entry extension '" + extension + "'.", "Use .js, .cjs, or .mjs.");
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
          "Use a known capability such as toast, app, app.launch, image, ocr, network, storage, shell, or plugin.<id>."
        );
      }
    });
  }
}

function validateDependencyBundle(root, issues) {
  const nodeModules = path.join(root, "node_modules");
  if (!fs.existsSync(nodeModules)) return;
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
      if (NATIVE_EXTENSIONS.has(extension)) {
        issue(issues, "NATIVE_ADDON_DISABLED", file, "Dependency bundle contains native file " + relative + ".", "Use pure JavaScript dependencies only; .node and native libraries are disabled.");
      }
      if (name === "binding.gyp") {
        issue(issues, "NODE_GYP_ARTIFACT_UNSUPPORTED", file, "Dependency bundle contains binding.gyp.", "Remove native addon packages or replace them with pure JavaScript alternatives.");
      }
      if (name === "package.json") {
        validateDependencyPackageJson(file, issues);
      }
    }
    return true;
  });
}

function validateDependencyPackageJson(file, issues) {
  const json = readJson(file, "dependency package.json", issues, false);
  if (!json) return;
  const scripts = json.scripts && typeof json.scripts === "object" && !Array.isArray(json.scripts)
    ? json.scripts
    : {};
  for (const scriptName of Object.keys(scripts)) {
    if (UNSAFE_DEPENDENCY_SCRIPT_NAMES.has(scriptName)) {
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
    permissions: template.permissions
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
