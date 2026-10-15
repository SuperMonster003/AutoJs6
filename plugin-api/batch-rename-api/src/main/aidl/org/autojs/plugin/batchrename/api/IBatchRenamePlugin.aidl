package org.autojs.plugin.batchrename.api;

import android.os.Bundle;

parcelable org.autojs.plugin.common.api.PluginInfo;

/**
 * Binder contract of the Batch Rename plugin (docs/dev/batch-rename-plugin-protocol-v1.md).
 *
 * The plugin only computes rename plans; the host enumerates the targets, executes the renames and
 * keeps the undo journal. Both methods are synchronous, stateless and safe to call concurrently from
 * the Binder thread pool. Transactions may only ever be appended (BatchRenameAidlOrderTest).
 */
interface IBatchRenamePlugin {

    /** Identity and capabilities; never null. */
    org.autojs.plugin.common.api.PluginInfo getInfo();

    /**
     * Computes one plan for the request Bundle described by BatchRenameRequestKeys. Never throws for
     * invalid input: the answer carries BatchRenameResultKeys.ERROR_CODE instead. Never null.
     */
    Bundle plan(in Bundle request);
}
