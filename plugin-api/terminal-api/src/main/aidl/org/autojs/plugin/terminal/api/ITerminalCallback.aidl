package org.autojs.plugin.terminal.api;

/**
 * State channel from the plugin to a registered host (ITerminalPlugin.registerCallback). Every
 * method is oneway: the plugin never waits for the host, and a dead host Binder is dropped
 * silently. Transaction codes follow the declaration order; methods may only ever be appended.
 */
oneway interface ITerminalCallback {
    /** The session set changed (opened, exited, closed, pending -> running); `sessionsJson` is the listSessions answer. */
    void onSessionsChanged(int count, String sessionsJson);

    /** The shell of the session exited with `exitCode` (the session may stay listed until closed). */
    void onSessionExited(String sessionId, int exitCode);

    /** The subscription buffer overflowed and `droppedBytes` of output were discarded oldest-first. */
    void onOutputOverflow(String sessionId, String subscriptionId, long droppedBytes);
}
