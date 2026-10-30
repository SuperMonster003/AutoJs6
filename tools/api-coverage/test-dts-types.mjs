#!/usr/bin/env node
// node tools/api-coverage/test-dts-types.mjs [--typescript <typescript.js>]
// Checks the levels dts-types.mjs gives d.ts parameter / return types against Kotlin type descriptors (P2.4).
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {createRequire} from "node:module";
import {fileURLToPath} from "node:url";
import {compareTypes, declarationText, typeExpressions} from "./dts-types.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const index = process.argv.indexOf("--typescript");
let compiler = index > 0 ? path.resolve(process.argv[index + 1]) : null;
if (!compiler) {
    const ace = path.resolve(here, "../../../AutoJs6-Plugin-Ace-Editor");
    const lock = JSON.parse(fs.readFileSync(path.join(ace, "tools/ace-lsp/ace-builds-lock.json"), "utf8"));
    compiler = path.join(ace, lock.assetRoot, "autojs6/typescript/typescript.js");
}
const ts = createRequire(import.meta.url)(compiler);

const declarations = `
declare namespace org.sample { class Point { x: number; } class SubPoint extends Point { y: number; } }
interface Options { size?: number; }
interface Result { ok: boolean; }
declare namespace sample {
    function exact(name: string, count: number): boolean;
    function literal(mode?: 'r' | 'w'): void;
    function nullable(options?: Options | null): Result;
    function noNull(options?: Options): void;
    function coerced(key: string | number): string;
    function wide(value: string | boolean): number;
    function wrong(flag: number): void;
    function nonNull(target: string | null): void;
    function voidButInt(): void;
    function promised(): Promise<string>;
    function missingNull(): string;
    function extraNull(): string | null;
    function point(): org.sample.SubPoint | null;
    function list(): number[];
    function generic<T>(value: T): T;
    function anything(value: any): any;
    function rest(...values: string[]): void;
    const count: 3;
    const size: number;
}
`;
const ts_ = (nominal, extra = {}) => ({ts: {nominal, ...extra}, nullable: false});
const param = (kotlin, type, more = {}) => ({name: "p", kotlin, optional: false, vararg: false, type, ...more});
const fn = (api, params, returns, kotlin = "?") => ({api, module: "sample", kind: "function", query: api, signature: "call",
    overloads: [{params, returns: {kotlin, type: returns}}]});
