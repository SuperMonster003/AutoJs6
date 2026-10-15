package org.autojs.plugin.ui.preview

/**
 * ui-mode layout preview contract v1 (3-Block Compose roadmap P10.2, decisions D46 - D50).
 *
 * The host (AutoJs6) exports a Binder service bound with [ACTION] and protected by the signature-level
 * `org.autojs.permission.PLUGIN` permission. A client (the 3-Block Compose designer) opens a session,
 * sends the XML text of a `"ui"` mode layout together with the values of its `{{ }}` bindings, and the
 * host inflates it with its real dynamic layout inflater inside a real script runtime. In the
 * [MODE_EMBEDDED] mode (API 30+) the inflated view tree is hosted by a `SurfaceControlViewHost` and
 * shown through the client's `SurfaceView`; in the [MODE_SNAPSHOT] mode the tree is measured, laid out
 * and drawn offscreen and the ARGB_8888 pixels are written to a file descriptor owned by the client.
 *
 * No user code runs: the host's runtime executes the constant script `"ui";` and every layout is applied
 * from Kotlin, so a request never becomes source text. Transactions are frozen in order and only grow by
 * appending; Bundles carry primitives, strings, string arrays, the `SurfacePackage` Parcelable, the
 * `InputTransferToken` Parcelable and the host token Binder. Error codes, limits and the JSON member names
 * of bounds and diagnostics are listed here; the protocol is described in
 * `docs/dev/ui-preview-plugin-protocol-v1.md`.
 */
object UiPreviewContract {

    /**
     * The current contract: v2 appends `runScript` / `stopScript` and the console / error / exit callbacks
     * (interactive preview, 3-Block Compose 1.7.0). `getContractVersion()` and `getContractHash()` keep
     * answering for the frozen v1 base ([BASE_VERSION] / [HASH_V1]) so that v1 clients keep working against a
     * v2 host; a v2 client reads [KEY_CONTRACT_VERSION] and [KEY_CONTRACT_HASH] of `getPreviewInfo()` (the
     * current [VERSION] / [HASH]) and [KEY_SCRIPT_RUN_SUPPORTED] before it uses the v2 transactions.
     */
    const val VERSION = 2
    /** The frozen v1 surface every host answers through `getContractVersion()`. */
    const val BASE_VERSION = 1
    const val ACTION = "org.autojs.plugin.ui.PREVIEW"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val HASH_INPUT_V1 =
        "autojs6.ui.preview.v1|" +
            "getContractVersion()->int|getContractHash()->String|getPreviewInfo()->Bundle|" +
            "createSession(Bundle,IUiPreviewCallback)->Bundle|" +
            "setLayout(String,Bundle,ParcelFileDescriptor)->Bundle|" +
            "resize(String,int,int)->Bundle|release(String)->void|reattach(String)->Bundle|" +
            "setPointerMode(String,int)->Bundle|setHighlight(String,int)->Bundle|" +
            "callback:onBounds(String,Bundle)|onLayoutFailed(String,Bundle)|onSessionLost(String,Bundle)|onTap(String,Bundle)|" +
            "bundle:primitive-surface-package-binder|snapshot:argb8888-rowmajor|bounds:json-inline"
    const val HASH_V1 = "680f41b5459648ff07bd1d3d9684c5279d9398b78f61c0307862f50f8dcac0b6"
    const val HASH_INPUT =
        "autojs6.ui.preview.v2|" +
            "getContractVersion()->int|getContractHash()->String|getPreviewInfo()->Bundle|" +
            "createSession(Bundle,IUiPreviewCallback)->Bundle|" +
            "setLayout(String,Bundle,ParcelFileDescriptor)->Bundle|" +
            "resize(String,int,int)->Bundle|release(String)->void|reattach(String)->Bundle|" +
            "setPointerMode(String,int)->Bundle|setHighlight(String,int)->Bundle|" +
            "runScript(String,Bundle)->Bundle|stopScript(String)->Bundle|" +
            "callback:onBounds(String,Bundle)|onLayoutFailed(String,Bundle)|onSessionLost(String,Bundle)|onTap(String,Bundle)|" +
            "onConsole(String,Bundle)|onScriptError(String,Bundle)|onScriptExit(String,Bundle)|" +
            "bundle:primitive-surface-package-binder|snapshot:argb8888-rowmajor|bounds:json-inline|script:text-inline"
    const val HASH = "81f7fe8d2338eed197dcc21e3cff7007a681ea89d080b42d1ae132c94688f1bb"

    // -------------------------------------------------------------------------------------------- modes

    const val MODE_EMBEDDED = "embedded"
    const val MODE_SNAPSHOT = "snapshot"

