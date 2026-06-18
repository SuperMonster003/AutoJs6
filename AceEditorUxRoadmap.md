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
- [x] Add dynamic/fixed gutter width modes, keeping dynamic width as the
  default while preserving an optional stable fixed-width mode.
- [x] Allow clicking folded-code placeholders to unfold.
- [x] Preserve expected automatic cursor reveal on IME/panel changes, but avoid
  unsolicited pull-back while browsing selected code.

Acceptance:

- [x] Selection is visible in dark theme.
- [x] Dynamic gutter width remains the default and follows the currently
  visible line-number digits.
- [x] Fixed gutter width does not shrink during the editing session and grows
  only when higher line-number digits become visible.
- [x] Fold placeholders unfold without requiring the gutter triangle.
- [x] Scrolling selected text offscreen does not pull the editor back unless an
  explicit viewport event such as IME or panel resize requires it.

## Gate UX-2: Mobile Selection And Gesture Bugs

- [x] Diagnose short-menu/long-menu transitions on double-tap and long-press.
- [x] Keep the floating action mode near the selection instead of jumping to the
  upper-left editor area.
- [x] Prevent transient short-menu flashes while dragging selection handles.
- [x] Prevent stale upper-left menus after scroll, app switch, or return.
- [x] Investigate Sony XQ-AT72 right-edge back gesture touch propagation into
  ACE long-press selection.
- [x] Add device notes for behavior that also reproduces in Auto.js Pro.

Acceptance:

- [ ] Selection-drag menus stay anchored and stable after the user lifts their
  finger.
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
- [x] Add a curated ECMAScript built-in completion layer for common globals,
  constructors, and prototypes such as `Object.prototype.hasOwnProperty`.
- [x] Keep static completion working when LSP is disabled or unavailable.
- [x] Document the future Node.js type-completion plan for the Node engine
  branch without enabling Node globals in the Rhino/AutoJs6 editor profile.

Acceptance:

- [x] Built-in signature help appears without requiring LSP.
- [x] Built-in completion appears for common ECMAScript objects and prototypes
  without requiring LSP.
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
- [x] Generalize action history beyond shortcut-panel commands and record the
  top toolbar plus first batch of direct editor overflow menu actions.
- [x] Support pinned actions, manual reorder, left-swipe delete, right-swipe pin
  or unpin, and clear confirmation.
- [x] Long-press the star to repeat the last command, independent of favorites
  list state.
- [x] Persist manual action-favorite reorder for both pinned and unpinned
  entries after drag release.
- [ ] Record open-ended editor setting actions in action favorites, such as
  setting word wrap, editor engine, font, and font size.

Acceptance:

- [x] ACE shortcut panel is visually coherent in light and dark themes.
- [x] Favorites ordering rules are deterministic.
- [x] Repeating the last command handles the no-last-command case with a toast.

## Gate UX-6: Pinch Zoom

- [x] Implement the existing View zoom strategy or replace it with a better
  continuous zoom model.
- [ ] Keep the zoom center near the user's fingers.
- [ ] Avoid ACE click/caret coordinate drift after pinch gestures.
- [ ] Persist font-size changes made by pinch gestures.

Acceptance:

- [x] Pinch zoom is continuous.
- [ ] The content near the gesture center remains visible after zoom.
- [x] Native and ACE behavior are documented separately if implementation
  differs.

Notes:

- ACE `Scale view` is enabled and continuous, but the current anchor is still
  biased toward the visible top-left area, tap-to-caret coordinates drift after
  zoom, and gesture-changed font size is not yet persisted. Native `Scale view`
  remains disabled as under development.

## Gate UX-7: Follow-Up UX Feedback

- [ ] Stabilize Gboard spacebar cursor direction in ACE without treating IME
  selection offsets as document-absolute offsets.
- [x] Keep double-tap and long-press host action modes anchored near the user's
  touch point without upper-left fallback flicker.
