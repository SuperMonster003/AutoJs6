# ACE Editor UX Roadmap

This roadmap tracks the post-release-gate ACE editor UX feedback without
replacing `Roadmap.md`.

## Current Principles

- Keep Native and ACE editor behavior isolated.
- Keep user-facing strings bilingual for English and Simplified Chinese.
- Keep ACE as the preferred editor while LSP remains disabled by default.
- Prefer instrumentation or static gates for every small UX fix; record manual
  device-only items explicitly.

## Gate UX-0: Settings, Localization, And Routing

- [x] Add Simplified Chinese translations for ACE/editor strings added during
  the ACE work.
- [x] Move `Editor engine` to the first item in the Code editor settings group.
- [x] Make ACE the default editor engine while preserving Native fallback.
- [x] Hide ACE-only settings when Native is selected.
- [x] Add ACE word wrap setting.
- [x] Add ACE line-number visibility setting.
- [x] Stop routing ordinary ACE large-file opens through the Native large-file
  pipeline.

Acceptance:

- [x] English and Simplified Chinese resources both contain every new or
  changed editor string.
- [x] Settings and editor menu agree on the selected engine.
- [x] LSP stays default-off.
- [x] Large files opened with ACE remain ACE unless an ACE failure or explicit
  debug hook requests Native fallback.

## Gate UX-1: Immediate ACE Surface Fixes

- [x] Increase dark-theme selection contrast.
- [x] Stabilize gutter width to avoid shifts around 99/100 and 999/1000.
- [ ] Allow clicking folded-code placeholders to unfold.
- [x] Preserve expected automatic cursor reveal on IME/panel changes, but avoid
  unsolicited pull-back while browsing selected code.

Acceptance:

- [x] Selection is visible in dark theme.
- [x] Gutter width does not shrink during the editing session and grows only
  when higher line-number digits become visible.
- [ ] Fold placeholders unfold without requiring the gutter triangle.
- [x] Scrolling selected text offscreen does not pull the editor back unless an
  explicit viewport event such as IME or panel resize requires it.

## Gate UX-2: Mobile Selection And Gesture Bugs

- [x] Diagnose short-menu/long-menu transitions on double-tap and long-press.
- [ ] Keep the floating action mode near the selection instead of jumping to the
  upper-left editor area.
- [x] Prevent transient short-menu flashes while dragging selection handles.
- [x] Prevent stale upper-left menus after scroll, app switch, or return.
- [x] Investigate Sony XQ-AT72 right-edge back gesture touch propagation into
  ACE long-press selection.
- [x] Add device notes for behavior that also reproduces in Auto.js Pro.

Acceptance:

- [ ] Double-tap, long-press, and selection-drag menus stay anchored and stable.
- [ ] Right-edge system-back gestures close IME or editor without triggering
  ACE long-press selection/haptic feedback on Sony XQ-AT72 when technically
  preventable from the app side.

Notes:

- AutoJs6 now treats Android `ActionMode` as the host-owned ACE selection menu
  and suppresses ACE's `.ace_mobile-menu` while that path is active. If ACE
  cannot measure the selection rect, it uses a non-zero viewport fallback anchor
  instead of the upper-left corner.
- Browsing code while a selection is active now suppresses both the Android
  `ActionMode` and ACE short menu until the selection changes, avoiding stale
  menus after scroll or app visibility transitions.
- Sony right-edge back gestures appear to leak the edge `ACTION_DOWN` into
  WebView/ACE before the system consumes the back gesture, matching the same
  behavior observed in Auto.js Pro. AutoJs6 now guards only the reported right
  system-gesture inset so ACE does not start long-press selection from that
  edge sequence; manual Sony verification is still required for the acceptance
  gate.

## Gate UX-3: Input, Keyboard, And Read-Only Mode

- [ ] Support IME spacebar cursor movement in ACE through soft-keyboard cursor
  key events.
- [x] Add ACE read-only mode, default off for every new editor session.
- [x] Strictly block text mutation paths while read-only is enabled.
- [x] Improve read-only scrolling performance where possible.

Acceptance:

