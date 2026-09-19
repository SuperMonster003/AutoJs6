# Mail Plugin Protocol V1

Status: versioned V1 contract. The host contract module `plugin-api/mail-api` (mail roadmap
P1.1) froze the six AIDL interfaces, the Bundle keys, the JSON envelope, the operation names,
the error vocabulary and the ceilings listed here. Transaction numbers are frozen in declaration
order; a later contract version may only append methods and optional keys. The host side of this
protocol (discovery, session client, attachment sink and source, watch bridge) landed with roadmap
P1.2, and the delivering host build is recorded in `MailIds.REQUIRED_HOST_VERSION_CODE`. The
plugin side (Angus Mail, package `io.github.supermonster003.autojs6.plugin.angus.mail`, plugin
roadmap P2, plugin build 12) implements every operation of this document; the cross-process
round trip of P1.3 is `MailPluginRoundTripTest` (see Host Client). The script API `mail` is
roadmap P3 of the plugin repository.

## Decision

Mail access is a separate plugin APK with its own UID and process. Eclipse Angus Mail, the MIME
handlers and the account store live in that plugin and are never loaded into the AutoJs6 process.
The host contributes discovery, a per-session Binder client, file transfer through descriptors
and the script objects; it never sees a mailbox connection (roadmap D1, D2, D25).

The stable engine, variant and official plugin id are `mail` / `default` / `angus-mail`. The
discovery actions are:

- `org.autojs.plugin.INFO` (plugin info service)
- `org.autojs.plugin.MAIL` with category `mail` (plugin service)

Both services are protected by the signature permission `org.autojs.permission.PLUGIN`.
Discovery does not grant authority. Before binding, the host requires an exported and enabled
service that declares the plugin permission and answers the action plus category query of its
own package. The plugin info must carry engine `mail`, variant `default`, a `mailContractVersion`
inside the supported range and a `requiresHostVersion` that is not below
`REQUIRED_HOST_VERSION_CODE`. Unlike the MCP Server contract the package name is not pinned: a
second implementation of the same engine may ship under another plugin id (roadmap F.3), and the
existing Plugin Center authorization and enable records decide which package the host binds.
Plugin-side caller checks (host package, signer, UID) belong to the plugin repository.

The plugin is registered in the Plugin Center with `defaultEnabled = true` (roadmap D19). Sessions
are opened on a dedicated binding lease that is held until the session closes; RemoteExceptions
during `openSession` are not retried.

## V1 Binder Surface

```aidl
IMailPlugin
    PluginInfo getInfo()
    Bundle getCapabilities()
    IMailSession openSession(Bundle account, IMailSessionCallback callback)
    Bundle listProviders()
    Bundle listSavedAccounts()

IMailSession
    Bundle getStatus()
    String call(Bundle request, ParcelFileDescriptor[] descriptors, IMailCallCallback callback)
    void cancel(String requestId)
    IMailWatch watch(Bundle options, IMailWatchCallback callback)
    void close()

IMailCallCallback (oneway)
    void onProgress(Bundle progress)
    void onResult(Bundle response)

IMailWatch
    Bundle getStatus()
    void stop()

IMailWatchCallback (oneway)
    void onEvent(long generation, long seq, Bundle event)

IMailSessionCallback (oneway)
    void onStatus(Bundle status)
```

`MailAidlOrderTest` parses the AIDL sources and guards this order and the `oneway` markers.
Every Bundle written by the host carries `contractVersion` (`KEY_CONTRACT_VERSION`, currently
`1`); the account bundle additionally carries `hostVersionCode`.

### Method semantics

