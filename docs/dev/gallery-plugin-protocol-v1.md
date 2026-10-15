# Gallery Plugin Protocol V1

Status: V1 contract, frozen with the host contract module `plugin-api/gallery-api` (3-Maple Image
plugin roadmap M6, item 10.1) and delivered by 3-Maple Image (package
`io.github.supermonster003.autojs6.plugin.three.maple.image`, plugin id `three-maple-image`) from
3.4.0. The module carries the three AIDL interfaces, the Bundle keys, the JSON field names of a
picked image, the closed value sets, the error vocabulary and the ceilings listed here;
`GalleryAidlOrderTest` guards the transaction order and the `oneway` marker, `GalleryContractTest`
snapshots the literals. Transaction numbers are frozen in declaration order; a later contract
version may only append methods and optional keys. The host client lives in
`org.autojs.autojs.core.plugin.gallery`, the script object `gallery` in
`org.autojs.autojs.runtime.api.augment.gallery`; the script API itself is documented in
AutoJs6-Documentation (`api/gallery.md`).

## Decision

The script object `gallery` lets a script use the plugin's picker, viewer, editor and converter
(plugin roadmap D12, "minimal UI-driven calls"): no headless pixel processing beyond the
converter and no media library queries, which would overlap `images` or hand the plugin's photo
access to scripts. The plugin maintainer settled the contract in a review on 2026-10-15:

1. A contract of its own that follows the EPUB three-layer integration (contract module, host
   client, augment object), not an extension of the Explorer Action protocol. The plugin mints a
   one-time token per interactive session; the host starts the plugin Activity itself; events
   come back over a `oneway` callback with a generation and a sequence number.
2. `pick` answers an array of `{ path, name, mimeType, width, height, size, dateTaken }`,
   `options.multiple` chooses single or multiple selection, a cancelled picker answers `null`.
   The host keeps only the paths the script may read.
3. `edit` saves next to the original as `name_edited.ext` by default; `options.output` names
   another path and `options.overwrite` replaces the original. The host writes the file (staged,
   read back, published) and answers the output path, or `null` when the user cancels.
4. `view` answers a session object as soon as the viewer is launched (events `page` and `close`,
   method `close()`); `viewAsync` settles once the viewer is open.
5. `convert` runs without UI with the options of the plugin's converter and saves as
   `name_converted.ext` by default (`output` and `overwrite` as for `edit`); it answers the
   output path, size and dimensions.
6. Without photo access the picker asks the user first (including partial access on Android 14
   and later) and fails with `PERMISSION_REQUIRED` only when the user refuses.
7. The plugin release that implements this contract (3.4.0) ships on its own; its file manager
   actions keep requiring host build 5352.
8. Documentation, declarations, the Ace Editor declarations and the offline documentation follow
   their own repositories' publishing flow.

The service and its Activity live in the plugin package next to its Explorer Action services:

| Service | Action | Category | `PluginInfo.engine` |
| --- | --- | --- | --- |
| `ExplorerActionService` (and the directory service) | `org.autojs.plugin.EXPLORER_ACTION` | - | `explorer-action` |
| `GalleryPluginService` | `org.autojs.plugin.GALLERY` | `gallery` | `gallery` (this document) |

Both are protected by the signature permission `org.autojs.permission.PLUGIN` and share the
plugin's `id`, `variant` (`default`), `versionName` and `versionCode`; only `engine` and
`capabilities` differ, so the Plugin Center keeps one 3-Maple Image entry.

## V1 Binder Surface

```aidl
interface IGalleryPlugin {
    PluginInfo getInfo();
    Bundle getCapabilities();
    IGallerySession openPicker(in Bundle options, IGalleryCallback callback);
    IGallerySession openViewer(in ParcelFileDescriptor[] sources, in Bundle options, IGalleryCallback callback);
    IGallerySession openEditor(in ParcelFileDescriptor source, in ParcelFileDescriptor target, in Bundle options, IGalleryCallback callback);
    Bundle convert(in ParcelFileDescriptor source, in ParcelFileDescriptor target, in Bundle options);
}

interface IGallerySession {
    Bundle getState();
    void close(in Bundle options);
}

oneway interface IGalleryCallback {
    void onEvent(long generation, long seq, in Bundle event);
}
```

### Method semantics

