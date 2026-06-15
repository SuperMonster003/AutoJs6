# VisualEditorAce Investigation

This note records the Stage 3 v2 investigation for the Pro-layout
`VisualEditorAce` path. It is intentionally not an implementation plan for the
current release branch.

## Current Repository State

- No `VisualEditorAce` or `VISUAL_ACE` implementation exists in the current
  source tree.
- `EditorEngineType` exposes only `NATIVE` and ordinary `ACE`.
- Editor settings arrays expose only `native` and `ace` preference values.
- The editor menu exposes only Native and ordinary ACE engine actions.
- `EditorEngineFactory` creates only `NativeCodeEditorEngine` or
  `AceScriptEditorEngine`.
- `EditorViewportPolicyFactory` has only Native and ordinary ACE viewport
  policy branches.

## Decision

Keep `VisualEditorAce` deferred. Do not add a `VISUAL_ACE` enum value, settings
entry, menu item, factory branch, or public UI route in this stage.

The ordinary ACE editor is still the integration surface that must stabilize
first. It now has CI checks, static completion, LSP fallback hooks, tooltip
fallback, diagnostics, TextMirror, and native fallback behavior, but the
physical-device keyboard and viewport matrix remains the release gate for
selection, CJK composition, repeated IME show/hide, rotation, and lower-viewport
tap anchors.

## Future Entry Conditions

Reopen `VisualEditorAce` only after all of these are true:

- The ordinary ACE keyboard and viewport matrix has recorded representative
  device results.
- Mobile selection, copy/paste, CJK composition, completion accept, and IME
  show/hide do not trigger ACE fallback on target devices.
- Asset ownership and redistribution rights for any Pro-layout files are clear.
- The target behavior is different enough from ordinary ACE to justify a
  separate engine route instead of an ordinary ACE option.
- The integration can be implemented through engine-neutral editor services,
  without adding Pro or visual-editor dependencies to common editor code.

## Required Shape For A Future Implementation

If it is reintroduced, `VisualEditorAce` should be modeled as a separate editor
engine, not as hidden branches inside ordinary ACE:

- `EditorEngineType.VISUAL_ACE` with a stable preference value, only when it is
  ready to be user-visible in an experimental setting.
- A separate `VisualAceScriptEditorEngine` or equivalent adapter implementing
  `ScriptEditorEngine`.
- A factory path with its own asset availability gate and fallback behavior.
- A viewport policy decision: either reuse `AceEditorViewportPolicy` only if the
  host layout remains clean-host, or add a narrow visual-specific policy.
- Diagnostics that identify the engine and asset revision separately from
  ordinary ACE.
- Release tests that prove Native remains default and ordinary ACE behavior is
  unchanged.

## Explicit Non-Goals For This Stage

- Do not add `VISUAL_ACE` to `EditorEngineType`.
- Do not add `key_editor_engine_visual_ace` or
  `entry_editor_engine_visual_ace`.
- Do not add `action_editor_engine_visual_ace`.
- Do not import Pro-layout assets into the ordinary ACE asset tree.
- Do not make VisualEditorAce part of the ACE keyboard and viewport release
  gate before the ordinary ACE matrix has real device results.

## Verification

`tools/editor-refactor/check-editor-boundaries.mjs` guards the current decision
by checking that no public VisualEditorAce enum, preference value, or menu entry
has been introduced early.
