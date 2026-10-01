package org.autojs.plugin.installer.api

/**
 * Frozen vocabulary of the installer contract: Bundle keys, JSON document fields, closed value
 * sets and the hard ceilings both sides enforce. Version 3 adds negotiated installation options
 * without changing the eleven V1/V2 transactions. Peers advertise minimum and maximum versions;
 * requests use the negotiated
 * version, never the newest constant unconditionally. The JSON
 * documents carried under the `*_JSON` keys are described in
 * `docs/dev/installer-plugin-protocol-v1.md`.
 */
object InstallerContract {

    const val CONTRACT_VERSION = 3
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 3

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    // Bundle keys.

    /** Int: contract version of the writer; the host puts it into every bundle it sends. */
    const val KEY_CONTRACT_VERSION = "contractVersion"

    /** Long: host `versionCode`, so the plugin can refuse hosts it does not support. */
    const val KEY_HOST_VERSION_CODE = "hostVersionCode"

    /** String: request document (`openSession`, `inspect`, `uninstall`, `setDefaultInstaller`, `getUsers`). */
    const val KEY_REQUEST_JSON = "requestJson"

    /** String: result document (`onResult`, `onCompleted`). */
    const val KEY_RESULT_JSON = "resultJson"

    /** String: error document `{code, message, status, systemMessage, packageName, retryable}` (`onError`, `onFailed`). */
    const val KEY_ERROR_JSON = "errorJson"

    /** String: stage or progress detail `{index, packageName, bytesWritten, totalBytes}` (`onStage`, `onProgress`). */
    const val KEY_DETAIL_JSON = "detailJson"

    /** String: authorizer, session or default-installer status document (`getAuthorizerState`, `getStatus`, `getDefaultInstallerState`). */
    const val KEY_STATUS_JSON = "statusJson"

    /** String: id the host assigned to the request, echoed in `onFailed` when `openSession` returns null. */
    const val KEY_HOST_SESSION_ID = "hostSessionId"

    // Request document fields.

    const val FIELD_ID = "id"
    /** Optional Boolean: preserves batch semantics even when the request contains one item. */
    const val FIELD_BATCH = "batch"
    /** Optional String: trusted host entry point, `host` (default) or `script`. */
    const val FIELD_SOURCE_ORIGIN = "sourceOrigin"
    const val FIELD_SOURCES = "sources"
    const val FIELD_OPTIONS = "options"
    const val FIELD_INTERACTION = "interaction"
    const val FIELD_AUTHORIZER = "authorizer"
    const val FIELD_DISPLAY_NAME = "displayName"
    const val FIELD_SIZE = "size"
    const val FIELD_SPLITS = "splits"
    const val FIELD_ITEM = "item"
    const val FIELD_PACKAGE_NAME = "packageName"
    const val FIELD_ALLOW_DOWNGRADE = "allowDowngrade"
    const val FIELD_ALLOW_TEST_ONLY = "allowTestOnly"
    const val FIELD_BYPASS_LOW_TARGET_SDK = "bypassLowTargetSdk"
    /** V3 options: omission preserves the backend's existing defaults; explicit no-ops still need V3. */
    const val FIELD_GRANT_ALL_REQUESTED_PERMISSIONS = "grantAllRequestedPermissions"
    const val FIELD_REQUEST_UPDATE_OWNERSHIP = "requestUpdateOwnership"
    const val FIELD_DEXOPT = "dexopt"
    const val FIELD_INSTALL_REASON = "installReason"
    const val FIELD_PACKAGE_SOURCE = "packageSource"
    const val FIELD_INSTALLER = "installer"
    const val FIELD_USER = "user"
    const val FIELD_DELETE_SOURCE = "deleteSource"
    const val FIELD_CONTINUE_ON_ERROR = "continueOnError"
    const val FIELD_TIMEOUT_MILLIS = "timeoutMillis"
    const val FIELD_KEEP_DATA = "keepData"
    /** V2 setDefaultInstallerV2 only; never sent to the V1 method. */
    const val FIELD_MODE = "mode"

    // Result and status document fields.

