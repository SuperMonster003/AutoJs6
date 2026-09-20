# EPUB Plugin Protocol V1

Status: final V1 contract (`EpubContract.CONTRACT_VERSION` 1), frozen with host commit
`261417e90` (plugin roadmap P5.1) and delivered by the Readium EPUB Reader plugin (package
`io.github.supermonster003.autojs6.plugin.readium.epub.reader`, plugin id `readium-epub-reader`)
from build 48 (book extraction, roadmap P5.2) and build 49 (reader sessions, roadmap P5.3). The
host contract module `plugin-api/epub-api` carries the four AIDL interfaces, the Bundle keys, the
JSON field names, the closed value sets, the error vocabulary and the ceilings listed here;
`EpubAidlOrderTest` guards the transaction order and the `oneway` marker, `EpubContractTest`
snapshots the literals. Transaction numbers are frozen in declaration order; a later contract
version may only append methods and optional keys. The host side of this protocol (discovery,
book client, reader client, event bridge, launcher, source and output sink) is roadmap P5.4 and
lives in `org.autojs.autojs.core.plugin.epub`; the delivering host build is recorded in
`EpubIds.REQUIRED_HOST_VERSION_CODE` (5282). The script API `epub` is roadmap P6 of the plugin
repository and is not part of this document.

## Decision

EPUB parsing and rendering stay in a separate plugin APK with its own UID and process. The
Readium Kotlin Toolkit (3.4.0), the WebView navigator, the book storage (progress, bookmarks,
recent books) and the read-aloud service live in that plugin and are never loaded into the
AutoJs6 process. The host contributes discovery, a per-book and per-session Binder client, file
transfer through descriptors, the two-step reader launch and, in P6, the script objects
(plugin roadmap decisions D1, D2, D10, D12).

The same package exports two contract services (D10):

| Service | Action | Category | `PluginInfo.engine` |
| --- | --- | --- | --- |
| `ExplorerActionService` | `org.autojs.plugin.EXPLORER_ACTION` | - | `explorer-action` (file manager hand-off, see `readium-epub-reader-plugin-integration.md`) |
| `ReadiumEpubReaderPluginService` | `org.autojs.plugin.EPUB` | `epub` | `epub` (this document) |

Both answer the common `org.autojs.plugin.INFO` action through the plugin's info service, and
both are protected by the signature permission `org.autojs.permission.PLUGIN`. Their
`PluginInfo` share `id`, `variant` (`default`), `versionName` and `versionCode`; only `engine`
and `capabilities` differ. The Plugin Center groups declared services by package, so the
package keeps a single `Readium EPUB Reader` entry whose activation, enable and disable apply to
both services; `PluginCenterEpubRegistrationTest` covers the routing (`SERVICE_ACTION_BY_ENGINE`
resolves `epub` to `org.autojs.plugin.EPUB`, both services are enabled by default, the common
info endpoint wins for the dual-service package). The engine `epub` is
`PluginDefaultEnabledPolicy` default-on like the other Explorer Action family plugins (D29).
On a device (`EpubPluginCenterEntryDeviceTest`, API 33 and API 37 emulators, 2026-09-21) the
package yields exactly one `InstalledPluginRepository` entry, `Readium EPUB Reader` 1.0.0 with
id `readium-epub-reader` and variant `default`; the entry carries the Explorer Action identity
(engine `explorer-action`, service action `org.autojs.plugin.EXPLORER_ACTION`, its own
`requiresHostVersion`), the `epub` service answers the same id, variant and version, and
switching the entry off makes `EpubPluginHost` refuse with `PLUGIN_DISABLED` until it is
switched on again. No change to the grouping rules was needed.

Discovery does not grant authority. Before binding, `EpubPluginHost` requires an exported and
enabled service that declares the plugin permission and answers the action plus category query
of its own package; the plugin info must carry engine `epub`, variant `default`, an
`epubContractVersion` inside `EpubContract.MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION` and a
`requiresHostVersion` that is not below `REQUIRED_HOST_VERSION_CODE`. The package name is not
pinned (`EpubIds.DEFAULT_PACKAGE_NAME` names the official implementation only); the existing
Plugin Center authorization and enable records decide which package the host binds. Plugin-side
caller checks belong to the plugin repository (see Security Boundary).

Books and reader sessions are opened on a dedicated binding lease that is held until the book or
session closes; RemoteExceptions during `openBook` and `openReader` are not retried.

## V1 Binder Surface

