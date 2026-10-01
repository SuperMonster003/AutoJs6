# Installer Plugin Protocol V1

Status: V1 contract module `plugin-api/installer-api` written on 2026-09-30 for plugin roadmap
P1.2 of `AutoJs6-Plugin-Three-Setup-Installer` (3-Setup Installer, package
`io.github.supermonster003.autojs6.plugin.three.setup.installer`). It freezes the four AIDL
interfaces, the Bundle keys, the JSON documents, the closed value sets, the error vocabulary and
the ceilings listed here. Transaction numbers are frozen in declaration order
(`InstallerAidlOrderTest`); a later contract version may only append methods and optional keys.
The host side (discovery, session client, install router and degradation, plugin roadmap P1.3)
and the removal of the host's own installer (P1.4) follow in the same host line; the delivering
host build is recorded in `InstallerIds.REQUIRED_HOST_VERSION_CODE` (P1.5). The plugin side
implements the operations in plugin roadmap P2 and the dialogs in P3. The script API `installer`
(plugin roadmap P4) is delivered by AutoJs6 6.8.0, host build 5300, as described below. This script
layer keeps the V1 AIDL and contract version unchanged. The refreshed contract AAR adds the
optional JSON field name `FIELD_BATCH` for preserving a one-item script array.
The later installation-history integration adds optional `FIELD_SOURCE_ORIGIN` metadata to
distinguish host entries from scripts, also without changing V1 AIDL or script API signatures.

The subsequent P8 host extension is specified in [Installer Plugin Protocol V2](installer-plugin-protocol-v2.md).
This document retains the V1 wire vocabulary and transaction order. The current host supports
V1 and V2; the compatible baseline host remains 5299, and optional capabilities are negotiated
before new methods, authorizers or presentation modes are sent.

## Decision

Package installation is a separate plugin APK with its own UID and processes. The privileged
transports (Shizuku `UserService`, libsu `RootService`), the hidden package-installer interfaces,
the staging of split sets and the confirmation dialogs live in that plugin. The host contributes
discovery, a per-request Binder client, file transfer through read-only descriptors, a routing
entry that every host installation goes through, and the script objects; without the plugin the
host hands the package to the system installer and shows an installation guide (plugin roadmap
D3, D4, D7, D21).

The stable engine, variant and official plugin id are `installer` / `default` /
`three-setup-installer`. The discovery actions are:

- `org.autojs.plugin.INFO` (plugin info service, category `installer`)
- `org.autojs.plugin.INSTALLER` with category `installer` (plugin service)

Both services are protected by the signature permission `org.autojs.permission.PLUGIN`.
Discovery does not grant authority. Before binding, the host requires an exported and enabled
service that declares the plugin permission and answers the action plus category query of its
own package. The plugin info must carry engine `installer`, variant `default`, an
`installerContractVersion` inside the supported range and a `requiresHostVersion` that is not
below `REQUIRED_HOST_VERSION_CODE`. The package name is not pinned: a second implementation of
the same engine may ship under another plugin id, and the existing Plugin Center authorization
and enable records decide which package the host binds. Plugin-side caller checks (host package,
signer, UID) belong to the plugin repository.

Data never travels by absolute path. The host opens read-only `ParcelFileDescriptor`s for the
packages it wants installed; the plugin duplicates them, streams the bytes into a
`PackageInstaller` session (its own or the privileged one) and closes its copies before the
terminal callback. The plugin therefore needs no storage permission and no access to the host's
private directories (plugin roadmap D16).

## V1 Binder Surface

```aidl
IInstallerPlugin
    PluginInfo getInfo()
    Bundle getCapabilities()
    Bundle getAuthorizerState(String authorizer)
    void requestAuthorizer(String authorizer, IInstallerCallback callback)
    void inspect(ParcelFileDescriptor source, Bundle request, IInstallerCallback callback)
    IInstallerSession openSession(ParcelFileDescriptor[] sources, Bundle request, IInstallerSessionCallback callback)
    void uninstall(Bundle request, IInstallerCallback callback)
    Bundle getDefaultInstallerState()
    void setDefaultInstaller(boolean enable, Bundle request, IInstallerCallback callback)
    void getUsers(Bundle request, IInstallerCallback callback)

IInstallerSession
    String getId()
    Bundle getStatus()
    boolean cancel()
    void close()

oneway IInstallerSessionCallback
    void onStage(String sessionId, String stage, Bundle detail)
    void onProgress(String sessionId, float progress, Bundle detail)
    void onCompleted(String sessionId, Bundle result)
    void onFailed(String sessionId, Bundle error)

oneway IInstallerCallback
    void onResult(Bundle result)
    void onError(Bundle error)
```

