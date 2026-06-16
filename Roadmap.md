# AutoJs6 ACE Phase 9 Roadmap

This roadmap replaces the completed Stage 3 v2 implementation roadmap. Phase 9
is the release-candidate stabilization stage for the ordinary ACE experimental
editor: freeze the feature surface, validate the risky paths on real hardware,
fix only release-blocking issues, then prepare the branch for gray release.

## Current State

- ACE integration is complete as an explicit experimental editor engine.
- Native remains the default editor and Native fallback is implemented.
- Old Native `CodeEditor` / `CodeEditText` behavior is bounded by editor
  boundary checks.
- ACE clean-host viewport, first paint, IME state, event throttling,
  TextMirror, fallback classification, diagnostics, static completion, LSP alpha
  hooks, and tooltip fallback are implemented.
- Phase 8 automated and static gates are in place.
- LSP alpha hooks are present but default-off and server-backed transport is
  deferred.
- VisualEditorAce has been investigated but is intentionally not exposed.
- The remaining release risk is real-device validation.

## Phase 9 Goal

Move ACE from code-level readiness to device-level release readiness.

ACE can be considered a gray-release experimental editor only after a
representative physical-device run proves that IME, viewport, selection,
clipboard, CJK composition, completion accept, fallback, TextMirror,
diagnostics, large-file routing, and Native regression paths are stable enough
for opt-in users.

## Feature Freeze

Allowed work in Phase 9:

- P0/P1 device bug fixes found by the release gate.
- Diagnostics, test-plan, and report updates needed to explain failures.
- Release packaging cleanup, including temporary package-name handling.
- Documentation of known issues and release decisions.

Deferred until after Phase 9:

- Server-backed LSP transport.
- Public VisualEditorAce engine exposure.
- Making ACE the default editor.
- Broad completion-model rewrites.
- New tooltip rendering dependencies.
- New editor-engine abstractions without a release-gate failure requiring them.

## Verification Commands

- `node tools/ace-ci/check-ace-release-gate.mjs`
- `node tools/ace-ci/check-ace-editor-gate.mjs`
- `node tools/editor-refactor/check-editor-boundaries.mjs`
- `node tools/ace-hardening/check-editor-surface.mjs`
- `node tools/ace-completion/verify-autojs6-pro-only.mjs`
- `node tools/ace-completion/test-completer.mjs`
- `node tools/ace-lsp/test-lsp-client.mjs`
- `node tools/ace-tooltip/test-tooltip.mjs`
- `node tools/ace-completion/verify-autojs6-indices.mjs`
- `node tools/ace-completion/verify-autojs6-dts.mjs`
- `.\gradlew.bat :app:testAppDebugUnitTest --tests org.autojs.autojs.ui.edit.editor.ace.lsp.AceLspServerManagerTest`
- `.\gradlew.bat :app:compileAppDebugKotlin`
- `.\gradlew.bat :app:compileAppDebugJavaWithJavac`
- `.\gradlew.bat :app:mergeAppDebugAssets`
- `git diff --check`

## Release Gate Status

Automated/static gate:

- [x] Native remains the default editor.
- [x] ACE clean-host behavior remains the default ACE path.
- [x] Native-only boundary checks pass.
- [x] ACE first-paint and surface smoke checks pass.
- [x] ACE completion, LSP fallback, tooltip, and API-index checks pass.
- [x] ACE editor CI gate passes.
- [x] Server-backed LSP transport remains deferred until this gate is
  satisfied.
- [x] VisualEditorAce remains deferred until this gate is satisfied.

Manual device gate:

- [x] Sony XQ-AT72 device environment is recorded.
- [x] IME show/hide manual checklist passes on representative devices.
- [ ] Keyboard and viewport matrix passes on representative devices.
- [x] Continuous keyboard show/hide does not fallback ACE.
- [ ] Long-press, double-tap, copy, paste, and CJK composition remain usable.
- [x] Completion accept and tooltip fallback do not destabilize editing.
- [x] Rotation and Native/ACE switching preserve text and dirty state.
- [x] Fallback preserves recent text through TextMirror in device/debug-hook
  validation.