```aidl
IEpubPlugin
    PluginInfo getInfo()
    Bundle getCapabilities()
    IEpubBook openBook(in ParcelFileDescriptor source, in Bundle options)
    IEpubReaderSession openReader(in ParcelFileDescriptor source, in Bundle options, IEpubReaderCallback callback)

IEpubBook
    Bundle getMetadata()
    Bundle getToc()
    Bundle getReadingOrder()
    Bundle getText(in Bundle request)
    ParcelFileDescriptor openResource(String href)
    Bundle search(in Bundle request)
    Bundle getPositions()
    void close()

IEpubReaderSession
    Bundle getState()
    void goTo(in Bundle target)
    void navigate(int direction)
    void setPreferences(in Bundle preferences)
    Bundle getBookmarks()
    void close(in Bundle options)

IEpubReaderCallback (oneway)
    void onEvent(long generation, long seq, in Bundle event)
```

Every Bundle written by the host carries `contractVersion` (`KEY_CONTRACT_VERSION`, currently
`1`), and so does every Bundle answer and event written by the plugin. A Bundle answer either
carries its result keys or `errorCode` plus `errorMessage`; a method that cannot answer with a
Bundle (`openBook`, `openReader`, `openResource`, and the `void` methods) throws across Binder
with the message `CODE: detail` (`EpubErrorCodes.encode`): `IllegalArgumentException` for
`INVALID_ARGUMENT`, `RESOURCE_NOT_FOUND` and `NOT_EPUB`, `IllegalStateException` for every other
code, `SecurityException` when the caller is refused. The host decodes the message back into the
code (`EpubErrorCodes.decode`); a message that does not start with a known code is reported as
`INTERNAL`.

### Method semantics

| Method | Threading | Behaviour |
| --- | --- | --- |
| `getInfo` | synchronous | never null; the same `capabilities` bundle as `getCapabilities` |
| `getCapabilities` | synchronous | `requiresHostVersion` (Long), `epubContractVersion` (Int), `epubFeatures` (String[]), `epubReadiumVersion` (String) |
| `openBook` | synchronous, parses the publication (at most `OPEN_TIMEOUT_MS`, else `TIMEOUT`) | the descriptor must be a regular file; `options` at most `MAX_OPTIONS_BYTES` as a parcel; `displayName` is trimmed and cut to 255 characters; at most `MAX_OPEN_BOOKS` books per plugin process (`LIMIT_EXCEEDED`); `NOT_EPUB`, `ENCRYPTED`, `PARSE_FAILED` for what Readium cannot open |
| `openReader` | synchronous, parses the publication like `openBook` | `callback` is required (`INVALID_ARGUMENT`); the start position (`locator`, `href` or `progression`) and the `preferences` document are validated before the session exists (see Reader Sessions); a new session replaces the previous one of the process |
| `getMetadata`, `getToc`, `getReadingOrder`, `getPositions` | synchronous; calls on one book are serialized, different books run in parallel | JSON documents under `metadata`, `toc`, `readingOrder`; `toc` and `readingOrder` carry `hasMore` when the entry ceiling cut the array; `positions` is the synthetic page count |
| `getText` | synchronous | one chunk of one reading-order resource, see Operations |
| `openResource` | returns at once with the read end of a pipe; the plugin streams on a background thread | `href` must be a manifest resource of at most `MAX_HREF_LENGTH` characters without control characters, its length at most `MAX_RESOURCE_BYTES`; the write end is closed with an error when a read fails, so `ParcelFileDescriptor.checkError()` on the host tells a truncated stream from a complete one |
| `search` | synchronous; a full page is computed before the answer | see Operations |
| `close` (book) | synchronous; idempotent | later calls answer `SESSION_CLOSED`; a book without a call for `BOOK_IDLE_TIMEOUT_MS` is closed by the plugin's sweeper the same way |
| `getState` | synchronous | the session token, `visible`, `positions` and, once the reader reported a locator, `locator`, `progression`, `href`, `index`, `title`; a closed session answers `SESSION_CLOSED` as a Bundle error |
| `goTo` | validated synchronously, executed on the reader's main thread | at least one of `locator`, `href`, `progression`; the target is checked against the publication before it is accepted; a target that fails later on the reader side produces an `error` event instead |
| `navigate` | executed on the reader's main thread | `direction` is one of `DIRECTION_*` (`INVALID_ARGUMENT` otherwise); `READER_NOT_VISIBLE` until the reader Activity claimed the session |
| `setPreferences` | validated synchronously, applied on the reader's main thread | the `preferences` document is required; known keys apply, unknown keys are reported by one `error` event with `UNSUPPORTED_PREFERENCE`; before the claim the patch is merged into the start preferences |
| `getBookmarks` | synchronous | the bookmarks of the session's book as a JSON array (at most `MAX_BOOKMARKS`) |
| `close` (session) | synchronous; idempotent | `finish` (default false) also finishes the reader Activity (D32); the session sends `close` with reason `host` |
| `onEvent` | oneway, from the plugin's main thread | never blocks the plugin; a `RemoteException` while sending ends the session silently (no further event, the reader stays open) |

