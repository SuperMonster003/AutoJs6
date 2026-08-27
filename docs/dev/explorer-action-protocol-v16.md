# Explorer Action protocol v16

## Purpose

Protocol v16 adds an opt-in, host-owned source-to-Trash handoff after archive creation. Its first consumer is Archive Manager. The feature exists so a plugin can offer "move source items to Trash after compression" without receiving arbitrary filesystem paths or direct delete authority.

This is deliberately a separate phase from output creation. The host accepts the request only after every physical archive output has been verified and committed, and it revalidates both the output proof and every selected source before removing any source data.

## Binary compatibility

The following methods are appended immediately after the v15 output-batch methods in `IExplorerActionFileSession.aidl`:

```aidl
Bundle moveTargetsToTrash(
    in List<String> targetIds,
    in List<String> outputTransactionIds
);

Bundle queryTargetTrash();
```

All v1-v15 method transaction numbers remain unchanged. Any later local or unpublished AIDL methods must remain after these v16 methods so an Archive Manager client compiled against the published v16 AAR invokes the same Binder transactions.

The action catalog protocol version is `16`. Hosts continue to parse older catalogs according to their declared version and reject catalogs that require a newer unknown protocol.

## Authorization and proof

`moveTargetsToTrash` succeeds only when both input lists are exact:

- `targetIds` must equal the complete original session selection in the original order. A subset, duplicate, reordered list, unknown ID, or added target is rejected.
- `outputTransactionIds` must equal every physical output created by this operation in commit order. Each value must be a canonical UUID and each transaction must still be provably committed in the same file session.
- Every source root must still have the canonical location, type, device, inode, size, and modification identity captured by the session. Any source change rejects the request before the Trash boundary.
- The plugin supplies opaque IDs only. It cannot choose a source path, Trash path, deletion algorithm, or extra target.

The option is off by default in the client. Protocol availability alone never opts the user in.

## Durable Trash boundary

The host performs each source move through its existing `TrashRepository`. Before source removal it:

1. copies the source into a private temporary Trash blob;
2. synchronizes file data;
3. publishes the recovery blob with a collision-refusing atomic rename;
4. synchronizes the containing directory;
5. persists the Trash database or recovery record;
6. runs the final source-identity validator;
7. removes the source;
8. refreshes Explorer for every changed item.

No source data is intentionally removed before a durable recovery copy and record exist. If a directory can be removed only partly, or a later selected source fails after earlier sources were moved, the host retains every available recovery blob and record and reports recovery required. It never cleans the only recovery copy merely to report rollback.

This contract does not claim filesystem-level atomic visibility across multiple source roots. A process death after the recovery record is durable but before source removal may temporarily leave both the source and its Trash copy. That duplicate is safer than removing a source without recoverable data.

## Terminal result

The result Bundle contains one terminal state and bounded proof fields:

```text
TARGET_TRASH_STATE
TARGET_TRASH_ITEM_IDS
TARGET_TRASH_MOVED_COUNT
TARGET_TRASH_RECOVERY_COUNT
```

States are:

- `COMMITTED`: every selected source was moved into the host Trash.
- `RECOVERY_REQUIRED`: at least one durable recovery item exists, but the complete selection did not reach the ordinary committed outcome.
- `FAILED`: the host rejected or failed the operation before entering an uncertain result boundary.
- `UNKNOWN`: the client cannot prove a host terminal state.

One file session publishes at most one target-Trash terminal result. Repeating `moveTargetsToTrash` returns that result instead of starting another move. If a Binder response is lost, the client calls `queryTargetTrash`; it does not blindly retry the mutating request.

The host result decoder and Archive Manager client both reject malformed states, impossible counts, duplicate or oversized item-ID lists, and inconsistent recovery evidence.

## Lifecycle and recovery limits

The host keeps the operation non-cancellable once the durable Trash phase begins. The Activity disables Back and cancellation for that short boundary and reports the four terminal categories separately.

The durable Trash item remains recoverable through the existing host Trash UI. Protocol v16 does not yet add a session-level grouped Undo Snackbar, grouped Trash history, or strict all-or-none visibility for multiple source roots. Those are presentation and recovery improvements, not permission to weaken the durable-copy-first invariant.

## Client responsibilities

An Archive Manager client must:

- leave the option off unless the user explicitly enables it;
- complete source scanning, archive writing, full read-back verification, and output commit first;
- retain the ordered committed output transaction IDs for normal, split, and per-item archive creation;
- submit the complete original target list and complete output proof exactly once;
- treat missing or malformed proof as a local failure without calling the host;
- query after response loss and never interpret `UNKNOWN` as success;
- distinguish committed, recovery-required, failed, and unknown outcomes in the UI.

## Verification

Host instrumentation covers complete file and directory moves with nested content and empty directories, restoration with byte equality, partial or reordered proof rejection, changed-output rejection, final source revalidation, unused recovery cleanup, partial-directory deletion recovery, idempotent terminal queries, and protocol catalog compatibility.

Archive Manager instrumentation covers the default-off option, Activity recreation, real archive creation, output-before-Trash ordering, exact target and output proof, the non-cancellable boundary, malformed result rejection, response-loss recovery, split-output order, and per-item output order.

A joint cross-APK instrumentation gate launches the installed Archive Manager Activity through a real host file session, enables the option through the rendered UI, creates a real ZIP, verifies the host moves the source to Trash, restores it through the host repository, and compares its bytes with the original.
