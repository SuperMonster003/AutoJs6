# Terminal Plugin Protocol V1

Status: V1 contract module `plugin-api/terminal-api` written on 2026-10-01 for plugin roadmap
P1.1 of `AutoJs6-Plugin-Three-Shell-Terminal` (3-Shell Terminal, package
`io.github.supermonster003.autojs6.plugin.three.shell.terminal`). It freezes the two AIDL
interfaces, the Bundle keys, the Intent extras, the JSON documents, the closed value sets, the
error vocabulary and the ceilings listed here. Transaction numbers are frozen in declaration order
(`TerminalAidlOrderTest`); a later contract version may only append methods and optional keys.
The host side (discovery, Binder client, launcher with its degradation routes, drawer state
monitor, plugin roadmap P1.2) and the removal of the host's own terminal with the legacy data
cleanup (P1.3) follow in the same host line; the delivering host build is recorded in
`TerminalIds.REQUIRED_HOST_VERSION_CODE` (P1.4, build 5304). The plugin side implements the
sessions, the Binder service and the Node.js integration in plugin roadmap P2 and the terminal UI
in P3. The Rhino script API `terminal` is implemented in P4.1-P4.3 (2026-10-02), including
session events and output pipes, without changing the V1 AIDL or the contract version.

## Decision

The terminal is a separate plugin APK with its own UID and processes. The pty, the shell
sessions, the foreground service, the Node.js toolchain (linked from the Node.js Runtime plugin),
the npm helpers, the terminal UI, the session manager and the terminal settings live in that
plugin. The host contributes discovery, a pooled Binder client, the entry points (drawer item and
project toolbar) with a five-state degradation, a drawer state monitor and the cleanup of the data
the built-in terminal left behind. Since host build 5320 the file manager directory menu shows
"Open in terminal" only through the plugin's Explorer Action protocol v23 directory action
(`explorer-action-protocol-v23.md`, 3-Shell Terminal 1.1.0 or later); builds 5304-5319 kept a
hard-coded menu item routed through `TerminalLauncher`. Without the plugin the host
has no terminal: every entry routes the user to the Plugin Center, the plugin settings or an
explanatory dialog (plugin roadmap D3, D10, D25, appendix D Q7).

| Concern | Host | Plugin |
| --- | --- | --- |
| pty, `/system/bin/sh` sessions, signals, exit codes | | owns |
| Foreground service and its notification | | owns |
| Node.js toolchain (`node` / `npm` / `npx` / `corepack` / `yarn` / `pnpm`), npm registry, signer trust of the Node.js Runtime plugin | | owns |
| Terminal screen, key bar, selection, session manager, settings page | | owns |
| Entry points, Plugin Center registration, install wizard catalog | owns | |
| Discovery, binding, contract validation, degradation routes | owns | |
| Session list shown by the drawer | mirrors through the state monitor | source of truth |
| Script API `terminal` (P4) | script surface in the host runtime | executes the sessions |
| Data of the removed built-in terminal (`<filesDir>/terminal`, `key_$_terminal_*`) | deletes, never migrates (D28) | |

The stable engine, variant and official plugin id are `terminal` / `default` /
`three-shell-terminal`. The discovery actions are:

- `org.autojs.plugin.INFO` (plugin info service, category `terminal`)
- `org.autojs.plugin.TERMINAL` with category `terminal` (plugin service)

Two Activity entries complete the surface: `org.autojs.plugin.TERMINAL_OPEN` (session or manager,
see Intent Protocol) and `org.autojs.plugin.TERMINAL_SETTINGS` (parameterless). The services and
both Activities are protected by the signature permission `org.autojs.permission.PLUGIN`.
Discovery does not grant authority. Before binding, the host requires an exported and enabled
service that declares the plugin permission and answers the action plus category query of its own
package. The plugin info must carry engine `terminal`, variant `default`, a
`terminalContractVersion` inside the supported range and a `requiresHostVersion` that is not below
`REQUIRED_HOST_VERSION_CODE`. The Binder client discovers by action and lets the Plugin Center
authorization and enable records choose among several packages; the Activity entries and the
drawer inspection target the official package `TerminalIds.DEFAULT_PACKAGE_NAME`. Plugin-side
caller checks (host package, signer, UID) belong to the plugin repository.

