package org.autojs.plugin.epub.api

/**
 * Frozen vocabulary of EPUB contract version 1: Bundle keys, JSON field names, closed value
 * sets and the hard ceilings both sides enforce. The JSON documents carried under the
 * [KEY_METADATA], [KEY_TOC], [KEY_READING_ORDER], [KEY_RESULTS], [KEY_BOOKMARKS], [KEY_LOCATOR]
 * and [KEY_PREFERENCES] keys are described in `docs/dev/epub-plugin-protocol-v1.md`.
 */
object EpubContract {

    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    // Bundle keys (roadmap appendix B.1).

    /** Int: contract version of the writer; the host puts it into every bundle it sends. */
    const val KEY_CONTRACT_VERSION = "contractVersion"

    /** String: optional caller-chosen id echoed back in the answer of the same call. */
    const val KEY_REQUEST_ID = "requestId"

    /** String: file name of the book as the host knows it (`openBook`, `openReader` options). */
    const val KEY_DISPLAY_NAME = "displayName"

    /** String: href of a publication resource (requests, state, events). */
    const val KEY_HREF = "href"

    /** Int: reading-order index of a resource (requests, state, events). */
    const val KEY_INDEX = "index"

    /** Int: character offset of a `getText` chunk or result offset of a `search` page. */
    const val KEY_OFFSET = "offset"

    /** Int: number of entries of one `search` page (at most [MAX_SEARCH_RESULTS]). */
    const val KEY_LIMIT = "limit"

    /** Int: number of characters of one `getText` chunk (at most [MAX_TEXT_CHARS_PER_CALL]). */
    const val KEY_MAX_CHARS = "maxChars"

    /** String: [FORMAT_TEXT] or [FORMAT_MARKDOWN] (`getText` request). */
    const val KEY_FORMAT = "format"

    /** String: search query of 1 to [MAX_QUERY_LENGTH] characters. */
    const val KEY_QUERY = "query"

    /** String: Readium locator as JSON (requests, state, events; at most [MAX_LOCATOR_BYTES]). */
    const val KEY_LOCATOR = "locator"

    /** Double: total progression in the publication, 0.0 to 1.0. */
    const val KEY_PROGRESSION = "progression"

    /** String: preferences as JSON (`openReader` options, `setPreferences`; at most [MAX_PREFERENCES_BYTES]). */
    const val KEY_PREFERENCES = "preferences"

    /** String: an [EpubErrorCodes] code; present in a Bundle answer only when the call failed. */
    const val KEY_ERROR_CODE = "errorCode"

    /** String: human-readable detail of [KEY_ERROR_CODE] (at most [MAX_ERROR_MESSAGE_BYTES]). */
    const val KEY_ERROR_MESSAGE = "errorMessage"

    /** String: one of the EVENT_* names (event bundles). */
    const val KEY_EVENT = "event"

    /** String: one of the REASON_* names ([EVENT_CLOSE]). */
    const val KEY_REASON = "reason"

    /** String: [CHANGE_ADDED] or [CHANGE_REMOVED] ([EVENT_BOOKMARK]). */
    const val KEY_CHANGE = "change"

    /** String: random session token minted by `openReader`, the only extra of the reader launch. */
    const val KEY_SESSION_TOKEN = "sessionToken"

    /** Boolean: whether the reader Activity of the session is resumed (`getState`). */
    const val KEY_VISIBLE = "visible"

    /** String: title of the current resource (state, events). */
    const val KEY_TITLE = "title"

    /** String: text chunk of `getText`. */
    const val KEY_TEXT = "text"

    /** Boolean: whether more text or results follow the returned chunk or page. */
    const val KEY_HAS_MORE = "hasMore"

    /** Int: number of synthetic page positions (`getPositions`). */
    const val KEY_POSITIONS = "positions"

    /** Boolean: finish the reader Activity when closing a session (`close` options; default false). */
    const val KEY_FINISH = "finish"

    /** String: metadata document as JSON (`getMetadata`). */
    const val KEY_METADATA = "metadata"

    /** String: flattened table of contents as a JSON array (`getToc`). */
    const val KEY_TOC = "toc"

    /** String: reading order as a JSON array (`getReadingOrder`). */
    const val KEY_READING_ORDER = "readingOrder"

    /** String: search results as a JSON array (`search`). */
    const val KEY_RESULTS = "results"

    /** String: bookmarks as a JSON array (`getBookmarks`). */
    const val KEY_BOOKMARKS = "bookmarks"

