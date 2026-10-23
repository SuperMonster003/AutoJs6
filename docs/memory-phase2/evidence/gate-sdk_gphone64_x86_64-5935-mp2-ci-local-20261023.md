# Bridge gate sdk_gphone64_x86_64 (5935) 20261023

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5654
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.nodejs versionCode 292 (1.32.0), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.bun.runtime versionCode 296 (0.21.0), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.quickjs.runtime versionCode 42 (1.0.4), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.lua.runtime versionCode 78 (0.1.2-rc.2), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.python.runtime versionCode 135 (0.5.5), commit -
- hidden_api_policy (ROADMAP D9): 1 during each run, before `None`, afterwards `None`
- Run: sdk_gphone64_x86_64-5935-20261023212904, label mp2-ci-local, 2026-10-23T13:29:04.445698+00:00 -> 2026-10-23T13:58:49.084736+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during ND8 normal, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS session objects back to start, system connections <= start + 1, bridge threads back to the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| ND1 Empty script | N | pass | 0 / +0.13 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 89.8 (+1.55) |  |
| ND2 Host capabilities through the broker | N | pass | 0 / +0.17 / +0.06 / +0 b+0 / h+0 s+0 / 0 / 94.7 (+0.35) |  |
| ND3 Pushed callbacks | N | pass | 0 / +0.15 / +0.06 / +0 b+0 / h+0 s+0 / 0 / 92.6 (+0.36) |  |
| ND4 Plugin process killed | N | pass | 0 / +0.28 / +0.05 / +0 b-2 / h+0 s-1 / 0 / - |  |
| ND5 UI session | N | pass | 0 / +0.15 / -4.79 / +0 b+0 / h+0 s+0 / 0 / 91.3 (+5.13) |  |
| ND6 Compose | N | pass | 0 / +0.12 / +0.33 / +0 b+0 / h+0 s+0 / 0 / 92.4 (+0.59) |  |
| ND7a Web page and action | N | pass | 0 / +0.19 / -4.48 / +0 b+0 / h+0 s+0 / 0 / 92.0 (+0.44) |  |
| ND7b Web local download | N | pass | 0 / +0.18 / -4.48 / +0 b+0 / h+0 s+0 / 0 / 91.9 (+0.32) |  |
| ND7c Web media session | N | pass | 0 / +0.18 / +0.16 / +0 b+0 / h+0 s+0 / 0 / 89.3 (-0.06) |  |
| ND8 Screen capture | N | pass | 0 / +0.14 / -4.47 / +0 b+0 / h+0 s+0 / 0 / 90.4 (+0.11) |  |
| ND9 Overlay | N | pass | 0 / +0.17 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 91.7 (-0.23) |  |
| ND10 Dialog | N | pass | 0 / +0.25 / -4.40 / +0 b+0 / h+0 s+0 / 0 / 92.0 (+0.22) |  |
| ND11 Image handles | N | pass | 0 / +0.18 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 94.1 (+0.93) |  |
| ND12 Audio recording | N | pass | 0 / +0.15 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 90.6 (+0.52) |  |
| ND13 ColorOS bind refusal loop | N | skipped | skipped | ND13 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| BN1 Empty script | N | pass | 0 / +0.15 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 22.6 (-0.29) |  |
| BN2 Host capabilities through the host client | N | pass | 0 / +0.16 / +0.00 / +0 b+0 / h+0 s+0 / 0 / 20.9 (+0.07) |  |
| BN3 Sensor callbacks | N | pass | 0 / +0.10 / +0.00 / +0 b-1 / h+0 s+0 / 0 / 21.3 (+0.20) |  |
| BN4 Plugin process killed | N | pass | 0 / +0.26 / -0.02 / +0 b+0 / h-1 s-1 / 0 / - |  |
| BN5 ColorOS bind refusal loop | N | skipped | skipped | BN5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| QJ1 Empty script | N | pass | 0 / +0.15 / +0.13 / +0 b+0 / h+0 s+0 / 0 / 23.2 (+0.52) |  |
| QJ2 File source and output | N | pass | 0 / +0.26 / +0.14 / +0 b+0 / h+0 s+0 / 0 / 22.6 (+0.71) |  |
| QJ3 Output callbacks from Promise jobs | N | pass | 0 / +0.15 / -0.01 / +0 b+0 / h+0 s+0 / 0 / 22.8 (+0.47) |  |
| QJ4 Plugin process killed | N | pass | 0 / +0.26 / -0.02 / +0 b+0 / h-1 s-1 / 0 / - |  |
| QJ5 ColorOS bind refusal loop | N | skipped | skipped | QJ5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| LU1 Empty script | N | pass | 0 / +0.18 / +0.16 / +0 b+0 / h+0 s+0 / 0 / 8.4 (+1.06) |  |
| LU2 Host capabilities and module snapshot | N | pass | 0 / +0.17 / +0.14 / +0 b+0 / h+0 s+0 / 0 / 9.7 (+1.24) |  |
| LU3 Output callbacks | N | pass | 0 / +0.15 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 9.2 (+0.86) |  |
| LU4 Plugin process killed | N | pass | 0 / +0.16 / +0.05 / +0 b+0 / h+0 s+0 / 0 / - |  |
| LU5 ColorOS bind refusal loop | N | skipped | skipped | LU5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| PY1 Empty script | N | pass | 0 / +0.14 / +0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY2 Host capabilities and file round trip | N | pass | 0 / +0.15 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY3 Output callbacks | N | pass | 0 / +0.15 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY4 Plugin process killed | N | pass | 0 / +0.18 / -0.02 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY5 ColorOS bind refusal loop | N | skipped | skipped | PY5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the AidlPluginHost connection pools of the Bun and QuickJS clients)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait after a run until they were back

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +0 | 1/1 -> 1/1 | - | 65.9 / 88.3 / 89.8 | - |
| ND2 | N | +0 | 1/1 -> 1/1 | - | 82.9 / 94.3 / 94.7 | deviceInfoKeys 12, pngBytes 275 |
| ND3 | N | +0 | 1/1 -> 1/1 | - | 93.2 / 92.2 / 92.6 | batteryEvents 1, sensorEvents 3 |
| ND4 | N | +0 | 1/1 -> 0/0 | - | 88.7 / - / - | ended by error 22 |
| ND5 | N | +0 | 1/1 -> 1/1 | - | 66.0 / 86.1 / 91.3 | - |
| ND6 | N | +0 | 1/1 -> 1/1 | - | 85.1 / 91.8 / 92.4 | - |
| ND7a | N | +0 | 1/1 -> 1/2 | - | 90.8 / 91.6 / 92.0 | chooserCancelled 1 |
| ND7b | N | +0 | 1/1 -> 1/2 | - | 90.5 / 91.6 / 91.9 | downloadDone 1 |
| ND7c | N | +0 | 1/1 -> 1/2 | - | 90.6 / 89.3 / 89.3 | startAttempts 2 |
| ND8 | N | +0 | 1/1 -> 1/1 | - | 87.0 / 90.3 / 90.4 | - |
| ND9 | N | +0 | 1/1 -> 1/1 | - | 88.7 / 91.9 / 91.7 | - |
| ND10 | N | +0 | 1/1 -> 1/1 | - | 90.7 / 91.8 / 92.0 | - |
| ND11 | N | +0 | 1/1 -> 1/1 | - | 90.6 / 93.2 / 94.1 | ocrDone 0 |
| ND12 | N | +0 | 1/1 -> 1/1 | - | 87.3 / 90.1 / 90.6 | - |
| BN1 | N | +0 | 1/2 -> 1/2 | - | 21.2 / 22.9 / 22.6 | session +0, cleared <= 33 ms |
| BN2 | N | +0 | 1/2 -> 1/2 | - | 20.8 / 20.8 / 20.9 | deviceInfoKeys 8, pixels 16, pngBytes 275; session +0, cleared <= 30 ms |
| BN3 | N | +0 | 1/2 -> 1/2 | - | 21.2 / 21.1 / 21.3 | sensorEvents 5; session +0, cleared <= 12 ms |
| BN4 | N | connectionPool -1 | 1/2 -> 0/1 | - | 21.4 / - / - | ended by error 22; session +0, cleared <= 9 ms |
| QJ1 | N | +0 | 1/2 -> 1/2 | - | 20.9 / 22.6 / 23.2 | session +0, cleared <= 5 ms |
| QJ2 | N | +0 | 1/2 -> 1/2 | - | 21.2 / 21.9 / 22.6 | sourceDataChars 65536, checksum 581538; session +0, cleared <= 5 ms |
| QJ3 | N | +0 | 1/2 -> 1/2 | - | 21.9 / 22.4 / 22.8 | ticks 3; session +0, cleared <= 6 ms |
| QJ4 | N | connectionPool -1 | 1/2 -> 0/1 | - | 21.9 / - / - | ended by error 22; session +0, cleared <= 5 ms |
| LU1 | N | +0 | 0/1 -> 0/1 | - | 6.6 / 7.4 / 8.4 | session +0, cleared <= 8 ms |
| LU2 | N | +0 | 0/1 -> 0/1 | - | 8.5 / 8.4 / 9.7 | sdkInt 35, checksum 685052; session +0, cleared <= 8 ms |
| LU3 | N | +0 | 0/1 -> 0/1 | - | 9.3 / 8.4 / 9.2 | ticks 3; session +0, cleared <= 8 ms |
| LU4 | N | +0 | 0/1 -> 0/1 | - | 9.1 / - / - | ended by error 22; session +0, cleared <= 10 ms |
| PY1 | N | +0 | 0/1 -> 0/1 | - | - / - / - | session +0, cleared <= 9 ms |
| PY2 | N | +0 | 0/1 -> 0/1 | - | - / - / - | battery 100; session +0, cleared <= 9 ms |
| PY3 | N | +0 | 0/1 -> 0/1 | - | - / - / - | ticks 3; session +0, cleared <= 8 ms |
| PY4 | N | +0 | 0/1 -> 0/1 | - | - / - / - | ended by error 22; session +0, cleared <= 11 ms |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +1.73 | +0.36/+0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +23 / +23 / +0 / +0 / +0 | - |
| ND2 | N | +1.76 | +0.52/+0.06/+0.00/+0.02 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND3 | N | +1.48 | +0.56/+0.06/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND4 | N | +1.66 | +0.09/+0.05/+0.00/+0.02 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND5 | N | +10.56 | -3.32/-4.79/+0.00/+0.04 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND6 | N | +7.20 | +0.70/+0.33/+0.00/+0.05 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| ND7a | N | +10.71 | -3.32/-4.48/+0.00/+0.06 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND7b | N | +10.71 | -2.99/-4.48/+0.00/-0.20 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND7c | N | +10.71 | +0.94/+0.17/+0.00/+0.24 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND8 | N | +16.55 | -3.47/-4.47/+0.00/+0.02 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND9 | N | +3.67 | +0.75/+0.04/+0.00/+0.03 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND10 | N | +10.34 | -3.20/-4.40/+0.00/+0.04 | +0 / 0 / +0 | - | +51 / +51 / +0 / +0 / +0 | - |
| ND11 | N | +1.80 | +0.66/+0.04/+0.00/+0.02 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND12 | N | +1.48 | +0.59/+0.04/+0.00/+0.04 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| BN1 | N | +0.05 | -1.46/+0.03/+0.00/+0.02 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| BN2 | N | +0.11 | +0.05/+0.00/+0.00/+0.02 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| BN3 | N | +0.13 | -0.09/+0.00/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN4 | N | +0.13 | +0.01/-0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |
| QJ1 | N | +0.05 | -1.31/+0.02/+0.00/+0.00 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| QJ2 | N | +0.15 | +0.09/+0.15/+0.00/+0.00 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| QJ3 | N | +0.05 | -0.13/-0.01/+0.00/+0.00 | +0 / 0 / +0 | - | +63 / +63 / +0 / +0 / +0 | - |
| QJ4 | N | +0.13 | +0.09/-0.02/+0.00/+0.02 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |
| LU1 | N | +0.65 | +0.17/+0.17/+0.00/+0.01 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| LU2 | N | +0.84 | +0.20/+0.02/+0.00/+0.02 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| LU3 | N | +0.65 | +0.16/+0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +17 / +17 / +0 / +0 / +0 | - |
| LU4 | N | +0.79 | +0.17/+0.06/+0.00/+0.01 | +0 / 0 / +0 | - | +48 / +23 / +0 / +0 / +0 | - |
| PY1 | N | +0.12 | +0.02/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +17 / +17 / +0 / +0 / +0 | - |
| PY2 | N | +0.12 | +0.05/-0.00/+0.00/+0.01 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| PY3 | N | +0.10 | +0.08/-0.02/+0.00/+0.02 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| PY4 | N | +0.14 | +0.04/-0.02/+0.00/+0.03 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