- [ ] Soft-keyboard cursor key events update the ACE caret without text loss.
- [ ] Manual Gboard spacebar cursor movement verification on Sony XQ-DQ72.
- [x] Read-only mode blocks typing, paste, delete, replacement, formatting, and
  script-mutating editor actions.
- [x] Read-only state is not persisted after leaving the editor.

## Gate UX-4: Completion And Signature Help

- [x] Add ECMAScript built-in function parameter/signature help.
- [x] Prioritize common built-ins such as `Function.prototype.apply`.
- [x] Keep static completion working when LSP is disabled or unavailable.

Acceptance:

- [x] Built-in signature help appears without requiring LSP.
- [x] Tooltip/signature failures do not fallback the whole editor.

## Gate UX-5: ACE Shortcut Panel And Action Favorites

- [x] Build an ACE-specific two-row function keyboard layout.
- [x] Split the ACE shortcut panel into a fixed 2x4 left block and a
  vertically scrollable right symbol flow.
- [x] Keep Native and ACE on the same symbol profile store while giving ACE an
  independent built-in default symbol set.
- [x] Let ACE shortcut UP/DOWN/TAB control the completion popup highlight and
  acceptance when the popup is open.
- [x] Add ACE shortcut long-press navigation for file start/end, line
  start/end, host back, and previous cursor return.
- [x] Use text drawables or equivalent for `ESC` and `TAB`.
- [x] Reuse the unused right-side area after ACE completion replaces the old
  candidate strip.
- [x] Replace the Pro-style lower-left figure icon with a star action-favorites
  entry.
- [x] Track action history by recency and frequency.
- [x] Support pinned actions, manual reorder, left-swipe delete, right-swipe pin
  or unpin, and clear confirmation.
- [x] Long-press the star to repeat the last command, independent of favorites
  list state.

Acceptance:

- [x] ACE shortcut panel is visually coherent in light and dark themes.
- [x] Favorites ordering rules are deterministic.
- [x] Repeating the last command handles the no-last-command case with a toast.

## Gate UX-6: Pinch Zoom

- [x] Implement the existing View zoom strategy or replace it with a better
  continuous zoom model.
- [x] Keep the zoom center near the user's fingers.
- [x] Avoid moving the visible code unexpectedly during pinch gestures.

Acceptance:

- [x] Pinch zoom is continuous.
- [x] The content near the gesture center remains visible after zoom.
- [x] Native and ACE behavior are documented separately if implementation
  differs.

Notes:

- ACE `Scale view` is enabled in the pinch-to-zoom menu and uses session-local
  focus-preserving visual zoom. Native `Scale view` remains disabled as under
  development.

## Gate UX-7: Follow-Up UX Feedback

- [ ] Stabilize Gboard spacebar cursor direction in ACE without treating IME
  selection offsets as document-absolute offsets.
- [x] Keep double-tap and long-press host action modes anchored near the user's
  touch point without upper-left fallback flicker.
- [x] Preserve gutter fold toggle after folded-code placeholder experiments.
- [ ] Restore folded-code placeholder unfold support without breaking gutter
  fold toggles.
- [ ] Keep Sony right-edge back gesture protection while preserving normal tap,
  long-press, double-tap, and vertical scroll behavior outside the narrow
  system gesture edge.
- [x] Add word wrap support to the Native editor if technically feasible.
- [x] Add a wrap indentation style setting: continuation indent, default, and
  classic flush continuation.
- [x] Expand line-number visibility into independent gutter visibility controls
  for breakpoints, line numbers, and fold markers across ACE and Native where
  feasible.
- [x] Slightly enlarge ACE shortcut arrow icons to better match Auto.js Pro.
- [x] Make ACE shortcut long-press UP/DOWN repeat completion highlight movement
  while the completion popup is open instead of jumping to file start/end.
- [x] Record every cursor location change as a TAB long-press return point,
  independent of which command caused the jump.
- [x] Add a standalone Code editor settings page and an in-editor settings
  dialog using the same content model.
- [x] Reorganize global settings and editor overflow menus so top-level editor
  settings stay concise and advanced options move into the dedicated page or
  dialog.
- [x] Keep all code editor setting labels engine-neutral; show unsupported
  options disabled with a hint explaining that they are not available for the
  current engine.
