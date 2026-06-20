# ESM Partial v1.1

Status: partial implementation enabled by default for Node Profile v1.1.
The legacy build property `autojs.nodejs.esm.experimental` remains the switch
used by existing build and test plumbing, but its default is now `true`.

## Goal

AutoJs6 must eventually account for `.mjs`, `package.json` `type`, static `import`/`export`, top-level await, and dynamic `import()`, because users will compare the Node entry behavior with Auto.js Pro and AutoX.

The v1.1 implementation promotes the existing controlled subset:

- `.mjs` routes to the Node backend and runs as partial ESM by default.
- dynamic import runs through the local-only AutoJs6-managed loader by default.
- package `type=module` switches `.js` entry files into ESM mode.
- encrypted and packaged module graphs are not interpreted as unrestricted Node ESM graphs.

## Feature Flag

The legacy ESM switch is:

```properties
autojs.nodejs.esm.experimental=true
```

Current wiring:

- Gradle property default: `true`
- BuildConfig field: `NODEJS_ESM_EXPERIMENTAL_ENABLED`
- Kotlin capability registry: `NodeRuntimeCapabilities.ESM_EXPERIMENTAL_PROPERTY`
- JavaScript profile snapshot: `require("autojs6:profile").experimentalFlags.esm`
- Embedded script request flag: `esmExperimentalEnabled`

The flag enables only AutoJs6 partial ESM v1.1. It does not enable Node's raw ESM loader, loader hooks, raw `node:module` loader authority, network imports, native addons, or unrestricted builtins.

Dynamic import is intentionally split into a local-only switch:

```properties
autojs.nodejs.dynamicImport.local=true
```

Current dynamic import wiring:

- Gradle property default: `true`
- BuildConfig field: `NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED`
- Kotlin capability registry: `NodeRuntimeCapabilities.DYNAMIC_IMPORT_LOCAL_PROPERTY`
- JavaScript profile snapshot: `require("autojs6:profile").experimentalFlags.dynamicImport`
- Embedded script request flag: `dynamicImportExperimentalEnabled`

The legacy broad experiment remains release-forbidden:

```properties
autojs.nodejs.dynamicImport.experimental=false
```

## Current Default Behavior

Default behavior:

- `.mjs` entries run as partial ESM.
- `.js` entries run as ESM only when the nearest scoped `package.json` contains `"type": "module"`.
- static `import`/`export` syntax in CommonJS entries still fails with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- dynamic `import()` supports local-only package, builtin, `.mjs`, and CommonJS imports.
- `import.meta` fails with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- CommonJS `require()` can synchronously load `.mjs`, `.mts`, and `type=module` `.js` targets when the partial ESM graph has no `await` token and no dynamic `import()`.
- CommonJS `require()` of an asynchronous partial ESM graph fails with `ERR_REQUIRE_ASYNC_MODULE`.

Explicitly setting `autojs.nodejs.esm.experimental=false` or an internal request
flag to false preserves the previous fail-closed path for diagnostics.

## Partial v1.1 Behavior

- `.mjs` entry scripts can run.
- `.js` entry scripts can run as ESM only when the nearest scoped `package.json` contains `"type": "module"`.
- local static imports of `.mjs` files inside `workingDirectory` are supported.
- ESM may default-import scoped CommonJS modules (`.cjs`, `.js`, or `.json`) through the existing CommonJS loader cache.
- ESM may import supported builtin modules through the existing Safe Node builtin allowlist and limited builtin wrappers.
- ESM bare package imports may select package `exports` using the ESM condition set `import`, `node`, `default`, then load a CommonJS target through the CommonJS loader.
- ESM package imports (`#alias`) may select package `imports` using the same ESM condition set.
- ESM package `exports` and `imports` may target `.mjs` files inside the package root.
- ESM self-reference and nested package imports use the ESM condition set.
- CommonJS `require()` may load synchronous partial ESM targets without changing CommonJS package condition order.
- simple named exports are supported for local `.mjs` modules.
- top-level await is supported because the partial ESM graph is evaluated through an async runner.
- diagnostics are emitted in the result envelope:
  - `esmEnabled`
  - `esmEntry`
  - `esmModuleGraphRoot`
  - `esmModuleGraphSize`
  - `esmModuleGraphModules`
  - `esmDeniedCount`
  - `esmLastDeniedReason`
  - `dynamicImportEnabled`

