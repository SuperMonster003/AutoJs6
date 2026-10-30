# Bun automation family: Rhino comparison and device validation

Date: 2026-10-30 (Asia/Taipei). Batch: api-coverage P4.4, the Bun column of automator (41 rows with the automator globals), auto (38), keys (12), ocr / barcode / qrcode / yolo / YoloDetector# (23), threads / continuation / timers / VolatileDispose# / the global sleep / wait (16), the selector globals with Flow# / flow (7) and events (31). The Node columns are P3.4 (`runtime-automation-2026-10-30.md`).

## Runtime behavior

The Bun host client (Bun Runtime 40ea7e0, 9b40a39, fa46dda; the later Node fixes synchronized by P4.3 in b01cc86 / 22a2035) carries the Node.js Runtime's shared automation factory. `tools/host-client/sync-automation-family.mjs` copies `__autojs6_create_automation` of Node 9736c852 - 079579ec with its declarations (automation.d.ts, ocr.d.ts, barcode.d.ts), and `sync-events.mjs` / `sync-system.mjs` the events and system factories it uses. `--check` of the three scripts runs in CI and in the host's structure check.

- automator and auto (Q183 (a)) are second modules of accessibility.control: the client sends their calls to the host's accessibility.control route with the Node module in the request, and BunAutomationBridge answers them through RuntimeAutomationBridge, as for Node. They need no grant of their own.
  - press / swipe / gesture* also need accessibility.gesture.
  - auto.start / stop / enable / disable / launchSettings / clearCache need accessibility.service (Q182 (a)), never implied by accessibility.control.
  - automator.captureScreen (the service's screenshot, Android 11+) needs screen.capture.
  - auto.dump / auto.explain (Q185 (a)) are on the accessibility.control white list; the other agent diagnostics (inspectNode, readScreenText, readDoc, pluginCapabilities, currentWindow) stay internal.
- keys, threads, continuation and timers are local. threads.start runs an asynchronous task of the same thread (Q175 (a)), atomic / lock use SharedArrayBuffer and can be passed to a Worker (Q176 (a)), disposable returns VolatileDispose. timers.keepAlive / loop / setIntervalExt are Rhino's; Rhino's timers.setTimeout and the five other timer members are Bun's own timers (D-9, native-timers), and the timed task members (addDailyTask and the rest) are host.tasks (tasks.control).
- ocr / barcode / qrcode / yolo (Q181 (a)) are behind three new grants: ocr.control (recognize, recognizeText, detectTextBounds; Rhino's mode / tap, detect, summary and the mlkit / paddle / rapid engines come from the shared factory's Rhino layer), barcode.control (detect, detectAll, recognizeText, recognizeTexts; qrcode is barcode limited to QR codes and shares the grant) and yolo.control (load, detect, close; YoloDetector).
  - A barcode takes an images handle of the execution, a file path or `{type: "capture", requireExistingPermission: true}`; the path and the capture are recycled when the call ends.
- events (Q182 (a)): the module's EventEmitter members are local under events.observe; onKeyDown and the other key name listeners need events.key, onNotification events.notification, onToast events.toast, onTouch events.touch and setKeyInterceptionEnabled events.key.intercept. events.touch and events.key.intercept are never implied. events.broadcast reaches every script over observeScriptBroadcasts / sendScriptBroadcast; emit waits for a session that is opening (Node c54d4592, found on this device run), and the session closes when the last listener goes (Node 079579ec).
- selector: pickup / detect / existsOne / existsAll probe with flow's one-step wait. They read the nodes over accessibility.control (the device run showed that flow.control is not involved; host b23220775 corrects the runtime policy).
- Globals (Q184 (a)): the family's 29 Rhino globals (click / longClick / press / swipe / gesture* / scroll* / input / setText / back / home / recents / notifications / quickSettings / powerDialog / splitScreen / retry, sync / keepAlive / loop, pickup / detect / existsOne / existsAll, sleep / wait with wait.async) are members of host.global; host.global.install(globalThis) puts them on the global object without replacing existing names.
- Grants: the client maps the Node capabilities of the shared factory to Bun grants (`familyCall`). A call whose grants are off rejects NOT_GRANTED with `missingCapabilities` before reaching the host (Q90). The selector functions reject in the client's call layer of flow, NOT_GRANTED with error.capability.
- Host (33ab825c4, 47fa448db, e2d824545): six grants, off by default, with Plugin Center switches in 11 languages: accessibility.service, events.touch, events.key.intercept, ocr.control, barcode.control, yolo.control. BunAutomationBridge routes 314 methods (295 before).

## Cases

`tools/api-coverage/device_bun_automation.py` runs the Node runtime's cases (`tools/nodejs/api/automation/cases.js` of P3.4, 53 cases) in Rhino, then in a Bun main script and in a Bun Worker of the same script. The Bun expressions see the host client namespaces under the Node names (system is host.global), after host.global.install(globalThis).

- The host's accessibility service must run; compare checks it first.
- Only cases without lasting effects run: actions target texts, indexes and bounds that match no node, gestures use negative coordinates, modes / flags / key interception / timeouts are restored, every listener, timer and task ends.
- A case whose id ends with "~" waits for an event (setIntervalExt, the broadcast).

The denied run turns off every family grant but events.observe and checks 19 entries:
- NOT_GRANTED with the missing grants: automator.click, the global back, auto.isRunning, auto.dump (accessibility.control); automator.press (with accessibility.gesture); auto.clearCache (with accessibility.service); automator.captureScreen (with screen.capture); events.observeTouch (events.touch); setKeyInterceptionEnabled (events.key, events.key.intercept); ocr.recognizeText and ocr.paddle.detect (ocr.control); qrcode.recognizeText (barcode.control); yolo.load (yolo.control);
- selector.pickup: NOT_GRANTED of accessibility.control in flow's call layer;
- answers from the local members: keys codes, threads.atomic, continuation.enabled, events.emitter and the global sleep.

## Verification

Device: Redmi 22120RN86C, serial `bek749scrwv4wo8h`, Android 13 / API 33, arm64-v8a. The maintainer enabled the host's accessibility service for the run (MIUI disables it when the host is force-stopped, so the runs bring the host to the front with `--foreground` instead).

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935) debug, md5 `b958b46599fe2c7188b58a84a1705553` | 6.8.0 (5935) debug arm64 built by P3.4 at 14:59 from an export of host HEAD with e2d824545, md5 `e379843552db1a339ed46eac395d99d7` | reinstalled from the recorded copy, md5 `b958b46599fe2c7188b58a84a1705553` |
| Bun runtime plugin | 0.20.0 (293) debug, md5 `bbfca1f67936a60a0700ec1a12e216eb` | 0.21.0 (296) debug arm64 of Bun 37a5f5c, md5 `037d7a71850f8283ee2893997b1b053a` | reinstalled from the recorded copy, md5 `bbfca1f67936a60a0700ec1a12e216eb` |

