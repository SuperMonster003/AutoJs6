#!/usr/bin/env node
// Compares the Node runtime declarations (docs/nodejs/types/autojs6-node) of the rows the matrix covers in Node main
// with the Kotlin types of script-modules.json (api-coverage P2.4, Q186 (a)); kotlin_types.py runtime_report calls it
// with the type rows and their Node module / member. Return values are compared awaited.
// Classes of returned-objects.json and Java classes have no Node declaration and stay unmapped. Reported only.
import fs from "node:fs";
import path from "node:path";
import {createRequire} from "node:module";
import {compareTypes, declarationText, typeExpressions} from "./dts-types.mjs";

const options = {};
for (let i = 2; i < process.argv.length; i++) {
    const name = process.argv[i];
    if (!name.startsWith("--") || !process.argv[i + 1]) throw new Error(`Invalid argument: ${name}`);
    options[name.slice(2)] = path.resolve(process.argv[++i]);
}
for (const key of ["node", "typescript", "rows", "output"]) {
    if (!options[key]) throw new Error(`Required argument: --${key}`);
}
const ts = createRequire(import.meta.url)(options.typescript);
const rows = JSON.parse(fs.readFileSync(options.rows, "utf8")).rows
    .map(row => ({...row, query: `node:${row.node.module}|${row.node.member}`}));
const texts = typeExpressions(rows);
const root = path.join(options.node, "docs/nodejs/types/autojs6-node/index.d.ts");
const queryFile = path.join(path.dirname(options.output), "__runtime_types_query__.ts");
const queryText = declarationText(texts);
const compilerOptions = {noEmit: true, skipLibCheck: true, strict: true, target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.NodeNext, moduleResolution: ts.ModuleResolutionKind.NodeNext, lib: ["lib.es2022.d.ts"], types: []};
const host = ts.createCompilerHost(compilerOptions);
const readFile = host.readFile.bind(host), exists = host.fileExists.bind(host);
host.readFile = file => path.resolve(file) === queryFile ? queryText : readFile(file);
host.fileExists = file => path.resolve(file) === queryFile || exists(file);
const program = ts.createProgram([root, queryFile], compilerOptions, host);
const checker = program.getTypeChecker();
const source = program.getSourceFile(queryFile);
const modules = new Map(checker.getAmbientModules().map(symbol => [symbol.name.replace(/^"|"$/g, ""), symbol]));

// The type of `<member>` (dotted, "()" for the module itself) of an ambient module, as list-runtime-surfaces.mjs reads it.
function memberType(moduleName, member) {
    const symbol = modules.get(moduleName);
    const declaration = symbol?.declarations?.[0];
    if (!declaration) return null;
    const target = checker.resolveExternalModuleSymbol(symbol);
    const parts = member === "()" ? [] : member.split(".");
    let type;
    if (target !== symbol) type = checker.getTypeOfSymbolAtLocation(target, declaration);
    else {
        if (!parts.length) return null;
        const exported = checker.getExportsOfModule(symbol).find(item => item.name === parts[0] && item.flags & ts.SymbolFlags.Value);
        if (!exported) return null;
        const resolved = exported.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(exported) : exported;
        type = checker.getTypeOfSymbolAtLocation(resolved, declaration);
        parts.shift();
    }
    for (const part of parts) {
        const property = checker.getPropertyOfType(checker.getNonNullableType(type), part);
        if (!property) return null;
        type = checker.getTypeOfSymbolAtLocation(property, declaration);
    }
    return type;
}

const typeOf = query => {
    const match = /^node:([^|]+)\|(.+)$/.exec(query);
    return match ? memberType(match[1], match[2]) : null;
};
// Host values reach Node through the bridge; a member the matrix lists as synchronous may still be declared as a
// promise (getters such as device.rotation), so every return value and accessor type is compared awaited.
const result = compareTypes({ts, checker, source, texts, typeOf, awaited: () => true}, rows);
for (const [index, row] of result.entries()) row.node = rows[index].node;
fs.mkdirSync(path.dirname(options.output), {recursive: true});
fs.writeFileSync(options.output, JSON.stringify({typescript: ts.version, rows: result}, null, 2) + "\n");
console.log(JSON.stringify({rows: result.length}));