Directories travel as absolute paths because the plugin runs a shell on the user's files; a
shared-storage directory needs the plugin's all-files grant and is refused with
`STORAGE_PERMISSION_REQUIRED` otherwise. Session output travels through pipes (read-only
`ParcelFileDescriptor`s handed to the host, owned by the host); input travels as raw bytes. The
plugin never receives a host Binder other than `ITerminalCallback` and never hands its own
privileged objects to the host.

## V1 Binder Surface

```aidl
ITerminalPlugin
    PluginInfo getInfo()
    Bundle getCapabilities()
    String listSessions()
    String openSession(String requestJson)
    boolean closeSession(String sessionId)
    int closeAllSessions()
    void writeInput(String sessionId, in byte[] data)
    Bundle subscribeOutput(String sessionId, String optionsJson)
    void unsubscribeOutput(String subscriptionId)
    Bundle readTranscript(String sessionId, int maxBytes)
    String getEnvironment()
    void registerCallback(ITerminalCallback callback)
    void unregisterCallback(ITerminalCallback callback)

oneway ITerminalCallback
    void onSessionsChanged(int count, String sessionsJson)
    void onSessionExited(String sessionId, int exitCode)
    void onOutputOverflow(String sessionId, String subscriptionId, long droppedBytes)
```

### Method semantics

- Every method answers immediately (target below 50 ms) and never waits for I/O on the Binder
  thread. Session output travels through the pipe handed out by `subscribeOutput`; state changes
  arrive through `ITerminalCallback`. A method that throws a `SecurityException` at the Binder
  boundary (caller check) answers nothing.
- `getInfo` is identical to the INFO service answer and never null. `getCapabilities` is the live
  capability Bundle keyed by `TerminalCapabilityKeys`; every value is readable without opening a
  session, so the Plugin Center can show compatibility up front and the host chooses its code path
  before the first request.
- `listSessions` returns a JSON array of session documents, possibly empty.
- `openSession` returns a session document or an error document (`INVALID_ARGUMENT`,
  `SESSION_LIMIT`, `DIRECTORY_INACCESSIBLE`, `STORAGE_PERMISSION_REQUIRED`, `PTY_FAILED`,
  `NODE_UNAVAILABLE`). A session whose Node.js toolchain is still being prepared (archive
  extraction on a worker thread) is returned in the `pending` state and announced through
  `onSessionsChanged` once it runs.
- `closeSession` sends `SIGHUP` and `SIGKILL` after the grace period; it returns `false` for an
  unknown id. `closeAllSessions` returns how many sessions it ended.
- `writeInput` accepts at most `MAX_INPUT_BYTES` per call. Unknown ids and exited sessions are
  ignored; the host validates both through `listSessions`.
- `subscribeOutput` answers a Bundle with `subscriptionId` and the read end of the pipe under
  `fd`, or `errorJson` (`SESSION_NOT_FOUND`, `SESSION_CLOSED`, `SUBSCRIPTION_LIMIT`). The options
  document may set `fromStart` to receive the current transcript before live output. Output
  beyond the per-subscription ring buffer (`OUTPUT_BUFFER_BYTES`) is dropped oldest-first and
  reported through `onOutputOverflow`. `unsubscribeOutput` closes the write end; unknown ids are
  ignored.
- `readTranscript` returns the current screen plus scrollback as `text` with `truncated` telling
  whether older text was cut to honor `maxBytes` (capped at `MAX_TRANSCRIPT_BYTES`); `errorJson`
  replaces both for an unknown session.
- `getEnvironment` returns the environment document.
- `registerCallback` keeps at most `MAX_CALLBACKS` listeners and removes one automatically when
  its Binder dies; `unregisterCallback` ignores unknown listeners.
- Callbacks are oneway: the plugin never waits for the host and drops a dead host Binder
  silently. `onSessionsChanged` carries the full `listSessions` answer. `onSessionExited` fires
  when the shell exits; the session may stay listed in the `exited` state until it is closed.
