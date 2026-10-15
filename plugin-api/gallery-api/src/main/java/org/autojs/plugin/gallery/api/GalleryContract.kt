package org.autojs.plugin.gallery.api

/**
 * Frozen vocabulary of the gallery contract (version 1): Bundle keys, JSON field names, closed value
 * sets and the hard ceilings both sides enforce. The operations and documents are described in
 * `docs/dev/gallery-plugin-protocol-v1.md`.
 *
 * The host writes the negotiated version (the smaller of its own [MAX_CONTRACT_VERSION] and the
 * plugin's `GalleryCapabilityKeys.MAX_CONTRACT_VERSION`) into every request, and the plugin answers a
 * session or conversion with the version its request carried.
 */
object GalleryContract {

    /** The newest version this vocabulary describes. */
    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    // Bundle keys.

    /** Int: contract version of the writer (every request and answer). */
    const val KEY_CONTRACT_VERSION = "contractVersion"

    /** String: an [GalleryErrorCodes] code; present in a Bundle answer or [EVENT_ERROR] only on failure. */
    const val KEY_ERROR_CODE = "errorCode"

    /** String: human-readable detail of [KEY_ERROR_CODE] (at most [MAX_ERROR_MESSAGE_BYTES]). */
    const val KEY_ERROR_MESSAGE = "errorMessage"

    /** String: one of the EVENT_* names (event bundles). */
    const val KEY_EVENT = "event"

    /** String: one of the REASON_* names ([EVENT_CLOSE]). */
    const val KEY_REASON = "reason"

    /** String: random session token minted by the interactive calls, the only extra of the launch. */
    const val KEY_SESSION_TOKEN = "sessionToken"

    /** String: one of the KIND_* names (`getState`). */
    const val KEY_KIND = "kind"

    /** Boolean: whether the session Activity is resumed (`getState`). */
    const val KEY_VISIBLE = "visible"

    /** Boolean: finish the session Activity when the host closes the session (`close` options; default true). */
    const val KEY_FINISH = "finish"

    /** Boolean: the picker allows choosing several images (default false). */
    const val KEY_MULTIPLE = "multiple"

    /** Int: most images a multiple picker accepts, 1 to [MAX_PICK_COUNT] (default [MAX_PICK_COUNT]). */
    const val KEY_MAX_COUNT = "maxCount"

    /** String array: MIME types (such as `image/gif`) or `image/` prefixes the picker offers; absent means every image. */
    const val KEY_MIME_TYPES = "mimeTypes"

    /** String: title of the picker (at most [MAX_TITLE_LENGTH] characters). */
    const val KEY_TITLE = "title"

    /** String: items of a picker [EVENT_RESULT] as a JSON array of FIELD_* objects. */
    const val KEY_ITEMS = "items"

    /** String array: file names of the viewer sources, one per descriptor. */
    const val KEY_DISPLAY_NAMES = "displayNames"

    /** Int: page the viewer opens at (default 0). */
    const val KEY_START_INDEX = "startIndex"

    /** Int: page of a viewer ([EVENT_PAGE], `getState`). */
    const val KEY_INDEX = "index"

    /** String: file name of the editor or conversion source, as the host knows it. */
    const val KEY_DISPLAY_NAME = "displayName"

    /** String: output format, one of the FORMAT_* names (absent: the source format when writable, else JPEG). */
    const val KEY_FORMAT = "format"

    /** Int: quality of JPEG and lossy WebP output, 1 to 100 (default [DEFAULT_QUALITY]). */
    const val KEY_QUALITY = "quality"

    /** Boolean: lossless WebP (Android 11 and later). */
    const val KEY_LOSSLESS = "lossless"

    /** Long: target file size in bytes for JPEG and lossy WebP; the quality is searched below it. */
    const val KEY_TARGET_BYTES = "targetBytes"

    /** String: one of the SIZE_* names (conversion; default [SIZE_ORIGINAL]). */
    const val KEY_SIZE_MODE = "sizeMode"

    /** Int: scale of [SIZE_PERCENT], [MIN_PERCENT] to [MAX_PERCENT]. */
    const val KEY_PERCENT = "percent"

    /** Int: longest side of [SIZE_LONG_EDGE] in pixels; images already smaller are not enlarged. */
    const val KEY_LONG_EDGE = "longEdge"

    /** Int: width in pixels ([SIZE_CUSTOM] request; result). */
    const val KEY_WIDTH = "width"

    /** Int: height in pixels ([SIZE_CUSTOM] request; result). */
    const val KEY_HEIGHT = "height"

    /** Boolean: [SIZE_CUSTOM] keeps the aspect ratio when only one of width and height is given (default true). */
    const val KEY_KEEP_ASPECT_RATIO = "keepAspectRatio"

    /** Int: ARGB color filling transparent areas of JPEG output (default opaque white). */
    const val KEY_BACKGROUND = "background"

    /** Boolean: keep the safe camera EXIF fields (default false). */
    const val KEY_KEEP_EXIF = "keepExif"

