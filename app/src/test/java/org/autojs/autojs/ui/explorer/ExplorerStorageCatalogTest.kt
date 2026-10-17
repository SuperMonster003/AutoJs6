package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerStorageCatalogTest {

    @Test
    fun mountedVolumesFollowRootAndPrimaryWithoutDuplicatingAliasesOrQueryingUnmountedMedia() {
        val queriedPaths = mutableListOf<String>()
        val entries = ExplorerStorageCatalog.buildEntries(
            rootTitle = "Root",
            volumes = listOf(
                volume("/storage/ABCD-1234", "SD", primary = false),
                volume("/storage/emulated/0", "Internal", primary = true),
                volume("/storage/ABCD-1234/", "Duplicate", primary = false),
                volume("/storage/REMOVED", "Removed", primary = false, mounted = false),
                volume("/storage/emulated/0/../0", "Internal alias", primary = true),
            ),
        ) { path ->
            queriedPaths += path
            ExplorerStorageUsage.fromBytes(1_000, 250)
        }

        assertEquals(listOf("/", "/storage/emulated/0", "/storage/ABCD-1234"), entries.map { it.path })
        assertEquals(listOf("Root", "Internal", "SD"), entries.map { it.title })
        assertEquals(listOf(ExplorerStorageKind.ROOT, ExplorerStorageKind.PRIMARY, ExplorerStorageKind.REMOVABLE), entries.map { it.kind })
        assertEquals(entries.map { it.path }, queriedPaths)
    }

    @Test
    fun unavailableCapacityKeepsTheStorageEntryWithoutClaimingItIsEmptyOrFull() {
        val entries = ExplorerStorageCatalog.buildEntries("Root", listOf(volume("/storage/ABCD-1234", "SD"))) { null }

        assertEquals(2, entries.size)
        assertTrue(entries.all { it.usage == null })
        assertNull(ExplorerStorageUsage.fromBytes(0, 0))
        assertNull(ExplorerStorageUsage.fromBytes(-1, 0))
        assertNull(ExplorerStorageUsage.fromBytes(10, -1))
        assertNull(ExplorerStorageUsage.fromBytes(10, 11))
    }

    @Test
    fun capacityUsesAvailableBytesAndDoesNotOverflowOnLargeVolumes() {
        val usage = requireNotNull(ExplorerStorageUsage.fromBytes(1_000, 310))
        assertEquals(690L, usage.usedBytes)
        assertEquals(69, usage.usedPercent)
        assertEquals(0, ExplorerStorageUsage.fromBytes(Long.MAX_VALUE, Long.MAX_VALUE)?.usedPercent)
        assertEquals(100, ExplorerStorageUsage.fromBytes(Long.MAX_VALUE, 0)?.usedPercent)
        assertEquals(100, ExplorerStorageUsage.fromBytes(1_000, 1)?.usedPercent)
    }

    @Test
    fun legacyMountResolutionOnlyUsesTheObservedAppSpecificExternalDirectory() {
        assertEquals(
            "/storage/ABCD-1234",
            ExplorerStorageCatalog.legacyVolumeRoot("/storage/ABCD-1234/Android/data/org.autojs.autojs6/files/", "org.autojs.autojs6"),
        )
        assertNull(ExplorerStorageCatalog.legacyVolumeRoot("/storage/ABCD-1234/Android/data/another.app/files", "org.autojs.autojs6"))
        assertNull(ExplorerStorageCatalog.legacyVolumeRoot("/storage/ABCD-1234/Android/data/org.autojs.autojs6/files/nested", "org.autojs.autojs6"))
        assertNull(ExplorerStorageCatalog.legacyVolumeRoot("/Android/data/org.autojs.autojs6/files", "org.autojs.autojs6"))
    }

    @Test
    fun protectedAndroidDirectoriesAreRecognizedAcrossSharedStorageMountsAndAliases() {
        listOf(
            "/storage/emulated/0/Android/data",
            "/storage/emulated/10/Android/obb/com.example.app",
            "/storage/ABCD-1234/Android/data/",
            "/storage/self/primary/Android/obb",
            "/sdcard/Android/data/com.example.app/files",
            "/mnt/sdcard/Android/data",
            "/mnt/media_rw/ABCD-1234/Android/obb",
        ).forEach { path -> assertTrue(path, ExplorerStorageCatalog.isAndroidRestrictedPath(path)) }
    }

    @Test
    fun similarlyNamedFoldersAndNestedOrdinaryDirectoriesAreNotClassifiedAsProtected() {
        listOf(
            "/storage/emulated/0/Android/media",
            "/storage/emulated/0/Android/database",
            "/storage/emulated/0/Download/Android/data",
            "/storage/emulated/0/Android/data/../media",
            "/data/user/0/Android/data",
            "relative/Android/data",
        ).forEach { path -> assertFalse(path, ExplorerStorageCatalog.isAndroidRestrictedPath(path)) }
    }

    private fun volume(path: String, title: String, primary: Boolean = false, mounted: Boolean = true) =
        ExplorerStorageVolumeSnapshot(path, title, primary, mounted)
}
