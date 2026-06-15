# AutoJs6 Editor Refactor Roadmap

This roadmap tracks the long-term editor refactor after the ACE integration. The
goal is to make editor behavior engine-neutral where possible, and keep native
`CodeEditText` workarounds out of ACE.

## Principles

- `EditorView` should orchestrate editor-page workflow, not own engine-specific
  rendering, IME, selection, gutter, or loading implementation details.
- Shared services may depend on `ScriptEditorEngine`, but must not depend on
  `CodeEditor`, `CodeEditText`, or ACE internals.
- Native-only behavior must stay in native implementation classes or policies.
- ACE-only behavior must stay in ACE implementation classes or policies.
- Future editor cores should only need `ScriptEditorEngine` plus policy/service
  adapters to join the common editor page.

## Completed Baseline

- [x] Add ACE engine behind `ScriptEditorEngine`.
- [x] Add ACE diagnostics, health monitor, and fallback to Native editor.
- [x] Add ACE font assets and font switching.
- [x] Add AutoJs6 declaration-backed ACE completions.
- [x] Fix ACE completion accept crash.
- [x] Fix ACE mobile action mode bridge crash.
- [x] Stabilize ACE soft-keyboard resize and heartbeat handling.
- [x] Prevent Native IME bottom-padding workaround from applying to ACE.

## Phase 1: Viewport and IME Isolation

- [x] Add `EditorViewportPolicy`.
- [x] Add `NativeEditorViewportPolicy`.
- [x] Add `AceEditorViewportPolicy`.
- [x] Make `EditorView` dispatch IME state to the current viewport policy.
- [x] Keep Native editor bottom padding and cursor-visible workaround native-only.
- [x] Keep ACE viewport handling limited to ACE resize/scroll settling.
- [x] Add focused regression checks for viewport-policy wiring.
- [x] Add optional debug diagnostics for active viewport policy and last IME event.

## Phase 2: Input Commands and Keyboard UI

- [ ] Make symbol bar and function keyboard depend only on editor commands.
- [ ] Keep `AutoCompletion(context, CodeEditText)` native-only.
- [ ] Confirm ACE completion uses only ACE completer/LSP paths.
- [ ] Remove direct `CodeEditText` access from common input UI setup.

## Phase 3: Loading and Large File Boundaries

- [ ] Split common file-read pipeline from engine-specific text load target.
- [ ] Keep `Editable.append`, loading gutter, and `CodeEditText` batching native-only.
- [ ] Define ACE small/medium/large text loading policy.
- [ ] Keep large-file Native route explicit and diagnosable.

## Phase 4: Editor Services

- [ ] Extract file open/save service.
- [ ] Extract draft restore/save service.
- [ ] Extract version history service.
- [ ] Extract run-text provider.
- [ ] Extract editor command dispatcher.
- [ ] Extract breakpoint model store.
- [ ] Extract theme/font application service.

## Phase 5: Native Editor Containment

- [ ] Move remaining `CodeEditor` / `CodeEditText` assumptions out of `EditorView`.
- [ ] Keep `requestRectangleOnScreen` native-only.
- [ ] Keep `LayoutHelper` and Android `Layout` cursor math native-only.
- [ ] Keep `TextViewUndoRedo` native-only.
- [ ] Keep native gutter/debug-line drawing native-only.

## Phase 6: ACE Hardening

- [ ] Add ACE viewport diagnostics snapshot fields for policy events.
- [ ] Add ACE first-paint and gutter stability smoke checks.
- [ ] Add ACE IME show/hide manual test checklist.
- [ ] Review whether ACE needs an explicit clean-host layout experiment flag.

## Acceptance Targets

- `EditorView` has no direct `CodeEditText` dependency.
- `requestRectangleOnScreen` appears only in native editor code.
- Native editor behavior remains unchanged after each extraction.
- ACE does not receive Native bottom-padding, `TextView` layout, or native
  cursor-visibility workarounds.
- A third editor core can be integrated by implementing `ScriptEditorEngine` and
  the required policies/adapters.

## Verification Commands

- `node tools/editor-refactor/check-editor-boundaries.mjs`
- `node tools/ace-completion/test-completer.mjs`
- `.\gradlew.bat :app:compileAppDebugKotlin`
- `.\gradlew.bat :app:mergeAppDebugAssets`
