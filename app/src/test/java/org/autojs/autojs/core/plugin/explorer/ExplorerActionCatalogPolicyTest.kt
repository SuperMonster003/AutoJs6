package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionPluginPermissions
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerActionCatalogPolicyTest {

    @Test
    fun validDescriptorIsNormalized() {
        val descriptor = descriptor(
            activityClassName = ".PreviewActivity",
            priority = Int.MAX_VALUE,
            mimeTypes = listOf(" Text/Markdown; charset=UTF-8 "),
            extensions = listOf(".MD", "Markdown"),
        ) ?: error("Expected a valid descriptor")

        assertEquals("com.example.preview.PreviewActivity", descriptor.activityClassName)
        assertEquals(10_000, descriptor.priority)
        assertEquals(setOf("text/markdown"), descriptor.mimeTypes)
        assertEquals(setOf("md", "markdown"), descriptor.extensions)
        assertEquals(ExplorerActionValues.OUTPUT_NONE, descriptor.outputMode)
        assertTrue(descriptor.outputMimeTypes.isEmpty())
        assertNull(descriptor.outputNameSuffix)
        assertNull(descriptor.maxOutputBytes)
    }

    @Test
    fun activityClassNamesSupportRelativeAndShortForms() {
        assertEquals(
            "com.example.preview.PreviewActivity",
            ExplorerActionCatalogPolicy.normalizeActivityClassName(
                "com.example.preview",
                ".PreviewActivity",
            ),
        )
        assertEquals(
            "com.example.preview.PreviewActivity",
            ExplorerActionCatalogPolicy.normalizeActivityClassName(
                "com.example.preview",
                "PreviewActivity",
            ),
        )
        assertEquals(
            "com.example.preview.ui.PreviewActivity",
            ExplorerActionCatalogPolicy.normalizeActivityClassName(
                "com.example.preview",
                "com.example.preview.ui.PreviewActivity",
            ),
        )
    }

    @Test
    fun mimeMatchingSupportsExactTypeWildcardAndGlobalWildcard() {
        val target = ExplorerActionTarget("text/markdown", "md")

        assertTrue(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("text/markdown"))!!, target))
        assertTrue(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("text/*"))!!, target))
        assertTrue(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("*/*"))!!, target))
        assertFalse(ExplorerActionCatalogPolicy.matches(descriptor(mimeTypes = listOf("image/*"))!!, target))
    }

    @Test
    fun mimeAndExtensionMatchersUseOrSemantics() {
        val descriptor = descriptor(
            mimeTypes = listOf("application/pdf"),
            extensions = listOf("md"),
        ) ?: error("Expected a valid descriptor")

        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("text/plain", "MD"),
            ),
        )
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("application/pdf", "bin"),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("image/png", "png"),
            ),
        )
    }

    @Test
    fun descriptorRequiresAtLeastOneValidMatcher() {
        assertNull(descriptor(mimeTypes = emptyList(), extensions = emptyList()))
        assertNull(descriptor(mimeTypes = listOf("not-a-mime")))
        assertNull(descriptor(extensions = listOf("bad.extension")))
    }

    @Test
    fun descriptorRejectsUnsupportedV1ExecutionModes() {
        assertNull(descriptor(targetKind = ExplorerActionValues.TARGET_FILE + 1))
        assertNull(descriptor(accessMode = ExplorerActionValues.ACCESS_READ_ONLY + 1))
        assertNull(descriptor(placement = ExplorerActionValues.PLACEMENT_PRIMARY))
        assertNull(descriptor(placement = ExplorerActionValues.PLACEMENT_PRIMARY + 1))
    }

    @Test
    fun protocolV2AcceptsPrimaryPlacementWithoutChangingMatchingSemantics() {
        val descriptor = descriptor(
            protocolVersion = 2,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            mimeTypes = listOf("video/*"),
        ) ?: error("Expected a valid primary descriptor")

        assertEquals(2, descriptor.protocolVersion)
        assertEquals(ExplorerActionValues.PLACEMENT_PRIMARY, descriptor.placement)
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("video/mp4", "mp4"),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                descriptor,
                ExplorerActionTarget("audio/mpeg", "mp3"),
            ),
        )
    }

    @Test
    fun protocolV4SupportsDirectoryAndOrderedMixedTargets() {
        val directoryAction = descriptor(
            protocolVersion = 4,
            targetKind = ExplorerActionValues.TARGET_DIRECTORY,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            mimeTypes = emptyList(),
        ) ?: error("Expected a valid directory descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                directoryAction,
                ExplorerActionTarget(
                    mimeType = "inode/directory",
                    extension = "",
                    kind = ExplorerActionValues.TARGET_DIRECTORY,
                ),
            ),
        )
        assertFalse(
            ExplorerActionCatalogPolicy.matches(
                directoryAction,
                ExplorerActionTarget("text/plain", "txt"),
            ),
        )

        val mixedSelectionAction = descriptor(
            protocolVersion = 4,
            targetKind = ExplorerActionValues.TARGET_MIXED,
            cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
            placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
            mimeTypes = listOf("*/*"),
        ) ?: error("Expected a valid mixed selection descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                mixedSelectionAction,
                listOf(
                    ExplorerActionTarget("text/plain", "txt"),
                    ExplorerActionTarget(
                        mimeType = "inode/directory",
                        extension = "",
                        kind = ExplorerActionValues.TARGET_DIRECTORY,
                    ),
                ),
            ),
        )
        assertFalse(ExplorerActionCatalogPolicy.matches(mixedSelectionAction, emptyList()))
    }

    @Test
    fun protocolV4RequiresExplicitCardinalityAndValidPlacementPairing() {
        assertNull(descriptor(protocolVersion = 4))
        assertNull(
            descriptor(
                protocolVersion = 4,
                cardinality = ExplorerActionValues.CARDINALITY_MULTIPLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 4,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR,
            ),
        )
    }

    @Test
    fun createInParentAccessIsAvailableOnlyToProtocolV4Actions() {
        assertNull(
            descriptor(
                protocolVersion = 3,
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
            ),
        )
        val createAction = descriptor(
            protocolVersion = 4,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
        ) ?: error("Expected a valid create-in-parent descriptor")
        assertTrue(
            ExplorerActionCatalogPolicy.matches(
                createAction,
                ExplorerActionTarget("text/plain", "txt"),
            ),
        )
    }

    @Test
    fun protocolV5HostExplorerPresentationIsStrictlyReadOnlyAndPrimary() {
        val embedded = descriptor(
            protocolVersion = 5,
            cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
            presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
        ) ?: error("Expected a valid host Explorer descriptor")
        assertEquals(ExplorerActionValues.PRESENTATION_HOST_EXPLORER, embedded.presentation)

        assertNull(
            descriptor(
                protocolVersion = 4,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 5,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 5,
                targetKind = ExplorerActionValues.TARGET_DIRECTORY,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
                mimeTypes = emptyList(),
            ),
        )
        assertNull(
            descriptor(
                protocolVersion = 5,
                cardinality = ExplorerActionValues.CARDINALITY_SINGLE,
                accessMode = ExplorerActionValues.ACCESS_CREATE_IN_PARENT,
                placement = ExplorerActionValues.PLACEMENT_PRIMARY,
                presentation = ExplorerActionValues.PRESENTATION_HOST_EXPLORER,
            ),
        )
    }

    @Test
    fun descriptorRejectsInvalidIdentifiersAndActivityNames() {
        assertNull(descriptor(id = "Preview"))
        assertNull(descriptor(activityClassName = "com.example.preview/PreviewActivity"))
        assertNull(descriptor(activityClassName = "com.example.preview.Bad Activity"))
    }

    @Test
    fun fallbackLabelIsSanitizedAndCanBeReplacedByAResourceName() {
        val sanitized = descriptor(labelFallback = "  Preview\u0000\n document  ")
            ?: error("Expected sanitized fallback label")
        assertEquals("Preview document", sanitized.labelFallback)

        assertTrue(
            descriptor(
                labelResourceName = "action_preview",
                labelFallback = null,
            ) != null,
        )
        assertNull(descriptor(labelResourceName = "ActionPreview", labelFallback = null))
    }

    @Test
    fun protocolAndHostVersionCompatibilityAreStrict() {
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(1L))
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(2L))
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(3L))
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(4L))
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(5L))
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(6L))
        assertTrue(ExplorerActionAvailabilityPolicy.isProtocolSupported(7L))
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(null))
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(0L))
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(8L))
        assertTrue(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(4L, 4))
        assertFalse(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(3L, 4))
        assertFalse(ExplorerActionAvailabilityPolicy.isCatalogProtocolCompatible(null, 4))

        assertTrue(ExplorerActionAvailabilityPolicy.isHostVersionCompatible(5_268L, 5_268L))
        assertTrue(ExplorerActionAvailabilityPolicy.isHostVersionCompatible(5_268L, null))
        assertFalse(ExplorerActionAvailabilityPolicy.isHostVersionCompatible(5_268L, 5_269L))
    }

    @Test
    fun emptyPluginAbisMeanUniversalAndDeclaredAbisMustIntersect() {
        val hostAbis = listOf("arm64-v8a", "armeabi-v7a")

        assertTrue(ExplorerActionAvailabilityPolicy.isAbiCompatible(emptyList(), hostAbis))
        assertTrue(ExplorerActionAvailabilityPolicy.isAbiCompatible(null, hostAbis))
        assertTrue(ExplorerActionAvailabilityPolicy.isAbiCompatible(listOf(" ARM64-V8A "), hostAbis))
        assertFalse(ExplorerActionAvailabilityPolicy.isAbiCompatible(listOf("x86_64"), hostAbis))
        assertFalse(ExplorerActionAvailabilityPolicy.isAbiCompatible(listOf(" "), hostAbis))
    }

    @Test
    fun activityDeclarationMustBeExportedEnabledAndPermissionProtected() {
        fun isValid(
            actualPackageName: String = "com.example.preview",
            exported: Boolean = true,
            activityEnabled: Boolean = true,
            applicationEnabled: Boolean = true,
            permission: String? = ExplorerActionPluginPermissions.PLUGIN,
        ) = ExplorerActionAvailabilityPolicy.isActivityDeclarationValid(
            expectedPackageName = "com.example.preview",
            actualPackageName = actualPackageName,
            exported = exported,
            activityEnabled = activityEnabled,
            applicationEnabled = applicationEnabled,
            requiredPermission = ExplorerActionPluginPermissions.PLUGIN,
            actualPermission = permission,
        )

        assertTrue(isValid())
        assertFalse(isValid(actualPackageName = "com.example.other"))
        assertFalse(isValid(exported = false))
        assertFalse(isValid(activityEnabled = false))
        assertFalse(isValid(applicationEnabled = false))
        assertFalse(isValid(permission = null))
        assertFalse(isValid(permission = "com.example.UNSAFE"))
    }

    @Suppress("LongParameterList")
    private fun descriptor(
        protocolVersion: Int = 1,
        id: String = "document-preview",
        labelResourceName: String? = null,
        labelFallback: String? = "Preview",
        activityClassName: String = ".PreviewActivity",
        priority: Int = 0,
        targetKind: Int = ExplorerActionValues.TARGET_FILE,
        cardinality: Int? = null,
        accessMode: Int = ExplorerActionValues.ACCESS_READ_ONLY,
        placement: Int = ExplorerActionValues.PLACEMENT_OVERFLOW,
        presentation: Int? = null,
        mimeTypes: Collection<String>? = listOf("text/*"),
        extensions: Collection<String>? = emptyList(),
    ): ExplorerActionDescriptor? {
        return ExplorerActionCatalogPolicy.normalizeDescriptor(
            protocolVersion = protocolVersion,
            packageName = "com.example.preview",
            id = id,
            labelResourceName = labelResourceName,
            labelFallback = labelFallback,
            activityClassName = activityClassName,
            priority = priority,
            targetKind = targetKind,
            cardinality = cardinality,
            accessMode = accessMode,
            placement = placement,
            presentation = presentation,
            mimeTypes = mimeTypes,
            extensions = extensions,
        )
    }
}
