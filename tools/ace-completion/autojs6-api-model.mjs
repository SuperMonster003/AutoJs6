import fs from "node:fs";
import path from "node:path";
import {
  BUNDLED_AUTOJS6_DTS_ROOT,
  normalizeMember,
  relativeToRoot,
} from "./indices-utils.mjs";

const API_MODEL_VERSION = 1;
const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const PRO_ONLY_NAMES = new Set(["autojsPro", "autojspro", "pro", "uiObject"]);
const INTERNAL_GLOBAL_PREFIXES = ["$", "__"];
const MEMBER_KINDS = new Set(["function", "member", "module", "variable"]);

export function buildAutoJs6ApiModel(dtsRoot = BUNDLED_AUTOJS6_DTS_ROOT) {
  const resolvedRoot = path.resolve(dtsRoot);
  if (!fs.existsSync(resolvedRoot)) {
    throw new Error(`Bundled AutoJs6 declaration root not found: ${resolvedRoot}`);
  }

  const dtsFiles = listAutoJs6DtsFiles(resolvedRoot);
  if (dtsFiles.length === 0) {
    throw new Error(`No bundled AutoJs6 declaration files found under ${resolvedRoot}`);
  }

  const dtsText = dtsFiles
    .map((fileName) => `\n// ${fileName}\n${fs.readFileSync(path.join(resolvedRoot, fileName), "utf8")}`)
    .join("\n");
  const parsedText = stripComments(dtsText);
  const internalTypes = parseInternalTypes(parsedText);
  const globalDeclarations = parseGlobalDeclarations(parsedText);
  const globalFunctions = dedupeNames(globalDeclarations.functions);
  const globalVariables = dedupeNames(globalDeclarations.variables);
  const modules = buildDeclarationModules(internalTypes, globalDeclarations.moduleBindings);
  const declarationBackedMemberCount = modules
    .reduce((count, moduleSpec) => count + moduleSpec.members.length, 0);

  return {
    version: API_MODEL_VERSION,
    declarationRoot: relativeToRoot(resolvedRoot),
    files: dtsFiles.map((fileName) => ({
      name: fileName,
      path: relativeToRoot(path.join(resolvedRoot, fileName)),
    })),
    filters: {
      proOnlyNames: [...PRO_ONLY_NAMES].sort((a, b) => a.localeCompare(b)),
      internalGlobalPrefixes: [...INTERNAL_GLOBAL_PREFIXES],
    },
    moduleBindings: [...globalDeclarations.moduleBindings.entries()]
      .map(([moduleName, typeName]) => ({
        moduleName,
        typeName,
        memberCount: (internalTypes.get(typeName) || []).length,
      }))
      .sort((a, b) => a.moduleName.localeCompare(b.moduleName)),
    modules,
    globals: {
      functions: globalFunctions.map((name) => ({ name, kind: "function" })),
      variables: globalVariables.map((name) => ({ name, kind: "variable" })),
    },
    summary: {
      declarationFileCount: dtsFiles.length,
      declarationBackedModuleCount: modules.length,
      declarationBackedMemberCount,
      globalFunctionCount: globalFunctions.length,
      globalVariableCount: globalVariables.length,
    },
  };
}

export function buildAutoJs6CompletionSource(baseSource, apiModel) {
  assertValidApiModel(apiModel);
  const source = {
    "$schema": baseSource.$schema || "./autojs6_indices.schema.json",
    version: baseSource.version || 1,
    modules: mergeSourceModules(baseSource.modules, apiModel.modules),
    globals: mergeIdentifierLists(
      baseSource.globals,
      apiModel.globals.functions.map((entry) => entry.name),
    ),
    variables: mergeIdentifierLists(
      baseSource.variables,
      apiModel.globals.variables.map((entry) => entry.name),
    ),
    globalMembers: {},
    snippets: baseSource.snippets || [],
  };
  source.globalMembers = sanitizeGlobalMembers(baseSource.globalMembers, source.modules);
  return source;
}

