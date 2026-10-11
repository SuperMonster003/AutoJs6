# MCP Server Plugin Protocol V1

Status: versioned V1 contract. The host contract module `plugin-api/mcp-server-api`
(MCP roadmap P1.1) froze the five AIDL interfaces, the Bundle keys, the state and
error vocabularies and the ceilings listed here. Transaction numbers are frozen in
declaration order; a later contract version may only append methods and optional
keys. The host side of this protocol (client, grant, broker, session controller)
landed with roadmap P1.2, the bridge methods the MCP tool groups rely on with
P1.3, and the delivering host build is recorded in `McpServerIds.REQUIRED_HOST_VERSION_CODE`.
This protocol does not run an MCP server in the AutoJs6 host by itself.

## Decision

The MCP server is a separate plugin APK (`io.github.supermonster003.autojs6.plugin.mcp.server`)
with its own UID and process. The Streamable HTTP listener, the MCP SDK, the
pairing store and the tool registry live in that plugin and must not be loaded
into the AutoJs6 process. The host contributes exactly one thing per session: a
capability broker Binder that maps tool calls onto Node Bridge methods
(`module.method`) under a host-issued grant (roadmap D2, D3, D6).

The stable plugin ID, engine and variant are `mcp-server` / `mcp-server` /
`default`. The discovery actions are:

- `org.autojs.plugin.INFO` (plugin info service)
- `org.autojs.plugin.MCP_SERVER` with category `mcp-server` (plugin service)

Both services are protected by the signature permission `org.autojs.permission.PLUGIN`.
Discovery does not grant authority. Before binding, the host pins the resolved
component and requires the package name above, an exported and enabled service
that declares the plugin permission, and membership in the set of components
answering the contract query. The plugin info must carry the expected id, engine
and variant, a `mcpServerContractVersion` inside the supported range, and a
`requiresHostVersion` that is not below `REQUIRED_HOST_VERSION_CODE`. Plugin-side
caller checks (host package, signer, UID) belong to the plugin repository.

The plugin is registered in the Plugin Center with `defaultEnabled = false`: the
user turns the server on. The host binds with a dedicated binding lease that is
held until the session closes; RemoteExceptions during `openServer` are not
retried.

## V1 Binder Surface

```aidl
IMcpServerPlugin
    PluginInfo getInfo()
    Bundle getCapabilities()
    IMcpServerSession openServer(Bundle config, IMcpHostCapabilityBroker broker, IMcpServerCallback callback)

IMcpServerSession
    Bundle getStatus()
    void updateConfig(Bundle config)
    void stop(Bundle reason)
    void close()

IMcpServerCallback (oneway)
    void onStatus(Bundle status)
    void onEvent(Bundle event)

IMcpHostCapabilityBroker
    Bundle getBrokerInfo()
    void dispatch(Bundle request, IMcpHostCapabilityCallback callback)
    void destroy(Bundle reason)

IMcpHostCapabilityCallback (oneway)
    void onResponse(Bundle response)
```

`McpServerAidlOrderTest` parses the AIDL sources and guards this order and the
`oneway` markers. Every Bundle written by the host carries `contractVersion`
(`KEY_CONTRACT_VERSION`, currently `1`); the broker info additionally carries
`hostCapabilityBrokerVersion` (currently `1`) so the plugin can gate broker
features separately from the session contract.

## Bundle Keys

All keys are constants of `McpServerContract` (plugin API package
`org.autojs.plugin.mcp.server.api`); wire literals are listed for clients that
read Bundles without the Kotlin module.

### Capabilities (`getCapabilities`)

| Key | Type | Meaning |
| --- | --- | --- |
| `requiresHostVersion` | Long | Minimum host `versionCode` (shared plugin key) |
| `mcpServerContractVersion` | Int | Contract version implemented by the plugin |
| `mcpServerToolGroups` | String[] | Tool groups the plugin exposes |
| `mcpServerProtocolVersions` | String[] | MCP protocol versions the server negotiates |
| `mcpServerSdkVersion` | String | MCP SDK version compiled into the plugin |

