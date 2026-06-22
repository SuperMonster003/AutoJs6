#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const SCAN_SCHEMA = "autojs6-node-rhino-augment-source-scan-v1";
const AUGMENT_ROOT = "app/src/main/java/org/autojs/autojs/runtime/api/augment";
const SCRIPT_RUNTIME = "app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt";
const AUGMENT_PACKAGE_PREFIX = "org.autojs.autojs.runtime.api.augment.";

const REGISTRATION_METHODS = new Set([
  "augment",
  "augmentWithRuntime",
  "assignWithRuntime",
  "proxying",
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const outputFile = options.output ? path.resolve(options.output) : null;
  const report = scan(repoRoot);
  const text = JSON.stringify(report, null, 2) + "\n";
  if (outputFile) {
    fs.mkdirSync(path.dirname(outputFile), { recursive: true });
    fs.writeFileSync(outputFile, text);
  } else {
    process.stdout.write(text);
  }
  if (options.failOnUnresolved && report.totals.unresolvedReceiverCount > 0) {
    process.exitCode = 1;
  }
}

function scan(repoRoot) {
  const augmentRoot = path.join(repoRoot, AUGMENT_ROOT);
  const scriptRuntimePath = path.join(repoRoot, SCRIPT_RUNTIME);
  const sourceIndex = indexAugmentSources(repoRoot, augmentRoot);
  const scriptRuntimeText = read(scriptRuntimePath);
  const imports = parseImports(scriptRuntimeText);
  const augmentBlock = extractAugmentBlock(scriptRuntimeText);
  const registrations = extractRegistrations(repoRoot, augmentBlock, imports, sourceIndex);
  const protoReferences = collectProtoReferences(registrations, imports, sourceIndex);
  const unresolvedReceivers = registrations.filter((entry) => !entry.className);

  return {
    schema: SCAN_SCHEMA,
    generatedAt: new Date().toISOString(),
    source: {
      augmentRoot: relativePath(repoRoot, augmentRoot),
      scriptRuntime: relativePath(repoRoot, scriptRuntimePath),
      scanner: relativePath(repoRoot, __filename),
    },
    modules: registrations,
    protoReferences,
    totals: {
      registeredModuleCount: registrations.length,
      protoReferenceCount: protoReferences.length,
      unresolvedReceiverCount: unresolvedReceivers.length,
      unresolvedReceivers: unresolvedReceivers.map((entry) => entry.receiver),
    },
  };
}

function indexAugmentSources(repoRoot, augmentRoot) {
  const byFqcn = new Map();
  const bySimpleName = new Map();
  walkKtFiles(augmentRoot).forEach((file) => {
    const text = read(file);
    const packageName = matchOne(text, /^package\s+([A-Za-z0-9_.]+)/m) || "";
    const lines = text.split(/\r?\n/);
    const declarationPattern = /\b(class|object)\s+([A-Za-z_][A-Za-z0-9_]*)/g;
    let match;
    while ((match = declarationPattern.exec(text)) !== null) {
      const kind = match[1];
      const simpleName = match[2];
      const line = lineNumberAt(text, match.index);
      const header = declarationHeader(text, match.index);
      const fqcn = packageName ? `${packageName}.${simpleName}` : simpleName;
      const metadata = {
        kind,
        simpleName,
        className: fqcn,
        packageName,
        source: {
          file: relativePath(repoRoot, file),
          line,
        },
        baseClass: header.includes("AugmentableProxy") ? "AugmentableProxy" : header.includes("Augmentable") ? "Augmentable" : "unknown",
        traits: detectTraits(text, header),
        construction: "unknown",
        requiresScriptRuntime: /scriptRuntime\s*:\s*ScriptRuntime/.test(header) || /scriptRuntime\s*:\s*ScriptRuntime/.test(text),
        originatesKeyName: text.includes("originateKeyName()"),
        simpleGetterProxyInterface: text.includes("@AugmentableSimpleGetterProxyInterface"),
        assignmentSummary: extractAssignmentSummary(text),
      };
      metadata.construction = constructionFor(metadata);
      byFqcn.set(fqcn, metadata);
      if (!bySimpleName.has(simpleName)) {
        bySimpleName.set(simpleName, []);
      }
      bySimpleName.get(simpleName).push(metadata);
    }
  });
  return { byFqcn, bySimpleName };
}

function extractRegistrations(repoRoot, augmentBlock, imports, sourceIndex) {
  const registrations = [];
  const constructorPattern = /\b([A-Za-z_][A-Za-z0-9_]*)\s*\([^;\n]*?\)\s*\.\s*(augment|assignWithRuntime|proxying)\s*\(([^;\n]*)/g;
  const objectPattern = /\b([A-Za-z_][A-Za-z0-9_]*)\s*\.\s*(augmentWithRuntime|assignWithRuntime|augment)\s*\(([^;\n]*)/g;

  augmentBlock.lines.forEach((line, offset) => {
    const lineNumber = augmentBlock.startLine + offset;
    const normalized = stripLineComment(line);
    for (const entry of matchRegistrations(normalized, constructorPattern, lineNumber, "constructor")) {
      registrations.push(enrichRegistration(repoRoot, entry, imports, sourceIndex, registrations.length));
    }
    for (const entry of matchRegistrations(normalized, objectPattern, lineNumber, "object")) {
      if (entry.receiver === "augmentedApp") {
        continue;
      }
      registrations.push(enrichRegistration(repoRoot, entry, imports, sourceIndex, registrations.length));
    }
  });

  return registrations;
}

function matchRegistrations(line, pattern, lineNumber, receiverKind) {
  const entries = [];
  pattern.lastIndex = 0;
  let match;
  while ((match = pattern.exec(line)) !== null) {
    const receiver = match[1];
    const method = match[2];
    if (!REGISTRATION_METHODS.has(method)) {
      continue;
    }
    const args = parseTopLevelArgs(match[3]);
    entries.push({
      receiver,
      receiverKind,
      method,
      args,
      raw: line.trim(),
      line: lineNumber,
    });
  }
  return entries;
}

function enrichRegistration(repoRoot, entry, imports, sourceIndex, order) {
  const fqcn = imports.get(entry.receiver) || null;
  const metadata = resolveMetadata(entry.receiver, fqcn, sourceIndex);
  const simpleName = metadata ? metadata.simpleName : simpleNameFromFqcn(fqcn) || entry.receiver;
  const protoExpressions = protoArgsFor(entry.method, entry.args);
  const withDollarPrefix = withDollarPrefixFor(entry.method, entry.args);
  const targetExpression = entry.args[0] || null;
  const additionalAttributes = entry.args.filter((arg) => arg === "READONLY" || arg === "DONTENUM");
  const registrationTarget = targetFor(entry.method, targetExpression);
  const className = metadata ? metadata.className : fqcn;

  return {
    order,
    id: moduleIdFor(className, entry.receiver, simpleName),
    receiver: entry.receiver,
    receiverKind: entry.receiverKind,
    key: keyFor(simpleName, metadata),
    className: className || null,
    packageName: metadata ? metadata.packageName : packageNameFromFqcn(fqcn),
    source: metadata ? metadata.source : null,
    registration: {
      method: entry.method,
      site: {
        file: SCRIPT_RUNTIME,
        line: entry.line,
      },
      target: registrationTarget,
      targetExpression,
      withDollarPrefix,
      additionalAttributes,
      protoExpressions,
      raw: entry.raw,
    },
    augmentable: metadata ? {
      baseClass: metadata.baseClass,
      traits: metadata.traits,
      construction: metadata.construction,
      requiresScriptRuntime: metadata.requiresScriptRuntime,
      originatesKeyName: metadata.originatesKeyName,
      simpleGetterProxyInterface: metadata.simpleGetterProxyInterface,
    } : null,
    assignmentSummary: metadata ? metadata.assignmentSummary : null,
  };
}

function collectProtoReferences(registrations, imports, sourceIndex) {
  const seen = new Map();
  registrations.forEach((registration) => {
    registration.registration.protoExpressions.forEach((expression) => {
      const symbol = expressionSymbol(expression);
      if (!symbol || symbol === registration.receiver) {
        return;
      }
      const fqcn = imports.get(symbol) || null;
      const metadata = resolveMetadata(symbol, fqcn, sourceIndex);
      const id = moduleIdFor(metadata ? metadata.className : fqcn, symbol, metadata ? metadata.simpleName : symbol);
      const key = `${registration.id}:${expression}`;
      if (seen.has(key)) {
        return;
      }
      seen.set(key, {
        id,
        expression,
        symbol,
        ownerModuleId: registration.id,
        ownerReceiver: registration.receiver,
        className: metadata ? metadata.className : fqcn,
        source: metadata ? metadata.source : null,
        traits: metadata ? metadata.traits : [],
        simpleGetterProxyInterface: metadata ? metadata.simpleGetterProxyInterface : false,
      });
    });
  });
  return Array.from(seen.values());
}

function parseImports(text) {
  const imports = new Map();
  const pattern = /^import\s+([A-Za-z0-9_.]+)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?/gm;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    const fqcn = match[1];
    if (!fqcn.startsWith(AUGMENT_PACKAGE_PREFIX)) {
      continue;
    }
    const alias = match[2] || fqcn.split(".").pop();
    imports.set(alias, fqcn);
  }
  return imports;
}

function extractAugmentBlock(text) {
  const signature = "private fun augment(target: ScriptableObject)";
  const start = text.indexOf(signature);
  if (start < 0) {
    throw new Error("Unable to find ScriptRuntime.augment block");
  }
  const open = text.indexOf("{", start);
  if (open < 0) {
    throw new Error("Unable to find ScriptRuntime.augment opening brace");
  }
  let depth = 0;
  let end = -1;
  for (let index = open; index < text.length; index += 1) {
    const ch = text[index];
    if (ch === "{") {
      depth += 1;
    } else if (ch === "}") {
      depth -= 1;
      if (depth === 0) {
        end = index;
        break;
      }
    }
  }
  if (end < 0) {
    throw new Error("Unable to find ScriptRuntime.augment closing brace");
  }
  const before = text.slice(0, open + 1);
  const startLine = before.split(/\r?\n/).length;
  return {
    startLine,
    lines: text.slice(open + 1, end).split(/\r?\n/),
  };
}

function extractAssignmentSummary(text) {
  const properties = [
    "selfAssignmentProperties",
    "globalAssignmentProperties",
    "selfAssignmentFunctions",
    "globalAssignmentFunctions",
    "selfAssignmentJavaClasses",
    "globalAssignmentJavaClasses",
    "selfAssignmentGetters",
    "selfAssignmentGettersAndSetters",
    "globalAssignmentGetters",
  ];
  const summary = {};
  properties.forEach((property) => {
    const block = extractListBlock(text, property);
    if (!block) {
      summary[property] = {
        present: false,
        names: [],
      };
      return;
    }
    summary[property] = {
      present: true,
      names: extractAssignmentNames(block),
    };
  });
  return summary;
}

function extractListBlock(text, propertyName) {
  const marker = `${propertyName} = listOf(`;
  const start = text.indexOf(marker);
  if (start < 0) {
    return null;
  }
  const open = text.indexOf("(", start);
  let depth = 0;
  for (let index = open; index < text.length; index += 1) {
    const ch = text[index];
    if (ch === "(") {
      depth += 1;
    } else if (ch === ")") {
      depth -= 1;
      if (depth === 0) {
        return text.slice(open + 1, index);
      }
    }
  }
  return null;
}

function extractAssignmentNames(block) {
  const names = new Set();
  let match;
  const functionNamePattern = /::([A-Za-z_][A-Za-z0-9_]*)\.name/g;
  while ((match = functionNamePattern.exec(block)) !== null) {
    names.add(match[1]);
  }
  const stringPattern = /"([A-Za-z_$][A-Za-z0-9_$]*)"/g;
  while ((match = stringPattern.exec(block)) !== null) {
    names.add(match[1]);
  }
  return Array.from(names).sort();
}

function detectTraits(text, header) {
  const traits = [];
  [
    "Invokable",
    "Constructable",
    "Versatile",
    "AsEmitter",
    "JsBuildInObjectExtensible",
    "SimpleGetterProxy",
  ].forEach((trait) => {
    if (header.includes(trait) || text.includes(`: ${trait}`) || text.includes(`, ${trait}`) || text.includes(` ${trait}`)) {
      traits.push(trait);
    }
  });
  if (text.includes("@AugmentableSimpleGetterProxyInterface") && !traits.includes("SimpleGetterProxy")) {
    traits.push("SimpleGetterProxy");
  }
  return traits;
}

function constructionFor(metadata) {
  if (metadata.baseClass === "AugmentableProxy") {
    return "proxy_object";
  }
  if (metadata.traits.includes("Versatile")) {
    return "base_function_versatile";
  }
  if (metadata.traits.includes("Invokable")) {
    return "base_function_invokable";
  }
  if (metadata.traits.includes("Constructable")) {
    return "base_function_constructable";
  }
  if (metadata.traits.includes("JsBuildInObjectExtensible")) {
    return "prototype_extension";
  }
  if (metadata.baseClass === "Augmentable") {
    return "plain_object";
  }
  return "unknown";
}

function resolveMetadata(receiver, fqcn, sourceIndex) {
  if (fqcn && sourceIndex.byFqcn.has(fqcn)) {
    return sourceIndex.byFqcn.get(fqcn);
  }
  const simpleName = simpleNameFromFqcn(fqcn) || receiver;
  const matches = sourceIndex.bySimpleName.get(simpleName) || [];
  return matches.length === 1 ? matches[0] : null;
}

function protoArgsFor(method, args) {
  if (method === "augment") {
    return args.length > 1 && !isBooleanLiteral(args[1]) ? [args[1]] : [];
  }
  if (method === "augmentWithRuntime") {
    return args.length > 2 && !isBooleanLiteral(args[2]) ? [args[2]] : [];
  }
  if (method === "assignWithRuntime") {
    return args.length > 2 ? [args[2]] : [];
  }
  if (method === "proxying") {
    return args.length > 1 ? [args[1]] : [];
  }
  return [];
}

function withDollarPrefixFor(method, args) {
  if (method === "assignWithRuntime") {
    return null;
  }
  for (let index = args.length - 1; index >= 0; index -= 1) {
    if (isBooleanLiteral(args[index])) {
      return args[index] === "true";
    }
  }
  return null;
}

function targetFor(method, targetExpression) {
  if (method === "proxying") {
    return "proxy_object";
  }
  if (targetExpression === "target") {
    return "top_level_scope";
  }
  if (!targetExpression) {
    return "unknown";
  }
  return "module_object";
}

function parseTopLevelArgs(rawArgs) {
  const args = [];
  let current = "";
  let depth = 0;
  let quote = null;
  for (let index = 0; index < rawArgs.length; index += 1) {
    const ch = rawArgs[index];
    const prev = rawArgs[index - 1];
    if (quote) {
      current += ch;
      if (ch === quote && prev !== "\\") {
        quote = null;
      }
      continue;
    }
    if (ch === "\"" || ch === "'") {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === "(" || ch === "[" || ch === "{") {
      depth += 1;
      current += ch;
      continue;
    }
    if (ch === ")" || ch === "]" || ch === "}") {
      if (depth === 0) {
        break;
      }
      depth -= 1;
      current += ch;
      continue;
    }
    if (ch === "," && depth === 0) {
      args.push(current.trim());
      current = "";
      continue;
    }
    current += ch;
  }
  if (current.trim()) {
    args.push(current.trim());
  }
  return args;
}

function declarationHeader(text, index) {
  const brace = text.indexOf("{", index);
  const newline = text.indexOf("\n", index);
  const end = brace >= 0 && (newline < 0 || brace < newline + 200) ? brace : newline;
  return text.slice(index, end >= 0 ? end : index + 240);
}

function moduleIdFor(fqcn, receiver, simpleName) {
  if (!fqcn || !fqcn.startsWith(AUGMENT_PACKAGE_PREFIX)) {
    return toSnakeCase(receiver);
  }
  const relative = fqcn.slice(AUGMENT_PACKAGE_PREFIX.length);
  const parts = relative.split(".");
  const classPart = toSnakeCase(simpleName || parts[parts.length - 1]);
  const packagePart = parts.slice(0, -1).map(toSnakeCase).join(".");
  if (!packagePart || packagePart === classPart) {
    return classPart;
  }
  if (compactId(packagePart) === compactId(classPart)) {
    return packagePart;
  }
  return `${packagePart}.${classPart}`;
}

function compactId(value) {
  return String(value).replace(/[^a-z0-9]/g, "");
}

function keyFor(simpleName, metadata) {
  if (!simpleName) {
    return null;
  }
  if (metadata && metadata.originatesKeyName) {
    return simpleName;
  }
  return simpleName[0].toLowerCase() + simpleName.slice(1);
}

function expressionSymbol(expression) {
  const match = String(expression).trim().match(/^([A-Za-z_][A-Za-z0-9_]*)/);
  return match ? match[1] : null;
}

function simpleNameFromFqcn(fqcn) {
  return fqcn ? fqcn.split(".").pop() : null;
}

function packageNameFromFqcn(fqcn) {
  return fqcn ? fqcn.split(".").slice(0, -1).join(".") : null;
}

function isBooleanLiteral(value) {
  return value === "true" || value === "false";
}

function toSnakeCase(value) {
  return String(value)
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1_$2")
    .replace(/[^A-Za-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

function stripLineComment(line) {
  const index = line.indexOf("//");
  return index >= 0 ? line.slice(0, index) : line;
}

function walkKtFiles(root) {
  const files = [];
  walk(root, (file) => {
    if (file.endsWith(".kt")) {
      files.push(file);
    }
  });
  return files.sort();
}

function walk(root, visitor) {
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const file = path.join(root, entry.name);
    if (entry.isDirectory()) {
      walk(file, visitor);
    } else if (entry.isFile()) {
      visitor(file);
    }
  }
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function matchOne(text, pattern) {
  const match = text.match(pattern);
  return match ? match[1] : null;
}

function lineNumberAt(text, index) {
  return text.slice(0, index).split(/\r?\n/).length;
}

function relativePath(root, file) {
  return path.relative(root, file).replace(/\\/g, "/");
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-unresolved"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node scan-rhino-augment-inventory.js [--repo-root dir] [--output file] [--fail-on-unresolved]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
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

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

main();