    /** The host root consumes pointer input, reports taps through `onTap` and draws the highlight. */
    const val POINTER_MODE_EDIT = 0

    /** Pointer input reaches the inflated views; popups of the embedded tree crash the host below API 33. */
    const val POINTER_MODE_INTERACTIVE = 1

    /** `SurfaceControlViewHost` (D50 level 1). */
    const val MIN_API_EMBEDDED = 30

    /** Popup windows anchored in the embedded tree (D50 level 2). */
    const val MIN_API_INTERACTIVE = 33

    /** Soft keyboard inside the embedded tree, which needs an `InputTransferToken` (D50 level 3). */
    const val MIN_API_IME = 35

    // ----------------------------------------------------------------------------- createSession request

    const val KEY_CONTRACT_VERSION = "contractVersion"
    const val KEY_MODE = "mode"
    /** `SurfaceView.getHostToken()` (Binder, embedded mode, API 30 - 34). */
    const val KEY_HOST_TOKEN = "hostToken"
    /** `SurfaceView.getRootSurfaceControl().getInputTransferToken()` (Parcelable, embedded mode, API 35+). */
    const val KEY_INPUT_TRANSFER_TOKEN = "inputTransferToken"
    const val KEY_DISPLAY_ID = "displayId"
    const val KEY_WIDTH_PX = "widthPx"
    const val KEY_HEIGHT_PX = "heightPx"
    /** 0 follows the host. */
    const val KEY_DENSITY_DPI = "densityDpi"
    /** [NIGHT_MODE_FOLLOW_HOST], [NIGHT_MODE_NO] or [NIGHT_MODE_YES]. */
    const val KEY_NIGHT_MODE = "nightMode"
    /** A BCP 47 tag; empty follows the host. */
    const val KEY_LOCALE_TAG = "localeTag"
    /** 0 follows the host. */
    const val KEY_FONT_SCALE = "fontScale"
    /** The client's versionCode, for the host log only. */
    const val KEY_CLIENT_VERSION_CODE = "clientVersionCode"
    /** Shown as the name of the preview runtime in the host's running task list. */
    const val KEY_LABEL = "label"

    const val NIGHT_MODE_FOLLOW_HOST = 0
    const val NIGHT_MODE_NO = 1
    const val NIGHT_MODE_YES = 2

    // ----------------------------------------------------------------------------------- setLayout request

    /** The layout XML with every E4X `{expr}` already replaced by the client. */
    const val KEY_XML = "xml"
    /** A JSON object: the `{{ }}` binding context, values are JSON primitives, objects or arrays. */
    const val KEY_BINDINGS_JSON = "bindingsJson"
    /** Optional lowercase SHA-256 of [KEY_XML]; a mismatch is rejected as [ERROR_INVALID_REQUEST]. */
    const val KEY_XML_SHA256 = "xmlSha256"

    // ------------------------------------------------------------------------------------------ responses

    const val KEY_SESSION_ID = "sessionId"
    /** `SurfaceControlViewHost.SurfacePackage` (Parcelable), embedded mode. */
    const val KEY_SURFACE_PACKAGE = "surfacePackage"
    const val KEY_OUTCOME = "outcome"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    /** Pre-order index of the element (elements only, root = 0); -1 when unknown. */
    const val KEY_ELEMENT_INDEX = "elementIndex"
    const val KEY_ATTR_NAME = "attrName"
    /** 1-based position of an XML syntax error; -1 when unknown. */
    const val KEY_LINE = "line"
    const val KEY_COLUMN = "column"
    const val KEY_ELAPSED_MS = "elapsedMs"
    /** Number of elements in the converted layout document. */
    const val KEY_ELEMENT_COUNT = "elementCount"
    /** A JSON array of property-level diagnostics that did not stop the layout (see [Diagnostic]). */
    const val KEY_DIAGNOSTICS_JSON = "diagnosticsJson"
    const val KEY_REASON = "reason"
    const val KEY_POINTER_MODE = "pointerMode"

    const val KEY_BOUNDS_JSON = "boundsJson"
    const val KEY_BOUNDS_COUNT = "boundsCount"
    const val KEY_BOUNDS_TRUNCATED = "boundsTruncated"

    const val KEY_SNAPSHOT_BYTE_LENGTH = "snapshotByteLength"
    const val KEY_SNAPSHOT_SHA256 = "snapshotSha256"
    const val KEY_SNAPSHOT_FORMAT = "snapshotFormat"
    const val KEY_SNAPSHOT_WIDTH_PX = "snapshotWidthPx"
    const val KEY_SNAPSHOT_HEIGHT_PX = "snapshotHeightPx"

