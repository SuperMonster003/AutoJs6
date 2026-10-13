# TypeScript parser contract V1

Status: V1, added 2026-10-13 for the 3-Block Compose designer's TSX projects (plugin roadmap D42).
The host owns `plugin-api/typescript-parser-api` (package `org.autojs.plugin.typescript.parser`);
the TypeScript Engine plugin provides the service and the 3-Block Compose plugin is the first client.
The host application itself neither binds nor packages this module.

## Why a separate contract

The TypeScript compiler contract (`plugin-api/typescript-api`, `ITypeScriptCompilerPlugin`) is frozen:
its transaction prefix and archive transport are hash-locked and it only grows through additive Bundle
keys. Parsing a source file into a syntax tree is a different operation: nothing is type-checked, emitted
or executed, the input is one in-memory file, and a parse must not wait for or block a compilation. It
therefore has its own AIDL interface, intent action, contract version and hash; the compiler contract and
its freeze checks are unchanged.

## Binder surface

```aidl
interface ITypeScriptParserPlugin {
    int getContractVersion();
    String getContractHash();
    Bundle getParserInfo();
    Bundle parse(in Bundle request, in ParcelFileDescriptor treeOutput);
}
```

- The service is bound with action `org.autojs.plugin.typescript.PARSER` and must be protected by
  `org.autojs.permission.PLUGIN`. A provider advertises it in its PluginInfo capabilities:
  `typescriptParserContractVersion` (int), `typescriptParserServiceAction` (string) and
  `typescriptParserTreeSchemaRevision` (int), so clients can decide before binding.
- `TypeScriptParserContract.HASH` is the SHA-256 of `HASH_INPUT`, which lists the transactions in order.
  Transactions are append-only; reordering or changing one requires a new contract version.
- Bundles carry primitives, strings and string arrays only.

## Parser info

`getParserInfo()` returns `contractVersion`, `contractHash`, `treeSchemaRevision`, `status`
(`ready`, `starting` or `unavailable`), `compilerVersion` (the exact TypeScript version),
`supportedExtensions` (`.ts`, `.tsx`, `.mts`, `.cts`, `.js`, `.jsx`), `maxSourceChars` and
`maxConcurrentParses`.

## Parse

Request keys: `contractVersion` (must equal 1), `requestId` (at most 128 chars), `fileName` (a file name
with one of the supported extensions, at most 255 chars; it selects the script kind and JSX handling, no
file is opened), `sourceText` (at most 262144 UTF-16 chars, so the request stays far below the Binder
buffer) and optional `timeoutMs` (1000 - 60000, default 15000, which covers a cold start of the provider's
runtime).

`treeOutput` is a writable file descriptor owned by the caller, normally a file in its cache directory. The
provider writes the UTF-8 JSON tree to it, flushes and closes its copy, then returns. Writing to a file
rather than a pipe means the caller reads after the call without risking a full-pipe deadlock.

Result keys: `outcome` (`parsed`, `failed` or `timed_out`), and for `parsed` also `treeByteLength`,
`treeSha256`, `nodeCount`, `diagnosticCount` and `elapsedMs`; for the other outcomes `errorCode` and
`errorMessage` (at most 4096 chars). A source with syntax errors is still `parsed`: the errors are in the
tree's `diagnostics`, and the tree contains TypeScript's recovery nodes.

Error codes (`ERR_AUTOJS6_TYPESCRIPT_PARSER_*`): `INVALID_REQUEST`, `CONTRACT_MISMATCH`, `BUSY` (more than
`maxConcurrentParses` = 2 parses at once), `TIMEOUT`, `UNAVAILABLE`, `TREE_LIMIT` (more than 250000 nodes,
32 MiB of JSON or 1000 diagnostics) and `OUTPUT_TRANSPORT`.

Parses never take a compilation slot or the compiler's admission lease, and compilations never wait for
a parse.

## Tree JSON (schema revision 1)

```json
{
  "schemaRevision": 1,
  "compilerVersion": "6.0.3",
  "fileName": "counter.tsx",
  "languageVariant": "jsx",
  "nodeCount": 154,
  "diagnostics": [{"start": 120, "length": 1, "code": 1005, "category": "error", "message": "'}' expected."}],
  "root": {"kind": "SourceFile", "start": 0, "end": 812, "statements": [ ... ], "endOfFileToken": { ... }}
}
```

- Every node has `kind` (the `ts.SyntaxKind` name), `start` (the position of its first token, leading
  trivia and JSDoc excluded, as `getStart` returns) and `end`. Positions are UTF-16 offsets into
  `sourceText`, the same unit as JavaScript and Java / Kotlin string indices.
- A node holds its child nodes under the property names of the TypeScript compiler API, in the order the
  properties appear on the node: a single node (`initializer`, `tagName`, `expression`, ...) or an array of
  nodes (`statements`, `properties`, `arguments`, `children`, ...). Missing optional children are omitted.
  JSDoc nodes are not included.
- Scalars: `text` on identifiers, private identifiers, string / numeric / bigint / regular expression and
  template literals and `JsxText` (the unescaped text TypeScript reports); `triviaOnly` on `JsxText` that
  contains only white space; `declarationKind` (`var`, `let`, `const`, `using` or `await using`) on
  `VariableDeclarationList`; `operator` (a `ts.SyntaxKind` name) on prefix and postfix unary expressions.
- Diagnostics are the syntactic diagnostics of the source file: `start`, `length`, `code`, `category`
  (`error`, `warning`, `suggestion` or `message`) and `message` (flattened).

Clients slice `sourceText` with `start` / `end` for anything the tree does not spell out (for example the
exact source of an expression), and must ignore unknown members and node kinds so that newer TypeScript
versions do not break them; the schema revision changes only when an existing member changes meaning.
