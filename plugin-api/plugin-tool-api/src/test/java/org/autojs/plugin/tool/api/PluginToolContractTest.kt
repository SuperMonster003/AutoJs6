package org.autojs.plugin.tool.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Freezes the published identifiers of contract version 1 (action, keys, values, limits) and the naming rules; a plugin
 * built against this module talks to every host of the same contract version.
 *
 * zh-CN: 冻结契约版本 1 已公开的标识 (action, 键, 取值, 上限) 与命名规则.
 */
class PluginToolContractTest {

    @Test
    fun versionAndActionAreFrozen() {
        assertEquals("org.autojs.plugin.TOOL_PROVIDER", PluginToolContract.SERVICE_ACTION)
        assertEquals(1, PluginToolContract.CONTRACT_VERSION)
        assertTrue(PluginToolContract.supportsContractVersion(1))
        assertFalse(PluginToolContract.supportsContractVersion(0))
        assertFalse(PluginToolContract.supportsContractVersion(2))
        assertEquals("pluginToolContractVersion", PluginToolCapabilityKeys.CONTRACT_VERSION)
    }

    @Test
    fun bundleAndCatalogKeysAreFrozen() {
        assertEquals(listOf("contractVersion", "hostVersionCode"), listOf(PluginToolCatalogRequestKeys.CONTRACT_VERSION, PluginToolCatalogRequestKeys.HOST_VERSION_CODE))
        assertEquals(listOf("contractVersion", "catalogJson", "errorCode", "errorMessage"),
            listOf(PluginToolCatalogKeys.CONTRACT_VERSION, PluginToolCatalogKeys.CATALOG_JSON, PluginToolCatalogKeys.ERROR_CODE, PluginToolCatalogKeys.ERROR_MESSAGE))
        assertEquals(listOf("contractVersion", "invocationId", "tool", "argumentsJson", "inputsJson", "outputsJson", "timeoutMs", "consumer"),
            listOf(PluginToolInvokeKeys.CONTRACT_VERSION, PluginToolInvokeKeys.INVOCATION_ID, PluginToolInvokeKeys.TOOL, PluginToolInvokeKeys.ARGUMENTS_JSON,
                PluginToolInvokeKeys.INPUTS_JSON, PluginToolInvokeKeys.OUTPUTS_JSON, PluginToolInvokeKeys.TIMEOUT_MS, PluginToolInvokeKeys.CONSUMER))
        assertEquals(listOf("accepted", "errorCode", "errorMessage"), listOf(PluginToolAcceptKeys.ACCEPTED, PluginToolAcceptKeys.ERROR_CODE, PluginToolAcceptKeys.ERROR_MESSAGE))
        assertEquals(listOf("invocationId", "resultJson", "text", "outputsJson"),
            listOf(PluginToolResultKeys.INVOCATION_ID, PluginToolResultKeys.RESULT_JSON, PluginToolResultKeys.TEXT, PluginToolResultKeys.OUTPUTS_JSON))
        assertEquals(listOf("invocationId", "code", "message"), listOf(PluginToolErrorKeys.INVOCATION_ID, PluginToolErrorKeys.CODE, PluginToolErrorKeys.MESSAGE))
        assertEquals(listOf("invocationId", "reason"), listOf(PluginToolCancelKeys.INVOCATION_ID, PluginToolCancelKeys.REASON))
        assertEquals(
            listOf("shortName", "sharedFamily", "tools", "name", "title", "description", "inputSchema", "outputSchema", "risk", "timeoutMs", "maxConcurrency", "outputs", "x-autojs6-type"),
            listOf(PluginToolCatalogJson.SHORT_NAME, PluginToolCatalogJson.SHARED_FAMILY, PluginToolCatalogJson.TOOLS, PluginToolCatalogJson.NAME,
                PluginToolCatalogJson.TITLE, PluginToolCatalogJson.DESCRIPTION, PluginToolCatalogJson.INPUT_SCHEMA, PluginToolCatalogJson.OUTPUT_SCHEMA,
                PluginToolCatalogJson.RISK, PluginToolCatalogJson.TIMEOUT_MS, PluginToolCatalogJson.MAX_CONCURRENCY, PluginToolCatalogJson.OUTPUTS,
                PluginToolCatalogJson.SPECIAL_TYPE),
        )
    }