| Method | Threading | Nullability and ownership |
| --- | --- | --- |
| `getInfo` | synchronous, no network | never null |
| `getCapabilities` | synchronous, no network | null is read as an empty bundle |
| `openSession` | synchronous, no network; the first `call` connects | null means the plugin refused the account and reported why through `onStatus` (a oneway callback that may land after the null return, so the host waits up to 3 s for that `closed` status before it falls back to `PLUGIN_UNAVAILABLE`); the host closes the session with `close()` and then releases the binding lease. A caller that is not the installed same-signer host gets a `SecurityException` instead (see Security Boundary) |
| `listProviders`, `listSavedAccounts` | synchronous, no network | JSON under `providersJson` / `accountsJson`; secrets are never included; `listSavedAccounts` is guarded like `openSession`, the other metadata methods are open to every holder of the plugin permission |
| `getStatus` | synchronous | JSON under `statusJson` |
| `call` | returns at once with the request id; the result is delivered exactly once by `onResult` after zero or more `onProgress`, from the session thread of the plugin, never from the Binder thread that accepted the request. The plugin runs the calls of one session one at a time (decision D35) and queues up to `MAX_QUEUED_CALLS` behind the call in flight | descriptors are host-owned (see below); a plugin that cannot accept the request (oversized request envelope, queue full, session closed) still answers through `onResult` (`LIMIT_EXCEEDED` / `SESSION_CLOSED`) after closing its descriptor copies |
| `cancel` | synchronous; a queued call is answered `CANCELLED` before `cancel` returns, the call in flight is interrupted and its sockets are closed, and it still completes exactly once: with `CANCELLED`, unless it had already succeeded, in which case the result stands | unknown and finished ids are ignored |
| `watch` | synchronous; events start after it returns | null means the watch was refused (`MAX_WATCHES_PER_SESSION`, closed session, unusable options, a callback that is already dead) and the reason is the session's `lastError`, which `BinderMailSessionPort` turns into the exception of the host's `watch`; plugin builds from 35 implement watches (feature `idle`): IMAP IDLE renewed every `IDLE_RENEW_MS` with a polling fallback (no IDLE capability, three failed IDLEs), POP3 by UIDL polling, exponential reconnects, `resync` on a UIDVALIDITY change or a queue overflow, and an immediate reconnect when the device's default network changes |
| `close` | synchronous; queued calls fail with `SESSION_CLOSED`, the call in flight is aborted the same way, then the mail connections close and `onStatus` reports `closed` with `reason` `closed`; watches emit a final `closed` event | idempotent; the host's death does the same with `reason` `host-died` |
| `stop` | synchronous; a final `closed` event follows | idempotent |

Descriptor ownership (roadmap B.3): the host creates and owns every descriptor. For
`messages.raw` and `attachments.download` it passes exactly one pipe write end and closes its
copy right after `call` returned, so the read end sees end-of-stream when the plugin closes its
duplicate. For `mail.send` and `messages.append` it passes read-only file descriptors referenced
by index from the request JSON and closes them after `onResult`. The plugin duplicates what it
needs and closes its copies before `onResult`.

Death handling: the host links a death recipient to the session binder and to nothing else; the
plugin links to the callback binders it received and cleans sessions and watches up when the host
dies.

## Bundle Keys

| Key | Type | Written by | Purpose |
| --- | --- | --- | --- |
| `contractVersion` | Int | host | contract version of the writer |
| `hostVersionCode` | Long | host | host `versionCode`, so the plugin can refuse unsupported hosts |
| `accountJson` | String | host | account without secrets (`openSession`) |
| `secretPassword` | String | host | password or provider authorization code (`openSession` only) |
| `secretAccessToken` | String | host | XOAUTH2 bearer token (`openSession` only) |
| `accountAlias` | String | host | alias of a saved account; excludes the three keys above |
| `requestJson` | String | host | request envelope (`call`) |
| `watchOptionsJson` | String | host | watch options (`watch`) |
| `generation` | Long | host | watch generation, echoed by every event of that watch |
| `responseJson` | String | plugin | response envelope (`onResult`) |
| `progressJson` | String | plugin | transfer progress (`onProgress`) |
| `eventJson` | String | plugin | watch event (`onEvent`) |
| `statusJson` | String | plugin | session or watch status (`getStatus`, `onStatus`) |
| `providersJson` | String | plugin | provider preset table (`listProviders`) |
| `accountsJson` | String | plugin | saved accounts without secrets (`listSavedAccounts`) |

Secrets travel only inside the `openSession` bundle. They never appear in any JSON document,
log line, exception message, `toString()`, crash report or changelog entry on either side.

## JSON Documents

All documents are UTF-8 JSON objects; dates are UTC milliseconds since the epoch or `null`
(roadmap D26); binary data never travels inline. A document larger than `MAX_ENVELOPE_BYTES`
(512 KiB) is refused with `LIMIT_EXCEEDED` by the side that would send it.

### Account (`accountJson`)

The `MailAccountOptions` of the script API without `password`, `accessToken` and
`tokenProvider`:

```json
{
  "provider": "qq",
  "address": "user@qq.com",
  "user": "user@qq.com",
  "name": "My Name",
  "auth": "password",
  "receive": "imap",
  "imap": {"host": "imap.qq.com", "port": 993, "tls": "ssl"},
  "pop3": {"host": "pop.qq.com", "port": 995, "tls": "ssl"},
  "smtp": {"host": "smtp.qq.com", "port": 465, "tls": "ssl"},
  "timeout": {"connect": 15000, "read": 60000, "write": 60000},
  "tls": {"trustAll": false},
  "debug": false,
  "clientId": {"name": "AutoJs6", "version": "6.8.0"}
}
```

