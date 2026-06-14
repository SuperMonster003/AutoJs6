# ESM Experimental v1

Status: experimental implementation behind `autojs.nodejs.esm.experimental=false`.
Default builds keep ESM disabled.

## Goal

AutoJs6 must eventually account for `.mjs`, `package.json` `type`, static `import`/`export`, top-level await, and dynamic `import()`, because users will compare the Node entry behavior with Auto.js Pro and AutoX.

The first implementation keeps the current rejection explicit and stable by default:

- `.mjs` still routes to the Node backend.
- ESM source execution is rejected by default.
- dynamic import is rejected by default.
- package `type=module` does not silently switch `.js` files into ESM mode unless the experimental flag is enabled.
- encrypted and packaged module graphs are not interpreted as unrestricted Node ESM graphs.

## Feature Flag

The gate flag is:

```properties
autojs.nodejs.esm.experimental=false
```

Current wiring:

- Gradle property default: `false`
- BuildConfig field: `NODEJS_ESM_EXPERIMENTAL_ENABLED`
- Kotlin capability registry: `NodeRuntimeCapabilities.ESM_EXPERIMENTAL_PROPERTY`
- JavaScript profile snapshot: `require("autojs6:profile").experimentalFlags.esm`
- Embedded script request flag: `esmExperimentalEnabled`

The flag enables only AutoJs6 ESM v1. It does not enable Node's raw ESM loader, dynamic import, loader hooks, `node:module`, network imports, native addons, or unrestricted builtins.

Dynamic import is intentionally split into a second gate:

```properties
autojs.nodejs.dynamicImport.experimental=false
```

Current dynamic import wiring:

- Gradle property default: `false`
- BuildConfig field: `NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED`
- Kotlin capability registry: `NodeRuntimeCapabilities.DYNAMIC_IMPORT_EXPERIMENTAL_PROPERTY`
- JavaScript profile snapshot: `require("autojs6:profile").experimentalFlags.dynamicImport`
- Embedded script request flag: `dynamicImportExperimentalEnabled`

## Current Default Behavior

Default behavior with the flag off:

- `.mjs` enters the Embedded Node route and fails before execution with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- The error message must include:

```text
.mjs routes to Node backend, but ESM is not enabled in AutoJs6 Safe Node Profile.
```

- static `import`/`export` syntax in CommonJS sources fails with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- dynamic `import()` fails with legacy `code=ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED` and canonical `autojs6Code=ERR_AUTOJS6_DYNAMIC_IMPORT_UNSUPPORTED`.
- `import.meta` fails with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- package `type=module` is recorded as a diagnostic but `.js` loading remains CommonJS.

## Experimental v1 Behavior

With `autojs.nodejs.esm.experimental=true` or an internal embedded-script request that explicitly enables ESM:

