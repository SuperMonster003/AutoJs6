# Node.js device, sensors and media family: Rhino comparison and device validation

Date: 2026-10-30 (Asia/Taipei). Batch: api-coverage P3.1c (device with Rhino's Device prototype, sensors with Rhino's Sensors, media with Rhino's Media), the last part of P3.1 after P3.1a and P3.1b.

## Runtime behavior

The Node.js Runtime plugin (3250fa46, d.ts 38a6c666, register order 74f6dfa8) builds device and sensors from the shared factories (`__autojs6_create_device`, `__autojs6_create_sensors`) on the main thread and in Workers (CJS, ESM, nested); Workers forward media to the main thread, where they had no media module before. Entries are `autojs6:device`, `autojs6:sensors` and `autojs6:media` (bare names too, `$autojs.<module>` on the main thread).

- Startup snapshot (Q148 (a)): the plugin's device-info module (NodeDeviceProfile) carries Rhino's Build fields (buildDisplay, manufacturer, device, incremental, baseOS, securityPatch, codename), pageSize, the market name, the 17 manufacturers / 3 brands / 9 roms checks by the host's rules (Device / RomUtils over system properties), the whole-screen width / height (short / long side, as Rhino) and the default sensor of each Rhino name and Sensor.TYPE_ name. density is the dpi and buildId is Build.DISPLAY, as in Rhino; getBuildId() stays Build.ID.
- Host routes (host bb01a8323): RuntimeDeviceBridge serves Rhino's device queries (summary, orientation / rotation, screen and power source, network, pointer location, phone state permission, roms.isBackgroundStartGranted, the hardware identifiers); keepScreenOn takes an optional timeout, keepAwake and cancelVibration are new controls, vibrate takes timing arrays; ControlledSensorPolicy accepts Rhino's 12 sensor names and the four delay levels (fastest is clamped to the game level, about 20 ms).
- Capabilities (Q146 (a)): the new `device.identifiers` (getIMEI, getSerial, getAndroidId, getMacAddress, getSharedDeviceId, and the imei / serial getters) is never implied by `device`, in the host manifest, the plugin manifest, the capability catalog and the facade. keepAwake / keepScreenDim / setPointerLocation* / togglePointerLocation need `device.power`; playback needs `media.playback`, scanFile `media.library`.
- sensors (Q147 (a)): the module and the emitters register returns are Rhino EventEmitters (copy-on-write emit, emitSticky, max listeners, newListener / removeListener); register is synchronous over the snapshot (null for a sensor the device lacks, or a no-op emitter and unsupported_sensor with ignoresUnsupportedSensor) and throws ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED without `sensors`, checked before the sensor table (74f6dfa8, suggested by P4.1c because Bun lists no sensors without its grant); change (event, ...values) and accuracy_change; unregister / unregisterAll; getSensor returns a property snapshot; once(type, options) stays Node's single sample, once(name, listener) is Rhino's. getTimer returns a Timer of the running thread (Q147 (a) as decided; Rhino's Sensors.getTimer() returns null, see Q150).
- media (Q149 (a)): Rhino's nine members are in `autojs6:media`, the same functions as `autojs6:compat`'s media.
- Differences kept (the plugin's HOST-API, "设备, 传感器与媒体族的 Rhino 对照"): queries return Promises; imei / serial ask the host on every read (Rhino reads them once); playMusic returns a PlaybackSession and scanFile whether the file was scanned; at most 4 sensor subscriptions at a time (host policy); getSensor gives a snapshot, not android.hardware.Sensor.

## Rhino values and cases

`tools/nodejs/api/device-media` in the Node plugin repository holds 28 cases (device 17, sensors 8, media 3; sensors.getTimer is Node-only and expects a Timer) and 9 undeclared-capability checks. `device_device_media.py compare` runs every case in Rhino and in Node in one session and compares the values there; identifiers, addresses and the summary are compared as FNV-1a hashes, a silent 3 s WAV file serves the media cases, `sensors.change~` waits for an accelerometer event (no movement needed). The undeclared run declares only `device` and `media` and expects ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED with `device.identifiers` (getIMEI, getAndroidId, imei), `device.power` (setPointerLocation, keepScreenDim), `sensors` (register), `media.playback` (playMusic) and `media.library` (scanFile), and a value from isScreenOff.

Desktop comparison (`tools/nodejs/api/test-device-media-rhino.js`, 8 tests, in the plugin's build workflow): snapshot fields and checks; the host routes and capabilities of the queries; keep* timeouts; the vibrate overloads; the sensors EventEmitter; register, delays, events and unregister; the capability error before the sensor lookup and both once forms; media in main threads and Workers with the Worker snapshots.

## Verification

Device: Sony G8441 (Xperia XZ1 Compact), serial `BH900ASK9E`, Android 9 / API 28, arm64-v8a, no Root. The preferred Xiaomi 968e9f18 was in use by P5.2e.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5835), base.apk md5 `5e836c4d7727f7a4a3ef6d4f613f85fe` | 6.8.0 (5935) debug arm64 built from an export of c0b3a686e with the engine files of bb01a8323 (byte-identical) | reinstalled, md5 verified |
| Node.js Runtime plugin | 1.32.0 (322), base.apk md5 `5b3a3812e8728edbeef0261a4bbe9835` | 1.32.0 (333) debug arm64 whose `node_bridge_sources.cpp` is byte-identical to 38a6c666 | reinstalled, md5 verified |

`device_device_media.py verify` against the recorded state reported the same packages and APK md5. The run directory (`/sdcard/Download/aj6-p31c`) was removed. The installed APK carries the facade before 74f6dfa8; the register order change only affects an unknown sensor without the `sensors` capability and is covered by the desktop test.

| Check | Result |
|---|---|
| Rhino | 28 / 28 cases recorded |
| Node main thread (CJS `require`) | 28 passed, 0 failed |
| Node ESM (`import` from a data: URL) | 28 passed, 0 failed |
| Nested Worker (Worker started by a Worker, CJS) | 28 passed, 0 failed |
| Undeclared capabilities | 9 passed |

Device values of this run: pageSize 4096, market name "Xperia XZ1 Compact", isManufacturer of the upper-cased manufacturer true; manufacturers only isSony, brands / roms none; screen on, portrait, rotation 0; 11 of the 14 sensor names present (no ambient_temperature, relative_humidity, temperature); media.music `[true, 3000, true, false, true, true, false, 0, -1]` (playing, duration, position, paused, resumed, seek, stopped, then Rhino's idle 0 / -1).

The first run showed one difference, in the harness: Rhino runs the following cases before it dispatches sensor events, so `sensors.unregisterAll` ended the registration of `sensors.change~` and Rhino timed out. The event case is now the last one; Node received the event in every form.

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-device-media-rhino.js
py tools/nodejs/api/device-media/device_device_media.py state -s <serial> --state state.json
py tools/nodejs/api/device-media/device_device_media.py compare -s <serial> --report report.json
py tools/nodejs/api/device-media/device_device_media.py verify -s <serial> --state state.json
```

`state` records versions and md5 only; keep copies of the installed APKs (`adb shell pm path`, `adb pull`) before installing the test builds.

In the host repository, `node tools/api-coverage/check-runtime-device-media.mjs` (CI "Script API coverage") checks that every host member of the family (146: device 86, sensors 21, media 9, manufacturers / brands / roms 30) has a member in the shared factories and a Node declaration, that the checks follow Device / RomUtils, that the plugin snapshot carries the fields the factory reads, that Workers build device and sensors from the same factories and forward media, Rhino's 12 sensor names in the host and the plugin, the 28 device routes with their capabilities in the catalog, `device.identifiers` never implied, and the HOST-API section. `test_runtime_matrix.py` (test_p3_1c) checks the family's matrix rows.

## Matrix

Host c20e7a36f: pending Node main 825 -> 754, Worker 887 -> 807 (the family 71 / 80 -> 0; all 116 rows bridged in both columns, media through `autojs6:media`). The Bun cells of the family stay in the baseline for P4.1c. Ace's runtime queries 4012 -> 4163 (Ace 05076a9), TS f964495, Ace e74c1e8, documentation 9028c38 / f822e43, Offline Docs 04c53cd.

## Leftovers

- Bun column: P4.1c (Bun's GENERATED copies of the Node factories need one more sync after 74f6dfa8; the baseline's 90 Bun cells; Bun declarations and bun-runtime-smoke.ts).
- Q150: getTimer returns a local Timer as Q147 (a) says, while Rhino's returns null; the recommended option keeps it.
- The host's limit of 4 sensor subscriptions stays (Q147 (a)).
- `check.py --matrix` at HEAD also reports the imgui listClipper cells (772cff4c6, Q122) as new pending cells; they are outside this batch.
