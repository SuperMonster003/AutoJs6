# Memory gate Android SDK built for x86 (5835) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5620
- Host: org.autojs.autojs6 versionCode 5835 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5835-20261022134337, label q48-after, 2026-10-22T05:43:37.794349+00:00 -> 2026-10-22T05:46:14.011764+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S5 ui script with button, text and webview | N | pass | 0 / +0.10 / -0.06 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.10 / -0.35 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.16 / +0.16 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.14 / -0.21 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S5 | N | +1.95 | +1.07/-0.06/+0.00/+1.59 | +0 / 0 / +0 | +0/+0/+1 (nodes +0 vs warm-ups) | +70 / +70 / +0 / +0 / +0 | - |
| S5 | F | +2.02 | +0.91/-0.35/+0.00/+1.59 | +0 / 0 / +0 | +0/+0/+1 (nodes +3 vs warm-ups) | +70 / +70 / +0 / +0 / +0 | - |
| S9 | N | +0.51 | +0.08/+0.15/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +77 / +77 / +0 / +0 / +0 | - |
| S9 | F | +0.97 | -0.11/-0.21/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +60 / +60 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
