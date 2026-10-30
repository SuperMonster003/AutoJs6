# Node.js automation family: Rhino comparison and device validation

Date: 2026-10-30 (Asia/Taipei). Batch: api-coverage P3.4 (automator, auto, keys, ocr / barcode / qrcode / yolo, threads / continuation / timers, selector globals, events; D-1 / D-6 / D-8).

## Runtime behavior

The Node.js Runtime plugin (9736c852, b77c28cb, 079579ec) builds the family from one shared factory (`__autojs6_create_automation`) on the main thread and in Workers (CJS, ESM, nested). Entries are `autojs6:automator`, `autojs6:auto`, `autojs6:threads`, `autojs6:continuation`, `autojs6:timers`, `autojs6:yolo`, `autojs6:selector` and `autojs6:qrcode`; `keys`, `ocr`, `autojs6:events` and `autojs6:system` gain Rhino's members. Host routes are in RuntimeAutomationBridge and NodeAutomationObservers (host 0feda5cfc).

- automator: click / longClick take (x, y), a point, a bounds object or node snapshot (its center), (text, index?) and (left, top, right, bottom); press / swipe / gesture / gestures take every Rhino argument form and need `accessibility.gesture`; the *Async forms return at once and call a trailing callback; coordinates follow setScreenMetrics. back / home / recents need `accessibility`, the other global actions `keys` (as the keys module). waitForService and auto.waitFor (D-1, Q179) enable the service when Android allows it without the settings page, then poll every 200 ms and reject with ERR_AUTOJS6_TIMEOUT.
- auto: the callable auto(mode?, forceRestart?), start / stop (enable / disable are aliases, as in Rhino) / launchSettings / clearCache under the new `accessibility.service`, mode / flags / window filter of the thread, getters returning a new Promise per read, window queries filtered by Rhino's window match arguments, registerEvent(s) / removeEvent(s) and stateListener over host event sessions (distinct and debounce run in the plugin), explain / dump, current*.
- threads (D-6, Q175 (a) / Q176 (a)): start runs the function as an asynchronous task of the same thread (handle with isAlive / interrupt / join / waitFor, an AbortSignal for the task); pool is a local task queue; atomic and lock live in a SharedArrayBuffer that Workers open with threads.atomic(buffer) / threads.lock(buffer); disposable is Rhino's VolatileDispose; sync chains calls on one lock.
- continuation (D-8, Q179): enabled is true (Node awaits natively), create / await / delay are Promises. timers: keepAlive returns a Node timer, setIntervalExt is Rhino's, loop only warns. Rhino's global sleep / wait (wait.async) are members of `autojs6:system`.
- events (Q18 prototype rows): EventEmitter members, emitSticky, emitter(), getTimer(), broadcast (the host's process-wide table, Rhino scripts included), key listeners by name, key interception under the new `events.key.intercept`, touch observation (Root or Shizuku) under the new `events.touch`.
- yolo (`yolo`, `image`): load returns a YoloDetector session (detect / close), Rhino's YOLO_* codes in error.code. ocr: mode / tap / options.mode select the engine, the fixed-engine ocr.mlkit / paddle / rapid, detect, summary. keys: Rhino's KeyEvent codes. qrcode: barcode limited to QR codes. selector: pickup / detect / existsOne / existsAll as one probe of flow.wait.
- Globals (Q177 (a)): automator's 20 actions, sync, keepAlive, loop and the four selector functions are accessors on globalThis in main threads and Workers; sleep / wait come with autojs6:system's globals.
- Differences kept (the plugin's HOST-API, "自动化族的 Rhino 对照"): actions and queries return Promises; threads.start does not start a Java thread; lock is not reentrant; ocr.recognizeText resolves one string; auto.setFlags keeps the flags in the thread and sends only the mode and appWindowsFallback with window queries (Rhino's useUsageStats / useShell change how current* read the foreground app, and useUsageStats without usage access throws SecurityException and opens its settings page; Node's auto.current* use accessibility); Flow#await / Flow#sync stay pending (Q31).

## Rhino values and cases

`tools/nodejs/api/automation` in the Node plugin repository holds 53 cases (44 compared with Rhino, 9 Node-only with expected values: auto.enable / disable, continuation, keepAlive, yolo.load, ocr modes, wait.async and the globals) and 8 undeclared-capability checks. `device_automation.py compare` runs every case in Rhino and in Node in one session and compares the values there. Actions target a text, an index or bounds that match no node, gestures use negative coordinates (Android rejects the path before dispatching), modes / flags / key interception / the touch timeout are restored, and every listener, timer and task ends; `timers.setIntervalExt~` and `events.broadcast~` wait for later events. The undeclared run declares `accessibility`, `events` and `events.key` only and expects ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED with `accessibility.service` (auto.clearCache, auto.launchSettings), `accessibility.gesture` (press), `keys` (dismissNotificationShade), `events.key.intercept` (setKeyInterceptionEnabled), `events.touch` (observeTouch) and `image` / `yolo` (yolo.load), and a value from auto.isRunning.

Desktop comparison (`tools/nodejs/api/test-automation-rhino.js`, 18 tests, in the plugin's build workflow; the workflow's host facade list passes 137 / 137).

## Verification

Device: Sony G8441 (Xperia XZ1 Compact), serial `BH900ASK9E`, Android 9 / API 28, arm64-v8a, no Root, the host's accessibility service enabled before the run.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935), base.apk md5 `b958b46599fe2c7188b58a84a1705553` | 6.8.0 (5935) debug arm64 built from an export of e2d824545 | reinstalled, md5 verified |
| Node.js Runtime plugin | 2.0.0 (340), base.apk md5 `7d4221fb5c373d2038c74485e91120a9` | 2.0.0 (340) debug arm64 built from an export of 8fc9c801 with the `node_bridge_sources.cpp` of 079579ec | reinstalled, md5 verified |

`device_automation.py verify` against the recorded state reported the same packages and APK md5. The run directory (`/sdcard/Download/aj6-p34`) was removed.

| Check | Result |
|---|---|
| Rhino | 44 / 44 cases recorded, none threw |
| Node main thread (CJS `require`) | 53 passed, 0 failed |
| Node ESM (`import` from a data: URL) | 53 passed, 0 failed |
| Nested Worker (Worker started by a Worker, CJS) | 53 passed, 0 failed |
| Undeclared capabilities | 8 passed |

Device values of this run: the service running and operational in both runtimes, the windows and roots present, actions on absent targets false, the four negative-coordinate gestures rejected in both, retry 2 after two failures, keys codes 3 / 4 / 82 / 24 / 25, atomic `[6, 6, 9, true, 1, 0]`, lock `[true, true, false]`, disposable `[7, null]`, wait `[true, 3, "no", 2]`, setIntervalExt `[3, 3]`, broadcast 3.

The first runs found three differences:

- `auto.setFlags(['useUsageStats'])` throws in Rhino without usage access (SecurityException) and opens the usage access settings page (`AccessibilityBridge.flags`; the page was closed with the Home key afterwards). The case now uses findOnUiThread, and HOST-API states that Node keeps the flags in the thread (only the mode and appWindowsFallback go to the host).
- `threads.pool({corePoolSize: 1})` throws in Rhino: `Threads.internalPool` compares corePoolSize with the raw maxPoolSize (0, which it treats as unbounded only for the executor). Node now throws a RangeError when maxPoolSize is given and corePoolSize is larger (as Rhino), and keeps the pool unbounded without maxPoolSize; Q180 asks whether to fix the host instead.
- The nested Worker form timed out after the main and ESM forms: both executions stayed alive because the events.broadcast session stayed referenced after `removeAllListeners`, and the third execution did not start within the 180 s wait (alone, or after the ESM form only, it passed). The broadcast session now closes with the last listener (079579ec, desktop test added); a main-thread run of all cases then ends right after the last case.

Afterwards P4.4 found on a Redmi (Bun, the same events factory) that a broadcast emitted right after the first `events.broadcast.on()` could reach the host before the observer was registered and was lost for the script (Rhino registers synchronously); in this run `events.broadcast~` passed in every form. Node c54d4592 makes emit wait for the session that is opening (desktop test of the host call order); it was not re-run on this device.

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-automation-rhino.js
py tools/nodejs/api/automation/device_automation.py state -s <serial> --state state.json
py tools/nodejs/api/automation/device_automation.py compare -s <serial> --report report.json
py tools/nodejs/api/automation/device_automation.py verify -s <serial> --state state.json
```

`state` records versions and md5 only; keep copies of the installed APKs (`adb shell pm path`, `adb pull`) before installing the test builds. The host's accessibility service has to run before `compare`.

In the host repository, `node tools/api-coverage/check-runtime-automation.mjs` checks that every host member of the family (180) has a member in the shared factory and a Node declaration, the events prototype (36), VolatileDispose / YoloDetector, the 29 globals on globalThis, the Worker table and forwarding, the 31 RuntimeAutomationBridge / NodeAutomationObservers routes in the catalog with their capabilities, `accessibility.service` / `events.key.intercept` / `events.touch` never implied, and the HOST-API section. `test_runtime_matrix.py` (test_p3_4) checks the family's matrix rows.

## Matrix

Host 307655f10 (the structure check is in CI since 94c21be6f): pending Node main 814 -> 680, Worker 867 -> 681 (the family 134 / 186 -> 0; Flow#await / Flow#sync stay pending per Q31). runtime-policy.json: returnedObjects VolatileDispose / YoloDetector for Node, wait.async member alias. Ace's runtime queries 4293 -> 4577 (Ace bc80847), TS bdaf3da, Ace 2285938, documentation efaad8e / 6e0936c, Offline Docs 28fa851.

## Leftovers

- Bun column: P4.4 (host e2d824545 / 7bb11982c).
- Flow#await / Flow#sync: Q31.
- Q179 (D-1 / D-8 / continuation forms): applied as recommended, awaiting confirmation.
- Q180: threads.pool without maxPoolSize; Rhino's Threads.internalPool compares corePoolSize with the raw maxPoolSize (0 = unbounded), so any corePoolSize > 0 throws there; Node treats it as unbounded.
- Bun: `tools/host-client/sync-events.mjs` has to copy the broadcast change of 079579ec into host-client.mjs (the GENERATED EVENT FACADES; its `--check` fails until then).
- auto.setFlags: Rhino's useUsageStats / useShell change how current* read the foreground app; Node's auto.current* use accessibility (HOST-API).
