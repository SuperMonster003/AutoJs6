#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import os from "node:os";
import {createRequire} from "node:module";
import {spawnSync} from "node:child_process";
import {performance} from "node:perf_hooks";

const options = {};
for (let i = 2; i < process.argv.length; i++) {
    const name = process.argv[i];
    if (name === "--skip-ace") options.skipAce = true;
    else {
        if (!name.startsWith("--") || !process.argv[i + 1]) throw new Error(`Invalid argument: ${name}`);
        options[name.slice(2)] = path.resolve(process.argv[++i]);
    }
}
for (const key of ["exports", "declarations", "ace", "output"]) {
    if (!options[key]) throw new Error(`Required argument: --${key}`);
}
const read = file => fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
const baseline = JSON.parse(read(options.exports));
const queries = baseline.queries;
if (!Array.isArray(queries) || !queries.length) throw new Error("No public API queries");
const lock = JSON.parse(read(path.join(options.ace, "tools/ace-lsp/ace-builds-lock.json")));
const assets = path.join(options.ace, lock.assetRoot, "autojs6");
const require = createRequire(import.meta.url);
const ts = require(options.typescript || path.join(assets, "typescript/typescript.js"));
const queryFile = path.join(options.output, "__coverage_query__.ts");
const compilerOptions = {noEmit: true, skipLibCheck: true, strict: true, target: ts.ScriptTarget.ES2020, lib: ["lib.es2020.d.ts"], types: []};
const host = ts.createCompilerHost(compilerOptions);
const readFile = host.readFile.bind(host), exists = host.fileExists.bind(host);
host.readFile = file => path.resolve(file) === queryFile ? "" : readFile(file);
host.fileExists = file => path.resolve(file) === queryFile || exists(file);
const program = ts.createProgram([path.join(options.declarations, "declarations/autojs6/index.d.ts"), queryFile], compilerOptions, host);
const checker = program.getTypeChecker();
const source = program.getSourceFile(queryFile);
const globals = new Map(checker.getSymbolsInScope(source, ts.SymbolFlags.Value).map(symbol => [symbol.name, symbol]));

function typeOf(api) {
    const parts = api.split(".");
    let symbol = globals.get(parts.shift());
    if (!symbol) return {status: "missing-global"};
    let type = checker.getTypeOfSymbolAtLocation(symbol, source);
    for (const member of parts) {
        if (type.flags & ts.TypeFlags.Any) return {status: "parent-any"};
        if (member === "()" || member === "new()") {
            const signatures = checker.getSignaturesOfType(type, member === "()" ? ts.SignatureKind.Call : ts.SignatureKind.Construct);
            if (!signatures.length) return {status: "not-callable"};
            type = checker.getReturnTypeOfSignature(signatures[0]);
        } else if (member === "await") {
            type = checker.getAwaitedType(type);
        } else {
            symbol = checker.getPropertyOfType(checker.getNonNullableType(type), member);
            if (!symbol) return {status: "missing-member"};
            type = checker.getTypeOfSymbolAtLocation(symbol, source);
        }
    }
    return {status: type.flags & ts.TypeFlags.Any ? "any" : "explicit", type: checker.typeToString(type)};
}

function session(text) {
    const lines = text.split("\n");
    return {getLength: () => lines.length, getLine: row => lines[row] || "", getValue: () => text,
        doc: {positionToIndex: position => lines.slice(0, position.row).reduce((sum, line) => sum + line.length + 1, 0) + position.column}};
}

function position(text) {
    const lines = text.split("\n");
    return {row: lines.length - 1, column: lines.at(-1).length};
}

function completionNames(provider, text, prefix, staticProvider = false) {
    let result, failure;
    const callback = (error, items) => { failure = error; result = items || []; };
    if (staticProvider) provider.getCompletions(null, session(text), position(text), prefix, callback);
    else provider.getCompletions(session(text), position(text), prefix, callback, text);
    if (failure) throw failure;
    if (!result) throw new Error("Completion provider did not finish synchronously");
    return result;
}

