# SDK 37 packaged examples: sdk_gphone16k_x86_64 (emulator-5600)

- Started 2026-09-15T12:23:56.901091+00:00, finished 2026-09-15T12:25:13.503415+00:00
- API 37, fingerprint `google/sdk_gphone16k_x86_64/emu64xa16k:17/CP31.260623.012/16064790:userdebug/dev-keys`; overall **PASS**
- Plugin org.autojs.plugin.apkbuilder.template 1.0.3+autojs6-6.8.0 built from Runtime Kit `autojs6-runtime-v6.8.0+5280-6da20f96975f5b38d4b7c2a8224f9a52f9c9108f-universal` (template `0e906eee82f78d76...`, host 5280)

| Step | Result | Detail |
| --- | --- | --- |
| host | PASS | host targetSdk 37 versionCode 5280; plugin 1.0.3+autojs6-6.8.0 kit autojs6-runtime-v6.8.0+5280-6da20f96975f5b38d4b7c2a8224f9a52f9c9108f-universal for host 5280 |
| produce | PASS | producer instrumentation passed |
| example a11y | PASS | 11 script checks |
| example ui | PASS | 9 script checks |
| example timer | PASS | 9 script checks |

## a11y (`org.autojs.autojs6.sdk37.a11y`)

- APK compile / target / min 37 / 37 / 24, LAN permission True, SHA-256 `0dc11646d5347bad...`
- Verdict: **PASS**

| Script check | Passed | Detail |
| --- | --- | --- |
| a11y-service-bound | True |  |
| a11y-own-window-front | True |  |
| a11y-root-window | True |  |
| a11y-window-event-after-transition | True |  |
| target-37 | True |  |
| visibility-settings-app | True |  |
| local-network-declared | True |  |
| post-notifications-runtime | True |  |
| exact-alarm-capability | True |  |
| load-dex | True |  |
| broadcast-roundtrip | True |  |

| Runner check | Passed | Detail |
| --- | --- | --- |
| badging | True | package org.autojs.autojs6.sdk37.a11y, target 37, LAN permission True |
| install | True | Performing Streamed Install
Success |
| a11y-service-enabled-by-runner | True | bound on attempt 1 |
| launcher-activity | True | org.autojs.autojs6.sdk37.a11y/org.autojs.autojs.inrt.SplashActivity |
| launch | True | utojs6.sdk37.a11y/org.autojs.autojs.inrt.SplashActivity }
Status: ok
LaunchState: WARM
Activity: org.autojs.autojs6.sdk37.a11y/org.autojs.autojs.inrt.LogActivity
TotalTime: 992
WaitTime: 1007
Complete |

## ui (`org.autojs.autojs6.sdk37.ui`)

- APK compile / target / min 37 / 37 / 24, LAN permission True, SHA-256 `964ef5b7abf55706...`
- Verdict: **PASS**

| Script check | Passed | Detail |
| --- | --- | --- |
| ui-layout-rendered | True |  |
| ui-edge-to-edge | True |  |
| target-37 | True |  |
| visibility-settings-app | True |  |
| local-network-declared | True |  |
| post-notifications-runtime | True |  |
| exact-alarm-capability | True |  |
| load-dex | True |  |
| broadcast-roundtrip | True |  |

| Runner check | Passed | Detail |
| --- | --- | --- |
| badging | True | package org.autojs.autojs6.sdk37.ui, target 37, LAN permission True |
| install | True | Performing Streamed Install
Success |
| launcher-activity | True | org.autojs.autojs6.sdk37.ui/org.autojs.autojs.inrt.SplashActivity |
| launch | True | utojs6.sdk37.ui/org.autojs.autojs.inrt.SplashActivity }
Status: ok
LaunchState: COLD
Activity: org.autojs.autojs6.sdk37.ui/org.autojs.autojs.inrt.SplashActivity
TotalTime: 1072
WaitTime: 1084
Complete |
| ui-predictive-back-finishes-activity | True | backPressed True, exited True, activity gone True |

## timer (`org.autojs.autojs6.sdk37.timer`)

- APK compile / target / min 37 / 37 / 24, LAN permission True, SHA-256 `ce04ffa4a6eb6f59...`
- Verdict: **PASS**

| Script check | Passed | Detail |
| --- | --- | --- |
| countdown-task-scheduled | True |  |
| tts-done | True |  |
| target-37 | True |  |
| visibility-settings-app | True |  |
| local-network-declared | True |  |
| post-notifications-runtime | True |  |
| exact-alarm-capability | True |  |
| load-dex | True |  |
| broadcast-roundtrip | True |  |

| Runner check | Passed | Detail |
| --- | --- | --- |
| badging | True | package org.autojs.autojs6.sdk37.timer, target 37, LAN permission True |
| install | True | Performing Streamed Install
Success |
| launcher-activity | True | org.autojs.autojs6.sdk37.timer/org.autojs.autojs.inrt.SplashActivity |
| launch | True | s6.sdk37.timer/org.autojs.autojs.inrt.SplashActivity }
Status: ok
LaunchState: COLD
Activity: org.autojs.autojs6.sdk37.timer/org.autojs.autojs.inrt.SplashActivity
TotalTime: 721
WaitTime: 739
Complete |
| timed-task-fired | True | {"fired": true, "firedAt": 1789475111007, "target": 37, "api": 37} |
