# Node.js app and shell family: Rhino comparison and device validation

Date: 2026-10-29 (Asia/Taipei). Batch: api-coverage P3.1a (app with the D-4 privileged and dual-user helpers, shell / shizuku with the Q18 prototype rows and D-12 shell.fromIntent, the 19 global key and gesture functions, ShellProcess# (Q31)).

## Runtime behavior

The Node.js Runtime plugin (5afd8b02, device fixes 30f05398) builds app from one shared factory (`__autojs6_create_app`) for the main thread and Workers (CJS, ESM, nested); Workers had no app module before (R8). shell, shizuku and the ShellProcess members come from the shared shell factory (`__autojs6_create_shell`). Entries follow Q6 (a): `autojs6:app` / `autojs6:shell` / `autojs6:shizuku` and `$autojs.<module>`, with no new globals. The Bun runtime syncs both factories (P4.1a).

- D-4 (Q89-Q92, all (a)): the dual-user helpers (launchDual* / openDualAppSetting(s) / startDualActivity / openDualUrl / uninstallDual / isDualInstalled / killDual) and kill run the same commands as Rhino through Shizuku, then Root, then the ordinary shell, and need the same declarations (`shell` plus `shell.shizuku` / `shell.root`) together with the app sub-capability of the non-dual call. A missing declaration rejects with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED` and `missingCapabilities` (the existing Node code for undeclared capabilities, noted at Q90 in appendix C). Without another user the helpers behave as in Rhino (launchDual* / killDual true, isDualInstalled false, the rest silent). startActivity / startService / sendBroadcast accept Rhino's `root` / `shizuku` / `dual` options.
- New read-only host routes (host 257807691): resolvePackage, getUserProfiles, getInstalledPackages, getApkInfo and getAppByAlias need `app.query`, sendLocalBroadcastSync needs `app.activity`; getAccess reports shizukuInstalled / shizukuHasService / shizukuOperational. getInstalledPackages answers in pages of about 200k JSON characters (host 720a9eae9).
- shell.execCommand follows Rhino's `shell(cmd, args?, withRoot?)` and returns `{code, result, error, timedOut, truncated}`; shizuku.isInstalled and the other status functions, shizuku.state and the current* functions return Promises (Q93, recommended option implemented ahead of the decision). The remaining differences from Rhino are listed in the plugin's HOST-API ("应用与 shell 族的 Rhino 对照"): Promise results, shizuku.current* through dumpsys instead of the Shizuku user service's getRunningTasks, the KeyCode name table of API 36, fromIntent / intentToShell quoting the data and omitting a zero `-f`, getApkInfo limited to the script's working directory.
- app.autojs stays pending; it follows the autojs module of P3.1b (Q93 (2)).

## Rhino values and cases

`tools/nodejs/api/app-shell` in the Node plugin repository holds 40 cases (command text 7, command results and shell helpers 14, shizuku 7, app queries and D-4 8, global key functions 3, ShellProcess members 1) and 6 undeclared-capability cases. `device_app_shell.py compare` runs every case in Rhino and in Node in one session and compares the values there, because they depend on the device (Root, Shizuku, other users). A Rhino Java exception matches any Node rejection; on a device without Root only the exit code of `shell(cmd, true)` is compared.

Desktop comparison (`tools/nodejs/api/test-app-shell-rhino.js`, 12 tests, added to the plugin's build workflow): command text and result conversion against Rhino's Shell.getCommandData / AbstractShell.Result, the D-4 routes and their missing capabilities, Shizuku that cannot be used, the getInstalledPackages pages, and the same factory in a nested Worker started with the real Worker preload. The build workflow's step with this test passed 89 / 89.

## Verification

Device: Redmi 22120RN86C, serial `bek749scrwv4wo8h`, Android 13 / API 33, arm64-v8a, no Root, Shizuku 13.5.4 installed.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935), base.apk md5 `6624bd371b1b64c1d9189f4c8a5397c9` | 6.8.0 (5935) debug arm64 built from host 67ef4c1a5 + 720a9eae9, md5 `54a5d601ac95074dc540485b5cf4e981` | reinstalled from the saved APK, md5 verified |
| Node.js Runtime plugin | 1.31.0 (291), md5 `a8811d17a2d96bd7cb2c4bb0a103e9a1` | 1.32.0 (307) debug arm64 built from Node 30f05398, md5 `a85c7b6f91f240b7d07d0315765e2aa8` | reinstalled from the saved APK, md5 verified |
| Bun runtime plugin | 0.20.0 (293), md5 `bbfca1f67936a60a0700ec1a12e216eb` | unchanged | unchanged |

`device_app_shell.py verify` against the recorded state reported the same packages and APK md5. The run directory (`/sdcard/Download/aj6-p31a`) was removed. Shizuku keeps running on the device until it restarts, and the restored host keeps the Shizuku permission granted for this run (it can be revoked in the Shizuku app).

The Node debug build was made in an export with `AUTOJS6_NODE_RUNTIME_SLOT` set to `node24_21`: the Node HEAD carries the Node 26.13 bridge headers of M25.2b, which do not link against the Node 24.21 `libnode.so` in LFS (Node roadmap Q7). `node_bridge_sources.cpp` of the build is byte-identical to 30f05398.

| Check | Shizuku not running | Shizuku running and granted |
|---|---|---|
| Rhino | 40 / 40 cases recorded | 40 / 40 cases recorded |
| Node main thread (CJS `require`) | 40 passed, 0 failed | 40 passed, 0 failed |
| Node ESM (`import` from a data: URL) | 40 passed, 0 failed | 40 passed, 0 failed |
| Nested Worker (Worker started by a Worker, CJS) | 40 passed, 0 failed | 40 passed, 0 failed |
| Undeclared capabilities (only `app.query`) | 6 passed | 6 passed |

For the second column the maintainer started Shizuku on the device and granted it to the debug host; Rhino then reported `{isInstalled, hasService, isRunning, hasPermission, isOperational}` all true, and shizuku.execCommand, the `adb` route of shell.execCommand, KeyCode through Shizuku and shizuku.currentPackage / currentComponent (dumpsys in Node, the Shizuku user service's getRunningTasks in Rhino) gave the same values in both runtimes. The first run with Shizuku showed one difference, hasService: it tells whether the host has bound its Shizuku user service, which the first command through Shizuku starts, so Rhino (run first) read false and Node read true. The state cases now follow the Shizuku commands (Node 2b8164e8); the second column was run in that order, the first one before the change (with Shizuku not running, hasService is false in both runtimes).

Found and fixed on the device before these runs: getInstalledPackages with component lists (`{get: ["activities"]}`) exceeded the Binder transaction size (about 540k JSON characters in one answer), so the host now answers in pages and the facade reads them in order; a process that cannot start (`su` on a device without Root) reports exit code 1, as Rhino's ProcessShell does, instead of the errno; shizuku.execCommand and the `adb` route reject when Shizuku cannot be used, as Rhino's Shizuku shell throws IllegalStateException, while the global key functions still report nothing; Node projects get a wall-clock budget of a few seconds unless `project.json` sets `node.timeoutMs` (the device project sets 300 s); getApkInfo reads files inside the script's working directory only, so the case copies the APK next to each script.

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-app-shell-rhino.js
py tools/nodejs/api/app-shell/device_app_shell.py state -s <serial> --state state.json
py tools/nodejs/api/app-shell/device_app_shell.py compare -s <serial> --wait 300 --report report.json
py tools/nodejs/api/app-shell/device_app_shell.py verify -s <serial> --state state.json
```

In the host repository, `node tools/api-coverage/check-runtime-app-shell.mjs` (CI "Script API coverage") checks that every app / shell / shizuku member, their global functions, the Q18 shizuku prototype rows and the ShellProcess members have a member in the shared factories and a Node declaration, that Workers build app from the same factory, and that the host routes the read-only app methods of D-4.
