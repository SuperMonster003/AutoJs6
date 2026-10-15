# AutoJs6 ACE Completion Tools

This directory owns the generated ACE completion artifacts for AutoJs6.

## Files

- `autojs6_indices.source.json`: authoritative AutoJs6 completion source.
- `autojs6_indices.schema.json`: JSON schema for the source shape.
- `autojs6-api-model.mjs`: declaration-backed intermediate API model builder.
- `type-backed-api-model.mjs`: resolves namespace merges, nested members and aliases with the editor's TypeScript runtime.
- `generate-editor-assets.mjs`: refreshes both static indices and semantic declaration bundles, then verifies both paths.
- `import-autojs6-dts.mjs`: imports selected AutoJs6 declaration files from a local declarations checkout.
- `generate-autojs6-indices-from-dts.mjs`: regenerates the completion source from bundled declaration files.
- `generate-autojs6-indices.mjs`: generates `autojs6_indices.js` and a build report.
- `generate-dts.mjs`: generates `lib.autojs6.d.ts` from the same source.
- `verify-autojs6-api-model.mjs`: verifies declaration-backed API model coverage.
- `verify-autojs6-pro-only.mjs`: detects reserved Pro-only API names in source and runtime indices.
- `verify-autojs6-indices.mjs`: verifies source structure and generated JS freshness.
- `verify-autojs6-dts.mjs`: verifies generated `lib.autojs6.d.ts` freshness and typed signatures.
- `test-completer.mjs`: runs the real browser completer in a Node VM.

## Commands

For the extracted Ace Editor plugin, first set the asset checkout in PowerShell:

```powershell
$env:AUTOJS6_ACE_EDITOR_ROOT = 'D:\idea-projects\AutoJs6-Plugin-Ace-Editor'
```

This redirects asset imports, generated bundles and completion checks to the
plugin. Source indices and reports remain in this host repository. When the
variable is omitted, the sibling `AutoJs6-Plugin-Ace-Editor` checkout is used.
The distribution directory is read from that checkout's `tools/ace-lsp/ace-builds-lock.json`.

After publishing and synchronizing declarations, use the unified entry point:

```text
node tools/ace-completion/generate-editor-assets.mjs
node tools/ace-completion/generate-editor-assets.mjs --check
```

The following individual commands are useful for focused work:

```text
node tools/ace-completion/import-autojs6-dts.mjs "<path-to-AutoJs6-TypeScript-Declarations>"
node tools/ace-completion/generate-autojs6-indices-from-dts.mjs
node tools/ace-completion/generate-autojs6-indices.mjs
node tools/ace-completion/generate-dts.mjs
node tools/ace-completion/verify-autojs6-api-model.mjs
node tools/ace-completion/verify-autojs6-pro-only.mjs
node tools/ace-completion/verify-autojs6-indices.mjs
node tools/ace-completion/verify-autojs6-dts.mjs
node tools/ace-completion/test-completer.mjs
```

Generated outputs:

- `<locked Ace asset root>/autojs6/autojs6_indices.js`
- `<locked Ace asset root>/autojs6/types/lib.autojs6.d.ts`
- `<locked Ace asset root>/autojs6/types/autojs6`
- `build/reports/ace-completion/autojs6-api-model.json`
- `build/reports/ace-completion/autojs6-api-model-report.md`
- `build/reports/ace-completion/autojs6-indices-report.md`

`autojs_indices.js` remains a compatibility fallback only. New broad AutoJs6 entries should come from the bundled selected `aj6-int-*.d.ts` files first, then from explicit source JSON fallback entries when the declaration parser cannot infer them safely.

JSOX Array, Number and Math extensions are generated separately and become available only
after an explicit unshadowed top-level `jsox` / `$jsox` activation in the current script.
Nested module paths and `$` aliases share the same declarations as their canonical objects.