- Lifecycle: a dead host Binder closes the write ends of its subscriptions and removes its
  callbacks, the sessions themselves are user-visible assets and survive. A killed plugin process
  loses every session; the next discovery sees an empty list.

## Bundle Keys

| Key | Type | Direction | Content |
| --- | --- | --- | --- |
| `subscriptionId` | String | plugin -> host | id of the subscription created by `subscribeOutput`, passed back to `unsubscribeOutput` |
| `fd` | ParcelFileDescriptor | plugin -> host | read end of the output pipe, owned by the caller |
| `text` | String | plugin -> host | transcript text (`readTranscript`) |
| `truncated` | Boolean | plugin -> host | older transcript text was cut to honor `maxBytes` |
| `errorJson` | String | plugin -> host | error document when a Bundle-returning method fails |

Requests carry no version envelope: the host validates `terminalContractVersion` and
`requiresHostVersion` from the plugin info before binding, and the plugin validates the caller at
the Binder boundary. Every JSON string is at most `MAX_JSON_BYTES` (64 KiB); both sides reject
longer documents with `INVALID_ARGUMENT`.

## Intent Protocol

The host starts the plugin's `TERMINAL_OPEN` Activity with an explicit component (resolved from
the package by action, category `DEFAULT`), never through a PendingIntent, carrying
`TerminalContract.EXTRA_*` (plugin roadmap D19):

| Extra | Type | Meaning |
| --- | --- | --- |
| `directory` | String | directory to start the session in; at most `MAX_PATH_BYTES` |
| `sessionId` | String | bring an existing session to the front instead of creating one |
| `newSession` | Boolean | always create a new session, even when one already runs in `directory` |
| `command` | String | command line executed after the shell starts; at most `MAX_COMMAND_BYTES` |
| `manager` | Boolean | open the session manager instead of a session |

Defaults are omitted, so an empty request carries no extras and the plugin resumes its most
recent session or creates one in its home. `directory` alone reuses a running session in that
directory. `manager` cannot be combined with any other extra, and `sessionId` cannot be combined
with `newSession` or `command` (`TerminalOpenRequest.validate`). `TERMINAL_SETTINGS` takes no
extras. The host resolves each action to exactly one exported Activity of the package that is
protected by the plugin permission; any other shape makes the plugin `INCOMPATIBLE`.

## JSON Documents

### Open request (`requestJson` of `openSession`)

```json
{
  "cwd": "/storage/emulated/0/Scripts",
  "command": "npm test",
  "env": { "CI": "1" },
  "title": "Scripts",
  "keepOpen": true
}
```

`cwd` is optional (the plugin home when absent), non-blank and at most `MAX_PATH_BYTES`.
`command` is at most `MAX_COMMAND_BYTES`. `env` holds at most `MAX_ENV_ENTRIES` string entries,
each key non-blank and without `=`, each `key=value` at most `MAX_ENV_ENTRY_BYTES`, laid over the
session environment. `title` is non-blank when present. `keepOpen` defaults to `true` and keeps
the interactive shell after `command` finishes.

### Session (`openSession`, items of `listSessions` and `onSessionsChanged`)

```json
{
  "id": "s-3",
  "cwd": "/storage/emulated/0/Scripts",
  "title": "Scripts",
  "createdAt": 1759300000000,
  "alive": true,
  "exitCode": null,
  "pid": 12345,
  "state": "running"
}
```

`id` and `state` are mandatory; the host rejects a document without them or with a state outside
`pending` / `running` / `exited` as `INTERNAL`. `exitCode` is present once the shell exited,
`pid` is `-1` while pending, `alive` defaults to `state != exited`, `createdAt` is epoch
milliseconds. `exited` is the only terminal state.

### Error

```json
{ "code": "SESSION_NOT_FOUND", "message": "no session s-3", "sessionId": "s-3" }
```

Returned as the String answer of `openSession`, under `errorJson` of a Bundle answer, or encoded
as `CODE: detail` in an exception message. The host normalizes the message to at most 4096 bytes
(`TerminalErrorMapper.MAX_MESSAGE_BYTES`) and maps unknown codes and host-only codes reported by a
plugin to `INTERNAL`.