Still unsupported in v1:

- `import.meta`
- CJS `require()` of asynchronous ESM graphs that contain `await` or dynamic `import()`; this fails with `ERR_REQUIRE_ASYNC_MODULE`
- ESM JSON import assertions; JSON may only be reached through the CommonJS interop path
- custom loaders and `node:module` loader hooks
- network, `file:`, `data:`, or other URL imports
- imports outside `workingDirectory`

## Routing Decisions

`.mjs`:

- Always routes to the Node backend so user intent is preserved.
- Default profile runs it as partial ESM.
- Explicit internal ESM-off requests reject before running code.

`.cjs`:

- Always remains CommonJS.
- Must not be affected by package `type=module`.

`.js`:

- Current default remains CommonJS unless the nearest scoped `package.json` has `"type": "module"`.
- Partial ESM treats `.js` as ESM only for the entry file and only when that package scope declares `"type": "module"`.

`package.json` `type=module`:

- Current default: may switch only the entry `.js` file in that package scope to ESM.

`package.json` `type=commonjs`:

- Current and future default: `.js` remains CommonJS.
- It should override parent `type=module` scopes once ESM mode exists.

## Import And Export Parsing

CommonJS entries use a conservative preflight scan to reject obvious ESM forms before CommonJS execution.

Partial v1.1 uses a deliberately small AutoJs6-managed transform for local static `.mjs` imports and simple exports. It is not a full Node ESM parser and is documented as a safe compatibility subset.

For packaged APKs, partial v1.1 verifies a small encrypted graph: a `.mjs`
entry can import a local `.mjs` dependency and a local CommonJS dependency
through budgeted encrypted `runtimeModuleSources`. Dynamic import graphs remain
outside this support contract.

Future implementation options:

- Native Node ESM loader integration, if embedder lifecycle ownership can be made reliable.
- AutoJs6-managed module graph that compiles ESM through V8 with explicit linking.
- A transpile-to-CJS compatibility mode for limited package patterns, only if documented as non-Node-compatible.

The preferred long-term direction is real ESM semantics through an explicit loader graph, not ad hoc transpilation.

## CJS And ESM Interop

CJS `require()` targeting ESM:

- Current default: load synchronous partial ESM graphs and return the ESM namespace object.
- CommonJS condition order remains `require`, `node`, `default`; `require()` does not select package `import` conditions.
- Graphs containing `await` or dynamic `import()` reject with `ERR_REQUIRE_ASYNC_MODULE` because they cannot be synchronously evaluated.
- Explicit ESM-off diagnostic requests may still reject earlier with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- Long-term behavior may align more closely with Node's `require(esm)` semantics where appropriate, but AutoJs6 policy errors must remain stable.

ESM importing CJS:

- Current default: allowed for scoped CommonJS modules.
- Partial v1.1 allows default import and namespace-style property access from scoped CommonJS modules by reusing the existing CommonJS cache.
- No raw native handles or unrestricted builtin access may be gained through interop.

CommonJS importing JSON:

- Remains supported.
- Future ESM JSON import assertions are out of scope for v1.

## Top-Level Await

Current default: supported in partial ESM entries and modules.

Future hardening should ensure:

- pending module evaluation is tracked as runtime work
- timeout and service destroy reject/stop cleanly
- lifecycle diagnostics distinguish module linking from script evaluation
- packaged smoke covers a top-level-await entry

## Dynamic Import

Current default: enabled as local-only partial support.

AutoJs6-managed dynamic import supports:

- local relative specifiers such as `./dep.mjs` and `./dep.cjs`
- safe package specifiers resolved inside `workingDirectory`
- allowed builtin specifiers such as `node:path`
- package `exports`/`imports` condition order for ESM importers
- the same ESM/CJS interop path as static ESM imports

It rejects:

- disabled builtin specifiers such as `node:child_process`
- absolute paths
- `file:`, `data:`, and other protocol imports
- `http:` and `https:` network imports
- `..` or realpath escapes outside `workingDirectory`
- dynamic-code sources such as `eval` and `Function`

