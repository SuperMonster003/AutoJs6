# Memory gate Android SDK built for x86 (5835) 20261022

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5835 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5835-20261022093643, label p4b-before, 2026-10-22T01:36:43.775490+00:00 -> 2026-10-22T01:43:42.130982+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | N | fail | 0 / +0.13 / +0.69 / +0 / +0 / 0 | javaClass instances +80 (vs end of warm-ups, limit +10); dexClassLoader instances +20 (vs end of warm-ups, limit +0); loadedClasses +80 (vs end of warm-ups, limit +10); hostDexLoaders +20 (vs end of warm-ups, limit +0) |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy | F | fail | 0 / +0.11 / +0.42 / +0 / +0 / 0 | javaClass instances +64 (vs end of warm-ups, limit +10); dexClassLoader instances +20 (vs end of warm-ups, limit +0); loadedClasses +64 (vs end of warm-ups, limit +10); hostDexLoaders +20 (vs end of warm-ups, limit +0) |
| S15 web.newInjectableWebView not attached to a window when the script ends | N | pass | 0 / +0.11 / -0.05 / +0 / +0 / 0 |  |
| S15 web.newInjectableWebView not attached to a window when the script ends | F | pass | 0 / +0.10 / -0.20 / +0 / +0 / 0 |  |
| S16 a UI script starting a UI script that ends first | N | fail | 0 / +0.07 / -0.08 / +0 / +0 / 0 | script stat innerRetainedWhileOuterRuns 1 |
| S16 a UI script starting a UI script that ends first | F | pass | 0 / +0.11 / -0.30 / +0 / +0 / 0 |  |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S8 | N | +0.54 | +0.45/+0.69/+0.00/+0.11 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +80 / +80 / +20 / +20 / +0 | - |
| S8 | F | +0.67 | +0.38/+0.42/+0.00/+0.11 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +64 / +64 / +20 / +20 / +0 | - |
| S15 | N | +0.50 | +0.64/-0.05/+0.00/+0.41 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +80 / +80 / +0 / +0 / +0 | webViews 1 |
| S15 | F | +0.53 | +0.50/-0.20/+0.00/+0.40 | +0 / 0 / +0 | +0/+0/-2 (nodes +0 vs warm-ups) | +63 / +63 / +0 / +0 / +0 | webViews 1 |
| S16 | N | +1.09 | +0.80/-0.08/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/-1 (nodes +0 vs warm-ups) | +10 / +10 / +0 / +0 / +0 | innerRetainedWhileOuterRuns 1, innerChecks 1 |
| S16 | F | +1.15 | +0.38/-0.30/+0.00/+0.00 | +0 / 0 / +0 | +0/+0/+3 (nodes +0 vs warm-ups) | +72 / +72 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
