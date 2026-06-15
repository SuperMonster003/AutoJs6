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

- Native remains the default editor in resources, preferences, enum fallback,
  and `EditorEngineFactory`.
- ACE remains on the clean-host viewport path through
  `AceEditorViewportPolicy`; no `key_ace_clean_host_layout` flag is exposed.
- Native-only boundary checks, ACE surface smoke checks, API model checks,
  completion checks, LSP client checks, and tooltip checks run through the ACE
  editor CI gate.
- ACE fallback still routes to Native without persisting the editor preference
  and uses the latest `textSnapshot`, which is TextMirror-backed for ACE.
- Renderer-gone and JS-init fatal debug hooks remain present for device/debug
  validation.
- Server-backed LSP transport remains unavailable in the alpha path; no
  WebSocket, worker, process, or runtime server startup is exposed.
- Public `VisualEditorAce` exposure remains deferred; no `VISUAL_ACE` enum,
  preference key, or menu item exists.

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
  and large-file Native routing.
- Continuous user-driven keyboard show/hide cycles must keep ACE active and
  avoid fallback.
- Fallback debug hooks must preserve recent small-file text through TextMirror.
- Renderer-gone and JS-init fatal debug hooks must still switch to Native and
  record useful diagnostics.

## Feature Freeze

Do not expand the ACE feature surface during this gate. Server-backed LSP,
public VisualEditorAce exposure, making ACE default, broad completion rewrites,
new tooltip dependencies, and new editor-engine abstractions stay deferred
unless a P0/P1 release-gate failure requires a targeted fix.

## Merge Note

The current branch temporarily renames the app package and label for parallel
device testing. Revert that temporary rename before final merge unless the merge
target explicitly wants the ACE test package.
