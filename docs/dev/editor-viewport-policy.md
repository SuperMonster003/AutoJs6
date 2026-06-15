# Editor Viewport Policy Decision

This document records the Stage 3 v2 viewport boundary for Native and ACE.
The goal is to keep the host layout predictable while avoiding Native
`CodeEditText` IME workarounds on ACE.

## Decision

The existing simple `EditorViewportPolicy` contract is enough for the current
stage. It should not grow `attach`, `detach`, IME animation start/end, or host
callbacks until there is a real second host implementation or a concrete engine
need that cannot be represented by `onImeInsetsChanged`.

Do not add `EditorViewportHost` in this stage. A host object would be useful
only if multiple policy implementations need shared lifecycle services. Today,
`EditorView` already owns the Android `WindowInsets` listener, converts it into
neutral IME state, and delegates the engine-specific behavior.

## Current Contract

`EditorView` owns:

- Android `WindowInsets` observation.
- IME visible and bottom-inset calculation.
- Last host IME event timestamps for diagnostics.
- Calling `mViewportPolicy.onImeInsetsChanged(imeVisible, targetBottom)`.

`EditorViewportPolicyFactory` owns:

- Native routing to `NativeEditorViewportPolicy`.
- ACE routing to `AceEditorViewportPolicy`.

`NativeEditorViewportPolicy` owns:

- Applying Native bottom padding.
- Calling `editor.onImeInsetsChanged`.
- Posting `editor.ensureCursorVisibleIfPossible()` while IME is visible.

`AceEditorViewportPolicy` owns:

- Clearing residual bottom padding to zero.
- Calling `editor.onImeInsetsChanged`.
- Keeping ACE resize and scroll settling inside the ACE engine path.

## ACE Clean-Host Rules

ACE policy must not use:

- Native bottom padding as an IME workaround.
- Android `TextView` `Layout` cursor math.
- `requestRectangleOnScreen`.
- `CodeEditText` or `CodeEditor`.
- Native cursor-visible scheduling.

ACE diagnostics must identify the active policy with:

```text
viewportPolicy=AceEditorViewportPolicy
```

The diagnostics snapshot also records the last viewport-policy IME event:

- `lastViewportPolicyEvent`
- `lastViewportPolicyEventAtUptimeMillis`
- `lastViewportPolicyImeVisible`
- `lastViewportPolicyBottomInset`

## When To Revisit

Consider extending the interface only when one of these is true:

- A second ACE host layout exists and must be compared with the clean host.
- Android IME animation start/end produces a real bug that cannot be handled by
  the ACE engine resize debounce.
- Native and ACE both need a shared host service that cannot stay in
  `EditorView`.
- A third editor core needs lifecycle callbacks beyond neutral IME geometry.

Until then, keeping the policy narrow is the safer default: it minimizes the
surface where Native-only workarounds can leak into ACE.

## Acceptance

- `EditorView` continues to avoid direct `CodeEditText` access.
- `requestRectangleOnScreen` remains Native-only.
- ACE policy remains limited to residual padding cleanup and ACE resize/scroll
  delegation.
- The clean-host no-flag decision stays documented.
- ACE diagnostics identify `viewportPolicy=AceEditorViewportPolicy`.
