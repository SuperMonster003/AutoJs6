# Memory gate Android SDK built for x86 (5835) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5584
- Host: org.autojs.autojs6 versionCode 5835 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5835-20261022134532, label p6, 2026-10-22T04:21:48.046732+00:00 -> 2026-10-22T05:45:55.051250+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Merged runs: Android_SDK_built_for_x86-5835-20261022122148 (all); Android_SDK_built_for_x86-5835-20261022132801 (S7, S7b); Android_SDK_built_for_x86-5835-20261022134532 (S7b)
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.08 / +0.24 / +0 / +0 / 0 |  |
| S1 empty script | F | pass | 0 / +0.09 / +0.19 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.14 / +0.11 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | F | pass | 0 / +0.11 / -0.00 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.12 / +0.18 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | F | pass | 0 / +0.09 / -0.26 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | N | pass | 0 / +0.07 / +8.40 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | F | pass | 0 / +0.10 / +12.39 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.10 / +0.11 / +0 / +0 / 0 |  |
| S4b images.read + findImage | F | pass | 0 / +0.08 / -0.20 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | N | pass | 0 / +0.08 / +0.39 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | F | pass | 0 / +0.06 / +0.14 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | N | pass | 0 / +0.10 / +0.03 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | F | pass | 0 / +0.09 / -0.06 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | pass | 0 / +0.12 / +0.07 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | pass | 0 / +0.10 / +0.16 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | N | pass | 0 / +0.02 / +4.83 / +0 / +0 / 0 |  |
| S4f captureScreen x 200 without recycle (issue #204) | F | pass | 0 / +0.03 / +0.78 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.07 / -0.00 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.09 / -0.27 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.11 / +0.99 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | F | pass | 0 / +0.10 / +0.71 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.06 / -1.22 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | F | pass | 0 / +0.05 / -0.88 / +0 / +0 / 0 |  |
| S7b className(...).find() loop on the accessibility tree | N | pass | 0 / +0.10 / +0.00 / +0 / +0 / 0 |  |
| S7b className(...).find() loop on the accessibility tree | F | pass | 0 / +0.06 / -1.08 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | N | pass | 0 / +0.11 / +0.73 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | F | pass | 0 / +0.10 / +0.35 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.17 / +0.18 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.14 / +0.02 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.11 / -0.06 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | F | pass | 0 / +0.10 / +0.40 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | N | pass | 0 / +0.08 / +0.10 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | F | pass | 0 / +0.06 / -0.28 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | N | pass | 0 / +0.07 / +0.05 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | F | pass | 0 / +0.06 / -0.20 / +0 / +0 / 0 |  |
| S13 speech.listen session open when the script ends | N | error | 20 / +27.53 / +0.42 / +0 / +0 / 0 | retained runtimes 20/20; java heap +27.53 MB; script errors: JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-normal-0-212b061a-4e32-43c3-b6da-da7612140402.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-normal-1-bf5311b5-2d89-45bb-a100-15526d91598f.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-normal-2-e6c0d362-281b-4945-a78b-cd6509fefc64.js#5) |
| S13 speech.listen session open when the script ends | F | error | 20 / +27.40 / +0.49 / +0 / +0 / 0 | retained runtimes 20/20; java heap +27.40 MB; script errors: JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-force-0-0bf6c649-07e4-423c-9b5d-7ca5567fb9de.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-force-1-b3a3ba63-1dae-4ccb-8930-44761dda384b.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-force-2-933ab095-2e27-497a-8484-88561d810ba8.js#5) |
| S14 console.show with exitOnClose, then the script ends | N | pass | 0 / -0.31 / +0.28 / +0 / +0 / 0 |  |
| S14 console.show with exitOnClose, then the script ends | F | pass | 0 / -0.31 / +0.17 / +0 / +0 / 0 |  |
| S15 web.newInjectableWebView not attached to a window when the script ends | N | pass | 0 / +0.09 / -0.17 / +0 / +0 / 0 |  |
| S15 web.newInjectableWebView not attached to a window when the script ends | F | pass | 0 / +0.07 / -0.31 / +0 / +0 / 0 |  |
| S16 a UI script starting a UI script that ends first | N | pass | 0 / +0.11 / +0.03 / +0 / +0 / 0 |  |
| S16 a UI script starting a UI script that ends first | F | pass | 0 / +0.11 / -0.18 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | N | +0.42 | +0.11/+0.24/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +22 / +22 / +0 / +0 / +0 | - |
| S1 | F | +0.54 | +0.12/+0.19/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S2 | N | +0.53 | +0.21/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +68 / +68 / +0 / +0 / +0 | - |
| S2 | F | +0.52 | +0.06/-0.00/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S3 | N | +0.48 | +0.11/+0.18/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +80 / +80 / +0 / +0 / +0 | - |
| S3 | F | +0.38 | -0.09/-0.26/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4a | N | +8.78 | +1.13/+8.40/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S4a | F | +8.52 | +0.69/+12.39/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +2 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4b | N | +6.84 | +0.30/+0.11/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4b | F | +8.82 | -0.04/-0.20/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4c | N | +0.87 | +0.57/+0.39/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S4c | F | +1.15 | +0.30/+0.14/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S4d | N | +6.27 | +0.20/+0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4d | F | +6.78 | +0.04/-0.06/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4e | N | +21.56 | +0.11/+0.07/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4e | F | +18.79 | +0.17/+0.16/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S4f | N | +8.35 | +0.96/+4.83/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | nullFrames 0, frames 200, slowestCaptureMs 3106, totalMs 252266, maxOpenMediaImages 0 |
| S4f | F | +4.54 | +0.85/+0.78/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +19 / +19 / +0 / +0 / +0 | nullFrames 0, frames 1, slowestCaptureMs 447, maxOpenMediaImages 0 |
| S5 | N | +2.04 | +1.13/-0.00/+0.00/+1.59 | +0 / 0 / +0 | +0/+0/+3 (nodes +0 vs warm-ups) | +11 / +11 / +0 / +0 / +0 | - |
| S5 | F | +1.86 | +1.14/-0.27/+0.00/+1.59 | +0 / 0 / +0 | +0/+0/+1 (nodes -2 vs warm-ups) | +73 / +73 / +0 / +0 / +0 | - |
| S6 | N | +0.98 | +1.06/+0.99/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +73 / +73 / +0 / +0 / +0 | - |
| S6 | F | +1.04 | +0.91/+0.71/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +73 / +73 / +0 / +0 / +0 | - |
| S7 | N | +0.52 | - | +0 / 0 / +0 | -65/+0/+0 (nodes +0 vs warm-ups) | +0 / +0 / +0 / +0 / +0 | - |
| S7 | F | +0.52 | - | +0 / 0 / +0 | +0/+0/-22 (nodes -22 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S7b | N | +0.46 | - | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | nodes 360 |
| S7b | F | +0.45 | - | +0 / 0 / +0 | -1/+0/-2 (nodes +0 vs warm-ups) | +5 / +5 / +0 / +0 / +0 | nodes 396 |
| S8 | N | +0.65 | +0.33/+0.73/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +62 (median +0 per run) / +62 / +0 / +0 / +0 | - |
| S8 | F | +0.65 | +0.22/+0.36/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +62 (median +0 per run) / +62 / +0 / +0 / +0 | - |
| S9 | N | +0.66 | +0.07/+0.18/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +82 / +82 / +0 / +0 / +0 | - |
| S9 | F | +0.98 | +0.01/+0.02/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S10 | N | +0.46 | +0.19/-0.06/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +2 / +2 / +0 / +0 / +0 | - |
| S10 | F | +0.42 | +0.30/+0.40/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +64 / +64 / +0 / +0 / +0 | - |
| S11 | N | +0.46 | +0.07/+0.10/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +18 / +18 / +0 / +0 / +0 | - |
| S11 | F | +0.47 | -0.11/-0.28/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S12 | N | +0.48 | +0.02/+0.05/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S12 | F | +0.44 | +0.17/-0.20/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S13 | N | +0.48 | +0.50/+0.42/+0.00/+0.00 | +0 / 0 / +0 | +1/+0/+48 (nodes +50 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S13 | F | +0.46 | +0.84/+0.49/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |
| S14 | N | +1.60 | +0.98/+0.28/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S14 | F | +1.45 | +0.96/+0.17/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | - |
| S15 | N | +0.48 | +0.59/-0.17/+0.00/+0.40 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | webViews 1 |
| S15 | F | +0.65 | +0.61/-0.31/+0.00/+0.40 | +0 / 0 / +0 | -1/+0/-3 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | webViews 1 |
| S16 | N | +1.03 | +0.59/+0.03/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+4 (nodes -1 vs warm-ups) | +72 / +72 / +0 / +0 / +0 | innerRetainedWhileOuterRuns 0, innerChecks 1 |
| S16 | F | +1.03 | +0.53/-0.18/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes -4 vs warm-ups) | +72 / +72 / +0 / +0 / +0 | innerRetainedWhileOuterRuns 0, innerChecks 1 |

## Shortest strong reference paths (shark, exited ScriptRuntime instances)

### S13 N: 22 exited runtimes in the dump, dump 15799 ms, analysis 6820 ms

- 22 runtime(s), root JNI_GLOBAL: SpeechSessionCallbackBinder.gate -> SpeechEventGate.sink -> SpeechSessionRunner.controller -> SpeechSession$Controller.session -> SpeechSession.scriptRuntime -> ScriptRuntime

```
JNI_GLOBAL
  org.autojs.autojs.core.plugin.speech.SpeechSessionCallbackBinder.gate
  org.autojs.autojs.core.plugin.speech.SpeechEventGate.sink
  org.autojs.autojs.runtime.api.augment.speech.SpeechSessionRunner.controller
  org.autojs.autojs.runtime.api.augment.speech.SpeechSession$Controller.session
  org.autojs.autojs.runtime.api.augment.speech.SpeechSession.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```

### S13 F: 22 exited runtimes in the dump, dump 20651 ms, analysis 9114 ms

- 22 runtime(s), root JNI_GLOBAL: SpeechSessionCallbackBinder.gate -> SpeechEventGate.sink -> SpeechSessionRunner.controller -> SpeechSession$Controller.session -> SpeechSession.scriptRuntime -> ScriptRuntime

```
JNI_GLOBAL
  org.autojs.autojs.core.plugin.speech.SpeechSessionCallbackBinder.gate
  org.autojs.autojs.core.plugin.speech.SpeechEventGate.sink
  org.autojs.autojs.runtime.api.augment.speech.SpeechSessionRunner.controller
  org.autojs.autojs.runtime.api.augment.speech.SpeechSession$Controller.session
  org.autojs.autojs.runtime.api.augment.speech.SpeechSession.scriptRuntime
  -> org.autojs.autojs.runtime.ScriptRuntime
```


Raw reports: the .json file with the same name (one report per line under `results`).
