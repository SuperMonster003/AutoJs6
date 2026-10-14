package org.autojs.plugin.explorer.api;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.util.List;

/**
 * Short-lived, least-privilege host session attached to an Explorer Action v4 request.
 * Implementations must bind every call to the plugin UID that received the request.
 */
interface IExplorerActionHostSession {

    Bundle listChildren(String targetId, String relativePath, int offset, int limit);

    ParcelFileDescriptor openFile(String targetId, String relativePath);

    Bundle prepareOutput(String displayName, String mimeType, int conflictPolicy);

    ParcelFileDescriptor openOutput(String transactionId);

    Bundle commitOutput(String transactionId);

    void abortOutput(String transactionId);

    void close();

    /**
     * v7. Opens one read-only descriptor for a staged output after its writer is closed and before
     * commit. Appended after all v4 methods so their Binder transaction codes remain stable.
     */
    ParcelFileDescriptor openPendingOutput(String transactionId);

    /**
     * v8. Reserves a host-owned replacement transaction for one original request target. Existing
     * output open, verify, commit and abort methods drive the rest of the transaction.
     */
    Bundle prepareTargetReplacement(String targetId);

    /**
     * v9. Atomically reserves a new sibling directory name and creates a host-owned output tree.
     * Child paths are written only through the methods below; existing sibling content is never
     * exposed or reused.
     */
    Bundle prepareOutputTree(String displayName, int conflictPolicy);

    /** v9. Creates one new directory below a prepared output tree. */
    void createOutputDirectory(String transactionId, String relativePath);

    /** v9. Creates and opens one new file below a prepared output tree. */
    ParcelFileDescriptor openOutputFile(String transactionId, String relativePath);

    /** v9. Returns the current or terminal state of one output transaction. */
    Bundle queryOutput(String transactionId);

    /** v9. Lists bounded active and recently completed output transactions for recovery. */
    Bundle listOutputs();

    /** v9. Attaches a plugin-process lifetime token so abandoned outputs can be rolled back. */
    void attachClient(IBinder clientToken);

    /**
     * v15. Durably groups verified, create-only file outputs before any final name is published.
     * Every transaction must still be pending in this session and may belong to only one batch.
     */
    Bundle prepareOutputBatch(in List<String> transactionIds);

    /**
     * v15. Publishes every member of a prepared batch, or rolls the whole batch back. Recovery
     * after host-process death also rolls back an interrupted publication before exposing state.
     */
    Bundle commitOutputBatch(String batchId);

    /** v15. Aborts every still-owned member of a prepared or interrupted output batch. */
    void abortOutputBatch(String batchId);

    /** v15. Returns the current or terminal state of one output batch. */
    Bundle queryOutputBatch(String batchId);

    /** v15. Lists bounded active and recently completed output batches for recovery. */
    Bundle listOutputBatches();

    /**
     * v16. Moves the complete original request selection into the host Trash after every supplied
     * create-only output has reached the committed state. The host revalidates both source and
     * output identities and never grants arbitrary path deletion to the plugin.
     * v24. For a read-only action that declared `targetTrash`, the output list must be empty and
     * the target list may name any not yet moved subset of the request targets in request order;
     * every such call records its own batch.
     */
    Bundle moveTargetsToTrash(in List<String> targetIds, in List<String> outputTransactionIds);

    /**
     * v16. Returns the terminal result of this session's single source-to-Trash operation.
     * v24. Returns the result of the most recent call for a `targetTrash` action.
     */
    Bundle queryTargetTrash();

    /**
     * v12 optional extension. Returns host-owned playback progress for the target or a permitted
     * direct sibling. The result always reports whether host history is enabled.
     */
    Bundle getPlaybackProgress(String targetId, String relativePath);

    /**
     * v12 optional extension. Stores or clears host-owned playback progress for the target or a
     * permitted direct sibling. Hosts with history disabled ignore reports.
     */
    void reportPlaybackProgress(
        String targetId,
        String relativePath,
        long positionMillis,
        long durationMillis,
        int reportState
    );

    /**
     * v18. Returns the latest host-owned replacement history for one original request target.
     * The plugin receives only an opaque history ID and bounded metadata, never a backup path.
     */
    Bundle queryTargetReplacement(String targetId);

    /**
     * v18. Restores the exact previous version only while the current target still matches the
     * replacement committed by this history record. The history ID is single-use.
     */
    Bundle undoTargetReplacement(String targetId, String replacementHistoryId);

    /**
     * v21. Returns one host-owned source-to-Trash batch scoped to this plugin and parent.
     * The result contains only an opaque batch ID and bounded per-source facts.
     */
    Bundle queryTargetTrashBatch(String targetTrashBatchId);

    /** v21. Lists bounded recent source-to-Trash batches scoped to this plugin and parent. */
    Bundle listTargetTrashBatches();

    /**
     * v21. Restores every still-available source in one batch to its original name. Restoration is
     * best-effort per source; conflicts and recovery-required items remain in host Trash.
     */
    Bundle undoTargetTrashBatch(String targetTrashBatchId);

    /**
     * v22. Opens a frozen, read-only related-file candidate whose exact name was derived by the
     * host as `<target display name><declared suffix>`. Returns null when that candidate did not
     * exist at session creation. Appended to preserve every v1-v21 Binder transaction code.
     */
    ParcelFileDescriptor openRelatedFile(String targetId, String suffix);
}
