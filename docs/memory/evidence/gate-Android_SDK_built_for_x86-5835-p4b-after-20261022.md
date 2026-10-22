# Memory gate Android SDK built for x86 (5835) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5835 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5835-20261022094931, label p4b-after, 2026-10-22T01:49:31.700925+00:00 -> 2026-10-22T01:52:32.975043+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | N | pass | 0 / +0.08 / +0.53 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | F | pass | 0 / +0.10 / +0.60 / +0 / +0 / 0 |  |
| S15 web.newInjectableWebView not attached to a window when the script ends | N | pass | 0 / +0.12 / +0.12 / +0 / +0 / 0 |  |
| S15 web.newInjectableWebView not attached to a window when the script ends | F | pass | 0 / +0.10 / -0.27 / +0 / +0 / 0 |  |
| S16 a UI script starting a UI script that ends first | N | pass | 0 / +0.13 / -0.17 / +0 / +0 / 0 |  |
| S16 a UI script starting a UI script that ends first | F | pass | 0 / +0.11 / -0.01 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S8 | N | +0.66 | +0.46/+0.53/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +18 (median +0 per run) / +18 / +0 / +0 / +0 | - |
| S8 | F | +0.61 | +0.33/+0.60/+0.00/+0.18 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +62 (median +0 per run) / +62 / +0 / +0 / +0 | - |
| S15 | N | +0.58 | +0.83/+0.12/+0.00/+0.40 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +80 / +80 / +0 / +0 / +0 | webViews 1 |
| S15 | F | +0.55 | +0.33/-0.27/+0.00/+0.40 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | webViews 1 |
| S16 | N | +1.05 | +0.41/-0.17/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+5 (nodes +1 vs warm-ups) | +72 / +72 / +0 / +0 / +0 | innerRetainedWhileOuterRuns 0, innerChecks 1 |
| S16 | F | +0.98 | +0.39/-0.01/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes -4 vs warm-ups) | +72 / +72 / +0 / +0 / +0 | innerRetainedWhileOuterRuns 0, innerChecks 1 |

Raw reports: the .json file with the same name (one report per line under `results`).