### Subscription options (`optionsJson` of `subscribeOutput`)

```json
{ "fromStart": true }
```

### Environment (`getEnvironment`)

```json
{
  "home": "/data/user/0/<plugin>/files/home",
  "prefix": "/data/user/0/<plugin>/files/usr",
  "shell": "/system/bin/sh",
  "node": {
    "available": true,
    "version": "v22.12.0",
    "packageName": "io.github.supermonster003.autojs6.plugin.nodejs",
    "pluginVersion": "1.5.1",
    "reason": "available"
  },
  "npmRegistry": "https://registry.npmmirror.com",
  "storageAccess": "granted",
  "pluginVersion": "1.0.0",
  "contractVersion": 1
}
```

`contractVersion` is mandatory. `node.reason` is one of the Node CLI states below,
`storageAccess` one of `granted` / `denied` / `not_applicable`.

## Capabilities

| Key | Type | Content |
| --- | --- | --- |
| `requiresHostVersion` | Long | host `versionCode` the plugin requires (read type-agnostically) |
| `terminalContractVersion` | Int | `TerminalContract.CONTRACT_VERSION` implemented by the plugin |
| `terminalFeatures` | String[] | subset of `node-cli`, `output-subscription`, `transcript`, `settings` |
| `terminalMaxSessions` | Int | concurrent sessions accepted, at most `MAX_SESSIONS` |
| `terminalMaxSubscriptions` | Int | output subscriptions per session, at most `MAX_SUBSCRIPTIONS_PER_SESSION` |
| `terminalNodeCli` | String | Node.js toolchain state |

