#!/usr/bin/env node
// node tools/api-coverage/test-dts-signatures.mjs [--typescript <typescript.js>]
// Checks the argument-count ranges dts-signatures.mjs reads from d.ts call and construct signatures.
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {createRequire} from "node:module";
import {fileURLToPath} from "node:url";
import {signatureArities} from "./dts-signatures.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const index = process.argv.indexOf("--typescript");
let compiler = index > 0 ? path.resolve(process.argv[index + 1]) : null;
if (!compiler) {
    const ace = path.resolve(here, "../../../AutoJs6-Plugin-Ace-Editor");
    const lock = JSON.parse(fs.readFileSync(path.join(ace, "tools/ace-lsp/ace-builds-lock.json"), "utf8"));
    compiler = path.join(ace, lock.assetRoot, "autojs6/typescript/typescript.js");
}
const ts = createRequire(import.meta.url)(compiler);

const source = `
declare namespace sample {
    function plain(a: string, b?: number): void;
    function overloaded(a: string): void;
    function overloaded(a: string, b: number, c: number): void;
    function rest(a: string, ...more: number[]): void;
    function tupleRest(...args: [string, number?]): void;
    function withThis(this: object, a: string): void;
    let maybe: ((a: string) => void) | undefined;
    const value: number;
}
declare const Made: { (x?: number): number; new (a: string, b: string): object };
`;
const file = "__dts_signatures__.d.ts";
const options = {noEmit: true, strict: true, target: ts.ScriptTarget.ES2020, lib: ["lib.es2020.d.ts"], types: []};
const host = ts.createCompilerHost(options);
const readFile = host.readFile.bind(host), exists = host.fileExists.bind(host), getSourceFile = host.getSourceFile.bind(host);
host.fileExists = name => path.basename(name) === file || exists(name);
host.readFile = name => path.basename(name) === file ? source : readFile(name);
host.getSourceFile = (name, version) => path.basename(name) === file ? ts.createSourceFile(name, source, version) : getSourceFile(name, version);
const program = ts.createProgram([file], options, host);
const checker = program.getTypeChecker();
const scope = checker.getSymbolsInScope(program.getSourceFile(file), ts.SymbolFlags.Value);
const lookup = api => {
    const [head, ...rest] = api.split(".");
    let type = checker.getTypeOfSymbol(scope.find(symbol => symbol.name === head));
    for (const member of rest) type = checker.getTypeOfSymbol(checker.getPropertyOfType(checker.getNonNullableType(type), member));
    return signatureArities(ts, checker, type).signatures;
};

assert.deepEqual(lookup("sample.plain").call, [{min: 1, max: 2}]);
assert.deepEqual(lookup("sample.overloaded").call, [{min: 1, max: 1}, {min: 3, max: 3}]);
assert.deepEqual(lookup("sample.rest").call, [{min: 1, max: null}]);
assert.deepEqual(lookup("sample.tupleRest").call, [{min: 1, max: 2}]);
assert.deepEqual(lookup("sample.withThis").call, [{min: 1, max: 1}]);
assert.deepEqual(lookup("sample.maybe").call, [{min: 1, max: 1}]);
assert.equal(lookup("sample.value"), undefined);
assert.deepEqual(lookup("Made"), {call: [{min: 0, max: 1}], construct: [{min: 2, max: 2}]});
console.log(`dts-signatures: 8 cases passed (TypeScript ${ts.version})`);