- [x] Ensure Clear is undoable and enables the undo affordance after clearing
  non-empty editor text.

Acceptance:

- [ ] Sony XQ-DQ72 first, then XQ-AT72 fallback, confirms the four regression
  fixes for Gboard cursor motion, mobile selection menus, fold toggle/unfold,
  and right-edge back gesture behavior.
- [ ] Settings changes remain bilingual in English and Simplified Chinese.
- [ ] Native and ACE editor settings share engine-neutral service boundaries and
  keep unsupported engine-specific options disabled instead of hidden on the
  dedicated settings surface.

Notes:

- Breakpoint markers, line numbers, and fold markers now use shared editor
  display preferences. ACE applies all three through bridge runtime APIs and CSS
  fallbacks; Native applies breakpoint marker and line-number visibility, while
  fold markers remain disabled/no-op because the Native editor has no fold
  marker surface.
- The standalone Code editor settings page now exists and the global settings
  Code editor group has been reduced to engine, log behavior, auto-show log
  panel, and an All settings entry. The editor overflow menu now keeps only
  common editor actions at the top level and moves advanced editor preferences
  behind the Settings entry; the in-editor settings dialog embeds the same
  settings fragment as the standalone page.
- Settings labels now avoid exposing the ACE implementation name on the
  dedicated settings surface. Engine-specific preferences stay visible but
  disabled with a current-engine availability hint when they do not apply.
- Native editor now applies the shared word-wrap preference. Wrapped visual
  lines are mapped back to logical file lines for line numbers, breakpoint
  gutter taps, cursor positions, and jump operations.
- Wrap indentation is available as an ACE-backed display preference with
  continuation indent as the default, a default option for future ACE asset
  compatibility, and classic flush continuation. Native keeps the setting
  disabled with the current-engine unsupported hint because it has no matching
  visual-wrap indentation surface.
- ACE gutter fold widgets are explicitly exempted from the custom breakpoint
  tap zone, so native gutter folding can still collapse/expand code while the
  folded-code placeholder click capture remains available for unfolding.
- ACE now suppresses WebView absolute collapsed `InputConnection.setSelection`
  calls outside text mutation windows and treats Gboard spacebar cursor updates
  as relative deltas only, preventing local IME offsets from jumping the cursor
  back near the beginning of the document.
- ACE host action mode now rejects invalid tiny 0,0 content rects, preserving
  the current good anchor or falling back to a centered non-zero rect instead
  of letting Android place the selection menu at the upper-left corner.

## Known Unresolved Issues

These issues have had several implementation attempts but are not considered
resolved. Future work should start from a targeted repro/instrumentation pass
instead of another blind patch.

### Gboard Spacebar Cursor Movement

Current state:

- Long-pressing the IME spacebar can enter cursor movement mode, but ACE caret
  movement is still unstable. Reported symptoms include reversed movement
  direction, movement trapped in a small local range, cyclic jumps around that
  range, and increasing cursor movement distance after repeated left/right
  finger motion.

Attempts:

- Added soft-keyboard cursor-key interception for `KEYCODE_DPAD_LEFT` and
  `KEYCODE_DPAD_RIGHT`, guarded by `KeyEvent.FLAG_SOFT_KEYBOARD` and virtual
  keyboard device checks, then dispatched relative cursor movement to ACE via
  `moveCursor(delta)`.
- Wrapped WebView `InputConnection` with `InputConnectionWrapper` and
  intercepted collapsed `setSelection(start, end)` updates from IMEs. The first
  implementation treated the collapsed selection offset as the basis for a
  relative delta but still called `super.setSelection(start, end)`, which could
  let WebView interpret Gboard's local composing-buffer offset as a
  document-absolute offset.
- Added coalescing for pending IME cursor-selection deltas to reduce jitter and
  overly frequent bridge calls.
- Changed the collapsed `InputConnection.setSelection()` path to suppress
  WebView's absolute collapsed selection updates outside text mutation windows,
  so ACE only receives relative deltas. This prevented some document-start
  jumps in code, but did not close the device-reported instability.

Expected result:

- Gboard spacebar cursor movement should behave like a normal text editor:
  caret movement direction should match finger direction, movement should not
  be constrained to a small local range unless the document boundary is reached,
  and repeated left/right motion should not accumulate an exaggerated step
  size.

Additional notes:

- The core ambiguity is whether Gboard reports document-absolute selection
  offsets, WebView-local editable-buffer offsets, or composing-buffer-local
  offsets under different states. Future work should log raw `InputConnection`
  calls, key events, ACE selection offsets, composing text calls, and WebView
  selection state for the same gesture before changing movement math again.

### Fold Placeholder Click To Unfold

Current state:

- Gutter fold widgets can collapse and expand code again, but clicking the
  folded-code placeholder displayed in the editor content area, visually similar
  to `<->`, still does not unfold the code.

Attempts:

- Added DOM click capture on the ACE editor container for folded-code
  placeholders, targeting `.ace_fold` while explicitly excluding gutter cells
  and `.ace_fold-widget` gutter widgets.
- Added `expandFoldAt(row, column)` using several ACE session APIs and
  fallbacks: `session.getFoldAt(row, column, side)`,
  `session.getFoldLine(row)`, probing all columns on the row, and finally
  `session.unfold(row, true)`.
- Added an ACE `editor.on("click")` fallback to expand folds from the ACE event
  document position when the DOM target looked like a folded-code placeholder.
- Fixed a regression introduced by the placeholder work where gutter fold
  widgets could no longer collapse code. The current code exempts gutter
  `.ace_fold-widget` hits from the custom breakpoint tap zone and leaves gutter
  folding to ACE's native handler.

Expected result:

- Tapping the folded-code placeholder in the content area should expand that
  fold, while tapping gutter fold widgets should continue to collapse and
  expand code through ACE's native gutter folding behavior.

Additional notes:

- The remaining failure may be due to the rendered placeholder DOM target not
  matching `.ace_fold` on Android WebView, ACE translating the tap to a nearby
  text position instead of the folded range, or ACE 1.4.12 requiring a different
  internal fold lookup for placeholder widgets. Future work should inspect the
  actual DOM event target/class list and the fold data returned by
  `session.getFoldAt()` / `session.getFoldLine()` at the tap point.

### Sony XQ-AT72 Right-Edge Back Gesture

Current state:

- On Sony XQ-AT72, swiping inward from the right edge can still leak into ACE as
  a long-press/selection gesture, selecting a word or phrase and triggering the
  long-press haptic feedback. A previous regression where roughly the right
  20% of the editor became unresponsive has been fixed, but the original
  right-edge gesture leak remains unresolved.

Attempts:

- Added a native WebView touch guard for right-edge system-back gestures using
  `WindowInsets.systemGestureInsets.right` with a fallback minimum guard width.
  The guard suppresses selection action mode and long-press haptic feedback
  when it consumes the sequence.
- Added a JavaScript DOM capture guard that tracks right-edge touch sequences,
  waits for a left-swipe delta, calls `preventDefault()`,
  `stopPropagation()`, and `stopImmediatePropagation()`, and suppresses ACE
  selection menus during the guarded sequence.
- Tuned the right-edge guard width. A wider guard stopped the long-press leak
  more aggressively but caused normal tapping, long-pressing, double-tapping,
  and vertical scrolling to stop working in a large right-side editor area. The
  guard was narrowed back to preserve normal editor interaction outside the
  actual system gesture edge.
- Added haptic suppression after detected right-edge guard activity to avoid
  confusing long-press vibration when the app can identify the gesture.

Expected result:

- A right-edge inward system-back gesture should close the IME or editor
  without moving the caret, selecting text, opening a selection menu, or
  triggering long-press haptic feedback. Normal tap, long-press, double-tap, and
  vertical scroll behavior should remain intact outside the narrow system
  gesture edge.

Additional notes:

- Similar behavior was observed in Auto.js Pro, which suggests this may involve
  Sony's gesture navigation dispatch, Android WebView touch routing, ACE mobile
  selection handling, or a combination of them rather than only AutoJs6 code.
  Future work should capture full native `MotionEvent` sequences, JS
  `touchstart/touchmove/touchend` timing, system gesture inset values, and ACE
  long-press timing on Sony XQ-AT72 before further tuning.
