# Bridge gate XQ-DQ72 (5935) 20261024

- Device: Sony XQ-DQ72, API 33, serial QV770340J7
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.nodejs versionCode 292 (1.32.0), commit 133812df
- hidden_api_policy (ROADMAP D9): 1 during each run, before `1`, afterwards `1`
- Run: XQ-DQ72-5935-20261024185203, label mp2-final-smoke, 2026-10-24T10:52:03.979919+00:00 -> 2026-10-24T11:01:38.374041+00:00
- Device state at start: {'batteryLevel': 72, 'powered': ['USB'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture off, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS session objects back to start, system connections <= start + 1, bridge threads back to the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| ND1 Empty script | N | pass | 0 / +0.23 / -0.13 / +0 b+0 / h+0 s+0 / 0 / 94.4 (+1.46) |  |
| ND1 Empty script | F | pass | 0 / +0.23 / -0.11 / +0 b+0 / h+0 s+0 / 0 / 92.7 (+2.96) |  |
| ND5 UI session | N | pass | 0 / +0.14 / -0.73 / +0 b+0 / h+0 s+0 / 0 / 101.7 (+6.09) |  |
| ND5 UI session | F | pass | 0 / +0.23 / +0.00 / +0 b+0 / h+0 s+0 / 0 / 95.5 (-1.12) |  |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the AidlPluginHost connection pools of the Bun and QuickJS clients)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait after a run until they were back

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +0 | 1/1 -> 1/3 | - | 55.6 / 92.9 / 94.4 | - |
| ND1 | F | +0 | 1/1 -> 1/2 | - | 90.1 / 89.8 / 92.7 | - |
| ND5 | N | +0 | 1/1 -> 1/3 | - | 88.7 / 95.6 / 101.7 | - |
| ND5 | F | +0 | 1/1 -> 1/1 | - | 96.7 / 96.6 / 95.5 | - |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +1.60 | -25.24/-0.13/+0.00/+0.00 | +0 / 0 / +0 | - | +82 / +15 / +0 / +0 / +0 | - |
| ND1 | F | +1.86 | +3.64/-0.11/+0.00/+0.00 | +0 / 0 / +0 | - | +60 / +60 / +0 / +0 / +0 | - |
| ND5 | N | +2.31 | +4.69/-0.73/-11.20/+0.00 | +0 / 0 / +0 | - | +75 / +75 / +0 / +0 / +0 | - |
| ND5 | F | +6.04 | +3.74/-0.61/-11.20/-0.00 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