### Server config (`openServer`, `updateConfig`)

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | Always `1` |
| `serverConfigPort` | Int | `1024..65535`, default `9637` |
| `serverConfigBindScope` | String | `loopback` (default) or `lan` |
| `serverConfigProtocolMode` | String | `stateful` (default); `dual` is reserved and rejected with `invalid_config` (roadmap D9) |
| `serverConfigHostLabel` | String | Optional, at most 64 characters |

The endpoint path is `/mcp`; the loopback endpoint is `http://127.0.0.1:<port>/mcp`.

### Status (`getStatus`, `onStatus`)

| Key | Type | Meaning |
| --- | --- | --- |
| `statusState` | String | One of the states below |
| `statusEndpoints` | String[] | Reachable endpoint URLs |
| `statusPort` | Int | Bound port |
| `statusBindScope` | String | Effective bind scope |
| `statusProtocolMode` | String | Effective protocol mode |
| `statusPairedClientCount` | Int | Paired clients |
| `statusActiveSessionCount` | Int | Live MCP sessions |
| `statusStartedAt` | Long | Epoch millis of the last successful start |
| `statusHostAvailable` | Boolean | Whether the broker is reachable from the plugin |
| `statusLastErrorCode` | String | Last error or stop reason code |
| `statusLastError` | String | Human-readable detail, at most 4 KiB |

Unknown states are read as `stopped`.

### Reason (`stop`, `destroy`)

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | Always `1` |
| `errorCode` | String | Error or reason code |
| `errorMessage` | String | Optional detail, at most 4 KiB |

### Event (`onEvent`)

| Key | Type | Meaning |
| --- | --- | --- |
| `eventType` | String | `pairing_requested`, `client_paired`, `client_revoked`, `tool_call`, `warning` |
| `eventText` | String | Optional detail, at most 2 KiB |
| `eventTimestamp` | Long | Epoch millis |
| `eventClientName` | String | Optional client name, at most 256 bytes |
| `eventToolName` | String | Optional tool name |

Events with an unknown or blank type are dropped by the host.

### Broker info (`getBrokerInfo`)

| Key | Type | Meaning |
| --- | --- | --- |
| `contractVersion` | Int | Always `1` |
| `hostCapabilityBrokerVersion` | Int | Broker contract version, `1` |
| `hostCapabilityBrokerId` | String | Session id (`mcp-<uuid>`) |
| `bridgeEngineInfo` | String | JSON of the synthetic engine info (`engineName = "mcp"`, `backend = "plugin"`, cwd = working directory) |
| `hostCapabilityModules` | String[] | Modules present in the grant |
| `grantMethods` | String[] | Allowed `module.method` keys |
| `grantPermissions` | String[] | Permission tokens the grant carries |
| `grantMaxRequestBytes` | Int | Per-request args ceiling |
| `grantMaxConcurrentCalls` | Int | Concurrent tool calls, at most 4 |
| `grantAccessibilityQueriesPerSecond` | Int | Accessibility rate limit |
| `grantDefaultTimeoutMs` | Long | Timeout applied when a request carries none, 30 s |
| `grantMaxTimeoutMs` | Long | Timeout clamp, 300 s |

### Bridge request (`dispatch`)

| Key | Type | Meaning |
| --- | --- | --- |
| `bridgeRequestJson` | String | Node Bridge request JSON: `{id, module, method, args, timeoutMs, permissions}` |
| `bridgeClientName` | String | Optional paired client name for audit lines, at most 256 bytes |

### Bridge response (`onResponse`)

| Key | Type | Meaning |
| --- | --- | --- |
| `bridgeResponseJson` | String | Node Bridge response JSON: `{id, ok, result}` or `{id, ok: false, error}` |
| `bridgeResponseOk` | Boolean | Mirrors `ok` |
| `bridgeErrorMessage` | String | Error message when `ok` is false |
| `bridgePayloadFd` | ParcelFileDescriptor | Present only for descriptor payloads (see below) |
| `bridgePayloadBytes` | Long | Exact payload length |
| `bridgePayloadMime` | String | Payload MIME type |

## State Machine

