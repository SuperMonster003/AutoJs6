# Explorer Action host file information v1

## Purpose

The host-file-information extension lets an authorized Explorer Action plugin enrich an existing
host-owned file information dialog without replacing the dialog, its actions, or its fallback
behavior. Its first consumer is APK Inspector: AutoJs6 renders its normal Android package fields
first, then may append a compact plugin report containing signature verification, manifest
component exposure, permission groups, native ABIs, DEX counts, compatibility findings, and the
source SHA-256.

This is an optional service capability rather than a new catalog protocol version. The plugin keeps
advertising Explorer Action protocol v22 and keeps its Activity action. Hosts that do not understand
this extension therefore discover and launch the plugin exactly as before.

## Binary compatibility and negotiation

The following transaction is appended at the absolute end of `IExplorerActionPlugin.aidl`:

```aidl
Bundle getHostFileInfo(in ParcelFileDescriptor source, in Bundle request);
```

All existing service transaction numbers remain unchanged. A plugin advertises the transaction by
setting `ExplorerActionCapabilityKeys.HOST_FILE_INFO_VERSION` to `1` in `PluginInfo.capabilities`.
A host must not call it unless the accepted plugin advertises a supported capability version.

The four mixed-version cases are deliberately asymmetric and safe:

- An old host and a new plugin continue using the v22 catalog and Activity entry.
- A new host and an old plugin retain the host's basic file information only.
- A new host and a new plugin may append the plugin summary.
- A missing, disabled, unauthorized, incompatible, stopped, or failing plugin retains the host's
  basic file information only.

The capability changes no menu placement and does not suppress the existing Activity action.

## Request

The host selects an already accepted catalog action that matches one readable regular file and is a
single-target, read-only Activity action with no output. It rechecks plugin enablement and trust,
opens the canonical source as a read-only `ParcelFileDescriptor`, and sends a `Bundle` containing:

- extension version `1`;
- the accepted catalog action ID;
- a bounded display name;
- source byte size and last-modified metadata;
- the lowercase SHA-256 previously computed by the host's own package analysis.

The call runs off the main thread. The plugin receives only the descriptor and bounded metadata; it
does not receive a filesystem path, parent URI, directory listing, write handle, or output channel.
Each peer closes its local descriptor instance. The plugin must finish reading its duplicate before
the synchronous call returns.

## Result

A successful result has error code `ERROR_NONE`, extension version `1`, a one-line localized title,
a localized plain-text summary, and the SHA-256 calculated while the plugin copied the descriptor.
The protocol limits the title to 160 characters and the summary to 16384 characters, 24 lines, and
1024 characters per line. Error text is diagnostic only and limited to 240 characters.

The host accepts a successful result only when:

- the result version and error code are valid;
- the returned SHA-256 is exactly 64 hexadecimal characters and equals the host's earlier digest;
- the title and summary are non-empty and within every envelope limit;
- control and Unicode format characters can be removed without erasing the content.

The host renders the result as plain text. It does not interpret markup, links, spans, intents, or
plugin-supplied actions.

## Host fallback invariant

AutoJs6 completes and renders its existing Android package analysis before requesting this optional
extension. The additional table row starts as `GONE` and becomes visible only after a validated
success. The original format, label, package name, version, installed version, components, file
size, signature scheme, SDK, installation exception, icon, Install button, Manifest button, Cancel
button, error handling, and dismissal behavior are not delegated to the plugin.

Cancellation closes the dialog scope and prevents a late result from changing the UI. Discovery,
binding, remote exceptions, malformed bundles, hash mismatches, source changes, and inspection
errors are intentionally silent in the dialog and leave the basic host presentation intact.

## APK Inspector behavior

APK Inspector validates the capability version, exact action ID, supported extension, declared
size, last-modified field, and expected digest before parsing. It compares descriptor size when
available, copies at most 4 GiB while preserving a 128 MiB cache reserve, calculates SHA-256 during
the copy, verifies the copied size and digest, and marks the private snapshot read-only before any
archive parser sees it. The snapshot directory is removed immediately after the summary call.

The compact result reuses the plugin's localized report vocabulary and bounded inspectors. It
includes selected-base APK signature verification when available, four manifest component groups,
permission protection counts, native-library ABIs and size, DEX count and size, a structural/device
compatibility conclusion, bounded problem codes, and source SHA-256. The independent full report
Activity remains available for certificate details, manifest viewing, device simulation, sharing,
and other interactive inspection features.

## Verification

Host tests cover control-character normalization, digest normalization, title, line, and total
envelopes. Plugin tests cover exact action and capability admission, supported file names, 4 GiB
size bounds, last-modified admission, and SHA-256 syntax. Both projects compile against the appended
AIDL surface; the plugin consumes an AAR built with Kotlin metadata readable by its compiler.
