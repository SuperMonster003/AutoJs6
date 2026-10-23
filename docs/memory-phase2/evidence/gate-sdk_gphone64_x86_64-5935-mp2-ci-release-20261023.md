# Bridge gate sdk_gphone64_x86_64 (5935) 20261023

- Device: Google sdk_gphone64_x86_64, API 35, serial emulator-5654
- Host: org.autojs.autojs6 versionCode 5935 (6.8.0), debuggable
- Plugin: io.github.supermonster003.autojs6.plugin.nodejs versionCode 240 (1.6.0), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.bun.runtime versionCode 118 (0.2.5), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.quickjs.runtime versionCode 15 (1.0.3), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.lua.runtime versionCode 78 (0.1.2-rc.2), commit -
- Plugin: io.github.supermonster003.autojs6.plugin.python.runtime versionCode 111 (0.5.4), commit -
- hidden_api_policy (ROADMAP D9): 1 during each run, before `None`, afterwards `None`
- Run: sdk_gphone64_x86_64-5935-20261023201927, label mp2-ci-release, 2026-10-23T12:19:27.990745+00:00 -> 2026-10-23T12:42:07.639969+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during ND8 normal, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on, meminfo phases
- Cell (docs/memory-phase2/ROADMAP.md appendix A): retained / Java heap MB / native heap MB / script threads, b bridge threads / bindings: h host bridge registries vs start, s system connections to the plugin vs start / live engines / plugin PSS MB at the end (change since measured run 2)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), script threads, engines and bridge registries back to start (the connection pools of the Node, Bun and QuickJS clients may keep 1), Bun / QuickJS session objects back to start, system connections <= start + 1, bridge threads back to the end of the warm-ups (idle workers of the cached binder-call pool get up to 65 s to expire first, Q6); plugin PSS recorded only (R9)

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| ND1 Empty script | N | pass | 0 / +0.14 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 58.4 (+1.65) |  |
| ND2 Host capabilities through the broker | N | error | 0 / +0.20 / +0.03 / +0 b+0 / h+0 s+0 / 0 / 63.3 (+0.84) | script errors: TypeError: image.fromBase64 is not a function /     at memory-gate-ND2-normal-0-6920552b-333b-423b-bc71-bb879cfe4d06.js:22:28 / TypeError: image.fromBase64 is not a function /     at memory-gate-ND2-normal-1-c6f5b301-2144-48fc-9c70-bf8cb3503b07.js:22:28 / TypeError: image.fromBase64 is not a function /     at memory-gate-ND2-normal-2-50b0277e-145b-40b4-9499-5c6c75c5635b.js:22:28 |
| ND3 Pushed callbacks | N | pass | 0 / +0.15 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 61.6 (+0.68) |  |
| ND4 Plugin process killed | N | pass | 0 / +0.28 / +0.04 / +0 b+0 / h+0 s-1 / 0 / - |  |
| ND5 UI session | N | pass | 0 / +0.16 / +0.20 / +0 b+0 / h+0 s+0 / 0 / 60.1 (+1.23) |  |
| ND6 Compose | N | error | 0 / +0.20 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 60.4 (+0.49) | script errors: Error: Cannot find module 'compose' / Require stack: / - /data/user/0/io.github.supermonster003.autojs6.plugin.nodejs/cache/node-plugin-workspaces/fbd5c4655f93be4d24bf8611da868cac/sandbox/memory-gate-ND6-normal-0-63d83daa-8fce-4150-ab8b-dfa49cbe2222.js /     at Module._resolveFilename (node:internal / Error: Cannot find module 'compose' / Require stack: / - /data/user/0/io.github.supermonster003.autojs6.plugin.nodejs/cache/node-plugin-workspaces/b54ec841f62bac6f875150437d931e55/sandbox/memory-gate-ND6-normal-1-bd6483e3-0df7-4875-8de3-a051ffb2a769.js /     at Module._resolveFilename (node:internal / Error: Cannot find module 'compose' / Require stack: / - /data/user/0/io.github.supermonster003.autojs6.plugin.nodejs/cache/node-plugin-workspaces/d106de4bbf1291ab7588c29449932900/sandbox/memory-gate-ND6-normal-2-3668f31f-44f5-4165-9921-8791b15fee30.js /     at Module._resolveFilename (node:internal |
| ND7a Web page and action | N | error | 0 / +0.20 / +0.05 / +0 b+0 / h+0 s+0 / 0 / 61.6 (+0.96) | script errors: TypeError: ui.createView is not a function /     at memory-gate-ND7a-normal-0-91a9fc54-4b02-4c9e-9f5a-e626f35c3f84.js:17:22 /     at Object.<anonymous> (memory-gate-ND7a-normal-0-91a9fc54-4b02-4c9e-9f5a-e626f35c3f84.js:28:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: ui.createView is not a function /     at memory-gate-ND7a-normal-1-cc769573-3787-404f-a99b-647e10b56157.js:17:22 /     at Object.<anonymous> (memory-gate-ND7a-normal-1-cc769573-3787-404f-a99b-647e10b56157.js:28:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: ui.createView is not a function /     at memory-gate-ND7a-normal-2-b19ba014-554c-40f9-b543-9793549672a4.js:17:22 /     at Object.<anonymous> (memory-gate-ND7a-normal-2-b19ba014-554c-40f9-b543-9793549672a4.js:28:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) |
| ND7b Web local download | N | error | 0 / +0.20 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 66.7 (+6.18) | script errors: TypeError: ui.createView is not a function /     at memory-gate-ND7b-normal-0-0282ed48-3426-4964-bf07-eddca073478b.js:17:22 /     at Object.<anonymous> (memory-gate-ND7b-normal-0-0282ed48-3426-4964-bf07-eddca073478b.js:28:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: ui.createView is not a function /     at memory-gate-ND7b-normal-1-5e3cb1c9-7d92-47e4-8a7d-3607117663a1.js:17:22 /     at Object.<anonymous> (memory-gate-ND7b-normal-1-5e3cb1c9-7d92-47e4-8a7d-3607117663a1.js:28:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: ui.createView is not a function /     at memory-gate-ND7b-normal-2-65f38318-06e6-4095-a2ac-198ab8787d1d.js:17:22 /     at Object.<anonymous> (memory-gate-ND7b-normal-2-65f38318-06e6-4095-a2ac-198ab8787d1d.js:28:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) |
| ND7c Web media session | N | error | 0 / +0.19 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 66.8 (+1.07) | script errors: TypeError: ui.createView is not a function /     at memory-gate-ND7c-normal-0-8b54326b-9701-4aed-947c-02f4bd68c40f.js:17:22 /     at Object.<anonymous> (memory-gate-ND7c-normal-0-8b54326b-9701-4aed-947c-02f4bd68c40f.js:37:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: ui.createView is not a function /     at memory-gate-ND7c-normal-1-4c09653b-eca4-430c-8aa2-ea1732d6e16a.js:17:22 /     at Object.<anonymous> (memory-gate-ND7c-normal-1-4c09653b-eca4-430c-8aa2-ea1732d6e16a.js:37:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: ui.createView is not a function /     at memory-gate-ND7c-normal-2-c301fad4-6083-48ef-8b11-f6b11f4897c5.js:17:22 /     at Object.<anonymous> (memory-gate-ND7c-normal-2-c301fad4-6083-48ef-8b11-f6b11f4897c5.js:37:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) |
| ND8 Screen capture | N | error | 0 / +0.22 / -4.47 / +0 b+0 / h+0 s+0 / 0 / 64.6 (+0.59) | script errors: Error: accessibility.screenshot does not accept option "requireExistingPermission"; expected one of format, quality, scale, maxWidth, region, savePath /     at __autojs6_error (/system/bin/app_process64:2984:19) /     at __autojs6_bridge_error (/system/bin/app_process64:5389:19) /     at __autojs6_b |
| ND9 Overlay | N | pass | 0 / +0.17 / -4.92 / +0 b+0 / h+0 s+0 / 0 / 66.4 (+0.50) |  |
| ND10 Dialog | N | pass | 0 / +0.19 / -4.70 / +0 b+0 / h+0 s+0 / 0 / 64.0 (-1.96) |  |
| ND11 Image handles | N | error | 0 / +0.19 / +0.04 / +0 b-1 / h+0 s+0 / 0 / 67.3 (+1.22) | script errors: TypeError: image.fromBase64 is not a function /     at memory-gate-ND11-normal-0-c53f9e3e-999a-459e-ac1c-1e6ebb73956a.js:18:55 /     at Object.<anonymous> (memory-gate-ND11-normal-0-c53f9e3e-999a-459e-ac1c-1e6ebb73956a.js:24:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: image.fromBase64 is not a function /     at memory-gate-ND11-normal-1-14b1c7e3-6f4e-4fd5-900a-444b142a490d.js:18:55 /     at Object.<anonymous> (memory-gate-ND11-normal-1-14b1c7e3-6f4e-4fd5-900a-444b142a490d.js:24:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) / TypeError: image.fromBase64 is not a function /     at memory-gate-ND11-normal-2-1112d81b-6add-4066-be34-dbd6cc2534cb.js:18:55 /     at Object.<anonymous> (memory-gate-ND11-normal-2-1112d81b-6add-4066-be34-dbd6cc2534cb.js:24:3) /     at Module._compile (node:internal/modules/cjs/loader:1929:14) |
| ND12 Audio recording | N | pass | 0 / +0.14 / +0.04 / +0 b+0 / h+0 s+0 / 0 / 64.3 (-0.26) |  |
| ND13 ColorOS bind refusal loop | N | skipped | skipped | ND13 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| BN1 Empty script | N | pass | 0 / +0.13 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 6.7 (-1.02) |  |
| BN2 Host capabilities through the host client | N | skipped | skipped | script: AUTOJS6_HOST_CLIENT_MODULE is not set |
| BN3 Sensor callbacks | N | skipped | skipped | script: AUTOJS6_HOST_CLIENT_MODULE is not set |
| BN4 Plugin process killed | N | pass | 0 / +0.24 / +0.00 / +0 b+0 / h-1 s-1 / 0 / - |  |
| BN5 ColorOS bind refusal loop | N | skipped | skipped | BN5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| QJ1 Empty script | N | pass | 0 / +0.13 / -0.03 / +0 b+0 / h+0 s+0 / 0 / 7.4 (-0.33) |  |
| QJ2 File source and output | N | pass | 0 / +0.22 / +0.02 / +0 b+0 / h+0 s+0 / 0 / 8.8 (+1.00) |  |
| QJ3 Output callbacks from Promise jobs | N | pass | 0 / +0.12 / +0.01 / +0 b+0 / h+0 s+0 / 0 / 8.3 (-0.25) |  |
| QJ4 Plugin process killed | N | pass | 0 / +0.25 / +0.00 / +0 b+0 / h-1 s-1 / 0 / - |  |
| QJ5 ColorOS bind refusal loop | N | skipped | skipped | QJ5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| LU1 Empty script | N | skipped | skipped | the LUA plugin did not answer the probe: LuaRuntimeHostException: LUA_RUNTIME_UNAVAILABLE: No eligible Lua runtime provider is available (rejected: io.github.supermonster003.autojs6.plugin.lua.runtime INFO_RUNTIME_ABI_MISMATCH) |
| LU2 Host capabilities and module snapshot | N | skipped | skipped | the LUA plugin did not answer the probe: LuaRuntimeHostException: LUA_RUNTIME_UNAVAILABLE: No eligible Lua runtime provider is available (rejected: io.github.supermonster003.autojs6.plugin.lua.runtime INFO_RUNTIME_ABI_MISMATCH) |
| LU3 Output callbacks | N | skipped | skipped | the LUA plugin did not answer the probe: LuaRuntimeHostException: LUA_RUNTIME_UNAVAILABLE: No eligible Lua runtime provider is available (rejected: io.github.supermonster003.autojs6.plugin.lua.runtime INFO_RUNTIME_ABI_MISMATCH) |
| LU4 Plugin process killed | N | skipped | skipped | the LUA plugin did not answer the probe: LuaRuntimeHostException: LUA_RUNTIME_UNAVAILABLE: No eligible Lua runtime provider is available (rejected: io.github.supermonster003.autojs6.plugin.lua.runtime INFO_RUNTIME_ABI_MISMATCH) |
| LU5 ColorOS bind refusal loop | N | skipped | skipped | LU5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |
| PY1 Empty script | N | pass | 0 / +0.15 / -0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY2 Host capabilities and file round trip | N | pass | 0 / +0.15 / -0.02 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY3 Output callbacks | N | pass | 0 / +0.15 / -0.02 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY4 Plugin process killed | N | pass | 0 / +0.19 / +0.01 / +0 b+0 / h+0 s+0 / 0 / - |  |
| PY5 ColorOS bind refusal loop | N | skipped | skipped | PY5 runs on ColorOS only (OPD2413); this device is Google sdk_gphone64_x86_64 |

## Bridge detail (docs/memory-phase2/ROADMAP.md T1 (d) / (h) / (i))

- Registries: host bridge tables whose size differs from the start (StaticRegistries.bridgeCounts, and the AidlPluginHost connection pools of the Bun and QuickJS clients)
- Bindings: connections of the host to the plugin / all bound service connections in LoadedApk.mServices, before the first run -> at the end (n/a: unreadable)
- New threads: bridge threads alive at the end that were not alive at the end of the warm-ups
- Plugin PSS MB: all processes of the plugin before the first run / after measured run 2 / at the end (recorded only, R9)
- Script: largest value of each gateStat over all runs; ended by error: runs of an expected failure (ND4 / BN4 / QJ4); session: Bun / QuickJS session objects above the start at the end (VMDebug counts), and the longest wait after a run until they were back

| Scenario | Mode | Registries | Bindings | New threads | Plugin PSS MB | Script |
| --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +0 | 1/1 -> 1/1 | - | 31.3 / 56.8 / 58.4 | - |
| ND2 | N | +0 | 1/1 -> 1/1 | - | 51.6 / 62.5 / 63.3 | deviceInfoKeys 12 |
| ND3 | N | +0 | 1/1 -> 1/1 | - | 61.8 / 60.9 / 61.6 | batteryEvents 1, sensorEvents 3 |
| ND4 | N | +0 | 1/1 -> 0/0 | - | 59.8 / - / - | ended by error 22 |
| ND5 | N | +0 | 1/1 -> 1/1 | - | 33.7 / 58.9 / 60.1 | - |
| ND6 | N | +0 | 1/1 -> 1/1 | - | 53.8 / 59.9 / 60.4 | - |
| ND7a | N | +0 | 1/1 -> 1/1 | - | 59.8 / 60.6 / 61.6 | - |
| ND7b | N | +0 | 1/1 -> 1/1 | - | 59.9 / 60.5 / 66.7 | - |
| ND7c | N | +0 | 1/1 -> 1/1 | - | 64.8 / 65.7 / 66.8 | - |
| ND8 | N | +0 | 1/1 -> 1/1 | - | 64.9 / 64.0 / 64.6 | - |
| ND9 | N | +0 | 1/1 -> 1/1 | - | 62.8 / 65.9 / 66.4 | - |
| ND10 | N | +0 | 1/1 -> 1/1 | - | 65.0 / 66.0 / 64.0 | - |
| ND11 | N | +0 | 1/1 -> 1/1 | - | 62.7 / 66.0 / 67.3 | - |
| ND12 | N | +0 | 1/1 -> 1/1 | - | 65.4 / 64.5 / 64.3 | - |
| BN1 | N | +0 | 1/2 -> 1/2 | - | 6.4 / 7.7 / 6.7 | session +0, cleared <= 71 ms |
| BN4 | N | connectionPool -1 | 1/2 -> 0/1 | - | 7.2 / - / - | ended by error 22; session +0, cleared <= 24 ms |
| QJ1 | N | +0 | 1/2 -> 1/2 | - | 6.1 / 7.7 / 7.4 | session +0, cleared <= 14 ms |
| QJ2 | N | +0 | 1/2 -> 1/2 | - | 7.5 / 7.8 / 8.8 | sourceDataChars 65536, checksum 581538; session +0, cleared <= 16 ms |
| QJ3 | N | +0 | 1/2 -> 1/2 | - | 8.2 / 8.5 / 8.3 | ticks 3; session +0, cleared <= 25 ms |
| QJ4 | N | connectionPool -1 | 1/2 -> 0/1 | - | 8.2 / - / - | ended by error 22; session +0, cleared <= 16 ms |
| PY1 | N | +0 | 0/1 -> 0/1 | - | - / - / - | session +0, cleared <= 19 ms |
| PY2 | N | +0 | 0/1 -> 0/1 | - | - / - / - | battery 100; session +0, cleared <= 12 ms |
| PY3 | N | +0 | 0/1 -> 0/1 | - | - / - / - | ticks 3; session +0, cleared <= 21 ms |
| PY4 | N | +0 | 0/1 -> 0/1 | - | - / - / - | ended by error 22; session +0, cleared <= 18 ms |

## Native detail (ROADMAP P3.1)

- Peak: largest rise of the native heap inside a measured run (sampled every 20 ms, before the after-run GC)
- Meminfo: dumpsys meminfo deltas in MB from the end of the warm-ups to the end: Native Heap Pss / Native Heap Alloc / Graphics / Unknown Pss
- Counters: images not recycled (vs start) / open media images at the end / live Mats (vs end of warm-ups)
- Instances: VMDebug instance count deltas from the start: Bitmap / org.opencv.core.Mat / AccessibilityNodeInfo, then AccessibilityNodeInfo against the end of the warm-ups (S7 / S7b fail above +50, ROADMAP P3.4)
- Classes (ROADMAP P4.4), from the end of the warm-ups to the end: loaded classes (Debug.getLoadedClassCount; S8 fails when they grow in most measured runs, one-time steps do not count) / VMDebug java.lang.Class objects / VMDebug DexClassLoader / loaders registered by runtime.loadDex in the host / VMDebug WebView; `-` where unavailable (VMDebug only on API 24 / 28)

| Scenario | Mode | Peak MB | Meminfo MB | Counters | Instances | Classes | Script stats (max) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND1 | N | +1.75 | -0.95/+0.04/+0.00/+0.02 | +0 / 0 / +0 | - | +19 / +19 / +0 / +0 / +0 | - |
| ND2 | N | +1.73 | +0.36/+0.03/+0.00/+0.02 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND3 | N | +1.72 | +0.31/+0.04/+0.00/+0.01 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND4 | N | +1.51 | +0.22/+0.04/+0.00/+0.04 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND5 | N | +10.56 | +0.44/+0.20/+0.00/+0.05 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND6 | N | +1.61 | +0.33/+0.04/+0.00/+0.02 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| ND7a | N | +1.72 | -0.64/+0.05/+0.00/+0.03 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND7b | N | +1.73 | +0.38/+0.04/+0.00/+0.02 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND7c | N | +2.03 | +0.31/+0.03/+0.00/+0.03 | +0 / 0 / +0 | - | +45 / +45 / +0 / +0 / +0 | - |
| ND8 | N | +10.54 | -3.60/-4.47/+0.00/+0.04 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| ND9 | N | +6.60 | -3.45/-4.92/+0.00/+0.03 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND10 | N | +10.34 | -3.90/-4.70/+0.00/+0.05 | +0 / 0 / +0 | - | +59 / +59 / +0 / +0 / +0 | - |
| ND11 | N | +1.74 | +0.31/+0.04/+0.00/+0.04 | +0 / 0 / +0 | - | +65 / +65 / +0 / +0 / +0 | - |
| ND12 | N | +1.73 | +0.35/+0.04/+0.00/+0.04 | +0 / 0 / +0 | - | +48 / +48 / +0 / +0 / +0 | - |
| BN1 | N | +0.05 | -2.02/+0.01/+0.00/+0.02 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| BN4 | N | +0.13 | +0.12/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +6 / +6 / +0 / +0 / +0 | - |
| QJ1 | N | +0.04 | +0.13/-0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| QJ2 | N | +0.06 | -0.52/+0.02/+0.00/+0.01 | +0 / 0 / +0 | - | +17 / +17 / +0 / +0 / +0 | - |
| QJ3 | N | +0.06 | -0.61/+0.01/+0.00/+0.02 | +0 / 0 / +0 | - | +0 / +0 / +0 / +0 / +0 | - |
| QJ4 | N | +0.14 | -1.23/+0.03/+0.00/+0.02 | +0 / 0 / +0 | - | +72 / +55 / +0 / +0 / +0 | - |
| PY1 | N | +0.11 | +0.02/-0.01/+0.00/+0.02 | +0 / 0 / +0 | - | +66 / +66 / +0 / +0 / +0 | - |
| PY2 | N | +0.12 | +0.03/-0.03/+0.00/+0.01 | +0 / 0 / +0 | - | +63 / +63 / +0 / +0 / +0 | - |
| PY3 | N | +0.11 | +0.50/-0.02/+0.00/+0.02 | +0 / 0 / +0 | - | +49 / +49 / +0 / +0 / +0 | - |
| PY4 | N | +0.14 | +0.01/+0.01/+0.00/+0.01 | +0 / 0 / +0 | - | +72 / +72 / +0 / +0 / +0 | - |

Raw reports: the .json file with the same name (one report per line under `results`).