Node CLI states (`TerminalContract.NODE_CLI_STATES`, also the `node.reason` of the environment):
`available`; `disabled` (switched off in the plugin settings); `plugin-missing`;
`plugin-untrusted` (the Node.js Runtime plugin is signed by neither the official key nor the
terminal plugin's key); `plugin-too-old` (predates the `NODE_CLI_*` manifest contract);
`executable-missing`; `exec-denied` (the platform refused to execute the launcher from the
terminal process); `setup-failed` (archive verification or extraction failed).

## Error Codes

`TerminalErrorCodes.ALL`: `PLUGIN_UNAVAILABLE`, `PLUGIN_INCOMPATIBLE`, `INVALID_ARGUMENT`,
`SESSION_NOT_FOUND`, `SESSION_LIMIT`, `SESSION_CLOSED`, `DIRECTORY_INACCESSIBLE`,
`STORAGE_PERMISSION_REQUIRED`, `PTY_FAILED`, `NODE_UNAVAILABLE`, `SUBSCRIPTION_LIMIT`, `TIMEOUT`,
`CANCELLED`, `INTERNAL`. `PLUGIN_UNAVAILABLE` (plugin missing, disabled, unauthorized, binding
failed or process gone; the message tells which), `PLUGIN_INCOMPATIBLE` (contract or minimum host
version not satisfied), `TIMEOUT` and `CANCELLED` are generated by the host only
(`TerminalErrorCodes.HOST_ONLY`).

Host mapping (`TerminalErrorMapper.fromThrowable`, by phase): a `DeadObjectException` during a
call is `SESSION_CLOSED` (the plugin process died with its sessions) and `PLUGIN_UNAVAILABLE`
during discovery or opening; other `RemoteException`s are `INTERNAL` during a call and
`PLUGIN_UNAVAILABLE` otherwise; `SecurityException` is `PLUGIN_UNAVAILABLE`; a coroutine timeout
is `TIMEOUT`, a cancellation or interruption `CANCELLED`; `IllegalArgumentException` is
`INVALID_ARGUMENT` and `IllegalStateException` is `PLUGIN_UNAVAILABLE` before a call and
`INTERNAL` during one, unless the message carries an encoded `CODE: detail`.

## Ceilings

| Constant | Value |
| --- | --- |
| `MAX_SESSIONS` | 16 concurrent sessions |
| `MAX_COMMAND_BYTES`, `MAX_INPUT_BYTES` | 65536 |
| `MAX_ENV_ENTRIES`, `MAX_ENV_ENTRY_BYTES` | 64 entries, 4096 bytes each |
| `MAX_PATH_BYTES` | 4096 |
| `MAX_JSON_BYTES` | 65536 |
| `MAX_CALLBACKS` | 8 registered listeners |
| `MAX_SUBSCRIPTIONS_PER_SESSION` | 4 |
| `MAX_TRANSCRIPT_BYTES`, `OUTPUT_BUFFER_BYTES` | 1048576 (1 MiB) |

Both sides reject anything beyond them with `INVALID_ARGUMENT` (plugin roadmap D22).

## Versioning

- `TerminalContract.CONTRACT_VERSION` is 1; the supported range is 1..1. The host reads
  `terminalContractVersion` from the plugin info before binding and never guesses a version from
  exceptions.
- New methods are appended at the end of an interface; new Bundle keys, extras and JSON fields are
  optional and ignored by older readers.
- `TerminalIds.REQUIRED_HOST_VERSION_CODE` is 5304, the first host build whose committed
  `version.properties` contains this module and the host client (commits `b8f4d6c939` and
  `1dcb393104`); the plugin repeats it in the `requiresHostVersion` meta-data of its INFO and
  terminal services and in `PluginInfo.capabilities`.

## Security Boundary

- Both plugin services and both Activity entries are behind `org.autojs.permission.PLUGIN`. The
  plugin additionally verifies the caller of every Binder method: the calling UID must be the UID
  of the installed AutoJs6 host package, that package must be at least the plugin's
  `requiresHostVersion`, and the SHA-256 signer sets of host and plugin must be equal and
  non-empty; otherwise it throws `SecurityException` and nothing reaches a callback.
- Output pipes are created by the plugin; the host owns and closes the read ends it receives and
  never writes to them. Input is bounded raw bytes; the host validates sizes before the call.
- Shared-storage directories are only reachable with the plugin's own all-files grant; the host
  never proxies file access for the plugin.
- The Node.js toolchain is linked only from a Node.js Runtime plugin signed by the official key or
  the terminal plugin's own key (plugin 1.1.0 adds an explicit "trust this signer" confirmation);
  anything else reports `plugin-untrusted`.
- The plugin logs session ids, error codes, sizes and durations; it never logs transcripts or
  command lines.

## Host Client (P1.2)

Package `org.autojs.autojs.core.plugin.terminal` of the host app (2026-10-01):

- `TerminalPluginHost`: the `AidlPluginHost<ITerminalPlugin>` wrapper (action
  `org.autojs.plugin.TERMINAL`, category `terminal`, pre-bind validation of export / permission /
  action + category, `validatePluginInfo` requiring engine `terminal`, variant `default`, a
  supported `terminalContractVersion` and `requiresHostVersion >=
  TerminalIds.REQUIRED_HOST_VERSION_CODE`). Every method is a suspend function on the pooled
  binding (30 s idle unbind); `discover`, `probe`, `isAvailable` and `queryServiceCount` serve
  the Plugin Center, the others wrap the contract methods and turn error documents and throwables
  into `TerminalPluginException` through `TerminalErrorMapper`.
- `TerminalJson` / `TerminalError` / `TerminalErrorMapper` / `TerminalOpenRequest`: the Gson
  codec of the request, session, error, subscription and environment documents with the contract
  ceilings, the error value and exception, the mapping of host-side throwables and plugin answers
  to contract codes, and the validated extras of the `TERMINAL_OPEN` Intent.
- `TerminalPluginInspector` / `TerminalAvailabilityPolicy`: one package inspection
  (application installed and enabled, contract service with its `requiresHostVersion` meta-data,
  entry and settings Activities, Plugin Center authorization and enablement, last Binder failure)
  decides the five states of plugin roadmap D25 and the route of each:

  | State | Facts | Route |
  | --- | --- | --- |
  | `AVAILABLE` | everything below holds | `OPEN`: start the `TERMINAL_OPEN` Activity |
  | `NOT_INSTALLED` | package missing | `PLUGIN_CENTER`: official scope, searching for the plugin package |
  | `DISABLED` | application disabled by Android | `APPLICATION_SETTINGS`: the system app details page |
  | `DISABLED` | not authorized or disabled in the Plugin Center | `PLUGIN_SETTINGS`: the host plugin settings page |
  | `INCOMPATIBLE` | no contract service, no entry Activity, or `requiresHostVersion` above the running host | `INCOMPATIBLE_DIALOG`: names the required host build or asks for a plugin update, with a Plugin Center button |
  | `UNAVAILABLE` | installed and enabled but the Binder failed | `FAILURE_TOAST`: toast and log |

- `TerminalLauncher`: `open(directory)`, `openNew(directory)`, `openSession(id)`,
  `openManager()` and `closeAll()`; `launch` validates the request, inspects the package and
  follows the route above, returning `true` only when the plugin UI itself was started. It is the
  single entry of the drawer item and the project toolbar. The directory menu entry comes from the
  plugin's Explorer Action (protocol v23, the host discloses the selected directory's canonical
  path) and therefore appears only while a compatible plugin is installed, enabled and authorized.