Descriptor ownership (plugin roadmap appendix B.3): the host opens the book read-only, passes the
descriptor to `openBook` or `openReader` and closes its copy right after the call returned; the
plugin duplicates what it needs and closes its copy when the book or session closes. The read end
returned by `openResource` is owned by the host and closed after the transfer.

Death handling: the host links a death recipient to every book binder (later calls answer
`SESSION_CLOSED` "EPUB plugin process died") and to every session binder (a synthetic `close`
with reason `error` reaches the sink). The plugin links to the reader callback it received and
ends the session silently when the host dies, and closes every book and session when the host
unbinds (`onUnbind`: books are closed, sessions send `close` with reason `host`).

## Bundle Keys

| Key | Type | Written by | Purpose |
| --- | --- | --- | --- |
| `contractVersion` | Int | both | contract version of the writer |
| `requestId` | String | host | optional caller-chosen id; reserved, not echoed by V1 answers |
| `displayName` | String | host | file name of the book (`openBook`, `openReader` options) |
| `href` | String | both | resource href (`getText` request and answer, `goTo`, state, events) |
| `index` | Int | both | reading-order index (`getText` request and answer, state) |
| `offset` | Int | both | character offset of a `getText` chunk; result offset of a `search` page |
| `limit` | Int | host | page size of `search` (0 means `DEFAULT_SEARCH_LIMIT`) |
| `maxChars` | Int | host | chunk size of `getText` (0 means `MAX_TEXT_CHARS_PER_CALL`) |
| `format` | String | both | `text` or `markdown` (`getText`) |
| `query` | String | both | search query, echoed normalized in the answer |
| `locator` | String | both | Readium locator as JSON (`openReader` options, `goTo`, state, events) |
| `progression` | Double | both | total progression 0.0 to 1.0 (`openReader` options, `goTo`, state, `progress` events) |
| `preferences` | String | host | preferences document as JSON (`openReader` options, `setPreferences`) |
| `errorCode`, `errorMessage` | String | plugin | failure of a Bundle answer or an `error` event |
| `event` | String | plugin | event type (`EVENT_*`) |
| `reason` | String | plugin | `close` reason (`REASON_*`) |
| `change` | String | plugin | `added` or `removed` (`bookmark` events) |
| `sessionToken` | String | plugin | the session token (`getState`) |
| `visible` | Boolean | plugin | whether the reader Activity is started (state, `open` events) |
| `title` | String | plugin | book title in `open` events; chapter title in `progress` events and state |
| `text` | String | plugin | the chunk of `getText` |
| `hasMore` | Boolean | plugin | more text, results or entries follow |
| `positions` | Int | plugin | synthetic page count (`getPositions`, state, `open` events) |
| `finish` | Boolean | host | finish the reader Activity when closing a session (default false) |
| `metadata`, `toc`, `readingOrder`, `results`, `bookmarks` | String | plugin | JSON documents below |

Keys a reader does not know are ignored; the host never relies on a key this table does not
list. The `openReader` and `openBook` options bundles are measured as parcels and refused above
`MAX_OPTIONS_BYTES` with `LIMIT_EXCEEDED`.

## JSON Documents

All documents are UTF-8 JSON built with the field names of `EpubContract.FIELD_*`. Hrefs are the
publication-relative hrefs of the Readium manifest (for example `OEBPS/chapter2.xhtml`), which are
the same strings in the reading order, the table of contents, the locators, the search results
and the bookmarks. Dates are the strings Readium parsed from the package document.

### Metadata (`metadata`)

```json
{
  "title": "Minimal EPUB 3",
  "authors": ["Jane Doe"],
  "language": "en",
  "identifier": "urn:uuid:...",
  "publisher": "...",
  "published": "2024-01-01T00:00:00Z",
  "modified": "2024-01-02T00:00:00Z",
  "description": "...",
  "subjects": ["Fiction"],
  "layout": "reflowable",
  "readingProgression": "ltr",
  "cover": "OEBPS/cover.png",
  "positions": 3
}
```

`title`, `authors`, `subjects`, `layout`, `readingProgression` and `positions` are always present;
the other fields are absent when the package carries nothing. `layout` is `reflowable` or
`fixed`, `readingProgression` is `ltr`, `rtl` or `auto`; `cover` is the href of the resource
with the `cover` relation, exportable through `openResource`; `description` is cut to 65,536
characters and the document stays below `MAX_METADATA_BYTES`.