The 12 Bun grants of the run: absent before, absent after. The run directory `/sdcard/Download/aj6-bun-automation` is removed after each run.

| Run | Bun main | Bun Worker |
|---|---|---|
| compare (53 cases, Rhino 53 / 53) | 53 pass | 53 pass |
| denied (19 cases, only events.observe on) | 19 pass | 19 pass |

A first run with Bun 802beee (md5 `bb87e4374045c1764f07432a64adf578`) passed 52 / 53 in the main script and 51 / 53 in the Worker. The two failures came from the shared Node factories and are fixed in Node:
- threads.pool.sizes: `{corePoolSize: 3, maxPoolSize: 2}` answered instead of throwing as in Rhino (Node 079579ec, Bun b01cc86).
- events.broadcast~: the first emit right after the first on() went out before the broadcast session was registered and was lost, in whichever context ran first. A probe in one execution showed it: on + emit at once timed out, on + emit after 1.5 s and a later on + emit at once arrived. Node c54d4592 makes emit wait for the opening session (Bun 22a2035); the second run is also the device check of that fix.

The denied run of the first build found that the selector functions reject NOT_GRANTED of accessibility.control. With only flow.control off they answer, so the runtime policy names accessibility.control for them (host b23220775).

Commands (host checkout, `--state` a private directory):

