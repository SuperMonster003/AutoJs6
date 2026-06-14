import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
export const PROJECT_ROOT = path.resolve(SCRIPT_DIR, "..", "..");
export const SOURCE_PATH = path.join(SCRIPT_DIR, "autojs6_indices.source.json");
export const GENERATED_INDEX_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs6_indices.js",
);
export const GENERATED_DTS_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "types",
  "lib.autojs6.d.ts",
);
export const REPORT_PATH = path.join(
  PROJECT_ROOT,
  "build",
  "reports",
  "ace-completion",
  "autojs6-indices-report.md",
);

const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const PRO_ONLY_NAMES = new Set([
  "autojsPro",
  "autojspro",
  "pro",
  "uiObject",
  "web",
]);

export function readSource(sourcePath = SOURCE_PATH) {
  return JSON.parse(fs.readFileSync(sourcePath, "utf8"));
}

export function normalizeMember(member) {
  if (typeof member === "string") {
    return {
      name: member,
      kind: "function",
      doc: "",
    };
  }
  return {
    name: member.name,
    kind: member.kind || "function",
    doc: member.doc || "",
    signature: member.signature,
    score: member.score,
  };
}

export function validateSource(source) {
  const errors = [];

  if (!source || typeof source !== "object" || Array.isArray(source)) {
    errors.push("source must be an object");
    return errors;
  }
  if (!Number.isInteger(source.version) || source.version < 1) {
    errors.push("version must be a positive integer");
  }
  if (!source.modules || typeof source.modules !== "object" || Array.isArray(source.modules)) {
    errors.push("modules must be an object");
  }
  for (const field of ["globals", "variables", "snippets"]) {
    if (!Array.isArray(source[field])) {
      errors.push(`${field} must be an array`);
    }
  }
  if (!source.globalMembers || typeof source.globalMembers !== "object" || Array.isArray(source.globalMembers)) {
    errors.push("globalMembers must be an object");
  }

  const modules = source.modules || {};
  for (const [moduleName, moduleSpec] of Object.entries(modules)) {
    if (!IDENTIFIER_PATTERN.test(moduleName)) {
      errors.push(`invalid module name: ${moduleName}`);
    }
    if (!moduleSpec || typeof moduleSpec !== "object" || Array.isArray(moduleSpec)) {
      errors.push(`module ${moduleName} must be an object`);
      continue;
    }
    if (typeof moduleSpec.doc !== "string") {
      errors.push(`module ${moduleName} doc must be a string`);
    }
    if (!Array.isArray(moduleSpec.members) || moduleSpec.members.length === 0) {
      errors.push(`module ${moduleName} must have members`);
      continue;
    }
    const seenMembers = new Set();
    for (const rawMember of moduleSpec.members) {
      const member = normalizeMember(rawMember);
      if (!member.name || !IDENTIFIER_PATTERN.test(member.name)) {
        errors.push(`module ${moduleName} has invalid member: ${JSON.stringify(rawMember)}`);
      }
      if (seenMembers.has(member.name)) {
        errors.push(`module ${moduleName} has duplicate member: ${member.name}`);
      }
      seenMembers.add(member.name);
    }
  }

  for (const field of ["globals", "variables"]) {
    const seen = new Set();
    for (const name of source[field] || []) {
      if (!IDENTIFIER_PATTERN.test(name)) {
        errors.push(`${field} contains invalid identifier: ${name}`);
      }
      if (seen.has(name)) {
        errors.push(`${field} contains duplicate identifier: ${name}`);
      }
      seen.add(name);
    }
  }

  for (const [moduleName, members] of Object.entries(source.globalMembers || {})) {
    if (!modules[moduleName]) {
      errors.push(`globalMembers references unknown module: ${moduleName}`);
      continue;
    }
    if (members === "*") {
      continue;
    }
    if (!Array.isArray(members)) {
      errors.push(`globalMembers.${moduleName} must be "*" or an array`);
      continue;
    }
    const moduleMembers = new Set(modules[moduleName].members.map((member) => normalizeMember(member).name));
    for (const memberName of members) {
      if (!moduleMembers.has(memberName)) {
        errors.push(`globalMembers.${moduleName} references unknown member: ${memberName}`);
      }
    }
  }

  const flatNames = new Set([
    ...Object.keys(modules),
    ...(source.globals || []),
    ...(source.variables || []),
  ]);
  for (const moduleSpec of Object.values(modules)) {
    for (const rawMember of moduleSpec.members || []) {
      flatNames.add(normalizeMember(rawMember).name);
    }
  }
  for (const name of flatNames) {
    if (PRO_ONLY_NAMES.has(name)) {
      errors.push(`source contains reserved Pro-only name: ${name}`);
    }
  }

  return errors;
}

export function assertValidSource(source) {
  const errors = validateSource(source);
  if (errors.length > 0) {
    throw new Error(`Invalid AutoJs6 indices source:\n${errors.map((error) => `- ${error}`).join("\n")}`);
  }
}

