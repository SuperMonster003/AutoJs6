# Explorer Action protocol v21

## Purpose

Protocol v21 adds host-owned, durable history for the source-to-Trash operation introduced in v16. Its first consumer is Archive Manager. After an archive creation operation commits every output and the user has explicitly opted to move the sources to Trash, the host records one opaque batch before touching the first source.

The history lets the plugin show bounded per-source outcomes, rediscover the operation in a later Activity or host session, and request a best-effort grouped restore. It does not grant Trash paths, original paths, Trash record IDs, arbitrary restore destinations, or direct delete authority to the plugin.

Archive output publication and source recovery remain independent operations. Restoring sources never rolls back, replaces, or removes an archive that was already created.

## Binary compatibility

The following methods are appended at the absolute end of `IExplorerActionHostSession.aidl`:

```aidl
Bundle queryTargetTrashBatch(String targetTrashBatchId);
Bundle listTargetTrashBatches();
Bundle undoTargetTrashBatch(String targetTrashBatchId);
```

All v1-v20 Binder transaction numbers remain unchanged. Future methods must stay after the v21 methods. A host continues to accept catalogs that declare an older supported protocol, while a plugin must not call the new transactions unless the negotiated catalog protocol is at least 21.

## Durable batch creation

A v21-capable `moveTargetsToTrash` call retains every v16 proof requirement. In addition, the host:

1. validates the complete source selection and committed output proof;
2. reserves a canonical batch UUID and one host-private Trash UUID per source;
3. atomically persists every source identity and its initial state in `noBackupFilesDir`;
4. persists state transitions before and after each source crosses the durable Trash boundary;
5. returns the opaque batch UUID with the ordinary v16 result.

The durable record is scoped to the calling plugin UID and the exact canonical parent directory path, device, and inode. A later session for the same plugin and same parent can query or restore it. Replacing the parent directory with another inode does not inherit the history.

The record may contain original paths and Trash IDs internally because the host needs them for recovery. None of those values are placed in protocol Bundles. The plugin receives only the batch ID, source display names, timestamps, bounded counts, and protocol states.

An in-process move or restore registers its batch in one process-wide active set before releasing the durable-store lock. Other host sessions skip that batch during recovery and do not expose it through query or list results until the operation leaves the active set. This prevents a concurrent session from mistaking a live `PENDING`, `MOVING`, or `RESTORING` transition for process death. A real process death clears the in-memory marker; the next host process then reconciles the durable record against the source and Trash repository.

## Query result

A batch Bundle uses these fields:

```text
TARGET_TRASH_BATCH_ID
TARGET_TRASH_BATCH_STATE
TARGET_TRASH_BATCH_ITEMS
TARGET_TRASH_BATCH_CREATED_AT
TARGET_TRASH_BATCH_UPDATED_AT
TARGET_TRASH_MOVED_COUNT
TARGET_TRASH_RESTORED_COUNT
TARGET_TRASH_RECOVERY_COUNT
```

Each bounded item Bundle contains `TARGET_TRASH_ITEM_DISPLAY_NAME` and `TARGET_TRASH_ITEM_STATE`. The plugin must validate UUIDs, counts, timestamps, list bounds, item states, and aggregate-state consistency before presenting a result.

Batch states are:

- `AVAILABLE`: every available recovery item is still in Trash and the batch can be restored.
- `PARTIAL`: at least one item can still be restored while another item has a different terminal outcome.
- `RESTORED`: the sources represented by the batch were restored and no item remains available.
- `STALE`: no item remains restorable because a Trash record disappeared, an original name became occupied, or another external change invalidated the recorded state.
- `RECOVERY_REQUIRED`: host state is ambiguous or needs host-side recovery evidence to be retained.
- `FAILED`: no source crossed the Trash boundary and no recovery remains available.
- `UNKNOWN`: the operation is in a transient or unprovable state, or the requested batch is unknown to this scope.

Item states are `MOVED`, `NOT_MOVED`, `RESTORED`, `STALE`, `RECOVERY_REQUIRED`, and `UNKNOWN`. The moved count describes items that crossed the durable Trash boundary, including items whose later recovery now needs attention; it is not a count of currently restorable items.

`listTargetTrashBatches` is newest-first and bounded by the protocol maximum. An unknown, malformed, foreign, or out-of-scope batch never reveals whether a matching host-private record exists.

## Best-effort restore

`undoTargetTrashBatch` is non-cancellable once host restoration starts. The host processes only items that are still in `MOVED` state and records every transition durably. Each item is independent:

- The original top-level name is reserved without replacement before restoration.
- If that name already exists, the host leaves the occupant untouched, keeps the Trash item, and marks the history item stale.
- A missing or mismatched Trash record also makes that item stale.
- A successful restore consumes its Trash item and marks the history item restored.
- If interruption leaves the source and Trash facts ambiguous, the host retains the record as recovery required.
- Failure of one item does not undo a successful restore of another item and does not delete the created archive.

This is deliberately best-effort per source, not an all-or-none filesystem transaction. A lost Binder response is handled by querying the same batch ID; the client must not blindly repeat an unproven mutation.

## Recovery, retention, and capacity

On startup and before every history operation, the host correlates transient item states with its private Trash repository and the original source identity. This resolves safe duplicates toward `NOT_MOVED`, completed restores toward `RESTORED`, available Trash records toward `MOVED`, and ambiguous facts toward `RECOVERY_REQUIRED`.

History is bounded to 32 records and normally retained for 30 days after its latest update. Capacity pressure may evict only terminal, non-restorable `RESTORED`, `STALE`, or `FAILED` records. It never evicts an `AVAILABLE` or `PARTIAL` record merely to make room; if every slot still contains recovery work, a new source-to-Trash batch is rejected safely. Age-based history expiration may remove an older restorable protocol record, but does not remove the underlying host Trash item or its normal Trash UI recovery path. `UNKNOWN` and `RECOVERY_REQUIRED` records are retained for host-side reconciliation.

## Client responsibilities

A v21 client must:

- keep source deletion opt-in and disabled by default;
- commit and verify all physical archive outputs before requesting the source-to-Trash phase;
- store and present only the opaque batch ID and validated metadata;
- re-query after Activity recreation, host-session recreation, or a lost restore response;
- describe partial and stale outcomes without claiming atomic rollback;
- make clear that restoring sources keeps every created archive;
- never offer overwrite as a conflict resolution for source recovery;
- treat malformed or unknown results as unproven, not as success.

## Verification

Host instrumentation covers same-session query and restore, cross-session discovery and restore, conflicting original names, partial restore, output preservation, idempotent terminal queries, opaque scope enforcement, protocol catalog compatibility, and a latch-controlled concurrent recovery attempt at the source deletion boundary on Android 7 through Android 15.

Archive Manager instrumentation covers strict result decoding, lost-response re-query, completion-page recreation, history discovery, source restoration with byte equality, archive preservation, and the default-off source-to-Trash option. The feature uses only the plugin-owned archive creation page and does not change the file manager's global layout or established action styling.
