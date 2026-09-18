# Mail Plugin Protocol V1

Status: versioned V1 contract. The host contract module `plugin-api/mail-api` (mail roadmap
P1.1) froze the six AIDL interfaces, the Bundle keys, the JSON envelope, the operation names,
the error vocabulary and the ceilings listed here. Transaction numbers are frozen in declaration
order; a later contract version may only append methods and optional keys. The host side of this
protocol (discovery, session client, attachment sink and source, watch bridge) landed with roadmap
P1.2, and the delivering host build is recorded in `MailIds.REQUIRED_HOST_VERSION_CODE`. The
plugin side (Angus Mail, package `io.github.supermonster003.autojs6.plugin.angus.mail`) and the
script API `mail` are roadmap P2 and P3 of the plugin repository.

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
| `openSession` | synchronous, no network; the first `call` connects | null means the plugin refused the account and reported why through `onStatus`; the host closes the session with `close()` and then releases the binding lease |
| `listProviders`, `listSavedAccounts` | synchronous, no network | JSON under `providersJson` / `accountsJson`; secrets are never included |
| `getStatus` | synchronous | JSON under `statusJson` |
| `call` | returns at once with the request id; the result is delivered exactly once by `onResult` after zero or more `onProgress`, from an executor thread of the plugin, never from the Binder thread that accepted the request | descriptors are host-owned (see below); a plugin that cannot accept the request (queue full, session closed) still answers through `onResult` |
| `cancel` | synchronous; the request still completes with `CANCELLED` | unknown ids are ignored |
| `watch` | synchronous; events start after it returns | null means the watch was refused (`MAX_WATCHES_PER_SESSION`, closed session) |
| `close` | synchronous; pending calls fail with `SESSION_CLOSED`, watches emit a final `closed` event | idempotent |
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
```

`total` is omitted when unknown. Progress is only sent for `messages.raw`,
`attachments.download`, `messages.append` and `mail.send`.

### Status (`statusJson`)

```json
{"state": "open"}
{"state": "closed", "reason": "idle-timeout", "lastError": {"code": "AUTH_FAILED", "message": "..."}}
```

`state` is `open` or `closed`. A watch status additionally carries `mode` (`idle` or `poll`).
The session callback receives `onStatus` on every change; a `closed` status ends the session on
the host side.

### Watch options (`watchOptionsJson`)

```json
{"folder": "INBOX", "mode": "idle", "pollIntervalMs": 60000, "generation": 3}
```

`mode` defaults to `idle` when the server advertises IDLE and `poll` otherwise;
`pollIntervalMs` is clamped to at least `MIN_POLL_INTERVAL_MS`.

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
| `session.test` | `{}` | `{imap?, pop3?, smtp?}` each `{ok, latencyMs, capabilities?, error?}` | - | all |
| `folders.list` | `{subscribedOnly?, status?}` | `MailFolder[]` | - | IMAP |
| `folders.status` | `{folder}` | `{name, path, messages, unseen, recent?, uidNext?, uidValidity?}` | - | IMAP |
| `folders.create` | `{path}` | `MailFolder` | - | IMAP |
| `folders.delete` | `{path}` | `true` | - | IMAP |
| `folders.rename` | `{path, newPath}` | `MailFolder` | - | IMAP |
| `messages.list` | `{folder?, limit?, before?, after?, order?, unseenOnly?}` | `MailMessage[]` (envelopes) | - | IMAP, POP3 |
| `messages.search` | `{query, folder?, limit?, before?, fallback?}` | `{messages: MailMessage[], fallback: "server" or "client"}` | - | IMAP, POP3 (client filter) |
| `messages.get` | `{folder?, uid, peek?, includeRaw?}` | `MailMessage` with body, `bodyTruncated` above `MAX_INLINE_BODY_BYTES` | - | IMAP, POP3 |
| `messages.raw` | `{folder?, uid}` | `{bytes, fileName, mimeType}` | one pipe write end | IMAP, POP3 |
| `attachments.download` | `{folder?, uid, partId}` | `{bytes, fileName, mimeType}` | one pipe write end | IMAP, POP3 |
| `messages.setFlags` | `{folder?, uids, flags, mode}` | `{uids}` | - | IMAP |
| `messages.move` | `{folder?, uids, target}` | `{uids?}` | - | IMAP |
| `messages.copy` | `{folder?, uids, target}` | `{uids?}` | - | IMAP |
| `messages.delete` | `{folder?, uids, expunge?}` | `{uids}` | - | IMAP, POP3 |
| `messages.expunge` | `{folder?}` | `{count}` | - | IMAP |
| `messages.append` | `{folder, message, flags?}` | `{uid}` | N read-only files | IMAP |
| `mail.send` | `{message, saveToSent?}` | `{messageId, accepted, rejected}` | N read-only files | SMTP |
| `session.close` | `{}` | `true` | - | all |

`message` of `mail.send` and `messages.append` is the `MailSendMessage` of the script API with
each attachment given as `{descriptorIndex, fileName, mimeType?, contentId?, inline?, size}`;
`descriptorIndex` points into the descriptor array of the call. `uids` are arrays of numbers or
strings, `flags` an array of IMAP flag names, `mode` one of `add`, `remove`, `set`. `query` is
the `MailSearchQuery` object of the script API. `MailFolder` and `MailMessage` follow roadmap
appendix A.4 with dates as UTC milliseconds; envelope messages carry `bodyLoaded: false`.

Ceilings the plugin enforces per operation: `MAX_RECIPIENTS` (to + cc + bcc),
`MAX_ATTACHMENTS_PER_MESSAGE`, `MAX_DESCRIPTORS`, `MAX_PAGE_SIZE` (with `DEFAULT_PAGE_SIZE` when
`limit` is absent), `MAX_CLIENT_FILTER` for the client-side search fallback,
`MAX_CONCURRENT_CALLS` and `MAX_QUEUED_CALLS` per session, `MAX_WATCHES_PER_SESSION`. Exceeding
any of them answers `LIMIT_EXCEEDED`.

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
| `MAX_CLIENT_FILTER` | 2000 | envelopes scanned by the client-side search fallback |
| `MAX_DESCRIPTORS` | 64 | one `call` |
| `MAX_CONCURRENT_CALLS` / `MAX_QUEUED_CALLS` | 4 / 32 | per session |
| `MAX_WATCHES_PER_SESSION` | 4 | per session |
| `MAX_WATCH_QUEUE` | 256 | undelivered events before `resync` |
| `MIN_POLL_INTERVAL_MS` / `DEFAULT_POLL_INTERVAL_MS` | 15 s / 60 s | poll mode |
| `IDLE_RENEW_MS` | 24 min | IDLE re-issue (RFC 2177 allows 29) |
| `SESSION_IDLE_TIMEOUT_MS` | 10 min | idle connection teardown inside the plugin |
| `DEFAULT_CONNECT_TIMEOUT_MS` / `DEFAULT_READ_TIMEOUT_MS` | 15 s / 60 s | socket timeouts |
| `MAX_ERROR_MESSAGE_BYTES` | 4 KiB | `error.message` |

Host-side call timeouts (`MailContract.callTimeoutMs`): metadata operations 30 s,
`messages.get` 120 s, transfers the larger of 5 min and the declared size at 64 KiB/s.

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
- The plugin checks every Binder caller (host package, signer, UID) before serving a request.
- Attachment file names sent by a server are untrusted: the host strips separators, control
  characters and leading dots, shortens over-long names and resolves clashes with a numeric
  suffix unless the script asked to overwrite (`MailFileNames`). Downloads go through a
  `<target>.part` file and are renamed only after the declared size matched.
- Read-only descriptors are opened by the host from script-supplied paths after existence,
  type, readability, size and count checks (`MailAttachmentSource`).
- `tls.trustAll` is per account, defaults to false and is surfaced as `insecure: true` in every
  status the plugin reports; there is no global switch (roadmap D25).

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
sequence checks and hands accepted events to a `MailWatchSink` (the P3 `MailWatch` object).
`MailJson` and `MailErrorMapper` are pure Kotlin and covered by JVM tests together with the
client state machine, the sink, the file-name rules and the bridge.

## Open Items

- P1.3: the Binder round trip (`openSession`, `session.test`, plugin process death, automatic
  reopen) against the P0 plugin on an API 24 emulator and a physical device; recorded in the
  plugin roadmap once the plugin implements `session.test` and `folders.list`.
- P2: plugin-side router, limits, caller guard and the account store.
- P3: script API `mail` and the documentation sync it requires.