### Table of contents (`toc`) and reading order (`readingOrder`)

```json
[{"title": "Chapter 1", "href": "OEBPS/chapter1.xhtml", "depth": 0}, {"title": "Section 1.1", "href": "OEBPS/chapter1.xhtml#s1", "depth": 1}]
[{"href": "OEBPS/chapter1.xhtml", "type": "application/xhtml+xml", "title": "Chapter 1"}]
```

The table of contents is the navigation tree flattened in document order with `depth` from 0;
entries beyond `MAX_TOC_ENTRIES` are dropped and `hasMore` is set. The reading order lists the
spine items with their media type (`type`, empty when unknown) and title when the manifest has
one, at most `MAX_READING_ORDER_ENTRIES`.

### Locator (`locator`)

The Readium locator (`Locator.toJSON`): `href`, `type`, optional `title`, `locations`
(`progression`, `totalProgression`, `position`, `cssSelector`, `fragments`) and, in search
results, `text` (`before`, `highlight`, `after`). A locator sent by the host must parse
(`INVALID_ARGUMENT`), must not exceed `MAX_LOCATOR_BYTES` (`LIMIT_EXCEEDED`) and must name a
resource of the open publication (`RESOURCE_NOT_FOUND`); locators received from the plugin can
be sent back unchanged.

### Search results (`results`)

```json
[{"href": "OEBPS/chapter1.xhtml", "title": "Chapter 1", "locator": {"...": "..."}, "text": {"before": "the old ", "highlight": "lantern", "after": " on the pier"}}]
```

### Bookmarks (`bookmarks`)

```json
[{"locator": {"...": "..."}, "createdAt": 1789920631874, "title": "Chapter 1", "text": "First words of the page"}]
```

`createdAt` is UTC milliseconds; `title` (chapter) and `text` (snippet) are absent when the
reader recorded none. The array is the reader's bookmark list of the book, at most
`MAX_BOOKMARKS`; a `bookmark` event carries a one-element array with the changed bookmark.

### Preferences (`preferences`)

```json
{"fontSize": 1.2, "fontFamily": "Literata", "lineHeight": 1.5, "pageMargins": 1.0, "theme": "dark", "scroll": false, "columnCount": "auto", "verticalText": false, "textAlign": "justify", "hyphens": true, "publisherStyles": false}
```

A JSON object whose keys are the `EpubContract.PREFERENCES` subset (plugin roadmap appendix A.6).
Every key is optional; a JSON `null` resets the key to the reader's default. Types and ranges
are strict (`INVALID_ARGUMENT` otherwise, nothing is clamped): `fontSize` 0.5 to 3.0,
`lineHeight` 1.0 to 2.0, `pageMargins` 0.0 to 4.0 (numbers), `theme` one of `light`, `sepia`,
`dark`, `columnCount` one of `auto`, `1`, `2`, `textAlign` one of `start`, `end`, `left`,
`right`, `justify`, `center`, `fontFamily` a name of at most 120 characters without control
characters, `scroll`, `verticalText`, `hyphens`, `publisherStyles` booleans. Setting one of the
advanced keys `lineHeight`, `textAlign` or `hyphens` turns `publisherStyles` off unless the same
document says otherwise, as the reader's own preference panel does. Unknown keys are not refused:
the known part applies and the unknown names arrive in one `error` event with
`UNSUPPORTED_PREFERENCE` ("unsupported preferences: a, b"). A document above
`MAX_PREFERENCES_BYTES` is refused with `LIMIT_EXCEEDED`. The applied preferences are the
reader's global defaults (plugin decision D14), so they outlive the session.

## Operations

### `getText`

Request keys: `href` or `index` (one is required; `href` wins, a fragment is stripped, and the
resource must be a reading-order item, otherwise `RESOURCE_NOT_FOUND`; a negative `index` is
`INVALID_ARGUMENT`, one beyond the reading order `RESOURCE_NOT_FOUND`), `offset` (default 0),
`maxChars` (default and ceiling `MAX_TEXT_CHARS_PER_CALL`), `format` (`text` by default,
`markdown`). The answer carries `href`, `index`, `format`, `offset`, `text` and `hasMore`; a
chunk never splits a surrogate pair, an `offset` at or beyond the end yields an empty chunk, and
a spine item that is not HTML (fixed-layout images, SVG) yields an empty chunk. The plugin
renders the resource with a DOM walk (jsoup) in document order: `text` separates blocks by one
line break, leaves headings, list items and code bare and drops images; `markdown` marks
headings with `#`, quotations with `> `, list items with `- `, code with fences, images as
`![caption](href)` and separates blocks by a blank line (plugin decision D30). A resource above
`MAX_RESOURCE_BYTES` is refused with `LIMIT_EXCEEDED`.

