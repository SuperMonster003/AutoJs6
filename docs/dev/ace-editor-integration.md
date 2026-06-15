# ACE Editor Integration

This document tracks the current AutoJs6 ACE editor deployment state. Native remains the default editor; ACE is an explicit experimental option.

## Stable Path

- Native is still the default value for `Editor engine`.
- ACE opens only when the user selects `ACE editor (experimental)` and the bundled ACE assets are present.
- Large files route back to Native under the current 512 KiB threshold policy.
- Runtime ACE failures fall back to Native through the editor engine switch path.
- Fallback snapshots preserve text, dirty state, cursor, selection, breakpoints, debug line, read-only state, theme, and font size as far as each engine supports them.
- ACE text is mirrored on the Kotlin side through change deltas, so fallback can keep recent edits even when the WebView cannot be queried.
- ACE uses bundled JetBrains Mono WOFF2 assets by default. Source Code Pro is bundled as the second editor font, and system `monospace` remains the final fallback.
- ACE font selection is available from settings and from the editor `More -> ACE editor font` menu.
- AutoJs6 static completion is generated from bundled selected `@sm003/autojs6-dts` declaration files under `autojs6/types/autojs6`, then emitted through `tools/ace-completion/autojs6_indices.source.json`; the legacy `autojs_indices.js` remains only as a compatibility fallback.
- ACE LSP is an experimental setting and is disabled by default. The stable editor path does not load `lsp.js` or create a WebSocket.

## Diagnostics

`AceDiagnostics` exports a plain-text snapshot for issue reports. The editor menu entry is:

```text
More -> Copy ACE diagnostics
```

The debug activity also exposes a `Copy ACE diagnostics` button for isolated WebView validation.

The snapshot includes:

- engine type
- ACE health state
- last failure type and message
- asset URL
- WebView package version
- Android version
- LSP enabled/state fields
- file size and large-file policy
- TextMirror revision
- ready/state/heartbeat timestamps
- viewport policy, host IME event, and ACE resize schedule/dispatch/complete fields

When ACE falls back to Native, `EditorView` keeps the last ACE diagnostics snapshot so the menu can still copy the failed ACE state after the Native editor is already active.

## UX Rules

- First manual Native/ACE switch shows that undo/redo history is not preserved across engines.
- ACE fallback shows a one-time Toast and points users to diagnostics.
- Large-file auto-Native routing shows a one-time Toast.
- Settings details state that ACE is experimental and Native remains the default.

## Release Gate

Before ACE is presented as a gray-release experimental editor:

- Run `node tools/ace-completion/verify-autojs6-indices.mjs`.
- Run `node tools/ace-completion/test-completer.mjs`.
- Run `node tools/ace-hardening/check-editor-surface.mjs`.
- Run the manual cases in `docs/dev/ace-editor-test-plan.md`.
- Verify asset-missing, ready-timeout, JS-init, renderer-gone, and large-file debug hooks.
- Verify recent small-file input survives fallback through TextMirror.
- Copy diagnostics from both an active ACE editor and after ACE fallback to Native.
- Confirm `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/lib.autojs6.d.ts` is generated from the same completion source.
- Confirm selected AutoJs6 declaration sources remain bundled under `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/autojs6`.
- Confirm `docs/dev/ace-lsp-design.md` still marks server-backed LSP as experimental and default-off.
- Confirm JetBrains Mono notice files remain bundled under `fonts/jetbrains-mono`.
- Confirm Source Code Pro `LICENSE.md` remains bundled under `fonts/source-code-pro`.
- Confirm AutoJs6 TypeScript Declarations `LICENSE` remains bundled under `autojs6/types/autojs6`.
- Confirm no font files with unclear redistribution rights exist under the ACE asset tree.
- Confirm `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/editor/ace-builds-1.4.12/LICENSE` are present.

## Deferred Work

- VisualEditorAce from the Pro layout remains deferred. Do not add an exposed `VISUAL_ACE` enum or UI entry until the ordinary ACE editor is stable.
- LSP remains a separate experimental path. The diagnostics schema and feature flag are in place, but server startup and WebSocket transport are deferred.