- `TerminalPluginStateMonitor` (plugin roadmap D26): application-scoped cache of the inspection
  and the session list for the drawer. While the drawer is visible it registers an
  `ITerminalCallback` and mirrors `onSessionsChanged`; once hidden the idle unbinding releases the
  binding and the callback. Package broadcasts (ignoring the `REMOVED` / `ADDED` pair of a
  replacement, `TerminalPluginPackageEventPolicy`) and Plugin Center enablement changes trigger a
  fresh inspection; listeners run on the main thread. The drawer subtitle names the state, the
  switch is enabled only when `AVAILABLE`, and the session count comes from the plugin.
- `TerminalPluginSettingsLauncher` (`ui/settings`): the official plugin settings target that
  starts `TERMINAL_SETTINGS` and explains the install / enable / application-disabled / update
  cases.
- Plugin Center registration: `SERVICE_ACTION_BY_ENGINE` maps `terminal` to the action,
  `InstalledPluginRepository` queries the action + category and discovers through
  `TerminalPluginHost`, the manifest `<queries>` block declares the package, the service and both
  Activity actions, the install wizard catalog lists `three.shell.terminal` under Tools, and the
  installed-plugin icon resolver treats its icon as transparent.

P1.3 removed the host's own terminal (`core/terminal`, `ui/terminal`, the three terminal
preferences, `PtyBridge`, `TerminalSelectionSnapshot`, `TerminalActivity`,
`TerminalSessionService`, their layouts, menu, ids, arrays, preference XML blocks and 57 strings
per locale). `TerminalLegacyCleanup` (D28) runs once per process start with the other startup
maintenance of `App`: it deletes `<filesDir>/terminal` recursively and removes every preference
key starting with `key_$_terminal_` plus `key_$_dialog_terminal_tips`, without migrating anything
and without a marker, so an upgrade installation ends up without the old data after its first
start. The host keeps `ic_terminal_black_48dp`, `text_terminal`, `description_terminal`
(rewritten to name the plugin), `text_open_in_terminal`, `text_terminal_new_session` and
`text_terminal_running_sessions` (D27) and the ten plugin-state strings P1.2 added.

## Script API (P4)

The Rhino globals `terminal` and `$terminal` refer to the same module. Check
`terminal.isAvailable()` before starting work and direct an unavailable user to Plugin Center.
`terminal.state()` returns `available`, `not_installed`, `disabled`, `incompatible` or
`unavailable`. Every API failure is a JavaScript `terminal.TerminalError` (an `Error` subtype)
with `code`, `message` and, where applicable, `sessionId`. An unavailable plugin produces
`PLUGIN_UNAVAILABLE`; a contract or minimum-host mismatch produces `PLUGIN_INCOMPATIBLE`.
Both include the host's localized install, enable or update hint when one is available.

