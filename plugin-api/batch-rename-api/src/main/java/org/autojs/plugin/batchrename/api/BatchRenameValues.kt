package org.autojs.plugin.batchrename.api

/**
 * Closed value sets of the contract. String values are the ones scripts see in `files.batchRename` options and plan
 * items, so they are lower case words; the host passes them through unchanged.
 *
 * zh-CN: 契约中的闭合取值集合. 字符串取值与脚本在 `files.batchRename` 选项和方案条目中看到的一致, 因此为小写单词; 宿主原样传递.
 */
object BatchRenameValues {

    // Item kinds (BatchRenameItemKeys.KIND).
    const val KIND_FILE = 1
    const val KIND_DIRECTORY = 2

    // Rename targets (BatchRenameOptionKeys.TARGET).
    const val TARGET_NAME = "name"
    const val TARGET_EXTENSION = "extension"
    const val TARGET_ALL = "all"
    val TARGETS: List<String> = listOf(TARGET_NAME, TARGET_EXTENSION, TARGET_ALL)

    // Text formatting (BatchRenameOptionKeys.FORMAT).
    const val FORMAT_NONE = "none"
    const val FORMAT_LOWER = "lower"
    const val FORMAT_UPPER = "upper"
    const val FORMAT_TITLE = "title"
    const val FORMAT_CAPITALIZE = "capitalize"
    val FORMATS: List<String> = listOf(FORMAT_NONE, FORMAT_LOWER, FORMAT_UPPER, FORMAT_TITLE, FORMAT_CAPITALIZE)

    // Date and time source (BatchRenameOptionKeys.DATE_TIME).
    const val DATE_TIME_MODIFIED = "modified"
    const val DATE_TIME_NOW = "now"
    const val DATE_TIME_NONE = "none"
    val DATE_TIMES: List<String> = listOf(DATE_TIME_MODIFIED, DATE_TIME_NOW, DATE_TIME_NONE)

    // Unicode normalization (BatchRenameOptionKeys.NORMALIZE).
    const val NORMALIZE_NONE = "none"
    const val NORMALIZE_NFC = "nfc"
    const val NORMALIZE_NFD = "nfd"
    val NORMALIZATIONS: List<String> = listOf(NORMALIZE_NONE, NORMALIZE_NFC, NORMALIZE_NFD)

    // Conflict policies (BatchRenameOptionKeys.ON_CONFLICT).
    const val CONFLICT_FAIL = "fail"
    const val CONFLICT_SKIP = "skip"
    const val CONFLICT_NUMBER = "number"
    const val CONFLICT_OVERWRITE = "overwrite"
    val CONFLICT_POLICIES: List<String> = listOf(CONFLICT_FAIL, CONFLICT_SKIP, CONFLICT_NUMBER, CONFLICT_OVERWRITE)

    // Pinyin tones (BatchRenameOptionKeys.PINYIN_TONE): none ("zhang"), tone marks ("zhāng") or numbers ("zhang1").
    const val PINYIN_TONE_NONE = "none"
    const val PINYIN_TONE_MARK = "mark"
    const val PINYIN_TONE_NUMBER = "number"
    val PINYIN_TONES: List<String> = listOf(PINYIN_TONE_NONE, PINYIN_TONE_MARK, PINYIN_TONE_NUMBER)

    // Pinyin syllable separators (BatchRenameOptionKeys.PINYIN_SEPARATOR).
    const val PINYIN_SEPARATOR_NONE = "none"
    const val PINYIN_SEPARATOR_SPACE = "space"
    const val PINYIN_SEPARATOR_UNDERSCORE = "underscore"
    const val PINYIN_SEPARATOR_HYPHEN = "hyphen"
    val PINYIN_SEPARATORS: List<String> = listOf(PINYIN_SEPARATOR_NONE, PINYIN_SEPARATOR_SPACE, PINYIN_SEPARATOR_UNDERSCORE, PINYIN_SEPARATOR_HYPHEN)

    // Pinyin letter case (BatchRenameOptionKeys.PINYIN_CASE): "zhangsan" or "ZhangSan".
    const val PINYIN_CASE_LOWER = "lower"
    const val PINYIN_CASE_CAPITALIZE = "capitalize"
    val PINYIN_CASES: List<String> = listOf(PINYIN_CASE_LOWER, PINYIN_CASE_CAPITALIZE)

    const val DEFAULT_CONFLICT_NUMBER_PATTERN = " ({n})"
    const val CONFLICT_NUMBER_PLACEHOLDER = "{n}"
    const val DEFAULT_SANITIZE_REPLACEMENT = "_"

    // Item status (BatchRenameItemKeys.STATUS); also the keys of BatchRenameResultKeys.COUNTS.
    const val STATUS_RENAME = "rename"
    const val STATUS_UNCHANGED = "unchanged"
    const val STATUS_CONFLICT = "conflict"
    const val STATUS_INVALID = "invalid"
    const val STATUS_EXCLUDED = "excluded"
    val STATUSES: List<String> = listOf(STATUS_RENAME, STATUS_UNCHANGED, STATUS_CONFLICT, STATUS_INVALID, STATUS_EXCLUDED)