Dynamic import still must answer in future phases:

- broader npm package graphs
- richer package/export graph metadata for packaged APKs
- how scoped file URLs are validated
- how service destroy cancels pending imports

The current implementation rewrites `import()` to the AutoJs6-managed loader instead of using Node's raw ESM loader.

## import.meta.url

Current default: disabled.

Future behavior must avoid leaking unrestricted absolute host paths.

Allowed directions:

- scoped `file:` URLs only after the file URL policy can validate them
- `autojs6-node:` virtual URLs for encrypted or moduleSources-backed entries
- stable source names for stack traces without exposing private cache extraction paths

`import.meta.resolve` is out of scope for v1.

## Package Exports Conditions

Current CommonJS condition order remains:

```text
require, node, default
```

ESM condition order is importer-sensitive:

- CommonJS importer: `require`, `node`, `default`
- ESM importer: `import`, `node`, `default`

The resolver rejects package export/import targets that escape the package root, including under ESM mode.

## moduleSources And Encrypted Graphs

Current `moduleSources` and encrypted `runtimeModuleSources` are source records
with stable `sourceURL` metadata. Partial v1.1 allows `.mjs` records in the same
budgeted path used by packaged encrypted CommonJS records.

Broader ESM graph support still requires metadata for each source:

- canonical module id
- source kind: `commonjs`, `esm`, `json`
- source name for stack traces
- package scope and nearest package metadata
- encrypted entry id or module source id
- dependency specifiers discovered at build time where possible

Packaged encrypted ESM cannot depend on arbitrary runtime filesystem discovery
outside the scoped package graph.

## Packaged APK Preloading

Packaged ESM support is limited to the verifier proving:

- ESM entry source exists
- the focused local `.mjs` and CommonJS graph sources are present or
  intentionally runtime-loaded
- encrypted sources keep stable source names in stack traces
- native library and service requirements still pass
- source byte and count budgets remain enforced

Package `exports`/`imports` condition metadata and dynamic import graphs remain
follow-up gates.

## Source Names And Stack Traces

Current CJS and partial ESM stack traces use existing source names.

Broader ESM stack traces must:

- show the user project-relative path or stable module source name
- avoid leaking private extraction/cache paths
- distinguish encrypted packaged sources from plain filesystem sources
- keep line and column numbers stable
- preserve `sourceName` in bridge and lifecycle diagnostics

## Tests Required For Partial Support

The partial profile must keep tests for:

- explicit ESM-off requests reject `.mjs` with the documented message
- CommonJS entries reject static import/export syntax and dynamic import when the dynamic gate is off
- `import.meta` remains rejected until a file URL policy is selected
- minimal `.mjs` entry smoke
- package `type=module` `.js` entry smoke
- CJS requiring ESM rejection or interop behavior
- ESM importing CJS behavior
- top-level await timeout and success paths
- dynamic import rejection or success paths, separately from static ESM
- package `exports` condition selection for `import` vs `require`
- scoped fs and package target escape denial in ESM graphs
- moduleSources and encrypted packaged ESM graph smoke; partial v1.1 currently has a focused `.mjs` entry plus local `.mjs`/CommonJS import graph smoke gated by refreshed template support
- sourceName and stack trace shape
- service destroy while module linking/evaluation is pending

## Current Decision

ESM is partial and enabled by default for Node Profile v1.1. Partial v1.1 enables local static `.mjs` imports for plain working directories, simple exports, top-level await, package `type=module` entry routing, ESM-to-CJS default interop, CJS `require()` of synchronous ESM graphs, supported builtin imports, package `exports`/`imports` ESM condition selection, nested package imports, self-reference, diagnostics, local-only dynamic import, and a focused packaged `.mjs` graph smoke with refreshed template support. Dynamic import is limited to local relative modules, allowed builtins, and packages resolved inside `workingDirectory`. This path still does not enable loader hooks, raw `node:module` loader authority, network imports, CJS `require()` of asynchronous ESM graphs, unrestricted file URLs, or unrestricted packaged encrypted ESM graphs.
