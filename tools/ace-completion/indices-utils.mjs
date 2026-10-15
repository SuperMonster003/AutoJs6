import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
export const PROJECT_ROOT = path.resolve(SCRIPT_DIR, "..", "..");
// The editor assets now live in a separate plugin checkout. Keep legacy host layouts usable.
export const EDITOR_PROJECT_ROOT = process.env.AUTOJS6_ACE_EDITOR_ROOT
  ? path.resolve(process.env.AUTOJS6_ACE_EDITOR_ROOT)
  : path.resolve(PROJECT_ROOT, "../AutoJs6-Plugin-Ace-Editor");
const aceLockPath = path.join(EDITOR_PROJECT_ROOT, "tools/ace-lsp/ace-builds-lock.json");
const aceAssetRoot = JSON.parse(fs.readFileSync(aceLockPath, "utf8")).assetRoot;
export const EDITOR_ASSET_ROOT = path.resolve(EDITOR_PROJECT_ROOT, aceAssetRoot, "autojs6");
if (!EDITOR_ASSET_ROOT.startsWith(EDITOR_PROJECT_ROOT + path.sep)) {
  throw new Error(`Ace asset root escapes the editor checkout: ${aceAssetRoot}`);
}
export const TYPESCRIPT_RUNTIME = path.join(EDITOR_ASSET_ROOT, "typescript/typescript.js");
export const SOURCE_PATH = path.join(SCRIPT_DIR, "autojs6_indices.source.json");
export const GENERATED_INDEX_PATH = path.join(EDITOR_ASSET_ROOT, "autojs6_indices.js");
export const GENERATED_DTS_PATH = path.join(EDITOR_ASSET_ROOT, "types/lib.autojs6.d.ts");
export const BUNDLED_TYPES_ROOT = path.join(EDITOR_ASSET_ROOT, "types");
export const BUNDLED_AUTOJS6_DTS_ROOT = path.join(BUNDLED_TYPES_ROOT, "autojs6");
export const REPORT_DIR = path.join(
  PROJECT_ROOT,
  "build",
  "reports",
  "ace-completion",
);
export const REPORT_PATH = path.join(
  REPORT_DIR,
  "autojs6-indices-report.md",
);
export const API_MODEL_JSON_PATH = path.join(REPORT_DIR, "autojs6-api-model.json");
export const API_MODEL_REPORT_PATH = path.join(REPORT_DIR, "autojs6-api-model-report.md");

const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const MODULE_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$/;
export const PRO_ONLY_COMPLETION_NAMES = [
  "autojsPro",
  "autojspro",
  "pro",
  "uiObject",
];
const PRO_ONLY_NAMES = new Set(PRO_ONLY_COMPLETION_NAMES.map((name) => name.toLowerCase()));

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
    ...(Array.isArray(member.signatures) && member.signatures.length > 1
      ? { signatures: member.signatures.slice() }
      : {}),
    score: member.score,
  };
}

export function isProOnlyCompletionName(name) {
  return PRO_ONLY_NAMES.has(String(name || "").toLowerCase());
}

export function findProOnlyEntriesInSource(source) {
  const entries = [];
  const modules = source && source.modules || {};
  for (const moduleName of Object.keys(modules)) {
    recordProOnly(entries, `modules.${moduleName}`, moduleName);
    for (const rawMember of modules[moduleName].members || []) {
      const member = normalizeMember(rawMember);
      recordProOnly(entries, `modules.${moduleName}.members.${member.name}`, member.name);
    }
  }
  for (const name of source && source.globals || []) {
    recordProOnly(entries, `globals.${name}`, name);
  }
  for (const name of source && source.variables || []) {
    recordProOnly(entries, `variables.${name}`, name);
  }
  for (const snippet of source && source.snippets || []) {
    recordProOnly(entries, `snippets.${snippet && snippet.name}`, snippet && snippet.name);
  }
  for (const [moduleName, members] of Object.entries(source && source.globalMembers || {})) {
    recordProOnly(entries, `globalMembers.${moduleName}`, moduleName);
    if (Array.isArray(members)) {
      for (const memberName of members) {
        recordProOnly(entries, `globalMembers.${moduleName}.${memberName}`, memberName);
      }
    }
  }
  return entries;
}