    /** Boolean: keep the GPS fields as well; only honored with [KEY_KEEP_EXIF] (default false). */
    const val KEY_KEEP_LOCATION = "keepLocation"

    /** String: MIME type of a written output (results). */
    const val KEY_MIME_TYPE = "mimeType"

    /** Long: byte size of a written output (results). */
    const val KEY_SIZE = "size"

    // JSON fields of a picker item ([KEY_ITEMS]).

    /** Absolute path the plugin derived for the image; the host keeps only paths the script may read. */
    const val FIELD_PATH = "path"
    const val FIELD_NAME = "name"
    const val FIELD_MIME_TYPE = "mimeType"
    const val FIELD_WIDTH = "width"
    const val FIELD_HEIGHT = "height"
    const val FIELD_SIZE = "size"

    /** Capture time in milliseconds since the epoch, the modification time when the image has none. */
    const val FIELD_DATE_TAKEN = "dateTaken"

    // Closed value sets.

    const val EVENT_OPEN = "open"
    const val EVENT_PAGE = "page"
    const val EVENT_RESULT = "result"
    const val EVENT_ERROR = "error"
    const val EVENT_CLOSE = "close"
    val EVENTS: Set<String> = setOf(EVENT_OPEN, EVENT_PAGE, EVENT_RESULT, EVENT_ERROR, EVENT_CLOSE)

    /** The user left the session (back, close button) without a result. */
    const val REASON_USER = "user"

    /** The host closed the session. */
    const val REASON_HOST = "host"

    /** The session ended after its [EVENT_RESULT]. */
    const val REASON_DONE = "done"

    /** No Activity claimed the session within [SESSION_CLAIM_TIMEOUT_MS]. */
    const val REASON_TIMEOUT = "timeout"

    /** Another interactive session of the same host replaced this one. */
    const val REASON_REPLACED = "replaced"

    /** The session ended after its [EVENT_ERROR]. */
    const val REASON_ERROR = "error"
    val REASONS: Set<String> = setOf(REASON_USER, REASON_HOST, REASON_DONE, REASON_TIMEOUT, REASON_REPLACED, REASON_ERROR)

    const val KIND_PICKER = "picker"
    const val KIND_VIEWER = "viewer"
    const val KIND_EDITOR = "editor"
    val KINDS: Set<String> = setOf(KIND_PICKER, KIND_VIEWER, KIND_EDITOR)

    const val FORMAT_JPEG = "jpeg"
    const val FORMAT_PNG = "png"
    const val FORMAT_WEBP = "webp"
    val FORMATS: Set<String> = setOf(FORMAT_JPEG, FORMAT_PNG, FORMAT_WEBP)

    const val SIZE_ORIGINAL = "original"
    const val SIZE_PERCENT = "percent"
    const val SIZE_LONG_EDGE = "longEdge"
    const val SIZE_CUSTOM = "custom"
    val SIZE_MODES: Set<String> = setOf(SIZE_ORIGINAL, SIZE_PERCENT, SIZE_LONG_EDGE, SIZE_CUSTOM)

    const val FEATURE_PICKER = "picker"
    const val FEATURE_VIEWER = "viewer"
    const val FEATURE_EDITOR = "editor"
    const val FEATURE_CONVERT = "convert"
    val FEATURES: Set<String> = setOf(FEATURE_PICKER, FEATURE_VIEWER, FEATURE_EDITOR, FEATURE_CONVERT)

    // Defaults and hard ceilings.

    const val DEFAULT_QUALITY = 92
    const val MIN_QUALITY = 1
    const val MAX_QUALITY = 100
    const val MIN_PERCENT = 1
    const val MAX_PERCENT = 1000

    /** Longest side of any output, as the plugin's converter allows. */
    const val MAX_DIMENSION = 16384

    /** Most pixels of any output (40 MP), as the plugin's converter allows. */
    const val MAX_PIXELS = 40_000_000L

    /** Images one picker session returns at most. */
    const val MAX_PICK_COUNT = 200

    /** Sources of one viewer session. */
    const val MAX_VIEW_PAGES = 128

    const val MAX_MIME_TYPES = 16
    const val MAX_TITLE_LENGTH = 200
    const val MAX_DISPLAY_NAME_LENGTH = 255

    /** Largest options bundle, measured as the parcel size. */
    const val MAX_OPTIONS_BYTES = 16 * 1024

    /** Largest event bundle, measured as the parcel size (a full picker result fits). */
    const val MAX_EVENT_BYTES = 256 * 1024

    /** Longest [KEY_ERROR_MESSAGE] either side keeps; longer text is truncated. */
    const val MAX_ERROR_MESSAGE_BYTES = 4 * 1024

    /** Time the plugin keeps an unclaimed session before closing it with [REASON_TIMEOUT]. */
    const val SESSION_CLAIM_TIMEOUT_MS = 60_000L

    // Host-side call timeouts.

    /** `getInfo`, `getCapabilities`, opening a session and `getState`. */
    const val CALL_TIMEOUT_MS = 30_000L

    /** One `convert` call (decoding and encoding a large image, a target-size search). */
    const val CONVERT_TIMEOUT_MS = 180_000L
}
