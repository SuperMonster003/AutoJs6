package org.autojs.plugin.batchrename.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snapshot of every literal of contract version 1. The plugin, the host bridge and the script API repeat these values, so
 * a change here is a protocol change: append new values, never edit or remove frozen ones.
 *
 * zh-CN: 契约版本 1 全部字面值的快照. 插件, 宿主桥接与脚本 API 都重复使用这些值, 因此此处的变化即协议变化: 只能追加, 不能修改或删除已冻结的值.
 */
class BatchRenameContractTest {

    @Test
    fun discoveryAndIdentityAreFrozen() {
        assertEquals("org.autojs.plugin.INFO", BatchRenamePluginActions.INFO)
        assertEquals(PluginActions.INFO, BatchRenamePluginActions.INFO)
        assertEquals("org.autojs.plugin.BATCH_RENAME", BatchRenamePluginActions.BATCH_RENAME)
        assertEquals("batch-rename", BatchRenamePluginActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", BatchRenamePluginActions.PLUGIN_PERMISSION)
        assertEquals("batch-rename", BatchRenamePluginIds.ID)
        assertEquals("batch-rename", BatchRenamePluginIds.ENGINE)
        assertEquals("default", BatchRenamePluginIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.batch.rename", BatchRenamePluginIds.PACKAGE_NAME)
        assertEquals("org.autojs.plugin.batchrename.api.IBatchRenamePlugin", IBatchRenamePlugin.DESCRIPTOR)
    }

    @Test
    fun contractVersionAndCapabilitiesAreFrozen() {
        assertEquals(1, BatchRenameContract.CONTRACT_VERSION)
        assertEquals(1, BatchRenameContract.MIN_CONTRACT_VERSION)
        assertEquals(1, BatchRenameContract.MAX_CONTRACT_VERSION)
        assertTrue(BatchRenameContract.supportsContractVersion(1))
        assertFalse(BatchRenameContract.supportsContractVersion(0))
        assertFalse(BatchRenameContract.supportsContractVersion(2))
        assertEquals("ecmascript", BatchRenameContract.REGEX_DIALECT_ECMASCRIPT)
        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, BatchRenameCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("batchRenameContractVersion", BatchRenameCapabilityKeys.CONTRACT_VERSION)
        assertEquals("batchRenameFeatures", BatchRenameCapabilityKeys.FEATURES)
        assertEquals("batchRenameMaxItems", BatchRenameCapabilityKeys.MAX_ITEMS)
        assertEquals("batchRenameRegexDialect", BatchRenameCapabilityKeys.REGEX_DIALECT)
    }

    @Test
    fun limitsAreFrozen() {
        assertEquals(2000, BatchRenameLimits.MAX_ITEMS_PER_PLAN)
        assertEquals(512, BatchRenameLimits.MAX_PARENTS_PER_PLAN)
        assertEquals(20_000, BatchRenameLimits.MAX_EXISTING_NAMES_PER_PLAN)
        assertEquals(1024, BatchRenameLimits.MAX_NAME_CHARS)
        assertEquals(4096, BatchRenameLimits.MAX_PATTERN_CHARS)
        assertEquals(262_144, BatchRenameLimits.MAX_REQUEST_CHARS)
        assertEquals(256, BatchRenameLimits.MAX_RANDOM_LENGTH)
        assertEquals(999, BatchRenameLimits.MAX_CONFLICT_NUMBER)
        assertEquals(255, BatchRenameLimits.MAX_RESULT_NAME_BYTES)
        assertEquals(100, BatchRenameLimits.MAX_PRESETS)
        assertEquals(64, BatchRenameLimits.MAX_PRESET_NAME_CHARS)
        assertEquals(131_072, BatchRenameLimits.MAX_PRESET_DOCUMENT_CHARS)
        assertTrue(BatchRenameLimits.MAX_PRESET_DOCUMENT_CHARS * 2 <= 512 * 1024)
        // One request of the largest allowed size stays well below the 1 MiB Binder transaction buffer.
        assertTrue(BatchRenameLimits.MAX_REQUEST_CHARS * 2 <= 512 * 1024)
    }

    @Test
    fun bundleKeysAreFrozen() {
        assertEquals(
            listOf("contractVersion", "options", "parents", "items", "locale", "timeZone", "now"),
            listOf(
                BatchRenameRequestKeys.CONTRACT_VERSION, BatchRenameRequestKeys.OPTIONS, BatchRenameRequestKeys.PARENTS,
                BatchRenameRequestKeys.ITEMS, BatchRenameRequestKeys.LOCALE, BatchRenameRequestKeys.TIME_ZONE, BatchRenameRequestKeys.NOW,
            ),
        )
        assertEquals(
            listOf("parentId", "existingNames", "caseInsensitive", "restrictedCharacters"),
            listOf(
                BatchRenameParentKeys.PARENT_ID, BatchRenameParentKeys.EXISTING_NAMES, BatchRenameParentKeys.CASE_INSENSITIVE,
                BatchRenameParentKeys.RESTRICTED_CHARACTERS,
            ),
        )
        assertEquals(
            listOf(
                "index", "parentId", "name", "kind", "depth", "lastModified", "length", "excluded",
                "newName", "status", "reason", "twoPhase", "overwrites",
            ),
            listOf(
                BatchRenameItemKeys.INDEX, BatchRenameItemKeys.PARENT_ID, BatchRenameItemKeys.NAME, BatchRenameItemKeys.KIND,
                BatchRenameItemKeys.DEPTH, BatchRenameItemKeys.LAST_MODIFIED, BatchRenameItemKeys.LENGTH, BatchRenameItemKeys.EXCLUDED,
                BatchRenameItemKeys.NEW_NAME, BatchRenameItemKeys.STATUS, BatchRenameItemKeys.REASON, BatchRenameItemKeys.TWO_PHASE,
                BatchRenameItemKeys.OVERWRITES,
            ),
        )
        assertEquals(
            listOf(
                "search", "replace", "regex", "regexFlags", "matchAll", "caseSensitive", "target", "includeFiles",
                "includeFolders", "includeSubfolders", "format", "enumerate", "random", "dateTime", "normalize", "halfWidth",
                "sanitize", "sanitizeReplacement", "trim", "continueNumbering", "onConflict", "conflictNumberPattern",
                "pinyin", "pinyinTone", "pinyinSeparator", "pinyinCase", "pinyinInitials", "preset",
            ),
            listOf(
                BatchRenameOptionKeys.SEARCH, BatchRenameOptionKeys.REPLACE, BatchRenameOptionKeys.REGEX, BatchRenameOptionKeys.REGEX_FLAGS,
                BatchRenameOptionKeys.MATCH_ALL, BatchRenameOptionKeys.CASE_SENSITIVE, BatchRenameOptionKeys.TARGET,
                BatchRenameOptionKeys.INCLUDE_FILES, BatchRenameOptionKeys.INCLUDE_FOLDERS, BatchRenameOptionKeys.INCLUDE_SUBFOLDERS,
                BatchRenameOptionKeys.FORMAT, BatchRenameOptionKeys.ENUMERATE, BatchRenameOptionKeys.RANDOM, BatchRenameOptionKeys.DATE_TIME,
                BatchRenameOptionKeys.NORMALIZE, BatchRenameOptionKeys.HALF_WIDTH, BatchRenameOptionKeys.SANITIZE,
                BatchRenameOptionKeys.SANITIZE_REPLACEMENT, BatchRenameOptionKeys.TRIM, BatchRenameOptionKeys.CONTINUE_NUMBERING,
                BatchRenameOptionKeys.ON_CONFLICT, BatchRenameOptionKeys.CONFLICT_NUMBER_PATTERN, BatchRenameOptionKeys.PINYIN,
                BatchRenameOptionKeys.PINYIN_TONE, BatchRenameOptionKeys.PINYIN_SEPARATOR, BatchRenameOptionKeys.PINYIN_CASE,
                BatchRenameOptionKeys.PINYIN_INITIALS, BatchRenameOptionKeys.PRESET,
            ),
        )
        assertEquals(
            listOf("contractVersion", "errorCode", "errorMessage", "rejected", "items", "counts", "options"),
            listOf(
                BatchRenameResultKeys.CONTRACT_VERSION, BatchRenameResultKeys.ERROR_CODE, BatchRenameResultKeys.ERROR_MESSAGE,
                BatchRenameResultKeys.REJECTED, BatchRenameResultKeys.ITEMS, BatchRenameResultKeys.COUNTS, BatchRenameResultKeys.OPTIONS,
            ),
        )
        assertEquals(
            listOf("document", "onConflict", "presets", "name", "options", "imported", "skipped", "rejected", "index", "reason"),
            listOf(
                BatchRenamePresetKeys.DOCUMENT, BatchRenamePresetKeys.ON_CONFLICT, BatchRenamePresetKeys.PRESETS, BatchRenamePresetKeys.NAME,
                BatchRenamePresetKeys.OPTIONS, BatchRenamePresetKeys.IMPORTED, BatchRenamePresetKeys.SKIPPED, BatchRenamePresetKeys.REJECTED,
                BatchRenamePresetKeys.INDEX, BatchRenamePresetKeys.REASON,
            ),
        )
    }

    @Test
    fun valueSetsAreFrozen() {
        assertEquals(1, BatchRenameValues.KIND_FILE)
        assertEquals(2, BatchRenameValues.KIND_DIRECTORY)
        assertEquals(listOf("name", "extension", "all"), BatchRenameValues.TARGETS)
        assertEquals(listOf("none", "lower", "upper", "title", "capitalize"), BatchRenameValues.FORMATS)
        assertEquals(listOf("modified", "now", "none"), BatchRenameValues.DATE_TIMES)
        assertEquals(listOf("none", "nfc", "nfd"), BatchRenameValues.NORMALIZATIONS)
        assertEquals(listOf("fail", "skip", "number", "overwrite"), BatchRenameValues.CONFLICT_POLICIES)
        assertEquals(" ({n})", BatchRenameValues.DEFAULT_CONFLICT_NUMBER_PATTERN)
        assertTrue(BatchRenameValues.DEFAULT_CONFLICT_NUMBER_PATTERN.contains(BatchRenameValues.CONFLICT_NUMBER_PLACEHOLDER))
        assertEquals("_", BatchRenameValues.DEFAULT_SANITIZE_REPLACEMENT)
        assertEquals(listOf("rename", "unchanged", "conflict", "invalid", "excluded"), BatchRenameValues.STATUSES)
        assertEquals(
            listOf(
                "noMatch", "sameName", "excludedByCaller", "notIncluded", "duplicateInBatch", "existingEntry",
                "numbersExhausted", "numbered", "overwrite", "empty", "dotName", "separator", "tooLong", "restrictedCharacter",
            ),
            BatchRenameValues.REASONS,
        )
        assertEquals(listOf("counter", "dateTime", "random", "cleanup", "pinyin", "presets"), BatchRenameValues.FEATURES)
        assertEquals(listOf("skip", "overwrite", "rename"), BatchRenameValues.PRESET_CONFLICTS)
        assertEquals(listOf("none", "mark", "number"), BatchRenameValues.PINYIN_TONES)
        assertEquals(listOf("none", "space", "underscore", "hyphen"), BatchRenameValues.PINYIN_SEPARATORS)
        assertEquals(listOf("lower", "capitalize"), BatchRenameValues.PINYIN_CASES)
        assertEquals(
            listOf("UNSUPPORTED_CONTRACT_VERSION", "INVALID_REQUEST", "INVALID_OPTIONS", "LIMIT_EXCEEDED", "INTERNAL_ERROR"),
            BatchRenameErrorCodes.ALL,
        )
    }

    @Test
    fun valueSetsHaveNoDuplicates() {
        listOf(
            BatchRenameValues.TARGETS, BatchRenameValues.FORMATS, BatchRenameValues.DATE_TIMES, BatchRenameValues.NORMALIZATIONS,
            BatchRenameValues.CONFLICT_POLICIES, BatchRenameValues.STATUSES, BatchRenameValues.REASONS, BatchRenameValues.FEATURES,
            BatchRenameValues.PINYIN_TONES, BatchRenameValues.PINYIN_SEPARATORS, BatchRenameValues.PINYIN_CASES,
            BatchRenameValues.PRESET_CONFLICTS, BatchRenameErrorCodes.ALL,
        ).forEach { values -> assertEquals(values.toSet().size, values.size) }
    }
}