export function findProOnlyEntriesInRuntimeIndices(indices) {
  const entries = [];
  for (const entry of indices && indices.globals || []) {
    recordProOnly(entries, `globals.${entry && entry.name}`, entry && entry.name);
  }
  for (const [moduleName, members] of Object.entries(indices && indices.modules || {})) {
    recordProOnly(entries, `modules.${moduleName}`, moduleName);
    for (const member of members || []) {
      recordProOnly(entries, `modules.${moduleName}.${member && member.name}`, member && member.name);
    }
  }
  for (const snippet of indices && indices.snippets || []) {
    recordProOnly(entries, `snippets.${snippet && snippet.name}`, snippet && snippet.name);
  }
  return entries;
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
    if (!MODULE_PATTERN.test(moduleName)) {
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
      if (member.signature != null && typeof member.signature !== "string") {
        errors.push(`module ${moduleName}.${member.name} signature must be a string`);
      }
      if (member.signatures != null) {
        if (!Array.isArray(member.signatures) || member.signatures.length < 2 ||
          member.signatures.some((signature) => typeof signature !== "string" || !signature)) {
          errors.push(`module ${moduleName}.${member.name} signatures must list at least two strings`);
        } else if (member.signatures[0] !== member.signature) {
          errors.push(`module ${moduleName}.${member.name} signature must equal the first overload`);
        }
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

  for (const entry of findProOnlyEntriesInSource(source)) {
    errors.push(`source contains reserved Pro-only name at ${entry.path}: ${entry.name}`);
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
        signatures: member.signatures,
        score: member.score,
      };
    });
    if (!moduleSpec.instanceOnly && !moduleName.includes(".")) globals.push({
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
  for (const name of Object.keys(source.aliases || {})) {
    globals.push({ name, type: "module", doc: `Alias of ${source.aliases[name]}` });
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
        signatures: member.signatures,
        score: member.score,
      });
    }
  }

  return {
    instanceOnlyModules: Object.keys(source.modules).filter((name) => source.modules[name].instanceOnly),
    version: source.version,
    globals: dedupeEntries(globals),
    modules,
    aliases: source.aliases || {},
    conditionalModules: source.conditionalModules || {},
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
      if (member.signature) {
        for (const signature of member.signatures || [member.signature]) {
          lines.push(`        ${signature}`);
        }
      } else if (member.kind === "variable") {
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
    if (source.modules[moduleName].instanceOnly || moduleName.includes(".")) continue;
    lines.push(`declare const ${moduleName}: AutoJs6.${interfaceName(moduleName)};`);
  }
  const globalMemberDeclarations = collectGlobalMemberDeclarations(source);
  const emittedGlobals = new Set();
  for (const name of source.globals) {
    const member = globalMemberDeclarations.get(name);
    if (member && member.kind !== "variable") {
      lines.push(renderGlobalMemberDeclaration(member));
      emittedGlobals.add(name);
    } else {
      lines.push(`declare function ${name}(...args: any[]): any;`);
    }
  }
  for (const name of source.variables) {
    const member = globalMemberDeclarations.get(name);
    if (member && member.kind === "variable") {
      lines.push(renderGlobalMemberDeclaration(member));
      emittedGlobals.add(name);
    } else {
      lines.push(`declare const ${name}: any;`);
    }
  }
  for (const [moduleName, memberNames] of Object.entries(source.globalMembers)) {
    const moduleMembers = source.modules[moduleName].members.map(normalizeMember);
    const selected = memberNames === "*"
      ? moduleMembers
      : moduleMembers.filter((member) => memberNames.includes(member.name));
    for (const member of selected) {
      if (emittedGlobals.has(member.name) ||
        source.globals.includes(member.name) ||
        source.variables.includes(member.name)) {
        continue;
      }
      lines.push(renderGlobalMemberDeclaration(member));
      emittedGlobals.add(member.name);
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
    "- Static completion source is generated from the bundled selected AutoJs6 TypeScript declarations.",
    "- Pro-only index files remain fallback inputs only; this generated source is the AutoJs6 authority.",
    "- The generated JavaScript preserves `AUTOJS_INDICES` compatibility for `autojs6_completer.js`.",
    "",
  ].join("\n");
}

export function writeIfChanged(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  if (
    fs.existsSync(filePath) &&
    normalizeLineEndings(fs.readFileSync(filePath, "utf8")) === normalizeLineEndings(content)
  ) {
    return false;
  }
  fs.writeFileSync(filePath, content, "utf8");
  return true;
}

export function normalizeLineEndings(content) {
  return content.replace(/\r\n?/g, "\n");
}

export function relativeToRoot(filePath) {
  return path.relative(PROJECT_ROOT, filePath).replaceAll(path.sep, "/");
}

function dedupeEntries(entries) {
  const seen = new Set();
  const positions = new Map();
  const result = [];
  for (const entry of entries) {
    const key = `${entry.type}:${entry.name}`;
    if (seen.has(key)) {
      const index = positions.get(key);
      result[index] = mergeCompletionEntry(result[index], entry);
      continue;
    }
    seen.add(key);
    positions.set(key, result.length);
    result.push(entry);
  }
  return result;
}

function collectGlobalMemberDeclarations(source) {
  const declarations = new Map();
  for (const [moduleName, memberNames] of Object.entries(source.globalMembers || {})) {
    const moduleSpec = source.modules[moduleName];
    if (!moduleSpec) {
      continue;
    }
    const moduleMembers = moduleSpec.members.map(normalizeMember);
    const selected = memberNames === "*"
      ? moduleMembers
      : moduleMembers.filter((member) => memberNames.includes(member.name));
    for (const member of selected) {
      declarations.set(member.name, mergeCompletionEntry(declarations.get(member.name), member));
    }
  }
  return declarations;
}

function renderGlobalMemberDeclaration(member) {
  if (member.kind === "variable") {
    return renderGlobalVariableDeclaration(member);
  }
  return renderGlobalFunctionDeclaration(member);
}

function renderGlobalFunctionDeclaration(member) {
  if (member.signature) {
    const declarations = [];
    for (const signature of member.signatures || [member.signature]) {
      const suffix = signature.replace(new RegExp(`^${escapeRegExp(member.name)}\\??\\s*`), "");
      if (suffix.startsWith("(") || suffix.startsWith("<")) {
        declarations.push(`declare function ${member.name}${suffix}`);
      }
    }
    if (declarations.length) {
      return declarations.join("\n");
    }
  }
  return `declare function ${member.name}(...args: any[]): any;`;
}

function renderGlobalVariableDeclaration(member) {
  if (member.signature) {
    const propertyMatch = new RegExp(`^${escapeRegExp(member.name)}\\??\\s*:\\s*(.+);$`).exec(member.signature);
    if (propertyMatch) {
      return `declare const ${member.name}: ${propertyMatch[1]};`;
    }
  }
  return `declare const ${member.name}: any;`;
}

function mergeCompletionEntry(existing, candidate) {
  if (!existing) {
    return candidate;
  }
  if (!candidate) {
    return existing;
  }
  const existingScore = completionEntryQuality(existing);
  const candidateScore = completionEntryQuality(candidate);
  const primary = candidateScore > existingScore ? candidate : existing;
  const secondary = primary === candidate ? existing : candidate;
  return {
    ...secondary,
    ...primary,
    doc: primary.doc || secondary.doc,
    signature: primary.signature || secondary.signature,
    signatures: primary.signatures || secondary.signatures,
    score: primary.score ?? secondary.score,
  };
}

function completionEntryQuality(entry) {
  return (entry && entry.signature ? 8 : 0) +
    (entry && entry.doc ? 4 : 0) +
    (entry && entry.score != null ? 2 : 0);
}

function recordProOnly(entries, entryPath, name) {
  if (isProOnlyCompletionName(name)) {
    entries.push({
      path: entryPath,
      name: String(name),
    });
  }
}

function indentJson(value, spaces) {
  const json = JSON.stringify(value, null, 4);
  const prefix = " ".repeat(spaces);
  return json.split("\n").map((line, index) => index === 0 ? line : prefix + line).join("\n");
}

function interfaceName(moduleName) {
  return (moduleName.charAt(0).toUpperCase() + moduleName.slice(1)).replace(/\./g, "_");
}

function escapeRegExp(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