- [x] Preserve gutter fold toggle after folded-code placeholder experiments.
- [x] Restore folded-code placeholder unfold support without breaking gutter
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
- [x] Use a hollow star shortcut icon and the supplied thumbtack icon for
  pinned editor actions.
- [x] Keep the latest unpinned action immediately below pinned favorites before
  falling back to frequency and recency ranking.
- [x] Extend action favorites beyond the shortcut panel to top-toolbar actions
  and directly replayable editor overflow actions.
- [x] Replay dialog-launch editor actions such as Find / Replace, Jump to line,
  build packaging, file details, and Java class search.
- [ ] Add saved parameter payloads for open-ended favorites such as remembered
  line numbers, search queries, build profiles, and Java class imports.
- [x] Add a standalone Code editor settings page and an in-editor settings
  dialog using the same content model.
- [x] Move the in-editor settings dialog to a fragment-safe MaterialDialog path.
- [x] Reorganize global settings and editor overflow menus so top-level editor
  settings stay concise and advanced options move into the dedicated page or
  dialog.
- [x] Keep all code editor setting labels engine-neutral; show unsupported
  options disabled with a hint explaining that they are not available for the
  current engine.
- [x] Ensure Clear is undoable and enables the undo affordance after clearing
  non-empty editor text.
- [x] Refresh redo state after undoing Clear, including ACE bridge state that
  arrives asynchronously.
- [x] Suppress transient long selection menus after text mutations such as
  soft-keyboard Backspace and Clear redo.
- [x] Extend session read-only mode to Native, suppress ACE read-only keyboard
  activation, and fully hide the shortcut/input bar while read-only is active.
- [x] Normalize user-visible editor engine names to Ace and JsDroidEditor.
- [x] Remove the duplicate Default wrap-indentation option from the visible
  chooser and rename classic flush continuation to flush continuation.
- [x] Show the ACE-specific default symbol set when opening Symbols settings
  while the selected editor engine is Ace.

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
- Gutter width now has a shared Dynamic/Fixed setting. Dynamic mode is the
  default and follows the currently visible line-number digits. Fixed mode keeps
  a stable session width, starts from the total digit count below 1000 lines or
  four digits at/above 1000 lines, and grows only when larger visible line
  numbers require it.
- When all supported gutter content is hidden, ACE disables its renderer gutter
  and Native drops its left padding to zero so the narrow empty gutter strip is
  not left behind.
- The standalone Code editor settings page now exists and the global settings
  Code editor group has been reduced to engine, log behavior, auto-show log
  panel, and an All settings entry. The editor overflow menu now keeps only
  common editor actions at the top level and moves advanced editor preferences
  behind the Settings entry; the in-editor settings dialog embeds the same
  settings fragment as the standalone page.
- A direct `MaterialDialog`-backed `DialogFragment` was reverted because
  AndroidX fills `DialogFragment` views through `Dialog.setContentView()`, and
  this MaterialDialog implementation rejects that API in favor of
  `Builder.customView(...)`.
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
- Read-only mode is now a session-level editor option for both Ace and
  JsDroidEditor. When enabled, text-mutating menu entries are hidden, the
  shortcut/input bar is removed from layout, and Ace suppresses soft-keyboard
  show requests while preserving selection/copy affordances.
- Action favorites now use a hollow star entry point and the supplied
  thumbtack icon for pinned actions. The latest unpinned action is ranked first
  below pinned entries, then the existing frequency/recency ranking applies.
- Action history now stores generic editor actions with mutation metadata, so
  the favorites dialog can replay shortcut-panel commands, toolbar commands,
  and the first batch of direct overflow-menu commands through the same
  ranking, pinning, deletion, and repeat-last-command flow.
- Dialog-launch editor actions now live on `EditorView`, letting overflow-menu
  clicks and action-favorite replays share the same Find / Replace, Jump to
  line, build packaging, file details, and Java class search paths.