    // JSON field names of the documents carried by the keys above (roadmap appendix A).

    const val FIELD_HREF = "href"
    const val FIELD_TYPE = "type"
    const val FIELD_TITLE = "title"
    const val FIELD_LOCATIONS = "locations"
    const val FIELD_PROGRESSION = "progression"
    const val FIELD_TOTAL_PROGRESSION = "totalProgression"
    const val FIELD_POSITION = "position"
    const val FIELD_CSS_SELECTOR = "cssSelector"
    const val FIELD_FRAGMENTS = "fragments"
    const val FIELD_TEXT = "text"
    const val FIELD_BEFORE = "before"
    const val FIELD_HIGHLIGHT = "highlight"
    const val FIELD_AFTER = "after"
    const val FIELD_AUTHORS = "authors"
    const val FIELD_LANGUAGE = "language"
    const val FIELD_IDENTIFIER = "identifier"
    const val FIELD_PUBLISHER = "publisher"
    const val FIELD_PUBLISHED = "published"
    const val FIELD_MODIFIED = "modified"
    const val FIELD_DESCRIPTION = "description"
    const val FIELD_SUBJECTS = "subjects"
    const val FIELD_LAYOUT = "layout"
    const val FIELD_READING_PROGRESSION = "readingProgression"
    const val FIELD_POSITIONS = "positions"
    const val FIELD_COVER = "cover"
    const val FIELD_DEPTH = "depth"
    const val FIELD_LOCATOR = "locator"
    const val FIELD_CREATED_AT = "createdAt"

    // Closed value sets.

    const val EVENT_OPEN = "open"
    const val EVENT_PROGRESS = "progress"
    const val EVENT_BOOKMARK = "bookmark"
    const val EVENT_CLOSE = "close"
    const val EVENT_ERROR = "error"
    val EVENTS: Set<String> = setOf(EVENT_OPEN, EVENT_PROGRESS, EVENT_BOOKMARK, EVENT_CLOSE, EVENT_ERROR)

    const val REASON_USER = "user"
    const val REASON_HOST = "host"
    const val REASON_REPLACED = "replaced"
    const val REASON_TIMEOUT = "timeout"
    const val REASON_ERROR = "error"
    val REASONS: Set<String> = setOf(REASON_USER, REASON_HOST, REASON_REPLACED, REASON_TIMEOUT, REASON_ERROR)

    const val CHANGE_ADDED = "added"
    const val CHANGE_REMOVED = "removed"
    val CHANGES: Set<String> = setOf(CHANGE_ADDED, CHANGE_REMOVED)

    const val DIRECTION_NEXT_PAGE = 1
    const val DIRECTION_PREVIOUS_PAGE = 2
    const val DIRECTION_NEXT_CHAPTER = 3
    const val DIRECTION_PREVIOUS_CHAPTER = 4
    val DIRECTIONS: Set<Int> = setOf(DIRECTION_NEXT_PAGE, DIRECTION_PREVIOUS_PAGE, DIRECTION_NEXT_CHAPTER, DIRECTION_PREVIOUS_CHAPTER)

    fun isKnownDirection(direction: Int): Boolean = direction in DIRECTIONS

    const val FORMAT_TEXT = "text"
    const val FORMAT_MARKDOWN = "markdown"
    val FORMATS: Set<String> = setOf(FORMAT_TEXT, FORMAT_MARKDOWN)

    const val LAYOUT_REFLOWABLE = "reflowable"
    const val LAYOUT_FIXED = "fixed"
    val LAYOUTS: Set<String> = setOf(LAYOUT_REFLOWABLE, LAYOUT_FIXED)

    const val READING_PROGRESSION_LTR = "ltr"
    const val READING_PROGRESSION_RTL = "rtl"
    const val READING_PROGRESSION_AUTO = "auto"
    val READING_PROGRESSIONS: Set<String> = setOf(READING_PROGRESSION_LTR, READING_PROGRESSION_RTL, READING_PROGRESSION_AUTO)

    const val FEATURE_SEARCH = "search"
    const val FEATURE_TTS = "tts"
    const val FEATURE_READER_SESSION = "reader-session"
    const val FEATURE_COVER = "cover"
    const val FEATURE_RESOURCE_EXPORT = "resource-export"
    const val FEATURE_MARKDOWN = "markdown"
    val FEATURES: Set<String> = setOf(
        FEATURE_SEARCH,
        FEATURE_TTS,
        FEATURE_READER_SESSION,
        FEATURE_COVER,
        FEATURE_RESOURCE_EXPORT,
        FEATURE_MARKDOWN,
    )

