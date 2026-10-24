# Bridge gate sdk_gphone64_x86_64 (5935) 20261024

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5720
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.nodejs versionCode 292 (1.32.0), commit 133812df
- Plugin: io.github.supermonster003.autojs6.plugin.bun.runtime versionCode 296 (0.21.0), commit 3056b94
- Plugin: io.github.supermonster003.autojs6.plugin.quickjs.runtime versionCode 42 (1.0.4), commit 0bcee52
- Plugin: io.github.supermonster003.autojs6.plugin.lua.runtime versionCode 102 (0.1.3-rc.2), commit 9ced281
- Plugin: io.github.supermonster003.autojs6.plugin.python.runtime versionCode 135 (0.5.5), commit 5514e82
- hidden_api_policy (ROADMAP D9): 1 during each run, before `None`, afterwards `None`
- Run: sdk_gphone64_x86_64-5935-20261024185304, label mp2-final, 2026-10-24T09:08:18.453407+00:00 -> 2026-10-24T11:25:45.745742+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- Merged runs: sdk_gphone64_x86_64-5935-20261024170818 (all); sdk_gphone64_x86_64-5935-20261024185110 (ND7c); sdk_gphone64_x86_64-5935-20261024185304 (ND1, ND2, ND3, ND4, ND5, ND6, ND7a, ND7b, ND7c, ND8, ND9, ND10, ND11, ND12, ND13, BN1, BN2, BN3, BN4, BN5, QJ1, QJ2, QJ3, QJ4, QJ5, LU1, LU2, LU3, LU4, LU5, PY1, PY2, PY3, PY4, PY5)
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during ND8 force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS session objects back to start, system connections <= start + 1, bridge threads back to the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| ND1 Empty script | N | pass | 0 / +0.15 / +0.06 / +0 b+0 / h+0 s+0 / 0 / 86.6 (+1.15) |  |
| ND1 Empty script | F | pass | 0 / +0.18 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 89.6 (+1.31) |  |
| ND2 Host capabilities through the broker | N | pass | 0 / +0.17 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 92.2 (+4.87) |  |
| ND2 Host capabilities through the broker | F | pass | 0 / +0.25 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 89.9 (+1.52) |  |
| ND3 Pushed callbacks | N | pass | 0 / +0.14 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 90.5 (+0.45) |  |
| ND3 Pushed callbacks | F | pass | 0 / +0.19 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 90.4 (+1.88) |  |
| ND4 Plugin process killed | N | pass | 0 / +0.28 / +0.03 / +0 b-2 / h+0 s-1 / 0 / - |  |
| ND4 Plugin process killed | F | pass | 0 / +0.28 / +0.04 / +0 b+0 / h+0 s-1 / 0 / - |  |
| ND5 UI session | N | pass | 0 / +0.18 / -0.29 / +0 b+0 / h+0 s+0 / 0 / 90.6 (+0.91) |  |
| ND5 UI session | F | pass | 0 / +0.22 / -4.79 / +0 b+0 / h+0 s+0 / 0 / 89.1 (+2.15) |  |
| ND6 Compose | N | pass | 0 / +0.15 / -4.65 / +0 b+0 / h+0 s+0 / 0 / 91.8 (+0.81) |  |
| ND6 Compose | F | pass | 0 / +0.21 / -4.61 / +0 b+0 / h+0 s+0 / 0 / 89.4 (+1.96) |  |
| ND7a Web page and action | N | pass | 0 / +0.19 / -4.48 / +0 b+0 / h+0 s+0 / 0 / 92.4 (+1.25) |  |
| ND7a Web page and action | F | pass | 0 / +0.25 / -4.46 / +0 b+0 / h+0 s+0 / 0 / 89.4 (+1.91) |  |
| ND7b Web local download | N | pass | 0 / +0.18 / +0.10 / +0 b+0 / h+0 s+0 / 0 / 92.6 (+2.00) |  |
| ND7b Web local download | F | pass | 0 / +0.23 / -4.46 / +0 b+0 / h+0 s+0 / 0 / 89.2 (+1.24) |  |
| ND7c Web media session | N | pass | 0 / +0.17 / -4.43 / +0 b+0 / h+0 s+0 / 0 / 90.0 (+7.03) |  |
| ND7c Web media session | F | pass | 0 / +0.24 / +0.17 / +0 b+0 / h+0 s+0 / 0 / 89.4 (+1.13) |  |
| ND8 Screen capture | N | pass | 0 / +0.15 / -4.51 / +0 b+0 / h+0 s+0 / 0 / 90.6 (+0.25) |  |
| ND8 Screen capture | F | pass | 0 / +0.23 / -4.51 / +0 b+0 / h+0 s+0 / 0 / 90.2 (+1.84) |  |
| ND9 Overlay | N | pass | 0 / +0.16 / -4.92 / +0 b+0 / h+0 s+0 / 0 / 92.1 (+0.07) |  |
| ND9 Overlay | F | pass | 0 / +0.22 / +0.05 / +0 b+0 / h+0 s+0 / 0 / 89.5 (+1.46) |  |
| ND10 Dialog | N | pass | 0 / +0.15 / +0.07 / +0 b+0 / h+0 s+0 / 0 / 92.6 (+0.59) |  |
| ND10 Dialog | F | pass | 0 / +0.36 / -3.72 / +0 b+0 / h+0 s+0 / 0 / 90.0 (+1.80) |  |
| ND11 Image handles | N | pass | 0 / +0.18 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 94.7 (+1.07) |  |
| ND11 Image handles | F | pass | 0 / +0.22 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 90.1 (+1.07) |  |
| ND12 Audio recording | N | pass | 0 / +0.14 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 91.1 (+0.29) |  |
| ND12 Audio recording | F | pass | 0 / +0.20 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 89.8 (+1.29) |  |
| ND13 ColorOS bind refusal loop | N | skipped | skipped | ND13 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| ND13 ColorOS bind refusal loop | F | skipped | skipped | ND13 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| BN1 Empty script | N | pass | 0 / +0.14 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 22.3 (-0.43) |  |
| BN1 Empty script | F | pass | 0 / +0.15 / +0.00 / +0 b+0 / h+0 s+0 / 0 / 22.5 (-0.24) |  |
| BN2 Host capabilities through the host client | N | pass | 0 / +0.16 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 22.2 (+1.37) |  |
| BN2 Host capabilities through the host client | F | pass | 0 / +0.16 / +0.00 / +0 b+0 / h+0 s+0 / 0 / 21.7 (+0.95) |  |
| BN3 Sensor callbacks | N | pass | 0 / +0.13 / +0.01 / +0 b-1 / h+0 s+0 / 0 / 21.2 (+0.03) |  |
| BN3 Sensor callbacks | F | pass | 0 / +0.14 / -0.01 / +0 b+0 / h+0 s+0 / 0 / 21.7 (-0.03) |  |
| BN4 Plugin process killed | N | pass | 0 / +0.26 / +0.00 / +0 b+0 / h-1 s-1 / 0 / - |  |
| BN4 Plugin process killed | F | pass | 0 / +0.24 / -0.01 / +0 b+0 / h-1 s-1 / 0 / - |  |
| BN5 ColorOS bind refusal loop | N | skipped | skipped | BN5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| BN5 ColorOS bind refusal loop | F | skipped | skipped | BN5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| QJ1 Empty script | N | pass | 0 / +0.15 / +0.13 / +0 b+0 / h+0 s+0 / 0 / 23.1 (+0.55) |  |
| QJ1 Empty script | F | pass | 0 / +0.13 / -0.01 / +0 b+0 / h+0 s+0 / 0 / 23.5 (+0.60) |  |
| QJ2 File source and output | N | pass | 0 / +0.28 / +0.09 / +0 b+0 / h+0 s+0 / 0 / 22.4 (+0.74) |  |
| QJ2 File source and output | F | pass | 0 / +0.24 / -0.00 / +0 b+0 / h+0 s+0 / 0 / 22.4 (+0.72) |  |
| QJ3 Output callbacks from Promise jobs | N | pass | 0 / +0.13 / -0.01 / +0 b+0 / h+0 s+0 / 0 / 22.6 (+0.40) |  |
| QJ3 Output callbacks from Promise jobs | F | pass | 0 / +0.16 / -0.01 / +0 b+0 / h+0 s+0 / 0 / 23.0 (+0.63) |  |
| QJ4 Plugin process killed | N | pass | 0 / +0.26 / +0.01 / +0 b+0 / h-1 s-1 / 0 / - |  |
| QJ4 Plugin process killed | F | pass | 0 / +0.21 / -0.02 / +0 b+0 / h-1 s-1 / 0 / - |  |
| QJ5 ColorOS bind refusal loop | N | skipped | skipped | QJ5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| QJ5 ColorOS bind refusal loop | F | skipped | skipped | QJ5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| LU1 Empty script | N | pass | 0 / +0.09 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 22.5 (+0.98) |  |
| LU1 Empty script | F | pass | 0 / +0.18 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 22.5 (+0.71) |  |
| LU2 Host capabilities and module snapshot | N | pass | 0 / +0.21 / +0.18 / +0 b+0 / h+0 s+0 / 0 / 22.6 (+0.41) |  |
| LU2 Host capabilities and module snapshot | F | pass | 0 / +0.17 / -0.01 / +0 b+0 / h+0 s+0 / 0 / 22.7 (-0.10) |  |
| LU3 Output callbacks | N | pass | 0 / +0.15 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 23.5 (+0.60) |  |
| LU3 Output callbacks | F | pass | 0 / +0.19 / -0.00 / +0 b+0 / h+0 s+0 / 0 / 23.2 (+0.09) |  |
| LU4 Plugin process killed | N | pass | 0 / +0.25 / +0.04 / +0 b+0 / h+0 s+0 / 0 / - |  |
| LU4 Plugin process killed | F | pass | 0 / +0.23 / +0.04 / +0 b+0 / h+0 s+0 / 0 / - |  |
| LU5 ColorOS bind refusal loop | N | skipped | skipped | LU5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| LU5 ColorOS bind refusal loop | F | skipped | skipped | LU5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| PY1 Empty script | N | pass | 0 / +0.16 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY1 Empty script | F | pass | 0 / +0.15 / -0.02 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY2 Host capabilities and file round trip | N | pass | 0 / +0.15 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY2 Host capabilities and file round trip | F | pass | 0 / +0.15 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY3 Output callbacks | N | pass | 0 / +0.14 / -0.02 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY3 Output callbacks | F | pass | 0 / +0.19 / -0.03 / +0 b-1 / h+0 s+0 / 0 / - |  |
| PY4 Plugin process killed | N | pass | 0 / +0.19 / +0.00 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY4 Plugin process killed | F | pass | 0 / +0.18 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY5 ColorOS bind refusal loop | N | skipped | skipped | PY5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| PY5 ColorOS bind refusal loop | F | skipped | skipped | PY5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the AidlPluginHost connection pools of the Bun and QuickJS clients)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait after a run until they were back

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +0 | 1/1 -> 1/1 | - | 65.0 / 85.5 / 86.6 | - |
| ND1 | F | +0 | 1/1 -> 1/1 | - | 84.6 / 88.3 / 89.6 | - |
| ND2 | N | +0 | 1/1 -> 1/1 | - | 80.4 / 87.4 / 92.2 | deviceInfoKeys 12, pngBytes 275 |
| ND2 | F | +0 | 1/1 -> 1/1 | - | 88.1 / 88.4 / 89.9 | deviceInfoKeys 12, pngBytes 275 |
| ND3 | N | +0 | 1/1 -> 1/1 | - | 91.1 / 90.0 / 90.5 | batteryEvents 1, sensorEvents 3 |
| ND3 | F | +0 | 1/1 -> 1/1 | - | 87.9 / 88.5 / 90.4 | sensorEvents 3 |
| ND4 | N | +0 | 1/1 -> 0/0 | - | 88.7 / - / - | ended by error 22 |
| ND4 | F | +0 | 1/1 -> 0/0 | - | 87.8 / - / - | ended by error 22 |
| ND5 | N | +0 | 1/1 -> 1/1 | - | 65.7 / 89.7 / 90.6 | - |
| ND5 | F | +0 | 1/1 -> 1/1 | - | 65.5 / 87.0 / 89.1 | - |
| ND6 | N | +0 | 1/1 -> 1/1 | - | 84.5 / 91.0 / 91.8 | - |
| ND6 | F | +0 | 1/1 -> 1/1 | - | 84.5 / 87.4 / 89.4 | - |
| ND7a | N | +0 | 1/1 -> 1/2 | - | 90.5 / 91.1 / 92.4 | chooserCancelled 1 |
| ND7a | F | +0 | 1/1 -> 1/2 | - | 87.0 / 87.5 / 89.4 | - |
| ND7b | N | +0 | 1/1 -> 1/2 | - | 90.8 / 90.6 / 92.6 | downloadDone 1 |
| ND7b | F | +0 | 1/1 -> 1/2 | - | 87.2 / 87.9 / 89.2 | downloadDone 1 |
| ND7c | N | +0 | 1/1 -> 1/2 | - | 65.1 / 83.0 / 90.0 | startAttempts 2 |
| ND7c | F | +0 | 1/1 -> 1/2 | - | 87.2 / 88.2 / 89.4 | startAttempts 2 |
| ND8 | N | +0 | 1/1 -> 1/1 | - | 88.5 / 90.4 / 90.6 | - |
| ND8 | F | +0 | 1/1 -> 1/1 | - | 87.4 / 88.4 / 90.2 | - |
| ND9 | N | +0 | 1/1 -> 1/1 | - | 89.1 / 92.1 / 92.1 | - |
| ND9 | F | +0 | 1/1 -> 1/1 | - | 87.6 / 88.1 / 89.5 | - |
| ND10 | N | +0 | 1/1 -> 1/1 | - | 89.0 / 92.0 / 92.6 | - |
| ND10 | F | +0 | 1/1 -> 1/1 | - | 87.5 / 88.2 / 90.0 | - |
| ND11 | N | +0 | 1/1 -> 1/1 | - | 91.1 / 93.6 / 94.7 | ocrDone 0 |
| ND11 | F | +0 | 1/1 -> 1/1 | - | 87.5 / 89.0 / 90.1 | ocrDone 0 |
| ND12 | N | +0 | 1/1 -> 1/1 | - | 87.7 / 90.8 / 91.1 | - |
| ND12 | F | +0 | 1/1 -> 1/1 | - | 87.9 / 88.5 / 89.8 | - |
| BN1 | N | +0 | 1/2 -> 1/2 | - | 21.2 / 22.7 / 22.3 | session +0, cleared <= 12 ms |
| BN1 | F | +0 | 1/2 -> 1/2 | - | 21.3 / 22.7 / 22.5 | session +0, cleared <= 8 ms |
| BN2 | N | +0 | 1/2 -> 1/2 | - | 20.8 / 20.9 / 22.2 | deviceInfoKeys 8, pixels 16, pngBytes 275; session +0, cleared <= 13 ms |
| BN2 | F | +0 | 1/2 -> 1/2 | - | 20.8 / 20.7 / 21.7 | deviceInfoKeys 8, pixels 16, pngBytes 275; session +0, cleared <= 10 ms |
| BN3 | N | +0 | 1/2 -> 1/2 | - | 21.3 / 21.1 / 21.2 | sensorEvents 5; session +0, cleared <= 9 ms |
| BN3 | F | +0 | 1/2 -> 1/2 | - | 21.3 / 21.8 / 21.7 | sensorEvents 5; session +0, cleared <= 10 ms |
| BN4 | N | connectionPool -1 | 1/2 -> 0/1 | - | 21.4 / - / - | ended by error 22; session +0, cleared <= 8 ms |
| BN4 | F | connectionPool -1 | 1/2 -> 0/1 | - | 21.5 / - / - | ended by error 22; session +0, cleared <= 9 ms |
| QJ1 | N | +0 | 1/2 -> 1/2 | - | 20.9 / 22.6 / 23.1 | session +0, cleared <= 5 ms |
| QJ1 | F | +0 | 1/2 -> 1/2 | - | 21.0 / 22.9 / 23.5 | session +0, cleared <= 4 ms |
| QJ2 | N | +0 | 1/2 -> 1/2 | - | 21.1 / 21.7 / 22.4 | sourceDataChars 65536, checksum 581538; session +0, cleared <= 5 ms |
| QJ2 | F | +0 | 1/2 -> 1/2 | - | 21.3 / 21.7 / 22.4 | sourceDataChars 65536, checksum 581538; session +0, cleared <= 4 ms |
| QJ3 | N | +0 | 1/2 -> 1/2 | - | 21.7 / 22.3 / 22.6 | ticks 3; session +0, cleared <= 5 ms |
| QJ3 | F | +0 | 1/2 -> 1/2 | - | 21.9 / 22.4 / 23.0 | ticks 3; session +0, cleared <= 4 ms |
| QJ4 | N | connectionPool -1 | 1/2 -> 0/1 | - | 21.7 / - / - | ended by error 22; session +0, cleared <= 6 ms |
| QJ4 | F | connectionPool -1 | 1/2 -> 0/1 | - | 22.0 / - / - | ended by error 22; session +0, cleared <= 7 ms |
| LU1 | N | +0 | 0/1 -> 0/1 | - | 22.8 / 21.5 / 22.5 | session +0, cleared <= 8 ms |
| LU1 | F | +0 | 0/1 -> 0/1 | - | 23.0 / 21.8 / 22.5 | session +0, cleared <= 9 ms |
| LU2 | N | +0 | 0/1 -> 0/1 | - | 22.5 / 22.2 / 22.6 | sdkInt 35, checksum 685052; session +0, cleared <= 9 ms |
| LU2 | F | +0 | 0/1 -> 0/1 | - | 22.6 / 22.8 / 22.7 | sdkInt 35, checksum 685052; session +0, cleared <= 9 ms |
| LU3 | N | +0 | 0/1 -> 0/1 | - | 22.7 / 22.9 / 23.5 | ticks 3; session +0, cleared <= 8 ms |
| LU3 | F | +0 | 0/1 -> 0/1 | - | 22.8 / 23.1 / 23.2 | ticks 3; session +0, cleared <= 9 ms |
| LU4 | N | +0 | 0/1 -> 0/1 | - | 23.4 / - / - | ended by error 22; session +0, cleared <= 8 ms |
| LU4 | F | +0 | 0/1 -> 0/1 | - | 23.3 / - / - | ended by error 22; session +0, cleared <= 8 ms |
| PY1 | N | +0 | 0/1 -> 0/1 | - | - / - / - | session +0, cleared <= 7 ms |
| PY1 | F | +0 | 0/1 -> 0/1 | - | - / - / - | session +0, cleared <= 9 ms |
| PY2 | N | +0 | 0/1 -> 0/1 | - | - / - / - | battery 100; session +0, cleared <= 8 ms |
| PY2 | F | +0 | 0/1 -> 0/1 | - | - / - / - | battery 100; session +0, cleared <= 9 ms |
| PY3 | N | +0 | 0/1 -> 0/1 | - | - / - / - | ticks 3; session +0, cleared <= 9 ms |
| PY3 | F | +0 | 0/1 -> 0/1 | - | - / - / - | ticks 3; session +0, cleared <= 29 ms |
| PY4 | N | +0 | 0/1 -> 0/1 | - | - / - / - | ended by error 22; session +0, cleared <= 10 ms |
| PY4 | F | +0 | 0/1 -> 0/1 | - | - / - / - | ended by error 22; session +0, cleared <= 10 ms |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +1.73 | -0.66/+0.06/+0.00/+0.02 | +0 / 0 / +0 | - | +19 / +19 / +0 / +0 / +0 | - |
| ND1 | F | +1.75 | +0.32/+0.05/+0.00/+0.02 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND2 | N | +1.48 | +0.36/+0.03/+0.00/+0.04 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND2 | F | +1.73 | +0.04/+0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND3 | N | +1.63 | +0.53/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND3 | F | +1.58 | +0.16/+0.04/+0.00/+0.02 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND4 | N | +1.50 | +0.22/+0.04/+0.00/+0.04 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND4 | F | +1.72 | +0.09/+0.04/+0.00/+0.02 | +0 / 0 / +0 | - | +54 / +54 / +0 / +0 / +0 | - |
| ND5 | N | +10.54 | -3.47/-4.81/+0.00/+0.04 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND5 | F | +10.56 | -3.55/-4.79/+0.00/+0.04 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND6 | N | +7.20 | -3.72/-4.65/+0.00/+0.03 | +0 / 0 / +0 | - | +47 / +47 / +0 / +0 / +0 | - |
| ND6 | F | +10.58 | -3.77/-4.61/+0.00/+0.05 | +0 / 0 / +0 | - | +47 / +47 / +0 / +0 / +0 | - |
| ND7a | N | +10.71 | -3.25/-4.48/+0.00/-0.17 | +0 / 0 / +0 | - | +50 / +50 / +0 / +0 / +0 | - |
| ND7a | F | +10.70 | -3.76/-4.46/+0.00/+0.11 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND7b | N | +10.71 | +0.91/+0.11/+0.00/-0.07 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND7b | F | +10.71 | -3.77/-4.46/+0.00/-0.40 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND7c | N | +10.71 | -3.00/-4.43/+0.00/-0.12 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND7c | F | +10.71 | +0.50/+0.17/+0.00/+0.16 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND8 | N | +16.58 | -3.27/-4.51/+0.00/+0.02 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND8 | F | +16.57 | -3.52/-4.51/+0.00/+0.02 | +0 / 0 / +0 | - | +4 / +4 / +0 / +0 / +0 | - |
| ND9 | N | +10.05 | -3.19/-4.92/+0.00/+0.03 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND9 | F | +1.74 | +0.37/+0.05/+0.00/+0.02 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND10 | N | +2.28 | +0.36/+0.07/+0.00/+0.03 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND10 | F | +7.25 | -2.65/-3.72/+0.00/+0.07 | +0 / 0 / +0 | - | +54 / +54 / +0 / +0 / +0 | - |
| ND11 | N | +1.86 | +0.62/+0.04/+0.00/+0.04 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND11 | F | +1.80 | +0.16/+0.05/+0.00/+0.03 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND12 | N | +1.73 | +0.48/+0.04/+0.00/+0.03 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND12 | F | +1.74 | +0.31/+0.02/+0.00/+0.03 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| BN1 | N | +0.05 | -1.92/+0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +17 / +17 / +0 / +0 / +0 | - |
| BN1 | F | +0.05 | -0.06/+0.01/+0.00/+0.00 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| BN2 | N | +0.12 | +0.16/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +17 / +17 / +0 / +0 / +0 | - |
| BN2 | F | +0.12 | +0.01/+0.00/+0.00/+0.02 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| BN3 | N | +0.14 | -0.01/+0.00/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| BN3 | F | +0.14 | -0.23/-0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| BN4 | N | +0.14 | -0.95/-0.00/+0.00/+0.01 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |
| BN4 | F | +0.12 | -0.45/-0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +55 / +55 / +0 / +0 / +0 | - |
| QJ1 | N | +0.04 | -1.33/+0.13/+0.00/+0.02 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| QJ1 | F | +0.05 | +0.02/-0.01/+0.00/+0.03 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| QJ2 | N | +0.06 | -1.15/+0.09/+0.00/+0.02 | +0 / 0 / +0 | - | +131 / +131 / +0 / +0 / +0 | - |
| QJ2 | F | +0.06 | -1.05/-0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| QJ3 | N | +0.05 | -0.82/-0.01/+0.00/+0.00 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| QJ3 | F | +0.05 | +0.25/-0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| QJ4 | N | +0.12 | +0.10/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +88 / +88 / +0 / +0 / +0 | - |
| QJ4 | F | +0.12 | +0.02/-0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |
| LU1 | N | +0.75 | +0.14/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| LU1 | F | +0.75 | +0.17/+0.01/+0.00/+0.00 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| LU2 | N | +1.04 | +0.18/+0.17/+0.00/+0.01 | +0 / 0 / +0 | - | +82 / +82 / +0 / +0 / +0 | - |
| LU2 | F | +0.95 | +0.18/-0.00/+0.00/+0.01 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| LU3 | N | +0.76 | +0.17/+0.02/+0.00/+0.00 | +0 / 0 / +0 | - | +20 / +20 / +0 / +0 / +0 | - |
| LU3 | F | +0.77 | +0.64/-0.00/+0.00/+0.00 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| LU4 | N | +0.89 | +0.11/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +130 / +130 / +0 / +0 / +0 | - |
| LU4 | F | +0.89 | +0.16/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +71 / +71 / +0 / +0 / +0 | - |
| PY1 | N | +0.10 | +0.07/+0.00/+0.00/+0.02 | +0 / 0 / +0 | - | +63 / +63 / +0 / +0 / +0 | - |
| PY1 | F | +0.11 | -0.01/-0.02/+0.00/+0.00 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| PY2 | N | +0.12 | +0.11/-0.01/+0.00/+0.02 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| PY2 | F | +0.11 | +0.02/+0.00/+0.00/+0.02 | +0 / 0 / +0 | - | +51 / +51 / +0 / +0 / +0 | - |
| PY3 | N | +0.10 | +0.05/-0.02/+0.00/+0.02 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| PY3 | F | +0.10 | +0.17/-0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +51 / +51 / +0 / +0 / +0 | - |
| PY4 | N | +0.14 | +0.09/+0.00/+0.00/+0.00 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |
| PY4 | F | +0.14 | +0.05/-0.00/+0.00/+0.01 | +0 / 0 / +0 | - | +55 / +55 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