- `.mjs` entry scripts can run.
- `.js` entry scripts can run as ESM only when the nearest scoped `package.json` contains `"type": "module"`.
- local static imports of `.mjs` files inside `workingDirectory` are supported.
- ESM may default-import scoped CommonJS modules (`.cjs`, `.js`, or `.json`) through the existing CommonJS loader cache.
- ESM may import supported builtin modules through the existing Safe Node builtin allowlist and limited builtin wrappers.
- ESM bare package imports may select package `exports` using the experimental ESM condition set `import`, `node`, `default`, then load a CommonJS target through the CommonJS loader.
- ESM package imports (`#alias`) may select package `imports` using the same ESM condition set.
- ESM package `exports` and `imports` may target `.mjs` files inside the package root.
- ESM self-reference and nested package imports use the ESM condition set.
- simple named exports are supported for local `.mjs` modules.
- top-level await is supported because the experimental graph is evaluated through an async runner.
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
- CJS `require()` of ESM; when the ESM flag is enabled this fails with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`
- ESM JSON import assertions; JSON may only be reached through the CommonJS interop path
- custom loaders and `node:module` loader hooks
- network, `file:`, `data:`, or other URL imports
- imports outside `workingDirectory`

## Routing Decisions

`.mjs`:

- Always routes to the Node backend so user intent is preserved.
- Default profile rejects before running code.
- Experimental ESM may enable only under `autojs.nodejs.esm.experimental=true` or an explicit internal embedded-script request flag.

`.cjs`:

- Always remains CommonJS.
- Must not be affected by package `type=module`.

`.js`:

- Current default remains CommonJS.
- Experimental ESM treats `.js` as ESM only for the entry file and only when the nearest scoped `package.json` has `"type": "module"`.

`package.json` `type=module`:

- Current default: diagnostic only, no execution mode switch.
- Experimental mode: may switch only the entry `.js` file in that package scope to ESM.

`package.json` `type=commonjs`:

- Current and future default: `.js` remains CommonJS.
- It should override parent `type=module` scopes once ESM mode exists.

## Import And Export Parsing

Current default uses a conservative preflight scan to reject obvious ESM forms before CommonJS execution.

Experimental v1 uses a deliberately small AutoJs6-managed transform for local static `.mjs` imports and simple exports. It is not a full Node ESM parser and is documented as a safe compatibility subset.

For packaged APKs, experimental v1 currently verifies single-file `.mjs` entry execution only. Packaged project files are encrypted by the existing inrt pipeline, so local static imports inside packaged encrypted graphs need explicit moduleSources/encrypted-source metadata before they can be enabled safely.

Future implementation options:

- Native Node ESM loader integration, if embedder lifecycle ownership can be made reliable.
- AutoJs6-managed module graph that compiles ESM through V8 with explicit linking.
- A transpile-to-CJS compatibility mode for limited package patterns, only if documented as non-Node-compatible.

The preferred long-term direction is real ESM semantics through an explicit loader graph, not ad hoc transpilation.

## CJS And ESM Interop

CJS `require()` targeting ESM:

- Current default: reject with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`.
- Experimental v1 rejects with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`.
- Long-term behavior may follow Node's `ERR_REQUIRE_ESM` style where appropriate, but AutoJs6 policy errors must remain stable.

ESM importing CJS:

- Current default: unavailable because ESM is disabled.
- Experimental v1 allows default import and namespace-style property access from scoped CommonJS modules by reusing the existing CommonJS cache.
- No raw native handles or unrestricted builtin access may be gained through interop.

CommonJS importing JSON:

- Remains supported.
- Future ESM JSON import assertions are out of scope for v1.

## Top-Level Await

Current default: unavailable because ESM is unavailable.

Future experimental mode may support top-level await only if:

- pending module evaluation is tracked as runtime work
- timeout and service destroy reject/stop cleanly
- lifecycle diagnostics distinguish module linking from script evaluation
- packaged smoke covers a top-level-await entry

## Dynamic Import

Current default: disabled.

Static ESM and dynamic import are separate gates. Enabling `autojs.nodejs.esm.experimental=true` does not enable `import()`.

With both `autojs.nodejs.esm.experimental=true` and `autojs.nodejs.dynamicImport.experimental=true`, AutoJs6-managed dynamic import supports only:

- local relative specifiers such as `./dep.mjs`
- targets resolved inside `workingDirectory`
- the same ESM/CJS interop path as static ESM imports

It rejects:

- package and builtin dynamic imports
- absolute paths
- `file:`, `data:`, and other protocol imports
- `http:` and `https:` network imports
- `..` or realpath escapes outside `workingDirectory`

Dynamic import still must answer in future phases:

- whether CJS may call `import()`
- whether ESM may call `import()`
- whether imported graph chunks can be loaded after initial startup
- how encrypted packaged module sources are discovered
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

Experimental ESM condition order is importer-sensitive:

- CommonJS importer: `require`, `node`, `default`
- ESM importer: `import`, `node`, `default`

The resolver rejects package export/import targets that escape the package root, including under ESM mode.

## moduleSources And Encrypted Graphs

Current moduleSources are CommonJS-oriented source records.

Future ESM graph support requires metadata for each source:

- canonical module id
- source kind: `commonjs`, `esm`, `json`
- source name for stack traces
- package scope and nearest package metadata
- encrypted entry id or module source id
- dependency specifiers discovered at build time where possible

Packaged encrypted ESM cannot depend on arbitrary runtime filesystem discovery outside the scoped package graph.

## Packaged APK Preloading

Packaged ESM support is not ready until the verifier can prove:

- ESM entry source exists
- every packaged graph source is present or intentionally runtime-loaded
- encrypted sources keep stable source names
- package `exports`/`imports` conditions are represented
- native library and service requirements still pass
- source byte and count budgets remain enforced

If the packaged template lacks ESM graph metadata, ESM must reject even when the app contains `.mjs` files.

## Source Names And Stack Traces

Current CJS stack traces use existing source names.

Future ESM stack traces must:

- show the user project-relative path or stable module source name
- avoid leaking private extraction/cache paths
- distinguish encrypted packaged sources from plain filesystem sources
- keep line and column numbers stable
- preserve `sourceName` in bridge and lifecycle diagnostics

## Tests Required Before Enabling

The experimental flag may not enable ESM until tests cover:

- flag off rejects `.mjs` with the documented message
- flag off rejects static import, export, dynamic import, and `import.meta`
- package `type=module` default behavior remains documented
- flag on minimal `.mjs` entry smoke
- flag on package `type=module` `.js` smoke, if selected
- CJS requiring ESM rejection or interop behavior
- ESM importing CJS behavior, if selected
- top-level await timeout and success paths, if enabled
- dynamic import rejection or success paths, separately from static ESM
- package `exports` condition selection for `import` vs `require`
- scoped fs and package target escape denial in ESM graphs
- moduleSources and encrypted packaged ESM graph smoke; experimental v1 currently has a focused single-file packaged `.mjs` smoke gated by `autojs.nodejs.esm.experimental=true`
- sourceName and stack trace shape
- service destroy while module linking/evaluation is pending

## Current Decision

ESM remains disabled by default. Experimental v1 enables local static `.mjs` imports for plain working directories, simple exports, top-level await, package `type=module` entry routing, ESM-to-CJS default interop, supported builtin imports, package `exports`/`imports` ESM condition selection, nested package imports, self-reference, diagnostics, and a focused single-file packaged `.mjs` smoke when `autojs.nodejs.esm.experimental=true`. Dynamic import is a separate experiment behind `autojs.nodejs.dynamicImport.experimental=true` and is limited to local relative specifiers inside `workingDirectory`. This path still does not enable loader hooks, `node:module`, network imports, CJS `require()` of ESM, or unrestricted packaged encrypted ESM graphs.
