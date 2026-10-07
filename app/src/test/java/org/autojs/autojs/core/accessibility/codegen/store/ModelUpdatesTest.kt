package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.catalog
import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.installed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelUpdatesTest {
    private fun official(code: Int = 1) = installed(code).copy(source = ModelSource.OFFICIAL)

    private fun version(code: Int) = catalog(code, code.toString(16).repeat(64)).models.single().versions.single()

    private fun versions(vararg versions: CatalogModelVersion): ModelCatalog {
        val base = catalog()
        return base.copy(models = listOf(base.models.single().copy(versions = versions.toList())))
    }

    @Test
    fun highestCompatibleVersionIsSelectedIndependentlyOfCatalogOrderAndVersionName() {
        val old = official().copy(manifest = official().manifest.copy(versionName = "999.0"))
        val newest = version(3).copy(versionName = "1.0")
        for (catalog in listOf(versions(version(2), newest), versions(newest, version(2)))) {
            val update = requireNotNull(ModelUpdates.find(old, catalog))
            assertEquals(old, update.installed)
            assertEquals(catalog.models.single(), update.model)
            assertEquals(newest, update.version)
        }
    }

    @Test
    fun sameOrLowerVersionCodeDoesNotOfferAnUpdateEvenWhenContentDiffers() {
        assertNull(ModelUpdates.find(official(3), versions(version(2), version(3))))
    }

    @Test
    fun incompatibleAndTooNewHostVersionsAreSkippedForTheNewestUsableVersion() {
        val eligible = version(2)
        val incompatible = version(3).copy(compatibility = ModelCatalogCompatibility.INCOMPATIBLE)
        val hostTooOld = version(4).copy(compatibility = ModelCatalogCompatibility.NEEDS_HOST_UPDATE)
        assertEquals(eligible, ModelUpdates.find(official(), versions(hostTooOld, incompatible, eligible))?.version)
        assertNull(ModelUpdates.find(official(), versions(incompatible, hostTooOld)))
    }

    @Test
    fun revokedAndDeprecatedVersionsAreNeverUpdateRecommendations() {
        val eligible = version(2)
        val revoked = version(3).copy(revoked = true)
        val deprecated = version(4).copy(deprecated = true)
        assertEquals(eligible, ModelUpdates.find(official(), versions(eligible, revoked, deprecated))?.version)
        assertNull(ModelUpdates.find(official(), versions(revoked, deprecated)))
        assertTrue(deprecated.canDownload)
        assertFalse(revoked.canDownload)
    }

    @Test
    fun customPackagesNeverParticipateInOnlineUpdates() {
        assertNull(ModelUpdates.find(installed(), versions(version(2))))
    }

    @Test
    fun missingAndDisplayOnlyCatalogsDoNotOfferUpdates() {
        assertNull(ModelUpdates.find(official(), null))
        assertNull(ModelUpdates.find(official(), versions(version(2)).copy(remoteArtifactsAllowed = false)))
    }

    @Test
    fun aHigherVersionOfAnotherModelIsNotAnUpdate() {
        val catalog = versions(version(2))
        assertNull(ModelUpdates.find(official(), catalog.copy(models = listOf(
            catalog.models.single().copy(modelId = "another.model")))))
    }

    @Test
    fun anOfficialVersionMissingFromTheCurrentCatalogCanStillBeUpdated() {
        val newer = version(2)
        assertEquals(newer, ModelUpdates.find(official(), versions(newer))?.version)
    }

    @Test
    fun aRevokedInstalledVersionCanBeUpdatedToANewerUsableVersion() {
        val newer = version(2)
        assertEquals(newer, ModelUpdates.find(official(), versions(version(1).copy(revoked = true), newer))?.version)
    }

    @Test
    fun completionSwitchesOnlyIfTheOldVersionIsStillCurrent() {
        val old = official()
        val other = installed(5, id = "another.model")
        assertTrue(ModelUpdates.shouldSwitch(old, old.key))
        // A user can change or clear the selection while a transfer is in progress.
        assertFalse(ModelUpdates.shouldSwitch(old, other.key))
        assertFalse(ModelUpdates.shouldSwitch(old, null))
        assertFalse(ModelUpdates.shouldSwitch(old, installed(2).key))
    }

    @Test
    fun completionDoesNotConfuseSameModelAndVersionWithDifferentContent() {
        val old = official()
        val changed = old.copy(sha256 = "f".repeat(64))
        assertFalse(ModelUpdates.shouldSwitch(old, changed.key))
    }
}
