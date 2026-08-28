# Explorer Action protocol v17

## Purpose

Protocol v17 lets a trusted archive plugin offer an explicit fallback for a file whose name and MIME type do not match its normal archive action. It also lets the plugin report the structurally detected archive format after the existing host-native archive session opens.

The first consumer is Archive Manager. A file such as `backup.data` can show `Open as archive...` in its overflow menu. The host does not read or scan that file in the background. Only a user click opens the existing read-only archive session, and the plugin performs its normal bounded structural detection exactly once.

## Binary compatibility

Protocol v17 does not add an AIDL method. The existing `openArchiveV11` request and `IExplorerArchiveSession` Binder interface remain unchanged, so every v1-v16 transaction number is preserved.

The action catalog protocol version is `17`. Hosts continue to parse older catalogs by their declared version and reject catalogs that require a newer unknown protocol.

v17 adds one optional action-catalog key:

```text
probeForActionId
```

It also adds three required session-info keys for v17 host-explorer archive sessions:

```text
formatId
formatDisplayName
displayNameMatchesFormat
```

## Fallback catalog action

A fallback descriptor names its normal primary archive action through `probeForActionId`. The host accepts it only when every constraint below holds:

- the catalog declares protocol v17 or later;
- the fallback is `FILE + SINGLE + READ_ONLY + OVERFLOW + HOST_EXPLORER`;
- it declares no MIME, extension, compound-suffix, output, replacement, directory, or multi-target capability;
- it does not reference itself;
- the referenced descriptor exists in the same plugin catalog;
- the referenced descriptor is `FILE + SINGLE + READ_ONLY + PRIMARY + HOST_EXPLORER` and is not itself a fallback;
- at most one accepted fallback references a given primary action.

Missing references, invalid action pairings, duplicate fallback claims, and descriptors published by v16 or older catalogs are discarded during catalog parsing.

## Matching and execution

The host evaluates descriptors within one plugin package. For exactly one ordinary file:

- if the referenced primary action matches the bounded MIME, leaf extension, or full-name suffix metadata, only the primary action is returned;
- if the primary action does not match, the fallback action is returned in the overflow menu;
- directories, multi-selections, cross-parent selections, and virtual targets never receive the fallback.

This is an explicit user-triggered recheck, not background magic probing. Selecting the fallback follows the same UID-pinned binding, read-only descriptor, source identity, bounded staging, password recovery, sibling-volume, and session-lifecycle path as the normal primary action. An unsupported or malformed file returns the existing typed archive-open failure and does not gain another retry or permission path.

## Detected format metadata

After structural detection succeeds, a v17 archive session reports:

- `formatId`: a stable lowercase identifier containing only letters, digits, period, underscore, or hyphen, bounded to 64 characters;
- `formatDisplayName`: a sanitized non-empty display label bounded to 64 characters, such as `ZIP` or `TAR.GZ`;
- `displayNameMatchesFormat`: whether the original display name already carries an extension or numbered first-volume name accepted for the detected format.

The host rejects a v17 session that omits or malforms these fields. v5-v16 sessions keep their previous behavior and receive no synthesized format claim.

When `displayNameMatchesFormat` is false, the native archive path bar annotates only its archive-root segment, for example `backup.data [ZIP]`. Internal path segments remain unchanged. The metadata is informational and never changes write, mutation, extraction, or sibling-volume authorization.

## Compound TAR suffixes

Protocol v14 already introduced bounded complete-name suffix matching. A v17 Archive Manager catalog uses that existing field for exact `tar.gz`, `tar.xz`, `tar.bz2`, and `tar.zst` names while retaining the leaf aliases `tgz`, `txz`, `tbz2`, and `tzst`.

Generic stream leaves such as `gz`, `xz`, `bz2`, and `zst` are not published as archive extensions. This prevents unrelated compressed streams from receiving the primary archive action; users can still explicitly choose `Open as archive...`, after which the plugin must verify both the outer compression signature and the inner TAR structure.

## Security properties

Protocol v17 adds no file, directory, URI, write, delete, or arbitrary-path authority. The fallback descriptor is intentionally matcher-free and read-only. It cannot be a primary button, selection action, Activity action, output action, or writable action.

The host performs no speculative file I/O while building a menu. A click reuses one existing archive-open call instead of making a probe call followed by a second open call. Source identity and descriptor ownership remain checked by the same session code used by recognized archive names.

## Verification

Host JVM tests cover protocol admission, exact compound TAR suffixes, fallback descriptor pairings, reference validation, duplicate claims, recognized-name suppression, single-file-only matching, and actual-format path annotation.

Archive Manager JVM and instrumentation tests cover its six-action catalog, exact suffix publication, structurally detected ZIP metadata for both recognized and disguised names, and the absence of generic compressor-leaf extensions. Cross-APK device tests exercise the real host parser, menu routing, existing archive session, and path-bar metadata boundary.
