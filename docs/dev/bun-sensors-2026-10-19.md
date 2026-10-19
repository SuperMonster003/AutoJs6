# Bun sensors and Node.js Worker sensor delivery

Date: 2026-10-19. Scope: the existing Node sensor contract in Bun, main threads and CJS/ESM/nested Workers, with actual hardware and packaged applications. No acoustic tone, playback, microphone recording or camera capture test was used.

## Delivered behavior

- Shared sensors factory: getAvailableSensors, once, subscribe, policy and immutable event snapshots. The three types remain accelerometer, gyroscope and light.
- Bun host.sensors uses the independent default-off sensors.control grant, also declared explicitly in packaged bun.hostCapabilities. Five native routes are covered by the capability catalog. Invalid local inputs and sample timeouts use Bun INVALID_REQUEST/TIMEOUT errors.
- Node bare and autojs6:sensors imports work in main threads, CommonJS, ESM and nested Workers. Main threads preserve negotiated pushed events. Workers and Bun use bounded queue polling.
- once uses a temporary subscription and releases it on sample, timeout or abort. The subscription signal remains active after registration. Repeated close, close while registration is pending, polling failures and Worker termination release their listeners. Late Worker registration replies and abandoned Bun registration results are cleaned up.
- Host sensor registration and destruction are serialized so queued registration cannot resurrect listeners after execution destruction. Sensor unsubscribe checks its own subscription map and cannot close an image or another resource kind.
- Documentation, Node/Bun source declarations, shared declarations, Ace assets and default popup checks are synchronized. Release notes and settings are localized; offline source provenance is pinned.

## Hardware results and limits

| Device | System | Samples verified | Explicit hardware restriction |
| --- | --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | Android 13 / API 33, arm64, 4096-byte pages | Accelerometer, main/Bun/CJS/ESM/nested Workers and packaged apps | No gyroscope. The listed Xiaomi camera_light_Sensor is rejected by vendor SensorService; the tests assert its permission-denied result and retain it as unavailable, never as a successful light sample. |
| Xiaomi 23046RP50C, 968e9f18 | Android 15 / API 35, arm64, 4096-byte pages | Accelerometer, gyroscope and light across all the same execution modes | None encountered for these three types. |

Sampling intervals are Android hints, not exact timing guarantees. The queue is bounded and the shared execution limit is four subscriptions, including once. Node main and Workers share that allowance. Bun retains the existing 1024-call execution budget and fixed request/response/RPC limits; its polling is not an unlimited-duration stream. The full Rhino SensorManager type surface and Java objects are outside this portable API.

## Validation

134 accepted physical JUnit cases, counted only from final successful runs. Initial failures and superseded receipts remain in ignored evidence and are not counted as passes. Redmi initially exposed the vendor light-registration restriction; the final test records that specific hardware denial while still requiring accelerometer sampling and lifecycle checks. All three real sensor types are required on Xiaomi.

| Device | Suite / phase | Accepted cases | Seconds |
| --- | --- | --- | --- |
| redmi | host | 6 | 11.936 |
| redmi | node-host | 8 | 2.976 |
| redmi | node | 34 | 113.435 |
| redmi | bun | 13 | 28.38 |
| redmi | produce | 1 | 38.79 |
| redmi | packaged | 1 | 18.278 |
| xiaomi | host | 6 | 8.364 |
| xiaomi | node-host | 8 | 1.587 |
| xiaomi | node | 34 | 39.744 |
| xiaomi | bun | 13 | 25.093 |
| xiaomi | produce | 1 | 16.095 |
| xiaomi | packaged | 1 | 32.26 |
| xiaomi | ace | 8 | 161.49 |

Each device produced an actual Bun APK and an actual Node Worker APK using the paired APK Builder. Both were installed and run twice with an owned-package force-stop between runs. Their package identity and embedded host build 5581 were checked. Four generated APK signatures were verified as independent of the host certificate; every runtime DEX matched the real Runtime Kit template. Sensor values came from Android hardware. Ace's eight physical cases include sensor completion/options/subscription popups, normal default API completion and JSOX; editor preferences and rotation were restored.

