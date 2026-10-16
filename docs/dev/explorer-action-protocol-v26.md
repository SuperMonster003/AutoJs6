# Explorer Action protocol v26

## Purpose

Protocol v26 lets an action that renames its targets (v25 `targetRename`) read the photo metadata of those targets
and of the entries it listed below them. Its consumer is the Batch Rename plugin (`AutoJs6-Plugin-Batch-Rename`,
roadmap D34): the metadata variables `$CAMERA_MAKE` ... `$COLOR_SPACE` of its file manager screen need the values the
`files.batchRename` script path reads on the host. The host reads the files; the plugin still receives no file and no
descriptor, only raw values.

## Status

Complete. `readMetadata` is appended to `IExplorerActionHostSession`; it delegates to the host's
`BatchRenameMetadataReader` (`core/plugin/batchrename`), which the script path shares.

## Binary compatibility

One transaction is appended after `undoRenameBatch`; every v1-v25 Binder transaction number is unchanged. Catalogs of
older protocol versions remain valid, and `targetRename` keeps requiring protocol 25 or later. A plugin learns whether
the host has v26 from `ExplorerActionIntentExtras.HOST_VERSION_CODE` (the protocol extra of a request carries the
action's own catalog version, not the host's); on an older host the transaction does not exist.

Plugin test doubles that implement `IExplorerActionHostSession.Stub` (3-Maple Image, 3-Stack Archive, 3-Amber Player,
Batch Rename) must implement `readMetadata` once they stage a protocol-26 `explorer-action-api.aar`; staying on their
current AAR keeps them unaffected.

## readMetadata

`Bundle readMetadata(in Bundle request)`, only for an action that declared `targetRename` (others get the usual access
error, `IllegalStateException`).

| Request key | Type | Meaning |
| --- | --- | --- |
| `metadataEntries` | ArrayList of Bundle | 1 to 128 entries, each with `renameTargetId` and `relativePath` (empty for the target itself) |

Each entry is a request target or a descendant this session listed through `listChildren`, resolved like
`renameEntries`; anything else fails the call with `IllegalArgumentException`.

| Answer key | Type | Meaning |
| --- | --- | --- |
| `metadataItems` | ArrayList of Bundle | one per request entry, in request order; `metadata` (Bundle) when the host read any |

Only image files answer metadata (JPEG, PNG, WebP, HEIF, DNG and other RAW formats, by extension); directories, other
files and unreadable images answer an empty Bundle. The metadata Bundle uses `ExplorerPhotoMetadataKeys`, the same
keys as `BatchRenameMetadataKeys` of the Batch Rename contract:

| Key | Type | Source |
| --- | --- | --- |
| `cameraMake`, `cameraModel`, `lens` | String | EXIF Make, Model, LensModel |
| `iso` | Int | EXIF PhotographicSensitivity |
| `fNumber` | Double | EXIF FNumber, else ApertureValue |
| `exposureTime` | Double | EXIF ExposureTime in seconds, else ShutterSpeedValue |
| `focalLength` | Double | EXIF FocalLength in millimeters |
| `flash` | Int | EXIF Flash bit field |
| `width`, `height` | Int | EXIF pixel dimensions, else the image header |
| `author`, `copyright` | String | EXIF Artist and Copyright, else XMP `dc:creator` and `dc:rights` |
| `latitude`, `longitude`, `altitude` | Double | EXIF GPS, degrees (negative south and west) and meters |
| `dateTaken` | String | EXIF DateTimeOriginal, else DateTimeDigitized, as `yyyy-MM-ddTHH:mm:ss` without time zone conversion |
| `exposureBias` | Double | EXIF ExposureBiasValue in EV |
| `orientation` | Int | EXIF Orientation, 1 to 8 |
| `colorSpace` | String | `sRGB`, `AdobeRGB` (ColorSpace 2, or uncalibrated with interoperability index R03) or `Uncalibrated` |

A missing key means the image does not record the value. Text values lose control characters and are cut to 256
characters.

## Limits

| Limit | Value |
| --- | --- |
| Entries of one `readMetadata` request | 128 |
| Characters of one text value | 256 |