Server states are `stopped`, `starting`, `running`, `stopping` and `failed`;
`starting`, `running` and `stopping` are the active states. The host session
controller (`McpServerSessionController`) drives them as follows:

1. `open(config)` validates the config (`invalid_config`), rejects a second open
   (`already_open`), discovers and validates the plugin (`unsupported_contract`
   on a version mismatch), creates a broker and a callback, calls `openServer`,
   links to the session Binder's death and reads `getStatus()`. The host
   publishes `starting` first and then whatever the plugin reports.
2. The plugin reports `running` once the listener is bound; `port_in_use` and
   `bind_failed` are plugin-side failures reported through `failed` or through
   `stopped` with a last error code.
3. `stop(reason)` asks the plugin to stop listening while keeping the session;
   `close()` releases the session, the lease and the broker. Both are idempotent
   on the host side; a user close suppresses any pending rebind.
4. `updateConfig(config)` while open re-validates and forwards; the plugin may
   restart the listener and reports `config_changed` as the stop reason of the
   previous listener.

Error codes: `unsupported_contract`, `invalid_config`, `port_in_use`,
`bind_failed`, `already_open`, `internal`. Stop reasons: `user_request`,
`host_shutdown`, `host_died`, `plugin_died`, `config_changed`.

## Grant And Ceilings

`McpCapabilityGrant` is a pure Kotlin value: the allowed `module.method` set, the
permission-token subset, `maxRequestBytes` (at most 512 KiB), `maxConcurrentCalls`
(at most 4), `accessibilityQueriesPerSecond`, `defaultTimeoutMs` (30 s) and
`maxTimeoutMs` (300 s). The grant is the enforced permission manifest of the
`AndroidNodeBridgeCapabilityProvider` behind the broker, so the Node Bridge path
and the MCP path share one policy implementation.

`dispatch` evaluates, in order:

1. Destroyed broker: `process-dead` failure.
2. Inline request larger than 512 KiB: `resource-limit`.
3. Malformed request JSON: `invalid-request`.
4. Method not granted: `permission-denied` with category `capability-denied`;
   requested permissions outside the grant: `capabilityNotDeclared`
   (category `capability-denied`); args above `maxRequestBytes`: `resource-limit`.
5. The request timeout is clamped to `defaultTimeoutMs` / `maxTimeoutMs` and the
   request is handed to the provider, which applies the Node Bridge preflight
   (operation declarations, args and request ceilings) and rate limits.

Results larger than 512 KiB of inline JSON are replaced by a `resource-limit`
failure telling the caller to use a descriptor-carrying method.

The default grant (`McpCapabilityGrant.DEFAULT`) allows the roadmap A.6 method
set: `accessibility.{isEnabled, ensureEnabled, dump, explain, screenshot, findOne, findAll, findByText, click, longClick, setText, scrollForward, scrollBackward, swipe, gesture, back, home, recentApps}`,
`engines.{execScript, execScriptFile, list, stop, stopAll}`, `console.tail`,
`files.{list, stat, read, write, mkdir, rename, delete}`,
`app.{launchPackage, launchApp, isInstalled, editFile, currentWindow, listSamples, readSample, listDocs, readDoc}`,
`package_manager.{list, verify}`, `clipboard.{getText, setText, hasText}`,
`device.{info, isScreenOn, wakeUp, isIgnoringBatteryOptimizations}`,
`media_projection.{requestScreenCapture, stop}`, `image.{captureScreen, recycle}`,
`shell.exec` and `toast.{toast, showToast}`, plus the global actions
`keys.{notifications, quickSettings, powerDialog, lockScreen}` behind the plugin's
`ui_press_key` (roadmap P3.2; `keys.takeScreenshot` and `keys.splitScreen` stay
outside), with the tokens `accessibility`, `accessibility.gesture`, `keys`,
`engines`, `engines.exec`, `screen_capture`, `image`, `app`, `app.launch`,
`app.query`, `app.activity`, `clipboard`, `device`, `device.power`, `shell`,
`toast`, `package_manager`, `files`, `files.write`, `files.delete` and `console`.
A `keys.*` call without a running accessibility service fails with the
`unavailable` category, as the `accessibility` module does.

