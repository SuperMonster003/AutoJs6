package org.autojs.plugin.mail.api

/**
 * Frozen vocabulary of mail contract version 1: Bundle keys, JSON envelope fields, operation
 * names, closed value sets and the hard ceilings both sides enforce. The JSON documents carried
 * under the `*_JSON` keys are described in `docs/dev/mail-plugin-protocol-v1.md`.
 */
object MailContract {

    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    // Bundle keys.

    /** Int: contract version of the writer; the host puts it into every bundle it sends. */
    const val KEY_CONTRACT_VERSION = "contractVersion"

    /** String: account JSON without secrets (`openSession`). */
    const val KEY_ACCOUNT_JSON = "accountJson"

    /** String: password or provider authorization code; only ever present in `openSession`. */
    const val KEY_SECRET_PASSWORD = "secretPassword"

    /** String: XOAUTH2 bearer token; only ever present in `openSession`. */
    const val KEY_SECRET_ACCESS_TOKEN = "secretAccessToken"

    /** String: alias of a saved account; exclusive with the three keys above. */
    const val KEY_ACCOUNT_ALIAS = "accountAlias"

    /** String: request envelope `{id, op, args}` (`call`). */
    const val KEY_REQUEST_JSON = "requestJson"

    /** String: response envelope `{id, ok, result}` or `{id, ok, error}` (`onResult`). */
    const val KEY_RESPONSE_JSON = "responseJson"

    /** String: watch event `{type, seq, generation, folder, ...}` (`onEvent`). */
    const val KEY_EVENT_JSON = "eventJson"

    /** String: session or watch status `{state, reason, lastError}` (`getStatus`, `onStatus`). */
    const val KEY_STATUS_JSON = "statusJson"

    /** String: transfer progress `{id, transferred, total}` (`onProgress`). */
    const val KEY_PROGRESS_JSON = "progressJson"

    /** String: watch options `{folder, mode, pollIntervalMs}` (`watch`). */
    const val KEY_WATCH_OPTIONS_JSON = "watchOptionsJson"

    /** String: provider preset table (`listProviders`). */
    const val KEY_PROVIDERS_JSON = "providersJson"

    /** String: saved account list without secrets (`listSavedAccounts`). */
    const val KEY_ACCOUNTS_JSON = "accountsJson"

    /** Long: host `versionCode`, so the plugin can refuse hosts it does not support. */
    const val KEY_HOST_VERSION_CODE = "hostVersionCode"

    /** Long: watch generation chosen by the host and echoed by every event of that watch. */
    const val KEY_GENERATION = "generation"

    // JSON envelope fields.

    const val FIELD_ID = "id"
    const val FIELD_OP = "op"
    const val FIELD_ARGS = "args"
    const val FIELD_OK = "ok"
    const val FIELD_RESULT = "result"
    const val FIELD_ERROR = "error"
    const val FIELD_ERROR_CODE = "code"
    const val FIELD_ERROR_MESSAGE = "message"
    const val FIELD_ERROR_DETAILS = "details"
    const val FIELD_ERROR_RETRYABLE = "retryable"
    const val FIELD_TRANSFERRED = "transferred"
    const val FIELD_TOTAL = "total"
    const val FIELD_TYPE = "type"
    const val FIELD_SEQ = "seq"
    const val FIELD_GENERATION = "generation"
    const val FIELD_FOLDER = "folder"
    const val FIELD_MESSAGE = "message"
    const val FIELD_MODE = "mode"
    const val FIELD_REASON = "reason"
    const val FIELD_STATE = "state"
    const val FIELD_LAST_ERROR = "lastError"
    const val FIELD_POLL_INTERVAL_MS = "pollIntervalMs"

    // Operations.