### Method semantics

- `getInfo`, `getCapabilities`, `getAuthorizerState` and `getDefaultInstallerState` are
  synchronous and cheap: they read cached state or query the package manager and never start a
  privileged process. `getAuthorizerState("root")` reports whether a `su` binary is reachable and
  whether an earlier grant is cached; it does not prompt.
- Every other method is asynchronous and answers exactly once through its callback (`onResult`
  or `onError`; `onCompleted` or `onFailed` for a session). A method that throws a
  `SecurityException` at the Binder boundary (caller check) answers nothing.
- Invalid explicit names passed to the synchronous `getAuthorizerState` return `INVALID_ARGUMENT`
  under `errorJson`, rather than attempting to marshal a plugin-specific exception across Binder.
- `openSession` returns a session handle or `null`. `null` means the request was refused before
  a session existed (ceiling violation, malformed request); the reason arrives through
  `onFailed` with the `hostSessionId` of the request as the session id.
- A session passes through the stages `pending`, `preparing`, `confirming` (only with the
  `dialog` interaction or the `none` authorizer), `writing`, `committing` and one terminal stage
  (`completed`, `failed`, `cancelled`). Batch sessions repeat `preparing` to `committing` per
  item; the `detail` document names the item by `index`.
- `cancel` returns `false` once the session is terminal; a cancelled session still reports
  `onFailed` with `CANCELLED` (or `onCompleted` with per-item errors for a batch that was
  running with `continueOnError`).
- `close` releases staging files; the host calls it after the terminal callback or when it gives
  up. Sessions the host never closes are reclaimed by the plugin ten minutes after they end.

## Bundle Keys

| Key | Type | Direction | Content |
| --- | --- | --- | --- |
| `contractVersion` | Int | host -> plugin | `InstallerContract.CONTRACT_VERSION` of the writer |
| `hostVersionCode` | Long | host -> plugin | host `versionCode` |
| `requestJson` | String | host -> plugin | request document of the method |
| `resultJson` | String | plugin -> host | result document (`onResult`, `onCompleted`) |
| `errorJson` | String | plugin -> host | error document (`onError`, `onFailed`) |
| `detailJson` | String | plugin -> host | stage / progress detail (`onStage`, `onProgress`) |
| `statusJson` | String | plugin -> host | status document (`getAuthorizerState`, `getStatus`, `getDefaultInstallerState`) |
| `hostSessionId` | String | host -> plugin | id the host assigned to an `openSession` request |

Every string under a `*Json` key is at most `MAX_JSON_BYTES` (64 KiB); both sides reject longer
documents with `INVALID_ARGUMENT`.

The plugin bounds optional response metadata before serialization. If an installation result would
exceed the ceiling, it preserves each item's outcome, version code, authorizer and actual interaction,
but may omit version names, long notes and oversized error text. Successful items explain this in
`notes`; errors explain it in `message` while retaining their code and platform status. A confirmed
installation must not be changed into failure solely because its metadata exceeds the transport budget.

## JSON Documents

### Install request (`requestJson` of `openSession`)

```json
{
  "id": "host-generated id",
  "batch": true,
  "sourceOrigin": "host",
  "sources": [
    { "item": 0, "displayName": "app.apk", "size": 12345678 },
    { "item": 1, "displayName": "base.apk", "size": 1000 },
    { "item": 1, "displayName": "split_config.arm64_v8a.apk", "size": 2000 }
  ],
  "interaction": "auto | dialog | silent",
  "options": {
    "authorizer": "auto | none | shizuku | root",
    "allowDowngrade": false,
    "allowTestOnly": false,
    "bypassLowTargetSdk": false,
    "installer": "package name or null",
    "user": "current | all | <user id>",
    "deleteSource": false,
    "continueOnError": true,
    "timeoutMillis": 1800000
  }
}
```

