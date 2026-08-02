package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerActionOutputPolicyTest {

    @Test
    fun legacyProtocolsDefaultToNoOutput() {
        listOf(1, 2).forEach { protocolVersion ->
            val output = normalize(protocolVersion = protocolVersion)
                ?: error("Expected legacy no-output descriptor")

            assertEquals(ExplorerActionValues.OUTPUT_NONE, output.mode)
            assertTrue(output.mimeTypes.isEmpty())
            assertNull(output.nameSuffix)
            assertNull(output.maxBytes)
        }
    }

    @Test
    fun legacyProtocolsRejectEveryOutputField() {
        listOf(1, 2).forEach { protocolVersion ->
            assertNull(normalize(protocolVersion, outputMode = ExplorerActionValues.OUTPUT_NONE))
            assertNull(normalize(protocolVersion, outputMimeTypes = emptyList()))
            assertNull(normalize(protocolVersion, outputNameSuffix = ".png"))
            assertNull(normalize(protocolVersion, maxOutputBytes = 1L))
            assertNull(
                createSibling(protocolVersion = protocolVersion),
            )
        }
    }

    @Test
    fun protocolV3NoOutputRejectsOutputMetadata() {
        val implicit = normalize(protocolVersion = 3) ?: error("Expected implicit no output")
        val explicit = normalize(protocolVersion = 3, outputMode = ExplorerActionValues.OUTPUT_NONE)
            ?: error("Expected explicit no output")

        assertEquals(implicit, explicit)
        assertEquals(ExplorerActionValues.OUTPUT_NONE, explicit.mode)
        assertNull(normalize(3, outputMimeTypes = listOf("image/png")))
        assertNull(normalize(3, outputNameSuffix = ".png"))
        assertNull(normalize(3, maxOutputBytes = 1L))
    }

    @Test
    fun protocolV3CreateSiblingNormalizesBoundedMetadata() {
        val output = createSibling(
            outputMimeTypes = listOf(" Image/PNG; charset=binary ", "image/webp"),
            outputNameSuffix = " _edited.webp ",
            maxOutputBytes = 16L * 1024L * 1024L,
        ) ?: error("Expected a valid output descriptor")

        assertEquals(ExplorerActionValues.OUTPUT_CREATE_SIBLING, output.mode)
        assertEquals(setOf("image/png", "image/webp"), output.mimeTypes)
        assertEquals("_edited.webp", output.nameSuffix)
        assertEquals(16L * 1024L * 1024L, output.maxBytes)
    }

    @Test
    fun createSiblingRequiresAllMetadata() {
        assertNull(createSibling(outputMimeTypes = null))
        assertNull(createSibling(outputMimeTypes = emptyList()))
        assertNull(createSibling(outputNameSuffix = null))
        assertNull(createSibling(maxOutputBytes = null))
    }

    @Test
    fun createSiblingRejectsWildcardsUnsafeSuffixesAndUnboundedSizes() {
        assertNull(createSibling(outputMimeTypes = listOf("image/*")))
        assertNull(createSibling(outputMimeTypes = listOf("*/*")))
        assertNull(createSibling(outputMimeTypes = listOf("not-a-mime")))
        assertNull(createSibling(outputMimeTypes = listOf("application/pdf")))
        assertNull(createSibling(outputMimeTypes = listOf("image/gif")))
        assertNull(createSibling(outputNameSuffix = "../edited.png"))
        assertNull(createSibling(outputNameSuffix = "edited\\image.png"))
        assertNull(createSibling(outputNameSuffix = ".."))
        assertNull(createSibling(maxOutputBytes = 0L))
        assertNull(createSibling(maxOutputBytes = ExplorerActionOutputPolicy.MAX_HOST_OUTPUT_BYTES + 1L))
    }

    @Test
    fun createSiblingAcceptsOnlyHostValidatedImageFormats() {
        assertEquals(
            setOf("image/jpeg", "image/png", "image/webp"),
            createSibling(
                outputMimeTypes = listOf("image/jpeg", "image/png", "image/webp"),
                maxOutputBytes = ExplorerActionOutputPolicy.MAX_HOST_OUTPUT_BYTES,
            )?.mimeTypes,
        )
    }

    @Test
    fun unknownOutputModeIsRejected() {
        assertNull(normalize(protocolVersion = 3, outputMode = Int.MAX_VALUE))
    }

    @Test
    fun unsupportedProtocolVersionIsRejected() {
        assertNull(normalize(protocolVersion = 0))
        assertNull(normalize(protocolVersion = ExplorerActionProtocol.VERSION + 1))
    }

    @Test
    fun normalizedDescriptorCarriesOutputPolicy() {
        val descriptor = ExplorerActionCatalogPolicy.normalizeDescriptor(
            protocolVersion = 3,
            packageName = "com.example.image",
            id = "convert-image",
            labelResourceName = null,
            labelFallback = "Convert image",
            activityClassName = ".ConvertActivity",
            priority = 0,
            targetKind = ExplorerActionValues.TARGET_FILE,
            accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
            mimeTypes = listOf("image/*"),
            extensions = emptyList(),
            outputMode = ExplorerActionValues.OUTPUT_CREATE_SIBLING,
            outputMimeTypes = listOf("image/png"),
            outputNameSuffix = ".converted.png",
            maxOutputBytes = 8L * 1024L * 1024L,
        ) ?: error("Expected a valid output descriptor")

        assertEquals(ExplorerActionValues.OUTPUT_CREATE_SIBLING, descriptor.outputMode)
        assertEquals(setOf("image/png"), descriptor.outputMimeTypes)
        assertEquals(".converted.png", descriptor.outputNameSuffix)
        assertEquals(8L * 1024L * 1024L, descriptor.maxOutputBytes)
    }

    @Test
    fun outputActionsAreRestrictedToOverflowPlacement() {
        assertNull(
            ExplorerActionCatalogPolicy.normalizeDescriptor(
                protocolVersion = 3,
                packageName = "com.example.image",
                id = "edit-image",
                labelResourceName = null,
                labelFallback = "Edit image",
                activityClassName = ".EditActivity",
                priority = 0,
                targetKind = ExplorerActionValues.TARGET_FILE,
                accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                mimeTypes = listOf("image/*"),
                extensions = emptyList(),
                outputMode = ExplorerActionValues.OUTPUT_CREATE_SIBLING,
                outputMimeTypes = listOf("image/png"),
                outputNameSuffix = "edited",
                maxOutputBytes = 1024L,
            ),
        )
    }

    private fun createSibling(
        protocolVersion: Int = 3,
        outputMimeTypes: Collection<String>? = listOf("image/png"),
        outputNameSuffix: String? = ".png",
        maxOutputBytes: Long? = 1024L,
    ) = normalize(
        protocolVersion = protocolVersion,
        outputMode = ExplorerActionValues.OUTPUT_CREATE_SIBLING,
        outputMimeTypes = outputMimeTypes,
        outputNameSuffix = outputNameSuffix,
        maxOutputBytes = maxOutputBytes,
    )

    private fun normalize(
        protocolVersion: Int,
        outputMode: Int? = null,
        outputMimeTypes: Collection<String>? = null,
        outputNameSuffix: String? = null,
        maxOutputBytes: Long? = null,
    ) = ExplorerActionOutputPolicy.normalize(
        protocolVersion = protocolVersion,
        outputMode = outputMode,
        outputMimeTypes = outputMimeTypes,
        outputNameSuffix = outputNameSuffix,
        maxOutputBytes = maxOutputBytes,
    )
}
