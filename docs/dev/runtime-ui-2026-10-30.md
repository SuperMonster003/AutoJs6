# Node.js UI family: Rhino comparison and device validation

Date: 2026-10-30 (Asia/Taipei). Batch: api-coverage P3.3 (ui with the content view and window members, console input and build, toast, floaty, web, terminal, compose), designed in appendix C Q164 - Q169.

## Runtime behavior

The Node.js Runtime plugin (8fc9c801, device harness fc0ca86b) builds the family from the shared UI factory `tools/nodejs/api/portable-ui-compose.js` (synchronized into `node_bridge_sources.cpp` and, by P4.3, the Bun host client) on the main thread and in Workers (CJS, ESM, nested). `autojs6:floaty`, `autojs6:web` and `autojs6:terminal` answer only to their `autojs6:` names.

- ui (D-17, Q164 (a)): one content window per execution (the showLayout Activity with the host option `contentView`). layout / layoutFile / setContentView return Promises and replace the root in place; XML ids resolve to native View handles before the Promise settles, so root / findById / findByStringId answer synchronously; finish closes the window. The emitter of the thread that set the content receives the Activity lifecycle and back_pressed; registerWidget / Widget expand custom tags locally, bindingContext evaluates `{{}}` bindings, R answers reference strings, run / post / isUiThread (always false) are local.
- ui window members (R7): keepScreenOn, the window and bar colors and the bar icon modes go to the content window (remembered until it opens); the bar heights ask the host on every read.
- console (D-7, Q165 (a) / Q166 (a)): rawInput prints the prompt, shows the console input and returns a Promise of the line (the host's ConsoleImpl.requestInput, cancelled with the execution); input evaluates the line in the calling thread; build returns a local ConsoleConfigurator with Rhino's 52 members whose show applies the recorded options in one host call (console.configure). isShowing and the remaining window controls are host routes.
- toast: dismissAll cancels the execution's toasts; toastLog / Info / Warn / Error / Verbose show a toast and print the first argument at their console level.
- floaty (Q168 (a)): window / rawWindow over the host routes floatyWindow / floatyRawWindow / floatyControl / floatyCloseAll (RuntimeFloatyWindow); `w.<id>` and findView answer synchronously, the geometry asks the host; capabilities ui and ui.overlay, rawWindow also floaty.raw.
- web (Q168 (a)): newWebSocket over the controlled `autojs6:websocket` connection with Rhino's events, a local ByteString, the injectable WebView client and WebView.
- terminal (Q167 (a)): RuntimeTerminalBridge over the host's TerminalService (the service of Rhino's terminal module) behind the new capability `terminal`, which shell / shell.root do not imply. Sessions are local handles; waitFor subscribes from its call (as Rhino), and since write and waitFor are separate asynchronous calls, a script waiting for a command's output starts waitFor before the write.
- compose: ComposeError (module and global), argument errors are ComposeError with code INVALID_ARGUMENT, nodes have type / key / nodeId / parent / children and the shortcut accessors (Q104); the session, floating window, state and ref are compared through their handle types (Q106 (a)).
- Globals in main threads and Workers: isUiThread, toastLog / ... and their lowercase forms, newWebSocket / newInjectableWebView / newInjectableWebClient, ComposeError (listed with the system globals for autojs6:compat, Q135 (b)).
- Differences kept (HOST-API, "UI 族的 Rhino 对照"): host calls return Promises; Node threads are not the Android UI thread; ui.R answers reference strings; every thread has an emitter (Rhino's exists in "ui" scripts only); Compose floating windows behave as Rhino raw windows (setAdjustEnabled(true) rejects); toasts settle with null, as toast() does; ui.layoutInflater is excluded as a Java object (Q169 (a), equivalent ui.inflate / ui.createView).

Host routes (host 77e94eb5b): RuntimeUiInterop (showLayout contentView, lifecycle events, interceptBack), RuntimeUiWindowControls (windowControl, barHeight), RuntimeFloatyWindow, RuntimeConsoleBridge (isShowing, 11 controls, configure, rawInput), ScriptToast.dismissAll by owner, RuntimeTerminalBridge and the capability `terminal` in the host manifest, the plugin manifest and the capability catalog.

## Rhino values and cases

`tools/nodejs/api/ui-family` in the Node plugin repository holds 22 cases: 19 compared with Rhino and 3 Node-only ones with fixed values (ui.R reference strings, the content view, and ui.emitter, which Rhino has in "ui" scripts only). They cover the ui thread and bar heights, floaty permission checks and a raw and a resizable window (position, view ids, adjustment), ByteString and the injectable client, terminal state and a session (open, waitFor, write, isAlive, close) and a missing session, console isShowing and build, toast dismissAll and toastLog, compose nodes / state / ref and ComposeError, the family's globals and a WebSocket to a closed local port (the failure event). `device_ui_family.py compare` runs them in Rhino and in Node in one session and compares the values; a last run declares shell, shell.root and network only and checks 7 entries: terminal (not implied by shell), floaty.window (ui, ui.overlay), floaty.rawWindow (floaty.raw, ui, ui.overlay), console.rawInput (console), toast.dismissAll (toast) and ui.layout (ui) reject with ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED and their missing capabilities, while ByteString stays local.

Desktop comparison (`tools/nodejs/api/test-ui-rhino.js`, 9 tests, in the plugin's build workflow): the content view and window members, floaty, web, terminal, console.build, compose, the leveled toasts and the CJS / ESM / nested Worker modules and globals.

## Verification

Device: Xiaomi 23046RP50C, serial `968e9f18`, Android 15 / API 35, arm64-v8a. The host held the overlay permission; 3-Block Compose and 3-Shell Terminal were installed.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935), base.apk md5 `ebb12da6beae22f72ab0278b44b89aa8` | 6.8.0 (5935) debug arm64 (md5 `e379843552db1a339ed46eac395d99d7`) built by P3.4 from an export of e2d824545, whose 12 main source files of the P3.3 routes are byte-identical to 77e94eb5b | reinstalled, md5 verified |
| Node.js Runtime plugin | 1.32.0 (292), base.apk md5 `399e7a359fea55557a2415801b3b6b3d` | 2.0.0 (340) debug arm64 (md5 `f2c016a3e79e920a73cdba153103e3c0`) built by P3.4 from 8fc9c801 plus two lines of P3.4's threads option check (not in the UI family) | reinstalled, md5 verified |

`device_ui_family.py verify` against the recorded state reported the same packages and APK md5. The run directory (`/sdcard/Download/aj6-p33`) was removed.

| Check | Result |
|---|---|
| Rhino | 19 / 19 cases recorded, none thrown |
| Node main thread (CJS `require`) | 22 passed, 0 failed |
| Node ESM (`import` from a data: URL) | 22 passed, 0 failed |
| Nested Worker (Worker started by a Worker, CJS) | 22 passed, 0 failed |
| Undeclared capabilities | 7 passed |

The final case set passed in two consecutive runs. The cases compare values, so the passes include equal status and navigation bar heights, the raw window's position after setPosition(12, 34), the resizable window's adjustment flag before and after setAdjustEnabled(true), terminal.state / isAvailable and the console configurator's recorded title and text size; the reports keep the counts only.

Earlier runs showed four differences, all in the harness: Rhino's ui.emitter is null outside "ui" scripts (the case became Node-only); Rhino's ConsoleConfigurator.titleTextSize is a boxed java.lang.Float (compared as Number); Rhino's toastLog returns undefined while the runtime's toasts settle with null like toast() (compared as such); and the first Node run after the Rhino run missed the echo of a terminal session because write and waitFor were awaited in sequence and waitFor subscribes from its call (the case now starts waitFor before the write; a probe measured open 107 ms, write 146 ms, match 193 ms; HOST-API notes the pattern).

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-ui-rhino.js
py tools/nodejs/api/ui-family/device_ui_family.py state -s <serial> --state state.json
py tools/nodejs/api/ui-family/device_ui_family.py compare -s <serial> --report report.json
py tools/nodejs/api/ui-family/device_ui_family.py verify -s <serial> --state state.json
```

`state` records versions and md5 only; keep copies of the installed APKs (`adb shell pm path`, `adb pull`) before installing the test builds.

In the host repository, `node tools/api-coverage/check-runtime-ui.mjs` (CI "Script API coverage") checks that every host member of ui / floaty / web / terminal and of the family's returned objects (224) has a member in the shared UI factory and a Node declaration, the console controls and input, the leveled toasts, the compose sessions, the 15 globals in both threads, the Worker wiring, the 66 catalog routes and that `terminal` is never implied.

## Matrix

Host 398ab3930: pending Node main 680 -> 577, Worker 681 -> 577 (the family 103 / 104 -> 0; P4.4's 7bb11982c had already counted 92 / 92 of them from Node 8fc9c801, the rest are the global aliases and the handle types). The family's 384 rows are bridged in both columns but 7 not-portable ones (6 internal names and ui.layoutInflater). Q106 adds 47 rows (matrix 3470 -> 3517); their 33 Bun cells stayed in the baseline for P4.3 (Bun 656 -> 688, then 577 with P4.3's cc3a55f97). The 103 rows of ConsoleConfigurator, the floaty windows, WebSocket and the injectable WebView / client moved from not-portable Java objects to handle-type comparisons (Q166 / Q168). Ace's runtime queries 4736 -> 5079 (Ace 072c938), TS d1f4cad, Ace b0a3b36, documentation ab68155 / 9567ec3, Offline Docs a0f2265.
