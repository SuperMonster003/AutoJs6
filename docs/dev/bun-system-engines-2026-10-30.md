# Bun system information and engines family: Rhino comparison and device validation

Date: 2026-10-30. Batch: api-coverage P4.1b (the Bun column of autojs with autojs.version and R, installer, memory, settings, sysprops, powerManager, notice and notice.channel, workManager, engines, plugins.extend* / tools, dialogs.build, app.autojs and the global system functions; Node counterpart P3.1b, see `runtime-system-engines-2026-10-30.md`; device / sensors / media are P4.1c).

## Runtime behavior

The Bun host client (Bun Runtime def82bc - 0207cd7) carries the Node.js Runtime's shared system factory (`__autojs6_create_system` of Node 1e69e422 - 6c2fae29) and its engines helpers: `tools/host-client/sync-system.mjs` copies them with their declarations into `host-client.mjs` / `host-client.d.mts` (`createSystemFamily`), `--check` fails on drift. The Bun differences are two text replacements (the one-level spawn message, the `bun-` invocation prefix) and the declaration types.

- Entries: `host.autojs` (with `version` and `R`), `host.installer`, `host.memory`, `host.settings`, `host.sysprops`, `host.powerManager`, `host.engines`, `host.plugins` (extend / extendAll / extendAllBut, tools), `host.dialogs.build`, `host.app.autojs`, the notice extras and `channel` on `host.notifications`, `host.tasks` for workManager, and the global system functions on `host.global` (current*, waitFor*, exit / stop and the state functions, requiresApi / requiresAutojsVersion, setScreenMetrics, WIDTH / HEIGHT, isAutoJs6). `host.global.install(target?)` puts them and R on globalThis (or target) as Rhino's globals, for the calling thread (Q161 (a)): accessors, assigning one replaces it, names the target already has are left alone; it returns the installed names and is not enumerable on host.global. Without it they stay on host.global only, as the Bun plugin injects no AutoJs6 globals.
- Rhino's shapes (Q135 (b), synced from Node 6c2fae29): `autojs.getRootMode()` resolves with Rhino's RootMode object (key, description, name(), ordinal(), toString(), one frozen object per mode) that setRootMode takes; an omitted notice channel id is the config's defaultChannelId, else the script's file name (such as "main.js"), or "script_channel" when useScriptNameAsDefaultChannelId is false.
- Grants (Q136 (a) / Q137 (a) / Q162 (a), off by default, Plugin Center switches in 11 languages): autojs.control / autojs.manage, settings.control / settings.write, sysprops.control, memory.control / memory.dump, installer.control / installer.mutate, engines.control / engines.exec, plugintools.control. plugintools.control lists and calls the tools of every provider, USER_GRANTED ones included as in Rhino and Node (Q162 (a) removed the second grant plugintools.usergranted of Q137 (a); host b2c78ccc5). notice reuses notifications.post / notifications.settings, powerManager device.control / device.power, workManager tasks.control, settings.export files.control. The factory asks for Node capability names; the client maps them to these grants, and a call whose grants are off rejects `HostCallError` NOT_GRANTED with `missingCapabilities` before the host is reached (Q90). The host maps each grant to the Node routes of `RuntimeSystemBridge` / `NodeBridgeModules` (`BunAutomationBridge`, host 39e240a14 - cb3c277ce); installer and plugin tools calls may wait up to 905 s, the others 120 s.
- The plugin tools grant IDs have no underscore: the Bun plugin's request parser accepts only dotted lowercase capability IDs (`[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+`) and rejects the whole execution otherwise. The first device run used `plugin_tools.control` / `plugin_tools.user_granted`, and every Bun script failed with "Bun Runtime returned a mismatched execution ID" while they were on; host a268b1526 / Bun 1383a79 renamed them, and `BunAutomationPolicyTest` checks every system family grant (and execution.control) against the pattern.
- Engine record (Q138 (a)): `engines.myEngine()` and the synchronous autojs fields come from the host info snapshot the plugin writes (`AUTOJS6_HOST_INFO_FILE`) plus, when engines.control is on, one host call at client creation for this execution's record (cwd, spawnDepth). engines.execScript* start host executions (Q131 (a)), one level deep.
- exit / stop (Q139 (b)) end the whole execution. A main thread ends the process (exit(error) prints the error and exits with code 1, which fails the execution). A Worker asks the host through `execution.control` (`{method: "stop", args: [message?]}`) and ends itself once the host has the request; the host stops the engine as its own stop does, or, with the error of exit(error), fails the execution with that message (host b2c78ccc5, Bun 0207cd7). execution.control is no grant: the host adds it to the capabilities of every broker (when fewer than the plugin's 64 are granted) and it reaches only this execution. Without it (an older host) a Worker ends only itself. isStopped reflects the exit / stop of the thread.
- memory.watch is not portable (Q140 (a), appendix D D-22 now also covers the Bun column): equivalents FinalizationRegistry, `bun:jsc` heapStats / generateHeapSnapshot. plugins.load stays rhino-only (D-14).
- WIDTH / HEIGHT and the SetScreenMetrics scaling of Tap / Swipe use the short and the long side of the real display (Bun e6b4296), as the host's ScreenMetrics gives Rhino. Before, `device-info.json` took the service window size, which leaves out the navigation bar on many devices.

## Cases

`tools/api-coverage/device_bun_system.py` runs the Node runtime's cases (`tools/nodejs/api/system/cases.js` of Node 6c2fae29, 51 cases) and 3 of its own (the engine record of `engines.myEngine()`, a script started by `engines.execScript`, `isAutoJs6` / `exit` / `app.autojs`): 54 cases, first in Rhino, then in a Bun main script and a Bun Worker of one execution (each calls `host.global.install()` first, so the global cases see the globals; the scripts are named `p31b_<engine>_<run>.js` for the default channel id case), with 23 grants on (the 12 of this family, files.control, device.control / device.power, notifications.post / notifications.settings, clipboard.control, accessibility.control, app.control, shell.control / shell.root / shell.shizuku). A second run (`denied`, 16 cases) keeps only the query grants on (autojs.control, settings.control, memory.control, installer.control, engines.control, device.control, notifications.post): setRootMode, setEnabled, export, sysprops, dumpHeap, install, execScript, the powerManager query, ensureEnabled and plugins.tools must reject NOT_GRANTED with the expected `missingCapabilities`, the queries must answer. The grants are host preferences, set and restored by a host script. A third phase (`exit`) runs a Bun script whose Worker calls `exit(new Error(...))`, then one whose Worker calls `stop()`, while the main thread would report again after 15 s: the execution must end before that, and only exit(error) leaves its error on the host console.

Not run on the device, because they act on the device or on AutoJs6: autojs.restart / exit, installer.install / uninstall / setDefault, memory.dumpHeap with a grant, settings.setEnabled with a grant, engines.stopAll, launchSettings, dialogs.build().show(). They are covered by the Bun unit tests (`tools/host-client/system.test.mjs`: the routes and arguments they send, grants) and the structure check `tools/api-coverage/check-bun-system.mjs` (119 members and 20 globals on the client and in `host-client.d.mts`, method tables against the host, grants, policy).

## Verification

Device: Redmi 22120RN86C, serial `bek749scrwv4wo8h`, Android 13 / API 33, arm64-v8a. No Root; 5 plugin tools listed.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935) debug, md5 `6624bd371b1b64c1d9189f4c8a5397c9` | 6.8.0 (5935) debug arm64 of an export of host e3689a136 with the changes of b2c78ccc5 / 262870051, md5 `d9e669fe6bf76a582327b9271c3e44a1` | reinstalled from the recorded copy, md5 `6624bd371b1b64c1d9189f4c8a5397c9` |
| Bun runtime plugin | 0.20.0 (293) debug, md5 `bbfca1f67936a60a0700ec1a12e216eb` | 0.21.0 (296) debug arm64 of an export of Bun 0207cd7, md5 `560d3f0b9bf4a9ff2525e49fa07fe6f3` | reinstalled from the recorded copy, md5 `bbfca1f67936a60a0700ec1a12e216eb` |

