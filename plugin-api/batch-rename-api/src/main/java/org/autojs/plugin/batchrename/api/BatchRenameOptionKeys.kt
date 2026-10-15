package org.autojs.plugin.batchrename.api

/**
 * Keys of the options Bundle ([BatchRenameRequestKeys.OPTIONS]). The names follow the script options of
 * `files.batchRename`; a missing key takes the default documented here (PowerRename defaults, roadmap D17). Unknown keys
 * are ignored so a newer host can add optional keys behind a capability.
 *
 * zh-CN: 选项 Bundle ([BatchRenameRequestKeys.OPTIONS]) 的键. 名称与 `files.batchRename` 的脚本选项一致; 缺省的键取这里写明的
 * 默认值 (PowerRename 默认值, 路线图 D17). 未知键被忽略, 便于较新的宿主在能力声明之后追加可选键.
 */
object BatchRenameOptionKeys {

    /** String: text or regular expression source to find; required, at most [BatchRenameLimits.MAX_PATTERN_CHARS]. */
    const val SEARCH = "search"

    /** String: replacement text with variables; default "". */
    const val REPLACE = "replace"

    /** Boolean: [SEARCH] is an ECMAScript regular expression; default false (plain text). */
    const val REGEX = "regex"

    /** String: further ECMAScript flags among `m`, `s`, `u` and `y`; `g` and `i` come from [MATCH_ALL] and [CASE_SENSITIVE]. */
    const val REGEX_FLAGS = "regexFlags"

    /** Boolean: replace every match instead of the first; default false. */
    const val MATCH_ALL = "matchAll"

    /** Boolean: default false (case-insensitive search). */
    const val CASE_SENSITIVE = "caseSensitive"

    /** String: `TARGET_*` of [BatchRenameValues]; default [BatchRenameValues.TARGET_ALL]. */
    const val TARGET = "target"

    /** Boolean: default true. */
    const val INCLUDE_FILES = "includeFiles"

    /** Boolean: default true. */
    const val INCLUDE_FOLDERS = "includeFolders"

    /** Boolean: items with a depth above 0 take part; default true. */
    const val INCLUDE_SUBFOLDERS = "includeSubfolders"

    /** String: `FORMAT_*` of [BatchRenameValues]; default [BatchRenameValues.FORMAT_NONE]. */
    const val FORMAT = "format"

    /** Boolean: expand `${}` counters; default false (literal text). */
    const val ENUMERATE = "enumerate"

    /** Boolean: expand `${rstring...}` and `${ruuidv4}`; default false (literal text). */
    const val RANDOM = "random"

    /** String: `DATE_TIME_*` of [BatchRenameValues]; default [BatchRenameValues.DATE_TIME_MODIFIED]. */
    const val DATE_TIME = "dateTime"

    /** String: `NORMALIZE_*` of [BatchRenameValues]; default [BatchRenameValues.NORMALIZE_NONE]. */
    const val NORMALIZE = "normalize"

    /** Boolean: convert full-width forms and the ideographic space to their half-width forms; default false. */
    const val HALF_WIDTH = "halfWidth"

    /** Boolean: replace characters FAT32 / exFAT reject and suffix reserved Windows names; default false. */
    const val SANITIZE = "sanitize"

    /** String: replacement of sanitized characters; default [BatchRenameValues.DEFAULT_SANITIZE_REPLACEMENT]. */
    const val SANITIZE_REPLACEMENT = "sanitizeReplacement"

    /** Boolean: trim leading and trailing white space and trailing dots; default false. */
    const val TRIM = "trim"

    /** Boolean: start counters after the largest number already present in the matching names of a directory; default false. */
    const val CONTINUE_NUMBERING = "continueNumbering"

    /** String: `CONFLICT_*` of [BatchRenameValues]; default [BatchRenameValues.CONFLICT_FAIL]. */
    const val ON_CONFLICT = "onConflict"

    /** String: suffix pattern of the number policy, `{n}` is the number; default [BatchRenameValues.DEFAULT_CONFLICT_NUMBER_PATTERN]. */
    const val CONFLICT_NUMBER_PATTERN = "conflictNumberPattern"
}