- [x] Renderer-gone and initialization fatal fallback still work in
  device/debug-hook validation.
- [ ] Native editor behavior has no regression from the ACE host work.

## Phase 9 Tasks

### Gate 0: Device Report Setup

- [x] Add `docs/dev/ace-release-device-report-sony-xq-at72.md`.
- [x] Record Sony XQ-AT72 device metadata:
  Android version, API level, WebView version, IME, theme, font, font scale,
  AutoJs6 build commit, test package, ACE state, and LSP state.

Acceptance:

- [x] The report identifies the exact tested build and environment.
- [x] Native default, explicit ACE opt-in, LSP default-off, and no
  VisualEditorAce user entry are confirmed.

### Gate 1: Automated Preflight

- [x] Run `node tools/ace-ci/check-ace-release-gate.mjs`.
- [x] Run `node tools/ace-ci/check-ace-editor-gate.mjs`.
- [x] Run `git diff --check`.
- [x] Record command results in the Sony XQ-AT72 report.

Acceptance:

- [x] All commands exit 0.
- [x] Any LF/CRLF warnings are recorded as non-blocking if no whitespace errors
  are present.

### Gate 2: First Paint And File Open

- [x] Open a normal `.js` file from the AutoJs6 file manager 10 times.
- [x] Repeat 5 times in dark theme.
- [x] Repeat 5 times in light theme.

Acceptance:

- [x] No old file-list frame flashes.
- [x] No white first frame or half-initialized ACE surface appears.
- [x] First-paint overlay and editor background remain coherent.

### Gate 3: IME Show/Hide And Tap Anchors

- [x] Tap top, middle, and lower visible code lines with keyboard hidden.
- [x] Show and hide the keyboard at least three rounds.
- [x] Check lower-viewport tap anchors near the keyboard edge.

Acceptance:

- [x] ACE does not white-screen, black-screen, jump unexpectedly, or fallback.
- [x] Caret lands on the intended line.
- [x] IME hide does not leave stale scroll or bottom padding.

### Gate 4: Keyboard Stress

- [x] Run the isolated ACE debug activity `IME stress` helper.
- [x] Manually repeat show/hide keyboard 20 times on a small file.
- [x] Repeat on a 100-line sample.
- [x] Repeat on a 1000-line sample when practical.

Acceptance:

- [x] ACE remains active.
- [x] No persistent flicker, WebView blanking, or heartbeat-triggered fallback.
- [x] Diagnostics do not show an unbounded bridge event storm.

### Gate 5: Mobile Selection, Clipboard, And CJK

- [x] Long-press a word.
- [ ] Drag selection handles.
- [x] Double-tap a word.
- [x] Copy selected text.
- [x] Paste single-line text.
- [x] Paste multi-line text.
- [x] Paste Chinese text, emoji, quotes, and backslashes.
- [ ] Type CJK composition text such as `toast("你好")`.
  Partial ADB-driven Gboard composition smoke is recorded; exact target text
  still needs human candidate selection.
- [x] Move the cursor and hide the keyboard while composition is uncommitted.

Acceptance:

- [ ] No lost or duplicated characters.
- [x] Copy/paste content is correct.
- [ ] TextMirror and saved text match the ACE display.
- [ ] Mobile selection paths do not fallback ACE.

### Gate 6: Completion, Tooltip, And LSP Default-Off

- [x] Trigger and accept static completions for `toast`, `app.`, `files.`,
  `images.`, and `device.`.
- [x] Confirm tooltip fallback does not load external resources.
- [x] Confirm LSP disabled returns no WebSocket or server transport.
- [x] Enable the LSP alpha flag and confirm no-server fallback remains static
  and feature-local.

Acceptance:

- [x] Completion accept inserts at the intended location.
- [x] Tooltip failures do not affect editing.
- [x] LSP failures degrade to static completion and do not fallback ACE.

### Gate 7: Rotation, Restore, And Engine Switching

- [x] Type unsaved text in ACE.
- [x] Show the keyboard.
- [x] Rotate to landscape and back to portrait.
- [x] Background and restore the app.
- [x] Save.
- [x] Switch to Native.
- [x] Switch back to ACE.

Acceptance:

- [x] Text and dirty state survive.
- [x] Cursor visibility remains acceptable.
- [x] No double-padding, tap offset, or duplicate fallback occurs.

### Gate 8: Fallback Hooks And TextMirror

- [x] Enter unsaved recent text before the ready-timeout fallback hook test.
- [x] Validate `forceReadyTimeout`.
- [x] Validate `forceJsInitThrow`.
- [x] Validate `forceRendererGoneFallback`.
- [x] Validate `forceAssetMissing`.

Acceptance:

- [x] ACE falls back to Native exactly once.
- [x] Recent text is preserved through TextMirror.
- [x] Dirty state is preserved.
- [x] Diagnostics include a clear failure type and runtime event history.
- [x] Renderer-gone fallback does not rely on querying a dead WebView.

Note: startup fatal hooks validate loaded-file text, Native fallback,
diagnostics, and event history because they fire before ACE is editable.
Unsaved recent text preservation is covered by the ready-timeout hook.

### Gate 9: Native Regression

- [ ] Set Native editor.
- [ ] Open and edit a file.
- [ ] Show and hide the keyboard.
- [ ] Save.
- [ ] Search and replace.
- [ ] Add and remove breakpoints.
- [ ] Open a large file.
- [ ] Run a script.

Acceptance:

- [ ] Native editor behavior is not degraded.
- [ ] Native IME workaround behavior remains Native-only.
- [ ] Native auto-completion and large-file loading still work.

### Gate 10: Publishing Cleanup

- [x] Check whether the temporary ACE test package and app label affect release
  builds.
- [ ] Restore the official application id and label before final merge, or limit
  the test package to debug/test-only scope.
- [x] Classify the `version.properties` change as release version bump, build
  auto-change, or accidental change.
- [x] Confirm ACE still is not the default editor.
- [x] Confirm LSP still is default-off.
- [x] Confirm VisualEditorAce still has no user entry.

Acceptance:

- [ ] No temporary package-name or label change leaks into an unintended release
  build.
- [ ] `version.properties` handling is documented or committed separately as
  appropriate.

### Gate 11: Final Release Decision

- [ ] Generate a final release-gate decision from the Sony XQ-AT72 report and
  automated gate output.
- [ ] List P0 blockers.
- [ ] List P1 known issues.
- [ ] List P2 follow-ups.
- [ ] Decide whether ACE can ship as an experimental editor.
- [ ] Decide that ACE must not become the default editor unless all manual
  device gates pass.

Acceptance:

- [ ] Any P0 blocker rejects the ACE release gate.
- [ ] P1-only issues can be accepted only with known-issue documentation.
- [ ] Missing keyboard/viewport device results prevent making ACE default.

## Blocker Classification

### P0 Blockers

- Normal input or save loses text.
- CJK composition loses or duplicates text.
- Continuous keyboard show/hide frequently falls back ACE.
- Tap anchors are visibly wrong.
- Fallback does not preserve recent text through TextMirror.
- Renderer-gone fallback crashes the app.
- Native editor behavior regresses.
- ACE becomes default.
- LSP becomes default-on or LSP failure falls back the whole editor.
- Diagnostics leak script text.

### P1 Known Issues

- Occasional minor first-frame flicker.
- Tooltip content is incomplete.
- Long-press selection experience is rough but copy/paste has a reliable
  alternative path.
- Large-file Native routing message is unclear.
- Rotation restores text but not exact scroll position.

### P2 Follow-Ups

- Richer LSP hover.
- Better declaration typing coverage.
- Completion ranking improvements.
- VisualEditorAce implementation.

## Release Decision Templates

Accepted:

```text
ACE experimental editor release gate accepted.

Native editor remains the default engine.
ACE can be enabled manually from settings.
LSP remains disabled by default.
VisualEditorAce remains unavailable.
```

Conditionally accepted:

```text
ACE experimental editor release gate conditionally accepted.

ACE may be exposed as an experimental editor.
Native remains default.
Known issues must be documented in release notes.
ACE must not become the default editor.
```

Rejected:

```text
ACE release gate rejected.

ACE should remain hidden or debug-only until blockers are fixed.
```