`sources[n]` describes descriptor `n` of the `sources` array in order. Items with the same
`item` value form one split set (plugin roadmap D20); a batch has several `item` values. At most
`MAX_BATCH_SOURCES` items and `MAX_SPLITS_PER_PACKAGE` descriptors per item are accepted.
The optional root `batch` boolean preserves the caller's batch shape independently of item count.
`true` keeps the `results` envelope and the batch `continueOnError` behavior for even one item;
`false` requires exactly one grouped item. A present non-boolean value, including null, is
`INVALID_ARGUMENT`. If omitted, the plugin infers batch mode from whether there is more than one
grouped item, preserving requests from older hosts. Hosts that do not specify batch shape in their
client calls retain that same inference. Older V1 plugins can ignore the optional field; preserving
one-item array failure semantics requires the updated plugin implementation. No AIDL transaction
or contract version changes, and the host and plugin share `InstallerContract.FIELD_BATCH`.

The optional root `sourceOrigin` string identifies the entry point for the plugin's installation
history. The Binder request accepts only `host` or `script`, using
`InstallerContract.FIELD_SOURCE_ORIGIN`, `SOURCE_HOST` and `SOURCE_SCRIPT`. Updated host entry
points default to `host`; the script installation service explicitly sends `script` for single,
batch and split requests. Requests from older clients that omit this metadata are recorded as
`host`; JSON null is treated as missing. Other non-null values are rejected with `INVALID_ARGUMENT`.
Older V1 plugins may ignore this optional field. The plugin's `external` and `home` history labels
are assigned by its own entry points and are not accepted in host request documents or read from
external Intent options. This field changes display metadata only, not authorization, interaction,
installation options, result shape or caller ownership. It is not a JavaScript option: existing
script calls and TypeScript declarations remain unchanged, as do AIDL transactions and the V1
contract version.

`deleteSource` only asks the plugin to report `sourceDeleted`; the party that owns the file
deletes it (D25), so for host-provided descriptors the host deletes after `completed`.

### Install result (`resultJson` of `onCompleted`)

One item:

```json
{
  "ok": true,
  "packageName": "com.example.app",
  "versionName": "1.2.3",
  "versionCode": 123,
  "previousVersionCode": 100,
  "authorizer": "shizuku",
  "interaction": "silent",
  "durationMillis": 1834,
  "sourceDeleted": false,
  "notes": ["bypassLowTargetSdk ignored below API 34"]
}
```

A batch wraps them: `{ "results": [ { ... }, { "ok": false, "error": { ... } } ] }` in item
order; a failed item carries the error document instead of the version fields.

### Error (`errorJson`, and `error` inside a failed batch item)

```json
{
  "code": "INSTALL_FAILED",
  "message": "human readable, English",
  "status": 4,
  "systemMessage": "INSTALL_FAILED_VERSION_DOWNGRADE: ...",
  "packageName": "com.example.app",
  "retryable": false
}
```

`status` is `PackageInstaller.STATUS_*` when the platform produced the failure, otherwise
absent. When included, `systemMessage` is `EXTRA_STATUS_MESSAGE` verbatim; the response budget rule
above permits omitting oversized text with an explicit explanation. `retryable` defaults per
`InstallerErrorCodes.RETRYABLE_DEFAULTS` when absent.

### Stage and progress detail (`detailJson`)

`{ "index": 0, "packageName": "com.example.app" }` for `onStage`;
`{ "index": 0, "bytesWritten": 1048576, "totalBytes": 12345678 }` for `onProgress`.

### Session status (`statusJson` of `IInstallerSession.getStatus`)

`{ "state": "writing", "index": 0, "progress": 0.42 }`; `state` is one of the stages.

### Authorizer status (`statusJson` of `getAuthorizerState`)

`{ "available": true, "running": true, "granted": false, "reason": "text or absent" }`.
`available` means installed and supported (Shizuku), or an executable `su` binary found or root
already confirmed (root). An active Shizuku server below API 11 is unavailable with an explanatory
`reason`. `running` means the Shizuku binder is alive (always `true` for root and none), and `granted`
means permission is held or the latest root authorization succeeded. Reading state never starts an
authorization prompt. Root requests wait at most 10 seconds (or the shorter caller deadline);
timeouts report `TIMEOUT`, while a completed refusal reports `AUTHORIZER_DENIED`.