export function buildRuntimeIndices(source) {
  assertValidSource(source);
  const modules = {};
  const globals = [];

  for (const [moduleName, moduleSpec] of Object.entries(source.modules)) {
    modules[moduleName] = moduleSpec.members.map((rawMember) => {
      const member = normalizeMember(rawMember);
      return {
        name: member.name,
        type: member.kind,
        doc: member.doc,
        signature: member.signature,
        score: member.score,
      };
    });
    globals.push({
      name: moduleName,
      type: "module",
      doc: moduleSpec.doc,
      score: moduleSpec.score,
    });
  }

  for (const name of source.globals) {
    globals.push({
      name,
      type: "function",
      doc: "",
    });
  }
  for (const name of source.variables) {
    globals.push({
      name,
      type: "variable",
      doc: "",
    });
  }
  for (const [moduleName, memberNames] of Object.entries(source.globalMembers)) {
    const moduleMembers = source.modules[moduleName].members.map(normalizeMember);
    const selected = memberNames === "*"
      ? moduleMembers
      : moduleMembers.filter((member) => memberNames.includes(member.name));
    for (const member of selected) {
      globals.push({
        name: member.name,
        type: member.kind,
        doc: member.doc,
        signature: member.signature,
        score: member.score,
      });
    }
  }

  return {
    version: source.version,
    globals: dedupeEntries(globals),
    modules,
    snippets: source.snippets,
  };
}

export function renderIndexJs(indices) {
  return [
    "(function(global) {",
    "    \"use strict\";",
    "",
    "    var indices = " + indentJson(indices, 4) + ";",
    "",
    "    global.AUTOJS6_INDICES = indices;",
    "    global.AutoJsAceIndices = indices;",
    "    global.AUTOJS_INDICES = global.AUTOJS6_INDICES || global.AUTOJS_INDICES;",
    "})(window);",
    "",
  ].join("\n");
}

export function renderDts(source) {
  assertValidSource(source);
  const lines = [
    "// Auto-generated by tools/ace-completion/generate-dts.mjs.",
    "// Manual edits will be overwritten.",
    "",
    "declare namespace AutoJs6 {",
  ];

  for (const [moduleName, moduleSpec] of Object.entries(source.modules)) {
    lines.push(`    interface ${interfaceName(moduleName)} {`);
    for (const rawMember of moduleSpec.members) {
      const member = normalizeMember(rawMember);
      if (member.kind === "variable") {
        lines.push(`        ${member.name}: any;`);
      } else {
        lines.push(`        ${member.name}(...args: any[]): any;`);
      }
    }
    lines.push("    }");
    lines.push("");
  }
  lines.push("}");
  lines.push("");

  for (const moduleName of Object.keys(source.modules)) {
    lines.push(`declare const ${moduleName}: AutoJs6.${interfaceName(moduleName)};`);
  }
  for (const name of source.globals) {
    lines.push(`declare function ${name}(...args: any[]): any;`);
  }
  for (const name of source.variables) {
    lines.push(`declare const ${name}: any;`);
  }
  for (const [moduleName, memberNames] of Object.entries(source.globalMembers)) {
    const moduleMembers = source.modules[moduleName].members.map(normalizeMember);
    const selected = memberNames === "*"
      ? moduleMembers
      : moduleMembers.filter((member) => memberNames.includes(member.name));
    for (const member of selected) {
      if (source.globals.includes(member.name) || source.variables.includes(member.name)) {
        continue;
      }
      if (member.kind === "variable") {
        lines.push(`declare const ${member.name}: any;`);
      } else {
        lines.push(`declare function ${member.name}(...args: any[]): any;`);
      }
    }
  }
  lines.push("");
  return lines.join("\n");
}

export function renderReport(source, indices) {
  const moduleCount = Object.keys(source.modules).length;
  const memberCount = Object.values(source.modules)
    .reduce((count, moduleSpec) => count + moduleSpec.members.length, 0);
  return [
    "# AutoJs6 ACE Completion Report",
    "",
    `- Source version: ${source.version}`,
    `- Module count: ${moduleCount}`,
    `- Module member count: ${memberCount}`,
    `- Global completion count: ${indices.globals.length}`,
    `- Snippet count: ${indices.snippets.length}`,
    "",
    "## Required Smoke Entries",
    "",
    "- `toast`",
    "- `app.launch`",
    "- `files.read`",
    "- `images.findImage`",
    "- `device.getBattery`",
    "- `console.log`",
    "- `threads.start`",
    "- `engines.myEngine`",
    "",
    "## Notes",
    "",
    "- Pro-only index files remain fallback inputs only; this generated source is the AutoJs6 authority.",
    "- The generated JavaScript preserves `AUTOJS_INDICES` compatibility for `autojs6_completer.js`.",
    "",
  ].join("\n");
}

export function writeIfChanged(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  if (fs.existsSync(filePath) && fs.readFileSync(filePath, "utf8") === content) {
    return false;
  }
  fs.writeFileSync(filePath, content, "utf8");
  return true;
}

export function relativeToRoot(filePath) {
  return path.relative(PROJECT_ROOT, filePath).replaceAll(path.sep, "/");
}

function dedupeEntries(entries) {
  const seen = new Set();
  const result = [];
  for (const entry of entries) {
    const key = `${entry.type}:${entry.name}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    result.push(entry);
  }
  return result;
}

function indentJson(value, spaces) {
  const json = JSON.stringify(value, null, 4);
  const prefix = " ".repeat(spaces);
  return json.split("\n").map((line, index) => index === 0 ? line : prefix + line).join("\n");
}

function interfaceName(moduleName) {
  return moduleName.charAt(0).toUpperCase() + moduleName.slice(1);
}