    const val KEY_X = "x"
    const val KEY_Y = "y"
    const val KEY_CLASS_NAME = "className"

    // ---------------------------------------------------------------------------------------- preview info

    const val KEY_CONTRACT_HASH = "contractHash"
    const val KEY_HOST_VERSION_CODE = "hostVersionCode"
    const val KEY_HOST_VERSION_NAME = "hostVersionName"
    const val KEY_STATUS = "status"
    /** String array of the modes this device supports. */
    const val KEY_MODES = "modes"
    const val KEY_INTERACTIVE_POINTER_SUPPORTED = "interactivePointerSupported"
    const val KEY_IME_SUPPORTED = "imeSupported"
    const val KEY_MAX_SESSIONS_PER_CLIENT = "maxSessionsPerClient"
    const val KEY_MAX_WIDTH_PX = "maxWidthPx"
    const val KEY_MAX_HEIGHT_PX = "maxHeightPx"
    const val KEY_MAX_XML_CHARS = "maxXmlChars"
    const val KEY_MAX_BINDINGS_CHARS = "maxBindingsChars"
    const val KEY_MAX_BOUNDS_ENTRIES = "maxBoundsEntries"
    const val KEY_MAX_SNAPSHOT_PIXELS = "maxSnapshotPixels"
    const val KEY_LAYOUT_TIMEOUT_MS = "layoutTimeoutMs"

    const val STATUS_READY = "ready"
    const val STATUS_UNAVAILABLE = "unavailable"

    const val SNAPSHOT_FORMAT_ARGB8888_ROW_MAJOR = "argb8888-rowmajor"
    const val SNAPSHOT_BYTES_PER_PIXEL = 4

    // ------------------------------------------------------------------------------------------- outcomes

    const val OUTCOME_APPLIED = "applied"
    const val OUTCOME_FAILED = "failed"
    const val OUTCOME_TIMED_OUT = "timed_out"

    const val REASON_STOPPED = "stopped"
    const val REASON_TIMEOUT = "timeout"
    const val REASON_SERVICE_DESTROYED = "service_destroyed"
    const val REASON_SCRIPT_ERROR = "script_error"

    // ----------------------------------------------------------------------------------------- error codes

    const val ERROR_INVALID_REQUEST = "ERR_AUTOJS6_UI_PREVIEW_INVALID_REQUEST"
    const val ERROR_CONTRACT_MISMATCH = "ERR_AUTOJS6_UI_PREVIEW_CONTRACT_MISMATCH"
    /** The embedded mode below API 30, or the interactive pointer mode below API 33. */
    const val ERROR_UNSUPPORTED_MODE = "ERR_AUTOJS6_UI_PREVIEW_UNSUPPORTED_MODE"
    const val ERROR_SESSION_LIMIT = "ERR_AUTOJS6_UI_PREVIEW_SESSION_LIMIT"
    const val ERROR_SESSION_NOT_FOUND = "ERR_AUTOJS6_UI_PREVIEW_SESSION_NOT_FOUND"
    /** Malformed XML: [KEY_LINE] / [KEY_COLUMN] and the index of the last element opened before the error. */
    const val ERROR_XML_INVALID = "ERR_AUTOJS6_UI_PREVIEW_XML_INVALID"
    /** Inflation stopped at [KEY_ELEMENT_INDEX], while applying [KEY_ATTR_NAME] when present. */
    const val ERROR_INFLATE_FAILED = "ERR_AUTOJS6_UI_PREVIEW_INFLATE_FAILED"
    /** A `{{ }}` expression failed; the raw text was applied instead and the layout continued. */
    const val ERROR_BINDING_FAILED = "ERR_AUTOJS6_UI_PREVIEW_BINDING_FAILED"
    const val ERROR_TIMEOUT = "ERR_AUTOJS6_UI_PREVIEW_TIMEOUT"
    const val ERROR_OUTPUT_TRANSPORT = "ERR_AUTOJS6_UI_PREVIEW_OUTPUT_TRANSPORT"
    const val ERROR_UNAVAILABLE = "ERR_AUTOJS6_UI_PREVIEW_UNAVAILABLE"

    // ---------------------------------------------------------------------------------------------- limits