- `getInfo` answers the plugin identity with `engine` `gallery`; its `capabilities` Bundle and
  `getCapabilities` carry `galleryContractVersion` (1), optionally `galleryMaxContractVersion`,
  `galleryFeatures` (a subset of `picker`, `viewer`, `editor`, `convert`) and
  `requiresHostVersion`.
- The three interactive calls (`openPicker`, `openViewer`, `openEditor`) validate the request,
  open a session and return it without starting any Activity. `getState` of the new session
  carries `sessionToken` (128 random bits as 32 hexadecimal characters), `kind` and `visible`
  (false). One interactive session is open per host process at a time: opening another closes the
  previous one with reason `replaced`.
- `openViewer` receives 1 to 128 read-only descriptors of regular files and `displayNames` with one
  entry each; `startIndex` chooses the first page.
- `openEditor` receives the read-only source and a target: an empty regular file the host created
  for the output and opened read-write. The plugin writes the target only when the user saves and
  never after the host closed the session.
- `convert` decodes the source and writes the target in the calling Binder thread; it answers the
  output description or an error. It never shows UI.
- Descriptors belong to the host, which closes them after the call returns; the plugin duplicates
  what it keeps. A method that cannot answer with a Bundle throws `IllegalArgumentException` or
  `IllegalStateException` whose message is `CODE: detail` (`GalleryErrorCodes.encode`).

## Bundle Keys

| Key | Type | Where |
| --- | --- | --- |
| `contractVersion` | Int | every request and answer |
| `errorCode`, `errorMessage` | String | failed answers, `error` events |
| `event`, `reason` | String | events |
| `sessionToken`, `kind`, `visible`, `index` | String, String, Boolean, Int | `getState` |
| `finish` | Boolean | `close` options (default true) |
| `multiple`, `maxCount`, `mimeTypes`, `title` | Boolean, Int, String[], String | `openPicker` options |
| `items` | String (JSON array) | picker `result` event |
| `displayNames`, `startIndex` | String[], Int | `openViewer` options |
| `index` | Int | viewer `page` event |
| `displayName` | String | `openEditor`, `convert` options |
| `format`, `quality`, `lossless`, `targetBytes` | String, Int, Boolean, Long | `openEditor` (format, quality), `convert` options |
| `sizeMode`, `percent`, `longEdge`, `width`, `height`, `keepAspectRatio` | String, Int, Int, Int, Int, Boolean | `convert` options |
| `background` | Int (ARGB) | `convert` options (JPEG output) |
| `keepExif`, `keepLocation` | Boolean | `openEditor`, `convert` options |
| `mimeType`, `width`, `height`, `size` | String, Int, Int, Long | editor `result` event, `convert` answer |

`format` is one of `jpeg`, `png` and `webp`; absent, the output keeps the source format when the
plugin can write it and is JPEG otherwise. `sizeMode` is one of `original` (default), `percent`
(1 to 1000), `longEdge` (images already smaller are not enlarged) and `custom` (`width` and/or
`height`, `keepAspectRatio` default true). `quality` is 1 to 100 (default 92). `keepLocation`
only counts together with `keepExif`; both default to false, so outputs carry no location unless
the script asks for it.

A picked image is a JSON object with `path` (absolute path the plugin derived from its media
library), `name`, `mimeType`, `width`, `height`, `size` (bytes) and `dateTaken` (milliseconds,
the modification time when the image has no capture time).

## Sessions

1. The host opens a session with an `IGalleryCallback` and reads the token from `getState`.
2. The host resolves the single exported Activity of the plugin package that answers
   `org.autojs.plugin.GALLERY_SESSION_OPEN` (category `DEFAULT`) and starts it with an explicit
   component and `org.autojs.plugin.gallery.extra.SESSION_TOKEN` as the only extra.
3. The Activity claims the session with the token (once; a second claim or an unknown token
   finishes the Activity). A session not claimed within 60 s closes with reason `timeout`.
4. Events follow on the callback, with a generation per session and a sequence number that grows
   by one per event:

| Event | Keys | Sent |
| --- | --- | --- |
| `open` | `kind` | the Activity claimed the session and is visible |
| `page` | `index` | a viewer moved to another page (also for the start page) |
| `result` | picker: `items`; editor: `mimeType`, `width`, `height`, `size` | the user chose images or saved |
| `error` | `errorCode`, `errorMessage` | the session failed (`PERMISSION_REQUIRED`, `DECODE_FAILED`, ...) |
| `close` | `reason` | always last: `user`, `host`, `done`, `timeout`, `replaced` or `error` |

