package org.autojs.plugin.terminal.api;

import android.os.Bundle;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.terminal.api.ITerminalCallback;

/**
 * Entry point of a terminal plugin (action TerminalActions.SERVICE_ACTION, category
 * TerminalActions.SERVICE_CATEGORY). Transaction codes follow the declaration order, so methods
 * may only ever be appended, never inserted, removed or renamed. Contract version 1 declares the
 * thirteen methods below. Every method answers immediately (no I/O wait on the Binder thread):
 * session output travels through the pipe handed out by subscribeOutput, state changes arrive
 * through ITerminalCallback. JSON documents are described in docs/dev/terminal-plugin-protocol-v1.md
 * and never exceed TerminalContract.MAX_JSON_BYTES.
 */
interface ITerminalPlugin {
    /** Identity, version and capabilities of the plugin; identical to the INFO service answer, never null. */
    PluginInfo getInfo();

    /** Live capability bundle keyed by TerminalCapabilityKeys; never null for contract version 1. */
    Bundle getCapabilities();

    /** JSON array of session documents (TerminalContract.FIELD_ID, FIELD_CWD, FIELD_TITLE, ...), possibly empty. */
    String listSessions();

    /**
     * Opens a shell session from the request document (`cwd`, optional `command`, `env`, `title`,
     * `keepOpen`). Returns the session document, or an error document (`code`, `message`) when the
     * request is refused (INVALID_ARGUMENT, SESSION_LIMIT, DIRECTORY_INACCESSIBLE,
     * STORAGE_PERMISSION_REQUIRED, PTY_FAILED, NODE_UNAVAILABLE). A session whose Node.js toolchain
     * is still being prepared is returned in the `pending` state and announced through
     * onSessionsChanged once it runs.
     */
    String openSession(String requestJson);

    /** Ends the session (SIGHUP, then SIGKILL after the grace period); false when the id is unknown. */
    boolean closeSession(String sessionId);

    /** Ends every session; returns how many were closed. */
    int closeAllSessions();

    /**
     * Writes raw bytes to the pty of the session, at most TerminalContract.MAX_INPUT_BYTES per call.
     * Unknown ids and exited sessions are ignored; the host validates both through listSessions.
     */
    void writeInput(String sessionId, in byte[] data);

    /**
     * Starts streaming the raw pty output of the session into a pipe. The bundle carries
     * TerminalContract.KEY_SUBSCRIPTION_ID and the read end under KEY_FD (owned by the caller), or
     * KEY_ERROR_JSON (SESSION_NOT_FOUND, SESSION_CLOSED, SUBSCRIPTION_LIMIT). The options document
     * may set `fromStart` to receive the current transcript first. Output beyond the per-subscription
     * buffer is dropped oldest-first and reported through onOutputOverflow.
     */
    Bundle subscribeOutput(String sessionId, String optionsJson);

    /** Closes the write end of a subscription; unknown ids are ignored. */
    void unsubscribeOutput(String subscriptionId);

    /**
     * Current screen plus scrollback of the session as text under TerminalContract.KEY_TEXT, at most
     * `maxBytes` (capped at MAX_TRANSCRIPT_BYTES); KEY_TRUNCATED tells whether older text was cut.
     * KEY_ERROR_JSON replaces both for an unknown session.
     */
    Bundle readTranscript(String sessionId, int maxBytes);

    /** Environment document: `home`, `prefix`, `shell`, `node`, `npmRegistry`, `storageAccess`, `pluginVersion`, `contractVersion`. */
    String getEnvironment();

    /** Registers a state listener (at most TerminalContract.MAX_CALLBACKS); removed automatically when its Binder dies. */
    void registerCallback(ITerminalCallback callback);

    /** Removes a listener registered with registerCallback; unknown listeners are ignored. */
    void unregisterCallback(ITerminalCallback callback);
}
