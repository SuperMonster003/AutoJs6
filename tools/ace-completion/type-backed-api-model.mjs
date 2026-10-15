import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { TYPESCRIPT_RUNTIME, isProOnlyCompletionName } from "./indices-utils.mjs";

// Resolve ambient namespace merges, callable modules, inherited members and nested objects
// with the same compiler shipped in the editor. The older textual model remains the source
// of established display signatures; this supplements paths it cannot resolve.
export function completeTypeBackedModules(dtsRoot, modules) {
  const ts = createRequire(import.meta.url)(TYPESCRIPT_RUNTIME);
  const roots = fs.readdirSync(dtsRoot)
    .filter(name => /^aj6-(?:int-|declarations|main-app)/.test(name) && name.endsWith(".d.ts"))
    .map(name => path.join(dtsRoot, name));
  const program = ts.createProgram(roots, {
    skipLibCheck: true, noEmit: true, target: ts.ScriptTarget.ES2022,
    ignoreDeprecations: "6.0",
  });
  const rootNames = new Set(roots.map(name => name.replaceAll("\\", "/")));
  const checker = program.getTypeChecker();
  const known = new Map(modules.filter(module => !module.name.startsWith("$")).map(module => [module.name, module]));
  const aliases = {};
  const functions = new Set(), variables = new Set();
  const registeredHelpers = new Set(["__buildRegion", "__assignFunctions__"]);
  const inheritedScriptMembers = new Set(["files.formatSizeWithUnit", "ocr.toString"]);
  const publicName = name => /^[A-Za-z_$][\w$]*$/.test(name) &&
    (!name.startsWith("__") || registeredHelpers.has(name)) && !/.+\$app$/.test(name) && !isProOnlyCompletionName(name);
  const scripted = symbol => (symbol.declarations || []).some(declaration =>
    /[/\\]aj6-int-[^/\\]+\.d\.ts$/.test(declaration.getSourceFile().fileName));
  const format = ts.TypeFormatFlags.NoTruncation | ts.TypeFormatFlags.UseAliasDefinedOutsideCurrentScope;

  function visit(type, moduleName, location, depth, ancestors) {
    if (depth > 3 || ancestors.has(type) || type.flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown)) return;
    const nextAncestors = new Set(ancestors).add(type);
    const candidates = checker.getPropertiesOfType(type);
    for (const qualified of inheritedScriptMembers) {
      if (!qualified.startsWith(moduleName + ".")) continue;
      const name = qualified.slice(moduleName.length + 1);
      const symbol = checker.getPropertyOfType(type, name);
      if (symbol && !candidates.some(candidate => candidate.name === name)) candidates.push(symbol);
    }
    const properties = candidates.filter(symbol => {
      const inherited = inheritedScriptMembers.has(`${moduleName}.${symbol.name}`);
      if (!publicName(symbol.name) || (!scripted(symbol) && !inherited)) return false;
      return (symbol.declarations || []).some(declaration =>
        (inherited || !/[/\\]lib\.[^/\\]+\.d\.ts$/.test(declaration.getSourceFile().fileName)) &&
        !declaration.modifiers?.some(m => m.kind === ts.SyntaxKind.PrivateKeyword || m.kind === ts.SyntaxKind.ProtectedKeyword));
    });
    if (!properties.length) return;
    let module = known.get(moduleName);
    if (!module) {
      module = { name: moduleName, typeName: moduleName.replace(/\./g, "_"),
        doc: `AutoJs6 ${moduleName} API`, members: [] };
      known.set(moduleName, module);
    }
    const names = new Set(module.members.map(member => member.name));
    for (const symbol of properties) {
      const declaration = symbol.valueDeclaration || symbol.declarations?.[0] || location;
      const propertyType = checker.getTypeOfSymbolAtLocation(symbol, declaration);
      const signatures = checker.getSignaturesOfType(propertyType, ts.SignatureKind.Call);
      if (!names.has(symbol.name)) {
        const signatureList = signatures.map(signature =>
          `${symbol.name}${checker.signatureToString(signature, declaration, format)};`);
        const signature = signatureList[0] || `${symbol.name}: ${checker.typeToString(propertyType, declaration, format)};`;
        module.members.push({ name: symbol.name, kind: signatures.length ? "function" : "variable",
          doc: signature, signature,
          ...(signatureList.length > 1 ? { signatures: signatureList } : {}) });
      }
      // Do not recursively expose implementation objects, Java packages or builtin prototypes.
      if (scripted(symbol) && symbol.name !== "prototype") {
        visit(propertyType, `${moduleName}.${symbol.name}`, declaration, depth + 1, nextAncestors);
      }
    }
  }

  for (const file of program.getSourceFiles()) {
    if (!rootNames.has(file.fileName.replaceAll("\\", "/")) || !/aj6-(int-|declarations)/.test(path.basename(file.fileName))) continue;
    for (const statement of file.statements) {
      const declarations = ts.isVariableStatement(statement) ? statement.declarationList.declarations :
        ts.isFunctionDeclaration(statement) || ts.isClassDeclaration(statement) ? [statement] : [];
      for (const declaration of declarations) {
        const name = declaration.name?.getText(file);
        if (!name || !publicName(name) || name === "Internal") continue;
        if (name.startsWith("$")) { aliases[name] = name.slice(1); continue; }
        const symbol = checker.getSymbolAtLocation(declaration.name);
        if (!symbol) continue;
        const type = checker.getTypeOfSymbolAtLocation(symbol, declaration);
        if (checker.getSignaturesOfType(type, ts.SignatureKind.Call).length) functions.add(name);
        else variables.add(name);
        visit(type, name, declaration, 0, new Set());
      }
    }
  }
  const conditionalModules = {};
  const extensionRoots = ["array", "number", "math"].map(name =>
    path.join(dtsRoot, `aj6-jsox-${name}-extensions.d.ts`)).filter(name => fs.existsSync(name));
  if (extensionRoots.length) {
    const extensionProgram = ts.createProgram(roots.concat(extensionRoots), {
      skipLibCheck: true, noEmit: true, target: ts.ScriptTarget.ES2022, ignoreDeprecations: "6.0",
    });
    const extensionChecker = extensionProgram.getTypeChecker();
    const contexts = { ArrayConstructor: "Array", Array: "Array.prototype",
      NumberConstructor: "Number", Number: "Number.prototype", Math: "Math" };
    for (const extensionRoot of extensionRoots) {
      const file = extensionProgram.getSourceFile(extensionRoot);
      const group = path.basename(extensionRoot).split("-")[2];
      const groupModules = conditionalModules[group] = {};
      for (const statement of file.statements) {
        if (!ts.isInterfaceDeclaration(statement) || !contexts[statement.name.text]) continue;
        const type = extensionChecker.getTypeAtLocation(statement);
        groupModules[contexts[statement.name.text]] = statement.members.filter(member => member.name).map(member => {
          const name = member.name.getText(file);
          const symbol = type.getProperty(name);
          const memberType = extensionChecker.getTypeOfSymbolAtLocation(symbol, member);
          const calls = extensionChecker.getSignaturesOfType(memberType, ts.SignatureKind.Call);
          const signatures = calls.map(call => `${name}${extensionChecker.signatureToString(call, member, format)};`);
          const signature = signatures[0] || `${name}: ${extensionChecker.typeToString(memberType, member, format)};`;
          return { name, kind: calls.length ? "function" : "variable", doc: signature, signature,
            ...(signatures.length > 1 ? { signatures } : {}) };
        });
      }
    }
  }
  return { modules: [...known.values()].sort((a, b) => a.name.localeCompare(b.name)), aliases, conditionalModules,
    functions: [...functions].filter(name => !known.has(name)),
    variables: [...variables].filter(name => !known.has(name)) };
}
