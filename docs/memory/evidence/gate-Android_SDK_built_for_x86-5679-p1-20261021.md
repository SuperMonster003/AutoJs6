# Memory gate Android SDK built for x86 (5679) 20261021

- Device: Google Android SDK built for x86, API 24, serial emulator-5580
- Host: org.autojs.autojs6 versionCode 5679 (6.8.0), debuggable
- Run: Android_SDK_built_for_x86-5679-20261021082257, label p1, 2026-10-21T00:22:57.843358+00:00 -> 2026-10-21T00:46:43.706345+00:00
- Device state at start: {'batteryLevel': 100, 'powered': ['AC'], 'wakefulness': 'Awake'}
- PROJECT_MEDIA app-op (ROADMAP Q3): `allow` during S4a normal, S4a force, before `default`, afterwards `default`
- Warm-ups 2, iterations 20, poll 10000 ms, force delay 1000 ms, capture on
- Cell: retained / Java heap MB / native heap MB / script threads / Rhino contexts / live engines (ROADMAP appendix A)
- Thresholds (T1): retained 0, Java <= 2 MB, native <= 4 MB (+1 frame for capture), threads and contexts back to start, engines back to start

| Scenario | Mode | Status | Cell | Reasons |
| --- | --- | --- | --- | --- |
| S1 empty script | N | pass | 0 / +0.20 / +0.12 / +0 / +0 / 0 |  |
| S1 empty script | F | fail | 0 / +0.08 / +9.59 / +0 / +0 / 0 | native heap +9.59 MB |
| S2 threads.start x 10 + threads.pool | N | pass | 0 / +0.12 / +0.01 / +0 / +0 / 0 |  |
| S2 threads.start x 10 + threads.pool | F | pass | 0 / +0.10 / -0.21 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | N | pass | 0 / +0.17 / +0.17 / +0 / +0 / 0 |  |
| S3 nested timers on main and child threads | F | fail | 0 / +0.13 / +9.82 / +0 / +0 / 0 | native heap +9.82 MB |
| S4a captureScreen x 20 | N | pass | 0 / +0.05 / +4.51 / +0 / +0 / 0 |  |
| S4a captureScreen x 20 | F | pass | 0 / +0.08 / +8.47 / +0 / +0 / 0 |  |
| S4b images.read + findImage | N | pass | 0 / +0.20 / +0.36 / +0 / +0 / 0 |  |
| S4b images.read + findImage | F | pass | 0 / +0.05 / -0.26 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | N | pass | 0 / +0.20 / +0.95 / +0 / +0 / 0 |  |
| S4c images.copy / clip / resize chain | F | pass | 0 / +0.05 / -0.29 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | N | pass | 0 / +0.20 / +0.42 / +0 / +0 / 0 |  |
| S4d matchTemplate + oneShot | F | pass | 0 / +0.05 / -0.20 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | N | pass | 0 / +0.08 / +0.03 / +0 / +0 / 0 |  |
| S4e threaded copy + findImage + recycle (issue #372 / #200 pattern) | F | pass | 0 / -0.04 / -0.12 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | N | pass | 0 / +0.08 / -0.41 / +0 / +0 / 0 |  |
| S5 ui script with button, text and webview | F | pass | 0 / +0.07 / -0.73 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | N | pass | 0 / +0.07 / +0.98 / +0 / +0 / 0 |  |
| S6 floaty.window + floaty.rawWindow | F | pass | 0 / +0.11 / +0.73 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | N | pass | 0 / +0.17 / +0.18 / +0 / +0 / 0 |  |
| S7 events / key observer / sensors / accessibility events | F | pass | 0 / +0.09 / -0.15 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | N | pass | 0 / +0.08 / +0.55 / +0 / +0 / 0 |  |
| S8 JavaAdapter / interface adapters x 100 + runtime.loadDex | F | fail | 0 / +0.12 / +10.32 / +0 / +0 / 0 | native heap +10.32 MB |
| S9 engines.execScriptFile restarting itself 3 levels | N | pass | 0 / +0.19 / +0.22 / +0 / +0 / 0 |  |
| S9 engines.execScriptFile restarting itself 3 levels | F | pass | 0 / +0.10 / -0.13 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | N | pass | 0 / +0.10 / -0.01 / +0 / +0 / 0 |  |
| S10 http.get + WebSocket on a loopback server | F | pass | 0 / +0.09 / +0.34 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | N | pass | 0 / +0.08 / +0.01 / +0 / +0 / 0 |  |
| S11 device.* reads, vibrate and keepScreenOn | F | pass | 0 / +0.07 / -0.05 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | N | pass | 0 / +0.09 / -0.15 / +0 / +0 / 0 |  |
| S12 plugins.load of an installed official plugin | F | fail | 0 / +0.11 / +9.83 / +0 / +0 / 0 | native heap +9.83 MB |

Raw reports: the .json file with the same name (one report per line under `results`).
