# Bun device, sensors and media family: Rhino comparison and device validation

Date: 2026-10-30. Batch: api-coverage P4.1c, the Bun column of device (86 rows: 25 module members and 61 prototype members of Rhino's Device), sensors (21 prototype members) and media (9 prototype members). The Node columns are P3.1c (`runtime-device-media-2026-10-30.md`).

## Runtime behavior

The Bun host client (Bun Runtime 4c43eed - a0cec4c) carries the Node.js Runtime's shared device and sensors factories. `tools/host-client/sync-device-dialogs.mjs` and `sync-sensors.mjs` copy `__autojs6_create_device` and `__autojs6_create_sensors` of Node 3250fa46 / 186fcade / 74f6dfa8, and `--check` keeps the copies equal. The media namespace is Bun's own, with the same shapes as Node's `autojs6:media`. The same client serves the main thread and Workers.

- Startup snapshot (Q148 (a), Bun Q151 (a) / Q154 (a)). The plugin writes it to `device-info.json` next to the client at the start of every execution (BunRuntimeService, `BunDeviceProfile`, Bun e89ed66), the same values as the Node runtime's NodeDeviceProfile:
  - the Build fields (buildId is Build.DISPLAY, as in Rhino), width / height (the short and long side of the whole display), density (dpi, as in Rhino) and pageSize;
  - getMarketName, the manufacturers / brands / roms checks (system properties, the host's Device / RomUtils rules) and the default sensor of every name Rhino's sensors.register accepts.

  The client offers these members only with the device.info grant (on by default). Without it they are absent, `roms` keeps only isBackgroundStartGranted (a host route), getSensor answers null, and so does register when sensors.control is on.
- Host queries (Q146 (a) / Q148 (a)): summary, orientation / rotation, screen and power source checks, network addresses, pointer location, phone state, the hardware identifiers, keepAwake / keepScreenDim, vibration patterns and cancelVibration. They go to the host's RuntimeDeviceBridge and NodeBridgeModules.deviceRhinoControls through device.control, whose method table is now 41 routes (host c4208e76e).
  - `rotation`, `orientation`, `imei` and `serial` are getters, each read is a new host call.
  - The client copies the module's property descriptors, so building it never reads them.
- Grants (Q152 (a) / Q153 (a), off by default, Plugin Center switches in 11 languages, host bad0bb8bf / c4208e76e):
  - device.identifiers (getIMEI / getSerial / getAndroidId / getMacAddress / getSharedDeviceId, imei / serial; never implied by device.control);
  - media.control (playback: Rhino's 8 music names and Node's play / pause / resume / stop / seekTo / getPlaybackStatus);
  - media.library (scanFile over media_store).

  keepAwake / keepScreenDim / the pointer location switches also need device.power, as keepScreenOn.

  The client maps the Node capabilities the shared factories name to Bun grants. A call whose grants are off rejects NOT_GRANTED with `missingCapabilities` before reaching the host (Q90). This covers device, device.power, device.audio, device.identifiers, sensors, media.playback and media.library.
- sensors (Q147 (a)) is Rhino's event emitter, with on / once / emit / emitSticky / getTimer and the rest. `register(name, delay = sensors.delay.normal)` returns a SensorEventEmitter synchronously, or null for a sensor the device lacks; samples arrive as `change` (event, ...values) and `accuracy_change`. Without sensors.control register throws NOT_GRANTED, checked before the sensor list (Node 74f6dfa8, Bun a0cec4c), which is empty without device.info.
- media (Q149 (a)): playMusic resolves the playback session, scanFile whether the host scanned the file, and the other Rhino names resolve Rhino's results (duration 0, position -1 without an active session). The host stops the player when the execution ends, a Worker's exit / stop included (Q139 (b)).
- runtime-policy (host 8760e52ef): bun.memberCapabilities maps the family members that are not in a method table:
  - the snapshot members and roms -> device.info;
  - the rotation / orientation getters, wakeUpIfNeeded, keepScreenDim and setPointerLocationEnabled / Disabled -> device.control;
  - imei / serial -> device.identifiers;
  - Rhino's media names -> media.control.

  The device.width / height / density aliases to getWidth / getHeight / getDensity are gone, since these are synchronous members now; host be312cd9d drops the member grants that only served them.

## Cases

`tools/api-coverage/device_bun_device_media.py` runs the Node runtime's cases (`tools/nodejs/api/device-media/cases.js` of P3.1c, 28 cases) in Rhino, then in a Bun main script and in a Bun Worker of the same script.

- A case whose id ends with "~" waits for an accelerometer event, which arrives without moving the device, so no case needs the device moved.
- Identifiers, addresses and the summary are compared as FNV-1a hashes, so no value of them is printed.
- Only cases without lasting effects run:
  - the pointer location is set to its current state;
  - wake locks and vibrations are short and cancelled;
  - the music is a silent 3 s WAV and is stopped.

The denied run turns off every family grant but device.control (device.info included) and checks 13 entries:
- NOT_GRANTED with the missing grant for getIMEI / getAndroidId / imei (device.identifiers), setPointerLocation / keepScreenDim (device.power), getAvailableSensors / register (sensors.control), playMusic / isMusicPlaying (media.control) and scanFile (media.library);
- the absent snapshot members, with roms keeping only isBackgroundStartGranted and getSensor answering null;
- answers from isScreenOff and roms.isBackgroundStartGranted.

## Verification

Device: Redmi 22120RN86C, serial `bek749scrwv4wo8h`, Android 13 / API 33, arm64-v8a.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935) debug, md5 `6624bd371b1b64c1d9189f4c8a5397c9` | 6.8.0 (5935) debug arm64 of an export of host HEAD 10:3x (with bad0bb8bf / c4208e76e / bb01a8323 / b2c78ccc5), md5 `108550636e20ffd9e8c2848c9ce604e2` | reinstalled from the recorded copy, md5 `6624bd371b1b64c1d9189f4c8a5397c9` |
| Bun runtime plugin | 0.20.0 (293) debug, md5 `bbfca1f67936a60a0700ec1a12e216eb` | 0.21.0 (296) debug arm64 of an export of Bun a0cec4c, md5 `d716e037265fda8254edfb4edf8ea972` | reinstalled from the recorded copy, md5 `bbfca1f67936a60a0700ec1a12e216eb` |

The 7 Bun grants of the family: absent before, absent after. The run directory `/sdcard/Download/aj6-bun-device-media` is removed after each run.

A first run with Bun d500a2d (md5 `4b4bfea9211ada6dfd25006a8cae035f`), before the register order of Node 74f6dfa8, passed the same compare cases and the denied cases of that time (12, register answering null without either grant).

| Run | Bun main | Bun Worker |
|---|---|---|
| compare (28 cases, Rhino 28 / 28) | 28 pass | 28 pass |
| denied (13 cases, only device.control on) | 13 pass | 13 pass |

Device values both engines agree on:
- pageSize 4096, digest "Redmi (Xiaomi) / earth (22120RN86C) / 13 (33)", market name "Redmi 12C";
- manufacturers.isXiaomi and roms.isMiui true;
- portrait with rotation 0;
- accelerometer, light, linear acceleration, proximity, step counter and rotation vector present among the 14 names the cases ask;
- the silent WAV plays for 3000 ms and pauses, resumes, seeks and stops as in Rhino.

The device.info grant is stored as `device_info` (it predates the dotted grant keys), so the script maps it when it sets grants.

Commands (host checkout, `--state` a private directory):

```
py -3.12 tools/api-coverage/device_bun_device_media.py record --serial SERIAL --state STATE
py -3.12 tools/api-coverage/device_bun_device_media.py install --serial SERIAL --state STATE --apk org.autojs.autojs6=HOST.apk --apk io.github.supermonster003.autojs6.plugin.bun.runtime=BUN.apk
py -3.12 tools/api-coverage/device_bun_device_media.py grant --serial SERIAL --state STATE --set on --only device.info device.control device.power device.identifiers sensors.control media.control media.library
py -3.12 tools/api-coverage/device_bun_device_media.py compare --serial SERIAL
py -3.12 tools/api-coverage/device_bun_device_media.py grant --serial SERIAL --state STATE --set off --only device.info device.power device.identifiers sensors.control media.control media.library
py -3.12 tools/api-coverage/device_bun_device_media.py denied --serial SERIAL
py -3.12 tools/api-coverage/device_bun_device_media.py grant --serial SERIAL --state STATE --set restore --only device.info device.control device.power device.identifiers sensors.control media.control media.library
py -3.12 tools/api-coverage/device_bun_device_media.py restore --serial SERIAL --state STATE
```

Desktop checks:
- Bun:
  - `node --test tools/host-client/*.test.mjs` passes 124 tests, among them device-media.test.mjs 9: the Build fields and the snapshot with and without device.info, the getters, NOT_GRANTED, the sensor list and media routing;
  - `sync-device-dialogs.mjs` / `sync-sensors.mjs --check` against Node HEAD (4d3a18f6);
  - `tsc --strict` of `tools/host-client/types.mts`;
  - `:app:testDebugUnitTest` for BunDeviceProfileTest 4 and BunHostClientModuleTest 7.
- Host:
  - BunAutomationPolicyTest 13 and RuntimeDeviceBridgeCapabilityTest 3;
  - `check-bun-device-media.mjs` (116 rows: device 86, sensors 21, media 9; routes device.control 41, media.control 6, media.library 1);
  - `check-runtime-device-dialogs.mjs`, `check-runtime-device-media.mjs`, `check-bun-sensors.mjs`, `check-bun-automation.mjs`, `check-runtime-bridges.mjs`, `check-bun-system.mjs` and `check-bun-js-tools.mjs`, all in an export of host be312cd9d with Bun a0cec4c and Node 4d3a18f6;
  - `check.py --matrix --strict-arity --docs-support` in the same export: no failure in the family, no policy conflict; it exits 1 only for the imgui ListClipper / listClipper cells and the api/imgui.md block (host 772cff4c6, outside this batch).

## Leftovers

- Grants: with every Bun grant on there are 62 grants plus execution.control, 63 of the plugin's 64. The next grant takes the last slot; a 64th grant would make the host leave execution.control out (P4.1b).
- In the matrix, getIMEI and the other identifier routes, keepAwake and the pointer location switches show device.control. That is the grant whose method table holds them; the second grant (device.identifiers / device.power) is checked per call and listed in host-api.md.
