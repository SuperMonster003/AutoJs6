# Readium EPUB Reader plugin integration

## Scope

The Readium EPUB Reader integration lets the AutoJs6 file manager recognize `.epub` e-books and hand
them to the standalone `AutoJs6-Plugin-Readium-EPUB-Reader` project through the existing Explorer
Action v2 contract. The host gains a file type, a previewer hand-off and a per-type archive limit;
it does not embed any Readium code, WebView rendering or book storage of its own.

The boundary is:

| Component | Owner |
| --- | --- |
| `FileUtils.TYPE.EPUB`, its icon and `PreviewerType.EPUB` | AutoJs6 host |
| Leftmost icon action, primary action resolution and the "open with other apps" fallback | AutoJs6 host (`ExplorerDocumentPreviewerPluginUi`) |
| Archive extraction limit for a book opened straight out of an archive | AutoJs6 host (`ExplorerArchiveCoordinator`) |
| Explorer Action envelope, descriptor grant and catalog acceptance | AutoJs6 host (Explorer Action v2, see the protocol documents) |
| Parsing, rendering, navigation, progress memory and reader settings | Readium EPUB Reader plugin |

The plugin package is `io.github.supermonster003.autojs6.plugin.readium.epub.reader`. Its catalog
advertises a primary action and an overflow action for `application/epub+zip` and the `.epub`
extension, both backed by one Activity, and it accepts an `application/zip` MIME type for a file whose
name ends with `.epub` (sniffed containers), while refusing PDF and comic-book MIME types.

## File type

`.epub` joins the `PreviewerType` family next to Markdown and HTML:

- `TypeDataHolder.EPUB_READER` carries a dedicated glyph and only `TYPE.IDENTITY_PREVIEWABLE`; the type
  is neither text-editable nor an archive entry point, so long-press and primary actions treat the file
  as an opaque binary document.
- The type is decided by extension, case-insensitively, regardless of content. A `.epub` that is not a
  valid container is still handed to the plugin, which shows its own "not an EPUB" error instead of
  the host guessing.
- `ExplorerPrimaryAction.DOCUMENT_PREVIEWER` therefore resolves for `.epub` exactly as it does for
  Markdown and HTML, which keeps the leftmost icon action, the tap behavior and the fallback path
  identical across the three previewer types.

## Hand-off

`ExplorerDocumentPreviewerPluginUi.specFor` maps `PreviewerType.EPUB` to the plugin package, the
localized name `plugin_readium_epub_reader_name` and the MIME type `application/epub+zip`. The
existing previewer flow stays unchanged:

1. When the official plugin is installed, enabled and accepted, the host builds the Explorer Action
   v2 envelope (two-item ClipData with the document and parent URIs, host version code and source
   surface) and starts the plugin Activity with a read-only descriptor grant.
2. When the plugin is missing, disabled, unauthorized or protocol-incompatible, the host shows the
   usual availability dialog offering Plugin Center filtered to the package, or "open with other apps"
   using the same MIME type.

The plugin never receives a filesystem path; it reads the book through the granted descriptor and
keys its progress memory by a content fingerprint, so renaming or moving the file in the file manager
keeps the reading position.

## Archive limit

Books are large containers, so `maximumArchiveDocumentBytes(previewerType)` returns 256 MiB for
`PreviewerType.EPUB` and keeps the 8 MiB limit for Markdown, HTML and unknown types. The limit applies
only when the document is opened straight out of an archive entry; a book already on disk is never
size-checked by the host.

## Tests

`DocumentPreviewerFileTypeTest` covers the new type and the per-type limit:

- `epubIsPreviewableAsEpubByExtensionRegardlessOfContent` checks a ZIP-like and a plain-text `.epub`
  both resolve to `TYPE.EPUB`, are previewable, carry `PreviewerType.EPUB`, are not text-editable, and
  resolve to the document previewer primary action.
- `archiveDocumentLimitIsPerPreviewerType` checks the 256 MiB and 8 MiB limits.

Device acceptance (opening a book from the file manager, from an archive and after a rename) is
tracked in the plugin repository's `ROADMAP.md` (P1.5, and P8.3 for the release smoke) together with
the plugin's own instrumentation suite.

## Status

Plugin 1.0.0 (build 65, plugin commit `ca3044b`, release artifact `autojs6-plugin-readium-epub-reader-v1.0.0-d84b0ec1.apk`) is the first release, cut on
2026-09-21 against this host at 6.8.0 build 5282 (Explorer Action protocol v2, EPUB contract version 1,
host protocol 22). The host side of that release is complete: the file type and hand-off above, the
`plugin-api/epub-api` contract module, the `core/plugin/epub` client, the `epub` script module with its
reader session (`epub.read`) and the three sample scripts under the in-app sample directory `电子书`; the
host changelog carries one line per piece (file type, contract module, host client, script API, reader
control, samples). The plugin's roadmap P8 records the release gate (Temurin build, unit tests, lint,
the device matrix, the signed release artifact and the four entry paths: file manager, launcher,
`ACTION_VIEW` and script). Highlights, notes and export (plugin roadmap P9, plugin 1.1.0) are planned
without a host change; a new `epub` capability would go through the contract's append-only AIDL rule
(`docs/dev/epub-plugin-protocol-v1.md`).
