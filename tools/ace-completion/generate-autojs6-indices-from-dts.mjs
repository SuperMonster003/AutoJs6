#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import {
  BUNDLED_AUTOJS6_DTS_ROOT,
  SOURCE_PATH,
  assertValidSource,
  normalizeMember,
  readSource,
  relativeToRoot,
  writeIfChanged,
} from "./indices-utils.mjs";

const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const PRO_ONLY_NAMES = new Set(["autojsPro", "autojspro", "pro", "uiObject"]);
const INTERNAL_GLOBAL_PREFIXES = ["$", "__"];

const dtsRoot = path.resolve(process.argv[2] || BUNDLED_AUTOJS6_DTS_ROOT);
if (!fs.existsSync(dtsRoot)) {
  throw new Error(`Bundled AutoJs6 declaration root not found: ${dtsRoot}`);
}

const baseSource = readSource();
const dtsFiles = fs.readdirSync(dtsRoot)
  .filter((fileName) => fileName === "aj6-declarations.d.ts" || /^aj6-int-.+\.d\.ts$/.test(fileName))
  .sort((a, b) => a.localeCompare(b));

if (dtsFiles.length === 0) {
  throw new Error(`No bundled AutoJs6 declaration files found under ${dtsRoot}`);
}

const dtsText = dtsFiles
  .map((fileName) => `\n// ${fileName}\n${fs.readFileSync(path.join(dtsRoot, fileName), "utf8")}`)
  .join("\n");
const parsedText = stripComments(dtsText);

const internalTypes = parseInternalTypes(parsedText);
const globalDeclarations = parseGlobalDeclarations(parsedText);
const modules = buildModules(baseSource.modules, internalTypes, globalDeclarations.moduleBindings);

const source = {
  "$schema": baseSource.$schema || "./autojs6_indices.schema.json",
  version: baseSource.version || 1,
  modules,
  globals: mergeIdentifierLists(baseSource.globals, globalDeclarations.functions),
  variables: mergeIdentifierLists(baseSource.variables, globalDeclarations.variables),
  globalMembers: sanitizeGlobalMembers(baseSource.globalMembers, modules),
  snippets: baseSource.snippets || [],
};

assertValidSource(source);

const changed = writeIfChanged(SOURCE_PATH, JSON.stringify(source, null, 2) + "\n");
const moduleCount = Object.keys(source.modules).length;
const memberCount = Object.values(source.modules)
  .reduce((count, moduleSpec) => count + moduleSpec.members.length, 0);

console.log(`${changed ? "updated" : "unchanged"}: ${relativeToRoot(SOURCE_PATH)}`);
console.log(`Parsed ${dtsFiles.length} d.ts files into ${moduleCount} modules and ${memberCount} module members.`);

