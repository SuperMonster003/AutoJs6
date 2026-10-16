package org.autojs.plugin.tool.api;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * One directory output of an accepted call (docs/dev/plugin-tool-protocol-v1.md, MCP roadmap D53): the host passes it
 * in PluginToolInvokeKeys.OUTPUT_TREES for every directory output the caller wants. Paths are relative, with '/'
 * between segments. The host stages what the plugin writes and moves it into the destination when the call reports
 * onResult; it discards everything when the call fails, times out, is cancelled or the plugin dies. Answers carry
 * PluginToolOutputTreeKeys; after the call ended both methods answer PluginToolOutputTreeErrors.CLOSED.
 * Transactions may only ever be appended (PluginToolAidlOrderTest).
 */
interface IPluginToolOutputTree {

    /** Creates a directory and its parents, so that empty directories appear in the destination too. */
    Bundle createDirectory(String relativePath);

    /**
     * Opens a new file for writing (its parents are created) and returns its descriptor, or null with the reason in
     * answer (PluginToolOutputTreeKeys.ERROR_CODE / ERROR_MESSAGE). expectedBytes is a hint, -1 when unknown. A file that
     * already exists in the destination answers PluginToolOutputTreeErrors.EXISTS unless the caller allowed
     * overwriting; the plugin may skip it and go on. The plugin closes every descriptor before it reports the result.
     */
    ParcelFileDescriptor openFile(String relativePath, long expectedBytes, out Bundle answer);
}