### Default installer status (`statusJson` of `getDefaultInstallerState`, `resultJson` of `setDefaultInstaller`)

`{ "component": "pkg/cls or null", "isSelf": false, "method": "preferred | persistent | none",
"requiresClear": false }`. `requiresClear` is `true` when another application holds a legacy
default that the privileged path cannot replace (plugin P0.2 evidence, `DEFAULT_REQUIRES_CLEAR`);
the plugin's own UI then sends the user to the system "open by default" page.

### Uninstall request and result

Request: `{ "packageName": "com.example.app", "keepData": false, "user": "current",
"authorizer": "auto", "interaction": "auto" }`. Result: `{ "ok": true, "packageName": "...",
"authorizer": "root" }`.

### Users request and result

Request: `{ "authorizer": "auto" }`. Result: `{ "users": [ { "id": 0, "name": "Owner",
"primary": true, "running": true } ] }`.

### Package archive info (`resultJson` of `inspect`)

```json
{
  "format": "xapk",
  "installable": true,
  "packageName": "com.example.app",
  "versionName": "1.2.3",
  "versionCode": 123,
  "label": "Example",
  "minSdk": 24,
  "targetSdk": 35,
  "size": 12345678,
  "icon": "base64 PNG, at most 64 KiB, or absent",
  "splits": [ { "name": "base.apk", "size": 1000, "selected": true, "reason": "base" } ],
  "signatureSchemes": ["v2", "v3"],
  "installed": { "versionName": "1.0.0", "versionCode": 100, "signerMatch": "match", "installer": "com.android.vending" },
  "problems": []
}
```

`format` is one of `InstallerContract.FORMATS`; `aab` and `unknown` are reported with
`installable: false`.

## Operations

| Operation | Authorizers | Notes |
| --- | --- | --- |
| install (single, split set, batch) | none, shizuku, root | `none` always confirms through the system dialog; `silent` requires shizuku or root (`AUTHORIZER_REQUIRED` otherwise) |
| allowDowngrade, allowTestOnly, bypassLowTargetSdk, installer, user | shizuku, root | mapped to `INSTALL_REQUEST_DOWNGRADE | INSTALL_ALLOW_DOWNGRADE`, `INSTALL_ALLOW_TEST`, `INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK` (API 34+, otherwise noted), installer package name, `userId` / `INSTALL_ALL_USERS`; requested without a privileged authorizer they fail with `AUTHORIZER_REQUIRED` |
| uninstall | none, shizuku, root | `none` uses the system uninstall confirmation; `keepData` and `user` need a privileged authorizer |
| inspect | none | never installs; AAB is described but `installable: false` |
| default installer | shizuku, root | `addPreferredActivity` for the APK MIME filters (method `preferred`); `persistent` is reserved for a later version; `none` reports the state and lets the UI guide the user |
| users | shizuku, root | `none` reports only the current user |

For installation, `auto` prefers silent execution with a privileged authorizer, but accepts a system
confirmation if the platform still requires one. The successful result then reports `interaction:
"dialog"` and records the fallback in `notes`. Explicit `silent` never displays that confirmation and
fails with `AUTHORIZER_REQUIRED`. A full byte-progress value means the data reached the session stream;
commit validation and synchronization still precede the terminal result.

## Error Codes