The 23 Bun grants: absent before, absent after. The run directory `/sdcard/Download/aj6-bun-system` is removed after each run.

| Run | Bun main | Bun Worker |
|---|---|---|
| compare (54 cases, Rhino 54 / 54) | 54 pass (2 Rhino Java exceptions met by rejections) | 54 pass (same) |
| denied (16 cases) | 16 pass | 16 pass |
| exit (Worker exit(error) / stop()) | - | both pass: the main thread did not report again, the error of exit(error) was on the host console, none for stop() |

Device values both engines agree on: `autojs.isRootAvailable()` false with root mode AUTO_DETECT (Rhino's RootMode object in both: key, description, toString, name(), ordinal()), installer queries [true, false, true], powerManager [false, false], 5 plugin tools.

Earlier runs: before the rework (host a268b1526, Bun 1383a79 with e6b4296, Node cases of 08781cee) 49 / 49 in Rhino and in each Bun thread, denied 16 / 16; the first (host 81f809d05 / Bun ef7e030) failed every Bun case while the plugin tools grants were on (the underscore IDs above); the second (host a268b1526 / Bun 1383a79) passed 48 / 49 in each thread, `global.metrics` gave 720 x 1569 against Rhino's 720 x 1650 (the window size above), fixed by Bun e6b4296.

Commands (host checkout, `--state` a private directory; GRANTS and QUERY_GRANTS are the lists in the script):

```
py -3.12 tools/api-coverage/device_bun_system.py record --serial SERIAL --state STATE
py -3.12 tools/api-coverage/device_bun_system.py install --serial SERIAL --state STATE --apk org.autojs.autojs6=HOST.apk --apk io.github.supermonster003.autojs6.plugin.bun.runtime=BUN.apk
py -3.12 tools/api-coverage/device_bun_system.py grant --serial SERIAL --state STATE --set on --only GRANTS
py -3.12 tools/api-coverage/device_bun_system.py compare --serial SERIAL
py -3.12 tools/api-coverage/device_bun_system.py grant --serial SERIAL --state STATE --set off --only GRANTS-without-QUERY_GRANTS
py -3.12 tools/api-coverage/device_bun_system.py denied --serial SERIAL
py -3.12 tools/api-coverage/device_bun_system.py exit --serial SERIAL
py -3.12 tools/api-coverage/device_bun_system.py grant --serial SERIAL --state STATE --set restore --only GRANTS
py -3.12 tools/api-coverage/device_bun_system.py restore --serial SERIAL --state STATE
```

Desktop: Bun `node --test tools/host-client/` (118 pass, among them system.test.mjs 8: RootMode, the default channel id, host.global.install and exit in a real worker_threads Worker sending execution.control), the `sync-*.mjs --check`, `:app:testDebugUnitTest` (66 pass); host `BunAutomationPolicyTest` (13), `node tools/api-coverage/check-bun-system.mjs` (in CI; also checks host.global.install, execution.control and that plugintools.usergranted is gone).

## Leftovers

- The Node.js Runtime takes WIDTH / HEIGHT from the same window metrics (`NodeRuntimeModuleInjector`, `resources.displayMetrics`); P3.1b passed on a device where they equal the display, on the Redmi above they would leave out the navigation bar. Follow-up for the Node runtime (P3.1c, whose device.width / height share the source).
- execution.control rides with the granted capabilities, and the plugin takes at most 64 per execution: with every Bun grant of host 262870051 on there are 62 grants plus execution.control, 63, so it fits; should the grants ever fill all 64, the host leaves execution.control out and a Worker's exit / stop end only that Worker again.
- At host 262870051 `check.py --matrix --strict-arity --docs-support` fails on rows outside this family (imgui.ListClipper / listClipper new pending since host 772cff4c6, 17 device cells no longer pending and api/device.md / api/imgui.md support tables while P4.1c / P3.1c are in progress); this family has no pending Bun cell.