`accessibility.swipe` with identical end points (`x1 == x2`, `y1 == y2`) is dispatched as a
press of `durationMs` (a single-point stroke); the MCP plugin's coordinate `ui_click` /
`ui_long_click` (roadmap P3.2) rely on this form. The gesture results are delivered on the main
looper and the dispatch thread waits for them: a host built before 2026-09-11 gave the automator
no handler, so a reused broker thread answered every gesture after its first one with `false`
(the plugin reported `ACTION_FAILED` "the system cancelled ...").

| Resource | V1 ceiling |
| --- | ---: |
| HTTP request body (plugin) | 1 MiB |
| Concurrent tool calls | 4 |
| Tool timeout | 30 s default, 300 s maximum |
| Inline bridge JSON (request and result) | 512 KiB |
| Descriptor payload | 8 MiB |
| Accessibility dump nodes / depth / text | 400 / 32 / 256 KiB |
| Screenshot encoded bytes | 4 MiB |
| Console tail lines / bytes | 500 / 256 KiB |
| File read / write | 1 MiB each |
| Listing entries (files, samples) | 2000 |
| Shell output | 256 KiB |
| Paired clients | 32 |
| Pairing window / cooldown | 60 s / 30 s |
| Error message / event text / client name | 4 KiB / 2 KiB / 256 bytes |

`McpServerContract` remains the authoritative set; the host bridge classes carry
matching constants (`BridgeAccessibilityLimits`, `NodeBridgeFileScope`,
`NodeBridgeConsoleTail`, `NodeBridgeSampleCatalog`).

## Bridge Methods Added For MCP (P1.3)

These methods serve every Node Bridge consumer (Node, Python host, MCP). Result
JSON objects carry a `schema` field; the schemas below are frozen at `v1`.

| Method | Schema | Notes |
| --- | --- | --- |
| `accessibility.dump(options)` | `autojs6-bridge-accessibility-dump-v1` | `format` text / json / xml, `window` active / all, `maxDepth <= 32`, `maxNodes <= 400`, `visibleOnly`, `properties`; JSON nodes carry the seven `BridgeNode` fields plus `root`, `depth`, `index`, `childCount`, `visible` and, since roadmap P3.2, the state flags `scrollable`, `checkable`, `checked`, `editable`, `focused`, `selected`, `longClickable`; text is capped at 256 KiB (`textTruncated`) |
| `accessibility.explain(selector, options)` | `autojs6-bridge-accessibility-explain-v1` | Per-filter `steps` (matched, cumulative), `matches`, `nearMisses`, `failingIndex`, rendered `text` |
| `accessibility.screenshot(options)` | `autojs6-bridge-accessibility-screenshot-v1` | API 30+ via `A11yScreenshotter`; `format` jpeg / png / webp, `quality`, `scale` or `maxWidth`, `region`, optional `savePath` inside the working directory; below API 30 an `unavailable` failure names the `media_projection` fallback; tokens `accessibility` + `screen_capture` |
| `app.currentWindow()` | `autojs6-bridge-app-current-window-v1` | Package and activity from `ActivityInfoProvider`, window list from `A11yWindowFacts` |
| `files.*` | `autojs6-bridge-files-<method>-v1` | Rooted at the working directory, normalized relative paths only, symbolic links cannot escape; `write` / `mkdir` / `rename` need `files.write`, `delete` needs `files.delete` |
| `console.tail(options)` | `autojs6-bridge-console-tail-v1` | `lines <= 500`, `sinceId`, `level`, `maxBytes <= 256 KiB`; returns `firstId` / `lastId` / `nextSinceId` / `latestId` / `total` / `capacity` (4096) / `evicted`; token `console` |
| `engines.execScript` / `execScriptFile` options | execution JSON | `captureConsole` and `waitMs` (0..300000, defaults to `timeoutMs` when capturing) add `finished`, `outcome` (`running` / `success` / `exception`), `error` and a `console` window with `captureMode = "global-window"` |
| `engines.list()` / `engines.stop(id)` | `autojs6-bridge-engines-list-v1` / `-stop-v1` | Host `ScriptEngineService` executions with `state` starting / running / finished; `stopAll({scope: "host"})` returns `autojs6-bridge-engines-stop-all-v1`, `stopAll()` without a scope keeps the plugin-process answer |
| `device.info()` | `autojs6-bridge-device-info-v1` | Build fields, screen, battery, host version and process, accessibility state, screen state; no hardware identifiers |
| `app.listSamples(path, options)` / `app.readSample(path, options)` | `autojs6-bridge-app-list-samples-v1` / `-read-sample-v1` | Read-only view of the `sample/` assets, dot entries hidden, `available: false` without samples; token `app.query` |
| `app.listDocs(path, options)` / `app.readDoc(path, options)` | `autojs6-bridge-app-list-docs-v1` / `-read-doc-v1` | Read-only view of the verified `docs/` assets of the installed Offline Documentation plugin (`OfflineDocsPluginStateMonitor`); directories are derived from the inventory, the root listing carries `status`, `packageName`, `contentVersion`, `fileCount` and `available: false` (with the monitor status and `errorCode`) without an eligible plugin; token `app.query` |

