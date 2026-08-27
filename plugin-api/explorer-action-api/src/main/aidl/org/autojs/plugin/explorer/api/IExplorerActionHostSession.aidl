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
}