| Operation | Synchronous form | Promise form |
| --- | --- | --- |
| Create a visible shell | `open(dirOrOptions?)` | `openAsync(dirOrOptions?)` |
| List sessions | `sessions()` | `sessionsAsync()` |
| Find one session, or null | `session(id)` | - |
| Close one / all sessions | `close(id)`, `closeAll()` | `closeAsync(id)`, `closeAllAsync()` |
| Bring the terminal to the front | `show(id?)` | `showAsync(id?)` |
| Execute a shell command | `exec(command, options?)` | `execAsync(command, options?)` |
| Execute an npm script | `npm.run(script, dir?, options?)` | `npm.runAsync(script, dir?, options?)` |
| Query shell, Node.js and storage environment | `env()` | `envAsync()` |
| Test availability | `isAvailable()` | `isAvailableAsync()` |
| Inspect availability state | `state()` | - |

`open` accepts a directory string or `{ cwd, env, show, title }`. Relative paths follow
`files.path` against the script's working directory; `cwd` defaults to `files.cwd()` (the
plugin HOME when the script has no working directory). URI paths such as `content://` are
rejected. `show` defaults to true. `env` cannot replace `HOME`, `PREFIX` or `PATH` and observes
the contract ceilings. Unknown option names and invalid shapes fail with `INVALID_ARGUMENT`.
The plugin has its own storage permissions: a host storage grant does not grant the plugin
access. A refused shared-storage directory is `STORAGE_PERMISSION_REQUIRED`.

`exec` adds `{ keepOpen, wait, timeout, stripAnsi }` to these options. `keepOpen` defaults to
true: after the command finishes, an interactive shell remains. **Use `keepOpen: false` when
waiting for the command's exit code.** `wait: true` waits for the session to exit; with the
default `keepOpen: true`, it waits for the remaining interactive shell too. `wait` defaults to
false; `timeout` defaults to 600000 ms and `0` means no limit. A timeout leaves the session
running so it can be inspected or closed explicitly. `npm.run` executes `npm run <script>`
using the same options; it requires the trusted Node.js Runtime toolchain. It does not install
dependencies or bypass Android restrictions on executable files and native addons.

```js
if (terminal.isAvailable()) {
    let result = terminal.exec('echo ready; exit 3', {
        cwd: terminal.env().home, show: false, keepOpen: false, wait: true,
    });
    console.log(result.exitCode); // 3
}
```

Returned sessions expose `id`, `cwd`, `title`, `createdAt` (epoch milliseconds), `pid`, `state`
and `exitCode` (null until observed). `isAlive()` refreshes the snapshot. They inherit the
host EventEmitter operations, including `on`, `once`, `prependListener`, `prependOnceListener`,
`removeListener`, `off` and `removeAllListeners`. Operations on the session are:

| Method | Result and semantics |
| --- | --- |
| `write(textOrBytes)` / `writeAsync(textOrBytes)` | UTF-8 text or byte values 0..255, up to 64 KiB; include `\n` to execute a typed command, `[3]` sends Ctrl+C |
| `show()` / `showAsync()` | Opens the existing session using `TERMINAL_OPEN` |
| `close()` / `closeAsync()` | Whether an existing session was closed |
| `waitFor(pattern, timeout?)` / `waitForAsync(pattern, timeout?)` | First subsequent matching line or prompt fragment; nonempty substring or RegExp, default 600000 ms, `0` unlimited |
| `transcript(maxBytes?)` / `transcriptAsync(maxBytes?)` | Current screen and scrollback text, newest text retained; limit 1..1048576 UTF-8 bytes, further bounded by Binder reply size |

`waitFor` opens its own output subscription starting at the time of the call; it does not
search old transcript text. It returns the matching text, reports `TIMEOUT` when the deadline
passes, and `SESSION_CLOSED` when the stream ends first. It always releases its subscription.
The RegExp uses the host selector API's Java-compatible regex translation; stateful JavaScript
`lastIndex` is not advanced. `transcript` is terminal-rendered text, not an exact raw byte log.

## Session stream and lifecycle (P4.2)