`InstallerErrorCodes.ALL`: `PLUGIN_UNAVAILABLE`, `INVALID_ARGUMENT`, `SOURCE_NOT_FOUND`,
`SOURCE_UNREADABLE`, `UNSUPPORTED_FORMAT`, `INVALID_PACKAGE`, `INCOMPATIBLE_DEVICE`,
`AUTHORIZER_UNAVAILABLE`, `AUTHORIZER_DENIED`, `AUTHORIZER_REQUIRED`, `USER_CANCELLED`,
`USER_ACTION_TIMEOUT`, `SIGNATURE_MISMATCH`, `INSUFFICIENT_STORAGE`, `INSTALL_FAILED`,
`UNINSTALL_FAILED`, `BLOCKED_BY_POLICY`, `TIMEOUT`, `CANCELLED`, `INTERNAL`.
`PLUGIN_UNAVAILABLE` is generated by the host only. Platform statuses map as follows:
`STATUS_FAILURE_ABORTED` -> `USER_CANCELLED`, `STATUS_FAILURE_BLOCKED` -> `BLOCKED_BY_POLICY`,
`STATUS_FAILURE_CONFLICT` with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or
`INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` -> `SIGNATURE_MISMATCH`, `STATUS_FAILURE_STORAGE` ->
`INSUFFICIENT_STORAGE`, `STATUS_FAILURE_INCOMPATIBLE` -> `INCOMPATIBLE_DEVICE`,
`STATUS_FAILURE_INVALID` with `INSTALL_FAILED_VERSION_DOWNGRADE` -> `INSTALL_FAILED` (the APK may be
valid but its version is refused); other `STATUS_FAILURE_INVALID` -> `INVALID_PACKAGE`;
`STATUS_FAILURE_TIMEOUT` -> `TIMEOUT`, anything
else -> `INSTALL_FAILED` with `status` and `systemMessage` kept.

A platform `SecurityException` from the regular (`none`) installer maps to `BLOCKED_BY_POLICY`, with
the original message in `systemMessage`; it is not an unavailable/retryable privileged authorizer.

## Ceilings

| Constant | Value |
| --- | --- |
| `MAX_BATCH_SOURCES` | 32 items per session |
| `MAX_SPLITS_PER_PACKAGE` | 64 descriptors per item |
| `MAX_JSON_BYTES` | 65536 |
| `MAX_INSTALLER_PACKAGE_LENGTH`, `MAX_DISPLAY_NAME_LENGTH` | 255 |
| `MAX_ICON_BYTES` | 65536 |
| `MAX_CONCURRENT_SESSIONS` | 4 per plugin process |
| `DEFAULT_USER_ACTION_TIMEOUT_MILLIS` | 300000 (5 minutes waiting for a confirmation) |
| `DEFAULT_SESSION_TIMEOUT_MILLIS`, `MAX_SESSION_TIMEOUT_MILLIS` | 1800000 (30 minutes), 7200000 |

## Versioning

- In the original V1 implementation, `InstallerContract.CONTRACT_VERSION` is 1 and the supported range is 1..1. The host reads
  `installerContractVersion` from the capabilities before choosing a code path and never
  guesses a version from exceptions.
- New methods are appended at the end of an interface; new Bundle keys and JSON fields are
  optional and ignored by older readers. The V2 extension adds a maximum-version capability,
  `dhizuku`, installation interaction `notification` and the persistent default operation;
  see the V2 document for its opt-in checks and preserved V1 behavior.
- `InstallerIds.REQUIRED_HOST_VERSION_CODE` names the first host build with this module, the
  shared parser and the host client; the plugin repeats it in its `requiresHostVersion`
  meta-data.

## Security Boundary

- Both plugin services are behind `org.autojs.permission.PLUGIN`. The plugin additionally
  verifies the caller of every asynchronous method: the calling UID must be the UID of the
  installed AutoJs6 host package, that package must be at least the plugin's
  `requiresHostVersion`, and the SHA-256 signer sets of host and plugin must be equal and
  non-empty; otherwise it throws `SecurityException` and nothing reaches a callback.
- Descriptors are read-only and owned by the host; the plugin never receives paths, never writes
  to the descriptors and never hands its privileged Binder to the host.
- The host validates every script-supplied source (existence, type, readability, size, count)
  before opening a descriptor and rejects requests over the ceilings before the Binder call.
- `deleteSource` is executed by the owner of the file only; the plugin deletes nothing it did
  not open itself.
- The plugin logs stage names, error codes, sizes and durations; it never logs package contents
  or host paths.

## Host Client (P1.3)

Package `org.autojs.autojs.core.plugin.installer` of the host app (2026-09-30):

- `InstallerPluginHost`: the `AidlPluginHost<IInstallerPlugin>` wrapper (action
  `org.autojs.plugin.INSTALLER`, category `installer`, enabled by default, pre-bind validation of
  export / permission / action + category, `validatePluginInfo` requiring engine `installer`,
  variant `default`, a supported `installerContractVersion` and `requiresHostVersion >=
  InstallerIds.REQUIRED_HOST_VERSION_CODE`). Synchronous contract methods run on the pooled
  binding; every asynchronous method runs on a dedicated binding lease with a
  `suspendCancellableCoroutine` around the one-shot callback and a timeout (60 s for `inspect`
  and `getUsers`, the user-action ceiling for `requestAuthorizer` and `setDefaultInstaller`, the
  request timeout for `uninstall`). `openSession` keeps the lease inside the returned
  `InstallerSessionHandle`.
