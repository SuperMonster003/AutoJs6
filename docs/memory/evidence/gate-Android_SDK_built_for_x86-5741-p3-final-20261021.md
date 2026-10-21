# Memory gate Android SDK built for x86 (5741) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5741 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5741-20261021153928, label p3-final, 2026-10-21T07:39:28.001665+00:00 -> 2026-10-21T08:24:33.015237+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a normal, S4a force, S4f normal, S4f force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.11 / +0.04 / +0 / +0 / 0 |  |
| S1 empty script | F | pass | 0 / +0.09 / -0.01 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.15 / +0.21 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | F | pass | 0 / +0.11 / -0.22 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.10 / +0.03 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | F | pass | 0 / +0.10 / -0.19 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | N | pass | 0 / +0.14 / +4.55 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | F | pass | 0 / +0.10 / +0.25 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.10 / +0.09 / +0 / +0 / 0 |  |
| S4b images.read + findImage | F | pass | 0 / +0.05 / -0.00 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | N | pass | 0 / +0.10 / -0.17 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | F | pass | 0 / +0.09 / +0.11 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | N | pass | 0 / +0.10 / +0.02 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | F | pass | 0 / +0.09 / +0.11 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | pass | 0 / +0.10 / +0.14 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | pass | 0 / +0.09 / +0.09 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | N | pass | 0 / +0.02 / +4.71 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | F | pass | 0 / +0.03 / +4.76 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.07 / -0.13 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.09 / -0.31 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.10 / +0.65 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | F | pass | 0 / -0.01 / +0.52 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | fail | 1 / +1.46 / +0.11 / +0 / +0 / 0 | retained runtimes 1/20 |
| S7 events / key observer / sensors / accessibility events | F | pass | 0 / +0.09 / -0.10 / +0 / +0 / 0 |  |
| S7b className(...).find() loop on the accessibility tree | N | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S7b className(...).find() loop on the accessibility tree | F | skipped | skipped | the host accessibility service is not bound to the test process (run with memoryGateAccessibilityWaitMs and turn the service off and on, ROADMAP appendix C Q9) |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | N | pass | 0 / +0.11 / +0.56 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | pass | 0 / +0.09 / +0.40 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.14 / +0.17 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.16 / +0.09 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.10 / -0.10 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | F | pass | 0 / +0.08 / +0.30 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | N | pass | 0 / +0.10 / +0.03 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | F | pass | 0 / +0.09 / -0.05 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | N | pass | 0 / +0.09 / +0.02 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | F | pass | 0 / +0.11 / +0.09 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +0.38 | +0.09/+0.04/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S1 | F | +0.43 | -0.14/-0.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S2 | N | +0.44 | +0.16/+0.21/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S2 | F | +0.44 | -0.00/-0.21/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S3 | N | +0.39 | +0.03/+0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S3 | F | +0.37 | -0.04/-0.19/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4a | N | +8.03 | +1.29/+4.52/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4a | F | +4.55 | +0.49/+0.25/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | - |
| S4b | N | +6.83 | -0.02/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4b | F | +9.79 | +0.21/-0.00/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes +0 vs warm-ups) | - |
| S4c | N | +0.89 | -0.14/-0.17/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4c | F | +0.96 | +0.24/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4d | N | +6.49 | +0.15/+0.02/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4d | F | +6.02 | +0.11/+0.12/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4e | N | +20.89 | +0.04/+0.14/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4e | F | +19.45 | +0.21/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S4f | N | +8.60 | +1.26/+4.71/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | nullFrames 0, frames 200, slowestCaptureMs 3284, totalMs 223107, maxOpenMediaImages 0 |
| S4f | F | +8.10 | +0.78/+4.76/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | nullFrames 0, frames 1, slowestCaptureMs 447, maxOpenMediaImages 0 |
| S5 | N | +2.07 | +1.13/-0.13/+0.00/+1.60 | +0 / 0 / +0 | +0/+0/+2 (nodes +1 vs warm-ups) | - |
| S5 | F | +2.03 | +0.92/-0.31/+0.00/+1.59 | +0 / 0 / +0 | +0/+0/+6 (nodes +7 vs warm-ups) | - |
| S6 | N | +1.13 | +0.82/+0.66/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+1 (nodes +1 vs warm-ups) | - |
| S6 | F | +1.07 | +0.81/+0.52/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes -2 vs warm-ups) | - |
| S7 | N | +0.63 | - | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | - |
| S7 | F | +0.43 | - | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | - |
| S8 | N | +0.63 | +0.51/+0.56/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S8 | F | +0.65 | +0.41/+0.40/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S9 | N | +0.48 | +0.10/+0.17/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S9 | F | +0.97 | +0.49/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S10 | N | +0.47 | +0.31/-0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S10 | F | +0.53 | +0.25/+0.30/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S11 | N | +0.56 | +0.12/+0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S11 | F | +0.46 | +0.05/-0.05/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |
| S12 | N | +0.46 | +0.11/+0.02/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes -1 vs warm-ups) | - |
| S12 | F | +0.46 | +0.47/+0.09/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | - |

## Shortest strong reference paths (shark, exited ScriptRuntime instances)

### S7 N: 1 exited runtimes in the dump, dump 26107 ms, analysis 4966 ms

- 1 runtime(s), root THREAD_OBJECT: TermSession$2<local> -> Message.callback -> Timer$PendingCallback.callback -> Timer$$ExternalSyntheticLambda5.f$1 -> JSFunction.declarationScope -> TopLevelScope.associatedValues -> HashMap[] -> ComposeJsErrors$Localizer.format -> Compose$2.receiver -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime

```
THREAD_OBJECT
  jackpal.androidterm.emulatorview.TermSession$2 <local>
  android.os.Message.callback
  org.autojs.autojs.core.looper.Timer$PendingCallback.callback
  org.autojs.autojs.core.looper.Timer$$ExternalSyntheticLambda5.f$1
  org.mozilla.javascript.JSFunction.declarationScope
  org.autojs.autojs.rhino.TopLevelScope.associatedValues
  java.util.HashMap[]
  org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors$Localizer.format
  org.autojs.autojs.runtime.api.augment.compose.Compose$2.receiver
  org.autojs.autojs.runtime.api.augment.compose.AndroidComposeScriptPlatform.runtime
  -> org.autojs.autojs.runtime.ScriptRuntime
```


Raw reports: the .json file with the same name (one report per line under `results`).
