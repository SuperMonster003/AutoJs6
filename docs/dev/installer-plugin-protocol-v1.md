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
implements the operations in plugin roadmap P2 and the dialogs in P3; the script API `installer`
is plugin roadmap P4 and lives in the host. Sections marked "pending" are filled by those phases.

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

- `InstallerContract.CONTRACT_VERSION` is 1; the supported range is 1..1. The host reads
  `installerContractVersion` from the capabilities before choosing a code path and never
  guesses a version from exceptions.
- New methods are appended at the end of an interface; new Bundle keys and JSON fields are
  optional and ignored by older readers. Contract version 2 (plugin roadmap P8) is expected to add
  the `dhizuku` authorizer, the `notification` interaction and the `persistent` default method.
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

## Script API (P4, pending)

The `installer` global (`$installer`), its synchronous, `Async` and session forms and
`InstallerError` are described here once plugin roadmap P4 lands; the reference lives in the
`installer` module of the AutoJs6 documentation.