## Lifecycle And Death Handling

- One broker per `openServer`; its id is the session id. `destroy(reason)`
  releases the provider (in-flight bridge calls complete with `process-dead`)
  and every later `dispatch` answers `process-dead`.
- The host links to the session Binder's death. On death it unlinks, closes the
  binding lease, destroys the broker and, unless the user closed the session,
  rebinds with delays of 1 s, 3 s and 9 s (`McpRebindPolicy`, three attempts)
  while publishing `starting` with `plugin_died` as the last error code; when the
  attempts are exhausted it publishes `stopped` with reason `plugin_died`.
- The plugin links to the broker Binder's death. The listener stays running
  with `hostAvailable = false`; host-backed tools return `HOST_UNAVAILABLE`.
  A new host session replaces the dead broker and reuses the listener. The host
  never replays tool calls after a death; the MCP client sees a tool error and decides.
- `onStatus` and `onResponse` are oneway. The host serializes status into a
  `StateFlow`, so a burst of status callbacks collapses to the latest value; the
  plugin must not rely on the host observing every intermediate state.
- `stop` and `close` are idempotent; `close` after `stop` is the normal shutdown
  order. A user-initiated close sets a flag before the Binder calls so a death
  during shutdown does not trigger a rebind.

## Payload Descriptor Convention

Bridge results stay inline JSON unless they name a one-shot file. A result whose
`payload` object is `{kind: "file", oneShot: true, path, mime, bytes}` with a path
canonically under the host cache directory (`cacheDir/bridge-payloads`) is
converted by the broker: the file is opened read-only, unlinked, the JSON keeps
only the metadata with `kind = "descriptor"` and no `path`, and the Bundle gains
`bridgePayloadFd`, `bridgePayloadBytes` and `bridgePayloadMime`. The receiver owns
the transaction's duplicate of the descriptor and must close it; the host closes
its own copy after `onResponse` returns. Payloads above 8 MiB fail with
`resource-limit`. Files written elsewhere (for example a screenshot `savePath`
inside the working directory) are reported as `{kind: "workspace", path}` and stay
inline. Unclaimed one-shot files are swept from the cache after ten minutes.

## R0 Scope And Non-Goals

P3.5 resource consumers use the existing files.read, app.listSamples,
app.readSample, device.info, and console.tail methods, plus app.listDocs and
app.readDoc for the optional documentation resources. The plugin owns URI
parsing, pairing, group checks, pagination, and prompt text. Sample binary
encoding uses the existing Okio base64 codec on API 24 and later. Large JSON
sample results use the P3.4 read-only descriptor path. No AIDL changes are
needed; the default grant only gains the two read-only docs methods. Text /
binary resource data remains bounded to 1 MiB and carries truncation metadata.
Offline-Docs resources stay optional: the host relays the verified assets of
an installed AutoJs6-Plugin-Offline-Docs and answers available: false
otherwise, so the plugin lists autojs6://docs/ only while that plugin is
present.

