# Terminal sessions and manager dialogs, 2026-09-17

Follow-up: [terminal and manager interaction refinements](terminal-manager-refinements-20260917.md) supersede the separate command field and startup-close implementation described here.

## Scope

- Drawer terminal switch reflects live shell sessions. Enabling creates a new session and opens the terminal; disabling closes all sessions. The Chinese subtitle is exactly `运行中  [ 会话: 1 ]`, with U+0020 U+0020 after `运行中`, and disappears when no session remains.
- Terminal description offers Manager / Close / New session. Its manager lists sessions and opens or closes them separately. Session switching preserves each shell and its unsent command draft.
- Accessibility, client, server and terminal managers share section header controls. Minus / plus stay at the right, separated from other actions. Collapsed settings open their actual controls in a standalone dialog and restore them when dismissed. The client's collapsed control row includes history before the divider.
- Terminal tips are menu-only. The keyboard has two rows, horizontally scrollable symbols and fixed navigation keys. The transcript has content padding. A multiline command field wraps long commands, while tapping the transcript, Tab and Ctrl / Alt retain direct shell interaction.
- Screen color picker Settings opens the plugin launcher activity. Stopped MCP Server has no drawer subtitle. Related MCP plugin theme work is committed in that repository as `55f7284`.

## Input behavior and lifecycle findings

A non-suspending debugger logpoint in `TerminalPtySession.applyWindowSize` on a Sony XQ-DQ72, API 33, recorded the expected column changes (68 to 57) while resizing. The lost prefix came from the system mksh line editor scrolling horizontally after the prompt and input exceeded the line width. The shell does not offer a multiline editing mode for this case. The new command field wraps before sending; the managed prompt also puts the directory on a separate line. Direct shell editing retains mksh behavior. Custom profiles are preserved; only the exact previous managed profile is migrated.

The upstream EmulatorView does not replace its emulator when attaching a different initialized session. Switching now creates a fresh view and detaches old callbacks. Device testing also exposed an upstream TermSession.finish null dereference before emulator initialization. Closing a starting session now closes its pty safely, and a shell created concurrently is hung up and reaped.

## Verification

- Host `:app:assembleAppDebug` and `:app:assembleAppDebugAndroidTest` succeeded. Terminal JVM tests: 49 passed, 0 failed or skipped.
- API 33 x86_64, private read-only `AVD_API_33` instance `emulator-5590`: 2 terminal instrumentation tests and 3 drawer layout tests passed. The terminal tests check distinct shell variables and transcripts, wrapped drafts, switching back to the same draft, closing one session while another remains live, and closing immediately during startup.
- All four managers were opened through the drawer in Chinese. For each, status / control / sessions where applicable were collapsed, settings were collapsed, the gear dialog was opened, all original setting labels were compared with the popup, and controls were restored. Client history placement and the divider rules were checked in screenshots.
- End-to-end drawer exercise: enable terminal, verify no tips popup, create and enter `/storage/emulated/0/Scripts/Node.js-Test`, type `npm install cheerio` without submitting it, leave, observe exactly one session, create another from the description, observe two sessions, disable, and verify the subtitle disappears. Both separating characters were read back as `0x20` from the UI hierarchy. No npm package download was needed.
- The terminal manager was also checked with two live sessions: its detail dialog showed directory, PID and elapsed time; closing session 1 left session 2 available to open. A long unsent command was visually checked wrapping across two lines with the new keyboard.
- Screen color picker description showed Settings; clicking it resumed `io.github.supermonster003.autojs6.plugin.screencolorpicker/.MainActivity`.
- MCP settings: 235 JVM tests, debug / test APK builds, lint with 0 errors and 11 warnings. Five settings and accessibility device tests passed on API 33 physical and emulator devices; the emulator also passed the settings case in dark mode. Light / dark settings and history screenshots were reviewed. Details are in the plugin ROADMAP.
- New strings were verified in 11 resource directories, with default / English equality, sorted insertion and ASCII punctuation. Ten-language changelogs and generated Markdown were updated. The previous changelog wording that restricted client history to fully expanded controls was corrected.

Raw logs, UI scripts and screenshots are in the ignored `build/verification/terminal-managers/` directory, with MCP artifacts in the plugin's `build/verification/theme/`. The original user change to the client history action's basic visibility is retained in this implementation. No script API or declaration changes were made.