A picker or editor session closes with `done` right after its `result`; a viewer has no result
and closes with `user` when the user leaves it. The host drops events of another generation,
out-of-order sequence numbers and anything after `close`. When the plugin process dies the host
closes the session locally with reason `error`.

## Outputs

The host owns every file the script names. For `edit` and `convert` it:

1. resolves the output path: `options.output` when given (relative paths resolve against the
   script's working directory), otherwise the source folder with `name_edited.ext` or
   `name_converted.ext`, where `ext` follows the output format and a taken default name gets a
   counter (`name_edited (1).jpg`); an explicit output that exists fails with `OUTPUT_EXISTS`
   unless `overwrite` is set, and `overwrite` without `output` targets the source itself;
2. creates a hidden staging file in the destination folder and passes it read-write;
3. after the plugin's answer, reads the staging file back (it must decode to the reported size and
   type) and renames it to the output, replacing an existing file only with `overwrite` (the old
   file is kept until the rename succeeded);
4. deletes the staging file on any failure, cancellation, timeout or plugin death.

The default extension follows the written type, so an editor output in PNG of `a.jpg` is
`a_edited.png`.

## Error Codes

| Code | Meaning |
| --- | --- |
| `PLUGIN_UNAVAILABLE`, `PLUGIN_DISABLED`, `PLUGIN_INCOMPATIBLE` | no usable plugin (host only) |
| `FILE_NOT_FOUND` | a script path does not exist or is not a regular file (host only) |
| `FILE_UNREADABLE` | a source cannot be opened or read |
| `UNSUPPORTED_FORMAT` | the plugin cannot open the source, or cannot write `format` |
| `DECODE_FAILED` | the source has a supported type but does not decode |
| `LIMIT_EXCEEDED` | a count, size or pixel ceiling was exceeded |
| `INVALID_ARGUMENT` | an option is missing, of the wrong type or out of range |
| `PERMISSION_REQUIRED` | the user refused photo access in the picker |
| `ACCESS_DENIED` | a path lies where the script may not read or write, or every picked image did (host only) |
| `OUTPUT_EXISTS` | the explicit output exists and `overwrite` is not set (host only) |
| `SESSION_CLOSED`, `SESSION_REPLACED` | the session ended, or another one replaced it |
| `CANCELLED`, `TIMEOUT`, `IO`, `INTERNAL` | the call was cancelled, timed out, failed on I/O or failed otherwise |

## Ceilings

| Ceiling | Value |
| --- | --- |
| Images one picker returns | 200 |
| Viewer sources | 128 |
| Picker MIME filters, title length, display name length | 16, 200, 255 |
| Output side, output pixels | 16384, 40 MP |
| Options bundle, event bundle, error message | 16 KiB, 256 KiB, 4 KiB |
| Unclaimed session | 60 s |
| Host call timeout, `convert` timeout | 30 s, 180 s |

## Versioning

The plugin declares `galleryContractVersion` 1 and may advertise newer versions through
`galleryMaxContractVersion`; the host writes the negotiated version into every request. Version 1
peers never see methods or keys of a later version. The plugin declares its host requirement
through `requiresHostVersion`; the host refuses plugins that declare less than
`GalleryIds.REQUIRED_HOST_VERSION_CODE`.

## Security Boundary

- Only the host binds the service and starts the Activity: both require the signature permission,
  and the service checks the caller. Tokens are single-use and expire with the session.
- The plugin never receives a script path. Sources and targets travel as descriptors the host
  opened with the script's own access, so a script reaches nothing through the plugin that it
  could not open itself.
- The picker answers paths of the plugin's media library. The host keeps only those that exist,
  are regular files and are readable by the script; the rest are dropped, and a result left empty
  that way fails with `ACCESS_DENIED`. The plugin's photo permission is never extended to scripts:
  they only learn the images the user chose.
- Outputs are written by the host into staging files and published only after they read back as
  the reported image.

## Open Items

- The host client and the script object (roadmap 10.1, items 2 and 3) are described here once
  implemented.