- The `dual` protocol mode (stateless MCP model of the 2026-07-28 specification)
  is reserved until the Kotlin SDK supports it (roadmap D9); the host rejects it.
- Pairing (token, first-connection confirmation) and tool toggles are plugin-side
  and surface here only as events and status counters.
- Console capture is an id window over the shared `GlobalConsole`, not a
  per-execution stream: every script runtime prints into the same console.
- The host drawer switch and plugin settings are delivered by the optional P4
  extension below. Binder round-trip tests remain the P1 / P2 acceptance path.

## P4 Settings Extension, Version 1

This extension leaves the AIDL transaction order and core contract version intact.
`mcpServerSettingsVersion = 1` is advertised in PluginInfo and `getCapabilities()`.
The drawer checks this capability before opening a session with
`serverConfigUseSavedSettings = true`. The plugin then uses its private saved
port, bind scope and developer-mode settings. Legacy callers omit the key and
keep their explicit port / scope behavior.

`mcpServerUserStopped` is a dynamic Boolean in `getCapabilities()` only. It defaults
to true when the lifecycle file is missing or invalid. A successful start writes
false; a user stop writes true. Automatic reconnect checks this flag, so a stop
from the plugin notification while the host was dead is respected on host relaunch.
Manual drawer activation can start a stopped server. Neither application starts
the endpoint at device boot.

The settings action is `org.autojs.plugin.mcp.server.action.OPEN_SETTINGS`.
Both drawer long-press and Plugin Center resolve one explicit activity in the
MCP Server package and require it to be enabled, exported and guarded by
`org.autojs.permission.PLUGIN`. Missing, disabled, unactivated, untrusted and
incompatible plugins have separate guidance. A user-granted signer requires
the extra D18 confirmation, pinned to its current certificate fingerprints.

The process-scoped owner survives drawer recreation. It ignores callbacks from
deliberate close operations, preserves starting until a real status callback,
and keeps retryable plugin death failures inside the bounded rebind loop.
Notification / settings stops close the drawer switch and release the broker.
Configuration restarts keep the user intent enabled. Plugin `stop()` and `close()`
retain synchronous completion while sharing the lifecycle queue with startup.

The paired release API AARs are built together. Their `minCompileSdk = 36`
metadata permits the compileSdk 36 plugin to consume this compileSdk 37 host
build without introducing any SDK 37 API usage into the contracts.

Validation on 2026-09-13: 19 host MCP JVM tests and real Binder round trips on
API 24 (x86 AVD) and API 35 (Xiaomi 23046RP50C), 1/1 each. API 24 UI evidence
confirms activation, drawer start, long-press settings, Plugin Center settings,
and notification stop synchronizing the drawer. The plugin repository's
`docs/dev/p4-settings-and-drawer.md` records the paired build and remaining
client / OEM acceptance work. The implementation is isolated on `feat/mcp-p4`
from existing unrelated changes in the original host worktree.

Host debug and androidTest assemblies passed. Full app lint was canceled after
about 45 minutes without a result; sampled threads were still in lint detector /
Kotlin UAST analysis. This is not a successful host lint check. No host runtime
dependency changed, and the host release assembly was not repeated in P4.

## Conformance Test App

`test-apps/mcp-server-conformance` (registered in `pluginConformanceTestApps`) is a
test-only fixture of this contract with a distinct package
(`org.autojs.plugin.mcp.server.fake`), debug-only signing and `android:testOnly`.
It implements `IMcpServerPlugin` and `IMcpServerSession` without opening a socket,
and every session reads the broker info and sends one `device.info` request through
the host broker before it reports `running`. Five components, selected by manifest
meta-data, cover the normal contract plus the hostile Binder cases: a
`mcpServerContractVersion` above the supported range, a `statusLastError` far beyond
its cap together with an inline bridge request above `MAX_BRIDGE_INLINE_JSON_BYTES`,
duplicated status and event callbacks with one more after `close()`, and a plugin
process that dies while the host holds the session. The fixture README lists the
components and the fixture-only status keys.

