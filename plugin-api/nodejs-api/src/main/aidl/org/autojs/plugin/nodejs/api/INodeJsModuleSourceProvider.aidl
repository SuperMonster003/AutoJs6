package org.autojs.plugin.nodejs.api;

import android.os.Bundle;

/**
 * Synchronous, request-scoped source provider used after the Node.js runtime
 * has resolved one exact module candidate.
 *
 * Source bytes are returned as a ParcelFileDescriptor stored in the response
 * Bundle so module size is not constrained by Binder's transaction buffer.
 */
interface INodeJsModuleSourceProvider {

    Bundle resolveModuleSource(in Bundle request);

    Bundle getNativeDiagnostics();

    void cancel(String reason);

}
