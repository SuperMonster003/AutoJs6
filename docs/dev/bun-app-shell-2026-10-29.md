# Bun app / shell / shizuku family: Rhino comparison and device validation

Date: 2026-10-29. Batch: api-coverage P4.1a (the Bun column of app, shell, shizuku, their global placements and ShellProcess#; Node counterpart P3.1a, see `runtime-app-shell-2026-10-29.md`).

## Runtime behavior

The Bun host client (Bun Runtime 1253024 / 5e2f9e1) carries the Node.js Runtime's shared app and shell factories (Node 5afd8b02 / 30f05398) unchanged: `tools/host-client/sync-automation.mjs` copies the app factory (`__autojs6_create_app`) and `tools/host-client/sync-shell.mjs` the shell factory (`__autojs6_create_shell`) of `node_bridge_sources.cpp`, with their declarations, into `host-client.mjs` / `host-client.d.mts`; `--check` fails on drift.

- Entries: `host.app` (kill, isDualInstalled, killDual, the D-4 dual-user helpers, getApkInfo, getInstalledPackages, getAppByAlias, sendLocalBroadcastSync), `host.shell` (execCommand, getCommand, fromIntent, kill, setDefaultOptions, isRootAvailable, currentPackage / currentActivity / currentComponent, the key and gesture functions Menu ... Camera, KeyCode, Text, Input, Tap, Swipe, Screencap, SetScreenMetrics), `host.shizuku` (callable, execCommand, getCommand, kill, current*, state and the five state functions) and the ShellProcess members (isAlive, getInputStream / getErrorStream / getOutputStream, toString, the EventEmitter aliases). The Rhino globals (Home, Tap, kill, launchDual, ...) are these members.
- Grants: the factories ask for Node capability names; the client maps shell to `shell.control`, app and app.* to `app.control`, `shell.root` / `shell.shizuku` keep their names (Q89, no new grant). A call whose grants are off rejects `HostCallError` NOT_GRANTED with `missingCapabilities` (the Bun grant names) before reaching the host (Q90).
- Host side: `BunAutomationBridge` adds resolvePackage / getUserProfiles / getInstalledPackages / getApkInfo / getAppByAlias / sendLocalBroadcastSync to `app.control` (host 77d17909a); getInstalledPackages is read in pages (host 720a9eae9).
- Rhino failures as in the Node runtime: a process that cannot start is a failed result with code 1; a Shizuku that cannot be used rejects shizuku.execCommand and the adb route; the key and gesture functions report no result. A Java exception of the host (Rhino `JavaException X`) reaches Bun as `HostCallError`.
- Tap / Swipe scale after SetScreenMetrics to the display size the plugin writes into `device-info.json` (`width` / `height`, DisplayMetrics widthPixels / heightPixels).
- Left: `app.autojs` follows the autojs module (Q93 (2), P3.1b / P4.1b).

## Cases

`tools/api-coverage/device_bun_app_shell.py` runs the Node runtime's cases (`tools/nodejs/api/app-shell/cases.js` of Node 2b8164e8, 40 cases) and 11 cases of its own (the dual-app helpers with a package that does not exist, SetScreenMetrics, Screencap, shell / shizuku currentActivity): 51 cases, first in Rhino, then in a Bun main script and a Bun Worker of one execution, with the grants `app.control`, `shell.control`, `shell.root` and `shell.shizuku` on. A second run (`denied`, 14 cases) turns `shell.control` / `shell.root` / `shell.shizuku` off: the shell-backed entries must reject NOT_GRANTED with the expected `missingCapabilities` and no host call, while app queries and getCommand still answer. The grants are host preferences, set and restored by a host script.

Not run on the device, because they act on the screen or open other apps: launchDualSettings, openDualAppSettings, openDualUrl, startDualActivity, Home and the other navigation keys, Text, Input, Tap, Swipe. They are covered by the Bun unit tests (`tools/host-client/app-shell.test.mjs`: the commands they send, scaling, grants) and the structure check `tools/api-coverage/check-bun-app-shell.mjs` (138 members on the client and in `host-client.d.mts`).

## Verification

Device: Redmi 22120RN86C, serial `bek749scrwv4wo8h`, Android 13 / API 33, arm64-v8a. No Root; Shizuku installed, running and authorized for the host; no dual-app user.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935) debug, md5 `6624bd371b1b64c1d9189f4c8a5397c9` | 6.8.0 (5935) debug arm64 of 67ef4c1a5 + 720a9eae9 (built by P3.1a), md5 `54a5d601ac95074dc540485b5cf4e981` | reinstalled from the recorded copy, md5 `6624bd371b1b64c1d9189f4c8a5397c9` |
| Bun runtime plugin | 0.20.0 (293) debug, md5 `bbfca1f67936a60a0700ec1a12e216eb` | 0.21.0 (296) debug arm64 of Bun 5e2f9e1, md5 `c113e38d3ea4d437386c25d522f73f8a` | reinstalled from the recorded copy, md5 `bbfca1f67936a60a0700ec1a12e216eb` |

