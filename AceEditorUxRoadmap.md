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
- [x] Allow clicking folded-code placeholders to unfold.
- [x] Preserve expected automatic cursor reveal on IME/panel changes, but avoid
  unsolicited pull-back while browsing selected code.

Acceptance:

- [x] Selection is visible in dark theme.
- [x] Gutter width does not shrink during the editing session and grows only
  when higher line-number digits become visible.
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

- [x] Support IME spacebar cursor movement in ACE through soft-keyboard cursor
  key events.
- [x] Add ACE read-only mode, default off for every new editor session.
- [x] Strictly block text mutation paths while read-only is enabled.
- [x] Improve read-only scrolling performance where possible.

Acceptance:

- [x] Soft-keyboard cursor key events update the ACE caret without text loss.
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
- [ ] Keep double-tap and long-press host action modes anchored near the user's
  touch point without upper-left fallback flicker.
- [ ] Preserve gutter fold toggle while keeping folded-code placeholder unfold
  support.
- [ ] Keep Sony right-edge back gesture protection while preserving normal tap,
  long-press, double-tap, and vertical scroll behavior outside the narrow
  system gesture edge.
- [x] Add word wrap support to the Native editor if technically feasible.
- [ ] Add a wrap indentation style setting: continuation indent, default, and
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
