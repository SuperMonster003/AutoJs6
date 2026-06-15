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
- ACE LSP is an experimental setting and is disabled by default. The native
  alpha manager exposes deterministic no-server options when enabled. The ACE
  LSP client facade exposes completion and hover hooks, but default-off and
  no-server paths use the static completion index as fallback and do not load
  `lsp.js`, create a WebSocket, or start a server.
- ACE hover tooltips are optional at the bridge boundary. The tooltip layer
  creates DOM lazily, uses the local safe markdown subset renderer, escapes raw
  HTML, and does not fetch external markdown resources.

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
- LSP enabled/state, transport, URI, fallback, and server availability fields
- file size and large-file policy
- TextMirror revision
- ready/state/heartbeat timestamps
- IME state, height, transition timestamps, and ignored IME request reason
- viewport policy, host IME event, and ACE resize schedule/dispatch/complete fields
- bridge event window counters for spotting JS-to-Kotlin event storms
- runtime event history for the last 30 ACE keyboard, resize, heartbeat,
  JavaScript, console, renderer, and fallback events

When ACE falls back to Native, `EditorView` keeps the last ACE diagnostics snapshot so the menu can still copy the failed ACE state after the Native editor is already active.

## UX Rules

- First manual Native/ACE switch shows that undo/redo history is not preserved across engines.
- ACE fallback shows a one-time Toast and points users to diagnostics.
- Large-file auto-Native routing shows a one-time Toast.
- Settings details state that ACE is experimental and Native remains the default.

## Clean Host Layout Decision

ACE does not need a separate clean-host experiment flag at this stage. The ACE path is already clean-host by default:

- `EditorViewportPolicyFactory` routes ACE to `AceEditorViewportPolicy`, not `NativeEditorViewportPolicy`.
- `AceEditorViewportPolicy` clears any residual bottom padding and delegates IME geometry to the ACE engine.
- Native bottom padding, Android `TextView` cursor math, and `requestRectangleOnScreen` stay native-only.
- ACE resize behavior is observable through diagnostics and covered by the IME show/hide checklist.
- ACE bridge cursor, text, and state events are throttled so keyboard animation
  does not create unbounded JS-to-Kotlin callback bursts.
- Dedicated keyboard and viewport coverage lives in
  `docs/dev/ace-keyboard-viewport-test-plan.md`, including first paint, tap
  anchors, repeated IME stress, completion accept, long-press selection, CJK
  input, rotation, fallback, large-file routing, and bridge event storm
  diagnostics.
- The isolated ACE debug activity includes an `IME stress` helper for quick
  repeated show/hide diagnostics before running the full physical-device matrix.

Do not add `key_ace_clean_host_layout` unless there is a concrete second ACE host layout to compare. A flag without another implementation would add state, test matrix size, and ambiguous diagnostics without improving the stable path.

See `docs/dev/editor-viewport-policy.md` for the Stage 3 v2 decision to keep
`EditorViewportPolicy` narrow and avoid adding `EditorViewportHost` until there
is a real second host or lifecycle requirement.

## Release Gate

Before ACE is presented as a gray-release experimental editor:

- Run `node tools/ace-ci/check-ace-release-gate.mjs`.
- Run `node tools/ace-ci/check-ace-editor-gate.mjs`.
- Run `node tools/ace-completion/verify-autojs6-api-model.mjs`.
- Run `node tools/ace-completion/verify-autojs6-pro-only.mjs`.
- Run `node tools/ace-completion/verify-autojs6-indices.mjs`.
- Run `node tools/ace-completion/verify-autojs6-dts.mjs`.
- Run `node tools/ace-completion/test-completer.mjs`.
- Run `node tools/ace-lsp/test-lsp-client.mjs`.
- Run `node tools/ace-tooltip/test-tooltip.mjs`.
- Run `node tools/ace-hardening/check-editor-surface.mjs`.
- Run `.\gradlew.bat :app:testAppDebugUnitTest --tests org.autojs.autojs.ui.edit.editor.ace.lsp.AceLspServerManagerTest`.
- Run the manual cases in `docs/dev/ace-editor-test-plan.md`.
- Run the `IME Show/Hide Manual Checklist` in `docs/dev/ace-editor-test-plan.md`.
- Run the dedicated keyboard and viewport matrix in
  `docs/dev/ace-keyboard-viewport-test-plan.md`.
- Track the automated/manual split in
  `docs/dev/ace-release-gate-status.md`.
- Use the isolated ACE debug activity `IME stress` helper as a quick preflight;
  do not treat it as a replacement for physical tap-anchor and CJK validation.
- Verify asset-missing, ready-timeout, JS-init, renderer-gone, and large-file debug hooks.
- Verify recent small-file input survives fallback through TextMirror.
- Copy diagnostics from both an active ACE editor and after ACE fallback to Native.
- Confirm diagnostics include runtime event history and bridge event window
  counters for keyboard and viewport failures.
- Confirm `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/lib.autojs6.d.ts` is generated from the same completion source.
- Confirm selected AutoJs6 declaration sources remain bundled under `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/types/autojs6`.
- Confirm `docs/dev/ace-lsp-design.md` still marks server-backed LSP as experimental and default-off.
- Confirm JetBrains Mono notice files remain bundled under `fonts/jetbrains-mono`.
- Confirm Source Code Pro `LICENSE.md` remains bundled under `fonts/source-code-pro`.
- Confirm AutoJs6 TypeScript Declarations `LICENSE` remains bundled under `autojs6/types/autojs6`.
- Confirm no font files with unclear redistribution rights exist under the ACE asset tree.
- Confirm `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/editor/ace-builds-1.4.12/LICENSE` are present.

## Deferred Work

- VisualEditorAce from the Pro layout remains deferred. The Stage 3 v2
  investigation is recorded in
  `docs/dev/visual-editor-ace-investigation.md`; do not add an exposed
  `VISUAL_ACE` enum or UI entry until the ordinary ACE editor has recorded
  keyboard and viewport matrix results on representative devices.
- LSP remains a separate experimental path. The native alpha manager, diagnostics
  schema, feature flag, and client-side completion/hover fallback hooks are in
  place, but server startup and WebSocket transport are deferred.
- Server-backed LSP transport and public VisualEditorAce engine exposure are not
  primary targets until the keyboard and viewport matrix is stable on
  representative devices.
