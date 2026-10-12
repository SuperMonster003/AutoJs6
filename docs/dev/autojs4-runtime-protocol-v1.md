# AutoJs4 Runtime Plugin Protocol V1

Status: versioned V1 contract, frozen together with the first `autojs4-runtime-api` module
(2026-10-12). This protocol does not enable AutoJs4 execution in the AutoJs6 host by itself;
the host only gains the `autojs4` engine channel and the explicit "Run with AutoJs4" menu entry
described in `docs/dev/autojs4-runtime-plugin-roadmap.md`.

## Decision

AutoJs4 (the Auto.js 4.1.1 Alpha2 clone, https://github.com/SuperMonster003/AutoJs4) is the
behavioral baseline of AutoJs6. To let users compare both engines on one device, the
`AutoJs6-Plugin-AutoJs4-Runtime` application embeds the AutoJs4 engine modules (`:autojs`,
`:common`, `:automator`) and runs scripts in its own process. The AutoJs4 classes share the
`com.stardust.*` package names with the frozen compatibility copy inside the host and link a
different Rhino, so they must never be loaded into the AutoJs6 process.

The stable engine ID is `autojs4`. The discovery and runtime actions are:

- `org.autojs.plugin.INFO` (category `autojs4-runtime`)
- `org.autojs.plugin.autojs4.RUNTIME` (category `autojs4-runtime`)

The runtime service uses the signature permission `org.autojs.permission.PLUGIN`. Discovery
grants no authority: the host pins the selected component and validates package identity, UID,
signer set, version and update identity on every call, and the plugin enforces the calling
permission on every Binder entry point. Both sides use the constants mirrored in
`plugin-api/autojs4-runtime-api` (`AutoJs4RuntimeContract`, `AutoJs4PluginIds`,
`AutoJs4PluginActions`, `AutoJs4PluginCapabilityKeys`).

## V1 Binder surface

```aidl
IAutoJs4RuntimePlugin
    PluginInfo getInfo()
    Bundle getRuntimeInfo()
    Bundle runScript(in Bundle request, IAutoJs4RuntimeCallback callback)
    boolean cancelScript(String executionId)

IAutoJs4RuntimeCallback (oneway)
    void onEvent(in Bundle event)
```

Unlike the QuickJS channel, `runScript` is an admission call: it validates the request, starts
the AutoJs4 execution and returns an acceptance Bundle immediately. AutoJs4 scripts have no
execution deadline (UI scripts, floating windows and timers run until the user stops them), so
the terminal state always arrives through the callback. Classic AIDL transaction numbers follow
declaration order; V1 methods may only be appended, never inserted or reordered.

### Source transport

Scripts are addressed by absolute file path (`SOURCE_TRANSPORT = file-path`). The AutoJs4 engine
resolves `require`, `files.cwd()`, `images.read` and other relative paths itself, exactly as
the AutoJs4 application does, so the host does not ship source bytes, content URIs or file
descriptors. The plugin needs its own storage permission (legacy external storage, since the
plugin keeps AutoJs4's `targetSdk 28`); a missing grant is reported as `PERMISSION_DENIED`
and the plugin opens its permission page.

Two source types exist:

| `sourceType` | AutoJs4 source class | Notes |
| --- | --- | --- |
| `javascript` | `com.stardust.autojs.script.JavaScriptFileSource` | `"ui";` scripts are routed by AutoJs4 to its `ScriptExecuteActivity` inside the plugin process |
| `auto` | `com.stardust.autojs.script.AutoFileSource` | Root input-event recordings replayed by `RootAutomatorEngine` |

Project folders are resolved by the host: it reads `project.json`, takes `main` as the script
path and the folder as `workingDirectory`, and passes the folder as `projectDirectory`. The
plugin applies AutoJs4's `ProjectLauncher` semantics (`ProjectConfig.fromProjectDir`, project
`features`) and ignores AutoJs6-only `project.json` keys.

### Request Bundle

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `protocolVersion` | int | yes | Must equal `1` |
| `executionId` | String | yes | `[A-Za-z0-9._-]{1,128}`, unique per request |
| `sourceType` | String | yes | `javascript` or `auto` |
| `scriptPath` | String | yes | Absolute path, at most `MAX_PATH_BYTES` (4096) UTF-8 bytes, no NUL |
| `workingDirectory` | String | yes | Absolute directory path, same limits |
| `projectDirectory` | String | no | Absolute project folder when launched from a project |
| `arguments` | Bundle | no | Script arguments (`engines.myEngine().execArgv`); at most `MAX_ARGUMENTS` (64) entries of String, boolean, int, long or double; each String at most `MAX_ARGUMENT_VALUE_BYTES` (64 KiB) |
| `delay` | long | no | Milliseconds before the first run, default 0 |
| `interval` | long | no | Milliseconds between loop runs, default 0 |
| `loopTimes` | int | no | Loop count, default 1; `0` means unlimited as in AutoJs4 |

Unknown keys are rejected with `INVALID_REQUEST`. The plugin does not inspect the script to
choose the engine; `sourceType` is authoritative.

### Acceptance Bundle (return value of `runScript`)

| Key | Type | Meaning |
| --- | --- | --- |
| `protocolVersion` | int | `1` |
| `executionId` | String | Echo of the request |
| `accepted` | boolean | `true` when the execution was started |
| `uiMode` | boolean | `true` when AutoJs4 routed the script to `ScriptExecuteActivity` |
| `errorCode` | String | Present when `accepted` is `false` |
| `errorMessage` | String | Bounded by `MAX_TERMINAL_MESSAGE_BYTES` (16 KiB) |
| `permission` | String | Present with `PERMISSION_DENIED`: `storage`, `overlay` or `accessibility` |

When `accepted` is `false` no callback event is emitted for that execution.

### Callback events

Every event carries `protocolVersion`, `executionId`, `eventType`, a monotonically increasing
`sequence` (from 0) and `timestamp` (epoch milliseconds). The host drops events whose
protocol version, execution ID, calling UID or sequence do not match.

| `eventType` | Extra keys | Meaning |
| --- | --- | --- |
| `started` | `uiMode` | AutoJs4 reported `onStart`; for UI scripts this fires when the Activity has created the engine |
| `log` | `level` (android.util.Log level), `text` | One `GlobalConsole.println` line, raw text without AutoJs4's own time prefix, at most `MAX_LOG_CHUNK_BYTES` (64 KiB) |
| `finished` | `succeeded`, `cancelled`, `durationMillis`, optional `errorCode`, `errorMessage`, `errorStackTrace`, `result` | Terminal event, emitted exactly once |

`errorStackTrace` is bounded by `MAX_STACK_TRACE_BYTES` (64 KiB); `result` is the
`String.valueOf` of the AutoJs4 execution result when it is not null, bounded like messages.

### Cancellation

`cancelScript(executionId)` returns `true` when the execution was known and a stop was
requested (`ScriptEngine.forceStop()`, which also finishes `ScriptExecuteActivity` for UI
scripts) or when the ID was remembered as a pre-dispatch cancellation for at most 60 seconds.
The execution then finishes with `cancelled = true` and `errorCode = CANCELLED`. The host waits
`CANCEL_CONFIRM_TIMEOUT_MILLIS` (10 s) for the terminal event, then marks the task as stopped
and logs a warning. A dead Binder (plugin process gone) is mapped by the host to
`RUNTIME_UNAVAILABLE` with the message "AutoJs4 runtime exited unexpectedly".

### Runtime info Bundle (`getRuntimeInfo`)

| Key | Type | Meaning |
| --- | --- | --- |
| `protocolVersion` | int | `1` |
| `runtimeReady` | boolean | AutoJs4 engine service initialised in the plugin process |
| `processName` | String | Must equal the declared service process |
| `autojs4VersionName` / `autojs4VersionCode` | String / int | From the embedded AutoJs4 `project-versions.json` |
| `autojs4Commit` | String | Git commit of the embedded AutoJs4 submodule |
| `rhinoVersion` | String | `1.7.7.2` |
| `supportedAbis` | String[] | ABIs packaged in the installed plugin |
| `processAbi` | String | ABI of the running plugin process |
| `storagePermissionGranted` / `overlayPermissionGranted` / `accessibilityServiceEnabled` | boolean | Current permission state |
| `activeExecutions` / `maxConcurrentExecutions` | int | Concurrency, `MAX_CONCURRENT_EXECUTIONS` = 16 |

### Error codes

`INVALID_REQUEST`, `PERMISSION_DENIED`, `FILE_NOT_FOUND`, `PROJECT_INVALID`, `BUSY`,
`RUNTIME_UNAVAILABLE`, `CANCELLED`, `SCRIPT_EXCEPTION`, `INTERNAL`.

## PluginInfo and capabilities

`PluginInfo.id = autojs4-runtime`, `engine = autojs4`, `variant = default`,
`supportedAbis` = packaged ABIs (currently `armeabi-v7a` and `x86`, mirroring AutoJs4 whose
OpenCV 3.4.3 native library exists only for 32-bit ABIs). `capabilities` must contain:

| Key | Value |
| --- | --- |
| `requiresHostVersion` (`PluginCapabilityKeys.REQUIRES_HOST_VERSION`) | `AutoJs4RuntimeContract.REQUIRES_HOST_VERSION` |
| `org.autojs.plugin.autojs4.CONTRACT_VERSION` | `1` |
| `org.autojs.plugin.autojs4.AUTOJS4_VERSION_NAME` / `AUTOJS4_VERSION_CODE` / `AUTOJS4_COMMIT` | Embedded AutoJs4 identity |
| `org.autojs.plugin.autojs4.RHINO_VERSION` | `1.7.7.2` |
| `org.autojs.plugin.autojs4.SOURCE_TRANSPORT` | `file-path` |
| `org.autojs.plugin.autojs4.EXECUTION_MODEL` | `isolated-process-concurrent` |
| `SUPPORTS_UI_MODE`, `SUPPORTS_AUTO_FILES`, `SUPPORTS_PROJECTS`, `SUPPORTS_CANCELLATION`, `SUPPORTS_STREAMING_LOG` | `true` |

The host accepts a plugin only when the contract version equals its own `PROTOCOL_VERSION`;
the AutoJs4 version is informational so that the plugin can upgrade its embedded AutoJs4 without
a host release.

## Host behaviour summary

- Package visibility: the host manifest declares `org.autojs.plugin.autojs4.RUNTIME` with
  category `autojs4-runtime` inside `<queries>`; without it Android 11+ hides the plugin
  service and the menu falls back to the install guidance although the plugin is installed.
- Engine registration: `AutoJs4PluginScriptEngine` under `AutoJs4ScriptSource.ENGINE`
  (`autojs4`), created only for the explicit menu action; no extension or directive routes to it.
- Logs are written to the host `GlobalConsole` with the original level and the prefix
  `[AutoJs4] `.
- Availability states shown by the menu: not installed (install guidance to Plugin Center),
  disabled, incompatible, failed, ready.
