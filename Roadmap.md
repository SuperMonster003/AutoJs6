# AutoJs6 ACE Stage 3 v2 Roadmap

This roadmap replaces the completed editor-refactor roadmap and tracks the next
stage of ACE work. Stage 3 v2 is stability-first: cleanly isolate old Native
`CodeEditText` behavior, keep ACE in a predictable host layout, harden keyboard
and fallback behavior, then resume CI, API-index, and LSP work.

## Goals

- Keep Native editor behavior stable while ACE remains experimental.
- Make Native-only editor code visibly bounded and mechanically guarded.
- Keep ACE free from Native IME padding, Android `TextView` cursor math, and
  `requestRectangleOnScreen` workarounds.
- Make ACE soft-keyboard show/hide, first paint, completion accept, selection,
  and fallback behavior repeatable and diagnosable.
- Defer LSP alpha, tooltip/markdown enhancements, and VisualEditorAce until the
  ordinary ACE editor passes keyboard/viewport release gates.

## Current Baseline

- [x] ACE engine exists behind `ScriptEditorEngine`.
- [x] Native remains the default editor; ACE is explicit and experimental.
- [x] ACE diagnostics, health monitor, TextMirror, and Native fallback exist.
- [x] JetBrains Mono and Source Code Pro font assets are bundled.
- [x] AutoJs6 declaration-backed static completion is bundled.
- [x] Completion accept crash and mobile action-mode bridge crash are fixed.
- [x] `EditorViewportPolicy`, `NativeEditorViewportPolicy`, and
  `AceEditorViewportPolicy` exist.
- [x] ACE uses the clean-host viewport policy by default; no separate
  `key_ace_clean_host_layout` flag is currently needed.
- [x] ACE first-paint overlay, initial background, and surface smoke checks
  exist.
- [x] Symbol/function keyboard command paths and large-file loading boundaries
  are already partially engine-neutral.
- [x] `tools/editor-refactor/check-editor-boundaries.mjs` guards the current
  boundaries.
- [x] `tools/ace-ci/check-ace-editor-gate.mjs` runs the local/CI ACE editor
  gate.
- [x] `docs/dev/ace-editor-test-plan.md` contains the IME show/hide manual
  checklist.
- [x] `docs/dev/ace-keyboard-viewport-test-plan.md` contains the dedicated
  keyboard and viewport release matrix.

## Phase 1: Native-Only Audit Documents

- [x] Add `docs/dev/editor-native-only-audit.md`.
- [x] Classify Native-only dependencies:
  `CodeEditor`, `CodeEditText`, `TextViewUndoRedo`, `HVScrollView`,
  `JavaScriptHighlighter`, `LayoutHelper`, Android `Layout`,
  `Editable.append`, `requestRectangleOnScreen`, `selectionStart`,
  `selectionEnd`, `setLoadingText`, `setLoadingGutterDigits`,
  `setRedoUndoEnabled`, and `AutoCompletion(context, CodeEditText)`.
- [x] Add `docs/dev/editor-engine-neutral-services.md`.
- [x] Document engine-neutral service owners and allowed dependencies.
- [x] Extend boundary checks to cover the audit lists.

Acceptance:

- [x] Native-only APIs are categorized as Native-only, per-engine interface,
  public service, or removed-from-ACE.
- [x] Common editor services are documented as depending only on
  `ScriptEditorEngine` and neutral editor models.

## Phase 2: Viewport Policy Deepening

- [x] Decide whether the existing simple `EditorViewportPolicy` is enough or
  should grow `attach`, `detach`, IME animation start/end, and host callbacks.
- [x] If needed, add `EditorViewportHost` without moving ACE back onto Native
  workaround paths.
- [x] Keep Native bottom padding and cursor-visible logic Native-only.
- [x] Keep ACE policy limited to residual padding cleanup and ACE resize/scroll
  settling.
- [x] Keep the clean-host no-flag decision documented unless a real second host
  implementation appears.

Acceptance:

- [x] `EditorView` continues to avoid direct `CodeEditText` access.
- [x] `requestRectangleOnScreen` remains Native-only.
- [x] ACE diagnostics identify `viewportPolicy=AceEditorViewportPolicy`.

## Phase 3: IME State Machine

- [x] Add `AceImeState`.
- [x] Add `AceImeController`.
- [x] Make `showSoftInput` / `hideSoftInput` explicitly idempotent.
- [x] Coalesce duplicate IME requests inside the existing 150 ms window.
- [x] Delay or ignore reverse show/hide requests during transitions.
- [x] Record `lastImeState`, `lastImeHeight`,
  `lastImeTransitionStartedAtUptimeMillis`,
  `lastImeTransitionEndedAtUptimeMillis`, and ignored request reasons in ACE
  diagnostics.
- [x] Confirm cursor/selection changes do not directly trigger keyboard
  show/hide feedback loops.

Acceptance:

- [x] Fast repeated taps do not create an IME feedback loop.
- [x] Consecutive show/hide cycles keep ACE active and diagnostics explain the
  last IME transition.

