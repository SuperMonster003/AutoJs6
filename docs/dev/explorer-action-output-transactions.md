# Explorer Action v3 output transactions

Explorer Action protocol v3 adds one host-owned output transaction for actions that transform a
single file without modifying the source. The first production consumer is the independent Image
Tools plugin. Protocol v1 and v2 read-only actions remain unchanged.

## Scope

The v3 host currently accepts an output-producing action only when all of these conditions hold:

- The plugin capability and action catalog both advertise protocol version 3.
- The target is one readable file on `ExplorerSurface.MAIN_FILES`.
- The action uses read-only input, overflow placement, and `OUTPUT_CREATE_SIBLING`.
- Every output MIME type is `image/jpeg`, `image/png`, or `image/webp`.
- `maxOutputBytes` is between 1 byte and 256 MiB.
- `outputNameSuffix` matches `[A-Za-z0-9._+-]+` and is at most 128 characters.

Invalid actions are rejected during catalog discovery. The same policy is checked again before
launch and publication.

## Request envelope

The host launches the plugin's explicit action Activity with these URI rules:

- `Intent.data` is the source content URI.
- `ClipData` contains exactly one item, the same source URI.
- The source receives only the launch Intent's exact read grant.
- The parent directory URI is omitted.
- `ExplorerActionIntentExtras.OUTPUT_URI` is a transaction-specific content URI with an explicit
  exact write grant. It is not placed in `ClipData`.
- The output URI cannot be read, queried, seeked, replaced, or opened more than once.

The request also includes the action ID, protocol version, display name, source size, host identity,
source surface, output transaction ID, accepted output MIME types, byte limit, and output name
suffix. Plugin implementations should use constants from `explorer-action-api` rather than copying
string literals.

## Plugin contract

An output-producing plugin must follow this sequence:

1. Validate protocol version 3, action ID, the one-item `ClipData`, transaction ID, output URI,
   output MIME types, and byte limit before doing work.
2. Read only from the source URI through `ContentResolver`.
3. Keep editing or conversion state in the plugin's private storage and memory.
4. Open the output URI exactly once with mode `w`, write one complete supported image, and close the
   stream before returning.
5. Return `RESULT_OK` with only the unchanged output transaction ID. Do not return a path or URI.
6. Return `RESULT_CANCELED` when the user cancels or output cannot be completed.

The plugin never chooses or observes the final sibling path. A successful Activity result is only a
request for the host to validate and commit the staged bytes.

## Host transaction

The durable journal uses this state sequence:

```text
PREPARED -> LAUNCHING -> LAUNCHED -> RESULT_RECEIVED -> SNAPSHOTTED
         -> VALIDATED -> COMMITTING -> COMMITTED
```

Every non-terminal state can instead move to `ROLLED_BACK`. `LAUNCHING` is written before Android
starts the plugin, closing the launch crash window without ever launching the same action twice.

The write-only provider drains a reliable pipe into host-private staging storage while enforcing the
action limit, the 256 MiB host ceiling, and a free-space reserve. After `RESULT_OK`, the host waits for
the writer to close, revokes the output grant, snapshots the bytes, and validates the complete image:

- PNG chunk structure, CRC values, terminal `IEND`, and exact EOF.
- JPEG marker and scan structure, terminal EOI, and exact EOF.
- WebP RIFF size, chunk structure, and exact EOF.
- A bounded native `BitmapFactory` decode for all accepted formats.

The host pins the plugin UID, version, update time, and signing certificate digests. It also pins the
canonical source directory and its device/inode identity and rechecks these values before opening the
writer and before publication.

## Publication and recovery

The validated snapshot is copied to a random temporary file in the source directory, flushed, and
verified by size, SHA-256, and inode identity. Publication prefers a hard-link no-replace commit. File
systems that reject hard links with a known capability error use a same-directory atomic rename
fallback with independent 128-bit temporary and destination tokens.

The final name has this shape:

```text
<source-base>_<output-suffix>-<random-token>.<validated-extension>
```

The source base is truncated at a UTF-8 boundary so the complete name stays within the 255-byte file
name limit. The source is never overwritten.

Startup maintenance rolls back `PREPARED`, `LAUNCHING`, and `LAUNCHED` transactions. It resumes
post-result states from their durable artifacts, verifies a destination left by an interrupted commit,
and either finalizes it or rolls the transaction back. Committed destinations are not deleted during
error cleanup.

## Failure and compatibility behavior

- Missing, disabled, untrusted, incompatible, or invalid plugins contribute no dynamic action.
- Cancellation, malformed output, timeout, size overflow, identity change, or storage failure creates
  no sibling file and leaves the source unchanged.
- Protocol v1 and v2 retain their two-item read-only `ClipData` envelope with source and parent URIs.
- v3 read-only actions may continue to use `OUTPUT_NONE`; only output-producing actions use the
  transaction envelope.

Future output types must add a format-specific structural validator, a native decoder or equivalent
semantic verifier, an explicit host limit, and end-to-end recovery tests before being accepted by the
catalog policy.