The host side is `McpServerAndroidConformanceTest` (`app/src/androidTest`, opt-in
with `-e autojs.mcpServer.conformance.enabled true`). Because `McpServerPluginHost`
pins the production package, the test binds the fixture components directly and
verifies `validatePluginInfo`, the `McpServerBundles` caps, and the real
`McpHostCapabilityBroker` under the default grant: the wrong contract version is
rejected before any `openServer`, the codec cuts the oversized error to 4 KiB, the
broker answers `resource-limit` to the oversized request and `process-dead` after
`destroy`, duplicate callbacks arrive in order and parse, and the session binder's
death recipient fires. The rebind schedule of `McpServerSessionController` is not
reachable with a foreign package and stays covered by the lifecycle matrix with the
production plugin; giving the controller a test seam for the fixture is a separate
decision.

## V2 extension: shared host capability broker

Three Stove Agent roadmap D33 introduces `plugin-api/host-capability-api`. MCP contract V2
depends on that module and appends one transaction after `getInfo`,
`getCapabilities`, and `openServer`:

```aidl
IMcpServerSession openServerV2(in Bundle config,
    IHostCapabilityBroker broker, IMcpServerCallback callback);
```

The host accepts MCP contract versions 1 through 2. It reads the plugin's declared
`mcpServerContractVersion` before selecting the transaction. Version 1 uses
`openServer` and `IMcpHostCapabilityBroker`; version 2 uses `openServerV2` and
`IHostCapabilityBroker`. Selection never depends on a failed transaction probe.
The V1 configuration envelope remains version 1; the V2 open envelope is version
2. Existing session configuration/reason records remain V1-compatible, and V2
plugins must continue to accept them. The shared broker data-plane version is 1,
independent of the MCP control-plane version.

`McpServerContract.KEY_BRIDGE_*`, `KEY_GRANT_*`, broker keys and shared limits are
compile-time aliases of the shared contract. Their released wire literals and
limits are unchanged. Both Binder adapters dispatch through the same host core.
See [Host capability contract V1](host-capability-contract-v1.md) for descriptor
ownership, grant evaluation, bounds and stable error categories.

Shared broker-info Bundles report data-plane `contractVersion = 1`; bridge requests
and responses retain their released envelope without requiring that key. In
particular, the V2 control-plane version is not copied into bridge responses.
Both adapters retain `bridgeResponseJson` and `bridgeResponseOk` when a response
uses `bridgePayloadFd`. The current shared core can also move a JSON result larger
than 64 KiB into an `application/json` descriptor. Its inline `result` is then a
`payload` metadata object, and the descriptor contains the original result JSON.
This conversion is shared by V1 and V2; changing the Binder descriptor does not
change how clients decode that payload.

This host extension does not migrate a separately installed MCP plugin by itself.
The plugin repository migrated in its 1.1.0 line (2026-10-11): it stages the
common/shared/MCP AARs of host build 5329 under its hash lock, implements both
`openServer` and `openServerV2`, and declares version 2 only to installed hosts
from build 5283 on. Older 6.8.0 builds reject version 2 in `validatePluginInfo`,
so the plugin declares version 1 to them; since the line also requires build 5298
(node references, below), such hosts can read its metadata and show that requirement.
Released plugin APKs up to 1.0.3 declare version 1 and stay on the legacy path.
Device evidence: a 5329 host opened the listener through `openServerV2` and a
5282 host through `openServer`, both with tool calls through the selected broker.

Three Stove Agent's complete P1 integration declares host build 5285 (AutoJs6 6.8.0).
That family-specific requirement does not raise the minimum for existing MCP V1
plugins or make Three Stove Agent depend on an installed MCP server. The MCP plugin's
1.1.0 line uses the host compact dumps and nodeRef handling instead of its own
formatter and references: `ui_dump` returns the host's compact text and `snapshotId`,
and its action tools pass `{nodeRef, snapshotId}` to the host, which relocates the node
or answers `NODE_REF_STALE`. That line requires host build 5298, the first build after
the relocation fixes of 5290 - 5297. The host's installed
V1 startup/stop regression and local V1/V2 transaction-selection checks pass
alongside the Agent entry tests; see
[P1.5 evidence](evidence/ai-agent-p15-20260923.md).
