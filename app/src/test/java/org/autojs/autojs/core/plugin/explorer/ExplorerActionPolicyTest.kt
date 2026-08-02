package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerActionPolicyTest {

    @Test
    fun mainFilesMapsEntryPointsToTheirDeclaredPlacement() {
        val primary = descriptor(
            protocolVersion = 2,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
        )
        val overflow = descriptor(
            protocolVersion = 1,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
        )

        listOf(
            ExplorerActionEntryPoint.DEFAULT_CLICK,
            ExplorerActionEntryPoint.PRIMARY_BUTTON,
            ExplorerActionEntryPoint.MEDIA_INFO_DIALOG,
        ).forEach { entryPoint ->
            assertTrue(ExplorerActionPolicy.allows(ExplorerSurface.MAIN_FILES, entryPoint, primary))
            assertFalse(ExplorerActionPolicy.allows(ExplorerSurface.MAIN_FILES, entryPoint, overflow))
        }
        assertTrue(
            ExplorerActionPolicy.allows(
                ExplorerSurface.MAIN_FILES,
                ExplorerActionEntryPoint.OVERFLOW_MENU,
                overflow,
            ),
        )
        assertFalse(
            ExplorerActionPolicy.allows(
                ExplorerSurface.MAIN_FILES,
                ExplorerActionEntryPoint.OVERFLOW_MENU,
                primary,
            ),
        )
    }

    @Test
    fun unspecifiedSurfaceDeniesEveryThirdPartyEntryPoint() {
        val primary = descriptor(
            protocolVersion = 2,
            placement = ExplorerActionValues.PLACEMENT_PRIMARY,
        )
        val overflow = descriptor(
            protocolVersion = 1,
            placement = ExplorerActionValues.PLACEMENT_OVERFLOW,
        )

        ExplorerActionEntryPoint.entries.forEach { entryPoint ->
            assertFalse(ExplorerActionPolicy.allows(ExplorerSurface.UNSPECIFIED, entryPoint, primary))
            assertFalse(ExplorerActionPolicy.allows(ExplorerSurface.UNSPECIFIED, entryPoint, overflow))
        }
        assertNull(ExplorerActionPolicy.sourceSurfaceValue(ExplorerSurface.UNSPECIFIED))
        assertEquals("main", ExplorerActionPolicy.sourceSurfaceValue(ExplorerSurface.MAIN_FILES))
    }

    @Test
    fun fallbackRunsExactlyOnceOnlyWhenTheAttemptFails() {
        var fallbackCount = 0

        assertTrue(ExplorerActionExecution.withFallback({ true }) { fallbackCount++ })
        assertEquals(0, fallbackCount)

        assertFalse(ExplorerActionExecution.withFallback({ false }) { fallbackCount++ })
        assertEquals(1, fallbackCount)

        assertFalse(
            ExplorerActionExecution.withFallback(
                attempt = { error("launch failed") },
                fallback = { fallbackCount++ },
            ),
        )
        assertEquals(2, fallbackCount)
    }

    private fun descriptor(
        protocolVersion: Int,
        placement: Int,
    ): ExplorerActionDescriptor {
        return ExplorerActionCatalogPolicy.normalizeDescriptor(
            protocolVersion = protocolVersion,
            packageName = "com.example.viewer",
            id = "view-file",
            labelResourceName = null,
            labelFallback = "View",
            activityClassName = ".ViewActivity",
            priority = 0,
            targetKind = ExplorerActionValues.TARGET_FILE,
            accessMode = ExplorerActionValues.ACCESS_READ_ONLY,
            placement = placement,
            mimeTypes = listOf("*/*"),
            extensions = emptyList(),
        ) ?: error("Expected a valid descriptor")
    }
}
