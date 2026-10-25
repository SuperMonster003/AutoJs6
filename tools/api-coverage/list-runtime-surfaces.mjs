#!/usr/bin/env node
// List the members the Node runtime declarations and the Bun host client declare, for runtime_matrix.py.
// Declarations are read with the TypeScript checker; Bun's capability method table comes from the real client module.
import fs from "node:fs";
import path from "node:path";
import {createRequire} from "node:module";
import {pathToFileURL} from "node:url";

const options = {};
for (let i = 2; i < process.argv.length; i++) {
    const name = process.argv[i];
    if (!name.startsWith("--") || !process.argv[i + 1]) throw new Error(`Invalid argument: ${name}`);
    options[name.slice(2)] = path.resolve(process.argv[++i]);
}
for (const key of ["node", "bun", "typescript", "output"]) {
    if (!options[key]) throw new Error(`Required argument: --${key}`);
}
const ts = createRequire(import.meta.url)(options.typescript);
const compilerOptions = {noEmit: true, skipLibCheck: true, strict: true, target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.NodeNext, moduleResolution: ts.ModuleResolutionKind.NodeNext, lib: ["lib.es2022.d.ts"], types: []};

function program(root) {
    const result = ts.createProgram([root], compilerOptions);
    return {program: result, checker: result.getTypeChecker(), source: result.getSourceFile(root)};
}

// A member is async when every call signature returns a thenable (Promise, Flow chains).
function describe(checker, type) {
    const signatures = checker.getSignaturesOfType(checker.getNonNullableType(type), ts.SignatureKind.Call);
    const constructs = checker.getSignaturesOfType(checker.getNonNullableType(type), ts.SignatureKind.Construct);
    const thenable = signature => {
        const returned = checker.getReturnTypeOfSignature(signature);
        return checker.getPropertyOfType(returned, "then") !== undefined;
    };
    return {callable: signatures.length > 0, constructable: constructs.length > 0, signatures: signatures.length,
        async: signatures.length > 0 && signatures.every(thenable)};
}

// One nested level (cvt.bytes.toBytes, flow.wait.async) is listed as dotted names.
function members(checker, type, location, prefix = "") {
    const result = {};
    for (const symbol of checker.getPropertiesOfType(checker.getNonNullableType(type))) {
        if (symbol.name.startsWith("__@")) continue;
        const memberType = checker.getTypeOfSymbolAtLocation(symbol, location);
        result[prefix + symbol.name] = describe(checker, memberType);
        // Promises, arrays and primitives are values, not namespaces.
        const value = checker.isArrayType(memberType) || memberType.flags & ts.TypeFlags.Primitive
            || checker.getPropertyOfType(checker.getNonNullableType(memberType), "then");
        if (!prefix && !value) {
            Object.assign(result, members(checker, memberType, location, symbol.name + "."));
        }
    }
    if (prefix) return result;
    const self = describe(checker, type);
    if (self.callable || self.constructable) result["()"] = self;
    return result;
}

function nodeModules() {
    const root = path.join(options.node, "docs/nodejs/types/autojs6-node/index.d.ts");
    const {checker, source} = program(root);
    const modules = {};
    for (const symbol of checker.getAmbientModules()) {
        const name = symbol.name.replace(/^"|"$/g, "");
        const declaration = symbol.declarations?.[0];
        if (!declaration) continue;
        const target = checker.resolveExternalModuleSymbol(symbol);
        let entries;
        if (target !== symbol) {
            entries = members(checker, checker.getTypeOfSymbolAtLocation(target, declaration), declaration);
        } else {
            entries = {};
            for (const exported of checker.getExportsOfModule(symbol)) {
                if (!(exported.flags & ts.SymbolFlags.Value)) continue;
                const resolved = exported.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(exported) : exported;
                entries[exported.name] = describe(checker, checker.getTypeOfSymbolAtLocation(resolved, declaration));
            }
        }
        modules[name] = {file: path.relative(options.node, declaration.getSourceFile().fileName).replaceAll("\\", "/"), members: entries};
    }
    // $autojs.<module> explicit entries: properties typed as typeof import("<module>").
    const explicit = {};
    const global = checker.getSymbolsInScope(source, ts.SymbolFlags.Value).find(item => item.name === "$autojs");
    if (global) {
        const type = checker.getTypeOfSymbolAtLocation(global, source);
        for (const property of checker.getPropertiesOfType(type)) {
            const text = property.valueDeclaration?.type?.getText() || "";
            const match = text.match(/^typeof import\("([^"]+)"\)$/);
            if (match) explicit[property.name] = match[1];
        }
    }
    return {modules, explicitGlobal: explicit};
}

async function bunNamespaces() {
    const declarations = path.join(options.bun, "app/src/main/assets/autojs6/host-client.d.mts");
    const {checker, source} = program(declarations);
    const moduleSymbol = checker.getSymbolAtLocation(source);
    const client = checker.getExportsOfModule(moduleSymbol).find(item => item.name === "HostClient");
    if (!client) throw new Error("host-client.d.mts no longer exports HostClient");
    const namespaces = {}, clientMembers = {};
    for (const property of checker.getPropertiesOfType(checker.getDeclaredTypeOfSymbol(client))) {
        const type = checker.getTypeOfSymbolAtLocation(property, source);
        const nested = checker.getPropertiesOfType(checker.getNonNullableType(type));
        // Client methods (toast, call), plain values and arrays (sdkVersion, capabilities) are not namespaces.
        if (!nested.length || type.flags & ts.TypeFlags.Primitive || checker.isArrayType(type)) clientMembers[property.name] = describe(checker, type);
        else namespaces[property.name] = {members: members(checker, type, source)};
    }
    const capabilityType = checker.getExportsOfModule(moduleSymbol).find(item => item.name === "HostCapability");
    const capabilities = capabilityType ? checker.getDeclaredTypeOfSymbol(capabilityType).types?.map(type => type.value) || [] : [];
    const client_ = await import(pathToFileURL(path.join(options.bun, "app/src/main/assets/autojs6/host-client.mjs")).href);
    return {file: "app/src/main/assets/autojs6/host-client.d.mts", namespaces, clientMembers, capabilities: capabilities.sort(),
        methods: Object.fromEntries(Object.entries(client_.HOST_API_METHODS).map(([key, value]) => [key, [...value].sort()]))};
}

const report = {typescript: ts.version, node: nodeModules(), bun: await bunNamespaces()};
fs.mkdirSync(path.dirname(options.output), {recursive: true});
fs.writeFileSync(options.output, JSON.stringify(report, null, 2) + "\n");
console.log(JSON.stringify({nodeModules: Object.keys(report.node.modules).length, explicitGlobal: Object.keys(report.node.explicitGlobal).length,
    bunNamespaces: Object.keys(report.bun.namespaces).length}));
