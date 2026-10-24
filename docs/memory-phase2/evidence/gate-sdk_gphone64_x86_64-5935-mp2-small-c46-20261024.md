# Bridge gate sdk_gphone64_x86_64 (5935) 20261024

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5594
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.nodejs versionCode 292 (1.32.0), commit -
- hidden_api_policy (ROADMAP D9): 1 during each run, before `None`, afterwards `None`
- Run: sdk_gphone64_x86_64-5935-20261024103943, label mp2-small-c46, 2026-10-24T02:39:43.093223+00:00 -> 2026-10-24T02:42:36.160682+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS session objects back to start, system connections <= start + 1, bridge threads back to the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| ND7a Web page and action | N | pass | 0 / +0.15 / +0.10 / +0 b+0 / h+0 s+0 / 0 / 92.0 (+5.74) |  |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the AidlPluginHost connection pools of the Bun and QuickJS clients)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait after a run until they were back

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| ND7a | N | +0 | 1/1 -> 1/2 | - | 64.7 / 86.2 / 92.0 | chooserCancelled 1 |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND7a | N | +10.72 | +1.08/+0.10/+0.00/-0.05 | +0 / 0 / +0 | - | +3 / +3 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