- The in-editor settings dialog now uses a dedicated MaterialDialog host that
  accepts DialogFragment content through a custom view container, avoiding the
  unsupported `MaterialDialog.setContentView()` path.
- The visible wrap-indentation chooser now offers only continuation indent and
  flush continuation. The old `default` key remains accepted for persisted
  compatibility but is no longer offered as a separate option.
- ECMAScript completion now merges a local built-in layer into the AutoJs6
  indices, covering common globals, constructors, and prototypes such as
  `Object`, `Array`, `String`, `Math`, `JSON`, `Promise`,
  `Function.prototype`, and `Object.prototype.hasOwnProperty`. Unknown plain
  member contexts such as `obj.` fall back to `Object.prototype` suggestions
  while concrete AutoJs6 modules keep their indexed members first.
- Node.js type completion should stay engine-scoped. When the Node engine branch
  is ready, ingest the selected `@types/node` `.d.ts` set into a separate Node
  completion profile, gate it by editor/runtime engine, and avoid exposing Node
  globals such as `process`, `Buffer`, or `require` in the current Rhino/AutoJs6
  profile.

## Latest User Feedback Matrix

This matrix mirrors the latest numbered feedback so every item can be tracked
and updated alongside commits.

1. Localization
   - [x] English and Simplified Chinese coverage is complete for the current
     ACE/editor strings.

2. Gboard spacebar cursor movement
   - [ ] Keep cursor direction stable while sliding left and right.
   - [ ] Prevent the cursor from being trapped in a small local range near the
     beginning of the document.
   - [ ] Prevent repeated back-and-forth slides from accumulating abnormal
     movement step sizes.

3. Double-tap and long-press selection menus
   - [x] Suppress short-menu interference during double-tap and long-press.
   - [x] Keep double-tap and normal long-press menus near the selection instead
     of the upper-left editor area.
   - [ ] Keep the long menu visible after dragging a long-press selection range
     and lifting the finger.

4. Fold placeholder and gutter folding
   - [x] Preserve gutter fold-widget collapse/expand.
   - [x] Expand folded code by tapping the content placeholder.
   - [x] Avoid triggering long-press selection after placeholder unfold.

5. Selected range auto pull-back
   - [x] Avoid unsolicited selection reveal while browsing selected code.

6. Background contrast
   - [x] Improve dark-theme selection contrast.
   - [x] Improve ACE bracket-match marker contrast.

7. Large-file loading
   - [x] Keep large ordinary ACE opens on Ace instead of forcing JsDroidEditor.

8. Editor engine switching
   - [x] Support Ace and JsDroidEditor engine switching.

9. Sony XQ-AT72 right-edge back gesture
   - [x] Restore normal interaction in the right-side editor area after the
     earlier wide guard regression.
   - [ ] Prevent right-edge inward back gestures from leaking into ACE
     long-press selection and haptic feedback.

10. Gutter width
   - [x] Provide dynamic and fixed line-number gutter width modes.
   - [x] Keep JsDroidEditor on fixed gutter width.

11. Word wrap
   - [x] Support ACE word wrap and expose the setting.
   - [x] Disable unsupported JsDroidEditor word wrap UI and normalize its
     stored value.
   - [x] Keep visible wrap indentation options to continuation indent and flush
     continuation.

12. Gutter visibility
   - [x] Support line number visibility.
   - [x] Support independent breakpoint, line-number, and fold-marker
     visibility where each engine has a surface for it.
   - [x] Remove the empty gutter strip when all supported gutter content is
     hidden.

13. ECMAScript and Node.js completion
   - [x] Provide signature help for common built-ins such as
     `Function.prototype.apply`.
   - [x] Audit and expand general ECMAScript built-in completions such as
     `Object.prototype.hasOwnProperty`.
   - [x] Plan Node.js type completions for the future Node.js engine branch.

