package org.autojs.plugin.terminal.api

/**
 * Frozen vocabulary of the terminal contract: Bundle keys, Intent extras, JSON document fields,
 * closed value sets and the hard ceilings both sides enforce (plugin roadmap D19 / D21 / D22,
 * appendix B). Version 1 ships with plugin 1.0.0. The JSON documents are described in
 * `docs/dev/terminal-plugin-protocol-v1.md`.
 */
object TerminalContract {

    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    // Bundle keys (subscribeOutput, readTranscript).

    /** String: id of the subscription created by `subscribeOutput`, passed back to `unsubscribeOutput`. */
    const val KEY_SUBSCRIPTION_ID = "subscriptionId"

    /** ParcelFileDescriptor: read end of the output pipe, owned by the caller. */
    const val KEY_FD = "fd"

    /** String: transcript text (`readTranscript`). */
    const val KEY_TEXT = "text"

    /** Boolean: older transcript text was cut to honor `maxBytes` (`readTranscript`). */
    const val KEY_TRUNCATED = "truncated"

    /** String: error document `{code, message, sessionId}` when a Bundle-returning method fails. */
    const val KEY_ERROR_JSON = "errorJson"

    // Intent extras of TerminalActions.OPEN_TERMINAL (plugin roadmap D19).

    /** String: directory to start the session in; shared-storage paths need the plugin's all-files grant. */
    const val EXTRA_DIRECTORY = "directory"

    /** String: bring an existing session to the front instead of creating one. */
    const val EXTRA_SESSION_ID = "sessionId"

    /** Boolean: always create a new session, even when one already runs in the directory. */
    const val EXTRA_NEW_SESSION = "newSession"

    /** String: command line executed in the new session after the shell starts (at most [MAX_COMMAND_BYTES]). */
    const val EXTRA_COMMAND = "command"

    /** Boolean: open the session manager instead of a session. */
    const val EXTRA_MANAGER = "manager"

    // Request document fields (`openSession`).

    const val FIELD_CWD = "cwd"
    const val FIELD_COMMAND = "command"
    /** Object of String -> String overrides laid over the session environment, at most [MAX_ENV_ENTRIES]. */
    const val FIELD_ENV = "env"
    const val FIELD_TITLE = "title"
    /** Boolean, default true: keep the interactive shell after `command` finishes. */
    const val FIELD_KEEP_OPEN = "keepOpen"

    // Session document fields (`listSessions`, `openSession`, `onSessionsChanged`).

    const val FIELD_ID = "id"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_ALIVE = "alive"
    const val FIELD_EXIT_CODE = "exitCode"
    const val FIELD_PID = "pid"
    /** One of [STATES]. */
    const val FIELD_STATE = "state"

    // Error document fields.

    const val FIELD_ERROR_CODE = "code"
    const val FIELD_ERROR_MESSAGE = "message"
    const val FIELD_ERROR_SESSION_ID = "sessionId"

    // Subscription options document fields (`subscribeOutput`).

    /** Boolean, default false: push the current transcript before live output. */
    const val FIELD_FROM_START = "fromStart"

    // Environment document fields (`getEnvironment`).

    const val FIELD_HOME = "home"
    const val FIELD_PREFIX = "prefix"
    const val FIELD_SHELL = "shell"
    /** Object `{available, version, packageName, pluginVersion, reason}`. */
    const val FIELD_NODE = "node"
    const val FIELD_NODE_AVAILABLE = "available"
    const val FIELD_NODE_VERSION = "version"
    const val FIELD_NODE_PACKAGE_NAME = "packageName"
    const val FIELD_NODE_PLUGIN_VERSION = "pluginVersion"
    const val FIELD_NODE_REASON = "reason"
    const val FIELD_NPM_REGISTRY = "npmRegistry"
    /** One of [STORAGE_ACCESS_STATES]. */
    const val FIELD_STORAGE_ACCESS = "storageAccess"
    const val FIELD_PLUGIN_VERSION = "pluginVersion"
    const val FIELD_CONTRACT_VERSION = "contractVersion"

    // Closed value sets.

    /** Node.js toolchain is still being prepared for the session (archive extraction). */
    const val STATE_PENDING = "pending"
    const val STATE_RUNNING = "running"
    const val STATE_EXITED = "exited"

    /** Every session state in lifecycle order; `exited` is terminal. */
    val STATES: List<String> = listOf(STATE_PENDING, STATE_RUNNING, STATE_EXITED)

    const val STORAGE_ACCESS_GRANTED = "granted"
    const val STORAGE_ACCESS_DENIED = "denied"
    const val STORAGE_ACCESS_NOT_APPLICABLE = "not_applicable"

    val STORAGE_ACCESS_STATES: List<String> = listOf(STORAGE_ACCESS_GRANTED, STORAGE_ACCESS_DENIED, STORAGE_ACCESS_NOT_APPLICABLE)

    const val NODE_CLI_AVAILABLE = "available"
    /** The user switched the Node.js integration off in the plugin settings. */
    const val NODE_CLI_DISABLED = "disabled"
    const val NODE_CLI_PLUGIN_MISSING = "plugin-missing"
    /** The Node.js Runtime plugin is signed by neither the official key nor the terminal plugin's key. */
    const val NODE_CLI_PLUGIN_UNTRUSTED = "plugin-untrusted"
    /** The Node.js Runtime plugin predates the `NODE_CLI_*` manifest contract. */
    const val NODE_CLI_PLUGIN_TOO_OLD = "plugin-too-old"
    const val NODE_CLI_EXECUTABLE_MISSING = "executable-missing"
    /** The platform refused to execute the launcher from the terminal process. */
    const val NODE_CLI_EXEC_DENIED = "exec-denied"
    /** Archive verification or extraction failed. */
    const val NODE_CLI_SETUP_FAILED = "setup-failed"

    /** Every value of [TerminalCapabilityKeys.NODE_CLI] and of the environment `node.reason` field. */
    val NODE_CLI_STATES: List<String> = listOf(
        NODE_CLI_AVAILABLE,
        NODE_CLI_DISABLED,
        NODE_CLI_PLUGIN_MISSING,
        NODE_CLI_PLUGIN_UNTRUSTED,
        NODE_CLI_PLUGIN_TOO_OLD,
        NODE_CLI_EXECUTABLE_MISSING,
        NODE_CLI_EXEC_DENIED,
        NODE_CLI_SETUP_FAILED,
    )

    // Ceilings (plugin roadmap D22). Both sides reject anything beyond them with INVALID_ARGUMENT.

    const val MAX_SESSIONS = 16
    const val MAX_COMMAND_BYTES = 65_536
    const val MAX_INPUT_BYTES = 65_536
    const val MAX_ENV_ENTRIES = 64
    const val MAX_ENV_ENTRY_BYTES = 4_096
    const val MAX_PATH_BYTES = 4_096
    const val MAX_JSON_BYTES = 65_536
    const val MAX_CALLBACKS = 8
    const val MAX_SUBSCRIPTIONS_PER_SESSION = 4
    const val MAX_TRANSCRIPT_BYTES = 1_048_576
    /** Per-subscription ring buffer; older output is dropped and reported through `onOutputOverflow`. */
    const val OUTPUT_BUFFER_BYTES = 1_048_576

    fun isState(value: String?): Boolean = value != null && value in STATES

    fun isTerminalState(value: String?): Boolean = value == STATE_EXITED

    fun isStorageAccessState(value: String?): Boolean = value != null && value in STORAGE_ACCESS_STATES

    fun isNodeCliState(value: String?): Boolean = value != null && value in NODE_CLI_STATES
}