export function validateApiModel(apiModel) {
  const errors = [];
  if (!apiModel || typeof apiModel !== "object" || Array.isArray(apiModel)) {
    return ["api model must be an object"];
  }
  if (!Number.isInteger(apiModel.version) || apiModel.version < 1) {
    errors.push("api model version must be a positive integer");
  }
  if (typeof apiModel.declarationRoot !== "string" || apiModel.declarationRoot.length === 0) {
    errors.push("api model declarationRoot must be a string");
  }
  if (!Array.isArray(apiModel.files) || apiModel.files.length === 0) {
    errors.push("api model files must be a non-empty array");
  }
  if (!Array.isArray(apiModel.moduleBindings)) {
    errors.push("api model moduleBindings must be an array");
  }
  if (!Array.isArray(apiModel.modules)) {
    errors.push("api model modules must be an array");
  }
  if (!apiModel.globals || typeof apiModel.globals !== "object" || Array.isArray(apiModel.globals)) {
    errors.push("api model globals must be an object");
  }
  if (!apiModel.summary || typeof apiModel.summary !== "object" || Array.isArray(apiModel.summary)) {
    errors.push("api model summary must be an object");
  }

  const seenFiles = new Set();
  for (const file of apiModel.files || []) {
    if (!file || typeof file !== "object" || Array.isArray(file)) {
      errors.push(`api model file must be an object: ${JSON.stringify(file)}`);
      continue;
    }
    if (typeof file.name !== "string" || file.name.length === 0) {
      errors.push(`api model file has invalid name: ${JSON.stringify(file)}`);
    }
    if (typeof file.path !== "string" || file.path.length === 0) {
      errors.push(`api model file has invalid path: ${JSON.stringify(file)}`);
    }
    if (seenFiles.has(file.name)) {
      errors.push(`api model has duplicate file: ${file.name}`);
    }
    seenFiles.add(file.name);
  }

  const seenModules = new Set();
  for (const moduleSpec of apiModel.modules || []) {
    validateModelModule(moduleSpec, errors, seenModules);
  }

  for (const field of ["functions", "variables"]) {
    const entries = apiModel.globals && apiModel.globals[field];
    if (!Array.isArray(entries)) {
      errors.push(`api model globals.${field} must be an array`);
      continue;
    }
    const seen = new Set();
    for (const entry of entries) {
      validateModelGlobal(entry, field, errors);
      if (entry && seen.has(entry.name)) {
        errors.push(`api model globals.${field} has duplicate entry: ${entry.name}`);
      }
      if (entry) {
        seen.add(entry.name);
      }
    }
  }

  const summary = apiModel.summary || {};
  const actualMemberCount = (apiModel.modules || [])
    .reduce((count, moduleSpec) => count + ((moduleSpec && moduleSpec.members || []).length), 0);
  if (summary.declarationFileCount !== (apiModel.files || []).length) {
    errors.push("api model summary declarationFileCount is stale");
  }
  if (summary.declarationBackedModuleCount !== (apiModel.modules || []).length) {
    errors.push("api model summary declarationBackedModuleCount is stale");
  }
  if (summary.declarationBackedMemberCount !== actualMemberCount) {
    errors.push("api model summary declarationBackedMemberCount is stale");
  }

  return errors;
}

export function assertValidApiModel(apiModel) {
  const errors = validateApiModel(apiModel);
  if (errors.length > 0) {
    throw new Error(`Invalid AutoJs6 API model:\n${errors.map((error) => `- ${error}`).join("\n")}`);
  }
}

export function renderApiModelReport(apiModel) {
  assertValidApiModel(apiModel);
  return [
    "# AutoJs6 API Model Report",
    "",
    `- Model version: ${apiModel.version}`,
    `- Declaration root: ${apiModel.declarationRoot}`,
    `- Declaration file count: ${apiModel.summary.declarationFileCount}`,
    `- Declaration-backed module count: ${apiModel.summary.declarationBackedModuleCount}`,
    `- Declaration-backed member count: ${apiModel.summary.declarationBackedMemberCount}`,
    `- Global function count: ${apiModel.summary.globalFunctionCount}`,
    `- Global variable count: ${apiModel.summary.globalVariableCount}`,
    "",
    "## Declaration Files",
    "",
    ...apiModel.files.map((file) => `- \`${file.name}\``),
    "",
    "## Notes",
    "",
    "- This model is generated from the bundled selected AutoJs6 TypeScript declarations.",
    "- Completion source generation merges this declaration-backed model with explicit source JSON fallbacks.",
    "- Pro-only names and internal global prefixes are filtered before source generation.",
    "",
  ].join("\n");
}