14. ACE function keyboard layout
   - [x] Match the two-part layout, scrollable symbol area, arrow sizing,
     shortcut behavior, default symbols, and hollow star entry point.

15. Action favorites
   - [x] Use the hollow star entry and rename the dialog to Action favorites.
   - [x] Record toolbar and replayable editor menu actions.
   - [x] Exclude shortcut short taps and symbol insertion from favorites.
   - [x] Record shortcut long-press actions and ACE selection-menu actions.
   - [x] Remove accidental Cancel entries.
   - [x] Compact the dialog rows and move usage counts to the right as `<n>`.
   - [x] Keep the most recent unpinned action first below pinned items.
   - [x] Keep drag sorting active during movement without full-list rebinding.
   - [x] Persist drag reorder after finger release.
   - [x] Support left-swipe delete and right-swipe pin/unpin.
   - [x] Use `R.drawable.ic_thumbtacks` for pinned items.
   - [x] Repeat the last action with star long-press.
   - [ ] Record open-ended setting actions such as setting wrap indentation,
     word wrap, editor engine, font, and font size.

16. Read-only mode
   - [x] Support session read-only mode for Ace and JsDroidEditor.
   - [x] Strictly block text mutation paths while read-only is enabled.
   - [x] Suppress soft keyboard activation in read-only mode.
   - [x] Hide the function keyboard area while read-only mode is enabled.
   - [x] Theme the Read-only mode menu checkbox with AutoJs6 colors in checked
     and unchecked states.
   - [x] Remove the occasional empty JsDroidEditor keyboard-area overlay after
     enabling read-only mode.

17. Pinch zoom
   - [x] Keep the `Scale view` zoom gesture continuous.
   - [ ] Keep the zoom anchor near the two-finger gesture center.
   - [ ] Fix ACE tap-to-caret coordinate offset after zoom.
   - [ ] Persist font-size changes after pinch zoom.

18. Code editor settings page and in-editor settings dialog
   - [x] Add the standalone Code editor settings page.
   - [x] Show the editor settings dialog from the editor menu.
   - [x] Use a fragment-safe MaterialDialog host for the in-editor settings
     dialog.
   - [ ] Apply editor engine changes immediately where feasible.
   - [x] Apply editor theme changes immediately where feasible.
   - [x] Apply editor font changes immediately where feasible.
   - [x] Apply editor font-size changes immediately where feasible.
   - [ ] Preserve dynamic gutter-width behavior when ACE word wrap is enabled.
   - [ ] Improve disabled unsupported-option visuals and hints consistently on
     both settings surfaces.

19. Clear undo/redo
   - [x] Make Clear undoable and enable Undo after clearing text.
   - [x] Enable Redo immediately after undoing Clear.
   - [x] Suppress the transient long selection menu that appears after redoing
     Clear.

20. Editor engine names
   - [x] Use `Ace` and `JsDroidEditor` user-facing engine names.
   - [x] Keep Ace first and default.

21. Input issues
   - [x] Suppress the long selection menu flash when deleting characters with
     the soft-keyboard Backspace key.
   - [x] Keep `xx.__proto__.` completion/runtime handling from crashing ACE.

22. Symbols settings
   - [x] Show the Ace default symbol configuration when the selected engine is
     Ace.

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
- Added a bridge-level `cancelTouchInteraction(reason)` path that resets
  accessible ACE pressed flags, hides transient completion/tooltip/signature
  UI, suppresses host selection action mode, and records
  `aceTouchInteractionCancelled`. Native and JavaScript right-edge guards now
  route detected pointer guards through this cancellation path. The bridge also
  listens for DOM `touchcancel` and window blur as best-effort cleanup points.

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
- If Sony still triggers long-press after this cleanup path, the likely next
  change is an ACE mobile `touch_handler` source patch that clears the internal
  long-touch timer on `touchcancel`/system takeover. A fixed wide right-edge
  dead zone should remain avoided because it breaks normal editor interaction.
