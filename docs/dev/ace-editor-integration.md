# ACE Editor Integration

This document tracks the current AutoJs6 ACE editor deployment state. ACE is the default editor when bundled assets are present. Native remains selectable and is still the fallback target.

## Stable Path

- ACE is the default value for `Editor engine`.
- Native remains available from settings and from the editor engine menu.
- ACE opens when bundled assets are present; if required assets are missing, the editor falls back to Native.
- Ordinary large files stay on ACE. The old progressive large-file path remains Native-only.
- Runtime ACE failures fall back to Native through the editor engine switch path.
- Fallback snapshots preserve text, dirty state, cursor, selection, breakpoints, debug line, read-only state, theme, and font size as far as each engine supports them.
- ACE text is mirrored on the Kotlin side through change deltas, so fallback can keep recent edits even when the WebView cannot be queried.
- ACE bundles ordinary Iosevka as its default WOFF2 font, with system `monospace` as the final fallback. Iosevka Slab and the other optional open-source fonts are installed on demand from the signed AutoJs6 font catalog; they are not APK assets.
- ACE font selection is available from settings and from the editor `More -> ACE editor font` menu.
- Font downloads are application-scoped: closing or recreating the selection dialog detaches its observer without stopping the transfer; only the row-level cancel action cancels the download.
- When a refreshed signed catalog revokes or replaces the selected download, every attached ACE editor re-resolves that selection and immediately falls back to bundled Iosevka when it is no longer authorized.
- The font dialog uses the same download icon as Plugin Center for missing fonts. Its title overflow menu can download every missing or outdated authorized font in one confirmed batch; batch downloads keep the current selection and use the downloader's two-worker limit.
- The signed catalog is published from the `ace-fonts` component of `SuperMonster003/AutoJs6-Shared-Assets`. Font Release tags and signing keys are scoped to that component so unrelated shared assets can evolve independently.
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
- Debug-forced Native routing shows a one-time Toast.
- Settings details state that ACE is the default editor and Native remains available as a fallback.

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
  input, rotation, fallback, ACE large-file direct loading, and bridge event storm
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
- Record Sony XQ-AT72 physical-device results in
  `docs/dev/ace-release-device-report-sony-xq-at72.md`.
- Track package/label and `version.properties` cleanup in
  `docs/dev/ace-release-publishing-cleanup.md`.
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
- Confirm `fonts/iosevka/Iosevka-Regular.woff2` and its `LICENSE.md` remain bundled and that no optional ACE WOFF2 files are present in the merged APK assets.
- Confirm the eight optional fonts, including Iosevka Slab, are listed by the signed catalog and that font bytes pass declared size, WOFF2 structure, and SHA-256 checks before installation.
- Confirm `Editor font -> More -> Download all` downloads every missing/updateable font, does not change the selected font, skips revoked/current/in-progress rows, and reports a single aggregate result without per-font Toast storms.
- Confirm a missing, invalid, rolled-back, or revoked remote catalog cannot authorize a new download or serve a revoked installed font; ordinary Iosevka must remain usable offline.
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
