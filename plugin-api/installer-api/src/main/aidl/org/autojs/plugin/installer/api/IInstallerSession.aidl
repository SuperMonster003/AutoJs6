package org.autojs.plugin.installer.api;

import android.os.Bundle;

/**
 * One installation session returned by IInstallerPlugin.openSession. The session runs on its
 * own; the host observes it through IInstallerSessionCallback and may cancel it at any time.
 */
interface IInstallerSession {
    /** Plugin-side session id, also echoed by every callback. */
    String getId();

    /** Session status as JSON under InstallerContract.KEY_STATUS_JSON (`state`, `stage`, `index`, `progress`). */
    Bundle getStatus();

    /** Requests cancellation; returns false when the session already reached a terminal state. */
    boolean cancel();

    /** Releases the session and its staging files; a running session is cancelled first. */
    void close();
}
