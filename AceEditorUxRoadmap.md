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