    // Preferences a host may set (roadmap appendix A.6); other fields are ignored and reported.

    const val PREFERENCE_FONT_SIZE = "fontSize"
    const val PREFERENCE_FONT_FAMILY = "fontFamily"
    const val PREFERENCE_LINE_HEIGHT = "lineHeight"
    const val PREFERENCE_PAGE_MARGINS = "pageMargins"
    const val PREFERENCE_THEME = "theme"
    const val PREFERENCE_SCROLL = "scroll"
    const val PREFERENCE_COLUMN_COUNT = "columnCount"
    const val PREFERENCE_VERTICAL_TEXT = "verticalText"
    const val PREFERENCE_TEXT_ALIGN = "textAlign"
    const val PREFERENCE_HYPHENS = "hyphens"
    const val PREFERENCE_PUBLISHER_STYLES = "publisherStyles"
    val PREFERENCES: Set<String> = setOf(
        PREFERENCE_FONT_SIZE,
        PREFERENCE_FONT_FAMILY,
        PREFERENCE_LINE_HEIGHT,
        PREFERENCE_PAGE_MARGINS,
        PREFERENCE_THEME,
        PREFERENCE_SCROLL,
        PREFERENCE_COLUMN_COUNT,
        PREFERENCE_VERTICAL_TEXT,
        PREFERENCE_TEXT_ALIGN,
        PREFERENCE_HYPHENS,
        PREFERENCE_PUBLISHER_STYLES,
    )

    const val THEME_LIGHT = "light"
    const val THEME_SEPIA = "sepia"
    const val THEME_DARK = "dark"
    val THEMES: Set<String> = setOf(THEME_LIGHT, THEME_SEPIA, THEME_DARK)

    // Hard ceilings (roadmap appendix B.5).

    /** Books one host may keep open at a time. */
    const val MAX_OPEN_BOOKS = 8

    /** Idle time after which the plugin closes a book on its own. */
    const val BOOK_IDLE_TIMEOUT_MS = 300_000L

    /** Largest `openBook` or `openReader` options bundle, measured as the parcel size. */
    const val MAX_OPTIONS_BYTES = 36 * 1024

    const val MAX_HREF_LENGTH = 2048

    /** Largest metadata JSON document. */
    const val MAX_METADATA_BYTES = 256 * 1024

    const val MAX_TOC_ENTRIES = 5000

    /** Largest table-of-contents JSON document. */
    const val MAX_TOC_BYTES = 1024 * 1024

    const val MAX_READING_ORDER_ENTRIES = 5000

    /** Characters of one `getText` chunk; also the default when the request omits [KEY_MAX_CHARS]. */
    const val MAX_TEXT_CHARS_PER_CALL = 1024 * 1024

    /** Largest resource streamed by `openResource`. */
    const val MAX_RESOURCE_BYTES = 64L * 1024 * 1024

    const val MIN_QUERY_LENGTH = 1
    const val MAX_QUERY_LENGTH = 256
    const val DEFAULT_SEARCH_LIMIT = 50
    const val MAX_SEARCH_RESULTS = 500

    /** Largest search-results JSON document. */
    const val MAX_RESULTS_BYTES = 2 * 1024 * 1024

    const val MAX_LOCATOR_BYTES = 16 * 1024
    const val MAX_PREFERENCES_BYTES = 16 * 1024
    const val MAX_BOOKMARKS = 500

    /** Largest event bundle, measured as the parcel size. */
    const val MAX_EVENT_BYTES = 32 * 1024

    /** Longest [KEY_ERROR_MESSAGE] either side keeps; longer text is truncated. */
    const val MAX_ERROR_MESSAGE_BYTES = 4 * 1024

    /** Time the plugin keeps an unclaimed reader session before closing it with [REASON_TIMEOUT]. */
    const val READER_CLAIM_TIMEOUT_MS = 60_000L

    /** Smallest interval between two [EVENT_PROGRESS] events of one session. */
    const val PROGRESS_THROTTLE_MS = 500L

    // Host-side call timeouts.

    /** `openBook` and `openReader` (parsing a large publication). */
    const val OPEN_TIMEOUT_MS = 30_000L

    /** Every other call, including a full search page. */
    const val CALL_TIMEOUT_MS = 60_000L
}
