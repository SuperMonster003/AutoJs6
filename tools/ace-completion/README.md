# AutoJs6 ACE Completion Tools

This directory owns the generated ACE completion artifacts for AutoJs6.

## Files

- `autojs6_indices.source.json`: authoritative AutoJs6 completion source.
- `autojs6_indices.schema.json`: JSON schema for the source shape.
- `import-autojs6-dts.mjs`: imports selected AutoJs6 declaration files from a local declarations checkout.
- `generate-autojs6-indices-from-dts.mjs`: regenerates the completion source from bundled declaration files.
- `generate-autojs6-indices.mjs`: generates `autojs6_indices.js` and a build report.
- `generate-dts.mjs`: generates `lib.autojs6.d.ts` from the same source.
- `verify-autojs6-indices.mjs`: verifies source structure and generated JS freshness.
- `test-completer.mjs`: runs the real browser completer in a Node VM.

## Commands

```text
node tools/ace-completion/import-autojs6-dts.mjs "<path-to-AutoJs6-TypeScript-Declarations>"
node tools/ace-completion/generate-autojs6-indices-from-dts.mjs
node tools/ace-completion/generate-autojs6-indices.mjs
node tools/ace-completion/generate-dts.mjs
node tools/ace-completion/verify-autojs6-indices.mjs
node tools/ace-completion/test-completer.mjs
```

Generated outputs:

- `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_indices.js`
- `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/lib.autojs6.d.ts`
- `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/autojs6`
- `build/reports/ace-completion/autojs6-indices-report.md`

`autojs_indices.js` remains a compatibility fallback only. New broad AutoJs6 entries should come from the bundled selected `aj6-int-*.d.ts` files first, then from explicit source JSON fallback entries when the declaration parser cannot infer them safely.