### `search`

Request keys: `query` (trimmed, inner whitespace collapsed, 1 to `MAX_QUERY_LENGTH` characters),
`offset` (default 0), `limit` (0 means `DEFAULT_SEARCH_LIMIT`, at most `MAX_SEARCH_RESULTS`).
`offset + limit` may not exceed `MAX_SEARCH_RESULTS`: a search never yields more than 500 hits,
whatever the paging. The answer carries the normalized `query`, `offset`, `results` and
`hasMore` (true when hits beyond the page exist and the page end is below the ceiling). Each
page runs Readium's search service from the start of the book, so deep pages cost more than the
first one.

### `openResource`

Any manifest resource (cover, images, fonts, stylesheets, chapters) streams through a pipe; the
host `EpubOutputSink` spools it into `<target>.part`, enforces `MAX_RESOURCE_BYTES`, checks the
pipe's error state and renames only a complete file. Targets are chosen by the host from
script-supplied paths (a directory gets the resource's file name, an existing file is refused
unless overwrite was requested).

## Reader Sessions

The two-step launch (plugin decision D12):

1. The host calls `openReader(descriptor, options, callback)`. The plugin parses the book with the
   reader's own font container, validates the start target and the preferences, mints a session
   token (`HostSessionPolicy`: 128 random bits from `SecureRandom`, 32 lowercase hex characters),
   registers the session and returns `IEpubReaderSession`. The host reads the token from
   `getState` (the only place it travels).
2. The host starts the plugin's reader Activity itself with an explicit component (the single
   exported and enabled Activity of the plugin package that answers
   `org.autojs.plugin.EPUB_READER_OPEN` with category DEFAULT; zero or several such Activities
   are `PLUGIN_INCOMPATIBLE`), the action, the category and the extra
   `org.autojs.plugin.epub.extra.SESSION_TOKEN`; `FLAG_ACTIVITY_NEW_TASK` is added when the
   context is not an Activity. The Activity sits behind `org.autojs.permission.PLUGIN`. The plugin
   service never starts an Activity on its own (background-launch restrictions).
3. The Activity accepts the intent only when the action matches, the component was explicit and
   the token is well formed; it claims the session with a constant-time comparison. A token that
   is unknown, already claimed or already closed shows the reader's invalid-request panel and
   reads nothing (the Activity is not finished). After the claim the reader's view model adopts
   the publication and the descriptor of the session, applies the start target (a `locator`,
   an `href` with optional fragment, or a total `progression` 0.0 to 1.0; the saved progress
   otherwise) and the start preferences, and the session becomes the bridge between that reader
   and the host callback. A session nobody claims within `READER_CLAIM_TIMEOUT_MS` closes with
   reason `timeout` and releases the book.

One session per plugin process: a new `openReader` closes the previous session with reason
`replaced` and finishes its reader Activity (the replaced host sees `close`, later calls answer
`SESSION_CLOSED`). The book of a session does not enter the reader's recent list.

Events go through `onEvent(generation, seq, event)`: `generation` is fixed for the life of one
session (the plugin takes a millisecond clock value, so it differs between sessions of one
process), `seq` starts at 1 and increases by one per event. Every event bundle carries
`contractVersion` and `event`:

| Event | Keys | When |
| --- | --- | --- |
| `open` | `visible`, `title` (book), `locator`, `href`, `positions` | once, when the navigator shows its first locator after the claim |
| `progress` | `locator`, `progression` (total), `title` (chapter, when known), `href` | every locator change, throttled to `PROGRESS_THROTTLE_MS` with a trailing flush |
| `bookmark` | `change`, `bookmarks` (one-element array) | per bookmark added or removed in the reader; the list at adoption time is the baseline, not an event |
| `error` | `errorCode`, `errorMessage` | unknown preference keys (`UNSUPPORTED_PREFERENCE`), a `goTo` target that could not be reached after validation (`RESOURCE_NOT_FOUND`, `INTERNAL`) |
| `close` | `reason` | exactly once, then nothing: `user` (the user left the reader, its view model was cleared), `host` (session `close` or host unbind), `replaced`, `timeout`, `error` (plugin-side failure) |

An event whose parcel exceeds `MAX_EVENT_BYTES` first loses `locator` and `bookmarks`; if it is
still too large it is dropped. A `RemoteException` from the callback (the host is gone) ends the
session silently: no `close` is sent, the reader stays open for the user.