function parseInternalTypes(text) {
  const types = new Map();
  const namespaceRegex = /declare\s+namespace\s+Internal\s*\{/g;
  let match;
  while ((match = namespaceRegex.exec(text)) !== null) {
    const openBrace = text.indexOf("{", match.index);
    const closeBrace = findMatchingBrace(text, openBrace);
    if (closeBrace < 0) {
      continue;
    }
    const namespaceBody = text.slice(openBrace + 1, closeBrace);
    for (const declaration of parseTopLevelTypeDeclarations(namespaceBody)) {
      const existing = types.get(declaration.name) || [];
      types.set(declaration.name, mergeMembers(existing, declaration.members));
    }
    namespaceRegex.lastIndex = closeBrace + 1;
  }
  return types;
}

function parseTopLevelTypeDeclarations(namespaceBody) {
  const declarations = [];
  const typeRegex = /\b(?:export\s+)?(?:abstract\s+)?(?:class|interface)\s+([A-Za-z_$][A-Za-z0-9_$]*)[^{};]*\{/g;
  let match;
  while ((match = typeRegex.exec(namespaceBody)) !== null) {
    if (braceDepthAt(namespaceBody, match.index) !== 0) {
      continue;
    }
    const openBrace = namespaceBody.indexOf("{", match.index);
    const closeBrace = findMatchingBrace(namespaceBody, openBrace);
    if (closeBrace < 0) {
      continue;
    }
    declarations.push({
      name: match[1],
      members: parseTypeMembers(namespaceBody.slice(openBrace + 1, closeBrace)),
    });
    typeRegex.lastIndex = closeBrace + 1;
  }
  return declarations;
}

function parseTypeMembers(body) {
  const members = [];
  for (const declaration of splitTopLevelStatements(body)) {
    const member = parseMemberDeclaration(declaration);
    if (member && isPublicCompletionName(member.name)) {
      members.push(member);
    }
  }
  return mergeMembers([], members);
}

function parseMemberDeclaration(rawDeclaration) {
  let declaration = collapseWhitespace(rawDeclaration);
  declaration = declaration.replace(/^(?:public|protected|private|declare)\s+/, "");
  declaration = declaration.replace(/^(?:abstract|override)\s+/, "");
  declaration = declaration.replace(/^static\s+/, "");
  declaration = declaration.replace(/^readonly\s+/, "");

  if (!declaration || declaration.startsWith("[") || declaration.startsWith("(")) {
    return null;
  }
  if (/^constructor\s*\(/.test(declaration)) {
    return null;
  }

  const accessorMatch = /^(?:get|set)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(/.exec(declaration);
  if (accessorMatch) {
    return {
      name: accessorMatch[1],
      kind: "variable",
      doc: declaration,
    };
  }

  const methodMatch = /^([A-Za-z_$][A-Za-z0-9_$]*)\??\s*(?:<[^(){};]*>)?\s*\(/.exec(declaration);
  if (methodMatch) {
    return {
      name: methodMatch[1],
      kind: "function",
      doc: declaration,
    };
  }

  const propertyMatch = /^([A-Za-z_$][A-Za-z0-9_$]*)\??\s*:/.exec(declaration);
  if (propertyMatch) {
    return {
      name: propertyMatch[1],
      kind: "variable",
      doc: declaration,
    };
  }

  return null;
}

function parseGlobalDeclarations(text) {
  const functions = [];
  const variables = [];
  const moduleBindings = new Map();

  const functionRegex = /declare\s+function\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*(?:<[^(){};]*>)?\s*\(/g;
  let match;
  while ((match = functionRegex.exec(text)) !== null) {
    const name = match[1];
    if (braceDepthAt(text, match.index) === 0 && isPublicCompletionName(name)) {
      functions.push(name);
    }
  }

  const variableRegex = /declare\s+(?:let|const|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*:\s*([^;]+);/g;
  while ((match = variableRegex.exec(text)) !== null) {
    const name = match[1];
    const type = collapseWhitespace(match[2]);
    if (braceDepthAt(text, match.index) !== 0 || !isPublicCompletionName(name)) {
      continue;
    }
    const internalTypeMatch = /^Internal\.([A-Za-z_$][A-Za-z0-9_$]*)$/.exec(type);
    if (internalTypeMatch) {
      moduleBindings.set(name, internalTypeMatch[1]);
    } else {
      variables.push(name);
    }
  }

  return {
    functions,
    variables,
    moduleBindings,
  };
}

function buildModules(baseModules, internalTypes, moduleBindings) {
  const modules = new Map();

  for (const [moduleName, moduleSpec] of Object.entries(baseModules || {})) {
    modules.set(moduleName, {
      doc: moduleSpec.doc || moduleDoc(moduleName, ""),
      score: moduleSpec.score,
      members: mergeMembers([], moduleSpec.members || []),
    });
  }

  for (const [moduleName, typeName] of moduleBindings.entries()) {
    const parsedMembers = internalTypes.get(typeName) || [];
    if (parsedMembers.length === 0) {
      continue;
    }
    const existing = modules.get(moduleName);
    modules.set(moduleName, {
      doc: existing && existing.doc || moduleDoc(moduleName, typeName),
      score: existing && existing.score,
      members: mergeMembers(parsedMembers, existing && existing.members || []),
    });
  }

  return Object.fromEntries(
    [...modules.entries()]
      .filter(([moduleName, moduleSpec]) => isPublicCompletionName(moduleName) && moduleSpec.members.length > 0)
      .sort(([a], [b]) => a.localeCompare(b)),
  );
}

function sanitizeGlobalMembers(globalMembers, modules) {
  const sanitized = {};
  for (const [moduleName, members] of Object.entries(globalMembers || {})) {
    if (!modules[moduleName]) {
      continue;
    }
    if (members === "*") {
      sanitized[moduleName] = "*";
      continue;
    }
    const moduleMemberNames = new Set(modules[moduleName].members.map((member) => normalizeMember(member).name));
    const selected = (members || []).filter((memberName) => moduleMemberNames.has(memberName));
    if (selected.length > 0) {
      sanitized[moduleName] = selected;
    }
  }
  return sanitized;
}

function mergeMembers(primaryMembers, fallbackMembers = []) {
  const seen = new Set();
  const members = [];
  for (const rawMember of [...primaryMembers, ...fallbackMembers]) {
    const member = normalizeMember(rawMember);
    if (!isPublicCompletionName(member.name) || seen.has(member.name)) {
      continue;
    }
    seen.add(member.name);
    members.push({
      name: member.name,
      kind: member.kind || "function",
      doc: member.doc || "",
      ...(member.signature ? { signature: member.signature } : {}),
      ...(member.score ? { score: member.score } : {}),
    });
  }
  return members;
}

function mergeIdentifierLists(primary = [], secondary = []) {
  const seen = new Set();
  const names = [];
  for (const name of [...primary, ...secondary]) {
    if (!isPublicCompletionName(name) || seen.has(name)) {
      continue;
    }
    seen.add(name);
    names.push(name);
  }
  return names;
}

function splitTopLevelStatements(body) {
  const statements = [];
  let start = 0;
  let parenDepth = 0;
  let bracketDepth = 0;
  let braceDepth = 0;
  let state = "normal";

  for (let i = 0; i < body.length; i += 1) {
    const ch = body[i];
    const next = body[i + 1];

    if (state === "single") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "'") {
        state = "normal";
      }
      continue;
    }
    if (state === "double") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "\"") {
        state = "normal";
      }
      continue;
    }
    if (state === "template") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "`") {
        state = "normal";
      }
      continue;
    }

    if (ch === "'") {
      state = "single";
    } else if (ch === "\"") {
      state = "double";
    } else if (ch === "`") {
      state = "template";
    } else if (ch === "(") {
      parenDepth += 1;
    } else if (ch === ")") {
      parenDepth = Math.max(0, parenDepth - 1);
    } else if (ch === "[") {
      bracketDepth += 1;
    } else if (ch === "]") {
      bracketDepth = Math.max(0, bracketDepth - 1);
    } else if (ch === "{") {
      braceDepth += 1;
    } else if (ch === "}") {
      braceDepth = Math.max(0, braceDepth - 1);
    } else if (ch === ";" && parenDepth === 0 && bracketDepth === 0 && braceDepth === 0) {
      statements.push(body.slice(start, i + 1));
      start = i + 1;
    } else if (ch === "\r" && next === "\n") {
      i += 1;
    }
  }

  return statements;
}

function findMatchingBrace(text, openBrace) {
  if (openBrace < 0 || text[openBrace] !== "{") {
    return -1;
  }
  let depth = 0;
  let state = "normal";
  for (let i = openBrace; i < text.length; i += 1) {
    const ch = text[i];
    if (state === "single") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "'") {
        state = "normal";
      }
      continue;
    }
    if (state === "double") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "\"") {
        state = "normal";
      }
      continue;
    }
    if (state === "template") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "`") {
        state = "normal";
      }
      continue;
    }
    if (ch === "'") {
      state = "single";
    } else if (ch === "\"") {
      state = "double";
    } else if (ch === "`") {
      state = "template";
    } else if (ch === "{") {
      depth += 1;
    } else if (ch === "}") {
      depth -= 1;
      if (depth === 0) {
        return i;
      }
    }
  }
  return -1;
}