## Phase 4: Fallback Classification And Event History

- [x] Add a bounded ACE runtime event history for the last 30 key events:
  IME show/hide, resize, focus, blur, heartbeat, evaluateJavascript,
  JS fatal, console error, renderer gone, and fallback.
- [x] Add `AceJsErrorClassifier`.
- [x] Treat resize, focus, and `scrollCursorIntoView` errors as recoverable
  after ACE is running.
- [x] Keep completion, LSP, tooltip, and markdown failures feature-local.
- [x] During IME transitions, report transient evaluateJavascript failures as
  degraded/retryable instead of immediate fallback.
- [x] Preserve immediate fallback for renderer-gone and initialization fatal
  failures.
- [x] Ensure fallback text prefers `AceTextMirror`.

Acceptance:

- [x] Repeated keyboard show/hide does not fallback from heartbeat or transient
  evaluateJavascript failures.
- [x] Forced renderer-gone and forced init fatal still fallback.
- [x] Diagnostics contain enough event history to explain the fallback decision.

## Phase 5: Bridge Event Throttling

- [x] Audit JS-to-Kotlin event frequency during IME animation.
- [x] Throttle cursor events to a bounded cadence.
- [x] Throttle selection/state events while preserving text deltas.
- [x] Keep `changeDelta` reliable and ordered.
- [x] Debounce UI menu/save-button refreshes triggered by high-frequency editor
  state changes.
- [x] Add smoke or diagnostic checks that expose event storms.

Acceptance:

- [x] Keyboard animation does not produce unbounded cursor/state event bursts.
- [x] Text edits remain lossless through TextMirror.

## Phase 6: Mobile Selection CSS Review

- [x] Audit `user-select`, `.ace_mobile-menu`, selection handles, and
  action-mode behavior on Android WebView.
- [ ] Test long-press, double-tap, copy, paste, CJK composition, completion
  accept, and IME show/hide with mobile selection paths.
- [x] Keep breakpoint and debug-line styles stable.
- [x] If selection suppression is needed, scope it narrowly instead of hiding
  all mobile behavior.

Acceptance:

- [ ] Long-press and double-tap do not fallback ACE.
- [ ] Copy/paste and CJK input remain usable.
- [ ] Touch location, selection, and gutter alignment remain coherent.

## Phase 7: Keyboard And Viewport Test Matrix

- [x] Add `docs/dev/ace-keyboard-viewport-test-plan.md` or extend the existing
  ACE test plan with a dedicated matrix section.
- [x] Add a debug action or debug-only helper to run repeated keyboard
  show/hide stress when feasible. The isolated ACE debug activity now has an
  IME stress helper; real tap-anchor validation still belongs to the device
  matrix.
- [x] Add diagnostics dump coverage for event history once Phase 4 lands.
- [x] Cover file-manager open first paint, top/middle/bottom tap anchors,
  completion popup accept, long-press selection, CJK input, rotation, fallback,
  and large-file Native routing.
- [x] Update release gates so LSP alpha is not the primary target until the
  keyboard/viewport matrix is stable.

Acceptance:

- [x] The original flicker, anchor drift, and keyboard fallback issues are
  reproducible as test cases.
- [x] The same matrix can be rerun after WebView or Android version changes.

## Phase 8: Resume Deferred Advanced Work

- [x] Instrumentation smoke tests for ACE basics.
- [x] CI gate for editor boundary and ACE hardening scripts.
- [ ] AutoJs6 API index intermediate model improvements.
- [ ] Pro-only API detection.
- [ ] Declaration and completion quality improvements.
- [ ] LSP server manager alpha.
- [ ] LSP completion and hover.
- [ ] Optional tooltip/markdown loading.
- [ ] VisualEditorAce investigation only after ordinary ACE is stable.

## Verification Commands

- `node tools/ace-ci/check-ace-editor-gate.mjs`
- `node tools/editor-refactor/check-editor-boundaries.mjs`
- `node tools/ace-hardening/check-editor-surface.mjs`
- `node tools/ace-completion/test-completer.mjs`
- `node tools/ace-completion/verify-autojs6-indices.mjs`
- `.\gradlew.bat :app:compileAppDebugKotlin`
- `.\gradlew.bat :app:compileAppDebugJavaWithJavac`
- `.\gradlew.bat :app:mergeAppDebugAssets`

## Release Gate

- [ ] Native remains the default editor.
- [ ] ACE clean-host behavior remains the default ACE path.
- [ ] Native-only boundary checks pass.
- [ ] ACE first-paint and surface smoke checks pass.
- [ ] ACE completion checks pass.
- [ ] ACE editor CI gate passes.
- [ ] IME show/hide manual checklist passes on representative devices.
- [ ] Keyboard and viewport matrix passes on representative devices.
- [ ] Continuous keyboard show/hide does not fallback ACE.
- [ ] Fallback preserves recent text through TextMirror.
- [ ] Renderer-gone and initialization fatal fallback still work.
- [ ] LSP and VisualEditorAce remain deferred until this gate is satisfied.
