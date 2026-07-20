# ACE Release Gate Status

This document tracks the release-gate split between automated checks and
representative-device validation. Run the automated gate with:

```powershell
node tools/ace-ci/check-ace-release-gate.mjs
```

The script first runs the existing ACE editor CI gate, then verifies release
invariants that are easy to regress during later ACE work.

Phase 9 is the active release-candidate stabilization phase. The release device
report for the preferred representative device is:

```text
docs/dev/ace-release-device-report-sony-xq-at72.md
```

Publishing cleanup audit:

```text
docs/dev/ace-release-publishing-cleanup.md
```

## Automated Gate

- ACE is the default editor in resources, preferences, enum fallback, and
  `EditorEngineFactory`; Native remains selectable and is still the fallback
  target.
- ACE remains on the clean-host viewport path through
  `AceEditorViewportPolicy`; no `key_ace_clean_host_layout` flag is exposed.
- Native-only boundary checks, ACE surface smoke checks, API model checks,
  completion checks, LSP client checks, and tooltip checks run through the ACE
  editor CI gate.
- LSP smart completion is default-on in both the resource and preference
  fallback, preserves an explicit user opt-out, and reports
  `manager=native-local` for the bundled TypeScript language service.
- The real bundled TypeScript runtime is covered for semantic completion,
  hover/JSDoc, diagnostics, signature help, hot enable/disable, retry/disposal,
  the 512 KiB degradation boundary, and the 300-item completion cap.
- The released semantic scope is the TypeScript 4.2.4 single-file baseline;
  project module resolution and NodeNext `.mts`/`.cts` semantics remain
  deferred, and misleading module-not-found annotations are suppressed.
- ACE fallback still routes to Native without persisting the editor preference
  and uses the latest `textSnapshot`, which is TextMirror-backed for ACE.
- Renderer-gone and JS-init fatal debug hooks remain present for device/debug
  validation.
- External/server-backed LSP transport remains outside the released local
  feature; no WebSocket, worker, process, or runtime server startup is exposed.
- Public `VisualEditorAce` exposure remains deferred; no `VISUAL_ACE` enum,
  preference key, or menu item exists.

### LSP promotion result - 2026-07-20

The formal default-on LSP scope passed the full automated release gate and two
targeted instrumentation tests on both Sony XQ-AT72 (Android 12/API 31,
WebView 145.0.7632.120) and Sony XQ-DQ72 (Android 13/API 33,
WebView 150.0.7871.47). The tests covered a fresh default, same-page disable and
re-enable, TypeScript provider readiness, semantic diagnostics, JSDoc hover,
signature help, and static fallback. See the dated addendum in
`docs/dev/ace-release-device-report-sony-xq-at72.md`.

## Manual Gate

These items need real-device validation and must not be marked complete from
static checks alone. The preferred representative device for this branch is
Sony XQ-AT72.

- Record the device environment and every result in
  `docs/dev/ace-release-device-report-sony-xq-at72.md`.
- Phase 6 mobile selection paths: long-press, double-tap, copy, paste, CJK
  composition, completion accept, and IME show/hide.
- Keyboard and viewport matrix: file-manager open first paint, top/middle/bottom
  tap anchors, lower-screen cursor anchors, rotation, completion popup accept,
  ACE large-file direct loading, and Native large-file regression coverage.
- Continuous user-driven keyboard show/hide cycles must keep ACE active and
  avoid fallback.
- Fallback debug hooks must preserve recent small-file text through TextMirror.
- Renderer-gone and JS-init fatal debug hooks must still switch to Native and
  record useful diagnostics.
- With a fresh LSP preference, confirm first-paint warm-up reaches
  `typescript-language-service` providers; then explicitly disable and
  re-enable it in the same editor page and confirm static fallback, disposal,
  and hot recovery without a WebView reload or network request.
- Confirm a document beyond the 512 KiB semantic limit remains editable and
  reports `document-too-large` rather than stalling the page.

## Feature Freeze

Do not expand the ACE feature surface during this gate. Server-backed LSP,
public VisualEditorAce exposure, broad completion rewrites, new tooltip
dependencies, and new editor-engine abstractions stay deferred
unless a P0/P1 release-gate failure requires a targeted fix.

## Merge Note

The current branch temporarily renames the app package and label for parallel
device testing. Revert that temporary rename before final merge unless the merge
target explicitly wants the ACE test package.