    const val MAX_SESSIONS_PER_CLIENT = 2
    const val MIN_SIZE_PX = 1
    const val MAX_WIDTH_PX = 4096
    const val MAX_HEIGHT_PX = 4096
    /** UTF-16 chars; XML plus bindings of one layout request stay within three quarters of the 1 MiB Binder buffer. */
    const val MAX_XML_CHARS = 256 * 1024
    const val MAX_BINDINGS_CHARS = 64 * 1024
    /** Bounds beyond this many entries are dropped and flagged with [KEY_BOUNDS_TRUNCATED]. */
    const val MAX_BOUNDS_ENTRIES = 5_000
    /** 64 MiB of raw ARGB_8888 pixels. */
    const val MAX_SNAPSHOT_PIXELS = 4096L * 4096L
    /** Wall clock of one setLayout, measured on the Binder thread; the session is released afterwards. */
    const val LAYOUT_TIMEOUT_MS = 5_000L
    /** Covers the creation of the preview runtime, which includes a script engine. */
    const val SESSION_CREATE_TIMEOUT_MS = 15_000L
    const val MAX_LABEL_CHARS = 128
    const val MAX_LOCALE_TAG_CHARS = 35
    const val MAX_ERROR_MESSAGE_CHARS = 4_096
    const val MIN_DENSITY_DPI = 72
    const val MAX_DENSITY_DPI = 1_200
    const val MIN_FONT_SCALE = 0.5f
    const val MAX_FONT_SCALE = 3.0f

    // ------------------------------------------------------------------- v2: interactive preview (1.7.0)

    /** `getPreviewInfo()`: true when `runScript` is available (embedded sessions on API 33+). */
    const val KEY_SCRIPT_RUN_SUPPORTED = "scriptRunSupported"
    /** `runScript` request: the whole `"ui"` mode script text. */
    const val KEY_SCRIPT_TEXT = "scriptText"
    /** `runScript` request: the name shown in the host's running list and in error messages (<= [MAX_LABEL_CHARS]). */
    const val KEY_SCRIPT_NAME = "scriptName"
    /** `runScript` response and the v2 callbacks: identifies one run of the session. */
    const val KEY_RUN_ID = "runId"
    /** `onConsole`: an `android.util.Log` priority (2 verbose, 3 debug, 4 info, 5 warn, 6 error, 7 assert). */
    const val KEY_LEVEL = "level"
    /** `onConsole`: the entry text. */
    const val KEY_TEXT = "text"

    /** The script called `exit()` or its runtime ended on its own. */
    const val EXIT_SCRIPT = "exit"
    /** `stopScript`, or the host stopped the run from its task list. */
    const val EXIT_STOPPED = "stopped"
    /** An uncaught error ended the run; `onScriptError` came first. */
    const val EXIT_ERROR = "error"
    /** A new `runScript` of the same session replaced the run. */
    const val EXIT_REPLACED = "replaced"
    /** The session was released while the script ran. */
    const val EXIT_RELEASED = "released"

    /** The script could not start (not a `"ui"` mode script, engine creation failed); the message says why. */
    const val ERROR_SCRIPT_FAILED = "ERR_AUTOJS6_UI_PREVIEW_SCRIPT_FAILED"

    /** UTF-16 chars of one `runScript` text. */
    const val MAX_SCRIPT_CHARS = 512 * 1024
    /** Covers the creation of the script's engine and the start of its body. */
    const val SCRIPT_START_TIMEOUT_MS = 15_000L

    val MODES
        get() = arrayOf(MODE_EMBEDDED, MODE_SNAPSHOT)

    val EXIT_REASONS
        get() = arrayOf(EXIT_SCRIPT, EXIT_STOPPED, EXIT_ERROR, EXIT_REPLACED, EXIT_RELEASED)

    val TERMINAL_OUTCOMES
        get() = arrayOf(OUTCOME_APPLIED, OUTCOME_FAILED, OUTCOME_TIMED_OUT)

    /**
     * Members of one bounds entry: `[{ "i": 0, "l": 0, "t": 0, "r": 1080, "b": 1920, "c": "JsLinearLayout",
     * "d": "root", "n": 0, "s": 0 }, ...]`, sorted by element index then instance. Coordinates are
     * pixels in the embedded root, which the client's SurfaceView shows one to one.
     */
    object Bounds {
        const val ELEMENT_INDEX = "i"
        const val LEFT = "l"
        const val TOP = "t"
        const val RIGHT = "r"
        const val BOTTOM = "b"
        const val CLASS_NAME = "c"
        /** The element's `id` attribute without the `@+id/` prefix; absent without an id. */
        const val ID = "d"
        /** 0 for the first view of an element; list and grid item instances of a template count up. */
        const val INSTANCE = "n"
        /** `View.getVisibility()`: 0 visible, 4 invisible, 8 gone. */
        const val VISIBILITY = "s"
    }

    /** Members of one diagnostic: `{ "code": "...", "message": "...", "i": 3, "a": "text" }`. */
    object Diagnostic {
        const val CODE = "code"
        const val MESSAGE = "message"
        const val ELEMENT_INDEX = "i"
        const val ATTR_NAME = "a"
    }
}
