# Memory gate Android SDK built for x86 (5935) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5640
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5935-20261022194456, label q50-after, 2026-10-22T11:35:58.861594+00:00 -> 2026-10-22T11:48:38.656498+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Merged runs: Android_SDK_built_for_x86-5935-20261022193558 (all); Android_SDK_built_for_x86-5935-20261022194456 (S5, S9)
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S5 ui script with button, text and webview | N | pass | 0 / +0.10 / -0.59 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.09 / -0.92 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.15 / -0.01 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.14 / -0.18 / +0 / +0 / 0 |  |
| S13 speech.listen session open when the script ends | N | error | 0 / +0.18 / +0.04 / +0 / +0 / 0 | script errors: JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-normal-0-497e2ce7-57c2-4444-b7a2-da88dee4375d.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-normal-1-fb1ca093-4e7b-43a3-9703-ad9674ca0e7e.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-normal-2-c4b2a9ca-9c3a-4e83-97b7-97a5bd0ddb89.js#5) |
| S13 speech.listen session open when the script ends | F | error | 0 / +0.10 / +0.14 / +0 / +0 / 0 | script errors: JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-force-0-214b27dd-4159-4f88-8d18-59b5d4a95e8b.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-force-1-37c0c374-cb53-41d5-b2cb-f999988158dc.js#5) / JavaScriptException: SpeechError [MODEL_MISSING]: No asr model is installed; install one with speech.models.install() (memory-gate-S13-force-2-9ff4ccb3-3067-4599-9e21-1c6a05779c0d.js#5) |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S5 | N | +1.87 | +0.86/-0.59/+0.00/+1.60 | +0 / 0 / +0 | +0/+0/+2 (nodes +4 vs warm-ups) | +70 / +70 / +0 / +0 / +0 | - |
| S5 | F | +1.95 | +0.65/-0.93/+0.00/+1.60 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +70 / +70 / +0 / +0 / +0 | - |
| S9 | N | +0.52 | +0.09/-0.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+0 (nodes +0 vs warm-ups) | +60 / +60 / +0 / +0 / +0 | - |
| S9 | F | +1.03 | -0.07/-0.18/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +60 / +60 / +0 / +0 / +0 | - |
| S13 | N | +0.52 | +0.11/+0.04/+0.00/+0.00 | +0 / 0 / +0 | +1/+0/-2 (nodes -2 vs warm-ups) | +22 / +22 / +0 / +0 / +0 | - |
| S13 | F | +0.50 | +0.48/+0.14/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +1 / +1 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