- `output(text)` delivers UTF-8 lines with ANSI control sequences stripped by default.
  `stripAnsi: false` on `exec` / `npm.run` preserves control sequences. Lines are split at
  64 KiB of UTF-8 without splitting a code point. A pipe idle for 100 ms flushes an incomplete
  line as a fragment, allowing scripts to answer prompts that do not end in a newline.
- `exit(code)` comes after the subscribed pipe has drained, once, and no output event follows
  it. The callback is registered before opening sessions so even quick commands retain their
  exit code. A listener for `exit` alone does not open an output pipe.
- `overflow(droppedBytes)` reports output lost because the plugin buffer or host script queue
  filled. Reports are coalesced; the final host count is delivered before `exit` even when the
  script queue was full. This is a lossy observation channel, not an archival log. The Binder
  overflow callback and the pipe are separate transports; reports cannot identify an exact
  byte offset in the output.
- The first output or overflow listener opens a pipe; removing the last such listener closes
  it, including after a `once` listener fires. The first subscription of a session created by
  this script requests the current transcript before live output. Recovered sessions from
  `sessions()` / `session(id)` start with live output. Replay only covers the current screen
  and scrollback of a still-existing session, so output produced before an already-removed
  session was subscribed cannot be recovered.
- Blocking terminal methods run work in a coroutine and drain terminal deliveries on their
  originating script thread while waiting. An `output` listener can call `write` while
  `waitFor` is blocked. Promise results and listeners run on that same thread, using the
  shared dispatcher and script looper. UI-thread synchronous calls are rejected with
  `INVALID_ARGUMENT`; use the Async forms there. This does not promise to pump arbitrary
  application timers while a terminal method blocks.
- Each script owns a callback binding lease and its subscriptions. Script exit cancels reads,
  unregisters its callback, releases the lease and suppresses queued deliveries; it leaves
  user sessions running. Plugin process death destroys its sessions. Pipe cancellation uses
  bounded polling so it works on API 24 without leaving a blocked reader thread behind.

The host examples under `sample/终端/` cover opening the script directory, waiting for an npm
script, and answering a prompt while listening to output. Each starts with an availability
guard. Visible execution uses the same Android activity-start rules as `app.startActivity`;
the system can restrict bringing a screen forward from the background.

The P7 reference pages are `terminal`, `terminalSessionType`, `terminalOpenOptionsType` and
`terminalErrorType` in AutoJs6-Documentation. They cover the module, both calling forms, session
events, error codes and the distinction from `shell()`. The same pages are included in the
Offline Docs asset snapshot. AutoJs6-TypeScript-Declarations 4.32.0 declares `terminal` and
`$terminal`, their session/event types and the exact Promise result types; its consumer tests
exercise valid calls and reject twelve invalid call patterns with TypeScript 5.1.3 and 6.0.3.
Ace consumes that declaration set through its normal generated completion assets.

The complete script API requires a host build containing P4 (6.8.0 / 5315 or later); 5304 is
the base terminal protocol requirement. A plugin release does not add missing API code to an
older host. Independent terminal use does not require a running host.

## Tests

- `plugin-api/terminal-api`: `TerminalAidlOrderTest` freezes the transaction order of both
  interfaces; `TerminalContractTest` pins the literals, the closed value sets, the ceilings and
  the host-only error codes.
- Host JVM (`org.autojs.autojs.core.plugin.terminal`, 25 tests): `TerminalAvailabilityPolicyTest`
  (every row of the D25 table), `TerminalOpenRequestTest` (extras in contract order, ceilings,
  forbidden combinations), `TerminalJsonTest` (documents, ceilings, malformed answers),
  `TerminalErrorMapperTest` (phases, encoded codes, host-only codes from a plugin, message
  truncation) and `TerminalLegacyCleanupTest` (directory removal, key selection, idempotence).
- Device (API 37 x86_64, 16 KB page size AVD, 2026-10-01): the P1.2 entries degrade to the
  incompatible dialog against a P0 plugin build without the entry Activity, the Plugin Center lists
  the plugin as official and enabled, and after seeding the legacy directory and keys the first
  host start removes them and logs the `TerminalLegacyCleanup` result.
