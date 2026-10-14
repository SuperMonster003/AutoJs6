# Explorer Action protocol v24

## Purpose

Protocol v24 lets a read-only viewer move the files it was opened with into the host Trash. Its
first consumer is 3-Maple Image, whose viewer shows a delete button when it was opened from the
file manager: the user deletes the image on screen, sees an Undo prompt, and can still restore the
file later from the host Trash page.

v16 already moves request targets into the host Trash, but only for an archive action that has
committed its outputs, and only for the complete selection at once. A viewer has no outputs and
deletes one page at a time, so v24 relaxes exactly those two rules for actions that ask for it.
Every other v16 and v21 rule stays: the host revalidates each file before moving it, keeps a
durable recovery copy first, records an opaque batch and restores it without overwriting.

## Binary compatibility

No AIDL changes. Every v1-v23 Binder transaction number remains unchanged. Hosts continue to
accept catalogs for older supported protocol versions, and older hosts reject a protocol-24
catalog through the existing advertised-version check, so the action does not appear there.

## Catalog declaration

An action may set `ExplorerActionCatalogKeys.TARGET_TRASH` (`targetTrash`) to `true`. The
declaration is valid only for:

- protocol 24 or later;
- `TARGET_FILE` with `CARDINALITY_SINGLE` or `CARDINALITY_MULTIPLE`, in any placement;
- `ACCESS_READ_ONLY` and Activity presentation;
- no output mode, unmatched-file probe or `filesystemPath`.

`readSiblings`, `playbackProgress` and related-file suffixes may be combined with it; they keep
their read-only meaning, and none of the files they expose can be moved. Any other combination
rejects the catalog action rather than broadening it. An absent or `false` value keeps the
pre-v24 meaning of the action.

The host always attaches an `IExplorerActionHostSession` to such an action, also for a single
file that declares no other session capability.

## Moving targets

For a `targetTrash` session, `moveTargetsToTrash(targetIds, outputTransactionIds)` accepts:

- `outputTransactionIds`: must be empty. Output proof belongs to the v16 archive path.
- `targetIds`: one or more distinct request target IDs, in the order of the original request.
  Any non-empty subset is allowed, for example only the page a viewer shows.

A request is rejected before anything is touched when it names an unknown, duplicated or
reordered ID, a target this session already moved, or a target whose device, inode, size or
modification time changed since the launch. Each accepted call records its own v21 batch and
returns the v16 result Bundle with that batch ID, so a session can delete several times.

Repeating the previous call with the same list returns the stored result instead of moving
anything again, as long as that call moved one of its targets; a client that lost a response can
also call `queryTargetTrash`, which returns the result of the most recent call. A target whose move
failed may be requested again, and so may a target the host restored (below).

Sessions without the declaration keep the v16 contract unchanged: the complete selection, a
complete output proof, and one terminal result per session.

## History and undo

`queryTargetTrashBatch`, `listTargetTrashBatches` and `undoTargetTrashBatch` are available to
`targetTrash` sessions with the v21 scope (the calling plugin UID and the exact parent directory)
and the v21 rules: best-effort restore per item, no overwrite of a name that is occupied again,
and stale or recovery-required items left in the host Trash.

When `undoTargetTrashBatch` restores a target of the current session to its original path, the
session adopts the identity of the restored file, so the user can delete it again in the same
viewer.

## Retention

v21 history keeps at most 32 batches and never evicts one that can still be restored. A viewer
creates one batch per deletion, so v24 batches follow a looser rule: when the history is full, the
host first evicts finished batches as before and then the oldest v24 batches that can still be
restored, provided none of their items is in transit or needs recovery. Eviction removes only the
undo record. The moved files stay in the host Trash, where the Trash page restores them as usual.
v21 archive batches are never evicted for a v24 batch.

## Client responsibilities

A protocol-24 client must:

- declare `targetTrash` only on a read-only viewer action that offers deletion to the user, and
  call `moveTargetsToTrash` only after an explicit user action;
- pass only IDs from the launch request and never output IDs;
- treat siblings opened through `readSiblings` and files below a directory as out of reach: they
  are not request targets;
- validate the result Bundle and the batch metadata as in v16 and v21, and query instead of
  retrying blindly after a lost response;
- offer Undo through `undoTargetTrashBatch` and describe partial or stale outcomes plainly.

## Verification

Catalog policy tests cover the accepted shapes (single file with or without sibling paging,
multiple files), the protocol gate and the rejection of directory, mixed, writable, host-explorer,
output and `filesystemPath` declarations. Host instrumentation
(`ExplorerActionTargetTrashV24InstrumentationTest`) covers moving a subset, a later deletion in
the same session, repeated requests, undo with byte equality and deleting the restored file again;
the rejection of output proof, empty, unknown, duplicated, reordered and changed targets without
touching any file; the unchanged v16 contract for sessions without the declaration; and the
eviction of the oldest v24 batch at full history while its Trash item and a restorable v21 archive
batch remain.