    const val FIELD_OK = "ok"
    const val FIELD_RESULTS = "results"
    const val FIELD_ERROR = "error"
    const val FIELD_ERROR_CODE = "code"
    const val FIELD_ERROR_MESSAGE = "message"
    const val FIELD_ERROR_STATUS = "status"
    const val FIELD_ERROR_SYSTEM_MESSAGE = "systemMessage"
    const val FIELD_ERROR_RETRYABLE = "retryable"
    const val FIELD_VERSION_NAME = "versionName"
    const val FIELD_VERSION_CODE = "versionCode"
    const val FIELD_PREVIOUS_VERSION_CODE = "previousVersionCode"
    const val FIELD_DURATION_MILLIS = "durationMillis"
    const val FIELD_SOURCE_DELETED = "sourceDeleted"
    const val FIELD_NOTES = "notes"
    /** Optional V3 observations; accepted dexopt does not prove compilation actually ran. */
    const val FIELD_UPDATE_OWNER = "updateOwner"
    const val FIELD_FILTER = "filter"
    const val FIELD_DEXOPT_STATUS = "status"
    const val FIELD_DEXOPT_MESSAGE = "message"
    const val FIELD_STAGE = "stage"
    const val FIELD_STATE = "state"
    const val FIELD_INDEX = "index"
    const val FIELD_PROGRESS = "progress"
    const val FIELD_BYTES_WRITTEN = "bytesWritten"
    const val FIELD_TOTAL_BYTES = "totalBytes"
    const val FIELD_GRANTED = "granted"
    const val FIELD_AVAILABLE = "available"
    const val FIELD_RUNNING = "running"
    const val FIELD_REASON = "reason"
    const val FIELD_COMPONENT = "component"
    const val FIELD_IS_SELF = "isSelf"
    const val FIELD_METHOD = "method"
    /** Optional Boolean: a local completed-configuration receipt, not an authoritative policy query. */
    const val FIELD_PERSISTENT_CONFIGURED = "persistentConfigured"
    const val FIELD_REQUIRES_CLEAR = "requiresClear"
    const val FIELD_USERS = "users"
    const val FIELD_USER_ID = "id"
    const val FIELD_USER_NAME = "name"
    const val FIELD_USER_PRIMARY = "primary"
    const val FIELD_USER_RUNNING = "running"

    // Package archive info document fields (`inspect`).

    const val FIELD_FORMAT = "format"
    const val FIELD_INSTALLABLE = "installable"
    const val FIELD_LABEL = "label"
    const val FIELD_MIN_SDK = "minSdk"
    const val FIELD_TARGET_SDK = "targetSdk"
    const val FIELD_ICON = "icon"
    const val FIELD_SPLIT_NAME = "name"
    const val FIELD_SPLIT_SELECTED = "selected"
    const val FIELD_SIGNATURE_SCHEMES = "signatureSchemes"
    const val FIELD_INSTALLED = "installed"
    const val FIELD_SIGNER_MATCH = "signerMatch"
    const val FIELD_PROBLEMS = "problems"

    // Closed value sets.

    const val SOURCE_HOST = "host"
    const val SOURCE_SCRIPT = "script"
    /** Plugin-local entry points; external Intent extras never supply these values. */
    const val SOURCE_EXTERNAL = "external"
    const val SOURCE_HOME = "home"

    const val AUTHORIZER_AUTO = "auto"
    const val AUTHORIZER_NONE = "none"
    const val AUTHORIZER_SHIZUKU = "shizuku"
    const val AUTHORIZER_ROOT = "root"
    const val AUTHORIZER_DHIZUKU = "dhizuku"

    /** Authorizers a plugin may implement; `auto` is a request-side choice that never appears here. */
    val AUTHORIZERS: List<String> = listOf(AUTHORIZER_NONE, AUTHORIZER_SHIZUKU, AUTHORIZER_ROOT, AUTHORIZER_DHIZUKU)

    const val INTERACTION_AUTO = "auto"
    const val INTERACTION_DIALOG = "dialog"
    const val INTERACTION_SILENT = "silent"
    /** V2 installation presentation; not an uninstall interaction. */
    const val INTERACTION_NOTIFICATION = "notification"

    val INTERACTIONS: List<String> = listOf(INTERACTION_AUTO, INTERACTION_DIALOG, INTERACTION_SILENT, INTERACTION_NOTIFICATION)

    const val USER_CURRENT = "current"
    const val USER_ALL = "all"

    const val STAGE_PENDING = "pending"
    const val STAGE_PREPARING = "preparing"
    const val STAGE_CONFIRMING = "confirming"
    const val STAGE_WRITING = "writing"
    const val STAGE_COMMITTING = "committing"
    /** V3 only, when a non-none manual dexopt was requested. */
    const val STAGE_OPTIMIZING = "optimizing"
    const val STAGE_COMPLETED = "completed"
    const val STAGE_FAILED = "failed"
    const val STAGE_CANCELLED = "cancelled"

    /** Every stage in the order a session passes through them; the last three are terminal. */
    val STAGES: List<String> = listOf(
        STAGE_PENDING,
        STAGE_PREPARING,
        STAGE_CONFIRMING,
        STAGE_WRITING,
        STAGE_COMMITTING,
        STAGE_OPTIMIZING,
        STAGE_COMPLETED,
        STAGE_FAILED,
        STAGE_CANCELLED,
    )

    val TERMINAL_STAGES: Set<String> = setOf(STAGE_COMPLETED, STAGE_FAILED, STAGE_CANCELLED)

    const val FORMAT_APK = "apk"
    const val FORMAT_APKS = "apks"
    const val FORMAT_XAPK = "xapk"
    const val FORMAT_APKM = "apkm"
    const val FORMAT_APKZ = "apkz"
    const val FORMAT_ZIP = "zip"
    const val FORMAT_AAB = "aab"
    const val FORMAT_UNKNOWN = "unknown"

    val FORMATS: List<String> = listOf(FORMAT_APK, FORMAT_APKS, FORMAT_XAPK, FORMAT_APKM, FORMAT_APKZ, FORMAT_ZIP, FORMAT_AAB, FORMAT_UNKNOWN)