    const val OP_SESSION_TEST = "session.test"
    const val OP_SESSION_CLOSE = "session.close"
    const val OP_FOLDERS_LIST = "folders.list"
    const val OP_FOLDERS_STATUS = "folders.status"
    const val OP_FOLDERS_CREATE = "folders.create"
    const val OP_FOLDERS_DELETE = "folders.delete"
    const val OP_FOLDERS_RENAME = "folders.rename"
    const val OP_MESSAGES_LIST = "messages.list"
    const val OP_MESSAGES_SEARCH = "messages.search"
    const val OP_MESSAGES_GET = "messages.get"
    const val OP_MESSAGES_RAW = "messages.raw"
    const val OP_MESSAGES_SET_FLAGS = "messages.setFlags"
    const val OP_MESSAGES_MOVE = "messages.move"
    const val OP_MESSAGES_COPY = "messages.copy"
    const val OP_MESSAGES_DELETE = "messages.delete"
    const val OP_MESSAGES_EXPUNGE = "messages.expunge"
    const val OP_MESSAGES_APPEND = "messages.append"
    const val OP_ATTACHMENTS_DOWNLOAD = "attachments.download"
    const val OP_MAIL_SEND = "mail.send"

    /** Every operation of contract version 1, in protocol-document order. */
    val OPS: List<String> = listOf(
        OP_SESSION_TEST,
        OP_FOLDERS_LIST,
        OP_FOLDERS_STATUS,
        OP_FOLDERS_CREATE,
        OP_FOLDERS_DELETE,
        OP_FOLDERS_RENAME,
        OP_MESSAGES_LIST,
        OP_MESSAGES_SEARCH,
        OP_MESSAGES_GET,
        OP_MESSAGES_RAW,
        OP_ATTACHMENTS_DOWNLOAD,
        OP_MESSAGES_SET_FLAGS,
        OP_MESSAGES_MOVE,
        OP_MESSAGES_COPY,
        OP_MESSAGES_DELETE,
        OP_MESSAGES_EXPUNGE,
        OP_MESSAGES_APPEND,
        OP_MAIL_SEND,
        OP_SESSION_CLOSE,
    )

    /** Operations that receive exactly one pipe write end owned by the host (streamed download). */
    val OPS_WITH_SINK: Set<String> = setOf(OP_MESSAGES_RAW, OP_ATTACHMENTS_DOWNLOAD)

    /** Operations that receive zero or more read-only attachment descriptors. */
    val OPS_WITH_SOURCES: Set<String> = setOf(OP_MESSAGES_APPEND, OP_MAIL_SEND)

    /** Operations that only move metadata and use the short host-side timeout. */
    val OPS_METADATA: Set<String> = setOf(
        OP_SESSION_TEST,
        OP_SESSION_CLOSE,
        OP_FOLDERS_LIST,
        OP_FOLDERS_STATUS,
        OP_FOLDERS_CREATE,
        OP_FOLDERS_DELETE,
        OP_FOLDERS_RENAME,
        OP_MESSAGES_LIST,
        OP_MESSAGES_SEARCH,
        OP_MESSAGES_SET_FLAGS,
        OP_MESSAGES_MOVE,
        OP_MESSAGES_COPY,
        OP_MESSAGES_DELETE,
        OP_MESSAGES_EXPUNGE,
    )

    fun isKnownOp(op: String?): Boolean = op != null && op in OPS

    // Closed value sets.

    const val STATE_OPEN = "open"
    const val STATE_CLOSED = "closed"
    val STATES: Set<String> = setOf(STATE_OPEN, STATE_CLOSED)

    const val EVENT_MESSAGE = "message"
    const val EVENT_ERROR = "error"
    const val EVENT_CLOSED = "closed"
    const val EVENT_MODE = "mode"
    const val EVENT_RESYNC = "resync"
    val EVENT_TYPES: Set<String> = setOf(EVENT_MESSAGE, EVENT_ERROR, EVENT_CLOSED, EVENT_MODE, EVENT_RESYNC)

    const val WATCH_MODE_IDLE = "idle"
    const val WATCH_MODE_POLL = "poll"
    val WATCH_MODES: Set<String> = setOf(WATCH_MODE_IDLE, WATCH_MODE_POLL)

    const val PROTOCOL_IMAP = "imap"
    const val PROTOCOL_POP3 = "pop3"
    const val PROTOCOL_SMTP = "smtp"
    val PROTOCOLS: Set<String> = setOf(PROTOCOL_IMAP, PROTOCOL_POP3, PROTOCOL_SMTP)

    const val AUTH_PASSWORD = "password"
    const val AUTH_XOAUTH2 = "xoauth2"
    val AUTH_MECHANISMS: Set<String> = setOf(AUTH_PASSWORD, AUTH_XOAUTH2)

