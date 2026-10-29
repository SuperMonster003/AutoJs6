# Node.js system information and engines family: Rhino comparison and device validation

Date: 2026-10-30 (Asia/Taipei). Batch: api-coverage P3.1b (autojs with autojs.version and the global R, app.autojs (Q93 (2)), installer, memory, settings, sysprops, powerManager, notice / notice.channel, workManager, engines (D-14), plugins.extend* / plugins.tools, dialogs.build, and the global system functions currentPackage / currentActivity / currentComponent, exit / stop / isRunning / isStopped / isShuttingDown / notStopped, requiresApi / requiresAutojsVersion, waitForActivity / waitForPackage, setScreenMetrics / WIDTH / HEIGHT, isAutoJs6, and in Workers getClip / setClip). device, sensors and media belong to P3.1c.

## Runtime behavior

The Node.js Runtime plugin (1e69e422, kit pin a1f09612, device fixes d6662d91, Bun guard 08781cee) builds the family from one shared factory (`__autojs6_create_system`) for the main thread and Workers (CJS, ESM, nested). Entries are `autojs6:autojs` (with autojs.version and R; `app.autojs` is the same object), `autojs6:installer`, `autojs6:memory`, `autojs6:settings`, `autojs6:sysprops`, `autojs6:power_manager` (Rhino's powerManager) and `autojs6:system` (Rhino's global system functions, not installed on globalThis), plus `$autojs.<module>`. workManager is the existing `autojs6:tasks` module (Rhino's workManager functions), and notice is the existing `autojs6:notifications` module.

- Host routes (host ea2e5bad4): RuntimeSystemBridge routes autojs / settings / sysprops / memory / installer / notice / power_manager and runs blocking calls (installer, memory.collect, settings.export) on its own threads. engines gains execAutoFile and stopAllAndToast, and engines.all / getEngines / stopAll take the host view (Q131 (a)).
- Capabilities (Q133 (b), the non-recommended option chosen by the maintainer): nine new ones, `autojs`, `autojs.control`, `settings`, `settings.write`, `sysprops`, `memory`, `memory.dump`, `installer`, `installer.mutate`, in the host manifest, the plugin manifest and the capability catalog. The four dotted ones are never implied by their parent. powerManager needs `device` and `device.power`, as device.isIgnoringBatteryOptimizations does. plugins.tools needs `plugin_tools`; the tools of USER_GRANTED providers are listed and run only when the script also declares `plugin_tools.user_granted`, which is never implied (Rhino always lists them).
- dialogs.build returns a local handle whose show() picks the route from its properties and emits Rhino's events; notice.builder / getBuilder return a Java facade handle of NotificationCompat.Builder (Q132 (a)).
- memory.watch is not-portable (runtime-native, Q134 (a), ROADMAP appendix D D-22, policy rule `native-memory-watch`): the Node equivalents are FinalizationRegistry and node:v8 writeHeapSnapshot / getHeapStatistics.
- Runtime shapes that differ from Rhino (Q135, recommended option done first, waiting for the maintainer): results are Promises except the constants (versionCode / versionName / versionDate / name / packageName, autojs.version, WIDTH / HEIGHT, isAutoJs6 as a property); getRootMode returns the enum name ("AUTO_DETECT" ...); getLanguage returns an Intl.Locale; the leaves of the R proxy resolve to resource ids; themeColor is a JSON object of the three colors; workManager is the tasks module; plugins.extend* act on the shared jsox; current* follow their mode (auto tries Shizuku, Root, then accessibility); waitFor* return Promises; installer uses its own InstallerService instance in the host. getClip in Node reads only the system clipboard: from Android 10 it is empty while AutoJs6 is not in the foreground, where Rhino reads it through the focus of a floating window when it has the overlay permission. The full list is in the plugin's HOST-API ("系统信息与引擎族的 Rhino 对照").

## Rhino values and cases

`tools/nodejs/api/system` in the Node plugin repository holds 46 cases (autojs 11, global system functions 11, memory 4, settings 4, sysprops 3, notice 3, plugins 3, installer 2, engines 2, powerManager 1, dialogs 1, app.autojs 1) and 7 undeclared-capability cases. `device_system.py compare` runs every case in Rhino and in Node in one session and compares the values there, because they depend on the device (Root mode, settings, notification channels, installed plugins). The undeclared run declares only `autojs`, `settings`, `memory` and `installer` and expects ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED with the missing `autojs.control`, `settings.write`, `memory.dump`, `installer.mutate`, `sysprops` and `device` / `device.power`.

Desktop comparison (`tools/nodejs/api/test-system-rhino.js`, 10 tests, in the plugin's build workflow): the version comparison against 112 results of the host's io.github.g00fy2.versioncompare 1.5.0 (`version-compare-expected.json`); autojs constants, R, root mode, language and script names; installer values, InstallerError and session events; the Rhino arguments and capabilities of memory / settings / sysprops / power_manager; current* by mode, waitFor*, the stop state and requires*; setScreenMetrics scaling; plugins.extend* on jsox and plugins.tools; notice config, switches, builder and channels; the routes and events of dialogs.build; and the system modules and engines in CJS, ESM and nested Workers through the Worker preload.

## Verification

Device: Xiaomi 23046RP50C, serial `968e9f18`, Android 15 / API 35, arm64-v8a, no Root.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935), base.apk md5 `608c9fa21ca92cbb5485bbd043c0ea17` | 6.8.0 (5935) debug arm64 built from an export of host d4ca4333f (contains ea2e5bad4) | reinstalled from the saved APK, md5 verified |
| Node.js Runtime plugin | 1.32.0 (292), base.apk md5 `399e7a359fea55557a2415801b3b6b3d` | 1.32.0 debug arm64 whose `node_bridge_sources.cpp` is byte-identical to d6662d91 | reinstalled from the saved APK, md5 verified |

`device_system.py verify` against the recorded state reported the same packages and APK md5. The run directory (`/sdcard/Download/aj6-p31b`) was removed. The Node debug build was made in an export with `AUTOJS6_NODE_RUNTIME_SLOT` set to `node24_21`, as in P3.1a (Node roadmap Q7).

| Check | Result |
|---|---|
| Rhino | 46 / 46 cases recorded |
| Node main thread (CJS `require`) | 46 passed, 0 failed |
| Node ESM (`import` from a data: URL) | 46 passed, 0 failed |
| Nested Worker (Worker started by a Worker, CJS) | 46 passed, 0 failed |
| Undeclared capabilities | 7 passed |

Device values of this run: autojs.root `[false, "AUTO_DETECT"]`, autojs.permissions `[false, false, true]`, installer queries `[true, false, true]`, notice.isEnabled `true`, powerManager `[true, false]`, plugins.tools.list 12 tools.

Found and fixed on the device before the final run (Node d6662d91): powerManager queries asked only for `device` while the host requires `device.power` too; Rhino's extendAllBut ignores unknown names and keeps the exclusions for later extendAll calls (Node had thrown RangeError); isAutoJs6 is a property in Rhino (Node had a function); Worker engine lists were not frozen as on the main thread; plugins.tools.list missed the USER_GRANTED providers that Rhino lists, now available with `plugin_tools.user_granted`. The getClip difference is documented (Q135) and not covered by the cases.

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-system-rhino.js
py tools/nodejs/api/system/device_system.py state -s <serial> --state state.json
py tools/nodejs/api/system/device_system.py compare -s <serial> --wait 300 --report report.json
py tools/nodejs/api/system/device_system.py verify -s <serial> --state state.json
```

In the host repository, `node tools/api-coverage/check-runtime-system.mjs` (CI "Script API coverage") checks that every host member of the family (118 members and 20 global rows) has a member in the shared factories and a Node declaration, that Workers build the family from the same factory, that the nine capabilities are in the host manifest, the plugin manifest and the catalog with the dotted ones never implied, that the host routes the system modules and the new engines methods, the policy entries (workManager alias tasks, autojs6:system as a global module, the memory.watch exclusion), and the HOST-API section.

## Leftovers

- Bun column: P4.1b. The Bun client copies the Node accessibility adapters; after Node 08781cee, the host's `check-bun-app-shell.mjs` reports "Bun automation adapters differ from Node sources" until P4.1b syncs them and regenerates host-client.mjs.
- Q135 (runtime shapes, including the getClip background difference) waits for the maintainer.
- device / sensors / media follow P3.1c.