Close semantics (plugin decision D32): the host's `close` without `finish` only ends the session
(token invalid, events stop, the reader Activity stays as an ordinary reader and keeps recording
progress); `close` with `finish: true` also finishes the Activity. The user leaving the reader
sends `close` with reason `user` to the host; the host's own `close` produces reason `host`.

The host bridge (`EpubReaderBridge`) accepts an event only when its type is known, its
generation is the first generation seen by the bridge, its `seq` is above the last accepted one
and no `close` was delivered yet; everything else is counted and reported to the sink as dropped
(`malformed`, `stale-generation`, `out-of-order`, `closed`). Closing the client or the death of
the session binder delivers one synthetic `close` (`host` or `error`) unless the plugin's
`close` already arrived.

## Error Codes

| Code | Meaning | Produced by |
| --- | --- | --- |
| `PLUGIN_UNAVAILABLE` | no plugin installed, binding refused, discovery or binding failure, the reader Activity could not be started, plugin process died while opening | host |
| `PLUGIN_DISABLED` | the plugin is installed but disabled in the Plugin Center | host |
| `PLUGIN_INCOMPATIBLE` | identity, contract version or required host version mismatch; an answer with an unsupported `contractVersion`; zero or several reader Activities | host |
| `FILE_NOT_FOUND`, `FILE_UNREADABLE` | the path of `openBook` / `openReader` | host |
| `NOT_EPUB` | neither an `.epub` name nor a ZIP container (host); no EPUB package inside the container (plugin) | both |
| `PARSE_FAILED` | Readium could not open the package | plugin |
| `ENCRYPTED` | the publication is protected (LCP or other DRM) | plugin |
| `RESOURCE_NOT_FOUND` | unknown href, reading-order index beyond the end, locator outside the book | plugin |
| `LIMIT_EXCEEDED` | any ceiling of this document | both |
| `INVALID_ARGUMENT` | malformed or missing request field, unknown value, out-of-range preference | both |
| `SESSION_CLOSED` | the book or session is closed; the plugin process died after a successful open | both |
| `SESSION_REPLACED` | reserved for a call on a session that a newer session replaced (V1 plugins answer `SESSION_CLOSED` after the `replaced` close) | plugin |
| `READER_NOT_VISIBLE` | `navigate` before the reader Activity claimed the session | plugin |
| `UNSUPPORTED_PREFERENCE` | unknown preference keys (an `error` event, never a refusal) | plugin |
| `CANCELLED` | the host coroutine was cancelled or interrupted | host |
| `TIMEOUT` | opening took longer than `OPEN_TIMEOUT_MS`; a host-side call timeout | both |
| `IO` | descriptor, pipe or resource read failure | both |
| `INTERNAL` | anything else, including unknown codes | both |

`EpubErrorMapper` on the host maps a `DeadObjectException` or `RemoteException` to
`SESSION_CLOSED` on an open book or session and to `PLUGIN_UNAVAILABLE` while discovering or
opening, `SecurityException` to `PLUGIN_UNAVAILABLE`, `TimeoutCancellationException` to
`TIMEOUT`, cancellation and interruption to `CANCELLED`, `FileNotFoundException` to
`FILE_NOT_FOUND` and other `IOException` to `IO`; an exception message that starts with a
contract code keeps that code. `errorMessage` is cut to `MAX_ERROR_MESSAGE_BYTES` on both sides.
Every failure surfaces as `EpubPluginException` carrying an `EpubError(code, message)`.

## Ceilings