- `InstallerSessionHandle` / `InstallerSessionCallbackBinder`: the callback Binder settles the
  outcome exactly once and ignores foreign session ids; the handle links a death recipient to
  the session Binder (`PLUGIN_UNAVAILABLE`, retryable), `await()` applies the request timeout and
  cancels on expiry, `close()` closes the plugin session, the host descriptors and the lease.
- `InstallerSource`: read-only descriptors opened from a `File` (existence, regular file,
  readability and non-empty size checked) or a `content://` / `file://` `Uri` (display name and
  size from `OpenableColumns`, falling back to `statSize`); the host owns and closes them.
- `InstallerJson` / `InstallerBundles` / `InstallerErrorMapper` / `InstallerError`: the codec of
  the request, result, error, detail and status documents with the contract ceilings, the Bundle
  envelope carrying `contractVersion`, `hostVersionCode` and `hostSessionId`, and the mapping of
  host-side throwables to contract codes.
- `PackageInstallRouter`: the single entry of every host installation (file manager, Plugin
  Center, finished build, self-update). With an available plugin it opens a session with the
  `dialog` interaction and lets the plugin's dialogs run; without one a plain APK goes to the
  system installer through a `content://` URI (one hint per process names the plugin) and every
  other format shows `InstallerPluginUi.showUnavailableDialog` (missing / disabled /
  unauthorized / incompatible, with a Plugin Center button). `installAndAwait` serves the plugin
  install wizard: the session outcome on the plugin path, `PackageChangeObserver.awaitInstalled`
  (`PACKAGE_ADDED` / `PACKAGE_REPLACED`, 5 minutes) on the system path.
- Plugin Center registration: `SERVICE_ACTION_BY_ENGINE` maps `installer` to the action,
  `InstalledPluginRepository` queries the action + category and discovers through
  `InstallerPluginHost`, the manifest `<queries>` block declares the intent, and the install
  wizard catalog lists `three.setup.installer` under Tools.

P1.4 removed the host's own installer (`PackageInstallerActivity`, the exported
`PackageInstallerEntryActivity` with its `${installerEnabled}` placeholder,
`AndroidPackageSessionInstaller`, the `PackageInstallStatus*` coordinator, receiver, activity and
store, the legacy `IntentUtils.installApk` helpers and fourteen strings only they used).
`ApkInfoDialogManager` and `DisplayManifestActivity` stay as the read-only package information
and now read the shared parser module (`docs/dev/package-archive-parser-v1.md`).

## Script API (P4)