    // Reasons (BatchRenameItemKeys.REASON).
    /** Unchanged: the search found nothing in the item's target part. */
    const val REASON_NO_MATCH = "noMatch"
    /** Unchanged: the computed name equals the current name. */
    const val REASON_SAME_NAME = "sameName"
    /** Excluded by the caller ([BatchRenameItemKeys.EXCLUDED]). */
    const val REASON_EXCLUDED_BY_CALLER = "excludedByCaller"
    /** Excluded by `includeFiles`, `includeFolders` or `includeSubfolders`. */
    const val REASON_NOT_INCLUDED = "notIncluded"
    /** Conflict: another item of the batch gets the same name in the same directory. */
    const val REASON_DUPLICATE_IN_BATCH = "duplicateInBatch"
    /** Conflict: an entry that is not renamed already has the name. */
    const val REASON_EXISTING_ENTRY = "existingEntry"
    /** Conflict: the number policy found no free name up to [BatchRenameLimits.MAX_CONFLICT_NUMBER]. */
    const val REASON_NUMBERS_EXHAUSTED = "numbersExhausted"
    /** Rename: the number policy resolved a conflict. */
    const val REASON_NUMBERED = "numbered"
    /** Rename: the overwrite policy replaces an existing entry ([BatchRenameItemKeys.OVERWRITES]). */
    const val REASON_OVERWRITE = "overwrite"
    /** Invalid: the name is empty. */
    const val REASON_EMPTY = "empty"
    /** Invalid: the name is `.` or `..`. */
    const val REASON_DOT_NAME = "dotName"
    /** Invalid: the name contains `/` or NUL. */
    const val REASON_SEPARATOR = "separator"
    /** Invalid: the name exceeds [BatchRenameLimits.MAX_RESULT_NAME_BYTES] UTF-8 bytes. */
    const val REASON_TOO_LONG = "tooLong"
    /** Invalid: the directory's file system rejects a character of the name ([BatchRenameParentKeys.RESTRICTED_CHARACTERS]). */
    const val REASON_RESTRICTED_CHARACTER = "restrictedCharacter"
    val REASONS: List<String> = listOf(
        REASON_NO_MATCH, REASON_SAME_NAME, REASON_EXCLUDED_BY_CALLER, REASON_NOT_INCLUDED,
        REASON_DUPLICATE_IN_BATCH, REASON_EXISTING_ENTRY, REASON_NUMBERS_EXHAUSTED, REASON_NUMBERED, REASON_OVERWRITE,
        REASON_EMPTY, REASON_DOT_NAME, REASON_SEPARATOR, REASON_TOO_LONG, REASON_RESTRICTED_CHARACTER,
    )

    // Optional features (BatchRenameCapabilityKeys.FEATURES).
    /** `${}` counters. */
    const val FEATURE_COUNTER = "counter"
    /** `$YYYY` ... `$f` date and time variables. */
    const val FEATURE_DATE_TIME = "dateTime"
    /** `${rstring...}` and `${ruuidv4}`. */
    const val FEATURE_RANDOM = "random"
    /** Normalization, half-width conversion, sanitizing, trimming and continued numbering. */
    const val FEATURE_CLEANUP = "cleanup"
    /** The `pinyin*` options and the `${pinyin}` variable (plugin 1.1.0). */
    const val FEATURE_PINYIN = "pinyin"
    /** Named presets: the `preset` option, the effective options of a plan and the preset transactions (plugin 1.1.0). */
    const val FEATURE_PRESETS = "presets"
    /** The `metadata` option, item metadata and the photo metadata variables (plugin 1.1.0). */
    const val FEATURE_METADATA = "metadata"
    val FEATURES: List<String> = listOf(
        FEATURE_COUNTER, FEATURE_DATE_TIME, FEATURE_RANDOM, FEATURE_CLEANUP, FEATURE_PINYIN, FEATURE_PRESETS, FEATURE_METADATA,
    )

    /**
     * The 25 photo metadata variables of PowerRename, written `$` plus the name; the longest name wins and they are
     * matched before the date variables (`$DATE_TAKEN_YYYY` is not `$D`).
     */
    val METADATA_VARIABLES: List<String> = listOf(
        "CAMERA_MAKE", "CAMERA_MODEL", "LENS", "ISO", "APERTURE", "SHUTTER", "FOCAL", "FLASH", "WIDTH", "HEIGHT", "AUTHOR",
        "COPYRIGHT", "LATITUDE", "LONGITUDE", "ALTITUDE", "DATE_TAKEN_YYYY", "DATE_TAKEN_YY", "DATE_TAKEN_MM", "DATE_TAKEN_DD",
        "DATE_TAKEN_HH", "DATE_TAKEN_mm", "DATE_TAKEN_SS", "EXPOSURE_BIAS", "ORIENTATION", "COLOR_SPACE",
    )

    // Values of [BatchRenameMetadataKeys.COLOR_SPACE].
    const val COLOR_SPACE_SRGB = "sRGB"
    const val COLOR_SPACE_ADOBE_RGB = "AdobeRGB"
    const val COLOR_SPACE_UNCALIBRATED = "Uncalibrated"
    val COLOR_SPACES: List<String> = listOf(COLOR_SPACE_SRGB, COLOR_SPACE_ADOBE_RGB, COLOR_SPACE_UNCALIBRATED)

    // What importPresets does with a preset whose name is taken ([BatchRenamePresetKeys.ON_CONFLICT]).
    /** Keep the existing preset and list the name under [BatchRenamePresetKeys.SKIPPED]; the default. */
    const val PRESET_CONFLICT_SKIP = "skip"
    /** Replace the existing preset. */
    const val PRESET_CONFLICT_OVERWRITE = "overwrite"
    /** Store the imported preset under the first free name of the form `name (n)`. */
    const val PRESET_CONFLICT_RENAME = "rename"
    val PRESET_CONFLICTS: List<String> = listOf(PRESET_CONFLICT_SKIP, PRESET_CONFLICT_OVERWRITE, PRESET_CONFLICT_RENAME)
}