Bun grants app.control / shell.control / shell.root / shell.shizuku: absent before, absent after. The run directory `/sdcard/Download/aj6-bun-app-shell` is removed after each run.

| Run | Bun main | Bun Worker |
|---|---|---|
| compare (51 cases, Rhino 51 / 51) | 51 pass (1 Java exception as HostCallError: app.uninstallDual of a missing package) | 51 pass (same) |
| compare, second run | 51 pass | 51 pass |
| denied (14 cases) | 14 pass | 14 pass |

Device values both engines agree on: `shell.isRootAvailable()` false, so execCommand with root and the root key events give the failed results Rhino gives; `shizuku.state` all true and the Shizuku commands, current* and kill answer through Shizuku; `app.isDualInstalled` false, so the D-4 helpers answer false without starting anything.

Two earlier runs were script corrections, not runtime differences: the case helpers (appShellResult / appShellState) were not visible to the Bun cases, and the APK copy for app.getApkInfo was outside the run directory (the host answers getApkInfo of Node and Bun only below the script's working directory). In one of them the Worker's setDefaultOptions case reported code 1 once; the two runs after the corrections passed it.

Commands (host checkout, `--state` a private directory):

```
py -3.12 tools/api-coverage/device_bun_app_shell.py record --serial SERIAL --state STATE
py -3.12 tools/api-coverage/device_bun_app_shell.py install --serial SERIAL --state STATE --apk org.autojs.autojs6=HOST.apk --apk io.github.supermonster003.autojs6.plugin.bun.runtime=BUN.apk
py -3.12 tools/api-coverage/device_bun_app_shell.py grant --serial SERIAL --state STATE --set on --only app.control shell.control shell.root shell.shizuku
py -3.12 tools/api-coverage/device_bun_app_shell.py compare --serial SERIAL --node NODE_CHECKOUT
py -3.12 tools/api-coverage/device_bun_app_shell.py grant --serial SERIAL --state STATE --set off --only shell.control shell.root shell.shizuku
py -3.12 tools/api-coverage/device_bun_app_shell.py denied --serial SERIAL --node NODE_CHECKOUT
py -3.12 tools/api-coverage/device_bun_app_shell.py grant --serial SERIAL --state STATE --set restore --only app.control shell.control shell.root shell.shizuku
py -3.12 tools/api-coverage/device_bun_app_shell.py restore --serial SERIAL --state STATE
```

Desktop: Bun `node --test tools/host-client/*.test.mjs` 107 pass, the 18 `sync-*.mjs --check`, `:app:testDebugUnitTest --tests *BunHostClientModuleTest`; host `node tools/api-coverage/check-bun-app-shell.mjs` (in CI) and `check-bun-automation.mjs`.