AutoJs6 6.8.0, build 5300, exposes the global `installer` and its `$installer` alias. The script
surface is implemented in `runtime/api/installer` and `runtime/api/augment/installer`; each
`ScriptRuntime` owns an `InstallerService` and closes it on exit. The original V1 host-client
minimum remains `InstallerIds.REQUIRED_HOST_VERSION_CODE = 5299`; build 5300 adds the script
surface and optional batch-shape metadata without changing V1 AIDL transactions or the contract
version. Full signatures and examples are maintained in
the [installer module reference](https://docs.autojs6.com/#/installer) and
[installer TypeScript declarations](https://github.com/SuperMonster003/AutoJs6-TypeScript-Declarations/blob/main/declarations/autojs6/aj6-int-installer.d.ts).

### Public entry points

| Area | Script entry points | Result |
| --- | --- | --- |
| Install or update | `install(source, options?)`, `installAsync(source, options?)`, `session(source, options?)` | `InstallResult`, a Promise of the same value, or an `InstallSession`; an array source produces `InstallResult[]` |
| Uninstall | `uninstall(packageName, options?)`, `uninstallAsync(packageName, options?)` | `UninstallResult`, or a Promise of it |
| Inspect | `inspect(source)`, `inspectAsync(source)` | `PackageArchiveInfo`, or a Promise of it; optional PNG icon data becomes an Android `Bitmap` on the script thread |
| Authorizers | `authorizer.available`, `authorizer.state(name?)`, `authorizer.request(name, timeout?)`, `authorizer.requestAsync(name, timeout?)` | Available concrete names, state, or an authorization boolean |
| Default installer | `isDefault()`, `setDefault(enabled?, options?)`, `setDefaultAsync(enabled?, options?)` | Boolean; `enabled` defaults to `true`, and setting returns whether the requested state was reached, including successful disable |
| Device users | `users()`, `usersAsync()` | `DeviceUser[]`, or a Promise of it |
| Plugin readiness | `isAvailable()`, read-only `status` | Boolean, or `{ available, pluginVersion?, contractVersion?, reason? }` for the selected plugin |
| Errors | `installer.InstallerError` | Script error constructor, shared by thrown errors, Promise rejections and failed item results |

`InstallerError` extends the script `Error` type and exposes `code`, `message` and `retryable`,
plus `status`, `systemMessage`, `packageName` and `authorizer` when known. Failed batch items
carry real `InstallerError` instances in their `error` field.

`isAvailable()` and `status` are probes: a missing, disabled, unauthorized or incompatible plugin
produces `false` or an unavailable status with a reason. Actions throw or reject `PLUGIN_UNAVAILABLE`
in that situation. Script actions use the plugin; the host UI's system-installer degradation does
not apply to this API. `app.uninstall` remains the separate system intent API; `installer.uninstall`
adds plugin results, authorizer selection, `keepData` and target-user options.

`authorizer.available` includes only concrete names whose actual `available` state is true, never
`auto`; availability alone does not imply a running service or a grant. `state()` defaults to
`auto` and aggregates readiness without claiming to know the plugin's configured selection order.
`request("auto")` checks already available, running and granted paths and does not prompt for new
Root or Shizuku authorization. Request a concrete name to initiate that authorization.

### Sources, options and result shape

A source is a local path string, a local `file://` URI, a readable `content://` URI, a
`java.io.File` or an `android.net.Uri`. Relative paths use the script's `files.cwd()`. The host
snapshots and validates arguments on the calling script thread, then opens sources on IO workers.
Content providers may report unknown size (`-1`), including pipe sources; they remain cancellable.
The script must already have access to the URI. A denied or revoked source read permission maps
to `SOURCE_UNREADABLE`, rather than implying that the installer plugin is unavailable.

| Source shape | Meaning and result shape |
| --- | --- |
| `"/sdcard/Download/app.apk"` | One application, one result |
| `[sourceA, sourceB]` | Independent applications, at most 32 items, results in item order; even a one-item array returns an array |
| `{ splits: [base, splitA] }` | One application's non-empty split set, at most 64 files, one result |
| `[{ splits: [base, splitA] }, sourceB]` | A batch containing a split set and an independent application |

The script host sends `batch: true` for array inputs, including a one-item array. With the default
`continueOnError: true`, an invalid archive in `[source]` therefore returns one failed item in the
result array; the same ordinary `source` reports the session failure directly. This is a root
request field, not an additional script option.

`splits` belongs only in a source object. `options.splits` is rejected. `inspect` accepts one
ordinary source, not arrays or explicit split sets; it can describe AAB metadata, while attempting
to install an AAB returns `UNSUPPORTED_FORMAT` and requires APKs generated with bundletool.
Encoded V1 JSON documents retain the 64 KiB ceiling, and installer package names are limited to
255 UTF-8 bytes. The script service runs at most four calls per script and admits at most
36 calls in total, including those waiting for a running slot. Exceeding that admission capacity
reports `INVALID_ARGUMENT`.

Install options are `authorizer`, `interaction`, `allowDowngrade`, `allowTestOnly`,
`bypassLowTargetSdk`, `installer`, `user`, `deleteSource`, `continueOnError` and `timeout`.
Uninstall options are `authorizer`, `interaction`, `keepData`, `user` and `timeout`.
V1 default-installer options contain only `authorizer`; the P8 host adds the optional `mode`
described in the V2 document. Unknown keys, explicit null values,
invalid enum values and out-of-range integers are rejected. Script `timeout` maps to the wire
field `timeoutMillis`; `user` is `"current"`, `"all"` or a non-negative integer user id.
An omitted or empty installer package name uses the plugin. Deletion and privilege flags default
to `false`, `continueOnError` defaults to `true`, and `user` defaults to `"current"`.

Both `authorizer` and `interaction` default to `"auto"`. **Scripts install silently by default
when a privileged authorizer is available** (D32). The plugin applies its configured authorizer
order; an explicitly selected authorizer does not fall back to another one. Without privilege,
`auto` uses confirmation dialogs. If the system still requests confirmation on a privileged path,
`auto` permits it and the result records `interaction: "dialog"` with a note. Use `dialog` to
request confirmation explicitly. Explicit `silent` reports `AUTHORIZER_REQUIRED` when a
confirmation would be needed.

### Sessions, script threads and cancellation

`session` returns an EventEmitter with a stable `id`, read-only `state` and `result`, plus
`cancel()` and `wait(timeout?)`. Events are `stage`, `progress`, `complete`, `error` and `cancel`.
The event methods are `on`, `addListener`, `once`, `off`, `removeListener`, `prependListener`,
`prependOnceListener`, `removeAllListeners`, `listenerCount`, `listeners`, `eventNames`,
`getMaxListeners`, `setMaxListeners` and `emit`. The object belongs to its creating script thread.
`cancel()` reports whether a new local cancellation request was accepted; an already observed
terminal result wins over a late cancellation. `result` is absent until completion and follows
the original single-source or array-source shape. A completed batch may contain failed items.
Progress is in `0..1`; reaching `1` does not replace the final installation result.

Synchronous methods wait on the calling script thread and drain that thread's queued installer
events while waiting. IO and Binder callbacks carry host DTOs or plain values, never Rhino
objects. Promise callbacks, event listeners, result conversion and bitmap decoding run on the
creating script thread. Pending Async calls and sessions hold a looper wait token until terminal
delivery and its microtasks finish. An unhandled session `error` is logged to the script console
instead of becoming an uncaught global error.

Blocking methods and metadata getters reject the Android UI thread with `INVALID_ARGUMENT`;
use an Async counterpart where provided. `session` also performs a synchronous availability
check, so create it on a script worker thread. A session cannot recursively call `wait()` from
listeners that are already being drained by its own `wait()`.

`wait(timeout)` limits only that wait. Expiry throws `InstallerError` with code `TIMEOUT`, leaves
the installation running, and permits a later wait. In contrast, install `options.timeout`
covers the whole operation, including waiting for capacity and opening sources; expiry cancels
the session. It defaults to 1,800,000 ms and accepts integer values in `1..7200000`; the same
range applies to an explicit wait timeout. The plugin's default user-confirmation limit remains
300,000 ms. A real terminal outcome already received is preserved when a deadline or cancel
races with its delivery.

Stopping the script rejects new work, cancels queued and running session work, releases local
descriptors and bindings, and suppresses pending script callbacks. Late descriptors or session
handles returned by an in-flight open are closed. V1 one-shot calls such as uninstall, authorizer
request and default-installer changes have no remote cancellation token. Script exit cancels the
host wait and releases its binding, but cannot revoke an operation or permission interaction
already accepted by Android.

### Host-owned source deletion (D25)

Only explicit `deleteSource: true` requests deletion. After confirmed successful item results,
the host attempts to delete path, File and local file-URI sources before publishing the script
terminal result. Content URI sources stay with their owner and report `sourceDeleted: false`
with an explanatory note. Failed or unprocessed items retain their sources. A file shared with
any failed or unprocessed batch item is retained; successful items sharing one file reuse a
single deletion result.

The host retains its read-only descriptors until cleanup and compares the opened file with the
current path by device/inode, size and modification/change timestamps. Symlinks, detected
replacements, changed files and closed descriptors are refused. Callers must keep source paths
stable until completion. Deletion is best effort: inability to delete adds a note and does not
change `ok: true`; `sourceDeleted` is true only when all sources of that item were deleted.

Host cleanup notes must not turn a valid result near the 64 KiB transport ceiling into a script
failure. If enrichment would exceed that ceiling, the host retains the original per-item or
batch wire JSON and updates the typed `sourceDeleted` and `notes` fields. Script conversion
overlays those fields, preserving both plugin metadata and host cleanup notes. This does not
change the V1 transport ceiling or discard an already confirmed result.
