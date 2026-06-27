# P13-41 ESM Loader Parity

P13-41 records ESM loader parity as partial.

## Decision

AutoJs6 Node v1.1 keeps the controlled ESM subset as default-on partial
support. This is not full desktop Node loader parity: the runtime uses the
AutoJs6-managed local transform/loader path and does not expose raw Node loader
hooks, network imports, unrestricted URL imports, or custom loader authority.

The new `autojs6:profile.esmLoaderProfile` diagnostic snapshot records the
current split:

- `.mjs` entries, `package.json` `type=module` entries, local static imports
  including common multiline/compact declarations, top-level await, local
  dynamic import, JSON import attributes,
  `import.meta`, simple ESM re-exports, package `exports` / `imports`, and
  synchronous CJS `require(esm)` are partial.
- Controlled `data:` URL imports are partial for inline JavaScript and JSON
  modules. JSON data URLs require `with { type: "json" }`; other MIME types
  and data-module relative/package imports remain denied.
- Named import/export/re-export specifier lists accept ordinary block and line
  comments, covering generated package output such as parse5's
  `export { /** @internal */ Parser } from ...`.
- `moduleSources` and focused packaged ESM/dynamic import smokes are partial.
- loader hooks, raw `node:module` loader authority, network imports, `file:`
  URL imports, unrestricted URL imports, absolute path imports, working-directory escapes,
  disabled builtins, and raw Node loader handles are denied.

## Boundary Decision

- Native Node ESM loader hooks and raw `node:module` loader authority remain
  stable-denied for P13-41. The only exposed module behavior is the
  AutoJs6-managed local transform/loader path.
- Custom condition sets, import maps, and loader registration APIs are not
  promoted. Package `exports` / `imports` condition handling stays limited to
  the controlled AutoJs6 package policy and importer-mode split already covered
  by instrumentation.
- Network, `file:`, unrestricted URL, absolute path, working-directory escape,
  disabled builtin, and raw loader-handle imports remain denied.

## Evidence

- Runtime diagnostics:
  - `require("autojs6:profile").esmLoaderProfile`
  - `NodeRuntimeCapabilities` partial default-on `esm` and local
    `dynamic_import` flags.
- Focused device coverage:
  - `NodeEsmLoaderPhase13DecisionInstrumentationTest`
  - `NodeEsmExperimentalInstrumentationTest`
  - `NodeSourceMapStackTraceInstrumentationTest`
  - `NodeSecurityRegressionInstrumentationTest`
- Packaged evidence:
  - `EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk`
  - `EmbeddedNodePackagedApkSmokeTest#installAndRunDynamicImportLocalEmbeddedNodePackagedApk`
  - `:app:verifyNodePackagedEsmGate`
- Host gate:
  - `tools/nodejs/phase13/verify-esm-loader-parity-decision.js`
  - `:app:verifyNodePhase13EsmLoaderParityDecision`
  - `build/reports/nodejs/phase13-esm-loader-parity-decision.json`
  - `build/reports/nodejs/phase13-esm-loader-parity-decision.md`

## Remaining Blockers

- Packaged encrypted package `exports` / `imports` graphs and broader dynamic
  import package graphs remain design-gated.
- Pending top-level-await/linking cancellation and service-destroy cleanup need
  a dedicated stress gate.
- ESM source-map parity is not yet equivalent to full desktop
  DevTools/source-map behavior.

The docs, release matrix, surface manifest, type docs, and Gradle gate are
evidence checks only. They do not add a second blocker for the same packaged
graph, pending-evaluation cleanup, and source-map parity work.

## Rollback

Remove `esmLoaderProfile` and the P13-41 verifier/report entries. Leave the
existing ESM runtime flags and partial ESM tests unchanged unless a separate
runtime regression requires disabling `autojs.nodejs.esm.experimental` or
`autojs.nodejs.dynamicImport.local`.