const rows = queries.map(api => ({api, declaration: typeOf(api)}));
let temp;
try {
    if (!options.skipAce) {
        // Always generate into a fresh temporary directory. Never audit a stale
        // previous build or update the checked-in/plugin build assets here.
        temp = fs.mkdtempSync(path.join(os.tmpdir(), "autojs6-api-coverage-"));
        const generator = spawnSync(process.execPath, [path.join(options.ace, "tools/ace-lsp/generate-declarations.mjs"), "--source", path.join(assets, "types"), "--output", temp, "--typescript", path.join(assets, "typescript/typescript.js")], {encoding: "utf8"});
        if (generator.status !== 0) throw new Error(`LSP declaration generation failed:\n${generator.stdout}\n${generator.stderr}`);
        const generated = path.join(temp, "editor", path.basename(path.dirname(assets)), "autojs6/types/generated");
        const context = {console, performance, setTimeout, clearTimeout};
        context.window = context;
        vm.createContext(context);
        const optionalDetector = path.join(assets, "autojs6_jsox_extensions.js");
        if (fs.existsSync(optionalDetector)) vm.runInContext(read(optionalDetector), context, {filename: optionalDetector});
        for (const file of ["typescript/typescript.js", "autojs6_ts_language_service.js", "autojs6_indices.js", "autojs6_local_symbols.js", "autojs6_completer.js"]) {
            vm.runInContext(read(path.join(assets, file)), context, {filename: file});
        }
        const libraries = {};
        for (const file of fs.readdirSync(path.join(assets, "typescript")).filter(name => /^lib\..+\.d\.ts$/.test(name))) {
            libraries[`file:///autojs6/typescript/${file}`] = read(path.join(assets, "typescript", file));
        }
        for (const file of fs.readdirSync(generated).filter(name => name.endsWith(".d.ts"))) {
            libraries[`file:///autojs6/types/generated/${file}`] = read(path.join(generated, file));
        }
        libraries["file:///autojs6/types/lib.autojs6.extra.d.ts"] = read(path.join(assets, "types/lib.autojs6.extra.d.ts"));
        const factory = context.AutoJsAceTsLanguageService;
        const defaults = factory.create({documentUri: "file:///autojs6/editor/coverage.js", libraryTextByUri: libraries, completionLimit: 3000});
        const all = factory.create({documentUri: "file:///autojs6/editor/coverage-all.js", libraryTextByUri: libraries,
            libraryUris: [...factory.constants.defaultLibraryUris, ...["android", "libraries", "resources", "main-app"].map(group => `file:///autojs6/types/generated/lib.autojs6.${group}.d.ts`)], completionLimit: 3000});
        const fallback = context.AutoJsAceCompleter.createCompleter(context.AutoJsAceIndices);
        const returnProbe = api => api.includes(".().") || api.includes(".new().") || api.includes(".await.");
        for (const row of rows) {
            const text = row.api.replaceAll(".()", "()");
            const prefix = text.slice(text.lastIndexOf(".") + 1);
            row.aceDefault = completionNames(defaults, text, prefix).some(item => item.caption === prefix);
            row.aceAllGroups = completionNames(all, text, prefix).some(item => item.caption === prefix);
            if (!returnProbe(row.api)) {
                row.aceStatic = completionNames(fallback, text, prefix, true).some(item =>
                    item.caption === prefix && (!row.api.includes(".") || item.autojs6MemberContext));
            }
        }
        defaults.dispose?.();
        all.dispose?.();
    }
    const failures = rows.filter(row => row.declaration.status !== "explicit" || (!options.skipAce && (!row.aceDefault || !row.aceAllGroups || row.aceStatic === false)));
    const report = {typescript: ts.version, count: rows.length, aceChecked: !options.skipAce, failures, rows};
    fs.mkdirSync(options.output, {recursive: true});
    fs.writeFileSync(path.join(options.output, "surfaces.json"), JSON.stringify(report, null, 2) + "\n");
    console.log(JSON.stringify({queriedPaths: rows.length, failingPaths: failures.length, failures: failures.map(({api, declaration, aceDefault, aceAllGroups, aceStatic}) => ({api, declaration: declaration.status, aceDefault, aceAllGroups, aceStatic}))}, null, 2));
    if (failures.length) process.exitCode = 1;
} finally {
    if (temp) {
        const resolved = path.resolve(temp);
        if (path.dirname(resolved) !== path.resolve(os.tmpdir()) || !path.basename(resolved).startsWith("autojs6-api-coverage-")) {
            throw new Error(`Refusing to remove unexpected temporary path: ${resolved}`);
        }
        fs.rmSync(resolved, {recursive: true, force: true});
    }
}