    @Test
    fun valuesAreFrozen() {
        assertEquals(setOf("readonly", "write", "destructive", "network"), PluginToolValues.RISKS)
        assertEquals(setOf("image", "file", "directory"), PluginToolValues.SPECIAL_TYPES)
        assertEquals("directory", PluginToolValues.INPUT_SOURCE_DIRECTORY)
        assertEquals("x-autojs6-files", PluginToolCatalogJson.DIRECTORY_FILES)
        assertEquals("kind", PluginToolCatalogJson.OUTPUT_KIND)
        assertEquals(setOf("file", "directory"), PluginToolValues.OUTPUT_KINDS)
        assertEquals("outputTrees", PluginToolInvokeKeys.OUTPUT_TREES)
        assertEquals(listOf("ok", "errorCode", "errorMessage"),
            listOf(PluginToolOutputTreeKeys.OK, PluginToolOutputTreeKeys.ERROR_CODE, PluginToolOutputTreeKeys.ERROR_MESSAGE))
        assertEquals(listOf("EXISTS", "INVALID_PATH", "LIMIT_EXCEEDED", "CLOSED", "IO_ERROR"),
            listOf(PluginToolOutputTreeErrors.EXISTS, PluginToolOutputTreeErrors.INVALID_PATH, PluginToolOutputTreeErrors.LIMIT_EXCEEDED,
                PluginToolOutputTreeErrors.CLOSED, PluginToolOutputTreeErrors.IO_ERROR))
        assertEquals("screen", PluginToolValues.IMAGE_SCREEN)
        assertEquals(
            listOf("UNSUPPORTED_CONTRACT_VERSION", "UNKNOWN_TOOL", "INVALID_ARGUMENTS", "NOT_READY", "BUSY", "LIMIT_EXCEEDED", "INPUT_UNREADABLE", "CANCELLED", "TIMEOUT", "FAILED"),
            listOf(PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION, PluginToolErrorCodes.UNKNOWN_TOOL, PluginToolErrorCodes.INVALID_ARGUMENTS,
                PluginToolErrorCodes.NOT_READY, PluginToolErrorCodes.BUSY, PluginToolErrorCodes.LIMIT_EXCEEDED, PluginToolErrorCodes.INPUT_UNREADABLE,
                PluginToolErrorCodes.CANCELLED, PluginToolErrorCodes.TIMEOUT, PluginToolErrorCodes.FAILED),
        )
    }

    @Test
    fun limitsMatchTheRoadmap() {
        assertEquals(256 * 1024, PluginToolLimits.MAX_CATALOG_BYTES)
        assertEquals(64, PluginToolLimits.MAX_TOOLS_PER_PLUGIN)
        assertEquals(8 * 1024 * 1024, PluginToolLimits.MAX_DESCRIPTOR_BYTES)
        assertEquals(512L * 1024 * 1024, PluginToolLimits.MAX_FILE_INPUT_BYTES)
        assertEquals(16, PluginToolLimits.MAX_DIRECTORY_FILES)
        assertEquals(100_000, PluginToolLimits.MAX_OUTPUT_TREE_FILES)
        assertEquals(4096, PluginToolLimits.MAX_OUTPUT_TREE_PATH_BYTES)
        assertEquals(255, PluginToolLimits.MAX_OUTPUT_TREE_SEGMENT_BYTES)
        assertEquals(32, PluginToolLimits.MAX_INPUT_DESCRIPTORS)
        assertEquals(PluginToolLimits.MAX_MCP_TOOL_NAME_CHARS - PluginToolLimits.MCP_TOOL_NAME_PREFIX.length, PluginToolLimits.MAX_FULL_NAME_CHARS)
        assertTrue(PluginToolLimits.DEFAULT_TIMEOUT_MS <= PluginToolLimits.MAX_TIMEOUT_MS)
        assertTrue(PluginToolLimits.DEFAULT_MAX_CONCURRENCY <= PluginToolLimits.MAX_CONCURRENCY)
    }

    @Test
    fun shortNamesAreLowerCaseWithoutUnderscores() {
        listOf("opencc", "yolo", "imagequant", "archive", "ab", "x1234567890abcde").forEach { assertTrue(it, PluginToolNames.isValidShortName(it)) }
        listOf("", "a", "OpenCC", "open_cc", "1yolo", "x1234567890abcdef", "yolo-ncnn").forEach { assertFalse(it, PluginToolNames.isValidShortName(it)) }
        assertTrue(PluginToolNames.isReservedShortName("ocr"))
        assertTrue(PluginToolNames.isReservedShortName("script"))
        assertFalse(PluginToolNames.isReservedShortName("opencc"))
        PluginToolNames.RESERVED_SHORT_NAMES.forEach { assertTrue("reserved names follow the pattern: $it", PluginToolNames.isValidShortName(it)) }
    }

    @Test
    fun toolNamesAndFullNames() {
        listOf("convert", "list_configs", "detect", "a1", "x_1_y").forEach { assertTrue(it, PluginToolNames.isValidToolName(it)) }
        listOf("", "Convert", "_convert", "convert_", "list__configs", "1convert", "con-vert").forEach { assertFalse(it, PluginToolNames.isValidToolName(it)) }
        assertEquals("opencc_list_configs", PluginToolNames.fullName("opencc", "list_configs"))
        assertEquals("opencc" to "list_configs", PluginToolNames.split("opencc_list_configs"))
        assertNull(PluginToolNames.split("opencc"))
        assertNull(PluginToolNames.split("_convert"))
        assertNull(PluginToolNames.split("Open_convert"))
        assertTrue(PluginToolNames.fitsMcpName("a".repeat(PluginToolLimits.MAX_FULL_NAME_CHARS)))
        assertFalse(PluginToolNames.fitsMcpName("a".repeat(PluginToolLimits.MAX_FULL_NAME_CHARS + 1)))
        assertEquals(64, (PluginToolLimits.MCP_TOOL_NAME_PREFIX + "a".repeat(PluginToolLimits.MAX_FULL_NAME_CHARS)).length)
    }
}