Other accepted checks:

- 5109 JVM cases discovered across host, Node, Bun, Ace, Builder and Offline Docs: 5083 passed, 26 existing skips, zero failures/errors.
- 47 Bun SDK tests; 13 shared Node facade/real desktop Worker tests, plus the existing Worker host-module regression. The five focused sensor facade cases cover timeout, abort, pending close, push mode and failed polling; two Worker cases cover CJS/ESM/nested imports and late termination cleanup.
- Node and Bun Python suites: 2 and 38 tests. API coverage extractor tests: 8.
- TypeScript 5.1.3 and 6.0.3 smoke programs, 70 synchronized declarations, 428 Ace semantic queries, and the focused five-route sensor coverage gate passed.
- Current NDK ARM64 C++ syntax check, Node/Bun/Ace lint, Bun native payload integrity, host/plugin/template builds and debug/release offline asset verification passed. Offline packages contain 220 verified documentation files. Markdown generation and normalization checks passed for all 164 API modules.

### Broader audit still open

The additional full Augmentable coverage scan queried 1527 paths across 1356 explicit exports. It still reports five pre-existing declaration/completion gaps: shell.checkAccess, shell.isRootAvailable, shell.setDefaultOptions, shell.spawn and shizuku.spawn. Their host sources match the pre-task baseline exactly; this batch did not modify the corresponding Rhino declarations. The full coverage gate therefore remains failing on those five paths. The exported evidence includes global-surfaces.json and preexisting-shell-gap.json. These names are recorded as follow-up work, rather than counted as sensor coverage failures or silently suppressed.

## Versions and provenance

Host 6.8.0 / Android build 5581; Node Runtime 1.17.0 / build 265; Bun Runtime 0.6.0 / build 265; declarations 4.64.0; Ace 2.26.0 / build 301; Offline Docs 6.8.21 / build 175; APK Builder 1.2.11 / build 86. Documentation revision 142.

Runtime Kit: `autojs6-runtime-v6.8.0+5581-d2d4b8044de84c884f83f381dd07561c99a8e280-arm64-v8a`. Source basis is host commit d2d4b8044de84c884f83f381dd07561c99a8e280 plus this delivery's working changes; this basis is not claimed as the final content commit. APK hashes, installation receipts and DEX hashes identify the tested candidates. Node's bridge was rebuilt; Node/V8 versions and Bun's native runtime payload/support matrix were unchanged. Documentation source `7840c32f9e353002de9b54e6a52082e51f14a3a3` is pinned in both Offline Docs provenance files. All changes are local; nothing was pushed, released or uploaded to a plugin catalog.

## Reproduction and retained evidence

Run `node tools/api-coverage/check-bun-sensors.mjs`, the Node tools/nodejs/api/test-sensors-facade.js and test-worker-sensors.js tests, Bun tools/host-client/sensors.test.mjs, declaration smoke checks and Ace tools/ace-lsp/verify-runtime-api-coverage.mjs. Android classes: BunSensorsDeviceTest, NodeSensorsBridgeInstrumentationTest and BunSensorsPackagedDeviceTest. The packaged producer consumes the exact SDK SHA-256 and requires the real matching Runtime Kit.

Ignored `.bak/bun-sensors-20261019/` retains baseline diffs, final APKs, SDK/hardware/popup JSON, logs, certificates, counts and per-install SHA-256 receipts. Original APK/settings restoration and owned-fixture cleanup are recorded in each device's restoration.json and owned-file-cleanup.json; finalization.json verifies restored hashes before deleting the temporary original APK copies. The five pre-existing host draft files remain uncommitted.


Follow-up: the [shell declaration/completion delivery](shell-api-coverage-2026-10-19.md) subsequently closed the five broader-audit gaps recorded above. The expanded global gate now passes; the original sensor validation results and limitations remain as recorded.
