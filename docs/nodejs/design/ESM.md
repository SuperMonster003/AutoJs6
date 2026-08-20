# ESM And Dynamic Import

Status: stable and enabled by default in Node Profile v1.2.

## Runtime Contract

ESM and dynamic import are compiled into every Node-enabled Debug and Release
variant. They have no Gradle opt-in, release block, project override, or
packaged-APK override.

Current wiring:

- BuildConfig: `NODEJS_ESM_ENABLED=true` and
  `NODEJS_DYNAMIC_IMPORT_ENABLED=true`
- host/plugin request contract v2: `esmEnabled` and `dynamicImportEnabled`
- profile snapshot: `featureStates.esm=true` and
  `featureStates.dynamicImport=true`
- capability status: `stable`

The request booleans are versioned host/plugin transport fields, not user-facing
feature switches. AutoJs6 sends both as enabled. Project and package metadata
cannot turn them on or off.

## Supported Entry And Module Forms

The scoped loader supports:

- `.mjs` and `.mts` entry files;
- `.js` entries selected by nearest `package.json` `type=module`;
- source-detected static `import`/`export` and `import.meta`;
- top-level await;
- local relative, scoped absolute, package, package-subpath, and supported
  builtin imports;
- local dynamic `import()`;
- `import.meta.url`, `filename`, `dirname`, `main`, and scoped
  `resolve()`;
- JSON imports with `with { type: "json" }`;
- ESM-to-CommonJS interop and synchronous CommonJS-to-ESM loading for graphs
  that do not require async evaluation;
- encrypted and packaged module-source graphs through the versioned host source
  provider.

CommonJS `require()` of an async ESM graph rejects with
`ERR_REQUIRE_ASYNC_MODULE`. This is a runtime semantic limit, not a feature
gate.

## Security And Resolution Boundary

Formal ESM support does not enable an unrestricted desktop loader:

- `http:`, `https:`, `data:`, arbitrary custom schemes, and remote URL
  imports are denied;
- file URLs and paths must resolve inside the execution sandbox or an authorized
  module-source graph;
- local packages cannot shadow reserved AutoJs6 modules or denied builtins;
- native `.node` addons remain unsupported;
- custom Node loader hooks and unrestricted `node:module` internals are not
  exposed;
- package `exports`/`imports` targets are checked for traversal and protocol
  escapes before loading.

Raw network builtins being stable does not make network URLs valid ESM module
specifiers.

## Packaging

Packaged scripts preserve the same entry-mode, resolver, import-attribute, and
scope rules. The APK builder must include every referenced source in its
approved graph and must fail preflight for path escapes, unresolved modules, or
native addons. Release and AAR generation do not change ESM availability.

## Verification

The ESM gate covers entry routing, package mode, CJS interop, top-level await,
dynamic import, `import.meta`, JSON attributes, scoped resolution, encrypted
graphs, packaged APK execution, source maps, and stable error codes. The primary
instrumentation class is
`NodeEsmStableInstrumentationTest`.

Historical pre-GA evaluation is retained in
[../reports/ESM_PRE_GA_EVALUATION.md](../reports/ESM_PRE_GA_EVALUATION.md).