function braceDepthAt(text, targetIndex) {
  let depth = 0;
  let state = "normal";
  for (let i = 0; i < targetIndex; i += 1) {
    const ch = text[i];
    if (state === "single") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "'") {
        state = "normal";
      }
      continue;
    }
    if (state === "double") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "\"") {
        state = "normal";
      }
      continue;
    }
    if (state === "template") {
      if (ch === "\\") {
        i += 1;
      } else if (ch === "`") {
        state = "normal";
      }
      continue;
    }
    if (ch === "'") {
      state = "single";
    } else if (ch === "\"") {
      state = "double";
    } else if (ch === "`") {
      state = "template";
    } else if (ch === "{") {
      depth += 1;
    } else if (ch === "}") {
      depth = Math.max(0, depth - 1);
    }
  }
  return depth;
}

function stripComments(text) {
  let result = "";
  let state = "normal";
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    const next = text[i + 1];

    if (state === "lineComment") {
      if (ch === "\n") {
        state = "normal";
        result += "\n";
      } else {
        result += " ";
      }
      continue;
    }
    if (state === "blockComment") {
      if (ch === "*" && next === "/") {
        result += "  ";
        i += 1;
        state = "normal";
      } else {
        result += ch === "\n" ? "\n" : " ";
      }
      continue;
    }
    if (state === "single") {
      result += ch;
      if (ch === "\\") {
        i += 1;
        result += text[i] || "";
      } else if (ch === "'") {
        state = "normal";
      }
      continue;
    }
    if (state === "double") {
      result += ch;
      if (ch === "\\") {
        i += 1;
        result += text[i] || "";
      } else if (ch === "\"") {
        state = "normal";
      }
      continue;
    }
    if (state === "template") {
      result += ch;
      if (ch === "\\") {
        i += 1;
        result += text[i] || "";
      } else if (ch === "`") {
        state = "normal";
      }
      continue;
    }

    if (ch === "/" && next === "/") {
      result += "  ";
      i += 1;
      state = "lineComment";
    } else if (ch === "/" && next === "*") {
      result += "  ";
      i += 1;
      state = "blockComment";
    } else {
      result += ch;
      if (ch === "'") {
        state = "single";
      } else if (ch === "\"") {
        state = "double";
      } else if (ch === "`") {
        state = "template";
      }
    }
  }
  return result;
}

function isPublicCompletionName(name) {
  return IDENTIFIER_PATTERN.test(name) &&
    !PRO_ONLY_NAMES.has(name) &&
    !INTERNAL_GLOBAL_PREFIXES.some((prefix) => name.startsWith(prefix));
}

function collapseWhitespace(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function moduleDoc(moduleName, typeName) {
  return typeName
    ? `AutoJs6 ${typeName} API`
    : `AutoJs6 ${moduleName} API`;
}