| Constant | Value | Applies to |
| --- | --- | --- |
| `MAX_OPEN_BOOKS` | 8 | books open at once per plugin process |
| `BOOK_IDLE_TIMEOUT_MS` | 5 min | a book without a call is closed by the plugin |
| `MAX_OPTIONS_BYTES` | 36 KiB | `openBook` / `openReader` options as a parcel (a 16 KiB locator plus a 16 KiB preferences document plus the names fit) |
| `MAX_HREF_LENGTH` | 2048 | every href sent by the host |
| `MAX_METADATA_BYTES` | 256 KiB | the metadata document |
| `MAX_TOC_ENTRIES` / `MAX_TOC_BYTES` | 5000 / 1 MiB | the table of contents |
| `MAX_READING_ORDER_ENTRIES` | 5000 | the reading order |
| `MAX_TEXT_CHARS_PER_CALL` | 1 MiB characters | one `getText` chunk (also the default `maxChars`) |
| `MAX_RESOURCE_BYTES` | 64 MiB | one `openResource` stream and one resource rendered by `getText` |
| `MIN_QUERY_LENGTH` / `MAX_QUERY_LENGTH` | 1 / 256 | `search` query |
| `DEFAULT_SEARCH_LIMIT` / `MAX_SEARCH_RESULTS` | 50 / 500 | one `search` page / all pages of one query |
| `MAX_RESULTS_BYTES` | 2 MiB | the results document |
| `MAX_LOCATOR_BYTES` | 16 KiB | a locator sent by the host |
| `MAX_PREFERENCES_BYTES` | 16 KiB | a preferences document |
| `MAX_BOOKMARKS` | 500 | the bookmarks document (the reader's own per-book ceiling, plugin decision D13) |
| `MAX_EVENT_BYTES` | 32 KiB | one event bundle as a parcel |
| `MAX_ERROR_MESSAGE_BYTES` | 4 KiB | `errorMessage` |
| `READER_CLAIM_TIMEOUT_MS` | 60 s | an unclaimed reader session |
| `PROGRESS_THROTTLE_MS` | 500 ms | `progress` events |
| `OPEN_TIMEOUT_MS` / `CALL_TIMEOUT_MS` | 30 s / 60 s | host-side timeouts of `openBook` / `openReader` and of every other call |

## Versioning

`epubContractVersion` in the plugin capabilities is compared against
`EpubContract.MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION` before the plugin is selected, and the
`contractVersion` of every Bundle answer is checked again on receipt (`PLUGIN_INCOMPATIBLE`
otherwise). A later version may append AIDL methods, add optional Bundle keys, JSON fields,
event types, reasons and preference keys; it may not change the meaning of an existing field,
reorder methods or remove a code. Plugins declare the host build they need through
`requiresHostVersion`, which must be at least `EpubIds.REQUIRED_HOST_VERSION_CODE`.

Mixed versions behave as follows:

- A plugin without the `epub` service (builds before 48) is invisible to `EpubPluginHost`
  (`PLUGIN_UNAVAILABLE`); its Explorer Action service keeps working in the file manager.
- A plugin build 48 (P5.2) with a host of this document answers `INTERNAL` to `openReader`
  (the reader session landed in build 49) and advertises `epubFeatures` without
  `reader-session`; hosts check the feature before calling.
- A plugin that requires a newer host than the installed one is refused as
  `PLUGIN_INCOMPATIBLE` at discovery; the Plugin Center shows it as incompatible.
- A host that does not know a Bundle key, JSON field, event type or preference key ignores it;
  a plugin that does not know an option key ignores it, and reports unknown preference keys
  through the `error` event instead of refusing the call. Unknown error codes become `INTERNAL`
  on the host, unknown event types are dropped as malformed.
- `tts` is a feature name of the vocabulary but no V1 plugin advertises it: the contract has no
  read-aloud control surface, so a host could not act on it.

## Security Boundary

- The plugin serves only the AutoJs6 host: `CallerGuard` requires that the calling UID owns the
  package `org.autojs.autojs6` and that the caller's signature matches the plugin's own
  (`PackageManager.checkSignatures`); the plugin's own UID is accepted in debug builds only (the
  instrumentation tests). Everything else throws `SecurityException` before any descriptor is
  touched. `org.autojs.autojs6.inrt` (the script runtime package) is not a caller of this
  contract.
- The services and the reader Activity are exported behind `org.autojs.permission.PLUGIN`, a
  signature permission; the reader Activity additionally requires an explicit component, the
  contract action and a well-formed token, compared in constant time, and shows nothing for a
  token it does not hold. The token is minted from `SecureRandom`, travels only through
  `getState` and the launch extra, and is worthless after the claim, the timeout or the close.
- Descriptors are opened read-only by the host from script-supplied paths after existence, type,
  readability and container checks (`EpubSource`); the plugin checks that every descriptor is a
  regular file, holds no URI grant and receives no path. Resource exports go through a
  `<target>.part` file under the 64 MiB ceiling and are renamed only when the pipe reported no
  error (`EpubOutputSink`).
- Every Binder input is bounds-checked by the plugin before use (hrefs, indices, offsets, sizes,
  JSON documents, preference values, directions), and every answer, event and thrown message is
  cut to the ceilings above; nothing in an error message echoes book content.
- The plugin service never starts an Activity from the background and never keeps a session's
  book after the session closed; the reader keeps its own progress and bookmarks as for any book.
- Plugin permissions stay at the set of plugin decision D19; the contract adds none, and the
  plugin's `<queries>` and the host's `<queries>` name only each other's contract actions.

## Host Client (P5.4)

