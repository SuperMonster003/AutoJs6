package org.autojs.autojs.ui.settings.codegen

import org.autojs.autojs.core.accessibility.codegen.store.CatalogModelVersion
import org.autojs.autojs.core.accessibility.codegen.store.InstalledModel
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalog
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogCompatibility
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.HOST_VERSION
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.catalog
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.installed
import org.autojs.autojs.core.accessibility.codegen.store.ModelSource
import org.autojs.autojs.ui.settings.codegen.CodeGenModelsViewModel.RowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeGenModelsStateTest {
    private fun official(code: Int = 1, hash: String = "a".repeat(64)) =
        installed(code, hash).copy(source = ModelSource.OFFICIAL)

    private fun version(code: Int, hash: String = code.toString(16).repeat(64)) =
        catalog(code, hash).models.single().versions.single()

    private fun versions(vararg versions: CatalogModelVersion): ModelCatalog {
        val base = catalog()
        return base.copy(models = listOf(base.models.single().copy(versions = versions.toList())))
    }

    private fun state(
        installed: List<InstalledModel> = emptyList(),
        currentKey: String? = null,
        catalog: ModelCatalog? = null,
        busyKeys: Set<String> = emptySet(),
        loading: Boolean = false,
        error: Throwable? = null,
    ) = CodeGenModelsViewModel.build(installed, currentKey, catalog, HOST_VERSION, busyKeys, loading, error)

    @Test
    fun installedVersionsAreGroupedAndStablySortedWithPerGroupAndTotalDiskUsage() {
        val first = installed(1, id = "a.model").copy(sizeBytes = 100)
        val older = installed(1, id = "z.model").copy(sizeBytes = 200)
        val newer = installed(2, id = "z.model").copy(sizeBytes = 300)
        val alternate = installed(2, hash = "b".repeat(64), id = "z.model").copy(sizeBytes = 400)
        val result = state(listOf(alternate, older, first, newer), older.key)
        assertEquals(listOf("a.model", "z.model"), result.installedGroups.map { it.modelId })
        assertEquals(listOf(newer, alternate, older), result.installedGroups.last().rows.map { it.installed })
        assertEquals(100L, result.installedGroups.first().installedBytes)
        assertEquals(900L, result.installedGroups.last().installedBytes)
        assertEquals(1000L, result.totalInstalledBytes)
        assertEquals(older, result.current)
        assertEquals(listOf(false, false, true), result.installedGroups.last().rows.map { it.isCurrent })
        assertEquals(result, state(listOf(newer, first, older, alternate), older.key))
    }

    @Test
    fun sameIdentityAndVersionWithCustomContentDoesNotHideTheOfficialDownload() {
        val custom = installed(1, "b".repeat(64))
        val result = state(listOf(custom), catalog = catalog())
        assertEquals(custom, result.installedGroups.single().rows.single().installed)
        val downloadable = result.downloadableGroups.single().rows.single()
        assertEquals("a".repeat(64), downloadable.version.artifact.sha256)
        assertTrue(downloadable.canDownload)
        assertNull(result.installedGroups.single().rows.single().update)
    }

    @Test
    fun installedFullHashesAreExcludedAcrossModelIdsAndHashCase() {
        val content = installed(8, "A".repeat(64), "another.model")
        val result = state(listOf(content), catalog = versions(version(1, "a".repeat(64)), version(2)))
        assertEquals(listOf(2), result.downloadableGroups.single().rows.map { it.version.versionCode })
    }

    @Test
    fun equalHashPrefixesStillLeaveDifferentContentAvailable() {
        val custom = installed(hash = "a".repeat(12) + "b".repeat(52))
        val remote = version(2, "a".repeat(12) + "c".repeat(52))
        assertEquals(remote, state(listOf(custom), catalog = versions(remote)).downloadableGroups.single().rows.single().version)
    }

    @Test
    fun downloadableModelsAndVersionsHaveDeterministicOrder() {
        val base = versions(version(1), version(3), version(2))
        val last = base.models.single().copy(modelId = "z.model")
        val first = last.copy(modelId = "a.model")
        val result = state(catalog = base.copy(models = listOf(last, first)))
        assertEquals(listOf("a.model", "z.model"), result.downloadableGroups.map { it.model.modelId })
        for (group in result.downloadableGroups) {
            assertEquals(listOf(3, 2, 1), group.rows.map { it.version.versionCode })
        }
    }

    @Test
    fun onlyCompatibleInstalledVersionsCanBeSetCurrent() {
        val usable = installed(1)
        val futureHost = installed(2).let { it.copy(manifest = it.manifest.copy(minHostVersionCode = HOST_VERSION + 1)) }
        val futureSchema = installed(3).let { it.copy(manifest = it.manifest.copy(featureSchemaVersion = Int.MAX_VALUE)) }
        val differentFeatures = installed(4).let { it.copy(manifest = it.manifest.copy(featureNamesHash = "f".repeat(64))) }
        val result = state(listOf(usable, futureHost, futureSchema, differentFeatures), futureSchema.key)
        val rows = result.installedGroups.single().rows.associateBy { it.installed.versionCode }
        assertTrue(rows.getValue(1).canSetCurrent)
        assertEquals(RowStatus.AVAILABLE, rows.getValue(1).status)
        assertEquals(RowStatus.NEEDS_HOST_UPDATE, rows.getValue(2).status)
        assertEquals(RowStatus.INCOMPATIBLE, rows.getValue(3).status)
        assertEquals(RowStatus.INCOMPATIBLE, rows.getValue(4).status)
        for (code in 2..4) {
            assertFalse(rows.getValue(code).canSetCurrent)
            assertTrue(rows.getValue(code).canDelete)
            assertTrue(rows.getValue(code).canViewModelCard)
        }
        assertTrue(rows.getValue(3).isCurrent)
        assertEquals(futureSchema, result.current)
        assertFalse(state(listOf(usable), usable.key).installedGroups.single().rows.single().canSetCurrent)
    }

    @Test
    fun missingCurrentSelectionIsNotAppliedToAnotherVersion() {
        val installed = installed()
        val result = state(listOf(installed), "missing@1-00000000")
        assertNull(result.current)
        assertFalse(result.installedGroups.single().rows.single().isCurrent)
        assertTrue(result.installedGroups.single().rows.single().canSetCurrent)
    }

    @Test
    fun revokedStatusBindsTheEntireArtifactIdentityAndDoesNotDisableInstalledUse() {
        val official = official()
        val custom = installed(hash = "b".repeat(64))
        val remote = versions(version(1, official.sha256).copy(revoked = true), version(2))
        val result = state(listOf(official, custom), catalog = remote)
        val rows = result.installedGroups.single().rows.associateBy { it.installed.sha256 }
        assertEquals(RowStatus.REVOKED, rows.getValue(official.sha256).status)
        assertTrue(rows.getValue(official.sha256).canSetCurrent)
        assertTrue(rows.getValue(official.sha256).canUpdate)
        assertEquals(RowStatus.AVAILABLE, rows.getValue(custom.sha256).status)
        assertNull(rows.getValue(custom.sha256).update)
    }

    @Test
    fun revokedAndIncompatibleDownloadsRemainVisibleButDisabled() {
        val remote = versions(
            version(1).copy(revoked = true),
            version(2).copy(compatibility = ModelCatalogCompatibility.INCOMPATIBLE),
            version(3).copy(compatibility = ModelCatalogCompatibility.NEEDS_HOST_UPDATE),
            version(4),
        )
        val rows = state(catalog = remote).downloadableGroups.single().rows.associateBy { it.version.versionCode }
        assertEquals(RowStatus.REVOKED, rows.getValue(1).status)
        assertEquals(RowStatus.INCOMPATIBLE, rows.getValue(2).status)
        assertEquals(RowStatus.NEEDS_HOST_UPDATE, rows.getValue(3).status)
        for (code in 1..3) assertFalse(rows.getValue(code).canDownload)
        assertTrue(rows.getValue(4).canDownload)
    }

    @Test
    fun displayOnlyCatalogKeepsKnownVersionsVisibleAndInstalledPackagesUsable() {
        val old = official()
        val result = state(listOf(old), catalog = versions(version(2)).copy(remoteArtifactsAllowed = false))
        val row = result.installedGroups.single().rows.single()
        assertTrue(row.canSetCurrent)
        assertTrue(row.canDelete)
        assertNull(row.update)
        assertFalse(row.canUpdate)
        assertEquals(RowStatus.AVAILABLE, row.status)
        assertEquals(1, result.downloadableGroups.single().rows.size)
        assertFalse(result.downloadableGroups.single().rows.single().canDownload)
    }

    @Test
    fun refreshFailureAndLoadingDoNotBlockInstalledActions() {
        val error = IllegalStateException("Offline")
        val old = installed()
        val result = state(listOf(old), loading = true, error = error)
        assertSame(error, result.catalogError)
        assertTrue(result.loading)
        assertFalse(result.canCheckUpdates)
        val row = result.installedGroups.single().rows.single()
        assertTrue(row.canSetCurrent)
        assertTrue(row.canDelete)
        assertTrue(row.canViewModelCard)
        assertTrue(result.downloadableGroups.isEmpty())
        assertTrue(state(listOf(old), error = error).canCheckUpdates)
    }

    @Test
    fun busyKeysDisableOnlyTheAffectedInstalledAndDownloadActions() {
        val old = official()
        val unrelated = installed(id = "another.model")
        val remote = versions(version(2))
        val target = remote.models.single().versions.single()
        val targetKey = CodeGenModelsViewModel.downloadKey(old.modelId, target)
        val busyDownload = state(listOf(old, unrelated), catalog = remote, busyKeys = setOf(targetKey))
        val oldRow = busyDownload.installedGroups.flatMap { it.rows }.single { it.installed == old }
        assertFalse(oldRow.canUpdate)
        assertTrue(oldRow.canSetCurrent)
        assertFalse(oldRow.isBusy)
        val downloadable = busyDownload.downloadableGroups.single().rows.single()
        assertTrue(downloadable.isBusy)
        assertFalse(downloadable.canDownload)
        assertEquals(targetKey, downloadable.key)
        val busyInstalled = state(listOf(old, unrelated), catalog = remote, busyKeys = setOf(old.key))
        val installedRows = busyInstalled.installedGroups.flatMap { it.rows }.associateBy { it.key }
        with(installedRows.getValue(old.key)) {
            assertTrue(isBusy)
            assertFalse(canSetCurrent)
            assertFalse(canDelete)
            assertFalse(canViewModelCard)
            assertFalse(canUpdate)
        }
        assertTrue(installedRows.getValue(unrelated.key).canSetCurrent)
    }

    @Test
    fun updateStateDisappearsWhenTheCandidateIsAlreadyInstalled() {
        val old = official()
        val remote = versions(version(2))
        val before = state(listOf(old), old.key, remote).installedGroups.single().rows.single()
        assertEquals(RowStatus.UPDATE_AVAILABLE, before.status)
        assertTrue(before.canUpdate)
        assertEquals(2, before.update?.version?.versionCode)
        val newer = official(2, version(2).artifact.sha256)
        val after = state(listOf(old, newer), old.key, remote)
        val oldRow = after.installedGroups.single().rows.single { it.installed == old }
        assertNull(oldRow.update)
        assertFalse(oldRow.canUpdate)
        assertEquals(RowStatus.AVAILABLE, oldRow.status)
        assertTrue(after.downloadableGroups.isEmpty())
        assertTrue(after.installedGroups.single().rows.single { it.installed == newer }.canSetCurrent)
        assertEquals(old, after.current)
    }

    @Test
    fun deprecatedVersionsCanBeExplicitlyDownloadedButAreNotRecommendedAsUpdates() {
        val deprecated = version(2).copy(deprecated = true)
        val remote = versions(version(1, "a".repeat(64)).copy(deprecated = true), deprecated)
        val result = state(listOf(official()), catalog = remote)
        assertTrue(result.installedGroups.single().rows.single().deprecated)
        assertNull(result.installedGroups.single().rows.single().update)
        assertTrue(result.downloadableGroups.single().rows.single().canDownload)
    }

    @Test
    fun stateGroupsAreReadOnlySnapshotsAndDoNotMutateCallerLists() {
        val old = installed()
        val installed = mutableListOf(old)
        val result = state(installed, catalog = versions(version(2)))
        assertEquals(listOf(old), installed)
        installed.clear()
        assertEquals(old, result.installedGroups.single().rows.single().installed)
        assertThrows(UnsupportedOperationException::class.java) { (result.installedGroups as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.installedGroups.single().rows as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.downloadableGroups.single().rows as MutableList<*>).clear() }
    }
}
