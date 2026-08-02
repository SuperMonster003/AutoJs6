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
        assertNull(descriptor(placement = ExplorerActionValues.PLACEMENT_OVERFLOW + 1))
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
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(null))
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(0L))
        assertFalse(ExplorerActionAvailabilityPolicy.isProtocolSupported(2L))

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
        id: String = "document-preview",
        labelResourceName: String? = null,
        labelFallback: String? = "Preview",
        activityClassName: String = ".PreviewActivity",
        priority: Int = 0,
        targetKind: Int = ExplorerActionValues.TARGET_FILE,
        accessMode: Int = ExplorerActionValues.ACCESS_READ_ONLY,
        placement: Int = ExplorerActionValues.PLACEMENT_OVERFLOW,
        mimeTypes: Collection<String>? = listOf("text/*"),
        extensions: Collection<String>? = emptyList(),
    ): ExplorerActionDescriptor? {
        return ExplorerActionCatalogPolicy.normalizeDescriptor(
            packageName = "com.example.preview",
            id = id,
            labelResourceName = labelResourceName,
            labelFallback = labelFallback,
            activityClassName = activityClassName,
            priority = priority,
            targetKind = targetKind,
            accessMode = accessMode,
            placement = placement,
            mimeTypes = mimeTypes,
            extensions = extensions,
        )
    }
}