`EpubPluginHost` wraps `AidlPluginHost` with the action, category and validators above and offers
`discover`, `probe`, `queryServiceCount`, `getCapabilities`, `openBook` and `openReader`; the
Plugin Center lists and probes the engine through the same object. `openBook` validates the path
(`EpubSource`), binds on a dedicated lease and returns `EpubBookClient` (`metadata`, `toc`,
`readingOrder`, `text`, `resource`, `search`, `positions`, `close`, `isClosed`); the client
decodes every answer through `EpubBundles.answer` (missing answer, unsupported contract version,
carried error code) and maps transport failures with `EpubErrorMapper`. `openReader` binds on a
dedicated lease, hands the plugin an `EpubReaderCallbackBinder` in front of an `EpubReaderBridge`,
reads the token, starts the reader through `EpubReaderLauncher` and returns `EpubReaderClient`
(`state`, `goTo`, `navigate`, `setPreferences`, `bookmarks`, `close(finish)`, `token`,
`bridge`); when the Activity cannot be started the session is closed with `finish` and the
failure is rethrown. `EpubOutputSink` implements the export convention above. `EpubReaderBridge`,
`EpubErrorMapper`, `EpubSource` and `EpubOutputSink` are covered by JVM tests
(`EpubReaderBridgeTest`, `EpubErrorMapperTest`, `EpubSourceTest`, `EpubOutputSinkTest`).

`EpubPluginRoundTripTest`, `EpubReaderRoundTripTest` and `EpubPluginCenterEntryDeviceTest`
(androidTest, skipped without an installed plugin; run them with
`-Pandroid.testInstrumentationRunnerArguments.package=org.autojs.autojs.core.plugin.epub`) are
the cross-process evidence of P5.4 against the plugin at commit `2c08db6` (build 49).
`EpubPluginRoundTripTest` writes a two-chapter EPUB in the test, opens it through
`EpubPluginHost.openBook` over the real Binder and checks capabilities, metadata, positions,
table of contents, reading order, text by index and by href with 9-character paging, the
Markdown heading, image and quotation (plain text drops the image with its caption), search hits
and paging, a resource export through `EpubOutputSink`, the refusal codes (unknown index,
negative index, unknown format, empty and over-long queries, a closed book) and a plugin process
death (`am force-stop`) that closes the book on the host side with `SESSION_CLOSED` "died" and
lets the next `openBook` succeed. `EpubReaderRoundTripTest` opens a session from a foreground
host Activity (`AboutActivity`) with an `href` start and a preferences document carrying an
unknown key, sees `open` (visible, book title, the chapter-2 href, two positions), the
`UNSUPPORTED_PREFERENCE` error, `progress` after `navigate(previous chapter)`, `goTo` by
progression, by locator and by href, a second error for `setPreferences` with an unknown key,
reads `state` (token, index, chapter title) and `bookmarks`, checks the refusal codes (unknown
direction, empty target, progression above 1, unknown href, malformed locator, unknown theme,
font size out of range, a non-object preferences document), then closes with `finish` and sees
exactly one `close` with reason `host`, nine events in one generation with no drop.
`EpubPluginCenterEntryDeviceTest` is the D10 check of the Decision section. On 2026-09-21 all
five tests passed on the API 33 and API 37 emulators (host debug build, plugin debug build of
commit `2c08db6`); the physical devices of the plugin matrix carry no host and were not used.

## Open Items

- P5.1: closed with host commits `261417e90` (contract module) and `9f86ea439` (Plugin Center
  registration, `PluginCenterEpubRegistrationTest`); the plugin repository staged the AAR in its
  commit `2c0b219`.
- P5.2: closed with plugin build 48 (`1fdc182`): `PluginServiceInstrumentationTest` and
  `PluginContractInstrumentationTest` passed on Sony G8441 (API 28), Redmi 12C (API 33) and the
  API 37 emulator; the plugin repository keeps the evidence under `files/p2-evidence/service-*`.
- P5.3: closed with plugin build 49: `ReaderSessionInstrumentationTest` (session round trip,
  replacement, wrong tokens, claim timeout, refusals) passed on the same three devices on
  2026-09-21; the Xiaomi Pad (API 35) is deferred until it is unlocked.
- P5.4: closed with the host client, its JVM tests (21) and the three device tests above on
  the API 33 and API 37 emulators (2026-09-21); the plugin repository keeps the plugin-center
  screenshots under `docs/images/evidence/p51-plugin-center-api33.png` and `-api37.png`.
- P5.5: this document; the host changelog carries the contract (P5.1) and client (P5.4)
  entries under v6.8.0; the plugin changelog carries the service (P5.2) and session (P5.3)
  entries.
- P6 (script API `epub`): not started; the `requestId` key and the `SESSION_REPLACED` code are
  reserved for it.