const STRING = ts_("string", {accepted: "string | number | boolean | object", returns: "string"});
const DOUBLE = ts_("number", {accepted: "number | `${number}`", returns: "number"});
const BOOLEAN = ts_("boolean", {accepted: "boolean", returns: "boolean"});
const OBJECT = {ts: {nominal: "object", accepted: "object", returns: "object", open: true}, nullable: true};
const UNIT = ts_("undefined", {returns: "undefined"});
const rows = [
    fn("sample.exact", [param("kotlin.String", STRING), param("kotlin.Int", DOUBLE)], BOOLEAN),
    fn("sample.literal", [param("kotlin.String?", {...STRING, nullable: true}, {optional: true})], UNIT),
    fn("sample.nullable", [param("NativeObject?", OBJECT, {optional: true})], {...OBJECT, nullable: false}),
    fn("sample.noNull", [param("NativeObject?", OBJECT, {optional: true})], UNIT),
    fn("sample.coerced", [param("kotlin.String", STRING)], STRING),
    fn("sample.wide", [param("kotlin.Double", DOUBLE)], DOUBLE),
    fn("sample.wrong", [param("kotlin.Boolean", BOOLEAN)], UNIT),
    fn("sample.nonNull", [param("kotlin.Any", ts_("{}", {returns: "{} | undefined", open: true}))], UNIT),
    fn("sample.voidButInt", [], DOUBLE),
    fn("sample.promised", [], ts_("{}", {returns: "{} | undefined", open: true})),
    fn("sample.missingNull", [], {...STRING, nullable: true}),
    fn("sample.extraNull", [], STRING),
    fn("sample.point", [], {java: "org.sample.Point", nullable: true}),
    fn("sample.list", [], ts_("readonly (number)[]", {returns: "readonly (number)[]"})),
    fn("sample.generic", [param("kotlin.String", STRING)], STRING),
    fn("sample.anything", [param("kotlin.Any?", {unmapped: "kotlin.Any?"})], {unmapped: "kotlin.Any?"}),
    fn("sample.rest", [param("kotlin.String", STRING, {vararg: true})], UNIT),
    fn("sample.undeclared", [], {java: "org.sample.Missing", nullable: false}),
    {api: "sample.count", module: "sample", kind: "accessor", query: "sample.count", value: {kotlin: "kotlin.Int", type: DOUBLE}},
    {api: "sample.size", module: "sample", kind: "accessor", query: "sample.size", value: {kotlin: "kotlin.String", type: STRING}},
];
const texts = typeExpressions(rows);
const files = {"__dts_types__.d.ts": declarations, "__dts_types_query__.ts": declarationText(texts)};
const options = {noEmit: true, strict: true, target: ts.ScriptTarget.ES2020, lib: ["lib.es2020.d.ts"], types: []};
const host = ts.createCompilerHost(options);
const readFile = host.readFile.bind(host), exists = host.fileExists.bind(host), getSourceFile = host.getSourceFile.bind(host);
host.fileExists = name => path.basename(name) in files || exists(name);
host.readFile = name => files[path.basename(name)] ?? readFile(name);
host.getSourceFile = (name, version) => path.basename(name) in files ? ts.createSourceFile(name, files[path.basename(name)], version) : getSourceFile(name, version);
const program = ts.createProgram(Object.keys(files), options, host);
const checker = program.getTypeChecker();
const source = program.getSourceFile("__dts_types_query__.ts");
const scope = checker.getSymbolsInScope(source, ts.SymbolFlags.Value);
const typeOf = api => {
    const [head, ...rest] = api.split(".");
    let symbol = scope.find(item => item.name === head);
    if (!symbol) return null;
    let type = checker.getTypeOfSymbolAtLocation(symbol, source);
    for (const member of rest) {
        symbol = checker.getPropertyOfType(checker.getNonNullableType(type), member);
        if (!symbol) return null;
        type = checker.getTypeOfSymbolAtLocation(symbol, source);
    }
    return type;
};
const result = Object.fromEntries(compareTypes({ts, checker, source, typeOf, texts}, rows).map(row => [row.api, row]));
const params = api => result[api].params.map(item => item.level);
const returned = api => result[api].returns.level;

assert.deepEqual(params("sample.exact"), ["match", "match"]);
assert.equal(returned("sample.exact"), "match");
assert.deepEqual(params("sample.literal"), ["narrow"], "literal union without the null a String? takes");
assert.deepEqual(params("sample.nullable"), ["refined"], "interface inside object, null kept");
assert.equal(returned("sample.nullable"), "refined");
assert.deepEqual(params("sample.noNull"), ["narrow"]);
assert.deepEqual(params("sample.coerced"), ["coerced"], "numbers reach a String parameter through toString");
assert.deepEqual(params("sample.wide"), ["wide"], "booleans are rejected by a Double parameter");
assert.deepEqual(params("sample.wrong"), ["error"]);
assert.deepEqual(params("sample.nonNull"), ["wide"], "null is rejected by kotlin.Any");
assert.equal(returned("sample.voidButInt"), "error");
assert.equal(returned("sample.promised"), "refined");
assert.equal(returned("sample.missingNull"), "narrow", "d.ts omits the null a String? returns");
assert.equal(returned("sample.extraNull"), "wide");
assert.equal(returned("sample.point"), "refined", "a subclass of the Java class, null kept");
assert.equal(returned("sample.list"), "refined", "mutable array of a readonly list");
assert.deepEqual(params("sample.generic"), ["unmapped"]);
assert.equal(result["sample.generic"].params[0].reason, "dts-any");
assert.equal(returned("sample.generic"), "unmapped");
assert.deepEqual(params("sample.anything"), ["unmapped"]);
assert.equal(result["sample.anything"].status, "unmapped");
assert.deepEqual(params("sample.rest"), ["match"], "vararg against a rest element");
assert.equal(result["sample.undeclared"].status, "no-dts");
assert.equal(returned("sample.count"), "refined", "literal of a number");
assert.equal(returned("sample.size"), "error");
assert.equal(result["sample.exact"].status, "typed");
assert.equal(result["sample.wide"].status, "differs");
console.log(`dts-types: 27 cases passed (TypeScript ${ts.version})`);