    const val SIGNER_MATCH = "match"
    const val SIGNER_MISMATCH = "mismatch"
    const val SIGNER_UNKNOWN = "unknown"

    const val DEFAULT_METHOD_PREFERRED = "preferred"
    const val DEFAULT_METHOD_PERSISTENT = "persistent"
    const val DEFAULT_METHOD_NONE = "none"

    /** Requested scope; distinct from DEFAULT_METHOD_*, which describes the observed state. */
    const val DEFAULT_MODE_PREFERRED = "preferred"
    const val DEFAULT_MODE_PERSISTENT = "persistent"
    val DEFAULT_MODES: List<String> = listOf(DEFAULT_MODE_PREFERRED, DEFAULT_MODE_PERSISTENT)

    /** Extra manual compilation only; none does not disable Android's own compilation. */
    const val DEXOPT_NONE = "none"
    const val DEXOPT_VERIFY = "verify"
    const val DEXOPT_SPEED_PROFILE = "speed-profile"
    const val DEXOPT_SPEED = "speed"
    val DEXOPT_MODES: List<String> = listOf(DEXOPT_NONE, DEXOPT_VERIFY, DEXOPT_SPEED_PROFILE, DEXOPT_SPEED)
    const val DEXOPT_STATUS_ACCEPTED = "accepted"
    const val DEXOPT_STATUS_FAILED = "failed"
    const val DEXOPT_STATUS_CANCELLED = "cancelled"
    const val DEXOPT_STATUS_TIMEOUT = "timeout"
    const val DEXOPT_STATUS_UNAVAILABLE = "unavailable"
    const val DEXOPT_STATUS_UNKNOWN = "unknown"
    val DEXOPT_STATUSES: List<String> = listOf(DEXOPT_STATUS_ACCEPTED, DEXOPT_STATUS_FAILED,
        DEXOPT_STATUS_CANCELLED, DEXOPT_STATUS_TIMEOUT, DEXOPT_STATUS_UNAVAILABLE, DEXOPT_STATUS_UNKNOWN)

    const val INSTALL_REASON_UNKNOWN = "unknown"
    const val INSTALL_REASON_DEVICE_RESTORE = "device-restore"
    const val INSTALL_REASON_DEVICE_SETUP = "device-setup"
    const val INSTALL_REASON_USER = "user"
    const val INSTALL_REASON_POLICY = "policy"
    val INSTALL_REASONS: List<String> = listOf(INSTALL_REASON_UNKNOWN, INSTALL_REASON_DEVICE_RESTORE,
        INSTALL_REASON_DEVICE_SETUP, INSTALL_REASON_USER, INSTALL_REASON_POLICY)

    const val PACKAGE_SOURCE_UNSPECIFIED = "unspecified"
    const val PACKAGE_SOURCE_STORE = "store"
    const val PACKAGE_SOURCE_LOCAL_FILE = "local-file"
    const val PACKAGE_SOURCE_DOWNLOADED_FILE = "downloaded-file"
    const val PACKAGE_SOURCE_OTHER = "other"
    val PACKAGE_SOURCES: List<String> = listOf(PACKAGE_SOURCE_UNSPECIFIED, PACKAGE_SOURCE_STORE,
        PACKAGE_SOURCE_LOCAL_FILE, PACKAGE_SOURCE_DOWNLOADED_FILE, PACKAGE_SOURCE_OTHER)

    // Ceilings (plugin roadmap D31). Both sides reject anything beyond them with INVALID_ARGUMENT.

    const val MAX_BATCH_SOURCES = 32
    const val MAX_SPLITS_PER_PACKAGE = 64
    const val MAX_JSON_BYTES = 65_536
    const val MAX_INSTALLER_PACKAGE_LENGTH = 255
    const val MAX_DISPLAY_NAME_LENGTH = 255
    const val MAX_ICON_BYTES = 65_536
    const val MAX_CONCURRENT_SESSIONS = 4
    const val DEFAULT_USER_ACTION_TIMEOUT_MILLIS = 300_000L
    const val DEFAULT_SESSION_TIMEOUT_MILLIS = 1_800_000L
    const val MAX_SESSION_TIMEOUT_MILLIS = 7_200_000L

    fun isAuthorizer(value: String?): Boolean = value != null && (value == AUTHORIZER_AUTO || value in AUTHORIZERS)

    fun isInteraction(value: String?): Boolean = value != null && value in INTERACTIONS

    fun isDefaultMode(value: String?): Boolean = value != null && value in DEFAULT_MODES

    fun isDexoptMode(value: String?): Boolean = value != null && value in DEXOPT_MODES

    fun isInstallReason(value: String?): Boolean = value != null && value in INSTALL_REASONS

    fun isPackageSource(value: String?): Boolean = value != null && value in PACKAGE_SOURCES

    fun isStage(value: String?): Boolean = value != null && value in STAGES

    fun isTerminalStage(value: String?): Boolean = value != null && value in TERMINAL_STAGES
}