    const val TLS_SSL = "ssl"
    const val TLS_STARTTLS = "starttls"
    const val TLS_NONE = "none"
    val TLS_MODES: Set<String> = setOf(TLS_SSL, TLS_STARTTLS, TLS_NONE)

    const val FEATURE_IDLE = "idle"
    const val FEATURE_SAVED_ACCOUNTS = "savedAccounts"
    const val FEATURE_APPEND = "append"
    const val FEATURE_CLIENT_SEARCH_FALLBACK = "clientSearchFallback"
    const val FEATURE_BACKGROUND_WATCH = "backgroundWatch"
    val FEATURES: Set<String> = setOf(
        FEATURE_IDLE,
        FEATURE_SAVED_ACCOUNTS,
        FEATURE_APPEND,
        FEATURE_CLIENT_SEARCH_FALLBACK,
        FEATURE_BACKGROUND_WATCH,
    )

    // Hard ceilings (roadmap appendix B.5).

    /** Largest JSON document carried by one Binder transaction. */
    const val MAX_ENVELOPE_BYTES = 512 * 1024

    /** Largest inline text plus html body of `messages.get`; larger bodies go through `messages.raw`. */
    const val MAX_INLINE_BODY_BYTES = 256 * 1024

    /** Largest single attachment, downloaded or uploaded. */
    const val MAX_ATTACHMENT_BYTES = 200L * 1024 * 1024

    const val MAX_ATTACHMENTS_PER_MESSAGE = 64

    /** to + cc + bcc. */
    const val MAX_RECIPIENTS = 500

    const val MAX_PAGE_SIZE = 1000
    const val DEFAULT_PAGE_SIZE = 50

    /** Envelopes scanned by the client-side filter fallback of `messages.search`. */
    const val MAX_CLIENT_FILTER = 2000

    /** Descriptors of one `call`. */
    const val MAX_DESCRIPTORS = 64

    const val MAX_CONCURRENT_CALLS = 4
    const val MAX_QUEUED_CALLS = 32
    const val MAX_WATCHES_PER_SESSION = 4

    /** Undelivered watch events before the plugin collapses the queue into one `resync` event. */
    const val MAX_WATCH_QUEUE = 256

    const val MIN_POLL_INTERVAL_MS = 15_000L
    const val DEFAULT_POLL_INTERVAL_MS = 60_000L

    /** IDLE is re-issued before the 29 minute server limit of RFC 2177. */
    const val IDLE_RENEW_MS = 24L * 60 * 1000

    /** Idle time after which the plugin drops the underlying connection. */
    const val SESSION_IDLE_TIMEOUT_MS = 10L * 60 * 1000

    const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
    const val DEFAULT_READ_TIMEOUT_MS = 60_000L

    /** Longest `error.message` either side keeps; longer text is truncated. */
    const val MAX_ERROR_MESSAGE_BYTES = 4 * 1024

    // Host-side call timeouts (roadmap appendix B.5, last row).

    const val METADATA_CALL_TIMEOUT_MS = 30_000L
    const val BODY_CALL_TIMEOUT_MS = 120_000L
    const val TRANSFER_CALL_MIN_TIMEOUT_MS = 5L * 60 * 1000
    const val TRANSFER_CALL_BYTES_PER_SECOND = 64L * 1024

    /**
     * Host-side timeout of one call: metadata operations use [METADATA_CALL_TIMEOUT_MS], body
     * fetches [BODY_CALL_TIMEOUT_MS], and transfers the larger of [TRANSFER_CALL_MIN_TIMEOUT_MS]
     * and the time [transferBytes] takes at [TRANSFER_CALL_BYTES_PER_SECOND]. Unknown sizes use the
     * transfer minimum.
     */
    fun callTimeoutMs(op: String, transferBytes: Long? = null): Long = when (op) {
        in OPS_METADATA -> METADATA_CALL_TIMEOUT_MS
        OP_MESSAGES_GET -> BODY_CALL_TIMEOUT_MS
        else -> {
            val bytes = transferBytes?.takeIf { it > 0 } ?: return TRANSFER_CALL_MIN_TIMEOUT_MS
            val seconds = (bytes + TRANSFER_CALL_BYTES_PER_SECOND - 1) / TRANSFER_CALL_BYTES_PER_SECOND
            maxOf(TRANSFER_CALL_MIN_TIMEOUT_MS, seconds * 1000)
        }
    }
}