```
py -3.12 tools/api-coverage/device_bun_automation.py record --serial SERIAL --state STATE
py -3.12 tools/api-coverage/device_bun_automation.py install --serial SERIAL --state STATE --apk org.autojs.autojs6=HOST.apk --apk io.github.supermonster003.autojs6.plugin.bun.runtime=BUN.apk
py -3.12 tools/api-coverage/device_bun_automation.py grant --serial SERIAL --state STATE --set on --only accessibility.control accessibility.gesture accessibility.service screen.capture events.observe events.key events.touch events.key.intercept ocr.control barcode.control yolo.control flow.control
py -3.12 tools/api-coverage/device_bun_automation.py compare --serial SERIAL --foreground
py -3.12 tools/api-coverage/device_bun_automation.py grant --serial SERIAL --state STATE --set off --only accessibility.control accessibility.gesture accessibility.service screen.capture events.key events.touch events.key.intercept ocr.control barcode.control yolo.control flow.control
py -3.12 tools/api-coverage/device_bun_automation.py denied --serial SERIAL --foreground
py -3.12 tools/api-coverage/device_bun_automation.py grant --serial SERIAL --state STATE --set restore --only accessibility.control accessibility.gesture accessibility.service screen.capture events.observe events.key events.touch events.key.intercept ocr.control barcode.control yolo.control flow.control
py -3.12 tools/api-coverage/device_bun_automation.py restore --serial SERIAL --state STATE
```

Desktop checks:
- Bun (37a5f5c): the 140 `node --test` tests of the CI (125 of the host client, automation-family.test.mjs 5 among them), `sync-automation-family.mjs` / `sync-events.mjs` / `sync-system.mjs --check` against Node HEAD, type checks of host-client.d.mts and `tools/host-client/types.mts`.
- Host: BunAutomationPolicyTest 18, RuntimeAutomationBridgeCapabilityTest 6, RuntimeFlowPolicyTest 8; `check-bun-automation-family.mjs` (members automator 55, auto 38, keys 12, ocr 6, barcode 4, qrcode 4, yolo 1, threads 9, continuation 4, timers 4; factory routes 37, ocr.control 3, barcode.control 4, yolo.control 3) and `check-runtime-events.mjs`; test_runtime_matrix 45 tests (test_p4_4); `check.py --matrix --strict-arity --docs-support` with Ace in an export of host HEAD with the KSP metadata of 77e94eb5b (the host sources after it only touch engine / plugin files).
- TS `verify-runtime-declarations.mjs` and Ace `verify-runtime-api-coverage.mjs --fresh` (4736 queries).

## Leftovers

- Flow#await / Flow#sync stay pending (Q31).
- threads.pool without maxPoolSize means no upper bound, as in Node (same shared factory), while Rhino throws IllegalArgumentException (Q180, undecided; `{corePoolSize: 3, maxPoolSize: 2}` throws in all three).
- In the matrix a Bun cell names one grant, the one whose method table routes the member. press / swipe / gesture* (accessibility.gesture), auto.start and the other service members (accessibility.service), captureScreen (screen.capture) and events.observeKey / observeTouch and the other observe* members (their events.* grant) need a second grant, checked per call before the host and listed in host-api.md; the denied run covers them.
- The flow namespace's own rows show flow.control. Flow's waits read the nodes over accessibility.control as the selector functions do; the selector rows now show accessibility.control, the flow rows are left to the flow family (Q28).