function listAutoJs6DtsFiles(dtsRoot) {
  return fs.readdirSync(dtsRoot)
    .filter((fileName) => fileName === "aj6-declarations.d.ts" || /^aj6-int-.+\.d\.ts$/.test(fileName))
    .sort((a, b) => a.localeCompare(b));
}

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

function buildDeclarationModules(internalTypes, moduleBindings) {
  const modules = [];
  for (const [moduleName, typeName] of moduleBindings.entries()) {
    const members = internalTypes.get(typeName) || [];
    if (members.length === 0 || !isPublicCompletionName(moduleName)) {
      continue;
    }
    modules.push({
      name: moduleName,
      typeName,
      doc: moduleDoc(moduleName, typeName),
      members,
    });
  }
  return modules.sort((a, b) => a.name.localeCompare(b.name));
}

function mergeSourceModules(baseModules, declarationModules) {
  const modules = new Map();

  for (const [moduleName, moduleSpec] of Object.entries(baseModules || {})) {
    modules.set(moduleName, {
      doc: moduleSpec.doc || moduleDoc(moduleName, ""),
      score: moduleSpec.score,
      members: mergeMembers([], moduleSpec.members || []),
    });
  }

  for (const moduleSpec of declarationModules || []) {
    const existing = modules.get(moduleSpec.name);
    modules.set(moduleSpec.name, {
      doc: existing && existing.doc || moduleSpec.doc || moduleDoc(moduleSpec.name, moduleSpec.typeName),
      score: existing && existing.score,
      members: mergeMembers(moduleSpec.members, existing && existing.members || []),
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

function dedupeNames(names) {
  const seen = new Set();
  const deduped = [];
  for (const name of names) {
    if (seen.has(name)) {
      continue;
    }
    seen.add(name);
    deduped.push(name);
  }
  return deduped;
}

function validateModelModule(moduleSpec, errors, seenModules) {
  if (!moduleSpec || typeof moduleSpec !== "object" || Array.isArray(moduleSpec)) {
    errors.push(`api model module must be an object: ${JSON.stringify(moduleSpec)}`);
    return;
  }
  if (!isPublicCompletionName(moduleSpec.name)) {
    errors.push(`api model module has invalid name: ${moduleSpec.name}`);
  }
  if (seenModules.has(moduleSpec.name)) {
    errors.push(`api model has duplicate module: ${moduleSpec.name}`);
  }
  seenModules.add(moduleSpec.name);
  if (typeof moduleSpec.typeName !== "string" || !IDENTIFIER_PATTERN.test(moduleSpec.typeName)) {
    errors.push(`api model module ${moduleSpec.name} has invalid typeName: ${moduleSpec.typeName}`);
  }
  if (typeof moduleSpec.doc !== "string") {
    errors.push(`api model module ${moduleSpec.name} doc must be a string`);
  }
  if (!Array.isArray(moduleSpec.members) || moduleSpec.members.length === 0) {
    errors.push(`api model module ${moduleSpec.name} must have members`);
    return;
  }
  const seenMembers = new Set();
  for (const rawMember of moduleSpec.members) {
    const member = normalizeMember(rawMember);
    if (!isPublicCompletionName(member.name)) {
      errors.push(`api model module ${moduleSpec.name} has invalid member: ${JSON.stringify(rawMember)}`);
    }
    if (!MEMBER_KINDS.has(member.kind || "function")) {
      errors.push(`api model module ${moduleSpec.name}.${member.name} has invalid kind: ${member.kind}`);
    }
    if (seenMembers.has(member.name)) {
      errors.push(`api model module ${moduleSpec.name} has duplicate member: ${member.name}`);
    }
    seenMembers.add(member.name);
  }
}

function validateModelGlobal(entry, field, errors) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    errors.push(`api model globals.${field} entry must be an object: ${JSON.stringify(entry)}`);
    return;
  }
  if (!isPublicCompletionName(entry.name)) {
    errors.push(`api model globals.${field} has invalid name: ${entry.name}`);
  }
  const expectedKind = field === "functions" ? "function" : "variable";
  if (entry.kind !== expectedKind) {
    errors.push(`api model globals.${field}.${entry.name} kind must be ${expectedKind}`);
  }
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