`auth` is `password` or `xoauth2` (`MailContract.AUTH_MECHANISMS`); `tls` of an endpoint is
`ssl`, `starttls` or `none` (`MailContract.TLS_MODES`). Explicit fields override the provider
preset. `tls.trustAll` marks the account as insecure in every status and summary the plugin
produces (roadmap D25).

### Request (`requestJson`)

```json
{"id": "9a1c...", "op": "messages.list", "args": {"folder": "INBOX", "limit": 50}}
```

`id` is chosen by the host and unique per session; `op` is one of `MailContract.OPS`; `args`
is always an object.

### Response (`responseJson`)

```json
{"id": "9a1c...", "ok": true, "result": []}
{"id": "9a1c...", "ok": false, "error": {"code": "FOLDER_NOT_FOUND", "message": "no such folder: Archive", "details": {"folder": "Archive"}, "retryable": false}}
```

`error.code` is one of `MailErrorCodes.ALL`; unknown codes are reported by the host as
`INTERNAL`. `retryable` may be omitted, in which case the host applies
`MailErrorCodes.RETRYABLE_DEFAULTS`. `message` is truncated to `MAX_ERROR_MESSAGE_BYTES`.

### Progress (`progressJson`)

```json
{"id": "9a1c...", "transferred": 1048576, "total": 5242880}
{"id": "9a1c...", "debug": ["imap connect imap.qq.com:993/ssl password", "..."]}
```

`total` is omitted when unknown. Transfer progress is only sent for `messages.raw` and
`attachments.download`; `mail.send` and `messages.append` report `elapsedMs` in their result
instead. An account with `debug: true` additionally receives one debug document per call, right
before the result, with the redacted protocol trace of that call (hosts, ports, TLS modes,
capabilities, folder names, sizes and durations, never credentials or message content); the host
client decodes it like any progress document (`transferred` reads as 0).

### Status (`statusJson`)

```json
{"state": "open", "connected": [], "queued": 0}
{"state": "open", "connected": ["imap", "smtp"], "queued": 3, "active": "9a1c..."}
{"state": "closed", "reason": "closed", "connected": [], "queued": 0, "lastError": {"code": "AUTH_FAILED", "message": "...", "retryable": false}}
```

