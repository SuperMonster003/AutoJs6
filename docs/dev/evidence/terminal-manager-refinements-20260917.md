# Terminal and manager interaction refinements, 2026-09-17

This follows [the initial terminal and manager work](terminal-managers-20260917.md). The direct-input and selection behavior below supersedes that report's separate command field and draft handling.

The later [terminal navigation, console settings and Node media follow-up](terminal-console-node-media-20260917.md) changes New session and Resume to open a terminal activity immediately while keeping the manager underneath for Back navigation.

## Changes

- Terminal input goes directly to the PTY. There is no separate command field or Send button. Unsubmitted input remains in each shell when switching sessions.
- The two-row key bar uses uppercase labels and equal-width paired keys. HOME / UP / END / PGUP above LEFT / DOWN / RIGHT / PGDN stay fixed on the right, including in RTL layouts. Only the symbols on the left scroll horizontally.
- Long press opens a frozen transcript with Android text-selection handles and Copy / Select all. The shell continues receiving output behind it. Copy removes display-only wrapping while retaining real line breaks; dismissing selection restores the live terminal.
- Terminal menus are grouped into Sessions, Text, Package management, Settings and Help, with Show keyboard directly accessible. Tips remain available only from the menu.
- Terminal manager New session creates a background session and keeps the manager open. Each session has a script-style play button to resume and a cross to stop it. The shared connection row still omits these buttons for ordinary server clients.
- Accessibility short status values use one line unless a second is needed. Permission rows open root manager candidates, the existing secure-settings ADB dialog, or Shizuku authorization / its app / the not-installed hint. Other diagnostic rows remain copyable.
- Root candidates render from persisted cache immediately and refresh from installed applications on a worker. The installed-app cache lasts five minutes, is locale-aware, and can be refreshed explicitly. Users can search installed app names or package names and add candidates; each candidate has a launch button.
- Stable mode uses the application's existing setting title, description and preference. It is available in the manager settings and as the last status entry, whose guidance offers Enable and Disable and explains restarting the service.
- Fully collapsed server controls include Disconnect all clients. The MCP drawer entry uses the supplied protocol mark with the existing icon tint and spacing.
- File-list and toolbar recognition accept executable Node.js project metadata independently of APK packaging metadata. Such projects expose Run, Edit and Terminal but not Build. The settings editor accepts the same executable manifest. Project rows retain the correct explorer parent.

## Startup cancellation regression

The final device regression exposed a failure in the earlier immediate-close test: the session disappeared from the registry but its exit callback did not arrive within 15 seconds. A remaining shell PID 12148 had its own process group and an ignored SIGHUP bit (`SigIgn` ended in `0001`).

Non-suspending logpoints were prepared using the ij-debugger skill. The IDE remote debugger remained at Connecting / Waiting for application and produced no logpoint events; Android instrumentation remained waiting for the debugger. No debugger observations are claimed. The temporary run configuration, both agent logpoints, and the emulator-only JDWP forward were removed, leaving the five original user exception breakpoints unchanged.

Process inspection and the [pinned upstream subprocess implementation](https://github.com/jackpal/Android-Terminal-Emulator/blob/35188f8a8b57989a4a4ec9485e11187b46be26d9/libtermexec/src/main/jni/process.cpp) support the startup race: the parent receives the child PID before the child necessarily establishes its process group, so a group SIGHUP alone cannot guarantee cancellation. Closing the PTY concurrently with subprocess creation was also unsynchronized.

The fix serializes subprocess creation / PID publication with explicit close. Cancellation before creation skips the fork. Explicit close hangs up the process group and also terminates the child PID, even before its process group exists. The watcher reaps the child and publishes its exit state before scheduling UI callbacks. Normal process completion still follows the existing callback path.

The regression now performs 30 cancellations, mixing immediate and briefly delayed close with initialized background sessions. It checks that each session leaves the registry, reports completion, and has no remaining `/proc/<pid>` entry when a child was created.

## Verification

- Debug application and instrumentation APKs built successfully with JDK 21; bundled native libraries passed the existing 16384-byte alignment check.
- 49 terminal JVM tests passed, with no failures or skips.
- Seven instrumentation tests passed on an owned API 33 x86_64 emulator (`AutoJs6_Refinements_API33`, serial `emulator-5590`): three terminal cases and four project-toolbar cases. They cover shell state and unsubmitted input across session switching, independently closing sessions, the 30 cancellation scenarios, selection snapshots with Chinese characters and long wrapped output, and executable Node.js / Python / legacy / rejected projects.
- The Node.js fixture matches the reported `host-events` project: `type: node`, `main: main.cjs`, runtime options and permissions, and no APK package/version metadata. The file list now returns an `ExplorerProjectPage` with the containing page as its parent.
- Manual emulator UI checks covered compact accessibility status heights, stable-mode preference changes through both entry points, the ADB guidance command, and the Shizuku-not-installed branch. The root picker was searched by package name; adding Settings persisted across dialog reopening and its launch button opened `com.android.settings/.Settings`. Actual Magisk / KernelSU grant state and a running Shizuku authorization dialog were not exercised on this emulator.
- Terminal manager checks created two sessions without closing the dialog, stopped one through its cross, and resumed the other through its play button. The left symbol area scrolled while all eight navigation button bounds stayed fixed.
- Text selection was visually checked in light and dark themes. A single long press showed both handles and the floating toolbar. Dragging expanded TERMINAL to TERMINAL TEXT; Copy followed by terminal Paste reproduced the selected text. Select all was also exercised. The instrumentation snapshot case verifies that subsequent output does not change the captured selection and that hard line breaks survive copying.
- In `/storage/emulated/0/Scripts/Node.js-Test`, the direct input `$ npm install cheerio` remained fully visible. The managed profile's separate directory prompt line prevents the original long-directory example from consuming the command's columns. Commands longer than the terminal width still follow the system mksh horizontal line-editing behavior; no general multiline shell editor is claimed.
- Two local protocol-1 TCP clients connected through an emulator-only forward to the server. Disconnect all clients from the fully collapsed control header closed both sockets while leaving the server listening. The test server and forward were then stopped.
- New strings were checked across 11 resource directories for uniqueness, insertion order and punctuation; default and English contents match. Ten-language version 6.8.0 changelogs use 2026/09/17, and `.python/generate_markdown.py` regenerated the README and bundled changelog files. No script API changed, so documentation and declaration companion projects require no update.

Raw build/test logs, UI helpers and screenshots are in the ignored `build/verification/terminal-refinements/` directory. Final test logs are `lifecycle-build.log` and `lifecycle-device-tests.log`; `package-final.log` includes the final resource/changelog packaging. Useful screenshots include `terminal-direct-input.png`, `terminal-symbols-scrolled.png`, `terminal-sessions.png`, `selection-first-long-press.png`, `selection-dragged.png`, `selection-dark-final.png`, `root-candidate-dark-final.png`, `server-collapsed-clients.png`, and `drawer-dark.png`.
