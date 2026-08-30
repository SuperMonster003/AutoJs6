# Explorer Action protocol v22

## Purpose

Protocol v22 adds one narrow read-only related-file capability for a selected file. Its first
consumer is APK Inspector, which can request the exact APK Signature Scheme v4 sidecar name
`<selected APK name>.idsig` without receiving a parent-directory listing, filesystem path, or a
general sibling-file handle.

The plugin declares suffixes rather than names or paths. The host derives every candidate as the
selected target's display name followed by one declared suffix. This makes the authority finite,
reviewable from the catalog, and independent of plugin-supplied path components.

## Binary compatibility

The following method is appended at the absolute end of `IExplorerActionHostSession.aidl`:

```aidl
ParcelFileDescriptor openRelatedFile(String targetId, String suffix);
```

Every v1-v21 Binder transaction number remains unchanged. A client must not call the new
transaction unless its accepted catalog entry declares protocol 22. Hosts continue to accept
catalogs for older supported protocol versions.

## Catalog declaration

An action may add `ExplorerActionCatalogKeys.RELATED_FILE_SUFFIXES`, an `ArrayList<String>` with at
most eight entries. Each entry is trimmed, lowercased, no longer than 128 characters, and must
match this grammar:

```text
.<segment>[.<segment>...]
segment := [a-z0-9][a-z0-9+_-]*
```

The declaration is valid only for a protocol-22, single-file, single-target, read-only Activity
action with no output capability. It does not imply `READ_SIBLINGS`; directory paging and general
`openFile` access remain unavailable. Invalid or duplicate-normalized declarations reject the
catalog action rather than broadening it.

For APK Inspector, the complete declaration is the singleton suffix `.idsig`. If the selected
display name is `release.apk`, the only derived candidate is `release.apk.idsig`.

## Frozen related-file scope

At host-session creation, the host evaluates each `(targetId, declared suffix)` pair exactly once.
It accepts only a readable regular file whose unresolved and canonical paths are the same direct
child of the already validated common parent. Symbolic links, directories, path escapes, and
unreadable entries are ignored.

For an accepted candidate, the host freezes its device, inode, byte size, modification time, and
Java last-modified value. A candidate that is absent at session creation remains absent for that
session even if a matching file appears later. No candidate names, existence inventory, parent
path, or directory contents are returned to the plugin.

`openRelatedFile` accepts only a target ID already bound to the session and a suffix in the exact
normalized catalog set. The host permits at most 32 calls per session. It revalidates the frozen
path identity immediately before opening, opens the file read-only, and compares the resulting
descriptor with the frozen device, inode, size, and modification time before returning it. A
replacement or ambiguous identity fails closed; the descriptor is closed on any failed
post-open check.

The ordinary Explorer Action UID pin, Binder death handling, and session-close rules apply. A
descriptor already returned remains an immutable reference to the opened inode, while a closed
session refuses all later calls.

## Client responsibilities

A protocol-22 client must:

- declare only suffixes required by its feature and validate that the negotiated protocol is 22;
- use the exact target ID supplied in the request metadata;
- treat a null descriptor as an absent sidecar, not as permission to search the parent;
- copy any needed bytes into plugin-private storage while the launch session is alive;
- enforce its own format and byte limits and close every descriptor promptly;
- treat malformed, changed, or cryptographically invalid related data as unverified;
- close the host session after input staging and never retain the Binder as ambient file access.

## Verification

Catalog policy tests cover protocol, cardinality, access, presentation, output, normalization, and
suffix-grammar rejection. Host instrumentation covers exact `.idsig` opening, undeclared suffix
rejection, denial of parent listing and general sibling opening, replacement detection, absence
freezing, and symbolic-link rejection. APK Inspector tests independently cover valid v4/v4.1,
rotation, multiple signers, missing sidecars, malformed sidecars, signed-root tampering, embedded
Merkle-tree tampering, and APK-byte tampering.