`state` is `open` or `closed`; `reason` is present only when closed (`closed` after `close()` or
the `session.close` operation, `host-died` after the host's death). `connected` lists the
protocols with a live connection (`imap`, `pop3`, `smtp`): empty before the first call and
after the idle teardown (`SESSION_IDLE_TIMEOUT_MS`). `queued` counts the calls
waiting behind the one in flight, whose request id is `active` while there is one. `lastError` is
the last failure the session recorded. A watch status additionally carries `mode` (`idle` or
`poll`). The session callback receives `onStatus` when the session closes; a `closed` status
ends the session on the host side.

### Watch options (`watchOptionsJson`)

```json
{"folder": "INBOX", "mode": "idle", "pollIntervalMs": 60000, "fetchBody": false, "generation": 3}
```

`mode` defaults to `idle` when the server advertises IDLE and `poll` otherwise (POP3 accounts
are always polled and refuse `idle`); `pollIntervalMs` is clamped into `MIN_POLL_INTERVAL_MS`
to one hour; `fetchBody` (default false) makes every `message` event carry the body instead of
the envelope alone. Unknown keys are refused with `INVALID_ARGUMENT`.

### Event (`eventJson`)

```json
{"type": "message", "seq": 12, "generation": 3, "folder": "INBOX", "message": {"uid": 4711, "...": "..."}}
{"type": "mode", "seq": 13, "generation": 3, "folder": "INBOX", "mode": "poll"}
{"type": "resync", "seq": 14, "generation": 3, "folder": "INBOX", "reason": "queue-overflow"}
{"type": "error", "seq": 15, "generation": 3, "folder": "INBOX", "error": {"code": "CONNECT_FAILED", "message": "...", "retryable": true}}
{"type": "closed", "seq": 16, "generation": 3, "folder": "INBOX", "reason": "stopped"}
```

`seq` increases by one per event of a watch and `generation` echoes the value from the watch
options. The host drops events of another generation or with a non-increasing `seq`
(`MailWatchBridge`); after `closed` nothing else is delivered. When more than `MAX_WATCH_QUEUE`
events are pending the plugin collapses them into one `resync` event.

## Operations

| op | args | result | descriptors | protocols |
| --- | --- | --- | --- | --- |
| `session.test` | `{}` | `{ok, account, imap?, pop3?, smtp?, elapsedMs}`; each endpoint report is `{protocol, host, port, tls, ok, elapsedMs, capabilities, error?}`; `account` is the account document without secrets plus `insecure` and `debug` | - | all |
| `folders.list` | `{subscribedOnly?, status?}` | `MailFolder[]` (a tree, `children` per folder; `messages` / `unseen` only with `status: true`) | - | IMAP, POP3 (`INBOX` only) |
| `folders.status` | `{folder}` | `{name, path, messages, unseen, recent?, uidNext?, uidValidity?}` | - | IMAP |
| `folders.create` | `{path}` | `MailFolder` | - | IMAP |
| `folders.delete` | `{path}` | `true` | - | IMAP |
| `folders.rename` | `{path, newPath}` | `MailFolder` | - | IMAP |
| `messages.list` | `{folder?, limit?, before?, after?, order?, unseenOnly?}` | `MailMessage[]` (envelopes, newest first unless `order: "asc"`) | - | IMAP, POP3 (no `unseenOnly`) |
| `messages.search` | `{query, folder?, limit?, before?, fallback?}` | `{messages: MailMessage[], fallback: "server" or "client"}` | - | IMAP, POP3 (client filter over headers only) |
| `messages.get` | `{folder?, uid, peek?, includeRaw?}` | `MailMessage` with body; `bodyTruncated` above `MAX_INLINE_BODY_BYTES`, `raw` / `rawTruncated` with `includeRaw` | - | IMAP, POP3 |
| `messages.raw` | `{folder?, uid}` | `{bytes, fileName, mimeType}` | one pipe write end | IMAP, POP3 |
| `attachments.download` | `{folder?, uid, partId}` | `{bytes, fileName, mimeType}` | one pipe write end | IMAP, POP3 |
| `messages.setFlags` | `{folder?, uids, flags, mode?}` | `{uids}` | - | IMAP |
| `messages.move` | `{folder?, uids, target}` | `{uids?}` (the uids in `target` when the server reports `COPYUID`) | - | IMAP |
| `messages.copy` | `{folder?, uids, target}` | `{uids?}` | - | IMAP |
| `messages.delete` | `{folder?, uids, expunge?}` | `{uids}` | - | IMAP, POP3 (`DELE`, committed when the call ends) |
| `messages.expunge` | `{folder?}` | `{count}` | - | IMAP |
| `messages.append` | `{folder, message, flags?}` | `{folder, uid?}` (`uid` when the server reports `APPENDUID`) | N read-only files | IMAP |
| `mail.send` | `{message, saveToSent?}` | `{messageId, accepted, rejected, savedToSent, sentCopy, sentFolder?, saveError?, elapsedMs}` | N read-only files | SMTP |
| `session.close` | `{}` | `true`; the session closes right after this result | - | all |

`message` of `mail.send` and `messages.append` is the `MailSendMessage` of the script API with
each attachment given as `{descriptorIndex, fileName, mimeType?, contentId?, inline?, size}`;
`descriptorIndex` points into the descriptor array of the call. `folder` defaults to `INBOX`.
`uid`, `before`, `after` and `uids` are numbers on IMAP (the folder's UIDs; `uidValidity` comes
from `folders.status`) and opaque UIDL strings on POP3. `flags` is an array of IMAP flag names,
`mode` one of `add` (default), `remove`, `set`. `query` is the `MailSearchQuery` object of the
script API; `fallback` is `client` (default: filter on the client when the server refuses the
SEARCH), `none` (never filter on the client) or `always` (never ask the server, for providers
whose SEARCH misses just-delivered mail), and `fallback` in the result names the path taken.
Unknown argument fields are refused with `INVALID_ARGUMENT`. `MailFolder` and `MailMessage`
follow roadmap appendix A.4 with dates as UTC milliseconds; envelope messages carry
`bodyLoaded: false`.

`sentCopy` of `mail.send` is `server` (the provider files sent mail itself, the plugin never
appends a duplicate), `appended` (the plugin appended the message to `sentFolder`), `failed`
(the message was sent but the append failed, `saveError` says why) or `none` (no copy wanted
or no sent folder); `savedToSent` is true for `server` and `appended`.

On a POP3 account (`receive: "pop3"`) every IMAP-only operation is refused with
`UNSUPPORTED_OPERATION` before any connection; `folders.list` answers `INBOX` alone;
`messages.list` pages by message number with the UIDL as `uid` and headers from `TOP`;
`messages.search` scans headers newest first over at most `MAX_POP3_CLIENT_FILTER` messages and
refuses `body`, `text` and `uid` conditions; `messages.get`, `messages.raw` and
`attachments.download` download the whole message; `messages.delete` issues `DELE` and commits
when the mailbox closes at the end of the call, whatever `expunge` says. Angus Mail fetches POP3
envelopes one at a time (two round trips each), so POP3 callers keep `limit` small.

The capabilities bundle of the plugin carries `mailContractVersion`, `mailProtocols` (`imap`,
`pop3`, `smtp`), `mailAuthMechanisms` (`password`, `xoauth2`), `mailFeatures`,
`mailProvidersVersion`, `mailLibraryVersion` and, from plugin build 27, `mailSettingsVersion`.
Plugin builds from 24 on advertise the features `append`, `clientSearchFallback` and
`savedAccounts` (account store, P4); builds from 35 add `idle` (watches, P5); `backgroundWatch`
(P8) is absent until that phase lands.

`mailSettingsVersion` 1 means the plugin exports the parameterless settings entry
`org.autojs.plugin.MAIL_SETTINGS` (`MailActions.OPEN_SETTINGS`, category DEFAULT, an activity
behind `org.autojs.permission.PLUGIN` that opens the plugin's saved-accounts page and finishes;
an intent carrying data, a clip or extras is ignored). The host reaches it from the developer
options (`MailSettingsPreference` through `OfficialPluginSettingsLauncher`, the same inspection
and recovery guides as the AI provider entry: install, enable in Plugin Center, enable the
application, or update when no trusted entry resolves). Accounts saved there open with
`mail.connect('alias')` and list with `mail.accounts.list()`.

Ceilings the plugin enforces per operation: `MAX_RECIPIENTS` (to + cc + bcc),
`MAX_ATTACHMENTS_PER_MESSAGE`, `MAX_DESCRIPTORS`, `MAX_PAGE_SIZE` (with `DEFAULT_PAGE_SIZE` when
`limit` is absent), `MAX_CLIENT_FILTER` for the client-side search fallback (a plugin constant
`MAX_POP3_CLIENT_FILTER` of 200 on POP3), `MAX_QUEUED_CALLS` per session (the plugin serializes
the calls of a session; `MAX_CONCURRENT_CALLS` is enforced by the host client, decision D35),
`MAX_WATCHES_PER_SESSION`. Exceeding any of them answers `LIMIT_EXCEEDED`.

## Error Codes

| Code | Meaning | Retryable by default |
| --- | --- | --- |
| `INVALID_ARGUMENT` | malformed request, unknown field value, missing required field | no |
| `NO_DEFAULT_ACCOUNT` | alias form without a default account in the plugin | no |
| `ACCOUNT_NOT_FOUND` | unknown alias | no |
| `PROVIDER_UNKNOWN` | unknown `provider` preset | no |
| `AUTH_FAILED` | server rejected the credentials or token | no |
| `AUTH_MECHANISM_UNSUPPORTED` | server offers no mechanism the account can use | no |
| `CONNECT_FAILED` | socket or DNS failure | yes |
| `TLS_FAILED` | handshake or certificate failure | no |
| `TIMEOUT` | connect, read or host-side call timeout | yes |
| `CANCELLED` | `cancel(requestId)` or host cancellation | no |
| `FOLDER_NOT_FOUND`, `MESSAGE_NOT_FOUND`, `ATTACHMENT_NOT_FOUND` | missing target | no |
| `UNSUPPORTED_OPERATION` | operation not available for the protocol or server | no |
| `LIMIT_EXCEEDED` | any ceiling of this document | no |
| `IO_FAILED` | descriptor, pipe or file failure | no |
| `SESSION_CLOSED` | session closed, plugin process died | yes |
| `WATCH_CLOSED` | watch already stopped | no |
| `SEND_REJECTED` | SMTP refused the message or all recipients | no |
| `SERVER_ERROR` | protocol level failure reported by the server | yes, unless the plugin says otherwise |
| `PLUGIN_UNAVAILABLE` | plugin not installed, disabled, unauthorized, incompatible or refusing | only on the process-death branch |
| `INTERNAL` | anything else, including unknown codes | no |

`MailErrorMapper` on the host maps discovery and binding failures to `PLUGIN_UNAVAILABLE`,
transport failures of an open session to `SESSION_CLOSED`, timeouts to `TIMEOUT`, host-side
cancellation to `CANCELLED` and file problems to `ATTACHMENT_NOT_FOUND` or `IO_FAILED`.

## Ceilings

| Constant | Value | Applies to |
| --- | --- | --- |
| `MAX_ENVELOPE_BYTES` | 512 KiB | every JSON document of one transaction |
| `MAX_INLINE_BODY_BYTES` | 256 KiB | text plus html of `messages.get` |
| `MAX_ATTACHMENT_BYTES` | 200 MiB | one download or upload |
| `MAX_ATTACHMENTS_PER_MESSAGE` | 64 | `mail.send`, `messages.append` |
| `MAX_RECIPIENTS` | 500 | to + cc + bcc |
| `MAX_PAGE_SIZE` / `DEFAULT_PAGE_SIZE` | 1000 / 50 | `messages.list`, `messages.search` |
| `MAX_CLIENT_FILTER` | 2000 | envelopes scanned by the client-side search fallback on IMAP (200 on POP3, plugin constant) |
| `MAX_DESCRIPTORS` | 64 | one `call` |
| `MAX_CONCURRENT_CALLS` / `MAX_QUEUED_CALLS` | 4 / 32 | per session: concurrency on the host side, queue depth behind the call in flight on the plugin side (the 34th outstanding call is refused) |
| `MAX_WATCHES_PER_SESSION` | 4 | per session |
| `MAX_WATCH_QUEUE` | 256 | undelivered events before `resync` |
| `MIN_POLL_INTERVAL_MS` / `DEFAULT_POLL_INTERVAL_MS` | 15 s / 60 s | poll mode |
| `IDLE_RENEW_MS` | 24 min | IDLE re-issue (RFC 2177 allows 29) |
| `SESSION_IDLE_TIMEOUT_MS` | 10 min | idle connection teardown inside the plugin |
| `DEFAULT_CONNECT_TIMEOUT_MS` / `DEFAULT_READ_TIMEOUT_MS` | 15 s / 60 s | socket timeouts |
| `MAX_ERROR_MESSAGE_BYTES` | 4 KiB | `error.message` |

Host-side call timeouts (`MailContract.callTimeoutMs`): metadata operations 30 s,
`messages.get` 120 s, transfers the larger of 5 min and the declared size at 64 KiB/s.

The envelope ceiling is checked on both sides of a call: the plugin refuses a request document
above `MAX_ENVELOPE_BYTES` before parsing it and replaces a result above the ceiling with a
`LIMIT_EXCEEDED` error; the host client refuses to send an oversized request.

## Versioning

`mailContractVersion` in the plugin capabilities is compared against
`MailContract.MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION` before the plugin is selected. A later
version may append AIDL methods, add optional JSON fields and add operations; it may not change
the meaning of an existing field or reorder methods. Plugins declare the host build they need
through `requiresHostVersion`, which must be at least `MailIds.REQUIRED_HOST_VERSION_CODE`.

## Security Boundary

- Credentials enter the plugin only through the `openSession` bundle and live in the session
  memory of both processes; the host client keeps them to reopen after a plugin death and drops
  them on `close()`.
- The plugin checks the caller of `openSession` and `listSavedAccounts` before serving it
  (`HostCallerGuard`, the rule of the MCP Server plugin's `HostCallerVerifier`): the calling UID
  must be the UID of the installed AutoJs6 host package, that package must be at least the
  plugin's `requiresHostVersion`, and the SHA-256 signer sets of host and plugin must be equal
  and non-empty; otherwise it throws `SecurityException("Caller is not the installed same-signer
  AutoJs6 host: ...")` and nothing reaches the session callback. `getInfo`, `getCapabilities` and
  `listProviders` are open to every holder of the plugin permission; every `IMailSession` method
  additionally requires the UID that opened the session.
- Attachment file names sent by a server are untrusted: the host strips separators, control
  characters and leading dots, shortens over-long names and resolves clashes with a numeric
  suffix unless the script asked to overwrite (`MailFileNames`). Downloads go through a
  `<target>.part` file and are renamed only after the declared size matched.
- Read-only descriptors are opened by the host from script-supplied paths after existence,
  type, readability, size and count checks (`MailAttachmentSource`).
- `tls.trustAll` is per account, defaults to false and is surfaced as `insecure: true` in the
  account document of `session.test` (and in `client.account` of the P3 script API); an endpoint
  with `tls: "none"` counts as insecure too. There is no global switch (roadmap D25).

## Host Client (P1.2)

`MailPluginHost` wraps `AidlPluginHost` with the action, category and validators above and
offers `discover`, `probe`, `queryServiceCount`, `getCapabilities`, `listProviders`,
`listSavedAccounts` and `openSession`; the Plugin Center lists and probes the engine through the
same object. `MailSessionClient` owns one account: it opens lazily, turns `call` into a
suspending function with the per-operation timeout, cancels on the plugin side when the caller
cancels or the timeout fires, fails pending calls with `SESSION_CLOSED` when the session dies and
reopens on the next call, and, for XOAUTH2 accounts with a token provider, refreshes the token
and retries exactly once after `AUTH_FAILED`. `MailAttachmentSink` and `MailAttachmentSource`
implement the descriptor conventions above; `MailWatchBridge` implements the generation and
sequence checks and hands accepted events to a `MailWatchSink` (`MailWatchRunner` behind the
script's `MailWatch` object, P5). `MailSessionClient` tells registered session-loss listeners
about a plugin death, which is how a watch learns that its plugin side is gone.
`MailJson` and `MailErrorMapper` are pure Kotlin and covered by JVM tests together with the
client state machine, the sink, the file-name rules and the bridge.

`MailPluginRoundTripTest` (androidTest, skipped without an installed mail plugin) is the
cross-process evidence of P1.3 against plugin build 12: it opens a session through
`MailPluginHost.newClient` on a loopback IMAP server owned by the host process, gets `ok` from
`session.test` over the real Binder, force-stops the plugin package while a second call is
blocked in a socket read, sees that call fail with `SESSION_CLOSED` "mail plugin process died"
through the death recipient, and watches the next call reopen the session on its own (new plugin
process, new dedicated binding lease); a death while idle is caught the same way. On 2026-09-18:
API 24 emulator `session.test` 123 ms, `SESSION_CLOSED` 149 ms after the force-stop, reopen plus
`session.test` 139 ms; Redmi 22120RN86C (API 33) 270 ms, 97 ms, 319 ms; three sessions and
three IMAP connections per run. `am force-stop` also delivers `onServiceDisconnected` to the
dedicated binding, whose own death recipient releases the lease.

## Script API (P3)

The `mail` global and the `MailClient` object of the host script API (mail roadmap P3.1 and
P3.2, appendix A.3 of the plugin roadmap) sit on the host client above: every script method
normalizes its arguments in pure Kotlin (`MailScriptArguments`), issues exactly one operation of
the table above through `MailSessionClient.call` and decorates the result on the script thread
(`MailJsResults`). Each method has a synchronous form that blocks the script thread inside the
runtime's coroutine context (stopping the script cancels the call on the plugin side) and an
`Async` form that returns a Promise settled on the script thread; `mail.<method>` forwards to
the default client (`mail.setDefault`) and answers `NO_DEFAULT_ACCOUNT` without one.

| Script method | op | Notes |
| --- | --- | --- |
| `mail.connect(options or alias)` / `connectAsync` | `openSession` (Binder method, no operation) | no network; `client.account` is the account document without secrets |
| `client.test()` | `session.test` | the result document as a plain object |
| `client.send(message, {saveToSent?})` | `mail.send` | attachment paths (or `{path, fileName?, mimeType?, contentId?, inline?}`) become `{descriptorIndex, fileName, mimeType?, contentId?, inline?, size?}` plus read-only descriptors from `MailAttachmentSource`; `date` accepts a `Date`, UTC milliseconds or ISO-8601 |
| `client.folders({subscribedOnly?, status?})` | `folders.list` | the tree as plain objects |
| `client.folderStatus(path)`, `client.folder(path).status()` | `folders.status` | `client.folder(path)` also offers `create()`, `delete()`, `rename(newPath)` |
| `client.createFolder(path)` | `folders.create` | |
| `client.deleteFolder(path)` | `folders.delete` | `true` |
| `client.renameFolder(path, newPath)` | `folders.rename` | |
| `client.fetch({folder?, limit?, before?, after?, order?, unseenOnly?})` | `messages.list` | `MailMessage[]` envelopes |
| `client.search(query, {folder?, limit?, before?, fallback?})` | `messages.search` | the `messages` array with a non-enumerable `fallback` property (`server` or `client`) |
| `client.get(uid or message, {folder?, peek?, includeRaw?})`, alias `fetchBody`, `message.load(options?)` | `messages.get` | a message object supplies `uid` and, unless the options name one, `folder` |
| `client.raw(uid or message, target?, {folder?, overwrite?})` | `messages.raw` | streams into `<uid>.eml` under the working directory, a directory or a file path; returns the path |
| `client.download(attachment or {uid, partId}, target?, {folder?, overwrite?, onProgress?})`, `attachment.download(target?, options?)` | `attachments.download` | same targets; `onProgress(transferred, total)` is called on the script thread by the `Async` form only; the attachment `size` (encoded part size) budgets the timeout and the progress total |
| `client.setFlags(uids, flags, mode?, {folder?})` | `messages.setFlags` | `uids` is a UID, a message or an array of them (the first message's `folder` rides along); returns the UID array |
| `client.markRead(uids)` / `markUnread` / `flag` / `unflag` | `messages.setFlags` | `seen` or `flagged` with `add` or `remove` |
| `client.move(uids, folder, {from?})` | `messages.move` | the target UIDs when the server reported them, else `true` |
| `client.copy(uids, folder, {from?})` | `messages.copy` | same result rule |
| `client.delete(uids, {folder?, expunge?})` | `messages.delete` | the UID array |
| `client.expunge(folder?)` | `messages.expunge` | the count |
| `client.append(folder, message, flags?)` | `messages.append` | the UID or null |
| `client.watch(folder?, {folder?, mode?, pollIntervalMs?, fetchBody?, reconnect?})`, `mail.watch` (P5) | `watch` (Binder method, no operation) | a `MailWatch` emitter (no `Async` form, the plugin answers at once): events `message(message, watch)`, `mode(mode)`, `resync(reason)`, `error(err)` and the single terminal `close(reason)` on the script thread; `stop()`, `folder`, `mode`, `generation`, `isActive`; `mode` is `auto` (default) / `idle` / `poll`; the watch keeps the script alive until it closed; a plugin death yields `error(PLUGIN_UNAVAILABLE)` and, with `reconnect` (default true), a new plugin watch of the next generation on the reopened session plus `resync('rewatched')`, otherwise `close('plugin-died')`; `AUTH_FAILED` on a token account with a `tokenProvider` refreshes and watches again; a script that stops draining gets `error(LIMIT_EXCEEDED)` and `close('overflow')`; `client.close()` closes its watches with `close('closed')`, the script's exit closes them silently |
| `client.close()` | `close` (Binder method) | `isClosed` turns true; later calls answer `SESSION_CLOSED` |

Result decoration: `date` and `receivedDate` are JavaScript `Date` values (decision D26 keeps
the wire format at UTC milliseconds), address objects print as `Name <address>`, attachments and
`bodyParts` carry the `uid` and `folder` of their message plus bound `download` / `downloadAsync`,
messages carry bound `load` / `loadAsync`; the bound methods are non-enumerable, so
`JSON.stringify(message)` and passing a message back into the API keep working. Argument
errors and host failures reach the script as `MailError` (`code`, `retryable`, `details`), the
`Async` forms reject with the same objects; `WrappedIllegalArgumentException` of the host
argument guards maps to `INVALID_ARGUMENT`. With `debug: true` the redacted trace lines of the
progress documents go to `console.verbose`.

## Open Items

- P1.3: closed on 2026-09-18 with `MailPluginRoundTripTest` on the API 24 emulator and a Redmi
  22120RN86C (API 33) against plugin build 12; the plugin repository records it in
  `docs/dev/p2-core-evidence.md`.
- P2: done in plugin build 12 (account layer, SMTP send with sent copies, IMAP receive, the POP3
  degraded path, router, limits, caller guard); the account store behind `listSavedAccounts` and
  the alias form of `openSession` is plugin roadmap P4, watches (`idle`) are P5.
- P3: the script API `mail` (P3.1 connection layer, P3.2 send and receive methods) is in the
  host; the plugin repository records the device evidence in `docs/dev/p3-script-api-evidence.md`
  (QQ and 163 on the API 24 emulator and a Redmi API 33; Gmail waits for a fresh access token).
- P5: watches are in plugin build 35 (`IdleWatcher` / `PollWatcher` / `MailWatchBinder`, feature
  `idle`) and in the host as `MailWatch` / `MailWatchRunner` / `client.watch` / `mail.watch`;
  `MailJson.WatchOptions` gained `fetchBody` and `MailSessionClient` the session-loss listeners
  and `refreshToken`; the plugin repository records the device matrix in
  `docs/dev/p5-watch-evidence.md`.
